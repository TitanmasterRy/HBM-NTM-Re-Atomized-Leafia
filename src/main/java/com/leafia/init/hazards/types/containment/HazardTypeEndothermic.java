package com.leafia.init.hazards.types.containment;

import java.util.List;

import com.custom_hbm.contents.torex.LCETorex;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.effects.nuke.NukeLCA;
import com.leafia.contents.effects.nuke.NukeLCA.ExplosionType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An item whose destruction sets off an endothermic blast: the addon's mushroom cloud in its blue endothermic
 * colours and an endothermic {@link NukeLCA}, both of the hazard's level as radius.
 *
 * <p>Upstream: {@code com.leafia.init.hazards.types.containment.HazardTypeEndothermic}. Its one registration is
 * the absorption particle capsule at level 15 ({@code AddonHazards}, {@code LeafiaHazards} here), so a capsule
 * thrown into lava or blown up leaves a radius-15 cold crater. The void and {@code /kill} do not set it off.
 * The tooltip is the base's gold line and then {@code trait._hazarditem.containment.endo}, split on upstream's
 * {@code $} line break.
 */
public class HazardTypeEndothermic extends HazardTypeContainmentBase {

	public static final HazardTypeEndothermic INSTANCE = new HazardTypeEndothermic();

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		super.addHazardInformation(player, list, level, stack, modifiers);
		for(String line : I18nUtil.resolveKey("trait._hazarditem.containment.endo").split("\\$"))
			list.add(Component.literal(line));
	}

	@Override
	public void onDestroyed(DamageSource source, Level world, Vec3 pos, double level) {
		if(isOutOfWorld(source)) return;
		LCETorex.statFacEndo(world, pos.x, pos.y, pos.z, (float) level);
		NukeLCA nuke = NukeLCA.statFac(world, (int) level, pos.x, pos.y, pos.z).setExplosionType(ExplosionType.ENDOTHERMIC);
		world.addFreshEntity(nuke);
	}
}
