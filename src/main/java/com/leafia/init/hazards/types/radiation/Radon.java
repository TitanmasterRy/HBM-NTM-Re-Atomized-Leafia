package com.leafia.init.hazards.types.radiation;

import java.util.List;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.hbm.util.ArmorUtil;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.config.LeafiaConfig;
import com.leafia.dev.hazards.CEHazardHelper;
import com.leafia.init.hazards.types.LCERad;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Radon: the one radiation type a respirator stops, and the one that ignores radiation
 * resistance.
 *
 * <p>With a helmet that protects against fine particles ({@code PARTICLE_FINE}) the dose is not
 * taken at all - it wears the mask's filter down by {@link CEHazardHelper#HAZARD_RATE} instead.
 * Without one it is {@code level x HAZARD_RATE} as {@code RAD_BYPASS}, which skips
 * {@code calculateRadiationMod}: a hazmat suit does nothing against it.
 *
 * <p><b>It is not divided by 20.</b> Every other radiation type here, and the base mod's own, turns
 * a RAD/s figure into a per-tick dose; this one adds the level itself each application. At CE's
 * five-tick cadence that is 5 x level per five ticks, one level per tick, which is what it is
 * here - twenty times what the same number would be as plain radiation. Carried across, not
 * corrected: it is how the upstream type is written, and whether the radon values were tuned
 * against it cannot be told from the jar (D-1078).
 *
 * <p>{@code GeneralConfig.enableRadon} is a CE option with no counterpart in the base mod, so the
 * switch is {@link LeafiaConfig#enableRadon}.
 */
public class Radon extends HazardTypeBase implements LCERad {

	public static final Radon INSTANCE = new Radon();

	private Radon() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		if(!LeafiaConfig.enableRadon) return;
		double level = CEHazardHelper.stackAndReacher(target, lvl, stack.getCount());
		if(ArmorRegistry.hasProtection(target, 3, HazardClass.PARTICLE_FINE)) {
			ArmorUtil.damageGasMaskFilter(target, CEHazardHelper.HAZARD_RATE);
		} else {
			ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.RAD_BYPASS,
					(float) (level * CEHazardHelper.HAZARD_RATE));
		}
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
