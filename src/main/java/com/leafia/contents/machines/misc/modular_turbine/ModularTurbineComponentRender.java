package com.leafia.contents.machines.misc.modular_turbine;

import java.util.HashMap;
import java.util.Map;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase.TurbineComponentType;
import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws every modular turbine part except the core: the spinning shaft, blades, flywheels, the
 * generator, and the static discs of separators and ports.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ModularTurbineComponentRender.java}.
 *
 * <h2>What it draws</h2>
 *
 * Everything is rotated by facing with upstream's four angles (north 180, south 0, west 270, east
 * 90 — transcribed, not taken from {@code ObjUtil.rotateForFacing}, which D-1021 found is not the
 * addon's convention everywhere). Then:
 *
 * <ul>
 * <li>every part but the core draws a <b>shaft</b> through itself, stretched along the shaft to the
 *     part's depth, turning by the core's angle — interpolated between its last two ticks — in the
 *     direction that matches which way the turbine faces;</li>
 * <li><b>blades</b> draw two offset fans on that shaft, the second slightly smaller and turned
 *     7.5°, and each stage's fans shrink by 0.9 for every blade stage after it on the shaft, which
 *     is what makes a run of blades taper; then the housing — its inner shell in the blade's own
 *     look, glass or not, and its outer shell (there is none for 3x3; upstream's {@code Tube3}
 *     does not exist);</li>
 * <li><b>flywheels</b> draw two grey discs, light and dark, twice over for the 2x2 and 3x3;</li>
 * <li>the <b>generator</b> draws its own model; <b>separators and ports</b> draw the disc for their
 *     size.</li>
 * </ul>
 *
 * <h2>Culling, which upstream turned off and 1.21.1 makes a render type</h2>
 *
 * Upstream wraps the blades and the inner housing in {@code disableCull()} so the thin fans and the
 * inside of the tube show from both sides. Culling is a {@link RenderType} property now, and the
 * block-sheet types all cull, so those draw through
 * {@link RenderType#entityCutoutNoCull} — or {@link RenderType#entityTranslucent} for a glass
 * housing — on the block atlas, where the baked quads' UVs already point. D-1020 left
 * {@code disableCull} as an open question; this is its answer.
 *
 * <h2>And in your hand</h2>
 *
 * Upstream's nested {@code ModularTurbineComponentItemRender} renders a dummy tile entity with this
 * renderer, scaled to fit. {@link ItemRender} does the same with a block entity the block builds for
 * itself, and the item model's {@code display} block does the rest.
 */
@OnlyIn(Dist.CLIENT)
public class ModularTurbineComponentRender implements BlockEntityRenderer<ModularTurbineComponentTE> {

	/** Upstream's flywheel greys. */
	public static final float LIGHT = 0.539F;
	public static final float DARK = 0.195F;

	public ModularTurbineComponentRender() { }

	public ModularTurbineComponentRender(BlockEntityRendererProvider.Context context) { }

	static float facingDegrees(Direction dir) {
		return switch(dir) {
			case NORTH -> 180F;
			case SOUTH -> 0F;
			case WEST -> 270F;
			case EAST -> 90F;
			default -> 0F;
		};
	}

	/**
	 * The buffer is asked for at every draw, never held across one: a {@link MultiBufferSource}
	 * hands every non-fixed render type the same shared builder and finishes it when a different
	 * type is asked for, so a consumer kept from before the switch throws "Not building!". That
	 * crashed the client on the first frame a turbine was in view, once D-1062 gave its OBJs
	 * quads to draw - with none, nothing had ever been written to the stale consumer.
	 */
	private static void draw(String part, PoseStack matrix, MultiBufferSource buffers, RenderType type, int light) {
		draw(part, matrix, buffers, type, light, 1F);
	}

	private static void draw(String part, PoseStack matrix, MultiBufferSource buffers, RenderType type, int light, float grey) {
		ObjUtil.renderModel(ObjUtil.getModel(ModularTurbineModels.get(part)), matrix, buffers.getBuffer(type), light,
				OverlayTexture.NO_OVERLAY, grey, grey, grey);
	}

	@Override
	public void render(ModularTurbineComponentTE te, float partialTicks, PoseStack matrix,
			MultiBufferSource buffers, int light, int overlay) {
		// CAUTION, upstream's and still true: this also draws items, where the block entity has
		// no level - every level read below is guarded.
		BlockState state = te.getBlockState();
		if(!(state.getBlock() instanceof ModularTurbineBlockBase block)) return;

		RenderType solid = Sheets.cutoutBlockSheet();
		RenderType noCull = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(facingDegrees(state.getValue(BlockDummyable.DIR))));

		int[] dimensions = block.getDimensions();
		int forwardSize = dimensions[2];
		int backwardSize = dimensions[3];
		int totalSize = 1 + forwardSize + backwardSize;
		double totalSizeHalf = 1 + forwardSize / 2d + backwardSize / 2d;
		Direction face = te.getAssemblyFacing();
		boolean renderShaft = block.componentType() != null;
		boolean reverseShaft = face.equals(Direction.SOUTH) || face.equals(Direction.WEST);

		if(renderShaft) {
			matrix.pushPose();
			matrix.translate(0D, block.shaftHeight() + 0.5D, 0D);
			if(te.core != null) {
				double spin = te.core.local$shaftAnglePrev + (te.core.local$shaftAngle - te.core.local$shaftAnglePrev) * partialTicks;
				if(!reverseShaft)
					spin *= -1;
				matrix.mulPose(Axis.ZP.rotationDegrees((float) spin));
			}
			matrix.pushPose();
			matrix.translate(0D, 0D, -(forwardSize - backwardSize) / 2d);
			matrix.scale(1F, 1F, (float) (totalSize + 0.01 / totalSizeHalf)); // retard gaming (upstream's)
			draw("shaft", matrix, buffers, solid, light);
			matrix.popPose();

			if(block.componentType() == TurbineComponentType.FLYWHEEL) {
				int n = block.size();
				if(n == 2 || n == 3) {
					for(double z : new double[] { -0.25, 0.25 }) {
						matrix.pushPose();
						matrix.translate(0D, 0D, z);
						draw("flywheel" + n, matrix, buffers, solid, light, LIGHT);
						draw("flywheel" + n + "_dark", matrix, buffers, solid, light, DARK);
						matrix.popPose();
					}
				} else {
					draw("flywheel" + n, matrix, buffers, solid, light, LIGHT);
					draw("flywheel" + n + "_dark", matrix, buffers, solid, light, DARK);
				}
			} else if(block.componentType() == TurbineComponentType.BLADES) {
				if(te.hasLevel())
					te.local$checkForBlades(te.local$firstRender);
				te.local$firstRender = false;
				double diameter = block.size();
				ModularTurbineComponentTE curBlade = te;
				while(curBlade.local$nextBlade != null) {
					curBlade = curBlade.local$nextBlade;
					diameter *= 0.9;
				}
				matrix.scale((float) (diameter / 3), (float) (diameter / 3), 2F);
				if(reverseShaft)
					matrix.scale(-1F, 1F, 1F);
				matrix.pushPose();
				matrix.translate(0D, 0D, -0.125D);
				draw("blades", matrix, buffers, noCull, light);
				matrix.popPose();
				matrix.pushPose();
				matrix.scale((float) Math.sqrt(0.9), (float) Math.sqrt(0.9), 1F);
				matrix.mulPose(Axis.ZP.rotationDegrees(7.5F));
				matrix.translate(0D, 0D, 0.125D);
				draw("blades", matrix, buffers, noCull, light);
				matrix.popPose();
			}
			matrix.popPose();
		}

		switch(block.componentType() == null ? TurbineComponentType.SHAFT_ONLY : block.componentType()) {
			case BLADES -> {
				int n = block.size();
				String look = block.variant();
				RenderType inner = look.contains("glass")
						? RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)
						: noCull;
				draw("tube" + n + "_inner_" + look, matrix, buffers, inner, light);
				if(ModularTurbineModels.has("tube" + n))
					draw("tube" + n, matrix, buffers, solid, light);
			}
			case FLYWHEEL -> { }
			case GENERATOR -> {
				if(block.size() == 3) draw("generator", matrix, buffers, solid, light);
			}
			default -> {
				switch(block.size()) {
					case 2, 3 -> draw("block2_3", matrix, buffers, solid, light);
					case 5 -> draw("block5", matrix, buffers, solid, light);
					case 7 -> draw("block7", matrix, buffers, solid, light);
					case 9 -> draw("block9", matrix, buffers, solid, light);
					default -> { }
				}
			}
		}

		matrix.popPose();
	}

	/** Upstream's fixed box, defined on the block entity beside the part it bounds. */
	@Override
	public AABB getRenderBoundingBox(ModularTurbineComponentTE te) {
		return te.getRenderBoundingBox();
	}

	/**
	 * Upstream's {@code ModularTurbineComponentItemRender}: the block entity renderer, drawing a
	 * block entity the block made for itself, facing north, scaled so the part's height fits the
	 * item's unit cube. The item model's {@code display} block does the hand and GUI transforms.
	 */
	@OnlyIn(Dist.CLIENT)
	public static class ItemRender extends BlockEntityWithoutLevelRenderer {

		private final ModularTurbineComponentRender renderer = new ModularTurbineComponentRender();
		private final Map<Block, ModularTurbineComponentTE> dummies = new HashMap<>();

		public ItemRender() {
			super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		}

		@Override
		public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix,
				MultiBufferSource buffers, int light, int overlay) {
			if(!(stack.getItem() instanceof BlockItem item)) return;
			if(!(item.getBlock() instanceof ModularTurbineBlockBase turbine)) return;

			ModularTurbineComponentTE dummy = dummies.computeIfAbsent(turbine, b -> {
				BlockState state = b.defaultBlockState()
						.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
						.setValue(BlockDummyable.DIR, Direction.NORTH);
				return turbine.newBlockEntity(BlockPos.ZERO, state) instanceof ModularTurbineComponentTE te ? te : null;
			});
			if(dummy == null) return;

			int size = turbine.shaftHeight() * 2 + 1;
			matrix.pushPose();
			matrix.translate(0.5D, 0.5D, 0.5D);
			matrix.scale(1F / size, 1F / size, 1F / size);
			matrix.translate(-0.5D, -size / 2D, -0.5D);
			renderer.render(dummy, 0F, matrix, buffers, light, overlay);
			matrix.popPose();
		}
	}
}
