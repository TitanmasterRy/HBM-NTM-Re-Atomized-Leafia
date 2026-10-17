package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.bomb.BlockEntityLandmine;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.bomb.Landmine;
import com.hbm.blocks.generic.BlockDeadPlant;
import com.hbm.blocks.generic.BlockSellafield;
import com.hbm.config.ServerConfig;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.projectile.EntityMeteor;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemMissile;
import com.hbm.saveddata.TomSaveData;
import com.hbm.slices.MissileSystem;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.bomb.chud.NukeChudBlock;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem.CustomNukeMissileInventory;
import com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntity;
import com.leafia.contents.miscellanous.diverter.DiverterBlock;
import com.leafia.contents.miscellanous.diverter.DiverterTE;
import com.leafia.database.AirbustMissiles;
import com.leafia.database.ImpactSeismic;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.eventbuses.LeafiaStrikeListener;
import com.leafia.init.LeafiaEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaMeteorCrater;
import com.leafia.overwrite_contents.LeafiaStrikeSeams;
import com.leafia.overwrite_contents.interfaces.IMixinEntityMissileBaseNT;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityLandmine;
import com.leafia.overwrite_contents.interfaces.IMixinTomSaveData;
import com.leafia.savedata.PlayerDeathsSavedData;
import com.leafia.unsorted.explosion_vnt.EntityProcessorLandmine;
import com.leafia.unsorted.explosion_vnt.EntityProcessorPlayerLandmine;

