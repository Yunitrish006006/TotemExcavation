package dev.totem.excavation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Data-driven excavation targeting plus the canonical hammer item family. */
public final class ExcavationTags {
    public static final TagKey<Block> HAMMER_MINEABLE = blockTag("hammer_mineable");
    public static final TagKey<Block> HAMMER_EFFICIENCY = blockTag("hammer_efficiency");
    public static final TagKey<Item> HAMMERS = itemTag("hammers");

    private ExcavationTags() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath("totem", "excavation/" + path)
        );
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath("totem", "excavation/" + path)
        );
    }
}
