package dev.totem.excavation.component;

import dev.totem.excavation.TotemExcavation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registry holder for all ItemStack-owned Excavation state. */
public final class ExcavationDataComponents {
    public static final DataComponentType<AreaSelection> AREA_SELECTION = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath("totem", "excavation/area_selection"),
            DataComponentType.<AreaSelection>builder()
                    .persistent(AreaSelection.CODEC)
                    .networkSynchronized(AreaSelection.STREAM_CODEC)
                    .cacheEncoding()
                    .build()
    );

    private ExcavationDataComponents() {
    }

    public static void register() {
        TotemExcavation.LOGGER.debug("Registered Totem Excavation data components");
    }
}
