package dev.totem.excavation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Data-driven target and efficiency rules transferred from Blossom. */
public final class ExcavationTags {
    public static final TagKey<Block> HAMMER_MINEABLE = blockTag("hammer_mineable");
    public static final TagKey<Block> HAMMER_EFFICIENCY = blockTag("hammer_efficiency");

    private ExcavationTags() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath("totem", "excavation/" + path)
        );
    }
}
