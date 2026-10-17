package com.leafia.contents.machines.reactors.apr.blocks.core;

import java.util.ArrayList;
import java.util.List;

import com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMBUI;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The APR's client half: opening the chamber editor, and the "Invalid Block" boxes.
 *
 * <p>The boxes are upstream's {@code TopRender} highlights as the editor's highlight button uses them: red,
 * labelled, for ten seconds of wall-clock time (upstream subtracts the frame time from {@code lifetime}), a box one
 * block wide round each position. One per position: a new box replaces an old one at the same place.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class APRClient {

	private APRClient() {}

	private record Highlight(BlockPos pos, long until) { }

	private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

	public static void openAssembly(APRCoreTE te) {
		Minecraft.getInstance().setScreen(new APRMBUI(te));
	}

	public static void addHighlights(List<BlockPos> positions, float seconds) {
		long until = System.currentTimeMillis() + (long) (seconds * 1000);
		synchronized(HIGHLIGHTS) {
			for(BlockPos pos : positions) {
				HIGHLIGHTS.removeIf(h -> h.pos.equals(pos));
				HIGHLIGHTS.add(new Highlight(pos, until));
			}
		}
	}

	public static int highlightCount() {
		synchronized(HIGHLIGHTS) {
			return HIGHLIGHTS.size();
		}
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		List<Highlight> live;
		long now = System.currentTimeMillis();
		synchronized(HIGHLIGHTS) {
			HIGHLIGHTS.removeIf(h -> h.until <= now);
			if(HIGHLIGHTS.isEmpty()) return;
			live = new ArrayList<>(HIGHLIGHTS);
		}
		Minecraft mc = Minecraft.getInstance();
		Camera camera = event.getCamera();
		Vec3 cam = camera.getPosition();
		PoseStack pose = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		float r = ((APRHighlightPacket.COLOR >> 16) & 0xFF) / 255F;
		float g = ((APRHighlightPacket.COLOR >> 8) & 0xFF) / 255F;
		float b = (APRHighlightPacket.COLOR & 0xFF) / 255F;

		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		for(Highlight h : live) {
			AABB box = new AABB(h.pos).move(-cam.x, -cam.y, -cam.z);
			LevelRenderer.renderLineBox(pose, lines, box, r, g, b, 1F);
		}
		buffers.endBatch(RenderType.lines());

		Font font = mc.font;
		for(Highlight h : live) {
			pose.pushPose();
			pose.translate(h.pos.getX() + 0.5 - cam.x, h.pos.getY() + 0.5 - cam.y, h.pos.getZ() + 0.5 - cam.z);
			pose.mulPose(camera.rotation());
			pose.scale(-0.025F, -0.025F, 0.025F);
			String label = APRHighlightPacket.LABEL;
			font.drawInBatch(label, -font.width(label) / 2F, -font.lineHeight / 2F, APRHighlightPacket.COLOR, false, pose.last().pose(),
					buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
			pose.popPose();
		}
		buffers.endBatch();
	}
}
