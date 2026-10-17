package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Every addon block's hardness and blast resistance, from upstream's own numbers through D-012.
 *
 * <p>D-012 is the base mod's rule, checked against vanilla: 1.12.2 stored
 * {@code setResistance(r)} as {@code r x 3} and read it back {@code / 5}, so 1.21.1 wants
 * {@code r x 0.6}; and {@code setHardness(h)} raises the stored value to at least {@code h x 5},
 * so a block with no {@code setResistance} has a resistance equal to its hardness, not zero.
 *
 * <p>D-1070 found the addon had broken it three ways — the HP boiler carried a raw 10, oily coal
 * a raw 15, and the broof and all five elevator blocks a resistance of 0 that upstream never
 * had, so any explosion erased an elevator shaft. The table below is upstream's <em>raw</em>
 * numbers, copied from the registrations and class constructors, so a reader checks it against
 * upstream directly and the conversion is done once, here. A block whose feature is switched off
 * is not registered and is skipped.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaStrengthTests {

	private static final String EMPTY = "empty";

	/** Upstream's raw numbers; {@code NaN} where the call is absent. */
	private record Raw(String id, float hardness, float resistance) {}

	private static final float NONE = Float.NaN;

	private static final List<Raw> UPSTREAM = List.of(
			new Raw("amat_duct", 5, 10),
			new Raw("amat_duct_charger", 5, 10),
			new Raw("spk_cable", 5, 10),
			// D-3011: AddonCoreComponent(Material.IRON, "dfc_pulser").setHardness(5.0F).setResistance(50.0F)
			new Raw("dfc_pulser", 5, 50),
			new Raw("fluid_duct_valve_mdl", 5, 10),
			new Raw("fluid_duct_valve_mdl_rs", 5, 10),
			new Raw("light_unlit", 5, 10),
			new Raw("wind_turbine_medium", 5, 10),
			new Raw("deco_turbine", 5, 10),
			new Raw("hp_boiler", 5, 10),
			// D-3016: MixingVatBlock and CoolantHeatexBlock, both AddonBlockDummyable defaults
			new Raw("mixingvat", 5, 10),
			new Raw("coolant_heatex", 5, 10),
			// D-3021: RackBlock(Material.IRON).setHardness(5.0F).setResistance(10.0F)
			new Raw("sellacity_rack", 5, 10),
			// D-3015: SaltSeparatorBlock(Material.IRON).setHardness(5.0F).setResistance(20.0F)
			new Raw("salt_separator", 5, 20),
			// D-1087: SolBlasterBlock's own setHardness(15) and setResistance(160).
			new Raw("sol_blaster", 15, 160),
			// D-1082: BlockMachineBase, AddonBlockBase and BlockNTMGlass, none with either call.
			new Raw("slop_reactor", NONE, NONE),
			new Raw("slop_reactor_casing", NONE, NONE),
			new Raw("slop_reactor_glass", NONE, NONE),
			new Raw("modular_turbine_core", 5, 50),
			new Raw("modular_turbine_shaft", 5, 50),
			new Raw("modular_turbine_3x3_blades", 5, 50),
			new Raw("block_welded_osmiridium", 15, 6_500_000),
			new Raw("block_xanaxium", 15, 6_500_000),
			new Raw("ore_coal_oil", 5, 15),
			new Raw("ore_coal_oil_burning", 5, 15),
			new Raw("letter_sign_a", 5, NONE),
			new Raw("broof", 0.1F, NONE),
			new Raw("regex_filter", 0.1F, 10),
			new Raw("elevator_shaft", 15, NONE),
			new Raw("elevator_pulley", 15, NONE),
			new Raw("elevator_buffer", 15, NONE),
			new Raw("elevator_s6floor", 15, NONE),
			new Raw("elevator_skyliftfloor", 15, NONE),
			new Raw("catwalk_railing_curved", NONE, NONE),
			new Raw("test_fluid_voider", NONE, NONE),
			// D-3031: the other debug blocks, Material.ANVIL with neither call, as the voider
			new Raw("test_ff_source", NONE, NONE),
			new Raw("test_ff_tank", NONE, NONE),
			new Raw("test_boom", NONE, NONE),
			new Raw("test_rbmk_jet", NONE, NONE),
			new Raw("test_blackhole_render", NONE, NONE),
			new Raw("test_render", NONE, NONE),
			new Raw("test_tom", NONE, NONE),
			new Raw("test_circle", NONE, NONE),
			new Raw("rbmk_rod_realersim", 15, 100),
			new Raw("rbmk_rod_realersim_mod", 5, 100),
			// D-1090: ConcreteBricks, one setHardness(15)/setResistance(160) for all four metas
			new Raw("brick_concrete_dark", 15, 160),
			new Raw("brick_concrete_dark_mossy", 15, 160),
			new Raw("brick_concrete_dark_cracked", 15, 160),
			new Raw("brick_concrete_dark_broken", 15, 160),
			new Raw("ore_corium_chernobylite", 20, 9000),
			new Raw("ore_corium_zetalite", 50, 9000),
			// D-3004: the balefire and digamma residue and the legacy contamination blocks, each with its
			// own setHardness and setResistance (a hardness alone reads back as itself)
			new Raw("baleonite", 5, 6),
			new Raw("digammitite", 5, 6),
			new Raw("ash_balefire", 0.5F, NONE),
			new Raw("waste_terracotta", 4, 8),
			new Raw("waste_sand_red", 0.5F, 1),
			new Raw("waste_red_sandstone", 3, 6),
			new Raw("waste_sand", 0.5F, 1),
			new Raw("waste_sandstone", 3, 6),
			new Raw("waste_gravel", 0.5F, 1),
			new Raw("waste_dirt", 0.5F, 1),
			new Raw("waste_snow", 0.1F, NONE),
			new Raw("waste_snow_block", 0.2F, NONE),
			new Raw("waste_ice", 0.2F, NONE),
			new Raw("scorched_earth", 0.5F, 1));

	/** D-012. */
	static float hardness(Raw raw) {
		return Float.isNaN(raw.hardness) ? 0F : raw.hardness;
	}

	static float resistance(Raw raw) {
		if(!Float.isNaN(raw.resistance)) return raw.resistance * 0.6F;
		return hardness(raw);
	}

	/**
	 * <b>A block that needs a tool is in a tool's tag.</b> {@code requiresCorrectToolForDrops()}
	 * says a correct tool is needed and the {@code mineable/*} tags say which; with the flag and
	 * no tag, no tool is ever correct and the block drops nothing to anything. D-1026 is that
	 * failure, found once by reading; this finds it every time, over the live registry.
	 */
	@GameTest(template = EMPTY)
	public static void aBlockThatNeedsAToolIsInATag(GameTestHelper helper) {

		List<String> stranded = new ArrayList<>();
		int needing = 0;
		for(Block block : BuiltInRegistries.BLOCK) {
			if(!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(LeafiaRef.MODID)) continue;
			var state = block.defaultBlockState();
			if(!state.requiresCorrectToolForDrops()) continue;
			needing++;
			if(!state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(BlockTags.MINEABLE_WITH_AXE)
					&& !state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !state.is(BlockTags.MINEABLE_WITH_HOE))
				stranded.add(BuiltInRegistries.BLOCK.getKey(block).getPath());
		}
		helper.assertTrue(stranded.isEmpty(), "need a tool but are in no tool's tag, so drop nothing: " + stranded);
		helper.assertTrue(needing > 0 || !com.leafia.config.LeafiaFeatures.oilyCoal, "no addon block needs a tool - the check checked nothing");
		helper.succeed();
	}

	/**
	 * The other direction: every block upstream needed a pickaxe for needs one here, and any
	 * pickaxe will do (level 0). The list is upstream's - {@code AddonBlockBase} and
	 * {@code AddonBlockBaked} subclasses, {@code SignBlock}'s own call, and {@code BlockOre} -
	 * one name per family (D-1072).
	 */
	@GameTest(template = EMPTY)
	public static void whatUpstreamMinedWithAPickaxeNeedsOne(GameTestHelper helper) {

		List<String> wrong = new ArrayList<>();
		for(String id : List.of("test_fluid_voider", "elevator_shaft", "catwalk_railing_curved", "amat_duct_charger",
				"deco_turbine", "letter_sign_a", "letter_sign_z", "block_welded_osmiridium", "block_xanaxium",
				"ore_coal_oil", "ore_coal_oil_burning", "rbmk_rod_realersim", "rbmk_rod_realersim_mod",
				"slop_reactor_casing", "regex_filter", "brick_concrete_dark", "brick_concrete_dark_mossy",
				"brick_concrete_dark_cracked", "brick_concrete_dark_broken",
				// D-3004: BlockMeta over Material.ROCK
				"baleonite", "digammitite", "waste_terracotta", "waste_sandstone", "waste_red_sandstone",
				// D-3031: the debug blocks that are AddonBlockBases
				"test_boom", "test_rbmk_jet", "test_blackhole_render", "test_render", "test_tom", "test_circle")) {
			var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, id));
			if(block.isEmpty()) continue;
			var state = block.get().defaultBlockState();
			if(!state.requiresCorrectToolForDrops())
				wrong.add(id + " drops to a bare hand");
			else if(!new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE).isCorrectToolForDrops(state))
				wrong.add(id + " refuses a wooden pickaxe");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The osmiridium blocks' random tick: 40 digamma over the cube of the distance to the target's
	 * eyes, and nothing to a cat, CE's {@code radiate} (D-1073). Driven by calling the tick, since
	 * random ticks cannot be waited for.
	 */
	@GameTest(template = "empty16")
	public static void osmiridiumRadiatesDigammaByTheInverseCube(GameTestHelper helper) {

		if(!com.leafia.config.LeafiaFeatures.osmiridiumBlocks) { helper.succeed(); return; }

		var level = helper.getLevel();
		var block = com.leafia.contents.LeafiaBlocks.block_welded_osmiridium.get();
		net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(at, block);
		net.minecraft.core.BlockPos abs = helper.absolutePos(at);

		var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(8, 2, 2));
		zombie.setNoAi(true);
		var cat = helper.spawn(net.minecraft.world.entity.EntityType.CAT, new net.minecraft.core.BlockPos(2, 2, 8));
		cat.setNoAi(true);

		block.defaultBlockState().randomTick(level, abs, level.random);

		double dx = zombie.getX() - (abs.getX() + 0.5), dy = zombie.getY() + zombie.getEyeHeight() - (abs.getY() + 0.5), dz = zombie.getZ() - (abs.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double dmgLen = Math.max(len, com.leafia.contents.machines.powercores.dfc.OsmiridiumBlock.RANGE * 0.05);
		float expected = (float) (40F / (dmgLen * dmgLen * dmgLen));
		float got = com.hbm.extprop.HbmLivingProps.getDigamma(zombie);
		helper.assertTrue(Math.abs(got - expected) < 1e-5, "zombie at " + len + " took " + got + " digamma, expected " + expected);
		helper.assertTrue(com.hbm.extprop.HbmLivingProps.getDigamma(cat) == 0F, "a cat is exempt, as CE's ocelot");
		helper.succeed();
	}

	private static boolean close(float a, float b) {
		return Math.abs(a - b) <= 1e-4 * Math.max(1F, Math.abs(b));
	}

	@GameTest(template = EMPTY)
	public static void everyBlockFollowsD012(GameTestHelper helper) {

		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for(Raw raw : UPSTREAM) {
			var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, raw.id()));
			if(block.isEmpty()) continue;
			checked++;
			Block b = block.get();
			float h = b.defaultDestroyTime();
			float r = b.getExplosionResistance();
			// Relative, because 6,500,000 x 0.6 in float arithmetic is 3,900,000.2.
			if(!close(h, hardness(raw)) || !close(r, resistance(raw)))
				wrong.add(raw.id() + " is " + h + "/" + r + ", expected " + hardness(raw) + "/" + resistance(raw));
		}
		helper.assertTrue(checked > 10, "only " + checked + " blocks checked");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
