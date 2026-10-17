package com.leafia.init.hazards;

import java.util.ArrayList;
import java.util.List;

import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.lib.NtmTags;
import com.leafia.config.LeafiaFeatures;
import com.leafia.database.AddonOreDictHazards;
import com.leafia.init.hazards.modifiers.NBTModifier;
import com.leafia.init.hazards.modifiers.NBTModifier.NBTKey;
import com.leafia.init.hazards.types.radiation.Alpha;
import com.leafia.init.hazards.types.radiation.Beta;
import com.leafia.init.hazards.types.radiation.Gamma;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.init.hazards.types.radiation.Radon;
import com.leafia.init.hazards.types.radiation.XRay;

import net.minecraft.world.level.ItemLike;

/**
 * The addon's radiation classification: what each radioactive material gives off, split into
 * alpha, beta, X-ray, gamma, neutrons, radon and "activation" (the original single figure).
 *
 * <p>The table below is generated from upstream by a generator script - 57
 * containers, each one the argument list of a {@link MultiRadContainer}, in upstream's order
 * because several are built from an earlier one. The five-argument form is
 * {@code (alpha, beta, x, gamma, neutrons)}; the seven-argument form adds {@code radon} and
 * {@code activation}. <b>Read the argument order carefully</b>: x-rays come third and gamma
 * fourth, which is not the order the types are listed in anywhere else.
 *
 * <p>{@link MultiRadContainer#register} is what puts one on an item. It <em>replaces</em> the
 * item's plain radiation entry - the first {@link HazardTypeRadiation} in its hazard data - with
 * one entry per non-zero component, each carrying an {@link NBTModifier} on its own key. It
 * does so only while {@code features.radiationClassification} is on, which is upstream's
 * {@code enableHealthMod}: with it off the call returns before touching anything, so an item the
 * base mod already made radioactive keeps its single figure and an item only the addon registers
 * (the corium ores) carries none. See D-1078 for the switch's default and for what is and is not
 * registered.
 */
public class ItemRads {

