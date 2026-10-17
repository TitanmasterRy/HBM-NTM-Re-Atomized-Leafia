package com.leafia.unsorted;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Upstream's seven {@code double}-taking overloads over the base mod's {@code NTMRenderHelper}: a
 * vertex written with doubles where the helper wanted floats.
 *
 * <p>Upstream: {@code com/leafia/unsorted/NTMRenderHelperWrapper.java}. Its whole job was one
 * {@code (float)} cast per coordinate, so that a renderer working in {@code double}s (a translation
 * plus a radius times a layer) could call a helper that was written for {@code float}s. The helper
 * itself, {@code NTMRenderHelper}, drew through 1.12.2's {@code Tessellator}, which 1.21.1 does
 * not have: this port's base mod has no such class, and nothing it dissolved into is a drop-in.
 * What does exist is a {@link VertexConsumer} and the {@link PoseStack.Pose} a renderer already
 * holds, so every method here takes the pair that the {@code Tessellator} used to be, and keeps
 * upstream's name, argument order and the double-in, float-out shape.
 *
 * <ul>
 * <li>The two {@code Tessellator}-taking overloads are the same two methods: the tessellator was
 *     only a way to say which buffer, and here the buffer is the first argument of all of them.</li>
 * <li>The {@code int} colour overload takes 0 to 255 per channel, as upstream's handed straight to
 *     {@code color(int, int, int, int)}; the {@code float} one takes 0 to 1.</li>
 * <li>The vertex is written through the pose, so a renderer pushes, translates and rotates its
 *     matrix as upstream did with {@code glTranslated} and {@code glRotatef}, and the quads land
 *     where upstream's did.</li>
 * </ul>
 *
 * <p>No drawing happens here; the caller chose the render type, which is where upstream's
 * {@code GL_BLEND} and {@code glBlendFunc} calls went (D-3002).
 */
@OnlyIn(Dist.CLIENT)
public class NTMRenderHelperWrapper {

	private NTMRenderHelperWrapper() {}

	/** A bare position. Upstream's {@code addVertex(double, double, double)}. */
	public static void addVertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z) {
		buffer.addVertex(pose, (float) x, (float) y, (float) z);
	}

	/** A position and a texture coordinate. Upstream's {@code addVertexWithUV}, with or without a tessellator. */
	public static void addVertexWithUV(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, double u, double v) {
		buffer.addVertex(pose, (float) x, (float) y, (float) z).setUv((float) u, (float) v);
	}

	/** A position and a colour in 0 to 255. Upstream's {@code addVertexColor(double x3, int x4)}. */
	public static void addVertexColor(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
			int red, int green, int blue, int alpha) {
		buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, alpha);
	}

	/** A position and a colour in 0 to 1. Upstream's {@code addVertexColor(double x3, float x4)}, with or without a tessellator. */
	public static void addVertexColor(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
			float red, float green, float blue, float alpha) {
		buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, alpha);
	}
}
