package com.leafia.contents.minerals;

import com.leafia.init.hazards.types.containment.HazardTypeContainmentBase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.lib.LeafiaRef;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The absorption particle capsule: a plain item with a yellow three-line description.
 *
 * <p>Upstream: the anonymous {@code AddonItemBaked} subclass behind
 * {@code AddonItems.particle_absorption}, which splits {@code item.particle_absorption.desc} on
 * NTM's {@code $} line break and adds each line in yellow - and, unlike its siblings, does
 * <em>not</em> call {@code super.addInformation}.
 *
 * <p>Upstream also registers a {@code HazardTypeEndothermic} of level 15 on it, which makes the
 * capsule detonate a cold blast when destroyed. That landed with the APR (D-3027): the hazard is
 * registered by {@code LeafiaHazards} under {@code features.apr}, and {@link #onDestroyed} is the
 * call upstream's {@code MixinEntityItem} made after an item entity died of damage.
 */
public class AbsorptionParticleItem extends Item {

	public AbsorptionParticleItem(Properties properties) {
		super(properties);
	}

	/** Upstream's {@code MixinEntityItem}: a destroyed capsule breaks its containment. */
	@Override
	public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
		super.onDestroyed(itemEntity, damageSource);
		HazardTypeContainmentBase.dispatch(itemEntity, damageSource);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		for(String line : I18nUtil.resolveKeyArray("item." + LeafiaRef.MODID + ".particle_absorption.desc"))
			tooltip.add(Component.literal(line).withStyle(ChatFormatting.YELLOW));
	}
}
