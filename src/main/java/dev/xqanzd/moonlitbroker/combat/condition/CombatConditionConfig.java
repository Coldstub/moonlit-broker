package dev.xqanzd.moonlitbroker.combat.condition;

/**
 * Combat Conditions 平衡常量（constants-only；FC3 全部战斗状态数值的
 * 唯一 owner，零 TradeConfig 追加 — COMBAT-COND-DIC-01）。
 * <p>
 * 数值随 CODE_PATCH_PLAN 接受冻结；runtime 矩阵后可调，不回写 canon。
 * 编译期常量、无磁盘 IO，与既有 *Config 同 pattern，无初始化顺序风险。
 */
public final class CombatConditionConfig {

    // ========== Bleeding（FC3-A）==========

    /**
     * 出血脉冲间隔（tick）。必须 >= 20：脉冲依赖自然错开 vanilla 伤害
     * cooldown 窗口（10 tick 比较 / 20 tick 重置），bleeding damage type
     * 显式不进入 bypasses_cooldown tag。
     */
    public static final int BLEED_INTERVAL_TICKS = 40;

    /** 每脉冲基础伤害。 */
    public static final float BLEED_DAMAGE_BASE = 1.0f;

    /** 每级 amplifier 的每脉冲附加伤害。 */
    public static final float BLEED_DAMAGE_PER_AMP = 0.5f;

    /**
     * attributed 出血受害者的 standalone persistent admission index 容量。
     * 满载时既有受害者可 refresh，新 attributed 施加显式 REJECTED_CAPACITY；
     * 禁止 eviction 与 ownerless 降级。
     */
    public static final int BLEED_TRACKER_CAP = 1024;

    static {
        if (BLEED_INTERVAL_TICKS < 20) {
            throw new IllegalStateException(
                    "BLEED_INTERVAL_TICKS must be >= 20 (bleeding does not join bypasses_cooldown): "
                            + BLEED_INTERVAL_TICKS);
        }
        if (BLEED_DAMAGE_BASE <= 0.0f) {
            throw new IllegalStateException("BLEED_DAMAGE_BASE must be positive: " + BLEED_DAMAGE_BASE);
        }
        if (BLEED_DAMAGE_PER_AMP < 0.0f) {
            throw new IllegalStateException("BLEED_DAMAGE_PER_AMP must be non-negative: " + BLEED_DAMAGE_PER_AMP);
        }
        if (BLEED_TRACKER_CAP <= 0) {
            throw new IllegalStateException("BLEED_TRACKER_CAP must be positive: " + BLEED_TRACKER_CAP);
        }
    }

    private CombatConditionConfig() {
    }
}
