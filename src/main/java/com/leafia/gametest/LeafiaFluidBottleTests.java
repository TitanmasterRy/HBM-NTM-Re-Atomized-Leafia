package com.leafia.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FT_Toxin;
import com.hbm.inventory.fluid.trait.FT_VentRadiation;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Viscous;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid bottle (D-1066). The constants are upstream's; the fluids are found by trait rather
 * than named, so the tests do not break when the base mod rebalances a fluid.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFluidBottleTests {

	private static final String EMPTY = "empty";

	@GameTest(template = EMPTY)
	public static void oneBottlePerDrinkableFluid(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidBottles) { helper.succeed(); return; }

		List<ItemStack> bottles = ItemNTMFBottle.allBottles(LeafiaItems.ntmfbottle.get());
		Set<Integer> ids = new HashSet<>();
		for(ItemStack bottle : bottles) {
			FluidType fluid = ItemNTMFBottle.getFluidFromStack(bottle);
			helper.assertTrue(ids.add(fluid.getID()), "two bottles of " + fluid.getName());
			helper.assertTrue(fluid != Fluids.NONE && fluid != Fluids.WATER && fluid != Fluids.MERCURY,
					"a bottle of " + fluid.getName() + ", which upstream leaves out");
			helper.assertTrue(!fluid.hasTrait(FT_Gaseous.class) && !fluid.hasTrait(FT_Amat.class),
					"a bottle of " + fluid.getName() + ", a gas or antimatter");
			helper.assertTrue(bottle.getMaxStackSize() == 1, "bottles stack to " + bottle.getMaxStackSize());
		}

		int drinkable = 0;
		for(FluidType fluid : Fluids.getInNiceOrder())
			if(ItemNTMFBottle.isValidFluid(fluid)) drinkable++;
		helper.assertTrue(bottles.size() == drinkable && drinkable > 30, bottles.size() + " bottles for " + drinkable + " drinkable fluids");

		// Viscous fluids take three times as long to drink.
		for(ItemStack bottle : bottles) {
			boolean viscous = ItemNTMFBottle.getFluidFromStack(bottle).hasTrait(FT_Viscous.class);
			int duration = bottle.getUseDuration(null);
			helper.assertTrue(duration == (viscous ? 96 : 32), ItemNTMFBottle.getFluidFromStack(bottle).getName() + " takes " + duration);
		}
		helper.succeed();
	}

	/** A drinkable fluid that does nothing but be hot, so the only damage is the burn. */
	private static FluidType onlyHot(int above) {
		for(FluidType fluid : Fluids.getInNiceOrder()) {
			if(!ItemNTMFBottle.isValidFluid(fluid) || fluid.temperature <= above || fluid.poison != 0) continue;
			if(fluid.hasTrait(FT_Toxin.class) || fluid.hasTrait(FT_Corrosive.class) || fluid.hasTrait(FT_VentRadiation.class)
					|| fluid.hasTrait(FT_Polluting.class)) continue;
			return fluid;
		}
		return null;
	}

	/**
	 * {@code (T - 60) / 400 * 20} of burn, and above 300°C {@code (T - 300) / 50 + 3} seconds of
	 * fire. An iron golem drinks it, because it has the health to survive any fluid here.
	 */
	@GameTest(template = EMPTY)
	public static void aHotFluidBurnsByItsTemperature(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidBottles) { helper.succeed(); return; }

		FluidType hot = onlyHot(300);
		helper.assertTrue(hot != null, "no fluid above 300°C without other traits to test with");

		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 1, 2));
		float before = golem.getHealth();
		ItemNTMFBottle.drink(hot, helper.getLevel(), golem, null);
		float expected = (hot.temperature - 60) / 400F * 20;
		float taken = before - golem.getHealth();
		helper.assertTrue(Math.abs(taken - expected) < 1e-3, hot.getName() + " at " + hot.temperature + "°C took " + taken + ", expected " + expected);
		int fireTicks = (int) ((hot.temperature - 300) / 50F + 3) * 20;
		helper.assertTrue(golem.getRemainingFireTicks() == fireTicks, "fire " + golem.getRemainingFireTicks() + ", expected " + fireTicks);
		helper.succeed();
	}

	/**
	 * Damage past 12.5 that would not kill makes you vomit: one packet a tick for ten ticks,
	 * which is upstream's self-requeueing runnable.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 40)
	public static void aBadEnoughDrinkVomitsForTenTicks(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidBottles) { helper.succeed(); return; }

		FluidType bad = null;
		for(FluidType fluid : Fluids.getInNiceOrder())
			if(ItemNTMFBottle.isValidFluid(fluid) && fluid.poison * 5 * 1.5F >= 12.5F) { bad = fluid; break; }
		helper.assertTrue(bad != null, "no fluid poisonous enough to test with");

		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 1, 2));
		int before = ItemNTMFBottle.Vomit.pending();
		ItemNTMFBottle.drink(bad, helper.getLevel(), golem, null);
		helper.assertTrue(ItemNTMFBottle.Vomit.pending() == before + 1, "drinking " + bad.getName() + " did not start vomiting");
		final FluidType fluid = bad;
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(ItemNTMFBottle.Vomit.pending() == before, "still vomiting after ten ticks of " + fluid.getName());
			helper.succeed();
		});
	}
}
