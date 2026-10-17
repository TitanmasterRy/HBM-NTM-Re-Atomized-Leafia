package com.leafia.contents.machines.powercores.dfc.components.exchanger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The DFC heat exchanger: a component that cools the core by boiling coolant with it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/exchanger/CoreExchangerTE.java}, on
 * the addon block {@code dfc_exchanger}. Its MK1 {@code IFluidStandardSender}/{@code Receiver} are the MK2
 * transceiver here (D-300: MK1 is a deprecated alias over MK2).
 *
 * <h2>What it does</h2>
 *
 * Every {@code tickDelay} ticks it subscribes its input tank and, if the core it is aimed at is hotter than
 * the output fluid, boils up to {@code amountToHeat} mB, in whole batches of the chain's input, into its
 * output: {@code (core - output temperature) / heatAmt * 1,000} mB at most, where {@code heatAmt} is the
 * output fluid's temperature less the input's. The core loses {@code drain * heatAmt / 1,000 / 20}
 * degrees for it, floored at zero. It sends its output on every tick.
 *
 * <p>The chain is the fluid's {@code FT_Heatable} first steps followed {@code compression} times (typed into
 * the screen, upstream's default 1), the amounts carried to a common multiple so that {@code inAmt} mB of
 * the first fluid become {@code outAmt} mB of the last. Coolant becomes hot coolant at 1:1 and is the
 * default. An identifier in the slot sets the input (and empties a tank of another fluid).
 *
 * <h2>Upstream oddities, kept (rule 4)</h2>
 *
 * <ul>
 * <li><b>The cooling is a twentieth of the heat.</b> {@code maxDrain} uses the full {@code heatAmt / 1,000}
 *     per mB but the core is cooled by a twentieth of that; mlbv removed the {@code / 20} as a bug and
 *     ntmleafia put it back ("if i divide maxDrain by 20 it just rounds down to 0"). So an exchanger
 *     always takes twenty times the fluid the heat it removes would need.</li>
 * <li>{@code drain * heatAmt} is an {@code int} product and overflows past about two billion.</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * The screen's three numbers arrive from the client; upstream floors two at 1 and lets the third reach
 * {@code setInput}. Here all three need this exchanger's screen open and an {@code Integer}, as upstream a compression below 1 changes nothing (D-1094). {@code IControllable} is D-1093 #8.
 */
public class CoreExchangerTE extends BlockEntityMachineBase implements IDFCBase, IFluidStandardTransceiverMK2, IDFCClientTicked, IControllable {

	public FluidTank input = new FluidTank(Fluids.COOLANT, 2560_000);
	public FluidTank output = new FluidTank(Fluids.COOLANT_HOT, 2560_000);
	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	int inAmt = 1;
	int outAmt = 1;
	public int compression = 1;
	public int amountToHeat = 1;
	public int tickDelay = 1;
	int timer = 0;
	@Nullable
	public BlockEntityCore lastGetCore = null;
	final Set<Player> listeners = new LinkedHashSet<>();
	protected final DFCParts.Hum hum = new DFCParts.Hum(false);

