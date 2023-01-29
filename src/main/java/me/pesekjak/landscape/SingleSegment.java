package me.pesekjak.landscape;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

public class SingleSegment implements Segment {

    final String value;

    static SingleSegment read(ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        input.read(buf);
        return read(input, buf.rewind().getShort());
    }

    static SingleSegment read(ReadableByteChannel input, short size) throws IOException {
        if(size != 1) throw new IllegalStateException();
        ByteBuffer buf = ByteBuffer.allocate(4);
        input.read(buf);
        byte[] bytes = new byte[buf.rewind().getInt()];
        buf = ByteBuffer.allocate(bytes.length);
        input.read(buf);
        return new SingleSegment(new String(buf.rewind().array()));
    }

    SingleSegment(String value) {
        this.value = value;
    }

    @Override
    public String[] palette() {
        return new String[]{value};
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
        return value;
    }

    @Override
    public ByteBuffer serialize() {
        byte[] palette = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(6 + palette.length);
        buf.putShort((short) 1);
        buf.putInt(palette.length);
        buf.put(palette);
        return buf.rewind();
    }

    @Override
    public void push() {
        throw new UnsupportedOperationException();
    }

}
