package dev.xqanzd.moonlitbroker.combat.condition;

import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedAttributionAccess;
import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedingStatusEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat Conditions 状态效果注册持有者（registerReference holder）。
 * <p>
 * FC3 全部战斗状态效果的唯一注册点。注册通过静态字段初始化完成，
 * 由 {@code CombatConditionInit.register()} 在 {@code Mymodtest.onInitialize}
 * 中触发；registry freeze 之后不得再触发本类的类加载首次初始化。
 * <p>
 * {@link net.minecraft.entity.effect.StatusEffectInstance} 的全部构造均要求
 * {@link RegistryEntry}，故使用 {@code Registry.registerReference}。
 */
public final class ModStatusEffects {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModStatusEffects.class);

    /** 出血（Bleeding）。ID 由 mixin access contract 单一拥有，避免双写。 */
    public static final RegistryEntry<StatusEffect> BLEEDING = Registry.registerReference(
            Registries.STATUS_EFFECT,
            BleedAttributionAccess.BLEEDING_EFFECT_ID,
            new BleedingStatusEffect());

    private ModStatusEffects() {
    }

    /**
     * 触发静态字段初始化（注册）。
     */
    public static void register() {
        LOGGER.info("[MoonlitBroker] Combat condition status effects registered");
    }
}
