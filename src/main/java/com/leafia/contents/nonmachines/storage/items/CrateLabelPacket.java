package com.leafia.contents.nonmachines.storage.items;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.llib.technical.FifthString;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The labelling screen's text, client to server: the crate it is open on and the seven lines.
 *
 * <p>Upstream: the {@code LeafiaPacket} {@code generateSyncPacket()} of the five crate mixins, sent from
 * {@code CrateLabelGUI.keyTyped} after every key a text box took. It carried keys 0-3 (the vertical lines),
 * 5-7 (upper, middle, lower), 8 and 9 (the icon and its metadata), and the server applied whatever arrived
 * to the tile entity at the packet's position from any sender, at any distance, with any string.
 *
 * <p>Here (D-1094) the sender must have a {@link CrateLabelMenu} open on <em>this</em> crate, be within reach
 * of it, and send lines that fit what the screen can type: {@link CrateLabels#MIDDLE_LENGTH} characters for the
 * middle line and {@link CrateLabels#LINE_LENGTH} for the rest. The icon is not in the packet; the server sets
 * it from the click on the icon slot.
 */
public class CrateLabelPacket implements LeafiaCustomPacketEncoder {

	public BlockPos pos = BlockPos.ZERO;
	public final String[] vertical = { "", "", "", "" };
	public String upper = "";
	public String middle = "";
	public String lower = "";

	public CrateLabelPacket() { }

	public CrateLabelPacket(BlockPos pos, String[] vertical, String upper, String middle, String lower) {
		this.pos = pos;
		System.arraycopy(vertical, 0, this.vertical, 0, 4);
		this.upper = upper;
		this.middle = middle;
		this.lower = lower;
	}

	@Override
	public void encode(LeafiaBuf buf) {
		buf.writeInt(pos.getX());
		buf.writeInt(pos.getY());
		buf.writeInt(pos.getZ());
		for(String line : vertical) buf.writeFifthString(new FifthString(line));
		buf.writeFifthString(new FifthString(upper));
		buf.writeFifthString(new FifthString(middle));
		buf.writeFifthString(new FifthString(lower));
	}

	@Override
	public boolean acceptsServerbound() {
		return true;
	}

	@Nullable
	@Override
	public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

		BlockPos at = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
		String[] lines = new String[4];
		for(int i = 0; i < 4; i++) lines[i] = buf.readFifthString().toString();
		String up = buf.readFifthString().toString();
		String mid = buf.readFifthString().toString();
		String low = buf.readFifthString().toString();

		return ctx -> {
			if(ctx.flow() != PacketFlow.SERVERBOUND) return;
			if(!(ctx.player() instanceof ServerPlayer player)) return;
			apply(player, at, lines, up, mid, low);
		};
	}

	/** The server's half, public for the tests. Answers whether the lines were written. */
	public static boolean apply(ServerPlayer player, BlockPos at, String[] vertical, String upper, String middle, String lower) {

		if(!(player.containerMenu instanceof CrateLabelMenu menu) || menu.crate() == null
				|| !menu.crate().getBlockPos().equals(at) || menu.crate().isRemoved()) {
			PacketGuard.reject("CrateLabelPacket", player, "no labelling screen open on " + at);
			return false;
		}
		BlockEntityCrateBase crate = menu.crate();

		if(!PacketGuard.near(player, at)) {
			PacketGuard.reject("CrateLabelPacket", player, "out of reach");
			return false;
		}

		if(vertical.length != 4 || middle.length() > CrateLabels.MIDDLE_LENGTH
				|| upper.length() > CrateLabels.LINE_LENGTH || lower.length() > CrateLabels.LINE_LENGTH) {
			PacketGuard.reject("CrateLabelPacket", player, "a line longer than the screen types");
			return false;
		}
		for(String line : vertical) {
			if(line.length() > CrateLabels.LINE_LENGTH) {
				PacketGuard.reject("CrateLabelPacket", player, "a line longer than the screen types");
				return false;
			}
		}

		CrateLabels.of(crate).setText(vertical, upper, middle, lower);
		return true;
	}
}
