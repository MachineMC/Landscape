package org.machinemc.landscape.containers;

import org.machinemc.landscape.ValueContainer;

/**
 * Container that automatically reduces its palette before serializing.
 */
public interface ReducingContainer extends ValueContainer {

    /**
     * Reduces the palette if possible.
     * @return whether the palette has been reduced
     */
    boolean reducePalette();

}
