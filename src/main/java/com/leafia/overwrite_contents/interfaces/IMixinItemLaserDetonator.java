package com.leafia.overwrite_contents.interfaces;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.overwrite_contents.detonator.LeafiaLaserDetonator;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What upstream's mixin added to the base laser detonator: {@code doDetonate}, which both the click and the pointing
 * key run, and the key's request packet.
 *
 * <p>Upstream: {@code com.leafia.overwrite_contents.interfaces.IMixinItemLaserDetonator}. The base item cannot be
 * given an interface, so {@code leafia$doDetonate} is the static {@link LeafiaLaserDetonator#doDetonate}, and the
 * interface keeps the packet, as upstream's does.
 */
public interface IMixinItemLaserDetonator {

	/**
	 * The pointing key: "show my laser". Empty on the wire. The server runs {@code doDetonate(..., MAIN_HAND, false)}
	 * for the sender - it points, it never detonates.
	 *
	 * <p>Upstream accepts it from anyone. Here (D-1094) the overhaul must be on and the sender must carry a laser
	 * detonator somewhere in their inventory, which is the only time upstream's client sends it.
	 */
	class RequestLaserPointPacket implements LeafiaCustomPacketEncoder {

		@Override
		public void encode(LeafiaBuf buf) { }

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			return ctx -> {
				if(ctx.flow() != PacketFlow.SERVERBOUND || !(ctx.player() instanceof ServerPlayer player)) return;
				if(!LeafiaFeatures.laserDetonatorOverhaul) {
					PacketGuard.reject("RequestLaserPointPacket", player, "the laser detonator overhaul is off");
					return;
				}
				if(!LeafiaLaserDetonator.carries(player)) {
					PacketGuard.reject("RequestLaserPointPacket", player, "no laser detonator in the inventory");
					return;
				}
				LeafiaLaserDetonator.doDetonate(player.level(), player, InteractionHand.MAIN_HAND, false);
			};
		}
	}
}
