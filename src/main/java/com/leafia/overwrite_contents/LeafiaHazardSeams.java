package com.leafia.overwrite_contents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.hbm.blocks.gas.BlockGasBase;
import com.hbm.blocks.gas.BlockGasRadon;
import com.hbm.blocks.gas.BlockGasRadonDense;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.RadiationEffectHandler;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.hazard.type.HazardTypeHot;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.config.RadiationConfig;
import com.hbm.handler.HazmatRegistry;
import com.hbm.items.tool.ItemGeigerCounter;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.hbm.util.ArmorUtil;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorWarningPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.rad_correction.RadCorrection;
import com.leafia.init.LeafiaSounds;
import com.leafia.init.hazards.types.LCERad;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Beta;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.init.hazards.types.radiation.Radon;
import com.leafia.init.hazards.types.radiation.XRay;
import com.leafia.unsorted.BullshitInDavenportIowa;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Where the addon plugs into the base mod's hazard seams (D-2199), all at once, from the mod
 * constructor. Not an upstream file: upstream reached each of these with a mixin into {@code com.hbm},
 * and D-1001 makes every one a seam consumer instead (D-3023).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code MixinHazardTypeHot}</td><td>{@link #HOT_ADVISOR}, an {@code ApplyHook.after}</td><td>hazardOverhaul and advisor</td></tr>
 * <tr><td>{@code MixinHazardSystem}</td><td>{@link #RADIATION_TOOLTIP}, a {@code TooltipHandler}</td><td>radiationClassification or sludge units</td></tr>
 * <tr><td>{@code MixinContaminationUtil}</td><td>{@link #GEIGER_PRINTER}, a {@code GeigerPrinter}</td><td>radiationClassification or sludge units</td></tr>
 * <tr><td>{@code MixinItemGeigerCounter}</td><td>{@link #GEIGER_CLICKS}, a {@code ClickHandler}</td><td>hazardOverhaul</td></tr>
 * <tr><td>{@code MixinBlockGasRadon}, {@code MixinBlockRadonDense}</td><td>{@link #RADON_CONTACT}, a {@code ContactHandler}</td><td>hazardOverhaul</td></tr>
 * <tr><td>{@code MixinEntityEffectHandler}</td><td>{@link #CRATER_UNDERGROUND}, a {@code CraterHook}</td><td>hazardOverhaul</td></tr>
 * <tr><td>{@code MixinBlockHazard}</td><td>nothing: the base mod's {@code BlockHazard.tickDefault} reschedules whenever it radiates, which is the fix (a test pins it)</td><td>-</td></tr>
 * <tr><td>{@code MixinHazardRegistry}</td><td>nothing: the base mod has no contaminating drops to exclude one from</td><td>-</td></tr>
 * </table>
 *
 * Each is registered whatever its switch says and reads the switch when it runs, so an off switch hands
 * the base mod its own behaviour back.
 */
public final class LeafiaHazardSeams {

	private LeafiaHazardSeams() {}

	private static boolean installed = false;

	/** Registers every handler, once. Called from the mod constructor. */
	public static void install() {
		if(installed) return;
		installed = true;
		HazardSystem.addApplyHook(HOT_ADVISOR);
		HazardSystem.addTooltipHandler(RADIATION_TOOLTIP);
		ContaminationUtil.addGeigerPrinter(GEIGER_PRINTER);
		ItemGeigerCounter.addClickHandler(GEIGER_CLICKS);
		BlockGasRadon.addContactHandler(RADON_CONTACT);
		RadiationEffectHandler.addCraterHook(CRATER_UNDERGROUND);
	}

	// ------------------------------------------------------------------ MixinHazardTypeHot

	/**
	 * A burning item tells its carrier's advisor ({@code AdvisorWarningPacket} 0, "pyrophoric"). Upstream
	 * injects at the tail of {@code HazardTypeHot.onUpdate}; it is reached whether or not the item
	 * actually lit anything - wet, or a reacher held - and not by a creative player, whose branch
	 * returns early. The base mod's type returns early on {@code disableHot} as well, which is the one
	 * place the tail would have been skipped there, so that flag is read here too and warns nobody.
	 */
	public static final HazardSystem.ApplyHook HOT_ADVISOR = new HazardSystem.ApplyHook() {
		@Override
		public void after(HazardEntry entry, ItemStack stack, @Nullable LivingEntity target, float level) {
			if(!LeafiaFeatures.hazardOverhaul || !LeafiaFeatures.advisor) return;
			if(!(entry.getType() instanceof HazardTypeHot) || RadiationConfig.disableHot) return;
			if(target instanceof ServerPlayer player && !player.getAbilities().instabuild)
				LeafiaCustomPacket.__start(new AdvisorWarningPacket(AdvisorWarningPacket.PYROPHORIC)).__sendToClient(player);
		}
	};

	// ------------------------------------------------------------------ MixinHazardSystem

	/** Whether the radiation readouts are the addon's: classification, or the sludge units that only they speak. */
	public static boolean classifiedReadouts() {
		return LeafiaFeatures.radiationClassification || LeafiaConfig.bullshitUnits;
	}

	/** Upstream's {@code Library.roundFloat(f, decimals)}. */
	public static float roundFloat(double number, int decimals) {
		double scale = Math.pow(10, decimals);
		return (float) (Math.round(number * scale) / scale);
	}

	/** {@code HazardTypeRadiation.getNewValue} of NTM-CE: a million or more is shown in millions, a billion in billions. */
	public static double getNewValue(double radiation) {
		if(radiation < 1_000_000.0) return radiation;
		return radiation < 1.0E9 ? radiation * 1.0E-6 : radiation * 1.0E-9;
	}

	/** {@code HazardTypeRadiation.getSuffix} of NTM-CE ({@code desc.mil}, {@code desc.bil}): "M" and "B". */
	public static String getSuffix(double radiation) {
		if(radiation < 1_000_000.0) return "";
		return radiation < 1.0E9 ? "M" : "B";
	}

	/**
	 * The classified radiation tooltip: one {@code [Radioactive]} header and a line per component
	 * (alpha, beta, X-ray, gamma, neutrons, contamination, then radon), a stack total, and the plain
	 * radiation lines of the base mod and the addon's types suppressed. Upstream's
	 * {@code @Inject(HEAD)} and {@code @Redirect} on {@code addHazardInfo}; the sludge units
	 * ({@code expie}, in grays) apply when {@link LeafiaConfig#bullshitUnits} is on.
	 */
	public static final HazardSystem.TooltipHandler RADIATION_TOOLTIP = new HazardSystem.TooltipHandler() {

		@Override
		public void head(ItemStack stack, @Nullable Player player, List<Component> list) {
			if(!classifiedReadouts()) return;
			list.addAll(radiationLines(stack, player));
		}

		@Override
		public boolean replaceEntry(HazardEntry entry, ItemStack stack, @Nullable Player player, List<Component> list) {
			if(!classifiedReadouts()) return false;
			HazardTypeBase type = entry.getType();
			return type instanceof HazardTypeRadiation || type instanceof LCERad;
		}
	};

	/** The lines {@link #RADIATION_TOOLTIP} puts at the head: empty for a stack with no radiation. */
	public static List<Component> radiationLines(ItemStack stack, @Nullable Player player) {
		List<Component> out = new ArrayList<>();
		double total = 0, alpha = 0, beta = 0, x = 0, gamma = 0, neutrons = 0, activation = 0, radon = 0;
		for(HazardEntry hazard : HazardSystem.getHazardsFromStack(stack)) {
			HazardTypeBase type = hazard.getType();
			if(!(type instanceof HazardTypeRadiation) && !(type instanceof LCERad)) continue;
			double level = HazardModifier.evalAllModifiers(stack, player, hazard.getBaseLevel(), hazard.getMods());
			total += level * stack.getCount();
			if(type instanceof Alpha) alpha += level;
			else if(type instanceof Beta) beta += level;
			else if(type instanceof XRay) x += level;
			else if(type instanceof Gamma) gamma += level;
			else if(type instanceof Neutrons) neutrons += level;
			else if(type instanceof HazardTypeRadiation) activation += level;
			else if(type instanceof Radon) radon += level;
		}
		if(total <= 0) return out;

		String unit = LeafiaConfig.bullshitUnits ? "expie" : "RAD";
		if(LeafiaConfig.bullshitUnits) {
			alpha = BullshitInDavenportIowa.RADToEx(alpha);
			beta = BullshitInDavenportIowa.RADToEx(beta);
			x = BullshitInDavenportIowa.RADToEx(x);
			gamma = BullshitInDavenportIowa.RADToEx(gamma);
			neutrons = BullshitInDavenportIowa.RADToEx(neutrons);
			activation = BullshitInDavenportIowa.RADToEx(activation);
			total = BullshitInDavenportIowa.RADToEx(total);
			radon = BullshitInDavenportIowa.RADToEx(radon);
		}

		out.add(Component.literal("[").append(Component.translatable("trait.radioactive")).append("]").withStyle(ChatFormatting.GREEN));
		if(alpha > 0) out.add(component("trait._hazarditem.radioactive.alpha", ChatFormatting.RED, alpha, unit));
		if(beta > 0) out.add(component("trait._hazarditem.radioactive.beta", ChatFormatting.AQUA, beta, unit));
		if(x > 0) out.add(component("trait._hazarditem.radioactive.x", ChatFormatting.DARK_AQUA, x, unit));
		if(gamma > 0) out.add(component("trait._hazarditem.radioactive.gamma", ChatFormatting.DARK_GREEN, gamma, unit));
		if(neutrons > 0) out.add(component("trait._hazarditem.radioactive.neutrons", ChatFormatting.YELLOW, neutrons, unit));
		if(activation > 0) out.add(component("trait._hazarditem.radioactive.activation", ChatFormatting.DARK_GRAY, activation, unit));
		if(stack.getCount() > 1) {
			out.add(Component.literal(" -::").withStyle(ChatFormatting.GREEN)
					.append(Component.translatable("trait._hazarditem.stack").withStyle(ChatFormatting.GOLD))
					.append(Component.literal(" " + value(total) + " " + unit).withStyle(ChatFormatting.GOLD)));
		}
		if(radon > 0) out.add(Component.literal(" -::").withStyle(ChatFormatting.GREEN)
				.append(Component.translatable("trait._hazarditem.radioactive.radon"))
				.append(Component.literal(" " + value(radon) + " " + unit)));
		return out;
	}

	private static MutableComponent component(String key, ChatFormatting color, double amount, String unit) {
		return Component.literal(" -::").withStyle(ChatFormatting.GREEN)
				.append(Component.translatable(key).withStyle(color))
				.append(Component.literal(" " + value(amount) + " " + unit).withStyle(color));
	}

	/** {@code roundFloat(getNewValue(v), 3) + getSuffix(v)}, as one string. */
	public static String value(double v) {
		return roundFloat(getNewValue(v), 3) + getSuffix(v);
	}

	// ------------------------------------------------------------------ MixinContaminationUtil

	/**
	 * The classified geiger readout (upstream's {@code @Overwrite} of {@code printGeigerData}): the
	 * same five lines as the base mod's with the dose in sieverts when classification is on (a hundredth
	 * of a RAD), in the sludge units ({@code dyatlov}, {@code expie}, {@code /stevejob}) when those are,
	 * and numbers that go to scientific notation when too large or too small to read.
	 */
	public static final ContaminationUtil.GeigerPrinter GEIGER_PRINTER = player -> {
		if(!classifiedReadouts()) return false;
		printGeigerData(player);
		return true;
	};

	public static void printGeigerData(Player player) {
		for(Component line : geigerLines(player)) player.sendSystemMessage(line);
	}

	/** The five lines of the readout and its banner, as {@link #printGeigerData} sends them. */
	public static List<Component> geigerLines(Player player) {
		double rawRadMod = ContaminationUtil.calculateRadiationMod(player);
		double eRad = HbmLivingProps.getRadiation(player);
		double rads = ChunkRadiationManager.proxy.getRadiation(player.level(),
				(int) Math.floor(player.getX()), (int) Math.floor(player.getY()), (int) Math.floor(player.getZ()));
		double env = HbmLivingProps.getRadBuf(player);
		double res = (1.0 - rawRadMod) * 100.0;
		double resKoeff = HazmatRegistry.getResistance(player) * 100.0;
		double rec = env * rawRadMod;

		double radsD = rads, eRadD = eRad, envD = env, recD = rec;

		boolean sludge = LeafiaConfig.bullshitUnits;
		String unit = sludge ? "dyatlov" : "Sv";
		String unitRAD = sludge ? "expie" : "RAD";
		String unitPS = sludge ? "/stevejob" : "/s";
		if(!LeafiaFeatures.radiationClassification) {
			unit = unitRAD;
			if(sludge) {
				radsD = BullshitInDavenportIowa.RADToEx(radsD);
				eRadD = BullshitInDavenportIowa.RADToEx(eRadD);
				envD = BullshitInDavenportIowa.RADToEx(envD);
				recD = BullshitInDavenportIowa.RADToEx(recD);
			}
		} else {
			eRadD /= 100;
			envD /= 100;
			recD /= 100;
			if(sludge) {
				radsD = BullshitInDavenportIowa.RADToEx(radsD);
				eRadD = BullshitInDavenportIowa.SvToDy(eRadD);
				envD = BullshitInDavenportIowa.SvToDy(envD);
				recD = BullshitInDavenportIowa.SvToDy(recD);
			}
		}
		if(sludge) {
			radsD = BullshitInDavenportIowa.PSToPSj(radsD);
			envD = BullshitInDavenportIowa.PSToPSj(envD);
			recD = BullshitInDavenportIowa.PSToPSj(recD);
		}

		String eRadS = format(eRad, eRadD, 3, 1.0e-3);
		String radsS = format(rads, radsD, 3, 1.0e-3);
		String envS = format(env, envD, 3, 1.0e-3);
		String recS = format(rec, recD, 3, 1.0e-3);
		String resS = format(res, res, 6, 1.0e-6);
		String resKoeffS = format(resKoeff, resKoeff, 2, 1.0e-2);

		String chunkPrefix = ContaminationUtil.getPreffixFromRad(rads);
		String envPrefix = ContaminationUtil.getPreffixFromRad(env);
		String recPrefix = ContaminationUtil.getPreffixFromRad(rec);
		String radPrefix = "";
		String resPrefix = "" + ChatFormatting.WHITE;

		if(eRad < 200) radPrefix += ChatFormatting.GREEN;
		else if(eRad < 400) radPrefix += ChatFormatting.YELLOW;
		else if(eRad < 600) radPrefix += ChatFormatting.GOLD;
		else if(eRad < 800) radPrefix += ChatFormatting.RED;
		else if(eRad < 1000) radPrefix += ChatFormatting.DARK_RED;
		else radPrefix += ChatFormatting.DARK_GRAY;
		if(resKoeff > 0) resPrefix += ChatFormatting.GREEN;

		return List.of(
				Component.literal("===== ☢ ").append(Component.translatable("geiger.title"))
						.append(Component.literal(" ☢ =====")).withStyle(ChatFormatting.GOLD),
				line("geiger.chunkRad", " " + chunkPrefix + radsS + " " + unitRAD + unitPS),
				line("geiger.envRad", " " + envPrefix + envS + " " + unit + unitPS),
				line("geiger.recievedRad", " " + recPrefix + recS + " " + unit + unitPS),
				line("geiger.playerRad", " " + radPrefix + eRadS + " " + unit),
				line("geiger.playerRes", " " + resPrefix + resS + "% (" + resKoeffS + ")"));
	}

	/**
	 * Upstream's number format: {@code %.<d>e} when the <em>raw</em> value is a million or more or
	 * (if non-zero) smaller than {@code small}, else {@code %.<d>f} - of the converted value either way.
	 */
	public static String format(double raw, double shown, int decimals, double small) {
		double ar = Math.abs(raw);
		boolean scientific = ar >= 1.0e6 || (ar > 0.0 && ar < small);
		return String.format(Locale.ROOT, scientific ? "%." + decimals + "e" : "%." + decimals + "f", shown);
	}

	private static MutableComponent line(String key, String value) {
		return Component.translatable(key).append(Component.literal(value)).withStyle(ChatFormatting.YELLOW);
	}

	// ------------------------------------------------------------------ MixinItemGeigerCounter

	/** The addon's nine click sounds, in order; the base mod has six. */
	public static List<DeferredHolder<SoundEvent, SoundEvent>> geigerSounds() {
		return List.of(LeafiaSounds.geiger1, LeafiaSounds.geiger2, LeafiaSounds.geiger3, LeafiaSounds.geiger4,
				LeafiaSounds.geiger5, LeafiaSounds.geiger6, LeafiaSounds.geiger7, LeafiaSounds.geiger8, LeafiaSounds.geiger9);
	}

	/**
	 * The quieter, nine-step geiger (upstream's {@code @Overwrite} of {@code playGeiger}: "the geigers
	 * are too loud at low rad levels"). Every fifth tick: at a dose above 1e-5 it picks a click at
	 * random from the bands the dose falls in (a quiet dose is silent half or more of the time), at a
	 * pitch that climbs above 50 RAD/s to at most 1.1; below it a one-in-a-hundred tick of the first
	 * click. The bands, in RAD/s: 0 under 1 and under 5; 1 under 10; 2 from 10 to 40; 3 from 20 to 60;
	 * 4 from 40 to 90; 5 from 70 to 130; 6 from 100 to 350; 7 from 300 to 800; 8 from 700 to 1600; 9 from 1300.
	 */
	public static final ItemGeigerCounter.ClickHandler GEIGER_CLICKS = (world, holder, stack, x) -> {
		if(!LeafiaFeatures.hazardOverhaul) return false;
		click(world, holder, x);
		return true;
	};

	/** The bands a dose falls in, as click numbers (0 is silence). Upstream's list, in its order. */
	public static List<Integer> geigerBands(double x) {
		List<Integer> list = new ArrayList<>();
		if(x < 1) list.add(0);
		if(x < 5) list.add(0);
		if(x < 10) list.add(1);
		if(x >= 10 && x < 40) list.add(2);
		if(x >= 20 && x < 60) list.add(3);
		if(x >= 40 && x < 90) list.add(4);
		if(x >= 70 && x < 130) list.add(5);
		if(x >= 100 && x <= 350) list.add(6);
		if(x >= 300 && x <= 800) list.add(7);
		if(x >= 700 && x <= 1600) list.add(8);
		if(x >= 1300) list.add(9);
		return list;
	}

	/** The pitch: 1, plus {@code (x - 200) / 35000} above 50 RAD/s, capped at 1.1. */
	public static float geigerPitch(double x) {
		float pitch = 1;
		if(x > 50) pitch = Math.min(pitch + (float) ((x - 200) / 35000), 1.1F);
		return pitch;
	}

	/** One tick of the clicking, for a holder taking {@code x} RAD/s. */
	public static void click(Level world, LivingEntity holder, double x) {
		if(world.getGameTime() % 5 != 0) return;
		List<DeferredHolder<SoundEvent, SoundEvent>> sounds = geigerSounds();
		if(x > 1e-5) {
			List<Integer> list = geigerBands(x);
			int r = list.get(world.random.nextInt(list.size()));
			if(r > 0)
				world.playSound(null, holder.getX(), holder.getY(), holder.getZ(), sounds.get(r - 1).get(), SoundSource.PLAYERS, 1.0F, geigerPitch(x));
		} else if(world.random.nextInt(100) == 0) {
			world.playSound(null, holder.getX(), holder.getY(), holder.getZ(), sounds.get(0).get(), SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	// ------------------------------------------------------------------ MixinBlockGasRadon, MixinBlockRadonDense

	/**
	 * Radon gas, reworked for protection. Plain radon: a fine-particle helmet loses a filter point and the
	 * wearer still takes 0.05 RAD through their resistance; without one, 0.05 RAD that ignores it
	 * ({@code RAD_BYPASS}). Dense radon: the helmet loses two filter points and nothing else happens;
	 * without one, 0.5 RAD bypassing resistance. The base mod's asbestos in the lungs is gone with the
	 * rework, as upstream's. Needs {@code enableRadon} (the addon's option; the base mod has none).
	 */
	public static final BlockGasRadon.ContactHandler RADON_CONTACT = (gas, state, level, pos, entity) -> {
		if(!LeafiaFeatures.hazardOverhaul) return false;
		boolean dense = gas instanceof BlockGasRadonDense;
		if(!dense && !(gas instanceof BlockGasRadon)) return false;
		contact(entity, dense);
		return true;
	};

	public static void contact(LivingEntity entity, boolean dense) {
		if(!LeafiaConfig.enableRadon) return;
		if(ArmorRegistry.hasAllProtection(entity, 3, HazardClass.PARTICLE_FINE)) {
			ArmorUtil.damageGasMaskFilter(entity, dense ? 2 : 1);
			if(!dense) ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.CREATIVE, 0.05F);
		} else {
			ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.RAD_BYPASS, dense ? 0.5F : 0.05F);
		}
	}

	// ------------------------------------------------------------------ MixinEntityEffectHandler

	/**
	 * The crater biomes do not irradiate whoever is underground: an entity eight blocks or more below the
	 * surface ({@link RadCorrection#isUnderground}) takes none of the biome's dose. Upstream's first
	 * redirect, on the {@code contaminate} that applies it. <b>Its second redirect</b> - reading the
	 * generated biome instead of the painted one underground - is subsumed: nothing in the crater branch
	 * reads the biome except to choose that dose, which this has already made zero.
	 */
	public static final RadiationEffectHandler.CraterHook CRATER_UNDERGROUND = new RadiationEffectHandler.CraterHook() {
		@Override
		public float radiation(LivingEntity entity, ResourceKey<Biome> biome, float radiation) {
			if(!LeafiaFeatures.hazardOverhaul) return radiation;
			return RadCorrection.isUnderground(entity) ? 0F : radiation;
		}
	};
}
