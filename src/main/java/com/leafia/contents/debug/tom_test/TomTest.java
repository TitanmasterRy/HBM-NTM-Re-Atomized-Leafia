package com.leafia.contents.debug.tom_test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code test_tom}: a block that removes itself when used.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/tom_test/TomTest.java}. Its {@code onBlockActivated} once spawned a
 * Tom blast of 30 and its cloud at the block; that body is commented out upstream, and what is left - on the server,
 * set the block to air, and answer that the click was used - is what is ported (D-3031). Registered for parity with
 * upstream's registry, under {@code features.debugBlocks}.
 */
public class TomTest extends Block {

	public TomTest(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(!level.isClientSide) {
			/*EntityTomBlast tom = ...; destructionRange = 30; EntityCloudTom cloud = new EntityCloudTom(worldIn, 30);*/
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
