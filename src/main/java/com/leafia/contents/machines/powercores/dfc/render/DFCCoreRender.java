package com.leafia.contents.machines.powercores.dfc.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.hbm.render.util.BeamPronter;
import com.hbm.render.util.RenderSparks;
import com.leafia.LeafiaMod;
import com.leafia.contents.machines.powercores.dfc.core.LeafiaCoreTE;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore.Cores;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore.DFCShock;
import com.llib.math.LeafiaColor;
import com.llib.math.MathLeafia;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the overhauled DFC's core: the dull ball while it is cold, the glowing orb with its shells once it
 * is hot, the instability ring, the five cores' own effects, the lines to a field disturber, and the
 * lightning the core throws.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/render/DFCCoreRender.java}.
 *
 * <ul>
 * <li><b>Below 100 degrees</b> ({@link #renderStandby}): a sphere a quarter of a block across (the model's
 *     radius is 1) in a grey that brightens with {@code (T/100)^1.5}, and over it, 1.25 times larger and
 *     additive, a blue that does too; one time in fifty it throws three sparks, a second set twice as
 *     long, in cyan and white.</li>
 * <li><b>From 100 degrees</b> ({@link #renderOrb}): scale {@code ln(T/50 + 1) + 0.5}. Inside the scale a
 *     solid sphere in the core's colour (turning, and shrinking to nothing in the last 3% of a collapse,
 *     on an exponential ease), five additive shells from 0.6 to 1.0 of it, and, if the instability ring
 *     is up, the ring in the catalyst's colour: two bands turning opposite ways. The glitch and the Eye
 *     of Harmony draw their shells as one of ten deformed spheres, a new one each frame, at half size.
 *     A singularity core is a black sphere seen from inside and sixteen blue beads on a swinging orbit; an
 *     Eye of Harmony core is 300 fans of light; a wormhole core is a stream of noise along the line to every
 *     component the core last found.</li>
 * <li><b>A field disturber</b> in range is joined to the core by two wavering lines, orange and cyan.</li>
 * <li><b>The shocks</b> - the paths the core threw in the last four ticks - are straight two-layer
 *     {@code 0x5B1D00} beams, skipping a pair of points under a tenth of a block apart, and not drawn one
 *     frame in four.</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * The meshes are the base mod's {@code sphere_uv} (radius 1) and {@code sphere_ruv} (0.5), and the addon's
 * deformed spheres and ring, drawn untextured in vertex colour ({@link DFCDraw#colored}) - upstream binds
 * {@code solid_emissive} and tints with {@code glColor}. Lighting is off throughout, as upstream's is, and
 * the blend functions are {@link DFCRenderTypes#colored} types in the order upstream sets them. Every number
 * is upstream's, float or double as written.
 *
 * <p><b>The glitch's two screen effects are not drawn.</b> Upstream, for a glitch core, shakes the world by
 * translating the GL texture matrix ({@code HandlerClient.rx/ry/rz}) and the model-view matrix by a random
 * amount that grows as the player nears; neither is a state a block entity renderer can set in 1.21.1
 * (D-3017). The core's own sound and its shells are drawn.
 */
@OnlyIn(Dist.CLIENT)
public class DFCCoreRender implements BlockEntityRenderer<LeafiaCoreTE> {

	private static final ResourceLocation NOISE = LeafiaRef.rl("textures/entity/dfc/null-noisemap.png");

	private static Mesh sphereUv;
	private static Mesh sphereRuv;
	private static Mesh[] deformSphere;

	public DFCCoreRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public boolean shouldRenderOffScreen(LeafiaCoreTE te) {
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(LeafiaCoreTE te) {
		return AABB.INFINITE;
	}

	@Override
	public int getViewDistance() {
		return 512;
	}

	// ---- meshes ---------------------------------------------------------------------------------

	private static Mesh base(String name) {
		ResourceLocation path = ResourceLocation.fromNamespaceAndPath("hbm", "models/" + name + ".obj");
		try(InputStream in = Minecraft.getInstance().getResourceManager().open(path)) {
			return DFCObj.whole(in);
		} catch(IOException | RuntimeException e) {
			LeafiaMod.LOGGER.error("Could not read {} for the DFC core", path, e);
			return new Mesh(new float[0], 0);
		}
	}

	private static Mesh sphereUv() {
		if(sphereUv == null) sphereUv = base("sphere_uv");
		return sphereUv;
	}

	private static Mesh sphereRuv() {
		if(sphereRuv == null) sphereRuv = base("sphere_ruv");
		return sphereRuv;
	}

	private static Mesh deform(int i) {
		if(deformSphere == null) {
			Mesh[] all = new Mesh[DFCObj.DEFORMED];
			for(int k = 0; k < all.length; k++) all[k] = DFCObj.merged("deform" + k);
			deformSphere = all;
		}
		return deformSphere[i];
	}

	// ---- the render types, in the order the orb sets their states --------------------------------

	/** Opaque and culled: the cold ball's grey. */
	private static final RenderType OPAQUE = DFCRenderTypes.colored("opaque", RenderStateShard.NO_TRANSPARENCY, true, true, false);
	/** {@code SRC_ALPHA, ONE}, culled: the shells, and the cold ball's blue. */
	private static final RenderType ADDITIVE = DFCRenderTypes.colored("additive", RenderStateShard.LIGHTNING_TRANSPARENCY, true, true, false);
	/** {@code SRC_ALPHA, ONE}, double-sided: the instability ring. */
	private static final RenderType ADDITIVE_BOTH = DFCRenderTypes.colored("additive_both", RenderStateShard.LIGHTNING_TRANSPARENCY, false, true, false);
	/** {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}, culled: the solid sphere and the singularity's inside. */
	private static final RenderType ALPHA = DFCRenderTypes.colored("alpha", RenderStateShard.TRANSLUCENT_TRANSPARENCY, true, true, false);
	/** {@code ONE, ONE_MINUS_SRC_ALPHA}, culled: the singularity's beads. */
	private static final RenderType BEADS = DFCRenderTypes.colored("beads", DFCRenderTypes.ONE_ONE_MINUS_ALPHA, true, true, false);
	/** {@code SRC_ALPHA, ONE}, culled, no depth write, triangles: the Eye of Harmony's fans. */
	private static final RenderType FLASH = DFCRenderTypes.colored("flash", RenderStateShard.LIGHTNING_TRANSPARENCY, true, false, true);

	// ---- the render -------------------------------------------------------------------------------

	@Override
	public void render(LeafiaCoreTE core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {

		Level level = core.getLevel();
		if(level == null) return;
		IMixinTileEntityCore mixin = core;

		if(mixin.getDFCTemperature() < 100)
			renderStandby(core, pose, buffers);
		else
			renderOrb(core, partialTick, pose, buffers);

		if(mixin.getDFCJammerPos() != null) {
			pose.pushPose();
			pose.translate(0.5, 0.5, 0.5);
			LeafiaColor colorBlast = new LeafiaColor(1, 0.5, 0);
			LeafiaColor colorJammer = new LeafiaColor(0, 0.75, 1);
			Vec3 vec = new Vec3(mixin.getDFCJammerPos().subtract(core.getBlockPos()).getX(),
					mixin.getDFCJammerPos().subtract(core.getBlockPos()).getY(),
					mixin.getDFCJammerPos().subtract(core.getBlockPos()).getZ());
			com.custom_hbm.render.misc.LCEBeamPronter.prontBeam(pose, buffers, vec,
					com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType.RANDOM, com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType.SOLID,
					colorBlast.toInARGB(), colorBlast.toInARGB(),
					(int) (level.getGameTime() % 1000), (int) vec.length(), 0.5f, 1, 0.2f);
			com.custom_hbm.render.misc.LCEBeamPronter.prontBeam(pose, buffers, vec,
					com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType.RANDOM, com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType.SOLID,
					colorJammer.toInARGB(), colorJammer.toInARGB(),
					(int) ((level.getGameTime() + 500) % 1000), (int) vec.length(), 0.5f, 1, 0.2f);
			pose.popPose();
		}

		// A packet adds to the list while the frame reads it (upstream's "fuck you java array iterations").
		List<DFCShock> shocks = new ArrayList<>(mixin.getDfcShocks());
		for(DFCShock shock : shocks) {
			Vec3 lastPos = null;
			pose.pushPose();
			pose.translate(-core.getBlockPos().getX(), -core.getBlockPos().getY(), -core.getBlockPos().getZ());
			if(level.random.nextInt(4) >= 1) {
				for(Vec3 pos : shock.poses) {
					if(lastPos != null) {
						if(pos.distanceTo(lastPos) < 0.1) continue;
						pose.pushPose();
						pose.translate(lastPos.x, lastPos.y, lastPos.z);
						com.custom_hbm.render.misc.LCEBeamPronter.prontBeam(pose, buffers, pos.subtract(lastPos),
								com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType.STRAIGHT, com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType.SOLID,
								0x5B1D00, 0x7F7F7F, 0, 1, 0, 2, 0.25f);
						pose.popPose();
					}
					lastPos = pos;
				}
			}
			pose.popPose();
		}
	}

	/** Below 100 degrees: the dull ball. */
	public void renderStandby(LeafiaCoreTE core, PoseStack pose, MultiBufferSource buffers) {
		IMixinTileEntityCore mixin = core;

		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.scale(0.25F, 0.25F, 0.25F);
		float brightness = DFCLayout.standbyBrightness(mixin.getDFCTemperature());
		float heat = (float) mixin.getDFCTemperature() / 100f;
		DFCDraw.colored(buffers, OPAQUE, sphereUv(), pose, 0.1F + brightness * .9f, 0.1F + brightness * .9f, 0.1F + brightness * .9f, 1F);

		pose.scale(1.25F, 1.25F, 1.25F);
		DFCDraw.colored(buffers, ADDITIVE, sphereUv(), pose, 0.1F + brightness * .9f, 0.2F + (heat * 0.25f + brightness * 0.75f) * .8f, 0.4F + heat * .6f, 1F);

		if(core.getLevel().random.nextInt(50) == 0) {
			for(int i = 0; i < 3; i++) {
				RenderSparks.renderSpark(pose, buffers, (int) System.currentTimeMillis() / 100 + i * 10000, 0, 0, 0, 1.5F, 5, 10, 0x00FFFF, 0xFFFFFF);
				RenderSparks.renderSpark(pose, buffers, (int) System.currentTimeMillis() / 50 + i * 10000, 0, 0, 0, 3F, 5, 10, 0x00FFFF, 0xFFFFFF);
			}
		}
		pose.popPose();
	}

	/** One stream of the wormhole core: a tapering tube of scrolling noise from the core toward a component. */
	public void renderDraconic(PoseStack pose, MultiBufferSource buffers, FiaMatrix face, double scale, double distance, float partialTicks) {
		int segments = 6;
		double startRadius = scale * 0.5;
		double endRadius = 0.3;
		int resolution = 8;

		float movement = (Minecraft.getInstance().player.tickCount + partialTicks) * 0.005F * 10;
		VertexConsumer buf = buffers.getBuffer(DFCRenderTypes.noise(NOISE));
		PoseStack.Pose last = pose.last();

		for(int i = 0; i < segments; i++) {
			double ratio0 = i / (double) segments;
			double ratio1 = (i + 1) / (double) segments;
			FiaMatrix mat0 = face.translate(0, 0, -distance * ratio0);
			FiaMatrix mat1 = face.translate(0, 0, -distance * ratio1);
			double ratio0r = Math.pow(ratio0, 0.5);
			double ratio1r = Math.pow(ratio1, 0.5);
			double radius0 = startRadius + (endRadius - startRadius) * ratio0r;
			double radius1 = startRadius + (endRadius - startRadius) * ratio1r;
			for(int j = 0; j < resolution; j++) {
				float ratioAngle0 = j / (float) resolution;
				float ratioAngle1 = (j + 1) / (float) resolution;
				float angle0 = ratioAngle0 * 360;
				float angle1 = ratioAngle1 * 360;
				FiaMatrix rot00 = mat0.rotate(RotationOrder.XYZ, 0, 0, angle0).translate(0, radius0, 0);
				FiaMatrix rot01 = mat0.rotate(RotationOrder.XYZ, 0, 0, angle1).translate(0, radius0, 0);
				FiaMatrix rot10 = mat1.rotate(RotationOrder.XYZ, 0, 0, angle0).translate(0, radius1, 0);
				FiaMatrix rot11 = mat1.rotate(RotationOrder.XYZ, 0, 0, angle1).translate(0, radius1, 0);
				buf.addVertex(last, (float) rot10.getX(), (float) rot10.getY(), (float) rot10.getZ()).setUv(0, 1 + movement).setColor(0.173f, 0.549f, 0.788f, (float) ratio1);
				buf.addVertex(last, (float) rot11.getX(), (float) rot11.getY(), (float) rot11.getZ()).setUv(1, 1 + movement).setColor(0.173f, 0.549f, 0.788f, (float) ratio1);
				buf.addVertex(last, (float) rot01.getX(), (float) rot01.getY(), (float) rot01.getZ()).setUv(1, movement).setColor(0.173f, 0.549f, 0.788f, (float) ratio0);
				buf.addVertex(last, (float) rot00.getX(), (float) rot00.getY(), (float) rot00.getZ()).setUv(0, movement).setColor(0.173f, 0.549f, 0.788f, (float) ratio0);
			}
		}
	}

	/** From 100 degrees: the orb. */
	public void renderOrb(LeafiaCoreTE core, float partialTicks, PoseStack pose, MultiBufferSource buffers) {
		IMixinTileEntityCore mixin = core;
		Cores type = mixin.getDFCClientType();
		boolean deformed = type == Cores.ams_core_eyeofharmony || type == Cores.glitch;

		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.pushPose();

		int color = core.color;
		float r = (color >> 16 & 255) / 255F;
		float g = (color >> 8 & 255) / 255F;
		float b = (color & 255) / 255F;

		float scale = DFCLayout.orbScale(mixin.getDFCTemperature(), mixin.getDFCCollapsing());
		double rot = DFCLayout.orbSpin(mixin.getDFCCollapsing());
		int colorC = mixin.getDFCColorCatalyst();
		float rC = (colorC >> 16 & 255) / 255F;
		float gC = (colorC >> 8 & 255) / 255F;
		float bC = (colorC & 255) / 255F;

		pose.scale(scale, scale, scale);

		if(mixin.getDFCRingAlpha() > 0) {
			pose.pushPose();
			pose.scale(0.025f, 0.025f, 0.025f);
			Map<String, Mesh> ring = DFCObj.load(DFCObj.RING);
			float angle = mixin.getDFCRingAngle() + mixin.getDFCRingSpinSpeed() * partialTicks;
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(angle));
			ringBand(buffers, ring, DFCObj.INNER_BAND, pose, rC, gC, bC, mixin.getDFCRingAlpha());
			pose.popPose();
			pose.mulPose(Axis.YP.rotationDegrees(-angle));
			ringBand(buffers, ring, DFCObj.OUTER_BAND, pose, rC, gC, bC, mixin.getDFCRingAlpha());
			pose.popPose();
		}

		pose.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(rot), new Vector3f(1, 1, 1).normalize())));
		pose.scale(0.5F, 0.5F, 0.5F);
		DFCDraw.colored(buffers, ALPHA, sphereRuv(), pose, r, g, b, 1F);
		pose.scale(2F, 2F, 2F);

		if(deformed) pose.scale(0.5F, 0.5F, 0.5F);
		for(int i = 6; i <= 10; i++) {
			pose.pushPose();
			pose.scale(i * 0.1F, i * 0.1F, i * 0.1F);
			DFCDraw.colored(buffers, ADDITIVE, deformed ? deform(core.getLevel().random.nextInt(10)) : sphereRuv(), pose, r, g, b, 1F);
			pose.popPose();
		}
		pose.popPose();

		pose.pushPose();
		if(type == Cores.ams_core_sing) {
			pose.pushPose();
			pose.scale(-scale * 1.15f, -scale * 1.15f, -scale * 1.15f);
			DFCDraw.colored(buffers, ALPHA, sphereRuv(), pose, 0.11f, 0.11f, 0.11f, 1F);
			pose.popPose();

			double tr = Mth.positiveModulo(System.currentTimeMillis() / 1000d, 3000) / 2 * Math.PI;
			double add = 0.4; // 0.6
			pose.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tr / 3 * (180 / Math.PI)), new Vector3f(0, 1, 2).normalize())));
			for(int d = 0; d < 16; d++) {
				pose.pushPose();
				double t = tr + add * d;
				pose.mulPose(Axis.ZP.rotationDegrees((float) (-MathLeafia.smoothLinear(Math.abs(Mth.positiveModulo(t / 3 / Math.PI, 2) - 1), 0.5) * 180 * 3)));
				pose.mulPose(Axis.YP.rotationDegrees((float) (Math.sin(t / 3) * 135)));
				pose.translate(0, 0, -scale * 1.4 / 2);
				pose.scale(-0.25f, -0.25f, -0.25f);
				DFCDraw.colored(buffers, BEADS, sphereRuv(), pose, 0.529f, 0.722f, 1F, 0.5f);
				pose.popPose();
			}
		} else if(type == Cores.ams_core_eyeofharmony) {
			pose.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(mixin.getDFCAngle() + partialTicks * mixin.getDFCLightRotateSpeed()),
					new Vector3f(1, 1, 1).normalize())));
			renderFlash(pose, buffers, 0.024f + core.getLevel().random.nextFloat() * 0.001f, 20, 1, r, g, b);
		}
		pose.popPose();

		if(type == Cores.ams_core_wormhole) {
			for(BlockPos pos : mixin.getDFCPrevComponentPositions()) {
				Vec3 relative = new Vec3(pos.subtract(core.getBlockPos()).getX(), pos.subtract(core.getBlockPos()).getY(), pos.subtract(core.getBlockPos()).getZ());
				FiaMatrix mat = new FiaMatrix(new Vec3(0, 0, 0), relative);
				renderDraconic(pose, buffers, mat.translate(0, 0, -2), scale, relative.length() - 2.5, partialTicks);
			}
		}
		pose.popPose();
	}

	private static void ringBand(MultiBufferSource buffers, Map<String, Mesh> ring, String band, PoseStack pose, float r, float g, float b, float a) {
		Mesh m = ring.get(band);
		if(m != null) DFCDraw.colored(buffers, ADDITIVE_BOTH, m, pose, r, g, b, a);
	}

	/**
	 * The Eye of Harmony's light: 300 fans, each turned by five more random rotations than the last, a
	 * white centre of {@code alpha} fading to the core's colour at nothing. Seeded 432, so the burst is the
	 * same every frame and only the turn moves it.
	 */
	private void renderFlash(PoseStack pose, MultiBufferSource buffers, float scale, double intensity, double alpha, float r, float g, float b) {

		pose.scale(0.2F, 0.2F, 0.2F);
		Random random = new Random(432L);
		pose.pushPose();

		VertexConsumer buf = buffers.getBuffer(FLASH);
		for(int i = 0; i < 300; i++) {

			pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
			pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
			pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F));
			pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
			pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));

			float vert1 = (random.nextFloat() * 20.0F + 5.0F + 1 * 10.0F) * (float) (intensity * scale);
			float vert2 = (random.nextFloat() * 2.0F + 1.0F + 1 * 2.0F) * (float) (intensity * scale);

			PoseStack.Pose last = pose.last();
			// A triangle fan of five: centre, A, B, C, A.
			float[][] rim = { { (float) (-0.866D * vert2), vert1, (float) (-0.5D * vert2) }, { (float) (0.866D * vert2), vert1, (float) (-0.5D * vert2) },
					{ 0F, vert1, vert2 } };
			for(int k = 0; k < 3; k++) {
				float[] from = rim[k];
				float[] to = rim[(k + 1) % 3];
				buf.addVertex(last, 0F, 0F, 0F).setColor(1.0F, 1.0F, 1.0F, (float) alpha);
				buf.addVertex(last, from[0], from[1], from[2]).setColor(r, g, b, 0.0F);
				buf.addVertex(last, to[0], to[1], to[2]).setColor(r, g, b, 0.0F);
			}
		}
		pose.popPose();
	}
}
