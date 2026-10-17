package com.leafia.contents.machines.reactors.rbmk.effects;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A jet of steam from an RBMK column: a tall, thin, vertical quad, turned to face the camera about the
 * vertical only, that steps through a twenty-frame strip over its life.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/effects/ParticleRBMKJet.java}, a
 * variant of the base mod's {@code ParticleRBMKSteam} (same {@code rbmk_jet_steam.png}, same
 * camera-offset trick) that takes its life from the caller and draws a size of {@code rand + 1}. The
 * strip, the offset, the cylindrical billboard and the additive blend are all translated as
 * {@code ParticleRBMKSteam} does them (D-616): upstream's {@code rotationX}, {@code rotationZ} and
 * {@code rotationYZ} are the camera's left vector's x, the up vector's y and the left vector's z.
 *
 * <p>The quad is {@code 2 × size} wide, shifted one block along its own width axis, and
 * {@code 4 × size} tall. The frame is {@code age × 20 / life} ({@link LeafiaParticleMath#jetFrame}), the
 * alpha a twenty-tick fade in and out, halved ({@link LeafiaParticleMath#jetAlpha}).
 *
 * <p>Two upstream quirks, kept. The colour's alpha is set <em>before</em> the new alpha is worked
 * out, so a frame is drawn with the previous frame's alpha, and the very first with 1. And the age is
 * clamped to the life while rendering, which at age == life puts the frame at index 20 and its left
 * edge at the strip's right (the last tick before it is removed).
 */
@OnlyIn(Dist.CLIENT)
public class ParticleRBMKJet extends Particle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/rbmk_jet_steam.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	/** Frames across the strip. */
	public static final int FRAMES = 20;
	/** The fade in and out, in ticks. */
	public static final int FADE_TICKS = 20;
	/** The quad's height over its half-width. */
	public static final float HEIGHT_FACTOR = 4F;

	/** Upstream's {@code particleScale}, {@code rand + 1}: the quad's half-width. */
	private final float particleScale;

	public ParticleRBMKJet(ClientLevel level, double x, double y, double z, int maxAge) {
		super(level, x, y, z);
		this.lifetime = maxAge;
		this.particleScale = this.random.nextFloat() + 1F;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 camPos = camera.getPosition();
		float pX = (float) (Mth.lerp((double) partialTicks, this.xo, this.x) - camPos.x());
		float pY = (float) (Mth.lerp((double) partialTicks, this.yo, this.y) - camPos.y());
		float pZ = (float) (Mth.lerp((double) partialTicks, this.zo, this.z) - camPos.z());

		float ox = pX + camera.getLeftVector().x();
		float oy = pY + camera.getUpVector().y();
		float oz = pZ + camera.getLeftVector().z();

		if(this.age > this.lifetime)
			this.age = this.lifetime;

		int texIndex = LeafiaParticleMath.jetFrame(this.age, this.lifetime);
		float f0 = 1F / FRAMES;
		float uMin = texIndex * f0;
		float uMax = uMin + f0;

		// Drawn with the alpha of the previous frame, as upstream sets its colour before computing it.
		float drawAlpha = this.alpha;
		this.alpha = LeafiaParticleMath.jetAlpha(this.age, this.lifetime);

		double rad = Math.toRadians(-camera.getYRot());
		double cos = Math.cos(rad), sin = Math.sin(rad);

		double[] lx = { -this.particleScale - 1, -this.particleScale - 1, this.particleScale - 1, this.particleScale - 1 };
		double[] ly = { 0, this.particleScale * HEIGHT_FACTOR, this.particleScale * HEIGHT_FACTOR, 0 };
		float[] u = { uMax, uMax, uMin, uMin };
		float[] v = { 1, 0, 0, 1 };

		for(int i = 0; i < 4; i++) {
			double rx = lx[i] * cos;
			double rz = -lx[i] * sin;
			buffer.addVertex(ox + (float) rx, oy + (float) ly[i], oz + (float) rz)
					.setUv(u[i], v[i]).setColor(1F, 1F, 1F, drawAlpha).setLight(LightTexture.FULL_BRIGHT);
		}
	}

	/** Wide enough for the offset and the quad's width, tall enough for its four times the size. */
	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return new AABB(this.x - 4, this.y - 1, this.z - 4, this.x + 4, this.y + 9, this.z + 4);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
