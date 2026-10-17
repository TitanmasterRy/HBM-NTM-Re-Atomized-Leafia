package com.leafia.init;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.FalloutConfigJSON;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.util.LegacyMaterial;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * The addon's fallout: what the ground becomes under a blast, in place of the base mod's slaked
 * sellafield, and the two tables its own explosions use.
 *
 * <p>Ported from {@code com.leafia.init.FalloutConfigInit}. Three tables:
 *
 * <ul>
 * <li>{@link FalloutConfigJSON#entries}, the base mod's table, which the addon <b>edits in place</b>,
 *     as upstream does: it throws out every row matching grass, ground or sand - the rows that cook
 *     the surface into slaked sellafield - and both trinitite rows, then appends its own: twelve a
 *     level, five levels {@code i = 1..5} nearest first, each reaching {@code 50 + 9i} percent of
 *     the blast radius (59, 68, 77, 86 and 95) and made of the legacy contamination blocks, level
 *     {@code 7 - i} (6 down to 2);
 * <li>{@link #digammaEntries}, six levels of digammitite over iron and rock, out to 45 percent -
 *     the table a digamma blast uses;
 * <li>{@link #endoEntries}, five rows turning grass, dirt, logs and planks to their frozen forms
 *     out to 45 percent - the table an endothermic blast uses.
 * </ul>
 *
 * The last two are read by the explosion batch's mixins (D-3004); nothing here reads them.
 *
 * <h2>When it runs</h2>
 *
 * Upstream edits the table once, after CE has built it. The base mod rebuilds its table every time a
 * server starts ({@code FalloutConfigJSON.initialize}, on {@code ServerAboutToStartEvent}, and then
 * reads a player's {@code hbmFallout.json} if there is one), so a one-off edit would be undone at the
 * first world. This runs on the same event at <b>low priority</b>, after the base's, every time, and
 * is idempotent: it removes its own rows before it adds them. A player's own file is still
 * edited - upstream's was too.
 *
 * <p>Gated by {@link LeafiaFeatures#falloutOverhaul} (off) and {@link LeafiaFeatures#legacyWaste},
 * because it removes base-mod rows (D-1002); the two private lists are filled whenever their blocks
 * exist, since only the addon's own code reads them.
 *
 * <h2>What changed in the translation</h2>
 *
 * <ul>
 * <li>Upstream removes rows with reflection on the entry's private fields. Here the base's public
 *     {@code write} serialises a row into the same JSON the template file uses, and the match is
 *     read back out of that - no reflection into {@code com.hbm}.
 * <li>The rows are {@link LeafiaFalloutEntry}, CE's builder on the base class; see there.
 * <li>1.12.2 blocks are their 1.21.1 successors: {@code GRASS} is the grass block, {@code LOG} and
 *     {@code LOG2} are every overworld log, {@code PLANKS} every plank, {@code HARDENED_CLAY} is
 *     terracotta, {@code SNOW_LAYER} at one layer is a one-layer snow, and the two sands are two
 *     blocks. The trinitite rows target the base mod's trinitite, which has no levels (one block
 *     with random textures), so all five levels of it are the same block - only the distance
 *     differs.
 * <li>Upstream's snow-layer rows with a state per layer are commented out upstream; so are they here
 *     (not carried).
 * </ul>
 *
 * <p><b>Two things upstream does that are kept and look like mistakes:</b>
 * <ul>
 * <li>the terracotta row is inserted at index 0 five times, one per level, so the list starts with
 *     level 2's row and ends that run with level 6's - and the first matching row wins, so every
 *     terracotta within 95 percent of the radius becomes level 2, and levels 3 to 6 are never made;
 * <li>every row is {@code opaque(true)}, which is {@code isOpaqueCube} upstream and
 *     {@code isSolidRender} here. A snow layer and ice are not opaque, so the one-layer snow rows
 *     and the ice row for {@code Material.ICE} never match anything; only packed ice (opaque) and the
 *     rest do.
 * </ul>
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class FalloutConfigInit {

	private FalloutConfigInit() {}

	/** The base mod's table - the live list, not a copy. */
	public static final List<FalloutEntry> entries = FalloutConfigJSON.entries;
	public static final List<FalloutEntry> digammaEntries = new ArrayList<>();
	public static final List<FalloutEntry> endoEntries = new ArrayList<>();

	/** Upstream's {@code mul = 1f/5*3}, in float: the level ladder's step. */
	public static final float LADDER_MUL = 1f / 5 * 3;
	/** Upstream's {@code offset = 50}. */
	public static final float LADDER_OFFSET = 50;

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		onInit();
	}

	/**
	 * Upstream's {@code onInit}. Safe to call any number of times, and safe with either feature off.
	 */
	public static void onInit() {

		entries.removeIf(e -> e instanceof LeafiaFalloutEntry);
		digammaEntries.clear();
		endoEntries.clear();

		if(LeafiaBlocks.digammitite != null) buildDigamma();
		buildEndo();

		if(LeafiaFeatures.falloutOverhaul && LeafiaFeatures.legacyWaste && LeafiaBlocks.waste_dirt != null)
			editBaseTable();
	}

	private static void buildDigamma() {
		for(int i = 1; i <= 6; i++) {
			int meta = 6 - i;
			BlockState state = LeafiaBlocks.digammitite.get().withMeta(meta);
			digammaEntries.add(LeafiaFalloutEntry.builder().addPrimary(state, 1).max(i * 7.5).opaque(true).solid(true)
					.matchingMaterial(LegacyMaterial.IRON).build());
			digammaEntries.add(LeafiaFalloutEntry.builder().addPrimary(state, 1).max(i * 7.5).opaque(true).solid(true)
					.matchingMaterial(LegacyMaterial.ROCK).build());
		}
	}

	private static void buildEndo() {
		double reach = 6 * 7.5;
		endoEntries.add(LeafiaFalloutEntry.builder().addPrimary(ModBlocks.frozen_grass.get().defaultBlockState(), 1)
				.max(reach).opaque(true).solid(true).matchesBlock(Blocks.GRASS_BLOCK).build());
		endoEntries.add(LeafiaFalloutEntry.builder().addPrimary(ModBlocks.frozen_dirt.get().defaultBlockState(), 1)
				.max(reach).opaque(true).solid(true).matchingMaterial(LegacyMaterial.GROUND).build());
		// LOG and LOG2 are two rows upstream because 1.12.2 split its logs across two blocks; one tag here.
		endoEntries.add(LeafiaFalloutEntry.builder().addPrimary(ModBlocks.frozen_log.get().defaultBlockState(), 1)
				.max(reach).opaque(true).solid(true).matchesTag(BlockTags.OVERWORLD_NATURAL_LOGS).build());
		endoEntries.add(LeafiaFalloutEntry.builder().addPrimary(ModBlocks.frozen_planks.get().defaultBlockState(), 1)
				.max(reach).opaque(true).solid(true).matchesTag(BlockTags.PLANKS).build());
	}

	/** The rest of upstream's {@code onInit}: the in-place edit of the base mod's table. */
	private static void editBaseTable() {

		removeByMatchingMaterial(LegacyMaterial.GRASS);
		removeByMatchingMaterial(LegacyMaterial.GROUND);
		removeByMatchingMaterial(LegacyMaterial.SAND);
		removeByPrimary(ModBlocks.waste_trinitite.get());
		removeByPrimary(ModBlocks.waste_trinitite_red.get());

		entries.add(LeafiaFalloutEntry.builder().addPrimary(LeafiaBlocks.waste_ice.get().defaultBlockState(), 1)
				.max(3 * 15 + 25).opaque(true).solid(true).matchingMaterial(LegacyMaterial.ICE).build());

		for(int i = 1; i <= 5; i++) {
			int m = 7 - i;
			float mul = LADDER_MUL;
			float offset = LADDER_OFFSET;
			double max = i * 15 * mul + offset;

			entries.add(row(LeafiaBlocks.waste_snow.get().withMeta(m), max).matchesState(Blocks.SNOW.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_gravel.get().withMeta(m), max).matchesState(Blocks.GRAVEL.defaultBlockState()).build());
			entries.add(row(ModBlocks.waste_trinitite_red.get().defaultBlockState(), max).primaryChance(0.05)
					.matchesState(Blocks.RED_SAND.defaultBlockState()).build());
			entries.add(row(ModBlocks.waste_trinitite.get().defaultBlockState(), max).primaryChance(0.05)
					.matchesState(Blocks.SAND.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_sand_red.get().withMeta(m), max).matchesState(Blocks.RED_SAND.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_red_sandstone.get().withMeta(m), max).matchesState(Blocks.RED_SANDSTONE.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_sand.get().withMeta(m), max).matchesState(Blocks.SAND.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_sandstone.get().withMeta(m), max).matchesState(Blocks.SANDSTONE.defaultBlockState()).build());
			entries.add(0, row(LeafiaBlocks.waste_terracotta.get().withMeta(m), max).matchesState(Blocks.TERRACOTTA.defaultBlockState()).build());
			entries.add(row(LeafiaBlocks.waste_ice.get().defaultBlockState(), max).matchingMaterial(LegacyMaterial.PACKED_ICE).build());
			entries.add(row(LeafiaBlocks.waste_dirt.get().withMeta(m), max).matchingMaterial(LegacyMaterial.GROUND).build());
			entries.add(row(LeafiaBlocks.scorched_earth.get().withMeta(m), max).matchingMaterial(LegacyMaterial.GRASS).build());
		}
	}

	/** The part every one of the thirty-odd rows has in common: one outcome, opaque, solid, out to {@code max}. */
	private static LeafiaFalloutEntry.Builder row(BlockState outcome, double max) {
		return LeafiaFalloutEntry.builder().addPrimary(outcome, 1).max(max).opaque(true).solid(true);
	}

	// ---------------------------------------------------------------- removal

	/**
	 * What a row says about itself, through the base's public serialiser. The template file the base
	 * writes is this JSON, so it is the one view of a row the base class offers without reflection.
	 */
	private static JsonObject describe(FalloutEntry entry) {
		StringWriter out = new StringWriter();
		try(JsonWriter writer = new JsonWriter(out)) {
			writer.beginObject();
			entry.write(writer);
			writer.endObject();
		} catch(IOException e) {
			throw new IllegalStateException("could not describe a fallout row", e);
		}
		return JsonParser.parseString(out.toString()).getAsJsonObject();
	}

	/** Upstream's {@code removeByMatchingMaterial}. */
	public static int removeByMatchingMaterial(LegacyMaterial material) {
		int before = entries.size();
		entries.removeIf(entry -> {
			JsonObject json = describe(entry);
			return json.has("matchesMaterial") && json.get("matchesMaterial").getAsString().equals(material.upstreamName());
		});
		return before - entries.size();
	}

	/** Upstream's {@code removeByPrimary}: every row any of whose outcomes is this block. */
	public static int removeByPrimary(Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		int before = entries.size();
		entries.removeIf(entry -> {
			JsonObject json = describe(entry);
			if(!json.has("primarySubstitution")) return false;
			JsonArray outcomes = json.getAsJsonArray("primarySubstitution");
			for(JsonElement outcome : outcomes)
				if(outcome.getAsJsonArray().get(0).getAsString().equals(id)) return true;
			return false;
		});
		return before - entries.size();
	}
}
