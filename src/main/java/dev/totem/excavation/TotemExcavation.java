package dev.totem.excavation;

import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.manual.ExcavationManual;
import dev.totem.excavation.network.HammerSelectionNetworking;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.session.ExcavationSessions;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for the standalone area-mining hammer module. */
public final class TotemExcavation implements ModInitializer {
    public static final String MOD_ID = "totem-excavation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ExcavationManual.register();
        ExcavationDataComponents.register();
        ExcavationItems.register();
        HammerSelectionNetworking.register();
        ExcavationSessions.register();
        LOGGER.info("Totem Excavation initialized");
    }
}
