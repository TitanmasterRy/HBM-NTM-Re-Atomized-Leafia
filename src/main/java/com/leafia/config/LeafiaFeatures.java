package com.leafia.config;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Which parts of the addon exist at all. A <b>STARTUP</b> config, and that is load-bearing.
 *
 * <h2>Why this is not a COMMON config</h2>
 *
 * D-1002 says a disabled feature must register <em>nothing</em> — no block, no
 * item, no BlockEntityType, no creative-tab entry — because a registered no-op still fills the
 * creative tab and still persists in any world that ever loaded it. Honouring that means the
 * switch has to be readable while {@code DeferredRegister} is registering.
 *
 * <p>A COMMON config is not. Read out of {@code CommonModLoader.begin} in NeoForge 21.1.248,
 * the order is:
 *
 * <ol>
 *   <li>{@code "Registry initialization"} → {@code GameData.postRegisterEvents()}</li>
 *   <li>{@code "Config loading"} → {@code loadConfigs(CLIENT)} then {@code loadConfigs(COMMON)}</li>
 * </ol>
 *
 * Registration happens <em>first</em>. A COMMON-backed gate would silently read its own
 * defaults during registration and the player's file only afterwards — so the switch would
 * appear to work in the config screen and do nothing on disk. STARTUP is the one type
 * {@code ConfigTracker.registerConfig} opens synchronously, right there in the call, which is
 * why it is the only type that can gate registration. See D-1003.
 *
 * <p>{@link #refresh()} is therefore called from the mod constructor immediately after
 * {@code registerConfig}, not from a {@code ModConfigEvent} listener.
 *
 * <h2>What a STARTUP config costs</h2>
 *
 * It is not synced to clients and it is not per-world. A server and a client running different
 * feature switches will disagree about which blocks exist — the same class of mismatch as
 * running different mod versions, and with the same remedy: match the files. That is an
 * acceptable trade for a switch whose whole job is deciding what gets registered, but it is
 * the reason <em>balance</em> numbers live in {@link LeafiaConfig} on a COMMON spec instead.
 *
 * <h2>Defaults</h2>
 *
 * <b>On</b> for additive content, <b>off</b> for anything that changes base-mod behaviour.
 * A player who installs the jar gets the addon's content; they do not silently get its
 * overhauls.
 */
public final class LeafiaFeatures {

	private LeafiaFeatures() {}

	/** Elevators — shaft, pulley, buffer, car, floors. Additive. (L2) */
	public static boolean elevators = true;
	/** Electromagnetic fluid duct, charger and flow gauges. Additive. (L3) */
	public static boolean ffDuct = true;
	/** Modular turbine, wind turbines, heat exchanger. Additive. (L4) */
	public static boolean turbines = true;

	/**
	 * Whether the <em>modular</em> turbine registers: {@link #turbines} <b>and</b>
	 * {@link #addonFluids}. Not a switch of its own — a derived gate.
	 *
	 * <p>The turbine's steam table starts at deathsteam, which is one of the addon's fluids, and
	 * that table is balance: a stage's buffer is sized by its steam's <em>index</em> in it, so a
	 * table missing its first entry would quietly shrink every buffer by 2.5x. Upstream always has
	 * both, so rather than run a turbine on a table upstream never shipped, it does not register
	 * without the fluids. The wind turbine has no such dependency and stays on {@code turbines}
	 * alone. See D-1058.
	 */
	public static boolean modularTurbine() {
		return turbines && addonFluids;
	}

	/**
	 * Whether the Forge-fluid bridge exists: {@link #ffDuct} <b>or</b> {@link #lftr}. Not a switch
	 * of its own - a derived gate, like {@link #modularTurbine()}.
	 *
	 * <p>The bridge ({@code com.leafia.contents.fluids.ff.LeafiaFF}) registers a NeoForge fluid at
	 * {@code leafia:ff/<name>} for every NTM fluid with no world fluid of its own, and those
	 * registrations are what a disabled feature must not leave behind (D-1002). Upstream's
	 * consumers are the FF duct network and its pump, filter and converter ({@code ffDuct}) and
	 * the thorium reactor, whose salt moves in Forge tanks ({@code lftr}, and {@link #lftrReactor()} since D-3012). A later batch whose
	 * machine keeps a Forge tank on a switch of its own adds that switch here. See D-3003.
	 */
	public static boolean ffBridge() {
		return ffDuct || lftr || lftrReactor() || mixingVatMachine() || coolantHeatexMachine();
	}
	/**
	 * The antimatter pipe and its charger: a fluid pipe that has to be held by a magnetic
	 * field, on its own network over the base mod's nodespace. Additive — it adds blocks and
	 * changes nothing the base mod already does. (L3)
	 */
	public static boolean amatPipes = true;
	/**
	 * The DFC Redirection Cable and its SPK network. Additive - it adds one block and a network
	 * nothing in the base mod can join. Upstream gates it behind {@code !disableAddonDFC}, which is
	 * {@link #dfcOverhaul} (default off) here; it has its own switch because the cable by itself
	 * overhauls nothing, and the parts that will plug into it are what the overhaul switch gates.
	 * (L3, D-3008)
	 */
	public static boolean spkCable = true;
	/**
	 * In-line equipment for the <em>base mod's</em> fluid ducts: the hand valve, the redstone
	 * valve and the flow gauge. Additive — it adds blocks that attach to base-mod
	 * pipes and changes nothing about the pipes themselves. (L3)
	 */
	public static boolean fluidDuctEquipment = true;
	/** The fluorescent light: a three-wide powered ceiling fixture. Additive. (L3) */
	public static boolean lights = true;
	/**
	 * The fourteen fluids the addon adds — FLIBE, the two enriched UF6s, hot water and air,
	 * radspice slop, malfunctioning coolant, deathsteam, HF, N2O, fluorine, pyrogel,
	 * cryogenic interfluid and oxygen gas (D-1068).
	 *
	 * <p>Additive, so on: these are <em>new</em> fluids at ids 1121 upward, nowhere near the base
	 * mod's, and every base-mod fluid keeps the id it had. What is <b>not</b> on this switch is
	 * upstream's one line that changes a base-mod fluid — {@code COOLANT_HOT}'s temperature —
	 * which lives with the heat-step chain under {@link #fluidTraits}. See D-1023. (L3)
	 */
	public static boolean addonFluids = true;

	/**
	 * Upstream's nine debug blocks: the fluid voider (D-1015), and since D-3031 the other eight - the Forge-fluid source
	 * and tank, the boom, the RBMK jet, the black hole, the render test, the Tom test and the circle test.
	 *
	 * <p><b>On</b>, because upstream registers them unconditionally: {@code AddonBlocks} holds
	 * them in a nested {@code TestBlocks} class and then forces that class to initialise with a
	 * dummy static field, so they are always in the creative tab. This switch exists because
	 * this port gates every feature, not because upstream offers the choice — and it defaults to
	 * upstream's behaviour rather than to a tidier one.
	 */
	public static boolean debugBlocks = true;

	/**
	 * The debug Forge-fluid source and tank (D-3031): {@link #debugBlocks}, and the FLiBe they carry with the bridge that
	 * moves it - the addon's fluids and {@link #ffBridge()}. Without those they would be blocks that do nothing.
	 */
	public static boolean debugFFBlocks() {
		return debugBlocks && addonFluids && ffBridge();
	}

	/** The circle test (D-3031): {@link #debugBlocks}, and the APR, whose blocks the torus it builds is made of. */
	public static boolean debugCircle() {
		return debugBlocks && aprReactor();
	}

	/**
	 * The wind: a strength and a compass angle drifting on their own timers, saved with the world
	 * and broadcast to every client each tick. Additive, so on — it changes nothing the base mod
	 * does, and nothing reads it yet.
	 *
	 * <p>The switch gates the <em>ticking</em>, not the packet type: an id in
	 * {@link com.leafia.dev.custompacket.LeafiaPacketTypes} is an index, so skipping one would
	 * renumber every packet after it (D-1024). With the wind off the simulation stands still at
	 * whatever the world last saved.
	 */
	public static boolean wind = true;

	/**
	 * The thirty-six letter signs: one wall-mounted block per character of
	 * {@code 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ}, dyeable. Purely decorative and purely
	 * additive, so on.
	 */
	public static boolean letterSigns = true;

	/**
	 * The catwalk railing: a thin rail along any of a block's four edges, joining into runs and
	 * corners by itself. Decoration, and purely additive, so on.
	 *
	 * <p>Upstream has no switch for it — {@code AddonBlocks.Railings} registers it
	 * unconditionally. One is added here because every other content family in this jar has one
	 * and a player who wants only the elevators should be able to say so.
	 */
	public static boolean catwalks = true;

	/**
	 * The fifty-five lined asphalts: road markings you paint onto asphalt with a white dye.
	 * Decoration, purely additive, so on.
	 *
	 * <p>The switch also gates {@code AsphaltPainting}, the listener that lets the base mod's
	 * own asphalt be painted. With the feature off that listener returns immediately, so a
	 * player who turns it off gets the base mod's asphalt behaving exactly as it does without
	 * this jar installed.
	 */
	public static boolean linedAsphalt = true;

	/**
	 * The broof: a green canopy you walk through, with fifty-four slots in it. Decoration and storage,
	 * purely additive, so on.
	 *
	 * <p>Upstream has no switch for it. One is added here for the same reason the catwalks and
	 * the lined asphalts have one, and because the broof is the addon's only block so far that
	 * a player could lose items in — a switch that hides it is a switch that should be set
	 * before a world is built, not after.
	 */
	public static boolean broof = true;

	/**
	 * Labels on the base mod's four crates (D-3021): sneak-click a side with a screwdriver and write on it
	 * - three lines, a vertical line for each of the four turns of the top and bottom, and an icon item.
	 * Additive, so on: an unlabelled crate saves and draws exactly as before. Off also removes the
	 * labelling screen and menu, the crate renderer and the crate tooltip line.
	 */
	public static boolean crateLabels = true;

	/**
	 * The storage rack: a 3x2x3 shelf holding up to four crates, which you open from the shelf. Additive, so on.
	 * Upstream has no switch; one is added for the reasons the broof's gives.
	 */
	public static boolean rack = true;

	/**
	 * The control upgrade: an upgrade item that names a radio channel, and caps every other upgrade in
	 * the same machine at the number it hears there (D-3021). Additive, so on, although a machine with one in
	 * its upgrade slots and no listener on the channel runs with no upgrades at all - as upstream's.
	 */
	public static boolean upgradeControl = true;

	/**
	 * The 231 mixed concretes: every pair of upstream's 22 concretes, split down the middle so a
	 * seam between two colours runs straight. Decoration, purely additive, so on.
	 *
	 * <p>The largest block family in this jar by a wide margin. The switch is worth having for
	 * that reason alone — a player who wants the elevators and not 231 creative-menu entries
	 * should be able to say so.
	 */
	public static boolean mixedConcrete = true;

	/**
	 * The fuzzy identifier: a hand tool that remembers one fluid and stamps it over a whole run
	 * of the base mod's fluid ducts in one click.
	 *
	 * <p>Additive, so on. It adds an item and changes nothing the base mod does — and it is the
	 * first thing in the project to implement {@code IItemFluidIdentifier}, so with it installed
	 * five base-mod machines' fluid-ID slots start working and without it they stay as inert as
	 * they have been since they landed. That is an addition, not an overhaul: the slots are the
	 * base mod's and they already refuse everything else.
	 */
	public static boolean fuzzyIdentifier = true;

	/**
	 * The high-pressure boiler: one block that runs a fluid up several {@code FT_Heatable} steps
	 * at once, for less heat than the same rungs would cost as separate machines.
	 *
	 * <p>Additive, so on. It adds a machine and changes nothing the base mod does — and it is the
	 * first addon machine to read a fluid identifier out of a slot, so it is the second thing in
	 * the project that {@link #fuzzyIdentifier} makes reachable.
	 */
	public static boolean hpBoiler = true;

	/**
	 * The slop reactor, "Average 'Nuclear Reactor' in Modern Mods": a controller block, a casing
	 * and a glass you build into a 5x5x5 cube, fill with toxic block from uranium ingots, and keep
	 * from melting down with snowballs.
	 *
	 * <p>Additive, so on. <b>Its meltdown is the one destructive thing in the addon that needs no
	 * overhaul switch to be reached</b>: left on without a snowball for thirty seconds it clears a
	 * 17-block cube, bedrock included, and kills everything inside (D-1082). It is upstream's, and
	 * this switch is the only gate on it.
	 */
	public static boolean slopReactor = true;

	/**
	 * The Neutron Blaster: a 3x3x3 machine with a tower, loaded through its door with a solinium core
	 * and four explosive lenses, that transmutes fifteen items at a time when it is powered and
	 * closed (D-1087).
	 *
	 * <p>Additive, so on. It adds a block and changes no base-mod block or item - the items it
	 * transmutes <em>into</em> are the ones that exist when it fires, and a row whose output belongs
	 * to a switched-off feature is simply not in its recipe table. What it does to a stack it is
	 * given - radioactive items become slaked sellafield, food and plants vanish - is upstream's.
	 */
	public static boolean solBlaster = true;

	/**
	 * The antimatter reaction chamber: the base, the emitter and the four stabilizers that make power
	 * out of deuterium and tritium (or any DFC fuel), three satellite chips and a core, and kill
	 * everything near them while they do it (D-3002).
	 *
	 * <p>Additive, so on. It adds three machine blocks and the ray-emission muzzle and the two
	 * stabilizer focuses they take, and changes nothing the base mod already has - the base mod's
	 * own AMS blocks, upstream's "deco" ones, are a different set. Off registers none of them.
	 */
	public static boolean ams = true;

	/**
	 * The anti-mass spectrometer's two blocks, the analyzer and the receiver (DFC-4, D-3017): a 7-block
	 * tower hung below its core and a 3x3 base, each drawn by a renderer and doing nothing else. Upstream
	 * marks both work in progress and comments out the receiver's whole tick; nothing here is balance.
	 * Additive, so on; off registers neither.
	 */
	public static boolean amsp = true;

	/**
	 * Three animated doors: the reactor door, which swings open on a handle and takes the paint of
	 * whatever block you hold, and the small and large crimson doors, which slide apart into the wall.
	 * All three are radiation-resistant while shut, and the crimson pair shake the camera.
	 *
	 * <p>Additive, so on. They are the addon's own blocks on the base mod's door system
	 * ({@code DoorDecl}, {@code BlockDoorGeneric}), and change nothing the base mod's doors do. Off
	 * registers none of them (D-3005).
	 */
	public static boolean doors = true;

	/**
	 * The addon's own pressurised water reactor (upstream's {@code AddonBlocks.PWR}): the pressure vessel,
	 * reflector, fuel and control rod columns in two skins each, coolant channels, integrated boilers,
	 * heat exchangers, the fluid port, the control port and the OCCS inlet and outlet (D-3006).
	 *
	 * <p>Additive, so on: these are new blocks beside the base mod's PWR, which they leave alone. What
	 * upstream does to the base PWR when its own is on - hiding its blocks and replacing its assembler
	 * recipes - is the overhaul, {@link #pwr}, which stays off. Registers only with {@link #addonFluids},
	 * because the emergency buffer holds the addon's malfunctioning coolant: see {@link #pwrReactor()}.
	 */
	public static boolean pwrBlocks = true;

	/** Whether the addon PWR registers: {@link #pwrBlocks} <b>and</b> {@link #addonFluids}. A derived gate. */
	public static boolean pwrReactor() {
		return pwrBlocks && addonFluids;
	}

	/**
	 * The molten-salt reactor's six blocks (D-3012): reaction chamber, fuel plug, ejector, control rod,
	 * its extension tube and the arbitrary block, upstream's {@code AddonBlocks.LFTR}, and the salt fuel
	 * separation plant that works the salt they spend (D-3015). Additive, so on -
	 * upstream registers them unconditionally, and there is no base-mod LFTR for them to change. The
	 * older {@link #lftr} switch, off, sat in the overhauls before any of it was ported and is kept as
	 * {@link #pwr} is beside {@link #pwrBlocks}. Registers only with {@link #addonFluids}: the salt is the
	 * addon's FLiBe, the plug's default coolant and the only {@code FT_LFTRCoolant} fluid there is.
	 */
	public static boolean lftrBlocks = true;

	/** Whether the MSR blocks register: {@link #lftrBlocks} <b>and</b> {@link #addonFluids}. A derived gate. */
	public static boolean lftrReactor() {
		return lftrBlocks && addonFluids;
	}

	/**
	 * The absorption particle reactor (D-3027): the core, its eight component blocks, the fluid port and the
	 * power outlet, the endothermic blast ({@code NukeLCA}) it ends in, and the containment hazard that blast
	 * makes of a destroyed absorption particle capsule. Additive - there is no base-mod APR - so on. Registers
	 * only with {@link #resourceItems} and {@link #addonFluids}: the capsule and the mysticite blanket are
	 * resource items, and the reactor runs on the addon's gaseous oxygen.
	 */
	public static boolean apr = true;

	/** Whether the APR registers: {@link #apr}, {@link #resourceItems} <b>and</b> {@link #addonFluids}. A derived gate. */
	public static boolean aprReactor() {
		return apr && resourceItems && addonFluids;
	}

	/**
	 * The mixing vat (D-3016): a two-tank machine that stirs fuel nuggets into FLiBe salt. Additive -
	 * upstream registers it unconditionally and there is no base-mod vat to change. Registers only with
	 * {@link #addonFluids}: the salt it takes is the addon's FLiBe, the only {@code FT_LFTRCoolant} fluid there is.
	 */
	public static boolean mixingVat = true;

	/** Whether the mixing vat registers: {@link #mixingVat} <b>and</b> {@link #addonFluids}. A derived gate. */
	public static boolean mixingVatMachine() {
		return mixingVat && addonFluids;
	}

	/**
	 * The coolant heat exchanger (D-3016): cools a hot fluid, in a Forge tank or an NTM one, into the heat that boils a
	 * second coolant. Additive, for the vat's reason. Registers only with {@link #addonFluids}: its warm air
	 * and warm water, and its default fluid, are the addon's.
	 */
	public static boolean coolantHeatex = true;

	/** Whether the heat exchanger registers: {@link #coolantHeatex} <b>and</b> {@link #addonFluids}. A derived gate. */
	public static boolean coolantHeatexMachine() {
		return coolantHeatex && addonFluids;
	}

	/**
	 * Oily coal ore, and the burning ore it turns into: a coal ore that drops two or three coal,
	 * catches when fire or lava touches it, and stays alight for good. Generates rarely, deep in
	 * the overworld.
	 *
	 * <p>Additive, so on. Upstream has no switch; mainline NTM removed the block and the addon
	 * restored it as one of its "legacy blocks". Off also stops it generating, because the three
	 * worldgen files are conditional on the block existing (D-1063).
	 */
	public static boolean oilyCoal = true;

	/**
	 * The fluid bottle: one bottle per drinkable NTM fluid, and what drinking each does to you -
	 * burns, frostbite, acid, radiation, poison, vomiting. Additive, so on. Upstream has no
	 * switch (D-1066).
	 */
	public static boolean fluidBottles = true;

	/**
	 * The addon's six battery packs - desh, euphemium, saturnite, "slop", spark and electronium -
	 * which hold and pass power exactly as the base mod's packs do. Additive, so on. Upstream
	 * appends them to the base mod's battery enum with a mixin; here they are items of their own
	 * (D-1067).
	 */
	public static boolean batteryPacks = true;

	/**
	 * "Last Resort", the meltdown pill: wipes your radiation and mutates you for three minutes,
	 * and a second one within the hour kills you. Additive, so on (D-1069).
	 */
	public static boolean meltdownPill = true;

	/**
	 * The block of welded osmiridium and its xanaxium-etched form: the addon's toughest blocks,
	 * 3.9 million blast resistance. Additive, so on (D-1071).
	 */
	public static boolean osmiridiumBlocks = true;

	/**
	 * The dark concrete bricks, in their four finishes (plain, mossy, cracked, broken): decoration with
	 * a hardness of 15 and a blast resistance of 96. Additive, so on (D-1090).
	 */
	public static boolean concreteBricks = true;

	/**
	 * The High-Tech Advisor: a pocket computer that warns a new player about coal dust, asbestos,
	 * radon, lava behind them and the rest, keeps a history of what it said, and is handed out once
	 * to every player who joins. Additive, so on - it adds an item and a screen and changes nothing
	 * the base mod does (D-1080).
	 */
	public static boolean advisor = true;

	/**
	 * The radiation locator goggles: a helmet drawn as the goggles' own model on the wearer's face (D-3026). Additive, so on. The
	 * goggles' one function upstream - showing a box with the dose wherever the chunk radiation rises within 256 blocks - needs the
	 * base mod to say when it does, which it cannot yet; they are worn and drawn, and recipe-craftable, and do not locate.
	 */
	public static boolean radGlasses = true;

	/**
	 * The RealerSim fuel channels, plain and moderated: an RBMK column that throws its neutrons in
	 * random directions, twice as far, bending toward absorbers and control rods. Additive - a new
	 * column beside the base mod's, which it leaves alone - so on. Brings the
	 * {@code dialReasimCount} gamerule with it (D-1074).
	 */
	public static boolean realerSim = true;

	/**
	 * Chernobylite and zetalite ore: two corium ores that need an iron and a diamond pickaxe and
	 * leak chunk radiation for as long as they stand. Additive, so on. Upstream has no switch. Nothing
	 * grows them yet: upstream's corium spawns them through a mixin into the base mod's corium
	 * spread, which is a hand-off (D-1076).
	 */
	public static boolean coriumOres = true;

	/**
	 * The addon's world-placeable fluids: liquid oxygen, liquid fluoride, liquid concrete and the
	 * molten DFC component, each a fluid, a block and a bucket (D-1079). Additive, so on. Upstream
	 * has no switch - it registers them with its blocks, unconditionally.
	 *
	 * <p>Not the same thing as {@link #addonFluids}, which is the tank fluids. These do not need
	 * those: nothing here is a {@code com.hbm.inventory.fluid.FluidType}, and the bridge between
	 * the two is not ported.
	 */
	public static boolean worldFluids = true;

	/**
	 * The addon's six shields and the two ingots they are made and mended with: steel, titanium,
	 * the SWAT shield, plutonium-238, fissite and mysticite (D-1083). Additive, so on. Upstream has
	 * no switch - it registers them with its items, unconditionally.
	 */
	public static boolean shields = true;

	/**
	 * The pneumatic tube regex filter: a one-slot pipe segment that only lets through items whose
	 * id, tag or creative tab matches a list of regular expressions, and pushes them into whatever
	 * it faces. Additive, so on (D-1084).
	 */
	public static boolean regexFilter = true;

	/**
	 * The addon's fifty simple material items: the alkali ingots, xanaxium, nuclium, taintium,
	 * fissium, fissite, mysticite and chydalium in their ingot, powder, billet and plate forms, the
	 * blankets, the anti-mass cells, the particle capsules and the DFC absorber shrapnels (D-1085).
	 * Additive, so on. Upstream has no switch - it registers them with its items, unconditionally.
	 *
	 * <p>Nothing here has a source yet - the crucible, the solar blaster, the DFC and the cyclotron
	 * are unported - so a player takes them from the creative tab. The fissite and mysticite ingots
	 * also register when {@link #shields} is on and this is off, because the shields are mended with
	 * them.
	 */
	public static boolean resourceItems = true;

	/**
	 * The addon's fuel rods: the empty rod assembly, fifty-nine rods - one per isotope, each with its
	 * own heat curve, melting point and life - and the five depleted fuels some are made from
	 * (D-3001). Additive, so on. Upstream has no switch: it registers them with its items.
	 *
	 * <p>A rod is crafted from the empty assembly and the base item it is made of, and uncrafted by
	 * putting it alone in the grid. Nothing in this jar takes one yet - the PWR, the RTG heater, the
	 * ZIRNOX and research-reactor hooks are later batches - so today a rod is carried, not burned,
	 * and it already heats, cools and sets its holder alight on its own.
	 */
	public static boolean rods = true;

	/**
	 * The reactor-tier line under a reactor block's name - "RBMK (Tier 3 / Fission Reactor)", and with
	 * shift the tiers either side - on the base mod's reactor blocks as well as the addon's (D-3001).
	 * Additive, so on; upstream does it unconditionally, through a bytecode hook on every tooltip.
	 */
	public static boolean reactorTiers = true;

	/**
	 * The torex: the addon's mushroom cloud, in nuke, balefire, digamma and endothermic palettes,
	 * spawned by {@code /hbmleaf torex ...}, and the camera shake that rides with it
	 * ({@code /hbmleaf shake ...}). Additive, so on.
	 *
	 * <p>What the switch gates is <em>behaviour</em>, not a registry: the torex is not an entity
	 * type here (D-1089), so there is nothing to withhold from a registry. Off, the command is not
	 * registered, {@code LCETorex.statFac*} spawn nothing, the server and client tickers return on
	 * their first line, and the camera hooks apply nothing. The three packet types stay in
	 * {@code LeafiaPacketTypes} regardless, because the count is the protocol version (D-1024).
	 */
	public static boolean torex = true;

	/**
	 * Baleonite and thermonuclear ash: the six-level rock a balefire pool leaves where it ate stone,
	 * and the falling grey powder balefire burns down to, which eats rock, catches fire again and
	 * burns whoever walks in it (D-3004). Additive, so on. Upstream has no switch. The balefire
	 * corium that makes the baleonite is a world fluid and rides {@link #worldFluids}.
	 */
	public static boolean balefireResidue = true;

	/**
	 * Digammitite: the six-level rock a digamma blast leaves, which gives off digamma to everything
	 * within 32 blocks (D-3004). Additive, so on. Upstream has no switch.
	 */
	public static boolean digammitite = true;

	/**
	 * The eleven contamination blocks upstream's fallout converts to: contaminated dirt, gravel,
	 * sand, red sand, sandstone, red sandstone, terracotta, snow, a snow block and ice, and
	 * scorched grass, each in up to seven levels (D-3004). Additive, so on - they are blocks a
	 * player can place from the creative tab and the PWR's meltdown can leave. <b>They are only
	 * made by fallout when {@link #falloutOverhaul} is on.</b> Upstream has no switch.
	 */
	public static boolean legacyWaste = true;

	/**
	 * The digamma crater: the digamma and endothermic kinds of fallout (their own column pass and, for
	 * digamma, a crater biome five times the blast's fallout radius), the {@code leafia:digamma_crater}
	 * biome itself - black sky and fog, red water, grey grass, no rain and no music - the blood rain and
	 * the things that stand in it, and the digamma rod's blast leaving it (D-3009). Additive, so on: only
	 * the addon's own blasts make either kind. Off, the biome is not in the registry, the two fallout
	 * kinds are not registered (a blast asking for one gets the base mod's fallout) and the client hooks
	 * do nothing.
	 */
	public static boolean digammaCrater = true;

	/**
	 * The radioisotope thermal heater - a heat source for the boilers above it, fed by fifteen RTG pellets
	 * and one addon fuel rod - and the Nuclium-279 RTG pellet, which decays into the depleted fissium
	 * pellet (registered with it, whatever {@link #resourceItems} says). Additive, so on (D-3020).
	 */
	public static boolean rtg = true;

	/**
	 * The Meteor Protection Beacon (D-3022): a torch-sized block that, once the player who placed it has
	 * been alive for {@code balance.meteor.diverterMinAliveTime} seconds, pushes every meteor that would fall
	 * within {@code diverterProtectionRadius} chunks of it out of that range. Additive, so on: it does nothing
	 * to a meteor until a player places one. Off, neither block is registered and no meteor is diverted.
	 */
	public static boolean meteorDiverter = true;

	/**
	 * The addon's two bombs (D-3022): the Fat Chud, which wrecks a 101-block cube without a blast - grass to dead
	 * and oily dirt, leaves, brick and concrete gone, every multiblock thrown into the air, fifteen towers of
	 * yellow barrels and sellafite and a soot cloud - and the custom nuke missile, a missile item with the
	 * custom nuke's 27 slots that every launch pad fires. Additive, so on.
	 */
	public static boolean addonBombs = true;

	/**
	 * The anti-mass rifle (D-3028): the addon's one sedna gun, its rainbow beam, its lock-on and the two weapon mods it takes
	 * (the scope and the refractor, which behave differently on it than on the base mod's guns). Additive, so on. Off, the item,
	 * its mod definitions and its two per-gun overrides are absent and no base gun is touched either way.
	 */
	public static boolean amRifle = true;

	/**
	 * The pink door (D-3028): a wooden door that is also a bomb - any detonator sets off a radius-15 explosion, a shower of RBMK
	 * debris and shrapnel, falling corium, two boxcars, seventeen catapult nukes and a falling hydrogen bomb. Additive, so on.
	 */
	public static boolean pinkDoor = true;

	/**
	 * The addon's two grenade fillings (D-3028): the null filling (a 7x7x7 cube of nothing, and everything in it killed) for the
	 * fragmentation shell and the solinium filling (a radius-25 solinium blast) for the nuclear shell. Additive, so on. The two enum
	 * constants are appended to the base mod's filling enum whatever this says (NeoForge's enum extension is applied when the class
	 * loads, before any config); off, their items are not registered, so no grenade can be built with them.
	 */
	public static boolean leafiaGrenades = true;

	/**
	 * The addon's fluid traits, hung on the base mod's fluids: DFC power ratings on 22 fuels,
	 * magnetic on four, and flavour text on cryogel.
	 *
	 * <p><b>Off</b>, and it belongs in this half of the file rather than under content: these
	 * attach to fluids the base mod owns, so switching it on changes what the base mod's own
	 * fluids say — and, once the DFC, the LFTR and the antimatter pipe are ported, what they
	 * do. It is not gated on {@link #dfcOverhaul} because only one of the four traits is a DFC
	 * trait; see D-1012.
	 */
	public static boolean fluidTraits = false;

	/**
	 * The addon's fallout table (D-3004): the base mod's fallout stops turning ground, sand and
	 * grass into slaked sellafield and makes the contamination blocks instead, in levels that fall
	 * with distance, and sand no longer makes trinitite out to the edge. <b>Off</b>, in this half of
	 * the file: it removes entries the base mod's own table has and so changes what a nuclear blast
	 * does to the ground. Needs {@link #legacyWaste}. Upstream does it unconditionally.
	 */
	public static boolean falloutOverhaul = false;

	/**
	 * Fallout weather (D-3009): when an ordinary nuke's fallout has finished sweeping, the sky over its
	 * radius keeps raining fallout for {@code 600 + radius x 1,200} ticks, thinning as it goes, and
	 * keeps dusting the ground with fallout layers. <b>Off</b>, in this half of the file: it follows every
	 * base-mod nuke and puts base-mod fallout on the ground long after the base mod's own rain stopped.
	 * Upstream does it unconditionally.
	 */
	public static boolean falloutRain = false;

	/**
	 * The addon's torex in place of the base mod's mushroom cloud (D-3009): every base-mod nuke and
	 * balefire bomb that would raise the base cloud raises an {@code LCETorex} instead. <b>Off</b>, in this
	 * half of the file: it replaces what the base mod draws for its own bombs. Upstream's
	 * {@code useLeafiaTorex} defaults on. Needs {@link #torex}; with that off the base cloud stays.
	 */
	public static boolean leafiaTorex = false;

	/**
	 * The addon's explosions, corium and fire (D-3013): balefire spreads its own way and leaves balefire
	 * ash; the balefire crater digs from five above the bomb, clears what is above it and leaves baleonite;
	 * the Solinium rinse restores the addon's contamination blocks and the crater biome and lifts fallout
	 * weather over it; corium only burns, irradiates 50 (500 for balefire corium) a tick, sets anywhere,
	 * lights balefire and makes baleonite from balefire corium, and <em>explodes on meeting water</em>;
	 * and a chemical thrower's concrete sets where it lands. <b>Off</b> by default (D-1002, D-3013): it
	 * changes how base-mod blocks and bombs behave, although upstream applies it unconditionally (eight
	 * mixins with no switch). The explosive-gas blast power is a rebalance and is
	 * {@link #explosiveGasRebalance}, off too.
	 */
	public static boolean explosionVfx = false;

	/**
	 * Folkvangr (D-3013): every F.L.E.I.J.A. blast (the base mod's MK3 sphere) becomes the addon's
	 * Folkvangr, which erases a sphere chunk by chunk behind the growing cloud, kills everything inside it
	 * outright and pulls what is outside it in afterwards; the cloud grows at the Folkvangr's pace, fires
	 * lightning, syncs a tick rate, sheds particles and is drawn as upstream's glowing sphere, and a Solinium
	 * rinse sweeps RBMK and ZIRNOX fuel debris up as it grows. <b>Off</b> by default, for the reason
	 * {@link #explosionVfx} gives. Off, the Folkvangr entity is not registered and every cloud and MK3 is
	 * the base mod's.
	 */
	public static boolean folkvangr = false;

	/**
	 * Explosive gas bursts at strength 2 and <em>breaks blocks</em>, where the base mod's is strength 3
	 * and spares them (upstream's {@code MixinBlockGasExplosive}). A rebalance hidden in a mixin, so
	 * <b>off</b> by D-1097, whatever upstream does (D-3013).
	 */
	public static boolean explosiveGasRebalance = false;

	/**
	 * The addon's RBMK (D-3018): a column above its rated heat takes damage over time and melts the
	 * reactor down when the damage is full, rather than a fuel channel melting at one temperature; a
	 * damaged column's lid jumps; the AZ-5 also shuts every automatic control rod off until it is
	 * acknowledged on the rod's own (replaced) screen; four fuels glow in their own Cherenkov colour;
	 * a melted reactor's debris pile smokes, and a lid thrown by the meltdown drops the blocks it tears
	 * out instead of deleting them. <b>Off</b> by default (D-1002): upstream applies all of it
	 * unconditionally (nine mixins, no switch), and every piece of it changes what a base-mod RBMK
	 * does. Off, every base-mod reactor is upstream's and the smoking debris block is not registered.
	 */
	public static boolean rbmkOverhaul = false;

	/**
	 * Whether the RBMK overhaul's own registry entries exist: the smoking debris block and its block
	 * entity. {@link #rbmkOverhaul}, or a GameTest server ({@code leafia.gametest.fixtures}), so the
	 * suite can test the overhaul and switch it off again, as {@link #dfcOverhaulContent()} does.
	 * Not a switch of its own.
	 */
	public static boolean rbmkOverhaulContent() {
		return rbmkOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * Antimatter in the base mod's own fluid networks (upstream's {@code MixinFluidNetMK2}): a provider of
	 * an antimatter fluid that sits on a plain network with anything else subscribed <b>explodes</b> (power
	 * 5, fire, blocks) and is dropped from the network. The antimatter pipe's own network is not touched -
	 * it holds the fluid with a field (D-1022). <b>Off</b> by D-1002: it changes how the base mod's tanks,
	 * barrels and pipes handle a fluid they already carry. Upstream applies it unconditionally (D-3025).
	 */
	public static boolean antimatterContainment = false;

	/**
	 * Fluid barrels with ports of the addon's choosing (upstream's {@code MixinTileEntityBarrel}, D-3025):
	 * with {@link #barrelSidePorts} on the barrel keeps its six ports but is <em>connectable</em> only from
	 * above and below; with it off the barrel has only its top and bottom ports. <b>Off</b> by D-1002: the
	 * base barrel has six ports and connects on every side.
	 */
	public static boolean barrelPorts = false;

	/**
	 * Upstream's {@code enableBarrelSidePorts} (default on), read only while {@link #barrelPorts} is on.
	 * Upstream's own reading of it is inconsistent - on, the barrel's ports are the six sides but
	 * {@code canConnect} refuses the four sides; off, the ports are top and bottom but {@code canConnect}
	 * answers as the base - and both halves are kept (D-3025).
	 */
	public static boolean barrelSidePorts = true;

	/**
	 * The industrial boiler's two tanks made five times larger, in boilers placed from now on (upstream's
	 * {@code MixinTileEntityHeatBoilerIndustrial}: 320,000 mB of water and 32,000,000 of steam, where the
	 * base mod's are 64,000 and 6,400,000). A <b>rebalance</b>, so off whatever upstream does (D-1000,
	 * D-3025); a boiler saved earlier keeps the size it was saved with.
	 */
	public static boolean industrialBoilerTanks = false;

	/**
	 * Firestorm (upstream's {@code enableFirestorm}, which upstream sets only in a development
	 * environment): fire next to a chemical plant holding a flammable fluid can burn it out. A burnt-out
	 * plant refuses its screen and does no work, drains its tanks, is drawn as a wreck, and while it burns
	 * sets entities alight and spreads fire in a radius of four (D-3025). <b>Off</b>: it destroys a base-mod
	 * machine, and upstream's own release leaves it off too.
	 */
	public static boolean firestorm = false;

	/** The DFC rework. <b>Overhauls</b> the base Dark Fusion Core. (L6) */
	public static boolean dfcOverhaul = false;

	/**
	 * What the addon does to the base mod's tungsten crate (D-3021): it takes the SPK network's power like a
	 * DFC receiver, and a beam's smelt reads the DFC recipe table (a crate full of redstone dust and a beam of
	 * 200,000 SPK makes mercury). <b>Overhauls</b>, so off: the crate's block entity is swapped for the
	 * addon's under its own type (the price of that is {@code dfcOverhaul}'s - a crate saved as the addon's
	 * type is dropped by a world loaded without it), and a beam's smelt changes.
	 */
	public static boolean tungstenCrate = false;

	/**
	 * Whether the tungsten crate's own registry entry exists. {@link #tungstenCrate}, or a GameTest server
	 * ({@code leafia.gametest.fixtures}), as {@link #dfcOverhaulContent()}. Not a switch of its own.
	 */
	public static boolean tungstenCrateContent() {
		return tungstenCrate || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * Whether the DFC overhaul's own registry entries exist: the overhauled core's block-entity type
	 * and menu, the pulse detonator, the two crucifixes. {@link #dfcOverhaul}, or a GameTest server
	 * ({@code leafia.gametest.fixtures}), so the suite can switch the overhaul on for a test and off
	 * again: what the switch decides at run time is whether the base core's block makes the addon's
	 * block entity (D-3011). Not a switch of its own.
	 */
	public static boolean dfcOverhaulContent() {
		return dfcOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}
	/**
	 * What the addon PWR does to the <em>base</em> PWR: hide its blocks and replace its assembler recipes
	 * ({@code LeafiaBaseRecipes.HELD}). <b>Overhauls</b>, so off. The addon's own reactor is
	 * {@link #pwrBlocks}. (L6)
	 */
	public static boolean pwr = false;
	/**
	 * The LFTR as an overhaul, a placeholder from before any of it was ported (L6). Nothing reads it but
	 * {@link #ffBridge()}; the reactor's own blocks are {@link #lftrBlocks} (D-3012).
	 */
	public static boolean lftr = false;
	/** ZIRNOX changes. <b>Overhauls</b> the base reactor. (L6) */
	public static boolean zirnoxOverhaul = false;

	/**
	 * Whether the ZIRNOX overhaul's own registry entries exist: the overhauled reactor's block-entity type.
	 * {@link #zirnoxOverhaul}, or a GameTest server, for the reason {@link #dfcOverhaulContent()} gives: what
	 * the switch decides at run time is whether the base reactor's core makes the addon's block entity
	 * (D-3020). Not a switch of its own.
	 */
	public static boolean zirnoxOverhaulContent() {
		return zirnoxOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * The RTG blast furnace takes an addon fuel rod in a tenth slot, whose heat adds to the pellets', and
	 * its screen is the addon's (upstream's three {@code DiFurnaceRTG} mixins). It changes a base machine,
	 * so <b>off</b> by D-1002 (D-3020).
	 */
	public static boolean rtgFurnaceOverhaul = false;

	/** Whether {@link #rtgFurnaceOverhaul}'s block-entity type exists; as {@link #zirnoxOverhaulContent()}. */
	public static boolean rtgFurnaceOverhaulContent() {
		return rtgFurnaceOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}
	/** Radiation classification — replaces item radiation values. <b>Overhaul</b>. (L6) */
	public static boolean radiationClassification = false;

	/**
	 * The addon's rework of how the base mod's hazards behave (D-3023): radon gas through a gas mask,
	 * the toxic type, alkaline metals replacing hydroreactive, sharp edges, the geiger counter's nine
	 * clicks, the hot item's advisor warning, the crater biomes' underground exemption and the mono
	 * filter's fine-particle class. It changes base behaviour, so <b>off</b> by D-1002.
	 */
	public static boolean hazardOverhaul = false;

	/**
	 * The addon's look for the base mod's things (D-3023): hot sellafield colours, darker slaked
	 * sellafield, the rod of discord's creative range, the hazard diamond's wider numbers, the cooling
	 * tower's wind and the gas flame's additive glow. It changes how base blocks and particles look, so
	 * <b>off</b> by D-1002.
	 */
	public static boolean cosmeticOverhaul = false;

	/**
	 * The addon's replacements for the base mod's crafting recipes (D-3026): the laser detonator and the multi-detonator
	 * made cheap, ice powder no longer craftable, and 5-HTP and Xanax made with digammitite. Upstream does all of it
	 * unconditionally ({@code removeRecipesForItem} in {@code AddonCraftingRecipes}); it changes what the base mod's own
	 * items cost, so it is <b>off</b> by D-1002. Off, the base's recipes are untouched; on, each is dropped by a
	 * conditional override of its recipe file and the addon's recipe is loaded in its place.
	 */
	public static boolean recipeOverhaul = false;

	/**
	 * The base mod's plain asphalt stops speeding the player up and stops saying it does (D-3026). Upstream's
	 * {@code MixinBlockSpeedy} cancels the block's {@code onPlayerStep} and its tooltip for {@code asphalt} alone, so the
	 * addon's lined asphalts, which never had the boost, and the road they are painted on behave alike. It changes what a base
	 * block does, so <b>off</b> by D-1002.
	 */
	public static boolean asphaltSpeed = false;
	/**
	 * The Custom Control Panel (D-3060): the data core, the panel block, its editor and every
	 * instrument. Additive content, so <b>on</b> (D-1002); off, no block, block entity, menu, item or packet handler of the
	 * panel is registered. Config key {@code features.content.controlPanel}, which is the {@code controlPanel.enabled} switch
	 * of the plan. The pure data classes load either way and are inert.
	 */
	public static boolean controlPanel = true;
	/**
	 * IC10 scripting on the Custom Control Panel (the IC10 node and its editor). Additive content, so <b>on</b> (D-1002, D-3073); off, the
	 * node is not offered, not built from a saved graph (it stays an inert placeholder with its tag) and never runs. (L6)
	 */
	public static boolean controlPanelIC10 = true;

	/**
	 * Floating and unsupported buildings collapse. Off, and upstream's own config comment is
	 * the reason it stays off: "WARNING: VERY LAGGY &amp; CHALLENGING!".
	 */
	public static boolean structuralIntegrity = false;

	/**
	 * Meteors land the addon's way (D-3022): a falling meteor shakes the camera of everyone within 250 blocks
	 * every five ticks, and on landing, with {@code balance.meteor.customCraters}, it digs the addon's crater -
	 * a lava-floored bowl lined with slaked sellafite and tektite, its debris thrown outward - in place of the
	 * base mod's rubble and smoke, and buries its meteorite two blocks deeper. <b>Off</b> (D-1002): it changes
	 * what a base-mod meteor does. Upstream applies it unconditionally.
	 */
	public static boolean meteorOverhaul = false;

	/**
	 * Five meteor strikes in six are called off (upstream's {@code MixinBossSpawnHandler}: a strike only goes
	 * ahead on {@code nextInt(6) == 0}). A rebalance hidden in a mixin, so <b>off</b> by D-1097 (D-3022).
	 */
	public static boolean meteorStrikeRebalance = false;

	/**
	 * Base-mod missiles end the addon's way (D-3022): the nuclear, nuclear cluster, doomsday, micro-nuke and EMP
	 * missiles burst in the air over their target (within 20 blocks of it and under 50 above the ground) and
	 * break up if they hit a block first; and every other missile shot down sets its warhead off where it was
	 * hit instead of scattering. <b>Off</b> (D-1002). The custom nuke missile, the addon's own, always does.
	 */
	public static boolean missileOverhaul = false;

	/**
	 * Base-mod landmines are the addon's (D-3022): a mine a player placed kills outright (600,000 damage,
	 * {@code leafia:mine_player}); a worldgen mine hits for 2.25 times the configured damage, capped at 11
	 * where that is 22 or less; the priming click is half as loud; and the anti-personnel mine's trigger box
	 * is a quarter as wide. Rebalances, so <b>off</b> (D-1097).
	 */
	public static boolean landmineOverhaul = false;

	/**
	 * After the Tom impact (D-3022): while the sky burns and for 400 days after, every block placed is checked
	 * for structural support at once, and the burning sky's chunk pass also brings down one unsupported block
	 * in a hundred. <b>Off</b> (D-1002): it changes what the base mod's impact does to the world.
	 */
	public static boolean impactSeismic = false;

	/**
	 * The base mod's siren is the addon's (D-3027): its sound is a loop the addon plays with a linear falloff
	 * from 6 blocks to {@code 6 + 1.5 x volume}, sent by the addon's packet to players within 8,192 blocks, and a
	 * one-shot track is cut off when the signal drops instead of playing to its end. It changes a base machine, so
	 * <b>off</b> (D-1002). Upstream applies it unconditionally.
	 */
	public static boolean sirenOverhaul = false;

	/** Whether {@link #sirenOverhaul}'s block-entity type exists; as {@link #zirnoxOverhaulContent()}. */
	public static boolean sirenOverhaulContent() {
		return sirenOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * The base mod's laser detonator is the addon's (D-3027): a 128-block ray from the hand that holds it (512
	 * while sneaking with the other hand empty), a red laser drawn for everyone in range, a key that only points,
	 * an aim-down-sights view and its own held model. It changes a base item, so <b>off</b> (D-1002).
	 */
	public static boolean laserDetonatorOverhaul = false;

	/**
	 * The base mod's arc welder is the addon's (D-3027): its own sparks, flashes, smoke and looping sound in place
	 * of the base mod's particles, and a welder working in the rain or within two blocks of water shocks everything
	 * around it for 4 and now and then sets fire. It changes a base machine, so <b>off</b> (D-1002).
	 */
	public static boolean arcWelderOverhaul = false;

	/** Whether {@link #arcWelderOverhaul}'s block-entity type exists; as {@link #zirnoxOverhaulContent()}. */
	public static boolean arcWelderOverhaulContent() {
		return arcWelderOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/** The soldering station's sparks and sound are the addon's (D-3027). <b>Off</b> (D-1002). */
	public static boolean solderingOverhaul = false;

	/** Whether {@link #solderingOverhaul}'s block-entity type exists; as {@link #zirnoxOverhaulContent()}. */
	public static boolean solderingOverhaulContent() {
		return solderingOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * The base mod's free-electron laser is the addon's (D-3027, upstream's "fuck up the dfc exploit"): each
	 * wavelength costs {@code 1250 x 4^n} instead of {@code 3^n}, burns its own band of block hardness, gamma
	 * leaves balefire and DRX always burns, and a beam into a DFC core feeds it SPK and heat instead of melting it.
	 * A rebalance of a base machine, so <b>off</b> (D-1002, D-1097).
	 */
	public static boolean felOverhaul = false;

	/** Whether {@link #felOverhaul}'s block-entity type exists; as {@link #zirnoxOverhaulContent()}. */
	public static boolean felOverhaulContent() {
		return felOverhaul || com.leafia.gametest.LeafiaGameTestFixtures.enabled();
	}

	/**
	 * Turrets hold fire through a friend (D-3028): the Chekhov (and so the friendly turret) and the Jeremy skip a shot when the
	 * first thing between the barrel and the target is an entity the turret would not target. Upstream's three turret mixins. It
	 * changes when a base-mod turret fires, so <b>off</b>.
	 */
	public static boolean turretFriendlyFire = false;

	public static final ModConfigSpec SPEC;
	private static final Values VALUES;

	static {
		Pair<Values, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Values::new);
		VALUES = pair.getLeft();
		SPEC = pair.getRight();
	}

	/** The spec values themselves. Read through the static fields above, not from here. */
	private static class Values {

		final ModConfigSpec.BooleanValue vElevators;
		final ModConfigSpec.BooleanValue vFfDuct;
		final ModConfigSpec.BooleanValue vTurbines;
		final ModConfigSpec.BooleanValue vAmatPipes;
		final ModConfigSpec.BooleanValue vSpkCable;
		final ModConfigSpec.BooleanValue vFluidDuctEquipment;
		final ModConfigSpec.BooleanValue vLights;
		final ModConfigSpec.BooleanValue vAddonFluids;
		final ModConfigSpec.BooleanValue vDebugBlocks;
		final ModConfigSpec.BooleanValue vWind;
		final ModConfigSpec.BooleanValue vLetterSigns;
		final ModConfigSpec.BooleanValue vCatwalks;
		final ModConfigSpec.BooleanValue vLinedAsphalt;
		final ModConfigSpec.BooleanValue vBroof;
		final ModConfigSpec.BooleanValue vControlPanel;
		final ModConfigSpec.BooleanValue vCrateLabels;
		final ModConfigSpec.BooleanValue vRack;
		final ModConfigSpec.BooleanValue vUpgradeControl;
		final ModConfigSpec.BooleanValue vMixedConcrete;
		final ModConfigSpec.BooleanValue vFuzzyIdentifier;
		final ModConfigSpec.BooleanValue vHpBoiler;
		final ModConfigSpec.BooleanValue vSlopReactor;
		final ModConfigSpec.BooleanValue vSolBlaster;
		final ModConfigSpec.BooleanValue vAms;
		final ModConfigSpec.BooleanValue vApr;
		final ModConfigSpec.BooleanValue vAmsp;
		final ModConfigSpec.BooleanValue vDoors;
		final ModConfigSpec.BooleanValue vPwrBlocks;
		final ModConfigSpec.BooleanValue vLftrBlocks;
		final ModConfigSpec.BooleanValue vMixingVat;
		final ModConfigSpec.BooleanValue vCoolantHeatex;
		final ModConfigSpec.BooleanValue vOilyCoal;
		final ModConfigSpec.BooleanValue vFluidBottles;
		final ModConfigSpec.BooleanValue vBatteryPacks;
		final ModConfigSpec.BooleanValue vMeltdownPill;
		final ModConfigSpec.BooleanValue vOsmiridiumBlocks;
		final ModConfigSpec.BooleanValue vConcreteBricks;
		final ModConfigSpec.BooleanValue vAdvisor;
		final ModConfigSpec.BooleanValue vRadGlasses;
		final ModConfigSpec.BooleanValue vAsphaltSpeed;
		final ModConfigSpec.BooleanValue vRealerSim;
		final ModConfigSpec.BooleanValue vCoriumOres;
		final ModConfigSpec.BooleanValue vWorldFluids;
		final ModConfigSpec.BooleanValue vShields;
		final ModConfigSpec.BooleanValue vRegexFilter;
		final ModConfigSpec.BooleanValue vResourceItems;
		final ModConfigSpec.BooleanValue vRods;
		final ModConfigSpec.BooleanValue vReactorTiers;
		final ModConfigSpec.BooleanValue vTorex;
		final ModConfigSpec.BooleanValue vBalefireResidue;
		final ModConfigSpec.BooleanValue vDigammitite;
		final ModConfigSpec.BooleanValue vLegacyWaste;
		final ModConfigSpec.BooleanValue vDigammaCrater;
		final ModConfigSpec.BooleanValue vRtg;
		final ModConfigSpec.BooleanValue vFalloutOverhaul;
		final ModConfigSpec.BooleanValue vFalloutRain;
		final ModConfigSpec.BooleanValue vLeafiaTorex;
		final ModConfigSpec.BooleanValue vFluidTraits;
		final ModConfigSpec.BooleanValue vDfcOverhaul;
		final ModConfigSpec.BooleanValue vTungstenCrate;
		final ModConfigSpec.BooleanValue vPwr;
		final ModConfigSpec.BooleanValue vLftr;
		final ModConfigSpec.BooleanValue vZirnoxOverhaul;
		final ModConfigSpec.BooleanValue vRtgFurnaceOverhaul;
		final ModConfigSpec.BooleanValue vRadiationClassification;
		final ModConfigSpec.BooleanValue vHazardOverhaul;
		final ModConfigSpec.BooleanValue vCosmeticOverhaul;
		final ModConfigSpec.BooleanValue vRecipeOverhaul;
		final ModConfigSpec.BooleanValue vControlPanelIC10;
		final ModConfigSpec.BooleanValue vStructuralIntegrity;
		final ModConfigSpec.BooleanValue vExplosionVfx;
		final ModConfigSpec.BooleanValue vFolkvangr;
		final ModConfigSpec.BooleanValue vExplosiveGasRebalance;
		final ModConfigSpec.BooleanValue vRbmkOverhaul;
		final ModConfigSpec.BooleanValue vMeteorDiverter;
		final ModConfigSpec.BooleanValue vAddonBombs;
		final ModConfigSpec.BooleanValue vAmRifle;
		final ModConfigSpec.BooleanValue vPinkDoor;
		final ModConfigSpec.BooleanValue vLeafiaGrenades;
		final ModConfigSpec.BooleanValue vTurretFriendlyFire;
		final ModConfigSpec.BooleanValue vMeteorOverhaul;
		final ModConfigSpec.BooleanValue vMeteorStrikeRebalance;
		final ModConfigSpec.BooleanValue vMissileOverhaul;
		final ModConfigSpec.BooleanValue vLandmineOverhaul;
		final ModConfigSpec.BooleanValue vImpactSeismic;
		final ModConfigSpec.BooleanValue vSirenOverhaul;
		final ModConfigSpec.BooleanValue vLaserDetonatorOverhaul;
		final ModConfigSpec.BooleanValue vArcWelderOverhaul;
		final ModConfigSpec.BooleanValue vSolderingOverhaul;
		final ModConfigSpec.BooleanValue vFelOverhaul;
		final ModConfigSpec.BooleanValue vAntimatterContainment;
		final ModConfigSpec.BooleanValue vBarrelPorts;
		final ModConfigSpec.BooleanValue vBarrelSidePorts;
		final ModConfigSpec.BooleanValue vIndustrialBoilerTanks;
		final ModConfigSpec.BooleanValue vFirestorm;

		Values(ModConfigSpec.Builder builder) {

			builder.comment(
					"Which parts of Leafia's Cursed Addon exist.",
					"",
					"A feature switched off registers nothing at all - no block, no item, no creative",
					"tab entry - so turning one off cannot leave dead entries behind. That is also why",
					"these are read at startup rather than per world: registration happens once, before",
					"any world loads, so changing one needs a game restart.",
					"",
					"This file is NOT synced to clients. A server and a client with different switches",
					"will disagree about which blocks exist, the same way different mod versions would.")
					.push("features");

			builder.comment("Additive content. On by default - installing the jar is the opt-in.")
					.push("content");
			vElevators = builder.comment("Elevators: shaft, pulley, buffer, car and floors.")
					.define("elevators", true);
			vFfDuct = builder.comment("Electromagnetic fluid duct, its charger and flow gauges.")
					.define("electromagneticFluidDuct", true);
			vTurbines = builder.comment("Modular turbine, wind turbines and the heat exchanger.")
					.define("turbines", true);
			vAmatPipes = builder.comment("The antimatter pipe and its charger. A fluid pipe that only holds its",
							"contents while a magnetic field is powered, on a network of its own.")
					.define("antimatterPipes", true);
			vSpkCable = builder.comment("The DFC Redirection Cable: a cable for SPK, the Dark Fusion Core's own energy,",
							"on a network of its own. It does nothing until something that makes or takes SPK is placed",
							"beside it.")
					.define("spkCable", true);
			vFluidDuctEquipment = builder.comment("In-line equipment for the base mod's fluid ducts: the hand valve, the redstone",
							"valve and the flow gauge. A shut valve cuts the run of pipe in two; the gauge shows how",
							"much is moving through it, scaled to a maximum you set with a screwdriver.")
					.define("fluidDuctEquipment", true);
			vLights = builder.comment("The fluorescent light: a three-wide ceiling fixture that runs on a trickle",
							"of HE and lights the floor below it.")
					.define("lights", true);
			vAddonFluids = builder.comment("The fourteen fluids the addon adds. New fluids at ids far above the base",
							"mod's, so no existing fluid moves. The modular turbine runs on deathsteam; the",
							"rest wait on their machines - the LFTR, the DFC and the APR.")
					.define("addonFluids", true);
			vDebugBlocks = builder.comment("Upstream's debug blocks, starting with the fluid voider: a block that",
							"advertises unlimited demand to every pipe touching it and throws away",
							"whatever it is given. Upstream registers these unconditionally; turn this",
							"off to keep them out of the creative tab.")
					.define("debugBlocks", true);
			vWind = builder.comment("The wind: a strength and a direction that drift on their own timers, saved",
							"with the world. The wind turbines take their output from it and the cooling",
							"tower blows its plume downwind. Off makes the wind stand still; it does not",
							"stop it being sent, because the packet id has to stay stable either way.")
					.define("wind", true);
			vLetterSigns = builder.comment("Thirty-six large letter signs, one per digit and letter, that hang on a",
							"wall and take any dye. Decoration only.")
					.define("letterSigns", true);
			vCatwalks = builder.comment("Catwalk railings: a thin rail on any edge of a block, which joins up with",
							"its neighbours into runs, corners and caps by itself. Decoration only.")
					.define("catwalks", true);
			vLinedAsphalt = builder.comment("Fifty-five asphalt blocks with road markings, painted on with a white dye:",
							"main hand for a half line toward the edge you clicked, off hand for a full",
							"line across. Off also stops the base mod's own asphalt responding to dye.")
					.define("linedAsphalt", true);
			vBroof = builder.comment("The broof: a 3x3 green canopy you can walk straight through, with a double",
							"chest's worth of storage in it. Decoration and storage.")
					.define("broof", true);
			vCrateLabels = builder.comment("Labels on the base mod's iron, steel, desh and tungsten crates: sneak-click a side",
							"with a screwdriver to write three lines, a line on each turn of the top and bottom,",
							"and an icon on it. An unlabelled crate is unchanged.")
					.define("crateLabels", true);
			vRack = builder.comment("The storage rack: a shelf of four crate places, opened from the shelf.")
					.define("rack", true);
			vUpgradeControl = builder.comment("The control upgrade: names a radio channel and caps the machine's other",
							"upgrades at the number it hears. With nothing on the channel, they all stop.")
					.define("upgradeControl", true);
			vMixedConcrete = builder.comment("231 mixed concretes: every pair of the base mod's 22 concretes, each block",
							"showing one on one face and the other opposite, with the four faces between",
							"split half and half. Decoration only, and 231 creative-menu entries.")
					.define("mixedConcrete", true);
			vFuzzyIdentifier = builder.comment("The fuzzy identifier: a tool that remembers one fluid. Click a fluid duct",
							"to re-type the whole run it belongs to, sneak-click one to copy its fluid, or",
							"sneak-click a machine to read a fluid off it. It also answers the base mod's",
							"own fluid-identifier slots, which nothing else in the game does yet.")
					.define("fuzzyIdentifier", true);
			vHpBoiler = builder.comment("The high-pressure boiler: a three-block machine that boils a fluid up",
							"several heating steps in one pass, for less heat than the same steps would",
							"cost as separate boilers. Set the number of steps in its GUI, and set what",
							"it boils with a fluid identifier in its middle slot.")
					.define("hpBoiler", true);
			vSlopReactor = builder.comment("The slop reactor: build a 5x5x5 cube of reactor casing and glass, fill it with",
							"toxic block from uranium ingots and it makes 2,500 HE/t. It needs snowballs",
							"to cool it, and without them it melts down after thirty seconds, clearing a",
							"17-block cube around it - bedrock too - and killing everything inside.")
					.define("slopReactor", true);
			vSolBlaster = builder.comment("The Neutron Blaster: load a solinium core and four explosive lenses through its door,",
							"close it and power it, and it transmutes the fifteen items inside - schrabidium",
							"ingots into euphemium nuggets, fissium into chydalium, fissite into mysticite.",
							"Anything else radioactive becomes slaked sellafield, and food and plants are lost.")
					.define("solBlaster", true);
			vAms = builder.comment("The antimatter reaction chamber: a base, an emitter and four stabilizers that burn two fuels",
							"for power, and kill everything within ten blocks of the ball while they do. Needs a core,",
							"four catalysts and three satellite chips tuned to resonators. Without stabilizers it detonates.")
					.define("ams", true);
			vApr = builder.comment("The absorption particle reactor: a core, component blocks, a fluid port and a power outlet,",
							"built as a disc with torus chambers around it. Fails in an endothermic blast that freezes the",
							"ground, which a destroyed absorption particle capsule also sets off. Needs resourceItems and",
							"addonFluids.")
					.define("apr", true);
			vAmsp = builder.comment("The anti-mass spectrometer's analyzer and receiver: two large machine blocks that stand",
							"there. Work in progress upstream too - they have no screen, no tick and no recipe, only a",
							"model. Off registers neither.")
					.define("amsp", true);
			vDoors = builder.comment("Three animated doors: the reactor door (a swinging plug door that you paint by",
							"holding a block in the off hand and sneak-clicking with a screwdriver) and the small and",
							"large crimson doors (sliding doors that shake the camera as they shut). All three block",
							"radiation while closed.")
					.define("doors", true);
			vPwrBlocks = builder.comment("The addon's pressurised water reactor: build any shape of fuel columns, control rods,",
							"coolant channels and boilers inside a shielding vessel; it forms itself. Additive - the",
							"base mod's PWR is untouched (that is overhauls.pwr). Needs addonFluids.")
					.define("pwrBlocks", true);
			vLftrBlocks = builder.comment("The molten-salt reactor's blocks: reaction chambers, fuel plug, ejector, control rods and",
							"their extension tubes, and the arbitrary block, moving FLiBe salt in Forge tanks, and the salt",
							"fuel separation plant that separates its uranium. Additive - there is no base-mod LFTR to",
							"change. Needs addonFluids.")
					.define("lftrBlocks", true);
			vMixingVat = builder.comment("The mixing vat: stirs fuel nuggets into FLiBe salt, a bucket at a time, for the molten-salt",
							"reactor. Additive - there is no base-mod vat to change. Needs addonFluids.")
					.define("mixingVat", true);
			vCoolantHeatex = builder.comment("The coolant heat exchanger: cools a hot fluid (in a Forge tank or an ordinary one) and",
							"boils a second coolant with the heat. Additive - there is no base-mod machine to change.",
							"Needs addonFluids.")
					.define("coolantHeatex", true);
			vOilyCoal = builder.comment("Oily coal ore: a rare deep coal ore that drops two or three coal and catches",
							"fire from fire or lava beside it, becoming a burning ore that sets alight",
							"whatever walks on it. Off also stops it generating in new chunks.")
					.define("oilyCoal", true);
			vFluidBottles = builder.comment("Fluid bottles: a drinkable bottle of every NTM fluid that is not a gas or",
							"antimatter. Hot ones burn, cold ones freeze, acids, toxins and radioactive",
							"fluids do what you would expect, and a bad enough one makes you vomit.")
					.define("fluidBottles", true);
			vBatteryPacks = builder.comment("Six more battery packs, from the desh battery up to the electronium battery,",
							"which charge and discharge like the base mod's packs.")
					.define("batteryPacks", true);
			vMeltdownPill = builder.comment("\"Last Resort\", the meltdown pill: clears your radiation and mutates you for",
							"three minutes. Taking a second within an hour kills you, even across deaths.")
					.define("meltdownPill", true);
			vOsmiridiumBlocks = builder.comment("Blocks of welded osmiridium and xanaxium-etched osmiridium: almost nothing",
							"short of a black hole will scratch them.")
					.define("osmiridiumBlocks", true);
			vConcreteBricks = builder.comment("Dark concrete bricks, plus their mossy, cracked and broken finishes.")
					.define("concreteBricks", true);
			vAdvisor = builder.comment("The High-Tech Advisor: a pocket computer handed out once to every new player that",
							"warns about coal dust, asbestos, radon and lava behind you, and keeps a",
							"history of its warnings. Off also stops the free copy on first join.")
					.define("advisor", true);
			vRadGlasses = builder.comment("Radiation locator goggles: a helmet drawn as its own model. They do not yet show where the",
							"chunk radiation rises (the base mod has nothing to tell them), but they can be crafted and worn (D-3026).")
					.define("radGlasses", true);
			vRealerSim = builder.comment("RealerSim RBMK fuel channels: neutrons fly in random directions, twice as far,",
							"and bend toward absorbers and control rods. Adds the dialReasimCount gamerule.")
					.define("realerSim", true);
			vCoriumOres = builder.comment("Chernobylite and zetalite ore: corium ores that need an iron and a diamond",
							"pickaxe and leak radiation into their chunk for as long as they stand.")
					.define("coriumOres", true);
			vWorldFluids = builder.comment("Liquid oxygen, liquid fluoride, liquid concrete and the molten DFC component:",
							"four fluids a bucket pours into the world. Oxygen freezes whatever stands in it,",
							"fluoride and the DFC component burn, and concrete sets into smooth concrete.")
					.define("worldFluids", true);
			vShields = builder.comment("Six shields: steel, titanium, the SWAT shield, plutonium-238 (sets attackers alight",
							"and irradiates them), fissite (irradiates them harder) and mysticite (indestructible),",
							"the last two with a sneak-block charge. Brings the fissite and mysticite ingots.")
					.define("shields", true);
			vRegexFilter = builder.comment("The pneumatic tube regex filter: a one-slot pipe segment that only passes items",
							"whose id, item tag or creative tab matches one of its regular expressions,",
							"and pushes them into the inventory it faces. A blacklisted match always wins.")
					.define("regexFilter", true);
			vResourceItems = builder.comment("Fifty material items: the alkali ingots, xanaxium, nuclium, taintium, fissium,",
							"fissite, mysticite and chydalium as ingots, powders, billets and plates, plus blankets,",
							"anti-mass cells, particle capsules and DFC shrapnels. Nothing makes them yet, so they",
							"are creative-only. The fissite and mysticite ingots also come in with the shields.")
					.define("resourceItems", true);
			vRods = builder.comment("Fifty-nine fuel rods, one per isotope, and the empty rod assembly they are crafted from,",
							"plus five depleted fuels. A rod carries its own heat, depletion and decay on the stack: it",
							"cools in a pocket, burns whoever holds it past 80 degrees, and turns into its decay",
							"product when spent. Nothing in this jar burns them in a reactor yet.")
					.define("rods", true);
			vReactorTiers = builder.comment("The reactor tier under a reactor block's name (\"RBMK (Tier 3 / Fission Reactor)\"),",
							"on the base mod's reactor blocks too, with the tiers either side while shift is held.")
					.define("reactorTiers", true);
			vTorex = builder.comment("The torex: a mushroom cloud in nuke, balefire, digamma and endothermic colours,",
							"made of thousands of drifting puffs, with a blinding flash, a shockwave that pushes",
							"you, and the camera shake that goes with it. Spawned with /hbmleaf torex statFac",
							"and friends; /hbmleaf shake sends the shake on its own. Nothing spawns it by itself yet.",
							"It costs frame time on the client while one is alive: a big cloud is thousands of quads.")
					.define("torex", true);
			vBalefireResidue = builder.comment("Baleonite and thermonuclear ash: the rock balefire corium leaves, and the falling",
							"powder balefire burns down to. The ash burns, eats rock, catches fire again and",
							"gives balefire powder. The balefire corium itself is under worldFluids.")
					.define("balefireResidue", true);
			vDigammitite = builder.comment("Digammitite: six levels of rock that give off digamma to everything within",
							"32 blocks, cooling a level at a time.")
					.define("digammitite", true);
			vLegacyWaste = builder.comment("Contaminated dirt, gravel, sand, red sand, sandstone, red sandstone, terracotta,",
							"snow, snow blocks and ice, and scorched grass, each in up to seven levels of",
							"contamination. Fallout only makes them if overhauls.falloutOverhaul is on.")
					.define("legacyWaste", true);
			vDigammaCrater = builder.comment("The digamma crater: digamma and endothermic fallout, which the addon's own blasts",
							"leave (the digamma fuel rod's among them), and the biome a digamma blast paints - black",
							"sky, black fog close in, red water, no rain and no music, and worse in it.")
					.define("digammaCrater", true);
			vRtg = builder.comment("The radioisotope thermal heater, a heat source fed by RTG pellets and one fuel rod, and the",
							"Nuclium-279 RTG pellet with the depleted fissium pellet it decays into.")
					.define("rtg", true);
			vMeteorDiverter = builder.comment("The Meteor Protection Beacon: once its placer has been alive long enough",
							"(balance.meteor.diverterMinAliveTime), it pushes meteors out of the chunks round it.")
					.define("meteorDiverter", true);
			vAddonBombs = builder.comment("The Fat Chud bomb and the custom nuke missile (the custom nuke's slots in a",
							"missile any launch pad fires).")
					.define("addonBombs", true);
			vAmRifle = builder.comment("The anti-mass rifle: a sedna gun with a rainbow beam that ignores armour and invulnerability,",
							"a lock-on with the refractor fitted, and its own scope.")
					.define("amRifle", true);
			vControlPanel = builder.comment("The Custom Control Panel: a placeable panel with configurable controls, a node-graph",
							"editor, and the links that let it drive machines (D-3060).")
					.define("controlPanel", true);
			vPinkDoor = builder.comment("The pink door: a wooden door that any detonator turns into a very large bomb.")
					.define("pinkDoor", true);
			vLeafiaGrenades = builder.comment("Two grenade fillings: null (a 7x7x7 cube of nothing) and solinium (a radius-25 solinium blast).")
					.define("leafiaGrenades", true);
			builder.pop();

			builder.comment(
					"Changes to how the base mod behaves. OFF by default, on purpose: installing the",
					"addon should give you its content, not silently rewrite machines you already use.")
					.push("overhauls");
			vFluidTraits = builder
					.comment("The addon's fluid traits on the base mod's fluids: DFC power ratings on 22",
							"fuels, magnetic on four, and flavour text on cryogel. Off because these",
							"change what base-mod fluids say, and later what they do.")
					.define("fluidTraits", false);
			vFalloutOverhaul = builder
					.comment("Fallout makes the addon's contamination blocks (needs content.legacyWaste) instead of",
							"slaked sellafield on grass, dirt and sand, in levels that fall off with distance,",
							"and sand no longer makes trinitite right out to the edge of the blast.")
					.define("falloutOverhaul", false);
			vFalloutRain = builder
					.comment("Fallout weather: after any nuke's fallout has swept the ground, it keeps raining",
							"fallout over the blast's radius for a while, thinning out, and keeps laying fallout",
							"on the ground there.")
					.define("falloutRain", false);
			vLeafiaTorex = builder
					.comment("The addon's mushroom cloud (content.torex) in place of the base mod's, for every",
							"base-mod nuke and balefire bomb.")
					.define("leafiaTorex", false);
			vDfcOverhaul = builder
					.comment("The DFC rework - component network, pulse detonators, absorbers.")
					.define("dfcOverhaul", false);
			vTungstenCrate = builder
					.comment("The base mod's tungsten crate takes SPK from the DFC cables, and a beam's smelt also reads",
							"the DFC recipe table. Swaps the crate's block entity for the addon's.")
					.define("tungstenCrate", false);
			vPwr = builder.comment("What the addon PWR does to the base mod's: hides its blocks and replaces its",
							"assembler recipes. Not ported yet; the addon's own reactor is pwrBlocks.")
					.define("pwr", false);
			vLftr = builder.comment("Reserved for what the LFTR changes outside itself; nothing yet. The reactor's own blocks",
							"are content.lftrBlocks.")
					.define("lftr", false);
			vZirnoxOverhaul = builder.comment("Changes to the ZIRNOX reactor.")
					.define("zirnoxOverhaul", false);
			vRtgFurnaceOverhaul = builder.comment("The RTG blast furnace takes an addon fuel rod in a tenth slot, whose heat adds to its",
							"pellets', and gets the addon's screen.")
					.define("rtgFurnaceOverhaul", false);
			vRadiationClassification = builder
					.comment("Replaces item radiation values with the addon's classification system.")
					.define("radiationClassification", false);
			vHazardOverhaul = builder
					.comment("The addon's rework of base hazards: radon through a gas mask, toxic, alkaline metals, sharp edges,",
							"the nine-click geiger counter, the hot item's advisor warning and the crater biomes' underground",
							"exemption (D-3023).")
					.define("hazardOverhaul", false);
			vCosmeticOverhaul = builder
					.comment("The addon's look for base things: hot sellafield colours, darker slaked sellafield, the rod of",
							"discord's creative range, wide hazard-diamond numbers, the cooling tower's wind and the gas",
							"flame's additive glow (D-3023).")
					.define("cosmeticOverhaul", false);
			vAsphaltSpeed = builder
					.comment("The base mod's plain asphalt no longer speeds the player up, nor says it does (the lined asphalts never",
							"did). Changes a base block, so off (D-3026).")
					.define("asphaltSpeed", false);
			vRecipeOverhaul = builder
					.comment("The addon's replacements for base crafting recipes: cheap laser and multi detonators, no ice",
							"powder, digammitite 5-HTP and Xanax (D-3026).")
					.define("recipeOverhaul", false);
			vControlPanelIC10 = builder
					.comment("IC10 scripting nodes and editor for the Custom Control Panel. Additive, so on by default (D-3073);",
							"off, the node is not offered and a saved one stays an inert placeholder.")
					.define("controlPanelIC10", true);
			vExplosionVfx = builder
					.comment("The addon's balefire, balefire crater, Solinium rinse, corium (it explodes on meeting",
							"water) and chemical-thrower concrete. Upstream applies all of it unconditionally",
							"(DECISIONS D-3013).")
					.define("explosionVfx", false);
			vFolkvangr = builder
					.comment("Every F.L.E.I.J.A. blast becomes the addon's Folkvangr: a sphere erased behind the",
							"growing cloud, everything inside it killed, a vacuum afterwards (D-3013).")
					.define("folkvangr", false);
			vExplosiveGasRebalance = builder
					.comment("Explosive gas bursts at strength 2 and breaks blocks (the base mod: strength 3,",
							"blocks spared). A rebalance, so off (D-1097, D-3013).")
					.define("explosiveGasRebalance", false);
			vRbmkOverhaul = builder
					.comment("The addon's RBMK: overheated columns take damage and melt the reactor down when it is",
							"full, damaged lids jump, the AZ-5 also shuts the automatic rods off until acknowledged,",
							"four fuels glow their own colour, the melted pile smokes. Upstream applies it",
							"unconditionally (DECISIONS D-3018).")
					.define("rbmkOverhaul", false);
			vAntimatterContainment = builder
					.comment("A provider of antimatter on one of the base mod's plain fluid networks, with anything",
							"else subscribed to it, explodes and is dropped. The antimatter pipe is not affected.",
							"Upstream applies it unconditionally (DECISIONS D-3025).")
					.define("antimatterContainment", false);
			vBarrelPorts = builder
					.comment("Fluid barrels: with barrelSidePorts on they keep six ports but can only be connected",
							"from above and below; with it off they have only a top and a bottom port.",
							"The base mod's barrel has six ports and connects on every side (D-3025).")
					.define("barrelPorts", false);
			vBarrelSidePorts = builder
					.comment("Upstream's enableBarrelSidePorts, read only while barrelPorts is on (D-3025).")
					.define("barrelSidePorts", true);
			vIndustrialBoilerTanks = builder
					.comment("Industrial boilers placed from now on have tanks five times larger: 320,000 mB of",
							"water and 32,000,000 mB of steam. A rebalance (D-3025).")
					.define("industrialBoilerTanks", false);
			vFirestorm = builder
					.comment("Fire next to a chemical plant holding a flammable fluid can burn it out for good.",
							"Upstream enables this only in a development environment (D-3025).")
					.define("firestorm", false);
			vStructuralIntegrity = builder
					.comment("Floating and unsupported buildings collapse.",
							"Upstream's own warning: VERY LAGGY & CHALLENGING.")
					.define("structuralIntegrity", false);
			vMeteorOverhaul = builder
					.comment("Falling meteors shake the camera, and land in the addon's crater (with",
							"balance.meteor.customCraters) instead of the base mod's rubble (DECISIONS D-3022).")
					.define("meteorOverhaul", false);
			vMeteorStrikeRebalance = builder
					.comment("Five meteor strikes in six are called off. A rebalance, so off (D-1097, D-3022).")
					.define("meteorStrikeRebalance", false);
			vMissileOverhaul = builder
					.comment("Nuclear and EMP missiles burst in the air over their target, and other missiles",
							"shot down set their warhead off (D-3022).")
					.define("missileOverhaul", false);
			vLandmineOverhaul = builder
					.comment("Player-placed landmines kill outright, worldgen ones hit for 2.25x (capped at 11 when",
							"low), the priming click is quieter and the AP mine's trigger is a quarter as wide.",
							"Rebalances, so off (D-1097, D-3022).")
					.define("landmineOverhaul", false);
			vImpactSeismic = builder
					.comment("After the Tom impact, placed blocks are checked for support and the burning sky",
							"brings unsupported blocks down (D-3022).")
					.define("impactSeismic", false);
			vSirenOverhaul = builder
					.comment("The siren plays its track through the addon's sound with a linear falloff, and a one-shot",
							"track stops when the signal drops (DECISIONS D-3027).")
					.define("sirenOverhaul", false);
			vLaserDetonatorOverhaul = builder
					.comment("The laser detonator aims from the hand (128 blocks, 512 sneaking one-handed), draws its",
							"laser for everyone, points with a key, aims down sights and has its own model (D-3027).")
					.define("laserDetonatorOverhaul", false);
			vArcWelderOverhaul = builder
					.comment("The arc welder has the addon's sparks and sound, and shocks everything near it when wet (D-3027).")
					.define("arcWelderOverhaul", false);
			vSolderingOverhaul = builder
					.comment("The soldering station has the addon's sparks and sound (D-3027).")
					.define("solderingOverhaul", false);
			vFelOverhaul = builder
					.comment("The free-electron laser costs 4^n per wavelength, burns by its own hardness bands and feeds a",
							"DFC core SPK and heat. A rebalance, so off (D-1097, D-3027).")
					.define("felOverhaul", false);
			vTurretFriendlyFire = builder
					.comment("The Chekhov, the friendly turret and the Jeremy hold fire while something they would not",
							"target is between the barrel and the target (D-3028).")
					.define("turretFriendlyFire", false);
			builder.pop();

			builder.pop(); // features
		}
	}

	/**
	 * Copies the loaded file into the static fields above.
	 *
	 * <p>Called directly from the mod constructor after {@code registerConfig}, because a
	 * STARTUP config is already open by the time that call returns — see the class note. Do not
	 * move this onto a {@code ModConfigEvent} listener: those fire after registration, which is
	 * the whole problem this class exists to avoid.
	 */
	public static void refresh() {
		elevators = VALUES.vElevators.get();
		ffDuct = VALUES.vFfDuct.get();
		turbines = VALUES.vTurbines.get();
		amatPipes = VALUES.vAmatPipes.get();
		spkCable = VALUES.vSpkCable.get();
		fluidDuctEquipment = VALUES.vFluidDuctEquipment.get();
		lights = VALUES.vLights.get();
		addonFluids = VALUES.vAddonFluids.get();
		debugBlocks = VALUES.vDebugBlocks.get();
		wind = VALUES.vWind.get();
		letterSigns = VALUES.vLetterSigns.get();
		catwalks = VALUES.vCatwalks.get();
		linedAsphalt = VALUES.vLinedAsphalt.get();
		broof = VALUES.vBroof.get();
		controlPanel = VALUES.vControlPanel.get();
		crateLabels = VALUES.vCrateLabels.get();
		rack = VALUES.vRack.get();
		upgradeControl = VALUES.vUpgradeControl.get();
		mixedConcrete = VALUES.vMixedConcrete.get();
		fuzzyIdentifier = VALUES.vFuzzyIdentifier.get();
		hpBoiler = VALUES.vHpBoiler.get();
		slopReactor = VALUES.vSlopReactor.get();
		solBlaster = VALUES.vSolBlaster.get();
		ams = VALUES.vAms.get();
		apr = VALUES.vApr.get();
		amsp = VALUES.vAmsp.get();
		doors = VALUES.vDoors.get();
		pwrBlocks = VALUES.vPwrBlocks.get();
		lftrBlocks = VALUES.vLftrBlocks.get();
		mixingVat = VALUES.vMixingVat.get();
		coolantHeatex = VALUES.vCoolantHeatex.get();
		oilyCoal = VALUES.vOilyCoal.get();
		fluidBottles = VALUES.vFluidBottles.get();
		batteryPacks = VALUES.vBatteryPacks.get();
		meltdownPill = VALUES.vMeltdownPill.get();
		osmiridiumBlocks = VALUES.vOsmiridiumBlocks.get();
		concreteBricks = VALUES.vConcreteBricks.get();
		advisor = VALUES.vAdvisor.get();
		radGlasses = VALUES.vRadGlasses.get();
		asphaltSpeed = VALUES.vAsphaltSpeed.get();
		realerSim = VALUES.vRealerSim.get();
		coriumOres = VALUES.vCoriumOres.get();
		worldFluids = VALUES.vWorldFluids.get();
		shields = VALUES.vShields.get();
		regexFilter = VALUES.vRegexFilter.get();
		resourceItems = VALUES.vResourceItems.get();
		rods = VALUES.vRods.get();
		reactorTiers = VALUES.vReactorTiers.get();
		torex = VALUES.vTorex.get();
		balefireResidue = VALUES.vBalefireResidue.get();
		digammitite = VALUES.vDigammitite.get();
		legacyWaste = VALUES.vLegacyWaste.get();
		digammaCrater = VALUES.vDigammaCrater.get();
		rtg = VALUES.vRtg.get();
		falloutOverhaul = VALUES.vFalloutOverhaul.get();
		falloutRain = VALUES.vFalloutRain.get();
		leafiaTorex = VALUES.vLeafiaTorex.get();
		fluidTraits = VALUES.vFluidTraits.get();
		dfcOverhaul = VALUES.vDfcOverhaul.get();
		tungstenCrate = VALUES.vTungstenCrate.get();
		pwr = VALUES.vPwr.get();
		lftr = VALUES.vLftr.get();
		zirnoxOverhaul = VALUES.vZirnoxOverhaul.get();
		rtgFurnaceOverhaul = VALUES.vRtgFurnaceOverhaul.get();
		radiationClassification = VALUES.vRadiationClassification.get();
		hazardOverhaul = VALUES.vHazardOverhaul.get();
		cosmeticOverhaul = VALUES.vCosmeticOverhaul.get();
		recipeOverhaul = VALUES.vRecipeOverhaul.get();
		controlPanelIC10 = VALUES.vControlPanelIC10.get();
		structuralIntegrity = VALUES.vStructuralIntegrity.get();
		explosionVfx = VALUES.vExplosionVfx.get();
		folkvangr = VALUES.vFolkvangr.get();
		explosiveGasRebalance = VALUES.vExplosiveGasRebalance.get();
		rbmkOverhaul = VALUES.vRbmkOverhaul.get();
		meteorDiverter = VALUES.vMeteorDiverter.get();
		addonBombs = VALUES.vAddonBombs.get();
		amRifle = VALUES.vAmRifle.get();
		pinkDoor = VALUES.vPinkDoor.get();
		leafiaGrenades = VALUES.vLeafiaGrenades.get();
		turretFriendlyFire = VALUES.vTurretFriendlyFire.get();
		meteorOverhaul = VALUES.vMeteorOverhaul.get();
		meteorStrikeRebalance = VALUES.vMeteorStrikeRebalance.get();
		missileOverhaul = VALUES.vMissileOverhaul.get();
		landmineOverhaul = VALUES.vLandmineOverhaul.get();
		impactSeismic = VALUES.vImpactSeismic.get();
		sirenOverhaul = VALUES.vSirenOverhaul.get();
		laserDetonatorOverhaul = VALUES.vLaserDetonatorOverhaul.get();
		arcWelderOverhaul = VALUES.vArcWelderOverhaul.get();
		solderingOverhaul = VALUES.vSolderingOverhaul.get();
		felOverhaul = VALUES.vFelOverhaul.get();
		antimatterContainment = VALUES.vAntimatterContainment.get();
		barrelPorts = VALUES.vBarrelPorts.get();
		barrelSidePorts = VALUES.vBarrelSidePorts.get();
		industrialBoilerTanks = VALUES.vIndustrialBoilerTanks.get();
		firestorm = VALUES.vFirestorm.get();
	}
}
