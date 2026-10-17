package com.leafia.dev.machine;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Puts the reactor-tier line under a reactor block's name (D-3001).
 *
 * <p>Upstream does it with a bytecode patch ({@code MixinItemStack}'s ASM hook) that calls
 * {@code MachineTooltip.addInfoASM} at the head of every item's tooltip, "before addInformation()". A
 * tooltip event is the 1.21.1 equivalent. "Before addInformation" means, in effect, "directly under the
 * name"; the event fires after the item has written its own lines, so the tier lines are inserted at
 * index 1 rather than appended.
 *
 * <p>The tier lines are gated by {@code features.reactorTiers}; the machine badges upstream's switch gives the base mod's
 * machines ({@link MachineTooltip#addBadges}, D-3040) follow them, behind {@code overhauls.cosmeticOverhaul}. Client only: a
 * tooltip is never built on a server.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT)
public final class ReactorTierTooltips {

	private ReactorTierTooltips() {}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		if(!LeafiaFeatures.reactorTiers && !LeafiaFeatures.cosmeticOverhaul) return;
		List<Component> lines = new ArrayList<>();
		if(LeafiaFeatures.reactorTiers) MachineTooltip.addInfoASM(event.getItemStack().getItem(), lines, Screen.hasShiftDown());
		if(LeafiaFeatures.cosmeticOverhaul) MachineTooltip.addBadges(event.getItemStack().getItem(), lines);
		if(lines.isEmpty()) return;
		List<Component> tooltip = event.getToolTip();
		tooltip.addAll(Math.min(1, tooltip.size()), lines);
	}
}
