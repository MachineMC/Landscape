package me.pesekjak.landscape;

import mx.kenzie.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Represents a 16x16x16 area of blocks in a Landscape region.
 */
public interface Segment {

    static int encode(byte x, byte y, byte z) {
        int index = z << 8;
        index |= y << 4;
        index |= x;
        return index;
    }

    /**
     * @return whether the segment has been generated before
     */
    boolean generated();

    /**
     * @return copy of palette of this segment
     */
    String[] palette();

    /**
     * @return entries in the palette that store nbt compounds
     */
    String[] nbtPalette();

    /**
     * Fills the whole segment with a single value.
     * @param value value to fill the segment with
     */
    void fill(String value);

    default String get(int x, int y, int z) {
        return get((byte) x, (byte) y, (byte) z);
    }

    /**
     * Returns a value located in the segment at given coordinates.
     * @param x relative x coordinate
     * @param y relative y coordinate
     * @param z relative z coordinate
     * @return value at given coordinates
     */
    String get(byte x, byte y, byte z);

    default void set(int x, int y, int z, String value) {
        set((byte) x, (byte) y, (byte) z, value);
    }

    default void set(byte x, byte y, byte z, String value, @Nullable NBTCompound compound) {
        set(x, y, z, value);
        setNBT(x, y, z, compound);
    }

    default void set(int x, int y, int z, String value, @Nullable NBTCompound compound) {
        set(x, y, z, value);
        setNBT(x, y, z, compound);
    }

    /**
     * Changes a single entry in the palette.
     * @param x relative x coordinate
     * @param y relative y coordinate
     * @param z relative z coordinate
     * @param value new value
     */
    void set(byte x, byte y, byte z, String value);

    default @Nullable NBTCompound getNBT(int x, int y, int z) {
        return getNBT((byte) x, (byte) y, (byte) z);
    }

    /**
     * Returns the nbt compound located in the segment at given coordinates.
     * @param x relative x coordinate
     * @param y relative y coordinate
     * @param z relative z coordinate
     * @return nbt compound at given coordinates
     */
    @Nullable NBTCompound getNBT(byte x, byte y, byte z);

    default void setNBT(int x, int y, int z, @Nullable NBTCompound compound) {
        setNBT((byte) x, (byte) y, (byte) z, compound);
    }

    /**
     * Changes nbt compound at given coordinates
     * @param x relative x coordinate
     * @param y relative y coordinate
     * @param z relative z coordinate
     * @param compound new nbt compound
     */
    void setNBT(byte x, byte y, byte z, @Nullable NBTCompound compound);

    default void resetNBT(byte x, byte y, byte z) {
        setNBT(x, y, z, null);
    }

    default void resetNBT(int x, int y, int z) {
        setNBT(x, y, z, null);
    }

    default Entry getEntry(byte x, byte y, byte z) {
        return new Entry(get(x, y, z), getNBT(x, y, z));
    }

    default Entry getEntry(int x, int y, int z) {
        return new Entry(get(x, y, z), getNBT(x, y, z));
    }

    default void setEntry(byte x, byte y, byte z, Entry entry) {
        set(x, y, z, entry.value, entry.nbt);
    }

    default void setEntry(int x, int y, int z, Entry entry) {
        set(x, y, z, entry.value, entry.nbt);
    }

    /**
     * Serializes the segment.
     */
    ByteBuffer serialize();

    /**
     * Pushes the changes of the segment to the Landscape to save.
     */
    void push();

    record Entry(String value, @Nullable NBTCompound nbt) {

    }

}
