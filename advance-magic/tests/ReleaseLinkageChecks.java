import com.example.advancemagic.spell.MagicContext;
import com.example.advancemagic.spell.MythicSpells;

/** Run against release JARs, not IDE target/classes. No running server is needed. */
public final class ReleaseLinkageChecks {
    public static void main(String[] args)throws Exception {
        // Reproduces the constructor failure reported during plugin onEnable.
        new MythicSpells(new MagicContext(null));
        for(String name:new String[]{"SolarLineVisuals","ChronosLineVisuals","MythicLineVisuals",
                "JudgmentSealVisuals","JudgmentBeamVisuals"}) {
            var type=Class.forName("com.example.advancemagic.spell."+name,false,ReleaseLinkageChecks.class.getClassLoader());
            type.getDeclaredConstructors();type.getDeclaredMethods();
        }
        Class.forName("com.example.advancemagic.effect.EffectEngine$Effect",false,ReleaseLinkageChecks.class.getClassLoader());
        Class.forName("com.example.advancemagic.api.MagicCastEvent",false,ReleaseLinkageChecks.class.getClassLoader()).getDeclaredMethods();
        Class.forName("com.example.voidscape.crop.CropBuffListener",false,ReleaseLinkageChecks.class.getClassLoader()).getDeclaredMethods();
        System.out.println("PASS: MythicSpells constructor, five effect classes and Evergarden MagicCastEvent linkage");
    }
}
