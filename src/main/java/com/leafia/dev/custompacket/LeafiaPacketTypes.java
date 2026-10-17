package com.leafia.dev.custompacket;

import java.util.ArrayList;
import java.util.List;

import com.custom_hbm.contents.torex.LCETorex.TorexFinishPacket;
import com.custom_hbm.contents.torex.LCETorex.TorexPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorPacket;
import com.leafia.contents.gear.advisor.AdvisorItem.AdvisorWarningPacket;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem.FuzzyIdentifierPacket;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem.FuzzyIdentifierResponsePacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonClickPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonEnablePacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonInteractPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonModifyPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvButtonSyncPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvInventorySyncPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvSpecialFloorsSyncPacket;
import com.leafia.contents.machines.elevators.car.ElevatorEntity.EvSyncRequestPacket;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioSyncRequestPacket;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioUIPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket.SyncVelocityPacket;
import com.leafia.passive.Wind.WindSyncPacket;
import com.leafia.passive.effects.LeafiaShakecam.ShakecamPacket;

/**
 * Every packet the addon can send, in a fixed order. The index is the id on the wire.
 *
 * <p>Upstream: the {@code CustomPacketType} enum inside {@code LeafiaCustomPacket}, whose comment
 * is <em>"add your packets here, that's literally all registering you have to do!"</em> — one
 * constant per packet, each constructing an instance of a class from somewhere else in the addon.
 *
 * <h2>Why it is a list here and not an enum</h2>
 *
 * Because <b>an enum could not be ported at all</b>. Its forty-one constants instantiate classes
 * from the elevators, the control panel, the modular turbine, the DFC, the wands, the fallout
 * system and eight mixins — of which two are ported. An enum is all-or-nothing: a constant cannot
 * be commented out and re-added later without moving every ordinal after it, and ordinals are
 * the wire ids.
 *
 * <p>A list is appended to as features land, which keeps ids stable for everything already in it.
 * That is the same trade the base mod's {@code SerializableRecipe.registerAllHandlers()} makes and
 * it comes with the same warning: <b>order is load-bearing, so append, never insert.</b>
 *
 * <h2>The count is a protocol version</h2>
 *
 * Upstream packs the type ordinal and the <em>number of types</em> into one short, and a client
 * whose count disagrees with the server's is disconnected rather than allowed to misread every
 * packet. That check is kept exactly (see {@link LeafiaCustomPacket#handle}), and this list being
 * shorter than upstream's is not a problem for it: both sides run this jar, so both count the
 * same. It does mean the ids here are <em>not</em> upstream's, which is fine — nothing outside
 * this jar reads them.
 *
 * <p><b>Registration must not depend on a config switch.</b> A feature that is off still registers
 * its packet type, because a server with elevators on and a client with them off must still agree
 * on what id 7 means — and the count check exists to catch exactly the case where they do not.
 */
public final class LeafiaPacketTypes {

	private LeafiaPacketTypes() {}

	private static final List<LeafiaCustomPacketEncoder> TYPES = new ArrayList<>();

	/**
	 * Upstream's {@code NONE} at ordinal 0: a placeholder rather than a packet, so that a
	 * zero-initialised id does not name a real one.
	 */
	public static final int NONE = register(null);

	public static final int SYNC_VELOCITY = register(new SyncVelocityPacket());

	/**
	 * The wind, broadcast to every client every tick. Upstream's {@code WIND_SYNC}.
	 *
	 * <p><b>Its id here is 2 and upstream's is 27.</b> That is the append-only rule working as
	 * intended rather than a mistake: ids are indices into this list, both ends of the wire are
	 * this build, and reserving twenty-five slots for packets that may never be ported would make
	 * every id a promise nothing keeps. {@code SYNC_VELOCITY} is already in the same position —
	 * upstream's 39, here 1. The order of the lines above is what must never change; agreement
	 * with upstream's ordinals was never available.
	 */
	public static final int WIND_SYNC = register(new WindSyncPacket());

