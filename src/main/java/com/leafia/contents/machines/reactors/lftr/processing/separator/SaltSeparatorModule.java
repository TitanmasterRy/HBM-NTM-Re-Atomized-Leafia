package com.leafia.contents.machines.reactors.lftr.processing.separator;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.module.machine.ModuleMachineBase;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipe;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipes;

import api.hbm.energymk2.IEnergyHandlerMK2;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The salt separator's recipe runner: the base mod's {@link ModuleMachineBase} with a salt tank beside
 * the ordinary ones.
 *
 * <p>Upstream: {@code .../lftr/processing/separator/SaltSeparatorModule.java}. It overrides the module's
 * {@code update}, {@code setupTanks}, {@code canProcess} and {@code process} to add one thing to each:
 * the <b>salt tank</b> (a Forge tank, not one of the module's NTM ones) has to hold the recipe's salt
 * carrying exactly the recipe's mixture, and a finished cycle takes {@code saltAmount} out of it.
 *
 * <h2>Overloads, not overrides</h2>
 *
 * Upstream's {@code canProcess} and {@code process} take a {@link SaltSeparatorRecipe}, so they
 * <em>overload</em> the base's {@code GenericRecipe} versions rather than replace them - and the base's
 * {@code update} would call the wrong one, which is why this class re-implements {@code update} and
 * has its own {@code process} (whose tail calls the salt-aware {@code canProcess}). Kept as upstream
 * has them, signature for signature.
 *
 * <h2>The mixture test</h2>
 *
 * {@link #isMixtureIncompatible} is exact: every isotope the recipe wants must be present at exactly
 * its amount (a {@code Double.equals}, so 3.0 and 3.0000000000000004 differ), and nothing else may be
 * in the salt. The machine builds the mixture to that amount itself (see {@code SaltSeparatorTE}),
 * from values that are subtracted and added in floating point, so whether a cycle ever starts depends
 * on those sums landing exactly. Upstream's, and pinned.
 *
 * <h2>Two differences from upstream, both about a missing recipe</h2>
 *
 * <ul>
 * <li>{@link #setupTanks} returns early for a {@code null} recipe before it reads the salt tank.
 *     Upstream goes on to read {@code recipe.saltType}, so a separator holding salt whose selected
 *     recipe has vanished (a pack deleted it from the recipe file, or the name was never one) would throw
 *     on every server tick. Reachable by a pack author and not by play, and a crash rather than a
 *     balance question, so guarded (D-3015).</li>
 * <li>The salt tank's {@code null} fluid is an empty stack here.</li>
 * </ul>
 */
public class SaltSeparatorModule extends ModuleMachineBase {

	net.neoforged.neoforge.fluids.capability.templates.FluidTank saltTank;

	public SaltSeparatorModule(int index, IEnergyHandlerMK2 battery, ItemStack[] slots) {
		super(index, battery, slots);
		this.inputSlots = new int[0];
		this.outputSlots = new int[0];
		this.inputTanks = new FluidTank[2];
		this.outputTanks = new FluidTank[3];
	}

	@Override
	public SaltSeparatorRecipes getRecipeSet() {
		return SaltSeparatorRecipes.INSTANCE;
	}

	public SaltSeparatorModule fluidInput(FluidTank a, FluidTank b) {
		inputTanks[0] = a;
		inputTanks[1] = b;
		return this;
	}

	public SaltSeparatorModule fluidOutput(FluidTank a, FluidTank b, FluidTank c) {
		outputTanks[0] = a;
		outputTanks[1] = b;
		outputTanks[2] = c;
		return this;
	}

	public SaltSeparatorModule saltTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank) {
		saltTank = tank;
		return this;
	}

	@Override
	public SaltSeparatorRecipe getRecipe() {
		return this.getRecipeSet().recipeNameMap.get(this.recipe);
	}

	@Override
	public void update(double speed, double power, boolean extraCondition, ItemStack blueprint) {
		SaltSeparatorRecipe recipe = this.getRecipe();
		if(recipe != null && recipe.isPooled() && !recipe.isPartOfPool(ItemBlueprints.grabPool(blueprint))) {
			this.didProcess = false;
			this.progress = 0.0D;
			this.recipe = "null";
		} else {
			this.setupTanks(recipe);
			this.didProcess = false;
			this.markDirty = false;
			if(extraCondition && this.canProcess(recipe, speed, power)) {
				this.process(recipe, speed, power);
				this.didProcess = true;
			} else {
				this.progress = 0.0D;
			}
		}
	}

	/**
	 * True unless the salt carries every isotope the recipe asks for at exactly the asked amount and
	 * nothing the recipe does not mention. Upstream's loop pair, including its unknown-name handling:
	 * an isotope name that is not an {@link MSRFuel} counts as incompatible rather than throwing.
	 */
	public boolean isMixtureIncompatible(SaltSeparatorRecipe recipe, Map<String, Double> mixture) {
		boolean isIncompatible = false;
		for(Entry<MSRFuel, Double> entry : recipe.mixture.entrySet()) {
			if(!mixture.containsKey(entry.getKey().name())) {
				isIncompatible = true;
				break;
			}
			if(!Objects.equals(mixture.get(entry.getKey().name()), entry.getValue())) {
				isIncompatible = true;
				break;
			}
		}
		for(Entry<String, Double> entry : mixture.entrySet()) {
			try {
				if(!recipe.mixture.containsKey(MSRFuel.valueOf(entry.getKey()))) {
					isIncompatible = true;
					break;
				}
			} catch(IllegalArgumentException e) {
				isIncompatible = true;
				break;
			}
		}
		return isIncompatible;
	}

	/**
	 * The base module conforms the NTM tanks to the recipe's fluids; this adds the salt: salt of the
	 * wrong fluid is thrown away whole (the drain asks for twice the capacity), and salt of the right
	 * one gets the mixture protocol's keys written onto its stack.
	 */
	public void setupTanks(SaltSeparatorRecipe recipe) {
		super.setupTanks(recipe);
		if(recipe == null) return; // upstream goes on and throws; see the class note
		if(!saltTank.getFluid().isEmpty()) {
			if(!LeafiaFF.fromFF(saltTank.getFluid().getFluid()).equals(recipe.saltType)) {
				saltTank.drain(saltTank.getCapacity() * 2, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
				return;
			}
			// Upstream also reads the mixture here and, in a block it commented out, emptied an incompatible
			// tank. The read has no effect but the protocol keys the tag gains.
			CompoundTag tag = MSRTEBase.nbtProtocol(saltTank.getFluid());
			LeafiaFF.setTag(saltTank.getFluid(), tag);
		}
	}

	public boolean canProcess(SaltSeparatorRecipe recipe, double speed, double power) {
		if(recipe == null) return false;
		if(!saltTank.getFluid().isEmpty()) {
			if(saltTank.getFluidAmount() < recipe.saltAmount) return false;
			CompoundTag tag = MSRTEBase.nbtProtocol(saltTank.getFluid());
			Map<String, Double> mixture = MSRTEBase.readMixture(tag);
			if(isMixtureIncompatible(recipe, mixture)) return false;
			return super.canProcess(recipe, speed, power);
		}
		return false;
	}

	public void process(SaltSeparatorRecipe recipe, double speed, double power) {
		this.battery.setPower(this.battery.getPower() - (power == 1.0D ? recipe.power : (long) ((double) recipe.power * power)));
		double step = Math.min(speed / (double) recipe.duration, 1.0D);
		this.progress += step;
		if(this.progress >= 1.0D) {
			this.consumeInput(recipe);
			this.produceItem(recipe);
			saltTank.drain(recipe.saltAmount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
			if(this.canProcess(recipe, speed, power)) {
				--this.progress;
			} else {
				this.progress = 0.0D;
			}
		}
	}
}
