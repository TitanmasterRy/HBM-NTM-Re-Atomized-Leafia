package com.leafia.init;

import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.weight.EvWeightEntity;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's entity types. Two, so far, and both belong to the elevators.
 *
 * <h2>The gate, again</h2>
 *
 * As with {@link LeafiaBlocks} and {@link LeafiaBlockEntities}, registration happens inside
 * {@link #register} behind the feature switch rather than in a static initialiser, because D-1002
 * says a disabled feature registers nothing. The holders are null when {@code features.elevators}
 * is off and every consumer is gated by the same switch.
 *
 * <h2>Upstream's numbers, and what became of them</h2>
 *
 * Upstream registers both through 1.12.2's {@code EntityRegistry.registerModEntity(..., id,
 * AddonBase.instance, 1000, 1, true)}:
 *
 * <ul>
 * <li><b>The mod-local numeric id</b> is gone with nothing to preserve — 1.21.1 keys entity types
 *     by {@code ResourceLocation}. The registry names {@code entity_elevator} and
 *     {@code entity_counterweight} are upstream's verbatim, so saved worlds and translation keys
 *     still name the same entities.</li>
 * <li><b>Tracking range 1000</b> is in <em>blocks</em> upstream and in <em>chunks</em> here, so it
 *     is 1000/16 = 62.5, rounded <em>up</em> to 63 rather than down, to be no shorter than
 *     upstream's. The server clamps it to the view distance in any case; what upstream is saying
 *     with a number that large is "never stop tracking this", and 63 says the same thing.</li>
 * <li><b>Update interval 1</b> is unchanged: every tick. A cabin a player is riding cannot
 *     interpolate.</li>
 * <li><b>{@code sendsVelocityUpdates = true}</b> has no successor and needs none — 1.21.1 sends
 *     velocity for every entity that has any.</li>
 * </ul>
 *
 * <p>{@link MobCategory#MISC} for both: neither is a mob, neither spawns naturally, and MISC is
 * what vanilla uses for boats and minecarts, which is what these are.
 */
public final class LeafiaEntities {

	private LeafiaEntities() {}

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(Registries.ENTITY_TYPE, LeafiaRef.MODID);

	/** The cabin. Null when {@code features.elevators} is off. */
	public static DeferredHolder<EntityType<?>, EntityType<ElevatorEntity>> ELEVATOR;

	/** The counterweight on the other end of the rope. Null when {@code features.elevators} is off. */
	public static DeferredHolder<EntityType<?>, EntityType<EvWeightEntity>> ELEVATOR_WEIGHT;

	/** The PWR's meltdown debris (D-3010). Null unless {@code LeafiaFeatures.pwrReactor()}. */
	public static DeferredHolder<EntityType<?>, EntityType<PWRDebrisEntity>> PWR_DEBRIS;

	/** A blown-apart DFC absorber's pieces (D-3014). Null unless {@code LeafiaFeatures.dfcOverhaulContent()}. */
	public static DeferredHolder<EntityType<?>, EntityType<com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity>> DFC_SHRAPNEL;

	/**
	 * Folkvangr, the addon's F.L.E.I.J.A. (D-3013). Null unless {@code features.overhauls.folkvangr}.
	 */
	public static DeferredHolder<EntityType<?>, EntityType<EntityNukeFolkvangr>> NUKE_FOLKVANGR;

	/** The APR's endothermic blast (D-3027). Null unless {@code LeafiaFeatures.aprReactor()}. */
	public static DeferredHolder<EntityType<?>, EntityType<com.leafia.contents.effects.nuke.NukeLCA>> NUKE_LCA;

	/** The custom nuke missile in flight (D-3022). Null when {@code features.addonBombs} is off. */
	public static DeferredHolder<EntityType<?>, EntityType<com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntity>> CUSTOM_NUKE_MISSILE;

	/** The rainbow beam of {@code ExplosionChaos.zomg}, the pink door's volley (D-3031). Null when {@code features.pinkDoor} is off. */
	public static DeferredHolder<EntityType<?>, EntityType<com.leafia.contents.effects.zomg.EntityRainbow>> RAINBOW;

	public static void register(IEventBus modBus) {

		if(LeafiaFeatures.addonBombs) {

			/*
			 * Upstream: registerModEntity(entity_missile_customnuke, CustomNukeMissileEntity, ..., 1000, 1, true) -
			 * tracked to 1000 blocks (63 chunks, D-607) every tick. The base mod's missiles are 1.5 x 1.5 (EntityThrowableNT's
			 * setSize), which a tier 0 missile inherits; so is this.
			 */
			CUSTOM_NUKE_MISSILE = ENTITY_TYPES.register("entity_missile_customnuke", () -> EntityType.Builder
					.<com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntity>of(
							com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntity::new, MobCategory.MISC)
					.sized(1.5f, 1.5f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_missile_customnuke"));
		}

		if(LeafiaFeatures.dfcOverhaulContent()) {

			/*
			 * Upstream: registerModEntity(entity_dfc_absorber_shrapnel, AbsorberShrapnelEntity, ..., 1000, 1, true),
			 * unconditionally - here with the overhaul's content, since only the overhauled absorber throws it.
			 * Tracked to 1000 blocks (63 chunks, D-607) every tick; the piece's own 0.45 x 0.3 hitbox.
			 */
			DFC_SHRAPNEL = ENTITY_TYPES.register("entity_dfc_absorber_shrapnel", () -> EntityType.Builder
					.<com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity>of(
							com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity::new, MobCategory.MISC)
					.sized(0.45f, 0.3f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_dfc_absorber_shrapnel"));
		}

		if(LeafiaFeatures.folkvangr) {

			/*
			 * Upstream: registerModEntity(entity_nuke_folkvangr, ..., 1000, 1, true) - tracked to 1000 blocks,
			 * which is 63 chunks, every tick. It never sets a size, so 1.12.2 Entity's 0.6 x 1.8.
			 */
			NUKE_FOLKVANGR = ENTITY_TYPES.register("entity_nuke_folkvangr", () -> EntityType.Builder
					.<EntityNukeFolkvangr>of(EntityNukeFolkvangr::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_nuke_folkvangr"));
		}

		if(LeafiaFeatures.aprReactor()) {

			/*
			 * Upstream: registerModEntity(entity_nuke_lca, NukeLCA, ..., 1000, 1, true) - tracked to 1000 blocks,
			 * which is 63 chunks, every tick. No size set, so 1.12.2 Entity's 0.6 x 1.8.
			 */
			NUKE_LCA = ENTITY_TYPES.register("entity_nuke_lca", () -> EntityType.Builder
					.<com.leafia.contents.effects.nuke.NukeLCA>of(com.leafia.contents.effects.nuke.NukeLCA::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_nuke_lca"));
		}

		if(LeafiaFeatures.pwrReactor()) {

			/*
			 * Upstream: registerModEntity(entity_lwr_debris, PWRDebrisEntity, ..., 1000, 1, true) - tracked to
			 * 1000 blocks, which is 63 chunks (D-607), every tick. The hitbox is the piece's own and changes
			 * with its kind (getDimensions); the type's size is NTM-CE's EntityDebrisBase default, 1.12.2
			 * Entity's 0.6 x 1.8, only until the kind is set.
			 */
			PWR_DEBRIS = ENTITY_TYPES.register("entity_lwr_debris", () -> EntityType.Builder
					.<PWRDebrisEntity>of(PWRDebrisEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_lwr_debris"));
		}

		if(LeafiaFeatures.elevators) {

			/*
			 * sized() is the type's declared size and ElevatorEntity overrides getDimensions to
			 * return a different height per side - 2.5 on the server, 0.1 on the client, which is
			 * upstream's own trick for keeping a player inside the cabin rather than on top of it.
			 * The server's is declared here, because that is the one that decides collision.
			 */
			ELEVATOR = ENTITY_TYPES.register("entity_elevator", () -> EntityType.Builder
					.<ElevatorEntity>of(ElevatorEntity::new, MobCategory.MISC)
					.sized(30 / 16f, 2.5f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_elevator"));

			/*
			 * Upstream never calls setSize on the counterweight, so it keeps 1.12.2 Entity's
			 * default 0.6 x 1.8. Declared explicitly here because 1.21.1 has no such default -
			 * EntityType.Builder's own is 0.6 x 1.8, the same numbers, but stating them is what
			 * makes the match checkable.
			 */
			ELEVATOR_WEIGHT = ENTITY_TYPES.register("entity_counterweight", () -> EntityType.Builder
					.<EvWeightEntity>of(EvWeightEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(63)
					.updateInterval(1)
					.build("entity_counterweight"));
		}

		if(LeafiaFeatures.pinkDoor) {

			/*
			 * NTM-CE's EntityRainbow, registered in the addon's namespace (D-3030 #4, D-3031): the only thing that fires one
			 * is the pink door's ExplosionChaos.zomg. A quarter-block box (a beam has no body to speak of); tracked as far as
			 * the base mod's bullets, every tick, since it flies straight and fast.
			 */
			RAINBOW = ENTITY_TYPES.register("entity_rainbow", () -> EntityType.Builder
					.<com.leafia.contents.effects.zomg.EntityRainbow>of(com.leafia.contents.effects.zomg.EntityRainbow::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build("entity_rainbow"));
		}

		ENTITY_TYPES.register(modBus);
	}
}
