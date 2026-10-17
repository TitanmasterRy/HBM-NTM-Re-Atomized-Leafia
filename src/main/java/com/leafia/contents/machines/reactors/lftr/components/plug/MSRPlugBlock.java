package com.leafia.contents.machines.reactors.lftr.components.plug;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementBlock;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code msr_plug}, the LFTR Fuel Plug.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/plug/MSRPlugBlock.java}, a
 * rotatable CE {@code BlockMachineBase}: it faces the player who placed it (four ways), shields
 * radiation ({@code IRadResistantBlock}, the base's M1 seam, with the section rebuild on place and
 * break), and a fluid identifier used on it sets the coolant it takes - any {@code FT_LFTRCoolant}
 * fluid; anything else is refused and the click passes. See {@link MSRPlugTE}.
 */
public class MSRPlugBlock extends Block implements EntityBlock, ILookOverlay, IRadResistantBlock, ITooltipProvider {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public MSRPlugBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** {@code BlockMachineBase.onBlockPlacedBy}: the opposite of the placer's horizontal facing. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
	public int getResistance() {
		return 1;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if(!oldState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!newState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.msr_plug.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MSRPlugTE(LeafiaBlockEntities.MSR_PLUG.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return MSRElementBlock.serverTicker(level, type, LeafiaBlockEntities.MSR_PLUG.get());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if(stack.getItem() instanceof IItemFluidIdentifier identifier) {
			FluidType ntmf = identifier.getType(level, pos, stack);
			if(!ntmf.hasTrait(FT_LFTRCoolant.class)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			if(!level.isClientSide) {
				if(level.getBlockEntity(pos) instanceof MSRPlugTE plug)
					plug.setType(ntmf);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		List<String> texts = new ArrayList<>();
		if(world.getBlockEntity(pos) instanceof MSRPlugTE plug) {
			texts.add("&[" + plug.getInputType().getColor() + "&]" + plug.getInputType().getLocalizedName());
			if(plug.molten)
				texts.add(ChatFormatting.DARK_RED + I18nUtil.resolveKey("tile.msr_plug.molten"));
		}
		MSRTEBase.appendPrintHook(texts, world, pos);
		MSRElementBlock.printScaled(gui, I18nUtil.resolveKey(getDescriptionId()), texts);
	}
}
