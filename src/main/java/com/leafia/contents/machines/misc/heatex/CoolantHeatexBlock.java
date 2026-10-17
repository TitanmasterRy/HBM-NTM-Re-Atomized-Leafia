package com.leafia.contents.machines.misc.heatex;

import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `coolant_heatex`: the coolant heat exchanger's block - a 7 by 3 by 3 machine with six ports on its top row.
 *
 * <p>Upstream: {@code .../heatex/CoolantHeatexBlock.java}, on {@code AddonBlockDummyable}, which D-1028 found
 * is registration bookkeeping over {@link BlockDummyable}. It is the base mod's powered condenser to the block:
 * the same {@code {2, 0, 1, 1, 3, 3}} with an offset of one, and the same six {@code EXTRA} blocks one up at
 * three out on either side and one out forward and back on both - which is where the ducts and cables meet the
 * machine, and what {@code CoolantHeatexTE.getConPos} stands one block further out from.
 *
 * <h2>The ports need a type of the addon's own</h2>
 *
 * Upstream gives each extra a {@code TileEntityProxyCombo().fluid().power()}. 1.21.1 needs a registered
 * {@link BlockEntityType} for it, and it cannot be the base mod's {@code MULTIBLOCK_DUMMY}, whose valid blocks are a
 * fixed list that must not take this block (rule 11), so the addon registers
 * {@link LeafiaBlockEntities#COOLANT_HEATEX_DUMMY}, as the boiler did. The same type carries the Forge-fluid
 * capability that lets a Special Pipe connect to a port ({@code LeafiaCapabilities}).
 *
 * <h2>The tooltip</h2>
 *
 * The boiler and condenser badges, and upstream's two lines of apology for the model, unedited.
 */
public class CoolantHeatexBlock extends BlockDummyable {

	public static final MapCodec<CoolantHeatexBlock> CODEC = simpleCodec(CoolantHeatexBlock::new);

	public CoolantHeatexBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends CoolantHeatexBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new CoolantHeatexTE(pos, state);
			// Upstream's TileEntityProxyCombo().fluid().power()
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.COOLANT_HEATEX_DUMMY.get(), pos, state).fluid().power();
			case DUMMY -> null;
		};
	}

	/** Ticks on both sides: the server runs the machine and the client spins the fans. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.COOLANT_HEATEX.get(), CoolantHeatexTE::tick);
	}

	/** Upstream's {@code {2, 0, 1, 1, 3, 3}}. */
	@Override
	public int[] getDimensions() {
		return new int[] { 2, 0, 1, 1, 3, 3 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos corePos = pos.relative(dir, o);
		Direction rot = dir.getClockWise();

		this.makeExtra(world, corePos.relative(rot, 3).above());
		this.makeExtra(world, corePos.relative(rot.getOpposite(), 3).above());
		this.makeExtra(world, corePos.relative(dir).relative(rot).above());
		this.makeExtra(world, corePos.relative(dir).relative(rot.getOpposite()).above());
		this.makeExtra(world, corePos.relative(dir.getOpposite()).relative(rot).above());
		this.makeExtra(world, corePos.relative(dir.getOpposite()).relative(rot.getOpposite()).above());
	}

	/** Upstream's {@code standardOpenBehavior}: any part opens the core's screen unless the player is sneaking. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return player.isShiftKeyDown() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		if(player.isShiftKeyDown()) return InteractionResult.PASS;

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.FAIL;

		if(level.getBlockEntity(core) instanceof CoolantHeatexTE heatex && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(heatex, core);

		return InteractionResult.SUCCESS;
	}

	/** Drawn entirely by {@link CoolantHeatexRender}. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addBoiler(tooltip);
		MachineTooltip.addCondenser(tooltip);
		tooltip.add(Component.literal("Yes, yes, I know this is just a sloppy recolor."));
		tooltip.add(Component.literal("I can't fucking make the texture for a new model ok?"));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
