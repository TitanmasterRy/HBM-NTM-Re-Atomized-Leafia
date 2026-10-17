package com.leafia.gametest;

import java.util.List;

import com.leafia.contents.machines.elevators.car.HitSrf;
import com.leafia.contents.machines.elevators.car.styles.EvWallBase;
import com.leafia.contents.machines.elevators.car.styles.panels.EvGenericDoorBase;
import com.leafia.contents.machines.elevators.car.styles.panels.S6Door;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The elevator cabin's collision panels — the hit surfaces a car pushes entities off.
 *
 * <p>None of this is reachable in game yet: nothing spawns an elevator car, because
 * {@code ElevatorEntity} is the middle of the 42-file unit D-1033 scopes. These panels are the part
 * of it that compiles on its own, and they are pure geometry, so a test is the <em>only</em> thing
 * that will notice a wrong number until the car lands — at which point a wrong number reads as
 * "players fall out of the cabin" and not as anything that names this file.
 *
 * <p>The tests take a {@link GameTestHelper} but never touch the level: they are arithmetic. They
 * live here rather than in a plain unit test because that is the only harness this project runs.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaElevatorPanelTests {

	private static final String EMPTY = "empty";
	private static final double EPS = 1e-9;

	/**
	 * {@link HitSrf}'s constructor takes {@code x0, y0, x1, y1} while its field list reads
	 * {@code x0, x1, y0, y1}. Transcribing the field order into the signature builds surfaces with
	 * {@code x0 > x1}, whose bounds checks can never pass — so the panel stops colliding silently
	 * rather than colliding wrongly.
	 */
	@GameTest(template = EMPTY)
	public static void theConstructorTakesX0Y0X1Y1(GameTestHelper helper) {

		HitSrf srf = new HitSrf(new FiaMatrix(), -1, -2, 3, 4, 0.5);

		helper.assertTrue(srf.x0 == -1, "x0 is the second argument, got " + srf.x0);
		helper.assertTrue(srf.y0 == -2, "y0 is the third argument, got " + srf.y0);
		helper.assertTrue(srf.x1 == 3, "x1 is the fourth argument, got " + srf.x1);
		helper.assertTrue(srf.y1 == 4, "y1 is the fifth argument, got " + srf.y1);
		helper.assertTrue(srf.getDepth() == 0.5, "depth is the sixth argument, got " + srf.getDepth());

		helper.assertTrue(srf.x0 < srf.x1 && srf.y0 < srf.y1,
				"a surface built from these arguments must not come out inside-out");
		helper.assertTrue(srf.type == 0, "and a surface is a wall until something says otherwise");
		helper.assertTrue(srf.enabled, "and is enabled until a door switches it off");

		helper.succeed();
	}

	/** A blank cabin wall is one sheet: full width, taller than the doorway, and a wall by type. */
	@GameTest(template = EMPTY)
	public static void theBlankWallIsOneFullWidthSheet(GameTestHelper helper) {

		List<HitSrf> surfaces = new EvWallBase(0).getHitSurfaces();

		helper.assertTrue(surfaces.size() == 1, "a blank wall is one surface, got " + surfaces.size());

		HitSrf wall = surfaces.get(0);
		helper.assertTrue(Math.abs((wall.x1 - wall.x0) - 30 / 16D) < EPS,
				"the wall spans 30/16, got " + (wall.x1 - wall.x0));
		helper.assertTrue(Math.abs(wall.y0) < EPS && Math.abs(wall.y1 - 36 / 16D) < EPS,
				"and stands from 0 to 36/16, got " + wall.y0 + ".." + wall.y1);
		helper.assertTrue(wall.type == 0, "and is a wall, got type " + wall.type);

		helper.succeed();
	}

	/**
	 * The doorway: six surfaces, and the hole in the middle is 14/16 wide and 32/16 tall.
	 *
	 * <p>The door leaf, the lintel, and the inner faces of the two side walls all have to agree on
	 * that opening — four numbers written out separately upstream. If one of them drifts the cabin
	 * grows a lip a player catches on, or a gap they fall through.
	 */
	@GameTest(template = EMPTY)
	public static void theDoorwayIsFourteenBySixteenthsWideAndThirtyTwoTall(GameTestHelper helper) {

		List<HitSrf> surfaces = new S6Door(0).getHitSurfaces();

		helper.assertTrue(surfaces.size() == 6,
				"a door panel is door + two walls + two returns + lintel = 6, got " + surfaces.size());

		HitSrf door = surfaces.get(0);
		helper.assertTrue(Math.abs(door.x0 + 7 / 16D) < EPS && Math.abs(door.x1 - 7 / 16D) < EPS,
				"the door leaf spans -7/16..7/16, got " + door.x0 + ".." + door.x1);
		helper.assertTrue(Math.abs(door.y1 - 32 / 16D) < EPS,
				"and is 32/16 tall, got " + door.y1);
		helper.assertTrue(Math.abs(door.getDepth() - 1 / 16D) < EPS,
				"and is the thin one, 1/16, got " + door.getDepth());

		HitSrf lintel = surfaces.get(5);
		helper.assertTrue(Math.abs(lintel.x0 + 7 / 16D) < EPS && Math.abs(lintel.x1 - 7 / 16D) < EPS,
				"the lintel must span exactly the doorway, got " + lintel.x0 + ".." + lintel.x1);

		// The side returns are the two 32/16-tall surfaces that are not the door leaf.
		for(int i : new int[]{2, 4}) {
			helper.assertTrue(Math.abs(surfaces.get(i).y1 - 32 / 16D) < EPS,
					"side return " + i + " must stop at the lintel, got " + surfaces.get(i).y1);
			helper.assertTrue(Math.abs(surfaces.get(i).getDepth() - 8 / 16D) < EPS,
					"side return " + i + " is 8/16 thick, got " + surfaces.get(i).getDepth());
		}

		// The two outer sheets carry on past the doorway to the wall's full height.
		for(int i : new int[]{1, 3}) {
			helper.assertTrue(Math.abs(surfaces.get(i).y1 - 36 / 16D) < EPS,
					"side wall " + i + " runs the full 36/16, got " + surfaces.get(i).y1);
			helper.assertTrue(Math.abs((surfaces.get(i).x1 - surfaces.get(i).x0) - 8 / 16D) < EPS,
					"side wall " + i + " is 8/16 wide, got " + (surfaces.get(i).x1 - surfaces.get(i).x0));
		}

		for(HitSrf srf : surfaces) {
			helper.assertTrue(srf.x0 < srf.x1 && srf.y0 < srf.y1,
					"no surface may come out inside-out: " + srf.x0 + ".." + srf.x1 + " / " + srf.y0 + ".." + srf.y1);
		}

		helper.succeed();
	}

	/**
	 * The door panel is not left-right symmetric by accident — it is built from two mirrored
	 * halves, and a copy-paste that leaves both sides on {@code -7/16} collapses the cabin onto
	 * itself with every other assertion above still passing.
	 */
	@GameTest(template = EMPTY)
	public static void theTwoSidesOfTheDoorwayAreMirrored(GameTestHelper helper) {

		List<HitSrf> surfaces = new S6Door(0).getHitSurfaces();

		HitSrf left = surfaces.get(1);
		HitSrf right = surfaces.get(3);

		helper.assertTrue(Math.abs(left.x0 + right.x1) < EPS && Math.abs(left.x1 + right.x0) < EPS,
				"the two side walls must mirror about x=0, got " + left.x0 + ".." + left.x1
						+ " and " + right.x0 + ".." + right.x1);

		helper.assertTrue(Math.abs(surfaces.get(2).mat.getX() + surfaces.get(4).mat.getX()) < EPS,
				"and so must the two returns they sit on, got " + surfaces.get(2).mat.getX()
						+ " and " + surfaces.get(4).mat.getX());

		helper.assertTrue(surfaces.get(2).mat.frontVector.distanceTo(surfaces.get(4).mat.frontVector) > EPS,
				"the returns face each other across the doorway, so they must not share a normal");

		helper.succeed();
	}

	/** The S6 panel's own two numbers, and the units they are in. */
	@GameTest(template = EMPTY)
	public static void theS6PanelPutsItsButtonsLeftOfCentre(GameTestHelper helper) {

		S6Door door = new S6Door(90);

		helper.assertTrue(door.getId().equals("s6door"), "got " + door.getId());
		helper.assertTrue(door.getStaticX() == -11, "got " + door.getStaticX());
		helper.assertTrue(Math.abs(door.getStaticZ() - 0.9365) < EPS, "got " + door.getStaticZ());
		helper.assertTrue(door.rotation == 90, "a panel keeps the angle it was built with");

		// getStaticX is sixteenths and getStaticZ is blocks - upstream's mismatch, and the thing a
		// reader is most likely to "fix" into a cabin whose buttons sit eleven blocks away.
		helper.assertTrue(Math.abs(door.getStaticX() / 16D) < 1D,
				"read as sixteenths, the buttons are inside the cabin");
		helper.assertTrue(door.getStaticZ() < 1D,
				"read as blocks, so is the panel face");

		helper.succeed();
	}

	/**
	 * A point inside the door leaf is pushed back out of it; a point beside the doorway is left
	 * alone.
	 *
	 * <p>This is the one behavioural test of {@link HitSrf#collideVector}, and it is what catches
	 * the bounds checks being wired to the wrong fields — a swap that leaves all the geometry above
	 * correct and still lets a player walk through the door.
	 */
	@GameTest(template = EMPTY)
	public static void aPointInsideTheDoorIsPushedOutAndOneBesideItIsNot(GameTestHelper helper) {

		HitSrf door = new S6Door(0).getHitSurfaces().get(0);

		/*
		 * Where the leaf actually is, for a car at the origin facing yaw 0: the panel's base matrix
		 * is 15/16 out, the leaf adds 4/16 on top of that, and collideVector itself steps half the
		 * 1/16 thickness further. So the plane sits at z = -(15+4)/16 - 1/32, and a point counts as
		 * inside only within depth/2 + length of it - here 1/32 + 0.15.
		 */
		final double plane = -19 / 16D - 1 / 32D;
		final double reach = 1 / 32D + 0.15;

		Vec3 inside = new Vec3(0, 1, plane + reach / 2);
		Vec3 pushed = HitSrf.collideVector(inside, new Vec3(0, 0, -0.1), 0.15, door, 0, 0, 0, 0);

		helper.assertTrue(pushed != null,
				"a point in the doorway's plane must collide, got null");
		helper.assertTrue(pushed.distanceTo(inside) > EPS,
				"and must actually be moved, it came back at " + pushed);

		// Twice the reach away is clear of it, which pins the thickness rather than just the plane.
		helper.assertTrue(HitSrf.collideVector(new Vec3(0, 1, plane + reach * 2), new Vec3(0, 0, -0.1),
						0.15, door, 0, 0, 0, 0) == null,
				"a point beyond depth/2 + length must not collide");

		// Two blocks to the side is past x1 by a long way, whichever field the check reads.
		Vec3 beside = new Vec3(2, 1, plane + reach / 2);
		helper.assertTrue(HitSrf.collideVector(beside, new Vec3(0, 0, -0.1), 0.15, door, 0, 0, 0, 0) == null,
				"a point clear of the doorway must not collide");

		// And well below the cabin floor is past y0.
		Vec3 below = new Vec3(0, -3, plane + reach / 2);
		helper.assertTrue(HitSrf.collideVector(below, new Vec3(0, 0, -0.1), 0.15, door, 0, 0, 0, 0) == null,
				"a point below the panel must not collide");

		// And above the doorway is past y1, which is the assertion that fails if y1 is read as x1.
		helper.assertTrue(HitSrf.collideVector(new Vec3(0, 32 / 16D + 1, plane + reach / 2),
						new Vec3(0, 0, -0.1), 0.15, door, 0, 0, 0, 0) == null,
				"a point above the doorway must not collide");

		helper.succeed();
	}

	/**
	 * {@link HitSrf#killVelocity} takes the component pointing into the surface out and leaves the
	 * rest — without it an entity shoved out of a wall is pushed straight back in next tick.
	 */
	@GameTest(template = EMPTY)
	public static void killVelocityRemovesOnlyTheComponentIntoTheSurface(GameTestHelper helper) {

		HitSrf door = new S6Door(0).getHitSurfaces().get(0);

		// Moving into the panel and sideways along it at once.
		Vec3 killed = HitSrf.killVelocity(new Vec3(0.3, 0, -0.4), door, 0);

		helper.assertTrue(Math.abs(killed.z) < 1e-6,
				"the component into the panel must be gone, got " + killed.z);
		helper.assertTrue(Math.abs(killed.x - 0.3) < 1e-6,
				"and the component along it must survive, got " + killed.x);

		// Sliding purely along the surface is untouched.
		Vec3 along = new Vec3(0.3, 0.2, 0);
		helper.assertTrue(HitSrf.killVelocity(along, door, 0).distanceTo(along) < 1e-6,
				"a velocity already parallel to the panel must come back unchanged");

		helper.succeed();
	}

	/**
	 * Every generic door panel gets the same geometry, whatever style extends it — the six
	 * surfaces are {@link EvGenericDoorBase}'s, not {@link S6Door}'s.
	 */
	@GameTest(template = EMPTY)
	public static void theGeometryBelongsToTheGenericDoorNotToS6(GameTestHelper helper) {

		EvGenericDoorBase other = new EvGenericDoorBase(180) {
			@Override public String getId() { return "test"; }
			@Override public int getStaticX() { return 0; }
		};

		List<HitSrf> s6 = new S6Door(0).getHitSurfaces();
		List<HitSrf> theirs = other.getHitSurfaces();

		helper.assertTrue(s6.size() == theirs.size(), "both must give the same count");

		for(int i = 0; i < s6.size(); i++) {
			helper.assertTrue(s6.get(i).x0 == theirs.get(i).x0 && s6.get(i).x1 == theirs.get(i).x1
							&& s6.get(i).y0 == theirs.get(i).y0 && s6.get(i).y1 == theirs.get(i).y1,
					"surface " + i + " must not depend on the style");
		}

		// And each call hands out its own surfaces, or one open door would open every cabin's.
		helper.assertTrue(new S6Door(0).getHitSurfaces().get(0) != s6.get(0),
				"getHitSurfaces must build a fresh list: the car mutates HitSrf.enabled on it");

		helper.succeed();
	}
}
