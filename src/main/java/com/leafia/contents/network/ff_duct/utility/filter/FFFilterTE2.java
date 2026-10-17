package com.leafia.contents.network.ff_duct.utility.filter;

import java.util.List;
import java.util.Map;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase;
import com.leafia.dev.container_utility.LeafiaPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Special Pipe Mixture Filter, the one the block makes: it holds no fluid and moves none. It sits <b>between</b>
 * two machines - a provider directly behind it and a receiver directly in front, not ducts - and moves one fuel's
 * share of the salt's mixture from the sender's tank into the receiver's.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/filter/FFFilterTE2.java} ({@link FFFilterTE} is
 * the earlier, tank-holding design, which nothing creates). With the utility's fluid set, every tick on the server:
 * look at the block behind (an {@link IFFProvider}) and the block in front (an {@link IFFReceiver}) along the facing;
 * ask each for its tank for a one-millibucket stack of the fluid; <b>valid</b> is whether both exist. If both tanks
 * hold something and the sender's mixture has the chosen {@link MSRFuel}:
 *
 * <pre>
 *   ratio = sending amount / receiving amount
 *   receiver's mixture[fuel] += sender's mixture[fuel] * ratio
 *   sender's mixture: fuel removed
 * </pre>
 *
 * Mixture entries are fuel <em>per bucket</em>, so the ratio rescales the sender's concentration into the receiver's
 * volume. It is done every tick the sender still has the fuel, so one tick moves all of it; a fuel the sender gets
 * back is moved again. Heat, amounts and the other fuels are untouched; the two stacks' tags are rewritten.
 * <b>Upstream's limits, kept:</b> the receiver need not be able to hold the salt (only its tank is read), the filter
 * does not check that either tank holds the utility's fluid, and the two tanks may be one and the same machine.
 * The filter is not a machine end of a duct (it has no handler), so no duct connects to it.
 *
 * <p>The fuel is the filter's own, set from its item when placed ({@link FFFilterBlock}) and saved as the enum's name
 * under {@code "filter"}. Sent to every client near each tick: key 0 the valid flag, key 2 the fuel's ordinal.
 */
public class FFFilterTE2 extends FFDuctUtilityTEBase {

	public boolean valid = false;
	public MSRFuel filter = MSRFuel.u235;

	public FFFilterTE2(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public MSRFuel getFilter() {
		return filter;
	}

	public void setFilter(MSRFuel filter) {
		this.filter = filter;
		setChanged();
	}

	void doTransfer() {
		if(LeafiaFF.getFF(getFluidType()) == null) return;
		if(level.isClientSide) return;
		valid = false;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof FFFilterBlock)) return;
		Direction facing = state.getValue(FFDuctUtilityBase.FACING);
		BlockEntity behind = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
		BlockEntity ahead = level.getBlockEntity(worldPosition.relative(facing));
		if(behind instanceof IFFProvider prov && ahead instanceof IFFReceiver rec) {
			FluidStack virtualStack = LeafiaFF.toFF(getFluidType(), 1);
			FluidTank sending = prov.getSendingTank(virtualStack);
			FluidTank receiving = rec.getCorrespondingTank(virtualStack);
			if(sending != null && receiving != null) {
				valid = true;
				if(receiving.getFluidAmount() > 0 && sending.getFluidAmount() > 0) {
					CompoundTag tag0 = MSRTEBase.nbtProtocol(sending.getFluid());
					CompoundTag tag1 = MSRTEBase.nbtProtocol(receiving.getFluid());
					Map<String, Double> mix0 = MSRTEBase.readMixture(tag0);
					Map<String, Double> mix1 = MSRTEBase.readMixture(tag1);
					if(mix0.containsKey(filter.name())) {
						double ratio = sending.getFluidAmount() / (double) receiving.getFluidAmount();
						mix1.put(filter.name(), mix1.getOrDefault(filter.name(), 0d) + mix0.get(filter.name()) * ratio);
						mix0.remove(filter.name());
						MSRTEBase.writeMixture(mix0, tag0);
						MSRTEBase.writeMixture(mix1, tag1);
						// a component is immutable, so the edited copies go back on the live stacks
						LeafiaFF.setTag(sending.getFluid(), tag0);
						LeafiaFF.setTag(receiving.getFluid(), tag1);
					}
				}
			}
		}
	}

	@Override
	public void update() {
		doTransfer();
		LeafiaPacket.Builder packet = LeafiaPacket._start(this).__write(0, valid);
		packet.__write(2, filter.ordinal());
		packet.__sendToAffectedClients();
	}

	@Override
	public void addInfo(List<String> info) {
		int color = valid ? 0x55FF55 : 0xFF0000;
		info.add(ChatFormatting.GRAY + I18nUtil.resolveKey("tile.ff_filter.filter", I18nUtil.resolveKey("tile.msr.fuel." + filter.name())));
		info.add("&[" + color + "&]" + I18nUtil.resolveKey("tile.ff_filter." + (valid ? "valid" : "invalid")));
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		try {
			filter = MSRFuel.valueOf(compound.getString("filter"));
		} catch(IllegalArgumentException ignored) {}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
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
			valid = (boolean) value;
		} else if(key == 2)
			filter = MSRFuel.values()[(int) value];
	}
}
