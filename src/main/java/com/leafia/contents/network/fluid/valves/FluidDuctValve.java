package com.leafia.contents.network.fluid.valves;

import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.sound.NtmSounds;
import com.leafia.contents.network.fluid.FluidDuctEquipmentBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `fluid_duct_valve_mdl`, the hand-operated fluid valve. Click it to open or shut the run.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/valves/FluidDuctValve.java}.
 *
 * <h2>The interaction is one method upstream and two here</h2>
 *
 * 1.7.10 and 1.12.2 had a single {@code onBlockActivated} for clicks with and without an item,
 * and upstream branches inside it: holding an {@code IItemFluidIdentifier} returns false so the
 * item can retype the pipe instead, anything else toggles. 1.21.1 splits that into
 * {@link #useItemOn} and {@link #useWithoutItem}, so the branch becomes the return value of the
 * first — {@code SKIP_DEFAULT_BLOCK_INTERACTION} lets the identifier act, which is what
 * upstream's {@code false} did.
 *
 * <p>The sound is upstream's {@code HBMSoundHandler.screw}. That name exists in NTM-CE and not
 * in 1.7.10 {@code hbm-src}, so it was resolved the way D-1012 resolved {@code getPercentage} —
 * out of the CE jar the upstream tree vendors, where it registers {@code weapon.reload.screw}.
 * That is {@code NtmSounds.WEAPON_RELOAD_SCREW} here. Upstream plays it in the
 * <em>blocks</em> category despite the event's own default being players, and that is kept.
 */
public class FluidDuctValve extends FluidDuctEquipmentBase {

	public FluidDuctValve(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidDuctValveTE(LeafiaBlockEntities.FLUID_DUCT_VALVE.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.FLUID_DUCT_VALVE.get(), FluidDuctValveTE::tick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {

		// Upstream's `return false`: hand the click to the identifier rather than toggling.
		if(stack.getItem() instanceof IItemFluidIdentifier)
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		toggle(level, pos, player);
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hitResult) {

		toggle(level, pos, player);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void toggle(Level level, BlockPos pos, Player player) {

		if(level.isClientSide) return;

		if(level.getBlockEntity(pos) instanceof FluidDuctValveTE valve) {
			valve.setOpen(!valve.isOpen());
			level.playSound(null, pos, NtmSounds.WEAPON_RELOAD_SCREW.get(), SoundSource.BLOCKS, 1F, 1F);
		}
	}
}
