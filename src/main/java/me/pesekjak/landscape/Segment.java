package me.pesekjak.landscape;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mx.kenzie.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * Represents a Landscape Segment (16x16x16 area of blocks).
 * <p>
 * Each segment contains block palette, biome palette (4x4x4) and
 * additional compound where extra data can be saved to, all operations
 * are synchronized internally.
 * <p>
 * Segments can be read from and will be forgotten once they are no longer
 * referenced, to write changes to the segment that should be saved
 * later {@link Segment#push()} is used.
 */
public class Segment {

    private static final int BLOCKS_DIMENSION = 16;
    private static final int BIOMES_DIMENSION = 4;
    private static final int ENTRIES = 4096; // BLOCKS_DIMENSION^3

    private final Landscape source;
    private final LandscapeHandler handler;
    private final int index;

    private final ValueContainer blocks;
    private final ValueContainer biomes;

    private final NBTCompound[] nbt;

    private final BitSet tickingBlocks;

    private final NBTCompound data;

    private final Object lock = new Object();

    protected Segment(Landscape source, int index) {
        this.source = source;
        this.index = index;
        this.handler = source.handler;

        blocks = new WrapperContainer(BLOCKS_DIMENSION, handler::getDefaultType);
        biomes = new WrapperContainer(BIOMES_DIMENSION, handler::getDefaultBiome);

        nbt = new NBTCompound[ENTRIES];
        tickingBlocks = new BitSet(ENTRIES);

        data = new NBTCompound();
    }

    protected Segment(Landscape source, int index, ByteBuffer buf) throws IOException {
        this.source = source;
        this.index = index;
        this.handler = source.handler;

        blocks = WrapperContainer.read(buf, BLOCKS_DIMENSION, handler::getDefaultType);
        biomes = WrapperContainer.read(buf, BIOMES_DIMENSION, handler::getDefaultType);

        nbt = new NBTCompound[ENTRIES];
        BitSet nbtPositions = readBitSet(buf);
        if(!nbtPositions.isEmpty()) {
            for (int i = 0; i < ENTRIES; i++) {
                if(!nbtPositions.get(i)) continue;
                nbt[i] = readCompound(buf);
            }
        }

        tickingBlocks = readBitSet(buf);

        data = readCompound(buf);
    }

    public Landscape getSource() {
        return source;
    }

    public int getIndex() {
        return index;
    }

    public NBTCompound getDataCompound() {
        return data;
    }

    public void push() {
        source.push(this, index);
    }

    public String getBlock(int x, int y, int z) {
        return blocks.get(x, y, z);
    }

    public NBTCompound getNBT(int x, int y, int z) {
        synchronized (lock) {
            final int index = ValueContainer.index(x, y, z, BLOCKS_DIMENSION);
            if (nbt[index] != null)
                return nbt[index];
            nbt[index] = new NBTCompound();
            return nbt[index];
        }
    }

    public boolean isTicking(int x, int y, int z) {
        synchronized (lock) {
            return tickingBlocks.get(ValueContainer.index(x, y, z, BLOCKS_DIMENSION));
        }
    }

    public void getAllTicking(CoordinatesConsumer consumer) {
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    if(isTicking(x, y, z)) consumer.accept(x, y, z);
    }

    public void setBlock(int x, int y, int z, String type, @Nullable NBTCompound compound, boolean isTicking) {
        blocks.set(x, y, z, type);
        final int index = ValueContainer.index(x, y, z, BLOCKS_DIMENSION);
        synchronized (lock) {
            nbt[index] = compound;
            tickingBlocks.set(index, isTicking);
        }
    }

    public String getBiome(int x, int y, int z) {
        return biomes.get(x / BIOMES_DIMENSION, y / BIOMES_DIMENSION, z / BIOMES_DIMENSION);
    }

    public void setBiome(int x, int y, int z, String type) {
        biomes.set(x / BIOMES_DIMENSION, y / BIOMES_DIMENSION, z / BIOMES_DIMENSION, type);
    }

    public ByteBuffer serialize() {
        ByteBuf unpooled = Unpooled.buffer();
        unpooled.writeBytes(blocks.serialize());
        unpooled.writeBytes(biomes.serialize());

        synchronized (lock) {
            BitSet nbtPositions = new BitSet(ENTRIES);
            for (int i = 0; i < nbt.length; i++) {
                if (nbt[i] == null || nbt[i].isEmpty()) continue;
                nbtPositions.set(i);
            }
            writeBitSet(unpooled, nbtPositions);
            for (NBTCompound compound : nbt) {
                if (compound != null) writeCompound(unpooled, compound);
            }

            writeBitSet(unpooled, tickingBlocks);

            writeCompound(unpooled, data);
        }

        ByteBuffer buf = ByteBuffer.allocate(unpooled.writerIndex());
        unpooled.readBytes(buf);
        return buf.rewind();
    }

    private BitSet readBitSet(ByteBuffer buf) {
        byte[] data = new byte[Byte.toUnsignedInt(buf.get())];
        buf.get(data);
        return BitSet.valueOf(data);
    }

    private void writeBitSet(ByteBuf buf, BitSet bitSet) {
        byte[] data = bitSet.toByteArray();
        buf.writeByte(data.length);
        buf.writeBytes(data);
    }

    private NBTCompound readCompound(ByteBuffer buf) {
        byte[] data = new byte[buf.getInt()];
        buf.get(data);
        try (ByteArrayInputStream is = new ByteArrayInputStream(data)) {
            NBTCompound compound = new NBTCompound();
            compound.readAll(is);
            return compound;
        } catch (Exception exception) {
            exception.printStackTrace();
            return new NBTCompound();
        }
    }

    private void writeCompound(ByteBuf buf, NBTCompound compound) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        compound.writeAll(os);
        byte[] data = os.toByteArray();
        buf.writeInt(data.length).writeBytes(data);
    }

    @FunctionalInterface
    interface CoordinatesConsumer {
        void accept(int x, int y, int z);
    }

}
