package com.leafia.contents.building.doors.renderers;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry under {@link DoorClip}, with nothing client-side in it so a GameTest can run it.
 *
 * <p>A vertex is {@code x, y, z, u, v, nx, ny, nz}.
 */
public final class DoorClipMath {

	private DoorClipMath() { }

	/** Floats per vertex. */
	public static final int N = 8;

	/** One Sutherland-Hodgman pass: keep {@code z >= bound} when {@code above}, else {@code z <= bound}. */
	public static List<float[]> cut(List<float[]> poly, float bound, boolean above) {

		List<float[]> out = new ArrayList<>(poly.size() + 2);

		for(int i = 0; i < poly.size(); i++) {

			float[] a = poly.get(i);
			float[] b = poly.get((i + 1) % poly.size());
			boolean aIn = above ? a[2] >= bound : a[2] <= bound;
			boolean bIn = above ? b[2] >= bound : b[2] <= bound;

			if(aIn) out.add(a);

			if(aIn != bIn) {
				float t = (bound - a[2]) / (b[2] - a[2]);
				float[] c = new float[N];
				for(int k = 0; k < N; k++) c[k] = a[k] + (b[k] - a[k]) * t;
				c[2] = bound;
				out.add(c);
			}
		}

		return out;
	}

}
