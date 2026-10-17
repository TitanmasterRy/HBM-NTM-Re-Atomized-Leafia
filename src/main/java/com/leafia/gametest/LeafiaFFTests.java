package com.leafia.gametest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blocks.ModBlocks;
import com.hbm.slices.WorldFluidSystem;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.fluids.ff.FFTankFluid;
import com.leafia.contents.fluids.ff.ForgeTankView;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.fluids.ff.NTMTankHandler;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.fluids.FluidLoaderBottle;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Forge-fluid bridge (D-3003): the map, the conversions, the two tank adapters, items, the world
 * and the bottle loader.
 *
 * <p>Most of these are statements about every fluid at once rather than about one, because the map
 * is generated from the base mod's fluid list and a rule that holds for diesel and fails for the
 * hundred-and-fortieth fluid is the failure that matters. Where a test names fluids, it names the
 * ones whose behaviour is a decision: the seeds, the overrides, and one that is neither.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFFTests {

	private static final String EMPTY16 = "empty16";

	private static boolean on() {
		return LeafiaFeatures.ffBridge();
	}

	// ------------------------------------------------------------------ the map

	/**
	 * Every NTM fluid but NONE is mapped, to a fluid no other NTM fluid has, and back to itself - a
	 * bijection - and the planner had to leave nothing out. A flowing half reads as its source.
	 */
	@GameTest(template = EMPTY16)
	public static void theMapIsABijectionOverEveryFluid(GameTestHelper helper) {
		if(!on()) {
			helper.assertTrue(LeafiaFF.entries().isEmpty(), "the bridge is off and the map is not empty");
			helper.succeed();
			return;
		}
		List<String> problems = new ArrayList<>();
		Map<Fluid, FluidType> seen = new IdentityHashMap<>();
		int mapped = 0;
		for(FluidType ntm : Fluids.getAll()) {
			if(ntm == Fluids.NONE) {
				if(LeafiaFF.getFF(ntm) != null) problems.add("NONE has a fluid");
				continue;
			}
			Fluid f = LeafiaFF.getFF(ntm);
			if(f == null) {
				problems.add(ntm.getName() + " has no fluid");
				continue;
			}
			mapped++;
			FluidType prior = seen.put(f, ntm);
			if(prior != null) problems.add(ntm.getName() + " shares " + BuiltInRegistries.FLUID.getKey(f) + " with " + prior.getName());
			if(LeafiaFF.fromFF(f) != ntm) problems.add(ntm.getName() + " -> " + BuiltInRegistries.FLUID.getKey(f) + " -> " + LeafiaFF.fromFF(f).getName());
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(mapped == LeafiaFF.entries().size() && mapped == Fluids.getAll().size() - 1,
				"mapped " + mapped + ", rows " + LeafiaFF.entries().size() + ", fluids " + (Fluids.getAll().size() - 1));
		helper.assertTrue(LeafiaFF.skipped().isEmpty(), "the planner skipped " + LeafiaFF.skipped());
		helper.assertTrue(LeafiaFF.fromFF(net.minecraft.world.level.material.Fluids.FLOWING_WATER) == Fluids.WATER,
				"flowing water should read as WATER");
		helper.assertTrue(LeafiaFF.fromFF(WorldFluidSystem.SCHRABIDIC_FLOWING.get()) == Fluids.SCHRABIDIC,
				"flowing schrabidic acid should read as SCHRABIDIC");
		helper.succeed();
	}

	/**
	 * The seeds are the world fluids, the rest are tank-only {@code ff/} fluids named by CE's rule
	 * with upstream's overrides, and a world fluid with no NTM fluid behind it maps to NONE.
	 */
	@GameTest(template = EMPTY16)
	public static void seedsAreWorldFluidsAndTheRestAreNamedByUpstreamsRule(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		expect(helper, Fluids.WATER, ResourceLocation.withDefaultNamespace("water"));
		expect(helper, Fluids.LAVA, ResourceLocation.withDefaultNamespace("lava"));
		expect(helper, Fluids.WATZ, ResourceLocation.fromNamespaceAndPath("hbm", "mud_fluid"));
		expect(helper, Fluids.SCHRABIDIC, ResourceLocation.fromNamespaceAndPath("hbm", "schrabidic_fluid"));
		expect(helper, Fluids.SULFURIC_ACID, ResourceLocation.fromNamespaceAndPath("hbm", "sulfuric_acid_fluid"));
		boolean world = LeafiaFeatures.worldFluids;
		if(LeafiaFluids.FLUORIDE != null)
			expect(helper, LeafiaFluids.FLUORIDE, LeafiaRef.rl(world ? "fluoride" : "ff/fluoride"));
		expect(helper, Fluids.CONCRETE, LeafiaRef.rl(world ? "leafia_concrete" : "ff/leafia_concrete"));
		expect(helper, Fluids.OXYGEN, LeafiaRef.rl(world ? "oxygen" : "ff/oxygen"));
		// CE's overrides, and a plain lower-cased name.
		expect(helper, Fluids.HYDROGEN, LeafiaRef.rl("ff/liquidhydrogen"));
		expect(helper, Fluids.HEAVYWATER, LeafiaRef.rl("ff/heavy_water"));
		expect(helper, Fluids.SUNFLOWEROIL, LeafiaRef.rl("ff/seed_oil"));
		expect(helper, Fluids.DIESEL, LeafiaRef.rl("ff/diesel"));
		if(LeafiaFluids.DEATHSTEAM != null) expect(helper, LeafiaFluids.DEATHSTEAM, LeafiaRef.rl("ff/deathsteam"));

		for(LeafiaFF.Entry e : LeafiaFF.entries()) {
			if(e.seeded) continue;
			Fluid f = e.fluid();
			helper.assertTrue(f instanceof FFTankFluid, e.key + " is not a tank fluid");
			helper.assertTrue(f.getBucket() == Items.AIR, e.key + " has a bucket");
			helper.assertTrue(f.defaultFluidState().createLegacyBlock().isAir(), e.key + " has a block");
			helper.assertTrue(f.getFluidType() == e.type.get(), e.key + " is not of its own type");
			helper.assertTrue(NeoForgeRegistries.FLUID_TYPES.getKey(e.type.get()).equals(e.key), e.key + " type key");
			helper.assertTrue(f.getFluidType().getDescriptionId().equals(e.ntm.getUnlocalizedName()), e.key + " is not named as its NTM fluid");
			helper.assertTrue(f.getFluidType().getTemperature() == e.ntm.temperature + 273, e.key + " temperature");
		}

		helper.assertTrue(LeafiaFF.fromFF(WorldFluidSystem.ACID.get()) == Fluids.NONE, "acid has no NTM fluid");
		if(LeafiaLiquids.osmiridium != null)
			helper.assertTrue(LeafiaFF.fromFF(LeafiaLiquids.osmiridium.source.get()) == Fluids.NONE, "the core component has no NTM fluid");
		helper.assertTrue(LeafiaFF.fromFF((Fluid) null) == Fluids.NONE && LeafiaFF.fromFF(FluidStack.EMPTY) == Fluids.NONE, "nothing is NONE");
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, FluidType ntm, ResourceLocation key) {
		Fluid f = LeafiaFF.getFF(ntm);
		helper.assertTrue(f != null && BuiltInRegistries.FLUID.getKey(f).equals(key),
				ntm.getName() + " should be " + key + ", is " + (f == null ? "nothing" : BuiltInRegistries.FLUID.getKey(f)));
		helper.assertTrue(key.equals(LeafiaFF.keyOf(ntm)), ntm.getName() + " keyOf");
	}

	/** With the switch off nothing is registered under {@code ff/}, and on, exactly the planned rows are. */
	@GameTest(template = EMPTY16)
	public static void theSwitchDecidesWhetherAnythingRegisters(GameTestHelper helper) {
		long fluids = BuiltInRegistries.FLUID.keySet().stream()
				.filter(k -> k.getNamespace().equals(LeafiaRef.MODID) && k.getPath().startsWith(LeafiaFF.PREFIX)).count();
		long types = NeoForgeRegistries.FLUID_TYPES.keySet().stream()
				.filter(k -> k.getNamespace().equals(LeafiaRef.MODID) && k.getPath().startsWith(LeafiaFF.PREFIX)).count();
		long planned = on() ? LeafiaFF.plan(new ArrayList<>()).stream().filter(e -> !e.seeded).count() : 0;
		helper.assertTrue(LeafiaFF.enabled() == on(), "enabled() " + LeafiaFF.enabled() + " but the switch reads " + on());
		helper.assertTrue(fluids == planned && types == planned, "registered " + fluids + " fluids and " + types + " types, planned " + planned);
		if(on()) helper.assertTrue(planned > 100, "only " + planned + " ff fluids planned");
		else helper.assertTrue(LeafiaFF.getFF(Fluids.DIESEL) == null && LeafiaFF.fromFF(net.minecraft.world.level.material.Fluids.WATER) == Fluids.NONE,
				"the map answered while the bridge is off");
		helper.succeed();
	}

	// ------------------------------------------------------------------ conversions

	/** NTM type to NeoForge stack and back keeps type and amount, for every mapped fluid. */
	@GameTest(template = EMPTY16)
	public static void aRoundTripKeepsTypeAndAmount(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int amount = 1234;
		for(LeafiaFF.Entry e : LeafiaFF.entries()) {
			FluidStack ff = LeafiaFF.toFF(e.ntm, amount);
			com.hbm.inventory.FluidStack back = LeafiaFF.toNTM(ff);
			if(ff.getAmount() != amount || back.type != e.ntm || back.fill != amount)
				problems.add(e.ntm.getName() + " came back as " + back.type.getName() + " x" + back.fill);
			amount += 7;
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(LeafiaFF.toFF(Fluids.NONE, 1000).isEmpty(), "NONE should convert to an empty stack");
		helper.assertTrue(LeafiaFF.toFF(Fluids.DIESEL, 0).isEmpty(), "zero millibuckets should be empty");
		helper.succeed();
	}

	/** Upstream's {@code FluidStack.tag} rides the stack's custom data, and survives a save. */
	@GameTest(template = EMPTY16)
	public static void aStacksTagSurvivesSaveAndLoad(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		FluidStack stack = LeafiaFF.toFF(Fluids.DIESEL, 500);
		helper.assertTrue(!LeafiaFF.hasTag(stack) && LeafiaFF.getTag(stack).isEmpty(), "a new stack has no tag");
		CompoundTag tag = new CompoundTag();
		tag.putDouble("heat", 412.5);
		tag.putString("mix", "u233");
		LeafiaFF.setTag(stack, tag);
		tag.putDouble("heat", 0); // the stack keeps its own copy
		CompoundTag saved = LeafiaFF.save(stack, helper.getLevel().registryAccess());
		FluidStack loaded = LeafiaFF.load(saved, helper.getLevel().registryAccess());
		helper.assertTrue(LeafiaFF.fromFF(loaded) == Fluids.DIESEL && loaded.getAmount() == 500, "loaded " + loaded);
		helper.assertTrue(LeafiaFF.getTag(loaded).getDouble("heat") == 412.5 && "u233".equals(LeafiaFF.getTag(loaded).getString("mix")),
				"tag came back as " + LeafiaFF.getTag(loaded));
		LeafiaFF.setTag(loaded, null);
		helper.assertTrue(!LeafiaFF.hasTag(loaded), "a null tag should remove it");
		helper.succeed();
	}

	/** The amount line, and upstream's houses: 9.544 to the bucket, truncated. */
	@GameTest(template = EMPTY16)
	public static void theAmountLineAndItsSludgeUnits(GameTestHelper helper) {
		helper.assertTrue(LeafiaFF.amountLine(1000, 16000, false).equals("1000/16000mB"), LeafiaFF.amountLine(1000, 16000, false));
		helper.assertTrue(LeafiaFF.amountLine(1000, 16000, true).equals("9/152house"), LeafiaFF.amountLine(1000, 16000, true));
		helper.assertTrue(LeafiaFF.amountLine(0, 333, true).equals("0/3house"), LeafiaFF.amountLine(0, 333, true));
		helper.succeed();
	}

	// ------------------------------------------------------------------ items

	/** A bucket through {@code Capabilities.FluidHandler.ITEM}: a bucket at a time, and only fluids with a bucket. */
	@GameTest(template = EMPTY16)
	public static void aBucketFillsAndDrainsThroughTheItemCapability(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		ItemStack bucket = new ItemStack(Items.BUCKET);
		LeafiaFF.ItemTransfer sim = LeafiaFF.fillItem(bucket, LeafiaFF.toFF(Fluids.WATER, 1000), FluidAction.SIMULATE);
		helper.assertTrue(sim.moved().getAmount() == 1000 && sim.container().is(Items.BUCKET), "simulating should report 1000 and change nothing");
		helper.assertTrue(LeafiaFF.fillItem(bucket, LeafiaFF.toFF(Fluids.WATER, 999), FluidAction.EXECUTE).moved().isEmpty(), "999 mB is not a bucket");

		LeafiaFF.ItemTransfer filled = LeafiaFF.fillItem(bucket, LeafiaFF.toFF(Fluids.WATER, 1000), FluidAction.EXECUTE);
		helper.assertTrue(filled.success() && filled.container().is(Items.WATER_BUCKET), "an empty bucket and 1000 mB of water should give a water bucket, gave " + filled.container());
		helper.assertTrue(LeafiaFF.fromFF(LeafiaFF.contained(filled.container())) == Fluids.WATER, "the water bucket holds WATER");

		LeafiaFF.ItemTransfer drained = LeafiaFF.drainItem(filled.container(), 1000, FluidAction.EXECUTE);
		helper.assertTrue(drained.moved().getAmount() == 1000 && LeafiaFF.fromFF(drained.moved()) == Fluids.WATER && drained.container().is(Items.BUCKET),
				"draining the water bucket should give 1000 WATER and a bucket");

		helper.assertTrue(LeafiaFF.fillItem(bucket, LeafiaFF.toFF(Fluids.DIESEL, 1000), FluidAction.EXECUTE).moved().isEmpty(),
				"an ff/ fluid has no bucket and must not fill one");
		if(LeafiaLiquids.fluoride != null && LeafiaFluids.FLUORIDE != null) {
			LeafiaFF.ItemTransfer salt = LeafiaFF.fillItem(bucket, LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1000), FluidAction.EXECUTE);
			helper.assertTrue(salt.container().is(LeafiaLiquids.fluoride.bucket.get()), "FLIBE should fill the addon's fluoride bucket");
		}
		helper.assertTrue(bucket.is(Items.BUCKET) && bucket.getCount() == 1, "the input stack itself must never change");
		helper.succeed();
	}

	// ------------------------------------------------------------------ tanks

	/** Base tanks as an {@code IFluidHandler}: capacity, simulate, type, pressure, NONE, and the side flags. */
	@GameTest(template = EMPTY16)
	public static void theBaseTankAdapterObeysCapacityAndSimulate(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		FluidTank tank = new FluidTank(Fluids.DIESEL, 1000);
		NTMTankHandler h = new NTMTankHandler(tank);
		FluidStack diesel = LeafiaFF.toFF(Fluids.DIESEL, 600);

		helper.assertTrue(h.getTanks() == 1 && h.getTankCapacity(0) == 1000 && h.getFluidInTank(0).isEmpty(), "an empty 1000 mB tank");
		helper.assertTrue(h.fill(diesel, FluidAction.SIMULATE) == 600 && tank.getFill() == 0, "simulate must not fill");
		helper.assertTrue(h.fill(diesel, FluidAction.EXECUTE) == 600 && tank.getFill() == 600, "execute fills 600");
		helper.assertTrue(h.fill(diesel, FluidAction.EXECUTE) == 400 && tank.getFill() == 1000, "only 400 fits");
		helper.assertTrue(h.fill(diesel, FluidAction.EXECUTE) == 0, "a full tank takes nothing");
		helper.assertTrue(h.isFluidValid(0, diesel) && !h.isFluidValid(0, LeafiaFF.toFF(Fluids.WATER, 1)), "valid only for its own type");
		helper.assertTrue(h.fill(LeafiaFF.toFF(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "the wrong fluid is refused");
		helper.assertTrue(h.getFluidInTank(0).getAmount() == 1000 && LeafiaFF.fromFF(h.getFluidInTank(0)) == Fluids.DIESEL, "it shows 1000 diesel");

		FluidStack sim = h.drain(300, FluidAction.SIMULATE);
		helper.assertTrue(sim.getAmount() == 300 && tank.getFill() == 1000, "simulated drain must not drain");
		helper.assertTrue(h.drain(LeafiaFF.toFF(Fluids.WATER, 300), FluidAction.EXECUTE).isEmpty(), "draining water from diesel gives nothing");
		FluidStack got = h.drain(LeafiaFF.toFF(Fluids.DIESEL, 1500), FluidAction.EXECUTE);
		helper.assertTrue(got.getAmount() == 1000 && tank.getFill() == 0, "draining 1500 takes all 1000");

		FluidTank pressurised = new FluidTank(Fluids.DIESEL, 1000).withPressure(1);
		pressurised.setFill(500);
		NTMTankHandler ph = new NTMTankHandler(pressurised);
		helper.assertTrue(ph.fill(diesel, FluidAction.EXECUTE) == 0 && ph.drain(100, FluidAction.EXECUTE).isEmpty()
				&& ph.getFluidInTank(0).isEmpty() && pressurised.getFill() == 500, "a pressurised tank neither gives nor takes");

		NTMTankHandler none = new NTMTankHandler(new FluidTank(Fluids.NONE, 1000));
		helper.assertTrue(none.fill(diesel, FluidAction.EXECUTE) == 0, "filling does not type a NONE tank");

		FluidTank in = new FluidTank(Fluids.DIESEL, 1000);
		in.setFill(500);
		helper.assertTrue(new NTMTankHandler(false, true, in).fill(diesel, FluidAction.EXECUTE) == 0, "a drain-only face takes nothing");
		helper.assertTrue(new NTMTankHandler(true, false, in).drain(100, FluidAction.EXECUTE).isEmpty(), "a fill-only face gives nothing");

		FluidTank a = new FluidTank(Fluids.DIESEL, 300), b = new FluidTank(Fluids.DIESEL, 300);
		helper.assertTrue(new NTMTankHandler(a, b).fill(diesel, FluidAction.EXECUTE) == 600 && a.getFill() == 300 && b.getFill() == 300,
				"a fill spreads over two tanks in order");
		helper.succeed();
	}

	/**
	 * A NeoForge tank seen as a base tank: the base network's own default send and receive move fluid
	 * in and out of it, and the stack's tag survives the trip.
	 */
	@GameTest(template = EMPTY16)
	public static void aForgeTankFeedsTheBaseNetworkThroughItsView(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		var forge = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(8000);
		ForgeTankView view = new ForgeTankView(forge, Fluids.DIESEL);
		helper.assertTrue(view.getTankType() == Fluids.DIESEL && view.getFill() == 0 && view.getMaxFill() == 8000, "empty, declared diesel");

		IFluidStandardTransceiverMK2 machine = new IFluidStandardTransceiverMK2() {
			@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { view }; }
			@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { view }; }
			@Override public FluidTank[] getAllTanks() { return new FluidTank[] { view }; }
			@Override public boolean isLoaded() { return true; }
		};

		helper.assertTrue(machine.getDemand(Fluids.DIESEL, 0) == 8000, "demand " + machine.getDemand(Fluids.DIESEL, 0));
		long left = machine.transferFluid(Fluids.DIESEL, 0, 3000);
		helper.assertTrue(left == 0 && forge.getFluidAmount() == 3000 && LeafiaFF.fromFF(forge.getFluid()) == Fluids.DIESEL,
				"the network's receive should put 3000 diesel in the Forge tank, has " + forge.getFluid());

		CompoundTag tag = new CompoundTag();
		tag.putDouble("heat", 99);
		LeafiaFF.setTag(forge.getFluid(), tag);

		helper.assertTrue(machine.getFluidAvailable(Fluids.DIESEL, 0) == 3000, "available " + machine.getFluidAvailable(Fluids.DIESEL, 0));
		machine.useUpFluid(Fluids.DIESEL, 0, 1000);
		helper.assertTrue(forge.getFluidAmount() == 2000, "the network's send should take 1000, left " + forge.getFluidAmount());
		helper.assertTrue(LeafiaFF.getTag(forge.getFluid()).getDouble("heat") == 99, "the stack's tag should survive setFill");
		helper.assertTrue(machine.getFluidAvailable(Fluids.WATER, 0) == 0 && machine.getFluidAvailable(Fluids.DIESEL, 1) == 0,
				"another type or pressure is not there");

		CompoundTag nbt = new CompoundTag();
		view.writeToNBT(nbt, "t");
		helper.assertTrue(nbt.getInt("t") == 2000 && "DIESEL".equals(nbt.getString("t_type")) && nbt.getInt("t_max") == 8000, "the view writes its delegate: " + nbt);

		view.setTankType(Fluids.WATER);
		helper.assertTrue(forge.isEmpty() && view.getTankType() == Fluids.WATER, "retyping empties the tank");
		view.readFromNBT(nbt, "t");
		helper.assertTrue(forge.getFluidAmount() == 2000 && LeafiaFF.fromFF(forge.getFluid()) == Fluids.DIESEL, "reading pushes back into the delegate");
		view.withPressure(2);
		helper.assertTrue(view.getPressure() == 0, "a Forge tank cannot be pressurised");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the world

	/** Tank to world in one call: a bucket of FLIBE becomes the addon's fluoride, water vanilla water, diesel nothing. */
	@GameTest(template = EMPTY16)
	public static void pouringPlacesTheWorldFluidAndDrainsABucket(GameTestHelper helper) {
		if(!on()) {
			helper.succeed();
			return;
		}
		var level = helper.getLevel();

		FluidTank water = new FluidTank(Fluids.WATER, 4000);
		water.setFill(1500);
		BlockPos a = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(LeafiaFF.pour(level, a, water), "water should pour");
		helper.assertTrue(level.getBlockState(a).is(Blocks.WATER) && water.getFill() == 500, "a water source and 500 mB left, got "
				+ level.getBlockState(a) + " / " + water.getFill());
		helper.assertTrue(!LeafiaFF.pour(level, helper.absolutePos(new BlockPos(4, 2, 2)), water) && water.getFill() == 500,
				"500 mB is not a bucket and must not pour");

		FluidTank diesel = new FluidTank(Fluids.DIESEL, 4000);
		diesel.setFill(2000);
		BlockPos b = helper.absolutePos(new BlockPos(6, 2, 2));
		helper.assertTrue(!LeafiaFF.pour(level, b, diesel) && diesel.getFill() == 2000 && level.getBlockState(b).isAir(),
				"diesel has no world fluid: nothing placed, nothing lost");
		helper.assertTrue(LeafiaFF.worldBlock(Fluids.DIESEL) == null && LeafiaFF.worldBlock(Fluids.WATER).is(Blocks.WATER), "worldBlock");

		if(LeafiaLiquids.fluoride != null && LeafiaFluids.FLUORIDE != null) {
			FluidTank salt = new FluidTank(LeafiaFluids.FLUORIDE, 4000);
			salt.setFill(1000);
			BlockPos c = helper.absolutePos(new BlockPos(8, 2, 2));
			helper.assertTrue(LeafiaFF.pour(level, c, salt), "FLIBE should pour");
			helper.assertTrue(level.getBlockState(c).is(LeafiaLiquids.fluoride.block.get()) && salt.getFill() == 0,
					"the addon's fluoride block and an empty tank, got " + level.getBlockState(c));
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the bottle loader

	/** At the head of the base chain, a glass bottle takes 333 mB out of a tank and a bottle puts it back. */
	@GameTest(template = EMPTY16)
	public static void theBottleLoaderFillsAndEmptiesAtTheHeadOfTheChain(GameTestHelper helper) {
		if(!LeafiaFeatures.fluidBottles) {
			helper.assertTrue(!FluidLoaderBottle.installed(), "installed with the bottle off");
			helper.succeed();
			return;
		}
		var reg = helper.getLevel().registryAccess();
		helper.assertTrue(FluidTank.loadingHandlers.get(0) instanceof FluidLoaderBottle
				&& FluidTank.loadingHandlers.stream().filter(x -> x instanceof FluidLoaderBottle).count() == 1,
				"the bottle loader should be first in the chain, once");

		FluidTank tank = new FluidTank(Fluids.DIESEL, 1000);
		tank.setFill(1000);
		ItemStack[] slots = { new ItemStack(Items.GLASS_BOTTLE, 2), ItemStack.EMPTY };
		helper.assertTrue(tank.unloadTank(0, 1, slots, reg), "unloadTank should report fluid out");
		helper.assertTrue(tank.getFill() == 667 && slots[0].getCount() == 1 && slots[1].getItem() instanceof ItemNTMFBottle
				&& ItemNTMFBottle.getFluidFromStack(slots[1]) == Fluids.DIESEL, "333 mB into a diesel bottle; tank " + tank.getFill() + ", out " + slots[1]);
		tank.unloadTank(0, 1, slots, reg);
		helper.assertTrue(tank.getFill() == 667 && slots[0].getCount() == 1, "a bottle stacks to one: a full output blocks the next");

		FluidTank water = new FluidTank(Fluids.WATER, 1000);
		water.setFill(1000);
		ItemStack[] ws = { new ItemStack(Items.GLASS_BOTTLE), ItemStack.EMPTY };
		water.unloadTank(0, 1, ws, reg);
		helper.assertTrue(!(ws[1].getItem() instanceof ItemNTMFBottle), "water is not a bottle fluid");

		FluidTank empty = new FluidTank(Fluids.NONE, 1000);
		ItemStack[] es = { ItemNTMFBottle.make(LeafiaItems.ntmfbottle.get(), Fluids.DIESEL), ItemStack.EMPTY };
		helper.assertTrue(empty.loadTank(0, 1, es, reg), "loadTank should report fluid in");
		helper.assertTrue(empty.getTankType() == Fluids.DIESEL && empty.getFill() == 333 && es[0].isEmpty() && es[1].is(Items.GLASS_BOTTLE),
				"an empty tank takes the bottle's type and 333 mB, and gives the glass back; " + empty.getTankType().getName() + " " + empty.getFill());

		FluidTank other = new FluidTank(Fluids.WATER, 1000);
		ItemStack[] os = { ItemNTMFBottle.make(LeafiaItems.ntmfbottle.get(), Fluids.DIESEL), ItemStack.EMPTY };
		other.loadTank(0, 1, os, reg);
		helper.assertTrue(other.getFill() == 0 && !os[0].isEmpty(), "a water tank refuses a diesel bottle");
		helper.succeed();
	}

	/** End to end on a real base machine: a steel barrel of diesel fills a bottle in its canister slots on its own tick. */
	@GameTest(template = EMPTY16)
	public static void aBarrelFillsABottleOnItsOwnTick(GameTestHelper helper) {
		if(!LeafiaFeatures.fluidBottles) {
			helper.succeed();
			return;
		}
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(pos, ModBlocks.barrel_steel.get().defaultBlockState());
		BlockEntityBarrel barrel = (BlockEntityBarrel) helper.getBlockEntity(pos);
		barrel.tank.setTankType(Fluids.DIESEL);
		barrel.tank.setFill(1000);
		barrel.slots[4] = new ItemStack(Items.GLASS_BOTTLE);
		barrel.updateEntity();
		helper.assertTrue(barrel.tank.getFill() == 667 && barrel.slots[4].isEmpty() && barrel.slots[5].getItem() instanceof ItemNTMFBottle
				&& ItemNTMFBottle.getFluidFromStack(barrel.slots[5]) == Fluids.DIESEL,
				"the barrel should have made a diesel bottle; tank " + barrel.tank.getFill() + ", slot 5 " + barrel.slots[5]);
		helper.succeed();
	}
}
