package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.custom_hbm.explosion.LCEExplosionNT;
import com.custom_hbm.explosion.LCEExplosionNT.LCEExAttrib;
import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK3.ATEntry;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemLens;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.traits.FT_DFCFuel;
import com.leafia.contents.machines.powercores.dfc.DFCCEStats;
import com.leafia.contents.machines.powercores.dfc.LCEItemLens;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorMenu;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.contents.machines.powercores.dfc.core.CoreMenu;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore.Cores;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore.packetKeys;
import com.leafia.overwrite_contents.other.LCEItemCatalyst;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's Dark Fusion Core overhaul (DFC-2, D-3011): how it is put into the base block and only
 * while its switch is on, the core's arithmetic on one tick, its catalysts' melt, the overload's
 * warning, the crucifixes, the pulse detonator's count, the two menus, the lenses, NTM-CE's numbers,
 * and the explosion it blows up with.
 *
 * <p>Every test that switches {@code LeafiaFeatures.dfcOverhaul} on does it around the one
 * {@code setBlock} that makes the core and restores the value it found. Every
 * test that runs a machine does it inside one body by calling {@code updateEntity()}, which is a tick
 * (D-953), and removes what it built, because a core left standing keeps ticking. The numbers were
 * worked out by running upstream's expressions in a scratch Java program (doubles and floats as
 * upstream declares them), not copied out of a run.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDFCTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	// ---- the rig ---------------------------------------------------------------------------

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

	/** Places the base mod's core block with the overhaul on for that one placement, and restores the switch. */
	private static LeafiaCoreTE overhauledCore(GameTestHelper helper, BlockPos rel) {
		boolean was = LeafiaFeatures.dfcOverhaul;
		LeafiaFeatures.dfcOverhaul = true;
		try {
			helper.setBlock(rel, ModBlocks.dfc_core.get());
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
		}
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		helper.assertTrue(be instanceof LeafiaCoreTE, "the overhaul did not put the addon core in the base block: " + be);
		return (LeafiaCoreTE) be;
	}

	private static void clear(GameTestHelper helper, BlockPos... rels) {
		for(BlockPos rel : rels) helper.setBlock(rel, Blocks.AIR);
	}

	private static boolean near(double a, double b, double eps) {
		return Math.abs(a - b) <= eps;
	}

	// ---- the switch ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOffTheBaseBlockMakesTheBaseCore(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.dfcOverhaul, "the overhaul must default off");
		BlockPos at = new BlockPos(1, 2, 1);
		helper.setBlock(at, ModBlocks.dfc_core.get());
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
		helper.assertTrue(be != null && be.getClass() == BlockEntityCore.class, "expected the base mod's own core, got " + be);
		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void withTheOverhaulOnTheBaseBlockMakesTheAddonCore(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		helper.assertTrue(!LeafiaFeatures.dfcOverhaul, "the switch was not restored");
		helper.assertTrue(core.getType() == LeafiaBlockEntities.DFC_CORE.get(), "the core is not on the addon's type");
		helper.assertTrue(core.getBlockState().is(ModBlocks.dfc_core.get()), "the block must stay the base mod's");
		helper.assertTrue(core.createMenu(0, mockPlayer(helper).getInventory(), mockPlayer(helper)) instanceof CoreMenu, "the core's menu is the addon's");
		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOverhauledCoreRatesFuelByItsTraitAndNothingElse(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		float expected = Fluids.DEUTERIUM.hasTrait(FT_DFCFuel.class) ? Fluids.DEUTERIUM.getTrait(FT_DFCFuel.class).getModifier() : 0F;
		helper.assertTrue(core.getFuelEfficiency(Fluids.DEUTERIUM) == expected, "deuterium rates " + core.getFuelEfficiency(Fluids.DEUTERIUM) + ", its trait says " + expected);
		helper.assertTrue(core.getFuelEfficiency(Fluids.WATER) == (Fluids.WATER.hasTrait(FT_DFCFuel.class) ? Fluids.WATER.getTrait(FT_DFCFuel.class).getModifier() : 0F), "water");
		clear(helper, at);
		helper.succeed();
	}

	// ---- NTM-CE's numbers ------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theCatalystsMeltAtUpstreamsPoints(GameTestHelper helper) {
		Object[][] rows = {
				{ModItems.ams_catalyst_iron, 2900}, {ModItems.ams_catalyst_copper, 2250}, {ModItems.ams_catalyst_aluminium, 2250},
				{ModItems.ams_catalyst_lithium, 600}, {ModItems.ams_catalyst_beryllium, 1287}, {ModItems.ams_catalyst_tungsten, 3422},
				{ModItems.ams_catalyst_cobalt, 1495}, {ModItems.ams_catalyst_niobium, 2900}, {ModItems.ams_catalyst_cerium, 2900},
				{ModItems.ams_catalyst_thorium, 800}, {ModItems.ams_catalyst_strontium, 3200}, {ModItems.ams_catalyst_caesium, 1952},
				{ModItems.ams_catalyst_schrabidium, 3000}, {ModItems.ams_catalyst_euphemium, 4000}, {ModItems.ams_catalyst_dineutronium, 6000},
		};
		for(Object[] row : rows) {
			@SuppressWarnings("unchecked")
			Item item = ((net.neoforged.neoforge.registries.DeferredItem<Item>) row[0]).get();
			int got = LCEItemCatalyst.getMelting(new ItemStack(item));
			helper.assertTrue(got == (int) row[1], item + " melts at " + got + ", upstream " + row[1]);
		}
		helper.assertTrue(LCEItemCatalyst.getMelting(new ItemStack(Items.DIRT)) == 1500000, "a non-catalyst answers the cap");
		helper.assertTrue(LCEItemCatalyst.getMelting(new ItemStack(ModItems.ams_catalyst_blank.get())) == 1500000, "the blank catalyst is not a catalyst");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCoreReadsNtmCesCatalystsAndCores(GameTestHelper helper) {
		// (item, powerAbs, powerMod, heatMod, fuelMod) - read out of NTM-CE 1.5.1.8's ModItems bytecode
		Object[][] rows = {
				{ModItems.ams_catalyst_iron, 10L, 0.5F, 1.5F, 1.5F}, {ModItems.ams_catalyst_copper, 100L, 0.6F, 1.2F, 0.6F},
				{ModItems.ams_catalyst_aluminium, 250L, 0.7F, 0.85F, 0.85F}, {ModItems.ams_catalyst_lithium, 500L, 0.8F, 0.75F, 1.15F},
				{ModItems.ams_catalyst_beryllium, 1000L, 0.9F, 1.15F, 0.75F}, {ModItems.ams_catalyst_tungsten, 5000L, 1.0F, 1.0F, 0.95F},
				{ModItems.ams_catalyst_cobalt, 10000L, 1.02F, 0.95F, 1.0F}, {ModItems.ams_catalyst_niobium, 25000L, 1.05F, 1.15F, 1.0F},
				{ModItems.ams_catalyst_cerium, 50000L, 1.05F, 1.0F, 1.15F}, {ModItems.ams_catalyst_thorium, 100000L, 1.1F, 0.95F, 1.2F},
				{ModItems.ams_catalyst_strontium, 200000L, 1.15F, 0.9F, 1.3F}, {ModItems.ams_catalyst_caesium, 400000L, 1.2F, 0.85F, 1.4F},
				{ModItems.ams_catalyst_schrabidium, 600000L, 1.3F, 0.7F, 1.25F}, {ModItems.ams_catalyst_euphemium, 800000L, 1.5F, 1.25F, 0.7F},
				{ModItems.ams_catalyst_dineutronium, 1000000L, 2.0F, 1.5F, 2.0F},
		};
		for(Object[] row : rows) {
			@SuppressWarnings("unchecked")
			ItemStack stack = new ItemStack(((net.neoforged.neoforge.registries.DeferredItem<Item>) row[0]).get());
			helper.assertTrue(DFCCEStats.getPowerAbs(stack) == (long) row[1]
					&& DFCCEStats.getPowerMod(stack) == (float) row[2]
					&& DFCCEStats.getHeatMod(stack) == (float) row[3]
					&& DFCCEStats.getFuelMod(stack) == (float) row[4], stack + " is not NTM-CE's");
		}
		helper.assertTrue(DFCCEStats.getPowerMod(new ItemStack(Items.DIRT)) == 1F && DFCCEStats.getPowerAbs(new ItemStack(Items.DIRT)) == 0L, "a non-catalyst is CE's 0 and 1");

		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		Object[][] cores = {
				{ModItems.ams_core_sing.get(), 500, 0.8F, 1.5F}, {ModItems.ams_core_wormhole.get(), 650, 1.5F, 0.8F},
				{ModItems.ams_core_eyeofharmony.get(), 800, 1.5F, 2.0F}, {ModItems.ams_core_thingy.get(), 2500, 0.7F, 0.7F},
				{ModItems.glitch.get(), 7500, 0.4F, 3F},
		};
		for(Object[] row : cores) {
			core.setItem(1, new ItemStack((Item) row[0]));
			helper.assertTrue(core.getCorePower() == (int) row[1] && core.getCoreHeat() == (float) row[2] && core.getCoreFuel() == (float) row[3],
					row[0] + " reads " + core.getCorePower() + "/" + core.getCoreHeat() + "/" + core.getCoreFuel());
		}
		core.setItem(1, ItemStack.EMPTY);
		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCoreTypesAndPacketKeysAreUpstreamsOrder(GameTestHelper helper) {
		helper.assertTrue(List.of(Cores.values()).toString().equals("[ams_core_sing, ams_core_wormhole, ams_core_eyeofharmony, glitch, ams_core_thingy]"), "Cores order");
		helper.assertTrue(packetKeys.DETONATION.key == 16 && packetKeys.DET_TIMER.key == 17 && packetKeys.HASCORE.key == 15 && packetKeys.PLAY_SOUND.key == 12, "packet keys");
		helper.assertTrue(LeafiaCoreTE.coreOrdinal(new ItemStack(ModItems.glitch.get())) == 3, "the glitch is core 3");
		helper.assertTrue(LeafiaCoreTE.coreOrdinal(new ItemStack(Items.DIRT)) == null, "dirt is no core");
		helper.assertTrue(IMixinTileEntityCore.failsafeLevel == 250000000.0 && IMixinTileEntityCore.maxEnergy == 10_000.0, "the two energy constants");
		helper.assertTrue(LeafiaPacketTypes.EXPLOSION_NT == LeafiaPacketTypes.DFC_SHOCK + 1, "the DFC's two packets are appended together");
		helper.succeed();
	}

	// ---- one tick --------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aColdCoreTurnsABeamIntoContainedEnergyAndHeat(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		core.setItem(0, new ItemStack(ModItems.ams_catalyst_iron.get()));
		core.setItem(1, new ItemStack(ModItems.ams_core_sing.get()));
		core.setItem(2, new ItemStack(ModItems.ams_catalyst_copper.get()));
		core.tanks[0].setTankType(Fluids.DEUTERIUM);
		core.tanks[1].setTankType(Fluids.TRITIUM);
		core.tanks[0].setFill(128000);
		core.tanks[1].setFill(128000);
		core.setDFCIncomingSpk(1);

		core.updateEntity();

		// consumption = ceil(sqrt(1 x 0.9 x 1.5)) = 2
		helper.assertTrue(core.tanks[0].getFill() == 127998 && core.tanks[1].getFill() == 127998,
				"each tank pays ceil(sqrt(beam x fuelMod x coreFuel)) = 2: " + core.tanks[0].getFill() + "/" + core.tanks[1].getFill());
		helper.assertTrue(core.getDFCMeltingPoint() == 2250, "the lower of 2,900 and 2,250: " + core.getDFCMeltingPoint());
		helper.assertTrue(near(core.getDFCContainedEnergy(), 0.15607428654125127, 1e-12), "addition0 = " + core.getDFCContainedEnergy());
		// target 550,990.08, a square-root step of 742.29, under the random limit of 1,000+
		helper.assertTrue(near(core.getDFCTemperature(), 742.2870582605399, 1e-9), "temperature " + core.getDFCTemperature());
		helper.assertTrue(core.isDFCHasCore() && core.color == core.getDFCColorCatalyst(), "both catalysts in: hasCore, and the colour is the catalysts' average at no wear");
		helper.assertTrue(LeafiaCoreTE.getCatalystDamage(core.getItem(0)) == 0, "a catalyst under its melting point regenerates, clamped at 0");
		helper.assertTrue(core.getDFCIncomingSpk() == 0 && core.isDFCWasBoosted(), "the beam is spent each tick and remembered as a boost");

		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCatalystMeltsAHalfATickAboveItsPointAndRegeneratesASixthBelow(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		core.setItem(0, new ItemStack(ModItems.ams_catalyst_lithium.get()));
		core.setItem(1, new ItemStack(ModItems.ams_core_sing.get()));
		core.setItem(2, new ItemStack(ModItems.ams_catalyst_lithium.get()));
		core.tanks[0].setFill(128000);
		core.tanks[1].setFill(128000);
		LeafiaCoreTE.setCatalystDamage(core.getItem(0), 100);
		core.setDFCTemperature(700);

		core.updateEntity();
		// No beam: the target is 0, the step sqrt(700) down to 673.54, still past lithium's 600.
		helper.assertTrue(near(core.getDFCTemperature(), 673.5424868893541, 1e-9), "temperature " + core.getDFCTemperature());
		helper.assertTrue(LeafiaCoreTE.getCatalystDamage(core.getItem(2)) == 0.5, "melting: +5/10 a tick, got " + LeafiaCoreTE.getCatalystDamage(core.getItem(2)));
		helper.assertTrue(LeafiaCoreTE.getCatalystDamage(core.getItem(0)) == 100, "clamped at 100");
		helper.assertTrue(core.getDFCOverloadTimer() == 1, "a melted catalyst above 100 degrees starts the overload: " + core.getDFCOverloadTimer());
		helper.assertTrue(core.getLevel().getBlockState(core.getBlockPos()).is(ModBlocks.dfc_core.get()), "the overload warns for six seconds before it blows");

		LeafiaCoreTE.setCatalystDamage(core.getItem(0), 50);
		core.setDFCTemperature(300);
		core.updateEntity();
		helper.assertTrue(near(LeafiaCoreTE.getCatalystDamage(core.getItem(0)), 50 - 5 / 30.0, 1e-12), "regenerating: -5/30 a tick");
		helper.assertTrue(near(LeafiaCoreTE.getCatalystDamage(core.getItem(2)), 0.5 - 5 / 30.0, 1e-12), "regenerating below 600");
		helper.assertTrue(core.getDFCOverloadTimer() == 0, "the overload stands down when no catalyst is melted");

		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aCollapsingCoreTakesBothCrucifixes(GameTestHelper helper) {
		// The 16-high template: the line of sight is taken from six blocks above the core (D-967), and
		// the 5-high one's ceiling is in the way.
		BlockPos at = new BlockPos(8, 1, 8);
		LeafiaCoreTE core = overhauledCore(helper, at);
		core.tanks[0].setFill(1000);
		core.tanks[1].setFill(1000);
		BlockPos abs = helper.absolutePos(at);

		// fix_tool first: D-3075, upstream's loop skipped it before the branch that names it.
		ItemEntity tool = new ItemEntity(helper.getLevel(), abs.getX() + 1.5, abs.getY() + 0.5, abs.getZ() + 0.5, new ItemStack(LeafiaItems.fix_tool.get()));
		tool.setNoGravity(true);
		helper.getLevel().addFreshEntity(tool);
		core.setDFCTemperature(500);
		core.setDFCCollapsing(0.1);
		core.setDFCStabilizers(8); // 80 - 8 x 10 = 0: the survival crucifix cannot fail
		core.updateEntity();
		helper.assertTrue(!tool.isAlive(), "the fix_tool is consumed (D-3075)");
		helper.assertTrue(core.getDFCTemperature() == 0 && core.tanks[0].getFill() == 0, "and resets the core");
		tool.discard();
		core.tanks[0].setFill(1000);
		core.tanks[1].setFill(1000);

		ItemEntity survival = new ItemEntity(helper.getLevel(), abs.getX() + 1.5, abs.getY() + 0.5, abs.getZ() + 0.5, new ItemStack(LeafiaItems.fix_survival.get()));
		survival.setNoGravity(true);
		helper.getLevel().addFreshEntity(survival);
		core.setDFCTemperature(500);
		core.setDFCStabilizers(8);
		core.updateEntity();
		helper.assertTrue(!survival.isAlive(), "the survival crucifix is consumed");
		helper.assertTrue(core.getDFCTemperature() == 0 && core.getDFCContainedEnergy() == 0, "and resets the core: " + core.getDFCTemperature());
		helper.assertTrue(core.tanks[0].getFill() == 0 && core.tanks[1].getFill() == 0, "and empties both tanks");

		for(ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(6))) e.discard();
		clear(helper, at);
		helper.succeed();
	}

	// ---- the pulse detonator ---------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theDetonationRadiusIsUpstreamsTable(GameTestHelper helper) {
		int[] expected = {0, 0, 130, 260, 390, 520, 650, 780, 910, 1170, 1430};
		for(int p = 0; p <= 10; p++)
			helper.assertTrue(LeafiaCoreTE.detonationRadius(p) == expected[p], p + " detonators: " + LeafiaCoreTE.detonationRadius(p) + ", expected " + expected[p]);
		helper.assertTrue(CoreDetonatorTE.consumption == 5_000_000_000_000L, "five trillion HE a tick");
		helper.assertTrue(LeafiaCoreTE.DETONATION_TICKS == 600 && LeafiaCoreTE.DETONATION_WARNING == 480, "thirty seconds, warned at twenty-four");
		helper.succeed();
	}

	private static CoreDetonatorTE pulser(GameTestHelper helper, BlockPos rel, Direction facing) {
		helper.setBlock(rel, LeafiaBlocks.dfc_pulser.get().defaultBlockState().setValue(CoreComponent.FACING, facing));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		helper.assertTrue(be instanceof CoreDetonatorTE, "no detonator at " + rel + ": " + be);
		return (CoreDetonatorTE) be;
	}

	@GameTest(template = EMPTY)
	public static void theDetonatorAimsAlongItsFacingAndNeedsAClearLine(GameTestHelper helper) {
		BlockPos corePos = new BlockPos(3, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, corePos);
		CoreDetonatorTE pulser = pulser(helper, new BlockPos(0, 2, 1), Direction.EAST);
		pulser.updateEntity();
		helper.assertTrue(pulser.leafia$getTargetPosition().equals(helper.absolutePos(new BlockPos(1, 2, 1))), "the target is one block along the facing: " + pulser.leafia$getTargetPosition());
		helper.assertTrue(pulser.leafia$lastGetCore() == core, "the line reaches the core through air");
		helper.assertTrue(core.getDFCPulsers().contains(pulser), "an armed detonator registers with its core");
		helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
		helper.assertTrue(pulser.leafia$getCore(50) == null, "a block in between ends the line");
		clear(helper, corePos, new BlockPos(0, 2, 1), new BlockPos(2, 2, 1));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theKeypadTakesTheFirstCodeAndOnlyThatOne(GameTestHelper helper) {
		CoreDetonatorTE pulser = pulser(helper, new BlockPos(1, 2, 1), Direction.EAST);
		ServerPlayer player = mockPlayer(helper);
		helper.assertTrue(pulser.tryCode("1234"), "a fresh detonator takes the first code");
		helper.assertTrue(!pulser.tryCode("9999") && pulser.tryCode("1234"), "then only that code");
		pulser.isOn = true;
		pulser.onReceivePacketServer((byte) 1, false, player);
		helper.assertTrue(pulser.isOn, "a command without the code is ignored");
		pulser.onReceivePacketServer((byte) 0, "1234", player);
		pulser.onReceivePacketServer((byte) 1, false, player);
		helper.assertTrue(!pulser.isOn, "the code authorises the command after it");
		pulser.updateEntity();
		helper.assertTrue(pulser.isAuthorized(player), "and the player stays authorised for five ticks (D-3076; upstream forgot at once)");
		pulser.forgetOldAuthorizations(helper.getLevel().getGameTime() + 6);
		helper.assertTrue(!pulser.isAuthorized(player), "then is forgotten");
		CompoundTag tag = pulser.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(tag.getString("code").equals("1234") && !tag.getBoolean("isOn") && tag.contains("x1"), "code, switch and target are saved: " + tag);
		clear(helper, new BlockPos(1, 2, 1));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCountRunsWhileEveryDetonatorPaysAndStopsWhenOneCannot(GameTestHelper helper) {
		BlockPos corePos = new BlockPos(3, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, corePos);
		CoreDetonatorTE pulser = pulser(helper, new BlockPos(0, 2, 1), Direction.EAST);
		pulser.power = pulser.getMaxPower(); // five ticks of the count
		pulser.updateEntity();
		core.updateEntity();
		helper.assertTrue(core.getDetonationTimer() == 0 && core.getDFCPulsers().isEmpty(), "not armed, no count, and the list is spent each tick");

		core.setDetonation(true);
		for(int t = 1; t <= 5; t++) {
			pulser.updateEntity();
			core.updateEntity();
			helper.assertTrue(core.getDetonationTimer() == t, "tick " + t + ": the count is " + core.getDetonationTimer());
			helper.assertTrue(pulser.power == CoreDetonatorTE.consumption * (5 - t), "tick " + t + ": the detonator holds " + pulser.power);
		}
		pulser.updateEntity();
		core.updateEntity();
		helper.assertTrue(!core.getDetonation() && core.getDetonationTimer() == 0, "an empty detonator cancels the count");

		// The last tick before the blast: 25,000 degrees, and the block still stands.
		pulser.power = pulser.getMaxPower();
		core.setDetonation(true);
		pulser.updateEntity();
		core.updateEntity();
		core.setDetonationTimer(LeafiaCoreTE.DETONATION_TICKS - 2);
		pulser.updateEntity();
		core.updateEntity();
		helper.assertTrue(core.getDetonationTimer() == LeafiaCoreTE.DETONATION_TICKS - 1 && core.getDFCTemperature() >= 25000, "one tick before the blast the core jumps 25,000: " + core.getDFCTemperature());
		helper.assertTrue(helper.getLevel().getBlockState(core.getBlockPos()).is(ModBlocks.dfc_core.get()), "not yet");

		clear(helper, corePos, new BlockPos(0, 2, 1));
		helper.succeed();
	}

	// ---- the menus -------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theCoreMenuIsUpstreamsColumnAndItsShiftClickLeavesTheMachine(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		LeafiaCoreTE core = overhauledCore(helper, at);
		ServerPlayer player = mockPlayer(helper);
		CoreMenu menu = new CoreMenu(0, player.getInventory(), core);
		helper.assertTrue(menu.slots.size() == 39, "3 + 36 slots");
		helper.assertTrue(menu.slots.get(0).x == 80 && menu.slots.get(0).y == 7 && menu.slots.get(1).y == 39 && menu.slots.get(2).y == 71, "the column at x 80");
		helper.assertTrue(menu.slots.get(3).x == 8 && menu.slots.get(3).y == 122 && menu.slots.get(30).y == 180, "inventory at 122, hotbar at 180");
		for(int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
		core.setItem(0, new ItemStack(ModItems.ams_catalyst_iron.get()));
		menu.quickMoveStack(player, 0);
		helper.assertTrue(core.getItem(2).isEmpty() && !core.getItem(0).isEmpty(), "with a full inventory the stack stays and never reaches the machine's own slot 2 (D-3076; upstream's range began there)");
		core.clearContent();
		clear(helper, at);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theDetonatorMenuIsOnlyThePlayerAndMovesNothing(GameTestHelper helper) {
		CoreDetonatorTE pulser = pulser(helper, new BlockPos(1, 2, 1), Direction.EAST);
		ServerPlayer player = mockPlayer(helper);
		CoreDetonatorMenu menu = new CoreDetonatorMenu(0, player.getInventory(), pulser);
		helper.assertTrue(menu.slots.size() == 36 && menu.slots.get(0).y == 88 && menu.slots.get(27).y == 146, "36 player slots at 88 and 146");
		player.getInventory().items.set(9, new ItemStack(Items.DIRT, 3));
		helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && player.getInventory().items.get(9).getCount() == 3, "shift-click does nothing");
		clear(helper, new BlockPos(1, 2, 1));
		helper.succeed();
	}

	// ---- the lenses ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void theFiveLensesAreUpstreams(GameTestHelper helper) {
		Object[][] rows = {
				{LeafiaItems.ams_focus_blank, 400000L, 0.5F, 1.75F, 1F},
				{LeafiaItems.ams_focus_limiter, 2500000000L, 1.5F, 1.75F, 0.8F},
				{LeafiaItems.ams_focus_booster, 100000000L, 0.8F, 0.5F, 1.35F},
				{LeafiaItems.ams_focus_omega, 1000000000L, 5.0F, 10.0F, 3.5F},
				{LeafiaItems.ams_focus_safe, 5000000000L, 8F, 4.5F, 0.1F},
		};
		for(Object[] row : rows) {
			@SuppressWarnings("unchecked")
			LCEItemLens lens = ((net.neoforged.neoforge.registries.DeferredItem<LCEItemLens>) row[0]).get();
			helper.assertTrue(lens.maxDamage == (long) row[1] && lens.fieldMod == (float) row[2] && lens.drainMod == (float) row[3] && lens.energyMod == (float) row[4],
					lens + " is not upstream's");
		}
		ItemStack base = new ItemStack(ModItems.ams_lens.get());
		helper.assertTrue(base.getItem() instanceof ItemLens && LCEItemLens.fieldMod(base) == 1F && LCEItemLens.drainMod(base) == 1F && LCEItemLens.energyMod(base) == 1F, "the base lens is 1/1/1");
		helper.assertTrue(LCEItemLens.fieldText(0.5F).equals("§c-50.0%") && LCEItemLens.fieldText(1.5F).equals("§a+50.0%"), "field text");
		helper.assertTrue(LCEItemLens.energyText(1F).equals("§8+0.0%") && LCEItemLens.energyText(0.1F).equals("§8-90.0%") && LCEItemLens.energyText(3.5F).equals("§6+250.0%"), "energy text");
		helper.assertTrue(LCEItemLens.drainText(1.75F).equals("§c+75.0%") && LCEItemLens.drainText(0.5F).equals("§a-50.0%"), "drain text");
		helper.succeed();
	}

	// ---- the explosion ---------------------------------------------------------------------

	private static void cube(GameTestHelper helper, BlockPos min, int size, net.minecraft.world.level.block.Block block) {
		for(int x = 0; x < size; x++) for(int y = 0; y < size; y++) for(int z = 0; z < size; z++)
			helper.setBlock(min.offset(x, y, z), block);
	}

	private static void clearCube(GameTestHelper helper, BlockPos min, int size) {
		cube(helper, min, size, Blocks.AIR);
		for(FallingBlockEntity e : helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(helper.absolutePos(min)).inflate(size + 4))) e.discard();
		for(ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(min)).inflate(size + 4))) e.discard();
	}

	@GameTest(template = BIG)
	public static void anIgnoredCellSurvivesItsOwnBlast(GameTestHelper helper) {
		BlockPos min = new BlockPos(4, 3, 4);
		BlockPos centre = min.offset(1, 1, 1);
		for(boolean ignore : new boolean[] {false, true}) {
			cube(helper, min, 3, Blocks.DIRT);
			BlockPos abs = helper.absolutePos(centre);
			LCEExplosionNT nt = new LCEExplosionNT(helper.getLevel(), null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 2);
			nt.addAllAttrib(List.of(LCEExAttrib.NODROP, LCEExAttrib.NOHURT, LCEExAttrib.NOSOUND, LCEExAttrib.NOPARTICLE));
			if(ignore) nt.ignoreBlockPoses.add(abs);
			nt.explode();
			boolean centreStands = helper.getLevel().getBlockState(abs).is(Blocks.DIRT);
			helper.assertTrue(centreStands == ignore, (ignore ? "an ignored" : "an unignored") + " centre " + (centreStands ? "stood" : "went"));
			for(Direction d : Direction.values())
				helper.assertTrue(helper.getLevel().getBlockState(abs.relative(d)).isAir(), "the " + d + " neighbour should go");
			clearCube(helper, min, 3);
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aMaximumResistanceStopsTheRayDead(GameTestHelper helper) {
		BlockPos target = new BlockPos(8, 3, 8);
		for(float max : new float[] {-1F, 0.5F}) {
			helper.setBlock(target, Blocks.DIRT);
			BlockPos abs = helper.absolutePos(target);
			LCEExplosionNT nt = new LCEExplosionNT(helper.getLevel(), null, abs.getX() - 1.5, abs.getY() + 0.5, abs.getZ() + 0.5, 3);
			nt.addAllAttrib(List.of(LCEExAttrib.NODROP, LCEExAttrib.NOHURT, LCEExAttrib.NOSOUND, LCEExAttrib.NOPARTICLE));
			nt.maxExplosionResistance = max;
			nt.explode();
			boolean stands = helper.getLevel().getBlockState(abs).is(Blocks.DIRT);
			// dirt is 0.5, and 0.5 x 3/5 = 0.3 is below it
			helper.assertTrue(stands == (max >= 0), "max " + max + ": the dirt " + (stands ? "stood" : "went"));
			helper.setBlock(target, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aLandslideBlastTakesOnlyTheSurfaceItReachesAndDropsNothing(GameTestHelper helper) {
		BlockPos min = new BlockPos(4, 2, 4);
		for(boolean fall : new boolean[] {false, true}) {
			cube(helper, min, 7, Blocks.DIRT);
			BlockPos abs = helper.absolutePos(min.offset(3, 3, 3));
			LCEExplosionNT nt = new LCEExplosionNT(helper.getLevel(), null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 8);
			nt.addAllAttrib(List.of(LCEExAttrib.NODROP, LCEExAttrib.NOHURT, LCEExAttrib.NOSOUND, LCEExAttrib.NOPARTICLE));
			if(fall) nt.addAttrib(LCEExAttrib.DFC_FALL);
			nt.ignoreBlockPoses.add(abs);
			nt.doNTExplosionA();
			if(fall) {
				helper.assertTrue(nt.fallBlocks != null, "candidates were gathered");
				Set<BlockPos> falls = Set.copyOf(nt.fallBlocks);
				for(Direction d : Direction.values()) {
					helper.assertTrue(falls.contains(abs.relative(d)), "the first cell out on " + d + " follows air, so it is a candidate");
					helper.assertTrue(!falls.contains(abs.relative(d, 2)), "the second cell out on " + d + " follows two solid steps, so it is not");
				}
			}
			nt.doNTExplosionB();
			for(Direction d : Direction.values()) {
				helper.assertTrue(helper.getLevel().getBlockState(abs.relative(d)).isAir(), (fall ? "landslide" : "plain") + ": the first cell on " + d + " goes");
				boolean second = helper.getLevel().getBlockState(abs.relative(d, 2)).is(Blocks.DIRT);
				// Reached in both runs (8 x 0.7 less ~1.85 to get there leaves power); only the plain blast breaks it.
				helper.assertTrue(second == fall, (fall ? "landslide" : "plain") + ": the second cell on " + d + (second ? " stood" : " went"));
			}
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(10)).isEmpty(), "nothing drops");
			clearCube(helper, min, 7);
		}
		helper.succeed();
	}

	// ---- the jammer ------------------------------------------------------------------------

	@GameTest(template = EMPTY)
	public static void aJammerIsAProtectionEntryWithin300BlocksAndExpiredOnesArePruned(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
		long now = helper.getLevel().getGameTime();
		ATEntry near = new ATEntry(helper.getLevel().dimension(), abs.getX() + 100, abs.getY(), abs.getZ());
		ATEntry far = new ATEntry(helper.getLevel().dimension(), abs.getX() + 400, abs.getY(), abs.getZ());
		ATEntry stale = new ATEntry(helper.getLevel().dimension(), abs.getX() + 1, abs.getY(), abs.getZ());
		try {
			EntityNukeExplosionMK3.at.put(far, now + 100);
			helper.assertTrue(LeafiaCoreTE.findJammer(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ()) == null, "400 blocks is out of range");
			EntityNukeExplosionMK3.at.put(stale, now - 1);
			helper.assertTrue(LeafiaCoreTE.findJammer(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ()) == null, "an expired entry does not jam");
			helper.assertTrue(!EntityNukeExplosionMK3.at.containsKey(stale), "and is pruned");
			EntityNukeExplosionMK3.at.put(near, now + 100);
			BlockPos found = LeafiaCoreTE.findJammer(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ());
			helper.assertTrue(found != null && found.equals(new BlockPos(near.x(), near.y(), near.z())), "100 blocks jams, at the entry's position: " + found);
		} finally {
			EntityNukeExplosionMK3.at.remove(near);
			EntityNukeExplosionMK3.at.remove(far);
			EntityNukeExplosionMK3.at.remove(stale);
		}
		helper.succeed();
	}
}
