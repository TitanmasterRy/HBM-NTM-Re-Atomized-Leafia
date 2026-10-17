package com.leafia.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.custom_hbm.util.LCETuple.Pair;
import com.hbm.blockentity.turret.BlockEntityTurretBaseNT;
import com.hbm.blockentity.turret.BlockEntityTurretChekhov;
import com.hbm.blockentity.turret.BlockEntityTurretJeremy;
import com.hbm.blockentity.turret.BlockEntityTurretSentry;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityCloudSolinium;
import com.hbm.entity.grenade.EntityGrenadeUniversal;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.projectile.EntityBoxcar;
import com.hbm.entity.projectile.EntityBulletBaseMK4;
import com.hbm.entity.projectile.EntityBulletBeamBase;
import com.hbm.entity.projectile.EntityFallingNuke;
import com.hbm.entity.projectile.EntityRBMKDebris;
import com.hbm.entity.projectile.EntityRBMKDebris.DebrisType;
import com.hbm.entity.projectile.EntityShrapnel;
import com.hbm.gametest.SpawnWindow;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.grenade.ItemGrenadeFilling;
import com.hbm.items.weapon.grenade.ItemGrenadeFilling.EnumGrenadeFilling;
import com.hbm.items.weapon.grenade.ItemGrenadeFuze.EnumGrenadeFuze;
import com.hbm.items.weapon.grenade.ItemGrenadeShell.EnumGrenadeShell;
import com.hbm.items.weapon.grenade.ItemGrenadeUniversal;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.BulletConfig.ProjectileType;
import com.hbm.items.weapon.sedna.Crosshair;
import com.hbm.items.weapon.sedna.GunConfig;
import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import com.hbm.items.weapon.sedna.ItemGunBaseNT.GunState;
import com.hbm.items.weapon.sedna.ItemGunBaseNT.LambdaContext;
import com.hbm.items.weapon.sedna.ItemGunBaseNT.WeaponQuality;
import com.hbm.items.weapon.sedna.Receiver;
import com.hbm.items.weapon.sedna.factory.GunFactory.EnumModSpecial;
import com.hbm.items.weapon.sedna.factory.GunStateDecider;
import com.hbm.items.weapon.sedna.factory.Lego;
import com.hbm.items.weapon.sedna.factory.XFactory44;
import com.hbm.items.weapon.sedna.factory.XFactory50;
import com.hbm.items.weapon.sedna.factory.XFactoryCatapult;
import com.hbm.items.weapon.sedna.factory.XFactoryEnergy;
import com.hbm.items.weapon.sedna.mags.MagazineInfinite;
import com.hbm.items.weapon.sedna.mods.XWeaponModManager;
import com.hbm.util.DamageResistanceHandler.DamageClass;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.building.pinkdoor.BlockPinkDoor;
import com.leafia.contents.gear.ILockonWeapon;
import com.leafia.contents.gear.ILockonWeapon.GetLockonPacket;
import com.leafia.contents.gear.ILockonWeapon.LockonHandler;
import com.leafia.contents.gear.combat.guns.GunInit;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifle;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifleBeam;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaGrenadeFillings;
import com.leafia.overwrite_contents.LeafiaSednaSeams;
import com.leafia.passive.EntityAttachedSounds;
import com.leafia.passive.EntityAttachedSounds.AttachedSoundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The SEDNA batch (D-3028): the anti-mass rifle, its beam, its lock-on and its two weapon-mod overrides; the attached sounds; the turret
 * friendly-fire veto and the shimmer sledge's hook; the two appended grenade fillings; and the pink door, both halves of it.
 *
 * <p>Every number is read back through the base gun system's own objects ({@code GunConfig}, {@code Receiver}, {@code BulletConfig},
 * {@code XWeaponModManager}) or off the entities a call spawned. Everything process wide a test changes (a feature switch, the audio
 * factory, the lock table, the client lock, the attached sounds, a weapon mod on a stack) is put back in a {@code finally}; mock
 * server players are detached from the player list ({@link LeafiaCrateTestKit#mockPlayer}, D-3000); anything that would blow up the
 * shared test world (the pink door's volley, the solinium rinse) is caught at {@link EntityJoinLevelEvent} and
 * {@link ExplosionEvent.Start} and cancelled, so what it would have spawned is counted and nothing is spawned.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSednaTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	private static AMRifle rifle() {
		return LeafiaItems.am_rifle.get();
	}

	private static ItemStack modStack(EnumModSpecial mod) {
		return new ItemStack(XWeaponModManager.modItem("special", mod));
	}

	private static void floor(GameTestHelper helper, int y, int size) {
		for(int x = 0; x < size; x++) for(int z = 0; z < size; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
	}

	private static boolean near(double a, double b, double eps) {
		return Math.abs(a - b) <= eps;
	}

	/** Runs {@code body} with every entity that tries to join the level, and every explosion, recorded and refused. */
	private static void intercepting(List<Entity> joined, List<ExplosionEvent.Start> blasts, Runnable body) {
		Consumer<EntityJoinLevelEvent> join = e -> {
			joined.add(e.getEntity());
			e.setCanceled(true);
		};
		Consumer<ExplosionEvent.Start> blast = e -> {
			blasts.add(e);
			e.setCanceled(true);
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityJoinLevelEvent.class, join);
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, ExplosionEvent.Start.class, blast);
		try {
			body.run();
		} finally {
			NeoForge.EVENT_BUS.unregister(join);
			NeoForge.EVENT_BUS.unregister(blast);
		}
	}

	// ------------------------------------------------------------------------------------------
	// the rifle's numbers
	// ------------------------------------------------------------------------------------------

	/** The gun config, through the base mod's getters: upstream's {@code GunInit} chain, and the item's quality and stack. */
	@GameTest(template = EMPTY)
	public static void theRiflesConfigIsUpstreams(GameTestHelper helper) {
		if(!LeafiaFeatures.amRifle) {
			helper.assertTrue(LeafiaItems.am_rifle == null, "the rifle is registered with features.amRifle off");
			helper.succeed();
			return;
		}
		AMRifle gun = rifle();
		ItemStack stack = new ItemStack(gun);
		GunConfig config = gun.getConfig(stack, 0);
		helper.assertTrue(gun.getConfigCount() == 1, "one config");
		helper.assertTrue(config.getDurability(stack) == 50F, "durability 50: " + config.getDurability(stack));
		helper.assertTrue(config.getDrawDuration(stack) == 10, "draw 10");
		helper.assertTrue(config.getCrosshair(stack) == Crosshair.CIRCLE, "the circle crosshair");
		helper.assertTrue(config.getScopeTexture(stack) == null, "no scope texture without the scope");
		Receiver[] receivers = config.getReceivers(stack);
		helper.assertTrue(receivers.length == 1, "one receiver");
		Receiver rec = receivers[0];
		helper.assertTrue(rec.getBaseDamage(stack) == 200F, "200 damage: " + rec.getBaseDamage(stack));
		helper.assertTrue(rec.getSplitProjectiles(stack) == 1F, "one projectile");
		helper.assertTrue(rec.getMagazine(stack) instanceof MagazineInfinite, "an infinite magazine");
		helper.assertTrue(rec.getMagazine(stack).getType(stack, null) == GunInit.am_beam, "of the AM beam");
		helper.assertTrue(rec.getProjectileOffset(stack).equals(new Vec3(0.75, -0.0625 * 1.5, -0.1875)), "the laser rifle's muzzle offset");
		helper.assertTrue(rec.getProjectileOffsetScoped(stack).equals(new Vec3(0.75, -0.0625 * 1.5, 0)), "and its scoped offset");
		helper.assertTrue(rec.getCanFire(stack) == Lego.LAMBDA_STANDARD_CAN_FIRE, "setupStandardFire's can-fire");
		helper.assertTrue(rec.getOnFire(stack) != Lego.LAMBDA_STANDARD_FIRE, "the fire lambda is the rifle's, not the standard one");
		helper.assertTrue(config.getPressPrimary(stack) != Lego.LAMBDA_STANDARD_CLICK_PRIMARY, "the primary press is the rifle's");
		helper.assertTrue(config.getPressReload(stack) == Lego.LAMBDA_STANDARD_RELOAD, "the standard reload");
		helper.assertTrue(config.getPressTertiary(stack) == Lego.LAMBDA_TOGGLE_AIM, "tertiary aims");
		helper.assertTrue(config.getDecider(stack) == GunStateDecider.LAMBDA_STANDARD_DECIDER, "the standard decider");
		helper.assertTrue(config.getAnims(stack) == AMRifle.LAMBDA_AMRIFLE, "the rifle's animations");
		helper.assertTrue(config.getOrchestra(stack) != null, "an orchestra");
		helper.assertTrue(gun.quality == WeaponQuality.SECRET && ItemGunBaseNT.secrets.contains(gun), "SECRET, on the hidden list");
		helper.assertTrue(stack.getMaxStackSize() == 1, "stacks to one");
		for(var holder : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN))
			helper.assertTrue(holder.get().asItem() != gun, "a SECRET gun is in no creative tab");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(gun).equals(LeafiaRef.rl("gun_leafia_amrifle")), "upstream's id");
		helper.assertTrue(GunInit.AM_DAMAGE == 200F && AMRifle.CHARGE_TICKS == 11 && AMRifle.COOLDOWN_TICKS == 200, "the cycle's numbers");
		helper.assertTrue(AMRifle.fanDuration == 50 && AMRifle.fanDiv == 5 && AMRifle.fanRPS == 360D, "the fan's numbers");
		helper.assertTrue(AMRifleBeam.RANGE == 250D && ILockonWeapon.MAX_LOCK_RANGE == 250D && ILockonWeapon.LOCK_LIFE == 60_000L, "reach and lock life");
		boolean ads = AMRifle.isADS;
		try {
			AMRifle.isADS = false;
			helper.assertTrue(gun.maxInclination(stack) == 0.45, "a hip-fire cone of 0.45");
			AMRifle.isADS = true;
			helper.assertTrue(gun.maxInclination(stack) == 0.1, "an aimed cone of 0.1");
		} finally {
			AMRifle.isADS = ads;
		}
		helper.succeed();
	}

	/** The beam: a LASER, a hit-scan beam of five ticks with no spread, unrotated, through everything, on the rifle's own hit. */
	@GameTest(template = EMPTY)
	public static void theBeamConfigIsUpstreams(GameTestHelper helper) {
		BulletConfig beam = GunInit.am_beam;
		helper.assertTrue(beam != null && BulletConfig.configs.get(beam.id) == beam, "the beam config exists whatever the switch, under its id");
		helper.assertTrue(beam.dmgClass == DamageClass.LASER, "LASER");
		helper.assertTrue(beam.pType == ProjectileType.BEAM, "a beam");
		helper.assertTrue(beam.spread == 0F, "no spread");
		helper.assertTrue(beam.expires == 5, "lives five ticks: " + beam.expires);
		helper.assertTrue(!beam.renderRotations, "not rotated");
		helper.assertTrue(beam.doesPenetrate, "penetrates");
		helper.assertTrue(beam.impactsEntities && !beam.isSpectral, "hits entities, stops at blocks");
		helper.assertTrue(beam.onImpactBeam == GunInit.LAMBDA_AM_BEAM_HIT, "the rifle's hit");
		helper.assertTrue(beam.damageMult == 1F && beam.wear == 1F, "damage and wear multipliers left at one");
		helper.succeed();
	}

	/**
	 * The hit: health drops by the beam's whole damage whatever armour, resistance or invulnerability would have stopped, and an entity
	 * left at zero is dead; one already dead is not hit again; a non-living target takes the damage through the i-frame rule.
	 */
	@GameTest(template = EMPTY)
	public static void aBeamHitTakesTheWholeDamageWhateverTheDefence(GameTestHelper helper) {
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
			zombie.setNoAi(true);
			level.addFreshEntity(zombie);
			try {
				zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
				zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4)); // resistance V: immune to ordinary damage
				zombie.invulnerableTime = 20;
				zombie.setHealth(20F);
				EntityBulletBeamBase beam = new EntityBulletBeamBase(level, GunInit.am_beam, 15F);
				GunInit.LAMBDA_AM_BEAM_HIT.accept(beam, new EntityHitResult(zombie, zombie.position()));
				helper.assertTrue(zombie.getHealth() == 5F, "20 - 15 = 5 through netherite, resistance V and i-frames: " + zombie.getHealth());

				// a hurt that something else cancels outright (another mod, a damage handler): the health is set regardless
				zombie.setHealth(20F);
				Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> shield = e -> {
					if(e.getEntity() == zombie) e.setCanceled(true);
				};
				NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class, shield);
				try {
					GunInit.LAMBDA_AM_BEAM_HIT.accept(beam, new EntityHitResult(zombie, zombie.position()));
				} finally {
					NeoForge.EVENT_BUS.unregister(shield);
				}
				helper.assertTrue(zombie.getHealth() == 5F, "a cancelled hurt still leaves 20 - 15 = 5: " + zombie.getHealth());
				helper.assertTrue(zombie.isAlive(), "and alive");

				EntityBulletBeamBase big = new EntityBulletBeamBase(level, GunInit.am_beam, 200F);
				GunInit.LAMBDA_AM_BEAM_HIT.accept(big, new EntityHitResult(zombie, zombie.position()));
				helper.assertTrue(zombie.getHealth() <= 0F && zombie.isDeadOrDying(), "200 leaves nothing: " + zombie.getHealth());

				float before = zombie.getHealth();
				GunInit.LAMBDA_AM_BEAM_HIT.accept(big, new EntityHitResult(zombie, zombie.position()));
				helper.assertTrue(zombie.getHealth() == before, "a dead target is not hit again");
				helper.assertTrue(LeafiaDamageSources.of(level, LeafiaDamageSources.am_rifle).is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY),
						"the am_rifle damage type bypasses invulnerability");
			} finally {
				zombie.discard();
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------
	// the cycle
	// ------------------------------------------------------------------------------------------

	private static List<EntityBulletBeamBase> beams(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(EntityBulletBeamBase.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32));
	}

	/**
	 * Primary while idle charges for eleven ticks and fires on the twelfth tick the gun is held (the orchestra sees the cycle's
	 * timer at 0 to 11): one beam of 200, the rifle marked fired, one point of wear; a press while charging or fired does nothing; with
	 * the refractor the beam carries 100.
	 */
	@GameTest(template = EMPTY16)
	public static void theCycleFiresOnceOnItsEleventhTick(GameTestHelper helper) {
		if(!LeafiaFeatures.amRifle) {
			helper.succeed();
			return;
		}
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			floor(helper, 1, 16);
			ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 8)));
			player.moveTo(at.x, at.y, at.z, 0F, 80F); // looking almost straight down at the floor, so the beam stops there
			AMRifle gun = rifle();
			ItemStack stack = new ItemStack(gun);
			player.getInventory().selected = 0;
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			List<EntityBulletBeamBase> before = beams(helper);
			try {
				for(int t = 0; t < 40 && ItemGunBaseNT.getState(stack, 0) != GunState.IDLE; t++) gun.inventoryTick(stack, level, player, 0, true);
				helper.assertTrue(ItemGunBaseNT.getState(stack, 0) == GunState.IDLE, "drawn and idle");
				GunConfig config = gun.getConfig(stack, 0);
				LambdaContext ctx = new LambdaContext(config, player, player.getInventory(), 0);
				config.getPressPrimary(stack).accept(stack, ctx);
				helper.assertTrue(AMRifle.getByte(stack, AMRifle.KEY_STATE) == 1, "charging");
				helper.assertTrue(ItemGunBaseNT.getState(stack, 0) == GunState.COOLDOWN && ItemGunBaseNT.getTimer(stack, 0) == 11, "an eleven-tick cooldown");
				for(int t = 0; t < 11; t++) gun.inventoryTick(stack, level, player, 0, true);
				config.getPressPrimary(stack).accept(stack, ctx); // pressed again while charging
				helper.assertTrue(beams(helper).size() == before.size(), "nothing for eleven ticks");
				gun.inventoryTick(stack, level, player, 0, true);
				List<EntityBulletBeamBase> fired = beams(helper);
				fired.removeAll(before);
				helper.assertTrue(fired.size() == 1, "one beam on the twelfth tick: " + fired.size());
				helper.assertTrue(fired.get(0).damage == 200F && fired.get(0).config == GunInit.am_beam && fired.get(0).thrower == player, "200, the AM beam, the shooter's");
				helper.assertTrue(AMRifle.getByte(stack, AMRifle.KEY_STATE) == 2, "fired");
				helper.assertTrue(ItemGunBaseNT.getWear(stack, 0) == 1F, "one point of wear: " + ItemGunBaseNT.getWear(stack, 0));
				for(int t = 0; t < 30; t++) gun.inventoryTick(stack, level, player, 0, true);
				config.getPressPrimary(stack).accept(stack, ctx);
				for(int t = 0; t < 30; t++) gun.inventoryTick(stack, level, player, 0, true);
				List<EntityBulletBeamBase> again = beams(helper);
				again.removeAll(before);
				helper.assertTrue(again.size() <= 1, "a press while fired does nothing");

				ItemStack refracted = new ItemStack(gun);
				XWeaponModManager.install(refracted, 0, modStack(EnumModSpecial.LAS_SHOTGUN));
				List<EntityBulletBeamBase> before2 = beams(helper);
				AMRifle.fire(refracted, new LambdaContext(gun.getConfig(refracted, 0), player, player.getInventory(), 0));
				List<EntityBulletBeamBase> fired2 = beams(helper);
				fired2.removeAll(before2);
				helper.assertTrue(fired2.size() == 1 && fired2.get(0).damage == 100F, "the refractor halves the beam: " + (fired2.isEmpty() ? "none" : fired2.get(0).damage));
			} finally {
				for(EntityBulletBeamBase beam : beams(helper)) if(!before.contains(beam)) beam.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * The two overrides (upstream's two mixins): the scope shows the luna scope on the rifle and the heavy revolver's own on that gun;
	 * the refractor on the rifle halves its damage and leaves one projectile and the circle, and on the laser rifle is the refractor.
	 */
	@GameTest(template = EMPTY)
	public static void theScopeAndTheRefractorAreDifferentOnTheRifleOnly(GameTestHelper helper) {
		if(!LeafiaFeatures.amRifle) {
			helper.succeed();
			return;
		}
		AMRifle gun = rifle();
		helper.assertTrue(XWeaponModManager.getModOverride(gun, XWeaponModManager.ID_SCOPE) != null
				&& XWeaponModManager.getModOverride(gun, XWeaponModManager.ID_LAS_SHOTGUN) != null, "both overrides set");
		helper.assertTrue(gun.recognizedMods.size() == 2, "the rifle takes two mods: " + gun.recognizedMods.size());
		ItemStack plain = new ItemStack(gun);
		helper.assertTrue(XWeaponModManager.isApplicable(plain, modStack(EnumModSpecial.SCOPE), 0, false)
				&& XWeaponModManager.isApplicable(plain, modStack(EnumModSpecial.LAS_SHOTGUN), 0, false), "both are applicable to it");

		ItemStack scoped = new ItemStack(gun);
		XWeaponModManager.install(scoped, 0, modStack(EnumModSpecial.SCOPE));
		helper.assertTrue(gun.getConfig(scoped, 0).getScopeTexture(scoped) == XFactoryEnergy.scope_luna, "the luna scope on the rifle");
		helper.assertTrue(gun.getConfig(scoped, 0).getHideCrosshair(scoped), "and the scope's own hidden crosshair");
		Item revolver = ModItems.gun_heavy_revolver.get();
		ItemStack revolverScoped = new ItemStack(revolver);
		XWeaponModManager.install(revolverScoped, 0, modStack(EnumModSpecial.SCOPE));
		helper.assertTrue(((ItemGunBaseNT) revolver).getConfig(revolverScoped, 0).getScopeTexture(revolverScoped) == XFactory44.scope_lilmac,
				"the heavy revolver keeps its own scope");

		ItemStack refracted = new ItemStack(gun);
		XWeaponModManager.install(refracted, 0, modStack(EnumModSpecial.LAS_SHOTGUN));
		Receiver rec = gun.getConfig(refracted, 0).getReceivers(refracted)[0];
		Receiver bare = gun.getConfig(plain, 0).getReceivers(plain)[0];
		helper.assertTrue(rec.getBaseDamage(refracted) == 100F, "200 / 2 on the rifle: " + rec.getBaseDamage(refracted));
		helper.assertTrue(rec.getSplitProjectiles(refracted) == 1F, "one projectile still");
		helper.assertTrue(gun.getConfig(refracted, 0).getCrosshair(refracted) == Crosshair.CIRCLE, "the circle still");
		helper.assertTrue(rec.getInnateSpread(refracted) == bare.getInnateSpread(plain) + 3F, "the refractor's own spread");
		helper.assertTrue(gun.canLockon(refracted) && !gun.canLockon(plain), "and it unlocks the lock-on");

		Item lasrifle = ModItems.gun_lasrifle.get();
		ItemStack lasPlain = new ItemStack(lasrifle);
		ItemStack lasRefracted = new ItemStack(lasrifle);
		XWeaponModManager.install(lasRefracted, 0, modStack(EnumModSpecial.LAS_SHOTGUN));
		Receiver lasRec = ((ItemGunBaseNT) lasrifle).getConfig(lasRefracted, 0).getReceivers(lasRefracted)[0];
		Receiver lasBare = ((ItemGunBaseNT) lasrifle).getConfig(lasPlain, 0).getReceivers(lasPlain)[0];
		helper.assertTrue(lasRec.getBaseDamage(lasRefracted) == lasBare.getBaseDamage(lasPlain) * 0.35F, "the laser rifle keeps 35%");
		helper.assertTrue(lasRec.getSplitProjectiles(lasRefracted) == lasBare.getSplitProjectiles(lasPlain) * 3F, "and three projectiles");
		helper.assertTrue(((ItemGunBaseNT) lasrifle).getConfig(lasRefracted, 0).getCrosshair(lasRefracted) == Crosshair.L_CIRCLE, "and the large circle");
		helper.succeed();
	}

	/** The fan: up one a tick to fifty while fired, the angle by 18 x fan / 50 a tick and wrapped, the fired state for 201 ticks, then down. */
	@GameTest(template = EMPTY)
	public static void theFanSpinsUpCountsDownAndSpinsDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie holder = EntityType.ZOMBIE.create(level);
		holder.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
		ItemStack stack = new ItemStack(LeafiaFeatures.amRifle ? rifle() : Items.STICK);
		AMRifle.setByte(stack, AMRifle.KEY_STATE, (byte) 2);
		AMRifle.setInt(stack, AMRifle.KEY_TIMER, 0);
		double rot = 0;
		for(int t = 1; t <= 201; t++) {
			AMRifle.tickFan(stack, level, holder);
			int fan = Math.min(t, 50);
			helper.assertTrue(AMRifle.getInt(stack, AMRifle.KEY_FAN) == fan, "fan " + fan + " at tick " + t);
			double next = rot + 360D / 20 * (fan / 50D);
			double prev = rot;
			if(next >= 360) {
				next -= 360;
				prev -= 360;
			}
			helper.assertTrue(near(AMRifle.getDouble(stack, AMRifle.KEY_ROT), next, 1e-9) && near(AMRifle.getDouble(stack, AMRifle.KEY_PREV_ROT), prev, 1e-9),
					"the angle at tick " + t + ": " + AMRifle.getDouble(stack, AMRifle.KEY_ROT) + " vs " + next);
			rot = next;
		}
		helper.assertTrue(AMRifle.tag(stack).contains(AMRifle.KEY_SOUND_ID), "a sound id");
		helper.assertTrue(AMRifle.getByte(stack, AMRifle.KEY_STATE) == 2 && AMRifle.getInt(stack, AMRifle.KEY_TIMER) == 201, "still fired after 201 ticks");
		AMRifle.tickFan(stack, level, holder);
		helper.assertTrue(AMRifle.getByte(stack, AMRifle.KEY_STATE) == 0 && !AMRifle.tag(stack).contains(AMRifle.KEY_TIMER), "ready on the 202nd, the timer gone");
		helper.assertTrue(AMRifle.getInt(stack, AMRifle.KEY_FAN) == 50, "the fan still at full");
		AMRifle.tickFan(stack, level, holder);
		helper.assertTrue(AMRifle.getInt(stack, AMRifle.KEY_FAN) == 49, "then down one a tick");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------
	// the lock-on
	// ------------------------------------------------------------------------------------------

	/** A locked beam leaves along the line from the shooter's eyes to the target's; an unlocked one along the shooter's look. */
	@GameTest(template = EMPTY16)
	public static void aLockedBeamAimsAtTheTargetsEyes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 1, 16);
		Zombie shooter = EntityType.ZOMBIE.create(level);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 5, 8)));
		shooter.moveTo(feet.x, feet.y, feet.z, 30F, 20F);
		ArmorStand target = EntityType.ARMOR_STAND.create(level);
		target.setPos(feet.x + 3.3, feet.y - 2.6, feet.z + 2.1); // below, so the beam ends in the floor
		AMRifleBeam locked = new AMRifleBeam(shooter, target, GunInit.am_beam, 200F, -0.1875, -0.09375, 0.75);
		Vec3 want = new Vec3(target.getX() - shooter.getX(), target.getEyeY() - shooter.getEyeY(), target.getZ() - shooter.getZ()).normalize();
		Vec3 got = new Vec3(locked.headingX, locked.headingY, locked.headingZ).normalize();
		helper.assertTrue(got.distanceTo(want) < 1e-3, "aimed at the target's eyes: " + got + " vs " + want);
		helper.assertTrue(near(new Vec3(locked.headingX, locked.headingY, locked.headingZ).length(), 250D, 1e-3), "250 blocks of heading");
		helper.assertTrue(locked.damage == 200F && locked.config == GunInit.am_beam, "200, the AM beam");

		AMRifleBeam free = new AMRifleBeam(shooter, null, GunInit.am_beam, 200F, -0.1875, -0.09375, 0.75);
		Vec3 look = shooter.getLookAngle();
		Vec3 heading = new Vec3(free.headingX, free.headingY, free.headingZ).normalize();
		helper.assertTrue(heading.distanceTo(look) < 1e-3, "unlocked, along the look: " + heading + " vs " + look);
		helper.succeed();
	}

	/** The server's half: a reported lock is kept only for a lock-on weapon's holder, on a live entity in reach; stale ones lapse. */
	@GameTest(template = EMPTY)
	public static void aReportedLockIsValidated(GameTestHelper helper) {
		if(!LeafiaFeatures.amRifle) {
			helper.succeed();
			return;
		}
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
			player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.setNoAi(true);
			zombie.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))));
			level.addFreshEntity(zombie);
			try {
				helper.assertTrue(LeafiaPacketTypes.idOf(new AttachedSoundPacket()) == LeafiaPacketTypes.ATTACHED_SOUND
						&& LeafiaPacketTypes.idOf(new GetLockonPacket()) == LeafiaPacketTypes.GET_LOCKON
						&& LeafiaPacketTypes.GET_LOCKON == LeafiaPacketTypes.ATTACHED_SOUND + 1, "the pair is registered in upstream's order");
				helper.assertTrue(new GetLockonPacket().acceptsServerbound(), "the answer may come from a client");

				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				LeafiaCrateTestKit.run(new GetLockonPacket(zombie), player, PacketFlow.SERVERBOUND);
				helper.assertTrue(ILockonWeapon.getLoadedLockon(player) == null, "refused without a lock-on weapon in hand");

				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rifle()));
				LeafiaCrateTestKit.run(new GetLockonPacket(zombie), player, PacketFlow.SERVERBOUND);
				helper.assertTrue(ILockonWeapon.getLoadedLockon(player) == zombie, "kept through the wire with the rifle in hand");

				LeafiaCrateTestKit.run(new GetLockonPacket(player), player, PacketFlow.SERVERBOUND);
				helper.assertTrue(ILockonWeapon.getLoadedLockon(player) == null, "a lock on oneself clears it");

				Zombie far = EntityType.ZOMBIE.create(level);
				far.setPos(player.getX() + 251, player.getY(), player.getZ());
				helper.assertTrue(GetLockonPacket.whyRefused(player, far) != null, "beyond 250 blocks is refused");
				far.setPos(player.getX() + 249, player.getY(), player.getZ());
				helper.assertTrue(GetLockonPacket.whyRefused(player, far) == null, "within 250 blocks is believed");

				LeafiaCrateTestKit.run(new GetLockonPacket(zombie), player, PacketFlow.SERVERBOUND);
				LeafiaCrateTestKit.run(new GetLockonPacket(null), player, PacketFlow.SERVERBOUND);
				helper.assertTrue(ILockonWeapon.getLoadedLockon(player) == null, "an empty answer clears it");

				GetLockonPacket.lockons.put(player, new Pair<>(zombie, System.currentTimeMillis() - ILockonWeapon.LOCK_LIFE - 1));
				helper.assertTrue(ILockonWeapon.getLoadedLockon(player) == null && !GetLockonPacket.lockons.containsKey(player), "a lock older than a minute lapses");
			} finally {
				GetLockonPacket.lockons.remove(player);
				zombie.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * The client's half, driven on the server's level: with the refractor fitted the rifle picks the entity inside the 0.45 square at
	 * unit depth, keeps it, counts the ticks it holds it, and ignores one outside the square, one behind a block and one behind the
	 * player; without the refractor, or while charging, nothing changes.
	 */
	@GameTest(template = EMPTY16)
	public static void theLockConeIsUpstreams(GameTestHelper helper) {
		if(!LeafiaFeatures.amRifle) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 2)));
		player.moveTo(feet.x, feet.y, feet.z, 0F, 0F); // facing +z
		double eye = player.getEyeY();
		ArmorStand inside = EntityType.ARMOR_STAND.create(level);
		ArmorStand outside = EntityType.ARMOR_STAND.create(level);
		ArmorStand behind = EntityType.ARMOR_STAND.create(level);
		ArmorStand walled = EntityType.ARMOR_STAND.create(level);
		inside.setPos(feet.x + 0.4 * 10, eye - inside.getEyeHeight(), feet.z + 10);   // x / depth = 0.4
		outside.setPos(feet.x - 0.5 * 10, eye - outside.getEyeHeight(), feet.z + 10); // 0.5
		behind.setPos(feet.x, eye - behind.getEyeHeight(), feet.z - 5);
		walled.setPos(feet.x - 0.1 * 6, eye - walled.getEyeHeight(), feet.z + 6);
		helper.setBlock(new BlockPos(8, 3, 6), Blocks.STONE); // between the player's eyes and the walled one
		helper.setBlock(new BlockPos(7, 3, 6), Blocks.STONE);
		List<Entity> all = List.of(player, inside, outside, behind, walled);
		ItemStack stack = new ItemStack(rifle());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		boolean ads = AMRifle.isADS;
		try {
			AMRifle.isADS = false;
			LockonHandler.target = null;
			LockonHandler.timer = 0;
			LockonHandler.handleLockon(player, level, all);
			helper.assertTrue(LockonHandler.target == null, "no lock without the refractor");

			XWeaponModManager.install(stack, 0, modStack(EnumModSpecial.LAS_SHOTGUN));
			LockonHandler.handleLockon(player, level, all);
			helper.assertTrue(LockonHandler.target == inside && LockonHandler.timer == 1, "the one inside the square: " + LockonHandler.target);
			for(int t = 0; t < 31; t++) LockonHandler.handleLockon(player, level, all);
			helper.assertTrue(LockonHandler.target == inside && LockonHandler.timer == 32, "held 32 ticks: " + LockonHandler.timer);

			AMRifle.setByte(stack, AMRifle.KEY_STATE, (byte) 1);
			LockonHandler.handleLockon(player, level, List.of(player, outside));
			helper.assertTrue(LockonHandler.target == inside && LockonHandler.timer == 32, "frozen while charging");
			AMRifle.setByte(stack, AMRifle.KEY_STATE, (byte) 0);

			LockonHandler.handleLockon(player, level, List.of(player, outside, behind, walled));
			helper.assertTrue(LockonHandler.target == null && LockonHandler.timer == 0, "none of the other three: " + LockonHandler.target);

			AMRifle.isADS = true;
			LockonHandler.handleLockon(player, level, all);
			helper.assertTrue(LockonHandler.target == null, "aimed, the square is 0.1 and the 0.4 one is outside it");
		} finally {
			AMRifle.isADS = ads;
			LockonHandler.target = null;
			LockonHandler.timer = 0;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------
	// the attached sounds
	// ------------------------------------------------------------------------------------------

	/** A wrapper that records what was asked of it. */
	private static final class Recording extends LCEAudioWrapper {
		int starts, stops;
		float volume, pitch;
		@Override public LCEAudioWrapper startSound() { starts++; return this; }
		@Override public LCEAudioWrapper stopSound() { stops++; return this; }
		@Override public LCEAudioWrapper updateVolume(float v) { volume = v; return this; }
		@Override public LCEAudioWrapper updatePitch(float p) { pitch = p; return this; }
		@Override public float getVolume() { return volume; }
		@Override public float getPitch() { return pitch; }
	}

	private static void deliver(AttachedSoundPacket packet, ServerLevel level) {
		var opened = LeafiaCustomPacket.open(LeafiaCustomPacket.__start(packet));
		UUID uuid = new UUID(opened.buf().readLong(), opened.buf().readLong());
		long id = opened.buf().readLong();
		EntityAttachedSounds.AttachedSoundPacketBase.apply(level.getAllEntities(), uuid, id, new AttachedSoundPacket().readBody(opened.buf()));
	}

	/** Through the wire: a play starts one loop at the sent volume and pitch, a second play retunes it, a stop ends it, silence lapses it. */
	@GameTest(template = EMPTY)
	public static void attachedSoundsStartRetuneStopAndLapse(GameTestHelper helper) {
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.setNoAi(true);
			zombie.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
			level.addFreshEntity(zombie);
			List<Recording> made = new ArrayList<>();
			LCEAudioProxy.Factory old = LCEAudioProxy.factory;
			List<EntityAttachedSounds.EntityAttachedSound> kept = new ArrayList<>(EntityAttachedSounds.sounds);
			EntityAttachedSounds.sounds.clear();
			try {
				LCEAudioProxy.factory = (world, sound, start, stop, cat, x, y, z, volume, pitch) -> {
					Recording r = new Recording();
					r.volume = volume;
					r.pitch = pitch;
					made.add(r);
					return r;
				};
				SoundEvent fan = LeafiaSounds.fan.get();
				deliver(new AttachedSoundPacket(zombie, 77L, fan, 0.35F, 1.0F), level);
				helper.assertTrue(EntityAttachedSounds.sounds.size() == 1 && made.size() == 1 && made.get(0).starts == 1, "one loop started");
				helper.assertTrue(made.get(0).volume == 0.35F && made.get(0).pitch == 1.0F, "at the sent volume and pitch");
				deliver(new AttachedSoundPacket(zombie, 77L, fan, 0.35F * 2F, 2.0F), level);
				helper.assertTrue(made.size() == 1 && made.get(0).pitch == 2.0F && made.get(0).volume == 0.7F, "the same loop, retuned");
				deliver(new AttachedSoundPacket(zombie, 77L), level);
				helper.assertTrue(EntityAttachedSounds.sounds.isEmpty() && made.get(0).stops == 1, "stopped and dropped");

				deliver(new AttachedSoundPacket(zombie, 78L, fan, 0.5F, 1.0F), level);
				EntityAttachedSounds.update(level);
				helper.assertTrue(EntityAttachedSounds.sounds.size() == 1, "fresh, kept");
				EntityAttachedSounds.sounds.get(0).lastTimestamp -= EntityAttachedSounds.STALE_AFTER + 1;
				EntityAttachedSounds.update(level);
				helper.assertTrue(EntityAttachedSounds.sounds.isEmpty() && made.get(1).stops == 1, "two seconds of silence lapse it");
			} finally {
				LCEAudioProxy.factory = old;
				EntityAttachedSounds.sounds.clear();
				EntityAttachedSounds.sounds.addAll(kept);
				zombie.discard();
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------
	// the turret veto and the sledge
	// ------------------------------------------------------------------------------------------

	private static void placeEye(Entity e, Vec3 mid) {
		e.setPos(mid.x, mid.y - e.getBbHeight() * 0.5, mid.z);
	}

	/**
	 * Upstream's three turret mixins: with the switch on, the Chekhov and the Jeremy hold fire while the first thing between pivot
	 * and target is an entity they would not target; not for a block first, not for a friend off the line, not for a sentry, and not
	 * at all with the switch off. Through the firing tick, a held Chekhov spends nothing.
	 */
	@GameTest(template = EMPTY16)
	public static void turretsHoldFireThroughAFriend(GameTestHelper helper) {
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			boolean on = LeafiaFeatures.turretFriendlyFire;
			BlockPos core = helper.absolutePos(new BlockPos(4, 1, 4));
			BlockEntityTurretChekhov chekhov = new BlockEntityTurretChekhov(core, ModBlocks.turret_chekhov.get().defaultBlockState());
			chekhov.setLevel(level);
			BlockEntityTurretJeremy jeremy = new BlockEntityTurretJeremy(core, ModBlocks.turret_jeremy.get().defaultBlockState());
			jeremy.setLevel(level);
			BlockEntityTurretSentry sentry = new BlockEntityTurretSentry(core, ModBlocks.turret_sentry.get().defaultBlockState());
			sentry.setLevel(level);
			Vec3 pivot = chekhov.getTurretPos();
			Zombie foe = EntityType.ZOMBIE.create(level);
			foe.setNoAi(true);
			placeEye(foe, pivot.add(0, 0, 6));
			ArmorStand friend = EntityType.ARMOR_STAND.create(level);
			placeEye(friend, pivot.add(0, 0, 3));
			level.addFreshEntity(foe);
			level.addFreshEntity(friend);
			List<EntityBulletBaseMK4> before = level.getEntitiesOfClass(EntityBulletBaseMK4.class, new AABB(core).inflate(64));
			try {
				for(BlockEntityTurretBaseNT t : List.of(chekhov, jeremy, sentry)) t.target = foe;
				helper.assertTrue(!chekhov.entityAcceptableTarget(friend) && chekhov.entityAcceptableTarget(foe), "the stand is a friend, the zombie a foe");

				LeafiaFeatures.turretFriendlyFire = false;
				helper.assertTrue(!LeafiaSednaSeams.FRIENDLY_FIRE.veto(chekhov, foe, null), "off: no veto");
				LeafiaFeatures.turretFriendlyFire = true;
				helper.assertTrue(LeafiaSednaSeams.FRIENDLY_FIRE.veto(chekhov, foe, null), "a Chekhov holds fire through the friend");
				helper.assertTrue(LeafiaSednaSeams.FRIENDLY_FIRE.veto(jeremy, foe, null), "so does a Jeremy");
				helper.assertTrue(!LeafiaSednaSeams.FRIENDLY_FIRE.veto(sentry, foe, null), "a sentry is not one of the two");
				helper.assertTrue(!LeafiaSednaSeams.FRIENDLY_FIRE.veto(chekhov, null, null), "no target, no veto");

				chekhov.slots[1] = XFactory50.bmg50_sp.ammo().toStack().copyWithCount(16);
				chekhov.rotationYaw = 0;
				chekhov.rotationPitch = 0;
				for(int tick = 0; tick < 48; tick++) chekhov.updateFiringTick();
				helper.assertTrue(chekhov.slots[1].getCount() == 16, "held through 48 firing ticks: " + chekhov.slots[1].getCount());

				helper.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
				helper.setBlock(new BlockPos(5, 3, 8), Blocks.STONE);
				helper.setBlock(new BlockPos(4, 2, 8), Blocks.STONE);
				helper.setBlock(new BlockPos(4, 3, 8), Blocks.STONE);
				placeEye(friend, pivot.add(0, 0, 4.9));
				helper.assertTrue(!LeafiaSednaSeams.FRIENDLY_FIRE.veto(chekhov, foe, null), "a block before the friend: the block is first, no veto");

				placeEye(friend, pivot.add(3, 0, 3));
				helper.setBlock(new BlockPos(5, 2, 8), Blocks.AIR);
				helper.setBlock(new BlockPos(5, 3, 8), Blocks.AIR);
				helper.setBlock(new BlockPos(4, 2, 8), Blocks.AIR);
				helper.setBlock(new BlockPos(4, 3, 8), Blocks.AIR);
				helper.assertTrue(!LeafiaSednaSeams.FRIENDLY_FIRE.veto(chekhov, foe, null), "a friend off the line: no veto");
				for(int tick = 0; tick < 48; tick++) chekhov.updateFiringTick();
				helper.assertTrue(chekhov.slots[1].getCount() < 16, "and the Chekhov fires: " + chekhov.slots[1].getCount());
			} finally {
				LeafiaFeatures.turretFriendlyFire = on;
				foe.discard();
				friend.discard();
				for(EntityBulletBaseMK4 b : level.getEntitiesOfClass(EntityBulletBaseMK4.class, new AABB(core).inflate(64))) if(!before.contains(b)) b.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * The sledge's hook keeps a control panel and nothing else (D-3072, inverting the "keeps nothing" test of D-3028, from before the panel
	 * existed); its tooltip line is upstream's and follows cosmeticOverhaul.
	 */
	@GameTest(template = EMPTY)
	public static void theSledgeKeepsAControlPanelAndItsLineFollowsTheSwitch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack sledge = new ItemStack(ModItems.shimmer_sledge.get());
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		for(Block block : new Block[] { Blocks.STONE, Blocks.OBSIDIAN, ModBlocks.turret_chekhov.get() })
			helper.assertTrue(!LeafiaSednaSeams.SLEDGE_HOOK.destroy((com.hbm.items.tool.WeaponSpecial) sledge.getItem(), sledge, null, level, pos, pos, block.defaultBlockState()),
					"keeps nothing else: " + block);
		for(Block panel : new Block[] { LeafiaBlocks.control_panel_custom.get(), LeafiaBlocks.control_panel_front.get() })
			helper.assertTrue(LeafiaSednaSeams.SLEDGE_HOOK.destroy((com.hbm.items.tool.WeaponSpecial) sledge.getItem(), sledge, null, level, pos, pos, panel.defaultBlockState()),
					"refuses to smash a control panel: " + panel);
		boolean on = LeafiaFeatures.cosmeticOverhaul;
		try {
			for(boolean flag : new boolean[] { true, false }) {
				LeafiaFeatures.cosmeticOverhaul = flag;
				List<Component> lines = new ArrayList<>(List.of(Component.literal("Shimmer Sledge"), Component.literal("base line")));
				LeafiaSednaSeams.onTooltip(new ItemTooltipEvent(sledge, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.of(level)));
				if(flag) helper.assertTrue(lines.size() == 3 && lines.get(1).getString().equals("Nate dont break that control panel!"), "on: upstream's line first: " + lines);
				else helper.assertTrue(lines.size() == 2, "off: nothing added");
				List<Component> other = new ArrayList<>(List.of(Component.literal("Stone")));
				LeafiaSednaSeams.onTooltip(new ItemTooltipEvent(new ItemStack(Items.STONE), null, other, TooltipFlag.NORMAL, Item.TooltipContext.of(level)));
				helper.assertTrue(other.size() == 1, "no other item gets it");
			}
		} finally {
			LeafiaFeatures.cosmeticOverhaul = on;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------
	// the grenade fillings
	// ------------------------------------------------------------------------------------------

	/** Appended after upstream's thirteen with upstream's colours and shells; their items are the addon's, found by the base's lookups. */
	@GameTest(template = EMPTY)
	public static void theFillingsAreAppendedAndBuildable(GameTestHelper helper) {
		EnumGrenadeFilling nul = LeafiaGrenadeFillings.nullFilling();
		EnumGrenadeFilling sol = LeafiaGrenadeFillings.solFilling();
		helper.assertTrue(nul != null && sol != null && nul.name().equals("LEAFIA_NULL") && sol.name().equals("LEAFIA_SOL"), "both constants exist");
		helper.assertTrue(nul.isExtension() && sol.isExtension() && nul.ordinal() >= 13 && sol.ordinal() >= 13, "appended after upstream's thirteen");
		helper.assertTrue(EnumGrenadeFilling.byName("LEAFIA_NULL") == nul && EnumGrenadeFilling.byName("LEAFIA_SOL") == sol, "found by name");
		helper.assertTrue(nul.explode == LeafiaGrenadeFillings.EXPLODE_NULL && sol.explode == LeafiaGrenadeFillings.EXPLODE_SOL, "upstream's explosions");
		helper.assertTrue(nul.bodyColor == 0xc2c2c2 && nul.labelColor == 0x383838, "null's colours");
		helper.assertTrue(sol.bodyColor == 0x114eaf && sol.labelColor == 0x000000, "solinium's colours");
		if(!LeafiaFeatures.leafiaGrenades) {
			helper.assertTrue(nul.compatibleShells.isEmpty() && sol.compatibleShells.isEmpty(), "off: no shell takes them");
			helper.assertTrue(LeafiaItems.grenade_filling_leafia_null == null, "off: no items");
			helper.succeed();
			return;
		}
		helper.assertTrue(nul.compatibleShells.equals(Set.of(EnumGrenadeShell.FRAG)), "null fits the fragmentation shell: " + nul.compatibleShells);
		helper.assertTrue(sol.compatibleShells.equals(Set.of(EnumGrenadeShell.NUKE)), "solinium the nuclear shell: " + sol.compatibleShells);
		ItemGrenadeFilling nulItem = LeafiaItems.grenade_filling_leafia_null.get();
		ItemGrenadeFilling solItem = LeafiaItems.grenade_filling_leafia_sol.get();
		helper.assertTrue(nulItem.getFilling() == nul && solItem.getFilling() == sol, "each item is its filling");
		helper.assertTrue(ItemGrenadeUniversal.partOf(nul) == nulItem && ItemGrenadeUniversal.partOf(sol) == solItem, "the base's part lookup finds them");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(nulItem).equals(LeafiaRef.rl("grenade_filling_leafia_null")), "in the addon's namespace");
		helper.assertTrue(nulItem.getDescriptionId().equals("item.hbm.grenade_filling.leafia_null")
				&& solItem.getDescriptionId().equals("item.hbm.grenade_filling.leafia_sol"), "under the base's filling key");
		EnumGrenadeFuze fuze = EnumGrenadeFuze.values()[0];
		ItemStack grenade = ItemGrenadeUniversal.make(EnumGrenadeShell.FRAG, nul, fuze);
		helper.assertTrue(ItemGrenadeUniversal.getFilling(grenade) == nul, "a built grenade reads back as null");
		Map<EnumGrenadeShell, Set<EnumGrenadeFilling>> buildable = new EnumMap<>(EnumGrenadeShell.class);
		ItemGrenadeUniversal.forEachBuildable(stack -> buildable.computeIfAbsent(ItemGrenadeUniversal.getShell(stack), s -> new HashSet<>()).add(ItemGrenadeUniversal.getFilling(stack)));
		helper.assertTrue(buildable.get(EnumGrenadeShell.FRAG).contains(nul) && buildable.get(EnumGrenadeShell.NUKE).contains(sol), "both are buildable");
		helper.assertTrue(!buildable.getOrDefault(EnumGrenadeShell.STICK, Set.of()).contains(nul) && !buildable.get(EnumGrenadeShell.FRAG).contains(sol), "and only in their shells");
		helper.succeed();
	}

	/**
	 * Null: the 7x7x7 cube round the grenade's block is air and everything in the box three below to four above is dead; something
	 * a block further out is untouched. Solinium: a range-25 MK3 at the blast speed, coefficient 1, no waste, the solinium rinse, and
	 * a cloud of 25 (caught at the join and never spawned).
	 */
	@GameTest(template = EMPTY16)
	public static void theFillingsExplodeAsUpstreams(GameTestHelper helper) {
		SpawnWindow.run(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos centre = helper.absolutePos(new BlockPos(8, 5, 8));
			for(int x = -4; x <= 4; x++) for(int y = -4; y <= 4; y++) for(int z = -4; z <= 4; z++) level.setBlock(centre.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(centre, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(centre.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(centre.offset(2, 1, 0), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(centre.offset(5, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(centre.offset(5, 1, 0), Blocks.AIR.defaultBlockState(), 3);
			Zombie in = EntityType.ZOMBIE.create(level);
			Zombie out = EntityType.ZOMBIE.create(level);
			for(Zombie z : List.of(in, out)) z.setNoAi(true);
			in.setPos(Vec3.atBottomCenterOf(centre.offset(2, 0, 0)));
			out.setPos(Vec3.atBottomCenterOf(centre.offset(5, 0, 0)));
			level.addFreshEntity(in);
			level.addFreshEntity(out);
			EntityGrenadeUniversal grenade = new EntityGrenadeUniversal(level);
			grenade.setPos(Vec3.atCenterOf(centre));
			try {
				LeafiaGrenadeFillings.EXPLODE_NULL.accept(grenade);
				int stone = 0;
				for(int x = -3; x <= 3; x++) for(int y = -3; y <= 3; y++) for(int z = -3; z <= 3; z++) if(!level.getBlockState(centre.offset(x, y, z)).isAir()) stone++;
				helper.assertTrue(stone == 0, "the 7x7x7 cube is empty: " + stone);
				helper.assertTrue(level.getBlockState(centre.offset(4, 0, 0)).is(Blocks.STONE) && level.getBlockState(centre.offset(0, -4, 0)).is(Blocks.STONE), "the shell outside it is not");
				helper.assertTrue(in.getHealth() <= 0F, "inside: dead");
				helper.assertTrue(out.isAlive() && out.getHealth() == out.getMaxHealth(), "outside: untouched");

				List<Entity> joined = new ArrayList<>();
				List<ExplosionEvent.Start> blasts = new ArrayList<>();
				intercepting(joined, blasts, () -> LeafiaGrenadeFillings.EXPLODE_SOL.accept(grenade));
				List<EntityNukeExplosionMK3> mk3 = joined.stream().filter(e -> e instanceof EntityNukeExplosionMK3).map(e -> (EntityNukeExplosionMK3) e).toList();
				List<Entity> clouds = joined.stream().filter(e -> e instanceof EntityCloudSolinium).toList();
				helper.assertTrue(mk3.size() == 1 && clouds.size() == 1 && joined.size() == 2, "one blast, one cloud: " + joined);
				EntityNukeExplosionMK3 blast = mk3.get(0);
				helper.assertTrue(blast.destructionRange == 25 && blast.speed == BombConfig.blastSpeed && blast.coefficient == 1.0F && !blast.waste && blast.extType == 1,
						"range 25, the blast speed, coefficient 1, no waste, the rinse");
				helper.assertTrue(blast.position().equals(grenade.position()) && clouds.get(0).position().equals(grenade.position()), "at the grenade");
			} finally {
				in.discard();
				out.discard();
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------
	// the pink door
	// ------------------------------------------------------------------------------------------

	/** A wooden door: top face only, both halves, a click toggles both, strength 3, wood, the axe and villagers' tags, lower half drops. */
	@GameTest(template = EMPTY)
	public static void thePinkDoorIsAWoodenDoor(GameTestHelper helper) {
		if(!LeafiaFeatures.pinkDoor) {
			helper.assertTrue(LeafiaBlocks.door_fuckoff == null && LeafiaItems.door_fuckoff == null, "off: no door");
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 5);
		ServerPlayer player = LeafiaCrateTestKit.mockPlayer(helper);
		ItemStack item = new ItemStack(LeafiaItems.door_fuckoff.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		BlockPos ground = helper.absolutePos(new BlockPos(2, 0, 2));
		InteractionResult side = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(ground), Direction.NORTH, ground, false)));
		helper.assertTrue(side == InteractionResult.FAIL && !level.getBlockState(ground.above()).is(LeafiaBlocks.door_fuckoff.get())
				&& !level.getBlockState(ground.north().above()).is(LeafiaBlocks.door_fuckoff.get()), "a side face fails and places nothing: " + side);
		item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
		BlockPos lower = ground.above();
		BlockState low = level.getBlockState(lower);
		BlockState up = level.getBlockState(lower.above());
		BlockPinkDoor door = LeafiaBlocks.door_fuckoff.get();
		helper.assertTrue(low.is(door) && up.is(door) && low.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && up.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
				"both halves placed from the top face");
		low.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(lower), Direction.NORTH, lower, false));
		helper.assertTrue(level.getBlockState(lower).getValue(DoorBlock.OPEN) && level.getBlockState(lower.above()).getValue(DoorBlock.OPEN), "a click opens both halves");
		level.getBlockState(lower.above()).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(lower.above()), Direction.NORTH, lower.above(), false));
		helper.assertTrue(!level.getBlockState(lower).getValue(DoorBlock.OPEN), "a click on the upper half closes it");

		helper.assertTrue(low.getDestroySpeed(level, lower) == 3.0F && door.getExplosionResistance() == 3.0F, "hardness 3, resistance 3");
		helper.assertTrue(low.getSoundType() == SoundType.WOOD && !low.requiresCorrectToolForDrops(), "wood, no tool needed");
		helper.assertTrue(low.is(BlockTags.MINEABLE_WITH_AXE) && low.is(BlockTags.WOODEN_DOORS), "an axe's, and a wooden door");
		helper.assertTrue(item.getMaxStackSize() == 64, "the item stacks to 64, so the recipe's three fit");
		List<ItemStack> lowerDrops = Block.getDrops(low, level, lower, null);
		List<ItemStack> upperDrops = Block.getDrops(up, level, lower.above(), null);
		helper.assertTrue(lowerDrops.size() == 1 && lowerDrops.get(0).is(LeafiaItems.door_fuckoff.get()) && upperDrops.isEmpty(), "the lower half drops the door, the upper nothing");
		helper.succeed();
	}

	/**
	 * The detonation, caught at the joins and at the blast: the door goes, one power-15 blast at its centre, 44 RBMK debris of upstream's
	 * five kinds (lids thrown higher), 43 shrapnels, 25 falling corium blocks between y 455 and 504, two boxcars at 350, seventeen
	 * catapult nukes of 100 at 0.15 down from 50 + 15i above the ground, one falling bomb of hydrogen 174 and dirty 50 80 up, and
	 * one rainbow beam at every living thing within 30 (D-3031).
	 */
	@GameTest(template = EMPTY)
	public static void thePinkDoorsDetonationIsUpstreams(GameTestHelper helper) {
		if(!LeafiaFeatures.pinkDoor) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPinkDoor door = LeafiaBlocks.door_fuckoff.get();
		BlockPos lower = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlock(lower, door.defaultBlockState(), 2);
		level.setBlock(lower.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
		List<Entity> joined = new ArrayList<>();
		List<ExplosionEvent.Start> blasts = new ArrayList<>();
		Cow witness = helper.spawn(EntityType.COW, new BlockPos(5, 1, 5));
		Vec3 doorCentre = Vec3.atCenterOf(lower);
		List<net.minecraft.world.entity.LivingEntity> inReach = com.leafia.contents.effects.zomg.LeafiaZomg.targets(level, doorCentre.x, doorCentre.y, doorCentre.z,
				BlockPinkDoor.ZOMG_REACH, null);
		try {
			intercepting(joined, blasts, () -> helper.assertTrue(door.explode(level, lower) == com.hbm.interfaces.IBomb.BombReturnCode.DETONATED, "detonated"));
		} finally {
			witness.discard();
		}
		helper.assertTrue(level.getBlockState(lower).isAir(), "the door is gone");
		helper.assertTrue(blasts.size() == 1 && blasts.get(0).getExplosion().radius() == BlockPinkDoor.BLAST_POWER && BlockPinkDoor.BLAST_POWER == 15F
				&& blasts.get(0).getExplosion().center().equals(Vec3.atCenterOf(lower)), "one power-15 blast at the door's centre");

		Map<DebrisType, Integer> debris = new EnumMap<>(DebrisType.class);
		int shrapnel = 0, boxcars = 0, corium = 0, nukes = 0, bombs = 0, rainbows = 0;
		Set<Integer> heights = new HashSet<>();
		for(Entity e : joined) {
			if(e instanceof EntityRBMKDebris d) {
				debris.merge(d.getDebrisType(), 1, Integer::sum);
				double vy = d.getDeltaMovement().y;
				boolean lid = d.getDebrisType() == DebrisType.LID;
				helper.assertTrue(lid ? vy >= 1.0 && vy < 2.5 : vy >= 0.5 && vy < 2.0, "debris thrown up as upstream: " + d.getDebrisType() + " " + vy);
			} else if(e instanceof EntityShrapnel) {
				shrapnel++;
			} else if(e instanceof EntityBoxcar) {
				boxcars++;
				helper.assertTrue(e.getY() == 350D, "a boxcar from 350");
			} else if(e instanceof FallingBlockEntity f) {
				corium++;
				helper.assertTrue(f.getBlockState().is(ModBlocks.corium_block.get()) && f.getY() >= 455 && f.getY() < 505
						&& Math.abs(f.getX() - lower.getX()) <= 101 && Math.abs(f.getZ() - lower.getZ()) <= 101, "corium high above: " + f.position());
			} else if(e instanceof EntityBulletBaseMK4 b) {
				nukes++;
				helper.assertTrue(b.config == XFactoryCatapult.nuke_high && b.damage == 100F && b.getDeltaMovement().equals(new Vec3(0, -0.15, 0)), "a catapult nuke of 100 falling at 0.15");
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) b.getX(), lower.getY());
				heights.add((int) Math.round(b.getY()) - ground);
				helper.assertTrue(b.getX() - lower.getX() >= -10 && b.getX() - lower.getX() < 31 && b.getZ() - lower.getZ() >= -10 && b.getZ() - lower.getZ() < 31,
						"scattered over -10 to +30: " + b.position());
			} else if(e instanceof com.leafia.contents.effects.zomg.EntityRainbow r) {
				rainbows++;
				helper.assertTrue(r.position().equals(doorCentre) && Math.abs(r.getDeltaMovement().length() - com.leafia.contents.effects.zomg.LeafiaZomg.SPEED) < 1e-9,
						"a rainbow from the door's centre at 1 a tick: " + r.position() + " " + r.getDeltaMovement());
			} else if(e instanceof EntityFallingNuke n) {
				bombs++;
				float[] s = n.strengths();
				helper.assertTrue(s[0] == 0 && s[1] == 0 && s[2] == 174 && s[3] == 0 && s[4] == 50 && s[5] == 0 && s[6] == 0, "hydrogen 174, dirty 50");
				helper.assertTrue(n.getX() == lower.getX() && n.getZ() == lower.getZ()
						&& n.getY() == level.getHeight(Heightmap.Types.MOTION_BLOCKING, lower.getX(), lower.getZ()) + 80, "80 above the ground over the door");
			}
		}
		helper.assertTrue(debris.getOrDefault(DebrisType.BLANK, 0) == 15 && debris.getOrDefault(DebrisType.GRAPHITE, 0) == 10 && debris.getOrDefault(DebrisType.LID, 0) == 4
				&& debris.getOrDefault(DebrisType.FUEL, 0) == 6 && debris.getOrDefault(DebrisType.ROD, 0) == 9 && !debris.containsKey(DebrisType.ELEMENT), "15/10/4/6/9 debris: " + debris);
		helper.assertTrue(shrapnel == 35 + 8, "35 + 8 shrapnels: " + shrapnel);
		helper.assertTrue(corium == 25 && boxcars == 2 && nukes == 17 && bombs == 1, "25 corium, 2 boxcars, 17 nukes, 1 bomb: " + corium + " " + boxcars + " " + nukes + " " + bombs);
		Set<Integer> want = new HashSet<>();
		for(int i = 0; i < 19; i++) if(i != 2 && i != 3) want.add(50 + i * 15);
		helper.assertTrue(heights.equals(want), "nukes at 50 + 15i above the ground, i not 2 or 3: " + heights);
		helper.assertTrue(inReach.contains(witness) && rainbows == inReach.size(),
				"one rainbow at each of the " + inReach.size() + " living things within 30, the cow among them: " + rainbows);
		helper.assertTrue(joined.size() == 44 + 43 + 25 + 2 + 17 + 1 + rainbows, "and nothing else: " + joined.size());
		helper.succeed();
	}

	/**
	 * {@code ExplosionChaos.zomg} (D-3031): it aims one rainbow at each living thing within the reach and spares the one it is
	 * told to; a rainbow flies straight at a constant speed and hits the first entity on its path for 10,000 to 100,000
	 * subatomic damage, then is gone.
	 */
	@GameTest(template = EMPTY)
	public static void theZomgVolleyAimsAtEveryLivingThingAndKills(GameTestHelper helper) {
		if(!LeafiaFeatures.pinkDoor) {
			helper.assertTrue(com.leafia.init.LeafiaEntities.RAINBOW == null, "the rainbow is registered with the pink door off");
			helper.succeed();
			return;
		}
		helper.assertTrue(com.leafia.contents.effects.zomg.EntityRainbow.DAMAGE_MIN == 10000F && com.leafia.contents.effects.zomg.EntityRainbow.DAMAGE_MAX == 100000F
				&& com.leafia.contents.effects.zomg.LeafiaZomg.SPEED == 1D && BlockPinkDoor.ZOMG_REACH == 30D, "10,000 to 100,000, 1 a tick, 30 blocks");
		ServerLevel level = helper.getLevel();
		Vec3 from = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Pig near = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 1));
		Pig spared = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 4));
		try {
			List<net.minecraft.world.entity.LivingEntity> targets = com.leafia.contents.effects.zomg.LeafiaZomg.targets(level, from.x, from.y, from.z, 30, spared);
			helper.assertTrue(targets.contains(near) && !targets.contains(spared), "the near pig is a target, the spared one is not");
			com.leafia.contents.effects.zomg.EntityRainbow beam = new com.leafia.contents.effects.zomg.EntityRainbow(level, from.x, from.y, from.z,
					new Vec3(near.getX() - from.x, near.getEyeY() - from.y, near.getZ() - from.z).normalize(), null);
			level.addFreshEntity(beam);
			for(int t = 0; t < 10 && !beam.isRemoved(); t++) beam.tick();
			helper.assertTrue(beam.isRemoved(), "the beam is gone after its hit");
			helper.assertTrue(!near.isAlive() || near.getHealth() <= 0, "and the pig took at least 10,000: " + near.getHealth());
			for(int i = 0; i < 64; i++) {
				float d = beam.rollDamage();
				helper.assertTrue(d >= 10000F && d < 100000F, "a roll in range: " + d);
			}
		} finally {
			near.discard();
			spared.discard();
		}
		helper.succeed();
	}
}
