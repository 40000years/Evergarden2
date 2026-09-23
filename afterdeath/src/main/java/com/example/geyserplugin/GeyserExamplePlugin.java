package com.example.geyserplugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class GeyserExamplePlugin extends JavaPlugin {

    private static GeyserExamplePlugin instance;
    private boolean floodgateAvailable = false;

    @Override
    public void onEnable() {
        instance = this;

        // ตรวจว่า Floodgate ถูกโหลดและเปิดใช้งาน (enabled) อยู่หรือไม่
        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            floodgateAvailable = true;
            getLogger().info("ตรวจพบ Floodgate — เปิดใช้งานฟีเจอร์สำหรับผู้เล่น Bedrock");
        } else {
            getLogger().warning("ไม่พบ Floodgate — plugin จะทำงานเฉพาะฟีเจอร์ฝั่ง Java เท่านั้น");
        }

        // ลงทะเบียน command พร้อมตรวจสอบ Null safety
        PluginCommand platformCommand = getCommand("platform");
        if (platformCommand != null) {
            platformCommand.setExecutor(new PlatformCommand());
        } else {
            getLogger().warning("ไม่พบคำสั่ง platform ใน plugin.yml");
        }

        // ลงทะเบียน Death Clock (นาฬิกาย้อนเวลาจุดเสียชีวิต)
        DeathClockListener deathClockListener = new DeathClockListener(this);
        getServer().getPluginManager().registerEvents(deathClockListener, this);

        PluginCommand deathClockCommand = getCommand("deathclock");
        if (deathClockCommand != null) {
            deathClockCommand.setExecutor(new DeathClockCommand(deathClockListener));
        }

        // ลงทะเบียน event listener อื่นๆ
        getServer().getPluginManager().registerEvents(new BedrockJoinListener(), this);

        getLogger().info("GeyserExamplePlugin เปิดใช้งานเรียบร้อย");
    }

    @Override
    public void onDisable() {
        getLogger().info("GeyserExamplePlugin ปิดการทำงาน");
        instance = null;
    }

    public static GeyserExamplePlugin getInstance() {
        return instance;
    }

    public boolean isFloodgateAvailable() {
        return floodgateAvailable;
    }

    /**
     * ตรวจสอบว่า UUID นี้เป็นผู้เล่น Bedrock (เชื่อมผ่าน Geyser) หรือไม่
     * ปลอดภัยจาก NoClassDefFoundError หากไม่ได้ติดตั้ง Floodgate
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (!floodgateAvailable || uuid == null) {
            return false;
        }
        return FloodgateHook.isBedrockPlayer(uuid);
    }

    /**
     * ดึงข้อมูลประเภทอุปกรณ์ (Device OS) ของผู้เล่น Bedrock
     */
    public String getBedrockDeviceOs(UUID uuid) {
        if (!floodgateAvailable || uuid == null) {
            return "UNKNOWN";
        }
        return FloodgateHook.getDeviceOs(uuid);
    }
}
