package org.machinemc.landscape;

/**
 * Handles additional actions for Landscape files.
 */
public interface LandscapeHandler {

    /**
     * @return default block type used to fill empty segments
     */
    String getDefaultType();

    /**
     * @return default biome used to fill empty segments
     */
    String getDefaultBiome();

    /**
     * @return whether the Landscape file should automatically save after certain amount
     * of segments are pushed
     */
    boolean isAutoSave();

    /**
     * @return limit of pushed segments at which the Landscape file should auto save
     */
    int getAutoSaveLimit();

}
