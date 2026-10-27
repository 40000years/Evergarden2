import com.example.voidscape.boss.*;
import com.example.voidscape.boss.ArkhaiaSovereign.*;
import java.util.*;

/** Pure checks for Arkhaia's duel director: health scaling, phases, ritual, posture, parry and the phase-three execution. */
public final class ArkhaiaSovereignChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static final Sense CLOSE=new Sense(true,5,true),FAR=new Sense(true,20,true),NONE=new Sense(false,0,false);
    static ArkhaiaSovereign boss(long seed){return new ArkhaiaSovereign(new Random(seed),7000,1500,10000);}
    static List<Event> run(ArkhaiaSovereign b,long from,long to,Sense sense){var events=new ArrayList<Event>();for(long t=from;t<to;t++){b.update(t,sense);events.addAll(b.drain());}return events;}
    public static void main(String[] args){
        check(ArkhaiaSovereign.scaledHealth(7000,1500,10000,1)==7000,"solo health");
        check(ArkhaiaSovereign.scaledHealth(7000,1500,10000,2)==8500,"duo health");
        check(ArkhaiaSovereign.scaledHealth(7000,1500,10000,3)==10000&&ArkhaiaSovereign.scaledHealth(7000,1500,10000,8)==10000,"trio and larger parties capped at 10000");
        var b=boss(1);b.damage(1000,0,0);b.roster(2);
        check(b.maxHealth()==8500&&b.health()==7500,"late joiner raises max and keeps the missing amount");b.roster(1);check(b.maxHealth()==8500,"roster never shrinks");
        // Phase boundaries cannot be skipped and the transitions are invulnerable.
        b=boss(2);double dealt=b.damage(99999,1,0);
        check(dealt==7000*.35&&b.phase()==2&&b.state()==State.TRANSITION&&b.drain().contains(Event.PHASE_TWO),"huge hit stops at 65% and opens phase two");
        check(b.damage(500,1,1)==0&&b.invulnerable(),"transition is invulnerable");
        var events=run(b,1,ArkhaiaSovereign.TRANSITION+2,CLOSE);
        check(events.contains(Event.RITUAL)&&b.state()==State.RITUAL&&b.damage(500,1,80)==0,"phase two opens with the invulnerable river ritual");
        b.endRitual(true,80);check(b.state()==State.BROKEN&&b.drain().contains(Event.RITUAL_BROKEN),"clearing the ritual breaks the boss");
        double before=b.health();check(b.damage(100,1,81)==200&&b.health()==before-200,"broken boss takes double damage without gaining posture");
        events=run(b,82,82+ArkhaiaSovereign.RITUAL_BROKEN,CLOSE);check(b.state()!=State.BROKEN&&b.posture()==0,"break ends and posture resets");
        dealt=b.damage(99999,1,400);check(Math.abs(b.health()-b.maxHealth()*.5)<1e-6&&b.state()==State.RITUAL&&b.drain().contains(Event.SECOND_RITUAL),"second ritual at 50% stops the burst");
        events=run(b,401,401+ArkhaiaSovereign.RITUAL_LIMIT+1,CLOSE);check(events.contains(Event.RITUAL_FAILED)&&b.state()!=State.RITUAL,"ritual timeout fails instead of killing anyone");
        dealt=b.damage(99999,1,1700);check(b.phase()==3&&Math.abs(b.health()-b.maxHealth()*.35)<1e-6&&b.drain().contains(Event.PHASE_THREE),"phase three at 35%");
        events=run(b,1701,1701+ArkhaiaSovereign.TRANSITION+200,CLOSE);check(events.contains(Event.DUEL),"execution duel arrives shortly after phase three");
        // The duel is two sweeps and a parryable execution.
        b=boss(3);b.damage(99999,1,0);run(b,1,80,CLOSE);b.endRitual(true,80);run(b,81,300,CLOSE);b.damage(99999,1,300);run(b,301,1700,CLOSE);b.damage(99999,1,1700);
        var moves=new ArrayList<Move>();long t=1701;boolean executeParried=false;
        for(;t<1701+600&&!executeParried;t++){var a=b.update(t,CLOSE);b.drain();
            if(a.state()==State.WINDUP&&a.age()==0&&a.duel())moves.add(a.move());
            if(a.state()==State.WINDUP&&a.move()==Move.EXECUTE&&a.age()==a.duration()-ArkhaiaSovereign.flash(Move.EXECUTE,3))executeParried=b.parry(t,false);
        }
        check(moves.size()==3&&moves.get(2)==Move.EXECUTE&&moves.subList(0,2).stream().allMatch(m->m==Move.SWEEP_LEFT||m==Move.SWEEP_RIGHT),"duel = two sweeps then execute");
        check(executeParried&&b.state()==State.BROKEN,"parrying the execution breaks the boss outright");
        boolean eclipse=false;for(long e=t;e<t+2400&&!eclipse;e++){var a=b.update(e,CLOSE);eclipse|=b.drain().contains(Event.ECLIPSE);
            if(a.move()==Move.ECLIPSE&&a.state()==State.WINDUP)check(a.duration()>=120,"lethal Eclipse warns at least six seconds");}
        check(eclipse,"phase three schedules Styx Eclipse");
        // Posture: 14% of max health in frontal hits breaks the stance.
        b=boss(4);for(int i=0;i<9;i++)b.damage(7000*.14/10,1,i);check(b.state()!=State.BROKEN&&b.posture()>80,"posture builds below the break");
        b.damage(7000*.14/10,1,10);check(b.state()==State.BROKEN&&b.drain().contains(Event.BROKEN),"posture fills into Broken");
        b=boss(5);b.damage(7000*.14/2,1.5,0);check(b.posture()>=74&&b.posture()<76,"rear posture scale applies");
        run(b,1,900,NONE);check(b.posture()<1,"posture decays when the boss is left alone");
        // Parry window is only the final flash of a heavy.
        b=boss(6);boolean early=false,parried=false;
        for(t=0;t<4000&&!parried;t++){var a=b.update(t,CLOSE);b.drain();
            if(a.state()==State.WINDUP&&a.move()==Move.HEAVY){
                if(a.age()==1)early|=b.parry(t,false);
                if(a.age()==a.duration()-ArkhaiaSovereign.flash(Move.HEAVY,1))parried=b.parry(t,false);
            }
        }
        check(!early&&parried&&b.state()==State.STAGGER&&b.posture()>=40,"parry only during the gold flash; staggers and builds posture");
        b=boss(7);boolean shield=false;
        for(t=0;t<4000&&!shield;t++){var a=b.update(t,CLOSE);b.drain();if(a.state()==State.ACTIVE&&a.move()==Move.HEAVY&&a.age()<=1)shield=b.parry(t,true);}
        check(shield,"fresh shield at the impact also parries");
        // Charge into a wall opens the boss.
        b=boss(8);boolean wall=false;
        for(t=0;t<4000&&!wall;t++){var a=b.update(t,FAR);b.drain();if(a.state()==State.ACTIVE&&a.move()==Move.CHARGE){b.wall(t);wall=b.state()==State.STAGGER;}}
        check(wall,"charge into terrain staggers");
        // Long fights: heavy finishers, no dead time, distance-appropriate moves.
        for(int phase=1;phase<=3;phase++){
            b=boss(10+phase);t=0;
            if(phase>=2){b.damage(99999,1,t);run(b,t,t+80,CLOSE);t+=80;b.endRitual(false,t);b.drain();}
            if(phase==3){b.damage(99999,1,t);run(b,t,t+1300,CLOSE);t+=1300;b.damage(99999,1,t);b.drain();}
            int neutral=0,total=0;var chosen=EnumSet.noneOf(Move.class);List<Move> string=new ArrayList<>();
            for(long end=t+12000;t<end;t++){
                Sense sense=(t/400)%3==0?FAR:(t/400)%3==1?CLOSE:new Sense(true,11,true);
                var a=b.update(t,sense);b.drain();total++;
                if(a.state()==State.NEUTRAL)neutral++;
                if(a.state()==State.WINDUP&&a.age()==0){chosen.add(a.move());if(a.index()==0)string.clear();string.add(a.move());
                    if(a.index()==a.count()-1&&string.get(0)==Move.LIGHT)check(a.move()==Move.HEAVY||a.move()==Move.SPIN,"sword strings end on a heavy or spin");
                    if(a.move()==Move.CHARGE)check(sense.distance()>13,"charge only at range");}
            }
            check(neutral<total*.35,"boss keeps pressure in phase "+phase);
            check(chosen.containsAll(List.of(Move.LIGHT,Move.HEAVY,Move.CHARGE,Move.RIFT)),"core kit used in phase "+phase);
            if(phase>1)check(chosen.containsAll(List.of(Move.SPIN,Move.PORTALS)),"phase "+phase+" adds spin and portals");
            if(phase==1)check(!chosen.contains(Move.SPIN)&&!chosen.contains(Move.ECLIPSE),"phase one stays a pure duel");
        }
        b=boss(20);check(run(b,0,200,NONE).isEmpty()&&b.state()==State.NEUTRAL,"no target, no attack");
        // A wipe restarts the phase rather than the fight.
        b=boss(21);b.damage(99999,1,0);run(b,1,80,CLOSE);b.endRitual(false,80);run(b,81,200,CLOSE);b.damage(1000,1,200);b.regroup(300);
        check(Math.abs(b.health()-b.maxHealth()*.65)<1e-6&&b.phase()==2,"regroup restores the phase start");
        check(run(b,301,340,CLOSE).contains(Event.RITUAL),"regrouped phase two repeats its ritual");
        b=boss(22);b.damage(99999,1,0);run(b,1,80,CLOSE);b.endRitual(false,80);b.damage(99999,1,100);run(b,101,1400,CLOSE);b.damage(99999,1,1400);run(b,1401,1500,CLOSE);
        b.damage(b.health()-b.maxHealth()*.14,1,1500);check(b.lastStand()&&b.drain().contains(Event.LAST_STAND),"last stand below 15%");
        check(ArkhaiaSovereign.gap(3,true)<ArkhaiaSovereign.gap(3,false),"last stand shortens gaps");
        b.damage(99999,1,1501);check(b.health()==0&&b.drain().contains(Event.DEFEATED),"phase three can be defeated");
        // Strike geometry.
        for(int yaw=0;yaw<360;yaw+=30){
            double a=ArkhaiaPattern.facing(yaw),fx=Math.cos(a),fz=Math.sin(a);
            check(Math.abs(fx+Math.sin(Math.toRadians(yaw)))<1e-9&&Math.abs(fz-Math.cos(Math.toRadians(yaw)))<1e-9,"facing matches Minecraft yaw");
            var sector=new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.SECTOR,0,0,a,7.5,Math.toRadians(75),0,0);
            check(sector.contains(fx*6,fz*6)&&!sector.contains(-fx*4,-fz*4)&&!sector.contains(fx*9,fz*9),"light slash fans forward only");
            ArkhaiaPattern.Hazard left=new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.HALF,0,0,a,12,1,0,0),right=new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.HALF,0,0,a,12,-1,0,0);
            double sx=-Math.sin(a)*6,sz=Math.cos(a)*6;
            check(left.contains(sx,sz)!=right.contains(sx,sz)&&left.contains(-sx,-sz)!=right.contains(-sx,-sz),"each sweep leaves the opposite side safe");
            var lane=ArkhaiaPattern.forward(0,0,yaw,13,2.4,0,0);
            check(lane.contains(fx*10,fz*10)&&!lane.contains(-fx*2,-fz*2)&&!lane.contains(sx,sz),"heavy lane is a forward line");
        }
        System.out.println("PASS "+checks+" Arkhaia duel director checks");
    }
}
