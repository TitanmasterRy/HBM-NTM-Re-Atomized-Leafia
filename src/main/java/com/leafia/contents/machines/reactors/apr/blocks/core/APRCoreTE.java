package com.leafia.contents.machines.reactors.apr.blocks.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.custom_hbm.contents.torex.LCETorex;
import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.items.ModItems;
import com.hbm.sound.NtmSounds;
import com.hbm.util.DirPos;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.effects.nuke.NukeLCA;
import com.leafia.contents.effects.nuke.NukeLCA.ExplosionType;
import com.leafia.contents.fluids.traits.FT_APRCoolable;
import com.leafia.contents.fluids.traits.FT_APRCoolant;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock.APRComponentType;
import com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMainContainer;
import com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOBlock;
import com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOTE;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.math.FiaMatrix.RotationOrder;
import com.leafia.eventbuses.interfaces.INotifyEventListener;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent;

/**
 * The absorption particle reactor's core: it turns gaseous oxygen into liquid oxygen through a set of torus
 * chambers, spins a turbine with the energy, and sells the turbine's speed as power through the outlet five
 * blocks below it.
 *
 * <p>Upstream: {@code .../reactors/apr/blocks/core/APRCoreTE.java}. Every number is upstream's and is pinned by
 * {@code LeafiaAPRTests}; the oddities are kept and listed below (D-3027).
 *
 * <h2>The structure</h2>
 * The player types chamber radii into the editor (6 to 32, no two within 3 of each other); the core keeps the
 * sorted list and from it the map of every block the structure needs ({@link #rebuildMBRequirement}): a disc as
 * wide as the largest chamber one block down and five deep (plating on top and bottom, dark plating on the two
 * axes, the outlet straight below the core, mechanical parts between), four columns of gold coil round a 3x3x4
 * funnel that must be empty, and each chamber's torus. A right click on a complete structure assembles it; any
 * block change in it afterwards that makes it incomplete disassembles it ({@link #onBlockNotify}).
 *
 * <h2>The run, per tick</h2>
 * <ul>
 * <li><b>Particles</b>: a capsule in slot 0 adds 1,000 (to at most the cap, a tenth of the chambers' combined
 *     capacity) and leaves an empty capsule in slot 1. Every flow below is scaled by {@code particles / cap}.</li>
 * <li><b>The funnel</b> moves toward the requested opening at 1 a tick, between 10 and 100.</li>
 * <li><b>Coolant</b>: {@code oxygen x particles/cap / 20 x control/100} millibuckets a tick convert to liquid oxygen
 *     at the coolant's rate (gaseous oxygen 50:1); it cools the hull by {@code (fraction)^0.25 x 0.65} of its excess
 *     over 20, and its energy, {@code mB x heatDifference^0.75 / 1000}, spins the turbine and heats every chamber.</li>
 * <li><b>Chambers</b> each convert their own fluid down its whole cooling chain, at the product of the chain's
 *     rates, for {@code (sum of temperature drops)^0.65} per bucket, heating that chamber alone.</li>
 * <li><b>Turbine</b>: {@code rps += energy / 1600 / (cap/30000)^0.75 x (1 - 0.85 min((rps-30)/80, 1))}, then decays
 *     by {@code 0.99^(1/(cap/30000))}. Power out is {@code rps x cap x 100 / 3 / 20 / 5} HE a tick.</li>
 * <li><b>Heat</b>: a chamber gains {@code energy / 100 x (1 - 0.5 (T-20)/8172)} and keeps {@code 0.998} of its
 *     excess over 20; the hull follows the hottest chamber at 5% a tick.</li>
 * <li><b>Damage</b> rises by one a tick while the turbine is over 110 RPS or any chamber over 8,192 degrees, and
 *     falls by one otherwise when everything is under 500. At 600 (30 seconds) every chamber over 8,192 explodes
 *     at a random point on its ring (strength 8, no fire) and, if the turbine is over 110, the reactor disassembles.
 *     A hull over 1,538 disassembles it.</li>
 * </ul>
 *
 * <h2>Disassembly</h2>
 * Destructive when the block that broke it was a support or an assembled hull, when there is any damage, or when the
 * hull is over 1,538 - and never when it holds no particles. Destructive means: over 1,538, half the supports and
 * hulls fall back to plain hull and drop a mysticite blanket; the RBMK explosion sound at volume 100; the addon's
 * endothermic cloud and an endothermic {@link NukeLCA} of radius {@code 22 + (particles - 30000) / 3000}.
 *
 * <h2>Upstream's oddities, kept</h2>
 * <ol>
 * <li><b>A rejected chamber list empties the reactor's.</b> {@link #setChambers} clears the list before it checks
 *     the request, and on an intersection it returns without adding anything back, so the structure map is rebuilt
 *     from nothing. The editor marks intersections in red, but still sends them on close.</li>
 * <li><b>The damage only falls while the hottest chamber is under 500</b>, and "hottest" is computed as
 *     {@code max(highest, temperature)} where {@code highest} is the hottest from <em>before</em> this tick's heating
 *     - so a chamber that cooled through 500 this tick is read as still hot.</li>
 * <li>Funnel and void share packet id 5: the client reads it as the funnel, the server as the void button.</li>
 * <li>A void request empties every particle above the last whole thousand - {@code particles = 0} - and pays one
 *     empty capsule per thousand it takes out of the player's inventory, stopping silently at the first it cannot
 *     find.</li>
 * </ol>
 *
 * <p>Port differences: the structure (whether it is assembled, and the radii) also rides the block entity's update tag,
 * which reaches a client before {@code onPlayerValidate} can (that is asked for by the client's validate sweep, D-3040);
 * the "Invalid Block" boxes upstream's debug tracker drew in survival are an addon packet of their own
 * (APRHighlightPacket); the elementals' {@code radMark} is a no-op in the base mod too.
 */
