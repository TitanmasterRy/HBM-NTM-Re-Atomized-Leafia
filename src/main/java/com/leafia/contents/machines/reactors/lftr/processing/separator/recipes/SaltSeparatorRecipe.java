package com.leafia.contents.machines.reactors.lftr.processing.separator.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * One salt-separator recipe: a {@link GenericRecipe} that also names a salt, how much of it a cycle
 * takes, and the fuel mixture that salt has to carry.
 *
 * <p>Upstream: {@code .../lftr/processing/separator/recipes/SaltSeparatorRecipe.java}.
 *
 * <h2>What a recipe means here</h2>
 *
 * The machine runs a cycle on {@link #saltAmount} millibuckets of {@link #saltType} that carries
 * exactly {@link #mixture} - an isotope and an amount per bucket - and on the fluid inputs the
 * ordinary way. Upstream's two recipes ask for one isotope each (uranium-233 or uranium-235 at
 * 3 per bucket), and the machine itself only supports one: see {@code SaltSeparatorTE}.
 *
 * <p>The builder overrides narrow the return type so a chain stays a {@code SaltSeparatorRecipe};
 * upstream's seven overloads that took a {@code Block} or an {@code Item} and a damage value are the
 * two {@link ItemLike} ones the base recipe has (1.21.1 has no damage-value axis, D-213).
 */
public class SaltSeparatorRecipe extends GenericRecipe {

	public FluidType saltType;
	public int saltAmount;
	public Map<MSRFuel, Double> mixture = new HashMap<>();

	public SaltSeparatorRecipe(String name) {
		super(name);
	}

	public SaltSeparatorRecipe addMixture(MSRFuel type, double amt) {
		mixture.put(type, amt);
		return this;
	}

	public SaltSeparatorRecipe setSalt(FluidType salt, int amount) {
		saltType = salt;
		saltAmount = amount;
		return this;
	}

	/**
	 * Upstream's tooltip: the name, the timing, then the <b>input mixture</b> block (the salt and the
	 * fuel it carries, per bucket), then the ordinary inputs and the outputs.
	 *
	 * <p>The base recipe's {@code print(boolean)} is {@code header, autoSwitch, duration, power, input,
	 * output}; this is the same with the mixture block spliced in before the inputs, and without the
	 * "Internal:" line, which upstream's override never printed.
	 */
	@Override
	public List<String> print(boolean showInternal) {
		List<String> list = new ArrayList<>();
		list.add(ChatFormatting.YELLOW + this.getLocalizedName());
		this.autoSwitch(list);
		this.duration(list);
		this.power(list);
		list.add(ChatFormatting.BOLD + I18nUtil.resolveKey("gui.recipe.inputMix") + ":");
		list.add("  " + ChatFormatting.AQUA + saltAmount + "mB " + saltType.getLocalizedName());

		for(Entry<MSRFuel, Double> entry : mixture.entrySet())
			list.add("  " + ChatFormatting.LIGHT_PURPLE + entry.getValue() + "/B " + I18nUtil.resolveKey("tile.msr.fuel." + entry.getKey()));
		list.add(ChatFormatting.BOLD + I18nUtil.resolveKey("gui.recipe.input") + ":");
		if(inputFluid != null) for(FluidStack fluid : inputFluid)
			list.add("  " + ChatFormatting.BLUE + fluid.fill + "mB " + fluid.type.getLocalizedName()
					+ (fluid.pressure == 0 ? "" : " at " + ChatFormatting.RED + fluid.pressure + " PU"));
		this.output(list);
		return list;
	}

	@Override
	public SaltSeparatorRecipe setupNamed(int duration, long power) {
		return (SaltSeparatorRecipe) super.setupNamed(duration, power);
	}

	@Override
	public SaltSeparatorRecipe setIcon(ItemStack icon) {
		return (SaltSeparatorRecipe) super.setIcon(icon);
	}

	@Override
	public SaltSeparatorRecipe setIcon(ItemLike item) {
		return (SaltSeparatorRecipe) super.setIcon(item);
	}

	@Override
	public SaltSeparatorRecipe setIconToFirstIngredient() {
		return (SaltSeparatorRecipe) super.setIconToFirstIngredient();
	}

	@Override
	public SaltSeparatorRecipe inputFluidsEx(FluidStack... input) {
		return (SaltSeparatorRecipe) super.inputFluidsEx(input);
	}

	@Override
	public SaltSeparatorRecipe inputFluids(FluidStack... input) {
		return (SaltSeparatorRecipe) super.inputFluids(input);
	}

	@Override
	public SaltSeparatorRecipe outputFluids(FluidStack... output) {
		return (SaltSeparatorRecipe) super.outputFluids(output);
	}

	@Override
	public SaltSeparatorRecipe outputItems(IOutput... output) {
		return (SaltSeparatorRecipe) super.outputItems(output);
	}

	@Override
	public SaltSeparatorRecipe outputItems(ItemStack... output) {
		return (SaltSeparatorRecipe) super.outputItems(output);
	}
}
