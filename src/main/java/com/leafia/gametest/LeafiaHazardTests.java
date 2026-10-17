package com.leafia.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.items.ModItems;
import com.hbm.lib.NtmTags;
import com.hbm.util.ContaminationUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.dev.hazards.CEHazardHelper;
import com.leafia.init.LeafiaHazards;
import com.leafia.init.hazards.ItemRads;
import com.leafia.init.hazards.ItemRads.MultiRadContainer;
import com.leafia.init.hazards.modifiers.NBTModifier;
import com.leafia.init.hazards.modifiers.NBTModifier.NBTKey;
import com.leafia.init.hazards.types.LCERad;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Beta;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.init.hazards.types.radiation.Radon;
import com.leafia.init.hazards.types.radiation.XRay;
import com.leafia.lib.LeafiaRef;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's radiation classification and the digamma on its blocks (D-1078): what an item the
 * addon made hazardous does to a player who carries it, at upstream's numbers.
 *
 * <p>The players are {@code makeMockPlayer}, not {@code makeMockServerPlayerInLevel} (the wind's
 * packet, see {@link LeafiaPillTests}), and each is aged past the spawn grace
 * ({@link ContaminationUtil#SPAWN_GRACE_TICKS}), because a player younger than ten seconds
 * takes nothing and a mock player is zero ticks old.
 *
 * <p><b>Every test that registers restores the registry.</b> Classification is off by default and
 * registers nothing, so the tests that need it turn the switch on and call
 * {@link LeafiaHazards#register()} themselves - which rewrites the base mod's hazard data for
 * balefire, corium, waste - and put every entry list, the switch and the radon flag back in a
 * {@code finally}. The body is synchronous: nothing else can observe the changed registry.
 *
 * <p>The doses are exact because everything between the type and the player's counter is
 * arithmetic: an unarmoured player's resistance factor is 10^0 = 1, and the only dice are the
 * skin-damage rolls, which land <em>after</em> the dose they would change.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaHazardTests {

	private static final String EMPTY = "empty";
	private static final float EPS = 1e-3F;

	// ---------------------------------------------------------------- scaffolding

	private static Player survivor(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.tickCount = ContaminationUtil.SPAWN_GRACE_TICKS + 200;
		HbmLivingProps.setRadiation(player, 0F);
		HbmLivingProps.setDigamma(player, 0F);
		return player;
	}

	private static float rad(Player player) {
		return HbmLivingProps.getRadiation(player);
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	/** Gives the player one stack in slot 0 and runs the base mod's per-tick hazard pass once. */
	private static void carryForATick(Player player, ItemStack stack) {
		for(int i = 0; i < 36; i++) player.getInventory().setItem(i, ItemStack.EMPTY); // not clearContent(): that takes the armour too
		player.getInventory().setItem(0, stack);
		HazardSystem.updatePlayerInventory(player);
	}

	private static void skin(Player player, int level) {
		player.removeEffect(LeafiaPotion.skindamage);
		if(level > 0) player.addEffect(new MobEffectInstance(LeafiaPotion.skindamage, 10000, level - 1));
	}

	/** One application of one type, straight from its {@code onUpdate}, on a fresh player. */
	private static float dose(GameTestHelper helper, HazardTypeBase type, float level, int count) {
		Player player = survivor(helper);
		type.onUpdate(player, level, new ItemStack(Items.STONE, count));
		return rad(player);
	}

	/** The registry's state, to put back. Entry lists are copied; the data objects stay the same. */
	private static final class Snapshot {
		final boolean classification = LeafiaFeatures.radiationClassification;
		final boolean radon = LeafiaConfig.enableRadon;
		final Map<Item, List<HazardEntry>> items = new HashMap<>();
		final Map<Object, List<HazardEntry>> ores = new HashMap<>();
		final Map<Item, HazardData> itemData = new HashMap<>();

		Snapshot() {
			HazardSystem.itemMap.forEach((k, v) -> { items.put(k, new ArrayList<>(v.getEntries())); itemData.put(k, v); });
			HazardSystem.oreMap.forEach((k, v) -> ores.put(k, new ArrayList<>(v.getEntries())));
		}

		void restore() {
			LeafiaFeatures.radiationClassification = classification;
			LeafiaConfig.enableRadon = radon;
			HazardSystem.itemMap.keySet().removeIf(k -> !items.containsKey(k));
			items.forEach((k, entries) -> {
				HazardData data = itemData.get(k);
				data.getEntries().clear();
				data.getEntries().addAll(entries);
				HazardSystem.itemMap.put(k, data);
			});
			HazardSystem.oreMap.keySet().removeIf(k -> !ores.containsKey(k));
			HazardSystem.oreMap.forEach((k, v) -> { v.getEntries().clear(); v.getEntries().addAll(ores.get(k)); });
		}
	}

	private static void withClassification(GameTestHelper helper, Runnable body) {
		Snapshot snapshot = new Snapshot();
		try {
			LeafiaFeatures.radiationClassification = true;
			LeafiaHazards.register();
			body.run();
		} finally {
			snapshot.restore();
		}
	}

	private static List<HazardEntry> entriesOf(Item item) {
		HazardData data = HazardSystem.itemMap.get(item);
		return data == null ? List.of() : data.getEntries();
	}

	private static HazardEntry entry(Item item, Class<? extends HazardTypeBase> type) {
		for(HazardEntry e : entriesOf(item)) if(e.getType().getClass() == type) return e;
		return null;
	}

	private static void level(GameTestHelper helper, Item item, Class<? extends HazardTypeBase> type, double expected, String what) {
		HazardEntry e = entry(item, type);
		helper.assertTrue(e != null, what + ": no " + type.getSimpleName() + " entry");
		near(helper, e.getBaseLevel(), expected, what + " " + type.getSimpleName());
	}

	// ---------------------------------------------------------------- the table

	@GameTest(template = EMPTY)
	public static void theTableIsUpstreams(GameTestHelper helper) throws Exception {

		int count = 0;
		for(Field f : ItemRads.class.getDeclaredFields())
			if(f.getType() == MultiRadContainer.class && Modifier.isStatic(f.getModifiers())) count++;
		helper.assertTrue(count == 57, "ItemRads should hold upstream's 57 containers, holds " + count);

		// The constructor's order is (alpha, beta, x, gamma, neutrons): x-rays third, gamma fourth.
		MultiRadContainer m = new MultiRadContainer(1, 2, 3, 4, 5);
		helper.assertTrue(m.alpha == 1 && m.beta == 2 && m.x == 3 && m.gamma == 4 && m.neutrons == 5 && m.radon == 0 && m.activation == 0,
				"argument order is alpha, beta, x, gamma, neutrons");
		MultiRadContainer seven = new MultiRadContainer(1, 2, 3, 4, 5, 6, 7);
		helper.assertTrue(seven.radon == 6 && seven.activation == 7, "the long form adds radon then activation");

		check(helper, ItemRads.corium, "corium", 30, 30, 30, 30, 50, 50, 0);
		check(helper, ItemRads.waste, "waste", 0, 125, 125, 50, 25, 0, 0);
		check(helper, ItemRads.waste_v, "waste_v", 0, 62.5, 62.5, 25, 12.5, 0, 0);
		check(helper, ItemRads.balefire, "balefire", 0, 0, 0, 300000, 0, 0, 0);
		check(helper, ItemRads.cobalt60, "cobalt60 (a third of 0/30/30/60)", 0, 10, 10, 20, 0, 0, 0);
		check(helper, ItemRads.fissium, "fissium", 0, 0, 0, 10, 30, 0, 0);
		check(helper, ItemRads.fissite, "fissite (a tenth of fissium)", 0, 0, 0, 1, 3, 0, 0);
		check(helper, ItemRads.flashlead, "flashlead (half of 0/12500/500/500)", 0, 6250, 250, 250, 0, 0, 0);
		check(helper, ItemRads.francium, "francium", 150000, 0, 0, 1400000, 0, 0, 0);
		check(helper, ItemRads.radium226, "radium226", 7.5, 0, 0, 0, 0, 5, 0);
		check(helper, ItemRads.uranium, "uranium", 0.35, 0, 0, 0, 0, 0.25, 0);
		check(helper, ItemRads.uranium233, "uranium233", 5, 0, 0, 0, 0, 0.425, 0);
		check(helper, ItemRads.uraniumFuel, "uraniumFuel", 0.5, 0, 0, 0, 0, 0.65, 0);
		check(helper, ItemRads.xanaxium, "xanaxium", 0, 0, 0, 2.2, 0, 0, 0);
		check(helper, ItemRads.tritium, "tritium", 0, 0, 0.5, 0, 0, 0, 0);
		check(helper, ItemRads.plutonium241, "plutonium241 (half of 0/25/25)", 0, 12.5, 12.5, 0, 0, 0, 0);
		check(helper, ItemRads.bismuth209zfb, "bismuth209zfb (0.35 of uranium235)", 0.35, 0, 0, 0, 0, 0.13125, 0);
		check(helper, ItemRads.americium242zfb, "americium242zfb (half of americiumRG)", 0, 4.5, 0, 0, 0, 0, 0);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, MultiRadContainer c, String name,
			double alpha, double beta, double x, double gamma, double neutrons, double radon, double activation) {
		helper.assertTrue(Math.abs(c.alpha - alpha) < 1e-5 && Math.abs(c.beta - beta) < 1e-5 && Math.abs(c.x - x) < 1e-5
				&& Math.abs(c.gamma - gamma) < 1e-5 && Math.abs(c.neutrons - neutrons) < 1e-5
				&& Math.abs(c.radon - radon) < 1e-5 && Math.abs(c.activation - activation) < 1e-5,
				name + " is " + c.alpha + "/" + c.beta + "/" + c.x + "/" + c.gamma + "/" + c.neutrons + "/" + c.radon + "/" + c.activation
						+ ", expected " + alpha + "/" + beta + "/" + x + "/" + gamma + "/" + neutrons + "/" + radon + "/" + activation);
	}

	@GameTest(template = EMPTY)
	public static void allSixTypesAreMarkedRadiation(GameTestHelper helper) {
		for(HazardTypeBase t : new HazardTypeBase[] { Alpha.INSTANCE, Beta.INSTANCE, Gamma.INSTANCE, Neutrons.INSTANCE, Radon.INSTANCE, XRay.INSTANCE })
			helper.assertTrue(t instanceof LCERad, t.getClass().getSimpleName() + " should be an LCERad");
		helper.assertTrue(!(((HazardTypeBase) Gamma.INSTANCE) instanceof HazardTypeRadiation), "and none of them is the base mod's own type");
		helper.assertTrue(CEHazardHelper.HAZARD_RATE == 1, "this port applies hazards every tick, so CE's hazardRate is one here");
		helper.assertTrue(CEHazardHelper.CE_HAZARD_RATE == 5, "CE applies every fifth tick, and the five non-radon types are divided by that (D-1094)");
		helper.succeed();
	}

	/**
	 * D-1094: five ticks of any non-radon type add up to one CE application, and radon, which CE
	 * multiplies by its rate, is unchanged.
	 */
	@GameTest(template = EMPTY)
	public static void fiveTicksAreOneCEApplication(GameTestHelper helper) {

		near(helper, CEHazardHelper.perTick(5.0), 1.0, "perTick divides by CE's five");
		helper.assertTrue(CEHazardHelper.rollOdds(2000) == 10000, "a roll made every tick has five times the denominator");

		Player gamma = survivor(helper);
		Player xray = survivor(helper);
		Player neutrons = survivor(helper);
		for(int tick = 0; tick < 5; tick++) {
			Gamma.INSTANCE.onUpdate(gamma, 20F, new ItemStack(Items.STONE));
			XRay.INSTANCE.onUpdate(xray, 20F, new ItemStack(Items.STONE));
			Neutrons.INSTANCE.onUpdate(neutrons, 20F, new ItemStack(Items.STONE));
		}
		near(helper, rad(gamma), 20 * 0.05, "five ticks of gamma 20 are CE's one application of 20 x 0.05");
		near(helper, rad(xray), 20 * 0.2, "and x-ray 20 x 0.2");
		near(helper, rad(neutrons), 20 * 1.3, "and neutrons 20 x 1.3");

		boolean old = LeafiaConfig.enableRadon;
		try {
			LeafiaConfig.enableRadon = true;
			Player radon = survivor(helper);
			for(int tick = 0; tick < 5; tick++) Radon.INSTANCE.onUpdate(radon, 4F, new ItemStack(Items.STONE));
			near(helper, rad(radon), 5 * 4.0, "radon is level x 5 per five ticks, as on CE: unchanged by D-1094");
		} finally {
			LeafiaConfig.enableRadon = old;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the types

	@GameTest(template = EMPTY)
	public static void gammaXRayAndNeutronsScaleByTheirFactorAndTheStack(GameTestHelper helper) {
		near(helper, dose(helper, Gamma.INSTANCE, 20F, 1), 0.2, "gamma 20 x 0.05 / 5");
		near(helper, dose(helper, Gamma.INSTANCE, 20F, 3), 0.6, "gamma on a stack of three");
		near(helper, dose(helper, XRay.INSTANCE, 20F, 1), 0.8, "x-ray 20 x 0.2 / 5");
		near(helper, dose(helper, XRay.INSTANCE, 20F, 2), 1.6, "x-ray on a stack of two");
		near(helper, dose(helper, Neutrons.INSTANCE, 20F, 1), 5.2, "neutrons 20 x 1.3 / 5");
		near(helper, dose(helper, Neutrons.INSTANCE, 20F, 2), 10.4, "neutrons on a stack of two");

		Player creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.tickCount = 400;
		Gamma.INSTANCE.onUpdate(creative, 20F, new ItemStack(Items.STONE));
		near(helper, rad(creative), 0, "a creative player takes nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aHeldReacherBendsTheLevel(GameTestHelper helper) {

		double plain = dose(helper, Gamma.INSTANCE, 20F, 1);

		Player held = survivor(helper);
		held.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.reacher.get()));
		Gamma.INSTANCE.onUpdate(held, 20F, new ItemStack(Items.STONE));
		double bent = sq(20D * 20) / 20;       // sqrt(level x 20) / 20, then x 0.05 / 5
		near(helper, rad(held), bent * 0.05 / 5, "a reacher in the main hand");
		helper.assertTrue(rad(held) < plain, "the reacher must lower the dose, " + rad(held) + " vs " + plain);

		// The bend sees the stack first: a stack of four is bent as 80, not as 20 four times.
		Player stacked = survivor(helper);
		stacked.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.reacher.get()));
		Gamma.INSTANCE.onUpdate(stacked, 20F, new ItemStack(Items.STONE, 4));
		near(helper, rad(stacked), sq(80D * 20) / 20 * 0.05 / 5, "a reacher in the off hand, stack of four");

		// CE's helper asks for a hand, not the inventory: the base mod's own type would count this one.
		Player pocket = survivor(helper);
		pocket.getInventory().setItem(5, new ItemStack(ModItems.reacher.get()));
		Gamma.INSTANCE.onUpdate(pocket, 20F, new ItemStack(Items.STONE));
		near(helper, rad(pocket), plain, "a reacher in a pocket is not held");

		// and under enable528 the helper says no, which is why upstream's 528 branch never runs
		boolean old = com.hbm.config.GeneralConfig.enable528;
		try {
			com.hbm.config.GeneralConfig.enable528 = true;
			Player p528 = survivor(helper);
			p528.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.reacher.get()));
			Gamma.INSTANCE.onUpdate(p528, 20F, new ItemStack(Items.STONE));
			near(helper, rad(p528), plain, "under 528 the reacher is not 'held'");
		} finally {
			com.hbm.config.GeneralConfig.enable528 = old;
		}
		helper.succeed();
	}

	/** The reworked radiation curve, written out so the test does not call the code it checks. */
	private static double sq(double x) {
		return Math.sqrt(x + 1.0 / ((x + 2.0) * (x + 2.0))) - 1.0 / (x + 2.0);
	}

	@GameTest(template = EMPTY)
	public static void alphaWaitsForDamagedSkinIII(GameTestHelper helper) {
		boolean old = LeafiaFeatures.radiationClassification;
		try {
			LeafiaFeatures.radiationClassification = true;
			for(int amp = 0; amp <= 2; amp++) {
				Player p = survivor(helper);
				skin(p, amp);
				Alpha.INSTANCE.onUpdate(p, 100F, new ItemStack(Items.STONE));
				near(helper, rad(p), 0, "alpha through damaged skin " + amp);
			}

			Player full = survivor(helper);
			skin(full, 3);
			Alpha.INSTANCE.onUpdate(full, 100F, new ItemStack(Items.STONE));
			near(helper, rad(full), 30.0, "alpha 100 at damaged skin III and full health: 1.5 / 1.0, / 5");

			Player half = survivor(helper);
			skin(half, 3);
			half.setHealth(10F);
			Alpha.INSTANCE.onUpdate(half, 100F, new ItemStack(Items.STONE));
			near(helper, rad(half), 100 * 1.5 / 0.625 / 5, "at half health div = 0.5 x 0.75 + 0.25 = 0.625");

			Player none = survivor(helper);
			skin(none, 3);
			Alpha.INSTANCE.onUpdate(none, 0F, new ItemStack(Items.STONE));
			near(helper, rad(none), 0, "a zero level is ignored");
			Alpha.INSTANCE.onUpdate(none, 100F, new ItemStack(Items.STONE, 2));
			near(helper, rad(none), 60.0, "the stack multiplies before the factor");
		} finally {
			LeafiaFeatures.radiationClassification = old;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void betaFollowsTheSkin(GameTestHelper helper) {
		boolean old = LeafiaFeatures.radiationClassification;
		try {
			LeafiaFeatures.radiationClassification = true;
			double[] factor = { 0.01, 0.04, 0.6, 0.6 };
			for(int amp = 0; amp <= 3; amp++) {
				Player p = survivor(helper);
				skin(p, amp);
				Beta.INSTANCE.onUpdate(p, 100F, new ItemStack(Items.STONE));
				near(helper, rad(p), 100 * factor[amp] / 5, "beta 100 at damaged skin " + amp);
			}
			Player hurt = survivor(helper);
			skin(hurt, 2);
			hurt.setHealth(5F);
			Beta.INSTANCE.onUpdate(hurt, 100F, new ItemStack(Items.STONE));
			near(helper, rad(hurt), 18.0, "under half health it jumps from 0.6 to 0.9");

			Player zero = survivor(helper);
			Beta.INSTANCE.onUpdate(zero, 0F, new ItemStack(Items.STONE));
			near(helper, rad(zero), 0, "a zero level is ignored");
		} finally {
			LeafiaFeatures.radiationClassification = old;
		}
		helper.succeed();
	}

	/** With the classification off a type is a plain dose: accountConfig answers 1.0. */
	@GameTest(template = EMPTY)
	public static void withTheSwitchOffTheHealthFactorIsOne(GameTestHelper helper) {
		boolean old = LeafiaFeatures.radiationClassification;
		try {
			LeafiaFeatures.radiationClassification = false;
			near(helper, dose(helper, Beta.INSTANCE, 100F, 1), 100.0 / 5, "beta without the health factor");
			near(helper, dose(helper, Alpha.INSTANCE, 100F, 1), 100.0 / 5, "alpha without the health factor");
		} finally {
			LeafiaFeatures.radiationClassification = old;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void radonIgnoresResistanceAndIsStoppedByAFineMask(GameTestHelper helper) {
		boolean old = LeafiaConfig.enableRadon;
		try {
			LeafiaConfig.enableRadon = true;
			near(helper, dose(helper, Radon.INSTANCE, 4F, 1), 4.0, "radon 4 is 4, not 4 / 20");
			near(helper, dose(helper, Radon.INSTANCE, 4F, 3), 12.0, "and scales with the stack");

			// resistance: a hazmat plate cuts gamma and does nothing to radon
			Player suited = survivor(helper);
			suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.hazmat_plate.get()));
			float mod = ContaminationUtil.calculateRadiationMod(suited);
			helper.assertTrue(mod < 0.9F, "a hazmat plate should resist, mod " + mod);
			Gamma.INSTANCE.onUpdate(suited, 20F, new ItemStack(Items.STONE));
			near(helper, rad(suited), 0.2 * mod, "gamma is cut by the suit");
			float before = rad(suited);
			Radon.INSTANCE.onUpdate(suited, 4F, new ItemStack(Items.STONE));
			near(helper, rad(suited) - before, 4.0, "radon is not");

			// a helmet that stops fine particles stops it outright
			Player masked = survivor(helper);
			masked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.schrabidium_helmet.get()));
			Radon.INSTANCE.onUpdate(masked, 4F, new ItemStack(Items.STONE));
			near(helper, rad(masked), 0, "radon through a sealed helmet");

			// and the switch
			LeafiaConfig.enableRadon = false;
			near(helper, dose(helper, Radon.INSTANCE, 4F, 1), 0, "radon with enableRadon off");
		} finally {
			LeafiaConfig.enableRadon = old;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the modifier

	@GameTest(template = EMPTY)
	public static void theNbtModifierReadsItsKeyFromTheStack(GameTestHelper helper) {
		NBTModifier beta = new NBTModifier(NBTKey.BETA);
		ItemStack stack = new ItemStack(Items.STONE);

		near(helper, beta.modify(stack, null, 10F), 10, "a stack with no tag passes through");
		NBTModifier.setMod(stack, NBTKey.BETA, 2.5);
		near(helper, beta.modify(stack, null, 10F), 25, "beta_mod multiplies");
		near(helper, new NBTModifier(NBTKey.GAMMA).modify(stack, null, 10F), 10, "another key is not read");
		beta.setAbsolute(stack, 7);
		near(helper, beta.modify(stack, null, 10F), 7, "beta_abs replaces, and wins over beta_mod");

		helper.assertTrue(NBTKey.ALPHA.getAbsKey().equals("alpha_abs") && NBTKey.XRAY.getModKey().equals("xray_mod")
				&& NBTKey.ACTIVATION.getAbsKey().equals("activation_abs") && NBTKey.NEUTRONS.getModKey().equals("neutrons_mod")
				&& NBTKey.RADON.getAbsKey().equals("radon_abs"), "the key names are upstream's");
		helper.assertTrue(NBTKey.values().length == 7, "seven keys");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the registrations

	@GameTest(template = EMPTY)
	public static void classificationIsOffByDefaultAndThenRegistersNothing(GameTestHelper helper) {

		helper.assertTrue(!LeafiaFeatures.radiationClassification, "radiationClassification is an overhaul and defaults off");

		Snapshot snapshot = new Snapshot();
		try {
			LeafiaFeatures.radiationClassification = false;
			Map<Item, List<String>> before = describe();
			int oresBefore = HazardSystem.oreMap.size();
			LeafiaHazards.register();
			Map<Item, List<String>> after = describe();
			helper.assertTrue(before.equals(after), "with the switch off register() must leave every item as it was");
			helper.assertTrue(HazardSystem.oreMap.size() == oresBefore, "and the ore map");
			if(LeafiaBlocks.ore_corium_zetalite != null)
				helper.assertTrue(!HazardSystem.itemMap.containsKey(LeafiaBlocks.ore_corium_zetalite.get().asItem()),
						"an ore only the addon names carries no hazard data at all");
			Item egg = ModItems.egg_balefire.get();
			helper.assertTrue(entry(egg, Gamma.class) == null && entry(egg, HazardTypeRadiation.class) != null,
					"balefire keeps the base mod's single figure");
		} finally {
			snapshot.restore();
		}
		helper.succeed();
	}

	private static Map<Item, List<String>> describe() {
		Map<Item, List<String>> out = new HashMap<>();
		HazardSystem.itemMap.forEach((k, v) -> {
			List<String> l = new ArrayList<>();
			for(HazardEntry e : v.getEntries()) l.add(e.getType().getClass().getName() + "@" + e.getBaseLevel());
			out.put(k, l);
		});
		return out;
	}

	@GameTest(template = EMPTY)
	public static void theBaseModsTableWasInPlaceFirst(GameTestHelper helper) {
		helper.assertTrue(LeafiaHazards.baseWasFirst,
				"the addon's setup ran before the base mod's hazard table: ItemRads would have nothing to replace");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void registrationReplacesTheRadiationEntryAndKeepsTheRest(GameTestHelper helper) {
		Item egg = ModItems.egg_balefire.get();
		List<String> kept = new ArrayList<>();
		for(HazardEntry e : entriesOf(egg)) if(!(e.getType() instanceof HazardTypeRadiation)) kept.add(e.getType().getClass().getName() + "@" + e.getBaseLevel());
		helper.assertTrue(entry(egg, HazardTypeRadiation.class) != null, "precondition: the base mod made balefire radioactive");

		withClassification(helper, () -> {
			// balefire: one gamma entry, and a tenth of it on the shard
			helper.assertTrue(entry(egg, HazardTypeRadiation.class) == null, "the base entry is gone");
			level(helper, egg, Gamma.class, 300000, "egg_balefire");
			level(helper, ModItems.egg_balefire_shard.get(), Gamma.class, 30000, "egg_balefire_shard");
			helper.assertTrue(entriesOf(egg).stream().filter(e -> e.getType() instanceof LCERad).count() == 1, "and only that one");
			List<String> after = new ArrayList<>();
			for(HazardEntry e : entriesOf(egg)) if(!(e.getType() instanceof LCERad)) after.add(e.getType().getClass().getName() + "@" + e.getBaseLevel());
			helper.assertTrue(kept.equals(after), "every other hazard on the item is untouched: " + kept + " vs " + after);

			// corium: the base mod's two blocks and the addon's two ores, all one container
			List<Item> corium = new ArrayList<>(List.of(ModBlocks.block_corium.get().asItem(), ModBlocks.block_corium_cobble.get().asItem()));
			if(LeafiaBlocks.ore_corium_chernobylite != null) corium.add(LeafiaBlocks.ore_corium_chernobylite.get().asItem());
			if(LeafiaBlocks.ore_corium_zetalite != null) corium.add(LeafiaBlocks.ore_corium_zetalite.get().asItem());
			for(Item item : corium) {
				String n = item.toString();
				helper.assertTrue(entry(item, HazardTypeRadiation.class) == null, n + " loses the plain figure");
				level(helper, item, Alpha.class, 30, n);
				level(helper, item, Beta.class, 30, n);
				level(helper, item, XRay.class, 30, n);
				level(helper, item, Gamma.class, 30, n);
				level(helper, item, Neutrons.class, 50, n);
				level(helper, item, Radon.class, 50, n);
				helper.assertTrue(entriesOf(item).size() == 6, n + " has six entries: no activation, got " + entriesOf(item).size());
			}

			// waste: 0/125/125/50/25 at 3, 3 x 1/2, 0.3, 0.3 x 1/2 and 1
			Object[][] waste = {
					{ ModItems.nuclear_waste.get(), 3.0 }, { ModItems.nuclear_waste_vitrified.get(), 1.5 },
					{ ModItems.nuclear_waste_tiny.get(), 0.3 }, { ModItems.nuclear_waste_vitrified_tiny.get(), 0.15 },
					{ ModItems.billet_nuclear_waste.get(), 1.0 } };
			for(Object[] w : waste) {
				Item item = (Item) w[0];
				double k = (Double) w[1];
				String n = item.toString();
				helper.assertTrue(entry(item, Alpha.class) == null, n + " has no alpha");
				level(helper, item, Beta.class, 125 * k, n);
				level(helper, item, XRay.class, 125 * k, n);
				level(helper, item, Gamma.class, 50 * k, n);
				level(helper, item, Neutrons.class, 25 * k, n);
			}

			// every entry carries a modifier of its own key
			for(HazardEntry e : entriesOf(egg)) {
				if(!(e.getType() instanceof LCERad)) continue;
				helper.assertTrue(e.getMods().size() == 1 && e.getMods().get(0) instanceof NBTModifier, "each entry carries one NBTModifier");
			}
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void registeringByOreNameKeysTheTagAndScalesByTheMultiplier(GameTestHelper helper) {
		Snapshot snapshot = new Snapshot();
		try {
			LeafiaFeatures.radiationClassification = true;
			ItemRads.uranium.register("ingotLeafiaTest", 2F);
			HazardData data = HazardSystem.oreMap.get(NtmTags.ore("ingotLeafiaTest"));
			helper.assertTrue(data != null, "the tag the base mod would key it on");
			HazardEntry a = null, r = null;
			for(HazardEntry e : data.getEntries()) {
				if(e.getType() == Alpha.INSTANCE) a = e;
				if(e.getType() == Radon.INSTANCE) r = e;
			}
			helper.assertTrue(a != null && r != null && data.getEntries().size() == 2, "alpha and radon only");
			near(helper, a.getBaseLevel(), 0.7, "0.35 x 2");
			near(helper, r.getBaseLevel(), 0.5, "0.25 x 2");

			LeafiaFeatures.radiationClassification = false;
			ItemRads.uranium.register("ingotLeafiaTestOff", 2F);
			helper.assertTrue(!HazardSystem.oreMap.containsKey(NtmTags.ore("ingotLeafiaTestOff")), "off leaves no empty entry behind either");
		} finally {
			snapshot.restore();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- end to end

	@GameTest(template = EMPTY)
	public static void carryingACoriumOreIrradiatesYouByTheSumOfItsParts(GameTestHelper helper) {
		if(LeafiaBlocks.ore_corium_zetalite == null) { helper.succeed(); return; }
		Item ore = LeafiaBlocks.ore_corium_zetalite.get().asItem();
		withClassification(helper, () -> {
			// alpha 30 -> 0 (skin intact), beta 30 x 0.01, gamma 30 x 0.05, x-ray 30 x 0.2,
			// neutrons 50 x 1.3, radon 50 x 1, in one tick
			// each divided by CE's five (D-1094), except the radon
			double perOre = (0.3 + 1.5 + 6 + 65) / 5 + 50;

			Player one = survivor(helper);
			carryForATick(one, new ItemStack(ore));
			near(helper, rad(one), perOre, "one ore, one tick");

			Player stack = survivor(helper);
			carryForATick(stack, new ItemStack(ore, 3));
			near(helper, rad(stack), perOre * 3, "a stack of three");

			// the same ore under damaged skin III: alpha now counts, beta jumps to 0.6
			Player burned = survivor(helper);
			skin(burned, 3);
			carryForATick(burned, new ItemStack(ore));
			near(helper, rad(burned), (30 * 1.5 + 30 * 0.6 + 1.5 + 6 + 65) / 5 + 50, "one ore under damaged skin III");

			// a hazmat plate takes 10^-r off everything but the radon
			Player suited = survivor(helper);
			suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.hazmat_plate.get()));
			float mod = ContaminationUtil.calculateRadiationMod(suited);
			carryForATick(suited, new ItemStack(ore));
			near(helper, rad(suited), (0.3 + 1.5 + 6 + 65) / 5 * mod + 50, "one ore in a hazmat plate");

			// a stack's own beta override, through the modifier on the entry
			ItemStack tuned = new ItemStack(ore);
			NBTModifier.setAbsolute(tuned, NBTKey.BETA, 100);
			NBTModifier.setMod(tuned, NBTKey.NEUTRONS, 0.5);
			Player custom = survivor(helper);
			carryForATick(custom, tuned);
			near(helper, rad(custom), (100 * 0.01 + 1.5 + 6 + 25 * 1.3) / 5 + 50, "beta_abs 100 and neutrons_mod 0.5");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNuclearWasteBilletIsThreeParts(GameTestHelper helper) {
		withClassification(helper, () -> {
			Player p = survivor(helper);
			carryForATick(p, new ItemStack(ModItems.billet_nuclear_waste.get()));
			// beta 125 x 0.01, x-ray 125 x 0.2, gamma 50 x 0.05, neutrons 25 x 1.3
			near(helper, rad(p), (1.25 + 25 + 2.5 + 32.5) / 5, "one billet");

			Player tiny = survivor(helper);
			carryForATick(tiny, new ItemStack(ModItems.nuclear_waste_tiny.get()));
			near(helper, rad(tiny), (1.25 + 25 + 2.5 + 32.5) / 5 * 0.3, "a tiny pile of waste is 0.3 of a billet");

			Player vit = survivor(helper);
			carryForATick(vit, new ItemStack(ModItems.nuclear_waste_vitrified.get()));
			near(helper, rad(vit), (1.25 + 25 + 2.5 + 32.5) / 5 * 1.5, "vitrified waste is half the waste, three times over");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void balefireIsAllGamma(GameTestHelper helper) {
		withClassification(helper, () -> {
			Player p = survivor(helper);
			carryForATick(p, new ItemStack(ModItems.egg_balefire_shard.get()));
			near(helper, rad(p), 300.0, "the shard: 30000 gamma x 0.05 / 5");
		});
		helper.succeed();
	}

	// ---------------------------------------------------------------- digamma

	@GameTest(template = EMPTY)
	public static void theOsmiridiumBlocksCarryDigamma(GameTestHelper helper) {
		if(LeafiaBlocks.block_xanaxium == null || LeafiaBlocks.block_welded_osmiridium == null) { helper.succeed(); return; }

		for(Item item : new Item[] { LeafiaBlocks.block_xanaxium.get().asItem(), LeafiaBlocks.block_welded_osmiridium.get().asItem() }) {
			String n = item.toString();
			List<HazardEntry> entries = entriesOf(item);
			helper.assertTrue(entries.size() == 1 && entries.get(0).getType() == LeafiaHazards.DIGAMMA, n + " should carry exactly the digamma entry, has " + entries.size());
			near(helper, entries.get(0).getBaseLevel(), 0.04, n + " digamma");
		}

		Item item = LeafiaBlocks.block_xanaxium.get().asItem();
		Player p = survivor(helper);
		carryForATick(p, new ItemStack(item));
		near(helper, HbmLivingProps.getDigamma(p), 0.002, "0.04 / 20 per tick");
		near(helper, rad(p), 0, "and no radiation");

		Player stack = survivor(helper);
		carryForATick(stack, new ItemStack(item, 5));
		near(helper, HbmLivingProps.getDigamma(stack), 0.01, "the base mod's type scales with the stack size (D-2287)");

		Player creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.tickCount = 400;
		carryForATick(creative, new ItemStack(item));
		near(helper, HbmLivingProps.getDigamma(creative), 0, "a creative player takes none");

		// the tooltip is the base mod's own HazardTypeDigamma now (the stand-in is retired, D-3023); its drawing is client only
		helper.assertTrue(LeafiaHazards.DIGAMMA == HazardRegistry.DIGAMMA, "the digamma type is the base mod's");
		helper.succeed();
	}
}
