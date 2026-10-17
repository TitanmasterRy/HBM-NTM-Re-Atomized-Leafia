package com.leafia.contents.network.ff_duct.utility;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * What the Forge-fluid duct's in-line utilities - the pump, the filter and the converter - share:
 * a six-way facing, a thin box along it, a fluid identifier that sets what the utility carries, and
 * the green look overlay.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/utility/FFDuctUtilityBase.java}, an
 * {@code AddonBlockContainer}.
 *
 * <ul>
 * <li><b>Facing.</b> The direction the fluid goes <em>to</em>. Placed against a face, a utility points
 *     <em>into</em> it - the opposite of the face clicked - and sneaking reverses that; an off-hand
 *     placement reverses the sneak (upstream's {@code snek = !snek}). A placement with no player
 *     (a dispenser) is not sneaking.</li>
 * <li><b>Shape.</b> One box, 0.3125 to 0.6875 across and the whole block along the facing's axis
 *     (the outline and the collision are the same box). The block is not a full cube.</li>
 * <li><b>The identifier.</b> A held {@link IItemFluidIdentifier} on the server sets the utility's
 *     fluid ({@link FFDuctUtilityTEBase#setType}); the click is consumed on both sides, whatever
 *     the utility is, as upstream returns {@code true}. Anything else passes.</li>
 * <li><b>The overlay.</b> The fluid's name in its colour, the utility's own lines
 *     ({@link FFDuctUtilityTEBase#addInfo}), titled in green.</li>
 * </ul>
 *
 * <p>The look is a baked model: upstream's renderer ({@code FFDuctUtilityRender}) and its item renderer
 * dissolve into a generator script and the colour handler in {@code LeafiaClient}
 * (D-3019).
 */
public abstract class FFDuctUtilityBase extends Block implements EntityBlock, ILookOverlay {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	private static final VoxelShape SHAPE_X = Shapes.box(0.0D, 0.3125D, 0.3125D, 1.0D, 0.6875D, 0.6875D);
	private static final VoxelShape SHAPE_Y = Shapes.box(0.3125D, 0.0D, 0.3125D, 0.6875D, 1.0D, 0.6875D);
	private static final VoxelShape SHAPE_Z = Shapes.box(0.3125D, 0.3125D, 0.0D, 0.6875D, 0.6875D, 1.0D);

	protected FFDuctUtilityBase(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Upstream's {@code getStateForPlacement}: into the clicked face, or out of it while sneaking (reversed by the off hand). */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, facingFor(context.getClickedFace(), context.getPlayer() != null && context.getPlayer().isShiftKeyDown(),
				context.getHand() == InteractionHand.OFF_HAND));
	}

	public static Direction facingFor(Direction clicked, boolean sneaking, boolean offHand) {
		boolean snek = sneaking;
		if(offHand) snek = !snek;
		return snek ? clicked : clicked.getOpposite();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Upstream's {@code getBB}: the box along the facing's axis. */
	public static VoxelShape shapeFor(Axis axis) {
		return switch(axis) {
			case X -> SHAPE_X;
			case Y -> SHAPE_Y;
			case Z -> SHAPE_Z;
		};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state.getValue(FACING).getAxis());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if(stack.getItem() instanceof IItemFluidIdentifier identifier) {
			if(!level.isClientSide) {
				if(level.getBlockEntity(pos) instanceof FFDuctUtilityTEBase base)
					base.setType(identifier.getType(level, pos, stack));
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/**
	 * What the renderer set as the colour of a part: {@code max = 240/255} of each channel of the fluid's colour for the
	 * pipes (tint 1), and a grey for the box (tint 2) - 0.4 when the fluid's three channels average over a half,
	 * 0.65 otherwise. Any other index is white (the arrow). The model's materials carry the indices
	 * and the colour handler in {@code LeafiaClient} calls this.
	 */
	public static int tint(int fluidColour, int tintIndex) {
		float max = 240 / 255f;
		float red = (fluidColour >>> 16 & 0xFF) / 255f;
		float green = (fluidColour >>> 8 & 0xFF) / 255f;
		float blue = (fluidColour & 0xFF) / 255f;
		if(tintIndex == 1)
			return Math.round(red * max * 255) << 16 | Math.round(green * max * 255) << 8 | Math.round(blue * max * 255);
		if(tintIndex == 2) {
			float grey = (red + green + blue) / 3f > 0.5f ? 0.4f : 0.65f;
			int c = Math.round(grey * 255);
			return c << 16 | c << 8 | c;
		}
		return 0xFFFFFF;
	}

	/** The server-side ticker of one of the utilities: the block entity's {@code update}. */
	@Nullable
	protected static <T extends BlockEntity> BlockEntityTicker<T> serverTicker(Level level, BlockEntityType<T> type, BlockEntityType<?> expected) {
		return level.isClientSide || type != expected ? null : (l, p, s, be) -> ((FFDuctUtilityTEBase) be).update();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(!(world.getBlockEntity(pos) instanceof FFDuctUtilityTEBase duct)) return;
		List<String> text = new ArrayList<>();
		text.add("&[" + duct.getFluidType().getColor() + "&]" + duct.getFluidType().getLocalizedName());
		duct.addInfo(text);
		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(getDescriptionId()), 0x00ff00, 0x004000, text);
	}
}
