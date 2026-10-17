package com.leafia.contents.machines.elevators.car.chips;

import javax.annotation.Nullable;

import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonEnablePacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.init.LeafiaSounds;
import com.llib.technical.LeafiaEase;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * The Otis S6 controller: the slow local lift, with a two-stage door and a doorbell.
 *
 * <p>Upstream: {@code .../car/chips/EvChipS6.java}. It keeps {@link EvChipBase}'s default
 * {@code accelerateTime} 1.5 and {@code brakeDistance} 2, and then multiplies the speed ratio by
 * <b>0.08</b> — so an S6 crawls at eight percent of what the same ratio gives a Skylift. That one
 * number is the whole difference in feel between the two lifts and is the easiest thing here to
 * lose.
 *
 * <h2>Two door levels, not one</h2>
 *
 * The S6 drives the cabin door ({@code DOOR_IN}) and the landing door ({@code DOOR_OUT})
 * separately: they open together but close <em>outer first, then inner</em>, so the landing door
 * shuts before the cabin door does. The Skylift moves both as one. Collapsing them would look
 * almost right and be wrong on every close.
 *
 * <p>Upstream carries a ~70-line commented-out earlier version of {@code onUpdate} at the end of
 * the file. It is dead, it references {@code HBMSoundEvents} rather than the addon's own sounds,
 * and its door logic disagrees with the live code; it is not reproduced.
 */
public class EvChipS6 extends EvChipBase {

	private int bellTimer = 0;

	private int doorOpenTimer = 0;
	private final int doorOpenTime = 10;
	private float doorLevelIn = 0;
	private float doorLevelOut = 0;
	private int pingpongTimer = 0;
	private int cooldown = 0;
	private boolean closing = false;
	private int closeTimer = 0;
	private final int closeTime = 60;
	private int moveCooldown = 0;

	public EvChipS6(ElevatorEntity entity) {
		super(entity);
		if(entity.doorOpen) {
			doorLevelIn = 1;
			doorLevelOut = 1;
		}
	}

	@Override
	public String getType() { return "s6"; }

