package com.leafia.contents.control.fuel.nuclearfuel;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.jei.AddonJeiPages;
import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.jei.AddonJeiPages.Page;
import com.leafia.jei.AddonJeiPages.Role;
import com.leafia.jei.AddonJeiPages.Slot;
import com.leafia.lib.LeafiaRef;
import com.hbm.blocks.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The rod breeding page (D-3032): a rod and the rod it becomes when its life is spent.
 *
 * <p>Upstream: {@code .../nuclearfuel/LeafiaRodDecayJEI.java}, the category over {@link LeafiaRodItem}'s
 * decay product. Its wrapper's {@code buildRecipes()} made a page for every rod with a {@code newFuel};
 * that is {@link #pages()}, with {@link LeafiaRodItem#newFuel()} resolved from the rod's decay product name
 * (the field upstream read is the method here). The layout is upstream's: the rod at (1, 1) and the product
 * at (91, 1) on the 109 by 19 cut at (33, 33) of {@code gui_nei_fusion.png}.
 *
 * <p>The catalysts are, in upstream's order, the addon PWR's fuel element in its three skins
 * ({@code lwr_element}, {@code reactor_element}, {@code reactor_element_blank}), the base mod's ZIRNOX and
 * research reactor, the unlit RTG furnace and the addon's RTG heater: the machines that burn a rod and
 * hand its product on. An absent one (the PWR switched off) is left out, where upstream would have thrown.
 *
 * <p>Like {@link LeafiaRodCraftingJEI}, it builds a fresh list on each call.
 */
public class LeafiaRodDecayJEI {

	public static final String PATH = "generic_rod_decay";

	public static final Kind KIND = new Kind(PATH, () -> LeafiaFeatures.rods && LeafiaRods.leafRod != null,
			() -> Component.translatable("info.leafiarod.jei.decay"), LeafiaRef.rl("textures/gui/jei/gui_nei_fusion.png"),
			33, 33, 109, 19, LeafiaRodDecayJEI::catalysts, LeafiaRodDecayJEI::pages);

	public static List<ItemStack> catalysts() {
		List<ItemStack> out = new ArrayList<>();
		AddonJeiPages.addCatalyst(out, LeafiaBlocks.lwr_element == null ? null : LeafiaBlocks.lwr_element.get());
		AddonJeiPages.addCatalyst(out, LeafiaBlocks.reactor_element == null ? null : LeafiaBlocks.reactor_element.get());
		AddonJeiPages.addCatalyst(out, LeafiaBlocks.reactor_element_blank == null ? null : LeafiaBlocks.reactor_element_blank.get());
		AddonJeiPages.addCatalyst(out, ModBlocks.reactor_zirnox.get());
		AddonJeiPages.addCatalyst(out, ModBlocks.reactor_research.get());
		AddonJeiPages.addCatalyst(out, ModBlocks.machine_difurnace_rtg_off.get());
		AddonJeiPages.addCatalyst(out, LeafiaBlocks.heater_rt == null ? null : LeafiaBlocks.heater_rt.get());
		return out;
	}

	public static List<Page> pages() {
		List<Page> pages = new ArrayList<>();
		for(LeafiaRodItem rod : LeafiaRodItem.fromResourceMap.values()) {
			LeafiaRodItem newFuel = rod.newFuel();
			if(newFuel == null) continue;
			pages.add(new Page(List.of(
					new Slot(Role.INPUT, 1, 1, List.of(new ItemStack(rod))),
					new Slot(Role.OUTPUT, 91, 1, List.of(new ItemStack(newFuel))))));
		}
		return pages;
	}
}
