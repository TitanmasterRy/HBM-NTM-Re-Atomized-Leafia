package com.leafia.contents.building.pinkdoor;

import java.util.List;

import com.hbm.items.tool.ItemModDoor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * The pink door's item (D-3028): places a {@link BlockPinkDoor}, from the top face of a block only, and says what it is.
 *
 * <p>Ported from {@code com.leafia.contents.building.pinkdoor.ItemPinkDoor}. Upstream copies 1.12.2's {@code ItemDoor.onItemUse}
 * with the block swapped for the pink door; the base mod's {@link ItemModDoor} is vanilla's {@code DoubleHighBlockItem}, which is that
 * routine, so the only thing left to say is upstream's first line: a click on any face but the top fails.
 *
 * <p>The tooltip is upstream's four lines: {@code [trait.nuclearbomb]}, a radius of 220, {@code [trait.fallout]} and a radius of
 * "???". Upstream shows the last two unless NTM-CE's {@code BombConfig.disableNuclear} is set; this base mod has no such option, so
 * they always show. The four keys are NTM-CE's, whose language file is not in the reference; their English here is this port's.
 */
public class ItemPinkDoor extends ItemModDoor {

	public static final String RADIUS = "220";

	public ItemPinkDoor(Block door, Properties properties) {
		super(door, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if(context.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
		return super.useOn(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("[").append(Component.translatable("trait.nuclearbomb")).append("]").withStyle(ChatFormatting.DARK_GREEN));
		tooltip.add(Component.literal(" ").append(Component.translatable("desc.radius", RADIUS)).withStyle(ChatFormatting.YELLOW));
		tooltip.add(Component.literal("[").append(Component.translatable("trait.fallout")).append("]").withStyle(ChatFormatting.DARK_GREEN));
		tooltip.add(Component.literal(" ").append(Component.translatable("desc.radius", "???")).withStyle(ChatFormatting.YELLOW));
	}
}
