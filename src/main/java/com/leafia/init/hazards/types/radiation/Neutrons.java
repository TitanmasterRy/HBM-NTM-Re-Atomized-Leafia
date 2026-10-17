package com.leafia.init.hazards.types.radiation;

import java.util.List;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.dev.hazards.CEHazardHelper;
import com.leafia.init.hazards.types.LCERad;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Neutron radiation: {@code level x 1.3} per application.
 *
 * <p><b>Half of CE's neutron contamination is missing, and says so.</b> Upstream contaminates
 * with CE's {@code HazardType.NEUTRON}, which does two things: it adds the dose exactly as
 * {@code RADIATION} does (same resistance, same immunity test), and it records the amount with
 * {@code HbmLivingProps.setNeutron}, the figure CE's neutron-activation system reads to make a
 * carrier's own inventory radioactive. Neither {@code NEUTRON} nor {@code setNeutron} exists in
 * this port's base mod or in {@code hbm-src}, and the addon may not add them (D-1001). So the
 * dose is delivered as {@code RADIATION} and the activation record is a base-mod hand-off
 * (D-1078).
 *
 * <p><b>Divided by five (D-1094)</b>, as every addon type but radon: see {@link CEHazardHelper#CE_HAZARD_RATE}.
 */
public class Neutrons extends HazardTypeBase implements LCERad {

	public static final Neutrons INSTANCE = new Neutrons();

	private Neutrons() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		double level = CEHazardHelper.stackAndReacher(target, lvl, stack.getCount());
		ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE, (float) CEHazardHelper.perTick(level * 1.3));
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
