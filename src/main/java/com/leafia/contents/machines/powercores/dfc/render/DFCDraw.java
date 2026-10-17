package com.leafia.contents.machines.powercores.dfc.render;

import java.util.Map;

import javax.annotation.Nullable;

import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code WaveFrontObjectVAO.renderPart} and {@code renderAll}: write one object, or a whole mesh, into the
 * buffer a render type hands out, at the pose's matrix.
 *
 * <p>The buffer is asked for at the draw and filled before the next call asks again - the shared builder of
 * a non-fixed render type is finished when another is requested, so a consumer held across two parts
 * would throw "Not building!" (D-1062).
 */
@OnlyIn(Dist.CLIENT)
public final class DFCDraw {

	private DFCDraw() {}

	/**
	 * One named object, tinted, lit by {@code light}. A name the mesh does not have draws nothing, as an
	 * unknown {@code renderPart} does upstream (the booster has no {@code Arm}, and the renderer asks).
	 */
	public static void part(MultiBufferSource buffers, RenderType type, Map<String, Mesh> mesh, String name, PoseStack pose,
			int light, float r, float g, float b, float a) {
		Mesh m = mesh.get(name);
		if(m == null) return;
		write(buffers.getBuffer(type), m, pose, light, r, g, b, a, null);
	}

	public static void part(MultiBufferSource buffers, RenderType type, Map<String, Mesh> mesh, String name, PoseStack pose, int light) {
		part(buffers, type, mesh, name, pose, light, 1F, 1F, 1F, 1F);
	}

	/** An object with its texture coordinates mapped into a window of the texture: {@code {u0, v0, du, dv}}. */
	public static void partInWindow(MultiBufferSource buffers, RenderType type, Map<String, Mesh> mesh, String name, PoseStack pose,
			int light, float[] window) {
		Mesh m = mesh.get(name);
		if(m == null) return;
		write(buffers.getBuffer(type), m, pose, light, 1F, 1F, 1F, 1F, window);
	}

	/** Every vertex of a mesh, in one colour, for a render type with no texture: the spheres. */
	public static void colored(MultiBufferSource buffers, RenderType type, Mesh mesh, PoseStack pose, float r, float g, float b, float a) {
		VertexConsumer buffer = buffers.getBuffer(type);
		PoseStack.Pose last = pose.last();
		float[] d = mesh.data();
		for(int i = 0; i < d.length; i += 8)
			buffer.addVertex(last, d[i], d[i + 1], d[i + 2]).setColor(r, g, b, a);
	}

	/** The vertex write for the entity and textured formats alike: an element the format lacks is skipped. */
	private static void write(VertexConsumer buffer, Mesh mesh, PoseStack pose, int light, float r, float g, float b, float a, @Nullable float[] window) {
		PoseStack.Pose last = pose.last();
		float[] d = mesh.data();
		for(int i = 0; i < d.length; i += 8) {
			float u = d[i + 3];
			float v = d[i + 4];
			if(window != null) {
				u = window[0] + u * window[2];
				v = window[1] + v * window[3];
			}
			buffer.addVertex(last, d[i], d[i + 1], d[i + 2])
					.setColor(r, g, b, a)
					.setUv(u, v)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(last, d[i + 5], d[i + 6], d[i + 7]);
		}
	}
}
