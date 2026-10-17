package com.leafia.jei;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.lib.LeafiaRef;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The addon's own JEI plugin (D-3032): upstream's {@code _AddonJEI} and the half of {@code MixinJEIConfig} that
 * is the addon's own - the bottle's subtypes and the hide list. The other half of that mixin, which
 * replaced thirteen of the base mod's categories with restyled ones, is not ported: JEI rejects a duplicate
 * category and replacing the base's would be a mixin into {@code com.hbm} (D-3030 #2, D-1001).
 *
 * <p><b>JEI stays optional.</b> JEI finds this class by its {@link JeiPlugin} annotation, so with JEI absent
 * it is never loaded; and no other class in the jar names a JEI type but {@link AddonJeiCategory}, which only
 * this one reaches. Everything that decides what is shown - the pages, the catalysts, the hide list, the
 * subtypes - is in classes that name none, and runs, and is tested, without JEI.
 *
 * <p>Upstream's {@code register} added the catalysts and recipes together; JEI 19 has a registration call
 * for each, so the three overrides below do one kind each, in upstream's order. A reload calls them all
 * again, and the pages are rebuilt from the tables each time.
 */
@JeiPlugin
public class AddonJeiPlugin implements IModPlugin {

	private final List<AddonJeiCategory> categories = new ArrayList<>();

	@Override
	public ResourceLocation getPluginUid() {
		return LeafiaRef.rl("jei_plugin");
	}

	/** The fluid bottle is one item with a fluid in its data: without this JEI shows one bottle. */
	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		if(!LeafiaFeatures.fluidBottles || com.leafia.contents.LeafiaItems.ntmfbottle == null) return;

		registration.registerSubtypeInterpreter(com.leafia.contents.LeafiaItems.ntmfbottle.get(), new ISubtypeInterpreter<ItemStack>() {
			@Override
			public Object getSubtypeData(ItemStack stack, UidContext context) {
				return AddonJeiSubtypes.key(stack);
			}

			@Override
			public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
				Integer key = AddonJeiSubtypes.key(stack);
				return key == null ? "" : "fluid:" + key;
			}
		});
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		categories.clear();
		for(Kind kind : AddonJeiPages.enabled()) {
			AddonJeiCategory category = new AddonJeiCategory(registration.getJeiHelpers().getGuiHelper(), kind);
			categories.add(category);
			registration.addRecipeCategories(category);
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		for(AddonJeiCategory category : categories) {
			List<AddonJeiPages.Page> pages = category.kind().pages().get();
			if(!pages.isEmpty()) registration.addRecipes(category.getRecipeType(), pages);
		}
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for(AddonJeiCategory category : categories) {
			List<ItemStack> machines = category.kind().catalysts().get();
			if(!machines.isEmpty()) registration.addRecipeCatalysts(category.getRecipeType(), machines.toArray(new ItemStack[0]));
		}
	}

	/** Upstream's {@code _JEIBlacklist}: JEI can hide an ingredient once its runtime is up. */
	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, _JEIBlacklist.hidden());
	}
}
