package com.leafia.contents.cannery.actors;

import java.util.OptionalDouble;

import com.hbm.wiaj.JarScript;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The drawing half of {@link ActorVisFrame} and {@link ActorVisText}: what a flux frame and a flux number look like in a jar.
 *
 * <p>Upstream drew both with the fixed pipeline inside {@code ActorVisBase.drawBackgroundComponent}: reset the effects, enable depth,
 * blend with {@code SRC_ALPHA, ONE}, move to the cell, and either build twelve one-pixel lines with a tessellator or draw strings
 * with the font renderer turned to face the viewer. Here the same sequence is a pose and a buffer.
 *
 * <ul>
 * <li><b>The frame</b> is twelve lines on a render type of its own: vanilla's line pipeline, one pixel wide (upstream's default
 * {@code glLineWidth}), depth-tested, with the additive blend {@code SRC_ALPHA, ONE} that upstream set. Vanilla's line shader widens a
 * segment on the screen from a direction carried in the vertex normal, and that direction has to be in the space the positions are in, so
 * each edge's normal is the difference of its two transformed ends and not the pose's normal matrix, which the jar's non-uniform scale
 * (10, -10, 5) would skew.</li>
 * <li><b>The text</b> is upstream's matrix sequence: to the cell's middle, undo the camera's yaw about the up axis and its pitch about
 * the east-west one (so the text stays square to the screen whatever the jar is turned to), scale to {@code scale / 32} of a block a
 * pixel, a half turn about the view axis, and each line centred on its own width and stepped down nine pixels, the block of lines
 * centred on the cell. The glyphs go through the font's own pipeline, which blends them ordinarily where upstream's were additive; for
 * the opaque colours the topics use the two agree away from a glyph's edge (D-3029). Upstream's "set unicode" is a font: the uniform
 * font for {@code 1}, the default one for {@code 0}.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class VisActorRenders {

	/** Vanilla's line pipeline, one pixel wide, blending {@code SRC_ALPHA, ONE}: the GL state upstream's frame was drawn in. */
	private static final RenderType FRAME_LINES = RenderType.create("leafia_cannery_frame", DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES, 1536, false, false,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
					.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(1.0D)))
					.setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
					.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	/** One edge of the cube: both ends, in the pose's space, with the same normal (the direction of the line). */
	private static void edge(VertexConsumer buffer, Matrix4f pose, int r, int g, int b,
			float x0, float y0, float z0, float x1, float y1, float z1) {

		Vector3f from = pose.transformPosition(x0, y0, z0, new Vector3f());
		Vector3f to = pose.transformPosition(x1, y1, z1, new Vector3f());
		Vector3f dir = to.sub(from, new Vector3f());
		if(dir.lengthSquared() == 0F) return;
		dir.normalize();

		buffer.addVertex(from.x, from.y, from.z).setColor(r, g, b, 255).setNormal(dir.x, dir.y, dir.z);
		buffer.addVertex(to.x, to.y, to.z).setColor(r, g, b, 255).setNormal(dir.x, dir.y, dir.z);
	}

	/** Upstream's {@code ActorVisFrame.renderContents}: the twelve edges of the unit cube, scaled about its middle. */
	public static void frame(PoseStack pose, MultiBufferSource buffers, int color, float scale) {

		pose.pushPose();
		float offs = (1 - scale) / 2;
		pose.translate(offs, offs, offs);
		pose.scale(scale, scale, scale);

		int r = color >>> 16 & 0xFF;
		int g = color >>> 8 & 0xFF;
		int b = color & 0xFF;
		Matrix4f m = pose.last().pose();
		VertexConsumer buffer = buffers.getBuffer(FRAME_LINES);

		// upstream's three groups of four: along x, along y, along z
		for(int y = 0; y <= 1; y++) for(int z = 0; z <= 1; z++) edge(buffer, m, r, g, b, 0, y, z, 1, y, z);
		for(int x = 0; x <= 1; x++) for(int z = 0; z <= 1; z++) edge(buffer, m, r, g, b, x, 0, z, x, 1, z);
		for(int x = 0; x <= 1; x++) for(int y = 0; y <= 1; y++) edge(buffer, m, r, g, b, x, y, 0, x, y, 1);

		pose.popPose();
	}

	/** The line of text in the font upstream's {@code setUnicode} chose: {@code -1} leaves it, {@code 1} the uniform font, {@code 0} the default. */
	private static Component styled(String text, int forceUnicode) {
		if(forceUnicode < 0) return Component.literal(text);
		return Component.literal(text).withStyle(Style.EMPTY.withFont(forceUnicode == 1 ? Minecraft.UNIFORM_FONT : Minecraft.DEFAULT_FONT));
	}

	/** Upstream's {@code ActorVisText.renderContents}. */
	public static void text(PoseStack pose, MultiBufferSource buffers, JarScript script, int color, float scale, int forceUnicode, String[] texts) {

		Font font = Minecraft.getInstance().font;

		pose.pushPose();
		pose.translate(0.5D, 0.5D, 0.5D);
		pose.mulPose(Axis.YN.rotationDegrees((float) script.yaw()));
		pose.mulPose(Axis.XN.rotationDegrees((float) script.pitch()));
		float pixel = (float) (1 / 16D / 2 * scale);
		pose.scale(pixel, pixel, pixel);
		pose.mulPose(Axis.ZP.rotationDegrees(180F));

		int lineHeight = 9;
		for(int i = 0; i < texts.length; i++) {
			Component line = styled(texts[i], forceUnicode);
			double offset = (i - (texts.length - 1) / 2D) * lineHeight;

			pose.pushPose();
			pose.translate(-font.width(line) / 2D, -7 / 2D + offset, 0D);
			font.drawInBatch(line, 0F, 0F, 0xFF000000 | color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}

		pose.popPose();
	}
}
