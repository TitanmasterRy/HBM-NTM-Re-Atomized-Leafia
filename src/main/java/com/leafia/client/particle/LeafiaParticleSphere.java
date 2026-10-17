package com.leafia.client.particle;

import java.io.IOException;
import java.io.InputStream;

import com.hbm.lib.RefStrings;
import com.leafia.LeafiaMod;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the base mod's {@code sphere_ruv} as particle vertices.
 *
 * <p>Three upstream particles — {@code ParticleDFC}, {@code ParticleEyeOfHarmony} and
 * {@code ParticleNuke} — are a coloured, additively blended, full-bright sphere. Upstream pushes a
 * matrix, translates, scales and calls {@code sphere_ruv.renderAll()}; here the particle's render
 * type hands over a vertex buffer in the particle format, and this writes the sphere's triangles into
 * it, already scaled and moved, each as a quad with its last corner repeated (the buffer takes only
 * quads).
 *
 * <p>The texture is never sampled for its detail: upstream bound a 1x1 white PNG (or the base
 * mod's white), so every UV is zero and the colour is the vertex colour alone.
 *
 * <p>Culling is left to the render state, as it was upstream — {@code sphere_ruv}'s faces point
 * outward, so a sphere that has grown past the camera is invisible from the inside and {@code
 * ParticleNuke}'s screen flash, not the sphere, is what a player standing in the blast sees.
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaParticleSphere {

	private LeafiaParticleSphere() {}

	/** The base mod's own copy of the model; the addon ships none of its own. */
	public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(RefStrings.MODID, "models/sphere_ruv.obj");

	private static SphereMesh mesh;

	/** Parsed once, on the first frame a sphere is drawn. Empty if the file cannot be read. */
	public static SphereMesh mesh() {
		if(mesh == null) {
			try(InputStream in = Minecraft.getInstance().getResourceManager().open(MODEL)) {
				mesh = SphereMesh.parse(in);
			} catch(IOException | RuntimeException e) {
				LeafiaMod.LOGGER.error("Could not read {} for the addon's sphere particles", MODEL, e);
				mesh = SphereMesh.empty();
			}
		}
		return mesh;
	}

	/**
	 * Writes the sphere centred at {@code (x, y, z)} (camera-relative) with the given scale, which is
	 * its diameter since the model's is 1.
	 */
	public static void emit(VertexConsumer buffer, float x, float y, float z, float scale,
			float r, float g, float b, float a, int light) {
		SphereMesh m = mesh();
		float[] p = m.positions;
		int[] t = m.triangles;
		for(int i = 0; i < t.length; i += 3) {
			vertex(buffer, p, t[i], x, y, z, scale, r, g, b, a, light);
			vertex(buffer, p, t[i + 1], x, y, z, scale, r, g, b, a, light);
			vertex(buffer, p, t[i + 2], x, y, z, scale, r, g, b, a, light);
			vertex(buffer, p, t[i + 2], x, y, z, scale, r, g, b, a, light);
		}
	}

	private static void vertex(VertexConsumer buffer, float[] p, int index, float x, float y, float z, float scale,
			float r, float g, float b, float a, int light) {
		buffer.addVertex(x + p[index * 3] * scale, y + p[index * 3 + 1] * scale, z + p[index * 3 + 2] * scale)
				.setUv(0.0F, 0.0F)
				.setColor(r, g, b, a)
				.setLight(light);
	}
}
