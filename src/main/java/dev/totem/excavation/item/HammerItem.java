package dev.totem.excavation.item;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.HammerTier;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.session.ExcavationSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A transferred Blossom hammer backed by an explicit immutable tier profile. */
public final class HammerItem extends Item {
    private static final float ATTACK_HITBOX_MARGIN = 0.3F;
    private static final float ATTACK_MOB_FACTOR = 1.0F;
    private static final float MACE_ENCHANTMENT_FALL_THRESHOLD = 1.5F;

    private final HammerTier tier;

    public HammerItem(HammerTier tier, Properties properties) {
        super(tier.material().applyToolProperties(
                properties.stacksTo(1).component(
                        DataComponents.ATTACK_RANGE,
                        new AttackRange(
                                0.0F,
                                tier.attackRange(),
                                0.0F,
                                tier.creativeAttackRange(),
                                ATTACK_HITBOX_MARGIN,
                                ATTACK_MOB_FACTOR
                        )
                ),
                ExcavationTags.HAMMER_MINEABLE,
                tier.attackDamage(),
                tier.attackSpeed(),
                0.0F
        ));
        this.tier = tier;
    }

    public HammerTier tier() {
        return tier;
    }

    @Override
    public float getAttackDamageBonus(Entity victim, float baseDamage, DamageSource damageSource) {
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return 0.0F;
        }

        float bonus = 0.0F;
        if (isHammerCritical(attacker)) {
            bonus += Math.max(0.0F, baseDamage) * tier.criticalBonusFraction();
        }

        ItemStack weapon = attacker.getWeaponItem();
        if (attacker.fallDistance >= MACE_ENCHANTMENT_FALL_THRESHOLD
                && ExcavationItems.isHammer(weapon)
                && attacker.level() instanceof ServerLevel serverLevel) {
            bonus += EnchantmentHelper.modifyFallBasedDamage(
                    serverLevel,
                    weapon,
                    victim,
                    damageSource,
                    0.0F
            ) * attacker.fallDistance;
        }
        return bonus;
    }

    private static boolean isHammerCritical(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return false;
        }
        return player.getAttackStrengthScale(0.5F) > 0.9F
                && player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!state.is(ExcavationTags.HAMMER_MINEABLE)) {
            return super.getDestroySpeed(stack, state) * 0.5F;
        }
        float speed = super.getDestroySpeed(stack, state);
        return state.is(ExcavationTags.HAMMER_EFFICIENCY) ? speed * 1.5F : speed * 0.8F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(ExcavationTags.HAMMER_MINEABLE) && super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean mineBlock(
            ItemStack stack,
            Level level,
            BlockState state,
            BlockPos pos,
            LivingEntity miner
    ) {
        boolean result = super.mineBlock(stack, level, state, pos, miner);
        if (!level.isClientSide()
                && level instanceof ServerLevel serverLevel
                && miner instanceof ServerPlayer player
                && !ExcavationSessions.isHarvesting(player)) {
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            ExcavationSessions.startAfterManualBreak(player, serverLevel, held, pos);
        }
        return result;
    }
}
