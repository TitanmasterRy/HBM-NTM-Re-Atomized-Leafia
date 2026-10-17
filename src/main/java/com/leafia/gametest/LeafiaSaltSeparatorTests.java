package com.leafia.gametest;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.gametest.TickWindow;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.FluidContainer;
import com.hbm.inventory.FluidContainerRegistry;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.Library;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorTE;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorBlock;
import com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE;
import com.leafia.contents.machines.reactors.lftr.processing.separator.container.SaltSeparatorMenu;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipe;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipes;
import com.leafia.dev.LeafiaUtil;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.init.recipes.LeafiaRecipeInjector;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The salt fuel separation plant (LFTR-2, D-3015): its two recipes and their file, the handler that
 * carries them, the multiblock and its proxies, the ports, the menu, and the machine's tick - the tank
 * it fills, the isotope it pulls out of a passing salt, the cycle it runs, and every quirk of upstream's
 * that this port keeps.
 *
 * <p>Most tests set a state and call the machine's own code in the same body, so no tick runs between
 * (rule 10). Two use the world's ticks and say what they assert: a relationship the passage of time does
 * not change (power spent is the recipe's power times the ticks it ran; the progress is those ticks over
 * the duration), opened through {@link TickWindow}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSaltSeparatorTests {

	private static final String EMPTY16 = "empty16";
	private static final double EPS = 1e-9;
	private static final BlockPos CORE = new BlockPos(8, 1, 8);
	/** A second core, clear of the first's footprint. */
	private static final BlockPos OTHER = new BlockPos(3, 1, 3);

	private static boolean on() {
		return LeafiaFeatures.lftrReactor() && LeafiaFF.enabled() && LeafiaFluids.FLUORIDE != null;
	}

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static float yawFor(Direction looking) {
		return switch(looking) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> 270F;
			default -> throw new IllegalArgumentException(looking.toString());
		};
	}

	/** Places a separator the way a player does: the block in the clicked cell, rebuilt around a core one cell back along {@code dir}. */
	private static SaltSeparatorTE place(GameTestHelper helper, BlockPos core, Direction dir) {
		ServerPlayer player = mockPlayer(helper);
		Block block = LeafiaBlocks.salt_separator.get();
		BlockPos at = core.relative(dir, 1);
		player.setYRot(yawFor(dir.getOpposite()));
		player.setXRot(0F);
		helper.setBlock(at, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), block.defaultBlockState(), player, new ItemStack(block));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(be instanceof SaltSeparatorTE, "no separator core at " + core + ": " + be);
		return (SaltSeparatorTE) be;
	}

	private static SaltSeparatorTE lone(GameTestHelper helper) {
		return place(helper, CORE, Direction.NORTH);
	}

	private static SaltSeparatorRecipe recipe(String name) {
		return SaltSeparatorRecipes.INSTANCE.recipeNameMap.get(name);
	}

	/** Salt of {@code mB} carrying {@code amount} of one isotope per bucket. */
	private static FluidStack salt(int mB, String fuel, double amount) {
		return LeafiaMSRTests.salt(mB, 0, fuel, amount);
	}

	private static Map<String, Double> mixtureOf(FluidStack stack) {
		return MSRTEBase.readMixture(MSRTEBase.nbtProtocol(stack));
	}

	/**
	 * A separator ready to run {@code name}: the recipe chosen, the salt tank full of the recipe's salt
	 * with exactly the recipe's mixture, fluorine in, power in, and every NTM tank already the type the
	 * recipe will conform it to (retyping a tank empties it).
	 */
	private static SaltSeparatorTE ready(GameTestHelper helper, String name) {
		return ready(helper, name, CORE);
	}

	private static SaltSeparatorTE ready(GameTestHelper helper, String name, BlockPos core) {
		SaltSeparatorTE te = place(helper, core, Direction.NORTH);
		SaltSeparatorRecipe r = recipe(name);
		te.module.setRecipe(name, false);
		te.saltType = r.saltType;
		te.saltTank.setCapacity(r.saltAmount);
		MSRFuel fuel = r.mixture.keySet().iterator().next();
		te.saltTank.setFluid(salt(r.saltAmount, fuel.name(), r.mixture.get(fuel)));
		te.inputTanks[0].setTankType(r.inputFluid[0].type);
		te.inputTanks[0].setFill(r.inputFluid[0].fill);
		te.outputTanks[0].setTankType(r.outputFluid[0].type);
		te.outputTanks[1].setTankType(r.outputFluid[1].type);
		te.power = 100_000;
		return te;
	}

	private static String problems(List<String> list) {
		return list.size() + " problem(s): " + String.join("; ", list);
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= EPS * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	// ------------------------------------------------------------------ the recipes

	/** Both rows: every number upstream's, read through the table the machine itself reads. */
	@GameTest(template = EMPTY16)
	public static void theRecipeTableIsUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorRecipes set = SaltSeparatorRecipes.INSTANCE;
		List<String> problems = new ArrayList<>();
		if(set.inputItemLimit() != 0 || set.inputFluidLimit() != 2 || set.outputItemLimit() != 0 || set.outputFluidLimit() != 3)
			problems.add("limits " + set.inputItemLimit() + "/" + set.inputFluidLimit() + "/" + set.outputItemLimit() + "/" + set.outputFluidLimit());
		if(!"leafiaSaltSeparator.json".equals(set.getFileName())) problems.add("file " + set.getFileName());

		Object[][] rows = {
				{ "separator.u233", MSRFuel.u233, LeafiaFluids.UF6_233 },
				{ "separator.u235", MSRFuel.u235, LeafiaFluids.UF6_235 },
		};
		for(Object[] row : rows) {
			String name = (String) row[0];
			SaltSeparatorRecipe r = recipe(name);
			if(r == null) { problems.add(name + " is missing"); continue; }
			if(r.duration != 60) problems.add(name + " duration " + r.duration);
			if(r.power != 400) problems.add(name + " power " + r.power);
			if(r.saltType != LeafiaFluids.FLUORIDE) problems.add(name + " salt " + r.saltType.getName());
			if(r.saltAmount != 1000) problems.add(name + " salt amount " + r.saltAmount);
			if(!r.mixture.equals(Map.of((MSRFuel) row[1], 3.0))) problems.add(name + " mixture " + r.mixture);
			if(r.inputItem != null || r.outputItem != null) problems.add(name + " has solid slots");
			if(r.inputFluid == null || r.inputFluid.length != 1 || r.inputFluid[0].type != LeafiaFluids.FLUORINE
					|| r.inputFluid[0].fill != 500 || r.inputFluid[0].pressure != 0)
				problems.add(name + " input fluids");
			FluidType uf6 = (FluidType) row[2];
			if(r.outputFluid == null || r.outputFluid.length != 2 || r.outputFluid[0].type != LeafiaFluids.FLUORIDE
					|| r.outputFluid[0].fill != 1000 || r.outputFluid[1].type != uf6 || r.outputFluid[1].fill != 300)
				problems.add(name + " output fluids");
			if(!r.customLocalization) problems.add(name + " is not named by a lang key");
			if(ItemFluidIcon.getFluidType(r.getIcon()) != uf6) problems.add(name + " icon is " + ItemFluidIcon.getFluidType(r.getIcon()).getName());
			if(r.isPooled()) problems.add(name + " is pooled");
		}
		int a = set.recipeOrderedList.indexOf(recipe("separator.u233")), b = set.recipeOrderedList.indexOf(recipe("separator.u235"));
		if(a < 0 || b != a + 1) problems.add("order " + a + ", " + b);
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** The rows go out to their file and come back the same: the salt, the amount and the mixture ride the extra data. */
	@GameTest(template = EMPTY16)
	public static void theRowsRoundTripThroughTheirFile(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorRecipes src = SaltSeparatorRecipes.INSTANCE;
		java.util.Map<String, GenericRecipe> global = new java.util.HashMap<>(GenericRecipes.nameToRecipeGlobal);
		try {
			StringWriter text = new StringWriter();
			try(JsonWriter writer = new JsonWriter(text)) {
				writer.setIndent("  ");
				writer.beginObject();
				writer.name("recipes").beginArray();
				for(SaltSeparatorRecipe r : src.recipeOrderedList) {
					writer.beginObject();
					src.writeRecipe(r, writer);
					writer.endObject();
				}
				writer.endArray();
				writer.endObject();
			} catch(java.io.IOException e) {
				throw new IllegalStateException(e);
			}

			JsonObject first = JsonParser.parseString(text.toString()).getAsJsonObject().getAsJsonArray("recipes").get(0).getAsJsonObject();
			List<String> problems = new ArrayList<>();
			if(!first.get("saltType").getAsString().equals(LeafiaFluids.FLUORIDE.getName())) problems.add("saltType " + first.get("saltType"));
			if(first.get("saltAmount").getAsInt() != 1000) problems.add("saltAmount " + first.get("saltAmount"));
			JsonArray mix = first.getAsJsonArray("mixture");
			if(mix.size() != 1 || !mix.get(0).getAsJsonArray().get(0).getAsString().equals("u233")
					|| mix.get(0).getAsJsonArray().get(1).getAsDouble() != 3.0)
				problems.add("mixture " + mix);

			SaltSeparatorRecipes copy = new SaltSeparatorRecipes();
			copy.readRecipeStream(new StringReader(text.toString()));
			if(copy.recipeOrderedList.size() != src.recipeOrderedList.size()) problems.add("read " + copy.recipeOrderedList.size() + " rows");
			for(SaltSeparatorRecipe r : src.recipeOrderedList) {
				SaltSeparatorRecipe c = copy.recipeNameMap.get(r.getInternalName());
				if(c == null) { problems.add(r.getInternalName() + " not read back"); continue; }
				if(c.duration != r.duration || c.power != r.power || c.saltType != r.saltType || c.saltAmount != r.saltAmount
						|| !c.mixture.equals(r.mixture) || !c.customLocalization)
					problems.add(r.getInternalName() + " came back different");
				if(c.inputFluid.length != r.inputFluid.length || c.inputFluid[0].type != r.inputFluid[0].type || c.inputFluid[0].fill != r.inputFluid[0].fill)
					problems.add(r.getInternalName() + " input fluids came back different");
				if(c.outputFluid.length != r.outputFluid.length || c.outputFluid[1].type != r.outputFluid[1].type || c.outputFluid[1].fill != r.outputFluid[1].fill)
					problems.add(r.getInternalName() + " output fluids came back different");
			}
			helper.assertTrue(problems.isEmpty(), problems(problems));
		} finally {
			GenericRecipes.nameToRecipeGlobal.clear();
			GenericRecipes.nameToRecipeGlobal.putAll(global);
		}
		helper.succeed();
	}

	/**
	 * Upstream's mixin adds the table to the handler list at the tail of {@code registerAllHandlers}; here
	 * the injector does, ahead of its own slot, once, and only while the reactor's switch is on.
	 */
	@GameTest(template = EMPTY16)
	public static void theHandlerIsListedAheadOfTheInjectorAndOnlyWhenTheReactorIs(GameTestHelper helper) {
		List<SerializableRecipe> handlers = SerializableRecipe.recipeHandlers;
		int count = 0;
		for(SerializableRecipe handler : handlers) if(handler == SaltSeparatorRecipes.INSTANCE) count++;
		helper.assertTrue(count == (LeafiaFeatures.lftrReactor() ? 1 : 0), "the table is in the list " + count + " time(s), gate " + LeafiaFeatures.lftrReactor());
		if(LeafiaFeatures.lftrReactor()) {
			int at = handlers.indexOf(SaltSeparatorRecipes.INSTANCE);
			int injector = handlers.indexOf(LeafiaRecipeInjector.INSTANCE);
			helper.assertTrue(injector == handlers.size() - 1, "the injector no longer owns the last slot: " + injector + " of " + handlers.size());
			helper.assertTrue(at >= 0 && at < injector, "the table is at " + at + ", the injector at " + injector);
			int size = handlers.size();
			LeafiaRecipeInjector.addHandlers();
			helper.assertTrue(handlers.size() == size && handlers.indexOf(SaltSeparatorRecipes.INSTANCE) == at, "adding twice changed the list");
			helper.assertTrue(SaltSeparatorRecipes.INSTANCE.recipeOrderedList.size() >= 2, "the table is empty after adding twice");

			// With the injector already in the list (every call after the first pass), a handler that is
			// missing goes in ahead of it and is brought up with its defaults.
			handlers.remove(SaltSeparatorRecipes.INSTANCE);
			SaltSeparatorRecipes.INSTANCE.deleteRecipes();
			LeafiaRecipeInjector.addHandlers();
			int again = handlers.indexOf(SaltSeparatorRecipes.INSTANCE);
			helper.assertTrue(handlers.size() == size && again >= 0 && again < handlers.indexOf(LeafiaRecipeInjector.INSTANCE)
					&& handlers.indexOf(LeafiaRecipeInjector.INSTANCE) == handlers.size() - 1,
					"a handler added late is at " + again + " of " + handlers.size() + ", the injector at " + handlers.indexOf(LeafiaRecipeInjector.INSTANCE));
			helper.assertTrue(SaltSeparatorRecipes.INSTANCE.recipeNameMap.containsKey("separator.u233")
					&& SaltSeparatorRecipes.INSTANCE.recipeNameMap.containsKey("separator.u235"), "a handler added late was not brought up with its defaults");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the block

	/** The shape upstream's dimensions and eight extras make, with the right block entity on each part. */
	@GameTest(template = EMPTY16)
	public static void aPlacedSeparatorHasUpstreamsShapeAndProxies(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorBlock block = (SaltSeparatorBlock) LeafiaBlocks.salt_separator.get();
		helper.assertTrue(java.util.Arrays.equals(block.getDimensions(), new int[] { 5, 0, 1, 1, 1, 1 }) && block.getOffset() == 1,
				"the dimensions are " + java.util.Arrays.toString(block.getDimensions()) + " offset " + block.getOffset());
		for(Direction dir : new Direction[] { Direction.NORTH, Direction.EAST }) {
			BlockPos core = dir == Direction.NORTH ? CORE : OTHER;
			SaltSeparatorTE te = place(helper, core, dir);
			List<String> problems = new ArrayList<>();
			int cores = 0, extras = 0, dummies = 0;
			for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) for(int dy = 0; dy <= 5; dy++) {
				BlockPos p = core.offset(dx, dy, dz);
				BlockState state = helper.getBlockState(p);
				String where = dx + "," + dy + "," + dz;
				if(state.getBlock() != block) { problems.add(where + " is " + state.getBlock()); continue; }
				BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(p));
				boolean ring = dy == 0 && (dx != 0 || dz != 0);
				if(dx == 0 && dy == 0 && dz == 0) {
					cores++;
					if(state.getValue(BlockDummyable.PART) != Part.CORE || state.getValue(BlockDummyable.DIR) != dir) problems.add("core is " + state);
					if(be != te) problems.add("the core's block entity is " + be);
				} else if(ring) {
					extras++;
					if(state.getValue(BlockDummyable.PART) != Part.EXTRA) problems.add(where + " is not an extra");
					if(!(be instanceof BlockEntityProxyCombo proxy) || proxy.getType() != LeafiaBlockEntities.SALT_SEPARATOR_DUMMY.get())
						problems.add(where + " is not the separator's proxy: " + be);
				} else {
					dummies++;
					if(state.getValue(BlockDummyable.PART) != Part.DUMMY) problems.add(where + " is not a dummy");
					if(be != null) problems.add(where + " has a block entity " + be);
				}
			}
			if(cores != 1 || extras != 8 || dummies != 45) problems.add("counts " + cores + "/" + extras + "/" + dummies);
			for(BlockPos out : List.of(core.offset(2, 0, 0), core.offset(0, 0, -2), core.above(6)))
				if(helper.getBlockState(out).getBlock() == block) problems.add(out + " is outside the shape and is the block");
			helper.assertTrue(problems.isEmpty(), dir + ": " + problems(problems));
		}
		helper.succeed();
	}

	/** Upstream's strengths (5 and 20, 12 here by D-012), step sound and blast: the numbers the block reads back. */
	@GameTest(template = EMPTY16)
	public static void theBlockIsStrongAsUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		BlockState state = helper.getBlockState(CORE);
		near(helper, state.getDestroySpeed(helper.getLevel(), helper.absolutePos(CORE)), 5.0, "hardness");
		near(helper, state.getBlock().getExplosionResistance(), 12.0, "blast resistance");
		helper.assertTrue(!state.requiresCorrectToolForDrops(), "upstream asks for no tool (D-1072)");
		helper.assertTrue(te.getName().equals("tile.salt_separator.name"), "the block entity's name key is " + te.getName());
		helper.succeed();
	}

	/**
	 * The proxies forward the fluid handler (and power, and the slots) to the core, so a Forge duct or a
	 * pipe beside any of the eight flank cells meets the machine; no other part of it answers.
	 */
	@GameTest(template = EMPTY16)
	public static void theProxiesForwardTheCoreAndNothingElseDoes(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.power = 1234;
		List<String> problems = new ArrayList<>();
		var level = helper.getLevel();
		Direction[] dirs = { null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
		for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) {
			BlockPos p = CORE.offset(dx, 0, dz);
			boolean isCore = dx == 0 && dz == 0;
			for(Direction d : dirs) {
				IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(p), d);
				if(handler != te) problems.add(dx + "," + dz + " side " + d + " answers " + handler);
			}
			if(!isCore) {
				BlockEntityProxyCombo proxy = (BlockEntityProxyCombo) level.getBlockEntity(helper.absolutePos(p));
				if(proxy.getPower() != 1234) problems.add(dx + "," + dz + " forwards power " + proxy.getPower());
				if(proxy.getContainerSize() != SaltSeparatorTE.SLOTS) problems.add(dx + "," + dz + " forwards " + proxy.getContainerSize() + " slots");
				if(!proxy.canConnect(Fluids.WATER, Direction.EAST)) problems.add(dx + "," + dz + " does not forward the NTM fluid connection");
			}
			if(!LeafiaUtil.canConnectFF(level, helper.absolutePos(p), LeafiaFluids.FLUORIDE, Direction.EAST))
				problems.add(dx + "," + dz + " is not a Forge duct end");
		}
		for(BlockPos dummy : List.of(CORE.above(), CORE.offset(1, 3, 1), CORE.above(5))) {
			if(level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(dummy), null) != null) problems.add(dummy + " answers a fluid handler");
			if(LeafiaUtil.canConnectFF(level, helper.absolutePos(dummy), LeafiaFluids.FLUORIDE, Direction.EAST)) problems.add(dummy + " is a Forge duct end");
		}
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** Upstream's twelve ports, exactly: eight beside the flank corners, four at the middles, each facing outward. */
	@GameTest(template = EMPTY16)
	public static void thePortsAreUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		BlockPos at = te.getBlockPos();
		// (dx, dz, direction): upstream's getConPos in its order, then getConPos2.
		Object[][] corners = {
				{ 2, 1, Library.POS_X }, { 2, -1, Library.POS_X }, { -2, 1, Library.NEG_X }, { -2, -1, Library.NEG_X },
				{ 1, 2, Library.POS_Z }, { -1, 2, Library.POS_Z }, { 1, -2, Library.NEG_Z }, { -1, -2, Library.NEG_Z } };
		Object[][] middles = { { 2, 0, Library.POS_X }, { -2, 0, Library.NEG_X }, { 0, 2, Library.POS_Z }, { 0, -2, Library.NEG_Z } };
		List<String> problems = new ArrayList<>();
		DirPos[] cons = te.getConPos();
		if(cons.length != 8) problems.add(cons.length + " ports");
		else for(int i = 0; i < 8; i++) {
			BlockPos want = at.offset((int) corners[i][0], 0, (int) corners[i][1]);
			if(!cons[i].equals(want) || cons[i].getDir() != corners[i][2]) problems.add("port " + i + " is " + cons[i] + " " + cons[i].getDir());
		}
		DirPos[] mid = te.getConPos2();
		if(mid.length != 4) problems.add(mid.length + " middle ports");
		else for(int i = 0; i < 4; i++) {
			BlockPos want = at.offset((int) middles[i][0], 0, (int) middles[i][1]);
			if(!mid[i].equals(want) || mid[i].getDir() != middles[i][2]) problems.add("middle port " + i + " is " + mid[i] + " " + mid[i].getDir());
		}
		helper.assertTrue(Library.POS_X == Direction.EAST && Library.NEG_X == Direction.WEST && Library.POS_Z == Direction.SOUTH && Library.NEG_Z == Direction.NORTH,
				"the library's axes are not what the ports assume");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	// ------------------------------------------------------------------ a fresh machine

	@GameTest(template = EMPTY16)
	public static void aFreshSeparatorHasUpstreamsSizesAndTheForgeHandlerOfItsBuffers(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		List<String> problems = new ArrayList<>();
		if(te.getContainerSize() != 14) problems.add("slots " + te.getContainerSize());
		if(te.maxPower != 100_000 || te.power != 0) problems.add("power " + te.power + "/" + te.maxPower);
		for(var t : te.inputTanks) if(t.getMaxFill() != 12000 || t.getTankType() != Fluids.NONE) problems.add("input tank " + t.getMaxFill());
		for(var t : te.outputTanks) if(t.getMaxFill() != 12000 || t.getTankType() != Fluids.NONE) problems.add("output tank " + t.getMaxFill());
		if(te.concIn.getMaxFill() != 12000 || te.concOut.getMaxFill() != 12000) problems.add("concentrate tanks");
		if(te.saltTank.getCapacity() != 12000 || te.bufferIn.getCapacity() != 12000 || te.bufferOut.getCapacity() != 12000) problems.add("forge tanks");
		if(te.saltType != Fluids.NONE || te.didProcess) problems.add("state");
		if(te.module.getRecipe() != null || !"null".equals(te.module.getRecipeName())) problems.add("a recipe is chosen: " + te.module.getRecipeName());
		if(te.getReceivingTanks().length != 3 || te.getReceivingTanks()[2] != te.concIn) problems.add("receiving tanks");
		if(te.getSendingTanks().length != 3) problems.add("sending tanks");
		if(te.getAllTanks().length != 5) problems.add("all tanks");

		// The handler: fill goes into the input buffer, drain comes from the output buffer, and no tanks are listed.
		FluidStack flibe = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 300);
		if(te.fill(flibe, IFluidHandler.FluidAction.EXECUTE) != 300 || te.bufferIn.getFluidAmount() != 300) problems.add("fill did not reach the input buffer");
		if(!te.drain(10, IFluidHandler.FluidAction.EXECUTE).isEmpty()) problems.add("drained from an empty output buffer");
		te.bufferOut.setFluid(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 500));
		if(te.drain(200, IFluidHandler.FluidAction.EXECUTE).getAmount() != 200 || te.bufferOut.getFluidAmount() != 300) problems.add("drain did not come from the output buffer");
		if(te.drain(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 100), IFluidHandler.FluidAction.EXECUTE).getAmount() != 100) problems.add("drain by stack");
		if(te.getTanks() != 0 || te.getTankCapacity(0) != 0 || !te.getFluidInTank(0).isEmpty() || te.isFluidValid(0, flibe)) problems.add("upstream listed no tank properties");
		if(te.getSendingTank(flibe) != te.bufferOut || te.getCorrespondingTank(flibe) != te.bufferIn) problems.add("the FF tanks are swapped");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the menu

	/** The slots, at upstream's coordinates, and what each takes. */
	@GameTest(template = EMPTY16)
	public static void theMenuIsUpstreamsLayout(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		ServerPlayer player = mockPlayer(helper);
		SaltSeparatorMenu menu = new SaltSeparatorMenu(1, player.getInventory(), te);
		List<String> problems = new ArrayList<>();
		int[][] at = { { 152, 81 }, { 35, 126 }, { 152, 108 }, { 152, 126 }, { 26, 54 }, { 44, 54 }, { 26, 72 }, { 44, 72 },
				{ 80, 54 }, { 98, 54 }, { 116, 54 }, { 80, 72 }, { 98, 72 }, { 116, 72 } };
		if(menu.slots.size() != 14 + 36) problems.add(menu.slots.size() + " slots");
		for(int i = 0; i < Math.min(14, menu.slots.size()); i++) {
			Slot s = menu.getSlot(i);
			if(s.x != at[i][0] || s.y != at[i][1]) problems.add("slot " + i + " at " + s.x + "," + s.y);
			if(s.getContainerSlot() != i || s.container != te) problems.add("slot " + i + " is container slot " + s.getContainerSlot());
			boolean takeOnly = i >= 6 && i <= 7 || i >= 11;
			if(s.mayPlace(new ItemStack(Items.DIRT)) == takeOnly) problems.add("slot " + i + (takeOnly ? " takes items" : " refuses items"));
		}
		if(menu.getSlot(14).x != 8 || menu.getSlot(14).y != 174) problems.add("the inventory starts at " + menu.getSlot(14).x + "," + menu.getSlot(14).y);
		if(menu.getSlot(14 + 27).x != 8 || menu.getSlot(14 + 27).y != 174 + 58) problems.add("the hotbar is at " + menu.getSlot(14 + 27).y);
		helper.assertTrue(menu.getType() == LeafiaMenuTypes.SALT_SEPARATOR.get(), "wrong menu type");
		helper.assertTrue(menu.separator == te, "the menu does not know its machine");
		Vec3 here = Vec3.atCenterOf(helper.absolutePos(CORE));
		player.setPos(here.x, here.y, here.z);
		helper.assertTrue(menu.stillValid(player), "a player beside the machine cannot use it");
		player.setPos(here.x + 100, here.y, here.z);
		helper.assertTrue(!menu.stillValid(player), "a player a hundred blocks away can");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** Shift-click: a battery, a blueprint, an upgrade, a full container, an empty one, and something that goes nowhere. */
	@GameTest(template = EMPTY16)
	public static void shiftClickSortsByWhatTheItemIs(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		ServerPlayer player = mockPlayer(helper);
		SaltSeparatorMenu menu = new SaltSeparatorMenu(1, player.getInventory(), te);
		List<String> problems = new ArrayList<>();

		// A container the registry knows and NeoForge does not: the machine has to recognise it by the NTM registry alone.
		FluidContainer ntm = null;
		for(FluidContainer c : FluidContainerRegistry.allContainers)
			if(!c.emptyContainer.isEmpty() && !c.fullContainer.isEmpty() && LeafiaFF.itemHandler(c.fullContainer) == null && LeafiaFF.itemHandler(c.emptyContainer) == null) { ntm = c; break; }
		helper.assertTrue(ntm != null, "the base registry lists no fluid container that NeoForge does not also know");

		// (what, the item, the first slot it may land in, the last)
		Object[][] cases = {
				{ "battery", new ItemStack(ModItems.battery_creative.get()), 0, 0 },
				{ "blueprint", new ItemStack(ModItems.blueprints.get()), 1, 1 },
				{ "upgrade", new ItemStack(ModItems.upgrade_speed_1.get()), 2, 3 },
				{ "a full NTM container", ntm.fullContainer.copy(), 4, 5 },
				{ "an empty NTM container", ntm.emptyContainer.copy(), 8, 10 },
				{ "a full Forge bucket", new ItemStack(Items.WATER_BUCKET), 4, 5 },
				{ "an empty Forge bucket", new ItemStack(Items.BUCKET), 8, 10 },
		};
		for(Object[] c : cases) {
			for(int i = 0; i < te.slots.length; i++) te.slots[i] = ItemStack.EMPTY;
			player.getInventory().clearContent();
			player.getInventory().setItem(0, ((ItemStack) c[1]).copy());
			menu.quickMoveStack(player, 14 + 27); // the first hotbar slot
			int found = -1;
			for(int i = 0; i < 14; i++) if(!te.slots[i].isEmpty()) found = i;
			if(found < (int) c[2] || found > (int) c[3]) problems.add(c[0] + " went to " + found + ", not " + c[2] + " to " + c[3]);
		}
		// Dirt goes nowhere and stays put.
		for(int i = 0; i < te.slots.length; i++) te.slots[i] = ItemStack.EMPTY;
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
		ItemStack moved = menu.quickMoveStack(player, 14 + 27);
		if(!moved.isEmpty() || player.getInventory().getItem(0).getCount() != 5) problems.add("dirt moved");
		for(ItemStack s : te.slots) if(!s.isEmpty()) problems.add("dirt landed in the machine");

		// Out of the machine, from a take-only slot, into the inventory.
		player.getInventory().clearContent();
		te.slots[6] = new ItemStack(Items.GLASS_BOTTLE, 3);
		menu.quickMoveStack(player, 6);
		int inInventory = 0;
		for(int i = 0; i < player.getInventory().getContainerSize(); i++) inInventory += player.getInventory().getItem(i).getCount();
		if(!te.slots[6].isEmpty() || inInventory != 3) problems.add("a take-only slot did not empty into the inventory (" + inInventory + ")");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** Upstream's {@code standardOpenBehavior}: a click on any block of the machine opens the core's screen, unless sneaking. */
	@GameTest(template = EMPTY16)
	public static void aClickOnAnyPartOpensTheCoresScreenUnlessSneaking(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		lone(helper);
		List<String> problems = new ArrayList<>();
		for(BlockPos p : List.of(CORE, CORE.offset(1, 0, 1), CORE.offset(0, 3, 1), CORE.above(5))) {
			ServerPlayer player = mockPlayer(helper);
			BlockPos abs = helper.absolutePos(p);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
			BlockState state = helper.getLevel().getBlockState(abs);
			if(!reachedSending(() -> state.useWithoutItem(helper.getLevel(), player, hit))) problems.add("a click on " + p + " did not open the screen");
			player.setShiftKeyDown(true);
			InteractionResult[] result = new InteractionResult[1];
			if(reachedSending(() -> result[0] = state.useWithoutItem(helper.getLevel(), player, hit))) problems.add("a sneaking click on " + p + " opened it");
			if(result[0] == null || !result[0].consumesAction()) problems.add("a sneaking click on " + p + " was not consumed: " + result[0]);
		}
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** The mock connection refuses the screen payload, which is as far as a server test can follow the opener. */
	private static boolean reachedSending(Runnable call) {
		try {
			call.run();
			return false;
		} catch(UnsupportedOperationException e) {
			return e.getMessage() != null && e.getMessage().contains("neoforge:advanced_open_screen");
		}
	}

	/** The recipe selector's control message, and who may send it. */
	@GameTest(template = EMPTY16)
	public static void theControlSelectsARecipeForAPlayerInReach(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		IControlReceiver receiver = te;
		CompoundTag data = new CompoundTag();
		data.putInt("index", 0);
		data.putString("selection", "separator.u235");
		receiver.receiveControl(data);
		helper.assertTrue("separator.u235".equals(te.module.getRecipeName()), "the recipe is " + te.module.getRecipeName());
		helper.assertTrue(te.module.getRecipe() == recipe("separator.u235"), "the module does not read the table");
		data.putInt("index", 1);
		data.putString("selection", "separator.u233");
		receiver.receiveControl(data);
		helper.assertTrue("separator.u235".equals(te.module.getRecipeName()), "an index other than 0 changed the recipe");
		receiver.receiveControl(new CompoundTag());
		helper.assertTrue("separator.u235".equals(te.module.getRecipeName()), "an empty message changed the recipe");

		ServerPlayer player = mockPlayer(helper);
		Vec3 here = Vec3.atCenterOf(helper.absolutePos(CORE));
		player.setPos(here.x, here.y, here.z);
		helper.assertTrue(receiver.hasPermission(player), "a player beside it has no permission");
		player.setPos(here.x + 100, here.y, here.z);
		helper.assertTrue(!receiver.hasPermission(player), "a player a hundred blocks off has permission");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the tick

	/**
	 * A chosen recipe resizes the salt tank to the recipe's amount (draining the excess) and types the
	 * concentrate tanks; with none chosen the tanks are left alone.
	 */
	@GameTest(template = EMPTY16)
	public static void theRecipeSizesTheSaltTankAndTypesTheConcentrate(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.saltTank.setFluid(salt(5000, "u233", 1));
		te.updateEntity();
		helper.assertTrue(te.saltTank.getCapacity() == 12000 && te.saltTank.getFluidAmount() == 5000 && te.saltType == Fluids.NONE && te.concIn.getTankType() == Fluids.NONE,
				"a separator with no recipe changed: " + te.saltTank.getCapacity() + "/" + te.saltTank.getFluidAmount());
		te.module.setRecipe("separator.u233", false);
		te.updateEntity();
		helper.assertTrue(te.saltType == LeafiaFluids.FLUORIDE, "the salt type is " + te.saltType.getName());
		helper.assertTrue(te.concIn.getTankType() == LeafiaFluids.FLUORIDE && te.concOut.getTankType() == LeafiaFluids.FLUORIDE, "the concentrate tanks are not typed");
		helper.assertTrue(te.saltTank.getCapacity() == 1000, "the capacity is " + te.saltTank.getCapacity());
		helper.assertTrue(te.saltTank.getFluidAmount() == 1000, "the salt tank holds " + te.saltTank.getFluidAmount() + " after the excess was drained");
		// The tanks the recipe names are conformed, the third output is reset.
		helper.assertTrue(te.inputTanks[0].getTankType() == LeafiaFluids.FLUORINE && te.inputTanks[1].getTankType() == Fluids.NONE, "input tanks");
		helper.assertTrue(te.outputTanks[0].getTankType() == LeafiaFluids.FLUORIDE && te.outputTanks[1].getTankType() == LeafiaFluids.UF6_233
				&& te.outputTanks[2].getTankType() == Fluids.NONE, "output tanks");
		helper.succeed();
	}

	/** The concentrate tops the salt tank up, a tick at a time, by as much as the tank lacks. */
	@GameTest(template = EMPTY16)
	public static void theConcentrateFillsTheSaltTank(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.module.setRecipe("separator.u233", false);
		te.updateEntity(); // sizes the tank and types the concentrate
		te.concIn.setFill(600);
		te.updateEntity();
		helper.assertTrue(te.saltTank.getFluidAmount() == 600 && te.concIn.getFill() == 0, "600 mB: salt " + te.saltTank.getFluidAmount() + ", concentrate " + te.concIn.getFill());
		helper.assertTrue(LeafiaFF.fromFF(te.saltTank.getFluid()) == LeafiaFluids.FLUORIDE, "the salt is " + LeafiaFF.fromFF(te.saltTank.getFluid()).getName());
		te.concIn.setFill(1000);
		te.updateEntity();
		helper.assertTrue(te.saltTank.getFluidAmount() == 1000 && te.concIn.getFill() == 600, "1000 mB: salt " + te.saltTank.getFluidAmount() + ", concentrate " + te.concIn.getFill());
		te.updateEntity();
		helper.assertTrue(te.saltTank.getFluidAmount() == 1000 && te.concIn.getFill() == 600, "a full tank took more");
		helper.succeed();
	}

	/**
	 * The extraction, against its own formula: with the tank full, the passing salt gives
	 * {@code min(its amount, shortfall / ratio)} of the isotope and the tank receives that times the ratio.
	 */
	@GameTest(template = EMPTY16)
	public static void theIsotopeMovesFromThePassingSaltIntoTheSaltTank(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		SaltSeparatorRecipe r = recipe("separator.u233");
		te.saltTank.setCapacity(1000);
		te.saltTank.setFluid(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1000));
		te.bufferIn.setFluid(salt(2000, "u233", 4));
		te.lastResortFuckingIdea(r.mixture);
		// ratio 2: the shortfall is 3, so 3 / 2 = 1.5 leaves the passing salt and 1.5 x 2 = 3 reaches the tank.
		near(helper, mixtureOf(te.bufferIn.getFluid()).get("u233"), 2.5, "what is left of the passing salt's isotope");
		near(helper, mixtureOf(te.saltTank.getFluid()).get("u233"), 3.0, "the salt tank's isotope");
		helper.assertTrue(te.bufferIn.getFluidAmount() == 2000 && te.saltTank.getFluidAmount() == 1000, "amounts changed");
		// Nothing more is wanted: the next call moves nothing.
		te.lastResortFuckingIdea(r.mixture);
		near(helper, mixtureOf(te.bufferIn.getFluid()).get("u233"), 2.5, "a satisfied tank took more");
		near(helper, mixtureOf(te.saltTank.getFluid()).get("u233"), 3.0, "a satisfied tank changed");

		// A passing salt short of the isotope gives all it has, and the shortfall remains.
		te.saltTank.setFluid(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1000));
		te.bufferIn.setFluid(salt(1000, "u233", 1));
		te.lastResortFuckingIdea(r.mixture);
		near(helper, mixtureOf(te.saltTank.getFluid()).get("u233"), 1.0, "a short salt's gift");
		near(helper, mixtureOf(te.bufferIn.getFluid()).get("u233"), 0.0, "a short salt's remainder");

		// An isotope the recipe does not want is left alone, and the passing salt gains the protocol's keys either way.
		te.saltTank.setFluid(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1000));
		te.bufferIn.setFluid(salt(1000, "th232", 5));
		te.lastResortFuckingIdea(r.mixture);
		near(helper, mixtureOf(te.bufferIn.getFluid()).get("th232"), 5.0, "an unwanted isotope");
		helper.assertTrue(mixtureOf(te.saltTank.getFluid()).getOrDefault("th232", 0d) == 0d, "an unwanted isotope reached the tank");
		helper.assertTrue(LeafiaFF.getTag(te.bufferIn.getFluid()).contains("heat") && LeafiaFF.getTag(te.saltTank.getFluid()).contains("heat"), "the tags lack the protocol's keys");

		// With either tank empty it does nothing at all.
		te.bufferIn.setFluid(FluidStack.EMPTY);
		te.lastResortFuckingIdea(r.mixture);
		helper.assertTrue(te.bufferIn.getFluid().isEmpty(), "an empty buffer was filled");
		helper.succeed();
	}

	/**
	 * Through the machine's tick: once the tank is full the passing salt's isotope moves into it, and
	 * the pass-through then sends the salt on (the tick is upstream's order: extraction first).
	 */
	@GameTest(template = EMPTY16)
	public static void aTickExtractsAndThenPassesTheSaltOn(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.module.setRecipe("separator.u233", false);
		te.updateEntity();
		te.concIn.setFill(1000);
		te.updateEntity(); // fills the tank to 1000
		te.bufferIn.setFluid(salt(1000, "u233", 5));
		te.updateEntity();
		near(helper, mixtureOf(te.saltTank.getFluid()).get("u233"), 3.0, "the salt tank's isotope after one tick");
		helper.assertTrue(te.bufferIn.getFluidAmount() == 0 && te.bufferOut.getFluidAmount() == 1000, "the salt was not passed on: " + te.bufferIn.getFluidAmount() + "/" + te.bufferOut.getFluidAmount());
		near(helper, mixtureOf(te.bufferOut.getFluid()).get("u233"), 2.0, "what the passing salt kept");
		helper.succeed();
	}

	/**
	 * Upstream's pass-through: everything in the input buffer goes to the output buffer each tick, only
	 * while a recipe is chosen, and what does not fit is destroyed - {@code fillFF} drains the sender by
	 * the amount it was asked for, not by what the receiver took.
	 */
	@GameTest(template = EMPTY16)
	public static void thePassThroughKeepsTheSaltsTagAndDestroysWhatDoesNotFit(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.bufferIn.setFluid(LeafiaMSRTests.salt(700, 1200, "th232", 0.5));
		te.updateEntity();
		helper.assertTrue(te.bufferIn.getFluidAmount() == 700 && te.bufferOut.isEmpty(), "salt moved with no recipe chosen");

		te.module.setRecipe("separator.u233", false);
		te.updateEntity();
		helper.assertTrue(te.bufferIn.isEmpty() && te.bufferOut.getFluidAmount() == 700, "700 mB did not pass: " + te.bufferIn.getFluidAmount() + "/" + te.bufferOut.getFluidAmount());
		near(helper, LeafiaFF.getTag(te.bufferOut.getFluid()).getDouble("heat"), 1200, "the salt's heat");
		near(helper, mixtureOf(te.bufferOut.getFluid()).get("th232"), 0.5, "the salt's mixture");

		// A nearly full output buffer: 500 asked, 100 fit, 400 are gone.
		te.bufferOut.setFluid(LeafiaMSRTests.salt(11_900, 1200, "th232", 0.5));
		te.bufferIn.setFluid(LeafiaMSRTests.salt(500, 1200, "th232", 0.5));
		te.updateEntity();
		helper.assertTrue(te.bufferIn.isEmpty(), "the input buffer kept " + te.bufferIn.getFluidAmount());
		helper.assertTrue(te.bufferOut.getFluidAmount() == 12_000, "the output buffer holds " + te.bufferOut.getFluidAmount() + ", 12000 fit");
		helper.succeed();
	}

	/** The mixture has to be exactly the recipe's: one isotope at exactly its amount, and nothing else. */
	@GameTest(template = EMPTY16)
	public static void theModuleWantsTheExactMixture(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		SaltSeparatorRecipe r = recipe("separator.u233");
		List<String> problems = new ArrayList<>();
		Object[][] mixtures = {
				{ "exact", Map.of("u233", 3.0), false },
				{ "a hair low", Map.of("u233", 2.9999999999999996), true },
				{ "a hair high", Map.of("u233", 3.0000000000000004), true },
				{ "an extra isotope", Map.of("u233", 3.0, "th232", 0.1), true },
				{ "the other isotope", Map.of("u235", 3.0), true },
				{ "a name that is no isotope", Map.of("u233", 3.0, "nonsense", 1.0), true },
				{ "empty", Map.<String, Double>of(), true },
		};
		for(Object[] m : mixtures) {
			@SuppressWarnings("unchecked")
			boolean got = te.module.isMixtureIncompatible(r, (Map<String, Double>) m[1]);
			if(got != (boolean) m[2]) problems.add(m[0] + " is " + (got ? "incompatible" : "compatible"));
		}

		// And through canProcess: exactly 3 runs, a hair off does not.
		helper.assertTrue(te.module.canProcess(r, 1, 1), "an exact salt cannot process");
		te.saltTank.setFluid(salt(1000, "u233", 2.9999999999999996));
		helper.assertTrue(!te.module.canProcess(r, 1, 1), "a hair-short salt processes");
		te.saltTank.setFluid(salt(999, "u233", 3));
		helper.assertTrue(!te.module.canProcess(r, 1, 1), "999 mB of salt processes");
		te.saltTank.setFluid(FluidStack.EMPTY);
		helper.assertTrue(!te.module.canProcess(r, 1, 1), "no salt processes");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** Salt of the wrong fluid is thrown away whole; salt of the right one is left; a vanished recipe no longer throws. */
	@GameTest(template = EMPTY16)
	public static void aWrongSaltIsThrownAwayAndAMissingRecipeIsSurvived(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		te.saltTank.setFluid(LeafiaFF.toFF(Fluids.WATER, 600));
		te.module.update(1, 1, true, ItemStack.EMPTY);
		helper.assertTrue(te.saltTank.getFluid().isEmpty(), "600 mB of water stayed in the salt tank: " + te.saltTank.getFluidAmount());

		te.saltTank.setFluid(salt(1000, "u233", 3));
		te.module.update(1, 1, true, ItemStack.EMPTY);
		helper.assertTrue(te.saltTank.getFluidAmount() > 0 || te.module.progress > 0, "the right salt was thrown away");
		helper.assertTrue(LeafiaFF.getTag(te.saltTank.getFluid()).contains("heat") || te.saltTank.getFluidAmount() == 0, "the right salt gained no protocol keys");

		// A recipe name the table does not have, with salt in the tank: upstream throws on every tick, this does nothing.
		SaltSeparatorTE other = place(helper, OTHER, Direction.NORTH);
		other.module.setRecipe("separator.nonsense", false);
		other.saltTank.setFluid(salt(500, "u233", 3));
		other.module.update(1, 1, true, ItemStack.EMPTY);
		other.updateEntity();
		helper.assertTrue(other.saltTank.getFluidAmount() == 500 && !other.didProcess && other.module.progress == 0, "a vanished recipe did something");
		helper.succeed();
	}

	/**
	 * A whole cycle, both rows: it needs as many ticks as the progress takes to reach 1 summing 1/60 in
	 * floating point (one more than 60 when the sum falls short), spends 400 HE on every one of them, and
	 * then turns the salt and the fluorine into the recipe's outputs.
	 */
	@GameTest(template = EMPTY16)
	public static void aFullCycleMakesUpstreamsOutputsOnUpstreamsPower(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		List<String> problems = new ArrayList<>();
		for(String name : List.of("separator.u233", "separator.u235")) {
			SaltSeparatorTE te = ready(helper, name, name.endsWith("233") ? CORE : OTHER);
			SaltSeparatorRecipe r = recipe(name);
			FluidType uf6 = r.outputFluid[1].type;

			// The tick count the arithmetic gives, derived rather than typed.
			double progress = 0;
			int expected = 0;
			while(progress < 1D) {
				progress += Math.min(1D / (double) r.duration, 1D);
				expected++;
			}

			int ticks = 0;
			while(ticks < 200 && te.outputTanks[0].getFill() == 0) {
				te.updateEntity();
				ticks++;
			}
			if(ticks != expected) problems.add(name + " took " + ticks + " ticks, the arithmetic says " + expected);
			if(100_000 - te.power != 400L * expected) problems.add(name + " spent " + (100_000 - te.power) + " HE, not " + 400L * expected);
			if(te.outputTanks[0].getTankType() != LeafiaFluids.FLUORIDE || te.outputTanks[0].getFill() != 1000) problems.add(name + " FLiBe out: " + te.outputTanks[0].getFill());
			if(te.outputTanks[1].getTankType() != uf6 || te.outputTanks[1].getFill() != 300) problems.add(name + " UF6 out: " + te.outputTanks[1].getFill() + " of " + te.outputTanks[1].getTankType().getName());
			if(te.inputTanks[0].getFill() != 0) problems.add(name + " fluorine left: " + te.inputTanks[0].getFill());
			if(te.saltTank.getFluidAmount() != 0) problems.add(name + " salt left: " + te.saltTank.getFluidAmount());
			if(te.module.progress != 0D) problems.add(name + " progress after the cycle " + te.module.progress);
			if(!te.didProcess) problems.add(name + " did not report processing on its last tick");
			te.updateEntity();
			if(te.didProcess) problems.add(name + " kept processing with nothing to process");
		}
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** Each of the five things a cycle needs, taken away alone: the machine idles and the progress falls to zero. */
	@GameTest(template = EMPTY16)
	public static void aCycleNeedsEveryInput(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		List<String> problems = new ArrayList<>();
		te.updateEntity();
		if(!te.didProcess || te.module.progress <= 0) problems.add("the baseline does not run");

		java.util.function.Consumer<String> idle = what -> {
			te.updateEntity();
			if(te.didProcess || te.module.progress != 0D) problems.add(what + ": didProcess " + te.didProcess + ", progress " + te.module.progress);
		};
		long power = te.power;
		te.power = 399; // below the recipe's power; the tick's own charge and cap do not lift it
		idle.accept("399 HE");
		te.power = power;
		te.updateEntity();
		int fluorine = te.inputTanks[0].getFill();
		te.inputTanks[0].setFill(499);
		idle.accept("499 mB of fluorine");
		te.inputTanks[0].setFill(fluorine);
		te.outputTanks[0].setFill(12000 - 999);
		idle.accept("1 mB short of room for the FLiBe");
		te.outputTanks[0].setFill(0);
		te.outputTanks[1].setFill(12000 - 299);
		idle.accept("1 mB short of room for the UF6");
		te.outputTanks[1].setFill(0);
		te.saltTank.getFluid().setAmount(999);
		idle.accept("999 mB of salt");
		te.saltTank.getFluid().setAmount(1000);
		te.updateEntity();
		if(!te.didProcess) problems.add("the baseline does not run again after the taking-away");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/**
	 * The two upgrade slots are inert: upstream's tick has the arithmetic and never reads the slots, so
	 * the progress per tick and the power per tick are the recipe's whatever sits in them.
	 */
	@GameTest(template = EMPTY16)
	public static void theUpgradeSlotsDoNothing(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		te.slots[2] = new ItemStack(ModItems.upgrade_speed_3.get());
		te.slots[3] = new ItemStack(ModItems.upgrade_power_2.get());
		te.updateEntity();
		near(helper, te.module.progress, 1D / 60D, "progress after one upgraded tick");
		helper.assertTrue(100_000 - te.power == 400, "an upgraded tick spent " + (100_000 - te.power) + " HE");
		te.slots[2] = new ItemStack(ModItems.upgrade_overdrive_3.get());
		te.updateEntity();
		near(helper, te.module.progress, 2D / 60D, "progress after an overdriven tick");
		helper.assertTrue(100_000 - te.power == 800, "two ticks spent " + (100_000 - te.power) + " HE");
		helper.succeed();
	}

	/** Upstream's three-statement {@code maxPower}: the recipe's power times 100, floored at 100,000, and never below the charge. */
	@GameTest(template = EMPTY16)
	public static void theMaxPowerFollowsUpstreamsSequence(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.updateEntity();
		helper.assertTrue(te.maxPower == 100_000, "no recipe: " + te.maxPower);
		te.module.setRecipe("separator.u233", false);
		te.updateEntity();
		helper.assertTrue(te.maxPower == 100_000, "400 x 100 = 40,000 is under the floor: " + te.maxPower);
		te.power = 250_000;
		te.updateEntity();
		helper.assertTrue(te.maxPower == 250_000, "a charge above the floor lifts the cap: " + te.maxPower);
		helper.succeed();
	}

	/** The decanting slots: a bottle of fluorine in slot 4 fills the first input tank, and an empty bottle in slot 8 fills with the first output. */
	@GameTest(template = EMPTY16)
	public static void theTankSlotsDecantBottles(GameTestHelper helper) {
		if(!on() || !LeafiaFeatures.fluidBottles || !com.leafia.unsorted.fluids.FluidLoaderBottle.installed()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		te.inputTanks[0].setFill(0);
		te.slots[4] = ItemNTMFBottle.make(LeafiaItems.ntmfbottle.get(), LeafiaFluids.FLUORINE);
		te.slots[8] = new ItemStack(Items.GLASS_BOTTLE);
		te.outputTanks[0].setFill(700);
		te.module.setRecipe("separator.u233", false);
		te.saltTank.setFluid(FluidStack.EMPTY); // so the cycle cannot start and change the fills
		te.updateEntity();
		helper.assertTrue(te.inputTanks[0].getFill() == 333, "the input tank holds " + te.inputTanks[0].getFill());
		helper.assertTrue(te.slots[4].isEmpty() && te.slots[6].is(Items.GLASS_BOTTLE), "the full bottle did not come out empty in slot 6: " + te.slots[4] + " / " + te.slots[6]);
		helper.assertTrue(te.outputTanks[0].getFill() == 700 - 333, "the output tank holds " + te.outputTanks[0].getFill());
		helper.assertTrue(te.slots[8].isEmpty() && te.slots[11].getItem() instanceof ItemNTMFBottle, "the empty bottle did not come out full in slot 11: " + te.slots[8] + " / " + te.slots[11]);
		helper.succeed();
	}

	// ------------------------------------------------------------------ persistence and sync

	@GameTest(template = EMPTY16)
	public static void aSeparatorSavesAndLoadsEverything(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u235");
		te.updateEntity();
		te.bufferIn.setFluid(LeafiaMSRTests.salt(321, 900, "th232", 0.25));
		te.bufferOut.setFluid(LeafiaMSRTests.salt(654, 100, "u233", 1.5));
		te.concIn.setTankType(LeafiaFluids.FLUORIDE);
		te.concIn.setFill(77);
		te.outputTanks[1].setFill(42);
		te.power = 31_337;
		te.maxPower = 123_456;
		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = te.saveWithoutMetadata(registries);

		SaltSeparatorTE loaded = new SaltSeparatorTE(LeafiaBlockEntities.SALT_SEPARATOR.get(), te.getBlockPos(), te.getBlockState());
		loaded.loadWithComponents(saved, registries);
		List<String> problems = new ArrayList<>();
		if(!"separator.u235".equals(loaded.module.getRecipeName())) problems.add("recipe " + loaded.module.getRecipeName());
		if(Math.abs(loaded.module.progress - te.module.progress) > EPS) problems.add("progress");
		if(loaded.power != 31_337 || loaded.maxPower != 123_456) problems.add("power " + loaded.power + "/" + loaded.maxPower);
		if(loaded.saltTank.getFluidAmount() != te.saltTank.getFluidAmount() || !mixtureOf(loaded.saltTank.getFluid()).equals(mixtureOf(te.saltTank.getFluid()))) problems.add("salt tank");
		if(loaded.bufferIn.getFluidAmount() != 321 || Math.abs(LeafiaFF.getTag(loaded.bufferIn.getFluid()).getDouble("heat") - 900) > EPS) problems.add("input buffer");
		if(loaded.bufferOut.getFluidAmount() != 654 || mixtureOf(loaded.bufferOut.getFluid()).get("u233") != 1.5) problems.add("output buffer");
		if(loaded.concIn.getFill() != 77 || loaded.concIn.getTankType() != LeafiaFluids.FLUORIDE) problems.add("concentrate");
		if(loaded.outputTanks[1].getFill() != 42 || loaded.outputTanks[1].getTankType() != te.outputTanks[1].getTankType()) problems.add("output tank");
		if(loaded.inputTanks[0].getTankType() != LeafiaFluids.FLUORINE || loaded.inputTanks[0].getFill() != te.inputTanks[0].getFill()) problems.add("input tank");

		// Upstream's guard: a saved maxPower of zero does not overwrite the default.
		saved.putLong("maxPower", 0);
		SaltSeparatorTE zero = new SaltSeparatorTE(LeafiaBlockEntities.SALT_SEPARATOR.get(), te.getBlockPos(), te.getBlockState());
		zero.loadWithComponents(saved, registries);
		if(zero.maxPower != 100_000) problems.add("a saved zero maxPower gave " + zero.maxPower);
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	/** The nine sync keys, fed to a second block entity as the client would get them. */
	@GameTest(template = EMPTY16)
	public static void theSyncKeysRebuildTheClientsCopy(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE server = ready(helper, "separator.u233");
		server.updateEntity();
		server.bufferOut.setFluid(LeafiaMSRTests.salt(250, 50, "u233", 2));
		server.concIn.setFill(88);
		server.concOut.setFill(99);
		SaltSeparatorTE client = new SaltSeparatorTE(LeafiaBlockEntities.SALT_SEPARATOR.get(), server.getBlockPos(), server.getBlockState());
		client.setLevel(helper.getLevel());
		client.onReceivePacketLocal((byte) 0, server.module.getRecipeName());
		client.onReceivePacketLocal((byte) 1, server.tankData(helper.getLevel().registryAccess()));
		client.onReceivePacketLocal((byte) 2, server.saltType.getID());
		client.onReceivePacketLocal((byte) 3, server.module.progress);
		client.onReceivePacketLocal((byte) 4, server.power);
		client.onReceivePacketLocal((byte) 5, server.didProcess);
		client.onReceivePacketLocal((byte) 6, server.concIn.getFill());
		client.onReceivePacketLocal((byte) 7, server.concOut.getFill());
		client.onReceivePacketLocal((byte) 8, server.saltTank.getCapacity());
		List<String> problems = new ArrayList<>();
		if(!"separator.u233".equals(client.module.getRecipeName())) problems.add("recipe");
		if(client.saltType != LeafiaFluids.FLUORIDE || client.concIn.getTankType() != LeafiaFluids.FLUORIDE) problems.add("salt type");
		if(client.module.progress != server.module.progress || client.power != server.power || client.didProcess != server.didProcess) problems.add("progress, power or the flag");
		if(client.concIn.getFill() != 88 || client.concOut.getFill() != 99) problems.add("concentrate fills");
		if(client.saltTank.getCapacity() != 1000 || client.saltTank.getFluidAmount() != server.saltTank.getFluidAmount()) problems.add("salt tank");
		if(client.bufferOut.getFluidAmount() != 250 || client.inputTanks[0].getFill() != server.inputTanks[0].getFill()
				|| client.inputTanks[0].getTankType() != LeafiaFluids.FLUORINE) problems.add("buffers and tanks");
		helper.assertTrue(problems.isEmpty(), problems(problems));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the world's ticks

	/**
	 * The machine on the world's own ticks: whatever number of them the window gave it, the power it spent
	 * is the recipe's per processing tick and its progress is those ticks over the duration.
	 */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void theMachineRunsOnTheWorldsTicks(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = ready(helper, "separator.u233");
		long start = te.power;
		TickWindow.sequence(helper).thenIdle(30).thenExecute(() -> {
			long spent = start - te.power;
			helper.assertTrue(spent > 0 && spent % 400 == 0, "spent " + spent + " HE, which is not a whole number of ticks of 400");
			near(helper, te.module.progress * 60D, spent / 400D, "progress x 60 against the ticks run");
			helper.assertTrue(te.didProcess, "the machine is idle");
			helper.assertTrue(te.outputTanks[0].getFill() == 0, "a cycle finished inside thirty ticks");
		}).thenSucceed();
	}

	/**
	 * Salt from a duct run reaches the input buffer, passes to the output buffer with its tag, and the
	 * separator's ports are registered with the network ({@code FFNet}): the Forge side of a real line.
	 */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void saltReachesTheMachineFromAnEjectorThroughDucts(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		SaltSeparatorTE te = lone(helper);
		te.module.setRecipe("separator.u233", false);
		// The port beside the east flank's first corner, then three ducts and an ejector facing back.
		BlockPos port = CORE.offset(2, 0, -1);
		for(int i = 0; i < 3; i++) helper.setBlock(port.east(i), LeafiaBlocks.ff_duct.get());
		BlockPos ej = port.east(3);
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.WEST));
		FuzzyIdentifierItem.spreadTypeFF(helper.getLevel(), helper.absolutePos(port), LeafiaFluids.FLUORIDE, Fluids.NONE, 8);
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		e.tank.setFluid(LeafiaMSRTests.salt(1000, 700, "th232", 0.5));
		TickWindow.sequence(helper).thenWaitUntil(() -> {
			int total = te.bufferIn.getFluidAmount() + te.bufferOut.getFluidAmount();
			helper.assertTrue(total == 1000, "the separator holds " + total + " mB of the ejector's salt");
			helper.assertTrue(e.tank.getFluidAmount() == 0, "the ejector still has " + e.tank.getFluidAmount() + " mB");
			helper.assertTrue(te.bufferOut.getFluidAmount() == 1000, "the salt did not pass through: out " + te.bufferOut.getFluidAmount());
			double heat = LeafiaFF.getTag(te.bufferOut.getFluid()).getDouble("heat");
			helper.assertTrue(heat > 0 && heat <= 700 + EPS, "the salt arrived at " + heat + " degrees; it left at 700 and only cools on the way");
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ the switches

	@GameTest(template = EMPTY16)
	public static void theSeparatorRegistersOnTheReactorsSwitch(GameTestHelper helper) {
		boolean block = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("salt_separator"));
		boolean core = BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("salt_separator"));
		boolean dummy = BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("salt_separator_dummy"));
		helper.assertTrue(block == LeafiaFeatures.lftrReactor() && core == LeafiaFeatures.lftrReactor() && dummy == LeafiaFeatures.lftrReactor(),
				"registered " + block + "/" + core + "/" + dummy + ", gate " + LeafiaFeatures.lftrReactor());
		helper.assertTrue(BuiltInRegistries.MENU.containsKey(LeafiaRef.rl("salt_separator")), "the menu type is registered whatever the switch");
		helper.succeed();
	}
}
