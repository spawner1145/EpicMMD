package com.shiroha.epicmmd;

import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.api.ModelInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EpicMmdPoseSync {
    private static final Logger LOGGER = LogManager.getLogger(EpicMmdMod.MOD_ID);
    private static final JointTransform IDENTITY_TRANSFORM = JointTransform.empty();
    private static final String[] MMD_LEG_IK_BONES = {
            "右足ＩＫ", "右足IK", "左足ＩＫ", "左足IK",
            "右つま先ＩＫ", "右つま先IK", "左つま先ＩＫ", "左つま先IK"
    };
    private static final String[] RIGHT_LEG_IK_BONES = {"右足ＩＫ", "右足IK"};
    private static final String[] LEFT_LEG_IK_BONES = {"左足ＩＫ", "左足IK"};
    private static final String[] RIGHT_TOE_IK_BONES = {"右つま先ＩＫ", "右つま先IK"};
    private static final String[] LEFT_TOE_IK_BONES = {"左つま先ＩＫ", "左つま先IK"};
    private static final String[] EPIC_JOINT_NAMES = {
            "Root", "Coord",
            "Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L",
            "Torso", "Chest", "Head",
            "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R",
            "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L",
            "IK_right", "IK_left"
    };
    private static final Map<String, String> EPIC_PARENT_BY_JOINT = Map.ofEntries(
            Map.entry("Thigh_R", "Root"),
            Map.entry("Leg_R", "Thigh_R"),
            Map.entry("Knee_R", "Thigh_R"),
            Map.entry("Thigh_L", "Root"),
            Map.entry("Leg_L", "Thigh_L"),
            Map.entry("Knee_L", "Thigh_L"),
            Map.entry("Torso", "Root"),
            Map.entry("Chest", "Torso"),
            Map.entry("Head", "Chest"),
            Map.entry("Shoulder_R", "Chest"),
            Map.entry("Arm_R", "Shoulder_R"),
            Map.entry("Hand_R", "Arm_R"),
            Map.entry("Tool_R", "Hand_R"),
            Map.entry("Elbow_R", "Arm_R"),
            Map.entry("Shoulder_L", "Chest"),
            Map.entry("Arm_L", "Shoulder_L"),
            Map.entry("Hand_L", "Arm_L"),
            Map.entry("Tool_L", "Hand_L"),
            Map.entry("Elbow_L", "Arm_L")
    );

    private static final BoneRule[] DEFAULT_BONE_RULES = {
            animated("Root", "センター", "Center", "center", "全ての親", "Root", "root"),
            animatedOptional("Coord", "グルーブ", "Groove"),
            animated("Torso", "下半身", "LowerBody", "lower body"),
            animatedWeighted("Torso", 0.65f, "腰", "hips", "pelvis"),
            animated("Chest", "上半身", "UpperBody", "upper body", "body", "spine"),
            animatedWeighted("Chest", 0.55f, "上半身2", "UpperBody2"),
            animatedWeighted("Chest", 0.30f, "上半身３", "上半身3", "UpperBody3"),
            animatedWeighted("Chest", 0.45f, "首", "Neck", "neck"),

            animated("Thigh_R", "右足", "RightLeg", "leg_r", "right leg", "right thigh"),
            animated("Leg_R", "右ひざ", "RightKnee", "knee_r", "right knee", "right lower leg"),
            animated("Knee_R", "右足首", "RightAnkle", "ankle_r", "right ankle", "right foot"),
            animated("Thigh_L", "左足", "LeftLeg", "leg_l", "left leg", "left thigh"),
            animated("Leg_L", "左ひざ", "LeftKnee", "knee_l", "left knee", "left lower leg"),
            animated("Knee_L", "左足首", "LeftAnkle", "ankle_l", "left ankle", "left foot"),
            animatedOptional("IK_right", "右足ＩＫ", "右足IK", "RightLegIK", "right leg ik"),
            animatedOptional("IK_left", "左足ＩＫ", "左足IK", "LeftLegIK", "left leg ik"),

            neutral("全ての親", "RootParent", "ParentNode"),
            neutral("グルーブ", "Groove"),
            neutral("下半身", "LowerBody", "lower body"),
            neutral("右つま先"),
            neutral("左つま先"),
            neutral("右足D", "右ひざD", "右足首D", "右足先EX"),
            neutral("左足D", "左ひざD", "左足首D", "左足先EX"),
            neutral("右目", "左目")
    };

    private final Map<Integer, ResolvedModel> resolvedModels = new HashMap<>();
    private final Set<Integer> loggedModelKeys = new HashSet<>();
    private boolean wasDrivingLocalPlayer;
    private boolean loggedPoseKeys;

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        driveLocalPlayer(event.getPartialTick());
    }

    private void driveLocalPlayer(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        LocalPlayerPatch patch = EpicFightCapabilities.getLocalPlayerPatch(player);
        if (patch == null || !patch.isEpicFightMode()) {
            clearIfNeeded(player);
            return;
        }

        Pose pose = patch.getClientAnimator().getPose(partialTick);
        if (pose == null) {
            clearIfNeeded(player);
            return;
        }

        ResolvedModel model = resolveModel(player);
        if (model == null || model.boneIndices.length == 0) {
            clearIfNeeded(player);
            return;
        }
        Map<String, JointTransform> epicTransforms = resolveEpicTransforms(pose, patch.getArmature());

        if (!loggedPoseKeys) {
            loggedPoseKeys = true;
            LOGGER.info("EpicFight pose joints: {}", pose.getJointTransformData().keySet());
        }

        Arrays.fill(model.activeBoneIndices, -1);
        int activeCount = 0;
        for (int i = 0; i < model.rules.length; i++) {
            BoneRule rule = model.rules[i];
            JointTransform transform = resolveTransform(pose, epicTransforms, rule);
            if (transform == null) {
                continue;
            }
            model.activeBoneIndices[activeCount] = model.boneIndices[i];
            writeTransform(model.activeTransforms, activeCount, transform, rule);
            activeCount++;
        }

        MmdSkinApi.clearBoneOverrides(player);
        if (activeCount == 0) {
            MmdSkinApi.clearExternalIkOverrides(player);
            wasDrivingLocalPlayer = false;
            return;
        }

        configureMmdLegIk(player, pose);
        int applied = MmdSkinApi.setBoneOverrideBatch(player, model.activeBoneIndices, model.activeTransforms);
        if (applied > 0) {
            wasDrivingLocalPlayer = true;
        } else {
            clearIfNeeded(player);
        }
    }

    private static JointTransform resolveTransform(Pose pose, Map<String, JointTransform> epicTransforms, BoneRule rule) {
        if (rule.neutralOnly()) {
            return IDENTITY_TRANSFORM;
        }
        JointTransform transform = epicTransforms.get(rule.epicJointName());
        if (transform != null) {
            return transform;
        }
        transform = pose.get(rule.epicJointName());
        if (transform != null) {
            return transform;
        }
        return rule.optionalWhenMissing() ? null : IDENTITY_TRANSFORM;
    }

    private static Map<String, JointTransform> resolveEpicTransforms(Pose pose, Armature armature) {
        Map<String, JointTransform> transforms = new HashMap<>();
        if (armature == null) {
            return transforms;
        }

        OpenMatrix4f[] poseMatrices = armature.getPoseAsTransformMatrix(pose, false);
        Map<String, OpenMatrix4f> restGlobalMatrices = new HashMap<>();
        for (String jointName : EPIC_JOINT_NAMES) {
            Joint joint = armature.searchJointByName(jointName);
            if (joint == null || joint.getId() < 0 || joint.getId() >= poseMatrices.length) {
                continue;
            }

            JointTransform transform = shouldUseRawEpicAnimationTransform(jointName)
                    ? copyPoseAnimationTransform(pose, jointName)
                    : null;
            if (transform == null) {
                OpenMatrix4f currentGlobal = poseMatrices[joint.getId()];
                if (currentGlobal == null) {
                    continue;
                }

                OpenMatrix4f currentLocal = toCurrentLocalMatrix(armature, poseMatrices, jointName, currentGlobal);
                OpenMatrix4f restLocal = joint.getLocalTransform();
                OpenMatrix4f localDelta = OpenMatrix4f.mul(OpenMatrix4f.invert(restLocal, null), currentLocal, null);
                transform = JointTransform.fromMatrixWithoutScale(localDelta);
            }
            OpenMatrix4f restGlobal = toRestGlobalMatrix(armature, jointName, restGlobalMatrices);
            Quaternionf rotation = jointName.equals("Root")
                    ? toMmdRootRotationSpace(transform.rotation(), restGlobal)
                    : shouldUseDirectMmdAnimationSpace(jointName)
                    ? new Quaternionf(transform.rotation()).normalize()
                    : toMmdRotationSpace(transform.rotation(), restGlobal);
            transform.rotation().set(rotation).normalize();
            transforms.put(jointName, transform);
        }

        return transforms;
    }

    private static JointTransform copyPoseAnimationTransform(Pose pose, String jointName) {
        JointTransform transform = pose.get(jointName);
        return transform == null ? null : transform.copy();
    }

    private static OpenMatrix4f toCurrentLocalMatrix(Armature armature,
                                                     OpenMatrix4f[] poseMatrices,
                                                     String jointName,
                                                     OpenMatrix4f currentGlobal) {
        String parentName = EPIC_PARENT_BY_JOINT.get(jointName);
        if (parentName == null) {
            return new OpenMatrix4f(currentGlobal);
        }

        Joint parent = armature.searchJointByName(parentName);
        if (parent == null || parent.getId() < 0 || parent.getId() >= poseMatrices.length) {
            return new OpenMatrix4f(currentGlobal);
        }

        OpenMatrix4f parentGlobal = poseMatrices[parent.getId()];
        if (parentGlobal == null) {
            return new OpenMatrix4f(currentGlobal);
        }

        return OpenMatrix4f.mul(OpenMatrix4f.invert(parentGlobal, null), currentGlobal, null);
    }

    private static OpenMatrix4f toRestGlobalMatrix(Armature armature,
                                                   String jointName,
                                                   Map<String, OpenMatrix4f> cache) {
        OpenMatrix4f cached = cache.get(jointName);
        if (cached != null) {
            return cached;
        }

        Joint joint = armature.searchJointByName(jointName);
        if (joint == null) {
            OpenMatrix4f identity = new OpenMatrix4f();
            cache.put(jointName, identity);
            return identity;
        }

        String parentName = EPIC_PARENT_BY_JOINT.get(jointName);
        OpenMatrix4f restLocal = joint.getLocalTransform();
        OpenMatrix4f restGlobal = parentName == null
                ? new OpenMatrix4f(restLocal)
                : OpenMatrix4f.mul(toRestGlobalMatrix(armature, parentName, cache), restLocal, null);
        cache.put(jointName, restGlobal);
        return restGlobal;
    }

    private static Quaternionf toMmdRotationSpace(Quaternionf epicLocalRotation, OpenMatrix4f epicRestGlobal) {
        Quaternionf basis = epicRestGlobal.toQuaternion().normalize();
        Quaternionf inverseBasis = new Quaternionf(basis).conjugate();
        return basis.mul(new Quaternionf(epicLocalRotation), new Quaternionf()).mul(inverseBasis).normalize();
    }

    private static Quaternionf toMmdRootRotationSpace(Quaternionf epicLocalRotation, OpenMatrix4f epicRestGlobal) {
        Quaternionf rotation = toMmdRotationSpace(epicLocalRotation, epicRestGlobal);
        return rotation.set(rotation.x(), -rotation.y(), rotation.z(), rotation.w()).normalize();
    }

    private static boolean shouldUseRawEpicAnimationTransform(String jointName) {
        return false;
    }

    private static boolean shouldUseDirectMmdAnimationSpace(String jointName) {
        return jointName.equals("Torso")
                || jointName.equals("Chest");
    }

    private void clearIfNeeded(LocalPlayer player) {
        if (wasDrivingLocalPlayer) {
            MmdSkinApi.clearBoneOverrides(player);
            MmdSkinApi.clearExternalIkOverrides(player);
            wasDrivingLocalPlayer = false;
        }
    }

    private static void configureMmdLegIk(LocalPlayer player, Pose pose) {
        boolean rightIk = pose.hasTransform("IK_right");
        boolean leftIk = pose.hasTransform("IK_left");
        setIkOverrides(player, RIGHT_LEG_IK_BONES, rightIk);
        setIkOverrides(player, LEFT_LEG_IK_BONES, leftIk);
        setIkOverrides(player, RIGHT_TOE_IK_BONES, false);
        setIkOverrides(player, LEFT_TOE_IK_BONES, false);
    }

    private static void setIkOverrides(LocalPlayer player, String[] ikBones, boolean enabled) {
        for (String ikBone : ikBones) {
            MmdSkinApi.setExternalIkOverride(player, ikBone, enabled);
        }
    }

    private static void disableMmdLegIk(LocalPlayer player) {
        for (String ikBone : MMD_LEG_IK_BONES) {
            MmdSkinApi.setExternalIkOverride(player, ikBone, false);
        }
    }

    private ResolvedModel resolveModel(LocalPlayer player) {
        ModelInfo modelInfo = MmdSkinApi.getModelInfo(player);
        if (modelInfo == null || modelInfo.getBoneNames() == null || modelInfo.getBoneNames().isEmpty()) {
            return null;
        }

        int key = modelInfo.getBoneNames().hashCode();
        ResolvedModel cached = resolvedModels.get(key);
        if (cached != null) {
            return cached;
        }

        Map<String, Integer> boneIndexByNormalizedName = new HashMap<>();
        List<String> boneNames = modelInfo.getBoneNames();
        for (int i = 0; i < boneNames.size(); i++) {
            boneIndexByNormalizedName.put(normalize(boneNames.get(i)), i);
        }

        List<BoneRule> rules = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (BoneRule rule : DEFAULT_BONE_RULES) {
            boolean matchedRule = false;
            if (rule.neutralOnly()) {
                for (String alias : rule.mmdAliases()) {
                    matchedRule |= addResolvedRule(rule, alias, boneIndexByNormalizedName, rules, indices, matched);
                }
            } else {
                for (String alias : rule.mmdAliases()) {
                    if (addResolvedRule(rule, alias, boneIndexByNormalizedName, rules, indices, matched)) {
                        matchedRule = true;
                        break;
                    }
                }
            }

            if (!rule.neutralOnly() && !matchedRule) {
                missing.add(rule.epicJointName());
            }
        }

        int[] boneIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            boneIndices[i] = indices.get(i);
        }

        ResolvedModel model = new ResolvedModel(
                rules.toArray(BoneRule[]::new),
                boneIndices,
                new int[boneIndices.length],
                new float[boneIndices.length * 7]);
        resolvedModels.put(key, model);

        if (loggedModelKeys.add(key)) {
            LOGGER.info("Resolved {} MMD bones for EpicMMD: {}", boneIndices.length, matched);
            if (!missing.isEmpty()) {
                LOGGER.warn("Missing required EpicMMD bone mappings for EpicFight joints: {}", missing);
            }
            LOGGER.debug("MMD model bones: {}", boneNames);
        }

        return model;
    }

    private static boolean addResolvedRule(BoneRule rule,
                                           String alias,
                                           Map<String, Integer> boneIndexByNormalizedName,
                                           List<BoneRule> rules,
                                           List<Integer> indices,
                                           List<String> matched) {
        Integer boneIndex = boneIndexByNormalizedName.get(normalize(alias));
        if (boneIndex == null || indices.contains(boneIndex)) {
            return false;
        }
        rules.add(rule.withSingleAlias(alias));
        indices.add(boneIndex);
        matched.add(rule.displayName(alias));
        return true;
    }

    private static void writeTransform(float[] transforms, int transformIndex, JointTransform transform, BoneRule rule) {
        int offset = transformIndex * 7;
        float clampedWeight = Math.max(0.0F, Math.min(1.0F, rule.weight()));
        Quaternionf rotation = clampedWeight >= 0.999F
                ? new Quaternionf(transform.rotation())
                : new Quaternionf().slerp(transform.rotation(), clampedWeight);
        float translationWeight = shouldWriteTranslation(rule.epicJointName()) ? clampedWeight : 0.0F;
        transforms[offset] = transform.translation().x * translationWeight;
        transforms[offset + 1] = transform.translation().y * translationWeight;
        transforms[offset + 2] = transform.translation().z * translationWeight;
        transforms[offset + 3] = rotation.x();
        transforms[offset + 4] = rotation.y();
        transforms[offset + 5] = rotation.z();
        transforms[offset + 6] = rotation.w();
    }

    private static boolean shouldWriteTranslation(String jointName) {
        return jointName.equals("Root")
                || jointName.equals("Coord")
                || jointName.equals("IK_right")
                || jointName.equals("IK_left");
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim()
                .replace("_", "")
                .replace(" ", "")
                .replace("　", "")
                .toLowerCase(Locale.ROOT);
    }

    private static BoneRule animated(String epicJointName, String... mmdAliases) {
        return animatedWeighted(epicJointName, 1.0F, mmdAliases);
    }

    private static BoneRule animatedWeighted(String epicJointName, float weight, String... mmdAliases) {
        return new BoneRule(epicJointName, false, false, weight, mmdAliases);
    }

    private static BoneRule animatedOptional(String epicJointName, String... mmdAliases) {
        return animatedOptionalWeighted(epicJointName, 1.0F, mmdAliases);
    }

    private static BoneRule animatedOptionalWeighted(String epicJointName, float weight, String... mmdAliases) {
        return new BoneRule(epicJointName, false, true, weight, mmdAliases);
    }

    private static BoneRule neutral(String... mmdAliases) {
        return new BoneRule("", true, false, 1.0F, mmdAliases);
    }

    private record BoneRule(String epicJointName, boolean neutralOnly, boolean optionalWhenMissing, float weight,
                            String... mmdAliases) {
        BoneRule withSingleAlias(String alias) {
            return new BoneRule(this.epicJointName, this.neutralOnly, this.optionalWhenMissing, this.weight, alias);
        }

        String displayName(String alias) {
            return this.neutralOnly ? alias + "=identity" : this.epicJointName + "->" + alias;
        }
    }

    private record ResolvedModel(BoneRule[] rules, int[] boneIndices, int[] activeBoneIndices, float[] activeTransforms) {
    }
}
