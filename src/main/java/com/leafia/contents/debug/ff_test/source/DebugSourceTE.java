package com.leafia.contents.debug.ff_test.source;

import java.util.Map;

import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.llib.group.LeafiaMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The debug Forge-fluid source: a bottomless 10,000 mB of thorium-uranium FLiBe that it offers to every side.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/ff_test/source/DebugSourceTE.java}. Every tick the tank is emptied and
 * refilled with {@link #FILL} mB of FLiBe whose tag carries upstream's test mixture - {@code th232} 4 and {@code u235}
 * 2, in that order, and no heat key - and, on the server, offered to the block on each of the six sides through
 * {@link IFFProvider#tryProvide} (a duct's network registers it, a receiver beside it is filled directly) and sent to
 * the clients in range under key 0 for the look overlay. It takes nothing ({@link #fill} answers 0) and gives from its
 * tank to anything that drains it.
 *
 * <p>FLiBe is the addon's fluid ({@code features.addonFluids}) and moves as a Forge fluid only with the bridge
 * ({@link LeafiaFF#enabled}); the block registers only when both are there ({@code LeafiaFeatures.debugFFBlocks()}).
 * This block also opens the CanneryTest guide topic, which {@code AddonJars} keys on its name (D-3029 hand-off 3).
 */
public class DebugSourceTE extends BlockEntity implements IFFProvider, LeafiaPacketReceiver {

	public static final int CAPACITY = 10000;
	public static final int FILL = 10000;

	public final FluidTank zaza = new FluidTank(CAPACITY);

	public DebugSourceTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public DebugSourceTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.FF_SOURCE.get(), pos, state);
	}

	/** Upstream's {@code stacc}: {@link #FILL} mB of FLiBe tagged with the test mixture. Empty without the bridge. */
	public static FluidStack testSalt() {
		if(LeafiaFluids.FLUORIDE == null) return FluidStack.EMPTY;
		FluidStack stacc = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, FILL);
		if(stacc.isEmpty()) return stacc;
		Map<String, Double> mix = new LeafiaMap<>();
		mix.put(MSRFuel.th232.name(), 4d);
		mix.put(MSRFuel.u235.name(), 2d);
		LeafiaFF.setTag(stacc, MSRTEBase.writeMixture(mix, new CompoundTag()));
		return stacc;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, DebugSourceTE te) {
		te.update();
	}

	/** Upstream's {@code update}: reset, refill, offer on six sides, sync. */
	public void update() {
		zaza.drain(999999999, FluidAction.EXECUTE); // reset shit
		zaza.fill(testSalt(), FluidAction.EXECUTE); // and refill shit
		if(level != null && !level.isClientSide) {
			for(Direction facing : Direction.values())
				tryProvide(zaza, level, worldPosition.relative(facing), facing);
			LeafiaPacket._start(this).__write(0, zaza.writeToNBT(level.registryAccess(), new CompoundTag())).__sendToAffectedClients();
		}
	}

	@Override
	public String getPacketIdentifier() {
		return "debug_ff_source";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0 && level != null) {
			zaza.setFluid(FluidStack.EMPTY);
			zaza.readFromNBT(level.registryAccess(), (CompoundTag) value);
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return zaza;
	}

	/// IFluidHandler ///

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return zaza.getFluid();
	}

	@Override
	public int getTankCapacity(int tank) {
		return zaza.getCapacity();
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return zaza.drain(resource, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return zaza.drain(maxDrain, action);
	}
}
