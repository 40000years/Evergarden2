package com.example.voidscape.boss;

/** Tick timings scale the authored preparation/contact/recovery clips together. */
record RiverCombatProfile(int windup,int active,int contact,int cooldown,double reach,double standOff) {
    static RiverCombatProfile of(ArkhaiaWaves.Kind kind){return switch(kind){
        case ACHERON->new RiverCombatProfile(10,8,4,44,3.5,0);
        case PHLEGETHON->new RiverCombatProfile(18,10,5,66,4,0);
        case COCYTUS->new RiverCombatProfile(20,8,4,64,18,10);
        case BANSHEE_WAIL->new RiverCombatProfile(16,10,5,55,26,5);
        case MALEFEASANCE->new RiverCombatProfile(16,10,5,62,10,8);
        case STYX->new RiverCombatProfile(26,10,5,84,4.5,0);
    };}
    int hitTick(){return windup+contact;}
    int duration(){return windup+active+14;}
    String clip(long age){return age<windup?"attack_windup":age<windup+active?"attack_active":"attack_recover";}
    double rate(long age){return age<windup?16.0/windup:age<windup+active?8.0/active:1;}
}
