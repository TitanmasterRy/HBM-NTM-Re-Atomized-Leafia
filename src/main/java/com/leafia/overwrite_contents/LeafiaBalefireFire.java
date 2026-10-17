package com.leafia.overwrite_contents;

import com.hbm.blocks.bomb.Balefire;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Balefire, the addon's way: upstream's {@code MixinBalefire}, an {@code @Overwrite} of the base fire's
 * {@code updateTick}, here a {@link Balefire.TickHandler} (D-2197) that takes every tick while
 * {@code features.overhauls.explosionVfx} is on and leaves the base mod's otherwise.
 *
 * <p>It is 1.12.2's own {@code BlockFire.updateTick} with three things changed, and the numbers are all
 * that file's: a fire ages by one a third of the time (to at most 15), reschedules itself
 * {@code 30 + nextInt(35)} ticks out, tries each of its six neighbours at odds of 100 (80 up and down,
 * 50 less in a humid biome) against the neighbour's flammability, and lights the 3 x 6 x 3 box around
 * it ({@code -1..4} up) at {@code (spreadSpeed + 40 + 7 x difficulty) / (age + 30)} in
 * {@code 100 + 100 x (dy - 1)}, halved when humid. The three changes:
 *
 * <ul>
 * <li><b>It burns forever on baleonite.</b> Upstream's {@code canDie} is "the block below is not
 *     baleonite", and a fire that cannot die skips the burn-out rules entirely; rain only stops it
 *     spreading where the new fire could die.</li>
 * <li><b>A burnt neighbour becomes balefire ash</b> ({@code ash_balefire}, one layer) where vanilla
 *     fire leaves air - the "otherwise" branch of the age roll, so a fire of age {@code a} leaves ash
 *     unless {@code nextInt(a + 10) < 8}.</li>
 * <li>Neither rain nor {@code isRainingAt} puts the fire itself out (that branch is commented out
 *     upstream).</li>
 * </ul>
 *
 * <p>Two things carried exactly that look like mistakes. When the fire cannot stay where it is
 * ({@code canSurvive} fails) it is set to air <em>and the tick carries on</em>, so a fire younger than
 * 15 puts its aged self straight back (vanilla 1.12.2's own fire does the same). And upstream's
 * overrides of {@code getFlammability(Block)} and {@code getEncouragement(Block)} on the balefire block
 * answer 20,000 for everything but baleonite - but 1.12.2 asks those of {@code Blocks.FIRE}, never of
 * balefire, so they were dead and are not ported. Its {@code onEntityCollision} is the base block's
 * {@code entityInside} already (ten seconds alight, radiation IX for five).
 *
 * <p>Translations: {@code isBlockinHighHumidity} is {@link BiomeTags#INCREASED_FIRE_BURNOUT}, the tag
 * 1.21.1's own fire asks the same question with; {@code isTopSolid}/{@code isSideSolid(UP)} is
 * {@code isFaceSturdy(UP)}; {@code canCatchFire} is {@code isFlammable}; {@code getFireSpreadSpeed} and
 * {@code getFlammability} are the block state's. {@code isAreaLoaded(pos, 2)} is every chunk within two.
 * With {@code features.balefireResidue} off there is no ash, and a burnt block is left as air - the line
 * upstream commented out for it.
 */
public final class LeafiaBalefireFire {

	private LeafiaBalefireFire() {}

	/** {@code BlockFire.tickRate}. */
	public static final int TICK_RATE = 30;
	/** {@code this.tickRate(worldIn) + rand.nextInt(35)}. */
	public static final int TICK_SPREAD = 35;
	/** {@code catchBalefire} odds east, west, north and south. */
	public static final int SIDE_CHANCE = 100;
	/** ... and up and down. */
	public static final int VERTICAL_CHANCE = 80;
	/** Humid biomes shorten each of those by this. */
	public static final int HUMID_BONUS = -50;

	public static final Balefire.TickHandler HANDLER = LeafiaBalefireFire::tick;

	public static boolean tick(Balefire block, BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
		if(!LeafiaFeatures.explosionVfx) return false;
		updateTick(block, state, level, pos, rand);
		return true;
	}

	/** {@code canDie}: anything but baleonite underneath. */
	public static boolean canDie(Level level, BlockPos pos) {
		return !(level.getBlockState(pos.below()).getBlock() instanceof BaleoniteBlock);
	}

	public static void updateTick(Balefire block, BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {

		if(!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
		if(!level.hasChunksAt(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) return;

		if(!state.canSurvive(level, pos)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}

		boolean flag = !canDie(level, pos);

		int i = state.getValue(Balefire.AGE);

		if(i < 15) {
			state = state.setValue(Balefire.AGE, i + rand.nextInt(3) / 2);
			level.setBlock(pos, state, 4);
		}

		level.scheduleTick(pos, block, TICK_RATE + rand.nextInt(TICK_SPREAD));

		if(!flag) {
			if(!canNeighborCatchFire(level, pos)) {
				BlockPos below = pos.below();
				if(!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || i > 3) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				}
				return;
			}

			if(!canCatchFire(level, pos.below(), Direction.UP) && i == 15 && rand.nextInt(4) == 0) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				return;
			}
		}

		boolean humid = level.getBiome(pos).is(BiomeTags.INCREASED_FIRE_BURNOUT);
		int j = humid ? HUMID_BONUS : 0;

		catchBalefire(block, level, pos.east(), SIDE_CHANCE + j, rand, i, Direction.WEST);
		catchBalefire(block, level, pos.west(), SIDE_CHANCE + j, rand, i, Direction.EAST);
		catchBalefire(block, level, pos.below(), VERTICAL_CHANCE + j, rand, i, Direction.UP);
		catchBalefire(block, level, pos.above(), VERTICAL_CHANCE + j, rand, i, Direction.DOWN);
		catchBalefire(block, level, pos.north(), SIDE_CHANCE + j, rand, i, Direction.SOUTH);
		catchBalefire(block, level, pos.south(), SIDE_CHANCE + j, rand, i, Direction.NORTH);

		for(int k = -1; k <= 1; ++k) {
			for(int l = -1; l <= 1; ++l) {
				for(int i1 = -1; i1 <= 4; ++i1) {
					if(k != 0 || i1 != 0 || l != 0) {
						int j1 = 100;

						if(i1 > 1) {
							j1 += (i1 - 1) * 100;
						}

						BlockPos blockpos = pos.offset(k, i1, l);
						int k1 = getNeighborEncouragement(level, blockpos);

						if(k1 > 0) {
							int l1 = (k1 + 40 + level.getDifficulty().getId() * 7) / (i + 30);

							if(humid) {
								l1 /= 2;
							}

							if(l1 > 0 && rand.nextInt(j1) <= l1 && (!level.isRaining() || !canDie(level, blockpos))) {
								int i2 = i + rand.nextInt(5) / 4;

								if(i2 > 15) {
									i2 = 15;
								}

								level.setBlock(blockpos, state.setValue(Balefire.AGE, i2), 3);
							}
						}
					}
				}
			}
		}
	}

	/** Upstream's {@code catchBalefire}. */
	static void catchBalefire(Balefire block, Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face) {
		BlockState old = level.getBlockState(pos);
		int i = old.getFlammability(level, pos, face);

		if(random.nextInt(chance) < i) {

			if(random.nextInt(age + 10) < 8 && !level.isRainingAt(pos)) {
				int j = age + random.nextInt(5) / 4;

				if(j > 15) {
					j = 15;
				}

				level.setBlock(pos, block.defaultBlockState().setValue(Balefire.AGE, j), 3);
			} else {
				level.setBlock(pos, ash(), 3);
			}

			if(old.is(Blocks.TNT)) {
				old.onCaughtFire(level, pos, face, null);
			}
		}
	}

	/** One layer of balefire ash, or air with the residue switched off. */
	static BlockState ash() {
		if(LeafiaBlocks.ash_balefire == null) return Blocks.AIR.defaultBlockState();
		return LeafiaBlocks.ash_balefire.get().defaultBlockState();
	}

	/** {@code BlockFire.canCatchFire}: the block's own flammability. */
	static boolean canCatchFire(LevelReader level, BlockPos pos, Direction face) {
		return level.getBlockState(pos).isFlammable(level, pos, face);
	}

	static boolean canNeighborCatchFire(LevelReader level, BlockPos pos) {
		for(Direction direction : Direction.values()) {
			if(canCatchFire(level, pos.relative(direction), direction.getOpposite())) return true;
		}
		return false;
	}

	/** Upstream's {@code getNeighborEncouragement}: zero unless empty, else the largest spread speed beside it. */
	static int getNeighborEncouragement(LevelReader level, BlockPos pos) {
		if(!level.isEmptyBlock(pos)) return 0;
		int i = 0;
		for(Direction direction : Direction.values()) {
			BlockPos near = pos.relative(direction);
			i = Math.max(level.getBlockState(near).getFireSpreadSpeed(level, near, direction.getOpposite()), i);
		}
		return i;
	}
}
