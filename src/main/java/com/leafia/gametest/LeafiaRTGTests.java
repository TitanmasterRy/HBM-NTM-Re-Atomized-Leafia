package com.leafia.gametest;

import com.hbm.blockentity.machine.BlockEntityDiFurnaceRTG;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineDiFurnaceRTG;
import com.hbm.config.VersatileConfig;
import com.hbm.items.machine.ItemRTGPellet;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.AddonRTGPelletItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.machines.heat.rtheater.HeaterRTGTE;
import com.leafia.contents.machines.heat.rtheater.container.HeaterRTGMenu;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGMenu;
import com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGTE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The RTG content (D-3020): the Nuclium-279 pellet, the radioisotope thermal heater and its menu, and the
 * RTG blast furnace's fuel-rod slot behind {@code overhauls.rtgFurnaceOverhaul}.
 *
 * <p>A tick is one {@code updateEntity()} inside the body (D-953); every machine is removed before the body
 * ends. The furnace switch is turned on around the one {@code setBlock} that makes the furnace and restored.
 * Mock players are detached from the player list (D-3000).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRTGTests {

	private static final String EMPTY = "empty";
	private static final BlockPos AT = new BlockPos(2, 1, 2);

	// ---- the rig ---------------------------------------------------------------------------

	private static HeaterRTGTE heater(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, LeafiaBlocks.heater_rt.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		BlockEntity be = helper.getBlockEntity(rel);
		helper.assertTrue(be instanceof HeaterRTGTE, "no heater core: " + be);
		return (HeaterRTGTE) be;
	}

	private static LeafiaDiFurnaceRTGTE overhauledFurnace(GameTestHelper helper) {
		boolean was = LeafiaFeatures.rtgFurnaceOverhaul;
		LeafiaFeatures.rtgFurnaceOverhaul = true;
		try {
			helper.setBlock(AT, ModBlocks.machine_difurnace_rtg_off.get());
		} finally {
			LeafiaFeatures.rtgFurnaceOverhaul = was;
		}
		BlockEntity be = helper.getBlockEntity(AT);
		helper.assertTrue(be instanceof LeafiaDiFurnaceRTGTE, "the overhaul did not put the addon furnace in the base block: " + be);
		return (LeafiaDiFurnaceRTGTE) be;
	}

	private static void clear(GameTestHelper helper, BlockPos... rels) {
		for(BlockPos rel : rels) helper.setBlock(rel, Blocks.AIR);
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/** An addon rod at a given temperature, written the way the rod writes it. */
	private static ItemStack rodAt(double heat) {
		LeafiaRodItem rod = LeafiaRods.get("leafRodU38");
		if(rod == null) throw new IllegalStateException("features.rods is off: no addon rod to test with");
		ItemStack stack = new ItemStack(rod);
		CompoundTag t = new CompoundTag();
		t.putDouble("heat", heat);
		LeafiaRodItem.putData(stack, t);
		return stack;
	}

	private static ItemStack pellet() {
		return new ItemStack(LeafiaItems.pellet_rtg_nc279.get());
	}

	// ---- the pellet ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theNuclium279PelletIsUpstreams(GameTestHelper helper) {
		helper.assertTrue(LeafiaFeatures.rtg && LeafiaItems.pellet_rtg_nc279 != null, "features.rtg should default on");
		AddonRTGPelletItem p = LeafiaItems.pellet_rtg_nc279.get();
		Item fissium = LeafiaResourceItems.get(LeafiaResourceItems.RTG_DECAY_PELLET).get();
		helper.assertTrue(p.getHeat() == 120, "heat 120, got " + p.getHeat());
		helper.assertTrue(p.getMaxLifespan() == 8_000_000L, "2,000,000 x 4 ticks, got " + p.getMaxLifespan());
		helper.assertTrue(p.getDoesDecay() && p.getDecayItem().getItem() == fissium, "it decays into the depleted fissium pellet");
		helper.assertTrue(ItemRTGPellet.pelletList.contains(p), "the furnace's pellet list names it");
		boolean mapped = false;
		for(var e : ItemRTGPellet.getRecipeMap().entrySet())
			if(e.getKey().getItem() == p && e.getValue().getItem() == fissium) mapped = true;
		helper.assertTrue(mapped, "and the decay map pairs it with the fissium pellet");
		helper.assertTrue(new ItemStack(p).getMaxStackSize() == 1, "a pellet stacks to one");

		if(VersatileConfig.rtgDecay()) {
			ItemStack spent = pellet();
			CustomData.update(DataComponents.CUSTOM_DATA, spent, tag -> tag.putLong(ItemRTGPellet.DEPLETION_KEY, 0L));
			helper.assertTrue(ItemRTGPellet.handleDecay(spent, p).getItem() == fissium, "a spent pellet becomes the fissium pellet");
		}
		helper.succeed();
	}

	// ---- the heater ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theHeaterCountsTenTUAPelletHeatAndKeepsAThousandthLess(GameTestHelper helper) {
		HeaterRTGTE h = heater(helper, AT);
		h.setItem(0, pellet());
		h.updateEntity();
		helper.assertTrue(h.heatGen == 1200, "one 120-heat pellet is 1,200 TU a tick, got " + h.heatGen);
		helper.assertTrue(h.heatEnergy == 1200, "and the store holds it, got " + h.heatEnergy);
		h.updateEntity();
		helper.assertTrue(h.heatEnergy == 1198 + 1200, "1,200 x 0.999 rounds down to 1,198, plus 1,200, got " + h.heatEnergy);

		h.setItem(0, ItemStack.EMPTY);
		h.heatEnergy = 999;
		h.updateEntity();
		helper.assertTrue(h.heatEnergy == 998 && h.heatGen == 0, "999 keeps 998, got " + h.heatEnergy);
		h.heatEnergy = 100;
		h.updateEntity();
		helper.assertTrue(h.heatEnergy == 99, "100 keeps 99, got " + h.heatEnergy);

		h.setItem(0, pellet());
		h.heatEnergy = HeaterRTGTE.MAX_HEAT;
		h.updateEntity();
		helper.assertTrue(h.heatEnergy == 750_000, "the store stops at 750,000, got " + h.heatEnergy);
		helper.assertTrue(h.getHeatScaled(52) == 52 && h.getHeatGenScaled(52) == (int) (Math.log(1200) * 52 / Math.log(90000)),
				"the bars: the store linear, the rate on a log scale full at 90,000");
		clear(helper, AT);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aRodAddsHeatOffTheTemperatureItHadBeforeTheTick(GameTestHelper helper) {
		helper.assertTrue(HeaterRTGTE.rodHeat(20) == 450, "a cold rod: floor((20/250)^0.54 x 15) = 3, x 150 = 450, got " + HeaterRTGTE.rodHeat(20));
		helper.assertTrue(HeaterRTGTE.rodHeat(250) == 2250, "at 250 degrees, 15 x 150 = 2,250, got " + HeaterRTGTE.rodHeat(250));
		helper.assertTrue(HeaterRTGTE.rodHeat(1000) == 4650, "at 1,000 degrees, 31 x 150 = 4,650, got " + HeaterRTGTE.rodHeat(1000));

		HeaterRTGTE h = heater(helper, AT);
		h.setItem(HeaterRTGTE.ROD_SLOT, rodAt(250));
		h.updateEntity();
		helper.assertTrue(h.heatGen == 2250, "a 250-degree rod and no pellets: 2,250 TU a tick, got " + h.heatGen);
		helper.assertTrue(h.getItem(HeaterRTGTE.ROD_SLOT).getItem() instanceof LeafiaRodItem, "the rod stays in");
		h.setItem(0, pellet());
		h.setItem(HeaterRTGTE.ROD_SLOT, rodAt(250));
		h.updateEntity();
		helper.assertTrue(h.heatGen == 2250 + 1200, "pellets and the rod add, got " + h.heatGen);
		clear(helper, AT);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHeaterTakesEverythingBelowAtEightyFivePercent(GameTestHelper helper) {
		BlockPos up = AT.above();
		HeaterRTGTE lower = heater(helper, AT);
		HeaterRTGTE upper = heater(helper, up);
		lower.heatEnergy = 10_000;
		upper.updateEntity();
		helper.assertTrue(upper.heatEnergy == 8_500, "85% of 10,000 arrives, got " + upper.heatEnergy);
		helper.assertTrue(lower.heatEnergy == 0, "and the source is emptied, left " + lower.heatEnergy);
		lower.heatEnergy = 30;
		lower.useUpHeat(100);
		helper.assertTrue(lower.heatEnergy == 0, "using up more than there is leaves nothing, not less");
		clear(helper, up, AT);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theHeaterMenuIsUpstreamsWithItsShiftClick(GameTestHelper helper) {
		HeaterRTGTE h = heater(helper, AT);
		ServerPlayer player = mockPlayer(helper);
		AbstractContainerMenu menu = h.createMenu(0, player.getInventory(), player);
		helper.assertTrue(menu instanceof HeaterRTGMenu, "the heater's menu: " + menu);
		helper.assertTrue(menu.slots.size() == 16 + 36, "16 machine slots and the player's 36, got " + menu.slots.size());
		helper.assertTrue(menu.slots.get(0).x == 26 && menu.slots.get(0).y == 22 && menu.slots.get(14).x == 98 && menu.slots.get(14).y == 58,
				"the pellets from (26, 22) to (98, 58)");
		helper.assertTrue(menu.slots.get(15).x == 4 && menu.slots.get(15).y == 40 && menu.slots.get(15).getContainerSlot() == HeaterRTGTE.ROD_SLOT,
				"the rod at (4, 40)");
		helper.assertTrue(menu.slots.get(16).x == 8 && menu.slots.get(16).y == 94 && menu.slots.get(51).y == 152, "the inventory from y 94, hotbar 152");

		// upstream's shift-click: the rod slot empties into the pellet slots, and nothing reaches the rod slot from the inventory
		h.setItem(HeaterRTGTE.ROD_SLOT, rodAt(20));
		menu.quickMoveStack(player, 15);
		helper.assertTrue(h.getItem(HeaterRTGTE.ROD_SLOT).isEmpty() && h.getItem(0).getItem() instanceof LeafiaRodItem,
				"a shift-click out of the rod slot lands in pellet slot 0");
		h.setItem(0, ItemStack.EMPTY);
		menu.slots.get(16).set(rodAt(20));
		menu.quickMoveStack(player, 16);
		helper.assertTrue(h.getItem(HeaterRTGTE.ROD_SLOT).isEmpty() && h.getItem(0).getItem() instanceof LeafiaRodItem,
				"and a rod shift-clicked from the inventory goes to a pellet slot too");
		clear(helper, AT);
		helper.succeed();
	}

	// ---- the furnace overhaul --------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theFurnaceOverhaulDefaultsOffAndPutsTheRodSlotInBothBlocks(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.rtgFurnaceOverhaul, "the furnace overhaul must default off");
		helper.setBlock(AT, ModBlocks.machine_difurnace_rtg_off.get());
		BlockEntity base = helper.getBlockEntity(AT);
		helper.assertTrue(base != null && base.getClass() == BlockEntityDiFurnaceRTG.class, "off, the base furnace: " + base);
		clear(helper, AT);

		LeafiaDiFurnaceRTGTE f = overhauledFurnace(helper);
		helper.assertTrue(!LeafiaFeatures.rtgFurnaceOverhaul, "the switch was not restored");
		helper.assertTrue(f.getType() == LeafiaBlockEntities.DIFURNACE_RTG.get() && f.getContainerSize() == 10, "the addon furnace, ten slots");
		ServerPlayer player = mockPlayer(helper);
		AbstractContainerMenu menu = f.createMenu(0, player.getInventory(), player);
		helper.assertTrue(menu instanceof LeafiaDiFurnaceRTGMenu, "its menu is the addon's: " + menu);
		helper.assertTrue(menu.slots.size() == 9 + 36 + 1, "the base furnace's nine, the player's 36, then the rod");
		helper.assertTrue(menu.slots.get(LeafiaDiFurnaceRTGMenu.ROD_INDEX).x == 4 && menu.slots.get(LeafiaDiFurnaceRTGMenu.ROD_INDEX).y == 36
				&& menu.slots.get(LeafiaDiFurnaceRTGMenu.ROD_INDEX).getContainerSlot() == LeafiaDiFurnaceRTGTE.ROD_SLOT, "the rod at (4, 36), menu index 45");

		menu.slots.get(9).set(rodAt(20));
		menu.quickMoveStack(player, 9);
		helper.assertTrue(f.getItem(LeafiaDiFurnaceRTGTE.ROD_SLOT).getItem() instanceof LeafiaRodItem, "a rod shift-clicked from the inventory goes to the rod slot");
		menu.quickMoveStack(player, LeafiaDiFurnaceRTGMenu.ROD_INDEX);
		helper.assertTrue(f.getItem(LeafiaDiFurnaceRTGTE.ROD_SLOT).isEmpty(), "and back out to the inventory");

		// running, the furnace swaps to its lit block and keeps the addon's block entity
		MachineDiFurnaceRTG.updateBlockState(true, helper.getLevel(), helper.absolutePos(AT));
		helper.assertTrue(helper.getBlockState(AT).is(ModBlocks.machine_difurnace_rtg_on.get()) && helper.getBlockEntity(AT) == f,
				"lit, it is the same addon furnace");
		clear(helper, AT);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFurnaceRodAddsItsHeatOnlyWhileTheFurnaceCanWork(GameTestHelper helper) {
		helper.assertTrue(LeafiaDiFurnaceRTGTE.rodHeat(20) == 3 && LeafiaDiFurnaceRTGTE.rodHeat(250) == 15 && LeafiaDiFurnaceRTGTE.rodHeat(1000) == 31,
				"the furnace's bonus: 3, 15, 31");

		LeafiaDiFurnaceRTGTE f = overhauledFurnace(helper);
		f.setItem(LeafiaDiFurnaceRTGTE.ROD_SLOT, rodAt(250));
		f.updateEntity();
		helper.assertTrue(f.getRodBonus() == 0 && f.progress == 0, "nothing to smelt: no bonus, no progress");

		f.setItem(0, new ItemStack(Items.IRON_INGOT));
		f.setItem(1, new ItemStack(Items.COAL));
		helper.assertTrue(f.canProcess(), "iron and coal make steel");
		f.setItem(LeafiaDiFurnaceRTGTE.ROD_SLOT, rodAt(250));
		f.updateEntity();
		helper.assertTrue(f.getRodBonus() == 15 && f.getPower() == 15, "a 250-degree rod is 15 heat, got " + f.getRodBonus() + "/" + f.getPower());
		helper.assertTrue(f.progress == 15, "and 15 heat is the base furnace's minimum: progress 15, got " + f.progress);

		// a cold rod is 3 heat, which a CE furnace would run on and this one, at 15 minimum, does not
		f.setItem(LeafiaDiFurnaceRTGTE.ROD_SLOT, rodAt(20));
		f.updateEntity();
		helper.assertTrue(f.getRodBonus() == 3 && f.getPower() == 3 && f.progress == 0, "a cold rod alone stalls it, got " + f.getPower() + "/" + f.progress);
		clear(helper, AT);
		helper.succeed();
	}
}