	@Override
	public void onButtonServer(String id, @Nullable Player player, @Nullable InteractionHand hand) {

		if(id.startsWith("floor") && !entity.enabledButtons.contains(id)) {

			int floor = Integer.parseInt(id.substring(5));
			// Already parked here: the S6 ignores the press entirely rather than reopening, which
			// is where it differs from the Skylift.
			if(entity.parkFloor == floor) return;

			entity.enabledButtons.add(id);
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					LeafiaSounds.s6beep.get(), SoundSource.BLOCKS, 0.35f, 1);
			entity.targetFloors = entity.getTargetFloorsFromEnabledButtons();

		} else if(id.equals("close")) {
			// Close only does something if somewhere is queued - an S6 will not shut its doors
			// while parked with nothing to do.
			if(entity.doorOpen && !entity.targetFloors.isEmpty()) {
				closeTimer = closeTime;
				cooldown = 0;
			}
		} else if(id.equals("open") && closing) {
			closing = false;
			closeTimer = 0;
			cooldown = 20;
		} else if(id.equals("bell") && bellTimer <= 0) {
			bellTimer = 65;
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					LeafiaSounds.s6bell.get(), SoundSource.BLOCKS, 5, 1);
		}
	}

	@Override
	public void passiveTick() {
		if(bellTimer > 0)
			bellTimer--;
	}

	private void openingDoor() {

		if(entity.isEnd(entity.down))
			entity.down = !entity.down;
		entity.doorOpen = true;

		if(entity.targetFloors.contains(entity.parkFloor)) {
			entity.targetFloors.remove(entity.parkFloor);
			entity.enabledButtons.remove("floor" + entity.parkFloor);
			LeafiaCustomPacket.__start(new EvButtonEnablePacket(entity)).__sendToAll();
		}

		if(doorOpenTimer < doorOpenTime) {
			doorOpenTimer++;
			return;
		}

		entity.getDataManager().set(ElevatorEntity.ARROW, entity.down ? -1 : 1);

		LeafiaEase ease = new LeafiaEase(Ease.EXPO, Direction.O);
		if(doorLevelIn < 1) {
			doorLevelIn = Math.min(1, doorLevelIn + 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_IN, (float) ease.get(doorLevelIn));
		}
		if(doorLevelOut < 1) {
			doorLevelOut = Math.min(1, doorLevelOut + 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_OUT, (float) ease.get(doorLevelOut));
			cooldown = 3 * 20;
		}

		// The two-tone arrival chime: one ping on opening, and a second lower one six ticks later
		// only when the cabin is heading down.
		if(pingpongTimer < 6) {
			if(pingpongTimer == 0)
				entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						LeafiaSounds.electronicpingshort.get(), SoundSource.BLOCKS, 0.35f, 1.7f);
			pingpongTimer++;
			if(entity.down && pingpongTimer == 6)
				entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						LeafiaSounds.electronicpingshort.get(), SoundSource.BLOCKS, 0.35f, 1.45f);
		}
	}

	private void closingDoor() {

		LeafiaEase ease = new LeafiaEase(Ease.SINE, Direction.IO);

		if(doorLevelOut > 0) {
			doorLevelOut = Math.max(0, doorLevelOut - 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_OUT, (float) ease.get(doorLevelOut));
		} else if(doorLevelIn > 0) {
			doorLevelIn = Math.max(0, doorLevelIn - 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_IN, (float) ease.get(doorLevelIn));
		} else {
			moveCooldown = 10;
			entity.timeSinceStart = 0;
			entity.braking = false;
			entity.doorOpen = false;
			entity.getDataManager().set(ElevatorEntity.ARROW, 0);
			closing = false;
			doorOpenTimer = 0;
			pingpongTimer = 0;
			entity.targetHeight = -1;
		}
	}

	@Override
	public void onUpdate() {

		Integer floor = entity.getFloorAtPos(
				BlockPos.containing(entity.getX(), entity.getY() + 0.5, entity.getZ()));
		Integer nextFloor = entity.getNextFloor();

		if(nextFloor == null && entity.isFloorOnReverseDirection()) {
			nextFloor = entity.getNextFloor(!entity.down);
			if(!entity.doorOpen) entity.down = !entity.down;
		}

		if(nextFloor != null && !entity.doorOpen) {
			if(nextFloor.equals(floor)) {
				// Already level with the next target.
			} else if(entity.targetHeight < -0.5) entity.parkFloor = nextFloor;
		}

		if(cooldown > 0) cooldown--;

		double ratio = getSpeedRatio();
		if(entity.pulley != null)
			entity.setMotion(0, entity.doorOpen ? 0 : ratio * 0.08, 0);

		if(nextFloor != null && !entity.doorOpen) {
			if(moveCooldown > 0) {
				moveCooldown--;
				if(moveCooldown <= 0 && floor != null)
					entity.getDataManager().set(ElevatorEntity.MUSIC,
							nextFloor > floor ? entity.getMusic().getA() : entity.getMusic().getB());
			} else
				entity.timeSinceStart++;
		}

		if(nextFloor != null && entity.doorOpen && !closing && cooldown <= 0) {
			if(closeTimer < closeTime)
				closeTimer++;
			else {
				closing = true;
				closeTimer = 0;
			}
		}

		if(floor != null && floor.equals(entity.parkFloor) && !closing) {
			entity.getDataManager().set(ElevatorEntity.MUSIC, entity.getMusic().getC());
			if(ratio == 0) {
				openingDoor();
				entity.timeSinceStart = 0;
			}
		}

		if(entity.doorOpen && closing)
			closingDoor();

		if(floor != null && !floor.equals(entity.getDataInteger(ElevatorEntity.FLOOR))) {
			entity.getDataManager().set(ElevatorEntity.FLOOR, floor);
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					LeafiaSounds.s6beep.get(), SoundSource.BLOCKS, 0.35f, 1);
		}
	}
}
