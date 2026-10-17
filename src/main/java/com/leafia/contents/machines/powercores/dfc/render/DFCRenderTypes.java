package com.leafia.contents.machines.powercores.dfc.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The GL states the DFC's renderers set, as render types.
 *
 * <p>Upstream flips {@code GlStateManager} switches around each {@code renderPart}: lighting, culling, the
 * blend function, the depth mask and the alpha test. 1.21.1 has one thing for all of that, so each
 * distinct combination upstream used is one type here, cached by texture so a frame does not make a fresh
 * one per part (the buffer source keys its batches by identity).
 *
 * <ul>
 * <li>{@link #body} - {@code entityCutoutNoCull}: lit, double-sided, alpha-tested. The frames, arms and
 *     cores; upstream's {@code disableCull} at the top of the renderer.</li>
 * <li>{@link #glass} - {@code entityTranslucent}: the stabilizer's glass, {@code SRC_ALPHA,
 *     ONE_MINUS_SRC_ALPHA} with the alpha test off ({@code glAlphaFunc(GL_ALWAYS)}).</li>
 * <li>{@link #unlit} - textured, tinted by the vertex colour, lighting off and full-bright: the neon, drawn
 *     on the one-pixel white texture.</li>
 * <li>{@link #additive} - the same, {@code SRC_ALPHA, ONE}, no depth write: the pulser's energy rings and
 *     arm lasers, the flare.</li>
 * <li>{@link #lens} - {@code ONE, SRC_COLOR}: the stabilizer's lens, which multiplies the glass behind it
 *     by its own colour.</li>
 * <li>{@link #colored} - untextured, vertex colour only, in a chosen blend: the core's spheres.</li>
 * <li>{@link #noise} - {@code POSITION_TEX_COLOR}, additive ({@code SRC_ALPHA, ONE}, the state the orb leaves
 *     behind it), no depth write: the wormhole's draconic streams.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class DFCRenderTypes {

	private DFCRenderTypes() {}

	/** {@code glBlendFunc(ONE, SRC_COLOR)}. */
	public static final RenderStateShard.TransparencyStateShard ONE_SRC_COLOR = new RenderStateShard.TransparencyStateShard(
			"leafia_dfc_one_src_color",
			() -> {
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.SRC_COLOR);
			},
			() -> {
				RenderSystem.disableBlend();
				RenderSystem.defaultBlendFunc();
			});

	/** {@code glBlendFunc(ONE, ONE_MINUS_SRC_ALPHA)}: the singularity core's orbiting beads. */
	public static final RenderStateShard.TransparencyStateShard ONE_ONE_MINUS_ALPHA = new RenderStateShard.TransparencyStateShard(
			"leafia_dfc_one_one_minus_alpha",
			() -> {
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
			},
			() -> {
				RenderSystem.disableBlend();
				RenderSystem.defaultBlendFunc();
			});

	/** Vanilla's {@code position_tex_color} shader, which {@code RenderStateShard} has no constant for. */
	private static final RenderStateShard.ShaderStateShard POSITION_TEX_COLOR = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

	private static final Map<String, RenderType> CACHE = new HashMap<>();

	public static RenderType body(ResourceLocation texture) {
		return RenderType.entityCutoutNoCull(texture);
	}

	public static RenderType glass(ResourceLocation texture) {
		return RenderType.entityTranslucent(texture);
	}

	public static RenderType unlit(ResourceLocation texture) {
		return textured("unlit", texture, RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.COLOR_DEPTH_WRITE);
	}

	public static RenderType additive(ResourceLocation texture) {
		return textured("additive", texture, RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.COLOR_WRITE);
	}

	public static RenderType lens(ResourceLocation atlas) {
		return textured("lens", atlas, ONE_SRC_COLOR, RenderStateShard.COLOR_DEPTH_WRITE);
	}

	private static RenderType textured(String kind, ResourceLocation texture, RenderStateShard.TransparencyStateShard transparency,
			RenderStateShard.WriteMaskStateShard write) {
		return CACHE.computeIfAbsent(kind + ":" + texture, k -> RenderType.create("leafia_dfc_" + kind,
				DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536,
				RenderType.CompositeState.builder()
						.setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
						.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
						.setTransparencyState(transparency)
						.setCullState(RenderStateShard.NO_CULL)
						.setLightmapState(RenderStateShard.LIGHTMAP)
						.setWriteMaskState(write)
						.createCompositeState(false)));
	}

	/**
	 * Untextured quads or triangles in the vertex colour.
	 *
	 * @param triangles the flash's fans are triangles; every other mesh is quads (a triangle is a quad with
	 *                  its last corner repeated)
	 */
	public static RenderType colored(String name, RenderStateShard.TransparencyStateShard transparency, boolean cull, boolean depthWrite,
			boolean triangles) {
		return CACHE.computeIfAbsent("colored:" + name, k -> RenderType.create("leafia_dfc_" + name,
				DefaultVertexFormat.POSITION_COLOR, triangles ? VertexFormat.Mode.TRIANGLES : VertexFormat.Mode.QUADS, 1536,
				RenderType.CompositeState.builder()
						.setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
						.setTransparencyState(transparency)
						.setCullState(cull ? RenderStateShard.CULL : RenderStateShard.NO_CULL)
						.setWriteMaskState(depthWrite ? RenderStateShard.COLOR_DEPTH_WRITE : RenderStateShard.COLOR_WRITE)
						.createCompositeState(false)));
	}

	public static RenderType noise(ResourceLocation texture) {
		return CACHE.computeIfAbsent("noise:" + texture, k -> RenderType.create("leafia_dfc_noise",
				DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536,
				RenderType.CompositeState.builder()
						.setShaderState(POSITION_TEX_COLOR)
						.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
						.setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
						.setCullState(RenderStateShard.NO_CULL)
						.setWriteMaskState(RenderStateShard.COLOR_WRITE)
						.createCompositeState(false)));
	}
}
