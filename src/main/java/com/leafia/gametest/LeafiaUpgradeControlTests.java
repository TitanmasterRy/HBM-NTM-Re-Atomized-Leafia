package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.machine.BlockEntityMachineSolderingStation;
import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.control.upgrades.AddonUpgradeItem;
import com.leafia.contents.control.upgrades.AddonUpgradeItem.ControlUpgradeFreqPacket;
import com.leafia.contents.control.upgrades.ControlUpgrade;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The control upgrade (D-3021): the item, its screen's packet, and the cap it puts on a machine's other upgrades.
 *
 * <p>The cap is tested on a real machine's own manager - the soldering station's, with upgrade slots 9 and 10 and
 * the three upgrade types a speed, power and overdrive upgrade make - fed a signal through the real RTTY bus. Every
 * channel a test uses is taken off the bus again.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaUpgradeControlTests {

	private static final String EMPTY = "empty";
	private static final String PREFIX = "w49_";

	private static boolean on() {
		return LeafiaFeatures.upgradeControl && LeafiaItems.upgrade_control != null;
	}

	private static ItemStack control(String freq) {
		ItemStack stack = new ItemStack(LeafiaItems.upgrade_control.get());
		if(freq != null) AddonUpgradeItem.setFrequency(stack, freq);
		return stack;
	}

	/** A soldering station that is not in the world, with the level it needs and one scan done. */
	private static BlockEntityMachineSolderingStation machine(GameTestHelper helper) {
		BlockEntityMachineSolderingStation be = new BlockEntityMachineSolderingStation(helper.absolutePos(new BlockPos(1, 1, 1)),
				ModBlocks.machine_soldering_station.get().defaultBlockState());
		be.setLevel(helper.getLevel());
		return be;
	}

	private static void scan(BlockEntityMachineSolderingStation be) {
		be.upgradeManager.checkSlots(be.slots, 9, 10);
	}

	private static void broadcast(GameTestHelper helper, String channel, Object signal) {
		RTTYSystem.broadcast(helper.getLevel(), PREFIX + channel, signal);
		RTTYSystem.updateBroadcastQueue();
	}

	private static void cleanBus() {
		RTTYSystem.broadcast.keySet().removeIf(key -> key.getValue().startsWith(PREFIX));
		RTTYSystem.newMessages.keySet().removeIf(key -> key.getValue().startsWith(PREFIX));
	}

	private static int level(BlockEntityMachineSolderingStation be, UpgradeType type) {
		return be.upgradeManager.getLevel(type);
	}

	// ------------------------------------------------------------------ the item

	@GameTest(template = EMPTY)
	public static void theControlUpgradeIsASpecialUpgradeOfTierZeroThatStacksToOne(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		Item item = LeafiaItems.upgrade_control.get();
		helper.assertTrue(item instanceof AddonUpgradeItem upgrade && upgrade.type == UpgradeType.SPECIAL && upgrade.tier == 0 && upgrade.isControl(), "a SPECIAL upgrade of tier 0");
		helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1, "upstream's setMaxStackSize(1)");
		helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream().anyMatch(s -> s.get().asItem() == item), "in the addon's tab");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theFrequencyIsTheStacksFreqAndSettingItReplacesTheWholeTag(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ItemStack stack = control(null);
		helper.assertTrue(AddonUpgradeItem.frequency(stack).isEmpty(), "none by default");
		AddonUpgradeItem.setFrequency(stack, "alpha");
		helper.assertTrue(AddonUpgradeItem.frequency(stack).equals("alpha") && stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("freq").equals("alpha"), "stored as {freq}");

		CompoundTag other = stack.get(DataComponents.CUSTOM_DATA).copyTag();
		other.putInt("something", 5);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(other));
		AddonUpgradeItem.setFrequency(stack, "beta");
		helper.assertTrue(stack.get(DataComponents.CUSTOM_DATA).copyTag().getAllKeys().equals(java.util.Set.of("freq")), "upstream's setTagCompound replaces the lot");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aRightClickInTheMainHandIsHandledAndTheOffHandFails(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		ItemStack stack = control("x");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResultHolder<ItemStack> main = stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(main.getResult().consumesAction(), "the main hand is handled on the server (the screen is the client's)");
		player.setItemInHand(InteractionHand.OFF_HAND, stack);
		InteractionResultHolder<ItemStack> off = stack.getItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertTrue(!off.getResult().consumesAction(), "the off hand fails: " + off.getResult());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theTooltipNamesTheFrequencyThenAnEmptyLineThenTheDescription(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		List<Component> tooltip = new ArrayList<>();
		control("sun").getItem().appendHoverText(control("sun"), Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
		helper.assertTrue(tooltip.size() >= 3, "frequency, a blank line, the description: " + tooltip.size());
		helper.assertTrue(tooltip.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key && key.getKey().equals("item.upgrade_control.freq")
				&& key.getArgs().length == 1 && key.getArgs()[0].equals("sun"), "the frequency line: " + tooltip.get(0));
		helper.assertTrue(tooltip.get(1).getString().isEmpty(), "a blank line");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRecipeIsUpstreams(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		var holder = helper.getLevel().getRecipeManager().byKey(LeafiaRef.rl("upgrade_control"));
		helper.assertTrue(holder.isPresent(), "a recipe");
		var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) holder.get().value();
		helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3, "3 by 3");
		helper.assertTrue(recipe.getIngredients().get(0).test(new ItemStack(ModItems.circuit_capacitor.get())) && recipe.getIngredients().get(1).test(new ItemStack(ModItems.circuit_basic.get()))
				&& recipe.getIngredients().get(4).test(new ItemStack(ModItems.upgrade_template.get())), "capacitors in the corners, basic circuits at the sides, the template in the middle");
		helper.assertTrue(recipe.getIngredients().get(8).test(new ItemStack(ModItems.circuit_capacitor.get())) && recipe.getIngredients().get(5).test(new ItemStack(ModItems.circuit_basic.get())), "the pattern CIC / ITI / CIC");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the packet

	@GameTest(template = EMPTY)
	public static void theChannelPacketWritesTheMainHandsControlUpgrade(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, control("old"));

		helper.assertTrue(ControlUpgradeFreqPacket.apply(player, "new"), "a control upgrade in the main hand took it");
		helper.assertTrue(AddonUpgradeItem.frequency(player.getMainHandItem()).equals("new"), "written");
		helper.assertTrue(ControlUpgradeFreqPacket.apply(player, "0123456789"), "ten characters fit");
		helper.assertTrue(!ControlUpgradeFreqPacket.apply(player, "01234567890") && AddonUpgradeItem.frequency(player.getMainHandItem()).equals("0123456789"), "eleven do not");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(!ControlUpgradeFreqPacket.apply(player, "x") && !player.getMainHandItem().has(DataComponents.CUSTOM_DATA), "a stick is left alone");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.upgrade_speed_1.get()));
		helper.assertTrue(!ControlUpgradeFreqPacket.apply(player, "x"), "another upgrade is left alone");

		// upstream's else-if repeats the main hand: the off hand is never looked in
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, control("off"));
		helper.assertTrue(!ControlUpgradeFreqPacket.apply(player, "x") && AddonUpgradeItem.frequency(player.getOffhandItem()).equals("off"), "the off hand is never looked in");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theChannelPacketThroughItsFraming(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, control(""));
		helper.assertTrue(new ControlUpgradeFreqPacket().acceptsServerbound(), "a client may send it");
		LeafiaCrateTestKit.run(new ControlUpgradeFreqPacket("clientside"), player, PacketFlow.CLIENTBOUND);
		helper.assertTrue(AddonUpgradeItem.frequency(player.getMainHandItem()).isEmpty(), "it acted clientbound");
		LeafiaCrateTestKit.run(new ControlUpgradeFreqPacket("serverside"), player, PacketFlow.SERVERBOUND);
		helper.assertTrue(AddonUpgradeItem.frequency(player.getMainHandItem()).equals("serverside"), "and serverbound it wrote the channel");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the cap

	@GameTest(template = EMPTY)
	public static void withoutAControlUpgradeTheLevelsAreUpstreams(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityMachineSolderingStation be = machine(helper);
		be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
		be.setItem(10, new ItemStack(ModItems.upgrade_power_1.get()));
		scan(be);
		helper.assertTrue(level(be, UpgradeType.SPEED) == 3 && level(be, UpgradeType.POWER) == 1 && level(be, UpgradeType.OVERDRIVE) == 0, "3, 1 and 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aControlUpgradeOnAChannelNobodyBroadcastsOnStopsEveryUpgrade(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		try {
			BlockEntityMachineSolderingStation be = machine(helper);
			be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
			be.setItem(10, control(PREFIX + "silent"));
			scan(be);
			helper.assertTrue(level(be, UpgradeType.SPEED) == 0 && level(be, UpgradeType.POWER) == 0 && level(be, UpgradeType.OVERDRIVE) == 0 && level(be, UpgradeType.SPECIAL) == 0,
					"every upgrade reads zero, the speed upgrade's included");

			// an upgrade with no name at all is the same silence
			be.setItem(10, control(null));
			scan(be);
			helper.assertTrue(level(be, UpgradeType.SPEED) == 0, "a control upgrade nobody named stops them too");
		} finally {
			cleanBus();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHeardNumberCapsTheLevel(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		try {
			BlockEntityMachineSolderingStation be = machine(helper);
			be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
			be.setItem(10, control(PREFIX + "dial"));
			scan(be);

			broadcast(helper, "dial", "2");
			helper.assertTrue(level(be, UpgradeType.SPEED) == 2, "a 2 caps a 3 at 2: " + level(be, UpgradeType.SPEED));
			helper.assertTrue(level(be, UpgradeType.POWER) == 0, "and caps no upgrade up: a 0 stays 0");

			broadcast(helper, "dial", "7");
			helper.assertTrue(level(be, UpgradeType.SPEED) == 3, "a 7 leaves the 3: " + level(be, UpgradeType.SPEED));

			broadcast(helper, "dial", 1);
			helper.assertTrue(level(be, UpgradeType.SPEED) == 1, "the signal need not be a string: an integer 1 gives " + level(be, UpgradeType.SPEED));

			broadcast(helper, "dial", "0");
			helper.assertTrue(level(be, UpgradeType.SPEED) == 0, "a 0 stops it");

			broadcast(helper, "dial", "fast");
			helper.assertTrue(level(be, UpgradeType.SPEED) == 0, "a word is zero");

			broadcast(helper, "dial", "-3");
			helper.assertTrue(level(be, UpgradeType.SPEED) == -3, "a negative signal gives a negative level, as upstream's min does: " + level(be, UpgradeType.SPEED));
		} finally {
			cleanBus();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void onlyTheFirstControlUpgradeCountsAndItCountsInAnySlotOfTheMachine(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		try {
			BlockEntityMachineSolderingStation be = machine(helper);
			be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
			// slot 0 is an ingredient slot, not an upgrade slot: upstream scans the machine's whole inventory
			be.setItem(0, control(PREFIX + "first"));
			be.setItem(10, control(PREFIX + "second"));
			scan(be);
			broadcast(helper, "first", "1");
			broadcast(helper, "second", "3");
			helper.assertTrue(level(be, UpgradeType.SPEED) == 1, "the first control upgrade in slot order wins, from an ingredient slot: " + level(be, UpgradeType.SPEED));

			be.setItem(0, ItemStack.EMPTY);
			scan(be);
			helper.assertTrue(level(be, UpgradeType.SPEED) == 3, "without it the second does: " + level(be, UpgradeType.SPEED));
		} finally {
			cleanBus();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aMachineThatNeverScannedHasNoChannels(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityMachineSolderingStation be = machine(helper);
		be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
		be.setItem(10, control(PREFIX + "unseen"));
		helper.assertTrue(ControlUpgrade.channels(be.upgradeManager).isEmpty(), "no scan, no channels (upstream's list starts empty)");
		helper.assertTrue(level(be, UpgradeType.SPEED) == 0, "and the manager answers its own cache, which is empty");
		scan(be);
		helper.assertTrue(ControlUpgrade.channels(be.upgradeManager).equals(List.of(PREFIX + "unseen")), "after a scan, the channel");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aManagerWithNoMachineOrNoWorldIsLeftAlone(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		BlockEntityMachineSolderingStation be = new BlockEntityMachineSolderingStation(helper.absolutePos(new BlockPos(1, 1, 1)),
				ModBlocks.machine_soldering_station.get().defaultBlockState());
		be.setItem(9, new ItemStack(ModItems.upgrade_speed_2.get()));
		be.setItem(10, control(PREFIX + "nowhere"));
		scan(be);
		helper.assertTrue(be.getLevel() == null && level(be, UpgradeType.SPEED) == 2, "no level, no cap: " + level(be, UpgradeType.SPEED));
		helper.assertTrue(new com.hbm.inventory.UpgradeManagerNT(null).getLevel(UpgradeType.SPEED) == 0, "a manager with no machine at all has no levels and no error");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSwitchTakesTheProviderOutOfTheChain(GameTestHelper helper) {

		if(!on()) { helper.succeed(); return; }

		boolean before = LeafiaFeatures.upgradeControl;
		try {
			BlockEntityMachineSolderingStation be = machine(helper);
			be.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
			be.setItem(10, control(PREFIX + "gone"));
			scan(be);
			helper.assertTrue(level(be, UpgradeType.SPEED) == 0, "on: silence on the channel");
			LeafiaFeatures.upgradeControl = false;
			ControlUpgrade.commonSetup();
			helper.assertTrue(level(be, UpgradeType.SPEED) == 3, "off: the provider is out of the chain");
		} finally {
			LeafiaFeatures.upgradeControl = before;
			ControlUpgrade.commonSetup();
			cleanBus();
		}
		BlockEntityMachineSolderingStation again = machine(helper);
		again.setItem(9, new ItemStack(ModItems.upgrade_speed_3.get()));
		again.setItem(10, control(PREFIX + "back"));
		scan(again);
		helper.assertTrue(level(again, UpgradeType.SPEED) == 0, "and back on afterwards");
		helper.succeed();
	}
}
