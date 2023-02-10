package me.pesekjak.landscape;

import mx.kenzie.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

/**
 * Segment filled with a single block.
 */
public class SingleSegment implements Segment {

    final String value;
    final byte[][] nbt;
    private int nbtCounter = 0;
    final BiomeData biomeData;

    static SingleSegment read(ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        input.read(buf);
        return read(input, buf.rewind().getShort());
    }

    static SingleSegment read(ReadableByteChannel input, short size) throws IOException {
        if(size != 1) throw new IllegalStateException();
        ByteBuffer buf = ByteBuffer.allocate(4);
        input.read(buf);
        buf = ByteBuffer.allocate(buf.rewind().getInt());
        input.read(buf);
        String value = new String(buf.rewind().array());

        buf = ByteBuffer.allocate(2);
        input.read(buf);
        short nbtPalette = buf.rewind().getShort();
        if(nbtPalette == 0)
            return new SingleSegment(value, BiomeData.read(input));

        byte[][] nbt = new byte[4096][];
        buf = ByteBuffer.allocate(4);
        input.read(buf);
        buf = ByteBuffer.allocate(buf.rewind().getInt());
        input.read(buf);
        try (ByteArrayInputStream is = new ByteArrayInputStream(buf.rewind().array())) {
            for (int i = 0; i < 4096; i++) {
                NBTCompound compound = new NBTCompound();
                compound.read(is);
                if(compound.size() == 0) continue;
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                compound.write(os);
                nbt[i] = os.toByteArray();
            }
            return new SingleSegment(value, nbt, BiomeData.read(input));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    SingleSegment(String value, BiomeData biomeData) {
        this(value, new byte[4096][], biomeData);
    }

    SingleSegment(String value, byte[][] nbt, BiomeData biomeData) {
        this.value = value;
        if(nbt.length != 4096)
            throw new IllegalStateException();
        this.nbt = nbt;
        for (byte[] compound : nbt) {
            if(compound != null) nbtCounter++;
        }
        this.biomeData = biomeData;
    }

    @Override
    public boolean generated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] palette() {
        return new String[]{value};
    }

    @Override
    public String[] nbtPalette() {
        return nbtCounter != 0 ? new String[]{value} : new String[0];
    }

    @Override
    public String[] biomePalette() {
        return biomeData.palette();
    }

    @Override
    public BiomeData biomeData() {
        return biomeData;
    }

    @Override
    public void fill(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fillBiome(String value) {
        biomeData.fill(value);
    }

    @Override
    public String get(byte x, byte y, byte z) {
        return value;
    }

    @Override
    public void set(byte x, byte y, byte z, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable NBTCompound getNBT(byte x, byte y, byte z) {
        byte[] data = nbt[Segment.encode(x, y, z)];
        if(data == null) return null;
        try (ByteArrayInputStream is = new ByteArrayInputStream(data)) {
            return new NBTCompound(is);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void setNBT(byte x, byte y, byte z, @Nullable NBTCompound compound) {
        int index = Segment.encode(x, y, z);
        if(compound == null || compound.size() == 0) {
            if(nbt[index] != null) {
                nbtCounter--;
                nbt[index] = null;
            }
            return;
        }
        if(nbt[index] == null)
            nbtCounter++;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            compound.write(os);
            nbt[index] = os.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public String getBiome(byte x, byte y, byte z) {
        return biomeData.get(x, y, z);
    }

    @Override
    public void setBiome(byte x, byte y, byte z, String value) {
        biomeData.set(x, y, z, value);
    }

    @Override
    public ByteBuffer serialize() {
        byte[] palette = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8 + palette.length);
        header.putShort((short) 1);
        header.putInt(palette.length);
        header.put(palette);
        if(nbtCounter == 0) {
            header.putShort((short) 0).rewind();
            ByteBuffer biomes = biomeData.serialize();
            return ByteBuffer.allocate(header.capacity() + biomes.capacity()).put(header).put(biomes).rewind();
        }
        header.putShort((short) 1);

        int length = 0;
        byte[] emptyCompound;

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            new NBTCompound().write(os);
            emptyCompound = os.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        for (byte[] compound : nbt)
            length += compound != null ? compound.length : emptyCompound.length;

        ByteBuffer buf = ByteBuffer.allocate(header.capacity() + 4 + length);
        buf.put(header.rewind());
        buf.putInt(length);
        for (byte[] compound : nbt)
            buf.put(compound != null ? compound : emptyCompound);

        buf.rewind();
        ByteBuffer biomes = biomeData.serialize();
        return ByteBuffer.allocate(header.capacity() + biomes.capacity()).put(buf).put(biomes).rewind();
    }

    @Override
    public void push() {
        throw new UnsupportedOperationException();
    }

}
