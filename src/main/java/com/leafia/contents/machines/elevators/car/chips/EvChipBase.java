package com.leafia.contents.machines.elevators.car.chips;

import java.util.Map;

import javax.annotation.Nullable;

import com.leafia.contents.machines.elevators.car.ElevatorEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * The cabin's controller: what a chip in slot 0 does with the buttons and the motor.
 *
 * <p>Upstream: {@code .../car/chips/EvChipBase.java}. A chip owns the doors, the floor queue and
 * the speed curve; the cabin owns the physics and just asks the chip what speed it wants.
 *
 * <h2>{@link #getSpeedRatio()} is the whole of the shared behaviour</h2>
 *
 * It returns a signed fraction of full speed, and it does three things at once: ramps up over
 * {@link #accelerateTime} seconds, brakes over the last {@link #brakeDistance} blocks, and then
 * crawls the last few centimetres so the cabin lands level with the floor rather than near it.
 * Those crawl constants (0.05, 0.02, and the 0.01 stop band) are upstream's and are the difference
 * between a lift that stops and one that oscillates.
 */
public abstract class EvChipBase {

	protected final ElevatorEntity entity;

	/** Seconds to reach full speed. Overridden per chip. */
	protected double accelerateTime = 1.5;

	/** Blocks out from the target that braking begins. Overridden per chip. */
	protected int brakeDistance = 2;

	/**
	 * Upstream reads {@code entity.getEntityData()} here — 1.12.2's per-entity <em>persistent
	 * custom</em> tag, which NeoForge calls {@link net.minecraft.world.entity.Entity#getPersistentData()}
	 * and not the synched data. It is normally empty: the chip's real state arrives later, when
	 * {@code ElevatorEntity.readAdditionalSaveData} hands it {@code chipData}. Kept anyway, because
	 * a chip that overrides {@code readEntityFromNBT} and expects to be called once at construction
	 * would otherwise silently stop being.
	 */
	public EvChipBase(ElevatorEntity entity) {
		this.entity = entity;
		readEntityFromNBT(entity.getPersistentData());
	}

	public abstract String getType();

	/**
	 * How fast the cabin should be moving right now, as a signed fraction of full speed.
	 *
	 * <p>Positive is up. Zero means "stop here" — reached only inside the 0.01-block dead band, so
	 * a cabin that is 2cm out keeps creeping rather than stopping short.
	 */
	protected double getSpeedRatio() {

		Integer nextFloor = entity.getNextFloor();
		if(nextFloor == null) return 0;

		// Once past the park floor and with no target height measured, look for a landing within
		// braking distance in the direction of travel.
		if(entity.getDataInteger(ElevatorEntity.FLOOR) != entity.parkFloor && entity.targetHeight <= -0.5) {
			Map<Integer, Integer> map = entity.getFloorsInRange(brakeDistance * (entity.down ? -1 : 1));
			if(map.containsKey(nextFloor))
				entity.targetHeight = map.get(nextFloor);
			else
				entity.targetHeight = -1;
		}

		double maxRatio = 1;
		if(entity.targetHeight >= 0)
			maxRatio = Math.min(1, Math.abs(entity.targetHeight - entity.getY()) / brakeDistance);
		if(maxRatio < 1)
			entity.braking = true;

		double ratio = Math.min(maxRatio, entity.timeSinceStart / (accelerateTime * 20));

		if(entity.targetHeight >= 0
				? entity.targetHeight < entity.getY()
				: nextFloor < entity.getDataInteger(ElevatorEntity.FLOOR))
			ratio *= -1;

		// The crawl. Below 5% of full speed the ramp is abandoned and the cabin is nudged at a
		// fixed rate until it is within a centimetre, then stopped dead.
		if(Math.abs(maxRatio) < 0.05) {
			double offset = entity.targetHeight - entity.getY();
			double dir = Math.signum(offset);
			ratio = 0.05 * dir;
			if(Math.abs(offset) < 0.1)
				ratio = 0.02 * dir;
			if(Math.abs(offset) < 0.01)
				ratio = 0;
		}

		return ratio;
	}

	public abstract void onButtonServer(String id, @Nullable Player player, @Nullable InteractionHand hand);

	/** Runs every tick whether or not the pulley has power. */
	public abstract void passiveTick();

	/** Runs every tick only while the pulley is powered and the cabin is rigged. */
	public abstract void onUpdate();

	public void readEntityFromNBT(CompoundTag compound) { }

	public void writeEntityToNBT(CompoundTag compound) { }
}
