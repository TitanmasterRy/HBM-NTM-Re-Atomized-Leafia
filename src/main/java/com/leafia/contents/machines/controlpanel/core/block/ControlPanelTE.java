package com.leafia.contents.machines.controlpanel.core.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import com.hbm.blockentity.IPersistentNBT;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.items.tool.ItemMultiDetonator;
import com.leafia.LeafiaMod;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlLinks;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.IRedstonePanel;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Custom Control Panel's block entity: the panel model, the redstone it reads and writes, the one linker slot, and the
 * server's side of everything a client may ask of it. Adapted from NTM-CE's {@code TileEntityControlPanel} (D-3063) with Leafia's
 * {@code MixinTileEntityControlPanel} folded in (the skin, {@link IPersistentNBT}, the {@code setGlobalVar} event). CE's
 * OpenComputers half is not ported (D-1076).
 *
 * <h2>Lifecycle</h2>
 * {@code validate}/{@code invalidate} became {@link #onLoad} and {@link #setRemoved}/{@link #onChunkUnloaded}
 *: the panel registers with its level's {@link ControlEventSystem} on load, subscribes to its
 * links, and leaves on removal or unload. <b>Initialisation is deferred to the first tick</b> where CE ran it inside
 * {@code onLoad}: the {@code initialize} event reads the six neighbours, and a neighbour in a chunk still loading must not be
 * asked for during a chunk load. The read itself is also guarded ({@link Level#isLoaded}), so an unloaded neighbour reads 0.
 *
 * <h2>The trust boundary (§2.5)</h2>
 * {@link #receiveControl(Player, CompoundTag)} is where a client's edit arrives. CE applied a {@code full_set} - the whole panel
 * tree - for any sender; this refuses it unless, in order: the sender is in reach, its open container is an editor for <i>this</i>
 * panel, the tree passes {@link ControlPanelGuard#scan}, the slab geometry is one the editor could have made, no control is
 * unreadable or unsafe or an unknown control invented by the sender, and every link is within the link radius and either already
 * there or stored on a multi-detonator the sender holds. Each refusal is logged with its reason and returned by
 * {@link #applyFullSet} so a test can read it. A click is accepted only as a {@code ctrl_press} on a control that exists.
 */
public class ControlPanelTE extends BlockEntity implements IControllable, IControlReceiver, IRedstonePanel, IPersistentNBT, LeafiaPacketReceiver {

	/** Opens the editor for a player; set by batch 6 when the editor exists. Null until then: nothing opens a menu with no screen. */
	@FunctionalInterface
	public interface EditorOpener {
		void open(net.minecraft.server.level.ServerPlayer player, ControlPanelTE panel);
	}

	public static EditorOpener editorOpener = null;

	/** The linker slot: where a multi-detonator goes to present the positions a panel may link to. */
	public final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	public ControlPanel panel;
	public final ControlPanelType panelType;

	/** The block this panel is dressed as, or null for the plain slab. Leafia's skin. */
	private Block skin = null;
	private boolean destroyedByCreativePlayer = false;

	private final int[] redstoneInputPower = new int[6];
	private final int[] redstoneInputWeak = new int[6];
	private final int[] redstoneInputStrong = new int[6];
	private final int[] redstoneOutputWeak = new int[6];
	private final int[] redstoneOutputStrong = new int[6];
	private final int[] redstoneOutputWeakPending = new int[6];
	private final int[] redstoneOutputStrongPending = new int[6];
	private final Deque<RedstoneInputSnapshot> pendingRedstoneInputSnapshots = new ArrayDeque<>();
	private RedstoneInputSnapshot activeRedstoneInputSnapshot;
	private int redstoneOutputCollectionDepth = 0;
	private boolean needsInitialize = false;

	public ControlPanelTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.CONTROL_PANEL.get(), pos, state);
		this.panelType = state.getBlock() instanceof ControlPanelFrontBlock ? ControlPanelType.FRONT_PANEL : ControlPanelType.CUSTOM_PANEL;
		if(panelType == ControlPanelType.FRONT_PANEL) {
			// Upstream's (commented-out) front block: a nearly upright slab, 11.31 degrees, 0.08 deep at the bottom.
			this.panel = new ControlPanel(this, (float) (1 - Math.tan(Math.toRadians(11.31)) / 2), (float) Math.toRadians(11.31), 0, 0, 0.08F, 0);
		} else {
			this.panel = new ControlPanel(this, 0.25F, (float) Math.toRadians(20), 0, 0, 0.25F, 0);
		}
		Arrays.fill(redstoneInputPower, -1);
		Arrays.fill(redstoneInputWeak, -1);
		Arrays.fill(redstoneInputStrong, -1);
		// onLoad runs on the level's next tick, not at placement; the transform is already needed by then (a click, a renderer).
		updateTransform();
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	public void onLoad() {
		super.onLoad();
		updateTransform();
		if(level == null || level.isClientSide)
			return;
		ControlEventSystem system = ControlEventSystem.get(level);
		system.addControllable(this);
		for(Control c : panel.controls) {
			for(BlockPos b : c.taggedLinks.values())
				system.subscribeTo(this, b);
		}
		needsInitialize = true;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		leaveEventSystem();
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		leaveEventSystem();
	}

	private void leaveEventSystem() {
		if(level != null && !level.isClientSide) {
			ControlEventSystem system = ControlEventSystem.getIfPresent(level);
			if(system != null)
				system.removeControllable(this);
		}
	}

	/** The server ticker. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, ControlPanelTE te) {
		te.update();
	}

	/** Upstream's {@code update}, kept under the base mod's name for machines. */
	public void update() {
		if(level == null || level.isClientSide)
			return;
		if(needsInitialize) {
			needsInitialize = false;
			reinitializePanelState();
			if(hasAnyRedstoneOutput())
				notifyRedstoneNeighbors();
		}
		dispatchPendingRedstoneInputEvents(false);
		panel.update();
		if(!panel.changedVars.isEmpty()) {
			setChanged();
			ControlPanelUpdatePacket.sendToTracking((ServerLevel) level, worldPosition, new ControlPanelUpdatePacket(worldPosition, panel.changedVars));
		}
	}

	// ------------------------------------------------------------------ persistence and sync

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.put("panel", panel.writeToNBT(new CompoundTag()));
		CompoundTag weakOutput = new CompoundTag();
		CompoundTag strongOutput = new CompoundTag();
		for(Direction facing : Direction.values()) {
			weakOutput.putInt(facing.getName(), redstoneOutputWeak[facing.get3DDataValue()]);
			strongOutput.putInt(facing.getName(), redstoneOutputStrong[facing.get3DDataValue()]);
		}
		compound.put("redstoneWeakOut", weakOutput);
		compound.put("redstoneStrongOut", strongOutput);
		if(skin != null)
			compound.putString("skin", BuiltInRegistries.BLOCK.getKey(skin).toString());
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		if(compound.contains("panel", Tag.TAG_COMPOUND))
			panel.readFromNBT(compound.getCompound("panel"));
		CompoundTag weakOutput = compound.getCompound("redstoneWeakOut");
		CompoundTag strongOutput = compound.getCompound("redstoneStrongOut");
		for(Direction facing : Direction.values()) {
			redstoneOutputWeak[facing.get3DDataValue()] = clampRedstoneStrength(weakOutput.getInt(facing.getName()));
			redstoneOutputStrong[facing.get3DDataValue()] = clampRedstoneStrength(strongOutput.getInt(facing.getName()));
		}
		skin = readSkin(compound);
		updateTransform();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Applies what the server said: a full panel, or this tick's variable deltas. Client side. */
	public void applyServerUpdate(ControlPanelUpdatePacket packet) {
		if(packet.full() != null) {
			panel.readFromNBT(packet.full());
			updateTransform();
		} else {
			panel.applyUpdates(packet.updates());
		}
	}

	// ------------------------------------------------------------------ the skin (Leafia)

	private static Block readSkin(CompoundTag tag) {
		if(!tag.contains("skin", Tag.TAG_STRING))
			return null;
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("skin"));
		if(id == null)
			return null;
		Block b = BuiltInRegistries.BLOCK.get(id);
		return b == Blocks.AIR ? null : b;
	}

	public Block getSkin() {
		return skin;
	}

	public void setSkin(Block b) {
		skin = b == Blocks.AIR ? null : b;
		setChanged();
	}

	/** Tells the clients near this panel what it is dressed as. */
	public void sendSkinPackets() {
		writeSkin(LeafiaPacket._start(this)).__sendToAffectedClients();
	}

	public LeafiaPacket.Builder writeSkin(LeafiaPacket.Builder packet) {
		if(skin != null)
			packet.__write(0, BuiltInRegistries.BLOCK.getKey(skin).toString());
		else
			packet.__write(0, null);
		return packet;
	}

	@Override
	public String getPacketIdentifier() {
		return "CTRL_PNL";
	}

	@Override
	public double affectionRange() {
		return 256;
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) {
			if(value == null) {
				skin = null;
			} else {
				ResourceLocation id = ResourceLocation.tryParse((String) value);
				Block b = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
				skin = b == Blocks.AIR ? null : b;
			}
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {}

	@Override
	public void onPlayerValidate(Player plr) {
		writeSkin(LeafiaPacket._start(this)).__sendToClient(plr);
	}

	// ------------------------------------------------------------------ the item keeps the panel (IPersistentNBT)

	@Override
	public void writeNBT(CompoundTag nbt) {
		if(skin != null)
			nbt.putString("skin", BuiltInRegistries.BLOCK.getKey(skin).toString());
		nbt.put("panel", panel.writeToNBT(new CompoundTag()));
	}

	@Override
	public void readNBT(CompoundTag nbt) {
		skin = readSkin(nbt);
		if(nbt.contains("panel", Tag.TAG_COMPOUND))
			panel.readFromNBT(nbt.getCompound("panel"));
		updateTransform();
		setChanged();
	}

	/** Leafia's flag: a creative player's break drops nothing. Vanilla already drops nothing for a creative break, so it is only recorded. */
	public void setDestroyedByCreativePlayer() {
		destroyedByCreativePlayer = true;
	}

	public boolean isDestroyedByCreativePlayer() {
		return destroyedByCreativePlayer;
	}

	// ------------------------------------------------------------------ IControllable

	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		// Leafia's MixinTileEntityControlPanel.leafia$onReceiveEvent: a panel's global can be set by an event.
		if(e.name.equals("setGlobalVar")) {
			DataValue nameVar = e.vars.get("name");
			DataValue val = e.vars.get("value");
			if(nameVar != null && val != null) {
				String name = nameVar.toString();
				if(panel.globalVars.containsKey(name)) {
					if(panel.globalVars.get(name) instanceof DataValueFloat)
						panel.globalVars.put(name, new DataValueFloat(val.getNumber()));
					else
						panel.globalVars.put(name, val);
				}
			}
		}
		panel.receiveEvent(from, e);
	}

	/** Leafia's: {@code setGlobalVar} as well as CE's {@code tick}. */
	@Override
	public List<String> getInEvents() {
		return Arrays.asList("setGlobalVar", "tick");
	}

	@Override
	public BlockPos getControlPos() {
		return getBlockPos();
	}

	@Override
	public Level getControlWorld() {
		return getLevel();
	}

	// ------------------------------------------------------------------ the trust boundary

	/** In reach of the block, as a player editing or clicking must be. */
	@Override
	public boolean hasPermission(Player player) {
		return level != null && !isRemoved() && player.level() == level && player.canInteractWithBlock(worldPosition, 1.0D);
	}

	/** Whether this player's open container is an editor for this panel. */
	public boolean isEditedBy(Player player) {
		return player.containerMenu instanceof IControlEditSession session && session.editedPanel() == this;
	}

	/** The positions every linker the player carries (anywhere in the inventory, and the linker slot) has stored, and the core each one stands for. */
	public Set<BlockPos> presentedLinks(Player player) {
		Set<BlockPos> set = new HashSet<>();
		// Anywhere on the player: the editor hands the linker back to the inventory as soon as it has read it (CE did), so by the
		// time the edit is sent it is rarely in a hand (D-3066). It must still be a multi-detonator the sender owns.
		List<ItemStack> held = new ArrayList<>();
		for(int i = 0; i < player.getInventory().getContainerSize(); i++)
			held.add(player.getInventory().getItem(i));
		held.add(inventory.getStackInSlot(0));
		for(ItemStack stack : held) {
			if(stack.getItem() instanceof ItemMultiDetonator) {
				List<BlockPos> locs = ItemMultiDetonator.getLocations(stack);
				if(locs != null) {
					set.addAll(locs);
					// A linker stores whatever block was clicked; the editor links the core of a multiblock (CE resolved it too).
					if(level != null) {
						for(BlockPos p : locs)
							set.add(ControlLinks.resolveCore(level, p));
					}
				}
			}
		}
		return set;
	}

	@Override
	public void receiveControl(CompoundTag data) {}

	@Override
	public void receiveControl(Player player, CompoundTag data) {
		if(level == null || level.isClientSide)
			return;
		if(data.contains("full_set")) {
			// A refused edit leaves the editor's copy ahead of the server's panel; send the real one back (D-3066).
			if(applyFullSet(player, data) != null && player instanceof net.minecraft.server.level.ServerPlayer sp
					&& sp.connection != null && sp.connection.hasChannel(ControlPanelUpdatePacket.TYPE)) {
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
						new ControlPanelUpdatePacket(worldPosition, panel.writeToNBT(new CompoundTag())));
			}
		} else if(data.contains("click_control")) {
			applyClick(player, data);
		}
	}

	/**
	 * Applies a client's whole-panel edit if every check passes. Returns null on success, else the reason it was refused
	 * (which is also logged). The panel is untouched on a refusal.
	 */
	public String applyFullSet(Player player, CompoundTag data) {
		String why = fullSetProblem(player, data);
		if(why != null) {
			LeafiaMod.LOGGER.warn("Refused a control panel edit from {} at {}: {}", player.getGameProfile().getName(), worldPosition.toShortString(), why);
			return why;
		}

		ControlPanel scratch = new ControlPanel(this);
		scratch.readFromNBT(data);

		ControlEventSystem system = ControlEventSystem.get(level);
		for(BlockPos b : ControlPanelGuard.linksOf(panel))
			system.unsubscribeFrom(this, b);
		this.panel = scratch;
		updateTransform();
		for(BlockPos b : ControlPanelGuard.linksOf(panel))
			system.subscribeTo(this, b);
		setChanged();
		reinitializePanelState();
		// What other players are told is what the server accepted, not the client's bytes.
		ControlPanelUpdatePacket.sendToTracking((ServerLevel) level, worldPosition,
				new ControlPanelUpdatePacket(worldPosition, panel.writeToNBT(new CompoundTag())));
		return null;
	}

	private String fullSetProblem(Player player, CompoundTag data) {
		if(!hasPermission(player))
			return "the sender is not within reach of the panel";
		if(!isEditedBy(player))
			return "the sender has no editor open for this panel";

		String why = ControlPanelGuard.scan(data);
		if(why != null)
			return why;

		for(String key : new String[]{"height", "angle", "a_offset", "b_offset", "c_offset", "d_offset"}) {
			if(!data.contains(key, Tag.TAG_ANY_NUMERIC))
				return "the panel geometry is missing '" + key + "'";
		}
		if(!ControlPanel.geometryValid(data.getFloat("height"), data.getFloat("angle"), data.getFloat("a_offset"),
				data.getFloat("b_offset"), data.getFloat("c_offset"), data.getFloat("d_offset")))
			return "the panel geometry is not one the editor can make";

		ControlPanel scratch = new ControlPanel(this);
		if(!scratch.readFromNBT(data))
			return "the panel could not be read";

		for(Control c : scratch.controls) {
			if(c instanceof UnknownControl u) {
				if(ControlRegistry.isRegistered(u.registryName))
					return "control '" + u.registryName + "' is unsafe or unreadable: " + u.problem;
				boolean carried = false;
				for(Control old : panel.controls) {
					if(old instanceof UnknownControl o && o.registryName.equals(u.registryName) && o.sameContent(u)) {
						carried = true;
						break;
					}
				}
				if(!carried)
					return "unknown control '" + u.registryName + "' was not on the panel";
			}
		}

		Set<BlockPos> known = new HashSet<>(ControlPanelGuard.linksOf(panel));
		Set<BlockPos> presented = presentedLinks(player);
		for(BlockPos link : ControlPanelGuard.linksOf(scratch)) {
			why = ControlPanelGuard.linkProblem(worldPosition, link, known, presented);
			if(why != null)
				return why;
		}
		return null;
	}

	/** A click on a control: only {@code ctrl_press}, only on a control that exists, only from a player in reach. */
	public boolean applyClick(Player player, CompoundTag data) {
		if(!hasPermission(player))
			return false;
		int index = data.getInt("click_control");
		if(index < 0 || index >= panel.controls.size())
			return false;
		if(!"ctrl_press".equals(data.getString("name")))
			return false;
		ControlEvent evt = ControlEvent.newEvent("ctrl_press");
		DataValue sneaking = DataValue.newFromNBT(data.getCompound("vars").get("isSneaking"));
		if(sneaking != null)
			evt.setVar("isSneaking", sneaking.getBoolean());
		panel.receiveDirectEvent(panel.controls.get(index), evt);
		return true;
	}

	// ------------------------------------------------------------------ redstone

	/** Called by the block when a neighbour changes. */
	public void captureRedstoneInputChanges() {
		captureRedstoneInputChanges(false);
	}

	private void captureRedstoneInputChanges(boolean forceEventDispatch) {
		if(level == null || level.isClientSide)
			return;

		int[] nextPower = Arrays.copyOf(redstoneInputPower, 6);
		int[] nextWeak = Arrays.copyOf(redstoneInputWeak, 6);
		int[] nextStrong = Arrays.copyOf(redstoneInputStrong, 6);
		List<Direction> changedFaces = new ArrayList<>();
		for(Direction facing : Direction.values()) {
			int idx = facing.get3DDataValue();
			BlockPos neighborPos = worldPosition.relative(facing);
			int effectivePower = 0;
			int weakPower = 0;
			int strongPower = 0;
			if(level.isLoaded(neighborPos)) {
				BlockState neighborState = level.getBlockState(neighborPos);
				effectivePower = level.getSignal(neighborPos, facing);
				weakPower = neighborState.getSignal(level, neighborPos, facing);
				strongPower = neighborState.getDirectSignal(level, neighborPos, facing);
			}

			boolean changed = effectivePower != redstoneInputPower[idx]
					|| weakPower != redstoneInputWeak[idx]
					|| strongPower != redstoneInputStrong[idx];
			nextPower[idx] = effectivePower;
			nextWeak[idx] = weakPower;
			nextStrong[idx] = strongPower;
			if(changed || forceEventDispatch)
				changedFaces.add(facing);
		}

		System.arraycopy(nextPower, 0, redstoneInputPower, 0, 6);
		System.arraycopy(nextWeak, 0, redstoneInputWeak, 0, 6);
		System.arraycopy(nextStrong, 0, redstoneInputStrong, 0, 6);
		for(Direction facing : changedFaces)
			pendingRedstoneInputSnapshots.addLast(new RedstoneInputSnapshot(facing, nextPower, nextWeak, nextStrong));
	}

	private static ControlEvent createRedstoneInputEvent(Direction facing, int effectivePower, int weakPower, int strongPower) {
		return ControlEvent.newEvent("redstone_input")
				.setVar("facing", facing)
				.setVar("power", effectivePower)
				.setVar("weak", weakPower)
				.setVar("strong", strongPower)
				.setVar("isPowered", effectivePower > 0)
				.setVar("isWeaklyPowered", weakPower > 0)
				.setVar("isStronglyPowered", strongPower > 0);
	}

	private void dispatchPendingRedstoneInputEvents(boolean clearOutputsFirst) {
		if(level == null || level.isClientSide || pendingRedstoneInputSnapshots.isEmpty())
			return;

		beginRedstoneOutputCollection(clearOutputsFirst);
		try {
			BlockPos controlPos = getBlockPos();
			while(!pendingRedstoneInputSnapshots.isEmpty()) {
				activeRedstoneInputSnapshot = pendingRedstoneInputSnapshots.removeFirst();
				panel.receiveEvent(controlPos, activeRedstoneInputSnapshot.toControlEvent());
			}
		} finally {
			activeRedstoneInputSnapshot = null;
			finishRedstoneOutputCollection();
		}
	}

	/** Gives every control {@code initialize}, then reads the neighbours as if every face had just changed. */
	public void reinitializePanelState() {
		if(level == null || level.isClientSide)
			return;

		pendingRedstoneInputSnapshots.clear();
		beginRedstoneOutputCollection(true);
		try {
			for(Control control : new ArrayList<>(panel.controls))
				control.receiveEvent(ControlEvent.newEvent("initialize"));
			captureRedstoneInputChanges(true);
			dispatchPendingRedstoneInputEvents(false);
		} finally {
			finishRedstoneOutputCollection();
		}
	}

	/** The weak signal leaving through {@code facing}, as the block's {@code getSignal} is asked for it. */
	public int getWeakRedstoneOutput(Direction facing) {
		return redstoneOutputWeak[facing.get3DDataValue()];
	}

	public int getStrongRedstoneOutput(Direction facing) {
		return redstoneOutputStrong[facing.get3DDataValue()];
	}

	private void notifyRedstoneNeighbors() {
		Block block = getBlockState().getBlock();
		level.updateNeighborsAt(worldPosition, block);
		for(Direction facing : Direction.values())
			level.updateNeighborsAt(worldPosition.relative(facing), block);
	}

	private boolean hasAnyRedstoneOutput() {
		for(Direction facing : Direction.values()) {
			if(redstoneOutputWeak[facing.get3DDataValue()] > 0 || redstoneOutputStrong[facing.get3DDataValue()] > 0)
				return true;
		}
		return false;
	}

	@Override
	public void beginRedstoneOutputCollection() {
		beginRedstoneOutputCollection(false);
	}

	public void beginRedstoneOutputCollection(boolean clearPending) {
		if(redstoneOutputCollectionDepth++ == 0) {
			if(clearPending) {
				Arrays.fill(redstoneOutputWeakPending, 0);
				Arrays.fill(redstoneOutputStrongPending, 0);
			} else {
				System.arraycopy(redstoneOutputWeak, 0, redstoneOutputWeakPending, 0, 6);
				System.arraycopy(redstoneOutputStrong, 0, redstoneOutputStrongPending, 0, 6);
			}
		}
	}

	@Override
	public void finishRedstoneOutputCollection() {
		if(redstoneOutputCollectionDepth == 0)
			return;
		if(--redstoneOutputCollectionDepth == 0)
			applyRedstoneOutputs(redstoneOutputWeakPending, redstoneOutputStrongPending);
	}

	@Override
	public int getRedstoneInputPower(Direction facing) {
		if(activeRedstoneInputSnapshot != null)
			return activeRedstoneInputSnapshot.getPower(facing);
		return Math.max(0, redstoneInputPower[facing.get3DDataValue()]);
	}

	@Override
	public int getRedstoneInputWeak(Direction facing) {
		if(activeRedstoneInputSnapshot != null)
			return activeRedstoneInputSnapshot.getWeak(facing);
		return Math.max(0, redstoneInputWeak[facing.get3DDataValue()]);
	}

	@Override
	public int getRedstoneInputStrong(Direction facing) {
		if(activeRedstoneInputSnapshot != null)
			return activeRedstoneInputSnapshot.getStrong(facing);
		return Math.max(0, redstoneInputStrong[facing.get3DDataValue()]);
	}

	@Override
	public void setWeakRedstoneOutput(Direction facing, int strength) {
		int idx = facing.get3DDataValue();
		int clamped = clampRedstoneStrength(strength);
		if(redstoneOutputCollectionDepth > 0) {
			redstoneOutputWeakPending[idx] = clamped;
			return;
		}
		int[] weakOutputs = Arrays.copyOf(redstoneOutputWeak, 6);
		weakOutputs[idx] = clamped;
		applyRedstoneOutputs(weakOutputs, redstoneOutputStrong);
	}

	@Override
	public void setStrongRedstoneOutput(Direction facing, int strength) {
		int idx = facing.get3DDataValue();
		int clamped = clampRedstoneStrength(strength);
		if(redstoneOutputCollectionDepth > 0) {
			redstoneOutputStrongPending[idx] = clamped;
			return;
		}
		int[] strongOutputs = Arrays.copyOf(redstoneOutputStrong, 6);
		strongOutputs[idx] = clamped;
		applyRedstoneOutputs(redstoneOutputWeak, strongOutputs);
	}

	private void applyRedstoneOutputs(int[] weakOutputs, int[] strongOutputs) {
		boolean changed = false;
		for(Direction facing : Direction.values()) {
			int idx = facing.get3DDataValue();
			int weak = clampRedstoneStrength(weakOutputs[idx]);
			int strong = clampRedstoneStrength(strongOutputs[idx]);
			if(redstoneOutputWeak[idx] != weak) {
				redstoneOutputWeak[idx] = weak;
				changed = true;
			}
			if(redstoneOutputStrong[idx] != strong) {
				redstoneOutputStrong[idx] = strong;
				changed = true;
			}
		}
		if(changed) {
			setChanged();
			if(level != null && !level.isClientSide)
				notifyRedstoneNeighbors();
		}
	}

	private static int clampRedstoneStrength(int strength) {
		return Math.max(0, Math.min(15, strength));
	}

	// ------------------------------------------------------------------ geometry

	/**
	 * Panel local space to block space, with CE's constants. The slab is placed by which face it stood on (up, down, or a wall)
	 * and which way it faces; control positions are in tenths of a block with the origin at the panel's corner.
	 */
	public void updateTransform() {
		BlockState state = getBlockState();
		if(!state.hasProperty(ControlPanelBlock.FACING))
			return;
		Direction facing = state.getValue(ControlPanelBlock.FACING);
		boolean isUp = state.getValue(ControlPanelBlock.UP);
		boolean isDown = !isUp && state.getValue(ControlPanelBlock.DOWN);

		Matrix4f mat = new Matrix4f();
		if(isUp) {
			mat.translate(0.5F, panel.height, 0.5F);
			rotateByFacing(mat, facing);
			mat.rotate(-panel.angle, 0, 0, 1);
			mat.rotate((float) Math.toRadians(90), 0, 1, 0);
		} else if(isDown) {
			mat.translate(0.5F, 1 - panel.height, 0.5F);
			rotateByFacing(mat, facing);
			mat.rotate((float) Math.toRadians(180), 1, 0, 0);
			mat.rotate(-panel.angle, 0, 0, 1);
			mat.rotate((float) Math.toRadians(90), 0, 1, 0);
		} else {
			mat.translate(0.5F, 0, 0.5F);
			rotateByFacing(mat, facing);
			mat.rotate((float) Math.toRadians(-90), 1, 0, 0);
			mat.rotate((float) Math.toRadians(-90), 0, 0, 1);
			mat.translate(0, panel.height - 0.5F, 0.5F);
			mat.rotate((float) Math.toRadians(-180), 0, 1, 0);
			mat.rotate(panel.angle, 1, 0, 0);
		}

		mat.scale(0.1F, 0.1F, 0.1F);
		mat.translate(0.5F, 0, 0.5F);
		panel.setTransform(mat);
	}

	private static void rotateByFacing(Matrix4f mat, Direction facing) {
		switch(facing) {
			case WEST -> mat.rotate((float) Math.toRadians(180), 0, 1, 0);
			case NORTH -> mat.rotate((float) Math.toRadians(90), 0, 1, 0);
			case SOUTH -> mat.rotate((float) Math.toRadians(270), 0, 1, 0);
			default -> {}
		}
	}

	/** The slab's footprint on the editor's grid: minX, minY, maxX, maxY, in grid units. */
	public float[] getBox() {
		return com.leafia.contents.machines.controlpanel.core.ControlGeometry.panelBox(panel);
	}

	/** The block's collision and selection box for a placement. CE's {@code getBoundingBox(isUp, isDown, facing)}. */
	public AABB getBoundingBox(boolean isUp, boolean isDown, Direction facing) {
		float height1 = ControlPanel.getSlopeHeightFromZ(1 - panel.c_off, panel.height, -panel.angle);
		float height0 = ControlPanel.getSlopeHeightFromZ(panel.a_off, panel.height, -panel.angle);

		AABB box;
		if(isUp) {
			box = new AABB(panel.d_off, 0, panel.a_off, 1 - panel.b_off, Math.max(height0, height1), 1 - panel.c_off);
		} else if(isDown) {
			box = new AABB(1 - panel.d_off, 1, panel.a_off, panel.b_off, 1 - Math.max(height0, height1), 1 - panel.c_off);
		} else {
			box = new AABB(panel.d_off, 1 - panel.a_off, 0, 1 - panel.b_off, panel.c_off, Math.max(height0, height1));
		}
		return rotateAABB(box, facing);
	}

	public static AABB rotateAABB(AABB box, Direction facing) {
		return switch(facing) {
			case NORTH -> new AABB(1 - box.minX, box.minY, 1 - box.maxZ, 1 - box.maxX, box.maxY, 1 - box.minZ);
			case EAST -> new AABB(box.minZ, box.minY, 1 - box.minX, box.maxZ, box.maxY, 1 - box.maxX);
			case WEST -> new AABB(1 - box.minZ, box.minY, box.minX, 1 - box.maxZ, box.maxY, box.maxX);
			default -> box;
		};
	}

	// ------------------------------------------------------------------ redstone snapshots

	private static final class RedstoneInputSnapshot {
		private final Direction changedFace;
		private final int[] powerByFace;
		private final int[] weakByFace;
		private final int[] strongByFace;

		private RedstoneInputSnapshot(Direction changedFace, int[] powerByFace, int[] weakByFace, int[] strongByFace) {
			this.changedFace = changedFace;
			this.powerByFace = Arrays.copyOf(powerByFace, powerByFace.length);
			this.weakByFace = Arrays.copyOf(weakByFace, weakByFace.length);
			this.strongByFace = Arrays.copyOf(strongByFace, strongByFace.length);
		}

		private int getPower(Direction facing) {
			return Math.max(0, powerByFace[facing.get3DDataValue()]);
		}

		private int getWeak(Direction facing) {
			return Math.max(0, weakByFace[facing.get3DDataValue()]);
		}

		private int getStrong(Direction facing) {
			return Math.max(0, strongByFace[facing.get3DDataValue()]);
		}

		private ControlEvent toControlEvent() {
			return createRedstoneInputEvent(changedFace, getPower(changedFace), getWeak(changedFace), getStrong(changedFace));
		}
	}
}
