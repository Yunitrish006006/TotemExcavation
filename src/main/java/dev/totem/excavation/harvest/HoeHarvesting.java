package dev.totem.excavation.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/** Server-authoritative right-click harvesting for hoes. */
public final class HoeHarvesting {
    private static final int BREAK_EVENT = 2001;

    private HoeHarvesting() {
    }

    public static InteractionResult tryHarvest(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        BlockState matureState = context.getLevel().getBlockState(pos);
        HarvestPlan plan = planFor(matureState, context);
        if (plan == null) return InteractionResult.PASS;

        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;

        ItemStack hoe = context.getItemInHand();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(
                matureState,
                level,
                pos,
                blockEntity,
                player,
                hoe
        );
        if (!reserveReplantingItem(drops, plan.replantingItem())) {
            return InteractionResult.PASS;
        }

        if (!level.setBlock(pos, plan.replantedState(), Block.UPDATE_ALL)) {
            return InteractionResult.PASS;
        }

        drops.forEach(drop -> Block.popResource(level, pos, drop));
        matureState.spawnAfterBreak(level, pos, hoe, true);
        level.levelEvent(player, BREAK_EVENT, pos, Block.getId(matureState));
        level.gameEvent(
                GameEvent.BLOCK_CHANGE,
                pos,
                GameEvent.Context.of(player, plan.replantedState())
        );
        hoe.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
        return InteractionResult.SUCCESS;
    }

    private static HarvestPlan planFor(BlockState state, UseOnContext context) {
        if (state.getBlock() instanceof CropBlock crop
                && !(state.getBlock() instanceof TorchflowerCropBlock)
                && crop.isMaxAge(state)) {
            return new HarvestPlan(
                    crop.getStateForAge(0),
                    state.getCloneItemStack(context.getLevel(), context.getClickedPos(), false)
            );
        }

        if (state.getBlock() instanceof NetherWartBlock
                && state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
            return new HarvestPlan(
                    state.setValue(NetherWartBlock.AGE, 0),
                    new ItemStack(Items.NETHER_WART)
            );
        }

        if (state.getBlock() instanceof CocoaBlock
                && state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE) {
            return new HarvestPlan(
                    state.setValue(CocoaBlock.AGE, 0),
                    new ItemStack(Items.COCOA_BEANS)
            );
        }

        return null;
    }

    private static boolean reserveReplantingItem(List<ItemStack> drops, ItemStack replantingItem) {
        if (replantingItem.isEmpty()) return false;

        for (ItemStack drop : drops) {
            if (ItemStack.isSameItemSameComponents(drop, replantingItem)) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    private record HarvestPlan(BlockState replantedState, ItemStack replantingItem) {
    }
}
