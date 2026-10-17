package com.leafia.contents.gear.combat.shields;

import com.leafia.init.LeafiaRecipeSerializers;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * A banner and one of the addon's shields in the crafting grid make the shield wear the banner (D-3026).
 *
 * <p>Upstream: {@code com/leafia/contents/gear/combat/shields/AddonShieldCraftingRecipe.java}, a dynamic
 * {@code IRecipe} that is vanilla's {@code ShieldDecorationRecipe} for {@code AddonShieldItem}s: one banner, one
 * shield, nothing else; a shield that already carries a {@code BlockEntityTag} is refused, and the result is the
 * shield with the banner's {@code BlockEntityTag} copied in and its colour written as {@code Base}.
 *
 * <p>1.21.1 keeps a banner's patterns and colour as data components, which vanilla's own recipe copies onto
 * {@code minecraft:shield}: {@code BANNER_PATTERNS} (the layers) and {@code BASE_COLOR}. This is that recipe over the
 * addon's shield class. "Already carries a {@code BlockEntityTag}" is "already carries a base colour": upstream writes
 * {@code Base} every time, so a shield that was ever decorated, however plainly, is refused; and vanilla's own test, an
 * empty pattern list, would let a plain-banner shield be decorated again.
 *
 * <p>The shield's tooltip lists the patterns without any help (the item extends vanilla's {@code ShieldItem}), and
 * {@code AddonShieldRender} draws them over the shield's blank skin (D-3031).
 *
 * <p><b>The rifle is not here.</b> Upstream's {@code matches} first asks whether the grid is the antimatter rifle's
 * recipe (a 3x3 of a schraranium crystal, a bismoid circuit, a bismoid bronze barrel, an ultra-alloy receiver, the filled
 * mysticite cell, a saturnite mechanism and a hard-plastic grip) and, if so, returns {@code leafia:gun_leafia_amrifle}.
 * It reads the grid by position, so it is the shaped data recipe {@code leafia:gun_leafia_amrifle}
 * (a generator script, D-3028), which a recipe viewer can show.
 */
public class AddonShieldCraftingRecipe extends CustomRecipe {

	public AddonShieldCraftingRecipe(CraftingBookCategory category) {
		super(category);
	}

	/** Whether this shield may still be decorated: upstream's {@code getSubCompound("BlockEntityTag") == null}. */
	static boolean undecorated(ItemStack shield) {
		return !shield.has(DataComponents.BASE_COLOR)
				&& shield.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		ItemStack shield = ItemStack.EMPTY;
		ItemStack banner = ItemStack.EMPTY;

		for(int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if(stack.isEmpty()) continue;

			if(stack.getItem() instanceof BannerItem) {
				if(!banner.isEmpty()) return false;
				banner = stack;
			} else {
				if(!(stack.getItem() instanceof AddonShieldItem) || !shield.isEmpty() || !undecorated(stack)) return false;
				shield = stack;
			}
		}

		return !shield.isEmpty() && !banner.isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		ItemStack shield = ItemStack.EMPTY;
		ItemStack banner = ItemStack.EMPTY;

		for(int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if(stack.getItem() instanceof BannerItem) banner = stack;
			else if(stack.getItem() instanceof AddonShieldItem) shield = stack.copy();
		}

		if(!shield.isEmpty() && !banner.isEmpty()) {
			shield.set(DataComponents.BANNER_PATTERNS, banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
			shield.set(DataComponents.BASE_COLOR, ((BannerItem) banner.getItem()).getColor());
		}
		return shield;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return LeafiaRecipeSerializers.SHIELD_DECORATION.get();
	}
}
