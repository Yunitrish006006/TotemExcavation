# Totem Excavation

Totem Excavation owns seven area-mining hammers: wood, stone,
copper, iron, gold, diamond and netherite. It requires Fabric API and
TotemCore 0.6.0 on Minecraft 26.2 with Java 25.

All hammers use `totem:excavation/<tier>_hammer`. The module does not register
or migrate any `blossom:*` identifiers.

Selection is held on each hammer stack. Crouch-use an eligible block to select
the first corner, then use another eligible block to select the second corner.
A manual hammer break starts bounded server-side excavation; client rendering
only displays the local player's selection outline.
