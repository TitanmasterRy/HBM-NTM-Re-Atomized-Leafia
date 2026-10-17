package com.leafia.contents.machines.misc.modular_turbine;

import com.leafia.client.particle.LeafiaParticleSprites;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A puff of steam that runs along a turbine stage and fades by the distance it has covered, not by
 * its age.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/ParticleMTSteam.java}, which
 * extends 1.12.2's {@code ParticleCloud}. 1.21.1's cloud is {@code PlayerCloudParticle}, which takes
 * a sprite set from a registered particle type; this particle is spawned directly by the turbine
 * core rather than through a type, so it extends {@link TextureSheetParticle} and carries
 * {@code ParticleCloud}'s constructor itself:
 *
 * <ul>
 * <li>size {@code × 0.75 × 2.5} on the default, which is 1.21.1's {@code × 1.875};</li>
 * <li>a grey of {@code 1 - random × 0.3} on all three channels;</li>
 * <li>no collision; and the motion is exactly the one given, as upstream then overrides it.</li>
 * </ul>
 *
 * <h2>Upstream's trick, kept</h2>
 *
 * {@code ParticleCloud} grows a puff from nothing over the first {@code 1/32} of its life. Upstream
 * wants it to grow over the first <em>block it travels</em> instead, so it writes
 * {@code distanceTravelled × maxAge / 32} into the age field the renderer reads — "trick the
 * renderer so the scale becomes the way we want" — while counting its real age separately for
 * expiry. {@link #getQuadSize} reads that age the way {@code ParticleCloud.renderParticle} did. The
 * frame steps from the eighth smoke sprite down to the first as the puff covers its distance, on
 * the particle atlas's own {@code generic_0..7}, which is what 1.12.2's texture indices 0..7 were.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleMTSteam extends TextureSheetParticle {

	final int travelDistance;
	double distanceTravelled = 0;
	int actualParticleAge = 0;

	public ParticleMTSteam(ClientLevel world, double x, double y, double z, double mx, double my, double mz, int travelDistance) {
		super(world, x, y, z, 0, 0, 0);
		this.travelDistance = travelDistance;
		float grey = 1.0F - (float) (Math.random() * 0.3F);
		this.rCol = grey;
		this.gCol = grey;
		this.bCol = grey;
		this.quadSize *= 1.875F;
		this.hasPhysics = false;
		this.lifetime = 200;
		this.xd = mx;
		this.yd = my;
		this.zd = mz;
		this.setSprite(frame(7));
	}

	private static TextureAtlasSprite frame(int index) {
		// Not ModelManager.getAtlas, which has no particle atlas and throws (D-1075).
		return LeafiaParticleSprites.generic(index);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	/** {@code ParticleCloud.renderParticle}'s growth curve, on the faked age. */
	@Override
	public float getQuadSize(float partialTicks) {
		return this.quadSize * Mth.clamp((this.age + partialTicks) / this.lifetime * 32.0F, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		distanceTravelled += Math.abs(xd) + Math.abs(yd) + Math.abs(zd);
		double progress = distanceTravelled / travelDistance;
		actualParticleAge++;
		if(progress >= 1 || actualParticleAge > lifetime)
			remove();
		this.setSprite(frame(7 - (int) Math.min(progress * 8, 7)));
		// trick the renderer so the scale becomes the way we want
		this.age = (int) (distanceTravelled * lifetime / 32);
		this.move(xd, yd, zd);
	}
}
