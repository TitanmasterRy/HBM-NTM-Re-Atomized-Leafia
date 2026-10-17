package com.leafia.contents.gear.utility.fuzzy;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.inventory.fluid.FluidType;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.sound.NtmSounds;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.llib.technical.FifthString;

import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The fuzzy identifier: a hand tool that remembers one fluid and stamps it onto fluid ducts.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/utility/fuzzy/FuzzyIdentifierItem.java}.
 *
 * <h2>What it is for</h2>
 *
 * A base-mod fluid duct carries exactly one fluid, chosen per block, and a long run of pipe has
 * to be set one block at a time. This holds a fluid on the stack and does two things with it:
 *
 * <ul>
 * <li><b>Sneak-click a duct</b> and the tool copies <em>that duct's</em> fluid onto itself.</li>
 * <li><b>Click a duct</b> and every connected duct carrying the duct's current fluid is
 *     re-stamped with the tool's, flooding out to 256 steps. That is how a whole run is
 *     converted in one click, and it is the entire point of the item.</li>
 * <li><b>Sneak-click a machine</b> and the tool reads a fluid off it — {@link IFuzzyCompatible}
 *     first if the machine answers it, otherwise cycling through the machine's sending tanks one
 *     per click.</li>
 * </ul>
 *
 * <h2>This is the project's first {@link IItemFluidIdentifier}, base mod included</h2>
 *
 * Five ported base-mod machines — the coker, the diesel generator, the hydrotreater, the vacuum
 * distiller and the heat exchanger — each have a slot that reads
 * {@code slots[n].getItem() instanceof IItemFluidIdentifier} to decide what their tank holds.
 * Nothing in the port satisfies it: upstream's own {@code ItemFluidIDMulti} is unported, and
 * {@code BlockEntityMachineDiesel}'s class doc says so in as many words. So those five slots have
 * been inert since they landed, and <b>this item is what turns them on</b>. An addon making a
 * base-mod machine work is the dependency running the right way round — the addon implements a
 * base-mod interface, and {@code com.hbm} is not touched.
 *
 * <h2>Two upstream branches: one landed later, one does not port</h2>
 *
 * <ul>
 * <li>The <b>FF duct</b> branch — the same two behaviours against the addon's Forge-fluid
 *     duct — landed with {@code contents/network/ff_duct} (D-3012); it is the pipe branch's code
 *     on {@code FFDuctTE}.</li>
 * <li>The <b>GUI tank click</b>, which is what the item's own tooltip advertises as its primary
 *     use, was reached upstream by {@code MixinFluidTankNTM} - an {@code @Inject} into the middle
 *     of {@code renderTankInfo}, which D-1001 forbids. On the addon's own screens it is
 *     {@code LeafiaFFClient.renderTankInfo}; on the base mod's it is
 *     {@code LeafiaFuzzyScreenClient}, a {@code ScreenEvent.MouseButtonPressed.Pre} listener that
 *     finds the tank under the mouse through the base's recorded tank rectangles
 *     ({@link FuzzyScreenClicks}, D-2207, D-3041). Both send {@link FuzzyIdentifierPacket}.</li>
 * </ul>
 *
 * <h2>An upstream bug, flagged and kept (D-021)</h2>
 *
 * {@link #appendHoverText} has a red <em>"No fluids set!"</em> line behind {@code if(f != null)},
 * and <b>it can never be shown</b>: {@code Fluids.fromName} answers {@link Fluids#NONE} rather
 * than null for an unknown name, and {@link #getType(ItemStack)} answers {@code NONE} for a stack
 * with no tag at all. A fresh identifier therefore reads "Identifier set to: None". The null
 * check is kept exactly as upstream wrote it, and the lang key with it.
 */
public class FuzzyIdentifierItem extends Item implements IItemFluidIdentifier {

	/** Upstream's NBT key, kept so a reader of both sees the same word. */
	public static final String KEY = "fluidtype";

	/** Upstream's flood-fill bound: 256 steps out from the block clicked, not 256 blocks. */
	public static final int SPREAD_RANGE = 256;

	public FuzzyIdentifierItem(Properties properties) {
		super(properties);
	}

	/**
	 * Which fluid this stack is set to, or {@link Fluids#NONE}.
	 *
	 * <p>1.12.2 keeps this on the stack's own {@code NBTTagCompound}; 1.21.1 has no such thing, so
	 * it goes through {@code CustomData} — the same component {@code EvSpawnItem} uses for
	 * upstream's stack tags, and for the same reason.
	 */
	public static FluidType getType(ItemStack stack) {

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null) return Fluids.NONE;

		return Fluids.fromName(data.copyTag().getString(KEY));
	}

	/** Writes the fluid onto the stack, leaving whatever else the tag holds alone. */
	public static void setType(ItemStack stack, FluidType type) {

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();

		tag.putString(KEY, type.getName());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** {@link IItemFluidIdentifier}: the base mod asks with a world and a position; neither matters. */
	@Override
	public FluidType getType(Level level, BlockPos pos, ItemStack stack) {
		return getType(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {

		FluidType f = getType(stack);

		tooltip.add(Component.literal(I18nUtil.resolveKey("desc.leafia.fuzzy.howto"))
				.withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.empty());

		// See the class doc: f is never null, so the else branch is upstream's dead code and is
		// kept rather than corrected.
		if(f != null) {
			tooltip.add(Component.literal(I18nUtil.resolveKey("desc.leafia.fuzzy.set")));
			tooltip.add(Component.literal("   " + f.getLocalizedName()));
		} else {
			tooltip.add(Component.literal(I18nUtil.resolveKey("desc.leafia.fuzzy.unset"))
					.withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Upstream's {@code onItemUseFirst}, which NeoForge publishes under the same name.
	 *
	 * <p>It fires <em>before</em> the clicked block's own {@code useItemOn}, which is what lets a
	 * click on a duct be a tool action rather than whatever the duct would have done. Returning
	 * anything but {@link InteractionResult#PASS} skips the block.
	 *
	 * <p><b>The flood fill runs on both sides</b>, as upstream's does: {@code onItemUseFirst} is
	 * called on the client and then again on the server, and the client's pass is a prediction
	 * the server's block updates confirm. {@code BlockEntityPipeBaseNT.setType} is already
	 * written to be side-safe — it only re-sends the block server-side — so nothing here has to
	 * guard it.
	 */
	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {

		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();

		if(player == null) return InteractionResult.PASS;
		if(!(stack.getItem() instanceof FuzzyIdentifierItem)) return InteractionResult.PASS;

		BlockEntity te = level.getBlockEntity(pos);

		if(te instanceof BlockEntityPipeBaseNT duct) {

			FluidType handType = getType(stack);

			if(handType != duct.getFluidType()) {
				if(player.isShiftKeyDown()) {
					setType(stack, duct.getFluidType());
					announce(level, player, duct.getFluidType());
				} else {
					spreadType(level, pos, handType, duct.getFluidType(), SPREAD_RANGE);
				}
			}

			return InteractionResult.SUCCESS;
		}

		// Upstream's FFDuctTE branch: the same two behaviours on the Forge-fluid duct (D-3012).
		if(te instanceof FFDuctTE duct) {

			FluidType handType = getType(stack);

			if(handType != duct.getFluidType()) {
				if(player.isShiftKeyDown()) {
					setType(stack, duct.getFluidType());
					announce(level, player, duct.getFluidType());
				} else {
					spreadTypeFF(level, pos, handType, duct.getFluidType(), SPREAD_RANGE);
				}
			}

			return InteractionResult.SUCCESS;
		}

		if(player.isShiftKeyDown()) {

			BlockState state = level.getBlockState(pos);

			if(state.getBlock() instanceof BlockDummyable dummyable) {
				BlockPos core = dummyable.findCore(level, pos);
				if(core != null) te = level.getBlockEntity(core);
			}

			FluidType output = FuzzyAdapters.outputOf(te);
			if(output != null) {
				FluidType fluid = output;
				setType(stack, fluid);
				announce(level, player, fluid);
				return InteractionResult.SUCCESS;
			}

			if(te instanceof IFluidStandardSenderMK2 mk2) {

				if(!level.isClientSide) {

					FluidTank[] sending = mk2.getSendingTanks();
					if(sending.length <= 0) return InteractionResult.PASS;

					// Upstream's cycling index is a static shared by every player on the server,
					// so two players cycling two machines at once step on each other. Kept: it is
					// a balance-free quirk, and "which tank did I get" is already a click-until-
					// you-see-it interaction. Flagged in D-1053.
					if(index >= sending.length) index = 0;
					FluidType fluid = sending[index].getTankType();
					index++;

					setType(stack, fluid);

					// Upstream's inventoryContainer.detectAndSendChanges(): the stack the player
					// is holding changed under them, and nothing else will tell the client.
					player.inventoryMenu.broadcastChanges();

					level.playSound(null, player.blockPosition(), NtmSounds.ITEM_TECH_BLEEP.get(),
							SoundSource.PLAYERS, 1F, 1F);

					FuzzyIdentifierResponsePacket response = new FuzzyIdentifierResponsePacket();
					response.id = fluid.getID();

					LeafiaCustomPacket packet = LeafiaCustomPacket.__start(response);
					if(packet != null) packet.__sendToClient(player);
				}

				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}

	/**
	 * Upstream's cycling index into a machine's sending tanks. Static, and deliberately so — see
	 * the note at its only use.
	 */
	static int index = 0;

	/**
	 * The bleep on the server, the chat line on the client.
	 *
	 * <p>Upstream writes this out four times over, once per branch, and each copy reaches into
	 * {@code Minecraft.getMinecraft().player} on the client half. {@link Player#displayClientMessage}
	 * does the same thing without a client class, which is what lets this be one method in a
	 * common file.
	 */
	private static void announce(Level level, Player player, FluidType fluid) {

		if(!level.isClientSide) {
			level.playSound(null, player.blockPosition(), NtmSounds.ITEM_TECH_BLEEP.get(),
					SoundSource.PLAYERS, 1F, 1F);
		} else {
			player.displayClientMessage(
					Component.translatable("item.fuzzy_identifier.message", fluid.getLocalizedName())
							.withStyle(ChatFormatting.YELLOW), false);
		}
	}

	/**
	 * Re-stamps every connected duct carrying {@code pipe} with {@code hand}, out to {@code x}
	 * steps.
	 *
	 * <p>Upstream's recursion, kept: a duct is re-typed <em>before</em> its six neighbours are
	 * visited, so it no longer matches {@code pipe} and the fill cannot come back through it.
	 * {@code x} is a step budget rather than a block count — a straight run of 256 ducts is
	 * reached, and so is a tangle of far more than 256 within 256 steps of the click.
	 */
	/** Upstream's {@code spreadTypeFF}: {@link #spreadType} over Forge-fluid ducts. */
	public static void spreadTypeFF(Level level, BlockPos pos, FluidType hand, FluidType pipe, int x) {

		if(x <= 0) return;

		if(level.getBlockEntity(pos) instanceof FFDuctTE duct && duct.getFluidType() == pipe) {

			duct.setType(hand);
			duct.setChanged();

			spreadTypeFF(level, pos.offset(1, 0, 0), hand, pipe, x - 1);
			spreadTypeFF(level, pos.offset(0, 1, 0), hand, pipe, x - 1);
			spreadTypeFF(level, pos.offset(0, 0, 1), hand, pipe, x - 1);
			spreadTypeFF(level, pos.offset(-1, 0, 0), hand, pipe, x - 1);
			spreadTypeFF(level, pos.offset(0, -1, 0), hand, pipe, x - 1);
			spreadTypeFF(level, pos.offset(0, 0, -1), hand, pipe, x - 1);
		}
	}

	public static void spreadType(Level level, BlockPos pos, FluidType hand, FluidType pipe, int x) {

		if(x <= 0) return;

		BlockEntity te = level.getBlockEntity(pos);

		if(te instanceof BlockEntityPipeBaseNT duct && duct.getFluidType() == pipe) {

			duct.setType(hand);
			// Upstream's markDirty(). Redundant here - this port's setType already calls
			// setChanged - and kept so the two read the same way.
			duct.setChanged();

			spreadType(level, pos.offset(1, 0, 0), hand, pipe, x - 1);
			spreadType(level, pos.offset(0, 1, 0), hand, pipe, x - 1);
			spreadType(level, pos.offset(0, 0, 1), hand, pipe, x - 1);
			spreadType(level, pos.offset(-1, 0, 0), hand, pipe, x - 1);
			spreadType(level, pos.offset(0, -1, 0), hand, pipe, x - 1);
			spreadType(level, pos.offset(0, 0, -1), hand, pipe, x - 1);
		}
	}

	/**
	 * Client to server: "set the stack on my cursor to this fluid".
	 *
	 * <p>Upstream's sender is a GUI click on a machine's fluid tank, reached by a mixin into
	 * {@code com.hbm}. Nothing sends it here yet — see the class doc — but the receiving half
	 * below is complete, and it is the half with something to get wrong.
	 *
	 * <p>Upstream reads {@code player.inventory.getItemStack()}, 1.12.2's stack-on-the-cursor.
	 * 1.21.1 keeps that on the open menu instead, as {@code containerMenu.getCarried()}, and
	 * {@code player.updateHeldItem()} becomes {@code broadcastChanges} on the same menu.
	 */
	public static class FuzzyIdentifierPacket implements LeafiaCustomPacketEncoder {

		public String fluidRsc = "";

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeFifthString(new FifthString(fluidRsc));
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		/** Longest fluid name a client may send; no registered name comes near it. */
		public static final int MAX_NAME = 64;

		/**
		 * Validated (D-1094). The packet is a click on a machine's fluid tank while the identifier is on
		 * the cursor, so the sender must be a server-side player with a <em>machine's</em> menu open
		 * (not just their own inventory), that menu must still be valid for them - vanilla's own reach
		 * test - the cursor must hold an identifier, and the name must be one of the registered fluids.
		 * Upstream took any string and stored whatever {@code Fluids.fromName} made of it.
		 */
		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			String name = buf.readFifthString().toString();

			return ctx -> {

				if(ctx.flow() != PacketFlow.SERVERBOUND) return;
				if(!(ctx.player() instanceof ServerPlayer player)) return;

				if(player.containerMenu == player.inventoryMenu || !player.containerMenu.stillValid(player)) {
					PacketGuard.reject("FuzzyIdentifierPacket", player, "no machine menu open and in reach");
					return;
				}

				if(name.length() > MAX_NAME || Fluids.fromName(name) == Fluids.NONE && !name.equals(Fluids.NONE.getName())) {
					PacketGuard.reject("FuzzyIdentifierPacket", player, "no such fluid");
					return;
				}

				ItemStack stack = player.containerMenu.getCarried();

				if(!stack.isEmpty() && stack.getItem() instanceof FuzzyIdentifierItem) {
					setType(stack, Fluids.fromName(name));
					player.containerMenu.broadcastChanges();
					player.level().playSound(null, player.blockPosition(),
							NtmSounds.ITEM_TECH_BLEEP.get(), SoundSource.PLAYERS, 1F, 1F);
				}
			};
		}
	}

	/**
	 * Server to client: "the tank you just cycled onto was this one".
	 *
	 * <p>The stack itself is set server-side; this carries only the chat line, because the server
	 * has no localised fluid name to send and the client does.
	 *
	 * <p>Upstream sends the fluid's <b>id</b> rather than its name, which the name-keyed packet
	 * above does not — an inconsistency kept, since both resolve through {@code Fluids} and both
	 * ends of each packet agree.
	 */
	public static class FuzzyIdentifierResponsePacket implements LeafiaCustomPacketEncoder {

		public int id;

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(id);
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			FluidType fluid = Fluids.fromID(buf.readInt());

			return ctx -> ctx.player().displayClientMessage(
					Component.translatable("item.fuzzy_identifier.message", fluid.getLocalizedName())
							.withStyle(ChatFormatting.YELLOW), false);
		}
	}
}
