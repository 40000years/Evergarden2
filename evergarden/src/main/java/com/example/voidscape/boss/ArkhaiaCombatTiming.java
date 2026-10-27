package com.example.voidscape.boss;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Properties;

/** Shared tick clock: the model exporter reads the same resource as the combat AI. */
public final class ArkhaiaCombatTiming {
    public record Timing(int windup,int active,int impact,int recovery,int cooldown) {}
    private static final EnumMap<ArkhaiaBrain.Action,Timing> ACTIONS=new EnumMap<>(ArkhaiaBrain.Action.class);
    public static final int STUN;
    static {
        Properties source=new Properties();
        try(var input=ArkhaiaCombatTiming.class.getResourceAsStream("/modelengine/arkhaia-combat.properties")){
            if(input==null)throw new IOException("Missing Arkhaia combat timing");
            source.load(input);
            for(var action:ArkhaiaBrain.Action.values())if(action!=ArkhaiaBrain.Action.NONE){
                String[] values=source.getProperty(action.name().toLowerCase(Locale.ROOT)).split(",");
                int windup=Integer.parseInt(values[0]),active=Integer.parseInt(values[1]),impact=Integer.parseInt(values[2]),
                    recovery=Integer.parseInt(values[3]),cooldown=Integer.parseInt(values[4]);
                if(windup<3||active<2||impact<1||impact>=active||recovery<1||cooldown<1)
                    throw new IllegalArgumentException("Invalid Arkhaia timing for "+action);
                ACTIONS.put(action,new Timing(windup,active,impact,recovery,cooldown));
            }
            ACTIONS.put(ArkhaiaBrain.Action.NONE,new Timing(1,1,0,4,0));
            STUN=Integer.parseInt(source.getProperty("stun"));
        }catch(IOException|RuntimeException error){throw new ExceptionInInitializerError(error);}
    }
    private ArkhaiaCombatTiming(){}
    public static Timing of(ArkhaiaBrain.Action action){return ACTIONS.get(action);}
}
