package com.leafia.init.hazards.types;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.dev.hazards.CEHazardHelper;
import com.leafia.dev.optimization.LeafiaParticlePacket.AlkaliFire;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The alkali metals, which burn and blow up in water - the addon's replacement for the base mod's
 * hydroreactive type, graded 1 to 6 (lithium, sodium, potassium, rubidium, caesium, francium; strontium
 * is 1.5).
 *
 * <p>Upstream: {@code com.leafia.init.hazards.types.HazardTypeAlkaline}. A stack in water, on a
 * holder or dropped, runs {@link #reactAlkaline}; one that is flaming throws the addon's fire
 * particle, lights replaceable blocks around it, and sets its holder alight.
 *
 * <h2>The stack remembers, in {@code custom_data}</h2>
 *
 * Upstream's {@code damage} and {@code flaming} integers on the stack's NBT, under the same names:
 * {@code damage} adds up while it is wet and a stack over 200 is destroyed; {@code flaming} is a
 * burn countdown of 240. A dropped item that leaves the water forgets its damage.
 *
 * <h2>The CE cadence</h2>
 *
 * Upstream's methods run once per <em>application</em>, which on CE is every fifth tick, and these
 * are discrete counters and explosions rather than doses, so they cannot be divided by five the way
 * D-1094 divides a dose. The application ticks are therefore every {@link CEHazardHelper#CE_HAZARD_RATE}th
 * game tick ({@link #isApplicationTick}), and the flame's countdown drops by that many at each, which
 * is upstream's {@code hazardRate}. The base mod's hazard pass runs every tick; between applications
 * this type does nothing.
 *
 * <p><b>Divergences from 1.12.2:</b> {@code newExplosion(null, x, y, z, r, true, smoke)} is
 * {@code Level.explode} with {@code ExplosionInteraction.NONE} for the rubidium blast (smoke false,
 * no block damage) and {@code BLOCK} for the others; "replaceable and not liquid" is
 * {@code canBeReplaced()} and an empty fluid state; fire is {@code Blocks.FIRE}'s default state, as
 * upstream's.
 */
public class HazardTypeAlkaline extends HazardTypeBase {

	public static final HazardTypeAlkaline INSTANCE = new HazardTypeAlkaline();

	/** The damage past which a wet stack is destroyed. Upstream's literal 200. */
	public static final int DESTROYED_ABOVE = 200;

	private HazardTypeAlkaline() {}

	/** Whether this game tick is one of CE's applications. */
	public static boolean isApplicationTick(Level level) {
		return isApplicationTick(level.getGameTime());
	}

	public static boolean isApplicationTick(long gameTime) {
		return gameTime % CEHazardHelper.CE_HAZARD_RATE == 0;
	}

	@Override
	public void onUpdate(LivingEntity entity, float level, ItemStack held) {
		if(!isApplicationTick(entity.level())) return;
		application(entity, level, held);
	}

	/** One application on a holder: upstream's {@code onUpdate}, past the cadence gate. */
	public void application(LivingEntity entity, float level, ItemStack held) {
		Level world = entity.level();
		int alkaline = (int) level;
		if(!world.isClientSide && entity.isInWater()) {
			int damage = reactAlkaline(world, held, entity.getX(), entity.getY(), entity.getZ(), alkaline);
			if(damage > DESTROYED_ABOVE && entity instanceof Player player) {
				held.shrink(held.getCount());
				player.inventoryMenu.broadcastChanges();
			}
		}
		if(tickAlkaline(world, held, entity.getX(), entity.getY(), entity.getZ(), alkaline))
			entity.igniteForSeconds(3);
	}

	@Override
	public void updateEntity(ItemEntity item, float level) {
		item.setInvulnerable(true);
		if(!isApplicationTick(item.level())) return;
		application(item, level);
	}

	/** One application on a dropped item: upstream's {@code updateEntity}, past the cadence gate. */
	public void application(ItemEntity item, float level) {
		Level world = item.level();
		int alkaline = (int) level;
		BlockPos pos = BlockPos.containing(item.getX(), item.getY(), item.getZ());
		if(item.isInWater() || world.isRainingAt(pos) || world.getFluidState(pos).is(FluidTags.WATER)) {
			int damage = reactAlkaline(world, item.getItem(), item.getX(), item.getY(), item.getZ(), alkaline);
			if(damage > DESTROYED_ABOVE) {
				item.discard();
				return;
			}
		} else {
			edit(item.getItem(), tag -> tag.remove("damage"));
		}
		tickAlkaline(world, item.getItem(), item.getX(), item.getY(), item.getZ(), alkaline);
	}

	@Override
	public void addHazardInformation(Player player, List<Component> list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		String number = level == (int) level ? Integer.toString((int) level) : Float.toString(level);
		list.add(Component.literal("[").append(Component.translatable("trait._hazarditem.hydro")).append(" " + number + "]")
				.withStyle(ChatFormatting.RED));
	}

	// ---- the stack's memory ----

	public static int getInt(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getInt(key);
	}

	private static void edit(ItemStack stack, Consumer<CompoundTag> change) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null) {
			// Upstream's getNBT creates the tag as it reads it; a removal on nothing creates nothing here.
			CompoundTag tag = new CompoundTag();
			change.accept(tag);
			if(!tag.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			return;
		}
		CompoundTag tag = data.copyTag();
		change.accept(tag);
		if(tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
		else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// ---- the LCE zone ----

	/**
	 * A flaming stack: the particle, the fire around it, the countdown. True while it was flaming,
	 * which sets its holder alight.
	 */
	public boolean tickAlkaline(Level world, ItemStack stack, double x, double y, double z, int alkaline) {
		int flaming = getInt(stack, "flaming");
		if(flaming <= 0) return false;

		if(world instanceof ServerLevel server)
			new AlkaliFire(alkaline).emit(server, new Vec3(x, y, z), Vec3.ZERO);

		Random prand = new Random(Math.floorDiv((int) (x + y * 200 + z * 40000), 100000));
		for(int i = 0; i <= alkaline - 1; i++) {
			BlockPos pos = BlockPos.containing(x, y, z);
			if(i > 0) {
				int range = (int) (i * 0.52) + 1;
				pos = pos.offset(prand.nextInt(range * 2) - range, prand.nextInt(range * 2) - range, prand.nextInt(range * 2) - range);
			}
			if(!world.isOutsideBuildHeight(pos) && world.getWorldBorder().isWithinBounds(pos) && world.random.nextBoolean()) {
				BlockState state = world.getBlockState(pos);
				if(state.canBeReplaced() && state.getFluidState().isEmpty())
					world.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
			}
		}
		if(flaming > 1) {
			int next = Math.max(flaming - CEHazardHelper.CE_HAZARD_RATE, 0);
			edit(stack, tag -> tag.putInt("flaming", next));
		} else {
			edit(stack, tag -> tag.remove("flaming"));
		}
		return true;
	}

	/**
	 * One application in water: the grade decides how much {@code damage} it adds, whether it
	 * starts flaming, and whether it blows. Returns the stack's damage afterwards.
	 */
	public int reactAlkaline(Level world, ItemStack stack, double x, double y, double z, int alkaline) {
		int damage = getInt(stack, "damage");
		int flaming = getInt(stack, "flaming");
		int add = 0;
		switch(alkaline) {
			case 1:
				if(flaming > 0 || world.random.nextInt(20) == 0) {
					flaming = 240;
					add = 1;
				}
				break;
			case 2:
				flaming = 240;
				add = 5;
				break;
			case 3:
				flaming = 240;
				add = 2;
				break;
			case 4:
				world.explode(null, x, y, z, 1F, true, Level.ExplosionInteraction.NONE);
				add = 1000;
				break;
			case 5:
				world.explode(null, x, y, z, 3F, true, Level.ExplosionInteraction.BLOCK);
				add = 1000;
				break;
			case 6:
				world.explode(null, x, y, z, 6F, true, Level.ExplosionInteraction.BLOCK);
				ChunkRadiationManager.incrementRad(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), 40F);
				add = 1000;
				break;
			default:
				break;
		}
		final int newDamage = damage + add;
		final int newFlaming = flaming;
		edit(stack, tag -> {
			if(newDamage > 0) tag.putInt("damage", newDamage);
			else tag.remove("damage");
			if(newFlaming > 1) tag.putInt("flaming", Math.max(newFlaming - 1, 0));
			else tag.remove("flaming");
		});
		return newDamage;
	}
}
