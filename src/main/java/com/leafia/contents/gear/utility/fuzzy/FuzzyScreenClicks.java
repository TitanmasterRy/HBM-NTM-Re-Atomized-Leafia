package com.leafia.contents.gear.utility.fuzzy;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.element.DrawnTanks;
import com.hbm.inventory.gui.element.DrawnTanks.DrawnTank;

import net.minecraft.world.item.ItemStack;

/**
 * The fuzzy identifier's click on a tank of one of the base mod's own screens: what the click means, with no client class in it so a
 * server test can check it. The listener that asks it is {@code com.leafia.client.LeafiaFuzzyScreenClient}.
 *
 * <p>Upstream: {@code MixinFluidTankNTM}, an {@code @Inject} at an {@code INVOKE} site inside the base's {@code renderTankInfo}, which
 * D-1001 forbids and D-1053 recorded as a hand-off. The base mod's seam is {@link DrawnTanks} (D-2207): {@code GUIElements.drawFluidTankInfo}
 * records every tank's rectangle each frame, so a {@code ScreenEvent.MouseButtonPressed.Pre} listener can find the tank under the mouse
 * without being inside the draw call. The click acts when the identifier is on the cursor and the tank holds a fluid type
 * ({@code Fluids.NONE} is not a type to copy, as in the addon's own screens' click).
 */
public final class FuzzyScreenClicks {

	private FuzzyScreenClicks() {}

	/** The fluid a click at {@code (mouseX, mouseY)} on {@code screen} would set the carried identifier to, or null for no click. */
	@Nullable
	public static FluidType target(Object screen, double mouseX, double mouseY, ItemStack carried) {
		if(carried.isEmpty() || !(carried.getItem() instanceof FuzzyIdentifierItem)) return null;
		DrawnTank drawn = DrawnTanks.at(screen, mouseX, mouseY);
		if(drawn == null) return null;
		FluidType type = drawn.tank().getTankType();
		return type == Fluids.NONE ? null : type;
	}
}
