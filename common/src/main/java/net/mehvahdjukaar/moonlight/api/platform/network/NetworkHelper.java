package net.mehvahdjukaar.moonlight.api.platform.network;

import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class NetworkHelper {

    @PlatformImpl
    public static void addNetworkRegistration(Consumer<RegisterMessagesEvent> eventListener, int version) {
        throw new AssertionError();
    }

    public interface RegisterMessagesEvent {
        <M extends Message> void registerServerBound(CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, M> messageType);

        <M extends Message> void registerClientBound(CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, M> messageType);

        /**
         * Clientbound, but a connection is allowed to go without it. Use this for anything a mod can live
         * without on one side, typically a client-side-only mod that merely works better with a server half:
         * NeoForge otherwise denies any connection that is missing a registered payload, which would lock such
         * a client out of every server that doesn't have the mod (vanilla ones included).
         *
         * <p>Two things follow from a payload being optional. It may be sent to players who can't receive it,
         * so NetworkHelper.sendToClientPlayer quietly skips those; the broadcast helpers do not, and
         * will throw on NeoForge, so send optional payloads per player. And the client can ask whether the
         * server has the mod at all, through NetworkHelper.serverHasChannel, which only works for
         * payloads registered here.
         */
        <M extends Message> void registerClientBoundOptional(CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, M> messageType);

        <M extends Message> void registerBidirectional(CustomPacketPayload.TypeAndCodec<RegistryFriendlyByteBuf, M> messageType);
    }

    private static final Set<Identifier> OPTIONAL_PAYLOADS = new HashSet<>();

    @ApiStatus.Internal
    public static void markOptional(CustomPacketPayload.Type<?> type) {
        OPTIONAL_PAYLOADS.add(type.id());
    }

    /** Whether this payload was registered with RegisterMessagesEvent.registerClientBoundOptional. */
    public static boolean isOptional(CustomPacketPayload.Type<?> type) {
        return OPTIONAL_PAYLOADS.contains(type.id());
    }

    /**
     * Server side: whether this player's client negotiated the given channel, i.e. whether it can be sent this
     * payload at all. Always true for a required payload, since a client without it can't be connected.
     */
    @PlatformImpl
    public static boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        throw new AssertionError();
    }

    /**
     * Client side: whether the server this client is connected to has the given channel, read off what the two
     * sides agreed on when the connection was set up. This is the way for a client to tell that the server has
     * a mod, and only answers for payloads registered with
     * RegisterMessagesEvent.registerClientBoundOptional (a required one can't be missing, and a plain
     * clientbound one isn't advertised in a direction Fabric lets clients see).
     */
    @PlatformImpl
    public static boolean serverHasChannel(CustomPacketPayload.Type<?> type) {
        throw new AssertionError();
    }


    @PlatformImpl
    public static void sendToClientPlayer(ServerPlayer serverPlayer, CustomPacketPayload message) {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void sendToAllClientPlayers(CustomPacketPayload message) {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void sendToAllClientPlayersInRange(ServerLevel level, BlockPos pos, double radius, CustomPacketPayload message) {
        throw new AssertionError();
    }

    public static void sendToAllClientPlayersInDefaultRange(ServerLevel level, BlockPos pos, CustomPacketPayload message) {
        sendToAllClientPlayersInRange(level, pos, 64, message);
    }

    // same distance as serverlevel send particles
    public static void sendToAllClientPlayersInParticleRange(ServerLevel level, BlockPos pos, CustomPacketPayload message) {
        sendToAllClientPlayersInRange(level, pos, 32, message);
    }

    public static void sendToAllClientPlayersInDistantParticleRange(ServerLevel level, BlockPos pos, CustomPacketPayload message) {
        sendToAllClientPlayersInRange(level, pos, 512, message);
    }

    @PlatformImpl
    public static void sendToAllClientPlayersTrackingEntity(Entity target, CustomPacketPayload message) {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void sendToAllClientPlayersTrackingChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload message) {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void sendToAllClientPlayersTrackingEntityAndSelf(Entity target, Message message) {
        throw new AssertionError();
    }

    @PlatformImpl
    public static void sendToServer(CustomPacketPayload message) {
        throw new AssertionError();
    }


}
