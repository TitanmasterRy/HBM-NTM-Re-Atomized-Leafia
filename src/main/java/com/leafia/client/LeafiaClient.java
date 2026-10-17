package com.leafia.client;

import com.leafia.contents.gear.ntmfbottle.ItemNTMFBottle;
import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.inventory.fluid.FluidType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.contents.gear.combat.shields.AddonShieldRender;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierRender;
import com.custom_hbm.sound.LCEAudioProxy;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerRender;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexRender;
import com.leafia.contents.machines.misc.heatex.container.CoolantHeatexScreen;
import com.leafia.contents.machines.processing.mixingvat.MixingVatRenderNeo;
import com.leafia.contents.machines.processing.mixingvat.container.MixingVatScreen;
import com.leafia.contents.machines.heat.hpboiler.container.HPBoilerScreen;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseRender;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlRender;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.RenderPWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalScreen;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisItemRender;
import com.leafia.contents.machines.reactors.pwr.debris.RenderPWRDebris;
import com.leafia.contents.machines.powercores.ams.base.container.AMSBaseScreen;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterRender;
import com.leafia.contents.machines.powercores.ams.emitter.container.AMSEmitterScreen;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerRender;
import com.leafia.contents.machines.powercores.ams.stabilizer.container.AMSStabilizerScreen;
import com.leafia.contents.machines.processing.solblaster.SolBlasterRender;
import com.leafia.contents.machines.processing.solblaster.container.SolBlasterScreen;
import com.leafia.contents.miscellanous.slop.container.SlopScreen;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.RegexFilterScreen;
import com.leafia.contents.building.light.LightRender;
import com.hbm.render.blockentity.RenderDoorGeneric;
import com.leafia.contents.building.doors.renderers.CrimsonDoorLargeRender;
import com.leafia.contents.building.doors.renderers.CrimsonDoorSmallRender;
import com.leafia.contents.building.doors.renderers.ReactorDoorRender;
import com.leafia.contents.building.sign.SignRender;
import com.leafia.contents.machines.elevators.EvBufferRender;
import com.leafia.contents.machines.elevators.EvPulleyRender;
import com.leafia.contents.machines.elevators.car.ElevatorModels;
import com.leafia.contents.machines.elevators.car.ElevatorRender;
import com.leafia.contents.machines.elevators.floors.EvFloorRender;
import com.leafia.contents.machines.elevators.gui.EvCabinScreen;
import com.leafia.contents.building.catwalk.railing.CatwalkRailingBakedModel;
import com.leafia.contents.building.storage.broof.BroofRender;
import com.leafia.contents.building.storage.broof.container.BroofScreen;
import com.leafia.init.LeafiaMenuTypes;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.leafia.contents.machines.elevators.floors.model.EvFloorBakedModel;
import com.leafia.contents.machines.elevators.weight.EvWeightRender;
import com.leafia.init.LeafiaEntities;
import com.leafia.contents.machines.misc.wind_turbines.medium.WindTurbineMediumRender;
import com.leafia.contents.network.fluid.FluidDuctEquipmentRender;
import com.leafia.contents.network.spk_cable.SPKCableRender;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentRender;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineModels;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The addon's client side: which models get baked, and which block entities get a renderer.
 *
 * <p>The base mod splits this in two — {@code ResourceManager} holds the model locations and
 * {@code ClientInitializer} registers the renderers. One class here, because the addon has two
 * renderers; it splits along the base mod's line once that stops being comfortable.
 *
 * <h2>Why the models have to be registered at all</h2>
 *
 * A model is only baked if something references it, and a blockstate is the usual referrer.
 * These are drawn by a <em>renderer</em> rather than chosen by a blockstate, so nothing
 * references them and they would silently bake to the missing-model cube. {@code
 * ModelEvent.RegisterAdditional} is how you say "bake this anyway", and forgetting it is the
 * kind of mistake that looks like a broken renderer rather than a missing registration.
 *
 * <h2>The gate applies here too</h2>
 *
 * Every registration is behind {@link LeafiaFeatures}, for the same reason D-1002 gives for
 * blocks: with the feature off there is no block entity type to attach a renderer to, and the
 * holder is null. Registering unconditionally would throw at startup rather than merely waste
 * a model.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaClient {

	private LeafiaClient() {}

	/**
	 * The fluorescent light's two pieces.
	 *
	 * <p>Upstream draws one loaded OBJ twice with {@code renderPart("Base")} and
	 * {@code renderPart("Bulb")}, binding a texture and setting a colour between the calls.
	 * Geometry is baked by name here rather than drawn by name, so each part is its own model
	 * with the other's visibility switched off — the same translation D-807 records for the
	 * base mod's bombs and the filing cabinet.
	 */
	public static final ModelResourceLocation LIGHT_BASE = standalone("block/light_base");
	public static final ModelResourceLocation LIGHT_BULB = standalone("block/light_bulb");

	/**
	 * The two valves, three parts and two.
	 *
	 * <p>The pipe stub is drawn in the base mod's own {@code pipe_silver} and tinted with the
	 * fluid's colour; the body carries the addon's texture; the handwheel is a third model
	 * because it turns independently of the body. The redstone valve's OBJ has no handwheel at
	 * all, which is why {@link #valveWheel} answers null for it rather than the renderer
	 * comparing blocks the way upstream compares models.
	 */
	public static final ModelResourceLocation VALVE_PIPE = standalone("block/fluid_duct_valve_mdl_pipe");
	public static final ModelResourceLocation VALVE_BODY = standalone("block/fluid_duct_valve_mdl_body");
	public static final ModelResourceLocation VALVE_WHEEL = standalone("block/fluid_duct_valve_mdl_wheel");
	public static final ModelResourceLocation VALVE_RS_PIPE = standalone("block/fluid_duct_valve_mdl_rs_pipe");
	public static final ModelResourceLocation VALVE_RS_BODY = standalone("block/fluid_duct_valve_mdl_rs_body");

	/** The flow gauge's three parts: the tinted pipe, the body, and the needle, which turns on its own. */
	public static final ModelResourceLocation GAUGE_PIPE = standalone("block/fluid_duct_gauge_mdl_pipe");
	public static final ModelResourceLocation GAUGE_BODY = standalone("block/fluid_duct_gauge_mdl_body");
	public static final ModelResourceLocation GAUGE_NEEDLE = standalone("block/fluid_duct_gauge_mdl_needle");

	/**
	 * The medium wind turbine's mast and rotor.
	 *
	 * <p>Third use of the same translation, and the one that shows the tool is wrong about the
	 * other eighty: the dependency checker calls every addon renderer blocked on
	 * {@code LeafiaGls}, {@code ResourceInit} and {@code LeafiaItemRenderer}, and none of the
	 * three needs porting — they are {@code PoseStack}, these two lines, and an item model JSON.
	 * See D-1028.
	 */
	/**
	 * The elevators' OBJ parts.
	 *
	 * <p>One model per (part, texture) pair upstream draws, because {@code renderPart} has no
	 * successor and {@code visibility} in the model JSON is what selects a named object
	 * (D-1040). All of them are generated by a script, and
	 * all of them have to be registered below: nothing references them from a blockstate, so
	 * without that they bake to the missing-model cube.
	 *
	 * <p>{@link #EV_PULLEY_PARTS} is in upstream's own draw order and {@link #EV_PULLEY_ROPE} is
	 * separate from it because the rope is the one part drawn tinted — see
	 * {@code EvPulleyRender}.
	 */
	public static final ModelResourceLocation EV_PULLEY_FLOOR = standalone("block/elevator_pulley_floor");
	public static final ModelResourceLocation EV_PULLEY_INLET = standalone("block/elevator_pulley_inlet");
	public static final ModelResourceLocation EV_PULLEY_MOTOR = standalone("block/elevator_pulley_motor");
	public static final ModelResourceLocation EV_PULLEY_SUPPORT = standalone("block/elevator_pulley_support");
	public static final ModelResourceLocation EV_PULLEY_SHEAVE = standalone("block/elevator_pulley_pulley");
	public static final ModelResourceLocation EV_PULLEY_ROPE = standalone("block/elevator_pulley_rope");

	public static final ModelResourceLocation[] EV_PULLEY_PARTS = {
			EV_PULLEY_FLOOR, EV_PULLEY_INLET, EV_PULLEY_MOTOR, EV_PULLEY_SUPPORT, EV_PULLEY_SHEAVE};

	public static final ModelResourceLocation EV_BUFFER_BASE = standalone("block/elevator_buffer_base");
	public static final ModelResourceLocation EV_BUFFER_MOVE = standalone("block/elevator_buffer_move");
	public static final ModelResourceLocation EV_BUFFER_SCALE = standalone("block/elevator_buffer_scale");

	public static final ModelResourceLocation EV_WEIGHT = standalone("block/elevator_weight_weight");

	/** The broof's one-piece rug. */
	public static final ModelResourceLocation BROOF = standalone("block/broof");

	/** The base mod's goggles model with the radiation goggles' skin (D-3026); drawn by {@code RadGlassesModel}. */
	public static final ModelResourceLocation RAD_GLASSES = standalone("armor/radglasses");

	/**
	 * The catwalk railing's thirty-two parts, read out of the generator's own manifest.
	 *
	 * <p>Derived rather than listed: a generator script writes one model
	 * per named object in the .obj and records the names, so a part added or renamed in Blender
	 * arrives here without an edit. Listing thirty-two names by hand is what D-1041 spent a
	 * batch learning not to do.
	 */
	public static final ModelResourceLocation[] CATWALK_RAILING_PARTS = catwalkParts();

	private static ModelResourceLocation[] catwalkParts() {
		try(java.io.InputStream in = LeafiaClient.class.getResourceAsStream("/assets/leafia/catwalk_models.json")) {
			if(in == null) return new ModelResourceLocation[0];
			com.google.gson.JsonObject root = com.google.gson.JsonParser
					.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
					.getAsJsonObject();
			String block = root.get("block").getAsString();
			java.util.List<ModelResourceLocation> out = new java.util.ArrayList<>();
			root.getAsJsonArray("parts").forEach(p ->
					out.add(standalone("block/" + block + "_" + p.getAsString().toLowerCase())));
			return out.toArray(new ModelResourceLocation[0]);
		} catch(java.io.IOException e) {
			throw new IllegalStateException("could not read the catwalk model manifest", e);
		}
	}

	public static final ModelResourceLocation TURBINE_MEDIUM_BASE = standalone("block/wind_turbine_medium_base");
	public static final ModelResourceLocation TURBINE_MEDIUM_BLADES = standalone("block/wind_turbine_medium_blades");

	/**
	 * The fuzzy identifier's flat sprite, which its own renderer draws.
	 *
	 * <p>The item's real model is {@code builtin/entity} — that is what routes it through
	 * {@link FuzzyIdentifierRender} at all — so the sprite has to live in a second model that
	 * nothing references, and an unreferenced model is not baked unless it is registered here.
	 * Upstream keeps the same pair as its {@code ModelBinding}.
	 */
	public static final ModelResourceLocation FUZZY_BASE = standalone("item/fuzzy_identifier_base");

	/** The high-pressure boiler, drawn whole by {@link HPBoilerRender}. */
	public static final ModelResourceLocation HP_BOILER = standalone("block/hp_boiler");

	/** The RT heater: the base mod's oil-burner OBJ in the addon's texture, drawn whole by {@code HeaterRTGRender} (D-3020). */
	public static final ModelResourceLocation HEATER_RT = standalone("block/heater_rt");

	/** The Fat Chud: the base mod's Fat Man OBJ in the addon's texture, drawn by {@code NukeChudRender} (D-3022). */
	public static final ModelResourceLocation NUKE_CHUD = standalone("block/nuke_chud");

	/** The mixing vat's four parts, drawn by {@link MixingVatRenderNeo} (D-3016). */
	public static final ModelResourceLocation MIXINGVAT_BODY = standalone("block/mixingvat_body");
	public static final ModelResourceLocation MIXINGVAT_FRAME = standalone("block/mixingvat_frame");
	public static final ModelResourceLocation MIXINGVAT_SPINNER = standalone("block/mixingvat_spinner");
	public static final ModelResourceLocation MIXINGVAT_FLUID = standalone("block/mixingvat_fluid");

	/** The coolant heat exchanger's three parts, drawn by {@link CoolantHeatexRender} (D-3016). */
	public static final ModelResourceLocation COOLANT_HEATEX_CONDENSER = standalone("block/coolant_heatex_condenser");
	public static final ModelResourceLocation COOLANT_HEATEX_FAN1 = standalone("block/coolant_heatex_fan1");
	public static final ModelResourceLocation COOLANT_HEATEX_FAN2 = standalone("block/coolant_heatex_fan2");
	/** The salt separator, drawn whole by {@code SaltSeparatorRender} (D-3015). */
	public static final ModelResourceLocation SALT_SEPARATOR = standalone("block/salt_separator");

	/** Upstream's: the overlay material's tint index, and the one the handler answers. */
	public static final int OVERLAY_TINT = 1;

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	/** True when {@code block} is the redstone valve rather than the hand valve. */
	private static boolean isRedstone(Block block) {
		return LeafiaBlocks.fluid_duct_valve_mdl_rs != null
				&& block == LeafiaBlocks.fluid_duct_valve_mdl_rs.get();
	}

	public static ModelResourceLocation valvePipe(Block block) {
		return isRedstone(block) ? VALVE_RS_PIPE : VALVE_PIPE;
	}

	public static ModelResourceLocation valveBody(Block block) {
		return isRedstone(block) ? VALVE_RS_BODY : VALVE_BODY;
	}

	/** Null for the redstone valve, which has no handwheel to turn. */
	public static ModelResourceLocation valveWheel(Block block) {
		return isRedstone(block) ? null : VALVE_WHEEL;
	}

	/**
	 * The two pieces of custom geometry this addon ships: the landing's paint disguise
	 * and the catwalk railing's part composer.
	 *
	 * <p>Registered unconditionally rather than behind {@code features.elevators}: a loader is
	 * named by a model JSON, and a JSON naming a loader that is not registered is a hard model
	 * error rather than a missing model. The blockstates that name it only exist when the
	 * feature is on, so nothing reaches it either way — but an unregistered loader would be a
	 * resource-pack error rather than a quiet absence, which is the wrong failure.
	 */
	@SubscribeEvent
	public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
		event.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "ev_floor_paint"),
				EvFloorBakedModel.Loader.INSTANCE);
		event.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "catwalk_railing"),
				CatwalkRailingBakedModel.Loader.INSTANCE);
	}

	@SubscribeEvent
	public static void registerExtraModels(ModelEvent.RegisterAdditional event) {

		if(LeafiaFeatures.lights) {
			event.register(LIGHT_BASE);
			event.register(LIGHT_BULB);
		}

		if(LeafiaFeatures.turbines) {
			event.register(TURBINE_MEDIUM_BASE);
			event.register(TURBINE_MEDIUM_BLADES);
		}

		if(LeafiaFeatures.elevators) {
			for(ModelResourceLocation part : EV_PULLEY_PARTS) event.register(part);
			event.register(EV_PULLEY_ROPE);
			event.register(EV_BUFFER_BASE);
			event.register(EV_BUFFER_MOVE);
			event.register(EV_BUFFER_SCALE);
			event.register(EV_WEIGHT);

			// The cabin's and the landing's hundred-odd parts. Collected by ElevatorModels
			// rather than listed again here, because a model that is not registered bakes to
			// the missing-model cube and a hundred names cannot be kept in step by hand.
			for(ModelResourceLocation part : ElevatorModels.all()) event.register(part);
		}

		if(LeafiaFeatures.broof) event.register(BROOF);

		if(LeafiaFeatures.radGlasses) event.register(RAD_GLASSES);

		if(LeafiaFeatures.fuzzyIdentifier) event.register(FUZZY_BASE);

		if(LeafiaFeatures.hpBoiler) event.register(HP_BOILER);

		if(LeafiaFeatures.rtg) event.register(HEATER_RT);

		if(LeafiaFeatures.addonBombs) {
			event.register(NUKE_CHUD);
			event.register(com.leafia.contents.bomb.missile.AddonMissileItemRender.NEONC);
		}

		// The older vat the LFTR guide scene draws, a part to a model (D-3029); the scene registers under the same two switches.
		if(LeafiaFeatures.lftrReactor() && LeafiaFeatures.ffDuct)
			for(ModelResourceLocation part : com.leafia.contents.machines.processing.mixingvat.MixingVatRender.all()) event.register(part);

		if(LeafiaFeatures.mixingVatMachine()) {
			event.register(MIXINGVAT_BODY);
			event.register(MIXINGVAT_FRAME);
			event.register(MIXINGVAT_SPINNER);
			event.register(MIXINGVAT_FLUID);
		}

		if(LeafiaFeatures.coolantHeatexMachine()) {
			event.register(COOLANT_HEATEX_CONDENSER);
			event.register(COOLANT_HEATEX_FAN1);
			event.register(COOLANT_HEATEX_FAN2);
		}
		if(LeafiaFeatures.lftrReactor()) event.register(SALT_SEPARATOR);

		// The APR core's two groups (D-3027): its body and one funnel blade, drawn twelve times.
		if(LeafiaFeatures.aprReactor()) {
			event.register(com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreRender.CORE);
			event.register(com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreRender.FUNNEL);
		}

		// The AMC's eight: each machine on its plain texture and its balefire one, and the two that
		// burn out. Drawn whole by their renderers, so nothing else references them.
		if(LeafiaFeatures.ams) {
			event.register(AMSBaseRender.MODEL);
			event.register(AMSBaseRender.MODEL_BALEFIRE);
			event.register(AMSEmitterRender.MODEL);
			event.register(AMSEmitterRender.MODEL_BALEFIRE);
			event.register(AMSEmitterRender.MODEL_DESTROYED);
			event.register(AMSStabilizerRender.MODEL);
			event.register(AMSStabilizerRender.MODEL_BALEFIRE);
			event.register(AMSStabilizerRender.MODEL_DESTROYED);
		}

		if(LeafiaFeatures.amsp) {
			event.register(com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerRender.MODEL);
			event.register(com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverRender.MODEL);
		}

		// The PWR control rod's sixteen parts: two meshes, eight named objects each.
		if(LeafiaFeatures.pwrReactor()) for(ModelResourceLocation part : PWRControlRender.all()) event.register(part);

		// PWR-2's wreck and debris meshes, one model per (mesh, object), drawn retextured (D-3010).
		if(LeafiaFeatures.pwrReactor()) {
			for(ModelResourceLocation part : RenderPWRMeshedWreck.all()) event.register(part);
			for(ModelResourceLocation part : RenderPWRDebris.all()) event.register(part);
		}

		if(LeafiaFeatures.modularTurbine()) ModularTurbineModels.register(event);

		// The DFC absorber's five shrapnel meshes (D-3014).
		if(LeafiaFeatures.dfcOverhaulContent())
			for(ModelResourceLocation part : com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelRender.all()) event.register(part);

		if(LeafiaFeatures.catwalks) {
			// All thirty-two, not the ones some branch mentions: which parts a railing draws is
			// decided per position from a sixteen-bit mask, and an unregistered part would bake
			// to the missing-model cube the first time a player built the shape that needs it.
			for(ModelResourceLocation part : CATWALK_RAILING_PARTS) event.register(part);
		}

		if(LeafiaFeatures.fluidDuctEquipment) {
			event.register(VALVE_PIPE);
			event.register(VALVE_BODY);
			event.register(VALVE_WHEEL);
			event.register(VALVE_RS_PIPE);
			event.register(VALVE_RS_BODY);
			event.register(GAUGE_PIPE);
			event.register(GAUGE_BODY);
			event.register(GAUGE_NEEDLE);
		}

		// The burnt-out chemical plant (D-3025) and the fluid icon's two overlay models (D-3025).
		if(LeafiaFeatures.firestorm) event.register(com.leafia.overwrite_contents.LeafiaFirestormClient.MODEL);
		if(LeafiaFeatures.addonFluids) {
			for(String overlay : com.leafia.unsorted.fluids.FluidIconOverlays.overlays)
				event.register(ModelResourceLocation.standalone(com.leafia.unsorted.fluids.FluidIconOverlays.model(overlay)));
		}

		if(LeafiaFeatures.doors) {
			for(ModelResourceLocation model : ReactorDoorRender.models()) event.register(model);
			for(ModelResourceLocation model : CrimsonDoorSmallRender.INSTANCE.models()) event.register(model);
			for(ModelResourceLocation model : CrimsonDoorLargeRender.INSTANCE.models()) event.register(model);
		}
	}

	/**
	 * The antimatter pipe's second, tinted pass.
	 *
	 * <p>Upstream's {@code AmatDuctBakedModel} draws every face twice — the base sprite untinted,
	 * then {@code pipe_amat_overlay} carrying tint index 1 — and registers this lambda to answer
	 * that index with the fluid the pipe is holding. Both halves are native here: the doubled
	 * quads and the tint index come out of {@code pipe_amat.mtl},
	 * and this is the lambda, unchanged apart
	 * from the API it hangs on. See D-1022.
	 *
	 * <p>No {@code ItemColor} is registered, which is upstream's shape too: an item has no world
	 * or position to read a fluid from, so its overlay draws white and the pipe in your hand is
	 * the empty one.
	 */
	/**
	 * The letter signs' item renderer.
	 *
	 * <p>The <b>first {@code BlockEntityWithoutLevelRenderer} in this project</b>, base mod
	 * included: the sign has no model, so the item has to draw the letter itself, exactly as the
	 * block does. Upstream's counterpart is its own {@code LeafiaItemRenderer} — one of the three
	 * classes D-1028 found are not blockers at all.
	 *
	 * <p>The instance is built lazily and shared: {@code BlockEntityWithoutLevelRenderer}'s
	 * constructor reaches into {@code Minecraft.getInstance()}, so it cannot be made while this
	 * event is being dispatched for the first item.
	 */
	@SubscribeEvent
	public static void registerItemExtensions(RegisterClientExtensionsEvent event) {

		if(LeafiaFeatures.letterSigns) {

			IClientItemExtensions extension = new IClientItemExtensions() {

				private SignRender.SignItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new SignRender.SignItemRender();
					return renderer;
				}
			};

			for(DeferredBlock<?> sign : LeafiaBlocks.letter_signs.values()) {
				event.registerItem(extension, sign.get().asItem());
			}
		}

		if(LeafiaFeatures.doors) {

			// The reactor door in the hand: a block entity renderer's door, drawn by an item renderer.
			// Same lazy instance, same reason as the signs.
			event.registerItem(new IClientItemExtensions() {

				private ReactorDoorRender.ItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new ReactorDoorRender.ItemRender();
					return renderer;
				}
			}, LeafiaBlocks.reactor_door.get().asItem());
		}

		/*
		 * The fuzzy identifier's readout. Same lazy-instance shape and the same reason: the
		 * renderer's constructor reaches into Minecraft.getInstance(), which is not ready while
		 * this event is being dispatched.
		 *
		 * This used to be an early return on !letterSigns, which was right while the signs were
		 * the only entry and would have silently dropped this one.
		 */
		if(LeafiaFeatures.modularTurbine()) {

			// Every turbine part in the hand, drawn by the block entity renderer as upstream's
			// ModularTurbineComponentItemRender did. Same lazy instance, same reason as above.
			IClientItemExtensions turbine = new IClientItemExtensions() {

				private ModularTurbineComponentRender.ItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new ModularTurbineComponentRender.ItemRender();
					return renderer;
				}
			};

			for(DeferredBlock<?> part : LeafiaBlocks.modular_turbine_parts.values()) {
				event.registerItem(turbine, part.get().asItem());
			}
		}

		if(LeafiaFeatures.solBlaster) {

			// The Neutron Blaster in the hand, drawn by its renderer's own parts as upstream's
			// SolBlasterItemRender did. Same lazy instance, same reason as above.
			event.registerItem(new IClientItemExtensions() {

				private SolBlasterRender.ItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new SolBlasterRender.ItemRender();
					return renderer;
				}
			}, LeafiaBlocks.sol_blaster.get().asItem());
		}

		if(LeafiaFeatures.shields) {

			// The four shields in vanilla's shape, and fissite and mysticite, which are plain OBJ item
			// models (D-1083) until they wear a banner: their leafia:decorated override is a builtin/entity
			// model, so a decorated one asks this renderer (D-3031). Same lazy instance, same reason.
			IClientItemExtensions shield = new IClientItemExtensions() {

				private AddonShieldRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new AddonShieldRender();
					return renderer;
				}
			};

			for(Kind kind : Kind.values())
				event.registerItem(shield, LeafiaItems.shields.get(kind).get());
		}

		if(LeafiaFeatures.pwrReactor()) {

			// The PWR's three debris items, drawn as the piece they are (D-3010). Same lazy instance.
			event.registerItem(new IClientItemExtensions() {

				private PWRDebrisItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new PWRDebrisItemRender();
					return renderer;
				}
			}, LeafiaItems.lwr_piece.get(), LeafiaItems.lwr_shrapnel.get(), LeafiaItems.lwr_shard.get());
		}

		if(LeafiaFeatures.addonBombs) {

			// The custom nuke missile, drawn as its neon micro missile and spun in a slot (D-3022).
			event.registerItem(new IClientItemExtensions() {

				private com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItemRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItemRender();
					return renderer;
				}
			}, LeafiaItems.missile_customnuke.get());
		}

		if(LeafiaFeatures.fuzzyIdentifier) {

			event.registerItem(new IClientItemExtensions() {

				private FuzzyIdentifierRender renderer;

				@Override
				public BlockEntityWithoutLevelRenderer getCustomRenderer() {
					if(renderer == null) renderer = new FuzzyIdentifierRender();
					return renderer;
				}
			}, LeafiaItems.fuzzy_identifier.get());
		}
	}

	@SubscribeEvent
	public static void registerColourHandlers(RegisterColorHandlersEvent.Block event) {

		/*
		 * The Forge-fluid duct (D-3012): upstream's FFDuctStandard.registerColorHandler, the carried
		 * fluid's colour on tint index 1 and white otherwise, for its OBJ's overlay material.
		 * Upstream registers no item colour, so the item's
		 * overlay stays white, as here.
		 */
		if(LeafiaFeatures.ffDuct)
			event.register((state, level, pos, tintIndex) -> {
				if(tintIndex != OVERLAY_TINT || level == null || pos == null) return 0xFFFFFF;
				if(!(level.getBlockEntity(pos) instanceof com.leafia.contents.network.ff_duct.FFDuctTE pipe)) return 0xFFFFFF;
				FluidType type = pipe.getFluidType();
				return type == null ? 0xFFFFFF : type.getColor();
			}, LeafiaBlocks.ff_duct.get());

		/*
		 * The duct utilities (D-3019): upstream's renderer sets the colour before each part. Tint 1 is the fluid's colour
		 * with each channel times 240/255 (its `max`) - the pipes - and tint 2 the box, a grey that is dark (0.4) when the
		 * fluid's channels average over a half and light (0.65) otherwise. The arrow has no tint. A utility whose fluid is
		 * not known yet is NONE's colour, as upstream's.
		 */
		if(LeafiaFeatures.ffDuct)
			event.register((state, level, pos, tintIndex) -> {
				if(level == null || pos == null) return 0xFFFFFF;
				if(!(level.getBlockEntity(pos) instanceof com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase util)) return 0xFFFFFF;
				return com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase.tint(util.getFluidType().getColor(), tintIndex);
			}, LeafiaBlocks.ff_pump.get(), LeafiaBlocks.ff_converter.get(), LeafiaBlocks.ff_filter.get());

		if(!LeafiaFeatures.amatPipes) return;

		event.register((state, level, pos, tintIndex) -> {

			if(tintIndex != OVERLAY_TINT || level == null || pos == null) return 0xFFFFFF;
			if(!(level.getBlockEntity(pos) instanceof BlockEntityPipeBaseNT pipe)) return 0xFFFFFF;

			FluidType type = pipe.getFluidType();
			return type == null ? 0xFFFFFF : type.getColor();

		}, LeafiaBlocks.amat_duct.get());
	}

	/**
	 * The advisor's open cover. Upstream's {@code AdvisorRender} read the stack's {@code open} tag
	 * in code; here the item model has an override on a property of that name, and this is what
	 * answers it. The model JSON is generated by a script (D-1080).
	 */
	@SubscribeEvent
	public static void clientSetup(FMLClientSetupEvent event) {
		/*
		 * The client audio proxy (D-1088). Not behind a feature switch: it registers nothing, it
		 * is the hook every looping addon sound is asked for through, and with no client it is
		 * empty and those requests answer with a wrapper that does nothing.
		 */
		LCEAudioProxy.Client.install();
		// The F.L.E.I.J.A. clouds' renderers, particles and the Folkvangr's vacuum (D-3013); each reads its switch.
		com.leafia.contents.effects.folkvangr.visual.FolkvangrClient.install();
		// The RBMK overhaul's column render hook and console screen hook (D-3018); both answer from server data.
		com.leafia.contents.machines.reactors.rbmk.LeafiaRBMKClient.install();
		// The burnt-out chemical plant's render hook (D-3025); it answers from the plant's synced extra tag.
		com.leafia.overwrite_contents.LeafiaFirestormClient.install();
		/*
		 * The LFTR salt's lines in every Forge-fluid tooltip and look overlay (D-3012): its heat raises the
		 * shown temperature, and its fuel mixture follows the traits. Upstream's addFluidInfo branch for an
		 * FT_LFTRCoolant fluid, hung on the bridge's hook rather than written into it.
		 */
		if(LeafiaFeatures.lftrReactor())
			LeafiaFFClient.INFO_HOOKS.add(new com.leafia.contents.machines.reactors.lftr.components.MSRSaltInfo());
		// The "coneBurst" spark effect TauSpark hands its keys to (D-3031): NTM-CE's, so the addon's, under its own name.
		event.enqueueWork(com.leafia.client.particle.LeafiaSparkCreator::install);
		// An enum socket of a control panel node gets a dropdown on the client only (CCP-NODE-EDITOR, D-3067).
		if(LeafiaFeatures.controlPanel)
			event.enqueueWork(() -> com.leafia.contents.machines.controlpanel.core.NodeConnection.enumSelectorFactory =
					com.leafia.contents.machines.controlpanel.core.NodeConnection::newEnumSelector);
		// The PWR and LFTR guide topics (D-3029), into the base mod's table once the items exist.
		event.enqueueWork(com.leafia.contents.cannery.AddonJars::initJars);
		if(LeafiaFeatures.shields)
			event.enqueueWork(com.leafia.contents.gear.combat.shields.AddonShieldProperties::register);
		// The addon's bobblehead figure wears its own skin instead of the Vault Boy's (D-3041).
		event.enqueueWork(() -> com.hbm.render.blockentity.RenderBobble.setSkin(
				com.leafia.overwrite_contents.LeafiaBobbleheads.leafia(), com.leafia.overwrite_contents.LeafiaBobbleheads.skin()));
		if(LeafiaFeatures.advisor)
			event.enqueueWork(() -> ItemProperties.register(LeafiaItems.advisor.get(), LeafiaRef.rl("open"),
					(stack, level, entity, seed) -> AdvisorItem.isOpen(stack) ? 1F : 0F));
	}

	/** The fluid bottle's overlay takes the fluid's colour (D-1066). */
	@SubscribeEvent
	public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
		if(LeafiaFeatures.fluidBottles)
			event.register(ItemNTMFBottle::getColor, LeafiaItems.ntmfbottle.get());
		// The fuel rods' glow, and the cooling tint on the five depleted fuels (D-3001).
		if(LeafiaFeatures.rods)
			com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodRender.registerColours(event);
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {

		if(LeafiaFeatures.lights) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.LIGHT.get(), LightRender::new);
		}

		// The black hole and the render test (D-3031).
		if(LeafiaFeatures.debugBlocks) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DEBUG_BLACKHOLE.get(), com.leafia.contents.debug.blackhole_test.DebugBHRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DEBUG_RENDER_TEST.get(), com.leafia.contents.debug.render_test.DebugRenderTestRender::new);
		}

		if(LeafiaFeatures.spkCable) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.SPK_CABLE.get(), SPKCableRender::new);
		}

		if(LeafiaFeatures.fluidDuctEquipment) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.FLUID_DUCT_VALVE.get(), FluidDuctEquipmentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.FLUID_DUCT_VALVE_RS.get(), FluidDuctEquipmentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.FLUID_DUCT_GAUGE.get(), FluidDuctEquipmentRender::new);
		}

		if(LeafiaFeatures.doors) {
			// The base mod's door renderer, which dispatches to each decl's own IRenderDoors.
			event.registerBlockEntityRenderer(LeafiaBlockEntities.ADDON_DOOR.get(), RenderDoorGeneric::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.REACTOR_DOOR.get(), RenderDoorGeneric::new);
		}

		if(LeafiaFeatures.turbines) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.WIND_TURBINE_MEDIUM.get(), WindTurbineMediumRender::new);
		}

		if(LeafiaFeatures.letterSigns) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.LETTER_SIGN.get(), SignRender::new);
		}

		if(LeafiaFeatures.broof) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.BROOF.get(), BroofRender::new);
		}

		if(LeafiaFeatures.rack) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.RACK.get(), com.leafia.contents.building.storage.rack.RackRender::new);
		}

		// The base mod's four crates have no renderer of their own; the labels are drawn by the addon's (D-3021).
		if(LeafiaFeatures.crateLabels) {
			event.registerBlockEntityRenderer(com.hbm.slices.Slice1.CRATE_IRON.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelRender::new);
			event.registerBlockEntityRenderer(com.hbm.slices.Slice1.CRATE_STEEL.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelRender::new);
			event.registerBlockEntityRenderer(com.hbm.slices.Slice1.CRATE_DESH.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelRender::new);
			event.registerBlockEntityRenderer(com.hbm.slices.Slice1.CRATE_TUNGSTEN.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelRender::new);
			if(LeafiaBlockEntities.CRATE_TUNGSTEN != null)
				event.registerBlockEntityRenderer(LeafiaBlockEntities.CRATE_TUNGSTEN.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelRender::new);
		}

		if(LeafiaFeatures.hpBoiler) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.HP_BOILER.get(), HPBoilerRender::new);
		}

		if(LeafiaFeatures.rtg) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.HEATER_RT.get(),
					com.leafia.contents.machines.heat.rtheater.HeaterRTGRender::new);
		}

		if(LeafiaFeatures.solBlaster) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.SOL_BLASTER.get(), SolBlasterRender::new);
		}

		if(LeafiaFeatures.mixingVatMachine()) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.MIXING_VAT.get(), MixingVatRenderNeo::new);
		}

		if(LeafiaFeatures.coolantHeatexMachine()) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.COOLANT_HEATEX.get(), CoolantHeatexRender::new);
		}

		if(LeafiaFeatures.aprReactor())
			event.registerBlockEntityRenderer(LeafiaBlockEntities.APR_CORE.get(), com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreRender::new);
		if(LeafiaFeatures.ams) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.AMS_BASE.get(), AMSBaseRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.AMS_EMITTER.get(), AMSEmitterRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.AMS_STABILIZER.get(), AMSStabilizerRender::new);
		}

		if(LeafiaFeatures.amsp) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.AMSP_ANALYZER.get(),
					com.leafia.contents.machines.research.amsp.analyzer.AMSPAnalyzerRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.AMSP_RECEIVER.get(),
					com.leafia.contents.machines.research.amsp.receiver.AMSPReceiverRender::new);
		}

		if(LeafiaFeatures.lftrReactor()) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.MSR_ARBITRARY.get(),
					com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.SALT_SEPARATOR.get(),
					com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorRender::new);
		}

		if(LeafiaFeatures.pwrReactor()) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.PWR_CONTROL.get(), PWRControlRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.PWR_WRECK.get(), RenderPWRMeshedWreck::new);
			event.registerEntityRenderer(LeafiaEntities.PWR_DEBRIS.get(), RenderPWRDebris::new);
		}

		if(LeafiaFeatures.addonBombs) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.NUKE_CHUD.get(), com.leafia.contents.bomb.chud.NukeChudRender::new);
			event.registerEntityRenderer(LeafiaEntities.CUSTOM_NUKE_MISSILE.get(),
					com.leafia.contents.bomb.missile.customnuke.entity.CustomNukeMissileEntityRender::new);
		}

		if(LeafiaFeatures.dfcOverhaulContent()) {
			event.registerEntityRenderer(LeafiaEntities.DFC_SHRAPNEL.get(), com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelRender::new);

			// DFC-4 (D-3017): the core, and the seven parts - one renderer, which picks mesh and texture by block entity.
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_CORE.get(), com.leafia.contents.machines.powercores.dfc.render.DFCCoreRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_EMITTER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_RECEIVER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_INJECTOR.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_STABILIZER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_CEMITTER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_EXCHANGER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.DFC_PULSER.get(), com.leafia.contents.machines.powercores.dfc.render.DFCComponentRender::new);
		}

		// Folkvangr draws nothing itself: the cloud bound to it does (D-3013).
		// The pink door's rainbow beams (D-3031).
		if(LeafiaEntities.RAINBOW != null)
			event.registerEntityRenderer(LeafiaEntities.RAINBOW.get(), com.leafia.contents.effects.zomg.RenderRainbow::new);
		if(LeafiaEntities.NUKE_LCA != null)
			event.registerEntityRenderer(LeafiaEntities.NUKE_LCA.get(), com.leafia.contents.effects.RenderNothing.factory());
		if(LeafiaEntities.NUKE_FOLKVANGR != null)
			event.registerEntityRenderer(LeafiaEntities.NUKE_FOLKVANGR.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);

		if(LeafiaFeatures.modularTurbine()) {
			// Every turbine part but the core, whichever of the three types it carries.
			event.registerBlockEntityRenderer(LeafiaBlockEntities.MODULAR_TURBINE_COMPONENT.get(), ModularTurbineComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.MODULAR_TURBINE_PORT.get(), ModularTurbineComponentRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.MODULAR_TURBINE_FLYWHEEL.get(), ModularTurbineComponentRender::new);
		}

		if(LeafiaFeatures.elevators) {
			event.registerBlockEntityRenderer(LeafiaBlockEntities.EV_PULLEY.get(), EvPulleyRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.EV_BUFFER.get(), EvBufferRender::new);
			event.registerEntityRenderer(LeafiaEntities.ELEVATOR_WEIGHT.get(), EvWeightRender::new);
			event.registerEntityRenderer(LeafiaEntities.ELEVATOR.get(), ElevatorRender::new);
			event.registerBlockEntityRenderer(LeafiaBlockEntities.EV_FLOOR.get(), EvFloorRender::new);
		}
	}

	/**
	 * Screens, against the menu types they draw.
	 *
	 * <p>The addon's first. 1.12.2 had the block entity hand back a {@code GuiScreen} every
	 * time one was opened; here the pairing is registered once, which is why
	 * {@code BroofTE.provideGUI} has no successor at all.
	 *
	 * <p>On NeoForge's {@code RegisterMenuScreensEvent} rather than {@code MenuScreens.register}
	 * in client setup: the vanilla method is private in 1.21.1, and the event is what the base
	 * mod's slices use.
	 */
	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent event) {
		if(LeafiaFeatures.crateLabels)
			event.register(LeafiaMenuTypes.CRATE_LABEL.get(), com.leafia.contents.nonmachines.storage.items.CrateLabelScreen::new);
		// Registered whatever the switch says, as the menu type is (D-3022): the custom nuke missile's slots.
		event.register(LeafiaMenuTypes.CUSTOM_NUKE_MISSILE.get(), com.leafia.contents.bomb.missile.customnuke.container.CustomNukeMissileUI::new);
		// The Custom Control Panel's editor (D-3066).
		if(LeafiaFeatures.controlPanel)
			event.register(LeafiaMenuTypes.CONTROL_EDIT.get(), com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen::new);
		if(LeafiaFeatures.broof)
			event.register(LeafiaMenuTypes.BROOF.get(), BroofScreen::new);
		if(LeafiaFeatures.hpBoiler)
			event.register(LeafiaMenuTypes.HP_BOILER.get(), HPBoilerScreen::new);
		// D-3020. The furnace's screen is the base mod's with the addon's sheet, so it is registered on the base menu's class.
		if(LeafiaFeatures.rtg)
			event.register(LeafiaMenuTypes.HEATER_RT.get(), com.leafia.contents.machines.heat.rtheater.container.HeaterRTGScreen::new);
		if(LeafiaFeatures.zirnoxOverhaulContent())
			event.register(LeafiaMenuTypes.ZIRNOX.get(), com.leafia.contents.machines.reactors.zirnox.ZirnoxScreen::new);
		if(LeafiaFeatures.rtgFurnaceOverhaulContent())
			event.<com.hbm.inventory.container.ContainerMachineDiFurnaceRTG, com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGScreen>register(
					LeafiaMenuTypes.DIFURNACE_RTG.get(), com.leafia.overwrite_contents.difurnace.LeafiaDiFurnaceRTGScreen::new);
		if(LeafiaFeatures.lftrReactor())
			event.register(LeafiaMenuTypes.SALT_SEPARATOR.get(),
					com.leafia.contents.machines.reactors.lftr.processing.separator.container.SaltSeparatorScreen::new);
		if(LeafiaFeatures.slopReactor)
			event.register(LeafiaMenuTypes.SLOP.get(), SlopScreen::new);
		if(LeafiaFeatures.solBlaster)
			event.register(LeafiaMenuTypes.SOL_BLASTER.get(), SolBlasterScreen::new);
		if(LeafiaFeatures.mixingVatMachine())
			event.register(LeafiaMenuTypes.MIXING_VAT.get(), MixingVatScreen::new);
		if(LeafiaFeatures.coolantHeatexMachine())
			event.register(LeafiaMenuTypes.COOLANT_HEATEX.get(), CoolantHeatexScreen::new);
		if(LeafiaFeatures.aprReactor())
			event.register(LeafiaMenuTypes.APR_MAIN.get(), com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMainUI::new);
		if(LeafiaFeatures.ams) {
			event.register(LeafiaMenuTypes.AMS_BASE.get(), AMSBaseScreen::new);
			event.register(LeafiaMenuTypes.AMS_EMITTER.get(), AMSEmitterScreen::new);
			event.register(LeafiaMenuTypes.AMS_STABILIZER.get(), AMSStabilizerScreen::new);
		}
		if(LeafiaFeatures.dfcOverhaulContent()) {
			event.register(LeafiaMenuTypes.DFC_CORE.get(), com.leafia.contents.machines.powercores.dfc.core.CoreScreen::new);
			event.register(LeafiaMenuTypes.DFC_PULSER.get(), com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorScreen::new);
			event.register(LeafiaMenuTypes.DFC_RECEIVER.get(), com.leafia.contents.machines.powercores.dfc.components.absorber.CoreReceiverScreen::new);
			event.register(LeafiaMenuTypes.DFC_INJECTOR.get(), com.leafia.contents.machines.powercores.dfc.components.injector.CoreInjectorScreen::new);
			event.register(LeafiaMenuTypes.DFC_CEMITTER.get(), com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterScreen::new);
			event.register(LeafiaMenuTypes.DFC_EXCHANGER.get(), com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerScreen::new);
		}
		if(LeafiaFeatures.elevators)
			event.register(LeafiaMenuTypes.EV_CABIN.get(), EvCabinScreen::new);
		if(LeafiaFeatures.regexFilter)
			event.register(LeafiaMenuTypes.REGEX_FILTER.get(), RegexFilterScreen::new);
		if(LeafiaFeatures.pwrReactor())
			event.register(LeafiaMenuTypes.PWR_TERMINAL.get(), PWRTerminalScreen::new);
	}
}
