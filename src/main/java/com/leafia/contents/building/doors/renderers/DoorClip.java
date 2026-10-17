package com.leafia.contents.building.doors.renderers;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws a baked model slid along Z and cut off at two planes: the sliding crimson door leaves, which
 * disappear into the wall.
 *
 * <p>Upstream does this with two {@code glClipPlane}s - {@code (0, 0, 1, d)} and {@code (0, 0, -1, d)},
 * so everything between {@code z = -d} and {@code z = d} in the door's own frame stays - set before
 * it draws the leaves and cleared before it draws the frame. 1.21.1 has no user clip planes at all
 * (they are a fixed-function feature, and the shaders have none), so this does the clipping to the
 * geometry instead: each quad of the model is shifted by the slide, cut against both planes with
 * Sutherland-Hodgman, interpolating position, texture coordinate and normal, and what is left is
 * emitted as triangles.
 *
 * <p>It runs every frame for two models of a few hundred quads each, and nothing about the result
 * is cached because the cut moves with the slide. The shift is applied to the vertices rather than
 * to the pose, so the planes stay where they were set: in the frame the leaf slides in, not in the
 * leaf's.
 *
 * <p>The plane is half a millimetre inside upstream's frame on each side ({@code 3.499} for a frame
 * whose outer face is at {@code 3.5}); the caller passes it, so the number stays upstream's.
 */
@OnlyIn(Dist.CLIENT)
final class DoorClip {

	private DoorClip() { }

	private static final RandomSource RANDOM = RandomSource.create();

	/**
	 * @param slide how far to move the model along Z before cutting
	 * @param min   keep geometry at or above this Z, after the slide
	 * @param max   keep geometry at or below this Z, after the slide
	 */
	static void render(BakedModel model, PoseStack matrix, VertexConsumer buffer, int light, int overlay,
			double slide, double min, double max) {

		PoseStack.Pose pose = matrix.last();

		for(var quad : model.getQuads(null, null, RANDOM)) {

			int[] data = quad.getVertices();
			int stride = data.length / 4;

			List<float[]> poly = new ArrayList<>(6);
			for(int k = 0; k < 4; k++) {
				int o = k * stride;
				int normal = data[o + 7];
				poly.add(new float[] {
						Float.intBitsToFloat(data[o]),
						Float.intBitsToFloat(data[o + 1]),
						(float) (Float.intBitsToFloat(data[o + 2]) + slide),
						Float.intBitsToFloat(data[o + 4]),
						Float.intBitsToFloat(data[o + 5]),
						((byte) normal) / 127F,
						((byte) (normal >> 8)) / 127F,
						((byte) (normal >> 16)) / 127F
				});
			}

			poly = DoorClipMath.cut(poly, (float) min, true);
			if(poly.size() >= 3) poly = DoorClipMath.cut(poly, (float) max, false);
			if(poly.size() < 3) continue;

			// A triangle fan; a triangle is a quad whose last two corners are the same.
			float[] first = poly.get(0);
			for(int i = 1; i + 1 < poly.size(); i++) {
				emit(buffer, pose, first, light, overlay);
				emit(buffer, pose, poly.get(i), light, overlay);
				emit(buffer, pose, poly.get(i + 1), light, overlay);
				emit(buffer, pose, poly.get(i + 1), light, overlay);
			}
		}
	}

	private static void emit(VertexConsumer buffer, PoseStack.Pose pose, float[] v, int light, int overlay) {
		buffer.addVertex(pose, v[0], v[1], v[2])
				.setColor(1F, 1F, 1F, 1F)
				.setUv(v[3], v[4])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, v[5], v[6], v[7]);
	}
}
