package com.leafia.init;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.FalloutConfigJSON;
import com.hbm.config.FalloutConfigJSON.FalloutEntry;
import com.hbm.util.LegacyMaterial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One row of the addon's fallout table: NTM-CE's {@code FalloutEntry}, built with its
 * {@code builder()}, on the base mod's {@link FalloutEntry}.
 *
 * <p>Not an upstream file. The addon's {@code FalloutConfigInit} is written against CE's entry,
 * whose outcomes are <em>block states</em> ({@code addPrimary(IBlockState, weight)}) and whose match
 * is a state, a block, or a material. This port's base entry is the 1.7.10 one: its outcomes are a
 * block and a metadata number, which it turns into a state only for the sellafield and the hive
 * block, and everything it holds is private. The addon may not edit it (D-1001), so this extends
 * it - the fields the base class keeps are never read, and every method that reads them is
 * overridden - and the base's own {@code EntityFalloutRain} calls {@link #eval} without knowing
 * the difference.
 *
 * <p>{@link #eval} is the base's, line for line, with CE's differences: it converts to a
 * <em>state</em>, a block can be matched exactly ({@code matchesState}) or by block alone
 * ({@code matchesBlock}) or by tag (the 1.7.10 blocks CE's one block stands for), "opaque" is
 * {@code isSolidRender}, and the sellafield guards read the block I am turning, not the one I turn
 * it into (none of the addon's targets is sellafield). Only primary outcomes exist: the addon's
 * thirty-odd rows never name a secondary.
 */
public class LeafiaFalloutEntry extends FalloutEntry {

	@Nullable private final BlockState matchesState;
	@Nullable private final Block matchesBlock;
	@Nullable private final TagKey<Block> matchesTag;
	@Nullable private final LegacyMaterial material;
	private final boolean opaque;
	private final List<Outcome> primary;
	private final double chance;
	private final double minDist;
	private final double maxDist;
	private final double falloffStart;
	private final boolean solid;

	/** One weighted outcome: a state, and the share of the roll it takes. */
	public record Outcome(BlockState state, int weight) {}

	private LeafiaFalloutEntry(Builder b) {
		this.matchesState = b.matchesState;
		this.matchesBlock = b.matchesBlock;
		this.matchesTag = b.matchesTag;
		this.material = b.material;
		this.opaque = b.opaque;
		this.primary = List.copyOf(b.primary);
		this.chance = b.chance;
		this.minDist = b.min;
		this.maxDist = b.max;
		this.falloffStart = b.falloff;
		this.solid = b.solid;
		// The base class's own copy of the numbers, so anything that reads them through its public
		// methods (the distance, the solid flag) sees the same row.
		max(b.max);
		sol(b.solid);
	}

	public static Builder builder() {
		return new Builder();
	}

	// ---------------------------------------------------------------- the row

	@Override
	public boolean matches(BlockState state) {
		if(matchesState != null && state != matchesState) return false;
		if(matchesBlock != null && state.getBlock() != matchesBlock) return false;
		if(matchesTag != null && !state.is(matchesTag)) return false;
		if(material != null && LegacyMaterial.of(state) != material) return false;
		return true;
	}

	@Override
	public double getMaxDist() {
		return maxDist;
	}

	@Override
	public boolean isSolid() {
		return solid;
	}

	public double getMinDist() {
		return minDist;
	}

	public List<Outcome> getPrimary() {
		return primary;
	}

	@Override
	public boolean eval(Level world, BlockPos pos, BlockState state, double dist) {

		if(dist > maxDist || dist < minDist) return false;
		if(!matches(state)) return false;
		if(opaque && !state.isSolidRender(world, pos)) return false;
		if(dist > maxDist * falloffStart && Math.abs(world.random.nextGaussian()) < Math.pow((dist - maxDist * falloffStart) / (maxDist - maxDist * falloffStart), 2D) * 3D) return false;

		if(!(chance == 1D || FalloutConfigJSON.rand.nextDouble() < chance)) return false; // there is no secondary

		Outcome conversion = choose();
		if(conversion == null) return false;

		Block b = state.getBlock();
		if(b == ModBlocks.sellafield_bedrock.get()) return false; // CE: only bedrock may replace sellafield bedrock
		if(pos.getY() == world.getMinBuildHeight()) return false; // CE: yGlobal == 0

		world.setBlock(pos, conversion.state(), 3);
		return true;
	}

	/** The base's {@code chooseRandomOutcome}, including its {@code <= 0}. */
	@Nullable
	private Outcome choose() {
		if(primary.isEmpty()) return null;

		int weight = 0;
		for(Outcome choice : primary) weight += choice.weight();

		int r = FalloutConfigJSON.rand.nextInt(weight);
		for(Outcome choice : primary) {
			r -= choice.weight();
			if(r <= 0) return choice;
		}
		return primary.get(0);
	}

	/**
	 * The base's template format, as far as it goes: the block (or tag) it matches, the material, the
	 * first outcome, the chance and the distances. Metadata is written as 0; a state has no metadata.
	 */
	@Override
	public void write(JsonWriter writer) throws IOException {
		if(matchesState != null) writer.name("matchesBlock").value(name(matchesState.getBlock()));
		if(matchesBlock != null) writer.name("matchesBlock").value(name(matchesBlock));
		if(matchesTag != null) writer.name("matchesTag").value(matchesTag.location().toString());
		if(opaque) writer.name("mustBeOpaque").value(true);
		if(material != null) writer.name("matchesMaterial").value(material.upstreamName());
		if(solid) writer.name("restrictDepth").value(true);

		writer.name("primarySubstitution");
		writer.beginArray();
		for(Outcome outcome : primary) {
			writer.beginArray();
			writer.value(name(outcome.state().getBlock()));
			writer.value(0);
			writer.value(outcome.weight());
			writer.endArray();
		}
		writer.endArray();

		if(chance != 1D) writer.name("chance").value(chance);
		if(minDist != 0.0D) writer.name("minimumDistancePercent").value(minDist);
		if(maxDist != 100.0D) writer.name("maximumDistancePercent").value(maxDist);
		if(falloffStart != 0.9D) writer.name("falloffStartFactor").value(falloffStart);
	}

	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	// ---------------------------------------------------------------- CE's builder

	public static final class Builder {

		private BlockState matchesState;
		private Block matchesBlock;
		private TagKey<Block> matchesTag;
		private LegacyMaterial material;
		private boolean opaque;
		private final List<Outcome> primary = new ArrayList<>();
		private double chance = 1.0D;
		private double min = 0.0D;
		private double max = 100.0D;
		private double falloff = 0.9D;
		private boolean solid;

		/** CE's {@code matchesState}: exactly this state. */
		public Builder matchesState(BlockState state) { this.matchesState = state; return this; }
		/** CE's {@code matchesBlock}: this block, whatever its state. */
		public Builder matchesBlock(Block block) { this.matchesBlock = block; return this; }
		/** A family of blocks 1.12.2 had as one: every log, every plank. */
		public Builder matchesTag(TagKey<Block> tag) { this.matchesTag = tag; return this; }
		public Builder matchingMaterial(LegacyMaterial material) { this.material = material; return this; }
		public Builder opaque(boolean opaque) { this.opaque = opaque; return this; }
		public Builder addPrimary(BlockState state, int weight) { this.primary.add(new Outcome(state, weight)); return this; }
		public Builder primaryChance(double chance) { this.chance = chance; return this; }
		public Builder min(double min) { this.min = min; return this; }
		public Builder max(double max) { this.max = max; return this; }
		public Builder falloff(double falloff) { this.falloff = falloff; return this; }
		public Builder solid(boolean solid) { this.solid = solid; return this; }

		public LeafiaFalloutEntry build() {
			return new LeafiaFalloutEntry(this);
		}
	}
}
