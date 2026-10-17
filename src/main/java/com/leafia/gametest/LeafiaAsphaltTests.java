package com.leafia.gametest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.ModBlocks;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.generic.lined_asphalt.AsphaltBlock;
import com.leafia.contents.building.generic.lined_asphalt.LinedAsphaltBlock;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fifty-five lined asphalts, and the painting that reaches them.
 *
 * <h2>What these cover</h2>
 *
 * The family is almost entirely generated, so the interesting failures are arithmetic ones: a
 * combination with no texture, a fold that points at a block nobody registered, a lookup that
 * does not agree with the registration loop. Those are all server-side and all checked here
 * against the generator's own manifest rather than against a copied table.
 *
 * <p>The one piece of real behaviour is {@code paintBlock}, and that is exercised in a world:
 * place asphalt, click it, see which block it became.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaAsphaltTests {

	private static final String EMPTY = "empty";

	/**
	 * Every combination the generator wrote is registered, and nothing else is.
	 *
	 * <p>Both directions, because the two failures look nothing alike: a block with no model is
	 * a purple-and-black cube in the creative menu, and a model with no block is a file nothing
	 * ever loads. The manifest is the generator's own output, so neither side is hand-copied.
	 */
	@GameTest(template = EMPTY)
	public static void everyCombinationIsRegisteredAndDrawn(GameTestHelper helper) {

		JsonObject manifest = read(helper, "/assets/leafia/asphalt_lines.json");
		Set<String> combos = new TreeSet<>();
		manifest.getAsJsonArray("combinations").forEach(c -> combos.add(c.getAsString()));

		List<String> problems = new ArrayList<>();

		if(combos.size() != 55)
			problems.add("the generator wrote " + combos.size() + " combinations, not 55");

		for(String combo : combos) {
			if(!LeafiaBlocks.lined_asphalt.containsKey(combo))
				problems.add(combo + " is generated but not registered");
			if(LeafiaAsphaltTests.class.getResourceAsStream(
					"/assets/leafia/blockstates/asphalt_lined_" + combo + ".json") == null)
				problems.add(combo + " has no blockstate");
			if(LeafiaAsphaltTests.class.getResourceAsStream(
					"/assets/leafia/models/item/asphalt_lined_" + combo + ".json") == null)
				problems.add(combo + " has no item model");
		}

		// The registry, minus the empty key, which is the base mod's plain asphalt.
		for(String key : LeafiaBlocks.lined_asphalt.keySet())
			if(!key.isEmpty() && !combos.contains(key))
				problems.add(key + " is registered but the generator did not write it");

		// One assert, at the end. Anything else reports one of fifty-five - D-1041, D-1043,
		// D-1046, which is three batches running.
		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The eight folded combinations resolve to a registered block, and are not registered
	 * themselves.
	 *
	 * <p>The fold is how a set like "a full X line plus both halves either side of it" is
	 * recognised as the same picture as the X line alone. If a fold pointed at something
	 * unregistered, painting a line would silently do nothing at exactly those eight moments —
	 * the kind of gap you find by building a road, not by reading the code.
	 */
	@GameTest(template = EMPTY)
	public static void theEightFoldedSetsResolveToRealBlocks(GameTestHelper helper) {

		JsonObject manifest = read(helper, "/assets/leafia/asphalt_lines.json");
		JsonObject folds = manifest.getAsJsonObject("replacements");
		List<String> problems = new ArrayList<>();

		if(folds.size() != 8) problems.add("expected 8 folds, the manifest has " + folds.size());

		for(Map.Entry<String, com.google.gson.JsonElement> fold : folds.entrySet()) {
			String from = fold.getKey();
			String to = fold.getValue().getAsString();

			if(LeafiaBlocks.lined_asphalt.containsKey(from))
				problems.add(from + " is folded away and also registered");
			if(LeafiaBlocks.linedAsphalt(from) == null)
				problems.add(from + " folds to " + to + ", which resolves to nothing");
			else if(LeafiaBlocks.linedAsphalt(from) != LeafiaBlocks.linedAsphalt(to))
				problems.add(from + " and " + to + " should be the same block");
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " fold problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** The empty set is the base mod's plain asphalt, which is what makes the lookup total. */
	@GameTest(template = EMPTY)
	public static void theEmptySetIsTheBaseModsAsphalt(GameTestHelper helper) {
		helper.assertTrue(LeafiaBlocks.linedAsphalt("") == ModBlocks.asphalt.get(),
				"the empty line set must resolve to hbm:asphalt");
		helper.assertTrue(LeafiaBlocks.linedAsphalt("qq") == null,
				"a set nothing draws must resolve to null, not to a default");
		helper.succeed();
	}

	// ------------------------------------------------------------------ painting

	/**
	 * A main-hand click paints the half line toward the edge you clicked.
	 *
	 * <p>All four edges, because the four branches are four separate chances to get a sign
	 * wrong — and upstream's own code tests {@code hitX > 0} for east but {@code hitZ < 0} for
	 * north, so three of the four read one way and one reads the other.
	 */
	@GameTest(template = EMPTY)
	public static void aMainHandClickPaintsTheNearerHalfLine(GameTestHelper helper) {

		record Case(double x, double z, String expect) {}
		List<String> problems = new ArrayList<>();

		for(Case c : List.of(
				new Case(0.95, 0.50, "e"),
				new Case(0.05, 0.50, "w"),
				new Case(0.50, 0.05, "n"),
				new Case(0.50, 0.95, "s"))) {

			BlockPos pos = new BlockPos(1, 1, 1);
			helper.setBlock(pos, ModBlocks.asphalt.get());
			var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_DYE));

			boolean painted = AsphaltBlock.paintBlock(helper.getLevel(), helper.absolutePos(pos),
					player, InteractionHand.MAIN_HAND, c.x(), c.z());

			Block now = helper.getBlockState(pos).getBlock();
			if(!painted) problems.add(c.expect() + ": the click did not paint");
			else if(!(now instanceof LinedAsphaltBlock lined))
				problems.add(c.expect() + ": became " + now + ", not a lined asphalt");
			else if(!lined.lines.equals(c.expect()))
				problems.add("clicking (" + c.x() + ", " + c.z() + ") drew " + lined.lines
						+ ", should draw " + c.expect());
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " painting problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/** An off-hand click paints the full line across, not the half line. */
	@GameTest(template = EMPTY)
	public static void anOffHandClickPaintsTheFullLine(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModBlocks.asphalt.get());
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHITE_DYE));

		helper.assertTrue(AsphaltBlock.paintBlock(helper.getLevel(), helper.absolutePos(pos),
				player, InteractionHand.OFF_HAND, 0.95, 0.5), "the off-hand click did not paint");

		Block now = helper.getBlockState(pos).getBlock();
		helper.assertTrue(now instanceof LinedAsphaltBlock lined && lined.lines.equals("x"),
				"an off-hand click near the east edge draws the full x line, got " + now);
		helper.succeed();
	}

	/**
	 * Painting stacks, and repainting a segment that is already there re-sets the same block.
	 *
	 * <p>The second half is upstream's and is <b>not</b> the no-op it looks like. {@code
	 * paintBlock} builds the new set, finds the block for it and sets it — without ever asking
	 * whether that is the block already standing there. So repainting writes the same state back
	 * and <b>reports the click handled</b>, which stops it falling through to the held item.
	 *
	 * <p>This test asserted the opposite when it was written, on the reasonable-sounding guess
	 * that "nothing changed" would be reported as nothing changed. The first run said otherwise.
	 * Since the dye is never consumed either, a player cannot tell the difference — but a caller
	 * can, and {@code AsphaltPainting} cancels the event on the strength of it.
	 */
	@GameTest(template = EMPTY)
	public static void paintingStacksAndRepaintingDoesNothing(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, ModBlocks.asphalt.get());
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_DYE));

		AsphaltBlock.paintBlock(helper.getLevel(), abs, player, InteractionHand.MAIN_HAND, 0.95, 0.5);
		AsphaltBlock.paintBlock(helper.getLevel(), abs, player, InteractionHand.MAIN_HAND, 0.5, 0.05);

		Block corner = helper.getBlockState(pos).getBlock();
		helper.assertTrue(corner instanceof LinedAsphaltBlock lined && lined.lines.equals("ne"),
				"east then north should give the 'ne' corner, got " + corner);

		helper.assertTrue(AsphaltBlock.paintBlock(helper.getLevel(), abs, player,
						InteractionHand.MAIN_HAND, 0.5, 0.05),
				"upstream reports a repaint handled, because it sets the block either way");
		helper.assertTrue(helper.getBlockState(pos).getBlock() == corner,
				"and the block must be the same one it already was");
		helper.succeed();
	}

	/** Anything but a white dye leaves the asphalt alone. */
	@GameTest(template = EMPTY)
	public static void onlyAWhiteDyePaints(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModBlocks.asphalt.get());
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

		for(net.minecraft.world.item.Item item : List.of(Items.BLACK_DYE, Items.YELLOW_DYE,
				Items.STICK, Items.AIR)) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			helper.assertTrue(!AsphaltBlock.paintBlock(helper.getLevel(), helper.absolutePos(pos),
							player, InteractionHand.MAIN_HAND, 0.95, 0.5),
					item + " should not paint a line");
		}
		helper.assertTrue(helper.getBlockState(pos).getBlock() == ModBlocks.asphalt.get(),
				"the asphalt should still be unpainted");
		helper.succeed();
	}

	// ------------------------------------------------------------------ loot

	/**
	 * Every addon block has a loot table, and every loot table has a block.
	 *
	 * <p><b>The gap this closes was invisible in every way a gap can be.</b> In 1.21.1 a
	 * block's drops are a datapack file named after the block; a block with no such file
	 * resolves to an empty table and nothing is logged — not at datapack load, not when the
	 * block breaks. This addon had shipped none at all, so 49 blocks vanished when a player
	 * broke them and no build, GameTest, server boot or client run had ever said so. Found
	 * while writing the lined asphalts' drops, which needed a table for a reason of their own
	 * (D-1047), and swept in D-1048.
	 *
	 * <p>Asserted <b>both ways</b>, because the generator that writes them parses
	 * {@code LeafiaBlocks.java} and a parse can be wrong in either direction: a block it misses
	 * keeps the defect, and a name it invents leaves a file that nothing will ever load. The
	 * first run found exactly that — 50 tables written against 49 blocks needing one.
	 *
	 * <p>This is the authority, not the generator: it walks the real block registry on a
	 * running server.
	 */
	@GameTest(template = EMPTY)
	public static void everyBlockHasALootTableAndEveryLootTableHasABlock(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		Set<String> expected = new TreeSet<>();

		// The lined asphalts drop the base mod's plain asphalt rather than themselves, which is
		// the one drop in this addon that is not drop-self.
		//
		// Into `problems`, not an assert of its own. This loop WAS an assert of its own, and a
		// three-regression run reported the wrong drop and hid both of the others - the fourth
		// time this campaign (D-1041, D-1043, D-1046, and here). The rule, restated: in a method
		// that accumulates, nothing asserts until the end, including the checks that were there
		// before the accumulating part was added.
		for(String combo : new String[]{"x", "ne", "xznswe"}) {
			String path = "/data/leafia/loot_table/blocks/asphalt_lined_" + combo + ".json";
			JsonObject table = readOrNull(path);
			if(table == null) {
				problems.add(path + " is missing");
				continue;
			}
			String drops = table.getAsJsonArray("pools").get(0).getAsJsonObject()
					.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
			if(!drops.equals("hbm:asphalt"))
				problems.add(path + " drops " + drops + ", should drop hbm:asphalt");
		}

		for(var entry : BuiltInRegistries.BLOCK.entrySet()) {
			var id = entry.getKey().location();
			if(!id.getNamespace().equals(LeafiaRef.MODID)) continue;

			// noLootTable() is a deliberate "drops nothing" - the light emitter and the cabin's
			// ceiling light, whose upstream getItemDropped returns AIR. Not a gap.
			if(entry.getValue().getLootTable()
					== net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY) continue;

			expected.add(id.getPath());
			if(LeafiaAsphaltTests.class.getResourceAsStream(
					"/data/leafia/loot_table/blocks/" + id.getPath() + ".json") == null)
				problems.add("no loot table for " + id.getPath() + ", so it drops nothing");
		}

		// The other direction. A table for a block that does not exist is dead weight and, more
		// usefully, it is the signature of a generator that guessed a name wrong.
		for(String name : shippedTables(helper))
			if(!expected.contains(name))
				problems.add("loot_table/blocks/" + name + ".json names no registered block");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " loot problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every drop-self table loads only while the item it names exists (D-3050).
	 *
	 * <p>A block behind a switch that is off has no item, and an unconditional table naming that
	 * item is an ERROR in the log on every boot and every reload - the client QA sweep found five,
	 * the DFC overhaul's four blocks and the RBMK debris smoke, at the default config, and the 231 mixed concretes would
	 * be 231 more with {@code features.mixedConcrete} off (both generators guard them now). A GameTest
	 * server switches every overhaul on, so the suite never sees the error itself; this pins the
	 * two halves that prevent it: every drop-self table carries {@code neoforge:item_exists} for
	 * its own item, and NeoForge's loot codec really skips such a table, without an error, when the
	 * item is missing (and fails on the same table with the condition taken off).
	 */
	@GameTest(template = EMPTY)
	public static void aDropSelfTableLoadsOnlyWhileItsItemExists(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		int dropSelf = 0;
		for(String name : shippedTables(helper)) {
			JsonObject table = readOrNull("/data/leafia/loot_table/blocks/" + name + ".json");
			if(table == null || !table.has("pools")) continue;
			var pools = table.getAsJsonArray("pools");
			if(pools.size() != 1) continue;
			var entries = pools.get(0).getAsJsonObject().getAsJsonArray("entries");
			if(entries.size() != 1 || !entries.get(0).getAsJsonObject().has("name")) continue;
			String drops = entries.get(0).getAsJsonObject().get("name").getAsString();
			// Drop-self, and the lit meteor beacon's drop of its unlit twin (gen_leafia_loot's DROPS_OTHER).
			if(!drops.equals("leafia:" + name) && !(name.equals("meteor_diverter") && drops.equals("leafia:meteor_diverter_unlit"))) continue;
			if(entries.get(0).getAsJsonObject().has("functions") || pools.get(0).getAsJsonObject().has("functions")) continue;
			dropSelf++;
			boolean guarded = false;
			if(table.has("neoforge:conditions"))
				for(var c : table.getAsJsonArray("neoforge:conditions")) {
					JsonObject o = c.getAsJsonObject();
					if("neoforge:item_exists".equals(o.get("type").getAsString()) && drops.equals(o.get("item").getAsString())) guarded = true;
				}
			if(!guarded) problems.add(name);
		}
		helper.assertTrue(dropSelf > 150, "only " + dropSelf + " drop-self tables found; the reader is broken");

		// The mechanism: the same table for an item nobody registers.
		var codec = net.minecraft.world.level.storage.loot.LootDataType.TABLE.conditionalCodec();
		var ops = new net.neoforged.neoforge.common.conditions.ConditionalOps<>(
				net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, helper.getLevel().registryAccess()),
				net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY);
		String absent = "leafia:not_a_registered_block";
		JsonObject guardedTable = JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:item_exists\",\"item\":\"" + absent
				+ "\"}],\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + absent + "\"}]}]}").getAsJsonObject();
		var skipped = codec.parse(ops, guardedTable);
		helper.assertTrue(skipped.isSuccess() && skipped.getOrThrow().isEmpty(), "a guarded table for a missing item was not skipped quietly: " + skipped);
		JsonObject bare = guardedTable.deepCopy();
		bare.remove("neoforge:conditions");
		helper.assertTrue(codec.parse(ops, bare).isError(), "an unguarded table for a missing item parsed - this test no longer shows the error it guards against");

		helper.assertTrue(problems.isEmpty(), problems.size() + " drop-self table(s) load whether or not their item exists: " + String.join(", ", problems));
		helper.succeed();
	}

	/**
	 * A block actually broken in a world actually drops something.
	 *
	 * <p><b>This is the test the previous one is not.</b> Checking that a file exists is a
	 * file-presence check, and file-presence checks are exactly what missed this defect for
	 * every batch so far — the files were absent and every check passed. So one block is broken
	 * for real and the dropped item is looked for on the floor.
	 *
	 * <p>Two blocks, deliberately: a plain one that drops itself, and a lined asphalt, which is
	 * the addon's only block that drops something else. One of each rule.
	 */
	@GameTest(template = EMPTY)
	public static void breakingABlockReallyDropsIt(GameTestHelper helper) {

		record Case(Block place, String expect) {}
		List<String> problems = new ArrayList<>();

		for(Case c : List.of(
				new Case(LeafiaBlocks.catwalk_railing_curved.get(), "leafia:catwalk_railing_curved"),
				new Case(LeafiaBlocks.linedAsphalt("x"), "hbm:asphalt"))) {

			BlockPos pos = new BlockPos(1, 2, 1);
			helper.setBlock(pos, c.place());
			helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

			List<String> found = new ArrayList<>();
			helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(3))
					.forEach(e -> found.add(BuiltInRegistries.ITEM.getKey(
							e.getItem().getItem()).toString()));

			if(!found.contains(c.expect()))
				problems.add(c.place() + " dropped " + found + ", expected " + c.expect());

			helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(3))
					.forEach(net.minecraft.world.entity.Entity::discard);
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " drop problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every loot table shipped under {@code data/leafia/loot_table/blocks}.
	 *
	 * <p>Read out of the jar rather than off disk: a GameTest server has a classpath, not a
	 * working directory, and the point is to check what actually ships.
	 */
	private static Set<String> shippedTables(GameTestHelper helper) {
		Set<String> out = new TreeSet<>();
		try {
			var url = LeafiaAsphaltTests.class.getResource("/data/leafia/loot_table/blocks");
			helper.assertTrue(url != null, "the addon ships no block loot tables at all");
			var uri = url.toURI();
			java.nio.file.Path dir;
			if("jar".equals(uri.getScheme())) {
				var fs = java.nio.file.FileSystems.newFileSystem(uri, java.util.Map.of());
				dir = fs.getPath("/data/leafia/loot_table/blocks");
			} else {
				dir = java.nio.file.Path.of(uri);
			}
			try(var stream = java.nio.file.Files.list(dir)) {
				stream.forEach(f -> {
					String n = f.getFileName().toString();
					if(n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
				});
			}
		} catch(Exception e) {
			helper.fail("could not list the shipped loot tables: " + e);
		}
		return out;
	}

	// ------------------------------------------------------------------ helpers

	/** The JSON at {@code path}, or null. Never throws — for use inside an accumulating loop. */
	private static JsonObject readOrNull(String path) {
		try(InputStream in = LeafiaAsphaltTests.class.getResourceAsStream(path)) {
			if(in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			return null;
		}
	}

	private static JsonObject read(GameTestHelper helper, String path) {
		try(InputStream in = LeafiaAsphaltTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "missing generated file: " + path);
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch(Exception e) {
			helper.fail("could not read " + path + ": " + e);
			return null;
		}
	}
}
