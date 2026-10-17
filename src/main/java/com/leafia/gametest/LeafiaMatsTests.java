package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.inventory.material.NTMMaterial.MatTraits;
import com.hbm.inventory.material.NTMMaterial.SmeltingBehavior;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilConstructionRecipe;
import com.hbm.items.special.ItemAutogen;
import com.hbm.lib.NtmTags;
import com.leafia.config.LeafiaFeatures;
import com.leafia.init.LeafiaMats;
import com.leafia.init.LeafiaMats.AutogenItem;
import com.leafia.init.LeafiaOreDict;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's nine materials and the items that go with them (D-3026): {@code AddonMats}, and the autogen items and ore
 * names the base mod's loops need to find for them. Every number is worked from upstream's {@code AddonMats.java}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaMatsTests {

	private static final String EMPTY = "empty";

	private record Row(String field, NTMMaterial mat, int id, String name, SmeltingBehavior smelts, int light, int dark, int molten, Set<MaterialShapes> autogen, MatTraits trait) {}

	/** Upstream's nine {@code makeSmeltable/makeNonSmeltable(_LF+n, frame, light, dark, molten).setAutogen(...).m()/n()} lines, typed out again here. */
	private static List<Row> rows() {
		return List.of(
				new Row("MAT_XANAXIUM", LeafiaMats.MAT_XANAXIUM, 21121, "Xanaxium", SmeltingBehavior.SMELTABLE, 0xcff3ab, 0x8375a7, 0x97b7ac, Set.of(MaterialShapes.CASTPLATE, MaterialShapes.WELDEDPLATE), MatTraits.METAL),
				new Row("MAT_CORIUM", LeafiaMats.MAT_CORIUM, 21122, "Corium", SmeltingBehavior.SMELTABLE, 0x272f35, 0x161616, 0x1d2123, Set.of(), MatTraits.NONMETAL),
				new Row("MAT_TAINTIUM", LeafiaMats.MAT_TAINTIUM, 21123, "Taintium", SmeltingBehavior.SMELTABLE, 0x616dba, 0x463d49, 0x586294, Set.of(MaterialShapes.CASTPLATE, MaterialShapes.WELDEDPLATE), MatTraits.METAL),
				new Row("MAT_NC279", LeafiaMats.MAT_NC279, 21124, "Nc279", SmeltingBehavior.SMELTABLE, 0xbfbfc7, 0x514967, 0x72758d, Set.of(), MatTraits.METAL),
				new Row("MAT_MYSTICITE", LeafiaMats.MAT_MYSTICITE, 21125, "Mysticite", SmeltingBehavior.NOT_SMELTABLE, 0xfdfdfd, 0xbbbbbb, 0xfdfdfd, Set.of(MaterialShapes.LIGHTRECEIVER), MatTraits.METAL),
				new Row("MAT_FISSITE", LeafiaMats.MAT_FISSITE, 21126, "Fissite", SmeltingBehavior.SMELTABLE, 0xd9d4dc, 0x615468, 0xb5abbb, Set.of(MaterialShapes.LIGHTRECEIVER), MatTraits.METAL),
				new Row("MAT_FISSIUM", LeafiaMats.MAT_FISSIUM, 21127, "Fissium", SmeltingBehavior.SMELTABLE, 0xfbfbfc, 0x777a90, 0xd4d4dc, Set.of(), MatTraits.METAL),
				new Row("MAT_MANA", LeafiaMats.MAT_MANA, 21128, "Mana", SmeltingBehavior.SMELTABLE, 0xf58cff, 0xa039a9, 0xf58cff, Set.of(), MatTraits.NONMETAL),
				new Row("MAT_TNALLOY", LeafiaMats.MAT_TNALLOY, 21129, "TnAlloy", SmeltingBehavior.SMELTABLE, 0xbfc3d1, 0x5d4b83, 0xa6aec0, Set.of(MaterialShapes.CASTPLATE, MaterialShapes.WELDEDPLATE), MatTraits.METAL));
	}

	@GameTest(template = EMPTY)
	public static void theNineMaterialsAreUpstreams(GameTestHelper helper) {

		if(!LeafiaMats.created()) {
			helper.assertTrue(!LeafiaFeatures.resourceItems, "the materials do not exist although features.resourceItems is on");
			helper.assertTrue(LeafiaMats.MAT_XANAXIUM == null && Mats.matById.get(21121) == null && LeafiaMats.ITEMS.isEmpty(), "and with it off there is nothing of them anywhere");
			helper.succeed();
			return;
		}

		for(Row r : rows()) {
			helper.assertTrue(r.mat() != null, r.field() + " exists");
			helper.assertTrue(r.mat().id == r.id() && Mats.matById.get(r.id()) == r.mat(), r.field() + " is id " + r.id() + ": " + r.mat().id);
			helper.assertTrue(r.mat().names.length == 1 && r.mat().names[0].equals(r.name()) && Mats.matByName.get(r.name()) == r.mat(), r.field() + " answers to " + r.name());
			helper.assertTrue(r.mat().smeltable == r.smelts(), r.field() + " is " + r.smelts() + ": " + r.mat().smeltable);
			helper.assertTrue(r.mat().solidColorLight == r.light() && r.mat().solidColorDark == r.dark() && r.mat().moltenColor == r.molten(),
					r.field() + " colours: " + Integer.toHexString(r.mat().solidColorLight) + " " + Integer.toHexString(r.mat().solidColorDark) + " " + Integer.toHexString(r.mat().moltenColor));
			helper.assertTrue(r.mat().autogen.equals(r.autogen()), r.field() + " autogens " + r.mat().autogen.size() + " shapes, expected " + r.autogen().size());
			helper.assertTrue(r.mat().traits.equals(Set.of(r.trait())), r.field() + " is " + r.trait() + ": " + r.mat().traits);
			helper.assertTrue(r.mat().smeltsInto == r.mat() && r.mat().convIn == 1 && r.mat().convOut == 1, r.field() + " smelts into itself");
			helper.assertTrue(Mats.orderedList.contains(r.mat()), r.field() + " is in Mats.orderedList, which the base mod's loops walk");
		}
		// they come after every material the base mod has: appended, so no base index moved
		helper.assertTrue(Mats.orderedList.indexOf(LeafiaMats.MAT_XANAXIUM) > Mats.orderedList.indexOf(Mats.MAT_RUBBER), "after the base mod's last material");
		// upstream's `MAT_BSCCO.setAutogen(WIRE)`
		helper.assertTrue(Mats.MAT_BSCCO.autogen.contains(MaterialShapes.WIRE) && Mats.MAT_BSCCO.autogen.contains(MaterialShapes.DENSEWIRE), "BSCCO has fine wire beside its dense wire");
		helper.succeed();
	}

	/**
	 * The invariant the base mod's {@code OreDictManager.registerOres} throws on: every shape a material in {@code Mats.orderedList} autogens
	 * has an {@code ItemAutogen} of that shape and material in the item registry. Checked for every material, the base's included, so a
	 * material the addon added without its item fails here and not at the next boot.
	 */
	@GameTest(template = EMPTY)
	public static void everyAutogenShapeOfEveryMaterialHasItsItem(GameTestHelper helper) {

		var byPair = new java.util.HashSet<String>();
		int addonItems = 0;
		for(Item item : BuiltInRegistries.ITEM) {
			if(item instanceof ItemAutogen a) {
				byPair.add(a.shape.name() + "/" + a.material.id);
				if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(LeafiaRef.MODID)) addonItems++;
			}
		}
		MaterialShapes[] covered = {MaterialShapes.CASTPLATE, MaterialShapes.WELDEDPLATE, MaterialShapes.DENSEWIRE, MaterialShapes.BOLT, MaterialShapes.INGOT, MaterialShapes.SHELL,
				MaterialShapes.PIPE, MaterialShapes.FRAGMENT, MaterialShapes.WIRE, MaterialShapes.LIGHTBARREL, MaterialShapes.HEAVYBARREL, MaterialShapes.LIGHTRECEIVER,
				MaterialShapes.HEAVYRECEIVER, MaterialShapes.MECHANISM, MaterialShapes.STOCK, MaterialShapes.GRIP};
		List<String> missing = new ArrayList<>();
		for(NTMMaterial mat : Mats.orderedList)
			for(MaterialShapes shape : covered)
				if(mat.autogen.contains(shape) && !byPair.contains(shape.name() + "/" + mat.id)) missing.add(shape.name() + " of " + mat.names[0]);
		// the INGOT shape of a base material is registered as an ingot, not an ItemAutogen: only the shapes with an item class are asked for
		missing.removeIf(m -> m.startsWith(MaterialShapes.INGOT.name() + " of "));
		helper.assertTrue(missing.isEmpty(), "materials autogen shapes that no ItemAutogen exists for: " + missing);
		helper.assertTrue(addonItems == (LeafiaMats.created() ? 9 : 0), "the addon registers nine autogen items: " + addonItems);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theAutogenItemsAreThePairsUpstreamsMaterialsNamed(GameTestHelper helper) {

		if(!LeafiaMats.created()) { helper.succeed(); return; }
		String[][] expected = {
				{"plate_cast_xanaxium", "plate_cast", "CASTPLATE", "MAT_XANAXIUM"}, {"plate_welded_xanaxium", "plate_welded", "WELDEDPLATE", "MAT_XANAXIUM"},
				{"plate_cast_taintium", "plate_cast", "CASTPLATE", "MAT_TAINTIUM"}, {"plate_welded_taintium", "plate_welded", "WELDEDPLATE", "MAT_TAINTIUM"},
				{"part_receiver_light_mysticite", "part_receiver_light", "LIGHTRECEIVER", "MAT_MYSTICITE"}, {"part_receiver_light_fissite", "part_receiver_light", "LIGHTRECEIVER", "MAT_FISSITE"},
				{"plate_cast_tnalloy", "plate_cast", "CASTPLATE", "MAT_TNALLOY"}, {"plate_welded_tnalloy", "plate_welded", "WELDEDPLATE", "MAT_TNALLOY"},
				{"wire_fine_bscco", "wire_fine", "WIRE", "MAT_BSCCO"}};
		helper.assertTrue(LeafiaMats.AUTOGEN.size() == expected.length && LeafiaMats.ITEMS.size() == expected.length, "nine items");
		for(int i = 0; i < expected.length; i++) {
			String[] e = expected[i];
			AutogenItem a = LeafiaMats.AUTOGEN.get(i);
			helper.assertTrue(a.name().equals(e[0]) && a.baseKey().equals(e[1]) && a.shape() == shape(e[2]), "row " + i + " is " + e[0] + ": " + a.name());
			Item item = LeafiaMats.item(e[0]);
			helper.assertTrue(item instanceof ItemAutogen, e[0] + " is an ItemAutogen");
			ItemAutogen ia = (ItemAutogen) item;
			NTMMaterial mat = e[3].equals("MAT_BSCCO") ? Mats.MAT_BSCCO : rows().stream().filter(r -> r.field().equals(e[3])).findFirst().get().mat();
			helper.assertTrue(ia.material == mat && ia.shape == shape(e[2]), e[0] + " is the " + e[2] + " of " + e[3]);
			helper.assertTrue(ia.getDescriptionId().equals("item.hbm." + e[1]), e[0] + " is named by the base's key " + ia.getDescriptionId());
			helper.assertTrue(ia.getName(new ItemStack(ia)).getString().length() > 0, e[0] + " has a name");
		}
		helper.succeed();
	}

	private static JsonObject lang() {
		try(InputStream in = LeafiaMatsTests.class.getResourceAsStream("/assets/leafia/lang/en_us.json")) {
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** `NTMMaterial.getUnlocalizedName` is `hbmmat.` plus the lower-cased first name; upstream's lang file names the nine. */
	@GameTest(template = EMPTY)
	public static void theMaterialsHaveUpstreamsEnglishNames(GameTestHelper helper) {

		if(!LeafiaMats.created()) { helper.succeed(); return; }
		String[][] names = {{"MAT_XANAXIUM", "hbmmat.xanaxium", "Xanaxium"}, {"MAT_CORIUM", "hbmmat.corium", "Corium"}, {"MAT_TAINTIUM", "hbmmat.taintium", "Taintium"},
				{"MAT_NC279", "hbmmat.nc279", "Nuclium"}, {"MAT_MYSTICITE", "hbmmat.mysticite", "Mysticite"}, {"MAT_FISSITE", "hbmmat.fissite", "Fissite"},
				{"MAT_FISSIUM", "hbmmat.fissium", "Fissium"}, {"MAT_MANA", "hbmmat.mana", "Liquid Mana"}, {"MAT_TNALLOY", "hbmmat.tnalloy", "Taintium Steel"}};
		JsonObject lang = lang();
		for(String[] n : names) {
			NTMMaterial mat = rows().stream().filter(r -> r.field().equals(n[0])).findFirst().get().mat();
			helper.assertTrue(mat.getUnlocalizedName().equals(n[1]), n[0] + " is " + mat.getUnlocalizedName());
			helper.assertTrue(lang.has(n[1]) && lang.get(n[1]).getAsString().equals(n[2]), n[1] + " reads " + (lang.has(n[1]) ? lang.get(n[1]).getAsString() : null) + ", expected " + n[2]);
		}
		helper.succeed();
	}

	private static MaterialShapes shape(String name) {
		return switch(name) {
			case "CASTPLATE" -> MaterialShapes.CASTPLATE;
			case "WELDEDPLATE" -> MaterialShapes.WELDEDPLATE;
			case "LIGHTRECEIVER" -> MaterialShapes.LIGHTRECEIVER;
			case "WIRE" -> MaterialShapes.WIRE;
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static boolean in(Item item, String oreName) {
		return new ItemStack(item).is(NtmTags.ore(oreName));
	}

	private static Item leafia(String name) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name)); }

	/**
	 * `AddonOreDict`: the autogen items are in the tag of the name the base's loops bind them under ({@code shape.name() + material name}),
	 * the two groups hold what upstream's {@code registerGroups} and {@code MixinOreDictManager} put in them, and the registrations that
	 * name base blocks and the addon's ores are tags too.
	 */
	@GameTest(template = EMPTY)
	public static void theOreNamesHoldTheAddonsItems(GameTestHelper helper) {

		if(!LeafiaMats.created()) { helper.succeed(); return; }
		// shape.name() + the first name of the material
		helper.assertTrue(in(LeafiaMats.item("plate_cast_xanaxium"), LeafiaOreDict.XN.plateCast()) && LeafiaOreDict.XN.plateCast().equals("plateTripleXanaxium"), "cast xanaxium: " + LeafiaOreDict.XN.plateCast());
		helper.assertTrue(in(LeafiaMats.item("plate_welded_xanaxium"), LeafiaOreDict.XN.plateWelded()) && LeafiaOreDict.XN.plateWelded().equals("plateSextupleXanaxium"), "welded xanaxium");
		helper.assertTrue(in(LeafiaMats.item("plate_cast_taintium"), LeafiaOreDict.TN.plateCast()) && in(LeafiaMats.item("plate_welded_taintium"), LeafiaOreDict.TN.plateWelded()), "taintium plates");
		helper.assertTrue(in(LeafiaMats.item("plate_cast_tnalloy"), LeafiaOreDict.TNALLOY.plateCast()) && in(LeafiaMats.item("plate_welded_tnalloy"), LeafiaOreDict.TNALLOY.plateWelded()), "taintium steel plates");
		helper.assertTrue(in(LeafiaMats.item("part_receiver_light_mysticite"), LeafiaOreDict.MYSTICITE.lightReceiver()) && LeafiaOreDict.MYSTICITE.lightReceiver().equals("receiverLightMysticite"), "mysticite receiver");
		helper.assertTrue(in(LeafiaMats.item("part_receiver_light_fissite"), LeafiaOreDict.FSALLOY.lightReceiver()), "fissite receiver");
		helper.assertTrue(in(LeafiaMats.item("wire_fine_bscco"), OreDictManager.BSCCO.wireFine()), "BSCCO's fine wire, which the antimatter pipe takes");

		// the groups: MixinOreDictManager gives ANY_RESISTANTALLOY the taintium steel, registerGroups() gives ANY_ULTRAALLOY mysticite on three shapes
		helper.assertTrue(in(leafia("ingot_tnalloy"), OreDictManager.ANY_RESISTANTALLOY.ingot()), "the taintium steel ingot is a resistant alloy");
		helper.assertTrue(in(leafia("plate_welded_tnalloy"), OreDictManager.ANY_RESISTANTALLOY.plateWelded()) && in(leafia("plate_cast_tnalloy"), OreDictManager.ANY_RESISTANTALLOY.plateCast()), "and so are its plates");
		helper.assertTrue(!in(leafia("plate_welded_xanaxium"), OreDictManager.ANY_RESISTANTALLOY.plateWelded()), "xanaxium is not");
		helper.assertTrue(in(leafia("ingot_mysticite"), LeafiaOreDict.ANY_ULTRAALLOY.ingot()) && LeafiaOreDict.ANY_ULTRAALLOY.ingot().equals("ingotAnyUltraAlloy"), "mysticite is an ultra alloy");
		helper.assertTrue(in(leafia("plate_mysticite"), LeafiaOreDict.ANY_ULTRAALLOY.plate()) && in(leafia("part_receiver_light_mysticite"), LeafiaOreDict.ANY_ULTRAALLOY.lightReceiver()), "as is its plate and its receiver");
		helper.assertTrue(!in(leafia("ingot_fissite"), LeafiaOreDict.ANY_ULTRAALLOY.ingot()), "fissite is not");

		// the lines of registerOres() that are not a Resources item
		helper.assertTrue(in(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "block_corium")), LeafiaOreDict.CORIUM.block())
				&& in(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "block_corium_cobble")), LeafiaOreDict.CORIUM.block()), "both blocks of corium are blockCorium");
		helper.assertTrue(in(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", "powder_magic")), LeafiaOreDict.MANA.dust()), "mana dust is the base's magic powder");
		if(leafia("ore_corium_chernobylite") != net.minecraft.world.item.Items.AIR) {
			helper.assertTrue(in(leafia("ore_corium_chernobylite"), LeafiaOreDict.CHERNOBYL.ore()), "chernobylite ore");
			helper.assertTrue(in(leafia("ore_corium_zetalite"), LeafiaOreDict.ZETA.ore()), "zetalite ore");
		}
		helper.succeed();
	}

	/**
	 * The ordering claim the mod constructor makes: the addon's materials exist before the base mod's loops walk {@code Mats.orderedList}.
	 * The anvil's fine-wire loop (every material with the WIRE shape and an ingot name) is one of those loops, and BSCCO only has the shape
	 * because the addon gave it; the arc furnace's autogen loop is the other.
	 */
	@GameTest(template = EMPTY)
	public static void theBaseModsLoopsSawTheAddonsMaterials(GameTestHelper helper) {

		if(!LeafiaMats.created()) { helper.succeed(); return; }
		Item wire = LeafiaMats.item("wire_fine_bscco");
		int found = 0;
		for(AnvilConstructionRecipe r : AnvilRecipes.constructionRecipes) {
			if(r.output.size() == 1 && r.output.get(0).stack.getItem() == wire) {
				found++;
				helper.assertTrue(r.output.get(0).stack.getCount() == 8 && r.tierLower == 4 && r.input.size() == 1 && r.input.get(0) instanceof com.hbm.inventory.RecipesCommon.OreDictStack o
						&& o.name.equals(OreDictManager.BSCCO.ingot()), "one BSCCO ingot at tier 4 draws eight fine wires");
			}
		}
		helper.assertTrue(found == 1, found + " anvil recipes draw BSCCO fine wire, expected the base's loop to have written one");

		// and the arc furnace melts a cast xanaxium plate: three ingots' worth (216 quanta) of xanaxium
		var melted = ArcFurnaceRecipes.getOutput(new ItemStack(LeafiaMats.item("plate_cast_xanaxium")), true);
		helper.assertTrue(melted != null && melted.fluidOutput.length == 1 && melted.fluidOutput[0].material == LeafiaMats.MAT_XANAXIUM
				&& melted.fluidOutput[0].amount == MaterialShapes.CASTPLATE.q(1) && MaterialShapes.CASTPLATE.q(1) == 216,
				"a cast xanaxium plate melts into 216 quanta of xanaxium: " + (melted == null ? null : melted.fluidOutput[0].amount));
		helper.succeed();
	}
}
