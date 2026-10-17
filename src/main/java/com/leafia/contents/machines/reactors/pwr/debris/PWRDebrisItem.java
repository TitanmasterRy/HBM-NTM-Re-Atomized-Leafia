package com.leafia.contents.machines.reactors.pwr.debris;

import javax.annotation.Nullable;

import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity.DebrisType;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A handful of a PWR's wreckage, named after the block it came from: {@code lwr_piece} (stone),
 * {@code lwr_shrapnel} (metal) and {@code lwr_shard} (glass).
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/debris/PWRDebrisItem.java}. The stack's
 * {@code block} tag is the source block's registry name, upstream's key; {@code state}, beside it, is the
 * block state this port carries in place of upstream's {@code meta}. Nine of a kind craft back into the
 * block ({@link PWRDebrisCrafting}) unless the item says otherwise - the shard does
 * ({@link #disableCrafting}).
 *
 * <p>The name is upstream's composition: {@code item.lwrdebris.conjunctive} ("{block} {type}") with the
 * block's name and the type's word, or the {@code .alt} form ("{type} of {block}") when the block's name
 * contains {@code item.lwrdebris.conjunctive.switch} ("of") as a word, compared lower-case - so "Block of Steel"
 * reads "Shrapnel of Block of Steel". A stack with no tag uses the item's own name as the block's, as
 * upstream does. The parts are looked up in the running language, as upstream's {@code I18nUtil} did on the client.
 */
public class PWRDebrisItem extends Item {

	public final DebrisType type;
	final String typeNameKey;
	public boolean canBeCraftedBack = true;

	public PWRDebrisItem(Properties properties, String name, DebrisType type) {
		super(properties);
		this.type = type;
		this.typeNameKey = name.replace("lwr_", "item.lwrdebris.");
	}

	public PWRDebrisItem disableCrafting() {
		canBeCraftedBack = false;
		return this;
	}

	/** A debris stack of {@code item} that remembers {@code block}. */
	public static ItemStack of(Item item, BlockState block) {
		ItemStack stack = new ItemStack(item);
		CompoundTag nbt = new CompoundTag();
		nbt.putString("block", blockName(block));
		nbt.put("state", NbtUtils.writeBlockState(block));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
		return stack;
	}

	/** The stack's tag, or null for a stack that has none. */
	@Nullable
	public static CompoundTag data(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag();
	}

	/** The block state a debris stack names: {@code state} if it has one, else {@code block}'s default, else null. */
	@Nullable
	public static BlockState stateOf(ItemStack stack, @Nullable HolderLookup.Provider registries) {
		CompoundTag nbt = data(stack);
		if(nbt == null || !nbt.contains("block")) return null;
		if(nbt.contains("state") && registries != null)
			return NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), nbt.getCompound("state"));
		Block block = blockOf(nbt.getString("block"));
		return block == Blocks.AIR ? null : block.defaultBlockState();
	}

	public static String blockName(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	/** Upstream's {@code Block.getBlockFromName}: air for a name no block has. */
	public static Block blockOf(String name) {
		ResourceLocation key = ResourceLocation.tryParse(name);
		return key == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(key);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal(composeName(stack));
	}

	/** Whether {@code word} stands alone in {@code text}, compared lower-case (D-3076: upstream took any substring, so "Offset" counted as "of"). */
	public static boolean containsWord(String text, String word) {
		if(word.isEmpty()) return false;
		return java.util.regex.Pattern.compile("(?<!\\p{L})" + java.util.regex.Pattern.quote(word.toLowerCase()) + "(?!\\p{L})")
				.matcher(text.toLowerCase()).find();
	}

	/** Upstream's {@code getItemStackDisplayName}. */
	public String composeName(ItemStack stack) {
		Language lang = Language.getInstance();
		String typename = lang.getOrDefault(typeNameKey);
		String itemNameKey = super.getName(stack).getString();
		CompoundTag nbt = data(stack);
		if(nbt != null) itemNameKey = nbt.getString("block");
		ResourceLocation key = ResourceLocation.tryParse(itemNameKey);
		String itemName = itemNameKey;
		if(key != null && BuiltInRegistries.BLOCK.containsKey(key))
			itemName = BuiltInRegistries.BLOCK.get(key).getName().getString();
		String mainKey = "item.lwrdebris.conjunctive";
		if(containsWord(itemName, lang.getOrDefault("item.lwrdebris.conjunctive.switch")))
			mainKey = mainKey + ".alt";
		return lang.getOrDefault(mainKey).replace("{type}", typename).replace("{block}", itemName);
	}
}
