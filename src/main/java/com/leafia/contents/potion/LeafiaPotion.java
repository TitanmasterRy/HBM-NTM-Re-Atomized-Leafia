package com.leafia.contents.potion;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorWarningPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.optimization.LeafiaParticlePacket.Sweat;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's three status effects: damaged skin, frigid and overdose.
 *
 * <p>Upstream: {@code com.leafia.contents.potion.LeafiaPotion}, one {@code Potion} subclass that
 * switches on {@code this == ...} for each of the three. Here each is a {@link MobEffect} with the
 * same colour, category and modifiers, and the per-effect behaviour is where 1.21.1 puts it:
 *
 * <ul>
 * <li><b>Frigid</b> slows movement and attack speed by 15% per level (upstream's operation 2,
 *     which is {@code ADD_MULTIPLIED_TOTAL}) and is <em>named</em> Slowness — upstream's
 *     {@code getName} returns vanilla's slowness key, so it reads as slowness in the HUD.
 * <li><b>Damaged skin</b> steps down rather than ending: when one of level II or III expires
 *     or is removed, it comes back one level lower for 300 seconds, on the next tick. Upstream
 *     does this from {@code removeAttributesModifiersFromEntity}, which 1.21.1 no longer hands
 *     the entity to, so it is NeoForge's expire and remove events here, queued to the end of
 *     the server tick as upstream queued it on {@code LeafiaPassiveServer}.
 * <li><b>Overdose</b> has no behaviour of its own: it is the meltdown pill's cooldown, made
 *     visible.
 * </ul>
 *
 * <p>These are registered whatever the feature switches say. They are inert registry entries
 * until something applies them, and three things in different features do — the meltdown pill
 * (overdose), the radiation classification overhaul (damaged skin, off by default) and liquid
 * oxygen (frigid, unported) — so tying them to any one switch would break the others. (D-1069)
 *
 * <p>Damaged skin's red "sweat" is carried by {@code LeafiaParticlePacket.Sweat} (D-3000), on upstream's
 * odds: each tick, one in {@code 50 - 20 * level} (level capped at II), a single speck of
 * redstone-block dust around the entity. It is presentation, and it is the first thing in the addon
 * to send that packet.
 */
public class LeafiaPotion extends MobEffect {

	public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, LeafiaRef.MODID);

	public static final DeferredHolder<MobEffect, LeafiaPotion> skindamage =
			EFFECTS.register("skindamage", () -> new LeafiaPotion(0xe11313));

	public static final DeferredHolder<MobEffect, LeafiaPotion> frigid =
			EFFECTS.register("frigid", () -> (LeafiaPotion) new LeafiaPotion(0xFFFFFF)
					.addAttributeModifier(Attributes.MOVEMENT_SPEED,
							ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "effect.frigid.movement"),
							-0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.ATTACK_SPEED,
							ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "effect.frigid.attack"),
							-0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	public static final DeferredHolder<MobEffect, LeafiaPotion> overdose =
			EFFECTS.register("overdose", () -> new LeafiaPotion(0x692681));

	public static void register(IEventBus modBus) {
		EFFECTS.register(modBus);
	}

	public LeafiaPotion(int color) {
		super(MobEffectCategory.HARMFUL, color); // upstream's isBad = true, all three
	}

	/** Upstream's {@code getName}: frigid wears vanilla slowness's name. */
	@Override
	public String getDescriptionId() {
		if(this == frigid.get())
			return MobEffects.MOVEMENT_SLOWDOWN.value().getDescriptionId();
		return super.getDescriptionId();
	}

	/**
	 * Upstream's {@code isReady}: damaged skin acts every tick, to roll its sweat. The other two
	 * effects have no per-tick behaviour, which is the default.
	 */
	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return this == skindamage.get();
	}

	/**
	 * The upper bound of the one-in-N roll for a sweat speck: upstream's {@code 50 - level * 20}
	 * with the level capped at II ("foolproof" upstream), which is 50, 30, then 10 for every level
	 * from there up.
	 */
	public static int sweatBound(int amplifier) {
		return 50 - Math.min(amplifier, 2) * 20;
	}

	/** Upstream's {@code performEffect}: damaged skin sometimes sweats a speck of blood. */
	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {

		if(this == skindamage.get() && entity.level() instanceof ServerLevel level
				&& entity.getRandom().nextInt(sweatBound(amplifier)) == 0) {
			new Sweat(entity, Blocks.REDSTONE_BLOCK.defaultBlockState(), 1).emit(level, entity.position(), Vec3.ZERO);
		}

		return true;
	}

	/**
	 * Adds damaged skin, one level higher than the entity has, up to {@code maxLevel} (1 to 3),
	 * for 450 seconds. Upstream's. From level II up a player is also told, so an advisor in their
	 * inventory can say so ({@code AdvisorWarningPacket}; D-1080) - a level is its amplifier, so
	 * the packet's id 1 is "skin damage II" and 2 is "III".
	 */
	public static void hurtSkin(LivingEntity entity, int maxLevel) {
		maxLevel -= 1;
		int level = 0;
		MobEffectInstance effect = entity.getEffect(skindamage);
		if(effect != null) {
			if(effect.getAmplifier() > maxLevel) return;
			level = Math.min(effect.getAmplifier() + 1, maxLevel);
		}
		entity.addEffect(new MobEffectInstance(skindamage, 450 * 20, level, false, false));
		if(LeafiaFeatures.advisor && entity instanceof ServerPlayer player && level > 0)
			LeafiaCustomPacket.__start(new AdvisorWarningPacket(level)).__sendToClient(player);
	}

	/** Damaged skin's level, 1 to 3, or 0 without it. */
	public static int getSkinDamage(LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(skindamage);
		return effect != null ? effect.getAmplifier() + 1 : 0;
	}

	/** The step-down, on the expire and remove events and applied at the end of the tick. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class SkinDecay {

		private SkinDecay() {}

		private static final List<Object[]> PENDING = new ArrayList<>();

		private static void ended(LivingEntity entity, MobEffectInstance instance) {
			if(instance == null || entity.level().isClientSide) return;
			if(!instance.getEffect().is(skindamage.getKey())) return;
			if(instance.getAmplifier() > 0)
				PENDING.add(new Object[] { entity, instance.getAmplifier() - 1 });
		}

		@SubscribeEvent
		public static void onExpired(MobEffectEvent.Expired event) {
			ended(event.getEntity(), event.getEffectInstance());
		}

		@SubscribeEvent
		public static void onRemoved(MobEffectEvent.Remove event) {
			ended(event.getEntity(), event.getEffectInstance());
		}

		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Post event) {
			if(PENDING.isEmpty()) return;
			List<Object[]> due = new ArrayList<>(PENDING);
			PENDING.clear();
			for(Object[] p : due) {
				LivingEntity entity = (LivingEntity) p[0];
				if(entity.isAlive())
					entity.addEffect(new MobEffectInstance(skindamage, 300 * 20, (Integer) p[1], false, false));
			}
		}
	}
}
