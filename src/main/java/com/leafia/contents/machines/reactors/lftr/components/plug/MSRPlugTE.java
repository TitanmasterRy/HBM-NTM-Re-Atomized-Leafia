package com.leafia.contents.machines.reactors.lftr.components.plug;

import javax.annotation.Nullable;

import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.container_utility.LeafiaPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The LFTR Fuel Plug: where salt enters the reactor, and - once it melts - where it leaves.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/plug/MSRPlugTE.java}.
 *
 * <h2>In</h2>
 *
 * A 10,000 mB tank. It subscribes to the duct in front of it ({@link #getDirection}) for its set
 * coolant - FLiBe unless an identifier says otherwise, and only an {@code FT_LFTRCoolant} fluid is
 * accepted - and a NeoForge handler on its front and bottom ({@code LeafiaCapabilities}) accepts that
 * coolant from anything else, mixing the incoming salt's tag with its own. It cannot be drained. It
 * pushes salt into the reactor like any MSR block, but no MSR block pushes into it until it melts.
 *
 * <h2>Out</h2>
 *
 * Once its salt is hotter than {@code 4000 - } the fluid's own temperature it is <b>molten</b>, for
 * good: from then on it offers its tank to whatever is below it (a duct or a receiver) every tick,
 * and if the block below is replaceable and not a liquid it fills it with fluoride - and while
 * fluoride is below, it loses 1,000 mB a tick and raises the radiation below to what it is above.
 * That is upstream's sequence, kept as it is (D-3012): the fluoride is placed for nothing, the
 * drain is a flat 1,000 mB each tick it sees fluoride there (the block it placed, or one already
 * there, with the placement tick included). A plug holding less than a bucket places nothing (D-3076:
 * upstream placed a whole bucket of fluoride for any amount, which made fluid from nothing).
 * {@code LeafiaFF.pour} is not used. The block is the FLiBe's world fluid, the addon's fluoride; with {@code worldFluids}
 * off there is none, and a molten plug only empties into ducts.
 *
 * <p>Its tank is sent to nearby clients each tick with the molten flag under key 0, and its set
 * coolant under key 1 when it changes.
 */
public class MSRPlugTE extends MSRTEBase implements IFFReceiver, IFFProvider {

	public boolean molten = false;
	FluidType inputType = LeafiaFluids.FLUORIDE == null ? Fluids.NONE : LeafiaFluids.FLUORIDE;

	public MSRPlugTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tank = new FluidTank(10000);
	}

	public FluidType getInputType() {
		return inputType;
	}

	public void setType(FluidType type) {
		inputType = type;
		setChanged();
		sendTypeUpdatePacket();
	}

	public Direction getDirection() {
		BlockState state = level.getBlockState(worldPosition);
		if(state.getBlock() instanceof MSRPlugBlock)
			return state.getValue(HorizontalDirectionalBlock.FACING);
		return Direction.NORTH;
	}

	/// IFluidHandler ///

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int i) {
		return tank.getFluid();
	}

	@Override
	public int getTankCapacity(int i) {
		return tank.getCapacity();
	}

	@Override
	public boolean isFluidValid(int i, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if(resource.isEmpty()) return 0;
		FluidType ntmf = LeafiaFF.fromFF(resource.getFluid());
		if(ntmf.equals(Fluids.NONE)) return 0;
		boolean compatible = true;
		if(!tank.getFluid().isEmpty())
			compatible = resource.getFluid().equals(tank.getFluid().getFluid());
		if(compatible && ntmf.hasTrait(FT_LFTRCoolant.class)) {
			if(action.execute()) {
				if(!tank.getFluid().isEmpty()) {
					transferStats(resource, tank.getFluid(), 1);
					return tank.fill(tank.getFluid().copyWithAmount(resource.getAmount()), action);
				} else
					return tank.fill(resource, action);
			} else {
				if(!tank.getFluid().isEmpty())
					return tank.fill(tank.getFluid().copyWithAmount(resource.getAmount()), action);
				else
					return tank.fill(resource, action);
			}
		} else
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

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return null;
	}

	@Override
	public FluidTank getCorrespondingTank(FluidStack stack) {
		return tank;
	}

	@Override
	public String getPacketIdentifier() {
		return "LFTR_PLUG";
	}

	/** The heat at which the plug melts: {@code 4000 - } the fluid's own temperature. Upstream's test is strictly greater. */
	public static double meltingHeat(FluidStack stack) {
		return 4000 - getBaseTemperature(LeafiaFF.fromFF(stack.getFluid()));
	}

	/** What a subscription asks the duct in front with when the tank is empty: the set coolant, 1 mB (see {@link IFFReceiver}). */
	FluidStack defaultStack() {
		Fluid ff = LeafiaFF.getFF(inputType);
		return ff == null ? FluidStack.EMPTY : new FluidStack(ff, 1);
	}

	@Override
	public void update() {
		if(level == null || level.isClientSide) return;
		sendFluids();
		Direction direction = getDirection();
		trySubscribe(tank, defaultStack(), level, worldPosition.relative(direction), direction);
		if(!tank.getFluid().isEmpty()) {
			if(nbtProtocol(tank.getFluid()).getDouble("heat") > meltingHeat(tank.getFluid()))
				molten = true;
			if(molten)
				tryProvide(tank, level, worldPosition.below(), Direction.DOWN);
			BlockPos below = worldPosition.below();
			BlockState belowState = level.getBlockState(below);
			BlockState fluoride = fluorideBlock();
			// a bucket of fluoride is paid for with a bucket of salt: a plug with less holds the spill back (D-3076; upstream placed a whole bucket for nothing)
			if(molten && fluoride != null && tank.getFluidAmount() >= 1000 && belowState.canBeReplaced() && !belowState.liquid()) {
				level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.0F, 0.5F);
				level.setBlockAndUpdate(below, fluoride);
			}
			if(molten && fluoride != null && level.getBlockState(below).getBlock() == fluoride.getBlock()) {
				tank.drain(1000, FluidAction.EXECUTE);
				float rad = ChunkRadiationManager.proxy.getRadiation(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ());
				ChunkRadiationManager.incrementRad(level, below, rad, rad);
			}
		}
		generateTankPacket().__write(0, molten).__sendToAffectedClients();
	}

	/** Upstream's {@code AddonBlocks.fluid_fluoride}: FLiBe's world fluid, null when the addon's world fluids are off. */
	@Nullable
	public static BlockState fluorideBlock() {
		return LeafiaFluids.FLUORIDE == null ? null : LeafiaFF.worldBlock(LeafiaFluids.FLUORIDE);
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0)
			molten = (boolean) value;
		else if(key == 1)
			inputType = Fluids.fromID((int) value);
	}

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket._start(this).__write(1, inputType.getID()).__sendToClient(plr);
	}

	public void sendTypeUpdatePacket() {
		if(level == null || level.isClientSide) return;
		LeafiaPacket._start(this).__write(1, inputType.getID()).__sendToAffectedClients();
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		molten = compound.getBoolean("molten");
		inputType = Fluids.fromID(compound.getInt("filter"));
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putBoolean("molten", molten);
		compound.putInt("filter", inputType.getID());
		super.saveAdditional(compound, registries);
	}

	/** The coolant the plug is set to also reaches a client through the update tag, standing in for {@code onPlayerValidate}. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}
}
