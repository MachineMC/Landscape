package me.pesekjak.landscape;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public final class ByteChannelUtil {

    private ByteChannelUtil() {
        throw new UnsupportedOperationException();
    }

    private static ByteBuffer fill(ReadableByteChannel channel, int distance) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(distance);
        channel.read(buf);
        return buf.rewind();
    }

    public static ByteBuffer read(ReadableByteChannel channel, int distance) throws IOException {
        return fill(channel, distance);
    }

    public static byte read(ReadableByteChannel channel) throws IOException {
        return fill(channel, 1).get();
    }

    public static short readShort(ReadableByteChannel channel) throws IOException {
        return fill(channel, 2).getShort();
    }

    public static int readInt(ReadableByteChannel channel) throws IOException {
        return fill(channel, 4).getInt();
    }

    public static long readLong(ReadableByteChannel channel) throws IOException {
        return fill(channel, 8).getLong();
    }

    public static float readFloat(ReadableByteChannel channel) throws IOException {
        return fill(channel, 4).getFloat();
    }

    public static double readDouble(ReadableByteChannel channel) throws IOException {
        return fill(channel, 4).getDouble();
    }

    public static String readUTF(ReadableByteChannel channel) throws IOException {
        byte[] chars = new byte[readInt(channel)];
        return new String(chars);
    }

    public static void writeUTF(WritableByteChannel channel, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        ByteBuffer length = ByteBuffer.allocate(4).putInt(data.length).rewind();
        ByteBuffer chars = ByteBuffer.allocate(data.length).put(data).rewind();
        channel.write(length);
        channel.write(chars);
    }

}
