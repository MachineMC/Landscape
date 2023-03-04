package org.machinemc.landscape.containers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.machinemc.landscape.ValueContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Container that stores each data value as a byte, its maximum palette size is 256.
 */
public class SmallContainer implements ReducingContainer {

    private String[] palette;
    private byte[] data;
    private final int dimension;

    public static SmallContainer read(ByteBuffer buffer, int size, int dimension) throws IOException {
        assert size <= 256;
        String[] palette = new String[size];
        for (int i = 0; i < size; i++) {
            byte[] utf = new byte[buffer.getInt()];
            buffer.get(utf);
            palette[i] = new String(utf);
        }
        byte[] data = new byte[dimension*dimension*dimension];
        buffer.get(data);
        return new SmallContainer(palette, data, dimension);
    }

    public SmallContainer(String value, int dimension) {
        palette = new String[]{value};
        data = new byte[dimension*dimension*dimension];
        this.dimension = dimension;
    }

    private SmallContainer(String[] palette, byte[] data, int dimension) {
        this.palette = palette.clone();
        this.data = data.clone();
        this.dimension = dimension;
    }

    @Override
    public String get(int x, int y, int z) {
        return palette[Byte.toUnsignedInt(data[ValueContainer.index(x, y, z, dimension)])];
    }

    @Override
    public void getAll(EntryConsumer consumer) {
        for (int x = 0; x < dimension; x++)
            for (int y = 0; y < dimension; y++)
                for (int z = 0; z < dimension; z++)
                    consumer.accept(x, y, z, get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, String value) {
        data[ValueContainer.index(x, y, z, dimension)] = (byte) getFromPalette(value);
    }

    @Override
    public void fill(String value) {
        palette = new String[]{value};
        data = new byte[dimension*dimension*dimension];
    }

    @Override
    public void setAll(EntrySupplier supplier) {
        for (int x = 0; x < dimension; x++)
            for (int y = 0; y < dimension; y++)
                for (int z = 0; z < dimension; z++)
                    set(x, y, z, supplier.get(x, y, z));
    }

    @Override
    public void replace(int x, int y, int z, String value) {
        set(x, y, z, value);
    }

    @Override
    public void replaceAll(EntryFunction function) {
        for (int x = 0; x < dimension; x++)
            for (int y = 0; y < dimension; y++)
                for (int z = 0; z < dimension; z++)
                    set(x, y, z, function.apply(x, y, z, get(x, y, z)));
    }

    @Override
    public int getCount() {
        return palette.length;
    }

    @Override
    public String[] getPalette() {
        return palette.clone();
    }

    @Override
    public int getBitsPerEntry() {
        return Byte.SIZE;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public ByteBuffer serialize() {
        reducePalette();

        ByteBuf unpooled = Unpooled.buffer();
        unpooled.writeShort(palette.length);
        for (String value : palette) {
            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            unpooled.writeInt(data.length).writeBytes(data);
        }
        unpooled.writeBytes(data);

        ByteBuffer buf = ByteBuffer.allocate(unpooled.writerIndex());
        unpooled.readBytes(buf);
        return buf.rewind();
    }

    @Override
    public boolean reducePalette() {
        List<String> reduced = new ArrayList<>();
        byte[] newData = new byte[dimension*dimension*dimension];
        for (int i = 0; i < newData.length; i++) {
            String value = palette[Byte.toUnsignedInt(data[i])];
            int index;
            if((index = reduced.indexOf(value)) != -1) {
                newData[i] = (byte) index;
            } else {
                newData[i] = (byte) reduced.size();
                reduced.add(value);
            }
        }

        final boolean isReduced = reduced.size() < palette.length;
        palette = reduced.toArray(new String[0]);
        data = newData;

        return isReduced;
    }

    private int getFromPalette(String value) {
        for (int i = 0; i < palette.length; i++) {
            if(palette[i].equals(value)) return i;
        }

        if(palette.length == 256) {
            if(!reducePalette()) throw new UnsupportedOperationException();
        }

        int index = palette.length;
        String[] newPalette = new String[index + 1];
        System.arraycopy(palette, 0, newPalette, 0, palette.length);
        newPalette[index] = value;
        palette = newPalette;
        return index;
    }

}
