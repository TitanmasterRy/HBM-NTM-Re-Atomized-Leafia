package com.leafia.contents.machines.reactors.lftr.processing.separator.recipes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.items.machine.ItemFluidIcon;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;

/**
 * The salt separator's recipe table: two rows, uranium-233 and uranium-235.
 *
 * <p>Upstream: {@code .../lftr/processing/separator/recipes/SaltSeparatorRecipes.java}.
 *
 * <h2>The rows</h2>
 *
 * Both run 60 ticks at 400 HE/t on 1,000 mB of FLiBe carrying 3 of the isotope per bucket and 500 mB
 * of fluorine, and make back the 1,000 mB of FLiBe and 300 mB of the isotope's UF6. The solid outputs
 * upstream left commented out (beryllium and lithium powder) stay out.
 *
 * <h2>Registration</h2>
 *
 * Upstream's mixin on {@code SerializableRecipe.registerAllHandlers} adds {@link #INSTANCE} to the
 * handler list at the tail. Here {@code LeafiaRecipeInjector} does it (D-1095's route, D-3015): it
 * owns the last slot and runs after every base handler. The file is {@code leafiaSaltSeparator.json}.
 *
 * <p>{@link #readExtraData} and {@link #writeExtraData} are upstream's: the salt's fluid name and
 * amount, and the mixture as pairs of an isotope name and an amount.
 */
public class SaltSeparatorRecipes extends GenericRecipes<SaltSeparatorRecipe> {

	public static final SaltSeparatorRecipes INSTANCE = new SaltSeparatorRecipes();

	@Override public int inputItemLimit() { return 0; }
	@Override public int inputFluidLimit() { return 2; }
	@Override public int outputItemLimit() { return 0; }
	@Override public int outputFluidLimit() { return 3; }

	@Override public String getFileName() { return "leafiaSaltSeparator.json"; }

	@Override
	public void readExtraData(JsonElement element, SaltSeparatorRecipe recipe) {
		JsonObject obj = (JsonObject) element;
		recipe.saltType = Fluids.fromName(obj.get("saltType").getAsString());
		recipe.saltAmount = obj.get("saltAmount").getAsInt();
		recipe.mixture = new HashMap<>();
		JsonArray mix = obj.get("mixture").getAsJsonArray();
		for(JsonElement element1 : mix) {
			JsonArray content = element1.getAsJsonArray();
			recipe.mixture.put(MSRFuel.valueOf(content.get(0).getAsString()), content.get(1).getAsDouble());
		}
	}

	@Override
	public void writeExtraData(SaltSeparatorRecipe recipe, JsonWriter writer) throws IOException {
		writer.name("saltType").value(recipe.saltType.getName());
		writer.name("saltAmount").value(recipe.saltAmount);
		writer.name("mixture").beginArray();
		for(Entry<MSRFuel, Double> entry : recipe.mixture.entrySet()) {
			writer.beginArray();
			writer.setIndent("");
			writer.value(entry.getKey().name());
			writer.value(entry.getValue());
			writer.endArray();
			writer.setIndent("  ");
		}
		writer.endArray();
	}

	@Override
	public SaltSeparatorRecipe instantiateRecipe(String name) {
		return new SaltSeparatorRecipe(name);
	}

	@Override
	public void registerDefaults() {
		this.register(new SaltSeparatorRecipe("separator.u233").setupNamed(60, 400)
				.setIcon(ItemFluidIcon.make(LeafiaFluids.UF6_233, 0))
				.setSalt(LeafiaFluids.FLUORIDE, 1000)
				.addMixture(MSRFuel.u233, 3)
				.inputFluids(new FluidStack(LeafiaFluids.FLUORINE, 500))
				.outputFluids(new FluidStack(LeafiaFluids.FLUORIDE, 1000), new FluidStack(LeafiaFluids.UF6_233, 300)));
		this.register(new SaltSeparatorRecipe("separator.u235").setupNamed(60, 400)
				.setIcon(ItemFluidIcon.make(LeafiaFluids.UF6_235, 0))
				.setSalt(LeafiaFluids.FLUORIDE, 1000)
				.addMixture(MSRFuel.u235, 3)
				.inputFluids(new FluidStack(LeafiaFluids.FLUORINE, 500))
				.outputFluids(new FluidStack(LeafiaFluids.FLUORIDE, 1000), new FluidStack(LeafiaFluids.UF6_235, 300)));
	}
}
