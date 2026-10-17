package com.leafia.contents.bomb.digamma;

import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.dev.blocks.legacy.MetaLevel;
import com.hbm.util.ContaminationUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Digammitite: the rock a digamma blast turns the ground into, in six levels of heat, each cooling
 * to the one below. The addon's sellafite again, recoloured to the colours of digamma, and instead
 * of radiation it gives off digamma.
 *
 * <p>Ported from {@code com.leafia.contents.bomb.digamma.DigammititeBlock}, a {@code BlockMeta}.
 * On every random tick a block
 *
 * <ul>
 * <li>cools one level, one chance in fifty (level 0 stays, and does nothing else), and
 * <li>if it is above level 0, radiates {@code 5 x (level + 1)} digamma to everything within 32
 *     blocks, falling off with the cube of the distance ({@code ContaminationUtil.radiate}, the base
 *     mod's own since D-3023; no radiation, no fire).
 * </ul>
 *
 * <p>Unlike its two siblings it has no {@code onEntityWalk}: it is dangerous at a distance and, as
 * an item, in the hand ({@code LeafiaHazards} registers 0.2 digamma per level above 0 on the
 * items). Its model differs from baleonite's in one place: the third texture variant has stray
 * green dots once recoloured, so upstream folds it into the second - the blockstate weights the
 * second variant twice. The sprites are recoloured by a generator script, as
 * baleonite's are.
 */
public class DigammititeBlock extends Block implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_6;
	public static final int LEVELS = 6;
	/** Upstream's {@code rad}: digamma per random tick is {@code RAD x (level + 1)}. */
	public static final float RAD = 5F;
	/** {@code radiate(..., 32, 0, netRad, 0, 0, 0)}. */
	public static final double RANGE = 32;
	/** A random tick cools the block one time in this many. */
	public static final int COOL_ONE_IN = 50;

	public DigammititeBlock(Properties properties) {
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

	/** {@code updateTick}, which {@code needsRandomTick} makes a random tick. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int meta = meta(state);
		if(meta > 0 && random.nextInt(COOL_ONE_IN) == 0)
			level.setBlock(pos, withMeta(meta - 1), Block.UPDATE_CLIENTS);
		if(meta == 0) return;

		float netRad = RAD * (meta + 1);
		ContaminationUtil.radiate(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RANGE, 0F, netRad);
	}
}
