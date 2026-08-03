package dev.totem.excavation;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Immutable gameplay profile for one transferred Blossom hammer tier. */
public enum HammerTier {
    WOODEN("wooden", ToolMaterial.WOOD, 2, 0.10F),
    STONE("stone", ToolMaterial.STONE, 4, 0.20F),
    COPPER("copper", ToolMaterial.COPPER, 8, 0.40F),
    IRON("iron", ToolMaterial.IRON, 12, 0.80F),
    GOLDEN("golden", ToolMaterial.GOLD, 16, 0.86F),
    DIAMOND("diamond", ToolMaterial.DIAMOND, 20, 0.90F),
    NETHERITE("netherite", ToolMaterial.NETHERITE, 24, 1.00F);

    private static final float ATTACK_DAMAGE = 4.0F;
    private static final float ATTACK_SPEED = -2.8F;
    private static final int EFFICIENCY_RANGE_PER_LEVEL = 2;
    private static final float EFFICIENCY_COMPLETION_PER_LEVEL = 0.05F;
    private static final float SILK_TOUCH_COMPLETION_PER_LEVEL = 0.20F;

    private final String path;
    private final ToolMaterial material;
    private final int baseRange;
    private final float baseCompletion;

    HammerTier(String path, ToolMaterial material, int baseRange, float baseCompletion) {
        this.path = path;
        this.material = material;
        this.baseRange = baseRange;
        this.baseCompletion = baseCompletion;
    }

    public String path() {
        return path;
    }

    public ToolMaterial material() {
        return material;
    }

    public int durability() {
        return material.durability();
    }

    public int baseRange() {
        return baseRange;
    }

    public float baseCompletion() {
        return baseCompletion;
    }

    public float attackDamage() {
        return ATTACK_DAMAGE;
    }

    public float attackSpeed() {
        return ATTACK_SPEED;
    }

    public int maxRange(ServerLevel level, ItemStack stack) {
        return maxRangeForEfficiency(enchantmentLevel(level, stack, Enchantments.EFFICIENCY));
    }

    public float completionFraction(ServerLevel level, ItemStack stack) {
        return completionFractionForEnchantments(
                enchantmentLevel(level, stack, Enchantments.EFFICIENCY),
                enchantmentLevel(level, stack, Enchantments.SILK_TOUCH)
        );
    }

    public int maxRangeForEfficiency(int efficiencyLevel) {
        return baseRange + Math.max(0, efficiencyLevel) * EFFICIENCY_RANGE_PER_LEVEL;
    }

    public float completionFractionForEnchantments(int efficiencyLevel, int silkTouchLevel) {
        float completion = baseCompletion
                + Math.max(0, efficiencyLevel) * EFFICIENCY_COMPLETION_PER_LEVEL
                + Math.max(0, silkTouchLevel) * SILK_TOUCH_COMPLETION_PER_LEVEL;
        return Math.min(1.0F, completion);
    }

    private static int enchantmentLevel(
            ServerLevel level,
            ItemStack stack,
            ResourceKey<Enchantment> enchantmentKey
    ) {
        Holder<Enchantment> enchantment = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantmentKey);
        return stack.getEnchantments().getLevel(enchantment);
    }
}
