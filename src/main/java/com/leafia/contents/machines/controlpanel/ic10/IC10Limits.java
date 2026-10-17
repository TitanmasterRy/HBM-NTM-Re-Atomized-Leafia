package com.leafia.contents.machines.controlpanel.ic10;

import com.leafia.lib.LeafiaRef;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every ceiling IC10 runs under (CCP-IC10, D-3073).
 *
 * <p>An IC10 program is a loop on the <b>server thread</b> that a player writes, and the client that sends it is not trusted
 * (section 2.5). Leafia's interpreter already stopped an update after 128 instructions ({@link #STEPS_PER_UPDATE}); NTM-CE
 * and the plan assumed it had no bound at all, and a {@code j 0} must never hang a server. The port keeps that bound and adds
 * the ones Leafia did not need because it never ran a hostile program:
 *
 * <ul>
 * <li>a <b>per-tick pool</b> of {@link #TICK_BUDGET} instructions shared by every IC10 node in the server, so a panel
 * full of IC10 nodes (or one graph read many times in a tick) cannot run 128 instructions each. A node that finds the pool empty
 * does nothing that update and keeps its place;</li>
 * <li>the size of a program ({@link #MAX_LINES}, {@link #MAX_LINE_CHARS}, {@link #MAX_PROGRAM_BYTES}, {@link #MAX_COMPRESSED}),
 * which the editor enforces as you type, the node enforces on load and {@code ControlPanelGuard} enforces on anything a client
 * sends (through {@link IC10Program#problem});</li>
 * <li>the size of what a program can build: text ({@link #MAX_TEXT}, a doubling {@code concat} is 2^128 characters otherwise),
 * output keys, variables, chat lines.</li>
 * </ul>
 *
 * <p>The registers (0 to 64, {@code ic10MaxRegisters}) and the stack (512, {@code ic10MaxStack}) are Leafia's config values in
 * {@code LeafiaConfig}.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class IC10Limits {

	private IC10Limits() {}

	/** Lines in one program. Leafia has no limit; the plan's "512-instruction program" is this. */
	public static final int MAX_LINES = 512;
	/** Characters in one line, indent included. */
	public static final int MAX_LINE_CHARS = 256;
	/** Bytes of a program once decoded: 512 full lines would be 131,072 characters, and no program is that. */
	public static final int MAX_PROGRAM_BYTES = 65_536;
	/** Bytes of a program as it sits in a panel's NBT (gzip). */
	public static final int MAX_COMPRESSED = 16_384;
	/** Instructions one {@code update} may execute: Leafia's own loop bound. */
	public static final int STEPS_PER_UPDATE = 128;
	/** Instructions every IC10 node in the server may execute in one tick together. */
	public static final int TICK_BUDGET = 4096;
	/** The longest text a register, an output or a variable holds. */
	public static final int MAX_TEXT = 4096;
	/** Distinct keys a node's output pack may hold, and the longest key. */
	public static final int MAX_OUTPUT_KEYS = 64;
	public static final int MAX_KEY = 64;
	/** Variables a control (or the panel) may hold once a program has written to it, and the longest name. */
	public static final int MAX_VARS = 256;
	public static final int MAX_VAR_NAME = 64;
	/** Chat lines one {@code print} may send per update, and their length. */
	public static final int MAX_PRINTS = 4;
	public static final int MAX_PRINT_CHARS = 256;
	/** Longest radio frequency a program may use (the radio nodes' own bound). */
	public static final int MAX_FREQUENCY = 128;

	private static int remaining = TICK_BUDGET;

	/** Takes up to {@code want} instructions from this tick's pool; returns how many were granted (0 when it is empty). */
	public static int take(int want) {
		int granted = Math.max(0, Math.min(want, remaining));
		remaining -= granted;
		return granted;
	}

	/** Gives back what a node was granted and did not use. */
	public static void refund(int unused) {
		if(unused > 0)
			remaining = Math.min(TICK_BUDGET, remaining + unused);
	}

	/** What is left of this tick's pool. */
	public static int remaining() {
		return remaining;
	}

	/** Starts a new tick's pool. The server tick does it; a test that has no tick between two updates calls it. */
	public static void newTick() {
		remaining = TICK_BUDGET;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Pre event) {
		remaining = TICK_BUDGET;
	}

	/** {@code s} cut to {@link #MAX_TEXT} characters. */
	public static String clip(String s) {
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
	}
}
