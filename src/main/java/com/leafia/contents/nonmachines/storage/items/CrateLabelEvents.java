package com.leafia.contents.nonmachines.storage.items;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable.ToolType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What a crate does when the labelling is asked for, and what its item says about it.
 *
 * <p>Upstream: {@code MixinBlockStorageCrate}, which adds {@code IToolable} to the base crate block - a
 * screwdriver used on a horizontal side of the iron, steel, tungsten or desh crate opens GUI id
 * {@code 1121 + the side's horizontal index} - and injects two tooltip lines into the block's
 * {@code addInformation}.
 *
 * <h2>Why an event, and why the sneak</h2>
 *
 * The base crate is not an {@code IToolable}, and it cannot be made one from here (D-1001). Its
 * {@code BlockStorageCrate.addUseHandler} seam (D-2198) is asked from {@code useItemOn}, which vanilla skips for
 * a <b>sneaking player with an item in hand</b> - and sneaking is exactly how upstream reaches the labelling:
 * its crate's {@code onBlockActivated} opens the crate for any plain click, so the screwdriver only gets to
 * {@code onItemUse} (and so to {@code IToolable}) when the block's own use was skipped, by sneaking. The
 * item string says so: "Sneak and use screwdriver to add label!". So the seam cannot see the click that matters
 * and this is a {@link PlayerInteractEvent.RightClickBlock} subscription, as the DFC's click rules are
 * ({@code LeafiaDFC.Events}); the rule itself is {@link #useOnCrate}, which a test calls directly.
 *
 * <p>Like upstream's there is no lock check: a locked crate can be labelled, and so can one on a stranger's
 * base.
 */
public final class CrateLabelEvents {

	private CrateLabelEvents() {}

	/**
	 * The crate a sneak-click with a screwdriver on {@code face} would label, or null when the click is not this
	 * rule's: the switch is off, the player is not sneaking, the item is not a screwdriver, the face is the top or
	 * the bottom, or the block is not one of the four crates.
	 */
	@Nullable
	public static BlockEntityCrateBase target(Level level, BlockPos pos, Player player, ItemStack stack, Direction face) {

		if(!LeafiaFeatures.crateLabels) return null;
		if(!player.isShiftKeyDown() || stack.isEmpty()) return null;
		if(ToolType.getType(stack) != ToolType.SCREWDRIVER) return null;
		if(!face.getAxis().isHorizontal()) return null;
		if(!CrateLabels.labellable(level.getBlockState(pos).getBlock())) return null;
		return level.getBlockEntity(pos) instanceof BlockEntityCrateBase crate ? crate : null;
	}

	/** The menu a labelling opens: the crate's, with the clicked face. */
	public static SimpleMenuProvider provider(BlockEntityCrateBase crate, Direction face) {
		return new SimpleMenuProvider((id, inventory, p) -> new CrateLabelMenu(id, inventory, crate, face),
				Component.translatable("gui.label_crate"));
	}

	/**
	 * A sneak-click with a screwdriver on a side of a labellable crate. Answers null when the click is not this
	 * rule's ({@link #target}); otherwise the result to cancel it with. On the server it opens the labelling menu,
	 * with the clicked side (upstream's {@code 1121 + horizontalIndex}) and the crate's position as its payload.
	 */
	@Nullable
	public static InteractionResult useOnCrate(Level level, BlockPos pos, Player player, ItemStack stack, Direction face) {

		BlockEntityCrateBase crate = target(level, pos, player, stack, face);
		if(crate == null) return null;

		if(level.isClientSide || !(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;

		server.openMenu(provider(crate, face), buf -> writeData(buf, pos, face));
		return InteractionResult.SUCCESS;
	}

	/** What the client's menu is built from: the crate's position and the clicked face's horizontal index. */
	public static void writeData(RegistryFriendlyByteBuf buf, BlockPos pos, Direction face) {
		buf.writeBlockPos(pos);
		buf.writeVarInt(face.get2DDataValue());
	}

	/** Upstream's two tooltip lines: the tungsten crate's note in red, and the labelling hint on all four. */
	public static void addTooltip(Block block, java.util.List<Component> tooltip) {
		if(!LeafiaFeatures.crateLabels && !LeafiaFeatures.tungstenCrate) return;
		if(block == ModBlocks.crate_tungsten.get() && LeafiaFeatures.tungstenCrate)
			tooltip.add(Component.translatable("tile.crate_tungsten.desc").withStyle(ChatFormatting.RED));
		if(LeafiaFeatures.crateLabels && CrateLabels.labellable(block))
			tooltip.add(Component.translatable("desc.leafia.crate_labelling"));
	}

	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Events {

		private Events() {}

		@SubscribeEvent
		public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
			InteractionResult result = useOnCrate(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack(),
					event.getFace() == null ? Direction.UP : event.getFace());
			if(result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		}

		@SubscribeEvent
		public static void onTooltip(ItemTooltipEvent event) {
			if(event.getItemStack().getItem() instanceof BlockItem item) addTooltip(item.getBlock(), event.getToolTip());
		}
	}
}
