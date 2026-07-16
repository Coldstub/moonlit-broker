package dev.xqanzd.moonlitbroker.combat.condition;

import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedAttributionCarrier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat Conditions 子系统 init owner（COMBAT-COND-DIC-02R：isolated
 * init owner，经既有 entrypoint 集成，不经 Katana/Broker/Trust/bounty
 * initializer）。由 {@code Mymodtest.onInitialize} 在 ModScreenHandlers
 * 与 KatanaInit 之间调用一次。
 */
public final class CombatConditionInit {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatConditionInit.class);

    private CombatConditionInit() {
    }

    public static void register() {
        LOGGER.info("[CombatCondition] Initializing combat condition subsystem...");

        // 状态效果注册（registry freeze 前的唯一触发点）
        ModStatusEffects.register();

        // 出血 admission index 的有界逐 tick reconciliation
        ServerTickEvents.END_SERVER_TICK.register(BleedAttributionCarrier::reconcileTick);

        // DP-C1(a) campaign tooling：仅开发环境注册；生产运行时注册面 = 零
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            CombatConditionDebugCommand.register();
            LOGGER.info("[CombatCondition] Dev-only bleed debug command registered");
        }

        LOGGER.info("[CombatCondition] Combat condition subsystem initialized!");
    }
}
