package com.leafia.gametest;

import java.util.EnumSet;

import com.leafia.contents.network.spk_cable.uninos.ISPKProvider;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Blocks that exist only so that an addon GameTest has something to point at.
 *
 * <p>The addon's counterpart of the base mod's {@code com.hbm.gametest.GameTestFixtures}, and
 * registered the same way: <b>only</b> when the {@code leafia.gametest.fixtures} system property
 * is set, which {@code addon-leafia/build.gradle} sets on the {@code gameTestServer} run and
 * nowhere else. A real game never sees them. The reason a fixture has to be a registered block
 * rather than an instance a test makes for itself is the base mod's (D-057): a {@code Block}
 * constructor claims a registry holder, and the registry is frozen by the time a test runs.
 *
 * <h2>What is here</h2>
 *
 * A provider and a receiver of SPK, because the SPK cable carries the Dark Fusion Core's energy
 * and the DFC's parts that will implement {@link ISPKProvider} and {@link ISPKReceiver} are a
 * later batch (D-3008). They are the smallest possible implementors: a buffer, a ceiling, and a
 * few public knobs so a test can set up exactly the arithmetic it wants. <b>Neither does
 * anything on its own</b> unless {@code auto} is set; a test normally calls
 * {@link ISPKProvider#tryProvideSPK} and {@link ISPKReceiver#trySubscribeSPK} itself, so that one
 * call is one transfer and the numbers can be asserted exactly. With {@code auto} set they behave
 * as the real parts do upstream - the receiver subscribes on every side every tick, the provider
 * pushes on its {@link SpkProvider#sides} every tick - which is what the end-to-end test uses.
 *
 * <p>Their ids start with {@code gametest_}, which the registry-wide tests skip.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaGameTestFixtures {

	public static boolean enabled() {
		return Boolean.getBoolean("leafia.gametest.fixtures");
	}

	public static Block spkProvider;
	public static Block spkReceiver;
	public static BlockEntityType<SpkProvider> spkProviderType;
	public static BlockEntityType<SpkReceiver> spkReceiverType;

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		if(!enabled()) return;

		event.register(Registries.BLOCK, helper -> {
			spkProvider = new FixtureBlock(true);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_spk_provider"), spkProvider);

			spkReceiver = new FixtureBlock(false);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_spk_receiver"), spkReceiver);
		});

		event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
			// The supplier reads the field rather than capturing it: the type does not exist yet
			// at the moment the builder is handed this lambda.
			spkProviderType = BlockEntityType.Builder
					.<SpkProvider>of((pos, state) -> new SpkProvider(spkProviderType, pos, state), spkProvider)
					.build(null);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_spk_provider"), spkProviderType);

			spkReceiverType = BlockEntityType.Builder
					.<SpkReceiver>of((pos, state) -> new SpkReceiver(spkReceiverType, pos, state), spkReceiver)
					.build(null);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_spk_receiver"), spkReceiverType);
		});
	}

	/** One class for both blocks, which differ only in the block entity they hold. */
	private static final class FixtureBlock extends Block implements EntityBlock {

		private final boolean provider;

		FixtureBlock(boolean provider) {
			super(Properties.of().noLootTable());
			this.provider = provider;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return provider ? new SpkProvider(spkProviderType, pos, state) : new SpkReceiver(spkReceiverType, pos, state);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			if(level.isClientSide) return null;
			if(provider && type == spkProviderType) return (BlockEntityTicker<T>) (BlockEntityTicker<SpkProvider>) (l, p, s, be) -> be.tick();
			if(!provider && type == spkReceiverType) return (BlockEntityTicker<T>) (BlockEntityTicker<SpkReceiver>) (l, p, s, be) -> be.tick();
			return null;
		}
	}

	/** An {@link ISPKProvider} with a buffer. {@link #getSendable} is the whole of it. */
	public static class SpkProvider extends BlockEntity implements ISPKProvider {

		public long spk = 0;
		public long max = 1_000;
		/** A cap on one transfer; negative for the interface's default (the whole buffer). */
		public long speed = -1;
		/** Push on {@link #sides} every tick, as the real emitter does. */
		public boolean auto = false;
		public final EnumSet<Direction> sides = EnumSet.allOf(Direction.class);

		SpkProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) {
			super(type, pos, state);
		}

		void tick() {
			if(!auto || level == null) return;
			for(Direction dir : sides) tryProvideSPK(level, worldPosition.relative(dir), dir, false);
		}

		@Override public long getSPK() { return spk; }
		@Override public void setSPK(long power) { this.spk = power; }
		@Override public long getMaxSPK() { return max; }
		@Override public long getSendable() { return spk; }

		@Override
		public long getSPKProviderSpeed() {
			return speed < 0 ? ISPKProvider.super.getSPKProviderSpeed() : speed;
		}
	}

	/** An {@link ISPKReceiver} with a buffer and per-side switches. */
	public static class SpkReceiver extends BlockEntity implements ISPKReceiver {

		public long spk = 0;
		public long max = 1_000;
		/** A cap on one intake; negative for the interface's default (the whole buffer). */
		public long speed = -1;
		/** Subscribe on every side every tick, as the real receiver does. */
		public boolean auto = false;
		/** Sides {@link #canConnectSPK} accepts. */
		public final EnumSet<Direction> accepts = EnumSet.allOf(Direction.class);
		/** Sides {@link #isInputPreferrable} accepts. */
		public final EnumSet<Direction> prefers = EnumSet.allOf(Direction.class);

		SpkReceiver(BlockEntityType<?> type, BlockPos pos, BlockState state) {
			super(type, pos, state);
		}

		void tick() {
			if(!auto || level == null) return;
			for(Direction dir : Direction.values()) trySubscribeSPK(level, worldPosition.relative(dir), dir);
		}

		@Override public long getSPK() { return spk; }
		@Override public void setSPK(long power) { this.spk = power; }
		@Override public long getMaxSPK() { return max; }

		@Override public boolean canConnectSPK(Direction dir) { return dir != null && accepts.contains(dir); }
		@Override public boolean isInputPreferrable(Direction dir) { return prefers.contains(dir); }

		@Override
		public long getSPKReceiverSpeed() {
			return speed < 0 ? ISPKReceiver.super.getSPKReceiverSpeed() : speed;
		}
	}
}
