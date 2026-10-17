package com.leafia.client;

import java.util.Random;

import com.custom_hbm.GuiEasterEgg;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Rolls for {@link GuiEasterEgg} when the title screen first opens (D-3026). Upstream: the first half of
 * {@code LeafiaClientListener.HandlerClient.onGuiInit} ({@code GuiScreenEvent.InitGuiEvent.Post} on {@code GuiMainMenu}); its second
 * half, the Essential and backups warning screens, is the close-out batch's. Behind {@code overhauls.cosmeticOverhaul}.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaEasterEggClient {

	private LeafiaEasterEggClient() {}

	@SubscribeEvent
	public static void onTitleScreen(ScreenEvent.Init.Post event) {
		if(!(event.getScreen() instanceof TitleScreen) || LeafiaEasterEgg.seenEasterEgg) return;
		if(!LeafiaFeatures.cosmeticOverhaul) return;
		LeafiaEasterEgg.seenEasterEgg = true;
		if(LeafiaEasterEgg.roll(new Random())) {
			Minecraft.getInstance().setScreen(new GuiEasterEgg());
			LeafiaEasterEgg.actuallySeenEasterEgg = true;
		}
	}
}
