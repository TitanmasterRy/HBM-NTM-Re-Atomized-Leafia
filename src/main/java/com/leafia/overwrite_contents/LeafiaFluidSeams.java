package com.leafia.overwrite_contents;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityHeatBoilerIndustrial;
import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blockentity.machine.storage.FluidStorageHooks;
import com.hbm.blocks.machine.BlockFluidBarrel;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.network.pipe_amat.uninos.AmatNet;
import com.leafia.contents.network.pipe_amat.uninos.AmatNode;
import com.leafia.unsorted.fluids.FluidIconOverlays;

import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Where the addon plugs into the base mod's fluid seams (D-2198, D-3025): the fluid network's provider check and
 * extra network, the barrel's port filter, the fluid stores' mode hook, the fluid icon's overlay provider and the
 * industrial boiler's tank sizer. Not an upstream file; each part is what one or two of upstream's mixins into
 * {@code com.hbm} became (D-1001).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th></tr>
 * <tr><td>{@code MixinFluidNetMK2} (an {@code @Overwrite} of {@code setupFluidProviders} to add an explosion)</td>
 * <td>{@link #ANTIMATTER}, a {@code FluidNetMK2.ProviderCheck}</td></tr>
 * <tr><td>{@code MixinIFluidReceiverMK2}, {@code MixinIFluidStandardSenderMK2} (an {@code @Overwrite} of
 * {@code trySubscribe}/{@code tryProvide} to join the antimatter network as well)</td><td>{@link #AMAT_NET}, a
 * {@code FluidNetMK2.ExtraNetwork}</td></tr>
 * <tr><td>{@code MixinTileEntityMachineFluidTank}, the buffer half of {@code MixinTileEntityBarrel} (an injection into
 * {@code update} to run the mode block against the antimatter network)</td><td>{@link #AMAT_STORAGE}, a
 * {@code FluidStorageHooks.Hook}</td></tr>
 * <tr><td>the port half of {@code MixinTileEntityBarrel}</td><td>{@link #BARREL_PORTS}, a
 * {@code BlockEntityBarrel.PortFilter}</td></tr>
 * <tr><td>{@code MixinItemFluidIcon}</td><td>{@link FluidIconOverlays}, an {@code ItemFluidIcon.OverlayProvider}</td></tr>
 * <tr><td>{@code MixinTileEntityHeatBoilerIndustrial}'s constructor tail</td><td>{@link #BOILER_TANKS}, a
 * {@code TankSizer}</td></tr>
 * </table>
 *
 * <p>Every part reads its switch when it runs, so with the switch off the base mod is untouched.
 */
public final class LeafiaFluidSeams {

	private LeafiaFluidSeams() {}

	private static boolean installed = false;

	public static synchronized void install() {
		if(installed) return;
		installed = true;
		FluidNetMK2.addProviderCheck(ANTIMATTER);
		FluidNetMK2.addExtraNetwork(AMAT_NET);
		FluidStorageHooks.register(AMAT_STORAGE);
		BlockEntityBarrel.addPortFilter(BARREL_PORTS);
		BlockEntityHeatBoilerIndustrial.addTankSizer(BOILER_TANKS);
		ItemFluidIcon.addOverlayProvider(FluidIconOverlays::overlayModel);
	}

	// ------------------------------------------------------------------ MixinFluidNetMK2

	/** The blast of an antimatter provider that is connected to anything: power 5, with fire, breaking blocks. */
	public static final float ANTIMATTER_POWER = 5F;

	/**
	 * Upstream's {@code checkExplode}, added to the provider loop's drop test. A provider on a plain network
	 * carrying antimatter, with any receiver other than itself subscribed, blows up where it stands and is dropped;
	 * a provider that is not a block entity is dropped by nothing here. The antimatter pipe's own network overrides
	 * the loop and never asks this (D-1022), so it is untouched.
	 */
	private static final FluidNetMK2.ProviderCheck ANTIMATTER = (net, provider) -> {
		if(!LeafiaFeatures.antimatterContainment) return false;
		if(!net.getType().hasTrait(FT_Amat.class)) return false;
		if(!net.hasOtherReceiver(provider)) return false;
		if(provider instanceof BlockEntity be && be.getLevel() != null) {
			BlockPos pos = be.getBlockPos();
			be.getLevel().explode(null, pos.getX(), pos.getY(), pos.getZ(), ANTIMATTER_POWER, true, Level.ExplosionInteraction.BLOCK);
			return true;
		}
		return false;
	};

	// ------------------------------------------------------------------ MixinIFluidReceiverMK2, MixinIFluidStandardSenderMK2

	/**
	 * The antimatter pipe's network for any fluid, beside the fluid's own: a machine next to an amat duct subscribes
	 * to it, and a provider provides into it. Upstream does it for every fluid, whether or not it is antimatter or
	 * magnetic - the net itself decides what to do with the fluid (D-1022).
	 */
	private static final FluidNetMK2.ExtraNetwork AMAT_NET = type -> LeafiaFeatures.amatPipes ? AmatNet.getProvider(type) : null;

	// ------------------------------------------------------------------ the buffer-mode node, MixinTileEntityBarrel

	/** Upstream's {@code @Unique} fields of the barrel and the tank: the node and the type it was made for. */
	private static final class AmatState {
		FluidType lastType;
		AmatNode node;
	}

	private static final Map<BlockEntity, AmatState> AMAT_STATES = new WeakHashMap<>();

	private static synchronized AmatState state(BlockEntity storage) {
		return AMAT_STATES.computeIfAbsent(storage, s -> new AmatState());
	}

	@Nullable
	private static synchronized AmatState peek(BlockEntity storage) {
		return AMAT_STATES.get(storage);
	}

	/** The antimatter node a store in buffer mode has on its own position, or null: for the tests. */
	@Nullable
	public static AmatNode bufferNode(BlockEntity storage) {
		AmatState state = peek(storage);
		return state == null ? null : state.node;
	}

	private static AmatNode createAmatNode(BlockEntity storage, FluidType type, DirPos[] conPos) {
		java.util.Set<BlockPos> posSet = new java.util.HashSet<>();
		posSet.add(storage.getBlockPos());
		for(DirPos pos : conPos) {
			Direction dir = pos.getDir();
			posSet.add(pos.relative(dir.getOpposite()));
		}
		return new AmatNode(AmatNet.getProvider(type), posSet.toArray(new BlockPos[0])).setConnections(conPos);
	}

	/**
	 * The mode block run again against the antimatter network. Upstream injects it into {@code update} and the base
	 * mod's own block follows it; here it follows the base's. Buffer mode (1) makes the store a node of its own on
	 * its fluid's antimatter net and puts it on that net as provider and receiver. Any other mode destroys the node
	 * and, on each port, takes the store off the neighbour's antimatter net as a provider (unless providing, mode 2)
	 * and as a receiver (unless receiving, mode 0).
	 *
	 * <p><b>Not reproduced:</b> upstream's mode 2 calls {@code tryProvide} on each port a second time. The base
	 * mod's own call already joins the antimatter net through the extra network, so the repeat only adds a second
	 * direct transfer into an adjacent machine each tick - an accident of the injection point (D-3025).
	 */
	private static final FluidStorageHooks.Hook AMAT_STORAGE = new FluidStorageHooks.Hook() {

		@Override
		public void afterModes(BlockEntity storage, FluidTank tank, short mode, DirPos[] ports) {
			if(!LeafiaFeatures.amatPipes) return;
			if(!(storage instanceof IFluidStandardTransceiverMK2 transceiver)) return;
			Level level = storage.getLevel();
			if(level == null) return;
			BlockPos pos = storage.getBlockPos();
			AmatState state = state(storage);
			FluidType type = tank.getTankType();

			if(mode == 1) {
				if(state.node == null || state.node.expired || type != state.lastType) {
					state.node = (AmatNode) UniNodespace.getNode(level, pos, AmatNet.getProvider(type));
					if(state.node == null || state.node.expired || type != state.lastType) {
						state.node = createAmatNode(storage, type, ports);
						UniNodespace.createNode(level, state.node);
						state.lastType = type;
					}
				}
				if(state.node != null && state.node.hasValidNet()) {
					state.node.net.addProvider(transceiver);
					state.node.net.addReceiver(transceiver);
				}
			} else {
				if(state.node != null) {
					UniNodespace.destroyNode(level, pos, AmatNet.getProvider(type));
					state.node = null;
				}
				for(DirPos port : ports) {
					AmatNode dirNode = (AmatNode) UniNodespace.getNode(level, port, AmatNet.getProvider(type));
					boolean valid = dirNode != null && dirNode.hasValidNet();

					if(mode != 2 && valid) dirNode.net.removeProvider(transceiver);
					if(mode == 0) {
						if(valid) dirNode.net.addReceiver(transceiver);
					} else if(valid) {
						dirNode.net.removeReceiver(transceiver);
					}
				}
			}
		}

		/** Upstream leaves the node behind when the store is removed; it expires with its pipes, but not with the store. */
		@Override
		public void onRemoved(BlockEntity storage, FluidTank tank) {
			AmatState state = peek(storage);
			if(state == null || state.node == null) return;
			Level level = storage.getLevel();
			if(level != null) UniNodespace.destroyNode(level, storage.getBlockPos(), AmatNet.getProvider(tank.getTankType()));
			state.node = null;
		}
	};

	// ------------------------------------------------------------------ MixinTileEntityBarrel: the ports

	private static boolean isPlainBarrel(BlockEntityBarrel barrel) {
		return barrel.getBlockState().getBlock() instanceof BlockFluidBarrel;
	}

	/**
	 * Upstream's {@code getConPos} and {@code canConnect}, for the fluid barrels (not the big tank). With
	 * {@link LeafiaFeatures#barrelSidePorts} on, the ports are the six sides - in upstream's order - and a neighbour
	 * may connect only from above and below; with it off, only the top and bottom ports exist and the base mod's
	 * {@code canConnect} answers. The two halves disagree about what "side ports" means, as upstream's do; both are
	 * kept (D-3025). Upstream's {@code hasCapability} half is a Forge-capability answer the base barrel has no
	 * counterpart of.
	 */
	private static final BlockEntityBarrel.PortFilter BARREL_PORTS = new BlockEntityBarrel.PortFilter() {

		@Override
		public DirPos[] ports(BlockEntityBarrel barrel, DirPos[] ports) {
			if(!LeafiaFeatures.barrelPorts || !isPlainBarrel(barrel)) return ports;
			BlockPos pos = barrel.getBlockPos();
			if(LeafiaFeatures.barrelSidePorts) {
				return new DirPos[] {
						new DirPos(pos.getX() + 1, pos.getY(), pos.getZ(), Direction.EAST),
						new DirPos(pos.getX() - 1, pos.getY(), pos.getZ(), Direction.WEST),
						new DirPos(pos.getX(), pos.getY() + 1, pos.getZ(), Direction.UP),
						new DirPos(pos.getX(), pos.getY() - 1, pos.getZ(), Direction.DOWN),
						new DirPos(pos.getX(), pos.getY(), pos.getZ() + 1, Direction.SOUTH),
						new DirPos(pos.getX(), pos.getY(), pos.getZ() - 1, Direction.NORTH)
				};
			}
			return new DirPos[] {
					new DirPos(pos.getX(), pos.getY() + 1, pos.getZ(), Direction.UP),
					new DirPos(pos.getX(), pos.getY() - 1, pos.getZ(), Direction.DOWN)
			};
		}

		@Override
		public boolean canConnect(BlockEntityBarrel barrel, FluidType type, Direction dir, boolean connects) {
			if(!LeafiaFeatures.barrelPorts || !isPlainBarrel(barrel)) return connects;
			if(LeafiaFeatures.barrelSidePorts && dir != Direction.UP && dir != Direction.DOWN) return false;
			return connects;
		}
	};

	// ------------------------------------------------------------------ MixinTileEntityHeatBoilerIndustrial

	/** The factor upstream's constructor tail applies: {@code 64_000 * 5} and {@code 64_000 * 5 * 100}. */
	public static final int BOILER_TANK_FACTOR = 5;

	private static final BlockEntityHeatBoilerIndustrial.TankSizer BOILER_TANKS =
			(tank, capacity) -> LeafiaFeatures.industrialBoilerTanks ? capacity * BOILER_TANK_FACTOR : capacity;
}
