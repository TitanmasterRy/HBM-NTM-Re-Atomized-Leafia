package com.leafia.gametest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.catwalk.railing.CatwalkRailingBase;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The catwalk railing — its arms, its joins, and the thirty-two models they are drawn from.
 *
 * <h2>What is covered, stated honestly</h2>
 *
 * The railing's one interesting behaviour is a sixteen-bit mask: four bits of its own state and
 * twelve read off its neighbours, deciding for each arm end whether it is drawn as a straight
 * run, a corner curve or a cap. <b>All of that is server-side arithmetic</b> —
 * {@code CatwalkRailingBase.renderMask} and {@code partsFor} — which is why the part selection
 * was hoisted out of the baked model onto the block. So these tests reach the whole decision.
 *
 * <p>What they do not reach is any pixel. That the chosen part models draw the geometry
 * upstream drew needs a client, and the batch's client sweep is where it was looked at.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCatwalkTests {

	private static final String EMPTY = "empty";

	/** A railing with all four arms, which is what placing one gives you. */
	private static BlockState full() {
		return LeafiaBlocks.catwalk_railing_curved.get().defaultBlockState()
				.setValue(CatwalkRailingBase.POS_X, true)
				.setValue(CatwalkRailingBase.POS_Z, true)
				.setValue(CatwalkRailingBase.NEG_X, true)
				.setValue(CatwalkRailingBase.NEG_Z, true);
	}

	private static BlockState arms(boolean px, boolean pz, boolean nx, boolean nz) {
		return LeafiaBlocks.catwalk_railing_curved.get().defaultBlockState()
				.setValue(CatwalkRailingBase.POS_X, px)
				.setValue(CatwalkRailingBase.POS_Z, pz)
				.setValue(CatwalkRailingBase.NEG_X, nx)
				.setValue(CatwalkRailingBase.NEG_Z, nz);
	}

	// ------------------------------------------------------------------ the mask

	/**
	 * A railing on its own, with all four arms, is four posts and four corner curves.
	 *
	 * <p>Not four posts and eight caps, which is what this test asserted when it was written and
	 * is the reason it is worth having: with all four arms present, <b>every</b> arm end has the
	 * perpendicular arm to turn into, so nothing is ever capped. A lone railing is a closed
	 * square. The cap branch needs an arm with no perpendicular neighbour, which is the next
	 * test.
	 */
	@GameTest(template = EMPTY)
	public static void aLoneFullRailingIsFourPostsAndFourCurves(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, full());

		int mask = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(pos),
				helper.getBlockState(pos));

		helper.assertTrue(mask == 0b1111, "a lone railing has the four arm bits and nothing else, got "
				+ Integer.toBinaryString(mask));

		Set<String> parts = CatwalkRailingBase.partsFor(mask);
		helper.assertTrue(parts.equals(Set.of(
						"post_pX", "post_nX", "post_pZ", "post_nZ",
						"curve_pXpZ", "curve_pXnZ", "curve_nXpZ", "curve_nXnZ")),
				"a lone full railing is four posts and four curves, got " + parts);
		helper.succeed();
	}

	/** One arm alone is a post and two caps — the branch a full railing never reaches. */
	@GameTest(template = EMPTY)
	public static void aSingleArmIsCappedAtBothEnds(GameTestHelper helper) {

		Set<String> parts = CatwalkRailingBase.partsFor(
				CatwalkRailingBase.armMask(arms(true, false, false, false)));

		helper.assertTrue(parts.equals(Set.of("post_pX", "end_pXpZ", "end_pXnZ")),
				"a lone +X arm is one post and two caps, got " + parts);
		helper.succeed();
	}

	/**
	 * Two railings side by side join along the two arms that run between them.
	 *
	 * <p>This is the test that makes the first one mean something: it pins that
	 * {@code diagonalMask} reads the neighbour, and reads the <b>right</b> neighbour — a mask
	 * that lit every join bit would pass a "did anything change" check.
	 *
	 * <p>Which two arms join is worth stating, because it is not the shared face. Two railings
	 * abutting along X make a corridor whose <em>north and south</em> rails run straight
	 * through; the +X arm of the western block still turns into its own north and south arms, so
	 * it stays a pair of curves.
	 */
	@GameTest(template = EMPTY)
	public static void twoRailingsInARowJoinAlongTheRailsThatRunBetweenThem(GameTestHelper helper) {

		BlockPos west = new BlockPos(1, 1, 1);
		BlockPos east = new BlockPos(2, 1, 1);
		helper.setBlock(west, full());
		helper.setBlock(east, full());

		int mask = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(west),
				helper.getBlockState(west));

		helper.assertTrue((mask & CatwalkRailingBase.MASK_NEG_Z_PX) != 0,
				"the -Z arm should run on into the neighbour at +X");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_POS_Z_PX) != 0,
				"the +Z arm should run on into the neighbour at +X");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_NEG_Z_NX) == 0,
				"nothing stands at -X, so the -Z arm must not join there");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_POS_Z_NX) == 0,
				"nothing stands at -X, so the +Z arm must not join there");

		Set<String> parts = CatwalkRailingBase.partsFor(mask);
		helper.assertTrue(parts.contains("straight_nZpX") && parts.contains("straight_pZpX"),
				"the two rails running between the blocks are drawn straight, got " + parts);
		helper.assertTrue(parts.contains("curve_nXnZ") && parts.contains("curve_nXpZ"),
				"the far side still turns the corner into the -X arm, got " + parts);
		helper.assertTrue(parts.stream().noneMatch(p -> p.startsWith("end_")),
				"nothing is capped when all four arms are present, got " + parts);

		// The mirror, for the reason given on the north-south pair.
		int mirror = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(east),
				helper.getBlockState(east));
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_NEG_Z_NX) != 0,
				"from the east block the -Z arm joins at its -X end");
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_POS_Z_NX) != 0,
				"from the east block the +Z arm joins at its -X end");
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_NEG_Z_PX) == 0,
				"nothing stands east of the east block");
		helper.succeed();
	}

	/**
	 * The same, one axis over — and it is here because the first version of these tests missed
	 * a whole half of {@code diagonalMask}.
	 *
	 * <p>Two railings abutting along X exercise only the four {@code MASK_*_Z_*X} bits. A
	 * falsification run that swapped {@code MASK_POS_X_NZ} for {@code MASK_POS_X_PZ} in
	 * {@code diagonalMask} passed every test, because nothing ever put a neighbour where those
	 * four bits could light. A pair along Z is what lights them.
	 */
	@GameTest(template = EMPTY)
	public static void twoRailingsStackedAlongZJoinTheOtherFourBits(GameTestHelper helper) {

		BlockPos north = new BlockPos(1, 1, 1);
		BlockPos south = new BlockPos(1, 1, 2);
		helper.setBlock(north, full());
		helper.setBlock(south, full());

		int mask = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(north),
				helper.getBlockState(north));

		helper.assertTrue((mask & CatwalkRailingBase.MASK_POS_X_PZ) != 0,
				"the +X arm should run on into the neighbour at +Z");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_NEG_X_PZ) != 0,
				"the -X arm should run on into the neighbour at +Z");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_POS_X_NZ) == 0,
				"nothing stands at -Z, so the +X arm must not join there");
		helper.assertTrue((mask & CatwalkRailingBase.MASK_NEG_X_NZ) == 0,
				"nothing stands at -Z, so the -X arm must not join there");

		Set<String> parts = CatwalkRailingBase.partsFor(mask);
		helper.assertTrue(parts.contains("straight_pXpZ") && parts.contains("straight_nXpZ"),
				"the two rails running between the blocks are drawn straight, got " + parts);
		helper.assertTrue(parts.contains("curve_pXnZ") && parts.contains("curve_nXnZ"),
				"the far side still turns the corner into the -Z arm, got " + parts);

		// And the same pair read from the other end, which is not redundant: the two blocks see
		// the join through *different* bits, and a bit assigned to the wrong end lights
		// correctly from one side and not the other. Asserting only the north block let a
		// swapped MASK_POS_X_NZ / MASK_POS_X_PZ through a falsification run.
		int mirror = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(south),
				helper.getBlockState(south));
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_POS_X_NZ) != 0,
				"from the south block the +X arm joins at its -Z end");
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_NEG_X_NZ) != 0,
				"from the south block the -X arm joins at its -Z end");
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_POS_X_PZ) == 0,
				"nothing stands south of the south block");
		helper.assertTrue((mirror & CatwalkRailingBase.MASK_NEG_X_PZ) == 0,
				"nothing stands south of the south block");
		helper.succeed();
	}

	/**
	 * An arm whose perpendicular neighbour is its own block turns a corner rather than capping.
	 *
	 * <p>The three-way choice per arm end is the block's whole point, and this is the third
	 * branch: straight when a neighbour continues it, <b>curve when this block turns</b>, cap
	 * otherwise. Asserted on a lone railing, where no join bit can be on, so only the
	 * perpendicular arm can be producing the curve.
	 */
	@GameTest(template = EMPTY)
	public static void aCornerOfOneBlockDrawsACurve(GameTestHelper helper) {

		// +X and +Z only: the two arms meet at the +X+Z corner of the block.
		int mask = CatwalkRailingBase.armMask(arms(true, true, false, false));
		Set<String> parts = CatwalkRailingBase.partsFor(mask);

		helper.assertTrue(parts.contains("curve_pXpZ"),
				"two arms meeting at +X+Z draw the corner curve, got " + parts);
		helper.assertTrue(parts.contains("end_pXnZ") && parts.contains("end_pZnX"),
				"their other two ends are still capped, got " + parts);
		helper.assertTrue(!parts.contains("post_nX") && !parts.contains("post_nZ"),
				"an arm that is not there has no post, got " + parts);
		helper.succeed();
	}

	/**
	 * The sloped branch is unreachable, and this says so rather than leaving it ambiguous.
	 *
	 * <p>{@code MASK_SLOPED} is never set by anything — not by {@code armMask}, not by
	 * {@code diagonalMask}, so not by {@code renderMask}. The branch and the .obj's eight
	 * {@code stair_} objects are carried anyway; this pins that nothing reaches them, so a
	 * future change that starts setting the bit fails here and has to be deliberate.
	 */
	@GameTest(template = EMPTY)
	public static void nothingEverSetsTheSlopedBit(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockPos side = new BlockPos(2, 1, 1);
		helper.setBlock(pos, full());
		helper.setBlock(side, full());

		int mask = CatwalkRailingBase.renderMask(helper.getLevel(), helper.absolutePos(pos),
				helper.getBlockState(pos));
		helper.assertTrue((mask & CatwalkRailingBase.MASK_SLOPED) == 0,
				"renderMask set MASK_SLOPED, which nothing in the port should");

		// And that the branch it guards does work, so it is dead rather than broken.
		// The two direction bits sit above MASK_S_DIR_SHIFT and MASK_SLOPED is the bit above
		// those, so a bare MASK_SLOPED reads as direction 0 - SOUTH, not NORTH. Derived from
		// the shift rather than guessed: guessing it said NORTH and the first run said otherwise.
		Set<String> sloped = CatwalkRailingBase.partsFor(
				CatwalkRailingBase.MASK_SLOPED | CatwalkRailingBase.MASK_POS_X);
		helper.assertTrue(sloped.equals(Set.of("stair_pXpZ")),
				"the sloped branch should draw one stair for a +X arm sloping south, got " + sloped);
		helper.succeed();
	}

	// ------------------------------------------------------------------ shape

	/**
	 * The outline follows the arms, and an armless railing is solid and invisible.
	 *
	 * <p>Both halves are upstream's and the second is the odd one: {@code shouldDrawHighlight}
	 * refuses a railing with no arms and {@code addCollisionBoxToList} gives it a full cube, so
	 * it cannot be seen, outlined or broken but does stop you walking through. Unreachable in
	 * play — every placed railing has four arms — and pinned so that stays true by accident
	 * rather than by luck.
	 */
	@GameTest(template = EMPTY)
	public static void theOutlineIsTheArmsAndAnArmlessRailingIsASolidHole(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);

		// Through the BlockState rather than the Block: that is the path the game takes, cache
		// and all, and the block's own overrides are protected.
		BlockState one = arms(false, false, false, true);
		helper.assertTrue(!one.getShape(level, abs).isEmpty(),
				"a railing with an arm must have an outline");

		// The -Z arm is a slab at the north edge, half a sixteenth thick, full height.
		var bounds = one.getShape(level, abs).bounds();
		helper.assertTrue(Math.abs(bounds.maxZ - 0.5 / 16) < 1e-9,
				"upstream's arm is 0.5/16 thick, got " + bounds.maxZ);
		helper.assertTrue(bounds.minY == 0 && bounds.maxY == 1,
				"upstream's arm is the full height of the block, got " + bounds.minY + ".." + bounds.maxY);

		BlockState none = arms(false, false, false, false);
		helper.assertTrue(none.getShape(level, abs).isEmpty(),
				"an armless railing has no outline at all - upstream's shouldDrawHighlight");
		helper.assertTrue(none.getCollisionShape(level, abs).bounds()
						.equals(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1)),
				"an armless railing collides as a full cube - upstream's addCollisionBoxToList");
		helper.succeed();
	}

	/** Placing a railing gives it all four arms, which is upstream's getStateForPlacement. */
	@GameTest(template = EMPTY)
	public static void aPlacedRailingHasAllFourArms(GameTestHelper helper) {
		helper.assertTrue(CatwalkRailingBase.armMask(full()) == 0b1111, "sanity: all four bits");
		for(Direction face : Direction.Plane.HORIZONTAL)
			helper.assertTrue(CatwalkRailingBase.armFor(face) != null,
					face + " has no arm property");
		helper.succeed();
	}

	/**
	 * Clicking adds the arm nearest the click, once, and refuses one that is already there.
	 *
	 * <p>{@code tryAddRailing} is the only way a railing's state ever changes, and the "already
	 * there" answer is what stops the item being consumed for nothing.
	 */
	@GameTest(template = EMPTY)
	public static void clickingAddsOneArmAndOnlyIfItIsMissing(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockPos abs = helper.absolutePos(pos);
		var block = LeafiaBlocks.catwalk_railing_curved.get();
		var level = helper.getLevel();

		helper.setBlock(pos, arms(false, false, false, false));

		// Well past centre in +X, barely off it in Z: the X axis wins and the +X arm appears.
		helper.assertTrue(block.tryAddRailing(level, abs, 0.9, 0.5), "the +X arm should have been added");
		helper.assertTrue(helper.getBlockState(pos).getValue(CatwalkRailingBase.POS_X),
				"the +X arm is not set");
		helper.assertTrue(!helper.getBlockState(pos).getValue(CatwalkRailingBase.NEG_X),
				"only the clicked arm should appear");

		helper.assertTrue(!block.tryAddRailing(level, abs, 0.9, 0.5),
				"an arm already there must refuse, so the item is not consumed");

		// The larger offset from centre decides, so a click past centre in -Z takes the Z axis.
		helper.assertTrue(block.tryAddRailing(level, abs, 0.55, 0.1), "the -Z arm should have been added");
		helper.assertTrue(helper.getBlockState(pos).getValue(CatwalkRailingBase.NEG_Z),
				"the -Z arm is not set");

		// A click near the block's diagonal, where the two offsets are close. The clear-cut
		// cases above cannot tell `abs(x) >= abs(z)` from any other comparison that leans the
		// same way; a falsification run that changed it to `abs(x) > abs(z) * 4` passed them
		// both. Here X leads Z by a fifth, which only the real comparison resolves to X.
		helper.setBlock(pos, arms(false, false, false, false));
		helper.assertTrue(block.tryAddRailing(level, abs, 0.80, 0.75),
				"a click just past the diagonal on the X side should add an arm");
		helper.assertTrue(helper.getBlockState(pos).getValue(CatwalkRailingBase.POS_X),
				"X leads Z by 0.30 to 0.25, so the +X arm is the one that should appear");

		// And the tie itself: equal offsets go to X, because the test is `>=`.
		helper.setBlock(pos, arms(false, false, false, false));
		helper.assertTrue(block.tryAddRailing(level, abs, 0.75, 0.75), "a tie should still add an arm");
		helper.assertTrue(helper.getBlockState(pos).getValue(CatwalkRailingBase.POS_X),
				"an exact tie goes to X, which is what upstream's >= means");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the models

	/**
	 * Every part the selection can name has a model, and every object in the .obj has one.
	 *
	 * <p>Derived on both sides, like {@code LeafiaElevatorRenderTests}: the generator's manifest
	 * says what it wrote, the .obj says what exists, and {@code partsFor} is enumerated over
	 * every one of the sixteen-bit masks it can actually be handed. A part name misspelt in the
	 * selection is otherwise invisible — the model simply is not found and that arm end is not
	 * drawn.
	 */
	@GameTest(template = EMPTY)
	public static void everyPartTheSelectionCanNameHasAModel(GameTestHelper helper) {

		JsonObject manifest = read(helper, "/assets/leafia/catwalk_models.json");
		String block = manifest.get("block").getAsString();

		Set<String> declared = new TreeSet<>();
		manifest.getAsJsonArray("parts").forEach(p -> declared.add(p.getAsString()));
		Set<String> inObj = objectsIn(helper, manifest.get("obj").getAsString());

		List<String> problems = new ArrayList<>();

		if(!declared.equals(inObj))
			problems.add("the manifest and the .obj disagree: manifest-only " + minus(declared, inObj)
					+ ", obj-only " + minus(inObj, declared));

		// Every part any reachable mask can ask for. The twelve live bits only - MASK_SLOPED is
		// unreachable, and its branch is covered by its own test above.
		Set<String> named = new TreeSet<>();
		for(int mask = 0; mask < (1 << 4); mask++) {
			int arms = mask;
			for(int joins = 0; joins < (1 << 8); joins++)
				named.addAll(CatwalkRailingBase.partsFor(arms | (joins << 8)));
		}
		named.addAll(CatwalkRailingBase.partsFor(
				CatwalkRailingBase.MASK_SLOPED | 0b1111));

		for(String part : named)
			if(!declared.contains(part))
				problems.add("partsFor can name " + part + ", which the .obj does not declare");

		// Every part model must exist and must hide every sibling: a visibility map is a set of
		// negatives, so a missing entry draws a whole extra arm and nothing anywhere complains.
		//
		// readOrNull, not read: `read` asserts, and an assert inside a loop that is accumulating
		// problems reports the first and hides the rest. That is D-1041's mistake, which D-1043
		// found a second time; a three-regression falsification found it here a THIRD time,
		// reporting one missing model and hiding both of the others. A loop that accumulates
		// must not call anything that throws.
		for(String part : declared) {
			String path = "/assets/leafia/models/block/" + block + "_" + part.toLowerCase() + ".json";
			JsonObject json = readOrNull(path);
			if(json == null) {
				problems.add("no model for part " + part);
				continue;
			}
			Set<String> hidden = new TreeSet<>(json.getAsJsonObject("visibility").keySet());
			Set<String> expected = new TreeSet<>(declared);
			expected.remove(part);
			if(!hidden.equals(expected))
				// The differences, not the two sets: with thirty-two siblings a full pair is
				// sixty lines per problem, and one renamed part makes every model report one.
				problems.add(part + ": hides " + minus(hidden, expected)
						+ " it should not, and not " + minus(expected, hidden));
		}

		// One assert, at the end - D-1041 and D-1043, twice learnt.
		helper.assertTrue(problems.isEmpty(),
				problems.size() + " catwalk model problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The blockstate has exactly one variant, and it names the composing loader.
	 *
	 * <p>Both halves matter. A second variant would mean somebody tried to express the joins in
	 * the blockstate, which cannot work — twelve of the sixteen bits are the neighbours'. A
	 * blockstate naming a loader that is not registered is a hard model error rather than a
	 * missing model, so the Java half ({@code LeafiaClient.registerGeometryLoaders}) and this
	 * have to agree.
	 */
	@GameTest(template = EMPTY)
	public static void theBlockstateIsOneVariantOnTheComposingLoader(GameTestHelper helper) {

		JsonObject blockstate = read(helper, "/assets/leafia/blockstates/catwalk_railing_curved.json");
		JsonObject variants = blockstate.getAsJsonObject("variants");

		helper.assertTrue(variants.size() == 1 && variants.has(CatwalkRailingBase.getStateMapperVariant()),
				"upstream maps every state to one variant; this blockstate has " + variants.keySet());

		JsonObject model = read(helper, "/assets/leafia/models/block/catwalk_railing_curved.json");
		helper.assertTrue(model.get("loader").getAsString().equals("leafia:catwalk_railing"),
				"the block model names loader " + model.get("loader").getAsString());
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static Set<String> minus(Set<String> a, Set<String> b) {
		Set<String> out = new TreeSet<>(a);
		out.removeAll(b);
		return out;
	}

	private static Set<String> objectsIn(GameTestHelper helper, String obj) {
		Set<String> parts = new TreeSet<>();
		try(InputStream in = LeafiaCatwalkTests.class.getResourceAsStream("/assets/leafia/" + obj)) {
			helper.assertTrue(in != null, "missing model file: " + obj);
			for(String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
				if(line.startsWith("o ")) parts.add(line.substring(2).trim());
		} catch(Exception e) {
			helper.fail("could not read " + obj + ": " + e);
		}
		helper.assertTrue(!parts.isEmpty(), obj + " declares no named objects");
		return parts;
	}

	/** The JSON at {@code path}, or null when there is none. Never throws — see the loop above. */
	private static JsonObject readOrNull(String path) {
		try(InputStream in = LeafiaCatwalkTests.class.getResourceAsStream(path)) {
			if(in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			return null;
		}
	}

	private static JsonObject read(GameTestHelper helper, String path) {
		try(InputStream in = LeafiaCatwalkTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "missing generated file: " + path);
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			helper.fail("could not read " + path + ": " + e);
			return null;
		}
	}
}
