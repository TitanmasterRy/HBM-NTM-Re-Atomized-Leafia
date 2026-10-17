package com.leafia.contents.building.doors.special.reactor_door;

import java.util.List;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.generic.DoorDecl;
import com.hbm.blocks.BlockDummyable;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.building.doors.AddonDoorGeneric;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * `reactor_door`: a plug door that turns a handle, swings on a hinge, and takes the paint of the
 * block you hold.
 *
 * <p>Upstream: {@code .../doors/special/reactor_door/ReactorDoorBlock.java}. Three things on top of
 * {@link AddonDoorGeneric}: its own block entity, a screwdriver that paints, and a line in the
 * tooltip saying so.
 *
 * <h2>Painting</h2>
 *
 * Sneak with a screwdriver in the main hand and a block in the off hand. The click lands on one
 * of the door's cells; the cell is placed relative to the core, looking along the door's facing
 * ({@code FiaMatrix.toObjectSpace}), and a cell in front of the plane through the core
 * ({@code x < 1}) paints the front face {@link ReactorDoorTE#skinA}, any other the back
 * {@link ReactorDoorTE#skinB}. With nothing in the off hand the same click cycles the door's skin
 * instead - the base door's own behaviour.
 *
 * <p>Upstream also skips any block whose render type is not a plain model, but the test is
 * {@code block.getBlock() instanceof IBlockProperties}, and a {@code Block} is never one - it is an
 * interface of {@code IBlockState} - so it never skips. Every block paints, and so does this: a block
 * drawn by its renderer paints with whatever its model offers as a particle.
 *
 * <p>Upstream answers false on the client, because everything sits inside {@code !world.isRemote}.
 * <b>This answers true</b>, because {@code ItemTooling} turns a false on the client into a second
 * attempt with the off hand - which here holds a block, and would place it. The server's answer is
 * the one that counts and is unchanged.
 */
public class ReactorDoorBlock extends AddonDoorGeneric {

	public ReactorDoorBlock(Properties properties, DoorDecl type, boolean isRadResistant) {
		super(properties, type, isRadResistant);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if(state.getValue(PART) != Part.CORE) return null;
		return new ReactorDoorTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.REACTOR_DOOR.get(), ReactorDoorTE::tick);
	}

	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {

		if(tool != ToolType.SCREWDRIVER || !player.isShiftKeyDown()) return false;

		BlockPos core = findCore(world, pos);
		if(core == null) return false;

		if(!(world.getBlockEntity(core) instanceof ReactorDoorTE door)) return false;

		BlockState paint = ReactorDoorTE.paintOf(player.getOffhandItem());

		if(paint != null) {
			if(world.isClientSide) return true;

			// Where the clicked cell is, with the core as the origin and the door's facing as forward.
			Direction facing = world.getBlockState(core).getValue(BlockDummyable.DIR);
			FiaMatrix mat = new FiaMatrix(Vec3.atLowerCornerOf(core), Vec3.atLowerCornerOf(core.relative(facing)))
					.toObjectSpace(new FiaMatrix(Vec3.atLowerCornerOf(pos)));

			if(mat.getX() < 1)
				door.skinA = paint;
			else
				door.skinB = paint;

			door.setChanged();
			door.networkPackNT(100);
			return true;
		}

		if(door.getDoorType().hasSkins()) {
			if(world.isClientSide) return true;
			door.cycleSkinIndex();
			return true;
		}

		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		for(String line : I18nUtil.resolveKey("tile.reactor_door.paint").split("\\$"))
			tooltip.add(Component.literal(line));
	}
}
