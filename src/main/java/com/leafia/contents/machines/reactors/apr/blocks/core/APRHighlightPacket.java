package com.leafia.contents.machines.reactors.apr.blocks.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The APR editor's "Highlight": the blocks that stand where the structure needs something else, each to be drawn
 * as a red box labelled "Invalid Block" for ten seconds.
 *
 * <p>Upstream sends one {@code LeafiaDebug.Tracker} {@code SHOW_BOX} packet per block ({@code writeFloat(10)},
 * colour {@code 0xFF0000}, one line {@code "Invalid Block"}, the position), drawn by {@code TopRender}'s highlight
 * list. Neither the tracker nor {@code TopRender} is ported (one is a debugger, the other waits on its own
 * batch), so the APR has this packet and {@code APRClient} draws the boxes: the same duration, colour and label, in
 * one packet for all of them. Clientbound only.
 */
public class APRHighlightPacket implements LeafiaCustomPacketEncoder {

	/** Upstream's {@code buf.writeFloat(10)}: seconds. */
	public static final float DURATION = 10;
	public static final int COLOR = 0xFF0000;
	public static final String LABEL = "Invalid Block";
	/** Not upstream's: a guard on one packet's size. Upstream's own cap ({@code if (highlightsEmitted > 50)}) is commented out. */
	public static final int MAX = 4096;

	public List<BlockPos> positions = new ArrayList<>();

	public APRHighlightPacket() { }

	public APRHighlightPacket(List<BlockPos> positions) {
		this.positions = positions;
	}

	public static void send(ServerPlayer player, List<BlockPos> positions) {
		List<BlockPos> sent = positions.size() > MAX ? positions.subList(0, MAX) : positions;
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new APRHighlightPacket(sent));
		if(packet != null) packet.__sendToClient(player);
	}

	@Override
	public void encode(LeafiaBuf buf) {
		buf.writeInt(positions.size());
		for(BlockPos pos : positions) buf.writeLong(pos.asLong());
	}

	@Nullable
	@Override
	public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
		int count = Math.min(buf.readInt(), MAX);
		List<BlockPos> read = new ArrayList<>(Math.max(count, 0));
		for(int i = 0; i < count; i++) read.add(BlockPos.of(buf.readLong()));
		return ctx -> {
			if(ctx.flow() != PacketFlow.CLIENTBOUND || !FMLEnvironment.dist.isClient()) return;
			APRClient.addHighlights(read, DURATION);
		};
	}
}
