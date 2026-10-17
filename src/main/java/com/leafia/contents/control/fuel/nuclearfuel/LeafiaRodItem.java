package com.leafia.contents.control.fuel.nuclearfuel;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.custom_hbm.contents.torex.LCETorex;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityBalefire;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemDepletedFuel;
import com.hbm.lib.Library;
import com.hbm.util.ArmorUtil;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaConfig;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.dev.items.itembase.AddonItemHazardBase;
import com.leafia.init.hazards.ItemRads.MultiRadContainer;
import com.leafia.init.hazards.types.radiation.Neutrons;
import com.leafia.unsorted.BullshitInDavenportIowa;
import com.llib.LeafiaLib;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The addon's fuel rod: one generic rod item for every isotope, each with its own heat curve, melting
 * point, life, decay product and radiation.
 *
 * <p>Upstream: {@code com/leafia/contents/control/fuel/nuclearfuel/LeafiaRodItem.java}. The rod is a
 * <em>stack</em>, not a block entity: heat, depletion and decay live on the stack, and
 * {@link #HeatFunction} advances them. A reactor that holds the rod calls it once a tick with the
 * flux it received and the coolant around it; a player carrying one calls it through
 * {@link #inventoryTick} with nothing, so a rod cools in a pocket and burns the holder when hot.
 *
 * <h2>Where the state is</h2>
 *
 * Upstream keeps it on the stack's NBT tag. Here it is the {@code custom_data} component, under the
 * same keys: {@code heat} (starts at 20), {@code depletion}, {@code melting}, {@code generosityTimer}
 * (90 ticks of grace past the melting point), {@code spillage}, {@code decay}, plus the two a reactor
 * writes, {@code incoming} (the flux, which is what the neutron hazard reads) and {@code cooled}.
 * A stack with no tag at all is a fresh rod, and {@link #HeatFunction} writes the starting values
 * the first time it runs against one.
 *
 * <h2>The heat curve</h2>
 *
 * {@link #HeatFunction} is the whole of the rod's physics and it is a switch over the rod's function id
 * (the label with {@code -} and spaces removed, lowercased). Each case is a curve from incoming flux
 * {@code x} to the temperature {@code y} the rod settles toward, and a string describing it for the
 * tooltip. The cases are copied exactly - several fall through on purpose ({@code catsmilium} into the
 * uranium curve) - and every constant in them is balance (rule 4).
 *
 * <h2>What waits</h2>
 *
 * <ul>
 *   <li>The digamma rod's blast ({@link #nuke}, case {@code dgomega}) grants a player whose digamma it
 *       raised {@code dgomega} ({@code LeafiaAdvancements.DG_OMEGA}); it landed with the close-out batch,
 *       with the rest of {@code AddonAdvancements} (D-3040).</li>
 *   <li><b>{@code balecorium}</b> (landed in D-3004): the balefire rods melt into
 *       {@code fluid_balecorium}, the balefire corium of {@code LeafiaLiquids}. Upstream names the block
 *       by its field; the registry name is {@code balecorium_fluid}, and {@link #corium()} maps one to
 *       the other. With {@code features.content.worldFluids} off there is no such block, and the rods
 *       melt into the base mod's corium, as every other rod does.</li>
 * </ul>
 *
 * <p>The tooltip's fluid-of-units switch is {@link LeafiaConfig#bullshitUnits} (upstream's
 * {@code enableSludgeUnits}).
 */
public class LeafiaRodItem extends AddonItemHazardBase {

	public enum ItemType {
		VOID,
		BILLET,
		BONEMEAL,
		DEPLETED
	}

	public enum Purity {
		RAW,		// no icon
		ISOTOPE,	// "I" icon
		FUEL,		// "F" icon
		SOURCE,		// "S" icon
		BREEDER,	// "B" icon
		UNSTABLE	// "X" icon
	}

	/** Every rod, by registry name ({@code leafia_rod_u238}), filled as each is constructed. */
	public static final Map<String, LeafiaRodItem> fromResourceMap = new HashMap<>();

	double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** What the rod is made from and what uncrafting it gives back; null when it has none. */
	public Supplier<? extends Item> baseItem = null;
	/** Upstream's metadata: 1 on a depleted fuel means the waste is still cooling. */
	public int baseMeta = 0;
	public ItemType baseItemType = ItemType.VOID;
	public boolean specialRodModel = false;
	public Purity purity = Purity.RAW;
	public String functionId = "null";
	public double life = 0;
	public double meltingPoint = 1538;
	public String label = "ERROR!";
	public String decayProductBuffer = null;
	public double emission = 1;
	public double reactivity = 1;
	public boolean splitIntoFast = true;
	public boolean splitWithFast = false;
	public boolean splitWithAny = false;
	/** Tooltip lines placed above the rod's own, in italics: upstream's three anonymous subclasses. */
	public final List<String> intro = new ArrayList<>();

	/** The block a melted rod leaves: the base mod's corium unless {@link #setCorium} named another. */
	private String coriumBlock = null;

	/**
	 * Which of two rods melts down first when both are melting: the higher priority, ties to the first
	 * argument. Null means "no rod".
	 */
	@Nullable
	public static ItemStack comparePriority(ItemStack a, @Nullable ItemStack b) {
		boolean validA = a.getItem() instanceof LeafiaRodItem;
		if(b == null) {
			if(validA) return a;
			else return null;
		} else {
			boolean validB = b.getItem() instanceof LeafiaRodItem;
			if(!validA && !validB)
				return null;
			else if(validA && !validB)
				return a;
			else if(!validA && validB)
				return b;
			int myPriority = ((LeafiaRodItem) a.getItem()).meltdownPriority;
			if(((LeafiaRodItem) b.getItem()).meltdownPriority > myPriority)
				return b;
			else
				return a;
		}
	}

	public int meltdownPriority = 0;

	public float detonateRadius = 5;
	/** "the fuck is point of this" - upstream's own comment. Never set by anything. */
	@Deprecated
	public boolean detonateNuclear = false;
	public boolean detonateVisualsOnly = false;
	public String detonateConfiguration = "default";

	public LeafiaRodItem resetDetonate() {
		detonateRadius = 5;
		detonateNuclear = false;
		detonateVisualsOnly = false;
		detonateConfiguration = "default";
		return this;
	}

	// ------------------------------------------------------------------------------- NBT access

	/** The stack's custom data as a tag, or null when it has none (a fresh rod). */
	@Nullable
	public static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag();
	}

	public static void putData(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// ----------------------------------------------------------------------------- the blast

	/**
	 * The entities the digamma rod's blast visits: a <b>copy</b> of the level's entity list taken before the loop (D-3031).
	 * Upstream iterates {@code getLoadedEntityList()} live and catches the {@code ConcurrentModificationException} that
	 * a change would throw; the 1.21.1 iterable is fastutil's, which throws nothing and was once seen spinning for twenty
	 * minutes in {@code next()} while {@code setDigamma} changed what it visited (D-3025 #2, one sample, cause unknown).
	 * An entity that joins the level after this call is not visited.
	 */
	public static Iterable<Entity> digammaTargets(ServerLevel server) {
		List<Entity> all = new ArrayList<>();
		server.getAllEntities().forEach(all::add);
		return all;
	}

	/**
	 * What a rod does when it reaches ten million degrees ({@code nuke}): a mushroom cloud and an
	 * explosion chosen by function id. Server side.
	 */
	public void nuke(Level world, BlockPos pos) {
		float x = pos.getX() + 0.5f;
		float y = pos.getY() + 0.5f;
		float z = pos.getZ() + 0.5f;
		switch(functionId) {
			case "balefire": case "blazingbalefire": {
				EntityNukeTorex.statFacBale(world, x, y, z, 180);
				EntityBalefire bf = new EntityBalefire(world);
				bf.setPos(x, y, z);
				bf.destructionRange = 280;
				world.addFreshEntity(bf);
				break;
			}
			case "dgomega": {
				try {
					if(world instanceof ServerLevel server) {
						for(Entity entity : digammaTargets(server)) {
							if(entity instanceof LivingEntity living) {
								Vec3 entityPos = new Vec3(living.getX(), living.getY(), living.getZ());
								Vec3 myPos = new Vec3(x, y, z);
								double distance = entityPos.distanceTo(myPos);
								double dg = HbmLivingProps.getDigamma(living);
								double level = Math.max(dg, Math.min(50 - distance / 20, 9.99999999));
								HbmLivingProps.setDigamma(living, (float) level);
								if(living instanceof net.minecraft.server.level.ServerPlayer player && level > dg)
									com.leafia.init.LeafiaAdvancements.grant(player, com.leafia.init.LeafiaAdvancements.DG_OMEGA);
							}
						}
					}
				} catch(ConcurrentModificationException ignored) {} // fuck off
				LCETorex.statFacDigamma(world, x, y, z, 50);
				EntityNukeExplosionMK5 mk5 = EntityNukeExplosionMK5.statFac(world, 50, x, y, z);
				com.leafia.overwrite_contents.LeafiaFalloutVariants.setDigammaFallout(mk5); // upstream's IMixinEntityNukeExploisonMK5 (D-3009)
				world.addFreshEntity(mk5);
				break;
			}
			case "sa326": {
				/*
				 * Upstream builds the MK3 by hand and asks EntityNukeExplosionMK3.isJammed (CE) whether a
				 * field disturber protects the spot. The base mod's statFacFleija is the same sequence with
				 * the disturber check inside it: a jammed blast comes back already discarded.
				 */
				EntityNukeExplosionMK3 entity = EntityNukeExplosionMK3.statFacFleija(world, x, y, z, 50);
				entity.speed = BombConfig.blastSpeed;
				entity.coefficient = 1.0F;
				entity.waste = false;
				if(!entity.isRemoved()) {
					world.addFreshEntity(entity);

					EntityCloudFleija cloud = new EntityCloudFleija(world, 50);
					cloud.setPos(x, y, z);
					world.addFreshEntity(cloud);
				}
				break;
			}
			default: {
				EntityNukeTorex.statFacStandard(world, x, y, z, 100);
				world.addFreshEntity(EntityNukeExplosionMK5.statFac(world, 100, x, y, z));
				break;
			}
		}
	}

	/**
	 * Called with null arguments from the constructor to set {@link #meltdownPriority}; with a world,
	 * it is the blast of a rod that has melted. The blast radius is {@link #detonateRadius} times the
	 * rod's factor, returned; the field itself is no longer grown by each call (D-3075, was D-3001).
	 */
	public float detonate(@Nullable Level world, @Nullable BlockPos pos) {
		boolean explode = (world != null);
		float radius = detonateRadius; // D-3075: local; upstream's *= grew the item singleton's field on every call
		switch(functionId) {
			case "flashgold": case "flashlead":
				meltdownPriority = 10;
				if(explode) {
					float x = pos.getX() + 0.5f;
					float y = pos.getY() + 0.5f;
					float z = pos.getZ() + 0.5f;
					radius *= 1.5f;
					EntityNukeTorex.statFacStandard(world, x, y, z, radius);
					if(!detonateVisualsOnly)
						world.addFreshEntity(EntityNukeExplosionMK5.statFac(world, (int) radius, x, y, z));
				}
				break;
			case "balefire": case "blazingbalefire":
				meltdownPriority = 20;
				if(explode) {
					float x = pos.getX() + 0.5f;
					float y = pos.getY() + 0.5f;
					float z = pos.getZ() + 0.5f;
					radius *= 2.5f;
					EntityNukeTorex.statFacBale(world, x, y, z, radius);
					if(!detonateVisualsOnly) {
						EntityBalefire bf = new EntityBalefire(world);
						bf.setPos(x, y, z);
						bf.destructionRange = (int) radius;
						world.addFreshEntity(bf);
					}
				}
				break;
			default:
				break;
		}
		if(explode && !detonateVisualsOnly) {
			ChunkRadiationManager.incrementRad(world, pos, 1000F, 2000F);
			if(!detonateNuclear) {
				world.explode(null, pos.getX(), pos.getY(), pos.getZ(), radius * 1.5f, Level.ExplosionInteraction.TNT);
				ExplosionNukeGeneric.waste(world, pos.getX(), pos.getY(), pos.getZ(), (int) radius * 2);
			}
		}
		return radius;
	}

	// ------------------------------------------------------------------------- the heat function

	double lastY = 0;

	/**
	 * Does nuclear fissions.
	 *
	 * @param stack the fuel rod stack to cause fission reaction
	 * @param updateHeat true for fission reaction, false for item tooltip
	 * @param x incoming heat
	 * @param cool coolant fraction, 0 to 1
	 * @param desiredTemp temperature of coolant
	 * @param coolingRate temperature of hot coolant
	 * @return tooltip message
	 */
	public String HeatFunction(@Nullable ItemStack stack, boolean updateHeat, double x, double cool, double desiredTemp, double coolingRate) {
		return HeatFunction(stack, updateHeat, x, cool, desiredTemp, coolingRate, 0);
	}

	public String HeatFunction(@Nullable ItemStack stack, boolean updateHeat, double x, double cool, double desiredTemp, double coolingRate, double minimumRequired) {
		return HeatFunction(stack, updateHeat, x, cool, desiredTemp, coolingRate, minimumRequired, 1);
	}

	public boolean lastDisableDecay = false;

	/**
	 * The temperature the rod settles toward at incoming flux {@code x}: the curve {@link #HeatFunction}
	 * evaluates, without a stack. What the tooltip's graph is drawn from.
	 */
	public double curve(double x) {
		HeatFunction(null, false, x, 0, 0, 0);
		return lastY;
	}

	public String HeatFunction(@Nullable ItemStack stack, boolean updateHeat, double x, double cool, double desiredTemp, double coolingRate, double minimumRequired, double heatMultiplier) {
		CompoundTag data = null;
		String flux = ChatFormatting.RED + "0°C" + ChatFormatting.YELLOW;
		String temp = ChatFormatting.GOLD + "ERROR°C" + ChatFormatting.YELLOW;
		double heat = 0;
		if(stack != null) {
			data = data(stack);
			if(data != null) {
				heat = data.getDouble("heat") - 20;
				flux = ChatFormatting.RED + String.format("%01.2f", data.getDouble("incoming")) + "°C" + ChatFormatting.YELLOW;
				temp = ChatFormatting.GOLD + "(" + String.format("%01.2f", data.getDouble("heat")) + "°C-20)" + ChatFormatting.YELLOW;
			}
		}
		boolean disableDecay = false;
		String n = "0";
		double tempx = heat - 20;
		double y = 0; // x = 20+~~
		// Most are Rational
		// Balefire is Polynomial n=3
		switch(functionId) {
			// DEPLETED
			case "depleteduranium": case "depletedmox":
			case "depletednaturaluranium": case "depleteduranium233": case "depleteduranium235": case "depleteduranium238":
				y = 80 - 20;
				n = "80-20";
				disableDecay = true;
				break;
			case "depletedplutonium":
				y = 90 - 20;
				n = "90-20";
				disableDecay = true;
				break;
			case "depletedthorium":
				y = 60 - 20;
				n = "60-20";
				disableDecay = true;
				break;
			case "depletedschrabidium":
			case "depletedmesa326":
			case "depletedhesa326":
			case "depletedsa326":
			case "depletedsa327":
				y = 100 - 20;
				n = "100-20";
				disableDecay = true;
				break;

			case "confusium":
				y = Math.abs(Math.tan(x)) * 1000;
				n = "abs(tan(" + flux + "))×1000 " + ChatFormatting.GRAY + "(??????)";
				break;
			case "catsmilium":
				disableDecay = true;

			// URANIUM
			case "meu235": case "nu": //case "u238": thanks grabber for telling me the reality
				y = Math.pow(x * 8, 0.56) * 3;
				n = "(" + flux + "×8)^0.56×3 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;
			case "heu235": case "heu233":
				y = Math.pow(x * 8, 0.56) * 3 + Math.pow(Math.max(x - 2500, 0) / 1000, 3);
				n = "(" + flux + "×8)^0.56×3 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;
			case "mox":
				y = Math.pow(x * 8, 0.56) * 2;
				n = "(" + flux + "×8)^0.56×2 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;

			// THORIUM
			case "thmeu":
				y = Math.pow(x * 65, 0.35) * 3;
				n = "(" + flux + "×65)^0.35×3 " + ChatFormatting.DARK_AQUA + "(POOR)";
				break;

			// PLUTONIUM
			case "lepu239": case "mepu239": case "npu": case "pu240":
				y = Math.pow(x * 8, 0.6) * 3;
				n = "(" + flux + "×8)^0.6×3 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;
			case "hepu239": case "hepu241":
				y = Math.pow(x * 8, 0.6) * 3 + Math.pow(Math.max(x - 2500, 0) / 800, 3);
				n = "(" + flux + "×8)^0.6×3 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;

			// AMERICIUM
			case "leam242": case "meam242": case "heam242":
				y = Math.pow(x * 8, 0.64) * 3;
				n = "(" + flux + "×8)^0.64×3 " + ChatFormatting.GOLD + "(RISKY)";
				break;
			case "heam241":
				y = Math.pow(x * 8, 0.64) * 3 + Math.pow(Math.max(x - 2500, 0) / 800, 3);
				n = "(" + flux + "×8)^0.64×3 " + ChatFormatting.GOLD + "(RISKY)";
				break;

			// NEPTUNIUM
			case "menp237": case "henp237":
				y = Math.pow(x * 8, 0.52) * 3;
				n = "(" + flux + "×8)^0.52×3 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;

			// SCHRABIDIUM
			case "lesa326": case "mesa326": case "hesa326": case "sa326": case "sa327":
				y = Math.pow(x, 0.65) * 12 + Math.pow(Math.max(x - 2500, 0) / 1600, 3);
				n = "" + flux + "^0.65×12 " + ChatFormatting.GOLD + "(RISKY)";
				break;

			// RADIUM
			case "ra226be":
				y = 300 / (1 + Math.pow(Math.E, -0.02 * x)) - 150;
				n = "300/(1+e^(-0.02×" + flux + "))-150 " + ChatFormatting.DARK_AQUA + "(POOR)";
				break;

			// OTHER
			case "potentialinstantblowoutapplicator":
				y = Math.tan(Math.min(heat / 400, 0.5) * Math.PI) + x / 4;
				n = "tan(min(" + temp + "/400,0.5)*PI)\n + " + flux + "/4 " + ChatFormatting.DARK_RED + "(JUST NO)";
				break;

			// B.F.
			case "balefire":
				y = 100 * Math.pow(x / 1000 - 2, 3) - 500 * Math.pow(x / 1000 - 2, 2) + x / 600 + 2800;
				n = "100((" + flux + "/1000-2)³)-500((" + flux + "/1000-2)²)+" + flux + "/600+2800 " + ChatFormatting.RED + "(DANGEROUS)";
				break;
			case "blazingbalefire":
				double z = x + 1000;
				y = 100 * Math.pow(z / 1000 - 2, 3) - 500 * Math.pow(z / 1000 - 2, 2) + z / 600 + 2800;
				n = "100((" + flux + "/1000-1)³)-500((" + flux + "/1000-1)²)+(" + flux + "+1000)/600+2800 " + ChatFormatting.RED + "(DANGEROUS)";
				break;

			// COPY OLD SHIT
			case "po210": case "po210be":
				y = 700 + Math.pow(x * 2, 0.69);
				n = "700 + (" + flux + "×2)^0.69 " + ChatFormatting.GOLD + "(RISKY)";
				break;
			case "au198":
				y = 1580 + Math.pow(x, 0.75) * 2.5;
				n = "1580 + " + flux + "^0.75 * 2.5 " + ChatFormatting.GOLD + "(RISKY)";
				break;
			case "pb209":
				y = 2300 + x * 0.4;
				n = "2300 + " + flux + "×0.4 " + ChatFormatting.DARK_RED + "(DANGEROUS)";
				break;

			// SOME RUSHED CRAP
			case "pu238": case "pu238be":
				y = 1330 + Math.pow(x * 2, 0.62);
				n = "1330 + (" + flux + "*2)^0.62 " + ChatFormatting.GOLD + "(RISKY)";
				break;

			case "leaus": case "heaus":
				y = Math.pow(x * 5, 0.65) * 2;
				n = "(" + flux + "×5)^0.65×2 " + ChatFormatting.DARK_GREEN + "(FINE)";
				break;

			case "debug":
				y = Math.max(heat, 0) + Math.sqrt(x);
				n = temp + "+20+√" + flux + ChatFormatting.GRAY + " (DEBUG)";
				disableDecay = true;
				break;

			// YHARONITE
			case "yhxxx":
				y = Math.pow(x, 0.75) * 8;
				n = "" + flux + "^0.75×8 " + ChatFormatting.RED + "(DANGEROUS)";
				disableDecay = true;
				break;
			// DIGAMMA
			case "dgomega":
				y = Math.pow(1.01916169, x);
				n = "1.01916169^" + flux + " " + ChatFormatting.DARK_RED + "(JUST NO)";
				disableDecay = true;
				break;
			case "kys3000":
				y = Math.tan(x);
				n = "tan(" + flux + ") " + ChatFormatting.LIGHT_PURPLE + "(KYS)";
				break;

			case "shitfuckium":
				y = (double) ShitfuckiumData.evalute(x / 3200 * 0.6) * 3200 / ShitfuckiumData.height * 1.25;
				n = ChatFormatting.DARK_RED + "Indescribable";
				break;
		}
		lastY = y;
		if(updateHeat) {
			double decay = 0;
			if(data == null) {
				data = new CompoundTag();
				data.putDouble("heat", 20);
				data.putDouble("depletion", 0);
				data.putBoolean("melting", false);
				data.putInt("generosityTimer", 90);
				data.putInt("spillage", 0);
				data.putDouble("decay", decay);
			}
			boolean meltdown = data.getBoolean("melting");
			data.putDouble("incoming", x);
			if(meltdown) {
				//y = heat+20;
				//cool = 0; // it's only a matter of time until your machine explodes >:)
				data.putInt("spillage", data.getInt("spillage") + 1);
			}
			if(data.getDouble("depletion") >= life && life > 0)
				y = 0;
			heat = data.getDouble("heat");
			decay = data.getDouble("decay");
			double heatX = Math.max(heat, 20);
			double heatMg = Math.pow(Math.abs((20 + y) - heat) + 1, 0.25) - 1;
			if(heatX > 20 + y)
				heatMg = heatMg * -1;
			if(Math.abs(heatMg) < 0.00001)
				heatMg = 0;
			if(!meltdown && (heatMg != 0 || x > 0)) {
				double curDepletion = data.getDouble("depletion") + Math.max(heatMg / 2 + Math.pow(x, 0.95) / 2000, 0); // +y is preferred but it doesnt really work with inert materials like lithium soo
				data.putDouble("depletion", curDepletion);
			}
			if(heatMg > 0)
				heatMg *= heatMultiplier;
			double decayBase = Math.min(heatMg / 2, x);
			if(decayBase > decay)
				decay += (decayBase - decay) * 0.01;
			if(disableDecay) decay = 0;
			decay *= 0.99992694932; // this is f*cked lmao //0.99854;
			data.putDouble("decay", decay);
			double fuckoff = 1 - Math.pow(Math.min(decay * 20 / 12, 1), 0.5);
			if(heatMg < 0)
				heatMg *= fuckoff;
			double newTemp = heat + heatMg + Math.max(decay - Math.max(heatMg, 0), 0);
			if(Double.isNaN(newTemp))
				newTemp = Double.MAX_VALUE;
			double cooled = (
					Math.pow(
							Math.max(newTemp - desiredTemp, 0) + 1,
							Math.pow(coolingRate, 0.5) / 100
					) - 1
			) * cool;
			if(cooled < minimumRequired) cooled = 0;
			double newCooledTemp = Math.max(newTemp - cooled, -273.15/*20*/);
			data.putDouble("cooled", cooled);
			if(Double.isNaN(newCooledTemp))
				newCooledTemp = Double.MAX_VALUE;
			data.putDouble("heat", newCooledTemp);
			if(newCooledTemp >= 10_000_000) // make it hotter
				data.putBoolean("nuke", true); // new update
			if(!meltdown && (meltingPoint != 0)) {
				int timer = data.getInt("generosityTimer");
				int initial = timer;
				if(newCooledTemp >= meltingPoint) { // oh no!
					if(newCooledTemp - heatX >= 0)
						timer = timer - 1;
					else
						timer = Math.min(timer + 20, 90);
					if(timer <= 0) {
						timer = 0;
						data.putBoolean("melting", true);
					}
				} else {
					timer = 90;
					data.putInt("spillage", Math.max(data.getInt("spillage") - 1, 0));
				}
				if(timer != initial)
					data.putInt("generosityTimer", timer);
			}
			if(meltdown && newCooledTemp < meltingPoint) {
				data.putBoolean("melting", false);
				data.putInt("spillage", 0);
			}
			putData(stack, data);
		}
		lastDisableDecay = disableDecay; // ass coding
		return n;
	}

	// ---------------------------------------------------------------------------- the item API

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return getDurabilityForDisplay(stack) > 0;
	}

	/** Upstream's {@code getDurabilityForDisplay}: the fraction of the rod's life that is spent. */
	public double getDurabilityForDisplay(ItemStack stack) {
		CompoundTag data = data(stack);
		if(data != null)
			return Math.min(data.getDouble("depletion") / life, 1);
		return 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F - (float) getDurabilityForDisplay(stack) * 13.0F);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		double green = 255;
		CompoundTag data = data(stack);
		if(data != null)
			green = Math.pow(Mth.clamp(1 - data.getDouble("depletion") / life, 0, 1), 1.25) * 155 + 100;
		return ((int) green) << 8;
	}

	public double getFlux(@Nullable ItemStack stack) {
		if(stack == null)
			return 0;
		if(stack.getItem() instanceof LeafiaRodItem otherRod) {
			double compat = 0.5;
			if(this.splitWithAny || otherRod.splitIntoFast == this.splitWithFast)
				compat = 3;
			compat = compat * otherRod.emission * this.reactivity;
			CompoundTag data = data(stack);
			if(data != null)
				return Math.max(data.getDouble("heat"), 20) * compat;
		}
		return 0;
	}

	public double getFlux(@Nullable ItemStack stack, boolean moderated) {
		if(stack == null)
			return 0;
		if(stack.getItem() instanceof LeafiaRodItem otherRod) {
			double compat = 0.5;
			if(this.splitWithAny || (moderated != this.splitWithFast))
				compat = 3;
			compat = compat * otherRod.emission * this.reactivity;
			CompoundTag data = data(stack);
			if(data != null)
				return Math.max(data.getDouble("heat"), 20) * compat;
		}
		return 0;
	}

	/** The rod this one turns into when its life is spent, resolved the first time it is asked for. */
	@Nullable
	public LeafiaRodItem newFuel() {
		return decayProductBuffer == null ? null : fromResourceMap.get("leafia_rod_" + decayProductBuffer);
	}

	/** What the rod becomes, carrying its heat over with its depletion reset; null while it is not spent or is melting. */
	@Nullable
	public ItemStack getDecayProduct(ItemStack stack) {
		CompoundTag data = data(stack);
		if(data == null)
			return null;
		else {
			if(data.getBoolean("melting")) {
				// TODO: add molten fuel rods
			} else {
				boolean isDepleted = (data.getDouble("depletion") >= life);
				if(isDepleted) {
					LeafiaRodItem newFuel = newFuel();
					if(newFuel != null) {
						CompoundTag newData = data.copy();
						newData.putDouble("depletion", 0);
						ItemStack newStack = new ItemStack(newFuel);
						putData(newStack, newData);
						return newStack;
					}
				}
			}
		}
		return null;
	}

	public boolean decay(ItemStack stack, ItemStackHandler inventory, int slot) {
		ItemStack newFuel = getDecayProduct(stack);
		if(newFuel != null) {
			inventory.setStackInSlot(slot, newFuel);
			return true;
		}
		return false;
	}

	int meltdownFlash = 0;

	public String formatHeatMultiplier(double x) {
		boolean isDiv = false;
		if(x < 1) {
			isDiv = true;
			x = 1 / x;
		}
		x = Math.round(x * 1000) / 1000d;
		String s = Double.toString(x);
		if(s.endsWith(".0"))
			s = s.substring(0, s.length() - 2);
		return (isDiv ? "/" : "×") + s;
	}

	/** The stack the rod is made from, which is what uncrafting it returns. Empty when the rod has no base item. */
	public ItemStack baseStack() {
		Item base = baseItem == null ? null : baseItem.get();
		if(base == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(base);
		if(baseMeta == 1 && base instanceof ItemDepletedFuel) ItemDepletedFuel.setCooling(stack, true);
		return stack;
	}

	/** Whether the shift key is down. Client only; a server never builds a tooltip. */
	private static boolean shiftDown() {
		return FMLEnvironment.dist.isClient() && ClientOnly.shift();
	}

	private static final class ClientOnly {
		static boolean shift() {
			return net.minecraft.client.gui.screens.Screen.hasShiftDown();
		}
	}

	private static void addLines(List<Component> list, String text) {
		for(String line : text.split("\n", -1)) list.add(Component.literal(line));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flagIn) {
		for(String line : intro)
			list.add(Component.literal(ChatFormatting.ITALIC + line));
		LeafiaRodItem item = this;
		CompoundTag data = data(stack);
		double heat = 20;
		double depletion = 0;
		double decay = 0;
		boolean meltdown = false;
		if(data != null) {
			heat = data.getDouble("heat");
			depletion = data.getDouble("depletion");
			meltdown = data.getBoolean("melting");
			decay = data.getDouble("decay");
		}
		if(shiftDown()) {
			addLines(list, ChatFormatting.YELLOW + I18nUtil.resolveKey("item.leafiarod.heatfunc_shift"));
			for(String s : graph)
				addLines(list, "  " + funcColor + s);
		} else {
			Item base = item.baseItem == null ? null : item.baseItem.get();
			if(base != null)
				addLines(list, ChatFormatting.DARK_GRAY + Component.translatable(base.getDescriptionId()).getString()
						+ ((life != 0) ? ("  " + ChatFormatting.DARK_GREEN + "[" + (int) Math.max(Math.ceil((1 - depletion / life) * 100), 0) + "%]") : ""));
			else if(life != 0)
				addLines(list, ChatFormatting.DARK_GREEN + "[" + (int) Math.max(Math.ceil((1 - depletion / life) * 100), 0) + "%]");
			LeafiaRodItem newFuel = newFuel();
			if(newFuel != null)
				addLines(list, ChatFormatting.DARK_GRAY + "  " + I18nUtil.resolveKey("item.leafiarod.decays", newFuel.label));
			if(life != 0) {
				if(!LeafiaConfig.bullshitUnits)
					addLines(list, ChatFormatting.DARK_GREEN + "  " + I18nUtil.resolveKey("item.leafiarod.life", life + "°C"));
				else
					addLines(list, ChatFormatting.DARK_GREEN + "  " + I18nUtil.resolveKey("item.leafiarod.life", BullshitInDavenportIowa.CelsiusToG(life) + "grille"));
			}
			String fnc = item.HeatFunction(stack, false, 0, 0, 0, 0);
			if(!fnc.equals("0") && !lastDisableDecay) {
				if(!LeafiaConfig.bullshitUnits)
					addLines(list, ChatFormatting.GOLD + "  " + I18nUtil.resolveKey("item.leafiarod.decayheat", "+" + String.format("%01.3f", decay * 20) + "°C/s"));
				else
					addLines(list, ChatFormatting.GOLD + "  " + I18nUtil.resolveKey("item.leafiarod.decayheat", "+" + String.format("%01.3f", BullshitInDavenportIowa.PSToPSj(BullshitInDavenportIowa.CelsiusToG(decay * 20))) + "grille/stevejob"));
			}
			if(emission != 1)
				addLines(list, ChatFormatting.AQUA + "  " + I18nUtil.resolveKey("item.leafiarod.reac.out", formatHeatMultiplier(emission)));
			if(reactivity != 1)
				addLines(list, ChatFormatting.AQUA + "  " + I18nUtil.resolveKey("item.leafiarod.reac.in", formatHeatMultiplier(reactivity)));
			if(splitWithAny)
				addLines(list, ChatFormatting.AQUA + "  " + I18nUtil.resolveKey("item.leafiarod.pref.all"));
			else if(splitWithFast)
				addLines(list, ChatFormatting.LIGHT_PURPLE + "  " + I18nUtil.resolveKey("item.leafiarod.pref.fast"));
			if(!splitIntoFast)
				addLines(list, ChatFormatting.AQUA + "  " + I18nUtil.resolveKey("item.leafiarod.moderated"));
			list.add(Component.literal(""));
			addLines(list, ChatFormatting.YELLOW + I18nUtil.resolveKey("item.leafiarod.heatfunc", fnc));
			if(!LeafiaConfig.bullshitUnits)
				addLines(list, ChatFormatting.GOLD + I18nUtil.resolveKey("item.leafiarod.temp", String.format("%01.1f", heat) + "°C"));
			else
				addLines(list, ChatFormatting.GOLD + I18nUtil.resolveKey("item.leafiarod.temp", String.format("%01.1f", BullshitInDavenportIowa.CelsiusToG(heat)) + "grille"));
		}
		if(meltingPoint != 0) {
			if(!LeafiaConfig.bullshitUnits)
				addLines(list, ChatFormatting.DARK_RED + I18nUtil.resolveKey("item.leafiarod.meltingpt", String.format("%01.1f", meltingPoint) + "°C"));
			else
				addLines(list, ChatFormatting.DARK_RED + I18nUtil.resolveKey("item.leafiarod.meltingpt", String.format("%01.1f", BullshitInDavenportIowa.CelsiusToG(meltingPoint)) + "grille"));
			double percent = heat / meltingPoint;
			int barLength = 60;
			String bar = "";
			boolean dark = false;
			double barPercent = (heat >= 0) ? percent : 1 - heat / -273.15;
			for(int i = 0; i < barLength; i++) {
				if((i >= Math.floor(barLength * barPercent)) && !dark) {
					dark = true;
					bar = bar + ((heat >= 0) ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
				}
				bar = bar + "|";
			}
			int status = 0;
			if(heat > 300)
				status = 1;
			if(percent > 0.65)
				status = 2;
			if(heat < 0)
				status = -1;
			if(meltdown)
				status = 3;
			if(functionId.equals("dgomega")) {

				switch(status) {
					case 0:
						list.add(Component.literal(ChatFormatting.RED + "[" + bar + ChatFormatting.RED + "]"));
						list.add(Component.literal(ChatFormatting.RED + "  " + I18nUtil.resolveKey("item.leafiarod.status.1dg")));
						break;
					case 1:
						list.add(Component.literal(ChatFormatting.DARK_RED + "[" + bar + ChatFormatting.DARK_RED + "]"));
						list.add(Component.literal(ChatFormatting.DARK_RED + "  " + I18nUtil.resolveKey("item.leafiarod.status.2dg")));
						break;
					case 2:
						list.add(Component.literal(ChatFormatting.GRAY + "[" + bar + ChatFormatting.GRAY + "]"));
						list.add(Component.literal(ChatFormatting.GRAY + "  " + I18nUtil.resolveKey("item.leafiarod.status.3dg")));
						break;
					case 3:
						list.add(Component.literal(ChatFormatting.GRAY + "[" + bar + ChatFormatting.GRAY + "]"));
						meltdownFlash = Math.floorMod(meltdownFlash + 1, 20);
						list.add(Component.literal((meltdownFlash >= 11) ? "" : ChatFormatting.GRAY + "  " + I18nUtil.resolveKey("item.leafiarod.status.4dg")));
						break;
					case -1:
						list.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + ChatFormatting.DARK_GRAY + bar + ChatFormatting.DARK_GREEN + "]"));
						list.add(Component.literal(ChatFormatting.GREEN + "  " + I18nUtil.resolveKey("item.leafiarod.status.0dg")));
						break;
				}
			} else {
				switch(status) {
					case 0:
						list.add(Component.literal(ChatFormatting.LIGHT_PURPLE + "[" + bar + ChatFormatting.LIGHT_PURPLE + "]"));
						list.add(Component.literal(ChatFormatting.LIGHT_PURPLE + "  " + I18nUtil.resolveKey("item.leafiarod.status.1")));
						break;
					case 1:
						list.add(Component.literal(ChatFormatting.GREEN + "[" + bar + ChatFormatting.GREEN + "]"));
						list.add(Component.literal(ChatFormatting.GREEN + "  " + I18nUtil.resolveKey("item.leafiarod.status.2")));
						break;
					case 2:
						list.add(Component.literal(ChatFormatting.RED + "[" + bar + ChatFormatting.RED + "]"));
						list.add(Component.literal(ChatFormatting.RED + "  " + I18nUtil.resolveKey("item.leafiarod.status.3")));
						break;
					case 3:
						list.add(Component.literal(ChatFormatting.DARK_RED + "[" + bar + ChatFormatting.DARK_RED + "]"));
						meltdownFlash = Math.floorMod(meltdownFlash + 1, 20);
						list.add(Component.literal((meltdownFlash >= 11) ? "" : ChatFormatting.DARK_RED + "  " + I18nUtil.resolveKey("item.leafiarod.status.4")));
						break;
					case -1:
						list.add(Component.literal(ChatFormatting.DARK_AQUA + "[" + ChatFormatting.DARK_GRAY + bar + ChatFormatting.DARK_AQUA + "]"));
						list.add(Component.literal(ChatFormatting.AQUA + "  " + I18nUtil.resolveKey("item.leafiarod.status.0")));
						break;
				}
			}
		}
	}

	/**
	 * Upstream's {@code onUpdate}: every tick, in any inventory, the rod advances with no flux and no
	 * coolant (so it cools toward the room), burns whoever is carrying it above 1500 and 3200 degrees,
	 * sets them alight above 80 unless they hold the reacher or wear asbestos, and turns into its decay
	 * product when its life is spent.
	 */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
		super.inventoryTick(stack, level, entity, itemSlot, isSelected);
		if(level.isClientSide) return;
		this.HeatFunction(stack, true, 0, 0, 0, 0);

		CompoundTag data = data(stack);
		if(data != null) {
			double heat = data.getDouble("heat");
			boolean reacher = false;
			if(entity instanceof Player && !GeneralConfig.enable528 && (heat < 900))
				reacher = Library.checkForHeld((Player) entity, ModItems.reacher.get());
			if(heat >= 1500)
				entity.hurt(level.damageSources().onFire(), 6);
			if(heat >= 3200)
				entity.hurt(level.damageSources().onFire(), 9);
			if((heat >= 80) && !reacher && (!(entity instanceof Player) || !ArmorUtil.checkForAsbestos((Player) entity))) {
				entity.igniteForSeconds(2 + (int) Math.floor(heat / 100));
			}
		}

		ItemStack nextItem = this.getDecayProduct(stack);
		if(nextItem != null)
			entity.getSlot(itemSlot).set(nextItem);
	}

	/** A rod changing temperature in the hand is not a new item: do not make the player re-equip it every tick. */
	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return slotChanged || oldStack.getItem() != newStack.getItem();
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.leafiarod", label);
	}

	/** For updating rods without changing registry name. */
	public LeafiaRodItem overrideLabel(String label) {
		this.label = label;
		return this;
	}

	protected String[] graph = new String[0];
	protected String funcColor = "";

	public LeafiaRodItem(Properties properties, String s, double heatGenerated, double meltingPoint) {
		super(properties.stacksTo(1));
		this.label = s;
		s = s.replace("-", "").replace(" ", "").toLowerCase();
		this.functionId = s;
		s = "leafia_rod_" + s;
		fromResourceMap.put(s, this);

		String fnc = HeatFunction(null, false, 0, 0, 0, 0);
		if(!fnc.equals("0")) {
			graph = LeafiaLib.drawGraph(45, 4, 0, 0, meltingPoint, 0, meltingPoint, (heat) -> {
				HeatFunction(null, false, heat, 0, 0, 0);
				return lastY;
			});
			for(int i = fnc.length() - 1; i >= 0; i--) {
				String sub = fnc.substring(i, Math.min(i + 2, fnc.length()));
				if(sub.startsWith("§")) {
					funcColor = sub;
					break;
				}
			}
		}

		this.life = heatGenerated;
		this.meltingPoint = meltingPoint;

		addRad(emptyRad);
		addModifier(Neutrons.class, genericRodRadModifier);

		detonate(null, null);
	}

	static final MultiRadContainer emptyRad = new MultiRadContainer(0, 0, 0, 0, 0);

	/** The neutron hazard a rod gives off: whatever flux the reactor last told it about, to the 0.65, halved. */
	static final HazardModifier genericRodRadModifier = new HazardModifier() {
		@Override
		public float modify(ItemStack stack, @Nullable LivingEntity holder, float baseLevel) {
			double add = 0;
			if(baseLevel < 0)
				baseLevel = 0;
			CompoundTag nbt = data(stack);
			if(nbt != null) {
				if(nbt.contains("incoming"))
					add = Math.pow(nbt.getDouble("incoming"), 0.65) / 2;
			}
			return (float) (baseLevel + add);
		}
	};

	public LeafiaRodItem setAppearance(Supplier<? extends Item> baseItem, ItemType baseItemType, Purity purity) {
		return setAppearance(baseItem, 0, baseItemType, purity);
	}

	public LeafiaRodItem setAppearance(Supplier<? extends Item> baseItem, int baseMeta, ItemType baseItemType, Purity purity) {
		this.baseItem = baseItem;
		this.baseMeta = baseMeta;
		this.baseItemType = baseItemType;
		this.purity = purity;
		return this;
	}

	public LeafiaRodItem setBaseItem(Supplier<? extends Item> baseItem) { this.baseItem = baseItem; return this; }
	public LeafiaRodItem setEmission(double emission) { this.emission = emission; return this; }
	public LeafiaRodItem setReactivity(double reactivity) { this.reactivity = reactivity; return this; }
	public LeafiaRodItem setSpecialRodModel() { specialRodModel = true; return this; }
	public LeafiaRodItem setModerated() { this.splitIntoFast = false; return this; }
	public LeafiaRodItem setDecayProduct(String funcName) { decayProductBuffer = funcName; return this; }
	public LeafiaRodItem preferFast() { this.splitWithFast = true; return this; }
	public LeafiaRodItem preferAny() { this.splitWithAny = true; return this; }
	public LeafiaRodItem addIntro(String line) { intro.add(line); return this; }

	/**
	 * Names the addon block a melted rod leaves, by registry name. Upstream hands it the block itself
	 * ({@code AddonBlocks.fluid_balecorium}); a block this port has not registered falls back to corium.
	 */
	public LeafiaRodItem setCorium(String block) { this.coriumBlock = block; return this; }

	/**
	 * Upstream's field names for the addon's own coriums, by the registry name they were registered
	 * under: {@code AddonBlocks.fluid_balecorium} is the block {@code balecorium_fluid}.
	 */
	private static String registryNameOf(String coriumBlock) {
		return "fluid_balecorium".equals(coriumBlock) ? "balecorium_fluid" : coriumBlock;
	}

	/** The block this rod melts into: its own if registered, else the base mod's corium. */
	public Block corium() {
		if(coriumBlock != null) {
			Block b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("leafia", registryNameOf(coriumBlock))).orElse(null);
			if(b != null) return b;
		}
		return ModBlocks.block_corium.get();
	}

	/** Whether {@link #setCorium} named a block, registered or not: what upstream would have used. */
	public String coriumName() {
		return coriumBlock == null ? "corium_block" : coriumBlock;
	}

	@Override
	public AddonItemHazardBase addRad(MultiRadContainer container) {
		container = container.multiply(0.5);
		if(container.neutrons == 0)
			container.neutrons = -1;
		return super.addRad(container);
	}

	/** Resolves every rod's decay product now. Upstream does it once the whole table is built; here it is also lazy. */
	public static void confirmDecayProducts() {
		for(LeafiaRodItem item : fromResourceMap.values()) {
			if(item.decayProductBuffer != null && item.newFuel() == null)
				throw new IllegalStateException(item.label + " decays into " + item.decayProductBuffer + ", which is not a rod");
		}
	}

	/** The rod assembly: a rod with nothing in it, what every fuel rod is crafted from and gives back. */
	public static class EmptyLeafiaRod extends Item {

		public EmptyLeafiaRod(Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			return Component.translatable("item.leafiarodempty");
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flagIn) {
			list.add(Component.literal(ChatFormatting.DARK_GRAY + I18nUtil.resolveKey("info.leafiarod.empty")));
		}
	}
}
