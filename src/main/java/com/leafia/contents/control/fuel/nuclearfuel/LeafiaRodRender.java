package com.leafia.contents.control.fuel.nuclearfuel;

import java.util.ArrayList;
import java.util.List;

import com.hbm.items.machine.ItemDepletedFuel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * How a fuel rod is drawn. Almost none of it is Java any more (D-3001).
 *
 * <p>Upstream: {@code com/leafia/contents/control/fuel/nuclearfuel/LeafiaRodRender.java}, a 777-line
 * {@code TEISR} that, for every rod, every frame, (1) drew the rod's bracket sprite, (2) laid a hand
 * extrusion of the base item's own pixels over the rod's middle with a {@code Brush} - a few dozen
 * quads, one per pixel patch, with its UV window onto a billet's texture - (3) stamped a small
 * "I", "F", "S", "B" or "X" purity icon in the corner the same way, and (4) drew a glow quad over
 * the lot whose opacity followed the rod's heat. The first three depend only on the rod's item type
 * and purity, which are fixed at registration, so they are <b>baked into each rod's item model by
 * a generator script</b>, which runs the renderer's own statements to do it - fifty-nine
 * models, each a list of flat elements with no texture of their own, pointing at the base item's
 * sprite. That is what is left of 700 lines.
 *
 * <p>What is left to do at draw time is the fourth, and it is one number: the glow is two faces in
 * every rod's model with {@code tintindex 1}, and this is the colour handler that gives them an
 * alpha. Upstream's formula, unchanged:
 *
 * <pre>
 * opacity = (heat - meltingPoint/2) / (meltingPoint/2)     // zero at half the melting point
 * </pre>
 *
 * clamped to one, and zero for a rod that has no tag (cold) or no melting point at all. Upstream
 * blends it additively ({@code DST_COLOR, ONE}); an item model has no blend function, so this is an
 * ordinary alpha blend of the same overlay sprite - the rod warms toward yellow (or green for the
 * balefire rods, whose overlay is the draconic one) instead of brightening. The two differ in
 * brightness, not in when or how much.
 *
 * <p>The five depleted fuels get the base mod's own tint: tan while the stack is still cooling.
 *
 * <p>Not ported, and said so: upstream shakes the DG-OMEGA rod by a gaussian 0.025 blocks each frame.
 * An item model cannot move; the rod is drawn still.
 */
public final class LeafiaRodRender {

	private LeafiaRodRender() {}

	/** The glow's tint index in every rod's model. */
	public static final int GLOW_TINT = 1;

	/** Opacity of the glow for a rod at the temperature in its stack: 0 to 1. */
	public static double glowOpacity(LeafiaRodItem rod, ItemStack stack) {
		double meltingPoint = rod.meltingPoint;
		if(meltingPoint == 0) return 0;
		CompoundTag data = LeafiaRodItem.data(stack);
		if(data == null) return 0;
		double heat = data.getDouble("heat");
		double opacity = (heat - meltingPoint / 2) / (meltingPoint / 2);
		if(!(opacity > 0)) return 0;
		return Math.min(opacity, 1);
	}

	/** ARGB for the glow faces: white at the rod's opacity. */
	public static int glowColour(LeafiaRodItem rod, ItemStack stack) {
		int alpha = (int) Math.round(glowOpacity(rod, stack) * 255);
		return (alpha << 24) | 0xFFFFFF;
	}

	/** Registers the glow handler on every rod and the cooling tint on the five depleted fuels. */
	public static void registerColours(RegisterColorHandlersEvent.Item event) {
		List<ItemLike> rods = new ArrayList<>();
		for(var holder : LeafiaRods.RODS.values()) rods.add(holder.get());
		if(!rods.isEmpty())
			event.register((stack, tint) -> {
				if(tint != GLOW_TINT || !(stack.getItem() instanceof LeafiaRodItem rod)) return 0xFFFFFFFF;
				return glowColour(rod, stack);
			}, rods.toArray(new ItemLike[0]));

		List<Item> fuels = new ArrayList<>();
		for(var holder : LeafiaRods.DEPLETED.values()) fuels.add(holder.get());
		if(!fuels.isEmpty())
			event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof ItemDepletedFuel fuel
					? 0xFF000000 | fuel.getColor(stack) : 0xFFFFFFFF, fuels.toArray(new ItemLike[0]));
	}
}
