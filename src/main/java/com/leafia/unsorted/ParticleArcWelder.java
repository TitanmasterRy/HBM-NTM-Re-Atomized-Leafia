package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleSprites;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The arc welder's smoke: a cloud puff that billows up and out and fades to
 * nothing, drawn additively by its colour.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleArcWelder.java}, a {@code ParticleCloud}. The cloud's
 * constructor is carried here rather than inherited (1.21.1's is a {@code PlayerCloudParticle} that
 * wants a sprite set from a registered type, and this is spawned directly): its random initial
 * motion, scaled by a tenth, of which only the vertical part survives because upstream then sets the
 * horizontal motion itself, to a Gaussian over 20 times a quarter.
 *
 * <p>It lives forty ticks. Each tick it gains 0.3/20 upward and keeps 80% of its vertical speed; its
 * growth rate climbs by 0.005 and its size by that rate, so the puff accelerates as it swells. Its
 * colour is {@code (0.8, 0.925, 0.95)} times its alpha, and its alpha is
 * {@code 0.5 − 0.5 × (age / life)²} ({@link LeafiaParticleMath#arcWelderAlpha}). The render type blends
 * by source colour, not source alpha, and that is why the colour is premultiplied.
 *
 * <p>The frame is {@code 7 − age × 4 / life}, so the puff only ever uses frames 7 down to 4 — upstream's
 * {@code × 4} where the cloud it copies has {@code × 8}. The spin is {@code rand × 360}, in radians:
 * upstream hands degrees to a field that is read as radians, which is random enough that nobody
 * noticed, and a spin that is a whole number of turns anyway. Kept.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleArcWelder extends TextureSheetParticle {

	public static final int LIFETIME = 40;
	public static final float START_SCALE = 0.5f;
	public static final float START_INCREASE = 0.05f;
	/** What the growth rate gains each tick. */
	public static final float INCREASE_STEP = 0.005f;
	public static final double LIFT = 0.3 / 20;
	public static final double LIFT_DRAG = 0.8;
	public static final float RED = 0.8f;
	public static final float GREEN = 0.925f;
	public static final float BLUE = 0.95f;

	public float scale = START_SCALE;
	public float increaseAmt = START_INCREASE;

	public ParticleArcWelder(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z, 0, 0, 0);
		// ParticleCloud's own constructor: the random motion is scaled by a tenth.
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.lifetime = LIFETIME;
		this.hasPhysics = false;
		this.xd = level.random.nextGaussian() / 20 * 0.25;
		this.zd = level.random.nextGaussian() / 20 * 0.25;
		this.roll = level.random.nextFloat() * 360;
		this.oRoll = this.roll;
		this.updateColor();
		this.setSprite(LeafiaParticleSprites.generic(7));
	}

	void updateColor() {
		this.alpha = LeafiaParticleMath.arcWelderAlpha(this.age, this.lifetime);
		this.rCol = RED * this.alpha;
		this.gCol = GREEN * this.alpha;
		this.bCol = BLUE * this.alpha;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.yd += LIFT;
		this.yd *= LIFT_DRAG;
		this.increaseAmt += INCREASE_STEP;
		this.scale += this.increaseAmt;
		this.setSprite(LeafiaParticleSprites.generic(7 - this.age * 4 / this.lifetime));
		this.move(this.xd, this.yd, this.zd);
		this.updateColor();
		this.age++;
		if(this.age >= this.lifetime)
			this.remove();
	}

	/** {@code ParticleCloud}'s growth over the first 1/32 of the life, on the welder's own scale. */
	@Override
	public float getQuadSize(float partialTicks) {
		return 0.1F * this.scale * LeafiaParticleMath.cloudGrowth(this.age, partialTicks, this.lifetime);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return LeafiaParticleRenderTypes.ATLAS_SRC_COLOR;
	}
}
