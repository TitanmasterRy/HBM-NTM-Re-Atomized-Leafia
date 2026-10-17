package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.handler.radiation.ChunkRadiationHandler;
import com.hbm.handler.radiation.ChunkRadiationHandlerSimple;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.minerals.corium.CoriumOreBase;
import com.leafia.contents.minerals.corium.ICoriumOre;
import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The two corium ores, chernobylite and zetalite (D-1076).
 *
 * <p>Every number is upstream's: hardness 20 and 50, resistance 9000 for both (5,400 here by
 * D-012), harvest level 2 and 3, and the radiation step - {@code rads / 10} up to a ceiling of
 * {@code rads = 450} - taken every {@code 60 + rand(500)} ticks. Strength is in
 * {@link LeafiaStrengthTests}' table; the rest is here. The radiation tests run on a handler of
 * their own, put into {@code ChunkRadiationManager.proxy} and taken out again, so the live system
 * is never disturbed.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCoriumOreTests {

	private static final String EMPTY = "empty";

	private static BlockState chernobylite() {
		return LeafiaBlocks.ore_corium_chernobylite.get().defaultBlockState();
	}

	private static BlockState zetalite() {
		return LeafiaBlocks.ore_corium_zetalite.get().defaultBlockState();
	}

	/** Runs a body with a handler of its own in the proxy slot, and puts the real one back. */
	private static void withHandler(GameTestHelper helper, java.util.function.Consumer<ChunkRadiationHandlerSimple> body) {
		ChunkRadiationHandler saved = ChunkRadiationManager.proxy;
		ChunkRadiationHandlerSimple handler = new ChunkRadiationHandlerSimple();
		handler.trackWorld(helper.getLevel());
		ChunkRadiationManager.proxy = handler;
		try {
			body.accept(handler);
		} finally {
			ChunkRadiationManager.proxy = saved;
		}
	}

	/** Upstream's two constants: the ceiling, and a tenth of it for the step. */
	@GameTest(template = EMPTY)
	public static void theCeilingIs450AndTheStepATenthOfIt(GameTestHelper helper) {

		helper.assertTrue(CoriumOreBase.rads == 450, "rads should be 450, was " + CoriumOreBase.rads);
		helper.assertTrue(CoriumOreBase.rads / 10f == 45F, "the step should be 45, was " + CoriumOreBase.rads / 10f);
		helper.succeed();
	}

	/**
	 * <b>Harvest level 2 and 3.</b> Chernobylite needs iron or better, zetalite diamond or better;
	 * a bare hand gets nothing from either, and neither does gold, which is wood's level in
	 * 1.12.2 as it is here.
	 */
	@GameTest(template = EMPTY)
	public static void chernobyliteNeedsIronAndZetaliteNeedsDiamond(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		List<String> wrong = new ArrayList<>();
		check(wrong, "chernobylite", chernobylite(), Items.WOODEN_PICKAXE, false);
		check(wrong, "chernobylite", chernobylite(), Items.GOLDEN_PICKAXE, false);
		check(wrong, "chernobylite", chernobylite(), Items.STONE_PICKAXE, false);
		check(wrong, "chernobylite", chernobylite(), Items.IRON_PICKAXE, true);
		check(wrong, "chernobylite", chernobylite(), Items.DIAMOND_PICKAXE, true);
		check(wrong, "chernobylite", chernobylite(), Items.NETHERITE_PICKAXE, true);
		check(wrong, "chernobylite", chernobylite(), Items.IRON_SHOVEL, false);

		check(wrong, "zetalite", zetalite(), Items.WOODEN_PICKAXE, false);
		check(wrong, "zetalite", zetalite(), Items.STONE_PICKAXE, false);
		check(wrong, "zetalite", zetalite(), Items.IRON_PICKAXE, false);
		check(wrong, "zetalite", zetalite(), Items.DIAMOND_PICKAXE, true);
		check(wrong, "zetalite", zetalite(), Items.NETHERITE_PICKAXE, true);

		helper.assertTrue(chernobylite().requiresCorrectToolForDrops() && zetalite().requiresCorrectToolForDrops(),
				"a bare hand should get nothing from either ore");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static void check(List<String> wrong, String name, BlockState state, Item tool, boolean harvests) {
		boolean does = new ItemStack(tool).isCorrectToolForDrops(state);
		if(does != harvests)
			wrong.add(name + (does ? " yields to " : " refuses ") + tool);
	}

	/** It drops itself, once, to the right tool - and the table is the plain drop-self one. */
	@GameTest(template = EMPTY)
	public static void theyDropThemselves(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

		check(helper, Block.getDrops(chernobylite(), level, pos, null, null, new ItemStack(Items.IRON_PICKAXE)),
				LeafiaBlocks.ore_corium_chernobylite.get(), "chernobylite with an iron pickaxe");
		check(helper, Block.getDrops(zetalite(), level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE)),
				LeafiaBlocks.ore_corium_zetalite.get(), "zetalite with a diamond pickaxe");

		helper.succeed();
	}

	private static void check(GameTestHelper helper, List<ItemStack> drops, Block expected, String what) {
		helper.assertTrue(drops.size() == 1 && drops.get(0).is(expected.asItem()) && drops.get(0).getCount() == 1,
				what + " should drop one of itself, dropped " + drops);
	}

	/**
	 * <b>Both clocks.</b> Placing an ore schedules a tick - upstream's {@code onBlockAdded} - and
	 * the block also ticks randomly, upstream's {@code setTickRandomly(true)}. A ticked ore
	 * schedules the next one.
	 */
	@GameTest(template = EMPTY)
	public static void aPlacedOreSchedulesATickAndTicksRandomly(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		withHandler(helper, handler -> {
			ServerLevel level = helper.getLevel();
			for(var block : List.of(LeafiaBlocks.ore_corium_chernobylite, LeafiaBlocks.ore_corium_zetalite)) {
				helper.assertTrue(block.get().defaultBlockState().isRandomlyTicking(), block.getId() + " should tick randomly");

				BlockPos rel = new BlockPos(1, 1, 1);
				helper.setBlock(rel, block.get().defaultBlockState());
				BlockPos pos = helper.absolutePos(rel);
				helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos, block.get()),
						block.getId() + " should schedule a tick when placed");
			}
		});
		helper.succeed();
	}

	/** The next tick is 60 to 559 ticks out, drawn fresh each time. */
	@GameTest(template = EMPTY)
	public static void theDelayIs60Plus500Random(GameTestHelper helper) {

		RandomSource random = RandomSource.create(20_241_007L);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for(int i = 0; i < 20_000; i++) {
			int delay = CoriumOreBase.tickRate(random);
			lo = Math.min(lo, delay);
			hi = Math.max(hi, delay);
		}
		helper.assertTrue(lo >= 60, "the shortest delay should be 60, was " + lo);
		helper.assertTrue(hi <= 559, "the longest delay should be 559, was " + hi);
		// 20,000 draws of 500 values reach both ends; a narrower range means the constants moved.
		helper.assertTrue(lo <= 62 && hi >= 557, "the range should be the whole 60..559, was " + lo + ".." + hi);
		helper.succeed();
	}

	/**
	 * <b>The tick.</b> Adds 45 to the chunk's radiation, never takes the chunk past 450, and does
	 * not lower a chunk that is already above it.
	 */
	@GameTest(template = EMPTY)
	public static void aTickAdds45UpToACeilingOf450(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		withHandler(helper, handler -> {
			ServerLevel level = helper.getLevel();
			BlockPos rel = new BlockPos(1, 1, 1);
			helper.setBlock(rel, chernobylite());
			BlockPos pos = helper.absolutePos(rel);
			int x = pos.getX(), y = pos.getY(), z = pos.getZ();
			RandomSource random = level.getRandom();

			handler.setRadiation(level, x, y, z, 0F);
			chernobylite().tick(level, pos, random);
			helper.assertTrue(handler.getRadiation(level, x, y, z) == 45F,
					"one tick from nothing should add 45, chunk reads " + handler.getRadiation(level, x, y, z));

			handler.setRadiation(level, x, y, z, 440F);
			chernobylite().tick(level, pos, random);
			helper.assertTrue(handler.getRadiation(level, x, y, z) == 450F,
					"a tick at 440 should stop at the ceiling, chunk reads " + handler.getRadiation(level, x, y, z));

			chernobylite().tick(level, pos, random);
			helper.assertTrue(handler.getRadiation(level, x, y, z) == 450F,
					"a tick at the ceiling should add nothing, chunk reads " + handler.getRadiation(level, x, y, z));

			handler.setRadiation(level, x, y, z, 5_000F);
			chernobylite().tick(level, pos, random);
			helper.assertTrue(handler.getRadiation(level, x, y, z) == 5_000F,
					"a chunk above the ceiling should be left alone, not pulled down; reads " + handler.getRadiation(level, x, y, z));

			// The same step from the other ore, and each tick schedules the next.
			// Read and write at the zetalite's own position, not the first ore's: the GameTest grid
			// sits at a random origin, so one block over is another chunk about one run in sixteen,
			// and the step would land where this test was not looking (2 failures in 31 runs).
			BlockPos rel2 = new BlockPos(1, 1, 2);
			helper.setBlock(rel2, zetalite());
			BlockPos pos2 = helper.absolutePos(rel2);
			x = pos2.getX(); y = pos2.getY(); z = pos2.getZ();
			handler.setRadiation(level, x, y, z, 100F);
			// Placing it scheduled a tick; take that away, so the one asserted below is the tick's own.
			((net.minecraft.world.ticks.LevelChunkTicks<Block>) level.getChunkAt(pos2).getBlockTicks())
					.removeIf(tick -> tick.pos().equals(pos2));
			helper.assertTrue(!level.getBlockTicks().hasScheduledTick(pos2, LeafiaBlocks.ore_corium_zetalite.get()),
					"the placement tick should be gone before the tick under test");
			zetalite().tick(level, pos2, random);
			helper.assertTrue(handler.getRadiation(level, x, y, z) == 145F,
					"zetalite steps 45 as well, chunk reads " + handler.getRadiation(level, x, y, z));
			helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos2, LeafiaBlocks.ore_corium_zetalite.get()),
					"a ticked ore should have scheduled its next tick");
		});
		helper.succeed();
	}

	/** The capped increment on its own: CE's rules, read out of the jar (D-1076). */
	@GameTest(template = EMPTY)
	public static void theCappedIncrementFollowsCE(GameTestHelper helper) {

		withHandler(helper, handler -> {
			ServerLevel level = helper.getLevel();
			BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
			int x = pos.getX(), y = pos.getY(), z = pos.getZ();

			handler.setRadiation(level, x, y, z, 10F);
			float added = ChunkRadiationManager.incrementRad(level, pos, 30F, 100F);
			helper.assertTrue(added == 30F && handler.getRadiation(level, x, y, z) == 40F, "plain add, added " + added);

			added = ChunkRadiationManager.incrementRad(level, pos, 100F, 100F);
			helper.assertTrue(added == 60F && handler.getRadiation(level, x, y, z) == 100F, "clamped to max, added " + added);

			added = ChunkRadiationManager.incrementRad(level, pos, 1F, 100F);
			helper.assertTrue(added == 0F && handler.getRadiation(level, x, y, z) == 100F, "at max adds nothing, added " + added);

			// An amount under 1e-5 is not worth making.
			handler.setRadiation(level, x, y, z, 0F);
			added = ChunkRadiationManager.incrementRad(level, pos, 1.0E-6F, 100F);
			helper.assertTrue(added == 0F, "a vanishing amount is skipped, added " + added);
		});
		helper.succeed();
	}

	/** Both are {@code ICoriumOre}: the marker upstream's corium spread tests for. */
	@GameTest(template = EMPTY)
	public static void bothAreMarkedCoriumOre(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		helper.assertTrue(LeafiaBlocks.ore_corium_chernobylite.get() instanceof ICoriumOre, "chernobylite is not an ICoriumOre");
		helper.assertTrue(LeafiaBlocks.ore_corium_zetalite.get() instanceof ICoriumOre, "zetalite is not an ICoriumOre");
		helper.succeed();
	}

	/**
	 * Over 50 blast resistance, so the gold line shows - upstream's {@code AddonOreBase.addInformation}
	 * and, because these are the second blocks over 50, the shared {@code BlastResistanceTooltip}.
	 * 5,400 is 9000 x 3 / 5, the number 1.12.2 prints.
	 *
	 * <p>The line's <em>text</em> cannot be read here: {@code I18nUtil} answers a server with a
	 * sarcastic constant on purpose (D-003), so what is pinned is whether a line is added, and the
	 * number it would be built from.
	 */
	@GameTest(template = EMPTY)
	public static void theTooltipAppearsOverFifty(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		for(var block : List.of(LeafiaBlocks.ore_corium_chernobylite, LeafiaBlocks.ore_corium_zetalite)) {
			helper.assertTrue(block.get().getExplosionResistance() == 5_400F,
					block.getId() + " should resist 5400, resists " + block.get().getExplosionResistance());
			List<Component> lines = new ArrayList<>();
			block.get().asItem().appendHoverText(new ItemStack(block.get()), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
			helper.assertTrue(lines.size() == 1, block.getId() + " should print one line, printed " + lines);
		}

		// The threshold is upstream's 50: stone (6) prints nothing, obsidian (1200) does.
		List<Component> stone = new ArrayList<>();
		BlastResistanceTooltip.add(Blocks.STONE, stone);
		helper.assertTrue(stone.isEmpty(), "stone should print nothing, printed " + stone);
		List<Component> obsidian = new ArrayList<>();
		BlastResistanceTooltip.add(Blocks.OBSIDIAN, obsidian);
		helper.assertTrue(obsidian.size() == 1, "obsidian should print a line, printed " + obsidian);
		helper.succeed();
	}

	/** Both ores are in the pickaxe tag - the flag and the tag go together (D-1026). */
	@GameTest(template = EMPTY)
	public static void bothAreInThePickaxeAndTierTags(GameTestHelper helper) {

		if(!LeafiaFeatures.coriumOres) { helper.succeed(); return; }

		for(BlockState state : List.of(chernobylite(), zetalite()))
			helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), state.getBlock() + " is not in mineable/pickaxe");
		helper.assertTrue(chernobylite().is(BlockTags.NEEDS_IRON_TOOL) && !chernobylite().is(BlockTags.NEEDS_DIAMOND_TOOL),
				"chernobylite should be in needs_iron_tool alone");
		helper.assertTrue(zetalite().is(BlockTags.NEEDS_DIAMOND_TOOL) && !zetalite().is(BlockTags.NEEDS_IRON_TOOL),
				"zetalite should be in needs_diamond_tool alone");
		helper.succeed();
	}
}
