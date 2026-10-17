package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.items.ModItems;
import com.hbm.lib.NtmTags;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.i18n.I18nServer;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.util.i18n.ITranslate;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.items.itembase.AddonItemHazardBase;
import com.leafia.init.LeafiaHazards;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Beta;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.lib.LeafiaRef;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's fifty simple material items (D-1085): what registers, in what order and tab, what each
 * looks like, what each says, and what each does to a player who carries it.
 *
 * <p>Every number below is written out by hand from upstream's {@code AddonItems} and
 * {@code ItemRads}, not read back from the table the generator wrote - a test that derived its
 * expectations from the thing under test would pass whatever the generator did. The hazard table
 * is the long one: eighteen of the fifty carry something, and the other thirty-two are pinned to
 * carry <em>nothing</em>, because an item that gains a hazard by accident is the bug a table is
 * most likely to have.
 *
 * <p>The switch-off case runs the whole table into a scratch registry with the switches flipped
 * ({@link LeafiaResourceItems#trial}), because the switches are read once, at registration, and a
 * running game cannot register again. Every switch a test flips is put back in a {@code finally}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaResourceItemTests {

	private static final String EMPTY = "empty";
	private static final float EPS = 1e-4F;

	/** Upstream's declaration order, which is the creative tab's order. */
	private static final List<String> NAMES = List.of(
			"dfcsh_cable", "dfcsh_core", "dfcsh_corner", "dfcsh_front", "dfcsh_beam",
			"ingot_potassium", "ingot_rubidium", "ingot_francium", "nugget_schraranium",
			"ingot_xanaxium", "powder_xanaxium", "billet_kys",
			"powder_digammitite", "powder_digammitite_tiny",
			"ingot_nc279", "powder_nc279", "billet_nc279",
			"ingot_taintium", "powder_taintium",
			"ingot_fissium", "billet_fissium",
			"ingot_chydalium", "powder_chydalium", "billet_chydalium",
			"ingot_fissite", "plate_fissite", "ingot_mysticite", "plate_mysticite", "ingot_tnalloy",
			"component_limiter", "component_emitter", "supercooler",
			"dna_felid", "dna_canid", "dna_eevee",
			"particle_dineutron", "pellet_rtg_fissium",
			"cells_fissite", "cells_mysticite", "cells_mysticite_filled",
			"particle_taint", "particle_cloud", "grenade_cloud", "grenade_pink_cloud",
			"blanket_fissite", "blanket_mysticite", "blanket_fissite_bundle", "blanket_mysticite_bundle",
			"particle_chydalium", "particle_absorption");

	/** {@code setCreativeTab(null)} upstream. */
	private static final Set<String> HIDDEN = Set.of("billet_kys", "dna_felid", "dna_canid", "dna_eevee");

	/** Items upstream never wrote a name for: it shows the raw key, and so does this port. */
	private static final Set<String> NO_UPSTREAM_NAME = Set.of(
			"dfcsh_cable", "dfcsh_core", "dfcsh_corner", "dfcsh_front", "dfcsh_beam",
			"component_limiter", "component_emitter", "grenade_cloud", "grenade_pink_cloud");

	// ---------------------------------------------------------------- plumbing

	private static Item item(String name) {
		DeferredItem<Item> held = LeafiaResourceItems.get(name);
		if(held == null) throw new IllegalStateException(name + " is not registered");
		return held.get();
	}

	private static String text(String path) {
		try(InputStream in = LeafiaResourceItemTests.class.getResourceAsStream("/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(text(path)).getAsJsonObject();
	}

	private static BufferedImage png(String path) {
		try(InputStream in = LeafiaResourceItemTests.class.getResourceAsStream("/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return ImageIO.read(in);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String lang(String key) {
		JsonObject lang = json("assets/leafia/lang/en_us.json");
		return lang.has(key) ? lang.get(key).getAsString() : null;
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	private static Player survivor(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.tickCount = ContaminationUtil.SPAWN_GRACE_TICKS + 200;
		HbmLivingProps.setRadiation(player, 0F);
		HbmLivingProps.setDigamma(player, 0F);
		return player;
	}

	private static void carryForATick(Player player, ItemStack stack) {
		for(int i = 0; i < 36; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
		player.getInventory().setItem(0, stack);
		HazardSystem.updatePlayerInventory(player);
	}

	/** The registered hazard entries of an item, by type, in the order registered. */
	private static Map<HazardTypeBase, Float> hazards(Item item) {
		Map<HazardTypeBase, Float> out = new LinkedHashMap<>();
		HazardData data = HazardSystem.itemMap.get(item);
		if(data != null) for(HazardEntry e : data.getEntries()) out.put(e.getType(), e.getBaseLevel());
		return out;
	}

	private static Map<HazardTypeBase, Float> expect(Object... pairs) {
		Map<HazardTypeBase, Float> out = new LinkedHashMap<>();
		for(int i = 0; i < pairs.length; i += 2) out.put((HazardTypeBase) pairs[i], ((Number) pairs[i + 1]).floatValue());
		return out;
	}

	private static boolean same(Map<HazardTypeBase, Float> a, Map<HazardTypeBase, Float> b) {
		if(!a.keySet().equals(b.keySet())) return false;
		for(HazardTypeBase k : a.keySet()) if(Math.abs(a.get(k) - b.get(k)) > EPS) return false;
		return true;
	}

	private static String show(Map<HazardTypeBase, Float> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(k.getClass().getSimpleName()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}

	// ---------------------------------------------------------------- registration

	/** Fifty items, upstream's order, stack of 64, common, undamageable, under the addon's namespace. */
	@GameTest(template = EMPTY)
	public static void fiftyItemsRegisterInUpstreamsOrder(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.resourceItems, "features.resourceItems is additive and on by default");
		helper.assertTrue(NAMES.size() == 50, "the expected list is fifty, got " + NAMES.size());
		helper.assertTrue(new ArrayList<>(LeafiaResourceItems.BY_NAME.keySet()).equals(NAMES),
				"registered " + LeafiaResourceItems.BY_NAME.keySet());
		helper.assertTrue(LeafiaResourceItems.NAMES.equals(NAMES), "the table's own list is " + LeafiaResourceItems.NAMES);

		for(String name : NAMES) {
			Item item = item(name);
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			helper.assertTrue(id.equals(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name)), name + " is registered as " + id);
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(stack.getMaxStackSize() == 64, name + " stacks to " + stack.getMaxStackSize() + ", upstream's is 64");
			helper.assertTrue(!stack.isDamageableItem(), name + " has durability");
			helper.assertTrue(stack.getRarity() == Rarity.COMMON, name + " is " + stack.getRarity());
			helper.assertTrue(!stack.has(net.minecraft.core.component.DataComponents.FOOD), name + " is food");
		}
		helper.succeed();
	}

	/** Upstream's {@code setCreativeTab(null)} on the Kysmium billet and the three DNA samples, and nothing else. */
	@GameTest(template = EMPTY)
	public static void onlyFourAreHiddenFromCreative(GameTestHelper helper) {

		List<Item> inTab = new ArrayList<>();
		LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).forEach(s -> inTab.add(s.get().asItem()));
		helper.assertTrue(inTab.size() > 100, "guard: the tab has the rest of the addon in it, got " + inTab.size());

		int shown = 0;
		for(String name : NAMES) {
			boolean listed = inTab.contains(item(name));
			helper.assertTrue(listed == !HIDDEN.contains(name), name + (listed ? " is in the tab" : " is not in the tab")
					+ ", upstream " + (HIDDEN.contains(name) ? "hides" : "shows") + " it");
			if(listed) shown++;
		}
		helper.assertTrue(shown == 46, "46 shown, got " + shown);

		// registered once: a duplicate in the tab would draw the item twice
		for(String name : NAMES) {
			Item i = item(name);
			helper.assertTrue(inTab.stream().filter(x -> x == i).count() <= 1, name + " is in the tab twice");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the switch

	/**
	 * {@code features.resourceItems} off registers nothing; {@code shields} on alone brings in
	 * exactly the two ingots; both on registers all fifty. Run into a scratch registry.
	 */
	@GameTest(template = EMPTY)
	public static void theSwitchRegistersNothingWhenOff(GameTestHelper helper) {

		boolean resource = LeafiaFeatures.resourceItems;
		boolean shields = LeafiaFeatures.shields;
		boolean rtg = LeafiaFeatures.rtg;
		try {
			LeafiaFeatures.resourceItems = false;
			LeafiaFeatures.shields = false;
			// features.rtg brings the depleted fissium pellet in by itself (D-3020); off, so this measures shields alone
			LeafiaFeatures.rtg = false;
			DeferredRegister.Items none = DeferredRegister.createItems("leafia_trial_none");
			Map<String, DeferredItem<? extends Item>> nothing = LeafiaResourceItems.trial(none);
			helper.assertTrue(nothing.isEmpty(), "both off still registered " + nothing.keySet());
			helper.assertTrue(none.getEntries().isEmpty(), "both off left entries in the registry: " + none.getEntries());

			LeafiaFeatures.shields = true;
			DeferredRegister.Items two = DeferredRegister.createItems("leafia_trial_two");
			Map<String, DeferredItem<? extends Item>> got = LeafiaResourceItems.trial(two);
			helper.assertTrue(new ArrayList<>(got.keySet()).equals(List.of("ingot_fissite", "ingot_mysticite")),
					"shields alone should bring exactly the two ingots, brought " + got.keySet());
			helper.assertTrue(two.getEntries().size() == 2, "and two registry entries, got " + two.getEntries().size());

			LeafiaFeatures.resourceItems = true;
			DeferredRegister.Items all = DeferredRegister.createItems("leafia_trial_all");
			helper.assertTrue(LeafiaResourceItems.trial(all).size() == 50, "both on should register fifty");

			LeafiaFeatures.shields = false;
			DeferredRegister.Items resourceOnly = DeferredRegister.createItems("leafia_trial_resource");
			helper.assertTrue(LeafiaResourceItems.trial(resourceOnly).size() == 50, "resourceItems alone brings the ingots too: fifty");
		} finally {
			LeafiaFeatures.resourceItems = resource;
			LeafiaFeatures.shields = shields;
			LeafiaFeatures.rtg = rtg;
		}

		// the trial touched nothing real
		helper.assertTrue(LeafiaResourceItems.BY_NAME.size() == 50, "the real table was disturbed");
		helper.assertTrue(LeafiaItems.ingot_fissite != null && LeafiaItems.ingot_mysticite != null, "LeafiaItems' fields were disturbed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the look

	/** A flat model over a 16x16 texture that exists, for all fifty - the two ingots included. */
	@GameTest(template = EMPTY)
	public static void everyItemHasAFlatModelOverASixteenPixelTexture(GameTestHelper helper) {

		Set<String> seen = new java.util.HashSet<>();
		for(String name : NAMES) {
			JsonObject model = json("assets/leafia/models/item/" + name + ".json");
			helper.assertTrue("minecraft:item/generated".equals(model.get("parent").getAsString()), name + "'s parent is " + model.get("parent"));
			String layer = model.getAsJsonObject("textures").get("layer0").getAsString();
			helper.assertTrue(layer.startsWith("leafia:item/"), name + "'s texture is " + layer);
			BufferedImage image = png("assets/leafia/textures/item/" + layer.substring("leafia:item/".length()) + ".png");
			helper.assertTrue(image.getWidth() == 16 && image.getHeight() == 16, name + "'s texture is " + image.getWidth() + "x" + image.getHeight());
			seen.add(layer);
		}
		// the three DNA samples share upstream's one texture; everything else has its own
		helper.assertTrue(json("assets/leafia/models/item/dna_felid.json").getAsJsonObject("textures").get("layer0").getAsString().equals("leafia:item/dna"), "dna_felid");
		helper.assertTrue(json("assets/leafia/models/item/dna_eevee.json").getAsJsonObject("textures").get("layer0").getAsString().equals("leafia:item/dna"), "dna_eevee");
		helper.assertTrue(seen.size() == 48, "fifty items over 48 textures (the DNA trio shares one), got " + seen.size());
		helper.succeed();
	}

	// ---------------------------------------------------------------- the words

	/** Upstream's English, for a spread of items, and the nine upstream never named. */
	@GameTest(template = EMPTY)
	public static void namesAreUpstreamsEnglish(GameTestHelper helper) {

		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("ingot_potassium", "Potassium Cube");
		expected.put("ingot_rubidium", "Rubidium Ingot");
		expected.put("ingot_tnalloy", "Taintium Steel Ingot");
		expected.put("billet_kys", "Kysmium-3000 Billet");
		expected.put("powder_digammitite_tiny", "Tiny Digammitite Powder");
		expected.put("nugget_schraranium", "Schraranium Nugget");
		expected.put("supercooler", "Supercooling Unit");
		expected.put("particle_dineutron", "Dineutron Particle Capsule");
		expected.put("particle_cloud", "Corrosive Fume Base");
		expected.put("pellet_rtg_fissium", "Depleted Fissium RTG Pellet");
		expected.put("cells_fissite", "Empty Fissite Anti-Mass Cell");
		expected.put("cells_mysticite_filled", "Mysticite Anti-Mass Cell");
		expected.put("blanket_fissite_bundle", "Bundle of Fissite Blanket");
		expected.put("dna_eevee", "Eevee DNA");
		expected.put("plate_mysticite", "Mysticite Plate");
		expected.put("ingot_fissite", "Fissite Ingot");
		expected.put("ingot_mysticite", "Mysticite Ingot");
		expected.forEach((name, english) -> helper.assertTrue(english.equals(lang("item.leafia." + name)),
				name + " is " + lang("item.leafia." + name) + ", upstream's is " + english));

		int named = 0;
		for(String name : NAMES) {
			boolean has = lang("item.leafia." + name) != null;
			helper.assertTrue(has == !NO_UPSTREAM_NAME.contains(name),
					name + (has ? " has" : " has no") + " name; upstream " + (NO_UPSTREAM_NAME.contains(name) ? "has none" : "has one"));
			if(has) named++;
		}
		helper.assertTrue(named == 41, "41 named, got " + named);
		helper.succeed();
	}

	/** The Kysmium billet's two lines, the absorption capsule's three yellow ones. */
	@GameTest(template = EMPTY)
	public static void theTwoTooltipsSayUpstreamsWords(GameTestHelper helper) {

		List<Component> kys = new ArrayList<>();
		item("billet_kys").appendHoverText(new ItemStack(item("billet_kys")), Item.TooltipContext.EMPTY, kys, TooltipFlag.NORMAL);
		List<String> kysLines = kys.stream().map(Component::getString).toList();
		helper.assertTrue(kysLines.equals(List.of("Inspired by Quasar!", "Corrstud will pay their life for this very billet.")), "kys " + kysLines);

		// I18nUtil answers a server with a sarcastic constant (D-003), so answer from the real lang file.
		ITranslate before = I18nUtil.getTranslator();
		try {
			I18nUtil.setTranslator(new I18nServer() {
				@Override
				public String[] resolveKeyArray(String s, Object... args) {
					String value = lang(s);
					return value == null ? new String[] { "MISSING " + s } : value.split("\\$");
				}
			});
			List<Component> absorb = new ArrayList<>();
			item("particle_absorption").appendHoverText(new ItemStack(item("particle_absorption")), Item.TooltipContext.EMPTY, absorb, TooltipFlag.NORMAL);
			List<String> lines = absorb.stream().map(Component::getString).toList();
			helper.assertTrue(lines.equals(List.of("Absorbs thermal energy and lights to it's", "own thermal and kinetic energy",
					"Could be used to directly power a turbine..")), "absorption " + lines);
			for(Component c : absorb)
				helper.assertTrue(c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == ChatFormatting.YELLOW.getColor(),
						"absorption's lines are yellow, got " + c.getStyle().getColor());
		} finally {
			I18nUtil.setTranslator(before);
		}

		// a plain item adds nothing
		List<Component> plain = new ArrayList<>();
		item("ingot_potassium").appendHoverText(new ItemStack(item("ingot_potassium")), Item.TooltipContext.EMPTY, plain, TooltipFlag.NORMAL);
		helper.assertTrue(plain.isEmpty(), "a plain ingot has a tooltip: " + plain);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the ore dictionary

	/** Upstream's {@code AddonOreDict.registerOres}, as the item tags the base mod's naming rule makes. */
	@GameTest(template = EMPTY)
	public static void oreDictionaryNamesAreTags(GameTestHelper helper) {

		String[][] table = {
				{"ingotPotassium", "ingot_potassium"}, {"ingotRubidium", "ingot_rubidium"}, {"ingotFrancium", "ingot_francium"},
				{"nuggetSchraranium", "nugget_schraranium"},
				{"ingotXanaxium", "ingot_xanaxium"}, {"dustXanaxium", "powder_xanaxium"},
				{"ingotNc279", "ingot_nc279"}, {"dustNc279", "powder_nc279"}, {"billetNc279", "billet_nc279"},
				{"ingotTaintium", "ingot_taintium"}, {"dustTaintium", "powder_taintium"},
				{"ingotFissium", "ingot_fissium"}, {"billetFissium", "billet_fissium"},
				{"ingotFissite", "ingot_fissite"}, {"plateFissite", "plate_fissite"},
				{"ingotMysticite", "ingot_mysticite"}, {"plateMysticite", "plate_mysticite"},
				{"ingotTnAlloy", "ingot_tnalloy"},
				{"ingotChydalium", "ingot_chydalium"}, {"dustChydalium", "powder_chydalium"}, {"billetChydalium", "billet_chydalium"},
		};
		for(String[] row : table) {
			helper.assertTrue(new ItemStack(item(row[1])).is(NtmTags.ore(row[0])), row[1] + " is not in the tag for " + row[0] + " (" + NtmTags.orePath(row[0]) + ")");
		}
		helper.assertTrue(table.length == 21, "twenty-one registrations, 19 of them this batch's");

		// and the tag has nothing it should not: a powder is not an ingot
		helper.assertTrue(!new ItemStack(item("powder_nc279")).is(NtmTags.ore("ingotNc279")), "powder in the ingot tag");
		helper.assertTrue(!new ItemStack(item("billet_kys")).is(NtmTags.ore("billetFissium")), "kys billet in a tag");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the hazards

	/**
	 * Eighteen items carry a hazard and thirty-two carry none. Each figure is upstream's container
	 * times upstream's multiplier, worked by hand: nuclium is beta 250 and gamma 70; fissium is gamma
	 * 10 and neutrons 30; fissite is a tenth of it.
	 */
	@GameTest(template = EMPTY)
	public static void hazardsAreUpstreamsNumbers(GameTestHelper helper) {

		HazardTypeBase hot = HazardRegistry.HOT;
		HazardTypeBase dig = LeafiaHazards.DIGAMMA;
		Map<String, Map<HazardTypeBase, Float>> table = new LinkedHashMap<>();
		table.put("dfcsh_cable", expect(dig, 0.003));
		table.put("dfcsh_core", expect(hot, 10));
		table.put("dfcsh_corner", expect(dig, 0.005));
		table.put("dfcsh_front", expect(dig, 0.004));
		table.put("dfcsh_beam", expect(dig, 0.002));
		table.put("powder_digammitite", expect(dig, 0.1));
		table.put("powder_digammitite_tiny", expect(dig, 0.01));
		table.put("ingot_nc279", expect(Beta.INSTANCE, 250, Gamma.INSTANCE, 70, hot, 4));
		table.put("powder_nc279", expect(Beta.INSTANCE, 750, Gamma.INSTANCE, 210, hot, 4));
		table.put("billet_nc279", expect(Beta.INSTANCE, 125, Gamma.INSTANCE, 35, hot, 4));
		table.put("ingot_fissium", expect(Gamma.INSTANCE, 10, Neutrons.INSTANCE, 30));
		table.put("billet_fissium", expect(Gamma.INSTANCE, 5, Neutrons.INSTANCE, 15));
		table.put("ingot_fissite", expect(Gamma.INSTANCE, 1, Neutrons.INSTANCE, 3));
		table.put("plate_fissite", expect(Gamma.INSTANCE, 1, Neutrons.INSTANCE, 3));
		table.put("pellet_rtg_fissium", expect(Gamma.INSTANCE, 5, Neutrons.INSTANCE, 15));
		table.put("cells_fissite", expect(Gamma.INSTANCE, 5, Neutrons.INSTANCE, 15));
		table.put("blanket_fissite", expect(Gamma.INSTANCE, 0.0625, Neutrons.INSTANCE, 0.1875));
		table.put("blanket_fissite_bundle", expect(Gamma.INSTANCE, 0.5, Neutrons.INSTANCE, 1.5));
		// the APR's capsule (D-3027): upstream's AddonHazards gives it HazardTypeEndothermic at 15
		boolean apr = LeafiaFeatures.aprReactor();
		if(apr)
			table.put("particle_absorption", expect(com.leafia.init.hazards.types.containment.HazardTypeEndothermic.INSTANCE, 15));

		for(String name : NAMES) {
			Map<HazardTypeBase, Float> want = table.getOrDefault(name, expect());
			Map<HazardTypeBase, Float> got = hazards(item(name));
			helper.assertTrue(same(want, got), name + " should carry " + show(want) + ", carries " + show(got));
		}
		helper.assertTrue(table.size() == 18 + (apr ? 1 : 0), "eighteen carry something, nineteen with the APR");

		// the radiation types are entries of their own, in upstream's order: alpha, beta, gamma, x, neutrons ...
		HazardData data = HazardSystem.itemMap.get(item("ingot_nc279"));
		helper.assertTrue(data.getEntries().get(0).getType() == Beta.INSTANCE && data.getEntries().get(1).getType() == Gamma.INSTANCE
				&& data.getEntries().get(2).getType() == hot, "nuclium's entries are in upstream's order");
		// ... and carry no NBT modifier: that is ItemRads.apply's, which this path never goes through
		helper.assertTrue(data.getEntries().get(0).getBaseLevel() == 250F, "beta 250");

		// Alpha is a type nothing here uses: an item with an alpha figure would have to register it
		helper.assertTrue(Alpha.INSTANCE != null, "alpha exists");
		helper.succeed();
	}

	/** Upstream gave the Kysmium billet cryogenic 5, and the base mod has no cold type to carry it. */
	@GameTest(template = EMPTY)
	public static void theOneUnwiredHazardIsNamed(GameTestHelper helper) {

		for(String name : NAMES) {
			if(!(item(name) instanceof AddonItemHazardBase hazard)) continue;
			List<String> unwired = hazard.unwired();
			if(name.equals("billet_kys"))
				helper.assertTrue(unwired.equals(List.of("cryogenic")), "kys billet is missing " + unwired);
			else
				helper.assertTrue(unwired.isEmpty(), name + " asks for a hazard nothing carries: " + unwired);
		}
		helper.assertTrue(((AddonItemHazardBase) item("billet_kys")).cryogenic == 5, "cryogenic 5, upstream's");
		helper.succeed();
	}

	/** Registering twice replaces rather than adds: the setup queue and a test both call it. */
	@GameTest(template = EMPTY)
	public static void registeringTwiceDoesNotDoubleAnything(GameTestHelper helper) {

		Item nuclium = item("ingot_nc279");
		int before = HazardSystem.itemMap.get(nuclium).getEntries().size();
		LeafiaHazards.registerItemHazards();
		LeafiaHazards.registerItemHazards();
		helper.assertTrue(HazardSystem.itemMap.get(nuclium).getEntries().size() == before, "entries multiplied");
		helper.assertTrue(HazardSystem.itemMap.get(item("dfcsh_core")).getEntries().size() == 1, "the shrapnel's entries multiplied");
		helper.succeed();
	}

	/** A fissium ingot in a pocket: gamma 10 x 0.05 and neutrons 30 x 1.3 per tick, times the stack. */
	@GameTest(template = EMPTY)
	public static void carryingFissiumIrradiates(GameTestHelper helper) {

		Player one = survivor(helper);
		carryForATick(one, new ItemStack(item("ingot_fissium")));
		near(helper, HbmLivingProps.getRadiation(one), 7.9, "(10 x 0.05 + 30 x 1.3) / 5");

		Player three = survivor(helper);
		carryForATick(three, new ItemStack(item("ingot_fissium"), 3));
		near(helper, HbmLivingProps.getRadiation(three), 23.7, "a stack of three is three times that");

		// fissite is a tenth: gamma 1, neutrons 3
		Player fissite = survivor(helper);
		carryForATick(fissite, new ItemStack(item("ingot_fissite")));
		near(helper, HbmLivingProps.getRadiation(fissite), 0.79, "(1 x 0.05 + 3 x 1.3) / 5");

		// a mystic anti-mass cell is inert, and so is a mysticite ingot
		Player inert = survivor(helper);
		carryForATick(inert, new ItemStack(item("cells_mysticite")));
		near(helper, HbmLivingProps.getRadiation(inert), 0, "mysticite cells");
		helper.succeed();
	}

	/** Digammitite powder is digamma 0.1, divided by the base mod's twenty; the tiny one a tenth. */
	@GameTest(template = EMPTY)
	public static void carryingDigammititeIsDigamma(GameTestHelper helper) {

		Player powder = survivor(helper);
		carryForATick(powder, new ItemStack(item("powder_digammitite")));
		near(helper, HbmLivingProps.getDigamma(powder), 0.005, "0.1 / 20");
		near(helper, HbmLivingProps.getRadiation(powder), 0, "and no radiation");

		Player tiny = survivor(helper);
		carryForATick(tiny, new ItemStack(item("powder_digammitite_tiny")));
		near(helper, HbmLivingProps.getDigamma(tiny), 0.0005, "0.01 / 20");

		// the DFC's front shrapnel
		Player front = survivor(helper);
		carryForATick(front, new ItemStack(item("dfcsh_front")));
		near(helper, HbmLivingProps.getDigamma(front), 0.0002, "0.004 / 20");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the container item

	/** The fissium RTG pellet hands an iron plate back; nothing else in the batch hands anything back. */
	@GameTest(template = EMPTY)
	public static void theFissiumPelletLeavesAnIronPlate(GameTestHelper helper) {

		ItemStack pellet = new ItemStack(item("pellet_rtg_fissium"));
		helper.assertTrue(pellet.hasCraftingRemainingItem(), "the pellet has a remainder");
		ItemStack left = pellet.getCraftingRemainingItem();
		helper.assertTrue(left.is(ModItems.plate_iron.get()) && left.getCount() == 1, "the remainder is " + left);

		for(String name : NAMES) {
			if(name.equals("pellet_rtg_fissium")) continue;
			helper.assertTrue(!new ItemStack(item(name)).hasCraftingRemainingItem(), name + " leaves something behind");
		}
		helper.succeed();
	}
}
