package com.leafia.contents.effects.folkvangr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.entity.logic.EntityExplosionChunkloading;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaDamageSources;
import com.leafia.init.LeafiaSounds;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.leafia.passive.effects.LeafiaShakecam.ShakecamPacket;
import com.llib.exceptions.messages.TextWarningLeafia;
import com.llib.technical.LeafiaEase;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Folkvangr: the addon's F.L.E.I.J.A. A sphere of the cloud's maximum size is erased chunk by chunk behind
 * the growing cloud, everything inside the cloud is killed outright, and when the cloud has finished a
 * vacuum pulls everything within three radii back towards the centre.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/EntityNukeFolkvangr.java}. Nothing spawns one
 * but the MK3 hook ({@code LeafiaExplosionSeams}, for {@code MixinEntityNukeExplosionMK3}): a F.L.E.I.J.A.
 * MK3's first tick makes a Folkvangr where it stands and removes itself, and the F.L.E.I.J.A. cloud the
 * same bomb spawned binds to it ({@link LeafiaFleijaClouds}).
 *
 * <h2>The numbers</h2>
 *
 * <ul>
 * <li><b>Binding.</b> A Folkvangr made without a cloud waits in {@link #awaitingBind}; a cloud within 1.5
 *     blocks takes it. Ten ticks unbound, it gives up, warns every player in the level and removes itself.</li>
 * <li><b>The sweep.</b> Chunks are visited in square rings round the Folkvangr's own chunk - ring 0 is that
 *     chunk, ring {@code n} the {@code 8n} around it - and the ring and column reached is the cloud's scale
 *     over 16: the eraser follows the cloud's edge. In each chunk a section that lies wholly inside the cube
 *     inscribed in the sphere ({@code 0.707 r}) is emptied whole; every other block within
 *     {@code sqrt(r^2 - dx^2 - dz^2 - 1)} of the centre height is erased one by one. Bedrock's layer is spared,
 *     as is any column whose top is below the section.</li>
 * <li><b>The kill.</b> Every tick, everything within the cloud's scale takes 2,147,483,647 of
 *     {@code leafia:back} damage and, if it lives, has its health set to 0. Ocelots are spared (and cats,
 *     which were ocelots in 1.12.2).</li>
 * <li><b>The end.</b> When the cloud is gone or past its size, the last ring is finished and the vacuum runs
 *     {@code (int)(min((r/50 + 1)^1.25 - 1, 400) x 20)} ticks: everything within {@code 3r} is pulled in by
 *     {@code (r/4)^0.75 / 30 x 1.75} a tick, scaled linearly from full at {@code r} to none at {@code 3r},
 *     and anything within {@code r} takes 3 of {@code leafia:fleija} a tick. The same pull runs on each
 *     client for its own player, from a packet, and draws {@link
 *     com.leafia.contents.effects.folkvangr.particles.ParticleFleijaVacuum}s.</li>
 * <li><b>The bang.</b> On binding: the Folkvangr sound at a volume of the radius, and a camera shake of
 *     {@code duration=r, range=2r} for everyone tracking within {@code 2.25r} (another, sharp one, for an
 *     antischrabidium cloud).</li>
 * </ul>
 *
 * <h2>Kept exactly, and flagged</h2>
 *
 * <ul>
 * <li>The final pass sets the ring to {@code ceil(r / 16)} and the column to that ring's whole count, and the
 *     loop runs while either is short - so it finishes that ring and then carries on into the next one for as
 *     many columns again, erasing up to a ring past the sphere's chunks (inside the sphere's own radius test,
 *     so nothing outside the sphere goes).</li>
 * <li>Block entities in a wholly-emptied section are removed without dropping anything, except in its top
 *     section, which upstream's range ({@code y < max x 16}) misses and so breaks normally.</li>
 * <li>Upstream's {@code lastCloud} is never assigned anywhere, so the constructor's quick bind is dead; the
 *     cloud always does the binding.</li>
 * </ul>
 *
 * <h2>What changed for 1.21.1</h2>
 *
 * <ul>
 * <li><b>Sections are emptied block by block</b> through the level ({@code setBlock} to air, flags 2 and 16:
 *     sent to clients, no neighbour updates), where upstream nulled the chunk's storage array and sent its
 *     own {@code ClearChunkPacket} so that clients did the same. Nulling a 1.21.1 section would leave the
 *     heightmaps and the light engine wrong, and vanilla's own block sync does the packet's job, so that
 *     packet is not ported. Every boundary block was already erased this way upstream.</li>
 * <li>Section 0 and the top section were "below 1" and "15" in 1.12.2's sixteen; here they are the level's
 *     own lowest and highest, and "spare bedrock" spares the level's floor.</li>
 * <li>The chunk ticket is the base mod's explosion controller ({@link EntityExplosionChunkloading}), which
 *     forces the Folkvangr's own chunk from the moment it is added. Upstream's ticket forced chunk (0, 0) at
 *     construction, before the position was known, and its own chunk only once bound.</li>
 * <li>{@code World.getHeight(x, z)} is the {@code MOTION_BLOCKING} heightmap (D-843). The chunks are loaded
 *     synchronously, generated if need be, as upstream's {@code getChunk} did.</li>
 * </ul>
 */
