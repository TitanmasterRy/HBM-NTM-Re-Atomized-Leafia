package com.leafia.contents.gear.combat.shields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.hbm.entity.projectile.IBulletBase;
import com.hbm.lib.NtmTags;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket.SyncVelocityPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One of the addon's six shields: what each does to whatever hits it, and the sneak-block charge
 * the two top shields share.
 *
 * <p>Upstream: {@code com.leafia.contents.gear.combat.shields.AddonShieldItem}, an
 * {@code ItemShield} whose behaviour is picked by comparing {@code this} to the six static
 * fields of {@code AddonItems.Shields}. Here the six are one class and a {@link Kind}, so the
 * comparison is {@code kind ==} and nothing depends on a static holder being initialised.
 *
 * <h2>What each one does</h2>
 *
 * <ul>
 *   <li><b>steel, titanium</b> - a shield, nothing more. 750 and 1,000 uses (see
 *       {@link com.leafia.init.LeafiaToolMaterials} for why those and not upstream's 500 and 750).</li>
 *   <li><b>SWAT shield</b> ({@code elec_shield}) - 450 uses; "blocks bullets" - see below.</li>
 *   <li><b>plutonium-238</b> - an attacker that hits a raised shield is set alight for five seconds
 *       and takes 10 radiation. 300 uses.</li>
 *   <li><b>fissite</b> - the same attacker takes 70 radiation. 2,500 uses. Sneak while blocking
 *       to charge.</li>
 *   <li><b>mysticite</b> - never wears out. Blocks bullets. Sneak while blocking to charge.</li>
 * </ul>
 *
 * <h2>The charge</h2>
 *
 * A fissite or mysticite shield held in either hand, raised, with the holder sneaking, counts a tick
 * up each tick; at 20 the holder is thrown forward ({@code 1.75} along their yaw, {@code +0.4}
 * up) by a {@link SyncVelocityPacket}, the item goes on a 60 tick cooldown, and for the first
 * fifteen of those ticks everything the holder overlaps takes 8 damage and eight seconds of
 * slowness ({@link Events#coolDown}). Sneaking or lowering the shield before the 20th tick resets
 * the count. While the cooldown runs the count does not move.
 *
 * <p>The state is two maps, as upstream's. They are keyed by the player's UUID rather than by the
 * player object (upstream's {@code Map<EntityPlayer, Pair<Integer, Long>>}), and the timestamp half
 * of upstream's pair is dropped: it existed so that {@code AddonBase.handleExpiration} could sweep
 * entries a minute old, and the cooldown is rewritten every tick it lives, so the sweep never
 * found one that mattered. What the sweep really did was forget a player who had gone, which is
 * {@link Events#onLogout}; and, because 1.12.2 built a new {@code EntityPlayerMP} on respawn,
 * forget a player who had died, which is {@link Events#onRespawn}.
 *
 * <h2>Bullets - dead for now, and wired to go live</h2>
 *
 * Upstream's shields that "block bullets" ({@code elec_shield}, {@code mysticite_shield}) test the
 * attacker against five classes, four of which are NTM-CE's own and none of which exists here:
 * only {@code EntityBulletBaseNT} is in the base mod this port follows, and it is not ported
 * either (it waits on the sedna gun system). {@link #isBullet} therefore tests for
 * {@link IBulletBase}, the interface that class implements, so the day it lands the shields start
 * stopping its rounds without anyone touching this file. {@link #BULLET_TESTS} is the seam for the
 * other four if a pack adds them, and {@link #UNBLOCKABLE} for upstream's one exception - the AM
 * rifle's beam, which the shield must not stop. Nothing reaches either today, and
 * {@code LeafiaShieldTests} drives them with a test entity so they are pinned rather than assumed.
 *
 * <h2>Banner decoration</h2>
 *
 * Upstream's {@code AddonShieldCraftingRecipe} copies a banner's patterns onto any addon shield, and
 * {@code AddonShieldRender} composes them over a per-shield "blank" skin. Here the recipe is the
 * {@code leafia:shield_decoration} serializer (D-3026) and the renderer lays the banner's layers over
 * {@link Kind#blank} (D-3031); before that, a decorated shield was drawn plain (D-1083).
 */
public class AddonShieldItem extends ShieldItem {

	/** The six shields, and the one number that is not on the item: what mends them. */
	public enum Kind {
		STEEL("steel_shield", "ingotSteel"),
		TITANIUM("titanium_shield", "ingotTitanium"),
		ELEC("elec_shield", "ingotPolymer"),
		PU238("pu238_shield", "ingotPu238"),
		FISSITE("fissite_shield", "ingotFissite"),
		MYSTICITE("mysticite_shield", "ingotMysticite");

		public final String id;
		/** Upstream's ore-dictionary name, as {@code NtmTags.ore} turns it into a tag. */
		public final String repairOre;

		Kind(String id, String repairOre) {
			this.id = id;
			this.repairOre = repairOre;
		}

		/**
		 * The skin a vanilla-shaped shield is drawn with: upstream's {@code items/shields/<id>.png},
		 * moved to where a 1.21.1 entity texture goes. Here and not on the renderer, which is a
		 * client class a dedicated server cannot load.
		 */
		public net.minecraft.resources.ResourceLocation skin() {
			return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/entity/shield/" + id + ".png");
		}

		/**
		 * The skin a banner is laid over (D-3031): upstream's third {@code AddonShieldRender} argument - the
		 * {@code <id>_blank} skin for steel, titanium and the SWAT shield, and the shield's own skin for plutonium-238,
		 * fissite and mysticite, which upstream passes twice. The KMS pair's skin is where their baked model reads it.
		 */
		public net.minecraft.resources.ResourceLocation blank() {
			return switch(this) {
				case STEEL, TITANIUM, ELEC -> net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/entity/shield/" + id + "_blank.png");
				case PU238 -> skin();
				case FISSITE, MYSTICITE -> net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/item/" + id + "_skin.png");
			};
		}

		/** Fissite and mysticite: the two that charge. */
		public boolean charges() {
			return this == FISSITE || this == MYSTICITE;
		}
	}

	/** Ticks of sneak-blocking before the charge fires. Upstream's {@code value < 20}. */
	public static final int CHARGE_TICKS = 20;
	/** Ticks the item, and the player's body-check, stay on cooldown after a charge. */
	public static final int COOLDOWN_TICKS = 60;
	/** The body-check works for the first this-many ticks of the cooldown. Upstream's {@code 60 - 15}. */
	public static final int HIT_TICKS = 15;
	public static final float HIT_DAMAGE = 8F;
	public static final int SLOWNESS_TICKS = 20 * 8;
	/** Upstream's {@code scale(1.75).add(0, 0.4, 0)}. */
	public static final double DASH_SPEED = 1.75;
	public static final double DASH_LIFT = 0.4;

	/** Radiation handed to an attacker, in upstream's units. */
	public static final float FISSITE_RADIATION = 70F;
	public static final float PU238_RADIATION = 10F;
	public static final int PU238_FIRE_SECONDS = 5;

	/** Players with a charge in progress, and how many ticks of it. Upstream's {@code chargePrepare}. */
	public static final Map<UUID, Integer> chargePrepare = new HashMap<>();
	/** Players on cooldown, and how many ticks are left. Upstream's {@code chargeCooldown}. */
	public static final Map<UUID, Integer> chargeCooldown = new HashMap<>();

	/**
	 * Further tests for "this attacker is a bullet". Seeded with {@link IBulletBase}, which is
	 * the base mod's one bullet entity; see the class doc.
	 */
	public static final List<Predicate<Entity>> BULLET_TESTS = new ArrayList<>(List.of(e -> e instanceof IBulletBase));

	/** Attackers that a bullet-blocking shield must not treat as bullets. Upstream's {@code AMRifleBeam}. */
	public static final List<Class<? extends Entity>> UNBLOCKABLE = new ArrayList<>();

	private final Kind kind;

	public AddonShieldItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		for(String line : I18nUtil.resolveKey("item." + LeafiaRef.MODID + "." + kind.id + ".desc").split("\\$"))
			tooltip.add(Component.literal(line));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/** Upstream's {@code onUpdate}, run for every stack in the inventory; only a held one counts. */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if(level.isClientSide) return;
		if(!kind.charges()) return;
		if(!(entity instanceof Player player)) return;
		if(player.getMainHandItem() != stack && player.getOffhandItem() != stack) return;
		chargeStep(player);
	}

	/**
	 * One tick of the charge for one player holding this shield, upstream's body of
	 * {@code onUpdate} after its guards.
	 *
	 * <p>Two upstream quirks are kept. A player holding two charging shields (one in each hand)
	 * is stepped twice a tick, because the guard is "this stack is held" and both are. And the
	 * count is only cleared by the else branch, which runs for a held shield, so a count left
	 * behind by dropping the shield mid-charge waits for the next one held.
	 */
	public void chargeStep(Player player) {
		UUID id = player.getUUID();
		if(player.isBlocking() && player.isShiftKeyDown()) {
			if(chargeCooldown.getOrDefault(id, 0) != 0) return; // upstream: "fah"
			int value = chargePrepare.getOrDefault(id, 0) + 1;
			if(value < CHARGE_TICKS) {
				chargePrepare.put(id, value);
			} else {
				chargePrepare.remove(id);
				chargeCooldown.put(id, COOLDOWN_TICKS);
				player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
				LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new SyncVelocityPacket(dash(player.getYRot())));
				if(packet != null) packet.__sendToClient(player);
			}
		} else {
			chargePrepare.remove(id);
		}
	}

	/** The velocity a charge gives, for a yaw: the look vector at pitch 0, times 1.75, and 0.4 up. */
	public static Vec3 dash(float yaw) {
		float f = Mth.cos(-yaw * 0.017453292F - (float) Math.PI);
		float f1 = Mth.sin(-yaw * 0.017453292F - (float) Math.PI);
		// Upstream's getVectorForRotation(0, yaw): f2 = -cos(0) = -1, f3 = sin(0) = 0.
		return new Vec3(f1 * -1F, 0, f * -1F).scale(DASH_SPEED).add(0, DASH_LIFT, 0);
	}

	public double calBulletDamage(float dmg) {
		return Math.sqrt(dmg);
	}

	public boolean canBlockBullets() {
		return kind == Kind.MYSTICITE || kind == Kind.ELEC;
	}

	/** Whether the shield treats this attacker as a bullet: a bullet test passes and it is not exempt. */
	public static boolean isBullet(Entity attacker) {
		for(Class<? extends Entity> exempt : UNBLOCKABLE)
			if(exempt.isInstance(attacker)) return false;
		for(Predicate<Entity> test : BULLET_TESTS)
			if(test.test(attacker)) return true;
		return false;
	}

	/**
	 * What the shield does to an attacker whose hit it has just stopped. Returns whether the hit is
	 * to be cancelled outright, which only a bullet-blocking shield does, and then only for a bullet:
	 * the shield's wear is booked here, as {@code (int) sqrt(damage)}, and not by vanilla's formula.
	 */
	public boolean handleImpact(ItemStack stack, Entity attacker, float amount) {
		if(canBlockBullets() && isBullet(attacker)) {
			if(stack.isDamageableItem())
				stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + (int) calBulletDamage(amount)));
			return true;
		}
		if(kind == Kind.FISSITE) {
			if(attacker instanceof LivingEntity living)
				ContaminationUtil.contaminate(living, HazardType.RADIATION, ContaminationType.CREATIVE, FISSITE_RADIATION);
		}
		if(kind == Kind.PU238) {
			attacker.igniteForSeconds(PU238_FIRE_SECONDS);
			if(attacker instanceof LivingEntity living)
				ContaminationUtil.contaminate(living, HazardType.RADIATION, ContaminationType.CREATIVE, PU238_RADIATION);
		}
		return false;
	}

	/** Upstream mends each shield with its material's ingot, and vanilla's planks still work. */
	@Override
	public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
		TagKey<Item> tag = NtmTags.ore(kind.repairOre);
		return repair.is(tag) || super.isValidRepairItem(toRepair, repair);
	}

	/** The shield side of the events: the block, the cooldown's body-check, and forgetting a player. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Events {

		private Events() {}

		/**
		 * Upstream's {@code LeafiaServerListener...onEntityHit}, a {@code LivingAttackEvent} that
		 * asked "is the player blocking, and is the attacker in front". 1.21.1's
		 * {@link LivingShieldBlockEvent} <em>is</em> that question, answered by vanilla, so the
		 * facing test is not ported; the event also only fires for a hit that deals damage, and
		 * not for one that bypasses shields. It runs last, so another mod that cancels the block
		 * is respected, and uses the item actually being held up rather than upstream's "main
		 * hand if it is an addon shield, else off hand".
		 */
		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void onShieldBlock(LivingShieldBlockEvent event) {
			if(!(event.getEntity() instanceof Player player)) return;
			if(!event.getBlocked()) return;
			Entity cause = event.getDamageSource().getDirectEntity();
			if(cause == null) return;
			ItemStack used = player.getUseItem();
			if(!(used.getItem() instanceof AddonShieldItem shield)) return;
			if(shield.handleImpact(used, cause, event.getOriginalBlockedDamage()))
				event.setShieldDamage(0); // the wear is already on the stack
		}

		/**
		 * The start of every tick: upstream's {@code priorTickWorld} pass over each world's players,
		 * which runs before any entity ticks, so a charge fired during one tick is first felt at the
		 * start of the next.
		 */
		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Pre event) {
			for(Player player : event.getServer().getPlayerList().getPlayers())
				coolDown(player);
		}

		/**
		 * One player's share of that pass.
		 *
		 * <p><b>The body-check's box now has depth, which upstream's does not (D-1093 #5, D-1094).</b>
		 * Upstream builds {@code new AxisAlignedBB(x - r, y - 1, z + r, x + r, y + eye + 0.05, z + r)}:
		 * both Z corners are {@code z + 0.35}, so the "box" is a flat plane facing south and only an
		 * entity straddling it is hit, whichever way the charge went. D-1083 flagged it as an
		 * evident typo and the owner said to fix it: the first corner is {@code z - r}, which makes
		 * the box 0.7 deep and centred on the player, like its X extent. <b>This is a deliberate
		 * divergence from upstream.</b>
		 */
		public static void coolDown(Player player) {
			UUID id = player.getUUID();
			int cooldown = chargeCooldown.getOrDefault(id, 0);
			if(cooldown > COOLDOWN_TICKS - HIT_TICKS) {
				double range = 0.35;
				AABB box = new AABB(
						player.getX() - range, player.getY() - 1, player.getZ() - range, // upstream: z + range (D-1094)
						player.getX() + range, player.getY() + player.getEyeHeight() + 0.05, player.getZ() + range);
				for(Entity entity : player.level().getEntities(player, box)) {
					if(entity instanceof LivingEntity living) {
						living.hurt(player.level().damageSources().playerAttack(player), HIT_DAMAGE);
						living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS));
					}
				}
			}
			if(cooldown - 1 > 0) chargeCooldown.put(id, cooldown - 1);
			else chargeCooldown.remove(id);
		}

		@SubscribeEvent
		public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
			forget(event.getEntity());
		}

		@SubscribeEvent
		public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
			forget(event.getEntity());
		}

		private static void forget(Player player) {
			chargePrepare.remove(player.getUUID());
			chargeCooldown.remove(player.getUUID());
		}
	}
}
