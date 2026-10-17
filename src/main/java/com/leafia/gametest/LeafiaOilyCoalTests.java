package com.leafia.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.gametest.TickWindow;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.lib.LeafiaRef;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Oily coal ore and the burning ore it becomes (D-1063).
 *
 * <p>Every number here is upstream's: hardness 5 and resistance 15 (9 here, by the 3/5) at the registration, light 10
 * on the burning ore, two or three coal with no fortune bonus, a 2-to-21-tick fuse, three
 * seconds of fire, and one vein of 48 in one chunk in sixteen at upstream's y 32-63.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaOilyCoalTests {

	private static final String EMPTY = "empty";
	private static final String EMPTY16 = "empty16";

	private static BlockState oily() {
		return LeafiaBlocks.ore_coal_oil.get().defaultBlockState();
	}

	private static BlockState burning() {
		return LeafiaBlocks.ore_coal_oil_burning.get().defaultBlockState();
	}

	@GameTest(template = EMPTY)
	public static void theOresAreUpstreamsNumbers(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		for(BlockState state : List.of(oily(), burning())) {
			Block block = state.getBlock();
			helper.assertTrue(block.defaultDestroyTime() == 5.0F, block + ": hardness " + block.defaultDestroyTime());
			// setResistance(15.0F) upstream: 1.12.2 stores x3 and reads /5, so 9.0 here (D-1070).
			helper.assertTrue(block.getExplosionResistance() == 9.0F, block + ": resistance " + block.getExplosionResistance());
			helper.assertTrue(state.requiresCorrectToolForDrops(), block + " should need a tool");
			helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), block + " is not in the pickaxe tag, so nothing can harvest it");
			helper.assertTrue(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), block + ": any pickaxe should do, as upstream's ROCK");
		}
		helper.assertTrue(oily().getLightEmission() == 0, "the plain ore should not glow");
		helper.assertTrue(burning().getLightEmission() == 10, "burning ore light " + burning().getLightEmission() + ", upstream's 10/15 is 10");
		helper.succeed();
	}

	/** Places a column: a stone floor, the block, and returns the block's position. */
	private static BlockPos onFloor(GameTestHelper helper, BlockPos pos, BlockState state) {
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, state);
		return pos;
	}

	@GameTest(template = EMPTY)
	public static void lavaBesideItLightsIt(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos ore = onFloor(helper, new BlockPos(1, 2, 2), oily());
		helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
		helper.setBlock(ore.south(), Blocks.LAVA);
		helper.succeedWhen(() -> helper.assertBlockPresent(LeafiaBlocks.ore_coal_oil_burning.get(), ore));
	}

	@GameTest(template = EMPTY)
	public static void burningOreLightsItsNeighbour(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos a = onFloor(helper, new BlockPos(1, 2, 2), oily());
		BlockPos b = onFloor(helper, new BlockPos(2, 2, 2), oily());
		helper.setBlock(a, burning());
		helper.succeedWhen(() -> helper.assertBlockPresent(LeafiaBlocks.ore_coal_oil_burning.get(), b));
	}

	/** The fuse is at most 21 ticks; thirty with a stone neighbour changing must leave it be. */
	@GameTest(timeoutTicks = TickWindow.TIMEOUT, template = EMPTY)
	public static void anOrdinaryNeighbourDoesNotLightIt(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos ore = onFloor(helper, new BlockPos(1, 2, 2), oily());
		helper.setBlock(ore.east(), Blocks.STONE);
		TickWindow.runAfterDelay(helper, 30, () -> {
			helper.assertBlockPresent(LeafiaBlocks.ore_coal_oil.get(), ore);
			helper.succeed();
		});
	}

	/**
	 * Broken, burning ore leaves fire and nothing else — not even with silk touch, because the fire
	 * replaces it before the drop is reached. {@code destroyBlock} is the path that would drop.
	 */
	@GameTest(template = EMPTY)
	public static void burningOreLeavesFireAndNoDrop(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos ore = onFloor(helper, new BlockPos(2, 2, 2), burning());
		helper.getLevel().destroyBlock(helper.absolutePos(ore), true);
		helper.assertBlockPresent(Blocks.FIRE, ore);
		AABB around = new AABB(helper.absolutePos(ore)).inflate(2);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "burning ore dropped something");
		helper.succeed();
	}

	/** Upstream's {@code onExplosionDestroy}, through NeoForge's own explosion path. */
	@GameTest(template = EMPTY)
	public static void blownUpOreLeavesFire(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos ore = onFloor(helper, new BlockPos(2, 2, 2), oily());
		BlockPos abs = helper.absolutePos(ore);
		ServerLevel level = helper.getLevel();
		Explosion explosion = new Explosion(level, null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 1F, false,
				Explosion.BlockInteraction.DESTROY);
		level.getBlockState(abs).onBlockExploded(level, abs, explosion);
		helper.assertBlockPresent(Blocks.FIRE, ore);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void walkingOnItSetsYouAlightForThreeSeconds(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		BlockPos ore = onFloor(helper, new BlockPos(2, 1, 2), burning());
		Pig pig = helper.spawn(EntityType.PIG, ore.above());
		helper.assertTrue(pig.getRemainingFireTicks() <= 0, "the pig was alight before it stepped on anything");
		burning().getBlock().stepOn(helper.getLevel(), helper.absolutePos(ore), burning(), pig);
		helper.assertTrue(pig.getRemainingFireTicks() == 60, "fire ticks " + pig.getRemainingFireTicks() + ", setFire(3) is 60");
		helper.succeed();
	}

	/**
	 * Two or three coal, and fortune changes nothing: upstream overrides Forge's three-argument
	 * {@code quantityDropped}, which is where {@code BlockOre}'s fortune bonus would have run.
	 * Silk touch gives the ore.
	 */
	@GameTest(template = EMPTY)
	public static void theOreDropsTwoOrThreeCoalAndFortuneDoesNothing(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
		fortune.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);

		for(ItemStack tool : List.of(new ItemStack(Items.WOODEN_PICKAXE), fortune)) {
			Set<Integer> seen = new HashSet<>();
			for(int i = 0; i < 60; i++) {
				List<ItemStack> drops = Block.getDrops(oily(), level, at, null, null, tool);
				helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.COAL), "drops " + drops);
				int n = drops.get(0).getCount();
				helper.assertTrue(n == 2 || n == 3, n + " coal from " + tool);
				seen.add(n);
			}
			helper.assertTrue(seen.size() == 2, "sixty breaks gave only " + seen + " with " + tool);
		}

		ItemStack silk = new ItemStack(Items.WOODEN_PICKAXE);
		silk.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
		List<ItemStack> drops = Block.getDrops(oily(), level, at, null, null, silk);
		helper.assertTrue(drops.size() == 1 && drops.get(0).is(LeafiaBlocks.ore_coal_oil.get().asItem()) && drops.get(0).getCount() == 1,
				"silk touch dropped " + drops);
		helper.succeed();
	}

	/**
	 * The three worldgen files load, read upstream's numbers after the 64-block shift, and the
	 * vein lands in deepslate — the rock that is actually at y -32 to -1, which a stone-only
	 * target would have missed.
	 */
	@GameTest(template = EMPTY16)
	public static void theOreGeneratesInDeepRock(GameTestHelper helper) {

		if(!LeafiaFeatures.oilyCoal) { helper.succeed(); return; }

		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "ore_coal_oil");
		PlacedFeature placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(id);
		ConfiguredFeature<?, ?> configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id);
		helper.assertTrue(placed != null && configured != null, "the placed or configured feature did not load");
		helper.assertTrue(level.registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).containsKey(id),
				"the biome modifier did not load, so the ore is never added to a biome");

		String json = PlacedFeature.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), placed)
				.getOrThrow().toString().replace(" ", "");
		for(String expected : List.of("\"chance\":16", "\"absolute\":-32", "\"absolute\":-1"))
			helper.assertTrue(json.contains(expected), "placement lacks " + expected + ": " + json);
		helper.assertTrue(configured.config() instanceof OreConfiguration ore && ore.size == 48,
				"the vein is not an ore of 48: " + configured.config());

		BlockPos min = new BlockPos(3, 3, 3);
		BlockPos max = new BlockPos(11, 11, 11);
		for(BlockPos p : BlockPos.betweenClosed(min, max))
			helper.setBlock(p, Blocks.DEEPSLATE);
		configured.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1063L), helper.absolutePos(new BlockPos(7, 7, 7)));

		int placedOre = 0;
		for(BlockPos p : BlockPos.betweenClosed(min.offset(-2, -2, -2), max.offset(2, 2, 2)))
			if(helper.getBlockState(p).is(LeafiaBlocks.ore_coal_oil.get())) placedOre++;
		helper.assertTrue(placedOre > 0, "a vein of 48 placed no ore in deepslate");
		helper.succeed();
	}
}
