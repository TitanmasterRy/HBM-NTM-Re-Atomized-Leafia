package com.leafia.contents.machines.elevators.items;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaEntities;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Places an elevator cabin in a shaft.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/items/EvSpawnItem.java}.
 *
 * <h2>It is aimed, not clicked</h2>
 *
 * This is a right-click in the air, not on a block. Upstream projects a point 1.5 blocks in front
 * of the player, lifts it by the vertical component of a second matrix built from the look pitch,
 * and takes the block at that point as the column to search. So where the cabin appears depends on
 * where the player is <em>looking</em>, not on what they are standing on — and the two matrices
 * are kept separate rather than composed because the first uses yaw only and the second yaw and
 * pitch, and only the second's {@code y} is read.
 *
 * <h2>Facing has to match the pulley's</h2>
 *
 * The player's horizontal facing must equal the pulley's, or the cabin is refused with a
 * different message. Upstream reads the pulley's facing off {@code getBlockMetadata() - 10};
 * {@link EvFloor#facingOf} reads the same thing off {@code BlockDummyable.DIR}, which is where
 * this port keeps it.
 *
 * <h2>The stack carries a saved configuration</h2>
 *
 * A {@code configuration} tag on the item becomes {@link ElevatorEntity#loadData}, which the
 * entity reads on its first tick — that is how a cabin picked up with its chip, styles and radio
 * still in it comes back the same. Nothing writes that tag yet: it is written by the cabin GUI,
 * which is deferred with {@code ItemTooling} (D-1037). Reading it is ported anyway, because the
 * entity half already is and a half-wired round trip is worse than a whole one that is not yet
 * reachable.
 *
 * <p>1.12.2's NBT lives on the stack directly; 1.21.1's is a {@code DataComponent}, and this port
 * has no addon component type yet — so the tag is read through {@code CustomData}, vanilla's
 * component for exactly this compatibility case.
 */
public class EvSpawnItem extends Item {

	public EvSpawnItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addBeta(tooltip);
		for(String line : I18nUtil.resolveKeyArray("item.ev_spawn.desc"))
			tooltip.add(Component.literal(line));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

		ItemStack stack = player.getItemInHand(hand);

		FiaMatrix mat = new FiaMatrix(new Vec3(player.getX(), player.getY(), player.getZ()))
				.rotateY(-player.getYRot())
				.translate(0, 0, 1.5);
		FiaMatrix look = new FiaMatrix()
				.rotateY(-player.getYRot())
				.rotateX(player.getXRot())
				.translate(0, 0, 1);
		BlockPos pos = BlockPos.containing(mat.position.add(0, 0.5 + look.position.y, 0));

		EvPulleyTE pulley = WeightSpawnItem.findPulley(level, pos);
		if(pulley == null) {
			error(level, player, "item.ev_spawn.error.pulley");
			return InteractionResultHolder.pass(stack);
		}

		Direction face = player.getDirection();
		if(face != EvFloor.facingOf(pulley.getBlockState())) {
			error(level, player, "item.ev_spawn.error.direction");
			return InteractionResultHolder.pass(stack);
		}

		if(!level.isClientSide) {
			ElevatorEntity elevator = new ElevatorEntity(LeafiaEntities.ELEVATOR.get(), level);
			CompoundTag saved = savedConfiguration(stack);
			if(saved != null) elevator.loadData = saved;
			elevator.moveTo(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, face.toYRot() + 180, 0);
			level.addFreshEntity(elevator);
		}

		// Upstream returns ItemStack.EMPTY, which consumes the whole stack - and the stack is
		// capped at one, so that is "consume the item".
		return InteractionResultHolder.sidedSuccess(ItemStack.EMPTY, level.isClientSide);
	}

	@Nullable
	private static CompoundTag savedConfiguration(ItemStack stack) {
		CompoundTag tag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) == null
				? null
				: stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
		if(tag == null || !tag.contains("configuration")) return null;
		return tag.getCompound("configuration");
	}

	private static void error(Level level, Player player, String key) {
		if(level.isClientSide)
			player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
	}
}
