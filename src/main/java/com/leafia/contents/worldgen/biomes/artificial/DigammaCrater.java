package com.leafia.contents.worldgen.biomes.artificial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.contents.worldgen.AddonBiome;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The digamma crater: the biome a digamma blast paints over five times its fallout radius, and what
 * waits in it. Upstream: {@code com/leafia/contents/worldgen/biomes/artificial/DigammaCrater.java}.
 *
 * <h2>The biome is JSON</h2>
 *
 * {@code data/leafia/worldgen/biome/digamma_crater.json}, with every number upstream's
 * {@code BiomeProperties} and overrides gave (the constants below mirror it and a test reads the
 * registered biome back against them): no rain, downfall 0, temperature 0.5 (1.12.2's default), water
 * {@code 0xFF0000}, sky {@code 0} (upstream's {@code getSkyColorByTemp}), fog {@code 0}, grass and
 * foliage {@code 0x666666}, the creature list cleared and the rest of 1.12.2's default spawns kept
 * (eight monsters, the squid, the bat). Upstream's {@code BiomeDictionary} types {@code SWAMP} and
 * {@code WET} are the {@code c:is_swamp} and {@code c:is_wet} biome tags. <b>The silence</b> that
 * {@code MixinWorldProviderNTM} gave the overworld's music inside the crater - a music type of
 * {@code LeafiaSoundEvents.literally_nothing}, delays 12,000 to 24,000 ticks - is the biome's own
 * {@code music} entry, on vanilla's {@code minecraft:intentionally_empty} (upstream's sound event has
 * no {@code sounds.json} entry, so it played nothing too, with a warning; vanilla's plays nothing
 * without one). 1.21.1 gives a creative player flying in the overworld the creative music whatever
 * the biome, which upstream's provider override did not; a non-flying player hears the silence.
 *
 * <h2>Null</h2>
 *
 * Standing in the crater, a player sees up to twenty figures between 20 and 200 blocks away, at the
 * surface, turning their heads to follow. Upstream's {@code NullEntity} is an {@code EntityLiving} that
 * is never in the world: a client-side list, ticked by {@code LeafiaPassiveLocal} and drawn by
 * {@code MixinRenderGlobal} at the head of the entity pass, and cleared by {@code MixinMinecraft} before
 * a screenshot. Here it is a plain object with the same list ({@link #NULL_LIST}), its living-entity
 * behaviour carried over (the look helper's 10 and 40 degree steps, the body helper's 75-degree lag);
 * its ticking, spawning, drawing and the screenshot are in {@link DigammaCraterClient}. Nothing in this
 * class names a client class, so the same rules run in a GameTest.
 *
 * <h2>The two packets</h2>
 *
 * {@link DigammaBackstabPacket} is the crater's one-in-30,000-ticks shove from behind (four health off
 * on the server, the shove on the client); {@link ForceSchizoPacket} switches on
 * {@link #schizoMode}, sent by upstream's {@code MixinWeaponSpecial}, which is not ported - so it is
 * registered and has no sender yet.
 */
public class DigammaCrater extends AddonBiome {

	public static final ResourceKey<Biome> KEY = ResourceKey.create(Registries.BIOME, LeafiaRef.rl("digamma_crater"));

	/* The biome's numbers, as upstream gave them. The JSON is the authority at run time; these are its mirror. */
	public static final float TEMPERATURE = 0.5F;
	public static final float DOWNFALL = 0F;
	public static final int SKY_COLOR = 0;
	public static final int FOG_COLOR = 0;
	public static final int WATER_COLOR = 0xFF0000;
	public static final int GRASS_COLOR = 0x666666;
	public static final int FOLIAGE_COLOR = 0x666666;
	/** {@code Digamma.silence}: {@code addMusicType("LEAFIA_SILENCE", literally_nothing, 12000, 24000)}. */
	public static final int MUSIC_MIN_DELAY = 12000;
	public static final int MUSIC_MAX_DELAY = 24000;
	/** Fog: starts at the eye and ends at 0.4 of where it would. */
	public static final float FOG_START_MUL = 0F;
	public static final float FOG_END_MUL = 0.4F;

	/* Null and the backstab, upstream's numbers. */
	/** {@code LeafiaPassiveLocal}: spawn while fewer than 20 counted themselves this tick. */
	public static final int NULL_LIMIT = 20;
	/** Spawned on a ring {@code 60 + rand x 80} blocks out. */
	public static final double NULL_SPAWN_MIN = 60;
	public static final double NULL_SPAWN_SPREAD = 80;
	/** A null closer than 20 or further than 200 blocks is gone. */
	public static final double NULL_KEEP_MIN = 20;
	public static final double NULL_KEEP_MAX = 200;
	/** The server-side failsafe: a null on a server lives 20 ticks. */
	public static final int NULL_SERVER_LIFE = 20;
	/** One tick in 30,000, only above 6 health, survival, first person. */
	public static final int BACKSTAB_ODDS = 30000;
	public static final float BACKSTAB_MIN_HEALTH = 6;
	public static final float BACKSTAB_DAMAGE = 4;
	public static final float BACKSTAB_STRENGTH = 0.4F;
	/** {@code ForceSchizoPacket}'s digamma, {@code CREATIVE} contamination. */
	public static final float SCHIZO_DIGAMMA = 0.2F;

	/**
	 * Upstream's {@code AddonConfig.schizoMode} ({@code enableSchizoMode}, default false): with it on,
	 * everywhere is the digamma crater. A runtime flag, set by {@link ForceSchizoPacket}'s countdown and by
	 * {@code balance.digamma.enableSchizoMode} in {@code leafia-balance.toml} when that file loads (D-3040).
	 */
	public static boolean schizoMode = false;

	/** The client's nulls. Empty on a server. */
	public static final List<NullEntity> NULL_LIST = new ArrayList<>();

	public DigammaCrater(String resource) {
		super(resource);
	}

	public static boolean isDigammaBiome(Holder<Biome> biome) {
		if(schizoMode) return true;
		return biome.is(KEY);
	}

	/** Upstream's {@code getFogColor}: {@code getSkyColorByTemp(0)}, which is 0 - opaque black. */
	@Override
	public int getFogColor() { return SKY_COLOR; }

	@Override
	public float getFogStart(float original) { return original * FOG_START_MUL; }

	@Override
	public float getFogEnd(float original) { return original * FOG_END_MUL; }

	// ------------------------------------------------------------------------------- Null

	/**
	 * One spawn attempt ({@code LeafiaPassiveLocal.priorTick}'s tail): a point {@code 60 + rand x 80} blocks
	 * from the viewer at a random angle, on the top block there; it counts only on solid ground ("stop
	 * spawning on oceans") inside the crater. Draws {@code nextDouble} for the radius, then for the angle.
	 */
	@Nullable
	public static NullEntity trySpawnNull(Level level, double viewerX, double viewerZ, RandomSource rand) {
		double r = NULL_SPAWN_MIN + rand.nextDouble() * NULL_SPAWN_SPREAD;
		double theta = rand.nextDouble() * 2 * Math.PI;
		int x = Mth.floor(viewerX + Math.cos(theta) * r);
		int z = Mth.floor(viewerZ + Math.sin(theta) * r);
		BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
		if(!level.getBlockState(p.below()).isSolid()) return null;
		if(!isDigammaBiome(level.getBiome(p))) return null;
		NullEntity entity = new NullEntity(level);
		entity.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		return entity;
	}

	/**
	 * A figure that stands and watches. Upstream extends {@code EntityLiving} with one goal that looks
	 * at the local player every tick; here the parts of {@code EntityLiving} it used are carried as
	 * fields: position, the head's yaw and pitch, the body's yaw and the body helper's counter.
	 */
	public static class NullEntity {

		/** 1.12.2's default entity height of 1.8, times 0.85. */
		public static final double EYE_HEIGHT = 1.8 * 0.85;
		/** {@code EntityLiving.getHorizontalFaceSpeed()} and {@code getVerticalFaceSpeed()}. */
		public static final float FACE_SPEED_YAW = 10;
		public static final float FACE_SPEED_PITCH = 40;
		/** {@code EntityBodyHelper}: the body lags the head by at most 75 degrees... */
		public static final float BODY_LAG = 75;
		/** ...until the head has been still (within 15 degrees) for 10 ticks, then closes over 10 more. */
		public static final float BODY_STILL = 15;

		public final Level level;
		/** Whether it ticks as a client null; a level's own side unless a test says otherwise. */
		public boolean clientSide;
		public double x, y, z;
		public float yHeadRot, yHeadRotO, xRot, xRotO, yBodyRot, yBodyRotO;
		public int tickCount;
		int timer;
		int bodyTicks;
		float bodyHeadRef;
		public boolean dead;

		public NullEntity(Level level) {
			this.level = level;
			this.clientSide = level.isClientSide;
		}

		public void setPos(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public void discard() {
			this.dead = true;
		}

		/**
		 * One tick, upstream's {@code onUpdate}: the living update (here, the body helper), death outside
		 * the crater, then the client's {@code check} - which looks at the viewer, dies if the viewer has
		 * left the crater (uncounted) or stands closer than 20 or further than 200 (still counted) - or, on
		 * a server, the 20-tick failsafe.
		 *
		 * @param viewer          the local player's feet
		 * @param viewerEyeHeight its eye height, which the head turns toward
		 * @return whether it counts toward {@link #NULL_LIMIT} this tick, as upstream's
		 *         {@code nullCounter++}
		 */
		public boolean tick(Vec3 viewer, double viewerEyeHeight) {
			this.tickCount++;
			this.yHeadRotO = this.yHeadRot;
			this.xRotO = this.xRot;
			this.yBodyRotO = this.yBodyRot;
			updateBody();
			if(!isDigammaBiome(this.level.getBiome(BlockPos.containing(this.x, this.y, this.z)))) discard();
			if(this.clientSide) return check(viewer, viewerEyeHeight);
			this.timer++;
			if(this.timer >= NULL_SERVER_LIFE) discard(); // failsafe
			return false;
		}

		private boolean check(Vec3 viewer, double viewerEyeHeight) {
			lookAt(viewer.x, viewer.y + viewerEyeHeight, viewer.z);
			if(!isDigammaBiome(this.level.getBiome(BlockPos.containing(viewer)))) {
				discard();
				return false;
			}
			double dist = viewer.distanceTo(new Vec3(this.x, this.y, this.z));
			if(dist < NULL_KEEP_MIN || dist > NULL_KEEP_MAX) discard();
			return true;
		}

		/**
		 * {@code EntityLookHelper.onUpdateLook} after {@code setLookPosition(.., 10, 40)}: the pitch is reset to
		 * 0 and turned at most 40 degrees toward the target, the head's yaw turned at most 10.
		 */
		public void lookAt(double tx, double ty, double tz) {
			this.xRot = 0;
			double dx = tx - this.x;
			double dy = ty - (this.y + EYE_HEIGHT);
			double dz = tz - this.z;
			double flat = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
			float pitch = (float) (-(Mth.atan2(dy, flat) * (180D / Math.PI)));
			this.xRot = turn(this.xRot, pitch, FACE_SPEED_PITCH);
			this.yHeadRot = turn(this.yHeadRot, yaw, FACE_SPEED_YAW);
		}

		/** 1.12.2's {@code updateRotation}: turn {@code current} toward {@code target} by at most {@code max}. */
		public static float turn(float current, float target, float max) {
			float f = Mth.wrapDegrees(target - current);
			if(f > max) f = max;
			if(f < -max) f = -max;
			return current + f;
		}

		/** {@code EntityBodyHelper.updateRenderAngles} for an entity that does not move. */
		void updateBody() {
			float bound = BODY_LAG;
			if(Math.abs(this.yHeadRot - this.bodyHeadRef) > BODY_STILL) {
				this.bodyTicks = 0;
				this.bodyHeadRef = this.yHeadRot;
			} else {
				this.bodyTicks++;
				if(this.bodyTicks > 10) bound = Math.max(1.0F - (this.bodyTicks - 10) / 10.0F, 0.0F) * BODY_LAG;
			}
			this.yBodyRot = bounded(this.yHeadRot, this.yBodyRot, bound);
		}

		/** {@code computeAngleWithBound}: {@code a} less the wrapped difference, clamped to {@code max}. */
		static float bounded(float a, float b, float max) {
			float f = Mth.wrapDegrees(a - b);
			if(f < -max) f = -max;
			if(f >= max) f = max;
			return a - f;
		}

		/** Upstream's render brightness: {@code min(((dist - 20) / 20)^2, 1)}. */
		public static float brightness(double dist) {
			return (float) Math.min(Math.pow((dist - 20) / 20, 2), 1);
		}
	}

	// ------------------------------------------------------------------------------- the backstab

	/**
	 * Upstream's {@code LeafiaClientListener.Digamma.backstab}, as arithmetic: the shove a player takes,
	 * from a direction within 45 degrees of straight behind. {@code spread} is the {@code nextFloat} that
	 * picks it. Halves the horizontal motion and pushes 0.4 back; on the ground it also halves the vertical
	 * and adds 0.4, capped at {@code 0.4000000059604645}.
	 */
	public static Vec3 backstabMotion(Vec3 motion, float yaw, float spread, boolean onGround) {
		float strength = BACKSTAB_STRENGTH;
		float shoveYaw = yaw + spread * 90 - 45;
		float xRatio = (float) -(-Math.sin(shoveYaw / 180 * Math.PI));
		float zRatio = (float) -(Math.cos(shoveYaw / 180 * Math.PI));
		float f = Mth.sqrt(xRatio * xRatio + zRatio * zRatio);
		double mx = motion.x / 2.0D - xRatio / (double) f * (double) strength;
		double mz = motion.z / 2.0D - zRatio / (double) f * (double) strength;
		double my = motion.y;
		if(onGround) {
			my /= 2.0D;
			my += strength;
			if(my > 0.4000000059604645D) my = 0.4000000059604645D;
		}
		return new Vec3(mx, my, mz);
	}

	/**
	 * Client to server: "I was shoved". The server takes four health ({@code setHealth}, not damage, as
	 * upstream - its {@code attackEntityFrom(digamma)} is commented out) and answers with the same packet,
	 * on which the client shoves itself. Upstream registers it as {@code DRX_BACKSTAB}.
	 */
	public static class DigammaBackstabPacket implements LeafiaCustomPacketEncoder {

		@Override
		public void encode(LeafiaBuf buf) { }

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Override
		@Nullable
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			return context -> {
				if(context.flow() == PacketFlow.SERVERBOUND) {
					if(context.player() instanceof ServerPlayer player) serve(player);
				} else if(FMLEnvironment.dist.isClient()) {
					DigammaCraterClient.backstab();
				}
			};
		}

		/** The server's half: the health, then the answer. */
		public static void serve(ServerPlayer player) {
			hurt(player);
			LeafiaCustomPacket.__start(new DigammaBackstabPacket()).__sendToClient(player);
		}

		/** Four health off, by {@code setHealth} (no damage event, no armour, no invulnerability frames). */
		public static void hurt(LivingEntity entity) {
			entity.setHealth(entity.getHealth() - BACKSTAB_DAMAGE);
			entity.hurtMarked = false; // upstream's velocityChanged = false
		}
	}

	/**
	 * Server to client: "lose your mind". A client not yet in schizo mode plays static and counts five
	 * ticks to it (then the record plays, see {@link DigammaCraterClient}); one already in it answers, and
	 * the server gives that player 0.2 digamma. Upstream registers it as {@code FORCE_SCHIZO}.
	 */
	public static class ForceSchizoPacket implements LeafiaCustomPacketEncoder {

		@Override
		public void encode(LeafiaBuf buf) { }

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Override
		@Nullable
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			return context -> {
				if(context.flow() == PacketFlow.CLIENTBOUND) {
					if(FMLEnvironment.dist.isClient()) DigammaCraterClient.forceSchizo();
				} else if(context.player() != null) {
					serve(context.player());
				}
			};
		}

		/** The server's half. A creative player is spared, as {@code CREATIVE} contamination always spares one. */
		public static void serve(LivingEntity player) {
			ContaminationUtil.contaminate(player, HazardType.DIGAMMA, ContaminationType.CREATIVE, SCHIZO_DIGAMMA);
		}
	}
}
