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
import com.leafia.contents.building.generic.mixed.BlockMixedConcrete;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The 231 mixed concretes.
 *
 * <h2>What is worth testing in a family this generated</h2>
 *
 * Almost nothing about an individual block, and three things about the set. **The names**, which
 * contain two traps that would each have produced silently broken blocks. **The models**, whose
 * two-element split is what replaces upstream's 924 synthesised sprites — a model with one
 * element, or with its seam faces declared, is a block that looks nearly right. And **the
 * placement rule**, which is upstream's and is not 1.21.1's idiom.
 *
 * <p>What cannot be tested here is whether the seam actually lines up on screen. The client
 * sweep proves the models resolve and the textures stitch; it does not prove a wall of them
 * reads straight.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaMixedConcreteTests {

	private static final String EMPTY = "empty";

	/**
	 * All 231 exist, the generator and the registry agree, and the two name traps are pinned.
	 *
	 * <p>The traps are the point of this test. 1.12.2's {@code EnumDyeColor.SILVER.getName()} is
	 * {@code "silver"} — using the modern {@code light_gray} would name 21 blocks after a texture
	 * the base mod does not have. And {@code ducrete_brick} is NTM-CE's name for a texture this
	 * repo's 1.7.10 base mod calls {@code brick_ducrete}. Both would have been purple-and-black
	 * faces with no warning anywhere: the model resolves, the sprite does not.
	 */
	@GameTest(template = EMPTY)
	public static void allTwoHundredAndThirtyOneExistWithUpstreamsNames(GameTestHelper helper) {

		JsonObject manifest = read(helper, "/assets/leafia/mixed_concrete.json");
		Set<String> generated = new TreeSet<>();
		manifest.getAsJsonArray("blocks").forEach(b -> generated.add(b.getAsString()));

		List<String> problems = new ArrayList<>();

		if(generated.size() != 231)
			problems.add("22 concretes give 231 pairs; the generator wrote " + generated.size());
		if(LeafiaBlocks.mixed_concrete.size() != generated.size())
			problems.add(LeafiaBlocks.mixed_concrete.size() + " registered against "
					+ generated.size() + " generated");

		for(String block : generated)
			if(!LeafiaBlocks.mixed_concrete.containsKey(block))
				problems.add(block + " is generated but not registered");
		for(String block : LeafiaBlocks.mixed_concrete.keySet())
			if(!generated.contains(block))
				problems.add(block + " is registered but not generated");

		// The traps, by name.
		if(!generated.contains("concrete__mixed_brick_silver"))
			problems.add("1.12.2's dye is 'silver'; no block is named for it");
		for(String block : generated)
			if(block.contains("light_gray"))
				problems.add(block + " uses the modern dye name; 1.12.2 says 'silver'");

		Set<String> textures = new TreeSet<>();
		manifest.getAsJsonArray("ids").forEach(e ->
				textures.add(e.getAsJsonObject().get("texture").getAsString()));
		if(!textures.contains("brick_ducrete"))
			problems.add("the ducrete texture in this repo is brick_ducrete, not NTM-CE's "
					+ "ducrete_brick; the manifest says " + textures);

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every model is two elements meeting at z=8, and declares no interior face.
	 *
	 * <p>This is what replaces upstream's four synthesised sprites per block, so it is the one
	 * piece of geometry in the family worth pinning. Three ways it could be wrong and all three
	 * look plausible: one element (no split at all), a seam face declared (drawn inside the
	 * block), or a hand-written {@code uv} (which would undo the whole point — vanilla derives
	 * the half from the element's bounds).
	 */
	@GameTest(template = EMPTY)
	public static void everyModelSplitsInTwoAtTheMiddle(GameTestHelper helper) {

		JsonObject manifest = read(helper, "/assets/leafia/mixed_concrete.json");
		List<String> problems = new ArrayList<>();
		int checked = 0;

		for(var element : manifest.getAsJsonArray("blocks")) {

			String block = element.getAsString();
			JsonObject model = readOrNull("/assets/leafia/models/block/" + block + ".json");
			if(model == null) {
				problems.add(block + ": no model");
				continue;
			}
			checked++;

			var elements = model.getAsJsonArray("elements");
			if(elements == null || elements.size() != 2) {
				problems.add(block + ": " + (elements == null ? "no" : elements.size())
						+ " elements, should be 2");
				continue;
			}

			JsonObject front = elements.get(0).getAsJsonObject();
			JsonObject back = elements.get(1).getAsJsonObject();

			if(front.getAsJsonArray("to").get(2).getAsInt() != 8
					|| back.getAsJsonArray("from").get(2).getAsInt() != 8)
				problems.add(block + ": the two halves do not meet at z=8");

			// The seam faces are interior and must not be declared at all.
			if(front.getAsJsonObject("faces").has("south"))
				problems.add(block + ": the front half declares its south face, which is interior");
			if(back.getAsJsonObject("faces").has("north"))
				problems.add(block + ": the back half declares its north face, which is interior");

			for(JsonObject half : new JsonObject[]{front, back}) {
				var faces = half.getAsJsonObject("faces");
				if(faces.size() != 5)
					problems.add(block + ": a half declares " + faces.size() + " faces, should be 5");
				for(String key : faces.keySet())
					if(faces.getAsJsonObject(key).has("uv"))
						problems.add(block + ": " + key + " has a hand-written uv; vanilla must "
								+ "derive it from the element bounds");
			}

			// A on the front half, B on the back one.
			if(!front.getAsJsonObject("faces").getAsJsonObject("north")
					.get("texture").getAsString().equals("#a"))
				problems.add(block + ": the front half is not textured #a");
			if(!back.getAsJsonObject("faces").getAsJsonObject("south")
					.get("texture").getAsString().equals("#b"))
				problems.add(block + ": the back half is not textured #b");
		}

		if(checked != 231) problems.add("checked " + checked + " models, not 231");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " model problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** Six blockstate variants, at upstream's six {@code ModelRotation} angles. */
	@GameTest(template = EMPTY)
	public static void theBlockstateIsUpstreamsSixBakes(GameTestHelper helper) {

		JsonObject blockstate = read(helper,
				"/assets/leafia/blockstates/concrete__mixed_brick_raw.json");
		JsonObject variants = blockstate.getAsJsonObject("variants");
		List<String> problems = new ArrayList<>();

		if(variants.size() != 6)
			problems.add("six facings, six variants; got " + variants.keySet());

		// Upstream's bakes: north unrotated, south Y180, west Y270, east Y90, up X270, down X90.
		record Expect(String variant, String axis, int angle) {}
		for(Expect e : List.of(
				new Expect("facing=north", null, 0),
				new Expect("facing=south", "y", 180),
				new Expect("facing=west", "y", 270),
				new Expect("facing=east", "y", 90),
				new Expect("facing=up", "x", 270),
				new Expect("facing=down", "x", 90))) {

			JsonObject v = variants.getAsJsonObject(e.variant());
			if(v == null) { problems.add("no " + e.variant()); continue; }

			if(e.axis() == null) {
				if(v.has("x") || v.has("y"))
					problems.add("facing=north should be unrotated, got " + v);
			} else if(!v.has(e.axis()) || v.get(e.axis()).getAsInt() != e.angle()) {
				problems.add(e.variant() + " should be " + e.axis() + "=" + e.angle() + ", got " + v);
			}
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " variant problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Fifteen destroy time and <b>84</b> blast resistance, which is the class's number and not
	 * the registration's.
	 *
	 * <p>Upstream sets {@code setResistance(CONCRETE.v)} in the constructor and the registration
	 * adds nothing — so the full 84, where the lined asphalts' registration overrode the same
	 * constructor call with {@code half()} and got 42 (D-1047). Two families, one enum, two
	 * different answers, and reading only one of the two places would have got one of them
	 * wrong. Pinned so the distinction cannot be tidied away.
	 */
	@GameTest(template = EMPTY)
	public static void theStrengthIsTheClasssNumberNotTheAsphaltsHalf(GameTestHelper helper) {

		Block mixed = LeafiaBlocks.mixed_concrete.get("concrete__mixed_brick_raw").get();
		BlockState state = mixed.defaultBlockState();

		helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 15.0F,
				"upstream's setHardness(15.0F), got " + state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO));
		helper.assertTrue(mixed.getExplosionResistance() == 84.0F,
				"upstream's CONCRETE.v is 84 here, not half()'s 42; got " + mixed.getExplosionResistance());

		// And the asphalt really is the other one, so the contrast is measured rather than
		// asserted from the comment above.
		Block asphalt = LeafiaBlocks.linedAsphalt("x");
		helper.assertTrue(asphalt.getExplosionResistance() == 42.0F,
				"the lined asphalt should still be half() at 42, got " + asphalt.getExplosionResistance());
		helper.succeed();
	}

	/**
	 * Upstream's placement rule, which reads the player's <em>position</em> rather than their
	 * look vector.
	 *
	 * <p>1.21.1's idiom is {@code getNearestLookingDirection().getOpposite()}. Upstream's is
	 * three branches on where the player is standing, and the difference is visible when you
	 * crouch next to a block and look down at it. All three branches are exercised, because a
	 * rule reproduced from prose is a rule nobody has run.
	 */
	@GameTest(template = EMPTY)
	public static void placementFollowsUpstreamsPositionRule(GameTestHelper helper) {

		Block mixed = LeafiaBlocks.mixed_concrete.get("concrete__mixed_brick_raw").get();
		BlockPos pos = new BlockPos(2, 2, 2);
		BlockPos abs = helper.absolutePos(pos);
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		List<String> problems = new ArrayList<>();

		// Close by horizontally, eyes well above: UP.
		player.setPos(abs.getX() + 0.5, abs.getY() + 4, abs.getZ() + 0.5);
		if(place(helper, mixed, abs, player) != Direction.UP)
			problems.add("standing four blocks above should face UP, got " + place(helper, mixed, abs, player));

		// Close by horizontally, the block above the player's eyes: DOWN.
		player.setPos(abs.getX() + 0.5, abs.getY() - 4, abs.getZ() + 0.5);
		if(place(helper, mixed, abs, player) != Direction.DOWN)
			problems.add("standing four blocks below should face DOWN, got " + place(helper, mixed, abs, player));

		// Far away horizontally: the opposite of the player's horizontal facing, whatever their
		// height. This is the branch the first two would pass without.
		player.setPos(abs.getX() + 8, abs.getY() + 4, abs.getZ() + 0.5);
		player.setYRot(0);   // facing south
		Direction far = place(helper, mixed, abs, player);
		if(far != Direction.NORTH)
			problems.add("eight blocks away facing south should face NORTH, got " + far);

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " placement problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** Rotation and mirror move the facing, which is what lets a structure block copy one. */
	@GameTest(template = EMPTY)
	public static void rotationMovesTheFacing(GameTestHelper helper) {

		Block mixed = LeafiaBlocks.mixed_concrete.get("concrete__mixed_brick_raw").get();
		BlockState north = mixed.defaultBlockState()
				.setValue(BlockMixedConcrete.FACING, Direction.NORTH);

		helper.assertTrue(north.rotate(Rotation.CLOCKWISE_90)
						.getValue(BlockMixedConcrete.FACING) == Direction.EAST,
				"a quarter turn from north is east");
		// LEFT_RIGHT, not FRONT_BACK: vanilla's names read the other way round from the axis
		// they flip. FRONT_BACK mirrors across X and leaves north alone; LEFT_RIGHT mirrors
		// across Z and is the one that turns north into south. Asserting FRONT_BACK here is
		// what the first run of this test did, and it was the test that was wrong.
		helper.assertTrue(north.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT)
						.getValue(BlockMixedConcrete.FACING) == Direction.SOUTH,
				"mirroring left to right turns north into south");
		helper.assertTrue(north.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK)
						.getValue(BlockMixedConcrete.FACING) == Direction.NORTH,
				"mirroring front to back flips west and east, so north is unchanged");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	/** Places {@code block} with a real {@code BlockPlaceContext} and reads back its facing. */
	private static Direction place(GameTestHelper helper, Block block, BlockPos abs,
			net.minecraft.world.entity.player.Player player) {

		var stack = new net.minecraft.world.item.ItemStack(block);
		var hit = new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.UP, abs, false);
		var context = new net.minecraft.world.item.context.BlockPlaceContext(
				helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);

		BlockState placed = block.getStateForPlacement(context);
		helper.assertTrue(placed != null, "getStateForPlacement returned null");
		return placed.getValue(BlockMixedConcrete.FACING);
	}

	private static JsonObject readOrNull(String path) {
		try(InputStream in = LeafiaMixedConcreteTests.class.getResourceAsStream(path)) {
			if(in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			return null;
		}
	}

	private static JsonObject read(GameTestHelper helper, String path) {
		try(InputStream in = LeafiaMixedConcreteTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "missing generated file: " + path);
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			helper.fail("could not read " + path + ": " + e);
			return null;
		}
	}
}
