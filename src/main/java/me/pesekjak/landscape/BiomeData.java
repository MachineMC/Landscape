package me.pesekjak.landscape;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BiomeData {

    String[] palette;
    byte[] data;

    static BiomeData read(ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        input.read(buf);
        byte length = buf.rewind().get();
        String[] palette = new String[length];
        for (int i = 0; i < length; i++) {
            buf = ByteBuffer.allocate(4);
            input.read(buf);
            byte[] bytes = new byte[buf.rewind().getInt()];
            buf = ByteBuffer.allocate(bytes.length);
            input.read(buf);
            palette[i] = new String(buf.rewind().array());
        }
        if(palette.length == 1) return new BiomeData(palette[0]);
        byte[] data = new byte[64];
        buf = ByteBuffer.allocate(64);
        input.read(buf);
        buf.rewind().get(data);
        return new BiomeData(palette, data);
    }

    static int encode(int x, int y, int z) {
        return encode((byte) x, (byte) y, (byte) z);
    }

    static byte encode(byte x, byte y, byte z) {
        int index = (z/4) << 4;
        index |= (y/4) << 2;
        index |= (x/4);
        return (byte) index;
    }

    BiomeData(String value) {
        palette = new String[]{value};
        data = new byte[0];
    }

    private BiomeData(String[] palette, byte[] data) {
        this.palette = palette;
        this.data = data;
    }

    public String[] palette() {
        return palette.clone();
    }

    public void fill(String value) {
        this.palette = new String[]{value};
        this.data = null;
    }

    public String get(int x, int y, int z) {
        return get((byte) x, (byte) y, (byte) z);
    }

    public String get(byte x, byte y, byte z) {
        if(palette.length == 1) return palette[0];
        return palette[data[encode(x, y, z)]];
    }

    public void set(int x, int y, int z, String value) {
        set((byte) x, (byte) y, (byte) z, value);
    }

    public void set(byte x, byte y, byte z, String value) {
        if(palette.length == 1) {
            if(palette[0].equals(value)) return;
            data = new byte[64];
        }
        int index = -1;
        for (int i = 0; i < palette.length; i++) {
            if(!value.equals(palette[i])) continue;
            index = i;
            break;
        }
        if(index == -1) {
            if(palette.length == 64) {
                reducePalette();
                if(palette.length == 64)
                    throw new IllegalStateException();
            }
            index = palette.length;
            String[] newPalette = new String[index + 1];
            newPalette[index] = value;
            System.arraycopy(palette, 0, newPalette, 0, palette.length);
            palette = newPalette;
        }
        data[encode(x, y, z)] = (byte) index;
    }

    public ByteBuffer serialize() {
        if(palette.length == 1) {
            byte[] paletteData = palette[0].getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(1 + 4 + paletteData.length);
            return buf.put((byte) 0).putInt(paletteData.length).put(paletteData).rewind();
        }
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
        ByteBuffer buf = ByteBuffer.allocate(1 + length + 64);
        buf.put((byte) palette.length);
        for (ByteBuffer palette : buffers)
            buf.put(palette.rewind());
        for (byte datum : data)
            buf.put(datum);
        return buf.rewind();
    }

    private void reducePalette() {
        List<String> reduced = new ArrayList<>();
        byte[] newData = new byte[64];
        for (int i = 0; i < 64; i++) {
            String value = palette[data[i]];
            byte index;
            if((index = (byte) reduced.indexOf(value)) != -1) {
                newData[i] = index;
            } else {
                newData[i] = (byte) reduced.size();
                reduced.add(value);
            }
        }
        palette = reduced.toArray(new String[0]);
        data = newData;
    }

}
