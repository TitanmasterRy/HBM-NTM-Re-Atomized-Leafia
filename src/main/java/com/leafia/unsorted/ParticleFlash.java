package com.leafia.unsorted;

import java.util.Random;

import com.hbm.particle.helper.HbmParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
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
 * A flash: three hundred thin white spikes fanned out from one point, growing then shrinking, drawn
 * additively and full-bright.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleFlash.java}. It is the base mod's
 * {@code ParticleAmatFlash} at three times the count and with a different envelope, and it is
 * ported the same way (D-615): each spike is {@code glRotatef} five times <em>onto whatever the
 * previous spike left behind</em>, so the 300 orientations are a random walk through rotations from
 * {@code Random(432L)}, and the table of them never changes — it is built once. The two size draws
 * that follow each spike's five rotations come from the same stream.
 *
 * <p>A spike is a triangle fan: a centre at alpha 1 and three rim points at alpha 0, so each fades
 * out along its length. A fan is drawn as three triangles, each as a quad with its last corner
 * repeated. As upstream, the faces are not double-sided: a spike seen from behind is culled, so
 * about half of the 300 are visible at any moment.
 *
 * <p>Size: {@code 0.2 × 0.05 × maxScale × envelope^0.75}, and the spike lengths are
 * {@code (rand × 20 + 15) × 20} and {@code (rand × 2 + 3) × 20}, those twenties being upstream's
 * {@code intensity}. The envelope is {@link LeafiaParticleMath#flashScale}: up over {@code ticksIn},
 * down over {@code ticksOut}. Past the end of the life it goes negative and its power is NaN, which
 * draws nothing; the particle is gone by then.
 *
 * <p>It is never culled: a flash fills the sky from a point that may be off-screen.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFlash extends Particle {

	public static final int FAN_COUNT = 300;
	public static final long SEED = 432L;
	/** Upstream's {@code renderFlash} intensity argument. */
	public static final float INTENSITY = 20F;
	/** The {@code glScalef(0.2F)} inside {@code renderFlash}. */
	public static final float INNER_SCALE = 0.2F;
	/** The {@code 0.05 × maxScale} outside it. */
	public static final float OUTER_SCALE = 0.05F;
	public static final float DEFAULT_MAX_SCALE = 10;
	public static final int DEFAULT_TICKS_IN = 5;
	public static final int DEFAULT_TICKS_OUT = 10;

	/** Each fan's accumulated rotation (row-major 3x3) and its two size draws. */
	private static final float[][][] ROTATIONS = new float[FAN_COUNT][][];
	private static final float[] LENGTH = new float[FAN_COUNT];
	private static final float[] WIDTH = new float[FAN_COUNT];

	static {
		Random random = new Random(SEED);
		float[][] m = identity();
		for(int i = 0; i < FAN_COUNT; i++) {
			m = mul(m, rotationX(random.nextFloat() * 360.0F));
			m = mul(m, rotationY(random.nextFloat() * 360.0F));
			m = mul(m, rotationZ(random.nextFloat() * 360.0F));
			m = mul(m, rotationX(random.nextFloat() * 360.0F));
			m = mul(m, rotationY(random.nextFloat() * 360.0F));
			ROTATIONS[i] = m;
			LENGTH[i] = (random.nextFloat() * 20.0F + 5.0F + 1 * 10.0F) * INTENSITY;
			WIDTH[i] = (random.nextFloat() * 2.0F + 1.0F + 1 * 2.0F) * INTENSITY;
		}
	}

	/** Upstream binds {@code solid_e}, a 1x1 white; the base mod's white pixel is the same colour. */
	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.additive(HbmParticleRenderTypes.WHITE);

	private final float maxScale;
	private final int ticksIn;
	private final int ticksOut;

	public ParticleFlash(ClientLevel level, double x, double y, double z, float maxScale, int ticksIn, int ticksOut) {
		super(level, x, y, z);
		this.maxScale = maxScale;
		this.ticksIn = ticksIn;
		this.ticksOut = ticksOut;
		this.lifetime = ticksIn + ticksOut;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 cam = camera.getPosition();
		float pX = (float) (Mth.lerp((double) partialTicks, this.xo, this.x) - cam.x());
		float pY = (float) (Mth.lerp((double) partialTicks, this.yo, this.y) - cam.y());
		float pZ = (float) (Mth.lerp((double) partialTicks, this.zo, this.z) - cam.z());

		float envelope = LeafiaParticleMath.flashScale(this.age + partialTicks, this.ticksIn, this.ticksOut);
		float glScale = (float) (OUTER_SCALE * this.maxScale * Math.pow(envelope, 0.75)) * INNER_SCALE;
		int light = LightTexture.FULL_BRIGHT;

		for(int i = 0; i < FAN_COUNT; i++) {
			float vert1 = LENGTH[i];
			float vert2 = WIDTH[i];

			float[] center = { 0F, 0F, 0F };
			float[] p1 = { (float) (-0.866 * vert2), vert1, (float) (-0.5 * vert2) };
			float[] p2 = { (float) (0.866 * vert2), vert1, (float) (-0.5 * vert2) };
			float[] p3 = { 0F, vert1, vert2 };

			float[] c = transform(ROTATIONS[i], center, glScale);
			float[] w1 = transform(ROTATIONS[i], p1, glScale);
			float[] w2 = transform(ROTATIONS[i], p2, glScale);
			float[] w3 = transform(ROTATIONS[i], p3, glScale);

			quad(buffer, pX, pY, pZ, c, w1, w2, w2, light);
			quad(buffer, pX, pY, pZ, c, w2, w3, w3, light);
			quad(buffer, pX, pY, pZ, c, w3, w1, w1, light);
		}
	}

	private static void quad(VertexConsumer buffer, float pX, float pY, float pZ,
			float[] center, float[] a, float[] b, float[] c, int light) {
		vertex(buffer, pX, pY, pZ, center, 1F, light);
		vertex(buffer, pX, pY, pZ, a, 0F, light);
		vertex(buffer, pX, pY, pZ, b, 0F, light);
		vertex(buffer, pX, pY, pZ, c, 0F, light);
	}

	private static void vertex(VertexConsumer buffer, float pX, float pY, float pZ, float[] local, float alpha, int light) {
		buffer.addVertex(pX + local[0], pY + local[1], pZ + local[2])
				.setUv(0F, 0F)
				.setColor(1F, 1F, 1F, alpha)
				.setLight(light);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return AABB.INFINITE;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}

	private static float[] transform(float[][] m, float[] v, float scale) {
		return new float[] {
				scale * (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2]),
				scale * (m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2]),
				scale * (m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]),
		};
	}

	private static float[][] identity() {
		return new float[][] { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
	}

	private static float[][] mul(float[][] a, float[][] b) {
		float[][] r = new float[3][3];
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				for(int k = 0; k < 3; k++)
					r[i][j] += a[i][k] * b[k][j];
		return r;
	}

	private static float[][] rotationX(float degrees) {
		double rad = Math.toRadians(degrees);
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		return new float[][] { { 1, 0, 0 }, { 0, c, -s }, { 0, s, c } };
	}

	private static float[][] rotationY(float degrees) {
		double rad = Math.toRadians(degrees);
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		return new float[][] { { c, 0, s }, { 0, 1, 0 }, { -s, 0, c } };
	}

	private static float[][] rotationZ(float degrees) {
		double rad = Math.toRadians(degrees);
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		return new float[][] { { c, -s, 0 }, { s, c, 0 }, { 0, 0, 1 } };
	}
}
