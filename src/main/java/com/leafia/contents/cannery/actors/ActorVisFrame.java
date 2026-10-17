package com.leafia.contents.cannery.actors;

import com.hbm.wiaj.JarScript;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * A wire cube in one cell: the twelve edges of a unit cube, scaled about its centre, in one colour. The LFTR topic draws the flux's
 * reach with these, a quarter of a block across, in yellow.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/actors/ActorVisFrame.java}. The twelve edges and the scale's offset
 * ({@code (1 - scale) / 2} on each axis, then the scale) are upstream's, in {@link VisActorRenders#frame}.
 */
public class ActorVisFrame extends ActorVisBase {

	final float scale;

	public ActorVisFrame(JarScript script, int x, int y, int z, int color) {
		this(script, x, y, z, color, 1);
	}

	public ActorVisFrame(JarScript script, int x, int y, int z, int color, float scale) {
		super(script, x, y, z, color);
		this.scale = scale;
	}

	public float scale() { return scale; }

	@Override
	public void renderContents(PoseStack pose, MultiBufferSource buffers) {
		VisActorRenders.frame(pose, buffers, color, scale);
	}
}
