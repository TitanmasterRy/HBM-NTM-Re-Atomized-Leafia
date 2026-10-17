package com.leafia.overwrite_contents;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.bomb.Balefire;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.blocks.gas.BlockGasExplosive;
import com.hbm.blocks.gas.BlockGasMeltdown;
import com.hbm.blocks.gas.BlockGasRadon;
import com.hbm.blocks.gas.BlockGasRadonDense;
import com.hbm.blocks.gas.BlockGasRadonTomb;
import com.hbm.blocks.generic.BlockFallout;
import com.hbm.blocks.machine.rbmk.RBMKDebris;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.entity.projectile.EntityChemical;
import com.hbm.entity.projectile.EntityRBMKDebris;
import com.hbm.entity.projectile.EntityThrowableNT;
import com.hbm.entity.projectile.EntityZirnoxDebris;
import com.hbm.explosion.ExplosionBalefire;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.explosion.ExplosionSolinium;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.util.LegacyMaterial;
import com.hbm.world.WorldUtil;
import com.hbm.world.biome.NtmBiomes;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.bomb.balefire.AshBalefire;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;
import com.leafia.contents.effects.folkvangr.EntityNukeFolkvangr;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreckEntity;
import com.leafia.database.FolkvangrJammers;
import com.leafia.init.LeafiaEntities;
import com.leafia.savedata.FalloutSavedData;
import com.leafia.savedata.FalloutSavedData.FalloutData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the addon plugs into the base mod's explosion, corium and cloud seams (D-2197), all at once, from
 * the mod constructor. Not an upstream file: upstream reached each of these with a mixin into {@code com.hbm},
 * and D-1001 makes every one a seam consumer instead (D-3013).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code MixinBalefire}</td><td>{@link LeafiaBalefireFire}, a {@code Balefire} tick handler</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinExplosionBalefire}</td><td>{@link #BALEFIRE_COLUMN}</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinExplosionSolinium}</td><td>{@link #SOLINIUM_COLUMN}</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinExplosionNukeGeneric}</td><td>{@link #SOLINIUM_BLOCK}</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinCoriumFinite}</td><td>{@link LeafiaCoriumBehavior}, a corium behaviour</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinEntityChemical}</td><td>{@link #CONCRETE_IMPACT}, an impact hook</td><td>explosionVfx</td></tr>
 * <tr><td>{@code MixinBlockGasExplosive}</td><td>{@link #GAS_BLAST}</td><td>explosiveGasRebalance (off)</td></tr>
 * <tr><td>{@code MixinEntityNukeExplosionMK3}</td><td>{@link #MK3_TICK} and {@link #JAM_LISTENER}</td><td>folkvangr (the jammer note: none)</td></tr>
 * <tr><td>{@code MixinEntityCloudFleija}, {@code ...Rainbow}, {@code IMixinEntityCloudFleija}</td><td>{@link LeafiaFleijaClouds}</td><td>folkvangr</td></tr>
 * <tr><td>the meteor, missile, launch-pad, landmine and impact mixins (D-3022)</td><td>{@link LeafiaStrikeSeams}, installed from here</td><td>see there</td></tr>
 * </table>
 *
 * Each is registered whatever its switch says and reads the switch when it runs, so an off switch hands the
 * base mod its own behaviour back.
 */
public final class LeafiaExplosionSeams {

	private LeafiaExplosionSeams() {}

	// ------------------------------------------------------------------ MixinExplosionBalefire

	/**
	 * The balefire crater column, upstream's {@code @Overwrite} of {@code breakColumn}. The base mod's column
	 * starts at the surface and digs a bowl; this one:
	 *
	 * <ol>
	 * <li>starts five above the bomb ({@code posY + 5}) and walks down clearing anything replaceable, to the
	 *     first block that is not then air ({@code y});</li>
	 * <li>clears the air above, from {@code posY + 5} up to
	 *     {@code posY + 5 + 2.5 x maxdepth x cos(d / r x pi/2)^0.375 - nextInt(5) - 10} (exclusive), with
	 *     {@code maxdepth = (int)(10 + r / 4)} - a dome over the crater, highest at the centre;</li>
	 * <li>digs {@code (int)(maxdepth x a / r) + 2 sin(0.15 a + 2)} below {@code y}, with
	 *     {@code a = (int)(r - d)} - note {@code maxdepth x a / r} is integer division - stopping at a
	 *     schrabidium cluster (one in ten: balefire on it and it becomes euphemium) or reinforced CMB brick
	 *     (one in ten: balefire on it);</li>
	 * <li>one in ten, balefire on the floor, which becomes euphemium if it is a schrabidium cluster;</li>
	 * <li>and if the floor block is 1.7.10 rock, baleonite of level {@code min(5, (int)(6 a / r))}.</li>
	 * </ol>
	 *
	 * <p>Kept exactly: the rock loop is {@code for(i = 0..6a/r) { if(rock) set; break; }} - its {@code break}
	 * is outside the {@code if}, so only the floor itself is ever tested. Upstream's "down to 0" and "at
	 * least 0" are the level's floor, as the base mod's column has them (D-408). With {@code balefireResidue}
	 * off there is no baleonite and the floor is left as it is.
	 */
	public static final ExplosionBalefire.ColumnHandler BALEFIRE_COLUMN = LeafiaExplosionSeams::balefireColumn;

	public static boolean balefireColumn(ExplosionBalefire explosion, int x, int z) {
		if(!LeafiaFeatures.explosionVfx) return false;
		Level level = explosion.level;
		int radius = explosion.radius;
		double dist = Math.sqrt(x * x + z * z);
		int amplitude = (int) (radius - dist);

		if(amplitude > 0) {
			int pX = explosion.posX + x;
			int pZ = explosion.posZ + z;
			int floor = level.getMinBuildHeight();

			int y = explosion.posY + 5;
			int ystart = y;
			for(int y0 = y; y0 >= floor; y0--) {
				BlockPos p = new BlockPos(pX, y0, pZ);
				if(level.getBlockState(p).canBeReplaced()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				if(level.isEmptyBlock(p)) y = y0;
				else break;
			}
			int maxdepth = (int) (10 + radius * 0.25);
			int voidDepth = (int) ((maxdepth * amplitude / radius) + (Math.sin(amplitude * 0.15 + 2) * 2));

			int yend = (int) (ystart + maxdepth * 2.5 * Math.pow(Math.cos(dist / (double) radius * Math.PI / 2), 0.375)) - level.random.nextInt(5);
			for(int yc = ystart; yc < yend - 10; yc++)
				level.setBlock(new BlockPos(pX, yc, pZ), Blocks.AIR.defaultBlockState(), 2);
			int depth = Math.max(y - voidDepth, floor);

			while(y > depth) {
				BlockPos here = new BlockPos(pX, y, pZ);
				BlockState state = level.getBlockState(here);

				if(state.is(ModBlocks.block_schrabidium_cluster.get())) {
					if(level.random.nextInt(10) == 0) {
						level.setBlock(here.above(), ModBlocks.balefire.get().defaultBlockState(), 3);
						level.setBlock(here, euphemium(state), 3);
					}
					return true;
				} else if(state.is(ModBlocks.cmb_brick_reinforced.get())) {
					if(level.random.nextInt(10) == 0) {
						level.setBlock(here.above(), ModBlocks.balefire.get().defaultBlockState(), 3);
					}
					return true;
				}

				level.setBlock(here, Blocks.AIR.defaultBlockState(), 3);
				y--;
			}

			if(level.random.nextInt(10) == 0) {
				level.setBlock(new BlockPos(pX, depth + 1, pZ), ModBlocks.balefire.get().defaultBlockState(), 3);
				BlockPos bottom = new BlockPos(pX, y, pZ);
				BlockState state = level.getBlockState(bottom);
				if(state.is(ModBlocks.block_schrabidium_cluster.get()))
					level.setBlock(bottom, euphemium(state), 3);
			}
			int startDepth = (int) (6 * amplitude / radius);
			for(int i = 0; i <= startDepth; i++) {
				BlockPos slice = new BlockPos(pX, depth - i, pZ);
				if(LegacyMaterial.of(level.getBlockState(slice)) == LegacyMaterial.ROCK && LeafiaBlocks.baleonite != null)
					level.setBlock(slice, LeafiaBlocks.baleonite.get().withMeta(Math.min(5, startDepth - i)), 3);
				break;
			}
		}
		return true;
	}

	/** {@code getStateFromMeta(getMetaFromState(...))} across: the cluster's axis carries over. */
	static BlockState euphemium(BlockState schrabidium) {
		BlockState euphemium = ModBlocks.block_euphemium_cluster.get().defaultBlockState();
		if(schrabidium.hasProperty(RotatedPillarBlock.AXIS))
			euphemium = euphemium.setValue(RotatedPillarBlock.AXIS, schrabidium.getValue(RotatedPillarBlock.AXIS));
		return euphemium;
	}

	// ------------------------------------------------------------------ MixinExplosionSolinium

	/**
	 * The Solinium rinse column, upstream's {@code @Overwrite} "to add biome change": every column first expires
	 * every fallout-weather record whose radius reaches the bomb ({@code (posX, 0, posZ)}, measured in three
	 * dimensions as upstream does), then rinses as the base mod does, then - inside the sphere - puts back the
	 * biome the generator would have made wherever the column stands in a crater biome, and resends the chunk's
	 * biomes. The records are only ever there with {@code overhauls.falloutRain} on.
	 */
	public static final ExplosionSolinium.ColumnHandler SOLINIUM_COLUMN = LeafiaExplosionSeams::soliniumColumn;

	public static boolean soliniumColumn(ExplosionSolinium explosion, int x, int z) {
		if(!LeafiaFeatures.explosionVfx) return false;
		if(!(explosion.worldObj instanceof ServerLevel level)) return false;

		Vec3 centre = new Vec3(explosion.posX, 0, explosion.posZ);
		for(FalloutData map : FalloutSavedData.forLevel(level).falloutMap) {
			if(map.pos.distanceTo(centre) <= map.radius) map.expire();
		}

		explosion.breakColumnDefault(x, z);

		int dist = explosion.radius2 - (x * x + z * z);
		if(dist > 0) {
			BlockPos pos = new BlockPos(explosion.posX + x, explosion.posY, explosion.posZ + z);
			if(isCrater(biomeAt(level, pos))) {
				Holder<Biome> original = generated(level, pos);
				WorldUtil.setBiome(level, pos.getX(), pos.getZ(), original);
				WorldUtil.syncBiomeChange(level, pos.getX(), pos.getZ());
			}
		}
		return true;
	}

	/**
	 * The biome of the cell holding {@code pos}, exactly. 1.12.2's {@code getBiome} read one column; 1.21.1's
	 * {@code Level.getBiome} blurs between neighbouring cells, which would answer for a column next door.
	 */
	public static Holder<Biome> biomeAt(Level level, BlockPos pos) {
		return level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
	}

	/** {@code instanceof BiomeGenCraterBase}: the base mod's three crater biomes. */
	public static boolean isCrater(Holder<Biome> biome) {
		return biome.is(NtmBiomes.CRATER) || biome.is(NtmBiomes.CRATER_INNER) || biome.is(NtmBiomes.CRATER_OUTER);
	}

	/** {@code getBiomeProvider().getBiome(pos)}: what the generator puts at {@code pos}. */
	public static Holder<Biome> generated(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
				QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()),
				level.getChunkSource().randomState().sampler());
	}

	// ------------------------------------------------------------------ MixinExplosionNukeGeneric

	/**
	 * What the Solinium rinse does to the addon's blocks and to some of the base mod's, upstream's injection into
	 * {@code ExplosionNukeGeneric.solinium} just after the state is read. "Replaces" answers {@code true}, so the
	 * base rule is skipped; "and on" answers {@code false}, so the base rule then runs on the state as it was
	 * read, as upstream's carried on with its stale local.
	 *
	 * <ul>
	 * <li>contaminated dirt and scorched grass: coarse dirt two times in five, dirt otherwise (replaces);</li>
	 * <li>contaminated gravel, ice, sand, sandstone, red sand, red sandstone, thin snow, snow block and
	 *     terracotta: the vanilla block each was (replaces);</li>
	 * <li>any corium pool, the corium block, corium cobble and sellafite: slaked sellafite (replaces);</li>
	 * <li>radon, dense radon, tomb radon and meltdown gas: air (replaces);</li>
	 * <li>any RBMK debris but plain and digamma debris: plain debris (replaces);</li>
	 * <li>a PWR wreck: scorch 0, and on;</li>
	 * <li>fallout and balefire ash: air, and on; balefire: fire, and on; baleonite: slaked sellafite, and on.</li>
	 * </ul>
	 *
	 * <p>The coarse-dirt roll is upstream's {@code ExplosionNukeGeneric.random}, here the level's.
	 */
	public static final ExplosionNukeGeneric.SoliniumHandler SOLINIUM_BLOCK = LeafiaExplosionSeams::soliniumBlock;

	public static boolean soliniumBlock(Level level, BlockPos pos, BlockState state) {
		if(!LeafiaFeatures.explosionVfx) return false;
		return soliniumBlock(level, pos, state, level.random);
	}

	public static boolean soliniumBlock(Level level, BlockPos pos, BlockState state, RandomSource random) {
		Block b = state.getBlock();

		if(is(b, LeafiaBlocks.waste_dirt) || is(b, LeafiaBlocks.scorched_earth)) {
			if(random.nextInt(5) < 2) level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
			else level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			return true;
		}
		BlockState vanilla = vanillaOf(b);
		if(vanilla != null) {
			level.setBlockAndUpdate(pos, vanilla);
			return true;
		}
		if(b instanceof CoriumFinite) {
			level.setBlockAndUpdate(pos, ModBlocks.sellafield_slaked.get().defaultBlockState());
			return true;
		}
		if(state.is(ModBlocks.block_corium.get()) || state.is(ModBlocks.block_corium_cobble.get()) || state.is(ModBlocks.sellafield.get())) {
			level.setBlockAndUpdate(pos, ModBlocks.sellafield_slaked.get().defaultBlockState());
			return true;
		}
		if(b instanceof BlockGasRadon || b instanceof BlockGasRadonDense || b instanceof BlockGasRadonTomb || b instanceof BlockGasMeltdown) {
			level.removeBlock(pos, false);
			return true;
		}
		if(b != ModBlocks.pribris.get() && b != ModBlocks.pribris_digamma.get() && b instanceof RBMKDebris) {
			level.setBlockAndUpdate(pos, ModBlocks.pribris.get().defaultBlockState());
			return true;
		}
		if(b instanceof PWRMeshedWreck) {
			if(level.getBlockEntity(pos) instanceof PWRMeshedWreckEntity te) {
				te.scorch = 0;
				te.sync();
			}
		}
		if(b instanceof BlockFallout) level.removeBlock(pos, false);
		if(b instanceof AshBalefire) level.removeBlock(pos, false);
		if(b instanceof Balefire) level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
		if(b instanceof BaleoniteBlock) level.setBlockAndUpdate(pos, ModBlocks.sellafield_slaked.get().defaultBlockState());
		return false;
	}

	static boolean is(Block b, net.neoforged.neoforge.registries.DeferredBlock<?> holder) {
		return holder != null && b == holder.get();
	}

	/** The nine contamination blocks that rinse back to one vanilla block each; null for anything else. */
	static BlockState vanillaOf(Block b) {
		if(is(b, LeafiaBlocks.waste_gravel)) return Blocks.GRAVEL.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_ice)) return Blocks.ICE.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_sand)) return Blocks.SAND.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_sandstone)) return Blocks.SANDSTONE.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_sand_red)) return Blocks.RED_SAND.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_red_sandstone)) return Blocks.RED_SANDSTONE.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_snow)) return Blocks.SNOW.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_snow_block)) return Blocks.SNOW_BLOCK.defaultBlockState();
		if(is(b, LeafiaBlocks.waste_terracotta)) return Blocks.TERRACOTTA.defaultBlockState();
		return null;
	}

	// ------------------------------------------------------------------ MixinBlockGasExplosive

	/** Upstream's redirect of {@code combust}'s blast: strength 2, flaming, breaking blocks. */
	public static final float GAS_STRENGTH = 2F;

	public static final BlockGasExplosive.BlastHandler GAS_BLAST = LeafiaExplosionSeams::gasBlast;

	public static boolean gasBlast(BlockGasExplosive block, Level level, BlockPos pos) {
		if(!LeafiaFeatures.explosiveGasRebalance) return false;
		level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, GAS_STRENGTH, true, Level.ExplosionInteraction.BLOCK);
		return true;
	}

	// ------------------------------------------------------------------ MixinEntityChemical

	/**
	 * A chemical thrower's concrete that hits a block sets as the addon's concrete fluid in the replaceable block
	 * it is in (its own position, not the hit block's) and the shot ends; anything else is the base mod's. With
	 * {@code worldFluids} off there is no concrete fluid and nothing happens.
	 */
	public static final EntityThrowableNT.ImpactHook CONCRETE_IMPACT = new EntityThrowableNT.ImpactHook() {
		@Override
		public void after(EntityThrowableNT projectile, HitResult hit) {
			concreteImpact(projectile, hit);
		}
	};

	public static void concreteImpact(EntityThrowableNT projectile, HitResult hit) {
		if(!LeafiaFeatures.explosionVfx) return;
		if(!(projectile instanceof EntityChemical chemical) || chemical.getFluidType() != Fluids.CONCRETE) return;
		if(hit.getType() != HitResult.Type.BLOCK) return;
		if(LeafiaLiquids.concrete == null) return;
		Level level = projectile.level();
		BlockPos bp = projectile.blockPosition();
		if(level.getBlockState(bp).canBeReplaced()) {
			level.setBlockAndUpdate(bp, LeafiaLiquids.concrete.block.get().defaultBlockState());
			projectile.discard();
		}
	}

	// ------------------------------------------------------------------ MixinEntityNukeExplosionMK3

	/**
	 * Upstream's head injection into the MK3's tick. A F.L.E.I.J.A. blast ({@code extType 0}, not in waste
	 * mode) that has not started becomes a Folkvangr where it stands and is removed - so the sphere it would
	 * have carved is never carved by it, and the Folkvangr bound to its cloud erases one instead. A Solinium
	 * blast ({@code extType 1}) carries on as the base mod's, but every tick first removes the RBMK fuel debris
	 * and ZIRNOX fuel element debris within its age in blocks. Upstream gates both on
	 * {@code CompatibilityConfig.isWarDim}, which the base mod does not have and which is every dimension by
	 * default.
	 */
	public static final EntityNukeExplosionMK3.TickHook MK3_TICK = LeafiaExplosionSeams::mk3Tick;

	public static boolean mk3Tick(EntityNukeExplosionMK3 blast) {
		if(!LeafiaFeatures.folkvangr || LeafiaEntities.NUKE_FOLKVANGR == null) return false;
		Level level = blast.level();
		if(!blast.did && !blast.waste && blast.extType == 0) {
			blast.baseTick();
			EntityNukeFolkvangr folkvangr = new EntityNukeFolkvangr(LeafiaEntities.NUKE_FOLKVANGR.get(), level, blast.position(), null);
			level.addFreshEntity(folkvangr);
			blast.discard();
			return true;
		}
		if(blast.extType == 1) {
			sweepDebris(level, blast.position(), blast.age);
		}
		return false;
	}

	/** RBMK fuel debris and ZIRNOX fuel elements within {@code radius} of {@code centre} are removed. */
	public static void sweepDebris(Level level, Vec3 centre, double radius) {
		AABB box = new AABB(centre.x - radius, centre.y - radius, centre.z - radius, centre.x + radius, centre.y + radius, centre.z + radius);
		for(Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
			if(centre.distanceTo(entity.position()) > radius) continue;
			if(entity instanceof EntityRBMKDebris rbmk) {
				if(rbmk.getDebrisType() == EntityRBMKDebris.DebrisType.FUEL) entity.discard();
			} else if(entity instanceof EntityZirnoxDebris zirnox) {
				if(zirnox.getDebrisType() == EntityZirnoxDebris.DebrisType.ELEMENT) entity.discard();
			}
		}
	}

	/**
	 * Upstream's injection into {@code isJammed}: where the field disturber that cancelled a F.L.E.I.J.A. is,
	 * for the DFC's collapse to read ({@link FolkvangrJammers}). Not behind a switch: upstream's has none and it
	 * changes nothing the base mod does.
	 */
	public static final EntityNukeExplosionMK3.JamListener JAM_LISTENER =
			(level, blast, jammer) -> FolkvangrJammers.lastDetectedJammer = new BlockPos(jammer.x(), jammer.y(), jammer.z());

	// ------------------------------------------------------------------ install

	private static boolean installed = false;

	public static void install() {
		// The meteor, missile, launch-pad, landmine and impact seams (D-3022), installed with these.
		LeafiaStrikeSeams.install();
		if(installed) return;
		installed = true;
		Balefire.addTickHandler(LeafiaBalefireFire.HANDLER);
		ExplosionBalefire.addColumnHandler(BALEFIRE_COLUMN);
		ExplosionSolinium.addColumnHandler(SOLINIUM_COLUMN);
		ExplosionNukeGeneric.addSoliniumHandler(SOLINIUM_BLOCK);
		CoriumFinite.addBehavior(LeafiaCoriumBehavior.INSTANCE);
		BlockGasExplosive.addBlastHandler(GAS_BLAST);
		EntityThrowableNT.addImpactHook(CONCRETE_IMPACT);
		EntityNukeExplosionMK3.addTickHook(MK3_TICK);
		EntityNukeExplosionMK3.addJamListener(JAM_LISTENER);
		LeafiaFleijaClouds.install();
	}
}
