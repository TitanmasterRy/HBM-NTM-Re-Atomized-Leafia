package com.leafia.overwrite_contents;

import com.hbm.blocks.bomb.LaunchPad;
import com.hbm.items.tool.ItemDesingator;
import com.hbm.items.tool.ItemDesingatorRange;
import com.hbm.lib.Library;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

/**
 * The two designators remember the height of what they were pointed at, as well as its x and z.
 *
 * <p>Upstream: {@code MixinItemDesignator} and {@code MixinItemDesignatorRange}. NTM-CE's {@code ItemDesignator} is
 * 1.7.10's typo'd {@code ItemDesingator}, which the base mod keeps, and both base designators write only
 * {@code xCoord}/{@code zCoord} - a launch pad aims at the ground, so the height never mattered. The addon's DFC reads
 * {@code yCoord} when a designator retargets a part ({@code LeafiaDFC.useOnPart}), so without these a retarget always
 * aimed at y = 0. No base seam is needed: NeoForge's own events fire at the two points upstream injected at.
 *
 * <ul>
 * <li><b>The designator</b>: upstream injects into {@code onItemUse} just before the {@code isRemote} read - after its
 *     launch-pad check, so on any block that is not a pad, on both sides. Here that is {@link UseItemOnBlockEvent}'s
 *     {@code ITEM_AFTER_BLOCK} phase, which fires immediately before {@code Item.useOn} and only when the item's use is
 *     really going to run (a block that consumed the click, or the DFC's own click rule, which cancels earlier, never
 *     reaches it). {@code CustomData.update} merges, so the base's x/z write that follows keeps the y.</li>
 * <li><b>The range designator</b>: upstream injects into {@code onItemRightClick} at the same place, writing the
 *     ray-traced position's y. {@link PlayerInteractEvent.RightClickItem} fires just before {@code Item.use}; the ray is
 *     the base's own call, {@code Library.rayTrace(player, 300, 1)}, made in the same tick from the same player, so the
 *     y written here and the x/z the base writes next name the same block. A launch pad is skipped, as upstream skips it.</li>
 * </ul>
 *
 * Two parts of {@code MixinItemDesignatorRange} are not carried. Its added {@code onItemUse} (a click on a block in
 * reach sets x/y/z there) is the same block the 300-block ray hits, which the right-click half already covers; and its
 * {@code @Overwrite} of {@code getCoords} to put the y in the vector changes nothing here, because every base caller
 * floors x and z only ({@code BlockEntityLaunchPadBase}). Not a switch: the y is a key no base code reads.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaDesignatorSeams {

	private LeafiaDesignatorSeams() {}

	/** Writes {@code yCoord = pos.y} onto a designator aimed at {@code pos}, unless that is a launch pad. True when written. */
	public static boolean writeHeight(Level level, BlockPos pos, ItemStack stack) {
		if(level.getBlockState(pos).getBlock() instanceof LaunchPad) return false;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("yCoord", pos.getY()));
		return true;
	}

	@SubscribeEvent
	public static void onUseOn(UseItemOnBlockEvent event) {
		if(event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK) return;
		ItemStack stack = event.getItemStack();
		if(stack.getItem() instanceof ItemDesingator) writeHeight(event.getLevel(), event.getPos(), stack);
	}

	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		ItemStack stack = event.getItemStack();
		if(!(stack.getItem() instanceof ItemDesingatorRange)) return;
		BlockHitResult hit = Library.rayTrace(event.getEntity(), 300, 1);
		writeHeight(event.getLevel(), hit.getBlockPos(), stack);
	}
}
