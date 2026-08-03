package dev.totem.excavation.session;

/** Server-side excavation limits. The JVM property is read once during startup. */
public final class ExcavationConfig {
    public static final String BLOCKS_PER_TICK_PROPERTY = "totem.excavation.blocks-per-tick";
    public static final int MIN_BLOCKS_PER_TICK = 1;
    public static final int MAX_BLOCKS_PER_TICK = 128;
    public static final int DEFAULT_BLOCKS_PER_TICK = 32;

    private static final int BLOCKS_PER_TICK = boundedInt(
            System.getProperty(BLOCKS_PER_TICK_PROPERTY),
            DEFAULT_BLOCKS_PER_TICK
    );

    private ExcavationConfig() {
    }

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK;
    }

    static int boundedInt(String rawValue, int fallback) {
        if (rawValue == null) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(rawValue), MIN_BLOCKS_PER_TICK, MAX_BLOCKS_PER_TICK);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
