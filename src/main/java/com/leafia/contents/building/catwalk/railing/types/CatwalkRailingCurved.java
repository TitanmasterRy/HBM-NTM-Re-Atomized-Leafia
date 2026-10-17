package com.leafia.contents.building.catwalk.railing.types;

import com.leafia.contents.building.catwalk.railing.CatwalkRailingBase;

/**
 * The one railing upstream registers: {@code catwalk_railing_curved}.
 *
 * <p>Upstream: {@code .../railing/types/CatwalkRailingCurved.java}, twelve lines that set
 * {@code spritePath} and {@code modelPath} to {@code "curved"} and pass
 * {@code Material.IRON}. The two paths are one argument here, because in this port the sprite
 * and the model are named by the generated model JSON rather than assembled at runtime — see
 * a generator script.
 *
 * <p>{@code CatwalkRailingBase} is abstract upstream and has this single subclass, so the pair
 * looks redundant; it is kept as a pair because the .obj carries eight {@code stair_*} objects
 * and a sloped variant is plainly what the base was split out for.
 */
public class CatwalkRailingCurved extends CatwalkRailingBase {

	public CatwalkRailingCurved(Properties properties) {
		super(properties, "curved");
	}
}
