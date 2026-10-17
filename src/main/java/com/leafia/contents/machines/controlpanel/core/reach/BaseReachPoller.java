package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Objects;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.lib.LeafiaRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The out-events of base-mod machines (batch 8, D-3068).
 *
 * <p>NTM-CE's door, tank and crane call {@code broadcastToSubscribed} from inside their own update. A base-mod machine cannot,
 * so this <b>polls only the positions something is subscribed to</b>, at {@code LevelTickEvent.Post}: each adapter that is
 * {@link ReachAdapter#polled()} gives a snapshot, the previous one is kept here, and a difference is broadcast to the subscribers.
 * Cost scales with subscriptions, never with the world.
 *
 * <h2>Divergences from CE, recorded rather than hidden</h2>
 * <ol>
 * <li>An event arrives at the <b>end of the tick</b>, not in the middle of the machine's update.</li>
 * <li>Two changes inside one tick <b>merge</b> into the last state; a door that opened and closed between two polls says nothing.</li>
 * <li>The first poll after a subscription (or after the target's chunk loaded) only <b>records a baseline</b> and sends nothing,
 * where CE's machine would have announced its next change whenever it came. A target that changed while its chunk was unloaded
 * is therefore not announced on load.</li>
 * <li>A target in an unloaded chunk is never polled and never loads one ({@link ControlEventSystem#resolve}).</li>
 * </ol>
 *
 * <p>Runtime state only, one map per level, dropped on {@code LevelEvent.Unload}.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class BaseReachPoller {

	private static final Reference2ObjectOpenHashMap<Level, Long2ObjectOpenHashMap<Object>> BASELINES = new Reference2ObjectOpenHashMap<>();

	private BaseReachPoller() {}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post evt) {
		Level level = evt.getLevel();
		if(level.isClientSide || !LeafiaFeatures.controlPanel)
			return;
		poll(level);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload evt) {
		if(evt.getLevel() instanceof Level level) {
			synchronized(BaseReachPoller.class) {
				BASELINES.remove(level);
			}
		}
	}

	private static synchronized Long2ObjectOpenHashMap<Object> baselines(Level level) {
		return BASELINES.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
	}

	/** How many targets have a baseline in this level, for tests. */
	public static synchronized int watched(Level level) {
		Long2ObjectOpenHashMap<Object> m = BASELINES.get(level);
		return m == null ? 0 : m.size();
	}

	/** One poll of this level, exactly what {@code LevelTickEvent.Post} runs. Public so a test can step it without waiting for a tick. */
	public static void poll(Level level) {
		ControlEventSystem sys = ControlEventSystem.getIfPresent(level);
		Long2ObjectOpenHashMap<Object> baselines = baselines(level);
		if(sys == null) {
			baselines.clear();
			return;
		}

		long[] subscribed = sys.subscribedPositions();
		LongOpenHashSet live = new LongOpenHashSet(subscribed);
		baselines.keySet().removeIf(k -> !live.contains(k));

		for(long key : subscribed) {
			IControllable c = sys.resolve(BlockPos.of(key));
			if(!(c instanceof ReachAdapter<?> adapter) || !adapter.polled()) {
				baselines.remove(key);
				continue;
			}
			Object now = adapter.snapshot();
			if(!baselines.containsKey(key)) {
				baselines.put(key, now);
				continue;
			}
			Object prev = baselines.put(key, now);
			if(!Objects.equals(prev, now)) {
				for(ControlEvent evt : adapter.diff(prev, now))
					sys.broadcastToSubscribed(adapter, evt);
			}
		}
	}

	/**
	 * Records the adapter's machine as being at its current snapshot, if it is being watched. Called after an echo so the poll at
	 * the end of the tick does not announce the same change again.
	 */
	static void rebase(ReachAdapter<?> adapter) {
		Level level = adapter.be.getLevel();
		if(level == null)
			return;
		Long2ObjectOpenHashMap<Object> baselines = baselines(level);
		long key = adapter.getControlPos().asLong();
		if(baselines.containsKey(key))
			baselines.put(key, adapter.snapshot());
	}
}
