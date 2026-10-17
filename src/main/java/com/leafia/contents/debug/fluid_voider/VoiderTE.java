package com.leafia.contents.debug.fluid_voider;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fluid voider: a debug block that subscribes to every neighbouring pipe as a receiver with
 * unlimited demand, and throws away everything it is given.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/fluid_voider/VoiderTE.java}.
 *
 * <h2>Why this two-file block is worth a batch</h2>
 *
 * It is <b>the addon's first {@link LeafiaPacketReceiver}</b>. {@code LeafiaPacket} landed in the
 * previous batch with 89 files waiting on it and nothing implementing the interface at the far
 * end, so the channel was pinned against its own bytes and never against a block entity (D-1025).
 * This is the smallest thing upstream has that closes that: two writes, two keys, a receiver that
 * puts them back where they came from. The three addon pipes that landed earlier are <em>not</em>
 * this — they were deliberately moved onto the base mod's {@code networkPackNT} because they are
 * base-mod pipes (D-1015).
 *
 * <h2>What it does per tick, in upstream's order</h2>
 *
 * Subscribe on all six sides, send the client what is in the buffer, <em>then</em> empty it. The
 * order matters and is upstream's: the number a player sees in the look overlay is what the
 * voider swallowed during the previous tick, not a running total. Emptying first would show a
 * permanent zero.
 *
 * <p>The tank is {@link Integer#MAX_VALUE} millibuckets, which is how the demand it advertises
 * comes out unlimited — {@code getDemand} is {@code maxFill - fill} and the fill is back to zero
 * every tick. That is upstream's mechanism for "eats anything", not a number with balance in it.
 *
 * <h2>What it cannot do here yet</h2>
 *
 * The buffer's type starts at spent steam and is changed by right-clicking with an
 * {@code IItemFluidIdentifier}. <b>No item implements that interface in this port yet</b> — the
 * fluid ID items are unported — so today the voider only ever eats spent steam. Same hand-off as
 * the amat charger's (D-705, D-1015), and the reason the type still rides the sync packet: it is
 * upstream's slot 0 and will start moving the moment those items land.
 */
public class VoiderTE extends BlockEntity implements IFluidStandardReceiverMK2, LeafiaPacketReceiver {

	/** Upstream's {@code loaded}, cleared on unload so the fluid net drops the subscription. */
	public boolean isLoaded = true;

	public FluidTank buffer = new FluidTank(Fluids.SPENTSTEAM, Integer.MAX_VALUE);

	public VoiderTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * NeoForge's counterpart of upstream's {@code onChunkUnload}, exactly as
	 * {@code BlockEntityLoadedBase} uses it.
	 */
	@Override
	public void onChunkUnloaded() {
		this.isLoaded = false;
		super.onChunkUnloaded();
	}

	@Override
	public boolean isLoaded() {
		return this.isLoaded;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { this.buffer };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { this.buffer };
	}

	/**
	 * The server-side ticker. Upstream is an {@code ITickable} whose whole body sits inside
	 * {@code if(!world.isRemote)}; here the block hands out the ticker on the server only, which
	 * is the same thing without the per-tick branch.
	 */
	public static void tick(Level level, BlockPos pos, BlockState state, VoiderTE voider) {
		voider.updateEntity();
	}

	/** Upstream's {@code update}, kept under the base mod's method name for machines. */
	public void updateEntity() {

		for(Direction dir : Direction.values()) {
			this.trySubscribe(this.buffer.getTankType(), this.level, this.worldPosition.relative(dir), dir);
		}

		this.syncPacket().__sendToAffectedClients();

		this.buffer.setFill(0);
	}

	/**
	 * The two slots the voider syncs, split out of {@link #updateEntity} so a test can build the
	 * real packet instead of a replica of it.
	 *
	 * <p>That distinction is the whole reason this method exists. {@code LeafiaPacket} carries no
	 * version tag and no slot names — the sender writes 0 and 1, and {@link #onReceivePacketLocal}
	 * separately believes 0 and 1 mean the type and the fill. Nothing in the format checks that,
	 * so a test that re-typed the two writes would agree with itself and prove nothing. This way
	 * the bytes under test are the ones a player's client receives.
	 */
	public LeafiaPacket.Builder syncPacket() {
		return LeafiaPacket._start(this)
				.__write(0, this.buffer.getTankType().getName())
				.__write(1, this.buffer.getFill());
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.buffer.readFromNBT(nbt, "buffer");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		this.buffer.writeToNBT(nbt, "buffer");
	}

	@Override
	public String getPacketIdentifier() {
		return "DEBUG_VOIDER";
	}

	/**
	 * The client half of the sync. The two keys are upstream's and are the same numbers
	 * {@link #updateEntity} writes — nothing checks that they agree, which is precisely the
	 * hazard {@code LeafiaPacket} carries no version tag for, so a test drives a real packet
	 * through this method rather than trusting the pairing by eye.
	 */
	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) this.buffer.setTankType(Fluids.fromName((String) value));
		else if(key == 1) this.buffer.setFill((int) value);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }
}
