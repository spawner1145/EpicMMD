package com.shiroha.epicmmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.render.ItemRenderHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

final class EpicFightItemRenderBridge implements ItemRenderHelper.ExternalItemRenderer {
    private static final EpicFightItemRenderBridge INSTANCE = new EpicFightItemRenderBridge();
    private static final OpenMatrix4f MMD_ITEM_BASIS = new OpenMatrix4f()
            .rotateDeg(90.0F, Vec3f.X_AXIS)
            .rotateDeg(180.0F, Vec3f.Y_AXIS)
            .unmodifiable();
    private static final OpenMatrix4f EPIC_DEFAULT_TOOL_ITEM_CORRECTION = new OpenMatrix4f()
            .translate(0.0F, 0.0F, -0.13F)
            .rotateDeg(-90.0F, Vec3f.X_AXIS)
            .unmodifiable();

    private EpicFightItemRenderBridge() {
    }

    static void register() {
        ItemRenderHelper.setExternalItemRenderer(INSTANCE);
    }

    @Override
    public boolean renderItems(AbstractClientPlayer player, ManagedModel model, PoseStack matrixStack,
                               MultiBufferSource vertexConsumers, int packedLight, float tickDelta,
                               float modelScale, float heldItemScale) {
        return renderItemsWithEpicFight(player, model, matrixStack, vertexConsumers, packedLight, tickDelta,
                heldItemScale);
    }

    public boolean renderItems(AbstractClientPlayer player, ManagedModel model, PoseStack matrixStack,
                               MultiBufferSource vertexConsumers, int packedLight, float tickDelta,
                               float modelScale) {
        return false;
    }

    private static boolean renderItemsWithEpicFight(AbstractClientPlayer player, ManagedModel model,
                                                    PoseStack matrixStack,
                                                    MultiBufferSource vertexConsumers, int packedLight,
                                                    float tickDelta, float heldItemScale) {
        PlayerPatch<?> patch = EpicFightCapabilities.getPlayerPatch(player);
        if (patch == null || !patch.isEpicFightMode()) {
            return false;
        }

        RenderEngine renderEngine = ClientEngine.getInstance().renderEngine;
        float itemScale = Float.isFinite(heldItemScale) && heldItemScale > 0.0F ? heldItemScale : 1.0F;
        float renderScale = 10.0F * itemScale;
        OpenMatrix4f[] poses = mmdAnchoredPose(patch, model, currentEpicPose(patch, tickDelta), renderScale);
        boolean rendered = false;

        matrixStack.pushPose();
        try {
            matrixStack.scale(renderScale, renderScale, renderScale);

            ItemStack mainHandStack = player.getMainHandItem();
            if (!mainHandStack.isEmpty() && mainHandStack.getItem() != Items.AIR) {
                renderEngine.getItemRenderer(mainHandStack).renderItemInHand(mainHandStack, patch,
                        InteractionHand.MAIN_HAND, poses, vertexConsumers, matrixStack, packedLight, tickDelta);
                rendered = true;
            }

            ItemStack offHandStack = player.getOffhandItem();
            if (patch.isOffhandItemValid() && !offHandStack.isEmpty() && offHandStack.getItem() != Items.AIR) {
                renderEngine.getItemRenderer(offHandStack).renderItemInHand(offHandStack, patch,
                        InteractionHand.OFF_HAND, poses, vertexConsumers, matrixStack, packedLight, tickDelta);
                rendered = true;
            }
        } finally {
            matrixStack.popPose();
        }

        return rendered;
    }

    private static OpenMatrix4f[] currentEpicPose(PlayerPatch<?> patch, float tickDelta) {
        Pose pose = patch.getClientAnimator().getPose(tickDelta);
        if (pose == null) {
            return identityPose(patch);
        }
        return patch.getArmature().getPoseAsTransformMatrix(pose, false);
    }

