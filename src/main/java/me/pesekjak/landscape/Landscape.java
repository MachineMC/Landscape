package me.pesekjak.landscape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Represents a region file container storing 16x16 area of Minecraft chunks in
 * so-called Segments.
 * <p>
 * Each Landscape is synchronized and multiple instances for
 * the same file can't exist in the system.
 * @see me.pesekjak.landscape.Segment
 */
public class Landscape {

    public static final short VERSION = 1;

    private final static Set<Landscape> cache = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    final File file;
    SeekableByteChannel channel;

    final short version = VERSION;
    final int x;
    final int y;
    final short height;
    final LandscapeHandler handler;

    final int VERSION_POINTER = 0; // starts at byte 0
    final int X_POINTER = 2;  // starts at byte 2
    final int Y_POINTER = 6;  // starts at byte 2
    final int HEIGHT_POINTER = 10; // starts at byte 10;

    Segment[] segments; // pushed segments
    Map<Segment, Integer> weakSegments = new WeakHashMap<>(); // weakly referenced segments (not pushed) matched with their indices
    private int pushed; // counter for pushed segments

    final int HEADER_SIZE = 12; // size of header in bytes
    final int TABLE_SIZE; // size of look up table in bytes
    final int TABLE_ENTRY_SIZE = 8; // size of single entry in the look up table (int position, int length)

    private final Object lock = new Object();

    public static Landscape of(File directory, int x, int y, LandscapeHandler handler) {
        return of(directory, x, y, (short) -1, handler);
    }

