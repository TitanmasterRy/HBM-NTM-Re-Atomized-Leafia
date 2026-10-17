package com.leafia.dev.custompacket;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One of the addon's packets: how it writes itself, and what it does when it arrives.
 *
 * <p>Upstream: {@code com/leafia/dev/custompacket/LeafiaCustomPacketEncoder.java}, and the whole
 * translation is one type. {@code decode} returns a {@link Consumer} of 1.12.2's
 * {@code MessageContext} there and of {@link IPayloadContext} here.
 *
 * <h2>Why decode returns a callback instead of doing the work</h2>
 *
 * This is upstream's shape and it is the right one in 1.21.1 for a reason 1.12.2 shared: reading
 * happens on the network thread and acting must happen on the game thread.
 * {@link LeafiaCustomPacket#handle} calls {@code decode} where the bytes arrive and hands the
 * returned callback to {@code context.enqueueWork}, so a decoder <b>must read every value it
 * needs eagerly and capture them</b> — a callback that keeps the {@link LeafiaBuf} and reads from
 * it later is reading a buffer that no longer belongs to it.
 *
 * <p>Returning null means "nothing to do", which is how a packet that only matters on one side
 * declines to act on the other.
 *
 * <h2>The instance you implement this on is a prototype</h2>
 *
 * {@link LeafiaPacketTypes} holds one instance of each implementor forever, and {@code decode} is
 * called on <em>that</em> instance rather than on a fresh one — so an implementor's fields are
 * for the sending side only, and a decoder must not write to them. Upstream has the same rule
 * (its enum constants are prototypes too) and does not say so anywhere, which is why it is said
 * here.
 */
public interface LeafiaCustomPacketEncoder {

	/** Writes this packet's contents. The type id has already been written. */
	void encode(LeafiaBuf buf);

	/**
	 * Reads this packet's contents and returns what to do with them on the game thread, or null
	 * to do nothing. Read eagerly — see the class note.
	 */
	@Nullable
	Consumer<IPayloadContext> decode(LeafiaBuf buf);

	/**
	 * Whether a client may send this packet to the server.
	 *
	 * <p>One payload type is registered both ways (see {@link LeafiaPacketDispatcher}), so as far as
	 * NeoForge is concerned any client may address any of the addon's packets to a server. Upstream
	 * has no such question: 1.12.2 registers each message in one direction. This answers it, and the
	 * answer is <b>no</b> unless a packet says otherwise - the ones that say so are the client's
	 * requests (the elevator's, the radio's, the fuzzy identifier's, the advisor's), each of which
	 * validates what it was sent (D-1094). {@link LeafiaCustomPacket#handle} drops every other
	 * packet arriving serverbound before it is decoded.
	 */
	default boolean acceptsServerbound() {
		return false;
	}
}