	/*
	 * The elevators' seven, in upstream's own order (its ordinals 11 to 18), minus one: the eighth,
	 * ELEVATOR_BUTTON_MODIFY, sits between ELEVATOR_BUTTON_CLICKED and ELEVATOR_SPECIAL_SYNC
	 * upstream and is registered further down, after the fuzzy identifier's pair, because the
	 * cabin screen it belongs to landed later (D-1077) and the ids are append-only. Nothing about
	 * the wire depends on it being adjacent.
	 */
	public static final int ELEVATOR_BUTTON_SYNC = register(new EvButtonSyncPacket());
	public static final int ELEVATOR_BUTTON_ENABLED_SYNC = register(new EvButtonEnablePacket());
	public static final int ELEVATOR_BUTTON_CLICK_SYNC = register(new EvButtonClickPacket());
	public static final int ELEVATOR_BUTTON_CLICKED = register(new EvButtonInteractPacket());
	public static final int ELEVATOR_SPECIAL_SYNC = register(new EvSpecialFloorsSyncPacket());
	public static final int ELEVATOR_SYNC_REQUEST = register(new EvSyncRequestPacket());
	public static final int ELEVATOR_INVENTORY_SYNC = register(new EvInventorySyncPacket());

	/**
	 * The fuzzy identifier's pair, upstream's {@code FUZZY_IDENTIFIER} and {@code FUZZY_RESPONSE}
	 * (its ordinals 6 and 22; here 10 and 11, per the note on {@code WIND_SYNC}).
	 *
	 * <p><b>{@code FUZZY_IDENTIFIER} is registered although nothing sends it yet</b>, which is the
	 * opposite of the call made for {@code ELEVATOR_BUTTON_MODIFY} above — and the difference is
	 * the one that matters. That packet's <em>class</em> is not ported, so there is nothing to
	 * register; this one's is, its server handler is complete, and what is missing is only its
	 * trigger: a click on a machine GUI's fluid tank, which upstream reaches by a mixin into
	 * {@code com.hbm} and which needs a base-mod hand-off here (D-1053). Registering the half
	 * that works is not a reserved slot, it is a working receiver waiting for a sender.
	 */
	public static final int FUZZY_IDENTIFIER = register(new FuzzyIdentifierPacket());
	public static final int FUZZY_RESPONSE = register(new FuzzyIdentifierResponsePacket());

	/**
	 * The cabin screen's button editor, upstream's {@code ELEVATOR_BUTTON_MODIFY} (its ordinal 13;
	 * here 12). Appended where the screen landed rather than where upstream has it, per the rule
	 * above. Client to server only.
	 */
	public static final int ELEVATOR_BUTTON_MODIFY = register(new EvButtonModifyPacket());

	/**
	 * The advisor's pair, upstream's {@code ADVISOR_WARNING} and {@code ADVISOR} (its ordinals 10
	 * and 20; here 13 and 14). {@code ADVISOR_WARNING} is the server telling the local client one
	 * of five things is hurting the player; {@code ADVISOR} is the screen's whole conversation
	 * with the server, a signal byte in front of whatever that signal carries (D-1080).
	 */
	public static final int ADVISOR_WARNING = register(new AdvisorWarningPacket());
	public static final int ADVISOR = register(new AdvisorPacket());

	/**
	 * The elevator radio's pair, upstream's {@code EV_RADIO_UI} and {@code EV_RADIO_REQUEST} (its
	 * ordinals 30 and 31; here 15 and 16). {@code EV_RADIO_UI} is the three track choices, three
	 * signed bytes, to the client as the answer to a request and to the server as the screen
	 * closes; {@code EV_RADIO_REQUEST} is the screen asking, with no payload (D-1088).
	 */
	public static final int EV_RADIO_UI = register(new EvRadioUIPacket());
	public static final int EV_RADIO_REQUEST = register(new EvRadioSyncRequestPacket());

	/**
	 * The torex's pair and the camera shake's one (D-1089). Upstream registers the torex pair as
	 * {@code LCETorex.TorexPacket} and {@code TorexFinishPacket} through its own packet register and
	 * the shake as {@code CommandLeaf.ShakecamPacket}; none was in {@code CustomPacketType}. Here they
	 * are all three in this list, appended after the radio's (15 and 16), so 17, 18 and 19: {@code TOREX_SPAWN} is the server telling
	 * a client a cloud has begun, {@code TOREX_FINISH} that the explosion it was waiting on has
	 * ended, {@code SHAKECAM} a camera shake in {@code /hbmleaf shake}'s own parameter strings.
	 */
	public static final int TOREX_SPAWN = register(new TorexPacket());
	public static final int TOREX_FINISH = register(new TorexFinishPacket());
	public static final int SHAKECAM = register(new ShakecamPacket());