public class EntityNukeFolkvangr extends EntityExplosionChunkloading {

	/** Folkvangrs made without a cloud, waiting for one to bind them. Shared across levels, as upstream's is. */
	public static final Set<EntityNukeFolkvangr> awaitingBind = ConcurrentHashMap.newKeySet();

	/** The bound cloud: an {@code EntityCloudFleija} or {@code EntityCloudFleijaRainbow}. */
	@Nullable
	public Entity cloudBound = null;
	@Nullable
	protected UUID cloudUUID = null;

	/** Chunks erased per tick the cloud's speed aims at. */
	public static final byte preferredChunksPerTick = 2;
	public static final LeafiaEase vacuumEase = new LeafiaEase(LeafiaEase.Ease.EXPO, LeafiaEase.Direction.O);
	public static final double vacuumForceMultiplier = 1.75;
	/** Damage a tick to anything inside the radius during the vacuum. */
	public static final float VACUUM_DAMAGE = 3;
	/** The vacuum packet reaches this far past the vacuum. */
	public static final double VACUUM_PACKET_MARGIN = 120;
	/** Ticks unbound before giving up. */
	public static final int BIND_TIMEOUT = 10;

	public short row = 0;
	public int columnIndex = 0;
	public int postEffect = -1;
	public double vacuumStart = 0;
	public double vacuumEnd = 0;
	public double vacuumForce = 0;
	boolean played = false;

	public EntityNukeFolkvangr(EntityType<? extends EntityNukeFolkvangr> type, Level level) {
		super(type, level);
	}

	/** Upstream's {@code (World, Vec3d, cloud)}: with a null cloud it waits to be bound. */
	public EntityNukeFolkvangr(EntityType<? extends EntityNukeFolkvangr> type, Level level, Vec3 pos, @Nullable Entity cloud) {
		super(type, level);
		this.setPos(pos.x, pos.y, pos.z);
		this.cloudBound = cloud;
		if(cloud == null && !level.isClientSide) awaitingBind.add(this);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) { }

	@Override
	protected void readAdditionalSaveData(CompoundTag compound) {
		if(compound.hasUUID("cloud")) cloudUUID = compound.getUUID("cloud");
		row = compound.getShort("row");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag compound) {
		if(cloudUUID != null) compound.putUUID("cloud", cloudUUID);
		compound.putShort("row", row);
	}

	@Override
	public void remove(RemovalReason reason) {
		awaitingBind.remove(this);
		super.remove(reason);
	}

	/** The bound cloud's scale, or 0. */
	public float cloudScale() {
		return cloudBound == null ? 0 : LeafiaFleijaClouds.getScale(cloudBound);
	}

	/** The bound cloud's maximum size, or 0. */
	public int cloudMaxSize() {
		return cloudBound == null ? 0 : LeafiaFleijaClouds.getMaxSize(cloudBound);
	}