public class APRCoreTE extends BlockEntityMachineBase implements LeafiaPacketReceiver, IFluidStandardTransceiverMK2, INotifyEventListener {

	public static final byte idChambers = 0;
	public static final byte idAssembled = 1;
	public static final byte idHighlight = 2;
	public static final byte idParticles = 3;
	public static final byte idParticleCap = 4;
	public static final byte idFunnel = 5;
	public static final byte idVoid = 5;
	public static final byte idFunnelDesired = 6;
	public static final byte idChamberSync = 7;
	public static final byte idPage = 8;
	public static final byte idDamage = 9;

	public static final int minChamberRadius = 6;
	public static final int maxChamberRadius = 32;
	public static final int graphXSegments = 16;
	public static final int graphRefreshRate = 10;
	public static final double controlSpeed = 20;
	public static final int maxDamage = 20 * 30;
	/** Mysticite's melting point: a blanket over this damages the reactor. */
	public static final double BLANKET_MELT = 8192;
	/** Steel's: a hull over this disassembles it. */
	public static final double HULL_MELT = 1538;
	/** Over this the turbine damages the reactor. */
	public static final double MAX_RPS = 110;
	public static final int SLOTS = 6;

	public final Map<BlockPos, BlockState> mbRequirement = new HashMap<>();
	public final List<Integer> chambers = new ArrayList<>();
	public boolean assembled = false;
	public int page = 0;
	public final List<APRChamber> chamberDatas = new ArrayList<>();

	@Nullable
	public APRChamber getChamberSafely(int index) {
		if(index < chamberDatas.size())
			return chamberDatas.get(index);
		return null;
	}

	public int graphTimer = 0;

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	public static class APRChamber {
		public FluidTank input = new FluidTank(Fluids.NONE, 0);
		public FluidTank output = new FluidTank(Fluids.NONE, 0);
		public double blanketTemp = 20;
		public double[] graph = new double[graphXSegments];

		public CompoundTag writeToNBT() {
			CompoundTag nbt = new CompoundTag();
			input.writeToNBT(nbt, "in");
			output.writeToNBT(nbt, "out");
			nbt.putDouble("temp", blanketTemp);
			return nbt;
		}

		public void readFromNBT(CompoundTag nbt) {
			input.readFromNBT(nbt, "in");
			output.readFromNBT(nbt, "out");
			blanketTemp = nbt.getDouble("temp");
		}
	}

	public double hullTemp = 20;
	public FluidTank oxygen = new FluidTank(Fluids.NONE, 0);
	public FluidTank lox = new FluidTank(Fluids.NONE, 0);
	public double particles = 0;
	public int particleCap = 0;
	public int damage = 0;
	public double rps;
	public double control = 10;
	public double targetControl = 10;
	public double lastControl = 10;
	public double controlQueue = 10;
	public int conversionInput = 1;
	public int conversionOutput = 1;

