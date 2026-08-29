package dev.totem.excavation.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerSelectionClientInputTest {
    @Test
    void sendsOnlyForThePhysicalPressAndNotForHeldTicks() {
        assertTrue(HammerSelectionClientInput.shouldSendRequest(1));
        assertTrue(HammerSelectionClientInput.shouldSendRequest(2));
        assertTrue(HammerSelectionClientInput.shouldSendRequest(-1));
        assertFalse(HammerSelectionClientInput.shouldSendRequest(0));
    }
}
