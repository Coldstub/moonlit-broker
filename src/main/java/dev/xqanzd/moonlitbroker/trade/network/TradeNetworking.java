package dev.xqanzd.moonlitbroker.trade.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 交易系统网络包注册
 */
public class TradeNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeNetworking.class);

    /**
     * 注册所有网络包（服务端）
     */
    public static void registerServer() {
        // 注册 C2S 包类型
        PayloadTypeRegistry.playC2S().register(
            TradeActionC2SPacket.ID,
            TradeActionC2SPacket.CODEC
        );

        // 注册 S2C 包类型
        PayloadTypeRegistry.playS2C().register(
            MasteryProgressS2CPacket.ID,
            MasteryProgressS2CPacket.CODEC
        );
        
        // 注册 C2S 包处理器
        ServerPlayNetworking.registerGlobalReceiver(
            TradeActionC2SPacket.ID,
            TradeActionHandler::handle
        );
        
        LOGGER.info("[MoonTrade] 网络包已注册");
    }

    /**
     * 注册客户端网络包
     */
    public static void registerClient() {
        // Client-only receivers are registered from the client entrypoint.
        // Payload types are registered through the common initializer on both physical sides.
    }
}
