package com.gr4v1ty.supplylines.network;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.network.messages.AddRestockPolicyMessage;
import com.gr4v1ty.supplylines.network.messages.AddSupplierMessage;
import com.gr4v1ty.supplylines.network.messages.GiveScepterMessage;
import com.gr4v1ty.supplylines.network.messages.RemoveRestockPolicyMessage;
import com.gr4v1ty.supplylines.network.messages.RemoveSupplierMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierAddressMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierLabelMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierPriorityMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierSpeculativeMessage;
import com.gr4v1ty.supplylines.network.messages.TriggerSettingMessage;
import com.minecolonies.api.network.IMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Network handler for SupplyLines mod. Registers and handles all network
 * messages.
 */
@SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x
public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel CHANNEL;
    private static int messageId = 0;

    /**
     * Initialize the network channel. Should be called during FMLCommonSetupEvent.
     */
    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(SupplyLines.MOD_ID, "main"),
                () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        // Register supplier messages
        registerMessage(AddSupplierMessage.class, AddSupplierMessage::new);
        registerMessage(RemoveSupplierMessage.class, RemoveSupplierMessage::new);
        registerMessage(SetSupplierPriorityMessage.class, SetSupplierPriorityMessage::new);
        registerMessage(SetSupplierAddressMessage.class, SetSupplierAddressMessage::new);
        registerMessage(SetSupplierLabelMessage.class, SetSupplierLabelMessage::new);
        registerMessage(SetSupplierSpeculativeMessage.class, SetSupplierSpeculativeMessage::new);

        // Register restock policy messages
        registerMessage(AddRestockPolicyMessage.class, AddRestockPolicyMessage::new);
        registerMessage(RemoveRestockPolicyMessage.class, RemoveRestockPolicyMessage::new);

        // Register scepter messages
        registerMessage(GiveScepterMessage.class, GiveScepterMessage::new);

        // Register settings messages
        registerMessage(TriggerSettingMessage.class, TriggerSettingMessage::new);
    }

    /**
     * Register a message type.
     *
     * @param clazz
     *            the message class.
     * @param factory
     *            the factory to create new instances.
     * @param <T>
     *            the message type.
     */
    private static <T extends IMessage> void registerMessage(final Class<T> clazz, final Supplier<T> factory) {
        CHANNEL.registerMessage(messageId++, clazz, IMessage::toBytes, buf -> {
            T msg = factory.get();
            msg.fromBytes(buf);
            return msg;
        }, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> msg.onExecute(ctx.get(), true));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * Send a message to the server.
     *
     * @param message
     *            the message to send.
     */
    public static void sendToServer(final IMessage message) {
        CHANNEL.sendToServer(message);
    }

    /**
     * Send a message to a specific player.
     *
     * @param message
     *            the message to send.
     * @param player
     *            the target player.
     */
    public static void sendToPlayer(final IMessage message, final ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
