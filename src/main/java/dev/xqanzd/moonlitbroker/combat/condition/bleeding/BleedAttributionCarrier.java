package dev.xqanzd.moonlitbroker.combat.condition.bleeding;

import dev.xqanzd.moonlitbroker.combat.condition.CombatConditionConfig;
import dev.xqanzd.moonlitbroker.combat.condition.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 出血归属载体 owner：entity-bound NBT record 的类型化读写 +
 * standalone persistent capped admission index。
 * <p>
 * 载体（record）是归属的权威记录，随实体 NBT 存续（A1 mixin 持久化）；
 * index 是 attributed 施加的容量记账，key = (victim world key, victim
 * UUID)，容量 {@link CombatConditionConfig#BLEED_TRACKER_CAP}，不读写
 * {@code MerchantUnlockState}。
 * <p>
 * 生命周期耦合（removal → carrier clear → matching-generation index
 * entry 同步清除）由三条有界路径共同保证：
 * <ol>
 * <li>victim 被销毁（KILLED/DISCARDED）或跨维度（CHANGED_DIMENSION）：
 *     {@code BleedingStatusEffect.onEntityRemoval} 同步调用
 *     {@link #clearOnRemoval}，generation-matched 清除。</li>
 * <li>效果在已加载存活 victim 上被移除/自然过期（无 vanilla 回调可用，
 *     A1 mixin 只清 carrier 字段）：{@link #reconcileTick} 每 server tick
 *     以游标扫描至多 {@link #RECONCILE_PER_TICK} 条 entry，载体/效果/
 *     generation 与 entry 不符即清除 —— 全量 index 的复核周期
 *     ≤ cap/64 = 16 tick。</li>
 * <li>admission 满载路径：{@link #reconcileAllLoaded} 在拒绝前做一次
 *     全量（受 cap 上界约束）stale 清理，保证 stale entry 不占用容量、
 *     不阻塞合法新申请。</li>
 * </ol>
 * 反向自愈：跨维度后 entry 已按旧 world key 清除，新实例首个脉冲经
 * {@link #healIndexEntry} 依据权威载体重建 entry（容量内 upsert，
 * 永不 eviction）。
 */
public final class BleedAttributionCarrier {

    private static final Logger LOGGER = LoggerFactory.getLogger(BleedAttributionCarrier.class);

    /** 每 tick reconciliation 扫描的 entry 上限（有界）。 */
    private static final int RECONCILE_PER_TICK = 64;

    // NBT keys（载体 record）
    private static final String NBT_KIND = "Kind";
    private static final String NBT_APPLIER_UUID = "ApplierUuid";
    private static final String NBT_APPLIER_WORLD = "ApplierWorld";
    private static final String NBT_GENERATION = "Generation";
    private static final String NBT_AMPLIFIER = "Amplifier";
    private static final String NBT_DURATION = "Duration";

    private static final String KIND_ATTRIBUTED = "ATTRIBUTED";
    private static final String KIND_OWNERLESS = "OWNERLESS";

    private static boolean healCapacityWarned = false;

    private final String kind;
    @Nullable
    private final UUID applierUuid;
    @Nullable
    private final Identifier applierWorld;
    private final long generation;
    private final int amplifier;
    private final int duration;

    private BleedAttributionCarrier(String kind, @Nullable UUID applierUuid,
                                    @Nullable Identifier applierWorld,
                                    long generation, int amplifier, int duration) {
        this.kind = kind;
        this.applierUuid = applierUuid;
        this.applierWorld = applierWorld;
        this.generation = generation;
        this.amplifier = amplifier;
        this.duration = duration;
    }

    public static BleedAttributionCarrier attributed(UUID applierUuid, Identifier applierWorld,
                                                     long generation, int amplifier, int duration) {
        return new BleedAttributionCarrier(KIND_ATTRIBUTED, applierUuid, applierWorld,
                generation, amplifier, duration);
    }

    public static BleedAttributionCarrier ownerless(long generation, int amplifier, int duration) {
        return new BleedAttributionCarrier(KIND_OWNERLESS, null, null, generation, amplifier, duration);
    }

    public boolean isAttributed() {
        return KIND_ATTRIBUTED.equals(this.kind);
    }

    @Nullable
    public UUID applierUuid() {
        return this.applierUuid;
    }

    public long generation() {
        return this.generation;
    }

    public int amplifier() {
        return this.amplifier;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NBT_KIND, this.kind);
        if (isAttributed()) {
            nbt.putUuid(NBT_APPLIER_UUID, this.applierUuid);
            nbt.putString(NBT_APPLIER_WORLD, this.applierWorld.toString());
        }
        nbt.putLong(NBT_GENERATION, this.generation);
        nbt.putInt(NBT_AMPLIFIER, this.amplifier);
        nbt.putInt(NBT_DURATION, this.duration);
        return nbt;
    }

    /**
     * @return 解析结果；malformed 返回 null（warn 一次由调用方决定清理）
     */
    @Nullable
    public static BleedAttributionCarrier fromNbt(NbtCompound nbt) {
        String kind = nbt.getString(NBT_KIND);
        long generation = nbt.getLong(NBT_GENERATION);
        int amplifier = nbt.getInt(NBT_AMPLIFIER);
        int duration = nbt.getInt(NBT_DURATION);
        if (generation < 0 || amplifier < 0 || duration <= 0) {
            return null;
        }
        if (KIND_OWNERLESS.equals(kind)) {
            return ownerless(generation, amplifier, duration);
        }
        if (KIND_ATTRIBUTED.equals(kind)) {
            if (!nbt.containsUuid(NBT_APPLIER_UUID)) {
                return null;
            }
            Identifier world = Identifier.tryParse(nbt.getString(NBT_APPLIER_WORLD));
            if (world == null) {
                return null;
            }
            return attributed(nbt.getUuid(NBT_APPLIER_UUID), world, generation, amplifier, duration);
        }
        return null;
    }

    /**
     * 解析 attributed applier。applier 所在维度未加载、实体未加载或已
     * 死亡时返回 null —— 调用方（pulse）必须跳过本次脉冲，不得降级为
     * ownerless damage。
     */
    @Nullable
    public static LivingEntity resolveApplier(MinecraftServer server, BleedAttributionCarrier carrier) {
        if (!carrier.isAttributed()) {
            return null;
        }
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, carrier.applierWorld));
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(carrier.applierUuid);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ========== 生命周期耦合 ==========

    /**
     * victim 移除路径（{@code BleedingStatusEffect.onEntityRemoval}）。
     * KILLED/DISCARDED：清 carrier 字段 + generation-matched 清 index；
     * CHANGED_DIMENSION：只清旧 world key 的 index entry（carrier NBT 已
     * 复制到新实例，由脉冲 {@link #healIndexEntry} 在新维度重建 entry）。
     */
    public static void clearOnRemoval(ServerWorld world, LivingEntity victim, Entity.RemovalReason reason) {
        AdmissionIndex index = AdmissionIndex.get(world.getServer());
        String key = victimKey(world, victim.getUuid());
        BleedAttributionAccess access = (BleedAttributionAccess) victim;
        NbtCompound nbt = access.xqanzd_moonlit_broker$getBleedAttribution();
        BleedAttributionCarrier carrier = nbt != null ? fromNbt(nbt) : null;
        if (reason.shouldDestroy()) {
            access.xqanzd_moonlit_broker$clearBleedAttribution();
        }
        if (carrier != null) {
            index.removeIfGeneration(key, carrier.generation);
        } else {
            // 载体缺失/损坏而 entry 尚存 = 定义性 stale，无条件清除
            if (index.removeEntry(key)) {
                LOGGER.warn("[CombatCondition] Bleed admission entry cleared without readable carrier: {}", key);
            }
        }
    }

    /**
     * 脉冲侧自愈：权威载体为 ATTRIBUTED 而 index 无匹配 entry（典型：
     * 跨维度 key 迁移）时按载体 generation 重建。容量内 upsert；满载时
     * 保留载体权威、不 eviction，warn 一次。
     */
    public static void healIndexEntry(ServerWorld world, LivingEntity victim, BleedAttributionCarrier carrier) {
        if (!carrier.isAttributed()) {
            return;
        }
        AdmissionIndex index = AdmissionIndex.get(world.getServer());
        String key = victimKey(world, victim.getUuid());
        Long entryGeneration = index.generationOf(key);
        if (entryGeneration != null && entryGeneration == carrier.generation) {
            return;
        }
        if (entryGeneration == null && index.size() >= CombatConditionConfig.BLEED_TRACKER_CAP) {
            if (!healCapacityWarned) {
                healCapacityWarned = true;
                LOGGER.warn("[CombatCondition] Bleed admission index full during entry heal; "
                        + "carrier stays authoritative without index entry (victim {})", key);
            }
            return;
        }
        index.put(key, carrier.generation);
    }

    /**
     * 每 server tick 的有界 reconciliation（{@code CombatConditionInit}
     * 注册于 END_SERVER_TICK）。仅对**已加载**的 victim 判定 stale：
     * 效果缺失、载体缺失/损坏、kind 非 ATTRIBUTED 或 generation 不匹配
     * 即清除 entry（generation 不匹配额外 warn，不沿用旧 attacker）。
     * 未加载实体不可证伪，保留（unload 语义）。
     */
    public static void reconcileTick(MinecraftServer server) {
        AdmissionIndex.get(server).reconcile(server, RECONCILE_PER_TICK);
    }

    /** admission 满载路径的一次性全量 stale 清理（上界 = cap）。 */
    public static void reconcileAllLoaded(MinecraftServer server) {
        AdmissionIndex index = AdmissionIndex.get(server);
        index.reconcile(server, index.size());
    }

    public static String victimKey(ServerWorld world, UUID victimUuid) {
        return world.getRegistryKey().getValue() + "|" + victimUuid;
    }

    // ========== standalone persistent admission index ==========

    /**
     * attributed 出血受害者容量记账。持久宿主 = overworld
     * PersistentStateManager（与 {@code KatanaOwnershipState} 同 pattern，
     * 独立 DATA_NAME，不读写 MerchantUnlockState）。确定性写出：
     * TreeMap 词法序；load 过滤 malformed 并按词法序截断至 cap（warn 一次）。
     */
    public static final class AdmissionIndex extends PersistentState {

        private static final String DATA_NAME = "xqanzd_moonlit_broker_bleed_attribution";
        private static final String NBT_ENTRIES = "Entries";
        private static final String NBT_ENTRY_KEY = "Victim";
        private static final String NBT_ENTRY_GENERATION = "Generation";
        private static final String NBT_NEXT_GENERATION = "NextGeneration";

        private static final Type<AdmissionIndex> TYPE = new Type<>(
                AdmissionIndex::new,
                AdmissionIndex::fromNbt,
                null
        );

        private final TreeMap<String, Long> entries = new TreeMap<>();
        private long nextGeneration = 0L;
        /** reconciliation 游标（transient，不持久化）。 */
        @Nullable
        private String reconcileCursor = null;

        public static AdmissionIndex get(MinecraftServer server) {
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            assert overworld != null;
            return overworld.getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
        }

        private static AdmissionIndex fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            AdmissionIndex index = new AdmissionIndex();
            long next = nbt.getLong(NBT_NEXT_GENERATION);
            index.nextGeneration = Math.max(next, 0L);
            NbtList list = nbt.getList(NBT_ENTRIES, NbtElement.COMPOUND_TYPE);
            int dropped = 0;
            for (int i = 0; i < list.size(); i++) {
                NbtCompound entry = list.getCompound(i);
                String key = entry.getString(NBT_ENTRY_KEY);
                long generation = entry.getLong(NBT_ENTRY_GENERATION);
                if (key.indexOf('|') <= 0 || generation < 0) {
                    dropped++;
                    continue;
                }
                if (index.entries.size() >= CombatConditionConfig.BLEED_TRACKER_CAP) {
                    dropped++;
                    continue;
                }
                index.entries.put(key, generation);
            }
            if (dropped > 0) {
                LOGGER.warn("[CombatCondition] Bleed admission index load dropped {} malformed/over-cap entries", dropped);
            }
            return index;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            nbt.putLong(NBT_NEXT_GENERATION, this.nextGeneration);
            NbtList list = new NbtList();
            for (Map.Entry<String, Long> entry : this.entries.entrySet()) {
                NbtCompound e = new NbtCompound();
                e.putString(NBT_ENTRY_KEY, entry.getKey());
                e.putLong(NBT_ENTRY_GENERATION, entry.getValue());
                list.add(e);
            }
            nbt.put(NBT_ENTRIES, list);
            return nbt;
        }

        public int size() {
            return this.entries.size();
        }

        public boolean hasEntry(String key) {
            return this.entries.containsKey(key);
        }

        @Nullable
        public Long generationOf(String key) {
            return this.entries.get(key);
        }

        public long nextGeneration() {
            long generation = this.nextGeneration++;
            markDirty();
            return generation;
        }

        public void put(String key, long generation) {
            this.entries.put(key, generation);
            markDirty();
        }

        public boolean removeEntry(String key) {
            if (this.entries.remove(key) != null) {
                markDirty();
                return true;
            }
            return false;
        }

        public boolean removeIfGeneration(String key, long generation) {
            Long current = this.entries.get(key);
            if (current != null && current == generation) {
                this.entries.remove(key);
                markDirty();
                return true;
            }
            return false;
        }

        /** 从游标起最多复核 {@code budget} 条 entry；见 owner 类 javadoc。 */
        private void reconcile(MinecraftServer server, int budget) {
            for (int i = 0; i < budget && !this.entries.isEmpty(); i++) {
                String key = this.reconcileCursor == null
                        ? this.entries.firstKey()
                        : this.entries.higherKey(this.reconcileCursor);
                if (key == null) {
                    this.reconcileCursor = null;
                    key = this.entries.firstKey();
                }
                this.reconcileCursor = key;
                reconcileEntry(server, key, this.entries.get(key));
            }
        }

        private void reconcileEntry(MinecraftServer server, String key, long entryGeneration) {
            int separator = key.indexOf('|');
            Identifier worldId = Identifier.tryParse(key.substring(0, separator));
            UUID victimUuid;
            try {
                victimUuid = UUID.fromString(key.substring(separator + 1));
            } catch (IllegalArgumentException e) {
                victimUuid = null;
            }
            if (worldId == null || victimUuid == null) {
                removeEntry(key);
                LOGGER.warn("[CombatCondition] Bleed admission entry with unparseable key removed: {}", key);
                return;
            }
            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldId));
            if (world == null) {
                return; // 维度未加载，不可证伪
            }
            Entity entity = world.getEntity(victimUuid);
            if (!(entity instanceof LivingEntity victim)) {
                return; // 实体未加载（chunk 卸载等），不可证伪；销毁路径由 onEntityRemoval 同步清理
            }
            NbtCompound nbt = ((BleedAttributionAccess) victim).xqanzd_moonlit_broker$getBleedAttribution();
            BleedAttributionCarrier carrier = nbt != null ? BleedAttributionCarrier.fromNbt(nbt) : null;
            boolean effectPresent = victim.hasStatusEffect(ModStatusEffects.BLEEDING);
            if (effectPresent && carrier != null && carrier.isAttributed()
                    && carrier.generation == entryGeneration) {
                return; // 一致
            }
            removeEntry(key);
            if (carrier != null && carrier.generation != entryGeneration) {
                LOGGER.warn("[CombatCondition] Bleed admission entry generation mismatch cleared "
                        + "(entry {}, carrier {}): {}", entryGeneration, carrier.generation, key);
            }
        }
    }
}
