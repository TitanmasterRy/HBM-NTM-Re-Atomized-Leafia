package com.leafia.overwrite_contents.packets;

import java.util.Locale;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.passive.rendering.TopRender.Highlight;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The rad goggles' locating box: one block, drawn through everything for ten seconds in {@code 0xAAFF11}, labelled with the dose
 * that was just added there.
 *
 * <p>Upstream's {@code MixinRadiationSystemNT} sends a {@code LeafiaDebug.Tracker} {@code SHOW_BOX} packet (lifetime {@code 10},
 * colour {@code 0xAAFF11}, one label line {@code String.format("+%01.1fRAD", amount)}, the position); the tracker is a debugger and
 * not ported, so this is the same box as a packet of its own over the ported {@link Highlight}. Clientbound only (D-3041).
 */
public class RadHighlightPacket implements LeafiaCustomPacketEncoder {

	/** Upstream's {@code buf.writeFloat(10)}: seconds. */
	public static final float DURATION = 10;
	public static final int COLOR = 0xAAFF11;

	public BlockPos pos = BlockPos.ZERO;
	public float amount;

	public RadHighlightPacket() { }

	public RadHighlightPacket(BlockPos pos, float amount) {
		this.pos = pos;
		this.amount = amount;
	}

	/** Upstream's label: the dose to one decimal. (The locale is pinned: upstream formatted with the default one.) */
	public static String label(float amount) {
		return String.format(Locale.ROOT, "+%01.1fRAD", amount);
	}

	public static void send(ServerPlayer player, BlockPos pos, float amount) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new RadHighlightPacket(pos, amount));
		if(packet != null) packet.__sendToClient(player);
	}

	@Override
	public void encode(LeafiaBuf buf) {
		buf.writeLong(pos.asLong());
		buf.writeFloat(amount);
	}

	@Nullable
	@Override
	public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
		BlockPos read = BlockPos.of(buf.readLong());
		float dose = buf.readFloat();
		return ctx -> {
			if(ctx.flow() != PacketFlow.CLIENTBOUND || !FMLEnvironment.dist.isClient()) return;
			show(read, dose);
		};
	}

	/** The box itself, on whichever side it is asked. */
	public static Highlight show(BlockPos pos, float amount) {
		return new Highlight(pos).setLifetime(DURATION).setColor(COLOR).setLabel(label(amount)).show();
	}
}
