package com.leafia.contents.gear.advisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.render.util.InfoSystem;
import com.hbm.render.util.InfoSystem.InfoEntry;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.advisor.container.AdvisorUI;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.technical.FifthString;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The High-Tech Advisor: a pocket computer that warns a new player about what is hurting them,
 * and keeps a list of everything it has said.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/advisor/AdvisorItem.java}. Everything here is
 * upstream's rule, one file, in four parts:
 *
 * <h2>The cover</h2>
 *
 * Right-clicking opens the screen on the client and, on the server, opens the <em>cover</em>: the
 * stack's tag becomes {@code {open:true}}, which the item model reads to draw the lid folded out
 *. Closing the screen sends {@code CLOSE}, and the
 * server wipes the tag from every advisor the player carries. Upstream wipes the <em>whole</em>
 * tag; the tag holds nothing else, so removing the component is the same.
 *
 * <p>Upstream's open branch tests the main hand twice - the second was meant to be the off hand.
 * Only the main hand can ever open the cover ({@link #use} refuses the other), so the dead branch
 * is dropped and nothing is lost.
 *
 * <h2>The watch</h2>
 *
 * While an advisor is anywhere in the local player's inventory, {@link #inventoryTick} runs on
 * the client every tick and looks for five things: coal, asbestos and radon gas at the player's
 * feet or head with no mask that stops them, lava within four blocks behind the player while they
 * are walking backwards, and the five flags the server sets through {@link AdvisorWarningPacket}
 * (a hot item, damaged skin II and III, toxic and highly toxic). Each is a gold overlay message
 * under a fixed id, so it replaces itself rather than stacking, and a one-shot entry in the
 * history list once its cooldown has run out.
 *
 * <p>The decisions are in {@link #watch}, which takes an {@link AlertSink} and touches nothing
 * client-side, so a dedicated server can run it and a test can read what it would have said.
 *
 * <h2>The history</h2>
 *
 * Kept on the <em>server</em>, in the player's {@code PlayerPersisted} tag, which NeoForge copies
 * across death exactly as Forge did: {@code leafia_advisor.history}, a list of message keys,
 * oldest first. The screen asks for it with {@code SYNC_REQUEST}, and every change is sent back
 * whole as {@code SYNC_LOCAL}.
 *
 * <h2>The first copy</h2>
 *
 * Every player is handed one advisor the first time they join, tracked by the same persisted tag
 * ({@code receivedAdvisor}). Upstream does it in its server listener; it is an event handler
 * here, gated by {@link LeafiaFeatures#advisor}.
 *
 * <h2>Upstream's defects, flagged and kept (D-021)</h2>
 *
 * See D-1080: the pyrophoric cooldown never counts down; the watch runs for <em>any</em> player
 * entity whose inventory ticks on the client, including other players holding an advisor; the
 * server stores any string a client sends as a history entry; a full inventory loses the first
 * copy and still marks it as given.
 */
public class AdvisorItem extends Item {

	/** Upstream's NBT key for the open cover. */
	public static final String OPEN = "open";

	/** Upstream's {@code msgRoot}: {@code item.advisor.message.<key>.name} / {@code .message}. */
	public static final String MSG_ROOT = "item.advisor.message.";

	/** The persisted-tag compound the history lives in, and its list. Upstream's keys. */
	public static final String PERSIST_KEY = "leafia_advisor";
	public static final String HISTORY_KEY = "history";

	/** The persisted-tag flag for the free copy. Upstream's key. */
	public static final String RECEIVED_KEY = "receivedAdvisor";

	/** How long a warning stays on screen, in milliseconds. Upstream's {@code len}. */
	public static final int LEN = 10000;

	/** One minute of ticks: how long a gas or toxic alert stays quiet after it fires. */
	public static final int GAS_COOLDOWN = 20 * 60;

	/** Upstream's first overlay id; each message takes ten of them. */
	private static final int INFO_ID_BASE = 1121;

	public AdvisorItem(Properties properties) {
		super(properties);
	}

	// ---- the cover ----

	/** Whether this stack's cover is open. */
	public static boolean isOpen(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(OPEN);
	}

	/**
	 * Opens the cover of the advisor in the main hand, or closes every advisor the player carries.
	 * Upstream's {@code setCover}. Server side.
	 */
	public static void setCover(Player player, boolean open) {

		if(open) {
			ItemStack stack = player.getMainHandItem();
			if(stack.getItem() instanceof AdvisorItem) {
				CompoundTag tag = new CompoundTag();
				tag.putBoolean(OPEN, true);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		} else {
			for(ItemStack stack : player.getInventory().items)
				close(stack);
			for(ItemStack stack : player.getInventory().offhand)
				close(stack);
			// "don't ask why" - upstream, on this very loop
			for(ItemStack stack : player.getInventory().armor)
				close(stack);
		}

		player.inventoryMenu.broadcastChanges();
	}

	private static void close(ItemStack stack) {
		if(stack.getItem() instanceof AdvisorItem)
			stack.remove(DataComponents.CUSTOM_DATA);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

		ItemStack stack = player.getItemInHand(hand);
		if(hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.fail(stack);

		if(!level.isClientSide)
			setCover(player, true);
		else
			ClientOnly.openUI();

		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		for(String line : I18nUtil.resolveKey("item.advisor.desc").split("\\$"))
			tooltip.add(Component.literal(line));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	// ---- the history, on the server ----

	/** The player's persisted tag, made if it is not there. */
	private static CompoundTag persisted(Player player) {
		CompoundTag data = player.getPersistentData();
		if(!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
			data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
		return data.getCompound(Player.PERSISTED_NBT_TAG);
	}

	/** The advisor's own compound inside it, made if it is not there. */
	private static CompoundTag advisorTag(Player player) {
		CompoundTag persisted = persisted(player);
		if(!persisted.contains(PERSIST_KEY, Tag.TAG_COMPOUND))
			persisted.put(PERSIST_KEY, new CompoundTag());
		return persisted.getCompound(PERSIST_KEY);
	}

	/** Appends {@code key} to the stored history, without telling the client. */
	public static void storeWarning(Player player, String key) {
		CompoundTag tag = advisorTag(player);
		ListTag list = tag.getList(HISTORY_KEY, Tag.TAG_STRING);
		list.add(StringTag.valueOf(key));
		tag.put(HISTORY_KEY, list);
	}

	/** Removes one entry, by its index in the stored (oldest-first) list. Out of range is ignored. */
	public static void removeStoredWarning(Player player, int index) {
		CompoundTag tag = advisorTag(player);
		ListTag list = tag.getList(HISTORY_KEY, Tag.TAG_STRING);
		if(index >= 0 && index < list.size())
			list.remove(index);
		tag.put(HISTORY_KEY, list);
	}

	/** Upstream's {@code getWarningHistory}: the stored keys, oldest first, appended to {@code target}. */
	public static List<String> getWarningHistory(Player player, List<String> target) {
		ListTag list = advisorTag(player).getList(HISTORY_KEY, Tag.TAG_STRING);
		for(int i = 0; i < list.size(); i++)
			target.add(list.getString(i));
		return target;
	}

	public static void addWarningHistory(Player player, String id) {
		storeWarning(player, id);
		syncHistory(player);
	}

	/**
	 * Every message key the watch can raise, which is every key a client may ask the server to
	 * store. Upstream stored any string it was sent (flagged in D-1080); the nine here are the
	 * ones {@link #watch} calls {@code sink.warn} with, and the screen has a lang entry for each.
	 */
	public static final List<String> WARNING_KEYS = List.of(
			"coal", "asbestos", "radon", "pyro", "skindmg2", "skindmg3", "toxic1", "toxic2", "lava");

	/**
	 * The most entries a player's stored history may hold. One is added per warning, once its
	 * cooldown has run out, so a long game can reach a few dozen; a client sending {@code WARN} in a
	 * loop would otherwise grow the player's save file without end. At the cap the oldest entry goes.
	 */
	public static final int MAX_HISTORY = 256;

	/** Whether the player is carrying an advisor anywhere: the hotbar, the inventory or the off hand. */
	public static boolean carriesAdvisor(Player player) {
		for(ItemStack stack : player.getInventory().items)
			if(stack.getItem() instanceof AdvisorItem) return true;
		for(ItemStack stack : player.getInventory().offhand)
			if(stack.getItem() instanceof AdvisorItem) return true;
		return false;
	}

	/**
	 * Whether the player has the advisor's screen open, as far as the server can tell: the advisor
	 * in the main hand has its cover open, which {@link #use} sets before the client opens the
	 * screen and {@code CLOSE} clears when it closes. The screen has no menu to ask.
	 */
	public static boolean screenOpen(Player player) {
		ItemStack stack = player.getMainHandItem();
		return stack.getItem() instanceof AdvisorItem && isOpen(stack);
	}

	/** {@code WARN}, validated (D-1094): a known key, from a player who carries an advisor. */
	public static boolean acceptWarning(Player player, String key) {

		if(!WARNING_KEYS.contains(key)) {
			PacketGuard.reject("AdvisorPacket.WARN", player, "unknown key of " + key.length() + " characters");
			return false;
		}

		if(!carriesAdvisor(player)) {
			PacketGuard.reject("AdvisorPacket.WARN", player, "no advisor carried");
			return false;
		}

		CompoundTag tag = advisorTag(player);
		ListTag list = tag.getList(HISTORY_KEY, Tag.TAG_STRING);
		while(list.size() >= MAX_HISTORY) list.remove(0);
		tag.put(HISTORY_KEY, list);

		addWarningHistory(player, key);
		return true;
	}

	/** {@code DISMISS}, validated: the screen is open and the index names an entry that exists. */
	public static boolean acceptDismissal(Player player, int index) {

		if(!screenOpen(player)) {
			PacketGuard.reject("AdvisorPacket.DISMISS", player, "the screen is not open");
			return false;
		}

		int size = advisorTag(player).getList(HISTORY_KEY, Tag.TAG_STRING).size();

		if(index < 0 || index >= size) {
			PacketGuard.reject("AdvisorPacket.DISMISS", player, "index " + index + " of " + size);
			return false;
		}

		removeWarningHistory(player, index);
		return true;
	}

	/** {@code SYNC_REQUEST}, validated: only the open screen asks. */
	public static boolean acceptSyncRequest(Player player) {

		if(!screenOpen(player)) {
			PacketGuard.reject("AdvisorPacket.SYNC_REQUEST", player, "the screen is not open");
			return false;
		}

		syncHistory(player);
		return true;
	}

	public static void removeWarningHistory(Player player, int index) {
		removeStoredWarning(player, index);
		syncHistory(player);
	}

	/** Sends the whole history to the player, as {@code SYNC_LOCAL}. */
	public static void syncHistory(Player player) {
		AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.SYNC_LOCAL.id);
		packet.warnings = getWarningHistory(player, new ArrayList<>());
		LeafiaCustomPacket.__start(packet).__sendToClient(player);
	}

	/**
	 * The history as the screen lists it: newest first. Row {@code p} of the list is stored entry
	 * {@code size - 1 - p}, which is the arithmetic the Dismiss button uses to go back.
	 */
	public static List<String> displayOrder(List<String> oldestFirst) {
		List<String> rows = new ArrayList<>(oldestFirst);
		Collections.reverse(rows);
		return rows;
	}

	// ---- the first copy ----

	/**
	 * Hands the player their one free advisor if they have not had it. Upstream's
	 * {@code onPlayerLogin}.
	 *
	 * @return whether a copy was given
	 */
	public static boolean giveFirstAdvisor(ServerPlayer player) {

		CompoundTag persisted = persisted(player);
		if(persisted.getBoolean(RECEIVED_KEY)) return false;

		persisted.putBoolean(RECEIVED_KEY, true);
		// Not checked: a full inventory loses the copy and keeps the flag, as upstream's does.
		player.getInventory().add(new ItemStack(LeafiaItems.advisor.get()));
		player.inventoryMenu.broadcastChanges();
		return true;
	}

	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class ServerEvents {

		private ServerEvents() {}

		@SubscribeEvent
		public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if(!LeafiaFeatures.advisor) return;
			if(event.getEntity() instanceof ServerPlayer player)
				giveFirstAdvisor(player);
		}
	}

	// ---- the packets ----

	/** Upstream's {@code AdvisorSignalType}. The ids are on the wire. */
	public enum AdvisorSignalType {
		CLOSE(0), // to server
		WARN(1), // to server
		SYNC_REQUEST(2), // to server
		SYNC_LOCAL(3), // to local
		DISMISS(4), // to server
		;

		public final byte id;

		AdvisorSignalType(int id) {
			this.id = (byte) id;
		}

		public static AdvisorSignalType fromId(byte id) {
			for(AdvisorSignalType value : values()) {
				if(value.id == id) return value;
			}
			throw new LeafiaDevFlaw("what");
		}
	}

	/**
	 * Upstream's {@code AdvisorPacket}: the screen's whole conversation with the server, one
	 * packet type with a signal in front.
	 *
	 * <p>Upstream decides which half to run from the receiving handler's class; here it is the
	 * flow of the payload, and a signal that arrives going the wrong way does nothing. That is
	 * stricter than upstream, which would run {@code SYNC_LOCAL}'s client branch only on a client
	 * and so also did nothing - the same behaviour, reached by a different test.
	 */
	public static class AdvisorPacket implements LeafiaCustomPacketEncoder {

		public byte signalType;
		public String warningKey;
		public List<String> warnings;
		public int dismissIndex;

		public AdvisorPacket() { }

		public AdvisorPacket(int signalType) {
			this.signalType = (byte) signalType;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeByte(signalType);
			switch(AdvisorSignalType.fromId(signalType)) {
				case WARN -> buf.writeFifthString(new FifthString(warningKey));
				case SYNC_LOCAL -> {
					buf.writeInt(warnings.size());
					for(String warning : warnings)
						buf.writeFifthString(new FifthString(warning));
				}
				case DISMISS -> buf.writeInt(dismissIndex);
				default -> { }
			}
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		/**
		 * The three signals that come from the screen are validated (D-1094, closing D-1080's flag that
		 * the server stored whatever string it was sent): {@code WARN} against {@link #WARNING_KEYS},
		 * {@code DISMISS} against the stored list's length, and both {@code DISMISS} and
		 * {@code SYNC_REQUEST} against the screen being open. An unknown signal id drops the packet
		 * instead of throwing, and a {@code SYNC_LOCAL} count that runs past the packet is refused
		 * before anything is allocated for it.
		 */
		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			byte id = buf.readByte();
			AdvisorSignalType type = null;
			for(AdvisorSignalType value : AdvisorSignalType.values())
				if(value.id == id) type = value;
			if(type == null) return null;

			return switch(type) {
				case CLOSE -> ctx -> {
					if(ctx.flow() == PacketFlow.SERVERBOUND) setCover(ctx.player(), false);
				};
				case WARN -> {
					String key = buf.readFifthString().toString();
					yield ctx -> {
						if(ctx.flow() == PacketFlow.SERVERBOUND) acceptWarning(ctx.player(), key);
					};
				}
				case SYNC_REQUEST -> ctx -> {
					if(ctx.flow() == PacketFlow.SERVERBOUND) acceptSyncRequest(ctx.player());
				};
				case SYNC_LOCAL -> {
					int length = buf.readInt();
					// Every string is at least its 5-bit terminator, so a count beyond the bits left is a lie.
					if(length < 0 || length > buf.readableBits() / 5) yield null;
					List<String> read = new ArrayList<>();
					for(int i = 0; i < length; i++)
						read.add(buf.readFifthString().toString());
					yield ctx -> {
						if(ctx.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient()) ClientOnly.syncLocal(read);
					};
				}
				case DISMISS -> {
					int index = buf.readInt();
					yield ctx -> {
						if(ctx.flow() == PacketFlow.SERVERBOUND) acceptDismissal(ctx.player(), index);
					};
				}
			};
		}
	}

	/**
	 * Upstream's {@code AdvisorWarningPacket}: the server telling the local client that
	 * something it cannot see from there is hurting the player. One int, five meanings.
	 */
	public static class AdvisorWarningPacket implements LeafiaCustomPacketEncoder {

		public static final int PYROPHORIC = 0;
		public static final int SKIN_DAMAGE_2 = 1;
		public static final int SKIN_DAMAGE_3 = 2;
		public static final int TOXIC = 3;
		public static final int TOXIC_STRONG = 4;

		public int warningId;

		public AdvisorWarningPacket() { }

		public AdvisorWarningPacket(int warningId) {
			this.warningId = warningId;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(warningId);
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND) raise(id);
			};
		}

		/** Sets the flag an id names; an id upstream has no flag for sets nothing. */
		public static void raise(int id) {
			if(id == PYROPHORIC)
				Warns.pyro = true;
			else if(id == SKIN_DAMAGE_2)
				Warns.skinDmg2 = true;
			else if(id == SKIN_DAMAGE_3)
				Warns.skinDmg3 = true;
			else if(id == TOXIC)
				Warns.toxic = true;
			else if(id == TOXIC_STRONG)
				Warns.toxicStrong = true;
		}
	}

	// ---- the watch ----

	/**
	 * Upstream's {@code Warns}: the client's memory between ticks. The five flags are raised by
	 * packets and cleared at the end of every client tick, so each lasts exactly the tick after
	 * it arrives; the three counters are cooldowns in ticks.
	 */
	public static class Warns {

		public static int gas = 0;
		public static boolean pyro = false;
		/** <b>Never counts down</b> - {@link #preTick} does not touch it. Upstream's, kept. */
		public static int pyroCooldown = 0;
		public static boolean skinDmg2 = false;
		public static boolean skinDmg3 = false;
		public static boolean toxic = false;
		public static boolean toxicStrong = false;
		public static int toxicCooldown = 0;
		public static int lava = 0;

		public static void preTick() {
			gas = decrement(gas);
			pyro = false;
			skinDmg2 = false;
			skinDmg3 = false;
			lava = decrement(lava);
			toxic = false;
			toxicStrong = false;
			toxicCooldown = decrement(toxicCooldown);
		}

		/** Back to a fresh session, for a test. */
		public static void reset() {
			gas = 0;
			pyro = false;
			pyroCooldown = 0;
			skinDmg2 = false;
			skinDmg3 = false;
			toxic = false;
			toxicStrong = false;
			toxicCooldown = 0;
			lava = 0;
		}

		static int decrement(int v) {
			return Math.max(v - 1, 0);
		}
	}

	/**
	 * What the watch does about an alert. {@link #warn} is upstream's {@code warnPlayer(true,
	 * key)}: a history entry and the warning sound. {@link #show} is
	 * {@code showMessage(msg(key), millis, id)}: the overlay.
	 */
	public interface AlertSink {
		void warn(String key);

		void show(String key, int millis, int id);
	}

	/**
	 * Upstream's {@code onUpdate} body, minus the client-only parts.
	 *
	 * <p>The order is upstream's, and it matters: a gas alert at the feet is raised before the one
	 * at the head, and the cooldown a first alert sets is what stops the second from sounding
	 * twice in one tick.
	 */
	public static void watch(Level level, Player player, AlertSink sink) {

		BlockPos pos = player.blockPosition();
		gasAlert(level, pos, player, sink);
		gasAlert(level, pos.above(), player, sink);

		if(Warns.pyro) {
			if(Warns.pyroCooldown <= 0)
				sink.warn("pyro");
			Warns.pyroCooldown = GAS_COOLDOWN;
			sink.show("pyro", LEN, 2);
		}
		if(Warns.skinDmg2) {
			sink.warn("skindmg2");
			sink.show("skindmg2", LEN, 3);
		}
		if(Warns.skinDmg3) {
			sink.warn("skindmg3");
			sink.show("skindmg3", LEN, 3);
		}
		if(Warns.toxic && !Warns.toxicStrong) {
			if(Warns.toxicCooldown <= 0)
				sink.warn("toxic1");
			Warns.toxicCooldown = GAS_COOLDOWN;
			sink.show("toxic1", LEN, 5);
		}
		if(Warns.toxicStrong) {
			if(Warns.toxicCooldown <= 0)
				sink.warn("toxic2");
			Warns.toxicCooldown = GAS_COOLDOWN;
			sink.show("toxic2", LEN, 6);
		}

		lavaBehind(level, player, sink);
	}

	/**
	 * Walking backwards toward lava. The player's velocity is taken into the frame they are
	 * facing, and if it points more than 45 degrees behind them, a column of up to ten blocks is
	 * probed at each of the four blocks behind - stopping at the first solid block - for lava.
	 *
	 * <p>"I must've been smoking my tail when I was coding this but the positive Z is forward for
	 * some reason lmao" - upstream, on the {@code -0.707}. It is kept: that is {@code FiaMatrix}'s
	 * own convention, and the test pins it by walking a player backwards into a known pool.
	 */
	static void lavaBehind(Level level, Player player, AlertSink sink) {

		Vec3 origin = new Vec3(player.getX() + 0.5, player.getY() + 0.5, player.getZ() + 0.5);
		FiaMatrix facing = new FiaMatrix(origin).rotateY(-player.getYHeadRot());
		Vec3 velocity = player.getDeltaMovement();
		Vec3 relativeVelocity = facing.toObjectSpace(facing.add(new Vec3(velocity.x, 0, velocity.z))).position.normalize();

		if(relativeVelocity.z >= -0.707) return;

		boolean lavaWarn = false;
		for(int i = 1; i <= 4; i++) {
			BlockPos p = BlockPos.containing(facing.translate(0, 0, -i).position);
			if(!isAir(level, p)) break;
			for(int j = 0; j < 10; j++) {
				BlockPos p2 = p.below(j);
				if(level.getBlockState(p2).getFluidState().is(FluidTags.LAVA)) {
					lavaWarn = true;
					break;
				}
				if(!isAir(level, p2)) break;
			}
			if(lavaWarn) break;
		}

		if(lavaWarn) {
			if(Warns.lava <= 0)
				sink.warn("lava");
			Warns.lava = 200;
			sink.show("lava", 4000, 4);
		}
	}

	/**
	 * Upstream's {@code isAir}: no collision box, which is wider than "is air" - water, grass and
	 * a torch all count.
	 */
	static boolean isAir(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * The three gases. Each needs its own protection class on the player's head slot (3): coarse
	 * particles for coal dust, fine for asbestos and for all four radon blocks.
	 *
	 * <p>The gas blocks are the base mod's, which registers them as plain blocks until their
	 * classes land, so they are matched by identity and not by {@code instanceof}.
	 */
	public static void gasAlert(Level level, BlockPos pos, Player player, AlertSink sink) {

		Block block = level.getBlockState(pos).getBlock();

		if(block == ModBlocks.gas_coal.get()) {
			if(!ArmorRegistry.hasProtection(player, 3, HazardClass.PARTICLE_COARSE)) {
				if(Warns.gas <= 0)
					sink.warn("coal");
				sink.show("coal", LEN, 0);
				Warns.gas = GAS_COOLDOWN;
			}
		}
		if(block == ModBlocks.gas_asbestos.get()) {
			if(!ArmorRegistry.hasProtection(player, 3, HazardClass.PARTICLE_FINE)) {
				if(Warns.gas <= 0)
					sink.warn("asbestos");
				sink.show("asbestos", LEN, 1);
				Warns.gas = GAS_COOLDOWN;
			}
		}
		if(block == ModBlocks.gas_radon.get() || block == ModBlocks.gas_radon_dense.get()
				|| block == ModBlocks.gas_radon_tomb.get() || block == ModBlocks.gas_meltdown.get()) {
			if(!ArmorRegistry.hasProtection(player, 3, HazardClass.PARTICLE_FINE)) {
				if(Warns.gas <= 0)
					sink.warn("radon");
				sink.show("radon", LEN, 7);
				Warns.gas = GAS_COOLDOWN;
			}
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if(level.isClientSide && entity instanceof Player player)
			ClientOnly.watch(level, player);
	}

	/**
	 * Upstream's {@code showMessage(String, millisec, id)}: the overlay under {@code 1121 + 10 *
	 * id}, one line per {@code $}, each on the next id up, in gold.
	 *
	 * <p>Upstream sends it through the base mod's {@code displayTooltipLegacy}, whose own queue
	 * ({@code RenderInfoSystemLegacy}) is a sibling of the one {@code displayTooltip} uses. This
	 * port has one queue, {@link InfoSystem}, so the messages share it: an id here can in principle
	 * replace an unkeyed message that was numbered the same, but those start at 1000 and count up
	 * by one per push, so it takes 121 of them.
	 */
	public static void showMessage(String msg, int millisec, int id) {
		id *= 10;
		for(String s : msg.split("\\$")) {
			InfoSystem.push(new InfoEntry(ChatFormatting.GOLD + s, millisec), INFO_ID_BASE + id);
			id++;
		}
	}

	/** The overlay text for a message key, translated. Client only in practice: the server's translator is a joke. */
	public static String msg(String key) {
		return I18nUtil.resolveKey(MSG_ROOT + key + ".message");
	}

	// ---- everything that needs a client ----

	/**
	 * The half of the item that names {@code Minecraft}. Behind its own class so a dedicated
	 * server never loads it: every path in is behind a level or dist check.
	 */
	@OnlyIn(Dist.CLIENT)
	static final class ClientOnly {

		private ClientOnly() {}

		static void openUI() {
			Minecraft.getInstance().setScreen(new AdvisorUI());
		}

		/** Upstream's {@code warnPlayer(true, key)}: tell the server, and sound the alarm. */
		static void warnPlayer(boolean playSound, String key) {
			AdvisorPacket packet = new AdvisorPacket(AdvisorSignalType.WARN.id);
			packet.warningKey = key;
			LeafiaCustomPacket.__start(packet).__sendToServer();
			if(playSound)
				Minecraft.getInstance().getSoundManager()
						.play(SimpleSoundInstance.forUI(LeafiaSounds.advisor_warning.get(), 1));
		}

		private static final AlertSink SINK = new AlertSink() {
			@Override
			public void warn(String key) {
				warnPlayer(true, key);
			}

			@Override
			public void show(String key, int millis, int id) {
				showMessage(msg(key), millis, id);
			}
		};

		static void watch(Level level, Player player) {
			AdvisorItem.watch(level, player, SINK);
		}

		/** Upstream's {@code processLocal}: only the open list screen takes a history. */
		static void syncLocal(List<String> oldestFirst) {
			if(AdvisorUI.instance instanceof AdvisorUI main)
				main.setHistory(oldestFirst);
		}
	}

	/** Upstream's {@code priorTick}: the flags clear at the end of every client tick. */
	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
	public static final class ClientEvents {

		private ClientEvents() {}

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			if(Minecraft.getInstance().level != null)
				Warns.preTick();
		}
	}
}
