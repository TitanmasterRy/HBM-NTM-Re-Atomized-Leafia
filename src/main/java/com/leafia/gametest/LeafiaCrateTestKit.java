package com.leafia.gametest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the crate, rack, rod and upgrade tests share (D-3021): a mock player taken back off the player list
 * (D-3000), and a payload context to run a packet's real decoder through.
 */
final class LeafiaCrateTestKit {

	private LeafiaCrateTestKit() {}

	/** A survival mock player, detached from the player list: one left on it crashes the server on the next broadcast. */
	@SuppressWarnings("deprecation")
	static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/**
	 * A survival player, for what breaks a block. {@link #mockPlayer} is a {@code ServerPlayer} whose
	 * {@code isCreative()} is hard-wired true (D-963), so a crate it breaks drops nothing at all.
	 */
	static Player survivor(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	/** Through the real framing and decoder, then the callback, as the handler runs it. */
	static void run(LeafiaCustomPacketEncoder encoder, ServerPlayer player, PacketFlow flow) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(encoder);
		var opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		if(callback != null) callback.accept(context(player, flow));
	}

	static IPayloadContext context(Player player, PacketFlow flow) {
		return new IPayloadContext() {
			@Override public net.minecraft.network.protocol.common.ClientCommonPacketListener listener() {
				throw new UnsupportedOperationException();
			}
			@Override public Player player() { return player; }
			@Override public CompletableFuture<Void> enqueueWork(Runnable task) {
				task.run();
				return CompletableFuture.completedFuture(null);
			}
			@Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
				return CompletableFuture.completedFuture(task.get());
			}
			@Override public PacketFlow flow() { return flow; }
			@Override public void handle(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
				throw new UnsupportedOperationException();
			}
			@Override public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
