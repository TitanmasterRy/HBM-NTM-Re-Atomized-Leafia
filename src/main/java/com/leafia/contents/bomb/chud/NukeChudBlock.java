package com.leafia.contents.bomb.chud;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.bomb.NukeBlockBase;
import com.hbm.blocks.generic.BlockDeadPlant;
import com.hbm.blocks.generic.BlockSellafield;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.interfaces.IBomb;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Fat Chud (D-3022): a Fat Man that does not explode. On a redstone signal (or any {@code IBomb} trigger) it
 * plays an explosion's sound at volume 20 and, through the 101-block cube round it ({@value #RANGE} each way):
 * <ul>
 * <li>grass becomes dead dirt, or one time in three oily dirt; leaves vanish; tall grass and ferns become a random
 *     dead plant; and any block whose registry name contains "brick" or "concrete" vanishes;</li>
 * <li>every multiblock machine whose core it finds is thrown {@code 10 + nextInt(15)} blocks into the air and rebuilt
 *     there (its core's contents spill as it is torn down);</li>
 * <li>fifteen towers of yellow barrels and sellafite (levels 3 to 5) rise at random spots in the cube, {@code 15 +
 *     nextInt(10)} high on a 5 x 5 base;</li>
 * <li>and a Gaussian of soot ({@code 10,000 x e^(-d^2 / 4.5)}, sigma 1.5 regions) is laid over the 7 x 7 pollution
 *     regions round it at once, rather than left to diffuse.</li>
 * </ul>
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/chud/NukeChudBlock.java}, {@code Material.IRON}, hardness 5, resistance
 * 6,000 (3,600 here by D-012's x0.6), in the nuke tab. Its {@code onBlockPlacedBy} sets the placer on a
 * {@code TileEntityNukeMan}, which this block never has, so it is dead and not ported.
 *
 * <p>Two things carried across that are worth knowing: rebuilding a lifted machine calls the base mod's protected
 * {@code BlockDummyable.fillSpace} reflectively, as upstream called it through a method handle ("thanks movblock");
 * and a part whose core cannot be found (a broken chain) is skipped, where upstream added {@code null} to its set and
 * then crashed reading it.
 */
public class NukeChudBlock extends Block implements EntityBlock, IBomb {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Blocks each way from the bomb. */
	public static final int RANGE = 50;
	public static final float SOUND_VOLUME = 20F;
	public static final int TOWERS = 15;
	/** Pollution regions each way, their sigma and the peak soot. */
	public static final int REGION_RADIUS = 3;
	public static final double SIGMA = 1.5;
	public static final float PEAK_SOOT = 10000F;

	public NukeChudBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Upstream's yaw-quadrant table, which is the base mod's bombs' too. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, NukeBlockBase.facingFor(context.getHorizontalDirection()));
	}

	/** {@code EnumBlockRenderType.INVISIBLE}, with the renderer drawing it; as the base mod's bombs. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NukeChudTE(pos, state);
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block blockIn, BlockPos fromPos, boolean movedByPiston) {
		if(world.hasNeighborSignal(pos) && !world.isClientSide) {
			world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			igniteTestBomb(world, pos.getX(), pos.getY(), pos.getZ());
		}
	}

	@Override
	public BombReturnCode explode(Level world, BlockPos pos) {
		if(!world.isClientSide) {
			world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			igniteTestBomb(world, pos.getX(), pos.getY(), pos.getZ());
			return BombReturnCode.DETONATED;
		}
		return BombReturnCode.UNDEFINED;
	}

	public static boolean igniteTestBomb(Level world, int x, int y, int z) {
		if(world.isClientSide) return false;
		world.playSound(null, new BlockPos(x, y, z), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, SOUND_VOLUME, 1);
		int range = RANGE;
		BlockPos.MutableBlockPos mbp = new BlockPos.MutableBlockPos();
		Set<BlockPos> set = new LinkedHashSet<>();
		for(int xo = -range; xo <= range; xo++) {
			for(int yo = -range; yo <= range; yo++) {
				for(int zo = -range; zo <= range; zo++) {
					mbp.set(x + xo, y + yo, z + zo);
					BlockState state = world.getBlockState(mbp);
					Block block = state.getBlock();
					if(block instanceof BlockDummyable dummyable) {
						BlockPos core = dummyable.findCore(world, mbp);
						if(core != null) set.add(core);
					}
					if(block == Blocks.GRASS_BLOCK) {
						int rand = world.random.nextInt(3);
						if(rand == 2) world.setBlock(mbp, ModBlocks.dirt_oily.get().defaultBlockState(), 3);
						else world.setBlock(mbp, ModBlocks.dirt_dead.get().defaultBlockState(), 3);
					} else if(block instanceof LeavesBlock) {
						world.setBlock(mbp, Blocks.AIR.defaultBlockState(), 3);
					} else if(block instanceof TallGrassBlock) {
						world.setBlock(mbp, deadPlant(world.random.nextInt(5)), 3);
					} else {
						String reg = BuiltInRegistries.BLOCK.getKey(block).toString();
						if(reg.contains("brick") || reg.contains("concrete")) world.setBlock(mbp, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		for(BlockPos pos : set) liftMultiblock(world, pos);
		for(int i = 0; i < TOWERS; i++)
			generateYellowBarrelTower(world, x + world.random.nextInt(range * 2 + 1) - range, z + world.random.nextInt(range * 2 + 1) - range);
		pollute(world, x, y, z);
		return true;
	}

	/** {@code plant_dead}'s {@code getStateFromMeta(meta)}: its five kinds in upstream's order. */
	public static BlockState deadPlant(int meta) {
		BlockDeadPlant plant = ModBlocks.plant_dead.get();
		return plant.defaultBlockState().setValue(plant.getVariantProperty(), BlockDeadPlant.EnumDeadPlantType.values()[meta]);
	}

	/** Tear the machine whose core is at {@code pos} down and rebuild it {@code 10 + nextInt(15)} higher. */
	public static void liftMultiblock(Level world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if(!(state.getBlock() instanceof BlockDummyable dummyable)) return;
		Direction dir = state.getValue(BlockDummyable.DIR);
		// upstream's meta >= 12: a core (10 + dir) facing a horizontal way; one facing up or down is only torn down
		boolean rebuild = state.getValue(BlockDummyable.PART) == BlockDummyable.Part.CORE && dir.get3DDataValue() >= 2;
		world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		pos = pos.above(world.random.nextInt(15) + 10);
		if(rebuild) {
			world.setBlock(pos, dummyable.defaultBlockState().setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, dir), Block.UPDATE_CLIENTS);
			BlockPos pos1 = pos.relative(dir, -dummyable.getOffset());
			fillSpace(dummyable, world, pos1, dir, dummyable.getOffset());
		}
	}

	private static Method fillSpace;

	/** The base mod's protected {@code BlockDummyable.fillSpace(Level, BlockPos, Direction, int)}, as upstream reached it. */
	public static void fillSpace(BlockDummyable dummyable, Level world, BlockPos pos, Direction dir, int o) {
		try {
			if(fillSpace == null) {
				fillSpace = BlockDummyable.class.getDeclaredMethod("fillSpace", Level.class, BlockPos.class, Direction.class, int.class);
				fillSpace.setAccessible(true);
			}
			fillSpace.invoke(dummyable, world, pos, dir, o);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("BlockDummyable.fillSpace", e);
		}
	}

	public static void generateYellowBarrelTower(Level world, int x, int z) {
		for(int xo = -2; xo <= 2; xo++) {
			for(int zo = -2; zo <= 2; zo++) {
				for(int yo = 0; yo <= 1; yo++) {
					if((zo == -2 || zo == 2) && (xo == -2 || xo == 2))
						if(world.random.nextInt(3) != 0) continue;
					int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x + xo, z + zo);
					placeYellowBarrel(world, new BlockPos(x + xo, y, z + zo), 100);
				}
			}
		}
		int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int height = 15 + world.random.nextInt(10);
		for(int yo = 0; yo < height; yo++) {
			placeYellowBarrel(world, new BlockPos(x, y + yo, z), 100);
			if(yo < height - 2) {
				placeYellowBarrel(world, new BlockPos(x + 1, y + yo, z), 65);
				placeYellowBarrel(world, new BlockPos(x - 1, y + yo, z), 65);
				placeYellowBarrel(world, new BlockPos(x, y + yo, z + 1), 65);
				placeYellowBarrel(world, new BlockPos(x, y + yo, z - 1), 65);
				placeYellowBarrel(world, new BlockPos(x + 1, y + yo, z + 1), 20);
				placeYellowBarrel(world, new BlockPos(x + 1, y + yo, z - 1), 20);
				placeYellowBarrel(world, new BlockPos(x - 1, y + yo, z + 1), 20);
				placeYellowBarrel(world, new BlockPos(x - 1, y + yo, z - 1), 20);
			}
		}
	}

	/** Under {@code chance} in a hundred (drawn only below 100), a yellow barrel or, half the time, sellafite of level 3 to 5. */
	public static void placeYellowBarrel(Level world, BlockPos pos, int chance) {
		if(chance < 100)
			if(world.random.nextInt(100) > chance) return;
		BlockState state;
		if(world.random.nextBoolean()) state = ModBlocks.yellow_barrel.get().defaultBlockState();
		else state = ModBlocks.sellafield.get().defaultBlockState().setValue(BlockSellafield.LEVEL, BlockSellafield.EnumSellafieldLevel.values()[world.random.nextInt(3) + 3]);
		world.setBlock(pos, state, 3);
	}

	/** Upstream's "seed an immediate Gaussian fallout footprint": regions are 64 x 64 ({@code pos >> 6}). */
	public static void pollute(Level world, int x, int y, int z) {
		int cx = x >> 6, cz = z >> 6;
		for(int dx = -REGION_RADIUS; dx <= REGION_RADIUS; dx++) {
			for(int dz = -REGION_RADIUS; dz <= REGION_RADIUS; dz++) {
				float amount = soot(dx, dz);
				if(amount < 1) continue;
				PollutionHandler.incrementPollution(world, new BlockPos((cx + dx) << 6, y, (cz + dz) << 6), PollutionType.SOOT, amount);
			}
		}
	}

	/** The soot laid {@code (dx, dz)} regions away: {@code 10,000 x e^(-(dx^2 + dz^2) / (2 sigma^2))}. */
	public static float soot(int dx, int dz) {
		return (float) (PEAK_SOOT * Math.exp(-(dx * dx + dz * dz) / (2 * SIGMA * SIGMA)));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("§2[Chud Bomb]§r"));
		tooltip.add(Component.literal(" §e" + I18nUtil.resolveKey("desc.radius", RANGE + "§r")));
	}
}
