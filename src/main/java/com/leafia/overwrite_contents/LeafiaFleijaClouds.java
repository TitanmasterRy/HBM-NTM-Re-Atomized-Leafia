package com.leafia.overwrite_contents;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.entity.effect.EntityCloudFleijaRainbow;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;

/**
 * The F.L.E.I.J.A. clouds, the addon's way: upstream's {@code MixinEntityCloudFleija} and
 * {@code MixinEntityCloudFleijaRainbow} (two near-identical {@code @Overwrite}s of the cloud's tick) and
 * {@code IMixinEntityCloudFleija} (the accessors they added), here one {@link EntityCloudFleija.TickDelegate}
 * registered on both cloud classes (D-2197), live while {@code features.overhauls.folkvangr} is on.
 *
 * <h2>What the cloud does instead</h2>
 *
 * <ul>
 * <li><b>It grows at the Folkvangr's pace.</b> One block a tick while unbound; bound to an
 *     {@link EntityNukeFolkvangr}, by {@link EntityNukeFolkvangr#getPreferredSpeedMultiplier} of the chunk
 *     ring its edge is in, {@code min(16 / (ring's chunks / 2), 1)}, so the eraser behind it handles about
 *     two chunks a tick.</li>
 * <li><b>It does not die at its maximum age.</b> On reaching it the cloud is marked finished and lives
 *     {@code ceil(10 + sqrt(max)) + 10} ticks more, which is how long the renderer's black shrinking sphere
 *     takes; before then a (visual-only) lightning bolt strikes 200 above it every tick.</li>
 * <li><b>It binds.</b> Every server tick it is unbound, it looks through the Folkvangrs waiting for a cloud
 *     and takes the first within 1.5 blocks. A player holding the debug wand ({@code wand_d}) is told each
 *     distance it measured, and "Bound".</li>
 * <li><b>It measures the server's tick rate</b> from the wall clock, {@code 50 ms / elapsed}, averaged over
 *     three ticks and synced, which the renderer multiplies the partial tick by.</li>
 * <li><b>On the client</b> it sheds {@code ParticleFleijaCloud}s (four rings when it appears, one more one
 *     tick in sixteen), and an antischrabidium cloud throws two kinds of ring particle in its first ticks.
 *     The one caller of {@code setAntischrab} is upstream's {@code MixinItemCell}: the cloud an anti-schrabidium cell
 *     spawns when it lands is marked ({@link #ANTISCHRAB_CELL}, D-3041), which is what reaches that branch.</li>
 * </ul>
 *
 * <h2>Where the state lives</h2>
 *
 * Upstream's four synced parameters ({@code SCALE}, {@code TICKRATE}, {@code ANTISCHRAB}, {@code FINISHED};
 * the rainbow cloud has no {@code ANTISCHRAB}) are keys in the cloud's synced extra-data tag (D-2197), all
 * written in one {@code setExtraData} a tick, as upstream set its scale every tick. The cloud's own scale
 * is the base class's field, which the base mod saves. Upstream's {@code @Unique} fields - the bound
 * Folkvangr, the finish timer, the clock, the client's counters - were never saved, and here they sit in a
 * weak per-entity map on each side, which is the same lifetime.
 *
 * <p>Dropped: {@code CompatibilityConfig.isWarDim}, which discards a cloud outside a war dimension. The base
 * mod has no such config and every dimension is one by default upstream. The constructor-time bind attempt
 * is the first tick's: upstream's ran before the spawner set the position, so it measured from the origin.
 */
public final class LeafiaFleijaClouds {

	private LeafiaFleijaClouds() {}

	public static final String SCALE = "leafia_scale";
	public static final String TICKRATE = "leafia_tickrate";
	public static final String ANTISCHRAB = "leafia_antischrab";
	public static final String FINISHED = "leafia_finished";

	/** How close a waiting Folkvangr has to be to be bound. */
	public static final double BIND_RANGE = 1.5;
	/** The bolt strikes this far above the cloud. */
	public static final int LIGHTNING_HEIGHT = 200;
	/** Upstream's clock period: millis are taken modulo this. */
	public static final long CLOCK_PERIOD = 10000L;

