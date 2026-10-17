package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.machine.rbmk.BlockEntityCraneConsole;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * The RBMK crane console ({@code TileEntityRBMKCraneConsole}) as an adapter (batch 8, D-3068).
 *
 * <ul>
 * <li>query {@code posX} (the crane's left offset, negated) and {@code posY} (its front offset), only while a crane is set up. The
 * plan's draft listed rod and temperature keys here as well; those are the console's OpenComputers table, not the panel's, and CE's
 * {@code getQueryData} has two keys;</li>
 * <li>in and out {@code rbmk_crane_move} ({@code up}, {@code down}, {@code left}, {@code right}) and {@code rbmk_crane_load}.</li>
 * </ul>
 *
 * <h2>Moving the crane from a panel</h2>
 *
 * CE latched the four flags and let the console move at 0.05 a tick for its 20-tick cooldown, so one event is a one-block move in
 * a direction. The base console has no such latch: it moves only while a player stands at the desk holding a key. The adapter
 * applies the whole block at once ({@value #STEP} per event, clamped to the crane's span as the console clamps every tick) and
 * echoes the event with the flags it applied, as CE's {@code processInput} did (opposite flags cancel; nothing is echoed when
 * nothing moves). The base's client eases toward the new position, so it is a short glide on the screen rather than a jump.
 * {@code rbmk_crane_load} sends the hook down ({@code startLoading}), under CE's guard: a crane that is set up and not already
 * loading.
 *
 * <h2>Out-events by polling</h2>
 *
 * The base console does not announce anything, so {@link BaseReachPoller} diffs a snapshot. {@code rbmk_crane_move} is sent when
 * the position changed, with the direction the position moved in (a key held at the desk sends one per tick, as CE did).
 * {@code rbmk_crane_load} is sent when the hook finishes going down, which is the tick {@code goesDown} turns false: CE sent it at
 * the same moment. Two loads cannot merge (a load takes 25 ticks); a move and a load in one tick are both sent. Standing still
 * at the span limit with a key held sends nothing, where CE kept announcing the key.
 */
public final class CraneConsoleReach extends ReachAdapter<BlockEntityCraneConsole> {

	public static final String MOVE = "rbmk_crane_move";
	public static final String LOAD = "rbmk_crane_load";

	/** CE's {@code speed} times its {@code coolDown}: the distance one latched flag covered. */
	public static final double STEP = 0.05D * 20;

	public CraneConsoleReach(BlockEntityCraneConsole be) {
		super(be);
	}

	@Override
	public Map<String, DataValue> getQueryData() {
		Map<String, DataValue> data = map();
		if(be.setUpCrane) {
			data.put("posX", num((float) -be.posLeft));
			data.put("posY", num((float) be.posFront));
		}
		return data;
	}

	@Override public List<String> getInEvents() { return Arrays.asList(MOVE, LOAD); }
	@Override public List<String> getOutEvents() { return Arrays.asList(MOVE, LOAD); }

	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		switch(e.name) {
		case MOVE -> {
			if(!be.setUpCrane || be.isCraneLoading())
				return;
			boolean up = flag(e, "up", false);
			boolean down = flag(e, "down", false);
			boolean left = flag(e, "left", false);
			boolean right = flag(e, "right", false);
			if(up == down) {
				up = false;
				down = false;
			}
			if(left == right) {
				left = false;
				right = false;
			}
			if(!(up || down || left || right))
				return;
			if(up) be.posFront += STEP;
			if(down) be.posFront -= STEP;
			if(left) be.posLeft += STEP;
			if(right) be.posLeft -= STEP;
			clamp();
			be.setChanged();
			echo(move(up, down, left, right));
		}
		case LOAD -> {
			if(be.setUpCrane && !be.isCraneLoading())
				be.startLoading();
		}
		default -> { }
		}
	}

	private void clamp() {
		be.posFront = Mth.clamp(be.posFront, -be.spanB, be.spanF);
		be.posLeft = Mth.clamp(be.posLeft, -be.spanR, be.spanL);
	}

	private static ControlEvent move(boolean up, boolean down, boolean left, boolean right) {
		return ControlEvent.newEvent(MOVE).setVar("up", up).setVar("down", down).setVar("left", left).setVar("right", right);
	}

	// ------------------------------------------------------------------ polling

	/** What the poller compares: a crane that is not set up has no position to report. */
	public record Pose(boolean setUp, double front, double left, boolean goingDown) {}

	@Override public boolean polled() { return true; }

	@Override
	public Object snapshot() {
		return new Pose(be.setUpCrane, be.posFront, be.posLeft, be.isGoingDown());
	}

	@Override
	public List<ControlEvent> diff(Object prev, Object now) {
		Pose a = (Pose) prev;
		Pose b = (Pose) now;
		List<ControlEvent> out = new ArrayList<>(2);
		if(a.setUp() && b.setUp()) {
			boolean up = b.front() > a.front();
			boolean down = b.front() < a.front();
			boolean left = b.left() > a.left();
			boolean right = b.left() < a.left();
			if(up || down || left || right)
				out.add(move(up, down, left, right));
			if(a.goingDown() && !b.goingDown())
				out.add(ControlEvent.newEvent(LOAD));
		}
		return out;
	}
}
