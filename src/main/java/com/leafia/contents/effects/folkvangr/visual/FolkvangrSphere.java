package com.leafia.contents.effects.folkvangr.visual;

import java.io.IOException;
import java.io.InputStream;

import org.joml.Matrix4f;

import com.hbm.lib.RefStrings;
import com.leafia.LeafiaMod;
import com.leafia.client.particle.SphereMesh;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The sphere both F.L.E.I.J.A. cloud renderers draw, and the four ways they draw it.
 *
 * <p>Upstream draws {@code hbm:models/Sphere.obj} - an icosphere of radius 1, 42 vertices and 80 faces,
 * the same geometry as the base mod's {@code sphere.obj} (which differs only in naming a material) - with
 * {@code renderAll()} under a scale. Its texture is {@code solid_e}, a white pixel, or none at all, and
 * its lighting is off, so the only colour is {@code glColor}: here the triangles are written as
 * {@code POSITION_COLOR}, read straight from the file ({@link SphereMesh}), with four render types for the
 * GL states upstream sets. Every type here has no fixed buffer, so switching from one to the next ends the
 * last one's batch and the passes draw in upstream's order (D-834). Depth is tested and written as
 * upstream left it.
 */
@OnlyIn(Dist.CLIENT)
public final class FolkvangrSphere {

	private FolkvangrSphere() {}

	public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(RefStrings.MODID, "models/sphere.obj");

	private static SphereMesh mesh;

	public static SphereMesh mesh() {
		if(mesh == null) {
			try(InputStream in = Minecraft.getInstance().getResourceManager().open(MODEL)) {
				mesh = SphereMesh.parse(in);
			} catch(IOException | RuntimeException e) {
				LeafiaMod.LOGGER.error("Could not read {} for the F.L.E.I.J.A. cloud", MODEL, e);
				mesh = SphereMesh.empty();
			}
		}
		return mesh;
	}

	private static RenderType type(String name, RenderStateShard.TransparencyStateShard transparency, boolean cull) {
		return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 256,
				RenderType.CompositeState.builder()
						.setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
						.setTransparencyState(transparency)
						.setCullState(cull ? RenderStateShard.CULL : RenderStateShard.NO_CULL)
						.createCompositeState(false));
	}

	/** {@code glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)}, cull off. */
	public static final RenderType BLEND = type("leafia_fleija_blend", RenderStateShard.TRANSLUCENT_TRANSPARENCY, false);
	/** {@code glBlendFunc(SRC_ALPHA, ONE)}, cull off. */
	public static final RenderType ADDITIVE = type("leafia_fleija_additive", RenderStateShard.LIGHTNING_TRANSPARENCY, false);
	/** No blending, cull on: the rainbow cloud's core. */
	public static final RenderType OPAQUE_CULLED = type("leafia_fleija_opaque", RenderStateShard.NO_TRANSPARENCY, true);
	/** {@code glBlendFunc(SRC_ALPHA, ONE)}, cull on: the rainbow cloud's shells. */
	public static final RenderType ADDITIVE_CULLED = type("leafia_fleija_additive_culled", RenderStateShard.LIGHTNING_TRANSPARENCY, true);

	/** Writes the sphere at the pose's origin with radius {@code scale} in one colour. */
	public static void draw(VertexConsumer buffer, PoseStack pose, float scale, float r, float g, float b, float a) {
		Matrix4f m = pose.last().pose();
		SphereMesh s = mesh();
		float[] p = s.positions;
		int[] t = s.triangles;
		for(int i = 0; i < t.length; i++) {
			int v = t[i] * 3;
			buffer.addVertex(m, p[v] * scale, p[v + 1] * scale, p[v + 2] * scale).setColor(r, g, b, a);
		}
	}
}