    public static Landscape of(File directory, int x, int y, short height, LandscapeHandler handler) {
        try {
            return of0(directory, x , y, height, handler);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Landscape of0(File directory, int x, int y, short height, LandscapeHandler handler) throws IOException {
        final File file = new File(directory, "r_" + x + "_" + y + ".ls");
        for(Landscape landscape : cache) {
            if(landscape.file.equals(file)) return landscape;
        }
        return new Landscape(file, x, y, height, handler);
    }

    private Landscape(@NotNull File source, int x, int y, short height, LandscapeHandler handler) throws IOException {

        file = source;
        openChannel();

        this.x = x;
        this.y = y;
        this.handler = handler;

        if(height == -1) { // height should be read from the file
            if(channel.size() < HEADER_SIZE)throw new IllegalStateException("Couldn't load " + file.getName() + " because it has invalid header");
            channel.position(HEIGHT_POINTER);
            this.height = ByteChannelUtil.readShort(channel);
        } else {
            if(height < 16 || height % 16 != 0)
                throw new IllegalStateException("Couldn't load " + file.getName() + " because of invalid height - " + height);
            this.height = height;
        }

        segments = new Segment[height / 16 * 16 * 16]; // amount of 16x16x16 segments in the whole file
        TABLE_SIZE = segments.length * TABLE_ENTRY_SIZE; // each segment in look-up table contains 8 bytes - (int position, int length)

        if(channel.size() == 0) {
            writeDefaults();
        } else {
            checkValidity();
        }
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

    public LandscapeHandler getHandler() {
        return handler;
    }

    public Segment loadSegment(int x, int y, int z) {
        try {
            return loadSegment(segmentIndex(x, y, z));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Pushes all loaded segments to the file.
     */
    public void flush() {
        try {
            flush0();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void flush0() throws IOException {
        final Segment[] toFlush = new Segment[height / 16 * 16 * 16];
        synchronized (lock) {
            if (!channel.isOpen())
                openChannel();

            if (weakSegments.size() == 0) {
                boolean empty = true;
                for (Segment segment : segments) {
                    if(segment == null) continue;
                    empty = false;
                    break;
                }
                if (empty) return; // there are no sections to push
            }

            System.arraycopy(segments, 0, toFlush, 0, segments.length);

            for (Map.Entry<Segment, Integer> weakEntry : weakSegments.entrySet())
                toFlush[weakEntry.getValue()] = weakEntry.getKey();

            int startPos = 1; // index of first pushed segment
            for (int i = 0; i < toFlush.length; i++) {
                if(toFlush[i] == null) continue;
                startPos = i;
                break;
            }

            // Temporary file to store data
            SeekableByteChannel temp = Files.newByteChannel(
                    Files.createTempFile("temp_r_" + x + "_" + y, ".ls"),
                    StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SPARSE, StandardOpenOption.DELETE_ON_CLOSE
            );

            // index of byte from where the segment data is updated,
            // -1 if there isn't one
            int touched = -1;

            for (int i = startPos; i < toFlush.length; i++) {

                final ByteBuffer original;
                final Segment segment = toFlush[i];

                if (segment == null) {
                    if(touched == -1) continue;
                    original = readSegmentData(i);
                    channel.position(HEADER_SIZE + (long) i * TABLE_ENTRY_SIZE);
                    channel.write(ByteBuffer.allocate(TABLE_ENTRY_SIZE)
                            .putInt((int) (touched + temp.position()))
                            .putInt(original.capacity()));
                    temp.write(original);
                    continue;
                }

                final ByteBuffer pushed = segment.serialize();

                if(touched == -1) {

                    original = readSegmentData(i);
                    channel.position(HEADER_SIZE + (long) i * TABLE_ENTRY_SIZE);
                    final int position = ByteChannelUtil.readInt(channel);

                    if(original.capacity() == pushed.capacity()) {
                        channel.position(position);
                        channel.write(pushed);
                        continue;
                    }

                    touched = position;
                }

                channel.position(HEADER_SIZE + (long) i * TABLE_ENTRY_SIZE);
                channel.write(ByteBuffer.allocate(TABLE_ENTRY_SIZE)
                        .putInt((int) (touched + temp.position()))
                        .putInt(pushed.capacity())
                        .rewind()
                );
                temp.write(pushed);
            }

            if(touched != -1) {
                channel.position(touched);
                temp.position(0);
                channel.write(ByteChannelUtil.read(temp, (int) temp.size()));
                channel.truncate(channel.position());
            }
            segments = new Segment[height / 16 * 16 * 16];
            pushed = 0;
        }
    }

    /**
     * Closes the channel of this Landscape file.
     */
    public void close() throws IOException {
        synchronized (lock) {
            if(!channel.isOpen()) return;
            channel.close();
        }
    }

    /**
     * Loads segment from the file.
     * @param index index of the segment
     * @return loaded segment
     */
    protected Segment loadSegment(int index) throws IOException {
        synchronized (lock) {
            Segment cached = getSegment(index);
            if(cached != null) return cached;
            Segment segment = readSegment(index);
            weakSegments.put(segment, index);
            return segment;
        }
    }

    /**
     * Returns cached segment of this Landscape if there is one.
     * @param index index of the segment
     * @return cached Segment
     */
    private @Nullable Segment getSegment(int index) {
        if(segments[index] != null)
            return segments[index];
        if(!weakSegments.containsValue(index)) return null;
        for (Segment weak : weakSegments.keySet()) {
            if(weakSegments.get(weak) == index)
                return weak;
        }
        return null;
    }

    /**
     * Reads segment of this Landscape file.
     * @param index index of the segment
     * @return segment
     */
    private Segment readSegment(int index) throws IOException {
        return new Segment(this, index, readSegmentData(index));
    }

    /**
     * Reads data of segment of this Landscape file.
     * @param index index of the segment
     * @return segment data
     */
    private ByteBuffer readSegmentData(int index) throws IOException {
        if(!channel.isOpen())
            openChannel();
        channel.position(HEADER_SIZE + (long) index * TABLE_ENTRY_SIZE);
        ByteBuffer buf = ByteChannelUtil.read(channel, 8);
        int position = buf.getInt();
        int length = buf.getInt();
        channel.position(position);
        return ByteChannelUtil.read(channel, length).rewind();
    }

    /**
     * Pushes segment reference to the file.
     * @param segment segment to push
     * @param index index of the segment
     * @see me.pesekjak.landscape.Segment#push()
     */
    protected void push(Segment segment, int index) {
        synchronized (lock) {
            segments[index] = segment;
            pushed++;
            if(!handler.isAutoSave()) return;
            if(pushed >= handler.getAutoSaveLimit())
                flush();
        }
    }

    /**
     * Writes header of the Landscape file
     */
    private void writeHeader() throws IOException {
        if(!channel.isOpen())
            openChannel();
        channel.position(0);
        channel.write(
                ByteBuffer.allocate(12)
                        .putShort(VERSION)
                        .putInt(x)
                        .putInt(y)
                        .putShort(height)
                        .rewind()
        );
    }

    /**
     * Writes empty segments to the file.
     */
    private void writeDefaults() throws IOException {
        writeHeader();

        ByteBuffer empty = new Segment(this, 0).serialize();
        final int size = empty.capacity();

        channel.position(HEADER_SIZE + TABLE_SIZE);
        for (int i = 0; i < segments.length; i++) {
            channel.write(empty);
            empty.rewind();
        }

        channel.position(HEADER_SIZE);
        for (int i = 0; i < segments.length; i++) {
            channel.write(ByteBuffer.allocate(TABLE_ENTRY_SIZE)
                    .putInt(HEADER_SIZE + TABLE_SIZE + empty.capacity() * i)
                    .putInt(empty.capacity())
                    .rewind()
            );
        }
    }

    /**
     * Checks if the Landscape file is valid and if not it repairs it.
     */
    private void checkValidity() throws IOException {
        if(!channel.isOpen())
            openChannel();
        channel.position(HEIGHT_POINTER);
        short fileHeight = ByteChannelUtil.readShort(channel);

        if(height == fileHeight) return;

        // Height in file doesn't match the provided height when loading
        throw new IllegalStateException(); // TODO Fixing height changes
    }

    /**
     * Opens the channel of the file in case it has been closed before.
     */
    private void openChannel() throws IOException {
        if(channel != null && channel.isOpen()) return;
        final OpenOption[] options = file.exists() ?
                new OpenOption[] {StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SPARSE} :
                new OpenOption[] {StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.SPARSE, StandardOpenOption.CREATE_NEW};
        channel = Files.newByteChannel(file.toPath(), options);
    }

    /**
     * @return unique index for the segment from its coordinates
     */
    private int segmentIndex(int x, int y, int z) {
        int index = y << 8; // y has to be the furthest due to variable height
        index |= z << 4;
        index |= x;
        return index;
    }

}
