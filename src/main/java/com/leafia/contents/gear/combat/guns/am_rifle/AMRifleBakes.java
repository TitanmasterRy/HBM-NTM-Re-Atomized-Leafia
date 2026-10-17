package com.leafia.contents.gear.combat.guns.am_rifle;

import com.leafia.lib.LeafiaRef;

import net.minecraft.resources.ResourceLocation;

/**
 * Where the anti-mass rifle's part bakes are, with no client class in it so a server test can check them (D-3041).
 *
 * <p>The base mod's part-bake seam (D-2207) draws one group of an OBJ in any namespace: {@code GunBakes.register} the groups,
 * {@code GunBakes.baked(OBJ, group)} to draw. {@link #OBJ} is the OBJ's path under {@code models/} without the extension, so the model
 * file is {@code leafia:models/weapons/am_rifle.obj} (the five groups under one material, which the part bake's material override
 * defines) and each group's bake is {@code leafia:gun/part/weapons/am_rifle/<group lower case>}, written by
 * a generator script as {@code GunSystem.Data.partBake} would. {@link AMRifleRender} draws them.
 */
public final class AMRifleBakes {

	private AMRifleBakes() {}

	public static final ResourceLocation OBJ = LeafiaRef.rl("weapons/am_rifle");

	/** The five groups of {@code am_rifle.obj}, in the file's order. */
	public static final String[] PARTS = { "Barrel", "Gun", "BarrelBase", "Grip", "Fan" };
}
