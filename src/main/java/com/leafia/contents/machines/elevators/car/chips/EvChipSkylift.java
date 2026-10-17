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
 * The Skylift controller: a slow, long-braking lift with a doorbell and a 5-second door timer.
 *
 * <p>Upstream: {@code .../car/chips/EvChipSkylift.java}. Its three numbers are what separate it
 * from the S6 — {@code accelerateTime} 3 seconds against 1.5, {@code brakeDistance} 8 blocks
 * against 2, and a 100-tick door hold — so a skylift is the express lift and the S6 the local one.
 */
public class EvChipSkylift extends EvChipBase {

	private int doorOpenTimer = 0;
	private final int doorOpenTime = 20;
	private int closeTimer = 0;
	private final int closeTime = 100;
	private float doorLevel = 0;
	private boolean closing = false;
	private boolean dinged = false;
	private boolean shouldOpen = false;

	public EvChipSkylift(ElevatorEntity entity) {
		super(entity);
		if(entity.doorOpen) doorLevel = 1;
		accelerateTime = 3;
		brakeDistance = 8;
	}

	@Override
	public String getType() { return "skylift"; }

	@Override
	public void onButtonServer(String id, @Nullable Player player, @Nullable InteractionHand hand) {

		if(id.startsWith("floor") && !entity.enabledButtons.contains(id)) {
			int floor = Integer.parseInt(id.substring(5));
			// Already here: just reopen rather than queueing a journey of length zero.
			if(entity.parkFloor == floor) { shouldOpen = true; return; }

			entity.enabledButtons.add(id);
			entity.targetFloors = entity.getTargetFloorsFromEnabledButtons();

		} else if(id.equals("close")) {
			if(entity.doorOpen) {
				closeTimer = closeTime;
				shouldOpen = false;
			}
		} else if(id.equals("open") && closing) {
			shouldOpen = true;
			closing = false;
			closeTimer = 0;
		}
	}

	@Override
	public void passiveTick() { }

	/** Doors part over a second on a sine ease, and the arrival chime plays once. */
	private void openingDoor() {

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

		LeafiaEase ease = new LeafiaEase(Ease.SINE, Direction.IO);
		if(doorLevel < 1) {
			doorLevel = Math.min(1, doorLevel + 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_IN, (float) ease.get(doorLevel));
			entity.getDataManager().set(ElevatorEntity.DOOR_OUT, (float) ease.get(doorLevel));
		} else shouldOpen = false;

		if(!dinged) {
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					LeafiaSounds.skyliftarrive.get(), SoundSource.BLOCKS, 0.35f, 1);
			dinged = true;
		}
	}

	private void closingDoor() {

		LeafiaEase ease = new LeafiaEase(Ease.SINE, Direction.IO);
		if(doorLevel > 0) {
			doorLevel = Math.max(0, doorLevel - 1 / 20f);
			entity.getDataManager().set(ElevatorEntity.DOOR_OUT, (float) ease.get(doorLevel));
			entity.getDataManager().set(ElevatorEntity.DOOR_IN, (float) ease.get(doorLevel));
		} else {
			entity.timeSinceStart = 0;
			entity.braking = false;
			entity.doorOpen = false;
			closing = false;
			doorOpenTimer = 0;
			dinged = false;
			entity.targetHeight = -1;
		}
	}

	@Override
	public void onUpdate() {

		Integer floor = entity.getFloorAtPos(
				BlockPos.containing(entity.getX(), entity.getY() + 0.5, entity.getZ()));
		Integer nextFloor = entity.getNextFloor();

		// Nothing ahead but something behind: turn around, unless the doors are open.
		if(nextFloor == null && entity.isFloorOnReverseDirection()) {
			nextFloor = entity.getNextFloor(!entity.down);
			if(!entity.doorOpen) entity.down = !entity.down;
		}

		if(nextFloor != null && !entity.doorOpen) {
			if(nextFloor.equals(floor)) {
				// Already level with the next target: nothing to re-park.
			} else if(entity.targetHeight < -0.5) entity.parkFloor = nextFloor;
		}

		double ratio = getSpeedRatio();
		if(entity.pulley != null)
			entity.setMotion(0, entity.doorOpen ? 0 : ratio, 0);

		if(!entity.doorOpen && !entity.targetFloors.isEmpty()) {
			if(entity.timeSinceStart == 0) {
				entity.getDataManager().set(ElevatorEntity.ARROW, entity.down ? -1 : 1);
				shouldOpen = true;
			}
			entity.timeSinceStart++;
		}

		// Music: up track while ascending, down track while descending, idle track when parked.
		if(nextFloor != null && floor != null && !shouldOpen)
			entity.getDataManager().set(ElevatorEntity.MUSIC,
					nextFloor > floor ? entity.getMusic().getA() : entity.getMusic().getB());
		else if(nextFloor == null)
			entity.getDataManager().set(ElevatorEntity.MUSIC, entity.getMusic().getC());

		if(entity.doorOpen && !closing) {
			if(closeTimer < closeTime)
				closeTimer++;
			else {
				closing = true;
				closeTimer = 0;
			}
		}

		if(floor != null && floor.equals(entity.parkFloor) && ratio == 0 && !closing && shouldOpen)
			openingDoor();

		if(entity.doorOpen && closing)
			closingDoor();

		// Passing a floor: update the indicator and chime.
		if(floor != null && !floor.equals(entity.getDataInteger(ElevatorEntity.FLOOR))) {
			entity.getDataManager().set(ElevatorEntity.FLOOR, floor);
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					LeafiaSounds.electronicpingshort.get(), SoundSource.BLOCKS, 0.35f, 2);
		}
	}
}
