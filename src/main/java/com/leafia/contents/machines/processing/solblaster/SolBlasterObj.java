package com.leafia.contents.machines.processing.solblaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Neutron Blaster's OBJ, read by hand: one mesh per named object, with the texture
 * coordinates exactly as the file has them.
 *
 * <p>Not an upstream file. Upstream draws the machine through its own {@code WaveFrontObjectVAO},
 * which keeps every texture coordinate as written and lets the GPU wrap it. That matters here:
 * the containment's UVs run from -5 to +5 (brick concrete tiled across it), the stain's to 3, and
 * the hemisphere, tower and covers all sit on the single point (0, 0) of a one-pixel white
 * texture. NeoForge's OBJ loader bakes quads against a sprite in the block atlas, which has no
 * wrap - a UV outside 0..1 reads the next sprite over, and (0, 0) on the corner of a 1x1 sprite
 * bleeds its neighbours, which is what the first screenshot showed: a dotted tower and a red dome.
 * So the machine is drawn from its own geometry on standalone textures, which repeat.
 *
 * <p>No client class is named here, so the parse is tested on a server.
 *
 * <p>Texture {@code v} is flipped ({@code 1 - v}), as {@code flip_v} does for every other OBJ in
 * this jar: Blender writes it from the bottom. Faces are triangles in this file and any larger
 * polygon is fanned; each is stored as a quad with its last vertex repeated, which is what a
 * vanilla entity render type draws.
 */
public final class SolBlasterObj {

	private SolBlasterObj() {}

	public static final String RESOURCE = "/assets/leafia/obj/sol_blaster.obj";

	/** One object's vertices: eight floats each (x, y, z, u, v, nx, ny, nz), four to a face. */
	public record Mesh(float[] data, int faces) {}

	private static Map<String, Mesh> meshes;

	/** The meshes by object name, parsed the first time they are asked for. */
	public static synchronized Map<String, Mesh> meshes() {
		if(meshes == null) {
			try(InputStream in = SolBlasterObj.class.getResourceAsStream(RESOURCE)) {
				if(in == null) throw new IllegalStateException("missing " + RESOURCE);
				meshes = parse(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + RESOURCE, e);
			}
		}
		return meshes;
	}

	public static Map<String, Mesh> parse(InputStream in) throws IOException {

		List<float[]> v = new ArrayList<>();
		List<float[]> vt = new ArrayList<>();
		List<float[]> vn = new ArrayList<>();
		Map<String, List<Float>> out = new LinkedHashMap<>();
		Map<String, Integer> faces = new LinkedHashMap<>();
		List<Float> current = null;
		String name = null;

		try(BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while((line = reader.readLine()) != null) {
				String[] t = line.trim().split("\\s+");
				if(t.length == 0) continue;
				switch(t[0]) {
					case "v" -> v.add(new float[] { Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3]) });
					case "vt" -> vt.add(new float[] { Float.parseFloat(t[1]), Float.parseFloat(t[2]) });
					case "vn" -> vn.add(new float[] { Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3]) });
					case "o", "g" -> {
						name = line.trim().substring(1).trim();
						current = out.computeIfAbsent(name, k -> new ArrayList<>());
						faces.putIfAbsent(name, 0);
					}
					case "f" -> {
						if(current == null) throw new IOException("a face before any object");
						int corners = t.length - 1;
						// A fan: (0, i, i + 1), each emitted as a quad with the last corner repeated.
						for(int i = 1; i < corners - 1; i++) {
							int[] ix = { 1, i + 1, i + 2, i + 2 };
							for(int k : ix) {
								String[] p = t[k].split("/");
								float[] pos = v.get(Integer.parseInt(p[0]) - 1);
								float[] uv = p.length > 1 && !p[1].isEmpty() ? vt.get(Integer.parseInt(p[1]) - 1) : new float[] { 0F, 0F };
								float[] n = p.length > 2 ? vn.get(Integer.parseInt(p[2]) - 1) : new float[] { 0F, 1F, 0F };
								current.add(pos[0]); current.add(pos[1]); current.add(pos[2]);
								current.add(uv[0]); current.add(1F - uv[1]);
								current.add(n[0]); current.add(n[1]); current.add(n[2]);
							}
							faces.merge(name, 1, Integer::sum);
						}
					}
					default -> { }
				}
			}
		}

		Map<String, Mesh> result = new LinkedHashMap<>();
		out.forEach((key, list) -> {
			float[] data = new float[list.size()];
			for(int i = 0; i < data.length; i++) data[i] = list.get(i);
			result.put(key, new Mesh(data, faces.get(key)));
		});
		return result;
	}
}
