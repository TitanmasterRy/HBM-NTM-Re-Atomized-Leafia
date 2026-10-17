package com.leafia.dev.firestorm;

import javax.annotation.Nullable;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.machine.MachineChemicalPlant;
import com.leafia.config.LeafiaFeatures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A block that fire may reach the machine behind.
 *
 * <p>Upstream: {@code com/leafia/dev/firestorm/IFirestormBlock.java}. Upstream mixed it into the base mod's
 * chemical plant block; here {@link #of} answers for the plant's block (and for a block of the addon's own that
 * implements this), and {@link #block} replaces the {@code this instanceof BlockDummyable} the defaults read, since
 * the answer is no longer the block itself. {@link #ignite} is what the vanilla fire block calls - from
 * {@code MixinBlockFire}, which upstream's comment calls "also used via ASM" - and what the burning plant's own fire
 * spreading calls.
 *
 * <p>Off with {@link LeafiaFeatures#firestorm}, which is upstream's {@code enableFirestorm}: no fire reaches any
 * machine.
 */
public interface IFirestormBlock {

	/** The block this answers for: a dummyable machine's parts all lead to one core. */
	Block block();

	/** What fire does to {@code pos}: if the block there is a firestorm block that can catch fire, it does. */
	static void ignite(Level level, BlockPos pos) {
		if(!LeafiaFeatures.firestorm) return;
		IFirestormBlock firestorm = of(level.getBlockState(pos).getBlock());
		if(firestorm == null) return;
		if(firestorm.canCatchFire(level, pos)) firestorm.catchFire(level, pos);
	}

	/** The firestorm face of a block, or null if fire does not reach the machine behind it. */
	@Nullable
	static IFirestormBlock of(Block block) {
		if(block instanceof IFirestormBlock own) return own;
		if(block instanceof MachineChemicalPlant) return () -> block;
		return null;
	}

	/** The machine's own position: the core for a multiblock, {@code pos} itself otherwise; null if there is none. */
	@Nullable
	default BlockPos core(Level level, BlockPos pos) {
		if(block() instanceof BlockDummyable dummyable) return dummyable.findCore(level, pos);
		return pos;
	}

	default void catchFire(Level level, BlockPos pos) {
		BlockPos core = core(level, pos);
		if(core == null) return;
		IFirestormTE te = IFirestormTE.of(level.getBlockEntity(core));
		if(te != null) te.catchFire();
	}

	default boolean canCatchFire(Level level, BlockPos pos) {
		BlockPos core = core(level, pos);
		if(core == null) return false;
		IFirestormTE te = IFirestormTE.of(level.getBlockEntity(core));
		return te != null && te.canCatchFire();
	}

	default boolean isDestroyed(Level level, BlockPos pos) {
		BlockPos core = core(level, pos);
		if(core == null) return false;
		IFirestormTE te = IFirestormTE.of(level.getBlockEntity(core));
		return te != null && te.isDestroyed();
	}
}
