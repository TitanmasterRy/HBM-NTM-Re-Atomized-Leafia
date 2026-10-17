package com.leafia.contents.machines.reactors.pwr.blocks.components.occs.outlet;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The PWR OCCS outlet, {@code lwr_occs_out}: where the Offline Core Cooling System's water (from rods it
 * barely cooled) and spent steam (from rods it cooled by more than 0.05 degrees) come out. A casing block.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/occs/outlet/PWROCCSOutBlock.java},
 * a CE {@code BlockRadResistant} ({@link AddonRadResistantBlockBaked} here).
 */
public class PWROCCSOutBlock extends AddonRadResistantBlockBaked implements ITooltipProvider, EntityBlock, PWRComponentBlock, ILookOverlay {

	public PWROCCSOutBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_occs_out.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide && !oldState.is(state.getBlock()))
			PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return true;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PWROCCSOutTE(LeafiaBlockEntities.PWR_OCCS_OUT.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_OCCS_OUT.get() ? (l, p, s, be) -> PWRAssignableEntity.tick(l, p, s, (PWRAssignableEntity) be) : null;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(!(world.getBlockEntity(pos) instanceof PWROCCSOutTE te)) return;
		List<String> texts = new ArrayList<>();
		PWRData data = te.getLinkedCoreDiagnosis();
		if(data != null) {
			texts.add(ChatFormatting.RED + "<- " + ChatFormatting.RESET + data.tanks[6].getTankType().getLocalizedName() + ": " + data.tanks[6].getFill() + "/" + data.tanks[6].getMaxFill() + "mB");
			texts.add(ChatFormatting.RED + "<- " + ChatFormatting.RESET + data.tanks[7].getTankType().getLocalizedName() + ": " + data.tanks[7].getFill() + "/" + data.tanks[7].getMaxFill() + "mB");
		}
		ILookOverlay.printGeneric(gui, getName().getString(), 0xFF55FF, 0x3F153F, texts);
	}
}
