package com.leafia.contents.gear.combat.guns;

import java.util.Objects;
import java.util.function.BiConsumer;

import com.hbm.entity.projectile.EntityBulletBeamBase;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.Crosshair;
import com.hbm.items.weapon.sedna.GunConfig;
import com.hbm.items.weapon.sedna.ItemGunBaseNT.WeaponQuality;
import com.hbm.items.weapon.sedna.Receiver;
import com.hbm.items.weapon.sedna.factory.GunFactory.EnumModSpecial;
import com.hbm.items.weapon.sedna.factory.XFactoryEnergy;
import com.hbm.items.weapon.sedna.mags.MagazineInfinite;
import com.hbm.items.weapon.sedna.mods.IWeaponMod;
import com.hbm.items.weapon.sedna.mods.XWeaponModManager;
import com.hbm.items.weapon.sedna.mods.XWeaponModManager.WeaponModDefinition;
import com.hbm.util.DamageResistanceHandler;
import com.hbm.util.EntityDamageUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.ILockonWeapon;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifle;
import com.leafia.contents.miscellanous.slop.SlopTE;
import com.leafia.init.LeafiaDamageSources;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.hbm.items.weapon.sedna.mods.XWeaponModManager.ID_LAS_SHOTGUN;
import static com.hbm.items.weapon.sedna.mods.XWeaponModManager.ID_SCOPE;

/**
 * The anti-mass rifle's common half: its beam, its gun config, its two weapon mods and what those mods do on it (D-3028).
 *
 * <p>Ported from {@code com.leafia.contents.gear.combat.guns.GunInit}, and the two mixins on the base mod's weapon mods,
 * {@code MixinWeaponModScope} and {@code MixinWeaponModLasShotgun}, which become the base's per-gun override
 * ({@link XWeaponModManager#setModOverride}, D-2205):
 * <ul>
 * <li>the <b>scope</b> on this rifle shows {@code XFactoryEnergy.scope_luna} instead of the generic one, and is otherwise the scope;</li>
 * <li>the <b>refractor</b> ({@code LAS_SHOTGUN}) on this rifle keeps one projectile and the rifle's own crosshair, halves the base
 *     damage instead of taking 35% of it, and is otherwise the refractor (its spread). It also unlocks the lock-on, which
 *     {@link AMRifle#canLockon} reads.</li>
 * </ul>
 *
 * <p>The numbers are upstream's: 200 damage (100 with the refractor), durability 50, a ten-tick draw, the circle crosshair, an
 * infinite magazine of {@link #am_beam}, the laser rifle's muzzle offset, and a beam that lives five ticks, never spreads, is a
 * {@code LASER} and goes through everything it meets. A hit sets the target's health to what it would be after the full damage
 * whatever armour, resistance or invulnerability said, and kills what that leaves at zero outright.
 *
 * <p>{@link #am_beam} is built by {@link #init} <b>whether or not the rifle is switched on</b>: a {@code BulletConfig} takes the next
 * id in a process-wide list the moment it exists, and the id is what a beam sends to the client, so a config that existed on one
 * side and not the other would shift every config built after it. The item, its mod definitions and the overrides follow the
 * switch.
 */
public class GunInit {

	/** The rifle's beam; built at mod construction, right after the base mod's own configs. */
	public static BulletConfig am_beam;

	/** The rifle's damage, before the refractor: {@code dmg(200)} and {@code float dmg = 200} upstream. */
	public static final float AM_DAMAGE = 200F;

	public static BiConsumer<EntityBulletBeamBase, HitResult> LAMBDA_AM_BEAM_HIT = (bullet, mop) -> {
		if(mop instanceof EntityHitResult hit) {
			Entity entity = hit.getEntity();

			if(entity instanceof LivingEntity && ((LivingEntity) entity).getHealth() <= 0) return;

			DamageSource source = LeafiaDamageSources.of(bullet.level(), LeafiaDamageSources.am_rifle, bullet, bullet.getThrower());
			if(entity instanceof LivingEntity living) {
				float health = living.getHealth();
				EntityDamageUtil.attackEntityFromIgnoreIFrame(entity, source, bullet.damage);
				float desiredHealth = Math.max(0, health - bullet.damage);
				if(living.getHealth() > desiredHealth) {
					living.setHealth(desiredHealth);
					if(desiredHealth <= 0) {
						SlopTE.tryKill(living);
						living.die(source);
					}
				}
			} else
				EntityDamageUtil.attackEntityFromIgnoreIFrame(entity, source, bullet.damage);
		}
	};

