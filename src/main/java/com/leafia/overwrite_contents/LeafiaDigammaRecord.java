package com.leafia.overwrite_contents;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;

/**
 * Every record plays the digamma record in the digamma crater (D-3026).
 *
 * <p>Upstream: the second injection of {@code MixinWorld}, at the head of {@code World.playEvent}. A record being played (event 1010
 * with a record's item id for data) in the digamma biome was cancelled and sent again with the id of {@code AddonItems.digammaRecord}
 * (the hidden record "digma balls", which has no tab and no recipe: the event is the only thing that ever carries it); a data of 0 was
 * "stop" in 1.12.2 and was let through.
 *
 * <p>In 1.21.1 a jukebox song is a datapack registry entry, the event's data is its registry id, and stopping is its own event (1011),
 * so there is no zero to exempt. The disc item is not ported: it is an inner class of {@code AddonItems}, nothing hands it to a
 * player, and what the event needs is the song, {@code leafia:digamma} - the same sound the schizo-mode static ends with
 * ({@code music.wtf}), 177.23 seconds, its description the obfuscated "digma balls". The song file carries the digamma crater's
 * feature condition.
 */
public final class LeafiaDigammaRecord {

	private LeafiaDigammaRecord() {}

	public static final ResourceKey<JukeboxSong> SONG = ResourceKey.create(Registries.JUKEBOX_SONG, LeafiaRef.rl("digamma"));

	/** The event data to send in place of {@code data}: the digamma song's id when a record is being played in the crater, else {@code data}. */
	public static int redirect(Level level, int type, BlockPos pos, int data) {
		return redirect(level, type, level.getBiome(pos), data);
	}

	/** {@link #redirect(Level, int, BlockPos, int)} for the biome at the jukebox, which is all it asks of the position. */
	public static int redirect(Level level, int type, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, int data) {
		if(type != 1010 || !LeafiaFeatures.digammaCrater) return data;
		var songs = level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
		var digamma = songs.getHolder(SONG);
		if(digamma.isEmpty()) return data;
		int id = songs.getId(digamma.get().value());
		if(data == id || !DigammaCrater.isDigammaBiome(biome)) return data;
		return id;
	}
}
