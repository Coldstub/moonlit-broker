package dev.xqanzd.moonlitbroker.combat.condition.bleeding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 出血归属载体的 mixin access contract。
 * <p>
 * 由 {@code BleedAttributionMixin} 在 {@link net.minecraft.entity.LivingEntity}
 * 上实现；任何 {@code LivingEntity} 均可强转为本接口。载体为不透明
 * {@link NbtCompound}：record 字段语义（source kind、applier UUID、
 * applier world key、application generation、获胜 amplifier/duration
 * fingerprint）的读写与校验由 {@code BleedAttributionCarrier} 单一拥有，
 * 本接口只负责随实体 NBT 持久化与移除清理。
 * <p>
 * 常量在此单一定义：mixin 侧禁止引用 {@code ModStatusEffects}
 * （避免在 registry freeze 之后才首次触发其静态注册初始化）；
 * 注册侧从这里取 ID，保证零双写。
 */
public interface BleedAttributionAccess {

    /** 出血效果注册 ID（单一来源）。 */
    Identifier BLEEDING_EFFECT_ID = Identifier.of("xqanzd_moonlit_broker", "bleeding");

    /** 实体 NBT 中的归属载体 key（namespaced，不与 vanilla/其他子系统冲突）。 */
    String BLEED_ATTRIBUTION_NBT_KEY = "xqanzd_moonlit_broker:bleed_attribution";

    /**
     * @return 当前归属载体；实体无出血归属时为 null
     */
    @Nullable
    NbtCompound xqanzd_moonlit_broker$getBleedAttribution();

    /**
     * 提交归属载体。仅允许 winning application 之后调用（所有权规则由
     * carrier owner 执行，本方法不做仲裁）。
     *
     * @param carrier 载体 compound；调用方保留所有权，实现方存副本
     */
    void xqanzd_moonlit_broker$setBleedAttribution(NbtCompound carrier);

    /**
     * 立即清除归属载体（效果移除/缺失清理路径）。幂等。
     */
    void xqanzd_moonlit_broker$clearBleedAttribution();
}
