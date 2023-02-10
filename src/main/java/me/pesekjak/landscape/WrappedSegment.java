package me.pesekjak.landscape;

import mx.kenzie.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class WrappedSegment implements Segment {

    final Landscape source;
    final int index;
    Segment wrapped;

    private final Object lock = new Object();

    static WrappedSegment read(Landscape source, int index, ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        input.read(buf);
        short size = buf.rewind().getShort();
        if(size == 0) {
            return new WrappedSegment(source, index, new EmptySegment());
        } else if(size == 1) {
            return new WrappedSegment(source, index, SingleSegment.read(input, size));
        } else if (size <= 256) {
            return new WrappedSegment(source, index, StandardSegment.read(input, size));
        } else {
            return new WrappedSegment(source, index, ComplexSegment.read(input, size));
        }
    }

    private WrappedSegment(Landscape source, int index, Segment wrapped) {
        this.source = source;
        this.index = index;
        this.wrapped = wrapped;
    }

    @Override
    public boolean generated() {
        return !(wrapped instanceof EmptySegment);
    }

    @Override
    public String[] palette() {
        synchronized (lock) {
            return wrapped.palette();
        }
    }

    @Override
    public String[] nbtPalette() {
        synchronized (lock) {
            return wrapped.nbtPalette();
        }
    }

    @Override
    public String[] biomePalette() {
        synchronized (lock) {
            return wrapped.biomePalette();
        }
    }

    @Override
    public BiomeData biomeData() {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            return wrapped.biomeData();
        }
    }

    @Override
    public void fill(String value) {
        synchronized (lock) {
            wrapped = new SingleSegment(value, wrapped.biomeData() != null ? wrapped.biomeData() : new BiomeData(source.properties.defaultBiome()));
        }
    }

    @Override
    public void fillBiome(String value) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            wrapped.fillBiome(value);
        }
    }

    @Override
    public String get(byte x, byte y, byte z) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            return wrapped.get(x, y, z);
        }
    }

    @Override
    public void set(byte x, byte y, byte z, String value) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment)
                wrapped = new StandardSegment(source.properties.defaultValue(), new BiomeData(source.properties.defaultBiome()));
            else if(wrapped instanceof SingleSegment) {
                if(wrapped.nbtPalette().length == 0)
                    wrapped = new StandardSegment(((SingleSegment) wrapped).value, new BiomeData(source.properties.defaultBiome()));
                else
                    wrapped = new StandardSegment(wrapped);
            } else if(wrapped instanceof StandardSegment && wrapped.palette().length == 256)
                wrapped = new ComplexSegment(wrapped);
            wrapped.set(x, y, z, value);
            wrapped.resetNBT(x, y, z);
        }
    }

    @Override
    public @Nullable NBTCompound getNBT(byte x, byte y, byte z) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            return wrapped.getNBT(x, y, z);
        }
    }

    @Override
    public void setNBT(byte x, byte y, byte z, @Nullable NBTCompound compound) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            wrapped.setNBT(x, y, z, compound);
        }
    }

    @Override
    public String getBiome(byte x, byte y, byte z) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            return wrapped.getBiome(x, y, z);
        }
    }

    @Override
    public void setBiome(byte x, byte y, byte z, String value) {
        synchronized (lock) {
            if(wrapped instanceof EmptySegment) createNewSegment();
            wrapped.setBiome(x, y, z, value);
        }
    }

    @Override
    public ByteBuffer serialize() {
        synchronized (lock) {
            if(wrapped instanceof ComplexSegment && wrapped.palette().length <= 256)
                wrapped = new StandardSegment(wrapped);
            return wrapped.serialize();
        }
    }

    @Override
    public void push() {
        synchronized (lock) {
            source.push(this, index);
        }
    }

    private void createNewSegment() {
        wrapped = new SingleSegment(source.properties.defaultValue(), new BiomeData(source.properties.defaultBiome()));
    }

}
