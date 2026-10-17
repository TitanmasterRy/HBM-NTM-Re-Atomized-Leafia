package com.leafia.contents.debug.circle_test;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreBlock;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code test_circle}: used, it builds the APR's torus of radius 6 round itself.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/circle_test/CircleTestBlock.java}. On the server it asks
 * {@link APRCoreBlock#generateTorus} for the ring - four hull-and-support circles at radii 5.5, 6.5, 5 and 7 one block
 * down, three deep, and the bladed inner circle at 6 on the block's own layer, five deep - and sets every block it
 * names. {@link #generateCircle} is upstream's unused helper, kept with it. The torus is the APR's blocks, so the block
 * registers only with the APR's content ({@code LeafiaFeatures.debugCircle()}).
 */
public class CircleTestBlock extends Block {

	/** Upstream's radius. */
	public static final double RADIUS = 6;

	public CircleTestBlock(Properties properties) {
		super(properties);
	}

	/** The blocks a use sets: {@code APRCoreBlock.generateTorus(pos, 6, ...)}. */
	public static Map<BlockPos, BlockState> torus(BlockPos pos) {
		Map<BlockPos, BlockState> states = new HashMap<>();
		APRCoreBlock.generateTorus(pos, RADIUS, states);
		return states;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(!level.isClientSide) {
			for(Entry<BlockPos, BlockState> entry : torus(pos).entrySet())
				level.setBlockAndUpdate(entry.getKey(), entry.getValue());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Upstream's {@code generateCircle}: a ring of {@code state} on one layer, a quarter turned and mirrored four ways. Unused upstream. */
	public static void generateCircle(Level world, BlockPos pos, double radius, BlockState state) {
		double subdivs = Math.floor(radius * 2 * Math.PI / 4);
		FiaMatrix mat = new FiaMatrix(new Vec3(0.5, 0.5, 0.5));
		for(int i = 0; i <= subdivs; i++) {
			double ratio = i / subdivs;
			BlockPos offset = BlockPos.containing(mat.rotate(RotationOrder.XYZ, 0, ratio * 90, 0).translate(0, 0, -radius + 0.25).position);
			world.setBlockAndUpdate(pos.offset(offset.getX(), 0, offset.getZ()), state);
			world.setBlockAndUpdate(pos.offset(-offset.getX(), 0, offset.getZ()), state);
			world.setBlockAndUpdate(pos.offset(offset.getX(), 0, -offset.getZ()), state);
			world.setBlockAndUpdate(pos.offset(-offset.getX(), 0, -offset.getZ()), state);
		}
	}
}
