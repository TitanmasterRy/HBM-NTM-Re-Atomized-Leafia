package com.leafia.contents.machines.reactors.pwr.debris;

import java.util.Set;

import com.custom_hbm.effectNT.EffectNT;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.projectile.EntityDebrisBase;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.items.ModItems;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.reactors.pwr.PWRMaterial;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRChannelBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRConductorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.init.LeafiaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of a PWR's vessel, flung by a level-3 meltdown: a lump of concrete, a sheet of steel, a length
 * of coolant channel, a control rod's frame or its rods, a graphite block's chip - drawn in the textures
 * of the block it came from, and burning if that block burns.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/debris/PWRDebrisEntity.java}, on NTM-CE's
 * {@code EntityDebrisBase} - the base mod's {@link EntityDebrisBase} here (D-2195). Every constant is
 * upstream's:
 *
 * <ul>
 * <li><b>The kind</b> is chosen from the block: a control rod is its frame one time in three and its
 *     rods otherwise; a coolant channel or boiler is three channels one time in three and one otherwise;
 *     graphite (the base mod's graphite block and every drilled pile block - upstream's
 *     {@code BlockGraphite || BlockGraphiteDrilledBase}, which the base mod has as plain blocks) is a
 *     graphite chip; anything else of {@code Material.IRON} is a blank one time in three, otherwise
 *     concrete if it sounds like stone and shrapnel if not; and anything else is concrete.</li>
 * <li><b>Fire.</b> A block fire spreads to burns for {@code max((300 - flammability - 190)^3, 600)}
 *     ticks of combustion, each tick nine chances in ten of counting; water puts it out with a hiss.
 *     Burning above a million it throws an {@code rbmkflame} one tick in 250, otherwise flames and lava
 *     one tick in fifty.</li>
 * <li><b>Picked up</b>, a frame gives three metal debris, the rods four lead nuggets or (one time in
 *     twenty) a lead-209 nugget, a graphite chip graphite debris, one channel a steel plate and three of
 *     them two steel pipes; anything else gives what the shredder makes of its block, or failing that a
 *     debris item of its kind (shards for glass).</li>
 * <li>Ten minutes' life; hitboxes 0.5 (blank, concrete, shrapnel), 0.25 (graphite) or 1.</li>
 * </ul>
 *
 * <h2>NTM-CE's base, not the base mod's, where they differ</h2>
 *
 * <p>Upstream's piece runs NTM-CE's {@code EntityDebrisBase.onUpdate}, read from the CE jar, and two of its
 * habits are not the base mod's (hbm-src's) and are kept here:
 *
 * <ul>
 * <li><b>Every rising piece smashes what it is about to hit</b> - NTM-CE dropped hbm-src's "lid only"
 *     test - so {@link #destroysBlocks()} answers true.</li>
 * <li><b>A piece that touches a floor or ceiling does not move sideways that tick, and its sideways
 *     velocity reverses and damps</b> ({@code *= -0.75}), because CE's hand-copied {@code moveEntity}
 *     zeroes the horizontal move after a vertical collision and then treats the zero as a wall. So a piece
 *     on the ground jitters to a stop where it landed rather than sliding. {@link #moveDebris} reproduces it.</li>
 * </ul>
 *
 * <p>The block is a synced {@link BlockState} rather than upstream's registry name and metadata; it is
 * saved under {@code block_rsc} (upstream's key, the registry name) and {@code block_state}. Upstream's
 * {@code isBurning} is {@link #isOnFire}, which is what draws the flames on an entity.
 */
public class PWRDebrisEntity extends EntityDebrisBase {

	public static final EntityDataAccessor<BlockState> BLOCK =
			SynchedEntityData.defineId(PWRDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
	public static final EntityDataAccessor<Integer> FLAMMABILITY =
			SynchedEntityData.defineId(PWRDebrisEntity.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Integer> COMBUSTION =
			SynchedEntityData.defineId(PWRDebrisEntity.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> EXTINGUISHED =
			SynchedEntityData.defineId(PWRDebrisEntity.class, EntityDataSerializers.BOOLEAN);

	/**
	 * The meshes the renderer draws, as (mesh, object) pairs baked by a generator script;
	 * kept here so a test on the server can check the models exist. The graphite chip is the base mod's mesh.
	 */
	public static final String[][] MESH_OBJECTS = {
			{"blank_unwrap", "Plane.001"}, {"concrete_unwrap", "Plane"}, {"shrapnel", "Cube"},
			{"channel1x", "SIDE"}, {"channel1x", "TOP"}, {"channel3x", "SIDE"}, {"channel3x", "TOP"},
			{"control_frame", "SIDE"}, {"control_frame", "TOP"}, {"control_rod", "SIDE"}, {"control_rod", "TOP"}};

	/** Upstream's {@code getLifetime}: {@code 10 * 60 * 20}. */
	public static final int LIFETIME = 10 * 60 * 20;

	/** The base mod's pile blocks that upstream's {@code BlockGraphite || BlockGraphiteDrilledBase} names. */
	public static Set<Block> graphiteBlocks() {
		return Set.of(ModBlocks.block_graphite.get(), ModBlocks.block_graphite_drilled.get(), ModBlocks.block_graphite_fuel.get(),
				ModBlocks.block_graphite_plutonium.get(), ModBlocks.block_graphite_rod.get(), ModBlocks.block_graphite_source.get(),
				ModBlocks.block_graphite_lithium.get(), ModBlocks.block_graphite_tritium.get(), ModBlocks.block_graphite_detector.get());
	}

	public PWRDebrisEntity(EntityType<? extends PWRDebrisEntity> type, Level level) {
		super(type, level);
	}

	public PWRDebrisEntity(Level world, double x, double y, double z, BlockState state) {
		this(LeafiaEntities.PWR_DEBRIS.get(), world);
		this.setPos(x, y, z);
		Block block = state.getBlock();
		BlockPos pos = BlockPos.containing(x, y, z);
		int encouragement = state.getFireSpreadSpeed(world, pos, Direction.UP);
		int flammability = state.getFlammability(world, pos, Direction.UP);
		if(encouragement > 0)
			this.entityData.set(FLAMMABILITY, (int) Math.max(Math.pow(300 - flammability - 190, 3), 600));
		this.entityData.set(BLOCK, state);
		this.setType(typeFor(state, world));
	}

	/** Upstream's choice of kind, rolling the world's random where upstream does. */
	public static DebrisType typeFor(BlockState state, Level world) {
		Block block = state.getBlock();
		if(block instanceof PWRControlBlock)
			return world.random.nextInt(3) == 0 ? DebrisType.CONTROL_FRAME : DebrisType.CONTROL_ROD;
		else if(block instanceof PWRChannelBlock || block instanceof PWRConductorBlock)
			return world.random.nextInt(3) == 0 ? DebrisType.CHANNEL_3X : DebrisType.CHANNEL_1X;
		else if(graphiteBlocks().contains(block))
			return DebrisType.GRAPHITE;
		else if(PWRMaterial.isIron(state)) {
			if(world.random.nextInt(3) == 0)
				return DebrisType.BLANK;
			return state.getSoundType() == SoundType.STONE ? DebrisType.CONCRETE : DebrisType.SHRAPNEL;
		}
		return DebrisType.CONCRETE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BLOCK, Blocks.TNT.defaultBlockState());
		builder.define(FLAMMABILITY, 0);
		builder.define(COMBUSTION, 0);
		builder.define(EXTINGUISHED, false);
	}

	public BlockState getBlock() {
		return this.entityData.get(BLOCK);
	}

	boolean wasExtinguished = true;

	@Override
	public void tick() {
		int flammability = this.entityData.get(FLAMMABILITY);
		int combustion = this.entityData.get(COMBUSTION);
		boolean extinguished = this.entityData.get(EXTINGUISHED);
		BlockPos pos = blockPosition();
		if(!level().isClientSide) {
			if(flammability > 0 && combustion < flammability && !extinguished) {
				if(level().random.nextInt(10) > 0) this.entityData.set(COMBUSTION, combustion + 1);
				if(level().isInWorldBounds(pos) && level().getFluidState(pos).is(FluidTags.WATER)) {
					this.entityData.set(EXTINGUISHED, true);
					level().playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1, 1);
				}
			}
		} else {
			remoteUpdate(flammability, extinguished, pos);
		}
		super.tick();
	}

	/** Client side: the hiss's cloud, and the flames. */
	void remoteUpdate(int flammability, boolean extinguished, BlockPos pos) {
		Level world = level();
		if(extinguished && !wasExtinguished) {
			for(int i = world.random.nextInt(3); i < 5; i++) {
				world.addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), 0, 0, 0);
				world.addParticle(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0, 0);
			}
		}
		wasExtinguished = extinguished;
		if(flammability > 0 && !extinguished && world.random.nextInt(50) == 0) {
			if(flammability >= 1000000) {
				if(world.random.nextInt(5) == 0) {
					CompoundTag data = new CompoundTag();
					data.putString("type", "rbmkflame");
					data.putInt("maxAge", 300);
					data.putDouble("posX", getX());
					data.putDouble("posY", getY() + 1.75);
					data.putDouble("posZ", getZ() + 0.5);
					EffectNT.effectNT(data);
				}
			} else {
				for(int i = world.random.nextInt(2); i < 3; i++)
					world.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
				for(int i = world.random.nextInt(2); i < 2; i++)
					world.addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
			}
			world.playLocalSound(pos.getX() + 0.5F, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
					1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
		}
	}

	/** Upstream's {@code isBurning}. */
	@Override
	public boolean isOnFire() {
		int flammability = this.entityData.get(FLAMMABILITY);
		int combustion = this.entityData.get(COMBUSTION);
		boolean extinguished = this.entityData.get(EXTINGUISHED);
		return flammability > 0 && combustion < flammability && !extinguished;
	}

	/** NTM-CE's base smashes ahead of every rising piece, not just the lid (class note). */
	@Override
	public boolean destroysBlocks() {
		return true;
	}

	/**
	 * NTM-CE's move (class note): gravity, then a floor or ceiling stops the sideways move for the tick and
	 * reverses the sideways velocity at 0.75; walls bounce as in the base; then upstream's friction or tumble.
	 */
	@Override
	protected void moveDebris() {
		Vec3 motion = getDeltaMovement();
		double mx = motion.x;
		double my = motion.y - GRAVITY;
		double mz = motion.z;
		this.stuckSpeedMultiplier = Vec3.ZERO;

		Vec3 vertical = new Vec3(0, my, 0);
		double allowedY = collideBoundingBox(this, vertical, getBoundingBox(), level(),
				level().getEntityCollisions(this, getBoundingBox().expandTowards(vertical))).y;
		if(allowedY != my) {
			move(MoverType.SELF, vertical);
			mx *= WALL_BOUNCE;
			mz *= WALL_BOUNCE;
			my = 0;
		} else {
			Vec3 wanted = new Vec3(mx, my, mz);
			Vec3 allowed = collideBoundingBox(this, wanted, getBoundingBox(), level(),
					level().getEntityCollisions(this, getBoundingBox().expandTowards(wanted)));
			move(MoverType.SELF, wanted);
			if(wanted.x != allowed.x) mx *= WALL_BOUNCE;
			if(wanted.z != allowed.z) mz *= WALL_BOUNCE;
		}

		this.lastRot = this.rot;
		if(onGround()) {
			mx *= GROUND_FRICTION;
			mz *= GROUND_FRICTION;
			my *= GROUND_BOUNCE;
		} else {
			this.rot += SPIN;
			if(this.rot >= 360F) {
				this.rot -= 360F;
				this.lastRot -= 360F;
			}
		}
		setDeltaMovement(mx, my, mz);
	}

	/** Upstream's {@code processInitialInteract}: what a piece gives, by kind, if it fits. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if(!level().isClientSide && !isRemoved()) {
			switch(getDebrisType()) {
				case CONTROL_FRAME -> {
					if(player.getInventory().add(new ItemStack(ModItems.debris_metal.get(), 3))) discard();
				}
				case CONTROL_ROD -> {
					ItemStack out = level().random.nextInt(20) == 0 ? new ItemStack(ModItems.nugget_pb209.get())
							: new ItemStack(ModItems.nugget_lead.get(), 4);
					if(player.getInventory().add(out)) discard();
				}
				case GRAPHITE -> {
					if(player.getInventory().add(new ItemStack(ModItems.debris_graphite.get()))) discard();
				}
				case CHANNEL_1X -> {
					if(player.getInventory().add(new ItemStack(ModItems.plate_steel.get()))) discard();
				}
				case CHANNEL_3X -> {
					if(player.getInventory().add(new ItemStack(ModItems.pipe_steel.get(), 2))) discard();
				}
				default -> {
					Block block = getBlock().getBlock();
					ItemStack shredded = ShredderRecipes.shredderRecipes.get(new ComparableStack(block.asItem()));
					if(shredded != null && player.getInventory().add(shredded.copy())) discard();
					if(isAlive()) {
						if(player.getInventory().add(getPickup())) discard();
					}
				}
			}
			player.inventoryMenu.broadcastChanges();
		}
		return InteractionResult.PASS;
	}

	/** The debris item a piece of the default kinds becomes: shards for glass, else by kind. */
	@Override
	public ItemStack getPickup() {
		Item item = switch(getDebrisType()) {
			case BLANK, SHRAPNEL -> LeafiaItems.lwr_shrapnel.get();
			default -> LeafiaItems.lwr_piece.get();
		};
		if(PWRDebrisItem.blockName(getBlock()).contains("glass")) item = LeafiaItems.lwr_shard.get();
		return PWRDebrisItem.of(item, getBlock());
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return switch(getDebrisType()) {
			case BLANK, CONCRETE, SHRAPNEL -> EntityDimensions.scalable(0.5F, 0.5F);
			case GRAPHITE -> EntityDimensions.scalable(0.25F, 0.25F);
			default -> EntityDimensions.scalable(1F, 1F);
		};
	}

	@Override
	public int getLifetime() {
		return LIFETIME;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if(nbt.contains("block_state"))
			this.entityData.set(BLOCK, NbtUtils.readBlockState(level().registryAccess().lookupOrThrow(Registries.BLOCK), nbt.getCompound("block_state")));
		else
			this.entityData.set(BLOCK, PWRDebrisItem.blockOf(nbt.getString("block_rsc")).defaultBlockState());
		if(nbt.contains("flammability")) {
			this.entityData.set(FLAMMABILITY, nbt.getInt("flammability"));
			this.entityData.set(COMBUSTION, nbt.getInt("combustion"));
			this.entityData.set(EXTINGUISHED, nbt.getBoolean("extinguished"));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putString("block_rsc", PWRDebrisItem.blockName(getBlock()));
		nbt.put("block_state", NbtUtils.writeBlockState(getBlock()));
		if(this.entityData.get(FLAMMABILITY) > 0) {
			nbt.putInt("flammability", this.entityData.get(FLAMMABILITY));
			nbt.putInt("combustion", this.entityData.get(COMBUSTION));
			nbt.putBoolean("extinguished", this.entityData.get(EXTINGUISHED));
		}
	}

	public void setType(DebrisType type) {
		setTypeId(type.ordinal());
	}

	/** Upstream's {@code getType}, renamed: {@code Entity.getType()} exists (D-2195). */
	public DebrisType getDebrisType() {
		return DebrisType.values()[Math.abs(getTypeId()) % DebrisType.values().length];
	}

	public enum DebrisType {
		BLANK,
		CONCRETE(4),
		SHRAPNEL,
		GRAPHITE,
		CONTROL_FRAME(2),
		CONTROL_ROD,
		CHANNEL_3X,
		CHANNEL_1X;

		/** Upstream's, and read by nothing upstream either. */
		final double weight;

		DebrisType() {
			this.weight = 1;
		}

		DebrisType(double weight) {
			this.weight = weight;
		}
	}
}
