package com.leafia.unsorted;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Ported from `com.leafia.unsorted.IEntityCustomCollision`.
 *
 * <p>An entity that collides as a set of boxes rather than as one bounding box - what the
 * elevator car is, so a player can stand inside one. {@code AxisAlignedBB} is {@link AABB}.
 */
public interface IEntityCustomCollision {
	List<AABB> getCollisionBoxes(Entity other);
}
