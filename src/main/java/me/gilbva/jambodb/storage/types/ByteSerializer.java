package me.gilbva.jambodb.storage.types;

import me.gilbva.jambodb.storage.btrees.Serializer;

import java.nio.ByteBuffer;

/**
 * Serializer for java.lang.Byte.
 */
public class ByteSerializer implements Serializer<Byte> {
    public static final ByteSerializer INSTANCE = new ByteSerializer();

    @Override
    public int size(ByteBuffer buffer) {
        return 1;
    }

    @Override
    public int size(Byte value) {
        return 1;
    }

    @Override
    public Byte read(ByteBuffer buffer) {
        return buffer.get();
    }

    @Override
    public void write(ByteBuffer buffer, Byte value) {
        buffer.put(value);
    }
}
