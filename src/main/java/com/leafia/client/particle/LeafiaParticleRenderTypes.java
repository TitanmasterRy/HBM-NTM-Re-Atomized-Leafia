package com.leafia.client.particle;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The blend states the addon's particles ask for, as 1.21.1 {@link ParticleRenderType}s.
 *
 * <p>1.12.2 particles bound a PNG and chose a blend function inside {@code renderParticle}.
 * 1.21.1 has no such hook — a particle writes vertices and its render type decides everything
 * about how they are drawn — so every distinct texture-and-blend pair upstream used becomes one
 * render type here. The base mod's {@code HbmParticleRenderTypes} has two of them, but both are
 * wrong for this addon's fire: {@code translucent} writes the depth buffer and {@code additive}
 * does not blend by alpha, and upstream's fire does {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} with
 * {@code depthMask(false)}.
 *
 * <p><b>Instances are cached.</b> {@code ParticleEngine} keys its queues by render type and
 * NeoForge's comparator orders the custom ones by identity, so a fresh instance per particle would
 * give every particle a queue, a begin and a draw call of its own. One instance per texture is the
 * rule.
 *
 * <p>What this cannot reproduce: upstream sets {@code alphaFunc(GL_GREATER, 0)} so that even the
 * faintest fragment is drawn, and 1.21.1's particle shader discards any fragment under alpha 0.1.
 * It shows only at the very end of a fade, and on the additive types a near-zero alpha adds almost
 * nothing anyway (D-1075).
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaParticleRenderTypes {

	private LeafiaParticleRenderTypes() {}

	private static final Map<ResourceLocation, ParticleRenderType> ADDITIVE = new HashMap<>();
	private static final Map<ResourceLocation, ParticleRenderType> ALPHA = new HashMap<>();
	private static final Map<ResourceLocation, ParticleRenderType> ALPHA_NO_DEPTH = new HashMap<>();

	/** {@code SRC_ALPHA, ONE}, depth untouched: upstream's glow. Cached per texture. */
	public static ParticleRenderType additive(ResourceLocation texture) {
		return ADDITIVE.computeIfAbsent(texture, k -> new Blend("leafia:additive[" + texture + "]", texture,
				SourceFactor.SRC_ALPHA, DestFactor.ONE));
	}

	/** {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA} without writing depth: upstream's fire. Cached per texture. */
	public static ParticleRenderType alpha(ResourceLocation texture) {
		return ALPHA.computeIfAbsent(texture, k -> new Blend("leafia:alpha[" + texture + "]", texture,
				SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA));
	}

	/**
	 * {@link #alpha} with the depth test off, so the particle is drawn over whatever stands in front of
	 * it: 1.12.2's {@code shouldDisableDepth()}. The test is switched back on by the next addon type to
	 * begin and by {@link LeafiaParticleDepth} when the particle stage ends, because a render type has no
	 * end hook of its own. Cached per texture.
	 */
	public static ParticleRenderType alphaNoDepth(ResourceLocation texture) {
		return ALPHA_NO_DEPTH.computeIfAbsent(texture, k -> new Blend("leafia:alpha_no_depth[" + texture + "]", texture,
				SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, false));
	}

	/**
	 * {@code SRC_COLOR, ONE} on the particle atlas: the arc welder's smoke, whose colour is
	 * premultiplied by its own alpha and so wants the source colour, not its alpha, to scale it.
	 */
	public static final ParticleRenderType ATLAS_SRC_COLOR = new Blend("leafia:atlas_src_color",
			TextureAtlas.LOCATION_PARTICLES, SourceFactor.SRC_COLOR, DestFactor.ONE);

	/** {@code SRC_ALPHA, ONE} on the particle atlas: the light-giving redstone dust. */
	public static final ParticleRenderType ATLAS_ADDITIVE = new Blend("leafia:atlas_additive",
			TextureAtlas.LOCATION_PARTICLES, SourceFactor.SRC_ALPHA, DestFactor.ONE);

	/**
	 * The base mod's beam state as a particle type: untextured quads in {@code POSITION_COLOR_LIGHTMAP},
	 * additive ({@code SRC_ALPHA, ONE}), double-sided, no depth write. A particle that draws a beam hands
	 * {@code LCEBeamPronter} this type's buffer as the one it asked {@code BeamPronter.BEAM} for
	 * ({@link #beamSource}), so the beam looks as it does from a block entity renderer (DFC-4, D-3017).
	 */
	public static final ParticleRenderType BEAM = new ParticleRenderType() {
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getRendertypeLeashShader);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
			RenderSystem.depthMask(false);
			RenderSystem.enableDepthTest();
			RenderSystem.disableCull();
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
		}

		@Override
		public String toString() {
			return "leafia:beam";
		}
	};

	/** {@link #BEAM}'s source: a buffer source that answers every request with the particle's own consumer. */
	public static net.minecraft.client.renderer.MultiBufferSource beamSource(com.mojang.blaze3d.vertex.VertexConsumer consumer) {
		return type -> consumer;
	}

	@OnlyIn(Dist.CLIENT)
	private static final class Blend implements ParticleRenderType {
		private final String name;
		private final ResourceLocation texture;
		private final SourceFactor src;
		private final DestFactor dst;
		private final boolean depthTest;

		Blend(String name, ResourceLocation texture, SourceFactor src, DestFactor dst) {
			this(name, texture, src, dst, true);
		}

		Blend(String name, ResourceLocation texture, SourceFactor src, DestFactor dst, boolean depthTest) {
			this.depthTest = depthTest;
			this.name = name;
			this.texture = texture;
			this.src = src;
			this.dst = dst;
		}

		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, texture);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(src, dst);
			RenderSystem.depthMask(false);
			if(depthTest) RenderSystem.enableDepthTest();
			else RenderSystem.disableDepthTest();
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
