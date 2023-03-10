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
 * Container that stores each data value as a short, its maximum palette size is 65536.
 */
public class BigContainer implements ReducingContainer {

    private String[] palette;
    private short[] data;
    private final int dimension;

    public static BigContainer read(ByteBuffer buffer, int size, int dimension) throws IOException {
        assert size <= 256;
        String[] palette = new String[size];
        for (int i = 0; i < size; i++) {
            byte[] utf = new byte[buffer.getInt()];
            buffer.get(utf);
            palette[i] = new String(utf);
        }
        short[] data = new short[dimension*dimension*dimension];
        for (int i = 0; i < data.length; i++)
            data[i] = buffer.getShort();
        return new BigContainer(palette, data, dimension);
    }

    public BigContainer(String value, int dimension) {
        palette = new String[]{value};
        data = new short[dimension*dimension*dimension];
        this.dimension = dimension;
    }

    private BigContainer(String[] palette, short[] data, int dimension) {
        this.palette = palette.clone();
        this.data = data.clone();
        this.dimension = dimension;
    }

    @Override
    public String get(int x, int y, int z) {
        return palette[Short.toUnsignedInt(data[ValueContainer.index(x, y, z, dimension)])];
    }

    @Override
    public void getAll(ValueContainer.EntryConsumer consumer) {
        for (int x = 0; x < dimension; x++)
            for (int y = 0; y < dimension; y++)
                for (int z = 0; z < dimension; z++)
                    consumer.accept(x, y, z, get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, String value) {
        data[ValueContainer.index(x, y, z, dimension)] = (short) getFromPalette(value);
    }

    @Override
    public void fill(String value) {
        palette = new String[]{value};
        data = new short[dimension*dimension*dimension];
    }

    @Override
    public void setAll(ValueContainer.EntrySupplier supplier) {
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
    public void replaceAll(ValueContainer.EntryFunction function) {
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
        return Short.SIZE;
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
        for (short value : data)
            unpooled.writeShort(value);

        ByteBuffer buf = ByteBuffer.allocate(unpooled.writerIndex());
        unpooled.readBytes(buf);
        return buf.rewind();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean reducePalette() {
        List<String> reduced = new ArrayList<>();
        short[] newData = new short[dimension*dimension*dimension];
        for (int i = 0; i < newData.length; i++) {
            String value = palette[Short.toUnsignedInt(data[i])];
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

        if(palette.length == 65536) {
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
