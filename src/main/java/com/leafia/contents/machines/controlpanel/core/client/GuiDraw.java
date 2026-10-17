package com.leafia.contents.machines.controlpanel.core.client;

import org.joml.Matrix4f;

import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The few drawing primitives the editor needs and {@link GuiGraphics} lacks (CCP-EDITOR, D-3066): rectangles and lines at
 * <b>float</b> coordinates, because the placement grid is drawn under a scale of about ten where one control is a unit or two
 * wide, and a texture quad with texture coordinates past 1, which tiles the grid texture. CE's {@code NTMRenderHelper.drawGuiRect}
 * and {@code drawGuiRectColor} are these two.
 */
@OnlyIn(Dist.CLIENT)
public final class GuiDraw {

	private GuiDraw() {}

	public static final String TEX = "textures/gui/control_panel/";

	public static ResourceLocation texture(String name) {
		return LeafiaRef.rl(TEX + name + ".png");
	}

	/** Packs 0..1 colour components into ARGB. */
	public static int argb(float r, float g, float b, float a) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	private static int clamp255(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255F)));
	}

	/** A filled rectangle in the current pose's space. */
	public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
		Matrix4f m = g.pose().last().pose();
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		vc.addVertex(m, x0, y0, 0).setColor(argb);
		vc.addVertex(m, x0, y1, 0).setColor(argb);
		vc.addVertex(m, x1, y1, 0).setColor(argb);
		vc.addVertex(m, x1, y0, 0).setColor(argb);
		g.flush();
	}

	/** An unfilled rectangle, {@code t} thick, drawn inside the given bounds. */
	public static void outline(GuiGraphics g, float x0, float y0, float x1, float y1, float t, int argb) {
		rect(g, x0, y0, x1, y0 + t, argb);
		rect(g, x0, y1 - t, x1, y1, argb);
		rect(g, x0, y0 + t, x0 + t, y1 - t, argb);
		rect(g, x1 - t, y0 + t, x1, y1 - t, argb);
	}

	/** A line of the given thickness, as a rectangle turned to run from one point to the other. */
	public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float thickness, int argb) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if(len <= 0)
			return;
		g.pose().pushPose();
		g.pose().translate(x0, y0, 0);
		g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
		rect(g, 0, -thickness / 2F, len, thickness / 2F, argb);
		g.pose().popPose();
	}

	/**
	 * A textured quad whose texture coordinates may run past 1, which the default wrap mode tiles. {@code uMax}/{@code vMax} are in
	 * texture widths: 2.5 draws two and a half copies of the texture across {@code w}.
	 */
	public static void tiled(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float uMax, float vMax) {
		g.flush();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, tex);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		Matrix4f m = g.pose().last().pose();
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		bb.addVertex(m, x, y + h, 0).setUv(0, vMax);
		bb.addVertex(m, x + w, y + h, 0).setUv(uMax, vMax);
		bb.addVertex(m, x + w, y, 0).setUv(uMax, 0);
		bb.addVertex(m, x, y, 0).setUv(0, 0);
		BufferUploader.drawWithShader(bb.buildOrThrow());
	}

	/**
	 * CE's {@code drawGuiRectColor}: a tinted quad of a texture at float coordinates, its texture coordinates as fractions of the
	 * whole texture (they may run past 1 to tile). {@code (u, v)} is the top left and {@code (uMax, vMax)} the bottom right.
	 * The node editor's sprite sheet is drawn with this, a piece at a time (CCP-NODE-EDITOR, D-3067).
	 */
	public static void texQuad(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float u, float v, float uMax, float vMax, int argb) {
		g.flush();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, tex);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		Matrix4f m = g.pose().last().pose();
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		bb.addVertex(m, x, y + h, 0).setUv(u, vMax).setColor(argb);
		bb.addVertex(m, x + w, y + h, 0).setUv(uMax, vMax).setColor(argb);
		bb.addVertex(m, x + w, y, 0).setUv(uMax, v).setColor(argb);
		bb.addVertex(m, x, y, 0).setUv(u, v).setColor(argb);
		BufferUploader.drawWithShader(bb.buildOrThrow());
	}

	/** Text at a scale about its own anchor, as the node editor draws every label (CE: translate, scale, translate back). */
	public static void scaledText(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float anchorX, float anchorY, float scale, float x, float y, int argb) {
		g.pose().pushPose();
		g.pose().translate(anchorX, anchorY, 0);
		g.pose().scale(scale, scale, 1);
		g.pose().translate(-anchorX, -anchorY, 0);
		g.drawString(font, text, x, y, argb, false);
		g.flush();
		g.pose().popPose();
	}

	/** A widget shown or hidden together with being enabled, so a hidden one neither draws nor takes a click. */
	public static void show(AbstractWidget w, boolean on) {
		w.visible = on;
		w.active = on;
		if(!on && w.isFocused())
			w.setFocused(false);
	}

	/** Width of the text at a scale, for centring. */
	public static int centredX(net.minecraft.client.gui.Font font, String text, int centre) {
		return centre - font.width(text) / 2;
	}
}
