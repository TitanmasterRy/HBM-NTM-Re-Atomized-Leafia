package com.leafia.unsorted;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A grey puff on the base mod's {@code contrail.png}: it fades out linearly over its life, slows to
 * 0.76 of its speed every tick, is drawn at full brightness and is drawn over anything in front of it.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleBasicSmoke.java}. Its size is its own field,
 * {@link #scale}, and it is a <em>half-size in blocks</em>: the renderer multiplies the camera vectors
 * by it directly, with no {@code 0.1}, so the default 1 is a quad two blocks across.
 * {@link #getQuadSize} returns it for that reason, and the random {@code particleScale} the
 * 1.12.2 constructor drew is not read.
 *
 * <ul>
 * <li>Life is {@code 100 + rand.nextInt(40)} ticks ({@link LeafiaParticleMath#smokeMaxAge}), held in
 * {@link #maxAge} because {@code ParticleSmokeShockwave} overwrites it.</li>
 * <li>The colour is {@code (0.25, 0.25, 0.25)}; the alpha is
 * {@code (1 − age / maxAge) × alphaMultiplier}, worked out before the age is counted
 * ({@link LeafiaParticleMath#smokeAlpha}).</li>
 * <li>{@code getBrightnessForRender} is 240, which is block light 15 and sky light 0:
 * {@code LightTexture.pack(15, 0)}.</li>
 * <li>{@code shouldDisableDepth} is true, so the render type has the depth test off.</li>
 * </ul>
 *
 * <p>Two things not read off upstream. 1.12.2's {@code Particle} base class is not in the clone, so
 * whether it collided by default is unverified; this keeps 1.21.1's default, which does. And the
 * culling box is the quad's reach, {@code scale} either side: 1.12.2 did not cull particles by
 * volume at all, and 1.21.1's default box, 0.2 across, would drop a two-block puff as its centre left
 * the frustum.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleBasicSmoke extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/contrail.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.alphaNoDepth(TEXTURE);

	/** {@code 0.7599999785423279D}: the float 0.76 widened, as upstream's literal is. */
	public static final double DRAG = 0.7599999785423279D;
	/** The grey on all three channels. */
	public static final float GREY = 0.25f;
	/** The shortest life; the longest is this plus 39. */
	public static final int MIN_LIFETIME = 100;
	/** The spread of the life: {@code rand.nextInt(40)}. */
	public static final int LIFETIME_SPREAD = 40;
	/** {@code getBrightnessForRender}: block 15, sky 0. */
	public static final int BRIGHTNESS = 240;

	public int maxAge;
	public float scale = 1;
	public float alphaMultiplier = 1;

	public ParticleBasicSmoke(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
		this.maxAge = LeafiaParticleMath.smokeMaxAge(this.random.nextInt(LIFETIME_SPREAD));
		this.setParticleColor(GREY, GREY, GREY);
	}

	public void setParticleColor(float r, float g, float b) {
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
	}

	public void setMotion(double x, double y, double z) {
		this.xd = x;
		this.yd = y;
		this.zd = z;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		this.alpha = LeafiaParticleMath.smokeAlpha(this.age, this.maxAge, this.alphaMultiplier);

		++this.age;

		if(this.age == this.maxAge) {
			this.remove();
		}

		this.xd *= DRAG;
		this.yd *= DRAG;
		this.zd *= DRAG;

		this.move(this.xd, this.yd, this.zd);
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return this.scale;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.pack(15, 0);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return new AABB(this.x - this.scale, this.y - this.scale, this.z - this.scale,
				this.x + this.scale, this.y + this.scale, this.z + this.scale);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
