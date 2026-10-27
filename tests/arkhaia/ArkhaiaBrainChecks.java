import com.example.voidscape.boss.*;
import java.util.*;

public final class ArkhaiaBrainChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    public static void main(String[] args){
        for(int yaw=0;yaw<360;yaw+=45){
            double a=Math.toRadians(yaw),fx=-Math.sin(a),fz=Math.cos(a);
            check(ArkhaiaRules.front(yaw,fx*5,fz*5)&&!ArkhaiaRules.behind(yaw,fx*5,fz*5),"front cone faces the body's yaw");
            check(ArkhaiaRules.behind(yaw,-fx*5,-fz*5)&&!ArkhaiaRules.front(yaw,-fx*5,-fz*5),"rear cone is opposite the yaw");
            check(!ArkhaiaRules.front(yaw,fz*5,-fx*5)&&!ArkhaiaRules.behind(yaw,fz*5,-fx*5),"flanks are neither front nor rear");
        }
        check(!ArkhaiaRules.behind(0,0,0)&&!ArkhaiaRules.front(0,0,0),"standing at boss center does not bypass rear or parry gates");
        check(ArkhaiaRules.posture(ArkhaiaRules.Kind.MELEE,true,true)==1.5*1.2*2&&ArkhaiaRules.posture(ArkhaiaRules.Kind.ADVANCE_MAGIC,false,false)==1,"posture scales by rear, melee and duel bystander");
        var waves=new ArkhaiaWaves();var seen=EnumSet.noneOf(ArkhaiaWaves.Kind.class);
        for(int i=0;i<12;i++){
            var batch=waves.fill(i*200,1);check(batch.size()==1,"solo summons exactly one spirit");
            if(i<6)check(seen.add(batch.get(0)),"all six types rotate before repeating");
            waves.died(batch.get(0),i*200);check(waves.fill(i*200+199,1).isEmpty(),"replacement waits full ten seconds");
        }
        waves.clear();var first=waves.fill(0,2);check(first.size()==2,"two players summon two unique spirits");
        waves.died(first.get(0),1);var next=waves.fill(201,2);check(next.size()==1&&!first.contains(next.get(0)),"survivor never duplicated; roster advances");
        check(waves.fill(202,20).size()==4&&waves.alive()==6,"large party capped at six unique spirits");
        waves.clear();check(waves.fill(0,0).isEmpty(),"empty arena never summons spirits");
        for(int phase=1;phase<=2;phase++){
            var brain=new ArkhaiaBrain(new Random(5),350);int magic=0,melee=0,recoverTicks=0;var chosen=new HashSet<ArkhaiaBrain.Action>();
            var starts=new EnumMap<ArkhaiaBrain.Action,Integer>(ArkhaiaBrain.Action.class);
            for(int t=0;t<18000;t++){
                var f=brain.update(t,new ArkhaiaBrain.Sense(true,6,phase==1?1:.5,true));
                if(f.state()==ArkhaiaBrain.State.WINDUP&&f.age()==0){
                    chosen.add(f.action());if(f.magic())magic++;else melee++;
                    Integer previous=starts.put(f.action(),t);
                    if(previous!=null)check(t-previous>=ArkhaiaCombatTiming.of(f.action()).cooldown(),"independent skill cooldowns");
                }
                if(f.state()==ArkhaiaBrain.State.RECOVER){recoverTicks++;check(!ArkhaiaSteering.tracksTarget(f),"rear opening never tracks attacker");}
                if(f.state()==ArkhaiaBrain.State.WINDUP&&f.age()>=6||f.state()==ArkhaiaBrain.State.ACTIVE)
                    check(!ArkhaiaSteering.tracksTarget(f),"cast direction stays locked");
                check(f.state()!=ArkhaiaBrain.State.BACKSTEP&&f.state()!=ArkhaiaBrain.State.RETREAT,"no routine backpedal loop");
            }
            check(chosen.containsAll(List.of(ArkhaiaBrain.Action.RIFT,ArkhaiaBrain.Action.NOVA,ArkhaiaBrain.Action.PORTALS,ArkhaiaBrain.Action.ECLIPSE)),"all four magic patterns participate");
            check(magic>30&&melee<(magic+melee)*.2,"magic dominates both phases");
            check(phase!=1||melee==0,"phase one is a stationary spell caster");
            check(recoverTicks>4000,"sustained fight offers substantial rear openings");
        }
        for(double distance:new double[]{24,40,78}){
            var distant=new ArkhaiaBrain(new Random(8),350);boolean cast=false;
            for(int t=0;t<100;t++){
                var f=distant.update(t,new ArkhaiaBrain.Sense(true,distance,1,true));
                cast|=f.state()==ArkhaiaBrain.State.WINDUP&&f.magic();
                check(f.action()!=ArkhaiaBrain.Action.NOVA,"distant target does not waste close-range nova");
            }
            check(cast,"boss casts at targets across the arena");
        }
        var edge=ArkhaiaPattern.plan(ArkhaiaBrain.Action.RIFT,1,0,0,0,40,0,0,0);
        check(edge.stream().anyMatch(h->h.contains(0,40)),"rift threatens arena-edge shooter instead of stopping at radius 30");
        var brain=new ArkhaiaBrain(new Random(1),350);brain.phaseTwo(0);
        check(brain.update(1,new ArkhaiaBrain.Sense(true,5,.5,true)).state()==ArkhaiaBrain.State.TRANSITION,"phase switch interrupts current attack");
        check(brain.update(70,new ArkhaiaBrain.Sense(true,5,.5,true)).state()==ArkhaiaBrain.State.RECOVER,"transition grants an opening");
        check(brain.update(71,new ArkhaiaBrain.Sense(false,0,.5,false)).state()==ArkhaiaBrain.State.IDLE,"lost target cancels cast safely");
        var steering=new ArkhaiaSteering();var orbit=new ArkhaiaBrain.Frame(ArkhaiaBrain.State.ORBIT,ArkhaiaBrain.Action.NONE,0,0,false);
        for(int t=0;t<40;t++)check(steering.update(6,0,orbit,1,0).length()==0,"no circling or retreating at spell range");
        for(int t=0;t<10;t++)steering.update(40,0,orbit,1,0);
        check(steering.update(40,0,orbit,1,0).length()<=.091,"out of range movement is restrained");
        for(var action:List.of(ArkhaiaBrain.Action.RIFT,ArkhaiaBrain.Action.NOVA,ArkhaiaBrain.Action.PORTALS,ArkhaiaBrain.Action.ECLIPSE))for(int phase=1;phase<=2;phase++){
            var plan=ArkhaiaPattern.plan(action,phase,0,0,0,15,0,0,0);
            check(!plan.isEmpty()&&plan.size()<=4,"bounded authored hazard count");
            for(var h:plan){
                check(h.tick()>=ArkhaiaCombatTiming.of(action).impact()&&h.tick()<ArkhaiaCombatTiming.of(action).active(),"every pulse occurs inside release clock");
                check(h.shape()==ArkhaiaPattern.Shape.RING||h.contains(h.x(),h.z()),"circle/lane center is in damage geometry");
                check(!h.contains(1000,1000),"finite warning bounds");
                if(h.shape()==ArkhaiaPattern.Shape.RING){check(!h.contains(h.x(),h.z()),"ring center is a real safe area");check(h.contains(h.x()+h.radius(),h.z()),"ring rim is hazardous");}
            }
        }
        var look=new ArkhaiaLook();ArkhaiaLook.Gaze gaze=null;
        for(int t=0;t<16;t++)gaze=look.update(0,-6,-4,6,true,false);
        check(Math.abs(gaze.yaw()-45)<1&&gaze.pitch()>24,"head still tracks independently outside openings");
        System.out.println("PASS "+checks+" worldboss rules, phase, decision, rear-opening, movement and pattern checks");
    }
}
