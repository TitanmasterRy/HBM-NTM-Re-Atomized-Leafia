package com.leafia.contents.network.ff_duct.utility.filter;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.group.LeafiaMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The earlier Mixture Filter: a 10,000 mB tank that <b>draws</b> one fuel's share out of the provider behind it and
 * offers it to the block in front. <b>Dead upstream.</b> {@code FFFilterBlock} makes {@link FFFilterTE2}, and
 * {@code TEInit} registers only that; nothing creates this class.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/filter/FFFilterTE.java}, ported whole so the file is
 * accounted for and the design is on record, and <b>not registered</b>: it takes a {@link BlockEntityType} in its
 * constructor like every other block entity here and nothing supplies one but the GameTests (which build it on the
 * filter's own type to pin {@link #doUpdate}). A player cannot meet it.
 *
 * <p>{@link #doUpdate} (the one {@link #update} calls) takes from the provider's tank, if the provider offers a
 * stack of the utility's fluid with a tag and this tank is empty: the part of the stack the fuel makes up,
 * {@code (int)(amount x mix / total)} millibuckets, with the fuel's concentration scaled to
 * {@code mix x (amt / (amount x mix / total))} and the sender losing that much and the fuel's entry. The sender's
 * new tag is the mixture alone, so <b>its heat is lost</b>; and an {@link IFluidHandler#drain} that is the decoy
 * every utility exposes would make a pump or converter provider give nothing, so only an ejector-like provider
 * works. {@link #doUpdateTest} and {@link #doTransfer} are upstream's two unused variants and are never called;
 * {@code doUpdateTest} throws a {@link NullPointerException} for a stack without the fuel, as upstream's does
 * (unboxing the missing entry).
 */
public class FFFilterTE extends FFDuctUtilityTEBase implements IFluidHandler, IFFProvider {

	public final FluidTank tank = new FluidTank(10000);
	public MSRFuel filter = MSRFuel.u235;

	public FFFilterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void setType(FluidType type) {
		super.setType(type);
		tank.drain(tank.getCapacity() * 2, IFluidHandler.FluidAction.EXECUTE);
	}

	/** Never called upstream. */
	void doUpdateTest() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof FFFilterBlock)) return;
		Direction facing = state.getValue(FFDuctUtilityBase.FACING);
		BlockEntity behind = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
		if(behind instanceof IFFProvider prov) {
			FluidStack stack = prov.drain(tank.getCapacity() - tank.getFluidAmount(), FluidAction.SIMULATE);
			if(!stack.isEmpty() && LeafiaFF.fromFF(stack.getFluid()) == getFluidType() && prov.getSendingTank(stack) != null && tank.getFluidAmount() == 0) {
				if(LeafiaFF.hasTag(stack)) {
					CompoundTag tag = MSRTEBase.nbtProtocol(stack);
					Map<String, Double> mixture = MSRTEBase.readMixture(tag);
					LeafiaMap<String, Double> contents = new LeafiaMap<>();
					double total = 0;
					for(Entry<String, Double> entry : mixture.entrySet()) {
						contents.put(entry.getKey(), entry.getValue() * stack.getAmount());
						total += entry.getValue() * stack.getAmount();
					}
					if(total > 0) {
						double ratio = stack.getAmount() / total;
						double mix = mixture.getOrDefault(filter.name(), 0d);
						LeafiaMap<String, Double> output = new LeafiaMap<>();
						output.put(filter.name(), contents.get(filter.name()));
						contents.remove(filter.name());

						CompoundTag tag0 = new CompoundTag();
						mixture = new LeafiaMap<>();
						for(Entry<String, Double> entry : contents.entrySet())
							mixture.put(entry.getKey(), entry.getValue() / stack.getAmount());
						CompoundTag tag1 = new CompoundTag();
						LeafiaMap<String, Double> outMix = new LeafiaMap<>();
						for(Entry<String, Double> entry : output.entrySet())
							outMix.put(entry.getKey(), entry.getValue() / stack.getAmount());
						MSRTEBase.writeMixture(mixture, tag0);
						MSRTEBase.writeMixture(outMix, tag1);
						int jackshit = (int) (mix * stack.getAmount() * ratio);
						FluidStack senderFluid = LeafiaFF.toFF(getFluidType(), (int) ((total - mix * stack.getAmount()) * ratio));
						if(!senderFluid.isEmpty()) LeafiaFF.setTag(senderFluid, tag0);
						FluidStack outputFluid = LeafiaFF.toFF(getFluidType(), jackshit);
						if(!outputFluid.isEmpty()) LeafiaFF.setTag(outputFluid, tag1);

						if(jackshit > 0) {
							FluidTank sending = prov.getSendingTank(stack);
							sending.setFluid(senderFluid);
							tank.fill(outputFluid, FluidAction.EXECUTE);
						}
					}
				}
			}
			tryProvide(tank, level, worldPosition.relative(facing), facing);
		}
	}

	/** What {@link #update} runs. */
	void doUpdate() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof FFFilterBlock)) return;
		Direction facing = state.getValue(FFDuctUtilityBase.FACING);
		BlockEntity behind = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
		if(behind instanceof IFFProvider prov) {
			draw(prov);
			tryProvide(tank, level, worldPosition.relative(facing), facing);
		}
	}

	/** Never called upstream: {@link #doUpdate} that also wants a receiver in front. */
	void doTransfer() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof FFFilterBlock)) return;
		Direction facing = state.getValue(FFDuctUtilityBase.FACING);
		BlockEntity behind = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
		BlockEntity ahead = level.getBlockEntity(worldPosition.relative(facing));
		if(behind instanceof IFFProvider prov && ahead instanceof IFFReceiver) {
			draw(prov);
			tryProvide(tank, level, worldPosition.relative(facing), facing);
		}
	}

	/** The body {@code doUpdate} and {@code doTransfer} share, line for line. */
	private void draw(IFFProvider prov) {
		FluidStack stack = prov.drain(tank.getCapacity() - tank.getFluidAmount(), FluidAction.SIMULATE);
		if(!stack.isEmpty() && LeafiaFF.fromFF(stack.getFluid()) == getFluidType() && prov.getSendingTank(stack) != null && tank.getFluidAmount() == 0) {
			if(LeafiaFF.hasTag(stack)) {
				CompoundTag tag = MSRTEBase.nbtProtocol(stack);
				Map<String, Double> mixture = MSRTEBase.readMixture(tag);
				double mix = mixture.getOrDefault(filter.name(), 0d);
				double mixTotal = 0;
				for(Double value : mixture.values())
					mixTotal += value;
				if(mixTotal > 0) {
					int amt = (int) (stack.getAmount() * mix / mixTotal);
					if(amt > 0) {
						double transferAmt = mix * (amt / (stack.getAmount() * mix / mixTotal));
						if(transferAmt > mix)
							throw new LeafiaDevFlaw("transferAmt higher than mix. Leafia is a dumbass.");

						if(transferAmt > 0) {
							Map<String, Double> mixture2 = new LeafiaMap<>();
							mixture2.put(filter.name(), transferAmt);
							mixture.put(filter.name(), mix - transferAmt);

							FluidStack fillStack = stack.copyWithAmount(amt);
							LeafiaFF.setTag(fillStack, MSRTEBase.writeMixture(mixture2, new CompoundTag()));

							// this is so jankshit
							FluidTank sending = prov.getSendingTank(stack);
							FluidStack live = sending.getFluid();
							if(live.isEmpty())
								throw new LeafiaDevFlaw("Got null FluidStack after processing. How?!");
							live.shrink(fillStack.getAmount());
							LeafiaFF.setTag(live, MSRTEBase.writeMixture(mixture, new CompoundTag()));
							sending.setFluid(live);

							tank.fill(fillStack, FluidAction.EXECUTE);
						}
					}
				}
			}
		}
	}

	@Override
	public void update() {
		doUpdate();
		LeafiaPacket.Builder packet = LeafiaPacket._start(this).__write(0, tank.getFluidAmount());
		if(!tank.getFluid().isEmpty() && LeafiaFF.hasTag(tank.getFluid()))
			packet.__write(1, LeafiaFF.getTag(tank.getFluid()));
		packet.__write(2, filter.ordinal());
		packet.__sendToAffectedClients();
	}

	@Override
	public void addInfo(List<String> info) {
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_pump.buffer", tank.getFluidAmount() + "mB"));
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_filter.filter", I18nUtil.resolveKey("tile.msr.fuel." + filter.name())));
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		tank.readFromNBT(registries, compound.getCompound("buffer"));
		try {
			filter = MSRFuel.valueOf(compound.getString("filter"));
		} catch(IllegalArgumentException ignored) {}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.put("buffer", tank.writeToNBT(registries, new CompoundTag()));
		compound.putString("filter", filter.name());
		super.saveAdditional(compound, registries);
	}

	@Override
	public String getPacketIdentifier() {
		return "FF_FILTER";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) {
			net.minecraft.world.level.material.Fluid ff = LeafiaFF.getFF(getFluidType());
			if(ff == null)
				tank.setFluid(FluidStack.EMPTY);
			else
				tank.setFluid(new FluidStack(ff, (int) value));
		} else if(key == 1 && !tank.getFluid().isEmpty())
			LeafiaFF.setTag(tank.getFluid(), (CompoundTag) value);
		else if(key == 2)
			filter = MSRFuel.values()[(int) value];
	}

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return tank;
	}

	// The handler is a decoy, as the pump's.

	@Override
	public int getTanks() {
		return 0;
	}

	@Override
	public FluidStack getFluidInTank(int i) {
		return FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int i) {
		return 0;
	}

	@Override
	public boolean isFluidValid(int i, FluidStack stack) {
		return false;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return FluidStack.EMPTY;
	}
}
