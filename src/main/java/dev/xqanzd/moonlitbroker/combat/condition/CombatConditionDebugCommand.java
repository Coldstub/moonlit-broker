package dev.xqanzd.moonlitbroker.combat.condition;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * DP-C1(a) campaign tooling：仅开发环境注册的出血调试命令。
 * <p>
 * 合同（RUNTIME_CAMPAIGN_CHARTER_2026-07_v2 §2）：注册面由
 * {@code CombatConditionInit} 以 {@code isDevelopmentEnvironment()}
 * 门控（生产运行时注册面 = 零；class bytes 可保留打包）；permission
 * >= 2；行为上**独占**调用 {@link CombatConditions#applyBleeding}，
 * 绝不直接 {@code addStatusEffect}、绝不直写 carrier/persistent
 * index；回显精确 ApplyResult 与施加后 active amplifier/duration。
 *
 * <pre>
 * /bleed apply &lt;target&gt; &lt;durationTicks&gt; &lt;amplifier&gt; ownerless
 * /bleed apply &lt;target&gt; &lt;durationTicks&gt; &lt;amplifier&gt; source &lt;livingEntity&gt;
 * </pre>
 */
public final class CombatConditionDebugCommand {

    private CombatConditionDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("bleed")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("apply")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .then(CommandManager.argument("durationTicks", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .then(CommandManager.literal("ownerless")
                                                                .executes(ctx -> executeApply(ctx, false)))
                                                        .then(CommandManager.literal("source")
                                                                .then(CommandManager.argument("source", EntityArgumentType.entity())
                                                                        .executes(ctx -> executeApply(ctx, true))))))))
        );
    }

    private static int executeApply(CommandContext<ServerCommandSource> context, boolean attributed)
            throws CommandSyntaxException {
        Entity targetEntity = EntityArgumentType.getEntity(context, "target");
        if (!(targetEntity instanceof LivingEntity victim)) {
            // debug-only 输出有意不本地化（dev 环境专用，生产零注册面）
            Text targetError = Text.literal("[bleed] target is not a LivingEntity");
            context.getSource().sendError(targetError);
            return 0;
        }
        LivingEntity applier = null;
        if (attributed) {
            Entity sourceEntity = EntityArgumentType.getEntity(context, "source");
            if (!(sourceEntity instanceof LivingEntity livingSource)) {
                Text sourceError = Text.literal("[bleed] source is not a LivingEntity");
                context.getSource().sendError(sourceError);
                return 0;
            }
            applier = livingSource;
        }
        int durationTicks = IntegerArgumentType.getInteger(context, "durationTicks");
        int amplifier = IntegerArgumentType.getInteger(context, "amplifier");

        CombatConditions.BleedApplicationResult result =
                CombatConditions.applyBleeding(victim, amplifier, durationTicks, applier);

        @Nullable
        StatusEffectInstance active = victim.getStatusEffect(ModStatusEffects.BLEEDING);
        String activeText = active == null
                ? "active=NONE"
                : "active amplifier=" + active.getAmplifier() + " duration=" + active.getDuration();
        boolean success = result == CombatConditions.BleedApplicationResult.APPLIED
                || result == CombatConditions.BleedApplicationResult.REFRESHED;
        Text feedback = Text.literal("[bleed] " + result.name() + " | " + activeText)
                .formatted(success ? Formatting.GREEN : Formatting.YELLOW);
        context.getSource().sendFeedback(() -> feedback, false);
        return success ? 1 : 0;
    }
}
