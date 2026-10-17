package com.leafia.overwrite_contents.packets;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.overwrite_contents.detonator.LeafiaLaserDetonatorClient;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The laser detonator's beam: a start point and a vector to the centre of the block it ended on, drawn by every
 * client in range as a line of red glowing motes.
 *
 * <p>Upstream: {@code com.leafia.overwrite_contents.packets.LaserDetonatorPacket} ("this used to be a static class of
 * ItemLaserDetonator"), a {@code RecordablePacket} of six doubles, sent to everyone tracking within twice the ray's
 * length of the target. Here it is one of the addon's custom packets, clientbound only; its body is
 * {@link LeafiaLaserDetonatorClient#drawLaser}: from both ends toward the middle, a mote every
 * {@code ((p/30)^1.5 + 1) x 0.2} blocks - dense near the ends and sparse in the middle - each an {@code rslight} of
 * scale 0.5, pure red, drifting at up to 0.025 a tick.
 */
public class LaserDetonatorPacket implements LeafiaCustomPacketEncoder {

	public Vec3 startPoint = Vec3.ZERO;
	public Vec3 direction = Vec3.ZERO;

	public LaserDetonatorPacket() { }

	public LaserDetonatorPacket set(Vec3 startPoint, Vec3 direction) {
		this.startPoint = startPoint;
		this.direction = direction;
		return this;
	}

	/** Upstream's {@code TargetPoint(dimension, target, range)}: every player within {@code range} of the target. */
	public void send(ServerLevel level, Vec3 target, double range) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(this);
		if(packet != null) packet.__sendToAllAround(level, target, range);
	}

	@Override
	public void encode(LeafiaBuf buf) {
		buf.writeDouble(startPoint.x);
		buf.writeDouble(startPoint.y);
		buf.writeDouble(startPoint.z);
		buf.writeDouble(direction.x);
		buf.writeDouble(direction.y);
		buf.writeDouble(direction.z);
	}

	@Nullable
	@Override
	public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
		Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		Vec3 dir = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		return ctx -> {
			if(ctx.flow() != PacketFlow.CLIENTBOUND || !FMLEnvironment.dist.isClient()) return;
			LeafiaLaserDetonatorClient.drawLaser(start, dir);
		};
	}

	/** The distances along the line at which upstream puts a mote, from one end; each is drawn from both ends. */
	public static java.util.List<Double> moteSteps(double length) {
		java.util.List<Double> out = new java.util.ArrayList<>();
		for(double p = 0; p < length / 2;) {
			p += (Math.pow(p / 30d, 1.5) + 1) * 0.2;
			out.add(p);
		}
		return out;
	}
}
