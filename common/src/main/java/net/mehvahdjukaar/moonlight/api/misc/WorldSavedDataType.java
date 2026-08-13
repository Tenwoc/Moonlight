package net.mehvahdjukaar.moonlight.api.misc;


import com.mojang.serialization.Codec;
import net.mehvahdjukaar.moonlight.api.MoonlightRegistry;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

//Basically a helper that manages syncing on world join and stores codecs cleanly
//per world, automatically synced when stream codec is not null. not per game. saved onto overworld level
public final class WorldSavedDataType<D extends WorldSavedData> {
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldSavedDataType<? extends WorldSavedData>> STREAM_CODEC =
            ByteBufCodecs.registry((ResourceKey) MoonlightRegistry.WORLD_SAVED_DATA_TYPE_REGISTRY.key());
    public static final Codec<WorldSavedDataType<? extends WorldSavedData>> CODEC =
            (Codec)      MoonlightRegistry.WORLD_SAVED_DATA_TYPE_REGISTRY.byNameCodec();

    //TODO: 26.1 old world data is NOT migrated: files moved from data/<ns>_<path>.dat (payload nested
    // under a name key) to data/<ns>/<path>.dat with the codec applied directly

    private final Supplier<Codec<D>> codec;
    @Nullable
    private final Supplier<StreamCodec<? super RegistryFriendlyByteBuf, D>> streamCodec;
    private final Function<ServerLevel, D> constructor;
    private final Identifier id;
    private final Scope scope;


    private D clientInstance = null;

    public enum Scope {
        SINGLE_OVERWORLD,
        PER_LEVEL;

        private ServerLevel getTargetLevel(ServerLevel sl) {
            ServerLevel targetLevel;
            if (this == Scope.PER_LEVEL) {
                targetLevel = sl;
            } else {
                targetLevel = sl.getServer().overworld();
            }
            return targetLevel;
        }
    }

    public WorldSavedDataType(Identifier id, Function<ServerLevel, D> overworldToDataConstructor,
                              Supplier<Codec<D>> codec,
                              @Nullable Supplier<StreamCodec<? super RegistryFriendlyByteBuf, D>> streamCodec) {
        this(id, overworldToDataConstructor, codec, streamCodec, Scope.SINGLE_OVERWORLD);
    }

    public WorldSavedDataType(Identifier id, Function<ServerLevel, D> overworldToDataConstructor,
                              Supplier<Codec<D>> codec,
                              @Nullable Supplier<StreamCodec<? super RegistryFriendlyByteBuf, D>> streamCodec, Scope scope) {
        this.codec = codec;
        this.streamCodec = streamCodec;
        this.id = id;
        this.scope = scope;
        this.constructor = overworldToDataConstructor;
    }

    // SavedDataType's constructor takes no level, so the target one has to be baked in per call.
    // Cheap to rebuild: the record only compares by id, so the storage map still sees the same key.
    private SavedDataType<D> typeFor(ServerLevel level) {
        return new SavedDataType<>(id, () -> constructor.apply(level), Codec.lazyInitialized(codec), null);
    }

    //only null when called on client too early
    @Nullable
    public D getData(Level level) {
        if (level.isClientSide() && !this.isSyncable()) {
            throw new IllegalStateException("Tried to access unsyncable world saved data on client side!");
        }

        if (level instanceof ServerLevel server) {
            ServerLevel targetLevel = scope.getTargetLevel(server);
            return targetLevel.getDataStorage().computeIfAbsent(typeFor(targetLevel));
        } else {
            return clientInstance;
        }
    }


    public void setData(Level level, D data) {
        if (level instanceof ServerLevel server) {
            ServerLevel targetLevel = scope.getTargetLevel(server);
            targetLevel.getDataStorage().set(typeFor(targetLevel), data);
        } else {
            this.clientInstance = data;
        }
        data.onReassigned(level);
    }

    public Codec<D> getCodec() {
        return codec.get();
    }

    @Nullable
    public StreamCodec<? super RegistryFriendlyByteBuf, D> getStreamCodec() {
        return streamCodec == null ? null : streamCodec.get();
    }

    public boolean isSyncable() {
        return streamCodec != null;
    }

    public Identifier getId() {
        return id;
    }

    public String getName() {
        return id.toDebugFileName();
    }
}
