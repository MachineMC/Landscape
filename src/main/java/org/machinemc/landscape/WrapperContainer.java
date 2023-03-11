package org.machinemc.landscape;

import org.machinemc.landscape.containers.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Container that wraps around other containers and switches
 * between different implementations to ensure its size is
 * as smallest as possible but still provides all features.
 */
public class WrapperContainer implements ValueContainer {

    private ValueContainer wrapped;
    private final int dimension;
    private final Supplier<String> defaultValue;

    private final Object lock = new Object();

    public static WrapperContainer read(ByteBuffer buffer, int dimension, Supplier<String> defaultValue) throws IOException {

        short size = buffer.getShort();

        ValueContainer wrapped;

        if (size == 0)
            return new WrapperContainer(EmptyContainer.read(size), dimension, defaultValue);
        else if (size == 1)
            return new WrapperContainer(SingleContainer.read(buffer, size, dimension), dimension, defaultValue);
        else if (size <= 256)
            return new WrapperContainer(SmallContainer.read(buffer, size, dimension), dimension, defaultValue);
        else
            return new WrapperContainer(BigContainer.read(buffer, size, dimension), dimension, defaultValue);
    }

    public WrapperContainer(int dimension, Supplier<String> defaultValue) {
        this.wrapped = new EmptyContainer();
        this.dimension = dimension;
        this.defaultValue = defaultValue;
    }

    private WrapperContainer(ValueContainer wrapped, int dimension, Supplier<String> defaultValue) {
        this.wrapped = wrapped;
        this.dimension = dimension;
        this.defaultValue = defaultValue;
    }

    @Override
    public String get(int x, int y, int z) {
        synchronized (lock) {
            if(wrapped instanceof EmptyContainer)
                wrapped = new SingleContainer(defaultValue.get(), dimension);
            return wrapped.get(x, y, z);
        }
    }

    @Override
    public void getAll(EntryConsumer consumer) {
        synchronized (lock) {
            if(wrapped instanceof EmptyContainer)
                wrapped = new SingleContainer(defaultValue.get(), dimension);
            wrapped.getAll(consumer);
        }
    }

    @Override
    public void set(int x, int y, int z, String value) {
        synchronized (lock) {
            if (wrapped instanceof EmptyContainer || wrapped instanceof SingleContainer)
                convert(new SmallContainer(defaultValue.get(), dimension));
            else if (getCount() >= 256)
                convert(new BigContainer(defaultValue.get(), dimension));
            wrapped.set(x, y, z, value);
        }
    }

    @Override
    public void fill(String value) {
        synchronized (lock) {
            wrapped = new SingleContainer(value, dimension);
        }
    }

    @Override
    public void setAll(EntrySupplier supplier) {
        synchronized (lock) {
            convert(new BigContainer(defaultValue.get(), dimension));
            wrapped.setAll(supplier);
        }
        reduce();
    }

    @Override
    public void replace(int x, int y, int z, String value) {
        synchronized (lock) {
            if (wrapped instanceof EmptyContainer || wrapped instanceof SingleContainer)
                convert(new SmallContainer(defaultValue.get(), dimension));
            else if (getCount() >= 256)
                convert(new BigContainer(defaultValue.get(), dimension));
            wrapped.replace(x, y, z, value);
        }
    }

    @Override
    public void replaceAll(EntryFunction function) {
        synchronized (lock) {
            convert(new BigContainer(defaultValue.get(), dimension));
            wrapped.replaceAll(function);
        }
        reduce();
    }

    @Override
    public int getCount() {
        synchronized (lock) {
            return wrapped.getCount();
        }
    }

    @Override
    public String[] getPalette() {
        synchronized (lock) {
            return wrapped.getPalette();
        }
    }

    @Override
    public int getBitsPerEntry() {
        synchronized (lock) {
            if (wrapped instanceof EmptyContainer)
                wrapped = new SingleContainer(defaultValue.get(), dimension);
            return wrapped.getBitsPerEntry();
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public ByteBuffer serialize() {
        synchronized (lock) {
            reduce();
            return wrapped.serialize();
        }
    }

    private void reduce() {
        if (wrapped instanceof ReducingContainer reducing)
            reducing.reducePalette();
        if (wrapped.getCount() <= 256 && wrapped instanceof BigContainer)
            convert(new SmallContainer(defaultValue.get(), dimension));
        else if (wrapped.getCount() == 1 && !(wrapped instanceof SingleContainer))
            wrapped = new SingleContainer(wrapped.getPalette()[0], dimension);
    }

    private void convert(ValueContainer target) {
        if(wrapped instanceof EmptyContainer)
            wrapped = new SingleContainer(defaultValue.get(), dimension);
        target.setAll(((x, y, z) -> wrapped.get(x, y, z)));
        wrapped = target;
    }

}
