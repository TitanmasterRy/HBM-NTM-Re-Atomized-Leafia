package com.leafia.overwrite_contents.detonator;

import com.hbm.config.GeneralConfig;
import com.hbm.interfaces.IBomb;
import com.hbm.items.ModItems;
import com.hbm.main.MainRegistry;
import com.hbm.sound.NtmSounds;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.IADSWeapon;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.packets.LaserDetonatorPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The laser detonator overhaul: upstream's {@code MixinItemLaserDetonator}, on the base mod's
 * {@code ItemLaserDetonator} without touching it (D-1001).
 *
 * <p>Upstream overwrites {@code onItemRightClick} to call its {@code doDetonate(world, player, hand, true)} and
 * return {@code PASS}. Here that is a {@link PlayerInteractEvent.RightClickItem} listener, which runs before the item's
 * own {@code use} on both sides and is cancelled with {@code PASS}, so the base item never fires. The other halves -
 * the pointing key, the tooltip, the aim-down-sights view and the held model - are client-side and in
 * {@link LeafiaLaserDetonatorClient}. All of it only while {@code overhauls.laserDetonatorOverhaul} is on (D-1002).
 *
 * <h2>{@link #doDetonate}, upstream's numbers</h2>
 * <ul>
 * <li>The ray is 128 blocks; sneaking with the detonator in one hand and the other empty, 512. ("Vanilla raytrace
 *     (which it used to use) has force max range of 200.")</li>
 * <li>It starts half a block in front of the eye, 0.16 below it and - for the short ray only - 0.1 toward the
 *     hand holding it (the main arm, or the other one for the off hand), so the laser leaves the device.</li>
 * <li>It stops at the first block's outline, liquids passed through; a miss ends on the block at its far end, as
 *     1.12.2's {@code returnLastUncollidableBlock} did, so there is always a target.</li>
 * <li>The server sends the laser to everyone within twice the ray length of the target. Detonating: a block that is
 *     an {@link IBomb} is told to explode; the clicking client prints {@code [Detonated]} in green or, for anything
 *     else, {@code chat.posbadrror} in red; the server plays the tech bleep for everyone either way (upstream's
 *     detonator never boops). Pointing only: the bleep.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaLaserDetonator {

	private LeafiaLaserDetonator() {}

	public static final int RANGE = 128;
	public static final int RANGE_ONE_HANDED = 512;
	/** Upstream's {@code getADS()}: a fifth more than a quarter of the normal view. */
	public static final float ADS = 0.3F;

	/** The base mod's detonator speaks {@link IADSWeapon} through this, since it cannot implement it. */
	public static final IADSWeapon AIM = new IADSWeapon() {
		@Override
		public float getADS() {
			return ADS;
		}
	};

	public static boolean isDetonator(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == ModItems.detonator_laser.get();
	}

	/** Whether {@code item} has sights: an addon {@link IADSWeapon}, or the base detonator while the overhaul is on. */
	public static IADSWeapon sights(Item item) {
		if(item instanceof IADSWeapon weapon) return weapon;
		if(LeafiaFeatures.laserDetonatorOverhaul && item == ModItems.detonator_laser.get()) return AIM;
		return null;
	}

	/** Upstream's {@code getViewADS}: the sights' multiplier, signed by which hand and which arm; 0 for none. */
	public static float getViewADS(Player player) {
		if(player.isShiftKeyDown()) {
			ItemStack main = player.getMainHandItem();
			ItemStack sub = player.getOffhandItem();
			if((!main.isEmpty() || !sub.isEmpty()) && (main.isEmpty() != sub.isEmpty())) {
				Item holding = main.isEmpty() ? sub.getItem() : main.getItem();
				IADSWeapon weapon = sights(holding);
				if(weapon != null && weapon.getADS() != 1f)
					return weapon.getADS() * (main.isEmpty() ? -1 : 1) * (player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
			}
		}
		return 0;
	}

	/** Whether the player carries a laser detonator anywhere: when upstream's client could send the pointing key. */
	public static boolean carries(Player player) {
		for(ItemStack stack : player.getInventory().items) if(isDetonator(stack)) return true;
		for(ItemStack stack : player.getInventory().offhand) if(isDetonator(stack)) return true;
		return false;
	}

	/** The ray's reach for this player now. */
	public static int range(Player player) {
		int distance = RANGE;
		if(player.isShiftKeyDown()) {
			if(player.getMainHandItem().isEmpty() != player.getOffhandItem().isEmpty())
				distance = RANGE_ONE_HANDED;
		}
		return distance;
	}

	/** Where the laser leaves the device: half a block ahead, 0.16 down, 0.1 to the holding hand on the short ray. */
	public static Vec3 rayStart(Player player, InteractionHand hand, int distance) {
		HumanoidArm side = player.getMainArm();
		if(hand == InteractionHand.OFF_HAND)
			side = side.getOpposite();
		Vec3 startPos = player.position().add(0, player.getEyeHeight(), 0);
		Vec3 vecLook = player.getViewVector(1);
		// thanks https://www.geogebra.org/m/psMTGDgc
		Vec3 vecRight = Vec3.directionFromRotation(0, player.getYHeadRot()).cross(new Vec3(0, 1, 0));
		Vec3 vecUp = vecLook.cross(vecRight.scale(-1));
		return startPos.add(vecLook.scale(0.5)).add(vecRight.scale((distance < 200) ? (side == HumanoidArm.RIGHT) ? 0.1 : -0.1 : 0)).add(vecUp.scale(-0.16));
	}

	public static BlockHitResult trace(Level world, Player player, Vec3 rayStart, int distance) {
		Vec3 startPos = player.position().add(0, player.getEyeHeight(), 0);
		Vec3 end = startPos.add(player.getViewVector(1).scale(distance));
		return world.clip(new ClipContext(rayStart, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	public static void doDetonate(Level world, Player player, InteractionHand hand, boolean detonate) {
		boolean rem = world.isClientSide;
		int distance = range(player);
		Vec3 rayStart = rayStart(player, hand, distance);
		BlockHitResult ray = trace(world, player, rayStart, distance);
		if(ray != null) {
			BlockPos pos = ray.getBlockPos();
			Vec3 vec = new Vec3(pos.getX() + 0.5 - rayStart.x, pos.getY() + 0.5 - rayStart.y, pos.getZ() + 0.5 - rayStart.z);
			if(world instanceof ServerLevel server)
				new LaserDetonatorPacket().set(rayStart, vec).send(server, Vec3.atCenterOf(pos), distance * 2);
			if(detonate) {
				if(world.getBlockState(pos).getBlock() instanceof IBomb bomb) {
					if(!rem)
						bomb.explode(world, pos);

					if(GeneralConfig.enableExtendedLogging)
						MainRegistry.LOGGER.info("[DET] Tried to detonate block at " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ() + " by " + player.getName().getString() + "!");

					if(rem)
						player.displayClientMessage(Component.literal("§2[" + I18nUtil.resolveKey("chat.detonated") + "]" + "§r"), false);
					else
						bleep(world, player);
				} else {
					if(rem)
						player.displayClientMessage(Component.literal("§c" + I18nUtil.resolveKey("chat.posbadrror") + "§r"), false);
					else
						bleep(world, player);
				}
			} else if(!rem)
				bleep(world, player);
		} else {
			if(rem) {
				if(detonate)
					player.displayClientMessage(Component.literal("§c" + I18nUtil.resolveKey("chat.postoofarerror") + "§r"), false);
			} else
				bleep(world, player);
		}
	}

	private static void bleep(Level world, Player player) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(), NtmSounds.ITEM_TECH_BLEEP.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
	}

	/** Upstream's {@code @Overwrite onItemRightClick}: detonate, then {@code super}'s {@code PASS}. */
	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if(!LeafiaFeatures.laserDetonatorOverhaul || !isDetonator(event.getItemStack())) return;
		doDetonate(event.getLevel(), event.getEntity(), event.getHand(), true);
		event.setCancellationResult(InteractionResult.PASS);
		event.setCanceled(true);
	}
}
