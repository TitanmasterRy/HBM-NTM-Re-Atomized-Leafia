package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockStorageCrate;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.nonmachines.storage.items.CrateLabelEvents;
import com.leafia.contents.nonmachines.storage.items.CrateLabelMenu;
import com.leafia.contents.nonmachines.storage.items.CrateLabelPacket;
import com.leafia.contents.nonmachines.storage.items.CrateLabels;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The crate labels (D-3021): what is written, how it is kept, how the screen is reached and what its menu and
 * packet do.
 *
 * <p>The state is the base crate's {@code extraData} tag under upstream's own keys, so most of what is pinned is
 * the keys, the save, the update tag and the item a broken crate drops. The screen and the renderer are client
 * classes and are looked at in a running client (the {@code crates} scene); the rule that opens the screen, the
 * menu it opens and the packet it sends are server code and are tested here. A mock player's connection refuses
 * the extra-data open packet (the base mod's tests say so too), so the menu is built directly and the rule that
 * decides to open it is {@link CrateLabelEvents#target}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCrateLabelTests {

	private static final String EMPTY = "empty";

	private static Block[] crates() {
		return new Block[] { ModBlocks.crate_iron.get(), ModBlocks.crate_steel.get(), ModBlocks.crate_tungsten.get(), ModBlocks.crate_desh.get() };
	}

	private static BlockEntityCrateBase crate(GameTestHelper helper, BlockPos pos, Block block) {
		helper.setBlock(pos, block);
		BlockEntity be = helper.getBlockEntity(pos);
		helper.assertTrue(be instanceof BlockEntityCrateBase, block + " made no crate block entity");
		return (BlockEntityCrateBase) be;
	}

	private static String[] four(String a, String b, String c, String d) {
		return new String[] { a, b, c, d };
	}

	private static ItemStack screwdriver() {
		return new ItemStack(ModItems.screwdriver.get());
	}

	private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3));
	}

	// ------------------------------------------------------------------ the state

	@GameTest(template = EMPTY)
	public static void anUnlabelledCrateAddsNothingToItsSave(GameTestHelper helper) {

		for(Block block : crates()) {
			BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), block);
			helper.assertTrue(CrateLabels.of(crate).isBlank() && crate.getExtraData().isEmpty(), block + ": a fresh crate has labels");
			CompoundTag saved = crate.saveWithFullMetadata(helper.getLevel().registryAccess());
			helper.assertTrue(!saved.contains("extraData"), block + ": a fresh crate saves extra data: " + saved);
			helper.assertTrue(!crate.getUpdateTag(helper.getLevel().registryAccess()).contains("extraData"), block + ": a fresh crate sends extra data");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theKeysAreUpstreamsOwn(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_iron.get());
		CrateLabels labels = CrateLabels.of(crate);
		labels.setText(four("n", "e", "s", "w"), "top", "42", "bottom");
		labels.setIcon(Items.DIAMOND);

		CompoundTag tag = crate.getExtraData();
		helper.assertTrue(tag.getString("leafia_label_upper").equals("top"), "upper");
		helper.assertTrue(tag.getString("leafia_label_middle").equals("42"), "middle");
		helper.assertTrue(tag.getString("leafia_label_lower").equals("bottom"), "lower");
		for(int i = 0; i < 4; i++)
			helper.assertTrue(tag.getString("leafia_label_" + i).equals(new String[] { "n", "e", "s", "w" }[i]), "vertical " + i);
		helper.assertTrue(tag.getString("leafia_icon_item").equals("minecraft:diamond"), "the icon is the item's registry name");
		helper.assertTrue(tag.size() == 8, "seven lines and the icon, got " + tag.getAllKeys());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anEmptyLineIsNotWritten(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_steel.get());
		CrateLabels labels = CrateLabels.of(crate);
		labels.setText(four("", "x", "", ""), "", "m", "");
		helper.assertTrue(crate.getExtraData().size() == 2 && crate.getExtraData().contains("leafia_label_1") && crate.getExtraData().contains("leafia_label_middle"),
				"only the two lines with text: " + crate.getExtraData().getAllKeys());
		labels.setText(four("", "", "", ""), "", "", "");
		helper.assertTrue(labels.isBlank(), "all lines empty: the crate is unlabelled again");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theIconIsTheItemAndNothingElseReadsAsNone(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_desh.get());
		CrateLabels labels = CrateLabels.of(crate);
		helper.assertTrue(labels.icon() == null, "no icon yet");
		labels.setIcon(ModItems.screwdriver.get());
		helper.assertTrue(labels.icon() == ModItems.screwdriver.get(), "the icon reads back");
		labels.setIcon(Items.AIR);
		helper.assertTrue(labels.icon() == null && crate.getExtraData().isEmpty(), "air clears it");
		labels.setIcon(Items.IRON_INGOT);
		labels.setIcon(null);
		helper.assertTrue(labels.icon() == null && labels.isBlank(), "null clears it");
		crate.getExtraData().putString("leafia_icon_item", "minecraft:air");
		helper.assertTrue(labels.icon() == null, "air by name reads as none");
		crate.getExtraData().putString("leafia_icon_item", "nosuchmod:nosuchitem");
		helper.assertTrue(labels.icon() == null, "an unknown name reads as none");
		crate.getExtraData().putString("leafia_icon_item", "not a name!");
		helper.assertTrue(labels.icon() == null, "an unparseable name reads as none");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void onlyTheFourCratesCanBeLabelled(GameTestHelper helper) {

		for(Block block : crates()) helper.assertTrue(CrateLabels.labellable(block), block + " should take a label");
		helper.assertTrue(!CrateLabels.labellable(ModBlocks.crate_lead.get()) && !CrateLabels.labellable(net.minecraft.world.level.block.Blocks.CHEST)
				&& !CrateLabels.labellable(ModBlocks.crate_weapon.get()), "another crate or chest took a label");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theVerticalLineForAFaceIsUpstreamsModulo(GameTestHelper helper) {

		// upstream: positiveModulo(1 - horizontalIndex, 4), with south 0, west 1, north 2, east 3
		helper.assertTrue(CrateLabels.verticalIndex(Direction.SOUTH) == 1, "south");
		helper.assertTrue(CrateLabels.verticalIndex(Direction.WEST) == 0, "west");
		helper.assertTrue(CrateLabels.verticalIndex(Direction.NORTH) == 3, "north");
		helper.assertTrue(CrateLabels.verticalIndex(Direction.EAST) == 2, "east");
		helper.succeed();
	}

	// ------------------------------------------------------------------ keeping them

	@GameTest(template = EMPTY)
	public static void labelsSurviveASaveAndALoadInAllFourCrates(GameTestHelper helper) {

		for(Block block : crates()) {
			BlockPos pos = new BlockPos(1, 1, 1);
			BlockEntityCrateBase crate = crate(helper, pos, block);
			CrateLabels.of(crate).setText(four("a", "b", "c", "d"), "up", "mid"
					.substring(0, 2), "low");
			CrateLabels.of(crate).setIcon(Items.GOLD_INGOT);

			CompoundTag saved = crate.saveWithFullMetadata(helper.getLevel().registryAccess());
			BlockState state = helper.getBlockState(pos);
			BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), state, saved, helper.getLevel().registryAccess());
			helper.assertTrue(loaded instanceof BlockEntityCrateBase, block + ": did not load");
			CrateLabels back = CrateLabels.of((BlockEntityCrateBase) loaded);
			helper.assertTrue(back.upper().equals("up") && back.middle().equals("mi") && back.lower().equals("low") && back.vertical(2).equals("c")
					&& back.icon() == Items.GOLD_INGOT, block + ": the labels did not survive a load");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theClientIsSentTheLabelsAndNoContents(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_iron.get());
		crate.setItem(0, new ItemStack(Items.DIAMOND, 5));
		CrateLabels.of(crate).setText(four("", "", "", ""), "hello", "", "");

		CompoundTag update = crate.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(update.getAllKeys().equals(java.util.Set.of("extraData")), "the update tag is the extra data only: " + update.getAllKeys());

		BlockEntityCrateBase client = new com.hbm.blockentity.machine.storage.BlockEntityCrateIron(com.hbm.slices.Slice1.CRATE_IRON.get(),
				helper.absolutePos(pos), helper.getBlockState(pos));
		client.handleUpdateTag(update, helper.getLevel().registryAccess());
		helper.assertTrue(CrateLabels.of(client).upper().equals("hello") && client.getItem(0).isEmpty(), "the client reads the label and has no contents");
		helper.succeed();
	}

	/** A crate broken with its contents kept hands the labels to the item, and a crate placed from it reads them. */
	@GameTest(template = EMPTY)
	public static void aBrokenCrateKeepsItsLabelsOnTheItem(GameTestHelper helper) {

		for(Block block : crates()) {
			BlockPos from = new BlockPos(1, 1, 1);
			BlockPos to = new BlockPos(3, 1, 3);
			BlockEntityCrateBase crate = crate(helper, from, block);
			CrateLabels.of(crate).setText(four("n", "", "", ""), "up", "ab", "");
			CrateLabels.of(crate).setIcon(Items.EMERALD);

			Player player = LeafiaCrateTestKit.survivor(helper);
			block.playerWillDestroy(helper.getLevel(), helper.absolutePos(from), helper.getBlockState(from), player);
			List<ItemEntity> dropped = drops(helper, from);
			ItemStack item = ItemStack.EMPTY;
			for(ItemEntity entity : dropped) {
				if(entity.getItem().is(block.asItem())) item = entity.getItem().copy();
				entity.discard();
			}
			helper.assertTrue(!item.isEmpty(), block + ": the broken crate dropped no item");
			CompoundTag data = item.get(DataComponents.CUSTOM_DATA).copyTag();
			helper.assertTrue(data.getCompound("extraData").getString("leafia_label_upper").equals("up"), block + ": the item carries no label: " + data);

			helper.setBlock(from, net.minecraft.world.level.block.Blocks.AIR);
			BlockEntityCrateBase placed = crate(helper, to, block);
			block.setPlacedBy(helper.getLevel(), helper.absolutePos(to), helper.getBlockState(to), player, item);
			CrateLabels back = CrateLabels.of(placed);
			helper.assertTrue(back.upper().equals("up") && back.middle().equals("ab") && back.vertical(0).equals("n") && back.icon() == Items.EMERALD,
					block + ": the placed crate did not read the item's labels");
			helper.setBlock(to, net.minecraft.world.level.block.Blocks.AIR);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ reaching the screen

	@GameTest(template = EMPTY)
	public static void aSneakingScrewdriverOnASideOfACrateIsTheLabellingClick(GameTestHelper helper) {

		if(!LeafiaFeatures.crateLabels) { helper.succeed(); return; }

		for(Block block : crates()) {
			BlockPos pos = new BlockPos(1, 1, 1);
			BlockEntityCrateBase crate = crate(helper, pos, block);
			ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
			player.setShiftKeyDown(true);
			for(Direction face : Direction.Plane.HORIZONTAL) {
				helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), helper.absolutePos(pos), player, screwdriver(), face) == crate,
						block + " " + face + ": the click was not the labelling's");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLabellingClickNeedsTheSneakTheToolTheSideAndTheCrate(GameTestHelper helper) {

		if(!LeafiaFeatures.crateLabels) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		crate(helper, pos, ModBlocks.crate_iron.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);

		// not sneaking: the crate opens as it always did
		player.setShiftKeyDown(false);
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, screwdriver(), Direction.NORTH) == null, "a plain click labelled");

		player.setShiftKeyDown(true);
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, screwdriver(), Direction.UP) == null, "the top face labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, screwdriver(), Direction.DOWN) == null, "the bottom face labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, ItemStack.EMPTY, Direction.NORTH) == null, "an empty hand labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, new ItemStack(Items.STICK), Direction.NORTH) == null, "a stick labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, new ItemStack(ModItems.wrench.get()), Direction.NORTH) == null, "a wrench labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), abs, player, new ItemStack(ModItems.screwdriver_desh.get()), Direction.NORTH) != null,
				"a desh screwdriver is a screwdriver");

		BlockPos other = new BlockPos(3, 1, 1);
		helper.setBlock(other, ModBlocks.crate_lead.get());
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), helper.absolutePos(other), player, screwdriver(), Direction.NORTH) == null,
				"another crate labelled");
		helper.assertTrue(CrateLabelEvents.target(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), player, screwdriver(), Direction.NORTH) == null,
				"air labelled");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSwitchTakesTheLabellingAway(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		crate(helper, pos, ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setShiftKeyDown(true);
		boolean before = LeafiaFeatures.crateLabels;
		try {
			LeafiaFeatures.crateLabels = false;
			helper.assertTrue(CrateLabelEvents.useOnCrate(helper.getLevel(), helper.absolutePos(pos), player, screwdriver(), Direction.NORTH) == null,
					"the labelling answered with the switch off");
			List<Component> tooltip = new ArrayList<>();
			CrateLabelEvents.addTooltip(ModBlocks.crate_iron.get(), tooltip);
			helper.assertTrue(tooltip.isEmpty(), "the tooltip lines stayed");
		} finally {
			LeafiaFeatures.crateLabels = before;
		}
		helper.succeed();
	}

	/** The server's open: the provider builds the crate's menu with the clicked face, and its payload builds the same menu on a client. */
	@GameTest(template = EMPTY)
	public static void theMenuTheServerOpensIsTheCratesAndItsPayloadBuildsTheSameOnAClient(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_steel.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		BlockPos abs = helper.absolutePos(pos);

		for(Direction face : Direction.Plane.HORIZONTAL) {
			var provider = CrateLabelEvents.provider(crate, face);
			helper.assertTrue(provider.getDisplayName().getContents().toString().contains("gui.label_crate"), "titled Labelling");
			var made = provider.createMenu(3, player.getInventory(), player);
			helper.assertTrue(made instanceof CrateLabelMenu m && m.crate() == crate && m.side() == face, face + ": the server's menu is the crate's, with the face");

			net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
			CrateLabelEvents.writeData(buf, abs, face);
			CrateLabelMenu client = new CrateLabelMenu(4, player.getInventory(), buf);
			helper.assertTrue(client.crate() == crate && client.side() == face, face + ": the payload builds the same menu: " + client.side());
		}
		helper.succeed();
	}

	/** The click itself: handled on both sides; the server's run reaches the open (a mock connection refuses the open packet). */
	@GameTest(template = EMPTY)
	public static void aLabellingClickIsHandledAndTheServerOpensTheMenu(GameTestHelper helper) {

		if(!LeafiaFeatures.crateLabels) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		crate(helper, pos, ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setShiftKeyDown(true);
		boolean reachedTheOpen = false;
		try {
			InteractionResult result = CrateLabelEvents.useOnCrate(helper.getLevel(), helper.absolutePos(pos), player, screwdriver(), Direction.WEST);
			helper.assertTrue(result == InteractionResult.SUCCESS, "answered " + result);
			reachedTheOpen = player.containerMenu instanceof CrateLabelMenu;
		} catch(UnsupportedOperationException refused) {
			reachedTheOpen = true; // a mock player's connection refuses the extra-data open packet, after the menu was made
		}
		helper.assertTrue(reachedTheOpen, "the server's click did not get as far as opening the menu");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theTooltipHasUpstreamsLines(GameTestHelper helper) {

		if(!LeafiaFeatures.crateLabels) { helper.succeed(); return; }

		boolean before = LeafiaFeatures.tungstenCrate;
		try {
			LeafiaFeatures.tungstenCrate = false;
			for(Block block : crates()) {
				List<Component> tooltip = new ArrayList<>();
				CrateLabelEvents.addTooltip(block, tooltip);
				helper.assertTrue(tooltip.size() == 1 && tooltip.get(0).getContents().toString().contains("desc.leafia.crate_labelling"),
						block + ": the labelling hint alone: " + tooltip);
			}
			LeafiaFeatures.tungstenCrate = true;
			List<Component> tooltip = new ArrayList<>();
			CrateLabelEvents.addTooltip(ModBlocks.crate_tungsten.get(), tooltip);
			helper.assertTrue(tooltip.size() == 2 && tooltip.get(0).getContents().toString().contains("tile.crate_tungsten.desc")
					&& tooltip.get(0).getStyle().getColor() != null && tooltip.get(0).getStyle().getColor().getValue() == 0xFF5555,
					"the tungsten note in red, then the hint: " + tooltip);
			List<Component> none = new ArrayList<>();
			CrateLabelEvents.addTooltip(ModBlocks.crate_lead.get(), none);
			helper.assertTrue(none.isEmpty(), "another crate got a line");
		} finally {
			LeafiaFeatures.tungstenCrate = before;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the menu

	private static CrateLabelMenu menu(GameTestHelper helper, BlockEntityCrateBase crate, ServerPlayer player, Direction face) {
		CrateLabelMenu menu = new CrateLabelMenu(1, player.getInventory(), crate, face);
		player.containerMenu = menu;
		return menu;
	}

	@GameTest(template = EMPTY)
	public static void theMenuIsUpstreamsLayout(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		CrateLabelMenu menu = menu(helper, crate, player, Direction.NORTH);

		helper.assertTrue(menu.slots.size() == 1 + 27 + 9, "an icon slot and the inventory, got " + menu.slots.size());
		helper.assertTrue(menu.getSlot(0).x == 62 && menu.getSlot(0).y == 35, "the icon slot is at (62, 35)");
		helper.assertTrue(menu.getSlot(1).x == 8 && menu.getSlot(1).y == 103 && menu.getSlot(27).x == 8 + 8 * 18 && menu.getSlot(27).y == 103 + 2 * 18,
				"the inventory starts at (8, 103)");
		helper.assertTrue(menu.getSlot(28).x == 8 && menu.getSlot(28).y == 161 && menu.getSlot(36).x == 8 + 8 * 18 && menu.getSlot(36).y == 161,
				"the hotbar is at y 161");
		helper.assertTrue(menu.side() == Direction.NORTH && menu.crate() == crate, "it knows its crate and face");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anItemOnTheIconSlotBecomesTheIconAndStaysOnTheCursor(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		CrateLabelMenu menu = menu(helper, crate, player, Direction.EAST);

		menu.setCarried(new ItemStack(Items.DIAMOND, 7));
		menu.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(CrateLabels.of(crate).icon() == Items.DIAMOND, "the crate's icon is the diamond");
		helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 7, "the cursor keeps all seven: " + menu.getCarried());
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 1, "the slot shows one");

		menu.setCarried(new ItemStack(Items.GOLD_INGOT));
		menu.clicked(0, 1, ClickType.PICKUP, player);
		helper.assertTrue(CrateLabels.of(crate).icon() == Items.GOLD_INGOT, "another item replaces it");

		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(CrateLabels.of(crate).icon() == null && menu.getSlot(0).getItem().isEmpty() && menu.getCarried().isEmpty(),
				"an empty cursor clears it and picks nothing up");
		helper.assertTrue(crate.getExtraData().isEmpty(), "and the crate is unlabelled again");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNumberKeySwapOffersTheHotbarStackAndOtherClicksDoNothing(GameTestHelper helper) {

		BlockEntityCrateBase crate = crate(helper, new BlockPos(1, 1, 1), ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		CrateLabelMenu menu = menu(helper, crate, player, Direction.EAST);

		player.getInventory().setItem(3, new ItemStack(Items.REDSTONE, 12));
		menu.clicked(0, 3, ClickType.SWAP, player);
		helper.assertTrue(CrateLabels.of(crate).icon() == Items.REDSTONE && player.getInventory().getItem(3).getCount() == 12, "the swap sets the icon and moves nothing");

		menu.clicked(0, 3, ClickType.QUICK_MOVE, player);
		menu.clicked(0, 0, ClickType.THROW, player);
		menu.clicked(0, 0, ClickType.CLONE, player);
		helper.assertTrue(CrateLabels.of(crate).icon() == Items.REDSTONE && player.getInventory().getItem(3).getCount() == 12, "shift, throw and clone do nothing to it");

		helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && menu.quickMoveStack(player, 12).isEmpty(), "shift-click moves nothing, as upstream's");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theMenuStaysOpenFromAnyDistanceUntilTheCrateIsGone(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		CrateLabelMenu menu = menu(helper, crate, player, Direction.NORTH);

		player.setPos(player.getX() + 500, player.getY(), player.getZ());
		helper.assertTrue(menu.stillValid(player), "upstream's canInteractWith is true however far");
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
		helper.assertTrue(!menu.stillValid(player), "the crate is gone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the packet

	@GameTest(template = EMPTY)
	public static void theOpenScreensTextIsWrittenToTheCrate(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_iron.get());
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		BlockPos abs = helper.absolutePos(pos);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
		menu(helper, crate, player, Direction.SOUTH);

		helper.assertTrue(CrateLabelPacket.apply(player, abs, four("v0", "v1", "v2", "v3"), "upper line", "ab", "lower line"), "refused a valid packet");
		CrateLabels labels = CrateLabels.of(crate);
		helper.assertTrue(labels.upper().equals("upper line") && labels.middle().equals("ab") && labels.lower().equals("lower line") && labels.vertical(3).equals("v3"),
				"the lines are in the crate");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLabelPacketIsOnlyForTheCrateWhoseScreenIsOpenAndInReach(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_iron.get());
		BlockEntityCrateBase other = crate(helper, new BlockPos(3, 1, 1), ModBlocks.crate_steel.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);

		// no screen open
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, four("", "", "", ""), "a", "", ""), "written with no screen open");
		menu(helper, crate, player, Direction.NORTH);
		// another crate's position
		helper.assertTrue(!CrateLabelPacket.apply(player, other.getBlockPos(), four("", "", "", ""), "a", "", ""), "written to a crate whose screen is not open");
		helper.assertTrue(CrateLabels.of(other).isBlank() && CrateLabels.of(crate).isBlank(), "nothing was written");
		// a line longer than the box takes
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, four("", "", "", ""), "x".repeat(33), "", ""), "33 characters in a 32-character box");
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, four("", "", "", ""), "", "abc", ""), "3 characters in the middle line");
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, four("", "y".repeat(33), "", ""), "", "", ""), "a long vertical line");
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, new String[] { "", "", "" }, "", "", ""), "three vertical lines");
		helper.assertTrue(CrateLabels.of(crate).isBlank(), "still nothing");
		// the longest lines that do fit
		helper.assertTrue(CrateLabelPacket.apply(player, abs, four("z".repeat(32), "", "", ""), "x".repeat(32), "ab", "y".repeat(32)), "refused the longest valid lines");
		// too far
		CrateLabels.of(crate).setText(four("", "", "", ""), "", "", "");
		player.setPos(abs.getX() + 100, abs.getY(), abs.getZ());
		helper.assertTrue(!CrateLabelPacket.apply(player, abs, four("", "", "", ""), "far", "", ""), "written from a hundred blocks away");
		helper.assertTrue(CrateLabels.of(crate).isBlank(), "nothing from afar");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theLabelPacketThroughItsFraming(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityCrateBase crate = crate(helper, pos, ModBlocks.crate_desh.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
		menu(helper, crate, player, Direction.WEST);

		helper.assertTrue(new CrateLabelPacket().acceptsServerbound(), "a client may send it");
		LeafiaCrateTestKit.run(new CrateLabelPacket(abs, four("a", "bb", "ccc", "dddd"), "hello", "99", "bye"), player, PacketFlow.CLIENTBOUND);
		helper.assertTrue(CrateLabels.of(crate).isBlank(), "the packet acted clientbound");
		LeafiaCrateTestKit.run(new CrateLabelPacket(abs, four("a", "bb", "ccc", "dddd"), "hello", "99", "bye"), player, PacketFlow.SERVERBOUND);
		CrateLabels labels = CrateLabels.of(crate);
		helper.assertTrue(labels.vertical(0).equals("a") && labels.vertical(1).equals("bb") && labels.vertical(2).equals("ccc") && labels.vertical(3).equals("dddd")
				&& labels.upper().equals("hello") && labels.middle().equals("99") && labels.lower().equals("bye"), "the lines crossed the framing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aLabelledItemIsUniqueSoItDoesNotStackWithAnUnlabelledOne(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		Block block = ModBlocks.crate_iron.get();
		BlockEntityCrateBase crate = crate(helper, pos, block);
		CrateLabels.of(crate).setText(four("", "", "", ""), "x", "", "");
		Player player = LeafiaCrateTestKit.survivor(helper);
		block.playerWillDestroy(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), player);
		ItemStack item = ItemStack.EMPTY;
		for(ItemEntity entity : drops(helper, pos)) {
			item = entity.getItem().copy();
			entity.discard();
		}
		CompoundTag data = item.get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(data.contains("stacklock") && data.contains("extraData"), "the label made the item unique: " + data);
		helper.assertTrue(!ItemStack.isSameItemSameComponents(item, new ItemStack(block)), "and it is not a plain crate");
		helper.succeed();
	}
}
