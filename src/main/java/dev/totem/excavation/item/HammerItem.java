package dev.totem.excavation.item;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.HammerTier;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.selection.HammerSelectionService;
import dev.totem.excavation.session.ExcavationSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A transferred Blossom hammer backed by an explicit immutable tier profile. */
public final class HammerItem extends Item {
    private final HammerTier tier;

    public HammerItem(HammerTier tier, Properties properties) {
        super(tier.material().applyToolProperties(
                properties.stacksTo(1),
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
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)
                || !level.getBlockState(context.getClickedPos()).is(ExcavationTags.HAMMER_MINEABLE)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(context.getHand());
        HammerItem hammer = ExcavationItems.hammer(stack);
        if (hammer == null) {
            return InteractionResult.FAIL;
        }
        return HammerSelectionService.select(
                player,
                context.getHand(),
                stack,
                hammer,
                serverLevel,
                context.getClickedPos(),
                context.isSecondaryUseActive()
        );
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
