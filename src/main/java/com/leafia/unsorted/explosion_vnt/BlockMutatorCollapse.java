package com.leafia.unsorted.explosion_vnt;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.interfaces.IBlockMutator;
import com.leafia.dev.LeafiaUtil;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Some of what a steam explosion breaks comes back down as falling blocks.
 *
 * <p>Upstream: {@code com/leafia/unsorted/explosion_vnt/BlockMutatorCollapse.java}. Before each block is
 * broken its state is remembered; afterwards, one time in five ({@code nextInt(5) != 0} returns), a block
 * with air under where it stood, that was a solid visible cube and had no block entity, is thrown back
 * as a falling block four times in five ({@code nextInt(5) > 0}) - so {@code 4/25} of the eligible
 * blocks, and none that had something under them to rest on.
 *
 * <p>Upstream walks downward from the broken position looking for the floor, but its loop gives up at
 * the first step: it needs the position itself to be air and stops either because the block under it is
 * replaceable (it may fall) or because it then steps onto that non-replaceable block, which is not air (it
 * may not). Both outcomes are kept as that one test. 1.12.2's {@code isReplaceable} material is
 * {@code canBeReplaced()}; {@code ITileEntityProvider} is {@link EntityBlock}; the falling block starts at
 * {@code fallTime = 1}, here {@code time}.
 */
public class BlockMutatorCollapse implements IBlockMutator {

	/** One position in this many is considered at all. */
	public static final int CONSIDER_ONE_IN = 5;
	/** ... and of those, all but one in this many fall. */
	public static final int FALL_UNLESS_ONE_IN = 5;

	final Long2ObjectOpenHashMap<BlockState> map = new Long2ObjectOpenHashMap<>();

	@Override
	public void mutatePre(ExplosionVNT explosion, BlockState state, BlockPos pos) {
		map.put(pos.asLong(), state);
	}

	@Override
	public void mutatePost(ExplosionVNT explosion, BlockPos pos) {
		BlockState state = map.get(pos.asLong());
		if(state == null) return;
		Level level = explosion.world;
		if(level.random.nextInt(CONSIDER_ONE_IN) != 0) return;
		if(!canFall(level, pos)) return;
		if(!LeafiaUtil.isSolidVisibleCube(state)) return;
		if(state.getBlock() instanceof EntityBlock) return;
		if(level.random.nextInt(FALL_UNLESS_ONE_IN) > 0) {
			FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
			falling.time = 1;
		}
	}

	/** Upstream's floor walk, which ends at its first step: the position is air and what is under it is replaceable. */
	public static boolean canFall(Level level, BlockPos pos) {
		BlockPos checkPos = pos;
		while(true) {
			if(!level.isEmptyBlock(checkPos)) return false;
			if(level.getBlockState(checkPos.below()).canBeReplaced()) return true;
			checkPos = checkPos.below();
		}
	}
}
