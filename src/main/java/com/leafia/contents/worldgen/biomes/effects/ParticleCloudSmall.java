package com.leafia.contents.worldgen.biomes.effects;

import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleSprites;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A puff of cloud for the addon's biome effects: vanilla's cloud, without its 1.875 times size, and with
 * a size and a drift the caller gives.
 *
 * <p>Upstream: {@code com/leafia/contents/worldgen/biomes/effects/ParticleCloudSmall.java}, a copy of
 * 1.12.2's {@code ParticleCloud} with the {@code × 2.5} commented out and a {@code scale} argument
 * in its place — the vanilla cloud's {@code × 0.75} is gone too. The constructor: a tenth of the
 * random motion plus the given one, a grey of {@code 1 − rand × 0.3}, the size times {@code scale}
 * (0.75 unless given), and a life of {@code 8 / (rand × 0.8 + 0.3)} times 2.5.
 *
 * <p>Each tick it takes the next smoke frame ({@code 7 − age × 8 / life}), moves, keeps 96% of its
 * speed and, on the ground, loses 30% of its sideways speed. A player within two blocks pulls it down
 * to their feet's height: if it is above their bounding box's floor it closes a fifth of the
 * difference and takes a fifth of the difference in vertical speed, which is how a cloud
 * hugging the ground parts around whoever walks through it. It grows from nothing over the first
 * 1/32 of its life ({@link LeafiaParticleMath#cloudGrowth}) and is drawn as vanilla's cloud is.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleCloudSmall extends TextureSheetParticle {

	public static final float DEFAULT_SCALE = 0.75f;
	/** The life is multiplied by this. */
	public static final float LIFETIME_FACTOR = 2.5F;
	/** Speed kept each tick: upstream's {@code 0.9599999785423279D}, the float 0.96. */
	public static final double DRAG = 0.9599999785423279D;
	public static final double GROUND_DRAG = 0.699999988079071D;
	/** How near a player has to be to disturb it. */
	public static final double PLAYER_RANGE = 2.0D;
	/** The fraction of the gap closed each tick. */
	public static final double PLAYER_PULL = 0.2D;

	public ParticleCloudSmall(ClientLevel level, double x, double y, double z) {
		this(level, x, y, z, 0, 0, 0, DEFAULT_SCALE);
	}

	public ParticleCloudSmall(ClientLevel level, double x, double y, double z, float scale) {
		this(level, x, y, z, 0, 0, 0, scale);
	}

	public ParticleCloudSmall(ClientLevel level, double x, double y, double z, double mx, double my, double mz, float scale) {
		super(level, x, y, z, 0.0D, 0.0D, 0.0D);
		this.xd *= 0.10000000149011612D;
		this.yd *= 0.10000000149011612D;
		this.zd *= 0.10000000149011612D;
		this.xd += mx;
		this.yd += my;
		this.zd += mz;
		float f = 1.0F - (float) (Math.random() * 0.30000001192092896D);
		this.rCol = f;
		this.gCol = f;
		this.bCol = f;
		this.quadSize *= scale;
		this.lifetime = (int) (8.0D / (Math.random() * 0.8D + 0.3D));
		this.lifetime = (int) ((float) this.lifetime * LIFETIME_FACTOR);
		this.setSprite(LeafiaParticleSprites.generic(7));
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return this.quadSize * LeafiaParticleMath.cloudGrowth(this.age, partialTicks, this.lifetime);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if(this.age++ >= this.lifetime) {
			this.remove();
		}

		this.setSprite(LeafiaParticleSprites.generic(7 - this.age * 8 / this.lifetime));
		this.move(this.xd, this.yd, this.zd);
		this.xd *= DRAG;
		this.yd *= DRAG;
		this.zd *= DRAG;
		Player player = this.level.getNearestPlayer(this.x, this.y, this.z, PLAYER_RANGE, false);

		if(player != null) {
			AABB box = player.getBoundingBox();

			if(this.y > box.minY) {
				this.y += (box.minY - this.y) * PLAYER_PULL;
				this.yd += (player.getDeltaMovement().y - this.yd) * PLAYER_PULL;
				this.setPos(this.x, this.y, this.z);
			}
		}

		if(this.onGround) {
			this.xd *= GROUND_DRAG;
			this.zd *= GROUND_DRAG;
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}
}
