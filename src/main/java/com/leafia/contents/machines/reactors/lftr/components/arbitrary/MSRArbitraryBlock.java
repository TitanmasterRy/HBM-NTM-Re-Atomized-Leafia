package com.leafia.contents.machines.reactors.lftr.components.arbitrary;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.items.tool.ItemTooling;
import com.hbm.sound.NtmSounds;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementBlock;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.block.IToolable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code msr_arbitrary}, the LFTR Arbitrary Block.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/arbitrary/MSRArbitraryBlock.java},
 * a CE {@code BlockMachineBase} with no GUI. Clicked with a block item while empty, it takes one of
 * the stack (the plug sound); clicked with a screwdriver while full, it drops the block at the player's
 * feet and the screwdriver takes a point of wear. Anything else passes.
 *
 * <p>Two of upstream's checks never fire, and are kept as the dead code they are (D-021): the held
 * item being an {@code ItemTooling} (no tool is a block item), and the block's render type being a
 * model, which upstream asks of {@code Block instanceof IBlockProperties} - an interface only a block
 * <em>state</em> implements in 1.12.2. So any block item goes in, invisible ones included; the renderer
 * then draws whatever sprite that block's model offers.
 */
public class MSRArbitraryBlock extends Block implements EntityBlock, ITooltipProvider, ILookOverlay {

	public MSRArbitraryBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.msr_arbitrary.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MSRArbitraryTE(LeafiaBlockEntities.MSR_ARBITRARY.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return MSRElementBlock.serverTicker(level, type, LeafiaBlockEntities.MSR_ARBITRARY.get());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level world, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if(!(world.getBlockEntity(pos) instanceof MSRArbitraryTE element))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		ItemStack stack = element.inventory.getStackInSlot(0);
		if(held.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if(stack.isEmpty()) {
			if(held.getItem() instanceof BlockItem) {
				if(held.getItem() instanceof ItemTooling) // never: no tool is a block item
					return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
				ItemStack stack1 = held.copy();
				stack1.setCount(1);
				element.inventory.setStackInSlot(0, stack1);
				if(world.isClientSide) return ItemInteractionResult.SUCCESS;
				held.shrink(1);
				player.inventoryMenu.broadcastChanges();
				world.playSound(null, pos, NtmSounds.ITEM_UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1F, 1F);
				element.setChanged();
				return ItemInteractionResult.SUCCESS;
			}
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		} else {
			if(held.getItem() instanceof ItemTooling tooling) {
				if(tooling.getType().equals(IToolable.ToolType.SCREWDRIVER)) {
					element.inventory.setStackInSlot(0, ItemStack.EMPTY);
					if(world.isClientSide) return ItemInteractionResult.SUCCESS;
					Containers.dropItemStack(world, player.getX(), player.getY(), player.getZ(), stack);
					held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
					world.playSound(null, pos, NtmSounds.BLOCK_LOCK_HANG.get(), SoundSource.BLOCKS, 0.85F, 1F);
					world.playSound(null, pos, NtmSounds.BLOCK_PIPE_PLACED.get(), SoundSource.BLOCKS, 0.65F, 0.8F);
					element.setChanged();
					return ItemInteractionResult.SUCCESS;
				}
			}
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(!(world.getBlockEntity(pos) instanceof MSRArbitraryTE element))
			return;
		List<String> texts = new ArrayList<>();
		ItemStack stack = element.inventory.getStackInSlot(0);
		if(stack.isEmpty())
			texts.add("Empty");
		else {
			texts.add(stack.getHoverName().getString());
			List<Component> info = new ArrayList<>();
			stack.getItem().appendHoverText(stack, Item.TooltipContext.of(world), info, TooltipFlag.NORMAL);
			for(Component line : info) texts.add(line.getString());
		}
		MSRTEBase.appendPrintHook(texts, world, pos);
		MSRElementBlock.printScaled(gui, I18nUtil.resolveKey(getDescriptionId()), texts);
	}
}
