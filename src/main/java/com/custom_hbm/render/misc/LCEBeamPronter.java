package com.custom_hbm.render.misc;

import java.util.Random;

import com.custom_hbm.util.LCEColor;
import com.hbm.render.util.BeamPronter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * The base mod's {@link BeamPronter}, with a better {@code RANDOM} wave and a {@code STRAIGHT} one.
 *
 * <p>Upstream: {@code com/custom_hbm/render/misc/LCEBeamPronter.java}. It stays in its own
 * package (D-3000: the {@code com.custom_hbm} name is where NTM-CE's additions live upstream, and
 * the addon keeps the neighbourhood) - it does not collide with the base class, which is
 * {@code com.hbm.render.util.BeamPronter}.
 *
 * <h2>What differs from the base beam, as upstream wrote it</h2>
 *
 * <ul>
 * <li>{@code STRAIGHT}: no offset at all, so the beam is a ruler-straight column.
 * <li>{@code RANDOM} leaves the first and last point on the axis, so a beam always starts and ends
 *     where it was told to; scales each offset by a random fraction of the radius rather than
 *     always using the whole of it; and <b>drops about one point in three</b>, which is what
 *     makes the arc look like lightning rather than a vibrating tube. A dropped point is skipped
 *     entirely - the next segment is drawn from the last point that was kept, so it is longer.
 * <li>The layers blend from the inner colour (thinnest) out to the outer colour (thickest), and a
 *     one-layer beam is simply the outer colour - the base beam divides by {@code layers - 1}
 *     and draws a one-layer solid beam black.
 * </ul>
 *
 * <h2>What the translation changes</h2>
 *
 * Upstream drew into the global GL matrix and {@code Tessellator}, and snapshotted, changed and
 * restored a dozen GL flags around the draw. Here the pose stack and buffers are passed in, and
 * the flags are one render type: {@link BeamPronter#BEAM} is exactly the state the solid branch
 * set (additive {@code SRC_ALPHA, ONE} blending, no culling, no lighting, no depth write), and
 * the line branch is vanilla's {@link RenderType#lines()}. So the {@code shouldDisableTexture2D}
 * parameter is gone: it said whether to switch texturing off, and nothing here is textured.
 * The one caller that passed {@code false} (the cable) bound {@code solid_emissive.png} first, a
 * flat full-bright texture, to the same effect as full-bright vertices.
 *
 * <p>Upstream's {@code Vec3d.rotateYaw} is {@link BeamPronter#rotY}, the same formula on the same
 * lookup-table sine. The colour blend is {@link LCEColor#interpolateColor}, a per-channel linear
 * interpolation in a class of its own (this one is client-only, so a server cannot load it to test
 * the arithmetic); upstream calls {@code BobMathUtil.interpolateColor}, which is NTM-CE's and is
 * not in the reference clone, so its exact rounding is an assumption (D-3008).
 *
 * <h2>{@code gluonBeam} is not here</h2>
 *
 * Upstream's second method draws the gluon beam through two shaders ({@code gluon_spiral},
 * {@code gluon_beam}) and three noise textures. Nothing in the addon or in the clone calls it,
 * and the base mod has neither shader. A method with no caller and no assets under it is a
 * placeholder, not a port; it comes back with a caller.
 */
@OnlyIn(Dist.CLIENT)
public class LCEBeamPronter {

	public enum EnumWaveType {
		RANDOM, SPIRAL, STRAIGHT
	}

	public enum EnumBeamType {
		SOLID, LINE
	}

	/**
	 * Draws a beam from the current origin along {@code skeleton}.
	 *
	 * @param start     the seed: the RANDOM wave is deterministic in it, and the SPIRAL's phase is in degrees of it
	 * @param segments  how many pieces the skeleton is cut into
	 * @param spinRadius how far a wavering point can stray from the axis
	 * @param layers    concentric shells, thinnest (inner colour) to thickest (outer colour)
	 * @param thickness the outermost shell's half-width
	 */
	public static void prontBeam(PoseStack pose, MultiBufferSource buffers, Vec3 skeleton, EnumWaveType wave, EnumBeamType beam,
			int outerColor, int innerColor, int start, int segments, float spinRadius, int layers, float thickness) {

		pose.pushPose();

		float sYaw = (float) (Math.atan2(skeleton.x, skeleton.z) * 180F / Math.PI);
		float sqrt = Mth.sqrt((float) (skeleton.x * skeleton.x + skeleton.z * skeleton.z));
		float sPitch = (float) (Math.atan2(skeleton.y, (double) sqrt) * 180F / Math.PI);

		pose.mulPose(Axis.YP.rotationDegrees(180));
		pose.mulPose(Axis.YP.rotationDegrees(sYaw));
		pose.mulPose(Axis.XP.rotationDegrees(sPitch - 90));

		Matrix4f matrix = pose.last().pose();
		VertexConsumer consumer = buffers.getBuffer(beam == EnumBeamType.SOLID ? BeamPronter.BEAM : RenderType.lines());

		// Local, not shared: the base beam's static Random is reseeded per call and two beams in
		// one frame would otherwise draw from each other's sequence if a renderer ever overlapped.
		Random rand = new Random(start);
		double length = skeleton.length();
		double segLength = length / segments;
		double lastX = 0, lastY = 0, lastZ = 0;

		for(int i = 0; i <= segments; i++) {

			boolean firstSegment = i == 0;
			boolean lastSegment = i == segments;

			// The skeleton is along +Y once rotated: upstream's unit vector (0, 1, 0).
			double pX = 0;
			double pY = segLength * i;
			double pZ = 0;

			if(wave != EnumWaveType.STRAIGHT && !(wave == EnumWaveType.RANDOM && (firstSegment || lastSegment))) {

				double[] spinner = { spinRadius, 0, 0 };

				if(wave == EnumWaveType.SPIRAL) {
					BeamPronter.rotY(spinner, (float) Math.PI * start / 180F);
					BeamPronter.rotY(spinner, (float) Math.PI * 45F / 180F * i);
				} else { // RANDOM
					float mult = rand.nextFloat();
					spinner[0] *= mult;
					spinner[1] *= mult;
					spinner[2] *= mult;
					BeamPronter.rotY(spinner, (float) (Math.PI * 2 * rand.nextFloat()));
					if(rand.nextInt(3) == 0) continue;
				}

				pX += spinner[0];
				pY += spinner[1];
				pZ += spinner[2];
			}

			if(beam == EnumBeamType.LINE && i > 0) {
				line(consumer, pose, pX, pY, pZ, lastX, lastY, lastZ, outerColor);
			}

			if(beam == EnumBeamType.SOLID && i > 0) {

				float radius = thickness / layers;

				for(int j = 1; j <= layers; j++) {

					int color = LCEColor.layerColor(innerColor, outerColor, layers, j);

					double w = radius * j;

					quad(consumer, matrix, color, lastX + w, lastY, lastZ + w, lastX + w, lastY, lastZ - w, pX + w, pY, pZ - w, pX + w, pY, pZ + w);
					quad(consumer, matrix, color, lastX - w, lastY, lastZ + w, lastX - w, lastY, lastZ - w, pX - w, pY, pZ - w, pX - w, pY, pZ + w);
					quad(consumer, matrix, color, lastX + w, lastY, lastZ + w, lastX - w, lastY, lastZ + w, pX - w, pY, pZ + w, pX + w, pY, pZ + w);
					quad(consumer, matrix, color, lastX + w, lastY, lastZ - w, lastX - w, lastY, lastZ - w, pX - w, pY, pZ - w, pX + w, pY, pZ - w);
				}
			}

			lastX = pX;
			lastY = pY;
			lastZ = pZ;
		}

		if(beam == EnumBeamType.LINE) {
			line(consumer, pose, 0, 0, 0, 0, length, 0, innerColor);
		}

		pose.popPose();
	}

	private static void quad(VertexConsumer consumer, Matrix4f matrix, int color,
			double x0, double y0, double z0, double x1, double y1, double z1,
			double x2, double y2, double z2, double x3, double y3, double z3) {
		vertex(consumer, matrix, color, x0, y0, z0);
		vertex(consumer, matrix, color, x1, y1, z1);
		vertex(consumer, matrix, color, x2, y2, z2);
		vertex(consumer, matrix, color, x3, y3, z3);
	}

	private static void vertex(VertexConsumer consumer, Matrix4f matrix, int color, double x, double y, double z) {
		consumer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
	}

	private static void line(VertexConsumer consumer, PoseStack pose, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
		float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
		float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if(len > 0) { nx /= len; ny /= len; nz /= len; }
		PoseStack.Pose last = pose.last();
		consumer.addVertex(last, (float) x0, (float) y0, (float) z0).setColor(0xFF000000 | color).setNormal(last, nx, ny, nz);
		consumer.addVertex(last, (float) x1, (float) y1, (float) z1).setColor(0xFF000000 | color).setNormal(last, nx, ny, nz);
	}
}
