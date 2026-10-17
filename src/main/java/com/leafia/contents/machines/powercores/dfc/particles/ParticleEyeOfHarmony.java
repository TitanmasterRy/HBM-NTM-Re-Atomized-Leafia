package com.leafia.contents.machines.powercores.dfc.particles;

import com.hbm.particle.helper.HbmParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleSphere;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A mote that arcs out of a block and back into it along a half-circle, the "eye of harmony" the
 * DFC core makes.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/particles/ParticleEyeOfHarmony.java}.
 * It has no velocity and does not use its age. It carries a frame at the block's centre, tipped by a
 * Gaussian 15 degrees on two axes and turned by a random full circle about the vertical, and a
 * progress from 0 to 1 that it advances by {@code speed} (1/20 to 1/40) each tick. Where it is
 * at progress {@code p} is {@link #calculate}: the frame turned {@code p × flyAngle} degrees about
 * its vertical (90 to 270 of them) and pushed back along its own axis by
 * {@code flyHeight × sin(p × π)}, so it leaves the centre, swings out to {@code flyHeight} and returns.
 * The sphere is {@code sphere_ruv} at scale 0.2, additive and full-bright, in the colour it was given.
 *
 * <p>Rendering reads the progress {@code partialTick × speed} ahead of the last tick, which is how
 * upstream smooths it. The path moves away from where the particle was placed, so it is never
 * culled.
 *
 * <p>Upstream also declares a texture, {@code particle_balefire.png}, binds it and then binds a white
 * pixel over it before drawing; the declaration is dead and is not carried.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleEyeOfHarmony extends Particle {

	public static final float SCALE = 0.2F;

	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(HbmParticleRenderTypes.WHITE);

	float curPercentage = 0;
	float speed = 1 / 30f;
	float flyAngle = 180;
	float flyHeight = 2;
	FiaMatrix mat;

	public ParticleEyeOfHarmony(ClientLevel level, BlockPos pos, float r, float g, float b, float scale) {
		super(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		this.mat = new FiaMatrix(new Vec3(pos.getX(), pos.getY(), pos.getZ()).add(0.5, 0.5, 0.5))
				.rotate(RotationOrder.YXZ, level.random.nextGaussian() * 15, level.random.nextDouble() * 360, level.random.nextGaussian() * 15);
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.flyHeight = (level.random.nextFloat() * 0.5f + 0.75f) * scale;
		this.flyAngle = level.random.nextFloat() * 180 + 90;
		this.speed = 1 / (level.random.nextFloat() * 20 + 20);
		this.hasPhysics = false;
	}

	/** Where the mote is at progress {@code perc} along its arc. */
	public FiaMatrix calculate(float perc) {
		return LeafiaParticleMath.eyeOfHarmony(this.mat, perc, this.flyAngle, this.flyHeight);
	}

	@Override
	public void tick() {
		this.curPercentage += this.speed;
		if(this.curPercentage >= 1)
			this.remove();
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 at = calculate(this.curPercentage + partialTicks * this.speed).position.subtract(camera.getPosition());
		LeafiaParticleSphere.emit(buffer, (float) at.x, (float) at.y, (float) at.z, SCALE, this.rCol, this.gCol, this.bCol, 1F, LightTexture.FULL_BRIGHT);
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