	/** Per-entity state, upstream's {@code @Unique} fields. */
	public static final class CloudState {
		public float tickrate = 1;
		public float tickrate1 = 1;
		public float tickrate2 = 1;
		public EntityNukeFolkvangr bound = null;
		public boolean isAntischrab;
		public int remoteTicks = 0;
		public int lastMillis = -1;
		public int didInitialEffects = 0;
		public int finishTimer = 0;
		/** Client: the four initial rings have been shed. Upstream did that in {@code entityInit}. */
		public boolean spawnedInitial = false;
	}

	private static final Map<Entity, CloudState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	public static CloudState state(Entity cloud) {
		return STATES.computeIfAbsent(cloud, c -> new CloudState());
	}

	public static boolean isCloud(Entity entity) {
		return entity instanceof EntityCloudFleija || entity instanceof EntityCloudFleijaRainbow;
	}

	public static boolean isRainbow(Entity entity) {
		return entity instanceof EntityCloudFleijaRainbow;
	}

	// ------------------------------------------------------------------ the base classes, as one

	public static float scaleField(Entity cloud) {
		if(cloud instanceof EntityCloudFleija f) return f.scale;
		if(cloud instanceof EntityCloudFleijaRainbow r) return r.scale;
		return 0;
	}

	static void setScaleField(Entity cloud, float value) {
		if(cloud instanceof EntityCloudFleija f) f.scale = value;
		else if(cloud instanceof EntityCloudFleijaRainbow r) r.scale = value;
	}

	public static int getMaxAge(Entity cloud) {
		if(cloud instanceof EntityCloudFleija f) return f.getMaxAge();
		if(cloud instanceof EntityCloudFleijaRainbow r) return r.getMaxAge();
		return 0;
	}

	static CompoundTag extra(Entity cloud) {
		if(cloud instanceof EntityCloudFleija f) return f.getExtraData();
		if(cloud instanceof EntityCloudFleijaRainbow r) return r.getExtraData();
		return new CompoundTag();
	}

	static void setExtra(Entity cloud, CompoundTag tag) {
		if(cloud instanceof EntityCloudFleija f) f.setExtraData(tag);
		else if(cloud instanceof EntityCloudFleijaRainbow r) r.setExtraData(tag);
	}

	// ------------------------------------------------------------------ IMixinEntityCloudFleija

	public static boolean getIsAntischrab(Entity cloud) {
		return state(cloud).isAntischrab;
	}

	/** Upstream's {@code setAntischrab}: synced on the F.L.E.I.J.A. cloud, not on the rainbow one. */
	public static Entity setAntischrab(Entity cloud) {
		state(cloud).isAntischrab = true;
		if(!isRainbow(cloud)) {
			CompoundTag tag = extra(cloud);
			tag.putBoolean(ANTISCHRAB, true);
			setExtra(cloud, tag);
		}
		return cloud;
	}

	public static float getScale(Entity cloud) {
		return scaleField(cloud);
	}

	public static void setScale(Entity cloud, float value) {
		setScaleField(cloud, value);
	}

	public static EntityNukeFolkvangr getBound(Entity cloud) {
		return state(cloud).bound;
	}

	public static void setBound(Entity cloud, EntityNukeFolkvangr value) {
		state(cloud).bound = value;
	}

	public static float getTickrate(Entity cloud) {
		return state(cloud).tickrate;
	}

	public static boolean isFinished(Entity cloud) {
		return extra(cloud).getBoolean(FINISHED);
	}

	public static int getMaxSize(Entity cloud) {
		return getMaxAge(cloud);
	}

	// ------------------------------------------------------------------ the tick

	public static final EntityCloudFleija.TickDelegate DELEGATE = LeafiaFleijaClouds::tick;

	/** Set by the client: sheds the particles. A no-op on a dedicated server. */
	public static Consumer<Entity> clientTick = cloud -> { };

	public static boolean tick(Entity cloud) {
		if(!LeafiaFeatures.folkvangr || !isCloud(cloud)) return false;
		cloud.baseTick();
		if(cloud.level().isClientSide) {
			remoteUpdate(cloud);
		} else {
			serverUpdate(cloud);
		}
		return true;
	}

	/** Upstream's {@code remoteUpdate}, without the particles, which {@link #clientTick} sheds. */
	static void remoteUpdate(Entity cloud) {
		CloudState st = state(cloud);
		CompoundTag tag = extra(cloud);
		if(!isRainbow(cloud)) st.isAntischrab = tag.getBoolean(ANTISCHRAB);
		st.tickrate = tag.contains(TICKRATE) ? tag.getFloat(TICKRATE) : 1.0F;
		st.remoteTicks += st.tickrate;
		float getSc = tag.getFloat(SCALE);
		if(getSc > scaleField(cloud)) st.remoteTicks = 0;
		setScaleField(cloud, getSc);
		clientTick.accept(cloud);
	}

