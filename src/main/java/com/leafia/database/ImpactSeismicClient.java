package com.leafia.database;

import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Random;

/**
 * The client's seismic wobble (D-3040): upstream's {@code LeafiaClientListener.Tom} - {@link ImpactSeismic#localTick} at the end
 * of every client tick, its field of view in {@code FOVUpdateEvent} and its jitter in {@code CameraSetup}. Upstream translates
 * the camera; 1.21.1's camera event takes angles only, so the jitter is the turn that moves a point
 * {@link LeafiaShakecam#SIMPLE_SHAKE_DEPTH} blocks ahead by the same amount, as the addon's other camera shakes are (D-1089).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ImpactSeismicClient {

	private ImpactSeismicClient() {}

	private static final Random RAND = new Random();

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) return;
		ImpactSeismic.localTick(mc.player.onGround(), RAND);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ImpactSeismic.seismic = 0;
		ImpactSeismic.localTick(false, RAND);
	}

	@SubscribeEvent
	public static void onFov(ComputeFovModifierEvent event) {
		if(ImpactSeismic.fovM != 1) event.setNewFovModifier(event.getNewFovModifier() * ImpactSeismic.fovM);
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if(ImpactSeismic.shakeX == 0 && ImpactSeismic.shakeY == 0) return;
		double depth = LeafiaShakecam.SIMPLE_SHAKE_DEPTH;
		event.setYaw(event.getYaw() + (float) Math.toDegrees(Math.atan2(ImpactSeismic.shakeX * ImpactSeismic.SHAKE_X, depth)));
		event.setPitch(event.getPitch() - (float) Math.toDegrees(Math.atan2(ImpactSeismic.shakeY * ImpactSeismic.SHAKE_Y, depth)));
	}
}
