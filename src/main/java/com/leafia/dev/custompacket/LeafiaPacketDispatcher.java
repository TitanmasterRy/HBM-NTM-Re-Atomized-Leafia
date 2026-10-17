package com.leafia.dev.custompacket;

import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.optimization.LeafiaParticlePacket;
import com.leafia.lib.LeafiaRef;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Puts the addon's payloads on the network: the packet hub and the block-entity sync in both
 * directions, and the particle spawner (D-3000) from server to client.
 *
 * <p>The base mod's {@code PacketDispatcher}, at addon scale: it registers three payloads and one
 * per direction, and this registers one that goes both ways. Upstream has no equivalent class —
 * 1.12.2's SimpleImpl registers each message with a direction and a discriminator at mod init, in
 * {@code LeafiaMod}'s own preInit — so this is where that call went rather than a file being
 * ported.
 *
 * <h2>Why bidirectional, and what it costs</h2>
 *
 * Upstream's forty packets go both ways — the elevator syncs to clients, its radio requests from
 * them — and they share one payload type here, so that type has to be registered both ways. The
 * cost is real and named in {@code LeafiaCustomPacket.SyncVelocityPacket}: NeoForge will no
 * longer refuse a clientbound-only packet arriving at a server, because as far as it is concerned
 * there is one packet and it is legal in both directions. Each encoder is responsible for not
 * touching a side it is not on.
 *
 * <h2>The protocol version</h2>
 *
 * Deliberately separate from the base mod's, and bumped for a different reason: this one changes
 * when the <em>payload's</em> wire shape changes, not when a packet is added. Adding a packet is
 * already caught by the type count that {@link LeafiaPacketTypes} carries inside every message,
 * which is upstream's own mechanism and gives a better error than a refused connection — it names
 * how many types each side knows about.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaPacketDispatcher {

	private LeafiaPacketDispatcher() {}

	/** Bumped when {@link LeafiaCustomPacket}'s own framing changes, not when a packet is added. */
	private static final String PROTOCOL_VERSION = "1";

	@SubscribeEvent
	public static void registerPayloads(RegisterPayloadHandlersEvent event) {

		PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

		registrar.playBidirectional(LeafiaCustomPacket.TYPE, LeafiaCustomPacket.STREAM_CODEC,
				LeafiaCustomPacket::handle);

		// The block-entity sync packet, which goes both ways for a different reason: clients ask
		// the server to bring them up to date with an empty one (D-1025's _validate).
		registrar.playBidirectional(LeafiaPacket.TYPE, LeafiaPacket.STREAM_CODEC, LeafiaPacket::handle);

		// The particle spawner (D-3000): server to client only, with its own kind count in the header
		// for the same reason the hub has one.
		registrar.playToClient(LeafiaParticlePacket.TYPE, LeafiaParticlePacket.STREAM_CODEC,
				LeafiaParticlePacket::handle);

		// The Custom Control Panel's variable deltas and whole-panel updates (D-3063): server to client only, so a client can
		// never send one. Always registered, as the hub is, so the payload set does not depend on a feature switch; the handler
		// does nothing for a position with no panel.
		registrar.playToClient(com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket.TYPE,
				com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket.STREAM_CODEC,
				com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket::handle);
	}
}
