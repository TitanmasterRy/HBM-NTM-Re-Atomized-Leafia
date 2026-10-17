package com.leafia.eventbuses;

import com.leafia.lib.LeafiaRef;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Appends {@link LeafiaComponentTooltips#linesFor}, {@link LeafiaComponentTooltips#integrityLines} and {@link LeafiaComponentTooltips#ccpLines} to a tooltip (D-3040, D-3072).
 * Client only: a tooltip is never built on a server.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT)
public final class LeafiaComponentTooltipsClient {

	private LeafiaComponentTooltipsClient() {}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		event.getToolTip().addAll(LeafiaComponentTooltips.linesFor(event.getItemStack().getItem()));
		event.getToolTip().addAll(LeafiaComponentTooltips.integrityLines(event.getItemStack().getItem()));
		event.getToolTip().addAll(LeafiaComponentTooltips.ccpLines(event.getItemStack().getItem())); // D-3072, upstream's last line
	}
}
