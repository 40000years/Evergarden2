package com.example.voidscape.boss;

import java.util.*;
import java.util.random.RandomGenerator;

/** Arkhaia's duel director: moves, posture, phases and scaled health share one tick clock. No Bukkit types. */
public final class ArkhaiaSovereign {
    public enum Move { NONE, LIGHT, HEAVY, CHARGE, RIFT, SPIN, PORTALS, ECLIPSE, SWEEP_LEFT, SWEEP_RIGHT, EXECUTE }
    public enum State { NEUTRAL, WINDUP, ACTIVE, RECOVER, STAGGER, BROKEN, TRANSITION, RITUAL }
    public enum Event { PHASE_TWO, PHASE_THREE, RITUAL, SECOND_RITUAL, RITUAL_BROKEN, RITUAL_FAILED, BROKEN, PARRIED, WALL, DUEL, ECLIPSE, LAST_STAND, DEFEATED }
    public record Sense(boolean target,double distance,boolean lineOfSight) {}
    public record Timing(int windup,int active,int impact,int recover) {}
    public record Act(State state,Move move,int age,int duration,boolean impact,int index,int count) {
        public boolean committed(){return state==State.WINDUP||state==State.ACTIVE||state==State.RECOVER;}
        public boolean duel(){return committed()&&(move==Move.SWEEP_LEFT||move==Move.SWEEP_RIGHT||move==Move.EXECUTE);}
        public boolean sword(){return move==Move.LIGHT||move==Move.HEAVY||move==Move.SWEEP_LEFT||move==Move.SWEEP_RIGHT||move==Move.EXECUTE;}
        public double progress(){return duration==0?0:Math.clamp(age/(double)duration,0,1);}
    }
    public static final int TRANSITION=70,STAGGER=36,WALL_STUN=60,BROKEN=120,RITUAL_BROKEN=160,RITUAL_LIMIT=1200;
    public static final int CHAIN_RECOVER=4,DUEL_INTERVAL=900,ECLIPSE_INTERVAL=1800;
    /** Posture fills after this share of maximum health in ordinary frontal damage. */
    public static final double POSTURE_SHARE=.14,PARRY_POSTURE=40,WALL_POSTURE=25;
    private static final EnumMap<Move,Integer> COOLDOWN=new EnumMap<>(Map.of(Move.CHARGE,160,Move.RIFT,140,Move.SPIN,200,Move.PORTALS,260));
    private final RandomGenerator random;
    private final double base,perExtra,cap;
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private final EnumMap<Move,Long> ready=new EnumMap<>(Move.class);
    private List<Move> plan=List.of();
    private State state=State.NEUTRAL;
    private Move lastChosen=Move.NONE;
    private int index,duration,phase=1,roster=1;
    private long entered,nextDecision,lastPosture,duelDue,eclipseDue;
    private double maxHealth,health,posture;
    private boolean impactEmitted,ritualPending,secondRitual,lastStand,defeated;
    public ArkhaiaSovereign(RandomGenerator random,double base,double perExtra,double cap){
        this.random=Objects.requireNonNull(random);
        this.base=Double.isFinite(base)?Math.clamp(base,100,1000000):7000;
        this.perExtra=Double.isFinite(perExtra)?Math.clamp(perExtra,0,1000000):1500;
        this.cap=Double.isFinite(cap)?Math.max(this.base,cap):Math.max(this.base,10000);
        maxHealth=health=this.base;
    }
    public static double scaledHealth(double base,double perExtra,double cap,int players){
        return Math.min(Math.max(base,cap),base+perExtra*Math.max(0,players-1));
    }
    public static Timing timing(Move move,int phase){
        return switch(move){
            case LIGHT -> new Timing(phase==3?11:13,8,4,26);
            case HEAVY -> new Timing(phase==3?20:24,12,6,44);
            case CHARGE -> new Timing(22,20,1,30);
            case RIFT -> new Timing(30,34,4,24);
            case SPIN -> new Timing(22,36,2,26);
            case PORTALS -> new Timing(36,52,6,30);
            case ECLIPSE -> new Timing(120,54,10,64);
            case SWEEP_LEFT,SWEEP_RIGHT -> new Timing(26,8,4,8);
            case EXECUTE -> new Timing(30,12,6,50);
            default -> new Timing(1,1,0,1);
        };
    }
    /** Final windup ticks in which a frontal melee hit (or a fresh shield raise at impact) parries. */
    public static int flash(Move move,int phase){return move==Move.HEAVY?(phase==3?9:10):move==Move.EXECUTE?12:0;}
    public static int gap(int phase,boolean lastStand){int gap=phase==1?16:phase==2?12:10;return lastStand?(int)Math.round(gap*.7):gap;}
    public int phase(){return phase;}
    public double health(){return health;}
    public double maxHealth(){return maxHealth;}
    public double posture(){return posture;}
    public State state(){return state;}
    public boolean lastStand(){return lastStand;}
    public boolean secondRitual(){return secondRitual;}
    public boolean invulnerable(){return state==State.TRANSITION||state==State.RITUAL;}
    public boolean broken(){return state==State.BROKEN;}
    public List<Event> drain(){var result=List.copyOf(events);events.clear();return result;}
    /** The roster only grows; current health keeps the same missing amount. */
    public void roster(int players){
        players=Math.clamp(players,1,16);if(players<=roster)return;roster=players;
        double next=scaledHealth(base,perExtra,cap,players);health+=next-maxHealth;maxHealth=next;
    }
    public double phaseStart(){return phase==1?maxHealth:phase==2?maxHealth*.65:maxHealth*.35;}
    private double floor(){return phase==1?maxHealth*.65:phase==2?maxHealth*(secondRitual?.35:.50):0;}
    /** Apply an accepted player hit; returns the health actually removed. */
    public double damage(double amount,double postureScale,long now){
        if(defeated||!Double.isFinite(amount)||amount<=0||invulnerable())return 0;
        double dealt=Math.min(Math.max(0,health-floor()),amount*(state==State.BROKEN?2:1));
        health-=dealt;
        if(state!=State.BROKEN&&dealt>0){
            posture+=dealt/maxHealth*100/POSTURE_SHARE*(Double.isFinite(postureScale)?Math.clamp(postureScale,0,4):1);lastPosture=now;
            if(posture>=100)breakStance(now,BROKEN);
        }
        if(health<=floor()+1e-9){
            if(phase==1){phase=2;posture=0;ritualPending=true;enter(State.TRANSITION,Move.NONE,now,TRANSITION);events.add(Event.PHASE_TWO);}
            else if(phase==2&&!secondRitual){secondRitual=true;posture=0;plan=List.of();enter(State.RITUAL,Move.NONE,now,RITUAL_LIMIT);events.add(Event.SECOND_RITUAL);}
            else if(phase==2){phase=3;posture=0;enter(State.TRANSITION,Move.NONE,now,TRANSITION);events.add(Event.PHASE_THREE);}
        }
        if(phase==3&&!lastStand&&health<=maxHealth*.15&&health>0){lastStand=true;events.add(Event.LAST_STAND);}
        if(health<=0&&!defeated){health=0;defeated=true;events.add(Event.DEFEATED);}
        return dealt;
    }
    private void breakStance(long now,int ticks){posture=100;plan=List.of();enter(State.BROKEN,Move.NONE,now,ticks);events.add(Event.BROKEN);}
    /** Melee during the flash, or a freshly raised shield before the impact tick. */
    public boolean parry(long now,boolean shield){
        boolean guarded=plan.size()>index&&(plan.get(index)==Move.HEAVY||plan.get(index)==Move.EXECUTE);
        if(!guarded)return false;Move move=plan.get(index);Timing timing=timing(move,phase);int age=(int)(now-entered);
        boolean window=state==State.WINDUP&&age>=timing.windup()-flash(move,phase)
            ||shield&&state==State.ACTIVE&&age<=timing.impact();
        if(!window)return false;
        events.add(Event.PARRIED);posture+=PARRY_POSTURE;lastPosture=now;
        if(move==Move.EXECUTE||posture>=100)breakStance(now,move==Move.EXECUTE?RITUAL_BROKEN:BROKEN);
        else{plan=List.of();enter(State.STAGGER,Move.NONE,now,STAGGER);}
        return true;
    }
    /** A charge that meets solid terrain stops and opens the boss. */
    public void wall(long now){
        if(state!=State.ACTIVE||plan.size()<=index||plan.get(index)!=Move.CHARGE)return;
        events.add(Event.WALL);posture+=WALL_POSTURE;lastPosture=now;plan=List.of();
        if(posture>=100)breakStance(now,BROKEN);else enter(State.STAGGER,Move.NONE,now,WALL_STUN);
    }
    public void endRitual(boolean success,long now){
        if(state!=State.RITUAL)return;
        if(success){events.add(Event.RITUAL_BROKEN);posture=100;enter(State.BROKEN,Move.NONE,now,RITUAL_BROKEN);}
        else{events.add(Event.RITUAL_FAILED);neutral(now);}
    }
    /** A wiped party restarts the current phase instead of the whole fight. */
    public void regroup(long now){
        if(defeated)return;
        health=Math.max(health,phaseStart());posture=0;plan=List.of();ready.clear();
        if(phase==2){secondRitual=false;ritualPending=true;}
        enter(State.NEUTRAL,Move.NONE,now,0);nextDecision=now+20;
    }
    public Act update(long now,Sense sense){
        if(defeated)return act(now,false);
        if(state!=State.BROKEN&&now-lastPosture>60)posture=Math.max(0,posture-.1);
        if(!sense.target()&&(state==State.NEUTRAL||state==State.WINDUP)){
            enter(State.NEUTRAL,Move.NONE,now,0);nextDecision=now+16;return act(now,false);
        }
        if(duration>0&&now-entered>=duration)advance(now);
        if(state==State.NEUTRAL&&ritualPending&&sense.target()){
            ritualPending=false;enter(State.RITUAL,Move.NONE,now,RITUAL_LIMIT);events.add(Event.RITUAL);
        }
        if(state==State.NEUTRAL&&sense.target()&&now>=nextDecision)choose(now,sense);
        boolean impact=state==State.ACTIVE&&!impactEmitted&&now-entered>=timing(plan.get(index),phase).impact();
        if(impact)impactEmitted=true;
        return act(now,impact);
    }
    private void advance(long now){
        switch(state){
            case WINDUP -> enter(State.ACTIVE,plan.get(index),now,timing(plan.get(index),phase).active());
            case ACTIVE -> {
                Move move=plan.get(index);boolean more=index+1<plan.size();
                enter(State.RECOVER,move,now,more?(move==Move.SWEEP_LEFT||move==Move.SWEEP_RIGHT?8:CHAIN_RECOVER):timing(move,phase).recover());
            }
            case RECOVER -> {
                if(index+1<plan.size()){index++;enter(State.WINDUP,plan.get(index),now,timing(plan.get(index),phase).windup());}
                else neutral(now);
            }
            case STAGGER -> neutral(now);
            case BROKEN -> {posture=0;neutral(now);}
            case TRANSITION -> {
                if(phase==3){duelDue=now+120;eclipseDue=now+600;}
                neutral(now);
            }
            case RITUAL -> {events.add(Event.RITUAL_FAILED);neutral(now);}
            default -> {}
        }
    }
    private void neutral(long now){plan=List.of();index=0;enter(State.NEUTRAL,Move.NONE,now,0);nextDecision=now+gap(phase,lastStand);}
    private void start(List<Move> moves,Move chosen,long now){
        plan=List.copyOf(moves);index=0;lastChosen=chosen;
        Integer cooldown=COOLDOWN.get(chosen);if(cooldown!=null)ready.put(chosen,now+cooldown);
        enter(State.WINDUP,plan.get(0),now,timing(plan.get(0),phase).windup());
    }
    private boolean ready(Move move,long now){return now>=ready.getOrDefault(move,0L);}
    private void choose(long now,Sense s){
        if(phase==3&&now>=duelDue&&s.distance()<=48){
            Move first=random.nextBoolean()?Move.SWEEP_LEFT:Move.SWEEP_RIGHT,second=random.nextBoolean()?Move.SWEEP_LEFT:Move.SWEEP_RIGHT;
            duelDue=now+DUEL_INTERVAL;eclipseDue=Math.max(eclipseDue,now+300);events.add(Event.DUEL);
            start(List.of(first,second,Move.EXECUTE),Move.EXECUTE,now);return;
        }
        if(phase==3&&now>=eclipseDue){
            eclipseDue=now+ECLIPSE_INTERVAL;duelDue=Math.max(duelDue,now+400);events.add(Event.ECLIPSE);
            start(List.of(Move.ECLIPSE),Move.ECLIPSE,now);return;
        }
        if(!s.lineOfSight()){nextDecision=now+4;return;}
        var weights=new EnumMap<Move,Double>(Move.class);double d=s.distance();
        if(d<=8.5){weights.put(Move.LIGHT,6.0);if(phase>1&&ready(Move.SPIN,now))weights.put(Move.SPIN,2.0);}
        else if(d<=13){
            if(ready(Move.RIFT,now))weights.put(Move.RIFT,1.5);
            if(phase>1&&ready(Move.SPIN,now))weights.put(Move.SPIN,1.0);
        }else{
            if(ready(Move.CHARGE,now))weights.put(Move.CHARGE,4.0);
            if(ready(Move.RIFT,now))weights.put(Move.RIFT,3.0);
            if(phase>1&&ready(Move.PORTALS,now))weights.put(Move.PORTALS,3.0);
        }
        if(weights.size()>1)weights.remove(lastChosen);
        double total=weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if(total<=0){nextDecision=now+4;return;}
        double roll=random.nextDouble(total);Move chosen=Move.NONE;
        for(var entry:weights.entrySet()){roll-=entry.getValue();if(roll<0){chosen=entry.getKey();break;}}
        if(chosen==Move.NONE)chosen=weights.keySet().iterator().next();
        start(chosen==Move.LIGHT?combo():List.of(chosen),chosen,now);
    }
    /** The heavy finisher always ends a string, so its long recovery stays the reward. */
    private List<Move> combo(){
        double roll=random.nextDouble();
        return switch(phase){
            case 1 -> roll<.6?List.of(Move.LIGHT,Move.LIGHT,Move.HEAVY):List.of(Move.LIGHT,Move.HEAVY);
            case 2 -> roll<.5?List.of(Move.LIGHT,Move.LIGHT,Move.HEAVY):roll<.8?List.of(Move.LIGHT,Move.LIGHT,Move.LIGHT,Move.HEAVY):List.of(Move.LIGHT,Move.SPIN);
            default -> roll<.45?List.of(Move.LIGHT,Move.LIGHT,Move.LIGHT,Move.HEAVY):roll<.75?List.of(Move.LIGHT,Move.LIGHT,Move.HEAVY):List.of(Move.LIGHT,Move.SPIN);
        };
    }
    private void enter(State state,Move move,long now,int duration){
        this.state=state;entered=now;this.duration=duration;impactEmitted=false;
        if(move==Move.NONE){plan=List.of();index=0;}
    }
    private Act act(long now,boolean impact){
        Move move=(state==State.WINDUP||state==State.ACTIVE||state==State.RECOVER)&&index<plan.size()?plan.get(index):Move.NONE;
        return new Act(state,move,(int)Math.max(0,now-entered),duration,impact,index,plan.size());
    }
    /** Ticks until the next duel and Eclipse (phase three); for admin tests. */
    public void schedule(long now,int duelIn,int eclipseIn){duelDue=now+duelIn;eclipseDue=now+eclipseIn;}
}
