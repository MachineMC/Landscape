package me.pesekjak.landscape;

import java.io.DataOutput;
import java.io.IOException;
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

    default void set(int x, int y, int z, String value) {
        set((byte) x, (byte) y, (byte) z, value);
    }

    /**
     * Changes a single entry in the palette.
     * @param x relative x coordinate
     * @param y relative y coordinate
     * @param z relative z coordinate
     * @param value new value
     */
    void set(byte x, byte y, byte z, String value);

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

    /**
     * Serializes the segment.
     * @param output output
     */
    ByteBuffer serialize();

    /**
     * Pushes the changes of the segment to the Landscape to save.
     */
    void push();

}
