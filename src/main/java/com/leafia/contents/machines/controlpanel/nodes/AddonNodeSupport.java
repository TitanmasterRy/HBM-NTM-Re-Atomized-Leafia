package com.leafia.contents.machines.controlpanel.nodes;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.lib.LeafiaRef;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What Leafia's nodes share (CCP-LEAFIA-NODES, D-3070): the world a node's control lives in, and the set of clocks that have
 * already counted this tick.
 *
 * <p><b>The world.</b> Leafia's nodes took a {@code World} in their constructor, read as
 * {@code nodeSystem.parent.panel.parent.getControlWorld()} at the loader and the menu. Here they take the {@link Control} and ask
 * for the world when they need it, so a node built before its panel is attached (a loader reads the graph first) works once it is,
 * and a node with no panel simply has no world and does nothing. That world is the one reach into the level the random and radio
 * nodes have; neither reads a block.
 *
 * <p><b>The clock's tick.</b> Leafia's {@code LeafiaPassiveServer.tickedNodes} was cleared at the start of the server tick
 * ({@code priorTick}) and {@code NodeClock} counted once per tick however many times its graph was evaluated. That set is
 * cleared here on {@link ServerTickEvent.Pre}, the same phase.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class AddonNodeSupport {

	private static final Set<Node> TICKED = ConcurrentHashMap.newKeySet();

	/**
	 * The longest text a Leafia node makes. Leafia's string nodes had no ceiling, and one that feeds both its inputs from one
	 * source doubles the text at every link: a chain of thirty (the guard allows 128) is a gigabyte on the server thread. 4096 is
	 * far past anything a readout shows; longer text is cut, not refused, so the panel still reads (D-3070).
	 */
	public static final int MAX_TEXT = 4096;

	private AddonNodeSupport() {}

	/** {@code s} cut to {@link #MAX_TEXT} characters. */
	public static String clip(String s) {
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
	}

	/** The level the control's panel stands in, or null if it has no panel or its host has no level. */
	public static Level levelOf(Control ctrl) {
		if(ctrl == null || ctrl.panel == null || ctrl.panel.parent == null)
			return null;
		return ctrl.panel.parent.getControlWorld();
	}

	/** True the first time it is asked of this node in a server tick, false for every later ask until the next tick begins. */
	public static boolean firstThisTick(Node node) {
		return TICKED.add(node);
	}

	/** Starts a new tick for {@link #firstThisTick}. The server tick does it; a test that has no tick between two evaluations calls it. */
	public static void newTick() {
		TICKED.clear();
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Pre event) {
		TICKED.clear();
	}
}
