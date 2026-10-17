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
 * Beta radiation: penetrates damaged skin, barely touches healthy skin.
 *
 * <p>The dose factor is {@code (amp + 1)^2 x 0.01} for damaged skin 0 and I - that is 0.01 and
 * 0.04 - and from damaged skin II up it jumps to 0.6, or 0.9 once health is under half.
 * ({@code amp} here is {@link LeafiaPotion#getSkinDamage}, 0 to 3, so a level III carrier takes
 * the same factors as a level II one.) Every application, whatever the level, has a 1 in 2000
 * chance of hurting the skin to at most level II.
 *
 * <p><b>Divided by five (D-1094):</b> the dose by {@link CEHazardHelper#CE_HAZARD_RATE}, and the
 * skin roll's odds multiplied by it, so one roll a tick is as likely per five ticks as CE's one.
 */
public class Beta extends HazardTypeBase implements LCERad {

	public static final Beta INSTANCE = new Beta();

	private Beta() {}

	@Override
	public void onUpdate(LivingEntity target, float lvl, ItemStack stack) {
		double level = lvl;
		if(level <= 0) return;
		level = CEHazardHelper.stackAndReacher(target, level, stack.getCount());
		int amp = LeafiaPotion.getSkinDamage(target);
		float health = target.getHealth() / target.getMaxHealth();
		if(target.getRandom().nextInt(CEHazardHelper.rollOdds(2000)) == 0)
			LeafiaPotion.hurtSkin(target, 2);
		ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE,
				(float) CEHazardHelper.perTick(level * HazardTypeHelper.accountConfig(amp >= 2 ? (health < 0.5 ? 0.9 : 0.6) : (amp + 1) * (amp + 1) * 0.01)));
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) { }
}
