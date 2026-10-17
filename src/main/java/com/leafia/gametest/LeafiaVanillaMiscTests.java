package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.leafia.client.LeafiaEasterEgg;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.init.LeafiaAdvancements;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaAsphaltSpeed;
import com.leafia.overwrite_contents.LeafiaDigammaRecord;
import com.leafia.overwrite_contents.LeafiaItemDrops;
import com.leafia.passive.rendering.TopRender;
import com.leafia.passive.rendering.TopRender.Highlight;
import com.leafia.transformer.LeafiaGeneralLocal;
import com.leafia.transformer.WorldServerLeafia;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The vanilla-misc batch (D-3026): who dropped an item and what became of it, the sparks off an ore, the title screen's splashes, the
 * digamma record, the asphalt's lost boost, the top-of-the-world drawing's geometry, the advancements, the goggles' loose ends.
 *
 * <p>Every switch a test changes is put back in a {@code finally}; every entity it spawns is discarded; the mock players are taken off
 * the player list afterwards, as the advisor's tests do.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaVanillaMiscTests {

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
	}

	private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	// ------------------------------------------------------------------ the item that was lost

	/**
	 * The thrower is recorded by the toss event, the pick-up by the pickup event, and a removal is a loss only when the entity was
	 * destroyed (not unloaded), still holds something, and nobody touched it. Only the advisor earns the advancement.
	 */
	@GameTest(template = EMPTY)
	public static void aDestroyedUntouchedAdvisorLosesTheAdvancement(GameTestHelper helper) {
		if(!LeafiaFeatures.advisor) { helper.succeed(); return; }
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(LeafiaRef.rl("loseadvisor"));
			helper.assertTrue(holder != null, "leafia:loseadvisor is not loaded");
			helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(), "the advancement starts done");

			// kept: the advisor was picked up, then destroyed
			ItemEntity kept = drop(helper, new ItemStack(LeafiaItems.advisor.get()));
			NeoForge.EVENT_BUS.post(new ItemTossEvent(kept, player));
			helper.assertTrue(LeafiaItemDrops.droppedBy(kept) == player, "the toss event did not record the thrower");
			NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, kept));
			helper.assertTrue(LeafiaItemDrops.wasPickedUp(kept), "the pickup event did not set the flag");
			kept.discard();
			helper.assertTrue(!LeafiaItemDrops.wasLost(kept), "a picked-up advisor counts as lost");
			LeafiaItemDrops.lose(kept);
			helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(), "a picked-up advisor earned the advancement");

			// unloaded: the chunk went away, the advisor did not
			ItemEntity asleep = drop(helper, new ItemStack(LeafiaItems.advisor.get()));
			NeoForge.EVENT_BUS.post(new ItemTossEvent(asleep, player));
			asleep.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			helper.assertTrue(!LeafiaItemDrops.wasLost(asleep), "an unloaded advisor counts as lost");

			// merged away: an emptied stack is not a loss
			ItemEntity merged = drop(helper, new ItemStack(LeafiaItems.advisor.get()));
			NeoForge.EVENT_BUS.post(new ItemTossEvent(merged, player));
			merged.setItem(ItemStack.EMPTY);
			merged.discard();
			helper.assertTrue(!LeafiaItemDrops.wasLost(merged), "an emptied stack counts as lost");

			// some other item destroyed: not the advisor
			ItemEntity other = drop(helper, new ItemStack(Items.STICK));
			NeoForge.EVENT_BUS.post(new ItemTossEvent(other, player));
			other.discard();
			helper.assertTrue(LeafiaItemDrops.wasLost(other), "a destroyed stick is not lost");
			LeafiaItemDrops.lose(other);
			helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(), "a stick earned the advancement");

			// lost: destroyed and untouched
			ItemEntity lost = drop(helper, new ItemStack(LeafiaItems.advisor.get()));
			NeoForge.EVENT_BUS.post(new ItemTossEvent(lost, player));
			lost.discard();
			helper.assertTrue(LeafiaItemDrops.wasLost(lost), "a destroyed untouched advisor is not lost");
			LeafiaItemDrops.lose(lost);
			helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(), "losing the advisor did not complete the advancement");

			// and once is enough
			helper.assertTrue(!LeafiaAdvancements.grant(player, LeafiaAdvancements.LOSE_ADVISOR), "granting it again said it completed it again");
			helper.assertTrue(!LeafiaAdvancements.grant(player, "no_such_advancement"), "an unknown advancement was granted");
		} finally {
			detach(helper, player);
		}
		helper.succeed();
	}

	/** With the advisor switched off nothing is granted, though the entity is lost. */
	@GameTest(template = EMPTY)
	public static void theAdvancementFollowsTheAdvisorSwitch(GameTestHelper helper) {
		boolean old = LeafiaFeatures.advisor;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var advancements = helper.getLevel().getServer().getAdvancements();
			helper.assertTrue((advancements.get(LeafiaRef.rl("loseadvisor")) != null) == old,
					"loseadvisor is " + (advancements.get(LeafiaRef.rl("loseadvisor")) != null ? "loaded" : "absent") + " with the advisor switch " + old);
			helper.assertTrue(advancements.get(LeafiaRef.rl("root_misc")) != null, "the root advancement is not loaded");
			if(old) {
				AdvancementHolder holder = advancements.get(LeafiaRef.rl("loseadvisor"));
				helper.assertTrue(holder.value().parent().map(p -> p.equals(LeafiaRef.rl("root_misc"))).orElse(false), "loseadvisor does not hang on the root");
				helper.assertTrue(holder.value().display().isPresent() && holder.value().display().get().isHidden(), "loseadvisor is not hidden");

				LeafiaFeatures.advisor = false;
				ItemEntity lost = drop(helper, new ItemStack(LeafiaItems.advisor.get()));
				NeoForge.EVENT_BUS.post(new ItemTossEvent(lost, player));
				lost.discard();
				LeafiaItemDrops.lose(lost);
				helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(), "the advancement was granted with the switch off");
			}
		} finally {
			LeafiaFeatures.advisor = old;
			detach(helper, player);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the sparks

	@GameTest(template = EMPTY)
	public static void metalToolsRingOnOresAndWoodDoesNot(GameTestHelper helper) {
		helper.assertTrue(WorldServerLeafia.ringsOnOre(new ItemStack(Items.IRON_PICKAXE)), "an iron pickaxe does not ring");
		helper.assertTrue(WorldServerLeafia.ringsOnOre(new ItemStack(Items.GOLDEN_AXE)), "a golden axe does not ring");
		helper.assertTrue(WorldServerLeafia.ringsOnOre(new ItemStack(Items.DIAMOND_SHOVEL)), "a diamond shovel does not ring");
		helper.assertTrue(WorldServerLeafia.ringsOnOre(new ItemStack(Items.STONE_PICKAXE)), "a stone pickaxe does not ring");
		helper.assertTrue(!WorldServerLeafia.ringsOnOre(new ItemStack(Items.WOODEN_PICKAXE)), "a wooden pickaxe rings");
		helper.assertTrue(!WorldServerLeafia.ringsOnOre(new ItemStack(Items.IRON_HOE)), "a hoe rings");
		helper.assertTrue(!WorldServerLeafia.ringsOnOre(new ItemStack(Items.IRON_SWORD)), "a sword rings");
		helper.assertTrue(!WorldServerLeafia.ringsOnOre(ItemStack.EMPTY), "a bare hand rings");
		helper.succeed();
	}

	/** Upstream's ores were coal, iron, gold, diamond, lapis, emerald and quartz: not redstone, not copper. */
	@GameTest(template = EMPTY)
	public static void theOresThatRingAreUpstreamsSeven(GameTestHelper helper) {
		for(var ore : List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.LAPIS_ORE,
				Blocks.EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE))
			helper.assertTrue(WorldServerLeafia.isVanillaOre(ore.defaultBlockState()), ore + " does not ring");
		for(var not : List.of(Blocks.REDSTONE_ORE, Blocks.COPPER_ORE, Blocks.STONE, Blocks.ANCIENT_DEBRIS, Blocks.IRON_BLOCK))
			helper.assertTrue(!WorldServerLeafia.isVanillaOre(not.defaultBlockState()), not + " rings");
		helper.succeed();
	}

	/**
	 * Through {@code MixinServerLevel}: breaking an ore with a metal pickaxe calls the hook on even progress steps only, and only
	 * under the switch. The hook counts itself ({@link WorldServerLeafia#rung}).
	 */
	@GameTest(template = EMPTY)
	public static void theBreakProgressHookRingsOnEvenStepsOfOresUnderTheSwitch(GameTestHelper helper) {
		boolean old = LeafiaFeatures.cosmeticOverhaul;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue(helper.getLevel().getEntity(player.getId()) == player, "the mock player is not in the level");
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			BlockPos ore = helper.absolutePos(new BlockPos(1, 2, 1));
			helper.getLevel().setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);

			LeafiaFeatures.cosmeticOverhaul = true;
			int before = WorldServerLeafia.rung;
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 0);
			helper.assertTrue(WorldServerLeafia.rung == before + 1, "step 0 did not ring");
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 1);
			helper.assertTrue(WorldServerLeafia.rung == before + 1, "step 1 rang");
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 4);
			helper.assertTrue(WorldServerLeafia.rung == before + 2, "step 4 did not ring");
			helper.getLevel().destroyBlockProgress(player.getId(), ore, -1);
			helper.assertTrue(WorldServerLeafia.rung == before + 2, "stopping the break rang");

			// not with a wooden pickaxe
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 2);
			helper.assertTrue(WorldServerLeafia.rung == before + 2, "wood rang");

			// not on stone
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			helper.getLevel().setBlock(ore, Blocks.STONE.defaultBlockState(), 3);
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 2);
			helper.assertTrue(WorldServerLeafia.rung == before + 2, "stone rang");

			// not with the switch off
			helper.getLevel().setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
			LeafiaFeatures.cosmeticOverhaul = false;
			helper.getLevel().destroyBlockProgress(player.getId(), ore, 2);
			helper.assertTrue(WorldServerLeafia.rung == before + 2, "an iron ore rang with the cosmetic overhaul off");

			// the oily coal answers to its own switch
			if(LeafiaBlocks.ore_coal_oil != null) {
				helper.getLevel().setBlock(ore, LeafiaBlocks.ore_coal_oil.get().defaultBlockState(), 3);
				helper.assertTrue(WorldServerLeafia.rings(helper.getLevel().getBlockState(ore)) == LeafiaFeatures.oilyCoal, "oily coal does not follow features.oilyCoal");
				if(LeafiaFeatures.oilyCoal) {
					helper.getLevel().destroyBlockProgress(player.getId(), ore, 2);
					helper.assertTrue(WorldServerLeafia.rung == before + 3, "oily coal did not ring");
				}
			}
			helper.getLevel().setBlock(ore, Blocks.AIR.defaultBlockState(), 3);
		} finally {
			LeafiaFeatures.cosmeticOverhaul = old;
			detach(helper, player);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the title screen

	@GameTest(template = EMPTY)
	public static void theWackySplashesAreUpstreamsAndTheEasterEggClearsThem(GameTestHelper helper) {
		boolean old = LeafiaEasterEgg.actuallySeenEasterEgg;
		try {
			LeafiaEasterEgg.actuallySeenEasterEgg = false;
			List<String> splashes = new ArrayList<>(List.of("Vanilla one", "Vanilla two"));
			LeafiaGeneralLocal.injectWackySplashes(splashes);
			helper.assertTrue(splashes.size() == 2 + 18 + 1 - 0, "after the injection there are " + splashes.size() + " splashes");
			helper.assertTrue(splashes.contains("Vanilla one") && splashes.contains("Floppenheimer!") && splashes.contains("Fentanyl!"), "vanilla's or the addon's lines are missing");
			helper.assertTrue(splashes.get(splashes.size() - 1).equals("Remove 1.7.10"), "the last splash is " + splashes.get(splashes.size() - 1));

			LeafiaEasterEgg.actuallySeenEasterEgg = true;
			splashes = new ArrayList<>(List.of("Vanilla one", "Vanilla two"));
			LeafiaGeneralLocal.injectWackySplashes(splashes);
			helper.assertTrue(splashes.equals(List.of("Remove 1.7.10")), "after the egg the splashes are " + splashes);
		} finally {
			LeafiaEasterEgg.actuallySeenEasterEgg = old;
		}
		helper.succeed();
	}

	/** The splash that names the player: red, in the JVM's language when upstream has a line for it, English when not. */
	@GameTest(template = EMPTY)
	public static void theSplashThatKnowsWhereYouLiveSpeaksYourLanguage(GameTestHelper helper) {
		String red = net.minecraft.ChatFormatting.RED.toString();
		helper.assertTrue(LeafiaGeneralLocal.knowsWhereYouLive("en", "bob").equals(red + "I know where you live, bob."), LeafiaGeneralLocal.knowsWhereYouLive("en", "bob"));
		helper.assertTrue(LeafiaGeneralLocal.knowsWhereYouLive("ru", "bob").equals(red + "Я знаю, где ты живёшь, bob."), LeafiaGeneralLocal.knowsWhereYouLive("ru", "bob"));
		helper.assertTrue(LeafiaGeneralLocal.knowsWhereYouLive("ja", "bob").contains("bob") && LeafiaGeneralLocal.knowsWhereYouLive("zh", "bob").contains("bob"), "ja/zh");
		helper.assertTrue(LeafiaGeneralLocal.knowsWhereYouLive("xx", "bob").equals(red + "I know where you live, bob."), "an unknown language is not English");
		helper.assertTrue(LeafiaGeneralLocal.knowsWhereYouLive(null, "bob").equals(red + "I know where you live, bob."), "no language is not English");
		helper.succeed();
	}

	/** One in 512, exactly: upstream's {@code new Random().nextInt(512) == 0}. */
	@GameTest(template = EMPTY)
	public static void theEasterEggIsOneInFiveHundredAndTwelve(GameTestHelper helper) {
		helper.assertTrue(LeafiaEasterEgg.ODDS == 512, "odds are 1 in " + LeafiaEasterEgg.ODDS);
		int[] asked = new int[1];
		Random zero = new Random() { @Override public int nextInt(int bound) { asked[0] = bound; return 0; } };
		Random one = new Random() { @Override public int nextInt(int bound) { asked[0] = bound; return 1; } };
		Random last = new Random() { @Override public int nextInt(int bound) { asked[0] = bound; return bound - 1; } };
		helper.assertTrue(LeafiaEasterEgg.roll(zero) && asked[0] == 512, "a roll of 0 of 512 does not show it");
		helper.assertTrue(!LeafiaEasterEgg.roll(one), "a roll of 1 shows it");
		helper.assertTrue(!LeafiaEasterEgg.roll(last), "the last roll shows it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the digamma record

	@GameTest(template = EMPTY)
	public static void aRecordPlayedInTheCraterPlaysTheDigammaSong(GameTestHelper helper) {
		var songs = helper.getLevel().registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
		var holder = songs.getHolder(LeafiaDigammaRecord.SONG);
		helper.assertTrue(holder.isPresent() == LeafiaFeatures.digammaCrater, "the digamma song is " + (holder.isPresent() ? "loaded" : "absent") + " with digammaCrater " + LeafiaFeatures.digammaCrater);
		if(!LeafiaFeatures.digammaCrater) { helper.succeed(); return; }

		var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		var crater = biomes.getHolderOrThrow(DigammaCrater.KEY);
		var plains = biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		int digamma = songs.getId(holder.get().value());
		int other = digamma == 0 ? 1 : 0;

		helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1010, crater, other) == digamma, "a record in the crater did not become the digamma song");
		helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1010, plains, other) == other, "a record on the plains was redirected");
		helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1011, crater, other) == other, "stopping a record was redirected");
		helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1010, crater, digamma) == digamma, "the digamma song was redirected to itself");
		helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1010, pos, other) == other || helper.getLevel().getBiome(pos).is(DigammaCrater.KEY), "a record in a test box was redirected");

		var song = holder.get().value();
		helper.assertTrue(song.comparatorOutput() == 15, "comparator output " + song.comparatorOutput());
		helper.assertTrue(Math.abs(song.lengthInSeconds() - 177.23077F) < 1e-3, "length " + song.lengthInSeconds());
		helper.assertTrue(song.soundEvent().is(LeafiaRef.rl("music.wtf")), "sound " + song.soundEvent());

		boolean old = LeafiaFeatures.digammaCrater;
		try {
			LeafiaFeatures.digammaCrater = false;
			helper.assertTrue(LeafiaDigammaRecord.redirect(helper.getLevel(), 1010, crater, other) == other, "redirected with the switch off");
		} finally {
			LeafiaFeatures.digammaCrater = old;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the asphalt

	@GameTest(template = EMPTY)
	public static void theAsphaltBoostIsUndoneExactlyAndNothingElse(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			double speed = ModBlocks.asphalt.get().speed;
			helper.assertTrue(speed == 1.5, "asphalt's multiplier is " + speed);
			Vec3 before = new Vec3(0.2, -0.1, -0.3);

			player.setDeltaMovement(before.x * speed, before.y, before.z * speed);
			helper.assertTrue(LeafiaAsphaltSpeed.undo(player, before), "a boosted player was not restored");
			helper.assertTrue(player.getDeltaMovement().equals(before), "restored to " + player.getDeltaMovement());

			player.setDeltaMovement(before);
			helper.assertTrue(!LeafiaAsphaltSpeed.undo(player, before), "an unboosted player was 'restored'");
			helper.assertTrue(player.getDeltaMovement().equals(before), "an unboosted player was changed");

			Vec3 pushed = new Vec3(before.x * speed + 0.1, before.y, before.z * speed);
			player.setDeltaMovement(pushed);
			helper.assertTrue(!LeafiaAsphaltSpeed.undo(player, before) && player.getDeltaMovement().equals(pushed), "a pushed player was put back");

			// vertical motion is never touched
			player.setDeltaMovement(before.x * speed, 0.7, before.z * speed);
			LeafiaAsphaltSpeed.undo(player, before);
			helper.assertTrue(player.getDeltaMovement().y == 0.7, "vertical motion was changed to " + player.getDeltaMovement().y);

			// the geometry is the base's: a hundredth under the feet
			BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
			helper.getLevel().setBlock(floor, ModBlocks.asphalt.get().defaultBlockState(), 3);
			player.setPos(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
			helper.assertTrue(LeafiaAsphaltSpeed.standsOnPlainAsphalt(player), "a player on asphalt is not on asphalt");
			helper.getLevel().setBlock(floor, ModBlocks.asphalt_light.get().defaultBlockState(), 3);
			helper.assertTrue(!LeafiaAsphaltSpeed.standsOnPlainAsphalt(player), "lit asphalt counts as plain");
			helper.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
			helper.getLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
		} finally {
			detach(helper, player);
		}
		helper.succeed();
	}

	/** The speed line leaves the plain asphalt's tooltip, only under the switch, and leaves the lit asphalt's and every other item's. */
	@GameTest(template = EMPTY)
	public static void theAsphaltTooltipLosesItsSpeedLineUnderTheSwitch(GameTestHelper helper) {
		boolean old = LeafiaFeatures.asphaltSpeed;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			ItemStack plain = new ItemStack(ModBlocks.asphalt.get());
			ItemStack lit = new ItemStack(ModBlocks.asphalt_light.get());
			helper.assertTrue(tooltip(player, plain, false).stream().anyMatch(LeafiaAsphaltSpeed::isSpeedLine), "the base's asphalt no longer says it is fast");

			helper.assertTrue(tooltip(player, plain, false).stream().anyMatch(LeafiaAsphaltSpeed::isSpeedLine), "the switch is off yet the line went");
			LeafiaFeatures.asphaltSpeed = true;
			helper.assertTrue(tooltip(player, plain, true).stream().noneMatch(LeafiaAsphaltSpeed::isSpeedLine), "the plain asphalt still says it is fast");
			helper.assertTrue(tooltip(player, lit, true).stream().anyMatch(LeafiaAsphaltSpeed::isSpeedLine), "the lit asphalt lost its line");
			helper.assertTrue(LeafiaAsphaltSpeed.isPlainAsphalt(plain) && !LeafiaAsphaltSpeed.isPlainAsphalt(lit) && !LeafiaAsphaltSpeed.isPlainAsphalt(new ItemStack(Items.STONE)), "isPlainAsphalt");
		} finally {
			LeafiaFeatures.asphaltSpeed = old;
			detach(helper, player);
		}
		helper.succeed();
	}

	private static List<Component> tooltip(ServerPlayer player, ItemStack stack, boolean filter) {
		List<Component> lines = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
		ItemTooltipEvent event = new ItemTooltipEvent(stack, player, lines, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY);
		LeafiaAsphaltSpeed.tooltip(event);
		return event.getToolTip();
	}

	// ------------------------------------------------------------------ the drawing over the world

	/** A unit box at the origin: twelve edges, then the zero-length ray, each end on a corner and coloured by its height. */
	@GameTest(template = EMPTY)
	public static void aHighlightIsTwelveEdgesOfItsBox(GameTestHelper helper) {
		Highlight h = new Highlight(new Vec3(10, 20, 30)).setColor(0x112233);
		h.colorTop = 0xAABBCC;
		h.size = new Vec3(2, 4, 6);
		List<Highlight.Edge> edges = h.edges(new Vec3(10, 20, 30));
		helper.assertTrue(edges.size() == 13, "an unextended highlight draws " + edges.size() + " lines, not 12 + the ray");

		for(int i = 0; i < 12; i++) {
			Highlight.Edge e = edges.get(i);
			Vec3 d = e.to().subtract(e.from());
			int axes = (d.x != 0 ? 1 : 0) + (d.y != 0 ? 1 : 0) + (d.z != 0 ? 1 : 0);
			helper.assertTrue(axes == 1, "edge " + i + " is not along an axis: " + d);
			helper.assertTrue(Math.abs(d.x) == 2 || Math.abs(d.y) == 4 || Math.abs(d.z) == 6, "edge " + i + " has length " + d);
			for(Vec3 end : new Vec3[] {e.from(), e.to()}) {
				helper.assertTrue(Math.abs(end.x) == 1 && Math.abs(end.y) == 2 && Math.abs(end.z) == 3, "edge " + i + " ends off a corner: " + end);
			}
			// passes: z, x, y
			helper.assertTrue((i < 4 && d.z != 0) || (i >= 4 && i < 8 && d.x != 0) || (i >= 8 && d.y != 0), "edge " + i + " is on the wrong pass: " + d);
			int fromColor = e.from().y > 0 ? 0xAABBCC : 0x112233, toColor = e.to().y > 0 ? 0xAABBCC : 0x112233;
			helper.assertTrue(e.fromColor() == fromColor && e.toColor() == toColor, "edge " + i + " colours " + Integer.toHexString(e.fromColor()) + "/" + Integer.toHexString(e.toColor()));
		}
		Highlight.Edge ray = edges.get(12);
		helper.assertTrue(ray.from().equals(Vec3.ZERO) && ray.to().equals(Vec3.ZERO), "the empty ray is " + ray);

		// a short ray is one line from the middle, in the top colour
		h.ray = new Vec3(0, 1, 0);
		edges = h.edges(new Vec3(10, 20, 30));
		helper.assertTrue(edges.size() == 13 && edges.get(12).to().equals(new Vec3(0, 1, 0)) && edges.get(12).toColor() == 0xAABBCC, "a short ray: " + edges.get(12));

		// a ray longer than the box's diagonal fans from all eight corners, and every one of the three passes adds the fan (upstream's
		// `draw` does, so the additive lines are three times as bright): 12 edges and 24 cone lines, no separate ray
		h.ray = new Vec3(0, 50, 0);
		edges = h.edges(new Vec3(10, 20, 30));
		helper.assertTrue(edges.size() == 12 + 24, "a long ray draws " + edges.size() + " lines");
		int cone = 0;
		java.util.Map<Vec3, Integer> perCorner = new java.util.HashMap<>();
		for(Highlight.Edge e : edges) {
			if(!e.to().equals(new Vec3(0, 50, 0))) continue;
			cone++;
			helper.assertTrue(e.fromColor() == 0xAABBCC && e.toColor() == 0xAABBCC, "a cone line is coloured " + Integer.toHexString(e.fromColor()));
			helper.assertTrue(Math.abs(e.from().x) == 1 && Math.abs(e.from().y) == 2 && Math.abs(e.from().z) == 3, "a cone line starts off a corner: " + e.from());
			perCorner.merge(e.from(), 1, Integer::sum);
		}
		helper.assertTrue(cone == 24 && perCorner.size() == 8 && perCorner.values().stream().allMatch(n -> n == 3), "the cone is " + cone + " lines over " + perCorner.size() + " corners");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aHighlightLivesForItsLifetimeAndNeverIfNegative(GameTestHelper helper) {
		Highlight forever = new Highlight(BlockPos.ZERO).show();
		Highlight brief = new Highlight(BlockPos.ZERO).setLifetime(1.0F).show();
		Highlight hidden = new Highlight(BlockPos.ZERO).setLifetime(5F).setVisibility(false);
		try {
			helper.assertTrue(forever.getVisibility() && brief.getVisibility() && !hidden.getVisibility(), "visibility");
			Highlight.cleanup(400);
			helper.assertTrue(brief.getVisibility() && Math.abs(brief.lifetime - 0.6F) < 1e-4, "after 0.4 s the lifetime is " + brief.lifetime);
			Highlight.cleanup(400);
			helper.assertTrue(brief.getVisibility() && Math.abs(brief.lifetime - 0.2F) < 1e-4, "after 0.8 s the lifetime is " + brief.lifetime);
			Highlight.cleanup(400);
			helper.assertTrue(!brief.getVisibility(), "after 1.2 s the highlight is still shown");
			helper.assertTrue(forever.getVisibility() && forever.lifetime == -1, "the permanent one expired");
			hidden.show();
			helper.assertTrue(hidden.getVisibility(), "show()");
			hidden.hide();
			helper.assertTrue(!hidden.getVisibility(), "hide()");
		} finally {
			forever.hide();
			brief.hide();
			hidden.hide();
		}
		helper.assertTrue(Highlight.instances.isEmpty(), "highlights are left over: " + Highlight.instances.size());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theAreaSettersBoxWholeBlocksAndPointsAlike(GameTestHelper helper) {
		Highlight block = new Highlight().setBlock(new BlockPos(3, 4, 5));
		helper.assertTrue(block.center.equals(new Vec3(3.5, 4.5, 5.5)) && block.size.equals(new Vec3(1, 1, 1)), "setBlock: " + block.center + " " + block.size);
		Highlight blocks = new Highlight().setArea(new BlockPos(1, 1, 1), new BlockPos(3, 2, 4));
		helper.assertTrue(blocks.size.equals(new Vec3(3, 2, 4)) && blocks.center.equals(new Vec3(2.5, 2, 3)), "block area: " + blocks.center + " " + blocks.size);
		Highlight points = new Highlight().setArea(new Vec3(4, 0, 2), new Vec3(0, 2, 6));
		helper.assertTrue(points.size.equals(new Vec3(4, 2, 4)) && points.center.equals(new Vec3(2, 1, 4)), "point area: " + points.center + " " + points.size);
		helper.succeed();
	}

	/** White, then orange at timer 16, red at 32; the gap 0.3, 0.15, 0, and the arms meet in the middle of each edge only when the gap is nothing. */
	@GameTest(template = EMPTY)
	public static void theLockonReticleClosesAndReddensAsTheTimerRuns(GameTestHelper helper) {
		int[][] colours = {{0, 0xFFFFFF}, {15, 0xFFFFFF}, {16, 0xFFAA00}, {31, 0xFFAA00}, {32, 0xFF0000}, {80, 0xFF0000}};
		for(int[] c : colours)
			helper.assertTrue(TopRender.lockonColor(c[0]) == c[1], "timer " + c[0] + " is " + Integer.toHexString(TopRender.lockonColor(c[0])));
		helper.assertTrue(TopRender.lockonGap(0) == 0.3F && TopRender.lockonGap(16) == 0.15F && TopRender.lockonGap(32) == 0F, "gaps");

		Vec3 left = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
		// at ten blocks the scale is 1: arm 0.4, gap 0.3, corners at 0.7
		Vec3[] open = TopRender.lockonBrackets(left, up, 10, 0);
		helper.assertTrue(open.length == 16, "the reticle has " + open.length + " ends");
		helper.assertTrue(near(open[0], new Vec3(-0.7, -0.7, 0)), "first corner " + open[0]);
		// the arm runs from the corner 0.4 along the edge (to x = -0.3), leaving a 0.6 gap in the middle
		helper.assertTrue(near(open[1], new Vec3(-0.3, -0.7, 0)), "first arm ends at " + open[1]);
		Vec3[] shut = TopRender.lockonBrackets(left, up, 10, 32);
		helper.assertTrue(near(shut[0], new Vec3(-0.4, -0.4, 0)) && near(shut[1], new Vec3(0, -0.4, 0)), "closed: " + shut[0] + " to " + shut[1]);
		// every arm of a closed reticle ends on the middle of its edge: the box is made of its corners and its midpoints
		for(int i = 1; i < 16; i += 2) helper.assertTrue(Math.abs(shut[i].x) < 1e-9 || Math.abs(shut[i].y) < 1e-9, "a closed arm ends at " + shut[i]);

		// it scales with distance: twice as far, twice as large
		Vec3[] far = TopRender.lockonBrackets(left, up, 20, 0);
		helper.assertTrue(near(far[0], open[0].scale(2)) && near(far[1], open[1].scale(2)), "the reticle did not scale with distance");
		// and it faces the camera: turn the basis and the corner turns with it
		Vec3[] turned = TopRender.lockonBrackets(new Vec3(0, 0, 1), up, 10, 0);
		helper.assertTrue(near(turned[0], new Vec3(0, -0.7, -0.7)), "turned corner " + turned[0]);
		helper.succeed();
	}

	private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 1e-5; }

	// ------------------------------------------------------------------ the rest

	/** The lock-on's two fields are the SEDNA handler's to write; nothing here sets them. */
	@GameTest(template = EMPTY)
	public static void theLockonStartsEmpty(GameTestHelper helper) {
		helper.assertTrue(TopRender.lockonTarget == null && TopRender.lockonTimer == 0, "the lock-on starts set");
		helper.succeed();
	}
}
