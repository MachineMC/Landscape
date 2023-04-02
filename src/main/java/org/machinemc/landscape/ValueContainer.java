package org.machinemc.landscape;

import java.nio.ByteBuffer;

/**
 * Represents a paletted String container.
 */
public interface ValueContainer {

    /**
     * @return encoded index for the container
     */
    static int index(int x, int y, int z, int dimension) {
        final int bits = (int)(Math.log(dimension-1) / Math.log(2) + 1);
        int index = z << (2*bits);
        index |= y << bits;
        index |= x;
        return index;
    }

    /**
     * Returns value at given coordinates in the container.
     * @param x x coordinate of the value
     * @param y y coordinate of the value
     * @param z z coordinate of the value
     * @return value at given coordinates
     */
    String get(int x, int y, int z);

    /**
     * Accepts all values in the container.
     * @param consumer consumer for all values in container
     */
    void getAll(EntryConsumer consumer);

    /**
     * Changes the value at given coordinates in the container.
     * @param x x coordinate of the value
     * @param y y coordinate of the value
     * @param z z coordinate of the value
     * @param value new value at given coordinates
     */
    void set(int x, int y, int z, String value);

    /**
     * Fills the full container with single value.
     * @param value value to fill the container with
     */
    void fill(String value);

    /**
     * Changes all values in the container.
     * @param supplier supplier
     */
    void setAll(EntrySupplier supplier);

    /**
     * Replaces value at given coordinates with new value.
     * @param x x coordinate of the value
     * @param y y coordinate of the value
     * @param z z coordinate of the value
     * @param value value to replace the old value with
     */
    void replace(int x, int y, int z, String value);

    /**
     * Replaces all values in the container using function.
     * @param function function for all values in container
     */
    void replaceAll(EntryFunction function);

    /**
     * @return number of entries in this container.
     */
    int getCount();

    /**
     * @return copy of the palette
     */
    String[] getPalette();

    /**
     * @return number of bits used per entry.
     */
    int getBitsPerEntry();

    /**
     * @return dimension of the containers
     */
    int getDimension();

    ByteBuffer serialize();

    /**
     * Resets the container to its initial state.
     */
    void reset();

    /**
     * Special supplier used for operations with the containers.
     */
    @FunctionalInterface
    interface EntrySupplier {
        String get(int x, int y, int z);
    }

    /**
     * Special consumer used for operations with the containers.
     */
    @FunctionalInterface
    interface EntryConsumer {
        void accept(int x, int y, int z, String value);
    }

    /**
     * Special function used for operations with the containers.
     */
    @FunctionalInterface
    interface EntryFunction {
        String apply(int x, int y, int z, String value);
    }

}
