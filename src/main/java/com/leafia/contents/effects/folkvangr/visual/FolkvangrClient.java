package com.leafia.contents.effects.folkvangr.visual;

import java.util.ArrayList;
import java.util.List;

import com.hbm.render.entity.effect.RenderCloudFleija;
import com.hbm.render.entity.effect.RenderCloudRainbow;
import com.leafia.lib.LeafiaRef;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.VacuumInstance;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaAntischrabA;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaAntischrabB;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaCloud;
import com.leafia.contents.effects.folkvangr.particles.ParticleFleijaVacuum;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.leafia.overwrite_contents.LeafiaFleijaClouds.CloudState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The client half of the cloud and the Folkvangr: the two render delegates, the particles the cloud sheds
 * ({@code MixinEntityCloudFleija.spawnParticle} and {@code remoteUpdate}'s antischrabidium rings), and the
 * vacuum's local pull ({@code FolkvangrVacuumPacket.Handler}: its list, {@code onMessage} and
 * {@code localTick}, which upstream's client listener ran every client tick).
 *
 * <p>The vacuum runs on {@link LevelTickEvent.Post} of the client level, beside the camera shake that
 * shares upstream's call site. For each vacuum still running it pulls the local player as the server pulls
 * entities, sheds between {@code 5} and {@code 11} vacuum particles on random headings ({@code -75..75}
 * pitch) between {@code 1.2 r} and {@code 1.2 r + 0.6 (3r - r)} out, and counts down; with no player in the
 * world it does nothing at all, not even count, as upstream's did not.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class FolkvangrClient {

	private FolkvangrClient() {}

	static final List<VacuumInstance> vacuums = new ArrayList<>();

	private static boolean installed = false;

	public static void install() {
		if(installed) return;
		installed = true;
		RenderCloudFleija.addRenderDelegate(LCERenderCloudFleija.DELEGATE);
		RenderCloudRainbow.addRenderDelegate(LCERenderCloudRainbow.DELEGATE);
		LeafiaFleijaClouds.clientTick = FolkvangrClient::cloudTick;
		EntityNukeFolkvangr.clientVacuum = v -> Minecraft.getInstance().execute(() -> vacuums.add(v));
	}

	// ------------------------------------------------------------------ the cloud's particles

	static void cloudTick(Entity cloud) {
		if(!(cloud.level() instanceof ClientLevel level)) return;
		CloudState st = LeafiaFleijaClouds.state(cloud);
		if(!st.spawnedInitial) {
			st.spawnedInitial = true;
			spawnParticle(level, cloud, 5);
			spawnParticle(level, cloud, 4);
			spawnParticle(level, cloud, 7);
			spawnParticle(level, cloud, 6);
		}
		if(level.random.nextInt(16) == 0) spawnParticle(level, cloud, LeafiaFleijaClouds.getScale(cloud) * 0.75);
		RandomSource rand = cloud.getRandom();
		if(st.isAntischrab && st.didInitialEffects < 5) {
			st.didInitialEffects++;
			int angRand = rand.nextInt(360);
			for(int i = 0; i < 360; i += 5) {
				Minecraft.getInstance().particleEngine.add(new ParticleFleijaAntischrabA(level, cloud.getX(), cloud.getY(), cloud.getZ(), i + angRand));
			}
		}
		if(st.isAntischrab && cloud.tickCount == 5) {
			int addRand = rand.nextInt(360);
			for(int i = addRand; i < 360 + addRand; i += 10) {
				for(float i2 = 1; i2 <= 6; i2 += 0.5F) {
					Minecraft.getInstance().particleEngine.add(new ParticleFleijaAntischrabB(level, cloud.getX(), cloud.getY(), cloud.getZ(),
							(float) Math.cos(i / 180.0d * Math.PI) * i2 * 1.414f, (float) Math.sin(i / 180.0d * Math.PI) * i2 * 1.414f));
				}
			}
		}
	}

	/** Upstream's {@code spawnParticle(radius)}: a ring of cloud particles on one random great circle. */
	static void spawnParticle(ClientLevel level, Entity cloud, double radius) {
		RandomSource rand = cloud.getRandom();
		float yaw = rand.nextFloat() * 360;
		Vec3 particleDir = Vec3.directionFromRotation(rand.nextFloat() * 120 - 60, yaw);
		Vec3 rightVector = Vec3.directionFromRotation(0, yaw + 90);
		double length = radius * 0.8;
		double theta = 1 / (2 * radius * Math.PI);
		for(double offs = 0; offs < length / theta; offs += 1 / theta) {
			Vec3 forward = particleDir.scale(Math.cos(theta * offs));
			Vec3 right = rightVector.scale(Math.sin(theta * offs));
			for(int i = -1; i <= ((offs == 0) ? 1 : -1); i++) {
				Minecraft.getInstance().particleEngine.add(new ParticleFleijaCloud(level, forward, right, cloud));
			}
		}
	}

	// ------------------------------------------------------------------ the vacuum

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if(!event.getLevel().isClientSide()) return;
		localTick();
	}

	static void localTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if(player == null || level == null) return;
		int i = 0;
		while(i < vacuums.size()) {
			VacuumInstance vacuum = vacuums.get(i);
			double distance = player.position().distanceTo(vacuum.pos);
			if(distance <= vacuum.vacuumEnd) {
				Vec3 pull = EntityNukeFolkvangr.pullOn(player.position(), vacuum.pos, distance, vacuum.vacuumStart, vacuum.vacuumEnd, vacuum.vacuumForce);
				player.setDeltaMovement(player.getDeltaMovement().add(pull));
			}
			RandomSource rand = level.random;
			for(int p = -rand.nextInt(4) * 2; p <= 10; p++) {
				Vec3 particleDir = Vec3.directionFromRotation(rand.nextFloat() * 150 - 75, rand.nextFloat() * 360)
						.scale(vacuum.vacuumStart * 1.2 + rand.nextDouble() * ((vacuum.vacuumEnd - vacuum.vacuumStart) * 0.6));
				ParticleFleijaVacuum fx = new ParticleFleijaVacuum(level,
						vacuum.pos.x + particleDir.x, vacuum.pos.y + particleDir.y, vacuum.pos.z + particleDir.z,
						rand.nextFloat() * 2 + 2, rand.nextFloat() * 0.1f + 0.1f, vacuum);
				mc.particleEngine.add(fx);
			}
			vacuum.postEffect--;
			if(vacuum.postEffect <= 0) vacuums.remove(i);
			else i++;
		}
	}
}
