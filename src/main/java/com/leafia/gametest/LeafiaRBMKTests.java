package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.RBMKColumn;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlAuto;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.blockentity.machine.rbmk.RBMKDials.RBMKKeys;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.hbm.blocks.machine.rbmk.RBMKDebris;
import com.hbm.config.NtmGameRules;
import com.hbm.entity.projectile.EntityRBMKDebris;
import com.hbm.entity.projectile.EntityRBMKDebris.DebrisType;
import com.hbm.gametest.TickWindow;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.rbmk.RBMKConstants;
import com.leafia.contents.machines.reactors.rbmk.debris.RBMKDebrisSmokeTE;
import com.leafia.dev.optimization.LeafiaParticlePacket;
import com.leafia.dev.optimization.LeafiaParticlePacket.JumpingRBMKParticle;
import com.leafia.dev.optimization.LeafiaParticlePacket.PinkRBMK;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaRBMKSeams;
import com.leafia.overwrite_contents.interfaces.IMixinRBMKColumn$ControlColumn;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKBase;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKControlAuto;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The RBMK overhaul (D-3018): the damage counter and the lid jump, the fuel channel's meltdown by damage,
 * the scram on the automatic rods and its acknowledgement, the console's AZ-5 order and its mark, the
 * smoking debris, the debris that drops what it tears out, the Cherenkov colours and the two particle kinds.
 *
 * <p>The overhaul is a process-wide hook that reads {@code overhauls.rbmkOverhaul} when it runs, so no test
 * turns the switch on for longer than one synchronous call: the rest of the batch's reactors must stay the
 * base mod's. Where a test needs the hook itself to act it arms its own positions in
 * {@link LeafiaRBMKSeams#ARMED}, and disarms them in a {@code finally}. Every body that drives a column calls
 * the overhaul's tick directly and synchronously, so passive cooling and the batch's tick order never move a
 * number between two reads (rule 10).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaRBMKTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	private static BlockEntityRBMKBase place(GameTestHelper helper, Supplier<? extends Block> block, BlockPos relative) {
		BlockState core = block.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, RBMKBase.DIR_NO_LID);
		helper.setBlock(relative, core);
		return (BlockEntityRBMKBase) helper.getBlockEntity(relative);
	}

	private static void close(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-9, what + ": expected " + expected + ", was " + actual);
	}

	/// THE DAMAGE COUNTER ///

	/** Upstream's two constants and its counter, row by row. */
	@GameTest(template = EMPTY)
	public static void theDamageCounterIsUpstreams(GameTestHelper helper) {

		helper.assertTrue(IMixinTileEntityRBMKBase.dmgIncrement == 2, "dmgIncrement is 2");
		helper.assertTrue(IMixinTileEntityRBMKBase.maxDamage == 1200, "maxDamage is 30 x 20 x 2 = 1200 (a minute at the base rate)");

		// above the rated heat and still heating: two a tick
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 1600, 1500, 1500, false) == 12, "heating above the rating costs 2");
		// above the rating, cooling, and below 2000: forgiven
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 1600, 1700, 1500, false) == 10, "cooling below 2000 costs nothing");
		// cooling, but at 2000 or over: still two
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 2000, 2100, 1500, false) == 12, "at 2000 cooling still costs 2");
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 1999, 2100, 1500, false) == 10, "just under 2000 cooling is forgiven");
		// the ramp: one more per 2000 over 3000, floored
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(0, 4999, 4999, 1500, false) == 2, "under 5000 it is still 2");
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(0, 5000, 5000, 1500, false) == 3, "5000 costs 3");
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(0, 9000, 9000, 1500, false) == 5, "9000 costs 5");
		// at or under the rating: heals one, never below 0
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 1500, 1500, 1500, false) == 9, "at the rating it heals 1");
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(0, 20, 20, 1500, false) == 0, "and stops at 0");
		// meltdowns disabled: always heals
		helper.assertTrue(LeafiaRBMKSeams.nextDamage(10, 9000, 9000, 1500, true) == 9, "with meltdowns off the column heals");

		helper.succeed();
	}

	/** The counter on a real column: kept in the extra tag under upstream's key, saved, and gone at 0. */
	@GameTest(template = EMPTY)
	public static void aHotColumnCountsDamageInItsExtraTagAndHealsWhenCool(GameTestHelper helper) {

		BlockEntityRBMKBase column = place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 2));
		helper.assertTrue(column.maxHeat() == 1500D, "a blank column is rated 1500");

		column.heat = 2500;
		LeafiaRBMKSeams.tickColumn(column); // last heat starts at upstream's 20
		LeafiaRBMKSeams.tickColumn(column);
		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$getDamage(column) == 4, "two ticks at 2500 cost 4, got " + IMixinTileEntityRBMKBase.leafia$getDamage(column));
		helper.assertTrue(column.getExtraData().getInt("leafia_damage") == 4, "under upstream's key");

		column.heat = 1600; // cooling from 2500, under 2000
		LeafiaRBMKSeams.tickColumn(column);
		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$getDamage(column) == 4, "cooling under 2000 is free");

		column.heat = 5000;
		LeafiaRBMKSeams.tickColumn(column);
		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$getDamage(column) == 7, "5000 costs 3");

		CompoundTag saved = column.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.getCompound(BlockEntityRBMKBase.EXTRA_KEY).getInt("leafia_damage") == 7, "the counter is saved with the column");

		column.heat = 20;
		for(int i = 0; i < 7; i++) LeafiaRBMKSeams.tickColumn(column);
		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$getDamage(column) == 0, "seven cool ticks heal seven");
		helper.assertTrue(!column.getExtraData().contains("leafia_damage"), "and an undamaged column carries no key");

		helper.succeed();
	}

	/// THE LID ///

	/** Four columns around and a ring of stone a block up: nothing would show the lid's sides. */
	private static BlockEntityRBMKBase enclosedColumn(GameTestHelper helper, boolean ring) {
		BlockEntityRBMKBase centre = place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 2));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(1, 1, 2));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(3, 1, 2));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 1));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 3));
		if(ring) {
			helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
			helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
			helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
			helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
		}
		return centre;
	}

	/** Upstream's rise and fall constants, and the chance a lid at rest takes off. */
	@GameTest(template = EMPTY)
	public static void theLidsNumbersAreUpstreams(GameTestHelper helper) {

		helper.assertTrue(RBMKConstants.gravity == 1, "gravity is 1 block/s^2, a twentieth a tick");
		close(helper, 100 * 0.0005D, LeafiaRBMKSeams.riseStep(1200), "a full-damage lid rises 0.05 a tick");
		close(helper, 100 * 0.005D, LeafiaRBMKSeams.riseLimit(1200), "to half a block");
		close(helper, 50 * 0.0005D, LeafiaRBMKSeams.riseStep(300), "a quarter-damage lid is sqrt(1/4) of that");
		close(helper, 50 * 0.005D, LeafiaRBMKSeams.riseLimit(300), "both ways");
		close(helper, 99 * 0.0005D, LeafiaRBMKSeams.riseStep(1199), "the damage factor is floored to an int percent");
		helper.assertTrue(LeafiaRBMKSeams.jumpChanceBound(0) == 405, "one in 405 at no damage");
		helper.assertTrue(LeafiaRBMKSeams.jumpChanceBound(600) == 205, "one in 205 at half");
		helper.assertTrue(LeafiaRBMKSeams.jumpChanceBound(1200) == 5, "one in 5 at full");
		helper.assertTrue(LeafiaRBMKSeams.jumpChanceBound(1199) == 5, "the third is floored");
		helper.succeed();
	}

	/**
	 * A lid already in the air rises a step a tick to its limit, falls under gravity and lands, and every
	 * tick's height, direction and speed is what upstream's arithmetic gives. Starting in the air skips the
	 * random take-off, so nothing here depends on a roll.
	 */
	@GameTest(template = EMPTY)
	public static void anEnclosedLidRisesFallsAndLandsByUpstreamsArithmetic(GameTestHelper helper) {

		BlockEntityRBMKBase column = enclosedColumn(helper, true);
		helper.assertTrue(LeafiaRBMKSeams.checkSurrounding(helper.getLevel(), column.getBlockPos()), "the base is enclosed by columns");
		helper.assertTrue(LeafiaRBMKSeams.checkSurrounding(helper.getLevel(), column.getBlockPos().above()), "and the block above by stone");

		column.heat = 20;
		IMixinTileEntityRBMKBase.leafia$setDamage(column, 1200);
		IMixinTileEntityRBMKBase.leafia$setJumpHeight(column, 0.001D);

		int damage = 1200;
		double jump = 0.001D;
		boolean falling = false;
		float speed = 0;
		boolean rose = false, fell = false, landed = false;

		for(int step = 0; step < 200 && !landed; step++) {

			damage = Math.max(damage - 1, 0); // 20 heat is under the rating

			if(!falling) {
				int d = (int) (Math.pow(damage / 1200D, 0.5) * 100);
				jump = jump + d * 0.0005D;
				if(jump > d * 0.005D) {
					jump = d * 0.005D;
					falling = true;
				}
				rose = true;
			} else if(jump > 0) {
				speed = speed + 1 * 0.05F;
				jump = Math.max(jump - speed, 0);
				fell = true;
			} else {
				jump = 0;
				speed = 0;
				falling = false;
				landed = true;
			}

			LeafiaRBMKSeams.tickColumn(column);

			helper.assertTrue(IMixinTileEntityRBMKBase.leafia$jumpHeight(column) == jump,
					"step " + step + ": height " + IMixinTileEntityRBMKBase.leafia$jumpHeight(column) + ", upstream " + jump);
			helper.assertTrue(LeafiaRBMKSeams.isFalling(column) == falling, "step " + step + ": falling " + falling);
			helper.assertTrue(LeafiaRBMKSeams.downwardSpeed(column) == speed, "step " + step + ": speed " + speed);
		}

		helper.assertTrue(rose && fell && landed, "the lid rose, fell and landed (" + rose + fell + landed + ")");
		helper.assertTrue(!column.getExtraData().contains(IMixinTileEntityRBMKBase.KEY_JUMP), "a landed lid carries no height");
		helper.succeed();
	}

	/** A column whose block above has an open side never moves its lid: upstream's guard against showing the seam. */
	@GameTest(template = EMPTY)
	public static void anExposedLidNeverMoves(GameTestHelper helper) {

		BlockEntityRBMKBase column = enclosedColumn(helper, false);
		helper.assertTrue(!LeafiaRBMKSeams.checkSurrounding(helper.getLevel(), column.getBlockPos().above()), "the block above is open");

		column.heat = 20;
		IMixinTileEntityRBMKBase.leafia$setDamage(column, 1200);
		IMixinTileEntityRBMKBase.leafia$setJumpHeight(column, 0.001D);

		for(int i = 0; i < 20; i++) LeafiaRBMKSeams.tickColumn(column);

		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$jumpHeight(column) == 0.001D, "the lid stays where it was");
		helper.assertTrue(IMixinTileEntityRBMKBase.leafia$getDamage(column) == 1180, "while the column still heals");
		helper.succeed();
	}

	/// THE MELTDOWN ///

	/**
	 * Damage past 1200 melts the reactor, and the centre of the pile becomes smoking debris of half the
	 * reactor's mean width - but only where the overhaul runs: an identical unarmed reactor melts the same way
	 * (the counter is the overhaul's) and smokes nowhere.
	 */
	@GameTest(template = EMPTY)
	public static void fullDamageMeltsTheReactorAndItsCentreSmokes(GameTestHelper helper) {

		BlockEntityRBMKBase origin = place(helper, ModBlocks.rbmk_blank, new BlockPos(1, 1, 1));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 1));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(3, 1, 1));
		BlockEntityRBMKBase unarmed = place(helper, ModBlocks.rbmk_blank, new BlockPos(1, 1, 3));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(2, 1, 3));
		place(helper, ModBlocks.rbmk_blank, new BlockPos(3, 1, 3));

		BlockPos at = origin.getBlockPos();
		LeafiaRBMKSeams.ARMED.add(at);
		try {
			origin.heat = 2500;
			IMixinTileEntityRBMKBase.leafia$setDamage(origin, 1199);
			LeafiaRBMKSeams.tickColumn(origin); // +2: 1201 > 1200
		} finally {
			LeafiaRBMKSeams.ARMED.remove(at);
		}
		unarmed.heat = 2500;
		IMixinTileEntityRBMKBase.leafia$setDamage(unarmed, 1199);
		LeafiaRBMKSeams.tickColumn(unarmed);

		helper.assertTrue(origin.isRemoved() && unarmed.isRemoved(), "both reactors melted down");

		RBMKDebrisSmokeTE smoke = null;
		int smokes = 0;
		for(int y = 1; y < 5; y++) {
			for(int x = 1; x <= 3; x++) {
				for(int z = 1; z <= 3; z += 2) {
					if(helper.getBlockState(new BlockPos(x, y, z)).is(LeafiaBlocks.pribris_smoke.get())) {
						smokes++;
						helper.assertTrue(x == 2 && z == 1, "the smoke is at the armed reactor's centre column, found it at " + x + "," + y + "," + z);
						smoke = (RBMKDebrisSmokeTE) helper.getBlockEntity(new BlockPos(x, y, z));
					}
				}
			}
		}
		helper.assertTrue(smokes == 1, "exactly one smoking block, found " + smokes);
		helper.assertTrue(smoke.getScale() == 0.5F, "three columns in a row: ((2 + 0) / 2) / 2 = 0.5, got " + smoke.getScale());

		helper.succeed();
	}

	/** The scan for the pile: the first RBMK debris or corium down the centre column, skipping anything else. */
	@GameTest(template = EMPTY)
	public static void theSmokeTakesTheTopOfTheDebrisAndSkipsOtherBlocks(GameTestHelper helper) {

		BlockEntityRBMKBase origin = place(helper, ModBlocks.rbmk_blank, new BlockPos(0, 1, 0));
		helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.pribris.get());
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.pribris_burning.get());
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.corium_block.get());

		BlockPos c = helper.absolutePos(new BlockPos(2, 1, 2));
		LeafiaRBMKSeams.placeSmoke(helper.getLevel(), origin.getBlockPos(), c.getX() - 2, c.getX() + 2, c.getZ() - 1, c.getZ() + 1, c.getX(), c.getZ());

		helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 3, 2));
		helper.assertBlockPresent(LeafiaBlocks.pribris_smoke.get(), new BlockPos(2, 2, 2));
		helper.assertBlockPresent(ModBlocks.pribris.get(), new BlockPos(2, 1, 2));
		RBMKDebrisSmokeTE smoke = (RBMKDebrisSmokeTE) helper.getBlockEntity(new BlockPos(2, 2, 2));
		helper.assertTrue(smoke.getScale() == 1.5F, "(4 + 2) / 2 / 2 = 1.5, got " + smoke.getScale());

		BlockPos k = helper.absolutePos(new BlockPos(4, 1, 4));
		LeafiaRBMKSeams.placeSmoke(helper.getLevel(), origin.getBlockPos(), k.getX(), k.getX(), k.getZ(), k.getZ(), k.getX(), k.getZ());
		helper.assertBlockPresent(LeafiaBlocks.pribris_smoke.get(), new BlockPos(4, 1, 4));
		helper.assertTrue(((RBMKDebrisSmokeTE) helper.getBlockEntity(new BlockPos(4, 1, 4))).getScale() == 0F, "corium counts too, and a one-column reactor has no width");

		helper.succeed();
	}

	/** The smoking debris: what it is, what it saves, how often it puffs and how long a puff lives. */
	@GameTest(template = EMPTY)
	public static void theSmokingDebrisIsRbmkDebrisAndPuffsOnUpstreamsClock(GameTestHelper helper) {

		Block block = LeafiaBlocks.pribris_smoke.get();
		helper.assertTrue(block instanceof RBMKDebris, "it is RBMK debris, so the digamma debris eats it like the rest");
		helper.assertTrue(block.defaultDestroyTime() == 50.0F, "hardness 50");
		helper.assertTrue(block.getExplosionResistance() == 3.6E10F, "resistance 6E10 x 0.6");
		helper.assertTrue(block.defaultBlockState().requiresCorrectToolForDrops(), "it needs a tool, as the base mod's pribris");
		helper.assertTrue(LeafiaBlockEntities.PRIBRIS_SMOKE.get().isValid(block.defaultBlockState()), "and holds its block entity");

		helper.setBlock(new BlockPos(1, 1, 1), block);
		RBMKDebrisSmokeTE te = (RBMKDebrisSmokeTE) helper.getBlockEntity(new BlockPos(1, 1, 1));
		te.setScale(2.5F);
		CompoundTag saved = te.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.getFloat("scale") == 2.5F, "the size is saved as upstream's scale");

		// timer++ > sqrt(scale): a puff every floor(sqrt) + 2 ticks
		helper.assertTrue(!RBMKDebrisSmokeTE.puffs(0, 1F) && !RBMKDebrisSmokeTE.puffs(1, 1F) && RBMKDebrisSmokeTE.puffs(2, 1F), "scale 1: every third tick");
		helper.assertTrue(!RBMKDebrisSmokeTE.puffs(0, 0.25F) && RBMKDebrisSmokeTE.puffs(1, 0.25F), "scale 0.25: every other tick");
		helper.assertTrue(!RBMKDebrisSmokeTE.puffs(3, 16F) && !RBMKDebrisSmokeTE.puffs(4, 16F) && RBMKDebrisSmokeTE.puffs(5, 16F), "scale 16: every sixth tick");
		// life: (int)(100 s) + rand((int)(50 s)) + 1, s = scale^0.75
		helper.assertTrue(RBMKDebrisSmokeTE.lifeBase(16F) == 800 && RBMKDebrisSmokeTE.lifeSpread(16F) == 400, "scale 16: 800 + rand(400) + 1");
		helper.assertTrue(RBMKDebrisSmokeTE.lifeBase(1F) == 100 && RBMKDebrisSmokeTE.lifeSpread(1F) == 50, "scale 1: 100 + rand(50) + 1");
		helper.succeed();
	}

	/// THE FUEL CHANNEL ///

	/**
	 * A fuel channel melts by damage and never by heat; past full damage it reads {@code -100}; with meltdowns
	 * disabled the dial's vent keeps upstream's threshold. Unarmed, every answer is the base mod's.
	 */
	@GameTest(template = EMPTY)
	public static void aFuelChannelMeltsByDamageNotHeat(GameTestHelper helper) {

		BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, ModBlocks.rbmk_rod, new BlockPos(2, 1, 2));
		BlockPos at = rod.getBlockPos();

		helper.assertTrue(LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500) == 1500, "unarmed, upstream's heat passes through");
		helper.assertTrue(!LeafiaRBMKSeams.HOOK.preventOverheatMeltdown(rod), "unarmed, the heat meltdown stands");

		GameRules.BooleanValue disabled = helper.getLevel().getGameRules().getRule(NtmGameRules.booleanKey(RBMKKeys.KEY_DISABLE_MELTDOWNS));
		boolean before = disabled.get();
		LeafiaRBMKSeams.ARMED.add(at);
		try {
			disabled.set(false, null);
			helper.assertTrue(LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500) == Double.POSITIVE_INFINITY, "armed, never by heat");
			helper.assertTrue(LeafiaRBMKSeams.HOOK.preventOverheatMeltdown(rod), "and the heat meltdown does nothing");

			IMixinTileEntityRBMKBase.leafia$setDamage(rod, 1201);
			helper.assertTrue(LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500) == -100, "past full damage it reads -100");

			IMixinTileEntityRBMKBase.leafia$setDamage(rod, 0);
			disabled.set(true, null);
			helper.assertTrue(LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500) == 1500, "meltdowns off: upstream's threshold for the vent");
		} finally {
			disabled.set(before, null);
			LeafiaRBMKSeams.ARMED.remove(at);
		}

		helper.succeed();
	}

	/** The switch, not only the arming: one synchronous call with it on, then off again. */
	@GameTest(template = EMPTY)
	public static void theSwitchTurnsTheHookOn(GameTestHelper helper) {

		BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, ModBlocks.rbmk_rod, new BlockPos(2, 1, 2));
		boolean before = LeafiaFeatures.rbmkOverhaul;
		double on;
		try {
			LeafiaFeatures.rbmkOverhaul = true;
			on = LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500);
		} finally {
			LeafiaFeatures.rbmkOverhaul = before;
		}
		helper.assertTrue(on == Double.POSITIVE_INFINITY, "with the switch on the hook acts at any position");
		helper.assertTrue(LeafiaRBMKSeams.HOOK.meltdownHeat(rod, 1500) == 1500, "and with it off (the default) it does not");
		helper.succeed();
	}

	/** The four fuels, their colours, and the index the server writes for the client's renderer. */
	@GameTest(template = EMPTY)
	public static void fourFuelsGlowTheirOwnCherenkovColour(GameTestHelper helper) {

		helper.assertTrue(LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_leaus.get()) == 1
				&& LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_heaus.get()) == 1, "both australium fuels are gold");
		helper.assertTrue(LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_balefire_gold.get()) == 2
				&& LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_flashlead.get()) == 2, "both antihydrogen fuels are violet");
		helper.assertTrue(LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_balefire.get()) == 3, "balefire is green");
		helper.assertTrue(LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_drx.get()) == 4, "digamma is orange");
		helper.assertTrue(LeafiaRBMKSeams.cherenkovIndex(ModItems.rbmk_fuel_ueu.get()) == 0, "any other fuel keeps the base colour");

		float[][] want = { { 1F, 0.9F, 0F }, { 0.6F, 0F, 1F }, { 0.25F, 1F, 0F }, { 1F, 0.25F, 0F } };
		for(int i = 0; i < 4; i++) {
			float[] got = LeafiaRBMKSeams.CHERENKOV[i + 1];
			helper.assertTrue(got[0] == want[i][0] && got[1] == want[i][1] && got[2] == want[i][2], "colour " + (i + 1));
		}

		BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, ModBlocks.rbmk_rod, new BlockPos(2, 1, 2));
		rod.slots[0] = new ItemStack(ModItems.rbmk_fuel_leaus.get());
		BlockPos at = rod.getBlockPos();
		LeafiaRBMKSeams.HOOK.onServerTick(rod);
		helper.assertTrue(LeafiaRBMKSeams.cherenkovOf(rod) == null, "unarmed, nothing is written");
		LeafiaRBMKSeams.ARMED.add(at);
		try {
			LeafiaRBMKSeams.HOOK.onServerTick(rod);
		} finally {
			LeafiaRBMKSeams.ARMED.remove(at);
		}
		float[] colour = LeafiaRBMKSeams.cherenkovOf(rod);
		helper.assertTrue(colour != null && colour[0] == 1F && colour[1] == 0.9F && colour[2] == 0F, "armed, the rod's tag asks for gold");
		rod.slots[0] = ItemStack.EMPTY;
		helper.succeed();
	}

	/// THE AUTOMATIC RODS, THE AZ-5 AND THE CONSOLE ///

	/**
	 * The AZ-5's extra order shuts off every automatic rod in the console's 15x15 grid and no further, passes
	 * the packet on to the base mod, and marks those rods on the console; a shut-off rod drives to 0 whatever
	 * its curve says; "Acknowledge" frees one rod and "Acknowledge All" every one joined to it.
	 */
	@GameTest(template = EMPTY16)
	public static void theAz5ShutsTheAutoRodsOffUntilAcknowledged(GameTestHelper helper) {

		helper.setBlock(new BlockPos(0, 1, 15), ModBlocks.rbmk_console.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, RBMKBase.DIR_NO_LID));
		BlockEntityRBMKConsole console = (BlockEntityRBMKConsole) helper.getBlockEntity(new BlockPos(0, 1, 15));
		BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));
		console.setTarget(target.getX(), target.getY(), target.getZ());

		BlockEntityRBMKControlAuto a = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto, new BlockPos(1, 1, 1));
		BlockEntityRBMKControlAuto b = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto, new BlockPos(2, 1, 1));
		BlockEntityRBMKControlAuto apart = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto, new BlockPos(1, 1, 3));
		BlockEntityRBMKControlAuto edge = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto, new BlockPos(8, 1, 8));
		BlockEntityRBMKControlAuto beyond = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto, new BlockPos(9, 1, 1));

		List<BlockPos> armed = new ArrayList<>();
		armed.add(console.getBlockPos());
		for(BlockEntityRBMKBase be : new BlockEntityRBMKBase[] { a, b, apart, edge, beyond }) armed.add(be.getBlockPos());
		LeafiaRBMKSeams.ARMED.addAll(armed);
		try {
			CompoundTag az5 = new CompoundTag();
			az5.putDouble("level", 0);
			az5.putBoolean(LeafiaRBMKSeams.KEY_AZ5_ALL, true);
			helper.assertTrue(!LeafiaRBMKSeams.HOOK.receiveControl(console, az5), "the console still reads the AZ-5's own packet");

			helper.assertTrue(IMixinTileEntityRBMKControlAuto.leafia$getScrammed(a) && IMixinTileEntityRBMKControlAuto.leafia$getScrammed(b)
					&& IMixinTileEntityRBMKControlAuto.leafia$getScrammed(apart), "the rods in the grid are shut off");
			helper.assertTrue(IMixinTileEntityRBMKControlAuto.leafia$getScrammed(edge), "out to seven blocks on both axes");
			helper.assertTrue(!IMixinTileEntityRBMKControlAuto.leafia$getScrammed(beyond), "and not at eight");

			// a shut-off rod drives to 0 whatever its curve says
			a.levelLower = 80;
			a.levelUpper = 80;
			helper.assertTrue(LeafiaRBMKSeams.HOOK.autoTargetLevel(a, 0.8D) == 0D, "a shut-off rod's target is 0");
			a.updateEntity();
			helper.assertTrue(a.targetLevel == 0D, "and the rod's own tick sets it so, got " + a.targetLevel);
			helper.assertTrue(LeafiaRBMKSeams.HOOK.autoTargetLevel(beyond, 0.8D) == 0.8D, "a running rod keeps its curve");

			// the console marks them
			CompoundTag data = new CompoundTag();
			LeafiaRBMKSeams.HOOK.addConsoleData(a, data);
			RBMKColumn column = new RBMKColumn(ColumnType.CONTROL_AUTO, data);
			helper.assertTrue(IMixinRBMKColumn$ControlColumn.leafia$getExclamation(column), "the console's scan carries the mark");
			List<String> stats = new ArrayList<>();
			LeafiaRBMKSeams.HOOK.addFancyStats(column, stats);
			helper.assertTrue(stats.equals(List.of(net.minecraft.ChatFormatting.GOLD + "Rod is shut off")), "and its tooltip says so in gold, got " + stats);
			CompoundTag running = new CompoundTag();
			LeafiaRBMKSeams.HOOK.addConsoleData(beyond, running);
			helper.assertTrue(running.isEmpty(), "a running rod adds nothing to its scan");

			// acknowledge one, then all
			CompoundTag one = new CompoundTag();
			one.putBoolean(LeafiaRBMKSeams.KEY_ACKNOWLEDGE, false);
			helper.assertTrue(LeafiaRBMKSeams.HOOK.receiveControl(a, one), "the acknowledgement is consumed");
			helper.assertTrue(!IMixinTileEntityRBMKControlAuto.leafia$getScrammed(a) && IMixinTileEntityRBMKControlAuto.leafia$getScrammed(b),
					"Acknowledge frees only its own rod");

			CompoundTag all = new CompoundTag();
			all.putBoolean(LeafiaRBMKSeams.KEY_ACKNOWLEDGE, true);
			LeafiaRBMKSeams.HOOK.receiveControl(a, all);
			helper.assertTrue(!IMixinTileEntityRBMKControlAuto.leafia$getScrammed(b), "Acknowledge All frees the rod joined to it");
			helper.assertTrue(IMixinTileEntityRBMKControlAuto.leafia$getScrammed(apart), "but not one in another reactor");
		} finally {
			LeafiaRBMKSeams.ARMED.removeAll(armed);
		}

		// unarmed, nothing of it happens
		CompoundTag az5 = new CompoundTag();
		az5.putBoolean(LeafiaRBMKSeams.KEY_AZ5_ALL, true);
		LeafiaRBMKSeams.HOOK.receiveControl(console, az5);
		helper.assertTrue(!IMixinTileEntityRBMKControlAuto.leafia$getScrammed(a), "unarmed, the AZ-5's order shuts nothing off");
		CompoundTag free = new CompoundTag();
		free.putBoolean(LeafiaRBMKSeams.KEY_ACKNOWLEDGE, true);
		helper.assertTrue(!LeafiaRBMKSeams.HOOK.receiveControl(apart, free), "unarmed, the acknowledgement is not consumed");
		helper.assertTrue(IMixinTileEntityRBMKControlAuto.leafia$getScrammed(apart), "and frees nothing");
		helper.assertTrue(LeafiaRBMKSeams.HOOK.autoTargetLevel(apart, 0.8D) == 0.8D, "unarmed, a flagged rod follows its curve");

		helper.succeed();
	}

	/// THE DEBRIS ///

	/**
	 * A torn-out block lets the solid blocks above it fall, the first with the debris' velocity and a
	 * fall time of 1; the sixth is deleted instead; nothing falls onto a solid block; smoking debris is never
	 * torn out; and unarmed the handler leaves everything to the base mod.
	 */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void tornOutBlocksDropWhatIsAboveThem(GameTestHelper helper) {

		// a stone over air: falls
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
		BlockPos low = helper.absolutePos(new BlockPos(1, 2, 1));
		Vec3 vel = new Vec3(0.1, 0.5, -0.2);
		LeafiaRBMKSeams.fallBlock(helper.getLevel(), low, vel, 0);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 2, 1));
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6);
		List<FallingBlockEntity> falling = helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, box);
		helper.assertTrue(falling.size() == 1, "one falling block, got " + falling.size());
		helper.assertTrue(falling.get(0).time == 1 && falling.get(0).getDeltaMovement().equals(vel), "with a fall time of 1 and the debris' velocity");

		// the sixth deletes
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
		LeafiaRBMKSeams.fallBlock(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 1)), null, LeafiaRBMKSeams.FALL_LIMIT);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 1));
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, box).size() == 1, "past the fifth it is deleted, not dropped");

		// over a solid block nothing happens
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
		LeafiaRBMKSeams.fallBlock(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), vel, 0);
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));

		// smoking debris is never torn out; unarmed the handler does nothing
		helper.setBlock(new BlockPos(1, 1, 3), LeafiaBlocks.pribris_smoke.get());
		BlockPos smoke = helper.absolutePos(new BlockPos(1, 1, 3));
		EntityRBMKDebris piece = new EntityRBMKDebris(helper.getLevel(), smoke.getX(), smoke.getY() + 3, smoke.getZ(), DebrisType.LID);
		helper.assertTrue(!LeafiaRBMKSeams.SMASH.smash(piece, helper.getLevel(), smoke, helper.getLevel().getBlockState(smoke)), "unarmed, the base mod decides");
		LeafiaRBMKSeams.ARMED.add(smoke);
		try {
			helper.assertTrue(LeafiaRBMKSeams.SMASH.smash(piece, helper.getLevel(), smoke, helper.getLevel().getBlockState(smoke)), "armed, smoking debris is kept");
		} finally {
			LeafiaRBMKSeams.ARMED.remove(smoke);
		}

		// next tick the block above the fallen one follows
		TickWindow.sequence(helper).thenIdle(1).thenExecute(() -> {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 3, 1));
			for(FallingBlockEntity e : helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, box)) e.discard();
		}).thenSucceed();
	}

	/// THE PARTICLE KINDS ///

	/** The two RBMK kinds are appended after the DFC's nuke, and the jump carries its column's position. */
	@GameTest(template = EMPTY)
	public static void theRbmkParticleKindsAreAppendedAndCarryTheColumn(GameTestHelper helper) {

		helper.assertTrue(LeafiaParticlePacket.idOf(new PinkRBMK()) == 9, "PinkRBMK is id 9");
		helper.assertTrue(LeafiaParticlePacket.idOf(new JumpingRBMKParticle(BlockPos.ZERO)) == 10, "JumpingRBMKParticle is id 10");

		BlockPos column = new BlockPos(123, -45, 6789);
		RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		LeafiaParticlePacket.STREAM_CODEC.encode(wire, new JumpingRBMKParticle(column).packet(Vec3.ZERO, new Vec3(0, 1, 0)));
		LeafiaParticlePacket decoded = LeafiaParticlePacket.STREAM_CODEC.decode(wire);
		helper.assertTrue(wire.readableBytes() == 0, "the codec consumes what it wrote");
		helper.assertTrue(decoded.particle() instanceof JumpingRBMKParticle j && column.equals(j.rbmkPos), "the column survives the wire");

		RegistryFriendlyByteBuf pink = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		LeafiaParticlePacket.STREAM_CODEC.encode(pink, new PinkRBMK().packet(Vec3.ZERO, Vec3.ZERO));
		helper.assertTrue(LeafiaParticlePacket.STREAM_CODEC.decode(pink).particle() instanceof PinkRBMK, "the pink cloud has no body");

		helper.assertTrue(LeafiaSounds.rbmkLid.getId().equals(LeafiaRef.rl("block.rbmklid")), "the lid lands with NTM-CE's rbmkLid, as leafia:block.rbmklid");
		helper.succeed();
	}
}
