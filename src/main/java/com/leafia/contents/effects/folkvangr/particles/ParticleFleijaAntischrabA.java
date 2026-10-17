package com.leafia.contents.effects.folkvangr.particles;

import com.hbm.lib.RefStrings;
import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A mote of the Folkvangr antischrab ring: it spirals outward from its origin, hugging the ground,
 * and fades out over fifty ticks.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/particles/ParticleFleijaAntischrabA.java}.
 * Each tick its radius grows by {@code 2 / radius^0.35} ({@link LeafiaParticleMath#fleijaNextPosition}),
 * starting at 1, and its angle in degrees by {@code (int)(10 / √(age + 1)) × 2}
 * ({@link LeafiaParticleMath#fleijaNextAngle}), so it spirals more slowly as it ages. Its position is
 * the origin plus {@code (cos, sin) × radius × 4}, with its height read from the world at that column.
 *
 * <p>The height is 1.12.2's {@code World.getHeight(x, z)}, the top of the light-blocking column; the
 * nearest 1.21.1 heightmap is {@code MOTION_BLOCKING} (D-1075). The cast to {@code int} truncates
 * toward zero, so on the negative side of the world the column read is the one on the other side of
 * the block edge, exactly as upstream has it.
 *
 * <p>The colour is {@code (1, 0.149, 0)}, the quad is 2 blocks to a side from the centre, and it is
 * additive on the base mod's {@code particle_base.png}. It fades from age 10 by
 * {@link LeafiaParticleMath#fleijaAlpha}, and is removed when the age reaches 50.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFleijaAntischrabA extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = RefStrings.rl("textures/particle/particle_base.png");
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(TEXTURE);

	public static final float SCALE = 2;
	public static final float RED = 1;
	public static final float GREEN = 0.149f;
	public static final float BLUE = 0;
	public static final int FADE_FROM = 10;
	public static final int LIFETIME = 50;
	/** The ring's reach: the radius is multiplied by this to get blocks. */
	public static final int RADIUS_FACTOR = 4;

	int angle;
	final double originX;
	final double originY;
	final double originZ;
	double position = 1;

	void updatePosition() {
		this.position = LeafiaParticleMath.fleijaNextPosition(this.position);
		this.angle = LeafiaParticleMath.fleijaNextAngle(this.angle, this.age);
		double theta = this.angle / 180d * Math.PI;
		double px = this.originX + Math.cos(theta) * this.position * RADIUS_FACTOR;
		double pz = this.originZ + Math.sin(theta) * this.position * RADIUS_FACTOR;
		double py = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) px, (int) pz);
		this.setPos(px, py, pz);
	}

	public ParticleFleijaAntischrabA(ClientLevel level, double x, double y, double z, int angle) {
		super(level, x, y, z);
		this.originX = x;
		this.originY = y;
		this.originZ = z;
		this.quadSize = SCALE;
		this.rCol = RED;
		this.gCol = GREEN;
		this.bCol = BLUE;
		this.angle = angle;
		this.hasPhysics = false;
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		updatePosition();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		updatePosition();
		if(this.age >= FADE_FROM) {
			this.alpha = LeafiaParticleMath.fleijaAlpha(this.age);
		}

		if(++this.age >= LIFETIME) {
			this.remove();
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
