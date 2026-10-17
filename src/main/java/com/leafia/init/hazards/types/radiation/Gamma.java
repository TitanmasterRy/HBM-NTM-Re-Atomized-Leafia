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
 * Gamma radiation: {@code level x 0.05} per application, the same {@code level / 20} the base
 * mod's own radiation type applies - so a gamma source of 30 is the plain 30 RAD/s, on the
 * base mod's every-tick cadence (see {@link CEHazardHelper#HAZARD_RATE}).
 *
 * <p><b>Divided by five (D-1094).</b> That was true until the owner's answer to D-1078's cadence
 * question: CE applies every fifth tick and this port every tick, so the dose is divided by
 * {@link CEHazardHelper#CE_HAZARD_RATE}. A gamma source of 30 is now 6 RAD/s, which is what the
 * same source gave on CE; it is no longer the base mod's plain 30.
 */
public class Gamma extends HazardTypeBase implements LCERad {

	public static final Gamma INSTANCE = new Gamma();

	private Gamma() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		double level = CEHazardHelper.stackAndReacher(target, lvl, stack.getCount());
		ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE, (float) CEHazardHelper.perTick(level * 0.05));
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
