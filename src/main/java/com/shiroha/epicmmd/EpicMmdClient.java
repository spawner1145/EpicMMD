package com.shiroha.epicmmd;

import net.minecraftforge.common.MinecraftForge;

final class EpicMmdClient {
    private EpicMmdClient() {
    }

    static void init() {
        EpicFightItemRenderBridge.register();
        MinecraftForge.EVENT_BUS.register(new EpicMmdPoseSync());
    }
}
