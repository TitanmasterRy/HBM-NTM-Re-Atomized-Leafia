package com.leafia.dev.optimization.diagnosis;

/**
 * How many bytes the addon's packets have moved. Upstream's {@code RecordablePacket} counters.
 *
 * <p>Upstream: {@code com/leafia/dev/optimization/diagnosis/RecordablePacket.java}, an abstract
 * class between {@code LeafiaCustomPacket} and NTM-CE's {@code ThreadedPacket} whose entire body
 * is (a) the bit-length framing and (b) these six counters. <b>Both halves went somewhere else
 * here and the class does not survive</b>, so the upstream file is counted as ported
 * against this file:
 *
 * <ul>
 *   <li>The framing — write the bit count, then the payload; read the bit count, then that many
 *       bytes — is what a {@link net.minecraft.network.codec.StreamCodec} is, so it became
 *       {@code LeafiaCustomPacket.STREAM_CODEC}. The zero-copy branch upstream added to
 *       {@code fromBytes} goes with it: {@code ByteBufCodecs.byteArray} already hands over an
 *       array, so there is nothing to alias.</li>
 *   <li>{@code ThreadedPacket}, the superclass, is not needed at all on this port (D-062) and a
 *       {@code CustomPacketPayload} is a value NeoForge encodes by itself.</li>
 *   <li>These counters have nowhere else to be, so they are here.</li>
 * </ul>
 *
 * <h2>The counters are upstream's and nothing reads them yet</h2>
 *
 * Six doubles: a running total plus per-second and per-minute accumulators, each with a
 * {@code previous} that a display would read after {@link #rollSecond()} or {@link #rollMinute()}
 * moved it. Upstream's own reader is its debug overlay, which is unported — so these count
 * correctly and nobody looks. Kept rather than dropped because they are the measurement behind
 * {@code LeafiaBuf} existing at all: the addon bit-packs its wire format to make these numbers
 * small, and deleting the numbers would leave the reason unevidenced.
 *
 * <p>They are plain fields with no synchronisation, exactly as upstream has them. Encoding can
 * happen on a network thread, so a concurrent write can be lost — which for a diagnostic counter
 * costs an undercount and nothing else. Named here so that a later reader does not mistake it for
 * an oversight.
 */
public final class LeafiaPacketStats {

	private LeafiaPacketStats() {}

	public static double bytesUsage = 0;
	public static double previousByteUsage = 0;
	public static double bytesUsageSec = 0;
	public static double previousByteUsageSec = 0;
	public static double bytesUsageMin = 0;
	public static double previousByteUsageMin = 0;

	/** Counts one packet, encoded or decoded — upstream counts both directions into one total. */
	public static void record(int bytes) {
		bytesUsage += bytes;
		bytesUsageSec += bytes;
		bytesUsageMin += bytes;
	}

	/** Moves the second's tally into {@link #previousByteUsageSec} and starts a new one. */
	public static void rollSecond() {
		previousByteUsageSec = bytesUsageSec;
		bytesUsageSec = 0;
	}

	/** As {@link #rollSecond()}, for the minute; also snapshots the running total. */
	public static void rollMinute() {
		previousByteUsageMin = bytesUsageMin;
		bytesUsageMin = 0;
		previousByteUsage = bytesUsage;
	}

	/** For tests, and for anything that wants a clean slate. */
	public static void reset() {
		bytesUsage = previousByteUsage = 0;
		bytesUsageSec = previousByteUsageSec = 0;
		bytesUsageMin = previousByteUsageMin = 0;
	}
}
