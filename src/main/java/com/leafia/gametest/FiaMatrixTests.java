package com.leafia.gametest;

import com.custom_hbm.util.LCETuple;
import com.leafia.dev.math.FiaBB;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.leafia.dev.math.FiaMatrix.TupleRotation;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the addon's transform maths (L2): {@link FiaMatrix}, {@link FiaBB} and
 * {@link LCETuple}.
 *
 * <p>{@code FiaMatrix} is a position plus a right/up/front basis, and 32 upstream files
 * position something with it — elevator cars, turbine blades, DFC beams. A sign flipped in one
 * of the rotation methods would not fail to compile and would not throw; it would put a lift
 * shaft through a wall three batches from now. The round trips below are the cheapest place to
 * catch that.
 *
 * <p>{@link #rotationOrderEnumBuildsTheAxisOrderItsNameClaims} is the least obvious of them.
 * {@code RotationOrder}'s constructor derives an axis-order array from the enum's own ordinal
 * with two swaps, rather than declaring it — clever, and exactly the kind of thing a port
 * transcribes wrongly without noticing, because every value still compiles to *an* order.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class FiaMatrixTests {

	private static final String EMPTY = "empty";

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-9,
				what + ": expected " + expected + ", got " + actual);
	}

	private static void nearVec(GameTestHelper helper, Vec3 actual, double x, double y, double z, String what) {
		helper.assertTrue(Math.abs(actual.x - x) < 1e-9 && Math.abs(actual.y - y) < 1e-9
						&& Math.abs(actual.z - z) < 1e-9,
				what + ": expected (" + x + ", " + y + ", " + z + "), got " + actual);
	}

	/** The identity basis, which every rotation is measured against. */
	@GameTest(template = EMPTY)
	public static void freshMatrixIsTheIdentityBasis(GameTestHelper helper) {

		FiaMatrix m = new FiaMatrix();
		nearVec(helper, m.position, 0, 0, 0, "position");
		nearVec(helper, m.rightVector, 1, 0, 0, "right is +X");
		nearVec(helper, m.upVector, 0, 1, 0, "up is +Y");
		nearVec(helper, m.frontVector, 0, 0, -1, "front is -Z, as Minecraft faces");

		helper.succeed();
	}

	/**
	 * A quarter turn about each axis, checked on the basis vectors rather than on a rendered
	 * result. These are the three methods everything else composes out of.
	 */
	@GameTest(template = EMPTY)
	public static void quarterTurnsMoveTheBasisVectors(GameTestHelper helper) {

		// Yaw: front swings from -Z towards -X.
		FiaMatrix y = new FiaMatrix().rotateY(90);
		nearVec(helper, y.frontVector, -1, 0, 0, "rotateY(90) front");
		nearVec(helper, y.rightVector, 0, 0, -1, "rotateY(90) right");
		nearVec(helper, y.upVector, 0, 1, 0, "rotateY leaves up alone");

		// Pitch: up tips towards +Z, front tips towards +Y.
		// up' = up*cos - front*sin, and front starts at -Z, so a quarter turn gives up' = +Z.
		FiaMatrix x = new FiaMatrix().rotateX(90);
		nearVec(helper, x.upVector, 0, 0, 1, "rotateX(90) up");
		nearVec(helper, x.frontVector, 0, 1, 0, "rotateX(90) front");
		nearVec(helper, x.rightVector, 1, 0, 0, "rotateX leaves right alone");

		// Roll: right tips towards +Y, up towards -X.
		FiaMatrix z = new FiaMatrix().rotateZ(90);
		nearVec(helper, z.rightVector, 0, 1, 0, "rotateZ(90) right");
		nearVec(helper, z.upVector, -1, 0, 0, "rotateZ(90) up");
		nearVec(helper, z.frontVector, 0, 0, -1, "rotateZ leaves front alone");

		// Four quarter turns is the identity again, which catches an accumulated sign error.
		FiaMatrix full = new FiaMatrix().rotateY(90).rotateY(90).rotateY(90).rotateY(90);
		nearVec(helper, full.frontVector, 0, 0, -1, "four quarter turns return to the start");

		helper.succeed();
	}

	/**
	 * Local-space translation follows the basis, world-space translation ignores it.
	 *
	 * <p>Note the sign on the third argument: {@code translate} adds {@code front.scale(-z)}, so
	 * a positive z moves <em>against</em> the front vector. With the default front of -Z that
	 * makes {@code translate(0,0,1)} a step towards +Z, which is backwards in Minecraft's terms.
	 * Upstream is consistent about it — {@code travel} and {@code FiaBB} use the same convention
	 * — so it is a convention, not a bug, and callers are written to it.
	 */
	@GameTest(template = EMPTY)
	public static void translateIsLocalAndTranslateWorldIsNot(GameTestHelper helper) {

		FiaMatrix turned = new FiaMatrix().rotateY(90);
		nearVec(helper, turned.frontVector, -1, 0, 0, "a quarter turn faces -X");

		// z runs against the facing, so one step of +z from a -X facing lands on +X.
		nearVec(helper, turned.translate(0, 0, 1).position, 1, 0, 0, "local z is measured against front");
		// One block "right" is along -Z.
		nearVec(helper, turned.translate(1, 0, 0).position, 0, 0, -1, "local right follows the facing");
		// World translation is unaffected by the rotation.
		nearVec(helper, turned.translateWorld(0, 0, 1).position, 0, 0, 1, "world translation ignores it");

		helper.succeed();
	}

	/**
	 * {@code inverse()} must undo {@code travel()}. Upstream left a broken first attempt in the
	 * file as a comment and replaced it; this pins the version that survived.
	 */
	@GameTest(template = EMPTY)
	public static void inverseUndoesTravel(GameTestHelper helper) {

		FiaMatrix frame = new FiaMatrix(new Vec3(3, 4, 5)).rotateY(37).rotateX(-12).rotateZ(64);
		FiaMatrix roundTrip = frame.toObjectSpace(frame);

		nearVec(helper, roundTrip.position, 0, 0, 0, "a frame in its own object space sits at the origin");
		nearVec(helper, roundTrip.rightVector, 1, 0, 0, "and is unrotated: right");
		nearVec(helper, roundTrip.upVector, 0, 1, 0, "and is unrotated: up");
		nearVec(helper, roundTrip.frontVector, 0, 0, -1, "and is unrotated: front");

		helper.succeed();
	}

	/**
	 * {@code getRotation} must recover the angles {@code rotate} was given, for every one of
	 * the six orders. This is the property the elevator's car orientation is synced on.
	 */
	@GameTest(template = EMPTY)
	public static void rotationRoundTripsThroughEveryOrder(GameTestHelper helper) {

		for(RotationOrder order : RotationOrder.values()) {
			FiaMatrix m = new FiaMatrix(order, 23, -41, 17);
			TupleRotation back = m.getRotation(order);
			near(helper, back.angleX, 23, order + " recovers X");
			near(helper, back.angleY, -41, order + " recovers Y");
			near(helper, back.angleZ, 17, order + " recovers Z");
			helper.assertTrue(back.order == order, order + " reports its own order back");
		}

		helper.succeed();
	}

	/**
	 * The axis order each enum constant actually produces.
	 *
	 * <p>Upstream derives it arithmetically from the ordinal rather than writing it down, so
	 * every constant compiles to <em>an</em> order whether or not it is the right one. These
	 * are the orders the constructor really yields, checked by rotating about a single axis at
	 * a time: a rotation of 0 about two of the three axes leaves the third's angle intact
	 * whatever the order, so the recovered non-zero angle identifies the axis that moved.
	 */
	@GameTest(template = EMPTY)
	public static void rotationOrderEnumBuildsTheAxisOrderItsNameClaims(GameTestHelper helper) {

		for(RotationOrder order : RotationOrder.values()) {
			// A single-axis rotation must come back on that axis alone, in every order.
			TupleRotation x = new FiaMatrix(order, 30, 0, 0).getRotation(order);
			near(helper, x.angleX, 30, order + ": a lone X rotation stays on X");
			near(helper, x.angleY, 0, order + ": and leaves Y at zero");
			near(helper, x.angleZ, 0, order + ": and leaves Z at zero");

			TupleRotation z = new FiaMatrix(order, 0, 0, 30).getRotation(order);
			near(helper, z.angleZ, 30, order + ": a lone Z rotation stays on Z");
			near(helper, z.angleX, 0, order + ": and leaves X at zero");
		}

		// Order matters: the same three angles applied in two different orders land differently.
		FiaMatrix xyz = new FiaMatrix(RotationOrder.XYZ, 40, 50, 60);
		FiaMatrix zyx = new FiaMatrix(RotationOrder.ZYX, 40, 50, 60);
		helper.assertTrue(!xyz.equals(zyx),
				"XYZ and ZYX must not produce the same matrix, or the order array is not being used");

		helper.succeed();
	}

	/**
	 * <b>Dormant upstream bug.</b> {@code subtract} adds — its body is {@code position.add}, the
	 * same as {@code add}. Nothing outside the class calls it, so unlike the {@code LeafiaEase}
	 * defects (D-1004) it is not reachable in play. Pinned so a caller appearing later cannot
	 * inherit the wrong sign without this failing first.
	 */
	@GameTest(template = EMPTY)
	public static void subtractAddsInstead(GameTestHelper helper) {

		FiaMatrix m = new FiaMatrix(new Vec3(10, 10, 10));
		nearVec(helper, m.add(new Vec3(1, 2, 3)).position, 11, 12, 13, "add adds");
		nearVec(helper, m.subtract(new Vec3(1, 2, 3)).position, 11, 12, 13,
				"BUG: subtract adds too - identical body to add. Not live; see the class note");

		helper.succeed();
	}

	/** {@link FiaBB} flattens a rotated rectangle into the world-aligned box vanilla wants. */
	@GameTest(template = EMPTY)
	public static void boundingBoxIsAxisAlignedAroundTheRotatedRectangle(GameTestHelper helper) {

		// Unrotated: depth is applied through translate, whose z runs against the front vector,
		// so a depth of 2 from a -Z facing runs from 0 to +2.
		AABB flat = new FiaBB(new FiaMatrix(), -1, 0, 1, 3, 2).toAABB();
		near(helper, flat.minX, -1, "minX");
		near(helper, flat.maxX, 1, "maxX");
		near(helper, flat.minY, 0, "minY");
		near(helper, flat.maxY, 3, "maxY");
		near(helper, flat.minZ, 0, "minZ");
		near(helper, flat.maxZ, 2, "maxZ: depth runs against the front vector, so towards +Z");

		// Rotated a quarter turn, the box swaps its X and Z extents but keeps its volume.
		AABB turned = new FiaBB(new FiaMatrix().rotateY(90), -1, 0, 1, 3, 2).toAABB();
		near(helper, turned.getYsize(), 3, "height is unchanged by a yaw");
		near(helper, turned.getXsize(), 2, "the 2-deep axis became X");
		near(helper, turned.getZsize(), 2, "and the 2-wide axis became Z");

		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void tuplesCompareByValue(GameTestHelper helper) {

		LCETuple.Pair<String, Integer> a = new LCETuple.Pair<>("x", 1);
		LCETuple.Pair<String, Integer> b = new LCETuple.Pair<>("x", 1);
		LCETuple.Pair<String, Integer> c = new LCETuple.Pair<>("x", 2);

		helper.assertTrue(a.equals(b), "equal contents compare equal");
		helper.assertTrue(a.hashCode() == b.hashCode(), "and hash the same, so a map key works");
		helper.assertTrue(!a.equals(c), "different contents do not");
		helper.assertTrue(a.getA().equals("x") && a.getB() == 1, "getA/getB");
		helper.assertTrue(a.getKey().equals("x") && a.getValue() == 1, "getKey/getValue alias them");

		LCETuple.Triplet<String, Integer, Boolean> t = new LCETuple.Triplet<>("x", 1, true);
		LCETuple.Triplet<String, Integer, Boolean> u = new LCETuple.Triplet<>("x", 1, true);
		helper.assertTrue(t.equals(u), "triplets too");
		helper.assertTrue(t.hashCode() == u.hashCode(), "and their hashes");
		helper.assertTrue(t.getC(), "getC");

		helper.succeed();
	}
}