	/**
	 * Fallout weather and the digamma crater's pair (D-3009), upstream's {@code FALLOUT_SYNC},
	 * {@code DRX_BACKSTAB} and {@code FORCE_SCHIZO}, in that order as upstream has them: the fallout
	 * records to every client in a dimension; the crater's shove from behind, both ways; and schizo mode,
	 * both ways. {@code FORCE_SCHIZO}'s sender ({@code MixinWeaponSpecial}) is not ported, so it is a
	 * receiver waiting for one, as {@code FUZZY_IDENTIFIER} above.
	 */
	public static final int FALLOUT_SYNC = register(new com.leafia.savedata.FalloutSavedData.FalloutSyncPacket());
	public static final int DRX_BACKSTAB = register(new com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.DigammaBackstabPacket());
	public static final int FORCE_SCHIZO = register(new com.leafia.contents.worldgen.biomes.artificial.DigammaCrater.ForceSchizoPacket());

	/**
	 * The addon DFC's pair (D-3011), upstream's {@code DFC_SHOCK} and {@code EXPLOSION_NT}, in upstream's
	 * order: a lightning path out of a core, for its renderer (DFC-4); and an {@code LCEExplosionNT}'s
	 * particles, drawn by the client. Server to client only.
	 */
	public static final int DFC_SHOCK = register(new com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore.DFCShockPacket());
	public static final int EXPLOSION_NT = register(new com.custom_hbm.explosion.LCEExplosionNT.ExplosionNTSyncPacket());

	/**
	 * The Folkvangr's vacuum (D-3013), upstream's {@code FolkvangrVacuumPacket} from its own packet register:
	 * a vacuum has begun, for each client to pull its own player and draw the dust. Its sibling there,
	 * {@code ClearChunkPacket}, is not ported - vanilla's block sync does its job here.
	 */
	public static final int FOLKVANGR_VACUUM = register(new com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr.FolkvangrVacuumPacket());

	/**
	 * The crate labelling screen's text (D-3021), upstream's crate {@code LeafiaPacket}; and the control
	 * upgrade's frequency, upstream's {@code ControlUpgradeFreqPacket} (the {@code CONTROL_UPGRADE} below).
	 */
	public static final int CRATE_LABEL = register(new com.leafia.contents.nonmachines.storage.items.CrateLabelPacket());
	public static final int CONTROL_UPGRADE = register(new com.leafia.contents.control.upgrades.AddonUpgradeItem.ControlUpgradeFreqPacket());

	/**
	 * A block the Tom impact brought down (D-3022), upstream's {@code IMPACT_COLLAPSE}: four ints, for each client to
	 * draw the dust of its fall. Upstream's {@code METEOR_SYNC} is not registered: its only sender is commented out.
	 */
	public static final int IMPACT_COLLAPSE = register(new com.leafia.database.ImpactSeismic.TomImpactCollapsePacket());

	/**
	 * The APR editor's "Invalid Block" boxes (D-3027): upstream sends these as {@code LeafiaDebug.Tracker} packets,
	 * which are a debugger's and not ported. And the laser detonator's pair: the laser drawn for everyone in range,
	 * and the pointing key's request (serverbound, validated: it only ever points the sender's own detonator).
	 */
	public static final int APR_HIGHLIGHT = register(new com.leafia.contents.machines.reactors.apr.blocks.core.APRHighlightPacket());
	public static final int LASER_DETONATOR = register(new com.leafia.overwrite_contents.packets.LaserDetonatorPacket());
	public static final int LASER_POINT_REQUEST = register(new com.leafia.overwrite_contents.interfaces.IMixinItemLaserDetonator.RequestLaserPointPacket());

