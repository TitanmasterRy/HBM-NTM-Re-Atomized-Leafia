package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.leafia.LeafiaMod;
import com.leafia.lib.LeafiaRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The per-level post office of the Custom Control Panel: who is listening, who is subscribed to whom, and the per-tick
 * {@code tick} event. Adapted from NTM-CE's {@code ControlEventSystem} (D-3061), redesigned for this port, and carrying Leafia's {@code MixinControlEventSystem.tick} (which {@code @Overwrite}s it to stop unloaded
 * machines ticking) as its own behaviour.
 *
 * <h2>What changed from CE, and why</h2>
 *
 * CE kept a per-event-name index of controllables by position, filled by each machine's {@code validate} and emptied by
 * {@code invalidate}. That lifecycle leaked in 1.12.2 (a machine whose chunk unloaded kept being ticked, which is what Leafia's
 * mixin patched). Here <b>dispatch is positional and resolved on demand</b>: {@link #broadcastEvent(BlockPos, ControlEvent, BlockPos)}
 * names a position, and the target is looked up when the event fires, never earlier:
 *
 * <ol>
 * <li>if the position is <b>not loaded</b> the event is dropped. A panel never loads a chunk; the same rule as the base's
 * {@code NBTControlPacket};</li>
 * <li>a block entity there that is itself an {@link IControllable} receives it;</li>
 * <li>otherwise the {@link #CONTROLLABLE} capability is asked, which is how an adapter over a base-mod machine answers;</li>
 * <li>otherwise a controllable that registered itself at that position with {@link #addControllable} (a target that is not a
 * block entity);</li>
 * <li>and last, the target's own {@link IControllable#getInEvents()} must name the event, as CE's index implied.</li>
 * </ol>
 *
 * <p>Registration therefore only matters for <b>{@code tick}</b> (the registered set the level tick walks) and for targets that
 * are not block entities. A subscription ({@link #subscribeTo}) is a position to a set of subscribers and, as in CE, <b>survives
 * the target unloading and reloading</b> on purpose: it is dropped only with the subscriber ({@link #removeControllable}).
 *
 * <p>One difference a pack maker could see: an exception thrown by a receiver is logged and swallowed per delivery, where CE let
 * it escape through the sender's tick. One bad panel should not stop a server (recorded in D-3061).
 *
 * <p>The system is runtime state only: nothing here is saved. Each panel rebuilds its subscriptions from its links when it loads.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public class ControlEventSystem {

	/**
	 * The capability a base-mod machine is reached through. A provider returns an {@link IControllable} adapter for a block
	 * entity; the addon registers providers in {@code LeafiaCapabilities} (batch 8). Void context: a panel addresses a
	 * position, not a face.
	 */
	public static final BlockCapability<IControllable, Void> CONTROLLABLE =
			BlockCapability.createVoid(LeafiaRef.rl("controllable"), IControllable.class);

	private static final Reference2ObjectOpenHashMap<Level, ControlEventSystem> SYSTEMS = new Reference2ObjectOpenHashMap<>();

	private final Level level;
	private final ReferenceOpenHashSet<IControllable> allControllables = new ReferenceOpenHashSet<>();
	private final ReferenceOpenHashSet<IControllable> tickables = new ReferenceOpenHashSet<>();
	private final Long2ObjectOpenHashMap<IControllable> registeredAt = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<ReferenceOpenHashSet<IControllable>> positionSubscriptions = new Long2ObjectOpenHashMap<>();

	private ControlEventSystem(Level level) {
		this.level = level;
	}

	public Level getLevel() {
		return level;
	}

	// ------------------------------------------------------------------ registration

	public void addControllable(IControllable c) {
		if(allControllables.contains(c))
			return;
		if(c.getInEvents().contains("tick"))
			tickables.add(c);
		registeredAt.put(c.getControlPos().asLong(), c);
		allControllables.add(c);
	}

	/**
	 * Forgets a controllable: its {@code tick}, its position entry, and every subscription <b>it holds as a subscriber</b>.
	 * Subscriptions <b>to</b> its position are kept, so a panel linked to a machine that unloads and reloads still hears it.
	 */
	public void removeControllable(IControllable c) {
		tickables.remove(c);
		long pos = c.getControlPos().asLong();
		if(registeredAt.get(pos) == c)
			registeredAt.remove(pos);
		for(var it = positionSubscriptions.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
			var entry = it.next();
			ReferenceOpenHashSet<IControllable> subscribers = entry.getValue();
			subscribers.remove(c);
			if(subscribers.isEmpty()) {
				it.remove();
			}
		}
		allControllables.remove(c);
	}

	public boolean isValid(IControllable c) {
		return allControllables.contains(c);
	}

	// ------------------------------------------------------------------ subscriptions

	public void subscribeTo(IControllable subscriber, IControllable target) {
		subscribeTo(subscriber, target.getControlPos());
	}

	public void subscribeTo(IControllable subscriber, BlockPos target) {
		positionSubscriptions.computeIfAbsent(target.asLong(), k -> new ReferenceOpenHashSet<>()).add(subscriber);
	}

	public void unsubscribeFrom(IControllable subscriber, IControllable target) {
		unsubscribeFrom(subscriber, target.getControlPos());
	}

	public void unsubscribeFrom(IControllable subscriber, BlockPos target) {
		long serializedTarget = target.asLong();
		ReferenceOpenHashSet<IControllable> subscribers = positionSubscriptions.get(serializedTarget);
		if(subscribers != null) {
			subscribers.remove(subscriber);
			if(subscribers.isEmpty()) {
				positionSubscriptions.remove(serializedTarget);
			}
		}
	}

	/**
	 * Every position somebody is subscribed to, as {@link BlockPos#asLong}. The out-event poller (batch 8, D-3068) walks these and
	 * nothing else, so its cost scales with subscriptions and not with the world.
	 */
	public long[] subscribedPositions() {
		return positionSubscriptions.keySet().toLongArray();
	}

	/** Whether {@code subscriber} is currently subscribed to {@code target}. */
	public boolean isSubscribed(IControllable subscriber, BlockPos target) {
		ReferenceOpenHashSet<IControllable> subscribers = positionSubscriptions.get(target.asLong());
		return subscribers != null && subscribers.contains(subscriber);
	}

	// ------------------------------------------------------------------ dispatch

	/**
	 * The target that would receive an event at this position right now, or null: nothing is loaded there, or nothing there is
	 * controllable. Never loads a chunk.
	 */
	public IControllable resolve(BlockPos pos) {
		if(!level.isLoaded(pos))
			return null;
		BlockEntity be = level.getBlockEntity(pos);
		if(be != null && !be.isRemoved()) {
			if(be instanceof IControllable c)
				return c;
			IControllable adapter = level.getCapability(CONTROLLABLE, pos);
			if(adapter != null)
				return adapter;
		}
		return registeredAt.get(pos.asLong());
	}

	/** Sends an event to the one target at {@code pos}, if it is loaded, controllable and accepts that event. */
	public void broadcastEvent(BlockPos from, ControlEvent evt, BlockPos pos) {
		IControllable c = resolve(pos);
		if(c != null && c.getInEvents().contains(evt.name))
			deliver(c, from, evt);
	}

	/** Sends an event to each position in turn. A null collection delivers to nobody: the global form CE had is not used by any caller. */
	public void broadcastEvent(BlockPos from, ControlEvent evt, Collection<BlockPos> positions) {
		if(positions == null)
			return;
		for(BlockPos pos : new ArrayList<>(positions))
			broadcastEvent(from, evt, pos);
	}

	/** Sends an event to everything subscribed to this controllable's position, as if from it. */
	public void broadcastToSubscribed(IControllable ctrl, ControlEvent evt) {
		ReferenceOpenHashSet<IControllable> subscribed = positionSubscriptions.get(ctrl.getControlPos().asLong());
		if(subscribed == null)
			return;
		BlockPos from = ctrl.getControlPos();
		for(IControllable sub : new ArrayList<>(subscribed))
			deliver(sub, from, evt);
	}

	/**
	 * How many deliveries may be nested on one stack. A panel sends to a machine that answers by sending back is two; a chain of
	 * panels is a few more. Past this the event is dropped and logged (D-3063): CE had no bound, and a pair of panels (or a panel
	 * and a machine) that echo an event to each other overflow the server's stack, which a rejected graph cannot always predict
	 * because the other end is another block.
	 */
	public static final int MAX_DELIVERY_DEPTH = 32;

	private static int deliveryDepth = 0;

	private static void deliver(IControllable c, BlockPos from, ControlEvent evt) {
		if(deliveryDepth >= MAX_DELIVERY_DEPTH) {
			LeafiaMod.LOGGER.warn("Control panel event '{}' to {} dropped: {} deliveries deep, which is a loop", evt.name, c.getControlPos(), deliveryDepth);
			return;
		}
		deliveryDepth++;
		try {
			c.receiveEvent(from, evt);
		} catch(RuntimeException e) {
			LeafiaMod.LOGGER.error("Control panel event '{}' failed at {}", evt.name, c.getControlPos(), e);
		} finally {
			deliveryDepth--;
		}
	}

	/** How many deliveries are nested right now, for tests. */
	public static int deliveryDepth() {
		return deliveryDepth;
	}

	/**
	 * Gives every registered {@code tick} target its {@code tick} event with the level's game time. A target whose position is
	 * not loaded, or whose block entity has been removed, is skipped: Leafia's {@code @Overwrite} of CE's loop, which is the
	 * reason the port resolves on demand at all.
	 */
	public void tick() {
		if(tickables.isEmpty())
			return;
		long time = level.getGameTime();
		for(IControllable c : new ArrayList<>(tickables)) {
			BlockPos pos = c.getControlPos();
			if(!level.isLoaded(pos))
				continue;
			if(c instanceof BlockEntity be && be.isRemoved())
				continue;
			deliver(c, pos, ControlEvent.newEvent("tick").setVar("time", time));
		}
	}

	/** The registered tick targets, for tests. */
	public List<IControllable> tickTargets() {
		return new ArrayList<>(tickables);
	}

	// ------------------------------------------------------------------ the level's system

	public static synchronized ControlEventSystem get(Level level) {
		return SYSTEMS.computeIfAbsent(level, ControlEventSystem::new);
	}

	/** The system for a level if one exists, without making one. */
	public static synchronized ControlEventSystem getIfPresent(Level level) {
		return SYSTEMS.get(level);
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Pre evt) {
		Level level = evt.getLevel();
		if(level.isClientSide)
			return;
		ControlEventSystem s;
		synchronized(ControlEventSystem.class) {
			s = SYSTEMS.get(level);
		}
		if(s != null)
			s.tick();
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload evt) {
		if(evt.getLevel() instanceof Level level) {
			synchronized(ControlEventSystem.class) {
				SYSTEMS.remove(level);
			}
		}
	}
}
