package com.leafia.contents.building.sign;

import com.hbm.blocks.BlockContainerBase;
import com.leafia.dev.container_utility.LeafiaPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A large letter hung on a wall. Thirty-six of them, one per character of
 * {@code 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ}, dyeable with any dye.
 *
 * <p>Upstream: {@code com/leafia/contents/building/sign/SignBlock.java}.
 *
 * <h2>There is no model, and that is deliberate upstream</h2>
 *
 * Upstream extends 1.12.2's {@code BlockContainer}, whose {@code getRenderType} is
 * {@code INVISIBLE}, and ships no blockstate, model or texture for any of the thirty-six. The
 * block is invisible geometry; everything a player sees is the letter drawn by {@link SignRender}
 * with the font.
 *
 * <p>That does <b>not</b> carry over for free, which a test caught rather than a reading did:
 * {@link BlockContainerBase} sits on {@code Block}, not on {@code BaseEntityBlock}, so its render
 * shape is {@code MODEL} like any other block's and the sign came out as a solid cube with a
 * letter inside it. {@link #getRenderShape} states it explicitly. The generated blockstates still
 * exist, because an invisible block is still asked for a particle texture and 1.21.1 logs a
 * missing model for a block that has none.
 *
 * <h2>One name for thirty-six blocks</h2>
 *
 * Upstream overrides {@code getLocalizedName} to format one key, {@code tile.letter_sign.name} =
 * "Large Sign %s", with the letter. {@link #getName} is where that lives now, so there is still
 * one translation key rather than thirty-six — which also means a translator writes the phrase
 * once.
 */
public class SignBlock extends BlockContainerBase {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	/*
	 * Upstream's four boxes. Each is an eighth of a block thick against the wall the sign hangs
	 * on, and the pairing looks inverted on purpose: a sign FACING north sits against the block to
	 * its south, so its box is at the north end of its own space. Kept as upstream has it.
	 */
	protected static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 2, 16, 16);
	protected static final VoxelShape WEST_SHAPE = Block.box(14, 0, 0, 16, 16, 16);
	protected static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 2);
	protected static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 14, 16, 16, 16);

	/** The character this block draws. Read by the renderer and by {@link #getName}. */
	public final String letter;

	public SignBlock(Properties properties, String letter) {
		super(properties);
		this.letter = letter;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SignTE(pos, state);
	}

	/** Upstream's {@code BlockContainer} default, which this hierarchy does not share. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/** Upstream's {@code tile.letter_sign.name}: one key, the letter substituted in. */
	@Override
	public MutableComponent getName() {
		return Component.translatable("block.leafia.letter_sign", letter);
	}

	/**
	 * A dye in hand colours the sign.
	 *
	 * <p>Upstream reads the colour from the stack's damage value; 1.21.1 has a {@link DyeItem} per
	 * colour that carries it directly. The packet is upstream's — slot 0, the colour index — and it
	 * still goes out even though vanilla's block-entity sync would also carry the change, because
	 * that is the channel {@link SignTE} is written against.
	 */
	@Override
	public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {

		// Upstream returns false for anything that is not a dye, which in 1.12.2 means "not
		// handled, let the item try" — that is PASS_TO_DEFAULT_BLOCK_INTERACTION here, not FAIL.
		if(!(stack.getItem() instanceof DyeItem dye))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if(!level.isClientSide && level.getBlockEntity(pos) instanceof SignTE sign) {
			sign.color = dye.getDyeColor();
			sign.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			LeafiaPacket._start(sign).__write(0, sign.color.getId()).__sendToAffectedClients();
		}

		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch(state.getValue(FACING)) {
			case SOUTH -> SOUTH_SHAPE;
			case WEST -> WEST_SHAPE;
			case EAST -> EAST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}

	/** Upstream gives it {@code NULL_AABB}: you can walk through a sign. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}

	/**
	 * The wall it hangs on has to be solid, and it drops when that wall goes.
	 *
	 * <p>Upstream does this from {@code neighborChanged} by hand — drop the item, set the block to
	 * air. 1.21.1 does exactly that for any block whose {@code canSurvive} turns false, so the
	 * rule is stated once here instead of being implemented twice. Unlike the wind turbine's
	 * placement rule (D-1028), {@code canSurvive} <em>is</em> the right hook: a sign genuinely
	 * should re-check on every neighbour change.
	 *
	 * <p><b>One deviation.</b> Upstream checks the wall only in {@code neighborChanged}, never at
	 * placement, so a sign can be hung in mid-air and stays there until something pokes the block
	 * next to it. {@code canSurvive} is checked at placement too, so here it simply cannot be
	 * placed. Same end state, different moment; recorded in D-1029 rather than replicated, because
	 * replicating it means hand-rolling the drop that vanilla already does. Upstream's cactus
	 * check goes with it — it is copy-paste from vanilla's banner code and governs nothing once
	 * the wall rule is stated properly.
	 */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		return level.getBlockState(pos.relative(facing.getOpposite())).isSolid();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
