package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.blocks.fluid.GenericFiniteFluid;
import com.hbm.config.FalloutConfigJSON;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.gametest.TickWindow;
import com.hbm.handler.radiation.ChunkRadiationHandler;
import com.hbm.handler.radiation.ChunkRadiationHandlerSimple;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.items.ModItems;
import com.hbm.potion.NtmEffects;
import com.hbm.util.LegacyMaterial;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.bomb.balefire.AshBalefire;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;
import com.leafia.contents.bomb.digamma.DigammititeBlock;
import com.leafia.contents.fluids.BaleCoriumFluid;
import com.leafia.contents.fluids.BaleCoriumFluid.BaleCoriumFluidBlock;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.dev.blocks.legacy.MetaLevel;
import com.leafia.dev.blocks.legacy.RadImmuneSpawn;
import com.leafia.init.FalloutConfigInit;
import com.leafia.init.LeafiaFalloutEntry;
import com.leafia.init.LeafiaHazards;
import com.leafia.lib.LeafiaRef;
import com.llib.math.LeafiaColor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The balefire and digamma residue, the legacy contamination blocks, the fallout table that makes
 * them, and balefire corium (D-3004).
 *
 * <p>Every behaviour is driven by calling the tick, with a random source that answers the way the
 * test says ({@link Script}), because a one-in-fifty cooling cannot be waited for and a test that
 * waits for it fails on the day it does not happen. The script also records the <em>bound</em> of
 * every draw, which pins the order of upstream's draws as well as their values.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaResidueTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	// ------------------------------------------------------------------ helpers

	/**
	 * A random source that answers {@code nextInt(bound)} from a list, in order, and records the bound
	 * it was asked for. When the list runs out it answers {@code bound - 1}: a miss for every one-in-n
	 * roll, and never zero.
	 */
	static final class Script extends LegacyRandomSource {
		private final int[] answers;
		private int next;
		final List<Integer> bounds = new ArrayList<>();

		Script(int... answers) {
			super(0L);
			this.answers = answers;
		}

		@Override
		public int nextInt(int bound) {
			bounds.add(bound);
			return next < answers.length ? answers[next++] : bound - 1;
		}
	}

	private static BlockState ash(int layers) {
		return LeafiaBlocks.ash_balefire.get().defaultBlockState().setValue(AshBalefire.LAYERS, layers);
	}

	private static void ironFloor(GameTestHelper helper, int size) {
		for(int x = 0; x < size; x++)
			for(int z = 0; z < size; z++)
				helper.setBlock(x, 0, z, Blocks.IRON_BLOCK);
	}

	/** Runs a body with a handler of its own in the proxy slot, and puts the real one back. */
	private static void withHandler(GameTestHelper helper, Consumer<ChunkRadiationHandlerSimple> body) {
		ChunkRadiationHandler saved = ChunkRadiationManager.proxy;
		ChunkRadiationHandlerSimple handler = new ChunkRadiationHandlerSimple();
		handler.trackWorld(helper.getLevel());
		ChunkRadiationManager.proxy = handler;
		try {
			body.accept(handler);
		} finally {
			ChunkRadiationManager.proxy = saved;
		}
	}

	private static float rad(ChunkRadiationHandlerSimple handler, GameTestHelper helper, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		return handler.getRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ());
	}

	private static List<ItemStack> drops(GameTestHelper helper, BlockState state, BlockPos rel) {
		return drops(helper, state, rel, ItemStack.EMPTY);
	}

	private static List<ItemStack> drops(GameTestHelper helper, BlockState state, BlockPos rel, ItemStack tool) {
		LootParams.Builder builder = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(rel)))
				.withParameter(LootContextParams.TOOL, tool);
		return state.getDrops(builder);
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static boolean residue() {
		return LeafiaFeatures.balefireResidue;
	}

	private static MetaBlock meta(String name) {
		return (MetaBlock) BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name));
	}

	private static Item item(String name) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name));
	}

	// ------------------------------------------------------------------ registration

	/** Block name, first level, last level: the item list of every metadata block. */
	private static final String[][] METAS = {
			{"baleonite", "0", "5", "residue"}, {"digammitite", "0", "5", "digamma"},
			{"waste_terracotta", "0", "6", "waste"}, {"waste_sand_red", "0", "6", "waste"},
			{"waste_red_sandstone", "0", "6", "waste"}, {"waste_sand", "0", "6", "waste"},
			{"waste_sandstone", "0", "6", "waste"}, {"waste_gravel", "0", "6", "waste"},
			{"waste_dirt", "0", "6", "waste"}, {"waste_snow", "0", "6", "waste"},
			{"waste_snow_block", "0", "6", "waste"}, {"scorched_earth", "2", "6", "waste"}};

	private static boolean on(String group) {
		return switch(group) {
			case "residue" -> LeafiaFeatures.balefireResidue;
			case "digamma" -> LeafiaFeatures.digammitite;
			default -> LeafiaFeatures.legacyWaste;
		};
	}

	/**
	 * Fourteen blocks and 72 + 2 items: twelve metadata blocks with one item a level, and the ash and
	 * the ice with one each. Each item places its own level, and answers to
	 * {@code block.leafia.<name>.<level>}. Gated, in both directions, by the three switches.
	 */
	@GameTest(template = EMPTY)
	public static void everyResidueBlockHasAnItemPerLevel(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		for(String[] row : METAS) {
			String name = row[0];
			int first = Integer.parseInt(row[1]), last = Integer.parseInt(row[2]);
			boolean registered = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name));
			if(registered != on(row[3])) problems.add(name + (registered ? " registered while off" : " missing"));
			if(!registered) continue;

			MetaBlock block = meta(name);
			List<String> levels = new ArrayList<>();
			for(MetaLevel level : block.getVariants()) levels.add(level.getSerializedName());
			List<String> expected = new ArrayList<>();
			for(int n = first; n <= last; n++) expected.add(Integer.toString(n));
			// scorched earth shares the dirt's seven-state property and has items for the last five only
			List<String> stateLevels = first == 2 ? List.of("0", "1", "2", "3", "4", "5", "6") : expected;
			if(!levels.equals(stateLevels)) problems.add(name + " has levels " + levels + ", expected " + stateLevels);

			for(int n = first; n <= last; n++) {
				Item item = BuiltInRegistries.ITEM.get(LeafiaRef.rl(name + "_" + n));
				if(item == Items.AIR) { problems.add(name + "_" + n + " has no item"); continue; }
				var stack = new ItemStack(item);
				if(!(item instanceof com.hbm.items.block.ItemBlockBase.Variant<?> v) || v.getBlock() != block)
					problems.add(name + "_" + n + " is not that block's variant item");
				if(!stack.getDescriptionId().equals("block.leafia." + name + "." + n)) problems.add(name + "_" + n + " answers to " + stack.getDescriptionId());
				BlockState placed = block.withMeta(n);
				if(((MetaBlock) placed.getBlock()).meta(placed) != n) problems.add(name + " level " + n + " does not round-trip");
			}
			// and no item outside the range
			for(int n = 0; n <= 6; n++)
				if((n < first || n > last) && BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(name + "_" + n)))
					problems.add(name + "_" + n + " exists outside the block's levels");
		}
		for(String single : List.of("ash_balefire", "waste_ice")) {
			boolean registered = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(single));
			boolean expectOn = single.equals("ash_balefire") ? LeafiaFeatures.balefireResidue : LeafiaFeatures.legacyWaste;
			if(registered != expectOn || BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(single)) != expectOn)
				problems.add(single + " registration disagrees with its switch");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** BlockMeta shows every level in the creative tab; the others show one (scorched earth: its first). */
	@GameTest(template = EMPTY)
	public static void theCreativeTabShowsWhatUpstreamShowed(GameTestHelper helper) {

		Set<String> tab = new HashSet<>();
		for(var supplier : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN))
			tab.add(id(supplier.get().asItem()));

		List<String> problems = new ArrayList<>();
		Map<String, int[]> shown = new java.util.LinkedHashMap<>();
		shown.put("baleonite", new int[] {0, 5}); shown.put("digammitite", new int[] {0, 5});
		shown.put("waste_terracotta", new int[] {0, 6}); shown.put("waste_red_sandstone", new int[] {0, 6});
		shown.put("waste_sandstone", new int[] {0, 6}); shown.put("waste_snow_block", new int[] {0, 6});
		shown.put("waste_sand_red", new int[] {0, 0}); shown.put("waste_sand", new int[] {0, 0});
		shown.put("waste_gravel", new int[] {0, 0}); shown.put("waste_dirt", new int[] {0, 0});
		shown.put("waste_snow", new int[] {0, 0}); shown.put("scorched_earth", new int[] {2, 2});
		for(var e : shown.entrySet()) {
			String name = e.getKey();
			if(!BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name))) continue;
			int first = Integer.parseInt(java.util.Arrays.stream(METAS).filter(r -> r[0].equals(name)).findFirst().get()[1]);
			for(int n = first; n <= 6; n++) {
				boolean should = n >= e.getValue()[0] && n <= e.getValue()[1];
				if(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(name + "_" + n)) && tab.contains("leafia:" + name + "_" + n) != should)
					problems.add(name + "_" + n + (should ? " should be in the tab" : " should not be in the tab"));
			}
		}
		for(String single : List.of("ash_balefire", "waste_ice"))
			if(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(single)) && !tab.contains("leafia:" + single)) problems.add(single + " should be in the tab");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The ash gives light 11: 15 x (9/12) truncated, which is what 1.12.2's float light level was. */
	@GameTest(template = EMPTY)
	public static void theAshGlowsAtEleven(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }
		for(int layers = 1; layers <= 8; layers++)
			helper.assertTrue(ash(layers).getLightEmission() == 11, layers + " layers give light " + ash(layers).getLightEmission());
		helper.assertTrue((int) (15F * (9F / 12F)) == 11, "and that is upstream's arithmetic");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the tint table and the textures

	private static JsonObject manifest() throws Exception {
		try(InputStream in = LeafiaResidueTests.class.getResourceAsStream("/assets/leafia/residue.json")) {
			return JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
		}
	}

	private static final int[][] SELLAFITE = {
			{0x4C7939, 0x41463F}, {0x418223, 0x3E443B}, {0x338C0E, 0x3B5431},
			{0x1C9E00, 0x394733}, {0x02B200, 0x37492F}, {0x00D300, 0x324C26}};

	/**
	 * The generator's colour table against upstream's own arithmetic, run through the real
	 * {@link LeafiaColor}: baleonite is {@code (r + (g - r) x 0.75, g, b)} and digammitite
	 * {@code (g, b, b)}, rounded by {@code toInARGB}. Two entries are also pinned by hand.
	 */
	@GameTest(template = EMPTY)
	public static void theTintTableIsUpstreamsArithmetic(GameTestHelper helper) throws Exception {

		JsonObject manifest = manifest();
		for(String block : List.of("baleonite", "digammitite")) {
			JsonArray rows = manifest.getAsJsonArray(block);
			helper.assertTrue(rows.size() == 6, block + " has " + rows.size() + " levels");
			for(int level = 0; level < 6; level++) {
				for(int which = 0; which < 2; which++) {
					LeafiaColor col = new LeafiaColor(SELLAFITE[level][which]);
					LeafiaColor out;
					if(block.equals("baleonite")) {
						double delta = col.green - col.red;
						out = new LeafiaColor(col.red + delta * 0.75, col.green, col.blue);
					} else {
						out = new LeafiaColor(col.green, col.blue, col.blue);
					}
					int expected = out.toInARGB();
					int got = Integer.parseInt(rows.get(level).getAsJsonArray().get(which).getAsString(), 16);
					helper.assertTrue(expected == got, String.format("%s level %d colour %d: LeafiaColor says %06x, the table %06x", block, level, which, expected, got));
				}
			}
		}
		// by hand: 0x4C (76) + (0x79 (121) - 76) x 0.75 = 109.75, which rounds to 110 = 0x6E
		helper.assertTrue(manifest.getAsJsonArray("baleonite").get(0).getAsJsonArray().get(0).getAsString().equals("6e7939"), "baleonite 0 light by hand");
		// and the dark one: 0x41 (65) + (0x46 (70) - 65) x 0.75 = 68.75 -> 69 = 0x45
		helper.assertTrue(manifest.getAsJsonArray("baleonite").get(0).getAsJsonArray().get(1).getAsString().equals("45463f"), "baleonite 0 dark by hand");
		// digammitite swaps red for green and green for blue: 0x4C7939 -> 0x793939
		helper.assertTrue(manifest.getAsJsonArray("digammitite").get(0).getAsJsonArray().get(0).getAsString().equals("793939"), "digammitite 0 light by hand");
		helper.succeed();
	}

	private static BufferedImage hbmTexture(String path) throws Exception {
		var file = net.neoforged.fml.ModList.get().getModContainerById("hbm").orElseThrow().getModInfo().getOwningFile().getFile();
		try(InputStream in = Files.newInputStream(file.findResource(path.split("/")))) {
			return ImageIO.read(in);
		}
	}

	private static BufferedImage addonTexture(String name) throws Exception {
		try(InputStream in = LeafiaResidueTests.class.getResourceAsStream("/assets/leafia/textures/block/" + name + ".png")) {
			helper_assertNotNull(in, name);
			return ImageIO.read(in);
		}
	}

	private static void helper_assertNotNull(Object o, String what) {
		if(o == null) throw new AssertionError("missing texture " + what);
	}

	/**
	 * A generated texture against the formula, pixel by pixel, from the slaked sellafite it was made
	 * from: {@code t = (c - sourceLight) / (sourceDark - sourceLight)} and
	 * {@code out = (int)(targetLight + t x (targetDark - targetLight)) & 0xFF}, per channel,
	 * truncated and wrapped. Two textures: baleonite level 3 on variant 2, digammitite level 4 on 3.
	 */
	@GameTest(template = EMPTY)
	public static void aRecolouredTextureIsTheRemapOfTheSlakedOne(GameTestHelper helper) throws Exception {

		JsonObject manifest = manifest();
		record Case(String block, int level, int variant, String source) {}
		for(Case c : List.of(new Case("baleonite", 3, 2, "sellafield_slaked_2"), new Case("digammitite", 4, 3, "sellafield_slaked_3"))) {
			BufferedImage src = hbmTexture("assets/hbm/textures/block/" + c.source() + ".png");
			BufferedImage out = addonTexture(c.block() + "_" + c.level() + "_" + c.variant());
			helper.assertTrue(src.getWidth() == out.getWidth() && src.getHeight() == out.getHeight(), c.block() + ": sizes differ");
			JsonArray tint = manifest.getAsJsonArray(c.block()).get(c.level()).getAsJsonArray();
			int light = Integer.parseInt(tint.get(0).getAsString(), 16), dark = Integer.parseInt(tint.get(1).getAsString(), 16);
			int sourceLight = 0x858384, sourceDark = 0x434343;
			int wrong = 0;
			for(int y = 0; y < src.getHeight(); y++) {
				for(int x = 0; x < src.getWidth(); x++) {
					int p = src.getRGB(x, y), q = out.getRGB(x, y);
					for(int shift = 16; shift >= 0; shift -= 8) {
						int cc = p >> shift & 0xFF;
						int bl = sourceLight >> shift & 0xFF, bd = sourceDark >> shift & 0xFF;
						int tl = light >> shift & 0xFF, td = dark >> shift & 0xFF;
						double t = (cc - bl) / (double) (bd - bl);
						int expected = (int) (tl + t * (td - tl)) & 0xFF;
						if((q >> shift & 0xFF) != expected) wrong++;
					}
					if((p >>> 24) != (q >>> 24)) wrong++;
				}
			}
			helper.assertTrue(wrong == 0, c.block() + " level " + c.level() + " variant " + c.variant() + ": " + wrong + " channels differ from the remap");
		}
		helper.succeed();
	}

	/**
	 * Every texture a residue model names exists, and every model a residue blockstate names exists.
	 * The generator wrote all of them from one table; this reads the files back the way the game does.
	 */
	@GameTest(template = EMPTY)
	public static void everyModelAndTextureTheResidueNamesExists(GameTestHelper helper) throws Exception {

		List<String> problems = new ArrayList<>();
		List<String> blocks = new ArrayList<>(List.of("ash_balefire", "waste_ice"));
		for(String[] row : METAS) blocks.add(row[0]);

		int models = 0, textures = 0;
		for(String block : blocks) {
			String statePath = "/assets/leafia/blockstates/" + block + ".json";
			JsonObject state;
			try(InputStream in = LeafiaResidueTests.class.getResourceAsStream(statePath)) {
				if(in == null) { problems.add(statePath + " is missing"); continue; }
				state = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
			}
			Set<String> modelNames = new HashSet<>();
			for(var variant : state.getAsJsonObject("variants").entrySet()) {
				JsonElement e = variant.getValue();
				if(e.isJsonArray()) for(JsonElement each : e.getAsJsonArray()) modelNames.add(each.getAsJsonObject().get("model").getAsString());
				else modelNames.add(e.getAsJsonObject().get("model").getAsString());
			}
			for(String model : modelNames) {
				String path = "/assets/leafia/models/block/" + model.substring("leafia:block/".length()) + ".json";
				models++;
				try(InputStream in = LeafiaResidueTests.class.getResourceAsStream(path)) {
					if(in == null) { problems.add(block + " names the missing model " + model); continue; }
					JsonObject json = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
					for(var tex : json.getAsJsonObject("textures").entrySet()) {
						String ref = tex.getValue().getAsString();
						if(!ref.startsWith("leafia:")) continue;
						textures++;
						if(LeafiaResidueTests.class.getResource("/assets/leafia/textures/" + ref.substring("leafia:".length()) + ".png") == null)
							problems.add(model + " names the missing texture " + ref);
					}
				}
			}
		}
		helper.assertTrue(models > 100 && textures > 100, "only " + models + " models and " + textures + " textures were looked at");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	/** Every level of every block has its upstream name in the English lang file. */
	@GameTest(template = EMPTY)
	public static void everyLevelHasAName(GameTestHelper helper) throws Exception {

		JsonObject lang;
		try(InputStream in = LeafiaResidueTests.class.getResourceAsStream("/assets/leafia/lang/en_us.json")) {
			lang = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
		}
		helper.assertTrue(lang.get("block.leafia.baleonite.0").getAsString().equals("Baleonite")
				&& lang.get("block.leafia.baleonite.5").getAsString().equals("Baleonite-Corium")
				&& lang.get("block.leafia.digammitite.3").getAsString().equals("Blazing Digammitite")
				&& lang.get("block.leafia.ash_balefire").getAsString().equals("Thermonuclear Ash")
				&& lang.get("block.leafia.waste_dirt.4").getAsString().equals("Contaminated Dirt")
				&& lang.get("block.leafia.scorched_earth.2").getAsString().equals("Scorched Grass")
				&& lang.get("hbmfluid.balecorium").getAsString().equals("Balefire Corium"), "upstream's names");
		List<String> missing = new ArrayList<>();
		for(String[] row : METAS)
			for(int n = Integer.parseInt(row[1]); n <= Integer.parseInt(row[2]); n++)
				if(!lang.has("block.leafia." + row[0] + "." + n)) missing.add(row[0] + "." + n);
		for(String single : List.of("ash_balefire", "waste_ice")) if(!lang.has("block.leafia." + single)) missing.add(single);
		helper.assertTrue(missing.isEmpty(), "no name for " + missing);
		helper.succeed();
	}

	// ------------------------------------------------------------------ baleonite

	/** Walking on it: radiation at amplifier level+2 (level x 4 + 2 at five) for 30 s, and fire from level 3. */
	@GameTest(template = EMPTY16)
	public static void baleoniteBurnsAndIrradiatesWhoWalksOnIt(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(2, 1, 2);
		int[] amplifiers = {2, 3, 4, 5, 6, 22};
		for(int level = 0; level <= 5; level++) {
			var pig = helper.spawn(EntityType.PIG, new BlockPos(6, 1, 6));
			BlockState state = LeafiaBlocks.baleonite.get().withMeta(level);
			helper.setBlock(at, state);
			state.getBlock().stepOn(helper.getLevel(), helper.absolutePos(at), state, pig);

			MobEffectInstance effect = pig.getEffect(NtmEffects.RADIATION);
			helper.assertTrue(effect != null && effect.getAmplifier() == amplifiers[level] && effect.getDuration() == 30 * 20,
					"level " + level + ": " + (effect == null ? "no effect" : "amplifier " + effect.getAmplifier() + " for " + effect.getDuration()));
			helper.assertTrue(BaleoniteBlock.walkAmplifier(level) == amplifiers[level], "level " + level + " amplifier formula");
			int fire = pig.getRemainingFireTicks();
			helper.assertTrue(level >= 3 ? fire == level * 20 : fire <= 0, "level " + level + " set it alight for " + fire + " ticks");
			pig.discard();
		}
		helper.succeed();
	}

	/** A random tick adds 5 x (level + 1) RAD, and cools one level one time in thirty; level 0 never changes. */
	@GameTest(template = EMPTY16)
	public static void baleoniteRadiatesAndCools(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		withHandler(helper, handler -> {
			BlockPos at = new BlockPos(2, 1, 2);
			ServerLevel level = helper.getLevel();
			for(int meta = 0; meta <= 5; meta++) {
				BlockState state = LeafiaBlocks.baleonite.get().withMeta(meta);
				helper.setBlock(at, state);

				float before = rad(handler, helper, at);
				state.randomTick(level, helper.absolutePos(at), new Script(1)); // nextInt(30) != 0
				helper.assertTrue(rad(handler, helper, at) - before == 5F * (meta + 1), "level " + meta + " added " + (rad(handler, helper, at) - before));
				helper.assertTrue(helper.getBlockState(at) == state, "level " + meta + " cooled without being asked to");

				Script cool = new Script(0);
				state.randomTick(level, helper.absolutePos(at), cool);
				BlockState after = helper.getBlockState(at);
				if(meta == 0) {
					helper.assertTrue(after == state && cool.bounds.isEmpty(), "level 0 stays, and does not even roll: " + cool.bounds);
				} else {
					helper.assertTrue(after == LeafiaBlocks.baleonite.get().withMeta(meta - 1) && cool.bounds.equals(List.of(30)),
							"level " + meta + " became " + after + " after rolling " + cool.bounds);
				}
			}
		});
		helper.succeed();
	}

	/**
	 * Upstream's {@code (rand.nextInt(3) + 1) ^ 2} is XOR: 3, 0 and 1. That makes level 2 two times in
	 * three, level 1 once, and level 0 never. Not a square (1, 4, 9), which would give 2, 1 and 0.
	 */
	@GameTest(template = EMPTY)
	public static void theBaleoniteRollIsXorNotASquare(GameTestHelper helper) {

		int[] rolls = new int[3];
		int[] levels = new int[3];
		for(int draw = 0; draw < 3; draw++) {
			rolls[draw] = BaleoniteBlock.roll(new Script(draw));
			levels[draw] = BaleoniteBlock.levelForRoll(rolls[draw]);
		}
		helper.assertTrue(rolls[0] == 3 && rolls[1] == 0 && rolls[2] == 1, "the rolls were " + java.util.Arrays.toString(rolls));
		helper.assertTrue(levels[0] == 1 && levels[1] == 2 && levels[2] == 2, "the levels were " + java.util.Arrays.toString(levels));
		helper.assertTrue(BaleoniteBlock.levelForRoll(4) == 1 && BaleoniteBlock.levelForRoll(5) == 0 && BaleoniteBlock.levelForRoll(9) == 0 && BaleoniteBlock.levelForRoll(10) == -1, "the thresholds are upstream's 1, 4 and 9");
		helper.succeed();
	}

	// ------------------------------------------------------------------ digammitite

	/**
	 * Digamma to everything within 32: 5 x (level + 1) over the cube of the distance, level 0 gives
	 * none, and the level read is the one before cooling.
	 */
	@GameTest(template = EMPTY16)
	public static void digammititeRadiatesDigammaAndCools(GameTestHelper helper) {
		if(!LeafiaFeatures.digammitite) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(2, 1, 2);
		BlockPos abs = helper.absolutePos(at);

		for(int meta = 0; meta <= 5; meta++) {
			var pig = helper.spawn(EntityType.PIG, new BlockPos(9, 1, 2));
			pig.setNoAi(true);
			BlockState state = LeafiaBlocks.digammitite.get().withMeta(meta);
			helper.setBlock(at, state);

			float before = HbmLivingProps.getDigamma(pig);
			state.randomTick(level, abs, new Script(1));
			float got = HbmLivingProps.getDigamma(pig) - before;
			if(meta == 0) {
				helper.assertTrue(got == 0F, "level 0 gives off nothing, gave " + got);
			} else {
				double dx = pig.getX() - (abs.getX() + 0.5), dy = pig.getY() + pig.getEyeHeight() - (abs.getY() + 0.5), dz = pig.getZ() - (abs.getZ() + 0.5);
				double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
				float expected = (float) (5F * (meta + 1) / (len * len * len));
				helper.assertTrue(Math.abs(got - expected) < 1e-5, "level " + meta + " gave " + got + ", expected " + expected);
			}
			helper.assertTrue(helper.getBlockState(at) == state, "level " + meta + " cooled without being asked to");

			float beforeCool = HbmLivingProps.getDigamma(pig);
			Script cool = new Script(0);
			state.randomTick(level, abs, cool);
			if(meta > 0) {
				helper.assertTrue(helper.getBlockState(at) == LeafiaBlocks.digammitite.get().withMeta(meta - 1) && cool.bounds.equals(List.of(50)),
						"level " + meta + " became " + helper.getBlockState(at) + " after rolling " + cool.bounds);
				// it radiated at the level it had, not the one it became
				helper.assertTrue(HbmLivingProps.getDigamma(pig) > beforeCool, "it still radiates on the tick it cools");
			} else {
				helper.assertTrue(cool.bounds.isEmpty(), "level 0 does not roll");
			}
			pig.discard();
		}
		helper.succeed();
	}

	/** Upstream's hazard table: 0.2, 0.4, 0.6, 0.8 and 1 digamma on levels 1 to 5, none on 0. */
	@GameTest(template = EMPTY)
	public static void digammititeItemsCarryDigamma(GameTestHelper helper) {
		if(!LeafiaFeatures.digammitite) { helper.succeed(); return; }

		float[] expected = {0F, 0.2F, 0.4F, 0.6F, 0.8F, 1F};
		for(int level = 0; level <= 5; level++) {
			var stack = new ItemStack(item("digammitite_" + level));
			List<HazardEntry> entries = HazardSystem.getHazardsFromStack(stack);
			float got = 0F;
			for(HazardEntry entry : entries) if(entry.getType() == LeafiaHazards.DIGAMMA) got = entry.getBaseLevel();
			helper.assertTrue(got == expected[level], "digammitite_" + level + " carries " + got + " digamma, expected " + expected[level]);
		}
		helper.succeed();
	}

	/** The shredder takes digammitite to tiny powder: 1, 4, 7, 10, 13 and 16, one row a level. */
	@GameTest(template = EMPTY)
	public static void theShredderGivesTinyDigammitePowder(GameTestHelper helper) {
		if(!LeafiaFeatures.digammitite || !LeafiaFeatures.resourceItems) { helper.succeed(); return; }

		int[] expected = {1, 4, 7, 10, 13, 16};
		for(int level = 0; level <= 5; level++) {
			ItemStack out = ShredderRecipes.getShredderResult(new ItemStack(item("digammitite_" + level)));
			helper.assertTrue(!out.isEmpty() && id(out.getItem()).equals("leafia:powder_digammitite_tiny") && out.getCount() == expected[level],
					"level " + level + " shreds to " + out);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the ash

	private static void tickAsh(GameTestHelper helper, BlockState state, BlockPos rel, RandomSource random) {
		state.tick(helper.getLevel(), helper.absolutePos(rel), random);
	}

	/** One time in 4000 it burns: two layers or fewer it becomes balefire, with more it loses two. */
	@GameTest(template = EMPTY16)
	public static void theAshBurnsOneTimeIn4000(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos at = new BlockPos(3, 1, 3);

		helper.setBlock(at, ash(2));
		Script script = new Script(0);
		tickAsh(helper, ash(2), at, script);
		helper.assertTrue(helper.getBlockState(at).is(ModBlocks.balefire.get()), "two layers became " + helper.getBlockState(at));
		helper.assertTrue(script.bounds.equals(List.of(4000)), "and the tick ended there: " + script.bounds);

		helper.setBlock(at, ash(5));
		script = new Script(0);
		tickAsh(helper, ash(5), at, script);
		helper.assertTrue(helper.getBlockState(at) == ash(3), "five layers became " + helper.getBlockState(at));
		helper.assertTrue(script.bounds.equals(List.of(4000, 5)), "and it ticked on, then scheduled itself: " + script.bounds);
		helper.succeed();
	}

	/** The target: a random block within two on each axis, three draws of five, in upstream's order. */
	@GameTest(template = EMPTY16)
	public static void theAshTurnsRockNextToItIntoBaleoniteByTheXorRoll(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos at = new BlockPos(3, 1, 3);
		BlockPos target = at.east();
		int[] draw = {0, 1, 2};          // nextInt(3): the rolls 3, 0 and 1
		int[] baleonite = {1, 2, 2};     // the levels they make

		for(int i = 0; i < 3; i++) {
			helper.setBlock(at, ash(8));
			helper.setBlock(target, Blocks.STONE);
			// 1 (not a burn), 0 (< 8), then x+1, y+0, z+0 as 3, 2, 2, then the roll
			Script script = new Script(1, 0, 3, 2, 2, draw[i]);
			tickAsh(helper, ash(8), at, script);
			helper.assertTrue(helper.getBlockState(target) == LeafiaBlocks.baleonite.get().withMeta(baleonite[i]),
					"roll " + draw[i] + " made " + helper.getBlockState(target) + ", expected baleonite level " + baleonite[i]);
			helper.assertTrue(script.bounds.equals(List.of(4000, 8, 5, 5, 5, 3, 5)), "the draws were " + script.bounds);
		}
		helper.succeed();
	}

	/** Corium, corium cobblestone and the corium ores are never eaten; baleonite is, and so is a stone variant. */
	@GameTest(template = EMPTY16)
	public static void theAshLeavesCoriumAlone(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos at = new BlockPos(3, 1, 3);
		BlockPos target = at.east();

		List<BlockState> alone = new ArrayList<>(List.of(ModBlocks.block_corium.get().defaultBlockState(), ModBlocks.block_corium_cobble.get().defaultBlockState()));
		if(LeafiaFeatures.coriumOres) alone.add(LeafiaBlocks.ore_corium_zetalite.get().defaultBlockState());
		for(BlockState state : alone) {
			helper.setBlock(at, ash(8));
			helper.setBlock(target, state);
			tickAsh(helper, ash(8), at, new Script(1, 0, 3, 2, 2, 0));
			helper.assertTrue(helper.getBlockState(target) == state, state + " was eaten");
		}
		// but baleonite, stone, cobblestone, infested stone and a rock the 1.7.10 table calls rock are
		for(BlockState state : List.of(LeafiaBlocks.baleonite.get().withMeta(4), Blocks.COBBLESTONE.defaultBlockState(),
				Blocks.INFESTED_STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState())) {
			helper.setBlock(at, ash(8));
			helper.setBlock(target, state);
			tickAsh(helper, ash(8), at, new Script(1, 0, 3, 2, 2, 2));
			helper.assertTrue(helper.getBlockState(target) == LeafiaBlocks.baleonite.get().withMeta(2), state + " became " + helper.getBlockState(target));
		}
		helper.succeed();
	}

	/** Air or ash with a flammable neighbour catches balefire; without one, nothing. */
	@GameTest(template = EMPTY16)
	public static void theAshIgnitesNextToAnythingFlammable(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos at = new BlockPos(3, 1, 3);
		BlockPos target = at.east();
		helper.setBlock(at, ash(8));

		tickAsh(helper, ash(8), at, new Script(1, 0, 3, 2, 2));
		helper.assertTrue(helper.getBlockState(target).isAir(), "air with nothing to burn beside it caught fire");

		helper.setBlock(target.east(), Blocks.OAK_PLANKS);
		tickAsh(helper, ash(8), at, new Script(1, 0, 3, 2, 2));
		helper.assertTrue(helper.getBlockState(target).is(ModBlocks.balefire.get()), "air beside planks became " + helper.getBlockState(target));

		// a layer of ash is a target too
		helper.setBlock(target, ash(3));
		tickAsh(helper, ash(8), at, new Script(1, 0, 3, 2, 2));
		helper.assertTrue(helper.getBlockState(target).is(ModBlocks.balefire.get()), "ash beside planks became " + helper.getBlockState(target));

		// and fewer layers means a smaller chance of picking a target at all: 3 layers, a roll of 3 of 8, is a miss
		helper.setBlock(target, Blocks.AIR);
		Script miss = new Script(1, 3);
		tickAsh(helper, ash(3), at, miss);
		helper.assertTrue(helper.getBlockState(target).isAir() && miss.bounds.equals(List.of(4000, 8, 5)), "three layers rolled 3 of 8 and still chose a target: " + miss.bounds);
		helper.succeed();
	}

	/** 0.8 RAD a tick, never past 80. */
	@GameTest(template = EMPTY16)
	public static void theAshAddsEightTenthsRadUpToEighty(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		withHandler(helper, handler -> {
			BlockPos at = new BlockPos(3, 1, 3);
			helper.setBlock(at, ash(1));

			tickAsh(helper, ash(1), at, new Script(1, 7));
			helper.assertTrue(Math.abs(rad(handler, helper, at) - 0.8F) < 1e-5, "one tick added " + rad(handler, helper, at));

			BlockPos abs = helper.absolutePos(at);
			handler.setRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ(), 79.9F);
			tickAsh(helper, ash(1), at, new Script(1, 7));
			helper.assertTrue(rad(handler, helper, at) == 80F, "from 79.9 it reached " + rad(handler, helper, at));

			tickAsh(helper, ash(1), at, new Script(1, 7));
			helper.assertTrue(rad(handler, helper, at) == 80F, "and stayed at " + rad(handler, helper, at));
		});
		helper.succeed();
	}

	/** It ticks again in 1 to 5 ticks, and falls when nothing holds it up. */
	@GameTest(template = EMPTY16)
	public static void theAshSchedulesItselfAndFalls(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos held = new BlockPos(2, 1, 2), loose = new BlockPos(5, 5, 5);
		helper.setBlock(held, ash(4));
		tickAsh(helper, ash(4), held, new Script(1, 7));
		helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(held), LeafiaBlocks.ash_balefire.get()), "a tick is scheduled");
		helper.assertTrue(helper.getBlockState(held) == ash(4), "a supported layer stays");

		helper.setBlock(loose, ash(4));
		tickAsh(helper, ash(4), loose, new Script(1, 7));
		List<FallingBlockEntity> falling = helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(helper.absolutePos(loose)).inflate(1));
		helper.assertTrue(falling.size() == 1 && falling.get(0).getBlockState() == ash(4), "an unsupported layer fell: " + falling);
		helper.assertTrue(helper.getBlockState(loose).isAir(), "and left its place empty");
		falling.get(0).discard();
		helper.succeed();
	}

	/**
	 * Falling onto a layer of itself adds the two together.
	 *
	 * <p>The layer on the ground is live while the other falls: it ticks every 1 to 5 ticks on the level's own random,
	 * and one tick in {@link AshBalefire#BURN_ONE_IN} it burns and loses two layers. So the sum is asserted against
	 * what the ground held on the last tick the falling layer was still in the air, not against the 3 that was written
	 * (rule 10's live-block-entity shape, D-3042). Only a burn on the landing tick itself, before the entity moves, is
	 * still unseen - about one run in ten thousand.
	 */
	@GameTest(template = EMPTY16)
	public static void aLayerLandingOnAnotherAddsToIt(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos ground = new BlockPos(3, 1, 3), high = new BlockPos(3, 5, 3);
		helper.setBlock(ground, ash(3));
		helper.setBlock(high, ash(4));
		FallingBlockEntity falling = FallingBlockEntity.fall(helper.getLevel(), helper.absolutePos(high), ash(4));
		int[] under = {3};

		helper.succeedWhen(() -> {
			BlockState now = helper.getBlockState(ground);
			if(!falling.isRemoved()) {
				if(now.is(LeafiaBlocks.ash_balefire.get())) under[0] = now.getValue(AshBalefire.LAYERS);
				throw new net.minecraft.gametest.framework.GameTestAssertException("still falling; the ground holds " + now);
			}
			int sum = Math.min(8, under[0] + 4);
			helper.assertTrue(now.is(LeafiaBlocks.ash_balefire.get()) && now.getValue(AshBalefire.LAYERS) == sum,
					"the ground holds " + now + ", expected " + under[0] + " + 4");
			helper.assertTrue(under[0] + 4 > 8 || helper.getBlockState(ground.above()).isAir(), "nothing on top: " + helper.getBlockState(ground.above()));
		});
	}

	/** Eleven layers on eight: the lower fills, the rest sits on top. */
	@GameTest(template = EMPTY16)
	public static void whatDoesNotFitSitsOnTop(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos ground = new BlockPos(3, 1, 3), high = new BlockPos(3, 5, 3);
		helper.setBlock(ground, ash(5));
		helper.setBlock(high, ash(6));
		FallingBlockEntity.fall(helper.getLevel(), helper.absolutePos(high), ash(6));

		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(ground) == ash(8), "the ground holds " + helper.getBlockState(ground));
			helper.assertTrue(helper.getBlockState(ground.above()) == ash(3), "the top holds " + helper.getBlockState(ground.above()));
		});
	}

	/** With no room above the surplus becomes powder: floor((total - 8) / 2) - nextInt(2), here 1 or 0. */
	@GameTest(template = EMPTY16)
	public static void whatCannotBeStackedBecomesPowder(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		ironFloor(helper, 8);
		BlockPos ground = new BlockPos(3, 1, 3);
		AABB around = new AABB(helper.absolutePos(ground)).inflate(3);
		int most = -1, least = 99;
		for(int attempt = 0; attempt < 40; attempt++) {
			helper.setBlock(ground, ash(5));
			helper.setBlock(ground.above(), Blocks.IRON_BLOCK);
			AshBalefire block = LeafiaBlocks.ash_balefire.get();
			block.onLand(helper.getLevel(), helper.absolutePos(ground), ash(6), ash(5), null);
			helper.assertTrue(helper.getBlockState(ground) == ash(8), "the lower filled to " + helper.getBlockState(ground));
			int powder = 0;
			for(ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
				helper.assertTrue(e.getItem().getItem() == ModItems.powder_balefire.get(), "dropped " + e.getItem());
				powder += e.getItem().getCount();
				e.discard();
			}
			most = Math.max(most, powder);
			least = Math.min(least, powder);
		}
		helper.assertTrue(most == 1 && least == 0, "3 surplus layers gave " + least + " to " + most + " powder, expected 0 to 1");
		helper.assertTrue(AshBalefire.drops(1, new Script(0)) == 0 && AshBalefire.drops(8, new Script(1)) == 3 && AshBalefire.drops(-3, new Script(0)) == -2, "the formula, including its negatives");
		helper.succeed();
	}

	/** floor(layers / 2) - nextInt(2) powder: none for one layer, 3 or 4 for eight. */
	@GameTest(template = EMPTY16)
	public static void theAshDropsPowder(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		for(int layers = 1; layers <= 8; layers++) {
			int min = 99, max = -1;
			for(int draw = 0; draw < 60; draw++) {
				int count = 0;
				for(ItemStack stack : drops(helper, ash(layers), new BlockPos(2, 1, 2))) {
					if(stack.isEmpty()) continue; // a count of zero rolled
					helper.assertTrue(stack.getItem() == ModItems.powder_balefire.get(), layers + " layers dropped " + stack);
					count += stack.getCount();
				}
				min = Math.min(min, count);
				max = Math.max(max, count);
			}
			int top = layers / 2;
			int expectedMin = Math.max(0, top - 1);
			helper.assertTrue(min == expectedMin && max == top, layers + " layers dropped " + min + " to " + max + ", expected " + expectedMin + " to " + top);
		}
		helper.succeed();
	}

	/** Setting alight for six seconds and radiation 5 for 25 ticks, walking on it or standing in it. */
	@GameTest(template = EMPTY16)
	public static void theAshBurnsWhoTouchesIt(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(2, 1, 2);
		helper.setBlock(at, ash(4));
		for(int how = 0; how < 2; how++) {
			var pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 8));
			if(how == 0) ash(4).getBlock().stepOn(helper.getLevel(), helper.absolutePos(at), ash(4), pig);
			else ash(4).entityInside(helper.getLevel(), helper.absolutePos(at), pig);
			helper.assertTrue(pig.getRemainingFireTicks() == 6 * 20, (how == 0 ? "walking" : "standing") + " set it alight for " + pig.getRemainingFireTicks());
			MobEffectInstance effect = pig.getEffect(NtmEffects.RADIATION);
			helper.assertTrue(effect != null && effect.getAmplifier() == 4 && effect.getDuration() == 25, "radiation was " + effect);
			pig.discard();
		}
		helper.succeed();
	}

	/** The selection box is layers x 2 pixels, the collision box one layer less, and it can be walked through below five. */
	@GameTest(template = EMPTY16)
	public static void theAshIsShapedLikeSnow(GameTestHelper helper) {
		if(!residue()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(2, 1, 2);
		for(int layers = 1; layers <= 8; layers++) {
			helper.setBlock(at, ash(layers));
			BlockState state = helper.getBlockState(at);
			double outline = state.getShape(helper.getLevel(), helper.absolutePos(at)).max(Direction.Axis.Y);
			double collision = state.getCollisionShape(helper.getLevel(), helper.absolutePos(at)).isEmpty() ? 0 : state.getCollisionShape(helper.getLevel(), helper.absolutePos(at)).max(Direction.Axis.Y);
			helper.assertTrue(Math.abs(outline - layers / 8.0) < 1e-9, layers + " layers: outline " + outline);
			helper.assertTrue(Math.abs(collision - (layers - 1) / 8.0) < 1e-9, layers + " layers: collision " + collision);
			helper.assertTrue(state.isPathfindable(PathComputationType.LAND) == layers < 5, layers + " layers: walkable " + state.isPathfindable(PathComputationType.LAND));
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the legacy blocks

	/** The sand falls; the dirt does not; the sand's drop is the block at level 0, whatever level was broken. */
	@GameTest(template = EMPTY16)
	public static void contaminatedSandFallsAndDropsLevelZero(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockPos loose = new BlockPos(4, 5, 4);
		for(String name : List.of("waste_sand", "waste_sand_red", "waste_gravel")) {
			helper.setBlock(loose, meta(name).withMeta(4));
			meta(name).withMeta(4).tick(helper.getLevel(), helper.absolutePos(loose), helper.getLevel().random);
			List<FallingBlockEntity> falling = helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(helper.absolutePos(loose)).inflate(1));
			helper.assertTrue(falling.size() == 1, name + " did not fall");
			falling.get(0).discard();

			List<ItemStack> dropped = drops(helper, meta(name).withMeta(4), loose);
			helper.assertTrue(dropped.size() == 1 && id(dropped.get(0).getItem()).equals("leafia:" + name + "_0"), name + " level 4 dropped " + dropped);
		}
		// earth does not fall
		helper.setBlock(loose, meta("waste_dirt").withMeta(3));
		meta("waste_dirt").withMeta(3).tick(helper.getLevel(), helper.absolutePos(loose), helper.getLevel().random);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(helper.absolutePos(loose)).inflate(1)).isEmpty(), "dirt fell");
		var dirt = drops(helper, meta("waste_dirt").withMeta(3), loose);
		helper.assertTrue(dirt.size() == 1 && id(dirt.get(0).getItem()).equals("leafia:waste_dirt_0"), "dirt level 3 dropped " + dirt);
		// scorched earth has no level 0: its first level stands in
		var scorched = drops(helper, meta("scorched_earth").withMeta(5), loose);
		helper.assertTrue(scorched.size() == 1 && id(scorched.get(0).getItem()).equals("leafia:scorched_earth_2"), "scorched earth dropped " + scorched);
		helper.assertTrue(((net.minecraft.world.level.block.Block) meta("scorched_earth")).defaultBlockState().getValue(com.leafia.dev.blocks.legacy.LegacyWasteEarth.META) == MetaLevel.L2, "scorched earth defaults to level 2");
		helper.succeed();
	}

	/** BlockMeta drops the level that was broken; the snow drops nothing. */
	@GameTest(template = EMPTY16)
	public static void metaBlocksDropTheirOwnLevel(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockPos at = new BlockPos(4, 1, 4);
		for(String name : List.of("waste_terracotta", "waste_sandstone", "waste_red_sandstone", "waste_snow_block")) {
			for(int n = 0; n <= 6; n++) {
				var dropped = drops(helper, meta(name).withMeta(n), at);
				helper.assertTrue(dropped.size() == 1 && id(dropped.get(0).getItem()).equals("leafia:" + name + "_" + n), name + " level " + n + " dropped " + dropped);
			}
		}
		if(LeafiaFeatures.balefireResidue)
			for(int n = 0; n <= 5; n++) {
				var dropped = drops(helper, meta("baleonite").withMeta(n), at);
				helper.assertTrue(dropped.size() == 1 && id(dropped.get(0).getItem()).equals("leafia:baleonite_" + n), "baleonite level " + n + " dropped " + dropped);
			}
		for(int n = 0; n <= 6; n++)
			helper.assertTrue(drops(helper, meta("waste_snow").withMeta(n), at).isEmpty(), "contaminated snow dropped something");
		helper.succeed();
	}

	/** Contaminated ice gives nothing without silk touch, and itself with it. */
	@GameTest(template = EMPTY16)
	public static void contaminatedIceNeedsSilkTouch(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockPos at = new BlockPos(4, 1, 4);
		BlockState ice = LeafiaBlocks.waste_ice.get().defaultBlockState();
		helper.assertTrue(drops(helper, ice, at).isEmpty(), "dropped without silk touch");

		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		pick.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		var dropped = drops(helper, ice, at, pick);
		helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem() == LeafiaBlocks.waste_ice.get().asItem(), "silk touch dropped " + dropped);
		helper.assertTrue(ice.getBlock().getFriction() == 0.98F, "slippery");
		helper.succeed();
	}

	/**
	 * Thin contaminated snow: placed only on leaves or an opaque block that stops movement, never on
	 * ice, and gone when the support is. Upstream's clause for standing on a full stack of itself
	 * needs {@code (meta & 7) == 7}, which no level reaches.
	 */
	@GameTest(timeoutTicks = TickWindow.TIMEOUT, template = EMPTY16)
	public static void contaminatedSnowNeedsAFloor(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockState snow = LeafiaBlocks.waste_snow.get().defaultBlockState();
		BlockPos at = new BlockPos(3, 1, 3);
		BlockPos abs = helper.absolutePos(at);
		var level = helper.getLevel();
		Map<Block, Boolean> floors = new java.util.LinkedHashMap<>();
		floors.put(Blocks.STONE, true);
		floors.put(Blocks.DIRT, true);
		floors.put(Blocks.OAK_LEAVES, true);
		floors.put(Blocks.ICE, false);
		floors.put(Blocks.PACKED_ICE, false);
		floors.put(Blocks.GLASS, false);
		floors.put(Blocks.AIR, false);
		for(var e : floors.entrySet()) {
			helper.setBlock(at.below(), e.getKey());
			helper.assertTrue(snow.canSurvive(level, abs) == e.getValue(), "on " + e.getKey() + " it " + (e.getValue() ? "should" : "should not") + " stand");
		}
		// and it is removed when the floor is
		helper.setBlock(at.below(), Blocks.STONE);
		helper.setBlock(at, snow);
		helper.setBlock(at.below(), Blocks.AIR);
		TickWindow.runAfterDelay(helper, 2, () -> {
			helper.assertTrue(helper.getBlockState(at).isAir(), "the snow stayed over air: " + helper.getBlockState(at));
			helper.succeed();
		});
	}

	/** Radiation 15 for two minutes underfoot; one RAD a tick, 10 to 39 ticks apart, never past 100; an eighth of a block; replaceable. */
	@GameTest(template = EMPTY16)
	public static void contaminatedSnowIrradiates(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockPos at = new BlockPos(3, 1, 3);
		ironFloor(helper, 8);
		BlockState snow = LeafiaBlocks.waste_snow.get().withMeta(3);
		helper.setBlock(at, snow);
		var abs = helper.absolutePos(at);

		var pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 8));
		snow.getBlock().stepOn(helper.getLevel(), abs, snow, pig);
		MobEffectInstance effect = pig.getEffect(NtmEffects.RADIATION);
		helper.assertTrue(effect != null && effect.getAmplifier() == 14 && effect.getDuration() == 2 * 60 * 20, "underfoot: " + effect);
		pig.discard();

		helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(abs, snow.getBlock()), "placing it scheduled a tick");
		helper.assertTrue(snow.getShape(helper.getLevel(), abs).max(Direction.Axis.Y) == 0.125 && snow.getCollisionShape(helper.getLevel(), abs).max(Direction.Axis.Y) == 0.125, "an eighth of a block, in both shapes");
		helper.assertTrue(snow.canBeReplaced(), "replaceable");

		withHandler(helper, handler -> {
			snow.tick(helper.getLevel(), abs, new Script(7));
			helper.assertTrue(rad(handler, helper, at) == 1F, "one tick added " + rad(handler, helper, at));
			handler.setRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ(), 99.5F);
			snow.tick(helper.getLevel(), abs, new Script(7));
			helper.assertTrue(rad(handler, helper, at) == 100F, "capped at " + rad(handler, helper, at));
		});
		helper.succeed();
	}

	/** Only the radiation-immune spawn on the contaminated earth, sand, gravel, snow and scorched grass. */
	@GameTest(template = EMPTY)
	public static void onlyTheRadiationImmuneSpawnOnContaminatedGround(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		for(String name : List.of("waste_dirt", "waste_sand", "waste_sand_red", "waste_gravel", "waste_snow", "scorched_earth")) {
			BlockState state = meta(name).withMeta(name.equals("scorched_earth") ? 2 : 0);
			for(EntityType<?> immune : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.MOOSHROOM, EntityType.OCELOT, EntityType.HUSK))
				helper.assertTrue(state.isValidSpawn(level, pos, immune), name + " refused " + immune);
			for(EntityType<?> mortal : List.of(EntityType.COW, EntityType.CREEPER, EntityType.SPIDER, EntityType.PIG, EntityType.STRAY, EntityType.CAT))
				helper.assertTrue(!state.isValidSpawn(level, pos, mortal), name + " allowed " + mortal);
		}
		helper.assertTrue(RadImmuneSpawn.allows(EntityType.GIANT) && !RadImmuneSpawn.allows(EntityType.WITHER_SKELETON), "the sets");
		helper.succeed();
	}

	/**
	 * The hazard hooks apply what is registered for the block's level-0 item, to a living thing walking
	 * on it or standing in it - and with nothing registered, as upstream, do nothing. A fire hazard is
	 * registered for the test and taken away again.
	 */
	@GameTest(template = EMPTY16)
	public static void legacyHazardBlocksApplyTheirItemsHazards(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		BlockPos at = new BlockPos(3, 1, 3);
		var level = helper.getLevel();
		Item zero = item("waste_terracotta_0"), iceItem = LeafiaBlocks.waste_ice.get().asItem();
		BlockState terracotta = LeafiaBlocks.waste_terracotta.get().withMeta(4);
		BlockState ice = LeafiaBlocks.waste_ice.get().defaultBlockState();

		var bare = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 8));
		terracotta.getBlock().stepOn(level, helper.absolutePos(at), terracotta, bare);
		ice.getBlock().stepOn(level, helper.absolutePos(at), ice, bare);
		helper.assertTrue(bare.getRemainingFireTicks() <= 0, "something was applied with nothing registered");
		bare.discard();

		HazardData previousTerracotta = HazardSystem.itemMap.get(zero), previousIce = HazardSystem.itemMap.get(iceItem);
		try {
			HazardSystem.register(zero, new HazardData().addEntry(HazardRegistry.HOT, 5F));
			HazardSystem.register(iceItem, new HazardData().addEntry(HazardRegistry.HOT, 5F));
			for(int how = 0; how < 4; how++) {
				var pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 8));
				switch(how) {
					case 0 -> terracotta.getBlock().stepOn(level, helper.absolutePos(at), terracotta, pig);
					case 1 -> terracotta.entityInside(level, helper.absolutePos(at), pig);
					case 2 -> ice.getBlock().stepOn(level, helper.absolutePos(at), ice, pig);
					default -> ice.entityInside(level, helper.absolutePos(at), pig);
				}
				helper.assertTrue(pig.getRemainingFireTicks() > 0, "way " + how + " applied nothing");
				pig.discard();
			}
		} finally {
			if(previousTerracotta == null) HazardSystem.itemMap.remove(zero); else HazardSystem.itemMap.put(zero, previousTerracotta);
			if(previousIce == null) HazardSystem.itemMap.remove(iceItem); else HazardSystem.itemMap.put(iceItem, previousIce);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ balefire corium

	/** Balefire corium: numbers, tick, block, and that the fuel rods find it. A block with no fluid since D-2173 (D-3052). */
	@GameTest(template = EMPTY16)
	public static void balefireCoriumIsACoriumPoolWithItsOwnNumbers(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }

		var holder = LeafiaLiquids.balecorium;
		helper.assertTrue(holder != null && holder.getId().equals(LeafiaRef.rl("balecorium_fluid")), "registered under upstream's name");
		BaleCoriumFluidBlock bale = holder.get();
		helper.assertTrue(bale instanceof CoriumFinite, "its block is the base mod's corium");
		helper.assertTrue(bale.tickRate == 20 && ModBlocks.corium_block.get().tickRate == 30, "ticks every 20, not corium's 30: " + bale.tickRate);
		helper.assertTrue(bale.density == 31200 && bale.quantaPerBlock == CoriumFinite.QUANTA, "density " + bale.density + ", " + bale.quantaPerBlock + " quanta");
		helper.assertTrue(BaleCoriumFluid.VISCOSITY == 500 && BaleCoriumFluid.TEMPERATURE == 5273, "upstream's viscosity and temperature");
		helper.assertTrue(bale.withQuanta(CoriumFinite.QUANTA).getLightEmission() == 0, "no light: neither upstream's fluid nor its block sets one");
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("balecorium_fluid"))
				&& !BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("balecorium_bucket"))
				&& !BuiltInRegistries.FLUID.containsKey(LeafiaRef.rl("balecorium")), "a block, with no fluid and no bucket, as corium");

		// and a balefire rod melts into it
		var rods = com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods.get("leafRodBF");
		helper.assertTrue(rods == null || rods.corium() == bale, "the balefire rod melts into " + (rods == null ? null : rods.corium()));
		helper.succeed();
	}

	/**
	 * Upstream's {@code canDisplace}: one time in three, no; air, and anything with a resistance
	 * under 1, always; otherwise one chance in {@code (int)(resistance^0.35)}; and never a liquid.
	 */
	@GameTest(template = EMPTY)
	public static void balefireCoriumDisplacesByTheFlatterCurve(GameTestHelper helper) {

		BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), obsidian = Blocks.OBSIDIAN.defaultBlockState();
		helper.assertTrue(!BaleCoriumFluid.canDisplace(new Script(0), air), "the one-in-three refusal comes first");
		helper.assertTrue(BaleCoriumFluid.canDisplace(new Script(1), air), "air");
		// with every later roll a hit, so only the liquid rule can say no
		helper.assertTrue(!BaleCoriumFluid.canDisplace(new Script(1, 0), Blocks.WATER.defaultBlockState()) && !BaleCoriumFluid.canDisplace(new Script(1, 0), Blocks.LAVA.defaultBlockState()), "no liquid");

		// stone: 6^0.35 = 1.87, int 1, so nextInt(1)
		Script stoneRoll = new Script(1, 0);
		helper.assertTrue(BaleCoriumFluid.canDisplace(stoneRoll, stone) && stoneRoll.bounds.equals(List.of(3, 1)), "stone: " + stoneRoll.bounds);
		// obsidian: 1200^0.35 = 11.96, int 11
		Script obsidianHit = new Script(1, 0), obsidianMiss = new Script(1, 5);
		helper.assertTrue(BaleCoriumFluid.canDisplace(obsidianHit, obsidian) && obsidianHit.bounds.equals(List.of(3, 11)), "obsidian hit: " + obsidianHit.bounds);
		helper.assertTrue(!BaleCoriumFluid.canDisplace(obsidianMiss, obsidian), "obsidian miss");
		// and corium's own curve is the steeper sqrt x 3: 1200 gives 103, so the exponent really is 0.35
		helper.assertTrue((int) (Math.sqrt(1200F) * 3) == 103 && (int) Math.pow(1200F, 0.35) == 11, "103 against 11");
		helper.succeed();
	}

	/**
	 * A full block on a floor: refused the floor by its own chance, it spreads its five quanta over the four
	 * sides and keeps one, losing none (upstream's {@code BlockFluidFinite}, which spreads into air whatever
	 * {@code canDisplace} says); allowed it, it melts the stone and drops into it whole. And it stands in for
	 * corium: it sets fire to what is in it.
	 */
	@GameTest(template = EMPTY16)
	public static void balefireCoriumSpreadsAndBurns(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }

		BaleCoriumFluidBlock block = LeafiaLiquids.balecorium.get();
		ServerLevel level = helper.getLevel();
		for(int x = 0; x < 9; x++) for(int z = 0; z < 9; z++) helper.setBlock(x, 0, z, Blocks.STONE);
		BlockPos at = new BlockPos(4, 1, 4);
		BlockPos abs = helper.absolutePos(at);

		// Refused: the one-in-three roll says no to the floor, and the pool levels out sideways.
		helper.setBlock(at, block.withQuanta(CoriumFinite.QUANTA));
		Script refuse = new Script(0, 0, 0, 0, 0, 0, 0, 0);
		RandomSource old = block.swapRandom(refuse);
		try {
			block.updateTick(level, abs, new Script());
		} finally {
			block.swapRandom(old);
		}
		helper.assertTrue(helper.getBlockState(at.below()).is(Blocks.STONE), "the floor survived a refusal");
		int total = block.getQuantaValue(level, abs);
		for(Direction d : GenericFiniteFluid.SPREAD_ORDER) {
			int side = block.getQuantaValue(level, abs.relative(d));
			helper.assertTrue(side == 1, d + " holds " + side + " quanta, expected one");
			total += side;
		}
		helper.assertTrue(total == CoriumFinite.QUANTA && block.getQuantaValue(level, abs) == 1, "five quanta, kept: " + total);
		helper.assertTrue(!refuse.bounds.isEmpty() && refuse.bounds.get(0) == 3, "the one-in-three roll is asked first: " + refuse.bounds);
		for(Direction d : GenericFiniteFluid.SPREAD_ORDER) helper.setBlock(at.relative(d), Blocks.AIR);

		// Allowed: stone is 6^0.35 = 1.87, int 1, so nextInt(1) - the floor goes and the pool drops whole.
		helper.setBlock(at, block.withQuanta(CoriumFinite.QUANTA));
		Script allow = new Script(1, 0);
		old = block.swapRandom(allow);
		try {
			block.updateTick(level, abs, new Script());
		} finally {
			block.swapRandom(old);
		}
		helper.assertTrue(helper.getBlockState(at).isAir() && block.getQuantaValue(level, abs.below()) == CoriumFinite.QUANTA,
				"it melted the stone and fell into it: " + helper.getBlockState(at.below()));
		helper.assertTrue(allow.bounds.equals(List.of(3, 1)), "the floor was asked about as 3 then 1: " + allow.bounds);

		var pig = helper.spawn(EntityType.PIG, new BlockPos(7, 1, 7));
		helper.setBlock(pig.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)), block.withQuanta(CoriumFinite.QUANTA));
		block.withQuanta(CoriumFinite.QUANTA).entityInside(level, pig.blockPosition(), pig);
		helper.assertTrue(pig.getRemainingFireTicks() > 0, "standing in it did not set a pig alight");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the fallout table

	private static String describe(FalloutEntry entry) throws Exception {
		StringWriter out = new StringWriter();
		try(JsonWriter writer = new JsonWriter(out)) {
			writer.beginObject();
			entry.write(writer);
			writer.endObject();
		}
		return out.toString();
	}

	private static JsonObject json(FalloutEntry entry) throws Exception {
		return JsonParser.parseString(describe(entry)).getAsJsonObject();
	}

	/**
	 * Runs a body against a fresh copy of the base mod's default table, with the overhaul switch set,
	 * and puts everything back: the table, both flags, and the random.
	 */
	private static void withTable(boolean overhaul, Consumer<List<FalloutEntry>> body) {
		List<FalloutEntry> saved = new ArrayList<>(FalloutConfigJSON.entries);
		boolean savedFlag = LeafiaFeatures.falloutOverhaul;
		var savedRandom = FalloutConfigJSON.rand;
		try {
			FalloutConfigJSON.entries.clear();
			FalloutConfigJSON.defaults();
			LeafiaFeatures.falloutOverhaul = overhaul;
			FalloutConfigInit.onInit();
			body.accept(FalloutConfigJSON.entries);
		} finally {
			FalloutConfigJSON.entries.clear();
			FalloutConfigJSON.entries.addAll(saved);
			LeafiaFeatures.falloutOverhaul = savedFlag;
			FalloutConfigJSON.rand = savedRandom;
			FalloutConfigInit.onInit();
		}
	}

	/** Off, the base table is untouched; the addon's own two tables are still built. */
	@GameTest(template = EMPTY)
	public static void withTheOverhaulOffTheBaseTableIsLeftAlone(GameTestHelper helper) {

		List<FalloutEntry> saved = new ArrayList<>(FalloutConfigJSON.entries);
		FalloutConfigJSON.entries.clear();
		FalloutConfigJSON.defaults();
		int base = FalloutConfigJSON.entries.size();
		boolean flag = LeafiaFeatures.falloutOverhaul;
		try {
			LeafiaFeatures.falloutOverhaul = false;
			FalloutConfigInit.onInit();
			helper.assertTrue(FalloutConfigJSON.entries.size() == base, "the base table changed from " + base + " to " + FalloutConfigJSON.entries.size());
			helper.assertTrue(FalloutConfigInit.endoEntries.size() == 4, "endo rows: " + FalloutConfigInit.endoEntries.size());
			helper.assertTrue(FalloutConfigInit.digammaEntries.size() == (LeafiaFeatures.digammitite ? 12 : 0), "digamma rows: " + FalloutConfigInit.digammaEntries.size());
		} finally {
			LeafiaFeatures.falloutOverhaul = flag;
			FalloutConfigJSON.entries.clear();
			FalloutConfigJSON.entries.addAll(saved);
			FalloutConfigInit.onInit();
		}
		helper.succeed();
	}

	/** On: grass, ground and sand rows and both trinitite rows go; sixty-one come in; running it twice changes nothing. */
	@GameTest(template = EMPTY)
	public static void theOverhaulEditsTheBaseTableInPlace(GameTestHelper helper) throws Exception {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		List<FalloutEntry> base = new ArrayList<>();
		FalloutConfigJSON.entries.clear();
		base.addAll(FalloutConfigJSON.defaults());
		int before = base.size();
		int removable = 0;
		for(FalloutEntry entry : new ArrayList<>(base)) {
			JsonObject json = json(entry);
			String mat = json.has("matchesMaterial") ? json.get("matchesMaterial").getAsString() : "";
			boolean trinitite = json.has("primarySubstitution") && json.get("primarySubstitution").toString().contains("waste_trinitite");
			if(mat.equals("grass") || mat.equals("ground") || mat.equals("sand") || trinitite) removable++;
		}
		helper.assertTrue(removable == 29 + 2, "the base table has " + removable + " removable rows, expected 9 + 10 + 10 + 2");
		final int removed = removable;

		int[] firstSize = new int[1];
		withTable(true, table -> {
			firstSize[0] = table.size();
			helper.assertTrue(table.size() == before - removed + 61, "the table is " + table.size() + ", expected " + (before - removed + 61));
			FalloutConfigInit.onInit();
			helper.assertTrue(table.size() == firstSize[0], "a second pass changed the table to " + table.size());
			long ours = table.stream().filter(e -> e instanceof LeafiaFalloutEntry).count();
			helper.assertTrue(ours == 61, "our rows: " + ours);
		});
		helper.succeed();
	}

	/** The ladder: five levels, 59 to 95 percent, level 6 down to 2; and the terracotta rows reversed at the front. */
	@GameTest(template = EMPTY)
	public static void theLadderRunsFromSixtyToNinetyFivePercent(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		withTable(true, table -> {
			// the front: terracotta level 2 first, level 6 last - upstream inserted each at 0
			for(int k = 0; k < 5; k++) {
				var row = (LeafiaFalloutEntry) table.get(k);
				helper.assertTrue(row.getPrimary().get(0).state() == LeafiaBlocks.waste_terracotta.get().withMeta(2 + k), "front row " + k + " is " + row.getPrimary().get(0).state());
				double max = (1 + (4 - k)) * 15 * (1f / 5 * 3) + 50;
				helper.assertTrue(row.getMaxDist() == max, "front row " + k + " reaches " + row.getMaxDist() + ", expected " + max);
			}
			double[] expected = {59, 68, 77, 86, 95};
			for(int i = 0; i < 5; i++)
				helper.assertTrue(table.get(4 - i) instanceof LeafiaFalloutEntry e && e.getMaxDist() == expected[i], "level " + (6 - i) + " reaches " + table.get(4 - i).getMaxDist());
			helper.assertTrue(FalloutConfigInit.LADDER_MUL == 1f / 5 * 3 && FalloutConfigInit.LADDER_OFFSET == 50, "the constants are float arithmetic");
		});
		helper.succeed();
	}

	/** What a block becomes at a distance: the first row that matches, as the base mod's fallout rain asks it. */
	private static BlockState convert(GameTestHelper helper, List<FalloutEntry> table, Block block, double dist) {
		BlockPos at = new BlockPos(2, 1, 2);
		BlockState before = block.defaultBlockState();
		helper.setBlock(at, before);
		for(FalloutEntry entry : table)
			if(entry.eval(helper.getLevel(), helper.absolutePos(at), helper.getBlockState(at), dist)) return helper.getBlockState(at);
		return helper.getBlockState(at);
	}

	private static final class Chance extends java.util.Random {
		private final double answer;
		Chance(double answer) { this.answer = answer; }
		@Override public double nextDouble() { return answer; }
	}

	/** The rows do what they say, over real blocks: sand, red sand, gravel, dirt, grass, sandstone, terracotta, packed ice. */
	@GameTest(template = EMPTY16)
	public static void theRowsConvertTheGroundByDistance(GameTestHelper helper) {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		withTable(true, table -> {
			FalloutConfigJSON.rand = new Chance(0.99); // never the 5 percent trinitite
			Object[][] cases = {
					{Blocks.SAND, 10.0, LeafiaBlocks.waste_sand.get().withMeta(6)},
					{Blocks.SAND, 60.0, LeafiaBlocks.waste_sand.get().withMeta(5)},
					{Blocks.SAND, 69.0, LeafiaBlocks.waste_sand.get().withMeta(4)},
					{Blocks.SAND, 77.2, LeafiaBlocks.waste_sand.get().withMeta(3)},
					{Blocks.RED_SAND, 10.0, LeafiaBlocks.waste_sand_red.get().withMeta(6)},
					{Blocks.GRAVEL, 68.5, LeafiaBlocks.waste_gravel.get().withMeta(4)}, // past 68 (level 5) and short of the level-4 row's gaussian fade, which starts at 0.9 x 77 = 69.3 (70 sat inside it: 1 red run in 3, D-3004)
					{Blocks.DIRT, 10.0, LeafiaBlocks.waste_dirt.get().withMeta(6)},
					{Blocks.GRASS_BLOCK, 10.0, LeafiaBlocks.scorched_earth.get().withMeta(6)},
					{Blocks.GRASS_BLOCK, 69.0, LeafiaBlocks.scorched_earth.get().withMeta(4)},
					// rock is slaked sellafite inside 50 percent, where the base mod's rows come first
					// at 12, not 10: the base's level-8 rock row ends at exactly 10 and fades on the level's own gaussian from 9, so at 10
					// it still converts whenever |g| >= 3 - 0.27 percent of runs (measured 9 in 5,000), seen twice as "level=8" (D-3042).
					// 12 is past that row and short of the level-7 row's fade at 13.5 (0 in 5,000).
					{Blocks.SANDSTONE, 12.0, ModBlocks.sellafield_slaked.get().defaultBlockState().setValue(com.hbm.blocks.generic.BlockSellafieldSlaked.LEVEL, 7)},
					{Blocks.SANDSTONE, 60.0, LeafiaBlocks.waste_sandstone.get().withMeta(5)},
					{Blocks.RED_SANDSTONE, 60.0, LeafiaBlocks.waste_red_sandstone.get().withMeta(5)},
					{Blocks.TERRACOTTA, 10.0, LeafiaBlocks.waste_terracotta.get().withMeta(2)},
					{Blocks.TERRACOTTA, 80.0, LeafiaBlocks.waste_terracotta.get().withMeta(2)}, // upstream's reversed insertion
					{Blocks.PACKED_ICE, 10.0, LeafiaBlocks.waste_ice.get().defaultBlockState()}};
			for(Object[] c : cases) {
				BlockState got = convert(helper, table, (Block) c[0], (Double) c[1]);
				helper.assertTrue(got == c[2], c[0] + " at " + c[1] + " became " + got + ", expected " + c[2]);
			}
			// out of range: sand at 97 percent stays sand
			helper.assertTrue(convert(helper, table, Blocks.SAND, 97.0).is(Blocks.SAND), "sand past the ladder changed");
			// and in the last stretch the row fades out on a gaussian, so it is level 2 or sand and never anything else
			int converted = 0, kept = 0;
			for(int i = 0; i < 300; i++) {
				BlockState got = convert(helper, table, Blocks.SAND, 90.0);
				if(got == LeafiaBlocks.waste_sand.get().withMeta(2)) converted++;
				else if(got.is(Blocks.SAND)) kept++;
				else helper.fail("sand at 90 percent became " + got);
			}
			helper.assertTrue(converted > 0 && kept > 0, "the fade: " + converted + " converted, " + kept + " kept");
			// not opaque, so never: upstream's snow rows and ice rows ask for an opaque cube
			// (the base mod destroys snow out to 65 percent, so look past that)
			helper.assertTrue(convert(helper, table, Blocks.SNOW, 70.0).is(Blocks.SNOW), "a snow layer changed, though it is not opaque");
			helper.assertTrue(convert(helper, table, Blocks.ICE, 70.0).is(Blocks.ICE), "ice changed, though it is not opaque");

			// and with the chance on its side, sand makes trinitite first
			FalloutConfigJSON.rand = new Chance(0.0);
			helper.assertTrue(convert(helper, table, Blocks.SAND, 10.0).is(ModBlocks.waste_trinitite.get()), "sand at 5 percent");
			helper.assertTrue(convert(helper, table, Blocks.RED_SAND, 10.0).is(ModBlocks.waste_trinitite_red.get()), "red sand at 5 percent");
		});
		helper.succeed();
	}

	/** The two private tables: digammitite over iron and rock to 45 percent; frozen forms to 45 percent. */
	@GameTest(template = EMPTY16)
	public static void theDigammaAndEndothermicTables(GameTestHelper helper) {
		FalloutConfigInit.onInit();

		if(LeafiaFeatures.digammitite) {
			List<FalloutEntry> table = FalloutConfigInit.digammaEntries;
			helper.assertTrue(table.size() == 12, "digamma rows: " + table.size());
			Object[][] cases = {
					{Blocks.IRON_BLOCK, 5.0, 5}, {Blocks.STONE, 5.0, 5}, {Blocks.IRON_BLOCK, 20.0, 3}, {Blocks.STONE, 20.0, 3},
					{Blocks.IRON_BLOCK, 40.0, 0}, {Blocks.STONE, 40.0, 0}};
			for(Object[] c : cases) {
				BlockState got = convert(helper, table, (Block) c[0], (Double) c[1]);
				helper.assertTrue(got == LeafiaBlocks.digammitite.get().withMeta((Integer) c[2]), c[0] + " at " + c[1] + " became " + got);
			}
			helper.assertTrue(convert(helper, table, Blocks.STONE, 46.0).is(Blocks.STONE), "stone past 45 percent changed");
			helper.assertTrue(convert(helper, table, Blocks.DIRT, 5.0).is(Blocks.DIRT), "dirt is neither iron nor rock");
		}

		List<FalloutEntry> endo = FalloutConfigInit.endoEntries;
		Object[][] frozen = {{Blocks.GRASS_BLOCK, ModBlocks.frozen_grass.get()}, {Blocks.DIRT, ModBlocks.frozen_dirt.get()},
				{Blocks.OAK_LOG, ModBlocks.frozen_log.get()}, {Blocks.ACACIA_LOG, ModBlocks.frozen_log.get()}, {Blocks.SPRUCE_PLANKS, ModBlocks.frozen_planks.get()}};
		for(Object[] c : frozen) {
			BlockState got = convert(helper, endo, (Block) c[0], 10.0);
			helper.assertTrue(got.is((Block) c[1]), c[0] + " became " + got);
		}
		helper.assertTrue(convert(helper, endo, Blocks.DIRT, 46.0).is(Blocks.DIRT), "dirt past 45 percent changed");
		helper.succeed();
	}

	/**
	 * The base mod rebuilds its table every time a server starts and then reads a player's file; the
	 * addon's edit must come after, on the same event. Both handlers are looked up by what they are
	 * annotated with, then run in that order: the edit lands on a table the base has just rebuilt, not
	 * on one it is about to throw away.
	 */
	@GameTest(template = EMPTY)
	public static void theEditRunsAfterTheBaseMods(GameTestHelper helper) throws Exception {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		var base = FalloutConfigJSON.class.getDeclaredMethod("onServerAboutToStart", net.neoforged.neoforge.event.server.ServerAboutToStartEvent.class)
				.getAnnotation(net.neoforged.bus.api.SubscribeEvent.class);
		var ours = FalloutConfigInit.class.getDeclaredMethod("onServerAboutToStart", net.neoforged.neoforge.event.server.ServerAboutToStartEvent.class)
				.getAnnotation(net.neoforged.bus.api.SubscribeEvent.class);
		helper.assertTrue(base != null && ours != null, "both are subscribed");
		helper.assertTrue(ours.priority().ordinal() > base.priority().ordinal(), "ours is " + ours.priority() + " and the base's is " + base.priority() + ": a later priority runs later");
		helper.assertTrue(FalloutConfigInit.class.isAnnotationPresent(net.neoforged.fml.common.EventBusSubscriber.class), "the class is registered");

		List<FalloutEntry> saved = new ArrayList<>(FalloutConfigJSON.entries);
		boolean flag = LeafiaFeatures.falloutOverhaul;
		try {
			LeafiaFeatures.falloutOverhaul = true;
			FalloutConfigJSON.onServerAboutToStart(null); // the base rebuilds - and reads the player's file, if there is one
			helper.assertTrue(FalloutConfigJSON.entries.stream().noneMatch(e -> e instanceof LeafiaFalloutEntry), "the base's rebuild leaves none of ours");
			FalloutConfigInit.onServerAboutToStart(null);
			helper.assertTrue(FalloutConfigJSON.entries.stream().filter(e -> e instanceof LeafiaFalloutEntry).count() == 61, "and ours then edits the fresh table");
			FalloutConfigJSON.onServerAboutToStart(null);
			helper.assertTrue(FalloutConfigJSON.entries.stream().noneMatch(e -> e instanceof LeafiaFalloutEntry), "the next server start begins again");
		} finally {
			LeafiaFeatures.falloutOverhaul = flag;
			FalloutConfigJSON.entries.clear();
			FalloutConfigJSON.entries.addAll(saved);
			FalloutConfigInit.onInit();
		}
		helper.succeed();
	}

	/** Describing a row through the base's writer is how the edit reads it: the row shows its material and its outcome. */
	@GameTest(template = EMPTY)
	public static void aRowDescribesItselfThroughTheBasesWriter(GameTestHelper helper) throws Exception {
		if(!LeafiaFeatures.legacyWaste) { helper.succeed(); return; }

		var row = LeafiaFalloutEntry.builder().addPrimary(LeafiaBlocks.waste_dirt.get().withMeta(4), 1).max(59).opaque(true).solid(true)
				.matchingMaterial(LegacyMaterial.GROUND).build();
		JsonObject json = json(row);
		helper.assertTrue(json.get("matchesMaterial").getAsString().equals("ground") && json.get("maximumDistancePercent").getAsDouble() == 59
				&& json.get("primarySubstitution").getAsJsonArray().get(0).getAsJsonArray().get(0).getAsString().equals("leafia:waste_dirt")
				&& json.get("mustBeOpaque").getAsBoolean() && json.get("restrictDepth").getAsBoolean(), "the row said " + describe(row));
		withTable(false, table -> {
			int ground = FalloutConfigInit.removeByMatchingMaterial(LegacyMaterial.GROUND);
			int sand = FalloutConfigInit.removeByMatchingMaterial(LegacyMaterial.SAND);
			int trinitite = FalloutConfigInit.removeByPrimary(ModBlocks.waste_trinitite.get());
			helper.assertTrue(ground == 10 && sand == 10 && trinitite == 1, "removed " + ground + " ground, " + sand + " sand and " + trinitite + " trinitite rows");
		});
		helper.succeed();
	}
}
