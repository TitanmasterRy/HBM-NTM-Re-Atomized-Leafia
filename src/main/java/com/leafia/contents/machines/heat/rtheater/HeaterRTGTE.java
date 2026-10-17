package com.leafia.contents.machines.heat.rtheater;

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

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.util.RTGUtil;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.machines.heat.rtheater.container.HeaterRTGMenu;

import api.hbm.tile.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The radioisotope thermal heater: fifteen RTG pellets and one addon fuel rod make heat, which the
 * boiler above takes through the heat port.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/heat/rtheater/HeaterRTGTE.java}, every number its own:
 *
 * <ul>
 * <li>the store holds {@value #MAX_HEAT} TU and keeps {@code 0.999} of itself a tick - an {@code int *= 0.999},
 *     so the loss rounds down and a store under 1,000 TU loses a whole unit every tick;</li>
 * <li>a pellet's heat counts {@value #HEAT_PER_RTG} TU a tick ({@code RTGUtil.updateRTGs(...) * 10}), the
 *     base mod's own pellet table and decay;</li>
 * <li>a fuel rod in slot {@value #ROD_SLOT} adds {@code floor((heat / 250)^0.54 * 15) * 10 * 15} TU a tick off
 *     the temperature it had <em>before</em> this tick's fission, then fissions on twice its own flux with
 *     no cooling at all - so a rod in this machine only ever heats up;</li>
 * <li>a rod that reaches ten million degrees ({@code nuke}) empties the machine, removes it and goes off
 *     at full size; one that has spilled for more than 100 ticks removes it and goes off at radius 2;</li>
 * <li>a heat source directly below gives up all of its heat and 85% of it arrives ({@link #tryPullHeat}).</li>
 * </ul>
 *
 * <p><b>The Custom Control Panel half is stripped</b> (D-1093 #8): upstream's {@code IControllable}
 * (two query values and the control-event registration) belongs to a panel this port does not have.
 * {@code IGUIProvider} is the menu provider every block entity already is. The render box and view
 * distance move to {@link HeaterRTGRender}, as the base mod's oil burner's did (D-319).
 */
public class HeaterRTGTE extends BlockEntityMachineBase implements IHeatSource, IControllable {

	/** Upstream's {@code maxHeatEnergy}. */
	public static final int MAX_HEAT = 750_000;
	/** TU a tick per unit of pellet heat. */
	public static final int HEAT_PER_RTG = 10;
	/** The fuel rod's slot; 0 to 14 are the pellets. */
	public static final int ROD_SLOT = 15;
	public static final int SLOTS = 16;
	/** Upstream's {@code spillage > 20*5}. */
	public static final int SPILLAGE_LIMIT = 20 * 5;
	/** The blast a spilling rod sets off. */
	public static final float SPILL_RADIUS = 2;
	/** The share of a lower source's heat that arrives. */
	public static final double PULL_EFFICIENCY = 0.85;

	static final int[] PELLET_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};

	public int heatGen;
	public int heatEnergy;

	public HeaterRTGTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	@Override
	public String getName() {
		return "container.heaterRadioThermal";
	}

	/** What a rod at {@code fuelHeat} degrees adds, in TU a tick. Upstream's expression, exactly. */
	public static int rodHeat(double fuelHeat) {
		return (int) Math.floor(Math.pow(fuelHeat / 250, 0.54) * 15) * 10 * 15;
	}

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		this.heatEnergy *= 0.999;

		this.tryPullHeat();

		this.heatGen = RTGUtil.updateRTGs(slots, PELLET_SLOTS) * HEAT_PER_RTG;

		if(slots[ROD_SLOT].getItem() instanceof LeafiaRodItem rod) {
			ItemStack stack = slots[ROD_SLOT];
			CompoundTag nbt = LeafiaRodItem.data(stack);
			double fuelHeat = 20;
			if(nbt != null) {
				fuelHeat = nbt.getDouble("heat");
				if(nbt.getBoolean("nuke")) {
					for(int i = 0; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
					level.removeBlock(worldPosition, false);
					rod.nuke(level, worldPosition);
					return;
				}
				if(nbt.getInt("spillage") > SPILLAGE_LIMIT) {
					ItemStack prevStack = null;
					for(int i = 0; i < slots.length; i++) {
						prevStack = LeafiaRodItem.comparePriority(slots[i], prevStack);
						slots[i] = ItemStack.EMPTY;
					}
					level.removeBlock(worldPosition, false);
					LeafiaRodItem detonate = (LeafiaRodItem) prevStack.getItem();
					detonate.resetDetonate();
					detonate.detonateRadius = SPILL_RADIUS;
					detonate.detonate(level, worldPosition);
					return;
				}
			}
			heatGen += rodHeat(fuelHeat);
			rod.HeatFunction(stack, true, rod.getFlux(stack) * 2, 0, 0, 0);
			ItemStack product = rod.getDecayProduct(stack);
			if(product != null) slots[ROD_SLOT] = product;
		}

		this.heatEnergy += heatGen;
		if(heatEnergy > MAX_HEAT) this.heatEnergy = MAX_HEAT;

		networkPackNT(25);
	}

	/** Upstream's: everything a heat source directly below has, at 85%. */
	protected void tryPullHeat() {
		BlockEntity con = level.getBlockEntity(worldPosition.below());

		if(con instanceof IHeatSource source) {
			this.heatEnergy += source.getHeatStored() * PULL_EFFICIENCY;
			source.useUpHeat(source.getHeatStored());
		}
	}

	@Override
	public int getHeatStored() {
		return this.heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		this.heatEnergy = Math.max(0, this.heatEnergy - heat);
	}

	public boolean hasHeatGen() {
		return heatGen > 0;
	}

	public boolean hasHeat() {
		return heatEnergy > 0;
	}

	/** The heat-rate bar: logarithmic, full at 90,000 TU a tick. */
	public int getHeatGenScaled(int i) {
		if(heatGen == 0) return 0;
		return (int) (Math.log(heatGen) * i / Math.log(90000));
	}

	public int getHeatScaled(int i) {
		return (heatEnergy * i) / MAX_HEAT;
	}

	/// PERSISTENCE AND SYNC ///

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(this.heatGen);
		buf.writeInt(this.heatEnergy);
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		this.heatGen = buf.readInt();
		this.heatEnergy = buf.readInt();
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.heatEnergy = nbt.getInt("heatEnergy");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("heatEnergy", heatEnergy);
	}

	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new HeaterRTGMenu(id, playerInventory, this);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, HeaterRTGTE heater) {
		heater.updateEntity();
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's two query keys. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("heat", heatEnergy, "maxHeat", MAX_HEAT);
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
