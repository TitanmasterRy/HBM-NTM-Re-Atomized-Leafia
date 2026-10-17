package com.leafia.contents.machines.elevators;

import com.leafia.dev.render.LeafiaBrush;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The rope between a pulley and whatever is hanging from it.
 *
 * <p>Upstream this is {@code ElevatorRender.renderCable} plus the {@code cable} texture constant,
 * a static on the cabin's renderer that {@code EvWeightRender} reaches into. <b>Hoisted</b>, the
 * same move D-1034 made with {@code HitSrf}: the counterweight's renderer needs the rope and
 * nothing else the cabin's renderer has, and leaving it there would put the whole 412-line
 * {@code ElevatorRender} — and the ~112 generated part models the cabin's OBJ needs — inside this
 * batch's closure for the sake of twenty lines.
 *
 * <p>That is now twice this campaign has found a shared leaf inside a file that looked atomic, so
 * it is worth stating as a rule: <b>before accepting that a renderer knot has no entry, check
 * what its siblings actually import from it.</b> Here it was one method and one
 * {@code ResourceLocation}.
 *
 * <h2>Five strands, not one</h2>
 *
 * Both callers loop {@code i} from -2 to 2 and draw the same rope five times, each offset
 * {@code i * 0.0625 * 2} along Z — a flat ribbon of five parallel ropes rather than one. Kept;
 * it is what makes the rope read as a cable from the side.
 *
 * <h2>Four faces, and one of them is degenerate</h2>
 *
 * {@link #renderCable} draws four quads around the rope's axis, which is upstream's, and the
 * winding is upstream's too — the two Z-facing quads wind opposite ways so both are visible, and
 * the two X-facing quads do not, so one of them is backface-culled. Reproduced rather than
 * corrected: it is invisible in play (the rope is under a block wide) and fixing it would change
 * what a player sees, which is D-021's line.
 */
@OnlyIn(Dist.CLIENT)
public final class EvRope {

	private EvRope() {}

	/** Upstream's {@code ElevatorRender.cable}, the rope texture. */
	public static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/block/elevator_rope.png");

	/** Upstream's strand offsets: five ropes, an eighth of a block apart. */
	private static final int STRANDS = 2;
	private static final double STRAND_SPACING = 0.0625 * 2;

	/** Upstream's {@code thickness} argument at both call sites. */
	public static final double THICKNESS = 0.0625;

	/**
	 * Draws the whole five-strand rope from {@code start} to {@code end}, both in the caller's
	 * current object space.
	 *
	 * <p>Upstream leaves the {@code startDrawingQuads}/{@code draw} pair and the strand loop at
	 * each call site and they are identical in both; folded in here, because two copies of a loop
	 * whose bounds decide how thick a rope looks is how the cabin's and the counterweight's ropes
	 * come to differ by one strand.
	 */
	public static void render(MultiBufferSource buffers, PoseStack matrix, Vec3 start, Vec3 end, int light) {

		LeafiaBrush brush = new LeafiaBrush().begin(buffers, matrix, TEXTURE, light);

		for(int i = -STRANDS; i <= STRANDS; i++) {
			Vec3 offset = new Vec3(0, 0, i * STRAND_SPACING);
			renderCable(brush, start.add(offset), end.add(offset), THICKNESS);
		}

		brush.end();
	}

	/** Upstream's {@code renderCable}, vertex for vertex. */
	private static void renderCable(LeafiaBrush brush, Vec3 startPos, Vec3 endPos, double thickness) {

		thickness /= 2;

		brush.addVertexWithUV(startPos.x - thickness, startPos.y, startPos.z + thickness, 0, 1);
		brush.addVertexWithUV(startPos.x + thickness, startPos.y, startPos.z + thickness, 1, 1);
		brush.addVertexWithUV(endPos.x + thickness, endPos.y, endPos.z + thickness, 1, 0);
		brush.addVertexWithUV(endPos.x - thickness, endPos.y, endPos.z + thickness, 0, 0);

		brush.addVertexWithUV(startPos.x + thickness, startPos.y, startPos.z - thickness, 0, 1);
		brush.addVertexWithUV(startPos.x - thickness, startPos.y, startPos.z - thickness, 1, 1);
		brush.addVertexWithUV(endPos.x - thickness, endPos.y, endPos.z - thickness, 1, 0);
		brush.addVertexWithUV(endPos.x + thickness, endPos.y, endPos.z - thickness, 0, 0);

		brush.addVertexWithUV(startPos.x - thickness, startPos.y, startPos.z - thickness, 0, 1);
		brush.addVertexWithUV(startPos.x - thickness, startPos.y, startPos.z + thickness, 1, 1);
		brush.addVertexWithUV(endPos.x - thickness, endPos.y, endPos.z + thickness, 1, 0);
		brush.addVertexWithUV(endPos.x - thickness, endPos.y, endPos.z - thickness, 0, 0);

		brush.addVertexWithUV(startPos.x + thickness, startPos.y, startPos.z + thickness, 0, 1);
		brush.addVertexWithUV(startPos.x + thickness, startPos.y, startPos.z - thickness, 1, 1);
		brush.addVertexWithUV(endPos.x + thickness, endPos.y, endPos.z - thickness, 1, 0);
		brush.addVertexWithUV(endPos.x + thickness, endPos.y, endPos.z + thickness, 0, 0);
	}
}
