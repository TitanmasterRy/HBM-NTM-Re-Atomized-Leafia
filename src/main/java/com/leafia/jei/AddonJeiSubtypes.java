package com.leafia.jei;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which items of the addon differ by data, and what tells two such stacks apart (D-3032): the fluid bottle,
 * and nothing else. Without an answer JEI treats every bottle as one item and the creative tab's thirty-odd
 * bottles collapse to a single entry.
 *
 * <p>Upstream's {@code MixinJEIConfig.registerSubtypes} registered {@code AddonItems.ntmfbottle} with an
 * interpreter that read the fluid from the damage value and returned its translation key - or an empty
 * string for a bottle with no valid fluid. Here the fluid is on the stack's custom data
 * ({@link ItemNTMFBottle#getFluidFromStack}), the key is the fluid's id, and "no valid fluid" is
 * {@code null}, which JEI reads as "no subtype". Two bottles are the same entry exactly when they hold the
 * same fluid.
 *
 * <p>Names no JEI type; {@code AddonJeiPlugin} hands {@link #key} to JEI.
 */
public final class AddonJeiSubtypes {

	private AddonJeiSubtypes() {}

	/** The items {@link #key} applies to. Empty with the bottle switched off. */
	public static boolean varies(Item item) {
		return LeafiaItems.ntmfbottle != null && item == LeafiaItems.ntmfbottle.get();
	}

	/** What tells this stack from another of the same item; null for a stack with no fluid. */
	@Nullable
	public static Integer key(ItemStack stack) {
		if(!varies(stack.getItem())) return null;
		FluidType fluid = ItemNTMFBottle.getFluidFromStack(stack);
		return fluid == Fluids.NONE ? null : fluid.getID();
	}
}
