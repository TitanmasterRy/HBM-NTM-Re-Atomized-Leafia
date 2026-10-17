package com.leafia.init.recipes;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The Dark Fusion Core's crate recipes: what an item becomes in a tungsten crate when a beam of enough energy
 * lands on it (D-3021).
 *
 * <p>Upstream's table is {@code com.hbm.inventory.recipes.DFCRecipes}, a class of <b>NTM-CE's</b>, not of the
 * 1.7.10 base this port follows (D-3011 found no such class in {@code hbm-src}), so the addon holds it. Its
 * one writer is {@link LeafiaBaseRecipes}' {@code dfc()} family - the thirty rows of {@code AddonDFCRecipes} -
 * and its one reader the tungsten crate's smelt ({@code TungstenCrateSpk}). Upstream's three accessors are kept
 * under their names: {@code setRecipe}, {@code getRequiredFlux} and {@code getOutput}.
 *
 * <p><b>The key is an item, a block, a stack, or an ore name</b> (a {@code String}, matched through the tag
 * {@code NtmTags} derives from it); a row written twice - the injector re-runs after every recipe reload - keeps
 * one copy. A stack matches without regard to its size, and the first row in the order written that matches
 * wins. An output is handed out as a copy.
 */
public final class DFCRecipes {

	private DFCRecipes() {}

	/** The energy a beam needs and what it makes. */
	public record Row(long flux, AStack input, ItemStack output) {}

	private static final Map<AStack, Row> ROWS = new LinkedHashMap<>();

	/** Upstream's {@code setRecipe(long, Object, ItemStack)}. */
	public static void setRecipe(long flux, Object input, ItemStack output) {
		AStack key;
		if(input instanceof String name) key = new OreDictStack(name);
		else if(input instanceof ItemStack stack) key = new ComparableStack(stack).makeSingular();
		else if(input instanceof ItemLike item) key = new ComparableStack(item);
		else throw new IllegalArgumentException("a DFC recipe input is an item, a block, a stack or an ore name, not " + input);
		ROWS.put(key, new Row(flux, key, output.copy()));
	}

	/** The row a stack falls under, or null. */
	@Nullable
	public static Row find(ItemStack stack) {
		if(stack.isEmpty()) return null;
		for(Row row : ROWS.values()) {
			if(row.input().matchesRecipe(stack, true)) return row;
		}
		return null;
	}

	/** The energy needed to make this stack into something else, or -1 if no row names it. */
	public static long getRequiredFlux(ItemStack stack) {
		Row row = find(stack);
		return row == null ? -1 : row.flux();
	}

	/** What the stack becomes (one of the output), or null if no row names it. */
	@Nullable
	public static ItemStack getOutput(ItemStack stack) {
		Row row = find(stack);
		return row == null ? null : row.output().copy();
	}

	public static int size() {
		return ROWS.size();
	}

	/** Takes a row away, for the tests: the input as it was written. Answers whether there was one. */
	public static boolean remove(Object input) {
		AStack key;
		if(input instanceof String name) key = new OreDictStack(name);
		else if(input instanceof ItemStack stack) key = new ComparableStack(stack).makeSingular();
		else if(input instanceof ItemLike item) key = new ComparableStack(item);
		else return false;
		return ROWS.remove(key) != null;
	}
}
