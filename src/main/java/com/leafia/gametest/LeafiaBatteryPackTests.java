package com.leafia.gametest;

import java.util.Map;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.control.battery.AddonBatteryPackItem;
import com.leafia.contents.control.battery.AddonEnumBatteryPack;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's battery packs (D-1067). The numbers are worked by hand from
 * {@code MixinEnumBatteryPack}'s arguments through the base enum's two constructors, so a slip in
 * either the table or the copied formulas shows up as a wrong literal.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaBatteryPackTests {

	private static final String EMPTY = "empty";

	/** {capacity, charge rate, discharge rate}. */
	private static final Map<AddonEnumBatteryPack, long[]> EXPECTED = Map.of(
			// boolean constructor: rate x 18,000 ticks, charging at 10x
			AddonEnumBatteryPack.BATTERY_DESH, new long[] { 3_150_000_000L, 1_750_000L, 175_000L },
			AddonEnumBatteryPack.BATTERY_EUPHEMIUM, new long[] { 27_000_000_000L, 15_000_000L, 1_500_000L },
			AddonEnumBatteryPack.BATTERY_SATURNITE, new long[] { 14_400_000_000L, 8_000_000L, 800_000L },
			// duration constructor: rate x duration, charging at 10x
			AddonEnumBatteryPack.BATTERY_SLOP, new long[] { 146_666_664_000L, 122_222_220L, 12_222_222L },
			AddonEnumBatteryPack.BATTERY_SPK, new long[] { 100_000_000_002_000L, 166_666_666_670L, 16_666_666_667L },
			AddonEnumBatteryPack.BATTERY_ELECTRO, new long[] { 1_000_000_000_002_000L, 1_666_666_666_670L, 166_666_666_667L });

	@GameTest(template = EMPTY)
	public static void everyPackHoldsMixinsNumbers(GameTestHelper helper) {

		if(!LeafiaFeatures.batteryPacks) { helper.succeed(); return; }

		helper.assertTrue(LeafiaItems.battery_packs.size() == 6, LeafiaItems.battery_packs.size() + " packs registered");
		for(AddonEnumBatteryPack pack : AddonEnumBatteryPack.values()) {
			AddonBatteryPackItem item = LeafiaItems.battery_packs.get(pack).get();
			ItemStack stack = new ItemStack(item);
			long[] want = EXPECTED.get(pack);
			helper.assertTrue(item.getMaxCharge(stack) == want[0], pack + " capacity " + item.getMaxCharge(stack) + ", expected " + want[0]);
			helper.assertTrue(item.getChargeRate(stack) == want[1], pack + " charge rate " + item.getChargeRate(stack));
			helper.assertTrue(item.getDischargeRate(stack) == want[2], pack + " discharge rate " + item.getDischargeRate(stack));
			helper.assertTrue(stack.getMaxStackSize() == 1, pack + " stacks to " + stack.getMaxStackSize());
		}
		helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
				.filter(s -> s.get().asItem() instanceof AddonBatteryPackItem).count() == 6, "not every pack is in the tab");
		helper.succeed();
	}

	/** The base mod's pack asymmetries, carried so the two kinds of pack agree. */
	@GameTest(template = EMPTY)
	public static void aPackChargesLikeTheBaseModsPacks(GameTestHelper helper) {

		if(!LeafiaFeatures.batteryPacks) { helper.succeed(); return; }

		AddonBatteryPackItem item = LeafiaItems.battery_packs.get(AddonEnumBatteryPack.BATTERY_DESH).get();

		ItemStack fresh = new ItemStack(item);
		helper.assertTrue(item.getCharge(fresh) == 0, "a new pack should read empty");

		ItemStack virgin = new ItemStack(item);
		item.dischargeBattery(virgin, 500);
		helper.assertTrue(item.getCharge(virgin) == 0, "discharging a never-charged pack should write 0, not -500");

		ItemStack charged = new ItemStack(item);
		item.chargeBattery(charged, 1_000);
		item.chargeBattery(charged, 750);
		item.dischargeBattery(charged, 250);
		helper.assertTrue(item.getCharge(charged) == 1_500, "1000 + 750 - 250 read " + item.getCharge(charged));

		item.setCharge(charged, 42);
		helper.assertTrue(item.getCharge(charged) == 42, "setCharge did not overwrite");
		helper.assertTrue(item.isBarVisible(charged), "a part-charged pack should show its bar");
		item.setCharge(charged, item.getMaxCharge(charged));
		helper.assertTrue(!item.isBarVisible(charged), "a full pack hides its bar, as the base mod's do");
		helper.succeed();
	}
}
