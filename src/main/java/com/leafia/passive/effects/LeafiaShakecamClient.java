package com.leafia.passive.effects;

import javax.annotation.Nullable;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam.Parsed;
import com.leafia.passive.effects.LeafiaShakecam.ShakeFrame;
import com.llib.exceptions.messages.TextWarningLeafia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The client half of the camera shake: what upstream's {@code LeafiaClientListener} did with
 * {@code LeafiaShakecam} from three of its handlers - tick it, apply it in {@code CameraSetup},
 * and clear it when the world unloads. {@link LeafiaShakecam} itself holds every number.
 *
 * <p>{@code ViewportEvent.ComputeCameraAngles} is the camera hook (D-1089): it fires once per frame
 * inside {@code Camera.setup}, which is where {@code CameraSetup} fired in 1.12.2, and its yaw,
 * pitch and roll are the only camera state it may change. The shake is applied through
 * {@link LeafiaShakecam#cameraAngles}, which keeps the rotation exact and turns the simple shake's
 * translation into a small turn ({@link LeafiaShakecam#SIMPLE_SHAKE_DEPTH}).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaShakecamClient {

	private LeafiaShakecamClient() {}

	/** A shake has arrived: parse it, tell the player what was wrong with it, add what is left. */
	static void receive(String[] params, @Nullable BlockPos pos) {
		if(!LeafiaFeatures.torex) return;
		Parsed parsed = LeafiaShakecam.parse(params);
		LocalPlayer player = Minecraft.getInstance().player;
		if(player != null) {
			for(String warning : parsed.warnings()) player.sendSystemMessage(TextWarningLeafia.of(warning));
		}
		if(parsed.shake() != null) LeafiaShakecam._addShake(pos, parsed.shake());
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if(!event.getLevel().isClientSide()) return;
		LeafiaShakecam.localTick();
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null || !LeafiaFeatures.torex) return;
		ShakeFrame frame = new ShakeFrame();
		LeafiaShakecam.shakeCam(player.position(), frame);
		if(LeafiaShakecam.instances.isEmpty() || frame.isIdentity()) return;
		float[] angles = LeafiaShakecam.cameraAngles(event.getYaw(), event.getPitch(), event.getRoll(), frame);
		event.setYaw(angles[0]);
		event.setPitch(angles[1]);
		event.setRoll(angles[2]);
	}

	/** Upstream clears the list when the world unloads. */
	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		LeafiaShakecam.instances.clear();
	}
}
