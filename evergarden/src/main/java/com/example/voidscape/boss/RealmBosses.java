package com.example.voidscape.boss;

import org.bukkit.plugin.Plugin;
import java.util.*;

/** The four realm bosses that will guard the Nether and End sanctuaries. Only their rigs and
 *  the admin showcase exist so far: blueprints in modelengine/realm/, native Bedrock entities,
 *  and one shared clip order (evergarden/tools/realm_bosses.py builds all of them). */
public final class RealmBosses {
    private RealmBosses(){}
    /** Clip order of every realm blueprint; the Bedrock controller indexes this list. */
    public static final List<String> CLIPS=List.of("idle","walk","attack_windup","attack_active","attack_recover",
        "skill_windup","skill_active","skill_recover","ultimate_windup","ultimate_active","ultimate_recover","stun","transition","spawn");
    /* ModelEngine builds with a model cap import twelve models in all; the main bosses, the river rigs and
       internal_fire take eleven. All four realm rigs therefore ship in ONE merged blueprint: bones prefixed with
       the rig's code, clips with its model id, one texture each. A spawned rig drops the other rigs' bones. */
    public static final String BLUEPRINT="realm_bosses";
    public enum Kind {
        CINDER_COLOSSUS("cinder_colossus","cc","Cinder Colossus","Nether · ป้อมมงกุฎลาวา"),
        SOUL_HOUND("soul_hound","sh","Soul-Eater Hound","Nether · วิหารกระดูกวิญญาณ"),
        CRYSTAL_MOTH("crystal_moth","cm","Crystal Moth Queen","The End · นครคริสตัลร้าง"),
        UNLIT_SUN("unlit_sun","us","Unlit Sun","The End · วิหารวงแหวนสุญญากาศ");
        public final String model,code,title,home;
        Kind(String model,String code,String title,String home){this.model=model;this.code=code;this.title=title;this.home=home;}
        public static Kind of(String model){for(Kind k:values())if(k.model.equalsIgnoreCase(model))return k;return null;}
    }
    public static final List<String> MODELS=Arrays.stream(Kind.values()).map(k->k.model).toList();
    public static boolean realm(String model){return MODELS.contains(model);}
    /** Whether a bone of the merged blueprint belongs to this rig: its code prefix, or its head tracker, which the
     *  blueprint names hi_&lt;code&gt; and ModelEngine registers as &lt;code&gt; once it strips the behaviour prefix. */
    public static boolean owns(String model,String bone){
        Kind kind=Kind.of(model);
        return kind!=null&&(bone.startsWith(kind.code+"_")||bone.equals("hi_"+kind.code)||bone.equals(kind.code));
    }
    /** The merged blueprint is installed unless the admin frees its slot (realm-bosses.model-preview.enabled: false). */
    public static boolean enabled(Plugin plugin){return plugin.getConfig().getBoolean("realm-bosses.model-preview.enabled",true);}
    /** Realm rigs ModelEngine imports: all four, or none when disabled. */
    public static List<String> installed(Plugin plugin){return enabled(plugin)?MODELS:List.of();}
}
