package com.leafia.unsorted.fluids;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidLoadingHandler;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lets every base-mod machine's canister slots fill a glass bottle from its tank, and pour a fluid
 * bottle back in: 333 mB a bottle, as upstream.
 *
 * <p>Upstream: {@code com/leafia/unsorted/fluids/FluidLoaderBottle.java}, put at the head of
 * {@code FluidTankNTM.loadingHandlers} by {@code MixinFluidTankNTM}'s {@code <clinit>} injection.
 * Here the base mod's {@link FluidTank#loadingHandlers} is a public list, so {@link #install()}
 * adds it at index 0 without a mixin (D-1001) - the same position, ahead of
 * {@code FluidLoaderStandard}, so a glass bottle meets this handler first.
 *
 * <p>The bodies are upstream's, line for line, including two things that look like mistakes and
 * are kept (rule 4):
 * <ul>
 * <li>{@link #fillItem} answers false even after it fills a bottle, so the chain goes on to the
 *     standard loader with the bottles that are left. It finds nothing to do: vanilla's glass
 *     bottle is a container only for water, and water is not a valid bottle fluid.</li>
 * <li>{@link #emptyItem} answers true whenever the input is a fluid bottle, even one it could not
 *     pour (wrong fluid, full tank, blocked output), so the chain stops there.</li>
 * </ul>
 *
 * <p>Two translations: 1.12.2's {@code IItemHandler} slots are the base mod's {@code ItemStack[]},
 * so {@code extractItem}/{@code insertItem} become reads of {@code slots[in]} and a "can the output
 * take it" check; and the bottle's fluid is a component rather than a damage value
 * ({@link ItemNTMFBottle#make}). A bottle stacks to one, so the output takes one only when empty.
 * Its own switch is {@code fluidBottles} - it is the bottle's, not the Forge-fluid bridge's.
 */
public class FluidLoaderBottle extends FluidLoadingHandler {

	/** Upstream's bottle volume. */
	public static final int AMOUNT = 333;

	private static boolean installed = false;

	/** Puts the handler at the head of the base mod's chain, once, when the bottle exists. */
	public static void install() {
		if(installed || !LeafiaFeatures.fluidBottles) return;
		installed = true;
		FluidTank.loadingHandlers.add(0, new FluidLoaderBottle());
	}

	public static boolean installed() {
		return installed;
	}

	private static boolean fits(ItemStack[] slots, int out, ItemStack stack) {
		ItemStack there = slots[out];
		if(there.isEmpty()) return true;
		return ItemStack.isSameItemSameComponents(there, stack) && there.getCount() + stack.getCount() <= there.getMaxStackSize();
	}

	private static void put(ItemStack[] slots, int out, ItemStack stack) {
		if(slots[out].isEmpty()) slots[out] = stack.copy();
		else slots[out].grow(stack.getCount());
	}

	@Override
	public boolean fillItem(ItemStack[] slots, int in, int out, FluidTank tank, HolderLookup.Provider registries) {
		if(tank.getPressure() != 0) return false;
		ItemStack inputStack = slots[in];
		if(inputStack.isEmpty()) return true;
		if(!inputStack.is(Items.GLASS_BOTTLE)) return false;
		FluidType type = tank.getTankType();
		if(!ItemNTMFBottle.isValidFluid(type)) return false;
		ItemStack bottle = ItemNTMFBottle.make(LeafiaItems.ntmfbottle.get(), type);
		int remainder = tank.getFill() - AMOUNT;
		if(remainder >= 0) {
			if(fits(slots, out, bottle)) {
				tank.setFill(remainder);
				put(slots, out, bottle);
				slots[in].shrink(1);
			}
		}
		return false;
	}

	@Override
	public boolean emptyItem(ItemStack[] slots, int in, int out, FluidTank tank, HolderLookup.Provider registries) {
		ItemStack inputStack = slots[in];
		if(inputStack.isEmpty()) return true;
		if(!(inputStack.getItem() instanceof ItemNTMFBottle)) return false;
		FluidType itemFluidType = ItemNTMFBottle.getFluidFromStack(inputStack);
		if(itemFluidType == Fluids.NONE) return false;
		// Upstream: "1.7 uses FluidContainerRegistry.getFluidContent(stackIn, tankType) here. logic
		// changed to make empty tanks change its type automatically to the fluid type of the container"
		int amount = AMOUNT;
		if(tank.getTankType() != Fluids.NONE && tank.getTankType() != itemFluidType) return false;
		if(tank.getFill() + amount > tank.getMaxFill()) return false;

		ItemStack emptyContainer = new ItemStack(Items.GLASS_BOTTLE);
		if(fits(slots, out, emptyContainer)) {
			if(tank.getTankType() == Fluids.NONE) tank.setTankType(itemFluidType);
			tank.setFill(tank.getFill() + amount);
			slots[in].shrink(1);
			put(slots, out, emptyContainer);
		}
		return true;
	}
}
