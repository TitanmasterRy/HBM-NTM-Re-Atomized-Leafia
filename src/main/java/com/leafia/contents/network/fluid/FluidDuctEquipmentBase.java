package com.leafia.contents.network.fluid;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.ILookOverlay;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The base for the addon's in-line fluid duct equipment — the valves, and the gauges when they
 * land.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/FluidDuctEquipmentBase.java}. These are
 * <em>the base mod's</em> ducts with something built into them, so unlike the antimatter pipe
 * they join the ordinary fluid network and connect to ordinary pipes. What they add is a
 * one-block-long body with a thin collision box lying along the run.
 *
 * <h2>The axis is read off the block entity, and it carries a live bug</h2>
 *
 * {@link #getAxis} is upstream's, and its middle branch reads {@code face == 0 || face == 2}
 * while {@link FluidDuctEquipmentTE#face} is only ever set to -1, 0 or 1 by
 * {@link #setPlacedBy}. So the {@code == 2} arm is dead, and **a valve placed while looking
 * roughly level always gets an X-axis collision box no matter which way the pipe runs** — place
 * one in a north-south run and its hitbox lies east-west. {@code direction}, which knows the
 * answer, is never consulted.
 *
 * <p>Preserved rather than corrected, per D-021: it is reachable by any player who places a
 * valve, and fixing it changes where a block can be stood on and clicked. Pinned by
 * {@code LeafiaValveTests.placementAxisReproducesUpstreamsBug} so a later tidy-up is loud. See
 * D-1016.
 */
public abstract class FluidDuctEquipmentBase extends BlockContainerBase implements ILookOverlay {

	/** Upstream's 0.3125/0.6875 — the same thin core the base mod's ducts use. */
	private static final double LOWER = 0.3125D;
	private static final double UPPER = 0.6875D;

	private static final VoxelShape SHAPE_X = Shapes.box(0D, LOWER, LOWER, 1D, UPPER, UPPER);
	private static final VoxelShape SHAPE_Y = Shapes.box(LOWER, 0D, LOWER, UPPER, 1D, UPPER);
	private static final VoxelShape SHAPE_Z = Shapes.box(LOWER, LOWER, 0D, UPPER, UPPER, 1D);

	protected FluidDuctEquipmentBase(Properties properties) {
		super(properties);
	}

	/**
	 * Upstream's {@code getRenderType() == ENTITYBLOCK_ANIMATED}: the body is drawn entirely by
	 * {@link FluidDuctEquipmentRender}, because the handwheel turns and the pipe stub is tinted
	 * with whatever the run is carrying. The blockstate model is therefore particle-only, the
	 * shape D-1019 established for exactly this case.
	 */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	/**
	 * Which way the body lies. Upstream's {@code getAxis}, dead branch and all — see the class
	 * note.
	 */
	public static Direction.Axis getAxis(BlockGetter level, BlockPos pos) {

		if(level.getBlockEntity(pos) instanceof FluidDuctEquipmentTE duct) {
			if(duct.vertical) return Direction.Axis.Y;
			if(duct.face == 0 || duct.face == 2) return Direction.Axis.X;
		}

		return Direction.Axis.Z;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch(getAxis(level, pos)) {
			case X -> SHAPE_X;
			case Y -> SHAPE_Y;
			case Z -> SHAPE_Z;
		};
	}

	/**
	 * Works out the orientation from where the player was standing and looking.
	 *
	 * <p>Upstream's {@code onBlockPlacedBy}, kept literally: {@code vertical} is true when the
	 * placer's line of sight passes through the block above or below this one — with the guards
	 * that stop a player standing inside the block's own layer from triggering both — and
	 * {@code face} is -1 looking steeply up, 1 steeply down, 0 otherwise. The 0.707 is a
	 * 45-degree cone, written as a raw component rather than an angle.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {

		super.setPlacedBy(level, pos, state, placer, stack);

		if(placer == null || !(level.getBlockEntity(pos) instanceof FluidDuctEquipmentTE duct)) return;

		AABB top = new AABB(pos.above());
		AABB btm = new AABB(pos.below());
		Vec3 eye = placer.getEyePosition(0);
		Vec3 far = eye.add(placer.getLookAngle().scale(10));

		duct.vertical = false;
		if(eye.y < pos.getY() + 1) duct.vertical = duct.vertical || top.clip(eye, far).isPresent();
		if(eye.y > pos.getY()) duct.vertical = duct.vertical || btm.clip(eye, far).isPresent();

		duct.direction = placer.getDirection();

		if(placer.getLookAngle().y > 0.707) duct.face = -1;
		else if(placer.getLookAngle().y < -0.707) duct.face = 1;
		else duct.face = 0;

		duct.setChanged();
		if(!level.isClientSide) duct.networkPackNT(FluidDuctEquipmentTE.SYNC_RANGE);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		if(!(world.getBlockEntity(pos) instanceof BlockEntityPipeBaseNT duct)) return;

		List<String> text = new ArrayList<>();
		text.add("&[" + duct.getFluidType().getColor() + "&]" + duct.getFluidType().getLocalizedName());
		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()), 0xffff00, 0x404000, text);
	}
}
