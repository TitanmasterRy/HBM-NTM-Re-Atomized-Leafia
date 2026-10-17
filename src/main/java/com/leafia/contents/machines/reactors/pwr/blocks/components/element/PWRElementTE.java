package com.leafia.contents.machines.reactors.pwr.blocks.components.element;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.custom_hbm.util.LCETuple.Pair;
import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_PWRModerator;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.leafia.contents.building.doors.AddonDoorGeneric;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.PWRReflectorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRChannelBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRConductorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.init.LeafiaSounds;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.group.LeafiaMap;
import com.llib.group.LeafiaSet;
import com.llib.math.range.RangeDouble;
import com.llib.math.range.RangeInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A PWR fuel column's block entity, on the top block of the column: the rod, and the neutron
 * bookkeeping that decides how much flux it receives from every other rod.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/element/PWRElementTE.java}
 * (976 lines). Per tick the rod's {@link LeafiaRodItem#HeatFunction} is driven by:
 *
 * <ul>
 * <li><b>Flux in</b>: every other rod in a straight line on the four sides, out to 19 blocks, at full
 * weight times this column's height, and the two diagonal-adjacent routes into each of the four corners at
 * half weight each. Each route's flux is the source rod's {@code getFlux} - blended toward its moderated
 * value by the fraction of the route's rows that pass graphite, a channel, a conductor or water - times the
 * fraction of the column's height the route is open over ({@link HeatRetrival#getControlMin}): shielding
 * blocks carve rows out, and control rods close the rows they cover at their current depth. A reflector in
 * a route counts this column's own flux back over the reflector's rows. A coolant with
 * {@code FT_PWRModerator} multiplies it all.</li>
 * <li><b>Cooling</b>: the channels near it ({@code channelScale}, from the diagnosis) times
 * {@code (coolant / capacity)^0.4}, toward the hot coolant's temperature, at {@code 400 x exchangerScale}.</li>
 * </ul>
 *
 * <p>What the rod cools by is spent as coolant ({@link PWRData#spendCoolant}); it emits chunk radiation
 * {@code flux^0.65 / 2} (capped, {@code ChunkRadiationManager.incrementRad}); a rod at {@code nuke} explodes the reactor (level 1)
 * and nukes; a rod spilling melts down - with a gaseous hot coolant the vessel blows (by toughness),
 * otherwise corium runs out under the column.
 *
 * <h2>Upstream behaviours kept, each a question for the owner rather than a fix (D-3006)</h2>
 *
 * <ul>
 * <li><b>{@link HeatRetrival#divisor} is never read.</b> It is {@code 2^(distance/2 - 1)} for a straight
 * route and 2 for a corner, and the cannery's guide says that is how flux falls off; the tick weighs every
 * straight route at {@code height} and every corner at {@code height/2} regardless.</li>
 * <li><b>The gaseous meltdown places corium {@code height} times at the same block</b>
 * ({@code pos.down(height)}), not down the column.</li>
 * <li>The packet after the meltdown branches reads the rod's data unguarded upstream; here a rod with no
 * data sends nothing rather than crash.</li>
 * </ul>
 *
 * <h2>What changed</h2>
 *
 * <ul>
 * <li><b>The stacked column.</b> Upstream keeps the old block entity across a {@code stacked} change and
 * moves the rod up in {@code connectUpper}; here the block swaps entity types explicitly
 * ({@code PWRElementBlock.updateState}) after {@link #connectUpper} has moved the rod to the new top, or
 * dropped it if the top is full. The rod lives only in the top block's entity, as upstream intends.</li>
 * <li>The inventory is NeoForge's {@link ItemStackHandler}, as upstream's, saved as {@code inventory};
 * the hopper face is a capability on this and on the proxy ({@code LeafiaCapabilities}).</li>
 * <li>The CE control panel's {@code IControllable} is stripped and its three values kept
 * ({@link #queryValues}, D-1093 #8). The debug {@code Tracker} calls are not carried.</li>
 * <li>{@code listeners} is a plain identity map; upstream's {@code LeafiaMap} compares block entities by
 * {@code equals}, which for a block entity is identity.</li>
 * </ul>
 */
public class PWRElementTE extends BlockEntityLoadedBase implements PWRComponentEntity, LeafiaPacketReceiver, IControllable {

	/** Every column's traced positions: a block change at one makes that column re-trace (PWRTicks). */
	public static final Map<PWRElementTE, LeafiaSet<BlockPos>> listeners = new HashMap<>();
	BlockPos corePos = null;
	PWRData data = null;
	int height = 1;
	LCEAudioWrapper sound = null;
	private boolean unloading = false;

	public final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			setChanged();
		}
	};

	public PWRElementTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Upstream's {@code isRadResistant(world, pos)} test for the trace: implements the interface (CE's default answers true). */
	public static boolean stopsNeutrons(BlockState state) {
		return state.getBlock() instanceof IRadResistantBlock;
	}

	/**
	 * The same test where the answer depends on the world: the addon's doors override CE's
	 * {@code isRadResistant(world, pos)} with "the flag, and the door is shut" (D-3005), so an open
	 * reactor door lets the neutrons through. Every other shielding block answers by its kind.
	 */
	public static boolean stopsNeutrons(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if(state.getBlock() instanceof AddonDoorGeneric door) return door.isRadResistant(level, pos);
		return stopsNeutrons(state);
	}

	@Override
	public boolean canAssignCore() {
		return true;
	}

	LeafiaSet<BlockPos> listenPositions() {
		if(this.isRemoved()) return new LeafiaSet<>(); // dummy set
		return listeners.computeIfAbsent(this, k -> new LeafiaSet<>());
	}

	@Override
	public void onDiagnosis() {
		height = getHeight();
		updateObstacleMappings();
	}

	public void updateObstacleMappings() {
		listenPositions().clear();
		updateCornerMap();
		updateLinearMap();
	}

	public int getHeight() {
		int height = 1;
		for(BlockPos p = worldPosition.below(); level.isInWorldBounds(p); p = p.below()) {
			if(level.getBlockState(p).getBlock() instanceof PWRElementBlock) height++;
			else break;
		}
		return height;
	}

	/** The height the last diagnosis measured, which the neutron maps are built for. */
	public int mappedHeight() {
		return height;
	}

	static abstract class MapConsumer {
		int i = 0;

		abstract HeatRetrival accept(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> areas);

		public MapConsumer() {}

		public MapConsumer(int i) {
			this.i = i;
		}
	}

	public final Set<HeatRetrival> linearFuelMap = new HashSet<>();
	public final Set<Pair<HeatRetrival, HeatRetrival>> cornerFuelMap = new HashSet<>();

	void updateCornerMap() {
		cornerFuelMap.clear();
		RangeInt range = new RangeInt(0, height - 1);
		for(int bin = 0; bin <= 0b11; bin++) {
			Pair<HeatRetrival, HeatRetrival> pair = new Pair<>(null, null);
			Pair<HeatRetrival, HeatRetrival> pairRef = new Pair<>(null, null);
			for(int bin2 = 0b01; bin2 <= 0b10; bin2++) {
				final Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls = new HashMap<>();
				List<RangeInt> areas = new ArrayList<>();
				areas.add(range.clone());
				Set<Integer> moderatedRows = new HashSet<>();
				int xMul = bin2 >> 1;
				int zMul = bin2 & 1;
				linetraceNeutrons(worldPosition.offset((((bin >> 1) & 1) * 2 - 1) * xMul, 0, ((bin & 1) * 2 - 1) * zMul), areas, controls, moderatedRows, new MapConsumer() {
					@Override
					HeatRetrival accept(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> areas) {
						HeatRetrival retrival = new HeatRetrival(fuelPos, controls, areas);
						retrival.entity = PWRElementTE.this;
						if(pairRef.getA() == null) pairRef.setA(retrival);
						else {
							pairRef.setB(retrival);
							cornerFuelMap.add(pairRef);
						}
						return retrival;
					}
				});
				searchFuelAndAdd(worldPosition.offset(((bin >> 1) & 1) * 2 - 1, 0, (bin & 1) * 2 - 1), areas, controls, moderatedRows, new MapConsumer() {
					@Override
					HeatRetrival accept(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> areas) {
						HeatRetrival retrival = new HeatRetrival(fuelPos, controls, areas);
						retrival.entity = PWRElementTE.this;
						if(pair.getA() == null) pair.setA(retrival);
						else {
							pair.setB(retrival);
							cornerFuelMap.add(pair);
						}
						return retrival;
					}
				});
			}
		}
	}

	void updateLinearMap() {
		linearFuelMap.clear();
		RangeInt range = new RangeInt(0, height - 1);
		for(Direction facing : Direction.Plane.HORIZONTAL) {
			final LeafiaMap<BlockPos, Pair<RangeDouble, RangeDouble>> controls = new LeafiaMap<>();
			List<RangeInt> areas = new ArrayList<>();
			areas.add(range.clone());
			Set<Integer> moderatedRows = new HashSet<>();
			for(int i = 1; (areas.size() > 0) && (i < 20); i++) {
				BlockPos basePos = worldPosition.offset(facing.getStepX() * i, 0, facing.getStepZ() * i);
				if(!level.isInWorldBounds(basePos)) break;
				linetraceNeutrons(basePos, areas, controls, moderatedRows, new MapConsumer(i) {
					@Override
					HeatRetrival accept(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> areas) {
						HeatRetrival retrival = new HeatRetrival(fuelPos, controls, areas, this.i);
						retrival.entity = PWRElementTE.this;
						linearFuelMap.add(retrival);
						return retrival;
					}
				});
				searchFuelAndAdd(basePos, new ArrayList<>(areas), controls.clone(), moderatedRows, new MapConsumer(i) {
					@Override
					HeatRetrival accept(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> areas) {
						HeatRetrival retrival = new HeatRetrival(fuelPos, controls, areas, this.i);
						retrival.entity = PWRElementTE.this;
						linearFuelMap.add(retrival);
						return retrival;
					}
				});
			}
		}
	}

	static Set<RangeDouble> intersectRanges(Set<RangeDouble> a, Set<RangeDouble> b) {
		Set<RangeDouble> intersection = new HashSet<>();
		for(RangeDouble rangeA : a) {
			for(RangeDouble rangeB : b) {
				RangeDouble range = new RangeDouble(Math.max(rangeA.min, rangeB.min), Math.min(rangeA.max, rangeB.max));
				if(range.min <= range.max) intersection.add(range);
			}
		}
		return intersection;
	}

	void searchFuelAndAdd(BlockPos basePos, List<RangeInt> areas, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<Integer> moderatedRows, MapConsumer callback) {
		Set<RangeDouble> scaledAreas = new HashSet<>();
		for(RangeInt area : areas) {
			/* Add 1 basically because:
			For example we had 3 blocks,
			With integers, the range would be 1 <= x <= 3
			But with doubles, the range should be 1 <= x < 4 because 3.99 is also within block 3's area
			 */
			scaledAreas.add(new RangeDouble(area.min / (double) height, (area.max + 1) / (double) height));
		}
		List<Integer> blocked = new ArrayList<>();
		for(RangeInt area : areas) {
			for(Integer depth : area) {
				int curDepth = depth;
				while(!blocked.contains(curDepth) && level.isInWorldBounds(basePos.below(curDepth))) {
					blocked.add(curDepth);
					Block block = level.getBlockState(basePos.below(curDepth)).getBlock();
					if(block instanceof PWRElementBlock element) {
						if(element.tileEntityShouldCreate(level, basePos.below(curDepth))) {
							int bottomDepth = depth;
							while(level.isInWorldBounds(basePos.below(bottomDepth + 1))) {
								if(level.getBlockState(basePos.below(bottomDepth + 1)).getBlock() instanceof PWRElementBlock) {
									bottomDepth++;
									blocked.add(bottomDepth);
								} else
									break;
							}
							int elementHeight = bottomDepth - curDepth + 1;
							int moderation = 0;
							for(int depthMod = curDepth; depthMod <= bottomDepth; depthMod++) {
								if(moderatedRows.contains(depthMod)) moderation++;
							}
							Set<RangeDouble> myArea = new HashSet<>();
							myArea.add(new RangeDouble(curDepth / (double) height, (bottomDepth + 1) / (double) height));
							callback.accept(basePos.below(curDepth), controls, intersectRanges(scaledAreas, myArea)).moderation = moderation / (double) elementHeight;
							break;
						}
					} else
						break;
					curDepth--;
				}
			}
		}
	}

	/** Whether a block's registry path has "graphite" as a word: upstream's moderator test. */
	static boolean isGraphite(Block block) {
		ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
		return ("_" + key.getPath() + "_").matches(".*[^a-z]graphite[^a-z].*");
	}

	void linetraceNeutrons(BlockPos basePos, List<RangeInt> areas, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<Integer> moderatedRows, MapConsumer reflectionCall) {
		RangeInt range = new RangeInt(0, height - 1);
		Set<RangeDouble> reflectAreas = new HashSet<>();
		for(RangeInt area : areas)
			reflectAreas.add(new RangeDouble(area.min / (double) height, (area.max + 1) / (double) height));
		boolean doReflect = false;
		boolean[] reflectors = new boolean[height];
		/* carving out neutron rays */ {
			for(Integer depth : range) {
				BlockPos searchPos = basePos.below(depth);

				RangeInt subject = null;
				for(RangeInt area : areas) {
					if(area.isInRange(depth)) {
						subject = area;
						break;
					}
				}
				if(subject == null) continue;
				listenPositions().add(searchPos);

				BlockState state = level.getBlockState(searchPos);
				Block block = state.getBlock();
				if(level.isInWorldBounds(searchPos)) {
					if(isGraphite(block) || block instanceof PWRChannelBlock || block instanceof PWRConductorBlock || block == Blocks.WATER)
						moderatedRows.add(depth);
				}
				if(block instanceof PWRReflectorBlock) {
					reflectors[depth] = true;
					doReflect = true;
				}

				if(!stopsNeutrons(level, searchPos)) continue;

				areas.remove(subject);
				int condition = ((subject.min == depth) ? 0b10 : 0) + ((subject.max == depth) ? 0b01 : 0);
				switch(condition) {
					case 0b00:
						areas.add(new RangeInt(subject.min, depth - 1));
						areas.add(new RangeInt(depth + 1, subject.max));
						break;
					case 0b01:
						subject.max--;
						areas.add(subject);
						break;
					case 0b10:
						subject.min++;
						areas.add(subject);
						break;
					case 0b11:
						break;
				}
			}
		}

		/* handle reflectors */ if(doReflect) {
			// this runs before detecting rods but those rods that's ignored are just the rods in the same column so we're fine with that
			LeafiaSet<RangeDouble> ranges = new LeafiaSet<>();
			int moderation = 0;
			boolean nextCreate = true;
			for(Integer depth : range) {
				if(!reflectors[depth])
					nextCreate = true;
				else {
					if(nextCreate) {
						ranges.add(0, new RangeDouble(depth / (double) height, (depth + 1d) / height));
						nextCreate = false;
					}
					ranges.get(0).max = (depth + 1d) / height;
					if(moderatedRows.contains(depth)) moderation++;
				}
			}
			if(ranges.size() > 0)
				reflectionCall.accept(worldPosition, controls, intersectRanges(reflectAreas, ranges)).moderation = moderation / (double) height;
		}

		/* detect rods */ {
			Integer rodTop = null;
			if(level.getBlockState(basePos).getBlock() instanceof PWRControlBlock) {
				rodTop = 0;
				for(BlockPos searchPos = basePos.above(); level.isInWorldBounds(searchPos); searchPos = searchPos.above()) {
					if(level.getBlockState(searchPos).getBlock() instanceof PWRControlBlock) rodTop -= 1;
					else break;
				}
			}
			int depth = 0;
			for(BlockPos searchPos = basePos; (level.isInWorldBounds(searchPos) || (rodTop != null)); searchPos = searchPos.below()) {
				boolean isControl = level.isInWorldBounds(searchPos);
				if(isControl) isControl = level.getBlockState(searchPos).getBlock() instanceof PWRControlBlock;
				if(rodTop == null) {
					if(isControl) rodTop = depth;
				} else {
					if(!isControl) {
						int rodBottom = depth - 1;
						double rodHeight = rodBottom - rodTop + 1;
						if(rodBottom < height + rodHeight) {
							double topStart = rodTop / rodHeight;
							double bottomStart = (rodBottom + 1 - height) / rodHeight;
							RangeDouble rangeBottom = new RangeDouble(bottomStart, bottomStart + height / rodHeight);
							RangeDouble rangeTop = new RangeDouble(topStart, topStart - height / rodHeight);
							controls.put(new BlockPos(basePos.getX(), basePos.getY() - rodTop, basePos.getZ()), new Pair<>(rangeBottom, rangeTop));
						}
						rodTop = null;
					}
				}
				depth++;
			}
		}
	}

	public static class HeatRetrival {
		public final Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls;
		public final BlockPos fuelPos;
		/** Computed and never read: see the class doc. */
		public final double divisor;
		public final Set<RangeDouble> areas;
		public double moderation;
		PWRElementTE entity;

		public HeatRetrival(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> copiedAreas, int distance) {
			this.fuelPos = fuelPos;
			this.divisor = Math.pow(2, distance / 2d - 1);
			this.areas = copiedAreas;
			this.controls = copyControlMap(controls);
		}

		public HeatRetrival(BlockPos fuelPos, Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls, Set<RangeDouble> copiedAreas) {
			this.fuelPos = fuelPos;
			this.divisor = 2;
			this.areas = copiedAreas;
			this.controls = copyControlMap(controls);
		}

		Map<BlockPos, Pair<RangeDouble, RangeDouble>> copyControlMap(Map<BlockPos, Pair<RangeDouble, RangeDouble>> controls) {
			Map<BlockPos, Pair<RangeDouble, RangeDouble>> copiedControls = new LeafiaMap<>();
			for(Entry<BlockPos, Pair<RangeDouble, RangeDouble>> entry : controls.entrySet())
				copiedControls.put(entry.getKey(), new Pair<>(entry.getValue().getA().clone(), entry.getValue().getB().clone()));
			return copiedControls;
		}

		/**
		 * The fraction of the column's height this route is open over: the route's areas, intersected with
		 * the open part of every control rod in it at its current depth. A rod that covers the whole route
		 * closes it (0).
		 */
		public double getControlMin(Level world) {
			Set<RangeDouble> intersection = new LeafiaSet<>();
			for(RangeDouble area : areas) intersection.add(area.clone());
			for(Entry<BlockPos, Pair<RangeDouble, RangeDouble>> entry : controls.entrySet()) {
				double rodPos = getControl(world, entry.getKey());
				double normalizedPosB = Math.max(entry.getValue().getA().ratio(rodPos), 0);
				double normalizedPosT = Math.max(entry.getValue().getB().ratio(rodPos), 0);

				Set<RangeDouble> openAreas = new LeafiaSet<>();
				if(normalizedPosT > 0) openAreas.add(new RangeDouble(0, normalizedPosT));
				if(normalizedPosB > 0) openAreas.add(new RangeDouble(1 - normalizedPosB, 1));
				if(openAreas.size() <= 0) return 0;
				else intersection = intersectRanges(intersection, openAreas);
			}
			double control = 0;
			for(RangeDouble range : intersection) control += Math.abs(range.max - range.min);
			return control;
		}

		public double getControlAvg(Level world) {
			throw new LeafiaDevFlaw("getControlAvg is scrapped");
		}

		public double getControl(Level world, BlockPos pos) {
			if(world.getBlockEntity(pos) instanceof PWRControlTE control) return control.position;
			BlockState state = world.getBlockState(pos);
			if(state.getBlock() instanceof IRadResistantBlock) return stopsNeutrons(world, pos) ? 0 : 1;
			return 1;
		}
	}

	public double getHeatFromHeatRetrival(HeatRetrival retrival, LeafiaRodItem rod) {
		BlockPos pos = retrival.fuelPos;
		if(level.getBlockState(pos).getBlock() instanceof PWRElementBlock element && element.tileEntityShouldCreate(level, pos)) {
			if(level.getBlockEntity(pos) instanceof PWRElementTE source) {
				ItemStack stack = source.inventory.getStackInSlot(0);
				double value = rod.getFlux(stack) * (1 - retrival.moderation) + rod.getFlux(stack, true) * retrival.moderation;
				if(data != null) {
					FluidType type = Fluids.fromID(data.coolantId);
					if(type.hasTrait(FT_PWRModerator.class)) value *= type.getTrait(FT_PWRModerator.class).getMultiplier();
				}
				return value;
			}
		}
		return 0;
	}

	/**
	 * Server: a column that has just had another element placed on it hands its rod to the new top if
	 * the top is empty, and drops it otherwise. The block then makes this block entity a proxy.
	 */
	public void connectUpper() {
		if(this.isRemoved() || level == null || !level.isLoaded(worldPosition) || level.isClientSide) return;
		BlockPos upPos = worldPosition.above();
		boolean mustTransmit = false;
		PWRElementTE target = null;
		while(level.isInWorldBounds(upPos)) {
			if(level.getBlockState(upPos).getBlock() instanceof PWRElementBlock) {
				mustTransmit = true;
				target = null;
				if(level.getBlockEntity(upPos) instanceof PWRElementTE above && !above.isRemoved()) {
					target = above;
					if(!target.inventory.getStackInSlot(0).isEmpty()) target = null;
				}
			} else
				break;
			upPos = upPos.above();
		}
		if(mustTransmit) {
			ItemStack stack = inventory.getStackInSlot(0);
			if(target != null) {
				target.inventory.setStackInSlot(0, stack);
				this.inventory.setStackInSlot(0, ItemStack.EMPTY);
			} else if(!stack.isEmpty()) {
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
				this.inventory.setStackInSlot(0, ItemStack.EMPTY);
			}
		}
	}

	@Override
	public void setCoreLink(@Nullable BlockPos pos) {
		corePos = pos;
	}

	@Nullable
	public BlockPos getCoreLink() {
		return corePos;
	}

	@Override
	public PWRData getCoreByCorePos() {
		return PWRComponentEntity.getCoreFromPos(level, corePos);
	}

	@Override
	public void assignCore(@Nullable PWRData data) {
		if(this.data != data && level != null)
			PWRData.addDataToPacket(LeafiaPacket._start(this), data).__sendToAffectedClients();
		this.data = data;
	}

	@Override
	public PWRData getCore() {
		return data;
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(compound.contains("corePosX"))
			corePos = new BlockPos(compound.getInt("corePosX"), compound.getInt("corePosY"), compound.getInt("corePosZ"));
		super.loadAdditional(compound, registries);
		if(compound.contains("inventory")) inventory.deserializeNBT(registries, compound.getCompound("inventory"));
		if(compound.contains("data")) {
			data = new PWRData(this);
			data.readFromNBT(compound, registries);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		if(corePos != null) {
			compound.putInt("corePosX", corePos.getX());
			compound.putInt("corePosY", corePos.getY());
			compound.putInt("corePosZ", corePos.getZ());
		}
		if(data != null) data.writeToNBT(compound, registries);
		compound.put("inventory", inventory.serializeNBT(registries));
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if(level != null && !level.isClientSide) syncLocals();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		unloading = true;
		stopSound();
		super.onChunkUnloaded();
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		listeners.remove(this);
		stopSound();
		super.setRemoved();
		if(!unloading && this.data != null) this.data.invalidate(level);
	}

	private void stopSound() {
		if(sound != null) sound.stopSound();
		sound = null;
	}

	@Nullable
	PWRData gatherData() {
		return this.getCoreByCorePos();
	}

	public double getHeat() {
		ItemStack stack = this.inventory.getStackInSlot(0);
		if(stack.getItem() instanceof LeafiaRodItem) {
			CompoundTag tag = LeafiaRodItem.data(stack);
			if(tag != null) return tag.getDouble("heat");
		}
		return 20;
	}

	public void setHeat(double heat) {
		ItemStack stack = this.inventory.getStackInSlot(0);
		if(stack.getItem() instanceof LeafiaRodItem) {
			CompoundTag tag = LeafiaRodItem.data(stack);
			if(tag != null) {
				tag.putDouble("heat", heat);
				LeafiaRodItem.putData(stack, tag);
			}
		}
	}

	public float channelScale = 0;
	public float exchangerScale = 1;

	public static Block getCorium(LeafiaRodItem rod) {
		return rod.corium();
	}

	/** The flux this column receives this tick: what the tick passes to {@code HeatFunction} as {@code x}. */
	public double heatDetection(LeafiaRodItem rod, int height) {
		double heatDetection = 0;
		for(Pair<HeatRetrival, HeatRetrival> pair : cornerFuelMap) {
			// getControlAvg is scrapped
			heatDetection += getHeatFromHeatRetrival(pair.getA(), rod) * pair.getA().getControlMin(level) * height / 2;
			heatDetection += getHeatFromHeatRetrival(pair.getB(), rod) * pair.getB().getControlMin(level) * height / 2;
		}
		for(HeatRetrival retrival : linearFuelMap)
			heatDetection += getHeatFromHeatRetrival(retrival, rod) * retrival.getControlMin(level) * height;
		return heatDetection;
	}

	public void tick() {
		if(this.data != null) this.data.update();
		if(level.isClientSide) {
			ItemStack stack = this.inventory.getStackInSlot(0);
			boolean play = false;
			if(stack.getItem() instanceof LeafiaRodItem) {
				CompoundTag nbt = LeafiaRodItem.data(stack);
				if(nbt != null && nbt.getDouble("incoming") > 0) play = true;
			}
			if(play && sound == null) {
				sound = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.pwrElement.get(), null, null, SoundSource.BLOCKS,
						worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, 0.0175f, 1);
				sound.startSound();
			} else if(!play && sound != null) {
				stopSound();
			}
			return;
		}
		ItemStack stack = this.inventory.getStackInSlot(0);
		if(stack.isEmpty() || !(stack.getItem() instanceof LeafiaRodItem rod)) return;
		int height = getHeight();
		double coolin = 0;
		PWRData gathered = gatherData();
		double coolantTemp = 400;
		double required = 0;
		if(gathered != null) {
			coolin = Math.pow(gathered.tanks[0].getFill() / (double) Math.max(gathered.tanks[0].getMaxFill(), 1), 0.4);
			coolantTemp = gathered.tankTypes[1].temperature;
			required = 1 / gathered.multiplier / (PWRData.transferMultiplier / gathered.multiplier);
		}
		double heatDetection = heatDetection(rod, height);

		rod.HeatFunction(stack, true, heatDetection, channelScale * coolin, coolantTemp, 400 * exchangerScale, required, Math.pow(height, 0.25));
		double rad = Math.pow(heatDetection, 0.65) / 2;
		ChunkRadiationManager.incrementRad(level, worldPosition, (float) rad / 8, (float) rad);

		rod.decay(stack, inventory, 0);
		CompoundTag data = LeafiaRodItem.data(stack);
		double cooled = 0;
		if(data != null) {
			if(data.getBoolean("nuke")) {
				if(gathered != null) gathered.explode(level, stack, rod, 1);
				inventory.setStackInSlot(0, ItemStack.EMPTY);
				return;
			} else if(data.getInt("spillage") > 100) {
				if(gathered != null && gathered.tankTypes[1].hasTrait(FT_Gaseous.class)) {
					for(int i = 0; i < height; i++)
						level.setBlockAndUpdate(worldPosition.below(height), rod.corium().defaultBlockState());
					gathered.explode(level, stack, null, 0);
				} else {
					BlockPos pos = this.worldPosition.below(height);
					int tries = height;
					while(tries >= 0) {
						tries--;
						Block there = level.getBlockState(pos).getBlock();
						if(there == ModBlocks.block_corium.get() || there == ModBlocks.block_corium_cobble.get()) {
							pos = pos.above();
							continue;
						} else if(there == rod.corium()) {
							// prevent the coriums being stuck
							if(level.getBlockState(pos.below()).isSolid()) level.removeBlock(pos.below(), false);
							break;
						} else {
							level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
							level.setBlockAndUpdate(pos, rod.corium().defaultBlockState());
							level.setBlockAndUpdate(pos, rod.corium().defaultBlockState());
						}
					}
				}
			}
			cooled = data.getDouble("cooled");
		}
		if(cooled > 0 && gathered != null) gathered.spendCoolant(cooled, stack);
		CompoundTag nbt = LeafiaRodItem.data(stack);
		if(nbt == null) return;
		LeafiaPacket.Builder packet = LeafiaPacket._start(this);
		if(nbt.contains("heat")) packet.__write(1, nbt.getDouble("heat"));
		if(nbt.contains("depletion")) packet.__write(2, nbt.getDouble("depletion"));
		if(nbt.contains("incoming")) packet.__write(3, nbt.getDouble("incoming"));
		if(nbt.contains("melting")) packet.__write(4, nbt.getBoolean("melting"));
		packet.__sendToAffectedClients();
	}

	@Override
	public String getPacketIdentifier() {
		return "PWR_FUEL";
	}

	public LeafiaPacket.Builder generateSyncPacket() {
		CompoundTag nbt = saveWithoutMetadata(level.registryAccess());
		nbt.remove("data");
		return LeafiaPacket._start(this).__write(0, nbt);
	}

	public void syncLocals() {
		generateSyncPacket().__sendToAffectedClients();
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0:
				if(value instanceof CompoundTag nbt) {
					nbt.putBoolean("_isSyncSignal", true);
					loadAdditional(nbt, level.registryAccess());
				}
				break;
			case 1:
			case 2:
			case 3: {
				String[] doubleArray = new String[] {"heat", "depletion", "incoming"};
				if(value instanceof Double d) {
					ItemStack stack = inventory.getStackInSlot(0);
					if(stack.getItem() instanceof LeafiaRodItem) {
						CompoundTag nbt = LeafiaRodItem.data(stack);
						if(nbt == null) nbt = new CompoundTag();
						nbt.putDouble(doubleArray[key - 1], d);
						LeafiaRodItem.putData(stack, nbt);
					}
				}
				break;
			}
			case 4:
				if(value instanceof Boolean b) {
					ItemStack stack = inventory.getStackInSlot(0);
					if(stack.getItem() instanceof LeafiaRodItem) {
						CompoundTag nbt = LeafiaRodItem.data(stack);
						if(nbt == null) nbt = new CompoundTag();
						nbt.putBoolean("melting", b);
						LeafiaRodItem.putData(stack, nbt);
					}
				}
				break;
			case 31:
				data = PWRData.tryLoadFromPacket(this, value);
				break;
		}
		if(this.data != null) this.data.onReceivePacketLocal(key, value);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(this.data != null) this.data.onReceivePacketServer(key, value, plr);
	}

	@Override
	public void onPlayerValidate(Player plr) {
		LeafiaPacket.Builder packet = generateSyncPacket();
		if(this.data != null) PWRData.addDataToPacket(packet, this.data);
		packet.__sendToClient(plr);
	}

	/** The control panel's query values (temperature, meltingPoint, depletion in percent), by upstream's names. */
	public Map<String, Float> queryValues() {
		Map<String, Float> map = new HashMap<>();
		map.put("temperature", 20F);
		map.put("meltingPoint", 0F);
		map.put("depletion", 100F);
		ItemStack stack = inventory.getStackInSlot(0);
		if(stack.getItem() instanceof LeafiaRodItem rod) {
			CompoundTag tag = LeafiaRodItem.data(stack);
			if(tag != null) {
				map.put("temperature", (float) tag.getDouble("heat"));
				map.put("meltingPoint", (float) rod.meltingPoint);
				if(rod.life > 0) map.put("depletion", (float) (tag.getDouble("depletion") * 100 / rod.life));
			}
		}
		return map;
	}

	/** For the renderer and the look overlay: the routes this column reads flux from. */
	public Collection<HeatRetrival> linearRoutes() {
		return linearFuelMap;
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's three query keys, from {@link #queryValues()}. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.floats(queryValues());
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}
}
