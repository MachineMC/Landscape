package me.pesekjak.landscape;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StandardSegment implements Segment {

    String[] palette;
    byte[] data;

    static StandardSegment read(ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        input.read(buf);
        return read(input, buf.rewind().getShort());
    }

    static StandardSegment read(ReadableByteChannel input, short length) throws IOException {
        String[] palette = new String[length];
        for (int i = 0; i < length; i++) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            input.read(buf);
            byte[] bytes = new byte[buf.rewind().getInt()];
            buf = ByteBuffer.allocate(bytes.length);
            input.read(buf);
            palette[i] = new String(buf.rewind().array());
        }
        byte[] data = new byte[4096];
        ByteBuffer buf = ByteBuffer.allocate(data.length);
        input.read(buf);
        buf.rewind().get(data);
        return new StandardSegment(palette, data);
    }

    StandardSegment(String value) {
        this.palette = new String[]{value};
        this.data = new byte[4096];
    }

    StandardSegment(Segment source) {
        this.palette = source.palette();
        if(palette.length == 1) {
            this.data = new byte[4096];
            return;
        }
        for (byte x = 0; x < 16; x++) {
            for (byte y = 0; y < 16; y++) {
                for (byte z = 0; z < 16; z++) {
                    set(x, y, z, source.get(x, y, z));
                }
            }
        }
    }

    private StandardSegment(String[] palette, byte[] data) {
        if(palette.length > 256) throw new IllegalStateException();
        if(data.length != 4096) throw new IllegalStateException();
        this.palette = palette;
        this.data = data;
    }

    @Override
    public String[] palette() {
        return palette.clone();
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
        int index = -1;
        for (int i = 0; i < palette.length; i++) {
            if(palette[i].equals(value)) {
                index = i;
                break;
            }
        }
        if(index == -1) {
            if(palette.length == 256)
                throw new UnsupportedOperationException();
            index = palette.length;
            String[] newPalette = new String[index + 1];
            System.arraycopy(palette, 0, newPalette, 0, palette.length);
            newPalette[index] = value;
            palette = newPalette;
        }
        data[Segment.encode(x, y, z)] = (byte) index;
    }

    @Override
    public String get(byte x, byte y, byte z) {
        return palette[Byte.toUnsignedInt(data[Segment.encode(x, y, z)])];
    }

    @Override
    public ByteBuffer serialize() {
        reducePalette();
        ByteBuffer[] buffers = new ByteBuffer[palette.length];
        int length = 0;
        for (int i = 0; i < palette.length; i++) {
            byte[] data = palette[i].getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(4 + data.length);
            buf.putInt(data.length);
            buf.put(data);
            buffers[i] = buf;
            length += buf.position();
        }
        ByteBuffer buf = ByteBuffer.allocate(2 + length + 4096);
        buf.putShort((short) palette.length);
        for (ByteBuffer palette : buffers)
            buf.put(palette.rewind());
        buf.put(data);
        return buf.rewind();
    }

    @Override
    public void push() {
        throw new UnsupportedOperationException();
    }

    private void reducePalette() {
        List<String> reduced = new ArrayList<>();
        byte[] newData = new byte[4096];
        for (int i = 0; i < 4096; i++) {
            String value = palette[Byte.toUnsignedInt(data[i])];
            int index;
            if((index = reduced.indexOf(value)) != -1) {
                newData[i] = (byte) index;
            } else {
                newData[i] = (byte) reduced.size();
                reduced.add(value);
            }
        }
        palette = reduced.toArray(new String[0]);
        data = newData;
    }

}
