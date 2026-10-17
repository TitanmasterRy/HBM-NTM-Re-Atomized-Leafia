package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.gametest.TickWindow;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.fluids.ConcreteFluid;
import com.leafia.contents.fluids.FiniteFluid;
import com.leafia.contents.fluids.FluorideFluid;
import com.leafia.contents.fluids.LOXFluid;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.fluids.LeafiaLiquids.Liquid;
import com.leafia.contents.fluids.OsmiridiumFluid;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.lib.LeafiaRef;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The addon's four world fluids (D-1079): oxygen, fluoride, concrete and the molten core
 * component.
 *
 * <p>Two kinds of test, because a fluid is two kinds of thing. What a block does to whoever is
 * inside it is called directly, as {@code state.entityInside}, so each number can be read off one
 * call - duration, amplifier, damage, fire. What the fluid does to <em>itself</em> is either run
 * for real (the two classic fluids are placed and left to spread for 280 ticks, which is the only
 * honest way to measure a flow distance) or, for the finite pair, stepped by hand with
 * {@link FiniteFluid#flow}, which takes one step and none of the chance that follows it.
 *
 * <p>The chance itself - one tick in ten that concrete sets, one in ten that the core component
 * vanishes - is tested with a random source that answers the same thing every time and records
 * the bound it was asked for, so the {@code 10} is pinned and nothing here is a probability.
 *
 * <p>Every player here is {@code makeMockPlayer} or an unplaced {@code ServerPlayer}, never
 * {@code makeMockServerPlayerInLevel}: that one joins the server's player list on a connection
 * without the addon's channel, and the wind's next broadcast throws on it.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaWorldFluidTests {

	private static final String EMPTY16 = "empty16";

	private static ResourceLocation rl(String path) {
		return LeafiaRef.rl(path);
	}

	/** (fluid name, block name, bucket name) for the four, in upstream's registration order. */
	private static final String[][] NAMES = {
			{"fluoride", "fluoride_fluid"},
			{"corecomponent", "osmiridium_fluid"},
			{"leafia_concrete", "leafia_concrete_fluid"},
			{"oxygen", "oxygen_fluid"}};

	/** A random source that answers every {@code nextInt} the same way and remembers what it was asked. */
	private static final class Fixed extends LegacyRandomSource {
		final boolean hit;
		int asked = -1;

		Fixed(boolean hit) {
			super(0L);
			this.hit = hit;
		}

		@Override
		public int nextInt(int bound) {
			asked = bound;
			return hit ? 0 : bound - 1;
		}
	}

	// ------------------------------------------------------------------ registration

	/**
	 * Four liquids, each a type, two fluids, a block and a bucket; and balefire corium, a block alone since the
	 * base mod's corium became a finite block again (D-3052). Everything is gated by {@code worldFluids}, in both
	 * directions.
	 */
	@GameTest(template = EMPTY16)
	public static void fourLiquidsAndBalefireCoriumRegister(GameTestHelper helper) {

		boolean on = LeafiaFeatures.worldFluids;
		for(String[] row : NAMES) {
			String fluid = row[0], block = row[1];
			helper.assertTrue(BuiltInRegistries.FLUID.containsKey(rl(fluid)) == on, fluid + ": source " + (on ? "missing" : "registered while off"));
			helper.assertTrue(BuiltInRegistries.FLUID.containsKey(rl("flowing_" + fluid)) == on, fluid + ": flowing half");
			helper.assertTrue(NeoForgeRegistries.FLUID_TYPES.containsKey(rl(fluid)) == on, fluid + ": fluid type");
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(rl(block)) == on, block + ": block");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(rl(fluid + "_bucket")) == on, fluid + ": bucket");
		}
		helper.assertTrue(LeafiaLiquids.ALL.size() == (on ? 4 : 0), "ALL holds " + LeafiaLiquids.ALL.size());
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(rl("balecorium_fluid")) == on && (LeafiaLiquids.balecorium != null) == on,
				"balecorium_fluid: block");
		helper.assertTrue(!BuiltInRegistries.FLUID.containsKey(rl("balecorium")) && !NeoForgeRegistries.FLUID_TYPES.containsKey(rl("balecorium"))
				&& !BuiltInRegistries.ITEM.containsKey(rl("balecorium_bucket")), "balecorium: no fluid, no type, no bucket");

		if(on) {
			for(Liquid l : LeafiaLiquids.ALL) {
				var source = l.source.get();
				helper.assertTrue(l.block.get().fluid == source, l.fluidName + ": the block holds the source half");
				helper.assertTrue(source.getFlowing() == l.flowing.get() && l.flowing.get().getSource() == source, l.fluidName + ": the halves know each other");
				helper.assertTrue(source.getFluidType() == l.type.get() && l.flowing.get().getFluidType() == l.type.get(), l.fluidName + ": both halves share the type");
				helper.assertTrue(source.getBucket() == l.bucket.get() && l.bucket.get().content == source, l.fluidName + ": bucket and fluid agree");
				helper.assertTrue(source.defaultFluidState().createLegacyBlock() == l.block.get().defaultBlockState(), l.fluidName + ": a source is the block's level 0");
				helper.assertTrue(source.defaultFluidState().isSource() && !l.flowing.get().defaultFluidState().isSource(), l.fluidName + ": source and flowing");
				ItemStack bucket = new ItemStack(l.bucket.get());
				helper.assertTrue(bucket.getMaxStackSize() == 1, l.fluidName + ": buckets do not stack");
				helper.assertTrue(l.bucket.get().getCraftingRemainingItem(bucket).getItem() == Items.BUCKET, l.fluidName + ": an emptied bucket leaves a bucket");
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the numbers

	/**
	 * Density, viscosity and temperature are upstream's {@code Fluid} fields, same units.
	 * Forge's defaults - 1000, 1000 - apply wherever upstream sets nothing, and 295 K for
	 * temperature; NeoForge's own is 300, so concrete's is set explicitly.
	 */
	@GameTest(template = EMPTY16)
	public static void fluidTypesCarryUpstreamsPhysicalConstants(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }

		// density, viscosity, temperature
		int[][] expected = {
				{1000, 1000, 500 + 273},   // fluoride
				{31200, 2000, 3200 + 273}, // corecomponent
				{31200, 2000, 295},        // leafia_concrete
				{1000, 1000, -100 + 273}}; // oxygen
		Liquid[] liquids = {LeafiaLiquids.fluoride, LeafiaLiquids.osmiridium, LeafiaLiquids.concrete, LeafiaLiquids.lox};
		for(int i = 0; i < 4; i++) {
			FluidType t = liquids[i].type.get();
			String n = liquids[i].fluidName;
			helper.assertTrue(t.getDensity() == expected[i][0], n + " density " + t.getDensity() + ", expected " + expected[i][0]);
			helper.assertTrue(t.getViscosity() == expected[i][1], n + " viscosity " + t.getViscosity() + ", expected " + expected[i][1]);
			helper.assertTrue(t.getTemperature() == expected[i][2], n + " temperature " + t.getTemperature() + ", expected " + expected[i][2]);
			helper.assertTrue(t.getLightLevel() == 0, n + " is not luminous: upstream sets no luminosity");
		}
		helper.assertTrue(LOXFluid.TEMPERATURE == 173 && FluorideFluid.TEMPERATURE == 773 && OsmiridiumFluid.TEMPERATURE == 3473
				&& ConcreteFluid.TEMPERATURE == 295, "the temperature constants");
		helper.assertTrue(LeafiaLiquids.lox.type.get().getDescriptionId().equals("hbmfluid.oxygen")
				&& LeafiaLiquids.fluoride.type.get().getDescriptionId().equals("hbmfluid.flibe")
				&& LeafiaLiquids.concrete.type.get().getDescriptionId().equals("hbmfluid.concrete")
				&& LeafiaLiquids.osmiridium.type.get().getDescriptionId().equals("hbmfluid.corecomponent"),
				"each fluid keeps upstream's unlocalised name");
		helper.succeed();
	}

	/**
	 * The material each block was built from, as the flags NeoForge uses for it. Water swims,
	 * drowns, extinguishes, floats a boat, hydrates farmland and takes the fall out of a drop;
	 * lava does none of the first two and burns; concrete's material "blocks movement", so
	 * mobs path around it and the flow does not push anything.
	 */
	@GameTest(template = EMPTY16)
	public static void materialsBecameFluidTypeFlags(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 1);
		Boat boat = EntityType.BOAT.create(level);
		BlockPos pos = cow.blockPosition();
		FluidState air = Fluids.EMPTY.defaultFluidState();

		FluidType lox = LeafiaLiquids.lox.type.get();
		helper.assertTrue(lox.canSwim(cow) && lox.canDrownIn(cow) && lox.canExtinguish(cow) && lox.canHydrate(cow)
				&& lox.supportsBoating(boat) && lox.getFallDistanceModifier(cow) == 0F,
				"oxygen is Material.WATER: swim, drown, extinguish, hydrate, boat, no fall damage");
		helper.assertTrue(lox.getBlockPathType(air, level, pos, cow, true) == net.minecraft.world.level.pathfinder.PathType.WATER, "oxygen is water to a mob");

		for(Liquid lava : new Liquid[] {LeafiaLiquids.fluoride, LeafiaLiquids.osmiridium}) {
			FluidType t = lava.type.get();
			helper.assertTrue(!t.canSwim(cow) && !t.canDrownIn(cow) && !t.canExtinguish(cow) && !t.canHydrate(cow) && !t.supportsBoating(boat),
					lava.fluidName + " is Material.LAVA: no swimming, no drowning, no extinguishing");
			helper.assertTrue(t.getBlockPathType(air, level, pos, cow, true) == net.minecraft.world.level.pathfinder.PathType.LAVA, lava.fluidName + " is lava to a mob");
			helper.assertTrue(t.canPushEntity(cow), lava.fluidName + ": a lava material's flow pushes");
		}
		// The core component's push is lava's, scaled by vanilla's own viscosity ratio.
		double scale = LeafiaLiquids.osmiridium.type.get().motionScale(cow);
		helper.assertTrue(Math.abs(scale - 0.007) < 1e-12, "viscosity 2000 -> motion scale " + scale + ", expected 0.014 x 1000 / 2000");
		helper.assertTrue(Math.abs(LeafiaLiquids.fluoride.type.get().motionScale(cow) - 0.014) < 1e-12, "viscosity 1000 -> vanilla's water scale");

		FluidType concrete = LeafiaLiquids.concrete.type.get();
		helper.assertTrue(!concrete.canSwim(cow) && !concrete.canDrownIn(cow) && !concrete.canPushEntity(cow)
				&& concrete.getBlockPathType(air, level, pos, cow, true) == net.minecraft.world.level.pathfinder.PathType.BLOCKED,
				"concrete is a liquid material that blocks movement: not swum, not drowned in, no push, a wall to a mob");
		helper.succeed();
	}

	/** Upstream's {@code tickRate}: 30, 30, 40 and 30. Water's is 5. */
	@GameTest(template = EMPTY16)
	public static void tickRatesAreUpstreams(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Level level = helper.getLevel();
		int[] rates = {
				LeafiaLiquids.lox.source.get().getTickDelay(level), LeafiaLiquids.fluoride.source.get().getTickDelay(level),
				LeafiaLiquids.concrete.source.get().getTickDelay(level), LeafiaLiquids.osmiridium.source.get().getTickDelay(level),
				LeafiaLiquids.lox.flowing.get().getTickDelay(level), LeafiaLiquids.concrete.flowing.get().getTickDelay(level)};
		int[] want = {30, 30, 40, 30, 30, 40};
		for(int i = 0; i < rates.length; i++)
			helper.assertTrue(rates[i] == want[i], "tick delay #" + i + " is " + rates[i] + ", expected " + want[i]);
		helper.assertTrue(LOXFluid.TICK_RATE == 30 && FluorideFluid.TICK_RATE == 30 && ConcreteFluid.TICK_RATE == 40 && OsmiridiumFluid.TICK_RATE == 30,
				"the constants the registrations read");
		helper.succeed();
	}

	// ------------------------------------------------------------------ flow

	private static boolean isFluid(GameTestHelper helper, Liquid l, int x, int y, int z) {
		return helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(x, y, z))).getType().isSame(l.source.get());
	}

	private static int amount(GameTestHelper helper, Liquid l, int x, int y, int z) {
		FluidState s = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(x, y, z)));
		return s.getType().isSame(l.source.get()) ? s.getAmount() : 0;
	}

	private static void floor(GameTestHelper helper, int size) {
		for(int x = 0; x < size; x++)
			for(int z = 0; z < size; z++)
				helper.setBlock(x, 0, z, Blocks.STONE);
	}

	/**
	 * A classic fluid spreads seven blocks: a source of eight, then seven, six, down to one. That
	 * is {@code quantaPerBlock} 8 on Forge's {@code BlockFluidClassic} and {@code BaseFlowingFluid}'s
	 * default drop-off of one, and the only way to see it is to let it run - 30 ticks a hop, so
	 * 280 is seven hops and a margin.
	 */
	private static void spreadsSevenBlocks(GameTestHelper helper, Liquid l) {
		floor(helper, 16);
		helper.setBlock(7, 1, 7, l.source.get().defaultFluidState().createLegacyBlock());
		TickWindow.runAfterDelay(helper, 280, () -> { // fluid ticks are scheduled ticks: they wait on the entity section (D-1262)
			for(Direction dir : Direction.Plane.HORIZONTAL) {
				int reach = 0;
				for(int d = 1; d <= 9; d++)
					if(isFluid(helper, l, 7 + dir.getStepX() * d, 1, 7 + dir.getStepZ() * d)) reach = d;
				helper.assertTrue(reach == 7, l.fluidName + " reached " + reach + " blocks " + dir + ", expected 7");
			}
			helper.assertTrue(amount(helper, l, 7, 1, 7) == 8, "the source stays a full block");
			for(int d = 1; d <= 7; d++)
				helper.assertTrue(amount(helper, l, 7 + d, 1, 7) == 8 - d, l.fluidName + " " + d + " blocks out holds " + amount(helper, l, 7 + d, 1, 7) + ", expected " + (8 - d));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY16, timeoutTicks = 280 + TickWindow.TIMEOUT)
	public static void liquidOxygenSpreadsSevenBlocks(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		spreadsSevenBlocks(helper, LeafiaLiquids.lox);
	}

	@GameTest(template = EMPTY16, timeoutTicks = 280 + TickWindow.TIMEOUT)
	public static void liquidFluorideSpreadsSevenBlocks(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		spreadsSevenBlocks(helper, LeafiaLiquids.fluoride);
	}

	/** A bucket places a full block, which here is the source, and a source - and only a source - can be picked up. */
	@GameTest(template = EMPTY16)
	public static void bucketsPlaceASourceAndOnlyASourceComesBack(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		int x = 0;
		for(Liquid l : LeafiaLiquids.ALL) {
			BlockPos rel = new BlockPos(x++, 1, 1);
			BlockPos pos = helper.absolutePos(rel);
			helper.assertTrue(l.bucket.get().emptyContents(null, level, pos, null), l.fluidName + ": the bucket should empty into air");
			BlockState placed = level.getBlockState(pos);
			helper.assertTrue(placed == l.block.get().defaultBlockState() && placed.getFluidState().isSource(),
					l.fluidName + ": placed " + placed + ", expected the block's source");

			BlockPos partial = pos.above(2);
			level.setBlock(partial, l.block.get().defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3), 3);
			helper.assertTrue(!((BucketPickup) l.block.get()).pickupBlock(null, level, partial, level.getBlockState(partial)).getItem().equals(l.bucket.get()),
					l.fluidName + ": a part-block is not a bucketful");

			ItemStack got = ((BucketPickup) l.block.get()).pickupBlock(null, level, pos, placed);
			helper.assertTrue(got.getItem() == l.bucket.get(), l.fluidName + ": picked up " + got);
			helper.assertTrue(level.getBlockState(pos).isAir(), l.fluidName + ": the block is gone after pickup");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ finite flow

	private static FiniteFluid finite(Liquid l) {
		return (FiniteFluid) l.source.get();
	}

	private static void put(GameTestHelper helper, Liquid l, int x, int y, int z, int quanta) {
		helper.setBlock(x, y, z, finite(l).withQuanta(quanta).createLegacyBlock());
	}

	private static int total(GameTestHelper helper, Liquid l, int size, int height) {
		int sum = 0;
		for(int x = 0; x < size; x++)
			for(int y = 0; y < height; y++)
				for(int z = 0; z < size; z++)
					sum += amount(helper, l, x, y, z);
		return sum;
	}

	private static boolean flow(GameTestHelper helper, Liquid l, int x, int y, int z) {
		return finite(l).flow(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
	}

	/** Falls whole: a block over empty air moves all eight quanta down and leaves nothing behind. */
	@GameTest(template = EMPTY16)
	public static void aFiniteFluidFallsAsAWholeBlock(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		for(Liquid l : new Liquid[] {LeafiaLiquids.concrete, LeafiaLiquids.osmiridium}) {
			floor(helper, 9);
			helper.setBlock(4, 1, 4, Blocks.AIR);
			put(helper, l, 4, 2, 4, 8);
			helper.assertTrue(flow(helper, l, 4, 2, 4), l.fluidName + ": there was a block to flow");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), l.fluidName + ": the top is empty");
			helper.assertTrue(amount(helper, l, 4, 1, 4) == 8 && helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(4, 1, 4))).isSource(),
					l.fluidName + ": all eight arrived, as a source");
			helper.assertTrue(!flow(helper, l, 4, 2, 4), "nothing flows from an empty block");
			helper.setBlock(4, 1, 4, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * Standing on a floor, a full block shares itself: a quantum to each of the four sides and
	 * four kept. Nothing is made or lost.
	 */
	@GameTest(template = EMPTY16)
	public static void aFiniteFluidOnTheFloorSharesWithItsSides(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Liquid l = LeafiaLiquids.concrete;
		floor(helper, 9);
		put(helper, l, 4, 1, 4, 8);
		flow(helper, l, 4, 1, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 4, "the centre keeps " + amount(helper, l, 4, 1, 4) + ", expected 4");
		for(Direction d : Direction.Plane.HORIZONTAL)
			helper.assertTrue(amount(helper, l, 4 + d.getStepX(), 1, 4 + d.getStepZ()) == 1, d + " side holds " + amount(helper, l, 4 + d.getStepX(), 1, 4 + d.getStepZ()) + ", expected 1");
		helper.assertTrue(total(helper, l, 9, 3) == 8, "eight quanta in, eight out: " + total(helper, l, 9, 3));
		helper.succeed();
	}

	/** Over a block of its own kind with room, it tops that up first and keeps only the rest. */
	@GameTest(template = EMPTY16)
	public static void aFiniteFluidTopsUpTheBlockBelow(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Liquid l = LeafiaLiquids.osmiridium;
		floor(helper, 9);
		put(helper, l, 4, 1, 4, 5);
		put(helper, l, 4, 2, 4, 4);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 8, "the lower block is full: " + amount(helper, l, 4, 1, 4));
		// 4 + 5 = 9: eight below, one left to share, which cannot be split.
		helper.assertTrue(amount(helper, l, 4, 2, 4) == 1, "the upper block keeps the one that did not fit: " + amount(helper, l, 4, 2, 4));
		helper.succeed();
	}

	/**
	 * A puddle settles and stays settled. Sweeping every cell thirty times conserves the total,
	 * keeps every cell within a block, and ends in a state a further sweep does not change.
	 */
	@GameTest(template = EMPTY16)
	public static void aFinitePuddleConservesItselfAndSettles(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Liquid l = LeafiaLiquids.concrete;
		floor(helper, 9);
		put(helper, l, 4, 3, 4, 8);
		put(helper, l, 4, 4, 4, 8);
		put(helper, l, 5, 1, 4, 6);

		for(int sweep = 0; sweep < 30; sweep++) {
			for(int y = 0; y < 6; y++)
				for(int x = 0; x < 9; x++)
					for(int z = 0; z < 9; z++)
						flow(helper, l, x, y, z);
			helper.assertTrue(total(helper, l, 9, 8) == 22, "sweep " + sweep + ": total " + total(helper, l, 9, 8) + ", expected 8 + 8 + 6");
		}
		int[] before = new int[81];
		for(int x = 0; x < 9; x++) for(int z = 0; z < 9; z++) before[x * 9 + z] = amount(helper, l, x, 1, z);
		for(int x = 0; x < 9; x++) for(int z = 0; z < 9; z++) flow(helper, l, x, 1, z);
		for(int x = 0; x < 9; x++) for(int z = 0; z < 9; z++)
			helper.assertTrue(amount(helper, l, x, 1, z) == before[x * 9 + z], "(" + x + "," + z + ") still moving after thirty sweeps");
		for(int y = 2; y < 8; y++) helper.assertTrue(total(helper, l, 9, y + 1) == total(helper, l, 9, y), "nothing is left in the air");
		helper.succeed();
	}

	/**
	 * Concrete and the core component are denser than anything vanilla has, so they take the place
	 * of water and lava; oxygen and fluoride, at water's density, cannot; and a waterlogged slab is
	 * a slab, not a pool - replacing it would delete the block.
	 */
	@GameTest(template = EMPTY16)
	public static void densityDecidesWhatGivesWay(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Liquid l = LeafiaLiquids.concrete;
		floor(helper, 9);

		helper.setBlock(4, 1, 4, Blocks.WATER);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 8, "concrete should have replaced the water, got " + helper.getBlockState(new BlockPos(4, 1, 4)));

		helper.setBlock(4, 1, 4, Blocks.LAVA);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 8, "and the lava");

		BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
		helper.setBlock(4, 1, 4, slab);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)) == slab, "a waterlogged slab must stay a slab");

		// A slab would survive on being solid alone. A sea pickle has no body at all (it is
		// noCollission), so only the waterlogging guard keeps it from being read as a pool and replaced.
		BlockState pickle = Blocks.SEA_PICKLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
		helper.setBlock(4, 1, 4, pickle);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)) == pickle, "a waterlogged sea pickle must stay a sea pickle");
		helper.setBlock(4, 1, 4, Blocks.SEA_PICKLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 8, "a dry sea pickle has no body and is displaced");

		// The vanilla-facing half, on the fluid state: who may replace whom.
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		FluidState concrete = LeafiaLiquids.concrete.source.get().defaultFluidState();
		FluidState oxygen = LeafiaLiquids.lox.source.get().defaultFluidState();
		FluidState core = LeafiaLiquids.osmiridium.source.get().defaultFluidState();
		helper.assertTrue(!concrete.canBeReplacedWith(level, at, Fluids.WATER, Direction.DOWN), "water cannot displace concrete");
		helper.assertTrue(!concrete.canBeReplacedWith(level, at, Fluids.LAVA, Direction.DOWN), "nor can lava: 3000 against 31200");
		helper.assertTrue(!oxygen.canBeReplacedWith(level, at, Fluids.WATER, Direction.DOWN), "water at oxygen's density does not displace it");
		helper.assertTrue(oxygen.canBeReplacedWith(level, at, LeafiaLiquids.concrete.source.get(), Direction.DOWN), "concrete does");
		helper.assertTrue(oxygen.canBeReplacedWith(level, at, LeafiaLiquids.osmiridium.source.get(), Direction.EAST), "and from any side, which is Forge's rule");
		helper.assertTrue(!core.canBeReplacedWith(level, at, LeafiaLiquids.concrete.source.get(), Direction.DOWN), "equal density displaces nothing");
		helper.assertTrue(!concrete.canBeReplacedWith(level, at, LeafiaLiquids.concrete.flowing.get(), Direction.DOWN), "a fluid never replaces itself");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the core component's appetite

	/** {@code max(1, (int)(sqrt(resistance) x 1.2))}, read off real blocks. */
	@GameTest(template = EMPTY16)
	public static void theCoreComponentsOddsFollowExplosionResistance(GameTestHelper helper) {
		helper.assertTrue(OsmiridiumFluid.oddsOneIn(Blocks.STONE.getExplosionResistance()) == 2, "stone (6): one in 2");
		helper.assertTrue(OsmiridiumFluid.oddsOneIn(Blocks.OBSIDIAN.getExplosionResistance()) == 41, "obsidian (1200): one in 41");
		helper.assertTrue(OsmiridiumFluid.oddsOneIn(Blocks.BEDROCK.getExplosionResistance()) == 2276, "bedrock (3,600,000): one in 2276");
		helper.assertTrue(OsmiridiumFluid.oddsOneIn(Blocks.OAK_LEAVES.getExplosionResistance()) == 1, "leaves (0.2): always");
		helper.assertTrue(OsmiridiumFluid.oddsOneIn(0F) == 1, "nothing: always");

		Fixed hit = new Fixed(true), miss = new Fixed(false);
		helper.assertTrue(OsmiridiumFluid.canDisplace(hit, Blocks.STONE.defaultBlockState()), "stone, on a hit");
		helper.assertTrue(hit.asked == 2, "and it rolled against 2, asked " + hit.asked);
		helper.assertTrue(!OsmiridiumFluid.canDisplace(miss, Blocks.STONE.defaultBlockState()), "stone, on a miss");
		helper.assertTrue(OsmiridiumFluid.canDisplace(miss, Blocks.OAK_LEAVES.defaultBlockState()), "leaves give way whatever is rolled");
		helper.assertTrue(OsmiridiumFluid.canDisplace(miss, Blocks.AIR.defaultBlockState()), "air too");
		helper.assertTrue(!OsmiridiumFluid.canDisplace(hit, Blocks.WATER.defaultBlockState()), "a liquid refuses outright - displaceIfPossible");
		helper.assertTrue(!OsmiridiumFluid.canDisplace(hit, Blocks.LAVA.defaultBlockState()), "lava too");
		helper.succeed();
	}

	/** In a world: it eats grass, leaves water alone, and stops at a floor. */
	@GameTest(template = EMPTY16)
	public static void theCoreComponentEatsSoftBlocksAndNotWater(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		Liquid l = LeafiaLiquids.osmiridium;
		floor(helper, 9);

		helper.setBlock(4, 1, 4, Blocks.SHORT_GRASS);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(amount(helper, l, 4, 1, 4) == 8, "it should have flowed through the grass, got " + helper.getBlockState(new BlockPos(4, 1, 4)));

		helper.setBlock(4, 1, 4, Blocks.WATER);
		put(helper, l, 4, 2, 4, 8);
		flow(helper, l, 4, 2, 4);
		helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)) == Blocks.WATER.defaultBlockState(), "water is not displaced");
		helper.assertTrue(amount(helper, l, 4, 2, 4) == 4, "it spreads instead: " + amount(helper, l, 4, 2, 4));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the chance

	/**
	 * {@code BlockFluidBase} called {@code setTickRandomly(true)}, so a finite puddle is stepped by
	 * random ticks - which is the only way a settled puddle of concrete ever sets. Vanilla water
	 * has no random tick, and neither do the two classic fluids here.
	 */
	@GameTest(template = EMPTY16)
	public static void finiteFluidsAreRandomlyTickedAndClassicOnesAreNot(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		for(Liquid l : new Liquid[] {LeafiaLiquids.concrete, LeafiaLiquids.osmiridium}) {
			helper.assertTrue(l.source.get().defaultFluidState().isRandomlyTicking() && l.flowing.get().defaultFluidState().isRandomlyTicking(),
					l.fluidName + " should be randomly ticked");
			helper.assertTrue(l.block.get().defaultBlockState().isRandomlyTicking(), l.fluidName + "'s block should be randomly ticked");
		}
		for(Liquid l : new Liquid[] {LeafiaLiquids.lox, LeafiaLiquids.fluoride})
			helper.assertTrue(!l.source.get().defaultFluidState().isRandomlyTicking(), l.fluidName + " is a classic fluid, as still as water");

		// And a random tick is a flow step: eight quanta over air fall.
		floor(helper, 9);
		put(helper, LeafiaLiquids.concrete, 4, 2, 4, 8);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		helper.getLevel().getFluidState(at).randomTick(helper.getLevel(), at, helper.getLevel().random);
		helper.assertTrue(amount(helper, LeafiaLiquids.concrete, 4, 1, 4) == 8, "a random tick should have let the block fall");
		helper.succeed();
	}

	/** Concrete's 1 in 10: a hit sets whatever is at the position, even air; a miss does nothing; the bound is 10. */
	@GameTest(template = EMPTY16)
	public static void concreteSetsOneTickInTen(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(2, 2, 2), abs = helper.absolutePos(rel);
		Fixed miss = new Fixed(false), hit = new Fixed(true);

		ConcreteFluid.harden(level, abs, miss);
		helper.assertTrue(miss.asked == 10, "asked for a roll of " + miss.asked + ", expected nextInt(10)");
		helper.assertTrue(level.getBlockState(abs).isAir(), "a miss changes nothing");

		ConcreteFluid.harden(level, abs, hit);
		helper.assertTrue(level.getBlockState(abs).is(com.hbm.blocks.ModBlocks.concrete_smooth.get()),
				"a hit puts smooth concrete there - in the air, where a block that has just drained left it (upstream's quirk): " + level.getBlockState(abs));
		helper.assertTrue(ConcreteFluid.HARDEN_ONE_IN == 10, "the constant");

		// And through a real step: flow, then the chance, at the position the block started on.
		floor(helper, 9);
		put(helper, LeafiaLiquids.concrete, 4, 2, 4, 8);
		BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
		finite(LeafiaLiquids.concrete).flow(level, start);
		finite(LeafiaLiquids.concrete).afterFlow(level, start, new Fixed(true));
		helper.assertTrue(level.getBlockState(start).is(com.hbm.blocks.ModBlocks.concrete_smooth.get()), "the block that fell left concrete behind");
		helper.succeed();
	}

	/** The core component's: radiate every tick, then one in ten, vanish - whatever is there. */
	@GameTest(template = EMPTY16)
	public static void theCoreComponentRadiatesThenVanishesOneTickInTen(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(2, 2, 2), abs = helper.absolutePos(rel);
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2.5F, 2F, 4.5F);
		double len = cow.position().add(0, cow.getEyeHeight(), 0).distanceTo(new Vec3(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5));
		double dmgLen = Math.max(len, OsmiridiumFluid.RADIATE_RANGE * 0.05);

		helper.setBlock(rel, Blocks.STONE);
		Fixed miss = new Fixed(false);
		OsmiridiumFluid.burnOff(level, abs, miss);
		helper.assertTrue(miss.asked == 10, "asked nextInt(" + miss.asked + "), expected 10");
		helper.assertTrue(level.getBlockState(abs).is(Blocks.STONE), "a miss leaves the block");
		float dig = HbmLivingProps.getDigamma(cow);
		float expected = (float) (85.0 / (dmgLen * dmgLen * dmgLen));
		helper.assertTrue(Math.abs(dig - expected) < 1e-3, "digamma " + dig + ", expected 85 / " + dmgLen + "^3 = " + expected);
		helper.assertTrue(Math.abs(OsmiridiumFluid.DIGAMMA - 85F) == 0 && OsmiridiumFluid.RADIATE_RANGE == 60, "radiate(..., 60, 0, 85, 0)");

		OsmiridiumFluid.burnOff(level, abs, new Fixed(true));
		helper.assertTrue(level.getBlockState(abs).isAir(), "a hit clears the position, whatever it holds");
		helper.succeed();
	}

	// ------------------------------------------------------------------ contact

	/** Frigid for ten seconds at amplifier 1, and one cryo damage - from a block touching a cow. */
	@GameTest(template = EMPTY16)
	public static void liquidOxygenFreezesAndHurts(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
		BlockState lox = LeafiaLiquids.lox.block.get().defaultBlockState();
		float before = cow.getHealth();

		lox.entityInside(level, cow.blockPosition(), cow);

		MobEffectInstance frigid = cow.getEffect(LeafiaPotion.frigid);
		helper.assertTrue(frigid != null, "no frigid");
		helper.assertTrue(frigid.getDuration() == 200, "frigid for " + frigid.getDuration() + " ticks, expected 10 s = 200");
		helper.assertTrue(frigid.getAmplifier() == 1, "frigid amplifier " + frigid.getAmplifier() + ", expected 1");
		helper.assertTrue(cow.getHealth() == before - 1F, "health " + before + " -> " + cow.getHealth() + ", expected 1 damage");
		helper.assertTrue(cow.getLastDamageSource() != null && cow.getLastDamageSource().is(LeafiaDamageSources.cryo), "the damage is cryo: " + cow.getLastDamageSource());
		helper.assertTrue(LOXFluid.FRIGID_TICKS == 200 && LOXFluid.FRIGID_AMPLIFIER == 1 && LOXFluid.CRYO_DAMAGE == 1F, "the constants");

		// Not a living thing: nothing to freeze, and nothing thrown.
		Entity item = helper.spawnItem(Items.STICK, 2, 2, 2);
		lox.entityInside(level, item.blockPosition(), item);
		helper.succeed();
	}

	/** The same, but through the game's own path: a cow standing in a placed block, moved. */
	@GameTest(template = EMPTY16)
	public static void aCowStandingInLiquidOxygenIsFrozen(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		helper.setBlock(2, 2, 2, LeafiaLiquids.lox.source.get().defaultFluidState().createLegacyBlock());
		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2.5F, 2F, 2.5F);
		cow.move(MoverType.SELF, Vec3.ZERO);
		MobEffectInstance frigid = cow.getEffect(LeafiaPotion.frigid);
		helper.assertTrue(frigid != null && frigid.getAmplifier() == 1, "a moved cow should be frozen by the block it stands in: " + frigid);
		helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "and hurt");
		helper.succeed();
	}

	/**
	 * Fluoride: lava, so a cow whose box is in it is set alight for fifteen seconds and hurt four;
	 * and upstream's own {@code setFire(3)}, which is all there is when the box only grazes it.
	 */
	@GameTest(template = EMPTY16)
	public static void fluorideBurnsLikeLavaAndSetsFireForThree(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockState fluoride = LeafiaLiquids.fluoride.block.get().defaultBlockState();

		Mob in = helper.spawnWithNoFreeWill(EntityType.COW, 2.5F, 2F, 2.5F);
		float before = in.getHealth();
		fluoride.entityInside(level, in.blockPosition(), in);
		helper.assertTrue(in.getRemainingFireTicks() == 15 * 20, "fire " + in.getRemainingFireTicks() + " ticks, expected lava's 15 s = 300");
		helper.assertTrue(in.getHealth() == before - 4F, "health " + before + " -> " + in.getHealth() + ", expected lava's 4");

		Mob grazing = helper.spawnWithNoFreeWill(EntityType.COW, 6.5F, 2F, 6.5F);
		float grazingBefore = grazing.getHealth();
		fluoride.entityInside(level, grazing.blockPosition().east(), grazing);
		helper.assertTrue(grazing.getRemainingFireTicks() == 3 * 20, "fire " + grazing.getRemainingFireTicks() + ", expected setFire(3) = 60");
		helper.assertTrue(grazing.getHealth() == grazingBefore, "no lava damage outside the shrunken box");

		// Head 0.4 into the block above: exactly what the vertical shrink excuses, so it is only setFire(3).
		Mob head = helper.spawnWithNoFreeWill(EntityType.COW, 10.5F, 2F, 10.5F);
		float headBefore = head.getHealth();
		fluoride.entityInside(level, head.blockPosition().above(), head);
		helper.assertTrue(head.getRemainingFireTicks() == 3 * 20, "fire " + head.getRemainingFireTicks() + " with only the head 0.4 in, expected 60");
		helper.assertTrue(head.getHealth() == headBefore, "and no lava damage: the box is shrunk 0.4 top and bottom");
		helper.assertTrue(FluorideFluid.FIRE_SECONDS == 3F, "the constant");
		helper.succeed();
	}

	/** Concrete: a cobweb, plus one point of in-wall damage when the eyes are in it. */
	@GameTest(template = EMPTY16)
	public static void concreteWebsAndSmothers(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockState concrete = LeafiaLiquids.concrete.block.get().defaultBlockState();

		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2.5F, 2F, 2.5F);
		float before = cow.getHealth();
		cow.fallDistance = 5F;
		concrete.entityInside(level, cow.blockPosition(), cow);
		helper.assertTrue(cow.fallDistance == 0F, "stuck in a web resets the fall: " + cow.fallDistance);
		helper.assertTrue(cow.getHealth() == before, "feet in concrete do not hurt");

		BlockPos eyes = BlockPos.containing(cow.getX(), cow.getEyeY(), cow.getZ());
		concrete.entityInside(level, eyes, cow);
		helper.assertTrue(cow.getHealth() == before - 1F, "eyes in concrete: " + before + " -> " + cow.getHealth() + ", expected 1");
		helper.assertTrue(cow.getLastDamageSource() != null && cow.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL), "in-wall damage");
		helper.assertTrue(ConcreteFluid.WEB.x == 0.25 && ConcreteFluid.WEB.y == 0.05 && ConcreteFluid.WEB.z == 0.25, "a cobweb's multipliers");
		helper.succeed();
	}

	/** The core component: web, three seconds of fire, four of in-fire damage, and a thin trickle of digamma. */
	@GameTest(template = EMPTY16)
	public static void theCoreComponentWebsBurnsAndContaminates(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockState core = LeafiaLiquids.osmiridium.block.get().defaultBlockState();

		Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2.5F, 2F, 2.5F);
		float before = cow.getHealth();
		cow.fallDistance = 5F;
		BlockPos away = cow.blockPosition().east(4); // outside the box lava's test shrinks: only upstream's own part runs
		core.entityInside(level, away, cow);
		helper.assertTrue(cow.fallDistance == 0F, "webbed: " + cow.fallDistance);
		helper.assertTrue(cow.getRemainingFireTicks() == 60, "fire " + cow.getRemainingFireTicks() + ", expected setFire(3) = 60");
		helper.assertTrue(cow.getHealth() == before - 4F, "health " + before + " -> " + cow.getHealth() + ", expected IN_FIRE 4");
		helper.assertTrue(cow.getLastDamageSource() != null && cow.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE), "in-fire damage");
		float dig = HbmLivingProps.getDigamma(cow);
		helper.assertTrue(Math.abs(dig - 1F / 200) < 1e-6, "digamma " + dig + ", expected 1/200");

		Mob inside = helper.spawnWithNoFreeWill(EntityType.COW, 6.5F, 2F, 6.5F);
		core.entityInside(level, inside.blockPosition(), inside);
		helper.assertTrue(inside.getRemainingFireTicks() == 15 * 20, "inside the box it is lava as well: " + inside.getRemainingFireTicks());
		helper.succeed();
	}

	private static ServerPlayer unplacedPlayer(GameTestHelper helper, boolean creative, boolean spectator) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(java.util.UUID.randomUUID(), "fluid-test"),
				net.minecraft.server.level.ClientInformation.createDefault()) {
			@Override
			public boolean isCreative() {
				return creative;
			}

			@Override
			public boolean isSpectator() {
				return spectator;
			}
		};
		player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
		return player;
	}

	/**
	 * Creative and spectator players on the <em>server</em> are left alone - and a player that is
	 * not a {@code ServerPlayer} is not, which is upstream's {@code instanceof EntityPlayerMP}:
	 * a creative player's own client is still webbed. Kept, and flagged.
	 */
	@GameTest(template = EMPTY16)
	public static void creativeServerPlayersAreSparedAndNobodyElseIs(GameTestHelper helper) {
		if(!LeafiaFeatures.worldFluids) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		for(Liquid l : new Liquid[] {LeafiaLiquids.concrete, LeafiaLiquids.osmiridium}) {
			BlockState block = l.block.get().defaultBlockState();
			for(boolean spectator : new boolean[] {false, true}) {
				ServerPlayer spared = unplacedPlayer(helper, !spectator, spectator);
				spared.fallDistance = 5F;
				block.entityInside(level, spared.blockPosition().east(4), spared);
				helper.assertTrue(spared.fallDistance == 5F, l.fluidName + ": a " + (spectator ? "spectator" : "creative") + " server player is not webbed");
				helper.assertTrue(spared.getRemainingFireTicks() <= 0, l.fluidName + ": nor set alight");
			}
			ServerPlayer survival = unplacedPlayer(helper, false, false);
			survival.fallDistance = 5F;
			block.entityInside(level, survival.blockPosition().east(4), survival);
			helper.assertTrue(survival.fallDistance == 0F, l.fluidName + ": a survival server player is webbed");

			Player mock = helper.makeMockPlayer(GameType.CREATIVE);
			mock.fallDistance = 5F;
			block.entityInside(level, mock.blockPosition().east(4), mock);
			helper.assertTrue(mock.fallDistance == 0F, l.fluidName + ": a creative player that is not a ServerPlayer is webbed - upstream's check");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ assets

	/**
	 * The textures upstream names, copied unchanged: still 16 wide, flowing 32 wide, every frame
	 * square - the layout vanilla's own fluids have, so the animation needs no help.
	 */
	@GameTest(template = EMPTY16)
	public static void theTexturesAreUpstreamsAndWellFormed(GameTestHelper helper) {
		String[][] files = {
				{"lox_still", "16"}, {"lox_flow", "32"},
				{"fluoride_still", "16"}, {"fluoride_flow", "32"},
				{"osmiridium_still", "16"}, {"osmiridium_flow", "32"},
				{"concrete_static", "16"}, {"concrete_flow", "32"},
				{"corium_bale_still", "16"}, {"corium_bale_flow", "32"}};
		List<String> problems = new ArrayList<>();
		for(String[] f : files) {
			String path = "/assets/leafia/textures/block/forgefluid/" + f[0] + ".png";
			try(InputStream in = LeafiaWorldFluidTests.class.getResourceAsStream(path)) {
				if(in == null) { problems.add(path + " is missing"); continue; }
				BufferedImage image = ImageIO.read(in);
				int width = Integer.parseInt(f[1]);
				if(image.getWidth() != width) problems.add(f[0] + " is " + image.getWidth() + " wide, expected " + width);
				if(image.getHeight() % width != 0) problems.add(f[0] + " is " + image.getHeight() + " tall, not a whole number of square frames");
			} catch(java.io.IOException e) {
				problems.add(path + " unreadable: " + e);
			}
			if(!f[0].equals("concrete_static") && LeafiaWorldFluidTests.class.getResourceAsStream(path + ".mcmeta") == null)
				problems.add(path + ".mcmeta is missing - an animated texture without its animation");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
