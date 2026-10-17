package com.leafia.contents.consumable.medication;

import java.util.List;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.potion.NtmEffects;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.lib.LeafiaRef;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "Last Resort": the meltdown pill. The first one wipes your radiation dose and gives three
 * minutes of mutation; a second within the hour kills you outright.
 *
 * <p>Upstream: {@code com.leafia.contents.consumable.medication.PillMeltdownItem}, a zero-food
 * {@code AddonFoodBaked} that is always edible and takes 10 ticks to eat. On eating, server
 * side: with no cooldown running it starts a 59-minute cooldown, adds mutation for 180 seconds
 * and sets the player's radiation to 0; with one running it deals the player's full health as
 * {@code pill_meltdown} damage and, if they somehow live, kills them anyway.
 *
 * <p>The cooldown lives on the player's {@code PlayerPersisted} data, which survives death —
 * the tooltip's "this timer does not reset from deaths!" — and ticks down on {@link Cooldown},
 * upstream's half of {@code LeafiaPassiveServer.priorTickWorld}, which also keeps the overdose
 * effect on the player for the time remaining.
 *
 * <p>Two things differ, both recorded in D-1069:
 * <ul>
 * <li><b>The cooldown is written to a tag that is attached.</b> Upstream reads
 *     {@code getCompoundTag(PERSISTED_NBT_TAG)}, which hands back a fresh, <em>unattached</em>
 *     compound when the player has none, and writes the cooldown into that — so on such a player
 *     the cooldown is never stored and the pill can be taken again safely. Whether a player had
 *     the compound depended on other code having written one. The tooltip states the intent
 *     plainly, and the item is pointless without it, so the compound is put back here.
 * <li><b>No gibs.</b> Upstream ends with {@code ConfettiUtil.gib(player)}, a base-mod sedna
 *     effect this port does not have. The death is the same; the spray of meat is not.
 * </ul>
 */
public class PillMeltdownItem extends Item {

	public static final String COOLDOWN = "pillMeltdownCooldown";
	/** Upstream's 59 minutes, in ticks. */
	public static final int COOLDOWN_TICKS = 59 * 60 * 20;

	public static final FoodProperties FOOD = new FoodProperties.Builder().nutrition(0).saturationModifier(0F).alwaysEdible().build();

	public PillMeltdownItem(Properties properties) {
		super(properties);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 10;
	}

	/** The player's death-surviving compound, attached so that a write to it is kept. */
	public static CompoundTag persisted(Player player) {
		CompoundTag data = player.getPersistentData();
		if(!data.contains(Player.PERSISTED_NBT_TAG))
			data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
		return data.getCompound(Player.PERSISTED_NBT_TAG);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if(!level.isClientSide && entity instanceof Player player)
			swallow(player);
		return result;
	}

	/** Upstream's {@code onFoodEaten} body. Public so a test can take one. */
	public static void swallow(Player player) {
		CompoundTag tag = persisted(player);
		if(!tag.contains(COOLDOWN)) {
			tag.putInt(COOLDOWN, COOLDOWN_TICKS);
			player.addEffect(new MobEffectInstance(NtmEffects.MUTATION, 180 * 20));
			HbmLivingProps.setRadiation(player, 0);
		} else {
			player.hurt(LeafiaDamageSources.of(player.level(), LeafiaDamageSources.pillMeltdown), player.getMaxHealth());
			if(player.getHealth() > 0) {
				// Upstream's SlopTE.tryKill: health to zero, then the death itself.
				player.setHealth(0);
				player.die(LeafiaDamageSources.of(player.level(), LeafiaDamageSources.pillMeltdown));
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		for(String line : I18nUtil.resolveKey("item.leafia.pill_meltdown.desc").split("\\$"))
			tooltip.add(Component.literal(line));
	}

	/** The cooldown's tick, and the overdose effect that shows it. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Cooldown {

		private Cooldown() {}

		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Pre event) {
			if(!LeafiaFeatures.meltdownPill) return;
			for(ServerPlayer player : event.getServer().getPlayerList().getPlayers())
				tick(player);
		}

		/** One tick of upstream's loop, for one player. */
		public static void tick(Player player) {
			CompoundTag data = player.getPersistentData();
			if(!data.contains(Player.PERSISTED_NBT_TAG)) return;
			CompoundTag tag = data.getCompound(Player.PERSISTED_NBT_TAG);
			if(!tag.contains(COOLDOWN)) return;
			int v = tag.getInt(COOLDOWN) - 1;
			if(v > 0) {
				tag.putInt(COOLDOWN, v);
				MobEffectInstance overdose = player.getEffect(LeafiaPotion.overdose);
				if(overdose != null && overdose.getDuration() >= v - 5)
					return;
				player.addEffect(new MobEffectInstance(LeafiaPotion.overdose, v, 0, false, false));
			} else {
				tag.remove(COOLDOWN);
			}
		}
	}
}
