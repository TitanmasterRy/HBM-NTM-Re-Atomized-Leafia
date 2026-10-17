package com.leafia.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Two blocks for the Custom Control Panel's GameTests (D-3061), registered only when {@code leafia.gametest.fixtures} is set,
 * like {@link LeafiaGameTestFixtures}' (and for the same reason: a block cannot be made once the registry is frozen).
 *
 * <ul>
 * <li>{@code gametest_controllable}: a block entity that <b>is</b> an {@link IControllable}, the way an addon machine is;</li>
 * <li>{@code gametest_controllable_adapted}: a block entity that is <b>not</b>, and is reached only through a provider of
 * {@link ControlEventSystem#CONTROLLABLE}, the way a base-mod machine will be (batch 8).</li>
 * </ul>
 *
 * Both record every event they are sent, and a test sets which events they accept.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaControlPanelFixtures {

	public static Block direct;
	public static Block adapted;
	public static BlockEntityType<DirectHost> directType;
	public static BlockEntityType<AdaptedHost> adaptedType;

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		if(!LeafiaGameTestFixtures.enabled()) return;

		event.register(Registries.BLOCK, helper -> {
			direct = new HostBlock(true);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_controllable"), direct);
			adapted = new HostBlock(false);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_controllable_adapted"), adapted);
		});

		event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
			directType = BlockEntityType.Builder.<DirectHost>of((pos, state) -> new DirectHost(directType, pos, state), direct).build(null);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_controllable"), directType);
			adaptedType = BlockEntityType.Builder.<AdaptedHost>of((pos, state) -> new AdaptedHost(adaptedType, pos, state), adapted).build(null);
			helper.register(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "gametest_controllable_adapted"), adaptedType);
		});
	}

	@SubscribeEvent
	public static void onCapabilities(RegisterCapabilitiesEvent event) {
		if(!LeafiaGameTestFixtures.enabled()) return;
		event.registerBlockEntity(ControlEventSystem.CONTROLLABLE, adaptedType, (be, ctx) -> be.adapter);
	}

	/** What a fixture was sent. */
	public record Received(BlockPos from, ControlEvent event) {}

	/** An {@link IControllable} that records, usable on its own as a target that is not a block entity. */
	public static class Recorder implements IControllable {

		private final Supplier<BlockPos> pos;
		private final Supplier<Level> level;
		public List<String> in = new ArrayList<>();
		/** What {@link #getQueryData} answers: a test sets the keys a node may read. */
		public final Map<String, DataValue> query = new LinkedHashMap<>();
		public final List<Received> received = new ArrayList<>();
		/** When set, thrown from {@link #receiveEvent} after recording. */
		public RuntimeException failWith = null;

		public Recorder(Supplier<BlockPos> pos, Supplier<Level> level) {
			this.pos = pos;
			this.level = level;
		}

		public Recorder(BlockPos pos, Level level, String... in) {
			this(() -> pos, () -> level);
			this.in = new ArrayList<>(List.of(in));
		}

		@Override public List<String> getInEvents() { return in; }
		@Override public Map<String, DataValue> getQueryData() { return query; }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			received.add(new Received(from, e));
			if(failWith != null) throw failWith;
		}

		@Override public BlockPos getControlPos() { return pos.get(); }
		@Override public Level getControlWorld() { return level.get(); }

		public long count(String eventName) {
			return received.stream().filter(r -> r.event().name.equals(eventName)).count();
		}
	}

	private static final class HostBlock extends Block implements EntityBlock {

		private final boolean direct;

		HostBlock(boolean direct) {
			super(Properties.of().noLootTable());
			this.direct = direct;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return direct ? new DirectHost(directType, pos, state) : new AdaptedHost(adaptedType, pos, state);
		}
	}

	/** A block entity that implements the interface itself. */
	public static class DirectHost extends BlockEntity implements IControllable {

		public final Recorder rec = new Recorder(() -> worldPosition, () -> level);

		DirectHost(BlockEntityType<?> type, BlockPos pos, BlockState state) {
			super(type, pos, state);
		}

		@Override public List<String> getInEvents() { return rec.getInEvents(); }
		@Override public Map<String, DataValue> getQueryData() { return rec.getQueryData(); }
		@Override public void receiveEvent(BlockPos from, ControlEvent e) { rec.receiveEvent(from, e); }
		@Override public BlockPos getControlPos() { return worldPosition; }
		@Override public Level getControlWorld() { return level; }
	}

	/** A block entity that knows nothing of the panel; its adapter is what the capability returns. */
	public static class AdaptedHost extends BlockEntity {

		public final Recorder adapter = new Recorder(() -> worldPosition, () -> level);

		AdaptedHost(BlockEntityType<?> type, BlockPos pos, BlockState state) {
			super(type, pos, state);
		}
	}
}