	/** Builds the beam (always) and hangs the lock-on sweep on the server tick. Called once, from the addon's constructor. */
	public static void init() {
		if(am_beam != null) return;
		am_beam = new BulletConfig().setupDamageClass(DamageResistanceHandler.DamageClass.LASER).setBeam().setSpread(0.0F).setLife(5)
				.setRenderRotations(false).setOnBeamImpact(LAMBDA_AM_BEAM_HIT).setDoesPenetrate(true);
		if(LeafiaFeatures.amRifle) NeoForge.EVENT_BUS.addListener(GunInit::onServerTick);
	}

	/** The rifle's one config, upstream's {@code new GunConfig()...} chain, for the item's constructor. */
	public static GunConfig rifleConfig() {
		return new GunConfig() // no longer secret because fucking jei exposes its recipe
				.dura(50).draw(10).crosshair(Crosshair.CIRCLE)
				.rec(
						new Receiver(0)
								.dmg(AM_DAMAGE)
								.mag(new MagazineInfinite(am_beam))
								.offset(0.75, -0.0625 * 1.5, -0.1875)
								.setupStandardFire().fire(AMRifle::fire)
				)
				.setupStandardConfiguration().pp(AMRifle::primary)
				.anim(AMRifle.LAMBDA_AMRIFLE).orchestra(AMRifle::orchestra);
	}

	/** The rifle's {@link WeaponQuality}: {@code SECRET}, so no creative tab and on the base's hidden list. */
	public static final WeaponQuality QUALITY = WeaponQuality.SECRET;

	/**
	 * Upstream's {@code initMods}: the scope and the refractor may go on the rifle, as the same mod objects the base mod's guns
	 * carry; then the two overrides. After {@code XWeaponModManager.init()}, which this calls first (a second call does nothing),
	 * so the order of the two common-setup tasks does not matter.
	 */
	public static void initMods() {
		if(!LeafiaFeatures.amRifle || LeafiaItems.am_rifle == null) return;
		XWeaponModManager.init();
		Item rifle = LeafiaItems.am_rifle.get();
		getDef(EnumModSpecial.SCOPE).addMod(new Item[] { rifle }, XWeaponModManager.idToMod.get(ID_SCOPE));
		getDef(EnumModSpecial.LAS_SHOTGUN).addMod(new Item[] { rifle }, XWeaponModManager.idToMod.get(ID_LAS_SHOTGUN));
		XWeaponModManager.setModOverride(rifle, ID_SCOPE, GunInit::scopeOnRifle);
		XWeaponModManager.setModOverride(rifle, ID_LAS_SHOTGUN, GunInit::refractorOnRifle);
	}

	/** {@code MixinWeaponModScope}: the luna scope's texture on this rifle, the scope's own answer for everything else. */
	public static Object scopeOnRifle(IWeaponMod mod, Object base, ItemStack gun, String key, Object parent) {
		if(Objects.equals(key, GunConfig.O_SCOPETEXTURE)) return XFactoryEnergy.scope_luna;
		return XWeaponModManager.evalModDefault(mod, base, gun, key, parent);
	}

	/**
	 * {@code MixinWeaponModLasShotgun}: on this rifle the refractor leaves the projectile count and the crosshair as they are and
	 * halves the base damage; every other key is the refractor's own.
	 */
	public static Object refractorOnRifle(IWeaponMod mod, Object base, ItemStack gun, String key, Object parent) {
		if(Objects.equals(key, Receiver.F_SPLITPROJECTILES) || Objects.equals(key, GunConfig.O_CROSSHAIR)) return base;
		if(Objects.equals(key, Receiver.F_BASEDAMAGE)) return (Float) base / 2;
		return XWeaponModManager.evalModDefault(mod, base, gun, key, parent);
	}

	public static WeaponModDefinition getDef(EnumModSpecial num) {
		return XWeaponModManager.stackToMod.get(new ComparableStack(XWeaponModManager.modItem("special", num)));
	}

	/** Upstream's {@code LeafiaPassiveServer.priorTick} lock sweep, once a second. */
	private static void onServerTick(ServerTickEvent.Post event) {
		if(event.getServer().getTickCount() % 20 == 0) ILockonWeapon.sweepLockons();
	}
}
