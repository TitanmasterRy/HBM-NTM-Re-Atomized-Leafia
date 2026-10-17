package com.leafia.unsorted;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
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
 * A spark that flies, falls and bounces, trailing a tapering line.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleSpark.java}, the particle the packet's {@code FiaSpark}
 * spawns. It is thrown along a frame's front vector, turned by up to {@code spread} degrees on every
 * axis, at {@code speed}; it falls at {@code 0.06} a tick, loses a thousandth of its speed a tick, and
 * loses 30% of its sideways speed every tick it spends on the ground. It lives {@code 16 / (rand x 0.8 + 0.2)}
 * ticks - 20 to 80.
 *
 * <p>The trail is the last {@code length} positions it was drawn at, a ring, joined by straight beams
 * whose width tapers from {@link #thickness} to nothing; the newest two segments are drawn partway so the
 * head follows the frame. <b>Upstream records a position when the sparks' own clock has caught up with a
 * target that starts at -65,535</b> and then advances by {@code 20 / segmentsPerTick} a <em>frame</em>, so
 * for the first nine thousand frames - longer than any spark lives - it records on every frame, and the
 * {@code segmentsPerTick} that looks like a sampling rate is not one. Kept (rule 4): the trail is the last
 * {@code length} frames.
 *
 * <p>Drawn with {@link LCEBeamPronter} through the beam particle type, full-bright and additive, from
 * the camera's frame. Upstream's {@code getFXLayer} 3 (its own layer) is the particle type here.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleSpark extends Particle {

	/** Where the trail begins its clock: far enough back that a frame always passes the test. */
	public static final double FIRST_TARGET = -65535;

	final Vec3[] positions;
	final int length;
	int curIndex = 0;
	final int color;
	public float thickness = 0.02f;
	public float segmentsPerTick = 3;
	final long startTick;
	double lastTick = FIRST_TARGET;

	public ParticleSpark(ClientLevel level, FiaMatrix mat, int color, int length, double speed, double spread) {
		super(level, mat.getX(), mat.getY(), mat.getZ());
		mat = mat.rotate(RotationOrder.XYZ, (level.random.nextDouble() * 2 - 1) * spread, (level.random.nextDouble() * 2 - 1) * spread,
				(level.random.nextDouble() * 2 - 1) * spread);
		this.xd = mat.frontVector.x * speed;
		this.yd = mat.frontVector.y * speed;
		this.zd = mat.frontVector.z * speed;
		this.color = color;
		this.lifetime = (int) (16.0D / (Math.random() * 0.8D + 0.2D));
		this.hasPhysics = true;
		this.length = length;
		this.startTick = level.getGameTime();
		this.positions = new Vec3[length];
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if(this.age++ >= this.lifetime) this.remove();

		this.yd -= 0.03D * 2;
		this.move(this.xd, this.yd, this.zd);
		this.xd *= 0.9990000128746033D;
		this.yd *= 0.9990000128746033D;
		this.zd *= 0.9990000128746033D;

		if(this.onGround) {
			this.xd *= 0.699999988079071D;
			this.zd *= 0.699999988079071D;
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {

		Vec3 cam = camera.getPosition();

		double curTime = (level.getGameTime() - startTick) + partialTicks;
		double targetTime = lastTick + 20 / segmentsPerTick;
		if(curTime >= targetTime) {
			lastTick = targetTime;
			positions[curIndex] = new Vec3(
					xo + (x - xo) * partialTicks,
					yo + (y - yo) * partialTicks,
					zo + (z - zo) * partialTicks);
			curIndex = Math.floorMod(curIndex + 1, length);
		}

		MultiBufferSource source = LeafiaParticleRenderTypes.beamSource(buffer);
		PoseStack pose = new PoseStack();
		Vec3 prevPos = null;
		for(int k = 0; k < length; k++) {
			int i = Math.floorMod(curIndex - k, length);
			Vec3 curPos = positions[i];
			if(prevPos != null && curPos != null) {
				pose.pushPose();
				if(k == length - 1) {
					Vec3 relative = curPos.subtract(prevPos);
					prevPos = prevPos.add(relative.scale(partialTicks));
				}
				pose.translate(prevPos.x - cam.x, prevPos.y - cam.y, prevPos.z - cam.z);
				Vec3 vec = curPos.subtract(prevPos);
				if(k <= 1) vec = vec.scale(partialTicks);
				LCEBeamPronter.prontBeam(pose, source, vec, EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
						color, color, 0, 1, 0, 1, thickness * (1 - k / (float) length));
				pose.popPose();
			}
			prevPos = curPos;
		}
	}

	/** The trail is drawn in absolute positions, wherever the head is. */
	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return AABB.INFINITE;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return LeafiaParticleRenderTypes.BEAM;
	}
}
