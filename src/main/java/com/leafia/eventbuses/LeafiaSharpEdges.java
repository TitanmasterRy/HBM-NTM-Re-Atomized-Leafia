package com.leafia.eventbuses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.sound.NtmSounds;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.dev.optimization.LeafiaParticlePacket.Sweat;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.init.LeafiaSounds;
import com.leafia.init.hazards.types.HazardTypeSharpEdges;
import com.leafia.lib.LeafiaRef;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * What a sharp item does: the second, pointed hit.
 *
 * <p>Upstream: {@code LeafiaServerListener.SharpEdges}, the part of that 450-line listener that
 * the hazard type {@link HazardTypeSharpEdges} is only the label of. Four triggers, as upstream's:
 *
 * <ul>
 * <li>a hit dealt <b>by</b> something holding sharp items: the victim takes extra pointed damage
 *     from the <em>attacker's</em> held stacks ({@code LivingAttackEvent} is
 *     {@code LivingIncomingDamageEvent} here);
 * <li>a hit <b>taken</b> that knocks back: the holder takes extra from their own sharp items, on
 *     the last hit's damage;
 * <li>a fall: the same, on the fall's damage (the sharp items in a pocket cut you when you land);
 * <li>picking a sharp item up: damage 1.
 * </ul>
 *
 * <p>The extra is {@code base x (sum x (1 - nerf) + max x nerf)}, where {@code sum} is every sharp
 * entry's level over 100 times its stack size and {@code max} is the strongest single level over
 * 100. It is a <em>pointed</em> hit - armour does not stop it ({@link LeafiaDamageSources#pointed}) -
 * with the blood sound, a bit of redstone dust and the addon's own sound, and it resets the
 * victim's hurt cooldown first so it always lands. It is queued to the start of the next server
 * tick (upstream's {@code LeafiaPassiveServer.queueFunction}, here {@link PWRTicks#queueFunction}),
 * because a hit cannot start another inside itself.
 *
 * <p>Gated by {@code features.hazardOverhaul}: with it off nothing listens to the damage.
 * <b>Divergence:</b> the splat is {@link NtmSounds#WEAPON_BODYSPLAT}, the base mod's name for what
 * the 1.12.2 jar called {@code weapon.blood_splat}.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaSharpEdges {

	private LeafiaSharpEdges() {}

	/** The last damage each entity took that was not pointed; what a knockback's extra is a share of. Upstream's {@code damageCache}. */
	static final Map<Entity, Float> damageCache = new WeakHashMap<>();

	@SubscribeEvent
	public static void onEntityHurt(LivingDamageEvent.Post event) {
		if(!LeafiaFeatures.hazardOverhaul) return;
		hurt(event.getEntity(), event.getSource(), event.getNewDamage());
	}

	@SubscribeEvent
	public static void onEntityHit(LivingIncomingDamageEvent event) {
		if(!LeafiaFeatures.hazardOverhaul) return;
		hit(event.getEntity(), event.getSource(), event.getAmount());
	}

	@SubscribeEvent
	public static void onEntityKnockback(LivingKnockBackEvent event) {
		if(!LeafiaFeatures.hazardOverhaul) return;
		knockback(event.getEntity(), event.getStrength());
	}

	@SubscribeEvent
	public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
		if(!LeafiaFeatures.hazardOverhaul) return;
		pickup(event.getPlayer(), event.getItemEntity().getItem());
	}

	// ---- the four triggers, as plain methods so a test can drive them ----

	/** Returns the extra damage queued. */
	public static float hurt(LivingEntity entity, DamageSource source, float amount) {
		if(!source.typeHolder().is(LeafiaDamageSources.pointed))
			damageCache.put(entity, amount);
		if(source.is(DamageTypes.FALL))
			return sharpDamageEntity(entity, amount, getItems(entity));
		return 0F;
	}

	public static float hit(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getDirectEntity();
		if(attacker == null) return 0F;
		List<ItemStack> stacks = new ArrayList<>();
		if(attacker instanceof LivingEntity living)
			for(ItemStack stack : living.getHandSlots()) stacks.add(stack);
		return sharpDamageEntity(victim, amount, stacks);
	}

	public static float knockback(LivingEntity entity, double strength) {
		if(strength <= 0 || inflicting) return 0F;
		Float damage = damageCache.get(entity);
		return damage != null ? sharpDamageEntity(entity, damage, getItems(entity)) : 0F;
	}

	public static float pickup(Entity entity, ItemStack stack) {
		return sharpDamageEntity(entity, 1F, Collections.singletonList(stack));
	}

	/** A player's whole inventory, armour and off hand included; anyone else's hands and armour. Upstream's {@code getItems}. */
	public static List<ItemStack> getItems(Entity entity) {
		List<ItemStack> stacks = new ArrayList<>();
		if(entity instanceof Player player) {
			Inventory inventory = player.getInventory();
			for(int i = 0; i < inventory.getContainerSize(); i++) stacks.add(inventory.getItem(i));
		} else {
			if(entity instanceof LivingEntity living) {
				for(ItemStack stack : living.getHandSlots()) stacks.add(stack);
				for(ItemStack stack : living.getArmorSlots()) stacks.add(stack);
			}
		}
		return stacks;
	}

	/**
	 * The pointed damage a hit of {@code baseDamage} gains from {@code stacks}: upstream's
	 * {@code base x (modifier x (1 - nerf) + max x nerf)} with {@code modifier} the sum of each
	 * sharp entry's level over 100 times its stack's size and {@code max} the largest level over 100.
	 */
	public static float additionalDamage(float baseDamage, List<ItemStack> stacks) {
		float modifier = 0;
		float max = 0;
		for(ItemStack stack : stacks) {
			if(stack.isEmpty()) continue;
			for(HazardEntry hazard : HazardSystem.getHazardsFromStack(stack)) {
				if(hazard.getType() instanceof HazardTypeSharpEdges) {
					modifier += (float) (hazard.getBaseLevel() / 100.0 * stack.getCount());
					max = Math.max(max, hazard.getBaseLevel() / 100F);
				}
			}
		}
		return baseDamage * (modifier * (1 - HazardTypeSharpEdges.sharpStackNerf) + max * HazardTypeSharpEdges.sharpStackNerf);
	}

	/** Queues the extra hit, if there is one. Returns what was queued. */
	public static float sharpDamageEntity(Entity entity, float baseDamage, List<ItemStack> stacks) {
		float additional = additionalDamage(baseDamage, stacks);
		if(additional > 0) PWRTicks.queueFunction(() -> inflict(entity, additional));
		return additional;
	}

	/** The queued half: sounds, speck, and a pointed hit that ignores the hurt cooldown. */
	public static void inflict(Entity entity, float additional) {
		Level level = entity.level();
		if(level == null || !(entity instanceof LivingEntity living)) return;
		play(level, entity, NtmSounds.WEAPON_BODYSPLAT.get());
		play(level, entity, LeafiaSounds.pointed.get());
		if(level instanceof ServerLevel server)
			new Sweat(entity, Blocks.REDSTONE_BLOCK.defaultBlockState(), level.random.nextInt(4) + 2)
					.emit(server, entity.position(), Vec3.ZERO);
		living.invulnerableTime = 0;
		inflicting = true;
		try {
			living.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.pointed), additional);
		} finally {
			inflicting = false;
		}
	}

	/**
	 * Set while the extra hit is being dealt. 1.21.1 knocks an entity back from damage with no attacker
	 * (1.12.2 did not), so the pointed hit raises a knockback event of its own - and its cut would read the
	 * last <em>ordinary</em> damage from the cache and queue another pointed hit, one a tick for ever.
	 * Upstream cannot happen; this keeps it that way.
	 */
	private static boolean inflicting = false;

	private static void play(Level level, Entity entity, SoundEvent sound) {
		level.playSound(null, entity.blockPosition(), sound, SoundSource.MASTER, 0.25F, level.random.nextFloat() * 0.2F + 0.9F);
	}
}
