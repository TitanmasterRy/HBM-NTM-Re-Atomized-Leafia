package com.leafia.contents.machines.reactors.apr.blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hbm.blocks.IBlockMulti;
import com.hbm.blocks.ILookOverlay;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.util.InventoryUtil;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.dev.machine.MachineTooltip;

import api.hbm.block.IToolable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code apr_component}: the eight kinds of block an absorption particle reactor is built from.
 *
 * <p>Upstream: {@code .../reactors/apr/blocks/APRComponentBlock.java}, a metadata block whose
 * {@code VARIANT} is upstream's {@link APRComponentType} in its declared order. Here it is one block
 * with an {@link EnumProperty} and one item per variant ({@code apr_component_support} and the rest,
 * each answering to {@code block.leafia.apr_component.<variant>}), the base mod's D-034 rule.
 *
 * <ul>
 * <li><b>The torch assembles a hull.</b> A blowtorch on a {@code HULL} consumes one steel bolt and one
 *     mysticite blanket from the player and makes it {@code HULL_ASSEMBLED} (upstream's {@code onScrew},
 *     through {@code InventoryUtil.doesPlayerHaveAStacks(..., true)}); held over a plain hull the overlay
 *     lists the two.</li>
 * <li><b>Drops</b> are the loot table's (written by a generator script): each variant
 *     drops itself, and an assembled hull comes apart into a hull, a steel bolt and a mysticite blanket,
 *     upstream's {@code getDrops}.</li>
 * <li>Mobs do not spawn on it (upstream's {@code canCreatureSpawn}), which is a properties predicate
 *     here ({@code LeafiaBlocks.noSpawns}).</li>
 * </ul>
 */
public class APRComponentBlock extends Block implements IBlockMulti<APRComponentBlock.APRComponentType>, IToolable, ILookOverlay {

	public enum APRComponentType implements StringRepresentable {
		SUPPORT,
		HULL,
		HULL_ASSEMBLED,
		PLATING,
		PLATING_DARK,
		BLADES,
		MECHANICAL,
		COIL,
		;

		/** Upstream's {@code getName}: the constant in lower case. */
		@Override
		public String getSerializedName() {
			return name().toLowerCase();
		}
	}

	public static final EnumProperty<APRComponentType> VARIANT = EnumProperty.create("variant", APRComponentType.class);

	public APRComponentBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(VARIANT, APRComponentType.SUPPORT));
	}

	@Override
	public EnumProperty<APRComponentType> getVariantProperty() {
		return VARIANT;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(VARIANT);
	}

	/** The state a variant places as; the core's multiblock map is built from these. */
	public BlockState state(APRComponentType type) {
		return defaultBlockState().setValue(VARIANT, type);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return getVariantStack(state);
	}

	/** What the torch takes for one hull: a steel bolt and a mysticite blanket. */
	public static List<AStack> assemblyCost() {
		return Arrays.asList(new OreDictStack(OreDictManager.STEEL.bolt()),
				new ComparableStack(LeafiaResourceItems.get("blanket_mysticite").get()));
	}

	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {
		if(tool != ToolType.TORCH) return false;
		BlockState state = world.getBlockState(pos);
		if(state.getBlock() == this && state.getValue(VARIANT) == APRComponentType.HULL) {
			if(InventoryUtil.doesPlayerHaveAStacks(player, assemblyCost(), true)) {
				world.setBlock(pos, state(APRComponentType.HULL_ASSEMBLED), Block.UPDATE_ALL);
				return true;
			}
		}
		return false;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		Player player = Minecraft.getInstance().player;
		if(player == null) return;
		ToolType tool = ToolType.getType(player.getMainHandItem());
		if(tool != ToolType.TORCH) return;

		BlockState state = world.getBlockState(pos);
		if(state.getBlock() != this || state.getValue(VARIANT) != APRComponentType.HULL) return;

		List<String> text = new ArrayList<>();
		text.add(ChatFormatting.GOLD + "Requires:");
		ItemStack bolt = new OreDictStack(OreDictManager.STEEL.bolt()).extractForCyclingDisplay(20);
		text.add("- " + bolt.getHoverName().getString() + " x1");
		text.add("- " + new ItemStack(LeafiaResourceItems.get("blanket_mysticite").get()).getHoverName().getString() + " x1");

		String blockName = I18nUtil.resolveKey(getDescriptionId(APRComponentType.HULL));
		ILookOverlay.printGeneric(gui, blockName, 0xffff00, 0x404000, text);
	}
}
