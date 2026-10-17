package com.leafia.contents.machines.reactors.lftr.components.element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code msr_element}, the LFTR Reaction Chamber.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/element/MSRElementBlock.java}:
 * a plain cube with an {@link MSRElementTE}, a pickaxe to break it (harvest level 0), the multiblock,
 * modular and nuclear tooltip tags and its description, and the half-size look overlay every MSR block
 * shares ({@link #printScaled}).
 */
public class MSRElementBlock extends Block implements EntityBlock, ILookOverlay, ITooltipProvider {

	public MSRElementBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		MachineTooltip.addNuclear(tooltip);
		addStandardInfo("tile.msr_element.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MSRElementTE(LeafiaBlockEntities.MSR_ELEMENT.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return serverTicker(level, type, LeafiaBlockEntities.MSR_ELEMENT.get());
	}

	/** Every MSR block ticks its salt on the server only, as upstream's {@code !world.isRemote}. */
	@Nullable
	public static <T extends BlockEntity> BlockEntityTicker<T> serverTicker(Level level, BlockEntityType<T> type, BlockEntityType<?> expected) {
		return level.isClientSide || type != expected ? null : (l, p, s, be) -> ((MSRTEBase) be).update();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		List<String> texts = new ArrayList<>();
		MSRTEBase.appendPrintHook(texts, world, pos);
		printScaled(gui, I18nUtil.resolveKey(getDescriptionId()), texts);
	}

	/**
	 * Upstream's MSR overlay: {@code printGeneric} in magenta, drawn at half size and moved by the
	 * screen height ({@code scale(0.5)} then {@code translate(height, height / 2)}), so it sits a
	 * little right of centre in small print.
	 */
	@OnlyIn(Dist.CLIENT)
	public static void printScaled(GuiGraphics gui, String title, List<String> texts) {
		gui.pose().pushPose();
		gui.pose().scale(0.5F, 0.5F, 1F);
		double height = gui.guiHeight();
		gui.pose().translate(height, height / 2, 0);
		ILookOverlay.printGeneric(gui, title, 0xFF55FF, 0x3F153F, texts);
		gui.pose().popPose();
	}
}
