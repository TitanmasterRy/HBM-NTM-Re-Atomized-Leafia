package com.leafia.gametest;

import com.hbm.blocks.BlockDummyable;
import com.hbm.gametest.TickWindow;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.light.LightBlock;
import com.leafia.contents.building.light.LightEmitter;
import com.leafia.contents.building.light.LightTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluorescent light. Its <em>appearance</em> needs a block entity renderer this port does
 * not have, but the mechanism underneath does not: a lit fixture drops a column of invisible
 * emitters beneath itself, and an emitter with nothing lit above it deletes itself.
 *
 * <p>That second half is the one worth having tests for. It runs on neighbour changes, so it
 * fires constantly and in situations nobody thinks about — and if it were wrong the failure is
 * an invisible, indestructible, light-emitting block left behind in someone's base, which is
 * about the worst thing this addon could leave in a world.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaLightTests {

	private static final String EMPTY = "empty";

	/** Upstream's numbers, which are deliberately tiny — see {@link LightTE}. */
	@GameTest(template = EMPTY)
	public static void lightBalanceMatchesUpstream(GameTestHelper helper) {

		helper.assertTrue(LightTE.CONSUMPTION == 1L, "consumption is upstream's 1 HE, got " + LightTE.CONSUMPTION);
		helper.assertTrue(LightTE.INTERVAL == 5L, "it drains every INTERVAL+1 ticks, and INTERVAL is 5, got " + LightTE.INTERVAL);
		helper.assertTrue(LightTE.MAX_POWER == 2L, "the buffer is consumption * 2 = 2, got " + LightTE.MAX_POWER);
		helper.assertTrue(LightEmitter.LIGHT_RANGE == 8, "the column reaches 8 blocks, got " + LightEmitter.LIGHT_RANGE);

		helper.succeed();
	}

	/**
	 * Power makes it light, and lighting it lays a column of emitters. Losing power takes both
	 * away again.
	 *
	 * <p>The gametest structure is only a few blocks tall, so this asserts the column fills the
	 * air it has and stops at the floor rather than its full eight — {@link LightEmitter#LIGHT_RANGE}
	 * itself is pinned by {@link #lightBalanceMatchesUpstream}, and
	 * {@link #theColumnStopsAtTheFirstSolidBlock} covers the stopping rule with an obstruction
	 * it controls.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 600)
	public static void aPoweredLightLaysAColumnOfEmittersAndTakesThemBack(GameTestHelper helper) {

		if(!LeafiaFeatures.lights) { helper.succeed(); return; }

		BlockPos core = new BlockPos(1, 3, 1);
		placeCore(helper, core);

		TickWindow.runAfterDelay(helper, 3, () -> {

			LightTE te = (LightTE) helper.getBlockEntity(core);
			helper.assertTrue(te != null, "the core should carry a LightTE");
			helper.assertTrue(!helper.getBlockState(core).getValue(LightBlock.LIT), "it starts unlit");

			te.setPower(LightTE.MAX_POWER);

			helper.runAfterDelay(3, () -> {

				helper.assertTrue(helper.getBlockState(core).getValue(LightBlock.LIT),
						"power should have lit it");

				for(int i = 1; i <= 2; i++) {
					BlockState below = helper.getBlockState(core.below(i));
					helper.assertTrue(LightEmitter.isEmitter(below),
							"there should be an emitter " + i + " below the fixture, found " + below.getBlock());
				}

				// Upstream's buffer is two ticks of consumption drained every six, so it goes
				// out on its own within about a dozen ticks once nothing is feeding it.
				helper.runAfterDelay(40, () -> {

					helper.assertTrue(!helper.getBlockState(core).getValue(LightBlock.LIT),
							"with nothing feeding it, it should have gone out");

					for(int i = 1; i <= 2; i++) {
						helper.assertTrue(!LightEmitter.isEmitter(helper.getBlockState(core.below(i))),
								"and taken its emitters with it - one is left " + i + " below");
					}

					helper.succeed();
				});
			});
		});
	}

	/** The column stops at the first thing that is not air, so a light over a table lights the table. */
	@GameTest(template = EMPTY, timeoutTicks = 600)
	public static void theColumnStopsAtTheFirstSolidBlock(GameTestHelper helper) {

		if(!LeafiaFeatures.lights) { helper.succeed(); return; }

		BlockPos core = new BlockPos(1, 3, 1);
		placeCore(helper, core);
		helper.setBlock(core.below(3), Blocks.STONE);

		TickWindow.runAfterDelay(helper, 3, () -> {

			((LightTE) helper.getBlockEntity(core)).setPower(LightTE.MAX_POWER);

			helper.runAfterDelay(3, () -> {

				helper.assertTrue(LightEmitter.isEmitter(helper.getBlockState(core.below(1))),
						"the two air blocks above the obstruction should be lit");
				helper.assertTrue(LightEmitter.isEmitter(helper.getBlockState(core.below(2))),
						"both of them");
				helper.assertTrue(!helper.getBlockState(core.below(3)).isAir(),
						"the obstruction is still there");

				helper.succeed();
			});
		});
	}

	/**
	 * A stray emitter deletes itself.
	 *
	 * <p>This is the safety net. An emitter is invisible, has no collision and drops nothing, so
	 * one left behind by a broken fixture would be a permanent unexplained light source that a
	 * player has no way to find or remove.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aStrayEmitterDeletesItself(GameTestHelper helper) {

		if(!LeafiaFeatures.lights) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, LeafiaBlocks.light_emitter.get());

		TickWindow.runAfterDelay(helper, 3, () -> {
			helper.assertTrue(!LightEmitter.isEmitter(helper.getBlockState(pos)),
					"an emitter with no lit fixture above it must remove itself");
			helper.succeed();
		});
	}

	/** Everything follows the switch, the emitter included. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.lights;

		for(String name : new String[] { "light_unlit", "light_emitter" }) {
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name)) == on,
					"leafia:" + name + " should exist exactly when lights is on");
		}

		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("light_unlit")) == on,
				"the fixture has a BlockItem");
		helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("light_emitter")),
				"the emitter never does - it is placed by the fixture, not by a player");

		// Upstream's second block is a blockstate property here, and should not be registered.
		helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "light_lit")),
				"light_lit is a property of light_unlit now, not a block of its own (D-1017)");

		helper.succeed();
	}

	/** Places a lone core directly, since {@code setBlock} does not run placement. */
	private static void placeCore(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, LeafiaBlocks.light_unlit.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.UP)
				.setValue(LightBlock.LIT, false));
	}
}
