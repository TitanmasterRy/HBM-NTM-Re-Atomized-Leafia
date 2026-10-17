package com.leafia.contents.effects.folkvangr.particles;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A spark of the Folkvangr antischrab burst: it is thrown out horizontally and slows unevenly as it
 * flies, fading out over fifty ticks.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/particles/ParticleFleijaAntischrabB.java}.
 * It is {@link ParticleFleijaAntischrabA}'s sibling: the same colour {@code (1, 0.149, 0)}, the same
 * additive {@code particle_base.png} and the same fade, but a quad of half-size 1, and it flies on a
 * velocity given at birth ({@code mx}, 0, {@code mz}) rather than spiralling. Each tick every velocity
 * component is multiplied by a fresh {@code rand × 0.1 + 0.9}, so it loses between nothing and a tenth
 * of its speed, unevenly; it moves with the vanilla move and never collides.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFleijaAntischrabB extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/particle_base.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	public static final float SCALE = 1;
	public static final float RED = 1;
	public static final float GREEN = 0.149f;
	public static final float BLUE = 0;
	/** A velocity component is multiplied by a draw from {@code SLOWEST} to {@code SLOWEST + 0.1}. */
	public static final double SLOWEST = 0.9;
	public static final double SLOW_SPREAD = 0.1;
	public static final int FADE_FROM = 10;
	public static final int LIFETIME = 50;

	public ParticleFleijaAntischrabB(ClientLevel level, double x, double y, double z, float mx, float mz) {
		super(level, x, y, z);
		this.quadSize = SCALE;
		this.rCol = RED;
		this.gCol = GREEN;
		this.bCol = BLUE;
		this.hasPhysics = false;
		this.xd = mx;
		this.yd = 0;
		this.zd = mz;
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.xd *= this.random.nextFloat() * SLOW_SPREAD + SLOWEST;
		this.yd *= this.random.nextFloat() * SLOW_SPREAD + SLOWEST;
		this.zd *= this.random.nextFloat() * SLOW_SPREAD + SLOWEST;
		this.move(this.xd, this.yd, this.zd);
		if(this.age >= FADE_FROM) {
			this.alpha = LeafiaParticleMath.fleijaAlpha(this.age);
		}

		if(++this.age >= LIFETIME) {
			this.remove();
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
