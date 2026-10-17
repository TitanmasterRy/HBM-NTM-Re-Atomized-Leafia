package com.leafia.contents.machines.reactors.rbmk.debris;

import java.util.Random;

import com.hbm.lib.RefStrings;
import com.hbm.particle.ParticleCoolingTower;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One puff of a melted RBMK's smoke: the base mod's cooling-tower steam, drawn as six dark quads.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/debris/ParticleRBMKSmoke.java}, a
 * {@code ParticleCoolingTower} that keeps the steam's motion (the lift, the sideways wander, the wind) and
 * replaces its size and its look:
 *
 * <ul>
 * <li><b>Size</b>: {@code base + (max - base) x (age / maxAge)^1.5}, set after the steam's own tick has
 * counted the age. Its own {@code baseScale}/{@code maxScale}, which shadow the steam's.</li>
 * <li><b>Look</b>: six quads, {@code 0.75 x size} across, each shifted by a Gaussian of 0.5 blocks on
 * every axis and shaded a grey of {@code rand x 0.4}, all from a {@code Random} seeded with the particle's
 * hash code - so the six stay where they are frame to frame - on the base mod's {@code contrail.png} (CE's
 * {@code NTMClientRegistry.contrail} sprite; the base mod's own smoke plume draws the same file). Lit by the
 * world. The alpha is the steam's, {@code 0.25 - 0.25 x age / maxAge}, worked out before the age counts.</li>
 * </ul>
 *
 * <p>Upstream's {@code depthMask(true)} around its quads does nothing: 1.12.2 buffers every particle of a
 * layer and draws them after the last one has turned the mask back off, so the smoke never wrote depth, and
 * the alpha-blended type without depth writes is what it was (D-3018). {@code particleAngle} is never set, so
 * its rotation branch is not ported.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleRBMKSmoke extends ParticleCoolingTower {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/contrail.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.alpha(TEXTURE);

	/** The steam's default {@code alphaMod}, which the smoke never changes. */
	public static final float ALPHA_MOD = 0.25F;

	private float baseScale = 1.0F;
	private float maxScale = 1.0F;
	private float smokeScale = 1.0F;
	private float smokeAlpha = 1.0F;

	public ParticleRBMKSmoke(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
	}

	/** {@link RBMKDebrisSmokeTE}'s {@code addSmoke}: one puff at a position, of a debris pile's size. */
	public static void puff(Level level, double x, double y, double z, float scale, int life) {
		ParticleRBMKSmoke smoke = new ParticleRBMKSmoke((ClientLevel) level, x, y, z);
		smoke.setBaseScale(scale);
		smoke.setMaxScale(scale * 3);
		smoke.setLife(life);
		smoke.setLift(0.65F);
		Minecraft.getInstance().particleEngine.add(smoke);
	}

	@Override
	public void setBaseScale(float f) {
		this.baseScale = f;
	}

	@Override
	public void setMaxScale(float f) {
		this.maxScale = f;
	}

	/** The size at an age: {@code base + (max - base) x (age / maxAge)^1.5}. */
	public static float scaleAt(float base, float max, int age, int maxAge) {
		return base + (max - base) * (float) Math.pow((float) age / maxAge, 1.5);
	}

	@Override
	public void tick() {
		this.smokeAlpha = ALPHA_MOD - ((float) this.age / (float) this.lifetime) * ALPHA_MOD;
		super.tick();
		this.smokeScale = scaleAt(this.baseScale, this.maxScale, this.age, this.lifetime);
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float interp) {

		Vec3 cam = camera.getPosition();
		float tx = camera.getLeftVector().x();
		float ty = camera.getLeftVector().y();
		float tz = camera.getLeftVector().z();
		float ux = camera.getUpVector().x();
		float uy = camera.getUpVector().y();
		float uz = camera.getUpVector().z();

		float f4 = 0.75F * this.smokeScale;
		int light = this.getLightColor(interp);
		float alpha = Mth.clamp(this.smokeAlpha, 0F, 1F);

		Random urandom = new Random(this.hashCode());

		for(int ii = 0; ii < 6; ii++) {

			float pX = (float) (this.xo + (this.x - this.xo) * interp - cam.x + urandom.nextGaussian() * 0.5);
			float pY = (float) (this.yo + (this.y - this.yo) * interp - cam.y + urandom.nextGaussian() * 0.5);
			float pZ = (float) (this.zo + (this.z - this.zo) * interp - cam.z + urandom.nextGaussian() * 0.5);

			float grey = urandom.nextFloat() * 0.4F + 0F;

			buffer.addVertex(pX - tx * f4 - ux * f4, pY - ty * f4 - uy * f4, pZ - tz * f4 - uz * f4)
					.setUv(1, 1).setColor(grey, grey, grey, alpha).setLight(light);
			buffer.addVertex(pX - tx * f4 + ux * f4, pY - ty * f4 + uy * f4, pZ - tz * f4 + uz * f4)
					.setUv(1, 0).setColor(grey, grey, grey, alpha).setLight(light);
			buffer.addVertex(pX + tx * f4 + ux * f4, pY + ty * f4 + uy * f4, pZ + tz * f4 + uz * f4)
					.setUv(0, 0).setColor(grey, grey, grey, alpha).setLight(light);
			buffer.addVertex(pX + tx * f4 - ux * f4, pY + ty * f4 - uy * f4, pZ + tz * f4 - uz * f4)
					.setUv(0, 1).setColor(grey, grey, grey, alpha).setLight(light);
		}
	}

	/** The six quads reach {@code 0.75 x size} plus a couple of Gaussians out; 1.21.1 culls by this box. */
	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		double r = 0.75 * this.smokeScale + 2;
		return new AABB(this.x - r, this.y - r, this.z - r, this.x + r, this.y + r, this.z + r);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
