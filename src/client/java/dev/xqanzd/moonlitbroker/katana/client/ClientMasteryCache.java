package dev.xqanzd.moonlitbroker.katana.client;

import dev.xqanzd.moonlitbroker.trade.KatanaIdUtil;
import dev.xqanzd.moonlitbroker.trade.TradeConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only client view of mastery progress for the current connection.
 */
public final class ClientMasteryCache {
    private static final Map<String, Integer> PROGRESS_BY_KATANA = new HashMap<>();

    private ClientMasteryCache() {
    }

    public static int getProgress(String katanaId) {
        String canonical = KatanaIdUtil.canonicalizeKatanaId(katanaId);
        if (canonical.isEmpty()) {
            return 0;
        }
        return PROGRESS_BY_KATANA.getOrDefault(canonical, 0);
    }

    static void applyProgress(String katanaId, int progress) {
        String canonical = KatanaIdUtil.canonicalizeKatanaId(katanaId);
        if (canonical.isEmpty() || !canonical.equals(katanaId)) {
            return;
        }

        if (progress <= 0) {
            PROGRESS_BY_KATANA.remove(canonical);
            return;
        }

        PROGRESS_BY_KATANA.put(canonical, TradeConfig.clampKatanaMasteryProgress(progress));
    }

    static void clear() {
        PROGRESS_BY_KATANA.clear();
    }
}
