package com.leafia.overwrite_contents;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blockentity.turret.BlockEntityTurretBaseNT;
import com.hbm.blockentity.turret.BlockEntityTurretChekhov;
import com.hbm.blockentity.turret.BlockEntityTurretJeremy;
import com.hbm.items.ModItems;
import com.hbm.items.tool.WeaponSpecial;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The addon's consumers of the base mod's sedna-family seams (D-2205), replacing five of upstream's mixins (D-3028):
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code MixinTileEntityTurretBaseNT} + {@code IMixinTileEntityTurretBaseNT}</td><td>{@link #detectFriendlyFire}, the
 *     line-of-fire test the interface added to every turret</td><td>-</td></tr>
 * <tr><td>{@code MixinTileEntityTurretChekhov}, {@code MixinTileEntityTurretJeremy}</td><td>{@link #FRIENDLY_FIRE}, a
 *     {@code FireVeto} for those two kinds (the friendly turret is a Chekhov, so it is covered as upstream's mixin covered it)</td>
 *     <td>{@code overhauls.turretFriendlyFire}</td></tr>
 * <tr><td>{@code MixinWeaponSpecial} ({@code onItemUse})</td><td>{@link #SLEDGE_HOOK}, a {@code DestroyHook} that keeps a control panel</td>
 *     <td>-</td></tr>
 * <tr><td>{@code MixinWeaponSpecial} ({@code addInformation})</td><td>{@link #onTooltip}</td><td>{@code overhauls.cosmeticOverhaul}</td></tr>
 * </table>
 *
 * <p><b>The friendly-fire test.</b> A ray from the turret's pivot to where it aims on the target, through blocks and then through
 * every pickable entity but the target; if the first thing it meets is an entity the turret would not target, the shot is held. A
 * block first, or nothing, fires as before. The mixins asked after {@code getFirstConfigLoaded()} and only with a target, and the
 * veto is asked at that point (D-2205), so a held shot still counts the turret's timer down (a Chekhov stays spun up). Upstream's
 * ray is NTM-CE's {@code Library.rayTraceIncludeEntities}, which neither this base nor {@code hbm-src} has, so {@link #rayTrace} is
 * this port's: the nearest of the first block and the nearest pickable entity whose box (grown by its pick radius) the segment
 * crosses.
 *
 * <p><b>The sledge.</b> Upstream sent the client its "forced schizo" packet when the shimmer sledge broke a {@code BlockControlPanel}
 * (and then broke it). With the Custom Control Panel in (D-3072) the hook has its one case back, and answers true for a
 * {@link ControlPanelBlock} (the desk and the upright front, which extends it): the sledge refuses to smash it, as
 * the port's plan for the panel says ("Nate dont break that control panel!" stops being a joke with no target). <b>A
 * divergence from the upstream mixin, which punished the break rather than preventing it, owner question in D-3072:</b> the panel is
 * kept and no packet is sent, so {@code FORCE_SCHIZO} is still a registered receiver with no sender. Every other block is the base
 * mod's, as before.
 */
public final class LeafiaSednaSeams {

	private LeafiaSednaSeams() {}

	private static boolean installed = false;

	/** The tooltip line upstream put at the head of the shimmer sledge's, verbatim. */
	public static final String SLEDGE_LINE = "Nate dont break that control panel!";

	public static void install() {
		if(installed) return;
		installed = true;
		BlockEntityTurretBaseNT.addFireVeto(FRIENDLY_FIRE);
		WeaponSpecial.addDestroyHook(SLEDGE_HOOK);
		NeoForge.EVENT_BUS.addListener(LeafiaSednaSeams::onTooltip);
	}

	/** The Chekhov's and the Jeremy's mixins: hold the shot when a friend is in the line of fire. Reads its switch each time. */
	public static final BlockEntityTurretBaseNT.FireVeto FRIENDLY_FIRE = (turret, target, config) -> {
		if(!LeafiaFeatures.turretFriendlyFire) return false;
		return vetoes(turret, target, config);
	};

	/** The veto's body, without the switch: what the two mixins did. */
	public static boolean vetoes(BlockEntityTurretBaseNT turret, @Nullable Entity target, @Nullable BulletConfig config) {
		if(!(turret instanceof BlockEntityTurretChekhov) && !(turret instanceof BlockEntityTurretJeremy)) return false;
		if(target != null) {
			return detectFriendlyFire(turret, target);
		}
		return false;
	}

	/** {@code IMixinTileEntityTurretBaseNT.leafia$detectFriendlyFire}: whether the first thing between barrel and target is a non-target. */
	public static boolean detectFriendlyFire(BlockEntityTurretBaseNT turret, Entity e) {
		Vec3 pos = turret.getTurretPos();
		Vec3 ent = turret.getEntityPos(e);
		HitResult res = rayTrace(turret.getLevel(), pos, ent, turret.target);
		if(res == null)
			return false;
		else {
			if(res.getType() == HitResult.Type.MISS)
				return false;
			if(res.getType() == HitResult.Type.BLOCK)
				return false;
			if(res instanceof EntityHitResult hit) {
				return !turret.entityAcceptableTarget(hit.getEntity());
			}
			return false;
		}
	}

	/** The first block or entity (other than {@code exclude}) on the segment from {@code from} to {@code to}, or null for nothing. */
	@Nullable
	public static HitResult rayTrace(Level level, Vec3 from, Vec3 to, @Nullable Entity exclude) {
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceToSqr(block.getLocation());
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

		Entity nearest = null;
		Vec3 nearestHit = null;
		double nearestDist = Double.MAX_VALUE;
		List<Entity> list = level.getEntities(exclude, new AABB(from, end).inflate(1.0D), e -> e.isPickable() && !e.isSpectator());
		for(Entity entity : list) {
			AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
			Vec3 hit = box.contains(from) ? from : box.clip(from, end).orElse(null);
			if(hit == null) continue;
			double dist = from.distanceToSqr(hit);
			if(dist < nearestDist) {
				nearestDist = dist;
				nearest = entity;
				nearestHit = hit;
			}
		}

		if(nearest != null && nearestDist <= blockDist) return new EntityHitResult(nearest, nearestHit);
		if(block.getType() != HitResult.Type.MISS) return block;
		return null;
	}

	/** {@code MixinWeaponSpecial.onItemUse}: the control-panel case (see the class note). Keeps a panel, nothing else. */
	public static final WeaponSpecial.DestroyHook SLEDGE_HOOK = LeafiaSednaSeams::onSledgeDestroy;

	public static boolean onSledgeDestroy(WeaponSpecial item, ItemStack stack, @Nullable Player player, Level level, BlockPos clicked, BlockPos at, BlockState state) {
		return state.getBlock() instanceof ControlPanelBlock;
	}

	/** {@code MixinWeaponSpecial.addInformation}: one line at the head of the shimmer sledge's tooltip. */
	public static void onTooltip(ItemTooltipEvent event) {
		if(!LeafiaFeatures.cosmeticOverhaul) return;
		if(event.getItemStack().getItem() != ModItems.shimmer_sledge.get()) return;
		List<Component> lines = event.getToolTip();
		lines.add(Math.min(1, lines.size()), Component.literal(SLEDGE_LINE));
	}
}
