package com.leafia.contents.control.fuel.nuclearfuel;

import javax.annotation.Nullable;

import com.leafia.init.LeafiaRecipeSerializers;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Uncrafting: a rod alone in the crafting grid becomes the item it was made from, and the empty rod
 * assembly comes back with it.
 *
 * <p>Upstream: {@code com/leafia/contents/control/fuel/nuclearfuel/LeafiaRodCrafting.java}, registered
 * as {@code leafia:generic_fuel_handler}. One dynamic recipe stands in for the fifty-eight reverse
 * recipes upstream's {@code AddonCraftingRecipes} has commented out ("addShapelessAuto(base, rod)").
 * The empty rod is not in this recipe's result: it is the rod's container item, which crafting
 * hands back by itself ({@code AddonItemHazardBase#getCraftingRemainingItem}), as for the forward
 * recipe.
 *
 * <p><b>A rod that has burned past five percent of its life cannot be uncrafted</b> - the check is
 * {@code depletion >= life * 0.05} when the grid is matched and {@code depletion > life * 0.05} when
 * the result is made, as upstream has them (the difference is a rod at exactly five percent, which
 * matches and then yields nothing). A rod with no life ({@code life == 0}: the depleted ones, lead)
 * is never refused. A rod with no base item - the balefire rods, catsmilium, confusium - <em>matches</em>
 * and yields nothing, as upstream's {@code new ItemStack(null, ...)} does: it is not crafted from
 * anything, so there is nothing to give back.
 */
public class LeafiaRodCrafting extends CustomRecipe {

	public LeafiaRodCrafting(CraftingBookCategory category) {
		super(category);
	}

	/** The one non-empty stack in the grid, or null when it holds none or more than one. */
	@Nullable
	public ItemStack getStack(CraftingInput inv) {
		ItemStack stackie = null;
		for(int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getItem(i);
			if(stack.isEmpty()) continue;
			if(stackie == null)
				stackie = stack;
			else
				return null;
		}
		return stackie;
	}

	@Override
	public boolean matches(CraftingInput inv, Level level) {
		ItemStack stackie = getStack(inv);
		if(stackie != null && stackie.getItem() instanceof LeafiaRodItem rod) {
			CompoundTag tag = LeafiaRodItem.data(stackie);
			if(tag != null) {
				if(tag.getDouble("depletion") >= rod.life * 0.05 && rod.life > 0)
					return false;
			}
			return true;
		}
		return false;
	}

	@Override
	public ItemStack assemble(CraftingInput inv, HolderLookup.Provider registries) {
		ItemStack stackie = getStack(inv);
		if(stackie != null && stackie.getItem() instanceof LeafiaRodItem rod) {
			CompoundTag tag = LeafiaRodItem.data(stackie);
			if(tag != null) {
				if(tag.getDouble("depletion") > rod.life * 0.05 && rod.life > 0)
					return ItemStack.EMPTY;
			}
			return rod.baseStack();
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width >= 1 && height >= 1;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return LeafiaRecipeSerializers.GENERIC_FUEL_HANDLER.get();
	}
}
