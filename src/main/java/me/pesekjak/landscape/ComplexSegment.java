package me.pesekjak.landscape;

import mx.kenzie.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Segment where the size of palette can be up to 4096 (each block can be unique).
 */
public class ComplexSegment implements Segment {

    String[] palette;
    short[] data;
    final byte[][] nbt;

    static ComplexSegment read(ReadableByteChannel input) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        input.read(buf);
        return read(input, buf.rewind().getShort());
    }

    static ComplexSegment read(ReadableByteChannel input, short length) throws IOException {
        String[] palette = new String[length];
        for (int i = 0; i < length; i++) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            input.read(buf);
            byte[] bytes = new byte[buf.rewind().getInt()];
            buf = ByteBuffer.allocate(bytes.length);
            input.read(buf);
            palette[i] = new String(buf.rewind().array());
        }
        short[] data = new short[4096];
        ByteBuffer buf = ByteBuffer.allocate(data.length * 2);
        input.read(buf);
        buf.rewind();
        for (int i = 0; i < data.length; i++)
            data[i] = buf.getShort();

        buf = ByteBuffer.allocate(2);
        input.read(buf);
        short nbtSize = buf.rewind().getShort();

        short[] nbtPaletteMap = new short[nbtSize];
        buf = ByteBuffer.allocate(nbtSize * 2);
        input.read(buf);
        buf.rewind();
        for (int i = 0; i < nbtPaletteMap.length; i++)
            nbtPaletteMap[i] = buf.getShort();

        buf = ByteBuffer.allocate(4);
        input.read(buf);
        int nbtLength = buf.rewind().getInt();

        ByteArrayInputStream is;
        {
            byte[] nbtData = new byte[nbtLength];
            buf = ByteBuffer.allocate(nbtLength);
            input.read(buf);
            buf.rewind().get(nbtData);
            is = new ByteArrayInputStream(nbtData);
        }

        byte[][] nbt = new byte[4096][];
        for (int i = 0; i < data.length; i++) {
            for (short j : nbtPaletteMap) {
                int unsigned = Short.toUnsignedInt(j);
                if(unsigned != data[i]) continue;
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                NBTCompound compound = new NBTCompound(is);
                if(compound.size() != 0) {
                    compound.write(os);
                    nbt[i] = os.toByteArray();
                }
                break;
            }
        }

        return new ComplexSegment(palette, data, nbt);
    }

    ComplexSegment(String value) {
        this(value, new byte[4096][]);
    }

    ComplexSegment(String value, byte[][] nbt) {
        this.palette = new String[]{value};
        this.data = new short[4096];
        this.nbt = nbt;
    }

    ComplexSegment(Segment source) {
        this.palette = source.palette();
        this.data = new short[4096];
        this.nbt = new byte[4096][];
        if(palette.length == 1 && source.nbtPalette().length == 0)
            return;
        for (byte x = 0; x < 16; x++) {
            for (byte y = 0; y < 16; y++) {
                for (byte z = 0; z < 16; z++) {
                    set(x, y, z, source.get(x, y, z));
                    setNBT(x, y, z, source.getNBT(x, y, z));
                }
            }
        }
    }

    private ComplexSegment(String[] palette, short[] data) {
        this(palette, data, new byte[4096][]);
    }

    private ComplexSegment(String[] palette, short[] data, byte[][] nbt) {
        if(palette.length > 4096) throw new IllegalStateException();
        if(data.length != 4096) throw new IllegalStateException();
        this.palette = palette;
        this.data = data;
        this.nbt = nbt;
    }

    @Override
    public boolean generated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] palette() {
        return palette.clone();
    }

    @Override
    public String[] nbtPalette() {
        List<String> nbtPalette = new ArrayList<>();
        for (int i = 0; i < nbt.length; i++) {
            if(nbt[i] == null) continue;
            int index = data[i];
            if(nbtPalette.size() > index) {
                nbtPalette.set(index, palette[index]);
                continue;
            }
            nbtPalette.add(index, palette[index]);
        }
        return nbtPalette.stream().filter(Objects::nonNull).toArray(String[]::new);
    }

    @Override
    public void fill(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String get(byte x, byte y, byte z) {
        return palette[Short.toUnsignedInt(data[Segment.encode(x, y, z)])];
    }

    @Override
    public void set(byte x, byte y, byte z, String value) {
        if(palette.length == 4096)
            reducePalette();
        int index = -1;
        for (int i = 0; i < palette.length; i++) {
            if(palette[i].equals(value)) {
                index = i;
                break;
            }
        }
        if(index == -1) {
            if(palette.length == 4096)
                throw new UnsupportedOperationException();
            index = palette.length;
            String[] newPalette = new String[index + 1];
            System.arraycopy(palette, 0, newPalette, 0, palette.length);
            newPalette[index] = value;
            palette = newPalette;
        }
        data[Segment.encode(x, y, z)] = (short) index;
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
            nbt[index] = null;
            return;
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            compound.write(os);
            nbt[index] = os.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
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
        ByteBuffer buf = ByteBuffer.allocate(2 + length + (4096 * 2) + 2);
        buf.putShort((short) palette.length);
        for (ByteBuffer palette : buffers)
            buf.put(palette.rewind());
        for (short datum : data)
            buf.putShort(datum);

        String[] nbtPalette = nbtPalette();

        if(nbtPalette.length == 0)
            return buf.putShort((short) 0).rewind();

        short[] nbtPaletteMap = new short[nbtPalette.length];
        for (int i = 0; i < nbtPaletteMap.length; i++) {
            for (int j = 0; j < palette.length; j++) {
                if(!palette[j].equals(nbtPalette[i])) continue;
                nbtPaletteMap[i] = (short) j;
                break;
            }
        }

        buf.putShort((short) nbtPalette.length);

        length = 0;
        byte[] emptyCompound;

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            new NBTCompound().write(os);
            emptyCompound = os.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        List<byte[]> allNBT = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            for (short j : nbtPaletteMap) {
                if (data[i] != j) continue;
                byte[] nbt = this.nbt[i] != null ? this.nbt[i] : emptyCompound;
                length += nbt.length;
                allNBT.add(nbt);
                break;
            }
        }

        buf = ByteBuffer.allocate(buf.capacity() + (nbtPalette.length * 2) + 4 + length).put(buf.rewind());

        for (short s : nbtPaletteMap)
            buf.putShort(s);
        buf.putInt(length);
        for (byte[] nbt : allNBT)
            buf.put(nbt);

        return buf.rewind();
    }

    @Override
    public void push() {
        throw new UnsupportedOperationException();
    }

    private void reducePalette() {
        List<String> reduced = new ArrayList<>();
        short[] newData = new short[4096];
        for (int i = 0; i < 4096; i++) {
            String value = palette[Short.toUnsignedInt(data[i])];
            int index;
            if((index = reduced.indexOf(value)) != -1) {
                newData[i] = (short) index;
            } else {
                newData[i] = (short) reduced.size();
                reduced.add(value);
            }
        }
        palette = reduced.toArray(new String[0]);
        data = newData;
    }

}
