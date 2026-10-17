package com.leafia.contents.building.generic.mixed;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * One of 231 concretes split down the middle: concrete A on one face, B on the opposite one, and
 * half of each on the four faces between.
 *
 * <p>Upstream: {@code .../building/generic/mixed/BlockMixedConcrete.java}. Every unordered pair
 * of upstream's 22 concretes — four brick finishes, ducrete brick, raw concrete and the sixteen
 * dyes — so a builder can put a straight seam between any two of them instead of a ragged one.
 *
 * <h2>Nearly all of it is data, including the half-split</h2>
 *
 * Upstream's class is 202 lines and this is 90, because two thirds of it is model baking.
 * {@code bakeModel} retextures {@code minecraft:block/cube} six ways for six facings and
 * installs the lot in {@code ModelBakeEvent}, and {@code registerSprite} <b>composites four new
 * sprites per block</b> through {@code TextureAtlasSpriteHalf} — 924 synthesised textures in
 * all.
 *
 * <p>None of that ports. A model with <b>two elements</b>, one from z=0 to z=8 wearing A and one
 * from z=8 to z=16 wearing B, says the same thing: with no {@code uv} written, vanilla derives
 * each face's UV from the element's own bounds, which is exactly the half of the sprite upstream
 * composited. The six facings are six blockstate variants of the one model. So
 * {@code TextureAtlasSpriteHalf} is <b>not ported at all</b> and nothing replaces it — see
 * a generator script and D-1050.
 *
 * <h2>What is left</h2>
 *
 * The facing property, its rotation and mirror, and upstream's placement rule — which is not
 * the modern one and is reproduced rather than approximated. See {@link #getStateForPlacement}.
 */
public class BlockMixedConcrete extends Block {

	public static final MapCodec<BlockMixedConcrete> CODEC = simpleCodec(BlockMixedConcrete::new);

	/** Upstream's {@code BlockDirectional.FACING}: all six, not just the horizontals. */
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	public BlockMixedConcrete(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockMixedConcrete> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/**
	 * Upstream's {@code EnumFacing.getDirectionFromEntityLiving}, reproduced rather than replaced.
	 *
	 * <p>1.21.1's idiom for a six-way directional block is
	 * {@code context.getNearestLookingDirection().getOpposite()}, which is a <em>different rule</em>:
	 * it reads the look vector. Upstream's reads position — if the player is standing within two
	 * blocks horizontally and their eyes are more than two blocks above the target it faces
	 * {@code UP}, if the target is above their eyes it faces {@code DOWN}, and otherwise it is
	 * the opposite of their horizontal facing.
	 *
	 * <p>The difference is visible: crouching next to a block and looking down at it gives
	 * {@code UP} under upstream's rule and the horizontal under the modern one. Ported verbatim
	 * because which way a decorative block's seam runs is the entire point of it.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING,
				directionFromEntityLiving(context.getClickedPos(), context.getPlayer()));
	}

	/** Upstream's helper, which 1.21.1 does not have. */
	private static Direction directionFromEntityLiving(BlockPos pos, LivingEntity placer) {

		if(placer == null) return Direction.NORTH;

		if(Math.abs(placer.getX() - (pos.getX() + 0.5)) < 2.0
				&& Math.abs(placer.getZ() - (pos.getZ() + 0.5)) < 2.0) {

			double eyes = placer.getY() + placer.getEyeHeight();
			if(eyes - pos.getY() > 2.0) return Direction.UP;
			if(pos.getY() - eyes > 0.0) return Direction.DOWN;
		}

		return placer.getDirection().getOpposite();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	/**
	 * Upstream's {@code withMirror} calls {@code state.withRotation(mirrorIn.toRotation(facing))},
	 * which is the standard idiom written the long way round. {@code mirror(rotation)} here.
	 */
	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
