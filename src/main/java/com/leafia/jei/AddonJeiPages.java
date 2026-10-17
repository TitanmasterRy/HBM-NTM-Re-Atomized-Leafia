package com.leafia.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodCraftingJEI;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodDecayJEI;
import com.leafia.contents.machines.processing.solblaster.recipes.SolBlasterJEI;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What the addon's JEI plugin shows, as plain data: the page kinds, in upstream's order, and the recipe
 * pages each one lists (D-3032).
 *
 * <p>Upstream's three categories are {@code IRecipeCategory}/{@code IRecipeWrapper} pairs
 * ({@code LeafiaRodCraftingJEI}, {@code LeafiaRodDecayJEI}, {@code SolBlasterJEI}), each with a
 * {@code buildRecipes()} and a layout in its {@code setRecipe}. Here each is a {@link Kind} that names no
 * JEI type - the same split the base mod makes between {@code com.hbm.handler.jei.view} and its
 * adapter - so what the plugin would register is readable by a GameTest on a server with JEI absent.
 * {@link AddonJeiCategory} turns a kind into a JEI category and is one of the two classes in the jar that
 * name a JEI type; the other is {@link AddonJeiPlugin}.
 *
 * <p>Coordinates are upstream's {@code IGuiItemStackGroup.init(index, input, x, y)}: the top-left of the
 * 16 by 16 item, inside a background that is the {@code (u, v, width, height)} cut of the texture. The
 * 18 by 18 slot box is part of the texture, so a slot here draws nothing of its own.
 */
public final class AddonJeiPages {

	private AddonJeiPages() {}

	public enum Role { INPUT, OUTPUT, CATALYST }

	/** One slot of a page. {@code stacks} cycle, as JEI does for an ore dictionary entry. */
	public record Slot(Role role, int x, int y, List<ItemStack> stacks) {
		public Slot {
			stacks = List.copyOf(stacks.stream().map(ItemStack::copy).toList());
		}
	}

	/** A line of text, centred on {@code centerX}. */
	public record Text(String text, int centerX, int y, int color) {}

	/** One recipe of a kind. */
	public record Page(List<Slot> slots, List<Text> texts) {
		public Page {
			slots = List.copyOf(slots);
			texts = List.copyOf(texts);
		}

		public Page(List<Slot> slots) {
			this(slots, List.of());
		}

		public List<Slot> of(Role role) {
			List<Slot> out = new ArrayList<>();
			for(Slot slot : slots) if(slot.role() == role) out.add(slot);
			return out;
		}
	}

	/**
	 * A category. {@code path} is upstream's uid with the namespace dot made a colon's worth of path:
	 * {@code leafia.generic_rod_uncrafting} is the recipe type {@code leafia:generic_rod_uncrafting}.
	 *
	 * @param enabled whether the category exists at all: the rods and the Sol blaster are features that
	 *                register nothing when switched off (D-1002), and a category over nothing would be an
	 *                empty tab. Upstream cannot be without them.
	 */
	public record Kind(String path, BooleanSupplier enabled, Supplier<Component> title, ResourceLocation texture,
			int u, int v, int width, int height, Supplier<List<ItemStack>> catalysts, Supplier<List<Page>> pages) {}

	/** The three kinds, in the order {@code _AddonJEI.registerCategories} adds them. */
	public static List<Kind> kinds() {
		return List.of(LeafiaRodCraftingJEI.KIND, LeafiaRodDecayJEI.KIND, SolBlasterJEI.KIND);
	}

	/** The kinds that exist with this configuration. */
	public static List<Kind> enabled() {
		return kinds().stream().filter(k -> k.enabled().getAsBoolean()).toList();
	}

	/** An item stack as a catalyst list entry, or nothing when the item is absent or air. */
	public static void addCatalyst(List<ItemStack> out, net.minecraft.world.level.ItemLike item) {
		if(item == null) return;
		ItemStack stack = new ItemStack(item);
		if(!stack.isEmpty()) out.add(stack);
	}
}
