package com.leafia.contents.machines.powercores.dfc.particles;

import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A speck of light drawn into the DFC pulser as it charges: it starts a third of a block out in a random
 * direction and falls inward, shrinking to nothing, over three to five ticks.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/particles/ParticlePulserCharge.java}.
 * Its starting distance is {@code rand × 0.25 + 0.35}, its direction a unit vector turned by a random
 * yaw and then a random pitch, each over a full circle (so the directions are not uniform on a
 * sphere, as upstream's are not). Its place and its size both follow a sine ease-out of the ratio
 * {@code 1 − age / life} ({@link LeafiaParticleMath#pulserEase}), so at birth it is at the far end and
 * full size, and at death at the centre and nothing. It ages two ticks to a tick, which makes a
 * life of three to five ticks one or two moves.
 *
 * <p>The quad is {@code particleScale} wide on each side of the centre — upstream draws it without the
 * vanilla 0.1 — so its half-size is 0.25 at the start. Additive, full-bright, white, {@code glow.png}.
 * The first thing the constructor does after drawing the direction is move it to the ratio-1
 * position, where it stands for the first frame.
 */
@OnlyIn(Dist.CLIENT)
public class ParticlePulserCharge extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/glow.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	/** Upstream's {@code particleScale}/{@code baseScale}: the quad's half-size at full. */
	public static final float BASE_SCALE = 0.25f;
	/** The nearest and the farthest the speck starts: {@code 0.35 + rand × 0.25}. */
	public static final double MIN_DISTANCE = 0.35;
	public static final double DISTANCE_SPREAD = 0.25;
	public static final int MIN_LIFETIME = 3;
	/** It ages by this each tick. */
	public static final int AGE_STEP = 2;

	private float particleScale;
	private final double startDistance;
	private final Vec3 startPos;
	private final Vec3 vec;

	void doMove(double ratio) {
		Vec3 calc = this.startPos.add(this.vec.scale(LeafiaParticleMath.pulserEase(ratio) * this.startDistance));
		this.setPos(calc.x, calc.y, calc.z);
		this.particleScale = (float) (LeafiaParticleMath.pulserEase(ratio) * BASE_SCALE);
	}

	public ParticlePulserCharge(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
		this.startPos = new Vec3(x, y, z);
		this.particleScale = BASE_SCALE;
		this.rCol = 1;
		this.gCol = 1;
		this.bCol = 1;
		this.hasPhysics = false;
		this.alpha = 1;
		this.startDistance = level.random.nextDouble() * DISTANCE_SPREAD + MIN_DISTANCE;
		this.lifetime = MIN_LIFETIME + level.random.nextInt(3);
		Vec3 v = new Vec3(0, 0, 1);
		v = v.yRot((float) (level.random.nextDouble() * Math.PI * 2));
		v = v.xRot((float) (level.random.nextDouble() * Math.PI * 2));
		this.vec = v;
		doMove(1);
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.age += AGE_STEP;
		if(this.age >= this.lifetime)
			this.remove();
		else {
			doMove(1 - this.age / (double) this.lifetime);
		}
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return this.particleScale;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
