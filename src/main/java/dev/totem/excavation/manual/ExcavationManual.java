package dev.totem.excavation.manual;

import dev.totem.core.api.v1.manual.TotemManualSection;
import dev.totem.core.api.v1.manual.TotemModuleManualSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Area-mining hammer guide recorded from a crafting table. */
public final class ExcavationManual {
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.fromNamespaceAndPath("totem", "excavation/manual"),
            500,
            "book.totem.excavation_manual.title",
            List.of(
                    "book.totem.excavation_manual.page.1",
                    "book.totem.excavation_manual.page.2",
                    "book.totem.excavation_manual.page.3"
            )
    );

    private ExcavationManual() {
    }

    public static void register() {
        TotemModuleManualSource.register(
                SECTION,
                Identifier.fromNamespaceAndPath("deadrecall", "excavation_manual"),
                state -> state.is(Blocks.CRAFTING_TABLE)
        );
    }
}
