package com.example.voidscape.boss;

import org.bukkit.configuration.file.FileConfiguration;

/** Migrate published balance defaults once, preserving administrator tuning. */
public final class JudgeBalance {
    public static final double CORE_HEALTH=10000;
    public static final double HAND_HEALTH=1500;
    public static final int JUDGMENT_COOLDOWN_SECONDS=115;
    public static final int PHASE_ONE_JUDGMENT_DELAY=40;
    public static final int ATTACK_POWER_PERCENT=130;
    public static final int EMPTY_RESET_SECONDS=180;
    private JudgeBalance(){}
    public static void upgrade(FileConfiguration config){
        String version="world-boss.balance-version",health="world-boss.core-health",power="world-boss.attack-power-percent";
        int previous=config.contains(version,true)?config.getInt(version):0;
        if(previous>=4)return;
        if(previous<1){
            if(!config.contains(health,true)||Double.compare(config.getDouble(health),40000)==0)config.set(health,20000);
            if(!config.contains(power,true))config.set(power,ATTACK_POWER_PERCENT);
        }
        String empty="world-boss.empty-reset-seconds",timeout="world-boss.fight-timeout-seconds";
        if(previous<2){
            if(!config.contains(empty,true)||config.getInt(empty)==30)config.set(empty,EMPTY_RESET_SECONDS);
            if(!config.contains(timeout,true)||config.getInt(timeout)==1800)config.set(timeout,0);
        }
        if(previous<3){
            if(!config.contains(health,true)||Double.compare(config.getDouble(health),20000)==0)config.set(health,CORE_HEALTH);
            String hands="world-boss.hand-health";
            if(!config.contains(hands,true)||config.getDouble(hands)==8500||config.getDouble(hands)==8000)config.set(hands,HAND_HEALTH);
            config.set("world-boss.damage-caps",null);
        }
        String cooldown="world-boss.judgment-cooldown-seconds",delay="world-boss.judgment-phase-one-delay-seconds";
        if(!config.contains(cooldown,true)||config.getInt(cooldown)==130)config.set(cooldown,JUDGMENT_COOLDOWN_SECONDS);
        if(!config.contains(delay,true)||config.getInt(delay)==45)config.set(delay,PHASE_ONE_JUDGMENT_DELAY);
        config.set(version,4);
    }
}
