package com.leafia.client.particle;

import com.hbm.particle.helper.IParticleCreator;
import com.hbm.particle.helper.ParticleCreators;
import com.leafia.dev.optimization.LeafiaParticlePacket.TauSpark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The {@code "spark"} effect's {@code coneBurst} mode, which upstream's {@code TauSpark} hands its keys to and which
 * neither the base mod nor {@code hbm-src} has: it is NTM-CE's (D-3030 #4). Registered in the base mod's creator table
 * under {@link TauSpark#EFFECT} from the addon's client setup.
 *
 * <p><b>This is the addon's reading of upstream's keys, not a port</b> - no NTM-CE source is in the reference, so the
 * semantics below are this port's, chosen from what the keys say and from the one upstream caller (commented out in
 * {@code WorldServerLeafia}, at a mined block's hit point, with the face normal times 3 to 4 as the motion, beside a
 * {@code FiaSpark} of speed 0.15 to 0.35 aimed the same way). D-3031 lists them:
 * <ul>
 * <li>{@code count} sparks, each aimed uniformly inside a cone of full aperture {@code angle} degrees round the
 *     packet's motion, straight up when the motion is zero ({@link TauSpark#coneDirection});</li>
 * <li>each at a speed of a tenth of the motion's length - so that one caller's sparks fly at 0.3 to 0.4 a tick, the
 *     speed of the {@code FiaSpark} beside it - jittered by a gaussian of {@code randomVelocity} times that speed on
 *     every axis;</li>
 * <li>living {@code lifetime + rand(randLifetime + 1)} ticks, {@code width} wide, a streak of {@code length} ticks of
 *     its own velocity, falling at {@code 0.04 x gravity} a tick ({@link ParticleConeSpark}), in {@code r, g, b} at
 *     opacity {@code a}.</li>
 * </ul>
 * Any other mode is ignored, as an unknown type is by the base dispatch.
 */
public final class LeafiaSparkCreator implements IParticleCreator {

	/** Speed per unit of the packet's motion. */
	public static final double SPEED_PER_MOTION = 0.1D;

	private static boolean installed = false;

	/** Claims {@link TauSpark#EFFECT} once; the base table throws on a second claim. */
	public static synchronized void install() {
		if(installed) return;
		ParticleCreators.register(TauSpark.EFFECT, new LeafiaSparkCreator());
		installed = true;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void makeParticle(Level world, Player player, RandomSource rand, double x, double y, double z, CompoundTag data) {
		if(!"coneBurst".equals(data.getString("mode")) || !(world instanceof ClientLevel level)) return;
		Vec3 motion = new Vec3(data.getDouble("mX"), data.getDouble("mY"), data.getDouble("mZ"));
		double speed = motion.length() * SPEED_PER_MOTION;
		double jitter = data.getFloat("randomVelocity") * speed;
		int rgb = Math.round(data.getFloat("r") * 255) << 16 | Math.round(data.getFloat("g") * 255) << 8 | Math.round(data.getFloat("b") * 255);
		float half = data.getFloat("angle") / 2F;
		int randLife = Math.max(0, data.getInt("randLifetime"));
		for(int i = 0; i < data.getInt("count"); i++) {
			Vec3 v = TauSpark.coneDirection(motion, half, rand.nextDouble(), rand.nextDouble()).scale(speed)
					.add(rand.nextGaussian() * jitter, rand.nextGaussian() * jitter, rand.nextGaussian() * jitter);
			Minecraft.getInstance().particleEngine.add(new ParticleConeSpark(level, x, y, z, v, rgb, data.getFloat("a"), data.getFloat("width"),
					data.getFloat("length"), data.getFloat("gravity"), data.getInt("lifetime") + rand.nextInt(randLife + 1)));
		}
	}
}
