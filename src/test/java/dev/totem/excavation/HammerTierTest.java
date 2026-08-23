package dev.totem.excavation;

import net.minecraft.world.item.ToolMaterial;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HammerTierTest {
    @Test
    void transferredTierTableIsExplicitAndComplete() {
        assertTier(HammerTier.WOODEN, "wooden", ToolMaterial.WOOD, 2, 0.10F);
        assertTier(HammerTier.STONE, "stone", ToolMaterial.STONE, 4, 0.20F);
        assertTier(HammerTier.COPPER, "copper", ToolMaterial.COPPER, 8, 0.40F);
        assertTier(HammerTier.IRON, "iron", ToolMaterial.IRON, 12, 0.80F);
        assertTier(HammerTier.GOLDEN, "golden", ToolMaterial.GOLD, 16, 0.86F);
        assertTier(HammerTier.DIAMOND, "diamond", ToolMaterial.DIAMOND, 20, 0.90F);
        assertTier(HammerTier.NETHERITE, "netherite", ToolMaterial.NETHERITE, 24, 1.00F);
    }

    @Test
    void enchantmentModifiersAreInclusiveAndCompletionIsCapped() {
        assertEquals(12, HammerTier.COPPER.maxRangeForEfficiency(2));
        assertEquals(0.70F, HammerTier.COPPER.completionFractionForEnchantments(2, 1), 0.0001F);
        assertEquals(1.00F, HammerTier.NETHERITE.completionFractionForEnchantments(10, 10), 0.0001F);
        assertEquals(2, HammerTier.WOODEN.maxRangeForEfficiency(-1));
    }

    private static void assertTier(
            HammerTier tier,
            String path,
            ToolMaterial material,
            int range,
            float completion
    ) {
        assertEquals(path, tier.path());
        assertEquals(material, tier.material());
        assertEquals(material.durability(), tier.durability());
        assertEquals(range, tier.baseRange());
        assertEquals(completion, tier.baseCompletion(), 0.0001F);
        assertEquals(6.0F, tier.attackDamage());
        assertEquals(-3.4F, tier.attackSpeed());
        assertEquals(2.5F, tier.attackRange());
        assertEquals(3.0F, tier.creativeAttackRange());
        assertEquals(0.35F, tier.criticalBonusFraction());
    }
}
