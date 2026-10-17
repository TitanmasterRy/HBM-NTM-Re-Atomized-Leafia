package com.leafia.contents.control.fuel.nuclearfuel;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.jei.AddonJeiPages.Page;
import com.leafia.jei.AddonJeiPages.Role;
import com.leafia.jei.AddonJeiPages.Slot;
import com.leafia.jei.AddonJeiPages.Text;
import com.leafia.lib.LeafiaRef;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The rod uncrafting page (D-3032): a rod, and the item it was made from plus the empty rod assembly.
 *
 * <p>Upstream: {@code .../nuclearfuel/LeafiaRodCraftingJEI.java}, the category over
 * {@link LeafiaRodCrafting}. Its wrapper's {@code buildRecipes()} walked {@link LeafiaRodItem#fromResourceMap}
 * and made a page for every rod with a base item; that is {@link #pages()}. The layout is upstream's
 * {@code setRecipe} - the rod at (2, 2), the base item at (58, 12) and the empty rod at (76, 12) on the
 * 96 by 56 cut of {@code generic_fuel_uncrafting.png} - and its {@code drawInfo}, the warning in two lines
 * centred on x = 48 from y = 39, nine apart, in {@code 0x404040}. The catalyst is the crafting table.
 *
 * <p>This class names no JEI type; {@code com.leafia.jei.AddonJeiCategory} is what turns the {@link #KIND}
 * into a JEI category.
 *
 * <p><b>Differences, both deliberate.</b> Upstream's {@code buildRecipes()} appended to a static list, so a
 * second call (a JEI reload) doubled every page; this builds a fresh list. And a rod whose base item is not
 * registered (a feature that is switched off, D-1002) has {@link LeafiaRodItem#baseStack()} empty - JEI
 * rejects an empty ingredient - so it gets no page; upstream registers every base item, so every rod had one.
 * The page order is {@code fromResourceMap}'s own, a {@code HashMap}'s, as upstream's.
 */
public class LeafiaRodCraftingJEI {

	public static final String PATH = "generic_rod_uncrafting";

	/** The warning, split on {@code $} as upstream's {@code drawInfo} does, and where it is drawn. */
	public static final int TEXT_CENTER_X = 96 / 2;
	public static final int TEXT_FIRST_Y = 39;
	public static final int TEXT_LINE_HEIGHT = 9;
	public static final int TEXT_COLOR = 4210752;

	public static final Kind KIND = new Kind(PATH, () -> LeafiaFeatures.rods && LeafiaRods.leafRod != null,
			() -> Component.translatable("info.leafiarod.jei.name"), LeafiaRef.rl("textures/gui/jei/generic_fuel_uncrafting.png"),
			0, 0, 96, 56, LeafiaRodCraftingJEI::catalysts, LeafiaRodCraftingJEI::pages);

	public static List<ItemStack> catalysts() {
		return List.of(new ItemStack(Items.CRAFTING_TABLE));
	}

	/** The warning, one entry per line. */
	public static List<Text> info() {
		List<Text> lines = new ArrayList<>();
		int y = TEXT_FIRST_Y;
		for(String line : Component.translatable("info.leafiarod.jei.message").getString().split("\\$")) {
			lines.add(new Text(line, TEXT_CENTER_X, y, TEXT_COLOR));
			y += TEXT_LINE_HEIGHT;
		}
		return lines;
	}

	public static List<Page> pages() {
		List<Page> pages = new ArrayList<>();
		if(LeafiaRods.leafRod == null) return pages;
		List<Text> info = info();
		for(LeafiaRodItem rod : LeafiaRodItem.fromResourceMap.values()) {
			if(rod.baseItem == null) continue;
			ItemStack base = rod.baseStack();
			if(base.isEmpty()) continue;
			pages.add(new Page(List.of(
					new Slot(Role.INPUT, 1 + 1, 1 + 1, List.of(new ItemStack(rod))),
					new Slot(Role.OUTPUT, 57 + 1, 11 + 1, List.of(base)),
					new Slot(Role.OUTPUT, 75 + 1, 11 + 1, List.of(new ItemStack(LeafiaRods.leafRod.get())))), info));
		}
		return pages;
	}
}
