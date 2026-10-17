package com.leafia.contents.control.upgrades;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.items.machine.ItemMachineUpgrade;
import com.leafia.contents.LeafiaItems;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.llib.technical.FifthString;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An upgrade item the addon registers: so far only the <b>control upgrade</b>.
 *
 * <p>Upstream: {@code .../control/upgrades/AddonUpgradeItem.java}, an {@code ItemMachineUpgrade} that moves
 * itself from the base mod's item list to the addon's (the registry bookkeeping, which is the registration in
 * {@code LeafiaItems} here). Its three constructors are the base class's, and it is a {@code SPECIAL} upgrade
 * of tier 0 like every other one with the no-argument constructor.
 *
 * <p><b>What the control upgrade does</b> is not in this class but in {@link ControlUpgrade}: a right click opens
 * a one-box screen for a radio channel name (the {@code freq} on the stack, ten characters at most); a machine
 * with the item in its upgrade slots then caps every other upgrade at the number it hears on that channel.
 * Only the main hand opens the screen, as upstream's - the off hand's click fails.
 *
 * <p>The frequency is the stack's custom data, {@code {freq: "..."}}, as upstream's NBT. Stacks to one.
 */
public class AddonUpgradeItem extends ItemMachineUpgrade {

	/** The longest frequency the screen's box takes - upstream's {@code setMaxStringLength(10)}. */
	public static final int MAX_FREQUENCY = 10;

	public AddonUpgradeItem(Properties properties) {
		super(properties);
	}

	public AddonUpgradeItem(Properties properties, UpgradeType type) {
		super(properties, type);
	}

	public AddonUpgradeItem(Properties properties, UpgradeType type, int tier) {
		super(properties, type, tier);
	}

	/** Whether this is the control upgrade (upstream compares against {@code AddonItems.upgrade_control}). */
	public boolean isControl() {
		return LeafiaItems.upgrade_control != null && this == LeafiaItems.upgrade_control.get();
	}

	/** The channel the stack names, or "". */
	public static String frequency(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getString("freq");
	}

	/**
	 * Upstream's {@code setTagCompound(nbt)}: <b>the whole tag is replaced</b> by one holding only the channel,
	 * so anything else on the stack's data is lost.
	 */
	public static void setFrequency(ItemStack stack, String freq) {
		CompoundTag tag = new CompoundTag();
		tag.putString("freq", freq);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.fail(stack);
		if(isControl()) {
			if(level.isClientSide) UpgradeControlClient.open(frequency(stack));
			return InteractionResultHolder.success(stack);
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, net.minecraft.world.item.TooltipFlag flag) {
		if(isControl()) {
			list.add(Component.translatable("item.upgrade_control.freq", frequency(stack)));
			list.add(Component.literal(""));
			for(String line : Component.translatable("item.upgrade_control.desc").getString().split("\\$"))
				list.add(Component.literal(line));
			return;
		}
		super.appendHoverText(stack, context, list, flag);
	}

	/**
	 * The screen's close, client to server: the channel it ended on.
	 *
	 * <p>Upstream's {@code ControlUpgradeFreqPacket}. Its server half looks in the main hand and then, in an
	 * {@code else if} whose condition is a copy of the first, in the main hand again - the <b>off hand is
	 * never looked in</b>. Kept (and unreachable in play anyway: the screen only opens from the main hand), so a
	 * control upgrade swapped to the off hand while its screen is open loses the edit. Validated (D-1094): a
	 * server-side player, holding a control upgrade, sending at most ten characters.
	 */
	public static class ControlUpgradeFreqPacket implements LeafiaCustomPacketEncoder {

		public String value = "";

		public ControlUpgradeFreqPacket() { }

		public ControlUpgradeFreqPacket(String value) {
			this.value = value;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeFifthString(new FifthString(value));
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			String freq = buf.readFifthString().toString();
			return ctx -> {
				if(ctx.flow() != PacketFlow.SERVERBOUND) return;
				if(!(ctx.player() instanceof ServerPlayer player)) return;
				apply(player, freq);
			};
		}

		/** The server's half, public for the tests. Answers whether the channel was written. */
		public static boolean apply(ServerPlayer player, String freq) {
			if(freq.length() > MAX_FREQUENCY) {
				PacketGuard.reject("ControlUpgradeFreqPacket", player, "a channel longer than the box types");
				return false;
			}
			ItemStack held = player.getMainHandItem();
			if(!(held.getItem() instanceof AddonUpgradeItem item) || !item.isControl()) {
				PacketGuard.reject("ControlUpgradeFreqPacket", player, "no control upgrade in the main hand");
				return false;
			}
			setFrequency(held, freq);
			player.inventoryMenu.broadcastChanges();
			return true;
		}
	}
}
