package com.leafia.contents.machines.powercores.dfc.components.stabilizer;

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

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreStabilizer;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemLens;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.contents.machines.powercores.dfc.LCEItemLens;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The overhauled DFC stabilizer: a field of {@code fieldMod * watts / 100} added to the core it is aimed
 * at, an energy modifier, and a cost of {@code watts^5 * drainMod} HE a tick from a 10-trillion buffer.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityCoreStabilizer.java}, which
 * overwrites NTM-CE's stabilizer {@code update} and {@code getMaxPower}. Here a subclass of the base mod's
 * {@link BlockEntityCoreStabilizer} under its own type, put into the base block by
 * {@code BlockEntityOverrides} while {@code overhauls.dfcOverhaul} is on (D-3014).
 *
 * <h2>What changes with the overhaul</h2>
 *
 * <ul>
 * <li><b>Six lenses, not one.</b> The base mod's {@code ams_lens} (field, drain and energy all 1, as
 *     upstream's {@code MixinModItems} makes it) or any of the addon's five ({@link LCEItemLens}). Each
 *     wears by {@code watts} a tick to its own {@code maxDamage} and then is gone.</li>
 * <li><b>The cost is a fifth power</b>, {@code watts^5}, against the base's fourth; times the lens's drain.
 *     A full setting costs ten billion HE a tick on the standard lens, which is why the buffer is ten
 *     trillion (the base's is 2.5 billion).</li>
 * <li><b>The field adds up.</b> Every stabilizer on the core adds {@code fieldMod * watts / 100} to its
 *     {@code stabilization} and counts itself; the base core keeps only the strongest field.</li>
 * <li><b>The energy modifier</b>: a lens above 1 adds its excess to the core's {@code energyMod}, a lens
 *     below 1 multiplies it - so two boosters are 1.7, two limiters 0.64.</li>
 * <li>It finds the core along the line to its target, up to {@code dfcComponentRange} (50) blocks.</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * Upstream accepts {@code instanceof LCEItemLens} because its {@code ams_lens} is one; the base lens is
 * not here, so it is named beside it. The base screen still reads {@code networkPackNT}; its power bar is
 * drawn against the base's 2.5 billion, so with the overhaul it fills at a quarter of a thousandth of the
 * buffer - upstream's CE screen does the same. {@code IControllable} and OpenComputers are D-1093 #8.
 */
public class LeafiaCoreStabilizerTE extends BlockEntityCoreStabilizer implements IMixinTileEntityCoreStabilizer, IDFCClientTicked, IControllable {

	public LensType lens = LensType.STANDARD;
	public boolean cl_hasLens = false;
	/** Whether it stabilised a core this tick. NTM-CE's field, which the base mod's 1.7.10 stabilizer has not. */
	public boolean isOn = false;
	@Nullable
	public BlockEntityCore lastGetCore;
	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	protected final DFCParts.Hum hum = new DFCParts.Hum(false);

	public LeafiaCoreStabilizerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** {@code watts^5} HE a tick, before the lens's drain. */
	public long overhaulDemand() {
		return (long) Math.pow(watts, 5);
	}

	/** Whether this stack is a lens the overhauled stabilizer takes: the base lens or an addon one. */
	public static boolean isLens(ItemStack stack) {
		return stack.getItem() instanceof LCEItemLens || (!stack.isEmpty() && stack.getItem() == ModItems.ams_lens.get());
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
		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();

		this.autoPort(ALL_AROUND); // upstream's updateConnections: six trySubscribe calls

		watts = Mth.clamp(watts, 1, 100);
		long demand = overhaulDemand();
		isOn = false;
		beam = 0;

		ItemStack stack = slots[0];
		boolean lensIn = isLens(stack);
		if(lensIn) {
			Item item = stack.getItem();
			for(LensType type : LensType.values()) {
				if(type.item.get() == item) {
					this.lens = type;
					break;
				}
			}
		}

		if(lensIn && power >= demand * LCEItemLens.drainMod(stack)) {
			BlockEntityCore core = getCore();
			if(core instanceof IMixinTileEntityCore mixin) {
				isOn = true;
				double eMod = mixin.getDFCEnergyMod();
				float energyMod = LCEItemLens.energyMod(stack);
				mixin.setDFCStabilization(mixin.getDFCStabilization() + LCEItemLens.fieldMod(stack) * (watts / 100d));
				mixin.setDFCStabilizers(mixin.getDFCStabilizers() + 1);
				mixin.setDFCEnergyMod(energyMod >= 1 ? eMod + (energyMod - 1) : eMod * energyMod);
				this.power -= (long) (demand * LCEItemLens.drainMod(stack));

				long dmg = ItemLens.getLensDamage(stack);
				dmg += watts;

				if(dmg >= ((ItemLens) stack.getItem()).maxDamage)
					slots[0] = ItemStack.EMPTY;
				else
					ItemLens.setLensDamage(stack, dmg);
			}
		}
		cl_hasLens = lensIn;
		LeafiaPacket._start(this)
				.__write(0, lensIn ? this.lens.ordinal() : -1)
				.__write(1, isOn)
				.__sendToClients(250);
		this.networkPackNT(250);
	}

	@Override
	public void leafia$clientTick() {
		if(isOn) {
			lastGetCore = getCore();
			if(lastGetCore != null)
				hum.play(level, worldPosition);
			else
				hum.stop();
		} else
			hum.stop();
	}

	@Nullable
	private BlockEntityCore getCore() {
		return leafia$getCore(LeafiaConfig.dfcComponentRange);
	}

	/** Upstream's "need more juice": {@link IMixinTileEntityCoreStabilizer#maxPower}, ten trillion. */
	@Override
	public long getMaxPower() {
		return IMixinTileEntityCoreStabilizer.maxPower;
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) {
			cl_hasLens = (int) value >= 0;
			if(cl_hasLens)
				this.lens = LensType.values()[(int) value];
		}
		if(key == 1)
			this.isOn = (boolean) value;
		IMixinTileEntityCoreStabilizer.super.onReceivePacketLocal(key, value);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("x1")) {
			targetPosition = new BlockPos(nbt.getInt("x1"), nbt.getInt("y1"), nbt.getInt("z1"));
			targetSet = true;
		}
		this.isOn = nbt.getBoolean("isOn");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		leafia$writeTargetPos(nbt);
		nbt.putBoolean("isOn", isOn);
	}

	@Override
	public String getPacketIdentifier() {
		return "dfc_stabilizer";
	}

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

	/** Upstream's: the client's word on whether a lens is in, which the server now keeps the same. */
	@Override
	public boolean hasLens() {
		return cl_hasLens;
	}

	@Override
	public LensType getLens() {
		return lens;
	}

	@Override @Nullable public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(@Nullable BlockEntityCore core) { this.lastGetCore = core; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { this.targetPosition = pos; targetSet = true; }

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/**
	 * Upstream's nine query keys. The six {@code core_*} values are 0 until the stabilizer is running with a core in reach, and
	 * are the core's own figures scaled as upstream scaled them.
	 */
	@Override
	public Map<String, DataValue> getQueryData() {
		float lensHealth = 0;
		ItemStack stack = slots[0];
		if(stack.getItem() instanceof ItemLens lens)
			lensHealth = 100 - ItemLens.getLensDamage(stack) * 100 / (float) lens.maxDamage;
		float temp = 0, stabilization = 0, energy = 0, expel = 0, potent = 0, collapse = 0;
		if(isOn && getCore() instanceof IMixinTileEntityCore core) {
			temp = (float) core.getDFCTemperature();
			stabilization = (float) core.getDFCStabilization();
			energy = (float) core.getDFCContainedEnergy() * 1000_000;
			expel = (float) core.getDFCExpellingEnergy() * 1000_000;
			potent = (float) core.getDFCPotentialGain() * 100;
			collapse = (float) Math.pow(core.getDFCCollapsing(), 4) * 100;
		}
		return ControllableLifecycle.values("active", isOn ? 1 : 0, "level", watts, "lens_health", lensHealth,
				"core_temp", temp, "core_stabilization", stabilization, "core_energy", energy, "core_expel", expel,
				"core_potent", potent, "core_collapse", collapse);
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("set_stabilizer_level");
	}

	/** {@code level} is held to the dial's 1..100, which {@code updateEntity} clamps to anyway; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("set_stabilizer_level")) {
			int level = ControllableLifecycle.clampedInt(e, "level", 1, 100);
			if(level != Integer.MIN_VALUE)
				watts = level;
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }
}
