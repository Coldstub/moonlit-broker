package dev.xqanzd.moonlitbroker.katana.client;

import dev.xqanzd.moonlitbroker.trade.network.MasteryProgressS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MasteryClientNetworking {
    private static boolean registered;

    private MasteryClientNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientPlayNetworking.registerGlobalReceiver(
                MasteryProgressS2CPacket.ID,
                (payload, context) -> ClientMasteryCache.applyProgress(
                        payload.katanaId(),
                        payload.progress())
        );

        ClientPlayConnectionEvents.INIT.register(
                (handler, client) -> ClientMasteryCache.clear()
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientMasteryCache.clear()
        );
    }
}
