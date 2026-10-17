package com.leafia.transformer;

import com.custom_hbm.contents.oilycoal.BlockCoalOil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.dev.optimization.LeafiaParticlePacket.FiaSpark;
import com.leafia.dev.optimization.LeafiaParticlePacket.VanillaExt;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.LeafiaItemDrops;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The addon's hooks into the server world (D-3026): the sparks a metal tool throws off an ore, and the item that was lost.
 *
 * <p>Upstream: {@code com/leafia/transformer/WorldServerLeafia.java}, static methods called from vanilla mixins. Three of its five
 * are ported here and two are not:
 *
 * <ul>
 *   <li>{@link #onBreakBlockProgress} and {@link #player_onBreakBlockProgress}, called from {@code MixinServerLevel} at the head of
 *       {@code ServerLevel.destroyBlockProgress} (upstream's {@code ServerWorldEventHandler.sendBlockBreakProgress}). 1.21.1 has no
 *       event for a break-progress step, so this is the addon's one mixin into the server world;</li>
 *   <li>{@link #onItemDroppedByPlayer} and {@link #onEntityRemoved}, which are {@link LeafiaItemDrops}' events;</li>
 *   <li><b>not ported:</b> {@code fluid_onFilling}, {@code fluid_canContinue}, {@code fluid_checkTraits} and {@code violatedPositions},
 *       Forge-fluid trait checks whose bodies upstream has commented out or short-circuited ({@code if (true) return true; // ah fuck it};
 *       the trait loop is a block comment), called from nothing that survives; and {@code onItemDestroyed}, whose body is a comment.</li>
 * </ul>
 *
 * <h2>What the sparks do</h2>
 *
 * Mining an ore with a metal pickaxe, axe or shovel (any tier but wood) rings on every second step of the break progress
 * (0, 2, 4, 6, 8): a clink at the block, and one time in two a spray of one to three sparks off the face the player is looking at.
 * Oily coal also smokes (one to four puffs, offset up to 0.1 either way) and, if the roll was the top one, throws a lava spit and one time
 * in three catches fire.
 *
 * <p>The 1.12.2 set of ores was {@code BlockOre}: coal, iron, gold, diamond, lapis, emerald and nether quartz (redstone ore was its own
 * class, and so is copper, which it predates). That is the vanilla ore tags here, less redstone and copper, plus the addon's oily coal.
 * Oily coal answers to {@code features.oilyCoal}; the vanilla ores to {@code overhauls.cosmeticOverhaul}, because it changes how mining
 * base game blocks looks and sounds. Upstream is unconditional.
 */
public class WorldServerLeafia {

	/** The vanilla ores {@code BlockOre} was: no redstone and no copper. */
	public static boolean isVanillaOre(BlockState state) {
		return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.DIAMOND_ORES)
				|| state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.EMERALD_ORES) || state.is(Blocks.NETHER_QUARTZ_ORE);
	}

	/** Upstream's `instanceof ItemTool || ItemToolAbility`, less the hoe (1.12.2's hoe was not an `ItemTool`) and the wooden ones. */
	public static boolean ringsOnOre(ItemStack held) {
		if(held.isEmpty()) return false;
		if(!(held.getItem() instanceof PickaxeItem || held.getItem() instanceof AxeItem || held.getItem() instanceof ShovelItem)) return false;
		return ((DiggerItem) held.getItem()).getTier() != Tiers.WOOD;
	}

	/** Whether the break-progress hook acts on this block under the current switches. */
	public static boolean rings(BlockState state) {
		if(state.getBlock() instanceof BlockCoalOil) return LeafiaFeatures.oilyCoal;
		return LeafiaFeatures.cosmeticOverhaul && isVanillaOre(state);
	}

	/** Test seam: how many times the hook has rung (clinked) since the game started. */
	public static int rung = 0;

	public static void onBreakBlockProgress(ServerLevel level, BlockPos pos, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if(!ringsOnOre(held)) return;
		rung++;

		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, LeafiaSounds.sbPickaxeOre.get(), SoundSource.BLOCKS,
				0.4F + level.random.nextFloat() * 0.2F, 1);
		if(!level.random.nextBoolean()) return;

		// Library.rayTrace(player, 20, 0): where the player is looking, and which face of it
		HitResult hit = player.pick(20, 0, false);
		if(!(hit instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) return;

		FiaSpark spark = new FiaSpark();
		spark.color = 0xFFEE80;
		spark.count = level.random.nextInt(3) + 1;
		spark.thickness = 0.014F;
		Vec3 at = block.getLocation();
		Vec3 normal = Vec3.atLowerCornerOf(block.getDirection().getNormal());
		spark.emit(level, at, normal);

		if(level.getBlockState(pos).getBlock() instanceof BlockCoalOil && LeafiaBlocks.ore_coal_oil_burning != null) {
			int puffs = level.random.nextInt(4);
			for(int i = 0; i <= puffs; i++)
				VanillaExt.Smoke().emit(level, at.add(level.random.nextDouble() * 0.2 - 0.1, level.random.nextDouble() * 0.2 - 0.1, level.random.nextDouble() * 0.2 - 0.1), Vec3.ZERO);
			if(puffs == 3) {
				VanillaExt.Lava().emit(level, at, Vec3.ZERO);
				if(level.random.nextInt(3) == 0) {
					level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.65F, 0.9F + level.random.nextFloat() * 0.2F);
					level.setBlock(pos, LeafiaBlocks.ore_coal_oil_burning.get().defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	/** From {@code MixinServerLevel}: a player's break of a block advanced to {@code progress} (-1 when it stopped). */
	public static void player_onBreakBlockProgress(ServerLevel level, int breakerId, BlockPos pos, int progress) {
		if(!(level.getEntity(breakerId) instanceof ServerPlayer player)) return;
		BlockState state = level.getBlockState(pos);
		if(rings(state) && Math.floorMod(progress, 2) == 0) onBreakBlockProgress(level, pos, player);
	}

	/** Upstream's {@code onItemDroppedByPlayer}: {@link LeafiaItemDrops#onToss}. */
	public static void onItemDroppedByPlayer(net.minecraft.world.entity.player.Player player, ItemEntity item) {
		LeafiaItemDrops.setDroppedBy(item, player);
	}

	/** Upstream's {@code onEntityRemoved}: an item entity gone from the world, and an advisor that nobody picked up. */
	public static void onEntityRemoved(Level level, Entity entity) {
		if(level.isClientSide || !(entity instanceof ItemEntity item)) return;
		LeafiaItemDrops.lose(item);
	}
}
