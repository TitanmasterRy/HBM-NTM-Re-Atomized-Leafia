package com.leafia.init;

import com.leafia.contents.gear.combat.shields.AddonShieldCraftingRecipe;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisCrafting;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodCrafting;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's recipe serializers: the dynamic recipes upstream registers by hand through
 * {@code RegistryEvent.Register<IRecipe>} (D-3001).
 *
 * <p>The rod uncrafting recipe, {@code leafia:generic_fuel_handler}, the PWR's debris recipe and the shield
 * decoration recipe, {@code leafia:shield_decoration} (D-3026). A data pack names a serializer by id, so a recipe file of this
 * type is only valid while the serializer is registered - the file carries a
 * {@code neoforge:item_exists} condition on the empty rod, which is registered by the same switch.
 *
 * <p>Gated like everything else (D-1002): with {@code features.rods} off the holder is null and nothing
 * is registered.
 */
public final class LeafiaRecipeSerializers {

	private LeafiaRecipeSerializers() {}

	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, LeafiaRef.MODID);

	/** {@code leafia:generic_fuel_handler}. Null when {@code features.rods} is off. */
	public static DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<LeafiaRodCrafting>> GENERIC_FUEL_HANDLER;

	/** {@code leafia:lwr_debris_crafting_handler}, nine debris back into their block (D-3010). Null unless the PWR is on. */
	public static DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<PWRDebrisCrafting>> LWR_DEBRIS_CRAFTING;

	/** {@code leafia:shield_decoration}, a banner onto one of the addon's shields (D-3026). Null when {@code features.shields} is off. */
	public static DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<AddonShieldCraftingRecipe>> SHIELD_DECORATION;

	public static void register(IEventBus modBus) {
		if(LeafiaFeatures.shields)
			SHIELD_DECORATION = SERIALIZERS.register("shield_decoration",
					() -> new SimpleCraftingRecipeSerializer<>(AddonShieldCraftingRecipe::new));
		if(LeafiaFeatures.pwrReactor())
			LWR_DEBRIS_CRAFTING = SERIALIZERS.register("lwr_debris_crafting_handler",
					() -> new SimpleCraftingRecipeSerializer<>(PWRDebrisCrafting::new));
		if(LeafiaFeatures.rods)
			GENERIC_FUEL_HANDLER = SERIALIZERS.register("generic_fuel_handler",
					() -> new SimpleCraftingRecipeSerializer<>(LeafiaRodCrafting::new));
		SERIALIZERS.register(modBus);
	}
}
