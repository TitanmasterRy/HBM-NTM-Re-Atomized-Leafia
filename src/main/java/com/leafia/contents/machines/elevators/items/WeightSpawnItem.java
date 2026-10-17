package com.leafia.contents.machines.elevators.items;

import java.util.List;

import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.EvShaftNeo;
import com.leafia.contents.machines.elevators.weight.EvWeightEntity;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaEntities;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places a counterweight on a shaft rail.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/items/WeightSpawnItem.java}.
 *
 * <h2>It has to be used on a shaft rail, not on the pulley</h2>
 *
 * The click has to land on an {@link EvShaftNeo}; the item then steps one block along that rail's
 * facing to find the shaft's centre column and walks <em>up</em> it looking for a pulley. So a
 * counterweight cannot be placed before the pulley that will drive it exists, which is upstream's
 * ordering and the reason both failure paths say the same thing.
 *
 * <h2>Upstream's stack is only shrunk on the server, and the failure messages only on the client</h2>
 *
 * Both kept. The `shrink(1)` sits inside `!world.isRemote` and the two error messages inside
 * `world.isRemote`, so each runs exactly once. 1.12.2's `player.inventoryContainer
 * .detectAndSendChanges()` has no successor and needs none — 1.21.1 syncs a changed held stack
 * itself.
 */
public class WeightSpawnItem extends Item {

	public WeightSpawnItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addBeta(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/**
	 * The first pulley at or above {@code basePos}.
	 *
	 * <p>Upstream starts this loop at {@code basePos.getY()} and ends at the hardcoded 255 — the
	 * 1.12.2 world ceiling. {@link Level#getMaxBuildHeight()} is the honest successor and is
	 * higher in 1.21.1, so a shaft above y=255 works here and does not upstream. Recorded as a
	 * deviation in D-1039 rather than reproducing a limit the world no longer has.
	 */
	@Nullable
	public static EvPulleyTE findPulley(Level level, BlockPos basePos) {
		for(int y = basePos.getY(); y < level.getMaxBuildHeight(); y++) {
			if(level.getBlockEntity(new BlockPos(basePos.getX(), y, basePos.getZ())) instanceof EvPulleyTE pulley)
				return pulley;
		}
		return null;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {

		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		BlockState state = level.getBlockState(pos);

		if(!(state.getBlock() instanceof EvShaftNeo)) {
			noPulley(level, player);
			return InteractionResult.SUCCESS;
		}

		Direction face = state.getValue(EvShaftNeo.FACING);
		EvPulleyTE pulley = findPulley(level, pos.relative(face));
		if(pulley == null) {
			noPulley(level, player);
			return InteractionResult.SUCCESS;
		}

		if(!level.isClientSide) {
			EvWeightEntity weight = new EvWeightEntity(LeafiaEntities.ELEVATOR_WEIGHT.get(), level);
			weight.moveTo(
					pos.getX() + 0.5 - face.getStepX() * 0.4,
					pos.getY(),
					pos.getZ() + 0.5 - face.getStepZ() * 0.4,
					face.toYRot() - 90,
					0);
			level.addFreshEntity(weight);
			if(player != null) context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** Upstream uses the <em>elevator</em> spawn item's key here, not one of its own. Kept. */
	private static void noPulley(Level level, @Nullable Player player) {
		if(level.isClientSide && player != null)
			player.sendSystemMessage(Component.translatable("item.ev_spawn.error.pulley")
					.withStyle(ChatFormatting.RED));
	}
}
