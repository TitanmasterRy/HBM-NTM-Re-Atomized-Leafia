package com.leafia.contents.miscellanous.slop;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.TransparentBlock;

/**
 * `slop_reactor_glass`: the reactor's walls.
 *
 * <p>Upstream: {@code .../slop/SlopGlass.java}, which is {@code BlockNTMGlass(IRON, CUTOUT, true,
 * true, name)} - drops itself, and is "radiation resistant". Both dissolve into things 1.21.1
 * already has:
 *
 * <ul>
 * <li>{@code BlockNTMGlass} is {@code BlockBreakable} with {@code lightOpacity 0} and
 *     {@code useNeighborBrightness}, which is vanilla's {@link TransparentBlock} - it culls the
 *     face between two glass blocks and lets light through - under the block's properties.</li>
 * <li><b>Radiation resistance has nowhere to go.</b> {@code IRadResistantBlock} is read by NTM-CE's
 *     pocket-based radiation system ({@code RadiationSystemNT}), and this port's base mod keeps
 *     radiation per chunk, with no per-block resistance to set. Upstream's flag is therefore not
 *     carried, only the tooltip that announces it. See D-1082.</li>
 * </ul>
 *
 * <p>The tooltip is {@code BlockNTMGlass}'s: dark green, bracketed, {@code trait.radshield}.
 */
public class SlopGlass extends TransparentBlock {

	public static final MapCodec<SlopGlass> CODEC = simpleCodec(SlopGlass::new);

	public SlopGlass(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends SlopGlass> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
