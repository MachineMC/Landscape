package me.pesekjak.landscape;

import java.nio.ByteBuffer;

public class EmptySegment implements Segment {

    @Override
    public String[] palette() {
        return new String[0];
    }

    @Override
    public String[] nbtPalette() {
        return new String[0];
    }

    @Override
    public void fill(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void set(byte x, byte y, byte z, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String get(byte x, byte y, byte z) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuffer serialize() {
        return ByteBuffer.allocate(2).putShort((short) 0).rewind();
    }

    @Override
    public void push() {
        throw new UnsupportedOperationException();
    }

}
