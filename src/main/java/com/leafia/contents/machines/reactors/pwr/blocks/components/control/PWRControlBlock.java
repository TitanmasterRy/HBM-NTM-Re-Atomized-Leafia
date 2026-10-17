package com.leafia.contents.machines.reactors.pwr.blocks.components.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.items.tool.ItemTooling;
import com.hbm.sound.NtmSounds;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.block.IToolable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A PWR control rod column, {@code lwr_control} (steel, fast) or {@code reactor_control} (lead, slow).
 * Stacked blocks of one kind are one rod; the top block holds the {@link PWRControlTE} and the others are
 * {@link #stacked}. The renderer draws the frame and the rods; the block itself is invisible.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/control/PWRControlBlock.java}.
 * A screwdriver raises the rod a quarter-block (sneaking lowers it), a name tag puts the rod in a group,
 * and the look overlay shows its depth. What changed:
 *
 * <ul>
 * <li><b>The stacked block holds no block entity.</b> 1.12.2 kept the old one alive across the
 * {@code stacked} change and the client killed it by hand ({@code connectUpper}); here
 * {@link #updateState} removes it when the block becomes stacked, {@link #newBlockEntity} creates none
 * for a stacked state, and a rod un-stacked again gets a fresh one. {@link PWRControlTE#tick} still
 * does nothing under another rod, for a client that has not caught up.</li>
 * <li>{@code onBlockActivated}'s name tag is {@link #useItemOn}; a tooling item falls through to the item,
 * which calls {@link #onScrew}, as upstream's {@code return false} did. A name tag that carries a custom
 * name ("{@code getSubCompound("display") != null}") names the rod after it; any other resets it.</li>
 * </ul>
 */
public class PWRControlBlock extends Block implements ITooltipProvider, EntityBlock, ILookOverlay, PWRComponentBlock, IToolable {

	public static final BooleanProperty stacked = BooleanProperty.create("stacked");

	/**
	 * The control rod OBJs' eight named parts, in upstream's order, which {@code PWRControlRender} draws one
	 * standalone model each of. Here rather than on the renderer so common code (and tests) can name them.
	 */
	public static final String[] PARTS = {"FrameSide", "FrameEnd", "FrameEndTop", "FrameEndBtm", "RodsSide", "RodsEnd", "RodsEndTop", "RodsEndBtm"};

	/** The model path of one part of one mesh ({@code final} or {@code classic}). */
	public static String partModel(String mesh, String part) {
		return "block/pwr_control_" + mesh + "_" + part.toLowerCase(java.util.Locale.ROOT);
	}

	public PWRControlBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(stacked, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(stacked);
	}

	@Override
	public boolean shouldRenderOnGUI() {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_control.desc", tooltip);
		MachineTooltip.addUpdate(tooltip, "block.hbm.reactor_control");
		BlastResistanceTooltip.add(this, tooltip);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(oldState.is(state.getBlock())) return;
		// Upstream calls updateState here; it is getStateForPlacement and the neighbour below's settling
		// instead, because 1.21.1 creates the block entity after onPlace (PWRElementBlock.onPlace).
		if(!level.isClientSide) PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return defaultBlockState().setValue(stacked, context.getLevel().getBlockState(pos.above()).getBlock() instanceof PWRControlBlock);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if(state.getValue(stacked)) return null;
		return new PWRControlTE(LeafiaBlockEntities.PWR_CONTROL.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_CONTROL.get() ? (l, p, s, be) -> ((PWRControlTE) be).tick() : null;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		pos = getTopControl(level, pos);
		if(!(level.getBlockEntity(pos) instanceof PWRControlTE control))
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		control.updateHeight();
		if(held.getItem() instanceof ItemTooling) {
			// moved: the item calls onScrew
		} else if(held.is(Items.NAME_TAG)) {
			if(!level.isClientSide) {
				level.playSound(null, pos, NtmSounds.ITEM_TECH_BLEEP.get(), SoundSource.BLOCKS, 1, 1);
				if(held.has(DataComponents.CUSTOM_NAME))
					control.name = held.getHoverName().getString();
				else
					control.name = PWRControlTE.defaultName;
				LeafiaPacket._start(control).__write(2, control.name).__sendToAffectedClients();
				control.setChanged();
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		BlockPos top = getTopControl(level, pos);
		if(!(level.getBlockEntity(top) instanceof PWRControlTE control))
			return InteractionResult.sidedSuccess(level.isClientSide);
		control.updateHeight();
		return InteractionResult.PASS;
	}

	public BlockPos getTopControl(Level world, BlockPos pos) {
		BlockPos upPos = pos.above();
		while(world.isInWorldBounds(upPos)) {
			if(world.getBlockState(upPos).getBlock() instanceof PWRControlBlock) pos = upPos;
			else break;
			upPos = pos.above();
		}
		return pos;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		pos = getTopControl(world, pos);
		if(world.getBlockState(pos.above()).getBlock() instanceof PWRControlBlock) return;
		if(!(world.getBlockEntity(pos) instanceof PWRControlTE control)) return;
		List<String> texts = new ArrayList<>();
		texts.add("§e" + String.format("%01.2f", control.position * control.height) + "m");
		texts.addAll(Arrays.asList(I18nUtil.resolveKey("tile.lwr_control.overlay.desc1").split("\\$")));
		texts.add("");
		texts.add("§8" + control.name);
		texts.addAll(Arrays.asList(I18nUtil.resolveKey("tile.lwr_control.overlay.desc2").split("\\$")));
		ILookOverlay.printGeneric(gui, getName().getString(), 0xFF55FF, 0x3F153F, texts);
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return !(world.getBlockState(pos.above()).getBlock() instanceof PWRControlBlock);
	}

	/**
	 * Keeps {@link #stacked} true exactly when another rod block is above, and breaks a block of one kind
	 * stacked under the other. Server only.
	 */
	public void updateState(BlockState state, Level worldIn, BlockPos pos) {
		if(worldIn.isClientSide) return;
		boolean shouldCreateTEbefore = !state.getValue(stacked);
		boolean shouldCreateTE = tileEntityShouldCreate(worldIn, pos);
		if(shouldCreateTE != shouldCreateTEbefore) {
			if(!shouldCreateTE) worldIn.removeBlockEntity(pos);
			worldIn.setBlockAndUpdate(pos, defaultBlockState().setValue(stacked, !shouldCreateTE));
		}
		// break itself if its combined with other rod types
		if(worldIn.getBlockState(pos.above()).getBlock() instanceof PWRControlBlock && !state.getBlock().equals(worldIn.getBlockState(pos.above()).getBlock()))
			worldIn.destroyBlock(pos, true);
		updateHeight(worldIn, pos.above());
		updateHeight(worldIn, pos.below());
	}

	public void updateHeight(Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof PWRControlTE control) control.updateHeight();
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
		if(worldIn.isClientSide) return;
		updateState(worldIn.getBlockState(pos), worldIn, pos);
		if(fromPos.equals(pos.below()) && worldIn.getBlockState(fromPos).getBlock() instanceof PWRControlBlock below)
			below.updateState(worldIn.getBlockState(fromPos), worldIn, fromPos);
		beginDiagnosis(worldIn, pos, fromPos);
	}

	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {
		pos = getTopControl(world, pos);
		if(!(world.getBlockEntity(pos) instanceof PWRControlTE control)) return true;
		control.updateHeight();
		if(tool == ToolType.SCREWDRIVER) {
			if(!world.isClientSide) {
				double ogPos = control.targetPosition;
				if(player.isShiftKeyDown())
					control.targetPosition = Math.max(control.targetPosition - 0.25 / control.height, 0);
				else
					control.targetPosition = Math.min(control.targetPosition + 0.25 / control.height, 1);
				if(control.targetPosition == ogPos) return false;
				world.playSound(null, pos, NtmSounds.BLOCK_LOCK_OPEN.get(), SoundSource.BLOCKS, 0.5f, 1);
			}
			return true;
		}
		return false;
	}
}
