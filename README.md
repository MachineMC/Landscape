Landscape
=====

## Introduction

Landscape is an alternative format for Minecraft worlds that focuses
primarily on speed and simplicity originally designed for [Machine](https://github.com/MachineMC/Machine) server.

### Goals and features
* Designed for large generated worlds with variable height
* Fast loading and saving times
* NBT, tile entities, ticking blocks and biomes support
* Doesn't load whole chunks but only their segments (16x16x16 sections)
* Multithreading support
* Easy to use
* Automatic memory management

## Maven information

Landscape is available from `machine-releases`

#### Maven
```xml
<repository>
    <id>machine-releases</id>
    <name>MachineMC Repository</name>
    <url>http://www.machinemc.org/releases</url>
</repository>
```
```xml
<dependency>
    <groupId>org.machinemc</groupId>
    <artifactId>nbt</artifactId>
    <version>{version}</version>
</dependency>
```
#### Gradle
```kotlin
maven {
    url = uri("http://www.machinemc.org/releases")
    isAllowInsecureProtocol = true
}
```
```kotlin
implementation("org.machinemc:landscape:{version}")
```

## File format

Name format: `r_x_y.ls`, x and y being the Landscape coordinates

File content:

|Header (12 bytes)                        |Lookup Table (8 bytes per segment)    |Segments data           |
|-----------------------------------------|--------------------------------------|------------------------|
|short version, int x, int y, short height|(int position, int length) per segment|data per segment        |

## Usage

### Loading a segment
```java
// Creates new or loads already existing Landscape file
// Height, in this case 256, doesn't have to be specified for already existing files
Landscape landscape = Landscape.of(dir, 0, 0, (short) 256, handler);
// Loads a segment at coordinates (0, 0, 0)
Segment segment = landscape.loadSegment(0 , 0, 0);
```

### Reading a segment
```java
// Reading information about block at coordinates (0, 0, 0) in the segment
String block = segment.getBlock(0, 0, 0); // getting saved block type
NBTCompound nbt = segment.getNBT(0, 0, 0); // getting NBT of the block
boolean isTicking segment.isTicking(0, 0, 0); // whether the block is saved as ticking
```

### Writing to a segment
```java
// Writing information about a block at coordinates (0, 0, 0) in the segment
segment.setBlock(0, 0, 0, "minecraft:cobblestone", compound, false);
// After writing the information to the segment we need to push it to save the changes
// even after reference to the segment is lost
segment.push();

// ...

// Saves all pushed and referenced segments to the file and clears segments that
// are no longer referenced in the code
landscape.flush();
```