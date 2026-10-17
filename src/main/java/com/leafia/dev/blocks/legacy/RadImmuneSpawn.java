package com.leafia.dev.blocks.legacy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate;

/**
 * "Only things that radiation cannot touch may spawn on me": upstream's
 * {@code canEntitySpawn(state, entity) { return ContaminationUtil.isRadImmune(entity); }}, on the
 * contamination blocks.
 *
 * <p>Not an upstream file. 1.12.2 asked the question of the entity; 1.21.1's
 * {@code Properties.isValidSpawn} asks it of an {@link EntityType}, and a type cannot answer
 * {@code ContaminationUtil.isRadImmune}, which tests the entity's class against
 * {@code immuneEntities} - mooshroom, zombie, skeleton, ocelot and {@code IRadiationImmune} -
 * and for the mutation effect on the individual. {@code EntityType.getBaseClass()} is no help
 * (it answers {@code Entity.class} for every type), so the set is spelled out: the types whose
 * class <em>is</em>, or extends, one of those. A zombie's subclasses are the husk, the drowned,
 * the zombie villager, the zombified piglin and the giant; 1.21.1's skeletons other than the
 * plain one (the stray, the wither skeleton, the bogged) extend {@code AbstractSkeleton}, not
 * {@code Skeleton}, so they are not immune, as upstream's four were not. The two things a type
 * cannot know - the mutation effect and an {@code IRadiationImmune} entity of the base mod's -
 * are not consulted.
 */
public final class RadImmuneSpawn {

	private RadImmuneSpawn() {}

	public static boolean allows(EntityType<?> type) {
		return type == EntityType.MOOSHROOM
				|| type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED
				|| type == EntityType.ZOMBIE_VILLAGER || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.GIANT
				|| type == EntityType.SKELETON
				|| type == EntityType.OCELOT;
	}

	/** For {@code Properties.isValidSpawn}. */
	public static final StateArgumentPredicate<EntityType<?>> PREDICATE = (state, level, pos, type) -> allows(type);
}