	public APRCoreTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	public APRCoreTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.APR_CORE.get(), pos, state);
	}

	public static int getAvailableCycles(int in, int out, int inAmt, int outAmt, int outCap) {
		int outFree = outCap - outAmt;
		return Math.min(inAmt / in, outFree / out);
	}

	/** Server only upstream: sizes the chambers' tanks and the main pair, and tells the clients. */
	public void onAssemble() {
		int capacity = 0;
		page = 0;
		chamberDatas.clear();
		for(Integer r : chambers) {
			int myCap = getCapacityFromRadius(r);
			APRChamber data = new APRChamber();
			data.input = new FluidTank(Fluids.NONE, myCap);
			data.output = new FluidTank(Fluids.NONE, myCap);
			chamberDatas.add(data);
			capacity += myCap;
		}
		particleCap = capacity / 10;
		setupMainTank(LeafiaFluids.OXYGEN_GAS);
		if(level != null && !level.isClientSide)
			writeState(LeafiaPacket._start(this)).__sendToAffectedClients();
	}

	public static int getCapacityFromRadius(int radius) {
		return radius * 50000;
	}

	public void setupMainTank(FluidType type) {
		int capacity = 0;
		for(Integer r : chambers)
			capacity += getCapacityFromRadius(r);
		oxygen = new FluidTank(type, capacity);
		FT_APRCoolant info = type.getTrait(FT_APRCoolant.class);
		lox = new FluidTank(info.conversion, capacity * info.out / info.in);
		conversionInput = info.in;
		conversionOutput = info.out;
	}

	public static double getHeatDifference(FluidType type) {
		if(type.hasTrait(FT_APRCoolant.class)) {
			FT_APRCoolant info = type.getTrait(FT_APRCoolant.class);
			return type.temperature - info.conversion.temperature;
		}
		if(type.hasTrait(FT_APRCoolable.class)) {
			FT_APRCoolable info = type.getTrait(FT_APRCoolable.class);
			return type.temperature - info.conversion.temperature;
		}
		if(type.hasTrait(FT_Coolable.class)) {
			FT_Coolable info = type.getTrait(FT_Coolable.class);
			return type.temperature - info.coolsTo.temperature;
		}
		return -1;
	}

	/** {@code (in, out)} millibuckets of one conversion step. */
	public static int[] getConversionRates(FluidType type) {
		if(type.hasTrait(FT_APRCoolant.class)) {
			FT_APRCoolant info = type.getTrait(FT_APRCoolant.class);
			return new int[] { info.in, info.out };
		}
		if(type.hasTrait(FT_APRCoolable.class)) {
			FT_APRCoolable info = type.getTrait(FT_APRCoolable.class);
			return new int[] { info.in, info.out };
		}
		if(type.hasTrait(FT_Coolable.class)) {
			FT_Coolable info = type.getTrait(FT_Coolable.class);
			return new int[] { info.amountReq, info.amountProduced };
		}
		return new int[] { 1, 1 };
	}

	public static FluidType getConversionTo(FluidType type) {
		if(type.hasTrait(FT_APRCoolant.class))
			return type.getTrait(FT_APRCoolant.class).conversion;
		if(type.hasTrait(FT_APRCoolable.class))
			return type.getTrait(FT_APRCoolable.class).conversion;
		if(type.hasTrait(FT_Coolable.class))
			return type.getTrait(FT_Coolable.class).coolsTo;
		return Fluids.NONE;
	}

	public static double getFinalHeatDifference(FluidType type) {
		double difference = 0;
		while(isValidFluid(type)) {
			difference += getHeatDifference(type);
			type = getConversionTo(type);
		}
		return difference;
	}

	public static int[] getFinalConversionRates(FluidType type) {
		int in = 1;
		int out = 1;
		while(isValidFluid(type)) {
			int[] rate = getConversionRates(type);
			in *= rate[0];
			out *= rate[1];
			type = getConversionTo(type);
		}
		return new int[] { in, out };
	}

	public static FluidType getFinalConversionTo(FluidType type) {
		FluidType output = Fluids.NONE;
		while(isValidFluid(type)) {
			output = getConversionTo(type);
			type = output;
		}
		return output;
	}

	public static double getEnergy(FluidType type) {
		return getEnergy(getHeatDifference(type));
	}

	public static double getFinalEnergy(FluidType type) {
		return Math.pow(getFinalHeatDifference(type), 0.65);
	}

	public static double getEnergy(double heatDifference) {
		return Math.pow(heatDifference, 0.75);
	}

	public static double energyFor(double energy, int amount) {
		return amount * energy / 1000;
	}

	public void addRPSFromEnergy(double energy) {
		double ratio = Math.min((rps - 30) / (110 - 30), 1);
		double neg = 1 - ratio * 0.85;
		rps += energy / 1600 / Math.pow(particleCap / 30000d, 0.75) * neg;
	}

	public int accountForParticles(int amt) {
		return (int) (amt * (particles / particleCap));
	}

	public void addHeatFromEnergy(double energy, APRChamber chamber) {
		double ratio = (chamber.blanketTemp - 20) / (8192 - 20);
		double neg = 1 - ratio * 0.5;
		double addition = energy / 100 * neg;
		chamber.blanketTemp += addition;
	}

	public void addHeatFromEnergy(double energy, List<APRChamber> chambers) {
		for(APRChamber chamber : chambers)
			addHeatFromEnergy(energy, chamber);
	}

	public void sortChambers() {
		chambers.sort(Comparator.naturalOrder());
	}

	static int[] toArray(List<Integer> radii) {
		int[] array = new int[radii.size()];
		for(int i = 0; i < array.length; i++)
			array[i] = radii.get(i);
		return array;
	}

	public void rebuildMBRequirement() {
		sortChambers();
		mbRequirement.clear();
		APRComponentBlock apr = LeafiaBlocks.apr_component.get();
		if(!chambers.isEmpty()) {
			// DISC
			BlockState plate = apr.state(APRComponentType.PLATING);
			BlockState dark = apr.state(APRComponentType.PLATING_DARK);
			BlockState parts = apr.state(APRComponentType.MECHANICAL);
			int radius = chambers.get(chambers.size() - 1);
			for(int x = -radius; x <= radius; x++) {
				for(int z = -radius; z <= radius; z++) {
					BlockPos p = new BlockPos(worldPosition.getX() + x, worldPosition.getY(), worldPosition.getZ() + z);
					// 1.12.2's distanceSq is corner to corner, so this is sqrt(x^2 + z^2)
					if(Math.sqrt(p.distSqr(worldPosition)) <= radius) {
						BlockState cover = (x == 0 || z == 0) ? dark : plate;
						if(x == 0 && z == 0) cover = LeafiaBlocks.apr_outlet.get().defaultBlockState();
						mbRequirement.put(p.below(), cover);
						mbRequirement.put(p.below(2), parts);
						mbRequirement.put(p.below(3), parts);
						mbRequirement.put(p.below(4), parts);
						mbRequirement.put(p.below(5), cover);
					}
				}
			}
			// COILS
			BlockState coil = apr.state(APRComponentType.COIL);
			for(int x = -1; x <= 1; x++) {
				for(int y = -1; y >= -4; y--) {
					mbRequirement.put(worldPosition.offset(x, y, 2), coil);
					mbRequirement.put(worldPosition.offset(x, y, -2), coil);
				}
			}
			for(int z = -1; z <= 1; z++) {
				for(int y = -1; y >= -4; y--) {
					mbRequirement.put(worldPosition.offset(2, y, z), coil);
					mbRequirement.put(worldPosition.offset(-2, y, z), coil);
				}
			}
			// FUNNEL CHAMBER
			for(int x = -1; x <= 1; x++) {
				for(int z = -1; z <= 1; z++) {
					for(int y = -1; y >= -4; y--)
						mbRequirement.put(worldPosition.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for(Integer r : chambers)
			APRCoreBlock.generateTorus(worldPosition, r, mbRequirement);
	}

	@Override
	public String getName() {
		return "container.apr";
	}

	// ---- save ------------------------------------------------------------------------------

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		assembled = nbt.getBoolean("assembled");
		setChambers(nbt.getIntArray("radius"), false);
		if(chambers.isEmpty())
			assembled = false; // upstream: "fuck you"
		if(assembled)
			onAssemble();
		readChamberData(nbt);
		particles = nbt.getDouble("particles");
		damage = nbt.getInt("damage");
		if(nbt.contains("convIn"))
			conversionInput = nbt.getInt("convIn");
		if(nbt.contains("convOut"))
			conversionOutput = nbt.getInt("convOut");
		if(nbt.contains("controlC"))
			control = nbt.getDouble("controlC");
		if(nbt.contains("controlD"))
			targetControl = nbt.getDouble("controlD");
		page = nbt.getInt("page");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		nbt.putBoolean("assembled", assembled);
		nbt.putIntArray("radius", toArray(chambers));
		addChamberData(nbt);
		nbt.putDouble("particles", particles);
		nbt.putInt("damage", damage);
		nbt.putInt("convIn", conversionInput);
		nbt.putInt("convOut", conversionOutput);
		nbt.putDouble("controlC", control);
		nbt.putDouble("controlD", targetControl);
		nbt.putInt("page", page);
		super.saveAdditional(nbt, registries);
	}

	/** Not upstream's: the structure for a client joining after it was built (see the class note). */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("assembled", assembled);
		tag.putIntArray("radius", toArray(chambers));
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		readStructure(tag);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		readStructure(pkt.getTag());
	}

	private void readStructure(CompoundTag tag) {
		assembled = tag.getBoolean("assembled");
		setChambers(tag.getIntArray("radius"), false);
	}

	// ---- the packet ------------------------------------------------------------------------

	@Override
	public String getPacketIdentifier() {
		return "APR_CORE";
	}

	LeafiaPacket.Builder writeState(LeafiaPacket.Builder packet) {
		return packet.__write(idAssembled, assembled).__write(idChambers, toArray(chambers));
	}

	@Override
	public void onPlayerValidate(Player plr) {
		writeState(LeafiaPacket._start(this)).__sendToClient(plr);
	}

	/** The editor's request, sent as it closes. */
	public void requestChambers(List<Integer> edited, boolean highlightOnClose) {
		LeafiaPacket._start(this).__write(idChambers, toArray(edited)).__write(idHighlight, highlightOnClose).__sendToServer();
	}

	public void addChamberData(CompoundTag nbt) {
		oxygen.writeToNBT(nbt, "in");
		lox.writeToNBT(nbt, "out");
		nbt.putDouble("hull", hullTemp);
		nbt.putDouble("rps", rps);
		ListTag list = new ListTag();
		for(APRChamber data : chamberDatas)
			list.add(data.writeToNBT());
		nbt.put("chambers", list);
	}

	public void readChamberData(CompoundTag nbt) {
		if(nbt.contains("in"))
			oxygen.readFromNBT(nbt, "in");
		if(nbt.contains("out"))
			lox.readFromNBT(nbt, "out");
		if(nbt.contains("hull"))
			hullTemp = nbt.getDouble("hull");
		if(nbt.contains("rps"))
			rps = nbt.getDouble("rps");
		if(nbt.contains("chambers")) {
			ListTag list = nbt.getList("chambers", Tag.TAG_COMPOUND);
			for(int i = 0; i < list.size(); i++) {
				if(chamberDatas.size() > i)
					chamberDatas.get(i).readFromNBT(list.getCompound(i));
				else
					break;
			}
		}
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case idAssembled -> assembled = (boolean) value;
			case idChambers -> setChambers(value, false);
			case idParticles -> particles = (double) value;
			case idParticleCap -> particleCap = (int) value;
			case idFunnel -> controlQueue = (double) value;
			case idFunnelDesired -> targetControl = (double) value;
			case idChamberSync -> readChamberData((CompoundTag) value);
			case idPage -> page = (int) value;
			case idDamage -> damage = (int) value;
			default -> { }
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key == idFunnelDesired) {
			// D-1094: the screen only ever sends 10 to 100; a client's number is not believed past that
			if(value instanceof Double d && Double.isFinite(d)) targetControl = Mth.clamp(d, 10, 100);
		} else if(key == idChambers) {
			if(assembled) return;
			setChambers(value, true);
			setChanged();
			writeState(LeafiaPacket._start(this)).__sendToAffectedClients();
			rebuildMBRequirement();
		} else if(key == idHighlight && Boolean.TRUE.equals(value)) {
			highlight(plr);
		} else if(key == idPage) {
			if(value instanceof Integer i) page = i;
		} else if(key == idVoid) {
			voidParticles(plr);
		}
	}

	/**
	 * Upstream's highlight button: a creative player has the whole structure built for them; anyone else is shown a
	 * red box round every block in the way.
	 */
	public void highlight(Player plr) {
		List<BlockPos> invalid = new ArrayList<>();
		for(Entry<BlockPos, BlockState> entry : mbRequirement.entrySet()) {
			if(plr.isCreative()) {
				level.setBlock(entry.getKey(), entry.getValue(), 3);
				continue;
			}
			if(!level.isEmptyBlock(entry.getKey())) {
				if(!isValidBlock(entry.getKey()))
					invalid.add(entry.getKey());
			}
		}
		if(!invalid.isEmpty() && plr instanceof ServerPlayer sp)
			APRHighlightPacket.send(sp, invalid);
	}

	/** Upstream's void button. */
	public void voidParticles(Player plr) {
		for(APRChamber data : chamberDatas) {
			if(data.blanketTemp > 100)
				return;
		}
		if(!slots[0].isEmpty())
			return;
		int req = (int) (particles / 1000);
		int slot = 0;
		List<ItemStack> stacks = plr.getInventory().items;
		for(int i = 0; i < req; i++) {
			while(slot < stacks.size()) {
				if(!stacks.get(slot).isEmpty()) {
					if(stacks.get(slot).getItem() == ModItems.particle_empty.get())
						break;
				}
				slot++;
			}
			if(slot >= stacks.size()) {
				// fail
				plr.inventoryMenu.broadcastChanges();
				return;
			}
			if(!stacks.get(slot).isEmpty()) {
				if(stacks.get(slot).getItem() == ModItems.particle_empty.get()) {
					particles -= 1000;
					stacks.get(slot).shrink(1);
					ItemStack capsule = new ItemStack(LeafiaResourceItems.get("particle_absorption").get());
					if(!plr.getInventory().add(capsule))
						plr.drop(capsule, false);
				}
			}
		}
		plr.inventoryMenu.broadcastChanges();
		particles = 0;
	}

	public boolean checkAssembled() {
		if(chambers.isEmpty()) return false;
		for(BlockPos bp : mbRequirement.keySet()) {
			if(!isValidBlock(bp))
				return false;
		}
		return true;
	}

	/** Upstream's: clamps each radius to 1 to 32, sorts, and with {@code canReject} gives up on any two within 3. */
	public void setChambers(Object received, boolean canReject) {
		this.chambers.clear();
		List<Integer> chambers = new ArrayList<>();
		if(received instanceof int[] radii) {
			for(int r : radii)
				chambers.add(Mth.clamp(r, 1, maxChamberRadius));
		}
		chambers.sort(Comparator.naturalOrder());
		int lastR = 0;
		for(Integer r : chambers) {
			if(r - lastR <= 3 && canReject)
				return;
			lastR = r;
		}
		this.chambers.addAll(chambers);
		rebuildMBRequirement();
	}

	private AABB bb = null;

	/** Upstream's render box: everything while unassembled (the ghosts), a 7 x 5 x 7 box round the core after. Asked by {@link APRCoreRender}. */
	public AABB getRenderBoundingBox() {
		if(!assembled) return AABB.INFINITE;
		if(bb == null) {
			bb = new AABB(worldPosition.getX() - 3, worldPosition.getY() - 2, worldPosition.getZ() - 3,
					worldPosition.getX() + 4, worldPosition.getY() + 3, worldPosition.getZ() + 4);
		}
		return bb;
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		if(!assembled) return null;
		return new APRMainContainer(containerId, playerInventory, this);
	}

	/** Slots take anything by hand; upstream's sides expose none. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	@Override
	public double affectionRange() {
		return 256;
	}

	public boolean isValidBlock(BlockPos pos) {
		BlockPos center = worldPosition;
		Vec3i relative = pos.subtract(center);
		if(Math.abs(relative.getX()) <= 1 && Math.abs(relative.getZ()) <= 1 && relative.getY() <= -1 && relative.getY() >= -4) {
			BlockState state = level.getBlockState(pos);
			// 1.12.2: mat.isReplaceable() && !mat.isSolid() && !mat.isLiquid()
			return state.canBeReplaced() && !state.blocksMotion() && state.getFluidState().isEmpty();
		}
		BlockState desired = mbRequirement.get(pos);
		if(desired != null) {
			BlockState state = level.getBlockState(pos);
			if(state.equals(desired)) return true;
			if(desired.getBlock() == LeafiaBlocks.apr_component.get()) {
				APRComponentType want = desired.getValue(APRComponentBlock.VARIANT);
				if(want == APRComponentType.PLATING || want == APRComponentType.PLATING_DARK) {
					if(state.getBlock() == LeafiaBlocks.apr_component.get()) {
						APRComponentType have = state.getValue(APRComponentBlock.VARIANT);
						if(have == APRComponentType.PLATING || have == APRComponentType.PLATING_DARK)
							return true;
					}
				} else if(want == APRComponentType.SUPPORT) {
					if(state.getBlock() instanceof APRFluidIOBlock) {
						if(level.getBlockEntity(pos) instanceof APRFluidIOTE fte)
							fte.targetPos = worldPosition;
						return true;
					}
				}
			}
			return false;
		}
		return true;
	}

	public static boolean isValidCoolant(FluidType type) {
		return type.hasTrait(FT_APRCoolant.class);
	}

	public static boolean isValidFluid(FluidType type) {
		if(type.hasTrait(FT_APRCoolant.class))
			return true;
		if(type.hasTrait(FT_APRCoolable.class))
			return true;
		return type.hasTrait(FT_Coolable.class);
	}

	/** A random point on a random face of {@code bp}: upstream's, side faces included. */
	public Vec3 getRandomVector(BlockPos bp) {
		Direction face = Direction.from3DDataValue(level.random.nextInt(6));
		if(face == Direction.UP)
			return new Vec3(bp.getX() + level.random.nextDouble(), bp.getY() + 1, bp.getZ() + level.random.nextDouble());
		else if(face == Direction.DOWN)
			return new Vec3(bp.getX() + level.random.nextDouble(), bp.getY(), bp.getZ() + level.random.nextDouble());
		else {
			Direction right = face.getClockWise();
			return new Vec3(
					bp.getX() + 0.5 + face.getStepX() / 2d + (right.getStepX() * level.random.nextDouble() - 0.5),
					bp.getY() + level.random.nextDouble(),
					bp.getZ() + 0.5 + face.getStepZ() / 2d + (right.getStepZ() * level.random.nextDouble() - 0.5));
		}
	}

	/** The screen's lever: 10 to 100 over 33 pixels (upstream's {@code getLeverOffset}). */
	public static int leverOffset(double control) {
		return (int) ((control - 10) / 90 * (37 - 4));
	}

	/** The screen's drag: the lever's value for a mouse {@code relX} pixels into the screen, clamped to 10..100. */
	public static double controlFromMouse(int relX) {
		double control = (relX - 138 + 2) / (37d - 4);
		control = control * 90 + 10;
		return Math.max(10, Math.min(control, 100));
	}

	/** The editor's rule for one typed radius: {@code MALFORMED}, {@code TOO_SMALL}, {@code TOO_LARGE}, or null. */
	@Nullable
	public static String radiusError(String text) {
		try {
			int v = Integer.parseInt(text);
			if(v < minChamberRadius)
				return "TOO_SMALL";
			else if(v > maxChamberRadius)
				return "TOO_LARGE";
			return null;
		} catch(NumberFormatException ignored) {
			return "MALFORMED";
		}
	}

	public long getPowerOutput() {
		return (long) (rps * particleCap * 100 / 3 / 20 / 5);
	}

	// ---- client ----------------------------------------------------------------------------

	LCEAudioWrapper controlSnd = null;
	LCEAudioWrapper turbineSnd = null;

	/** Upstream's turbine falloff: full to the outermost ring, then quadratically to nothing 30 blocks past it, at a quarter. */
	public double attenuationFunc(float vol, double dist) {
		double radius = 1;
		if(!chambers.isEmpty())
			radius = chambers.get(chambers.size() - 1);
		double maxRadius = radius + 30;
		double ratio = (dist - radius) / (maxRadius - radius);
		return Math.pow(1 - Math.min(Math.max(ratio, 0), 1), 2) * 0.25 * vol;
	}

	boolean controlSoundPlaying = false;
	public int stressSoundTimer = 0;
	public double stressTimer = 300;

	private void stopSounds() {
		if(controlSnd != null)
			controlSnd.stopSound();
		controlSnd = null;
		if(turbineSnd != null)
			turbineSnd.stopSound();
		turbineSnd = null;
	}

	@Override
	public void onChunkUnloaded() {
		stopSounds();
		super.onChunkUnloaded();
	}

	@Override
	public void setRemoved() {
		stopSounds();
		com.leafia.eventbuses.NotifyEventListeners.unsubscribe(this);
		super.setRemoved();
	}

	public void updateLocal() {
		float x = worldPosition.getX() + 0.5f, y = worldPosition.getY() + 0.5f, z = worldPosition.getZ() + 0.5f;
		if(turbineSnd == null) {
			controlSnd = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.pwrRodLoop.get(), LeafiaSounds.pwrRodStart.get(),
					LeafiaSounds.pwrRodStop.get(), SoundSource.BLOCKS, x, y, z, 0.15f, 0.75f);
			turbineSnd = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.modular_turbine.get(), null, null, SoundSource.BLOCKS,
					x, y, z, 0.01f, 0.5f).setLooped(true).setCustomAttenuation(this::attenuationFunc).startSound();
		}
		if(control != targetControl) {
			if(!controlSoundPlaying) {
				controlSoundPlaying = true;
				controlSnd.startSound();
			}
		} else {
			if(controlSoundPlaying) {
				controlSoundPlaying = false;
				controlSnd.stopSound();
			}
		}
		float ratio = (float) (Math.pow(rps / 60, 0.75));
		turbineSnd.updatePitch(0.5f + ratio);
		turbineSnd.updateVolume(ratio);
		if(stressSoundTimer <= 0) {
			stressSoundTimer = 70;
			double vol = Math.pow((Math.max(rps - 65, 0) / 35), 3) * 0.8;
			if(vol > 0)
				level.playLocalSound(worldPosition, LeafiaSounds.pipestressed.get(), SoundSource.BLOCKS, (float) vol, 0.65f, false);
		}
		stressSoundTimer--;
		double stress = damage / (double) maxDamage;
		stressTimer -= Math.pow(stress, 0.9) * 64;
		if(stressTimer <= 0)
			level.playLocalSound(x, y, z, LeafiaSounds.stressSounds[level.random.nextInt(7)].get(), SoundSource.BLOCKS,
					(float) Mth.clampedLerp(0.25, 14, Math.pow(stress, 4)), 1.0F, false);
		if(hullTemp >= 650) {
			for(Entry<BlockPos, BlockState> entry : mbRequirement.entrySet()) {
				if(entry.getValue().getBlock() == LeafiaBlocks.apr_component.get()) {
					if(entry.getValue().getValue(APRComponentBlock.VARIANT) == APRComponentType.HULL_ASSEMBLED) {
						if(level.random.nextInt(hullTemp > 850 ? 20 : 150) == 0) {
							Vec3 ranVec = getRandomVector(entry.getKey());
							level.addParticle(ParticleTypes.LARGE_SMOKE, ranVec.x, ranVec.y, ranVec.z, 0, 0, 0);
						}
						if(hullTemp >= 1100 && level.random.nextInt(10) == 0) {
							Vec3 ranVec = getRandomVector(entry.getKey());
							level.addParticle(ParticleTypes.FLAME, ranVec.x, ranVec.y, ranVec.z, 0, 0, 0);
						}
					}
				}
			}
		}
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {
		update();
	}

	public void update() {
		if(level.isClientSide) {
			lastControl = control;
			control = controlQueue;
			if(chamberDatas.size() != chambers.size()) {
				chamberDatas.clear();
				for(int i = 0; i < chambers.size(); i++)
					chamberDatas.add(new APRChamber());
			}
			graphTimer++;
			if(graphTimer >= graphRefreshRate) {
				graphTimer = 0;
				for(APRChamber data : chamberDatas) {
					for(int i = 0; i < graphXSegments - 1; i++)
						data.graph[i] = data.graph[i + 1];
					double sub = data.blanketTemp - 20;
					data.graph[graphXSegments - 1] = lerp(data.blanketTemp, sub * (level.random.nextDouble() * (sub / (8192 - 20)) * 0.75 + 0.35) + 20,
							particles / (double) particleCap);
				}
			}
			updateLocal();
			return;
		}
		subscribeToBlockNotification();
		tickServer();
		if(assembled) setChanged();
	}

	/** The server's half of a tick, upstream's {@code update()} after the client branch. */
	public void tickServer() {
		ItemStack particleIn = slots[0];
		if(!particleIn.isEmpty() && particleIn.getItem() == LeafiaResourceItems.get("particle_absorption").get() && particles < particleCap) {
			ItemStack stack = slots[1];
			if(stack.isEmpty() || (stack.getItem() == ModItems.particle_empty.get() && stack.getCount() < stack.getMaxStackSize())) {
				int count = stack.getCount() + 1;
				particleIn.shrink(1);
				particles = Math.min(particles + 1000, particleCap);
				slots[1] = new ItemStack(ModItems.particle_empty.get(), count);
			}
		}
		if(LeafiaUtil.setTypeBy(oxygen, 2, 3, slots, APRCoreTE::isValidCoolant, oxygen.getTankType()))
			setupMainTank(oxygen.getTankType());

		if(assembled) {
			if(Math.abs(targetControl - control) < controlSpeed / 20)
				control = targetControl;
			else
				control += Math.signum(targetControl - control) * (controlSpeed / 20);
			double highest = 0;
			for(APRChamber chamber : chamberDatas)
				highest = Math.max(highest, chamber.blanketTemp);
			hullTemp = hullTemp + (highest - hullTemp) * 0.05;
			int cycles = getAvailableCycles(conversionInput, conversionOutput, (int) (accountForParticles(oxygen.getFill()) / 20d * (control / 100)),
					lox.getFill(), lox.getMaxFill());
			if(cycles > 0) {
				oxygen.setFill(oxygen.getFill() - cycles * conversionInput);
				lox.setFill(lox.getFill() + cycles * conversionOutput);
				double fah = (cycles * conversionInput) / (double) oxygen.getMaxFill();
				hullTemp = (hullTemp - 20) * (1 - Math.pow(Math.max(fah, 0), 0.25) * 0.65) + 20;
				double energy = energyFor(getEnergy(oxygen.getTankType()), cycles * conversionInput);
				if(energy > 0) {
					addRPSFromEnergy(energy);
					addHeatFromEnergy(energy, chamberDatas);
				}
			}
			rps *= Math.pow(0.99, 1 / (particleCap / 30000d));
			int curPage = 0;
			boolean damaging = rps > MAX_RPS;
			double highestTemp = 0;
			for(APRChamber data : chamberDatas) {
				if(curPage == page && LeafiaUtil.setTypeBy(data.input, 4, 5, slots, APRCoreTE::isValidFluid, data.input.getTankType()))
					data.output.setTankType(getFinalConversionTo(data.input.getTankType()));
				int[] rate = getFinalConversionRates(data.input.getTankType());
				int cyclesProcess = getAvailableCycles(rate[0], rate[1], accountForParticles(data.input.getFill()), data.output.getFill(), data.output.getMaxFill());
				if(cyclesProcess > 0) {
					data.input.setFill(data.input.getFill() - cyclesProcess * rate[0]);
					data.output.setFill(data.output.getFill() + cyclesProcess * rate[1]);
					double energy = energyFor(getFinalEnergy(data.input.getTankType()), cyclesProcess * rate[0]);
					if(energy > 0) {
						addRPSFromEnergy(energy);
						addHeatFromEnergy(energy, data);
					}
				}
				data.blanketTemp = (data.blanketTemp - 20) * 0.998 + 20;
				curPage++;
				highestTemp = Math.max(highest, data.blanketTemp);
				if(data.blanketTemp > BLANKET_MELT)
					damaging = true;
			}
			if(damaging) {
				damage++;
				if(damage > maxDamage) {
					// adios
					damage = maxDamage;
					for(int i = 0; i < chambers.size(); i++) {
						if(chamberDatas.get(i).blanketTemp > BLANKET_MELT) {
							FiaMatrix mat = new FiaMatrix(new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5));
							mat = mat.rotate(RotationOrder.XYZ, 0, level.random.nextDouble() * 360, 0).translate(0, 0, -chambers.get(i));
							// 1.12.2 createExplosion(null, x, y, z, 8, true): the flag is smoke and block damage, not fire
							level.explode(null, mat.getX(), mat.getY(), mat.getZ(), 8, false, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
						}
					}
					if(rps > MAX_RPS)
						disassemble(worldPosition);
				}
			} else if(highestTemp < 500)
				damage = Math.max(damage - 1, 0);
			if(hullTemp > HULL_MELT)
				disassemble(worldPosition);
		} else
			rps = 0;

		tryProvide(lox.getTankType(), level, new DirPos(worldPosition.above(2), Direction.UP));
		CompoundTag nbt = new CompoundTag();
		addChamberData(nbt);
		LeafiaPacket._start(this)
				.__write(idAssembled, assembled)
				.__write(idParticles, particles)
				.__write(idParticleCap, particleCap)
				.__write(idFunnel, control)
				.__write(idFunnelDesired, targetControl)
				.__write(idChamberSync, nbt)
				.__write(idPage, page)
				.__write(idDamage, damage)
				.__sendToAffectedClients();
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { lox };
	}

	@Override
	public FluidTank[] getAllTanks() {
		FluidTank[] tanks = new FluidTank[chamberDatas.size() * 2 + 2];
		tanks[0] = oxygen;
		tanks[1] = lox;
		for(int i = 0; i < chamberDatas.size(); i++) {
			tanks[2 + i * 2] = chamberDatas.get(i).input;
			tanks[2 + i * 2 + 1] = chamberDatas.get(i).output;
		}
		return tanks;
	}

	@Override
	public void onBlockNotify(NeighborNotifyEvent evt) {
		if(assembled && evt.getLevel() == level && !isValidBlock(evt.getPos()))
			disassemble(evt.getPos());
	}

	/** Upstream's blast radius: {@code 22 + (particles - 30000) / 600 / 5}. */
	public static double blastRadius(double particles) {
		return 22 + (particles - 30000) / 600d / 5;
	}

	/** Whether a disassembly caused at {@code p} would be the blast; upstream's rules, in upstream's order. */
	public boolean isDestructive(BlockPos p) {
		boolean destructive = false;
		if(mbRequirement.containsKey(p)) {
			BlockState state = mbRequirement.get(p);
			if(state.getBlock() == LeafiaBlocks.apr_component.get()) {
				APRComponentType type = state.getValue(APRComponentBlock.VARIANT);
				if(type == APRComponentType.SUPPORT || type == APRComponentType.HULL_ASSEMBLED)
					destructive = true;
			}
		}
		// misc condition here (such as damage)
		if(damage > 0)
			destructive = true;
		if(hullTemp > HULL_MELT)
			destructive = true;
		// check if particle tank is empty here
		if(particles <= 0)
			destructive = false;
		return destructive;
	}

	public void disassemble(BlockPos p) {
		if(!assembled) return;
		if(level.isClientSide) return;
		assembled = false;
		if(isDestructive(p)) {
			if(hullTemp > HULL_MELT) {
				for(Entry<BlockPos, BlockState> entry : new ArrayList<>(mbRequirement.entrySet())) {
					BlockState state = entry.getValue();
					if(state.getBlock() == LeafiaBlocks.apr_component.get()) {
						APRComponentType type = state.getValue(APRComponentBlock.VARIANT);
						if(type == APRComponentType.SUPPORT || type == APRComponentType.HULL_ASSEMBLED) {
							if(level.random.nextBoolean()) {
								level.setBlock(entry.getKey(), LeafiaBlocks.apr_component.get().state(APRComponentType.HULL), 3);
								Containers.dropItemStack(level, entry.getKey().getX() + 0.5, entry.getKey().getY() + 0.5, entry.getKey().getZ() + 0.5,
										new ItemStack(LeafiaResourceItems.get("blanket_mysticite").get()));
							}
						}
					}
				}
			}
			double radius = blastRadius(particles);
			level.playSound(null, p, NtmSounds.BLOCK_RBMK_EXPLOSION.get(), SoundSource.BLOCKS, 100, 1);
			LCETorex.statFacEndo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, (float) radius);
			NukeLCA nuke = NukeLCA.statFac(level, (int) radius, p.getX() + 0.5, p.getY(), p.getZ() + 0.5).setExplosionType(ExplosionType.ENDOTHERMIC);
			level.addFreshEntity(nuke);
			// nukeapr for every player within 200 of the core (D-3040); upstream's radMark is a no-op in the base mod
			com.leafia.init.LeafiaAdvancements.grantWithin(level, new net.minecraft.world.phys.AABB(p).inflate(200), com.leafia.init.LeafiaAdvancements.NUKE_APR);
		}
	}
}
