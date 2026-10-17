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
 * X-rays: {@code level x 0.2} per application, four times the gamma dose for the same level.
 *
 * <p><b>Divided by five (D-1094)</b>, as every addon type but radon: see {@link CEHazardHelper#CE_HAZARD_RATE}.
 */
public class XRay extends HazardTypeBase implements LCERad {

	public static final XRay INSTANCE = new XRay();

	private XRay() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		double level = CEHazardHelper.stackAndReacher(target, lvl, stack.getCount());
		ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE, (float) CEHazardHelper.perTick(level * 0.2));
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
