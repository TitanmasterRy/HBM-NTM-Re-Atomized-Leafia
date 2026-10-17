package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The permanent guard for D-1092: every addon block and item a player can see has an English name.
 *
 * <p>The list is built from the <b>registries</b>, not from the files under {@code models/} or
 * {@code blockstates/}: those hold model-only variants ({@code *_shield_blocking},
 * {@code advisor_open}) that no registered thing is called, and the file comparison that first
 * found this gap over-counted by exactly that. The question a player's screen asks is "what is
 * this registered thing's {@code getDescriptionId()}, and does the language file have it", so
 * that is what is asked here.
 *
 * <p>Upstream never wrote a name for {@link #UNNAMED_UPSTREAM} (D-1085 listed nine items; the
 * rest are blocks and items of the same kind). They keep their raw key on purpose - inventing a
 * name would be a divergence from upstream with nothing to cite - and the test pins that set
 * <em>exactly</em>: a name that gets added must leave the list, and a name that goes missing
 * cannot hide in it.
 *
 * <p>Blocks that have no item and are not placeable by any route a player takes are a separate
 * question: their name can only appear in a death message or a debug screen. They are not
 * exempted by guesswork - the second test reports how many each category holds, and the first
 * one exempts nothing but the pinned set.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaLangNameTests {

	private static final String EMPTY = "empty";

	/**
	 * Description ids upstream's {@code en_us.lang} has no entry for, so the raw key is what
	 * upstream shows too: the nine items D-1085 listed, and the catwalk railing, which a player
	 * does hold. (The 231 mixed concretes and 55 lined asphalts are in the same position upstream
	 * but are given composed names by a generator script - D-1092.)
	 */
	private static final Set<String> UNNAMED_UPSTREAM = Set.of(
			"block.leafia.catwalk_railing_curved",
			"block.leafia.crimson_door_small", "block.leafia.crimson_door_large",
			"item.leafia.dfcsh_beam", "item.leafia.dfcsh_cable", "item.leafia.dfcsh_core",
			"item.leafia.dfcsh_corner", "item.leafia.dfcsh_front",
			"item.leafia.component_emitter", "item.leafia.component_limiter",
			"item.leafia.grenade_cloud", "item.leafia.grenade_pink_cloud",
			// The PWR's wreck blocks and debris items (D-3010): upstream names neither wreck, and the debris
			// items compose their shown name from the block they carry, around these raw keys.
			"block.leafia.lwrwreck_stone", "block.leafia.lwrwreck_metal",
			"item.leafia.lwr_piece", "item.leafia.lwr_shrapnel", "item.leafia.lwr_shard",
			// The storage rack (D-3021): upstream's lang has no tile.sellacity_rack.name.
			"block.leafia.sellacity_rack",
			// The APR's component block (D-3027): upstream names each variant (tile.apr_component.<v>.name, every
			// variant item here) and not the block, which a player holds only as one of its eight items.
			"block.leafia.apr_component");

	/**
	 * Blocks with no item and no creative-tab entry: invisible markers an elevator car or a light
	 * pass leaves behind, which a player can never hold. Their name could only appear in a debug
	 * screen or a death message. {@link #theExemptionsAreReal} proves each has no item.
	 */
	private static final Set<String> HIDDEN_MARKERS = Set.of(
			"block.leafia.light_emitter", "block.leafia.elevator_light");

	private static JsonObject lang() {
		try(InputStream in = LeafiaLangNameTests.class.getResourceAsStream("/assets/leafia/lang/en_us.json")) {
			if(in == null) throw new IllegalStateException("missing en_us.json");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Description id -> what it names, for everything registered under the addon namespace. */
	private static TreeMap<String, String> registered() {
		TreeMap<String, String> out = new TreeMap<>();
		for(Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			// gametest_*: blocks the suite registers itself (LeafiaGameTestFixtures), never in a real game.
			if(id.getNamespace().equals(LeafiaRef.MODID) && !id.getPath().startsWith("gametest_")) out.put(block.getDescriptionId(), "block " + id.getPath());
		}
		for(Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			// The addon's autogen plates, receivers and wire (D-3026) are named by the base mod's shape key with the material's
			// name in it ("Welded %s Plate"): the shape's key is the base's, and LeafiaMatsTests pins the materials' names.
			if(item instanceof com.hbm.items.special.ItemAutogen) continue;
			if(id.getNamespace().equals(LeafiaRef.MODID)) out.putIfAbsent(item.getDescriptionId(), "item " + id.getPath());
		}
		return out;
	}

	/** Every id the addon's creative tab holds - what a player can actually pick up. */
	private static Set<ResourceLocation> inTab() {
		Set<ResourceLocation> out = new HashSet<>();
		for(Supplier<? extends ItemLike> s : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN))
			out.add(BuiltInRegistries.ITEM.getKey(s.get().asItem()));
		return out;
	}

	@GameTest(template = EMPTY)
	public static void everyRegisteredBlockAndItemHasAnEnglishName(GameTestHelper helper) {
		JsonObject lang = lang();
		Set<String> missing = new TreeSet<>();
		for(var e : registered().entrySet())
			if(!lang.has(e.getKey())) missing.add(e.getKey());
		Set<String> unexpected = new TreeSet<>(missing);
		unexpected.removeAll(UNNAMED_UPSTREAM);
		unexpected.removeAll(HIDDEN_MARKERS);
		helper.assertTrue(unexpected.isEmpty(),
				unexpected.size() + " registered addon blocks/items show a raw key: " + unexpected);
		Set<String> stale = new TreeSet<>(UNNAMED_UPSTREAM);
		stale.addAll(HIDDEN_MARKERS);
		stale.removeAll(missing);
		helper.assertTrue(stale.isEmpty(), "named now, so no longer 'unnamed upstream': " + stale);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theUnnamedSetIsRealAndReachable(GameTestHelper helper) {
		// Every exempted key must belong to something registered (a typo here would exempt nothing),
		// and a name for an item the tab holds is the case worth knowing about: say which.
		var registered = registered();
		for(String key : UNNAMED_UPSTREAM)
			helper.assertTrue(registered.containsKey(key), key + " is exempted but nothing registered has it");
		helper.assertTrue(!inTab().isEmpty(), "the creative tab holds nothing - the seam is broken");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theExemptionsAreReal(GameTestHelper helper) {
		Set<ResourceLocation> tab = inTab();
		for(String key : HIDDEN_MARKERS) {
			// block.leafia.<path> -> the id of the thing the key names
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, key.substring("block.leafia.".length()));
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), key + " is exempted but is not a registered block");
			helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(id), key + " has an item, so a player can hold it - it needs a name");
			helper.assertTrue(!tab.contains(id), key + " is in the creative tab - it needs a name");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void signsAndTheElevatorItemsReadAsUpstreamDoes(GameTestHelper helper) {
		JsonObject lang = lang();
		// Upstream: tile.letter_sign.name = "Large Sign %s", formatted with the letter.
		helper.assertTrue("Large Sign A".equals(str(lang, "block.leafia.letter_sign_a")), "sign A: " + str(lang, "block.leafia.letter_sign_a"));
		helper.assertTrue("Large Sign 7".equals(str(lang, "block.leafia.letter_sign_7")), "sign 7: " + str(lang, "block.leafia.letter_sign_7"));
		// Upstream's item.ev_spawn.name, and the tooltip and error keys the item reads by their own spelling.
		helper.assertTrue("Modular Elevator".equals(str(lang, "item.leafia.ev_spawn")), "ev_spawn name");
		for(String key : new String[] {"item.ev_spawn.desc", "item.ev_spawn.error.pulley", "item.ev_spawn.error.direction",
				"item.ev_chip_s6.desc", "item.ev_chip_skylift.desc"})
			helper.assertTrue(lang.has(key), "missing tooltip/error line " + key);
		// A composed name, spot-checked against the base mod's own words for the halves.
		helper.assertTrue("Lined Asphalt (X N W)".equals(str(lang, "block.leafia.asphalt_lined_xnw")), "lined asphalt xnw");
		helper.assertTrue(str(lang, "block.leafia.concrete__mixed_brick_brickm").equals("Mixed Concrete (Concrete Bricks / Mossy Concrete Bricks)"),
				"mixed brick/brickm: " + str(lang, "block.leafia.concrete__mixed_brick_brickm"));
		helper.succeed();
	}

	private static String str(JsonObject lang, String key) {
		return lang.has(key) ? lang.get(key).getAsString() : null;
	}
}
