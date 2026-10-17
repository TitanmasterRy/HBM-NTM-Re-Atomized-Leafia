package com.leafia.savedata;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * When each player last died, by name, in this dimension's game time (D-3022). The Meteor Protection Beacon reads
 * it: a beacon only works once its placer has been alive {@code balance.meteor.diverterMinAliveTime} seconds.
 *
 * <p>Upstream's {@code WorldSavedData} under {@code leafiaPlayerDeaths}, kept in the <b>per-world</b> storage - one
 * file a dimension, each timed by its own clock - which is what {@code ServerLevel.getDataStorage()} is. So, as
 * upstream, a death is recorded in the dimension the player died in, and a beacon in another dimension does not
 * see it. Written by {@code LeafiaStrikeListener} on every player death; keyed by name, as upstream, not UUID.
 */
public class PlayerDeathsSavedData extends SavedData {

	public static final String KEY = "leafiaPlayerDeaths";

	public static final SavedData.Factory<PlayerDeathsSavedData> FACTORY =
			new SavedData.Factory<>(PlayerDeathsSavedData::new, PlayerDeathsSavedData::load);

	public final Map<String, Long> timestamps = new HashMap<>();

	public static PlayerDeathsSavedData forLevel(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, KEY);
	}

	public static PlayerDeathsSavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
		PlayerDeathsSavedData data = new PlayerDeathsSavedData();
		for(String s : nbt.getAllKeys()) data.timestamps.put(s, nbt.getLong(s));
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
		for(Entry<String, Long> entry : timestamps.entrySet()) compound.putLong(entry.getKey(), entry.getValue());
		return compound;
	}
}
