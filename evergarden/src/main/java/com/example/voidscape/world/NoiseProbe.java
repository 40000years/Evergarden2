package com.example.voidscape.world;

import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Reads the vanilla world generator's base terrain (noise only, no carvers or features) for any
 * column without generating or loading a chunk. It is a pure function of the world seed, so the
 * placement planner can look at real cave and island shapes and still be restart- and
 * generation-order-independent. Uses Mojang-mapped server classes through reflection; when it is
 * unavailable the dimensional sanctuaries stay disabled instead of guessing.
 */
final class NoiseProbe {
    static final byte AIR = 0, SOLID = 1, FLUID = 2;
    private final Object generator, randomState, level;
    private final Method baseColumn, getBlock, isAir, fluidState, fluidEmpty;
    private final int minY, maxY;

    private NoiseProbe(Object generator, Object randomState, Object level, Method baseColumn, Method getBlock,
                       Method isAir, Method fluidState, Method fluidEmpty, int minY, int maxY) {
        this.generator = generator; this.randomState = randomState; this.level = level;
        this.baseColumn = baseColumn; this.getBlock = getBlock; this.isAir = isAir;
        this.fluidState = fluidState; this.fluidEmpty = fluidEmpty; this.minY = minY; this.maxY = maxY;
    }

    static NoiseProbe create(World world) throws ReflectiveOperationException {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Object source = handle.getClass().getMethod("getChunkSource").invoke(handle);
        Object generator = source.getClass().getMethod("getGenerator").invoke(source);
        Object randomState = source.getClass().getMethod("randomState").invoke(source);
        Class<?> chunkGenerator = Class.forName("net.minecraft.world.level.chunk.ChunkGenerator");
        Class<?> heightAccessor = Class.forName("net.minecraft.world.level.LevelHeightAccessor");
        Class<?> state = Class.forName("net.minecraft.world.level.levelgen.RandomState");
        Method base = chunkGenerator.getMethod("getBaseColumn", int.class, int.class, heightAccessor, state);
        Class<?> column = Class.forName("net.minecraft.world.level.NoiseColumn");
        Method block = column.getMethod("getBlock", int.class);
        Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState");
        Method air = blockState.getMethod("isAir");
        Method fluid = blockState.getMethod("getFluidState");
        Method empty = Class.forName("net.minecraft.world.level.material.FluidState").getMethod("isEmpty");
        NoiseProbe probe = new NoiseProbe(generator, randomState, handle, base, block, air, fluid, empty,
                world.getMinHeight(), world.getMaxHeight() - 1);
        probe.classify(0, 0); // fail now, not in the middle of world generation
        return probe;
    }

    int minY() { return minY; }
    int maxY() { return maxY; }

    /** Classification of every block in the column, indexed by y - minY. */
    byte[] classify(int x, int z) {
        try {
            Object column = baseColumn.invoke(generator, x, z, level, randomState);
            byte[] result = new byte[maxY - minY + 1];
            for (int y = minY; y <= maxY; y++) {
                Object state = getBlock.invoke(column, y);
                if ((Boolean) isAir.invoke(state)) continue;
                result[y - minY] = (Boolean) fluidEmpty.invoke(fluidState.invoke(state)) ? SOLID : FLUID;
            }
            return result;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot read base terrain at " + x + "," + z, ex);
        }
    }

    /** Highest solid block in the base terrain, or Integer.MIN_VALUE for an empty column. */
    int top(int x, int z) {
        byte[] c = classify(x, z);
        for (int i = c.length - 1; i >= 0; i--) if (c[i] == SOLID) return i + minY;
        return Integer.MIN_VALUE;
    }
}
