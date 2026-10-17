package com.leafia.contents.machines.elevators.floors;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.elevators.car.ElevatorModels.Landing;
import com.leafia.contents.machines.elevators.car.ElevatorModels.S6;
import com.leafia.contents.machines.elevators.car.ElevatorModels.Skylift;
import com.leafia.contents.machines.elevators.car.ElevatorModels.Style;
import com.leafia.dev.render.LeafiaBrush;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A landing: its door frame, its two sliding leaves and its call button.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/floors/EvFloorRender.java}. Both
 * floor blocks share one OBJ and differ only in which textures they take, which is why the
 * generator writes {@code elevator_landing_frames_s6} and {@code _skylift} rather than one model
 * each way.
 *
 * <h2>The doors track the cabin without either knowing about the other</h2>
 *
 * {@link EvFloorTE#open} is written directly by the cabin every tick it is adjacent, and the
 * block entity broadcasts the change — so this renderer reads a number the cabin put there and
 * moves the leaves by upstream's {@code 0.440625} at full open, the same travel the cabin's own
 * doors use. The two match because it is the same constant, not because anything synchronises
 * them.
 *
 * <h2>The call button lights from the cabin's own button set</h2>
 *
 * A landing's button is lit when the cabin has {@code "floor<n>"} enabled or freshly clicked —
 * so it reads through the pulley to the cabin, and a landing with no cabin in its shaft draws an
 * unlit button rather than nothing. Upstream's, and the reason the button is a hand-built quad
 * rather than part of the OBJ: it has to change texture without changing geometry.
 *
 * <p>{@code staticZ} here is {@code 0.501f} — a hair in front of the door's own face, so the
 * button does not z-fight with it. Upstream's number, and the only one of the three that is not
 * shared with {@link EvFloor}'s hit region.
 */
@OnlyIn(Dist.CLIENT)
public class EvFloorRender implements BlockEntityRenderer<EvFloorTE> {

	/** Upstream's door travel, the same constant the cabin's doors use. */
	private static final float DOOR_TRAVEL = 0.440625F;

	/** Upstream's button position, which is {@link EvFloor}'s hit region to the digit. */
	private static final float BUTTON_X = 0.125F * 5;
	private static final float BUTTON_Y = 0.125F * 8;

	/** Just proud of the door's face, so the button does not z-fight it. */
	private static final float BUTTON_Z = 0.501F;

	public EvFloorRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(EvFloorTE landing, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = landing.getBlockState();
		if(!(state.getBlock() instanceof EvFloor)) return;

		boolean skylift = isSkylift(state.getBlock());
		Style style = skylift ? Skylift.STYLE : S6.STYLE;

		ModelResourceLocation frames = skylift ? Landing.FRAMES_SKYLIFT : Landing.FRAMES_S6;
		ModelResourceLocation doorL = skylift ? Landing.DOOR_L_SKYLIFT : Landing.DOOR_L_S6;
		ModelResourceLocation doorR = skylift ? Landing.DOOR_R_SKYLIFT : Landing.DOOR_R_S6;

		VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(EvFloor.yawOf(state.getValue(BlockDummyable.DIR))));

		draw(matrix, buffer, frames, packedLight);

		float door = landing.open.get();

		matrix.pushPose();
		matrix.translate(-DOOR_TRAVEL * door, 0D, 0D);
		draw(matrix, buffer, doorL, packedLight);
		matrix.popPose();

		matrix.pushPose();
		matrix.translate(DOOR_TRAVEL * door, 0D, 0D);
		draw(matrix, buffer, doorR, packedLight);
		matrix.popPose();

		renderCallButton(landing, style, matrix, buffers, packedLight);

		matrix.popPose();
	}

	/**
	 * The call button, lit when the cabin has this floor queued.
	 *
	 * <p>The quad's insets are the cabin's own — {@link Style#buttonLow}/{@link Style#buttonHigh}
	 * — so a Skylift landing's button is the same size as a Skylift cabin's, which is upstream's
	 * arrangement and the reason those two numbers live on the style rather than in either
	 * renderer.
	 */
	private static void renderCallButton(EvFloorTE landing, Style style, PoseStack matrix,
			MultiBufferSource buffers, int light) {

		String id = "floor" + landing.floor;
		boolean lit = landing.pulley != null && landing.pulley.elevator != null
				&& (landing.pulley.elevator.enabledButtons.contains(id)
						|| landing.pulley.elevator.clickedButtons.containsKey(id));

		ResourceLocation texture = lit ? style.buttonOn : style.buttonOff;

		LeafiaBrush brush = new LeafiaBrush().begin(buffers, matrix, texture, light).normal(0, 0, 1);
		double x0 = BUTTON_X + style.buttonLow / 16D;
		double y0 = BUTTON_Y + style.buttonLow / 16D;
		double x1 = BUTTON_X + style.buttonHigh / 16D;
		double y1 = BUTTON_Y + style.buttonHigh / 16D;
		brush.addVertexWithUV(x0, y0, BUTTON_Z, 0, 1);
		brush.addVertexWithUV(x1, y0, BUTTON_Z, 1, 1);
		brush.addVertexWithUV(x1, y1, BUTTON_Z, 1, 0);
		brush.addVertexWithUV(x0, y1, BUTTON_Z, 0, 0);
		brush.end();
	}

	/** True for {@code elevator_skyliftfloor}, which is the only thing separating the two. */
	private static boolean isSkylift(Block block) {
		return LeafiaBlocks.elevator_skyliftfloor != null
				&& block == LeafiaBlocks.elevator_skyliftfloor.get();
	}

	private static void draw(PoseStack matrix, VertexConsumer buffer, ModelResourceLocation model, int light) {
		ObjUtil.renderModel(ObjUtil.getModel(model), matrix, buffer, light, OverlayTexture.NO_OVERLAY);
	}
}
