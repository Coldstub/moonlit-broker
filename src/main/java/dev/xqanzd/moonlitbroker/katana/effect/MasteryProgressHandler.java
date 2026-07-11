package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

/**
 * Server-side Katana Mastery settlement.
 * Attribution ownership remains in {@link KatanaMasteryHooks}; this handler
 * only consumes an accepted attribution and advances persistent progress.
 */
public final class MasteryProgressHandler {
    private MasteryProgressHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(MasteryProgressHandler::onEntityDeath);
    }

    private static void onEntityDeath(LivingEntity target, DamageSource source) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return;
        }

        var attribution = KatanaMasteryHooks.consumeEligibleDirectDeath(world, target, source);
        if (attribution.isEmpty()) {
            return;
        }

        MerchantUnlockState.getServerState(world).addKatanaMasteryProgress(
                attribution.get().attackerUuid(),
                attribution.get().canonicalKatanaId(),
                1);
    }
}
