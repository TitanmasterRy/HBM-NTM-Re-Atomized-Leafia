package com.leafia.contents.machines.reactors.pwr.debris;

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
 * Nine identical debris stacks in a crafting table make the block they came from.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/debris/PWRDebrisCrafting.java}, registered by
 * hand as {@code leafia:lwr_debris_crafting_handler}; a data-pack recipe of that type here
 * (a generator script writes it). As upstream:
 *
 * <ul>
 * <li>All nine slots must hold the same stack - same item, same tag <b>and same count</b> (1.12.2's
 *     {@code areItemStacksEqual}, {@link ItemStack#matches} here) - of a debris item that may be crafted
 *     back (not the shard) and that carries a tag. So it needs a 3x3 grid.</li>
 * <li>The result is one plain item of the named block: the block's state is not carried, as upstream's
 *     {@code new ItemStack(block)} dropped the metadata. A name no block has gives nothing.</li>
 * </ul>
 *
 * <p>Upstream also wrote a neutron-contamination tag onto the result, commented out; not carried.
 */
public class PWRDebrisCrafting extends CustomRecipe {

	public PWRDebrisCrafting(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput inv, Level level) {
		if(inv.size() != 9) return false;
		ItemStack oneStack = null;
		for(int i = 0; i < 9; i++) {
			ItemStack stack = inv.getItem(i);
			if(oneStack == null) oneStack = stack;
			if(!ItemStack.matches(oneStack, stack)) return false;
		}
		if(oneStack.isEmpty()) return false;
		if(oneStack.getItem() instanceof PWRDebrisItem item)
			return item.canBeCraftedBack && PWRDebrisItem.data(oneStack) != null;
		return false;
	}

	@Override
	public ItemStack assemble(CraftingInput inv, HolderLookup.Provider registries) {
		ItemStack stk = inv.size() > 0 ? inv.getItem(0) : ItemStack.EMPTY;
		if(!stk.isEmpty() && stk.getItem() instanceof PWRDebrisItem) {
			CompoundTag nbt = PWRDebrisItem.data(stk);
			if(nbt != null) return new ItemStack(PWRDebrisItem.blockOf(nbt.getString("block")));
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width == 3 && height == 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return LeafiaRecipeSerializers.LWR_DEBRIS_CRAFTING.get();
	}
}
