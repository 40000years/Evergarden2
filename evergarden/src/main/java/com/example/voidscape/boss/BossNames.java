package com.example.voidscape.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.text.BreakIterator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One menacing look per boss, shared by health bars, nameplates and "killed by" messages:
 * an icon on both sides, the name in a bold two-colour gradient and its Thai title in an accent colour.
 * Bosses are recognised by their plain name (the same string the death attribution already records), so a
 * name this table does not know still gets the generic blood-red look.
 * <p>Java shows every gradient step; Geyser maps each hex colour to the nearest Bedrock colour, so Bedrock players
 * see the same name in a few bands. Thai is coloured per grapheme cluster so tone marks stay on their letter.
 */
public final class BossNames {
    private BossNames(){}

    private record Look(String key,String name,String title,String icon,int from,int to,int accent){}

    // Longer keys first where one contains another (Acheron Echo before Acheron).
    private static final List<Look> LOOKS=List.of(
        new Look("VHAROK","VHAROK","มังกรเพลิงโลกันตร์","✹",0xFF2400,0xFFB300,0xFF7A3D),
        new Look("VAELTHYR","VAELTHYR","มังกรอัสนีเวหา","⚡",0x9D4EFF,0xFFE45C,0xD7B8FF),
        new Look("GLACERYN","GLACERYN","มังกรเหมันต์นิรันดร์","❄",0xF0FDFF,0x2E9BFF,0x9BE6FF),
        new Look("Cinder Colossus","Cinder Colossus","ป้อมปราการมีชีวิต","♨",0xFF3D00,0xFFC400,0xFF8A4C),
        new Look("Soul-Eater Hound","Soul-Eater Hound","นักล่ากระดูกวิญญาณ","☠",0x7DF9FF,0x3D7BFF,0x52E5FF),
        new Look("Crystal Moth Queen","Crystal Moth Queen","ราชินีผลึกแห่งนครร้าง","❖",0xFF8CF5,0x8A3CFF,0xF28CFF),
        new Look("Unlit Sun","Unlit Sun","ดวงอาทิตย์ดับเหนือวงแหวน","☀",0xFFE680,0xC2410C,0xFFD257),
        new Look("Shadow Overlord","Shadow Overlord","จอมมารแห่งความมืด","☠",0xE07BFF,0x9B30FF,0xB57BFF),
        new Look("Astral Archon","Astral Archon","อัครเทวทูตดวงดาว","✦",0x8FF0FF,0xB07CFF,0xC9E8FF),
        new Look("Chronos Vanguard","Chronos Vanguard","ผู้พิทักษ์กาลเวลา","⚙",0xFFE08A,0xB86E00,0xFFCF70),
        new Look("SERAPHON","SERAPHON","ผู้พิทักษ์บัญชาสวรรค์","✦",0xFFFFFF,0xFFC93C,0xFFE9A8),
        new Look("ผู้พิพากษาไร้ร่าง","ผู้พิพากษาไร้ร่าง",null,"⚖",0xE3C6FF,0xA35BFF,0),
        new Look("Arkhaia","Arkhaia",null,"⚔",0xE8C0FF,0xA356F0,0),
        new Look("Malefeasance","Malefeasance",null,"✧",0xFF7ADB,0x9040FF,0),
        new Look("Acheron Echo","Acheron Echo",null,"☠",0xC8F8DA,0x44B87A,0),
        new Look("Acheron","Acheron",null,"☠",0xA8F0C4,0x38B26E,0),
        new Look("Phlegethon","Phlegethon",null,"♨",0xFF4D2E,0xFFA53C,0),
        new Look("Cocytus","Cocytus",null,"❄",0xD9F6FF,0x3F8CFF,0),
        new Look("Banshee","Banshee Wail",null,"☽",0xE6E6FF,0x8C7CFF,0),
        new Look("Styx","Styx",null,"☠",0xC4C4FF,0x7B6CFF,0));
    private static final Look FALLBACK=new Look("","",null,"☠",0xFF4040,0x9B0000,0);
    private static final Pattern TAG=Pattern.compile("^(\\[[^\\]]+\\])\\s*");
    private static final LegacyComponentSerializer LEGACY=LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static Look find(String plain){
        String lower=plain.toLowerCase(Locale.ROOT);
        for(Look look:LOOKS)if(lower.contains(look.key().toLowerCase(Locale.ROOT)))return look;
        return null;
    }
    /** True when the table has a look for this boss name. */
    public static boolean known(String plain){return plain!=null&&find(plain)!=null;}

    /** The styled name for a boss's plain name; a leading tag such as "[Tier V]" is kept in front. */
    public static Component styled(String plain){
        if(plain==null||plain.isBlank())return Component.empty();
        String rest=plain.strip(),prefix=null;
        Matcher tag=TAG.matcher(rest);
        if(tag.find()){prefix=tag.group(1);rest=rest.substring(tag.end());}
        Look look=find(rest);
        if(look==null)look=new Look("",rest,null,FALLBACK.icon(),FALLBACK.from(),FALLBACK.to(),0);
        TextComponent.Builder out=Component.text();
        if(prefix!=null)out.append(Component.text(prefix+" ",NamedTextColor.GRAY));
        TextColor edge=TextColor.color(look.from());
        out.append(Component.text(look.icon()+" ",edge));
        out.append(gradient(look.name(),look.from(),look.to()));
        out.append(Component.text(" "+look.icon(),TextColor.color(look.to())));
        if(look.title()!=null)
            out.append(Component.text(" · ",NamedTextColor.DARK_GRAY)).append(Component.text(look.title(),TextColor.color(look.accent())));
        return out.build();
    }
    /** The styled name as a legacy string (§x hex colours) for Bukkit boss bars and chat, ending in a reset. */
    public static String legacy(String plain){return LEGACY.serialize(styled(plain))+"§r";}

    /** A bold gradient over Latin text; Thai stays regular weight and is coloured per grapheme cluster. */
    static Component gradient(String text,int from,int to){
        List<String> clusters=new ArrayList<>();
        BreakIterator it=BreakIterator.getCharacterInstance(Locale.forLanguageTag("th"));it.setText(text);
        for(int start=it.first(),end=it.next();end!=BreakIterator.DONE;start=end,end=it.next())clusters.add(text.substring(start,end));
        boolean thai=text.codePoints().anyMatch(c->c>=0x0E00&&c<=0x0E7F);
        TextComponent.Builder out=Component.text();
        int steps=Math.max(1,clusters.size()-1);
        for(int i=0;i<clusters.size();i++){
            Component part=Component.text(clusters.get(i),TextColor.color(mix(from,to,i/(double)steps)));
            if(!thai)part=part.decorate(TextDecoration.BOLD);
            out.append(part);
        }
        return out.build();
    }
    private static int mix(int from,int to,double t){
        int r=(int)Math.round(((from>>16)&255)+(((to>>16)&255)-((from>>16)&255))*t);
        int g=(int)Math.round(((from>>8)&255)+(((to>>8)&255)-((from>>8)&255))*t);
        int b=(int)Math.round((from&255)+((to&255)-(from&255))*t);
        return (r<<16)|(g<<8)|b;
    }
}
