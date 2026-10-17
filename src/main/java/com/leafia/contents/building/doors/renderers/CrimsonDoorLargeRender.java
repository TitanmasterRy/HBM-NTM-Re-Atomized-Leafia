package com.leafia.contents.building.doors.renderers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The large crimson door's renderer: 3 blocks of slide either way, and
 * the cut at 3.499.
 *
 * <p>Upstream: {@code .../doors/renderers/CrimsonDoorLargeRender.java}, and see
 * {@link CrimsonDoorRenderBase} for what it draws. The model is
 * {@code crimdoorlarge.obj} on the large door's sheet - upstream's small renderer binds
 * {@code crimdoorlarge/texture.png} too.
 */
@OnlyIn(Dist.CLIENT)
public class CrimsonDoorLargeRender extends CrimsonDoorRenderBase {

	public static final CrimsonDoorLargeRender INSTANCE = new CrimsonDoorLargeRender();

	private CrimsonDoorLargeRender() {
		super("crimson_door_large", 3.0, 3.0, 3.499);
	}
}
