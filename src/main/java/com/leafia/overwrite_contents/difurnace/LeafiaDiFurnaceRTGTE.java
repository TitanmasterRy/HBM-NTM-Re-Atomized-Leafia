package com.leafia.overwrite_contents.difurnace;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityDiFurnaceRTG;
import com.hbm.inventory.recipes.BlastFurnaceRecipes;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The RTG blast furnace with upstream's fuel-rod slot: an addon rod in slot {@value #ROD_SLOT} adds its heat
 * to the pellets'.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityDiFurnaceRTG.java}, three
 * changes to NTM-CE's furnace, made here as overrides on the base mod's ({@code overhauls.rtgFurnaceOverhaul},
 * {@link LeafiaRTGFurnace}):
 *
 * <ul>
 * <li><b>a tenth slot</b> - the constructor's inventory of ten. CE's {@code readFromNBT} overwrite that
 *     raises an old save's {@code Size} to 10 is a 1.12.2 {@code ItemStackHandler} matter and dissolves: a
 *     nine-slot save loads into the first nine here;</li>
 * <li><b>the rod's tick</b>, injected before the furnace asks {@code hasPower()}: a rod that reaches ten
 *     million degrees empties the furnace, removes it and goes off; one that has spilled for more than 100
 *     ticks removes it and goes off at radius 2; otherwise, while the furnace can process, it adds
 *     {@code floor((heat / 250)^0.54 * 15)} to the heat and fissions on twice its own flux, and while it
 *     cannot, it only cools ({@code HeatFunction} on zero flux). Here the bonus is added in
 *     {@link #hasPower()}, where the base furnace computes its heat;</li>
 * <li><b>{@code canProcess} treats a rod as power</b> - upstream redirects the one {@code hasPower()} call in
 *     it. That call only guards the empty-input early-out, which the recipe lookup would refuse anyway, so the
 *     redirect changes no answer; what it changes is that the call does not run, and in the base furnace that
 *     call is also a pellet decay step ({@code RTGUtil.updateRTGs}). So with a rod in, {@link #canProcess()}
 *     makes no such call.</li>
 * </ul>
 *
 * <p>Two numbers are the base mod's, not CE's, and stay so: CE runs on {@code rtgPower > 0} with a 2,400-tick
 * job and a 6,000 heat cap, the base furnace on 15 heat ({@code MIN_HEAT}) with a 1,200-tick job. So a cold
 * rod alone (3 heat) runs a CE furnace slowly and does not run this one; it has to be helped over 15 by
 * pellets or by its own temperature (D-3020).
 */
public class LeafiaDiFurnaceRTGTE extends BlockEntityDiFurnaceRTG {

	public static final int ROD_SLOT = 9;
	public static final int SLOTS = 10;
	/** Upstream's {@code spillage > 20*5}. */
	public static final int SPILLAGE_LIMIT = 20 * 5;
	public static final float SPILL_RADIUS = 2;

	/** The rod's heat this tick: 0 unless it is in and the furnace could process when the tick began. */
	protected int rodBonus = 0;

	public LeafiaDiFurnaceRTGTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	/** Upstream's {@code (int)Math.floor(Math.pow(fuelHeat/250,0.54)*15)}. */
	public static int rodHeat(double fuelHeat) {
		return (int) Math.floor(Math.pow(fuelHeat / 250, 0.54) * 15);
	}

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		rodBonus = 0;

		if(slots[ROD_SLOT].getItem() instanceof LeafiaRodItem rod) {
			ItemStack stack = slots[ROD_SLOT];
			CompoundTag nbt = LeafiaRodItem.data(stack);
			double fuelHeat = 20;
			if(nbt != null) {
				fuelHeat = nbt.getDouble("heat");
				if(nbt.getBoolean("nuke")) {
					for(int i = 0; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
					level.removeBlock(worldPosition, false);
					rod.nuke(level, worldPosition);
					return;
				}
				if(nbt.getInt("spillage") > SPILLAGE_LIMIT) {
					ItemStack prevStack = null;
					for(int i = 0; i < slots.length; i++) {
						prevStack = LeafiaRodItem.comparePriority(slots[i], prevStack);
						slots[i] = ItemStack.EMPTY;
					}
					level.removeBlock(worldPosition, false);
					LeafiaRodItem detonate = (LeafiaRodItem) prevStack.getItem();
					detonate.resetDetonate();
					detonate.detonateRadius = SPILL_RADIUS;
					detonate.detonate(level, worldPosition);
					return;
				}
			}
			if(canProcess()) {
				rodBonus = rodHeat(fuelHeat);
				rod.HeatFunction(stack, true, rod.getFlux(stack) * 2, 0, 0, 0);
				ItemStack product = rod.getDecayProduct(stack);
				if(product != null) slots[ROD_SLOT] = product;
			} else
				rod.HeatFunction(stack, true, 0, 0, 0, 0);
		}

		super.updateEntity();
	}

	/** The pellets' heat, as the base furnace computes it, plus the rod's. */
	@Override
	public boolean hasPower() {
		super.hasPower();
		processSpeed += rodBonus;
		return processSpeed >= MIN_HEAT;
	}

	/** The base furnace's test with upstream's redirect: a rod in the slot stands in for power. See the class note. */
	@Override
	public boolean canProcess() {
		if(!(slots[ROD_SLOT].getItem() instanceof LeafiaRodItem)) return super.canProcess();

		ItemStack recipeResult = BlastFurnaceRecipes.getOutput(slots[0], slots[1]);
		if(recipeResult == null)
			return false;
		else if(slots[2].isEmpty())
			return true;
		else if(!ItemStack.isSameItemSameComponents(slots[2], recipeResult))
			return false;
		else if(slots[2].getCount() + recipeResult.getCount() > getMaxStackSize())
			return false;
		else if(slots[2].getCount() < getMaxStackSize() && slots[2].getCount() < slots[2].getMaxStackSize())
			return true;
		else
			return slots[2].getCount() < recipeResult.getMaxStackSize();
	}

	public int getRodBonus() {
		return rodBonus;
	}

	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new LeafiaDiFurnaceRTGMenu(containerId, playerInventory, this);
	}
}
