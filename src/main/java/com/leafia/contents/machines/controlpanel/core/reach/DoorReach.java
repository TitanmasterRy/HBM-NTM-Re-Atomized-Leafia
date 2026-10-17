package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Collections;
import java.util.List;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.machine.BlockEntityBlastDoor;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The two doors NTM-CE made controllable ({@code TileEntityDoorGeneric}, {@code TileEntityBlastDoor}), as adapters (batch 8,
 * D-3068). In {@code door_toggle}; out {@code door_open_state} carrying {@code state}, which in CE is {@code DoorState.ordinal()}:
 * {@code 0} closed, {@code 1} open, {@code 2} closing, {@code 3} opening.
 *
 * <p>The generic door's byte is already in that order. The blast door's is not: the base keeps closed / transitioning / open and a
 * separate {@code isOpening}, so {@link Blast#ordinal} maps it back (transitioning is {@code 3} while opening and {@code 2}
 * while closing), and a panel reads the same four values from either door.
 *
 * <h2>The lock</h2>
 *
 * CE's generic door honours the event's {@code passcode} (default {@code 0}); a locked door opens only for its pin. Its blast door
 * ignores the passcode and the lock altogether, so any linked panel could open a locked blast door. The base mod's own redstone
 * path gates on the lock ({@code !isLocked() && powered}), so the blast door's adapter takes the generic door's rule: a locked
 * door obeys the event only when the pin matches. A divergence, hardening a path a modified client can author (D-1093 #4).
 */
public final class DoorReach {

	private DoorReach() {}

	public static final String IN = "door_toggle";
	public static final String OUT = "door_open_state";

	private static ControlEvent state(int ordinal) {
		return ControlEvent.newEvent(OUT).setVar("state", ordinal);
	}

	/** {@code BlockEntityDoorGeneric}: the vault, fire, sliding and silo doors. */
	public static final class Generic extends ReachAdapter<BlockEntityDoorGeneric> {

		public Generic(BlockEntityDoorGeneric be) {
			super(be);
		}

		@Override public List<String> getInEvents() { return Collections.singletonList(IN); }
		@Override public List<String> getOutEvents() { return Collections.singletonList(OUT); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals(IN)) {
				// CE: passcode is -1 unless the event carries one; a registered event always does (default 0).
				int passcode = e.vars.containsKey("passcode") && e.vars.get("passcode") != null ? (int) e.vars.get("passcode").getNumber() : -1;
				be.tryToggle(passcode);
			}
		}

		@Override public boolean polled() { return true; }
		@Override public Object snapshot() { return (int) be.state; }

		@Override
		public List<ControlEvent> diff(Object prev, Object now) {
			return List.of(state((Integer) now));
		}
	}

	/** {@code BlockEntityBlastDoor}. */
	public static final class Blast extends ReachAdapter<BlockEntityBlastDoor> {

		public Blast(BlockEntityBlastDoor be) {
			super(be);
		}

		/** CE's {@code DoorState.ordinal()} for the base's three-state byte. */
		public int ordinal() {
			return switch(be.state) {
				case BlockEntityBlastDoor.STATE_OPEN -> 1;
				case BlockEntityBlastDoor.STATE_TRANSITIONING -> be.isOpening ? 3 : 2;
				default -> 0;
			};
		}

		@Override public List<String> getInEvents() { return Collections.singletonList(IN); }
		@Override public List<String> getOutEvents() { return Collections.singletonList(OUT); }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals(IN)) {
				Level level = be.getLevel();
				if(level == null || level.isClientSide)
					return;
				if(be.isLocked() && whole(e, "passcode") != be.getPins())
					return;
				be.tryToggle();
			}
		}

		@Override public boolean polled() { return true; }
		@Override public Object snapshot() { return ordinal(); }

		@Override
		public List<ControlEvent> diff(Object prev, Object now) {
			return List.of(state((Integer) now));
		}
	}
}
