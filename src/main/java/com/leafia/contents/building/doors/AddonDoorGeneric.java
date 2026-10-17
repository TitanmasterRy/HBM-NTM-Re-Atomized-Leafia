package com.leafia.contents.building.doors;

import java.util.List;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.generic.DoorDecl;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.generic.BlockDoorGeneric;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The addon's door block: the base mod's {@code BlockDoorGeneric}, radiation-resistant while it is
 * shut, with a shape that follows its {@link DoorDecl}.
 *
 * <p>Upstream: {@code com/leafia/contents/building/doors/AddonDoorGeneric.java}, whose body is the
 * constructor, taking the door off the base mod's block list and onto the addon's, and a block
 * entity that exists only for the core ({@code meta >= 12}). The rest is {@code BlockDoorGeneric}'s
 * in NTM-CE, and the base mod's port of it is a documented partial, so what this adds to the
 * partial is listed here.
 *
 * <h2>Radiation</h2>
 *
 * CE's {@code BlockDoorGeneric(Material, DoorDecl, boolean isRadResistant, String)} keeps the flag,
 * answers {@code isRadResistant(world, pos)} with "the flag, and the core is shut", and tells the
 * radiation system a section changed when any part of the door is placed or broken. All three are
 * here: {@link #isRadResistant(Level, BlockPos)}, and {@link #onPlace}/{@link #onRemove} calling
 * {@link ChunkRadiationManager#markSectionForRebuild}. The base mod's interface is the 1.7.10 one,
 * {@link IRadResistantBlock#getResistance()}, which is a flag with no world to ask - so
 * {@link #getResistance()} says 1 for a door that can ever shield, and the question that depends
 * on whether it is shut is the addon's own method. The base's handler for the per-section system
 * ({@code ChunkRadiationHandlerNT}) is not ported, so nothing reads either yet; the handler it does
 * have (PRISM) takes the marks. See D-3005.
 *
 * <h2>The shape</h2>
 *
 * The base mod's {@code BlockDoorGeneric} leaves every part a full cube open or shut, and lists
 * its per-part box among the things not ported. This is it, for the addon's doors: the same
 * rotation and reflection upstream's {@code getBoundingBox} does from the core's facing, around
 * {@link DoorDecl#getBlockBound}. Without it an open crimson door is a solid wall, since opening
 * only turns a part into an "extra" and says nothing about whether the part can be walked through.
 * A cell is open when it is an extra or when the core says its own cell is
 * ({@link BlockEntityDoorGeneric#shouldUseBB}).
 */
public class AddonDoorGeneric extends BlockDoorGeneric implements IRadResistantBlock {

	public final boolean isRadResistant;

	public AddonDoorGeneric(Properties properties, DoorDecl type, boolean isRadResistant) {
		super(properties, type);
		this.isRadResistant = isRadResistant;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if(state.getValue(PART) != Part.CORE) return null;
		return new AddonDoorTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.ADDON_DOOR.get(), AddonDoorTE::tick);
	}

	// ---- radiation ------------------------------------------------------------------------------

	/** Upstream's {@code isRadResistant(world, pos)}: the flag, and the door this cell belongs to is shut. */
	public boolean isRadResistant(Level level, BlockPos pos) {

		if(!isRadResistant || level == null) return false;

		BlockPos core = findCore(level, pos);
		if(core == null) return false;

		return level.getBlockEntity(core) instanceof BlockEntityDoorGeneric door && door.state == BlockEntityDoorGeneric.STATE_CLOSED;
	}

	/** The base mod's flag: 1 for a door that shields, which is how its handler reads it. */
	@Override
	public int getResistance() {
		return isRadResistant ? 1 : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		// 1.12.2's onBlockAdded fires when the block changes, not when one of its states does.
		if(isRadResistant && !oldState.is(state.getBlock()))
			ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(isRadResistant && !state.is(newState.getBlock()))
			ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/**
	 * Tells the radiation system when a door starts to open, starts to close or finishes closing, which
	 * is CE's {@code toggle()} and the end of its close; not when it finishes opening, because nothing
	 * about the shielding changes then.
	 *
	 * <p>CE marks from the click that toggles the door. This tree's base door changes its state in a
	 * click too but has no radiation hook, so each door's ticker calls this with the state it last
	 * reported and sees the same transitions a tick later.
	 *
	 * @param seen the state last reported, or -1 before the first tick, which reports nothing
	 * @return the state to remember
	 */
	public static byte tellRadiation(Level level, BlockPos pos, BlockEntityDoorGeneric door, byte seen) {

		if(seen < 0) return door.state;
		if(door.state == seen) return seen;

		if(door.state != BlockEntityDoorGeneric.STATE_OPEN
				&& door.getBlockState().getBlock() instanceof AddonDoorGeneric block && block.isRadResistant)
			ChunkRadiationManager.markSectionForRebuild(level, pos);

		return door.state;
	}

	/**
	 * Every chunk section a door at {@code core} occupies, marked for a radiation rebuild. Upstream's
	 * {@code getOccupiedSections()}: the box {@code fillSpace} filled, in 16-block steps.
	 */
	public static int markOccupiedSections(Level level, BlockPos core, BlockState coreState, DoorDecl type) {

		Direction dir = coreState.getValue(DIR);
		BlockPos centre = core.relative(dir, type.getBlockOffset());
		int[] rot = MultiblockHandlerXR.rotate(type.getDimensions(), dir);

		int minX = (centre.getX() - rot[4]) >> 4, maxX = (centre.getX() + rot[5]) >> 4;
		int minY = (centre.getY() - rot[1]) >> 4, maxY = (centre.getY() + rot[0]) >> 4;
		int minZ = (centre.getZ() - rot[2]) >> 4, maxZ = (centre.getZ() + rot[3]) >> 4;

		int count = 0;
		for(int sx = minX; sx <= maxX; sx++)
			for(int sy = minY; sy <= maxY; sy++)
				for(int sz = minZ; sz <= maxZ; sz++) {
					ChunkRadiationManager.markSectionForRebuild(level, new BlockPos(sx << 4, sy << 4, sz << 4));
					count++;
				}
		return count;
	}

	// ---- shape ----------------------------------------------------------------------------------

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeOf(state, level, pos, false);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeOf(state, level, pos, true);
	}

	/** The cell's box in its own coordinates, which the shape is built from. */
	public AABB boundOf(BlockState state, BlockGetter level, BlockPos pos, boolean forCollision) {

		BlockPos core = findCore(level, pos);
		if(core == null) return new AABB(0, 0, 0, 1, 1, 1);

		BlockState coreState = level.getBlockState(core);
		Direction dir = coreState.getValue(DIR);

		boolean open = state.getValue(PART) == Part.EXTRA
				|| (level.getBlockEntity(pos) instanceof BlockEntityDoorGeneric door && door.shouldUseBB);

		// Upstream rotates the offset into the door's own frame, asks the decl, then maps the answer
		// back by the core's facing: two different rotations, which is why this is not one.
		BlockPos rel = pos.subtract(core).rotate(blockRotation(dir).getRotated(Rotation.COUNTERCLOCKWISE_90));
		AABB box = type.getBlockBound(rel.getX(), rel.getY(), rel.getZ(), open, forCollision);

		return switch(dir) {
			case NORTH -> new AABB(1 - box.minX, box.minY, 1 - box.minZ, 1 - box.maxX, box.maxY, 1 - box.maxZ);
			case WEST -> new AABB(1 - box.minZ, box.minY, box.minX, 1 - box.maxZ, box.maxY, box.maxX);
			case SOUTH -> box;
			case EAST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
			default -> new AABB(0, 0, 0, 1, 1, 1);
		};
	}

	private VoxelShape shapeOf(BlockState state, BlockGetter level, BlockPos pos, boolean forCollision) {
		// A box with no volume is no shape: the player walks through it, and a ray does too.
		return Shapes.create(boundOf(state, level, pos, forCollision));
	}

	/** Upstream's {@code ForgeDirection.getBlockRotation}, as the base mod's door block entity already has it. */
	public static Rotation blockRotation(Direction dir) {
		return switch(dir) {
			case NORTH -> Rotation.NONE;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			case WEST -> Rotation.CLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	// ---- tooltip --------------------------------------------------------------------------------

	/** Upstream's {@code addInformation}: the shielding line first, then the blast line above 50. */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if(isRadResistant)
			tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
		BlastResistanceTooltip.add(this, tooltip);
	}
}
