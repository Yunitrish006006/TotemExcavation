package dev.totem.excavation.gametest;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.HammerTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Verifies that every canonical hammer participates in the vanilla 26.2 enchantment item tags. */
public final class HammerEnchantmentGameTest {
    private static final TagKey<Item> ENCHANTABLE_MINING = vanillaItemTag("enchantable/mining");
    private static final TagKey<Item> ENCHANTABLE_MINING_LOOT = vanillaItemTag("enchantable/mining_loot");
    private static final TagKey<Item> ENCHANTABLE_DURABILITY = vanillaItemTag("enchantable/durability");

    @GameTest(maxTicks = 40)
    public void everyHammerSupportsVanillaMiningEnchantments(GameTestHelper helper) {
        for (HammerTier tier : HammerTier.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    "totem",
                    "excavation/" + tier.path() + "_hammer"
            );
            Item item = BuiltInRegistries.ITEM.getValue(id);
            require(helper, item != null, "Missing canonical hammer: " + id);

            ItemStack stack = new ItemStack(item);
            require(helper, stack.is(ExcavationTags.HAMMERS),
                    id + " is absent from #totem:excavation/hammers");
            require(helper, stack.is(ENCHANTABLE_MINING),
                    id + " cannot receive Efficiency");
            require(helper, stack.is(ENCHANTABLE_MINING_LOOT),
                    id + " cannot receive Fortune or Silk Touch");
            require(helper, stack.is(ENCHANTABLE_DURABILITY),
                    id + " cannot receive Unbreaking or Mending");
        }
        helper.succeed();
    }

    private static TagKey<Item> vanillaItemTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath("minecraft", path)
        );
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
