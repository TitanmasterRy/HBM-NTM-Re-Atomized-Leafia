package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.items.ModItems;
import com.hbm.items.tool.NtmTiers;
import com.hbm.lib.NtmTags;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.combat.shields.AddonShieldItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Events;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.LeafiaToolMaterials;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's six shields, their two ingots and their three tool materials (D-1083).
 *
 * <p>Everything that acts is driven through vanilla's own path: the attacker hits a player whose
 * shield is up with {@code Player.hurt}, so {@code LivingShieldBlockEvent} fires from
 * {@code LivingEntity.hurt} and nothing here calls a handler by hand. What is called directly is
 * what a test cannot wait for - the item's tick and the start-of-tick cooldown pass - which are
 * stepped by calling {@code inventoryTick} and {@code Events.coolDown} the number of times a
 * server would.
 *
 * <p>The players are {@code makeMockPlayer}, never {@code makeMockServerPlayerInLevel}: that one
 * joins the player list and the wind's next packet throws on it. A mock player is raised the way a
 * real one is, by {@code startUsingItem} and then ten ticks of use, which this has to write
 * ({@code useItemRemaining} is protected) because a mock is not in the level to be ticked.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaShieldTests {

	private static final String EMPTY = "empty";

	// ---- plumbing ----

	private static String text(String path) {
		try(InputStream in = LeafiaShieldTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(text(path)).getAsJsonObject();
	}

	private static BufferedImage png(String path) {
		try(InputStream in = LeafiaShieldTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return ImageIO.read(in);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String lang(String key) {
		JsonObject lang = json("lang/en_us.json");
		return lang.has(key) ? lang.get(key).getAsString() : null;
	}

	private static AddonShieldItem shield(Kind kind) {
		return LeafiaItems.shields.get(kind).get();
	}

	private static double[] numbers(JsonObject obj, String context, String key) {
		JsonArray a = obj.getAsJsonObject("display").getAsJsonObject(context).getAsJsonArray(key);
		double[] out = new double[a.size()];
		for(int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
		return out;
	}

	/** A mock player standing at the template's middle, facing +Z (yaw 0), with {@code kind} raised in {@code hand}. */
	private static Player raised(GameTestHelper helper, Kind kind, InteractionHand hand) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		player.setPos(at);
		player.setYRot(0F);
		player.setYHeadRot(0F);
		ItemStack stack = new ItemStack(shield(kind));
		player.setItemInHand(hand, stack);
		player.startUsingItem(hand);
		try {
			Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining");
			remaining.setAccessible(true);
			remaining.setInt(player, stack.getUseDuration(player) - 10);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return player;
	}

	/** A pig {@code dz} blocks along +Z of the player, which is in front of one facing yaw 0. */
	private static Pig pigAt(GameTestHelper helper, Player player, double dx, double dz) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		pig.setNoAi(true);
		pig.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
		helper.getLevel().addFreshEntity(pig);
		return pig;
	}

	private static DamageSource bite(GameTestHelper helper, Entity attacker) {
		return helper.getLevel().damageSources().mobAttack((LivingEntity) attacker);
	}

	private static void assertNormal(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getDifficulty() == Difficulty.NORMAL,
				"a mob's bite is scaled by difficulty and these numbers are NORMAL's, got " + helper.getLevel().getDifficulty());
	}

	// ---- the items ----

	/** Six shields, stack size 1, each with the durability its material says, in the addon's tab. */
	@GameTest(template = EMPTY)
	public static void theSixShieldsAreRegisteredWithTheirDurability(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.shields, "features.shields is additive and on by default");
		helper.assertTrue(LeafiaItems.shields.size() == 6, "six shields, got " + LeafiaItems.shields.size());

		Map<Kind, Integer> expected = Map.of(
				Kind.STEEL, 750, Kind.TITANIUM, 1000, Kind.ELEC, 450,
				Kind.PU238, 300, Kind.FISSITE, 2500, Kind.MYSTICITE, 0);
		List<Item> inTab = new ArrayList<>();
		LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).forEach(s -> inTab.add(s.get().asItem()));

		for(Kind kind : Kind.values()) {
			ItemStack stack = new ItemStack(shield(kind));
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			helper.assertTrue(id.equals("leafia:" + kind.id), kind + " is registered as " + id);
			helper.assertTrue(stack.getMaxStackSize() == 1, kind.id + " stacks to " + stack.getMaxStackSize());
			int uses = expected.get(kind);
			if(uses == 0) {
				helper.assertTrue(!stack.isDamageableItem() && stack.getMaxDamage() == 0
						&& !stack.has(DataComponents.MAX_DAMAGE), "mysticite never wears out");
			} else {
				helper.assertTrue(stack.isDamageableItem() && stack.getMaxDamage() == uses,
						kind.id + " has durability " + stack.getMaxDamage() + ", expected " + uses);
			}
			helper.assertTrue(inTab.contains(stack.getItem()), kind.id + " is not in the creative tab");
			helper.assertTrue(stack.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK, kind.id + " does not raise like a shield");
		}
		helper.succeed();
	}

	/**
	 * Upstream's steel and titanium are NTM-CE's 500 and 750; this port's base mod has 750 and 1,000
	 * (D-1000), and the shields read the base mod's tiers rather than a copy of either.
	 */
	@GameTest(template = EMPTY)
	public static void steelAndTitaniumReadTheBaseModsTiers(GameTestHelper helper) {
		helper.assertTrue(new ItemStack(shield(Kind.STEEL)).getMaxDamage() == NtmTiers.STEEL.getUses(), "steel");
		helper.assertTrue(new ItemStack(shield(Kind.TITANIUM)).getMaxDamage() == NtmTiers.TITANIUM.getUses(), "titanium");
		helper.assertTrue(NtmTiers.STEEL.getUses() == 750 && NtmTiers.TITANIUM.getUses() == 1000,
				"the base mod's HBM_STEEL and HBM_TITANIUM are 750 and 1000: " + NtmTiers.STEEL.getUses() + ", " + NtmTiers.TITANIUM.getUses());
		helper.succeed();
	}

	/** {@code AddonToolMaterials}' fifteen numbers, and the two repair items. */
	@GameTest(template = EMPTY)
	public static void theToolMaterialsAreUpstreams(GameTestHelper helper) {

		Object[][] table = {
				{ "mysticite", LeafiaToolMaterials.MYSTICITE, 0, 15F, 5F, 20 },
				{ "fissite", LeafiaToolMaterials.FISSITE, 2500, 8F, 3F, 10 },
				{ "pu238", LeafiaToolMaterials.PU238, 300, 6F, 2F, 5 },
		};
		for(Object[] row : table) {
			Tier tier = (Tier) row[1];
			String n = (String) row[0];
			helper.assertTrue(tier.getUses() == (int) row[2], n + " uses " + tier.getUses());
			helper.assertTrue(tier.getSpeed() == (float) row[3], n + " speed " + tier.getSpeed());
			helper.assertTrue(tier.getAttackDamageBonus() == (float) row[4], n + " damage " + tier.getAttackDamageBonus());
			helper.assertTrue(tier.getEnchantmentValue() == (int) row[5], n + " enchantability " + tier.getEnchantmentValue());
		}
		helper.assertTrue(LeafiaToolMaterials.MYSTICITE.getIncorrectBlocksForDrops() == net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
				"mysticite is harvest level 5, past diamond");
		helper.assertTrue(LeafiaToolMaterials.FISSITE.getIncorrectBlocksForDrops() == net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL
				&& LeafiaToolMaterials.PU238.getIncorrectBlocksForDrops() == net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
				"fissite and plutonium-238 are harvest level 3");

		helper.assertTrue(LeafiaToolMaterials.FISSITE.getRepairIngredient().test(new ItemStack(LeafiaItems.ingot_fissite.get())),
				"fissite mends with the fissite ingot");
		helper.assertTrue(LeafiaToolMaterials.PU238.getRepairIngredient().test(new ItemStack(ModItems.ingot_pu238.get())),
				"plutonium-238 mends with the plutonium-238 ingot");
		helper.assertTrue(LeafiaToolMaterials.MYSTICITE.getRepairIngredient().isEmpty(), "mysticite has no repair item - upstream's \"you can't though\"");
		helper.succeed();
	}

	/** Each shield mends with its own ore-dictionary ingot, and with planks as an {@code ItemShield} does. */
	@GameTest(template = EMPTY)
	public static void eachShieldMendsWithItsIngotAndPlanks(GameTestHelper helper) {

		Map<Kind, Item> ingot = Map.of(
				Kind.STEEL, ModItems.ingot_steel.get(), Kind.TITANIUM, ModItems.ingot_titanium.get(),
				Kind.ELEC, ModItems.ingot_polymer.get(), Kind.PU238, ModItems.ingot_pu238.get(),
				Kind.FISSITE, LeafiaItems.ingot_fissite.get(), Kind.MYSTICITE, LeafiaItems.ingot_mysticite.get());

		for(Kind kind : Kind.values()) {
			ItemStack broken = new ItemStack(shield(kind));
			for(Map.Entry<Kind, Item> e : ingot.entrySet()) {
				boolean mends = shield(kind).isValidRepairItem(broken, new ItemStack(e.getValue()));
				helper.assertTrue(mends == (e.getKey() == kind),
						kind.id + (mends ? " is mended by " : " is not mended by ") + e.getKey() + "'s ingot, expected the opposite");
			}
			helper.assertTrue(shield(kind).isValidRepairItem(broken, new ItemStack(Items.OAK_PLANKS)), kind.id + " takes planks");
			helper.assertTrue(!shield(kind).isValidRepairItem(broken, new ItemStack(Items.IRON_INGOT)), kind.id + " takes iron");
		}

		helper.assertTrue(NtmTags.ore("ingotFissite").location().toString().equals("c:ingots/fissite"), "fissite's tag");
		helper.assertTrue(NtmTags.ore("ingotMysticite").location().toString().equals("c:ingots/mysticite"), "mysticite's tag");
		helper.succeed();
	}

	/** The two ingots exist, are in the tab, and carry the tags the shields name. */
	@GameTest(template = EMPTY)
	public static void theTwoIngotsAreRegisteredAndTagged(GameTestHelper helper) {

		List<Item> inTab = new ArrayList<>();
		LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).forEach(s -> inTab.add(s.get().asItem()));
		for(Item ingot : List.of(LeafiaItems.ingot_fissite.get(), LeafiaItems.ingot_mysticite.get())) {
			helper.assertTrue(inTab.contains(ingot), ingot + " is not in the tab");
			helper.assertTrue(new ItemStack(ingot).getMaxStackSize() == 64, "an ingot stacks to 64");
		}
		helper.assertTrue(new ItemStack(LeafiaItems.ingot_fissite.get()).is(NtmTags.ore("ingotFissite")), "ingot_fissite is c:ingots/fissite");
		helper.assertTrue(new ItemStack(LeafiaItems.ingot_mysticite.get()).is(NtmTags.ore("ingotMysticite")), "ingot_mysticite is c:ingots/mysticite");

		// The mysticite ingot's tooltip is upstream's six lines, the blank ones included.
		List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
		LeafiaItems.ingot_mysticite.get().appendHoverText(new ItemStack(LeafiaItems.ingot_mysticite.get()),
				Item.TooltipContext.EMPTY, tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
		List<String> lines = new ArrayList<>();
		tooltip.forEach(c -> lines.add(c.getString()));
		helper.assertTrue(lines.equals(List.of("The ultimate alloy.", "", "You cannot shape it due to it's",
				"extreme durability and heat resistance.", "", "Congratulations, you just wasted it.")), "tooltip " + lines);
		helper.succeed();
	}

	// ---- the recipes ----

	private static ItemStack craft(GameTestHelper helper, int w, int h, Item... cells) {
		List<ItemStack> grid = new ArrayList<>();
		for(Item cell : cells) grid.add(cell == null ? ItemStack.EMPTY : new ItemStack(cell));
		CraftingInput input = CraftingInput.of(w, h, grid);
		var level = helper.getLevel();
		var found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return found.map(RecipeHolder::value).map(r -> r.assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/** AddonCraftingRecipes lines 188 to 192, each through the real recipe manager. */
	@GameTest(template = EMPTY)
	public static void theFiveShieldRecipesCraft(GameTestHelper helper) {

		Item iron = Items.IRON_INGOT;
		Item steel = ModItems.ingot_steel.get();
		Item titanium = ModItems.ingot_titanium.get();
		Item polymer = ModItems.ingot_polymer.get();
		Item pane = Items.GLASS_PANE;
		Item plate = ModItems.plate_polymer.get();
		Item pu238 = ModItems.ingot_pu238.get();
		Item hardPlastic = ModItems.ingot_pc.get();
		Item fissite = LeafiaItems.ingot_fissite.get();

		helper.assertTrue(craft(helper, 3, 3, steel, iron, steel, steel, steel, steel, null, steel, null).is(shield(Kind.STEEL)), "steel");
		helper.assertTrue(craft(helper, 3, 3, titanium, iron, titanium, titanium, titanium, titanium, null, titanium, null).is(shield(Kind.TITANIUM)), "titanium");
		helper.assertTrue(craft(helper, 3, 3, polymer, pane, polymer, polymer, pane, polymer, polymer, pane, polymer).is(shield(Kind.ELEC)), "SWAT");
		helper.assertTrue(craft(helper, 3, 3, plate, pu238, plate, polymer, plate, polymer, null, polymer, null).is(shield(Kind.PU238)), "plutonium-238");
		helper.assertTrue(craft(helper, 3, 3, hardPlastic, fissite, hardPlastic, hardPlastic, hardPlastic, hardPlastic, null, hardPlastic, null).is(shield(Kind.FISSITE)), "fissite");

		// the wrong metal in the same shape is a different recipe, or none
		helper.assertTrue(craft(helper, 3, 3, titanium, iron, titanium, titanium, titanium, titanium, null, titanium, null).is(shield(Kind.STEEL)) == false,
				"titanium must not craft the steel shield");
		helper.assertTrue(craft(helper, 3, 3, steel, iron, steel, steel, steel, steel, null, steel, null).is(shield(Kind.TITANIUM)) == false,
				"steel must not craft the titanium shield");
		helper.assertTrue(craft(helper, 3, 3, steel, steel, steel, steel, steel, steel, null, steel, null).isEmpty(),
				"the iron ingot in the middle of the top row is part of the recipe");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(shield(Kind.MYSTICITE)).getPath().equals("mysticite_shield"), "mysticite");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "mysticite_shield")).isEmpty(),
				"the mysticite shield has no crafting recipe upstream (a solar blaster makes it from the fissite one)");
		helper.succeed();
	}

	// ---- what a raised shield does to whatever hits it ----

	/**
	 * The plutonium-238 shield, through {@code Player.hurt}: the attacker is set alight for five
	 * seconds and takes 10 radiation, the player takes nothing, and the shield wears by vanilla's
	 * {@code 1 + floor(damage)}.
	 */
	@GameTest(template = EMPTY)
	public static void aPlutoniumShieldBurnsAndIrradiatesWhoeverHitsIt(GameTestHelper helper) {

		assertNormal(helper);
		Player player = raised(helper, Kind.PU238, InteractionHand.OFF_HAND);
		Pig pig = pigAt(helper, player, 0, 2);
		helper.assertTrue(pig.getRemainingFireTicks() <= 0, "precondition: the pig is not burning");

		player.hurt(bite(helper, pig), 4F);

		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the shield took the hit, health " + player.getHealth());
		helper.assertTrue(pig.getRemainingFireTicks() == 5 * 20, "five seconds alight, got " + pig.getRemainingFireTicks() + " ticks");
		helper.assertTrue(HbmLivingProps.getRadEnv(pig) == 10F, "10 radiation, got " + HbmLivingProps.getRadEnv(pig));
		helper.assertTrue(player.getOffhandItem().getDamageValue() == 5, "a 4 damage hit wears 1 + 4, got " + player.getOffhandItem().getDamageValue());
		pig.discard();
		helper.succeed();
	}

	/** The fissite shield irradiates for 70 and sets nothing alight; the main hand works as well as the off hand. */
	@GameTest(template = EMPTY)
	public static void aFissiteShieldIrradiatesButDoesNotBurn(GameTestHelper helper) {

		assertNormal(helper);
		Player player = raised(helper, Kind.FISSITE, InteractionHand.MAIN_HAND);
		Pig pig = pigAt(helper, player, 0, 2);

		player.hurt(bite(helper, pig), 4F);

		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "blocked, health " + player.getHealth());
		helper.assertTrue(HbmLivingProps.getRadEnv(pig) == 70F, "70 radiation, got " + HbmLivingProps.getRadEnv(pig));
		helper.assertTrue(pig.getRemainingFireTicks() <= 0, "fissite does not burn, got " + pig.getRemainingFireTicks());
		pig.discard();
		helper.succeed();
	}

	/** The other four do nothing to the attacker - and mysticite's 4 damage hit wears nothing. */
	@GameTest(template = EMPTY)
	public static void theOtherShieldsLeaveTheAttackerAlone(GameTestHelper helper) {

		assertNormal(helper);
		for(Kind kind : new Kind[] {Kind.STEEL, Kind.TITANIUM, Kind.ELEC, Kind.MYSTICITE}) {
			Player player = raised(helper, kind, InteractionHand.OFF_HAND);
			Pig pig = pigAt(helper, player, 0, 2);
			player.hurt(bite(helper, pig), 4F);
			helper.assertTrue(player.getHealth() == player.getMaxHealth(), kind.id + " blocked, health " + player.getHealth());
			helper.assertTrue(pig.getRemainingFireTicks() <= 0, kind.id + " set the pig alight");
			helper.assertTrue(HbmLivingProps.getRadEnv(pig) == 0F, kind.id + " irradiated the pig: " + HbmLivingProps.getRadEnv(pig));
			int wear = player.getOffhandItem().getDamageValue();
			helper.assertTrue(wear == (kind == Kind.MYSTICITE ? 0 : 5), kind.id + " wore " + wear);
			pig.discard();
		}
		helper.succeed();
	}

	/** A shield that is not raised stops nothing and does nothing; and neither does one raised against a hit from behind. */
	@GameTest(template = EMPTY)
	public static void onlyAShieldRaisedAtTheAttackerActs(GameTestHelper helper) {

		assertNormal(helper);

		// held but not raised
		Player lowered = helper.makeMockPlayer(GameType.SURVIVAL);
		lowered.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		lowered.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(shield(Kind.PU238)));
		Pig first = pigAt(helper, lowered, 0, 2);
		lowered.hurt(bite(helper, first), 4F);
		helper.assertTrue(lowered.getHealth() < lowered.getMaxHealth(), "a lowered shield stopped the hit");
		helper.assertTrue(first.getRemainingFireTicks() <= 0 && HbmLivingProps.getRadEnv(first) == 0F, "a lowered shield burnt or irradiated");

		// raised, hit from behind
		Player facing = raised(helper, Kind.PU238, InteractionHand.OFF_HAND);
		Pig behind = pigAt(helper, facing, 0, -2);
		facing.hurt(bite(helper, behind), 4F);
		helper.assertTrue(facing.getHealth() < facing.getMaxHealth(), "a hit from behind was blocked");
		helper.assertTrue(behind.getRemainingFireTicks() <= 0 && HbmLivingProps.getRadEnv(behind) == 0F, "a hit from behind burnt or irradiated");

		// raised, damage that bypasses shields
		Player bypass = raised(helper, Kind.FISSITE, InteractionHand.OFF_HAND);
		Pig third = pigAt(helper, bypass, 0, 2);
		bypass.hurt(helper.getLevel().damageSources().indirectMagic(third, third), 4F);
		helper.assertTrue(HbmLivingProps.getRadEnv(third) == 0F, "magic goes through a shield, and so does its irradiation");
		first.discard();
		behind.discard();
		third.discard();
		helper.succeed();
	}

	/**
	 * The bullet seam, run with an arrow standing in for a bullet: a bullet-blocking shield books
	 * {@code (int) sqrt(damage)} of wear instead of vanilla's {@code 1 + damage} and stops the hit;
	 * the plutonium shield does not block bullets; an exempt class is not a bullet. Every list this
	 * mutates is put back.
	 */
	@GameTest(template = EMPTY)
	public static void aBulletBlockingShieldBooksSqrtOfTheDamage(GameTestHelper helper) {

		var seam = new java.util.function.Predicate<Entity>() {
			@Override public boolean test(Entity e) { return e instanceof Arrow; }
		};
		helper.assertTrue(AddonShieldItem.BULLET_TESTS.size() == 1 && AddonShieldItem.UNBLOCKABLE.isEmpty(),
				"precondition: the seam starts with only the base mod's IBulletBase and no exemption");
		try {
			AddonShieldItem.BULLET_TESTS.add(seam);

			Player swat = raised(helper, Kind.ELEC, InteractionHand.OFF_HAND);
			Arrow arrow = EntityType.ARROW.create(helper.getLevel());
			arrow.setPos(swat.getX(), swat.getY() + 1, swat.getZ() + 2);
			swat.hurt(helper.getLevel().damageSources().arrow(arrow, null), 16F);
			helper.assertTrue(swat.getHealth() == swat.getMaxHealth(), "the SWAT shield stopped the bullet, health " + swat.getHealth());
			helper.assertTrue(swat.getOffhandItem().getDamageValue() == 4, "sqrt(16) = 4 of wear, vanilla would have said 17, got "
					+ swat.getOffhandItem().getDamageValue());

			Player mystic = raised(helper, Kind.MYSTICITE, InteractionHand.MAIN_HAND);
			arrow.setPos(mystic.getX(), mystic.getY() + 1, mystic.getZ() + 2);
			mystic.hurt(helper.getLevel().damageSources().arrow(arrow, null), 16F);
			helper.assertTrue(mystic.getHealth() == mystic.getMaxHealth(), "mysticite stopped it too");
			helper.assertTrue(mystic.getMainHandItem().getDamageValue() == 0, "and wore nothing");

			Player pu = raised(helper, Kind.PU238, InteractionHand.OFF_HAND);
			arrow.setPos(pu.getX(), pu.getY() + 1, pu.getZ() + 2);
			pu.hurt(helper.getLevel().damageSources().arrow(arrow, null), 16F);
			helper.assertTrue(pu.getOffhandItem().getDamageValue() == 17, "the plutonium shield does not block bullets: vanilla's 1 + 16, got "
					+ pu.getOffhandItem().getDamageValue());
			helper.assertTrue(arrow.getRemainingFireTicks() == 5 * 20, "and it sets the arrow alight, as upstream does to any attacker");

			AddonShieldItem.UNBLOCKABLE.add(Arrow.class);
			helper.assertTrue(!AddonShieldItem.isBullet(arrow), "an exempt class is not a bullet - upstream's AMRifleBeam");
			Player exempt = raised(helper, Kind.ELEC, InteractionHand.OFF_HAND);
			arrow.setPos(exempt.getX(), exempt.getY() + 1, exempt.getZ() + 2);
			exempt.hurt(helper.getLevel().damageSources().arrow(arrow, null), 16F);
			helper.assertTrue(exempt.getOffhandItem().getDamageValue() == 17, "an exempt attacker wears the shield the vanilla way, got "
					+ exempt.getOffhandItem().getDamageValue());
		} finally {
			AddonShieldItem.BULLET_TESTS.remove(seam);
			AddonShieldItem.UNBLOCKABLE.clear();
		}
		helper.assertTrue(!AddonShieldItem.isBullet(EntityType.ARROW.create(helper.getLevel())), "and an arrow is not a bullet again");
		helper.assertTrue(!(shield(Kind.STEEL).canBlockBullets() || shield(Kind.TITANIUM).canBlockBullets()
				|| shield(Kind.PU238).canBlockBullets() || shield(Kind.FISSITE).canBlockBullets())
				&& shield(Kind.ELEC).canBlockBullets() && shield(Kind.MYSTICITE).canBlockBullets(), "SWAT and mysticite are the bullet blockers");
		helper.assertTrue(shield(Kind.ELEC).calBulletDamage(16F) == 4.0 && shield(Kind.ELEC).calBulletDamage(2F) == Math.sqrt(2),
				"calBulletDamage is a square root");
		helper.succeed();
	}

	// ---- the charge ----

	private static void tick(Player player, AddonShieldItem item, ItemStack stack, int times) {
		for(int i = 0; i < times; i++) stack.inventoryTick(player.level(), player, 0, true);
	}

	/** Twenty ticks of sneak-blocking: nineteen count, the twentieth throws, and the item goes on cooldown. */
	@GameTest(template = EMPTY)
	public static void twentyTicksOfSneakBlockingFireTheCharge(GameTestHelper helper) {

		for(Kind kind : new Kind[] {Kind.FISSITE, Kind.MYSTICITE}) {
			Player player = raised(helper, kind, InteractionHand.OFF_HAND);
			player.setShiftKeyDown(true);
			AddonShieldItem item = shield(kind);
			ItemStack stack = player.getOffhandItem();
			UUID id = player.getUUID();

			tick(player, item, stack, 19);
			helper.assertTrue(AddonShieldItem.chargePrepare.getOrDefault(id, 0) == 19, kind.id + " counted " + AddonShieldItem.chargePrepare.get(id));
			helper.assertTrue(!AddonShieldItem.chargeCooldown.containsKey(id) && !player.getCooldowns().isOnCooldown(item), "nothing has fired yet");

			tick(player, item, stack, 1);
			helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id), "the count is cleared by the throw");
			helper.assertTrue(AddonShieldItem.chargeCooldown.getOrDefault(id, 0) == 60, "60 ticks of cooldown, got " + AddonShieldItem.chargeCooldown.get(id));
			helper.assertTrue(player.getCooldowns().isOnCooldown(item), "the item is on cooldown too");

			// while it runs the count does not move, however long the player keeps sneaking
			tick(player, item, stack, 30);
			helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id) && AddonShieldItem.chargeCooldown.get(id) == 60,
					"a cooldown in force blocks the count");
			AddonShieldItem.chargeCooldown.remove(id);
		}
		helper.succeed();
	}

	/** Stop sneaking or lower the shield and the count is gone; the other four shields never count. */
	@GameTest(template = EMPTY)
	public static void aBrokenChargeStartsOverAndTheOtherShieldsNeverCharge(GameTestHelper helper) {

		Player player = raised(helper, Kind.FISSITE, InteractionHand.MAIN_HAND);
		AddonShieldItem item = shield(Kind.FISSITE);
		ItemStack stack = player.getMainHandItem();
		UUID id = player.getUUID();

		player.setShiftKeyDown(true);
		tick(player, item, stack, 12);
		helper.assertTrue(AddonShieldItem.chargePrepare.get(id) == 12, "twelve ticks in");
		player.setShiftKeyDown(false);
		tick(player, item, stack, 1);
		helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id), "standing up resets it");

		player.setShiftKeyDown(true);
		tick(player, item, stack, 5);
		player.stopUsingItem();
		tick(player, item, stack, 1);
		helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id), "lowering the shield resets it");
		helper.assertTrue(!AddonShieldItem.chargeCooldown.containsKey(id), "and nothing fired");

		// a shield that is in the inventory but not in a hand does not count
		Player pocket = raised(helper, Kind.FISSITE, InteractionHand.MAIN_HAND);
		pocket.setShiftKeyDown(true);
		ItemStack spare = new ItemStack(item);
		spare.inventoryTick(pocket.level(), pocket, 5, false);
		helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(pocket.getUUID()), "a stack that is not held does not count");

		for(Kind kind : new Kind[] {Kind.STEEL, Kind.TITANIUM, Kind.ELEC, Kind.PU238}) {
			Player p = raised(helper, kind, InteractionHand.OFF_HAND);
			p.setShiftKeyDown(true);
			tick(p, shield(kind), p.getOffhandItem(), 40);
			helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(p.getUUID()) && !AddonShieldItem.chargeCooldown.containsKey(p.getUUID()),
					kind.id + " charged");
		}
		helper.succeed();
	}

	/**
	 * Upstream stepped each held stack, so two charging shields charged twice as fast; fixed in D-3075:
	 * ten ticks of two shields do not throw, twenty do.
	 */
	@GameTest(template = EMPTY)
	public static void twoShieldsInTwoHandsChargeAtTheSingleRate(GameTestHelper helper) {

		Player player = raised(helper, Kind.FISSITE, InteractionHand.OFF_HAND);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(shield(Kind.MYSTICITE)));
		player.setShiftKeyDown(true);
		UUID id = player.getUUID();
		for(int t = 0; t < 10; t++) {
			player.getMainHandItem().inventoryTick(player.level(), player, 0, true);
			player.getOffhandItem().inventoryTick(player.level(), player, 40, false);
		}
		helper.assertTrue(AddonShieldItem.chargeCooldown.getOrDefault(id, 0) == 0, "ten ticks of two held shields must not throw (D-3075), got "
				+ AddonShieldItem.chargeCooldown.get(id));
		for(int t = 0; t < 10; t++) {
			player.getMainHandItem().inventoryTick(player.level(), player, 0, true);
			player.getOffhandItem().inventoryTick(player.level(), player, 40, false);
		}
		helper.assertTrue(AddonShieldItem.chargeCooldown.getOrDefault(id, 0) == 60, "twenty ticks throw, got "
				+ AddonShieldItem.chargeCooldown.get(id));
		AddonShieldItem.chargeCooldown.remove(id);
		helper.succeed();
	}

	/** The throw's velocity: the look vector at pitch 0, times 1.75, and 0.4 up. */
	@GameTest(template = EMPTY)
	public static void theThrowIsTheLookVectorTimesOneSeventyFiveAndLifted(GameTestHelper helper) {

		Vec3 south = AddonShieldItem.dash(0F);
		helper.assertTrue(Math.abs(south.x) < 1e-6 && Math.abs(south.y - 0.4) < 1e-6 && Math.abs(south.z - 1.75) < 1e-6, "yaw 0 is " + south);
		Vec3 west = AddonShieldItem.dash(90F);
		helper.assertTrue(Math.abs(west.x + 1.75) < 1e-6 && Math.abs(west.y - 0.4) < 1e-6 && Math.abs(west.z) < 1e-6, "yaw 90 is " + west);
		for(float yaw : new float[] {-135F, -45F, 30F, 77.5F, 180F, 270F}) {
			Vec3 expected = Vec3.directionFromRotation(0F, yaw).scale(1.75).add(0, 0.4, 0);
			Vec3 got = AddonShieldItem.dash(yaw);
			helper.assertTrue(got.distanceTo(expected) < 1e-5, "yaw " + yaw + ": " + got + " vs vanilla's " + expected);
		}
		helper.assertTrue(AddonShieldItem.CHARGE_TICKS == 20 && AddonShieldItem.COOLDOWN_TICKS == 60 && AddonShieldItem.HIT_TICKS == 15
				&& AddonShieldItem.HIT_DAMAGE == 8F && AddonShieldItem.SLOWNESS_TICKS == 160, "the constants");
		helper.succeed();
	}

	/**
	 * The body-check, for the first fifteen ticks of the cooldown: 8 damage and eight seconds of
	 * slowness to whatever the box 0.35 around the player cuts through. A pig two blocks ahead,
	 * and one behind, are not hit. Upstream's box was a flat plane at {@code z + 0.35} and missed
	 * everything on the player's own side of it; the owner fixed that to {@code z - 0.35}
	 * (D-1093 #5, D-1094), and the pig overlapping from the -z side is hit now.
	 */
	@GameTest(template = EMPTY)
	public static void theBodyCheckHitsForFifteenTicksAndOnlyWithinTheBox(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		UUID id = player.getUUID();

		// a pig whose box (width 0.9) overlaps the player's 0.7-deep box: its centre within 0.8 of the player
		Pig hit = pigAt(helper, player, 0, 0.1);
		Pig ahead = pigAt(helper, player, 0, 2);
		Pig behind = pigAt(helper, player, 0, -2);
		Pig beside = pigAt(helper, player, 2, 0.1);
		// on the -z side: its own box ends at z - 0.05, short of upstream's plane at z + 0.35 but inside z - 0.35
		Pig close = pigAt(helper, player, 0, -0.5);
		for(Pig pig : new Pig[] {hit, ahead, behind, beside, close}) {
			pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			pig.setHealth(1000);
		}

		AddonShieldItem.chargeCooldown.put(id, 60);
		for(int pass = 1; pass <= 60; pass++) {
			for(Pig pig : new Pig[] {hit, ahead, behind, beside, close}) pig.invulnerableTime = 0;
			Events.coolDown(player);
			if(pass == 15) {
				helper.assertTrue(hit.getHealth() == 1000F - 15 * 8F, "15 passes of 8, got " + (1000F - hit.getHealth()));
				helper.assertTrue(AddonShieldItem.chargeCooldown.get(id) == 45, "45 left after 15 passes");
			}
		}
		helper.assertTrue(hit.getHealth() == 1000F - 15 * 8F, "and it stops at fifteen, got " + (1000F - hit.getHealth()));
		helper.assertTrue(!AddonShieldItem.chargeCooldown.containsKey(id), "the cooldown ran out and was removed");
		helper.assertTrue(hit.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && hit.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() > 0
				&& hit.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 0, "slowness I");
		helper.assertTrue(ahead.getHealth() == 1000F, "a pig two blocks ahead was hit");
		helper.assertTrue(behind.getHealth() == 1000F, "a pig behind was hit");
		helper.assertTrue(beside.getHealth() == 1000F, "a pig two blocks to the side was hit");
		helper.assertTrue(close.getHealth() == 1000F - 15 * 8F, "a pig on the -z side, inside z - 0.35, was not hit: the box's near corner is z - r (D-1094), not upstream's z + r twice; took "
				+ (1000F - close.getHealth()));
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the player hit themselves");
		for(Pig pig : new Pig[] {hit, ahead, behind, beside, close}) pig.discard();
		helper.succeed();
	}

	/** The slowness lasts eight seconds. */
	@GameTest(template = EMPTY)
	public static void theBodyChecksSlownessLastsEightSeconds(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		Pig pig = pigAt(helper, player, 0, 0.1);
		AddonShieldItem.chargeCooldown.put(player.getUUID(), 60);
		Events.coolDown(player);
		helper.assertTrue(pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == 160, "eight seconds, got "
				+ pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration());
		AddonShieldItem.chargeCooldown.remove(player.getUUID());
		pig.discard();
		helper.succeed();
	}

	/** Logging out and respawning both forget a charge: 1.12.2 built a new player object for each. */
	@GameTest(template = EMPTY)
	public static void aPlayerWhoLeavesOrDiesForgetsTheCharge(GameTestHelper helper) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		UUID id = player.getUUID();
		AddonShieldItem.chargePrepare.put(id, 7);
		AddonShieldItem.chargeCooldown.put(id, 33);
		Events.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
		helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id) && !AddonShieldItem.chargeCooldown.containsKey(id), "logout forgets");

		AddonShieldItem.chargePrepare.put(id, 7);
		AddonShieldItem.chargeCooldown.put(id, 33);
		Events.onRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
		helper.assertTrue(!AddonShieldItem.chargePrepare.containsKey(id) && !AddonShieldItem.chargeCooldown.containsKey(id), "respawn forgets");
		helper.succeed();
	}

	// ---- the assets ----

	/** Upstream's six names and descriptions, under the 1.21.1 prefix, and the two ingots'. */
	@GameTest(template = EMPTY)
	public static void theLangIsUpstreams(GameTestHelper helper) {

		String[][] expected = {
				{ "steel_shield", "Steel Shield", "Durable shield" },
				{ "titanium_shield", "Titanium Shield", "Very durable shield" },
				{ "elec_shield", "SWAT Shield", "Somewhat durable shield$Blocks bullets" },
				{ "pu238_shield", "Plutonium-238 Shield", "Sets attackers on fire and contaminates a little" },
				{ "fissite_shield", "Fissite Shield", "Irradiates attackers$Sneak while using the shield to charge forward" },
				{ "mysticite_shield", "Mysticite Shield", "Blocks bullets, infinite durability$Sneak while using the shield to charge forward" },
		};
		for(String[] row : expected) {
			helper.assertTrue(row[1].equals(lang("item.leafia." + row[0])), row[0] + " name: " + lang("item.leafia." + row[0]));
			helper.assertTrue(row[2].equals(lang("item.leafia." + row[0] + ".desc")), row[0] + " desc: " + lang("item.leafia." + row[0] + ".desc"));
		}
		helper.assertTrue("Fissite Ingot".equals(lang("item.leafia.ingot_fissite")), "fissite ingot");
		helper.assertTrue("Mysticite Ingot".equals(lang("item.leafia.ingot_mysticite")), "mysticite ingot");
		for(Kind kind : Kind.values())
			helper.assertTrue(lang(shield(kind).getDescriptionId()) != null, kind.id + "'s description id has no name: " + shield(kind).getDescriptionId());
		helper.succeed();
	}

	/** Four 64x64 skins where the renderer reads them, two OBJ skins, six particle crops and two ingot icons. */
	@GameTest(template = EMPTY)
	public static void theTexturesAreWhereTheirReadersLookAndSquare(GameTestHelper helper) {

		for(String id : new String[] {"steel_shield", "titanium_shield", "elec_shield", "pu238_shield"}) {
			BufferedImage skin = png("textures/entity/shield/" + id + ".png");
			helper.assertTrue(skin.getWidth() == 64 && skin.getHeight() == 64, id + " skin is " + skin.getWidth() + "x" + skin.getHeight() + ", ShieldModel is 64x64");
			helper.assertTrue(Kind.valueOf(id.replace("_shield", "").toUpperCase()).skin().getPath()
					.equals("textures/entity/shield/" + id + ".png"), "the renderer reads somewhere else");
		}
		for(String id : new String[] {"fissite_shield", "mysticite_shield"}) {
			BufferedImage skin = png("textures/item/" + id + "_skin.png");
			helper.assertTrue(skin.getWidth() == 64 && skin.getHeight() == 64, id + " OBJ skin " + skin.getWidth());
		}
		for(Kind kind : Kind.values()) {
			BufferedImage particle = png("textures/item/" + kind.id + "_particle.png");
			helper.assertTrue(particle.getWidth() == 12 && particle.getHeight() == 12, kind.id + " particle crop is " + particle.getWidth());
		}
		for(String id : new String[] {"ingot_fissite", "ingot_mysticite"}) {
			BufferedImage icon = png("textures/item/" + id + ".png");
			helper.assertTrue(icon.getWidth() == icon.getHeight() && icon.getWidth() >= 16, id + " icon " + icon.getWidth() + "x" + icon.getHeight());
		}
		helper.succeed();
	}

	/**
	 * The item models: every shield is {@code builtin/entity} or the OBJ, carries vanilla 1.21.1's
	 * display block (the one upstream copied, brought up to date) and a {@code blocking} override
	 * to a model with vanilla's blocking pose.
	 */
	@GameTest(template = EMPTY)
	public static void theItemModelsCarryVanillasShieldDisplay(GameTestHelper helper) {

		for(Kind kind : Kind.values()) {
			JsonObject model = json("models/item/" + kind.id + ".json");
			boolean obj = kind == Kind.FISSITE || kind == Kind.MYSTICITE;
			if(obj) {
				helper.assertTrue("neoforge:obj".equals(model.get("loader").getAsString())
						&& "leafia:models/blocks/shield_kms.obj".equals(model.get("model").getAsString())
						&& model.get("flip_v").getAsBoolean(), kind.id + " is the OBJ");
				helper.assertTrue(!model.has("parent"), kind.id + " must not parent builtin/entity, or NeoForge asks a renderer for it");
			} else {
				helper.assertTrue("builtin/entity".equals(model.get("parent").getAsString()), kind.id + " asks the renderer");
			}
			double[] fixed = numbers(model, "fixed", "translation");
			helper.assertTrue(fixed[0] == -4.5 && fixed[1] == 4.5 && fixed[2] == -5, kind.id + " fixed translation " + Arrays.toString(fixed));
			double[] ground = numbers(model, "ground", "translation");
			helper.assertTrue(ground[0] == 2 && ground[1] == 4 && ground[2] == 2, kind.id + " ground translation " + Arrays.toString(ground));
			double[] hand = numbers(model, "firstperson_righthand", "translation");
			helper.assertTrue(hand[0] == -10 && hand[1] == 2 && hand[2] == -10, kind.id + " first person " + Arrays.toString(hand));
			double[] third = numbers(model, "thirdperson_righthand", "translation");
			helper.assertTrue(third[0] == 10 && third[1] == 6 && third[2] == -4, kind.id + " third person " + Arrays.toString(third));
			helper.assertTrue("front".equals(model.get("gui_light").getAsString()), kind.id + " gui light");

			JsonObject override = model.getAsJsonArray("overrides").get(0).getAsJsonObject();
			helper.assertTrue(override.getAsJsonObject("predicate").get("blocking").getAsInt() == 1
					&& ("leafia:item/" + kind.id + "_blocking").equals(override.get("model").getAsString()), kind.id + " override");

			JsonObject blocking = json("models/item/" + kind.id + "_blocking.json");
			double[] pose = numbers(blocking, "thirdperson_righthand", "rotation");
			helper.assertTrue(pose[0] == 45 && pose[1] == 155 && pose[2] == 0, kind.id + " blocking pose " + Arrays.toString(pose));
			double[] fp = numbers(blocking, "firstperson_righthand", "translation");
			helper.assertTrue(fp[0] == -15 && fp[1] == 5 && fp[2] == -11, kind.id + " blocking first person " + Arrays.toString(fp));
		}
		helper.succeed();
	}

	/**
	 * {@code shield_kms.obj} is upstream's with {@code (x, y, z)} turned to {@code (x, -y, -z)}:
	 * the same counts, y spanning +-0.75 either way, and z, which was -0.1875 to 0.3125, now -0.3125
	 * to 0.1875. It carries a material, or NeoForge bakes nothing (D-1062).
	 */
	@GameTest(template = EMPTY)
	public static void theObjIsUpstreamsTurnedAboutX(GameTestHelper helper) {

		String obj = text("models/blocks/shield_kms.obj");
		int vertices = 0, normals = 0, faces = 0;
		double[] lo = {9, 9, 9}, hi = {-9, -9, -9};
		String first = null, firstNormal = null;
		for(String line : obj.split("\n")) {
			String[] p = line.trim().split("\\s+");
			if(p[0].equals("v")) {
				vertices++;
				if(first == null) first = line;
				for(int i = 0; i < 3; i++) {
					double v = Double.parseDouble(p[i + 1]);
					lo[i] = Math.min(lo[i], v);
					hi[i] = Math.max(hi[i], v);
				}
			} else if(p[0].equals("vn")) {
				normals++;
				if(firstNormal == null) firstNormal = line;
			} else if(p[0].equals("f")) faces++;
		}
		helper.assertTrue(vertices == 206 && normals == 21 && faces == 326, "counts " + vertices + "/" + normals + "/" + faces);
		helper.assertTrue(first.equals("v 0.062500 0.187500 0.062500"), "upstream's first vertex was (0.0625, -0.1875, -0.0625): " + first);
		helper.assertTrue(lo[0] == -0.5 && hi[0] == 0.5, "x " + lo[0] + " to " + hi[0]);
		helper.assertTrue(lo[1] == -0.75 && hi[1] == 0.75, "y " + lo[1] + " to " + hi[1]);
		helper.assertTrue(lo[2] == -0.3125 && hi[2] == 0.1875, "z " + lo[2] + " to " + hi[2] + ", upstream's was -0.1875 to 0.3125");
		helper.assertTrue(obj.contains("\nusemtl "), "no material: NeoForge would bake zero quads");
		helper.succeed();
	}

	/**
	 * The banner (D-3031): each shield names the blank a banner is laid over - upstream's {@code _blank} skin for steel,
	 * titanium and the SWAT shield, the shield's own skin for the other three - and every blank is a 64 x 64 file in the
	 * jar; fissite and mysticite send a decorated stack to the renderer through two {@code builtin/entity} overrides,
	 * the decorated-and-blocking one last (the last match wins).
	 */
	@GameTest(template = EMPTY)
	public static void aBannerHasABlankToLieOnAndTheKmsPairSendsItToTheRenderer(GameTestHelper helper) {
		for(Kind kind : Kind.values()) {
			String path = kind.blank().getPath();
			boolean own = kind == Kind.STEEL || kind == Kind.TITANIUM || kind == Kind.ELEC;
			String want = own ? "textures/entity/shield/" + kind.id + "_blank.png"
					: kind.charges() ? "textures/item/" + kind.id + "_skin.png" : kind.skin().getPath();
			helper.assertTrue(path.equals(want), kind.id + " lays a banner over " + path + ", upstream's is " + want);
			try(java.io.InputStream in = LeafiaShieldTests.class.getResourceAsStream("/assets/leafia/" + path)) {
				helper.assertTrue(in != null, kind.id + ": " + path + " is in the jar");
				java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
				helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, kind.id + "'s blank is 64 x 64, is " + img.getWidth() + " x " + img.getHeight());
			} catch(java.io.IOException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException(kind.id + ": " + e);
			}
			if(!kind.charges()) continue;
			JsonObject model = json("models/item/" + kind.id + ".json");
			var overrides = model.getAsJsonArray("overrides");
			helper.assertTrue(overrides.size() == 3, kind.id + " has three overrides: " + overrides);
			JsonObject decorated = overrides.get(1).getAsJsonObject(), both = overrides.get(2).getAsJsonObject();
			helper.assertTrue(decorated.getAsJsonObject("predicate").get("leafia:decorated").getAsInt() == 1
					&& decorated.getAsJsonObject("predicate").size() == 1
					&& ("leafia:item/" + kind.id + "_decorated").equals(decorated.get("model").getAsString()), kind.id + " decorated override");
			helper.assertTrue(both.getAsJsonObject("predicate").get("leafia:decorated").getAsInt() == 1 && both.getAsJsonObject("predicate").get("blocking").getAsInt() == 1
					&& ("leafia:item/" + kind.id + "_decorated_blocking").equals(both.get("model").getAsString()), kind.id + " decorated and blocking, last");
			for(String suffix : new String[] {"_decorated", "_decorated_blocking"})
				helper.assertTrue("builtin/entity".equals(json("models/item/" + kind.id + suffix + ".json").get("parent").getAsString()),
						kind.id + suffix + " asks the renderer");
		}
		helper.succeed();
	}
}
