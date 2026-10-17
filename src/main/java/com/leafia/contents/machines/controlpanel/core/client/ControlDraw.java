package com.leafia.contents.machines.controlpanel.core.client;

import java.util.List;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * What a control's renderer draws with: the pose, the buffers, the light, and the handful of primitives NTM-CE's immediate
 * mode calls turn into (D-3065).
 *
 * <ul>
 * <li>{@link #part} is {@code model.renderPart("name")} with {@code GlStateManager.color(r, g, b)} before it. CE's colour was
 * a multiplier of the texture, which is what the vertex tint here is; a multiplier above 1 (CE lit a lamp at 1.5 or 3) is
 * clamped, because a byte colour cannot exceed 255 and an overflow would wrap to black.</li>
 * <li>{@code OpenGlHelper.setLightmapTextureCoords(.., 240, 240)} is {@link #part}'s {@code fullBright}: the lightmap value
 * {@link LightTexture#FULL_BRIGHT}.</li>
 * <li>{@code glBlendFunc} is the {@link Layer}: opaque and alpha-tested, alpha-blended, or additive.</li>
 * <li>{@code font.drawString} under {@code rotate(90, 1, 0, 0)} is {@link #text}: the pose the caller has built lays the
 * font's plane on the panel, and polygon offset keeps it from z-fighting the slab.</li>
 * </ul>
 *
 * <p>A renderer draws at the control's own origin: the panel loop has already moved the pose to {@code (posX, 0, posY)}
 * (and applied the offset CE gave a control larger than one unit), where CE's per-part {@code translate(posX, y, posY)}
 * did. The pose is in <b>panel units</b> (the panel transform scales a unit to a tenth of a block), with +y out of the panel.
 */
@OnlyIn(Dist.CLIENT)
public final class ControlDraw {

	/** CE's three blend states. */
	public enum Layer {
		/** Opaque, with alpha cut-out: the default. */
		SOLID,
		/** {@code glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)}: the encased button's cover. */
		TRANSLUCENT,
		/** {@code glBlendFunc(SRC_ALPHA, ONE)}: the RGB lamp's glow. */
		GLOW,
		/** The nixie tube's solid parts, drawn at once and in order, ahead of the slab ({@link ControlPanelRenderTypes#INSET}). */
		INSET,
		/** The nixie tube's alpha-blended glass, at once and in order ({@link ControlPanelRenderTypes#GLASS}). */
		GLASS,
		/** The nixie's hole in the slab: writes depth and no colour ({@link ControlPanelRenderTypes#MASK}). */
		MASK
	}

	public final PoseStack pose;
	public final MultiBufferSource buffers;
	public final int light;
	public final Font font;

	public ControlDraw(PoseStack pose, MultiBufferSource buffers, int light) {
		this.pose = pose;
		this.buffers = buffers;
		this.light = light;
		this.font = Minecraft.getInstance().font;
	}

	// ------------------------------------------------------------------ parts

	/** One baked part, untinted, at the block's own light. */
	public void part(String model, String part) {
		part(Layer.SOLID, model, part, 1, 1, 1, false);
	}

	/** One baked part tinted by a colour multiplier. */
	public void part(String model, String part, float r, float g, float b) {
		part(Layer.SOLID, model, part, r, g, b, false);
	}

	/** One baked part tinted, emissive when {@code fullBright}. */
	public void part(String model, String part, float r, float g, float b, boolean fullBright) {
		part(Layer.SOLID, model, part, r, g, b, fullBright);
	}

	public void part(Layer layer, String model, String part, float r, float g, float b, boolean fullBright) {
		part(layer, model, part, r, g, b, 1F, fullBright);
	}

	/**
	 * One baked part with an alpha: {@code glColor4f(r, g, b, a)}, which a blended or additive {@link Layer} multiplies into the
	 * texture's own alpha (the text indicator's swelling glow, the nixie's glass). {@link ObjUtil#renderModel} writes alpha 1, so the
	 * quads are copied here.
	 */
	public void part(Layer layer, String model, String part, float r, float g, float b, float a, boolean fullBright) {
		if(layer == Layer.GLOW && buffers instanceof MultiBufferSource.BufferSource source)
			source.endBatch(Sheets.cutoutBlockSheet()); // the glow is drawn the moment it is asked for; what it glows over has to be on the screen first
		VertexConsumer consumer = buffers.getBuffer(typeOf(layer));
		BakedModel baked = ObjUtil.getModel(ControlPanelModels.part(model, part));
		int lightmap = fullBright ? LightTexture.FULL_BRIGHT : light;
		var last = pose.last();
		for(var quad : baked.getQuads(null, null, RANDOM))
			consumer.putBulkData(last, quad, clamp(r), clamp(g), clamp(b), clamp(a), lightmap, OverlayTexture.NO_OVERLAY);
		for(Direction side : Direction.values())
			for(var quad : baked.getQuads(null, side, RANDOM))
				consumer.putBulkData(last, quad, clamp(r), clamp(g), clamp(b), clamp(a), lightmap, OverlayTexture.NO_OVERLAY);
	}

	private static final RandomSource RANDOM = RandomSource.create(42);

	private static RenderType typeOf(Layer layer) {
		return switch(layer) {
			case SOLID -> Sheets.cutoutBlockSheet();
			case TRANSLUCENT -> Sheets.translucentCullBlockSheet();
			case GLOW -> ControlPanelRenderTypes.GLOW;
			case INSET -> ControlPanelRenderTypes.INSET;
			case GLASS -> ControlPanelRenderTypes.GLASS;
			case MASK -> ControlPanelRenderTypes.MASK;
		};
	}

	/**
	 * Draws what has been asked of a layer's type now. The three nixie types are not in the buffer source's fixed set and are drawn
	 * when another type is asked for; the nixie calls this after its last request so a tube that is the last thing in a frame is not
	 * left to the end of the pass, which would put it after the slab it has to make a hole in.
	 */
	public void flush(Layer layer) {
		if(buffers instanceof MultiBufferSource.BufferSource source)
			source.endBatch(typeOf(layer));
	}

	/** {@code r, g, b} from a colour's 0..1 components, brightened by CE's multiplier. */
	public void part(String model, String part, float[] rgb, float multiplier, boolean fullBright) {
		part(Layer.SOLID, model, part, rgb[0] * multiplier, rgb[1] * multiplier, rgb[2] * multiplier, fullBright);
	}

	static float clamp(float v) {
		return v < 0 ? 0 : Math.min(v, 1F);
	}

	// ------------------------------------------------------------------ text

	/**
	 * Draws a string with its top left at the pose's origin, the font's y running down the plane. {@code full} is CE's
	 * {@code setLightmapTextureCoords(240, 240)} around the call.
	 */
	public void text(String text, float x, float y, int rgb, boolean full) {
		font.drawInBatch(text, x, y, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
				full ? LightTexture.FULL_BRIGHT : light);
	}

	/** {@code font.drawSplitString}: the text wrapped to {@code wrap} pixels, one line below another. */
	public void wrapped(String text, float x, float y, int wrap, int rgb) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), wrap);
		for(FormattedCharSequence line : lines) {
			font.drawInBatch(line, x, y, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
			y += font.lineHeight;
		}
	}

	/** Lays the font's plane on the panel: CE's {@code scale(s), rotate(90, 1, 0, 0)}, and its {@code (s, -s, s)} for a label. */
	public void flatText(float scale, boolean flipY) {
		pose.scale(scale, flipY ? -scale : scale, scale);
		pose.mulPose(Axis.XP.rotationDegrees(90));
	}

	// ------------------------------------------------------------------ plain quads

	private static TextureAtlasSprite white;

	/** The white 16x16 the generator writes, on the block atlas. */
	public static TextureAtlasSprite white() {
		if(white == null)
			white = sprite("leafia:block/ccp/white");
		return white;
	}

	/** Forgets cached sprites; called when resources reload. */
	static void reset() {
		white = null;
	}

	public static TextureAtlasSprite sprite(String location) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(location));
	}

	/**
	 * A flat coloured quad in the pose's x-z plane at height {@code y}, facing up, its corners given as the rectangle
	 * {@code (x0, z0)} to {@code (x1, z1)}. CE's untextured {@code Tessellator} quad behind a text display.
	 */
	public void flatQuad(float x0, float z0, float x1, float z1, float y, float r, float g, float b) {
		VertexConsumer c = buffers.getBuffer(Sheets.cutoutBlockSheet());
		TextureAtlasSprite s = white();
		float u = (s.getU0() + s.getU1()) / 2, v = (s.getV0() + s.getV1()) / 2;
		var last = pose.last();
		c.addVertex(last, x0, y, z0).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x0, y, z1).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x1, y, z1).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x1, y, z0).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
	}

	/**
	 * A flat quad in the pose's x-z plane at height {@code y}, facing up, filled with one point of a block-atlas sprite: the point
	 * {@code (uFrac, vFrac)} of the sprite, 0 to 1 from its top left. CE drew the RBMK display's background by letting a 2 x 2 texture
	 * repeat; the atlas does not repeat, so each cell takes one pixel's centre (CCP-INSTRUMENTS-2, D-3072).
	 */
	public void flatSpriteQuad(TextureAtlasSprite s, float x0, float z0, float x1, float z1, float y, float uFrac, float vFrac, float r, float g, float b) {
		VertexConsumer c = buffers.getBuffer(Sheets.cutoutBlockSheet());
		float u = s.getU(uFrac), v = s.getV(vFrac);
		var last = pose.last();
		c.addVertex(last, x0, y, z0).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x0, y, z1).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x1, y, z1).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
		c.addVertex(last, x1, y, z0).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 1, 0);
	}

	/**
	 * A flat coloured convex polygon in the pose's x-z plane at height {@code y}, facing up: the first {@code n} points of
	 * {@code xs}/{@code zs}, in either winding (it is turned to face up, the way {@link #flatQuad} does). Emissive when
	 * {@code fullBright}. CE's {@code Tessellator} quads behind a chart's plot and under a box label (CCP-INSTRUMENTS-1, D-3071).
	 * A fan of quads with a repeated last corner, since the block render types draw quads.
	 */
	public void flatPolygon(float[] xs, float[] zs, int n, float y, float r, float g, float b, boolean fullBright) {
		if(n < 3)
			return;
		double area = 0;
		for(int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			area += (double) xs[i] * zs[j] - (double) xs[j] * zs[i];
		}
		if(area == 0)
			return;
		boolean reverse = area > 0;
		VertexConsumer c = buffers.getBuffer(Sheets.cutoutBlockSheet());
		TextureAtlasSprite s = white();
		float u = (s.getU0() + s.getU1()) / 2, v = (s.getV0() + s.getV1()) / 2;
		int lightmap = fullBright ? LightTexture.FULL_BRIGHT : light;
		var last = pose.last();
		for(int k = 1; k < n - 1; k++) {
			int p1 = reverse ? k + 1 : k;
			int p2 = reverse ? k : k + 1;
			c.addVertex(last, xs[0], y, zs[0]).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightmap).setNormal(last, 0, 1, 0);
			c.addVertex(last, xs[p1], y, zs[p1]).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightmap).setNormal(last, 0, 1, 0);
			c.addVertex(last, xs[p2], y, zs[p2]).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightmap).setNormal(last, 0, 1, 0);
			c.addVertex(last, xs[p2], y, zs[p2]).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightmap).setNormal(last, 0, 1, 0);
		}
	}
}
