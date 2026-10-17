package com.leafia.contents.machines.reactors.pwr.blocks.components.element;

import java.util.ArrayList;
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
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.block.IToolable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A PWR fuel column: {@code lwr_element}, and the two older skins {@code reactor_element} and
 * {@code reactor_element_blank}, which behave the same. Stacked blocks of one kind are one column; the
 * top block holds the rod ({@link PWRElementTE}) and the others are {@link #stacked} proxies
 * ({@link PWRProxyTE}).
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/element/PWRElementBlock.java},
 * a CE {@code BlockMachineBase} with no GUI. Right-click with a rod puts the <em>whole held stack</em> in the
 * column (upstream's, kept: the slot takes up to 64); a screwdriver takes it out and drops it at the player's
 * feet. What changed:
 *
 * <ul>
 * <li><b>The entity swap.</b> 1.12.2 created a proxy or an element by metadata; 1.21.1 keeps a block entity
 * across a state change of the same block, so {@link #updateState} removes the wrong one before it sets the
 * new state, and {@link #newBlockEntity} makes the right one ({@link PWRElementTE#connectUpper} has already
 * moved the rod up). Upstream's {@code check()}'s {@code Chunk.EnumCreateEntityType.QUEUED} branch - make the
 * entity a new top block is owed - is that swap.</li>
 * <li>The blazing balefire rod's particles are {@link PWRElementClient}.</li>
 * </ul>
 */
public class PWRElementBlock extends Block implements ITooltipProvider, ILookOverlay, PWRComponentBlock, EntityBlock {

	public static final BooleanProperty stacked = BooleanProperty.create("stacked");

	public PWRElementBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(stacked, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(stacked);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!newState.is(state.getBlock()) && tileEntityShouldCreate(level, pos) && level.getBlockEntity(pos) instanceof PWRElementTE element)
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), element.inventory.getStackInSlot(0));
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public boolean shouldRenderOnGUI() {
		return true;
	}

	/** Server: a top column block gets its rod moved to a new top ({@link PWRElementTE#connectUpper}). */
	public void check(Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof PWRElementTE element && !element.isRemoved()) element.connectUpper();
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
		if(worldIn.isClientSide) return;
		check(worldIn, pos);
		updateState(worldIn.getBlockState(pos), worldIn, pos);
		// A column block placed under this one: settle it now its block entity exists (see onPlace).
		if(fromPos.equals(pos.below()) && worldIn.getBlockState(fromPos).getBlock() instanceof PWRElementBlock below)
			below.updateState(worldIn.getBlockState(fromPos), worldIn, fromPos);
		beginDiagnosis(worldIn, pos, fromPos);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(oldState.is(state.getBlock())) return;
		// Upstream calls updateState here. 1.21.1 runs onPlace before it creates the block entity, so a state
		// change from here would race it: a player's placement is right from getStateForPlacement, and a block
		// placed some other way is settled by the block above it, whose neighborChanged runs afterwards.
		if(!level.isClientSide) PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return defaultBlockState().setValue(stacked, context.getLevel().getBlockState(pos.above()).getBlock() instanceof PWRElementBlock);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		MachineTooltip.addNuclear(tooltip);
		addStandardInfo("tile.lwr_element.desc", tooltip);
		MachineTooltip.addUpdate(tooltip, "block.hbm.reactor_element");
		BlastResistanceTooltip.add(this, tooltip);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if(state.getValue(stacked)) return new PWRProxyTE(LeafiaBlockEntities.PWR_ELEMENT_PROXY.get(), pos, state);
		return new PWRElementTE(LeafiaBlockEntities.PWR_ELEMENT.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_ELEMENT.get() ? (l, p, s, be) -> ((PWRElementTE) be).tick() : null;
	}

	/**
	 * Keeps {@link #stacked} true exactly when another element block is above, swapping the block entity
	 * to match, and breaks a block of one kind stacked under another. Server only.
	 */
	public void updateState(BlockState state, Level worldIn, BlockPos pos) {
		if(worldIn.isClientSide) return;
		boolean shouldCreateTEbefore = !state.getValue(stacked);
		boolean shouldCreateTE = tileEntityShouldCreate(worldIn, pos);
		if(shouldCreateTE != shouldCreateTEbefore) {
			worldIn.removeBlockEntity(pos);
			worldIn.setBlockAndUpdate(pos, defaultBlockState().setValue(stacked, !shouldCreateTE));
		}
		// break itself if its combined with other rod types
		if(worldIn.getBlockState(pos.above()).getBlock() instanceof PWRElementBlock && !state.getBlock().equals(worldIn.getBlockState(pos.above()).getBlock()))
			worldIn.destroyBlock(pos, true);
	}

	public BlockPos getTopElement(Level world, BlockPos pos) {
		BlockPos upPos = pos.above();
		while(world.isInWorldBounds(upPos)) {
			BlockState state = world.getBlockState(upPos);
			if(state.getBlock() instanceof PWRElementBlock && state.getBlock().equals(world.getBlockState(pos).getBlock())) pos = upPos;
			else break;
			upPos = pos.above();
		}
		return pos;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		pos = getTopElement(world, pos);
		if(world.getBlockState(pos.above()).getBlock() instanceof PWRElementBlock) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		if(!(world.getBlockEntity(pos) instanceof PWRElementTE element)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		ItemStack stack = element.inventory.getStackInSlot(0);
		if(held.isEmpty()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		if(stack.isEmpty()) {
			if(held.getItem() instanceof ItemTooling) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
			if(world.isClientSide) return ItemInteractionResult.SUCCESS;
			element.inventory.setStackInSlot(0, held.copy());
			player.setItemSlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			world.playSound(null, pos, NtmSounds.ITEM_UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1, 1);
			element.setChanged();
			return ItemInteractionResult.SUCCESS;
		} else if(held.getItem() instanceof ItemTooling tool && tool.getType() == IToolable.ToolType.SCREWDRIVER) {
			if(world.isClientSide) return ItemInteractionResult.SUCCESS;
			element.inventory.setStackInSlot(0, ItemStack.EMPTY);
			Containers.dropItemStack(world, player.getX() - 0.5, player.getY(), player.getZ() - 0.5, stack);
			held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			world.playSound(null, pos, NtmSounds.BLOCK_LOCK_HANG.get(), SoundSource.BLOCKS, 0.85f, 1);
			world.playSound(null, pos, NtmSounds.BLOCK_PIPE_PLACED.get(), SoundSource.BLOCKS, 0.65f, 0.8f);
			element.setChanged();
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		pos = getTopElement(world, pos);
		if(world.getBlockState(pos.above()).getBlock() instanceof PWRElementBlock) return;
		if(!(world.getBlockEntity(pos) instanceof PWRElementTE element)) return;
		List<String> texts = new ArrayList<>();
		ItemStack stack = element.inventory.getStackInSlot(0);
		if(stack.isEmpty())
			texts.add(I18nUtil.resolveKey("tile.lwr_element.overlay.empty"));
		else {
			texts.add(stack.getHoverName().getString());
			List<Component> lines = new ArrayList<>();
			stack.getItem().appendHoverText(stack, Item.TooltipContext.of(world), lines, TooltipFlag.NORMAL);
			for(Component line : lines) texts.add(line.getString());
		}
		ILookOverlay.printGeneric(gui, getName().getString(), 0xFF55FF, 0x3F153F, texts);
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return !(world.getBlockState(pos.above()).getBlock() instanceof PWRElementBlock);
	}

	@Override
	public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource rand) {
		super.animateTick(state, world, pos, rand);
		if(world.getBlockEntity(pos) instanceof PWRElementTE element) {
			Item item = element.inventory.getStackInSlot(0).getItem();
			if(BuiltInRegistries.ITEM.getKey(item).toString().contains("blazingbalefire"))
				PWRElementClient.balefire(world, pos, rand);
		}
	}
}
