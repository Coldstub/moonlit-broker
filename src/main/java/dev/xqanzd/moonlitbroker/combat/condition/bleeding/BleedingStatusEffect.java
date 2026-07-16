package dev.xqanzd.moonlitbroker.combat.condition.bleeding;

import dev.xqanzd.moonlitbroker.combat.condition.CombatConditionConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 出血状态效果：周期性脉冲伤害驱动器。
 * <p>
 * 脉冲 DamageSource 恒为自有 bleeding damage type、direct source =
 * null；attacker = 已解析的 applier，或仅在显式 ownerless application
 * 下为 null。attributed source 暂时无法解析（applier 维度未加载/实体
 * 未加载/已死亡）时**跳过该脉冲**，不得降级为 ownerless damage
 * （COMBAT-COND-DIC-03R）。attacker 非空时 {@code isDirect()==false}；
 * ownerless 双 null 与 vanilla {@code DamageSources.magic()} 同一性
 * 语义一致。归属载体 fingerprint（amplifier）与在体效果不符时视为
 * 绕过 API 的外部变异：清载体 + generation-matched 清 index（warn），
 * 不沿用旧 attacker。
 * <p>
 * {@link #onEntityRemoval} 是 removal→index 生命周期耦合的同步路径：
 * KILLED/DISCARDED 清 carrier + matching-generation entry；
 * CHANGED_DIMENSION 只清旧 world key entry（新实例由脉冲自愈重建）。
 */
public class BleedingStatusEffect extends StatusEffect {

    private static final Logger LOGGER = LoggerFactory.getLogger(BleedingStatusEffect.class);

    /** 效果颜色：暗血红。 */
    private static final int BLEEDING_COLOR = 0x8A0303;

    /** 出血 damage type（data/xqanzd_moonlit_broker/damage_type/bleeding.json）。
     *  与效果共用 "bleeding" ID（单一来源见 access contract）。 */
    public static final RegistryKey<DamageType> BLEEDING_DAMAGE_TYPE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, BleedAttributionAccess.BLEEDING_EFFECT_ID);

    public BleedingStatusEffect() {
        super(StatusEffectCategory.HARMFUL, BLEEDING_COLOR);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % CombatConditionConfig.BLEED_INTERVAL_TICKS == 0;
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !entity.isAlive()) {
            return true;
        }
        BleedAttributionAccess access = (BleedAttributionAccess) entity;
        NbtCompound carrierNbt = access.xqanzd_moonlit_broker$getBleedAttribution();
        BleedAttributionCarrier carrier = null;
        if (carrierNbt != null) {
            carrier = BleedAttributionCarrier.fromNbt(carrierNbt);
            String victimKey = BleedAttributionCarrier.victimKey(world, entity.getUuid());
            if (carrier == null) {
                LOGGER.warn("[CombatCondition] Malformed bleed attribution carrier cleared: {}", victimKey);
                access.xqanzd_moonlit_broker$clearBleedAttribution();
                BleedAttributionCarrier.AdmissionIndex.get(world.getServer()).removeEntry(victimKey);
            } else if (carrier.amplifier() != amplifier) {
                LOGGER.warn("[CombatCondition] Bleed carrier fingerprint mismatch (carrier amp {}, "
                        + "active amp {}) cleared, old attacker not retained: {}",
                        carrier.amplifier(), amplifier, victimKey);
                access.xqanzd_moonlit_broker$clearBleedAttribution();
                BleedAttributionCarrier.AdmissionIndex.get(world.getServer())
                        .removeIfGeneration(victimKey, carrier.generation());
                carrier = null;
            }
        }
        LivingEntity attacker = null;
        if (carrier != null && carrier.isAttributed()) {
            BleedAttributionCarrier.healIndexEntry(world, entity, carrier);
            attacker = BleedAttributionCarrier.resolveApplier(world.getServer(), carrier);
            if (attacker == null) {
                return true; // attributed source 暂不可解析：跳过脉冲
            }
        }
        float amount = CombatConditionConfig.BLEED_DAMAGE_BASE
                + amplifier * CombatConditionConfig.BLEED_DAMAGE_PER_AMP;
        DamageSource source = new DamageSource(
                world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEEDING_DAMAGE_TYPE),
                null, attacker);
        entity.damage(source, amount);
        return true;
    }

    @Override
    public void onEntityRemoval(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            BleedAttributionCarrier.clearOnRemoval(world, entity, reason);
        }
    }
}