import io.netty.buffer.Unpooled;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EXPL-C (D-3022): the meteor, missile, landmine and impact seams, the Meteor Protection Beacon, the custom nuke
 * missile and the Fat Chud. Every world-touching test runs at a random far spot and clears what it made; every switch
 * and static a test sets is put back in the same body.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaExplCTests {

	private static final String EMPTY = "empty";

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

	/** Answers {@code nextInt} from a script and records every bound. */
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

	/** A custom nuke missile that records its impact and its shoot-down instead of going off. Never added to a level. */
	static final class Probe extends CustomNukeMissileEntity {
		int impacts;
		int kills;

		Probe(Level level) {
			super(LeafiaEntities.CUSTOM_NUKE_MISSILE.get(), level);
		}

		@Override
		public void onMissileImpact(HitResult mop) {
			impacts++;
		}

		@Override
		public void killMissile() {
			kills++;
		}
	}

	private static boolean bombs() {
		return LeafiaFeatures.addonBombs && LeafiaItems.missile_customnuke != null;
	}

	// =================================================================== landmines (MixinLandmine, MixinTileEntityLandmine, MixinModBlocks)

	/** The two processors' numbers: x4.5 then halved and capped at 11 at or under 22; a flat 600,000. */
	@GameTest(template = EMPTY)
	public static void theLandmineProcessorsAreUpstreams(GameTestHelper helper) {
		helper.assertTrue(EntityProcessorLandmine.DAMAGE_MULT == 4.5F && EntityProcessorLandmine.HIT_MULT == 0.5F
				&& EntityProcessorLandmine.CAP_BELOW == 22F && EntityProcessorLandmine.CAP == 11F, "the constants");
		helper.assertTrue(EntityProcessorLandmine.hit(10F) == 5F, "10 halves to 5: " + EntityProcessorLandmine.hit(10F));
		helper.assertTrue(EntityProcessorLandmine.hit(30F) == 11F && EntityProcessorLandmine.hit(44F) == 11F, "15 and 22 cap at 11");
		helper.assertTrue(EntityProcessorLandmine.hit(46F) == 23F, "23 is over 22 and stays: " + EntityProcessorLandmine.hit(46F));
		helper.assertTrue(new EntityProcessorLandmine(0.5, 10F).getFixedDamage() == 45F, "x4.5 at construction");
		helper.assertTrue(new EntityProcessorPlayerLandmine(0.5, 10F).getFixedDamage() == 600000F, "a flat 600,000");

		// the detonation hook reads the base mod's processor back (D-2207) and rebuilds it as the addon's, carrying what it found
		Landmine ap = (Landmine) ModBlocks.mine_ap.get();
		ExplosionVNT apBlast = blast(helper, new EntityProcessorCrossSmooth(0.5, ServerConfig.MINE_AP_DAMAGE.get()).setupPiercing(5F, 0.2F));
		EntityProcessorCrossSmooth wild = LeafiaStrikeSeams.processor(ap, false, (EntityProcessorCrossSmooth) apBlast.getEntityProcessor());
		helper.assertTrue(wild instanceof EntityProcessorLandmine && wild.getFixedDamage() == ServerConfig.MINE_AP_DAMAGE.get() * 4.5F
				&& wild.getNodeDist() == 0.5 && wild.getPierceDT() == 5F && wild.getPierceDR() == 0.2F, "AP, worldgen: the base's node distance and piercing, damage x4.5");
		EntityProcessorCrossSmooth placed = LeafiaStrikeSeams.processor((Landmine) ModBlocks.mine_he.get(), true,
				new EntityProcessorCrossSmooth(1, ServerConfig.MINE_HE_DAMAGE.get()).setupPiercing(15F, 0.2F));
		helper.assertTrue(placed instanceof EntityProcessorPlayerLandmine && placed.getNodeDist() == 1 && placed.getPierceDT() == 15F, "HE, player");
		EntityProcessorCrossSmooth fat = LeafiaStrikeSeams.processor((Landmine) ModBlocks.mine_fat.get(), false,
				new EntityProcessorCrossSmooth(2, ServerConfig.MINE_NUKE_DAMAGE.get()));
		helper.assertTrue(fat.getFixedDamage() == ServerConfig.MINE_NUKE_DAMAGE.get() * 4.5F && fat.getNodeDist() == 2 && fat.getPierceDT() == 0F, "fat");
		helper.assertTrue(LeafiaStrikeSeams.processor((Landmine) ModBlocks.mine_shrap.get(), false,
				new EntityProcessorCrossSmooth(0.5, ServerConfig.MINE_SHRAP_DAMAGE.get())).getNodeDist() == 0.5, "shrapnel");
		helper.assertTrue(LeafiaStrikeSeams.processor((Landmine) ModBlocks.mine_naval.get(), false,
				new EntityProcessorCrossSmooth(0.5, ServerConfig.MINE_NAVAL_DAMAGE.get()).setupPiercing(5F, 0.2F)).getPierceDR() == 0.2F, "naval");

		// through the hook itself: the explosion's processor is replaced by one carrying the old one's numbers; the switch off leaves it alone
		boolean was = LeafiaFeatures.landmineOverhaul;
		try {
			LeafiaFeatures.landmineOverhaul = false;
			ExplosionVNT off = blast(helper, new EntityProcessorCrossSmooth(0.5, 3F).setupPiercing(5F, 0.2F));
			Object before = off.getEntityProcessor();
			LeafiaStrikeSeams.LANDMINE_BLAST.configure(ap, helper.getLevel(), BlockPos.ZERO, null, off);
			helper.assertTrue(off.getEntityProcessor() == before, "switched off, the base's processor stays");
			LeafiaFeatures.landmineOverhaul = true;
			LeafiaStrikeSeams.LANDMINE_BLAST.configure(ap, helper.getLevel(), BlockPos.ZERO, null, off);
			helper.assertTrue(off.getEntityProcessor() instanceof EntityProcessorLandmine swapped && swapped.getNodeDist() == 0.5 && swapped.getPierceDT() == 5F
					&& swapped.getFixedDamage() == 3F * 4.5F, "on, it is the addon's with the old one's numbers");
			ExplosionVNT plain = blast(helper, new com.hbm.explosion.vanillant.standard.EntityProcessorCross(1));
			Object kept = plain.getEntityProcessor();
			LeafiaStrikeSeams.LANDMINE_BLAST.configure(ap, helper.getLevel(), BlockPos.ZERO, null, plain);
			helper.assertTrue(plain.getEntityProcessor() == kept, "a processor that is not a smooth cross is left alone");
		} finally {
			LeafiaFeatures.landmineOverhaul = was;
		}
		helper.succeed();
	}

	private static ExplosionVNT blast(GameTestHelper helper, com.hbm.explosion.vanillant.interfaces.IEntityProcessor processor) {
		ExplosionVNT vnt = new ExplosionVNT(helper.getLevel(), 0, 0, 0, 3F);
		vnt.setEntityProcessor(processor);
		return vnt;
	}

	/** The AP mine's range is a quarter and the priming click half a volume unit with the overhaul; nothing changes without it. */
	@GameTest(template = EMPTY)
	public static void theLandmineOverhaulQuartersTheAPRangeAndQuietsTheClick(GameTestHelper helper) {
		boolean was = LeafiaFeatures.landmineOverhaul;
		Landmine ap = (Landmine) ModBlocks.mine_ap.get();
		Landmine he = (Landmine) ModBlocks.mine_he.get();
		try {
			LeafiaFeatures.landmineOverhaul = false;
			LeafiaStrikeSeams.syncLandmineVolume();
			helper.assertTrue(ap.triggerRange() == ap.range && BlockEntityLandmine.getPrimeVolume() == 3.0F, "off: the base mod's range and click");
			LeafiaFeatures.landmineOverhaul = true;
			LeafiaStrikeSeams.syncLandmineVolume();
			helper.assertTrue(ap.triggerRange() == ap.range / 4, "on: the AP mine's range / 4: " + ap.triggerRange());
			helper.assertTrue(he.triggerRange() == he.range, "the HE mine's is left alone");
			helper.assertTrue(BlockEntityLandmine.getPrimeVolume() == 0.5F, "the click at 0.5");
		} finally {
			LeafiaFeatures.landmineOverhaul = was;
			LeafiaStrikeSeams.syncLandmineVolume();
		}
		helper.succeed();
	}

	/** A mine a living thing places is marked, the mark saves with the mine, and a mine nobody placed is not. */
	@GameTest(template = EMPTY)
	public static void aPlacedMineRemembersItWasPlaced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		BlockPos a = far, b = far.offset(3, 0, 0);
		boolean safe = Landmine.safeMode;
		try {
			for(BlockPos p : new BlockPos[] {a, b}) {
				level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
				level.setBlock(p, ModBlocks.mine_ap.get().defaultBlockState(), 2 | 16);
			}
			Zombie zombie = EntityType.ZOMBIE.create(level);
			NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, a), Blocks.STONE.defaultBlockState(), zombie));
			NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, b), Blocks.STONE.defaultBlockState(), null));
			BlockEntity mineA = level.getBlockEntity(a), mineB = level.getBlockEntity(b);
			helper.assertTrue(IMixinTileEntityLandmine.getPlayerPlaced(mineA), "placed by a living thing");
			helper.assertTrue(!IMixinTileEntityLandmine.getPlayerPlaced(mineB), "placed by nothing");
			CompoundTag saved = mineA.saveWithFullMetadata(level.registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(a, mineA.getBlockState(), saved, level.registryAccess());
			helper.assertTrue(loaded != null && IMixinTileEntityLandmine.getPlayerPlaced(loaded), "and it saves: " + saved);
		} finally {
			Landmine.safeMode = true;
			try {
				clear(level, far.offset(-1, -1, -1), far.offset(4, 1, 1));
			} finally {
				Landmine.safeMode = safe;
			}
		}
		helper.succeed();
	}

	/**
	 * End to end, three anti-personnel mines at a far spot, an iron golem (100 health) 1.5 out from each: without the
	 * overhaul the base mod's 10 at most; with it, a player-placed mine kills and a worldgen one takes at most 22.5.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void aPlayerPlacedMineKillsAndAWorldgenOneIsCapped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		BlockPos[] mines = {far.offset(-16, 0, 0), far, far.offset(16, 0, 0)};
		IronGolem[] golems = new IronGolem[3];
		for(int i = 0; i < 3; i++) {
			BlockPos m = mines[i];
			for(int x = -2; x <= 2; x++) for(int z = -2; z <= 3; z++) level.setBlock(m.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(m, ModBlocks.mine_ap.get().defaultBlockState(), 2 | 16);
			golems[i] = EntityType.IRON_GOLEM.create(level);
			golems[i].moveTo(m.getX() + 0.5, m.getY(), m.getZ() + 2.0, 0, 0);
			golems[i].setNoAi(true);
			level.addFreshEntity(golems[i]);
		}
		IMixinTileEntityLandmine.setPlayerPlaced(level.getBlockEntity(mines[1]));
		Runnable cleanup = () -> {
			boolean safe = Landmine.safeMode;
			Landmine.safeMode = true;
			try {
				for(BlockPos m : mines) clear(level, m.offset(-3, -1, -3), m.offset(3, 2, 4));
				for(IronGolem g : golems) g.discard();
			} finally {
				Landmine.safeMode = safe;
			}
		};
		helper.startSequence().thenWaitUntil(() -> {
			for(IronGolem g : golems)
				if(level.getEntitiesOfClass(IronGolem.class, g.getBoundingBox().inflate(1)).isEmpty()) helper.fail("waiting for the golems to be visible");
		}).thenExecute(() -> {
			boolean was = LeafiaFeatures.landmineOverhaul;
			try {
				Landmine ap = (Landmine) ModBlocks.mine_ap.get();
				LeafiaFeatures.landmineOverhaul = false;
				ap.explode(level, mines[0]);
				LeafiaFeatures.landmineOverhaul = true;
				ap.explode(level, mines[1]);
				ap.explode(level, mines[2]);
				helper.assertTrue(golems[0].isAlive() && golems[0].getHealth() >= 90F, "off, the base mod's 10 at most: " + golems[0].getHealth());
				helper.assertTrue(!golems[1].isAlive() || golems[1].getHealth() <= 0F, "a player's mine kills: " + golems[1].getHealth());
				helper.assertTrue(golems[2].isAlive() && golems[2].getHealth() >= 77.5F && golems[2].getHealth() < 100F,
						"a worldgen mine, x4.5 then halved: " + golems[2].getHealth());
			} finally {
				LeafiaFeatures.landmineOverhaul = was;
				cleanup.run();
			}
		}).thenSucceed();
	}

	// =================================================================== the custom nuke missile

	/** NTM-CE's sum over the base mod's table: one item a slot, the gates, the caps, euphemium never, saved to the stack. */
	@GameTest(template = EMPTY)
	public static void theCustomNukeMissileSumsNtmCesWay(GameTestHelper helper) {
		if(!bombs()) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		ItemStack box = new ItemStack(LeafiaItems.missile_customnuke.get());
		CustomNukeMissileInventory inv = new CustomNukeMissileInventory(box, level.registryAccess());
		for(int i = 0; i < 3; i++) inv.setStackInSlot(i, new ItemStack(Blocks.TNT));
		inv.setStackInSlot(3, new ItemStack(ModItems.ingot_u235.get()));
		helper.assertTrue(inv.tnt == 12F && inv.nuke == 0F, "TNT 12 is under 16, so no nuclear stage: " + inv.tnt + "/" + inv.nuke);
		inv.setStackInSlot(4, new ItemStack(Blocks.TNT));
		helper.assertTrue(inv.tnt == 16F && inv.nuke == 15F, "TNT 16 lets the nuclear stage in: " + inv.tnt + "/" + inv.nuke);
		helper.assertTrue(inv.getNukeAdj() == 23F, "15 + 16 / 2: " + inv.getNukeAdj());
		CompoundTag saved = CustomNukeMissileItem.inventoryTag(box);
		helper.assertTrue(saved.getFloat("tnt") == 16F && saved.getFloat("nuke") == 15F && saved.contains("Items"), "saved: " + saved);
		helper.assertTrue(inv.insertItem(5, new ItemStack(Blocks.TNT, 4), false).getCount() == 3, "one item a slot");
		inv.setStackInSlot(5, ItemStack.EMPTY);

		for(int i = 5; i < 8; i++) inv.setStackInSlot(i, new ItemStack(ModItems.ingot_u235.get()));
		inv.setStackInSlot(8, new ItemStack(ModBlocks.block_schrabidium.get()));
		inv.setStackInSlot(9, new ItemStack(ModItems.ingot_euphemium.get()));
		helper.assertTrue(inv.nuke == 60F && inv.schrab == 50F, "nuclear 60, schrabidium 50: " + inv.nuke + "/" + inv.schrab);
		helper.assertTrue(inv.euph == 0F && inv.sol == 0F, "no solinium stage in the base table, so no euphemium: " + inv.euph);

		CustomNukeMissileInventory reread = new CustomNukeMissileInventory(box, level.registryAccess());
		helper.assertTrue(reread.tnt == 16F && reread.nuke == 60F && reread.schrab == 50F, "read back from the stack");

		ItemStack full = new ItemStack(LeafiaItems.missile_customnuke.get());
		CustomNukeMissileInventory capped = new CustomNukeMissileInventory(full, level.registryAccess());
		for(int i = 0; i < CustomNukeMissileItem.SLOTS; i++) capped.setStackInSlot(i, new ItemStack(ModBlocks.det_charge.get()));
		helper.assertTrue(capped.tnt == 150F, "27 x 15 caps at 150: " + capped.tnt);
		helper.assertTrue(CustomNukeMissileItem.MAX_TNT == 150 && CustomNukeMissileItem.MAX_NUKE == 200 && CustomNukeMissileItem.MAX_HYDRO == 350
				&& CustomNukeMissileItem.MAX_BALE == 350 && CustomNukeMissileItem.MAX_DIRTY == 200 && CustomNukeMissileItem.MAX_SCHRAB == 250
				&& CustomNukeMissileItem.MAX_SOL == 350 && CustomNukeMissileItem.MAX_EUPH == 20, "NTM-CE's BombConfig caps");
		helper.succeed();
	}

	/** Every pad fires it through the provider, with the stack's stages; with no TNT, nothing. Its own air and shoot-down rules. */
	@GameTest(template = EMPTY)
	public static void aPadLaunchesTheCustomNukeMissile(GameTestHelper helper) {
		if(!bombs()) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos pad = new BlockPos(1000, 70, -2000);
		ItemStack empty = new ItemStack(LeafiaItems.missile_customnuke.get());
		helper.assertTrue(LeafiaStrikeSeams.CUSTOM_NUKE_PROVIDER.accepts(null, empty), "the provider takes the item");
		helper.assertTrue(!LeafiaStrikeSeams.CUSTOM_NUKE_PROVIDER.accepts(null, new ItemStack(ModItems.missile_nuclear.get())), "and no base missile");
		helper.assertTrue(LeafiaStrikeSeams.launch(level, pad, 1.0, empty, 0, 0) == null, "no TNT, no missile");

		ItemStack box = new ItemStack(LeafiaItems.missile_customnuke.get());
		CustomNukeMissileInventory inv = new CustomNukeMissileInventory(box, level.registryAccess());
		for(int i = 0; i < 4; i++) inv.setStackInSlot(i, new ItemStack(Blocks.TNT));
		inv.setStackInSlot(4, new ItemStack(ModItems.ingot_u235.get()));
		CustomNukeMissileEntity missile = LeafiaStrikeSeams.launch(level, pad, 1.0, box, 1200, -1800);
		helper.assertTrue(missile != null && missile.tnt == 16F && missile.nuke == 15F, "the stages ride along");
		helper.assertTrue(missile.targetX == 1200 && missile.targetZ == -1800 && missile.getX() == 1000.5 && missile.getY() == 71.0, "aimed from the pad: " + missile.position());

		helper.assertTrue(missile.shouldDetonateInAir(), "a nuclear stage bursts in the air");
		missile.schrab = 1;
		helper.assertTrue(!missile.shouldDetonateInAir(), "not with schrabidium");
		missile.schrab = 0;
		missile.nuke = 0;
		missile.tnt = 75;
		helper.assertTrue(missile.shouldDetonateInAir(), "TNT 75 is N2-like");
		missile.tnt = 74;
		helper.assertTrue(!missile.shouldDetonateInAir(), "TNT 74 is not");

		Probe probe = new Probe(level);
		probe.tnt = 74;
		helper.assertTrue(!probe.interactABMissile() && probe.impacts == 1, "shot down under 75 TNT, it goes off");
		probe.tnt = 75;
		helper.assertTrue(probe.interactABMissile() && probe.impacts == 1, "at 75 it scatters");

		missile.tnt = 16;
		missile.nuke = 15;
		missile.euph = 3;
		CompoundTag nbt = new CompoundTag();
		missile.saveWithoutId(nbt);
		helper.assertTrue(nbt.getFloat("custom_tnt") == 16F && nbt.getFloat("custom_nuke") == 15F && nbt.getFloat("custom_euph") == 3F, "upstream's keys: " + nbt);
		CustomNukeMissileEntity again = new CustomNukeMissileEntity(LeafiaEntities.CUSTOM_NUKE_MISSILE.get(), level);
		again.load(nbt);
		helper.assertTrue(again.tnt == 16F && again.nuke == 15F && again.euph == 3F, "read back");
		helper.succeed();
	}

	// =================================================================== airburst (MixinEntityMissileBaseNT, MixinEntityThrowableNT)

	/** Within 20 of the target and under 50 above the ground, an air-bursting missile goes off, once; a block hit breaks it up. */
	@GameTest(template = EMPTY)
	public static void anAirburstMissileGoesOffOverItsTarget(GameTestHelper helper) {
		if(!bombs()) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 100);
		int x = far.getX(), z = far.getZ();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		helper.assertTrue(LeafiaStrikeSeams.AIRBURST_REACH == 20 && LeafiaStrikeSeams.AIRBURST_HEIGHT == 50, "the window");

		Probe in = probe(level, x, ground + 49, z, x + 19, z, 1);
		LeafiaStrikeSeams.MISSILE.onFlightTick(in);
		helper.assertTrue(in.impacts == 1 && in.isRemoved(), "49 up, 19 off: it goes off and is gone");
		Probe high = probe(level, x, ground + 50, z, x + 19, z, 1);
		LeafiaStrikeSeams.MISSILE.onFlightTick(high);
		helper.assertTrue(high.impacts == 0 && !high.isRemoved(), "50 up: not yet");
		Probe wide = probe(level, x, ground + 10, z, x, z - 20, 1);
		LeafiaStrikeSeams.MISSILE.onFlightTick(wide);
		helper.assertTrue(wide.impacts == 0, "20 off: not");
		Probe schrab = probe(level, x, ground + 10, z, x, z, 1);
		schrab.schrab = 1;
		LeafiaStrikeSeams.MISSILE.onFlightTick(schrab);
		helper.assertTrue(schrab.impacts == 0, "a schrabidium warhead never bursts in the air");

		Probe hit = probe(level, x, ground + 10, z, x, z, 1);
		BlockHitResult block = new BlockHitResult(hit.position(), Direction.UP, hit.blockPosition(), false);
		helper.assertTrue(LeafiaStrikeSeams.AIRBURST_IMPACT.replace(hit, block) && hit.kills == 1, "hitting a block first, it is shot down instead");
		helper.assertTrue(!LeafiaStrikeSeams.AIRBURST_IMPACT.replace(schrab, block) && schrab.kills == 0, "a ground-burst one hits as usual");

		Probe low = probe(level, x, ground + 10, z, x, z, 0);
		low.tnt = 10;
		helper.assertTrue(LeafiaStrikeSeams.MISSILE.onShotDown(low) && low.impacts == 1, "shot down with 10 TNT: its warhead goes off, the scatter is skipped");
		helper.assertTrue(!LeafiaStrikeSeams.MISSILE.onShotDown(hit), "with a nuclear stage it scatters");
		helper.succeed();
	}

	private static Probe probe(Level level, int x, int y, int z, int tx, int tz, float nuke) {
		Probe p = new Probe(level);
		p.setup(x, y, z, tx, tz);
		p.nuke = nuke;
		return p;
	}

	/** The base mod's missiles are the addon's to end only with the switch; the list is upstream's less the N2 missile. */
	@GameTest(template = EMPTY)
	public static void baseMissilesAreGovernedOnlyWithTheSwitch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<net.minecraft.world.item.Item> list = AirbustMissiles.defaultAirDetonationMissiles;
		helper.assertTrue(list.equals(List.of(ModItems.missile_micro.get(), ModItems.missile_nuclear.get(), ModItems.missile_nuclear_cluster.get(),
				ModItems.missile_doomsday.get(), ModItems.missile_emp.get(), ModItems.missile_emp_strong.get())), "upstream's six, in order: " + list);
		EntityMissileBaseNT nuclear = MissileSystem.MISSILE_NUCLEAR.get().create(level);
		EntityMissileBaseNT generic = MissileSystem.MISSILE_GENERIC.get().create(level);
		helper.assertTrue(IMixinEntityMissileBaseNT.shouldDetonateInAir(nuclear) && !IMixinEntityMissileBaseNT.shouldDetonateInAir(generic), "nuclear bursts, HE does not");
		boolean was = LeafiaFeatures.missileOverhaul;
		try {
			LeafiaFeatures.missileOverhaul = false;
			helper.assertTrue(!IMixinEntityMissileBaseNT.governs(nuclear), "off: the base mod's");
			helper.assertTrue(!LeafiaStrikeSeams.MISSILE.onShotDown(generic), "off: a shot-down HE missile scatters as the base mod's");
			LeafiaFeatures.missileOverhaul = true;
			helper.assertTrue(IMixinEntityMissileBaseNT.governs(nuclear), "on: the addon's");
		} finally {
			LeafiaFeatures.missileOverhaul = was;
		}
		if(bombs()) helper.assertTrue(IMixinEntityMissileBaseNT.governs(new Probe(level)), "the addon's own missile always");
		helper.succeed();
	}

	// =================================================================== the Meteor Protection Beacon (MixinEntityMeteor's divert)

	/** The beacon works once its placer has been alive 20 x minAliveTime ticks; it lights, keeps its block entity, and protects its radius. */
	@GameTest(template = EMPTY)
	public static void theBeaconWaitsForItsPlacersLife(GameTestHelper helper) {
		if(LeafiaBlocks.diverter == null) { helper.succeed(); return; }
		helper.assertTrue(DiverterTE.timeRemaining(100_000, 0, 1800, false) == 36_000 - 100_000, "20 x 1800 - (now - death)");
		helper.assertTrue(DiverterTE.timeRemaining(100_000, 0, 1800, true) == 1, "no placer: never below 1");
		helper.assertTrue(DiverterBlock.formatTime(36_000).equals("0:30:00") && DiverterBlock.formatTime(20 * 3725).equals("1:02:05"), "H:MM:SS");

		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		String name = "explc_" + Integer.toHexString(level.random.nextInt());
		PlayerDeathsSavedData deaths = PlayerDeathsSavedData.forLevel(level);
		long minTicks = 20L * LeafiaConfig.meteorDiverterMinAliveTime;
		try {
			level.setBlock(far, LeafiaBlocks.diverter_unlit.get().defaultBlockState(), 3);
			DiverterTE te = (DiverterTE) level.getBlockEntity(far);
			te.update();
			helper.assertTrue(te.isError() && te.getTimeRemaining() >= 1, "no placer is an error");
			te.setPlacerName(name);
			deaths.timestamps.put(name, level.getGameTime() - 10);
			te.update();
			helper.assertTrue(te.getTimeRemaining() == minTicks - 10 && level.getBlockState(far).is(LeafiaBlocks.diverter_unlit.get()), "died 10 ticks ago: " + te.getTimeRemaining());
			deaths.timestamps.put(name, level.getGameTime() - minTicks - 1);
			te.update();
			helper.assertTrue(te.isActive() && level.getBlockState(far).is(LeafiaBlocks.diverter.get()), "alive long enough: lit");
			helper.assertTrue(level.getBlockEntity(far) == te && name.equals(te.getPlacerName()), "the same block entity, placer kept");

			int radius = LeafiaConfig.meteorDiverterProtectionRadius;
			int cx = far.getX() >> 4, cz = far.getZ() >> 4;
			helper.assertTrue(DiverterBlock.isProtected(level, cx + radius, cz - radius), "the corner of its square is protected");
			helper.assertTrue(!DiverterBlock.isProtected(level, cx + radius + 1, cz), "one chunk past is not");
		} finally {
			deaths.timestamps.remove(name);
			deaths.setDirty();
			clear(level, far, far);
		}
		helper.succeed();
	}

	/** A meteor over an active beacon is moved radius x 16 at a time along its own heading until clear, and replaced there. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aMeteorIsDivertedOutOfTheBeaconsRange(GameTestHelper helper) {
		if(LeafiaBlocks.diverter == null) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		String name = "explc_" + Integer.toHexString(level.random.nextInt());
		PlayerDeathsSavedData deaths = PlayerDeathsSavedData.forLevel(level);
		deaths.timestamps.put(name, -1_000_000_000L);
		level.setBlock(far, LeafiaBlocks.diverter_unlit.get().defaultBlockState(), 3);
		DiverterTE te = (DiverterTE) level.getBlockEntity(far);
		te.setPlacerName(name);
		te.update();

		EntityMeteor meteor = new EntityMeteor(level);
		meteor.safe = true;
		meteor.setPos(far.getX() + 0.5, 300, far.getZ() + 0.5);
		meteor.setDeltaMovement(0.25, -1, 0.5);
		Vec3 start = meteor.position();
		double angle = LeafiaStrikeSeams.divertAngle(meteor);
		int radius = LeafiaConfig.meteorDiverterProtectionRadius;
		boolean diverted;
		boolean wasDiverter = LeafiaFeatures.meteorDiverter;
		try {
			LeafiaFeatures.meteorDiverter = false;
			helper.assertTrue(!LeafiaStrikeSeams.METEOR.tick(meteor), "with the beacon switched off nothing happens");
			LeafiaFeatures.meteorDiverter = true;
			diverted = LeafiaStrikeSeams.METEOR.tick(meteor);
		} finally {
			LeafiaFeatures.meteorDiverter = wasDiverter;
		}
		Vec3 end = meteor.position();
		double moved = end.subtract(start).horizontalDistance();
		double step = radius * 16;
		long steps = Math.round(moved / step);
		helper.assertTrue(diverted && meteor.isRemoved(), "the tick is taken and the meteor replaced");
		helper.assertTrue(steps >= 1 && Math.abs(moved - steps * step) < 1e-6, "moved a whole number of " + step + " steps: " + moved);
		helper.assertTrue(Math.abs(Math.atan2(end.z - start.z, end.x - start.x) - Math.atan2(Math.sin(angle), Math.cos(angle))) < 1e-6, "along its own heading");
		helper.assertTrue(!DiverterBlock.isProtected(level, (int) (end.x / 16), (int) (end.z / 16)), "and out of range");
		AABB around = new AABB(end, end).inflate(3);
		helper.startSequence().thenWaitUntil(() -> {
			List<EntityMeteor> found = level.getEntitiesOfClass(EntityMeteor.class, around);
			if(found.isEmpty()) helper.fail("waiting for the replacement meteor");
			EntityMeteor fresh = found.get(0);
			boolean same = fresh.safe && fresh.getDeltaMovement().equals(new Vec3(0.25, -1, 0.5));
			for(EntityMeteor m : found) m.discard();
			if(!same) throw new net.minecraft.gametest.framework.GameTestAssertException("the replacement keeps safe and motion");
		}).thenExecute(() -> {
			deaths.timestamps.remove(name);
			deaths.setDirty();
			clear(level, far, far);
		}).thenSucceed();
	}

	/** A player's death is recorded by name at the dimension's game time. */
	@GameTest(template = EMPTY)
	public static void aPlayersDeathIsRecorded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(level.getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		String name = player.getGameProfile().getName();
		PlayerDeathsSavedData deaths = PlayerDeathsSavedData.forLevel(level);
		Long before = deaths.timestamps.get(name);
		try {
			LeafiaStrikeListener.onEntityDied(new LivingDeathEvent(player, level.damageSources().generic()));
			helper.assertTrue(deaths.timestamps.get(name) == level.getGameTime(), "recorded at " + level.getGameTime() + ": " + deaths.timestamps.get(name));
		} finally {
			if(before == null) deaths.timestamps.remove(name);
			else deaths.timestamps.put(name, before);
			deaths.setDirty();
			player.discard();
		}
		helper.succeed();
	}

	// =================================================================== meteors (MixinEntityMeteor, MixinBossSpawnHandler)

	/** With the overhaul a meteor is buried two deeper and digs the addon's crater in place of the rubble; a safe one is left alone. */
	@GameTest(template = EMPTY)
	public static void theMeteorOverhaulBuriesDeeperAndReplacesTheTails(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EntityMeteor meteor = new EntityMeteor(level);
		BlockPos p = new BlockPos(5, 60, 5);
		boolean was = LeafiaFeatures.meteorOverhaul, craters = LeafiaConfig.meteorCraters;
		try {
			LeafiaFeatures.meteorOverhaul = false;
			helper.assertTrue(LeafiaStrikeSeams.METEOR.burialPos(meteor, p).equals(p) && !LeafiaStrikeSeams.METEOR.replaceTails(meteor), "off: the base mod's");
			LeafiaFeatures.meteorOverhaul = true;
			LeafiaConfig.meteorCraters = true;
			helper.assertTrue(LeafiaStrikeSeams.METEOR.burialPos(meteor, p).equals(p.below(2)), "on: two deeper");
			meteor.safe = true;
			helper.assertTrue(LeafiaStrikeSeams.METEOR.burialPos(meteor, p).equals(p), "a safe meteor is not");
			helper.assertTrue(LeafiaStrikeSeams.METEOR.replaceTails(meteor), "and its tails are skipped without a crater");
			LeafiaConfig.meteorCraters = false;
			meteor.safe = false;
			helper.assertTrue(LeafiaStrikeSeams.METEOR.burialPos(meteor, p).equals(p) && !LeafiaStrikeSeams.METEOR.replaceTails(meteor), "custom craters off: the base mod's");
		} finally {
			LeafiaFeatures.meteorOverhaul = was;
			LeafiaConfig.meteorCraters = craters;
		}
		helper.assertTrue(LeafiaStrikeSeams.SHAKE_INTERVAL == 5 && LeafiaStrikeSeams.SHAKE_SEND_RANGE == 250
				&& LeafiaStrikeSeams.CRATER_BURY == 2 && LeafiaStrikeSeams.CRATER_DRIFT == 4, "the constants");
		helper.succeed();
	}

	/** With the rebalance a strike goes ahead on {@code nextInt(6) == 0}; without it, always, and nothing is drawn. */
	@GameTest(template = EMPTY)
	public static void theStrikeRebalanceLetsOneInSixThrough(GameTestHelper helper) {
		boolean was = LeafiaFeatures.meteorStrikeRebalance;
		try {
			LeafiaFeatures.meteorStrikeRebalance = false;
			Script off = new Script(1);
			helper.assertTrue(LeafiaStrikeSeams.strikeGoesAhead(off) && off.bounds.isEmpty(), "off: every strike, no draw");
			LeafiaFeatures.meteorStrikeRebalance = true;
			Script zero = new Script(0), one = new Script(1), five = new Script(5);
			helper.assertTrue(LeafiaStrikeSeams.strikeGoesAhead(zero) && zero.bounds.equals(List.of(6)), "0 of 6 goes ahead");
			helper.assertTrue(!LeafiaStrikeSeams.strikeGoesAhead(one) && !LeafiaStrikeSeams.strikeGoesAhead(five), "1 and 5 do not");
		} finally {
			LeafiaFeatures.meteorStrikeRebalance = was;
		}
		helper.succeed();
	}

	/** The crater's numbers, and one dug at a far spot into a stone slab: the centre column, its lava and its tektite lining. */
	@GameTest(template = EMPTY)
	public static void aMeteorDigsTheAddonsCrater(GameTestHelper helper) {
		helper.assertTrue(LeafiaMeteorCrater.RADIUS == 13 && LeafiaMeteorCrater.MAX_DEPTH == 8, "radius 13, depth 8");
		helper.assertTrue(LeafiaMeteorCrater.depth(0) == 8 && LeafiaMeteorCrater.depth(6.5) == 4 && LeafiaMeteorCrater.depth(13) == 0, "ceil(8 x (1 - d/13))");
		helper.assertTrue(LeafiaMeteorCrater.shade(0) == 8 && LeafiaMeteorCrater.shade(0.5) == 4 && LeafiaMeteorCrater.shade(1.0) == 0,
				"(int)(max(1 - 1.1 r, 0)^0.65 x 8): " + LeafiaMeteorCrater.shade(0.5));
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 280);
		level.getChunk(0, 0);
		int originTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		try {
			for(BlockPos p : BlockPos.betweenClosed(far.offset(-14, -12, -14), far.offset(14, 0, 14))) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2 | 16);
			LeafiaMeteorCrater.createMeteorCrater(level, far);
			for(int dy = 0; dy <= 5; dy++)
				helper.assertTrue(level.getBlockState(far.below(dy)).isAir(), "the centre is dug " + dy + " down: " + level.getBlockState(far.below(dy)));
			helper.assertTrue(level.getBlockState(far.below(6)).is(Blocks.LAVA), "and lava below 5.6: " + level.getBlockState(far.below(6)));
			BlockState lining = level.getBlockState(far.below(8));
			helper.assertTrue(lining.is(ModBlocks.tektite.get()) || lining.is(ModBlocks.ore_tektite_osmiridium.get()), "the centre is lined with tektite: " + lining);
			helper.assertTrue(level.getBlockState(far.offset(13, 0, 13)).is(Blocks.STONE), "the corner, 18 out, is not dug");
		} finally {
			clear(level, far.offset(-32, -14, -32), far.offset(32, 6, 32));
			// upstream's centre-column debris lands on the world origin column (D-3022); take it back off
			for(int y = originTop; y < originTop + 16; y++) {
				BlockPos o = new BlockPos(0, y, 0);
				if(level.getBlockState(o).is(Blocks.STONE)) level.setBlock(o, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		helper.succeed();
	}

	// =================================================================== the Tom impact (MixinTomSaveData, MixinImpactWorldHandler)

	/** The seismic level: 1 while the sky burns, then down by 1 / 9,600,000 a tick; saved in the impact data's extra tag. */
	@GameTest(template = EMPTY)
	public static void theSeismicLevelSettlesOverFourHundredDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TomSaveData data = new TomSaveData();
		data.fire = 1;
		LeafiaStrikeListener.tickSeismic(data);
		helper.assertTrue(IMixinTomSaveData.getSeismic(data) == 1F, "the sky burns: 1");
		data.fire = 0;
		LeafiaStrikeListener.tickSeismic(data);
		helper.assertTrue(IMixinTomSaveData.getSeismic(data) == 1F - 1F / 9600000F, "then settles: " + IMixinTomSaveData.getSeismic(data));
		helper.assertTrue(LeafiaStrikeListener.SETTLE == 1F / 9600000F, "400 days of 24,000 ticks");
		IMixinTomSaveData.setSeismic(data, 0.25F);
		CompoundTag nbt = data.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(nbt.getCompound(TomSaveData.EXTRA_KEY).getFloat("seismic") == 0.25F, "under upstream's key: " + nbt);
		helper.assertTrue(IMixinTomSaveData.getSeismic(TomSaveData.load(nbt, level.registryAccess())) == 0.25F, "and read back");
		helper.succeed();
	}

	/** A collapsing block falls through air and breaks what is not a solid full cube, to the first that is; the dust packet is four ints. */
	@GameTest(template = EMPTY)
	public static void aCollapsingBlockFallsToTheFirstSolidCube(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 250);
		try {
			level.setBlock(far, Blocks.STONE.defaultBlockState(), 2 | 16);
			level.setBlock(far.below(4), Blocks.GLASS.defaultBlockState(), 2 | 16);
			level.setBlock(far.below(5), Blocks.STONE.defaultBlockState(), 2 | 16);
			BlockPos to = ImpactSeismic.fallTo(level, far);
			helper.assertTrue(to.equals(far.below(4)), "it lands where the glass was: " + to);
			helper.assertTrue(level.getBlockState(far.below(4)).isAir(), "the glass broke");
			helper.assertTrue(level.getBlockState(far).is(Blocks.STONE), "fallTo moves nothing itself");
		} finally {
			clear(level, far.below(6), far);
		}
		helper.assertTrue(ImpactSeismic.ODDS == 100 && ImpactSeismic.PACKET_RANGE == 128, "one in a hundred, 128 out");

		ImpactSeismic.TomImpactCollapsePacket p = new ImpactSeismic.TomImpactCollapsePacket();
		p.x = -70_001;
		p.y0 = 90;
		p.y1 = 84;
		p.z = 123_456;
		LeafiaBuf b = new LeafiaBuf(Unpooled.buffer());
		p.encode(b);
		helper.assertTrue(b.writerIndex == 4 * 32, "four ints: " + b.writerIndex);
		helper.assertTrue(b.readInt() == -70_001 && b.readInt() == 90 && b.readInt() == 84 && b.readInt() == 123_456, "in upstream's order");
		helper.assertTrue(LeafiaPacketTypes.idOf(new ImpactSeismic.TomImpactCollapsePacket()) == LeafiaPacketTypes.IMPACT_COLLAPSE, "registered");
		helper.succeed();
	}

	// =================================================================== the Fat Chud

	/** The soot, the dead plants, the barrels, and a launch pad thrown into the air and rebuilt there. */
	@GameTest(template = EMPTY)
	public static void theFatChudsPieces(GameTestHelper helper) {
		helper.assertTrue(NukeChudBlock.RANGE == 50 && NukeChudBlock.TOWERS == 15 && NukeChudBlock.SOUND_VOLUME == 20F, "50 each way, 15 towers, volume 20");
		helper.assertTrue(NukeChudBlock.soot(0, 0) == 10000F, "10,000 at the centre");
		helper.assertTrue(Math.abs(NukeChudBlock.soot(1, 0) - 8007.374F) < 0.01F && Math.abs(NukeChudBlock.soot(3, 3) - 183.156F) < 0.01F,
				"e^(-d^2 / 4.5): " + NukeChudBlock.soot(1, 0) + ", " + NukeChudBlock.soot(3, 3));
		for(int m = 0; m < 5; m++)
			helper.assertTrue(NukeChudBlock.deadPlant(m).getValue(ModBlocks.plant_dead.get().getVariantProperty()) == BlockDeadPlant.EnumDeadPlantType.values()[m], "dead plant " + m);

		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper, 200);
		BlockPos barrel = far.offset(0, 0, 6);
		BlockPos core = far, lone = far.offset(5, 0, 0);
		BlockDummyable pad = (BlockDummyable) ModBlocks.launch_pad.get();
		try {
			NukeChudBlock.placeYellowBarrel(level, barrel, 100);
			BlockState b = level.getBlockState(barrel);
			helper.assertTrue(b.is(ModBlocks.yellow_barrel.get()) || (b.is(ModBlocks.sellafield.get())
					&& BlockSellafield.getLevel(b) >= 3 && BlockSellafield.getLevel(b) <= 5), "a yellow barrel or sellafite 3 to 5: " + b);

			level.setBlock(core, pad.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH), 3);
			NukeChudBlock.fillSpace(pad, level, core.relative(Direction.NORTH, -pad.getOffset()), Direction.NORTH, pad.getOffset());
			helper.assertTrue(level.getBlockState(core.east()).is(pad), "the pad is built");
			NukeChudBlock.liftMultiblock(level, core);
			for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++)
				helper.assertTrue(level.getBlockState(core.offset(dx, 0, dz)).isAir(), "torn down at " + dx + "," + dz);
			int found = -1;
			for(int up = 10; up <= 24; up++) {
				BlockState s = level.getBlockState(core.above(up));
				if(s.is(pad) && s.getValue(BlockDummyable.PART) == BlockDummyable.Part.CORE) found = up;
			}
			helper.assertTrue(found >= 10, "its core is 10 to 24 up");
			for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++)
				helper.assertTrue(level.getBlockState(core.offset(dx, found, dz)).is(pad), "rebuilt at " + dx + "," + dz);

			level.setBlock(lone, pad.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.UP), 3);
			NukeChudBlock.liftMultiblock(level, lone);
			boolean any = false;
			for(int up = 0; up <= 24; up++) any |= level.getBlockState(lone.above(up)).is(pad);
			helper.assertTrue(!any, "a core facing up is torn down and not rebuilt (upstream's meta >= 12)");
		} finally {
			clear(level, far.offset(-2, -1, -2), far.offset(6, 26, 7));
		}
		helper.succeed();
	}

	// =================================================================== the registry

	/** Upstream's block, item and entity properties, and the lit beacon's drop. */
	@GameTest(template = EMPTY)
	public static void theBlocksAndItemsAreUpstreams(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		if(LeafiaBlocks.diverter != null) {
			BlockState lit = LeafiaBlocks.diverter.get().defaultBlockState(), unlit = LeafiaBlocks.diverter_unlit.get().defaultBlockState();
			helper.assertTrue(lit.getLightEmission() == 7 && unlit.getLightEmission() == 0, "lit at 7, unlit dark");
			helper.assertTrue(lit.getDestroySpeed(level, at) == 0F && lit.requiresCorrectToolForDrops(), "instant, a pickaxe to drop");
			helper.assertTrue(lit.getCollisionShape(level, at).isEmpty(), "no collision");
			helper.assertTrue(LeafiaBlocks.diverter.get().asItem() == Items.AIR, "the lit one has no item");
			List<ItemStack> drops = Block.getDrops(lit, level, at, null, null, new ItemStack(Items.IRON_PICKAXE));
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(LeafiaBlocks.diverter_unlit.get().asItem()), "it drops the unlit one: " + drops);
		}
		if(bombs()) {
			BlockState chud = LeafiaBlocks.nuke_chud.get().defaultBlockState();
			helper.assertTrue(chud.getDestroySpeed(level, at) == 5F && LeafiaBlocks.nuke_chud.get().getExplosionResistance() == 3600F, "5 / 6,000 x 0.6");
			CustomNukeMissileItem missile = LeafiaItems.missile_customnuke.get();
			helper.assertTrue(missile.getDefaultMaxStackSize() == 1 && missile.formFactor == ItemMissile.MissileFormFactor.MICRO
					&& missile.tier == ItemMissile.MissileTier.TIER0 && missile.fuel == ItemMissile.MissileFuel.SOLID && missile.launchable, "a tier 0 micro missile");
			helper.assertTrue(LeafiaEntities.CUSTOM_NUKE_MISSILE.get().getWidth() == 1.5F, "the base mod's missile size");
		}
		helper.succeed();
	}
}
