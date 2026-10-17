package com.leafia.contents.machines.powercores.dfc.particles;

import com.hbm.particle.helper.HbmParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleSphere;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A mote of a DFC blast: a small, bright, coloured sphere thrown out in a random direction.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/particles/ParticleDFC.java}. It is
 * given its colour, draws a random velocity of up to 1.5 blocks a tick on each axis
 * ({@code (rand − 0.5) × 3}), and lives as long as a fire particle does
 * ({@link LeafiaParticleMath#fireLifetime}). It does not use the vanilla move: each tick it adds the
 * velocity straight to its position, so it passes through blocks and never slows. Its sphere is the
 * base mod's {@code sphere_ruv} at scale 0.2, additive and full-bright.
 *
 * <p>It is not culled: its bounding box would otherwise be where it started, since the position is
 * written directly, and a mote that has flown 20 blocks off its first position would vanish.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleDFC extends Particle {

	/** The sphere's scale: upstream's {@code LeafiaGls.scale(0.2)} on a model of diameter 1. */
	public static final float SCALE = 0.2F;
	/** Upstream's {@code (rand - 0.5) × 3}. */
	public static final float SPEED = 3F;

	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(HbmParticleRenderTypes.WHITE);

	public ParticleDFC(ClientLevel level, double x, double y, double z, float red, float green, float blue) {
		super(level, x, y, z);
		this.xd = (double) (this.random.nextFloat() - 0.5F) * SPEED;
		this.yd = (double) (this.random.nextFloat() - 0.5F) * SPEED;
		this.zd = (double) (this.random.nextFloat() - 0.5F) * SPEED;
		this.rCol = red;
		this.gCol = green;
		this.bCol = blue;
		this.lifetime = LeafiaParticleMath.fireLifetime(Math.random());
		this.hasPhysics = false;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if(this.age++ >= this.lifetime) {
			this.remove();
		}

		this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 cam = camera.getPosition();
		float pX = (float) (Mth.lerp((double) partialTicks, this.xo, this.x) - cam.x());
		float pY = (float) (Mth.lerp((double) partialTicks, this.yo, this.y) - cam.y());
		float pZ = (float) (Mth.lerp((double) partialTicks, this.zo, this.z) - cam.z());
		LeafiaParticleSphere.emit(buffer, pX, pY, pZ, SCALE, this.rCol, this.gCol, this.bCol, 1F, LightTexture.FULL_BRIGHT);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return AABB.INFINITE;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
