package com.leafia.gametest;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.potion.NtmEffects;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.consumable.medication.PillMeltdownItem;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's status effects and the meltdown pill (D-1069).
 *
 * <p>The players are {@code makeMockPlayer}, not {@code makeMockServerPlayerInLevel}: the second
 * joins the server's player list on a connection that never negotiated the addon's channel, and
 * the wind's next broadcast throws on it and stops the server tick. Every real client has the
 * channel, which is required, so that is a test artefact rather than a bug to route around.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaPillTests {

	private static final String EMPTY = "empty";

	@GameTest(template = EMPTY)
	public static void theEffectsAreUpstreams(GameTestHelper helper) {

		helper.assertTrue(LeafiaPotion.skindamage.get().getColor() == 0xe11313, "damaged skin's colour");
		helper.assertTrue(LeafiaPotion.overdose.get().getColor() == 0x692681, "overdose's colour");
		helper.assertTrue(!LeafiaPotion.frigid.get().isBeneficial() && !LeafiaPotion.overdose.get().isBeneficial(),
				"all three are harmful, upstream's isBad");
		helper.assertTrue(LeafiaPotion.frigid.get().getDescriptionId().equals(MobEffects.MOVEMENT_SLOWDOWN.value().getDescriptionId()),
				"frigid should read as slowness, upstream's getName");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		player.addEffect(new MobEffectInstance(LeafiaPotion.frigid, 200, 1)); // level II: 2 x -15%
		double slowed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(Math.abs(slowed - speed * 0.7) < 1e-6, "frigid II: speed " + slowed + " of " + speed + ", expected 70%");
		helper.succeed();
	}

	/** A level III damaged skin that ends comes back as level II for 300 seconds, next tick. */
	@GameTest(template = EMPTY, timeoutTicks = 20)
	public static void damagedSkinStepsDownInsteadOfEnding(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.addEffect(new MobEffectInstance(LeafiaPotion.skindamage, 1000, 2));
		player.removeEffect(LeafiaPotion.skindamage);
		helper.assertTrue(LeafiaPotion.getSkinDamage(player) == 0, "removed means gone, for this tick");
		helper.runAfterDelay(2, () -> {
			MobEffectInstance back = player.getEffect(LeafiaPotion.skindamage);
			helper.assertTrue(back != null && back.getAmplifier() == 1 && back.getDuration() > 300 * 20 - 5,
					"expected level II for 300 s, got " + back);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY)
	public static void theFirstPillCleansAndTheSecondKills(GameTestHelper helper) {

		if(!LeafiaFeatures.meltdownPill) { helper.succeed(); return; }

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		HbmLivingProps.setRadiation(player, 750F);

		PillMeltdownItem.swallow(player);
		helper.assertTrue(HbmLivingProps.getRadiation(player) == 0F, "the first pill should zero the dose");
		MobEffectInstance mutation = player.getEffect(NtmEffects.MUTATION);
		helper.assertTrue(mutation != null && mutation.getDuration() == 180 * 20, "180 s of mutation, got " + mutation);
		int cooldown = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(PillMeltdownItem.COOLDOWN);
		helper.assertTrue(cooldown == 59 * 60 * 20, "cooldown " + cooldown + ", expected 59 minutes - and stored on an attached tag");
		helper.assertTrue(player.isAlive(), "the first pill does not kill");

		PillMeltdownItem.Cooldown.tick(player);
		helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(PillMeltdownItem.COOLDOWN) == cooldown - 1,
				"the cooldown ticks down by one");
		MobEffectInstance overdose = player.getEffect(LeafiaPotion.overdose);
		helper.assertTrue(overdose != null && overdose.getDuration() == cooldown - 1, "overdose shows the time left, got " + overdose);

		PillMeltdownItem.swallow(player);
		helper.assertTrue(!player.isAlive() || player.getHealth() <= 0, "a second pill within the hour should kill");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theCooldownEndsAndForgetsItself(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		PillMeltdownItem.persisted(player).putInt(PillMeltdownItem.COOLDOWN, 1);
		PillMeltdownItem.Cooldown.tick(player);
		helper.assertTrue(!PillMeltdownItem.persisted(player).contains(PillMeltdownItem.COOLDOWN),
				"at zero the cooldown should be removed, so the next pill is safe again");
		helper.succeed();
	}
}