	/**
	 * The anti-mass rifle's pair (D-3028), upstream's {@code ATTACHED_SOUND} and {@code GET_LOCKON} in upstream's order: a looping
	 * sound that follows an entity (the rifle's fan), server to client; and the lock-on question, an empty request server to client
	 * and the client's answer back, which is validated ({@code acceptsServerbound}, D-1094).
	 */
	public static final int ATTACHED_SOUND = register(new com.leafia.passive.EntityAttachedSounds.AttachedSoundPacket());
	public static final int GET_LOCKON = register(new com.leafia.contents.gear.ILockonWeapon.GetLockonPacket());

	/**
	 * Each dimension's seismic level, to everyone in it every tick (D-3040), upstream's {@code WORLD_GLOBAL_SYNC}
	 * ({@code LeafiaPassiveServer.WorldGlobalSyncPacket}): what the client's camera wobble and the structural integrity
	 * preview read. Sent only while {@code overhauls.impactSeismic} is on, the only time the level can be anything but 0.
	 */
	public static final int WORLD_GLOBAL_SYNC = register(new com.leafia.database.ImpactSeismic.SeismicSyncPacket());

	/**
	 * The rad goggles' box (D-3041): upstream sends a {@code LeafiaDebug.Tracker} {@code SHOW_BOX} packet from
	 * {@code MixinRadiationSystemNT}, which is a debugger's and not ported; this is the same box as a packet of its own.
	 */
	public static final int RAD_HIGHLIGHT = register(new com.leafia.overwrite_contents.packets.RadHighlightPacket());

	// The Custom Control Panel's instrument sync (CCP-INSTRUMENTS-1, D-3071): server to client only, none accepts serverbound.
	public static final int CCP_GRAPH_SYNC = register(new com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph.CCPGraphSyncPacket());
	public static final int CCP_LEVER_SYNC = register(new com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverBase.CCPLeverSyncPacket());
	public static final int CCP_LARGE_SWITCH_SYNC = register(new com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeSwitch.CCPLargeSwitchSyncPacket());

	// The vertical meter's drum angle and the text indicator's glow step (CCP-INSTRUMENTS-2, D-3072): server to client only too.
	public static final int CCP_METER_V_SYNC = register(new com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical.MeterVertical.CCPMeterVerticalSyncPacket());
	public static final int CCP_INDICATOR_SYNC = register(new com.leafia.contents.machines.controlpanel.instruments.types.textindicator.TextIndicator.CCPTextIndicatorSyncPacket());

	/*
	 * The rest of upstream's thirty-nine, each landing with the feature that sends it. Append
	 * below in upstream's own order where it can be followed, and NEVER insert above an existing
	 * line:
	 *
	 *   VISUALIZER_TRACE, HIGHLIGHT_SAVING_SAVE/REMOVE/PRODUCT, WAND_STRUCTURE,
	 *   TURBINE_ERROR, CCP_SWITCH_SYNC,
	 *   REQUEST_POINT
	 */

	private static int register(LeafiaCustomPacketEncoder prototype) {
		TYPES.add(prototype);
		return TYPES.size() - 1;
	}

	/** How many types exist. This is the protocol version on the wire. */
	public static int count() {
		return TYPES.size();
	}

	/** The prototype for an id, or null for {@link #NONE} or an id this build does not have. */
	public static LeafiaCustomPacketEncoder byId(int id) {
		return id < 0 || id >= TYPES.size() ? null : TYPES.get(id);
	}

	/**
	 * The id an encoder sends under.
	 *
	 * <p>Upstream matches by {@code getClass().isInstance(encoder)} against each constant, so a
	 * <em>subclass</em> of a registered packet would match its parent's slot. That is matched here
	 * with {@code isInstance} rather than tightened to an exact class comparison: nothing in
	 * upstream subclasses a packet today, and if something ever does, this port should misbehave
	 * the same way rather than differently.
	 *
	 * @return the id, or -1 when the encoder was never registered.
	 */
	public static int idOf(LeafiaCustomPacketEncoder encoder) {

		for(int i = 0; i < TYPES.size(); i++) {
			LeafiaCustomPacketEncoder prototype = TYPES.get(i);
			if(prototype != null && prototype.getClass().isInstance(encoder)) return i;
		}

		return -1;
	}
}
