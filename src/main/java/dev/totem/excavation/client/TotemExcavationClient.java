package dev.totem.excavation.client;

import net.fabricmc.api.ClientModInitializer;

/** Registers client-only selection rendering without loading client classes on servers. */
public final class TotemExcavationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HammerSelectionClientInput.register();
        ExcavationOutlineRenderer.register();
    }
}
