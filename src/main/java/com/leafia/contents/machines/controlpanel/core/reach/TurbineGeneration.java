package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.function.ToLongFunction;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Leafia's 20-tick mean of a turbine's generation, kept on the adapter side (owner decision 2026-10-17, D-3072; it replaces D-3069's
 * "the last tick's value" divergence).
 *
 * <p>Leafia's turbine mixins kept {@code long[20] generateds} and an index inside the machine, zeroed one slot at the start of
 * every update and filled when the update added energy, and answered {@code sum / 20f}. The base mod gives the machine no such
 * history, only {@code lastTickGeneration} (D-2275). The adapter is created per lookup and cannot hold it, so the ring lives
 * here, <b>per level and per position</b>, and is fed once per tick at {@code LevelTickEvent.Post} (after the machine's update, so
 * the value is the tick's own) from the same seam.
 *
 * <ul>
 * <li>A machine is <b>tracked from the first time a panel looks it up</b> ({@link #track}); until then it has no history, and the
 * mean starts from zeros exactly as a freshly placed Leafia machine's did. A panel that polls it every tick therefore sees the
 * mean from the tick after it linked.</li>
 * <li>Sampled <b>at most once per game tick</b> (the tick is remembered), so a test or a second caller cannot double a sample.</li>
 * <li>A removed block entity (broken, or its chunk unloaded) is <b>dropped at the next sample</b>, and so is a whole level when it
 * unloads. A turbine placed again at the same position starts a fresh ring.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class TurbineGeneration {

	/** Leafia's window. */
	public static final int WINDOW = 20;

	private TurbineGeneration() {}

	private static final class Ring {
		final BlockEntity be;
		final ToLongFunction<BlockEntity> source;
		final long[] samples = new long[WINDOW];
		int index = 0;
		long lastTick = Long.MIN_VALUE;

		Ring(BlockEntity be, ToLongFunction<BlockEntity> source) {
			this.be = be;
			this.source = source;
		}

		float mean() {
			float sum = 0;
			for(long s : samples)
				sum += s;
			return sum / WINDOW;
		}
	}

	private static final Reference2ObjectOpenHashMap<Level, Long2ObjectOpenHashMap<Ring>> RINGS = new Reference2ObjectOpenHashMap<>();

	@SuppressWarnings("unchecked")
	public static synchronized <T extends BlockEntity> void track(T be, ToLongFunction<T> generation) {
		Level level = be.getLevel();
		if(level == null || level.isClientSide)
			return;
		Long2ObjectOpenHashMap<Ring> rings = RINGS.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
		long key = be.getBlockPos().asLong();
		Ring ring = rings.get(key);
		if(ring == null || ring.be != be)
			rings.put(key, new Ring(be, (ToLongFunction<BlockEntity>) generation));
	}

	/** The mean of the last {@link #WINDOW} samples; zero for a machine that has not been tracked yet. */
	public static synchronized float mean(BlockEntity be) {
		Level level = be.getLevel();
		Long2ObjectOpenHashMap<Ring> rings = level == null ? null : RINGS.get(level);
		Ring ring = rings == null ? null : rings.get(be.getBlockPos().asLong());
		return ring == null || ring.be != be ? 0F : ring.mean();
	}

	/** How many machines are tracked in this level, for tests. */
	public static synchronized int tracked(Level level) {
		Long2ObjectOpenHashMap<Ring> rings = RINGS.get(level);
		return rings == null ? 0 : rings.size();
	}

	/**
	 * One sample of every tracked machine in this level: exactly what {@code LevelTickEvent.Post} runs. A machine already sampled
	 * this game tick is skipped; a removed one is forgotten.
	 */
	public static synchronized void sample(Level level) {
		Long2ObjectOpenHashMap<Ring> rings = RINGS.get(level);
		if(rings == null)
			return;
		long now = level.getGameTime();
		ObjectIterator<Ring> it = rings.values().iterator();
		while(it.hasNext()) {
			Ring ring = it.next();
			if(ring.be.isRemoved() || ring.be.getLevel() != level) {
				it.remove();
				continue;
			}
			if(ring.lastTick == now)
				continue;
			ring.lastTick = now;
			ring.index = Math.floorMod(ring.index + 1, WINDOW);
			ring.samples[ring.index] = ring.source.applyAsLong(ring.be);
		}
		if(rings.isEmpty())
			RINGS.remove(level);
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post evt) {
		Level level = evt.getLevel();
		if(level.isClientSide || !LeafiaFeatures.controlPanel)
			return;
		sample(level);
	}

	@SubscribeEvent
	public static synchronized void onLevelUnload(LevelEvent.Unload evt) {
		if(evt.getLevel() instanceof Level level)
			RINGS.remove(level);
	}
}
