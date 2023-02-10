package me.pesekjak.landscape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;

/**
 * Represents a region file container storing 16x16 area of Minecraft chunks.
 */
public class Landscape {

    public static final short VERSION = 1;

    private final static Set<Landscape> cache = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    final File file;
    SeekableByteChannel channel;
    final int x, y;
    final short height;
    final Properties properties;
    final short version = VERSION;

    Segment[] segments;
    Map<Segment, Integer> weakSegments = new WeakHashMap<>();

    private static final int HEADER_SIZE = 12;
    private final int LOOKUP_SIZE; // depends on height

    private static final int HEADER_X_POS = 0;
    private static final int HEADER_Y_POS = 4;
    private static final int HEADER_HEIGHT_POS = 8;
    private static final int HEADER_VERSION_POS = 10;

    private final Object lock = new Object();

    public static Landscape of(@NotNull File directory, int x, int y, short height, Properties properties) {
        try {
            return of0(directory, x , y, height, properties);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Landscape of0(@NotNull File directory, int x, int y, short height, Properties properties) throws IOException {
        final File file = new File(directory, "r_" + x + "_" + y + ".ls");
        for(Landscape landscape : cache) {
            if(landscape.file.equals(file)) return landscape;
        }
        return new Landscape(file, x, y, height, properties);
    }

    private Landscape(@NotNull File source, int x, int y, short height, Properties properties) throws IOException {
        final OpenOption[] options;
        if(source.exists())
            options = new OpenOption[] {
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SPARSE,
            };
        else
            options = new OpenOption[] {
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SPARSE,
                    StandardOpenOption.CREATE_NEW
            };

        openChannel(source.toPath(), options);

        if(height < 16 || height % 16 != 0)
            throw new IllegalStateException();
        this.x = x;
        this.y = y;
        this.height = height;
        this.properties = properties;
        file = source;
        segments = new Segment[height / 16 * 16 * 16]; // amount of 16x16x16 sections in the whole file
        LOOKUP_SIZE = height / 16 * 16 * 16 * 4;
        if(channel.size() == 0) {
            writeDefaults();
        } else {
            checkValidity();
        }
    }

    private Landscape(int x, int y, short height, Properties properties) throws IOException {
        Path path = Files.createTempFile("temp_r_" + x + y + ".ls", "");
        openChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.SPARSE, StandardOpenOption.DELETE_ON_CLOSE);
        if(height < 16 || height % 16 != 0)
            throw new IllegalStateException();
        this.x = x;
        this.y = y;
        this.height = height;
        this.properties = properties;
        file = path.toFile();
        segments = new Segment[height / 16 * 16 * 16];
        LOOKUP_SIZE = height / 16 * 16 * 16 * 4;
        writeDefaults();
    }

    private void openChannel(Path path, OpenOption... options) throws IOException {
        if(channel != null && channel.isOpen()) throw new IllegalStateException();
        channel = Files.newByteChannel(path, options);
    }

    private void reopenChannel() throws IOException {
        if(file == null || (channel != null && channel.isOpen())) throw new IllegalStateException();
        channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ,
                StandardOpenOption.WRITE, StandardOpenOption.SPARSE);
    }

