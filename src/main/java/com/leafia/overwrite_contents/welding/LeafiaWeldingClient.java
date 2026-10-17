package com.leafia.overwrite_contents.welding;

import com.leafia.client.LeafiaDFCClient;
import com.leafia.dev.optimization.LeafiaParticlePacket.FiaSpark;
import com.leafia.dev.optimization.LeafiaParticlePacket.FlashParticle;
import com.leafia.unsorted.ParticleArcWelder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The client halves of the welder and the soldering station overhauls: their particles, and the client tick the base
 * blocks do not give (they tick on the server only), which is the DFC's client ticker ({@link LeafiaDFCClient#track},
 * D-3011) - any {@code IDFCClientTicked} it is handed.
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaWeldingClient {

	private LeafiaWeldingClient() {}

	public static void track(BlockEntity be) {
		LeafiaDFCClient.track(be);
	}

	private static final Vec3 UP = new Vec3(0, 1, 0);

	/** Upstream's {@code leafia$spawnParticles}. */
	public static void welderParticles(LeafiaArcWelderTE te) {
		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;
		Vec3 vec = te.sparkPoint();
		if(world.getGameTime() % 2 == world.random.nextInt(2)) {
			FiaSpark spark = new FiaSpark();
			spark.color = 0xFFEE80;
			spark.count = world.random.nextInt(3) + 1;
			spark.thickness = 0.014f;
			spark.speedMin = 0.25f;
			spark.speedMax = 0.5f;
			spark.emitLocal(vec, UP);
		}
		if(world.getGameTime() % 2 == 0 || world.random.nextInt(3) == 0) {
			FlashParticle flash = new FlashParticle();
			flash.scale = world.random.nextFloat() * 0.015f + 0.15f;
			flash.ticksIn = 2;
			flash.ticksOut = world.random.nextInt(2) + 2;
			flash.emitLocal(vec, UP);
		}
		int amt = world.random.nextInt(2) + 1;
		for(int i = 0; i < amt; i++)
			Minecraft.getInstance().particleEngine.add(new ParticleArcWelder(world, vec.x, vec.y, vec.z));
	}

	/** The soldering station's sparks: upstream's client tail. */
	public static void solderingParticles(LeafiaSolderingStationTE te) {
		ClientLevel world = Minecraft.getInstance().level;
		if(world == null) return;
		if(world.getGameTime() % 4 == world.random.nextInt(4)) {
			FiaSpark spark = new FiaSpark();
			spark.color = 0xFFEE80;
			spark.count = world.random.nextInt(3) + 1;
			spark.thickness = 0.014f;
			spark.emitLocal(te.sparkPoint(), UP);
		}
	}
}
