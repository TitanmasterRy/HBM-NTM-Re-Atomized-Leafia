package com.leafia.contents.gear.combat.guns.am_rifle;

import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import com.hbm.items.weapon.sedna.mods.XWeaponModManager;
import com.hbm.render.anim.HbmAnimationsClient;
import com.hbm.render.item.weapon.sedna.GunBakes;
import com.hbm.render.item.weapon.sedna.ItemRenderWeaponBase;
import com.hbm.render.util.ObjUtil;
import com.leafia.lib.LeafiaRef;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * The anti-mass rifle in the first person hand (D-3028): the body and the spinning fan on the rifle's own skin, the laser rifle's
 * grip, barrel base and barrel on that gun's skin, the heavy revolver's scope when the scope is fitted (and nothing at all once fully
 * aimed through it, when the base mod draws the scope overlay instead), and the laser rifle's shotgun barrel in place of the barrel
 * when the refractor is.
 *
 * <p>Ported from {@code com.leafia.contents.gear.combat.guns.am_rifle.AMRifleRender}, transform for transform; see the base mod's
 * {@code ItemRenderWeaponBase} for what every sedna renderer changed (a {@code PoseStack} for {@code GL11}, a baked part per OBJ group).
 * The rifle's own OBJ is the addon's, so its five groups are baked through the base mod's part-bake seam ({@link AMRifleBakes#PARTS} of
 * {@link AMRifleBakes#OBJ}: {@code GunBakes.register}, drawn with {@code GunBakes.baked}; the model JSON is made by
 * a generator script, D-3041) and drawn with {@code ObjUtil.renderRetextured}; the scope and the shotgun barrel are the
 * base mod's own part bakes. The fan turns by the angle the server keeps in the stack ({@code leafia_rot}), interpolated.
 *
 * <p>Two things carried as upstream has them. {@link #getViewFOV} writes {@link AMRifle#isADS}, which is how the lock-on cone narrows
 * when aimed. And the muzzle flash is drawn only from 550 ms after the last shot, with a flash that lasts 150 ms from that same shot,
 * so it never shows; kept, not fixed (D-3028).
 *
 * <p>The slot, the ground, a frame and the third person hand draw the static model ({@code item/gun_leafia_amrifle}: the five groups
 * with the fan at rest), carried in the hand as the laser rifle is ({@link #thirdPersonKey}: upstream's {@code setupThirdPerson} is
 * the laser rifle's, scale 1.25 and four forward).
 */
@OnlyIn(Dist.CLIENT)
public class AMRifleRender extends ItemRenderWeaponBase {

	public static final ResourceLocation tex = LeafiaRef.rl("textures/models/weapons/am_rifle.png");
	public static final ResourceLocation lasrifle = ResourceLocation.fromNamespaceAndPath("hbm", "textures/models/weapons/lasrifle.png");
	public static final ResourceLocation mods = ResourceLocation.fromNamespaceAndPath("hbm", "textures/models/weapons/lasrifle_mods.png");
	public static final ResourceLocation lilmac_scope_tex = ResourceLocation.fromNamespaceAndPath("hbm", "textures/models/weapons/lilmac_scope.png");

	/** The rifle's OBJ and its five groups, as the base mod's part-bake seam names them ({@link AMRifleBakes}, D-3041). */
	public static final ResourceLocation OBJ = AMRifleBakes.OBJ;
	public static final String[] PARTS = AMRifleBakes.PARTS;

	public static void registerParts(ModelEvent.RegisterAdditional event) {
		GunBakes.register(event, OBJ, PARTS);
	}

	private static void draw(Frame f, ResourceLocation texture, String group) {
		ObjUtil.renderRetextured(GunBakes.baked(OBJ, group), f.pose, f.buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), f.light, OverlayTexture.NO_OVERLAY);
	}

	@Override
	public String thirdPersonKey() {
		return "ItemRenderLasrifle";
	}

	@Override
	protected float getTurnMagnitude(ItemStack stack) {
		return ItemGunBaseNT.getIsAiming(stack) ? 2.5F : -0.25F;
	}

	@Override
	public float getViewFOV(ItemStack stack, float fov) {
		float aimingProgress = ItemGunBaseNT.prevAimingProgress + (ItemGunBaseNT.aimingProgress - ItemGunBaseNT.prevAimingProgress) * interp;
		AMRifle.isADS = aimingProgress == 1;
		return fov * (1 - aimingProgress * (hasScope(stack) ? 7 / 8F : 0.2F));
	}

	@Override
	public void setupFirstPerson(Frame f) {
		f.pose.translate(0, 0, 0.875);

		float offset = 0.8F;
		if(hasScope(f.stack)) {
			standardAimingTransform(f.pose,
					-1.5F * offset, -1.5F * offset, 2.5F * offset,
					0, -8.375 / 8D, 1.2);
		} else {
			standardAimingTransform(f.pose,
					-1.5F * offset, -1.5F * offset, 2.5F * offset,
					0, -7.25 / 8D, 1);
		}
	}

	/** The fan's angle this frame, from the server's two angles in the stack. */
	public static double fanAngle(ItemStack stack, float interp) {
		CompoundTag tag = AMRifle.tag(stack);
		return tag.getDouble(AMRifle.KEY_PREV_ROT) + (tag.getDouble(AMRifle.KEY_ROT) - tag.getDouble(AMRifle.KEY_PREV_ROT)) * interp;
	}

	@Override
	public void renderFirstPerson(Frame f) {

		if(hasScope(f.stack) && ItemGunBaseNT.prevAimingProgress == 1 && ItemGunBaseNT.aimingProgress == 1) return;
		ItemGunBaseNT gun = (ItemGunBaseNT) f.stack.getItem();
		double scale = 0.3125D;
		f.pose.scale((float) scale, (float) scale, (float) scale);

		double[] equip = HbmAnimationsClient.getRelevantTransformation("EQUIP");
		double[] recoil = HbmAnimationsClient.getRelevantTransformation("RECOIL");
		double[] charge = HbmAnimationsClient.getRelevantTransformation("CHARGE");

		f.pose.translate(0, -1, -6);
		f.pose.mulPose(Axis.XP.rotationDegrees((float) equip[0]));
		f.pose.translate(0, 1, 6);

		f.pose.translate(0, 0, recoil[2]);

		f.pose.translate(0, 0.5, -1.5);
		draw(f, tex, "Gun");
		f.pose.pushPose();
		f.pose.mulPose(Axis.ZP.rotationDegrees((float) fanAngle(f.stack, interp)));
		draw(f, tex, "Fan");
		f.pose.popPose();
		if(hasScope(f.stack)) {
			f.pose.pushPose();
			f.pose.translate(0, -0.85, -1.25);
			f.pose.mulPose(Axis.YP.rotationDegrees(90));
			f.pose.scale(0.75F, 0.75F, 0.75F);
			f.part(lilmac_scope_tex, "weapons/lilmac", "Scope");
			f.pose.popPose();
		}

		draw(f, lasrifle, "Grip");
		f.pose.translate(0, 0, charge[2]);
		draw(f, lasrifle, "BarrelBase");
		f.pose.translate(0, 0, -charge[2]);
		if(!hasRefractor(f.stack)) draw(f, lasrifle, "Barrel");

		f.pose.translate(0, -0.5, 1.5);
		if(hasRefractor(f.stack)) f.part(mods, "weapons/lasrifle_mods", "BarrelShotgun");

		long shot = gun.lastShot[0] + 550;
		if(System.currentTimeMillis() >= shot) {
			f.pose.pushPose();
			f.pose.translate(0, 1.5, 12);
			f.pose.mulPose(Axis.YP.rotationDegrees(90));
			renderLaserFlash(f, gun.lastShot[0], 150, 1.5D, 0xff0000);
			f.pose.translate(0, 0, -0.25);
			renderLaserFlash(f, gun.lastShot[0], 150, 0.75D, 0xff8000);
			f.pose.popPose();
		}
	}

	public boolean hasScope(ItemStack stack) {
		return XWeaponModManager.hasUpgrade(stack, 0, XWeaponModManager.ID_SCOPE);
	}

	public boolean hasRefractor(ItemStack stack) {
		return XWeaponModManager.hasUpgrade(stack, 0, XWeaponModManager.ID_LAS_SHOTGUN);
	}
}
