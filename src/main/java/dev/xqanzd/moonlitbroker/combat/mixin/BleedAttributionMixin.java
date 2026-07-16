package dev.xqanzd.moonlitbroker.combat.mixin;

import dev.xqanzd.moonlitbroker.combat.condition.bleeding.BleedAttributionAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 出血归属载体 Mixin（FC3-A1）
 * 处理：
 * - 归属载体随实体自定义 NBT 持久化（unload/reload/restart 存续）
 * - 出血效果移除/过期时立即清除载体（onStatusEffectRemoved 覆盖
 *   removeStatusEffect 与 tick 过期两条路径）
 * <p>
 * 本 Mixin 不触碰 damage/heal pipeline，不做归属仲裁；效果 ID 比较
 * 走 {@link BleedAttributionAccess#BLEEDING_EFFECT_ID}，不引用注册
 * 持有者类。
 */
@Mixin(LivingEntity.class)
public class BleedAttributionMixin implements BleedAttributionAccess {

    @Unique
    @Nullable
    private NbtCompound xqanzd_moonlit_broker$bleedAttribution;

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void combat$writeBleedAttribution(NbtCompound nbt, CallbackInfo ci) {
        if (this.xqanzd_moonlit_broker$bleedAttribution != null) {
            nbt.put(BLEED_ATTRIBUTION_NBT_KEY, this.xqanzd_moonlit_broker$bleedAttribution.copy());
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void combat$readBleedAttribution(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(BLEED_ATTRIBUTION_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            this.xqanzd_moonlit_broker$bleedAttribution = nbt.getCompound(BLEED_ATTRIBUTION_NBT_KEY).copy();
        } else {
            this.xqanzd_moonlit_broker$bleedAttribution = null;
        }
    }

    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void combat$clearBleedAttributionOnRemoval(StatusEffectInstance effect, CallbackInfo ci) {
        if (effect.getEffectType().matchesId(BLEEDING_EFFECT_ID)) {
            this.xqanzd_moonlit_broker$bleedAttribution = null;
        }
    }

    @Override
    @Nullable
    public NbtCompound xqanzd_moonlit_broker$getBleedAttribution() {
        return this.xqanzd_moonlit_broker$bleedAttribution;
    }

    @Override
    public void xqanzd_moonlit_broker$setBleedAttribution(NbtCompound carrier) {
        this.xqanzd_moonlit_broker$bleedAttribution = carrier.copy();
    }

    @Override
    public void xqanzd_moonlit_broker$clearBleedAttribution() {
        this.xqanzd_moonlit_broker$bleedAttribution = null;
    }
}
