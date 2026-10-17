package com.leafia.contents.machines.elevators.items.radio;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.hbm.util.EnumUtil;
import com.leafia.contents.machines.elevators.items.radio.container.EvRadioUI;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The elevator radio: an item that holds three track choices - what to play while a cabin goes
 * up, while it goes down, and while it idles - and, put in the cabin's slot 7, plays them.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/items/radio/EvRadioItem.java}. It
 * has been waiting since D-1037 for the one thing it cannot do without, a client audio proxy; that
 * is {@code com.custom_hbm.sound.LCEAudioProxy} now, and the music is played by
 * {@code ElevatorEntity.updateMusic}.
 *
 * <h2>What the stack holds</h2>
 *
 * Upstream's tag keys, unchanged: {@code "up"}, {@code "down"} and {@code "idle"}, each the
 * <em>name</em> of an {@link ElevatorMusic} constant (not its ordinal, so adding a track in the
 * middle of the enum does not change what an old radio plays). Stored in the stack's
 * {@code custom_data} component, which is where 1.21.1 puts a tag an item makes up for itself. A
 * key that is absent, or whose value names no constant, reads as "none" (-1).
 *
 * <h2>The conversation</h2>
 *
 * Right-clicking opens the screen on the client and does nothing on the server. The screen asks
 * for the current choices ({@link EvRadioSyncRequestPacket}, no payload), the server answers with
 * them ({@link EvRadioUIPacket}), and closing the screen sends the same packet back with the new
 * choices, which the server writes onto the radio in the <b>main hand</b>. Both packets only ever
 * look at the main hand - upstream's {@code getHeldItemMainhand} - so a radio held in the off hand
 * opens a screen that says "Loading..." for good. Flagged in D-1088, not fixed.
 *
 * <p>The UI packet is untrusted: a client may send any three bytes. An ordinal other than -1 is
 * folded into the enum with {@code EnumUtil.grabEnumSafely}'s modulo, so it cannot throw and
 * cannot name a track that does not exist; -1 means "none" and removes the key.
 */
public class EvRadioItem extends Item {

	/** The two tracks upstream ships. Their order is the order of the list screen, and the ordinals the cabin's {@code MUSIC} parameter carries. */
	public enum ElevatorMusic {
		LOCAL_FORECAST(LeafiaSounds.local_forecast, "Local Forecast", "Kevin Macleod"),
		ELEVATOR_JAM(LeafiaSounds.elevator_jam_loop, LeafiaSounds.elevator_jam_end, "Elevator Jam", "LSPLASH");

		private final Supplier<SoundEvent> loop;
		@Nullable
		private final Supplier<SoundEvent> end;
		public final String title;
		public final String artist;

		ElevatorMusic(Supplier<SoundEvent> music, String title, String artist) {
			this(music, null, title, artist);
		}

		ElevatorMusic(Supplier<SoundEvent> loop, @Nullable Supplier<SoundEvent> end, String title, String artist) {
			this.loop = loop;
			this.end = end;
			this.title = title;
			this.artist = artist;
		}

		/** The clip that loops while the music plays. Resolved on use: the registry is not filled when the enum is built. */
		public SoundEvent loop() {
			return loop.get();
		}

		/** The clip played once when the music is stopped, or null. */
		@Nullable
		public SoundEvent end() {
			return end == null ? null : end.get();
		}

		/**
		 * Upstream's {@code EnumUtil.grabEnumSafely(ElevatorMusic.values(), ordinal)}: any int
		 * lands on a constant, by modulo. <b>-1 does too</b> (on the last constant), so a caller
		 * that means "none" must test for it first, as every caller does.
		 */
		public static ElevatorMusic ofOrdinal(int ordinal) {
			return EnumUtil.grabEnumSafely(ElevatorMusic.class, ordinal);
		}
	}

	/** Upstream's three tag keys. */
	public static final String UP = "up";
	public static final String DOWN = "down";
	public static final String IDLE = "idle";

	/** The three choices as ordinals, -1 for none. */
	public record Selection(int up, int down, int idle) {
		public static final Selection NONE = new Selection(-1, -1, -1);
	}

	public EvRadioItem(Properties properties) {
		super(properties);
	}

	/** One key of the tag as an ordinal: absent, not a string or not a constant's name is -1. */
	private static int ordinalOf(@Nullable CompoundTag tag, String key) {
		try {
			if(tag != null && tag.contains(key))
				return ElevatorMusic.valueOf(tag.getString(key)).ordinal();
		} catch(IllegalArgumentException ignored) {}
		return -1;
	}

