package com.leafia.contents.machines.reactors.lftr.components.element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import com.hbm.blocks.IRadResistantBlock;
import com.hbm.inventory.OreDictManager;
import com.hbm.util.Tuple.Pair;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryBlock;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryTE;
import com.leafia.contents.machines.reactors.pwr.blocks.PWRReflectorBlock;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.math.FiaMatrix;
import com.llib.group.LeafiaMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The LFTR's reaction chamber: the block where the salt's fuel burns.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/element/MSRElementTE.java}.
 *
 * <h2>The fuels</h2>
 *
 * {@link MSRFuel} is upstream's table, value for value: each isotope's heat function of the
 * <em>target</em> chamber's salt temperature, its {@code life} (heat per unit burnt into its
 * {@link MSRByproduct}), and the one decay, protactinium-233 into uranium-233 at 0.03. The
 * {@code heatPerFlux} column is marked "scrapped" upstream and nothing reads it. The {@code items}
 * and {@code dicts} columns are what the mixing vat adds to the salt; nothing here reads them.
 *
 * <h2>A tick</h2>
 *
 * After {@link MSRTEBase#update} (cooling, the sellafield roll, the flow between blocks):
 *
 * <ol>
 * <li>{@link #decay}: every decaying isotope loses {@code rate x amount x buckets x 0.05} to its
 *     product.</li>
 * <li>The reactions, {@link #reactionTargets}: the twelve edge-adjacent positions of the 3x3x3
 *     around the chamber ("3x3 cube excluding the corner-corners"), at distance 2, and then, in each
 *     of the six directions, the first chamber within four blocks. A PWR neutron reflector in a
 *     reaction position stands for this chamber reacting with itself.</li>
 * <li>Each reaction ({@link #react}): every fuel's heat function, of the target's temperature, times
 *     its share, the two chambers' mean fill in buckets and the multiplier; the sum {@code y} pulls
 *     this chamber's heat towards it by {@code |y - heat|^0.2}, and burns fuel into its byproduct.
 *     The multiplier is 2 if graphite moderates the path and 0.5 if not, scaled by
 *     {@code 1 - max(restriction)} of the two chambers' control rods, and divided by half the
 *     distance.</li>
 * </ol>
 *
 * A block "is graphite" if its registry path contains the word {@code graphite} delimited by
 * non-letters - upstream's regex, so it is any mod's graphite. The arbitrary block counts as the
 * block it holds.
 *
 * <h2>Upstream's quirks, kept (D-021)</h2>
 *
 * <ul>
 * <li>A corner is blocked when either of the two face neighbours between it and the chamber is
 *     radiation shielding - except that upstream tests the <em>first</em> neighbour twice
 *     ({@code resistantB = isRadResistant(blockA)}), so only that one blocks. A GameTest pins it.</li>
 * <li>Decay divides by the isotope's {@code byproduct.division}, not its {@code decayProduct}'s
 *     (both are 1 for the one decaying isotope, so nothing changes today).</li>
 * <li>{@code decay} writes the new mixture into a protocol tag that is only the stack's own when the
 *     stack already had one; {@code MSRTEBase.update} gives every stack one first, so it always is.</li>
 * <li>A reaction's radiation ({@code incrementRad}) is commented out upstream, and so here.</li>
 * </ul>
 */
public class MSRElementTE extends MSRTEBase {

	public enum MSRByproduct {
		th232(1, new Pair<>("pa233", 1d)),
		pa233(1, new Pair<>("pa234", 1d)),
		pa233_decay(1, new Pair<>("u233", 1d)),
		u233(1, new Pair<>("u235", 1d));

		final public double division;
		final public Pair<String, Double>[] byproducts;

		@SafeVarargs
		MSRByproduct(double division, Pair<String, Double>... byproducts) {
			this.division = division;
			this.byproducts = byproducts;
		}
	}

	public enum MSRFuel {
		th232(new Item[] {}, new String[] { OreDictManager.TH232.nugget() }, "x^0.3/B", (x) -> Math.pow(x, 0.3), 0.3,
				MSRByproduct.th232, 30000000d),
		pa233(new Item[] {}, new String[] {}, "x^0.3/B", (x) -> Math.pow(x, 0.3), 1,
				MSRByproduct.pa233, 10000d, 0.03, MSRByproduct.pa233_decay),
		u233(new Item[] {}, new String[] { OreDictManager.U233.nugget() }, "x^0.85/B", (x) -> Math.pow(x, 0.85), 1,
				MSRByproduct.u233, 600000000d),
		u235(new Item[] {}, new String[] { OreDictManager.U235.nugget() }, "x^0.75/B", (x) -> Math.pow(x, 0.75), 1),
		pa234(new Item[] {}, new String[] {}, "x^0.15/B", (x) -> Math.pow(x, 0.15), 1);

		final public Item[] items;
		final public String[] dicts;
		final public String funcString;
		final public Function<Double, Double> function;
		final public double decayRate;
		final public MSRByproduct decayProduct;
		/** scrapped */
		final public double heatPerFlux;
		final public MSRByproduct byproduct;
		final public double life;

		MSRFuel(Item[] items, String[] dicts, String funcString, Function<Double, Double> function, double heatPerFlux) {
			this(items, dicts, funcString, function, heatPerFlux, null, 0, 0, null);
		}

		MSRFuel(Item[] items, String[] dicts, String funcString, Function<Double, Double> function, double heatPerFlux,
				MSRByproduct byproduct, double life) {
			this(items, dicts, funcString, function, heatPerFlux, byproduct, life, 0, null);
		}

		MSRFuel(Item[] items, String[] dicts, String funcString, Function<Double, Double> function, double heatPerFlux,
				MSRByproduct byproduct, double life, double decayRate, MSRByproduct decayProduct) {
			this.items = items;
			this.dicts = dicts;
			this.funcString = funcString;
			this.function = function;
			this.byproduct = byproduct;
			this.heatPerFlux = heatPerFlux;
			this.decayRate = decayRate;
			this.decayProduct = decayProduct;
			this.life = life;
		}
	}

	public BlockPos control = null;
	public double restriction = 0;

	public MSRElementTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/// DECAY ///

	public void decay() {
		double B = tank.getFluidAmount() / 1000d;
		FluidStack stack = tank.getFluid();
		if(!stack.isEmpty()) {
			boolean hadTag = LeafiaFF.hasTag(stack);
			CompoundTag nbt = nbtProtocol(stack);
			Map<String, Double> mixture = readMixture(nbt);
			for(Entry<String, Double> entry : mixture.entrySet()) {
				// entry.getValue() doesn't work because the mixture changes concurrently
				double mix = mixture.get(entry.getKey());
				if(mix <= 0) continue;
				try {
					MSRFuel type = MSRFuel.valueOf(entry.getKey());
					if(type.decayProduct != null) {
						double addAmt = type.decayRate * mix * B * 0.05;
						for(Pair<String, Double> byproduct : type.decayProduct.byproducts)
							addMixture(mixture, byproduct.getKey(), addAmt / type.byproduct.division);
						double perc = mix - addAmt;
						mixture.put(entry.getKey(), perc);
						if(perc <= 0)
							mixture.remove(entry.getKey());
					}
				} catch(IllegalArgumentException ignored) {}
			}
			nbt.put("itemMixture", writeMixture(mixture));
			if(hadTag) LeafiaFF.setTag(stack, nbt);
		}
	}

	/// WHAT IS AROUND ///

	/** The block at {@code pos}, or the block an arbitrary block there holds. */
	public Block getBlockArbitrary(BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if(block instanceof MSRArbitraryBlock) {
			if(level.getBlockEntity(pos) instanceof MSRArbitraryTE arbitrary) {
				if(arbitrary.inventory.getStackInSlot(0).getItem() instanceof BlockItem b)
					block = b.getBlock();
			}
		}
		return block;
	}

	static BlockPos toBlockPos(FiaMatrix mat) {
		return BlockPos.containing(mat.position);
	}

	static FiaMatrix toFiaMatrix(BlockPos pos) {
		return new FiaMatrix(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** Upstream's moderator test: "graphite" as a word in the registry path. */
	public static boolean isModerator(Block block) {
		return ("_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_").matches(".*[^a-z]graphite[^a-z].*");
	}

	public static boolean isRadResistant(Block block) {
		return block instanceof IRadResistantBlock;
	}

	MSRElementTE getReactionTarget(BlockPos pos) {
		Block block = getBlockArbitrary(pos);
		if(block instanceof PWRReflectorBlock)
			return this;
		if(level.getBlockEntity(pos) instanceof MSRElementTE te)
			return te;
		return null;
	}

	static void addMixture(Map<String, Double> mixture, String fuelType, double amt) {
		if(mixture.containsKey(fuelType))
			mixture.put(fuelType, mixture.get(fuelType) + amt);
		else
			mixture.put(fuelType, amt);
	}

	/// REACTIONS ///

	/** One reaction this tick: the chamber reacted with, the distance and the moderation multiplier. */
	public record Reaction(MSRElementTE target, double distance, double multiplier) {}

	/**
	 * See the class note: this chamber reacts with {@code te} at {@code distance}. Only this chamber's
	 * salt changes; {@code te}'s temperature is read.
	 */
	public void react(MSRElementTE te, double distance, double multiplier) {
		double B = te.tank.getFluidAmount() / 2000d + tank.getFluidAmount() / 2000d;
		FluidStack stack0 = tank.getFluid();
		FluidStack stack1 = te.tank.getFluid();
		if(!stack0.isEmpty()) {
			double y = 0;
			CompoundTag nbt = nbtProtocol(stack0);
			double curRestriction = Math.max(restriction, te.restriction);
			multiplier *= (1 - curRestriction);
			multiplier /= distance / 2;
			if(!stack1.isEmpty()) {
				Map<String, Double> mixture = readMixture(nbt);
				for(Entry<String, Double> entry : mixture.entrySet()) {
					// entry.getValue() doesn't work because the mixture changes concurrently
					double mix = mixture.get(entry.getKey());
					if(mix <= 0) continue;
					try {
						MSRFuel type = MSRFuel.valueOf(entry.getKey());
						double tempAdd = type.function.apply(nbtProtocol(stack1).getDouble("heat")
								+ getBaseTemperature(LeafiaFF.fromFF(stack0.getFluid()))) * mix * B * multiplier;
						y += tempAdd;
						if(type.byproduct != null) {
							double addAmt = tempAdd / type.life;
							for(Pair<String, Double> byproduct : type.byproduct.byproducts)
								addMixture(mixture, byproduct.getKey(), addAmt / type.byproduct.division);
							double perc = mix - addAmt;
							mixture.put(entry.getKey(), perc);
							if(perc <= 0)
								mixture.remove(entry.getKey());
						}
					} catch(IllegalArgumentException ignored) {}
				}
				nbt.put("itemMixture", writeMixture(mixture));
			}
			double heat = nbt.getDouble("heat");
			double heatMg = y - heat;
			heat += Math.pow(Math.abs(heatMg), 0.2) * Math.signum(heatMg);
			nbt.putDouble("heat", heat);
			LeafiaFF.setTag(stack0, nbt);
		}
	}

	void reactCorners2D(FiaMatrix mat, Map<BlockPos, Block> blocks, List<Reaction> out) {
		for(int x = -1; x <= 2; x += 2) {
			for(int y = -1; y <= 2; y += 2) {
				BlockPos a = toBlockPos(mat.translate(x, 0, 0));
				BlockPos b = toBlockPos(mat.translate(0, y, 0));
				BlockPos c = toBlockPos(mat.translate(x, y, 0));
				Block blockA = blocks.getOrDefault(a, Blocks.AIR);
				Block blockB = blocks.getOrDefault(b, Blocks.AIR);
				boolean moderatedA = isModerator(blockA);
				boolean moderatedB = isModerator(blockB);
				boolean resistantA = isRadResistant(blockA);
				boolean resistantB = isRadResistant(blockA); // upstream's: blockA twice
				if(!resistantA && !resistantB) {
					MSRElementTE te = getReactionTarget(c);
					if(te != null)
						out.add(new Reaction(te, 2, (moderatedA || moderatedB) ? 2 : 0.5));
				}
			}
		}
	}

	void reactCorners(List<Reaction> out) {
		Map<BlockPos, Block> mop = new LeafiaMap<>();
		for(Direction value : Direction.values()) {
			BlockPos p = worldPosition.relative(value);
			mop.put(p, getBlockArbitrary(p));
		}
		FiaMatrix mat = toFiaMatrix(worldPosition);
		reactCorners2D(mat, mop, out);
		reactCorners2D(mat.rotateY(90), mop, out);
		reactCorners2D(mat.rotateX(90), mop, out);
	}

	void reactLine(Direction facing, List<Reaction> out) {
		boolean moderated = false;
		for(int i = 1; i <= 4; i++) {
			BlockPos p = worldPosition.relative(facing, i);
			Block block = getBlockArbitrary(p);
			if(isModerator(block)) moderated = true;
			MSRElementTE te = getReactionTarget(p);
			if(te != null) {
				out.add(new Reaction(te, i, moderated ? 2 : 0.5));
				return;
			}
			if(isRadResistant(block)) return;
		}
	}

	/**
	 * Every reaction this chamber takes part in this tick, in upstream's order: the three corner
	 * planes, then the six lines. Collected first and applied by {@link #update} in the same order,
	 * which is upstream's behaviour exactly: what a reaction position holds does not depend on any
	 * tank, and each reaction reads the salt as the previous one left it.
	 */
	public List<Reaction> reactionTargets() {
		List<Reaction> out = new ArrayList<>();
		reactCorners(out);
		for(Direction face : Direction.values())
			reactLine(face, out);
		return out;
	}

	@Override
	public String getPacketIdentifier() {
		return "LFTR_FUEL";
	}

	@Override
	public void update() {
		super.update();
		if(level == null || level.isClientSide || isRemoved()) return;
		decay();
		for(Reaction r : reactionTargets())
			react(r.target(), r.distance(), r.multiplier());
		LeafiaPacket._start(this).__write(0, restriction).__sendToAffectedClients();
		markChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		restriction = compound.getDouble("restriction");
		if(compound.contains("controlX")) {
			control = new BlockPos(compound.getInt("controlX"), compound.getInt("controlY"), compound.getInt("controlZ"));
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putDouble("restriction", restriction);
		if(control != null) {
			compound.putInt("controlX", control.getX());
			compound.putInt("controlY", control.getY());
			compound.putInt("controlZ", control.getZ());
		} else {
			compound.remove("controlX");
			compound.remove("controlY");
			compound.remove("controlZ");
		}
		super.saveAdditional(compound, registries);
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0)
			restriction = (double) value;
	}
}
