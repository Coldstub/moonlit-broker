package dev.xqanzd.moonlitbroker.combat.condition;

import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedAttributionAccess;
import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedAttributionCarrier;
import dev.xqanzd.moonlitbroker.entity.MysteriousMerchantEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat Conditions 公共施加 API。admission 与 exclusion 的独占 owner：
 * 模组内一切出血施加必须经 {@link #applyBleeding}，不得直接
 * {@code addStatusEffect}。
 * <p>
 * 归属所有权规则（COMBAT-COND-DIC-03R）：只有实际获胜的效果施加才
 * 提交 attribution —— 被更高 amplifier 拒绝的低阶施加不偷取归属；
 * 成功的更高 amplifier 与成功的同 amplifier refresh 由最近获胜 applier
 * 接管；成功的 ownerless 重施加清除旧 attacker。满载时既有 attributed
 * victim 可 refresh，新 attributed victim 显式 {@code REJECTED_CAPACITY}
 * （施加整体失败），禁止 eviction 与 ownerless 降级。
 */
public final class CombatConditions {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatConditions.class);

    /** {@link #applyBleeding} 的五类明确结果。 */
    public enum BleedApplicationResult {
        /** 施加成功，此前不带出血。 */
        APPLIED,
        /** 施加成功，覆盖/刷新既有出血。 */
        REFRESHED,
        /** 效果层拒绝（在体更高 amplifier、上游 mixin veto 等），零归属变更。 */
        REJECTED_EFFECT,
        /** attributed admission index 满载且 victim 无既有 entry，施加整体失败。 */
        REJECTED_CAPACITY,
        /** 排除域：creative/spectator、无敌、已死亡、神秘商人、非法参数、非服务端。 */
        REJECTED_EXCLUDED
    }

    private CombatConditions() {
    }

    /**
     * 施加出血。
     *
     * @param victim   目标
     * @param amplifier 效果等级（>= 0）
     * @param durationTicks 持续 tick（> 0）
     * @param applier  归属施加者；null = 显式 ownerless application
     *                 （环境性出血），不占用 admission 容量、不伪造归属
     */
    public static BleedApplicationResult applyBleeding(LivingEntity victim, int amplifier,
                                                       int durationTicks,
                                                       @Nullable LivingEntity applier) {
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            LOGGER.warn("[CombatCondition] applyBleeding called off-server; rejected");
            return BleedApplicationResult.REJECTED_EXCLUDED;
        }
        if (amplifier < 0 || durationTicks <= 0) {
            return BleedApplicationResult.REJECTED_EXCLUDED;
        }
        if (!victim.isAlive() || victim.isInvulnerable()) {
            return BleedApplicationResult.REJECTED_EXCLUDED;
        }
        if (victim instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
            return BleedApplicationResult.REJECTED_EXCLUDED;
        }
        if (victim instanceof MysteriousMerchantEntity) {
            return BleedApplicationResult.REJECTED_EXCLUDED;
        }

        boolean attributed = applier != null;
        BleedAttributionCarrier.AdmissionIndex index = BleedAttributionCarrier.AdmissionIndex.get(world.getServer());
        String victimKey = BleedAttributionCarrier.victimKey(world, victim.getUuid());
        if (attributed && !index.hasEntry(victimKey)
                && index.size() >= CombatConditionConfig.BLEED_TRACKER_CAP) {
            // 拒绝前先做一次有界全量 stale 清理，保证 stale entry 不阻塞合法申请
            BleedAttributionCarrier.reconcileAllLoaded(world.getServer());
            if (index.size() >= CombatConditionConfig.BLEED_TRACKER_CAP) {
                return BleedApplicationResult.REJECTED_CAPACITY;
            }
        }

        boolean wasPresent = victim.hasStatusEffect(ModStatusEffects.BLEEDING);
        boolean accepted = victim.addStatusEffect(
                new StatusEffectInstance(ModStatusEffects.BLEEDING, durationTicks, amplifier), applier);
        if (!accepted) {
            return BleedApplicationResult.REJECTED_EFFECT;
        }
        StatusEffectInstance active = victim.getStatusEffect(ModStatusEffects.BLEEDING);
        if (active == null || active.getAmplifier() != amplifier || active.getDuration() != durationTicks) {
            // addStatusEffect 报告成功但 active instance 与候选不符（上游 mixin
            // 变异等）：非获胜施加，不提交归属
            LOGGER.warn("[CombatCondition] Bleeding application accepted but active instance "
                    + "mismatches candidate (victim {}); attribution not committed", victimKey);
            return BleedApplicationResult.REJECTED_EFFECT;
        }

        long generation = index.nextGeneration();
        BleedAttributionCarrier carrier = attributed
                ? BleedAttributionCarrier.attributed(applier.getUuid(),
                        applier.getWorld().getRegistryKey().getValue(), generation, amplifier, durationTicks)
                : BleedAttributionCarrier.ownerless(generation, amplifier, durationTicks);
        ((BleedAttributionAccess) victim).xqanzd_moonlit_broker$setBleedAttribution(carrier.toNbt());
        if (attributed) {
            index.put(victimKey, generation);
        } else {
            // 成功 ownerless 重施加清除旧 attacker 的 admission entry
            index.removeEntry(victimKey);
        }
        return wasPresent ? BleedApplicationResult.REFRESHED : BleedApplicationResult.APPLIED;
    }
}
