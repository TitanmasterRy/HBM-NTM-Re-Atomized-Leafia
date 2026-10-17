package com.leafia.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockHazard;
import com.hbm.config.RadiationConfig;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeHydroactive;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.items.ModItems;
import com.hbm.lib.NtmTags;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.hbm.util.ContaminationUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.dev.rad_correction.RadCorrection;
import com.leafia.eventbuses.LeafiaSharpEdges;
import com.leafia.init.LeafiaHazards;
import com.leafia.init.LeafiaOreDict;
import com.leafia.init.hazards.types.HazardTypeAlkaline;
import com.leafia.init.hazards.types.HazardTypeSharpEdges;
import com.leafia.init.hazards.types.HazardTypeToxic;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Radon;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaHazardSeams;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's hazard rework (HAZARDS, D-3023): the four hazard types, the registrations they ride on, and
 * the seams that replace nine of upstream's mixins. Everything that changes base behaviour is behind
 * {@code features.hazardOverhaul} or {@code radiationClassification}, so each test sets the switch it needs
 * and puts every static and every registry back in a {@code finally}.
 *
 * <p>Mock players are {@code makeMockPlayer} (aged past the spawn grace) except where a server player is
 * needed to see a packet sent - there the mock is detached from the player list afterwards (D-3000).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaHazardSeamTests {

	private static final String EMPTY = "empty";
	private static final float EPS = 1e-3F;

	// ---------------------------------------------------------------- scaffolding

	/** Everything these tests may change, to put back. */
	private static final class Restore {
		final boolean overhaul = LeafiaFeatures.hazardOverhaul;
		final boolean classification = LeafiaFeatures.radiationClassification;
		final boolean advisor = LeafiaFeatures.advisor;
		final boolean radon = LeafiaConfig.enableRadon;
		final boolean toxic = LeafiaConfig.disableToxic;
		final boolean sludge = LeafiaConfig.bullshitUnits;
		final boolean hot = RadiationConfig.disableHot;
		final float nerf = HazardTypeSharpEdges.sharpStackNerf;
		final Map<Item, HazardData> items = new HashMap<>(HazardSystem.itemMap);
		final Map<TagKey<Item>, HazardData> ores = new HashMap<>(HazardSystem.oreMap);
		final Map<ComparableStack, HazardData> stacks = new HashMap<>(HazardSystem.stackMap);
		final Map<HazardData, List<HazardEntry>> entries = new IdentityHashMap<>();
		final Map<Item, List<HazardClass>> armour = new HashMap<>(ArmorRegistry.hazardClasses);

		Restore() {
			for(HazardData d : HazardSystem.itemMap.values()) entries.put(d, new ArrayList<>(d.getEntries()));
			for(HazardData d : HazardSystem.oreMap.values()) entries.put(d, new ArrayList<>(d.getEntries()));
			for(HazardData d : HazardSystem.stackMap.values()) entries.put(d, new ArrayList<>(d.getEntries()));
		}

		void restore() {
			LeafiaFeatures.hazardOverhaul = overhaul;
			LeafiaFeatures.radiationClassification = classification;
			LeafiaFeatures.advisor = advisor;
			LeafiaConfig.enableRadon = radon;
			LeafiaConfig.disableToxic = toxic;
			LeafiaConfig.bullshitUnits = sludge;
			RadiationConfig.disableHot = hot;
			HazardTypeSharpEdges.sharpStackNerf = nerf;
			HazardSystem.itemMap.clear();
			HazardSystem.itemMap.putAll(items);
			HazardSystem.oreMap.clear();
			HazardSystem.oreMap.putAll(ores);
			HazardSystem.stackMap.clear();
			HazardSystem.stackMap.putAll(stacks);
			entries.forEach((d, list) -> { d.getEntries().clear(); d.getEntries().addAll(list); });
			ArmorRegistry.hazardClasses.clear();
			ArmorRegistry.hazardClasses.putAll(armour);
		}
	}

	private static Player survivor(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.tickCount = ContaminationUtil.SPAWN_GRACE_TICKS + 200;
		HbmLivingProps.setRadiation(player, 0F);
		HbmLivingProps.setRadBuf(player, 0F);
		return player;
	}

	private static ServerPlayer serverPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/** A mock server player has no connection: sending a packet to one throws, and that is how a send is seen. */
	private static boolean sends(Runnable action) {
		try {
			action.run();
		} catch(RuntimeException e) {
			return String.valueOf(e.getMessage()).contains("may not be sent");
		}
		return false;
	}

	private static float rad(Player player) {
		return HbmLivingProps.getRadiation(player);
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	/** A component as its keys and literals, translatable arguments in brackets. */
	private static String flat(Component c) {
		StringBuilder out = new StringBuilder();
		flat(c, out);
		return out.toString();
	}

	private static void flat(Component c, StringBuilder out) {
		if(c.getContents() instanceof TranslatableContents t) {
			out.append(t.getKey());
			if(t.getArgs().length > 0) {
				out.append('(');
				for(Object arg : t.getArgs()) out.append(arg instanceof Component ac ? flat(ac) : String.valueOf(arg));
				out.append(')');
			}
		} else if(c.getContents() instanceof PlainTextContents p) {
			out.append(p.text());
		}
		for(Component sibling : c.getSiblings()) flat(sibling, out);
	}

	private static List<String> flat(List<Component> list) {
		List<String> out = new ArrayList<>();
		for(Component c : list) out.add(ChatFormatting.stripFormatting(flat(c)));
		return out;
	}

	private static HazardEntry find(HazardData data, HazardTypeBase type) {
		if(data == null) return null;
		for(HazardEntry e : data.getEntries()) if(e.getType() == type) return e;
		return null;
	}

	private static HazardData ore(String name) {
		return HazardSystem.oreMap.get(NtmTags.ore(name));
	}

	private static float levelOf(String name, HazardTypeBase type) {
		HazardEntry e = find(ore(name), type);
		return e == null ? -1F : e.getBaseLevel();
	}

	// ---------------------------------------------------------------- the switch-over

	/** D-3023 retired three stand-ins; the base mod's own take their place, and a lingering copy would be a second source. */
	@GameTest(template = EMPTY)
	public static void theRetiredStandInsAreGone(GameTestHelper helper) {
		for(String name : new String[] {"CEChunkRad", "CERadiate", "DigammaHazard"}) {
			boolean found = true;
			try {
				Class.forName("com.leafia.dev.hazards." + name);
			} catch(ClassNotFoundException e) {
				found = false;
			}
			helper.assertTrue(!found, name + " should be gone");
		}
		helper.assertTrue(LeafiaHazards.DIGAMMA == HazardRegistry.DIGAMMA, "the digamma the addon registers is the base mod's type");
		helper.succeed();
	}

	/** The base mod's capped increment is CE's rule: nothing under 1e-5, nothing past the cap, the rest up to it. */
	@GameTest(template = EMPTY)
	public static void theBaseCappedIncrementIsTheOneTheAddonUses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 100, 1));
		float before = ChunkRadiationManager.getRadiation(level, pos.getX(), pos.getY(), pos.getZ());
		try {
			float added = ChunkRadiationManager.incrementRad(level, pos, 30F, before + 100F);
			near(helper, added, 30, "30 under a cap 100 above");
			added = ChunkRadiationManager.incrementRad(level, pos, 100F, before + 100F);
			near(helper, added, 70, "100 more is cut at the cap");
			added = ChunkRadiationManager.incrementRad(level, pos, 1F, before + 100F);
			near(helper, added, 0, "nothing past the cap");
			added = ChunkRadiationManager.incrementRad(level, pos, 1.0E-6F, before + 1000F);
			near(helper, added, 0, "an amount under 1e-5 is not worth making");
		} finally {
			float now = ChunkRadiationManager.getRadiation(level, pos.getX(), pos.getY(), pos.getZ());
			if(now > before) ChunkRadiationManager.decrementRad(level, pos.getX(), pos.getY(), pos.getZ(), now - before);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- sharp edges

	@GameTest(template = EMPTY)
	public static void sharpEdgesTooltipAndNumbers(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			helper.assertTrue(HazardTypeSharpEdges.percent(40F).equals("40%") && HazardTypeSharpEdges.percent(2.5F).equals("3%"), "Math.round(v) and a percent sign");
			helper.assertTrue(HazardTypeSharpEdges.stackPercent(40F, 4) == 70, "4 x 40 x 0.25 + 40 x 0.75 = 70, got " + HazardTypeSharpEdges.stackPercent(40F, 4));
			helper.assertTrue(HazardTypeSharpEdges.stackPercent(40F, 2) == 50, "and for two");
			HazardTypeSharpEdges.sharpStackNerf = 0.5F;
			helper.assertTrue(HazardTypeSharpEdges.stackPercent(40F, 2) == 60, "the nerf is a live static");
			HazardTypeSharpEdges.sharpStackNerf = r.nerf;

			List<Component> lines = new ArrayList<>();
			HazardTypeSharpEdges.INSTANCE.addHazardInformation(survivor(helper), lines, 40F, new ItemStack(Items.STICK, 4), List.of());
			List<String> text = flat(lines);
			helper.assertTrue(text.equals(List.of("[trait._hazarditem.sharp]", " -::trait._hazarditem.sharp.add(40%)",
					" -::trait._hazarditem.stack 70%")), "tooltip " + text);
			lines.clear();
			HazardTypeSharpEdges.INSTANCE.addHazardInformation(survivor(helper), lines, 40F, new ItemStack(Items.STICK), List.of());
			helper.assertTrue(lines.size() == 2, "one item has no stack line");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void sharpEdgesAddDamage(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			HazardSystem.itemMap.put(Items.STICK, new HazardData().addEntry(LeafiaHazards.SHARP, 40F));
			HazardSystem.itemMap.put(Items.BONE, new HazardData().addEntry(LeafiaHazards.SHARP, 20F));
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.STICK))), 4.0, "10 x (0.4 x 0.25 + 0.4 x 0.75)");
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.STICK, 4))), 7.0, "four in a stack: 10 x (1.6 x 0.25 + 0.4 x 0.75)");
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.STICK), new ItemStack(Items.BONE, 2))), 5.0, "two kinds: modifier 0.8, max 0.4");
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.DIRT), ItemStack.EMPTY)), 0.0, "nothing sharp, nothing empty");
			HazardTypeSharpEdges.sharpStackNerf = 0F;
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.STICK, 4))), 16.0, "nerf 0: the plain sum");
			HazardTypeSharpEdges.sharpStackNerf = 1F;
			near(helper, LeafiaSharpEdges.additionalDamage(10F, List.of(new ItemStack(Items.STICK, 4))), 4.0, "nerf 1: only the strongest");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSharpHitAddsAPointedHitAfterIt(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			HazardSystem.itemMap.put(Items.STICK, new HazardData().addEntry(LeafiaHazards.SHARP, 40F));
			for(boolean on : new boolean[] {true, false}) {
				LeafiaFeatures.hazardOverhaul = on;
				PWRTicks.runQueue();
				LivingEntity golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, 3, 2, 3);
				Player attacker = survivor(helper);
				attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
				float before = golem.getHealth();
				golem.hurt(helper.getLevel().damageSources().playerAttack(attacker), 6F);
				near(helper, before - golem.getHealth(), 6.0, (on ? "on" : "off") + ": the blow itself");
				PWRTicks.runQueue();
				near(helper, before - golem.getHealth(), on ? 8.4 : 6.0, (on ? "on" : "off") + ": and the extra 6 x 0.4 on the next tick");
				golem.discard();
			}
		} finally {
			r.restore();
			PWRTicks.runQueue();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void fallKnockbackAndPickupCutToo(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			HazardSystem.itemMap.put(Items.STICK, new HazardData().addEntry(LeafiaHazards.SHARP, 40F));
			LeafiaFeatures.hazardOverhaul = true;
			ServerLevel level = helper.getLevel();

			Player p = survivor(helper);
			p.getInventory().setItem(5, new ItemStack(Items.STICK));
			near(helper, LeafiaSharpEdges.hurt(p, level.damageSources().fall(), 5F), 2.0, "a fall of 5 cuts for 5 x 0.4 from the pocket");
			near(helper, LeafiaSharpEdges.hurt(p, level.damageSources().generic(), 8F), 0.0, "a plain hit only remembers its damage");
			near(helper, LeafiaSharpEdges.knockback(p, 0.4), 3.2, "and a knockback cuts for that remembered 8");
			near(helper, LeafiaSharpEdges.knockback(p, 0.0), 0.0, "no knockback, no cut");
			near(helper, LeafiaSharpEdges.pickup(p, new ItemStack(Items.STICK)), 0.4, "picking one up is damage 1");
			PWRTicks.runQueue();

			// and through the real event bus: the same numbers arrive as health
			Player target = survivor(helper);
			target.getInventory().setItem(5, new ItemStack(Items.STICK));
			LeafiaSharpEdges.hurt(target, level.damageSources().generic(), 8F);
			float before = target.getHealth();
			NeoForge.EVENT_BUS.post(new LivingKnockBackEvent(target, 0.4F, 0, 0));
			PWRTicks.runQueue();
			near(helper, before - target.getHealth(), 3.2, "knockback on the bus");
			before = target.getHealth();
			ItemEntity drop = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), new ItemStack(Items.STICK));
			NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(target, drop));
			PWRTicks.runQueue();
			near(helper, before - target.getHealth(), 0.4, "a pick-up on the bus");

			LeafiaFeatures.hazardOverhaul = false;
			before = target.getHealth();
			NeoForge.EVENT_BUS.post(new LivingKnockBackEvent(target, 0.4F, 0, 0));
			NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(target, drop));
			PWRTicks.runQueue();
			near(helper, before - target.getHealth(), 0.0, "with the switch off the bus changes nothing");
		} finally {
			r.restore();
			PWRTicks.runQueue();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- alkaline

	private static int datum(ItemStack stack, String key) {
		return HazardTypeAlkaline.getInt(stack, key);
	}

	@GameTest(template = EMPTY)
	public static void alkaliMetalsReactByGrade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HazardTypeAlkaline type = HazardTypeAlkaline.INSTANCE;
		double x = helper.absolutePos(BlockPos.ZERO).getX() + 1.5, y = 300, z = helper.absolutePos(BlockPos.ZERO).getZ() + 1.5;
		BlockPos where = BlockPos.containing(x, y, z);
		float before = ChunkRadiationManager.getRadiation(level, where.getX(), where.getY(), where.getZ());
		try {
			ItemStack s2 = new ItemStack(Items.STONE);
			helper.assertTrue(type.reactAlkaline(level, s2, x, y, z, 2) == 5, "sodium adds 5");
			helper.assertTrue(datum(s2, "damage") == 5 && datum(s2, "flaming") == 239, "damage 5, flaming 240 then one off, got " + datum(s2, "damage") + "/" + datum(s2, "flaming"));
			helper.assertTrue(type.reactAlkaline(level, s2, x, y, z, 2) == 10 && datum(s2, "flaming") == 239, "and again");

			ItemStack s3 = new ItemStack(Items.STONE);
			helper.assertTrue(type.reactAlkaline(level, s3, x, y, z, 3) == 2 && datum(s3, "flaming") == 239, "potassium adds 2");

			ItemStack s1 = new ItemStack(Items.STONE);
			CustomData.update(DataComponents.CUSTOM_DATA, s1, t -> t.putInt("flaming", 100));
			helper.assertTrue(type.reactAlkaline(level, s1, x, y, z, 1) == 1 && datum(s1, "flaming") == 239, "lithium that is already flaming always reacts, and restarts the burn");
			ItemStack cold = new ItemStack(Items.STONE);
			int dmg = type.reactAlkaline(level, cold, x, y, z, 1);
			helper.assertTrue(dmg == 0 || dmg == 1, "lithium that is not flaming reacts one time in twenty");

			ItemStack s0 = new ItemStack(Items.STONE);
			helper.assertTrue(type.reactAlkaline(level, s0, x, y, z, 0) == 0 && !s0.has(DataComponents.CUSTOM_DATA), "grade 0 leaves no trace");

			for(int grade = 4; grade <= 6; grade++) {
				ItemStack blast = new ItemStack(Items.STONE);
				helper.assertTrue(type.reactAlkaline(level, blast, x, y, z, grade) == 1000, "grade " + grade + " adds 1000, enough to destroy it");
			}
			float after = ChunkRadiationManager.getRadiation(level, where.getX(), where.getY(), where.getZ());
			near(helper, after - before, RadiationConfig.enableChunkRads ? 40 : 0, "francium leaves 40 RAD in the chunk");
			helper.assertTrue(HazardTypeAlkaline.DESTROYED_ABOVE == 200, "a stack over 200 damage goes");
		} finally {
			float now = ChunkRadiationManager.getRadiation(level, where.getX(), where.getY(), where.getZ());
			if(now > before) ChunkRadiationManager.decrementRad(level, where.getX(), where.getY(), where.getZ(), now - before);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFlamingMetalBurnsDownAndASoakedDropIsDestroyed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HazardTypeAlkaline type = HazardTypeAlkaline.INSTANCE;
		double x = helper.absolutePos(BlockPos.ZERO).getX() + 1.5, y = 300, z = helper.absolutePos(BlockPos.ZERO).getZ() + 1.5;

		helper.assertTrue(HazardTypeAlkaline.isApplicationTick(10L) && !HazardTypeAlkaline.isApplicationTick(11L), "an application every fifth tick, CE's hazardRate");

		ItemStack burning = new ItemStack(Items.STONE);
		CustomData.update(DataComponents.CUSTOM_DATA, burning, t -> t.putInt("flaming", 240));
		helper.assertTrue(type.tickAlkaline(level, burning, x, y, z, 3), "a flaming stack reports it");
		helper.assertTrue(datum(burning, "flaming") == 235, "and counts down by CE's hazardRate, got " + datum(burning, "flaming"));
		CustomData.update(DataComponents.CUSTOM_DATA, burning, t -> t.putInt("flaming", 1));
		type.tickAlkaline(level, burning, x, y, z, 3);
		helper.assertTrue(datum(burning, "flaming") == 0 && !burning.has(DataComponents.CUSTOM_DATA), "the last tick removes the key, and the empty tag with it");
		helper.assertTrue(!type.tickAlkaline(level, new ItemStack(Items.STONE), x, y, z, 3), "a cold stack does not");

		// a holder on a flaming stack is lit
		Player p = survivor(helper);
		ItemStack held = new ItemStack(Items.STONE);
		CustomData.update(DataComponents.CUSTOM_DATA, held, t -> t.putInt("flaming", 100));
		type.application(p, 3F, held);
		helper.assertTrue(p.getRemainingFireTicks() > 0, "the holder is set alight");

		// a dropped item: out of the water it forgets its damage
		ItemEntity dry = new ItemEntity(level, x, y, z, new ItemStack(Items.STONE));
		CustomData.update(DataComponents.CUSTOM_DATA, dry.getItem(), t -> t.putInt("damage", 50));
		type.application(dry, 2F);
		helper.assertTrue(datum(dry.getItem(), "damage") == 0, "a dry drop forgets");

		// in the water it takes the grade's damage and past 200 it is gone
		BlockPos wet = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
		ItemEntity soaked = new ItemEntity(level, wet.getX() + 0.5, wet.getY() + 0.2, wet.getZ() + 0.5, new ItemStack(Items.STONE));
		CustomData.update(DataComponents.CUSTOM_DATA, soaked.getItem(), t -> t.putInt("damage", 195));
		type.application(soaked, 2F);
		helper.assertTrue(!soaked.isRemoved() && datum(soaked.getItem(), "damage") == 200, "200 is not past 200");
		type.application(soaked, 2F);
		helper.assertTrue(soaked.isRemoved(), "205 is");
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);

		List<Component> lines = new ArrayList<>();
		type.addHazardInformation(p, lines, 3F, new ItemStack(Items.STONE), List.of());
		type.addHazardInformation(p, lines, 1.5F, new ItemStack(Items.STONE), List.of());
		helper.assertTrue(flat(lines).equals(List.of("[trait._hazarditem.hydro 3]", "[trait._hazarditem.hydro 1.5]")), "tooltip " + flat(lines));
		helper.succeed();
	}

	// ---------------------------------------------------------------- toxic

	private static Player toxicSubject(GameTestHelper helper) {
		Player p = survivor(helper);
		p.removeAllEffects();
		return p;
	}

	private static int amp(Player p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		MobEffectInstance i = p.getEffect(effect);
		return i == null ? -100 : i.getAmplifier();
	}

	@GameTest(template = EMPTY)
	public static void toxicMetalsClimbTheLadder(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaConfig.disableToxic = false;
			HazardTypeToxic t = HazardTypeToxic.INSTANCE;

			Player low = toxicSubject(helper);
			t.onUpdate(low, 1.75F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(low, MobEffects.WEAKNESS) == 0 && low.getEffect(MobEffects.WEAKNESS).getDuration() == 110, "level 1.75: weakness I for 110 ticks");
			helper.assertTrue(amp(low, MobEffects.MOVEMENT_SLOWDOWN) == -100 && amp(low, MobEffects.HUNGER) == -100, "and nothing else");

			Player mid = toxicSubject(helper);
			t.onUpdate(mid, 3F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(mid, MobEffects.WEAKNESS) == 2, "level 3: weakness (int)(3 - 1)");
			helper.assertTrue(amp(mid, MobEffects.MOVEMENT_SLOWDOWN) == -100, "and no slowness: (int) min(4, 3 - 4) = -1 is upstream's, which is no strength at all");
			Player four = toxicSubject(helper);
			t.onUpdate(four, 4F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(four, MobEffects.MOVEMENT_SLOWDOWN) == 0, "level 4: slowness I");

			Player high = toxicSubject(helper);
			t.onUpdate(high, 5F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(high, MobEffects.WEAKNESS) == 4 && amp(high, MobEffects.MOVEMENT_SLOWDOWN) == 1 && amp(high, MobEffects.HUNGER) == 5, "level 5: 4, 1 and hunger 5");

			Player fatigue = toxicSubject(helper);
			t.onUpdate(fatigue, 9F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(fatigue, MobEffects.DIG_SLOWDOWN) == 1, "level 9: mining fatigue (int)(9 - 8)");
			helper.assertTrue(amp(fatigue, MobEffects.HARM) == -100, "but no instant damage yet");

			Player extreme = toxicSubject(helper);
			t.onUpdate(extreme, 17F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(extreme, MobEffects.HARM) == 1, "level 17: instant damage (int)(17 - 16)");

			// the reacher protects from the first block and not from the second
			Player reach = toxicSubject(helper);
			reach.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.reacher.get()));
			t.onUpdate(reach, 9F, new ItemStack(Items.STONE));
			helper.assertTrue(amp(reach, MobEffects.WEAKNESS) == -100 && amp(reach, MobEffects.HUNGER) == -100, "a held reacher stops weakness and hunger");
			helper.assertTrue(amp(reach, MobEffects.DIG_SLOWDOWN) == 1, "but not the fatigue");

			LeafiaConfig.disableToxic = true;
			Player off = toxicSubject(helper);
			t.onUpdate(off, 17F, new ItemStack(Items.STONE));
			helper.assertTrue(off.getActiveEffects().isEmpty(), "disableToxic does nothing at all");

			helper.assertTrue(HazardTypeToxic.adjectiveKey(2F).endsWith("little") && HazardTypeToxic.adjectiveKey(2.5F).endsWith("medium")
					&& HazardTypeToxic.adjectiveKey(4F).endsWith("medium") && HazardTypeToxic.adjectiveKey(4.1F).endsWith("high")
					&& HazardTypeToxic.adjectiveKey(8F).endsWith("high") && HazardTypeToxic.adjectiveKey(8.1F).endsWith("veryhigh")
					&& HazardTypeToxic.adjectiveKey(16F).endsWith("veryhigh") && HazardTypeToxic.adjectiveKey(16.1F).endsWith("extreme"), "the five bands break above 2, 4, 8 and 16");
			List<Component> lines = new ArrayList<>();
			t.addHazardInformation(low, lines, 5F, new ItemStack(Items.STONE), List.of());
			helper.assertTrue(flat(lines).equals(List.of("trait._hazarditem.toxic.high trait._hazarditem.toxic")), "tooltip " + flat(lines));
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the registrations

	@GameTest(template = EMPTY)
	public static void theAlkaliAndToxicNamesAreGradedByShape(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaFeatures.hazardOverhaul = true;
			LeafiaHazards.register();
			HazardTypeBase alk = LeafiaHazards.ALKALINE, tox = LeafiaHazards.TOXIC;

			near(helper, levelOf(OreDictManager.LI.ingot(), alk), 1, "lithium ingot");
			near(helper, levelOf(OreDictManager.LI.block(), alk), 1, "alkaline is not scaled by shape");
			near(helper, levelOf(OreDictManager.NA.dust(), alk), 2, "sodium dust");
			near(helper, levelOf(LeafiaOreDict.K.ingot(), alk), 3, "potassium");
			near(helper, levelOf(LeafiaOreDict.RB.ingot(), alk), 4, "rubidium");
			near(helper, levelOf(OreDictManager.CS.billet(), alk), 5, "caesium billet");
			near(helper, levelOf(OreDictManager.CS137.ingot(), alk), 5, "caesium-137");
			near(helper, levelOf(LeafiaOreDict.FR.crystal(), alk), 6, "francium crystal");
			near(helper, levelOf(OreDictManager.SR.ingot(), alk), 1.5, "strontium");
			near(helper, levelOf(OreDictManager.SR90.dust(), alk), 1.5, "strontium-90");
			helper.assertTrue(levelOf(OreDictManager.LI.nugget(), alk) < 0, "a nugget is not one of the names");

			near(helper, levelOf(OreDictManager.PB.ingot(), tox), 1.75, "lead ingot");
			near(helper, levelOf(OreDictManager.PB.block(), tox), 1.75 + (17.5 - 1.75) * 0.25, "a block is a quarter of the way to ten times");
			near(helper, levelOf("plateSextuple" + OreDictManager.PB.mats[0], tox), 1.75 + (10.5 - 1.75) * 0.25, "a welded plate is six");
			near(helper, levelOf(OreDictManager.BE.ingot(), tox), 1.75, "beryllium");
			near(helper, levelOf(OreDictManager.CD.dust(), tox), 5 + (15 - 5) * 0.25, "cadmium dust, powder x 3");
			near(helper, levelOf(OreDictManager.AS.ingot(), tox), 10, "arsenic");

			int before = ore(OreDictManager.LI.ingot()).getEntries().size();
			LeafiaHazards.register();
			helper.assertTrue(ore(OreDictManager.LI.ingot()).getEntries().size() == before, "registering twice does not pile entries");

			LeafiaFeatures.hazardOverhaul = false;
			r.restore();
			Restore fresh = new Restore();
			LeafiaHazards.register();
			helper.assertTrue(find(ore(OreDictManager.LI.ingot()), alk) == null && find(ore(OreDictManager.PB.ingot()), tox) == null, "off: neither exists");
			fresh.restore();
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void osmiridiumCarriesDigammaAndHydroactiveIsReplaced(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			boolean hadHydro = false;
			for(HazardEntry e : HazardSystem.itemMap.get(ModItems.powder_caesium.get()).getEntries()) hadHydro |= e.getType() instanceof HazardTypeHydroactive;
			helper.assertTrue(hadHydro, "the base mod's caesium powder is hydroreactive");

			LeafiaFeatures.hazardOverhaul = true;
			LeafiaHazards.register();
			near(helper, levelOf(OreDictManager.OSMIRIDIUM.ingot(), HazardRegistry.DIGAMMA), 0.004, "osmiridium ingot");
			near(helper, levelOf(OreDictManager.OSMIRIDIUM.block(), HazardRegistry.DIGAMMA), 0.04, "a block is ten times");
			near(helper, levelOf(OreDictManager.OSMIRIDIUM.dust(), HazardRegistry.DIGAMMA), 0.012, "a dust is three");
			near(helper, levelOf(OreDictManager.OSMIRIDIUM.nugget(), HazardRegistry.DIGAMMA), 0.0004, "and every shape, not only the regular ones");
			if(LeafiaFeatures.resourceItems) near(helper, levelOf(LeafiaOreDict.XN.ingot(), HazardRegistry.DIGAMMA), 0.004, "xanaxium ingot");

			boolean hydro = false;
			for(HazardData d : HazardSystem.itemMap.values()) for(HazardEntry e : d.getEntries()) hydro |= e.getType() instanceof HazardTypeHydroactive;
			for(HazardData d : HazardSystem.oreMap.values()) for(HazardEntry e : d.getEntries()) hydro |= e.getType() instanceof HazardTypeHydroactive;
			for(HazardData d : HazardSystem.stackMap.values()) for(HazardEntry e : d.getEntries()) hydro |= e.getType() instanceof HazardTypeHydroactive;
			helper.assertTrue(!hydro, "no hydroactive entry is left anywhere");
			helper.assertTrue(find(HazardSystem.itemMap.get(ModItems.powder_caesium.get()), HazardRegistry.HOT) != null, "caesium powder still burns");

			// the mono filter takes fine particles with the overhaul and not without
			List<HazardClass> mono = ArmorRegistry.hazardClasses.get(ModItems.gas_mask_filter_mono.get());
			helper.assertTrue(mono.equals(List.of(HazardClass.PARTICLE_COARSE, HazardClass.GAS_MONOXIDE, HazardClass.PARTICLE_FINE)), "mono filter " + mono);
			r.restore();
			LeafiaFeatures.hazardOverhaul = false;
			LeafiaHazards.registerArmor();
			mono = ArmorRegistry.hazardClasses.get(ModItems.gas_mask_filter_mono.get());
			helper.assertTrue(mono.equals(List.of(HazardClass.PARTICLE_COARSE, HazardClass.GAS_MONOXIDE)), "off: the base mod's two, " + mono);
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void classifiedFramesGetTheirShapeMultiplier(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaFeatures.radiationClassification = false;
			LeafiaHazards.register();
			helper.assertTrue(find(ore(OreDictManager.U.ingot()), Alpha.INSTANCE) == null, "off: no alpha on uranium");
			r.restore();

			LeafiaFeatures.radiationClassification = true;
			LeafiaHazards.register();
			near(helper, levelOf(OreDictManager.U.ingot(), Alpha.INSTANCE), 0.35, "uranium ingot alpha");
			near(helper, levelOf(OreDictManager.U.ingot(), Radon.INSTANCE), 0.25, "and radon (0.5 / 2)");
			near(helper, levelOf(OreDictManager.U.block(), Alpha.INSTANCE), 3.5, "a block is ten times");
			near(helper, levelOf(OreDictManager.U.dust(), Alpha.INSTANCE), 1.05, "a dust is three");
			near(helper, levelOf(OreDictManager.U.nugget(), Alpha.INSTANCE), 0.035, "a nugget a tenth");
			near(helper, levelOf(OreDictManager.PU239.ingot(), Alpha.INSTANCE), 5, "plutonium-239");
			near(helper, levelOf(LeafiaOreDict.FR.ingot(), Gamma.INSTANCE), 1400000, "francium's gamma");
			near(helper, levelOf(LeafiaOreDict.FSALLOY.ingot(), Gamma.INSTANCE), 1, "fissite is a tenth of fissium");
			boolean plain = false;
			for(HazardEntry e : ore(OreDictManager.U.ingot()).getEntries()) plain |= e.getType() instanceof HazardTypeRadiation;
			helper.assertTrue(!plain, "the base mod's single figure is replaced");
			helper.assertTrue(ore(OreDictManager.U.ingot()).getEntries().get(0).getMods().size() == 1, "the entries carry their modifier");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void sharpEdgesRideTheItemsOnlyWithTheOverhaul(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaFeatures.hazardOverhaul = true;
			LeafiaHazards.register();
			near(helper, level(ModItems.blades_desh.get()), 40, "desh blades");
			near(helper, level(ModItems.blades_steel.get()), 40, "steel blades");
			near(helper, level(ModItems.blades_titanium.get()), 40, "titanium blades");
			near(helper, level(ModItems.nugget_zirconium.get()), 10, "zirconium nugget");
			near(helper, level(ModItems.debris_shrapnel.get()), 1, "shrapnel");
			if(LeafiaItems.lwr_shrapnel != null) near(helper, level(LeafiaItems.lwr_shrapnel.get()), 35, "the PWR's shrapnel");
			if(LeafiaItems.lwr_shard != null) near(helper, level(LeafiaItems.lwr_shard.get()), 85, "the PWR's shard");
			if(LeafiaResourceItems.get("dfcsh_cable") != null) {
				List<HazardEntry> cable = HazardSystem.itemMap.get(LeafiaResourceItems.get("dfcsh_cable").get()).getEntries();
				helper.assertTrue(cable.size() == 2 && cable.get(0).getType() == LeafiaHazards.SHARP && cable.get(1).getType() == HazardRegistry.DIGAMMA, "the cable: sharp 5, then digamma 0.003");
				near(helper, cable.get(0).getBaseLevel(), 5, "cable sharp");
				near(helper, level(LeafiaResourceItems.get("dfcsh_beam").get()), 25, "beam sharp");
				near(helper, level(LeafiaResourceItems.get("dfcsh_corner").get()), 5, "corner sharp");
			}
			r.restore();

			LeafiaFeatures.hazardOverhaul = false;
			LeafiaHazards.register();
			helper.assertTrue(HazardSystem.itemMap.get(ModItems.blades_desh.get()) == null || level(ModItems.blades_desh.get()) < 0, "off: the blades are not sharp");
			if(LeafiaResourceItems.get("dfcsh_cable") != null)
				helper.assertTrue(HazardSystem.itemMap.get(LeafiaResourceItems.get("dfcsh_cable").get()).getEntries().size() == 1, "off: the cable is digamma alone");
			// the items that declare these hazards say so
			LeafiaRodItem lithium = LeafiaRodItem.fromResourceMap.get("leafia_rod_lithium");
			LeafiaRodItem lead = LeafiaRodItem.fromResourceMap.get("leafia_rod_lead");
			if(lithium != null && lead != null) {
				helper.assertTrue(lithium.unwired().equals(List.of("alkaline")) && lead.unwired().equals(List.of("toxic")), "off: listed as unwired");
				LeafiaFeatures.hazardOverhaul = true;
				helper.assertTrue(lithium.unwired().isEmpty() && lead.unwired().isEmpty(), "on: wired");
				near(helper, find(LeafiaHazards.itemHazards(lithium), LeafiaHazards.ALKALINE).getBaseLevel(), 1, "the lithium rod's alkaline");
				near(helper, find(LeafiaHazards.itemHazards(lead), LeafiaHazards.TOXIC).getBaseLevel(), 2, "the lead rod's toxic");
			}
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	private static float level(Item item) {
		HazardEntry e = find(HazardSystem.itemMap.get(item), LeafiaHazards.SHARP);
		return e == null ? -1F : e.getBaseLevel();
	}

	// ---------------------------------------------------------------- the seams

	@GameTest(template = EMPTY)
	public static void aBurningItemWarnsTheAdvisor(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			ServerPlayer p = serverPlayer(helper);
			p.getAbilities().instabuild = false; // a mock server player is creative, and upstream's branch skips creative
			HazardEntry hot = new HazardEntry(HazardRegistry.HOT, 3F);
			ItemStack stack = new ItemStack(Items.STONE);
			LeafiaFeatures.hazardOverhaul = true;
			LeafiaFeatures.advisor = true;
			RadiationConfig.disableHot = false;
			helper.assertTrue(sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, p, 3F)), "a hot entry warns");
			helper.assertTrue(!sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(new HazardEntry(HazardRegistry.BLINDING, 3F), stack, p, 3F)), "another type does not");
			RadiationConfig.disableHot = true;
			helper.assertTrue(!sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, p, 3F)), "hot disabled: the base mod returned early, so no warning");
			RadiationConfig.disableHot = false;
			p.getAbilities().instabuild = true;
			helper.assertTrue(!sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, p, 3F)), "creative: upstream's branch returns early");
			p.getAbilities().instabuild = false;
			LeafiaFeatures.advisor = false;
			helper.assertTrue(!sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, p, 3F)), "no advisor feature");
			LeafiaFeatures.advisor = true;
			LeafiaFeatures.hazardOverhaul = false;
			helper.assertTrue(!sends(() -> LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, p, 3F)), "the switch off");
			LeafiaFeatures.hazardOverhaul = true;
			LeafiaHazardSeams.HOT_ADVISOR.after(hot, stack, survivor(helper), 3F); // a non-server player: nothing to send to, nothing thrown
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void radonGasIsReworkedForProtection(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaFeatures.hazardOverhaul = true;
			LeafiaConfig.enableRadon = true;

			Player bare = survivor(helper);
			LeafiaHazardSeams.contact(bare, false);
			near(helper, rad(bare), 0.05, "bare, radon: 0.05 that ignores resistance");
			Player dense = survivor(helper);
			LeafiaHazardSeams.contact(dense, true);
			near(helper, rad(dense), 0.5, "bare, dense: 0.5");

			Player masked = survivor(helper);
			masked.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.schrabidium_helmet.get()));
			float mod = ContaminationUtil.calculateRadiationMod(masked);
			LeafiaHazardSeams.contact(masked, false);
			near(helper, rad(masked), 0.05 * mod, "masked, radon: still 0.05, through the suit's resistance");
			Player maskedDense = survivor(helper);
			maskedDense.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.schrabidium_helmet.get()));
			LeafiaHazardSeams.contact(maskedDense, true);
			near(helper, rad(maskedDense), 0, "masked, dense: nothing");
			helper.assertTrue(HbmLivingProps.getAsbestos(bare) == 0, "and none of the base mod's asbestos");

			LeafiaConfig.enableRadon = false;
			Player off = survivor(helper);
			LeafiaHazardSeams.contact(off, true);
			near(helper, rad(off), 0, "enableRadon off");
			LeafiaConfig.enableRadon = true;

			BlockState state = Blocks.AIR.defaultBlockState();
			Player entity = survivor(helper);
			helper.assertTrue(LeafiaHazardSeams.RADON_CONTACT.contact(ModBlocks.gas_radon.get(), state, helper.getLevel(), BlockPos.ZERO, entity), "plain radon is handled");
			helper.assertTrue(LeafiaHazardSeams.RADON_CONTACT.contact(ModBlocks.gas_radon_dense.get(), state, helper.getLevel(), BlockPos.ZERO, entity), "and dense");
			helper.assertTrue(!LeafiaHazardSeams.RADON_CONTACT.contact(ModBlocks.gas_coal.get(), state, helper.getLevel(), BlockPos.ZERO, entity), "another gas is not");
			LeafiaFeatures.hazardOverhaul = false;
			helper.assertTrue(!LeafiaHazardSeams.RADON_CONTACT.contact(ModBlocks.gas_radon.get(), state, helper.getLevel(), BlockPos.ZERO, entity), "off: the base mod's");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theGeigerHasNineBands(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			helper.assertTrue(LeafiaHazardSeams.geigerBands(0.5).equals(List.of(0, 0, 1)), "0.5: silent twice, a soft click once");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(3).equals(List.of(0, 1)), "3: " + LeafiaHazardSeams.geigerBands(3));
			helper.assertTrue(LeafiaHazardSeams.geigerBands(5.5).equals(List.of(1)), "5.5: past the second silent band, " + LeafiaHazardSeams.geigerBands(5.5));
			helper.assertTrue(LeafiaHazardSeams.geigerBands(7).equals(List.of(1)), "7");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(15).equals(List.of(2)), "15");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(25).equals(List.of(2, 3)), "25");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(45).equals(List.of(3, 4)), "45");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(80).equals(List.of(4, 5)), "80");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(110).equals(List.of(5, 6)), "110");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(350).equals(List.of(6, 7)), "350 is inclusive in 6 and 7");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(750).equals(List.of(7, 8)), "750");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(1500).equals(List.of(8, 9)), "1500");
			helper.assertTrue(LeafiaHazardSeams.geigerBands(5000).equals(List.of(9)), "5000");
			near(helper, LeafiaHazardSeams.geigerPitch(50), 1.0, "no pitch up to 50");
			near(helper, LeafiaHazardSeams.geigerPitch(200), 1.0, "the formula starts at 200");
			near(helper, LeafiaHazardSeams.geigerPitch(1000), 1 + 800.0 / 35000, "1000");
			near(helper, LeafiaHazardSeams.geigerPitch(50000), 1.1, "capped at 1.1");
			helper.assertTrue(LeafiaHazardSeams.geigerSounds().size() == 9, "nine sounds, the base mod has six");
			helper.assertTrue(LeafiaHazardSeams.geigerSounds().get(8).getId().getPath().equals("item.geiger9"), "in order");

			LivingEntity holder = survivor(helper);
			LeafiaFeatures.hazardOverhaul = false;
			helper.assertTrue(!LeafiaHazardSeams.GEIGER_CLICKS.click(helper.getLevel(), holder, ItemStack.EMPTY, 10F), "off: the base mod clicks");
			LeafiaFeatures.hazardOverhaul = true;
			helper.assertTrue(LeafiaHazardSeams.GEIGER_CLICKS.click(helper.getLevel(), holder, ItemStack.EMPTY, 10F), "on: the addon does");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	private static List<String> readout(Player p) {
		List<String> out = new ArrayList<>();
		for(Component line : LeafiaHazardSeams.geigerLines(p))
			out.add(ChatFormatting.stripFormatting(line.getSiblings().isEmpty() ? flat(line) : line.getSiblings().get(0).getString()));
		return out;
	}

	@GameTest(template = EMPTY)
	public static void theGeigerReadoutIsClassifiedAndSludged(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			Player p = survivor(helper);
			p.setPos(10000, 100, 10000); // a chunk nothing has irradiated
			HbmLivingProps.setRadiation(p, 250F);
			HbmLivingProps.setRadBuf(p, 10F);

			LeafiaFeatures.radiationClassification = false;
			LeafiaConfig.bullshitUnits = false;
			List<String> plain = readout(p);
			helper.assertTrue(plain.subList(1, 6).equals(List.of(" 0.000 RAD/s", " 10.000 RAD/s", " 10.000 RAD/s", " 250.000 RAD", " 0.000000% (0.00)")), "plain " + plain);

			LeafiaFeatures.radiationClassification = true;
			List<String> sv = readout(p);
			helper.assertTrue(sv.subList(1, 6).equals(List.of(" 0.000 RAD/s", " 0.100 Sv/s", " 0.100 Sv/s", " 2.500 Sv", " 0.000000% (0.00)")), "classified " + sv);

			LeafiaConfig.bullshitUnits = true;
			List<String> both = readout(p);
			helper.assertTrue(both.subList(1, 6).equals(List.of(" 0.000 expie/stevejob", " 0.617 dyatlov/stevejob", " 0.617 dyatlov/stevejob", " 0.417 dyatlov", " 0.000000% (0.00)")), "both " + both);

			LeafiaFeatures.radiationClassification = false;
			List<String> sludge = readout(p);
			helper.assertTrue(sludge.subList(1, 6).equals(List.of(" 0.000 expie/stevejob", " 4.317 expie/stevejob", " 4.317 expie/stevejob", " 2.917 expie", " 0.000000% (0.00)")), "sludge " + sludge);

			helper.assertTrue(LeafiaHazardSeams.format(2.0e6, 2.0e6, 3, 1e-3).equals("2.000e+06") && LeafiaHazardSeams.format(5e-4, 5e-4, 3, 1e-3).equals("5.000e-04")
					&& LeafiaHazardSeams.format(0, 0, 3, 1e-3).equals("0.000"), "scientific notation outside the readable range");

			LeafiaConfig.bullshitUnits = false;
			helper.assertTrue(!LeafiaHazardSeams.GEIGER_PRINTER.print(p), "neither on: the base mod prints");
			LeafiaFeatures.radiationClassification = true;
			helper.assertTrue(LeafiaHazardSeams.GEIGER_PRINTER.print(p), "classification on: the addon prints");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRadiationTooltipIsClassified(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			HazardSystem.itemMap.put(Items.STICK, new HazardData().addEntry(Alpha.INSTANCE, 100F).addEntry(Gamma.INSTANCE, 50F).addEntry(Radon.INSTANCE, 4F));
			Player p = survivor(helper);
			LeafiaFeatures.radiationClassification = false;
			LeafiaConfig.bullshitUnits = false;
			List<Component> list = new ArrayList<>();
			LeafiaHazardSeams.RADIATION_TOOLTIP.head(new ItemStack(Items.STICK), p, list);
			helper.assertTrue(list.isEmpty(), "off: the head adds nothing");
			helper.assertTrue(!LeafiaHazardSeams.RADIATION_TOOLTIP.replaceEntry(new HazardEntry(Alpha.INSTANCE, 1F), ItemStack.EMPTY, p, list), "and replaces nothing");

			LeafiaFeatures.radiationClassification = true;
			LeafiaHazardSeams.RADIATION_TOOLTIP.head(new ItemStack(Items.STICK, 2), p, list);
			List<String> text = flat(list);
			helper.assertTrue(text.equals(List.of("[trait.radioactive]", " -::trait._hazarditem.radioactive.alpha 100.0 RAD",
					" -::trait._hazarditem.radioactive.gamma 50.0 RAD", " -::trait._hazarditem.stack 308.0 RAD", " -::trait._hazarditem.radioactive.radon 4.0 RAD")), "classified " + text);
			helper.assertTrue(LeafiaHazardSeams.RADIATION_TOOLTIP.replaceEntry(new HazardEntry(Alpha.INSTANCE, 1F), ItemStack.EMPTY, p, list)
					&& LeafiaHazardSeams.RADIATION_TOOLTIP.replaceEntry(new HazardEntry(HazardRegistry.RADIATION, 1F), ItemStack.EMPTY, p, list), "the plain lines are suppressed");
			helper.assertTrue(!LeafiaHazardSeams.RADIATION_TOOLTIP.replaceEntry(new HazardEntry(HazardRegistry.HOT, 1F), ItemStack.EMPTY, p, list), "the others are not");

			list.clear();
			LeafiaConfig.bullshitUnits = true;
			LeafiaHazardSeams.RADIATION_TOOLTIP.head(new ItemStack(Items.STICK), p, list);
			text = flat(list);
			helper.assertTrue(text.get(1).equals(" -::trait._hazarditem.radioactive.alpha 1.167 expie"), "sludge units: " + text);

			list.clear();
			LeafiaHazardSeams.RADIATION_TOOLTIP.head(new ItemStack(Items.DIRT), p, list);
			helper.assertTrue(list.isEmpty(), "a stack with no radiation gets no header");
			helper.assertTrue(LeafiaHazardSeams.value(2.5e6).equals("2.5M") && LeafiaHazardSeams.value(3.0e9).equals("3.0B") && LeafiaHazardSeams.value(999999.5).equals("999999.5"), "millions and billions");
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void craterDoseStopsUnderground(GameTestHelper helper) {
		Restore r = new Restore();
		try {
			LeafiaFeatures.hazardOverhaul = true;
			Player p = survivor(helper);
			Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
			p.setPos(at.x, at.y, at.z);
			helper.assertTrue(!RadCorrection.isUnderground(p), "in the open");
			near(helper, LeafiaHazardSeams.CRATER_UNDERGROUND.radiation(p, null, 5F), 5, "the open takes the dose");

			helper.setBlock(new BlockPos(2, 10, 2), Blocks.STONE);
			helper.assertTrue(RadCorrection.isUnderground(p), "nine blocks under a roof");
			near(helper, LeafiaHazardSeams.CRATER_UNDERGROUND.radiation(p, null, 5F), 0, "takes none");
			LeafiaFeatures.hazardOverhaul = false;
			near(helper, LeafiaHazardSeams.CRATER_UNDERGROUND.radiation(p, null, 5F), 5, "switch off: the base mod's dose");
			helper.setBlock(new BlockPos(2, 10, 2), Blocks.AIR);
		} finally {
			r.restore();
		}
		helper.succeed();
	}

	/** MixinBlockHazard dissolves into the base mod: a radiating block keeps rescheduling itself. */
	@GameTest(template = EMPTY, timeoutTicks = com.hbm.gametest.TickWindow.TIMEOUT)
	public static void aRadiatingBlockKeepsRescheduling(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, ModBlocks.block_uranium.get());
		BlockPos abs = helper.absolutePos(pos);
		com.hbm.gametest.TickWindow.runAfterDelay(helper, 30, () -> {
			BlockHazard block = ModBlocks.block_uranium.get();
			helper.assertTrue(block.getRad() > 0, "the block read its radiation on placement");
			helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(abs, block), "twenty ticks in, it has already ticked once and has the next scheduled");
			helper.setBlock(pos, Blocks.AIR);
			helper.succeed();
		});
	}

	/** A spec's default for the key, wherever in the file it sits. The static starts at the default but the loaded file overwrites it, so it proves nothing. */
	public static Object defaultOf(com.electronwill.nightconfig.core.UnmodifiableConfig config, String key) {
		for(var entry : config.valueMap().entrySet()) {
			Object v = entry.getValue();
			if(v instanceof net.neoforged.neoforge.common.ModConfigSpec.ValueSpec spec) {
				if(entry.getKey().equals(key)) return spec.getDefault();
			} else if(v instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub) {
				Object found = defaultOf(sub, key);
				if(found != null) return found;
			}
		}
		return null;
	}

	/** Both switches change base behaviour, so D-1002 has them off; the scaffold test pins the same for classification. */
	@GameTest(template = EMPTY)
	public static void theHazardSwitchesAreOffByDefault(GameTestHelper helper) {
		helper.assertTrue(Boolean.FALSE.equals(defaultOf(LeafiaFeatures.SPEC.getSpec(), "hazardOverhaul")), "hazardOverhaul is an overhaul and defaults off");
		helper.assertTrue(Boolean.FALSE.equals(defaultOf(LeafiaFeatures.SPEC.getSpec(), "cosmeticOverhaul")), "cosmeticOverhaul is an overhaul and defaults off");
		helper.assertTrue(Boolean.FALSE.equals(defaultOf(LeafiaConfig.SPEC.getSpec(), "disableToxic")) && Boolean.TRUE.equals(defaultOf(LeafiaConfig.SPEC.getSpec(), "enableRadon")), "toxic and radon are on, CE's defaults");
		helper.assertTrue(!LeafiaFeatures.hazardOverhaul && !LeafiaFeatures.cosmeticOverhaul, "and a plain run has them off");
		helper.succeed();
	}
}
