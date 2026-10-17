package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.bomb.Balefire;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK3.ATEntry;
import com.hbm.entity.projectile.EntityChemical;
import com.hbm.entity.projectile.EntityRBMKDebris;
import com.hbm.entity.projectile.EntityZirnoxDebris;
import com.hbm.explosion.ExplosionBalefire;
import com.hbm.explosion.ExplosionSolinium;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.radiation.ChunkRadiationHandler;
import com.hbm.handler.radiation.ChunkRadiationHandlerSimple;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.slices.Slice6;
import com.hbm.world.WorldUtil;
import com.hbm.world.biome.NtmBiomes;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.FolkvangrVacuumPacket;
import com.leafia.contents.effects.folkvangr.FolkvangrParticleMath;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.database.FolkvangrJammers;
import com.leafia.dev.blocks.legacy.MetaLevel;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaBalefireFire;
import com.leafia.overwrite_contents.LeafiaCoriumBehavior;
import com.leafia.overwrite_contents.LeafiaExplosionSeams;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.leafia.savedata.FalloutSavedData;
import com.leafia.savedata.FalloutSavedData.FalloutData;
import com.leafia.unsorted.explosion_vnt.BlockAllocatorSteamExplosion;
import com.leafia.unsorted.explosion_vnt.BlockMutatorCollapse;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EXPL-B (D-3013): the addon's balefire, balefire crater, Solinium rinse, corium, steam explosion, explosive
 * gas, chemical-thrower concrete, MK3 hook, F.L.E.I.J.A. clouds and Folkvangr - every upstream mixin of the
 * batch as a seam consumer, each pinned against upstream's numbers.
 *
 * <p>Ticks are driven by calling the handler with a {@link Script} random, which answers each {@code nextInt}
 * as the test says and records the bound, so the order of upstream's draws is pinned as well as their values.
 * Every blast runs at a random far spot no earlier run can have prepared, and is cleared afterwards; every
 * static switch and process-wide table the tests touch ({@code LeafiaFeatures}, the radiation proxy, the
 * level's fallout records, the field disturber map, {@code FolkvangrJammers}) is put back inside the same
 * synchronous body (D-952). No mock player is used.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaExplosionTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	/** Answers {@code nextInt} from a script, then {@code bound - 1}, and records every bound. */
	static final class Script extends LegacyRandomSource {
		private final int[] ints;
		private int next;
		final List<Integer> bounds = new ArrayList<>();

		Script(int... ints) {
			super(0L);
			this.ints = ints;
		}

		@Override
		public int nextInt(int bound) {
			bounds.add(bound);
			return next < ints.length ? ints[next++] : bound - 1;
		}
	}

	/** A far, chunk-aligned spot no earlier run can have touched, with the 3 x 3 chunks round it loaded. */
	private static BlockPos farSpot(GameTestHelper helper, int y) {
		RandomSource r = helper.getLevel().random;
		int x = (70_000 + r.nextInt(4000) * 16) * (r.nextBoolean() ? 1 : -1);
		int z = (70_000 + r.nextInt(4000) * 16) * (r.nextBoolean() ? 1 : -1);
		ServerLevel level = helper.getLevel();
		for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
		return new BlockPos(x + 8, y, z + 8);
	}

	private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
		for(BlockPos p : BlockPos.betweenClosed(from, to)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
		for(Entity e : level.getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(from, to).inflate(4))) e.discard();
	}

	private static void withSwitch(boolean vfx, boolean folkvangr, Runnable body) {
		boolean wasVfx = LeafiaFeatures.explosionVfx, wasFolk = LeafiaFeatures.folkvangr;
		LeafiaFeatures.explosionVfx = vfx;
		LeafiaFeatures.folkvangr = folkvangr;
		try {
			body.run();
		} finally {
			LeafiaFeatures.explosionVfx = wasVfx;
			LeafiaFeatures.folkvangr = wasFolk;
		}
	}

	/** Runs a body with a radiation handler of its own in the proxy slot, and puts the real one back. */
	private static void withHandler(GameTestHelper helper, Consumer<ChunkRadiationHandlerSimple> body) {
		ChunkRadiationHandler saved = ChunkRadiationManager.proxy;
		ChunkRadiationHandlerSimple handler = new ChunkRadiationHandlerSimple();
		handler.trackWorld(helper.getLevel());
		ChunkRadiationManager.proxy = handler;
		try {
			body.accept(handler);
		} finally {
			ChunkRadiationManager.proxy = saved;
		}
	}

	/** The test server runs with {@code doFireTick} off; the fire rules need it on. Put back in the same body. */
	private static void withFireTick(GameTestHelper helper, Runnable body) {
		ServerLevel level = helper.getLevel();
		GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
		boolean was = rule.get();
		rule.set(true, level.getServer());
		try {
			body.run();
		} finally {
			rule.set(was, level.getServer());
		}
	}

	private static void stoneFloor(GameTestHelper helper, int size) {
		for(int x = 0; x < size; x++) for(int z = 0; z < size; z++) helper.setBlock(x, 0, z, Blocks.STONE);
	}

	private static BlockState balefire(int age) {
		return ModBlocks.balefire.get().defaultBlockState().setValue(Balefire.AGE, age);
	}

	// =================================================================== balefire (MixinBalefire)

	/** Upstream's draws in upstream's order: the age, the reschedule, then the six neighbours at 100/100/80/80/100/100. */
	@GameTest(template = EMPTY16)
	public static void balefireRollsUpstreamsOddsInOrder(GameTestHelper helper) {
		withFireTick(helper, () -> {
			ServerLevel level = helper.getLevel();
			stoneFloor(helper, 6);
			BlockPos at = new BlockPos(2, 1, 2);
			helper.setBlock(at, balefire(0));
			helper.setBlock(at.east(), Blocks.OAK_PLANKS);
			BlockPos abs = helper.absolutePos(at);
			boolean humid = level.getBiome(abs).is(BiomeTags.INCREASED_FIRE_BURNOUT);
			int side = humid ? 50 : 100, vertical = humid ? 30 : 80;

			Script script = new Script(0, 0);
			LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, abs, script);
			helper.assertTrue(script.bounds.size() >= 8 && script.bounds.subList(0, 8).equals(List.of(3, 35, side, side, vertical, vertical, side, side)),
					"draws " + script.bounds);
			helper.assertTrue(helper.getBlockState(at).is(ModBlocks.balefire.get()) && helper.getBlockState(at).getValue(Balefire.AGE) == 0, "age kept: " + helper.getBlockState(at));
			helper.assertTrue(level.getBlockTicks().hasScheduledTick(abs, ModBlocks.balefire.get()), "rescheduled");
			helper.assertTrue(helper.getBlockState(at.east()).is(Blocks.OAK_PLANKS), "every catch roll missed");

			Script older = new Script(2, 0);
			LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, abs, older);
			helper.assertTrue(helper.getBlockState(at).getValue(Balefire.AGE) == 1, "nextInt(3) / 2 = 1 ages it: " + helper.getBlockState(at));
			helper.assertTrue(LeafiaBalefireFire.TICK_RATE == 30 && LeafiaBalefireFire.TICK_SPREAD == 35 && LeafiaBalefireFire.HUMID_BONUS == -50, "the constants");
		});
		helper.succeed();
	}

	/** A burnt neighbour becomes balefire ash unless {@code nextInt(age + 10) < 8}, when it catches one generation on. */
	@GameTest(template = EMPTY16)
	public static void balefireLeavesAshWhereItBurns(GameTestHelper helper) {
		withFireTick(helper, () -> {
			if(LeafiaBlocks.ash_balefire == null) return;
			ServerLevel level = helper.getLevel();
			stoneFloor(helper, 6);
			BlockPos at = new BlockPos(2, 1, 2);
			BlockPos abs = helper.absolutePos(at);

			helper.setBlock(at, balefire(2));
			helper.setBlock(at.east(), Blocks.OAK_PLANKS);
			Script ash = new Script(0, 0, 0, 11);
			LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, abs, ash);
			helper.assertTrue(ash.bounds.get(3) == 12, "the age roll is nextInt(age + 10): " + ash.bounds);
			helper.assertTrue(helper.getBlockState(at.east()).is(LeafiaBlocks.ash_balefire.get()), "planks became " + helper.getBlockState(at.east()));

			helper.setBlock(at, balefire(2));
			helper.setBlock(at.east(), Blocks.OAK_PLANKS);
			Script fire = new Script(0, 0, 0, 7, 4);
			LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, abs, fire);
			BlockState east = helper.getBlockState(at.east());
			helper.assertTrue(east.is(ModBlocks.balefire.get()) && east.getValue(Balefire.AGE) == 3, "7 < 8 catches at age 2 + 4/4: " + east);
		});
		helper.succeed();
	}

	/** On baleonite a balefire cannot die; on stone, with nothing to burn, an old one goes out. */
	@GameTest(template = EMPTY16)
	public static void balefireNeverDiesOnBaleonite(GameTestHelper helper) {
		withFireTick(helper, () -> {
			ServerLevel level = helper.getLevel();
			stoneFloor(helper, 6);
			BlockPos at = new BlockPos(2, 1, 2);
			helper.setBlock(at, balefire(4));
			LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, helper.absolutePos(at), new Script(0, 0));
			helper.assertTrue(helper.getBlockState(at).isAir(), "age 4 on stone went out: " + helper.getBlockState(at));
			helper.assertTrue(LeafiaBalefireFire.canDie(level, helper.absolutePos(at)), "stone lets it die");

			if(LeafiaBlocks.baleonite != null) {
				helper.setBlock(at.below(), LeafiaBlocks.baleonite.get().withMeta(0));
				helper.setBlock(at, balefire(4));
				helper.assertTrue(!LeafiaBalefireFire.canDie(level, helper.absolutePos(at)), "baleonite does not");
				LeafiaBalefireFire.updateTick(ModBlocks.balefire.get(), helper.getBlockState(at), level, helper.absolutePos(at), new Script(0, 0));
				helper.assertTrue(helper.getBlockState(at).is(ModBlocks.balefire.get()), "on baleonite it burns on: " + helper.getBlockState(at));
			}
		});
		helper.succeed();
	}

	// =================================================================== corium (MixinCoriumFinite)

	private static CoriumFinite corium() {
		return ModBlocks.corium_block.get();
	}

	private static void tickCorium(GameTestHelper helper, CoriumFinite block, BlockPos at, Script tick, Script fire) {
		RandomSource old = LeafiaCoriumBehavior.INSTANCE.swapRandom(fire);
		try {
			BlockPos abs = helper.absolutePos(at);
			helper.assertTrue(LeafiaCoriumBehavior.INSTANCE.updateTick(block, helper.getLevel(), abs, tick), "the behaviour takes the tick");
		} finally {
			LeafiaCoriumBehavior.INSTANCE.swapRandom(old);
		}
	}

	/** 50 RAD a tick, uncapped; it sets one tick in ten even on corium; ore one in three of the block branch. */
	@GameTest(template = EMPTY16)
	public static void coriumRadiatesAndSetsEvenOnCorium(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			withHandler(helper, handler -> {
				stoneFloor(helper, 6);
				BlockPos at = new BlockPos(2, 2, 2);
				helper.setBlock(at.below(), corium().defaultBlockState());
				helper.setBlock(at, corium().defaultBlockState());
				BlockPos abs = helper.absolutePos(at);

				helper.getLevel().getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(abs));
				Script stays = new Script(1);
				tickCorium(helper, corium(), at, stays, new Script(1, 1, 1));
				float rad = handler.getRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ());
				helper.assertTrue(Math.abs(rad - 50F) < 0.01F, "50 RAD, got " + rad);
				helper.assertTrue(helper.getBlockState(at).is(corium()) && stays.bounds.equals(List.of(10)), "nextInt(10) = 1: still liquid, " + stays.bounds);
				helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(abs, corium()), "and ticking");

				Script ore = new Script(0, 0, 0);
				tickCorium(helper, corium(), at, ore, new Script(1, 1, 1));
				BlockState expected = LeafiaBlocks.ore_corium_chernobylite == null ? ModBlocks.block_corium.get().defaultBlockState() : LeafiaBlocks.ore_corium_chernobylite.get().defaultBlockState();
				helper.assertTrue(helper.getBlockState(at).equals(expected) && ore.bounds.equals(List.of(10, 3, 3)), "sets as chernobylite over corium: " + helper.getBlockState(at) + " " + ore.bounds);
				float twice = handler.getRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ());
				helper.assertTrue(Math.abs(twice - 100F) < 0.01F, "uncapped, 100 after two ticks: " + twice);

				helper.setBlock(at, corium().defaultBlockState());
				tickCorium(helper, corium(), at, new Script(0, 1), new Script(1, 1, 1));
				helper.assertTrue(helper.getBlockState(at).is(ModBlocks.block_corium_cobble.get()), "nextInt(3) = 1 > 0: cobble, " + helper.getBlockState(at));
			});
			helper.succeed();
		});
	}

	/** Balefire corium: 500 RAD, and the block branch twice as likely, ending in zetalite. */
	@GameTest(template = EMPTY16)
	public static void balefireCoriumSetsAsZetalite(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			if(LeafiaLiquids.balecorium == null) { helper.succeed(); return; }
			CoriumFinite bale = LeafiaLiquids.balecorium.get();
			withHandler(helper, handler -> {
				stoneFloor(helper, 6);
				BlockPos at = new BlockPos(2, 1, 2);
				BlockPos abs = helper.absolutePos(at);
				helper.setBlock(at, bale.defaultBlockState());
				tickCorium(helper, bale, at, new Script(0, 1, 0), new Script(1, 1, 1));
				BlockState expected = LeafiaBlocks.ore_corium_zetalite == null ? ModBlocks.block_corium.get().defaultBlockState() : LeafiaBlocks.ore_corium_zetalite.get().defaultBlockState();
				helper.assertTrue(helper.getBlockState(at).equals(expected), "1 <= 1 then 0: zetalite, got " + helper.getBlockState(at));
				float rad = handler.getRadiation(helper.getLevel(), abs.getX(), abs.getY(), abs.getZ());
				helper.assertTrue(Math.abs(rad - 500F) < 0.01F, "500 RAD, got " + rad);
				helper.setBlock(at, bale.defaultBlockState());
				tickCorium(helper, bale, at, new Script(0, 1, 1), new Script(1, 1, 1));
				helper.assertTrue(helper.getBlockState(at).is(ModBlocks.block_corium.get()), "1 <= 1 then 1: the block, got " + helper.getBlockState(at));
			});
			helper.succeed();
		});
	}

	/** The sideways fire roll: balefire corium turns the rock beside it into baleonite (the XOR roll) and lights balefire over planks. */
	@GameTest(template = EMPTY16)
	public static void balefireCoriumEatsRockAndLightsBalefire(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			withFireTick(helper, () -> {
				if(LeafiaLiquids.balecorium == null || LeafiaBlocks.baleonite == null) return;
				CoriumFinite bale = LeafiaLiquids.balecorium.get();
				withHandler(helper, handler -> {
					stoneFloor(helper, 8);
					BlockPos at = new BlockPos(3, 1, 3);
					helper.setBlock(at, bale.defaultBlockState());
					helper.setBlock(at.east(), Blocks.STONE);
					helper.setBlock(at.west(), Blocks.OAK_PLANKS);
					// i = 0, then: east (2,1) - stone, eaten with roll 1 -> (1+1)^2 = 0 -> level 2; west (0,1) - planks under air, lit; self (1,1)
					Script fire = new Script(0, 2, 1, 1, 0, 1, 1, 1);
					tickCorium(helper, bale, at, new Script(1), fire);
					BlockState east = helper.getBlockState(at.east());
					helper.assertTrue(east.is(LeafiaBlocks.baleonite.get()) && east.getValue(BaleoniteBlock.META) == MetaLevel.L2, "stone became " + east);
					helper.assertTrue(helper.getBlockState(at.west().above()).is(ModBlocks.balefire.get()), "balefire over the planks: " + helper.getBlockState(at.west().above()));
					helper.assertTrue(fire.bounds.equals(List.of(3, 3, 3, 3, 3, 3, 3, 3)), "draws " + fire.bounds);

					// plain corium lights plain fire, and leaves the rock alone
					helper.setBlock(at, corium().defaultBlockState());
					helper.setBlock(at.east(), Blocks.STONE);
					helper.setBlock(at.west().above(), Blocks.AIR);
					tickCorium(helper, corium(), at, new Script(1), new Script(0, 2, 1, 0, 1, 1, 1));
					helper.assertTrue(helper.getBlockState(at.east()).is(Blocks.STONE), "corium leaves stone: " + helper.getBlockState(at.east()));
					helper.assertTrue(helper.getBlockState(at.west().above()).is(Blocks.FIRE), "and lights fire: " + helper.getBlockState(at.west().above()));
				});
			});
			helper.succeed();
		});
	}

	/** Contact: three seconds alight, three lava damage and 500 RAD through anything. */
	@GameTest(template = EMPTY16)
	public static void coriumBurnsAndIrradiatesWhoWadesIn(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			stoneFloor(helper, 6);
			Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
			float health = pig.getHealth();
			float rad = HbmLivingProps.getRadiation(pig);
			boolean took = LeafiaCoriumBehavior.INSTANCE.entityInside(corium(), corium().defaultBlockState(), helper.getLevel(), pig.blockPosition(), pig);
			helper.assertTrue(took, "the behaviour takes the contact");
			helper.assertTrue(pig.getRemainingFireTicks() == 60, "three seconds alight: " + pig.getRemainingFireTicks());
			helper.assertTrue(Math.abs(health - pig.getHealth() - 3F) < 0.01F, "three damage: " + health + " to " + pig.getHealth());
			helper.assertTrue(Math.abs(HbmLivingProps.getRadiation(pig) - rad - 500F) < 0.01F, "500 RAD: " + (HbmLivingProps.getRadiation(pig) - rad));
			pig.discard();
			helper.succeed();
		});
	}

	/** One time in three the pool refuses before the base rule is asked; otherwise the base decides. */
	@GameTest(template = EMPTY)
	public static void coriumRefusesOneTimeInThree(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			BlockState air = Blocks.AIR.defaultBlockState();
			Script no = new Script(0), defer = new Script(1);
			RandomSource old = LeafiaCoriumBehavior.INSTANCE.swapRandom(no);
			try {
				helper.assertTrue(Boolean.FALSE.equals(LeafiaCoriumBehavior.INSTANCE.canDisplace(corium(), helper.getLevel(), BlockPos.ZERO, air, helper.getLevel().random)), "0: no");
				LeafiaCoriumBehavior.INSTANCE.swapRandom(defer);
				helper.assertTrue(LeafiaCoriumBehavior.INSTANCE.canDisplace(corium(), helper.getLevel(), BlockPos.ZERO, air, helper.getLevel().random) == null, "1: the base rule");
			} finally {
				LeafiaCoriumBehavior.INSTANCE.swapRandom(old);
			}
			helper.assertTrue(no.bounds.equals(List.of(3)) && defer.bounds.equals(List.of(3)), "nextInt(3)");
			helper.succeed();
		});
	}

	/**
	 * Corium meeting water vanishes and blows up: {@code 12 + water / 40}, the water counted in the 21-cube.
	 * With the switch off it does neither. At a far spot, high in the air, cleared afterwards.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void coriumMeetingWaterExplodes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 250);
		BlockPos lo = far.offset(-12, -12, -12), hi = far.offset(12, 12, 12);
		try {
			for(int x = -1; x <= 1; x++) for(int z = -1; z <= 1; z++) level.setBlock(far.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2 | 16);
			BlockPos pool = far.offset(2, 0, 0);
			helper.assertTrue(LeafiaCoriumBehavior.countWater(level, pool) == 9, "nine water blocks: " + LeafiaCoriumBehavior.countWater(level, pool));
			helper.assertTrue(LeafiaCoriumBehavior.blastSize(9) == 9 / 40F + 12F, "12 + w / 40");
			helper.assertTrue(LeafiaCoriumBehavior.BASE_SIZE == 12F && LeafiaCoriumBehavior.WATER_PER_SIZE == 40F && LeafiaCoriumBehavior.WATER_REACH == 10
					&& LeafiaCoriumBehavior.STEAM_RESOLUTION == 32, "the constants");
			BlockPos near = far.offset(4, 0, 0), distant = far.offset(2, 11, 0);
			level.setBlock(near, Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(distant, Blocks.STONE.defaultBlockState(), 2 | 16);

			withSwitch(false, LeafiaFeatures.folkvangr, () -> level.setBlockAndUpdate(pool, corium().defaultBlockState()));
			helper.assertTrue(level.getBlockState(pool).is(corium()), "switched off, it sits beside the water: " + level.getBlockState(pool));
			level.setBlock(pool, Blocks.AIR.defaultBlockState(), 2 | 16);

			withSwitch(true, LeafiaFeatures.folkvangr, () -> level.setBlockAndUpdate(pool, corium().defaultBlockState()));
			helper.assertTrue(!level.getBlockState(pool).is(corium()), "it vanished: " + level.getBlockState(pool));
			helper.assertTrue(!level.getBlockState(near).is(Blocks.STONE), "stone two blocks out went: " + level.getBlockState(near));
			helper.assertTrue(level.getBlockState(distant).is(Blocks.STONE), "stone eleven up stayed");
		} finally {
			clear(level, lo, hi);
		}
		helper.succeed();
	}

	/** The steam allocator's numbers and what a ray passes for free; the collapse's floor test. */
	@GameTest(template = EMPTY16)
	public static void theSteamBlastPassesThroughWaterAndCorium(GameTestHelper helper) {
		helper.assertTrue(BlockAllocatorSteamExplosion.STEP_SIZE == 0.3F && BlockAllocatorSteamExplosion.AIR_LOSS == 2.75F
				&& BlockAllocatorSteamExplosion.RESISTANCE_DIVISOR == 10F && BlockAllocatorSteamExplosion.RESISTANCE_FLOOR == 0.03F, "the constants");
		helper.assertTrue(BlockAllocatorSteamExplosion.isFree(Blocks.AIR.defaultBlockState()) && BlockAllocatorSteamExplosion.isFree(Blocks.WATER.defaultBlockState())
				&& BlockAllocatorSteamExplosion.isFree(corium().defaultBlockState()), "air, water and corium are free");
		helper.assertTrue(!BlockAllocatorSteamExplosion.isFree(Blocks.STONE.defaultBlockState()) && !BlockAllocatorSteamExplosion.isFree(Blocks.LAVA.defaultBlockState())
				&& !BlockAllocatorSteamExplosion.isWater(Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)),
				"stone, lava and a waterlogged stair are not");
		if(LeafiaLiquids.balecorium != null)
			helper.assertTrue(BlockAllocatorSteamExplosion.isFree(LeafiaLiquids.balecorium.get().defaultBlockState()), "balefire corium is corium");

		stoneFloor(helper, 6);
		helper.setBlock(2, 3, 2, Blocks.AIR);
		helper.setBlock(2, 2, 2, Blocks.AIR);
		helper.setBlock(2, 1, 2, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BlockMutatorCollapse.canFall(level, helper.absolutePos(new BlockPos(2, 3, 2))), "air over air may fall");
		helper.assertTrue(!BlockMutatorCollapse.canFall(level, helper.absolutePos(new BlockPos(2, 2, 2))), "air over stone may not");
		helper.assertTrue(!BlockMutatorCollapse.canFall(level, helper.absolutePos(new BlockPos(2, 1, 2))), "a solid position may not");
		helper.setBlock(3, 1, 3, Blocks.SHORT_GRASS);
		helper.assertTrue(BlockMutatorCollapse.canFall(level, helper.absolutePos(new BlockPos(3, 2, 3))), "air over grass may");
		helper.assertTrue(BlockMutatorCollapse.CONSIDER_ONE_IN == 5 && BlockMutatorCollapse.FALL_UNLESS_ONE_IN == 5, "one in five, then four in five");
		helper.succeed();
	}

	// =================================================================== the balefire crater (MixinExplosionBalefire)

	/**
	 * Three columns of a radius-20 crater, at a far spot in the air: the centre digs 13 below the first air above
	 * the stone and turns the floor to baleonite 5; ten out digs 6 and leaves baleonite 3; a schrabidium cluster
	 * stops a column dead, with no baleonite. The dome above the centre is cleared to 27 up and no further than 42.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void theBalefireCraterDigsUpstreamsProfile(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 150);
		int py = far.getY();
		BlockPos lo = far.offset(-11, -20, -1), hi = far.offset(11, 50, 1);
		try {
			int[] xs = {0, 10, -10};
			for(int x : xs) for(int y = py - 15; y <= py + 2; y++) level.setBlock(far.offset(x, y - py, 0), Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(far.offset(0, 20, 0), Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(far.offset(0, 45, 0), Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(far.offset(-10, 0, 0), ModBlocks.block_schrabidium_cluster.get().defaultBlockState(), 2 | 16);

			ExplosionBalefire blast = new ExplosionBalefire(far.getX(), py, far.getZ(), level, 20);
			withSwitch(true, LeafiaFeatures.folkvangr, () -> {
				for(int x : xs) helper.assertTrue(LeafiaExplosionSeams.balefireColumn(blast, x, 0), "the handler takes column " + x);
			});

			for(int y = py - 9; y <= py + 2; y++)
				helper.assertTrue(!level.getBlockState(far.offset(0, y - py, 0)).is(Blocks.STONE), "centre dug at " + (y - py));
			BlockState floor = level.getBlockState(far.offset(0, -10, 0));
			if(LeafiaBlocks.baleonite != null)
				helper.assertTrue(floor.is(LeafiaBlocks.baleonite.get()) && floor.getValue(BaleoniteBlock.META) == MetaLevel.L5, "floor -10 is baleonite 5: " + floor);
			helper.assertTrue(level.getBlockState(far.offset(0, -11, 0)).is(Blocks.STONE), "only the floor is tested: -11 is stone");
			helper.assertTrue(level.getBlockState(far.offset(0, 20, 0)).isAir(), "the dome reaches 20 up");
			helper.assertTrue(level.getBlockState(far.offset(0, 45, 0)).is(Blocks.STONE), "and not 45");

			for(int y = py - 2; y <= py + 2; y++)
				helper.assertTrue(!level.getBlockState(far.offset(10, y - py, 0)).is(Blocks.STONE), "ten out dug at " + (y - py));
			BlockState floor10 = level.getBlockState(far.offset(10, -3, 0));
			if(LeafiaBlocks.baleonite != null)
				helper.assertTrue(floor10.is(LeafiaBlocks.baleonite.get()) && floor10.getValue(BaleoniteBlock.META) == MetaLevel.L3, "floor -3 is baleonite 3: " + floor10);
			helper.assertTrue(level.getBlockState(far.offset(10, -4, 0)).is(Blocks.STONE), "-4 is stone");

			BlockState cluster = level.getBlockState(far.offset(-10, 0, 0));
			helper.assertTrue(cluster.is(ModBlocks.block_schrabidium_cluster.get()) || cluster.is(ModBlocks.block_euphemium_cluster.get()), "the cluster stopped it: " + cluster);
			helper.assertTrue(level.getBlockState(far.offset(-10, -3, 0)).is(Blocks.STONE), "and no floor was made");

			withSwitch(false, LeafiaFeatures.folkvangr, () -> helper.assertTrue(!LeafiaExplosionSeams.balefireColumn(blast, 0, 0), "switched off, the base digs"));
		} finally {
			clear(level, lo, hi);
		}
		helper.succeed();
	}

	// =================================================================== the Solinium rinse (MixinExplosionSolinium, MixinExplosionNukeGeneric)

	/** A rinsed column in a crater biome gets the generated biome back, and fallout weather over the bomb expires. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void theSoliniumRinseRestoresTheBiomeAndEndsTheWeather(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 100);
		FalloutSavedData saved = FalloutSavedData.forLevel(level);
		FalloutData over = new FalloutData(new Vec3(far.getX() + 3, 0, far.getZ()), 10);
		FalloutData away = new FalloutData(new Vec3(far.getX() + 500, 0, far.getZ()), 10);
		saved.falloutMap.add(over);
		saved.falloutMap.add(away);
		try {
			WorldUtil.setBiome(level, far.getX(), far.getZ(), NtmBiomes.CRATER);
			helper.assertTrue(LeafiaExplosionSeams.isCrater(LeafiaExplosionSeams.biomeAt(level, far)), "painted");
			ExplosionSolinium sol = new ExplosionSolinium(far.getX(), far.getY(), far.getZ(), level, 5, 1F, 1F);
			withSwitch(true, LeafiaFeatures.folkvangr, () -> helper.assertTrue(LeafiaExplosionSeams.soliniumColumn(sol, 0, 0), "the handler takes the column"));
			Holder<Biome> now = LeafiaExplosionSeams.biomeAt(level, far);
			helper.assertTrue(!LeafiaExplosionSeams.isCrater(now) && now.equals(LeafiaExplosionSeams.generated(level, far)), "the generated biome is back: " + now.unwrapKey());
			helper.assertTrue(over.getTimeElapsed() > over.getLifetime(), "the record over the bomb expired");
			helper.assertTrue(away.getTimeElapsed() <= away.getLifetime(), "the one 500 out did not");
		} finally {
			saved.falloutMap.remove(over);
			saved.falloutMap.remove(away);
		}
		helper.succeed();
	}

	/** Every block the rinse treats specially, with "replaces" and "and on" told apart. */
	@GameTest(template = EMPTY16)
	public static void theSoliniumRinseRestoresTheAddonsBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(2, 1, 2);
		BlockPos abs = helper.absolutePos(at);

		record Case(Block from, Block to, boolean replaces) { }
		List<Case> cases = new ArrayList<>();
		if(LeafiaBlocks.waste_gravel != null) {
			cases.add(new Case(LeafiaBlocks.waste_gravel.get(), Blocks.GRAVEL, true));
			cases.add(new Case(LeafiaBlocks.waste_ice.get(), Blocks.ICE, true));
			cases.add(new Case(LeafiaBlocks.waste_sand.get(), Blocks.SAND, true));
			cases.add(new Case(LeafiaBlocks.waste_sandstone.get(), Blocks.SANDSTONE, true));
			cases.add(new Case(LeafiaBlocks.waste_sand_red.get(), Blocks.RED_SAND, true));
			cases.add(new Case(LeafiaBlocks.waste_red_sandstone.get(), Blocks.RED_SANDSTONE, true));
			cases.add(new Case(LeafiaBlocks.waste_snow_block.get(), Blocks.SNOW_BLOCK, true));
			cases.add(new Case(LeafiaBlocks.waste_terracotta.get(), Blocks.TERRACOTTA, true));
		}
		cases.add(new Case(ModBlocks.block_corium.get(), ModBlocks.sellafield_slaked.get(), true));
		cases.add(new Case(ModBlocks.block_corium_cobble.get(), ModBlocks.sellafield_slaked.get(), true));
		cases.add(new Case(ModBlocks.sellafield.get(), ModBlocks.sellafield_slaked.get(), true));
		cases.add(new Case(ModBlocks.gas_radon.get(), Blocks.AIR, true));
		cases.add(new Case(ModBlocks.pribris_burning.get(), ModBlocks.pribris.get(), true));
		cases.add(new Case(ModBlocks.fallout.get(), Blocks.AIR, false));
		if(LeafiaBlocks.ash_balefire != null) cases.add(new Case(LeafiaBlocks.ash_balefire.get(), Blocks.AIR, false));
		if(LeafiaBlocks.baleonite != null) cases.add(new Case(LeafiaBlocks.baleonite.get(), ModBlocks.sellafield_slaked.get(), false));

		for(Case c : cases) {
			helper.setBlock(at.below(), Blocks.STONE);
			level.setBlock(abs, c.from().defaultBlockState(), 2 | 16);
			boolean replaced = LeafiaExplosionSeams.soliniumBlock(level, abs, level.getBlockState(abs), new Script(4));
			helper.assertTrue(replaced == c.replaces(), c.from() + " replaces " + replaced);
			helper.assertTrue(level.getBlockState(abs).is(c.to()), c.from() + " became " + level.getBlockState(abs) + ", expected " + c.to());
		}

		helper.setBlock(at, balefire(0));
		helper.assertTrue(!LeafiaExplosionSeams.soliniumBlock(level, abs, level.getBlockState(abs), new Script()) && level.getBlockState(abs).is(Blocks.FIRE), "balefire to fire, and on");

		if(LeafiaBlocks.waste_dirt != null) {
			level.setBlock(abs, LeafiaBlocks.waste_dirt.get().defaultBlockState(), 2 | 16);
			Script coarse = new Script(1);
			helper.assertTrue(LeafiaExplosionSeams.soliniumBlock(level, abs, level.getBlockState(abs), coarse) && level.getBlockState(abs).is(Blocks.COARSE_DIRT) && coarse.bounds.equals(List.of(5)), "1 < 2: coarse dirt");
			level.setBlock(abs, LeafiaBlocks.scorched_earth.get().defaultBlockState(), 2 | 16);
			helper.assertTrue(LeafiaExplosionSeams.soliniumBlock(level, abs, level.getBlockState(abs), new Script(2)) && level.getBlockState(abs).is(Blocks.DIRT), "2: dirt");
		}

		level.setBlock(abs, Blocks.STONE.defaultBlockState(), 2 | 16);
		helper.assertTrue(!LeafiaExplosionSeams.soliniumBlock(level, abs, level.getBlockState(abs), new Script()) && level.getBlockState(abs).is(Blocks.STONE), "anything else is the base mod's");
		withSwitch(false, LeafiaFeatures.folkvangr, () -> helper.assertTrue(!LeafiaExplosionSeams.soliniumBlock(level, abs, ModBlocks.block_corium.get().defaultBlockState()), "switched off, the base rule"));
		helper.succeed();
	}

	// =================================================================== explosive gas (MixinBlockGasExplosive)

	/** Off by default; on, strength 2 and it breaks the block it sits on. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void explosiveGasRebalanceIsOffAndBreaksBlocksWhenOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		var gas = ModBlocks.gas_explosive.get();
		boolean was = LeafiaFeatures.explosiveGasRebalance;
		try {
			helper.assertTrue(!was, "off by default (D-1097)");
			level.setBlock(far.below(), Blocks.DIRT.defaultBlockState(), 2 | 16);
			helper.assertTrue(!LeafiaExplosionSeams.gasBlast(gas, level, far) && level.getBlockState(far.below()).is(Blocks.DIRT), "off: the base blast");
			LeafiaFeatures.explosiveGasRebalance = true;
			helper.assertTrue(LeafiaExplosionSeams.gasBlast(gas, level, far), "on: the handler takes it");
			helper.assertTrue(!level.getBlockState(far.below()).is(Blocks.DIRT), "and the dirt under it went");
			helper.assertTrue(LeafiaExplosionSeams.GAS_STRENGTH == 2F, "strength 2");
		} finally {
			LeafiaFeatures.explosiveGasRebalance = was;
			clear(level, far.offset(-4, -4, -4), far.offset(4, 4, 4));
		}
		helper.succeed();
	}

	// =================================================================== chemical concrete (MixinEntityChemical)

	/** Concrete that hits a block sets as the addon's concrete fluid in its own block, and the shot ends. */
	@GameTest(template = EMPTY16)
	public static void chemicalConcreteSetsWhereItLands(GameTestHelper helper) {
		// The overhaul defaults off (D-3013 merge note); this test exercises it.
		withSwitch(true, LeafiaFeatures.folkvangr, () -> {
			if(LeafiaLiquids.concrete == null) { helper.succeed(); return; }
			ServerLevel level = helper.getLevel();
			stoneFloor(helper, 6);
			BlockPos at = new BlockPos(2, 1, 2);
			BlockPos abs = helper.absolutePos(at);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()), Direction.UP, abs.below(), false);

			EntityChemical other = new EntityChemical(Slice6.CHEMICAL.get(), level).setFluid(Fluids.WATER);
			other.setPos(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5);
			LeafiaExplosionSeams.concreteImpact(other, hit);
			helper.assertTrue(helper.getBlockState(at).isAir() && !other.isRemoved(), "water is the base mod's");

			EntityChemical concrete = new EntityChemical(Slice6.CHEMICAL.get(), level).setFluid(Fluids.CONCRETE);
			concrete.setPos(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5);
			LeafiaExplosionSeams.concreteImpact(concrete, new EntityHitResult(other));
			helper.assertTrue(helper.getBlockState(at).isAir() && !concrete.isRemoved(), "an entity hit is not a block");
			LeafiaExplosionSeams.concreteImpact(concrete, hit);
			helper.assertTrue(helper.getBlockState(at).is(LeafiaLiquids.concrete.block.get()) && concrete.isRemoved(), "set: " + helper.getBlockState(at));
			helper.succeed();
		});
	}

	// =================================================================== the MK3 (MixinEntityNukeExplosionMK3)

	/** An unstarted F.L.E.I.J.A. MK3 becomes a Folkvangr where it stands; a started one, a waste one and a Solinium one do not. */
	@GameTest(template = EMPTY16)
	public static void aFleijaBlastBecomesAFolkvangr(GameTestHelper helper) {
		if(LeafiaEntities.NUKE_FOLKVANGR == null) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(4, 2, 4));
		EntityNukeExplosionMK3 blast = new EntityNukeExplosionMK3(level);
		blast.setPos(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
		blast.waste = false;

		EntityNukeExplosionMK3 started = new EntityNukeExplosionMK3(level);
		started.waste = false;
		started.did = true;
		EntityNukeExplosionMK3 waste = new EntityNukeExplosionMK3(level);
		EntityNukeExplosionMK3 sol = new EntityNukeExplosionMK3(level).makeSol();
		sol.waste = false;
		helper.assertTrue(!LeafiaExplosionSeams.mk3Tick(started) && !LeafiaExplosionSeams.mk3Tick(waste) && !LeafiaExplosionSeams.mk3Tick(sol), "only an unstarted F.L.E.I.J.A.");
		helper.assertTrue(!started.isRemoved() && !waste.isRemoved() && !sol.isRemoved(), "the others carry on");
		withSwitch(LeafiaFeatures.explosionVfx, false, () -> helper.assertTrue(!LeafiaExplosionSeams.mk3Tick(blast), "switched off, the base MK3"));

		helper.assertTrue(LeafiaExplosionSeams.mk3Tick(blast) && blast.isRemoved(), "the hook takes it and it is gone");
		List<EntityNukeFolkvangr> made = level.getEntitiesOfClass(EntityNukeFolkvangr.class, new AABB(abs).inflate(1));
		helper.assertTrue(made.size() == 1 && made.get(0).position().equals(blast.position()), "one Folkvangr where it stood: " + made);
		EntityNukeFolkvangr f = made.get(0);
		helper.assertTrue(EntityNukeFolkvangr.awaitingBind.contains(f), "waiting for its cloud");
		f.discard();
		helper.assertTrue(!EntityNukeFolkvangr.awaitingBind.contains(f), "and forgotten when removed");
		helper.succeed();
	}

	/** A Solinium MK3 sweeps up RBMK fuel and ZIRNOX fuel elements within its age; nothing else. */
	@GameTest(template = EMPTY16)
	public static void aSoliniumBlastSweepsUpFuelDebris(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 c = Vec3.atCenterOf(helper.absolutePos(new BlockPos(8, 4, 8)));
		EntityRBMKDebris fuelNear = new EntityRBMKDebris(level, c.x + 2, c.y, c.z, EntityRBMKDebris.DebrisType.FUEL);
		EntityRBMKDebris fuelFar = new EntityRBMKDebris(level, c.x + 6, c.y, c.z, EntityRBMKDebris.DebrisType.FUEL);
		EntityRBMKDebris blank = new EntityRBMKDebris(level, c.x - 2, c.y, c.z, EntityRBMKDebris.DebrisType.BLANK);
		EntityZirnoxDebris element = new EntityZirnoxDebris(level, c.x, c.y, c.z + 3, EntityZirnoxDebris.DebrisType.ELEMENT);
		EntityZirnoxDebris shrapnel = new EntityZirnoxDebris(level, c.x, c.y, c.z - 3, EntityZirnoxDebris.DebrisType.SHRAPNEL);
		List<Entity> all = List.of(fuelNear, fuelFar, blank, element, shrapnel);
		for(Entity e : all) level.addFreshEntity(e);
		try {
			LeafiaExplosionSeams.sweepDebris(level, c, 5);
			helper.assertTrue(fuelNear.isRemoved() && element.isRemoved(), "fuel within 5 swept");
			helper.assertTrue(!fuelFar.isRemoved() && !blank.isRemoved() && !shrapnel.isRemoved(), "fuel 6 out, a beam and shrapnel stay");
		} finally {
			for(Entity e : all) e.discard();
		}
		helper.succeed();
	}

	/** A field disturber's cancellation is recorded for the DFC, through the real jam path. */
	@GameTest(template = EMPTY)
	public static void aJammedFleijaRecordsTheJammer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos saved = FolkvangrJammers.lastDetectedJammer;
		BlockPos far = farSpot(helper, 64);
		ATEntry entry = new ATEntry(level.dimension(), far.getX(), far.getY(), far.getZ());
		EntityNukeExplosionMK3.at.put(entry, level.getGameTime() + 100);
		try {
			EntityNukeExplosionMK3 jammed = EntityNukeExplosionMK3.statFacFleija(level, far.getX() + 10, far.getY(), far.getZ(), 5);
			helper.assertTrue(jammed.isRemoved(), "the disturber cancelled it");
			helper.assertTrue(far.equals(FolkvangrJammers.lastDetectedJammer), "and its position was recorded: " + FolkvangrJammers.lastDetectedJammer);
		} finally {
			EntityNukeExplosionMK3.at.remove(entry);
			FolkvangrJammers.lastDetectedJammer = saved;
		}
		helper.succeed();
	}

	// =================================================================== the clouds (MixinEntityCloudFleija)

	/** A cloud grows a block a tick, is finished at its size, and lingers {@code ceil(10 + sqrt(max)) + 10} ticks. */
	@GameTest(template = EMPTY16)
	public static void aCloudGrowsAndLingers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(4, 2, 4));
		withSwitch(LeafiaFeatures.explosionVfx, true, () -> {
			EntityCloudFleija cloud = new EntityCloudFleija(level, 5);
			cloud.setPos(abs.getX(), abs.getY(), abs.getZ());
			for(int t = 1; t <= 5; t++) {
				cloud.tick();
				helper.assertTrue(cloud.scale == t && !LeafiaFleijaClouds.isFinished(cloud), "tick " + t + ": scale " + cloud.scale);
			}
			helper.assertTrue(cloud.getExtraData().getFloat(LeafiaFleijaClouds.SCALE) == 5F, "the scale is synced");
			cloud.tick();
			helper.assertTrue(LeafiaFleijaClouds.isFinished(cloud) && cloud.scale == 6, "finished at 5, grown on");
			helper.assertTrue(LeafiaFleijaClouds.finishTicks(5) == 23 && LeafiaFleijaClouds.finishTicks(50) == 28, "lingers");
			for(int t = 0; t < 22; t++) cloud.tick();
			helper.assertTrue(!cloud.isRemoved(), "23 finished ticks: still there");
			cloud.tick();
			helper.assertTrue(cloud.isRemoved(), "24: gone");
			helper.assertTrue(cloud.getExtraData().contains(LeafiaFleijaClouds.TICKRATE), "a tick rate was measured");
		});
		withSwitch(LeafiaFeatures.explosionVfx, false, () -> {
			EntityCloudFleija cloud = new EntityCloudFleija(level, 5);
			cloud.setPos(abs.getX(), abs.getY(), abs.getZ());
			for(int t = 0; t < 5; t++) cloud.tick();
			helper.assertTrue(cloud.isRemoved() && !cloud.getExtraData().contains(LeafiaFleijaClouds.SCALE), "switched off, the base cloud dies at its age");
		});
		helper.succeed();
	}

	/** A cloud binds a waiting Folkvangr within 1.5 blocks and no further. */
	@GameTest(template = EMPTY16)
	public static void aCloudBindsAFolkvangrWithinOneAndAHalf(GameTestHelper helper) {
		if(LeafiaEntities.NUKE_FOLKVANGR == null) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		Vec3 p = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		Vec3 q = Vec3.atCenterOf(helper.absolutePos(new BlockPos(12, 2, 12)));
		EntityNukeFolkvangr near = new EntityNukeFolkvangr(LeafiaEntities.NUKE_FOLKVANGR.get(), level, p, null);
		EntityNukeFolkvangr far = new EntityNukeFolkvangr(LeafiaEntities.NUKE_FOLKVANGR.get(), level, q, null);
		try {
			EntityCloudFleija a = new EntityCloudFleija(level, 50);
			a.setPos(p.x + 1.5, p.y, p.z);
			EntityCloudFleija b = new EntityCloudFleija(level, 50);
			b.setPos(q.x + 1.6, q.y, q.z);
			withSwitch(LeafiaFeatures.explosionVfx, true, () -> {
				a.tick();
				b.tick();
			});
			helper.assertTrue(near.cloudBound == a && LeafiaFleijaClouds.getBound(a) == near && !EntityNukeFolkvangr.awaitingBind.contains(near), "1.5 binds");
			helper.assertTrue(far.cloudBound == null && LeafiaFleijaClouds.getBound(b) == null && EntityNukeFolkvangr.awaitingBind.contains(far), "1.6 does not");
		} finally {
			near.discard();
			far.discard();
		}
		helper.succeed();
	}

	/** The rings: 1 chunk, then {@code 8n}; a bound cloud's speed {@code min(16 / (ring / 2), 1)}; the vacuum's numbers. */
	@GameTest(template = EMPTY)
	public static void theFolkvangrsRingsAndVacuumNumbers(GameTestHelper helper) {
		helper.assertTrue(EntityNukeFolkvangr.getRowTotal((short) 0) == 1 && EntityNukeFolkvangr.getRowTotal((short) 1) == 8
				&& EntityNukeFolkvangr.getRowTotal((short) 2) == 16 && EntityNukeFolkvangr.getRowTotal((short) 5) == 40, "1, 8, 16, 40");
		helper.assertTrue(EntityNukeFolkvangr.getPreferredSpeedMultiplier((short) 4) == 1 && EntityNukeFolkvangr.getPreferredSpeedMultiplier((short) 5) == 0.8
				&& EntityNukeFolkvangr.getPreferredSpeedMultiplier((short) 10) == 0.4, "full speed to ring 4, then 0.8, 0.4");
		helper.assertTrue(EntityNukeFolkvangr.postEffectTicks(50) == 27 && EntityNukeFolkvangr.postEffectTicks(6) == 3 && EntityNukeFolkvangr.postEffectTicks(200) == 129,
				"vacuum lengths " + EntityNukeFolkvangr.postEffectTicks(50));
		helper.assertTrue(Math.abs(EntityNukeFolkvangr.vacuumForce(50) - 0.2215956623727) < 1e-9, "force " + EntityNukeFolkvangr.vacuumForce(50));
		Vec3 at10 = EntityNukeFolkvangr.pullOn(new Vec3(10, 0, 0), Vec3.ZERO, 10, 6, 18, 0.1);
		helper.assertTrue(Math.abs(at10.x + 0.1 * 1.75 * Math.pow(2, -10 / 3D)) < 1e-9 && at10.y == 0 && at10.z == 0, "a third of the way out pulls at the expo-out curve (D-3075): " + at10);
		helper.assertTrue(EntityNukeFolkvangr.pullOn(new Vec3(20, 0, 0), Vec3.ZERO, 20, 6, 18, 0.1).length() == 0, "none past 3r");
		helper.assertTrue(Math.abs(EntityNukeFolkvangr.pullOn(new Vec3(3, 0, 0), Vec3.ZERO, 3, 6, 18, 0.1).x + 0.175) < 1e-9, "all of it inside r");
		helper.assertTrue(EntityNukeFolkvangr.vacuumForceMultiplier == 1.75 && EntityNukeFolkvangr.VACUUM_DAMAGE == 3 && EntityNukeFolkvangr.BIND_TIMEOUT == 10
				&& LeafiaFleijaClouds.BIND_RANGE == 1.5 && LeafiaFleijaClouds.LIGHTNING_HEIGHT == 200, "the constants");
		helper.succeed();
	}

	/** The cloud particle's fade, its ring sizes and the vacuum particle's shrink and fade. */
	@GameTest(template = EMPTY)
	public static void theCloudParticlesFadeAndRing(GameTestHelper helper) {
		helper.assertTrue(FolkvangrParticleMath.cloudAlpha(0, 0) == 0 && FolkvangrParticleMath.cloudAlpha(5, 0) == 0.5F
				&& FolkvangrParticleMath.cloudAlpha(10, 0) == 1F && FolkvangrParticleMath.cloudAlpha(25, 0.7F) == 0.7F, "in over ten, then held");
		helper.assertTrue(Math.abs(FolkvangrParticleMath.cloudAlpha(50, 1) - 0.35355338F) < 1e-6 && FolkvangrParticleMath.cloudAlpha(60, 1) == 0, "out from 40 to 60");
		helper.assertTrue(FolkvangrParticleMath.ringCount(4) == 6 && FolkvangrParticleMath.ringCount(5) == 6 && FolkvangrParticleMath.ringCount(7) == 8,
				"ring sizes " + FolkvangrParticleMath.ringCount(4) + " " + FolkvangrParticleMath.ringCount(5) + " " + FolkvangrParticleMath.ringCount(7));
		Vec3 pos = FolkvangrParticleMath.cloudPosition(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 0, 0.5), 10);
		helper.assertTrue(pos.distanceTo(new Vec3(14, 0, 7)) < 1e-9, "1.4 times the scale: " + pos);
		helper.assertTrue(FolkvangrParticleMath.vacuumRatio(10, 10, 4) == 1 && FolkvangrParticleMath.vacuumRatio(2, 10, 4) == 0
				&& FolkvangrParticleMath.vacuumRatio(6, 10, 4) == 0.5, "ratio from start to half the inner radius");
		helper.assertTrue(FolkvangrParticleMath.vacuumScale(2, 1) == 2 && FolkvangrParticleMath.vacuumAlpha(0.2F, 0) == 0.2F
				&& Math.abs(FolkvangrParticleMath.vacuumAlpha(0.2F, 0.75) - 0.1F) < 1e-6, "shrink and brighten");
		helper.assertTrue(FolkvangrParticleMath.CLOUD_SCALE == 3 && FolkvangrParticleMath.VACUUM_LIFETIME == 200 && FolkvangrParticleMath.CLOUD_LIFETIME == 60, "sizes and lives");
		helper.succeed();
	}

	/** The vacuum packet is upstream's five fields on the wire, appended at 25. */
	@GameTest(template = EMPTY)
	public static void theVacuumPacketOnTheWire(GameTestHelper helper) {
		helper.assertTrue(LeafiaPacketTypes.FOLKVANGR_VACUUM == 25 && LeafiaPacketTypes.idOf(new FolkvangrVacuumPacket()) == 25, "id 25, after the DFC's pair");
		FolkvangrVacuumPacket p = new FolkvangrVacuumPacket(new Vec3(1.5, -64.25, 2_000_000.125), 27, 50, 150, 0.22);
		LeafiaBuf b = new LeafiaBuf(Unpooled.buffer());
		p.encode(b);
		helper.assertTrue(b.writerIndex == 6 * 64 + 32, "six doubles and an int: " + b.writerIndex);
		FolkvangrVacuumPacket q = FolkvangrVacuumPacket.read(b);
		helper.assertTrue(q.pos.equals(p.pos) && q.postEffect == 27 && q.vacuumStart == 50 && q.vacuumEnd == 150 && q.vacuumForce == 0.22, "round trip");
		helper.succeed();
	}

	/**
	 * End to end, as the F.L.E.I.J.A. bomb does it, at a far spot in the air: an MK3 and a radius-6 cloud. The
	 * MK3 becomes a Folkvangr, the cloud binds it, the sphere is erased - {@code sqrt(36 - 1) = 5} up and down at
	 * the centre, {@code sqrt(36 - 25 - 1) = 3} five across, nothing six across - a pig inside dies, and an item
	 * 9.9 out is pulled in.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 1200, batch = "leafia_folkvangr_isolated")
	public static void aFolkvangrErasesItsSphere(GameTestHelper helper) {
		if(LeafiaEntities.NUKE_FOLKVANGR == null || !LeafiaFeatures.folkvangr) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		for(int y = -7; y <= 7; y++) for(int x = -7; x <= 7; x++) level.setBlock(far.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 2 | 16);

		EntityNukeExplosionMK3 mk3 = EntityNukeExplosionMK3.statFacFleija(level, far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5, 6);
		level.addFreshEntity(mk3);
		EntityCloudFleija cloud = new EntityCloudFleija(level, 6);
		cloud.setPos(far.getX(), far.getY(), far.getZ());
		level.addFreshEntity(cloud);
		Pig pig = EntityType.PIG.create(level);
		pig.setPos(far.getX() + 0.5, far.getY() + 2, far.getZ() + 2.5);
		pig.setNoAi(true);
		pig.setNoGravity(true);
		level.addFreshEntity(pig);
		// in the Folkvangr's own chunk, which its ticket keeps ticking: 9.9 out, past the kill and inside the vacuum
		// an item, not a stand: a stand with no gravity has no physics at all and would not move
		ItemEntity stand = new ItemEntity(level, far.getX() + 7.5, far.getY(), far.getZ() + 7.5, new ItemStack(Items.STONE));
		stand.setDeltaMovement(Vec3.ZERO);
		stand.setNoGravity(true);
		stand.setNeverPickUp();
		level.addFreshEntity(stand);
		double startX = stand.getX();
		EntityNukeFolkvangr[] seen = new EntityNukeFolkvangr[1];

		helper.onEachTick(() -> {
			if(seen[0] == null) {
				List<EntityNukeFolkvangr> f = level.getEntitiesOfClass(EntityNukeFolkvangr.class, new AABB(far).inflate(2));
				if(!f.isEmpty()) seen[0] = f.get(0);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(seen[0] != null && seen[0].isRemoved() && mk3.isRemoved(), "still running");
			helper.assertTrue(seen[0].cloudBound == cloud, "the cloud bound it");
			helper.assertTrue(level.getBlockState(far.offset(0, 5, 0)).isAir() && level.getBlockState(far.offset(0, -5, 0)).isAir(), "five up and down erased");
			helper.assertTrue(level.getBlockState(far.offset(0, 6, 0)).is(Blocks.STONE) && level.getBlockState(far.offset(0, -6, 0)).is(Blocks.STONE), "six up and down kept");
			helper.assertTrue(level.getBlockState(far.offset(5, 3, 0)).isAir() && level.getBlockState(far.offset(5, 4, 0)).is(Blocks.STONE), "five across, three up erased and four kept");
			helper.assertTrue(level.getBlockState(far.offset(6, 0, 0)).is(Blocks.STONE) && level.getBlockState(far.offset(-6, 0, 0)).is(Blocks.STONE), "six across kept");
			helper.assertTrue(pig.isRemoved() || pig.isDeadOrDying(), "the pig inside died");
			helper.assertTrue(stand.getX() < startX, "the item 9.9 out was pulled in: " + startX + " to " + stand.getX());
			// cleared only once every assertion has held: a failed check would otherwise check the cleared world next tick
			cloud.discard();
			pig.discard();
			stand.discard();
			clear(level, far.offset(-8, -8, -1), far.offset(8, 8, 8));
		});
	}
}
