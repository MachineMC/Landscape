package org.machinemc.landscape.containers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.machinemc.landscape.ValueContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Container that stores only a single value, can't be modified.
 */
public class SingleContainer implements ValueContainer {

    private String value;
    private final int dimension;

    public static SingleContainer read(ByteBuffer buffer, int size, int dimension) throws IOException {
        assert size == 1;

        byte[] utf = new byte[buffer.getInt()];
        buffer.get(utf);

        return new SingleContainer(new String(utf), dimension);
    }

    public SingleContainer(String value, int dimension) {
        this.value = value;
        this.dimension = dimension;
    }

    @Override
    public String get(int x, int y, int z) {
        return value;
    }

    @Override
    public void getAll(EntryConsumer consumer) {
        for (int x = 0; x < dimension; x++)
            for (int y = 0; y < dimension; y++)
                for (int z = 0; z < dimension; z++)
                    consumer.accept(x, y, z, value);
    }

    @Override
    public void set(int x, int y, int z, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fill(String value) {
        this.value = value;
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
        return 1;
    }

    @Override
    public String[] getPalette() {
        return new String[]{value};
    }

    @Override
    public int getBitsPerEntry() {
        return 0;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public ByteBuffer serialize() {
        ByteBuf unpooled = Unpooled.buffer();
        unpooled.writeShort(1);
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        unpooled.writeInt(data.length).writeBytes(data);

        ByteBuffer buf = ByteBuffer.allocate(unpooled.writerIndex());
        unpooled.readBytes(buf);
        return buf.rewind();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

}
