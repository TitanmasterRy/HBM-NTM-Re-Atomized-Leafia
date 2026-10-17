package com.leafia.contents.machines.powercores.dfc.particles;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The flare at the DFC pulser when it overcharges: one large spinning glow that lasts a single
 * tick.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/particles/ParticlePulserOvercharge.java}.
 * Its size is {@code 7 + rand × 3} (the vanilla quad of half-size 0.1 of that, so 0.7 to 1.0), its angle is
 * a random {@code rand × 4π} that does not change, and its life is 1: it is removed on its second
 * tick, so it is drawn for two ticks. It is drawn only in the first half of each: upstream returns
 * from {@code renderParticle} when the partial tick is 0.5 or more, which makes it flicker at ten
 * frames in twenty, and that is kept.
 *
 * <p>Its texture runs a quarter-turn from the usual: upstream's four corners carry
 * {@code (0,1) (1,1) (1,0) (0,0)} where a plain billboard carries {@code (1,1) (1,0) (0,0) (0,1)}.
 * The corners here are in 1.21.1's order, which is mirrored from 1.12.2's side for side (both draw the
 * texture's bottom-right at their first corner), so the same permutation of the texture lands on the
 * matching corners.
 */
@OnlyIn(Dist.CLIENT)
public class ParticlePulserOvercharge extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/overcharge.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	public static final float MIN_SCALE = 7f;
	public static final float SCALE_SPREAD = 3f;
	/** Upstream's {@code particleMaxAge = 1}. */
	public static final int LIFETIME = 1;
	/** A frame is skipped from here to the end of the tick. */
	public static final float VISIBLE_UNTIL = 0.5f;

	public ParticlePulserOvercharge(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
		this.quadSize = 0.1F * (MIN_SCALE + level.random.nextFloat() * SCALE_SPREAD);
		this.rCol = 1;
		this.gCol = 1;
		this.bCol = 1;
		this.hasPhysics = false;
		this.alpha = 1;
		this.roll = (float) (level.random.nextDouble() * Math.PI * 4);
		this.oRoll = this.roll;
		this.lifetime = LIFETIME;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if(this.age >= this.lifetime)
			this.remove();
		this.age++;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		if(partialTicks >= VISIBLE_UNTIL) return;
		super.render(buffer, camera, partialTicks);
	}

	@Override
	protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float partialTicks) {
		float size = this.getQuadSize(partialTicks);
		int light = this.getLightColor(partialTicks);
		corner(buffer, quaternion, x, y, z, 1.0F, -1.0F, size, 0, 1, light);
		corner(buffer, quaternion, x, y, z, 1.0F, 1.0F, size, 1, 1, light);
		corner(buffer, quaternion, x, y, z, -1.0F, 1.0F, size, 1, 0, light);
		corner(buffer, quaternion, x, y, z, -1.0F, -1.0F, size, 0, 0, light);
	}

	private void corner(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z,
			float xOffset, float yOffset, float size, float u, float v, int light) {
		Vector3f p = new Vector3f(xOffset, yOffset, 0.0F).rotate(quaternion).mul(size).add(x, y, z);
		buffer.addVertex(p.x(), p.y(), p.z())
				.setUv(u, v)
				.setColor(this.rCol, this.gCol, this.bCol, this.alpha)
				.setLight(light);
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
