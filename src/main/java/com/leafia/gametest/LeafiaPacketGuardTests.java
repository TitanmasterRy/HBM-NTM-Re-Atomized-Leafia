package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorSignalType;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem.FuzzyIdentifierPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.ElevatorButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonInteractPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonModifyPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvSyncRequestPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FireButton;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.FloorButton;
import com.leafia.contents.machines.elevators.floors.EvFloorTE;
import com.leafia.contents.machines.elevators.gui.EvCabinMenu;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE;
import com.leafia.contents.machines.heat.hpboiler.container.HPBoilerMenu;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterBlock;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.RegexFilterTE;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.RegexFilterMenu;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket.SyncVelocityPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaEntities;
import com.leafia.lib.LeafiaRef;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.technical.FifthString;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Every serverbound addon packet, validated (D-1094, the owner's answer to D-1093 #4).
 *
 * <p>The addon has two payloads and each reaches the server. {@code leafia:custom}
 * ({@link LeafiaCustomPacket}) carries forty packet types of which seven are a client's requests -
 * the cabin's button press, sync request and button editor, the radio's request and answer, the
 * fuzzy identifier's tank click and the advisor's screen. {@code leafia:container}
 * ({@link LeafiaPacket}) carries a block entity's updates, and four block entities listen for
 * one on the server - the floor landing, the boiler, the reactor and the filter. One test each,
 * every one with the same shape: <b>a valid packet acts; one with a bad value, one for the wrong
 * thing, one from the wrong place and one from too far away does not</b>. Where a rule has no
 * meaning for a handler (the radio has no block to be near), its test says so and skips it.
 *
 * <p>The layer beneath them is tested first: a packet type that does not say it takes serverbound
 * traffic is dropped before it is decoded, and a decoder that throws on a serverbound packet drops
 * it instead of reaching the network thread.
 *
 * <p>"Too far" is a player a hundred blocks off; "in reach" is a player on the block. Mock players
 * are taken off the player list, as every other test of the addon's packets does, because one left
 * on it is sent the addon's broadcasts on a connection that negotiated no channels and crashes the
 * whole GameTest server.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaPacketGuardTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ the layer beneath

	/** A packet type that does not accept serverbound traffic is dropped unread; one that does is queued. */
	@GameTest(template = EMPTY)
	public static void aServerboundPacketOfAClientboundTypeIsDropped(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		LeafiaCustomPacket velocity = LeafiaCustomPacket.__start(new SyncVelocityPacket(new Vec3(0, 1, 0)));

		Counter toServer = new Counter();
		LeafiaCustomPacket.handle(velocity, context(player, PacketFlow.SERVERBOUND, toServer));
		helper.assertTrue(toServer.queued == 0, "a client moved a player's velocity: the packet was queued");

		Counter toClient = new Counter();
		LeafiaCustomPacket.handle(velocity, context(player, PacketFlow.CLIENTBOUND, toClient));
		helper.assertTrue(toClient.queued == 1, "the same packet clientbound was not queued");

		LeafiaCustomPacket request = LeafiaCustomPacket.__start(new EvSyncRequestPacket() {
			{ localEntity = null; }
			@Override public void encode(LeafiaBuf buf) { buf.writeInt(-1); }
		});
		Counter accepted = new Counter();
		LeafiaCustomPacket.handle(request, context(player, PacketFlow.SERVERBOUND, accepted));
		helper.assertTrue(accepted.queued == 1, "a request packet, which accepts serverbound, was not queued");
		helper.succeed();
	}

	/** A decoder that throws on a serverbound packet drops it: the hand byte of a button press is range-checked at decode. */
	@GameTest(template = EMPTY)
	public static void aMalformedServerboundPacketIsDroppedNotThrown(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		LeafiaCustomPacket badHand = LeafiaCustomPacket.__start(new EvButtonInteractPacket() {
			@Override public void encode(LeafiaBuf buf) {
				buf.writeInt(-1);
				buf.writeFifthString(new FifthString("fire"));
				buf.writeByte(5); // there are two hands
			}
		});
		Counter counter = new Counter();
		LeafiaCustomPacket.handle(badHand, context(player, PacketFlow.SERVERBOUND, counter));
		helper.assertTrue(counter.queued == 0, "a hand ordinal of 5 produced a callback");

		// and at the decoder itself, not only through the handler's net
		var opened = LeafiaCustomPacket.open(badHand);
		helper.assertTrue(opened.prototype().decode(opened.buf()) == null, "the decoder accepted a hand ordinal of 5");

		// a decoder that throws: a button editor packet whose label length is a varint of four bytes
		LeafiaCustomPacket throwing = LeafiaCustomPacket.__start(new EvButtonModifyPacket() {
			@Override public void encode(LeafiaBuf buf) {
				buf.writeInt(-1);
				buf.insert(3, 2);  // modify
				buf.writeInt(0);
				buf.insert(1, 2);  // the indicator text
				for(int i = 0; i < 4; i++) buf.writeByte(0x80); // "VarInt too big"
			}
		});
		Counter thrown = new Counter();
		LeafiaCustomPacket.handle(throwing, context(player, PacketFlow.SERVERBOUND, thrown));
		helper.assertTrue(thrown.queued == 0, "an undecodable packet produced a callback");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the cabin: press

	/**
	 * A button press: the cabin must be within reach, and the button one the cabin has. Pressing
	 * {@code fire} no longer replaces the layout with upstream's demo (D-3075), so the checks assert it stays.
	 */
	@GameTest(template = EMPTY)
	public static void theCabinButtonPressIsValidated(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		cabin.buttons.clear();
		cabin.buttons.add(new FloorButton(cabin, 1, "1", 0, 3));
		ServerPlayer player = mockPlayer(helper);

		// in reach, but "fire" is a button this cabin does not have: the demo layout must not be installed
		place(player, cabin, 1);
		press(cabin, "fire", player, PacketFlow.SERVERBOUND);
		press(cabin, "nope", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(cabin.buttons.size() == 1, "an unknown button id was run");

		cabin.buttons.clear();
		cabin.buttons.add(new FireButton(cabin, 0, 3));

		// too far: a hundred blocks off
		place(player, cabin, 100);
		press(cabin, "fire", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(cabin.buttons.size() == 1, "a player a hundred blocks away pressed the button");
		place(player, cabin, 1);

		// a clientbound packet does nothing, whoever it claims to be from
		press(cabin, "fire", player, PacketFlow.CLIENTBOUND);
		helper.assertTrue(cabin.buttons.size() == 1, "a clientbound press was run");

		// an entity id that is not a cabin: dropped (nothing to assert beyond not throwing)
		LeafiaCustomPacket stray = LeafiaCustomPacket.__start(new EvButtonInteractPacket() {
			@Override public void encode(LeafiaBuf buf) {
				buf.writeInt(player.getId());
				buf.writeFifthString(new FifthString("fire"));
				buf.writeByte(0);
			}
		});
		run(stray, player, PacketFlow.SERVERBOUND);

		// the real thing
		press(cabin, "fire", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(cabin.buttons.size() == 1, "the fire press must not rewrite the button set (D-3075): " + cabin.buttons.size() + " buttons");
		cabin.discard();
		helper.succeed();
	}

	/** A sync request is answered for a cabin the sender could see, and for no other. */
	@GameTest(template = EMPTY)
	public static void theCabinSyncRequestIsValidated(GameTestHelper helper) {

		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);

		place(player, cabin, 2);
		helper.assertTrue(syncRequest(cabin.getId(), player, PacketFlow.SERVERBOUND),
				"a request from beside the cabin was not answered");

		place(player, cabin, 100);
		helper.assertTrue(syncRequest(cabin.getId(), player, PacketFlow.SERVERBOUND),
				"a request from a hundred blocks, inside the entity's tracking range, was not answered");

		place(player, cabin, 20000);
		helper.assertTrue(!syncRequest(cabin.getId(), player, PacketFlow.SERVERBOUND),
				"a request from beyond the tracking range was answered");

		place(player, cabin, 2);
		helper.assertTrue(!syncRequest(cabin.getId(), player, PacketFlow.CLIENTBOUND),
				"a clientbound request was answered");
		helper.assertTrue(!syncRequest(-12345, player, PacketFlow.SERVERBOUND),
				"a request for an entity that does not exist was answered");
		helper.assertTrue(!syncRequest(player.getId(), player, PacketFlow.SERVERBOUND),
				"a request for an entity that is not a cabin was answered");
		cabin.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ the cabin: editor

	/** The editor's packets need the cabin's screen open, reach, a real target, a real type and a short label. */
	@GameTest(template = EMPTY)
	public static void theCabinButtonEditorIsValidated(GameTestHelper helper) {

		ElevatorEntity cabin = cabinWithButtons(helper);
		ElevatorEntity other = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		place(player, cabin, 1);

		// no screen open
		player.containerMenu = player.inventoryMenu;
		modify(cabin, 0, 2, player, p -> { p.localX = 33; });
		helper.assertTrue(cabin.buttons.get(2).x != 33, "a player with no cabin screen open moved a button");

		// the screen of a different cabin
		player.containerMenu = new EvCabinMenu(1, player.getInventory(), other);
		modify(cabin, 0, 2, player, p -> { p.localX = 33; });
		helper.assertTrue(cabin.buttons.get(2).x != 33, "another cabin's screen let a player move this cabin's button");

		// the right screen, but too far
		player.containerMenu = new EvCabinMenu(1, player.getInventory(), cabin);
		place(player, cabin, 100);
		modify(cabin, 0, 2, player, p -> { p.localX = 33; });
		helper.assertTrue(cabin.buttons.get(2).x != 33, "a player a hundred blocks away moved a button");

		// reach and screen: valid moves act, bad values do not
		place(player, cabin, 1);
		modify(cabin, 0, 2, player, p -> { p.localX = 33; p.localY = 5; });
		helper.assertTrue(cabin.buttons.get(2).x == 33 && cabin.buttons.get(2).y == 5, "the valid move did not act");

		for(int target : new int[] {-1, 4, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
			for(int mode : new int[] {0, 2, 3})
				modify(cabin, mode, target, player, p -> { p.localX = 44; p.localFloor = 99; });
			helper.assertTrue(cabin.buttons.size() == 4, "target " + target + " changed the list");
		}
		for(ElevatorButton b : cabin.buttons)
			helper.assertTrue(b.x != 44, b.id + " was moved by an out-of-range target");

		// add: the five kinds act; type 5, 6 and 7 do not; the cap holds
		for(int type = 5; type < 8; type++) {
			int t = type;
			modify(cabin, 1, 0, player, p -> p.localButtonType = t);
			helper.assertTrue(cabin.buttons.size() == 4, "button type " + type + " was added");
		}
		modify(cabin, 1, 0, player, p -> p.localButtonType = 2);
		helper.assertTrue(cabin.buttons.size() == 5, "a valid add did not act");
		while(cabin.buttons.size() < ElevatorEntity.MAX_BUTTONS) cabin.buttons.add(new FireButton(cabin, 0, 3));
		modify(cabin, 1, 0, player, p -> p.localButtonType = 2);
		helper.assertTrue(cabin.buttons.size() == ElevatorEntity.MAX_BUTTONS, "the cabin grew past " + ElevatorEntity.MAX_BUTTONS + " buttons");
		while(cabin.buttons.size() > 4) cabin.buttons.remove(cabin.buttons.size() - 1);

		// modify: property 3 does not exist, and a label has a length
		modify(cabin, 3, 1, player, p -> { p.localProperty = 3; p.localLabel = "X"; });
		helper.assertTrue(((FloorButton) cabin.buttons.get(1)).label.equals("2"), "property 3 changed the label");
		String longest = "x".repeat(ElevatorEntity.MAX_LABEL);
		modify(cabin, 3, 1, player, p -> { p.localProperty = 2; p.localLabel = longest + "x"; });
		helper.assertTrue(((FloorButton) cabin.buttons.get(1)).label.equals("2"), "a label of " + (longest.length() + 1) + " characters was stored");
		modify(cabin, 3, 1, player, p -> { p.localProperty = 2; p.localLabel = longest; });
		helper.assertTrue(((FloorButton) cabin.buttons.get(1)).label.equals(longest), "a label of " + longest.length() + " characters was refused");

		// remove acts last, and keeps the order
		modify(cabin, 2, 0, player, p -> { });
		helper.assertTrue(cabin.buttons.size() == 3 && cabin.buttons.get(0).id.equals("floor2"), "the valid removal did not act");

		cabin.discard();
		other.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ the radio

	/**
	 * The radio has no block to be near and no menu to have open - its screen is a plain {@code Screen}
	 * and the item in the main hand is the proof of it - so its two checks are the hand and the range
	 * of the three ordinals. {@code LeafiaEvRadioTests.everyByteIsSafe} sweeps all 256 bytes; this
	 * pins the three outcomes by name.
	 */
	@GameTest(template = EMPTY)
	public static void theRadioPacketsAreValidated(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		ItemStack radio = new ItemStack(LeafiaItems.ev_radio.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, radio);

		runPacket(radioUi(0, 1, -1), player, PacketFlow.SERVERBOUND);
		helper.assertTrue(radio.has(DataComponents.CUSTOM_DATA), "the valid answer did not act");

		ItemStack untouched = new ItemStack(LeafiaItems.ev_radio.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, untouched);
		runPacket(radioUi(0, 2, 0), player, PacketFlow.SERVERBOUND);
		runPacket(radioUi(-2, 0, 0), player, PacketFlow.SERVERBOUND);
		runPacket(radioUi(0, 0, 127), player, PacketFlow.SERVERBOUND);
		runPacket(radioUi(0, 0, 0), player, PacketFlow.CLIENTBOUND);
		helper.assertTrue(!untouched.has(DataComponents.CUSTOM_DATA), "an out-of-range or wrong-way answer was written");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
		runPacket(radioUi(0, 0, 0), player, PacketFlow.SERVERBOUND);
		helper.assertTrue(!player.getMainHandItem().has(DataComponents.CUSTOM_DATA), "a stick was written");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the fuzzy identifier

	/** A fluid on the cursor needs a machine's menu, still valid for the sender, and a fluid that exists. */
	@GameTest(template = EMPTY)
	public static void theFuzzyIdentifierPacketIsValidated(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		ItemStack tool = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(tool, Fluids.WATER);

		// only the player's own inventory open: no machine, no click
		player.containerMenu = player.inventoryMenu;
		player.containerMenu.setCarried(tool);
		fuzzy("OIL", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(FuzzyIdentifierItem.getType(tool) == Fluids.WATER, "the inventory screen set a fluid");

		// a machine screen that is no longer valid for the sender (too far, in vanilla's terms)
		Container gone = new SimpleContainer(27) {
			@Override public boolean stillValid(Player p) { return false; }
		};
		ChestMenu stale = ChestMenu.threeRows(1, player.getInventory(), gone);
		player.containerMenu = stale;
		stale.setCarried(tool);
		fuzzy("OIL", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(FuzzyIdentifierItem.getType(tool) == Fluids.WATER, "a menu that was no longer valid set a fluid");

		ChestMenu open = ChestMenu.threeRows(2, player.getInventory(), new SimpleContainer(27));
		player.containerMenu = open;
		open.setCarried(tool);

		fuzzy("NOT_A_FLUID", player, PacketFlow.SERVERBOUND);
		fuzzy("X".repeat(FuzzyIdentifierPacket.MAX_NAME + 1), player, PacketFlow.SERVERBOUND);
		fuzzy("OIL", player, PacketFlow.CLIENTBOUND);
		helper.assertTrue(FuzzyIdentifierItem.getType(tool) == Fluids.WATER, "an invalid name or a clientbound packet set a fluid");

		open.setCarried(new ItemStack(net.minecraft.world.item.Items.STICK));
		fuzzy("OIL", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(open.getCarried().getItem() == net.minecraft.world.item.Items.STICK, "a stick on the cursor was changed");

		open.setCarried(tool);
		fuzzy("OIL", player, PacketFlow.SERVERBOUND);
		helper.assertTrue(FuzzyIdentifierItem.getType(tool) == Fluids.OIL, "the valid click did not act");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the advisor

	/** The advisor's four serverbound signals. */
	@GameTest(template = EMPTY)
	public static void theAdvisorPacketIsValidated(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		for(int i = 0; i < 36; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
		player.getInventory().offhand.set(0, ItemStack.EMPTY);

		// WARN: needs an advisor on the player, and a key the watch can raise
		advisorWarn("coal", player);
		helper.assertTrue(history(player).isEmpty(), "a player with no advisor stored a warning");

		player.getInventory().setItem(3, new ItemStack(LeafiaItems.advisor.get()));
		advisorWarn("not a warning", player);
		advisorWarn("coal ".repeat(500), player);
		advisorWarn("", player);
		helper.assertTrue(history(player).isEmpty(), "an unknown key was stored");

		advisorWarn("lava", player);
		advisorWarn("coal", player);
		helper.assertTrue(history(player).equals(List.of("lava", "coal")), "the valid warnings did not act: " + history(player));

		// DISMISS and SYNC_REQUEST need the screen open: the main-hand advisor's cover
		dismiss(0, player);
		helper.assertTrue(history(player).size() == 2, "a dismissal with the screen shut was honoured");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeafiaItems.advisor.get()));
		CompoundTag open = new CompoundTag();
		open.putBoolean("open", true);
		player.getMainHandItem().set(DataComponents.CUSTOM_DATA, CustomData.of(open));

		for(int index : new int[] {-1, 2, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			helper.assertTrue(!dismiss(index, player), "index " + index + " was answered with a history");
			helper.assertTrue(history(player).size() == 2, "index " + index + " removed something");
		}
		helper.assertTrue(!runPacket(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id), player, PacketFlow.CLIENTBOUND),
				"a clientbound request was answered");
		helper.assertTrue(runPacket(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id), player, PacketFlow.SERVERBOUND),
				"the open screen's request was not answered");
		helper.assertTrue(dismiss(0, player), "the valid dismissal did not answer");
		helper.assertTrue(history(player).equals(List.of("coal")), "the valid dismissal removed the wrong entry: " + history(player));

		player.getMainHandItem().remove(DataComponents.CUSTOM_DATA);
		helper.assertTrue(!runPacket(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id), player, PacketFlow.SERVERBOUND),
				"a request with the cover shut was answered");

		// the history is capped, oldest out
		for(int i = 0; i < AdvisorItem.MAX_HISTORY + 20; i++) advisorWarn(i % 2 == 0 ? "radon" : "lava", player);
		helper.assertTrue(history(player).size() == AdvisorItem.MAX_HISTORY, "the history grew to " + history(player).size());
		helper.succeed();
	}

	/** An unknown signal and a history that claims more entries than the packet holds are refused at decode. */
	@GameTest(template = EMPTY)
	public static void theAdvisorDecoderRefusesLies(GameTestHelper helper) {

		LeafiaCustomPacket unknown = LeafiaCustomPacket.__start(new AdvisorPacket() {
			@Override public void encode(LeafiaBuf buf) { buf.writeByte(99); }
		});
		var opened = LeafiaCustomPacket.open(unknown);
		helper.assertTrue(opened.prototype().decode(opened.buf()) == null, "signal 99 produced a callback");

		LeafiaCustomPacket lie = LeafiaCustomPacket.__start(new AdvisorPacket() {
			@Override public void encode(LeafiaBuf buf) {
				buf.writeByte(AdvisorSignalType.SYNC_LOCAL.id);
				buf.writeInt(Integer.MAX_VALUE); // two billion entries, none sent
			}
		});
		var liar = LeafiaCustomPacket.open(lie);
		helper.assertTrue(liar.prototype().decode(liar.buf()) == null, "a count of two billion produced a callback");

		LeafiaCustomPacket negative = LeafiaCustomPacket.__start(new AdvisorPacket() {
			@Override public void encode(LeafiaBuf buf) {
				buf.writeByte(AdvisorSignalType.SYNC_LOCAL.id);
				buf.writeInt(-5);
			}
		});
		var neg = LeafiaCustomPacket.open(negative);
		helper.assertTrue(neg.prototype().decode(neg.buf()) == null, "a negative count produced a callback");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the container packet

	/**
	 * The container channel's server side: a position, a creation mode and an array length are a
	 * client's words, and the sender must be within reach for anything that changes a block. The
	 * floor landing is the receiver used here because it takes one value and needs no structure.
	 */
	@GameTest(template = EMPTY)
	public static void theContainerPacketIsValidatedBeforeAnyReceiverSeesIt(GameTestHelper helper) {

		BlockPos core = new BlockPos(2, 1, 2);
		EvFloorTE te = floor(helper, core);
		ServerPlayer player = mockPlayer(helper);
		te.editors.add(player.getUUID());
		Vec3 at = Vec3.atCenterOf(te.getBlockPos());

		LeafiaPacket set17 = new LeafiaPacket.Builder(te.getBlockPos(), te.getPacketIdentifier(), helper.getLevel())
				.__write(0, 17).build();

		// too far
		player.setPos(at.x + 100, at.y, at.z);
		LeafiaPacket.handle(set17, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 1, "a player a hundred blocks away set the floor");

		// a different identifier: the packet is for a different receiver
		player.setPos(at.x, at.y, at.z);
		LeafiaPacket wrongName = new LeafiaPacket.Builder(te.getBlockPos(), "NOT_EV_FLOOR", helper.getLevel())
				.__write(0, 17).build();
		LeafiaPacket.handle(wrongName, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 1, "a packet for a different identifier reached the landing");

		// a position with no receiver on it
		LeafiaPacket wrongBlock = new LeafiaPacket.Builder(te.getBlockPos().above(1), te.getPacketIdentifier(), helper.getLevel())
				.__write(0, 17).build();
		LeafiaPacket.handle(wrongBlock, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 1, "a packet for the block above reached the landing");

		// a creation mode that is no mode
		LeafiaPacket badMode = new LeafiaPacket(set17.x(), set17.y(), set17.z(), set17.identifier(), (byte) 9,
				set17.bitLength(), set17.data());
		LeafiaPacket.handle(badMode, context(player, PacketFlow.SERVERBOUND, new Counter()));
		LeafiaPacket negativeMode = new LeafiaPacket(set17.x(), set17.y(), set17.z(), set17.identifier(), (byte) -1,
				set17.bitLength(), set17.data());
		LeafiaPacket.handle(negativeMode, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 1, "a creation mode of 9 or -1 got through");

		// an array that claims sixteen million elements in a handful of bytes
		LeafiaBuf lie = new LeafiaBuf(Unpooled.buffer());
		lie.writeByte((7 << 5) + 31);
		lie.writeInt((3 << 5) | (16_000_000 << 8)); // key 0, type int, sixteen million of them
		byte[] bytes = new byte[lie.payloadLength()];
		System.arraycopy(lie.bytes, lie.payloadOffset(), bytes, 0, bytes.length);
		LeafiaPacket lying = new LeafiaPacket(set17.x(), set17.y(), set17.z(), set17.identifier(), (byte) 1,
				lie.writerIndex, bytes);
		boolean refused = false;
		try {
			lying.decode(helper.getLevel().registryAccess());
		} catch(LeafiaDevFlaw expected) {
			// refused up front, before the allocation, and not by the read that would have run off the end
			refused = String.valueOf(expected.getMessage()).contains("array of 16000000");
		}
		helper.assertTrue(refused, "an array of sixteen million in five bytes was not refused by its length");
		LeafiaPacket.handle(lying, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 1, "the lying packet changed the floor");

		// the real thing: in reach, the screen opened for them, a floor the screen could send
		LeafiaPacket.handle(set17, context(player, PacketFlow.SERVERBOUND, new Counter()));
		helper.assertTrue(te.floor == 17, "the valid packet did not act, floor is " + te.floor);
		helper.succeed();
	}

	/** The floor landing: only someone the screen was opened for, in reach, with a floor the field can hold. */
	@GameTest(template = EMPTY)
	public static void theFloorLandingIsValidated(GameTestHelper helper) {

		EvFloorTE te = floor(helper, new BlockPos(2, 1, 2));
		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(te.getBlockPos());
		player.setPos(at.x, at.y, at.z);

		te.onReceivePacketServer((byte) 0, 17, player);
		helper.assertTrue(te.floor == 1, "a player who was never sent the screen set the floor");

		te.editors.add(player.getUUID());
		for(int floor : new int[] {EvFloorTE.MIN_FLOOR - 1, EvFloorTE.MAX_FLOOR + 1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
			te.onReceivePacketServer((byte) 0, floor, player);
			helper.assertTrue(te.floor == 1, "floor " + floor + " was stored");
		}
		te.onReceivePacketServer((byte) 0, "17", player);
		te.onReceivePacketServer((byte) 0, null, player);
		helper.assertTrue(te.floor == 1, "a non-number was stored");
		te.onReceivePacketServer((byte) 0, EvFloorTE.MAX_FLOOR, player);
		helper.assertTrue(te.floor == EvFloorTE.MAX_FLOOR, "the top floor was refused");
		te.onReceivePacketServer((byte) 0, EvFloorTE.MIN_FLOOR, player);
		helper.assertTrue(te.floor == EvFloorTE.MIN_FLOOR, "the bottom floor was refused");

		player.setPos(at.x + 100, at.y, at.z);
		te.onReceivePacketServer((byte) 0, 5, player);
		helper.assertTrue(te.floor == EvFloorTE.MIN_FLOOR, "a player a hundred blocks away set the floor");
		helper.assertTrue(!te.editors.contains(player.getUUID()), "and keeps the screen");
		helper.succeed();
	}

	/** The boiler's chain length: its screen open, in reach, a positive int. */
	@GameTest(template = EMPTY)
	public static void theBoilersChainLengthIsValidated(GameTestHelper helper) {

		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, LeafiaBlocks.hp_boiler.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, net.minecraft.core.Direction.NORTH));
		HPBoilerTE boiler = (HPBoilerTE) helper.getBlockEntity(pos);
		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(boiler.getBlockPos());
		player.setPos(at.x, at.y, at.z);
		// A water boiler's chain is one rung deep whatever is asked, so the length itself shows
		// nothing. What shows is whether setFluid ran: it rebuilds the tanks when the output is not
		// what the chain makes, so the output is set to NONE and a packet that gets through puts
		// STEAM back.
		boiler.output = new com.hbm.inventory.fluid.tank.FluidTank(Fluids.NONE, 1000);

		// no screen open
		player.containerMenu = player.inventoryMenu;
		boiler.onReceivePacketServer((byte) 0, 1, player);
		helper.assertTrue(boiler.output.getTankType() == Fluids.NONE, "a player with no screen open changed the chain");

		player.containerMenu = new HPBoilerMenu(1, player.getInventory(), boiler);
		for(int bad : new int[] {0, -1, Integer.MIN_VALUE}) {
			boiler.onReceivePacketServer((byte) 0, bad, player);
			helper.assertTrue(boiler.output.getTankType() == Fluids.NONE, "a chain length of " + bad + " was stored");
		}
		boiler.onReceivePacketServer((byte) 0, "2", player);
		boiler.onReceivePacketServer((byte) 0, 2L, player);
		boiler.onReceivePacketServer((byte) 7, 1, player);
		helper.assertTrue(boiler.output.getTankType() == Fluids.NONE, "a non-int or another key changed the chain");

		player.setPos(at.x + 100, at.y, at.z);
		boiler.onReceivePacketServer((byte) 0, 1, player);
		helper.assertTrue(boiler.output.getTankType() == Fluids.NONE, "a player a hundred blocks away changed the chain");

		// the boiler's other screen: a different boiler's menu is not this boiler's
		// (a menu that found no boiler stands in for a second boiler: the template is only 5 blocks wide)
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new HPBoilerMenu(2, player.getInventory(), (HPBoilerTE) null);
		boiler.onReceivePacketServer((byte) 0, 1, player);
		helper.assertTrue(boiler.output.getTankType() == Fluids.NONE, "another boiler's screen let a player change this one");

		// the valid one
		player.containerMenu = new HPBoilerMenu(3, player.getInventory(), boiler);
		boiler.onReceivePacketServer((byte) 0, 1, player);
		helper.assertTrue(boiler.output.getTankType() == Fluids.STEAM, "the valid length did not act, output is " + boiler.output.getTankType().getName());
		helper.succeed();
	}

	/** The filter's list: its screen open, in reach, and a list the screen could have saved. */
	@GameTest(template = EMPTY)
	public static void theFilterListIsValidated(GameTestHelper helper) {

		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, LeafiaBlocks.regex_filter.get().defaultBlockState().setValue(RegexFilterBlock.FACING, net.minecraft.core.Direction.EAST));
		RegexFilterTE te = (RegexFilterTE) helper.getBlockEntity(pos);
		// not a ServerPlayer: the filter's menu sends its first sync to its player, and a mock connection refuses it
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		Vec3 at = Vec3.atCenterOf(te.getBlockPos());
		player.setPos(at.x, at.y, at.z);

		CompoundTag good = list(entry("RESOURCE_ID", "minecraft:stone", false));

		// no screen
		te.onReceivePacketServer((byte) 0, good, player);
		helper.assertTrue(te.filters.isEmpty(), "a player with no screen open set the list");

		player.containerMenu = new RegexFilterMenu(1, player.getInventory(), te);
		te.listeners.clear();

		// bad lists, one flaw each
		List<CompoundTag> bad = new ArrayList<>();
		bad.add(list(entry("NOT_A_TYPE", "a", false)));
		bad.add(list(entry("RESOURCE_ID", "(unclosed", false)));
		bad.add(list(entry("RESOURCE_ID", "a".repeat(RegexFilterTE.MAX_PATTERN + 1), false)));
		bad.add(list(entry("RESOURCE_ID", "ok", false), entry("RESOURCE_ID", "[", true)));
		CompoundTag many = new CompoundTag();
		ListTag tooMany = new ListTag();
		for(int i = 0; i <= RegexFilterTE.MAX_FILTERS; i++) tooMany.add(entry("RESOURCE_ID", "n" + i, false));
		many.put("a", tooMany);
		bad.add(many);
		CompoundTag strings = new CompoundTag();
		ListTag notCompounds = new ListTag();
		notCompounds.add(StringTag.valueOf("RESOURCE_ID"));
		strings.put("a", notCompounds);
		bad.add(strings);
		CompoundTag noType = new CompoundTag();
		ListTag untyped = new ListTag();
		CompoundTag bare = new CompoundTag();
		bare.putString("value", "a");
		untyped.add(bare);
		noType.put("a", untyped);
		bad.add(noType);

		for(CompoundTag tag : bad) {
			te.onReceivePacketServer((byte) 0, tag, player);
			helper.assertTrue(te.filters.isEmpty(), "a list the screen could not have made was stored: " + tag);
		}
		te.onReceivePacketServer((byte) 0, "not a tag", player);
		te.onReceivePacketServer((byte) 1, good, player);
		helper.assertTrue(te.filters.isEmpty(), "a non-tag or another key changed the list");

		player.setPos(at.x + 100, at.y, at.z);
		te.onReceivePacketServer((byte) 0, good, player);
		helper.assertTrue(te.filters.isEmpty(), "a player a hundred blocks away set the list");

		// a different filter's screen
		// (a menu that found no filter stands in for a second filter: the template is only 5 blocks wide)
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new RegexFilterMenu(2, player.getInventory(), (RegexFilterTE) null);
		te.onReceivePacketServer((byte) 0, good, player);
		helper.assertTrue(te.filters.isEmpty(), "another filter's screen let a player set this one's list");

		// the valid list, at the size limit
		player.containerMenu = new RegexFilterMenu(3, player.getInventory(), te);
		te.listeners.clear();
		te.onReceivePacketServer((byte) 0, good, player);
		helper.assertTrue(te.filters.size() == 1 && te.filters.get(0).regex.equals("minecraft:stone"), "the valid list did not act");
		CompoundTag full = new CompoundTag();
		ListTag fullList = new ListTag();
		for(int i = 0; i < RegexFilterTE.MAX_FILTERS; i++) fullList.add(entry("ORE_DICT", "p" + i, i % 2 == 0));
		full.put("a", fullList);
		te.onReceivePacketServer((byte) 0, full, player);
		helper.assertTrue(te.filters.size() == RegexFilterTE.MAX_FILTERS, "a list of exactly the limit was refused");
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static CompoundTag entry(String type, String value, boolean blacklist) {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", type);
		tag.putString("value", value);
		tag.putBoolean("blacklist", blacklist);
		return tag;
	}

	private static CompoundTag list(CompoundTag... entries) {
		ListTag list = new ListTag();
		for(CompoundTag entry : entries) list.add(entry);
		CompoundTag tag = new CompoundTag();
		tag.put("a", list);
		return tag;
	}

	private static List<String> history(Player player) {
		return AdvisorItem.getWarningHistory(player, new ArrayList<>());
	}

	private static boolean advisorWarn(String key, ServerPlayer player) {
		AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.WARN.id);
		packet.warningKey = key;
		return runPacket(packet, player, PacketFlow.SERVERBOUND);
	}

	private static boolean dismiss(int index, ServerPlayer player) {
		AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.DISMISS.id);
		packet.dismissIndex = index;
		return runPacket(packet, player, PacketFlow.SERVERBOUND);
	}

	private static boolean fuzzy(String name, ServerPlayer player, PacketFlow flow) {
		FuzzyIdentifierPacket packet = new FuzzyIdentifierPacket();
		packet.fluidRsc = name;
		return runPacket(packet, player, flow);
	}

	private static com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioUIPacket radioUi(int up, int down, int idle) {
		var packet = new com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioUIPacket();
		packet.upOrdinal = up;
		packet.downOrdinal = down;
		packet.idleOrdinal = idle;
		return packet;
	}

	private static EvFloorTE floor(GameTestHelper helper, BlockPos core) {
		helper.setBlock(core, LeafiaBlocks.elevator_s6floor.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, net.minecraft.core.Direction.NORTH));
		EvFloorTE te = (EvFloorTE) helper.getLevel().getBlockEntity(helper.absolutePos(core));
		helper.assertTrue(te != null && te.floor == 1, "the landing is not floor 1");
		return te;
	}

	private static boolean press(ElevatorEntity cabin, String id, ServerPlayer player, PacketFlow flow) {
		return run(LeafiaCustomPacket.__start(new EvButtonInteractPacket(cabin, id, InteractionHand.MAIN_HAND)), player, flow);
	}

	private static boolean syncRequest(int entityId, ServerPlayer player, PacketFlow flow) {
		return run(LeafiaCustomPacket.__start(new EvSyncRequestPacket() {
			@Override public void encode(LeafiaBuf buf) { buf.writeInt(entityId); }
		}), player, flow);
	}

	private static boolean modify(ElevatorEntity cabin, int mode, int target, ServerPlayer player,
			Consumer<EvButtonModifyPacket> fill) {
		EvButtonModifyPacket packet = new EvButtonModifyPacket();
		packet.localEntity = cabin;
		packet.localMode = mode;
		packet.localTarget = target;
		fill.accept(packet);
		return runPacket(packet, player, PacketFlow.SERVERBOUND);
	}

	/** Puts the player the given number of blocks east of the cabin, on its level. */
	private static void place(ServerPlayer player, ElevatorEntity cabin, double distance) {
		player.setPos(cabin.getX() + distance, cabin.getY(), cabin.getZ());
	}

	private static boolean runPacket(LeafiaCustomPacketEncoder encoder, ServerPlayer player, PacketFlow flow) {
		return run(LeafiaCustomPacket.__start(encoder), player, flow);
	}

	/**
	 * Through the real framing and decoder, then the callback, as the handler does after its own checks.
	 *
	 * @return whether the handler got as far as <em>replying</em>: a mock connection negotiated no
	 *         channels, so a reply is refused with an exception naming the payload, and that refusal is
	 *         the evidence the request was honoured. The refusal is swallowed here.
	 */
	private static boolean run(LeafiaCustomPacket packet, ServerPlayer player, PacketFlow flow) {
		var opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		if(callback == null) return false;
		try {
			callback.accept(context(player, flow, new Counter()));
			return false;
		} catch(UnsupportedOperationException e) {
			if(e.getMessage() != null && e.getMessage().contains("leafia:custom")) return true;
			throw e;
		}
	}

	private static final class Counter {
		int queued = 0;
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static ElevatorEntity cabin(GameTestHelper helper) {
		ElevatorEntity cabin = (ElevatorEntity) LeafiaEntities.ELEVATOR.get().create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		cabin.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(cabin);
		return cabin;
	}

	/** Four buttons: floors 1, 2 and 3, and an open button. */
	private static ElevatorEntity cabinWithButtons(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		cabin.buttons.clear();
		cabin.buttons.add(new FloorButton(cabin, 1, "1", -2, 14));
		cabin.buttons.add(new FloorButton(cabin, 2, "2", 1, 14));
		cabin.buttons.add(new FloorButton(cabin, 3, "3", -2, 17));
		cabin.buttons.add(new ElevatorEntity.OpenButton(cabin, 1, 9));
		return cabin;
	}

	private static IPayloadContext context(Player player, PacketFlow flow, Counter counter) {
		return new IPayloadContext() {
			@Override public net.minecraft.network.protocol.common.ClientCommonPacketListener listener() {
				throw new UnsupportedOperationException();
			}
			@Override public Player player() { return player; }
			@Override public CompletableFuture<Void> enqueueWork(Runnable task) {
				counter.queued++;
				task.run();
				return CompletableFuture.completedFuture(null);
			}
			@Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
				counter.queued++;
				return CompletableFuture.completedFuture(task.get());
			}
			@Override public PacketFlow flow() { return flow; }
			@Override public void handle(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
				throw new UnsupportedOperationException();
			}
			@Override public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
