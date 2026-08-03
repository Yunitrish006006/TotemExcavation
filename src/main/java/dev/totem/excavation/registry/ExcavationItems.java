package dev.totem.excavation.registry;

import dev.totem.excavation.HammerTier;
import dev.totem.excavation.item.HammerItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Canonical Totem hammer registration; no Blossom content is owned here. */
public final class ExcavationItems {
    private static final Map<HammerTier, HammerItem> CANONICAL = new EnumMap<>(HammerTier.class);
    private static final Map<Item, HammerItem> HAMMERS = new IdentityHashMap<>();
    private static boolean initialized;

    public static final HammerItem WOODEN_HAMMER = canonical(HammerTier.WOODEN);
    public static final HammerItem STONE_HAMMER = canonical(HammerTier.STONE);
    public static final HammerItem COPPER_HAMMER = canonical(HammerTier.COPPER);
    public static final HammerItem IRON_HAMMER = canonical(HammerTier.IRON);
    public static final HammerItem GOLDEN_HAMMER = canonical(HammerTier.GOLDEN);
    public static final HammerItem DIAMOND_HAMMER = canonical(HammerTier.DIAMOND);
    public static final HammerItem NETHERITE_HAMMER = canonical(HammerTier.NETHERITE);

    private ExcavationItems() {
    }

    public static synchronized void register() {
        if (initialized) {
            return;
        }
        registerCreativeTab();
        initialized = true;
    }

    public static HammerItem hammer(ItemStack stack) {
        return stack.isEmpty() ? null : HAMMERS.get(stack.getItem());
    }

    public static boolean isHammer(ItemStack stack) {
        return hammer(stack) != null;
    }

    private static HammerItem canonical(HammerTier tier) {
        return CANONICAL.computeIfAbsent(tier, current -> register(
                Identifier.fromNamespaceAndPath("totem", "excavation/" + current.path() + "_hammer"),
                current
        ));
    }

    private static HammerItem register(Identifier id, HammerTier tier) {
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item existing = BuiltInRegistries.ITEM.getValue(id);
            if (existing instanceof HammerItem hammer && hammer.tier() == tier) {
                HAMMERS.put(hammer, hammer);
                return hammer;
            }
            throw new IllegalStateException("Item ID already belongs to another owner: " + id);
        }
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        HammerItem item = new HammerItem(tier, new Item.Properties().setId(key));
        HammerItem registered = Registry.register(BuiltInRegistries.ITEM, key, item);
        HAMMERS.put(registered, registered);
        return registered;
    }

    private static void registerCreativeTab() {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath("totem-excavation", "main")
        );
        if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(key)) {
            return;
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totem_excavation.main"))
                        .icon(() -> new ItemStack(DIAMOND_HAMMER))
                        .build());
        CreativeModeTabEvents.modifyOutputEvent(key).register(ExcavationItems::addCreativeItems);
    }

    private static void addCreativeItems(FabricCreativeModeTabOutput output) {
        for (HammerTier tier : HammerTier.values()) {
            output.accept(canonical(tier));
        }
    }
}
