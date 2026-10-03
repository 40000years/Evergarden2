package com.example.voidscape.boss;

import org.bukkit.configuration.file.FileConfiguration;

/** Migrate published balance defaults once, preserving administrator tuning. */
public final class JudgeBalance {
    public static final double CORE_HEALTH=20000;
    public static final int ATTACK_POWER_PERCENT=130;
    private JudgeBalance(){}
    public static void upgrade(FileConfiguration config){
        String version="world-boss.balance-version",health="world-boss.core-health",power="world-boss.attack-power-percent";
        if(config.contains(version,true)&&config.getInt(version)>=1)return;
        if(!config.contains(health,true)||Double.compare(config.getDouble(health),40000)==0)config.set(health,CORE_HEALTH);
        if(!config.contains(power,true))config.set(power,ATTACK_POWER_PERCENT);
        config.set(version,1);
    }
}
