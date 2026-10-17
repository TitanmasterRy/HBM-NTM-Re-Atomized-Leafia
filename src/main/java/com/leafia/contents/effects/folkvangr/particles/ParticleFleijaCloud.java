package com.leafia.contents.effects.folkvangr.particles;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.contents.effects.folkvangr.FolkvangrParticleMath;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A glowing mote riding the surface of a F.L.E.I.J.A. cloud.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/particles/ParticleFleijaCloud.java}. It is fixed
 * to its cloud at {@code (forward + right) x scale x 1.4} from the centre, re-read every tick so it grows
 * with the cloud ({@link FolkvangrParticleMath#cloudPosition}); fades in over ten ticks, out from forty to
 * sixty, and is gone at sixty or when its cloud is ({@link FolkvangrParticleMath#cloudAlpha}). Additive on
 * the base mod's {@code particle_base.png}, half-size 3, grey-white {@code 0.9 + rand x 0.05}, lit by the
 * world as upstream's {@code getBrightnessForRender} was.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFleijaCloud extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/particle_base.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	final Entity cloud;
	final Vec3 lookVector;
	final Vec3 rightVector;

	void updatePosition() {
		Vec3 p = FolkvangrParticleMath.cloudPosition(cloud.position(), lookVector, rightVector, LeafiaFleijaClouds.getScale(cloud));
		this.setPos(p.x, p.y, p.z);
	}

	public ParticleFleijaCloud(ClientLevel level, Vec3 forward, Vec3 right, Entity cloud) {
		super(level, cloud.getX(), cloud.getY(), cloud.getZ());
		this.quadSize = FolkvangrParticleMath.CLOUD_SCALE;
		this.rCol = this.gCol = this.bCol = 0.9F + level.random.nextFloat() * 0.05F;
		this.hasPhysics = false;
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		this.cloud = cloud;
		this.lookVector = forward;
		this.rightVector = right;
		updatePosition();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.alpha = 0;
	}

	@Override
	public void tick() {
		if(cloud.isRemoved()) {
			this.remove();
			return;
		}
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		updatePosition();
		this.alpha = FolkvangrParticleMath.cloudAlpha(this.age, this.alpha);
		if(++this.age >= FolkvangrParticleMath.CLOUD_LIFETIME) {
			this.remove();
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
