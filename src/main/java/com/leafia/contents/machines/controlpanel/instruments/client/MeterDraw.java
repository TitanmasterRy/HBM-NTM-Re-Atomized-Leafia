package com.leafia.contents.machines.controlpanel.instruments.client;

import com.leafia.contents.machines.controlpanel.core.client.ControlDraw;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.horizontal.MeterHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical.MeterVertical;
import com.mojang.math.Axis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two meters' drawing (CCP-INSTRUMENTS-2, D-3072): ports of the bodies of Leafia's {@code MeterHorizontal.render} and
 * {@code MeterVertical.render}.
 *
 * <h2>The horizontal meter</h2>
 * Leafia drew it in a 2D frame (x across, y down) rotated a quarter about the panel's x axis; in the panel's plane that frame is
 * simply {@code (x, z)}, so every quad here is a {@link ControlDraw#flatQuad} at {@code (x, y2d)}. The layers sit 0.01 apart above the
 * panel (Leafia's 0.002 would z-fight at a block's depth precision): the frame, the white face, the marks, the needle's rail, the
 * pointer's black outline and the pointer's glow. The face is a white plate 0.1 in from a dark frame; the labelled marks are 0.025
 * wide and 0.25 tall, the small ones 0.15 (0.2 for the middle one of an odd count); a label sits just above its mark, 0.025 per pixel.
 * The pointer is a pentagon, a rectangle with a point toward the marks, in black with the needle's colour (emissive) inside it.
 *
 * <h2>The vertical meter</h2>
 * A baked base plate, a baked needle, and a baked drum that turns about an axis 1.75 below the panel; the scale is printed round the
 * drum (a thin mark and a label every division, and small marks between) in the drum's own turning frame. Leafia cut the drum off at
 * the panel's plane with {@code GL_CLIP_PLANE0}; there is no clip plane here, so the part of the drum below the panel's plane is
 * hidden only by whatever the slab hides, and a client check must look at it from the side.
 *
 * <p>Both leave Leafia's editor-only 2D previews ({@code renderControl}) to {@code ControlPreview.real}, as every control does.
 */
@OnlyIn(Dist.CLIENT)
final class MeterDraw {

	private MeterDraw() {}

	private static final String V = AddonInstrumentModels.METER_V;

	// ------------------------------------------------------------------ horizontal

	static void horizontal(MeterHorizontal m, ControlDraw d) {
		d.pose.pushPose();
		d.pose.translate(-5 / 2f, 0, -0.5F); // Leafia: translate(posX - 5/2, 0, posY - 0.5), then a quarter turn about x
		draw(m, d, 1F, m.fraction());
		d.pose.popPose();
	}

	private static void draw(MeterHorizontal m, ControlDraw d, float greenTint, float value) {
		int length = m.getLength();
		int divisions = m.getDivisions();
		int subdivisions = m.getSubdivisions();

		// the dark frame and the white face
		d.flatQuad(0, 0, length, 1, 0.01F, 0.1F, 0.1F * greenTint, 0.1F);
		float length1 = length - 0.2F;
		d.flatQuad(0.1F, 0.1F, 0.1F + length1, 0.9F, 0.02F, 1, greenTint, 1);

		// the marks and their labels, all 0.1 lower than the face's top edge
		float padding = MeterHorizontal.PADDING;
		float endpoint = length - padding;
		float yOff = 0.1F;
		int startY = 22;
		for(int i0 = 0; i0 < divisions; i0++) {
			float i = i0 / (float) (divisions - 1);
			if(subdivisions > 0 && i0 < divisions - 1) {
				boolean isOdd = subdivisions % 2 == 1;
				for(int j = 1; j <= subdivisions; j++) {
					float increment = 1f / (divisions - 1) / (subdivisions + 1);
					float x = MeterHorizontal.lerp(padding, endpoint, i + increment * j);
					int height = 3;
					if(isOdd && j - 1 == subdivisions / 2)
						height = 5;
					d.flatQuad(x - 0.0125F, yOff + (startY - height) * 0.025F, x + 0.0125F, yOff + (startY + 3) * 0.025F, 0.03F, 0, 0, 0);
				}
			}
			float x = MeterHorizontal.lerp(padding, endpoint, i);
			d.flatQuad(x - 0.0125F, yOff + (startY - 7) * 0.025F, x + 0.0125F, yOff + (startY + 3) * 0.025F, 0.03F, 0, 0, 0);
			String s = m.label(i0);
			d.pose.pushPose();
			d.pose.translate(x, 0.035F, yOff);
			d.flatText(0.025F, false);
			d.text(s, -((d.font.width(s) - 1) / 2), startY - 8 - 7, 0x000000, false);
			d.pose.popPose();
		}

		// the needle's rail
		float nw = 0.05F;
		float nh = 0.7F;
		d.flatQuad(padding - nw, yOff + nh - nw * 2, endpoint + nw, yOff + nh, 0.04F, 0, 0, 0);

		// the pointer: a black outline, then the needle's colour, lit
		float x = MeterHorizontal.lerp(padding, endpoint, value);
		float no = nw / 3;
		pointer(d, x, yOff, nh, nw, no, 0.05F, 0, 0, 0, false);
		pointer(d, x, yOff, nh, nw, 0, 0.06F, m.getRed() / 255F, m.getGreen() / 255F * greenTint, m.getBlue() / 255F, true);
	}

	/**
	 * Leafia's five-corner fan: a box {@code nw} either side of {@code x} and {@code 2 nw} tall above the rail's lower edge, with its
	 * point {@code 3 nw} up, every edge grown by {@code grow} (the outline is the same shape grown by a third of {@code nw}).
	 */
	private static void pointer(ControlDraw d, float x, float yOff, float nh, float nw, float grow, float height, float r, float g, float b, boolean lit) {
		float[] xs = {x - nw - grow, x - nw - grow, x + nw + grow, x + nw + grow, x};
		float[] zs = {yOff + nh - nw * 2 - grow, yOff + nh + grow, yOff + nh + grow, yOff + nh - nw * 2 - grow, yOff + nh - nw * 3 - grow};
		d.flatPolygon(xs, zs, 5, height, r, g, b, lit);
	}

	// ------------------------------------------------------------------ vertical

	static void vertical(MeterVertical m, ControlDraw d) {
		int divisions = m.getDivisions();
		int subdivisions = m.getSubdivisions();
		d.pose.pushPose();
		d.part(V, "Base", 0.1F, 0.1F, 0.1F);

		// the needle, then the drum, each turning about an axis 1.75 below the panel; the drum's turn is on top of the needle's offset
		d.pose.translate(0, -1.75F, 0);
		d.pose.mulPose(Axis.XP.rotationDegrees(-m.getNeedleOffset() * MeterVertical.NEEDLE_STEP));
		d.pose.translate(0, 1.75F, 0);
		d.part(V, "Needle");

		d.pose.translate(0, -1.75F, 0);
		d.pose.mulPose(Axis.XP.rotationDegrees(m.getCurAngle()));
		d.pose.translate(0, 1.75F, 0);
		d.part(V, "Meter");

		int offset = 5;
		int startX = 26 - offset + 1;
		int endX = 18 - offset - 3;
		for(int i0 = 0; i0 <= divisions; i0++) {
			float i = i0 / (float) divisions;
			d.pose.pushPose();
			d.pose.translate(0, -1.75F, 0);
			d.pose.mulPose(Axis.XP.rotationDegrees(m.getAngleForRatio(i)));
			if(subdivisions > 0 && i0 < divisions) {
				for(int j = 1; j <= subdivisions; j++) {
					int endX2 = 22 - offset - 3;
					float increment = 1f / divisions / (subdivisions + 1);
					d.pose.pushPose();
					d.pose.mulPose(Axis.XP.rotationDegrees(m.getAngleForRatio(increment * j + 0.5f)));
					d.pose.translate(0, 2.9F, 0);
					mark(d, endX2, startX);
					d.pose.popPose();
				}
			}
			d.pose.translate(0, 2.9F, 0);
			mark(d, endX, startX);
			String s = m.label(i0);
			d.pose.pushPose();
			d.pose.translate(0, 0.02F, 0);
			d.flatText(0.025F, false);
			d.text(s, 15 - 3 - d.font.width(s), -3 - 0.5F, 0x000000, false);
			d.pose.popPose();
			d.pose.popPose();
		}
		d.pose.popPose();
	}

	/** One mark on the drum: {@code x0} to {@code x1} along the drum's axis in 0.025s, one 0.025 thick across it, centred. */
	private static void mark(ControlDraw d, int x0, int x1) {
		d.pose.pushPose();
		d.pose.scale(0.025F, 0.025F, 0.025F);
		d.flatQuad(x0, -0.5F, x1, 0.5F, 0.8F, 0, 0, 0);
		d.pose.popPose();
	}
}
