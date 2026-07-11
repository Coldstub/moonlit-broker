package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import dev.xqanzd.moonlitbroker.util.KatanaContractUtil;
import dev.xqanzd.moonlitbroker.world.KatanaOwnershipState;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared attack-time seam for Katana Mastery v0.
 *
 * <p>Only same-server-tick direct {@code PLAYER_ATTACK} deaths can consume a
 * record. The configured attribution window bounds transient memory only; it
 * never extends kill credit beyond the originating server tick.</p>
 */
public final class KatanaMasteryHooks {
    private static final LinkedHashMap<AttributionKey, DirectAttribution> DIRECT_ATTRIBUTIONS =
            new LinkedHashMap<>();

    private KatanaMasteryHooks() {
    }

    /**
     * Records an eligible hostile-katana attack and returns its current mastery multiplier.
     * Invalid candidates deliberately return the stage-zero multiplier without side effects.
     */
    public static float recordEligibleAttack(
            ServerWorld world,
            PlayerEntity player,
            LivingEntity target,
            ItemStack katanaStack) {
        if (world == null || player == null || target == null || katanaStack == null
                || !(target instanceof HostileEntity)) {
            return TradeConfig.MASTERY_STAGE_0_DAMAGE_MULTIPLIER;
        }

        String canonicalKatanaId = KatanaIdUtil.extractCanonicalKatanaId(katanaStack);
        if (canonicalKatanaId.isEmpty()) {
            return TradeConfig.MASTERY_STAGE_0_DAMAGE_MULTIPLIER;
        }

        UUID activeInstanceId = activeInstanceIdIfEligible(world, player, katanaStack, canonicalKatanaId);
        if (activeInstanceId == null) {
            return TradeConfig.MASTERY_STAGE_0_DAMAGE_MULTIPLIER;
        }

        MinecraftServer server = world.getServer();
        long serverTick = server.getTicks();
        cleanupStaleAttributions(server, serverTick);

        UUID attackerUuid = player.getUuid();
        UUID victimUuid = target.getUuid();
        AttributionKey key = new AttributionKey(attackerUuid, victimUuid);
        DirectAttribution attribution = new DirectAttribution(
                server,
                attackerUuid,
                victimUuid,
                canonicalKatanaId,
                activeInstanceId,
                serverTick);

        // LinkedHashMap insertion order does not refresh on put for an existing key.
        DIRECT_ATTRIBUTIONS.remove(key);
        evictOldestAttributionsUntilBelowCap();
        DIRECT_ATTRIBUTIONS.put(key, attribution);

        int stage = MerchantUnlockState.getServerState(world)
                .getKatanaMasteryStage(attackerUuid, canonicalKatanaId);
        return multiplierForStage(stage);
    }

    /**
     * Consumes only a direct, same-server-tick, active-contract player attack.
     * The source weapon is re-checked at death time because DamageSource does
     * not provide an immutable attack-time weapon snapshot.
     */
    public static Optional<DirectAttribution> consumeEligibleDirectDeath(
            ServerWorld world,
            LivingEntity target,
            DamageSource source) {
        if (world == null || target == null || source == null) {
            return Optional.empty();
        }

        MinecraftServer server = world.getServer();
        long deathTick = server.getTicks();
        cleanupStaleAttributions(server, deathTick);

        UUID victimUuid = target.getUuid();
        ServerPlayerEntity attacker = source.getAttacker() instanceof ServerPlayerEntity player
                ? player
                : null;
        DirectAttribution attribution = attacker == null
                ? null
                : DIRECT_ATTRIBUTIONS.get(new AttributionKey(attacker.getUuid(), victimUuid));

        // A dead victim can never legally consume any remaining attack candidate.
        removeAttributionsForDeadVictim(server, victimUuid);

        if (attacker == null || attribution == null
                || !source.isDirect()
                || !source.isOf(DamageTypes.PLAYER_ATTACK)
                || attribution.server() != server
                || !attribution.attackerUuid().equals(attacker.getUuid())
                || !attribution.victimUuid().equals(victimUuid)
                || attribution.serverTick() != deathTick) {
            return Optional.empty();
        }

        ItemStack sourceWeapon = source.getWeaponStack();
        if (sourceWeapon == null
                || !attribution.canonicalKatanaId().equals(KatanaIdUtil.extractCanonicalKatanaId(sourceWeapon))
                || !attribution.activeInstanceId().equals(KatanaContractUtil.getInstanceId(sourceWeapon))
                || !attribution.activeInstanceId().equals(activeInstanceIdIfEligible(
                        world,
                        attacker,
                        sourceWeapon,
                        attribution.canonicalKatanaId()))) {
            return Optional.empty();
        }

        return Optional.of(attribution);
    }

    private static UUID activeInstanceIdIfEligible(
            ServerWorld world,
            PlayerEntity player,
            ItemStack katanaStack,
            String canonicalKatanaId) {
        if (!canonicalKatanaId.equals(KatanaIdUtil.extractCanonicalKatanaId(katanaStack))
                || !KatanaContractUtil.isActiveContract(world, player, katanaStack)) {
            return null;
        }

        UUID stackInstanceId = KatanaContractUtil.getInstanceId(katanaStack);
        if (stackInstanceId == null) {
            return null;
        }

        KatanaOwnershipState ownershipState = KatanaOwnershipState.getServerState(world);
        UUID playerUuid = player.getUuid();
        if (!ownershipState.hasOwned(playerUuid, canonicalKatanaId)) {
            return null;
        }

        UUID activeInstanceId = ownershipState.getActiveInstanceId(playerUuid, canonicalKatanaId);
        return stackInstanceId.equals(activeInstanceId) ? activeInstanceId : null;
    }

    private static void cleanupStaleAttributions(MinecraftServer server, long currentTick) {
        Iterator<Map.Entry<AttributionKey, DirectAttribution>> iterator =
                DIRECT_ATTRIBUTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            DirectAttribution attribution = iterator.next().getValue();
            long age = currentTick - attribution.serverTick();
            if (attribution.server() != server || age < 0
                    || age > TradeConfig.MASTERY_ATTRIBUTION_WINDOW_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void evictOldestAttributionsUntilBelowCap() {
        while (DIRECT_ATTRIBUTIONS.size() >= TradeConfig.MASTERY_ATTRIBUTION_BUFFER_CAP) {
            Iterator<AttributionKey> iterator = DIRECT_ATTRIBUTIONS.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static void removeAttributionsForDeadVictim(MinecraftServer server, UUID victimUuid) {
        Iterator<Map.Entry<AttributionKey, DirectAttribution>> iterator =
                DIRECT_ATTRIBUTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            DirectAttribution attribution = iterator.next().getValue();
            if (attribution.server() == server && attribution.victimUuid().equals(victimUuid)) {
                iterator.remove();
            }
        }
    }

    private static float multiplierForStage(int stage) {
        return switch (stage) {
            case 1 -> TradeConfig.MASTERY_STAGE_1_DAMAGE_MULTIPLIER;
            case 2 -> TradeConfig.MASTERY_STAGE_2_DAMAGE_MULTIPLIER;
            case 3 -> TradeConfig.MASTERY_STAGE_3_DAMAGE_MULTIPLIER;
            default -> TradeConfig.MASTERY_STAGE_0_DAMAGE_MULTIPLIER;
        };
    }

    private record AttributionKey(UUID attackerUuid, UUID victimUuid) {
    }

    public record DirectAttribution(
            MinecraftServer server,
            UUID attackerUuid,
            UUID victimUuid,
            String canonicalKatanaId,
            UUID activeInstanceId,
            long serverTick) {
    }
}
