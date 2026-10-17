package com.leafia.contents.machines.reactors.pwr.blocks.components.terminal;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.PacketGuard;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR control terminal's block entity: the window onto the reactor, and a fluid port besides.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/terminal/PWRTerminalTE.java}.
 * As upstream:
 *
 * <ul>
 * <li>It never holds the reactor ({@link #canAssignCore} is false - "for some reason this one stays
 *     broken"); it links to the core like any component, and tells the client where the core is whenever
 *     a diagnosis runs (packet key 0, a position or null).</li>
 * <li>It is the PWR fluid port's twin: coolant and water in, hot coolant and steam out, on every face
 *     ({@code autoPort(ALL_AROUND)} for upstream's per-face {@code trySubscribe}/{@code tryProvide}).</li>
 * <li>Opening it re-diagnoses the reactor if the link has gone stale ({@link #getLinkedCoreDiagnosis}),
 *     and a terminal with no reactor has no screen.</li>
 * </ul>
 *
 * <h2>The screen's packets come here (D-1094)</h2>
 *
 * Upstream's screen sends its rod demands (key 30) and boiler compression (key 29) straight to the core's
 * block entity, which may be anywhere in a reactor of any size - beyond reach of a player standing at the
 * terminal, which is all the base packet guard can check. So the screen sends them to the terminal, and
 * the terminal hands them to the core only from a player who has <em>this</em> terminal's menu open over
 * <em>that</em> core; the core then checks the values ({@link PWRData#onReceivePacketServer}).
 */
public class PWRTerminalTE extends PWRAssignableEntity implements IFluidStandardReceiverMK2, IFluidStandardSenderMK2, MenuProvider {

	public PWRTerminalTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** The core, re-diagnosing once if the link is stale. Server side only does the diagnosis. */
	@Nullable
	public PWRData getLinkedCoreDiagnosis() {
		PWRData data = getCoreByCorePos();
		if(data == null) {
			Block block = level.getBlockState(worldPosition).getBlock();
			if(block instanceof PWRComponentBlock pwr) {
				pwr.beginDiagnosis(level, worldPosition, worldPosition);
				data = getCoreByCorePos();
			}
		}
		return data;
	}

	@Override
	public void onDiagnosis() {
		LeafiaPacket._start(this).__write(0, corePos).__sendToAffectedClients();
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_TERMINAL";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		super.onReceivePacketLocal(key, value);
		if(key == 0) corePos = value instanceof BlockPos pos ? pos : null; // Now supports null values!
	}

	/** The screen's two requests, forwarded to the core for a player looking at this terminal over it (class note). */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		PWRData core = getCoreByCorePos();
		if(core == null) return;
		if(!(plr.containerMenu instanceof PWRTerminalMenu menu) || !menu.isFor(this, core)) {
			PacketGuard.reject("PWRTerminalTE", plr, "the terminal screen was not opened over this reactor");
			return;
		}
		core.onReceivePacketServer(key, value, plr);
	}

	@Override
	public void onPlayerValidate(Player plr) {
		addDataToPacket(LeafiaPacket._start(this).__write(0, corePos)).__sendToClient(plr);
	}

	/** "for some reason this one stays broken" */
	@Override
	public boolean canAssignCore() {
		return false;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new FluidTank[] {core.tanks[0], core.tanks[3]};
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getSendingTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return new FluidTank[] {core.tanks[1], core.tanks[4]};
		return new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		PWRData core = getCoreByCorePos();
		if(core != null) return core.tanks;
		return new FluidTank[0];
	}

	@Override
	public void tick() {
		super.tick();
		if(!level.isClientSide && getCoreByCorePos() != null) autoPort(ALL_AROUND);
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		PWRData core = getLinkedCoreDiagnosis();
		return core == null ? null : new PWRTerminalMenu(containerId, playerInventory, this, core);
	}
}
