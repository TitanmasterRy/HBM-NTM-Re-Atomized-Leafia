package com.leafia.contents.miscellanous.regex_filter.pneumatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.IRegexFilterGUI;
import com.leafia.contents.miscellanous.regex_filter.pneumatic.container.RegexFilterMenu;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.init.LeafiaBlockEntities;
import com.llib.group.LeafiaSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The regex filter's one-slot buffer, its filter list, and the push into whatever it faces.
 *
 * <p>Upstream: {@code .../regex_filter/pneumatic/RegexFilterTE.java}. A pneumatic tube segment
 * that <b>only accepts items its filters pass</b> and then, every tick, tries to move what it
 * holds into the inventory in front of it. It is not a tube network node - the base mod's
 * pneumatic tube is not ported and upstream's filter does not talk to it either: it is an item
 * handler on its back face and an inserter on its front, so a hopper, a pipe or another filter
 * can feed it and a chest can sit at the end.
 *
 * <h2>What a filter list means</h2>
 *
 * Upstream's {@link #isItemValid}, kept exactly:
 * <ul>
 *   <li>Each filter is a regular expression, a type and a blacklist flag; the pattern has to match
 *       the <b>whole</b> of the string ({@link String#matches}), so {@code stone} is not
 *       {@code minecraft:stone}.</li>
 *   <li>An item is valid if <b>some whitelist</b> filter matches and <b>no blacklist</b> filter
 *       does. A blacklist match wins whatever order the list is in, and a list of only blacklists
 *       passes nothing, because nothing ever set {@code valid}. An empty list passes nothing.</li>
 *   <li>A pattern that does not compile matches nothing, so as a blacklist it blocks nothing.</li>
 * </ul>
 *
 * <h2>Two things that are not upstream's, both forced</h2>
 *
 * <b>{@link FilterType#ORE_DICT} matches item tags.</b> 1.21.1 has no ore dictionary; its answer
 * to "which families is this in" is the item tag, so the string tested is the tag's id -
 * {@code c:ingots/iron}, {@code minecraft:coals} - for each tag the item carries. A pattern a
 * 1.12.2 player wrote ({@code ingot.*}) does not carry across. The enum constant keeps its name
 * because that is what is saved. <b>{@link FilterType#CREATIVE_TAB}</b> tests the creative tab's
 * registry id; see {@link RegexFilterTabs}.
 *
 * <h2>The packet channel</h2>
 *
 * The addon's own {@link LeafiaPacket}, as upstream: the whole filter list is one NBT in slot 0,
 * client to server when a screen changes it, and back to every player with the menu open.
 * Upstream's server side accepts it from <b>any</b> client for <b>any</b> loaded filter, with no
 * check that the sender has the screen open, and runs the patterns on the server thread; both are
 * kept and flagged in D-1084.
 */
public class RegexFilterTE extends BlockEntity implements LeafiaPacketReceiver, MenuProvider {

	public static class RegexFilter {
		public String regex = "";
		public FilterType type = FilterType.RESOURCE_ID;
		public boolean blacklist = false;

		/** Upstream's: two filters are the same when pattern and type agree - the flag is not part of it. */
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof RegexFilter filter)
				return filter.regex.equals(regex) && filter.type == type;
			return super.equals(obj);
		}

		@Override
		public int hashCode() {
			return Objects.hash(regex, type);
		}
	}

	public enum FilterType { RESOURCE_ID, ORE_DICT, CREATIVE_TAB }

	public final LeafiaSet<RegexFilter> filters = new LeafiaSet<>();

	/**
	 * The one slot. It refuses what the filters refuse, from a hopper and from a player alike.
	 *
	 * <p>The {@code insertItem} override is upstream's and is <b>redundant here</b>: NeoForge's
	 * {@code ItemStackHandler.insertItem} asks {@code isItemValid} itself, which 1.12.2's did not
	 * (D-1084 removed it to find out, and no test noticed). Kept, because it costs nothing and
	 * says what the slot is for.
	 */
	public final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			RegexFilterTE.this.setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return RegexFilterTE.this.isItemValid(stack);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if(!RegexFilterTE.this.isItemValid(stack)) return stack;
			return super.insertItem(slot, stack, simulate);
		}
	};

	/** The open screen, if any (client only). An interface so a dedicated server never loads a screen. */
	public IRegexFilterGUI gui;

	/** The players with the menu open (server only), who are sent every change. */
	public final List<Player> listeners = new ArrayList<>();

	public RegexFilterTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.REGEX_FILTER.get(), pos, state);
	}

	// ------------------------------------------------------------------ the filtering

	/**
	 * Upstream's {@code isItemValid}: whether the filters let {@code stack} in.
	 *
	 * <p>The blacklist is tested inside the same loop as the whitelist and returns at once, which
	 * is why it wins regardless of where it sits.
	 */
	public boolean isItemValid(ItemStack stack) {
		boolean valid = false;
		for(RegexFilter filter : filters) {
			for(String value : valuesOf(stack, filter.type)) {
				if(LeafiaUtil.matchesRegex(value, filter.regex)) {
					if(filter.blacklist) return false;
					valid = true;
				}
			}
		}
		return valid;
	}

	/** The strings a filter of this type tests {@code stack} against. */
	private List<String> valuesOf(ItemStack stack, FilterType type) {
		Item item = stack.getItem();
		switch(type) {
			case ORE_DICT: {
				List<String> tags = new ArrayList<>();
				stack.getTags().map(TagKey::location).forEach(id -> tags.add(id.toString()));
				return tags;
			}
			case RESOURCE_ID:
				return List.of(BuiltInRegistries.ITEM.getKey(item).toString());
			case CREATIVE_TAB:
				return RegexFilterTabs.tabsOf(level, item);
			default:
				return List.of();
		}
	}

	/**
	 * Upstream's "stolen code goes brr": the largest count of {@code stack} that {@code slot} takes,
	 * by binary search over simulated inserts.
	 */
	private int findMaxInsertable(IItemHandler target, int slot, ItemStack stack, int upperBound) {
		int lo = 0;
		int hi = upperBound;

		while(lo < hi) {
			int mid = (lo + hi + 1) >>> 1;

			ItemStack test = stack.copy();
			test.setCount(mid);
			ItemStack res = target.insertItem(slot, test, true);

			if(res.isEmpty()) lo = mid;
			else hi = mid - 1;
		}

		return lo;
	}

	/**
	 * Upstream's {@code tryInsertItemCap}: unloads {@code stack} into {@code target}, slot by slot,
	 * shrinking it in place by what went in.
	 *
	 * <p>A slot is skipped unless it takes a single item on its own; of the rest, as many as the
	 * binary search says fit are inserted. So a slot that would take only whole stacks of four, or
	 * one that cannot hold the item, is not partly filled.
	 *
	 * @return whether anything moved
	 */
	public boolean tryInsertItemCap(IItemHandler target, ItemStack stack) {
		if(stack.isEmpty()) return false;

		boolean movedAny = false;

		for(int i = 0; i < target.getSlots() && !stack.isEmpty(); i++) {
			ItemStack probe = stack.copy();
			probe.setCount(1);
			ItemStack simOne = target.insertItem(i, probe, true);
			if(!simOne.isEmpty()) continue;

			int maxTry = Math.min(stack.getCount(), target.getSlotLimit(i));
			int accepted = findMaxInsertable(target, i, stack, maxTry);

			if(accepted > 0) {
				ItemStack toInsert = stack.copy();
				toInsert.setCount(accepted);
				ItemStack rest = target.insertItem(i, toInsert, false);

				int actuallyInserted = accepted - (!rest.isEmpty() ? rest.getCount() : 0);
				if(actuallyInserted > 0) {
					stack.shrink(actuallyInserted);
					movedAny = true;
				}
			}
		}

		return movedAny;
	}

	/** Upstream's {@code update()}, server side: push the held stack into what the front face meets. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, RegexFilterTE filter) {

		ItemStack held = filter.inventory.getStackInSlot(0);
		if(held.isEmpty()) return;
		if(!(state.getBlock() instanceof RegexFilterBlock)) return;

		Direction facing = state.getValue(RegexFilterBlock.FACING);
		IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK,
				pos.relative(facing), facing.getOpposite());

		if(target != null && filter.tryInsertItemCap(target, held))
			filter.setChanged();
	}

	/** Which way it pushes. Upstream falls back to up when the block is not a filter. */
	public Direction getFacing() {
		BlockState state = getBlockState();
		if(state.getBlock() instanceof RegexFilterBlock)
			return state.getValue(RegexFilterBlock.FACING);
		return Direction.UP;
	}

	// ------------------------------------------------------------------ the filter list as NBT

	/**
	 * Upstream's {@code loadFilterFromNBT}: replaces the list. An entry whose {@code type} is not
	 * one of the three is skipped, and every other field is read leniently ({@code ""} and
	 * {@code false} when missing), so a client cannot make this throw.
	 */
	public void loadFilterFromNBT(ListTag list) {
		filters.clear();
		for(Tag nbt : list) {
			if(nbt instanceof CompoundTag tag) {
				String type = tag.getString("type");
				try {
					FilterType filter = FilterType.valueOf(type);
					RegexFilter instance = new RegexFilter();
					instance.type = filter;
					instance.regex = tag.getString("value");
					instance.blacklist = tag.getBoolean("blacklist");
					filters.add(instance);
				} catch(IllegalArgumentException ignored) {
				}
			}
		}
	}

	public ListTag saveFilterToNBT() {
		ListTag list = new ListTag();
		for(RegexFilter filter : filters) {
			CompoundTag tag = new CompoundTag();
			tag.putString("type", filter.type.name());
			tag.putString("value", filter.regex);
			tag.putBoolean("blacklist", filter.blacklist);
			list.add(tag);
		}
		return list;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.put("filter", saveFilterToNBT());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		if(tag.contains("filter"))
			loadFilterFromNBT(tag.getList("filter", Tag.TAG_COMPOUND));
	}

	// ------------------------------------------------------------------ chunk sync

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		super.handleUpdateTag(tag, registries);
		if(level != null && level.isClientSide) updateGUIs();
	}

	void updateGUIs() {
		if(gui != null) gui.onSyncFilters();
	}

	// ------------------------------------------------------------------ the menu

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new RegexFilterMenu(containerId, playerInventory, this);
	}

	// ------------------------------------------------------------------ the packet channel

	@Override
	public String getPacketIdentifier() {
		return "PNEUMO_REGEX";
	}

	/** Upstream's: nothing is broadcast by range, only to the players with the menu open. */
	@Override
	public double affectionRange() {
		return 0;
	}

	@Override
	public List<Player> getListeners() {
		return listeners;
	}

	public LeafiaPacket.Builder generateSyncPacket() {
		CompoundTag compound = new CompoundTag();
		compound.put("a", saveFilterToNBT());
		return LeafiaPacket._start(this).__write(0, compound);
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 0) {
			if(value instanceof CompoundTag tag && tag.contains("a")) {
				loadFilterFromNBT(tag.getList("a", Tag.TAG_COMPOUND));
				updateGUIs();
			}
		}
	}

	/** The most filters a list may hold. The screen scrolls, and nobody builds this many by hand. */
	public static final int MAX_FILTERS = 128;

	/** The longest pattern a client may save, in characters. */
	public static final int MAX_PATTERN = 256;

	/**
	 * Whether a list a client sent is one the screen could have made: at most {@link #MAX_FILTERS}
	 * entries, each a compound with a type that is one of the three, a pattern of at most
	 * {@link #MAX_PATTERN} characters that compiles (the screen refuses to save one that does not),
	 * and a boolean flag.
	 *
	 * <p>This bounds the list and the pattern text; running is bounded separately: {@link LeafiaUtil#matchesRegex} abandons a catastrophic
	 * pattern after {@link LeafiaUtil#MATCH_STEP_LIMIT} character reads (D-3075).
	 */
	public static boolean isValidFilterList(ListTag list) {

		if(list.size() > MAX_FILTERS) return false;

		for(Tag nbt : list) {
			if(!(nbt instanceof CompoundTag tag)) return false;
			if(!tag.contains("type", Tag.TAG_STRING) || !tag.contains("value", Tag.TAG_STRING)) return false;
			if(tag.contains("blacklist") && !tag.contains("blacklist", Tag.TAG_BYTE)) return false;
			try {
				FilterType.valueOf(tag.getString("type"));
			} catch(IllegalArgumentException ignored) {
				return false;
			}
			String pattern = tag.getString("value");
			if(pattern.length() > MAX_PATTERN || !LeafiaUtil.isRegexValid(pattern)) return false;
		}

		return true;
	}

	/**
	 * A screen changed the list: replace it, save, send the block to everyone tracking it and the
	 * list to everyone with a menu open.
	 *
	 * <p><b>Upstream trusts the sender completely</b> - any client can name any loaded filter's
	 * position and rewrite its list, and the patterns are then run on the server thread. D-1084
	 * flagged it and the owner's answer to D-1093 #4 is to fix it, so (D-1094) the sender must have
	 * <em>this</em> filter's menu open, be within reach, and send a list {@link #isValidFilterList}
	 * accepts. Anything else is dropped whole.
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key == 0) {
			if(!(plr != null && plr.containerMenu instanceof RegexFilterMenu menu && menu.entity == this)) {
				PacketGuard.reject("RegexFilterTE list", plr, "this filter's screen is not open");
				return;
			}
			if(!PacketGuard.near(plr, worldPosition)) {
				PacketGuard.reject("RegexFilterTE list", plr, "out of reach");
				return;
			}
			if(value instanceof CompoundTag tag && tag.contains("a", Tag.TAG_LIST)) {
				ListTag list = (ListTag) tag.get("a");
				if(!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND || !isValidFilterList(list)) {
					PacketGuard.reject("RegexFilterTE list", plr, "not a list the screen could have made");
					return;
				}
				loadFilterFromNBT(list);
				setChanged();
				if(level != null) {
					BlockState state = level.getBlockState(worldPosition);
					level.sendBlockUpdated(worldPosition, state, state, 3);
				}
				generateSyncPacket().__sendToListeners();
			}
		}
	}

	@Override
	public void onPlayerValidate(Player plr) { }
}
