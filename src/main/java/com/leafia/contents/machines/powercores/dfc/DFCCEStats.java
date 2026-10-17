package com.leafia.contents.machines.powercores.dfc;

import java.util.HashMap;
import java.util.Map;

import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCatalyst;
import com.hbm.items.special.ItemAMSCore;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The numbers the addon's Dark Fusion Core was balanced against: NTM-CE's catalysts and AMS cores, not
 * the 1.7.10 ones this port's base mod carries.
 *
 * <h2>Why the core does not read the base mod's items</h2>
 *
 * The addon is written against NTM-CE 1.5.1.8, and CE <b>rebalanced both item families</b> the core
 * reads. Its catalysts have a different absolute bonus and different modifiers - iron is
 * {@code 10, 0.5, 1.5, 1.5} in CE and {@code 1,000,000, 1.15, 0.95, 1.05} in 1.7.10 - and its AMS cores
 * have an {@code int} power of 500 to 2,500 and {@code float} heat and fuel factors where 1.7.10's are a
 * {@code long} power of a billion and up, an {@code int} heat and an {@code int} fuel. The core's
 * formulas raise power to the 0.666 and multiply by these, so reading the base mod's numbers would not
 * shift the balance, it would move it by nine orders of magnitude. The colours agree, and those stay the
 * base items' own.
 *
 * <p>So the core reads this table, which was read out of the vendored CE jar's bytecode
 * ({@code ModItems.<clinit>}: each {@code ItemCatalyst(int, long, float, float, float, String)} and
 * {@code ItemAMSCore(int, float, float, String)}; the field order of both constructors was checked in
 * their own bytecode). CE's accessors' answers for a stack that is not one are kept too: power 0,
 * modifiers 1. {@code ModItems.glitch}'s three numbers are upstream's own literals in the core (7500,
 * 0.4, 3). D-3011.
 */
public final class DFCCEStats {

	private DFCCEStats() {}

	/** A catalyst: absolute power bonus and three multipliers. */
	public record Catalyst(long powerAbs, float powerMod, float heatMod, float fuelMod) {}

	/** An AMS core: power, heat factor, fuel factor. */
	public record Core(int power, float heat, float fuel) {}

	private static final Map<Item, Catalyst> CATALYSTS = new HashMap<>();
	private static final Map<Item, Core> CORES = new HashMap<>();

	private static synchronized void fill() {
		if(!CATALYSTS.isEmpty()) return;
		CATALYSTS.put(ModItems.ams_catalyst_iron.get(), new Catalyst(10L, 0.5F, 1.5F, 1.5F));
		CATALYSTS.put(ModItems.ams_catalyst_copper.get(), new Catalyst(100L, 0.6F, 1.2F, 0.6F));
		CATALYSTS.put(ModItems.ams_catalyst_aluminium.get(), new Catalyst(250L, 0.7F, 0.85F, 0.85F));
		CATALYSTS.put(ModItems.ams_catalyst_lithium.get(), new Catalyst(500L, 0.8F, 0.75F, 1.15F));
		CATALYSTS.put(ModItems.ams_catalyst_beryllium.get(), new Catalyst(1000L, 0.9F, 1.15F, 0.75F));
		CATALYSTS.put(ModItems.ams_catalyst_tungsten.get(), new Catalyst(5000L, 1.0F, 1.0F, 0.95F));
		CATALYSTS.put(ModItems.ams_catalyst_cobalt.get(), new Catalyst(10000L, 1.02F, 0.95F, 1.0F));
		CATALYSTS.put(ModItems.ams_catalyst_niobium.get(), new Catalyst(25000L, 1.05F, 1.15F, 1.0F));
		CATALYSTS.put(ModItems.ams_catalyst_cerium.get(), new Catalyst(50000L, 1.05F, 1.0F, 1.15F));
		CATALYSTS.put(ModItems.ams_catalyst_thorium.get(), new Catalyst(100000L, 1.1F, 0.95F, 1.2F));
		CATALYSTS.put(ModItems.ams_catalyst_strontium.get(), new Catalyst(200000L, 1.15F, 0.9F, 1.3F));
		CATALYSTS.put(ModItems.ams_catalyst_caesium.get(), new Catalyst(400000L, 1.2F, 0.85F, 1.4F));
		CATALYSTS.put(ModItems.ams_catalyst_schrabidium.get(), new Catalyst(600000L, 1.3F, 0.7F, 1.25F));
		CATALYSTS.put(ModItems.ams_catalyst_euphemium.get(), new Catalyst(800000L, 1.5F, 1.25F, 0.7F));
		CATALYSTS.put(ModItems.ams_catalyst_dineutronium.get(), new Catalyst(1000000L, 2.0F, 1.5F, 2.0F));

		CORES.put(ModItems.ams_core_sing.get(), new Core(500, 0.8F, 1.5F));
		CORES.put(ModItems.ams_core_wormhole.get(), new Core(650, 1.5F, 0.8F));
		CORES.put(ModItems.ams_core_eyeofharmony.get(), new Core(800, 1.5F, 2.0F));
		CORES.put(ModItems.ams_core_thingy.get(), new Core(2500, 0.7F, 0.7F));
	}

	/** CE's catalyst for this stack, or null when it is not one of the fifteen. */
	public static Catalyst catalyst(ItemStack stack) {
		fill();
		return stack.getItem() instanceof ItemCatalyst ? CATALYSTS.get(stack.getItem()) : null;
	}

	public static long getPowerAbs(ItemStack stack) {
		Catalyst c = catalyst(stack);
		return c == null ? 0L : c.powerAbs();
	}

	public static float getPowerMod(ItemStack stack) {
		Catalyst c = catalyst(stack);
		return c == null ? 1F : c.powerMod();
	}

	public static float getHeatMod(ItemStack stack) {
		Catalyst c = catalyst(stack);
		return c == null ? 1F : c.heatMod();
	}

	public static float getFuelMod(ItemStack stack) {
		Catalyst c = catalyst(stack);
		return c == null ? 1F : c.fuelMod();
	}

	/** CE's core for this stack, or null when it is not one of the four. */
	public static Core core(ItemStack stack) {
		fill();
		return stack.getItem() instanceof ItemAMSCore ? CORES.get(stack.getItem()) : null;
	}

	public static int getPowerBase(ItemStack stack) {
		Core c = core(stack);
		return c == null ? 0 : c.power();
	}

	public static float getHeatBase(ItemStack stack) {
		Core c = core(stack);
		return c == null ? 1F : c.heat();
	}

	public static float getFuelBase(ItemStack stack) {
		Core c = core(stack);
		return c == null ? 1F : c.fuel();
	}
}
