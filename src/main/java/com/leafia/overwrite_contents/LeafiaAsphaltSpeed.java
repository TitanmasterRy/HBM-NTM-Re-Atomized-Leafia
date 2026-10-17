package com.leafia.overwrite_contents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The base mod's plain asphalt stops speeding a player up, and stops saying it does (D-3026, {@code features.asphaltSpeed}).
 *
 * <p>Upstream: {@code MixinBlockSpeedy} injects at the head of {@code BlockSpeedy.onPlayerStep} and {@code addInformation} and
 * cancels both when the block {@code equals(ModBlocks.asphalt)}. Its third injection, the right-click that paints the asphalt,
 * is {@link com.leafia.contents.building.generic.lined_asphalt.AsphaltPainting}. The lit variant, the stairs and the slabs keep the
 * boost: the test is for the one block.
 *
 * <p>A mixin into the base mod is not allowed (D-1001) and the base's per-tick hook is its own listener, not a method the addon
 * can veto, so this undoes the boost after it is applied. The base's {@code StepTick} multiplies the player's horizontal motion
 * by 1.5 at normal priority on {@code PlayerTickEvent.Pre}, client side, while the player has movement input and stands on a
 * {@code IStepTickReceiver}. This listens on either side of it: the motion is noted first, and last, if it is exactly the noted
 * motion times the block's speed on both horizontal axes, it is put back. Anything else that moved the player in between (a
 * different multiplier, a push) leaves the pair unequal and is left alone.
 *
 * <p>The tooltip line is {@code "Increases speed by 50%"}, the one {@code BlockSpeedy.appendHoverText} adds; it is removed from
 * the plain asphalt item's tooltip.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaAsphaltSpeed {

	private LeafiaAsphaltSpeed() {}

	private static final Map<UUID, Vec3> NOTED = new HashMap<>();

	/** The base's own geometry: the block a hundredth of a block under the player's feet. */
	public static boolean standsOnPlainAsphalt(Player player) {
		if(player.getAbilities().flying) return false;
		BlockPos pos = BlockPos.containing(player.getX(), player.getY() - 0.01D, player.getZ());
		return player.level().getBlockState(pos).is(ModBlocks.asphalt.get());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void before(PlayerTickEvent.Pre event) {
		Player player = event.getEntity();
		if(!LeafiaFeatures.asphaltSpeed || !player.level().isClientSide) return;
		if(standsOnPlainAsphalt(player)) NOTED.put(player.getUUID(), player.getDeltaMovement());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void after(PlayerTickEvent.Pre event) {
		Player player = event.getEntity();
		Vec3 before = NOTED.remove(player.getUUID());
		if(before != null) undo(player, before);
	}

	/** Puts the player's motion back to {@code before} if the only thing that happened to it was the asphalt's multiplier. */
	public static boolean undo(Player player, Vec3 before) {
		double speed = ModBlocks.asphalt.get().speed;
		Vec3 now = player.getDeltaMovement();
		if(speed == 1 || now.x != before.x * speed || now.z != before.z * speed) return false;
		player.setDeltaMovement(before.x, now.y, before.z);
		return true;
	}

	/** Upstream's cancelled {@code addInformation}: the speed line, off the plain asphalt's tooltip. */
	@SubscribeEvent
	public static void tooltip(ItemTooltipEvent event) {
		if(!LeafiaFeatures.asphaltSpeed) return;
		ItemStack stack = event.getItemStack();
		if(!isPlainAsphalt(stack)) return;
		event.getToolTip().removeIf(LeafiaAsphaltSpeed::isSpeedLine);
	}

	public static boolean isPlainAsphalt(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() == ModBlocks.asphalt.get();
	}

	public static boolean isSpeedLine(Component line) {
		return line.getString().startsWith("Increases speed by ");
	}
}
