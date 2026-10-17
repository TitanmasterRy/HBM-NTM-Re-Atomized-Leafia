package com.leafia.contents.machines.misc.modular_turbine.ports;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.misc.modular_turbine.MTPacketId;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A steam port: where a stage of the turbine takes steam in and gives spent steam back.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ports/MTComponentPortTE.java}.
 *
 * <h2>It owns no tanks</h2>
 *
 * Both tanks belong to the {@code TurbineAssembly} — the steam stage the port sits in — and every
 * port in that stage hands out the same two. So a stage with three ports is one buffer reached
 * from three places, and an unassembled port has nothing to fill: {@link #getReceivingTanks()}
 * answers an empty array until a core claims it.
 *
 * <h2>Where steam enters is recorded</h2>
 *
 * {@link #transferFluid} is the base mod's default, line for line, plus one call: each time it
 * puts steam into a tank it tells the stage which shaft position received it ({@link #onFill}).
 * The core reads those positions back to decide which way each blade should face, so <b>a blade
 * is right or wrong relative to where the steam came in</b>, not relative to the shaft.
 *
 * <h2>Two syncs, because one of them is dead here</h2>
 *
 * Upstream sends the identifier on the first tick, and again from {@code onPlayerValidate} as each
 * player comes within range. The second never runs in this port — its driving sweep lives in the
 * unported client listener (D-1029) — so a player arriving after the first tick would see every
 * port unset. The identifier rides vanilla's block-entity update tag as well, the way the letter
 * signs' colour does, and the first-tick packet is kept because upstream sends it.
 *
 * <p>No block uses this yet: the fourteen port blocks are T3, and that batch
 * registers its {@link BlockEntityType}. It lands here because it is in the core's import cycle.
 */
public class MTComponentPortTE extends ModularTurbineComponentTE
		implements IFluidStandardSenderMK2, IFluidStandardReceiverMK2, LeafiaPacketReceiver {

	public FluidType identifier;
	/** Always true: upstream's only toggle for it is commented out. See {@code ModularTurbineBlockBase.onScrew}. */
	public boolean decompress = true;
	boolean loaded = true;

	public MTComponentPortTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void onChunkUnloaded() {
		loaded = false;
		super.onChunkUnloaded();
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		if(assembly != null)
			return new FluidTank[] { assembly.input };
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		if(assembly != null)
			return new FluidTank[] { assembly.output };
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		if(assembly != null)
			return new FluidTank[] { assembly.input, assembly.output };
		return new FluidTank[0];
	}

	@Override
	public boolean isLoaded() {
		return loaded;
	}

	IMTPortBlock port = null;
	boolean syncNeeded = true;

	/** Upstream's {@code update()}. Server-side only, as upstream's is. */
	public void updateEntity() {
		if(!level.isClientSide) {
			if(port == null && level.getBlockState(worldPosition).getBlock() instanceof IMTPortBlock p)
				port = p;
			if(assembly != null && port != null) {
				for(DirPos conPos : port.getConPos(worldPosition, getAssemblyFacing())) {
					trySubscribe(assembly.typeIn, level, conPos);
					tryProvide(assembly.output, level, conPos);
				}
			}
			if(syncNeeded) {
				syncNeeded = false;
				LeafiaPacket._start(this)
						.__write(MTPacketId.PORT_IDENTIFIER.id, identifier != null ? identifier.getName() : null)
						.__write(MTPacketId.PORT_DECOMPRESSION.id, decompress)
						.__sendToAffectedClients();
			}
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MTComponentPortTE port) {
		port.updateEntity();
	}

	@Override
	public String getPacketIdentifier() {
		return "MT_PORT";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == MTPacketId.PORT_IDENTIFIER.id) {
			if(value == null) identifier = null;
			else identifier = Fluids.fromName((String) value);
		} else if(key == MTPacketId.PORT_DECOMPRESSION.id)
			decompress = (boolean) value;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket._start(this)
				.__write(MTPacketId.PORT_IDENTIFIER.id, identifier != null ? identifier.getName() : null)
				.__write(MTPacketId.PORT_DECOMPRESSION.id, decompress)
				.__sendToClient(plr);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		identifier = compound.contains("identifier") ? Fluids.fromName(compound.getString("identifier")) : null;
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(identifier != null)
			compound.putString("identifier", identifier.getName());
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/**
	 * Records the shaft position that just received steam.
	 *
	 * <p>It records this port's first shaft position from the core's {@code positionToComponentMap}
	 * (D-3075; upstream used the index into the component list, which is wrong behind a generator).
	 */
	void onFill() {
		if(assembly != null && core != null) {
			// D-3075: shaft position, not index in the component list (upstream's list index was two places
			// early behind a three-long generator and made a correctly built turbine report a wrong blade).
			int index = -1;
			for(var entry : core.positionToComponentMap.entrySet())
				if(entry.getValue() == this && (index == -1 || entry.getKey() < index)) index = entry.getKey();
			if(index == -1)
				core.disassemble();
			else
				assembly.receivingPositions.add(index);
		}
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long amount) {
		int tanks = 0;
		for(FluidTank tank : this.getReceivingTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure)
				++tanks;
		}
		if(tanks > 1) {
			int firstRound = (int) Math.floor((double) amount / (double) tanks);
			for(FluidTank tank : this.getReceivingTanks()) {
				if(tank.getTankType() == type && tank.getPressure() == pressure) {
					int toAdd = Math.min(firstRound, tank.getMaxFill() - tank.getFill());
					tank.setFill(tank.getFill() + toAdd);
					amount -= toAdd;
					onFill();
				}
			}
		}
		if(amount > 0L) {
			for(FluidTank tank : this.getReceivingTanks()) {
				if(tank.getTankType() == type && tank.getPressure() == pressure) {
					int toAdd = (int) Math.min(amount, (long) (tank.getMaxFill() - tank.getFill()));
					tank.setFill(tank.getFill() + toAdd);
					amount -= toAdd;
					onFill();
				}
			}
		}
		return amount;
	}
}
