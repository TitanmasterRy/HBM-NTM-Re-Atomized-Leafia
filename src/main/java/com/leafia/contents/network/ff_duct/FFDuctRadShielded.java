package com.leafia.contents.network.ff_duct;

import java.util.List;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.blocks.BlastResistanceTooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code ff_duct_solid_shielded}, the "Rad Shielded Special Pipe": a full block of compound mesh
 * with a Forge-fluid duct through it.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/FFDuctRadShielded.java}. It is an ordinary
 * {@link FFDuctTE} duct to the network - it connects on all six faces by the same rule, and its
 * neighbours see it as a duct - but it draws as a cube ({@code BlockBakeFrame.cubeAll} of the base
 * mod's sealed solid duct texture, a model JSON here) and it stops radiation.
 *
 * <p>The shielding is NTM-CE's {@code IRadResistantBlock}, the base mod's M1 seam
 * ({@link IRadResistantBlock}, D-2195): it answers 1, and placing or breaking it marks the section
 * for a rebuild ({@code RadiationSystemNT.markSectionForRebuild} upstream,
 * {@link ChunkRadiationManager#markSectionForRebuild} here), which has a real effect only once a
 * handler that caches section geometry exists.
 */
public class FFDuctRadShielded extends FFDuctBase implements IRadResistantBlock, ILookOverlay {

	public FFDuctRadShielded(Properties properties) {
		super(properties);
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
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		FFDuctStandard.printDuct(gui, world, pos, getDescriptionId());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
		BlastResistanceTooltip.add(this, tooltip);
	}
}
