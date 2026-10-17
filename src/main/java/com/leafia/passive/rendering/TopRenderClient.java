package com.leafia.passive.rendering;

import java.util.OptionalDouble;

import com.leafia.lib.LeafiaRef;
import com.leafia.passive.rendering.TopRender.Highlight;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The drawing half of {@link TopRender} (D-3026): lines through everything, additive, and the labels. See there for what is drawn.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT)
public final class TopRenderClient {

	private TopRenderClient() {}

	/** Line render types need a few protected shards, so this is a subclass for the access. */
	private static final class Types extends RenderType {
		private Types() { super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {}); }

		/** {@code glBlendFunc(SRC_ALPHA, ONE)}, depth test off, no depth write: lines that read through walls. */
		static final RenderType LINES = RenderType.create("leafia_top_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
				VertexFormat.Mode.LINES, 1536, false, false,
				RenderType.CompositeState.builder()
						.setShaderState(RENDERTYPE_LINES_SHADER)
						.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
						.setLayeringState(NO_LAYERING)
						.setTransparencyState(LIGHTNING_TRANSPARENCY)
						.setDepthTestState(NO_DEPTH_TEST)
						.setWriteMaskState(COLOR_WRITE)
						.setCullState(NO_CULL)
						.createCompositeState(false));
	}

	private static void line(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, int ca, Vec3 b, int cb) {
		float nx = (float) (b.x - a.x), ny = (float) (b.y - a.y), nz = (float) (b.z - a.z);
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if(len < 1e-6F) { nx = 0; ny = 1; nz = 0; } else { nx /= len; ny /= len; nz /= len; }
		vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(0xFF000000 | ca).setNormal(pose, nx, ny, nz);
		vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(0xFF000000 | cb).setNormal(pose, nx, ny, nz);
	}

	/** Draws the lines for one frame; the pose is camera-relative. */
	public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera) {
		Vec3 viewPos = camera.getPosition();
		Highlight.cleanup();
		boolean any = false;

		if(!Highlight.instances.isEmpty()) {
			VertexConsumer vc = buffers.getBuffer(Types.LINES);
			for(Highlight h : Highlight.instances)
				for(Highlight.Edge e : h.edges(viewPos))
					line(vc, pose.last(), e.from(), e.fromColor(), e.to(), e.toColor());
			any = true;
		}

		Entity target = TopRender.lockonTarget;
		if(target != null) {
			Vec3 tgt = new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
			Vec3 left = new Vec3(camera.getLeftVector());
			Vec3 up = new Vec3(camera.getUpVector());
			Vec3[] seg = TopRender.lockonBrackets(left, up, viewPos.distanceTo(tgt), TopRender.lockonTimer);
			Vec3 rel = tgt.subtract(viewPos);
			int col = TopRender.lockonColor(TopRender.lockonTimer);
			VertexConsumer vc = buffers.getBuffer(Types.LINES);
			for(int i = 0; i < seg.length; i += 2) line(vc, pose.last(), rel.add(seg[i]), col, rel.add(seg[i + 1]), col);
			any = true;
		}
		if(any) buffers.endBatch(Types.LINES);
	}

	/** One label, projected to the screen: where, how big (the text's own pixel scale), and what. */
	private record Label(float x, float y, float scale, String[] lines, int color) {}

	private static final java.util.List<Label> LABELS = new java.util.ArrayList<>();

	/**
	 * Projects every labelled highlight's text anchor to the screen and notes how large a font pixel is there. Upstream draws the text
	 * in the world, billboarded and scaled by {@code 1/32 textSize} blocks a pixel with the depth test off; a world-space text draw was
	 * tried first and drew nothing at any level-render stage in the headless client (the lightmap, the cull state and the stage were
	 * each tried), while screen text through {@code GuiGraphics} is the path the whole game uses and is the same picture: a flat
	 * label that faces the camera and shrinks with distance, seen through everything.
	 */
	static void collectLabels(RenderLevelStageEvent event) {
		LABELS.clear();
		Minecraft mc = Minecraft.getInstance();
		Vec3 viewPos = event.getCamera().getPosition();
		org.joml.Matrix4f projection = event.getProjectionMatrix();
		org.joml.Matrix4f modelView = event.getModelViewMatrix();
		int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
		for(Highlight h : Highlight.instances) {
			if(h.label.length <= 0) continue;
			Vec3 at = h.center.add(h.ray.scale(0.41421)).subtract(viewPos);
			org.joml.Vector4f v = new org.joml.Vector4f((float) at.x, (float) at.y, (float) at.z, 1);
			v.mul(modelView);
			float depth = -v.z;
			if(depth <= 0.05F) continue;                       // behind the camera
			v.mul(projection);
			if(v.w <= 0) continue;
			float ndcX = v.x / v.w, ndcY = v.y / v.w;
			float x = (ndcX * 0.5F + 0.5F) * width, y = (1 - (ndcY * 0.5F + 0.5F)) * height;
			// a block at this depth is (m11 * height / 2 / depth) gui pixels tall; a font pixel is textSize / 32 of a block
			float perBlock = projection.m11() * height * 0.5F / depth;
			float scale = (float) (1 / 16d / 2 * h.textSize) * perBlock;
			String[] lines = new String[h.label.length];
			for(int i = 0; i < lines.length; i++) lines[i] = String.valueOf(h.label[i]);
			LABELS.add(new Label(x, y, scale, lines, 0xFF000000 | (h.colorTop & 0xFFFFFF)));
		}
	}

	/** Draws what {@link #collectLabels} found, over the finished frame and under the HUD. */
	@SubscribeEvent
	public static void drawLabels(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
		if(LABELS.isEmpty()) return;
		net.minecraft.client.gui.GuiGraphics graphics = event.getGuiGraphics();
		Font font = Minecraft.getInstance().font;
		int lineHeight = 9;
		for(Label label : LABELS) {
			graphics.pose().pushPose();
			graphics.pose().translate(label.x(), label.y(), 0);
			graphics.pose().scale(label.scale(), label.scale(), 1);
			for(int i = 0; i < label.lines().length; i++) {
				String text = label.lines()[i];
				double offset = (i - (label.lines().length - 1) / 2d) * lineHeight;
				graphics.drawString(font, text, (int) Math.round(-font.width(text) / 2d), (int) Math.round(-7 / 2d + offset), label.color(), false);
			}
			graphics.pose().popPose();
		}
	}

	/** Upstream's {@code TopRender.main(RenderWorldLastEvent)}. */
	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || (Highlight.instances.isEmpty() && TopRender.lockonTarget == null)) {
			LABELS.clear();
			return;
		}
		render(event.getPoseStack(), mc.renderBuffers().bufferSource(), event.getCamera());
		collectLabels(event);
	}
}
