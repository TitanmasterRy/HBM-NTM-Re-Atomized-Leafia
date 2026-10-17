package com.leafia.contents.effects.folkvangr.particles;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.VacuumInstance;
import com.leafia.contents.effects.folkvangr.FolkvangrParticleMath;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Dust drawn into a Folkvangr's vacuum (and, once the DFC lands, into a collapsing core's).
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/particles/ParticleFleijaVacuum.java}. It is pulled
 * exactly as an entity is ({@link EntityNukeFolkvangr#pullOn}), with no drag, so it accelerates inward; as it
 * goes it shrinks to {@code ratio^0.1} of its size and brightens to {@code (1 - ratio)^0.5} of its alpha, the
 * ratio running from 1 where it started to 0 at half the vacuum's inner radius
 * ({@link FolkvangrParticleMath#vacuumRatio}). It is gone there, or after ten seconds. Ordinary alpha
 * blending on {@code particle_base.png}, no depth write, grey-white {@code 0.9 + rand x 0.05}.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFleijaVacuum extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/particle_base.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.alpha(TEXTURE);

	private final float baseScale;
	final VacuumInstance instance;
	final double startDistance;
	final float baseAlpha;

	public ParticleFleijaVacuum(ClientLevel level, double x, double y, double z, float scale, float alpha, VacuumInstance instance) {
		super(level, x, y, z);
		this.quadSize = scale;
		this.baseScale = scale;
		this.rCol = this.gCol = this.bCol = 0.9F + level.random.nextFloat() * 0.05F;
		this.hasPhysics = false;
		this.alpha = alpha;
		this.baseAlpha = alpha;
		this.instance = instance;
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		this.startDistance = new Vec3(this.x, this.y, this.z).distanceTo(instance.pos);
		this.lifetime = FolkvangrParticleMath.VACUUM_LIFETIME;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		Vec3 posVec = new Vec3(this.x, this.y, this.z);
		double distance = posVec.distanceTo(instance.pos);
		double ratio = FolkvangrParticleMath.vacuumRatio(distance, startDistance, instance.vacuumStart);

		this.quadSize = FolkvangrParticleMath.vacuumScale(baseScale, ratio);
		this.alpha = FolkvangrParticleMath.vacuumAlpha(baseAlpha, ratio);

		Vec3 pull = EntityNukeFolkvangr.pullOn(posVec, instance.pos, distance, instance.vacuumStart, instance.vacuumEnd, instance.vacuumForce);
		this.xd += pull.x;
		this.yd += pull.y;
		this.zd += pull.z;
		this.move(this.xd, this.yd, this.zd);

		if(++this.age >= FolkvangrParticleMath.VACUUM_LIFETIME || distance < instance.vacuumStart / 2) {
			this.remove();
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
