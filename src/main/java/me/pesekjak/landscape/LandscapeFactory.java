package me.pesekjak.landscape;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Supplier;

public abstract class LandscapeFactory {

    public static LandscapeFactory create(@NotNull File directory, short height, @NotNull String defaultValue, @NotNull String defaultBiome) {
        return new LandscapeFactoryImpl(directory, height, new Landscape.SimpleProperties(defaultValue, defaultBiome));
    }

    public static LandscapeFactory create(@NotNull File directory, short height, @NotNull Supplier<String> defaultValue, @NotNull Supplier<String> defaultBiome) {
        return new LandscapeFactoryImpl(directory, height, new Landscape.DynamicProperties(defaultValue, defaultBiome));
    }

    public static LandscapeFactory create(@NotNull File directory, short height, @NotNull Landscape.Properties properties) {
        return new LandscapeFactoryImpl(directory, height, properties);
    }

    public abstract @NotNull Landscape of(int x, int y);

    static class LandscapeFactoryImpl extends LandscapeFactory {

        private final File directory;
        private final short height;
        private final Landscape.Properties provider;

        LandscapeFactoryImpl(File directory, short height, Landscape.Properties provider) {
            this.directory = directory;
            this.height = height;
            this.provider = provider;
        }

        @Override
        public @NotNull Landscape of(int x, int y) {
            return Landscape.of(directory, x, y, height, provider);
        }

    }

}
