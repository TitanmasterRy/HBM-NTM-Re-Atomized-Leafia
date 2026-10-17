package com.leafia.contents.machines.elevators.car;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.custom_hbm.util.LCETuple.Triplet;
import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.elevators.EvBuffer;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.EvShaftNeo;
import com.leafia.contents.machines.elevators.car.chips.EvChipBase;
import com.leafia.contents.machines.elevators.car.chips.EvChipItem;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem.StyleType;
import com.leafia.contents.machines.elevators.car.styles.EvWallBase;
import com.leafia.contents.machines.elevators.car.styles.panels.ElevatorPanelBase;
import com.leafia.contents.machines.elevators.car.styles.panels.EvGenericDoorBase;
import com.leafia.contents.machines.elevators.car.styles.panels.S6Door;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.contents.machines.elevators.floors.EvFloorTE;
import com.leafia.contents.machines.elevators.gui.EvCabinMenu;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.ElevatorMusic;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.Selection;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.llib.technical.FifthString;

import api.hbm.block.IToolable.ToolType;
import com.leafia.init.LeafiaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The elevator cabin: an entity that hangs off a pulley, carries players between floors, and is
 * assembled out of six style panels and a control chip.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/car/ElevatorEntity.java}, 1,388
 * lines. This is the keystone of the elevator campaign — twelve other files name it.
 *
 * <h2>What moved, and why (D-1037)</h2>
 *
 * <ul>
 * <li><b>{@code IEntityMultiPart}/{@code MultiPartEntityPart} are gone in 1.21.1.</b> The car uses
 *     them for one clickable hitbox per button per panel. NeoForge's successor is
 *     {@link PartEntity} plus {@link #isMultipartEntity()}/{@link #getParts()}. Upstream builds
 *     the parts <b>client-side only</b> — {@code if (!world.isRemote) return super.getParts()} —
 *     and that is kept: a button press is a click on the client that sends
 *     {@link EvButtonInteractPacket}, so the server never needs the hitboxes.</li>
 * <li><b>Fifteen {@code DataParameter}s</b> become {@link EntityDataAccessor}s defined in
 *     {@link #defineSynchedData}.</li>
 * <li><b>A different height per side.</b> Upstream's {@code entityInit} sets
 *     {@code height = world.isRemote ? 0.1f : 2.5f}: the server wants a tall box so entities ride
 *     the cabin, the client wants a flat one so the player's own collision does not fight it.
 *     1.21.1 fixes dimensions on the {@link EntityType}, so this is a {@link #getDimensions}
 *     override — behaviour, not an accident, so it survives.</li>
 * <li><b>{@code BlockDummyable.META >= 12}</b> is {@code PART == Part.CORE} here, and the
 *     exterior-door test upstream writes as {@code >= 12} is the same thing.</li>
 * <li><b>The radio is in</b> (D-1088): slot 7's {@code EvRadioItem} chooses the music and
 *     {@link #updateMusic} plays it through {@code LCEAudioProxy}, which was what D-1037 had cut
 *     it for. <b>The cabin screen is in</b>, opened by
 *     {@link #interact} for anything the base mod's {@link ToolType} calls a screwdriver; nothing
 *     is one until {@code ItemTooling} is ported. See D-1077.</li>
 * </ul>
 */
public class ElevatorEntity extends Entity {

	// ---------------------------------------------------------------- synched data

	public static final EntityDataAccessor<String> STYLE_FLOOR = def();
	public static final EntityDataAccessor<String> STYLE_CEILING = def();
	public static final EntityDataAccessor<String> STYLE_FRONT = def();
	public static final EntityDataAccessor<String> STYLE_LEFT = def();
	public static final EntityDataAccessor<String> STYLE_RIGHT = def();
	public static final EntityDataAccessor<String> STYLE_BACK = def();

	private static EntityDataAccessor<String> def() {
		return SynchedEntityData.defineId(ElevatorEntity.class, EntityDataSerializers.STRING);
	}

	/**
	 * Upstream's order, which is <b>not</b> the declaration order above: floor, ceiling, front,
	 * left, <em>back</em>, right. {@link #getPanel} indexes this with {@code side + 2}, so the four
	 * walls are entries 2..5 — and swapping back and right here rotates a cabin's walls by two
	 * without anything failing.
	 */
	@SuppressWarnings("unchecked")
	public static final EntityDataAccessor<String>[] styleParams = new EntityDataAccessor[]{
			STYLE_FLOOR, STYLE_CEILING, STYLE_FRONT, STYLE_LEFT, STYLE_BACK, STYLE_RIGHT};

	public static final EntityDataAccessor<String> FLOOR_DISPLAY =
			SynchedEntityData.defineId(ElevatorEntity.class, EntityDataSerializers.STRING);

	public static final EntityDataAccessor<Integer> FLOOR = defInt();
	public static final EntityDataAccessor<Integer> ARROW = defInt();
	public static final EntityDataAccessor<Integer> PULLEY_X = defInt();
	public static final EntityDataAccessor<Integer> PULLEY_Y = defInt();
	public static final EntityDataAccessor<Integer> PULLEY_Z = defInt();
	public static final EntityDataAccessor<Integer> MUSIC = defInt();

	private static EntityDataAccessor<Integer> defInt() {
		return SynchedEntityData.defineId(ElevatorEntity.class, EntityDataSerializers.INT);
	}

	public static final EntityDataAccessor<Float> DOOR_IN = defFloat();
	public static final EntityDataAccessor<Float> DOOR_OUT = defFloat();

	private static EntityDataAccessor<Float> defFloat() {
		return SynchedEntityData.defineId(ElevatorEntity.class, EntityDataSerializers.FLOAT);
	}

	// ---------------------------------------------------------------- state

	public CompoundTag loadData = null;

	public final Map<Integer, String> specialDisplayFloors = new HashMap<>();

	/** What a floor indicator can show. Upstream's set, and the order does not matter. */
	public static final String[] ALLOWED_DIGITS =
			{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "L", "R", "B", "E"};

	public static boolean isDigitAllowed(String s) {
		for(String allowedDigit : ALLOWED_DIGITS) {
			if(allowedDigit.equals(s)) return true;
		}
		return false;
	}

	public BlockPos lastLight = new BlockPos(0, 0, 0);
	public boolean doorOpen = false;
	public boolean down = false;
	public HashSet<Integer> targetFloors = new HashSet<>();

	public int timeSinceStart = 0;
	public double targetHeight = -1;
	public boolean braking = false;
	public int parkFloor = 1;
	public boolean parking = false;

	public HashSet<String> enabledButtons = new HashSet<>();

	/** Client only: how many ticks ago each button was pressed, for the press animation. */
	public HashMap<String, Integer> clickedButtons = new HashMap<>();

	public List<ElevatorButton> buttons = new ArrayList<>();
	public ItemStackHandler inventory;

	public EvChipBase controller = null;
	private String curController = "";

	public EvPulleyTE pulley = null;

	public boolean shouldUpdateItems = true;
	public HashMap<String, List<HitSrf>> surfaces = new HashMap<>();

	private final String[] localLastWalls = new String[4];
	private boolean sync = true;
	private Boolean lightState = null;
	private int lastMusic = -1;

	/** The looping music, or null while none is chosen. Client side only: the server never plays sound. */
	@Nullable
	private LCEAudioWrapper music;
	private int lastPanelCount = 0;

	// ---------------------------------------------------------------- construction

	public ElevatorEntity(EntityType<? extends ElevatorEntity> type, Level level) {
		super(type, level);
		this.inventory = new CabinInventory();
		updateHitSurfaces();
	}

	public ElevatorEntity(Level level) {
		this(LeafiaEntities.ELEVATOR.get(), level);
	}

	/**
	 * Slot 0 takes a chip, 1 a floor panel, 2 a ceiling, 3-6 walls, 7 the radio.
	 *
	 * <p>Upstream's {@code isItemValid} returns true for slot 7 by falling off the end, which is
	 * why the radio slot accepts anything — kept, though the radio item itself is deferred (D-1037),
	 * so today slot 7 holds whatever a player puts in it and nothing reads it.
	 */
	private class CabinInventory extends ItemStackHandler {

		CabinInventory() {
			super(8);
		}

		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			shouldUpdateItems = true;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if(slot == 0) {
				return stack.getItem() instanceof EvChipItem;
			} else if(slot >= 1 && slot < 7) {
				if(stack.getItem() instanceof EvStyleItem style) {
					if(slot == 1) return style.type == StyleType.FLOOR;
					else if(slot == 2) return style.type == StyleType.CEILING;
					else return style.type == StyleType.WALL;
				} else return false;
			}
			return true;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(STYLE_FLOOR, "");
		builder.define(STYLE_CEILING, "");
		builder.define(STYLE_FRONT, "");
		builder.define(STYLE_LEFT, "");
		builder.define(STYLE_BACK, "");
		builder.define(STYLE_RIGHT, "");
		builder.define(FLOOR, 1);
		builder.define(FLOOR_DISPLAY, "1");
		builder.define(ARROW, 1);
		builder.define(DOOR_IN, 0f);
		builder.define(DOOR_OUT, 0f);
		builder.define(PULLEY_X, 1);
		builder.define(PULLEY_Y, 1);
		builder.define(PULLEY_Z, 1);
		builder.define(MUSIC, -1);
	}

	/**
	 * 30/16 wide, and <b>2.5 tall on the server but 0.1 on the client</b> — upstream's
	 * {@code height = world.isRemote ? 0.1f : 2.5f}.
	 *
	 * <p>The server needs the tall box so {@code processEntity} catches riders and so the cabin
	 * collides with the shaft; the client needs the flat one so the player standing inside is not
	 * continuously pushed by the cabin's own bounding box. It reads like a hack and it is load-
	 * bearing: making both 2.5 traps the player, making both 0.1 drops them through the floor.
	 */
	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.fixed(30 / 16f, level().isClientSide ? 0.1f : 2.5f);
	}

	// ---------------------------------------------------------------- floors

	public HashSet<Integer> getTargetFloorsFromEnabledButtons() {
		HashSet<Integer> floors = new HashSet<>();
		for(String id : enabledButtons) {
			if(id.startsWith("floor")) {
				try {
					floors.add(Integer.parseInt(id.substring(5)));
				} catch(NumberFormatException ignored) { }
			}
		}
		return floors;
	}

	public boolean isOnTargetFloor() {
		int floor = getDataInteger(FLOOR);
		for(Integer targetFloor : targetFloors) {
			if(targetFloor == floor) return true;
		}
		return false;
	}

	public Integer getNextFloor(boolean down) {
		int floor = getDataInteger(FLOOR);
		Integer nextFloor = null;
		for(Integer targetFloor : targetFloors) {
			if(targetFloor == floor && !braking) continue;
			if(!down) {
				if(targetFloor >= floor) {
					if(nextFloor == null) nextFloor = targetFloor;
					else nextFloor = Math.min(nextFloor, targetFloor);
				}
			} else {
				if(targetFloor <= floor) {
					if(nextFloor == null) nextFloor = targetFloor;
					else nextFloor = Math.max(nextFloor, targetFloor);
				}
			}
		}
		return nextFloor;
	}

	public Integer getNextFloor() {
		return getNextFloor(down);
	}

	public boolean isFloorOnReverseDirection() {
		int floor = getDataInteger(FLOOR);
		for(Integer targetFloor : targetFloors) {
			if(targetFloor == floor && !braking) continue;
			if(!down) {
				if(targetFloor < floor) return true;
			} else {
				if(targetFloor > floor) return true;
			}
		}
		return false;
	}

	/** The floor number of any {@link EvFloor} landing adjacent to {@code pos}, or null. */
	@Nullable
	public Integer getFloorAtPos(BlockPos pos) {
		for(Direction horizontal : Direction.Plane.HORIZONTAL) {
			BlockPos p = pos.relative(horizontal);
			if(level().getBlockState(p).getBlock() instanceof EvFloor) {
				BlockEntity te = level().getBlockEntity(p);
				if(te instanceof EvFloorTE floorTe)
					return floorTe.floor;
			}
		}
		return null;
	}

	/** {@code {floor: y}} for every landing within {@code offset} blocks, signed for direction. */
	public Map<Integer, Integer> getFloorsInRange(int offset) {
		Map<Integer, Integer> map = new HashMap<>();
		for(int i = 0; i <= Math.abs(offset); i++) {
			BlockPos pos = BlockPos.containing(getX(), getY() + 0.5 + i * Math.signum(offset), getZ());
			Integer floor = getFloorAtPos(pos);
			if(floor != null)
				map.put(floor, pos.getY());
		}
		return map;
	}

	public boolean isEnd(boolean down) {
		int floor = getDataInteger(FLOOR);
		for(ElevatorButton btn : buttons) {
			if(btn instanceof FloorButton fb) {
				if(fb.floor > floor && !down) return false;
				if(fb.floor < floor && down) return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- music

	/**
	 * Up, down and idle music ids from the radio in slot 7: the ordinals of the
	 * {@code ElevatorMusic} its tag names, -1 for any of the three it does not (or for anything but
	 * a radio in the slot). Upstream's {@code getMusic}; the chips call it every tick they decide
	 * what to play.
	 */
	public Triplet<Integer, Integer, Integer> getMusic() {
		ItemStack stack = inventory.getStackInSlot(7);
		if(stack.getItem() instanceof EvRadioItem) {
			Selection selection = EvRadioItem.read(stack);
			return new Triplet<>(selection.up(), selection.down(), selection.idle());
		}
		return new Triplet<>(-1, -1, -1);
	}

	// ---------------------------------------------------------------- pulley

	@Nullable
	public EvPulleyTE getPulley() {
		BlockPos pos = new BlockPos(getDataInteger(PULLEY_X), getDataInteger(PULLEY_Y),
				getDataInteger(PULLEY_Z));
		BlockEntity te = level().getBlockEntity(pos);
		if(te instanceof EvPulleyTE found) return found;
		return null;
	}

	/**
	 * Walks the column above the cabin for a pulley. Upstream does not stop at the first — it takes
	 * the highest — and that is kept; see {@code EvWeightEntity.findPulley}, which does the same.
	 */
	public void findPulley() {
		for(int i = (int) getY(); i < 255; i++) {
			BlockEntity te = level().getBlockEntity(BlockPos.containing(getX(), i, getZ()));
			if(te instanceof EvPulleyTE found) {
				pulley = found;
				BlockPos pos = pulley.getBlockPos();
				entityData.set(PULLEY_X, pos.getX());
				entityData.set(PULLEY_Y, pos.getY());
				entityData.set(PULLEY_Z, pos.getZ());
			}
		}
	}

	// ---------------------------------------------------------------- data accessors

	public String getDataString(EntityDataAccessor<String> param) { return entityData.get(param); }

	public Integer getDataInteger(EntityDataAccessor<Integer> param) { return entityData.get(param); }

	public Float getDataFloat(EntityDataAccessor<Float> param) { return entityData.get(param); }

	/** Upstream calls {@code getDataManager()} on the entity; here the field is what chips use. */
	public SynchedEntityData getDataManager() { return entityData; }

	// ---------------------------------------------------------------- panels

	public List<ElevatorPanelBase> getPanels() {
		List<ElevatorPanelBase> list = new ArrayList<>();
		for(int i = 0; i < 4; i++) {
			ElevatorPanelBase panel = getPanel(i);
			if(panel != null)
				list.add(panel);
		}
		return list;
	}

	/**
	 * The panel on one side, or null if that side is blank.
	 *
	 * <p>Upstream maps both {@code s6door} and {@code skyliftdoor} to {@link S6Door}. That is not a
	 * copy-paste slip: the two share their collision geometry and differ only in what the renderer
	 * draws, so the skylift cabin deliberately reuses the S6 panel. Kept (standing rule 4).
	 */
	@Nullable
	public ElevatorPanelBase getPanel(int side) {
		String style = getDataString(styleParams[side + 2]);
		switch(style) {
			case "s6door": return new S6Door(side);
			case "skyliftdoor": return new S6Door(side);
		}
		return null;
	}

	@Nullable
	public EvWallBase getWallInstance(int side) {
		ElevatorPanelBase panel = getPanel(side);
		if(panel != null) return panel;
		String style = getDataString(styleParams[side + 2]);
		if(style.equals("")) return null;
		return new EvWallBase(side);
	}

	// ---------------------------------------------------------------- multipart

	/**
	 * The button hitboxes, which exist <b>only on the client</b>.
	 *
	 * <p>Upstream returns {@code super.getParts()} — null — on the server, and this keeps that: a
	 * button press is a client-side click that sends {@link EvButtonInteractPacket}, so the server
	 * has no use for the parts and building them there would be per-tick work for nothing.
	 */
	@Override
	public boolean isMultipartEntity() {
		return level().isClientSide;
	}

	@Override
	public PartEntity<?>[] getParts() {
		if(!level().isClientSide) return null;

		List<ElevatorPanelBase> panels = getPanels();
		if(panels.size() != lastPanelCount) {
			lastPanelCount = panels.size();
			for(ElevatorButton button : buttons)
				button.updateHitboxes();
		}

		PartEntity<?>[] list = new PartEntity<?>[buttons.size() * panels.size()];
		try {
			for(int i = 0; i < buttons.size(); i++) {
				for(int j = 0; j < panels.size(); j++)
					list[i * panels.size() + j] = buttons.get(i).hitboxes.get(j);
			}
		} catch(IndexOutOfBoundsException ignored) { }
		return list;
	}

	// ---------------------------------------------------------------- buttons

	public static class ElevatorButton {

		public int x;
		public int y;
		public String id;

		final ElevatorEntity entity;
		final List<ButtonPart> hitboxes = new ArrayList<>();
		List<ElevatorPanelBase> panels;

		ElevatorButton(ElevatorEntity elevator, String id, int x, int y) {
			entity = elevator;
			panels = elevator.getPanels();
			this.id = id;
			if(elevator.level().isClientSide)
				updateHitboxes();
			this.x = x;
			this.y = y;
		}

		void updateHitboxes() {
			panels = entity.getPanels();
			hitboxes.clear();
			for(int i = 0; i < panels.size(); i++)
				hitboxes.add(new ButtonPart(entity, this));
		}

		/** Places each hitbox on its panel, once per tick. */
		void onUpdate() {
			for(int i = 0; i < panels.size() && i < hitboxes.size(); i++) {
				ButtonPart hitbox = hitboxes.get(i);
				ElevatorPanelBase panel = panels.get(i);
				FiaMatrix mat = new FiaMatrix(entity.position())
						.rotateY(-entity.getYRot()).rotateY(panel.rotation * 90);
				double offset = 0.5;
				if(this instanceof FireButton) offset *= -1;
				Vec3 pos = mat.translate(panel.getStaticX() / 16d + x / 16d + offset / 16d,
						y / 16d, -panel.getStaticZ()).position;
				hitbox.setPos(pos.x, pos.y, pos.z);
			}
		}

		public boolean onInteract(Player player, InteractionHand hand) {
			return entity.onButtonPressed(id, player, hand);
		}
	}

	/** One clickable button face. A {@link PartEntity} rather than 1.12.2's MultiPartEntityPart. */
	public static class ButtonPart extends PartEntity<ElevatorEntity> {

		private final ElevatorButton button;
		private final EntityDimensions size = EntityDimensions.fixed(1 / 16f, 1 / 16f);

		public ButtonPart(ElevatorEntity parent, ElevatorButton button) {
			super(parent);
			this.button = button;
			this.refreshDimensions();
		}

		@Override
		public InteractionResult interact(Player player,
				InteractionHand hand) {
			return button.onInteract(player, hand)
					? InteractionResult.SUCCESS
					: InteractionResult.PASS;
		}

		@Override public EntityDimensions getDimensions(Pose pose) { return size; }

		@Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }

		@Override protected void readAdditionalSaveData(CompoundTag tag) { }

		@Override protected void addAdditionalSaveData(CompoundTag tag) { }

		@Override public boolean isPickable() { return true; }
	}

	public static class FloorButton extends ElevatorButton {
		public String label;
		public int floor;
		public FloorButton(ElevatorEntity e, int floor, String label, int x, int y) {
			super(e, "floor" + floor, x, y);
			this.floor = floor;
			this.label = label;
		}
	}

	public static class OpenButton extends ElevatorButton {
		public OpenButton(ElevatorEntity e, int x, int y) { super(e, "open", x, y); }
	}

	public static class CloseButton extends ElevatorButton {
		public CloseButton(ElevatorEntity e, int x, int y) { super(e, "close", x, y); }
	}

	public static class BellButton extends ElevatorButton {
		public BellButton(ElevatorEntity e, int x, int y) { super(e, "bell", x, y); }
	}

	public static class FireButton extends ElevatorButton {
		public FireButton(ElevatorEntity e, int x, int y) { super(e, "fire", x, y); }
	}

	public boolean onButtonPressed(String id, Player player, InteractionHand hand) {
		player.swing(hand);
		if(level().isClientSide) {
			LeafiaCustomPacket.__start(new EvButtonInteractPacket(this, id, hand)).__sendToServer();
		}
		return true;
	}

	/**
	 * A button was pressed, on the server.
	 *
	 * <p>The {@code fire} button no longer wipes the button set (D-3075; upstream installed a hard-coded
	 * demo layout of four floors, a fire button and open/close).
	 */
	public void onButtonServer(String id, @Nullable Player player,
			@Nullable InteractionHand hand) {

		// D-3075: the fire button used to replace the whole button set with a hard-coded demo layout
		// (upstream's leftover debugging). It is now an ordinary click handed to the controller.
		LeafiaCustomPacket.__start(new EvButtonClickPacket(this, id)).__sendToAll();

		if(controller != null)
			controller.onButtonServer(id, player, hand);

		LeafiaCustomPacket.__start(new EvButtonEnablePacket(this)).__sendToAll();
	}

	// ---------------------------------------------------------------- items

	void updateController() {
		if(inventory.getStackInSlot(0).getItem() instanceof EvChipItem chip) {
			if(!chip.getChipId().equals(curController)) {
				curController = chip.getChipId();
				controller = chip.getController(this);
			}
		} else controller = null;
	}

	void updateWallParameters() {
		for(int i = 1; i < 7; i++) {
			String style = "";
			if(inventory.getStackInSlot(i).getItem() instanceof EvStyleItem item)
				style = item.getStyleId();
			entityData.set(styleParams[i - 1], style);
		}
	}

	// ---------------------------------------------------------------- collision surfaces

	void updateHitSurfaces() {
		surfaces.clear();
		HitSrf floorSrf = new HitSrf(new FiaMatrix().rotateX(90),
				-15 / 16d, -15 / 16d, 15 / 16d, 15 / 16d, 3d / 16d).setType(-1);
		HitSrf ceilingSrf = new HitSrf(new FiaMatrix().rotateX(-90).translateWorld(0, 36 / 16d, 0),
				-15 / 16d, -15 / 16d, 15 / 16d, 15 / 16d, 3d).setType(1);
		surfaces.put("floor", new ArrayList<>(List.of(floorSrf)));
		surfaces.put("ceiling", new ArrayList<>(List.of(ceilingSrf)));
		for(int i = 0; i < 4; i++) {
			EvWallBase wall = getWallInstance(i);
			if(wall != null) {
				List<HitSrf> srfs = wall.getHitSurfaces();
				for(HitSrf srf : srfs)
					srf.mat = new FiaMatrix().rotateY(90 * i).travel(srf.mat);
				surfaces.put("wall" + i, srfs);
			}
		}
	}

	void updateDoorCollisions() {
		boolean open = getDataFloat(DOOR_IN) > 0;
		for(int i = 0; i < 4; i++) {
			EvWallBase wall = getWallInstance(i);
			if(wall instanceof EvGenericDoorBase) {
				if(hasExteriorDoor(i)) {
					List<HitSrf> srfs = surfaces.get("wall" + i);
					if(srfs != null && !srfs.isEmpty())
						srfs.get(0).enabled = !open;
				}
			}
		}
	}

	/**
	 * Is there a landing door on this side?
	 *
	 * <p>Upstream tests {@code state.getValue(BlockDummyable.META) >= 12}, 1.12.2's packed
	 * multiblock metadata, where 12 and up is the core. This port carries that as a
	 * {@link BlockDummyable#PART} property, so the same question is "is this the core".
	 */
	public boolean hasExteriorDoor(int side) {
		FiaMatrix mat = new FiaMatrix(new Vec3(getX(), getY() + 0.5, getZ()))
				.rotateY(side * 90 - getYRot());
		BlockPos pos = BlockPos.containing(mat.translate(0, 0, -1).position);
		BlockState state = level().getBlockState(pos);
		if(state.getBlock() instanceof EvFloor)
			return state.getValue(BlockDummyable.PART) == BlockDummyable.Part.CORE;
		return false;
	}

	/** Pushes one entity out of every enabled cabin surface it is inside. */
	public void processEntity(Entity e) {

		if(getBoundingBox().expandTowards(0, -Math.min(0, getDeltaMovement().y) * 4 - e.getBbHeight(), 0)
				.contains(e.position())) {
			e.fallDistance = 0;
			e.setPos(e.getX() + getDeltaMovement().x, e.getY() + getDeltaMovement().y,
					e.getZ() + getDeltaMovement().z);
			if(e.getY() < getY() + getDeltaMovement().y) {
				e.setPos(e.getX(), getY() + getDeltaMovement().y, e.getZ()); // anti-fallthrough
				setMotion(e, e.getDeltaMovement().x,
						Math.max(e.getDeltaMovement().y, getDeltaMovement().y),
						e.getDeltaMovement().z);
			}
		}

		for(List<HitSrf> srfs : surfaces.values()) {
			for(HitSrf srf : srfs) {
				if(!srf.enabled) continue;
				Vec3 pushed = null;
				Vec3 vel = e.getDeltaMovement();
				if(srf.type == -1) {
					pushed = HitSrf.collideVector(e.position(), vel, 0, srf,
							getX(), getY(), getZ(), getYRot());
				} else if(srf.type == 0) {
					AABB bounding = e.getBoundingBox();
					double thickness = (bounding.maxX - bounding.minX) / 4
							+ (bounding.maxZ - bounding.minZ) / 4;
					pushed = HitSrf.collideVector(
							new Vec3(e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ()),
							vel, thickness, srf, getX(), getY(), getZ(), getYRot());
					if(pushed != null) pushed = pushed.subtract(0, e.getBbHeight() / 2, 0);
				} else if(srf.type == 1) {
					pushed = HitSrf.collideVector(
							new Vec3(e.getX(), e.getY() + e.getBbHeight(), e.getZ()),
							vel, 0, srf, getX(), getY(), getZ(), getYRot());
					if(pushed != null) pushed = pushed.subtract(0, e.getBbHeight(), 0);
				}
				if(pushed != null) {
					e.setPos(pushed.x, pushed.y, pushed.z);
					Vec3 killed = HitSrf.killVelocity(e.getDeltaMovement(), srf, getYRot());
					setMotion(e, killed.x, killed.y, killed.z);
				}
			}
		}
	}

	public void setMotion(Entity e, double x, double y, double z) {
		e.setDeltaMovement(x, y, z);
		if(level().isClientSide)
			e.lerpMotion(x, y, z);
	}

	public void setMotion(double x, double y, double z) {
		setMotion(this, x, y, z);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public AABB getBoundingBoxForCulling() {
		double renderMaxHeight = getY() + 2.5;
		if(pulley != null) {
			double pulleyHeight = pulley.getBlockPos().getY();
			if(renderMaxHeight < pulleyHeight)
				renderMaxHeight = pulleyHeight;
		}
		return new AABB(getX() - 1.5, getY(), getZ() - 1.5,
				getX() + 1.5, renderMaxHeight, getZ() + 1.5);
	}

	@Override
	public void tick() {

		BlockPos newLight = BlockPos.containing(getX(), getY() + 1.5, getZ());
		boolean newLightState = pulley != null && pulley.getPower() >= EvPulleyTE.consumption;

		if(!lastLight.equals(newLight) || lightState == null || !lightState.equals(newLightState)) {
			if(level().getBlockState(lastLight).getBlock() instanceof ElevatorLight)
				level().removeBlock(lastLight, false);
			lastLight = newLight;
			if(pulley != null && newLightState && LeafiaBlocks.elevator_light != null)
				level().setBlockAndUpdate(lastLight, LeafiaBlocks.elevator_light.get().defaultBlockState());
		}
		lightState = newLightState;

		super.tick();

		if(!level().isClientSide) {
			if(loadData != null)
				readAdditionalSaveData(loadData);
			loadData = null;
		}

		if(pulley != null && pulley.isRemoved())
			pulley = null;

		// Upstream wraps the whole body: a chip mutating targetFloors while it is being walked is
		// a real possibility and upstream chose to swallow it rather than copy the set.
		try {
			if(level().isClientSide) tickClient();
			else tickServer();

			if(pulley != null)
				pulley.elevator = this;
			updateDoorCollisions();
		} catch(ConcurrentModificationException ignored) { }
	}

	private void tickClient() {

		if(sync) {
			sync = false;
			EvSyncRequestPacket packet = new EvSyncRequestPacket();
			packet.localEntity = this;
			LeafiaCustomPacket.__start(packet).__sendToServer();
		}

		if(pulley == null)
			findPulley();

		for(ElevatorButton button : buttons) button.onUpdate();

		for(String btn : new ArrayList<>(clickedButtons.keySet())) {
			clickedButtons.put(btn, clickedButtons.get(btn) + 1);
			if(clickedButtons.get(btn) > 4)
				clickedButtons.remove(btn);
		}

		Player player = clientPlayer();
		if(player != null && !player.isSpectator())
			processEntity(player);

		move(MoverType.SELF, getDeltaMovement());

		boolean updateSurfaces = false;
		for(int i = 0; i < 4; i++) {
			String style = getDataString(styleParams[i + 2]);
			if(!style.equals(localLastWalls[i])) {
				localLastWalls[i] = style;
				updateSurfaces = true;
			}
		}
		if(updateSurfaces)
			updateHitSurfaces();

		updateMusic();
	}

	/**
	 * Starts, moves and stops the cabin's music from the synched {@code MUSIC} id. Upstream's
	 * block in {@code onUpdate}'s client branch, a method here only so a test can drive it with a
	 * recording {@code LCEAudioProxy} factory - there is no client in a GameTest.
	 *
	 * <p>A changed id stops what was playing (and so plays its end clip, if it has one); a
	 * non-negative id with nothing playing builds a looping sound at the cabin with upstream's
	 * arguments - no start clip, the track's own end clip, the records mixer, volume 1, pitch 1 -
	 * and starts it; and whatever is playing is moved to the cabin every tick. The sound has the
	 * wrapper's default attenuation, linear, so it is heard within sixteen blocks.
	 */
	public void updateMusic() {
		int curMusic = getDataInteger(MUSIC);
		if(lastMusic != curMusic) {
			lastMusic = curMusic;
			if(music != null) {
				music.stopSound();
				music = null;
			}
		}
		if(curMusic != -1 && music == null) {
			ElevatorMusic data = ElevatorMusic.ofOrdinal(curMusic);
			music = LCEAudioProxy.getLoopedSoundStartStop(level(), data.loop(), null, data.end(),
					SoundSource.RECORDS, (float) getX(), (float) getY(), (float) getZ(), 1, 1);
			music.setLooped(true);
			music.startSound();
		}
		if(music != null)
			music.updatePosition((float) getX(), (float) getY(), (float) getZ());
	}

	/** Stops the music if it is playing. Upstream's {@code setDead} body; called from every way a client loses a cabin. */
	private void stopMusic() {
		if(music != null) {
			music.stopSound();
			music = null;
		}
		lastMusic = -1;
	}

	/** The playing music's handle, or null; for tests. */
	@Nullable
	public LCEAudioWrapper getMusicHandle() {
		return music;
	}

	private void tickServer() {

		if(shouldUpdateItems) {
			updateController();
			updateWallParameters();
			updateHitSurfaces();
			shouldUpdateItems = false;
		}

		if(sync) {
			sync = false;
			broadcastFullState(null);
		}

		AABB area = new AABB(getX() - 1.5, getY() - 0.2, getZ() - 1.5,
				getX() + 1.5, getY() + 2.5, getZ() + 1.5);
		for(Entity entity : level().getEntities(this, area)) {
			if(!(entity instanceof Player))
				processEntity(entity);
			else
				entity.fallDistance = 0;
		}

		// A buffer anywhere in the 3x3 column up to four blocks below cushions the fall.
		int bufferHeight = -1;
		boolean foundBuffer = false;
		outer:
		for(int i = -1; i <= 1; i++) {
			for(int j = -1; j <= 1; j++) {
				for(int yo = 0; yo >= -4; yo--) {
					BlockPos bp = BlockPos.containing(getX() + i, getY() - 0.5 + yo, getZ() + j);
					if(level().getBlockState(bp).getBlock() instanceof EvBuffer buf) {
						foundBuffer = true;
						BlockPos core = buf.findCore(level(), bp);
						bufferHeight = (core == null ? bp.getY() : core.getY()) + 3;
						break outer;
					}
				}
			}
		}

		if(foundBuffer) {
			double dist = getY() - bufferHeight;
			double mul = Math.pow(Mth.clamp(dist / 3, 0, 1), 0.5);
			if(getDeltaMovement().y < 0)
				setDeltaMovement(getDeltaMovement().multiply(1, mul, 1));
		}

		if(pulley == null) {
			findPulley();
			if(pulley == null && !foundBuffer)
				setMotion(getDeltaMovement().x, getDeltaMovement().y - 9.8 / 400, getDeltaMovement().z);
		}

		if(pulley != null) {
			boolean guided = false;
			/*
			 * Upstream's `for (int i = 0; i < 3; i++)` checked only three of the four horizontals,
			 * so a cabin guided solely by a rail on the fourth side was not recognised. Fixed in
			 * D-3075 (owner decision 2026-10-17): all four.
			 */
			for(int i = 0; i < 4; i++) {
				Direction face = Direction.from2DDataValue(i);
				BlockPos at = BlockPos.containing(getX(), getY() + 0.5, getZ()).relative(face);
				if(level().getBlockState(at).getBlock() instanceof EvShaftNeo) {
					guided = true;
					setMotion(0, getDeltaMovement().y, 0);
					setPos(pulley.getBlockPos().getX() + 0.5, getY(), pulley.getBlockPos().getZ() + 0.5);
					break;
				}
			}
			if(!guided)
				setMotion((pulley.getBlockPos().getX() + 0.5 - getX()) / 20, getDeltaMovement().y,
						(pulley.getBlockPos().getZ() + 0.5 - getZ()) / 20);
		}

		if(controller != null)
			controller.passiveTick();

		if(pulley != null && pulley.elevator != null && pulley.counterweight != null
				&& pulley.getPower() >= EvPulleyTE.consumption) {
			if(controller != null)
				controller.onUpdate();
		} else {
			entityData.set(MUSIC, -1);
			if(pulley != null)
				setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
		}

		int floor = getDataInteger(FLOOR);
		if(specialDisplayFloors.containsKey(floor))
			entityData.set(FLOOR_DISPLAY, specialDisplayFloors.get(floor));
		else
			entityData.set(FLOOR_DISPLAY, Integer.toString(floor));
		if(pulley != null && pulley.counterweight == null)
			entityData.set(FLOOR_DISPLAY, "Er");

		Vec3 prevMotion = getDeltaMovement();
		move(MoverType.SELF, getDeltaMovement());

		if(horizontalCollision || verticalCollision) {
			if(prevMotion.length() > 15 / 20d && !foundBuffer) {
				level().explode(null, getX(), getY(), getZ(), 3,
						Level.ExplosionInteraction.BLOCK);
				int slots = inventory.getSlots();
				for(int i = 0; i < slots; i++) {
					ItemStack stack = inventory.getStackInSlot(i);
					spawnAtLocation(stack);
					inventory.setStackInSlot(i, ItemStack.EMPTY);
				}
				discard();
				return;
			}
		}

		for(Direction horizontal : Direction.Plane.HORIZONTAL) {
			BlockPos p = BlockPos.containing(getX(), getY() + 0.5, getZ()).relative(horizontal);
			if(level().getBlockState(p).getBlock() instanceof EvFloor) {
				BlockEntity te = level().getBlockEntity(p);
				if(te instanceof EvFloorTE floorTe)
					floorTe.open.cur = getDataFloat(DOOR_OUT);
			}
		}
	}

	/**
	 * The four state packets a joining or resyncing client needs, in upstream's order.
	 *
	 * <p>Upstream writes this out twice — once in {@code EvSyncRequestPacket} to one player and
	 * once in {@code onUpdate} to everyone — with the four blocks copy-pasted. One method with a
	 * nullable recipient says the same thing once; the order is upstream's and is kept, because
	 * the button-enable packet names buttons that the button-sync packet has to have created.
	 */
	void broadcastFullState(@Nullable Player to) {
		send(new EvSpecialFloorsSyncPacket(this), to);
		send(new EvButtonSyncPacket(this), to);
		send(new EvButtonEnablePacket(this), to);
		send(new EvInventorySyncPacket(this), to);
	}

	private static void send(LeafiaCustomPacketEncoder packet, @Nullable Player to) {
		LeafiaCustomPacket started = LeafiaCustomPacket.__start(packet);
		if(to == null) started.__sendToAll();
		else started.__sendToClient(to);
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		stopMusic();
	}

	/**
	 * <b>The three ways a client loses a cabin, and only the first is {@code remove}.</b>
	 * {@code ClientLevel} removes an entity with {@code setRemoved} (which is final) followed by
	 * {@code onClientRemoval}, never through {@code remove}, and a cabin that goes out of range or
	 * whose chunk unloads leaves through {@code onRemovedFromLevel}. Overriding only
	 * {@code remove} - upstream's {@code setDead} - would leave the music looping at the spot where
	 * the cabin was, for good.
	 */
	@Override
	public void onClientRemoval() {
		super.onClientRemoval();
		stopMusic();
	}

	@Override
	public void onRemovedFromLevel() {
		super.onRemovedFromLevel();
		stopMusic();
	}

	// ---------------------------------------------------------------- collision

	@Override
	public boolean canBeCollidedWith() {
		return !isRemoved();
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	// ---------------------------------------------------------------- interaction

	/**
	 * A screwdriver opens the cabin's button and slot editor. Upstream's
	 * {@code processInitialInteract}, whose {@code ItemTooling} branch this is.
	 *
	 * <p>Upstream tests {@code instanceof ItemTooling} and {@code getType() == SCREWDRIVER}; the
	 * base mod answers both with {@link ToolType#getType}, which is filled in by each tool's
	 * constructor. <b>Nothing is registered as a screwdriver today</b> - {@code ItemTooling} is
	 * unported and the base mod's screwdriver is a plain {@code Item} - so this is dormant, and
	 * it wakes with no change here the moment that item lands. Until then a GameTest registers a
	 * stack and drives it, and says so.
	 *
	 * <p>The answer is {@code SUCCESS} whatever was held, because upstream's is {@code true}
	 * unconditionally: a right click on the cabin body is consumed even with an empty hand. The
	 * buttons are separate entities ({@link ButtonPart}) and answer for themselves.
	 *
	 * <p>The upstream <b>hand drill</b> branch - which packs the cabin back into an
	 * {@code ev_spawn} item - sits in the same method and is not here: it is not a GUI, and
	 * the hand drill is the same unported {@code ItemTooling}. A hand-off in D-1077.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {

		ItemStack held = player.getItemInHand(hand);

		if(!held.isEmpty() && ToolType.getType(held) == ToolType.SCREWDRIVER) {
			player.swing(hand);
			if(player instanceof ServerPlayer server)
				openCabinMenu(server);
		}

		return InteractionResult.sidedSuccess(level().isClientSide);
	}

	/** Opens {@link EvCabinMenu}; the payload is this cabin's entity id, which the client looks up. */
	public void openCabinMenu(ServerPlayer player) {
		player.openMenu(
				new SimpleMenuProvider((id, inventory, p) -> new EvCabinMenu(id, inventory, this), Component.empty()),
				buf -> buf.writeVarInt(getId()));
	}

	// ---------------------------------------------------------------- nbt

	@Override
	protected void readAdditionalSaveData(CompoundTag compound) {

		Tag list0 = compound.get("buttons");
		if(list0 instanceof ListTag list) {
			buttons.clear();
			for(Tag nbtBase : list) {
				CompoundTag tag = (CompoundTag) nbtBase;
				int x = tag.getByte("x");
				int y = tag.getByte("y");
				switch(tag.getString("type")) {
					case "floor" -> buttons.add(new FloorButton(this, tag.getByte("floor"),
							tag.getString("label"), x, y));
					case "fire" -> buttons.add(new FireButton(this, x, y));
					case "open" -> buttons.add(new OpenButton(this, x, y));
					case "close" -> buttons.add(new CloseButton(this, x, y));
					case "bell" -> buttons.add(new BellButton(this, x, y));
					default -> { }
				}
			}
		}

		if(compound.contains("displays")) {
			CompoundTag displays = compound.getCompound("displays");
			specialDisplayFloors.clear();
			for(String key : displays.getAllKeys())
				specialDisplayFloors.put(Integer.parseInt(key), displays.getString(key));
		}

		if(compound.contains("enableds")) {
			ListTag enableds = compound.getList("enableds", Tag.TAG_STRING);
			for(Tag enabled : enableds)
				enabledButtons.add(enabled.getAsString());
		}

		entityData.set(FLOOR, (int) compound.getByte("floor"));
		parkFloor = compound.getByte("parkFloor");

		if(compound.contains("inventory"))
			inventory.deserializeNBT(level().registryAccess(), compound.getCompound("inventory"));

		updateController();
		if(controller != null && compound.contains("chipData"))
			controller.readEntityFromNBT(compound.getCompound("chipData"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag compound) {

		ListTag list = new ListTag();
		for(ElevatorButton button : buttons) {
			CompoundTag tag = new CompoundTag();
			tag.putByte("x", (byte) button.x);
			tag.putByte("y", (byte) button.y);
			if(button instanceof FloorButton fb) {
				tag.putString("type", "floor");
				tag.putString("label", fb.label);
				tag.putByte("floor", (byte) fb.floor);
			} else if(button instanceof FireButton) tag.putString("type", "fire");
			else if(button instanceof OpenButton) tag.putString("type", "open");
			else if(button instanceof CloseButton) tag.putString("type", "close");
			else if(button instanceof BellButton) tag.putString("type", "bell");
			list.add(tag);
		}
		compound.put("buttons", list);

		CompoundTag displays = new CompoundTag();
		for(Entry<Integer, String> entry : specialDisplayFloors.entrySet())
			displays.putString(entry.getKey().toString(), entry.getValue());
		compound.put("displays", displays);

		ListTag enableds = new ListTag();
		for(String enabled : enabledButtons)
			enableds.add(StringTag.valueOf(enabled));
		compound.put("enableds", enableds);

		compound.putByte("floor", getDataInteger(FLOOR).byteValue());
		compound.putByte("parkFloor", (byte) parkFloor);
		compound.put("inventory", inventory.serializeNBT(level().registryAccess()));

		if(controller != null) {
			CompoundTag chipData = new CompoundTag();
			controller.writeEntityToNBT(chipData);
			compound.put("chipData", chipData);
		}
	}


	// ---------------------------------------------------------------- packets

	/*
	 * Upstream declares eight LeafiaCustomPacketEncoders inside this class, and all eight are here.
	 * The eighth, EvButtonModifyPacket, is the cabin screen's button editor; it sits last, after
	 * the other seven, because it arrived with the screen (D-1077) and the wire ids are append-only.
	 *
	 * Every one follows the encoder contract: decode() reads EAGERLY on the network thread and
	 * returns a callback that runs on the game thread, so nothing below keeps the LeafiaBuf.
	 */

	/** Finds the elevator an arriving packet names, on whichever side it landed. */
	@Nullable
	private static ElevatorEntity find(Level level, int id) {
		if(level == null) return null;
		Entity get = level.getEntity(id);
		return get instanceof ElevatorEntity ev ? ev : null;
	}

	/** Most buttons a cabin may carry. A panel has room for a dozen; the cap only stops a runaway client. */
	public static final int MAX_BUTTONS = 64;

	/** Longest label or floor indicator a client may set: the cabin screen's text field is 32 characters. */
	public static final int MAX_LABEL = 32;

	/** Whether the cabin has a button with this id. */
	boolean hasButton(String id) {
		for(ElevatorButton button : buttons)
			if(button.id.equals(id)) return true;
		return false;
	}

	/**
	 * The cabin a serverbound packet names, if the sender is entitled to use it (D-1094): the packet
	 * came toward a server, from a server-side player, and the cabin exists, is alive and is within
	 * reach of the sender.
	 */
	@Nullable
	private static ElevatorEntity reachable(IPayloadContext ctx, int id, String packet) {

		if(ctx.flow() != net.minecraft.network.protocol.PacketFlow.SERVERBOUND) return null;

		Player player = ctx.player();
		ElevatorEntity entity = find(player.level(), id);

		if(entity == null || entity.isRemoved()) {
			PacketGuard.reject(packet, player, "no such cabin " + id);
			return null;
		}

		if(!PacketGuard.near(player, entity)) {
			PacketGuard.reject(packet, player, "cabin " + id + " is out of reach");
			return null;
		}

		return entity;
	}

	/**
	 * As {@link #reachable}, and the sender must also have this cabin's screen open: the packets that
	 * edit the button layout are the screen's, and a player who has not opened it (it takes a
	 * screwdriver) has no business sending them.
	 */
	@Nullable
	private static ElevatorEntity editable(IPayloadContext ctx, int id, String packet) {

		ElevatorEntity entity = reachable(ctx, id, packet);
		if(entity == null) return null;

		if(!(ctx.player().containerMenu instanceof EvCabinMenu menu) || menu.entity != entity) {
			PacketGuard.reject(packet, ctx.player(), "the cabin screen is not open");
			return null;
		}

		return entity;
	}

	/**
	 * The client's level, or null anywhere but a client.
	 *
	 * <p>Upstream reaches for {@code Minecraft.getMinecraft()} directly from this class, which is
	 * safe in 1.12.2 because its packet classes are registered one-directional and the handler is
	 * {@code @SideOnly(CLIENT)}. It is <b>not</b> safe here, and it crashed a dedicated server on
	 * boot before this guard existed: this port has one payload type registered both ways, the
	 * packet classes are nested in <em>this</em> class, so registering them loads
	 * {@code ElevatorEntity}, and verifying a method whose bytecode names
	 * {@code ClientLevel} throws {@code "Attempted to load class ... for invalid dist
	 * DEDICATED_SERVER"} before a single line runs.
	 *
	 * <p>The fix is the one {@code LeafiaCustomPacket.SyncVelocityPacket} already uses: every
	 * client type stays inside a {@link ClientOnly} holder that a dedicated server never loads,
	 * and the call site asks {@link FMLEnvironment} first. Nothing about the behaviour changes on
	 * a client. D-1039.
	 */
	@Nullable
	private static Level clientLevel() {
		return FMLEnvironment.dist.isClient() ? ClientOnly.level() : null;
	}

	@Nullable
	private static Player clientPlayer() {
		return FMLEnvironment.dist.isClient() ? ClientOnly.player() : null;
	}

	/** Every reference to a client-only type in this class, and nothing else. */
	@OnlyIn(Dist.CLIENT)
	private static final class ClientOnly {

		@Nullable
		static Level level() {
			return net.minecraft.client.Minecraft.getInstance().level;
		}

		@Nullable
		static Player player() {
			return net.minecraft.client.Minecraft.getInstance().player;
		}
	}

	/** Client -> server: a player pressed a button. */
	public static class EvButtonInteractPacket implements LeafiaCustomPacketEncoder {

		ElevatorEntity localEntity;
		String localId;
		InteractionHand localHand;

		public EvButtonInteractPacket() { }

		public EvButtonInteractPacket(ElevatorEntity localEntity, String localId, InteractionHand localHand) {
			this.localEntity = localEntity;
			this.localHand = localHand;
			this.localId = localId;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(localEntity.getId());
			buf.writeFifthString(new FifthString(localId));
			buf.writeByte(localHand.ordinal());
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		/**
		 * Validated (D-1094, which closes the first half of D-1077's flag): the sender must be within
		 * reach of the cabin, the hand must be one of the two there are, and the button must be one
		 * this cabin actually has - upstream's handler ran whatever id it was sent, and {@code "fire"}
		 * rewrites the whole button set. A press is a world action, not a GUI one, so no menu is
		 * required.
		 */
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			String pressedButton = buf.readFifthString().toString();
			int handIndex = buf.readByte();
			if(handIndex < 0 || handIndex >= InteractionHand.values().length) return null;
			InteractionHand hand = InteractionHand.values()[handIndex];
			return ctx -> {
				ElevatorEntity entity = reachable(ctx, id, "EvButtonInteractPacket");
				if(entity == null) return;
				if(!entity.hasButton(pressedButton)) {
					PacketGuard.reject("EvButtonInteractPacket", ctx.player(), "no button \"" + pressedButton + "\"");
					return;
				}
				entity.onButtonServer(pressedButton, ctx.player(), hand);
			};
		}
	}

	/**
	 * Server -> client: the whole button layout.
	 *
	 * <p>The button <em>type</em> is bit-packed into three bits rather than written as a byte, so
	 * the identifier and the two coordinates share the stream. {@code identifierBits} has to match
	 * on both sides or every field after the first button is read from the wrong offset — which is
	 * why it is one constant used by both halves and not two literals.
	 */
	public static class EvButtonSyncPacket implements LeafiaCustomPacketEncoder {

		ElevatorEntity serverEntity;
		static final int identifierBits = 3;

		public EvButtonSyncPacket() { }

		public EvButtonSyncPacket(ElevatorEntity serverEntity) { this.serverEntity = serverEntity; }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(serverEntity.getId());
			buf.writeInt(serverEntity.buttons.size());
			for(ElevatorButton btn : serverEntity.buttons) {
				if(btn instanceof FloorButton fb) {
					buf.insert(0, identifierBits);
					buf.writeInt(fb.floor);
					buf.writeFifthString(new FifthString(fb.label));
				} else if(btn instanceof FireButton) buf.insert(1, identifierBits);
				else if(btn instanceof OpenButton) buf.insert(2, identifierBits);
				else if(btn instanceof CloseButton) buf.insert(3, identifierBits);
				else if(btn instanceof BellButton) buf.insert(4, identifierBits);
				buf.writeByte(btn.x);
				buf.writeByte(btn.y);
			}
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			int id = buf.readInt();
			int btnCount = buf.readInt();

			// Read every button here, on the network thread: the callback must not touch the buf.
			List<Object[]> read = new ArrayList<>();
			for(int i = 0; i < btnCount; i++) {
				int buttonType = buf.extract(identifierBits);
				if(buttonType == 0) {
					int floor = buf.readInt();
					String label = buf.readFifthString().toString();
					read.add(new Object[]{buttonType, floor, label, buf.readByte(), buf.readByte()});
				} else {
					read.add(new Object[]{buttonType, 0, "", buf.readByte(), buf.readByte()});
				}
			}

			return ctx -> {
				ElevatorEntity entity = find(clientLevel(), id);
				if(entity == null) return;
				entity.buttons.clear();
				for(Object[] b : read) {
					int type = (Integer) b[0];
					int x = (Byte) b[3];
					int y = (Byte) b[4];
					switch(type) {
						case 0 -> entity.buttons.add(
								new FloorButton(entity, (Integer) b[1], (String) b[2], x, y));
						case 1 -> entity.buttons.add(new FireButton(entity, x, y));
						case 2 -> entity.buttons.add(new OpenButton(entity, x, y));
						case 3 -> entity.buttons.add(new CloseButton(entity, x, y));
						case 4 -> entity.buttons.add(new BellButton(entity, x, y));
						default -> { }
					}
				}
			};
		}
	}

	/** Server -> client: which buttons are lit. */
	public static class EvButtonEnablePacket implements LeafiaCustomPacketEncoder {

		ElevatorEntity serverEntity;

		public EvButtonEnablePacket() { }

		public EvButtonEnablePacket(ElevatorEntity serverEntity) { this.serverEntity = serverEntity; }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(serverEntity.getId());
			buf.writeByte(serverEntity.enabledButtons.size());
			for(String id : serverEntity.enabledButtons)
				buf.writeFifthString(new FifthString(id));
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			String[] enableds = new String[buf.readByte()];
			for(int i = 0; i < enableds.length; i++)
				enableds[i] = buf.readFifthString().toString();
			return ctx -> {
				ElevatorEntity entity = find(clientLevel(), id);
				if(entity == null) return;
				entity.enabledButtons.clear();
				entity.enabledButtons.addAll(List.of(enableds));
			};
		}
	}

	/** Server -> client: play the press animation on one button. */
	public static class EvButtonClickPacket implements LeafiaCustomPacketEncoder {

		ElevatorEntity serverEntity;
		String serverId;

		public EvButtonClickPacket() { }

		public EvButtonClickPacket(ElevatorEntity serverEntity, String serverId) {
			this.serverEntity = serverEntity;
			this.serverId = serverId;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(serverEntity.getId());
			buf.writeFifthString(new FifthString(serverId));
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			String pressedButton = buf.readFifthString().toString();
			return ctx -> {
				ElevatorEntity entity = find(clientLevel(), id);
				if(entity != null)
					entity.clickedButtons.put(pressedButton, 0);
			};
		}
	}

	/** Server -> client: the floors whose indicator shows something other than their number. */
	public static class EvSpecialFloorsSyncPacket implements LeafiaCustomPacketEncoder {

		public ElevatorEntity serverEntity;

		public EvSpecialFloorsSyncPacket() { }

		public EvSpecialFloorsSyncPacket(ElevatorEntity serverEntity) { this.serverEntity = serverEntity; }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(serverEntity.getId());
			buf.writeInt(serverEntity.specialDisplayFloors.size());
			for(Entry<Integer, String> entry : serverEntity.specialDisplayFloors.entrySet()) {
				buf.writeInt(entry.getKey());
				buf.writeUTF8String(entry.getValue());
			}
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			int length = buf.readInt();
			Map<Integer, String> read = new HashMap<>();
			for(int i = 0; i < length; i++)
				read.put(buf.readInt(), buf.readUTF8String());
			return ctx -> {
				ElevatorEntity entity = find(clientLevel(), id);
				if(entity == null) return;
				entity.specialDisplayFloors.clear();
				entity.specialDisplayFloors.putAll(read);
			};
		}
	}

	/** Client -> server: "I just loaded, send me everything." */
	public static class EvSyncRequestPacket implements LeafiaCustomPacketEncoder {

		public ElevatorEntity localEntity;

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(localEntity.getId());
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int entityId = buf.readInt();
			return ctx -> {
				if(ctx.flow() != net.minecraft.network.protocol.PacketFlow.SERVERBOUND) return;
				Player player = ctx.player();
				ElevatorEntity entity = find(player.level(), entityId);
				if(entity == null || entity.isRemoved()) {
					PacketGuard.reject("EvSyncRequestPacket", player, "no such cabin " + entityId);
					return;
				}
				// Not the interaction reach: a client asks the moment a cabin comes into view, which is
				// as far away as the entity type is tracked. Anything further cannot have seen it.
				double tracked = entity.getType().clientTrackingRange() * 16.0D;
				if(player.distanceToSqr(entity) > tracked * tracked) {
					PacketGuard.reject("EvSyncRequestPacket", player, "cabin " + entityId + " is out of sight");
					return;
				}
				entity.broadcastFullState(player);
			};
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}
	}

	/** Server -> client: the cabin's eight slots, as one NBT blob. */
	public static class EvInventorySyncPacket implements LeafiaCustomPacketEncoder {

		public ElevatorEntity serverEntity;

		public EvInventorySyncPacket() { }

		public EvInventorySyncPacket(ElevatorEntity serverEntity) { this.serverEntity = serverEntity; }

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(serverEntity.getId());
			buf.writeNBT(serverEntity.inventory.serializeNBT(serverEntity.level().registryAccess()));
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int id = buf.readInt();
			CompoundTag inventory = buf.readNBT();
			return ctx -> {
				ElevatorEntity entity = find(clientLevel(), id);
				if(entity != null && inventory != null)
					entity.inventory.deserializeNBT(entity.level().registryAccess(), inventory);
			};
		}
	}

	/**
	 * Client -> server: the cabin screen's button editor. Upstream's {@code EvButtonModifyPacket}.
	 *
	 * <p>One packet, four modes, chosen by two bits: <b>0</b> moves a button, <b>1</b> adds one
	 * (of five types, in three bits), <b>2</b> removes one and <b>3</b> edits a floor button's
	 * floor, indicator or label. Whatever it does, the server answers with an
	 * {@link EvButtonSyncPacket} to <em>everyone</em>, as upstream does.
	 *
	 * <h2>Read eagerly, which upstream did not</h2>
	 *
	 * Upstream's mode 3 returns a callback that reads the floor byte or the string out of the
	 * buffer <em>inside</em> the callback, on the game thread, after {@code decode} has returned.
	 * That worked in 1.12.2 because the buffer outlived the call; {@link LeafiaCustomPacketEncoder}
	 * forbids it here, so the value is read where the bytes arrive. Nothing changes on the wire.
	 *
	 * <h2>Validated, which upstream's is not (D-1094)</h2>
	 *
	 * Upstream's handler asked nothing of the sender: any client that could name an entity id could
	 * rearrange any cabin's buttons from any distance, and a negative target reached
	 * {@code List.get(-1)} and kicked the sender (D-1077 flagged both). Here the sender must be within
	 * reach of the cabin <em>and have its screen open</em>; a target outside {@code 0..size-1}, a
	 * button type above 4, a property above 2, a label over {@link #MAX_LABEL} characters and an add
	 * past {@link #MAX_BUTTONS} are each dropped. The floor stays one signed byte, which is all the
	 * wire carries.
	 *
	 * <h2>Kept, because they are upstream's - and flagged</h2>
	 *
	 * <ul>
	 * <li><b>The indicator's "clear" never fires.</b> Mode 3, property 1 removes a floor's
	 *     special indicator when the text is {@code ""} - written upstream as {@code s == ""}, a
	 *     reference comparison that is false for a string read off the wire. The other half of
	 *     the condition, "equals the floor number", does work. So clearing the field stores an
	 *     empty indicator instead of removing the override. Kept as written: the comparison below
	 *     is deliberately {@code false}, not {@code isEmpty()}.</li>
	 * <li><b>The floor is one signed byte</b>, so a floor above 127 wraps negative. The screen
	 *     lets you type any integer.</li>
	 * </ul>
	 */
	public static class EvButtonModifyPacket implements LeafiaCustomPacketEncoder {

		public ElevatorEntity localEntity;
		/** 0: move, 1: add, 2: remove, 3: modify. */
		public int localMode = 0;
		public int localTarget = -1;
		public int localX = 0;
		public int localY = 0;
		/** 0: floor, 1: floor indicator, 2: button label. */
		public int localProperty = 0;
		public int localFloor = 1;
		public String localLabel = "ERROR";
		/** 0: Floor, 1: Fire, 2: Open, 3: Close, 4: Bell. */
		public int localButtonType = 0;

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(localEntity.getId());
			buf.insert(localMode, 2);
			if(localMode != 1)
				buf.writeInt(localTarget);
			if(localMode == 0) {
				buf.writeByte(localX);
				buf.writeByte(localY);
			} else if(localMode == 1)
				buf.insert(localButtonType, 3);
			else if(localMode == 3) {
				buf.insert(localProperty, 2);
				if(localProperty == 0)
					buf.writeByte(localFloor);
				else
					buf.writeUTF8String(localLabel);
			}
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {

			int entityId = buf.readInt();
			int mode = buf.extract(2);

			switch(mode) {
				case 0: {
					int target = buf.readInt();
					int x = buf.readByte();
					int y = buf.readByte();
					return ctx -> {
						ElevatorEntity entity = editable(ctx, entityId, "EvButtonModifyPacket");
						if(entity == null) return;
						if(target < 0 || target >= entity.buttons.size()) {
							PacketGuard.reject("EvButtonModifyPacket", ctx.player(), "move: no button " + target);
							return;
						}
						entity.buttons.get(target).x = x;
						entity.buttons.get(target).y = y;
						LeafiaCustomPacket.__start(new EvButtonSyncPacket(entity)).__sendToAll();
					};
				}
				case 1: {
					int type = buf.extract(3);
					return ctx -> {
						ElevatorEntity entity = editable(ctx, entityId, "EvButtonModifyPacket");
						if(entity == null) return;
						if(type > 4 || entity.buttons.size() >= MAX_BUTTONS) {
							PacketGuard.reject("EvButtonModifyPacket", ctx.player(), "add: type " + type
									+ " with " + entity.buttons.size() + " buttons");
							return;
						}
						switch(type) {
							case 0 -> entity.buttons.add(new FloorButton(entity, 1, "1", 0, 3));
							case 1 -> entity.buttons.add(new FireButton(entity, 0, 3));
							case 2 -> entity.buttons.add(new OpenButton(entity, 0, 3));
							case 3 -> entity.buttons.add(new CloseButton(entity, 0, 3));
							default -> entity.buttons.add(new BellButton(entity, 0, 3));
						}
						LeafiaCustomPacket.__start(new EvButtonSyncPacket(entity)).__sendToAll();
					};
				}
				case 2: {
					int target = buf.readInt();
					return ctx -> {
						ElevatorEntity entity = editable(ctx, entityId, "EvButtonModifyPacket");
						if(entity == null) return;
						if(target < 0 || target >= entity.buttons.size()) {
							PacketGuard.reject("EvButtonModifyPacket", ctx.player(), "remove: no button " + target);
							return;
						}
						entity.buttons.remove(target);
						LeafiaCustomPacket.__start(new EvButtonSyncPacket(entity)).__sendToAll();
					};
				}
				default: {
					int target = buf.readInt();
					int property = buf.extract(2);
					int floor = 0;
					String text = null;
					if(property == 0) floor = buf.readByte();
					else if(property == 1 || property == 2) text = buf.readUTF8String();
					int readFloor = floor;
					String readText = text;
					return ctx -> {
						ElevatorEntity entity = editable(ctx, entityId, "EvButtonModifyPacket");
						if(entity == null) return;
						if(target < 0 || target >= entity.buttons.size()) {
							PacketGuard.reject("EvButtonModifyPacket", ctx.player(), "modify: no button " + target);
							return;
						}
						if(property > 2 || (readText != null && readText.length() > MAX_LABEL)) {
							PacketGuard.reject("EvButtonModifyPacket", ctx.player(), "modify: property " + property
									+ (readText == null ? "" : ", text of " + readText.length() + " characters"));
							return;
						}
						if(entity.buttons.get(target) instanceof FloorButton btn) {
							switch(property) {
								case 0 -> {
									btn.floor = readFloor;
									btn.id = "floor" + readFloor;
								}
								case 1 -> {
									// Upstream: `s == "" || s.equals(...)`. The first half never
									// holds for a decoded string; see the class note.
									if(readText.equals(Integer.toString(btn.floor)))
										entity.specialDisplayFloors.remove(btn.floor);
									else
										entity.specialDisplayFloors.put(btn.floor, readText);
									LeafiaCustomPacket.__start(new EvSpecialFloorsSyncPacket(entity)).__sendToAll();
								}
								default -> btn.label = readText;
							}
							LeafiaCustomPacket.__start(new EvButtonSyncPacket(entity)).__sendToAll();
						}
					};
				}
			}
		}
	}
}
