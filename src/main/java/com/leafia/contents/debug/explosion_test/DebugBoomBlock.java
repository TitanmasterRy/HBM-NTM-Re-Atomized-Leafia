package com.leafia.contents.debug.explosion_test;

import com.hbm.interfaces.IBomb;
import com.leafia.overwrite_contents.LeafiaMeteorCrater;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code test_boom}: a bomb that digs the addon's meteor crater where it stands.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/explosion_test/DebugBoomBlock.java}, an {@code IBomb}. Its
 * {@code explode}, on the server, digs {@code createMeteorCrater} at the block and plays
 * {@code createMeteorExplosionEffect} at its centre, and answers {@code DETONATED} on either side. Both statics moved
 * to {@link LeafiaMeteorCrater} with the meteor batch, which is where {@code MixinEntityMeteor} called them from
 * (D-3022), so this calls them there. Upstream's {@code ExplosionVNT} experiment in the same body is commented out, as
 * are the nuke particles in its {@code onBlockActivated}, which therefore only reports the click used. The block itself
 * is left standing: a crater is dug down from the surface around it, and nothing in upstream removes it. A working bomb
 * in a creative tab is upstream's choice, kept (D-3031).
 */
public class DebugBoomBlock extends Block implements IBomb {

	public DebugBoomBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BombReturnCode explode(Level world, BlockPos pos) {
		if(!world.isClientSide) {
			LeafiaMeteorCrater.createMeteorCrater(world, pos);
			LeafiaMeteorCrater.createMeteorExplosionEffect(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		}
		return BombReturnCode.DETONATED;
	}
}
