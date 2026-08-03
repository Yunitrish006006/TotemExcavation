package dev.totem.excavation.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcavationConfigTest {
    @Test
    void tickBudgetIsBoundedAndFallsBackSafely() {
        assertEquals(ExcavationConfig.DEFAULT_BLOCKS_PER_TICK,
                ExcavationConfig.boundedInt(null, ExcavationConfig.DEFAULT_BLOCKS_PER_TICK));
        assertEquals(ExcavationConfig.MIN_BLOCKS_PER_TICK, ExcavationConfig.boundedInt("0", 9));
        assertEquals(ExcavationConfig.MAX_BLOCKS_PER_TICK, ExcavationConfig.boundedInt("200", 9));
        assertEquals(24, ExcavationConfig.boundedInt("24", 9));
        assertEquals(9, ExcavationConfig.boundedInt("not-a-number", 9));
    }

    @Test
    void harvestBudgetNeverExceedsTheConfiguredPerTickLimit() {
        assertEquals(0, ExcavationSessions.harvestBudget(-1));
        assertEquals(0, ExcavationSessions.harvestBudget(0));
        assertEquals(1, ExcavationSessions.harvestBudget(1));
        assertEquals(ExcavationConfig.DEFAULT_BLOCKS_PER_TICK,
                ExcavationSessions.harvestBudget(ExcavationConfig.DEFAULT_BLOCKS_PER_TICK + 1));
    }
}
