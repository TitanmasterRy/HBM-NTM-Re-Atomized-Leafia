package com.custom_hbm.explosion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The addon's own copy of NTM-CE's {@code ExplosionNT}, with four knobs the base one does not have, and
 * a fifth attribute that turns a blast into a landslide.
 *
 * <p>Upstream: {@code com/custom_hbm/explosion/LCEExplosionNT.java}. Kept in {@code com.custom_hbm},
 * which the base mod does not have (D-3000's reading for {@code EffectNT}). Its consumers are the
 * addon's Dark Fusion Core (three blasts) and, later, the APR and the PWR's wreck.
 *
 * <h2>What it adds to the base mod's {@code ExplosionNT}</h2>
 *
 * <ul>
 * <li>{@link #ignoreBlockPoses}: cells a ray passes through as if they were not there - the core blows
 *     up around itself. The ray still pays its 0.3 x 0.75 a step for the cell; only the block's own
 *     resistance and its destruction are skipped, and the skipped step does not count toward the limit.</li>
 * <li>{@link #iterationLimit}: how many steps a ray may take ({@code -1}, unlimited, by default).</li>
 * <li>{@link #maxExplosionResistance}: a ray that meets a block whose resistance is above
 *     {@code max x 3/5} stops dead. The 3/5 is 1.12.2's resistance scaling, which this port folds into
 *     a block's own number (D-969), so it compares like with like.</li>
 * <li>{@link LCEExAttrib#DFC_FALL}: <b>the blast destroys nothing</b>. Every block a ray reaches with
 *     power left while one of the two cells before it was air becomes a candidate; a candidate falls as
 *     a falling block (four in five) if it is a solid visible cube and the column under it, down to a
 *     replaceable block, is all candidates too; anything else is deleted. A block entity is deleted
 *     rather than dropped. Nothing is dropped as an item. This is how the DFC's final phase makes the
 *     surface of its crater slide in.</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * The same as the base mod's {@code ExplosionNT} (its class note), because this is the same algorithm:
 * a real vanilla {@link Explosion} is built only so the block hooks have something to read; resistance
 * is {@code state.getExplosionResistance}; removal is {@code onBlockExploded}; drops are
 * {@code popResource} at {@code 1 / size} unless {@code ALLDROP}; {@code isNormalCube()} is
 * {@code isCollisionShapeFullBlock}; the fire check under a cell is {@code isSolidRender}; blast
 * protection is the knockback attribute. {@code LeafiaUtil.isSolidVisibleCube} stands in for 1.12.2's
 * "full cube, solid, not replaceable, model render type".
 *
 * <p><b>The particles are the client's.</b> Upstream runs the block pass on the server and then sends
 * {@link ExplosionNTSyncPacket} to everyone within 64 blocks, whose client runs the same pass again,
 * where the only thing that does anything is the particles - the big one at the centre and, unless
 * {@code DFC_FALL}, up to 3,000 puffs scattered through the sphere. That is kept: one packet, the
 * client draws. Upstream's {@code CompatibilityConfig.isWarDim} gate is NTM-CE's and is not in the
 * base mod, so every dimension allows the blast.
 *
 * <p>One upstream oddity kept: the entity box is built from six numbers whose names are shuffled
 * ({@code endY} holds the maximum x), but the box comes out right.
 */
public class LCEExplosionNT {

	public Set<LCEExAttrib> attributes = new HashSet<>();

	private final RandomSource explosionRNG = RandomSource.create();
	private final Level worldObj;
	protected int resolution = 16;
	protected Map<Player, Vec3> affectedEntities = new HashMap<>();
	public float explosionSize;
	public double explosionX;
	public double explosionY;
	public double explosionZ;
	@Nullable
	public Entity exploder;
	public int iterationLimit = -1;
	public int affectedBlockCount;

	public float maxExplosionResistance = -1;
	/** The cells this explosion destroys, gathered by the first pass. */
	public final List<BlockPos> affectedBlockPositions;

	public final List<BlockPos> ignoreBlockPoses = new ArrayList<>();

	@Nullable
	public List<BlockPos> fallBlocks;

	public static final List<LCEExAttrib> nukeAttribs = Arrays.asList(LCEExAttrib.FIRE, LCEExAttrib.NOPARTICLE, LCEExAttrib.NOSOUND, LCEExAttrib.NODROP, LCEExAttrib.NOHURT);

	private final Explosion vanillaExplosion;

	public LCEExplosionNT(Level world, @Nullable Entity exploder, double x, double y, double z, float strength, List<BlockPos> affected, int count) {
		this.worldObj = world;
		this.explosionSize = strength;
		this.explosionX = x;
		this.explosionY = y;
		this.explosionZ = z;
		this.exploder = exploder;
		this.affectedBlockPositions = affected;
		this.affectedBlockCount = count;
		this.vanillaExplosion = new Explosion(world, exploder, x, y, z, strength, false, Explosion.BlockInteraction.DESTROY);
	}

	public LCEExplosionNT(Level world, @Nullable Entity exploder, double x, double y, double z, float strength) {
		this(world, exploder, x, y, z, strength, new ArrayList<>(), 0);
	}

	public LCEExplosionNT addAttrib(LCEExAttrib attrib) {
		attributes.add(attrib);
		return this;
	}

	public LCEExplosionNT addAllAttrib(List<LCEExAttrib> attrib) {
		attributes.addAll(attrib);
		return this;
	}

	public LCEExplosionNT overrideResolution(int res) {
		resolution = res;
		return this;
	}

	public int getResolution() {
		return resolution;
	}

	public void explode() {
		doNTExplosionA();
		doNTExplosionB();
		if(worldObj instanceof ServerLevel server) {
			ExplosionNTSyncPacket packet = new ExplosionNTSyncPacket();
			packet.nt = this;
			LeafiaCustomPacket wire = LeafiaCustomPacket.__start(packet);
			if(wire != null) {
				double rangeSq = 4096; // upstream's sqrt(4096) = 64 blocks
				for(ServerPlayer player : server.players()) {
					if(player.distanceToSqr(explosionX, explosionY, explosionZ) > rangeSq) continue;
					if(player.connection != null && player.connection.hasChannel(LeafiaCustomPacket.TYPE)) wire.__sendToClient(player);
				}
			}
		}
	}

	/** The rays: what is destroyed, what falls, and (unless {@code NOHURT}) what is hurt. */
	public void doNTExplosionA() {
		float f = this.explosionSize;
		Set<BlockPos> hashset = new LinkedHashSet<>();
		Set<BlockPos> hashset2 = has(LCEExAttrib.DFC_FALL) ? new LinkedHashSet<>() : null;

		for(int endX = 0; endX < this.resolution; ++endX) {
			for(int endY = 0; endY < this.resolution; ++endY) {
				for(int endZ = 0; endZ < this.resolution; ++endZ) {
					// basically hollow box
					if(endX == 0 || endX == this.resolution - 1 || endY == 0 || endY == this.resolution - 1 || endZ == 0 || endZ == this.resolution - 1) {
						double ratioX = (double) ((float) endX / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double ratioY = (double) ((float) endY / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double ratioZ = (double) ((float) endZ / ((float) this.resolution - 1.0F) * 2.0F - 1.0F);
						double distance = Math.sqrt(ratioX * ratioX + ratioY * ratioY + ratioZ * ratioZ);
						ratioX /= distance;
						ratioY /= distance;
						ratioZ /= distance;
						float power = this.explosionSize * (0.7F + this.worldObj.random.nextFloat() * 0.6F);
						double curX = this.explosionX;
						double curY = this.explosionY;
						double curZ = this.explosionZ;

						BlockState lastBlock = Blocks.AIR.defaultBlockState();
						BlockState lastBlock2 = Blocks.AIR.defaultBlockState();
						int i = 0;
						for(float weaken = 0.3F; power > 0.0F; power -= weaken * 0.75F) {
							BlockPos pos = new BlockPos(Mth.floor(curX), Mth.floor(curY), Mth.floor(curZ));
							curX += ratioX * (double) weaken;
							curY += ratioY * (double) weaken;
							curZ += ratioZ * (double) weaken;
							if(ignoreBlockPoses.contains(pos))
								continue;
							i++;
							if(i > iterationLimit && iterationLimit >= 0)
								break;

							BlockState block = this.worldObj.getBlockState(pos);

							if(!block.isAir()) {
								float resistance = block.getExplosionResistance(worldObj, pos, vanillaExplosion);
								if(maxExplosionResistance >= 0 && resistance > maxExplosionResistance * 3f / 5f)
									power = 0;
								power -= (resistance + 0.3F) * weaken;
								if(hashset2 != null && power > 0) {
									if(lastBlock.isAir() || lastBlock2.isAir()) {
										hashset2.add(pos);
									} else {
										lastBlock2 = lastBlock;
										lastBlock = block;
										continue;
									}
								}
							}

							if(power > 0.0F) {
								hashset.add(pos);
							}

							lastBlock2 = lastBlock;
							lastBlock = block;
						}
					}
				}
			}
		}

		this.affectedBlockPositions.addAll(hashset);
		if(hashset2 != null) {
			fallBlocks = new ArrayList<>(hashset2);
		}

		if(!has(LCEExAttrib.NOHURT)) {

			this.explosionSize *= 2.0F;
			int endX = Mth.floor(this.explosionX - (double) this.explosionSize - 1.0D);
			int endY = Mth.floor(this.explosionX + (double) this.explosionSize + 1.0D);
			int endZ = Mth.floor(this.explosionY - (double) this.explosionSize - 1.0D);
			int i2 = Mth.floor(this.explosionY + (double) this.explosionSize + 1.0D);
			int l = Mth.floor(this.explosionZ - (double) this.explosionSize - 1.0D);
			int j2 = Mth.floor(this.explosionZ + (double) this.explosionSize + 1.0D);
			List<Entity> list = this.worldObj.getEntities(this.exploder, new AABB(endX, endZ, l, endY, i2, j2));
			Vec3 vec3 = new Vec3(this.explosionX, this.explosionY, this.explosionZ);
			DamageSource source = worldObj.damageSources().explosion(exploder, getExplosivePlacedBy());

			for(Entity entity : list) {
				double d4 = Math.sqrt(entity.distanceToSqr(this.explosionX, this.explosionY, this.explosionZ)) / (double) this.explosionSize;

				if(d4 <= 1.0D) {
					double dx = entity.getX() - this.explosionX;
					double dy = entity.getEyeY() - this.explosionY;
					double dz = entity.getZ() - this.explosionZ;
					double d9 = Math.sqrt(dx * dx + dy * dy + dz * dz);

					if(d9 != 0.0D) {
						dx /= d9;
						dy /= d9;
						dz /= d9;
						double d10 = Explosion.getSeenPercent(vec3, entity);
						double d11 = (1.0D - d4) * d10;
						entity.hurt(source, (float) ((int) ((d11 * d11 + d11) / 2.0D * 8.0D * (double) this.explosionSize + 1.0D)));
						double d8 = d11;
						if(entity instanceof LivingEntity living)
							d8 = d11 * (1.0D - living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
						entity.setDeltaMovement(entity.getDeltaMovement().add(dx * d8, dy * d8, dz * d8));

						if(entity instanceof Player player) {
							this.affectedEntities.put(player, new Vec3(dx * d11, dy * d11, dz * d11));
						}
					}
				}
			}

			this.explosionSize = f;
		}
	}

	/** The server's half of the second pass: the sound, the landslide, the destruction and the fire. */
	public void doNTExplosionB() {
		if(worldObj.isClientSide) return;

		if(!has(LCEExAttrib.NOSOUND))
			this.worldObj.playSound(null, this.explosionX, this.explosionY, this.explosionZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
					(1.0F + (this.worldObj.random.nextFloat() - this.worldObj.random.nextFloat()) * 0.2F) * 0.7F);

		if(has(LCEExAttrib.DFC_FALL) && fallBlocks != null) {
			for(BlockPos pos : fallBlocks) {
				BlockState state = worldObj.getBlockState(pos);
				boolean destroy = false;
				boolean canFall = true;
				BlockPos checkPos = pos;
				while(true) {
					if(!fallBlocks.contains(checkPos)) {
						canFall = false;
						break;
					}
					if(worldObj.getBlockState(checkPos.below()).canBeReplaced())
						break;
					else
						checkPos = checkPos.below();
				}
				if(canFall) {
					Block bluk = state.getBlock();
					if(LeafiaUtil.isSolidVisibleCube(state, worldObj, pos)) {
						if(bluk instanceof EntityBlock)
							destroy = true;
						else {
							if(worldObj.random.nextInt(5) > 0) {
								FallingBlockEntity fallingBlock = FallingBlockEntity.fall(worldObj, pos, state);
								fallingBlock.time = 1;
							} else
								destroy = true;
						}
					} else destroy = true;
				} else
					destroy = true;
				if(destroy)
					worldObj.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		if(!has(LCEExAttrib.DFC_FALL)) {
			for(BlockPos chunkposition : this.affectedBlockPositions) {
				BlockState block = this.worldObj.getBlockState(chunkposition);
				if(block.isAir()) continue;

				if(block.canDropFromExplosion(worldObj, chunkposition, vanillaExplosion) && !has(LCEExAttrib.NODROP)) {
					float chance = has(LCEExAttrib.ALLDROP) ? 1.0F : 1.0F / this.explosionSize;
					if(worldObj.random.nextFloat() <= chance)
						Block.popResource(worldObj, chunkposition, new ItemStack(block.getBlock()));
				}

				boolean normalCube = block.isCollisionShapeFullBlock(worldObj, chunkposition);
				block.onBlockExploded(this.worldObj, chunkposition, vanillaExplosion);

				if(normalCube) {
					int i = chunkposition.getX();
					int j = chunkposition.getY();
					int k = chunkposition.getZ();
					if(has(LCEExAttrib.DIGAMMA)) {
						this.worldObj.setBlock(chunkposition, ModBlocks.ash_digamma.get().defaultBlockState(), 3);
						if(this.explosionRNG.nextInt(5) == 0 && this.worldObj.getBlockState(chunkposition.above()).isAir())
							this.worldObj.setBlock(chunkposition.above(), ModBlocks.fire_digamma.get().defaultBlockState(), 3);
					} else if(has(LCEExAttrib.DIGAMMA_CIRCUIT)) {
						if(i % 3 == 0 && k % 3 == 0) {
							this.worldObj.setBlock(chunkposition, ModBlocks.pribris_digamma.get().defaultBlockState(), 3);
						} else if((i % 3 == 0 || k % 3 == 0) && this.explosionRNG.nextBoolean()) {
							this.worldObj.setBlock(chunkposition, ModBlocks.pribris_digamma.get().defaultBlockState(), 3);
						} else {
							this.worldObj.setBlock(chunkposition, ModBlocks.ash_digamma.get().defaultBlockState(), 3);
							if(this.explosionRNG.nextInt(5) == 0 && this.worldObj.getBlockState(new BlockPos(i, j + 1, k)).isAir())
								this.worldObj.setBlock(new BlockPos(i, j + 1, k), ModBlocks.fire_digamma.get().defaultBlockState(), 3);
						}
					} else if(has(LCEExAttrib.LAVA_V)) {
						this.worldObj.setBlock(chunkposition, ModBlocks.volcanic_lava_block.get().defaultBlockState(), 3);
					}
				}
			}
			if(has(LCEExAttrib.FIRE) || has(LCEExAttrib.BALEFIRE) || has(LCEExAttrib.LAVA)) {
				for(BlockPos chunkposition : this.affectedBlockPositions) {
					BlockState block = this.worldObj.getBlockState(chunkposition);
					BlockState block1 = this.worldObj.getBlockState(chunkposition.below());

					boolean shouldReplace = true;
					if(!has(LCEExAttrib.ALLMOD))
						shouldReplace = this.explosionRNG.nextInt(3) == 0;

					if(block.isAir() && block1.isSolidRender(worldObj, chunkposition.below()) && shouldReplace) {
						if(has(LCEExAttrib.FIRE))
							this.worldObj.setBlock(chunkposition, Blocks.FIRE.defaultBlockState(), 3);
						else if(has(LCEExAttrib.BALEFIRE))
							this.worldObj.setBlock(chunkposition, ModBlocks.balefire.get().defaultBlockState(), 3);
						else if(has(LCEExAttrib.LAVA))
							this.worldObj.setBlock(chunkposition, Blocks.LAVA.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	public Map<Player, Vec3> getAffectedEntities() {
		return this.affectedEntities;
	}

	@Nullable
	public LivingEntity getExplosivePlacedBy() {
		if(exploder == null) return null;
		if(exploder instanceof PrimedTnt tnt) return tnt.getOwner();
		if(exploder instanceof LivingEntity living) return living;
		return null;
	}

	// unconventional name, sure, but it's short
	public boolean has(LCEExAttrib attrib) {
		return this.attributes.contains(attrib);
	}

	// this solution is a bit hacky but in the end easier to work with
	public enum LCEExAttrib {
		FIRE,		//classic vanilla fire explosion
		BALEFIRE,	//same with but with balefire
		DIGAMMA,
		DIGAMMA_CIRCUIT,
		LAVA,		//again the same thing but lava
		LAVA_V,		//again the same thing but volcaniclava
		ALLMOD,		//block placer attributes like fire are applied for all destroyed blocks
		ALLDROP,	//miner TNT!
		NODROP,		//the opposite
		NOPARTICLE,
		NOSOUND,
		NOHURT,
		DFC_FALL
	}

	/**
	 * Server to client: where, how big, how many blocks went, and which attributes - so the client can
	 * draw the particles. Upstream's bytes: three doubles, a float, two ints, then one byte per attribute.
	 */
	public static class ExplosionNTSyncPacket implements LeafiaCustomPacketEncoder {
		LCEExplosionNT nt;

		@Override
		public void encode(LeafiaBuf buf) {
			buf.writeDouble(nt.explosionX);
			buf.writeDouble(nt.explosionY);
			buf.writeDouble(nt.explosionZ);
			buf.writeFloat(nt.explosionSize);
			buf.writeInt(nt.affectedBlockPositions.size());
			buf.writeInt(nt.attributes.size());
			for(LCEExAttrib a : nt.attributes)
				buf.writeByte(a.ordinal());
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			double x = buf.readDouble();
			double y = buf.readDouble();
			double z = buf.readDouble();
			float size = buf.readFloat();
			int affecteds = buf.readInt();
			int attribs = buf.readInt();
			List<LCEExAttrib> attrib = new ArrayList<>(attribs);
			for(int i = 0; i < attribs; i++)
				attrib.add(LCEExAttrib.values()[buf.readByte()]);
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient())
					com.leafia.client.LeafiaDFCClient.explosionParticles(x, y, z, size, affecteds, attrib.contains(LCEExAttrib.NOPARTICLE), attrib.contains(LCEExAttrib.DFC_FALL));
			};
		}
	}
}
