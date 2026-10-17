package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemPWRFuel.EnumPWRFuel;
import com.hbm.lib.RefStrings;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodCrafting;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodCraftingJEI;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodDecayJEI;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterJEI;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes.Input;
import com.leafia.jei.AddonJeiPages;
import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.jei.AddonJeiPages.Page;
import com.leafia.jei.AddonJeiPages.Role;
import com.leafia.jei.AddonJeiPages.Slot;
import com.leafia.jei.AddonJeiPages.Text;
import com.leafia.jei.AddonJeiSubtypes;
import com.leafia.jei._JEIBlacklist;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's JEI plugin (D-3032), pinned without JEI: what it would register - the three page kinds, their
 * pages and catalysts, the hide list and the bottle's subtypes - is plain data in classes that name no JEI
 * type, so it is read here on a server that never loads one. The last test is the guarantee that makes that
 * true of the whole jar: it reads every compiled class and finds a JEI reference in exactly two.
 *
 * <p>Whether JEI <em>draws</em> any of it is a client question and was looked at in a running client; these
 * tests cannot see it.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaJeiTests {

	private static final String EMPTY = "empty";

	private static Item leafia(String name) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, name));
	}

	private static ItemStack firstOf(Page page, Role role, int index) {
		return page.of(role).get(index).stacks().get(0);
	}

	/** Upstream's {@code registerCategories} adds the rod uncrafting, rod decay and Sol Blaster pages, in that order. */
	@GameTest(template = EMPTY)
	public static void theThreePageKindsAreUpstreamsInUpstreamsOrder(GameTestHelper helper) {
		List<Kind> kinds = AddonJeiPages.kinds();
		helper.assertTrue(kinds.size() == 3, "three kinds, not " + kinds.size());
		String[] paths = { "generic_rod_uncrafting", "generic_rod_decay", "sol_blaster" };
		// upstream's (u, v, width, height): setRecipe's backgrounds
		int[][] cuts = { { 0, 0, 96, 56 }, { 33, 33, 109, 19 }, { 33, 33, 109, 19 } };
		String[] textures = { "generic_fuel_uncrafting", "gui_nei_fusion", "blaster" };
		for(int i = 0; i < 3; i++) {
			Kind kind = kinds.get(i);
			helper.assertTrue(kind.path().equals(paths[i]), "kind " + i + " is " + kind.path());
			helper.assertTrue(kind.u() == cuts[i][0] && kind.v() == cuts[i][1] && kind.width() == cuts[i][2] && kind.height() == cuts[i][3],
					kind.path() + " cuts " + kind.u() + "," + kind.v() + " " + kind.width() + "x" + kind.height());
			helper.assertTrue(kind.texture().equals(LeafiaRef.rl("textures/gui/jei/" + textures[i] + ".png")), kind.path() + " texture " + kind.texture());
			helper.assertTrue(kind.enabled().getAsBoolean(), kind.path() + " is not enabled with its feature on");
			helper.assertTrue(!kind.pages().get().isEmpty(), kind.path() + " lists no recipe");
			helper.assertTrue(!kind.catalysts().get().isEmpty(), kind.path() + " has no catalyst");
			helper.assertTrue(!kind.title().get().getString().isEmpty(), kind.path() + " has no title");
		}
		helper.assertTrue(AddonJeiPages.enabled().size() == 3, "all three exist with their features on");
		helper.succeed();
	}

	/** The textures are shipped, are 256 square as the cuts assume, and every cut lies inside one. */
	@GameTest(template = EMPTY)
	public static void everyPagesBackgroundIsAShippedTextureThatContainsItsCut(GameTestHelper helper) {
		for(Kind kind : AddonJeiPages.kinds()) {
			String path = "/assets/" + kind.texture().getNamespace() + "/" + kind.texture().getPath();
			try(InputStream in = LeafiaJeiTests.class.getResourceAsStream(path)) {
				helper.assertTrue(in != null, path + " is not shipped");
				var image = ImageIO.read(in);
				helper.assertTrue(image.getWidth() == 256 && image.getHeight() == 256, path + " is " + image.getWidth() + "x" + image.getHeight());
				helper.assertTrue(kind.u() + kind.width() <= 256 && kind.v() + kind.height() <= 256, kind.path() + " cut leaves the texture");
			} catch(IOException e) {
				helper.fail(path + ": " + e);
			}
			// and every slot of every page sits inside the cut it is drawn on
			for(Page page : kind.pages().get())
				for(Slot slot : page.slots())
					helper.assertTrue(slot.x() >= 0 && slot.y() >= 0 && slot.x() + 16 <= kind.width() && slot.y() + 16 <= kind.height(),
							kind.path() + " has a slot at " + slot.x() + "," + slot.y() + " outside " + kind.width() + "x" + kind.height());
		}
		helper.succeed();
	}

	/** One uncrafting page per rod with a base item: the rod in, the base item and the empty rod out, at upstream's slots. */
	@GameTest(template = EMPTY)
	public static void theUncraftingPagesAreEveryRodWithABaseItem(GameTestHelper helper) {
		List<Page> pages = LeafiaRodCraftingJEI.pages();

		Set<Item> expected = new HashSet<>();
		for(LeafiaRodItem rod : LeafiaRodItem.fromResourceMap.values())
			if(rod.baseItem != null && !rod.baseStack().isEmpty()) expected.add(rod);
		helper.assertTrue(pages.size() == expected.size() && pages.size() >= 50, pages.size() + " pages for " + expected.size() + " rods with a base item");
		// a fresh list each time: upstream's static list doubled on every JEI reload
		helper.assertTrue(LeafiaRodCraftingJEI.pages().size() == pages.size(), "a second call lists " + LeafiaRodCraftingJEI.pages().size());

		Set<Item> seen = new HashSet<>();
		for(Page page : pages) {
			helper.assertTrue(page.slots().size() == 3, "three slots, not " + page.slots().size());
			Slot in = page.of(Role.INPUT).get(0);
			Slot base = page.of(Role.OUTPUT).get(0);
			Slot empty = page.of(Role.OUTPUT).get(1);
			helper.assertTrue(in.x() == 2 && in.y() == 2 && base.x() == 58 && base.y() == 12 && empty.x() == 76 && empty.y() == 12, "slots are not upstream's");

			Item rod = in.stacks().get(0).getItem();
			helper.assertTrue(rod instanceof LeafiaRodItem && seen.add(rod), "a page for " + rod + " twice, or for a non-rod");
			helper.assertTrue(empty.stacks().get(0).is(LeafiaRods.leafRod.get()), "the second output is not the empty rod");

			// the page shows what the recipe gives
			ItemStack made = new LeafiaRodCrafting(CraftingBookCategory.MISC).assemble(
					CraftingInput.of(1, 1, List.of(in.stacks().get(0).copy())), helper.getLevel().registryAccess());
			helper.assertTrue(ItemStack.isSameItemSameComponents(made, base.stacks().get(0)), rod + " uncrafts to " + made + ", the page says " + base.stacks().get(0));
		}
		helper.assertTrue(seen.equals(expected), "the pages are not the rods with a base item");
		helper.succeed();
	}

	/** The warning is two lines, centred on 48 from y = 39, nine apart, in 0x404040 (upstream's drawInfo). */
	@GameTest(template = EMPTY)
	public static void theUncraftingWarningIsDrawnWhereUpstreamDrawsIt(GameTestHelper helper) {
		List<Text> info = LeafiaRodCraftingJEI.info();
		String message = Component.translatable("info.leafiarod.jei.message").getString();
		helper.assertTrue(info.size() == message.split("\\$").length && info.size() == 2, info.size() + " lines in \"" + message + "\"");
		for(int i = 0; i < info.size(); i++) {
			Text t = info.get(i);
			helper.assertTrue(t.centerX() == 48 && t.y() == 39 + 9 * i && t.color() == 0x404040, "line " + i + " at " + t.centerX() + "," + t.y() + " in " + Integer.toHexString(t.color()));
		}
		helper.assertTrue(String.join("$", info.stream().map(Text::text).toList()).equals(message), "the lines are not the message");
		for(Page page : LeafiaRodCraftingJEI.pages())
			helper.assertTrue(page.texts().equals(info), "a page without the warning");
		helper.succeed();
	}

	/** One decay page per rod with a product; the product is what the rod really decays into. */
	@GameTest(template = EMPTY)
	public static void theDecayPagesAreEveryRodWithAProduct(GameTestHelper helper) {
		List<Page> pages = LeafiaRodDecayJEI.pages();

		int expected = 0;
		for(LeafiaRodItem rod : LeafiaRodItem.fromResourceMap.values()) if(rod.newFuel() != null) expected++;
		helper.assertTrue(pages.size() == expected && pages.size() == 20, pages.size() + " pages for " + expected + " rods with a product (upstream sets 20 decay products)");

		for(Page page : pages) {
			helper.assertTrue(page.slots().size() == 2, "two slots, not " + page.slots().size());
			Slot in = page.of(Role.INPUT).get(0);
			Slot out = page.of(Role.OUTPUT).get(0);
			helper.assertTrue(in.x() == 1 && in.y() == 1 && out.x() == 91 && out.y() == 1, "slots are not upstream's");

			LeafiaRodItem rod = (LeafiaRodItem) in.stacks().get(0).getItem();
			Item product = out.stacks().get(0).getItem();
			helper.assertTrue(product != rod, rod.label + " decays into itself");

			ItemStack spent = new ItemStack(rod);
			CompoundTag data = new CompoundTag();
			data.putDouble("depletion", rod.life);
			LeafiaRodItem.putData(spent, data);
			ItemStack decayed = rod.getDecayProduct(spent);
			helper.assertTrue(decayed != null && decayed.getItem() == product, rod.label + " spent becomes " + (decayed == null ? "nothing" : decayed.getItem()) + ", the page says " + product);
		}
		helper.succeed();
	}

	/** Upstream's catalysts, in upstream's order. */
	@GameTest(template = EMPTY)
	public static void theCatalystsAreUpstreamsInUpstreamsOrder(GameTestHelper helper) {
		helper.assertTrue(LeafiaRodCraftingJEI.catalysts().size() == 1 && LeafiaRodCraftingJEI.catalysts().get(0).is(Items.CRAFTING_TABLE), "uncrafting is made at a crafting table");

		List<String> decay = LeafiaRodDecayJEI.catalysts().stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList();
		List<String> want = new ArrayList<>();
		for(String name : new String[] { "lwr_element", "reactor_element", "reactor_element_blank" })
			if(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(name))) want.add(LeafiaRef.MODID + ":" + name);
		want.add(RefStrings.MODID + ":machine_zirnox");
		want.add(RefStrings.MODID + ":machine_reactor_small");
		want.add(RefStrings.MODID + ":machine_difurnace_rtg_off");
		if(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("heater_rt"))) want.add(LeafiaRef.MODID + ":heater_rt");
		helper.assertTrue(decay.equals(want) && decay.size() >= 6, "decay catalysts " + decay + ", wanted " + want);

		List<ItemStack> blaster = SolBlasterJEI.catalysts();
		helper.assertTrue(blaster.size() == 1 && blaster.get(0).is(leafia("sol_blaster")), "the Sol blaster is its own catalyst");
		helper.assertTrue(SolBlasterJEI.KIND.title().get().getString().equals(blaster.get(0).getHoverName().getString()), "its title is its name");
		helper.succeed();
	}

	/** One Sol Blaster page per recipe, and none for a fallback; a tag row lists every item of the tag. */
	@GameTest(template = EMPTY)
	public static void theBlasterPagesAreTheRecipesAndNotTheFallbacks(GameTestHelper helper) {
		List<Page> pages = SolBlasterJEI.pages();
		var recipes = SolBlasterRecipes.recipes();
		helper.assertTrue(pages.size() == recipes.size() && pages.size() >= 8, pages.size() + " pages for " + recipes.size() + " recipes");

		int i = 0;
		boolean tagRow = false;
		for(var entry : recipes.entrySet()) {
			Page page = pages.get(i++);
			Slot in = page.of(Role.INPUT).get(0);
			Slot out = page.of(Role.OUTPUT).get(0);
			helper.assertTrue(in.x() == 1 && in.y() == 1 && out.x() == 91 && out.y() == 1, "slots are not upstream's");
			helper.assertTrue(out.stacks().size() == 1 && out.stacks().get(0).is(entry.getValue()), "page " + (i - 1) + " does not make " + entry.getValue());
			Input input = entry.getKey();
			if(input.isTag()) {
				tagRow = true;
				helper.assertTrue(!in.stacks().isEmpty(), "the tag " + input + " lists no item");
				for(ItemStack stack : in.stacks()) helper.assertTrue(stack.is(input.tag()), stack + " is not in " + input);
			} else {
				helper.assertTrue(in.stacks().size() == 1 && in.stacks().get(0).is(input.item()), "page " + (i - 1) + " does not take " + input);
			}
		}
		helper.assertTrue(tagRow, "no tag row, so no expansion was checked");

		// a fallback (the balefire egg is deleted by the blaster) is not a recipe, so has no page
		for(Page page : pages)
			for(ItemStack stack : page.of(Role.INPUT).get(0).stacks())
				helper.assertTrue(!stack.is(com.hbm.items.ModItems.egg_balefire.get()), "a page for the balefire egg, a fallback");
		helper.succeed();
	}

	/** The hide list: the joke and test items in upstream's order, and the base PWR only while the overhaul is on. */
	@GameTest(template = EMPTY)
	public static void theHideListIsUpstreamsTailAndTheBasePWRBehindItsOverhaul(GameTestHelper helper) {
		boolean was = LeafiaFeatures.pwr;
		try {
			LeafiaFeatures.pwr = false;
			List<ItemStack> off = _JEIBlacklist.hidden();

			List<Item> tail = new ArrayList<>();
			for(String name : new String[] { "billet_kys", "leafia_rod_kys3000", "elevator_light", "dna_felid", "dna_canid", "dna_eevee",
					"leafia_rod_catsmilium", "leafia_rod_confusium", "leafia_rod_shitfuckium", "cells_mysticite", "cells_mysticite_filled",
					"part_receiver_light_mysticite" }) {
				Item item = leafia(name);
				if(item != Items.AIR) tail.add(item);
			}
			helper.assertTrue(tail.size() >= 11, "only " + tail.size() + " of the twelve hidden items exist");
			List<Item> got = off.stream().map(ItemStack::getItem).toList();
			helper.assertTrue(got.equals(tail), "hidden with the overhaul off: " + got + ", wanted " + tail);

			LeafiaFeatures.pwr = true;
			List<ItemStack> on = _JEIBlacklist.hidden();
			int grades = EnumPWRFuel.values().length;
			// eleven blocks upstream; pwr_block has no item here (regNoItem), so ten stacks, and a stack is all JEI can hide
			helper.assertTrue(ModBlocks.pwr_block.get().asItem() == Items.AIR, "pwr_block has an item now, so the count below is one short");
			int base = 3 * grades + 10;
			helper.assertTrue(on.size() == off.size() + base, "hidden with it on: " + on.size() + ", wanted " + (off.size() + base));
			// the base PWR comes first, then upstream's tail unchanged
			helper.assertTrue(on.subList(on.size() - off.size(), on.size()).stream().map(ItemStack::getItem).toList().equals(tail), "the tail moved");
			Set<Item> head = new HashSet<>(on.subList(0, on.size() - off.size()).stream().map(ItemStack::getItem).toList());
			helper.assertTrue(head.size() == base, "the base PWR list has repeats: " + head.size());
			helper.assertTrue(head.contains(ModBlocks.pwr_casing.get().asItem()) && head.contains(ModBlocks.pwr_fuel.get().asItem())
					&& head.contains(com.hbm.items.machine.ItemPWRFuel.forGrade(EnumPWRFuel.MEU)), "the base PWR list lacks a block or a rod");
			for(ItemStack stack : on) helper.assertTrue(!stack.isEmpty(), "an empty stack is hidden");
		} finally {
			LeafiaFeatures.pwr = was;
		}
		helper.succeed();
	}

	/** Every fluid is its own bottle to JEI, and a bottle of nothing is none. */
	@GameTest(template = EMPTY)
	public static void eachFluidIsItsOwnBottleSubtype(GameTestHelper helper) {
		if(!LeafiaFeatures.fluidBottles || LeafiaItems.ntmfbottle == null) { helper.succeed(); return; }

		Item bottle = LeafiaItems.ntmfbottle.get();
		Set<Integer> keys = new HashSet<>();
		List<ItemStack> all = ItemNTMFBottle.allBottles(bottle);
		for(ItemStack stack : all) {
			Integer key = AddonJeiSubtypes.key(stack);
			FluidType fluid = ItemNTMFBottle.getFluidFromStack(stack);
			helper.assertTrue(key != null && key == fluid.getID(), fluid.getName() + " keys as " + key);
			helper.assertTrue(keys.add(key), "two bottles share the key " + key);
			helper.assertTrue(AddonJeiSubtypes.key(stack.copy()).equals(key), "a copy keys differently");
		}
		helper.assertTrue(keys.size() == all.size() && all.size() > 30, keys.size() + " keys for " + all.size() + " bottles");
		helper.assertTrue(AddonJeiSubtypes.key(ItemNTMFBottle.make(bottle, Fluids.NONE)) == null, "an empty bottle has a subtype");
		helper.assertTrue(AddonJeiSubtypes.key(new ItemStack(bottle)) == null, "a bottle with no data has a subtype");
		helper.assertTrue(AddonJeiSubtypes.key(new ItemStack(Items.STICK)) == null && !AddonJeiSubtypes.varies(Items.STICK), "a stick has a subtype");
		helper.succeed();
	}

	/**
	 * JEI stays optional: of every class compiled into the addon, only the plugin and its category name a JEI
	 * type. A third would fail to load on a game without JEI the moment anything touched it.
	 */
	@GameTest(template = EMPTY)
	public static void onlyThePluginAndItsCategoryNameAJeiType(GameTestHelper helper) {
		byte[] needle = "mezz".concat("/jei/").getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Set<String> offenders = new HashSet<>();
		int[] scanned = { 0 };

		try {
			Path location = Path.of(AddonJeiPages.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if(Files.isDirectory(location)) {
				try(Stream<Path> files = Files.walk(location)) {
					for(Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator)
						check(location.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file), needle, offenders, scanned);
				}
			} else {
				try(JarFile jar = new JarFile(location.toFile())) {
					for(JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
						if(!entry.getName().endsWith(".class")) continue;
						try(InputStream in = jar.getInputStream(entry)) {
							check(entry.getName(), in.readAllBytes(), needle, offenders, scanned);
						}
					}
				}
			}
		} catch(IOException | URISyntaxException | RuntimeException e) {
			helper.fail("could not read the addon's classes: " + e);
			return;
		}

		helper.assertTrue(scanned[0] > 1000, "only " + scanned[0] + " classes were read, so the scan proves nothing");
		helper.assertTrue(offenders.equals(Set.of("com/leafia/jei/AddonJeiPlugin", "com/leafia/jei/AddonJeiCategory")), "classes naming a JEI type: " + offenders);
		helper.succeed();
	}

	private static void check(String name, byte[] bytes, byte[] needle, Set<String> offenders, int[] scanned) {
		scanned[0]++;
		if(indexOf(bytes, needle) < 0) return;
		String top = name.substring(0, name.length() - ".class".length());
		int inner = top.indexOf('$');
		offenders.add(inner < 0 ? top : top.substring(0, inner));
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for(int i = 0; i + needle.length <= haystack.length; i++) {
			for(int j = 0; j < needle.length; j++) if(haystack[i + j] != needle[j]) continue outer;
			return i;
		}
		return -1;
	}
}