    private static OpenMatrix4f[] mmdAnchoredPose(PlayerPatch<?> patch, ManagedModel model,
                                                  OpenMatrix4f[] epicPose, float renderScale) {
        OpenMatrix4f[] pose = copyPose(patch, epicPose);
        replaceToolAnchor(patch, model, pose, epicPose, InteractionHand.MAIN_HAND, "Hand_R", "Tool_R", renderScale);
        replaceToolAnchor(patch, model, pose, epicPose, InteractionHand.OFF_HAND, "Hand_L", "Tool_L", renderScale);
        return pose;
    }

    private static OpenMatrix4f[] copyPose(PlayerPatch<?> patch, OpenMatrix4f[] epicPose) {
        int jointCount = patch.getArmature().getJointNumber();
        OpenMatrix4f[] pose = new OpenMatrix4f[jointCount];
        for (int i = 0; i < jointCount; i++) {
            pose[i] = i < epicPose.length && epicPose[i] != null ? new OpenMatrix4f(epicPose[i]) : new OpenMatrix4f();
        }
        return pose;
    }

    private static void replaceToolAnchor(PlayerPatch<?> patch, ManagedModel model, OpenMatrix4f[] pose,
                                          OpenMatrix4f[] epicPose, InteractionHand hand, String handJointName,
                                          String toolJointName, float renderScale) {
        Joint handJoint = patch.getArmature().searchJointByName(handJointName);
        Joint toolJoint = patch.getArmature().searchJointByName(toolJointName);
        if (!isPoseIndexValid(handJoint, pose) || !isPoseIndexValid(toolJoint, pose)) {
            return;
        }

        OpenMatrix4f mmdHand = mmdHandAnchor(model, hand, renderScale);
        pose[handJoint.getId()] = new OpenMatrix4f(mmdHand);

        OpenMatrix4f mmdToEpicHand = mmdToEpicHandBasis(toolJoint);
        OpenMatrix4f handToTool = currentHandToToolTransform(handJoint, toolJoint, epicPose);
        pose[toolJoint.getId()] = OpenMatrix4f.mul(OpenMatrix4f.mul(mmdHand, mmdToEpicHand, null),
                handToTool, null);
    }

    private static boolean isPoseIndexValid(Joint joint, OpenMatrix4f[] pose) {
        return joint != null && joint.getId() >= 0 && joint.getId() < pose.length;
    }

    private static OpenMatrix4f mmdHandAnchor(ManagedModel model, InteractionHand hand, float renderScale) {
        Matrix4f handMatrix = ItemRenderHelper.getMmdHandMatrix(model, hand);
        OpenMatrix4f anchor = OpenMatrix4f.importFromMojangMatrix(handMatrix);
        if (Float.isFinite(renderScale) && renderScale != 0.0F) {
            anchor.m30 /= renderScale;
            anchor.m31 /= renderScale;
            anchor.m32 /= renderScale;
        }
        return anchor;
    }

    private static OpenMatrix4f currentHandToToolTransform(Joint handJoint, Joint toolJoint, OpenMatrix4f[] epicPose) {
        if (handJoint.getId() < epicPose.length && toolJoint.getId() < epicPose.length
                && epicPose[handJoint.getId()] != null && epicPose[toolJoint.getId()] != null) {
            return OpenMatrix4f.mul(OpenMatrix4f.invert(epicPose[handJoint.getId()], null),
                    epicPose[toolJoint.getId()], null);
        }
        return new OpenMatrix4f(toolJoint.getLocalTransform());
    }

    private static OpenMatrix4f mmdToEpicHandBasis(Joint toolJoint) {
        OpenMatrix4f restToolToItem = OpenMatrix4f.mul(toolJoint.getLocalTransform(),
                EPIC_DEFAULT_TOOL_ITEM_CORRECTION, null);
        return OpenMatrix4f.mul(MMD_ITEM_BASIS, OpenMatrix4f.invert(restToolToItem, null), null);
    }

    private static OpenMatrix4f[] identityPose(PlayerPatch<?> patch) {
        OpenMatrix4f[] pose = new OpenMatrix4f[patch.getArmature().getJointNumber()];
        for (int i = 0; i < pose.length; i++) {
            pose[i] = new OpenMatrix4f();
        }
        return pose;
    }
}
