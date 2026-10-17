package com.leafia.contents.machines.heat.hpboiler;

import com.google.common.math.IntMath;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.saveddata.TomSaveData;
import com.hbm.sound.NtmSounds;
import com.hbm.util.DirPos;
import com.leafia.contents.machines.heat.hpboiler.container.HPBoilerMenu;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import api.hbm.tile.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The high-pressure boiler: one block that runs a fluid up <em>several</em> heating steps at once.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/heat/hpboiler/HPBoilerTE.java}.
 *
 * <h2>What makes it worth having</h2>
 *
 * The base mod's own boilers do one {@link FT_Heatable} step: water to steam, steam to dense
 * steam, and so on, one machine per rung. This does up to {@code compression} rungs in a single
 * pass, and the player sets that number in the GUI. Chaining is not free — the steps are fused by
 * {@link #getBoiledFluid}, which raises the input and heat costs to a common multiple so the
 * whole chain runs atomically — but it comes with a bonus: the fused heat requirement is divided
 * by {@code 1.5^(comp-1)}, so three rungs in one block cost less heat than three blocks would.
 *
 * <p>It reads its fluid from a <b>fluid identifier in slot 4</b>, and
 * {@link com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem} is the only implementor of
 * {@code IItemFluidIdentifier} in the project (D-1053) — so this is the second machine to be
 * reachable because of that item, and the first in the addon.
 *
 * <h2>Heat comes from below, and from the sky after an impact</h2>
 *
 * {@link #tryPullHeat} takes {@link #diffusion} of the difference from an {@link IHeatSource}
 * directly beneath, and with nothing there the stored heat bleeds away at a thousandth per tick.
 * The second source is {@link TomSaveData#fire}: while Tom's impact has the sky burning and this
 * block can see it, the buffer fills by five millionths of its own remaining headroom per tick —
 * rampant at first and asymptotic after, so the sky alone never quite fills it.
 *
 * <h2>An upstream bug, flagged and kept (D-021)</h2>
 *
 * {@link #getBoiledFluid} multiplies each step's {@code getEfficiency(BOILER)} into a local
 * {@code efficiency}, and then <b>passes the literal {@code 1} to the result</b> instead of it.
 * The computed value is dead, on both return paths. So {@link #efficiency} is always 1 and a
 * fluid whose trait declares a poor boiler efficiency is heated as if it were perfect. Preserved
 * exactly, and pinned by {@code LeafiaHPBoilerTests.upstreamDiscardsTheAccumulatedEfficiency}.
 *
 * <h2>What does not port, and why</h2>
 *
 * <ul>
 * <li>Upstream's {@code super(5, true, false)} asks {@code TileEntityMachineBase} for a Forge
 *     {@code IFluidHandler} capability wrapper. That needs the Forge-fluid bridge on
 *     {@code FluidType} which D-1023 already records as a base-mod hand-off, so there is no
 *     wrapper here and the boiler is reachable only over NTM's own fluid network.</li>
 * <li>{@code input.loadTank}/{@code output.unloadTank} — the canister slots 0..3 — do not exist
 *     on this port's {@link FluidTank} at all. Same gap the wind turbine's lubricant canister
 *     hit (D-1028), same base-mod hand-off, and the slots are still there and still shown,
 *     because they are upstream's and they work the moment that lands.</li>
 * </ul>
 */
public class HPBoilerTE extends BlockEntityMachineBase implements LeafiaPacketReceiver,
		IFluidStandardReceiverMK2, IFluidStandardSenderMK2 {

	/** Upstream's buffer: 128 million TU. */
	public static int maxHeat = 128_000_000;

	/** The share of the difference pulled from the heat source below, per tick. */
	public static double diffusion = 0.1D;

	/** Upstream's five: two canister slots in, two out, and the fluid identifier. */
	public static final int SLOTS = 5;

	/** The slot the fluid identifier goes in. */
	public static final int SLOT_IDENTIFIER = 4;

	public int heat;

	public FluidTank input;
	public FluidTank output;
	public int compression = 4;
	public boolean isOn = false;

	int inAmt;
	int outAmt;

	/**
	 * Always 1. See the class doc — upstream computes a value here and discards it.
	 */
	double efficiency;

	public int heatReq;

	public HPBoilerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
		setFluid(Fluids.WATER, compression);
	}

	/**
	 * Re-sizes both tanks for a fluid and a chain length, if either changed.
	 *
	 * <p>The guard is upstream's and matters: this is called every tick from the identifier slot,
	 * and rebuilding the tanks unconditionally would empty them once per tick.
	 */
	public void setFluid(FluidType fluid, int compression) {

		BoilData dat = getBoiledFluid(fluid, compression);

		if(dat.outType != Fluids.NONE) {
			if(input == null || output == null || input.getTankType() != fluid
					|| output.getTankType() != dat.outType) {

				input = new FluidTank(fluid, 64000 * dat.inAmt);
				output = new FluidTank(dat.outType, 64000 * dat.outAmt);
				this.compression = dat.compression;
				inAmt = dat.inAmt;
				outAmt = dat.outAmt;
				efficiency = dat.efficiency;
				heatReq = dat.heatReq;
			}
		}
	}

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		isOn = false;

		// Upstream's input.loadTank(0, 1, inventory) and output.unloadTank(2, 3, inventory) sit
		// here. See the class doc: FluidTank has no canister transfer on this port.

		ItemStack stack = slots[SLOT_IDENTIFIER];

		if(stack.getItem() instanceof IItemFluidIdentifier identifier)
			setFluid(identifier.getType(level, worldPosition, stack), compression);

		int light = level.getBrightness(LightLayer.SKY, worldPosition);

		if(light > 7 && level instanceof ServerLevel server && TomSaveData.forLevel(server).fire > 1e-5) {
			// Constantly heat up 0.0005% of the remaining heat buffer, for rampant but
			// diminishing heating. Upstream's comment and upstream's constant.
			this.heat += (int) ((maxHeat - heat) * 0.000005D);
		}

		tryPullHeat();
		tryConvert();

		for(DirPos conPos : getConPos()) {
			trySubscribe(input.getTankType(), level, conPos);
			tryProvide(output, level, conPos);
		}

		CompoundTag tankData = new CompoundTag();
		input.writeToNBT(tankData, "input");
		output.writeToNBT(tankData, "output");

		LeafiaPacket._start(this)
				.__write(0, tankData)
				.__write(1, compression)
				.__write(2, heat)
				.__write(3, isOn)
				.__write(4, heatReq)
				.__sendToAffectedClients();
	}

	/**
	 * The two side ports, one block up and two out along the machine's own left-right axis.
	 *
	 * <p>Upstream reads the facing off {@code getBlockMetadata() - BlockDummyable.offset} and
	 * rotates it about UP; {@link BlockDummyable#DIR} holds the same facing here and
	 * {@link Direction#getClockWise()} is the same rotation.
	 */
	protected DirPos[] getConPos() {

		Direction dir = getBlockState().getValue(BlockDummyable.DIR).getClockWise();

		return new DirPos[] {
				new DirPos(worldPosition.getX() + dir.getStepX() * 2, worldPosition.getY() + 1,
						worldPosition.getZ() + dir.getStepZ() * 2, dir),
				new DirPos(worldPosition.getX() - dir.getStepX() * 2, worldPosition.getY() + 1,
						worldPosition.getZ() - dir.getStepZ() * 2, dir.getOpposite()),
		};
	}

	/**
	 * One tick's boiling: as many fused cycles as the input, the output room and the heat allow.
	 *
	 * <p>Note what the groan is tied to. It is a 1-in-400 chance per <em>ticking</em> tick, not
	 * per cycle, so a boiler running one operation groans as often as one running a thousand.
	 * Upstream's, kept.
	 */
	public void tryConvert() {

		int inputOps = input.getFill() / inAmt;
		int outputOps = (output.getMaxFill() - output.getFill()) / outAmt;
		int heatOps = (int) (this.heat * efficiency) / heatReq;

		int ops = Math.min(inputOps, Math.min(outputOps, heatOps));

		input.setFill(input.getFill() - inAmt * ops);
		output.setFill(output.getFill() + outAmt * ops);
		this.heat -= heatReq * ops;

		if(ops > 0 && level.random.nextInt(400) == 0) {
			level.playSound(null, worldPosition.getX() + 0.5D, worldPosition.getY() + 2,
					worldPosition.getZ() + 0.5D, NtmSounds.BLOCK_BOILER_GROAN.get(),
					SoundSource.BLOCKS, 0.5F, 1.0F);
		}

		if(ops > 0) this.isOn = true;
	}

	/** What one chain of heating steps costs and produces, fused into a single operation. */
	public static class BoilData {

		public final FluidType outType;
		public final int compression;
		public final int inAmt;
		public final int outAmt;
		public final double efficiency;
		public final int heatReq;

		public BoilData(FluidType outType, int comp, int inAmt, int outAmt, double efficiency, int heatReq) {
			this.outType = outType;
			this.compression = comp;
			this.inAmt = inAmt;
			this.outAmt = outAmt;
			this.efficiency = efficiency;
			this.heatReq = heatReq;
		}
	}

	/**
	 * Walks up to {@code compression} heating steps from {@code fluid} and fuses them into one.
	 *
	 * <p>Each rung has its own input, output and heat per operation, and consecutive rungs do not
	 * line up — one step may make 3 units where the next consumes 2. The fusion scales the whole
	 * prefix by {@code step.amountReq / gcd} and the new step by {@code prevOut / gcd}, so a
	 * single fused operation is a whole number of every rung's operations. It then divides the
	 * three totals by their common factor, which keeps the numbers small without changing the
	 * ratios.
	 *
	 * <p>The chain stops early at the first fluid with no {@link FT_Heatable}, a non-positive
	 * boiler efficiency, or no first step — which is how a fluid that cannot be boiled at all
	 * returns {@link Fluids#NONE} and leaves the tanks alone.
	 *
	 * <p><b>The efficiency it computes is thrown away.</b> See the class doc.
	 */
	public BoilData getBoiledFluid(FluidType fluid, int compression) {

		FluidType cur = fluid;
		FluidType outType = Fluids.NONE;
		int comp = 0;
		int inAmt = 0;
		int outAmt = 0;
		double efficiency = 1.0;
		int heatReq = 0;

		for(int i = 0; i < compression; i++) {

			FT_Heatable trait = cur.getTrait(FT_Heatable.class);
			if(trait == null) break;

			double stepEfficiency = trait.getEfficiency(HeatingType.BOILER);
			if(stepEfficiency <= 0.0) break;

			FT_Heatable.HeatingStep step = trait.getFirstStep();
			if(step == null || step.typeProduced == null) break;

			if(comp == 0) {
				inAmt = step.amountReq;
				outAmt = step.amountProduced;
				heatReq = step.heatReq;
			} else {
				int prevOut = outAmt;
				// Upstream's note: guava gcd is faster than a hand-rolled one.
				int g = IntMath.gcd(prevOut, step.amountReq);
				int prefixMul = step.amountReq / g;  // how many times the existing fused cycles run
				int stepMul = prevOut / g;           // how many times this new step runs
				inAmt *= prefixMul;
				outAmt = step.amountProduced * stepMul;
				heatReq = heatReq * prefixMul + step.heatReq * stepMul;
			}

			comp++;
			efficiency *= stepEfficiency;
			outType = cur = step.typeProduced;
		}

		if(comp <= 0) return new BoilData(outType, 0, 0, 0, 1, 0);

		heatReq = (int) (heatReq / Math.pow(1.5, comp - 1)); // apply bonus

		int div = IntMath.gcd(IntMath.gcd(inAmt, outAmt), heatReq);

		if(div > 1) {
			inAmt /= div;
			outAmt /= div;
			heatReq /= div;
		}

		// The accumulated `efficiency` is NOT passed here. Upstream's bug; see the class doc.
		return new BoilData(outType, comp, inAmt, outAmt, 1, heatReq);
	}

	/**
	 * Takes heat from an {@link IHeatSource} one block down, or bleeds it away when there is none.
	 *
	 * <p>Upstream returns early only when it actually <em>gained</em> heat, so a source colder
	 * than the boiler falls through to the bleed below — the boiler cools as if nothing were
	 * there, which is the sensible reading and is what upstream does.
	 */
	protected void tryPullHeat() {

		BlockEntity con = level.getBlockEntity(worldPosition.below(1));

		if(con instanceof IHeatSource source) {

			int diff = source.getHeatStored() - this.heat;
			if(diff == 0) return;

			if(diff > 0) {
				diff = (int) Math.ceil(diff * diffusion);
				diff = Math.min(diff, maxHeat - this.heat);
				source.useUpHeat(diff);
				this.heat += diff;
				if(this.heat > maxHeat) this.heat = maxHeat;
				return;
			}
		}

		this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
	}

	@Override
	public String getName() {
		return "tile.hp_boiler.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new HPBoilerMenu(containerId, playerInventory, this);
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { input };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { output };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { input, output };
	}

	@Override
	public String getPacketIdentifier() {
		return "HP_BOILER";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) {
			CompoundTag tag = (CompoundTag) value;
			input.readFromNBT(tag, "input");
			output.readFromNBT(tag, "output");
		} else if(key == 1) {
			compression = (int) value;
		} else if(key == 2) {
			heat = (int) value;
		} else if(key == 3) {
			isOn = (boolean) value;
		} else if(key == 4) {
			heatReq = (int) value;
		}
	}

	/** The GUI's one control: the chain length, typed into a box and sent on a button press. */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key != 0) return;
		// D-1094: the screen's button. Its sender has this boiler's menu open and is in reach, and the
		// chain length is a positive int - the screen refuses anything else. No upper bound is needed:
		// the chain stops at the first fluid that cannot be heated, so the length it stores is the
		// real one, never the number that was sent.
		if(!(plr != null && plr.containerMenu instanceof HPBoilerMenu menu
				&& menu.boiler == this)) {
			PacketGuard.reject("HPBoilerTE chain length", plr, "this boiler's screen is not open");
			return;
		}
		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("HPBoilerTE chain length", plr, "out of reach");
			return;
		}
		if(!(value instanceof Integer length) || length < 1) {
			PacketGuard.reject("HPBoilerTE chain length", plr, "length " + value);
			return;
		}
		setFluid(input.getTankType(), length);
	}

	@Override
	public void onPlayerValidate(Player plr) { }

	@Override
	protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putString("fluid", input.getTankType().getName());
		nbt.putInt("compression", compression);
		input.writeToNBT(nbt, "input");
		output.writeToNBT(nbt, "output");
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		compression = nbt.getInt("compression");
		setFluid(Fluids.fromName(nbt.getString("fluid")), compression);
		input.readFromNBT(nbt, "input");
		output.readFromNBT(nbt, "output");
	}

	public static void tick(Level level, BlockPos pos, BlockState state, HPBoilerTE boiler) {
		boiler.updateEntity();
	}
}
