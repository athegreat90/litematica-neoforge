package fi.dy.masa.litematica.util;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block state NBT in the key layout Litematica schematics use ({@code Name} / {@code Properties}).
 * <p>
 * Minecraft 26.3 changed {@link NbtUtils#readBlockState} / {@link NbtUtils#writeBlockState} to the keys
 * {@code id} / {@code properties}. Reading a {@code .litematic} palette with the vanilla method therefore
 * turned every entry into air, and writing with it would produce files other Litematica versions can't read.
 * These helpers translate between the two layouts and let vanilla do the actual parsing.
 */
public class BlockStateNbt
{
	private static final String LEGACY_NAME = "Name";
	private static final String LEGACY_PROPERTIES = "Properties";
	private static final String VANILLA_NAME = "id";
	private static final String VANILLA_PROPERTIES = "properties";

	/**
	 * Reads a block state tag in either layout ({@code Name}/{@code Properties} or {@code id}/{@code properties}).
	 */
	public static BlockState readBlockState(HolderGetter<Block> lookup, CompoundTag tag)
	{
		if (tag.contains(LEGACY_NAME) && tag.contains(VANILLA_NAME) == false)
		{
			CompoundTag converted = tag.copy();
			renameKey(converted, LEGACY_NAME, VANILLA_NAME);
			renameKey(converted, LEGACY_PROPERTIES, VANILLA_PROPERTIES);
			return NbtUtils.readBlockState(lookup, converted);
		}

		return NbtUtils.readBlockState(lookup, tag);
	}

	/**
	 * Writes a block state tag in the {@code Name}/{@code Properties} layout of Litematica schematics.
	 */
	public static CompoundTag writeBlockState(BlockState state)
	{
		CompoundTag tag = NbtUtils.writeBlockState(state);
		renameKey(tag, VANILLA_NAME, LEGACY_NAME);
		renameKey(tag, VANILLA_PROPERTIES, LEGACY_PROPERTIES);
		return tag;
	}

	private static void renameKey(CompoundTag tag, String from, String to)
	{
		Tag value = tag.remove(from);

		if (value != null)
		{
			tag.put(to, value);
		}
	}
}
