package com.leafia.contents.worldgen.biomes.artificial;

import java.io.File;
import java.util.Iterator;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.DigammaBackstabPacket;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.ForceSchizoPacket;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.NullEntity;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenshotEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * The digamma crater's client half: the nulls (ticked, spawned and drawn), the backstab, the schizo
 * countdown and the screenshot. Not an upstream file; it holds the parts of four upstream files that drive
 * {@link DigammaCrater}'s members, each named where it is used:
 *
 * <ul>
 *   <li>{@code LeafiaPassiveLocal.priorTick}'s null loop, spawn attempt, backstab roll and static countdown
 *       (that file is not ported; these lines are the ones that tick this batch's classes);</li>
 *   <li>{@code MixinRenderGlobal}, which drew each null at the head of the entity pass: here
 *       {@link RenderLevelStageEvent} at {@code AFTER_ENTITIES}, NeoForge's hook for drawing in the world
 *       (no vanilla mixin);</li>
 *   <li>{@code MixinMinecraft}, which cancelled the screenshot key, cleared the nulls and took the
 *       screenshot at the end of the next frame, so a null is never in a picture: here
 *       {@link ScreenshotEvent} (cancel, clear) and {@link RenderFrameEvent.Post} (take it), no mixin;</li>
 *   <li>{@code LeafiaClientListener.Digamma.backstab} and {@code HandlerClient.staticTicks}.</li>
 * </ul>
 *
 * <p>Everything is gated by {@code features.digammaCrater}: with it off the biome does not exist, so
 * nothing here would fire, and the tick returns on its first line regardless.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DigammaCraterClient {

	private DigammaCraterClient() {}

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/entity/null/omfgitsnullminecraft.png");
	public static final ResourceLocation EYES = LeafiaRef.rl("textures/entity/null/omfgitsnullminecraft_e.png");

	/** {@code LeafiaPassiveLocal.nullCounter}. */
	public static int nullCounter = 0;
	/** {@code HandlerClient.staticTicks}: the static before schizo mode starts. Its overlay is drawn by {@code LeafiaClientListener}, not ported. */
	public static int staticTicks = 0;
	public static final int STATIC_TICKS = 5;
	public static final float RECORD_PITCH = 0.7F;

	/** Upstream's silence, for schizo mode, where every biome is the crater; the biome's own JSON says the same. */
	static final Music SILENCE = new Music(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY), DigammaCrater.MUSIC_MIN_DELAY, DigammaCrater.MUSIC_MAX_DELAY, true);

	// ------------------------------------------------------------------------------- the tick

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		if(!LeafiaFeatures.digammaCrater) return;
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		LocalPlayer player = mc.player;
		if(level == null || player == null) {
			DigammaCrater.NULL_LIST.clear();
			return;
		}
		boolean paused = mc.isPaused();

		if(!paused || !mc.isSingleplayer()) {
			if(staticTicks > 0) {
				staticTicks--;
				if(staticTicks == 0) {
					DigammaCrater.schizoMode = true;
					mc.getSoundManager().play(SimpleSoundInstance.forUI(LeafiaSounds.digamma_record.get(), RECORD_PITCH));
				}
			}
		}

		if(!paused) {
			if(!player.isSpectator() && !player.isCreative()
					&& DigammaCrater.isDigammaBiome(level.getBiome(player.blockPosition()))
					&& player.getHealth() > DigammaCrater.BACKSTAB_MIN_HEALTH
					&& level.random.nextInt(DigammaCrater.BACKSTAB_ODDS) == 0
					&& mc.options.getCameraType().isFirstPerson())
				LeafiaCustomPacket.__start(new DigammaBackstabPacket()).__sendToServer();
		}

		Vec3 viewer = player.position();
		for(Iterator<NullEntity> it = DigammaCrater.NULL_LIST.iterator(); it.hasNext(); ) {
			NullEntity nullEntity = it.next();
			if(nullEntity.level != level) {
				it.remove();
				continue;
			}
			if(nullEntity.tick(viewer, player.getEyeHeight())) nullCounter++;
			if(nullEntity.dead) it.remove();
		}

		if(nullCounter < DigammaCrater.NULL_LIMIT && !paused) {
			if(DigammaCrater.isDigammaBiome(level.getBiome(player.blockPosition()))) {
				NullEntity entity = DigammaCrater.trySpawnNull(level, player.getX(), player.getZ(), level.random);
				if(entity != null) DigammaCrater.NULL_LIST.add(entity);
			}
		}
		nullCounter = 0;
	}

	/** {@code Digamma.backstab}: the client's shove, on the answer from the server. */
	public static void backstab() {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null) return;
		player.hasImpulse = true; // upstream's isAirBorne
		player.setDeltaMovement(DigammaCrater.backstabMotion(player.getDeltaMovement(), player.getYRot(),
				player.level().random.nextFloat(), player.onGround()));
	}

	/** {@code ForceSchizoPacket}'s client half. */
	public static void forceSchizo() {
		if(!DigammaCrater.schizoMode) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(LeafiaSounds.noise.get(), 1));
			staticTicks = STATIC_TICKS;
		} else {
			LeafiaCustomPacket.__start(new ForceSchizoPacket()).__sendToServer();
		}
	}

	@SubscribeEvent
	public static void onSelectMusic(SelectMusicEvent event) {
		if(LeafiaFeatures.digammaCrater && DigammaCrater.schizoMode) event.setMusic(SILENCE);
	}

	// ------------------------------------------------------------------------------- drawing

	private static HumanoidModel<LivingEntity> model;

	/** Upstream's {@code new ModelBiped()}: the 64 x 32 biped. */
	private static HumanoidModel<LivingEntity> model() {
		if(model == null)
			model = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32).bakeRoot());
		return model;
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
		if(!LeafiaFeatures.digammaCrater || DigammaCrater.NULL_LIST.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || mc.player == null) return;
		float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		for(NullEntity nullEntity : DigammaCrater.NULL_LIST) {
			if(nullEntity.level != mc.level) continue;
			render(nullEntity, event.getCamera(), partialTicks, buffers);
		}
		buffers.endBatch();
	}

	/**
	 * {@code NullRender}: a {@code RenderBiped} with no shadow whose {@code renderModel} draws twice - the
	 * eyes texture full-bright in {@code (1, b, b)}, then the body texture at its own light with an alpha of
	 * {@code 1 - rand x 0.95 x (0.75 b + 0.25)} - both shaken by a gaussian {@code 0.05 x (1 - b)}, where
	 * {@code b} is {@link NullEntity#brightness} of its distance from the player: red-eyed and flickering
	 * near, white-eyed and still past 40 blocks.
	 */
	static void render(NullEntity entity, Camera camera, float partialTicks, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		HumanoidModel<LivingEntity> m = model();
		Vec3 cam = camera.getPosition();
		PoseStack pose = new PoseStack();
		pose.translate(entity.x - cam.x, entity.y - cam.y, entity.z - cam.z);

		float body = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		float head = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.xRot);
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - body));
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.translate(0.0F, -1.501F, 0.0F);

		m.head.yRot = (head - body) * Mth.DEG_TO_RAD;
		m.head.xRot = pitch * Mth.DEG_TO_RAD;
		m.hat.copyFrom(m.head);
		m.rightArm.xRot = m.leftArm.xRot = m.rightArm.zRot = m.leftArm.zRot = 0;
		m.rightLeg.xRot = m.leftLeg.xRot = 0;
		AnimationUtils.bobArms(m.rightArm, m.leftArm, entity.tickCount + partialTicks);

		double dist = new Vec3(entity.x, entity.y, entity.z).distanceTo(mc.player.position());
		float brightness = NullEntity.brightness(dist);
		var rand = entity.level.random;
		float shake = 0.05F * (1 - brightness);
		pose.translate(rand.nextGaussian() * shake, rand.nextGaussian() * shake, rand.nextGaussian() * shake);

		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EYES)), LightTexture_FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1, 1, brightness, brightness));

		int light = LevelRenderer.getLightColor(entity.level, BlockPos.containing(entity.x, entity.y, entity.z));
		float alpha = 1 - rand.nextFloat() * 0.95F * (brightness * 0.75F + 0.25F);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light,
				OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(alpha, 1, 1, 1));
	}

	/** {@code setLightmapTextureCoords(240, 240)}. */
	private static final int LightTexture_FULL_BRIGHT = 0xF000F0;

	// ------------------------------------------------------------------------------- the screenshot

	private static File pendingScreenshot;

	/**
	 * {@code MixinMinecraft}: a screenshot taken with nulls about is put off to the end of the next frame,
	 * which is drawn without them. The chat line is vanilla's own for the file the screenshot will be
	 * written to. Upstream defers every screenshot; one with no null to hide is the same picture either
	 * way, so only those are deferred here.
	 */
	@SubscribeEvent
	public static void onScreenshot(ScreenshotEvent event) {
		if(!LeafiaFeatures.digammaCrater || DigammaCrater.NULL_LIST.isEmpty() || pendingScreenshot != null) return;
		DigammaCrater.NULL_LIST.clear();
		File target = event.getScreenshotFile();
		pendingScreenshot = target;
		event.setCanceled(true);
		event.setResultMessage(Component.translatable("screenshot.success", Component.literal(target.getName())
				.withStyle(ChatFormatting.UNDERLINE)
				.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, target.getAbsolutePath())))));
		event.getImage().close(); // a cancelled screenshot's image is otherwise never closed
	}

	@SubscribeEvent
	public static void onRenderFrame(RenderFrameEvent.Post event) {
		File target = pendingScreenshot;
		if(target == null) return;
		pendingScreenshot = null;
		NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
		Util.ioPool().execute(() -> {
			try {
				image.writeToFile(target);
			} catch(Exception e) {
				com.leafia.LeafiaMod.LOGGER.warn("Couldn't save screenshot", e);
				Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat()
						.addMessage(Component.translatable("screenshot.failure", e.getMessage())));
			} finally {
				image.close();
			}
		});
	}
}
