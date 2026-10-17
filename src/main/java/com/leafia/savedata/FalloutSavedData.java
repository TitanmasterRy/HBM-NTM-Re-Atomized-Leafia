package com.leafia.savedata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockFallout;
import com.hbm.entity.effect.EntityFalloutRain;
import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaFalloutVariants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Fallout weather: where an ordinary nuke's fallout has finished sweeping, it keeps raining fallout for a
 * while - densest at ground zero, thinning outward and over time - and the ground under it keeps being
 * dusted with fallout layers. Upstream: {@code com/leafia/savedata/FalloutSavedData.java}, a
 * {@code WorldSavedData} keyed {@code leafiaFallout}, filled by {@code MixinEntityFalloutRain}'s
 * {@code setDead} injection and ticked by both passive tickers.
 *
 * <h2>What feeds it</h2>
 *
 * The base mod's fallout finish listener (D-2196), which fires when a rain has swept its last chunk -
 * upstream's {@code finished == 1} at {@code setDead}. A digamma or endothermic rain leaves no weather, as
 * upstream's flags excluded them; any other rain, the base mod's own included, does. That is the base-mod
 * behaviour this changes, so the listener does nothing unless {@code features.overhauls.falloutRain} is on.
 *
 * <h2>The numbers</h2>
 *
 * A record lives {@code 600 + radius / 100 x 120,000} ticks (a 100-block rain: 120,600, about 100
 * minutes). Its density at a point is {@code max(1 - age / life, 0) x min(sqrt(2 x (1 - d / radius)), 1)}.
 * Each server tick, one time in 50 rising to eleven in 50 as it ages ({@code nextInt(50) <= age / life x
 * 10}), it walks the chunks under its radius in random strides of 1 to 50 and drops one fallout layer on
 * the surface of a random column of each chunk it lands on, inside the radius, where fallout can sit.
 * The client keeps its own copy for the rain it draws ({@code AddonRainRender}), ticked so it thins
 * between syncs, and the server resends the list every 200 ticks and whenever a record is added or ends.
 *
 * <h2>Translations</h2>
 * <ul>
 *   <li>{@code getHeight} walks down from the top of the build height through replaceable blocks, to the
 *       bottom of the world rather than to 0; 1.12.2's {@code getActualHeight} was 256.</li>
 *   <li>{@code canPlaceBlockAt} is the block there being replaceable and the base mod's
 *       {@code BlockFallout.canSitOn}.</li>
 *   <li>The client copy is per level, replaced when the client's level changes, where upstream's was the
 *       client world's own storage.</li>
 * </ul>
 */
public class FalloutSavedData extends SavedData {

	/** Upstream's save key: the name of a file in the world folder. */
	public static final String KEY = "leafiaFallout";

	/** Every 10 seconds. */
	public static final int SYNC_INTERVAL = 10 * 20;
	/** One chance in this many, rising with age. */
	public static final int PLACE_ODDS = 50;
	/** The stride between the chunks visited: {@code nextInt(50) + 1}. */
	public static final int CHUNK_STRIDE = 50;

	public static final SavedData.Factory<FalloutSavedData> FACTORY =
			new SavedData.Factory<>(FalloutSavedData::new, FalloutSavedData::load);

	public final List<FalloutData> falloutMap = new ArrayList<>();
	public int syncTimer = 0;
	/** The level it belongs to; a server level, or the client's. */
	@Nullable Level level;

	public FalloutSavedData() { }

	public static FalloutSavedData forLevel(ServerLevel level) {
		FalloutSavedData data = level.getDataStorage().computeIfAbsent(FACTORY, KEY);
		data.level = level;
		return data;
	}

	/** A record list for {@code level} that is not the level's saved one: for tests, which must not leave records in a world that persists between runs. */
	public static FalloutSavedData detached(Level level) {
		FalloutSavedData data = new FalloutSavedData();
		data.level = level;
		return data;
	}

	private static FalloutSavedData client;

	/** The client's copy for {@code level}: a new, empty one whenever the client's level changes. */
	public static FalloutSavedData forClient(Level level) {
		if(client == null || client.level != level) {
			client = new FalloutSavedData();
			client.level = level;
		}
		return client;
	}

	// ------------------------------------------------------------------------------- one record

	public static class FalloutData {
		int timeElapsed;
		public final Vec3 pos;
		public final double radius;
		final double lifetime;