	static void serverUpdate(Entity cloud) {
		CloudState st = state(cloud);
		CompoundTag tag = extra(cloud);

		if(cloud.isAlive() && st.bound == null) tryBindAuto(cloud, st);

		int max = getMaxAge(cloud);
		float scale = scaleField(cloud);

		if(scale >= max) {
			tag.putBoolean(FINISHED, true);
			st.finishTimer++;
			if(st.finishTimer > finishTicks(max)) cloud.discard();
		} else {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(cloud.level());
			if(bolt != null) {
				bolt.setPos(cloud.getX(), cloud.getY() + LIGHTNING_HEIGHT, cloud.getZ());
				bolt.setVisualOnly(true);
				cloud.level().addFreshEntity(bolt);
			}
		}

		if(st.bound == null) scale++;
		else scale += EntityNukeFolkvangr.getPreferredSpeedMultiplier((short) Math.ceil(scale / 16));
		setScaleField(cloud, scale);
		tag.putFloat(SCALE, scale);

		int millis = (int) Math.floorMod(System.currentTimeMillis(), CLOCK_PERIOD);
		if(st.lastMillis >= 0) {
			int elapsed = Math.floorMod(millis - st.lastMillis, (int) CLOCK_PERIOD);
			st.tickrate2 = st.tickrate1;
			st.tickrate1 = st.tickrate;
			st.tickrate = 50.0f / elapsed;
			tag.putFloat(TICKRATE, (st.tickrate + st.tickrate1 + st.tickrate2) / 3);
		}
		st.lastMillis = millis;

		setExtra(cloud, tag);
	}

	/** How long a finished cloud lingers: {@code ceil(10 + sqrt(max)) + 10} ticks. */
	public static int finishTicks(int max) {
		return (int) Math.ceil(10 + Math.pow(max, 0.5)) + 10;
	}

	/** Upstream's {@code tryBindAuto}. */
	static void tryBindAuto(Entity cloud, CloudState st) {
		for(EntityNukeFolkvangr folkvangr : EntityNukeFolkvangr.awaitingBind) {
			double distance = folkvangr.position().distanceTo(cloud.position());
			for(Player player : cloud.level().players()) {
				if(player.getMainHandItem().is(ModItems.wand_d.get()))
					player.sendSystemMessage(Component.literal("Distance " + distance).withStyle(ChatFormatting.YELLOW));
			}
			if(distance <= BIND_RANGE) {
				for(Player player : cloud.level().players()) {
					if(player.getMainHandItem().is(ModItems.wand_d.get()))
						player.sendSystemMessage(Component.literal("Bound").withStyle(ChatFormatting.YELLOW));
				}
				folkvangr.cloudBound = cloud;
				EntityNukeFolkvangr.awaitingBind.remove(folkvangr);
				st.bound = folkvangr;
				break;
			}
		}
	}

	private static boolean installed = false;

	public static void install() {
		if(installed) return;
		installed = true;
		EntityCloudFleija.addTickDelegate(DELEGATE);
		EntityCloudFleijaRainbow.addTickDelegate(DELEGATE);
		com.hbm.items.special.ItemDrop.addSpawnHook(ANTISCHRAB_CELL);
	}

	/**
	 * Upstream's {@code MixinItemCell}: the cloud an anti-schrabidium cell spawns when it lands is marked antischrabidium, just
	 * before it is added to the world - the only caller {@code setAntischrab} ever had, so without it the cloud's ring particles and
	 * the antischrab rings of {@code FolkvangrClient} are unreachable. The base's spawn seam (D-2207) asks for every entity a landed
	 * drop spawns; only the cell's cloud is marked, and it is added as the base adds it (the hook answers false). Behind the same
	 * switch as the cloud's tick, because the mark is only read by the addon's cloud (D-3041).
	 */
	public static final com.hbm.items.special.ItemDrop.SpawnHook ANTISCHRAB_CELL = (drop, stack, spawned) -> {
		if(LeafiaFeatures.folkvangr && spawned instanceof EntityCloudFleija && stack.is(ModItems.cell_anti_schrabidium.get()))
			setAntischrab(spawned);
		return false;
	};
}