	@Override
	public void tick() {
		if(this.isRemoved()) return;
		super.tick();
		if(this.level().isClientSide) return;
		ServerLevel level = (ServerLevel) this.level();

		if(postEffect >= 0) {
			postEffect--;
			if(postEffect <= 0) {
				this.discard();
			} else {
				pull(level);
			}
			return;
		}

		if(cloudUUID != null && cloudBound == null) {
			Entity found = level.getEntity(cloudUUID);
			if(found != null && LeafiaFleijaClouds.isCloud(found) && found.isAlive()) {
				awaitingBind.remove(this);
				cloudBound = found;
			}
		}
		if(cloudUUID == null && cloudBound != null && cloudBound.isAlive() && cloudBound.isAddedToLevel()) {
			cloudUUID = cloudBound.getUUID();
		}
		if(this.tickCount > BIND_TIMEOUT && (cloudUUID == null || cloudBound == null)) {
			awaitingBind.remove(this);
			this.discard();
			for(Player player : level.players())
				player.sendSystemMessage(TextWarningLeafia.of("EntityCloudFleija did not bind in time!"));
			return;
		}

		if(cloudUUID != null && cloudBound != null) {
			int max = cloudMaxSize();
			float scale = cloudScale();
			if(!played) bang(level, max);
			played = true;

			double curRange = scale / 16d;
			short start = (short) Math.floor(curRange);
			int destColumn = (int) Math.floor((curRange - Math.floor(curRange)) * getRowTotal(start));

			if(!cloudBound.isAlive() || scale > max) {
				start = (short) Math.ceil(max / 16d);
				destColumn = getRowTotal(start);
				postEffect = postEffectTicks(max);
				vacuumStart = max;
				vacuumEnd = max * 3;
				vacuumForce = vacuumForce(max);
				FolkvangrVacuumPacket packet = new FolkvangrVacuumPacket(this.position(), postEffect, vacuumStart, vacuumEnd, vacuumForce);
				LeafiaCustomPacket.__start(packet).__sendToAllAround(level, this.position(), vacuumEnd + VACUUM_PACKET_MARGIN);
			}

			kill(level, scale);

			while(row < start || columnIndex < destColumn) {
				byte side = (byte) Math.floorDiv(columnIndex, getSideLength(row));
				int sideOffset = columnIndex - side * getSideLength(row);
				switch(side) {
					case 0: processChunk(level, getSideStart(row) + sideOffset, row); break;
					case 1: processChunk(level, row, -getSideStart(row) - sideOffset); break;
					case 2: processChunk(level, -getSideStart(row) - sideOffset, -row); break;
					case 3: processChunk(level, -row, getSideStart(row) + sideOffset); break;
				}
				if(++columnIndex >= getRowTotal(row)) {
					row++;
					columnIndex = 0;
				}
			}
		}
	}

	/** The sound and the camera shake, once. */
	void bang(ServerLevel level, int max) {
		BlockPos pos = this.blockPosition();
		level.playSound(null, pos, LeafiaSounds.nuke_folkvangr.get(), SoundSource.BLOCKS, max, 1);
		LeafiaCustomPacket.__start(new ShakecamPacket(new String[] {"duration=" + max, "range=" + max * 2}, pos))
				.__sendToAllAround(level, pos, max * 2.25);
		if(LeafiaFleijaClouds.getIsAntischrab(cloudBound)) {
			LeafiaCustomPacket.__start(new ShakecamPacket(new String[] {"type=smooth", "duration=2", "speed=8", "ease=expoOut", "intensity=12", "range=" + max * 2}, pos))
					.__sendToAllAround(level, pos, max * 2.25);
		}
	}

