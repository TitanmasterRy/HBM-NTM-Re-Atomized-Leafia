package com.leafia.contents.bomb.missile.customnuke;

import java.util.List;

import com.hbm.blockentity.bomb.BlockEntityNukeCustom;
import com.hbm.blockentity.bomb.BlockEntityNukeCustom.CustomNukeEntry;
import com.hbm.blockentity.bomb.BlockEntityNukeCustom.EnumEntryType;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.items.weapon.ItemMissile;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.bomb.missile.customnuke.container.CustomNukeMissileContainer;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The custom nuke missile (D-3022): a tier 0 micro missile whose warhead is the custom nuke's 27 slots, filled by
 * right-clicking it. Any launch pad fires it (through {@code LeafiaStrikeSeams.CUSTOM_NUKE_PROVIDER}) as long as it
 * carries some TNT; the missile then sets off the base mod's custom nuke where it lands.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/CustomNukeMissileItem.java}, an NTM-CE
 * {@code ItemMissileStandard(MICRO, TIER0)} - the base mod's {@link ItemMissile} of the same form factor and tier, so
 * solid-fuelled and needing none - that opens its GUI on use.
 *
 * <h2>The sum, which is NTM-CE's, not the base mod's custom nuke's</h2>
 *
 * {@link CustomNukeMissileInventory#update} reads each slot against the base mod's {@code BlockEntityNukeCustom.entries}
 * and, as upstream, does not multiply by the stack size (each slot holds one item, so it would not matter). After the
 * multipliers the stages gate each other: TNT under 16 zeroes nuclear; nuclear under 100 zeroes thermonuclear, under
 * 50 balefire and schrabidium, under 25 solinium; and euphemium needs schrabidium <em>and</em> solinium of at least 1.
 * Then each is capped at NTM-CE's {@code BombConfig} defaults: TNT 150, nuclear 200, thermonuclear 350, balefire 350,
 * salted 200, schrabidium 250, solinium 350, euphemium 20.
 *
 * <p>Two consequences of building it on the 1.7.10 base mod, flagged in D-3022: the base mod's table has <b>no
 * solinium entry</b> (NTM-CE added the stage), so solinium is always 0 - and so, by NTM-CE's own gate, <b>euphemium is
 * always 0</b> too; and its "antimatter" entries are what NTM-CE calls balefire.
 *
 * <h2>Where the slots live</h2>
 *
 * In the stack's {@code minecraft:custom_data}, under upstream's {@code Inventory} key: the handler's own tag plus the
 * eight capped stage floats, which is what the launch pad reads. Upstream saved it off-thread after checking the
 * compressed size against NTM-CE's {@code crateByteSize} and ejected everything when over; the base mod has neither
 * setting, and 27 single items cannot approach it, so it saves at once.
 */
public class CustomNukeMissileItem extends ItemMissile {

	public static final int SLOTS = 27;

	public static final int MAX_TNT = 150;
	public static final int MAX_NUKE = 200;
	public static final int MAX_HYDRO = 350;
	public static final int MAX_BALE = 350;
	public static final int MAX_DIRTY = 200;
	public static final int MAX_SCHRAB = 250;
	public static final int MAX_SOL = 350;
	public static final int MAX_EUPH = 20;

	/** The custom-data key the slots and stages are saved under. */
	public static final String INVENTORY = "Inventory";

	public CustomNukeMissileItem(Properties properties) {
		super(MissileFormFactor.MICRO, MissileTier.TIER0, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if(!world.isClientSide && player instanceof ServerPlayer server) {
			server.openMenu(new SimpleMenuProvider((id, inv, p) -> new CustomNukeMissileContainer(id, inv, hand),
					stack.getHoverName()), buf -> buf.writeEnum(hand));
		}
		return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, context, list, flag);
		list.add(Component.literal("§d[" + I18nUtil.resolveKey("item.missile_customnuke.desc") + "]§r"));
	}

	/** The saved {@code Inventory} tag of a stack, or an empty one. */
	public static CompoundTag inventoryTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null) return new CompoundTag();
		return data.copyTag().getCompound(INVENTORY);
	}

	/** The 27 slots of a missile stack. Changes are written back to the stack as they happen. */
	public static class CustomNukeMissileInventory extends ItemStackHandler {

		public final ItemStack box;
		private final HolderLookup.Provider registries;
		private boolean loading;

		public float tnt;
		public float nuke;
		public float hydro;
		public float bale;
		public float dirty;
		public float schrab;
		public float sol;
		public float euph;

		public CustomNukeMissileInventory(ItemStack box, HolderLookup.Provider registries) {
			super(SLOTS);
			this.box = box;
			this.registries = registries;
			this.loading = true;
			try {
				this.deserializeNBT(registries, inventoryTag(box));
			} finally {
				this.loading = false;
			}
			update();
		}

		public void update() {
			float tnt = 0F,		tntMod = 1F;
			float nuke = 0F,	nukeMod = 1F;
			float hydro = 0F,	hydroMod = 1F;
			float bale = 0F,	baleMod = 1F;
			float dirty = 0F,	dirtyMod = 1F;
			float schrab = 0F,	schrabMod = 1F;
			float sol = 0F,		solMod = 1F;
			float euph = 0F;

			for(int i = 0; i < getSlots(); i++) {
				ItemStack stack = getStackInSlot(i);
				if(stack.isEmpty()) continue;

				CustomNukeEntry ent = BlockEntityNukeCustom.entries.get(new ComparableStack(stack).makeSingular());
				if(ent == null) continue;

				if(ent.entry == EnumEntryType.ADD) {
					switch(ent.type) {
						case TNT: tnt += ent.value; break;
						case NUKE: nuke += ent.value; break;
						case HYDRO: hydro += ent.value; break;
						case AMAT: bale += ent.value; break;
						case DIRTY: dirty += ent.value; break;
						case SCHRAB: schrab += ent.value; break;
						case EUPH: euph += ent.value; break;
					}
				} else if(ent.entry == EnumEntryType.MULT) {
					switch(ent.type) {
						case TNT: tntMod *= ent.value; break;
						case NUKE: nukeMod *= ent.value; break;
						case HYDRO: hydroMod *= ent.value; break;
						case AMAT: baleMod *= ent.value; break;
						case DIRTY: dirtyMod *= ent.value; break;
						case SCHRAB: schrabMod *= ent.value; break;
						default: break;
					}
				}
			}
			tnt *= tntMod;
			nuke *= nukeMod;
			hydro *= hydroMod;
			bale *= baleMod;
			dirty *= dirtyMod;
			sol *= solMod;
			schrab *= schrabMod;

			if(tnt < 16) nuke = 0;
			if(nuke < 100) hydro = 0;
			if(nuke < 50) bale = 0;
			if(nuke < 50) schrab = 0;
			if(nuke < 25) sol = 0;
			if(schrab < 1 || sol < 1) euph = 0;

			this.tnt = Math.min(tnt, MAX_TNT);
			this.nuke = Math.min(nuke, MAX_NUKE);
			this.hydro = Math.min(hydro, MAX_HYDRO);
			this.bale = Math.min(bale, MAX_BALE);
			this.dirty = Math.min(dirty, MAX_DIRTY);
			this.schrab = Math.min(schrab, MAX_SCHRAB);
			this.sol = Math.min(sol, MAX_SOL);
			this.euph = Math.min(euph, MAX_EUPH);
		}

		public float getNukeAdj() {
			if(nuke == 0) return 0;
			return Math.min(nuke + tnt / 2, MAX_NUKE);
		}

		public float getHydroAdj() {
			if(hydro == 0) return 0;
			return Math.min(hydro + nuke / 2 + tnt / 4, MAX_HYDRO);
		}

		public float getBaleAdj() {
			if(bale == 0) return 0;
			return Math.min(bale + hydro / 2 + nuke / 4 + tnt / 8, MAX_BALE);
		}

		public float getSchrabAdj() {
			if(schrab == 0) return 0;
			return Math.min(schrab + bale / 2 + hydro / 4 + nuke / 8 + tnt / 16, MAX_SCHRAB);
		}

		public float getSolAdj() {
			if(sol == 0) return 0;
			return Math.min(sol + schrab / 2 + bale / 4 + hydro / 8 + nuke / 16 + tnt / 32, MAX_SOL);
		}

		@Override
		protected void onContentsChanged(int slot) {
			if(loading) return;
			update();
			CompoundTag nbt = serializeNBT(registries);
			CustomData.update(DataComponents.CUSTOM_DATA, box, tag -> tag.put(INVENTORY, nbt));
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		/** The handler's slots, and the eight capped stages, as upstream wrote them for the launch pad. */
		@Override
		public CompoundTag serializeNBT(HolderLookup.Provider provider) {
			CompoundTag nbt = super.serializeNBT(provider);
			nbt.putFloat("tnt", Math.min(tnt, MAX_TNT));
			nbt.putFloat("nuke", Math.min(nuke, MAX_NUKE));
			nbt.putFloat("hydro", Math.min(hydro, MAX_HYDRO));
			nbt.putFloat("bale", Math.min(bale, MAX_BALE));
			nbt.putFloat("dirty", Math.min(dirty, MAX_DIRTY));
			nbt.putFloat("schrab", Math.min(schrab, MAX_SCHRAB));
			nbt.putFloat("sol", Math.min(sol, MAX_SOL));
			nbt.putFloat("euph", Math.min(euph, MAX_EUPH));
			return nbt;
		}
	}
}
