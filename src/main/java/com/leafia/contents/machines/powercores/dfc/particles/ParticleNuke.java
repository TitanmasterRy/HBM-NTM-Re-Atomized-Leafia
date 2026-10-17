package com.leafia.contents.machines.powercores.dfc.particles;

import com.hbm.particle.helper.HbmParticleRenderTypes;
import com.leafia.client.particle.DfcFlash;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleSphere;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The DFC blast's fireball: one white sphere that swells to {@code radius × 2} across and fades out,
 * and flashes the screen white for the player it engulfs.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/particles/ParticleNuke.java}. It is
 * created at the middle of a block, lives {@code maxAge} ticks (60 unless the caller sets it), and
 * its diameter is a quadratic ease-out of age to {@code radius × 2} ({@link LeafiaParticleMath#nukeDiameter}).
 * It is opaque until twenty ticks before the end and then fades linearly to nothing
 * ({@link LeafiaParticleMath#nukeAlpha}). White, additive, full-bright.
 *
 * <p>The first frame the sphere's radius reaches the player's eyes, it starts the screen flash by
 * writing {@code 100 + flashTime} to {@link DfcFlash} — once; {@code reachedPlayer} stays set. Both
 * the radius and the eye position are the frame's, not interpolated, as upstream has them.
 *
 * <p>It is never culled, since the camera ends up inside it.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleNuke extends Particle {

	public static final int DEFAULT_RADIUS = 250;
	public static final int DEFAULT_FLASH_TIME = 50;
	/** {@code 20 * 3}: three seconds. */
	public static final int DEFAULT_LIFETIME = 20 * 3;
	/** The last twenty ticks, over which it fades. */
	public static final int FADE_TICKS = 20;

	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(HbmParticleRenderTypes.WHITE);

	boolean reachedPlayer = false;
	public int radius = DEFAULT_RADIUS;
	public int flashTime = DEFAULT_FLASH_TIME;

	public ParticleNuke(ClientLevel level, BlockPos pos) {
		super(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		this.lifetime = DEFAULT_LIFETIME;
	}

	float getScale(float age) {
		return LeafiaParticleMath.nukeDiameter(age, this.lifetime, this.radius);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if(this.age++ >= this.lifetime) {
			this.remove();
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 cam = camera.getPosition();
		float pX = (float) (Mth.lerp((double) partialTicks, this.xo, this.x) - cam.x());
		float pY = (float) (Mth.lerp((double) partialTicks, this.yo, this.y) - cam.y());
		float pZ = (float) (Mth.lerp((double) partialTicks, this.zo, this.z) - cam.z());

		float alpha = LeafiaParticleMath.nukeAlpha(this.age, this.lifetime);
		float sc = getScale(this.age + partialTicks);
		LeafiaParticleSphere.emit(buffer, pX, pY, pZ, sc, 1F, 1F, 1F, alpha, LightTexture.FULL_BRIGHT);

		LocalPlayer player = Minecraft.getInstance().player;
		if(!this.reachedPlayer && player != null && sc / 2 >= new Vec3(this.x, this.y, this.z).distanceTo(player.getEyePosition())) {
			this.reachedPlayer = true;
			DfcFlash.trigger(this.flashTime);
		}
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
