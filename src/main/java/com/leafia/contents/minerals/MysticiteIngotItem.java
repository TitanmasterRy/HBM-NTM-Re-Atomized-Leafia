package com.leafia.contents.minerals;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The mysticite ingot: a plain item with a six-line joke for a tooltip.
 *
 * <p>Upstream: the anonymous {@code AddonItemBaked} subclass in {@code AddonItems.Resources}
 * that overrides {@code addInformation}. The lines are upstream's own English, written straight
 * into the code rather than the lang file, which is how upstream has them.
 *
 * <p>The ingot cannot be made yet. Upstream melts it out of the mystic-system alloys in a
 * crucible and a solar blaster, neither of which exists here, so it is creative-only. It is
 * registered because the mysticite shield names it as its repair item.
 */
public class MysticiteIngotItem extends Item {

	public MysticiteIngotItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal("The ultimate alloy."));
		tooltip.add(Component.literal(""));
		tooltip.add(Component.literal("You cannot shape it due to it's"));
		tooltip.add(Component.literal("extreme durability and heat resistance."));
		tooltip.add(Component.literal(""));
		tooltip.add(Component.literal("Congratulations, you just wasted it."));
	}
}
