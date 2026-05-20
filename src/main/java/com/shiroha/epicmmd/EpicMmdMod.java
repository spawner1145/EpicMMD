package com.shiroha.epicmmd;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(EpicMmdMod.MOD_ID)
public final class EpicMmdMod {
    public static final String MOD_ID = "epicmmd";

    public EpicMmdMod() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> EpicMmdClient::init);
    }
}
