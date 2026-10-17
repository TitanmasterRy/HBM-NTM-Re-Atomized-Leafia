package com.leafia.jei;

import java.util.List;

import com.leafia.jei.AddonJeiPages.Kind;
import com.leafia.jei.AddonJeiPages.Page;
import com.leafia.jei.AddonJeiPages.Slot;
import com.leafia.jei.AddonJeiPages.Text;
import com.leafia.lib.LeafiaRef;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One of the addon's JEI pages over an {@link AddonJeiPages.Kind} (D-3032): the adapter for upstream's
 * {@code LeafiaRodCraftingJEI}, {@code LeafiaRodDecayJEI} and {@code SolBlasterJEI}. Everything with a
 * number in it is the kind's. This class names JEI and client types and cannot load without them; with
 * {@link AddonJeiPlugin} it is the only class in the jar that does, and {@code AddonJeiTests} pins that.
 *
 * <p>The background is the kind's cut of its texture, drawn first in {@link #draw}; each {@link Slot}
 * becomes a JEI slot at its own coordinates and carries no background of its own, since the 18 by 18 box is
 * part of the texture (the base mod's {@code ViewCategory} does the same).
 */
public class AddonJeiCategory implements IRecipeCategory<Page> {

	private final Kind kind;
	private final RecipeType<Page> type;
	private final IDrawableStatic background;
	private final IDrawable icon;

	public AddonJeiCategory(IGuiHelper gui, Kind kind) {
		this.kind = kind;
		this.type = typeOf(kind);
		this.background = gui.drawableBuilder(kind.texture(), kind.u(), kind.v(), kind.width(), kind.height()).build();

		List<ItemStack> machines = kind.catalysts().get();
		this.icon = machines.isEmpty() ? gui.createBlankDrawable(16, 16) : gui.createDrawableItemStack(machines.get(0));
	}

	/** Upstream's {@code leafia.generic_rod_uncrafting} uid is the recipe type {@code leafia:generic_rod_uncrafting}. */
	public static RecipeType<Page> typeOf(Kind kind) {
		return RecipeType.create(LeafiaRef.MODID, kind.path(), Page.class);
	}

	public Kind kind() { return kind; }

	@Override public RecipeType<Page> getRecipeType() { return type; }

	@Override public Component getTitle() { return kind.title().get(); }

	@Override public int getWidth() { return kind.width(); }

	@Override public int getHeight() { return kind.height(); }

	@Override public IDrawable getIcon() { return icon; }

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, Page page, IFocusGroup focuses) {
		for(Slot slot : page.slots())
			builder.addSlot(role(slot.role()), slot.x(), slot.y()).addItemStacks(slot.stacks());
	}

	private static RecipeIngredientRole role(AddonJeiPages.Role role) {
		return switch(role) {
			case INPUT -> RecipeIngredientRole.INPUT;
			case OUTPUT -> RecipeIngredientRole.OUTPUT;
			case CATALYST -> RecipeIngredientRole.CATALYST;
		};
	}

	@Override
	public void draw(Page page, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		background.draw(graphics, 0, 0);
		if(page.texts().isEmpty()) return;
		Font font = Minecraft.getInstance().font;
		for(Text text : page.texts())
			graphics.drawString(font, text.text(), text.centerX() - font.width(text.text()) / 2, text.y(), 0xFF000000 | text.color(), false);
	}
}
