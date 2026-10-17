package com.leafia.contents.machines.misc.wind_turbines.medium;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.misc.wind_turbines.WindTurbineTEBase;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;
import com.llib.math.SIPfx;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * `wind_turbine_medium`: a six-block mast that has to stand two chunks clear of another turbine.
 *
 * <p>Upstream: {@code .../wind_turbines/medium/WindTurbineMediumBlock.java}.
 *
 * <h2>Its base class dissolves, for the third time</h2>
 *
 * Upstream extends {@code AddonBlockDummyable}, and reading it leaves almost nothing. Three of
 * its members are 1.12.2 registration bookkeeping ({@code ModBlocks.ALL_BLOCKS.remove},
 * {@code AddonBlocks.ALL_BLOCKS.add}, {@code IDynamicModels.INSTANCES.remove}); {@code makeExtra}
 * and abstract {@code getDimensions} are already on the base mod's {@link BlockDummyable}; and
 * {@code getRotationMat} has exactly one consumer anywhere upstream, the mixing vat, so it lands
 * with the mixing vat rather than ahead of it.
 *
 * <p>What is left is real and is <b>not</b> on this class: three overrides that stop mobs
 * spawning on large machines. 1.21.1 has no such override point — spawning is a
 * {@code BlockBehaviour.Properties} predicate — so it moves to the registration, where
 * {@code LeafiaBlocks.noSpawns} applies it. Third addon block base to dissolve after
 * {@code AddonBlockBase} and {@code AddonBlockBaked} (D-1015, D-1026); see D-1028.
 *
 * <h2>Two chunks of clearance, checked at the right moment</h2>
 *
 * Upstream refuses placement from {@code canPlaceBlockAt} if any turbine's block entity is in the
 * 3×3 chunks around the target. The literal translation is {@code canSurvive}, and it would be
 * <b>wrong</b>: vanilla re-checks that on every neighbour update, so the moment the turbine was
 * placed it would find <em>itself</em> and break. {@link BlockDummyable#checkRequirement} is the
 * hook that means what upstream's meant — it runs once, at placement, and refunds the item — so
 * that is what this overrides.
 */
public class WindTurbineMediumBlock extends BlockDummyable implements ILookOverlay, ITooltipProvider {

	public static final MapCodec<WindTurbineMediumBlock> CODEC = simpleCodec(WindTurbineMediumBlock::new);

	public WindTurbineMediumBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends WindTurbineMediumBlock> codec() {
		return CODEC;
	}

	/** Six up and nothing sideways: a mast. */
	@Override public int[] getDimensions() { return new int[] { 6, 0, 0, 0, 0, 0 }; }
	@Override public int getOffset() { return 0; }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new WindTurbineMediumTE(LeafiaBlockEntities.WIND_TURBINE_MEDIUM.get(), pos, state);
			case EXTRA, DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.WIND_TURBINE_MEDIUM.get(), WindTurbineMediumTE::tick);
	}

	/**
	 * Upstream's spacing rule: no second turbine anywhere in the 3×3 chunks around this one.
	 *
	 * <p>It walks the block entities of nine chunks, which loads them if they are not loaded —
	 * upstream's cost, kept, because doing it on loaded chunks only would make the rule depend on
	 * where the player happens to be standing.
	 */
	@Override
	protected boolean checkRequirement(Level world, BlockPos pos, Direction dir, int o) {

		for(int x = -1; x <= 1; x++) {
			for(int z = -1; z <= 1; z++) {

				LevelChunk chunk = world.getChunkAt(pos.offset(x * 16, 0, z * 16));

				for(BlockEntity te : chunk.getBlockEntities().values()) {
					if(te instanceof WindTurbineTEBase) return false;
				}
			}
		}

		return super.checkRequirement(world, pos, dir, o);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addGenerator(tooltip);
		addStandardInfo(((Block) this).getDescriptionId() + ".desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/*
	 * Upstream also has onBlockActivated: a right-click with a lubricant canister fills the tank
	 * through FluidTankNTM.loadTank(slot, slot, handler). That method does not exist on this
	 * port's FluidTank at all — loading a tank from an item slot needs IItemFluidIdentifier and
	 * the canister items, which are unported (see FluidTank's own note). It is a base-mod
	 * hand-off, not something the addon can add, so the interaction is left out and the turbine
	 * runs on the 0.05 floor of its lubricant term until it lands. See D-1028.
	 */

	/**
	 * The panel beside the crosshair: lubricant, output, and a blinking warning when the rotor is
	 * sheltered.
	 *
	 * <p>{@code BobMathUtil.getBlink} is what makes the warning flash, and it is the base mod's
	 * own helper — the colour alternates per half-second rather than the text appearing and
	 * disappearing, so the line does not jump.
	 */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		BlockPos corePos = this.findCore(world, pos);
		if(corePos == null) return;

		if(!(world.getBlockEntity(corePos) instanceof WindTurbineTEBase turbine)) return;

		List<String> text = new ArrayList<>();

		text.add(ChatFormatting.YELLOW + "-- " + ChatFormatting.RESET
				+ turbine.lube.getTankType().getLocalizedName() + ": "
				+ turbine.lube.getFill() + "/" + turbine.lube.getMaxFill() + "mB");

		text.add(ChatFormatting.RED + "<- " + ChatFormatting.RESET + SIPfx.auto(turbine.getPower()) + "HE");

		if(turbine.obstructed) {
			text.add("&[" + (BobMathUtil.getBlink() ? 0xff0000 : 0xffff00) + "&]"
					+ I18nUtil.resolveKey("tile.wind_turbine.obstructed"));
		}

		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()),
				0xffff00, 0x404000, text);
	}
}
