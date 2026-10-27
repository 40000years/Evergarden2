import com.example.voidscape.boss.JudgmentFight;
import com.example.voidscape.boss.JudgmentPattern;
import com.example.voidscape.boss.JudgeBalance;
import static com.example.voidscape.boss.JudgmentFight.Part.*;
import java.util.UUID;

public final class JudgmentRulesChecks {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args){System.out.println("PASS "+run()+" encounter rules checks");}
    public static int run(){
        for(int phase=1;phase<=3;phase++){
            var pattern=JudgmentPattern.forPhase(phase);
            check(pattern.pulses()==phase&&pattern.recovery()==new int[]{68,56,48}[phase-1],"later phases add pulses and retain an extra second of recovery");
            check(pattern.slamRadius()+1<pattern.slamWarning()/20.0*5.6,"larger slams leave a sprint escape plus a one-block margin");
            check(pattern.laneHalfWidth()*Math.sqrt(2)+1<pattern.crossWarning()/20.0*5.6,"cross intersections retain an escape from the center");
            check(pattern.ringHalfWidth()+1<pattern.ringWarning()/20.0*5.6,"wide annular warnings retain a radial escape");
            for(int i=0;i<36;i++){
                double angle=i*Math.PI/18,w=pattern.laneHalfWidth(),x=30*Math.cos(angle)-(w-.01)*Math.sin(angle),z=30*Math.sin(angle)+(w-.01)*Math.cos(angle);
                check(JudgmentPattern.cross(x,z,angle,w),"rotated lane includes the visible inner edge");
                x=30*Math.cos(angle)-(w+.01)*Math.sin(angle);z=30*Math.sin(angle)+(w+.01)*Math.cos(angle);
                check(!JudgmentPattern.cross(x,z,angle,w),"rotated lane excludes positions beyond its visible boundary");
            }
            check(JudgmentPattern.ring(30+pattern.ringHalfWidth(),0,30,pattern.ringHalfWidth()),"annulus includes its visible outer edge");
            check(!JudgmentPattern.ring(30+pattern.ringHalfWidth()+.01,0,30,pattern.ringHalfWidth()),"annulus excludes points beyond its outer warning");
        }
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var bonus=new JudgmentFight(JudgeBalance.CORE_HEALTH,JudgeBalance.HAND_HEALTH,.65,240,16);bonus.join(a);
        check(bonus.core()==10000&&bonus.hand(LEFT)==1500&&bonus.hand(RIGHT)==1500,"new solo HP defaults");
        for(var kind:JudgmentFight.DamageKind.values())check(bonus.accepts(kind),"phase one accepts "+kind);
        check(bonus.coreMultiplier(0)==1,"sealed core has no damage bonus");
        check(bonus.hit(a,LEFT,1000,0).damage()==1000&&bonus.hit(a,LEFT,500,0).broke(),"same-tick hits have no per-hit or per-second cap");
        check(bonus.coreMultiplier(0)==2&&bonus.hit(a,CORE,100,0).damage()==200,"one broken hand doubles core damage");
        check(bonus.hit(a,RIGHT,1500,0).broke()&&bonus.coreMultiplier(0)==3,"both hands broken in one opening grant triple damage");
        check(bonus.hit(a,CORE,100,0).damage()==300,"both-hand bonus is applied to core damage");
        bonus.join(b);check(Math.abs(bonus.hit(b,CORE,165,1).damage()-300)<1e-9,"multiplayer scaling and bonus are each applied once");
        long bonusEnd=bonus.exposedUntil();bonus.tick(bonusEnd);
        check(bonus.coreMultiplier(bonusEnd)==1&&bonus.hand(LEFT)==675&&bonus.hand(RIGHT)==675,"missed opening clears the bonus and restores broken hands at 45 percent HP");
        bonus.hit(a,RIGHT,1000000,bonusEnd+1);check(bonus.coreMultiplier(bonusEnd+1)==2,"rebreaking one regenerated hand only grants the single-hand bonus");
        bonus.hit(a,CORE,1000000,bonusEnd+2);
        check(bonus.phase()==2&&bonus.coreMultiplier(bonusEnd+2)==1,"phase transition resets the bonus");
        check(!bonus.accepts(JudgmentFight.DamageKind.PROJECTILE)&&bonus.accepts(JudgmentFight.DamageKind.MAGIC),"phase two blocks projectiles and permits wands");
        bonus.hit(a,LEFT,1000000,bonusEnd+3);bonus.hit(a,CORE,1000000,bonusEnd+4);bonus.tick(bonusEnd+164);
        check(bonus.phase()==3&&bonus.coreMultiplier(bonusEnd+164)==1,"final phase has no bonus from removed hands");
        check(!bonus.accepts(JudgmentFight.DamageKind.PROJECTILE)&&!bonus.accepts(JudgmentFight.DamageKind.MAGIC)&&bonus.accepts(JudgmentFight.DamageKind.MELEE),"phase three blocks arrows and wands while allowing melee");
        JudgmentFight f=new JudgmentFight(30000,6500,.65,240,16);f.join(a);
        check(f.hit(a,CORE,2000,0).damage()==0,"sealed core rejects a burst");
        check(f.hit(a,LEFT,2000,1).damage()==2000,"strong equipment retains its damage on a hand");
        check(f.hit(a,LEFT,Double.NaN,1).damage()==0&&f.hand(LEFT)==4500,"invalid spell damage cannot poison HP");
        check(f.hit(a,LEFT,Double.POSITIVE_INFINITY,1).damage()==0&&f.hand(LEFT)==4500,"infinite damage cannot poison HP");
        f.join(b);check(Math.abs(f.scale()-1.65)<1e-9,"duo gets 65 percent extra effective HP");
        check(f.core()==30000&&f.hand(LEFT)==4500,"joining changes neither existing damage nor boss-bar percentage");
        check(Math.abs(f.hit(b,LEFT,1650,2).damage()-1000)<1e-9,"duo damage uses the shared scale once");
        check(f.hit(a,LEFT,1000000,3).broke()&&f.exposed(3),"breaking one hand opens the core for solo-compatible mechanics");
        check(f.hit(a,CORE,1000000,4).transition()&&f.phase()==2&&f.core()==21000,"huge burst stops at the first phase boundary");
        check(f.hit(a,CORE,1000000,5).damage()==0,"same-tick follow-ups cannot skip the next sealed phase");
        check(f.hand(LEFT)==6500&&f.hand(RIGHT)==6500,"new phase restores both parts");
        f.hit(a,RIGHT,1000000,6);long end=f.exposedUntil();
        f.tick(end);check(!f.exposed(end)&&f.hand(RIGHT)==2925,"missed exposure restores only 45 percent of the broken hand");
        f.hit(a,RIGHT,1000000,end+1);f.hit(a,CORE,1000000,end+2);
        check(f.phase()==3&&f.core()==10500&&f.hand(LEFT)==0&&f.hand(RIGHT)==0,"second boundary leaves a final independent heart");
        check(f.openingIn(end+2)==0&&f.exposed(end+2),"final core is immediately open without a countdown");
        check(!f.tick(end+161)&&f.exposed(end+161),"final core stays open while time advances");
        check(!f.tick(end+10000)&&f.exposed(end+10000),"final core never reseals after a missed damage window");
        check(f.hit(a,CORE,1000000,end+163).won(),"final exposed core can be defeated");
        check(f.hit(a,CORE,1000000,end+164).damage()==0,"dead encounters cannot award contribution twice");
        check(f.contribution(a)>0&&f.contribution(b)>0,"separate contributors remain credited");
        var finale=new JudgmentFight(10000,1500,.65,240,16);finale.join(a);finale.prepareTestPhase(3);
        check(finale.core()==3500&&finale.exposed(0)&&finale.hand(LEFT)==0,"phase-three test starts directly with the actual final-phase HP");
        finale.beginRupture(0);
        check(finale.ruptureCharging(159)&&!finale.ruptureCharging(160),"charge deadline is exactly eight seconds");
        check(!finale.strikeSeal(a,0,JudgmentFight.DamageKind.MAGIC,1000,1)&&finale.sealHits(0)==0,"wands cannot break a seal");
        check(!finale.strikeSeal(a,0,JudgmentFight.DamageKind.PROJECTILE,1000,1)&&finale.sealHits(0)==0,"arrows cannot break a seal");
        check(!finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,Double.NaN,1)&&finale.sealHits(0)==0,"invalid damage cannot break a seal");
        finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,1000000,1);
        check(finale.sealHits(0)==1,"huge melee damage counts as one strike");
        finale.strikeSeal(a,1,JudgmentFight.DamageKind.MELEE,1000000,1);
        check(finale.sealHits(1)==0,"same-tick bonus hits cannot advance a second seal");
        finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,1,9);finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,1,17);
        check(finale.sealHits(0)==3&&!finale.stunned(17)&&finale.coreMultiplier(17)==1,"one completed seal does not interrupt the charge");
        finale.strikeSeal(a,1,JudgmentFight.DamageKind.MELEE,1,25);finale.strikeSeal(a,1,JudgmentFight.DamageKind.MELEE,1,33);
        check(finale.strikeSeal(a,1,JudgmentFight.DamageKind.MELEE,1,41)&&finale.stunned(41)&&!finale.ruptureCharging(41),"six valid strikes interrupt the charge");
        check(finale.hit(a,CORE,100,42).damage()==300,"successful interruption grants triple core damage");
        check(finale.stunned(180)&&!finale.stunned(181)&&finale.coreMultiplier(181)==1,"stun and bonus expire at the exact seven-second boundary");
        finale.beginRupture(200);finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,1,201);finale.finishRupture();
        check(finale.sealHits(0)==0&&finale.sealHits(1)==0&&finale.coreMultiplier(360)==1&&finale.exposed(360),"failed charge resets its seals without resealing the core");
        finale.beginRupture(400);
        check(!finale.strikeSeal(a,0,JudgmentFight.DamageKind.MELEE,1,560)&&finale.sealHits(0)==0,"a strike at the deadline cannot cancel a resolved attack");
        finale.prepareTestPhase(2);check(finale.core()==7000&&!finale.exposed(0)&&!finale.stunned(0)&&finale.contribution(a)==0,"test phase reset clears damage and final-phase mechanics");
        var lure=new JudgmentFight(10000,1500,.65,240,16);lure.join(a);lure.prepareTestPhase(2);
        check(JudgmentPattern.baitRune(3,0,0,0)&&!JudgmentPattern.baitRune(3.01,0,0,0),"lure collision matches the exact visible three-block rune");
        check(lure.baitHand(a,LEFT,0).broke()&&lure.hand(LEFT)==0&&lure.coreMultiplier(0)==2,"a successful lure breaks its hand and doubles core damage");
        check(lure.contribution(a)==1500&&lure.baitHand(a,LEFT,1).damage()==0,"lure effort earns contribution once without repeat credit");
        check(lure.baitHand(a,RIGHT,2).broke()&&lure.coreMultiplier(2)==3,"luring both hands in one opening grants triple damage");
        lure.tick(lure.exposedUntil());check(lure.hand(LEFT)==675&&lure.hand(RIGHT)==675,"missed lure opening regenerates hands under the same encounter rules");
        lure.prepareTestPhase(1);check(!lure.baitHand(a,LEFT,10).broke()&&lure.hand(LEFT)==1500,"lure mechanic cannot break phase-one hands");
        lure.prepareTestPhase(3);check(!lure.baitHand(a,LEFT,11).broke(),"lure mechanic cannot change the final phase");
        JudgmentFight capped=new JudgmentFight(1,1,.65,240,1);capped.join(a);
        check(!capped.join(b)&&capped.scale()==1,"participant limit does not alter HP");
        check(JudgmentFight.sanctuary(30,0,30,0,10)&&JudgmentFight.sanctuary(-30,0,30,0,10),"either sanctuary is valid");
        check(JudgmentFight.sanctuary(40,0,30,0,10)&&!JudgmentFight.sanctuary(40.01,0,30,0,10),"safe circle boundary exactly matches the warning");
        check(!JudgmentFight.sanctuary(0,0,30,0,10),"center is not accidentally safe during judgment");
        for(int i=0;i<360;i++){
            double edgeAngle=i*Math.PI/180,x=79*Math.cos(edgeAngle),z=79*Math.sin(edgeAngle),distance=Double.POSITIVE_INFINITY;
            for(int q=0;q<4;q++){double angle=q*Math.PI/2;distance=Math.min(distance,Math.hypot(x-48*Math.cos(angle),z-48*Math.sin(angle))-16);}
            check(distance<=41,"a sanctuary is reachable within eight seconds of sprinting from every arena edge");
        }
        // A deterministic endgame DPS simulation. Test the promised target instead of guessing HP.
        for(int players:new int[]{1,4,6}){
            JudgmentFight sim=new JudgmentFight(JudgeBalance.CORE_HEALTH,JudgeBalance.HAND_HEALTH,.65,240,16);
            UUID[] ids=new UUID[players];for(int i=0;i<players;i++){ids[i]=UUID.randomUUID();sim.join(ids[i]);}
            long ticks=0;
            for(;ticks<20*1800&&!sim.won();ticks+=20){
                sim.tick(ticks);
                for(UUID uuid:ids)sim.hit(uuid,sim.exposed(ticks)?CORE:sim.hand(LEFT)>0?LEFT:RIGHT,180,ticks);
            }
            check(sim.won(),"endgame DPS simulation completes for "+players+" players");
            double minutes=ticks/1200.0;System.out.printf("%d players at 180 applied DPS each: %.2f minutes%n",players,minutes);
            check(minutes>0&&minutes<3,"lower HP and hand bonuses finish the stationary DPS simulation within three minutes");
        }
        return checks;
    }
}
