package com.leafia.unsorted;

import java.util.List;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
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
 * A bolt of lightning that lives four ticks: the "particle edition" of the DFC's shock.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleDFCShockParticleEditionTM.java}, spawned by the packet's
 * {@code DFCShockParticleEditionTM} kind, which {@code IMixinTileEntityCore.shockParticleEditionTrademark}
 * sends. It is the same bolt as {@code DFCCoreRender}'s shock - the core's path of points, each pair
 * joined by a two-layer {@code 0x5B1D00} beam a quarter of a block across, skipping a pair closer than
 * a tenth - but it belongs to no core: it is a particle, so a battery socket or a spectrometer can throw
 * one. It flickers: on three frames in four it is drawn, on the fourth it is not.
 *
 * <p>The points are absolute. Upstream translates by {@code prevPos - interpPos - pos}, which for a
 * particle that never moves is the camera's offset alone.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleDFCShockParticleEditionTM extends Particle {

	/** Upstream's {@code particleMaxAge}. */
	public static final int LIFETIME = 4;

	final List<Vec3> shock;

	public ParticleDFCShockParticleEditionTM(ClientLevel level, double x, double y, double z, List<Vec3> shock) {
		super(level, x, y, z);
		this.shock = shock;
		this.lifetime = LIFETIME;
		// A 1.21 particle is born with a random drift; upstream's three-argument constructor has none.
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		this.hasPhysics = false;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if(this.age++ >= this.lifetime) this.remove();
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		if(level.random.nextInt(4) < 1) return;
		Vec3 cam = camera.getPosition();
		MultiBufferSource source = LeafiaParticleRenderTypes.beamSource(buffer);
		PoseStack pose = new PoseStack();
		Vec3 lastPos = null;
		for(Vec3 pos : shock) {
			if(lastPos != null) {
				if(pos.distanceTo(lastPos) < 0.1) continue;
				pose.pushPose();
				pose.translate(lastPos.x - cam.x, lastPos.y - cam.y, lastPos.z - cam.z);
				Vec3 vec3 = pos.subtract(lastPos);
				LCEBeamPronter.prontBeam(pose, source, vec3, EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
						0x5B1D00, 0x7F7F7F, 0, 1, 0, 2, 0.25f);
				pose.popPose();
			}
			lastPos = pos;
		}
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
