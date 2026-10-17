package com.leafia.contents.machines.controlpanel.core;

/**
 * The editor's flat geometry, as pure arithmetic so a GameTest can pin it (CCP-EDITOR, D-3066). Adapted from NTM-CE's
 * {@code NTMRenderHelper.intersects2DBox}, {@code boxesOverlap} and {@code boxContainsOther} and from
 * {@code TileEntityControlPanel.getBox}. A box is {@code {minX, minY, maxX, maxY}} in the editor's grid units, where one control
 * unit is a tenth of a block. Nothing here names a client type, which is why it is not in {@code core.client}.
 */
public final class ControlGeometry {

	private ControlGeometry() {}

	/** Strictly inside: a point on the edge is not in the box. */
	public static boolean intersects2DBox(float x, float y, float[] box) {
		return x > box[0] && x < box[2] && y > box[1] && y < box[3];
	}

	/** Two boxes that share only an edge do not overlap. */
	public static boolean boxesOverlap(float[] box1, float[] box2) {
		return box1[0] < box2[2] && box1[2] > box2[0] && box1[1] < box2[3] && box1[3] > box2[1];
	}

	/** An inner box touching the outer one's edge is still contained. */
	public static boolean boxContainsOther(float[] box, float[] other) {
		return box[0] <= other[0] && box[1] <= other[1] && box[2] >= other[2] && box[3] >= other[3];
	}

	/** The slab's footprint on the editor's grid: minX, minY, maxX, maxY, in grid units. */
	public static float[] panelBox(ControlPanel panel) {
		float baseSizeX = 1 - (panel.b_off + panel.d_off);
		float baseSizeY = 1 - (panel.a_off + panel.c_off);

		double base_hyp = 1 / Math.cos(Math.abs(panel.angle));
		double panel_hyp = baseSizeY / Math.cos(Math.abs(panel.angle));

		float box_width = 10;
		float box_height = (float) (base_hyp * 10);
		float minX = (-box_width / 2) + (panel.d_off * box_width);
		float minY = (-box_height / 2) + (panel.a_off * box_height);

		return new float[]{minX, minY, minX + baseSizeX * 10, (float) (minY + panel_hyp * 10)};
	}
}
