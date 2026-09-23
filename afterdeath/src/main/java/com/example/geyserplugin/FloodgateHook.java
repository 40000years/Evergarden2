package com.example.geyserplugin;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.UUID;

/**
 * แยกการเรียกใช้งาน Floodgate API ออกมาไว้ในคลาสนี้
 * เพื่อป้องกัน NoClassDefFoundError กรณีที่เซิร์ฟเวอร์ไม่ได้ติดตั้ง Floodgate (softdepend)
 */
public final class FloodgateHook {

    private FloodgateHook() {}

    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    public static String getDeviceOs(UUID uuid) {
        if (uuid == null) {
            return "UNKNOWN";
        }
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(uuid);
        return (fp != null && fp.getDeviceOs() != null) ? fp.getDeviceOs().name() : "UNKNOWN";
    }
}