	/** Everything within the cloud's scale but ocelots and cats. */
	void kill(ServerLevel level, double scale) {
		AABB box = new AABB(getX() - scale, getY() - scale, getZ() - scale, getX() + scale, getY() + scale, getZ() + scale);
		for(Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
			if(isSpared(entity)) continue;
			if(entity.position().distanceTo(this.position()) <= scale) {
				entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.back), 2147483647);
				if(entity instanceof LivingEntity living) living.setHealth(0);
			}
		}
	}

	/** {@code instanceof EntityOcelot}, which in 1.12.2 was the cat as well. */
	public static boolean isSpared(Entity entity) {
		return entity instanceof Ocelot || entity instanceof Cat;
	}

	/** The vacuum, on the server: every entity within {@link #vacuumEnd}. */
	void pull(ServerLevel level) {
		AABB box = new AABB(getX() - vacuumEnd, getY() - vacuumEnd, getZ() - vacuumEnd, getX() + vacuumEnd, getY() + vacuumEnd, getZ() + vacuumEnd);
		for(Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
			double distance = entity.position().distanceTo(this.position());
			if(distance <= vacuumEnd) {
				Vec3 pull = pullOn(entity.position(), this.position(), distance, vacuumStart, vacuumEnd, vacuumForce);
				entity.setDeltaMovement(entity.getDeltaMovement().add(pull));
				if(distance <= vacuumStart) {
					if(isSpared(entity)) continue;
					entity.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.fleija), VACUUM_DAMAGE);
				}
			}
		}
	}

	/** The velocity the vacuum adds in a tick to something at {@code at}, {@code distance} from {@code centre}. */
	public static Vec3 pullOn(Vec3 at, Vec3 centre, double distance, double vacuumStart, double vacuumEnd, double vacuumForce) {
		double force = vacuumEase.get((distance - vacuumStart) / (vacuumEnd - vacuumStart), 1, 0, true);
		Vec3 pullVec = centre.subtract(at).normalize();
		return pullVec.scale(vacuumForce * vacuumForceMultiplier * force);
	}

	public static int postEffectTicks(int max) {
		return (int) (Math.min(Math.pow(max / 50d + 1, 1.25) - 1, 20 * 20) * 20);
	}

	public static double vacuumForce(int max) {
		return Math.pow(max / 4d, 0.75) / 30d;
	}

	// ------------------------------------------------------------------ the eraser

	/** Upstream's {@code carveChunk}: one 16-high slice of a chunk column, section {@code cy}. */
	protected boolean carveChunk(ServerLevel level, int cx, int cy, int cz) {
		long radius = cloudMaxSize();
		ChunkPos chunkPos = new ChunkPos(cx, cz);
		BlockPos centre = this.blockPosition();
		int floor = level.getMinBuildHeight();
		boolean carved = false;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for(int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
			for(int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
				if(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) < cy * 16) continue;
				carved = true;
				long rx = x - centre.getX();
				long rz = z - centre.getZ();
				double distFromRing = radius * radius - (rx * rx + rz * rz);
				if(distFromRing > 1) {
					int ylength = (int) Math.sqrt(Math.max(0, distFromRing - 1));
					int ystart = Math.max(centre.getY() - ylength, cy * 16);
					int yend = Math.min(centre.getY() + ylength, cy * 16 + 15);
					if(ystart <= floor) ystart++;
					if(yend <= floor) yend++;
					for(int y = ystart; y <= yend; y++) {
						eraseBlock(level, cursor.set(x, y, z));
					}
				}
			}
		}
		return carved;
	}

	/** Upstream's {@code processChunk}, {@code (x, z)} relative to the Folkvangr's chunk. */
	protected void processChunk(ServerLevel level, int x, int z) {
		int radius = cloudMaxSize();
		ChunkPos home = this.chunkPosition();
		LevelChunk chunk = level.getChunk(home.x + x, home.z + z);
		int lowest = level.getMinSection();
		int highest = level.getMaxSection() - 1;
		double posX = getX(), posY = getY(), posZ = getZ();
		int ccx = chunk.getPos().x, ccz = chunk.getPos().z;
		if(((Math.ceil((posX - radius * 0.707) / 16) <= ccx) && (ccx + 1 < Math.floor((posX + radius * 0.707) / 16)))
				&& ((Math.ceil((posZ - radius * 0.707) / 16) <= ccz) && (ccz + 1 < Math.floor((posZ + radius * 0.707) / 16)))) {
			int minCY = (int) Mth.clamp(Math.ceil((posY - radius * 0.707) / 16), lowest + 1, highest);
			int maxCY = (int) Mth.clamp(Math.floor((posY + radius * 0.707) / 16), lowest + 1, highest);
			eraseChunk(level, chunk, minCY, maxCY);
			if(minCY >= maxCY) {
				for(int cy = lowest; cy <= highest; cy++) {
					if(!carveChunk(level, ccx, cy, ccz)) break;
				}
			} else {
				for(int cy = lowest; cy < minCY; cy++) {
					if(!carveChunk(level, ccx, cy, ccz)) break;
				}
				for(int cy = maxCY + 1; cy <= highest; cy++) {
					if(!carveChunk(level, ccx, cy, ccz)) break;
				}
			}
		} else {
			for(int cy = lowest; cy <= highest; cy++) {
				if(!carveChunk(level, ccx, cy, ccz)) break;
			}
		}
	}

	/** Upstream's {@code eraseBlock}: flag 2, no neighbour updates. */
	protected static void eraseBlock(Level level, BlockPos pos) {
		if(level.isInWorldBounds(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
	}

	/**
	 * Upstream's {@code eraseChunk}: sections {@code min..max} emptied whole. Block entities with
	 * {@code min x 16 <= y < max x 16} are removed first, so they drop nothing (upstream removed them from
	 * the chunk's map); those in section {@code max} are not, and break as any erased block does.
	 */
	protected static boolean eraseChunk(Level level, LevelChunk chunk, int min, int max) {
		if(min > max) return false;
		List<BlockPos> removeQueue = new ArrayList<>();
		for(BlockPos pos : chunk.getBlockEntities().keySet()) {
			if(pos.getY() >= min * 16 && pos.getY() < max * 16) removeQueue.add(pos);
		}
		for(BlockPos pos : removeQueue) chunk.removeBlockEntity(pos);
		ChunkPos cp = chunk.getPos();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for(int cy = min; cy <= max; cy++) {
			for(int y = cy * 16; y < cy * 16 + 16; y++) {
				for(int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
					for(int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
						cursor.set(x, y, z);
						BlockState state = chunk.getBlockState(cursor);
						if(!state.isAir()) eraseBlock(level, cursor);
					}
				}
			}
		}
		chunk.setUnsaved(true);
		return true;
	}

	static protected int getSideStart(short row) { return -Math.max(0, row - 1); }
	static protected int getSideLength(short row) { return row - getSideStart(row) + 1; }

	/** The chunks in ring {@code row}: 1, then {@code 8 x row}. */
	public static int getRowTotal(short row) {
		if(row == 0) return 1;
		return getSideLength(row) * 4;
	}

	/** How fast a bound cloud grows with its edge in ring {@code row}: {@code min(16 / (ring / 2), 1)}. */
	public static double getPreferredSpeedMultiplier(short row) {
		return Math.min(16d / (getRowTotal(row) / (float) preferredChunksPerTick), 1);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return false;
	}

	// ------------------------------------------------------------------ the vacuum on the client

	/** One vacuum, as the client keeps it. Public because the DFC's pull draws vacuum particles too. */
	public static class VacuumInstance {
		public int postEffect = -1;
		public double vacuumStart = 0;
		public double vacuumEnd = 0;
		public double vacuumForce = 0;
		public Vec3 pos;

		public VacuumInstance() { }

		public VacuumInstance(Vec3 pos, int postEffect, double vacuumStart, double vacuumEnd, double vacuumForce) {
			this.pos = pos;
			this.postEffect = postEffect;
			this.vacuumStart = vacuumStart;
			this.vacuumEnd = vacuumEnd;
			this.vacuumForce = vacuumForce;
		}
	}

	/** Set by the client: what a vacuum packet does there. */
	public static Consumer<VacuumInstance> clientVacuum = v -> { };

	/** Server to client: a vacuum has begun. Upstream's {@code FolkvangrVacuumPacket}, the same five fields. */
	public static class FolkvangrVacuumPacket extends VacuumInstance implements LeafiaCustomPacketEncoder {

		public FolkvangrVacuumPacket() { }

		public FolkvangrVacuumPacket(Vec3 pos, int postEffect, double vacuumStart, double vacuumEnd, double vacuumForce) {
			super(pos, postEffect, vacuumStart, vacuumEnd, vacuumForce);
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeDouble(pos.x);
			buf.writeDouble(pos.y);
			buf.writeDouble(pos.z);
			buf.writeInt(postEffect);
			buf.writeDouble(vacuumStart);
			buf.writeDouble(vacuumEnd);
			buf.writeDouble(vacuumForce);
		}

		public static FolkvangrVacuumPacket read(LeafiaBuf buf) {
			Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
			return new FolkvangrVacuumPacket(pos, buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
		}

		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			FolkvangrVacuumPacket p = read(buf);
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND)
					clientVacuum.accept(new VacuumInstance(p.pos, p.postEffect, p.vacuumStart, p.vacuumEnd, p.vacuumForce));
			};
		}
	}
}
