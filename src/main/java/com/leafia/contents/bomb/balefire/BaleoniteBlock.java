package com.leafia.contents.bomb.balefire;

import com.hbm.blocks.ModBlocks;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.potion.NtmEffects;
import com.hbm.util.LegacyMaterial;
import com.leafia.contents.minerals.corium.ICoriumOre;
import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.dev.blocks.legacy.MetaLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Baleonite: the rock a balefire pool leaves where it ate stone, in six levels of heat, each
 * cooling to the one below. The addon's own sellafite, recoloured from green to the colour of
 * balefire.
 *
 * <p>Ported from {@code com.leafia.contents.bomb.balefire.BaleoniteBlock}, a {@code BlockMeta}
 * (registry name {@code baleonite}; upstream's field is {@code baleonitite}). On every random tick
 * a block
 *
 * <ul>
 * <li>adds {@code 5 x (level + 1)} RAD to its chunk, and
 * <li>cools one level, one chance in thirty, to level 0 - where it stays (there is no slaked
 *     baleonite: upstream's level 0 never changes into anything).
 * </ul>
 *
 * <p>Walking on it gives thirty seconds of radiation sickness at amplifier {@code level + 2} for
 * levels 0 to 4 and {@code level x 4 + 2} for level 5, and from level 3 up sets the walker alight
 * for {@code level} seconds. (The base sellafite is {@code level} and {@code level x 2} with no
 * fire.)
 *
 * <p>Upstream drew it with a dynamic model whose sprites it recoloured while the atlas was stitched,
 * from the four slaked-sellafield textures, through {@code RGBMutatorInterpolatedComponentRemap};
 * 1.21.1 has no stitch hook, so a generator script does the recolouring once, with
 * the same remap and the same colour table, and the blockstate carries the four textures as random
 * variants (D-3004, after the base mod's own D-169).
 */
public class BaleoniteBlock extends Block implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_6;
	public static final int LEVELS = 6;
	/** Upstream's {@code rad}: chunk RAD per random tick is {@code RAD x (level + 1)}. */
	public static final float RAD = 5F;
	/** Radiation effect on contact lasts thirty seconds. */
	public static final int WALK_TICKS = 30 * 20;
	/** A random tick cools the block one time in this many. */
	public static final int COOL_ONE_IN = 30;

	public BaleoniteBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(META, MetaLevel.L0));
	}

	@Override
	public EnumProperty<MetaLevel> getVariantProperty() {
		return META;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(META);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return getVariantStack(state);
	}

	/**
	 * What a balefire pool or a layer of ash may turn into baleonite: stone, cobblestone, baleonite
	 * itself, infested stone, or anything the 1.7.10 material table calls rock. Upstream's test, spelled
	 * the same way in the ash's tick and in the corium mixin.
	 */
	public static boolean consumes(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.COBBLESTONE || block == Blocks.STONE || block instanceof BaleoniteBlock
				|| block == Blocks.INFESTED_STONE || LegacyMaterial.of(state) == LegacyMaterial.ROCK;
	}

	/** Corium and its ores are never eaten: {@code block_corium}, {@code block_corium_cobble} and every {@link ICoriumOre}. */
	public static boolean isCorium(BlockState state) {
		Block block = state.getBlock();
		return block == ModBlocks.block_corium.get() || block == ModBlocks.block_corium_cobble.get() || block instanceof ICoriumOre;
	}

	/**
	 * Upstream's {@code (rand.nextInt(3) + 1) ^ 2}. <b>That is XOR, not a square</b>: it gives 3, 0 or 1,
	 * each one time in three. Whether the author meant {@code Math.pow} is unknowable; the result is
	 * that {@link #levelForRoll} yields level 2 two times in three, level 1 one time in three and never
	 * level 0. Kept exactly, and pinned by a test so nobody "fixes" it into a square without noticing
	 * the table moves.
	 */
	public static int roll(RandomSource random) {
		return (random.nextInt(3) + 1) ^ 2;
	}

	/** The baleonite level a roll makes: 2 for 0 or 1, 1 for 2 to 4, 0 for 5 to 9, and none (-1) above. */
	public static int levelForRoll(int roll) {
		if(roll <= 1) return 2;
		if(roll <= 4) return 1;
		if(roll <= 9) return 0;
		return -1;
	}

	/** The amplifier of the radiation effect for a level: {@code level < 5 ? level + 2 : level * 4 + 2}. */
	public static int walkAmplifier(int level) {
		return level < 5 ? level + 2 : level * 4 + 2;
	}

	/** {@code onEntityWalk}. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if(level.isClientSide || !(entity instanceof LivingEntity living)) return;
		int meta = meta(state);
		living.addEffect(new MobEffectInstance(NtmEffects.RADIATION, WALK_TICKS, walkAmplifier(meta)));
		if(meta >= 3) living.igniteForSeconds(meta);
	}

	/** {@code updateTick}, which {@code needsRandomTick} makes a random tick. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int meta = meta(state);
		ChunkRadiationManager.incrementRad(level, pos.getX(), pos.getY(), pos.getZ(), RAD * (meta + 1));

		if(meta > 0 && random.nextInt(COOL_ONE_IN) == 0)
			level.setBlock(pos, withMeta(meta - 1), Block.UPDATE_CLIENTS);
	}
}
