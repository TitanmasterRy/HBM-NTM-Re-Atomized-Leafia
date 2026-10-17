package com.leafia.client.particle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The triangles of an OBJ, read straight from the file.
 *
 * <p>Upstream's three sphere particles draw {@code ResourceManager.sphere_ruv}, an icosphere of
 * diameter 1, by calling {@code renderAll()} under a scale. Here a particle writes its own vertices
 * into whatever buffer its render type hands it, so what it needs is the bare geometry — positions
 * and triangle indices — and nothing a model baker would add (a texture, a tint, UVs, normals, a
 * material). Baking it through {@code neoforge:obj} would also need a {@code usemtl}, without which
 * NeoForge bakes zero quads silently (D-1058), and the base mod's copy has none; reading the file
 * avoids both.
 *
 * <p>Only {@code v} and {@code f} lines are read. A face is fanned into triangles, so a quad
 * {@code a b c d} becomes {@code a b c} and {@code a c d}, and each vertex reference may be
 * {@code v}, {@code v/vt}, {@code v//vn} or {@code v/vt/vn}; only the position index is used. A
 * negative index counts from the end, as the format allows.
 *
 * <p>No client class is imported, so a GameTest can parse the real file.
 */
public final class SphereMesh {

	/** Packed {@code x, y, z} of each vertex. */
	public final float[] positions;
	/** Three vertex indices per triangle, wound as the file winds them. */
	public final int[] triangles;

	private SphereMesh(float[] positions, int[] triangles) {
		this.positions = positions;
		this.triangles = triangles;
	}

	/** No geometry: what a sphere particle draws when the model cannot be read. */
	public static SphereMesh empty() { return new SphereMesh(new float[0], new int[0]); }

	public int vertexCount() { return positions.length / 3; }

	public int triangleCount() { return triangles.length / 3; }

	public static SphereMesh parse(InputStream stream) throws IOException {
		List<float[]> verts = new ArrayList<>();
		List<int[]> tris = new ArrayList<>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while((line = reader.readLine()) != null) {
				line = line.trim();
				if(line.startsWith("v ")) {
					String[] p = line.split("\\s+");
					verts.add(new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
				} else if(line.startsWith("f ")) {
					String[] p = line.split("\\s+");
					int[] corners = new int[p.length - 1];
					for(int i = 1; i < p.length; i++) {
						int slash = p[i].indexOf('/');
						int index = Integer.parseInt(slash < 0 ? p[i] : p[i].substring(0, slash));
						corners[i - 1] = index > 0 ? index - 1 : verts.size() + index;
					}
					for(int i = 1; i + 1 < corners.length; i++)
						tris.add(new int[] { corners[0], corners[i], corners[i + 1] });
				}
			}
		}
		float[] positions = new float[verts.size() * 3];
		for(int i = 0; i < verts.size(); i++) {
			positions[i * 3] = verts.get(i)[0];
			positions[i * 3 + 1] = verts.get(i)[1];
			positions[i * 3 + 2] = verts.get(i)[2];
		}
		int[] triangles = new int[tris.size() * 3];
		for(int i = 0; i < tris.size(); i++) {
			triangles[i * 3] = tris.get(i)[0];
			triangles[i * 3 + 1] = tris.get(i)[1];
			triangles[i * 3 + 2] = tris.get(i)[2];
		}
		return new SphereMesh(positions, triangles);
	}
}
