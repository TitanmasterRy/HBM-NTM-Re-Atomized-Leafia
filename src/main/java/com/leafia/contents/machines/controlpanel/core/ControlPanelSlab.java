package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * The geometry of the desk panel's slab, in <b>block space</b>: six quads built from the panel's height, slope and four
 * edge insets. Adapted from NTM-CE's {@code RenderControlPanel.renderCustomPanel} (D-3065), which built the same quads
 * between GL calls, in a frame its rotation chain had set up from the block's metadata.
 *
 * <p>The chain is CE's, written once with JOML instead of as GL calls: to the block's middle; {@code -90} about Z; about X by
 * the facing (west {@code -180}, north {@code -90}, east {@code 0}, south {@code -270}); then for a ceiling panel {@code -180}
 * about X, {@code -90} about Z and one block down, for a floor panel {@code 90} about Z, and for a wall panel half a block
 * back; and finally half a block back in X and Z, so the slab's own corner is the origin. Doing it here rather than with a
 * {@code PoseStack} means the renderer needs no matrix at all, and a GameTest on a server can check the result against the
 * block's collision box ({@code ControlPanelTE.getBoundingBox}), which is the only thing that ties the facing table to the
 * world.
 *
 * <p>Pure maths, nothing client-only: the sweep in {@code LeafiaControlPanelDataTests} loads it.
 */
public final class ControlPanelSlab {

	private ControlPanelSlab() {}

	/**
	 * One quad: four corners in block space, their texture coordinates in the slab texture's 0..1, and the outward normal.
	 * Wound so the corners run counter-clockwise seen from outside, whatever order CE wrote them in.
	 */
	public record Quad(float[] pos, float[] uv, float[] normal) {}

	/** The rotation chain as one matrix: slab-local (corner at the origin, y up out of the panel) to block space. */
	public static Matrix4f frame(boolean up, boolean down, Direction facing) {
		boolean isDown = !up && down;
		Matrix4f m = new Matrix4f();
		m.translate(0.5F, 0, 0.5F);
		m.rotateZ(rad(-90));
		switch(facing) {
			case WEST -> m.rotateX(rad(-180));
			case NORTH -> m.rotateX(rad(-90));
			case SOUTH -> m.rotateX(rad(-270));
			default -> m.rotateX(rad(0));
		}
		if(isDown) {
			m.rotateX(rad(-180));
			m.rotateZ(rad(-90));
			m.translate(0, -1, 0);
		} else if(up) {
			m.rotateZ(rad(90));
		} else {
			m.translate(-0.5F, -0.5F, 0);
		}
		m.translate(-0.5F, 0, -0.5F);
		return m;
	}

	/**
	 * The slab's quads for a panel, or an empty list for a flat one (CE drew nothing when {@code height == 0}).
	 * Every corner is in block space; a quad that collapsed to a line (a face of zero area) is left out.
	 */
	public static List<Quad> build(ControlPanel panel, boolean up, boolean down, Direction facing) {
		List<Quad> out = new ArrayList<>();
		float height = panel.height;
		if(height == 0)
			return out;

		float a = panel.a_off, b = panel.b_off, c = panel.c_off, d = panel.d_off;
		float height1 = ControlPanel.getSlopeHeightFromZ(1 - c, height, -panel.angle);
		float height0 = ControlPanel.getSlopeHeightFromZ(a, height, -panel.angle);

		// CE's six quads, corner for corner: x, y, z then u, v.
		float[][] raw = {
				// back
				{a, 0, 1 - d, 1, 1, a, height0, 1 - d, 1, 0, a, height0, b, 0, 0, a, 0, b, 0, 1},
				// left
				{1 - c, 0, 1 - d, 1, 1, 1 - c, height1, 1 - d, 1, 0, a, height0, 1 - d, 0, 0, a, 0, 1 - d, 0, 1},
				// right
				{a, 0, b, 1, 1, a, height0, b, 1, 0, 1 - c, height1, b, 0, 0, 1 - c, 0, b, 0, 1},
				// front
				{1 - c, 0, b, 1, 1, 1 - c, height1, b, 1, 0, 1 - c, height1, 1 - d, 0, 0, 1 - c, 0, 1 - d, 0, 1},
				// top
				{1 - c, height1, b, 1, 1, a, height0, b, 1, 0, a, height0, 1 - d, 0, 0, 1 - c, height1, 1 - d, 0, 1},
				// bottom
				{1 - c, 0, 1 - d, 0, 1, a, 0, 1 - d, 0, 0, a, 0, b, 1, 0, 1 - c, 0, b, 1, 1},
		};

		Matrix4f frame = frame(up, down, facing);
		float[][] pos = new float[raw.length][12];
		float[][] uv = new float[raw.length][8];
		Vector3f centre = new Vector3f();
		Vector3f v = new Vector3f();
		for(int q = 0; q < raw.length; q++) {
			for(int k = 0; k < 4; k++) {
				v.set(raw[q][k * 5], raw[q][k * 5 + 1], raw[q][k * 5 + 2]);
				frame.transformPosition(v);
				pos[q][k * 3] = v.x;
				pos[q][k * 3 + 1] = v.y;
				pos[q][k * 3 + 2] = v.z;
				centre.add(v);
				uv[q][k * 2] = raw[q][k * 5 + 3];
				uv[q][k * 2 + 1] = raw[q][k * 5 + 4];
			}
		}
		centre.div(raw.length * 4);

		for(int q = 0; q < raw.length; q++) {
			float[] p = pos[q];
			// Newell-style normal from the diagonals: (p2 - p0) x (p3 - p1).
			float ax = p[6] - p[0], ay = p[7] - p[1], az = p[8] - p[2];
			float bx = p[9] - p[3], by = p[10] - p[4], bz = p[11] - p[5];
			float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if(len < 1E-6F)
				continue;
			nx /= len;
			ny /= len;
			nz /= len;
			float mx = (p[0] + p[3] + p[6] + p[9]) / 4 - centre.x;
			float my = (p[1] + p[4] + p[7] + p[10]) / 4 - centre.y;
			float mz = (p[2] + p[5] + p[8] + p[11]) / 4 - centre.z;
			float[] fp = p.clone();
			float[] fu = uv[q].clone();
			if(nx * mx + ny * my + nz * mz < 0) {
				// wound inward: reverse the corners and their coordinates, and flip the normal
				for(int k = 0; k < 4; k++) {
					int from = 3 - k;
					fp[k * 3] = p[from * 3];
					fp[k * 3 + 1] = p[from * 3 + 1];
					fp[k * 3 + 2] = p[from * 3 + 2];
					fu[k * 2] = uv[q][from * 2];
					fu[k * 2 + 1] = uv[q][from * 2 + 1];
				}
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			out.add(new Quad(fp, fu, new float[]{nx, ny, nz}));
		}
		return out;
	}

	/** The box that contains every quad {@link #build} gives, for comparison with the block's collision box. Null for a flat panel. */
	public static AABB bounds(List<Quad> quads) {
		if(quads.isEmpty())
			return null;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for(Quad q : quads) {
			for(int k = 0; k < 4; k++) {
				minX = Math.min(minX, q.pos[k * 3]);
				minY = Math.min(minY, q.pos[k * 3 + 1]);
				minZ = Math.min(minZ, q.pos[k * 3 + 2]);
				maxX = Math.max(maxX, q.pos[k * 3]);
				maxY = Math.max(maxY, q.pos[k * 3 + 1]);
				maxZ = Math.max(maxZ, q.pos[k * 3 + 2]);
			}
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static float rad(float degrees) {
		return (float) Math.toRadians(degrees);
	}
}
