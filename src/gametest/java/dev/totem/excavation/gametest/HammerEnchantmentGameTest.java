package dev.totem.excavation.gametest;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.HammerTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;

/** Verifies that every canonical hammer participates in the intended vanilla 26.2 enchantment and combat tags. */
public final class HammerEnchantmentGameTest {
    private static final TagKey<Item> ENCHANTABLE_MINING = vanillaItemTag("enchantable/mining");
    private static final TagKey<Item> ENCHANTABLE_MINING_LOOT = vanillaItemTag("enchantable/mining_loot");
    private static final TagKey<Item> ENCHANTABLE_DURABILITY = vanillaItemTag("enchantable/durability");
    private static final TagKey<Item> ENCHANTABLE_MACE = vanillaItemTag("enchantable/mace");
    private static final TagKey<Item> ENCHANTABLE_WEAPON = vanillaItemTag("enchantable/weapon");
    private static final TagKey<Item> ENCHANTABLE_FIRE_ASPECT = vanillaItemTag("enchantable/fire_aspect");
    private static final TagKey<Item> ENCHANTABLE_SHARP_WEAPON = vanillaItemTag("enchantable/sharp_weapon");
    private static final TagKey<Item> ENCHANTABLE_SWEEPING = vanillaItemTag("enchantable/sweeping");

    @GameTest(maxTicks = 40)
    public void everyHammerSupportsIntendedVanillaEnchantments(GameTestHelper helper) {
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
            require(helper, stack.is(ENCHANTABLE_MACE),
                    id + " cannot receive Density, Breach or Wind Burst");
            require(helper, stack.is(ENCHANTABLE_WEAPON),
                    id + " cannot receive Smite or Bane of Arthropods");
            require(helper, stack.is(ENCHANTABLE_FIRE_ASPECT),
                    id + " cannot receive Fire Aspect");
            require(helper, !stack.is(ENCHANTABLE_SHARP_WEAPON),
                    id + " unexpectedly accepts Sharpness");
            require(helper, !stack.is(ENCHANTABLE_SWEEPING),
                    id + " unexpectedly accepts Sweeping Edge");

            AttackRange attackRange = stack.get(DataComponents.ATTACK_RANGE);
            require(helper, attackRange != null, id + " is missing its attack range component");
            require(helper, Math.abs(attackRange.maxReach() - tier.attackRange()) < 0.001F,
                    id + " has the wrong survival attack reach");
            require(helper, Math.abs(attackRange.maxCreativeReach() - tier.creativeAttackRange()) < 0.001F,
                    id + " has the wrong creative attack reach");
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
