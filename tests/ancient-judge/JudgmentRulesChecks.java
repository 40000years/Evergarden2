import com.example.voidscape.boss.JudgmentFight;
import com.example.voidscape.boss.JudgmentPattern;
import com.example.voidscape.boss.JudgeDamageBudget;
import static com.example.voidscape.boss.JudgmentFight.Part.*;
import java.util.UUID;

public final class JudgmentRulesChecks {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args){System.out.println("PASS "+run()+" encounter rules checks");}
    public static int run(){
        for(int phase=1;phase<=3;phase++){
            var pattern=JudgmentPattern.forPhase(phase);
            check(pattern.pulses()==phase&&pattern.recovery()<=48,"each later phase adds a pulse and shortens recovery");
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
        var budget=new JudgeDamageBudget();var limits=new JudgeDamageBudget.Limits(100,80,240,80,240);
        check(budget.available(a,JudgeDamageBudget.Kind.PROJECTILE,1000000,0,limits)==80,"projectile bonuses cannot exceed their final cap");
        check(budget.available(a,JudgeDamageBudget.Kind.MELEE,1000000,0,limits)==100,"melee has a separate hit cap");
        check(budget.available(a,JudgeDamageBudget.Kind.OTHER,1000000,0,limits)==80,"unclassified damage also has a cap");
        budget.spend(a,80,0);budget.spend(a,100,5);
        check(budget.available(a,JudgeDamageBudget.Kind.MAGIC,1000000,19,limits)==60,"mixed attacks share a rolling budget");
        check(budget.available(b,JudgeDamageBudget.Kind.MAGIC,1000000,19,limits)==240,"each participant has an independent budget");
        check(budget.available(a,JudgeDamageBudget.Kind.MAGIC,1000000,20,limits)==140,"only hits a full second old expire");
        check(budget.available(a,JudgeDamageBudget.Kind.MAGIC,1000000,25,limits)==240,"expired damage restores the full budget");
        check(budget.available(a,JudgeDamageBudget.Kind.MAGIC,Double.NaN,25,limits)==0,"NaN damage is rejected");
        check(budget.available(a,JudgeDamageBudget.Kind.MAGIC,Double.POSITIVE_INFINITY,25,limits)==0,"infinite damage is rejected");
        JudgmentFight f=new JudgmentFight(30000,6500,.65,240,16);f.join(a);
        check(f.hit(a,CORE,2000,0).damage()==0,"sealed core rejects a burst");
        check(f.hit(a,LEFT,2000,1).damage()==2000,"strong equipment retains its damage on a hand");
        check(f.hit(a,LEFT,Double.NaN,1).damage()==0&&f.hand(LEFT)==4500,"invalid spell damage cannot poison HP");
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
        check(f.openingIn(end+2)==160&&f.openingIn(end+102)==60,"final opening countdown is tied to actual server ticks");
        check(!f.tick(end+161)&&!f.exposed(end+161),"final phase gives warning time before opening");
        check(f.tick(end+162)&&f.exposed(end+162),"final core opens without requiring another hand or another player");
        check(f.hit(a,CORE,1000000,end+163).won(),"final exposed core can be defeated");
        check(f.hit(a,CORE,1000000,end+164).damage()==0,"dead encounters cannot award contribution twice");
        check(f.contribution(a)>0&&f.contribution(b)>0,"separate contributors remain credited");
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
            JudgmentFight sim=new JudgmentFight(40000,8500,.65,240,16);
            UUID[] ids=new UUID[players];for(int i=0;i<players;i++){ids[i]=UUID.randomUUID();sim.join(ids[i]);}
            long ticks=0;
            for(;ticks<20*1800&&!sim.won();ticks+=20){
                sim.tick(ticks);
                for(UUID uuid:ids)sim.hit(uuid,sim.exposed(ticks)?CORE:sim.hand(LEFT)>0?LEFT:RIGHT,180,ticks);
            }
            check(sim.won(),"endgame DPS simulation completes for "+players+" players");
            double minutes=ticks/1200.0;System.out.printf("%d players at 180 applied DPS each: %.2f minutes%n",players,minutes);
            check(minutes>=4&&minutes<=15,"endgame simulation stays within a playable time range");
        }
        return checks;
    }
}
