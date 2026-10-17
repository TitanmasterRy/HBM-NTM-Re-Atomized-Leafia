package com.leafia.contents.machines.reactors.lftr.components.arbitrary;

import java.util.List;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.leafia.dev.math.FiaMatrix;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the block an arbitrary block holds: a twelve-sixteenths square of its texture on each of the
 * six faces, just proud of the surface.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/arbitrary/MSRArbitraryRender.java}.
 * Upstream finds the held block's texture from its model - the first north-facing quad's sprite, else
 * the particle sprite - and binds that texture <em>file</em>; here the same sprite is drawn out of the
 * block atlas, which is the same picture (an animated texture shows its current frame rather than the
 * whole strip). The six squares are placed with the same {@link FiaMatrix} construction as upstream's,
 * 0.502 from the centre and 6/16 either side, and each is lit by the block on the side its matrix
 * faces away from - which, because {@code translate} moves against the front vector, is the block the
 * square is drawn against. Upstream's order of operations is kept rather than simplified, since it is
 * what makes the lighting come out right.
 */
public class MSRArbitraryRender implements BlockEntityRenderer<MSRArbitraryTE> {

	private static final float HALF = 6 / 16F;

	public MSRArbitraryRender(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(MSRArbitraryTE te, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
		ItemStack held = te.inventory.getStackInSlot(0);
		if(!(held.getItem() instanceof BlockItem block) || te.getLevel() == null) return;
		BlockState display = block.getBlock().defaultBlockState();
		BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(display);
		TextureAtlasSprite sprite = spriteOf(baked, display);
		if(sprite == null) return;

		VertexConsumer buf = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		drawSquares(pose, buf, sprite, facing -> LevelRenderer.getLightColor(te.getLevel(), te.getBlockPos().relative(facing.getOpposite())));
	}

	/**
	 * The six squares, drawn from the pose's origin (the block's centre) in the sprite given, each lit by {@code lightOf} for the face
	 * it sits on. Shared with the Cannery's LFTR scene, whose jar draws the same squares on its own arbitrary blocks (D-3029).
	 */
	public static void drawSquares(PoseStack pose, VertexConsumer buf, TextureAtlasSprite sprite, ToIntFunction<Direction> lightOf) {
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		PoseStack.Pose last = pose.last();
		for(Direction facing : Direction.values()) {
			int light = lightOf.applyAsInt(facing);
			FiaMatrix mat = new FiaMatrix(Vec3.ZERO, Vec3.atLowerCornerOf(facing.getNormal())).translate(0, 0, 0.502);
			Vec3 vec0 = mat.translate(-HALF, -HALF, 0).position;
			Vec3 vec1 = mat.translate(HALF, -HALF, 0).position;
			Vec3 vec2 = mat.translate(HALF, HALF, 0).position;
			Vec3 vec3 = mat.translate(-HALF, HALF, 0).position;
			Vec3 normal = mat.position.normalize();
			vertex(buf, last, vec0, sprite.getU0(), sprite.getV1(), light, normal);
			vertex(buf, last, vec1, sprite.getU1(), sprite.getV1(), light, normal);
			vertex(buf, last, vec2, sprite.getU1(), sprite.getV0(), light, normal);
			vertex(buf, last, vec3, sprite.getU0(), sprite.getV0(), light, normal);
		}
		pose.popPose();
	}

	private static void vertex(VertexConsumer buf, PoseStack.Pose pose, Vec3 v, float u, float w, int light, Vec3 n) {
		buf.addVertex(pose, (float) v.x, (float) v.y, (float) v.z)
				.setColor(1F, 1F, 1F, 1F)
				.setUv(u, w)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}

	/** Upstream's {@code tryBindQuads}: the first north quad's sprite, else the particle sprite. */
	@Nullable
	public static TextureAtlasSprite spriteOf(BakedModel baked, BlockState display) {
		try {
			List<BakedQuad> quads = baked.getQuads(display, Direction.NORTH, RandomSource.create(0));
			if(!quads.isEmpty())
				return quads.get(0).getSprite();
			return baked.getParticleIcon();
		} catch(IllegalArgumentException ignored) { // upstream: "FUCK YOUU"
			return null;
		}
	}
}
