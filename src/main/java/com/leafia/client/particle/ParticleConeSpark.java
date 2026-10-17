package com.leafia.client.particle;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One spark of a {@code coneBurst}: a full-bright streak that flies, falls and fades out with its age.
 *
 * <p>Not an upstream file. NTM-CE's {@code "spark"} effect is what upstream's {@code TauSpark} hands its keys to, and
 * no NTM-CE source is in the reference, so this is the addon's reading of those keys, chosen to look like the
 * tau cannon's sparks the class says it was "stolen from" (D-3031): it falls at {@code 0.04 x gravity} a tick (the
 * 1.7.10 particle convention the base mod's own {@code ParticleSpark} keeps), loses 2% of its speed a tick (vanilla's
 * particle friction), collides with blocks, and is drawn as a beam {@code width} wide from its position back along
 * {@code length} ticks of its own velocity, in its colour scaled by its alpha and by what is left of its life
 * (the beam type is additive, so brightness is opacity).
 */
@OnlyIn(Dist.CLIENT)
public class ParticleConeSpark extends Particle {

	public static final double FALL_PER_GRAVITY = 0.04D;
	public static final double FRICTION = 0.98D;

	final int color;
	final float alpha;
	final float width;
	final float length;
	final float gravity;

	public ParticleConeSpark(ClientLevel level, double x, double y, double z, Vec3 velocity, int rgb, float alpha, float width, float length,
			float gravity, int lifetime) {
		super(level, x, y, z);
		this.xd = velocity.x;
		this.yd = velocity.y;
		this.zd = velocity.z;
		this.color = rgb;
		this.alpha = alpha;
		this.width = width;
		this.length = length;
		this.gravity = gravity;
		this.lifetime = Math.max(1, lifetime);
		this.hasPhysics = true;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if(this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		this.yd -= FALL_PER_GRAVITY * gravity;
		this.move(this.xd, this.yd, this.zd);
		this.xd *= FRICTION;
		this.yd *= FRICTION;
		this.zd *= FRICTION;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 cam = camera.getPosition();
		double px = xo + (x - xo) * partialTicks, py = yo + (y - yo) * partialTicks, pz = zo + (z - zo) * partialTicks;
		Vec3 streak = new Vec3(-xd, -yd, -zd).scale(length);
		if(streak.lengthSqr() < 1.0E-8) return;
		float fade = alpha * Math.max(0F, 1F - (age + partialTicks) / (float) lifetime);
		int r = (int) ((color >> 16 & 0xFF) * fade), g = (int) ((color >> 8 & 0xFF) * fade), b = (int) ((color & 0xFF) * fade);
		int shade = r << 16 | g << 8 | b;
		MultiBufferSource source = LeafiaParticleRenderTypes.beamSource(buffer);
		PoseStack pose = new PoseStack();
		pose.translate(px - cam.x, py - cam.y, pz - cam.z);
		LCEBeamPronter.prontBeam(pose, source, streak, EnumWaveType.STRAIGHT, EnumBeamType.SOLID, shade, shade, 0, 1, 0, 1, width);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return AABB.INFINITE;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return LeafiaParticleRenderTypes.BEAM;
	}
}
