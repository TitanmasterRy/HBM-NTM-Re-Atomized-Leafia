package com.leafia.contents.debug.fluid_voider;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.ILookOverlay;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * `test_fluid_voider`, the block half of the fluid voider.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/fluid_voider/VoiderBlock.java}.
 *
 * <h2>Its base class is still not written, and still does not need to be</h2>
 *
 * Upstream extends {@code AddonBlockBase}. D-1015 read that class and found it to be registration
 * boilerplate — registry name, translation key, harvest level, creative tab — every line of which
 * is {@code BlockBehaviour.Properties} plus {@code DeferredRegister} in 1.21.1, leaving one real
 * method: a blast-resistance tooltip that only fires above 50. This block's resistance is
 * <b>zero</b>, so it would not fire, and {@code AddonBlockBase} stays unported for the same reason
 * the amat charger left it unported. Whichever addon block first exceeds 50 is where that tooltip
 * belongs.
 *
 * <h2>Upstream ships no model for any of its eight test blocks</h2>
 *
 * There is no blockstate, model, texture or lang entry for {@code test_fluid_voider} anywhere in
 * the addon's resources, so in 1.12.2 it renders as the missing-model cube with a raw translation
 * key under it. That cannot be carried over literally: this port's client sweep treats a missing
 * model as a failure, and rightly. It gets a {@code cube_all} of the base mod's own debug-block
 * texture, {@code hbm:block/event_tester}, and an English name — the smallest choice that invents
 * no art and leaves the block identifiable as what it is.
 */
public class VoiderBlock extends BlockContainerBase implements ILookOverlay {

	public VoiderBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VoiderTE(LeafiaBlockEntities.FLUID_VOIDER.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.FLUID_VOIDER.get(), VoiderTE::tick);
	}

	/**
	 * A held fluid identifier sets what the voider eats.
	 *
	 * <p>Upstream's {@code onBlockActivated} on 1.21.1's item-first hook. <b>Unreachable today</b>:
	 * nothing in this port implements {@code IItemFluidIdentifier} yet, so the voider is stuck on
	 * the spent steam its tank starts with. Ported anyway rather than deferred, because the
	 * interface it needs is here and the items are somebody else's batch — see D-705.
	 */
	@Override
	public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {

		if(!(stack.getItem() instanceof IItemFluidIdentifier identifier))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if(!level.isClientSide && level.getBlockEntity(pos) instanceof VoiderTE voider) {
			voider.buffer.setTankType(identifier.getType(level, pos, stack));
		}

		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * The panel beside the crosshair: what it is eating and how much of it went down last tick.
	 *
	 * <p>Both numbers come off the client's copy of the tank, which only {@link VoiderTE}'s sync
	 * packet ever fills in — so this line is also the visible end of the addon's own packet
	 * channel, and it reads {@code 0mB} if that channel is broken.
	 */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		List<String> text = new ArrayList<>();

		if(world.getBlockEntity(pos) instanceof VoiderTE voider) {
			text.add(ChatFormatting.GREEN + "-> " + ChatFormatting.RESET
					+ voider.buffer.getTankType().getLocalizedName() + ": "
					+ voider.buffer.getFill() + "/" + voider.buffer.getMaxFill() + "mB");
		}

		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()),
				0xFFFF55, 0x3F3F15, text);
	}
}
