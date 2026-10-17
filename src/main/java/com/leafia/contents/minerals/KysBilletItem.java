package com.leafia.contents.minerals;

import java.util.List;

import com.leafia.dev.items.itembase.AddonItemHazardBase;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Kysmium-3000 billet: a hazard item with a two-line joke for a tooltip.
 *
 * <p>Upstream: the anonymous {@code AddonItemHazardBaked} subclass behind
 * {@code Resources.billet_kys}, which calls {@code super.addInformation} first and then adds the
 * two lines. Like the mysticite ingot's, they are upstream's own English written into the code
 * rather than the lang file.
 *
 * <p>Registered with a null creative tab - upstream's {@code setCreativeTab(null)} - so it
 * exists, is carried by the Kysmium rod as its appearance, and cannot be taken from the menu.
 * Its {@code addCryogenic(5)} has no hazard type to land on yet ({@code HazardTypeCold} is not in
 * the base mod), and {@link #unwired()} says so.
 */
public class KysBilletItem extends AddonItemHazardBase {

	public KysBilletItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal("Inspired by Quasar!"));
		tooltip.add(Component.literal("Corrstud will pay their life for this very billet."));
	}
}
