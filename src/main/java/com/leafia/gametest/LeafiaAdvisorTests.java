package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.ModBlocks;
import com.hbm.render.util.InfoSystem;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorSignalType;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorWarningPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AlertSink;
import com.leafia.contents.gear.advisor.AdvisorItem.Warns;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.LeafiaUtil.ScrollUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The High-Tech Advisor (D-1080): the item, its cover, its watch, its history and its packets,
 * and the assets that stand in for its renderer.
 *
 * <h2>What is pinned and what is not</h2>
 *
 * The three screens cannot be loaded on a dedicated server, so none of their pixels is here: the
 * list's rows, its scroll bar, the Read panel, the buttons and the open lid were checked on a
 * running client (D-1080's screenshots). What the server side owns is all pinned: the stack's open
 * flag, the persisted history and its ordering, the first-join gift, every decision {@code watch}
 * makes (against a recording sink in place of the client's), the wire format of both packets, the
 * flag each warning id raises, and the generated models, textures and lang keys.
 *
 * <h2>Test hazards</h2>
 *
 * <ul>
 * <li>Players are {@code makeMockPlayer} wherever a {@code ServerPlayer} is not needed, and a
 *     <em>detached</em> {@code ServerPlayer} (off the player list, D-1077) where it is: the
 *     addon's wind tick broadcasts to every listed player, and a mock connection refuses the
 *     payload.</li>
 * <li>The same refusal happens when a handler under test <em>replies</em> - the history sync - so
 *     such a handler's effect is read from the player's NBT, and the refusal is expected and
 *     asserted to be that message and nothing else.</li>
 * <li>{@code Warns}, {@code InfoSystem} and {@code ArmorRegistry.hazardClasses} are process-wide
 *     statics. Every test that writes one restores it in a {@code finally} (D-952).</li>
 * </ul>
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaAdvisorTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	/** Upstream's draw order and the texture each part binds. */
	private static final Map<String, String> BODY = new LinkedHashMap<>();
	private static final Map<String, String> COVER = new LinkedHashMap<>();
	static {
		BODY.put("base", "panel");
		BODY.put("hingeBase", "hinge");
		BODY.put("screen", "black");
		BODY.put("logo", "logo");
		BODY.put("solar", "solar");
		COVER.put("cover", "panel");
		COVER.put("hingeCover", "hinge");
		COVER.put("antenna", "black");
		COVER.put("instrument", "instrument");
		COVER.put("speakers", "speaker");
		COVER.put("speakersIn", "speaker");
		COVER.put("hta", "hta");
	}

	/** Every message key {@code watch} can emit. */
	private static final List<String> KEYS = List.of(
			"coal", "asbestos", "radon", "pyro", "skindmg2", "skindmg3", "toxic1", "toxic2", "lava");

	// ---- helpers ----

	/** What the watch would have done, as strings. */
	private static final class Recorder implements AlertSink {
		final List<String> events = new ArrayList<>();

		@Override
		public void warn(String key) {
			events.add("warn:" + key);
		}

		@Override
		public void show(String key, int millis, int id) {
			events.add("show:" + key + ":" + millis + ":" + id);
		}
	}

	@SuppressWarnings("deprecation")
	private static ServerPlayer mockPlayer(GameTestHelper helper) {
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

	private static IPayloadContext context(PacketFlow flow, Player player) {
		return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
				new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> switch(method.getName()) {
					case "flow" -> flow;
					case "player" -> player;
					default -> null;
				});
	}

	/** Sends a packet through the real framing and runs its handler, returning what the handler threw. */
	private static RuntimeException deliver(LeafiaCustomPacketEncoder encoder, PacketFlow flow, Player player) {
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(encoder);
		LeafiaCustomPacket.Opened opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		if(callback == null) return null;
		try {
			callback.accept(context(flow, player));
		} catch(RuntimeException e) {
			return e;
		}
		return null;
	}

	/** The mock connection's refusal to carry the addon's payload: what a reply looks like from here. */
	private static boolean isRefusal(RuntimeException e) {
		return e != null && String.valueOf(e.getMessage()).contains("may not be sent");
	}

	private static String lang(String key) {
		return json("lang/en_us.json").has(key) ? json("lang/en_us.json").get(key).getAsString() : null;
	}

	private static String text(String path) {
		try(InputStream in = LeafiaAdvisorTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(text(path)).getAsJsonObject();
	}

	private static List<String> lines(String path) {
		return Arrays.asList(text(path).split("\n"));
	}

	private static BufferedImage png(String path) {
		try(InputStream in = LeafiaAdvisorTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return ImageIO.read(in);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ItemStack advisor() {
		return new ItemStack(LeafiaItems.advisor.get());
	}

	private static void open(ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("open", true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// ---- the item ----

	/** One item under its own name, in the addon's tab, stacking to 64 as upstream's does. */
	@GameTest(template = EMPTY)
	public static void theItemIsRegisteredWithUpstreamsDefaults(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.advisor, "the advisor is additive, so it defaults on");
		helper.assertTrue(LeafiaItems.advisor != null, "the advisor did not register with its switch on");

		var id = BuiltInRegistries.ITEM.getKey(LeafiaItems.advisor.get());
		helper.assertTrue(id.toString().equals("leafia:advisor"), "registered as " + id);
		helper.assertTrue(LeafiaItems.advisor.get() instanceof AdvisorItem, "not an AdvisorItem");
		helper.assertTrue(advisor().getMaxStackSize() == 64,
				"AddonItemBase sets no stack size, so 64, got " + advisor().getMaxStackSize());
		helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
				.anyMatch(s -> s.get().asItem() == LeafiaItems.advisor.get()), "the advisor is not in the addon's tab");
		helper.assertTrue(!AdvisorItem.isOpen(advisor()), "a fresh advisor is closed");
		helper.succeed();
	}

	// ---- the cover ----

	/** Using it in the main hand opens the cover; the off hand is refused and opens nothing. */
	@GameTest(template = EMPTY)
	public static void usingItOpensTheCoverInTheMainHandOnly(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, advisor());
		player.setItemInHand(InteractionHand.OFF_HAND, advisor());

		AdvisorItem item = LeafiaItems.advisor.get();

		var off = item.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertTrue(off.getResult() == InteractionResult.FAIL, "the off hand's use is a failure, got " + off.getResult());
		helper.assertTrue(!AdvisorItem.isOpen(player.getMainHandItem()) && !AdvisorItem.isOpen(player.getOffhandItem()),
				"the refused off-hand use opened something");

		var main = item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(main.getResult() == InteractionResult.SUCCESS, "the main hand's use is a success, got " + main.getResult());
		helper.assertTrue(AdvisorItem.isOpen(player.getMainHandItem()), "the main hand's cover did not open");
		helper.assertTrue(!AdvisorItem.isOpen(player.getOffhandItem()), "the off hand's cover opened as well");

		CompoundTag tag = player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag();
		helper.assertTrue(tag.getBoolean("open") && tag.size() == 1, "upstream's tag is exactly {open:true}, got " + tag);
		helper.succeed();
	}

	/** Closing wipes every advisor the player carries - hotbar, main, off hand, armour slot - and nothing else. */
	@GameTest(template = EMPTY)
	public static void closingTheCoverWipesEveryAdvisorTheyCarry(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack stone = new ItemStack(Items.STONE);

		player.setItemInHand(InteractionHand.MAIN_HAND, advisor());
		player.getInventory().items.set(20, advisor());
		player.getInventory().offhand.set(0, advisor());
		player.getInventory().armor.set(3, advisor());
		player.getInventory().items.set(21, stone);

		open(player.getMainHandItem());
		open(player.getInventory().items.get(20));
		open(player.getInventory().offhand.get(0));
		open(player.getInventory().armor.get(3));
		helper.assertTrue(AdvisorItem.isOpen(player.getInventory().armor.get(3)), "setup: the head-slot advisor should be open");

		AdvisorItem.setCover(player, false);

		helper.assertTrue(!AdvisorItem.isOpen(player.getMainHandItem()), "the held advisor stayed open");
		helper.assertTrue(!AdvisorItem.isOpen(player.getInventory().items.get(20)), "a stowed advisor stayed open");
		helper.assertTrue(!AdvisorItem.isOpen(player.getInventory().offhand.get(0)), "the off-hand advisor stayed open");
		helper.assertTrue(!AdvisorItem.isOpen(player.getInventory().armor.get(3)), "the head-slot advisor stayed open");
		helper.assertTrue(player.getInventory().items.get(21) == stone && stone.getCount() == 1, "something else was touched");
		helper.succeed();
	}

	/** Opening is for an advisor in the main hand; with anything else there it does nothing. */
	@GameTest(template = EMPTY)
	public static void openingTheCoverNeedsAnAdvisorInTheMainHand(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		player.setItemInHand(InteractionHand.OFF_HAND, advisor());

		AdvisorItem.setCover(player, true);

		helper.assertTrue(!player.getMainHandItem().has(DataComponents.CUSTOM_DATA), "stone gained a tag");
		helper.assertTrue(!AdvisorItem.isOpen(player.getOffhandItem()),
				"the off hand opened (upstream's second branch tests the main hand twice, so it never does)");
		helper.succeed();
	}

	// ---- the history ----

	/** The history is oldest first under PlayerPersisted.leafia_advisor.history, upstream's keys. */
	@GameTest(template = EMPTY)
	public static void theHistoryLivesInThePersistedTagOldestFirst(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		AdvisorItem.storeWarning(player, "coal");
		AdvisorItem.storeWarning(player, "lava");
		AdvisorItem.storeWarning(player, "radon");

		ListTag raw = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
				.getCompound("leafia_advisor").getList("history", Tag.TAG_STRING);
		helper.assertTrue(raw.size() == 3 && raw.getString(0).equals("coal") && raw.getString(2).equals("radon"),
				"stored as " + raw);

		List<String> read = AdvisorItem.getWarningHistory(player, new ArrayList<>());
		helper.assertTrue(read.equals(List.of("coal", "lava", "radon")), "read back as " + read);

		List<String> existing = new ArrayList<>(List.of("kept"));
		AdvisorItem.getWarningHistory(player, existing);
		helper.assertTrue(existing.equals(List.of("kept", "coal", "lava", "radon")), "it appends to the target, got " + existing);

		helper.assertTrue(AdvisorItem.displayOrder(read).equals(List.of("radon", "lava", "coal")),
				"the screen lists newest first, got " + AdvisorItem.displayOrder(read));
		helper.assertTrue(read.equals(List.of("coal", "lava", "radon")), "displayOrder reordered its argument");
		helper.succeed();
	}

	/** Dismissing is by stored index, and an index outside the list is ignored rather than throwing. */
	@GameTest(template = EMPTY)
	public static void dismissingRemovesByStoredIndexAndIgnoresTheOutOfRange(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		for(String key : List.of("a", "b", "c", "d"))
			AdvisorItem.storeWarning(player, key);

		AdvisorItem.removeStoredWarning(player, 1);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).equals(List.of("a", "c", "d")),
				"index 1 should take b");
		AdvisorItem.removeStoredWarning(player, -1);
		AdvisorItem.removeStoredWarning(player, 3);
		AdvisorItem.removeStoredWarning(player, 99);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).equals(List.of("a", "c", "d")),
				"out-of-range indices changed the list");

		// The row on screen to the stored index is size - 1 - row, so row 0, the newest, is the last stored.
		AdvisorItem.removeStoredWarning(player, 3 - 1 - 0);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).equals(List.of("a", "c")),
				"row 0 should dismiss the newest, got " + AdvisorItem.getWarningHistory(player, new ArrayList<>()));
		helper.succeed();
	}

	/** Reading a history that was never written does not write one that is not empty. */
	@GameTest(template = EMPTY)
	public static void aPlayerWithNoHistoryHasAnEmptyOne(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).isEmpty(), "a new player has a history");
		AdvisorItem.removeStoredWarning(player, 0);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).isEmpty(), "removing from nothing made something");
		helper.succeed();
	}

	// ---- the first copy ----

	/**
	 * Logging in is what hands it over: the mock player below is logged in for real, and the
	 * event handler has already run by the time the test holds it.
	 */
	@GameTest(template = EMPTY)
	public static void aPlayerLoggingInIsHandedOneAdvisor(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);

		long count = player.getInventory().items.stream().filter(s -> s.getItem() == LeafiaItems.advisor.get()).count();
		helper.assertTrue(count == 1, "the login handler should have given one advisor, got " + count);
		helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean("receivedAdvisor"),
				"upstream's flag key is receivedAdvisor");
		helper.assertTrue(!AdvisorItem.giveFirstAdvisor(player), "a second call gives nothing");
		helper.succeed();
	}

	/** One free advisor per player, ever: marked in the persisted tag, whatever becomes of it. */
	@GameTest(template = EMPTY)
	public static void everyPlayerIsGivenOneAdvisorOnce(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		forgetTheGift(player);

		helper.assertTrue(AdvisorItem.giveFirstAdvisor(player), "the first call gives one");
		long count = player.getInventory().items.stream().filter(s -> s.getItem() == LeafiaItems.advisor.get()).count();
		helper.assertTrue(count == 1, "one advisor in the inventory, got " + count);
		helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean("receivedAdvisor"),
				"the flag is set");

		player.getInventory().clearContent();
		helper.assertTrue(!AdvisorItem.giveFirstAdvisor(player), "a second call gives nothing");
		helper.assertTrue(player.getInventory().isEmpty(), "and so the inventory stays empty even after losing the first");
		helper.succeed();
	}

	/** A full inventory loses the copy and keeps the flag: upstream's, flagged. */
	@GameTest(template = EMPTY)
	public static void aFullInventoryLosesTheFirstCopyButKeepsTheFlag(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		// A creative player's full inventory "accepts" anything and discards it, which would make
		// this test pass for the wrong reason; the mock logs in as creative.
		player.setGameMode(GameType.SURVIVAL);
		forgetTheGift(player);
		for(int i = 0; i < player.getInventory().items.size(); i++)
			player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));

		helper.assertTrue(AdvisorItem.giveFirstAdvisor(player), "it reports giving");
		helper.assertTrue(player.getInventory().items.stream().noneMatch(s -> s.getItem() == LeafiaItems.advisor.get()),
				"there was no room, so no advisor");
		helper.assertTrue(!AdvisorItem.giveFirstAdvisor(player), "and the flag is already set, so it is lost for good");
		helper.succeed();
	}

	/** Puts a logged-in player back to before the gift: no flag, an empty inventory. */
	private static void forgetTheGift(ServerPlayer player) {
		player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove("receivedAdvisor");
		player.getInventory().clearContent();
	}

	// ---- the packets ----

	/** The two registered ids, appended after the cabin screen's, and nothing before them moved. */
	@GameTest(template = EMPTY)
	public static void theAdvisorsPacketsAreAppended(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.ELEVATOR_BUTTON_MODIFY == 12, "the cabin's modify packet moved");
		helper.assertTrue(LeafiaPacketTypes.ADVISOR_WARNING == 13, "ADVISOR_WARNING is " + LeafiaPacketTypes.ADVISOR_WARNING);
		helper.assertTrue(LeafiaPacketTypes.ADVISOR == 14, "ADVISOR is " + LeafiaPacketTypes.ADVISOR);
		helper.assertTrue(LeafiaPacketTypes.idOf(new AdvisorPacket(0)) == 14, "the screen's packet registered under its own id");
		helper.assertTrue(LeafiaPacketTypes.idOf(new AdvisorWarningPacket(0)) == 13, "the warning packet registered under its own id");
		helper.succeed();
	}

	/** Upstream's five signals and their ids, which are on the wire. */
	@GameTest(template = EMPTY)
	public static void theSignalIdsAreUpstreams(GameTestHelper helper) {
		helper.assertTrue(AdvisorSignalType.CLOSE.id == 0 && AdvisorSignalType.WARN.id == 1
				&& AdvisorSignalType.SYNC_REQUEST.id == 2 && AdvisorSignalType.SYNC_LOCAL.id == 3
				&& AdvisorSignalType.DISMISS.id == 4, "a signal id moved");
		helper.assertTrue(AdvisorSignalType.values().length == 5, "a signal was added or lost");
		boolean threw = false;
		try {
			AdvisorSignalType.fromId((byte) 5);
		} catch(RuntimeException e) {
			threw = true;
		}
		helper.assertTrue(threw, "an unknown signal id is an error, as upstream's \"what\"");
		helper.succeed();
	}

	/**
	 * Sizes on the wire. The header is 16 bits and the signal one byte, so CLOSE and SYNC_REQUEST
	 * are 24, DISMISS is 24 plus an int, a warning is the header plus an int, and a warning key
	 * costs five bits a letter more for each extra letter.
	 */
	@GameTest(template = EMPTY)
	public static void theWireSizesAreWhatTheEncodingImplies(GameTestHelper helper) {

		int close = LeafiaCustomPacket.__start(new AdvisorPacket(AdvisorSignalType.CLOSE.id)).bitLength();
		helper.assertTrue(close == 24, "CLOSE is 24 bits, got " + close);
		int request = LeafiaCustomPacket.__start(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id)).bitLength();
		helper.assertTrue(request == 24, "SYNC_REQUEST is 24 bits, got " + request);

		AdvisorPacket dismiss = new AdvisorPacket(AdvisorSignalType.DISMISS.id);
		dismiss.dismissIndex = 3;
		int dismissBits = LeafiaCustomPacket.__start(dismiss).bitLength();
		helper.assertTrue(dismissBits == 56, "DISMISS is 56 bits, got " + dismissBits);

		int warning = LeafiaCustomPacket.__start(new AdvisorWarningPacket(4)).bitLength();
		helper.assertTrue(warning == 48, "a warning is a 16-bit header and an int, got " + warning);

		AdvisorPacket warn = new AdvisorPacket(AdvisorSignalType.WARN.id);
		warn.warningKey = "coal";
		int coal = LeafiaCustomPacket.__start(warn).bitLength();
		warn.warningKey = "asbestos";
		int asbestos = LeafiaCustomPacket.__start(warn).bitLength();
		helper.assertTrue(asbestos - coal == 4 * 5, "four more letters are four more fifths of a byte: coal " + coal + ", asbestos " + asbestos);
		helper.succeed();
	}

	/** Every key survives the codec, digits included, and a handler stores exactly it. */
	@GameTest(template = EMPTY)
	public static void aWarnPacketStoresItsKeyOnTheServer(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		player.getInventory().setItem(0, advisor()); // D-1094: only a carrier of an advisor is heard

		for(String key : KEYS) {
			AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.WARN.id);
			packet.warningKey = key;
			RuntimeException thrown = deliver(packet, PacketFlow.SERVERBOUND, player);
			helper.assertTrue(thrown == null || isRefusal(thrown), key + ": unexpected " + thrown);
		}

		List<String> stored = AdvisorItem.getWarningHistory(player, new ArrayList<>());
		helper.assertTrue(stored.equals(KEYS), "stored " + stored);
		helper.succeed();
	}

	/** A sync request is answered, which from here is the refusal; and a dismissal removes by index. */
	@GameTest(template = EMPTY)
	public static void theServerAnswersASyncRequestAndHonoursADismissal(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, advisor());
		open(player.getMainHandItem()); // D-1094: only the open screen asks and dismisses
		AdvisorItem.storeWarning(player, "coal");
		AdvisorItem.storeWarning(player, "lava");
		AdvisorItem.storeWarning(player, "radon");

		RuntimeException reply = deliver(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id), PacketFlow.SERVERBOUND, player);
		helper.assertTrue(isRefusal(reply), "the server should have tried to reply with the history, got " + reply);

		AdvisorPacket dismiss = new AdvisorPacket(AdvisorSignalType.DISMISS.id);
		dismiss.dismissIndex = 1;
		RuntimeException after = deliver(dismiss, PacketFlow.SERVERBOUND, player);
		helper.assertTrue(isRefusal(after), "and it answers a dismissal with the new history, got " + after);
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).equals(List.of("coal", "radon")),
				"stored index 1 should have gone, got " + AdvisorItem.getWarningHistory(player, new ArrayList<>()));
		helper.succeed();
	}

	/** CLOSE folds the lid; going the wrong way, no signal does anything. */
	@GameTest(template = EMPTY)
	public static void theSignalsOnlyActInTheirOwnDirection(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, advisor());
		open(player.getMainHandItem());

		helper.assertTrue(deliver(new AdvisorPacket(AdvisorSignalType.CLOSE.id), PacketFlow.CLIENTBOUND, player) == null,
				"a clientbound CLOSE threw");
		helper.assertTrue(AdvisorItem.isOpen(player.getMainHandItem()), "a clientbound CLOSE closed the cover");

		helper.assertTrue(deliver(new AdvisorPacket(AdvisorSignalType.CLOSE.id), PacketFlow.SERVERBOUND, player) == null,
				"a serverbound CLOSE threw");
		helper.assertTrue(!AdvisorItem.isOpen(player.getMainHandItem()), "a serverbound CLOSE left the cover open");

		AdvisorPacket warn = new AdvisorPacket(AdvisorSignalType.WARN.id);
		warn.warningKey = "coal";
		helper.assertTrue(deliver(warn, PacketFlow.CLIENTBOUND, player) == null, "a clientbound WARN threw");
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).isEmpty(), "a clientbound WARN was stored");

		helper.assertTrue(deliver(new AdvisorPacket(AdvisorSignalType.SYNC_REQUEST.id), PacketFlow.CLIENTBOUND, player) == null,
				"a clientbound SYNC_REQUEST was answered");

		AdvisorItem.storeWarning(player, "coal");
		AdvisorPacket dismiss = new AdvisorPacket(AdvisorSignalType.DISMISS.id);
		dismiss.dismissIndex = 0;
		helper.assertTrue(deliver(dismiss, PacketFlow.CLIENTBOUND, player) == null, "a clientbound DISMISS threw");
		helper.assertTrue(AdvisorItem.getWarningHistory(player, new ArrayList<>()).size() == 1, "a clientbound DISMISS removed an entry");
		helper.succeed();
	}

	/**
	 * A history sent to a client is read to its last bit, in order, and does nothing on a server -
	 * where the screen it would fill does not exist.
	 */
	@GameTest(template = EMPTY)
	public static void aSyncedHistoryIsReadToTheEnd(GameTestHelper helper) {

		AdvisorPacket sync = new AdvisorPacket(AdvisorSignalType.SYNC_LOCAL.id);
		sync.warnings = List.of("coal", "skindmg2", "toxic1", "lava");

		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(sync);
		LeafiaCustomPacket.Opened opened = LeafiaCustomPacket.open(packet);
		Consumer<IPayloadContext> callback = opened.prototype().decode(opened.buf());
		helper.assertTrue(callback != null, "SYNC_LOCAL has a handler");
		helper.assertTrue(opened.buf().readableBits() == 0, "the decoder left " + opened.buf().readableBits() + " bits unread");

		callback.accept(context(PacketFlow.CLIENTBOUND, null));
		callback.accept(context(PacketFlow.SERVERBOUND, null));

		AdvisorPacket empty = new AdvisorPacket(AdvisorSignalType.SYNC_LOCAL.id);
		empty.warnings = List.of();
		LeafiaCustomPacket.Opened none = LeafiaCustomPacket.open(LeafiaCustomPacket.__start(empty));
		none.prototype().decode(none.buf());
		helper.assertTrue(none.buf().readableBits() == 0, "an empty history is a count of zero and nothing after");
		helper.succeed();
	}

	// ---- the warnings the server raises ----

	/** Each id raises its own flag and no other; an id upstream has no flag for raises nothing. */
	@GameTest(template = EMPTY)
	public static void eachWarningIdRaisesItsOwnFlag(GameTestHelper helper) {
		try {
			boolean[][] expected = {
					{true, false, false, false, false},
					{false, true, false, false, false},
					{false, false, true, false, false},
					{false, false, false, true, false},
					{false, false, false, false, true},
			};
			for(int id = 0; id < 5; id++) {
				Warns.reset();
				AdvisorWarningPacket.raise(id);
				boolean[] now = {Warns.pyro, Warns.skinDmg2, Warns.skinDmg3, Warns.toxic, Warns.toxicStrong};
				helper.assertTrue(Arrays.equals(now, expected[id]), "id " + id + " raised " + Arrays.toString(now));
			}
			Warns.reset();
			AdvisorWarningPacket.raise(5);
			AdvisorWarningPacket.raise(-1);
			helper.assertTrue(!Warns.pyro && !Warns.skinDmg2 && !Warns.skinDmg3 && !Warns.toxic && !Warns.toxicStrong,
					"an unknown id raised a flag");
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** The packet carries the id, raises its flag only on arrival at a client, and is ignored going to a server. */
	@GameTest(template = EMPTY)
	public static void aWarningPacketRaisesItsFlagOnlyOnArrivalAtAClient(GameTestHelper helper) {
		try {
			Warns.reset();
			helper.assertTrue(deliver(new AdvisorWarningPacket(3), PacketFlow.SERVERBOUND, null) == null, "a serverbound warning threw");
			helper.assertTrue(!Warns.toxic, "a client could raise the server's flag");
			helper.assertTrue(deliver(new AdvisorWarningPacket(3), PacketFlow.CLIENTBOUND, null) == null, "a clientbound warning threw");
			helper.assertTrue(Warns.toxic && !Warns.toxicStrong, "toxic was not raised, or strong was too");
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** The clock: flags last one tick, three cooldowns count down, and the pyrophoric one does not. */
	@GameTest(template = EMPTY)
	public static void theClientTickClearsFlagsAndCountsCooldownsDown(GameTestHelper helper) {
		try {
			Warns.reset();
			Warns.pyro = Warns.skinDmg2 = Warns.skinDmg3 = Warns.toxic = Warns.toxicStrong = true;
			Warns.gas = 3;
			Warns.lava = 2;
			Warns.toxicCooldown = 1;
			Warns.pyroCooldown = 1200;

			Warns.preTick();
			helper.assertTrue(!Warns.pyro && !Warns.skinDmg2 && !Warns.skinDmg3 && !Warns.toxic && !Warns.toxicStrong,
					"a flag survived the tick");
			helper.assertTrue(Warns.gas == 2 && Warns.lava == 1 && Warns.toxicCooldown == 0,
					"cooldowns are " + Warns.gas + ", " + Warns.lava + ", " + Warns.toxicCooldown);

			for(int i = 0; i < 5000; i++) Warns.preTick();
			helper.assertTrue(Warns.gas == 0 && Warns.lava == 0 && Warns.toxicCooldown == 0, "a cooldown went negative or stuck");
			helper.assertTrue(Warns.pyroCooldown == 1200,
					"upstream's pyrophoric cooldown never counts down, so it stays 1200, got " + Warns.pyroCooldown);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** Damaged skin sends a warning from level II up, and only to a player. */
	@GameTest(template = EMPTY)
	public static void damagedSkinTellsThePlayerFromLevelTwoUp(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);

		LeafiaPotion.hurtSkin(player, 3);
		helper.assertTrue(LeafiaPotion.getSkinDamage(player) == 1, "the first application is level I");

		RuntimeException second = null;
		try {
			LeafiaPotion.hurtSkin(player, 3);
		} catch(RuntimeException e) {
			second = e;
		}
		helper.assertTrue(isRefusal(second), "level II should have sent the advisor a warning, got " + second);
		helper.assertTrue(LeafiaPotion.getSkinDamage(player) == 2, "the second application is level II");

		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		LeafiaPotion.hurtSkin(other, 3);
		LeafiaPotion.hurtSkin(other, 3);
		helper.assertTrue(LeafiaPotion.getSkinDamage(other) == 2, "a non-server player takes the levels and sends nothing");
		helper.succeed();
	}

	// ---- the watch ----

	private static Player playerAt(GameTestHelper helper, double x, double y, double z) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		player.setPos(at.x, at.y, at.z);
		player.setYHeadRot(0);
		player.setDeltaMovement(Vec3.ZERO);
		return player;
	}

	/** Coal dust at the player's feet: one history entry per cooldown, and a message every tick. */
	@GameTest(template = EMPTY)
	public static void coalDustWarnsOnceAMinuteAndShowsEveryTick(GameTestHelper helper) {
		try {
			Warns.reset();
			Player player = playerAt(helper, 2.5, 2, 2.5);
			helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.gas_coal.get());

			Recorder first = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, first);
			helper.assertTrue(first.events.equals(List.of("warn:coal", "show:coal:10000:0")), "first tick: " + first.events);
			helper.assertTrue(Warns.gas == 1200, "the cooldown is a minute of ticks, got " + Warns.gas);

			Recorder second = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, second);
			helper.assertTrue(second.events.equals(List.of("show:coal:10000:0")), "second tick: " + second.events);

			for(int i = 0; i < 1199; i++) Warns.preTick();
			Recorder early = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, early);
			helper.assertTrue(early.events.equals(List.of("show:coal:10000:0")), "a tick before the minute is up: " + early.events);

			Warns.gas = 0;
			Recorder again = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, again);
			helper.assertTrue(again.events.equals(List.of("warn:coal", "show:coal:10000:0")), "after the cooldown: " + again.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** Gas at head height counts, and a clear tile warns of nothing. */
	@GameTest(template = EMPTY)
	public static void gasAtTheHeadCountsAndCleanAirDoesNot(GameTestHelper helper) {
		try {
			Warns.reset();
			Player player = playerAt(helper, 2.5, 2, 2.5);

			Recorder clean = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, clean);
			helper.assertTrue(clean.events.isEmpty(), "clean air said " + clean.events);

			helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.gas_coal.get());
			Recorder head = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, head);
			helper.assertTrue(head.events.equals(List.of("warn:coal", "show:coal:10000:0")), "gas at the head: " + head.events);

			// One block over, outside both of the player's own tiles.
			Warns.reset();
			helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
			helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.gas_coal.get());
			Recorder beside = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, beside);
			helper.assertTrue(beside.events.isEmpty(), "gas beside the player said " + beside.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** Feet before head, and the cooldown the first sets silences the second's history entry but not its message. */
	@GameTest(template = EMPTY)
	public static void feetAreCheckedBeforeTheHeadAndShareOneCooldown(GameTestHelper helper) {
		try {
			Warns.reset();
			Player player = playerAt(helper, 2.5, 2, 2.5);
			helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.gas_coal.get());
			helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.gas_asbestos.get());

			Recorder rec = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, rec);
			helper.assertTrue(rec.events.equals(List.of("warn:coal", "show:coal:10000:0", "show:asbestos:10000:1")),
					"got " + rec.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** Each gas wants its own filter class on the head slot, and a mask for one is no help against another. */
	@GameTest(template = EMPTY)
	public static void eachGasWantsItsOwnProtectionClass(GameTestHelper helper) {

		Item helmet = Items.LEATHER_HELMET;
		var before = ArmorRegistry.hazardClasses.get(helmet);
		try {
			Warns.reset();
			Player player = playerAt(helper, 2.5, 2, 2.5);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(helmet));

			helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.gas_coal.get());

			ArmorRegistry.hazardClasses.put(helmet, new ArrayList<>(List.of(HazardClass.PARTICLE_COARSE)));
			Recorder coarse = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, coarse);
			helper.assertTrue(coarse.events.isEmpty(), "a coarse filter should stop coal dust, got " + coarse.events);

			ArmorRegistry.hazardClasses.put(helmet, new ArrayList<>(List.of(HazardClass.PARTICLE_FINE)));
			Recorder fine = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, fine);
			helper.assertTrue(fine.events.size() == 2, "a fine filter does not stop coal dust, got " + fine.events);

			helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.gas_asbestos.get());
			Warns.reset();
			Recorder asbestosFine = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, asbestosFine);
			helper.assertTrue(asbestosFine.events.isEmpty(), "a fine filter stops asbestos, got " + asbestosFine.events);

			ArmorRegistry.hazardClasses.put(helmet, new ArrayList<>(List.of(HazardClass.PARTICLE_COARSE)));
			Recorder asbestosCoarse = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, asbestosCoarse);
			helper.assertTrue(asbestosCoarse.events.equals(List.of("warn:asbestos", "show:asbestos:10000:1")),
					"a coarse filter does not stop asbestos, got " + asbestosCoarse.events);

			// And a protected head slot is not the chest: the leather chestplate is irrelevant.
			helper.succeed();
		} finally {
			if(before == null) ArmorRegistry.hazardClasses.remove(helmet);
			else ArmorRegistry.hazardClasses.put(helmet, before);
			Warns.reset();
		}
	}

	/** All four radon blocks are one warning, under id 7, and need the fine class. */
	@GameTest(template = EMPTY)
	public static void allFourRadonBlocksAreOneWarning(GameTestHelper helper) {
		try {
			List<Block> radon = List.of(ModBlocks.gas_radon.get(), ModBlocks.gas_radon_dense.get(),
					ModBlocks.gas_radon_tomb.get(), ModBlocks.gas_meltdown.get());
			for(Block block : radon) {
				Warns.reset();
				Player player = playerAt(helper, 2.5, 2, 2.5);
				helper.setBlock(new BlockPos(2, 2, 2), block);
				Recorder rec = new Recorder();
				AdvisorItem.watch(helper.getLevel(), player, rec);
				helper.assertTrue(rec.events.equals(List.of("warn:radon", "show:radon:10000:7")),
						BuiltInRegistries.BLOCK.getKey(block) + ": " + rec.events);
			}

			// The two gases the base mod has and the advisor does not mention.
			for(Block block : List.of(ModBlocks.gas_monoxide.get(), ModBlocks.gas_flammable.get(), ModBlocks.gas_explosive.get())) {
				Warns.reset();
				Player player = playerAt(helper, 2.5, 2, 2.5);
				helper.setBlock(new BlockPos(2, 2, 2), block);
				Recorder rec = new Recorder();
				AdvisorItem.watch(helper.getLevel(), player, rec);
				helper.assertTrue(rec.events.isEmpty(), BuiltInRegistries.BLOCK.getKey(block) + " should not warn, got " + rec.events);
			}
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** The five flags the server raises each have their own message id, and the exclusions upstream has. */
	@GameTest(template = EMPTY)
	public static void theRaisedFlagsBecomeTheirOwnMessages(GameTestHelper helper) {
		try {
			Player player = playerAt(helper, 2.5, 2, 2.5);

			Warns.reset();
			Warns.pyro = true;
			Recorder pyro = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, pyro);
			helper.assertTrue(pyro.events.equals(List.of("warn:pyro", "show:pyro:10000:2")), "pyro: " + pyro.events);
			helper.assertTrue(Warns.pyroCooldown == 1200, "pyro arms its cooldown");
			Warns.pyro = true;
			Recorder pyroAgain = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, pyroAgain);
			helper.assertTrue(pyroAgain.events.equals(List.of("show:pyro:10000:2")), "pyro twice: " + pyroAgain.events);

			// Skin damage has no cooldown at all: every raised tick is a history entry.
			Warns.reset();
			Warns.skinDmg2 = true;
			Recorder skin2 = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, skin2);
			helper.assertTrue(skin2.events.equals(List.of("warn:skindmg2", "show:skindmg2:10000:3")), "skin II: " + skin2.events);
			Warns.skinDmg2 = true;
			Recorder skin2Again = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, skin2Again);
			helper.assertTrue(skin2Again.events.equals(List.of("warn:skindmg2", "show:skindmg2:10000:3")), "skin II again: " + skin2Again.events);

			Warns.reset();
			Warns.skinDmg3 = true;
			Recorder skin3 = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, skin3);
			helper.assertTrue(skin3.events.equals(List.of("warn:skindmg3", "show:skindmg3:10000:3")), "skin III: " + skin3.events);

			Warns.reset();
			Warns.toxic = true;
			Recorder toxic = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, toxic);
			helper.assertTrue(toxic.events.equals(List.of("warn:toxic1", "show:toxic1:10000:5")), "toxic: " + toxic.events);
			helper.assertTrue(Warns.toxicCooldown == 1200, "toxic arms its cooldown");

			// Strong toxicity displaces plain toxicity even when both were raised.
			Warns.reset();
			Warns.toxic = true;
			Warns.toxicStrong = true;
			Recorder strong = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, strong);
			helper.assertTrue(strong.events.equals(List.of("warn:toxic2", "show:toxic2:10000:6")), "strong: " + strong.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** Lava anywhere in the four blocks behind, to ten down, while walking backwards. */
	@GameTest(template = EMPTY16)
	public static void lavaBehindAWalkerIsSeenUpToFourBlocksBack(GameTestHelper helper) {
		try {
			Warns.reset();
			floor(helper);

			// The player stands on a block corner: upstream adds half a block to the position
			// before it floors, so the probe lands in the player's own column only from there.
			Player player = playerAt(helper, 8, 2, 8);
			player.setDeltaMovement(0, 0, -0.2); // facing south, so moving north is backwards

			helper.setBlock(new BlockPos(8, 2, 6), Blocks.LAVA);
			Recorder two = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, two);
			helper.assertTrue(two.events.equals(List.of("warn:lava", "show:lava:4000:4")), "lava two back: " + two.events);
			helper.assertTrue(Warns.lava == 200, "the lava cooldown is 200 ticks, got " + Warns.lava);

			Recorder again = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, again);
			helper.assertTrue(again.events.equals(List.of("show:lava:4000:4")), "inside the cooldown: " + again.events);

			// Four back is the limit.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 2, 4), Blocks.LAVA);
			Recorder four = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, four);
			helper.assertTrue(four.events.size() == 2, "lava four back: " + four.events);

			Warns.reset();
			helper.setBlock(new BlockPos(8, 2, 4), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 2, 3), Blocks.LAVA);
			Recorder five = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, five);
			helper.assertTrue(five.events.isEmpty(), "lava five back is out of range: " + five.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** The probe reads ten blocks down (0 to 9), and stops at the first thing with a collision box. */
	@GameTest(template = EMPTY16)
	public static void theProbeFallsTenBlocksAndStopsAtSolidGround(GameTestHelper helper) {
		try {
			floor(helper);
			Player player = playerAt(helper, 8, 6, 8);
			player.setDeltaMovement(0, 0, -0.2);

			// A drop of ten from the player's own level: lava at the bottom of the range.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 1, 6), Blocks.LAVA);
			Recorder deep = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, deep);
			helper.assertTrue(deep.events.size() == 2, "lava five below: " + deep.events);

			// A step in between stops it.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 3, 6), Blocks.STONE);
			Recorder stopped = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, stopped);
			helper.assertTrue(stopped.events.isEmpty(), "a ledge should hide the lava below it: " + stopped.events);

			// A torch has no collision box, so it is not a ledge.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 3, 6), Blocks.TORCH);
			Recorder torch = new Recorder();
			AdvisorItem.watch(helper.getLevel(), player, torch);
			helper.assertTrue(torch.events.size() == 2, "a torch is as good as air to upstream's isAir: " + torch.events);

			// The tenth block down is the first past the end: the probe looks at j = 0 to 9, so from
			// a player at y = 12 the last block it reads is y = 3.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 3, 6), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 1, 6), Blocks.AIR);
			Player high = playerAt(helper, 8, 12, 8);
			high.setDeltaMovement(0, 0, -0.2);
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.LAVA);
			Recorder far = new Recorder();
			AdvisorItem.watch(helper.getLevel(), high, far);
			helper.assertTrue(far.events.isEmpty(), "lava ten below the probe's column is out of reach: " + far.events);

			Warns.reset();
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 3, 6), Blocks.LAVA);
			Recorder last = new Recorder();
			AdvisorItem.watch(helper.getLevel(), high, last);
			helper.assertTrue(last.events.size() == 2, "lava nine below is the last block it reads: " + last.events);
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	/** A wall between the player and the lava hides it; walking forward, or standing, or sideways, sees nothing. */
	@GameTest(template = EMPTY16)
	public static void onlyBackwardsWalkingSeesLava(GameTestHelper helper) {
		try {
			floor(helper);
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.LAVA);
			Player player = playerAt(helper, 8, 2, 8);

			player.setDeltaMovement(0, 0, 0.2);
			helper.assertTrue(run(helper, player).isEmpty(), "walking forward saw the lava behind");

			Warns.reset();
			player.setDeltaMovement(0, 0, 0);
			helper.assertTrue(run(helper, player).isEmpty(), "standing still saw the lava behind (a zero velocity has no direction)");

			Warns.reset();
			player.setDeltaMovement(0.2, 0, 0);
			helper.assertTrue(run(helper, player).isEmpty(), "walking sideways saw the lava behind");

			// 45 degrees is the edge: upstream's -0.707 is just inside sin(45).
			double forty = Math.toRadians(40);
			Warns.reset();
			player.setDeltaMovement(Math.sin(forty) * 0.2, 0, -Math.cos(forty) * 0.2);
			helper.assertTrue(run(helper, player).size() == 2, "40 degrees off straight back should still count");

			double fifty = Math.toRadians(50);
			Warns.reset();
			player.setDeltaMovement(Math.sin(fifty) * 0.2, 0, -Math.cos(fifty) * 0.2);
			helper.assertTrue(run(helper, player).isEmpty(), "50 degrees off straight back should not count");

			// Vertical motion is ignored: falling straight back-and-down is still just back.
			Warns.reset();
			player.setDeltaMovement(0, -0.8, -0.2);
			helper.assertTrue(run(helper, player).size() == 2, "the vertical component should be dropped");

			// And the frame turns with the head: facing north, backwards is south.
			Warns.reset();
			player.setYHeadRot(180);
			player.setDeltaMovement(0, 0, -0.2);
			helper.assertTrue(run(helper, player).isEmpty(), "facing north, walking north is walking forward");
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 2, 9), Blocks.LAVA);
			Warns.reset();
			player.setDeltaMovement(0, 0, 0.2);
			helper.assertTrue(run(helper, player).size() == 2, "facing north, walking south with lava south of here");

			// A wall hides it.
			Warns.reset();
			helper.setBlock(new BlockPos(8, 2, 9), Blocks.AIR);
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.LAVA);
			helper.setBlock(new BlockPos(8, 2, 7), Blocks.STONE);
			player.setYHeadRot(0);
			player.setDeltaMovement(0, 0, -0.2);
			helper.assertTrue(run(helper, player).isEmpty(), "a wall between the player and the lava should hide it");
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	private static List<String> run(GameTestHelper helper, Player player) {
		Recorder rec = new Recorder();
		AdvisorItem.watch(helper.getLevel(), player, rec);
		return rec.events;
	}

	/** A stone floor under the 16 by 16 plot at y = 0, so the probe has a bottom. */
	private static void floor(GameTestHelper helper) {
		for(int x = 0; x < 16; x++)
			for(int z = 0; z < 16; z++)
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
	}

	/** Every message key the watch can emit is exactly the nine, so nothing is untranslated. */
	@GameTest(template = EMPTY)
	public static void everyKeyTheWatchCanEmitHasATranslation(GameTestHelper helper) {
		try {
			Set<String> emitted = new LinkedHashSet<>();

			Player player = playerAt(helper, 2.5, 2, 2.5);
			for(Block gas : List.of(ModBlocks.gas_coal.get(), ModBlocks.gas_asbestos.get(), ModBlocks.gas_radon.get())) {
				Warns.reset();
				helper.setBlock(new BlockPos(2, 2, 2), gas);
				collect(emitted, helper, player);
			}
			helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);

			for(int id = 0; id < 5; id++) {
				Warns.reset();
				AdvisorWarningPacket.raise(id);
				collect(emitted, helper, player);
			}

			Warns.reset();
			Warns.toxic = true;
			Warns.toxicStrong = true;
			collect(emitted, helper, player);

			helper.assertTrue(emitted.containsAll(List.of("coal", "asbestos", "radon", "pyro", "skindmg2", "skindmg3", "toxic1", "toxic2")),
					"the flags and gases emitted only " + emitted);
			emitted.add("lava"); // proven by the lava tests, which need a plot of their own

			helper.assertTrue(emitted.equals(new LinkedHashSet<>(KEYS)) || new LinkedHashSet<>(KEYS).containsAll(emitted),
					"the watch emitted a key this test does not know: " + emitted);
			for(String key : KEYS) {
				helper.assertTrue(lang("item.advisor.message." + key + ".name") != null, key + " has no name");
				helper.assertTrue(lang("item.advisor.message." + key + ".message") != null, key + " has no message");
			}
			helper.succeed();
		} finally {
			Warns.reset();
		}
	}

	private static void collect(Set<String> into, GameTestHelper helper, Player player) {
		for(String event : run(helper, player))
			into.add(event.split(":")[1]);
	}

	// ---- the overlay ----

	/** One line per dollar sign, each in gold on the next id up; an id replaces rather than stacking. */
	@GameTest(template = EMPTY)
	public static void messagesSplitOnDollarsAndReplaceByIdInGold(GameTestHelper helper) {
		try {
			InfoSystem.reset();

			AdvisorItem.showMessage("one$two$three", 5000, 3);
			InfoSystem.tick(System.currentTimeMillis());
			helper.assertTrue(InfoSystem.size() == 3, "three lines, got " + InfoSystem.size());

			List<String> texts = new ArrayList<>();
			for(var entry : InfoSystem.sorted()) texts.add(entry.text);
			helper.assertTrue(texts.containsAll(List.of("§6one", "§6two", "§6three")), "gold lines, got " + texts);

			// The same id again replaces its three, it does not add three more.
			AdvisorItem.showMessage("one$two$three", 5000, 3);
			InfoSystem.tick(System.currentTimeMillis());
			helper.assertTrue(InfoSystem.size() == 3, "a repeat stacked, got " + InfoSystem.size());

			// A neighbouring id is a different message: id 4 starts at 1121 + 40, clear of id 3's 1121 + 30..32.
			AdvisorItem.showMessage("other", 5000, 4);
			InfoSystem.tick(System.currentTimeMillis());
			helper.assertTrue(InfoSystem.size() == 4, "a second id should add one line, got " + InfoSystem.size());

			// A message with more than ten lines would run into the next id; none does.
			helper.succeed();
		} finally {
			InfoSystem.reset();
		}
	}

	// ---- the scroll bar ----

	/** Upstream's arithmetic, on the screen's own track: a 15-pixel bar on 106. */
	@GameTest(template = EMPTY)
	public static void theScrollBarArithmeticIsUpstreams(GameTestHelper helper) {

		helper.assertTrue(ScrollUtil._getScrollRatio(15, 106, 7.5) == 0, "the bar's centre at the top of its travel is 0");
		helper.assertTrue(ScrollUtil._getScrollRatio(15, 106, -50) == 0, "clamped below");
		helper.assertTrue(ScrollUtil._getScrollRatio(15, 106, 98.5) == 1, "the bar's centre at the bottom of its travel is 1");
		helper.assertTrue(ScrollUtil._getScrollRatio(15, 106, 500) == 1, "clamped above");
		helper.assertTrue(Math.abs(ScrollUtil._getScrollRatio(15, 106, 53) - 0.5) < 1e-9, "halfway");

		helper.assertTrue(ScrollUtil._getScrollOffset(15, 106, 53, 10) == 5, "halfway through ten rows is 5");
		helper.assertTrue(ScrollUtil._getScrollOffset(15, 106, 53, 11) == 6, "5.5 + 0.5 rounds up to 6");
		helper.assertTrue(ScrollUtil._getScrollOffset(15, 106, 0, 10) == 0 && ScrollUtil._getScrollOffset(15, 106, 999, 10) == 10,
				"the ends");

		helper.assertTrue(ScrollUtil._getScrollRatio(3, 6) == 0.5, "offset over max");
		helper.assertTrue(Double.isNaN(ScrollUtil._getScrollRatio(0, 0)), "no clamp, so no scroll is NaN - the screen never asks");

		helper.assertTrue(ScrollUtil.getScrollBarPos(15, 106, 0) == 0, "bar at the top");
		helper.assertTrue(ScrollUtil.getScrollBarPos(15, 106, 1) == 91, "bar at the bottom is 106 - 15");
		helper.assertTrue(ScrollUtil.getScrollBarPos(15, 106, 0.5) == 45, "45.5 truncates to 45");
		helper.assertTrue(ScrollUtil.getScrollBarPosDouble(15, 106, 0.5) == 45.5, "and the double keeps the half");
		helper.succeed();
	}

	// ---- the assets that stand in for the renderer ----

	/** The twelve parts, upstream's draw order, each with its upstream texture. */
	@GameTest(template = EMPTY)
	public static void everyPartHasAModelOverTheRightObjectAndTexture(GameTestHelper helper) {

		JsonObject manifest = json("advisor_models.json");

		Set<String> body = new LinkedHashSet<>();
		manifest.getAsJsonArray("body").forEach(e -> body.add(e.getAsString()));
		Set<String> cover = new LinkedHashSet<>();
		manifest.getAsJsonArray("cover").forEach(e -> cover.add(e.getAsString()));
		helper.assertTrue(body.equals(BODY.keySet()), "body parts are " + body);
		helper.assertTrue(cover.equals(COVER.keySet()), "cover parts are " + cover);

		Set<String> declared = new LinkedHashSet<>();
		for(String line : lines("models/blocks/advisor.obj"))
			if(line.startsWith("o ")) declared.add(line.substring(2).trim());
		helper.assertTrue(declared.equals(new LinkedHashSet<>(List.of("base", "hingeBase", "logo", "screen", "solar", "antenna",
				"cover", "hingeCover", "speakers", "hta", "speakersIn", "instrument"))), "the .obj declares " + declared);

		JsonObject models = manifest.getAsJsonObject("models");
		helper.assertTrue(models.size() == 19, "12 closed parts and 7 open cover parts, got " + models.size());

		for(var entry : models.entrySet()) {
			String name = entry.getKey();
			JsonObject info = entry.getValue().getAsJsonObject();
			String part = info.get("visible").getAsString();
			boolean isCover = COVER.containsKey(part);
			boolean open = name.startsWith("advisor_open_");

			Map<String, String> table = isCover ? COVER : BODY;
			helper.assertTrue(info.get("texture").getAsString().equals(table.get(part)), name + " is not " + table.get(part));
			helper.assertTrue(info.get("obj").getAsString().equals(open ? "models/blocks/advisor_open.obj" : "models/blocks/advisor.obj"),
					name + " is over " + info.get("obj"));
			helper.assertTrue(!open || isCover, name + ": only cover parts have an open model");

			JsonObject model = json("models/block/" + name + ".json");
			helper.assertTrue(model.get("loader").getAsString().equals("neoforge:obj"), name + " loader");
			helper.assertTrue(model.get("model").getAsString().equals("leafia:" + info.get("obj").getAsString()), name + " model");

			Set<String> hidden = new LinkedHashSet<>();
			model.getAsJsonObject("visibility").entrySet().forEach(v -> {
				if(!v.getValue().getAsBoolean()) hidden.add(v.getKey());
			});
			Set<String> expectedHidden = new LinkedHashSet<>(declared);
			expectedHidden.remove(part);
			helper.assertTrue(hidden.equals(expectedHidden), name + " hides " + hidden + " but should hide every part but " + part);

			String texture = model.getAsJsonObject("textures").get("0").getAsString();
			helper.assertTrue(texture.equals("leafia:item/advisor_" + table.get(part)), name + " texture " + texture);
			png("textures/item/advisor_" + table.get(part) + ".png");
		}
		helper.succeed();
	}

	/** Upstream binds the one white pixel and multiplies it by a grey; here the product is the pixel. */
	@GameTest(template = EMPTY)
	public static void theSolidColoursAreUpstreamsGreys(GameTestHelper helper) {

		int[][] expected = {{0x66}, {0x00}, {0xbf}};
		String[] names = {"hinge", "black", "instrument"};
		double[] upstream = {0.4, 0.0, 0.75};
		for(int i = 0; i < 3; i++) {
			BufferedImage image = png("textures/item/advisor_" + names[i] + ".png");
			helper.assertTrue(image.getWidth() == 1 && image.getHeight() == 1, names[i] + " is " + image.getWidth() + "x" + image.getHeight());
			int rgb = image.getRGB(0, 0);
			helper.assertTrue(((rgb >> 16) & 0xFF) == expected[i][0] && ((rgb >> 8) & 0xFF) == expected[i][0] && (rgb & 0xFF) == expected[i][0],
					names[i] + " is " + Integer.toHexString(rgb) + ", expected grey " + Integer.toHexString(expected[i][0]));
			helper.assertTrue(expected[i][0] == (int) (upstream[i] * 255), names[i] + " is not LeafiaColor(" + upstream[i] + ") times white");
		}
		helper.succeed();
	}

	/** The open model is the closed one with the cover turned half a turn about the hinge, in the file. */
	@GameTest(template = EMPTY)
	public static void theOpenObjIsTheClosedOneWithTheCoverTurned(GameTestHelper helper) {

		List<String> closed = lines("models/blocks/advisor.obj");
		List<String> open = lines("models/blocks/advisor_open.obj");
		helper.assertTrue(closed.size() == open.size(), "the files differ in length");

		double[] hinge = {1.375, 0.125}; // twice upstream's translate(0.6875, 0.0625)
		String object = null;
		int turned = 0, kept = 0, normalsTurned = 0;
		for(int i = 0; i < closed.size(); i++) {
			String a = closed.get(i);
			String b = open.get(i);
			if(a.startsWith("o ")) object = a.substring(2).trim();
			boolean isCover = object != null && COVER.containsKey(object);

			if(a.startsWith("v ")) {
				double[] p = parse(a), q = parse(b);
				if(isCover) {
					helper.assertTrue(Math.abs(q[0] - (hinge[0] - p[0])) < 1e-5 && Math.abs(q[1] - (hinge[1] - p[1])) < 1e-5
							&& Math.abs(q[2] - p[2]) < 1e-5, object + " vertex " + i + " is not turned about the hinge");
					turned++;
				} else {
					helper.assertTrue(Math.abs(q[0] - p[0]) < 1e-5 && Math.abs(q[1] - p[1]) < 1e-5 && Math.abs(q[2] - p[2]) < 1e-5,
							object + " vertex " + i + " moved though it is body");
					kept++;
				}
			} else if(a.startsWith("vn ")) {
				double[] p = parse(a), q = parse(b);
				if(isCover) {
					helper.assertTrue(Math.abs(q[0] + p[0]) < 1e-3 && Math.abs(q[1] + p[1]) < 1e-3 && Math.abs(q[2] - p[2]) < 1e-3,
							object + " normal " + i + " is not turned half a turn about Z");
					normalsTurned++;
				} else {
					helper.assertTrue(Math.abs(q[0] - p[0]) < 1e-3 && Math.abs(q[1] - p[1]) < 1e-3 && Math.abs(q[2] - p[2]) < 1e-3,
							object + " normal " + i + " changed though it is body");
				}
			} else {
				helper.assertTrue(a.equals(b), "line " + i + " differs: '" + a + "' vs '" + b + "'");
			}
		}
		helper.assertTrue(turned > 50 && kept > 50 && normalsTurned > 10,
				"the check ran over " + turned + " cover and " + kept + " body vertices and " + normalsTurned + " cover normals");
		helper.succeed();
	}

	private static double[] parse(String line) {
		String[] f = line.trim().split("\\s+");
		return new double[] {Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3])};
	}

	/**
	 * The item model: a composite of the twelve closed parts, an override on {@code leafia:open}
	 * to a composite that swaps the seven cover parts for the turned ones, and one display block
	 * shared by both so the two sit in the hand identically.
	 */
	@GameTest(template = EMPTY)
	public static void theItemModelSwapsTheCoverWhenOpen(GameTestHelper helper) {

		JsonObject closed = json("models/item/advisor.json");
		JsonObject open = json("models/item/advisor_open.json");

		helper.assertTrue(closed.get("loader").getAsString().equals("neoforge:composite"), "closed loader");
		helper.assertTrue(open.get("loader").getAsString().equals("neoforge:composite"), "open loader");

		Set<String> closedChildren = closed.getAsJsonObject("children").keySet();
		Set<String> expectedClosed = new LinkedHashSet<>();
		for(String p : BODY.keySet()) expectedClosed.add("advisor_" + p.toLowerCase());
		for(String p : COVER.keySet()) expectedClosed.add("advisor_" + p.toLowerCase());
		helper.assertTrue(closedChildren.equals(expectedClosed), "closed children " + closedChildren);

		Set<String> expectedOpen = new LinkedHashSet<>();
		for(String p : BODY.keySet()) expectedOpen.add("advisor_" + p.toLowerCase());
		for(String p : COVER.keySet()) expectedOpen.add("advisor_open_" + p.toLowerCase());
		helper.assertTrue(open.getAsJsonObject("children").keySet().equals(expectedOpen),
				"open children " + open.getAsJsonObject("children").keySet());

		for(var e : open.getAsJsonObject("children").entrySet())
			helper.assertTrue(e.getValue().getAsJsonObject().get("parent").getAsString().equals("leafia:block/" + e.getKey()),
					e.getKey() + " points somewhere else");

		List<String> order = new ArrayList<>();
		closed.getAsJsonArray("item_render_order").forEach(e -> order.add(e.getAsString()));
		helper.assertTrue(new LinkedHashSet<>(order).equals(closedChildren) && order.size() == 12, "the render order covers every child once");

		var overrides = closed.getAsJsonArray("overrides");
		helper.assertTrue(overrides.size() == 1, "one override");
		JsonObject override = overrides.get(0).getAsJsonObject();
		helper.assertTrue(override.getAsJsonObject("predicate").get("leafia:open").getAsDouble() == 1, "the predicate is leafia:open at 1");
		helper.assertTrue(override.get("model").getAsString().equals("leafia:item/advisor_open"), "the override's model");
		helper.assertTrue(!open.has("overrides"), "the open model overrides nothing, or it would recurse");

		helper.assertTrue(closed.get("display").equals(open.get("display")), "the two models sit differently in the hand");
		helper.succeed();
	}

	/**
	 * The display block puts the model where vanilla puts a block: for every context, the middle of
	 * the model's box lands on the point vanilla's own {@code block/block} display puts the middle
	 * of a unit cube, and in the inventory the whole box fits the slot.
	 *
	 * <p>This is the test the first version of the generator would have failed. It derived each
	 * translation without the half-block shift vanilla applies before the display transform, and
	 * the advisor, whose box is a block away from the origin, drew nothing in the hotbar. Run
	 * through JOML with the same order vanilla multiplies in (translate, rotate XYZ, scale, then
	 * the half-block shift), so it checks the arithmetic and not the generator's reading of it.
	 */
	@GameTest(template = EMPTY)
	public static void theDisplayPutsTheModelWhereABlockWouldSit(GameTestHelper helper) {

		double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for(String line : lines("models/blocks/advisor.obj")) {
			if(!line.startsWith("v ")) continue;
			double[] p = parse(line);
			for(int i = 0; i < 3; i++) {
				lo[i] = Math.min(lo[i], p[i]);
				hi[i] = Math.max(hi[i], p[i]);
			}
		}
		helper.assertTrue(hi[2] - lo[2] > 0.8, "the model's depth is " + (hi[2] - lo[2]) + ", antenna included");

		// Where vanilla's block/block display puts the middle of a block, in sixteenths - except
		// the first-person hand, which is raised seven and moved three toward the middle of the
		// screen so a long model does not run off the corner of it.
		Map<String, double[]> home = Map.of(
				"gui", new double[] {0, 0, 0},
				"ground", new double[] {0, 3, 0},
				"fixed", new double[] {0, 0, 0},
				"thirdperson_righthand", new double[] {0, 2.5, 0},
				"thirdperson_lefthand", new double[] {0, 2.5, 0},
				"firstperson_righthand", new double[] {-3, 7, 0},
				"firstperson_lefthand", new double[] {-3, 7, 0});

		JsonObject display = json("models/item/advisor.json").getAsJsonObject("display");
		helper.assertTrue(display.keySet().equals(home.keySet()), "contexts: " + display.keySet());

		for(String context : home.keySet()) {
			JsonObject d = display.getAsJsonObject(context);
			double[] t = {d.getAsJsonArray("translation").get(0).getAsDouble(), d.getAsJsonArray("translation").get(1).getAsDouble(),
					d.getAsJsonArray("translation").get(2).getAsDouble()};
			double[] r = {d.getAsJsonArray("rotation").get(0).getAsDouble(), d.getAsJsonArray("rotation").get(1).getAsDouble(),
					d.getAsJsonArray("rotation").get(2).getAsDouble()};
			float sc = d.getAsJsonArray("scale").get(0).getAsFloat();

			org.joml.Matrix4f m = new org.joml.Matrix4f()
					.translate((float) (t[0] / 16), (float) (t[1] / 16), (float) (t[2] / 16))
					.rotateXYZ((float) Math.toRadians(r[0]), (float) Math.toRadians(r[1]), (float) Math.toRadians(r[2]))
					.scale(sc)
					.translate(-0.5f, -0.5f, -0.5f);

			org.joml.Vector3f middle = m.transformPosition(new org.joml.Vector3f(
					(float) ((lo[0] + hi[0]) / 2), (float) ((lo[1] + hi[1]) / 2), (float) ((lo[2] + hi[2]) / 2)));
			double[] want = home.get(context);
			helper.assertTrue(Math.abs(middle.x - want[0] / 16) < 0.01 && Math.abs(middle.y - want[1] / 16) < 0.01 && Math.abs(middle.z - want[2] / 16) < 0.01,
					context + ": the middle lands at " + middle + ", expected (" + want[0] / 16 + ", " + want[1] / 16 + ", " + want[2] / 16 + ")");

			if(context.equals("gui")) {
				for(int k = 0; k < 8; k++) {
					org.joml.Vector3f corner = m.transformPosition(new org.joml.Vector3f(
							(float) ((k & 1) == 0 ? lo[0] : hi[0]), (float) ((k & 2) == 0 ? lo[1] : hi[1]), (float) ((k & 4) == 0 ? lo[2] : hi[2])));
					helper.assertTrue(Math.abs(corner.x) <= 0.5 && Math.abs(corner.y) <= 0.5,
							"a corner of the box leaves the inventory slot: " + corner);
				}
			}
		}
		helper.succeed();
	}

	/** The two screens' backgrounds are the 256 by 256 sheets the blits assume. */
	@GameTest(template = EMPTY)
	public static void theScreenTexturesAre256Square(GameTestHelper helper) {
		for(String name : List.of("main", "message")) {
			BufferedImage image = png("textures/gui/advisor/" + name + ".png");
			helper.assertTrue(image.getWidth() == 256 && image.getHeight() == 256,
					name + ".png is " + image.getWidth() + "x" + image.getHeight() + ", and blit's default sheet is 256");
		}
		// The three row sprites the list draws from the main sheet are opaque: (0,145) idle and (0,163) selected, 162x18.
		BufferedImage main = png("textures/gui/advisor/main.png");
		for(int y : new int[] {145, 163})
			helper.assertTrue(((main.getRGB(0, y) >>> 24) & 0xFF) == 255 && ((main.getRGB(161, y + 17) >>> 24) & 0xFF) == 255,
					"the row sprite at (0," + y + ") is not opaque at its corners");
		// And the two scroll-bar sprites at (195,0) and (207,0), 12 by 15.
		for(int x : new int[] {195, 207})
			helper.assertTrue(((main.getRGB(x, 0) >>> 24) & 0xFF) != 0 && ((main.getRGB(x + 11, 14) >>> 24) & 0xFF) != 0,
					"the scroll-bar sprite at (" + x + ",0) is empty at its corners");
		helper.succeed();
	}

	/** Upstream's lang, verbatim, under upstream's keys - and the item's name under 1.21.1's. */
	@GameTest(template = EMPTY)
	public static void theLangIsUpstreams(GameTestHelper helper) {
		helper.assertTrue("High-Tech Advisor".equals(lang("item.leafia.advisor")), "name: " + lang("item.leafia.advisor"));
		helper.assertTrue(lang("item.advisor.name") == null, "upstream's name key was kept as well as the 1.21.1 one");
		helper.assertTrue("Keep this in inventory for advices for new players".equals(lang("item.advisor.desc")), "desc");
		helper.assertTrue("Dismiss".equals(lang("item.advisor.gui.main.dismiss")), "dismiss");
		helper.assertTrue("Read".equals(lang("item.advisor.gui.main.read")), "read");
		helper.assertTrue("Back".equals(lang("item.advisor.gui.message.close")), "back");
		helper.assertTrue("Lava behind you!".equals(lang("item.advisor.message.lava.message")), "lava");
		helper.assertTrue(lang("item.advisor.message.coal.message").contains("$"), "the line break is kept as upstream's $");
		helper.assertTrue(lang("item.advisor.message.toxic1.message").split("\\$").length == 3, "toxic1 is three lines");
		helper.succeed();
	}
}