	public CoreExchangerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 1);
	}

	/** A heating chain: how many steps it took, the fluid it ends on, and the batch in and out. */
	public record Chain(int steps, FluidType produced, int in, int out) { }

	/** Upstream's {@code getBoiledFluid}. */
	public static Chain getBoiledFluid(FluidType f, int compression) {
		int comp = 0;
		FluidType cur = f;
		long in = 0;
		long out = 0;

		for(int i = 0; i < compression; i++) {
			FT_Heatable trait = cur.getTrait(FT_Heatable.class);
			if(trait == null) break;

			FT_Heatable.HeatingStep step = trait.getFirstStep();
			if(step == null || step.typeProduced == null) break;
			long reqPrev = step.amountReq;
			long prodCur = step.amountProduced;
			if(reqPrev <= 0L || prodCur <= 0L) break;

			if(comp == 0) {
				in = reqPrev;
				out = prodCur;
			} else {
				long l = lcm(in, prodCur);
				long scaleExisting = l / in;
				long batches = l / prodCur;
				in = batches * reqPrev;
				out = out * scaleExisting;
			}

			cur = step.typeProduced;
			comp++;
		}

		int inI = in > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) in;
		int outI = out > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) out;
		return new Chain(comp, cur, inI, outI);
	}

	private static long lcm(long a, long b) {
		if(a == 0L || b == 0L) return 0L;
		long g = gcd(a, b);
		return (a / g) * b;
	}

	private static long gcd(long a, long b) {
		while(b != 0L) {
			long t = a % b;
			a = b;
			b = t;
		}
		return a;
	}

	public void setInput(FluidType f, int compression) {
		Chain chain = getBoiledFluid(f, compression);
		if(chain.steps() > 0) {
			if(input.getTankType() != f)
				input.setFill(0);
			if(output.getTankType() != f)
				output.setFill(0);
			this.compression = chain.steps();
			input.setTankType(f);
			output.setTankType(chain.produced());
			inAmt = chain.in();
			outAmt = chain.out();
		}
	}

	public int getInAmt() { return inAmt; }
	public int getOutAmt() { return outAmt; }

	/** Upstream's {@code slotContentsChanged}: an identifier put in sets the input. */
	@Override
	public void setItem(int i, ItemStack stack) {
		super.setItem(i, stack);
		if(level != null && stack.getItem() instanceof IItemFluidIdentifier identifier)
			setInput(identifier.getType(level, worldPosition, stack), compression);
	}

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			leafia$clientTick();
			return;
		}
		if(!targetSet) {
			BlockState state = getBlockState();
			if(state.hasProperty(CoreComponent.FACING))
				leafia$targetPosition(worldPosition.relative(state.getValue(CoreComponent.FACING)));
		}
		BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);
		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();
		timer++;

		if(timer >= tickDelay) {
			timer = 0;
			for(DirPos pos : ALL_AROUND) this.trySubscribe(input.getTankType(), level, pos);

			FluidType inType = input.getTankType();
			FluidType outType = output.getTankType();

			int heatAmt = outType.temperature - inType.temperature;
			if(core instanceof IMixinTileEntityCore mixin && heatAmt > 0 && inAmt > 0) {
				double mbPerCelsius = 1000.0;
				double difference = mixin.getDFCTemperature() - outType.temperature;
				if(difference > 0) {
					int maxDrain = (int) (difference / heatAmt * mbPerCelsius);
					int drain = Math.min(maxDrain, amountToHeat);
					drain = (drain / inAmt) * inAmt;
					if(drain > 0 && maxDrain > 0) {
						int availableIn = input.getFill();
						if(availableIn >= drain && inType != Fluids.NONE) {
							int batches = drain / inAmt;
							int fill = batches * outAmt;
							int canFill = DFCParts.fill(output, outType, fill, false);
							if(canFill == fill) {
								input.setFill(availableIn - drain);
								DFCParts.fill(output, outType, fill, true);
								mixin.setDFCTemperature(Math.max(mixin.getDFCTemperature() - drain * heatAmt / mbPerCelsius / 20, 0.0));
							}
						}
					}
				}
			}
		}

		for(DirPos pos : ALL_AROUND) this.tryProvide(output, level, pos);
		LeafiaPacket._start(this)
				.__write(0, input.getTankType().getName())
				.__write(1, output.getTankType().getName())
				.__write(2, input.getFill())
				.__write(3, output.getFill())
				.__write(4, compression)
				.__write(5, amountToHeat)
				.__write(6, tickDelay)
				.__sendToListeners();
		this.markChanged();
	}

	@Override
	public void leafia$clientTick() {
		BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);
		if(core != null)
			hum.play(level, worldPosition);
		else
			hum.stop();
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		IDFCBase.super.onReceivePacketServer(key, value, plr);
		if(key < 0 || key > 2) return;
		if(!(plr != null && plr.containerMenu instanceof CoreExchangerMenu menu && menu.te == this)) {
			PacketGuard.reject("DFC_EXCHANGER", plr, "this exchanger's screen is not open");
			return;
		}
		if(!(value instanceof Integer v)) {
			PacketGuard.reject("DFC_EXCHANGER", plr, "value " + value);
			return;
		}
		switch(key) {
			case 0 -> amountToHeat = Math.max(v, 1);
			case 1 -> tickDelay = Math.max(v, 1);
			case 2 -> setInput(input.getTankType(), v);
			default -> { }
		}
		markChanged();
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		IDFCBase.super.onReceivePacketLocal(key, value);
		switch(key) {
			case 0 -> input.setTankType(Fluids.fromName((String) value));
			case 1 -> output.setTankType(Fluids.fromName((String) value));
			case 2 -> input.setFill((int) value);
			case 3 -> output.setFill((int) value);
			case 4 -> compression = (int) value;
			case 5 -> amountToHeat = (int) value;
			case 6 -> tickDelay = (int) value;
			default -> { }
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		leafia$writeTargetPos(compound);
		input.writeToNBT(compound, "tankI");
		output.writeToNBT(compound, "tankO");
		compound.putInt("amount", amountToHeat);
		compound.putInt("delay", tickDelay);
		compound.putByte("compression", (byte) compression);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("x1")) {
			targetPosition = new BlockPos(compound.getInt("x1"), compound.getInt("y1"), compound.getInt("z1"));
			targetSet = true;
		}
		if(compound.contains("amount"))
			amountToHeat = compound.getInt("amount");
		if(compound.contains("delay"))
			tickDelay = compound.getInt("delay");
		if(compound.contains("compression"))
			compression = compound.getByte("compression");
		if(compound.contains("tankI")) {
			input.readFromNBT(compound, "tankI");
			output.readFromNBT(compound, "tankO");
			setInput(input.getTankType(), compression);
		}
	}

	@Override
	public List<Player> getListeners() {
		return new ArrayList<>(listeners);
	}

	@Override
	public String getName() {
		return "tile.dfc_exchanger.name";
	}

	@Override
	public String getPacketIdentifier() {
		return "DFC_EXCHANGER";
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreExchangerMenu(id, playerInventory, this);
	}

	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { output }; }
	@Override public FluidTank[] getAllTanks() { return new FluidTank[] { input, output }; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { input }; }

	@Override
	public void onLoad() {
		ControllableLifecycle.join(this);
		super.onLoad();
		if(level != null && level.isClientSide) com.leafia.client.LeafiaDFCClient.track(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		hum.release();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
		hum.release();
	}

	@Override @Nullable public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(@Nullable BlockEntityCore core) { lastGetCore = core; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { targetPosition = pos; targetSet = true; }

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's two query keys: the fill of each tank. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("input", input.getFill(), "output", output.getFill());
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }
}
