package com.leafia.overwrite_contents;

import java.util.function.Consumer;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityCloudSolinium;
import com.hbm.entity.grenade.EntityGrenadeUniversal;
import com.hbm.entity.logic.EntityNukeExplosionMK3;
import com.hbm.items.weapon.grenade.ItemGrenadeFilling.EnumGrenadeFilling;
import com.hbm.items.weapon.grenade.ItemGrenadeShell.EnumGrenadeShell;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.miscellanous.slop.SlopTE;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * The addon's two grenade fillings, appended to the base mod's {@code EnumGrenadeFilling} by NeoForge's enum extension (D-2205's
 * seam, D-3028): {@code LEAFIA_NULL} for the fragmentation shell and {@code LEAFIA_SOL} for the nuclear shell.
 *
 * <p>Upstream: {@code MixinEnumGrenadeFilling}, which rewrote the enum's {@code $VALUES} in its static initialiser to append
 * {@code NULL} and {@code SOL} and patched {@code valueOf} to find them. D-1001 rules out a mixin into {@code com.hbm}; the base enum
 * implements {@code IExtensibleEnum} now, so the addon lists the two constants in {@code META-INF/enumextensions.json} (declared by
 * the addon's {@code neoforge.mods.toml}), which points at the two {@link EnumProxy} fields below for the constructor's arguments.
 * The loader requires a constant's name to start with the mod id, so upstream's {@code NULL} and {@code SOL} are {@code LEAFIA_NULL}
 * and {@code LEAFIA_SOL}, and their translation keys {@code item.hbm.grenade_filling.leafia_null} and {@code .leafia_sol}.
 *
 * <p>The two explosions are upstream's, number for number:
 * <ul>
 * <li><b>null</b>: every block of the 7x7x7 cube centred on the grenade's block becomes air (flags 18, no drops, no neighbour
 *     shape updates), and everything in the box from three below to four above that block is killed outright ({@code SlopTE.tryKill});
 *     colours {@code 0xc2c2c2} and {@code 0x383838};</li>
 * <li><b>solinium</b>: the generic explosion sound at volume 10, an {@code EntityNukeExplosionMK3} of range 25 at
 *     {@code BombConfig.blastSpeed} with coefficient 1, no waste and {@code extType} 1 (the solinium rinse), and a solinium cloud of
 *     25; colours {@code 0x114eaf} and {@code 0x000000}.</li>
 * </ul>
 *
 * <p>The constants exist whether or not {@code features.leafiaGrenades} is on, because the extension is applied as the base enum
 * loads, before any config. With the switch off {@link #install} empties both constants' shell sets, so no shell takes them: they
 * are not buildable, not in the creative tab or JEI, and no recipe makes one. Their items are not registered either.
 */
public final class LeafiaGrenadeFillings {

	private LeafiaGrenadeFillings() {}

	public static final Consumer<EntityGrenadeUniversal> EXPLODE_NULL = grenade -> {
		Level world = grenade.level();
		BlockPos pos = BlockPos.containing(grenade.getX(), grenade.getY(), grenade.getZ());
		for(int x = -3; x <= 3; x++) {
			for(int y = -3; y <= 3; y++) {
				for(int z = -3; z <= 3; z++)
					world.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 18);
			}
		}
		AABB aabb = new AABB(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
		for(Entity entity : world.getEntities((Entity) null, aabb))
			SlopTE.tryKill(entity);
	};

	public static final Consumer<EntityGrenadeUniversal> EXPLODE_SOL = grenade -> {
		Level world = grenade.level();
		world.playSound(null, grenade.getX(), grenade.getY(), grenade.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10.0f, world.random.nextFloat() * 0.1F + 0.9F);

		EntityNukeExplosionMK3 entity = new EntityNukeExplosionMK3(world);
		entity.setPos(grenade.getX(), grenade.getY(), grenade.getZ());
		entity.destructionRange = 25;
		entity.speed = BombConfig.blastSpeed;
		entity.coefficient = 1.0F;
		entity.waste = false;
		entity.extType = 1;

		world.addFreshEntity(entity);

		EntityCloudSolinium cloud = new EntityCloudSolinium(world, 25);
		cloud.setPos(grenade.getX(), grenade.getY(), grenade.getZ());
		world.addFreshEntity(cloud);
	};

	public static final EnumProxy<EnumGrenadeFilling> LEAFIA_NULL_PROXY = new EnumProxy<>(EnumGrenadeFilling.class,
			EXPLODE_NULL, 0xc2c2c2, 0x383838, new EnumGrenadeShell[] { EnumGrenadeShell.FRAG });

	public static final EnumProxy<EnumGrenadeFilling> LEAFIA_SOL_PROXY = new EnumProxy<>(EnumGrenadeFilling.class,
			EXPLODE_SOL, 0x114eaf, 0x000000, new EnumGrenadeShell[] { EnumGrenadeShell.NUKE });

	/** The constant; touching the enum first, because a proxy is only filled in by the enum's own static initialiser. */
	public static EnumGrenadeFilling nullFilling() {
		EnumGrenadeFilling.values();
		return LEAFIA_NULL_PROXY.getValue();
	}

	public static EnumGrenadeFilling solFilling() {
		EnumGrenadeFilling.values();
		return LEAFIA_SOL_PROXY.getValue();
	}

	/** Loads the enum (and so the two constants) and, with the switch off, takes every shell from them. From the addon's constructor. */
	public static void install() {
		if(!LeafiaFeatures.leafiaGrenades) {
			nullFilling().compatibleShells.clear();
			solFilling().compatibleShells.clear();
		}
	}
}
