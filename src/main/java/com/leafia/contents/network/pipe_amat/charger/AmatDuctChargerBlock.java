package com.leafia.contents.network.pipe_amat.charger;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.ILookOverlay;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.init.LeafiaBlockEntities;

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
 * `amat_duct_charger`, upstream's "Electromagnetic Fluid Duct Charger".
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/charger/AmatDuctChargerBlock.java}.
 *
 * <h2>Its base class does not port, and does not need to</h2>
 *
 * Upstream extends {@code AddonBlockBaked}, which the dependency tools call a blocker on
 * {@code IDynamicModels}. Reading it, it is not one: the whole of {@code AddonBlockBaked} is
 * 1.12.2 model plumbing — fetch an {@code IModel}, retexture it, bake it, and put the result in
 * the model registry under {@code "normal"} and {@code "inventory"} — and its parent
 * {@code AddonBlockBase} is registration boilerplate (set the registry name, set the harvest
 * level, add yourself to a list). In 1.21.1 the first is a {@code cube_all} model JSON and the
 * second is {@code DeferredRegister}. That is D-019's conclusion reaching the addon, and it
 * means **no addon block base needs writing at all** — this extends the base mod's
 * {@link BlockContainerBase} directly. See D-1015.
 *
 * <p>The one part of {@code AddonBlockBase} that is behaviour rather than plumbing is its
 * tooltip: a blast-resistance line when the resistance exceeds 50. This block's is 6, so it
 * would not fire; whichever addon block first exceeds 50 is where that belongs.
 *
 * <h2>It is a duct that is also a machine</h2>
 *
 * The block is a plain cube, but its block entity extends {@code AmatDuctTE}, so it joins the
 * amat network exactly like a length of pipe and neighbouring pipes connect to it without a
 * special case — {@code AmatDuctStandard}'s "is this a plain duct" test already answers no for
 * it.
 */
public class AmatDuctChargerBlock extends BlockContainerBase implements ILookOverlay {

	public AmatDuctChargerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AmatDuctChargerTE(LeafiaBlockEntities.AMAT_DUCT_CHARGER.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.AMAT_DUCT_CHARGER.get(), AmatDuctChargerTE::tick);
	}

	/**
	 * A held fluid identifier sets what this charger — and so the run of pipe it is on —
	 * carries.
	 *
	 * <p>Upstream's {@code onBlockActivated}, on 1.21.1's item-first interaction hook.
	 * {@code IItemFluidIdentifier} is ported here, unlike on the base mod's own ducts where the
	 * same interaction is still waiting (D-705) — those additionally want the modifier keys that
	 * decide between one duct and a whole run, and this block has no such choice to make.
	 */
	@Override
	public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {

		if(!(stack.getItem() instanceof IItemFluidIdentifier identifier))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if(!level.isClientSide && level.getBlockEntity(pos) instanceof AmatDuctChargerTE charger) {
			charger.setType(identifier.getType(level, pos, stack));
		}

		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		if(!(world.getBlockEntity(pos) instanceof AmatDuctChargerTE charger)) return;

		List<String> text = new ArrayList<>();
		text.add("&[" + charger.getFluidType().getColor() + "&]" + charger.getFluidType().getLocalizedName());
		text.add(I18nUtil.resolveKey("tile.amat_duct.power", charger.getStoredPower() + " HE"));
		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()), 0xffff00, 0x404000, text);
	}
}
