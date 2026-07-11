package dev.xqanzd.moonlitbroker.registry;

import dev.xqanzd.moonlitbroker.katana.client.ClientMasteryCache;
import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModTooltips {
    private static final String MOD_ID = "xqanzd_moonlit_broker";
    private static final Map<String, List<String>> KATANA_INSCRIPTIONS = createKatanaInscriptions();
    private static boolean initialized;

    private ModTooltips() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) -> appendTooltip(stack, type, tooltip));
    }

    private static void appendTooltip(ItemStack stack, TooltipType tooltipType, List<Text> tooltip) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!MOD_ID.equals(id.getNamespace())) {
            return;
        }

        String path = id.getPath();
        List<String> inscriptions = TooltipHelper.isKatana(path) ? KATANA_INSCRIPTIONS.get(path) : null;

        TooltipComposer.compose(
                MOD_ID,
                path,
                stack,
                tooltipType,
                tooltip,
                inscriptions
        );

        appendMasteryTooltip(stack, tooltip);
    }

    private static void appendMasteryTooltip(ItemStack stack, List<Text> tooltip) {
        String katanaId = KatanaIdUtil.extractCanonicalKatanaId(stack);
        if (katanaId.isEmpty()) {
            return;
        }

        int progress = ClientMasteryCache.getProgress(katanaId);
        int stage = TradeConfig.masteryStageForProgress(progress);
        float multiplier = TradeConfig.masteryMultiplierForStage(stage);
        float bonusPercent = (multiplier - 1.0f) * 100.0f;
        String formattedBonus = formatBonusPercent(bonusPercent);

        tooltip.add(Text.empty());
        tooltip.add(Text.translatable(
                "tooltip.xqanzd_moonlit_broker.mastery.stage",
                stage).formatted(Formatting.GOLD));
        if (stage >= 3) {
            tooltip.add(Text.translatable(
                    "tooltip.xqanzd_moonlit_broker.mastery.max").formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable(
                    "tooltip.xqanzd_moonlit_broker.mastery.progress",
                    progress,
                    TradeConfig.masteryProgressTargetForStage(stage)).formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable(
                "tooltip.xqanzd_moonlit_broker.mastery.damage_bonus",
                formattedBonus).formatted(Formatting.GRAY));
    }

    private static String formatBonusPercent(float bonusPercent) {
        float rounded = Math.round(bonusPercent * 10.0f) / 10.0f;
        if (rounded == Math.rint(rounded)) {
            return String.format(Locale.ROOT, "%.0f", rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static Map<String, List<String>> createKatanaInscriptions() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("moon_glow_katana", List.of("朧に霞む春の月", "淡き光、掌に残る"));
        map.put("regret_blade", List.of("癒えぬ傷、名を呼ぶ"));
        map.put("eclipse_blade", List.of("月影、静かに喰らう"));
        map.put("oblivion_edge", List.of("思念、闇に沈む"));
        map.put("nmap_katana", List.of("兆しは既に在り"));
        return map;
    }
}
