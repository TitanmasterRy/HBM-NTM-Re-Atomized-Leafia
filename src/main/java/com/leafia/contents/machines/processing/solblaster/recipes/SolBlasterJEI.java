package com.leafia.contents.machines.processing.solblaster.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterRecipes.Input;
import com.leafia.jei.AddonJeiPages;
import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.jei.AddonJeiPages.Page;
import com.leafia.jei.AddonJeiPages.Role;
import com.leafia.jei.AddonJeiPages.Slot;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Neutron Blaster page (D-3032): an item, or any item of a tag, and what the blaster makes of it.
 *
 * <p>Upstream: {@code .../solblaster/recipes/SolBlasterJEI.java}, the category over
 * {@link SolBlasterRecipes}. Its wrapper's {@code buildRecipes()} walked the recipe table - <b>only the
 * recipes</b>, not the fallbacks or the stack rules, which a hopper cannot feed in and the blaster treats
 * differently - and made a page per row, an ore dictionary entry expanding to every stack the dictionary
 * lists; that is {@link #pages()}, a tag expanding to every item it holds. The layout is upstream's: the
 * input at (1, 1) and the output at (91, 1) on the 109 by 19 cut at (33, 33) of {@code blaster.png}. The
 * catalyst is the blaster, and the title is its name.
 *
 * <p>Upstream has no {@code metadata}-keyed inner map to walk here (every variant is an item of its own,
 * D-213), so a row is one page. A row whose tag holds nothing, or whose output is absent, has no ingredient
 * for JEI to show and gets no page. A fresh list is built on each call, where upstream's appended to a
 * static one.
 */
public class SolBlasterJEI {

	public static final String PATH = "sol_blaster";

	public static final Kind KIND = new Kind(PATH, () -> LeafiaFeatures.solBlaster && LeafiaBlocks.sol_blaster != null,
			() -> LeafiaBlocks.sol_blaster == null ? Component.empty() : LeafiaBlocks.sol_blaster.get().getName(),
			LeafiaRef.rl("textures/gui/jei/blaster.png"), 33, 33, 109, 19, SolBlasterJEI::catalysts, SolBlasterJEI::pages);

	public static List<ItemStack> catalysts() {
		List<ItemStack> out = new ArrayList<>();
		AddonJeiPages.addCatalyst(out, LeafiaBlocks.sol_blaster == null ? null : LeafiaBlocks.sol_blaster.get());
		return out;
	}

	/** Every stack a row accepts: its item, or the items of its tag. */
	public static List<ItemStack> inputs(Input input) {
		List<ItemStack> stacks = new ArrayList<>();
		if(input.item() != null) {
			stacks.add(new ItemStack(input.item()));
		} else if(input.tag() != null) {
			for(Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(input.tag()))
				stacks.add(new ItemStack(holder.value()));
		}
		return stacks;
	}

	public static List<Page> pages() {
		List<Page> pages = new ArrayList<>();
		for(Map.Entry<Input, Item> entry : SolBlasterRecipes.recipes().entrySet()) {
			List<ItemStack> in = inputs(entry.getKey());
			if(in.isEmpty() || entry.getValue() == null) continue;
			pages.add(new Page(List.of(
					new Slot(Role.INPUT, 1, 1, in),
					new Slot(Role.OUTPUT, 91, 1, List.of(new ItemStack(entry.getValue()))))));
		}
		return pages;
	}
}
