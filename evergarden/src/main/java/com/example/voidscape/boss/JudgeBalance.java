package com.example.voidscape.boss;

import org.bukkit.configuration.file.FileConfiguration;

/** Migrate published balance defaults once, preserving administrator tuning. */
public final class JudgeBalance {
    public static final double CORE_HEALTH=20000;
    public static final int ATTACK_POWER_PERCENT=130;
    public static final int EMPTY_RESET_SECONDS=180;
    private JudgeBalance(){}
    public static void upgrade(FileConfiguration config){
        String version="world-boss.balance-version",health="world-boss.core-health",power="world-boss.attack-power-percent";
        int previous=config.contains(version,true)?config.getInt(version):0;
        if(previous>=2)return;
        if(previous<1){
            if(!config.contains(health,true)||Double.compare(config.getDouble(health),40000)==0)config.set(health,CORE_HEALTH);
            if(!config.contains(power,true))config.set(power,ATTACK_POWER_PERCENT);
        }
        String empty="world-boss.empty-reset-seconds",timeout="world-boss.fight-timeout-seconds";
        if(!config.contains(empty,true)||config.getInt(empty)==30)config.set(empty,EMPTY_RESET_SECONDS);
        if(!config.contains(timeout,true)||config.getInt(timeout)==1800)config.set(timeout,0);
        config.set(version,2);
    }
}
