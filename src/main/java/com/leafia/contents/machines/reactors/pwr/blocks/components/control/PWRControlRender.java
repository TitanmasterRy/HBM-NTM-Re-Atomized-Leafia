package com.leafia.contents.machines.reactors.pwr.blocks.components.control;

import java.util.ArrayList;
import java.util.List;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws a PWR control rod column: the frame, one segment per block, and the rods inside it raised by
 * {@code position x height}.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/control/PWRControlRender.java},
 * which binds a side or top texture and calls {@code renderPart} on one of two OBJs, segment by segment,
 * with the light of each segment's block. {@code renderPart} has no successor, so every (mesh, part) pair
 * is its own standalone {@code neoforge:obj} model with the other parts' visibility off and the texture that
 * part is drawn with - sixteen, written by a generator script (D-1040's translation).
 * Every texture coordinate is inside 0..1, so they bake against the block atlas (D-1087). The draw order,
 * the per-segment light, the {@code RodsEnd} condition and the classic mesh drawing its rod ends with the
 * side texture are upstream's. Upstream turns face culling off, so this draws with
 * {@code entityCutoutNoCull}; {@code isGlobalRenderer} is {@link #shouldRenderOffScreen}, and the render
 * box covers the whole column.
 */
public class PWRControlRender implements BlockEntityRenderer<PWRControlTE> {

	/** The OBJs' eight named parts, in upstream's order ({@link PWRControlBlock#PARTS}). */
	public static final String[] PARTS = PWRControlBlock.PARTS;

	/** {@code meshPWR} parts, in {@link #PARTS} order. */
	public static final ModelResourceLocation[] FINAL = parts("final");
	/** {@code meshOld} parts, for {@code reactor_control}. */
	public static final ModelResourceLocation[] CLASSIC = parts("classic");

	private static ModelResourceLocation[] parts(String mesh) {
		ModelResourceLocation[] out = new ModelResourceLocation[PARTS.length];
		for(int i = 0; i < PARTS.length; i++)
			out[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, PWRControlBlock.partModel(mesh, PARTS[i])));
		return out;
	}

	/** Every model this renderer draws, for {@code ModelEvent.RegisterAdditional}. */
	public static List<ModelResourceLocation> all() {
		List<ModelResourceLocation> all = new ArrayList<>(List.of(FINAL));
		all.addAll(List.of(CLASSIC));
		return all;
	}

	private static final int FRAME_SIDE = 0, FRAME_END = 1, FRAME_END_TOP = 2, FRAME_END_BTM = 3,
			RODS_SIDE = 4, RODS_END = 5, RODS_END_TOP = 6, RODS_END_BTM = 7;

	public PWRControlRender(BlockEntityRendererProvider.Context context) {}

	@Override
	public boolean shouldRenderOffScreen(PWRControlTE entity) {
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(PWRControlTE entity) {
		BlockPos pos = entity.getBlockPos();
		return new AABB(pos.getX(), pos.getY() + 1 - entity.height, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
	}

	private static void part(ModelResourceLocation[] mesh, int part, PoseStack matrix, VertexConsumer buffer, int light) {
		ObjUtil.renderModel(ObjUtil.getModel(mesh[part]), matrix, buffer, light, OverlayTexture.NO_OVERLAY);
	}

	@Override
	public void render(PWRControlTE entity, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {
		Level level = entity.getLevel();
		if(level == null || entity.underAnotherRod()) return;
		BlockPos pos = entity.getBlockPos();
		boolean classic = LeafiaBlocks.reactor_control != null && entity.getBlockState().is(LeafiaBlocks.reactor_control.get());
		ModelResourceLocation[] mesh = classic ? CLASSIC : FINAL;
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		int height = entity.height;

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);

		matrix.pushPose();
		for(int i = 1; i <= height; i++) {
			int segment = ObjUtil.lightAt(level, pos.below(i - 1));
			part(mesh, FRAME_SIDE, matrix, buffer, segment);
			part(mesh, FRAME_END, matrix, buffer, segment);
			if(i == 1) part(mesh, FRAME_END_TOP, matrix, buffer, segment);
			if(i == height) part(mesh, FRAME_END_BTM, matrix, buffer, segment);
			matrix.translate(0, -1, 0);
		}
		matrix.popPose();

		double raised = entity.position * height;
		matrix.translate(0, raised, 0);
		matrix.pushPose();
		for(int i = 1; i <= height; i++) {
			int segment = ObjUtil.lightAt(level, pos.below(i - 1).above((int) raised));
			part(mesh, RODS_SIDE, matrix, buffer, segment);
			if((Mth.positiveModulo(raised, 1) != 0) || (raised - i + 1 > 0)) part(mesh, RODS_END, matrix, buffer, segment);
			if(i == 1) part(mesh, RODS_END_TOP, matrix, buffer, segment);
			if(i == height) part(mesh, RODS_END_BTM, matrix, buffer, segment);
			matrix.translate(0, -1, 0);
		}
		matrix.popPose();

		matrix.popPose();
	}
}
