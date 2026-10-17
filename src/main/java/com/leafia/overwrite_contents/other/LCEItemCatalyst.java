package com.leafia.overwrite_contents.other;

import java.util.HashMap;
import java.util.Map;

import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCatalyst;
import com.llib.exceptions.LeafiaDevFlaw;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The fifteen catalysts' melting points, in degrees: what the addon's Dark Fusion Core compares its
 * temperature against to decide whether a catalyst is wearing out.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/other/LCEItemCatalyst.java}, a static table filled
 * once from {@code AddonBase} and keyed by the base mod's items. The core's melting point is the lower
 * of its two catalysts', capped at 1,500,000; a stack that is not a catalyst answers that cap.
 *
 * <p>A catalyst the table does not name is upstream's {@code LeafiaDevFlaw} - a programming error, not
 * a state a player can reach, since the base mod registers exactly these fifteen. The blank catalyst is
 * a plain item, not an {@code ItemCatalyst}, and so never asks.
 */
public final class LCEItemCatalyst {

	private LCEItemCatalyst() {}

	/** Upstream's cap, and the answer for a stack that is not a catalyst. */
	public static final int NO_CATALYST = 1500000;

	public static final Map<Item, Integer> meltingPoints = new HashMap<>();

	/** Upstream's {@code registerMeltingPoints}, in upstream's order. Idempotent. */
	public static void registerMeltingPoints() {
		meltingPoints.put(ModItems.ams_catalyst_iron.get(), 2900);
		meltingPoints.put(ModItems.ams_catalyst_copper.get(), 2250);
		meltingPoints.put(ModItems.ams_catalyst_aluminium.get(), 2250);
		meltingPoints.put(ModItems.ams_catalyst_lithium.get(), 600);
		meltingPoints.put(ModItems.ams_catalyst_beryllium.get(), 1287);
		meltingPoints.put(ModItems.ams_catalyst_tungsten.get(), 3422);
		meltingPoints.put(ModItems.ams_catalyst_cobalt.get(), 1495);
		meltingPoints.put(ModItems.ams_catalyst_niobium.get(), 2900);
		meltingPoints.put(ModItems.ams_catalyst_cerium.get(), 2900);
		meltingPoints.put(ModItems.ams_catalyst_thorium.get(), 800);
		meltingPoints.put(ModItems.ams_catalyst_strontium.get(), 3200);
		meltingPoints.put(ModItems.ams_catalyst_caesium.get(), 1952);
		meltingPoints.put(ModItems.ams_catalyst_schrabidium.get(), 3000);
		meltingPoints.put(ModItems.ams_catalyst_euphemium.get(), 4000);
		meltingPoints.put(ModItems.ams_catalyst_dineutronium.get(), 6000);
	}

	public static int getMelting(ItemStack stack) {
		if(stack == null || !(stack.getItem() instanceof ItemCatalyst))
			return NO_CATALYST;
		if(meltingPoints.isEmpty()) registerMeltingPoints();
		Integer value = meltingPoints.get(stack.getItem());
		if(value == null)
			throw new LeafiaDevFlaw("Melting point for catalyst type " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " not found");
		return value;
	}
}
