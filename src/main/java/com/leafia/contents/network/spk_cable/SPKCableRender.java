package com.leafia.contents.network.spk_cable;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.leafia.contents.network.spk_cable.SPKCableTE.EffectLink;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The SPK cable's sparks.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/SPKCableRender.java}, which drew the
 * whole cable: it picked {@code CX}/{@code CY}/{@code CZ} for a straight piece or {@code Core}
 * plus an arm per neighbour for a corner, and then, for a corner, the crackling arcs. In 1.21.1
 * the first half is a blockstate (the six connections choose a model baked from
 * {@code cable_spk.obj}, see a generator script), so this renderer is the
 * second half only.
 *
 * <p>For each of a corner's six links that this end <em>emits</em> (see
 * {@link SPKCableTE.EffectLink}) it draws one {@link EnumWaveType#RANDOM} beam from just outside
 * the core, {@code 3/16} along the direction, to just outside the far end: {@code 3/16} short of
 * the other corner's centre, or, for a link that ends at a receiver rather than a cable, all the
 * way to the receiver's centre. Colours, thickness, layer count and the five segments per block
 * are upstream's literals. The seed is the world clock, so the arc re-rolls every tick, as it did.
 *
 * <p>Upstream's second class here, {@code SPKCableItemRender}, drew four short beams in the
 * inventory icon and held item. That is not reproduced: an item model is static JSON, and the
 * beams would need a custom item renderer that also redraws the baked arms. The icon is the
 * core and four arms without sparks (D-3008).
 *
 * <p>Upstream's note on its own beam call is "mlbv: i really don't know how i am supposed to port
 * this", which is left as a record of where the author was unsure.
 */
@OnlyIn(Dist.CLIENT)
public class SPKCableRender implements BlockEntityRenderer<SPKCableTE> {

	/** Upstream's {@code 3/16d}: how far from the core's centre an arc starts. */
	private static final double OFFSET = 3 / 16d;

	public SPKCableRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(SPKCableTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {

		if(te.getLevel() == null || !te.isCorner) return;

		for(EffectLink link : te.links) {

			if(link.link == null || !link.emit) continue;

			double distance = Math.sqrt(te.getBlockPos().distSqr(link.link));
			Vec3 look = new Vec3(link.direction.getStepX(), link.direction.getStepY(), link.direction.getStepZ());

			pose.pushPose();
			pose.translate(0.5, 0.5, 0.5);
			pose.translate(look.x * OFFSET, look.y * OFFSET, look.z * OFFSET);

			LCEBeamPronter.prontBeam(pose, buffers,
					look.scale(distance - OFFSET * (link.nonCable ? 1 : 2)),
					EnumWaveType.RANDOM, EnumBeamType.SOLID,
					0x64001e, 0x9A9A9A,
					(int) (te.getLevel().getGameTime() % 1000),
					(int) (distance * 5), 1 / 16f,
					2, 0.666f / 16f);

			pose.popPose();
		}
	}

	/** An arc can reach as far as the cable it links to, which may be well outside the block. */
	@Override
	public AABB getRenderBoundingBox(SPKCableTE te) {

		AABB box = new AABB(te.getBlockPos());

		for(EffectLink link : te.links) {
			if(link.link != null && link.emit) box = box.minmax(new AABB(link.link));
		}

		return box;
	}
}
