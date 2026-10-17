package com.leafia.contents.gear.combat.guns;

import com.hbm.entity.projectile.EntityBulletBeamBase;
import com.hbm.items.weapon.sedna.BulletConfig.BulletRenderer;
import com.hbm.items.weapon.sedna.factory.LegoClient;
import com.hbm.render.item.weapon.sedna.GunRenderers;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.combat.guns.am_rifle.AMRifleRender;
import com.llib.math.LeafiaColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import static com.hbm.items.weapon.sedna.factory.GunFactoryClient.setRendererBulkBeam;
import static com.hbm.items.weapon.sedna.factory.LegoClient.renderStandardLaser;

/**
 * The anti-mass rifle's client half (D-3028): its durability bar, its beam's colour and its held renderer.
 *
 * <p>Ported from {@code com.leafia.contents.gear.combat.guns.GunInitLocal}, which upstream's client proxy ran from
 * {@code registerGunCfg}; here {@code LeafiaSednaClient} runs it at client setup, after the base mod's {@code GunFactoryClient.init}
 * has had its turn. The beam is the base mod's standard laser in a colour drawn afresh every frame, a random hue at full saturation
 * and half value, so the beam flickers through the rainbow. The renderer is registered in the base mod's table, which is where every
 * client hook looks a held gun up; upstream's {@code ItemRendererInit.register(Guns.am_rifle, new AMRifleRender())} line.
 */
@OnlyIn(Dist.CLIENT)
public class GunInitLocal {

	public static void init() {
		if(LeafiaItems.am_rifle == null) return;
		LeafiaItems.am_rifle.get().getConfig(null, 0).hud(LegoClient.HUD_COMPONENT_DURABILITY);
		setRendererBulkBeam(RENDER_LASER_RAINBOW, GunInit.am_beam);
		GunRenderers.register(LeafiaItems.am_rifle.get(), new AMRifleRender());
	}

	public static BulletRenderer<EntityBulletBeamBase> RENDER_LASER_RAINBOW = (bullet, interp, pose, buffers, light) -> {
		LeafiaColor color = LeafiaColor.fromHSV(bullet.level().random.nextDouble() * 360, 1, 0.5);
		renderStandardLaser(bullet, interp, pose, buffers, (int) (color.red * 255), (int) (color.green * 255), (int) (color.blue * 255));
	};
}
