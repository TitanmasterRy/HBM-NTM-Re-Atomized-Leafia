package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleSprites;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * An expanding ring of {@link ParticleBasicSmoke} that hugs the ground: every tick it throws a ring
 * four blocks wider than the last, for fifty ticks, out to a radius of 200.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleSmokeShockwave.java}. It is a controller more than
 * a particle, but it extends {@code Particle} and does not override {@code renderParticle}, so in 1.12.2
 * it is also drawn as an ordinary particle: texture index 0 of the vanilla sheet, white, at the
 * centre, for as long as the wave runs. That is carried over, as {@code generic_0} on the 1.21.1
 * particle atlas ({@link LeafiaParticleSprites}) with the vanilla translucent type, at the vanilla
 * size {@code quadSize} already is. The {@code particleScale} it declares shadows the base field and
 * reaches nothing but the ring's own puffs, so it is a plain field here.
 *
 * <h2>One ring</h2>
 *
 * At age {@code a} the radius is {@code a × increment}, and the ring is
 * {@link LeafiaParticleMath#shockwaveAmount} puffs, {@code 8 + (int)(radius^0.4 × 20)}, at equal
 * angles. Each is placed at the column's height ({@code MOTION_BLOCKING}, with the cast to {@code int}
 * that truncates toward zero; see {@code ParticleFleijaAntischrabA}) with a Gaussian of 0.2 on each axis,
 * and gets the fade {@code sc = √(1 − radius / maxRadius)}: size {@code particleScale × (0.3 + sc × 0.7)},
 * alpha {@code sc × 0.8}, life {@link #particleLifetime}. The ring at radius 0 is therefore eight
 * puffs on the centre, and the last, at 200, is 174 puffs of size 1.5 and alpha 0.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleSmokeShockwave extends TextureSheetParticle {

	/** The ring's growth per tick, in blocks. */
	public static final double INCREMENT = 4;
	public static final float PARTICLE_SCALE = 5;
	public static final double MAX_RADIUS = 200;
	public static final int PARTICLE_LIFETIME = 12;
	/** The Gaussian each puff's position is jittered by, per axis, in blocks. */
	public static final double JITTER = 0.2;

	public double increment = INCREMENT;
	public float particleScale = PARTICLE_SCALE;
	public double maxRadius = MAX_RADIUS;
	public int particleLifetime = PARTICLE_LIFETIME;

	public ParticleSmokeShockwave(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
		this.setSprite(LeafiaParticleSprites.generic(0));
	}

	public void circleParticles(double radius, int amount, float scale, int lifetime, float alpha) {
		for(int i = 0; i < amount; i++) {
			double angle = LeafiaParticleMath.shockwaveAngle(amount, i);
			double oX = Math.cos(angle) * radius;
			double oZ = Math.sin(angle) * radius;
			int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) (this.x + oX), (int) (this.z + oZ));
			ParticleBasicSmoke smoke = new ParticleBasicSmoke(
					this.level,
					this.x + oX + this.level.random.nextGaussian() * JITTER,
					y + this.level.random.nextGaussian() * JITTER,
					this.z + oZ + this.level.random.nextGaussian() * JITTER
			);
			smoke.scale = scale;
			smoke.maxAge = lifetime;
			smoke.alphaMultiplier = alpha;
			Minecraft.getInstance().particleEngine.add(smoke);
		}
	}

	public int getAmountForRadius(double radius) {
		return LeafiaParticleMath.shockwaveAmount(radius);
	}

	@Override
	public void tick() {
		double radius = LeafiaParticleMath.shockwaveRadius(this.age, this.increment);
		float sc = LeafiaParticleMath.shockwaveFade(radius, this.maxRadius);
		circleParticles(radius, getAmountForRadius(radius),
				LeafiaParticleMath.shockwaveScale(this.particleScale, sc), this.particleLifetime,
				LeafiaParticleMath.shockwaveAlpha(sc));
		if(radius >= this.maxRadius)
			this.remove();
		this.age++;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}
}
