package com.leafia.contents.fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.leafia.config.LeafiaFeatures;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The addon's world-placeable fluids: four fluids a bucket pours into the world, and balefire corium,
 * as opposed to the fourteen tank fluids in {@code LeafiaFluids}, which exist only inside machines.
 *
 * <p>The two are unrelated and this class is where that is easiest to get wrong. {@code LeafiaFluids}
 * adds {@code com.hbm.inventory.fluid.FluidType}s - an id, a colour and some traits, the currency of
 * tanks and pipes. This class adds NeoForge's {@code FluidType} plus a source and a flowing
 * {@code Fluid}, a {@code LiquidBlock} and a bucket: the things that exist as blocks. Upstream
 * kept the two apart as well, with {@code AddonFF} bridging them, and that bridge is the one thing
 * not ported (D-1023, D-1079).
 *
 * <h2>The five</h2>
 *
 * <ul>
 * <li>{@link LOXFluid} - {@code oxygen}: cryogenic liquid oxygen, water-like.
 * <li>{@link FluorideFluid} - {@code fluoride}: the molten salt of the thorium reactor, lava-like.
 * <li>{@link ConcreteFluid} - {@code leafia_concrete}: sets into smooth concrete.
 * <li>{@link OsmiridiumFluid} - {@code corecomponent}: the molten core component of the DFC.
 * <li>{@link BaleCoriumFluid} - {@code balecorium}: the molten core of a balefire rod. It was
 *     held back in D-1079 because it extends the base mod's {@code CoriumFinite}, which this port
 *     had not got; it has since landed, and the fifth joined in D-3004. Since the base mod's corium
 *     became upstream's finite block again (D-2173) it is a block only - no fluid, no bucket - and so
 *     it is {@link #balecorium}, a block holder, and not in {@link #ALL} (D-3052).
 * </ul>
 *
 * <h2>Registry names</h2>
 *
 * A fluid keeps the name upstream's {@code Fluid} had - {@code oxygen}, {@code fluoride},
 * {@code corecomponent}, {@code leafia_concrete}, the last two of which do not match their blocks -
 * and its flowing half is {@code flowing_<name>}. A block keeps its own upstream registry name
 * ({@code oxygen_fluid} and so on), and each fluid gets a {@code <name>_bucket}, which stands in for
 * the single universal bucket 1.12.2 filled with whichever fluid it was told to.
 *
 * <p>Gated by {@link LeafiaFeatures#worldFluids}: with it off nothing here is registered, and every
 * holder below is null.
 */
public final class LeafiaLiquids {

	private LeafiaLiquids() {}

	public static final DeferredRegister<FluidType> TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, LeafiaRef.MODID);
	public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, LeafiaRef.MODID);
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LeafiaRef.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LeafiaRef.MODID);

	/** Everything one fluid is: its type, both halves, its block and its bucket. */
	public static final class Liquid {
		public final String fluidName;
		public final String blockName;
		public DeferredHolder<FluidType, FluidType> type;
		public DeferredHolder<Fluid, FlowingFluid> source;
		public DeferredHolder<Fluid, FlowingFluid> flowing;
		public DeferredBlock<LiquidBlock> block;
		public DeferredItem<BucketItem> bucket;

		private Liquid(String fluidName, String blockName) {
			this.fluidName = fluidName;
			this.blockName = blockName;
		}
	}

	/** Every fluid-backed liquid registered, in registration order: the four, not balefire corium. Empty when {@link LeafiaFeatures#worldFluids} is off. */
	public static final List<Liquid> ALL = new ArrayList<>();

	public static Liquid lox;
	public static Liquid fluoride;
	public static Liquid concrete;
	public static Liquid osmiridium;
	/** Balefire corium: a finite corium block with no fluid (D-3052). Null when {@link LeafiaFeatures#worldFluids} is off. */
	public static DeferredBlock<BaleCoriumFluid.BaleCoriumFluidBlock> balecorium;

	public static void register(IEventBus modBus) {
		if(!LeafiaFeatures.worldFluids) return;

		lox = LOXFluid.define();
		fluoride = FluorideFluid.define();
		osmiridium = OsmiridiumFluid.define();
		concrete = ConcreteFluid.define();
		balecorium = BaleCoriumFluid.define();

		TYPES.register(modBus);
		FLUIDS.register(modBus);
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
	}

	/**
	 * Declares one liquid.
	 *
	 * @param fluidName    upstream's fluid name
	 * @param blockName    upstream's block registry name
	 * @param type         the fluid's NeoForge type
	 * @param source       builds the source half
	 * @param flowing      builds the flowing half
	 * @param block        builds the block; it is handed the source half
	 * @param blockProps   the block's properties
	 * @param tuning       sets tick rate, flow distance and the like on the shared fluid properties
	 */
	static Liquid define(String fluidName, String blockName, Supplier<FluidType> type,
			Function<BaseFlowingFluid.Properties, BaseFlowingFluid> source,
			Function<BaseFlowingFluid.Properties, BaseFlowingFluid> flowing,
			BiFunction<FlowingFluid, BlockBehaviour.Properties, LiquidBlock> block,
			BlockBehaviour.Properties blockProps, UnaryOperator<BaseFlowingFluid.Properties> tuning) {

		Liquid l = new Liquid(fluidName, blockName);
		l.type = TYPES.register(fluidName, type);

		BaseFlowingFluid.Properties props = tuning.apply(new BaseFlowingFluid.Properties(
				() -> l.type.get(), () -> l.source.get(), () -> l.flowing.get())
				.block(() -> l.block.get())
				.bucket(() -> l.bucket.get()));

		l.source = FLUIDS.register(fluidName, () -> source.apply(props));
		l.flowing = FLUIDS.register("flowing_" + fluidName, () -> flowing.apply(props));
		l.block = BLOCKS.register(blockName, () -> block.apply(l.source.get(), blockProps));
		l.bucket = ITEMS.register(fluidName + "_bucket",
				() -> new BucketItem(l.source.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

		// Upstream's buckets were the one universal bucket, shown once per fluid in the creative menu.
		LeafiaCreativeTabs.add(LeafiaCreativeTabs.MAIN, l.bucket);

		ALL.add(l);
		return l;
	}

	/**
	 * A fluid block's properties: vanilla water's, less its strength.
	 *
	 * <p>Upstream's fluid blocks never call {@code setHardness} or {@code setResistance}, and
	 * {@code BlockFluidBase} does not either, so they keep {@code Block}'s zero of both - which is
	 * the 0 here. Vanilla's own 100 would make any explosion bounce off a pool; with zero it passes
	 * through. Forge's source is not in the vendored reference, so that is the one assumption.
	 */
	static BlockBehaviour.Properties liquidProps(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.replaceable()
				.noCollission()
				.strength(0.0F, 0.0F)
				.pushReaction(PushReaction.DESTROY)
				.liquid()
				.sound(SoundType.EMPTY)
				.noLootTable();
	}

	/**
	 * What standing in {@code Material.LAVA} does that a custom fluid does not get for free.
	 *
	 * <p>In 1.12.2 lava is a <em>material</em>: any block of it is lava to {@code Entity.isInLava},
	 * which sets the entity alight for fifteen seconds, hurts it for four and halves its fall
	 * distance, once a tick. 1.21.1 hard-wires the same test to NeoForge's one {@code LAVA_TYPE}
	 * ({@code Entity.isInLava} reads {@code forgeFluidTypeHeight} for that type alone), so a fluid
	 * of its own never triggers it. The test upstream used was the entity's box, shrunk by 0.1 on
	 * each horizontal side and 0.4 on each vertical one, touching the block - which is what this
	 * repeats, and then calls vanilla's own {@code lavaHurt}.
	 */
	static void lavaBurn(Entity entity, net.minecraft.core.BlockPos pos) {
		if(!entity.getBoundingBox().deflate(0.1, 0.4, 0.1).intersects(new AABB(pos))) return;
		entity.fallDistance *= 0.5F;
		entity.lavaHurt();
	}
}
