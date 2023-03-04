package org.machinemc.landscape.containers;

import org.machinemc.landscape.ValueContainer;

import java.nio.ByteBuffer;

public class EmptyContainer implements ValueContainer {

    public static EmptyContainer read(int size) {
        assert size == 0;
        return new EmptyContainer();
    }

    @Override
    public String get(int x, int y, int z) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getAll(EntryConsumer consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void set(int x, int y, int z, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fill(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAll(EntrySupplier supplier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replace(int x, int y, int z, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(EntryFunction function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public String[] getPalette() {
        return new String[0];
    }

    @Override
    public int getBitsPerEntry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDimension() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuffer serialize() {
        return ByteBuffer.allocate(2).putShort((short) 0).rewind();
    }

}
