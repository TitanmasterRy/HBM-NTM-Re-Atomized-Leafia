package com.leafia.contents.machines.powercores.dfc.components.injector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreInjector;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.contents.machines.powercores.dfc.components.DFCParts;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityInjector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The overhauled DFC injector: it fills the core it is aimed at, and it refuses to set either of its two
 * tanks to the fluid the other one holds.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityCoreInjector.java}, which
 * overwrites NTM-CE's injector {@code update}. Here a subclass of the base mod's {@link BlockEntityCoreInjector}
 * under its own type, put into the base block by {@code BlockEntityOverrides} while
 * {@code overhauls.dfcOverhaul} is on (D-3014).
 *
 * <h2>What changes with the overhaul</h2>
 *
 * <ul>
 * <li><b>A target, not a facing.</b> The core is found along the line to the target, up to
 *     {@code dfcComponentRange} (50) blocks, past anything whose shape the line misses (D-3011's
 *     {@code leafia$getCore}); the base injector looks fifteen blocks along its facing and stops at
 *     anything that is not air.</li>
 * <li><b>The two fuels must differ.</b> An identifier in slot 0 is read only if it does not name tank B's
 *     fluid, and one in slot 2 only if it does not name tank A's. The base injector sets each tank
 *     unconditionally, so it can run the same fuel in both.</li>
 * <li>Its screen shows the core: a glow when both of the core's tanks hold fuel, more above 100 degrees,
 *     the whole core above 1,500 ({@code CoreInjectorScreen}).</li>
 * </ul>
 *
 * The transfer itself is the base injector's {@link #transfer} - upstream's loop is the same arithmetic.
 * {@code IControllable} is D-1093 #8.
 */
public class LeafiaCoreInjectorTE extends BlockEntityCoreInjector implements IMixinTileEntityInjector, IDFCClientTicked, IControllable {

	protected BlockPos targetPosition = BlockPos.ZERO;
	protected boolean targetSet = false;
	@Nullable
	public BlockEntityCore lastGetCore;
	protected final DFCParts.Hum hum = new DFCParts.Hum(false);

	public LeafiaCoreInjectorTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** The fluid an identifier in this slot names, or {@code NONE}. */
	public FluidType getFluid(int slot) {
		ItemStack stack = slots[slot];
		if(stack.getItem() instanceof IItemFluidIdentifier identifier)
			return identifier.getType(level, worldPosition, stack);
		return Fluids.NONE;
	}

	@Override
	public void updateEntity() {
		if(level.isClientSide) {
			leafia$clientTick();
			return;
		}
		if(!targetSet) {
			BlockState state = getBlockState();
			if(state.hasProperty(CoreComponent.FACING))
				leafia$targetPosition(worldPosition.relative(state.getValue(CoreComponent.FACING)));
		}
		BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);

		// Upstream's two subscribeToAllAround calls, one per tank type (D-300).
		this.autoPort(ALL_AROUND);

		if(!getFluid(0).equals(tanks[1].getTankType()))
			this.tanks[0].setType(0, 1, slots);
		if(!getFluid(2).equals(tanks[0].getTankType()))
			this.tanks[1].setType(2, 3, slots);
		LeafiaPacket._start(this).__write(KEY_TARGET, targetPosition).__sendToAffectedClients();
		if(core != null) {
			for(int t = 0; t < 2; ++t) {
				if(core.tanks[t].getTankType() == this.tanks[t].getTankType() || core.tanks[t].getFill() == 0)
					transfer(core, t);
			}
		}
		this.markChanged();
		this.networkPackNT(250);
	}

	@Override
	public void leafia$clientTick() {
		BlockEntityCore core = leafia$getCore(LeafiaConfig.dfcComponentRange);
		if(core != null)
			hum.play(level, worldPosition);
		else
			hum.stop();
	}

	/**
	 * Which picture the screen shows of the core, upstream's order: 2 the whole core (1,500 degrees and up),
	 * 1 the hot core (100 and up), 0 the fuelled core (both its tanks hold something), -1 nothing. Here, not
	 * in the client-only screen, so a server test can ask.
	 */
	public static int screenPicture(BlockEntityCore core) {
		if(core instanceof com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore mixin) {
			if(mixin.getDFCTemperature() >= 1500) return 2;
			if(mixin.getDFCTemperature() >= 100) return 1;
		}
		if(core.tanks[0].getFill() > 0 && core.tanks[1].getFill() > 0) return 0;
		return -1;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new CoreInjectorMenu(id, playerInventory, this);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("x1")) {
			targetPosition = new BlockPos(nbt.getInt("x1"), nbt.getInt("y1"), nbt.getInt("z1"));
			targetSet = true;
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		leafia$writeTargetPos(nbt);
	}

	@Override
	public String getPacketIdentifier() {
		return "dfc_injector";
	}

	@Override
	public void onLoad() {
		ControllableLifecycle.join(this);
		super.onLoad();
		if(level != null && level.isClientSide) com.leafia.client.LeafiaDFCClient.track(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		hum.release();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
		hum.release();
	}

	@Override @Nullable public BlockEntityCore leafia$lastGetCore() { return lastGetCore; }
	@Override public void leafia$lastGetCore(@Nullable BlockEntityCore core) { this.lastGetCore = core; }
	@Override public BlockPos leafia$getTargetPosition() { return targetPosition; }
	@Override public void leafia$targetPosition(BlockPos pos) { this.targetPosition = pos; targetSet = true; }

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/**
	 * Upstream's two query keys. <b>Owner-approved divergence (2026-10-17, D-3072):</b> Leafia's {@code tankB} read {@code tanks[0]}
	 * like {@code tankA}, so a panel never saw the second tank; it reads {@code tanks[1]} here.
	 */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("tankA", tanks[0].getFill(), "tankB", tanks[1].getFill());
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }
}