	// BEGIN GENERATED
	public static final MultiRadContainer actinium227 = new MultiRadContainer(0,30,0,0,0);
	public static final MultiRadContainer americium241 = new MultiRadContainer(8.5,0,0,0,0);
	public static final MultiRadContainer americium242 = new MultiRadContainer(0,9.5,0,0,0);
	public static final MultiRadContainer americiumRG = new MultiRadContainer(0,9,0,0,0);
	public static final MultiRadContainer americiumFuel = new MultiRadContainer(0,4.75,0,0,0);
	public static final MultiRadContainer balefire = new MultiRadContainer(0,0,0,300000,0);
	public static final MultiRadContainer cobalt60 = new MultiRadContainer(0,30,30,60,0).multiply(1/3f);
	public static final MultiRadContainer corium = new MultiRadContainer(30,30,30,30,50,50,0);
	public static final MultiRadContainer fissium = new MultiRadContainer(0,0,0,10,30);
	public static final MultiRadContainer fissite = fissium.multiply(0.1);
	public static final MultiRadContainer flashlead = new MultiRadContainer(0,500+10000+2000,500,500,0).multiply(1/2f);
	public static final MultiRadContainer francium = new MultiRadContainer(150000,0,0,1400000,0);
	public static final MultiRadContainer gold198 = new MultiRadContainer(0,500,500,500,0).multiply(1/2f);
	public static final MultiRadContainer lead209 = new MultiRadContainer(0,10000,0,0,0);
	public static final MultiRadContainer moxie = new MultiRadContainer(2.5,0,0,0,0);
	public static final MultiRadContainer neptunium237 = new MultiRadContainer(2.5,0,0,0,0);
	public static final MultiRadContainer neptuniumFuel = new MultiRadContainer(1.5,0,0,0,0);
	public static final MultiRadContainer nuclium279 = new MultiRadContainer(0,250,0,70,0);
	public static final MultiRadContainer plutonium = new MultiRadContainer(7.5,0,0,0,0);
	public static final MultiRadContainer plutoniumRG = new MultiRadContainer(6.25,0,0,0,0);
	public static final MultiRadContainer plutoniumFuel = new MultiRadContainer(4.25,0,0,0,0);
	public static final MultiRadContainer plutonium238 = new MultiRadContainer(10,0,0,0,0);
	public static final MultiRadContainer plutonium239 = new MultiRadContainer(5,0,0,0,0);
	public static final MultiRadContainer plutonium240 = new MultiRadContainer(7,0,0,0,0);
	public static final MultiRadContainer plutonium241 = new MultiRadContainer(0,25f,25f,0,0).multiply(1/2f);
	public static final MultiRadContainer plutonium238be = plutonium238.multiply(0.5);
	public static final MultiRadContainer polonium210 = new MultiRadContainer(75,0,0,0,0);
	public static final MultiRadContainer polonium210be = polonium210.multiply(0.5);
	public static final MultiRadContainer radium226 = new MultiRadContainer(7.5,0,0,0,0,5,0);
	public static final MultiRadContainer radium226be = radium226.multiply(0.5);
	public static final MultiRadContainer schrabidium326 = new MultiRadContainer(15,0,0,0,0);
	public static final MultiRadContainer solinium327 = new MultiRadContainer(17.5,0,0,0,0);
	public static final MultiRadContainer schrabidate = new MultiRadContainer(1.5,0,0,0,0);
	public static final MultiRadContainer schraranium = new MultiRadContainer(1.5,0,0,0,0);
	public static final MultiRadContainer schrabidiumLow = new MultiRadContainer(2.52,0,0,0,0);
	public static final MultiRadContainer schrabidiumMedium = new MultiRadContainer(5.85,0,0,0,0);
	public static final MultiRadContainer schrabidiumHigh = new MultiRadContainer(8.8,0,0,0,0);
	public static final MultiRadContainer technetium99 = new MultiRadContainer(0,2.75,0,0,0);
	public static final MultiRadContainer tritium = new MultiRadContainer(0,0,0.5f,0,0);
	public static final MultiRadContainer thorium232 = new MultiRadContainer(0.1,0,0,0,0);
	public static final MultiRadContainer thoriumFuel = new MultiRadContainer(1.75,0,0,0,0);
	public static final MultiRadContainer uranium = new MultiRadContainer(0.35,0,0,0,0,0.5/2,0);
	public static final MultiRadContainer uranium233 = new MultiRadContainer(5,0,0,0,0,0.85/2,0);
	public static final MultiRadContainer uranium235 = new MultiRadContainer(1,0,0,0,0,0.75/2,0);
	public static final MultiRadContainer uranium238 = new MultiRadContainer(0.25,0,0,0,0,0.1,0);
	public static final MultiRadContainer uraniumFuel = new MultiRadContainer(0.5,0,0,0,0,0.65,0);
	public static final MultiRadContainer waste = new MultiRadContainer(0,125,125,50,25);
	public static final MultiRadContainer waste_v = waste.copy().multiply(1/2f);
	public static final MultiRadContainer wasteUranium = waste; // temporary
	public static final MultiRadContainer wastePlutonium = waste; // temporary
	public static final MultiRadContainer wasteThorium = waste; // temporary
	public static final MultiRadContainer wasteMOX = waste; // temporary
	public static final MultiRadContainer wasteSchrabidium = waste; // temporary
	public static final MultiRadContainer xanaxium = new MultiRadContainer(0,0,0,2.2,0,0,0);
	public static final MultiRadContainer bismuth209zfb = uranium235.multiply(0.35f);
	public static final MultiRadContainer plutonium241zfb = plutonium241.multiply(0.5f);
	public static final MultiRadContainer americium242zfb = americiumRG.multiply(0.5f);
	// END GENERATED

	public static class MultiRadContainer {
		public double alpha;
		public double beta;
		public double gamma;
		public double x;
		public double neutrons;
		public double radon;
		public double activation;

		public MultiRadContainer(double alpha, double beta, double x, double gamma, double neutrons) {
			this.alpha = alpha;
			this.beta = beta;
			this.x = x;
			this.gamma = gamma;
			this.neutrons = neutrons;
		}

