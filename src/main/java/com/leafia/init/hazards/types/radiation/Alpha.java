package com.leafia.init.hazards.types.radiation;

import java.util.List;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.dev.hazards.CEHazardHelper;
import com.leafia.init.hazards.types.HazardTypeHelper;
import com.leafia.init.hazards.types.LCERad;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Alpha radiation: harmless through the skin, and the thing that damages it.
 *
 * <p>The dose is nothing until the carrier has <em>damaged skin III</em>
 * ({@link LeafiaPotion#getSkinDamage}); then it is {@code level x 1.5 / div}, where
 * {@code div = health / 4 x 3 + 0.25} runs from 1 at full health to 0.25 at none, so a dying
 * carrier takes up to six times the dose. Meanwhile, from damaged skin II up, each application
 * has a one-in-{@code 2000 - min(floor((level/10)^0.8) x 100, 950)} chance of making it worse
 * ({@code hurtSkin(3)}), which is one in 2000 for a weak source and one in 1050 for a strong one.
 *
 * <p>Upstream's tooltip text ("I am alpha") is a developer placeholder that its
 * {@code MixinHazardSystem} replaces; with no such hook here the type writes no line of its own
 * (D-1078).
 *
 * <p><b>Divided by five (D-1094):</b> the dose by {@link CEHazardHelper#CE_HAZARD_RATE}, and the
 * skin roll's odds multiplied by it, so one roll a tick is as likely per five ticks as CE's one.
 */
public class Alpha extends HazardTypeBase implements LCERad {

	public static final Alpha INSTANCE = new Alpha();

	private Alpha() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		double level = lvl;
		if(level <= 0.0) return;
		level = CEHazardHelper.stackAndReacher(target, level, stack.getCount());
		int amp = LeafiaPotion.getSkinDamage(target);
		float health = target.getHealth() / target.getMaxHealth();
		double div = health / 4.0 * 3.0 + 0.25d;
		if(amp >= 2) {
			if(target.getRandom().nextInt(CEHazardHelper.rollOdds(2000 - Math.min((int) Math.floor(Math.pow(level / 10.0, 0.8)) * 100, 950))) == 0)
				LeafiaPotion.hurtSkin(target, 3);
		}
		ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE,
				(float) CEHazardHelper.perTick(level * HazardTypeHelper.accountConfig(amp >= 3 ? 1.5 / div : 0.0)));
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
