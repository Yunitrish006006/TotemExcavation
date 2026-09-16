package dev.totem.excavation.mixin;

import dev.totem.excavation.harvest.HoeHarvesting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
abstract class HoeItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void totemExcavation$harvestMatureCrop(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (!context.getItemInHand().is(ItemTags.HOES)) return;
        InteractionResult result = HoeHarvesting.tryHarvest(context);
        if (result != InteractionResult.PASS) callback.setReturnValue(result);
    }
}
