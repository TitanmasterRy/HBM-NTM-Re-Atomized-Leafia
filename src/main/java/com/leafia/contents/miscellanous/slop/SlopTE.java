package com.leafia.contents.miscellanous.slop;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.BlockMachineBase;
import com.hbm.items.ModItems;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.miscellanous.slop.container.SlopMenu;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import api.hbm.energymk2.IEnergyProviderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * The slop reactor ("Average 'Nuclear Reactor' in Modern Mods"): a 5x5x5 glass cube you build,
 * fill with layers of toxic block from uranium ingots, and keep from melting down with snowballs.
 *
 * <p>Upstream: {@code .../miscellanous/slop/SlopTE.java}. Every number is upstream's and each is
 * read off the field that holds it: {@link #generation} is {@code 50000/20}, {@link #maxPower}
 * thirty seconds of it, {@link #maxDamage} thirty seconds of ticks, {@link #maxPartialConsumption}
 * {@code 20*50*9/3}, {@link #maxCool} five seconds.
 *
 * <h2>The structure</h2>
 *
 * The block is the middle of the <b>bottom edge of the front face</b>. {@code forward} is the
 * opposite of the block's facing, the cube's centre is two ahead and two up, and the 5x5x5 around
 * it is read in world axes: a cell with <em>two or more</em> coordinates at +-2 is frame (casing),
 * any other cell on the surface is glass, and the 3x3x3 inside may hold only air or toxic block.
 * The reactor's own cell is skipped. Nothing in this is rotated, because the cube is symmetric.
 *
 * <h2>The fuel</h2>
 *
 * The core is three layers of 3x3. A layer is built from {@code 9 - fuel%9} uranium ingots in
 * slot 0 the moment they are there ({@code fuel < 27}), and burning removes the <em>top</em> layer
 * after {@code 3000 * (fuel%9 or 9) / 9} ticks. Only a toxic block with {@code level == 0} counts
 * as fuel - upstream's toxic block is a fluid source - and on this port, where the base mod's
 * {@code toxic_block} is still a plain cube with no {@code level} property at all, every toxic
 * block is a source. {@link #isValidFuel} says so in one place, so the day the base mod makes it
 * a {@link LiquidBlock} the same line starts reading its level.
 *
 * <h2>The melt</h2>
 *
 * While on, with no snowball cooling it, {@link #damage} rises one a tick; past {@link #maxDamage}
 * (thirty seconds) {@link #meltdown} clears a 17x17x17 cube around the core to air and kills
 * everything in it. <b>That includes bedrock and obsidian</b>, and the casing and glass it is
 * built of, and it is the same upstream ({@code setBlockToAir} ignores hardness). Note also that
 * damage rises whether or not there is any fuel: turning an empty reactor on without a snowball
 * melts it just the same (D-1082).
 *
 * <h2>What does not port, and why</h2>
 *
 * <ul>
 * <li><b>{@code LCETileEntityMachineBase}</b> is 1.12.2 {@code ItemStackHandler} plumbing; the
 *     base machine inventory does all of it (D-1054). The two quirks it carried are kept:
 *     both slots accept anything ({@code isItemValidForSlot} is {@code true}), and slot 0 is
 *     reachable only from below, slot 1 from every other side.</li>
 * <li><b>The tracker box</b> upstream shows over each wrong block when a player clicks an
 *     unfinished reactor is {@code LeafiaDebug}'s, which is not ported. A burst of red dust at each
 *     wrong block says the same thing to the same players, so that is used instead.</li>
 * <li><b>{@code DelayedTick}</b> is a "run at the end of this world tick" queue used for one thing,
 *     the force-kill below; {@link Delayed} is that, in eight lines.</li>
 * </ul>
 */
public class SlopTE extends BlockEntityMachineBase implements LeafiaPacketReceiver, IEnergyProviderMK2 {

	public static final int SLOTS = 2;

	/** Uranium ingots, from below. */
	public static final int SLOT_FUEL = 0;

	/** Snowballs, from every other side. */
	public static final int SLOT_COOLANT = 1;

	/** Upstream: {@code 20*30}. Thirty seconds without cooling. */
	public static int maxDamage = 20 * 30;

	/** Upstream: {@code 20*50*9/3}. Ticks one full layer of fuel lasts. */
	public static int maxPartialConsumption = 20 * 50 * 9 / 3;

	/** Upstream: {@code 50000/20}. HE per tick while on with fuel. */
	public static long generation = 50000 / 20;

	/** Thirty seconds of {@link #generation}. */
	public static long maxPower = generation * 30 * 20;

	/** Upstream: {@code 20*5}. Ticks one snowball cools for. */
	public static int maxCool = 20 * 5;

	/** The core is three layers of nine. */
	public static final int MAX_FUEL = 27;

	/** Half the side of the meltdown's cube. Upstream's {@code range = 8}. */
	public static final int MELT_RANGE = 8;

	/** Only recomputed on the server, every tick. Not saved. */
	public boolean formed = false;

	public int damage = 0;
	public int partialConsumption = 0;
	public int dynaMaxPartialConsumption = 0;
	public int fuel = 0;
	public boolean on = false;
	public long power = 0;
	public int cool = 0;

	/** The players with the menu open, who are sent the six numbers every tick. */
	public final List<Player> listeners = new ArrayList<>();

	/** What was last saved, so {@link #setChanged} is called when something moved and not before. */
	private int savedSignature;

	public SlopTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	public SlopTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.SLOP.get(), pos, state);
	}

	// ---- inventory -------------------------------------------------------------------------

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? new int[] { SLOT_FUEL } : new int[] { SLOT_COOLANT };
	}

	/** Upstream's {@code isItemValidForSlot} is {@code true} for both, and a hopper's the same. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public String getName() {
		return "tile.slop_reactor.name";
	}

	@Override
	public boolean stillValid(Player player) {
		return formed && super.stillValid(player);
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new SlopMenu(containerId, playerInventory, this);
	}

	// ---- the structure ---------------------------------------------------------------------

	private Direction forward() {
		return getBlockState().getValue(BlockMachineBase.FACING).getOpposite();
	}

	/** The middle of the 3x3x3 core. */
	public BlockPos center() {
		return worldPosition.relative(forward(), 2).above(2);
	}

	/**
	 * Every cell of the 5x5x5 that is wrong, in upstream's scan order (x, then z, then y).
	 * Empty means built.
	 */
	public List<BlockPos> invalidBlocks() {

		BlockPos center = center();
		List<BlockPos> wrong = new ArrayList<>();

		for(int xo = -2; xo <= 2; xo++) {
			for(int zo = -2; zo <= 2; zo++) {
				for(int yo = -2; yo <= 2; yo++) {

					BlockPos p = center.offset(xo, yo, zo);
					BlockState state = level.getBlockState(p);

					if(Math.abs(xo) <= 1 && Math.abs(yo) <= 1 && Math.abs(zo) <= 1) { // inside

						if(!state.is(ModBlocks.toxic_block.get()) && !state.isAir()) wrong.add(p);

					} else if(!p.equals(worldPosition)) {

						boolean frame = (Math.abs(xo) == 2 && Math.abs(yo) == 2)
								|| (Math.abs(zo) == 2 && Math.abs(yo) == 2)
								|| (Math.abs(xo) == 2 && Math.abs(zo) == 2);

						if(!state.is(frame ? LeafiaBlocks.slop_reactor_casing.get() : LeafiaBlocks.slop_reactor_glass.get()))
							wrong.add(p);
					}
				}
			}
		}

		return wrong;
	}

	/**
	 * Upstream's {@code checkFormed(printInvalidBlocks)}: true when the 5x5x5 is built, and when
	 * asked to print, a burst of red dust at each block that is not.
	 */
	public boolean checkFormed(boolean printInvalidBlocks) {

		List<BlockPos> wrong = invalidBlocks();

		if(printInvalidBlocks && level instanceof ServerLevel server) {
			for(BlockPos p : wrong)
				server.sendParticles(new DustParticleOptions(new Vector3f(1F, 0F, 0F), 2F),
						p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 10, 0.3D, 0.3D, 0.3D, 0D);
		}

		return wrong.isEmpty();
	}

	// ---- the fuel --------------------------------------------------------------------------

	/**
	 * Upstream: a toxic block whose {@code BlockFluidClassic.LEVEL} is 0. The base mod's
	 * {@code toxic_block} has no such property yet, so it is a source by default.
	 */
	public boolean isValidFuel(BlockPos p) {

		BlockState state = level.getBlockState(p);
		if(!state.is(ModBlocks.toxic_block.get())) return false;

		return !state.hasProperty(LiquidBlock.LEVEL) || state.getValue(LiquidBlock.LEVEL) == 0;
	}

	/** Counts the core's fuel: three layers, one block ahead of the reactor and up. */
	public void updateFuel() {

		Direction forward = forward();
		Direction right = forward.getClockWise();
		fuel = 0;

		for(int xo = -1; xo <= 1; xo++)
			for(int yo = 1; yo <= 3; yo++)
				for(int zo = 1; zo <= 3; zo++)
					if(isValidFuel(worldPosition.relative(forward, zo).relative(right, xo).above(yo))) fuel++;
	}

	/** Burns the top layer. */
	public void clearFuelLayer() {

		Direction forward = forward();
		Direction right = forward.getClockWise();
		int yo = (int) Math.ceil(fuel / 9f);

		for(int xo = -1; xo <= 1; xo++)
			for(int zo = 1; zo <= 3; zo++)
				level.setBlock(worldPosition.relative(forward, zo).relative(right, xo).above(yo),
						Blocks.AIR.defaultBlockState(), 3);
	}

	/** Builds the next layer, whole, whatever the layer below it is made of. */
	public void fillFuelLayer() {

		Direction forward = forward();
		Direction right = forward.getClockWise();
		int yo = fuel / 9 + 1;

		for(int xo = -1; xo <= 1; xo++)
			for(int zo = 1; zo <= 3; zo++)
				level.setBlock(worldPosition.relative(forward, zo).relative(right, xo).above(yo),
						ModBlocks.toxic_block.get().defaultBlockState(), 3);
	}

	// ---- the meltdown ----------------------------------------------------------------------

	/** The 17x17x17 cube around the core goes to air, and whatever was in it dies. */
	public void meltdown() {

		BlockPos center = center();
		int range = MELT_RANGE;

		for(int xo = -range; xo <= range; xo++)
			for(int yo = -range; yo <= range; yo++)
				for(int zo = -range; zo <= range; zo++)
					level.setBlock(center.offset(xo, yo, zo), Blocks.AIR.defaultBlockState(), 3);

		for(Entity entity : level.getEntities((Entity) null, new AABB(
				center.getX() - range, center.getY() - range, center.getZ() - range,
				center.getX() + range + 1, center.getY() + range + 1, center.getZ() + range + 1), e -> true))
			tryKill(entity);
	}

	/**
	 * Upstream's {@code tryKill}, "copied from IF null grenade": health to zero for anything
	 * living, gone for anything else, and a second pass at the end of the tick for whatever
	 * survived. A dragon's part kills the dragon.
	 *
	 * <p>A player gets the death upstream's 1.12.2 gave it by itself, explicitly, as the meltdown
	 * pill does (D-1069).
	 */
	public static void tryKill(Entity toKill) {

		if(toKill instanceof PartEntity<?> part) {
			if(part.getParent() != null) {
				tryKill(part.getParent());
				return;
			}
		}

		if(toKill instanceof LivingEntity living) {
			living.setHealth(0F);
			if(living instanceof Player && living.level() instanceof ServerLevel server)
				living.die(server.damageSources().genericKill());
		} else {
			toKill.discard();
		}

		Delayed.queue(toKill);
	}

	/** Upstream's {@code DelayedTick.nextWorldTickEnd}, for the one thing it was used for. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
	public static final class Delayed {

		private Delayed() {}

		/** Entities {@link #tryKill} touched this tick. */
		private static final List<Entity> PENDING = new ArrayList<>();

		/** Asks for {@code entity} to be looked at again at the end of the tick. */
		public static void queue(Entity entity) {
			PENDING.add(entity);
		}

		@SubscribeEvent
		public static void onServerTick(ServerTickEvent.Post event) {
			run();
		}

		/** Public so a test can run the end of the tick without waiting for it. */
		public static void run() {
			if(PENDING.isEmpty()) return;
			List<Entity> batch = new ArrayList<>(PENDING);
			PENDING.clear();
			for(Entity entity : batch) forceRemove(entity);
		}

		/** What is left standing after setHealth(0) and discard(). */
		static void forceRemove(Entity entity) {

			if(entity.isRemoved() || !entity.isAlive()) return;

			if(entity instanceof Player) {
				entity.kill();
				return;
			}

			entity.ejectPassengers();
			entity.stopRiding();

			if(entity.isMultipartEntity() && entity.getParts() != null)
				for(PartEntity<?> part : entity.getParts()) {
					if(part == null) continue;
					part.ejectPassengers();
					part.stopRiding();
					part.discard();
				}

			entity.discard();
		}
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		formed = checkFormed(false);

		if(!formed) {
			on = false;
			return;
		}

		updateFuel();

		ItemStack ingots = slots[SLOT_FUEL];
		if(ingots.is(ModItems.ingot_uranium.get()) && fuel < MAX_FUEL) {
			int demand = 9 - fuel % 9;
			if(ingots.getCount() >= demand) {
				ingots.shrink(demand);
				fillFuelLayer();
			}
		}

		int mod = fuel % 9;
		if(fuel == 0) mod = 0;
		else if(mod == 0) mod = 9;
		dynaMaxPartialConsumption = maxPartialConsumption * mod / 9;

		if(on) {

			if(fuel > 0) {
				power = Math.min(power + generation, maxPower);
				partialConsumption++;
				if(partialConsumption > dynaMaxPartialConsumption) {
					partialConsumption = 0;
					clearFuelLayer();
				}
			}

			if(cool <= 0) {
				ItemStack snow = slots[SLOT_COOLANT];
				if(snow.is(Items.SNOWBALL)) {
					snow.shrink(1);
					cool = maxCool;
				}
			} else {
				cool--;
			}

			if(cool <= 0) {
				damage++;
				if(damage > maxDamage) {
					meltdown();
					return;
				}
			} else {
				damage = 0;
			}
		}

		Direction face = getBlockState().getValue(BlockMachineBase.FACING);
		tryProvide(level, worldPosition.relative(face), face);
		tryProvide(level, worldPosition.below(), Direction.DOWN);

		if(!listeners.isEmpty()) {
			LeafiaPacket._start(this)
					.__write(0, fuel)
					.__write(1, damage)
					.__write(2, partialConsumption)
					.__write(3, dynaMaxPartialConsumption)
					.__write(4, on)
					.__write(5, power)
					.__sendToListeners();
		}

		int signature = java.util.Objects.hash(damage, partialConsumption, power, on, cool, slots[0].getCount(), slots[1].getCount());
		if(signature != savedSignature) {
			savedSignature = signature;
			setChanged();
		}
	}

	// ---- persistence -----------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("damage", damage);
		nbt.putInt("partial", partialConsumption);
		nbt.putLong("power", power);
		nbt.putBoolean("on", on);
		nbt.putInt("cool", cool);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		damage = nbt.getInt("damage");
		partialConsumption = nbt.getInt("partial");
		power = nbt.getLong("power");
		on = nbt.getBoolean("on");
		cool = nbt.getInt("cool");
	}

	// ---- energy ----------------------------------------------------------------------------

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	// ---- packets ---------------------------------------------------------------------------

	@Override
	public String getPacketIdentifier() {
		return "SLOP_NUKE";
	}

	@Override
	public double affectionRange() {
		return 0;
	}

	@Override
	public List<Player> getListeners() {
		return listeners;
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> fuel = (int) value;
			case 1 -> damage = (int) value;
			case 2 -> partialConsumption = (int) value;
			case 3 -> dynaMaxPartialConsumption = (int) value;
			case 4 -> on = (boolean) value;
			case 5 -> power = (long) value;
		}
	}

	/** The GUI's one control: the switch. */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key != 0) return;
		// D-1094 (closing D-1080's flag): the switch is the reactor screen's one control, so the sender
		// must have this reactor's menu open and be in reach, and the value must be a boolean.
		if(!(plr != null && plr.containerMenu instanceof SlopMenu menu
				&& menu.slop == this)) {
			PacketGuard.reject("SlopTE switch", plr, "this reactor's screen is not open");
			return;
		}
		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("SlopTE switch", plr, "out of reach");
			return;
		}
		if(!(value instanceof Boolean state)) {
			PacketGuard.reject("SlopTE switch", plr, "value " + value);
			return;
		}
		on = state;
	}

	@Override
	public void onPlayerValidate(Player plr) { }

	public static void tick(Level level, BlockPos pos, BlockState state, SlopTE slop) {
		slop.updateEntity();
	}
}
