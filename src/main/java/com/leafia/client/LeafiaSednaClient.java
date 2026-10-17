package com.leafia.client;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.ILockonWeapon.LockonHandler;
import com.leafia.contents.gear.combat.guns.GunInitLocal;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifleRender;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.EntityAttachedSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * The anti-mass rifle's client wiring (D-3028): its part bakes and renderer, the lock-on tick and its marker, and the attached
 * sounds' tick. Upstream spreads these over {@code ResourceInit}, {@code ItemRendererInit}, its client proxy's {@code registerGunCfg},
 * {@code LeafiaPassiveLocal.priorTick}/{@code onTick} and {@code TopRender.renderLockon}.
 */
public final class LeafiaSednaClient {

	private LeafiaSednaClient() {}

	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class Mod {

		@SubscribeEvent
		public static void registerExtraModels(ModelEvent.RegisterAdditional event) {
			if(LeafiaFeatures.amRifle) AMRifleRender.registerParts(event);
		}

		/** Upstream's {@code proxy.registerGunCfg()}: after the registries, as the base mod's own {@code GunFactoryClient.init}. */
		@SubscribeEvent
		public static void clientSetup(FMLClientSetupEvent event) {
			if(LeafiaFeatures.amRifle) event.enqueueWork(GunInitLocal::init);
		}
	}

	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
	public static final class Game {

		/** {@code LeafiaPassiveLocal}: the lock-on (upstream's {@code priorTick}) and the attached sounds (its {@code onTick}). */
		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			Minecraft mc = Minecraft.getInstance();
			if(mc.level == null || mc.player == null) return;
			EntityAttachedSounds.update(mc.level);
			if(LeafiaFeatures.amRifle) LockonHandler.handleLockon(mc.player, mc.level, mc.level.entitiesForRendering());
		}

		@SubscribeEvent
		public static void onRenderStage(RenderLevelStageEvent event) {
			if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
			if(LockonHandler.target == null) return;
			Minecraft mc = Minecraft.getInstance();
			MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
			renderLockon(event.getCamera(), event.getPoseStack(), buffers.getBuffer(RenderType.lines()));
			buffers.endBatch(RenderType.lines());
		}
	}

	/** A linear blend, upstream's {@code TopRender.lerp}. */
	private static Vec3 lerp(Vec3 a, Vec3 b, double factor) {
		return a.add(b.subtract(a).scale(factor));
	}

	/**
	 * Upstream's {@code TopRender.renderLockon}: four corner brackets round the target's eyes, facing the camera, white while the lock
	 * builds, orange and closer in from the eighth beep, red and closed at the sixteenth. Its size grows with distance (a tenth of it),
	 * so it reads the same on screen at any range. The corners are the camera's left and up axes, which is what the 1.12.2
	 * {@code ActiveRenderInfo} rotations were.
	 */
	public static void renderLockon(Camera camera, PoseStack pose, VertexConsumer lines) {
		Entity target = LockonHandler.target;
		Vec3 pos = camera.getPosition();
		float scale = 0.4f;
		float gap = 0.3f;
		int col = 0xFFFFFF;
		if(LockonHandler.timer / 2 >= 8) {
			col = 0xFFAA00;
			gap = 0.15f;
		}
		if(LockonHandler.timer / 2 >= 16) {
			col = 0xFF0000;
			gap = 0;
		}
		Vec3 tgtPos = new Vec3(target.getX(), target.getEyeY(), target.getZ());
		double sc2 = pos.distanceTo(tgtPos) / 10;
		scale *= (float) sc2;
		gap *= (float) sc2;

		Vector3f l = camera.getLeftVector();
		Vector3f u = camera.getUpVector();
		Vec3 left = new Vec3(l.x(), l.y(), l.z()).scale(scale + gap);
		Vec3 up = new Vec3(u.x(), u.y(), u.z()).scale(scale + gap);
		Vec3 rd = left.scale(-1).subtract(up);
		Vec3 ru = left.scale(-1).add(up);
		Vec3 lu = left.add(up);
		Vec3 ld = left.subtract(up);

		double fac = 0.5 - (gap / (gap + scale)) / 2;
		Vec3 newPos = tgtPos.subtract(pos);
		float r = (col >> 16 & 255) / 255F, g = (col >> 8 & 255) / 255F, b = (col & 255) / 255F;

		Vec3[][] segments = {
				{ rd, lerp(rd, ld, fac) }, { rd, lerp(rd, ru, fac) },
				{ ru, lerp(ru, lu, fac) }, { ru, lerp(ru, rd, fac) },
				{ lu, lerp(lu, ru, fac) }, { lu, lerp(lu, ld, fac) },
				{ ld, lerp(ld, rd, fac) }, { ld, lerp(ld, lu, fac) } };
		PoseStack.Pose last = pose.last();
		for(Vec3[] seg : segments) {
			Vec3 a = newPos.add(seg[0]);
			Vec3 c = newPos.add(seg[1]);
			Vec3 n = c.subtract(a).normalize();
			lines.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, b, 1F).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
			lines.addVertex(last, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, 1F).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
		}
	}
}
