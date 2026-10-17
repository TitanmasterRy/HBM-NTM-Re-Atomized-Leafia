package com.leafia.client;

import com.custom_hbm.sound.LCEAudioWrapperClientStartStop;
import com.hbm.extprop.HbmLivingProps;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCraterClient;
import com.leafia.contents.worldgen.biomes.artificial.DigammaEffects;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Random;
import java.util.function.BiFunction;

/**
 * The digamma crater's sound and screen, and what a digamma dose does to the view (D-3040).
 *
 * <p>Upstream: {@code LeafiaClientListener.Digamma} (the ambience, the beat, the vignette, the dose's words, shake and field of
 * view) and three pieces of {@code HandlerClient}: the schizo static ({@code staticTicks}, counted down by
 * {@link DigammaCraterClient}), the black sky of schizo mode ({@code renderWorld}'s inverted black sphere) and the
 * vignette's place in {@code onOverlayRenderPost}. D-3009 left exactly these for the close-out batch. {@link DigammaEffects}
 * holds the numbers.
 *
 * <ul>
 * <li>The crater half rides {@code features.digammaCrater}, as the crater does.</li>
 * <li>The dose half reads the base mod's digamma, so it changes how that feels in every biome; it rides
 *     {@code overhauls.cosmeticOverhaul}, off. With it off the dose is read as 0: no words, no shake, the field of view
 *     untouched.</li>
 * <li>The sideways shake is a translation upstream ({@code glTranslated} in {@code CameraSetup}); 1.21.1's camera event takes
 *     angles only, so it is turned into the yaw that moves a point {@link LeafiaShakecam#SIMPLE_SHAKE_DEPTH} blocks ahead
 *     by the same amount, as the addon's camera shake already is (D-1089).</li>
 * <li>Upstream's sky is a black sphere 400 blocks across drawn over the finished frame. A uniform black sphere around the
 *     camera only ever shows as black wherever nothing nearer was drawn, so here the colour buffer is cleared to black
 *     right after the sky is drawn ({@code AFTER_SKY}), before the terrain; the depth buffer is left alone.</li>
 * <li>Upstream's {@code Digamma.update} began by disconnecting from two servers it named by a hash of their address. That
 *     is a grudge, not a feature, and is not ported.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaDigammaClient {

	private LeafiaDigammaClient() {}

	public static final ResourceLocation VIGNETTE = LeafiaRef.rl("textures/gui/overlay/vignette.png");
	public static final ResourceLocation STATIC = LeafiaRef.rl("textures/gui/overlay/bigbadstatic.png");
	private static final int VIGNETTE_W = 1280, VIGNETTE_H = 720;
	private static final int STATIC_W = 1280, STATIC_H = 2160;

	public static final DigammaEffects EFFECTS = new DigammaEffects();
	private static final Random RAND = new Random();
	private static LCEAudioWrapperClientStartStop ambience;
	/** Upstream's {@code nope}: the ambience is as loud as the fade says, wherever the player stands. */
	private static final BiFunction<Float, Double, Double> FLAT = (intended, distance) -> EFFECTS.ambienceVolume;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		LocalPlayer player = mc.player;
		if(level == null || player == null) return;

		if(ambience != null && (ambience.world != level || ambience.sound == null || !mc.getSoundManager().isActive(ambience.sound))) {
			ambience.stopSound();
			ambience = null;
		}

		boolean inCrater = LeafiaFeatures.digammaCrater && DigammaCrater.isDigammaBiome(level.getBiome(player.blockPosition()));
		double digamma = LeafiaFeatures.cosmeticOverhaul ? HbmLivingProps.getDigamma(player) : 0;
		if(EFFECTS.tick(inCrater, digamma, RAND, n -> I18n.get("gui.digamma_message." + n)))
			mc.getSoundManager().play(SimpleSoundInstance.forUI(LeafiaSounds.eversionsong7_cut.get(), 1));

		if(inCrater) {
			if(ambience == null) {
				ambience = new LCEAudioWrapperClientStartStop(level, LeafiaSounds.sbmoon_surface.get(), null, null, 0.01F, SoundSource.AMBIENT);
				ambience.setCustomAttenuation(FLAT);
				ambience.setLooped(true);
				ambience.startSound();
			}
			ambience.updateVolume((float) EFFECTS.ambienceVolume / 1.5F);
		} else if(ambience != null) {
			ambience.updateVolume((float) EFFECTS.ambienceVolume / 1.5F);
			if(EFFECTS.ambienceVolume <= 0) {
				ambience.stopSound();
				ambience = null;
			}
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		if(ambience != null) ambience.stopSound();
		ambience = null;
		EFFECTS.reset();
	}

	/** The words, under the crosshair as upstream drew them ({@code CROSSHAIRS}, pre). */
	@SubscribeEvent
	public static void onCrosshair(RenderGuiLayerEvent.Pre event) {
		if(!event.getName().equals(VanillaGuiLayers.CROSSHAIR) || EFFECTS.texts.isEmpty()) return;
		GuiGraphics gui = event.getGuiGraphics();
		Font font = Minecraft.getInstance().font;
		for(DigammaEffects.Text text : EFFECTS.texts) {
			int w = font.width(text.message);
			gui.pose().pushPose();
			gui.pose().translate(gui.guiWidth() * text.x + RAND.nextFloat() * 4 - 2, gui.guiHeight() * text.y + RAND.nextFloat() * 4 - 2, 0);
			gui.pose().scale(text.scale, text.scale, 1);
			gui.pose().translate(-w / 2F, -font.lineHeight / 2F, 0);
			gui.drawString(font, text.message, 0, 0, 0xFFFFFF, false);
			gui.pose().popPose();
		}
	}

	/** The static before schizo mode, then the vignette (upstream's {@code onOverlayRenderPost}, after the whole HUD). */
	@SubscribeEvent
	public static void onGuiPost(RenderGuiEvent.Post event) {
		GuiGraphics gui = event.getGuiGraphics();
		int w = gui.guiWidth();
		int h = gui.guiHeight();
		if(LeafiaFeatures.digammaCrater && DigammaCraterClient.staticTicks > 0) {
			// a screen-sized window of the noise, one texel a pixel, at a random height every frame
			int v = RAND.nextInt(STATIC_W);
			gui.blit(STATIC, 0, 0, 0, v, w, h, STATIC_W, STATIC_H);
		}
		float alpha = EFFECTS.vignetteAlpha(event.getPartialTick().getGameTimeDeltaPartialTick(true));
		if(alpha > 0) {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			gui.setColor(1, 1, 1, alpha);
			gui.blit(VIGNETTE, 0, 0, w, h, 0, 0, VIGNETTE_W, VIGNETTE_H, VIGNETTE_W, VIGNETTE_H);
			gui.setColor(1, 1, 1, 1);
			RenderSystem.disableBlend();
		}
	}

	@SubscribeEvent
	public static void onFov(ComputeFovModifierEvent event) {
		if(!LeafiaFeatures.cosmeticOverhaul) return;
		event.setNewFovModifier(event.getNewFovModifier() * EFFECTS.fovMultiplier());
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if(!LeafiaFeatures.cosmeticOverhaul) return;
		double shake = EFFECTS.shake(RAND.nextGaussian());
		if(shake != 0) event.setYaw(event.getYaw() + (float) Math.toDegrees(Math.atan2(shake, LeafiaShakecam.SIMPLE_SHAKE_DEPTH)));
	}

	/** Schizo mode's black sky. */
	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !DigammaCrater.schizoMode) return;
		RenderSystem.clearColor(0, 0, 0, 1);
		RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
	}
}
