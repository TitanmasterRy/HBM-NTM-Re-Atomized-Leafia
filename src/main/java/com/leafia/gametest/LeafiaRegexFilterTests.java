package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterBlock;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE.FilterType;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE.RegexFilter;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTabs;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.RegexFilterMenu;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;
import com.hbm.util.Tuple.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The pneumatic tube regex filter (D-1084): what it lets through, what it pushes where, and what
 * it remembers.
 *
 * <p>The matching rules are the part that has to be exactly upstream's, because a player writes a
 * pattern against them: the pattern must match the <b>whole</b> string, a blacklist wins wherever
 * it sits, a list of blacklists passes nothing, and a pattern that does not compile matches
 * nothing. Each is pinned on its own so a regression names the rule it broke.
 *
 * <p>What these cannot reach is the two screens, which are {@code @OnlyIn(Dist.CLIENT)}: the
 * client sweep opens them. The menu, the packet channel and the block entity are all server-side
 * and are asserted here.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRegexFilterTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ helpers

	private static RegexFilterTE place(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, LeafiaBlocks.regex_filter.get().defaultBlockState().setValue(RegexFilterBlock.FACING, facing));
		BlockEntity be = helper.getBlockEntity(pos);
		helper.assertTrue(be instanceof RegexFilterTE, "the block has no filter entity");
		return (RegexFilterTE) be;
	}

	private static RegexFilter filter(String regex, FilterType type, boolean blacklist) {
		RegexFilter f = new RegexFilter();
		f.regex = regex;
		f.type = type;
		f.blacklist = blacklist;
		return f;
	}

	private static RegexFilterTE with(RegexFilterTE te, RegexFilter... filters) {
		te.filters.clear();
		for(RegexFilter f : filters) te.filters.add(f);
		return te;
	}

	private static ItemStack stack(net.minecraft.world.level.ItemLike item) {
		return new ItemStack(item);
	}

	// ------------------------------------------------------------------ the matching

	/**
	 * {@code String.matches}, not a search: the pattern has to cover the whole id.
	 */
	@GameTest(template = EMPTY)
	public static void aPatternMustMatchTheWholeString(GameTestHelper helper) {

		List<String> wrong = new ArrayList<>();
		Object[][] cases = {
				{ "minecraft:stone", "stone", false },
				{ "minecraft:stone", "minecraft:stone", true },
				{ "minecraft:stone", ".*stone", true },
				{ "minecraft:stone", "minecraft:.*", true },
				{ "minecraft:stone", "minecraft:ston", false },
				{ "minecraft:stone", "MINECRAFT:STONE", false },
				{ "minecraft:stone", "minecraft:(stone|dirt)", true },
				{ "", "", true },
				{ "x", "", false },
		};
		for(Object[] c : cases) {
			boolean got = LeafiaUtil.matchesRegex((String) c[0], (String) c[1]);
			if(got != (boolean) c[2]) wrong.add("'" + c[0] + "' ~ '" + c[1] + "' was " + got);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A pattern that does not compile is not an error: it matches nothing. */
	@GameTest(template = EMPTY)
	public static void anInvalidPatternMatchesNothingAndSaysSo(GameTestHelper helper) {

		helper.assertTrue(!LeafiaUtil.isRegexValid("("), "an open group compiled");
		helper.assertTrue(!LeafiaUtil.isRegexValid("[a-"), "an open class compiled");
		helper.assertTrue(!LeafiaUtil.isRegexValid("*"), "a bare star compiled");
		helper.assertTrue(LeafiaUtil.isRegexValid(""), "the empty pattern is a pattern");
		helper.assertTrue(LeafiaUtil.isRegexValid("a(b)*[c-d]\\d+"), "a plain pattern did not compile");

		helper.assertTrue(!LeafiaUtil.matchesRegex("(", "("), "an invalid pattern matched the text it spells");
		helper.assertTrue(!LeafiaUtil.matchesRegex("anything", "[a-"), "an invalid pattern matched");
		helper.succeed();
	}

	/** D-3075: a catastrophic pattern is abandoned quickly instead of hanging the server thread, and an ordinary one still matches. */
	@GameTest(template = EMPTY)
	public static void aCatastrophicPatternIsAbandonedAndMatchesNothing(GameTestHelper helper) {
		long start = System.nanoTime();
		boolean got = LeafiaUtil.matchesRegex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", "(a+)+$");
		long ms = (System.nanoTime() - start) / 1_000_000;
		helper.assertTrue(!got, "the catastrophic pattern matched");
		helper.assertTrue(ms < 2000, "it took " + ms + " ms");
		helper.assertTrue(LeafiaUtil.matchesRegex("minecraft:stone", ".*stone"), "an ordinary pattern stopped matching");
		helper.succeed();
	}

	/** D-3075: breaking a filter drops the stack in its slot (it used to be destroyed). */
	@GameTest(template = EMPTY)
	public static void breakingAFilterDropsItsStack(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 1, 2);
		RegexFilterTE te = place(helper, at, Direction.EAST);
		te.inventory.setStackInSlot(0, new ItemStack(Items.STONE, 40));
		helper.setBlock(at, Blocks.AIR);
		int n = 0;
		for(var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(at)).inflate(2)))
			if(e.getItem().is(Items.STONE)) { n += e.getItem().getCount(); e.discard(); }
		helper.assertTrue(n == 40, "dropped " + n + " of 40 stone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the filter list

	/** Nothing passes an empty list. */
	@GameTest(template = EMPTY)
	public static void anEmptyListPassesNothing(GameTestHelper helper) {
		RegexFilterTE te = place(helper, new BlockPos(2, 1, 2), Direction.EAST);
		helper.assertTrue(te.filters.isEmpty(), "a fresh filter has filters");
		helper.assertTrue(!te.isItemValid(stack(Items.STONE)), "an empty list passed stone");
		helper.assertTrue(!te.isItemValid(ItemStack.EMPTY), "an empty list passed the empty stack");
		helper.succeed();
	}

	/** Whitelists are a union. */
	@GameTest(template = EMPTY)
	public static void whitelistsAreAUnion(GameTestHelper helper) {
		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("minecraft:stone", FilterType.RESOURCE_ID, false),
				filter("minecraft:coal", FilterType.RESOURCE_ID, false));
		helper.assertTrue(te.isItemValid(stack(Items.STONE)), "stone was refused");
		helper.assertTrue(te.isItemValid(stack(Items.COAL)), "coal was refused");
		helper.assertTrue(!te.isItemValid(stack(Items.DIRT)), "dirt was passed");
		helper.succeed();
	}

	/**
	 * A blacklist match wins even when it comes after the whitelist that matched, because the loop
	 * returns at the first blacklist hit - and a list of blacklists alone passes nothing, because
	 * only a whitelist hit ever sets {@code valid}.
	 */
	@GameTest(template = EMPTY)
	public static void aBlacklistWinsWhereverItSitsAndNeverPassesAnything(GameTestHelper helper) {

		BlockPos pos = new BlockPos(2, 1, 2);
		RegexFilterTE te = with(place(helper, pos, Direction.EAST),
				filter(".*", FilterType.RESOURCE_ID, false),
				filter("minecraft:stone", FilterType.RESOURCE_ID, true));
		helper.assertTrue(!te.isItemValid(stack(Items.STONE)), "a later blacklist did not win");
		helper.assertTrue(te.isItemValid(stack(Items.DIRT)), "the blacklist spread to dirt");

		with(te,
				filter("minecraft:stone", FilterType.RESOURCE_ID, true),
				filter(".*", FilterType.RESOURCE_ID, false));
		helper.assertTrue(!te.isItemValid(stack(Items.STONE)), "an earlier blacklist did not win");
		helper.assertTrue(te.isItemValid(stack(Items.DIRT)), "the whitelist stopped passing dirt");

		with(te, filter("minecraft:stone", FilterType.RESOURCE_ID, true));
		helper.assertTrue(!te.isItemValid(stack(Items.STONE)), "a lone blacklist passed what it names");
		helper.assertTrue(!te.isItemValid(stack(Items.DIRT)), "a lone blacklist passed dirt: it must pass nothing");
		helper.succeed();
	}

	/**
	 * An invalid pattern as a whitelist passes nothing and as a blacklist blocks nothing - the
	 * second is the surprising half, and it is upstream's.
	 */
	@GameTest(template = EMPTY)
	public static void anInvalidPatternNeitherPassesNorBlocks(GameTestHelper helper) {

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("(", FilterType.RESOURCE_ID, false));
		helper.assertTrue(!te.isItemValid(stack(Items.STONE)), "an invalid whitelist passed stone");

		with(te,
				filter("minecraft:.*", FilterType.RESOURCE_ID, false),
				filter("(", FilterType.RESOURCE_ID, true));
		helper.assertTrue(te.isItemValid(stack(Items.STONE)), "an invalid blacklist blocked stone");
		helper.succeed();
	}

	/** Each mode tests its own string: the item's id, each of its tags, each of its creative tabs. */
	@GameTest(template = EMPTY)
	public static void eachModeTestsItsOwnString(GameTestHelper helper) {

		RegexFilterTabs.reset();
		RegexFilterTE te = place(helper, new BlockPos(2, 1, 2), Direction.EAST);
		List<String> wrong = new ArrayList<>();

		with(te, filter("minecraft:coal", FilterType.RESOURCE_ID, false));
		if(!te.isItemValid(stack(Items.COAL))) wrong.add("RESOURCE_ID refused minecraft:coal");
		if(te.isItemValid(stack(Items.CHARCOAL))) wrong.add("RESOURCE_ID passed charcoal");

		// 1.21.1's ore dictionary is the item tag: the test string is the tag's id.
		with(te, filter("minecraft:coals", FilterType.ORE_DICT, false));
		if(!te.isItemValid(stack(Items.COAL))) wrong.add("ORE_DICT refused coal, which is in minecraft:coals");
		if(!te.isItemValid(stack(Items.CHARCOAL))) wrong.add("ORE_DICT refused charcoal, which is in minecraft:coals");
		if(te.isItemValid(stack(Items.STONE))) wrong.add("ORE_DICT passed stone");

		with(te, filter("minecraft:coal", FilterType.ORE_DICT, false));
		if(te.isItemValid(stack(Items.COAL))) wrong.add("ORE_DICT matched an item ID, which is not a tag");

		with(te, filter("c:ingots/iron", FilterType.ORE_DICT, false));
		if(!te.isItemValid(stack(Items.IRON_INGOT))) wrong.add("ORE_DICT refused an iron ingot for c:ingots/iron");

		with(te, filter("minecraft:building_blocks", FilterType.CREATIVE_TAB, false));
		if(!te.isItemValid(stack(Items.STONE))) wrong.add("CREATIVE_TAB refused stone for minecraft:building_blocks");
		if(te.isItemValid(stack(Items.APPLE))) wrong.add("CREATIVE_TAB passed an apple for building blocks");

		with(te, filter("minecraft:coal", FilterType.CREATIVE_TAB, false));
		if(te.isItemValid(stack(Items.COAL))) wrong.add("CREATIVE_TAB matched an item ID, which is not a tab");

		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The tab index inverts the tabs, which a dedicated server has never built: an item is in every
	 * tab that lists it, an NTM item is in its own tab, the addon's in {@code leafia:main}, and an
	 * item nobody lists is in none.
	 */
	@GameTest(template = EMPTY)
	public static void theTabIndexInvertsTheTabs(GameTestHelper helper) {

		RegexFilterTabs.reset();
		var level = helper.getLevel();
		List<String> wrong = new ArrayList<>();

		if(!RegexFilterTabs.tabsOf(level, Items.STONE).contains("minecraft:building_blocks"))
			wrong.add("stone is not in building_blocks: " + RegexFilterTabs.tabsOf(level, Items.STONE));
		if(!RegexFilterTabs.tabsOf(level, Items.APPLE).contains("minecraft:food_and_drinks"))
			wrong.add("an apple is not in food_and_drinks: " + RegexFilterTabs.tabsOf(level, Items.APPLE));
		if(!RegexFilterTabs.tabsOf(level, ModBlocks.pneumatic_tube.get().asItem()).contains("hbm:machine"))
			wrong.add("the pneumatic tube is not in hbm:machine: "
					+ RegexFilterTabs.tabsOf(level, ModBlocks.pneumatic_tube.get().asItem()));
		if(!RegexFilterTabs.tabsOf(level, LeafiaBlocks.regex_filter.get().asItem()).contains("leafia:main"))
			wrong.add("the filter is not in leafia:main: " + RegexFilterTabs.tabsOf(level, LeafiaBlocks.regex_filter.get().asItem()));
		if(!RegexFilterTabs.tabsOf(level, Items.AIR).isEmpty())
			wrong.add("air is in a tab: " + RegexFilterTabs.tabsOf(level, Items.AIR));

		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the slot

	/** The slot refuses what the filters refuse - to a simulated insert, a real one and a slot's own query. */
	@GameTest(template = EMPTY)
	public static void theSlotRefusesWhatTheFiltersRefuse(GameTestHelper helper) {

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("minecraft:stone", FilterType.RESOURCE_ID, false));

		ItemStack dirt = new ItemStack(Items.DIRT, 5);
		ItemStack back = te.inventory.insertItem(0, dirt, false);
		helper.assertTrue(back.getCount() == 5 && te.inventory.getStackInSlot(0).isEmpty(), "dirt went in");
		helper.assertTrue(!te.inventory.isItemValid(0, dirt), "isItemValid said yes to dirt");

		ItemStack sim = te.inventory.insertItem(0, new ItemStack(Items.STONE, 7), true);
		helper.assertTrue(sim.isEmpty() && te.inventory.getStackInSlot(0).isEmpty(), "a simulated insert changed the slot");

		ItemStack rest = te.inventory.insertItem(0, new ItemStack(Items.STONE, 7), false);
		helper.assertTrue(rest.isEmpty() && te.inventory.getStackInSlot(0).getCount() == 7, "stone did not go in");
		helper.succeed();
	}

	// ------------------------------------------------------------------ pushing

	/**
	 * Upstream's {@code tryInsertItemCap}: a slot that will not take one is skipped, the rest take
	 * as many as the binary search finds, and the held stack shrinks by what went.
	 */
	@GameTest(template = EMPTY)
	public static void theUnloaderFillsOnlyWhatFits(GameTestHelper helper) {

		RegexFilterTE te = place(helper, new BlockPos(2, 1, 2), Direction.EAST);

		ItemStackHandler target = new ItemStackHandler(4);
		target.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 60)); // room for 4
		target.setStackInSlot(1, new ItemStack(Items.DIRT, 1));          // a different item: skipped
		// slot 2 is empty: room for a full stack
		target.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));  // full: skipped

		ItemStack held = new ItemStack(Items.COBBLESTONE, 20);
		boolean moved = te.tryInsertItemCap(target, held);

		helper.assertTrue(moved, "nothing moved");
		helper.assertTrue(held.isEmpty(), "the held stack kept " + held.getCount());
		helper.assertTrue(target.getStackInSlot(0).getCount() == 64, "slot 0 holds " + target.getStackInSlot(0).getCount());
		helper.assertTrue(target.getStackInSlot(1).is(Items.DIRT) && target.getStackInSlot(1).getCount() == 1, "slot 1 changed");
		helper.assertTrue(target.getStackInSlot(2).getCount() == 16, "slot 2 holds " + target.getStackInSlot(2).getCount());
		helper.assertTrue(target.getStackInSlot(3).getCount() == 64, "slot 3 changed");

		ItemStackHandler full = new ItemStackHandler(2);
		full.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
		full.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
		ItemStack stuck = new ItemStack(Items.COBBLESTONE, 9);
		helper.assertTrue(!te.tryInsertItemCap(full, stuck), "a full target reported a move");
		helper.assertTrue(stuck.getCount() == 9, "a full target took " + (9 - stuck.getCount()));

		helper.assertTrue(!te.tryInsertItemCap(full, ItemStack.EMPTY), "the empty stack moved");
		helper.succeed();
	}

	/** A filter pushes what it holds into the chest it faces, and only that. */
	@GameTest(timeoutTicks = TickWindow.TIMEOUT, template = EMPTY)
	public static void aFilterPushesIntoWhatItFaces(GameTestHelper helper) {

		BlockPos filterPos = new BlockPos(1, 1, 2);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		RegexFilterTE te = with(place(helper, filterPos, Direction.EAST),
				filter("minecraft:stone", FilterType.RESOURCE_ID, false));

		IItemHandler back = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
				helper.absolutePos(filterPos), Direction.WEST);
		helper.assertTrue(back != null, "the back face has no item handler");

		helper.assertTrue(back.insertItem(0, new ItemStack(Items.DIRT, 3), false).getCount() == 3, "dirt went in the back");
		helper.assertTrue(back.insertItem(0, new ItemStack(Items.STONE, 3), false).isEmpty(), "stone was refused at the back");

		TickWindow.runAfterDelay(helper, 3, () -> {
			ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
			helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "the filter still holds " + te.inventory.getStackInSlot(0));
			helper.assertTrue(chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 3,
					"the chest holds " + chest.getItem(0));
			helper.succeed();
		});
	}

	/** With nothing in front of it, or a full chest, the filter keeps what it holds. */
	@GameTest(timeoutTicks = TickWindow.TIMEOUT, template = EMPTY)
	public static void aFilterWithNowhereToGoKeepsItsItems(GameTestHelper helper) {

		BlockPos filterPos = new BlockPos(1, 1, 2);
		RegexFilterTE te = with(place(helper, filterPos, Direction.EAST),
				filter(".*", FilterType.RESOURCE_ID, false));
		te.inventory.setStackInSlot(0, new ItemStack(Items.STONE, 12));

		TickWindow.runAfterDelay(helper, 3, () -> {
			helper.assertTrue(te.inventory.getStackInSlot(0).getCount() == 12, "air took some");

			BlockPos chestPos = new BlockPos(2, 1, 2);
			helper.setBlock(chestPos, Blocks.CHEST);
			ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
			for(int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));

			helper.runAfterDelay(3, () -> {
				helper.assertTrue(te.inventory.getStackInSlot(0).getCount() == 12, "a full chest took some");
				helper.succeed();
			});
		});
	}

	/**
	 * Two filters in a row: the first accepts anything, the second only stone. What the second
	 * refuses never leaves the first, and stone goes the whole way.
	 */
	@GameTest(timeoutTicks = TickWindow.TIMEOUT, template = EMPTY)
	public static void filtersChainFrontToBack(GameTestHelper helper) {

		BlockPos a = new BlockPos(0, 1, 2);
		BlockPos b = new BlockPos(1, 1, 2);
		BlockPos chestPos = new BlockPos(2, 1, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		RegexFilterTE first = with(place(helper, a, Direction.EAST), filter(".*", FilterType.RESOURCE_ID, false));
		RegexFilterTE second = with(place(helper, b, Direction.EAST), filter("minecraft:stone", FilterType.RESOURCE_ID, false));

		first.inventory.setStackInSlot(0, new ItemStack(Items.DIRT, 4));

		TickWindow.runAfterDelay(helper, 4, () -> {
			helper.assertTrue(first.inventory.getStackInSlot(0).getCount() == 4, "dirt left the first filter");
			helper.assertTrue(second.inventory.getStackInSlot(0).isEmpty(), "dirt got into the second");

			first.inventory.setStackInSlot(0, new ItemStack(Items.STONE, 4));
			helper.runAfterDelay(5, () -> {
				ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
				helper.assertTrue(first.inventory.getStackInSlot(0).isEmpty() && second.inventory.getStackInSlot(0).isEmpty(),
						"stone is stuck in a filter");
				helper.assertTrue(chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 4,
						"the chest holds " + chest.getItem(0));
				helper.succeed();
			});
		});
	}

	// ------------------------------------------------------------------ the faces

	/** The handler is on the open back and nowhere else; a null side sees it too. */
	@GameTest(template = EMPTY)
	public static void theHandlerIsOnTheBackOnly(GameTestHelper helper) {

		List<String> wrong = new ArrayList<>();
		BlockPos pos = new BlockPos(2, 1, 2);
		for(Direction facing : Direction.values()) {
			place(helper, pos, facing);
			for(Direction side : Direction.values()) {
				IItemHandler cap = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
				boolean expected = side == facing.getOpposite();
				if((cap != null) != expected)
					wrong.add("facing " + facing + ", side " + side + ": " + (cap != null ? "has" : "lacks") + " a handler");
			}
			if(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null) == null)
				wrong.add("facing " + facing + ": a null side sees nothing");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * It points into the block it was placed against, rotates and mirrors with the world, and its
	 * outline is a rod 8 pixels across along its axis.
	 */
	@GameTest(template = EMPTY)
	public static void placementFacingRotationAndShape(GameTestHelper helper) {

		List<String> wrong = new ArrayList<>();
		RegexFilterBlock block = LeafiaBlocks.regex_filter.get();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));

		for(Direction clicked : Direction.values()) {
			BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
					new ItemStack(block), new BlockHitResult(Vec3.atCenterOf(pos), clicked, pos, false));
			BlockState state = block.getStateForPlacement(context);
			if(state.getValue(RegexFilterBlock.FACING) != clicked.getOpposite())
				wrong.add("clicking the " + clicked + " face gave " + state.getValue(RegexFilterBlock.FACING));
		}

		BlockState east = block.defaultBlockState().setValue(RegexFilterBlock.FACING, Direction.EAST);
		if(east.rotate(Rotation.CLOCKWISE_90).getValue(RegexFilterBlock.FACING) != Direction.SOUTH) wrong.add("east did not turn to south");
		if(east.rotate(Rotation.CLOCKWISE_180).getValue(RegexFilterBlock.FACING) != Direction.WEST) wrong.add("east did not turn to west");
		if(east.mirror(Mirror.LEFT_RIGHT).getValue(RegexFilterBlock.FACING) != Direction.EAST) wrong.add("a z-mirror moved east");
		if(east.mirror(Mirror.FRONT_BACK).getValue(RegexFilterBlock.FACING) != Direction.WEST) wrong.add("an x-mirror left east");
		BlockState up = block.defaultBlockState().setValue(RegexFilterBlock.FACING, Direction.UP);
		if(up.rotate(Rotation.CLOCKWISE_90).getValue(RegexFilterBlock.FACING) != Direction.UP) wrong.add("up turned");

		double lo = 4d / 16;
		double hi = 1 - 4d / 16;
		for(Direction facing : Direction.values()) {
			VoxelShape shape = block.defaultBlockState().setValue(RegexFilterBlock.FACING, facing)
					.getShape(helper.getLevel(), pos, CollisionContext.empty());
			double[] min = { shape.min(net.minecraft.core.Direction.Axis.X), shape.min(net.minecraft.core.Direction.Axis.Y), shape.min(net.minecraft.core.Direction.Axis.Z) };
			double[] max = { shape.max(net.minecraft.core.Direction.Axis.X), shape.max(net.minecraft.core.Direction.Axis.Y), shape.max(net.minecraft.core.Direction.Axis.Z) };
			int axis = facing.getAxis().ordinal();
			for(int i = 0; i < 3; i++) {
				double expectMin = i == axis ? 0 : lo;
				double expectMax = i == axis ? 1 : hi;
				if(Math.abs(min[i] - expectMin) > 1e-9 || Math.abs(max[i] - expectMax) > 1e-9)
					wrong.add(facing + " axis " + i + " spans " + min[i] + ".." + max[i]);
			}
		}

		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ------------------------------------------------------------------ what it remembers

	/**
	 * The list, its order, every flag and the held stack survive a save and a load, and an entry
	 * with a type this build does not know is skipped without taking the rest with it.
	 */
	@GameTest(template = EMPTY)
	public static void theListAndTheStackSurviveASaveAndALoad(GameTestHelper helper) {

		var registries = helper.getLevel().registryAccess();
		RegexFilterTE te = with(place(helper, new BlockPos(1, 1, 2), Direction.NORTH),
				filter("minecraft:.*", FilterType.RESOURCE_ID, false),
				filter("c:ingots/.*", FilterType.ORE_DICT, true),
				filter("\\d+|[a-z]{2,}\\\\é", FilterType.CREATIVE_TAB, false));
		te.inventory.setStackInSlot(0, new ItemStack(Items.STONE, 33));

		CompoundTag saved = te.saveWithoutMetadata(registries);
		RegexFilterTE copy = place(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
		copy.loadWithComponents(saved, registries);

		helper.assertTrue(copy.filters.size() == 3, "the copy has " + copy.filters.size() + " filters");
		for(int i = 0; i < 3; i++) {
			RegexFilter a = te.filters.get(i);
			RegexFilter b = copy.filters.get(i);
			helper.assertTrue(a.regex.equals(b.regex) && a.type == b.type && a.blacklist == b.blacklist,
					"filter " + i + " changed: '" + a.regex + "' " + a.type + " " + a.blacklist
							+ " became '" + b.regex + "' " + b.type + " " + b.blacklist);
		}
		helper.assertTrue(copy.inventory.getStackInSlot(0).is(Items.STONE) && copy.inventory.getStackInSlot(0).getCount() == 33,
				"the stack became " + copy.inventory.getStackInSlot(0));

		helper.assertTrue(te.getUpdateTag(registries).equals(saved), "the update tag is not the saved tag");

		// A ListTag holds one type, so "an entry that is not a compound" can only be a whole list of
		// them: a list of strings reads as no filters at all.
		ListTag strings = new ListTag();
		strings.add(StringTag.valueOf("not a compound"));
		copy.loadFilterFromNBT(strings);
		helper.assertTrue(copy.filters.isEmpty(), "a list of strings made " + copy.filters.size() + " filters");

		ListTag list = new ListTag();
		CompoundTag bogus = new CompoundTag();
		bogus.putString("type", "BOGUS");
		bogus.putString("value", "x");
		list.add(bogus);
		CompoundTag good = new CompoundTag();
		good.putString("type", "ORE_DICT");
		good.putString("value", "kept");
		good.putBoolean("blacklist", true);
		list.add(good);
		CompoundTag lenient = new CompoundTag();
		lenient.putString("type", "CREATIVE_TAB");
		list.add(lenient);
		copy.loadFilterFromNBT(list);

		helper.assertTrue(copy.filters.size() == 2, "expected the two readable entries, got " + copy.filters.size());
		helper.assertTrue(copy.filters.get(0).regex.equals("kept") && copy.filters.get(0).blacklist
				&& copy.filters.get(0).type == FilterType.ORE_DICT, "the good entry changed");
		helper.assertTrue(copy.filters.get(1).regex.isEmpty() && !copy.filters.get(1).blacklist,
				"a missing field is not the empty string and false");

		CompoundTag noFilter = new CompoundTag();
		RegexFilterTE blank = place(helper, new BlockPos(3, 1, 3), Direction.UP);
		blank.loadWithComponents(noFilter, registries);
		helper.assertTrue(blank.filters.isEmpty() && blank.inventory.getStackInSlot(0).isEmpty(), "an empty tag made content");
		helper.succeed();
	}

	/** Two filters are the same filter when pattern and type agree; the flag is not part of it. */
	@GameTest(template = EMPTY)
	public static void twoFiltersAreEqualByPatternAndType(GameTestHelper helper) {

		RegexFilter a = filter("x", FilterType.ORE_DICT, false);
		helper.assertTrue(a.equals(filter("x", FilterType.ORE_DICT, true)), "the flag broke equality");
		helper.assertTrue(!a.equals(filter("x", FilterType.RESOURCE_ID, false)), "the type did not matter");
		helper.assertTrue(!a.equals(filter("y", FilterType.ORE_DICT, false)), "the pattern did not matter");
		helper.assertTrue(a.hashCode() == filter("x", FilterType.ORE_DICT, true).hashCode(), "equal filters hash apart");

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST), a);
		helper.assertTrue(!te.filters.add(filter("x", FilterType.ORE_DICT, true)), "the set took a duplicate");
		helper.assertTrue(te.filters.size() == 1, "the list grew");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the packet channel

	/**
	 * The sync packet carries the whole list as one compound in slot 0, and decodes to a list that
	 * loads into another filter unchanged. The sizes are measured, not derived: they move if the
	 * wire format or the NBT layout does, and a filter sync is sent to every player with the menu
	 * open on every edit.
	 */
	@GameTest(template = EMPTY)
	public static void theSyncPacketCarriesTheWholeListAndHasASize(GameTestHelper helper) {

		var registries = helper.getLevel().registryAccess();
		RegexFilterTE te = place(helper, new BlockPos(1, 1, 2), Direction.EAST);

		LeafiaPacket none = te.generateSyncPacket().build();
		with(te, filter("minecraft:stone", FilterType.RESOURCE_ID, false));
		LeafiaPacket one = te.generateSyncPacket().build();
		with(te, filter("minecraft:stone", FilterType.RESOURCE_ID, false),
				filter("c:ingots/.*", FilterType.ORE_DICT, true),
				filter("hbm:machine", FilterType.CREATIVE_TAB, false));
		LeafiaPacket three = te.generateSyncPacket().build();

		helper.assertTrue(none.identifier() == "PNEUMO_REGEX".hashCode(), "the identifier is not PNEUMO_REGEX's hash");
		helper.assertTrue(none.data().length < one.data().length && one.data().length < three.data().length,
				"size is not increasing: " + none.data().length + ", " + one.data().length + ", " + three.data().length);
		helper.assertTrue(three.data().length < LeafiaPacket.MAX_PAYLOAD, "three filters are over the limit");

		Map<Byte, Pair<Byte, Object>> decoded = three.decode(registries);
		helper.assertTrue(decoded.size() == 1, "expected one slot, got " + decoded.keySet());
		Object value = decoded.values().iterator().next().getValue();
		helper.assertTrue(value instanceof CompoundTag tag && tag.contains("a"), "slot 0 is not the compound " + value);

		RegexFilterTE other = place(helper, new BlockPos(3, 1, 2), Direction.EAST);
		other.onReceivePacketLocal((byte) 0, value);
		helper.assertTrue(other.filters.size() == 3 && other.filters.get(1).blacklist
				&& other.filters.get(1).type == FilterType.ORE_DICT && other.filters.get(2).regex.equals("hbm:machine"),
				"the decoded list is not the sent one");
		helper.succeed();
	}

	/** Client receive replaces the list and tells the open screen; a stray key or type does neither. */
	@GameTest(template = EMPTY)
	public static void theClientReplacesTheListAndTellsTheScreen(GameTestHelper helper) {

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("old", FilterType.RESOURCE_ID, false));
		int[] synced = { 0 };
		te.gui = () -> synced[0]++;

		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		CompoundTag entry = new CompoundTag();
		entry.putString("type", "ORE_DICT");
		entry.putString("value", "new");
		list.add(entry);
		tag.put("a", list);

		te.onReceivePacketLocal((byte) 1, tag);
		te.onReceivePacketLocal((byte) 0, "not a compound");
		helper.assertTrue(synced[0] == 0 && te.filters.get(0).regex.equals("old"), "a stray packet changed the filter");

		te.onReceivePacketLocal((byte) 0, tag);
		helper.assertTrue(synced[0] == 1, "the screen was told " + synced[0] + " times");
		helper.assertTrue(te.filters.size() == 1 && te.filters.get(0).regex.equals("new"), "the list was not replaced");
		helper.succeed();
	}

	/** The server replaces the list from a client's packet and saves it; a stray key or type does nothing. */
	@GameTest(template = EMPTY)
	public static void theServerReplacesTheListFromAClientPacket(GameTestHelper helper) {

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("old", FilterType.RESOURCE_ID, false));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		te.listeners.add(player);

		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for(String regex : new String[] { "a", "b" }) {
			CompoundTag entry = new CompoundTag();
			entry.putString("type", "RESOURCE_ID");
			entry.putString("value", regex);
			entry.putBoolean("blacklist", regex.equals("b"));
			list.add(entry);
		}
		tag.put("a", list);

		// D-1094: nothing is heard from a player without this filter's screen open
		te.onReceivePacketServer((byte) 0, tag, player);
		helper.assertTrue(te.filters.size() == 1 && te.filters.get(0).regex.equals("old"), "a player with no screen open replaced the list");
		Vec3 at = Vec3.atCenterOf(te.getBlockPos());
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new RegexFilterMenu(1, player.getInventory(), te);
		te.listeners.clear();

		te.onReceivePacketServer((byte) 5, tag, player);
		te.onReceivePacketServer((byte) 0, 42, player);
		te.onReceivePacketServer((byte) 0, new CompoundTag(), player);
		helper.assertTrue(te.filters.size() == 1 && te.filters.get(0).regex.equals("old"), "a stray packet changed the list");

		te.onReceivePacketServer((byte) 0, tag, player);
		helper.assertTrue(te.filters.size() == 2 && te.filters.get(1).blacklist, "the list was not replaced");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the menu

	/** One machine slot at (8, 35), then the inventory at y 103 and the hotbar at y 161. */
	@GameTest(template = EMPTY)
	public static void theMenuHasUpstreamsSlots(GameTestHelper helper) {

		RegexFilterTE te = place(helper, new BlockPos(2, 1, 2), Direction.EAST);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		RegexFilterMenu menu = new RegexFilterMenu(1, player.getInventory(), te);

		helper.assertTrue(menu.getType() == LeafiaMenuTypes.REGEX_FILTER.get(), "the menu is not of type regex_filter");
		helper.assertTrue(menu.slots.size() == 37, "37 slots, got " + menu.slots.size());
		helper.assertTrue(menu.slots.get(0).x == 8 && menu.slots.get(0).y == 35, "the machine slot is at "
				+ menu.slots.get(0).x + "," + menu.slots.get(0).y);
		helper.assertTrue(menu.slots.get(0).container != player.getInventory(), "the first slot is the player's");
		helper.assertTrue(menu.slots.get(1).x == 8 && menu.slots.get(1).y == 103, "the inventory starts at "
				+ menu.slots.get(1).x + "," + menu.slots.get(1).y);
		helper.assertTrue(menu.slots.get(27).x == 8 + 8 * 18 && menu.slots.get(27).y == 103 + 2 * 18, "the inventory ends wrong");
		helper.assertTrue(menu.slots.get(28).x == 8 && menu.slots.get(28).y == 161, "the hotbar starts at "
				+ menu.slots.get(28).x + "," + menu.slots.get(28).y);
		helper.assertTrue(menu.stillValid(player), "upstream's canInteractWith is always true");
		helper.succeed();
	}

	/**
	 * A server menu puts its player on the listener list and takes it off when closed; shift-click
	 * moves only what the filters pass into the machine slot and anything back out of it.
	 */
	@GameTest(template = EMPTY)
	public static void theMenuListensAndShiftClicksThroughTheFilter(GameTestHelper helper) {

		RegexFilterTE te = with(place(helper, new BlockPos(2, 1, 2), Direction.EAST),
				filter("minecraft:stone", FilterType.RESOURCE_ID, false));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		RegexFilterMenu menu = new RegexFilterMenu(1, player.getInventory(), te);

		helper.assertTrue(te.listeners.contains(player), "the opening player is not a listener");

		player.getInventory().setItem(0, new ItemStack(Items.DIRT, 10));
		player.getInventory().setItem(1, new ItemStack(Items.STONE, 10));
		int dirtSlot = 28 + 0;
		int stoneSlot = 28 + 1;

		menu.quickMoveStack(player, dirtSlot);
		helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "shift-click put dirt in a filter that refuses it");
		helper.assertTrue(player.getInventory().getItem(0).getCount() == 10, "the dirt moved");

		menu.quickMoveStack(player, stoneSlot);
		helper.assertTrue(te.inventory.getStackInSlot(0).is(Items.STONE) && te.inventory.getStackInSlot(0).getCount() == 10,
				"stone did not go in: " + te.inventory.getStackInSlot(0));

		te.inventory.setStackInSlot(0, new ItemStack(Items.DIRT, 6)); // what a changed filter strands
		menu.quickMoveStack(player, 0);
		helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "the stranded dirt did not come out");

		menu.removed(player);
		helper.assertTrue(!te.listeners.contains(player), "a closed menu left its player listening");
		helper.succeed();
	}

	// ------------------------------------------------------------------ registration

	@GameTest(template = EMPTY)
	public static void theBlockEntityAndItsBlockAreRegistered(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.regexFilter, "the feature is off");
		helper.assertTrue(LeafiaBlockEntities.REGEX_FILTER.get().isValid(LeafiaBlocks.regex_filter.get().defaultBlockState()),
				"the block entity type does not accept the block");
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(LeafiaBlocks.regex_filter.get());
		helper.assertTrue(id.equals(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "regex_filter")), "the block is " + id);
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "the block has no item");
		helper.assertTrue(BuiltInRegistries.MENU.containsKey(id), "the menu is not registered");
		helper.succeed();
	}
}
