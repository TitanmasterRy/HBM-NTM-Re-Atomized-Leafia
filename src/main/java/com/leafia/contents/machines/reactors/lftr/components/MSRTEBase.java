package com.leafia.contents.machines.reactors.lftr.components;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.llib.group.LeafiaMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Every block of the molten-salt reactor that holds salt: one Forge tank, its salt's heat and fuel
 * mixture, and the passing of both to the MSR blocks around it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/MSRTEBase.java}.
 *
 * <h2>The salt's NBT</h2>
 *
 * The tank holds FLiBe (the one {@code FT_LFTRCoolant} fluid) as a NeoForge stack, and the stack's
 * tag - {@code CUSTOM_DATA} here, {@link LeafiaFF#getTag}/{@link LeafiaFF#setTag} (D-3003) - carries
 * {@link #nbtProtocol}'s two keys: {@code heat}, degrees above the fluid's own temperature, and
 * {@code itemMixture}, a list of {@code {type, amount}} with the fuel isotopes per bucket. Upstream
 * edits the live tag in place; a component is immutable, so every edit here is read, change,
 * {@link LeafiaFF#setTag} - on a non-empty stack only, since an empty one may be the shared
 * {@code FluidStack.EMPTY}.
 *
 * <h2>What a tick does</h2>
 *
 * <ol>
 * <li>Passive cooling: {@code heat = max(0, heat x 0.99)}.</li>
 * <li>At {@code heat >= 6000 - base temperature}, one chance in 350 a tick that the block becomes
 *     sellafield of level 5, with the break particles. The roll is random; the threshold is pinned.</li>
 * <li>{@link #sendFluids}: salt flows to MSR neighbours holding less, and heat and mixture are mixed
 *     with every MSR neighbour.</li>
 * <li>The tank goes to every client near, every tick, for the look overlay.</li>
 * </ol>
 *
 * <h2>{@link #sendFluids}'s arithmetic, and three upstream quirks kept (D-021)</h2>
 *
 * For each MSR neighbour holding less, the demand is {@code min(room, (mine - theirs) / 2)}, in
 * integer millibuckets. All of it is offered at {@code min(total demand, mine) / total demand}, and
 * then <b>divided again by the number of such neighbours</b>: a block with four emptier neighbours
 * passes each a quarter of what the first formula gives. Kept, and pinned by a GameTest. Before any
 * fluid moves, {@link #transferStats} mixes this block's salt with every MSR neighbour's, with the
 * heat divisor set to the neighbour count. A plug that has not melted is skipped entirely - not
 * filled and not mixed - so the reactor cannot push salt back into the plug feeding it until it
 * melts. And a neighbour that already holds a <em>different</em> fluid is filled with its own fluid
 * while this block is drained of its own: nothing filters an MSR tank but the plug's intake, so that
 * takes building one with two fluids in it, but nothing forbids it.
 *
 * <h2>What is not here</h2>
 *
 * {@code IControllable} - the control panel's query and event hooks - is stripped, per D-1093 #8:
 * the control panel and IC10 are not ported. {@code Tracker}'s profiling calls are a developer
 * tool and are gone.
 */
public abstract class MSRTEBase extends BlockEntity implements LeafiaPacketReceiver, IControllable {

	public FluidTank tank = new FluidTank(1000);

	protected MSRTEBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static double getBaseTemperature(FluidType fluidType) {
		return fluidType.temperature;
	}

	/** The "chunk is modified, please don't forget to save me" effect of markDirty, minus the block updates. */
	public void markChanged() {
		if(level != null) level.blockEntityChanged(worldPosition);
	}

	/// THE SALT'S NBT ///

	/** Upstream's {@code nbtProtocol}: the tag with both keys present, a fresh one if there was none. Edits {@code tag} in place. */
	public static CompoundTag nbtProtocol(@Nullable CompoundTag tag) {
		if(tag == null) tag = new CompoundTag();
		if(!tag.contains("itemMixture"))
			tag.put("itemMixture", new ListTag());
		if(!tag.contains("heat"))
			tag.putDouble("heat", 0);
		return tag;
	}

	/** {@link #nbtProtocol} of a stack's tag: upstream's {@code nbtProtocol(stack.tag)}, as a copy. */
	public static CompoundTag nbtProtocol(FluidStack stack) {
		return nbtProtocol(stack.isEmpty() || !LeafiaFF.hasTag(stack) ? null : LeafiaFF.getTag(stack));
	}

	public static Map<String, Double> readMixture(CompoundTag tag) {
		Map<String, Double> mixture = new LeafiaMap<>();
		ListTag list = tag.getList("itemMixture", Tag.TAG_COMPOUND);
		for(Tag nbtBase : list) {
			if(nbtBase instanceof CompoundTag compound)
				mixture.put(compound.getString("type"), compound.getDouble("amount"));
		}
		return mixture;
	}

	public double getMixture(CompoundTag tag, String element) {
		ListTag list = tag.getList("itemMixture", Tag.TAG_COMPOUND);
		for(Tag nbtBase : list) {
			if(nbtBase instanceof CompoundTag compound) {
				if(compound.getString("type").equals(element))
					return compound.getDouble("amount");
			}
		}
		return 0;
	}

	/** Sets the {@code itemMixture} key and answers the tag. */
	public static CompoundTag writeMixture(Map<String, Double> mixture, CompoundTag nbt) {
		nbt.put("itemMixture", writeMixture(mixture));
		return nbt;
	}

	public static ListTag writeMixture(Map<String, Double> mixture) {
		ListTag list = new ListTag();
		for(Entry<String, Double> entry : mixture.entrySet()) {
			CompoundTag compound = new CompoundTag();
			compound.putString("type", entry.getKey());
			compound.putDouble("amount", entry.getValue());
			list.add(compound);
		}
		return list;
	}

	/**
	 * Mixes two stacks of salt: both end with the same mixture, each isotope weighted by the two
	 * amounts, and heat flows from the hotter by {@code difference / (div x 2)} - so a divisor of 1
	 * halves the difference and leaves them level. Both tags are rewritten (and gain
	 * {@link #nbtProtocol}'s keys). Nothing happens if either stack is empty. Answers {@code stack1},
	 * as upstream.
	 */
	public static FluidStack transferStats(FluidStack stack0, FluidStack stack1, double div) {
		if(stack0.isEmpty()) return stack1;
		if(stack1.isEmpty()) return stack1;
		CompoundTag compound = nbtProtocol(stack0);
		CompoundTag target = nbtProtocol(stack1);
		Map<String, Double> mixture0 = readMixture(compound);
		Map<String, Double> mixture1 = readMixture(target);
		Map<String, Double> mixture2 = new LeafiaMap<>();
		int sum = stack0.getAmount() + stack1.getAmount();
		if(stack0.getAmount() > 0) {
			for(Entry<String, Double> entry : mixture0.entrySet()) {
				double amount = 0;
				amount += entry.getValue() * stack0.getAmount() / sum;
				mixture2.put(entry.getKey(), amount);
			}
		}
		if(stack1.getAmount() > 0) {
			for(Entry<String, Double> entry : mixture1.entrySet()) {
				double amount = mixture2.getOrDefault(entry.getKey(), 0d);
				amount += entry.getValue() * stack1.getAmount() / sum;
				mixture2.put(entry.getKey(), amount);
			}
		}
		compound.put("itemMixture", writeMixture(mixture2));
		target.put("itemMixture", writeMixture(mixture2));
		double heatTransfer = compound.getDouble("heat") - target.getDouble("heat");
		heatTransfer /= div * 2;
		compound.putDouble("heat", compound.getDouble("heat") - heatTransfer);
		target.putDouble("heat", target.getDouble("heat") + heatTransfer);
		LeafiaFF.setTag(stack0, compound);
		LeafiaFF.setTag(stack1, target);
		return stack1;
	}

	/// FLOW BETWEEN MSR BLOCKS ///

	/** See the class note for the arithmetic and its quirks. */
	public void sendFluids() {
		if(level == null) return;
		int demand = 0;
		Map<MSRTEBase, Integer> list = new LeafiaMap<>();
		List<MSRTEBase> transferStatTargets = new ArrayList<>();
		for(Direction facing : Direction.values()) {
			BlockPos target = worldPosition.relative(facing);
			if(level.getBlockEntity(target) instanceof MSRTEBase te) {
				if(te instanceof MSRPlugTE plug) {
					if(!plug.molten)
						continue;
				}
				if(te.tank.getFluidAmount() < tank.getFluidAmount()) {
					int a = te.tank.getCapacity() - te.tank.getFluidAmount();
					int b = (tank.getFluidAmount() - te.tank.getFluidAmount()) / 2;
					int addDemand = Math.min(a, b);
					demand += addDemand;
					list.put(te, addDemand);
				}
				transferStatTargets.add(te);
			}
		}
		double canSend = Math.min(demand, tank.getFluidAmount());
		double multiplier = canSend / demand;
		for(MSRTEBase target : transferStatTargets)
			transferStats(tank.getFluid(), target.tank.getFluid(), transferStatTargets.size());
		if(!list.isEmpty()) {
			for(Entry<MSRTEBase, Integer> entry : list.entrySet()) {
				int amt = (int) (entry.getValue() * multiplier / list.size());
				if(amt > 0) {
					MSRTEBase te = entry.getKey();
					FluidStack offer = (te.tank.getFluid().isEmpty() ? tank.getFluid() : te.tank.getFluid()).copyWithAmount(amt);
					tank.drain(te.tank.fill(offer, FluidAction.EXECUTE), FluidAction.EXECUTE);
				}
			}
		}
	}

	/// TICK ///

	public static void tick(Level level, BlockPos pos, BlockState state, MSRTEBase msr) {
		msr.update();
	}

	public void update() {
		if(level == null || level.isClientSide) return;
		if(!tank.getFluid().isEmpty()) {
			FluidStack stack = tank.getFluid();
			CompoundTag nbt = nbtProtocol(stack);
			nbt.putDouble("heat", Math.max(0, nbt.getDouble("heat") * 0.99)); // passive cooling
			LeafiaFF.setTag(stack, nbt);
			if(nbt.getDouble("heat") >= meltdownHeat(stack)) {
				if(level.random.nextInt(350) == 0) {
					level.levelEvent(2001, worldPosition, Block.getId(level.getBlockState(worldPosition)));
					level.setBlockAndUpdate(worldPosition, ModBlocks.sellafield.get().withLevel(5));
					return;
				}
			}
		}
		sendFluids();
		generateTankPacket().__sendToAffectedClients();
	}

	/** The heat above which the block may turn to sellafield: {@code 6000 - } the fluid's own temperature. */
	public static double meltdownHeat(FluidStack stack) {
		return 6000 - getBaseTemperature(LeafiaFF.fromFF(stack.getFluid()));
	}

	protected LeafiaPacket.Builder generateTankPacket() {
		LeafiaPacket.Builder packet = LeafiaPacket._start(this);
		packet.__write(31, tank.writeToNBT(level.registryAccess(), new CompoundTag()));
		return packet;
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 31 && level != null) {
			tank.setFluid(FluidStack.EMPTY);
			tank.readFromNBT(level.registryAccess(), (CompoundTag) value);
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {}

	@Override
	public void onPlayerValidate(Player plr) {}

	/// NBT ///

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("tank"))
			tank.readFromNBT(registries, compound.getCompound("tank"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.put("tank", tank.writeToNBT(registries, new CompoundTag()));
		super.saveAdditional(compound, registries);
	}

	/// LOOK OVERLAY ///

	/** The lines every MSR block's overlay carries: status, the element's restriction, the fill, the salt. */
	public static void appendPrintHook(List<String> list, Level world, BlockPos pos) {
		BlockEntity entity = world.getBlockEntity(pos);
		if(entity instanceof MSRTEBase msr) {
			list.add(ChatFormatting.GOLD + I18nUtil.resolveKey("tile.msr.status"));
			if(entity instanceof MSRElementTE element)
				list.add("  " + I18nUtil.resolveKey("tile.msr_element.restriction", (int) (element.restriction * 100)));
			list.add("  " + I18nUtil.resolveKey("tile.msr.fill", msr.tank.getFluidAmount() + "/" + msr.tank.getCapacity() + "mB"));
			if(!msr.tank.getFluid().isEmpty())
				LeafiaFFClient.addFluidInfo(msr.tank.getFluid(), list, "  ", net.minecraft.client.gui.screens.Screen.hasShiftDown());
		}
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/**
	 * Upstream's three query keys, for every MSR block: the salt's amount and the tank's capacity in mB, and the salt's temperature,
	 * its fluid's base temperature plus the heat it carries (20 for an empty tank).
	 */
	@Override
	public Map<String, DataValue> getQueryData() {
		float heat = 20;
		FluidStack stack = tank.getFluid();
		if(!stack.isEmpty()) {
			heat = (float) getBaseTemperature(LeafiaFF.fromFF(stack.getFluid()));
			if(LeafiaFF.hasTag(stack))
				heat += (float) LeafiaFF.getTag(stack).getDouble("heat");
		}
		return ControllableLifecycle.values("amount", tank.getFluidAmount(), "capacity", tank.getCapacity(), "temperature", heat);
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
