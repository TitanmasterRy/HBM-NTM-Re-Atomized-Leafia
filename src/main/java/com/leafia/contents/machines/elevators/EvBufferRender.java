package com.leafia.contents.machines.elevators;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The spring at the bottom of a shaft, which compresses as a cabin settles onto it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvBufferRender.java}. Three parts
 * of one OBJ: a fixed {@code Base}, a {@code Move} piece that slides down, and a {@code Scale}
 * piece that squashes. All three share one texture, so unlike the pulley the split into three
 * models is about the animation rather than about texturing.
 *
 * <h2>The arithmetic, which is all upstream's and is the only thing here worth getting right</h2>
 *
 * <ul>
 * <li>{@link EvBufferTE#GLOBAL_SCALE} is {@code 6/8.375} — the model is 8.375 units tall and the block it
 *     sits in is 6, so everything below is in <em>model</em> units and the whole thing is scaled
 *     once at the end.</li>
 * <li>{@code shrinkLength} is how far the cabin has pushed in, measured from the buffer's top
 *     ({@code pos.getY() + 8.375 * GLOBAL_SCALE}) down to the cabin's {@code posY - 0.25}, back
 *     out of world units by dividing by the scale, and clamped at zero so a cabin above the
 *     buffer does not stretch it.</li>
 * <li>{@code length} is {@code 4 - shrinkLength} clamped at {@code -0.375 * GLOBAL_SCALE}, which
 *     is how far the spring can bottom out.</li>
 * <li>{@code scale} is {@code (length + 0.375) / 4.375}: the squash factor, 1 at rest.</li>
 * </ul>
 *
 * <p>All of it lives on {@link EvBufferTE} rather than here, because a
 * {@code @OnlyIn(Dist.CLIENT)} class cannot be loaded by a GameTest server and arithmetic in a
 * renderer is arithmetic nothing can check — D-1021's call for the fluid valve's handwheel,
 * made again. This class is left with the matrix work.
 *
 * <h2>Client-side only, and that is upstream's design</h2>
 *
 * {@link EvBufferTE#elevator} is found by a client-side AABB scan up the column rather than being
 * synchronised, so the compression is a purely visual effect computed where it is drawn. Nothing
 * on the server knows the spring is compressed, and nothing needs to.
 */
@OnlyIn(Dist.CLIENT)
public class EvBufferRender implements BlockEntityRenderer<EvBufferTE> {

	public EvBufferRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(EvBufferTE buffer, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = buffer.getBlockState();
		if(!(state.getBlock() instanceof EvBuffer)) return;

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		double bufferY = buffer.getBlockPos().getY();
		double cabinY = buffer.elevator == null ? Double.NaN : buffer.elevator.getY();

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(EvFloor.yawOf(state.getValue(BlockDummyable.DIR))));
		matrix.scale((float) EvBufferTE.GLOBAL_SCALE, (float) EvBufferTE.GLOBAL_SCALE, (float) EvBufferTE.GLOBAL_SCALE);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.EV_BUFFER_BASE), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		float scale = EvBufferTE.squash(bufferY, cabinY);

		matrix.pushPose();
		matrix.translate(0D, EvBufferTE.travel(bufferY, cabinY), 0D);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.EV_BUFFER_MOVE), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);
		matrix.popPose();

		matrix.pushPose();
		matrix.translate(0D, 3.625F * (1 - scale), 0D);
		matrix.scale(1F, scale, 1F);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.EV_BUFFER_SCALE), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);
		matrix.popPose();

		matrix.popPose();
	}
}
