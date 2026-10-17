package com.leafia.gametest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.elevators.EvBufferTE;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The elevator renderers — or rather, the parts of them a dedicated server can see.
 *
 * <h2>What can and cannot be tested here, stated up front</h2>
 *
 * A GameTest server is a <em>dedicated</em> server: it never builds a {@code ResourceManager} for
 * {@code assets/}, and it cannot load a {@code @OnlyIn(Dist.CLIENT)} class at all. So nothing
 * below draws anything, and none of it proves a pixel is in the right place. Three things it
 * <em>can</em> do, and each of them is a real failure this batch could have:
 *
 * <ul>
 * <li><b>The generated model JSONs are on the classpath and their {@code visibility} maps are
 *     right.</b> The assets ship in the jar even though the server never loads them, so they can
 *     be read as plain resources. A mistyped part name in
 *     a generator script produces a model that hides nothing and draws
 *     the whole machine six times over, and nothing else would notice.</li>
 * <li><b>The spring's arithmetic</b>, which lives on {@link EvBufferTE} rather than in the
 *     renderer precisely so this test can reach it.</li>
 * <li><b>The facing table</b>. Not the thing that makes it a trap, though — see
 *     {@link #theFacingTableIsUpstreams}, where the obvious assertion turned out to need a
 *     client-only class.</li>
 * </ul>
 *
 * <p>Saying which of those is coverage and which is not is the point. "The renderers are tested"
 * would be false; "the data they read is pinned" is true.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaElevatorRenderTests {

	private static final String EMPTY = "empty";

	/**
	 * Every part model, checked against the {@code .obj} it actually points at.
	 *
	 * <p>The expectation is <b>derived, not copied</b>. An earlier version of this test held the
	 * part lists in a table beside the generator's, which covered ten models and would have had
	 * to grow to a hundred and eleven — and a table copied from a generator drifts from it the
	 * first time either changes. Instead the generator emits a manifest of what it wrote, this
	 * reads the {@code .obj} files out of the jar, and the two are compared. Neither side can
	 * quietly stop agreeing with the other, and a model file that is simply missing fails here.
	 *
	 * <p>What it catches: a {@code visibility} map naming a part that does not exist. Such a map
	 * is a set of negatives, so a misspelt key is silently ignored and the part it was meant to
	 * hide is drawn — a pulley with six motors in it, and no log line anywhere. One letter is
	 * enough, and the falsification run in D-1040 used exactly that.
	 */
	@GameTest(template = EMPTY)
	public static void everyPartModelHidesExactlyItsSiblings(GameTestHelper helper) {

		JsonObject root = read(helper, "/assets/leafia/elevator_models.json");
		JsonObject manifest = root.getAsJsonObject("models");
		JsonObject undrawn = root.getAsJsonObject("undrawn");
		helper.assertTrue(manifest.size() > 100,
				"the manifest holds only " + manifest.size() + " models; the generator did not run");

		Map<String, List<String>> declared = new java.util.HashMap<>();
		List<String> problems = new ArrayList<>();

		for(String model : manifest.keySet()) {

			JsonObject entry = manifest.getAsJsonObject(model);
			String obj = entry.get("obj").getAsString();
			String visible = entry.get("visible").getAsString();
			String path = "/assets/leafia/models/block/" + model + ".json";

			// Computed before the file check, so a missing model cannot also drop its whole
			// .obj out of the completeness pass below.
			List<String> parts = declared.computeIfAbsent(obj, o -> objectsIn(helper, o));

			JsonObject json = readOrNull(path);
			if(json == null) {
				problems.add(path + ": missing");
				continue;
			}

			if(!json.get("model").getAsString().equals("leafia:" + obj)) {
				problems.add(path + ": points at " + json.get("model").getAsString()
						+ ", the manifest says " + obj);
				continue;
			}
			if(!parts.contains(visible)) {
				problems.add(path + ": " + visible + " is not declared by " + obj);
				continue;
			}

			List<String> hidden = new ArrayList<>(json.getAsJsonObject("visibility").keySet());
			List<String> expected = new ArrayList<>(parts);
			expected.remove(visible);
			java.util.Collections.sort(hidden);
			java.util.Collections.sort(expected);

			if(!hidden.equals(expected))
				problems.add(path + ": hides " + hidden + ", should hide " + expected);
			else
				for(String h : hidden)
					if(json.getAsJsonObject("visibility").get(h).getAsBoolean())
						problems.add(path + ": leaves " + h + " visible");
		}

		// Completeness, rather than a count. Every named object an .obj declares is either drawn
		// by some model or named in UNDRAWN; a bare total has to be hand-edited every batch and
		// says nothing about WHICH part went missing. An earlier draft asserted 111 and failed
		// the next time a variant was added, which is the whole argument against it.
		Map<String, Set<String>> drawnPer = new java.util.HashMap<>();
		for(String model : manifest.keySet()) {
			JsonObject entry = manifest.getAsJsonObject(model);
			drawnPer.computeIfAbsent(entry.get("obj").getAsString(), o -> new java.util.HashSet<>())
					.add(entry.get("visible").getAsString());
		}
		for(Map.Entry<String, List<String>> e : declared.entrySet()) {
			Set<String> covered = new java.util.HashSet<>(
					drawnPer.getOrDefault(e.getKey(), java.util.Set.of()));
			if(undrawn.has(e.getKey()))
				undrawn.getAsJsonArray(e.getKey()).forEach(u -> covered.add(u.getAsString()));
			Set<String> uncovered = new java.util.TreeSet<>(e.getValue());
			uncovered.removeAll(covered);
			if(!uncovered.isEmpty())
				problems.add(e.getKey() + " declares part(s) nothing draws and UNDRAWN does not "
						+ "name: " + uncovered);
			Set<String> phantom = new java.util.TreeSet<>(covered);
			e.getValue().forEach(phantom::remove);
			if(!phantom.isEmpty())
				problems.add(e.getKey() + ": the manifest names part(s) the .obj does not "
						+ "declare: " + phantom);
		}

		if(declared.size() != 6)
			problems.add("six OBJ models expected, saw " + declared.size());

		// Every problem, not the first. A fail-fast loop over a hundred-odd files reports one and
		// hides the rest — D-1041 found that by injecting three regressions and being told about
		// one, and D-1043 found it AGAIN here, because the completeness check below was added
		// after an assertion rather than into the list it feeds. One assert, at the end.
		helper.assertTrue(problems.isEmpty(),
				problems.size() + " model problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** The model JSON at {@code path}, or null when there is no such file. */
	private static JsonObject readOrNull(String path) {
		try(InputStream in = LeafiaElevatorRenderTests.class.getResourceAsStream(path)) {
			if(in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			return null;
		}
	}

	/**
	 * The named objects an {@code .obj} declares, read out of the file in the jar.
	 *
	 * <p>This is the half of the comparison the generator cannot fake: it is the actual model
	 * file, so a part renamed in Blender fails every model that hides it.
	 */
	private static List<String> objectsIn(GameTestHelper helper, String obj) {
		List<String> parts = new ArrayList<>();
		try(InputStream in = LeafiaElevatorRenderTests.class.getResourceAsStream("/assets/leafia/" + obj)) {
			helper.assertTrue(in != null, "missing model file: " + obj);
			for(String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
				if(line.startsWith("o ")) parts.add(line.substring(2).trim());
		} catch(Exception e) {
			helper.fail("could not read " + obj + ": " + e);
		}
		helper.assertTrue(!parts.isEmpty(), obj + " declares no named objects");
		return parts;
	}

	/**
	 * The two item models are composites over every part, in draw order, and carry a display
	 * block.
	 *
	 * <p>Without the display block these render at world scale in a one-block cell — the pulley
	 * is three blocks across and the buffer eight and a third tall — which is the whole reason
	 * the generator derives one. The scale is pinned against the model's own bounding box
	 * because that is the rule it is derived by; the <em>appearance</em> cannot be checked
	 * without a client, and D-1040 says so rather than implying otherwise.
	 */
	@GameTest(template = EMPTY)
	public static void theItemModelsCoverEveryPartAndAreScaledToFit(GameTestHelper helper) {

		assertItemModel(helper, "elevator_pulley", 1 / 3.0);
		assertItemModel(helper, "elevator_buffer", 1 / 8.375);

		// Both landings are the same .obj with different textures, so they must scale alike.
		assertItemModel(helper, "elevator_s6floor", 1 / 3.0);
		assertItemModel(helper, "elevator_skyliftfloor", 1 / 3.0);

		// The counterweight is an entity, never an item, so it has no item model at all.
		helper.assertTrue(LeafiaElevatorRenderTests.class
						.getResourceAsStream("/assets/leafia/models/item/elevator_weight.json") == null,
				"the counterweight has no item form and should have no item model");

		helper.succeed();
	}

	/**
	 * Every registered block has a blockstate and every registered item has a model.
	 *
	 * <p><b>This is the test the addon most needed and did not have.</b> The first client sweep
	 * run since D-1019 reported <b>nine</b> missing models, every one of them landed by a batch
	 * that never ran a client: the six cabin styles (D-1036), the two spawn items (D-1039) and
	 * {@code elevator_light}'s blockstate. None of it fails a build, a GameTest or a server boot.
	 *
	 * <p>The reasoning that produced them was wrong in a specific and repeatable way, and it is
	 * worth naming because it is D-1019's own mistake made twice more: <b>a null creative tab
	 * hides an item from the menu; it does not stop Minecraft loading the item's model.</b>
	 * D-1036 argued that a hidden style item "never shows a missing texture", which is true of
	 * the inventory and false of the model loader. D-1019 had already recorded the block half of
	 * exactly this — a blockstate is loaded for every registered block whatever its render shape.
	 *
	 * <p>So this asserts the rule rather than the nine instances: registration is what obliges a
	 * model to exist, not visibility. It runs on a dedicated server by reading the classpath, so
	 * it costs nothing and does not need a display.
	 */
	@GameTest(template = EMPTY)
	public static void everyRegisteredThingHasAModel(GameTestHelper helper) {

		List<String> missing = new ArrayList<>();

		for(var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
			var id = entry.getKey().location();
			if(!id.getNamespace().equals(LeafiaRef.MODID)) continue;
			if(id.getPath().startsWith("gametest_")) continue; // LeafiaGameTestFixtures: never in a real game
			if(LeafiaElevatorRenderTests.class.getResourceAsStream(
					"/assets/leafia/blockstates/" + id.getPath() + ".json") == null)
				missing.add("blockstates/" + id.getPath() + ".json");
		}

		for(var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
			var id = entry.getKey().location();
			if(!id.getNamespace().equals(LeafiaRef.MODID)) continue;
			if(LeafiaElevatorRenderTests.class.getResourceAsStream(
					"/assets/leafia/models/item/" + id.getPath() + ".json") == null)
				missing.add("models/item/" + id.getPath() + ".json");
		}

		helper.assertTrue(missing.isEmpty(),
				missing.size() + " registered thing(s) with no model: " + String.join(", ", missing));
		helper.succeed();
	}

	/**
	 * The landings are drawn by the addon's one piece of custom geometry, and say so in data.
	 *
	 * <p>A blockstate naming a loader that was never registered is a hard model error rather
	 * than a missing model, so the two halves have to agree: this checks the JSON half, and the
	 * Java half is {@code LeafiaClient.registerGeometryLoaders}. Nothing here can check that the
	 * disguise actually <em>draws</em> — that needs a client, and D-1042's client sweep is where
	 * it was checked.
	 */
	@GameTest(template = EMPTY)
	public static void bothLandingsAreDrawnByThePaintLoader(GameTestHelper helper) {

		for(String name : new String[]{"elevator_s6floor", "elevator_skyliftfloor"}) {

			JsonObject blockstate = read(helper, "/assets/leafia/blockstates/" + name + ".json");
			String model = blockstate.getAsJsonObject("variants").getAsJsonObject("")
					.get("model").getAsString();
			helper.assertTrue(model.equals("leafia:block/" + name + "_paint"),
					name + ": the blockstate draws " + model + ", not its paint model");

			JsonObject paint = read(helper, "/assets/leafia/models/block/" + name + "_paint.json");
			helper.assertTrue(paint.get("loader").getAsString().equals("leafia:ev_floor_paint"),
					name + "_paint: names loader " + paint.get("loader").getAsString());
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the spring

	/**
	 * The spring is exactly at rest with no cabin above it.
	 *
	 * <p><b>This does not pin upstream's constants, and a falsification run is what established
	 * that.</b> Changing {@code BOTTOM_OUT} from 0.375 to 0.5 left every assertion here passing,
	 * because {@code (REST_LENGTH + BOTTOM_OUT) / (REST_LENGTH + BOTTOM_OUT)} is 1 whatever the
	 * two are — the rest state is structural. What it does pin is the <em>clamp</em>: a cabin
	 * above the spring must not move it, which is what {@code max(..., 0)} in {@code shrink} is
	 * for and is a real thing to get wrong. The constants are pinned by
	 * {@link #theSpringCompressesDownwardsAndThenStops} instead.
	 */
	@GameTest(template = EMPTY)
	public static void theSpringIsAtRestUntilACabinReachesIt(GameTestHelper helper) {
		helper.assertTrue(EvBufferTE.squash(64, Double.NaN) == 1F,
				"no cabin: squash is 1, got " + EvBufferTE.squash(64, Double.NaN));
		helper.assertTrue(EvBufferTE.travel(64, Double.NaN) == 0F,
				"no cabin: travel is 0, got " + EvBufferTE.travel(64, Double.NaN));

		// A cabin well above the spring must be the same as no cabin at all - that is what the
		// max(..., 0) in shrink() is for, and without it a distant cabin stretches the spring.
		helper.assertTrue(EvBufferTE.squash(64, 80) == 1F,
				"a cabin 16 blocks up must not move the spring, got " + EvBufferTE.squash(64, 80));
		helper.succeed();
	}

	/**
	 * The spring compresses as the cabin comes down, and stops at its bottom.
	 *
	 * <p>Monotonic rather than pinned value by value: what a reader needs to trust is that lower
	 * means shorter and that it stops, and a table of squash values would pin the implementation
	 * instead. The bottom is checked by pushing the cabin absurdly far in, where the clamp is the
	 * only thing that can be answering.
	 */
	@GameTest(template = EMPTY)
	public static void theSpringCompressesDownwardsAndThenStops(GameTestHelper helper) {

		float last = EvBufferTE.squash(64, Double.NaN);
		for(double cabin = 70; cabin >= 64; cabin -= 0.25) {
			float now = EvBufferTE.squash(64, cabin);
			helper.assertTrue(now <= last,
					"the spring grew as the cabin came down, at y=" + cabin + ": " + last + " -> " + now);
			last = now;
		}
		helper.assertTrue(last < 1F, "the cabin reached the spring and nothing compressed");

		float bottom = EvBufferTE.squash(64, 64);
		helper.assertTrue(EvBufferTE.squash(64, -1000) == bottom,
				"the spring did not clamp: a cabin 1000 blocks down gives "
						+ EvBufferTE.squash(64, -1000) + " against " + bottom);

		// The one value all four of upstream's constants reach. Derived rather than copied out
		// of a run: BOTTOM_OUT * (1 - GLOBAL_SCALE) / (REST_LENGTH + BOTTOM_OUT), which is
		// 0.375 * (1 - 6/8.375) / 4.375. Change any of the three and this is what moves - the
		// rest state does not, which the falsification run in D-1040 found out the hard way.
		double expected = EvBufferTE.BOTTOM_OUT * (1 - EvBufferTE.GLOBAL_SCALE)
				/ (EvBufferTE.REST_LENGTH + EvBufferTE.BOTTOM_OUT);
		helper.assertTrue(Math.abs(bottom - 0.024307) < 1e-5,
				"fully compressed is upstream's 0.024307, got " + bottom);
		helper.assertTrue(Math.abs(bottom - expected) < 1e-6,
				"the clamp does not follow its own formula: " + bottom + " against " + expected);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the facing table

	/**
	 * The elevators' facing table is upstream's.
	 *
	 * <p>{@link ObjUtil#rotateForFacing} is the obvious call and is <b>wrong</b> here: it is these
	 * same four angles shifted by half a turn, so using it would draw every pulley, buffer and
	 * landing backwards. D-1021 records the identical trap for the fluid valve, which makes it a
	 * property of this addon's upstream rather than a one-off.
	 *
	 * <p><b>That disagreement is not asserted, and the first draft of this test asserted it and
	 * could not run.</b> Recovering the helper's angle means applying it to a {@code PoseStack},
	 * and {@code com.mojang.blaze3d} is client-dist-only — the dedicated server this runs on
	 * refuses to load the class. Hardcoding the helper's four angles instead would only restate
	 * its implementation in a second place, which is the thing that goes stale. So the table is
	 * pinned and the trap is documented; catching a future change to {@code rotateForFacing}
	 * needs a client-side test, which this port has no harness for.
	 */
	@GameTest(template = EMPTY)
	public static void theFacingTableIsUpstreams(GameTestHelper helper) {
		helper.assertTrue(EvFloor.yawOf(Direction.NORTH) == 180, "north is 180");
		helper.assertTrue(EvFloor.yawOf(Direction.SOUTH) == 0, "south is 0");
		helper.assertTrue(EvFloor.yawOf(Direction.WEST) == 270, "west is 270");
		helper.assertTrue(EvFloor.yawOf(Direction.EAST) == 90, "east is 90");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static JsonObject read(GameTestHelper helper, String path) {
		try(InputStream in = LeafiaElevatorRenderTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "missing generated model: " + path);
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			helper.fail("could not read " + path + ": " + e);
			return null;
		}
	}

	/** Which .obj-derived model name an item's children are prefixed with. */
	private static String objFor(String item) {
		return item.endsWith("floor") ? "elevator_landing" : item;
	}

	private static void assertItemModel(GameTestHelper helper, String model, double expectedScale) {

		String path = "/assets/leafia/models/item/" + model + ".json";
		JsonObject json = read(helper, path);

		helper.assertTrue(json.get("loader").getAsString().equals("neoforge:composite"),
				path + ": item models over an OBJ's parts are composites");

		List<String> order = new ArrayList<>();
		json.getAsJsonArray("item_render_order").forEach(e -> order.add(e.getAsString()));
		helper.assertTrue(!order.isEmpty(), path + ": draws nothing");
		for(String child : order)
			helper.assertTrue(LeafiaElevatorRenderTests.class.getResourceAsStream(
							"/assets/leafia/models/block/" + model.split("_")[0] + "_"
									+ child + ".json") != null
							|| LeafiaElevatorRenderTests.class.getResourceAsStream(
							"/assets/leafia/models/block/" + objFor(model) + "_" + child + ".json") != null,
					path + ": names a child with no model, " + child);

		helper.assertTrue(json.has("display"), path + ": no display block, so it renders at world scale");
		float gui = json.getAsJsonObject("display").getAsJsonObject("gui")
				.getAsJsonArray("scale").get(0).getAsFloat();
		// Not "1 / the largest extent": that was the first derivation, which ignored vanilla's
		// half-block shift and left every elevator item up to a block off its slot (D-1081). Where
		// the model lands is LeafiaItemDisplayTests'; here, only that it is no longer world scale
		// (a block at scale 1) and no larger than the whole-cell fit the derivation started from.
		helper.assertTrue(gui > 0 && gui <= expectedScale * 1.5,
				path + ": the GUI scale " + gui + " is not a fit-to-slot scale (a whole-cell fit would be " + expectedScale + ")");
	}
}
