package com.leafia.contents.machines.misc.modular_turbine.core;

import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.sound.NtmSounds;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeError;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code modular_turbine_core}: the one block a player clicks to build a turbine.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/core/MTCoreBlock.java}.
 *
 * <h2>Clicking it</h2>
 *
 * <ul>
 * <li>With an <b>overdrive upgrade</b> in hand, and no upgrade fitted yet, the upgrade goes in and
 *     the governed speed rises by {@link MTCoreTE#getRPSBoost} — 10, 25 or 40 rps.</li>
 * <li>With anything else, if the turbine is not assembled, the core walks the shaft. A failure is
 *     reported in chat with its reason.</li>
 * </ul>
 *
 * Breaking it drops a fitted upgrade.
 *
 * <h2>It is drawn by a model, not a renderer</h2>
 *
 * Upstream draws the core with {@code MTCoreRender}, a 25-line block entity renderer whose whole
 * body is "rotate by facing, draw the {@code Core} group of {@code export.obj}". Nothing in it
 * moves, so it is a baked model here: {@link #getRenderShape} answers {@link RenderShape#MODEL},
 * and the blockstate rotates one extracted OBJ by facing. {@code MTCoreRender} dissolves the way
 * the elevator item renderers did (D-1043). A generator script
 * extracts the group and carries the rotation table.
 *
 * <h2>What waits</h2>
 *
 * <ul>
 * <li>The <b>screwdriver</b> takes the upgrade back out. It is gated on {@code ItemTooling}, a
 *     base-mod item that is not ported — the same blocker as the elevators' three screens
 *     (D-1045). Until it lands an upgrade comes out only by breaking the core.</li>
 * <li>The <b>error highlight</b>. Upstream also sends the client a {@code TurbineErrorHighlight}
 *     that outlines the two blocks at fault for eight seconds, drawn by
 *     {@code TopRender.Highlight}, which is not ported. The chat line says what is wrong; it does
 *     not yet point at where.</li>
 * </ul>
 */
public class MTCoreBlock extends ModularTurbineBlockBase {

	public static final MapCodec<MTCoreBlock> CODEC = simpleCodec(MTCoreBlock::new);

	public MTCoreBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends MTCoreBlock> codec() {
		return CODEC;
	}

	@Override
	public int shaftHeight() {
		return 0;
	}

	@Override
	public TurbineComponentType componentType() {
		return null;
	}

	@Override
	public int[] canConnectTo() {
		return new int[] {}; // doesn't matter for the core
	}

	@Override
	public int size() {
		return 0; // doesn't matter for the core
	}

	@Override
	public double weight() {
		return 0;
	}

	/** A single block: every extent is zero. */
	@Override
	public int[] getDimensions() {
		return new int[] { 0, 0, 0, 0, 0, 0 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** A baked model, not the multiblock default — see the class doc. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new MTCoreTE(LeafiaBlockEntities.MODULAR_TURBINE_CORE.get(), pos, state);
			case EXTRA, DUMMY -> null;
		};
	}

	/** Both sides: the client turns the shaft, the server runs it. Upstream's {@code ITickable}. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.MODULAR_TURBINE_CORE.get(), MTCoreTE::tick);
	}

	/** Upstream's three overdrive tiers, as items. */
	public static Item getOverdriveUpgradeFromInt(int tier) {
		return switch(tier) {
			case 1 -> ModItems.upgrade_overdrive_1.get();
			case 2 -> ModItems.upgrade_overdrive_2.get();
			case 3 -> ModItems.upgrade_overdrive_3.get();
			default -> null;
		};
	}

	/**
	 * Upstream's {@code onBlockActivated}, split the way 1.21.1 splits a click: an overdrive
	 * upgrade is handled here, and anything else passes on to {@link #useWithoutItem}, which
	 * vanilla calls next for the main hand whatever is held.
	 */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level worldIn, BlockPos pos,
			Player playerIn, InteractionHand hand, BlockHitResult hit) {
		if(worldIn.getBlockEntity(pos) instanceof MTCoreTE core) {
			if(stack.getItem() instanceof ItemMachineUpgrade upgrade) {
				if(upgrade.type == UpgradeType.OVERDRIVE) {
					if(!worldIn.isClientSide) {
						if(core.overdrive == 0) {
							core.overdrive = upgrade.tier;
							stack.shrink(1);
							playerIn.inventoryMenu.broadcastChanges();
							worldIn.playSound(null, pos, NtmSounds.ITEM_UPGRADE_PLUG.get(), SoundSource.BLOCKS, 1, 1);
							core.setChanged();
						}
					}
					return ItemInteractionResult.sidedSuccess(worldIn.isClientSide);
				}
			}
			// Upstream's ItemTooling SCREWDRIVER branch sits here, taking the upgrade back out.
			// ItemTooling is unported; see the class doc.
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/** An unassembled turbine assembles on click, and says why if it cannot. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player playerIn, BlockHitResult hit) {
		if(worldIn.getBlockEntity(pos) instanceof MTCoreTE core) {
			if(core.components.isEmpty()) {
				if(!worldIn.isClientSide) {
					AssemblyReturnCode code = core.reassemble();
					if(code instanceof ReturnCodeError error) {
						playerIn.sendSystemMessage(Component.translatable(errorKey(error.reason))
								.withStyle(ChatFormatting.RED));
					}
				}
				return InteractionResult.sidedSuccess(worldIn.isClientSide);
			}
		}
		return InteractionResult.PASS;
	}

	/** The chat line for an assembly error: {@code chat.turbine.error.} plus the reason in lower case. */
	public static String errorKey(MTCoreTE.AssemblyReturnCode.AssemblyErrorReason reason) {
		return "chat.turbine.error." + reason.name().toLowerCase();
	}

	/**
	 * Upstream's {@code breakBlock}: a fitted upgrade drops where the core stood. Read before
	 * {@code super}, because the block entity is gone after it.
	 */
	@Override
	protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!state.is(newState.getBlock()) && !world.isClientSide
				&& world.getBlockEntity(pos) instanceof MTCoreTE core) {
			Item item = getOverdriveUpgradeFromInt(core.overdrive);
			if(item != null) {
				core.overdrive = 0;
				Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item));
			}
		}
		super.onRemove(state, world, pos, newState, movedByPiston);
	}
}
