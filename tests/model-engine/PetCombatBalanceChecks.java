package com.example.voidscape.pet;

import java.util.UUID;

/** Focused balance checks for companion fights against ordinary and levelled mobs. */
public final class PetCombatBalanceChecks {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static PetRecord pet(PetCatalog.Species species,PetCatalog.Stage stage){
        PetRecord r=new PetRecord(UUID.randomUUID(),species,stage);
        r.vitality=12;r.affinity=12;return r;
    }
    public static void main(String[] args){
        PetRecord adult=pet(PetCatalog.Species.SHADOW_PANTHER,PetCatalog.Stage.ADULT);
        double max=PetSkills.maxHealth(adult);
        double low=PetSkills.damageTaken(adult,4),extreme=PetSkills.damageTaken(adult,10000);
        check(max>52,"ordinary adults have more health than the previous 40 + vitality");
        check(low>0 && low<4,"ordinary counterattacks still hurt, but hurt less");
        check(extreme>low && extreme<=max*.2,"levelled-mob damage is bounded per hit");
        double remaining=max;
        for(int i=0;i<4;i++)remaining-=extreme;
        check(remaining>0,"a healthy companion survives four extreme hits");
        PetRecord tank=pet(PetCatalog.Species.CRYSTAL_TORTOISE,PetCatalog.Stage.ADULT);
        check(PetSkills.damageTaken(tank,10000)<extreme,"tortoise keeps its extra mitigation");
        PetRecord mythic=pet(PetCatalog.Species.IGNIS_DRACONIS,PetCatalog.Stage.ADULT);
        check(PetSkills.maxHealth(mythic)>max && PetSkills.maxHealth(mythic)<max*1.25,"mythical health buff remains modest");
        check(PetSkills.damage(mythic)>PetSkills.damage(adult)
            && PetSkills.damage(mythic)<PetSkills.damage(adult)*1.25,"mythical strike buff remains modest");
        check(PetSkills.MODES.contains("idle"),"idle mode can be saved and loaded");
        System.out.println("PASS: pet combat survivability and mythical balance");
    }
}
