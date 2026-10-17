package com.leafia.init.hazards.types.containment;

import java.util.List;

import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hazard that does nothing while held or dropped, and something when the item is <em>destroyed</em> - its
 * containment breaks.
 *
 * <p>Upstream: {@code com.leafia.init.hazards.types.containment.HazardTypeContainmentBase}. The tooltip is
 * upstream's gold {@code [Dangerous Content]}; the subclass adds its own line. Something must call
 * {@link #onDestroyed}: upstream's {@code MixinEntityItem} does it after {@code EntityItem.attackEntityFrom}
 * kills the entity. Here that is {@link #dispatch}, called from the item's
 * {@code onDestroyed(ItemEntity, DamageSource)}, which 1.21.1's {@code ItemEntity.hurt} calls at exactly that
 * point (read in the decompile). The only item carrying a containment hazard is the absorption particle
 * capsule, which is the addon's own, so the hook needs no mixin and no base-mod seam.
 */
public abstract class HazardTypeContainmentBase extends HazardTypeBase {

	@Override
	public void onUpdate(LivingEntity target, float level, ItemStack stack) { }

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		list.add(Component.literal("[").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("trait._hazarditem.containment")).append("]"));
	}

	/** What happens when the item breaks: at {@code pos}, the entity's eye, with the stack's hazard level. */
	public abstract void onDestroyed(DamageSource source, Level world, Vec3 pos, double level);

	/**
	 * Upstream's {@code leafia$onAttackEntityFrom}: every containment hazard on the destroyed stack fires once,
	 * at the item entity's eye height, at its level for that stack.
	 */
	public static void dispatch(ItemEntity entity, DamageSource source) {
		ItemStack stack = entity.getItem();
		for(HazardEntry entry : HazardSystem.getHazardsFromStack(stack)) {
			if(entry.getType() instanceof HazardTypeContainmentBase contain)
				contain.onDestroyed(source, entity.level(), new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ()),
						HazardSystem.getHazardLevelFromStack(stack, entry.getType()));
		}
	}

	/** Upstream's {@code source == DamageSource.OUT_OF_WORLD}: the void, or {@code /kill}. */
	public static boolean isOutOfWorld(DamageSource source) {
		return source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL);
	}
}
