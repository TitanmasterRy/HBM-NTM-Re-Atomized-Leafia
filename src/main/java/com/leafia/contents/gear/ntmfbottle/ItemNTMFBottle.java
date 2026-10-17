package com.leafia.contents.gear.ntmfbottle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FT_Toxin;
import com.hbm.inventory.fluid.trait.FT_Toxin.EffectSpec;
import com.hbm.inventory.fluid.trait.FT_Toxin.ToxinEffects;
import com.hbm.inventory.fluid.trait.FT_Toxin.ToxinEntry;
import com.hbm.inventory.fluid.trait.FT_VentRadiation;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Delicious;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Viscous;
import com.hbm.particle.helper.IParticleCreator;
import com.hbm.sound.NtmSounds;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A bottle of any NTM fluid, and what drinking it does to you.
 *
 * <p>Upstream: {@code com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle}. One item, the fluid
 * in its damage value. Here the fluid is on {@link DataComponents#CUSTOM_DATA} under
 * {@code "fluid"}, exactly as the base mod's own fluid-keyed items ({@code ItemFluidTank},
 * {@code ItemCanister}) keep it, so anything that reads one reads the other.
 *
 * <p>Drinking, server side, in upstream's order: a hot fluid burns (and above 300°C sets you
 * alight), a cold one freezes; toxins apply their potion effects; a corrosive one burns for a
 * third of its rating; a radioactive one doses you; you gain two food (five if delicious); and
 * a poisonous or polluting one poisons you, and past 12.5 damage that would not kill you makes
 * you vomit instead of taking all of it. Every constant is upstream's, including the two
 * comments that say "i felt like it was too weak".
 *
 * <p><b>One upstream bug, kept (rule 4):</b> fallout pollution is meant to add asbestos to the
 * black-lung count, and upstream increments it by the <em>coal</em> figure instead. A fluid
 * that releases fallout but no soot therefore adds nothing for its fallout (D-1066).
 *
 * <p><b>Drawn differently:</b> upstream masks each fluid's <em>texture</em> into the potion
 * overlay with a runtime sprite. Here the overlay is tinted with the fluid's colour, the way
 * vanilla draws potions and the base mod draws its tanks — no generated sprites and no vanilla
 * art shipped in this jar. A visual difference only.
 */
public class ItemNTMFBottle extends Item {

	public ItemNTMFBottle(Properties properties) {
		super(properties);
	}

	private static CompoundTag readTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static FluidType getFluidFromStack(ItemStack stack) {
		return Fluids.fromID(readTag(stack).getInt("fluid"));
	}

