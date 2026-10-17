package com.leafia.contents.building.doors.renderers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The small crimson door's renderer: the left leaf slides 1.4375 and the right
 * only 0.4375, because the right leaf is narrower, and the cut is at 1.499.
 *
 * <p>Upstream: {@code .../doors/renderers/CrimsonDoorSmallRender.java}, and see
 * {@link CrimsonDoorRenderBase} for what it draws. The model is
 * {@code crimdoorsmall.obj} on the large door's sheet - upstream's small renderer binds
 * {@code crimdoorlarge/texture.png} too.
 */
@OnlyIn(Dist.CLIENT)
public class CrimsonDoorSmallRender extends CrimsonDoorRenderBase {

	public static final CrimsonDoorSmallRender INSTANCE = new CrimsonDoorSmallRender();

	private CrimsonDoorSmallRender() {
		super("crimson_door_small", 1.4375, 0.4375, 1.499);
	}
}
