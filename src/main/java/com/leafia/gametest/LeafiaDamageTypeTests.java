package com.leafia.gametest;

import com.leafia.init.LeafiaDamageSources;
import com.leafia.lib.LeafiaRef;
import com.llib.LeafiaLib;
import com.leafia.unsorted.BullshitInDavenportIowa;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the addon's 16 damage types (L3, D-1079) and its unit conversions.
 *
 * <p>A damage type is a datapack registry entry in 1.21.1, so these tests are not testing Java
 * — they are testing that sixteen JSON files parse, that eight tag files name types that
 * exist, and that the flags upstream set on each {@code DamageSource} survived the translation
 * into tags. Get one wrong and nothing complains: a player survives a hit they should not have,
 * or takes one in creative mode, and nobody notices for months.
 *
 * <p>{@link #tagsMergeWithTheBaseModsRatherThanReplacingThem} is the one that checks the
 * mechanism the whole arrangement rests on. The addon ships its own
 * {@code data/minecraft/tags/damage_type/bypasses_armor.json}, and Minecraft unions tag files
 * of the same path across data packs. If it replaced them instead, installing this addon would
 * silently strip the base mod's 37 damage types out of their tags.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaDamageTypeTests {

	private static final String EMPTY = "empty";

	private static Holder<DamageType> holder(GameTestHelper helper, ResourceKey<DamageType> key) {
		return helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(key);
	}

	private static void hasTag(GameTestHelper helper, ResourceKey<DamageType> key,
			TagKey<DamageType> tag, boolean expected) {
		boolean actual = holder(helper, key).is(tag);
		helper.assertTrue(actual == expected,
				key.location() + " should " + (expected ? "" : "not ") + "be in " + tag.location()
						+ ", but is " + (actual ? "" : "not"));
	}

	/** All sixteen entries parsed and registered. A missing JSON fails datapack load, not here. */
	@GameTest(template = EMPTY)
	public static void everyDamageTypeResolves(GameTestHelper helper) {

		ResourceKey<DamageType>[] all = new ResourceKey[] {
				LeafiaDamageSources.fleija, LeafiaDamageSources.back, LeafiaDamageSources.mine,
				LeafiaDamageSources.dfc, LeafiaDamageSources.dfcMeltdown, LeafiaDamageSources.pointed,
				LeafiaDamageSources.poison, LeafiaDamageSources.drinkcryo, LeafiaDamageSources.drinkhot,
				LeafiaDamageSources.drinkacid, LeafiaDamageSources.flywheel,
				LeafiaDamageSources.mine_player, LeafiaDamageSources.am_rifle,
				LeafiaDamageSources.pillMeltdown, LeafiaDamageSources.cryo, LeafiaDamageSources.endothermic};

		helper.assertTrue(all.length == 16, "upstream declares sixteen, got " + all.length);
		for(ResourceKey<DamageType> key : all) {
			helper.assertTrue(key.location().getNamespace().equals(LeafiaRef.MODID),
					"registered under the addon's namespace: " + key.location());
			helper.assertTrue(holder(helper, key).isBound(), "resolves: " + key.location());
		}

		// And one builds into a usable DamageSource, which is what a machine will actually do.
		DamageSource src = LeafiaDamageSources.of(helper.getLevel(), LeafiaDamageSources.dfc);
		helper.assertTrue(src.getMsgId().equals("dfc"),
				"the message id keeps upstream's spelling, got " + src.getMsgId());

		helper.succeed();
	}

	/**
	 * The message ids keep upstream's camelCase while the registry paths are lowercase.
	 *
	 * <p>Same trap D-025 records for the base mod: {@code death.attack.<msgId>} is what the
	 * language files key on, so lowercasing both would silently break the death message for
	 * every locale that has one.
	 */
	@GameTest(template = EMPTY)
	public static void registryPathsAreLowercaseAndMessageIdsAreNot(GameTestHelper helper) {

		helper.assertTrue(LeafiaDamageSources.dfcMeltdown.location().getPath().equals("dfc_meltdown"),
				"path is snake_case, got " + LeafiaDamageSources.dfcMeltdown.location().getPath());
		helper.assertTrue(
				LeafiaDamageSources.of(helper.getLevel(), LeafiaDamageSources.dfcMeltdown).getMsgId()
						.equals("dfcMeltdown"),
				"message id keeps camelCase");

		helper.assertTrue(LeafiaDamageSources.pillMeltdown.location().getPath().equals("pill_meltdown"),
				"and again for pillMeltdown");
		helper.assertTrue(
				LeafiaDamageSources.of(helper.getLevel(), LeafiaDamageSources.pillMeltdown).getMsgId()
						.equals("pillMeltdown"),
				"message id keeps camelCase");

		helper.succeed();
	}

	/**
	 * The flags upstream set, as tags. Each row here is a line of
	 * {@code com.leafia.init.LeafiaDamageSource} read back out of the registry.
	 */
	@GameTest(template = EMPTY)
	public static void upstreamFlagsSurvivedAsTags(GameTestHelper helper) {

		// setDamageBypassesArmor, and nothing else.
		hasTag(helper, LeafiaDamageSources.fleija, DamageTypeTags.BYPASSES_ARMOR, true);
		hasTag(helper, LeafiaDamageSources.fleija, DamageTypeTags.BYPASSES_EFFECTS, false);
		hasTag(helper, LeafiaDamageSources.fleija, DamageTypeTags.BYPASSES_INVULNERABILITY, false);

		// setExplosion + setDamageBypassesArmor.
		hasTag(helper, LeafiaDamageSources.mine, DamageTypeTags.IS_EXPLOSION, true);
		hasTag(helper, LeafiaDamageSources.mine, DamageTypeTags.BYPASSES_ARMOR, true);

		// setDamageIsAbsolute is three tags, not one - "absolute" in 1.12.2 meant ignoring
		// armour, resistance and enchantments alike (D-025).
		hasTag(helper, LeafiaDamageSources.dfc, DamageTypeTags.BYPASSES_EFFECTS, true);
		hasTag(helper, LeafiaDamageSources.dfc, DamageTypeTags.BYPASSES_ENCHANTMENTS, true);
		hasTag(helper, LeafiaDamageSources.dfc, DamageTypeTags.BYPASSES_RESISTANCE, true);

		// setDamageAllowedInCreativeMode. `back` is upstream's "fuck you :D" source and carries
		// every flag there is; if creative mode ever stops being bypassed, that is this tag.
		hasTag(helper, LeafiaDamageSources.back, DamageTypeTags.BYPASSES_INVULNERABILITY, true);
		hasTag(helper, LeafiaDamageSources.back, DamageTypeTags.BYPASSES_ARMOR, true);
		hasTag(helper, LeafiaDamageSources.back, DamageTypeTags.BYPASSES_EFFECTS, true);

		// setFireDamage, which the base mod's 1.7.10 source never used - see D-1010.
		hasTag(helper, LeafiaDamageSources.drinkhot, DamageTypeTags.IS_FIRE, true);
		hasTag(helper, LeafiaDamageSources.drinkacid, DamageTypeTags.IS_FIRE, true);
		hasTag(helper, LeafiaDamageSources.drinkcryo, DamageTypeTags.IS_FIRE, false);

		// setMagicDamage, mapped the way vanilla's own `magic` type is built.
		hasTag(helper, LeafiaDamageSources.am_rifle, DamageTypeTags.WITCH_RESISTANT_TO, true);
		hasTag(helper, LeafiaDamageSources.am_rifle, DamageTypeTags.BYPASSES_ARMOR, true);

		// mine_player is absolute but does NOT bypass armour - the one source where those two
		// come apart, and the thing a careless "they're all the same" port would flatten.
		hasTag(helper, LeafiaDamageSources.mine_player, DamageTypeTags.BYPASSES_EFFECTS, true);
		hasTag(helper, LeafiaDamageSources.mine_player, DamageTypeTags.BYPASSES_ARMOR, false);

		// `cryo` is a bare `new DamageSource("cryo")` - liquid oxygen's contact damage - and so is
		// in no tag at all: armour, effects and creative mode all apply (D-1079).
		hasTag(helper, LeafiaDamageSources.cryo, DamageTypeTags.BYPASSES_ARMOR, false);
		hasTag(helper, LeafiaDamageSources.cryo, DamageTypeTags.BYPASSES_EFFECTS, false);
		hasTag(helper, LeafiaDamageSources.cryo, DamageTypeTags.BYPASSES_INVULNERABILITY, false);
		hasTag(helper, LeafiaDamageSources.cryo, DamageTypeTags.IS_FIRE, false);
		// `endothermic` is setMagicDamage + setDamageBypassesArmor + setExplosion.
		hasTag(helper, LeafiaDamageSources.endothermic, DamageTypeTags.IS_EXPLOSION, true);
		hasTag(helper, LeafiaDamageSources.endothermic, DamageTypeTags.BYPASSES_ARMOR, true);
		hasTag(helper, LeafiaDamageSources.endothermic, DamageTypeTags.WITCH_RESISTANT_TO, true);
		hasTag(helper, LeafiaDamageSources.endothermic, DamageTypeTags.BYPASSES_EFFECTS, false);

		helper.succeed();
	}

	/**
	 * setDifficultyScaled has no tag — it is the {@code scaling} field, and only one source
	 * uses it. D-1010 records why it maps to {@code always} rather than vanilla's
	 * {@code when_caused_by_living_non_player}.
	 */
	@GameTest(template = EMPTY)
	public static void onlyPointedScalesWithDifficulty(GameTestHelper helper) {

		helper.assertTrue(holder(helper, LeafiaDamageSources.pointed).value().scaling()
						== net.minecraft.world.damagesource.DamageScaling.ALWAYS,
				"pointed is the one difficulty-scaled source");
		helper.assertTrue(holder(helper, LeafiaDamageSources.dfc).value().scaling()
						== net.minecraft.world.damagesource.DamageScaling.NEVER,
				"everything else never scales");
		helper.assertTrue(holder(helper, LeafiaDamageSources.dfc).value().exhaustion() == 0.0F,
				"exhaustion is 0 throughout - 1.12.2 had no per-source concept (D-025)");

		helper.succeed();
	}

	/**
	 * The addon's tag files must <em>add to</em> the base mod's, not replace them.
	 *
	 * <p>Both mods ship {@code data/minecraft/tags/damage_type/bypasses_armor.json}. Minecraft
	 * unions them unless one sets {@code "replace": true}, so a type from each should be in the
	 * tag at once. If this ever fails, installing the addon has been quietly stripping the base
	 * mod's damage types out of their tags — which would show up as players surviving hits, not
	 * as an error.
	 */
	@GameTest(template = EMPTY)
	public static void tagsMergeWithTheBaseModsRatherThanReplacingThem(GameTestHelper helper) {

		hasTag(helper, LeafiaDamageSources.fleija, DamageTypeTags.BYPASSES_ARMOR, true);

		ResourceKey<DamageType> baseRadiation = ResourceKey.create(Registries.DAMAGE_TYPE,
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "radiation"));
		helper.assertTrue(holder(helper, baseRadiation).is(DamageTypeTags.BYPASSES_ARMOR),
				"the base mod's radiation is still in bypasses_armor with the addon installed - "
						+ "if it is not, the addon's tag file replaced hbm's instead of merging");

		helper.succeed();
	}

	/**
	 * The unit conversions behind {@code enableSludgeUnits}. Every number is balance under
	 * standing rule 4, and each assertion below is one line of upstream read back.
	 */
	@GameTest(template = EMPTY)
	public static void sludgeUnitConversions(GameTestHelper helper) {

		near(helper, BullshitInDavenportIowa.SToSj(37), 1.0, "37 seconds is one sjmole");
		near(helper, BullshitInDavenportIowa.PSToPSj(1), 37.0, "and the per-second form inverts it");
		near(helper, BullshitInDavenportIowa.MToBn(0.2), 1.0, "0.2 metres is one ban");
		near(helper, BullshitInDavenportIowa.HEToCn(1), 15.0, "1 HE is 15 cn");
		near(helper, BullshitInDavenportIowa.HEPSToCnPSj(1), 555.0, "and 1 HE/s is 555 cn/sj");

		near(helper, BullshitInDavenportIowa.CelsiusToBm(-273.15), 0.0, "absolute zero is 0 bm");
		near(helper, BullshitInDavenportIowa.RADToGy(100), 1.0, "100 rad is one gray");
		near(helper, BullshitInDavenportIowa.SvToDy(6), 1.0, "6 Sv is one dy");
		near(helper, BullshitInDavenportIowa.RADToDy(600), 1.0, "so 600 rad is one dy");
		near(helper, BullshitInDavenportIowa.GyToEx(0.8571429), 1.0, "0.857 Gy is one ex");

		// The temperature curve's two branches meet at 51 C, where both give exactly 0. That is
		// the only place the piecewise function can be checked without trusting one branch.
		near(helper, BullshitInDavenportIowa.CelsiusToG(51.0), 0.0,
				"the two branches of the bm-to-g curve meet at 51 C");
		helper.assertTrue(BullshitInDavenportIowa.CelsiusToG(60.0) > 0,
				"and it climbs above that");
		helper.assertTrue(BullshitInDavenportIowa.CelsiusToG(40.0) < 0,
				"and falls below it");

		helper.succeed();
	}

	/** Small holders that came across with the batch. */
	@GameTest(template = EMPTY)
	public static void scaleConstantsAndDataHolders(GameTestHelper helper) {

		helper.assertTrue(com.leafia.database.FolkvangrJammers.lastDetectedJammer
				.equals(net.minecraft.core.BlockPos.ZERO), "the jammer position starts at origin");
		// D-3023: the tables are filled from the base mod's shape multipliers (upstream's MixinDictFrame did it per frame)
		java.util.Map<String, Float> shapes = com.leafia.database.AddonOreDictHazards.prefixToHazMultMap;
		helper.assertTrue(shapes.get("ingot") == 1F && shapes.get("block") == 10F && shapes.get("dust") == 3F && shapes.get("nugget") == 0.1F
				&& shapes.get("billet") == 0.5F && shapes.get("plateTriple") == 3F && shapes.get("crystal") == 1F,
				"the hazard tables hold the shapes' multipliers: " + shapes);
		helper.assertTrue(LeafiaLib.NumScale.KILO == 1_000, "and the scale constants still hold");

		helper.succeed();
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-6,
				what + ": expected " + expected + ", got " + actual);
	}
}
