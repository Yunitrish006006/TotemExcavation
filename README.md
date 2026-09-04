# Totem Excavation

Totem Excavation owns seven area-mining hammers: wood, stone,
copper, iron, gold, diamond and netherite. It requires Fabric API and
TotemCore on Minecraft 26.2 with Java 25.

All hammers use `totem:excavation/<tier>_hammer`. The module does not register
or migrate any `blossom:*` identifiers.

## Controls

Selection is held on the exact hammer stack in the main hand.

- Sneak + left-click an eligible block to set Corner A.
- Sneak + left-click another eligible block to set Corner B.
- Click the same incomplete corner, or either corner of a complete selection,
  to clear the whole selection.
- With a complete selection, click any other eligible block to restart Corner A
  and clear Corner B.
- Left-click normally inside a complete selection to start bounded server-side
  excavation.
- Right-click a mature crop with any hoe to harvest and replant it. This uses the
  normal loot table and Fortune enchantment, reserves one planting item, and
  costs one point of hoe durability. Wheat, carrots, potatoes, beetroot, nether
  wart and cocoa are supported.
- Right-click is never consumed by the hammer, so vanilla offhand use remains
  available.

Selection attacks are cancelled client-side before crack progress, then validated
against server-owned state before changing the selected hammer. Client rendering
only displays the local player's selection outline.
