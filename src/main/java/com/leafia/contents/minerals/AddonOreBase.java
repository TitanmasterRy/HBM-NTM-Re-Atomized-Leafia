package com.leafia.contents.minerals;

import java.util.List;

import com.leafia.dev.blocks.BlastResistanceTooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * Ported from {@code com.leafia.contents.minerals.AddonOreBase}: the base of the addon's own ores.
 *
 * <p>Upstream is a {@code BlockOre} - 1.12.2's coal-ore/lapis/emerald class, which here is a
 * plain stone block that drops itself - that sets a translation key, a registry name, the
 * creative tab, a pickaxe harvest level and "no random tick", then registers itself in a static
 * list. All of that is {@code DeferredRegister} and {@code Properties} now. The harvest level is
 * a {@code needs_*_tool} tag, written by a generator script, and the drop is the
 * loot table. What is behaviour is the tooltip: a blast-resistance line over 50, upstream's
 * {@code addInformation} (D-1076).
 */
public class AddonOreBase extends Block {

	public AddonOreBase(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		BlastResistanceTooltip.add(this, tooltip);
	}
}
