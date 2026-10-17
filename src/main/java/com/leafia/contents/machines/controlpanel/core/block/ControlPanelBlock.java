package com.leafia.contents.machines.controlpanel.core.block;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.IPersistentNBT;
import com.mojang.serialization.MapCodec;

import api.hbm.block.IToolable.ToolType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.leafia.init.LeafiaBlockEntities;

/**
 * The Custom Control Panel's desk block. Adapted from NTM-CE's {@code BlockControlPanel} (D-3063), with Leafia's
 * {@code MixinBlockControlPanel} folded in: the skin, and the panel riding the dropped item.
 *
 * <h2>What a click does</h2>
 * <ul>
 * <li><b>A screwdriver</b> (any item the base mod registers as one) in a hand, with a block in the <i>other</i> hand: the panel
 * is dressed as that block (Leafia's skin; a block that is not drawn as a plain model is refused, as upstream's
 * {@code getRenderType} test did) and the nearby clients are told. Without a block it opens the editor, through
 * {@link ControlPanelTE#editorOpener}, which batch 6 sets: until then it does nothing, because a menu with no screen
 * soft-locks the player.</li>
 * <li><b>Anything else</b> is a press. CE worked out which control the player's eye ray hits <i>on the client</i>, with the
 * client's matrices, and sent the index to the server as an {@code NBTControlPacket}; the server re-checks reach and index
 * (§2.5). The ray cast is batch 5's and arrives through {@link #clientClick}; the server's click does nothing.</li>
 * </ul>
 * Either way the click is consumed, as CE's was: a panel cannot be built against with a block in hand unless the player sneaks.
 *
 * <h2>Redstone</h2>
 * A panel is a signal source on every face. {@code getSignal} and {@code getDirectSignal} answer from the block entity's outputs
 * with the <b>direction the neighbour asks along</b> used as CE used it (its {@code getWeakPower(.., side)} indexed the output by
 * {@code side} unchanged), so a node writing face {@code F} is read by the neighbour that is on the opposite side of the panel
 * from {@code F}. That is CE's behaviour kept, flagged in D-3063 for review. A neighbour change re-reads the inputs.
 *
 * <h2>The drop</h2>
 * Breaking one drops the panel with everything on it ({@link IPersistentNBT}); a creative break drops nothing (vanilla's rule,
 * which is also Leafia's {@code setDestroyedByCreativePlayer}). Placing the item puts it back.
 *
 * <p>The slab and the controls are drawn by the block entity renderer {@code core.client.ControlPanelRender} (D-3065); the
 * block itself draws nothing, and its blockstate model survives only as the source of the breaking particles.
 */
public class ControlPanelBlock extends BaseEntityBlock {

	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");
	public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

	public static final MapCodec<ControlPanelBlock> CODEC = simpleCodec(ControlPanelBlock::new);

	/**
	 * The client's click: finds the control the player is looking at and sends the press. Set by
	 * {@code core.client.ControlPanelClient} (D-3065); returns whether a control was hit. Null on a server.
	 */
	@FunctionalInterface
	public interface ClientClick {
		boolean click(Level level, BlockPos pos, Player player);
	}

	public static ClientClick clientClick = null;

	public ControlPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UP, DOWN, FACING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ControlPanelTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide)
			return null;
		return createTickerHelper(type, LeafiaBlockEntities.CONTROL_PANEL.get(), ControlPanelTE::serverTick);
	}

	// ------------------------------------------------------------------ placement and shape

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(UP, clicked == Direction.UP)
				.setValue(DOWN, clicked == Direction.DOWN);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if(!level.isClientSide)
			IPersistentNBT.restoreData(level, pos, stack);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if(level.getBlockEntity(pos) instanceof ControlPanelTE te)
			return Shapes.create(te.getBoundingBox(state.getValue(UP), !state.getValue(UP) && state.getValue(DOWN), state.getValue(FACING)));
		return Shapes.block();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// The slab and every control are drawn by ControlPanelRender (batch 5, D-3065); the chunk mesh draws nothing.
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	// ------------------------------------------------------------------ clicks

	/** Whether this stack is one of the base mod's screwdrivers. */
	public static boolean isScrewdriver(ItemStack stack) {
		return !stack.isEmpty() && ToolType.getType(stack) == ToolType.SCREWDRIVER;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {

		if(!isScrewdriver(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if(!level.isClientSide && level.getBlockEntity(pos) instanceof ControlPanelTE panel) {
			// Leafia's skin: a screwdriver in one hand and a block in the other.
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if(other.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().getRenderShape() == RenderShape.MODEL) {
				panel.setSkin(blockItem.getBlock());
				panel.sendSkinPackets();
				return ItemInteractionResult.SUCCESS;
			}
			if(player instanceof ServerPlayer sp && ControlPanelTE.editorOpener != null)
				ControlPanelTE.editorOpener.open(sp, panel);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if(level.isClientSide && clientClick != null)
			clientClick.click(level, pos, player);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	// ------------------------------------------------------------------ drops

	/** Upstream drops the panel from {@code breakBlock} through {@code IPersistentNBT}; vanilla hands the block entity to this. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof IPersistentNBT persistent)
			return persistent.getDrops(this);
		return new ArrayList<>();
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return new ItemStack(this);
	}

	// ------------------------------------------------------------------ redstone

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		if(level.getBlockEntity(pos) instanceof ControlPanelTE panel)
			return panel.getWeakRedstoneOutput(side);
		return 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		if(level.getBlockEntity(pos) instanceof ControlPanelTE panel)
			return panel.getStrongRedstoneOutput(side);
		return 0;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if(!level.isClientSide && level.getBlockEntity(pos) instanceof ControlPanelTE panel)
			panel.captureRedstoneInputChanges();
	}
}