		public FalloutData(Vec3 pos, double radius) {
			this.pos = pos;
			this.radius = radius;
			this.lifetime = getLifetimeFromRadius(radius);
		}

		public FalloutData(CompoundTag compound) {
			this(new Vec3(compound.getDouble("x"), 0, compound.getDouble("z")), compound.getDouble("r"));
			this.timeElapsed = compound.getInt("t");
		}

		public static double getLifetimeFromRadius(double radius) {
			return 600 + radius / 100 * (12000 * 10);
		}

		public CompoundTag serialize() {
			CompoundTag compound = new CompoundTag();
			compound.putDouble("x", this.pos.x);
			compound.putDouble("z", this.pos.z);
			compound.putDouble("r", this.radius);
			compound.putInt("t", this.timeElapsed);
			return compound;
		}

		public void expire() {
			this.timeElapsed = (int) this.lifetime + 1;
		}

		public int getTimeElapsed() {
			return this.timeElapsed;
		}

		public void setTimeElapsed(int ticks) {
			this.timeElapsed = ticks;
		}

		public double getLifetime() {
			return this.lifetime;
		}

		/** Returns true when expired. */
		boolean tick() {
			this.timeElapsed++;
			return this.timeElapsed > this.lifetime;
		}

		public double getDensity() {
			return Math.max(1 - this.timeElapsed / this.lifetime, 0);
		}

		public double getDistanceMultiplier(Vec3 pos) {
			double baseDensity = 1 - this.pos.distanceTo(pos) / this.radius;
			return Math.min(Math.pow(baseDensity * 2, 0.5), 1);
		}
	}

	/** The record whose rain is heaviest at {@code pos} (y ignored by callers passing 0), or null outside them all. Ties go to the later. */
	@Nullable
	public FalloutData getDensest(Vec3 pos) {
		double density = 0;
		FalloutData ret = null;
		for(FalloutData data : this.falloutMap) {
			double d = data.pos.distanceTo(pos);
			if(d <= data.radius) {
				double dens = data.getDensity() * data.getDistanceMultiplier(pos);
				if(dens >= density) {
					density = dens;
					ret = data;
				}
			}
		}
		return ret;
	}

	/** Adds a record, marks the save dirty and asks for a sync on the next tick. */
	public void add(FalloutData data) {
		this.falloutMap.add(data);
		this.syncTimer = 0;
		setDirty();
	}

	// ------------------------------------------------------------------------------- the tick

	/**
	 * The surface a fallout layer lands on: from the top of the world down through every replaceable
	 * block, the first Y above something that is not. Upstream returns {@code getActualHeight() - 1} for a
	 * column that is replaceable all the way to the top, which here is the build height less one.
	 */
	public static int getHeight(Level level, int x, int z) {
		int top = level.getMaxBuildHeight();
		int y = top;
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos(x, y - 1, z);
		while(bp.getY() >= level.getMinBuildHeight()) {
			BlockState state = level.getBlockState(bp);
			if(state.canBeReplaced()) {
				y--;
				bp.set(x, y - 1, z);
			} else break;
		}
		return y < top ? y : top - 1;
	}

	public void tick() {
		tick(this.level == null ? RandomSource.create() : this.level.random);
	}

	/** Upstream's {@code tick}, with the random it draws from passed in. */
	public void tick(RandomSource rand) {
		this.syncTimer--;
		Set<Integer> ignoreX = new HashSet<>();
		Set<Integer> ignoreZ = new HashSet<>();
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		boolean server = this.level instanceof ServerLevel;
		for(int i = 0; i < this.falloutMap.size(); ) {
			FalloutData data = this.falloutMap.get(i);
			if(!data.tick()) {
				i++;
				if(server && rand.nextInt(PLACE_ODDS) <= data.timeElapsed / data.lifetime * 10) {
					int ccx = (int) (data.pos.x / 16 + 0.5);
					int ccz = (int) (data.pos.z / 16 + 0.5);
					int cr = (int) (data.radius / 16 + 1);
					int j = 0;
					int dim = cr * 2 + 1;
					while(j < dim * dim) {
						j += rand.nextInt(CHUNK_STRIDE) + 1;
						int cx = ccx - cr + j % dim;
						int cz = ccz - cr + j / dim;
						if(!ignoreX.contains(cx) || !ignoreZ.contains(cz)) {
							ignoreX.add(cx);
							ignoreZ.add(cz);
							bp.set(cx * 16 + rand.nextInt(16), 0, cz * 16 + rand.nextInt(16));
							Vec3 p = new Vec3(bp.getX() + 0.5, 0, bp.getZ() + 0.5);
							if(data.pos.distanceTo(p) <= data.radius) {
								if(!this.level.hasChunkAt(bp)) continue;
								bp.setY(getHeight(this.level, bp.getX(), bp.getZ()));
								if(this.level.getBlockState(bp).canBeReplaced() && BlockFallout.canSitOn(this.level, bp))
									this.level.setBlock(bp, ModBlocks.fallout.get().defaultBlockState(), 3);
							}
						}
					}
				}
			} else {
				this.falloutMap.remove(data);
				setDirty();
				this.syncTimer = 0;
			}
		}
		if(this.syncTimer <= 0) {
			this.syncTimer = SYNC_INTERVAL;
			if(this.level instanceof ServerLevel serverLevel)
				LeafiaCustomPacket.__start(new FalloutSyncPacket(this)).__sendToAllInDimension(serverLevel);
		}
	}