		public MultiRadContainer(double alpha, double beta, double x, double gamma, double neutrons, double radon, double activation) {
			this(alpha, beta, x, gamma, neutrons);
			this.radon = radon;
			this.activation = activation;
		}

		public MultiRadContainer multiply(double v) {
			MultiRadContainer c = copy();
			c.alpha *= v;
			c.beta *= v;
			c.x *= v;
			c.gamma *= v;
			c.neutrons *= v;
			c.radon *= v;
			c.activation *= v;
			return c;
		}

		public MultiRadContainer copy() {
			return new MultiRadContainer(alpha, beta, x, gamma, neutrons, radon, activation);
		}

		/**
		 * Upstream's {@code register(Block)} and {@code register(Item)} in one: a
		 * {@code DeferredItem}, a {@code DeferredBlock} or a bare item or block all answer
		 * {@code asItem()}, which is what the base mod's own registry keys on.
		 */
		public void register(ItemLike item) {
			if(!LeafiaFeatures.radiationClassification) return; // before computeIfAbsent: off leaves no trace
			HazardData data = HazardSystem.itemMap.computeIfAbsent(item.asItem(), k -> new HazardData());
			// if you need to add a hazard modifier to HazardTypeRadiation, find its entry and addMod
			apply(data);
		}

		/**
		 * Upstream's {@code register(DictFrame)}: every name the frame's materials go by, each at its
		 * shape's multiplier ({@link AddonOreDictHazards#table}). A block of uranium is ten times an ingot.
		 */
		public void register(DictFrame frame) {
			if(!LeafiaFeatures.radiationClassification) return;
			for(java.util.Map.Entry<String, Float> entry : AddonOreDictHazards.table(frame).entrySet())
				register(entry.getKey(), entry.getValue());
		}

		/**
		 * By ore name, upstream's {@code register(String)}: the name becomes the item tag the base
		 * mod keys its ore dictionary on ({@link NtmTags#ore}).
		 */
		public void register(String item) {
			register(item, 1);
		}

		public void register(String item, float mul) {
			if(!LeafiaFeatures.radiationClassification) return;
			HazardData data = HazardSystem.oreMap.computeIfAbsent(NtmTags.ore(item), k -> new HazardData());
			apply(data, mul);
		}

		public void apply(HazardData data) {
			apply(data, 1);
		}

		public void apply(HazardData data, float multiplier) {
			if(!LeafiaFeatures.radiationClassification) return;
			List<HazardEntry> entries = data.getEntries();
			for(HazardEntry entry : new ArrayList<>(entries)) {
				if(entry.getType() instanceof HazardTypeRadiation) {
					entries.remove(entry);
					break;
				}
			}
			if(alpha > 0) data.addEntry(new HazardEntry(Alpha.INSTANCE, (float) (alpha * multiplier)).addMod(new NBTModifier(NBTKey.ALPHA)));
			if(beta > 0) data.addEntry(new HazardEntry(Beta.INSTANCE, (float) (beta * multiplier)).addMod(new NBTModifier(NBTKey.BETA)));
			if(gamma > 0) data.addEntry(new HazardEntry(Gamma.INSTANCE, (float) (gamma * multiplier)).addMod(new NBTModifier(NBTKey.GAMMA)));
			if(neutrons > 0) data.addEntry(new HazardEntry(Neutrons.INSTANCE, (float) (neutrons * multiplier)).addMod(new NBTModifier(NBTKey.NEUTRONS)));
			if(x > 0) data.addEntry(new HazardEntry(XRay.INSTANCE, (float) (x * multiplier)).addMod(new NBTModifier(NBTKey.XRAY)));
			if(radon > 0) data.addEntry(new HazardEntry(Radon.INSTANCE, (float) (radon * multiplier)).addMod(new NBTModifier(NBTKey.RADON)));
			if(activation > 0) data.addEntry(new HazardEntry(HazardRegistry.RADIATION, (float) (activation * multiplier)).addMod(new NBTModifier(NBTKey.ACTIVATION)));
		}
	}
}
