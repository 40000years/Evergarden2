import com.example.voidscape.boss.SeraphonRules;
import com.example.voidscape.boss.SeraphonRules.Skill;
import java.util.*;

/** Pure checks for Seraphon's skill choice, mirror geometry, shelter and scaling. */
public final class SeraphonRulesChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    public static void main(String[] args){
        check(SeraphonRules.health(7000,1500,10000,1)==7000&&SeraphonRules.health(7000,1500,10000,2)==8500,"solo and duo health");
        check(SeraphonRules.health(7000,1500,10000,3)==10000&&SeraphonRules.health(7000,1500,10000,9)==10000,"health capped at 10000");
        check(SeraphonRules.phase(1)==1&&SeraphonRules.phase(.65)==2&&SeraphonRules.phase(.30)==3,"phase thresholds 65% and 30%");
        check(SeraphonRules.wheels(1)==2&&SeraphonRules.wheels(2)==3&&SeraphonRules.wheels(3)==4&&SeraphonRules.wheels(10)==4,"wheel count 2/3/4");
        for(int phase=1;phase<=3;phase++){
            var random=new Random(phase);var ready=new EnumMap<Skill,Long>(Skill.class);Skill last=null;var seen=EnumSet.noneOf(Skill.class);
            for(long t=0;t<20000;t+=60){
                Skill s=SeraphonRules.choose(phase,last,ready,t,random);
                check(s!=last,"never the same skill twice");check(s!=Skill.CHOIR&&s!=Skill.WINGS,"scripted skills are never random");
                check(t>=ready.getOrDefault(s,0L),"cooldowns respected");
                int cooldown=SeraphonRules.cooldown(s,phase);if(cooldown>0)ready.put(s,t+cooldown);seen.add(s);last=s;
            }
            check(seen.containsAll(List.of(Skill.LANCES,Skill.FEATHERS,Skill.SMITE,Skill.SOLAR,Skill.VERDICT)),"full kit in phase "+phase);
            check(phase>1==seen.contains(Skill.ORBIT),"orbit only from phase two");
        }
        // Orb six blocks above the floor; target at the 24-block mirror ring.
        double floor=100,orbY=floor+6.4,eye=floor+1.62,mirrorY=floor+SeraphonRules.MIRROR_RADIUS+.3;
        check(SeraphonRules.mirrored(0,orbY,0,0,eye,26,0,mirrorY,24),"a mirror two blocks in front of the target shields it");
        check(SeraphonRules.mirrored(0,orbY,0,.8,eye,25,0,mirrorY,24),"slightly off-axis still covered by the mirror face");
        check(!SeraphonRules.mirrored(0,orbY,0,4,eye,26,0,mirrorY,24),"stepping aside leaves the mirror's cover");
        check(!SeraphonRules.mirrored(0,orbY,0,0,eye,20,0,mirrorY,24),"a mirror behind the target does nothing");
        check(!SeraphonRules.mirrored(0,orbY,0,0,eye,40,0,mirrorY,24),"a mirror far from the target cannot shield it");
        check(!SeraphonRules.mirrored(0,orbY,0,0,eye,26,0,orbY,1.5),"nothing near the orb counts as a mirror");
        check(SeraphonRules.sheltered(3,4,0,0)&&!SeraphonRules.sheltered(6,6,0,0),"wings shelter radius 7");
        int safe=0;for(int petal=0;petal<12;petal++)if(SeraphonRules.safePetal(petal,1))safe++;check(safe==3,"three safe petals");
        System.out.println("PASS "+checks+" Seraphon rule checks");
    }
}
