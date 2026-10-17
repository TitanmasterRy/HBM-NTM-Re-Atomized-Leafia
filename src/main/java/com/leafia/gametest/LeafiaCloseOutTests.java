package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK3.ATEntry;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.building.sign.SignTE;
import com.leafia.contents.machines.reactors.pwr.PWRMeltdown;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.contents.worldgen.biomes.artificial.DigammaEffects;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.database.ImpactSeismic;
import com.leafia.dev.container_utility.LeafiaValidateSweep;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.eventbuses.LeafiaComponentTooltips;
import com.leafia.eventbuses.LeafiaServerHandlers;
import com.leafia.init.LeafiaAdvancements;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.StructuralIntegrityPreview;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The close-out batch (D-3040): the members of the twenty accumulator files that were still missing when every one of their
 * consumers was in - the validate sweep, the six advancements, the base PWR's hidden tab entries, schizo mode's config key, the
 * machine badges, the component and structural integrity tooltip lines, digamma rain, burnt skin, the stale anti-tamper
 * protections, the digamma crater's senses, the structural integrity preview and the seismic level's sync.
 *
 * <p>Every switch or static a test changes is put back in a {@code finally}; every entity it spawns is discarded; the mock players
 * are taken off the player list afterwards.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaCloseOutTests {

	private static final String EMPTY = "empty";

	@SuppressWarnings("deprecation")
	private static void detach(GameTestHelper helper, ServerPlayer player) {
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		player.discard();
	}

	// ------------------------------------------------------------------ the validate sweep

	/**
	 * A receiver is validated once when the player comes within its range, kept while the player is within 1.25 times it, and
	 * asked again only after the player has left that margin and come back. A removed one is forgotten; a block entity that is
	 * not a receiver is never asked.
	 */
	@GameTest(template = EMPTY)
	public static void theSweepValidatesEachReceiverOnceInRange(GameTestHelper helper) {
		if(!LeafiaFeatures.letterSigns) { helper.succeed(); return; }
		BlockState state = LeafiaBlocks.letter_signs.get("A").get().defaultBlockState();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
		SignTE near = new SignTE(origin, state);
		SignTE far = new SignTE(origin.offset(100, 0, 0), state);
		near.setLevel(helper.getLevel());
		far.setLevel(helper.getLevel());
		helper.assertTrue(near.affectionRange() == 32, "a sign's range is the default 32");
		List<BlockEntity> loaded = List.of(near, far);
		List<BlockEntity> sent = new ArrayList<>();
		LeafiaValidateSweep sweep = new LeafiaValidateSweep();

		sweep.step(helper.getLevel(), loaded, origin.offset(32, 0, 0), sent::add);
		helper.assertTrue(sent.equals(List.of(near)), "at exactly the range only the near sign is asked: " + sent.size());
		sweep.step(helper.getLevel(), loaded, origin.offset(32, 0, 0), sent::add);
		helper.assertTrue(sent.size() == 1, "a validated sign was asked again");

		sweep.step(helper.getLevel(), loaded, origin.offset(40, 0, 0), sent::add);
		helper.assertTrue(sweep.isValidated(near) && sent.size() == 1, "at 1.25 times the range it is still remembered");
		sweep.step(helper.getLevel(), loaded, origin.offset(41, 0, 0), sent::add);
		helper.assertTrue(!sweep.isValidated(near), "past 1.25 times the range it is forgotten");
		sweep.step(helper.getLevel(), loaded, origin.offset(33, 0, 0), sent::add);
		helper.assertTrue(sent.size() == 1, "back inside the margin but outside the range, it was asked");
		sweep.step(helper.getLevel(), loaded, origin.offset(0, 31, 0), sent::add);
		helper.assertTrue(sent.size() == 2 && sent.get(1) == near, "coming back into range did not ask again");

		near.setRemoved();
		sweep.step(helper.getLevel(), loaded, origin, sent::add);
		helper.assertTrue(!sweep.isValidated(near) && sent.size() == 2, "a removed sign is remembered or asked");

		BlockEntity notReceiver = new net.minecraft.world.level.block.entity.ChestBlockEntity(origin, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
		notReceiver.setLevel(helper.getLevel());
		sweep.step(helper.getLevel(), List.of(notReceiver), origin, sent::add);
		helper.assertTrue(sent.size() == 2 && sweep.size() == 0, "a chest was asked to validate");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the advancements

	/** The seven advancements load with upstream's parents, frames and rewards; the five impossible ones are granted by code alone. */
	@GameTest(template = EMPTY)
	public static void theAdvancementsHangWhereUpstreamHungThem(GameTestHelper helper) {
		var advancements = helper.getLevel().getServer().getAdvancements();
		String[][] expected = {
				{"genericfuel", "hbm:achievement/gas_cent", "TASK", "rods"},
				{"openpwr", "leafia:genericfuel", "GOAL", "pwr"},
				{"nukepwr", "leafia:openpwr", "CHALLENGE", "pwr"},
				{"nukebwr", "leafia:openpwr", "CHALLENGE", "pwr"},
				{"zetalite", "hbm:achievement/bismuth", "TASK", "corium"},
				{"nukeapr", "leafia:zetalite", "CHALLENGE", "apr"},
				{"dgomega", "hbm:achievement/omega12", "CHALLENGE", "rods"}};
		for(String[] row : expected) {
			boolean on = switch(row[3]) {
				case "rods" -> LeafiaFeatures.rods;
				case "pwr" -> LeafiaFeatures.rods && LeafiaFeatures.pwrReactor();
				case "corium" -> LeafiaFeatures.coriumOres && LeafiaFeatures.resourceItems;
				default -> LeafiaFeatures.coriumOres && LeafiaFeatures.aprReactor();
			};
			AdvancementHolder holder = advancements.get(LeafiaRef.rl(row[0]));
			helper.assertTrue((holder != null) == on, row[0] + " is " + (holder != null ? "loaded" : "absent") + " with its switches " + on);
			if(holder == null) continue;
			helper.assertTrue(holder.value().parent().map(p -> p.toString().equals(row[1])).orElse(false), row[0] + " does not hang on " + row[1]);
			helper.assertTrue(holder.value().display().get().getType() == AdvancementType.valueOf(row[2]), row[0] + " is not a " + row[2]);
		}
		AdvancementHolder dg = advancements.get(LeafiaRef.rl("dgomega"));
		if(dg != null) helper.assertTrue(dg.value().rewards().experience() == 1000, "dgomega rewards 1000 experience");
		AdvancementHolder zeta = advancements.get(LeafiaRef.rl("zetalite"));
		if(zeta != null) helper.assertTrue(zeta.value().rewards().experience() == 30 && zeta.value().criteria().size() == 1, "zetalite rewards 30 for one criterion");
		helper.succeed();
	}

	/** A meltdown's advancement follows the hot coolant: ultra-hot steam is a BWR, any liquid a PWR, a gas nothing. */
	@GameTest(template = EMPTY)
	public static void aMeltdownGrantsByItsHotCoolant(GameTestHelper helper) {
		helper.assertTrue(LeafiaAdvancements.NUKE_BWR.equals(PWRMeltdown.advancementFor(Fluids.ULTRAHOTSTEAM)), "ultra-hot steam is not nukebwr");
		helper.assertTrue(LeafiaAdvancements.NUKE_PWR.equals(PWRMeltdown.advancementFor(Fluids.WATER)), "water is not nukepwr");
		helper.assertTrue(PWRMeltdown.advancementFor(Fluids.HOTSTEAM) == null, "hot steam, a gas, granted something");
		helper.assertTrue(PWRMeltdown.advancementFor(null) == null, "no coolant granted something");
		helper.succeed();
	}

	/** {@code grantWithin} completes the advancement for a player inside the box and not for one outside it. */
	@GameTest(template = EMPTY)
	public static void grantWithinReachesOnlyThePlayersInTheBox(GameTestHelper helper) {
		if(!LeafiaFeatures.rods || !LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
			player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			var advancements = helper.getLevel().getServer().getAdvancements();
			AdvancementHolder far = advancements.get(LeafiaRef.rl(LeafiaAdvancements.NUKE_BWR));
			AdvancementHolder near = advancements.get(LeafiaRef.rl(LeafiaAdvancements.NUKE_PWR));
			LeafiaAdvancements.grantWithin(helper.getLevel(), new AABB(at.offset(300, 0, 0)).inflate(100), LeafiaAdvancements.NUKE_BWR);
			helper.assertTrue(!player.getAdvancements().getOrStartProgress(far).isDone(), "a player 300 blocks away was granted it");
			LeafiaAdvancements.grantWithin(helper.getLevel(), new AABB(at).inflate(100), LeafiaAdvancements.NUKE_PWR);
			helper.assertTrue(player.getAdvancements().getOrStartProgress(near).isDone(), "the player inside the box was not granted it");
		} finally {
			detach(helper, player);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the base PWR's tab entries

	/** With the overhaul on and the addon's PWR present, the base PWR's blocks and fuels leave the tabs; nothing else does. */
	@GameTest(template = EMPTY)
	public static void theOverhaulHidesTheBasePwrFromTheTabs(GameTestHelper helper) {
		boolean old = LeafiaFeatures.pwr;
		try {
			Item casing = item("hbm", "pwr_casing"), fuelBlock = item("hbm", "pwr_fuel"), fuel = item("hbm", "pwr_fuel_meu"),
					hot = item("hbm", "pwr_fuel_hot_mox"), printer = item("hbm", "pwr_printer");
			LeafiaFeatures.pwr = false;
			helper.assertTrue(!LeafiaCreativeTabs.hidesBasePwr(casing), "the casing is hidden with the overhaul off");
			LeafiaFeatures.pwr = true;
			if(LeafiaFeatures.pwrReactor()) {
				helper.assertTrue(LeafiaCreativeTabs.hidesBasePwr(casing) && LeafiaCreativeTabs.hidesBasePwr(fuelBlock)
						&& LeafiaCreativeTabs.hidesBasePwr(fuel) && LeafiaCreativeTabs.hidesBasePwr(hot), "a base PWR piece is still shown");
				helper.assertTrue(!LeafiaCreativeTabs.hidesBasePwr(printer) && !LeafiaCreativeTabs.hidesBasePwr(Items.STONE),
						"something that is not the base PWR is hidden");
				if(LeafiaBlocks.lwr_element != null)
					helper.assertTrue(!LeafiaCreativeTabs.hidesBasePwr(LeafiaBlocks.lwr_element.get().asItem()), "the addon's own PWR is hidden");
			}
		} finally {
			LeafiaFeatures.pwr = old;
		}
		helper.succeed();
	}

	private static Item item(String ns, String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ns, path));
	}

	// ------------------------------------------------------------------ schizo mode's key

	@GameTest(template = EMPTY)
	public static void schizoModeIsAConfigKeyAndOffByDefault(GameTestHelper helper) {
		helper.assertTrue(Boolean.FALSE.equals(LeafiaHazardSeamTests.defaultOf(LeafiaConfig.SPEC.getSpec(), "enableSchizoMode")),
				"enableSchizoMode is missing or on by default");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the machine badges

	/** Upstream's switch, case for case: the base mod's machines and the badges each one reads. */
	@GameTest(template = EMPTY)
	public static void theBadgeTableIsUpstreamsSwitch(GameTestHelper helper) {
		helper.assertTrue(MachineTooltip.BADGES.size() == 70, "seventy names, got " + MachineTooltip.BADGES.size());
		helper.assertTrue("BN".equals(MachineTooltip.BADGES.get("machine_zirnox")), "the ZIRNOX is a nuclear boiler");
		helper.assertTrue("MKBN".equals(MachineTooltip.BADGES.get("struct_watz_core")), "the Watz core");
		helper.assertTrue("MDN".equals(MachineTooltip.BADGES.get("rbmk_rod_reasim_mod")), "a ReaSim rod");
		helper.assertTrue("G".equals(MachineTooltip.BADGES.get("rtg_polonium")) && "C".equals(MachineTooltip.BADGES.get("machine_tower_large")), "a generator and a condenser");

		List<Component> zirnox = new ArrayList<>();
		MachineTooltip.addBadges(item("hbm", "machine_zirnox"), zirnox);
		helper.assertTrue(zirnox.size() == 1, "the nuclear badge did not join the steam line: " + zirnox.size());
		List<Component> rod = new ArrayList<>();
		MachineTooltip.addBadges(item("hbm", "rbmk_rod"), rod);
		helper.assertTrue(rod.size() == 1, "modular and nuclear did not join the multiblock line: " + rod.size());
		List<Component> stone = new ArrayList<>();
		MachineTooltip.addBadges(Items.STONE, stone);
		helper.assertTrue(stone.isEmpty(), "stone has a badge");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the tooltip lines

	@GameTest(template = EMPTY)
	public static void reactorComponentsSayWhatTheyAre(GameTestHelper helper) {
		if(LeafiaBlocks.lwr_element != null) {
			List<Component> lines = LeafiaComponentTooltips.linesFor(LeafiaBlocks.lwr_element.get().asItem());
			helper.assertTrue(lines.size() == 2 && lines.get(0).getString().contains("trait.leafia.component.pwr")
					|| lines.size() == 2 && lines.get(0).getString().contains("PWR"), "a PWR element is not a PWR component: " + lines);
			helper.assertTrue(LeafiaComponentTooltips.linesFor(LeafiaBlocks.lwr_casing.get().asItem()).isEmpty(), "the casing claims to be a component");
		}
		if(LeafiaBlocks.msr_element != null) {
			helper.assertTrue(LeafiaComponentTooltips.linesFor(LeafiaBlocks.msr_element.get().asItem()).size() == 2, "an MSR element is not an LFTR component");
			helper.assertTrue(LeafiaComponentTooltips.linesFor(LeafiaBlocks.msr_control.get().asItem()).isEmpty(), "the MSR control rod claims salt flows through it");
		}
		helper.assertTrue(LeafiaComponentTooltips.linesFor(Items.STONE).isEmpty(), "stone is a reactor component");

		boolean old = LeafiaFeatures.structuralIntegrity;
		try {
			LeafiaFeatures.structuralIntegrity = false;
			helper.assertTrue(LeafiaComponentTooltips.integrityLines(Items.STONE).isEmpty(), "integrity lines with the switch off");
			LeafiaFeatures.structuralIntegrity = true;
			List<Component> si = LeafiaComponentTooltips.integrityLines(Items.STONE);
			helper.assertTrue(si.size() == 3 && si.get(1).getString().startsWith(" - Mass: ") && si.get(2).getString().startsWith(" - Glue: "),
					"stone's integrity lines: " + si);
			helper.assertTrue(LeafiaComponentTooltips.integrityLines(Items.STICK).isEmpty(), "a stick has integrity lines");
		} finally {
			LeafiaFeatures.structuralIntegrity = old;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the server handlers

	/** Digamma rain falls on one tick in seventy, under open sky in the crater, and not with the crater switched off. */
	@GameTest(template = EMPTY)
	public static void digammaRainFallsOnceEverySeventyTicks(GameTestHelper helper) {
		boolean oldSchizo = DigammaCrater.schizoMode, oldCrater = LeafiaFeatures.digammaCrater;
		int oldCounter = LeafiaServerHandlers.digammaRainCounter;
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
		try {
			pig.setNoAi(true);
			DigammaCrater.schizoMode = true; // every biome is the crater
			LeafiaFeatures.digammaCrater = true;
			BlockPos head = new BlockPos(net.minecraft.util.Mth.floor(pig.getX() + 0.5), net.minecraft.util.Mth.floor(pig.getY() + pig.getBbHeight() - 0.1), net.minecraft.util.Mth.floor(pig.getZ() + 0.5));
			boolean sky = helper.getLevel().canSeeSky(head);
			float before = HbmLivingProps.getDigamma(pig);
			LeafiaServerHandlers.digammaRainCounter = 1;
			helper.assertTrue(!LeafiaServerHandlers.digammaRain(pig) && HbmLivingProps.getDigamma(pig) == before, "rain fell off its tick");
			LeafiaServerHandlers.digammaRainCounter = 0;
			boolean fell = LeafiaServerHandlers.digammaRain(pig);
			helper.assertTrue(fell == sky, "rain " + (fell ? "fell" : "did not fall") + " with the sky " + (sky ? "open" : "closed"));
			if(fell) {
				float got = HbmLivingProps.getDigamma(pig) - before;
				helper.assertTrue(got >= LeafiaServerHandlers.DIGAMMA_RAIN_MIN && got <= LeafiaServerHandlers.DIGAMMA_RAIN_MIN + LeafiaServerHandlers.DIGAMMA_RAIN_SPREAD,
						"one fall gave " + got);
			}
			LeafiaFeatures.digammaCrater = false;
			helper.assertTrue(!LeafiaServerHandlers.digammaRain(pig), "rain fell with the crater switched off");
			for(int i = 0; i < LeafiaServerHandlers.DIGAMMA_RAIN_PERIOD; i++) LeafiaServerHandlers.onServerTick(null);
			helper.assertTrue(LeafiaServerHandlers.digammaRainCounter == 0, "seventy ticks did not bring the counter round");
		} finally {
			DigammaCrater.schizoMode = oldSchizo;
			LeafiaFeatures.digammaCrater = oldCrater;
			LeafiaServerHandlers.digammaRainCounter = oldCounter;
			pig.discard();
		}
		helper.succeed();
	}

	/** Fire burns skin on a roll of 0 in {@code 5 + 5 x level}, up to level III; nothing else does, and not with the overhaul off. */
	@GameTest(template = EMPTY)
	public static void fireBurnsTheSkin(GameTestHelper helper) {
		boolean old = LeafiaFeatures.hazardOverhaul;
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
		try {
			var sources = helper.getLevel().damageSources();
			int[] bound = {0};
			LegacyRandomSource zero = new LegacyRandomSource(0) {
				@Override public int nextInt(int b) { bound[0] = b; return 0; }
			};
			LegacyRandomSource one = new LegacyRandomSource(0) {
				@Override public int nextInt(int b) { return 1; }
			};
			LeafiaFeatures.hazardOverhaul = false;
			helper.assertTrue(!LeafiaServerHandlers.burnSkin(pig, sources.inFire(), zero), "skin burnt with the overhaul off");
			LeafiaFeatures.hazardOverhaul = true;
			helper.assertTrue(!LeafiaServerHandlers.burnSkin(pig, sources.generic(), zero), "a generic hit burnt the skin");
			helper.assertTrue(!LeafiaServerHandlers.burnSkin(pig, sources.inFire(), one), "a roll of 1 burnt the skin");
			helper.assertTrue(LeafiaServerHandlers.burnSkin(pig, sources.inFire(), zero) && bound[0] == 5, "the first burn's odds were 1 in " + bound[0]);
			helper.assertTrue(LeafiaPotion.getSkinDamage(pig) >= 1, "no damaged skin after a burn");
			int skin = LeafiaPotion.getSkinDamage(pig);
			LeafiaServerHandlers.burnSkin(pig, sources.onFire(), zero);
			helper.assertTrue(bound[0] == 5 + 5 * skin, "burnt skin's odds were 1 in " + bound[0] + " at level " + skin);
		} finally {
			LeafiaFeatures.hazardOverhaul = old;
			pig.discard();
		}
		helper.succeed();
	}

	/** Loading a level forgets the field disturber's protections in its own dimension and no other. */
	@GameTest(template = EMPTY)
	public static void aLoadedLevelForgetsItsProtections(GameTestHelper helper) {
		ATEntry here = new ATEntry(helper.getLevel().dimension().location(), 123456, 7, 654321);
		ATEntry elsewhere = new ATEntry(ResourceLocation.fromNamespaceAndPath("leafia", "nowhere"), 123456, 7, 654321);
		try {
			EntityNukeExplosionMK3.at.put(here, Long.MAX_VALUE);
			EntityNukeExplosionMK3.at.put(elsewhere, Long.MAX_VALUE);
			int dropped = LeafiaServerHandlers.forgetProtections(helper.getLevel().dimension().location());
			helper.assertTrue(dropped >= 1 && !EntityNukeExplosionMK3.at.containsKey(here), "this dimension's protection survived");
			helper.assertTrue(EntityNukeExplosionMK3.at.containsKey(elsewhere), "another dimension's protection was forgotten");
		} finally {
			EntityNukeExplosionMK3.at.remove(here);
			EntityNukeExplosionMK3.at.remove(elsewhere);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the crater's senses

	/** In the crater: a beat every seventeen ticks with a ten-tick vignette, and an ambience that fades in and out by a hundredth. */
	@GameTest(template = EMPTY)
	public static void theCraterBeatsEverySeventeenTicks(GameTestHelper helper) {
		DigammaEffects fx = new DigammaEffects();
		Random rand = new Random(1);
		int beats = 0;
		for(int i = 1; i <= 34; i++) {
			boolean beat = fx.tick(true, 0, rand, n -> "m");
			if(beat) {
				beats++;
				helper.assertTrue(i % DigammaEffects.BEAT_PERIOD == 0, "a beat on tick " + i);
				helper.assertTrue(fx.vgTicks == DigammaEffects.VIGNETTE_TICKS, "the vignette did not start");
			}
		}
		helper.assertTrue(beats == 2, "two beats in 34 ticks, got " + beats);
		helper.assertTrue(Math.abs(fx.ambienceVolume - 0.34) < 1e-9, "the ambience after 34 ticks: " + fx.ambienceVolume);
		helper.assertTrue(Math.abs(fx.vignetteAlpha(1) - (float) Math.sin(0.9 * Math.PI) / 2.5F) < 1e-5, "the vignette's alpha: " + fx.vignetteAlpha(1));
		fx.tick(false, 0, rand, n -> "m");
		helper.assertTrue(Math.abs(fx.ambienceVolume - 0.33) < 1e-9, "the ambience did not fade out");
		helper.assertTrue(fx.texts.isEmpty(), "words without a dose");
		helper.succeed();
	}

	/** The dose: {@code (digamma / 10) ^ 0.666}; at the lethal ten the view narrows by three fourteenths and words come. */
	@GameTest(template = EMPTY)
	public static void aDigammaDoseNarrowsTheViewAndSpeaks(GameTestHelper helper) {
		helper.assertTrue(DigammaEffects.dose(0) == 0 && Math.abs(DigammaEffects.dose(10) - 1) < 1e-6, "the dose curve's ends");
		DigammaEffects fx = new DigammaEffects();
		Random low = new Random() {
			@Override public float nextFloat() { return 0F; }
			@Override public int nextInt(int bound) { return 3; }
		};
		for(int i = 0; i < 100; i++) fx.tick(false, 10, low, n -> "message " + n);
		helper.assertTrue(Math.abs(fx.fovMultiplier() - (1 - 3F / 14F)) < 1e-4, "the field of view at dose 1: " + fx.fovMultiplier());
		helper.assertTrue(!fx.texts.isEmpty() && fx.texts.get(0).message.equals("message 3"), "no words at dose 1");
		helper.assertTrue(fx.shake(1) > 0 && Math.abs(fx.shake(1) - DigammaEffects.SHAKE_SCALE) < 1e-6, "the shake at dose 1");
		fx.tick(false, 1, low, n -> "m");
		helper.assertTrue(fx.shake(1) == 0, "a dose under a quarter shook the camera"); // (0.1)^0.666 = 0.216
		helper.succeed();
	}

	// ------------------------------------------------------------------ the structural integrity preview

	@GameTest(template = EMPTY)
	public static void thePreviewFillsFromTheBottom(GameTestHelper helper) {
		List<StructuralIntegrityPreview.Box> standing = StructuralIntegrityPreview.boxes(0.5, 0);
		helper.assertTrue(standing.size() == 2 && standing.get(0).scale() == 0.75 && standing.get(1).scale() == 0.75, "a standing block's two copies");
		helper.assertTrue(StructuralIntegrityPreview.boxes(0.5, 0.9).size() == 1, "the growing copy outlived its three quarters");
		helper.assertTrue(StructuralIntegrityPreview.boxes(1, 0.375).size() == 1
				&& Math.abs(StructuralIntegrityPreview.boxes(1, 0.375).get(0).scale() - 0.85) < 1e-9, "a falling block swells to 0.85");

		List<StructuralIntegrityPreview.Edge> safe = StructuralIntegrityPreview.edges(1);
		helper.assertTrue(safe.stream().noneMatch(StructuralIntegrityPreview.Edge::red), "an unloaded box has red in it");
		List<StructuralIntegrityPreview.Edge> doomed = StructuralIntegrityPreview.edges(0);
		helper.assertTrue(doomed.stream().allMatch(StructuralIntegrityPreview.Edge::red), "a falling box has white in it");
		List<StructuralIntegrityPreview.Edge> half = StructuralIntegrityPreview.edges(0.5);
		helper.assertTrue(half.size() == 8 + 2 * 16, "squares and four-times-drawn verticals: " + half.size());
		helper.assertTrue(half.stream().filter(e -> !e.red() && e.y0() != e.y1()).allMatch(e -> e.y1() == 0) , "the white runs to half height");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the seismic level

	/** On the ground while the level is up, the view drops by 0 or 2% and jitters; off it, nothing. The sync packet is registered. */
	@GameTest(template = EMPTY)
	public static void theSeismicLevelWobblesTheGroundedView(GameTestHelper helper) {
		float old = ImpactSeismic.seismic;
		try {
			Random rand = new Random(7);
			ImpactSeismic.seismic = 1;
			boolean dropped = false;
			for(int i = 0; i < 50; i++) {
				ImpactSeismic.localTick(true, rand);
				helper.assertTrue(ImpactSeismic.fovM == 1 || Math.abs(ImpactSeismic.fovM - 0.98F) < 1e-6, "fov " + ImpactSeismic.fovM);
				helper.assertTrue(Math.abs(ImpactSeismic.shakeX) <= 0.5F && Math.abs(ImpactSeismic.shakeY) <= 0.5F, "the jitter");
				dropped |= ImpactSeismic.fovM != 1;
			}
			helper.assertTrue(dropped, "fifty grounded ticks never dropped the view");
			ImpactSeismic.localTick(false, rand);
			helper.assertTrue(ImpactSeismic.fovM == 1 && ImpactSeismic.shakeX == 0 && ImpactSeismic.shakeY == 0, "an airborne player wobbled");
			ImpactSeismic.seismic = 0;
			ImpactSeismic.localTick(true, rand);
			helper.assertTrue(ImpactSeismic.fovM == 1 && ImpactSeismic.shakeX == 0, "no level, yet a wobble");
			helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.WORLD_GLOBAL_SYNC) instanceof ImpactSeismic.SeismicSyncPacket, "the sync packet is not registered");
		} finally {
			ImpactSeismic.seismic = old;
			ImpactSeismic.localTick(false, new Random());
		}
		helper.succeed();
	}
}