	public static ItemStack make(Item bottle, FluidType fluid) {
		ItemStack stack = new ItemStack(bottle);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("fluid", fluid.getID()));
		return stack;
	}

	public static boolean isValidFluid(FluidType fluid) {
		if(fluid.equals(Fluids.NONE)) return false; // what's the point?
		if(fluid.equals(Fluids.WATER)) return false; // already exists
		if(fluid.equals(Fluids.MERCURY)) return false; // already exists
		if(fluid.hasTrait(FT_Amat.class)) return false;
		return !fluid.hasTrait(FT_Gaseous.class);
	}

	/** One bottle per drinkable fluid, in the base mod's display order: the creative tab. */
	public static List<ItemStack> allBottles(Item bottle) {
		List<ItemStack> stacks = new ArrayList<>();
		for(FluidType fluid : Fluids.getInNiceOrder())
			if(isValidFluid(fluid)) stacks.add(make(bottle, fluid));
		return stacks;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		if(getFluidFromStack(stack).hasTrait(FT_Viscous.class))
			return 96;
		return 32;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		Player player = entity instanceof Player p ? p : null;
		if(!level.isClientSide) drink(getFluidFromStack(stack), level, entity, player);
		if(player != null && !player.isCreative())
			return new ItemStack(Items.GLASS_BOTTLE);
		return stack;
	}

	/** Upstream's {@code onItemUseFinish} body, server side. Public so a test can drink. */
	public static void drink(FluidType fluid, Level level, LivingEntity entity, Player player) {

		if(fluid.temperature > 60) {
			// BURNING
			float damage = (fluid.temperature - 60) / 400F * 20; // maybe it was too weak
			entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.drinkhot), damage);
			if(fluid.temperature > 300) {
				float duration = (fluid.temperature - 300) / 50F + 3;
				entity.igniteForSeconds((int) duration);
			}
		} else if(fluid.temperature < -20) {
			// FREEZING
			float damage = -(fluid.temperature + 20) / 200F * 20;
			entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.drinkcryo), damage);
		}

		// EFFECTS
		if(fluid.hasTrait(FT_Toxin.class)) {
			for(ToxinEntry entry : fluid.getTrait(FT_Toxin.class).entries) {
				if(entry instanceof ToxinEffects eff) {
					for(EffectSpec effect : eff.effects)
						entity.addEffect(new MobEffectInstance(effect.effect, effect.duration, effect.amplifier));
				}
			}
		}

		if(fluid.hasTrait(FT_Corrosive.class)) {
			// ACID
			entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.drinkacid), fluid.getTrait(FT_Corrosive.class).getRating() / 3F);
		}

		if(fluid.hasTrait(FT_VentRadiation.class)) {
			// RADIATION
			ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.CREATIVE,
					fluid.getTrait(FT_VentRadiation.class).getRadPerMB() * 333);
		}

		if(player != null) {
			// HUNGER
			int fill = 2;
			if(fluid.hasTrait(FT_Delicious.class))
				fill = 5;
			player.getFoodData().eat(fill, fill);
		}

		// POISONING
		float damage = fluid.poison * 5;
		if(fluid.hasTrait(FT_Polluting.class)) {
			float poisoning = 0;
			float coal = 0;
			float asbestos = 0;
			for(Entry<PollutionType, Float> entry : fluid.getTrait(FT_Polluting.class).releaseMap.entrySet()) {
				float value = entry.getValue() * 1000;
				switch(entry.getKey()) {
					case POISON -> poisoning += value * 50;
					case SOOT -> {
						poisoning += value * 10;
						coal += value * 20;
					}
					case FALLOUT -> {
						poisoning += value * 20;
						asbestos += value * 20;
					}
					case HEAVYMETAL -> poisoning += value * 70;
				}
			}
			if(coal >= 1)
				HbmLivingProps.incrementBlackLung(entity, (int) coal);
			if(asbestos >= 1)
				HbmLivingProps.incrementBlackLung(entity, (int) coal); // upstream's: coal, not asbestos - kept
			damage = Math.max(damage, poisoning);
		}
		damage *= 1.5F; // i felt like it was too weak
		boolean isFatal = entity.getHealth() <= damage / 1.5;
		if(damage >= 12.5 && !isFatal) {
			damage /= 1.5F;
			Vomit.start(entity);
			int ix = Mth.floor(entity.getX());
			int iy = Mth.floor(entity.getY());
			int iz = Mth.floor(entity.getZ());
			level.playSound(null, ix, iy, iz, NtmSounds.PLAYER_VOMIT.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
			entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 19));
		}
		if(damage >= 0.05) {
			entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.poison), damage);
			entity.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (damage * 20), (int) Math.max(0, damage / 5 - 1)));
		}
	}

	/** {@code "<fluid> Bottle"}: upstream's {@code item.ntmfbottle.name=%s Bottle}. */
	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId(), getFluidFromStack(stack).getLocalizedName());
	}

	/** Upstream's {@code addInfoItemTanks}: the fluid's own trait lines, as a tank shows them. */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		List<String> lines = new ArrayList<>();
		getFluidFromStack(stack).addInfo(lines, false);
		for(String line : lines) tooltip.add(Component.literal(line));
	}

	/**
	 * The overlay's tint: the fluid's colour, white if it has none. Layer 0, the glass, is never
	 * tinted. The same rule as the base mod's {@code ItemFluidTank.getColor}.
	 */
	public static int getColor(ItemStack stack, int tintIndex) {
		if(tintIndex == 0) return 0xFFFFFFFF;
		int color = getFluidFromStack(stack).getColor();
		return 0xFF000000 | (color < 0 ? 0xFFFFFF : color);
	}

	/**
	 * Upstream's vomiting: a runnable that sends one {@code vomit} particle packet and re-queues
	 * itself on {@code LeafiaPassiveServer} until it has run ten times — ten packets on ten
	 * consecutive ticks. That queue is not ported, so this is the same schedule kept on a server
	 * tick listener. The packet is the base mod's own ({@code RadiationEffectHandler} sends the
	 * same one); like that one it draws nothing until the base mod's client particle handler
	 * lands.
	 */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Vomit {

		private Vomit() {}

		private static final List<int[]> PENDING = new ArrayList<>();
		private static final List<LivingEntity> ENTITIES = new ArrayList<>();

		/** How many packets each pending entity has had. Exposed for tests. */
		public static int pending() {
			return ENTITIES.size();
		}

		static void start(LivingEntity entity) {
			ENTITIES.add(entity);
			PENDING.add(new int[] { 0 });
		}

		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Post event) {
			Iterator<LivingEntity> entities = ENTITIES.iterator();
			Iterator<int[]> counters = PENDING.iterator();
			while(entities.hasNext()) {
				LivingEntity entity = entities.next();
				int[] counter = counters.next();
				if(counter[0] < 10 && !entity.isRemoved()) {
					CompoundTag nbt = new CompoundTag();
					nbt.putString("type", "vomit");
					nbt.putString("mode", "normal");
					nbt.putInt("count", 15);
					nbt.putInt("entity", entity.getId());
					IParticleCreator.sendPacket(entity.level(), entity.getX(), entity.getY(), entity.getZ(), 25, nbt);
				}
				counter[0]++;
				if(counter[0] >= 10 || entity.isRemoved()) {
					entities.remove();
					counters.remove();
				}
			}
		}
	}
}
