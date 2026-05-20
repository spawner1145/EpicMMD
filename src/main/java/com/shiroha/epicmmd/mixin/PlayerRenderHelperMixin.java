package com.shiroha.epicmmd.mixin;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.render.PlayerRenderHelper;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

@Mixin(value = PlayerRenderHelper.class, remap = false)
abstract class PlayerRenderHelperMixin {
    private static final Logger EPICMMD_LOGGER = LogManager.getLogger("epicmmd");
    private static boolean epicmmd$loggedYawBridge;

    @Inject(method = "calculateMutableRenderPose", at = @At("RETURN"), remap = false)
    private static void epicmmd$useEpicFightModelYaw(AbstractClientPlayer player,
                                                     ManagedModel modelData,
                                                     float tickDelta,
                                                     CallbackInfoReturnable<MutableRenderPose> cir) {
        PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
        if (patch == null || !patch.isEpicFightMode()) {
            return;
        }

        MutableRenderPose pose = cir.getReturnValue();
        if (pose != null) {
            pose.bodyYaw = Mth.rotLerp(tickDelta, patch.getYRotO(), patch.getYRot());
            if (!epicmmd$loggedYawBridge) {
                epicmmd$loggedYawBridge = true;
                EPICMMD_LOGGER.info("EpicMMD using Epic Fight model yaw for MMD render pose");
            }
        }
    }
}
