package com.leafia.init.hazards.types;

import java.util.List;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.util.ArmorUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorWarningPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.hazards.CEHazardHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Toxic metals (lead, beryllium, cadmium, arsenic): a hazard graded by level that weakens, slows,
 * starves, poisons, fatigues and finally harms whoever carries it unprotected.
 *
 * <p>Upstream: NTM-CE's {@code HazardTypeToxic}, which the addon's {@code MixinHazardTypeToxic}
 * <em>overwrites</em> ("rework"). The base mod has no such type - toxic is CE's, not {@code hbm-src}'s -
 * so the addon owns the reworked one outright: this is the mixin's body, and CE's original is not
 * kept. What the rework changed against CE is that the nerve-agent filter is gone (a hazmat suit or
 * the reacher is the protection), the poison/slowness/hunger block no longer needs the filter test,
 * and the carrier is warned through the advisor ({@code AdvisorWarningPacket} ids 3 and 4).
 *
 * <p>Per application, with {@code n = (int) level}:
 * <ul>
 * <li>unprotected (no hazmat, no reacher held): weakness {@code n-1}; above 2 slowness
 *     {@code min(4, level-4)}; above 4 hunger {@code n}; the advisor's toxic warning;
 * <li>unless wearing hazmat <em>and</em> holding the reacher (upstream's {@code !hasHazmat || !reacher}, which
 *     is true unless both): above 6 the strong warning and a one-in-{@code 2000 / level} poison
 *     {@code level-4}; above 8 fatigue {@code level-8}; above 16 instant damage {@code level-16}.
 * </ul>
 * every effect for 110 ticks.
 *
 * <p><b>Divergences from 1.12.2:</b> the poison roll's odds are multiplied by
 * {@link CEHazardHelper#CE_HAZARD_RATE} (D-1094: one roll a tick is as likely per five ticks as CE's one
 * per application); an amplifier below 0 applies nothing (1.12.2 made -1 no strength at all; 1.21.1 would floor it at I); the warnings need
 * {@code features.advisor}; and {@code disableToxic} is {@link LeafiaConfig#disableToxic}, the addon's
 * option, since the base mod has no such config.
 */
public class HazardTypeToxic extends HazardTypeBase {

	public static final HazardTypeToxic INSTANCE = new HazardTypeToxic();

	/** The duration of every effect it applies, in ticks. */
	public static final int DURATION = 110;

	private HazardTypeToxic() {}

	@Override
	public void onUpdate(LivingEntity target, float level, ItemStack stack) {

		if(LeafiaConfig.disableToxic) return;

		boolean reacher = CEHazardHelper.isHoldingReacher(target);
		boolean hasHazmat = false;
		if(target instanceof Player player) hasHazmat = ArmorUtil.checkForHazmat(player);

		boolean isUnprotected = !(hasHazmat || reacher);

		if(isUnprotected) {
			apply(target, MobEffects.WEAKNESS, DURATION, (int) (level - 1));

			if(level > 2) apply(target, MobEffects.MOVEMENT_SLOWDOWN, DURATION, (int) Math.min(4, level - 4));

			if(level > 4) apply(target, MobEffects.HUNGER, DURATION, (int) level);

			warn(target, AdvisorWarningPacket.TOXIC);
		}

		if(!hasHazmat || !reacher) {

			if(level > 6) warn(target, AdvisorWarningPacket.TOXIC_STRONG);

			if(level > 6 && target.getRandom().nextInt(CEHazardHelper.rollOdds(Math.max(1, (int) (2000 / level)))) == 0)
				apply(target, MobEffects.POISON, DURATION, (int) (level - 4));

			if(level > 8) apply(target, MobEffects.DIG_SLOWDOWN, DURATION, (int) (level - 8));

			if(level > 16) apply(target, MobEffects.HARM, DURATION, (int) (level - 16));
		}
	}

	private static void apply(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier) {
		// 1.12.2 took an amplifier of -1 and made it nothing (the strength is amplifier + 1); 1.21.1 would floor it at I.
		if(amplifier < 0) return;
		target.addEffect(new MobEffectInstance(effect, duration, amplifier));
	}

	private static void warn(LivingEntity target, int id) {
		if(LeafiaFeatures.advisor && target instanceof ServerPlayer player)
			LeafiaCustomPacket.__start(new AdvisorWarningPacket(id)).__sendToClient(player);
	}

	@Override
	public void updateEntity(ItemEntity item, float level) { }

	/** The adjective key for a level: upstream's five bands, above 16, 8, 4, 2, and the rest. */
	public static String adjectiveKey(float level) {
		if(level > 16) return "trait._hazarditem.toxic.extreme";
		if(level > 8) return "trait._hazarditem.toxic.veryhigh";
		if(level > 4) return "trait._hazarditem.toxic.high";
		if(level > 2) return "trait._hazarditem.toxic.medium";
		return "trait._hazarditem.toxic.little";
	}

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		list.add(Component.empty().withStyle(ChatFormatting.GREEN)
				.append(Component.translatable(adjectiveKey(level))).append(" ")
				.append(Component.translatable("trait._hazarditem.toxic")));
	}
}
