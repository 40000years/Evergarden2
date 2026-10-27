package com.example.voidscape.boss;

import java.util.*;
import java.util.random.RandomGenerator;

/** One combat clock: long committed casts and stationary rear openings. */
public final class ArkhaiaBrain {
    public enum State { IDLE, ORBIT, WINDUP, ACTIVE, RECOVER, BACKSTEP, STUN, RETREAT, TRANSITION }
    public enum Action { NONE, LIGHT, HEAVY, RIFT, NOVA, PORTALS, ECLIPSE }
    public record Sense(boolean target,double distance,double healthFraction,boolean lineOfSight) {}
    public record Frame(State state,Action action,int age,int duration,boolean impact) {
        public double progress(){return duration==0?0:Math.clamp(age/(double)duration,0,1);}
        public boolean committed(){return state==State.WINDUP||state==State.ACTIVE||state==State.RECOVER;}
        public boolean magic(){return action==Action.RIFT||action==Action.NOVA||action==Action.PORTALS||action==Action.ECLIPSE;}
    }
    private record Hit(long tick,double damage) {}
    private final RandomGenerator random;
    private final double staggerThreshold;
    private final EnumMap<Action,Long> ready=new EnumMap<>(Action.class);
    private final ArrayDeque<Hit> hits=new ArrayDeque<>();
    private State state=State.IDLE;
    private Action action=Action.NONE,lastChosen=Action.NONE;
    private long entered,nextDecision,staggerReady;
    private int duration,phase=1,magicStreak;
    private boolean pendingStagger,impactEmitted;
    public ArkhaiaBrain(RandomGenerator random,double staggerThreshold){
        this.random=Objects.requireNonNull(random);
        this.staggerThreshold=Double.isFinite(staggerThreshold)?Math.clamp(staggerThreshold,50,2000):350;
    }
    public long cooldown(Action action,long now){return Math.max(0,ready.getOrDefault(action,0L)-now);}
    public int phase(){return phase;}
    public void phaseTwo(long now){
        if(phase==2)return;phase=2;pendingStagger=false;hits.clear();ready.clear();
        enter(State.TRANSITION,Action.NONE,now,70);staggerReady=now+100;
    }
    public void hit(double damage,long now){
        if(!Double.isFinite(damage)||damage<=0||now<staggerReady||state==State.STUN||state==State.TRANSITION)return;
        hits.addLast(new Hit(now,damage));while(!hits.isEmpty()&&now-hits.peekFirst().tick()>20)hits.removeFirst();
        if(damage>=staggerThreshold||hits.stream().mapToDouble(Hit::damage).sum()>=staggerThreshold*1.7)pendingStagger=true;
    }
    public Frame update(long now,Sense sense){
        if(!sense.target()){
            enter(State.IDLE,Action.NONE,now,0);pendingStagger=false;hits.clear();nextDecision=now+16;return frame(now,false);
        }
        if(phase==1&&sense.healthFraction()<=.5)phaseTwo(now);
        // Finish promised pulses. Recovery is already a longer opening than a
        // short stagger, so incoming hits must never shorten that opening.
        if(pendingStagger&&(state==State.ORBIT||state==State.WINDUP)){
            enter(State.STUN,Action.NONE,now,ArkhaiaCombatTiming.STUN);pendingStagger=false;hits.clear();staggerReady=now+180;
        }
        if(state==State.IDLE){enter(State.ORBIT,Action.NONE,now,0);nextDecision=Math.max(nextDecision,now+16);}
        if(duration>0&&now-entered>=duration){
            switch(state){
                case WINDUP -> enter(State.ACTIVE,action,now,ArkhaiaCombatTiming.of(action).active());
                case ACTIVE -> enter(State.RECOVER,action,now,ArkhaiaCombatTiming.of(action).recovery());
                case STUN,TRANSITION -> enter(State.RECOVER,Action.NONE,now,40);
                default -> {pendingStagger=false;hits.clear();orbit(now);}
            }
        }
        if(state==State.ORBIT&&now>=nextDecision){
            nextDecision=now+6;Action selected=choose(sense,now);
            if(selected!=Action.NONE){
                ready.put(selected,now+ArkhaiaCombatTiming.of(selected).cooldown());lastChosen=selected;
                magicStreak=selected==Action.LIGHT||selected==Action.HEAVY?0:magicStreak+1;
                enter(State.WINDUP,selected,now,ArkhaiaCombatTiming.of(selected).windup());
            }
        }
        boolean impact=state==State.ACTIVE&&!impactEmitted&&now-entered>=ArkhaiaCombatTiming.of(action).impact();
        if(impact)impactEmitted=true;return frame(now,impact);
    }
    private Action choose(Sense s,long now){
        if(!s.lineOfSight()||s.distance()>84)return Action.NONE;
        var weights=new EnumMap<Action,Double>(Action.class);
        for(var a:List.of(Action.RIFT,Action.NOVA,Action.PORTALS,Action.ECLIPSE))if(cooldown(a,now)==0&&(a!=Action.NOVA||s.distance()<22))
            weights.put(a,switch(a){case RIFT->4.0;case PORTALS->5.0;case NOVA->s.distance()<22?4.0:1.0;default->2.0;});
        if(phase==2&&magicStreak>=3&&s.distance()<=7.5&&cooldown(Action.LIGHT,now)==0)weights.put(Action.LIGHT,1.0);
        if(weights.size()>1)weights.remove(lastChosen);
        double total=weights.values().stream().mapToDouble(Double::doubleValue).sum();if(total==0)return Action.NONE;
        double roll=random.nextDouble(total);for(var e:weights.entrySet()){roll-=e.getValue();if(roll<0)return e.getKey();}return Action.NONE;
    }
    private void orbit(long now){enter(State.ORBIT,Action.NONE,now,0);nextDecision=now+16+random.nextInt(9);}
    private void enter(State state,Action action,long now,int duration){this.state=state;this.action=action;this.entered=now;this.duration=duration;impactEmitted=false;}
    private Frame frame(long now,boolean impact){return new Frame(state,action,(int)Math.max(0,now-entered),duration,impact);}
}
