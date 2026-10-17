package com.leafia.contents.machines.controlpanel.instruments.client;

import java.util.Locale;

import com.leafia.contents.machines.controlpanel.core.client.ControlDraw;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph.GraphSkin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the graph instrument (CCP-INSTRUMENTS-1, D-3071): a port of the body of Leafia's {@code Graph.render}, layer for layer.
 *
 * <p>Leafia drew with {@code LeafiaBrush} quads and four GL clip planes to keep the line inside the plot; 1.21.1 has neither, so
 * each quad here is clipped to the plot rectangle on the CPU ({@link #clip}) before it is emitted, which gives the same picture.
 * The layers sit a little above the panel and each other (heights in panel units) instead of Leafia's 0.001 steps, which z-fight
 * at a block's depth precision: the frame 0.01, the background 0.02, the grid 0.03, the line 0.04, the scale's text 0.05.
 *
 * <p>In the control's frame the plot's top left corner is at {@code (-width / 2, -height / 2)} and the maximum value is at the top
 * ({@code z = 0}). The grid's vertical lines are every {@code verticalLineSpan} samples; the horizontal ones divide the height
 * {@code horizontalLineDivision} ways and carry the scale at the right, {@code textOffset} units wide, in the skin's text colour
 * with {@code decimalLength} places. The line is {@code 0.1} units thick, a quad per segment with a wedge filling the outside of
 * each bend, in the colour {@code red}/{@code green}/{@code blue} (emissive in an emissive skin).
 *
 * <p><b>Two deliberate differences from Leafia's picture.</b> (1) The wedge at a bend is drawn from the third sample on: Leafia
 * drew one from the second sample too, whose "previous corners" were still the zero it started with, so a rising first segment
 * grew a sliver to the plot's top left corner. (2) A sample that is not a finite number draws as 0, where Leafia's line went to NaN
 * and vanished, and a one-sample graph draws its sample at the left (Leafia divided by zero). A client check must look at both.
 */
@OnlyIn(Dist.CLIENT)
final class GraphDraw {

	private GraphDraw() {}

	private static final float LINE = 0.05F;
	private static final float GRID = 0.01F;

	static void draw(Graph g, ControlDraw d) {
		float width = g.getWidth();
		float height = g.getHeight();
		float border = Graph.BORDER;
		GraphSkin skin = g.getSkin();

		d.pose.pushPose();
		d.pose.translate(-width / 2, 0, -height / 2);

		// frame
		d.flatQuad(-border, -border, width + border, height + border, 0.01F, 0.25F, 0.25F, 0.25F);

		// background
		float[] bg = rgb(skin.bgColor);
		d.flatQuad(0, 0, width, height, 0.02F, bg[0], bg[1], bg[2]);

		float[] gc = rgb(skin.gridColor);
		double[] values = g.values;
		int n = values.length;
		float sc = (width - g.getTextOffset()) / width;

		// vertical grid lines
		for(int i = 0; i < n; i += g.getVerticalLineSpan()) {
			float x = (float) (i / Math.max(1d, n - 1d)) * width * sc;
			d.flatQuad(x - GRID, 0, x + GRID, height, 0.03F, gc[0], gc[1], gc[2]);
		}

		// horizontal grid lines and the scale
		int div = g.getHorizontalLineDivision();
		if(div > 0) {
			double min = g.getMinValue();
			double max = g.getMaxValue();
			float[] tc = rgb(skin.textColor);
			int text = ((int) (tc[0] * 255) << 16) | ((int) (tc[1] * 255) << 8) | (int) (tc[2] * 255);
			for(int i = 0; i <= div; i++) {
				float y = i / (float) div;
				float z = y * height;
				d.flatQuad(0, z - GRID, width - g.getTextOffset(), z + GRID, 0.03F, gc[0], gc[1], gc[2]);
				double value = max + (min - max) / div * i;
				d.pose.pushPose();
				d.pose.translate(width - g.getTextOffset(), 0.05F, z);
				d.flatText(0.02F, false);
				int ypos = -7 / 2;
				if(i == 0)
					ypos = 0;
				if(i == div)
					ypos = -7;
				d.text(String.format(Locale.ROOT, "%01." + g.getDecimalLength() + "f", value), 1, ypos, text, false);
				d.pose.popPose();
			}
		}

		line(g, d, width, height, sc, n);
		d.pose.popPose();
	}

	/** The line: a quad per segment, a wedge at each bend, every one clipped to the plot. */
	private static void line(Graph g, ControlDraw d, float width, float height, float sc, int n) {
		if(n < 1)
			return;
		boolean full = g.getSkin().emissive;
		float r = g.getRed() / 255, gr = g.getGreen() / 255, b = g.getBlue() / 255;
		double[] values = g.values;
		double spacing = 1 / Math.max(1d, n - 1d);

		double x0 = -1, y0 = -1, delta0 = 0, value0 = 0;
		double lowerX = 0, lowerY = 0, upperX = 0, upperY = 0;
		for(int i = 0; i < n; i++) {
			double sample = Double.isFinite(values[i]) ? values[i] : 0;
			double scaled = g.scaleValue(sample);
			double x1 = spacing * i * width;
			double y1 = height * (1 - scaled);
			double delta1 = i == 0 ? 0 : scaled - value0;
			if(x0 >= 0) {
				double ang = Math.atan2(delta1, spacing);
				double up = ang + Math.PI / 2;
				double upX = Math.cos(up) * LINE;
				double upY = -Math.sin(up) * LINE; // the plot's vertical axis runs down
				if(i >= 2) {
					if(delta1 > delta0)
						emit(d, width, height, 0.04F, r, gr, b, full, x0 * sc, y0, lowerX, lowerY, x0 * sc - upX, y0 - upY);
					else if(delta1 < delta0)
						emit(d, width, height, 0.04F, r, gr, b, full, x0 * sc, y0, x0 * sc + upX, y0 + upY, upperX, upperY);
				}
				emit(d, width, height, 0.04F, r, gr, b, full, x0 * sc - upX, y0 - upY, x1 * sc - upX, y1 - upY, x1 * sc + upX, y1 + upY, x0 * sc + upX, y0 + upY);
				lowerX = x1 * sc - upX;
				lowerY = y1 - upY;
				upperX = x1 * sc + upX;
				upperY = y1 + upY;
			}
			delta0 = delta1;
			value0 = scaled;
			x0 = x1;
			y0 = y1;
		}
	}

	/** Clips the polygon given as x, z pairs to the plot and draws what is left. */
	private static void emit(ControlDraw d, float width, float height, float y, float r, float g, float b, boolean full, double... pts) {
		int n = pts.length / 2;
		float[] xs = new float[n + 4];
		float[] zs = new float[n + 4];
		for(int i = 0; i < n; i++) {
			xs[i] = (float) pts[2 * i];
			zs[i] = (float) pts[2 * i + 1];
		}
		n = clip(xs, zs, n, width, height);
		d.flatPolygon(xs, zs, n, y, r, g, b, full);
	}

	/**
	 * Sutherland-Hodgman against {@code 0 <= x <= width} and {@code 0 <= z <= height}, in place; returns the new vertex count (0 when
	 * nothing is inside). A convex polygon has at most four more corners after four cuts, and the arrays are sized for that.
	 */
	static int clip(float[] xs, float[] zs, int n, float width, float height) {
		float[] ox = new float[n + 4];
		float[] oz = new float[n + 4];
		for(int plane = 0; plane < 4 && n > 0; plane++) {
			int m = 0;
			for(int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float ax = xs[i], az = zs[i], bx = xs[j], bz = zs[j];
				float da = distance(plane, ax, az, width, height);
				float db = distance(plane, bx, bz, width, height);
				if(da >= 0) {
					ox[m] = ax;
					oz[m] = az;
					m++;
				}
				if((da >= 0) != (db >= 0)) {
					float t = da / (da - db);
					ox[m] = ax + (bx - ax) * t;
					oz[m] = az + (bz - az) * t;
					m++;
				}
			}
			n = m;
			System.arraycopy(ox, 0, xs, 0, Math.min(m, xs.length));
			System.arraycopy(oz, 0, zs, 0, Math.min(m, zs.length));
		}
		return n;
	}

	/** Signed distance inside plane {@code plane}: left, right, top, bottom of the plot. */
	private static float distance(int plane, float x, float z, float width, float height) {
		return switch(plane) {
			case 0 -> x;
			case 1 -> width - x;
			case 2 -> z;
			default -> height - z;
		};
	}

	static float[] rgb(int color) {
		return new float[]{((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F};
	}
}