    public File getFile() {
        return file;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public short getHeight() {
        return height;
    }

    @Nullable Segment getSegment(int index) {
        synchronized (lock) {
            if(segments[index] != null)
                return segments[index];
            if(!weakSegments.containsValue(index)) return null;
            for (Segment weak : weakSegments.keySet()) {
                if(weakSegments.get(weak) == index)
                    return weak;
            }
        }
        return null;
    }

    public Segment loadSegment(int x, int y, int z) {
        return loadSegment(segmentIndex(x, y, z));
    }

    Segment loadSegment(int index) {
        synchronized (lock) {
            if(segments[index] != null)
                return segments[index];
            for (Segment weak : weakSegments.keySet()) {
                if(weakSegments.get(weak) == index)
                    return weak;
            }
            try {
                Segment segment = readSegment(index);
                weakSegments.put(segment, index);
                return segment;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    void push(Segment segment, int index) {
        synchronized (lock) {
            segments[index] = segment;
        }
    }

    public void flush() {
        final Segment[] toFlush = new Segment[height / 16 * 16 * 16];
        synchronized (lock) {
            try {
                if (!channel.isOpen()) reopenChannel();
            } catch (IOException exception) {
                return;
            }
            if (weakSegments.size() == 0) {
                boolean empty = true;
                for (Segment segment : segments) {
                    if (segment != null) {
                        empty = false;
                        break;
                    }
                }
                if (empty) return;
            }
            try {
                System.arraycopy(segments, 0, toFlush, 0, segments.length);
                for (Map.Entry<Segment, Integer> weakEntry : weakSegments.entrySet())
                    toFlush[weakEntry.getValue()] = weakEntry.getKey();
                int startPos = 0;
                for (int i = 0; i < toFlush.length; i++) {
                    if(toFlush[i] != null) {
                        startPos = i;
                        break;
                    }
                }
                for (int i = startPos; i < toFlush.length; i++) {
                    if (toFlush[i] != null) continue;
                    toFlush[i] = readSegment(i);
                }
                channel.position(HEADER_SIZE + LOOKUP_SIZE);
                for (int i = startPos; i < toFlush.length; i++) {
                    int position = (int) channel.position();
                    channel.position(HEADER_SIZE + (i * 4L));
                    channel.write(ByteBuffer.allocate(4).putInt(position).rewind());
                    channel.position(position);
                    channel.write(toFlush[i].serialize());
                }
                channel.truncate(channel.position());
                segments = new Segment[height / 16 * 16 * 16];
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if(!channel.isOpen()) return;
            channel.close();
        }
    }

    private int segmentIndex(int x, int y, int z) {
        int index = y << 8;
        index |= z << 4;
        index |= x;
        return index;
    }

    private Segment readSegment(int index) throws IOException {
        if(!channel.isOpen())
            reopenChannel();
        channel.position(HEADER_SIZE + (index * 4L));
        ByteBuffer buf = ByteBuffer.allocate(4);
        channel.read(buf);
        channel.position(buf.rewind().getInt());
        return WrappedSegment.read(this, index, channel);
    }

    private void writeDefaults() throws IOException {
        writeHeader();
        Segment empty = new EmptySegment();
        Arrays.fill(segments, empty);
        flush();
    }

    private void checkValidity() throws IOException {
        if(!channel.isOpen())
            reopenChannel();
        channel.position(HEADER_HEIGHT_POS);
        ByteBuffer buf = ByteBuffer.allocate(2);
        channel.read(buf);
        short fileHeight = buf.rewind().getShort();
        if(height == fileHeight)
            return;

        final Segment[] toFlush = new Segment[height / 16 * 16 * 16];
        Arrays.fill(toFlush, new EmptySegment());

        Landscape temp = new Landscape(x, y, fileHeight, properties);
        ByteBuffer source = ByteBuffer.allocate((int) channel.size());
        temp.channel.truncate(0);
        channel.position(0).read(source);
        temp.channel.write(source);
        int yMax = Math.min(fileHeight, height) / 16;
        int index;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < yMax; y++) {
                for (int z = 0; z < 16; z++) {
                    index = segmentIndex(x, y, z);
                    toFlush[index] = temp.loadSegment(index);
                }
            }
        }
        temp.close();
        channel.truncate(0);
        writeHeader();
        segments = toFlush;
        flush();
    }

    private void writeHeader() throws IOException {
        if(!channel.isOpen())
            reopenChannel();
        channel.position(0);
        channel.write(
                ByteBuffer.allocate(12)
                .putInt(x)
                .putInt(y)
                .putShort(height)
                .putShort(VERSION)
                .rewind()
        );
    }

    public interface Properties {

        static Properties of(@NotNull String defaultValue, @NotNull String defaultBiome) {
            return new SimpleProperties(defaultValue, defaultBiome);
        }

        static Properties of(@NotNull Supplier<String> defaultValue, @NotNull Supplier<String> defaultBiome) {
            return new DynamicProperties(defaultValue, defaultBiome);
        }

        @NotNull String defaultValue();

        @NotNull String defaultBiome();

    }

    static record SimpleProperties(@NotNull String defaultValue, @NotNull String defaultBiome) implements Properties {

    }

    static class DynamicProperties implements Properties {

        final Supplier<String> defaultValue;
        final Supplier<String> defaultBiome;

        DynamicProperties(@NotNull Supplier<String> defaultValue, @NotNull Supplier<String> defaultBiome) {
            this.defaultValue = defaultValue;
            this.defaultBiome = defaultBiome;
        }

        @Override
        public @NotNull String defaultValue() {
            return defaultValue.get();
        }

        @Override
        public @NotNull String defaultBiome() {
            return defaultBiome.get();
        }

    }

}
