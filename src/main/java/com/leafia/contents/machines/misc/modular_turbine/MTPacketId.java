package com.leafia.contents.machines.misc.modular_turbine;

/**
 * The keys the modular turbine's block entities sync under.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/MTPacketId.java}.
 *
 * <h2>The ids repeat, and that is deliberate</h2>
 *
 * {@code PORT_IDENTIFIER} and {@code CORE_ASSEMBLY_SYNC} are both <b>0</b>, and the two runs
 * carry on overlapping. They are not one numbering: a {@code LeafiaPacket} key is scoped to the
 * <em>block entity it is sent from</em> (D-1025 — five bits of key, three of type, no sender id
 * on the wire), so the port's keys and the core's keys never meet. Renumbering them into one
 * sequence would be a silent behaviour change on both.
 *
 * <p>So this enum is two tables sharing a type, and the {@code id} is what goes on the wire
 * rather than the ordinal. Reading {@code ordinal()} here would be a bug that looks like a
 * tidy-up.
 */
public enum MTPacketId {

	/// Sent by a port: which steam the player pointed a fluid identifier at.
	PORT_IDENTIFIER(0),
	/// Sent by a port: whether it decompresses. Upstream's toggle is commented out at its only
	/// call site — "why would you want to disable decompression" — so nothing writes this yet.
	PORT_DECOMPRESSION(1),

	/// Sent by the core, and a separate numbering from the two above.
	CORE_ASSEMBLY_SYNC(0),
	CORE_STEAM_SYNC(1),
	CORE_TURBULENCE_REASONS(2),
	CORE_WEIGHT(3),
	CORE_TURBULENCE(4),
	CORE_GENERATION(5),
	CORE_GLOBAL_GEAR(6),
	CORE_RPS(7),
	CORE_OVERDRIVE(8),
	;

	/** The key on the wire. <b>Not</b> {@link #ordinal()} — see the class doc. */
	public final int id;

	MTPacketId(int id) {
		this.id = id;
	}
}
