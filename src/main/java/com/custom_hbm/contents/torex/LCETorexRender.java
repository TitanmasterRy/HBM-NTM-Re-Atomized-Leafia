package com.custom_hbm.contents.torex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.custom_hbm.contents.torex.LCETorex.Cloudlet;
import com.custom_hbm.contents.torex.LCETorex.TorexType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws every client torex. Upstream: {@code com/custom_hbm/contents/torex/LCETorexRender.java}, a
 * 1.12.2 {@code Render<LCETorex>}: one entity renderer, called for each cloud by the entity pass.
 *
 * <h2>Where it is called from</h2>
 *
 * The torex is not an entity here (see {@link LCETorex}), so there is no entity pass to be in. It
 * draws from {@link RenderLevelStageEvent} at {@code AFTER_PARTICLES}: after the opaque and
 * translucent world and the particles, depth-tested and not depth-writing, which is what the
 * entity pass gave it in 1.12.2 for everything it drew. The one visible difference is that the
 * translucent terrain (water) no longer paints over a cloud behind it.
 *
 * <h2>Three layers, and one change to how the first is drawn</h2>
 *
 * <ol>
 *   <li><b>The puffs.</b> Each is two camera-facing quads of {@code particle_base.png}: an additive
 *       one ({@code SRC_ALPHA, ONE}) and an alpha-blended one, drawn far to near, one after the other.
 *       Upstream makes a draw call for each quad, 40,000 of them for a full cloud. <b>Here a quad that
 *       would add nothing is not written</b> - an additive quad whose colour or alpha rounds to
 *       zero in a byte, an alpha quad whose alpha does - and since a hot puff is additive-only and a
 *       cool one alpha-only, consecutive quads are of one kind and one draw call carries the run.
 *       The picture is identical, because those quads are invisible, and the call count falls by
 *       one to two orders of magnitude.</li>
 *   <li><b>The flare.</b> Three gaussian-scattered additive sprites of {@code flare.png}, seeded
 *       by the cloud's id, for its first 600 ticks times its scale.</li>
 *   <li><b>The flash.</b> 300 white spikes fanned out from {@code 0.8 * coreHeight}, additive and
 *       one-sided, each rotated onto the last one's orientation - the same random walk as
 *       {@code ParticleFlash}'s (D-1075), from the same seed.</li>
 * </ol>
 *
 * <p>The fog is switched off for all three, as upstream's {@code glDisable(GL_FOG)}: a mushroom
 * cloud is seen from far past the render distance, and fogged out at the edge of it.
 *
 * <h2>Deviations</h2>
 * <ul>
 *   <li>The puffs are sorted by distance from the <em>camera</em>, upstream's from the player: the
 *       same in first person, and right in third. The key is a precomputed float, not a
 *       comparator that measures twice per comparison.</li>
 *   <li>{@code CONDENSATION} puffs are simulated and never drawn, upstream's: its draw loop skips
 *       them. Kept.</li>
 *   <li>1.21.1's particle shader discards a fragment under alpha 0.1 where upstream drew anything
 *       over 0; a puff's last tenth of fading is gone (D-1075 records the same).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LCETorexRender {

	private LCETorexRender() {}

	public static final ResourceLocation CLOUDLET = ResourceLocation.fromNamespaceAndPath("hbm", "textures/particle/particle_base.png");
	public static final ResourceLocation FLARE = ResourceLocation.fromNamespaceAndPath("hbm", "textures/particle/flare.png");

	/** Measured, for the log and the notes: what the last frame's puffs cost to build. */
	public static long lastBuildNanos = 0;
	public static int lastQuads = 0;
	public static int lastDraws = 0;
	public static int lastPuffs = 0;

	/** {@code LEAFIA_TOREX_PROFILE=1} in the environment logs one line a second about the first cloud's cost. */
	private static final boolean PROFILE = System.getenv("LEAFIA_TOREX_PROFILE") != null;
	private static long lastProfileLog = 0;

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
		if(!LeafiaFeatures.torex) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || LCETorex.CLIENT.isEmpty()) return;
		float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		Camera camera = event.getCamera();
		for(LCETorex torex : LCETorex.CLIENT) {
			if(torex.level != mc.level || torex.isDead) continue;
			long started = System.nanoTime();
			doRender(torex, camera, partialTicks);
			if(PROFILE && System.currentTimeMillis() - lastProfileLog > 1000) {
				lastProfileLog = System.currentTimeMillis();
				com.leafia.LeafiaMod.LOGGER.info("[torex profile] age {} palette {} puffs {} quads {} draws {} build {} ms, whole draw {} ms",
						torex.ticksExisted, torex.getType(), lastPuffs, lastQuads, lastDraws,
						String.format("%.2f", lastBuildNanos / 1e6), String.format("%.2f", (System.nanoTime() - started) / 1e6));
			}
		}
	}

	/** Upstream's {@code doRender}. */
	static void doRender(LCETorex cloud, Camera camera, float partialTicks) {
		if(!cloud.isValid()) return;
		float scale = (float) cloud.getScale();
		float flashDuration = scale * TorexMath.FLASH_BASE_DURATION;
		float flareDuration = scale * TorexMath.FLARE_BASE_DURATION;
		Vec3 cam = camera.getPosition();

		double x = cloud.initPosX - cam.x;
		double y = cloud.initPosY - cam.y;
		double z = cloud.initPosZ - cam.z;

		Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
		float fogStart = RenderSystem.getShaderFogStart();
		float fogEnd = RenderSystem.getShaderFogEnd();
		RenderSystem.setShaderFogStart(Float.MAX_VALUE);
		RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);

		try {
			cloudletWrapper(cloud, camera, partialTicks, x, y, z, cam);

			if(cloud.ticksExisted < flareDuration + 1) flareWrapper(cloud, camera, partialTicks, flareDuration, x, y, z);

			flashWrapper(cloud, partialTicks, flashDuration, x, y, z);
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.disableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableCull();
			RenderSystem.setShaderFogStart(fogStart);
			RenderSystem.setShaderFogEnd(fogEnd);
			Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
		}
	}

	// ----- the puffs --------------------------------------------------------------------------

	private static final int NONE = 0;
	private static final int ADDITIVE = 1;
	private static final int ALPHA = 2;

	/** The mutable state of one frame's puff drawing: which blend is set, and the open buffer. */
	private static final class Run {
		int mode = NONE;
		BufferBuilder buffer;
		int quads = 0;
		int draws = 0;

		void use(int wanted) {
			if(mode == wanted) return;
			flush();
			mode = wanted;
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, CLOUDLET);
			RenderSystem.disableCull();
			if(wanted == ADDITIVE) RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
			else RenderSystem.blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
			buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		void flush() {
			if(buffer == null) return;
			MeshData mesh = buffer.build();
			buffer = null;
			if(mesh != null) {
				BufferUploader.drawWithShader(mesh);
				draws++;
			}
		}
	}

	private static void cloudletWrapper(LCETorex cloud, Camera camera, float partialTicks, double ox, double oy, double oz, Vec3 cam) {
		long started = System.nanoTime();

		ArrayList<Cloudlet> list = cloud.cloudlets;
		int n = list.size();

		// far to near: sort a key of (distance bits, index), then walk it backwards
		long[] keys = new long[n];
		for(int i = 0; i < n; i++) {
			Cloudlet c = list.get(i);
			double dx = cam.x - c.initPosX;
			double dy = cam.y - c.initPosY;
			double dz = cam.z - c.initPosZ;
			keys[i] = ((long) Float.floatToRawIntBits((float) (dx * dx + dy * dy + dz * dz)) << 32) | i;
		}
		Arrays.sort(keys);

		// the camera's right and up, in world space: a billboard's two axes
		Quaternionf rotation = camera.rotation();
		Vector3f right = new Vector3f(1, 0, 0).rotate(rotation);
		Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);

		Run run = new Run();
		double[] color = new double[3];

		for(int k = n - 1; k >= 0; k--) {
			Cloudlet puff = list.get((int) (keys[k] & 0xFFFFFFFFL));
			if(puff.type == TorexType.CONDENSATION) continue;

			double px = (puff.prevPosX + (puff.initPosX - puff.prevPosX) * partialTicks) - cam.x;
			double py = (puff.prevPosY + (puff.initPosY - puff.prevPosY) * partialTicks) - cam.y;
			double pz = (puff.prevPosZ + (puff.initPosZ - puff.prevPosZ) * partialTicks) - cam.z;

			float alpha = puff.getAlpha();
			float scale = puff.getScale();
			float brightness = 0.75F * puff.colorMod;
			puff.getInterpColor(partialTicks, color);

			// the additive quad
			quad(run, ADDITIVE, 0F, color, brightness, alpha, scale, px, py, pz, right, up);
			// the alpha quad
			quad(run, ALPHA, 0.35F, color, brightness, alpha, scale, px, py, pz, right, up);
		}
		run.flush();

		lastBuildNanos = System.nanoTime() - started;
		lastQuads = run.quads;
		lastDraws = run.draws;
		lastPuffs = n;
	}

	/**
	 * Upstream's {@code tessellateCloudlet}: the colour arithmetic, in its order, and then four
	 * corners - unless the quad is one the blend would add nothing for.
	 */
	private static void quad(Run run, int mode, float minBrightness, double[] color, float brightness, float a, float scale,
			double px, double py, double pz, Vector3f right, Vector3f up) {

		float r = Math.max(minBrightness, (float) color[0] * brightness);
		float g = Math.max(minBrightness, (float) color[1] * brightness);
		float b = Math.max(minBrightness, (float) color[2] * brightness);

		int br = (int) Math.max(48, (Math.min((r + g + b) / 3D, 1) * 240));
		r = Math.min(1F, r);
		g = Math.min(1F, g);
		b = Math.min(1F, b);

		if(mode == ADDITIVE) {
			r = Math.max(r * 1.2f - 0.2f, 0f);
			g = Math.max(g * 1.2f - 0.2f, 0f);
			b = Math.max(b * 1.2f - 0.2f, 0f);
			// a colour that is zero in every byte adds nothing, whatever the alpha
			if((int) (r * 255) == 0 && (int) (g * 255) == 0 && (int) (b * 255) == 0) return;
		} else {
			float br2 = ((r + g + b) / 3 - 0.25f) * 1.5f;
			a *= Math.max(0, 1 - Math.max(br2, 0));
		}
		if((int) (a * 255) == 0) return;

		run.use(mode);
		BufferBuilder buf = run.buffer;
		float rx = right.x * scale, ry = right.y * scale, rz = right.z * scale;
		float ux = up.x * scale, uy = up.y * scale, uz = up.z * scale;

		buf.addVertex((float) (px - rx - ux), (float) (py - ry - uy), (float) (pz - rz - uz)).setUv(1, 1).setColor(r, g, b, a).setUv2(br, br);
		buf.addVertex((float) (px - rx + ux), (float) (py - ry + uy), (float) (pz - rz + uz)).setUv(1, 0).setColor(r, g, b, a).setUv2(br, br);
		buf.addVertex((float) (px + rx + ux), (float) (py + ry + uy), (float) (pz + rz + uz)).setUv(0, 0).setColor(r, g, b, a).setUv2(br, br);
		buf.addVertex((float) (px + rx - ux), (float) (py + ry - uy), (float) (pz + rz - uz)).setUv(0, 1).setColor(r, g, b, a).setUv2(br, br);
		run.quads++;
	}

	// ----- the flare --------------------------------------------------------------------------

	private static void flareWrapper(LCETorex cloud, Camera camera, float partialTicks, float flareDuration, double ox, double oy, double oz) {
		RenderSystem.setShader(GameRenderer::getParticleShader);
		RenderSystem.setShaderTexture(0, FLARE);
		RenderSystem.disableCull();
		RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);

		double age = Math.min(cloud.ticksExisted + partialTicks, flareDuration);
		float alpha = (float) Math.min(1, (flareDuration - age) / flareDuration);

		Random rand = new Random(cloud.entityId);

		Quaternionf rotation = camera.rotation();
		Vector3f right = new Vector3f(1, 0, 0).rotate(rotation);
		Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);

		for(int i = 0; i < 3; i++) {
			float x = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float y = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float z = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float scale = (float) (10 * cloud.rollerSize);
			int br = (int) (alpha * 240);
			double cx = ox + x;
			double cy = oy + y + cloud.coreHeight;
			double cz = oz + z;
			float rx = right.x * scale, ry = right.y * scale, rz = right.z * scale;
			float ux = up.x * scale, uy = up.y * scale, uz = up.z * scale;
			buf.addVertex((float) (cx - rx - ux), (float) (cy - ry - uy), (float) (cz - rz - uz)).setUv(1, 1).setColor(1F, 1F, 1F, alpha).setUv2(br, br);
			buf.addVertex((float) (cx - rx + ux), (float) (cy - ry + uy), (float) (cz - rz + uz)).setUv(1, 0).setColor(1F, 1F, 1F, alpha).setUv2(br, br);
			buf.addVertex((float) (cx + rx + ux), (float) (cy + ry + uy), (float) (cz + rz + uz)).setUv(0, 0).setColor(1F, 1F, 1F, alpha).setUv2(br, br);
			buf.addVertex((float) (cx + rx - ux), (float) (cy + ry - uy), (float) (cz + rz - uz)).setUv(0, 1).setColor(1F, 1F, 1F, alpha).setUv2(br, br);
		}

		MeshData mesh = buf.build();
		if(mesh != null) BufferUploader.drawWithShader(mesh);
	}

	// ----- the flash --------------------------------------------------------------------------

	private static void flashWrapper(LCETorex cloud, float interp, float flashDuration, double ox, double oy, double oz) {
		double[] f = TorexMath.flash(cloud.ticksExisted + interp, flashDuration);
		if(f == null) return;
		float scale = (float) cloud.getScale();
		renderFlash(scale * (float) f[0], TorexMath.FLASH_INTENSITY, cloud.coreHeight, f[1], ox, oy, oz);
	}

	/**
	 * Upstream's {@code renderFlash}: a {@code glScalef(0.2)}, then a translate of {@code height * 4}
	 * inside it, so the spikes are one fifth of their stated size and start at {@code 0.8 * height}.
	 */
	private static void renderFlash(float scale, double intensity, double height, double alpha, double ox, double oy, double oz) {
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.enableCull();
		RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

		float s = 0.2F;
		float cx = (float) ox;
		float cy = (float) (oy + height * 4 * s);
		float cz = (float) oz;
		float a = (float) alpha;

		for(int i = 0; i < TorexMath.FLASH_FANS; i++) {
			float vert1 = TorexMath.FLASH_LEN[i] * (float) (intensity * scale);
			float vert2 = TorexMath.FLASH_WID[i] * (float) (intensity * scale);

			float[][] m = TorexMath.FLASH_ROT[i];
			// centre, then the three rim points of the fan
			float[] p1 = at(m, (float) (-0.866D * vert2), vert1, (float) (-0.5D * vert2), s);
			float[] p2 = at(m, (float) (0.866D * vert2), vert1, (float) (-0.5D * vert2), s);
			float[] p3 = at(m, 0F, vert1, vert2, s);

			tri(buf, cx, cy, cz, a, p1, p2);
			tri(buf, cx, cy, cz, a, p2, p3);
			tri(buf, cx, cy, cz, a, p3, p1);
		}

		MeshData mesh = buf.build();
		if(mesh != null) BufferUploader.drawWithShader(mesh);
	}

	private static float[] at(float[][] m, float x, float y, float z, float scale) {
		return new float[] {
				scale * (m[0][0] * x + m[0][1] * y + m[0][2] * z),
				scale * (m[1][0] * x + m[1][1] * y + m[1][2] * z),
				scale * (m[2][0] * x + m[2][1] * y + m[2][2] * z)};
	}

	/** One triangle of a fan, as a quad whose last corner repeats; the centre is opaque and the rim clear. */
	private static void tri(BufferBuilder buf, float cx, float cy, float cz, float a, float[] p, float[] q) {
		buf.addVertex(cx, cy, cz).setColor(1F, 1F, 1F, a);
		buf.addVertex(cx + p[0], cy + p[1], cz + p[2]).setColor(1F, 1F, 1F, 0F);
		buf.addVertex(cx + q[0], cy + q[1], cz + q[2]).setColor(1F, 1F, 1F, 0F);
		buf.addVertex(cx + q[0], cy + q[1], cz + q[2]).setColor(1F, 1F, 1F, 0F);
	}
}
