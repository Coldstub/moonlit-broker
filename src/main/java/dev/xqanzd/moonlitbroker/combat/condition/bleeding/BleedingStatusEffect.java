package dev.xqanzd.moonlitbroker.combat.condition.bleeding;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 出血状态效果。
 * <p>
 * 周期性脉冲伤害由 {@code CombatConditions} 行为接线后生效；本类自身
 * 不在 tick 中造成伤害前，保持注册载体语义。伤害来源恒为间接
 * （非 {@code PLAYER_ATTACK}、{@code isDirect()==false}），归属状态
 * 由 {@link BleedAttributionAccess} 载体持有，不由效果实例持有。
 */
public class BleedingStatusEffect extends StatusEffect {

    /** 效果颜色：暗血红。 */
    private static final int BLEEDING_COLOR = 0x8A0303;

    public BleedingStatusEffect() {
        super(StatusEffectCategory.HARMFUL, BLEEDING_COLOR);
    }
}
