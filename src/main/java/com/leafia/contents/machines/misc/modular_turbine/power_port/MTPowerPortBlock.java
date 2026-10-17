package com.leafia.contents.machines.misc.modular_turbine.power_port;

import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * `modular_turbine_port`, the "electricity port": where a modular turbine's power comes out.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/power_port/MTPowerPortBlock.java}.
 *
 * <p>An ordinary six-way block, not a turbine part. Put it against any part of a turbine with its
 * port face pointing away, and it hands the network whatever the core generated that tick — see
 * {@link MTPowerPortTE}.
 *
 * <h2>Facing, piston-style</h2>
 *
 * Upstream sets {@code FACING} with {@code EnumFacing.getDirectionFromEntityLiving}: towards the
 * player horizontally, but straight up or down if the player is within two blocks sideways and
 * more than two above (or at all below). 1.21.1's piston uses
 * {@code getNearestLookingDirection()}, which follows where the player <em>looks</em> rather than
 * where they <em>stand</em>, and would face differently from a ledge. So
 * {@link #facingFromEntity} is upstream's rule, line for line.
 */
public class MTPowerPortBlock extends BaseEntityBlock {

	public static final MapCodec<MTPowerPortBlock> CODEC = simpleCodec(MTPowerPortBlock::new);
	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	public MTPowerPortBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends MTPowerPortBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** {@code BaseEntityBlock} defaults to invisible; this is a plain cube. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	/**
	 * 1.12.2's {@code EnumFacing.getDirectionFromEntityLiving}. The positions are compared without
	 * the half-block centring a 1.21.1 author would add, because upstream compares raw coordinates.
	 */
	public static Direction facingFromEntity(BlockPos pos, LivingEntity placer) {
		if(Math.abs((float) placer.getX() - (float) pos.getX()) < 2.0F
				&& Math.abs((float) placer.getZ() - (float) pos.getZ()) < 2.0F) {
			double eye = placer.getY() + placer.getEyeHeight();
			if(eye - pos.getY() > 2.0D) return Direction.UP;
			if(pos.getY() - eye > 0.0D) return Direction.DOWN;
		}
		return placer.getDirection().getOpposite();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getPlayer() != null
				? facingFromEntity(context.getClickedPos(), context.getPlayer())
				: context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MTPowerPortTE(LeafiaBlockEntities.MODULAR_TURBINE_POWER_PORT.get(), pos, state);
	}

	/** Server only: upstream's {@code update()} does nothing on a client. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.MODULAR_TURBINE_POWER_PORT.get(), MTPowerPortTE::tick);
	}
}
