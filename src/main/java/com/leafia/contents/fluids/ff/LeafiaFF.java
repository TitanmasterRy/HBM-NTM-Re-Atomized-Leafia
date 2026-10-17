package com.leafia.contents.fluids.ff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.hbm.slices.WorldFluidSystem;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.LeafiaMod;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Forge-fluid bridge: a two-way map between the base mod's NTM {@link FluidType} - the currency
 * of tanks and pipes - and NeoForge's {@link Fluid}/{@link FluidStack}, plus the conversions,
 * adapters and one-call helpers the addon's Forge-tank machines are built on.
 *
 * <p>Upstream: the remainder of {@code com/leafia/contents/AddonFluids.java} ({@code fromFF},
 * {@code addCompatFluid}, the two {@code setFFNameOverride} calls) and {@code MixinFluids}, which
 * hooked NTM-CE's {@code Fluids.registerForgeFluidCompat} to fill that map as CE registered a Forge
 * fluid per {@code FluidType}. This port's {@code FluidType} has no Forge fluid at all ({@code getFF},
 * {@code getFFName} and {@code setFFNameOverride} do not exist in the base mod, which registers
 * NeoForge fluids only for its eight world fluids), and mixins into {@code com.hbm} are not allowed
 * (D-1001). So the whole of CE's mechanism is rebuilt here, in the addon, as the owner asked
 * (D-1093 #9). See D-3003.
 *
 * <h2>What maps to what</h2>
 *
 * Every NTM fluid but {@code NONE} maps to exactly one NeoForge fluid, and the map is a bijection on
 * those it holds:
 * <ol>
 * <li><b>Seeds</b> - fluids that already exist as world fluids keep them, which is CE's
 *     "already registered" branch ({@code MixinFluids.onGetFF}): {@code WATER} and {@code LAVA} are
 *     vanilla's; {@code WATZ}, {@code SCHRABIDIC} and {@code SULFURIC_ACID} are the base mod's
 *     {@code mud_fluid}, {@code schrabidic_fluid} and {@code sulfuric_acid_fluid}; {@code FLIBE},
 *     {@code CONCRETE} and {@code OXYGEN} are the addon's {@code fluoride}, {@code leafia_concrete} and
 *     {@code oxygen} (D-1079). Those three hold only while {@code worldFluids} is on (and
 *     {@code FLIBE} also needs {@code addonFluids}); otherwise they fall to the next rule.</li>
 * <li><b>Everything else</b> gets a tank-only {@link FFTankFluid} registered here at
 *     {@code leafia:ff/<name>}, where the name is CE's {@code getFFName}: the override if the fluid
 *     has one ({@link #NAME_OVERRIDES}), otherwise its NTM name in lower case.</li>
 * </ol>
 * World fluids with no NTM fluid behind them - the base mod's acid, toxic and the two lavas,
 * the addon's molten core component - are not in the map, and {@link #fromFF} answers {@code NONE}
 * for them, as upstream's {@code getOrDefault} did for any Forge fluid it had not been told about.
 * A flowing half maps through its source.
 *
 * <h2>The switch</h2>
 *
 * {@link LeafiaFeatures#ffBridge()}. Off, {@link #register} registers nothing and the map is empty:
 * {@link #getFF} answers null for everything and {@link #fromFF} {@code NONE} (D-1002).
 *
 * <h2>The API, for the batches that build on it</h2>
 *
 * <ul>
 * <li>Mapping: {@link #getFF}, {@link #fromFF(Fluid)}, {@link #fromFF(FluidStack)}, {@link #toFF},
 *     {@link #toNTM}, {@link #isMapped}, {@link #ffName}, {@link #keyOf}, {@link #entries}.</li>
 * <li>A stack's NBT - upstream's {@code FluidStack.tag}, which the LFTR's salt mixture and heat ride
 *     on - is its {@code CUSTOM_DATA} component: {@link #getTag}, {@link #setTag}, {@link #save},
 *     {@link #load}.</li>
 * <li>Tanks: {@link NTMTankHandler} shows base {@link FluidTank}s to anything that speaks
 *     {@link IFluidHandler}; {@link ForgeTankView} shows a NeoForge tank to the base mod's fluid
 *     network as a base {@code FluidTank}.</li>
 * <li>Items: {@link #fillItem}, {@link #drainItem}, {@link #contained}, through
 *     {@code Capabilities.FluidHandler.ITEM}.</li>
 * <li>World: {@link #pour} - a bucket's worth out of a tank and into the world as its world fluid,
 *     in one call (what D-1079 found missing); {@link #worldBlock}.</li>
 * <li>Capabilities: {@link #registerBlock} - {@code Capabilities.FluidHandler.BLOCK} for an addon
 *     block entity, gated on the bridge. Call it from {@code LeafiaCapabilities}.</li>
 * <li>Tooltips: {@link #amountLine} (the line {@code BullshitMixinFluidTankNTM} rewrote); the
 *     drawing and the rest of the tooltip are client-only, in {@code LeafiaFFClient}.</li>
 * </ul>
 */
public final class LeafiaFF {

	private LeafiaFF() {}

	/** The path prefix of every fluid this class registers. */
	public static final String PREFIX = "ff/";

	/** A bucket, in millibuckets - what one {@link #pour} moves. */
	public static final int BUCKET = net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME;

	/**
	 * Upstream's {@code setFFNameOverride} calls, keyed by NTM name: NTM-CE 1.5.1.8's own (read out of
	 * {@code Fluids.init} in the vendored jar, those whose fluid exists in this base mod) and the
	 * addon's two ({@code FLIBE}, {@code CONCRETE}). {@code HCL}, {@code OLIVEOIL} and
	 * {@code GASEOUS_HYDROGEN} are CE-only fluids and have no row. A seeded fluid keeps its world
	 * fluid whatever its override says; the override is the name it falls back to when the seed is
	 * switched off.
	 */
	public static final Map<String, String> NAME_OVERRIDES;

	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("SCHRABIDIC", "schrabidic_fluid");
		m.put("WATZ", "mud_fluid");
		m.put("CARBONDIOXIDE", "carbon_dioxide");
		m.put("HELIUM3", "helium_3");
		m.put("HEAVYWATER", "heavy_water");
		m.put("XPJUICE", "experience");
		m.put("SULFURIC_ACID", "sulfuric_acid");
		m.put("REDMUD", "red_mud");
		m.put("FISHOIL", "fish_oil");
		m.put("SUNFLOWEROIL", "seed_oil");
		m.put("HYDROGEN", "liquidhydrogen");
		// The addon's (AddonFluids.init).
		m.put("FLIBE", "fluoride");
		m.put("CONCRETE", "leafia_concrete");
		NAME_OVERRIDES = Collections.unmodifiableMap(m);
	}

	public static final DeferredRegister<net.neoforged.neoforge.fluids.FluidType> TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, LeafiaRef.MODID);
	public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, LeafiaRef.MODID);

	/** One row of the map: an NTM fluid, the key of its NeoForge fluid, and where that fluid comes from. */
	public static final class Entry {
		public final FluidType ntm;
		public final ResourceLocation key;
		/** True for a world fluid that already existed; false for one registered here. */
		public final boolean seeded;
		Supplier<? extends Fluid> fluid;
		/** The NeoForge type registered for it; null for a seed. */
		@Nullable public DeferredHolder<net.neoforged.neoforge.fluids.FluidType, net.neoforged.neoforge.fluids.FluidType> type;

		Entry(FluidType ntm, ResourceLocation key, boolean seeded, Supplier<? extends Fluid> fluid) {
			this.ntm = ntm;
			this.key = key;
			this.seeded = seeded;
			this.fluid = fluid;
		}

		public Fluid fluid() {
			return fluid.get();
		}
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final List<String> SKIPPED = new ArrayList<>();
	private static boolean registered = false;

	private static Map<FluidType, Fluid> toFF;
	private static Map<Fluid, FluidType> fromFF;

	// ------------------------------------------------------------------ registration

	/**
	 * Builds the map and registers the {@code ff/} fluids. Called from the mod constructor after
	 * {@code LeafiaFluids.init()}, because the addon's fourteen fluids must exist to be mapped, and
	 * after the base mod's {@code Fluids.init()}, which ran in its own constructor (this jar loads
	 * after {@code hbm}). Idempotent.
	 */
	public static void register(IEventBus modBus) {
		if(!LeafiaFeatures.ffBridge() || registered) return;
		registered = true;

		ENTRIES.addAll(plan(SKIPPED));

		for(Entry e : ENTRIES) {
			if(e.seeded) continue;
			FluidType ntm = e.ntm;
			String path = e.key.getPath();
			var type = TYPES.register(path, () -> new net.neoforged.neoforge.fluids.FluidType(
					net.neoforged.neoforge.fluids.FluidType.Properties.create()
							.descriptionId(ntm.getUnlocalizedName())
							// CE's FluidNTM: the NTM temperature, in kelvin. Nothing else is carried over.
							.temperature(ntm.temperature + 273)));
			e.type = type;
			e.fluid = FLUIDS.register(path, () -> new FFTankFluid(type));
		}

		TYPES.register(modBus);
		FLUIDS.register(modBus);

		LeafiaMod.LOGGER.info("Forge-fluid bridge: {} NTM fluids mapped, {} as world fluids, {} registered at leafia:{}*{}",
				ENTRIES.size(), ENTRIES.stream().filter(x -> x.seeded).count(),
				ENTRIES.stream().filter(x -> !x.seeded).count(), PREFIX,
				SKIPPED.isEmpty() ? "" : ", skipped " + SKIPPED);
	}

	/**
	 * The rows the map would hold with the switches as they are now, without registering anything:
	 * every NTM fluid but {@code NONE}, in the base mod's registration order, seed or {@code ff/}.
	 * A fluid whose name is not a valid path, or collides with one already taken, is left out and
	 * named in {@code skipped} - none of the shipped fluids is.
	 */
	public static List<Entry> plan(List<String> skipped) {

		Map<FluidType, Seed> seeds = seeds();
		List<Entry> out = new ArrayList<>();
		Set<String> taken = new HashSet<>();

		for(FluidType ntm : Fluids.getAll()) {
			if(ntm == Fluids.NONE) continue;

			Seed seed = seeds.get(ntm);
			if(seed != null) {
				out.add(new Entry(ntm, seed.key, true, seed.fluid));
				continue;
			}

			String path = PREFIX + ffName(ntm);
			if(!ResourceLocation.isValidPath(path) || !taken.add(path)) {
				skipped.add(ntm.getName());
				continue;
			}
			out.add(new Entry(ntm, LeafiaRef.rl(path), false, () -> null));
		}
		return out;
	}

	private record Seed(ResourceLocation key, Supplier<? extends Fluid> fluid) {}

	/** The world fluids that already exist, by the NTM fluid each one is. */
	private static Map<FluidType, Seed> seeds() {
		Map<FluidType, Seed> seeds = new LinkedHashMap<>();
		seeds.put(Fluids.WATER, new Seed(ResourceLocation.withDefaultNamespace("water"), () -> net.minecraft.world.level.material.Fluids.WATER));
		seeds.put(Fluids.LAVA, new Seed(ResourceLocation.withDefaultNamespace("lava"), () -> net.minecraft.world.level.material.Fluids.LAVA));
		seeds.put(Fluids.WATZ, new Seed(WorldFluidSystem.MUD.getId(), WorldFluidSystem.MUD));
		seeds.put(Fluids.SCHRABIDIC, new Seed(WorldFluidSystem.SCHRABIDIC.getId(), WorldFluidSystem.SCHRABIDIC));
		seeds.put(Fluids.SULFURIC_ACID, new Seed(WorldFluidSystem.SULFURIC.getId(), WorldFluidSystem.SULFURIC));
		if(LeafiaLiquids.fluoride != null && LeafiaFluids.FLUORIDE != null)
			seeds.put(LeafiaFluids.FLUORIDE, new Seed(LeafiaLiquids.fluoride.source.getId(), LeafiaLiquids.fluoride.source));
		if(LeafiaLiquids.concrete != null)
			seeds.put(Fluids.CONCRETE, new Seed(LeafiaLiquids.concrete.source.getId(), LeafiaLiquids.concrete.source));
		if(LeafiaLiquids.lox != null)
			seeds.put(Fluids.OXYGEN, new Seed(LeafiaLiquids.lox.source.getId(), LeafiaLiquids.lox.source));
		return seeds;
	}

	/** CE's {@code getFFName}: the override if there is one, otherwise the NTM name in lower case. */
	public static String ffName(FluidType ntm) {
		String override = NAME_OVERRIDES.get(ntm.getName());
		return override != null ? override : ntm.getName().toLowerCase(Locale.US);
	}

	// ------------------------------------------------------------------ the map

	/** Whether the bridge exists in this game (its switch was on at startup). */
	public static boolean enabled() {
		return registered;
	}

	/** Every row, in registration order. Empty when the bridge is off. */
	public static List<Entry> entries() {
		return Collections.unmodifiableList(ENTRIES);
	}

	/** NTM names the planner had to leave out. Empty for the shipped fluids; a test holds it there. */
	public static List<String> skipped() {
		return Collections.unmodifiableList(SKIPPED);
	}

	private static void bind() {
		if(toFF != null) return;
		Map<FluidType, Fluid> to = new IdentityHashMap<>();
		Map<Fluid, FluidType> from = new IdentityHashMap<>();
		for(Entry e : ENTRIES) {
			Fluid f = e.fluid();
			if(f == null) throw new IllegalStateException("LeafiaFF used before fluid registration finished: " + e.key);
			to.put(e.ntm, f);
			from.put(f, e.ntm);
		}
		fromFF = from;
		toFF = to;
	}

	/**
	 * Upstream's {@code FluidType.getFF()}: the NeoForge fluid an NTM fluid travels as, or null if it
	 * has none (it is {@code NONE}, or the bridge is off). Always the source half.
	 */
	@Nullable
	public static Fluid getFF(FluidType ntm) {
		if(!registered || ntm == null) return null;
		bind();
		return toFF.get(ntm);
	}

	/** Upstream's {@code AddonFluids.fromFF}: the NTM fluid a NeoForge fluid is, {@code NONE} if it is none. */
	public static FluidType fromFF(@Nullable Fluid fluid) {
		if(!registered || fluid == null) return Fluids.NONE;
		bind();
		if(fluid instanceof FlowingFluid flowing) fluid = flowing.getSource();
		FluidType ntm = fromFF.get(fluid);
		return ntm == null ? Fluids.NONE : ntm;
	}

	public static FluidType fromFF(FluidStack stack) {
		return stack.isEmpty() ? Fluids.NONE : fromFF(stack.getFluid());
	}

	public static boolean isMapped(FluidType ntm) {
		return getFF(ntm) != null;
	}

	/** The key of an NTM fluid's NeoForge fluid, or null if it has none. */
	@Nullable
	public static ResourceLocation keyOf(FluidType ntm) {
		for(Entry e : ENTRIES) if(e.ntm == ntm) return e.key;
		return null;
	}

	/** An NTM fluid as a NeoForge stack: {@link FluidStack#EMPTY} if it has no fluid or the amount is not positive. */
	public static FluidStack toFF(FluidType ntm, int amount) {
		Fluid f = getFF(ntm);
		if(f == null || amount <= 0) return FluidStack.EMPTY;
		return new FluidStack(f, amount);
	}

	/** A NeoForge stack as the base mod's stack: type and amount, pressure zero (a Forge stack has none). */
	public static com.hbm.inventory.FluidStack toNTM(FluidStack stack) {
		return new com.hbm.inventory.FluidStack(fromFF(stack), stack.getAmount());
	}

	// ------------------------------------------------------------------ a stack's NBT

	/**
	 * Upstream's {@code FluidStack.tag}, which 1.21.1 replaced with components: a copy of the stack's
	 * {@code CUSTOM_DATA}, or an empty tag. Writing to the copy changes nothing; use {@link #setTag}.
	 */
	public static CompoundTag getTag(FluidStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	/** Whether the stack carries a tag at all - upstream's {@code tag != null}. */
	public static boolean hasTag(FluidStack stack) {
		return stack.get(DataComponents.CUSTOM_DATA) != null;
	}

	/** Upstream's {@code stack.tag = tag}; null or empty removes it. */
	public static void setTag(FluidStack stack, @Nullable CompoundTag tag) {
		if(tag == null || tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
		else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** A stack, components included, as NBT. An empty stack writes an empty tag. */
	public static CompoundTag save(FluidStack stack, HolderLookup.Provider registries) {
		Tag tag = stack.saveOptional(registries);
		return tag instanceof CompoundTag c ? c : new CompoundTag();
	}

	public static FluidStack load(CompoundTag tag, HolderLookup.Provider registries) {
		return FluidStack.parseOptional(registries, tag);
	}

	// ------------------------------------------------------------------ tooltips

	/** Upstream's sludge units: houses per bucket. {@code BullshitMixinFluidTankNTM}'s constant. */
	public static final double HOUSE_PER_BUCKET = 9.54406631763;

	/**
	 * The amount line of a tank's tooltip: {@code "fill/maxmB"}, or, with upstream's
	 * {@code enableSludgeUnits}, {@code "fill/maxhouse"} at {@link #HOUSE_PER_BUCKET} houses a bucket,
	 * truncated as upstream truncates. That is the line {@code BullshitMixinFluidTankNTM} rewrote in
	 * every base tank's tooltip; the switch itself arrives with the other unit swaps (M6's cosmetic
	 * seam), so callers pass false until then.
	 */
	public static String amountLine(int fill, int max, boolean sludge) {
		if(!sludge) return fill + "/" + max + "mB";
		return (int) (fill / 1000D * HOUSE_PER_BUCKET) + "/" + (int) (max / 1000D * HOUSE_PER_BUCKET) + "house";
	}

	// ------------------------------------------------------------------ items

	/** What an item transfer moved, and the container it left behind (the input, if nothing moved). */
	public record ItemTransfer(FluidStack moved, ItemStack container) {
		public boolean success() {
			return !moved.isEmpty();
		}
	}

	/** The fluid handler of one item of a stack, or null. The stack itself is never touched. */
	@Nullable
	public static IFluidHandlerItem itemHandler(ItemStack container) {
		if(container.isEmpty()) return null;
		return container.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
	}

	/** What one item of the stack holds, or empty. */
	public static FluidStack contained(ItemStack container) {
		IFluidHandlerItem h = itemHandler(container);
		return h == null || h.getTanks() == 0 ? FluidStack.EMPTY : h.getFluidInTank(0).copy();
	}

	/**
	 * Fills one item of {@code container} from {@code resource}. A vanilla bucket takes exactly a
	 * bucket of a fluid that has a bucket item, so an {@code ff/} fluid never goes into one. On
	 * {@code SIMULATE} the returned container is a copy and nothing anywhere has changed.
	 */
	public static ItemTransfer fillItem(ItemStack container, FluidStack resource, FluidAction action) {
		IFluidHandlerItem h = itemHandler(container);
		if(h == null || resource.isEmpty()) return new ItemTransfer(FluidStack.EMPTY, container);
		int filled = h.fill(resource.copy(), action);
		if(filled <= 0) return new ItemTransfer(FluidStack.EMPTY, container);
		return new ItemTransfer(resource.copyWithAmount(filled), action.execute() ? h.getContainer() : container);
	}

	/** Drains up to {@code max} from one item of {@code container}. */
	public static ItemTransfer drainItem(ItemStack container, int max, FluidAction action) {
		IFluidHandlerItem h = itemHandler(container);
		if(h == null || max <= 0) return new ItemTransfer(FluidStack.EMPTY, container);
		FluidStack drained = h.drain(max, action);
		if(drained.isEmpty()) return new ItemTransfer(FluidStack.EMPTY, container);
		return new ItemTransfer(drained, action.execute() ? h.getContainer() : container);
	}

	// ------------------------------------------------------------------ the world

	/**
	 * The block an NTM fluid becomes in the world, or null if it has no world fluid (the
	 * {@code ff/} ones, and everything while the bridge is off).
	 */
	@Nullable
	public static BlockState worldBlock(FluidType ntm) {
		Fluid f = getFF(ntm);
		if(f == null) return null;
		BlockState state = f.defaultFluidState().createLegacyBlock();
		return state.isAir() ? null : state;
	}

	/**
	 * Tank to world, in one call: takes a bucket of whatever {@code source} gives first and places it
	 * at {@code pos} as its world fluid, through NeoForge's own {@code FluidUtil.tryPlaceFluid} - so it
	 * drains only on success, plays the fluid's bucket-empty sound, replaces what a bucket would
	 * replace (anything non-solid, another liquid included) and vaporises water in an ultrawarm
	 * dimension (that counts as success: the bucket is gone). A source holding less than a bucket,
	 * or an {@code ff/} fluid, places nothing and loses nothing.
	 *
	 * <p>Upstream's one placer, the LFTR plug, writes {@code fluid_fluoride} into a block that is
	 * replaceable and not a liquid, then drains a bucket when it sees the block there; a caller that
	 * wants "not a liquid" checks {@code level.getFluidState(pos).isEmpty()} first.
	 */
	public static boolean pour(Level level, BlockPos pos, IFluidHandler source) {
		FluidStack probe = source.drain(BUCKET, FluidAction.SIMULATE);
		if(probe.getAmount() < BUCKET) return false;
		return FluidUtil.tryPlaceFluid(null, level, InteractionHand.MAIN_HAND, pos, source, probe.copyWithAmount(BUCKET));
	}

	/** {@link #pour(Level, BlockPos, IFluidHandler)} straight out of a base-mod tank. */
	public static boolean pour(Level level, BlockPos pos, FluidTank tank) {
		return pour(level, pos, new NTMTankHandler(tank));
	}

	// ------------------------------------------------------------------ capabilities

	/**
	 * Exposes an addon block entity's fluid handler to pipes, ducts and buckets: one
	 * {@code Capabilities.FluidHandler.BLOCK} registration, made only while the bridge exists. Call
	 * it from {@code LeafiaCapabilities.registerCapabilities}, behind the machine's own switch. A
	 * machine that holds base {@link FluidTank}s returns {@code new NTMTankHandler(...)}; one that
	 * holds NeoForge tanks returns the tank, which is already an {@link IFluidHandler}.
	 */
	public static <T extends BlockEntity> void registerBlock(RegisterCapabilitiesEvent event, BlockEntityType<T> type,
			ICapabilityProvider<? super T, Direction, IFluidHandler> provider) {
		if(!registered) return;
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, provider);
	}
}
