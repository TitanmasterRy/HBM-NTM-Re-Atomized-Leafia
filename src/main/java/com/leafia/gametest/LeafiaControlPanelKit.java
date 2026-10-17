package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hbm.items.ModItems;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.block.IControlEditSession;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodesRegister;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Shared scaffolding for the Custom Control Panel block's GameTests (CCP-PANEL, D-3063): a registered test control, a stand-in
 * for the editor menu, a way to put a panel in the world and a player beside it, and a way to write an edit the way a client
 * would. Nothing here is a test.
 */
final class LeafiaControlPanelKit {

	private LeafiaControlPanelKit() {}

	/** Set by {@link Tripwire}'s static initialiser, which nothing may ever run: a class a client named. */
	static boolean tripped = false;

	/** A class whose initialisation is observable. Referenced only by name: {@code .class} does not initialise it. */
	static final class Tripwire {
		static {
			LeafiaControlPanelKit.tripped = true;
		}
	}

	/** Where the panel stands, relative to the test. */
	static final BlockPos AT = new BlockPos(2, 1, 2);

	/** A control that does nothing of its own, so a test can put anything on a panel. Registered by {@link #withTestControl}. */
	static final class TestControl extends Control {

		static final String NAME = "ccp_test";

		TestControl(ControlPanel panel) {
			super("Test", NAME, panel);
		}

		@Override public ControlType getControlType() { return ControlType.BUTTON; }
		@Override public float[] getSize() { return new float[]{1, 1, 0.5F}; }
		@Override public Control newControl(ControlPanel panel) { return new TestControl(panel); }
		@Override public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}
	}

	/** A body that may throw, for {@link #withTestControl}. */
	@FunctionalInterface
	interface Body {
		void run() throws Exception;
	}

	/**
	 * Registers {@link TestControl} for the body, with the stock node loader present, and puts both back as they were, whatever
	 * happens: the loader list is process-wide and another class's test may have taken the loader out.
	 */
	static void withTestControl(Body body) throws Exception {
		List<INodeLoader> loaders = new ArrayList<>(NTMControlPanelRegistry.nbtNodeLoaders);
		ControlRegistry.register(new TestControl(null));
		StockNodesRegister.register();
		try {
			body.run();
		} finally {
			ControlRegistry.unregister(TestControl.NAME);
			NTMControlPanelRegistry.nbtNodeLoaders.clear();
			NTMControlPanelRegistry.nbtNodeLoaders.addAll(loaders);
		}
	}

	/** The menu a client would have open: it names the panel it edits, as batch 6's does. */
	static final class EditMenu extends AbstractContainerMenu implements IControlEditSession {

		private final ControlPanelTE te;

		EditMenu(ControlPanelTE te) {
			super(null, 1001);
			this.te = te;
		}

		@Override public ControlPanelTE editedPanel() { return te; }
		@Override public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
		@Override public boolean stillValid(Player player) { return true; }
	}

	static ControlPanelTE place(GameTestHelper helper, BlockPos relative) {
		helper.setBlock(relative, LeafiaBlocks.control_panel_custom.get().defaultBlockState());
		return (ControlPanelTE) helper.getBlockEntity(relative);
	}

	static ControlPanelTE placeFront(GameTestHelper helper, BlockPos relative) {
		helper.setBlock(relative, LeafiaBlocks.control_panel_front.get().defaultBlockState());
		return (ControlPanelTE) helper.getBlockEntity(relative);
	}

	/**
	 * A mock player that the rest of the world cannot see. The framework logs one in through the player list and leaves it there
	 * for the rest of the run; its connection is an empty channel that has negotiated no payloads, so the next addon packet
	 * broadcast to "everyone" (the wind's, every tick) throws at it and takes the server down. So it is taken out of the player
	 * list and out of the level before the test uses it, as the PWR and MSR tests do.
	 */
	@SuppressWarnings("removal")
	static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
		return player;
	}

	/** A mock player standing two blocks from the panel, with its editor open: the one case where an edit is allowed. */
	static ServerPlayer editor(GameTestHelper helper, ControlPanelTE te) {
		ServerPlayer player = mockPlayer(helper);
		stand(player, te, 2);
		player.containerMenu = new EditMenu(te);
		return player;
	}

	/** Moves a player to {@code distance} blocks south of the panel. (A mock player starts at the world origin.) */
	static void stand(ServerPlayer player, ControlPanelTE te, double distance) {
		BlockPos p = te.getBlockPos();
		player.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5 + distance);
	}

	/** A multi-detonator whose list holds these positions. */
	static ItemStack linker(BlockPos... positions) {
		ItemStack stack = new ItemStack(ModItems.detonator_multi.get());
		int[] xs = new int[positions.length];
		int[] ys = new int[positions.length];
		int[] zs = new int[positions.length];
		for(int i = 0; i < positions.length; i++) {
			xs[i] = positions[i].getX();
			ys[i] = positions[i].getY();
			zs[i] = positions[i].getZ();
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putIntArray("xValues", xs);
			tag.putIntArray("yValues", ys);
			tag.putIntArray("zValues", zs);
		});
		return stack;
	}

	static void hold(ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
	}

	/**
	 * An edit the way a client writes it: a scratch panel on this block entity, filled by {@code fill}, saved, and marked
	 * {@code full_set}. The scratch panel starts as a copy of the live one's geometry.
	 */
	static CompoundTag edit(ControlPanelTE te, Consumer<ControlPanel> fill) {
		ControlPanel scratch = new ControlPanel(te, te.panel.height, te.panel.angle, te.panel.a_off, te.panel.b_off, te.panel.c_off, te.panel.d_off);
		fill.accept(scratch);
		CompoundTag tag = scratch.writeToNBT(new CompoundTag());
		tag.putBoolean("full_set", true);
		return tag;
	}

	/** A control of the test type on this panel. */
	static TestControl control(ControlPanel panel, float x, float y) {
		TestControl c = new TestControl(panel);
		c.posX = x;
		c.posY = y;
		panel.controls.add(c);
		return c;
	}

	static NodeSystem receiveSystem(Control c, String event) {
		NodeSystem sys = new NodeSystem(c);
		c.receiveNodeMap.put(event, sys);
		return sys;
	}

	static NodeSystem sendSystem(Control c, String event) {
		NodeSystem sys = new NodeSystem(c);
		c.sendNodeMap.put(event, sys);
		return sys;
	}

	/** A payload context whose player is this one, for driving the real packet handlers. */
	static IPayloadContext context(Player player) {
		return context(player, PacketFlow.SERVERBOUND);
	}

	/** The same, arriving in the given direction: a client's packet is serverbound, a server's instrument sync is clientbound. */
	static IPayloadContext context(Player player, PacketFlow flow) {
		return new IPayloadContext() {
			@Override public net.minecraft.network.protocol.common.ClientCommonPacketListener listener() {
				throw new UnsupportedOperationException();
			}
			@Override public Player player() { return player; }
			@Override public CompletableFuture<Void> enqueueWork(Runnable task) {
				task.run();
				return CompletableFuture.completedFuture(null);
			}
			@Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
				return CompletableFuture.completedFuture(task.get());
			}
			@Override public PacketFlow flow() { return flow; }
			@Override public void handle(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
				throw new UnsupportedOperationException();
			}
			@Override public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
