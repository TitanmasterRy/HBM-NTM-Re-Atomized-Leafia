package com.leafia.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.custom_hbm.util.LCETuple.Triplet;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.gui.EvCabinMenu;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.ElevatorMusic;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioSyncRequestPacket;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioUIPacket;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.Selection;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.init.LeafiaEntities;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The elevator radio (D-1088): the item and what it stores, its two packets, the cabin's reading
 * of it from slot 7, and the controller that turns the cabin's {@code MUSIC} parameter into a
 * looping sound.
 *
 * <h2>What is not asserted here, and how it was checked instead</h2>
 *
 * <b>Nothing is heard.</b> A GameTest server has no client, no sound engine and no screen, and
 * the sandbox this was built in has no audio device either (OpenAL fails to open). What is pinned
 * is every decision on the way to a sound: which track a radio chooses, what the cabin asks the
 * audio proxy for and with what, when it starts, moves and stops it. The proxy's far end - the
 * {@code LCEAudioWrapperClientStartStop} that builds a vanilla sound instance - is checked only by
 * the client log (no sound-registry error, no missing file) and by reading it against upstream. The
 * two screens are checked by screenshot.
 *
 * <h2>The audio proxy is replaced for the length of one test</h2>
 *
 * {@link #withRecorder} installs a factory that records every request and hands back a wrapper
 * that records every call, and puts the old factory back in a {@code finally}: the factory is a
 * process-wide static, and a test that left one installed would make every later cabin test talk
 * to a recorder.
 *
 * <h2>Rule 10, once</h2>
 *
 * A cabin on a GameTest server ticks, and with no pulley its {@code tickServer} writes
 * {@code MUSIC = -1} every tick. Every test that sets {@code MUSIC} therefore reads the result in
 * the same tick body and never waits.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaEvRadioTests {

	private static final String EMPTY = "empty";

	// ------------------------------------------------------------------ the item

	/** Registered under the elevators switch, and a stack of 64 as upstream's {@code AddonItemBaked} default. */
	@GameTest(template = EMPTY)
	public static void theRadioIsRegisteredAndStacks(GameTestHelper helper) {
		helper.assertTrue(LeafiaItems.ev_radio != null, "the radio is not registered with the elevators switch on");
		helper.assertTrue(LeafiaItems.ev_radio.getId().toString().equals("leafia:ev_radio"),
				"registry name " + LeafiaItems.ev_radio.getId());
		helper.assertTrue(LeafiaItems.ev_radio.get().getDefaultMaxStackSize() == 64,
				"upstream sets no stack limit, so 64; got " + LeafiaItems.ev_radio.get().getDefaultMaxStackSize());
		helper.succeed();
	}

	/**
	 * The two tracks, in upstream's order, with upstream's text and sound events. The order is the
	 * ordinals the cabin's {@code MUSIC} carries, and the order of the list screen.
	 */
	@GameTest(template = EMPTY)
	public static void theTwoTracksAreUpstreams(GameTestHelper helper) {
		ElevatorMusic[] tracks = ElevatorMusic.values();
		helper.assertTrue(tracks.length == 2, "two tracks, got " + tracks.length);

		helper.assertTrue(tracks[0] == ElevatorMusic.LOCAL_FORECAST && tracks[1] == ElevatorMusic.ELEVATOR_JAM,
				"order is Local Forecast then Elevator Jam");
		helper.assertTrue(tracks[0].title.equals("Local Forecast") && tracks[0].artist.equals("Kevin Macleod"),
				"track 0 is " + tracks[0].title + " / " + tracks[0].artist);
		helper.assertTrue(tracks[1].title.equals("Elevator Jam") && tracks[1].artist.equals("LSPLASH"),
				"track 1 is " + tracks[1].title + " / " + tracks[1].artist);

		helper.assertTrue(tracks[0].loop() == LeafiaSounds.local_forecast.get(), "Local Forecast loops the wrong event");
		helper.assertTrue(tracks[0].end() == null, "Local Forecast has no end clip");
		helper.assertTrue(tracks[1].loop() == LeafiaSounds.elevator_jam_loop.get(), "Elevator Jam loops the wrong event");
		helper.assertTrue(tracks[1].end() == LeafiaSounds.elevator_jam_end.get(), "Elevator Jam ends on the wrong event");
		helper.succeed();
	}

	/**
	 * {@code EnumUtil.grabEnumSafely} folds any int into the enum by {@code abs(i % length)}, so
	 * <b>-1 is the last track, not none</b>. Every caller tests for -1 first; this pins the fold so
	 * nobody "simplifies" one of those tests away.
	 */
	@GameTest(template = EMPTY)
	public static void anyOrdinalFoldsOntoATrack(GameTestHelper helper) {
		helper.assertTrue(ElevatorMusic.ofOrdinal(0) == ElevatorMusic.LOCAL_FORECAST, "0");
		helper.assertTrue(ElevatorMusic.ofOrdinal(1) == ElevatorMusic.ELEVATOR_JAM, "1");
		helper.assertTrue(ElevatorMusic.ofOrdinal(2) == ElevatorMusic.LOCAL_FORECAST, "2 wraps to 0");
		helper.assertTrue(ElevatorMusic.ofOrdinal(7) == ElevatorMusic.ELEVATOR_JAM, "7 wraps to 1");
		helper.assertTrue(ElevatorMusic.ofOrdinal(-1) == ElevatorMusic.ELEVATOR_JAM, "-1 is NOT none: it folds onto 1");
		helper.assertTrue(ElevatorMusic.ofOrdinal(-128) == ElevatorMusic.LOCAL_FORECAST, "-128 folds onto 0");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the stack's tag

	/** A selection is stored as the constants' <em>names</em>, under upstream's three keys. */
	@GameTest(template = EMPTY)
	public static void theTagHoldsNamesUnderUpDownIdle(GameTestHelper helper) {
		ItemStack radio = radio();
		EvRadioItem.write(radio, 1, 0, 1);

		CompoundTag tag = radio.get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(tag.getString("up").equals("ELEVATOR_JAM"), "up is " + tag.getString("up"));
		helper.assertTrue(tag.getString("down").equals("LOCAL_FORECAST"), "down is " + tag.getString("down"));
		helper.assertTrue(tag.getString("idle").equals("ELEVATOR_JAM"), "idle is " + tag.getString("idle"));
		helper.assertTrue(tag.size() == 3, "and nothing else, got " + tag.getAllKeys());

		Selection back = EvRadioItem.read(radio);
		helper.assertTrue(back.equals(new Selection(1, 0, 1)), "reads back as " + back);
		helper.succeed();
	}

	/** -1 writes no key, and an all-none selection leaves an empty tag rather than no tag. */
	@GameTest(template = EMPTY)
	public static void noneWritesNoKey(GameTestHelper helper) {
		ItemStack radio = radio();
		EvRadioItem.write(radio, 0, -1, -1);
		CompoundTag tag = radio.get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(tag.contains("up") && !tag.contains("down") && !tag.contains("idle"),
				"only up is set: " + tag.getAllKeys());

		EvRadioItem.write(radio, -1, -1, -1);
		helper.assertTrue(radio.has(DataComponents.CUSTOM_DATA), "upstream leaves an empty compound, not none");
		helper.assertTrue(radio.get(DataComponents.CUSTOM_DATA).copyTag().isEmpty(), "and it is empty");
		helper.assertTrue(EvRadioItem.read(radio).equals(Selection.NONE), "which reads as all none");
		helper.succeed();
	}

	/** Writing replaces the whole tag, as upstream's {@code setTagCompound} does. */
	@GameTest(template = EMPTY)
	public static void writingReplacesTheWholeTag(GameTestHelper helper) {
		ItemStack radio = radio();
		CompoundTag existing = new CompoundTag();
		existing.putString("down", "LOCAL_FORECAST");
		existing.putString("note", "kept?");
		radio.set(DataComponents.CUSTOM_DATA, CustomData.of(existing));

		EvRadioItem.write(radio, 1, -1, -1);
		CompoundTag tag = radio.get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(!tag.contains("note"), "an unrelated key survived the write");
		helper.assertTrue(!tag.contains("down"), "a choice the new selection leaves out survived the write");
		helper.assertTrue(tag.getString("up").equals("ELEVATOR_JAM"), "up not written");
		helper.succeed();
	}

	/** An absent key, a name that is no track and a value that is not a string all read as none. */
	@GameTest(template = EMPTY)
	public static void aBadTagReadsAsNone(GameTestHelper helper) {
		ItemStack radio = radio();
		helper.assertTrue(EvRadioItem.read(radio).equals(Selection.NONE), "a radio with no tag is all none");

		CompoundTag tag = new CompoundTag();
		tag.putString("up", "NO_SUCH_TRACK");
		tag.putInt("down", 1);
		tag.putString("idle", "local_forecast");
		radio.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		Selection read = EvRadioItem.read(radio);
		helper.assertTrue(read.equals(Selection.NONE),
				"an unknown name, an int and a wrong-case name are all none; got " + read);

		tag.putString("idle", "LOCAL_FORECAST");
		radio.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		helper.assertTrue(EvRadioItem.read(radio).equals(new Selection(-1, -1, 0)),
				"and one good key among them still reads: " + EvRadioItem.read(radio));
		helper.succeed();
	}

	// ------------------------------------------------------------------ right click

	/**
	 * On the server a right click does nothing but succeed - the screen is the client's. Neither
	 * hand is refused (upstream's does not), and no menu is opened.
	 */
	@GameTest(template = EMPTY)
	public static void aServerRightClickOpensNothing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		ItemStack radio = radio();
		EvRadioItem.write(radio, 1, 0, -1);
		player.setItemInHand(InteractionHand.MAIN_HAND, radio);

		for(InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if(held.isEmpty()) player.setItemInHand(hand, radio.copy());
			InteractionResultHolder<ItemStack> result = radio.getItem().use(helper.getLevel(), player, hand);
			helper.assertTrue(result.getResult().consumesAction(), hand + " was refused");
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a menu was opened for " + hand);
		}
		helper.assertTrue(EvRadioItem.read(radio).equals(new Selection(1, 0, -1)), "the right click changed the tag");
		helper.succeed();
	}

	// ------------------------------------------------------------------ packets

	/** Ids 15 and 16, appended after the advisor's pair; ids never move. */
	@GameTest(template = EMPTY)
	public static void theRadioPacketsAreAppendedAtFifteenAndSixteen(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.EV_RADIO_UI == 15, "EV_RADIO_UI is " + LeafiaPacketTypes.EV_RADIO_UI);
		helper.assertTrue(LeafiaPacketTypes.EV_RADIO_REQUEST == 16,
				"EV_RADIO_REQUEST is " + LeafiaPacketTypes.EV_RADIO_REQUEST);
		helper.assertTrue(LeafiaPacketTypes.ADVISOR == 14, "the advisor's pair moved: ADVISOR is " + LeafiaPacketTypes.ADVISOR);
		helper.assertTrue(LeafiaPacketTypes.byId(15) instanceof EvRadioUIPacket, "id 15 is not the UI packet");
		helper.assertTrue(LeafiaPacketTypes.byId(16) instanceof EvRadioSyncRequestPacket, "id 16 is not the request");
		helper.succeed();
	}

	/** The UI packet is a 16-bit header and three signed bytes; the request is the header alone. */
	@GameTest(template = EMPTY)
	public static void theWireSizesAreUpstreams(GameTestHelper helper) {
		EvRadioUIPacket ui = new EvRadioUIPacket();
		ui.upOrdinal = 1;
		ui.downOrdinal = -1;
		ui.idleOrdinal = 0;
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(ui);
		helper.assertTrue(packet != null && packet.bitLength() == 16 + 3 * 8,
				"the UI packet is " + (packet == null ? "dropped" : packet.bitLength()) + " bits, expected 40");

		LeafiaCustomPacket request = LeafiaCustomPacket.__start(new EvRadioSyncRequestPacket());
		helper.assertTrue(request != null && request.bitLength() == 16,
				"the request is " + (request == null ? "dropped" : request.bitLength()) + " bits, expected 16");
		helper.succeed();
	}

	/** The answer to the screen's close: the main-hand radio takes the three choices. */
	@GameTest(template = EMPTY)
	public static void aClosingScreenWritesTheMainHandRadio(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		ItemStack radio = radio();
		player.setItemInHand(InteractionHand.MAIN_HAND, radio);

		deliver(ui(1, -1, 0), player, PacketFlow.SERVERBOUND);

		helper.assertTrue(EvRadioItem.read(radio).equals(new Selection(1, -1, 0)),
				"the radio holds " + EvRadioItem.read(radio));
		helper.succeed();
	}

	/**
	 * Only the main hand's radio is ever written - upstream's {@code getHeldItemMainhand} - so a
	 * radio in the off hand, or a different item in the main hand, is left alone. <b>This is the
	 * upstream behaviour that makes an off-hand radio's screen load forever</b> (D-1088).
	 */
	@GameTest(template = EMPTY)
	public static void onlyTheMainHandIsWritten(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		ItemStack offhand = radio();
		player.setItemInHand(InteractionHand.OFF_HAND, offhand);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));

		deliver(ui(1, 1, 1), player, PacketFlow.SERVERBOUND);

		helper.assertTrue(!offhand.has(DataComponents.CUSTOM_DATA), "the off-hand radio was written");
		helper.assertTrue(!player.getMainHandItem().has(DataComponents.CUSTOM_DATA), "a stick was given a tag");
		helper.succeed();
	}

	/** A packet arriving on the wrong side does nothing: the client branch is not the server's. */
	@GameTest(template = EMPTY)
	public static void aClientboundPacketDoesNotWriteTheStack(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		ItemStack radio = radio();
		player.setItemInHand(InteractionHand.MAIN_HAND, radio);

		deliver(ui(1, 1, 1), player, PacketFlow.CLIENTBOUND);

		helper.assertTrue(!radio.has(DataComponents.CUSTOM_DATA),
				"a clientbound packet wrote the radio (and on a dedicated server it must not touch a screen)");
		helper.succeed();
	}

	/**
	 * The packet is untrusted. Upstream folded an ordinal that is no track onto one by modulo, so
	 * none of the 256 values threw; the owner's answer to D-1093 #4 (D-1094) is to refuse them
	 * instead. A byte from -1 (none) to the last track is written; every other byte, in any of the
	 * three places, leaves the radio exactly as it was.
	 */
	@GameTest(template = EMPTY)
	public static void everyByteIsSafe(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		int tracks = ElevatorMusic.values().length;
		for(int b = -128; b < 128; b++) {
			ItemStack radio = radio();
			player.setItemInHand(InteractionHand.MAIN_HAND, radio);
			deliver(ui(b, b, b), player, PacketFlow.SERVERBOUND);

			Selection s = EvRadioItem.read(radio);
			Selection expected = b >= -1 && b < tracks ? new Selection(b, b, b) : Selection.NONE;
			helper.assertTrue(s.equals(expected), "byte " + b + " wrote " + s + ", expected " + expected);
			if(expected == Selection.NONE)
				helper.assertTrue(!radio.has(DataComponents.CUSTOM_DATA), "byte " + b + " touched the radio's tag");
		}

		// one bad place spoils the packet: the other two are not written either
		ItemStack radio = radio();
		player.setItemInHand(InteractionHand.MAIN_HAND, radio);
		deliver(ui(0, tracks, 0), player, PacketFlow.SERVERBOUND);
		helper.assertTrue(!radio.has(DataComponents.CUSTOM_DATA), "a packet with one bad ordinal was half-applied");
		helper.succeed();
	}

	/**
	 * The request asks the server for the main-hand radio's choices and the server answers with
	 * the UI packet. A mock connection negotiated no channels, so "answered" is read as the server
	 * having tried to send {@code leafia:custom} (see {@code LeafiaEvCabinGuiTests}).
	 */
	@GameTest(template = EMPTY)
	public static void aRequestIsAnsweredOnlyForARadio(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(!reachedSending(() -> deliver(new EvRadioSyncRequestPacket(), player, PacketFlow.SERVERBOUND)),
				"a request with a stick in hand was answered");

		player.setItemInHand(InteractionHand.MAIN_HAND, radio());
		helper.assertTrue(reachedSending(() -> deliver(new EvRadioSyncRequestPacket(), player, PacketFlow.SERVERBOUND)),
				"a request with a radio in hand was not answered");

		helper.assertTrue(!reachedSending(() -> deliver(new EvRadioSyncRequestPacket(), player, PacketFlow.CLIENTBOUND)),
				"a clientbound request was answered");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the cabin's reading

	/** Slot 7's radio is read into (up, down, idle) ordinals; anything else, or nothing, is (-1, -1, -1). */
	@GameTest(template = EMPTY)
	public static void theCabinReadsTheRadioInSlotSeven(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);

		assertMusic(helper, cabin.getMusic(), -1, -1, -1, "an empty cabin");

		ItemStack radio = radio();
		EvRadioItem.write(radio, 1, 0, 1);
		cabin.inventory.setStackInSlot(7, radio);
		assertMusic(helper, cabin.getMusic(), 1, 0, 1, "a radio set up/down/idle to 1/0/1");

		ItemStack partial = radio();
		EvRadioItem.write(partial, -1, 1, -1);
		cabin.inventory.setStackInSlot(7, partial);
		assertMusic(helper, cabin.getMusic(), -1, 1, -1, "a radio with only down set");

		cabin.inventory.setStackInSlot(7, radio());
		assertMusic(helper, cabin.getMusic(), -1, -1, -1, "a radio with no tag");

		ItemStack stone = new ItemStack(Items.STONE);
		stone.set(DataComponents.CUSTOM_DATA, radioTag(1, 1, 1));
		cabin.inventory.setStackInSlot(7, stone);
		assertMusic(helper, cabin.getMusic(), -1, -1, -1, "a stone wearing a radio's tag");

		// Only slot 7 is the radio slot.
		cabin.inventory.setStackInSlot(7, ItemStack.EMPTY);
		ItemStack elsewhere = radio();
		EvRadioItem.write(elsewhere, 1, 1, 1);
		cabin.inventory.setStackInSlot(6, elsewhere);
		assertMusic(helper, cabin.getMusic(), -1, -1, -1, "a radio in slot 6");

		cabin.discard();
		helper.succeed();
	}

	/** Shift-clicking a radio from the player's inventory fills slot 7, and the cabin then plays what it holds. */
	@GameTest(template = EMPTY)
	public static void aRadioShiftClickedInReachesSlotSeven(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		ServerPlayer player = mockPlayer(helper);
		EvCabinMenu menu = new EvCabinMenu(1, player.getInventory(), cabin);

		ItemStack radio = radio();
		EvRadioItem.write(radio, 0, 1, 0);
		player.getInventory().setItem(9, radio);
		menu.quickMoveStack(player, 8);

		helper.assertTrue(cabin.inventory.getStackInSlot(7).getItem() instanceof EvRadioItem,
				"the radio did not land in slot 7: " + cabin.inventory.getStackInSlot(7));
		assertMusic(helper, cabin.getMusic(), 0, 1, 0, "the radio that was shift-clicked in");
		cabin.discard();
		helper.succeed();
	}

	/** The radio's tag is in the cabin's saved inventory, so a reloaded cabin plays the same music. */
	@GameTest(template = EMPTY)
	public static void theChoicesSurviveTheCabinsSave(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		ItemStack radio = radio();
		EvRadioItem.write(radio, 1, 1, 0);
		cabin.inventory.setStackInSlot(7, radio);

		CompoundTag saved = cabin.inventory.serializeNBT(helper.getLevel().registryAccess());
		ElevatorEntity other = cabin(helper);
		other.inventory.deserializeNBT(helper.getLevel().registryAccess(), saved);
		assertMusic(helper, other.getMusic(), 1, 1, 0, "a cabin loaded from the saved inventory");

		cabin.discard();
		other.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ the music controller

	/**
	 * The controller asks the proxy for upstream's exact request - the track's loop and its end
	 * clip, no start clip, the records mixer, volume 1, pitch 1, at the cabin - sets it looping,
	 * starts it once, and moves it with the cabin every call after.
	 */
	@GameTest(template = EMPTY)
	public static void aTrackIsRequestedWithUpstreamsArguments(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		withRecorder(rec -> {
			cabin.getEntityData().set(ElevatorEntity.MUSIC, 1);
			cabin.updateMusic();

			helper.assertTrue(rec.requests.size() == 1, "one request, got " + rec.requests.size());
			Request r = rec.requests.get(0);
			helper.assertTrue(r.sound == LeafiaSounds.elevator_jam_loop.get(), "loop is " + r.sound);
			helper.assertTrue(r.start == null, "no start clip, got " + r.start);
			helper.assertTrue(r.stop == LeafiaSounds.elevator_jam_end.get(), "end clip is " + r.stop);
			helper.assertTrue(r.cat == SoundSource.RECORDS, "mixer is " + r.cat);
			helper.assertTrue(r.volume == 1f && r.pitch == 1f, "volume " + r.volume + " pitch " + r.pitch);
			helper.assertTrue(r.x == (float) cabin.getX() && r.y == (float) cabin.getY() && r.z == (float) cabin.getZ(),
					"requested at " + r.x + "," + r.y + "," + r.z);

			Recorder music = rec.made.get(0);
			helper.assertTrue(music.log.equals(List.of("looped true", "start", "move")),
					"the wrapper was driven as " + music.log);

			cabin.updateMusic();
			helper.assertTrue(rec.requests.size() == 1, "a second tick built a second sound");
			helper.assertTrue(music.log.equals(List.of("looped true", "start", "move", "move")),
					"the second tick should only move it: " + music.log);
			helper.assertTrue(cabin.getMusicHandle() == music, "the cabin does not hold what the proxy built");
		});
		cabin.discard();
		helper.succeed();
	}

	/** Local Forecast has no end clip, so it asks for none. */
	@GameTest(template = EMPTY)
	public static void theFirstTrackAsksForNoEndClip(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		withRecorder(rec -> {
			cabin.getEntityData().set(ElevatorEntity.MUSIC, 0);
			cabin.updateMusic();
			Request r = rec.requests.get(0);
			helper.assertTrue(r.sound == LeafiaSounds.local_forecast.get(), "loop is " + r.sound);
			helper.assertTrue(r.stop == null, "Local Forecast has no end clip, asked for " + r.stop);
		});
		cabin.discard();
		helper.succeed();
	}

	/**
	 * -1 means silence: nothing is requested for it, and going to -1 stops what played (which is
	 * what plays the end clip).
	 */
	@GameTest(template = EMPTY)
	public static void minusOneIsSilenceAndStopsWhatPlayed(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		withRecorder(rec -> {
			cabin.getEntityData().set(ElevatorEntity.MUSIC, -1);
			cabin.updateMusic();
			helper.assertTrue(rec.requests.isEmpty(), "-1 asked the proxy for a sound");
			helper.assertTrue(cabin.getMusicHandle() == null, "-1 left a handle");

			cabin.getEntityData().set(ElevatorEntity.MUSIC, 1);
			cabin.updateMusic();
			Recorder music = rec.made.get(0);

			cabin.getEntityData().set(ElevatorEntity.MUSIC, -1);
			cabin.updateMusic();
			helper.assertTrue(music.log.contains("stop"), "going to -1 did not stop the music: " + music.log);
			helper.assertTrue(cabin.getMusicHandle() == null, "the handle outlived the stop");
			helper.assertTrue(rec.requests.size() == 1, "going to -1 asked for a new sound");
		});
		cabin.discard();
		helper.succeed();
	}

	/** A different track stops the old one and starts the new one in the same call. */
	@GameTest(template = EMPTY)
	public static void changingTrackStopsOneAndStartsTheOther(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		withRecorder(rec -> {
			cabin.getEntityData().set(ElevatorEntity.MUSIC, 1);
			cabin.updateMusic();
			cabin.getEntityData().set(ElevatorEntity.MUSIC, 0);
			cabin.updateMusic();

			helper.assertTrue(rec.requests.size() == 2, "two requests, got " + rec.requests.size());
			helper.assertTrue(rec.made.get(0).log.contains("stop"), "the first track was not stopped");
			helper.assertTrue(rec.made.get(1).log.get(1).equals("start"), "the second track did not start");
			helper.assertTrue(rec.requests.get(1).sound == LeafiaSounds.local_forecast.get(), "the second is not Local Forecast");
			helper.assertTrue(cabin.getMusicHandle() == rec.made.get(1), "the cabin holds the old one");
		});
		cabin.discard();
		helper.succeed();
	}

	/**
	 * Every way a cabin leaves the world stops its music: removal, a client's removal, and leaving
	 * the level. The first way uses a cabin that was never added to the level, because removing one
	 * that was also fires {@code onRemovedFromLevel} and would let the third way hide the first.
	 */
	@GameTest(template = EMPTY)
	public static void everyWayOutStopsTheMusic(GameTestHelper helper) {
		for(int way = 0; way < 3; way++) {
			ElevatorEntity cabin = way == 0 ? (ElevatorEntity) LeafiaEntities.ELEVATOR.get().create(helper.getLevel())
					: cabin(helper);
			int which = way;
			withRecorder(rec -> {
				cabin.getEntityData().set(ElevatorEntity.MUSIC, 1);
				cabin.updateMusic();
				Recorder music = rec.made.get(0);

				switch(which) {
					case 0 -> cabin.discard();
					case 1 -> cabin.onClientRemoval();
					default -> cabin.onRemovedFromLevel();
				}

				helper.assertTrue(music.log.contains("stop"), "way " + which + " left the music playing: " + music.log);
				helper.assertTrue(cabin.getMusicHandle() == null, "way " + which + " kept the handle");
			});
			cabin.discard();
		}
		helper.succeed();
	}

	/** With no client the proxy answers with a wrapper that does nothing, and the controller runs on it without a fault. */
	@GameTest(template = EMPTY)
	public static void withoutAClientTheMusicIsASilentNoOp(GameTestHelper helper) {
		ElevatorEntity cabin = cabin(helper);
		LCEAudioProxy.Factory previous = LCEAudioProxy.factory;
		LCEAudioProxy.factory = null;
		try {
			cabin.getEntityData().set(ElevatorEntity.MUSIC, 1);
			cabin.updateMusic();
			LCEAudioWrapper handle = cabin.getMusicHandle();
			helper.assertTrue(handle != null && handle.getClass() == LCEAudioWrapper.class,
					"expected the no-op base, got " + handle);
			cabin.updateMusic();
			cabin.getEntityData().set(ElevatorEntity.MUSIC, -1);
			cabin.updateMusic();
			helper.assertTrue(cabin.getMusicHandle() == null, "the handle outlived the stop");
		} finally {
			LCEAudioProxy.factory = previous;
		}
		cabin.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ assets

	/** The panels are 256 square (what {@code GuiGraphics.blit}'s default sheet size assumes) and the icon 16. */
	@GameTest(template = EMPTY)
	public static void theTexturesAreInTheJar(GameTestHelper helper) throws Exception {
		assertPng(helper, "/assets/leafia/textures/gui/elevators/radio_main.png", 256, 256);
		assertPng(helper, "/assets/leafia/textures/gui/elevators/radio_change.png", 256, 256);
		assertPng(helper, "/assets/leafia/textures/item/ev_radio.png", 16, 16);
		try(InputStream in = LeafiaEvRadioTests.class.getResourceAsStream("/assets/leafia/models/item/ev_radio.json")) {
			helper.assertTrue(in != null, "models/item/ev_radio.json is not in the jar");
			String model = new String(in.readAllBytes());
			helper.assertTrue(model.contains("leafia:item/ev_radio"), "the model does not name its texture");
		}
		try(InputStream in = LeafiaEvRadioTests.class.getResourceAsStream("/assets/leafia/lang/en_us.json")) {
			String lang = new String(in.readAllBytes());
			helper.assertTrue(lang.contains("\"item.leafia.ev_radio\": \"Elevator Radio\""), "the item has no name");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static void assertPng(GameTestHelper helper, String path, int w, int h) throws Exception {
		try(InputStream in = LeafiaEvRadioTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, path + " is not in the jar");
			var image = ImageIO.read(in);
			helper.assertTrue(image.getWidth() == w && image.getHeight() == h,
					path + " is " + image.getWidth() + "x" + image.getHeight() + ", expected " + w + "x" + h);
		}
	}

	private static ItemStack radio() {
		return new ItemStack(LeafiaItems.ev_radio.get());
	}

	private static CustomData radioTag(int up, int down, int idle) {
		ItemStack scratch = radio();
		EvRadioItem.write(scratch, up, down, idle);
		return scratch.get(DataComponents.CUSTOM_DATA);
	}

	private static void assertMusic(GameTestHelper helper, Triplet<Integer, Integer, Integer> got, int up, int down,
			int idle, String what) {
		helper.assertTrue(got.getA() == up && got.getB() == down && got.getC() == idle,
				what + ": expected (" + up + ", " + down + ", " + idle + "), got (" + got.getA() + ", " + got.getB()
						+ ", " + got.getC() + ")");
	}

	private static EvRadioUIPacket ui(int up, int down, int idle) {
		EvRadioUIPacket packet = new EvRadioUIPacket();
		packet.upOrdinal = up;
		packet.downOrdinal = down;
		packet.idleOrdinal = idle;
		return packet;
	}

	/** Sends a packet through the real encoder and decoder, as the network thread would, then runs its callback. */
	private static void deliver(LeafiaCustomPacketEncoder p, ServerPlayer player, PacketFlow flow) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(p);
		var opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		if(callback != null) callback.accept(context(player, flow));
	}

	/** True if the call got as far as sending a payload to the mock connection, which refuses every one. */
	private static boolean reachedSending(Runnable call) {
		try {
			call.run();
			return false;
		} catch(UnsupportedOperationException e) {
			return e.getMessage() != null && e.getMessage().contains("leafia:custom");
		}
	}

	/** See {@code LeafiaEvCabinGuiTests.mockPlayer}: a mock player left on the list crashes the whole server. */
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

	private static ElevatorEntity cabin(GameTestHelper helper) {
		ElevatorEntity cabin = (ElevatorEntity) LeafiaEntities.ELEVATOR.get().create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		cabin.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(cabin);
		return cabin;
	}

	private static IPayloadContext context(Player player, PacketFlow flow) {
		return new IPayloadContext() {
			@Override public net.minecraft.network.protocol.common.ClientCommonPacketListener listener() {
				throw new UnsupportedOperationException();
			}
			@Override public Player player() { return player; }
			@Override public CompletableFuture<Void> enqueueWork(Runnable task) {
				task.run();
				return CompletableFuture.completedFuture(null);
			}
			@Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
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

	// ------------------------------------------------------------------ the recording proxy

	/** One request to the proxy, as the cabin made it. */
	private record Request(SoundEvent sound, @Nullable SoundEvent start, @Nullable SoundEvent stop, SoundSource cat,
			float x, float y, float z, float volume, float pitch) {}

	/** A wrapper that records every call the cabin makes on it. */
	private static final class Recorder extends LCEAudioWrapper {
		final List<String> log = new ArrayList<>();

		@Override public LCEAudioWrapper updatePosition(float x, float y, float z) { log.add("move"); return this; }
		@Override public LCEAudioWrapper setLooped(boolean looped) { log.add("looped " + looped); return this; }
		@Override public LCEAudioWrapper startSound() { log.add("start"); return this; }
		@Override public LCEAudioWrapper stopSound() { log.add("stop"); return this; }
	}

	private static final class Recording {
		final List<Request> requests = new ArrayList<>();
		final List<Recorder> made = new ArrayList<>();
	}

	/** Installs a recording factory for the length of a body and puts the old one back whatever happens. */
	private static void withRecorder(Consumer<Recording> body) {
		Recording rec = new Recording();
		LCEAudioProxy.Factory previous = LCEAudioProxy.factory;
		LCEAudioProxy.factory = (world, sound, start, stop, cat, x, y, z, volume, pitch) -> {
			rec.requests.add(new Request(sound, start, stop, cat, x, y, z, volume, pitch));
			Recorder made = new Recorder();
			rec.made.add(made);
			return made;
		};
		try {
			body.accept(rec);
		} finally {
			LCEAudioProxy.factory = previous;
		}
	}
}
