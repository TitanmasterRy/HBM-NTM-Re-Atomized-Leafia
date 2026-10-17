package com.leafia.unsorted;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.leafia.contents.machines.powercores.dfc.render.DFCDraw;
import com.leafia.contents.machines.powercores.dfc.render.DFCRenderTypes;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A black hole: a black core, an orange lensing glow in three nested shells, two faint polar jets and two textured
 * accretion disks, each part turning at its own speed off the wall clock.
 *
 * <p>Upstream: {@code com/leafia/unsorted/BlackholeRenderer.java}, immediate-mode GL over {@code blackhole.obj}'s four
 * objects, ported to the {@code PoseStack} and render types as the other D-1020 renderers are. Its GL state, in
 * order, becomes:
 * <ul>
 * <li><b>Core</b>: the white texture tinted black with the lightmap at full, before blending is turned on - an opaque
 *     black body ({@code entitySolid}).</li>
 * <li><b>Lens</b>, then blending {@code SRC_ALPHA, ONE} from here on: the white emissive texture tinted
 *     {@code (1, 0.35, 0.127)}, turned by {@code getRotation(0.75)}, drawn at scale 1, 0.875 and 0.75
 *     ({@link DFCRenderTypes#additive}).</li>
 * <li><b>The jets</b>, when {@code large}: per pole, a fan from the centre at {@code (1, 1, 1, 0.35)} out to a ring of
 *     radius 0.4 at {@code y = +-8} at alpha 0, eight segments - drawn as their triangles, additive, both faces.</li>
 * <li><b>DiskOuter</b>, when {@code large}: {@code disk_02} at grey 0.75, turned by {@code getRotation(0.25)}.</li>
 * <li><b>DiskInner</b>: {@code disk_01}, at grey 0.75 when {@code large} and white otherwise, turned by
 *     {@code getRotation(0.5)}.</li>
 * </ul>
 * Upstream culls the core, lens and inner disk and not the jets or outer disk; the additive types here draw both faces
 * throughout, which on these closed or flat shapes under additive blending is the same picture. The textures and mesh are
 * upstream's files, copied by a generator script.
 */
@OnlyIn(Dist.CLIENT)
public final class BlackholeRenderer {

	private BlackholeRenderer() {}

	public static final String OBJ = "/assets/leafia/obj/blackhole.obj";
	public static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
	public static final ResourceLocation DISK_01 = LeafiaRef.rl("textures/misc/blackhole/disk_01.png");
	public static final ResourceLocation DISK_02 = LeafiaRef.rl("textures/misc/blackhole/disk_02.png");

	private static Map<String, Mesh> mdl;

	private static synchronized Map<String, Mesh> mdl() {
		if(mdl == null) {
			try(InputStream in = BlackholeRenderer.class.getResourceAsStream(OBJ)) {
				if(in == null) throw new IllegalStateException("missing " + OBJ);
				mdl = SolBlasterObj.parse(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + OBJ, e);
			}
		}
		return mdl;
	}

	/** Upstream's {@code getRotation}: degrees, {@code speed} turns per second off the wall clock. */
	public static float getRotation(float speed) {
		return (float) Mth.positiveModulo(System.currentTimeMillis() / 1000d * 360, 360 / speed) * speed;
	}

	public static void draw(PoseStack pose, MultiBufferSource buffers, boolean large) {
		Map<String, Mesh> m = mdl();
		int bright = LightTexture.FULL_BRIGHT;

		DFCDraw.part(buffers, RenderType.entitySolid(WHITE), m, "Core", pose, bright, 0, 0, 0, 1);

		RenderType lens = DFCRenderTypes.additive(WHITE);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(getRotation(0.75f)));
		DFCDraw.part(buffers, lens, m, "Lens", pose, bright, 1, 0.35f, 0.127f, 1);
		for(float s : new float[] {0.875f, 0.75f}) {
			pose.pushPose();
			pose.scale(s, s, s);
			DFCDraw.part(buffers, lens, m, "Lens", pose, bright, 1, 0.35f, 0.127f, 1);
			pose.popPose();
		}
		pose.popPose();

		if(large) {
			VertexConsumer fan = buffers.getBuffer(DFCRenderTypes.colored("blackhole_jet", RenderStateShard.LIGHTNING_TRANSPARENCY, false, false, true));
			PoseStack.Pose last = pose.last();
			for(int y = -1; y <= 1; y += 2) {
				for(int i = 0; i < 8; i++) {
					double a0 = Math.PI * 2 / 8 * i, a1 = Math.PI * 2 / 8 * (i + 1);
					fan.addVertex(last, 0, 0, 0).setColor(1, 1, 1, 0.35f);
					fan.addVertex(last, (float) (0.4 * Math.cos(a0)), y * 8, (float) (0.4 * Math.sin(a0))).setColor(1, 1, 1, 0);
					fan.addVertex(last, (float) (0.4 * Math.cos(a1)), y * 8, (float) (0.4 * Math.sin(a1))).setColor(1, 1, 1, 0);
				}
			}
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(getRotation(0.25f)));
			DFCDraw.part(buffers, DFCRenderTypes.additive(DISK_02), m, "DiskOuter", pose, bright, 0.75f, 0.75f, 0.75f, 1);
			pose.popPose();
		}

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(getRotation(0.5f)));
		float grey = large ? 0.75f : 1;
		DFCDraw.part(buffers, DFCRenderTypes.additive(DISK_01), m, "DiskInner", pose, bright, grey, grey, grey, 1);
		pose.popPose();
	}
}
