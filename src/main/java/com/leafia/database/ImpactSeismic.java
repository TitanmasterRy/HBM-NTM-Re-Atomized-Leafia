package com.leafia.database;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.hbm.blocks.IRadResistantBlock;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.unsorted.StructuralIntegrityHandler;
import com.leafia.unsorted.StructuralIntegrityHandler.SimulationData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Tom impact brings unsupported blocks down (D-3022): while the base mod's impact pass works through a chunk
 * (only while there is dust or fire), every block in it is given one chance in a hundred to be tested, and one the
 * structural-integrity handler finds overloaded comes down.
 *
 * <p>Upstream: {@code com/leafia/database/ImpactSeismic.java}, called for every block of the chunk by
 * {@code MixinImpactWorldHandler} just before {@code impactEffects} reads the chunk's position. Here
 * {@code LeafiaStrikeSeams.SEISMIC_CHUNK} is a base-mod {@code ImpactWorldHandler.ChunkListener}, which is called at
 * the same point, behind {@code overhauls.impactSeismic}.
 *
 * <p>The rule, upstream's:
 * <ol>
 * <li>a radiation-resistant block never moves;</li>
 * <li>otherwise one in a hundred - drawn for <em>every</em> block, air included, from the bottom of the world to the
 *     top - is tested if there is air under it or it is not a solid full cube;</li>
 * <li>if the handler's simulation reads an overload ratio of 1 or more, a block that is not a full solid cube is
 *     broken (and drops); a full solid one falls instead, breaking every non-solid block under it until it lands,
 *     plays its place sound and tells clients within 128 to draw the dust of its fall.</li>
 * </ol>
 *
 * <p>1.12.2's {@code isOpaqueCube} is {@link BlockState#isSolidRender} (D-964), {@code isFullCube} is
 * {@code isCollisionShapeFullBlock} (as {@code LeafiaUtil.isSolidVisibleCube} reads it), and {@code isFullBlock}
 * both at once. "Every block from 0 to 255" is the level's whole height. Upstream's loop that built a
 * {@code blockdust} particle per step and never sent it is dead and not ported.
 *
 * <p>{@link #seismic} is upstream's client-side level. The server sends it to every client in the dimension each tick
 * ({@link SeismicSyncPacket}, upstream's {@code WorldGlobalSyncPacket} in {@code LeafiaPassiveServer}), and the client wobbles
 * while it is above 0 ({@link #localTick}, upstream's {@code LeafiaClientListener.Tom}): on the ground, the field of view
 * drops by 2% on a coin flip and the camera jitters, every tick (D-3040). D-3022 read the field as never written; the packet
 * was in a file that had not been ported yet.
 */
public final class ImpactSeismic {

	private ImpactSeismic() {}

	/** One block in this many is tested. */
	public static final int ODDS = 100;
	/** Clients within this many blocks see the dust. */
	public static final double PACKET_RANGE = 128;

	public static float seismic = 0;

	/** Upstream's {@code Tom.fovM}: what the field of view is multiplied by this tick. */
	public static float fovM = 1;
	/** Upstream's {@code Tom.shakeX} and {@code shakeY}, each in {@code [-0.5, 0.5)}. */
	public static float shakeX = 0;
	public static float shakeY = 0;
	/** The camera moves {@code shakeX} times this sideways... */
	public static final float SHAKE_X = 0.05F;
	/** ...and {@code shakeY} times this up. */
	public static final float SHAKE_Y = 0.005F;

	/** Upstream's {@code Tom.localTick}, its seismic half (the dust half waits on the base mod's client dust, D-3040). */
	public static void localTick(boolean onGround, java.util.Random rand) {
		if(seismic > 0 && onGround) {
			float mul = 1F;
			fovM = 1.0F - (rand.nextInt(2) * 0.02F) * mul;
			shakeX = (rand.nextFloat() - 0.5F) * mul;
			shakeY = (rand.nextFloat() - 0.5F) * mul;
		} else {
			fovM = 1.0F;
			shakeX = 0F;
			shakeY = 0F;
		}
	}

	/** The level, server to client: upstream's {@code WorldGlobalSyncPacket}. */
	public static class SeismicSyncPacket implements LeafiaCustomPacketEncoder {
		public float level;

		public SeismicSyncPacket() {}

		public SeismicSyncPacket(float level) {
			this.level = level;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeFloat(level);
		}

		@Override
		public @Nullable Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			float value = buf.readFloat();
			return ctx -> {
				if(ctx.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND) seismic = value;
			};
		}
	}

	/** Every block of the chunk, bottom to top, column by column, as upstream's three loops. */
	public static void collapseChunk(ServerLevel level, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for(int x = 0; x < 16; x++) {
			for(int z = 0; z < 16; z++) {
				for(int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
					p.set(x0 + x, y, z0 + z);
					BlockPos pos = p.immutable();
					tryCollapseBlock(level, pos, level.getBlockState(pos));
				}
			}
		}
	}

	public static void tryCollapseBlock(Level world, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		boolean canCollapse = true;
		if(block instanceof IRadResistantBlock resistant && resistant.isRadResistant(world, pos)) canCollapse = false;
		if(!canCollapse) return;

		if(world.random.nextInt(ODDS) == 0 && (world.isEmptyBlock(pos.below()) || !opaque(state, world, pos) || !fullCube(state, world, pos))) {
			SimulationData sim = StructuralIntegrityHandler.SERVER.handleBlock(world, pos, true, false, null);
			if(sim != null && sim.maxRatio >= 1) {
				if(!fullBlock(state, world, pos)) {
					world.destroyBlock(pos, true);
				} else if(!opaque(state, world, pos)) {
					world.destroyBlock(pos, true);
				} else {
					BlockPos newPos = fallTo(world, pos);
					if(!newPos.equals(pos)) {
						world.setBlock(newPos, state, 3);
						world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						world.playSound(null, newPos, state.getSoundType(world, newPos, null).getPlaceSound(), SoundSource.BLOCKS, 1, 1);
						if(world instanceof ServerLevel server) {
							TomImpactCollapsePacket packet = new TomImpactCollapsePacket();
							packet.x = newPos.getX();
							packet.y0 = pos.getY();
							packet.y1 = newPos.getY();
							packet.z = newPos.getZ();
							LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
							if(wire != null) wire.__sendToAllAround(server, pos, PACKET_RANGE);
						}
					}
				}
			}
		}
	}

	/** Down through air and anything not a solid full cube (which breaks and drops), to the first that is. */
	public static BlockPos fallTo(Level world, BlockPos pos) {
		BlockPos newPos = pos;
		for(boolean stop = false; !stop; ) {
			BlockPos downPos = newPos.below();
			stop = true;
			if(!world.hasChunkAt(downPos)) break;
			if(world.isOutsideBuildHeight(downPos)) break;
			if(world.isEmptyBlock(downPos)) {
				stop = false;
			} else {
				BlockState dstate = world.getBlockState(downPos);
				if(!opaque(dstate, world, downPos) || !fullBlock(dstate, world, downPos)) {
					stop = false;
					world.destroyBlock(downPos, true);
				}
			}
			if(!stop) newPos = downPos;
		}
		return newPos;
	}

	static boolean opaque(BlockState state, Level world, BlockPos pos) {
		return state.isSolidRender(world, pos);
	}

	static boolean fullCube(BlockState state, Level world, BlockPos pos) {
		return state.isCollisionShapeFullBlock(world, pos);
	}

	static boolean fullBlock(BlockState state, Level world, BlockPos pos) {
		return opaque(state, world, pos) && fullCube(state, world, pos);
	}

	/**
	 * A block fell from {@code y0} to {@code y1}: dust along its path. Upstream's packet, registered as
	 * {@code IMPACT_COLLAPSE}; the four ints in upstream's order.
	 */
	public static class TomImpactCollapsePacket implements LeafiaCustomPacketEncoder {
		public int x;
		public int y0;
		public int y1;
		public int z;

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeInt(x);
			buf.writeInt(y0);
			buf.writeInt(y1);
			buf.writeInt(z);
		}

		@Override
		public @Nullable Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			int x = buf.readInt();
			int y0 = buf.readInt();
			int y1 = buf.readInt();
			int z = buf.readInt();
			return ctx -> {
				if(ctx.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient())
					ClientOnly.execute(x, y0, y1, z);
			};
		}

		/**
		 * Upstream's {@code execute}: 27 particles of the block for every block it fell, each step's spread
		 * narrowed by the step. 1.12.2's {@code BLOCK_DUST} is the block-break particle with a velocity of its
		 * own, which is 1.21.1's {@code minecraft:block}.
		 */
		@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
		private static final class ClientOnly {
			static void execute(int x, int y0, int y1, int z) {
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				mc.execute(() -> {
					Level world = mc.level;
					if(world == null) return;
					BlockPos pos = new BlockPos(x, y1, z);
					BlockState state = world.getBlockState(pos);
					if(world.isEmptyBlock(pos)) {
						BlockPos pos0 = new BlockPos(x, y0, z);
						if(!world.isEmptyBlock(pos0)) state = world.getBlockState(pos0);
					}
					BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
					for(int i = 1; i <= (y0 - y1); i++) {
						for(int j2 = 0; j2 < 3; ++j2) {
							for(int k2 = 0; k2 < 3; ++k2) {
								for(int l2 = 0; l2 < 3; ++l2) {
									double d0 = ((double) j2 + 0.5D) / 3.0D;
									double d1 = ((double) k2 + 0.5D) / 3.0D;
									double d2 = ((double) l2 + 0.5D) / 3.0D;
									world.addParticle(particle, pos.getX() + d0, pos.getY() + i + d1, pos.getZ() + d2,
											(d0 - 0.5) / i, d1 - 0.75, (d2 - 0.5) / i);
								}
							}
						}
					}
				});
			}
		}
	}
}
