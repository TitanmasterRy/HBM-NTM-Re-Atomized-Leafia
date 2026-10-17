package com.leafia.contents.building.doors.special.reactor_door;

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

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.generic.DoorDecl;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.IBlockMulti;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockConcreteColoredExt;
import com.hbm.blocks.generic.BlockConcreteColoredExt.EnumConcreteType;
import com.hbm.items.block.ItemBlockBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.building.doors.AddonDoorGeneric;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;
import com.leafia.passive.effects.LeafiaShakecam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The reactor door's block entity: the base mod's door with its own sounds, its own shakes at fixed
 * ticks, and two blocks to wear on its frame.
 *
 * <p>Upstream: {@code .../doors/special/reactor_door/ReactorDoorTE.java}. It overrides {@code
 * update()} outright rather than calling the base's, so the whole of the base's tick is written
 * out again here - {@link BlockEntityDoorGeneric#updateEntity} is private - with upstream's
 * additions in their places.
 *
 * <h2>The timeline, in ticks (the door takes {@code 86 + 23 = 109})</h2>
 *
 * <table>
 * <caption>Opening, then closing</caption>
 * <tr><td>opening, tick 0</td><td>the handle turns: {@code reactor_door_handle}, volume 1</td></tr>
 * <tr><td>opening, tick 43</td><td>the leaf starts to swing: {@code reactor_door_open}, volume 3, and a
 *     shake (0.15 intensity, 0.5 s, speed 2, range 12)</td></tr>
 * <tr><td>closing, tick {@code 109 - 23 = 86}</td><td>{@code reactor_door_close}, volume 3</td></tr>
 * <tr><td>closing, tick {@code 109 - 44 - 2 = 63}</td><td>a shake (0.35, 0.75 s, speed 8, range 12). Upstream's
 *     own comment on the {@code - 2}: "why is it off sync"</td></tr>
 * <tr><td>closing, tick {@code 109 - 43 - 23 = 43}</td><td>the handle turns back: {@code reactor_door_handle},
 *     volume 3</td></tr>
 * </table>
 *
 * Each is compared before the tick's own increment or decrement, as upstream does, so the tick
 * named is the value {@code openTicks} holds on the way in. The sounds and shakes are the server's;
 * the client counts {@code openTicks} too but a world's {@code playSound(null, ...)} on a client plays
 * nothing, so upstream's two sides did the same.
 *
 * <h2>Things upstream's override leaves out, and this puts back</h2>
 *
 * <ul>
 * <li><b>The redstone check.</b> Upstream's {@code update()} has none, because in CE the redstone
 *     handling is in {@code updateRedstonePower}. This tree's base door does it at the end of its
 *     tick - open on a signal, close when it goes - so a tick that skipped it would leave this door
 *     the one door in the mod that ignores a lever. The base's lines are copied.</li>
 * <li><b>The control event.</b> {@code broadcastControlEvt} goes to the Custom Control Panel and is back (CCP-RETROFIT,
 *     D-3069): {@code door_open_state} when the door finishes opening or closing, as upstream sent it.</li>
 * </ul>
 *
 * <h2>The skins</h2>
 *
 * Upstream holds a {@code Block} and a metadata int for each of the frame's two faces, defaulting to
 * {@code brick_concrete_dark} and the base mod's machine concrete. There is no metadata any more: a
 * block state carries the finish, so each is one {@link BlockState}. They ride the base door's sync
 * packet (upstream sent its own, {@code LeafiaPacket} keys 0-3, and again on every load) and are
 * saved as states.
 */
public class ReactorDoorTE extends BlockEntityDoorGeneric implements IControllable {

	public static final int SHAKE_PLAYER_RANGE = 10;

	/** The two faces' paint: A is the front (the side the door faces), B the back. */
	public BlockState skinA = defaultSkinA();
	public BlockState skinB = defaultSkinB();

	/** The state the radiation system was last told about; see {@link AddonDoorGeneric#tellRadiation}. */
	private byte rebuiltFor = -1;

	public ReactorDoorTE(BlockPos pos, BlockState state) {
		super(pos, state);
	}

	@Override
	public BlockEntityType<?> getType() {
		return LeafiaBlockEntities.REACTOR_DOOR.get();
	}

	/**
	 * Upstream's {@code AddonBlocks.brick_concrete_dark}. That block is the dark bricks feature's, so
	 * with it off the base mod's own brick concrete stands in - the texture upstream's renderer
	 * falls back to when it cannot read one.
	 */
	public static BlockState defaultSkinA() {
		if(LeafiaBlocks.brick_concrete_dark != null) return LeafiaBlocks.brick_concrete_dark.get().defaultBlockState();
		return ModBlocks.brick_concrete.get().defaultBlockState();
	}

	/** Upstream's {@code ModBlocks.concrete_colored_ext} at {@code EnumConcreteType.MACHINE}. */
	public static BlockState defaultSkinB() {
		return ModBlocks.concrete_colored_ext.get().defaultBlockState()
				.setValue(BlockConcreteColoredExt.VARIANT, EnumConcreteType.MACHINE);
	}

	/**
	 * What painting with this stack would use, or null when it is not a block item. Upstream's
	 * {@code block.getBlock()} and {@code block.getMetadata(stack)}: a variant item names its finish.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static BlockState paintOf(ItemStack stack) {

		if(!(stack.getItem() instanceof BlockItem item)) return null;

		Block block = item.getBlock();
		if(item instanceof ItemBlockBase.Variant variant && block instanceof IBlockMulti multi)
			return block.defaultBlockState().setValue(multi.getVariantProperty(), (Enum) variant.getVariant());

		return block.defaultBlockState();
	}

	// ---- the timeline ---------------------------------------------------------------------------

	/** One thing the door does at one tick: a sound at a volume, a shake, or both. */
	public record Cue(DeferredHolder<SoundEvent, SoundEvent> sound, float volume, String[] shake) { }

	/** Upstream's two shakes, as the strings {@code /hbmleaf shake} takes. */
	public static final String[] OPEN_SHAKE = {"type=smooth", "intensity=0.15", "duration=0.5", "speed=2",
			"blurDulling=50", "bloomDulling=50", "range=12", "curve=0.5"};
	public static final String[] CLOSE_SHAKE = {"type=smooth", "intensity=0.35", "duration=0.75", "speed=8",
			"blurDulling=50", "bloomDulling=50", "range=12", "curve=0.5"};

	/**
	 * What the door does on the tick it enters with {@code openTicks} ticks of travel, before the
	 * count moves. The table in the class note, in code - separate from the tick so that it can be read
	 * and tested without a world.
	 */
	public static List<Cue> cues(boolean opening, int openTicks, int timeToOpen) {

		List<Cue> out = new ArrayList<>(2);

		if(opening) {
			if(openTicks == 0)
				out.add(new Cue(LeafiaSounds.reactor_door_handle, 1F, null));
			if(openTicks == 43) {
				out.add(new Cue(null, 0F, OPEN_SHAKE));
				out.add(new Cue(LeafiaSounds.reactor_door_open, 3F, null));
			}
		} else {
			if(openTicks == timeToOpen - 23)
				out.add(new Cue(LeafiaSounds.reactor_door_close, 3F, null));
			if(openTicks == timeToOpen - 44 - 2) // why is it off sync
				out.add(new Cue(null, 0F, CLOSE_SHAKE));
			if(openTicks == timeToOpen - 43 - 23)
				out.add(new Cue(LeafiaSounds.reactor_door_handle, 3F, null));
		}

		return out;
	}

	// ---- the tick -------------------------------------------------------------------------------

	/** The server ticker the block hands out. */
	public static void tick(Level level, BlockPos pos, BlockState state, ReactorDoorTE door) {
		door.updateReactor(level, pos, state);
	}

	private void updateReactor(Level level, BlockPos pos, BlockState blockState) {

		DoorDecl type = getDoorType();
		if(type == null) {
			setRemoved();
			return;
		}

		if(type.onDoorUpdate() != null)
			type.onDoorUpdate().accept(this);

		ServerLevel server = level instanceof ServerLevel s ? s : null;

		if(server != null) {
			for(Cue cue : cues(state == STATE_OPENING, openTicks, type.timeToOpen())) {
				if(cue.shake() != null)
					LeafiaShakecam.sendNear(server, pos, SHAKE_PLAYER_RANGE, cue.shake());
				if(cue.sound() != null)
					server.playSound(null, pos, cue.sound().get(), SoundSource.BLOCKS, cue.volume(), 1);
			}
		}

		if(state == STATE_OPENING) {
			openTicks++;
			if(openTicks >= type.timeToOpen())
				openTicks = type.timeToOpen();

		} else if(state == STATE_CLOSING) {
			openTicks--;
			if(openTicks <= 0)
				openTicks = 0;
		}

		if(level.isClientSide) return;

		int[][] ranges = type.getDoorOpenRanges();
		Direction dir = blockState.getValue(BlockDummyable.DIR);

		if(state == STATE_OPENING) {

			for(int i = 0; i < ranges.length; i++) {

				int[] range = ranges[i];
				BlockPos startPos = new BlockPos(range[0], range[1], range[2]);
				float time = type.getDoorRangeOpenTime(openTicks, i);

				for(int j = 0; j < Math.abs(range[3]); j++) {

					if((float) j / (Math.abs(range[3] - 1)) > time) break;

					for(int k = 0; k < range[4]; k++) {
						BlockPos finalPos = rangeStep(startPos, range, j, k, dir, pos);

						// Upstream's, and unlike the base's: reaching the core's own cell turns its box on.
						if(finalPos.equals(pos))
							this.shouldUseBB = true;
						else if(blockState.getBlock() instanceof BlockDummyable dummy)
							dummy.makeExtra(level, finalPos);
					}
				}
			}

		} else if(state == STATE_CLOSING) {

			for(int i = 0; i < ranges.length; i++) {

				int[] range = ranges[i];
				BlockPos startPos = new BlockPos(range[0], range[1], range[2]);
				float time = type.getDoorRangeOpenTime(openTicks, i);

				for(int j = Math.abs(range[3]) - 1; j >= 0; j--) {

					if((float) j / (Math.abs(range[3] - 1)) < time) break;

					for(int k = 0; k < range[4]; k++) {
						BlockPos finalPos = rangeStep(startPos, range, j, k, dir, pos);

						if(finalPos.equals(pos))
							this.shouldUseBB = false;
						else if(blockState.getBlock() instanceof BlockDummyable dummy)
							dummy.removeExtra(level, finalPos);
					}
				}
			}
		}

		if(state == STATE_OPENING && openTicks == type.timeToOpen()) {
			state = STATE_OPEN;
			broadcastControlEvt();
		}

		if(state == STATE_CLOSING && openTicks == 0) {
			state = STATE_CLOSED;
			broadcastControlEvt();

			// With the door finally shut it is radiation resistant, and nothing has told the
			// system: opening it did, and a door in the middle of closing is still open.
			AddonDoorGeneric.markOccupiedSections(level, pos, blockState, type);
		}

		rebuiltFor = AddonDoorGeneric.tellRadiation(level, pos, this, rebuiltFor);

		this.networkPackNT(100);

		// The base door's own redstone lines, which upstream's override does not need (see the class note).
		if(redstonePower == -1 && state == STATE_OPEN) {
			tryToggle(-1);
		} else if(redstonePower > 0 && state == STATE_CLOSED) {
			tryToggle(-1);
		}
		if(redstonePower == -1) {
			redstonePower = 0;
		}
	}

	// ---- sync and save --------------------------------------------------------------------------

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeVarInt(Block.getId(skinA));
		buf.writeVarInt(Block.getId(skinB));
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		BlockState a = Block.stateById(buf.readVarInt());
		BlockState b = Block.stateById(buf.readVarInt());
		// Upstream refuses a name that is not a block; an id outside the registry is air here.
		if(!a.isAir()) skinA = a;
		if(!b.isAir()) skinB = b;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("skinA", NbtUtils.writeBlockState(skinA));
		tag.put("skinB", NbtUtils.writeBlockState(skinB));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		skinA = readSkin(tag, "skinA", registries, skinA);
		skinB = readSkin(tag, "skinB", registries, skinB);
	}

	private static BlockState readSkin(CompoundTag tag, String key, HolderLookup.Provider registries, BlockState fallback) {
		if(!tag.contains(key, Tag.TAG_COMPOUND)) return fallback;
		BlockState read = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(key));
		return read.isAir() ? fallback : read;
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("door_toggle");
	}

	@Override
	public List<String> getOutEvents() {
		return Collections.singletonList("door_open_state");
	}

	/** {@code door_toggle}, behind the door's pin exactly as the generic door's adapter has it (D-3068 #2). */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("door_toggle")) {
			DataValue passcode = e.vars.get("passcode");
			tryToggle(passcode == null ? -1 : (int) passcode.getNumber());
		}
	}

	/** Upstream's {@code broadcastControlEvt}: {@code door_open_state} with CE's ordinal (0 closed, 1 open, 2 closing, 3 opening). */
	private void broadcastControlEvt() {
		if(level != null && !level.isClientSide)
			ControlEventSystem.get(level).broadcastToSubscribed(this, ControlEvent.newEvent("door_open_state").setVar("state", (float) state));
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