	// ------------------------------------------------------------------------------- feeding and ticking

	/**
	 * The finish listener: upstream's {@code setDead} injection. A rain that has swept its last chunk and
	 * is neither digamma nor endothermic leaves a record of its centre and its scale as the radius.
	 */
	public static final EntityFalloutRain.FinishListener FINISH_LISTENER = FalloutSavedData::onRainFinished;

	public static void onRainFinished(EntityFalloutRain rain, ServerLevel level) {
		if(!LeafiaFeatures.falloutRain) return;
		ResourceLocation variant = rain.getVariantId();
		if(LeafiaFalloutVariants.DIGAMMA.equals(variant) || LeafiaFalloutVariants.ENDOTHERMIC.equals(variant)) return;
		forLevel(level).add(new FalloutData(new Vec3(rain.getX(), 0, rain.getZ()), rain.getScale()));
	}

	/** Every server level, at the start of its tick: upstream's {@code LeafiaPassiveServer.priorTickWorld}. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Events {
		private Events() {}

		@SubscribeEvent
		public static void onLevelTick(LevelTickEvent.Pre event) {
			if(!LeafiaFeatures.falloutRain) return;
			if(event.getLevel() instanceof ServerLevel level) forLevel(level).tick();
		}
	}

	// ------------------------------------------------------------------------------- saving and syncing

	public static FalloutSavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
		FalloutSavedData data = new FalloutSavedData();
		ListTag list = nbt.getList("map", Tag.TAG_COMPOUND);
		for(int i = 0; i < list.size(); i++) data.falloutMap.add(new FalloutData(list.getCompound(i)));
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for(FalloutData data : this.falloutMap) list.add(data.serialize());
		compound.put("map", list);
		return compound;
	}

	/**
	 * Server to client: the whole list, as count then {@code x, z, radius, age} per record. Upstream's
	 * {@code FALLOUT_SYNC}.
	 */
	public static class FalloutSyncPacket implements LeafiaCustomPacketEncoder {
		public FalloutSavedData save;

		public FalloutSyncPacket() { }

		public FalloutSyncPacket(FalloutSavedData save) {
			this.save = save;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(this.save.falloutMap.size());
			for(FalloutData data : this.save.falloutMap) {
				buf.writeDouble(data.pos.x);
				buf.writeDouble(data.pos.z);
				buf.writeDouble(data.radius);
				buf.writeInt(data.timeElapsed);
			}
		}

		/** The list a sync carries. */
		public static List<FalloutData> read(LeafiaBuf buf) {
			List<FalloutData> newMap = new ArrayList<>();
			int count = buf.readInt();
			for(int i = 0; i < count; i++) {
				double x = buf.readDouble();
				double z = buf.readDouble();
				FalloutData data = new FalloutData(new Vec3(x, 0, z), buf.readDouble());
				data.timeElapsed = buf.readInt();
				newMap.add(data);
			}
			return newMap;
		}

		@Override
		@Nullable
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			List<FalloutData> newMap = read(buf);
			return context -> {
				if(FMLEnvironment.dist.isClient()) ClientOnly.replace(newMap);
			};
		}

		@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
		private static final class ClientOnly {
			static void replace(List<FalloutData> newMap) {
				Level level = net.minecraft.client.Minecraft.getInstance().level;
				if(level == null) return;
				FalloutSavedData saved = forClient(level);
				saved.falloutMap.clear();
				saved.falloutMap.addAll(newMap);
			}
		}
	}
}
