package com.leafia.contents.machines.reactors.rbmk;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.RBMKColumn;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.inventory.gui.GUIRBMKConsole;
import com.hbm.inventory.gui.GUIRBMKControlAuto;
import com.hbm.render.blockentity.RenderRBMKLid;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.reactors.rbmk.columns.control_auto.container.RBMKControlAutoGUI;
import com.leafia.contents.machines.reactors.rbmk.effects.ParticleJumpingRBMK;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaRBMKSeams;
import com.leafia.overwrite_contents.interfaces.IMixinRBMKColumn$ControlColumn;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKBase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client half of the RBMK overhaul: upstream's four client mixins as the base mod's client seams
 * (D-2197), installed once from client setup (D-3018).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th></tr>
 * <tr><td>{@code MixinRenderRBMKLid} (redirects the Cherenkov colour for four fuels)</td><td>{@link #RENDER}'s
 * {@code cherenkovColor}, reading the index the server's hook writes into the column's extra tag</td></tr>
 * <tr><td>{@code MixinRenderRBMKControlRod} (a jumping column's own renderer stands down)</td><td>{@link #RENDER}'s
 * {@code skip}, for every column whose lid is in the air, which {@link ParticleJumpingRBMK} then draws</td></tr>
 * <tr><td>{@code MixinGUIRBMKConsole} (the AZ-5 also sends the shut-off order; the shut-off mark)</td><td>{@link #SCREEN}</td></tr>
 * <tr><td>{@code MixinTileEntityRBMKControlAuto.provideGUI} (the rod's screen with the acknowledge buttons)</td><td>
 * {@link #onScreenOpening}, which swaps the base mod's screen for {@link RBMKControlAutoGUI} on the same menu</td></tr>
 * </table>
 *
 * <p>Every hook here answers from data the server sent - the extra tags and the console's column tags - so
 * an overhaul the server does not run draws nothing different. The screen swap is the exception and follows
 * this client's own {@code overhauls.rbmkOverhaul}, as a registry difference would.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaRBMKClient {

	private LeafiaRBMKClient() {}

	/** Upstream's {@code rbmk_console_additions.png}: the shut-off mark is its top-left 10x10. */
	public static final ResourceLocation ADDITIONS = LeafiaRef.rl("textures/gui/reactors/rbmk_console_additions.png");

	private static boolean installed = false;

	public static synchronized void install() {
		if(installed) return;
		installed = true;
		RenderRBMKLid.addRenderHook(RENDER);
		GUIRBMKConsole.addScreenHook(SCREEN);
	}

	public static final RenderRBMKLid.RenderHook RENDER = new RenderRBMKLid.RenderHook() {

		@Override
		public boolean skip(BlockEntityRBMKBase column, float partialTick) {
			if(IMixinTileEntityRBMKBase.leafia$jumpHeight(column) <= 0) return false;
			// A client that missed the jump's packet (it came into range mid-jump) still gets the lid drawn.
			if(column.getLevel() instanceof ClientLevel level) ParticleJumpingRBMK.spawn(level, column.getBlockPos());
			return true;
		}

		@Override
		public float[] cherenkovColor(BlockEntityRBMKRod rod, float[] rgb) {
			float[] colour = LeafiaRBMKSeams.cherenkovOf(rod);
			return colour == null ? rgb : colour.clone();
		}
	};

	public static final GUIRBMKConsole.ScreenHook SCREEN = new GUIRBMKConsole.ScreenHook() {

		/** Upstream sent a second packet; the key rides the AZ-5's own here, and a server without the overhaul ignores it. */
		@Override
		public void onAz5(BlockEntityRBMKConsole console, CompoundTag control) {
			control.putBoolean(LeafiaRBMKSeams.KEY_AZ5_ALL, true);
		}

		@Override
		public void drawColumn(GuiGraphics graphics, BlockEntityRBMKConsole console, int index, RBMKColumn column, int x, int y, int size) {
			if(column.type == ColumnType.CONTROL_AUTO && IMixinRBMKColumn$ControlColumn.leafia$getExclamation(column))
				graphics.blit(ADDITIONS, x, y, 0, 0, size, size);
		}
	};

	@SubscribeEvent
	public static void onScreenOpening(ScreenEvent.Opening event) {
		if(!LeafiaFeatures.rbmkOverhaul) return;
		if(event.getNewScreen() instanceof GUIRBMKControlAuto old) {
			Minecraft mc = Minecraft.getInstance();
			if(mc.player == null) return;
			event.setNewScreen(new RBMKControlAutoGUI(old.getMenu(), mc.player.getInventory(), old.getTitle()));
		}
	}
}
