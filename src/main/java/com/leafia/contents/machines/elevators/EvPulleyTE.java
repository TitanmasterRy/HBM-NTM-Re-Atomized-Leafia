package com.leafia.contents.machines.elevators;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.weight.EvWeightEntity;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The winch at the top of a shaft: it draws power, and it keeps the counterweight exactly as far
 * below itself as the cabin is, mirrored.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvPulleyTE.java}.
 *
 * <h2>The two setup distances are the whole mechanism</h2>
 *
 * {@link #setupDistCabin} and {@link #setupDistWeight} are how far below the pulley each of the
 * two hung things was when it first attached. After that the weight is placed by arithmetic
 * rather than by physics: the cabin moves, the difference from its setup distance is the offset,
 * and the weight goes the same offset the other way. Both reset to {@code -1} when their entity
 * dies, which is the flag meaning "not measured yet" — so a cabin that is destroyed and replaced
 * re-measures instead of inheriting the old one's rope length.
 *
 * <h2>Power</h2>
 *
 * {@link #consumption} is <b>10 HE/t</b> and the buffer is five ticks of it, upstream's numbers.
 * The pulley subscribes to whatever is one block down and to the side of it, and burns its
 * consumption every tick it can afford to — unconditionally, whether or not the cabin moves, which
 * is upstream's behaviour and not an oversight worth correcting (standing rule 4).
 *
 * <p>Upstream computes the side to subscribe on from {@code getBlockMetadata()-10}, 1.12.2's
 * packed multiblock rotation. This port's {@link BlockDummyable} carries the direction as a real
 * {@link BlockDummyable#DIR} property, so the same side is {@code DIR} rotated once about Y — see
 * D-1037.
 */
public class EvPulleyTE extends BlockEntityLoadedBase implements IEnergyReceiverMK2, LeafiaPacketReceiver {

	/** The cabin hanging off this pulley, once one has found it. Server-side. */
	public ElevatorEntity elevator;

	/** The counterweight on the other end of the rope. */
	public EvWeightEntity counterweight;

	/** How far below the pulley the cabin was when it attached; {@code -1} until measured. */
	public double setupDistCabin = -1;

	/** The same for the counterweight. */
	public double setupDistWeight = -1;

	/** HE per tick. Upstream's number, and the unit the whole elevator's power budget is in. */
	public static long consumption = 10;

	private long power = 0;

	public EvPulleyTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.EV_PULLEY.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvPulleyTE te) {
		te.updateEntity();
	}

	public void updateEntity() {

		if(level.isClientSide) return;

		if(elevator != null && !elevator.isAlive()) {
			setupDistCabin = -1;
			elevator = null;
		}
		if(counterweight != null && !counterweight.isAlive()) {
			setupDistWeight = -1;
			counterweight = null;
		}

		if(counterweight != null && elevator != null) {
			double distEv = worldPosition.getY() - elevator.getY();
			double offset = distEv - setupDistCabin;
			counterweight.setPos(counterweight.getX(),
					worldPosition.getY() - setupDistWeight + offset,
					counterweight.getZ());
		}

		Direction face = getState().getValue(BlockDummyable.DIR);
		// Upstream is 1.12.2 and says ForgeDirection.DOWN.toEnum(); this port has no
		// ForgeDirection at all, vanilla's Direction replaced it wholesale. D-1039.
		trySubscribe(level, worldPosition.relative(rotateY(face.getOpposite())).below(), Direction.DOWN);

		if(power >= consumption)
			power -= consumption;

		LeafiaPacket._start(this).__write(0, power).__sendToAffectedClients();
	}

	/** Upstream's {@code EnumFacing.rotateY()}, which 1.21.1's {@link Direction} calls clockwise. */
	private static Direction rotateY(Direction dir) {
		return dir.getClockWise();
	}

	private BlockState getState() {
		return level.getBlockState(worldPosition);
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return consumption * 5;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("setupDistCabin"))
			setupDistCabin = compound.getDouble("setupDistCabin");
		if(compound.contains("setupDistWeight"))
			setupDistWeight = compound.getDouble("setupDistWeight");
		power = compound.getLong("power");
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putDouble("setupDistCabin", setupDistCabin);
		compound.putDouble("setupDistWeight", setupDistWeight);
		compound.putLong("power", power);
	}

	/**
	 * 256 blocks, upstream's. A shaft can be most of a world tall and a player at the bottom still
	 * wants the pulley's power state.
	 */
	@Override
	public double affectionRange() {
		return 256;
	}

	@Override
	public String getPacketIdentifier() {
		return "EV_PULLEY";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0)
			power = (long) value;
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	/**
	 * Nothing: the pulley pushes its power every tick, so there is no rare state a joining player
	 * would miss. That matters because {@code onPlayerValidate} is dead in this port for every
	 * receiver — its client sweep lives in the unported {@code LeafiaClientListener} (D-1029) — and
	 * this is one of the receivers that does not care.
	 */
	@Override
	public void onPlayerValidate(Player plr) { }
}
