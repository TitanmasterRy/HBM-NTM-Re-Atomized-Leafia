package com.leafia.contents.network.pipe_amat.charger;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.uninos.UniNodespace;
import com.leafia.contents.network.pipe_amat.AmatDuctTE;
import com.leafia.contents.network.pipe_amat.uninos.AmatNet;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The antimatter pipe's charger: the thing that keeps the magnetic field up.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/charger/AmatDuctChargerTE.java}. It
 * is an {@link AmatDuctTE} — it sits on the amat network like any other length of pipe, carries
 * a fluid type, and connects the same way — that also draws HE from the base mod's power
 * network and pours it into {@link AmatNet#power}. Without one, a net drains from
 * {@link AmatNet#maxPower} to nothing in twenty ticks and never recovers, which is the state
 * the pipe shipped in before this landed (D-1014).
 *
 * <h2>Sync, which is where this diverges from upstream</h2>
 *
 * Upstream broadcasts its HE buffer to nearby clients every tick through {@code LeafiaPacket},
 * which is unported and is a packet-layer decision nobody has taken (D-1008). The base mod has
 * the same channel in a shape this port already uses everywhere:
 * {@link #serialize}/{@link #deserialize} over {@code networkPackNT}. So the buffer rides that
 * instead, at the same cadence and the same 15-block range the base mod's own capacitor uses.
 * Nothing about what the player sees changes; the wire does.
 *
 * <p><b>The type is not written twice.</b> Upstream saves it under {@code "filter"} on top of
 * the parent's {@code "type"}, then reads {@code "filter"} back over what the parent just
 * loaded — the same value by two names. The parent's is kept and the duplicate dropped, which
 * is a redundancy rather than a behaviour.
 */
public class AmatDuctChargerTE extends AmatDuctTE implements IEnergyReceiverMK2 {

	/** The HE buffer. Upstream's number. */
	public static long maxPower = 10000;

	private long power = 0;

	public AmatDuctChargerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * Upstream's {@code update}, in its order: subscribe to whatever power conductors touch us,
	 * make sure the amat node exists, then hand the field whatever it will take.
	 *
	 * <p>The transfer is uncapped per tick on purpose — upstream moves {@code min(power, demand)}
	 * in one go, so a full buffer refills an empty field instantly and then coasts. That is the
	 * balance, not an oversight: the field costs 50 a tick and the buffer holds 10,000, so one
	 * charger carries a network for two hundred ticks between top-ups.
	 */
	@Override
	public void updateEntity() {

		if(level == null) return;

		if(level.isClientSide) {
			super.updateEntity();
			return;
		}

		for(Direction dir : Direction.values()) {
			trySubscribe(level, worldPosition.relative(dir), dir);
		}

		super.updateEntity();

		if(amatNode != null && amatNode.net != null) {

			long demand = AmatNet.maxPower - amatNode.net.power;
			long transfer = Math.min(power, demand);

			if(transfer > 0) {
				power -= transfer;
				amatNode.net.power += transfer;
			}
		}

		networkPackNT(15);
	}

	/** Upstream's {@code setType}, which is the parent's plus a client resync. */
	@Override
	public void setType(FluidType type) {
		super.setType(type);
		if(level != null && !level.isClientSide) networkPackNT(15);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();

		if(level != null && !level.isClientSide) {
			UniNodespace.destroyNode(level, worldPosition, AmatNet.getProvider(getFluidType()));
		}
	}

	/** What the look overlay prints. */
	public long getStoredPower() {
		return this.power;
	}

	/// IEnergyReceiverMK2 ///

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return maxPower; }

	/// NBT AND SYNC ///

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.power = nbt.getLong("power");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putLong("power", this.power);
	}

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(this.power);
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		this.power = buf.readLong();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, AmatDuctChargerTE charger) {
		charger.updateEntity();
	}
}
