package dev.totem.excavation.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Persistent per-stack area selection; active excavation sessions are not persisted. */
public record AreaSelection(
        int dataVersion,
        ResourceKey<Level> dimension,
        BlockPos firstCorner,
        Optional<BlockPos> secondCorner
) {
    public static final int CURRENT_DATA_VERSION = 1;
    public static final Codec<AreaSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("data_version").forGetter(AreaSelection::dataVersion),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(AreaSelection::dimension),
            BlockPos.CODEC.fieldOf("first_corner").forGetter(AreaSelection::firstCorner),
            BlockPos.CODEC.optionalFieldOf("second_corner").forGetter(AreaSelection::secondCorner)
    ).apply(instance, AreaSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaSelection> STREAM_CODEC = StreamCodec.of(
            (buffer, selection) -> {
                buffer.writeVarInt(selection.dataVersion);
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buffer, selection.dimension);
                BlockPos.STREAM_CODEC.encode(buffer, selection.firstCorner);
                buffer.writeBoolean(selection.secondCorner.isPresent());
                selection.secondCorner.ifPresent(corner -> BlockPos.STREAM_CODEC.encode(buffer, corner));
            },
            buffer -> {
                int dataVersion = buffer.readVarInt();
                ResourceKey<Level> dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buffer);
                BlockPos first = BlockPos.STREAM_CODEC.decode(buffer);
                Optional<BlockPos> second = buffer.readBoolean()
                        ? Optional.of(BlockPos.STREAM_CODEC.decode(buffer))
                        : Optional.empty();
                return new AreaSelection(dataVersion, dimension, first, second);
            }
    );

    public static AreaSelection firstCorner(ResourceKey<Level> dimension, BlockPos corner) {
        return new AreaSelection(CURRENT_DATA_VERSION, dimension, corner.immutable(), Optional.empty());
    }

    public AreaSelection withSecondCorner(BlockPos corner) {
        return new AreaSelection(dataVersion, dimension, firstCorner, Optional.of(corner.immutable()));
    }

    public AreaSelection withoutSecondCorner() {
        return new AreaSelection(dataVersion, dimension, firstCorner, Optional.empty());
    }

    public boolean isComplete() {
        return secondCorner.isPresent();
    }
}
