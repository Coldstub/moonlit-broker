package dev.xqanzd.moonlitbroker.katana.effect;

import dev.xqanzd.moonlitbroker.trade.network.MasteryProgressS2CPacket;
import dev.xqanzd.moonlitbroker.world.MerchantUnlockState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendInitialSnapshot(handler.player));
    }

    private static void onEntityDeath(LivingEntity target, DamageSource source) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return;
        }

        var attribution = KatanaMasteryHooks.consumeEligibleDirectDeath(world, target, source);
        if (attribution.isEmpty()) {
            return;
        }

        var acceptedAttribution = attribution.get();
        var result = MerchantUnlockState.getServerState(world).addKatanaMasteryProgress(
                acceptedAttribution.attackerUuid(),
                acceptedAttribution.canonicalKatanaId(),
                1);
        if (!result.progressed()) {
            return;
        }

        ServerPlayerEntity player = world.getServer().getPlayerManager()
                .getPlayer(acceptedAttribution.attackerUuid());
        if (player != null) {
            sendProgress(player, acceptedAttribution.canonicalKatanaId(), result.currentProgress());
        }
    }

    private static void sendInitialSnapshot(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, MasteryProgressS2CPacket.ID)) {
            return;
        }
        MerchantUnlockState.getServerState(player.getServerWorld())
                .getKatanaMasterySnapshot(player.getUuid())
                .forEach((katanaId, progress) ->
                        ServerPlayNetworking.send(player, new MasteryProgressS2CPacket(katanaId, progress)));
    }

    private static void sendProgress(ServerPlayerEntity player, String katanaId, int progress) {
        if (progress > 0 && ServerPlayNetworking.canSend(player, MasteryProgressS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new MasteryProgressS2CPacket(katanaId, progress));
        }
    }
}