	/**
	 * What a stack's tag says, as upstream's {@code EvRadioSyncRequestPacket} and
	 * {@code ElevatorEntity.getMusic} both read it. A stack with no tag is all -1.
	 */
	public static Selection read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null) return Selection.NONE;
		CompoundTag tag = data.copyTag();
		return new Selection(ordinalOf(tag, UP), ordinalOf(tag, DOWN), ordinalOf(tag, IDLE));
	}

	/**
	 * Replaces the stack's whole tag with the three choices, as upstream's UI packet does: a fresh
	 * compound with a key for each choice that is not -1, so whatever else the tag held is gone and
	 * an all-none selection leaves an empty tag rather than none.
	 */
	public static void write(ItemStack stack, int up, int down, int idle) {
		CompoundTag tag = new CompoundTag();
		if(up != -1) tag.putString(UP, ElevatorMusic.ofOrdinal(up).name());
		if(down != -1) tag.putString(DOWN, ElevatorMusic.ofOrdinal(down).name());
		if(idle != -1) tag.putString(IDLE, ElevatorMusic.ofOrdinal(idle).name());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** The radio the player is holding: the main hand's, else the off hand's (D-3076; upstream asked the main hand only, so an off-hand radio's screen never loaded). */
	@Nullable
	public static ItemStack heldRadio(Player player) {
		if(player.getMainHandItem().getItem() instanceof EvRadioItem) return player.getMainHandItem();
		if(player.getOffhandItem().getItem() instanceof EvRadioItem) return player.getOffhandItem();
		return null;
	}

	/**
	 * Opens the screen on the client; the server does nothing. Both hands succeed, as upstream's
	 * does - see the class note for what that means for the off hand.
	 */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if(level.isClientSide)
			ClientOnly.openUI();
		return InteractionResultHolder.success(player.getItemInHand(hand));
	}

	/**
	 * The screen asking what the radio in the main hand is set to. No payload.
	 *
	 * <p>Server-bound only: one that arrives going the other way does nothing. Upstream sends the
	 * reply to the asking player and not to anyone else, and only if the main hand really holds a
	 * radio.
	 */
	public static class EvRadioSyncRequestPacket implements LeafiaCustomPacketEncoder {

		@Override
		public void encode(LeafiaBuf buf) {
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			return ctx -> {
				if(ctx.flow() != PacketFlow.SERVERBOUND) return;
				Player player = ctx.player();
				ItemStack stack = heldRadio(player);
				if(stack != null) {
					Selection selection = read(stack);
					EvRadioUIPacket packet = new EvRadioUIPacket();
					packet.upOrdinal = selection.up();
					packet.downOrdinal = selection.down();
					packet.idleOrdinal = selection.idle();
					LeafiaCustomPacket reply = LeafiaCustomPacket.__start(packet);
					if(reply != null) reply.__sendToClient(player);
				}
			};
		}
	}

	/**
	 * The radio's choices, three signed bytes, in both directions: to the client as the answer to
	 * the request, to the server as the screen closes.
	 */
	public static class EvRadioUIPacket implements LeafiaCustomPacketEncoder {

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		public int upOrdinal = -1;
		public int downOrdinal = -1;
		public int idleOrdinal = -1;

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeByte(upOrdinal);
			buf.writeByte(downOrdinal);
			buf.writeByte(idleOrdinal);
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int up = buf.readByte();
			int down = buf.readByte();
			int idle = buf.readByte();
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND) {
					// The dist test is not upstream's: one payload type is registered both ways here, so
					// a client can address this id to a server, and the screen it names is not there.
					if(FMLEnvironment.dist.isClient()) ClientOnly.handleLocal(up, down, idle);
				} else {
					Player player = ctx.player();
					ItemStack stack = heldRadio(player);
					if(stack == null) {
						PacketGuard.reject("EvRadioUIPacket", player, "no radio in either hand");
						return;
					}
					// D-1094: an ordinal is -1 (none) or a track that exists. Upstream folded anything else
					// into the enum by modulo, which kept it from throwing and let a client pick tracks by
					// sending nonsense; it is dropped here instead, whole.
					int tracks = ElevatorMusic.values().length;
					if(up < -1 || up >= tracks || down < -1 || down >= tracks || idle < -1 || idle >= tracks) {
						PacketGuard.reject("EvRadioUIPacket", player, "track " + up + "/" + down + "/" + idle);
						return;
					}
					write(stack, up, down, idle);
					player.inventoryMenu.broadcastChanges();
				}
			};
		}
	}

	/** The half of the item that names {@code Minecraft}; every way in is behind a level or dist check. */
	@OnlyIn(Dist.CLIENT)
	static final class ClientOnly {

		private ClientOnly() {}

		static void openUI() {
			Minecraft.getInstance().setScreen(new EvRadioUI());
		}

		/** Upstream's {@code handleLocal}: only an open radio screen takes an answer. */
		static void handleLocal(int up, int down, int idle) {
			EvRadioUI ui = EvRadioUI.instance;
			if(ui != null) {
				ui.upOrdinal = up;
				ui.downOrdinal = down;
				ui.idleOrdinal = idle;
				ui.loading = false;
			}
		}
	}
}
