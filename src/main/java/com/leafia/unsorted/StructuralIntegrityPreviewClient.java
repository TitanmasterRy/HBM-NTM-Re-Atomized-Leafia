package com.leafia.unsorted;

import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.StructuralIntegrityHandler.SimulationData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

/**
 * Draws {@link StructuralIntegrityPreview} where the held block would go (D-3040). Upstream drew at {@code RenderWorldLast};
 * here {@code AFTER_TRANSLUCENT_BLOCKS}, through vanilla's line render type. Upstream reads the held block's state from its
 * metadata; a 1.21.1 block item places its default state, which is what is simulated.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class StructuralIntegrityPreviewClient {

	private StructuralIntegrityPreviewClient() {}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		if(!StructuralIntegrityHandler.automatic() && !(com.leafia.database.ImpactSeismic.seismic > 0)) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.level == null) return;
		ItemStack stack = mc.player.getMainHandItem();
		if(!(stack.getItem() instanceof BlockItem blockItem)) return;
		HitResult hit = mc.hitResult;
		if(!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
		BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
		SimulationData data = StructuralIntegrityHandler.handleBlockSimulate(mc.level, pos, blockItem.getBlock().defaultBlockState());
		if(data == null) return;

		Vec3 camera = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();
		pose.pushPose();
		pose.translate(pos.getX() + 0.5 - camera.x, pos.getY() + 0.5 - camera.y, pos.getZ() + 0.5 - camera.z);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		double offset = Math.floorMod(System.currentTimeMillis(), 1000L) / 1000D;
		double ratio = 1 - data.maxRatio;
		for(StructuralIntegrityPreview.Box box : StructuralIntegrityPreview.boxes(data.maxRatio, offset)) {
			pose.pushPose();
			pose.scale((float) box.scale(), (float) box.scale(), (float) box.scale());
			Matrix4f matrix = pose.last().pose();
			for(StructuralIntegrityPreview.Edge edge : StructuralIntegrityPreview.edges(ratio)) {
				float g = edge.red() ? 0 : 1;
				float nx = (float) (edge.x1() - edge.x0()), ny = (float) (edge.y1() - edge.y0()), nz = (float) (edge.z1() - edge.z0());
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if(len == 0) continue;
				lines.addVertex(matrix, (float) edge.x0(), (float) edge.y0(), (float) edge.z0()).setColor(1, g, g, box.alpha())
						.setNormal(pose.last(), nx / len, ny / len, nz / len);
				lines.addVertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1()).setColor(1, g, g, box.alpha())
						.setNormal(pose.last(), nx / len, ny / len, nz / len);
			}
			pose.popPose();
		}
		buffers.endBatch(RenderType.lines());
		pose.popPose();
	}
}
