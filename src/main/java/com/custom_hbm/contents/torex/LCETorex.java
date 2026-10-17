package com.custom_hbm.contents.torex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * <b>Toroidal Convection Simulation Explosion Effect</b> - the addon's mushroom cloud. Upstream:
 * {@code com/custom_hbm/contents/torex/LCETorex.java}, "a modified version of NTMEE Torex".
 *
 * <h2>What it is, and what it is not any more</h2>
 *
 * Upstream's {@code LCETorex} is an {@code Entity} that is never in the world. It is added to
 * {@code world.weatherEffects}, which 1.12.2 ticks and renders but neither tracks, saves nor
 * chunk-loads, and it refuses to exist any other way: a torex that finds itself {@code addedToChunk}
 * (one that was {@code /summon}ed) kills itself and tells every player off. The server's copy is
 * only a <em>timer</em> - it ages, optionally waits for an explosion to finish, and dies - and the
 * cloud itself, every puff of it, is simulated by a second copy on each client, built from a packet.
 *
 * <p><b>1.21.1 has no weather-effect list, and an entity would be wrong for it.</b> A client entity
 * ticks and draws only while its chunk is loaded, and a mushroom cloud that cannot be seen from the
 * next valley is not a mushroom cloud: upstream sets {@code ignoreFrustumCheck} and
 * {@code isInRangeToRenderDist -> true} to be seen from {@code 200 + amp + amp^0.8 * 16} blocks
 * (about 3,000 at the largest scale, scale 5), straight through the view distance. So this is a plain object
 * with a plain list per side, ticked from {@code LevelTickEvent}, and nothing about it is an entity
 * type. D-1089 records this as the one structural deviation.
 *
 * <p>The two sides are still one class, as upstream's are: {@link #serverUpdate} is the timer,
 * {@link #clientUpdate} is the simulation, and the simulation depends on nothing client-only -
 * only {@code Level.getHeight}, a biome and a random source - so a server can run it, which is how
 * the GameTests exercise it. Everything that touches {@code Minecraft} is in
 * {@link LCETorexClient}.
 *
 * <h2>Public API for callers (the nukes)</h2>
 *
 * <pre>
 *   LCETorex.statFac(level, x, y, z, scale)            // nuke palette
 *   LCETorex.statFacBale(level, x, y, z, scale)        // balefire
 *   LCETorex.statFacDigamma(level, x, y, z, scale)     // digamma, red
 *   LCETorex.statFacEndo(level, x, y, z, scale)        // endothermic, blue, with its pillar
 *   // each has a (..., boolean sound) overload; sound=false also silences the shake and knockback
 *   LCETorex.bindMe = someExplosionEntity;             // optional, just before the call: see below
 * </pre>
 *
 * {@code scale} is upstream's yield-like number: a hundredth of it is the cloud's scale, clamped
 * between a quarter and five ({@link TorexMath#scaleOf}), so 100 is a scale of 1 for the balefire,
 * digamma and endothermic palettes. <b>The nuke palette first bends it</b>
 * ({@link TorexMath#nukeInput}: a 0.9 power above 10), so {@code statFac(..., 100)} is a scale of 0.79. {@code bindMe} makes the torex wait: if a
 * young explosion entity of yours (under ten ticks old, within 1.5 blocks, same dimension) is set
 * there when you spawn, the cloud's age is held back while it lives, and a
 * {@link TorexFinishPacket} releases the client when it dies. All of these return the torex, or null
 * on a client level or with {@code features.torex} off, where upstream returned nothing.
 *
 * <h2>Upstream quirks kept</h2>
 * <ul>
 *   <li>{@code setScale} multiplies the <em>current</em> heights by the scale, and the client calls
 *       it twice - once when the packet is read and again on its first tick - so a client torex's
 *       core, convection, torus and roller are {@code base * scale^2}, not {@code base * scale}.
 *       A scale-1 cloud is unaffected; a scale-5 one is five times taller than the math suggests.</li>
 *   <li>{@code sound = false} also suppresses the shake and the knockback, because upstream puts
 *       all three behind {@code if(!reachedPlayer && sound)}.</li>
 *   <li>{@code MAX_CLOUDLETS} bounds nothing (see {@link TorexMath}).</li>
 *   <li>The first loop of the endothermic palette is {@code SequencedCloudlet}s ({@code ENDOTHERMIC});
 *       its second loop, six per spawn, is the pillar ({@code ENDOTHERMIC_PILLAR}).</li>
 * </ul>
 */
public class LCETorex {

	/** Clients' clouds waiting on a {@link TorexFinishPacket}. */
	static final Set<LCETorex> waitingTorexes = new HashSet<>();

	/** The clouds each side is running. The client's is ticked and drawn; the server's only ages. */
	static final List<LCETorex> CLIENT = new ArrayList<>();
	static final java.util.Map<Level, List<LCETorex>> SERVER = new java.util.IdentityHashMap<>();

	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

	/** See the class note. Upstream's {@code bindMe}. */
	@Nullable
	public static Entity bindMe = null;

	public final Level level;
	/**
	 * Whether this is a client's copy (the simulation) rather than the server's (the timer). A torex
	 * built from a packet always is, which is what lets a test build one on a server level and run
	 * the real simulation.
	 */
	public boolean clientSide;
	public int entityId = NEXT_ID.getAndIncrement();
	public UUID uuid = UUID.randomUUID();
	/** The server's id for this cloud, which a finish packet may name instead of {@link #uuid}. */
	@Nullable
	UUID backupUUID;
	public RandomSource rand = RandomSource.create();

	public double posX;
	public double posY;
	public double posZ;
	public int ticksExisted = 0;
	public boolean isDead = false;

	public boolean reachedPlayer = false;
	public boolean sound = true;
	public boolean calculationFinished = true;
	@Nullable
	Entity boundEntity;
	int subt = 0;

	public double initPosX;
	public double initPosY;
	public double initPosZ;
	boolean valid = false;

	/** Upstream's {@code SCALE} data parameter. */
	float scale = 1.0F;
	/** Upstream's {@code TYPE} data parameter: a {@code TorexMath.TYPE_*}. */
	byte type = 0;

	public double coreHeight = 3;
	public double convectionHeight = 3;
	public double torusWidth = 3;
	public double rollerSize = 1;
	public double heat = 1;
	public double heatScaled = 1;
	public double scaleCurrent = 0;
	public double scaleGrow = 0;
	public double lastSpawnY = -1;
	public final ArrayList<Cloudlet> cloudlets = new ArrayList<>();
	public int maxAge = 1000;
	public float humidity = -1;

	float animTick = 0;
	int animTickI = 0;

	/** What the last tick decided, for the wand's debug readout. */
	public boolean lastDoSpawn = false;
	public double lastSimSpeed = 0;

	public LCETorex(Level level) {
		this.level = level;
		this.clientSide = level.isClientSide();
	}

	public BlockPos getInitialPosition() {
		return BlockPos.containing(initPosX, initPosY, initPosZ);
	}

	public boolean isValid() {
		return valid;
	}

	public void setPosition(double x, double y, double z) {
		this.posX = x;
		this.posY = y;
		this.posZ = z;
	}

	public void setDead() {
		this.isDead = true;
	}

	public LCETorex setScale(float scale) {
		this.scale = scale;
		this.coreHeight = this.coreHeight * scale;
		this.convectionHeight = this.convectionHeight * scale;
		this.torusWidth = this.torusWidth * scale;
		this.rollerSize = this.rollerSize * scale;
		this.maxAge = TorexMath.maxAge(scale);
		return this;
	}

	public LCETorex setType(int type) {
		this.type = (byte) type;
		return this;
	}

	public double getScale() {
		return this.scale;
	}

	public byte getType() {
		return this.type;
	}

	/** The age the server's timer has been held at by a living bound entity. */
	public int getSubt() {
		return subt;
	}

	@Nullable
	public Entity getBoundEntity() {
		return boundEntity;
	}

	// ----- the tick --------------------------------------------------------------------------

	/** Upstream's {@code onUpdate}, after {@code ++ticksExisted}, which the caller does. */
	public void onUpdate() {
		boolean remote = clientSide;
		if(remote && !valid) setDead();
		if(!remote) serverUpdate();
		else clientUpdate();
		if(this.ticksExisted - subt > maxAge) {
			this.setDead();
		}
		if(remote) {
			if(waitingTorexes.contains(this)) {
				if(this.isDead) waitingTorexes.remove(this);
			}
		}
	}

	/** The server's copy: keeps track of the bound entity, and times the cloud out. */
	void serverUpdate() {
		if(boundEntity != null) {
			if(boundEntity.isAlive()) {
				subt++;
				calculationFinished = false;
				animTickI = 0; // reused as a packet timeout, as upstream does
			} else {
				if(!calculationFinished) {
					if(animTickI++ < 40) calculationFinished = true;
					TorexFinishPacket packet = new TorexFinishPacket();
					packet.uuid = this.uuid;
					LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
					if(wire != null && level instanceof ServerLevel server) {
						for(ServerPlayer player : server.getServer().getPlayerList().getPlayers()) deliver(player, wire);
					}
				}
			}
		}
	}

	/** The client's copy: the whole simulation. Public so a server can run it (the tests do). */
	public void clientUpdate() {
		animTick = this.ticksExisted * TorexMath.ANIMATION_SPEED_GENERAL;
		animTickI = (int) animTick;

		double s = this.getScale();
		double cs = 1.5;
		if(this.ticksExisted == 1) this.setScale((float) s);

		if(humidity == -1) {
			humidity = level.getBiome(BlockPos.containing(posX, posY, posZ)).value().getModifiedClimateSettings().downfall();
		}

		if(lastSpawnY == -1) {
			lastSpawnY = initPosY - 3;
		}

		int spawnTarget = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(initPosX), (int) Math.floor(initPosZ)) - 3, 1);
		double moveSpeed = 0.5D;

		if(Math.abs(spawnTarget - lastSpawnY) < moveSpeed) {
			lastSpawnY = spawnTarget;
		} else {
			lastSpawnY += moveSpeed * Math.signum(spawnTarget - lastSpawnY);
		}

		// spawn mush clouds
		double range = (torusWidth - rollerSize) * 0.5;
		double simSpeed = getSimulationSpeed();
		int lifetime = TorexMath.cloudletLifetime(this.ticksExisted, maxAge, subt);
		boolean doSpawn = rand.nextInt(TorexMath.spawnBound(simSpeed)) == 0;
		lastDoSpawn = doSpawn;
		lastSimSpeed = simSpeed;
		boolean endo = getType() == TorexMath.TYPE_ENDOTHERMIC;
		double limit = TorexMath.spawnLimit(doSpawn, simSpeed, endo ? 5 : 1);
		for(int i = 0; i < limit; i++) {
			if(!endo) {
				double x = initPosX + rand.nextGaussian() * range;
				double z = initPosZ + rand.nextGaussian() * range;
				Cloudlet cloud = new Cloudlet(x, lastSpawnY, z, (float) (rand.nextDouble() * 2D * Math.PI), 0, lifetime);
				cloud.setScale((float) (Math.sqrt(s) * 3 + scaleCurrent * s), (float) (Math.sqrt(s) * 3 + scaleGrow * TorexMath.ANIMATION_SPEED_GENERAL * cs * s), (float) (Math.sqrt(s) * 3 + scaleGrow * cs * s));
				cloudlets.add(cloud);
			} else {
				SequencedCloudlet cloud = new SequencedCloudlet(
						rand.nextDouble() * Math.PI * 2,
						initPosX, initPosY, initPosZ,
						(float) (rand.nextDouble() * 2D * Math.PI), 0, (int) ((3.14 * 2) / 4 * 20 * 4));
				cloud.setScale((float) (Math.sqrt(s) * 3 + scaleCurrent * s) / 2, (float) (Math.sqrt(s) * 3 + scaleGrow * TorexMath.ANIMATION_SPEED_GENERAL * cs * s) / 2, (float) (Math.sqrt(s) * 3 + scaleGrow * cs * s) / 2);
				cloud.type = TorexType.ENDOTHERMIC;
				cloud.param0 = rand.nextDouble() + 0.75;
				cloudlets.add(cloud);
			}
		}
		if(endo) {
			double pillars = TorexMath.spawnLimit(doSpawn, simSpeed, 6);
			for(int i = 0; i < pillars; i++) {
				SequencedCloudlet cloud = new SequencedCloudlet(
						rand.nextDouble() * Math.PI * 2,
						initPosX, initPosY, initPosZ,
						(float) (rand.nextDouble() * 2D * Math.PI), 0, (int) ((Math.sqrt(s) * 3 + scaleCurrent * s) * 15 + 25));
				cloud.setScale((float) (Math.sqrt(s) * 3 + scaleCurrent * s), 0);
				cloud.type = TorexType.ENDOTHERMIC_PILLAR;
				cloud.param0 = rand.nextDouble() * 0.15 + 0.6;
				cloud.param1 = rand.nextDouble() * 0.75 + 0.5;
				cloudlets.add(cloud);
			}
		}

		if(this.ticksExisted < 120 * s) {
			level.setSkyFlashTime(2);
		}
		double tickOffset = TorexMath.tickOffset(getScale());
		double ticksExistedO = ticksExisted - tickOffset;
		int animTickIO = animTickI - (int) tickOffset;
		// spawn shock clouds
		if(TorexMath.shockActive(ticksExisted, tickOffset)) {

			int cloudCount = TorexMath.shockCount(ticksExistedO);
			int shockLife = TorexMath.shockLife(ticksExistedO);
			double shockMotion = TorexMath.shockMotion(ticksExistedO);

			for(int i = 0; i < cloudCount; i++) {
				TorexVec vec = new TorexVec((ticksExistedO + rand.nextDouble() * 2) * 1.5 * TorexMath.ANIMATION_SPEED_SHK /* make it a little faster */, 0, 0);
				float rot = (float) (Math.PI * 2 * rand.nextDouble());
				vec.rotateAroundY(rot);
				this.cloudlets.add(new Cloudlet(vec.x + initPosX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) (vec.x + initPosX) + 1, (int) (vec.z + initPosZ)), vec.z + initPosZ, rot, 0, shockLife, TorexType.SHOCK)
						.setScale((float) s * 5F * 2, (float) s * 2F * 2).setMotion(shockMotion));
			}
		}

		// spawn ring clouds
		if(this.ticksExisted < 200 && getType() != TorexMath.TYPE_ENDOTHERMIC) {
			lifetime *= s;
			for(int i = 0; i < 2; i++) {
				Cloudlet cloud = new Cloudlet(initPosX, initPosY + coreHeight, initPosZ, (float) (rand.nextDouble() * 2D * Math.PI), 0, lifetime, TorexType.RING);
				cloud.setScale(1F + (float) scaleCurrent * 0.0025F * (float) (cs * s), 1F + animTick * 0.0025F * 5F * (float) (cs * s), 1F + this.ticksExisted * 0.0025F * 5F * (float) (cs * s));
				cloudlets.add(cloud);
			}
		}

		if(this.humidity > 0 && animTick < 220 + tickOffset && animTick > tickOffset) {
			// spawn lower condensation clouds
			spawnCondensationClouds(animTickIO, this.humidity, TorexMath.FIRST_CONDENSE_HEIGHT, 80, 4, s, cs);

			// spawn upper condensation clouds
			spawnCondensationClouds(animTickIO, this.humidity, TorexMath.SECOND_CONDENSE_HEIGHT, 80, 2, s, cs);
		}

		cloudlets.removeIf(x -> x.isDead);
		for(int i = 0; i < cloudlets.size(); i++) {
			cloudlets.get(i).update();
		}

		if(getType() != TorexMath.TYPE_ENDOTHERMIC) coreHeight += TorexMath.coreHeightStep(this.ticksExisted, s);
		torusWidth += TorexMath.torusWidthStep(this.ticksExisted, s);
		rollerSize = torusWidth * 0.35;
		convectionHeight = coreHeight + rollerSize;
		scaleCurrent += TorexMath.scaleCurrentStep(this.ticksExisted, s, simSpeed);
		scaleGrow += TorexMath.scaleGrowStep(this.ticksExisted);

		int maxHeat = TorexMath.maxHeat(s, getType());
		heat = TorexMath.heat(maxHeat, this.ticksExisted, subt, maxAge);
		heatScaled = TorexMath.heatScaled(maxHeat, this.ticksExisted, maxAge);
	}

	public void spawnCondensationClouds(int age, float humidity, int height, int count, int spreadAngle, double s, double cs) {
		if((initPosY + age) > height) {

			for(int i = 0; i < (int) (5 * humidity * count / (double) spreadAngle); i++) {
				for(int j = 1; j < spreadAngle; j++) {
					float angle = (float) (Math.PI * 2 * rand.nextDouble());
					TorexVec vec = new TorexVec(0, age, 0);
					vec.rotateAroundZ((float) Math.acos((height - initPosY) / (age)) + (float) Math.toRadians(humidity * humidity * 90 * j * (0.1 * rand.nextDouble() - 0.05)));
					vec.rotateAroundY(angle);
					Cloudlet cloud = new Cloudlet(initPosX + vec.x, initPosY + vec.y, initPosZ + vec.z, angle, 0, (int) ((20 + age / 10) * (1 + rand.nextDouble() * 0.1)), TorexType.CONDENSATION);
					cloud.setScale(3F * (float) (cs * s), 4F * (float) (cs * s));
					cloudlets.add(cloud);
				}
			}
		}
	}

	/**
	 * The clock the whole cloud runs on: 1 at full speed, 0 when it has finished, in between while it
	 * starts (a boost for big clouds), slows (to a quarter over three minutes) or winds down.
	 * <b>It writes {@code subt}</b>: while a bound explosion is still calculating, the cloud's life is
	 * pinned at a quarter of its maximum and the difference is banked.
	 */
	public double getSimulationSpeed() {
		int life = this.ticksExisted - subt;
		int pauseTick = TorexMath.pauseTick(maxAge);
		if(!this.calculationFinished && (this.ticksExisted >= pauseTick)) {
			subt = this.ticksExisted - pauseTick;
			life = pauseTick;
		}
		return TorexMath.simulationSpeed(this.ticksExisted, life, maxAge, this.getScale());
	}

	public float getAlpha() {
		return TorexMath.alpha(maxAge, ticksExisted, subt);
	}

	/** The palette at {@code interp}, as a new {@code {r, g, b}}. */
	public double[] getInterpColor(double interp, byte type) {
		double[] out = new double[3];
		TorexMath.color(type, interp, out);
		return out;
	}

	// ----- the puffs -------------------------------------------------------------------------

	public enum TorexType {
		STANDARD(true),
		RING(true),
		CONDENSATION(false),
		SHOCK(false),
		ENDOTHERMIC(true),
		ENDOTHERMIC_PILLAR(true);

		public final boolean isMainEffect;

		TorexType(boolean isMain) {
			this.isMainEffect = isMain;
		}
	}

	/** Not really sequenced, upstream's comment says; it follows a parametric curve instead of the torus. */
	public class SequencedCloudlet extends Cloudlet {
		public double theta;
		public boolean first = true;
		public double t = 0;
		public double param0 = 1;
		public double param1 = 1;

		public SequencedCloudlet(double theta, double initPosX, double initPosY, double initPosZ, float angle, int age, int maxAge) {
			super(initPosX, initPosY, initPosZ, angle, age, maxAge);
			this.theta = theta;
		}

		public SequencedCloudlet(double theta, double initPosX, double initPosY, double initPosZ, float angle, int age, int maxAge, TorexType type) {
			super(initPosX, initPosY, initPosZ, angle, age, maxAge, type);
			this.theta = theta;
		}

		@Override
		public void update() {
			age++;
			this.curScale += Mth.clampedLerp(1d, 0d, (LCETorex.this.ticksExisted - 600) / 1200d) / this.growDiv * Mth.clampedLerp(this.growingScaleEnd, this.growingScale, LCETorex.this.heatScaled);
			if(age > cloudletLife) {
				this.isDead = true;
			}
			this.prevPosX = this.initPosX;
			this.prevPosY = this.initPosY;
			this.prevPosZ = this.initPosZ;

			double alpha = 0;
			double beta = 0;
			double mul = scaleCurrent * 13;
			switch(type) {
			case ENDOTHERMIC -> {
				t += 1 / 20d * 4 * getSimulationSpeed();
				double a = Math.cos(t) - 1;
				double b = Math.sin(t);

				alpha = a;
				beta = b;
				if(beta < 0) {
					double sign = Math.signum(alpha);
					alpha = Math.pow(Math.abs(alpha / 2), 0.45) * sign * 2;
				}
				beta = (b * 0.45 + Math.pow(-alpha / 2, 1.5)) / 2 * param0;
			}
			case ENDOTHERMIC_PILLAR -> {
				t += 1d / (cloudletLife - 20) * getSimulationSpeed();
				if(t < 1) {
					alpha = (1 - param0 + t * param0) * param1;
					beta = t;
				} else {
					double t1 = (age - cloudletLife + 20) / 20d;
					double ma = Math.cos(t1 * Math.PI) / 2 + 0.5;
					double mb = Math.sin(t1 * Math.PI);
					alpha = 1 * ma * param1;
					if(ma > 0.5) beta = t + 0.1 * mb;
					else beta = (t + 0.1) * mb;
				}
				beta *= 1.9;
			}
			default -> {
			}
			}
			initPosX = LCETorex.this.initPosX + alpha * Math.cos(theta) * mul;
			initPosZ = LCETorex.this.initPosZ + alpha * Math.sin(theta) * mul;
			initPosY = LCETorex.this.initPosY + beta * mul;
			if(first) {
				this.prevPosX = this.initPosX;
				this.prevPosY = this.initPosY;
				this.prevPosZ = this.initPosZ;
			}
			first = false;
			this.updateColor();
		}
	}

	/** One puff of the cloud: a billboard that moves, grows, changes colour and dies. */
	public class Cloudlet {

		public double initPosX;
		public double initPosY;
		public double initPosZ;
		public double prevPosX;
		public double prevPosY;
		public double prevPosZ;
		public double motionX;
		public double motionY;
		public double motionZ;
		public int age;
		public int cloudletLife;
		public float angle;
		public boolean isDead = false;
		float rangeMod = 1.0F;
		public float colorMod = 1.0F;
		/** The puff's colour now and a tick ago, {@code r, g, b}. */
		public double colorR, colorG, colorB;
		public double prevColorR, prevColorG, prevColorB;
		public TorexType type;
		public float startingScale = 3F;
		public float growingScale = 5F;
		public float growingScaleEnd = 5F;
		public double curScale = 0;
		public double growDiv = 1;

		public Cloudlet(double initPosX, double initPosY, double initPosZ, float angle, int age, int maxAge) {
			this(initPosX, initPosY, initPosZ, angle, age, Math.min(maxAge, age + (int) (2400 / Math.max(getSimulationSpeed(), 0.001))), TorexType.STANDARD);
			this.growDiv = maxAge;
		}

		public Cloudlet(double initPosX, double initPosY, double initPosZ, float angle, int age, int maxAge, TorexType type) {
			this.initPosX = initPosX;
			this.initPosY = initPosY;
			this.initPosZ = initPosZ;
			this.age = age;
			this.cloudletLife = maxAge;
			this.angle = angle;
			this.rangeMod = 0.3F + rand.nextFloat() * 0.7F;
			this.colorMod = 0.8F + rand.nextFloat() * 0.2F;
			this.type = type;
			this.growDiv = maxAge;

			this.updateColor();
			// Upstream's prevColor is null until the first update, and nothing reads it before then.
			this.prevColorR = colorR;
			this.prevColorG = colorG;
			this.prevColorB = colorB;
		}

		public double motionMult = 1F;
		public double motionConvectionMult = 0.5F;
		public double motionLiftMult = 0.625F;
		public double motionRingMult = 0.5F;
		public double motionCondensationMult = 1F;
		public double motionShockwaveMult = 1F;

		public void update() {
			age++;
			this.curScale += Mth.clampedLerp(1d, 0d, (LCETorex.this.ticksExisted - 600) / 1200d) / this.growDiv * Mth.clampedLerp(this.growingScaleEnd, this.growingScale, LCETorex.this.heatScaled);

			if(age > cloudletLife) {
				this.isDead = true;
			}

			this.prevPosX = this.initPosX;
			this.prevPosY = this.initPosY;
			this.prevPosZ = this.initPosZ;

			TorexVec simPos = new TorexVec(LCETorex.this.initPosX - this.initPosX, 0, LCETorex.this.initPosZ - this.initPosZ);
			double simPosX = LCETorex.this.initPosX + simPos.length();
			double simPosZ = LCETorex.this.initPosZ + 0D;

			double mult = this.motionMult;
			if(this.type.isMainEffect) mult *= getSimulationSpeed();
			switch(this.type) {
			case STANDARD -> {
				TorexVec convection = getConvectionMotion(simPosX, simPosZ);
				TorexVec lift = getLiftMotion(simPosX, simPosZ);

				double factor = Mth.clamp((this.initPosY - LCETorex.this.initPosY) / LCETorex.this.coreHeight, 0, 1);
				this.motionX = convection.x * factor + lift.x * (1D - factor);
				this.motionY = convection.y * factor + lift.y * (1D - factor);
				this.motionZ = convection.z * factor + lift.z * (1D - factor);
			}
			case RING -> {
				TorexVec motion = getRingMotion(simPosX, simPosZ);
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;
			}
			case CONDENSATION -> {
				TorexVec motion = getCondensationMotion();
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;
			}
			case SHOCK -> {
				TorexVec motion = getShockwaveMotion();
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;
			}
			default -> {
			}
			}

			this.initPosX += this.motionX * mult;
			this.initPosY += this.motionY * mult;
			this.initPosZ += this.motionZ * mult;

			this.updateColor();
		}

		TorexVec getCondensationMotion() {
			TorexVec delta = new TorexVec(initPosX - LCETorex.this.initPosX, 0, initPosZ - LCETorex.this.initPosZ).normalize();
			double speed = motionCondensationMult * LCETorex.this.getScale() * 0.125D * TorexMath.ANIMATION_SPEED_GENERAL;
			delta.x *= speed;
			delta.y = 0;
			delta.z *= speed;
			return delta;
		}

		TorexVec getShockwaveMotion() {
			TorexVec delta = new TorexVec(initPosX - LCETorex.this.initPosX, 0, initPosZ - LCETorex.this.initPosZ).normalize();
			double speed = motionShockwaveMult * LCETorex.this.getScale() * 0.25D * TorexMath.ANIMATION_SPEED_SHK;
			delta.x *= speed;
			delta.y = 0;
			delta.z *= speed;
			return delta;
		}

		TorexVec getRingMotion(double simPosX, double simPosZ) {

			if(simPosX > LCETorex.this.initPosX + torusWidth * 2) return new TorexVec(0, 0, 0);

			/* the position of the torus' outer ring center */
			TorexVec torusPos = new TorexVec(
					(LCETorex.this.initPosX + torusWidth),
					(LCETorex.this.initPosY + coreHeight * 0.35), // changed shit (used to be *0.5)
					LCETorex.this.initPosZ);

			/* the difference between the cloudlet and the torus' ring center */
			TorexVec delta = new TorexVec(torusPos.x - simPosX, torusPos.y - this.initPosY, torusPos.z - simPosZ);

			/* the distance this cloudlet wants to achieve to the torus' ring center */
			double roller = LCETorex.this.rollerSize * this.rangeMod * 0.25;
			/* the distance between this cloudlet and the torus' outer ring perimeter */
			double dist = delta.length() / roller - 1D;

			/* euler function based on how far the cloudlet is away from the perimeter */
			double func = 1D - Math.pow(Math.E, -dist); // [0;1]
			/* just an approximation, but it's good enough */
			float angle = (float) (func * Math.PI * 0.5D); // [0;90 deg]

			/* vector going from the ring center in the direction of the cloudlet, stopping at the perimeter */
			TorexVec rot = new TorexVec(-delta.x / dist, -delta.y / dist, -delta.z / dist);
			/* rotate by the approximate angle */
			rot.rotateAroundZ(angle);

			/* the direction from the cloudlet to the target position on the perimeter */
			TorexVec motion = new TorexVec(
					torusPos.x + rot.x - simPosX,
					torusPos.y + rot.y - this.initPosY,
					torusPos.z + rot.z - simPosZ);

			motion = motion.normalize();
			motion.rotateAroundY(this.angle);
			double speed = motionRingMult * 0.5D * TorexMath.ANIMATION_SPEED_GENERAL;
			motion.x *= speed;
			motion.y *= speed;
			motion.z *= speed;

			return motion;
		}

		/** Simulated on a 2D plane along the X/Y axis. */
		TorexVec getConvectionMotion(double simPosX, double simPosZ) {

			if(simPosX > LCETorex.this.initPosX + torusWidth * 2) return new TorexVec(0, 0, 0);

			TorexVec torusPos = new TorexVec(
					(LCETorex.this.initPosX + torusWidth),
					(LCETorex.this.initPosY + coreHeight),
					LCETorex.this.initPosZ);

			TorexVec delta = new TorexVec(torusPos.x - simPosX, torusPos.y - this.initPosY, torusPos.z - simPosZ);

			double roller = LCETorex.this.rollerSize * this.rangeMod;
			double dist = delta.length() / roller - 1D;

			double func = 1D - Math.pow(Math.E, -dist);
			float angle = (float) (func * Math.PI * 0.5D);

			TorexVec rot = new TorexVec(-delta.x / dist, -delta.y / dist, -delta.z / dist);
			rot.rotateAroundZ(angle);

			TorexVec motion = new TorexVec(
					torusPos.x + rot.x - simPosX,
					torusPos.y + rot.y - this.initPosY,
					torusPos.z + rot.z - simPosZ);

			motion = motion.normalize();
			motion.rotateAroundY(this.angle);

			motion.x *= motionConvectionMult;
			motion.y *= motionConvectionMult;
			motion.z *= motionConvectionMult;

			return motion;
		}

		TorexVec getLiftMotion(double simPosX, double simPosZ) {
			double scale = Mth.clamp(1D - (simPosX - (LCETorex.this.initPosX + torusWidth)), 0, 1) * motionLiftMult;

			TorexVec motion = new TorexVec(LCETorex.this.initPosX - this.initPosX, (LCETorex.this.initPosY + convectionHeight) - this.initPosY, LCETorex.this.initPosZ - this.initPosZ);

			motion = motion.normalize();
			motion.x *= scale * TorexMath.ANIMATION_SPEED_GENERAL;
			motion.y *= scale * TorexMath.ANIMATION_SPEED_GENERAL;
			motion.z *= scale * TorexMath.ANIMATION_SPEED_GENERAL;

			return motion;
		}

		public void updateColor() {
			this.prevColorR = colorR;
			this.prevColorG = colorG;
			this.prevColorB = colorB;

			double exX = LCETorex.this.initPosX;
			double exY = LCETorex.this.initPosY + LCETorex.this.coreHeight;
			double exZ = LCETorex.this.initPosZ;

			double distX = exX - initPosX;
			double distY = exY - initPosY;
			double distZ = exZ - initPosZ;

			double distSq = distX * distX + distY * distY + distZ * distZ;
			double col = TorexMath.heatInterp(distSq, LCETorex.this.heat, this.type == TorexType.SHOCK, LCETorex.this.getScale());

			double[] c = new double[3];
			TorexMath.color(LCETorex.this.getType(), col, c);
			this.colorR = c[0];
			this.colorG = c[1];
			this.colorB = c[2];
		}

		/** The puff's drawn colour at {@code interp} between last tick and this, into {@code out}. */
		public void getInterpColor(float interp, double[] out) {

			if(this.type == TorexType.CONDENSATION) {
				out[0] = out[1] = out[2] = 1F;
				return;
			}

			double greying = 0;

			if(this.type == TorexType.RING) {
				greying += 0.05;
			}

			out[0] = (prevColorR + (colorR - prevColorR) * interp) + greying;
			out[1] = (prevColorG + (colorG - prevColorG) * interp) + greying;
			out[2] = (prevColorB + (colorB - prevColorB) * interp) + greying;
		}

		public float getAlpha() {
			float alpha = (1F - ((float) age / (float) cloudletLife)) * LCETorex.this.getAlpha();
			if(this.type == TorexType.CONDENSATION) alpha *= 0.25;
			return Mth.clamp(alpha, 0.0001F, 1F);
		}

		public float getScale() {
			return startingScale + (float) curScale;
		}

		public Cloudlet setScale(float start, float grow, float growEnd) {
			this.startingScale = start;
			this.growingScale = grow;
			this.growingScaleEnd = growEnd;
			return this;
		}

		public Cloudlet setScale(float start, float grow) {
			return setScale(start, grow, grow);
		}

		public Cloudlet setMotion(double mult) {
			this.motionMult = mult;
			return this;
		}
	}

	// ----- spawning ---------------------------------------------------------------------------

	/**
	 * Upstream's {@code spawnTorex}: bind to a young explosion if {@link #bindMe} names one, record
	 * the origin, start the server's timer and tell every player in range. Server levels only.
	 */
	public static void spawnTorex(Level level, LCETorex torex) {
		if(!(level instanceof ServerLevel server)) return;
		if(torex.boundEntity == null) {
			if(bindMe != null) {
				if(bindMe.isAlive() && bindMe.tickCount < 10) {
					if(bindMe.level().dimension().equals(server.dimension())
							&& Math.sqrt(bindMe.blockPosition().distSqr(BlockPos.containing(torex.posX, torex.posY, torex.posZ))) < 1.5) {
						torex.boundEntity = bindMe;
						bindMe = null;
					}
				} else bindMe = null;
			}
		}
		torex.initPosX = torex.posX;
		torex.initPosY = torex.posY;
		torex.initPosZ = torex.posZ;
		torex.valid = true;
		SERVER.computeIfAbsent(level, k -> new ArrayList<>()).add(torex);
		TorexPacket packet = new TorexPacket();
		packet.entityId = torex.entityId;
		packet.uuid = torex.uuid;
		packet.x = torex.initPosX;
		packet.y = torex.initPosY;
		packet.z = torex.initPosZ;
		torex.calculationFinished = torex.boundEntity == null;
		packet.doWait = torex.boundEntity != null;
		packet.scale = torex.scale;
		packet.type = torex.type;
		packet.sound = torex.sound;
		packet.range = TorexMath.packetRange(torex.getScale());
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
		if(wire == null) return;
		// Upstream sends to the whole dimension and lets each client discard it by horizontal distance.
		// The client still does; the server just does the same test first and spares the others the bytes.
		for(ServerPlayer player : server.players()) {
			double dx = player.getX() - packet.x;
			double dz = player.getZ() - packet.z;
			if(Math.sqrt(dx * dx + dz * dz) <= packet.range) deliver(player, wire);
		}
	}

	/**
	 * Sends to one player, if their connection can take the addon's payload. A client without the
	 * addon cannot, and sending it one is an exception that takes the server's tick down, not a
	 * dropped packet; so is a connection that never negotiated it (a mock player's).
	 */
	static void deliver(ServerPlayer player, LeafiaCustomPacket wire) {
		if(player.connection != null && player.connection.hasChannel(LeafiaCustomPacket.TYPE)) wire.__sendToClient(player);
	}

	/** The one place a torex is made: scale already final, palette chosen. Null on a client level or with the switch off. */
	@Nullable
	private static LCETorex make(Level level, double x, double y, double z, float finalScale, int type, boolean sound) {
		if(!LeafiaFeatures.torex || level.isClientSide()) return null;
		LCETorex torex = new LCETorex(level).setScale(finalScale).setType(type);
		torex.setPosition(x, y, z);
		torex.sound = sound;
		spawnTorex(level, torex);
		return torex;
	}

	public static LCETorex statFac(Level level, double x, double y, double z, float scale) {
		return statFac(level, x, y, z, scale, true);
	}

	public static LCETorex statFac(Level level, double x, double y, double z, float scale, boolean sound) {
		return make(level, x, y, z, TorexMath.scaleOf(TorexMath.nukeInput(scale)), TorexMath.TYPE_NUKE, sound);
	}

	public static LCETorex statFacBale(Level level, double x, double y, double z, float scale) {
		return statFacBale(level, x, y, z, scale, true);
	}

	public static LCETorex statFacBale(Level level, double x, double y, double z, float scale, boolean sound) {
		return make(level, x, y, z, TorexMath.scaleOf(scale), TorexMath.TYPE_BALEFIRE, sound);
	}

	public static LCETorex statFacEndo(Level level, double x, double y, double z, float scale) {
		return statFacEndo(level, x, y, z, scale, true);
	}

	public static LCETorex statFacEndo(Level level, double x, double y, double z, float scale, boolean sound) {
		return make(level, x, y, z, TorexMath.scaleOf(scale), TorexMath.TYPE_ENDOTHERMIC, sound);
	}

	public static LCETorex statFacDigamma(Level level, double x, double y, double z, float scale) {
		return statFacDigamma(level, x, y, z, scale, true);
	}

	public static LCETorex statFacDigamma(Level level, double x, double y, double z, float scale, boolean sound) {
		return make(level, x, y, z, TorexMath.scaleOf(scale), TorexMath.TYPE_DIGAMMA, sound);
	}

	// ----- the two sides' lists ---------------------------------------------------------------

	/** Ticks every torex of one level, on whichever side that level is, and drops the dead. */
	public static void tickLevel(Level level) {
		if(!LeafiaFeatures.torex) return;
		List<LCETorex> list = level.isClientSide() ? CLIENT : SERVER.get(level);
		if(list == null || list.isEmpty()) return;
		for(int i = 0; i < list.size(); i++) {
			LCETorex torex = list.get(i);
			if(torex.level != level) continue;
			++torex.ticksExisted;
			torex.onUpdate();
		}
		list.removeIf(t -> t.level == level && t.isDead);
	}

	/** The torexes the server is timing in {@code level}. Read-only; for tests and callers that want to look. */
	public static List<LCETorex> serverTorexes(Level level) {
		List<LCETorex> list = SERVER.get(level);
		return list == null ? List.of() : java.util.Collections.unmodifiableList(list);
	}

	/** The torexes this client is simulating and drawing. */
	public static List<LCETorex> clientTorexes() {
		return java.util.Collections.unmodifiableList(CLIENT);
	}

	public static void forgetLevel(Level level) {
		SERVER.remove(level);
		CLIENT.removeIf(t -> t.level == level);
		waitingTorexes.removeIf(t -> t.level == level);
	}

	/** Takes one torex out of whichever list it is in. */
	public static void forget(LCETorex torex) {
		CLIENT.remove(torex);
		waitingTorexes.remove(torex);
		List<LCETorex> list = SERVER.get(torex.level);
		if(list != null) list.remove(torex);
	}

	static void forgetAll() {
		SERVER.clear();
		CLIENT.clear();
		waitingTorexes.clear();
		bindMe = null;
	}

	// ----- packets ----------------------------------------------------------------------------

	/**
	 * Server to client: a cloud has begun. Upstream's nested {@code TorexPacket}, with its one
	 * change: the NBT compound ({@code scale}, {@code type}, {@code sound}) is three plain fields.
	 * Nothing outside this jar read that compound, and a compound is a 1.12.2 tag-name string per
	 * value on a wire that is otherwise bit-packed.
	 */
	public static class TorexPacket implements LeafiaCustomPacketEncoder {
		public int entityId;
		public double x;
		public double y;
		public double z;
		public float scale;
		public byte type;
		public boolean sound;
		public double range = 0;
		public UUID uuid;
		public boolean doWait;

		public TorexPacket() { }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(this.entityId);
			buf.writeLong(uuid.getMostSignificantBits());
			buf.writeLong(uuid.getLeastSignificantBits());
			buf.writeDouble(this.x);
			buf.writeDouble(this.y);
			buf.writeDouble(this.z);
			buf.writeBoolean(this.doWait);
			buf.writeFloat(this.scale);
			buf.writeByte(this.type);
			buf.writeBoolean(this.sound);
			buf.writeDouble(range);
		}

		/** Reads one back. The decoder's whole job apart from what it does with the result. */
		public static TorexPacket read(LeafiaBuf buf) {
			TorexPacket p = new TorexPacket();
			p.entityId = buf.readInt();
			p.uuid = new UUID(buf.readLong(), buf.readLong());
			p.x = buf.readDouble();
			p.y = buf.readDouble();
			p.z = buf.readDouble();
			p.doWait = buf.readBoolean();
			p.scale = buf.readFloat();
			p.type = buf.readByte();
			p.sound = buf.readBoolean();
			p.range = buf.readDouble();
			return p;
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			TorexPacket p = read(buf);
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient()) ClientOnly.receive(p);
			};
		}

		@OnlyIn(Dist.CLIENT)
		private static final class ClientOnly {
			static void receive(TorexPacket message) {
				LCETorexClient.receive(message);
			}
		}
	}

	/** Server to client: the bound explosion is done, so the waiting cloud may go on. */
	public static class TorexFinishPacket implements LeafiaCustomPacketEncoder {
		public UUID uuid;

		public TorexFinishPacket() { }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeLong(uuid.getMostSignificantBits());
			buf.writeLong(uuid.getLeastSignificantBits());
		}

		public static TorexFinishPacket read(LeafiaBuf buf) {
			TorexFinishPacket p = new TorexFinishPacket();
			p.uuid = new UUID(buf.readLong(), buf.readLong());
			return p;
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			UUID id = read(buf).uuid;
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient()) finish(id);
			};
		}

		/** Releases the waiting cloud named by {@code id}. Upstream's handler body. */
		public static void finish(UUID id) {
			for(LCETorex torex : waitingTorexes) {
				if(torex.uuid.equals(id) || (torex.backupUUID != null && torex.backupUUID.equals(id))) {
					torex.calculationFinished = true;
					waitingTorexes.remove(torex);
					break;
				}
			}
		}
	}

	/**
	 * Builds the client's copy from a {@link TorexPacket}, which is what upstream's handler does
	 * inside {@code Minecraft.addScheduledTask}. Common code, so a test can call it.
	 *
	 * @return the new cloud, or null if the packet is out of range of {@code playerX/playerZ}
	 */
	@Nullable
	public static LCETorex fromPacket(Level level, TorexPacket message, double playerX, double playerZ) {
		double dx = playerX - message.x;
		double dz = playerZ - message.z;
		if(Math.sqrt(dx * dx + dz * dz) > message.range) return null;
		LCETorex torex = new LCETorex(level);
		torex.entityId = message.entityId;
		torex.uuid = message.uuid;
		torex.valid = true;
		torex.clientSide = true;
		torex.setPosition(message.x, message.y, message.z);
		torex.initPosX = message.x;
		torex.initPosY = message.y;
		torex.initPosZ = message.z;
		// readEntityFromNBT: each key present, in order
		torex.setScale(message.scale);
		torex.type = message.type;
		torex.sound = message.sound;
		CLIENT.add(torex);
		if(message.doWait) {
			torex.calculationFinished = false;
			torex.backupUUID = message.uuid;
			waitingTorexes.add(torex);
		}
		return torex;
	}
}
