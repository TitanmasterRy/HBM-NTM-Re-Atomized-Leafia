package com.leafia.passive;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.lib.LeafiaRef;
import com.llib.math.MathLeafia;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The addon's weather-independent wind: a strength from 0 to 100 and a compass angle, both
 * drifting on their own timers, saved with the world and broadcast to every client.
 *
 * <p>Upstream: {@code com/leafia/passive/Wind.java}.
 *
 * <h2>What reads it</h2>
 *
 * The wind turbines, which take their output from {@link #power} and raytrace along
 * {@link #angle} to decide whether they are sheltered; and a cooling-tower particle mixin, which
 * blows the steam plume downwind. Neither is ported — the turbines want
 * {@code AddonBlockDummyable} and the mixin is a hand-off under D-1001 — so <b>nothing consumes
 * the wind yet.</b> It lands first because it is the one thing both of them need and it is the
 * whole of what {@code WindTurbineTEBase} is blocked on.
 *
 * <h2>Where the tick comes from</h2>
 *
 * Upstream ticks this from {@code LeafiaPassiveServer.priorTick} and {@code priorTickWorld},
 * called out of its own server-listener class. That listener is 151 lines of unrelated
 * per-player work and is not ported, so the wind ticks <em>itself</em> here — the same shape the
 * base mod's {@code RTTYSystem} took for the same reason (D-055). The phases are upstream's:
 * both hooks are the <b>Pre</b> events, because {@code priorTick}/{@code priorTickWorld} run
 * before anything else in the tick reads the wind.
 *
 * <h2>Two upstream quirks preserved rather than fixed (D-021)</h2>
 *
 * <ol>
 *   <li><b>The state is static; the save file is per-dimension.</b> {@link WindSavedData} reads
 *       and writes these static fields, so on a server with more than one dimension every
 *       dimension's file holds a copy of one shared wind, and whichever loads last wins. The
 *       wind is genuinely global — the nether and the overworld blow the same way — which is
 *       almost certainly not what a per-world save was meant to express.
 *   <li><b>One sync packet per loaded dimension per tick.</b> {@link #updateWorld} sends to
 *       <em>all</em> players, and it is called once per level, so N dimensions means N identical
 *       broadcasts every tick. Left alone because it is the difference between "the wind is
 *       chatty" and "the wind is different", and only the second would be a port bug.
 * </ol>
 *
 * Both are reachable by a player and neither is fixed here. See D-1027.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public class Wind {

	/**
	 * How strongly the wind blows in each dimension, as a multiplier. Upstream seeds only the
	 * overworld and every other dimension defaults to zero at the one call site — the cooling
	 * tower's particles — so the wind moves nothing outside the overworld.
	 *
	 * <p>Keyed by {@link ResourceKey} rather than upstream's integer dimension id, which 1.21.1
	 * does not have.
	 */
	public static Map<ResourceKey<Level>, Double> dimensionWindMultiplier = new HashMap<>();
	static {
		dimensionWindMultiplier.put(Level.OVERWORLD, 1d);
	}

	public static double power = 0;
	public static int randomShortWindCounter = 0;
	public static int randomLongWindCounter = 0;
	public static int randomShortWindLength = 200;
	public static int randomLongWindLength = 2000;
	public static boolean strongWind = false;
	public static double startPower = 0;
	public static double endPower = 0;

	public static double angle = 0;
	public static double lastAngle = 0;
	public static int randomAngleCounter = 0;
	public static int randomAngleLength = 200;
	public static double startAngle = 0;
	public static double endAngle = 0;

	/** Upstream's save key, kept: it is the name of a file in a player's world folder. */
	public static final String SAVE_KEY = "leafiaWind";

	/**
	 * The world save. Upstream's {@code WorldSavedData} on 1.21.1's {@link SavedData}, which
	 * differs in one way that matters: a {@code Factory} supplies both the fresh instance and
	 * the loaded one, so the "if it is null, make one and ask again" dance upstream does in
	 * {@code forWorld} has no successor — {@code computeIfAbsent} is that dance.
	 */
	public static class WindSavedData extends SavedData {

		public static final SavedData.Factory<WindSavedData> FACTORY =
				new SavedData.Factory<>(WindSavedData::new, (tag, registries) -> {
					Wind.readFromNBT(tag);
					return new WindSavedData();
				});

		public static WindSavedData forLevel(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(FACTORY, SAVE_KEY);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			return Wind.writeToNBT(tag);
		}
	}

	public static void readFromNBT(CompoundTag compound) {
		power = compound.getDouble("power");
		randomShortWindCounter = compound.getInt("shortWindCounter");
		randomShortWindLength = compound.getInt("shortWindLength");
		randomLongWindCounter = compound.getInt("longWindCounter");
		randomLongWindLength = compound.getInt("longWindLength");
		strongWind = compound.getBoolean("strong");
		startPower = compound.getDouble("startPower");
		endPower = compound.getDouble("endPower");
		angle = compound.getDouble("angle");
		lastAngle = compound.getDouble("lastAngle");
		randomAngleCounter = compound.getInt("angleCounter");
		randomAngleLength = compound.getInt("angleLength");
		startAngle = compound.getDouble("startAngle");
		endAngle = compound.getDouble("endAngle");
	}

	public static CompoundTag writeToNBT(CompoundTag compoundIn) {

		CompoundTag compound = compoundIn == null ? new CompoundTag() : compoundIn;

		compound.putDouble("power", power);
		compound.putInt("shortWindCounter", randomShortWindCounter);
		compound.putInt("shortWindLength", randomShortWindLength);
		compound.putInt("longWindCounter", randomLongWindCounter);
		compound.putInt("longWindLength", randomLongWindLength);
		compound.putBoolean("strong", strongWind);
		compound.putDouble("startPower", startPower);
		compound.putDouble("endPower", endPower);
		compound.putDouble("angle", angle);
		compound.putDouble("lastAngle", lastAngle);
		compound.putInt("angleCounter", randomAngleCounter);
		compound.putInt("angleLength", randomAngleLength);
		compound.putDouble("startAngle", startAngle);
		compound.putDouble("endAngle", endAngle);
		return compound;
	}

	/**
	 * How far a gust may swing from where it is now, and the whole of what decides the wind's
	 * shape: a calm target of 5 and a strong target of {@code 100 - range}, each approached a
	 * quarter at a time, with the result clamped to one {@code range} either side of it.
	 */
	static final double range = 20;

	/**
	 * Public where upstream is package-private, so the clamps can be swept from a test with a
	 * {@link Random} that rolls a chosen number. Nothing else about it changes.
	 */
	public static double calculateTargetPower(Random rand) {

		double basePower = strongWind ? 100 - range : 5;
		double outPower = power + rand.nextDouble() * range * 2 - range;
		double adjustment = basePower - power;
		outPower += adjustment / 4;

		if(outPower < 0) outPower = 0;
		if(outPower < basePower - range) outPower = basePower - range;
		if(outPower > 100) outPower = 100;
		if(outPower > basePower + range) outPower = basePower + range;

		return outPower;
	}

	static Random rand = new Random();

	/** Upstream's per-world half: tell everyone, and mark the file dirty. */
	public static void updateWorld(Level level) {

		if(level.isClientSide) return;

		LeafiaCustomPacket.__start(new WindSyncPacket()).__sendToAll();

		if(level instanceof ServerLevel server) WindSavedData.forLevel(server).setDirty();
	}

	/** Upstream's global half. Server side only — the client is told, it does not simulate. */
	public static void update() {

		lastAngle = angle;

		if(rand.nextBoolean()) {

			randomAngleCounter++;

			if(randomAngleCounter > randomAngleLength) {
				randomAngleCounter = 0;
				randomAngleLength = 180 + rand.nextInt(40);
				lastAngle = MathLeafia.positiveModulo(lastAngle, 360);
				angle = MathLeafia.positiveModulo(angle, 360);
				startAngle = angle;
				endAngle = angle + rand.nextDouble(45 * 2) - 45;
			}

			angle = lerp(startAngle, endAngle, (double) randomAngleCounter / randomAngleLength);
		}

		randomShortWindCounter++;
		randomLongWindCounter++;

		if(randomLongWindCounter > randomLongWindLength) {
			randomLongWindCounter = 0;
			randomLongWindLength = rand.nextInt(1000) + 1750;
			strongWind = rand.nextFloat() >= 0.66f;
		}

		if(randomShortWindCounter > randomShortWindLength) {
			randomShortWindCounter = 0;
			randomShortWindLength = rand.nextInt(200) + 150;
			startPower = power;
			endPower = calculateTargetPower(rand);
		}

		power = lerp(startPower, endPower, (double) randomShortWindCounter / randomShortWindLength);
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/*
	 * The two hooks, in upstream's phases. Gated on the feature switch rather than on
	 * registration: the packet type must be registered whether the wind blows or not, because a
	 * type id is an index and skipping one renumbers every packet after it (D-1024).
	 */

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Pre event) {
		if(LeafiaFeatures.wind) update();
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Pre event) {
		if(LeafiaFeatures.wind) updateWorld(event.getLevel());
	}

	/**
	 * Three doubles to every client, every tick, per loaded dimension. Upstream's, and the reason
	 * the addon has a bit-packed wire format at all.
	 */
	public static class WindSyncPacket implements LeafiaCustomPacketEncoder {

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeDouble(power);
			buf.writeDouble(angle);
			buf.writeDouble(lastAngle);
		}

		@Override
		public @Nullable Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			double p = buf.readDouble();
			double a = buf.readDouble();
			double la = buf.readDouble();

			return ctx -> {
				power = p;
				angle = a;
				lastAngle = la;
			};
		}
	}
}
