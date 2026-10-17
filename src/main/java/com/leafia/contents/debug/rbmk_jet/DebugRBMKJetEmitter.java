package com.leafia.contents.debug.rbmk_jet;

import com.leafia.dev.optimization.LeafiaParticlePacket.RBMKJetParticle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code test_rbmk_jet}: used, it shows the RBMK's steam jet over itself, on the clicking client only.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/rbmk_jet/DebugRBMKJetEmitter.java}: on the client, a
 * {@code ParticleRBMKJet} of age {@link #MAX_AGE} at the bottom centre of the block, upstream's
 * {@code (x + 0.5, y, z + 0.5)}; nothing on the server, and the click is used on both sides. Here the particle is made
 * through the particle packet's own local emit ({@code RBMKJetParticle.emitLocal}), which is the same constructor
 * behind the dispatch that keeps client classes off a server (D-3000).
 */
public class DebugRBMKJetEmitter extends Block {

	public static final int MAX_AGE = 30;

	public DebugRBMKJetEmitter(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(level.isClientSide)
			new RBMKJetParticle(MAX_AGE).emitLocal(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Vec3.ZERO);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
