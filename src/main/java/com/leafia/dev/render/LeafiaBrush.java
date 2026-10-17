package com.leafia.dev.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Upstream's "for making porting easy" vertex helper, on 1.21.1's pipeline.
 *
 * <p>Upstream: {@code com/leafia/dev/render/LeafiaBrush.java}, 44 lines, whose own comment is
 * <em>"For making porting easy"</em> — it is the shim ntmleafia wrote to make 1.7.10 renderer
 * code compile on 1.12.2, so this is a shim being re-shimmed one version further on.
 *
 * <h2>This file was ruled out once, and that was wrong</h2>
 *
 * D-1011 deferred it: <em>"15 consumers, but an adapter over immediate-mode {@code Tessellator}
 * /{@code GL11}. 1.21.1 draws through {@code RenderType} and {@code MultiBufferSource}."</em>
 * Every word of that is true about the <b>mechanism</b>, and the conclusion drawn from it — that
 * the file does not port — does not follow. It is the sole blocker on five elevator renderers,
 * and what its consumers actually call is fifty-odd {@code addVertexWithUV(x, y, z, u, v)} lines
 * that transcribe one-for-one onto a {@link VertexConsumer}. Two of its eight methods have no
 * successor; six do. Deferring the file deferred the six for the sake of the two.
 *
 * <p>The lesson is D-1035's, again: <b>a deferral written from a file's imports is a guess until
 * somebody reads the file against its callers.</b> Here the imports say `Tessellator` and `GL11`
 * and the callers say "emit a quad".
 *
 * <h2>What changed, and why the vertex calls did not</h2>
 *
 * Upstream binds a texture with {@code bindTexture(...)}, calls {@code startDrawingQuads()},
 * emits vertices and calls {@code draw()}. 1.21.1 has no bound-texture state: the texture
 * <em>selects the buffer</em>, through {@code RenderType}. So those three calls collapse into
 * {@link #begin}, and {@link #end} exists only to make the paired shape survive in the ported
 * code — {@link MultiBufferSource} batches, so there is nothing to flush.
 *
 * <p><b>The vertex methods keep upstream's names and argument order exactly</b>, which is the
 * whole point: the long runs of {@code addVertexWithUV} in the elevator renderers are checkable
 * against the original line by line, and those runs are most of what there is to get wrong.
 *
 * <h2>The pieces 1.21.1 needs and 1.12.2 did not</h2>
 *
 * A {@code VertexConsumer} wants colour, overlay, light and a normal on every vertex where
 * {@code Tessellator} wanted none. They come from {@link #begin} and stay fixed for the run,
 * which is what the immediate-mode GL state they replace did:
 *
 * <ul>
 * <li><b>Colour</b> is upstream's {@code LeafiaGls.color(...)} — the vertex tint, multiplied with
 *     the texel. Upstream's {@code disableTexture2D(); color(0, 0, 0)} idiom for drawing
 *     something flat black has no direct successor, and needs none: black times any texel is
 *     black.</li>
 * <li><b>Light</b> is the packed light the renderer was handed. Where upstream calls
 *     {@code disableLighting()} to make something glow, pass {@link #FULLBRIGHT}.</li>
 * <li><b>The normal</b> decides diffuse shading. It is per-{@code begin} rather than per-vertex
 *     because every caller in the addon emits one flat run of quads facing one way.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaBrush {

	/** What {@code LeafiaGls.disableLighting()} means here: block light 15, sky light 15. */
	public static final int FULLBRIGHT = 0xF000F0;

	private VertexConsumer buf;
	private PoseStack.Pose pose;
	private int light;
	private float r = 1, g = 1, b = 1, a = 1;
	private float nx, ny = 1, nz;

	/**
	 * Upstream's {@code bindTexture(tex)} plus {@code startDrawingQuads()}.
	 *
	 * <p>{@link RenderType#entityCutout} rather than {@code RenderType.cutout()}, because these
	 * are standalone textures rather than atlas sprites — the same call the OBJ block renderers
	 * avoid, since a baked model's quads already carry atlas coordinates and these do not.
	 */
	public LeafiaBrush begin(MultiBufferSource buffers, PoseStack matrix, ResourceLocation texture, int light) {
		this.buf = buffers.getBuffer(RenderType.entityCutout(texture));
		this.pose = matrix.last();
		this.light = light;
		return this;
	}

	/**
	 * The same, for a run that needs a {@link RenderType} this class would not have picked.
	 *
	 * <p>{@link #begin(MultiBufferSource, PoseStack, ResourceLocation, int)} answers the common
	 * case — a standalone texture, alpha-tested, lit — and hard-codes it. The fuzzy identifier's
	 * overlay needs <em>additive</em> blending for its fluid patches and no texture at all for
	 * its hazard pips, neither of which that call can express, so the caller hands the type in.
	 *
	 * <p><b>An untextured type is fine here.</b> A {@code VertexConsumer} element setter whose
	 * element the format does not have is a no-op rather than an error — {@code beginElement}
	 * answers -1 and the write is skipped — so {@link #addVertexColorWithUV} writing a UV, an
	 * overlay and a normal into a {@code POSITION_COLOR_LIGHTMAP} buffer costs three no-ops and
	 * fills every element that format does require. That is what lets one pair of vertex methods
	 * serve both.
	 */
	public LeafiaBrush begin(MultiBufferSource buffers, PoseStack matrix, RenderType type, int light) {
		this.buf = buffers.getBuffer(type);
		this.pose = matrix.last();
		this.light = light;
		return this;
	}

	/** The vertex tint, upstream's {@code LeafiaGls.color(r, g, b)}. Resets on {@link #end}. */
	public LeafiaBrush color(float r, float g, float b, float a) {
		this.r = r; this.g = g; this.b = b; this.a = a;
		return this;
	}

	/** Which way this run of quads faces, for diffuse shading. Resets on {@link #end}. */
	public LeafiaBrush normal(float x, float y, float z) {
		this.nx = x; this.ny = y; this.nz = z;
		return this;
	}

	public void addVertexWithUV(double x, double y, double z, double u, double v) {
		buf.addVertex(pose, (float) x, (float) y, (float) z)
				.setColor(r, g, b, a)
				.setUv((float) u, (float) v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/**
	 * A vertex carrying its own colour, upstream's
	 * {@code LCERenderHelper.addVertexColorWithUV(x, y, z, u, v, r, g, b, a)}.
	 *
	 * <p>{@link #addVertexWithUV} takes the colour from {@link #color} and holds it for the whole
	 * run, which is what {@code LeafiaGls.color} did. This is the other upstream helper, where
	 * the colour is per-vertex — the fuzzy identifier's four fluid patches each flicker on their
	 * own timer, so the alpha changes within one run. The name and argument order are upstream's,
	 * so its long vertex runs stay checkable line by line.
	 */
	public void addVertexColorWithUV(double x, double y, double z, double u, double v,
			float r, float g, float b, float a) {
		buf.addVertex(pose, (float) x, (float) y, (float) z)
				.setColor(r, g, b, a)
				.setUv((float) u, (float) v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/**
	 * The same without a texture, upstream's {@code LCERenderHelper.addVertexColor}, which it
	 * uses after {@code disableTexture2D()} to draw something in flat colour.
	 *
	 * <p>The UV written is (0, 0) and is discarded by an untextured format — see
	 * {@link #begin(MultiBufferSource, PoseStack, RenderType, int)}. Do not call this on a
	 * textured run expecting the texture to be ignored; there it samples the sprite's corner.
	 */
	public void addVertexColor(double x, double y, double z, float r, float g, float b, float a) {
		addVertexColorWithUV(x, y, z, 0, 0, r, g, b, a);
	}

	/**
	 * Upstream's {@code draw()}.
	 *
	 * <p>Nothing is flushed: {@link MultiBufferSource} batches by render type and ends the batch
	 * itself. This drops the buffer so a stray {@code addVertexWithUV} after it fails loudly
	 * rather than writing into whatever the next caller began, and resets the colour and normal
	 * so one run cannot inherit the last one's — which is exactly the leak immediate-mode GL
	 * state used to have, and upstream guards by calling {@code color(1, 1, 1)} afterwards.
	 */
	public void end() {
		buf = null;
		pose = null;
		r = g = b = a = 1;
		nx = nz = 0;
		ny = 1;
	}
}
