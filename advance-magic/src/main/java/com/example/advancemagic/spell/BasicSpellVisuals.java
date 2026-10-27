package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Bounded accents plus small pieces of the existing celestial artwork. */
final class BasicSpellVisuals implements AutoCloseable {
    private final MagicContext c;
    private final Spell spell;
    private final Location origin;
    private final Vector forward, right, up;
    private final Color color;
    private final MythicVisuals viewers;
    private final Map<String, MythicLineVisuals> art = new LinkedHashMap<>();
    private int budget;
    private Location lastImpact,beamEnd,judgmentBase;
    private int beamLife,impactLife;

    BasicSpellVisuals(MagicContext c, Spell spell, Location origin, Vector forward) {
        this.c = c; this.spell = spell; this.origin = origin.clone(); this.forward = forward.clone();
        color = Color.fromRGB(spell==Spell.SHULKER_LEVITATION?0xA36BFF:spell.color);
        Vector side = forward.clone().crossProduct(new Vector(0, 1, 0));
        right = side.lengthSquared() < .001 ? new Vector(1, 0, 0) : side.normalize();
        up = right.clone().crossProduct(forward).normalize();
        viewers = new MythicVisuals(c);
    }

    void beginFrame(Location position) {
        viewers.frame(position);
        budget = Math.clamp(c.plugin.getConfig().getInt("basic-casting.particle-budget-per-frame", 48), 16, 96);
    }

    void entrance() {
        Sound sound = switch (spell) {
            case BLAZE_BARRAGE, DRAGONS_BREATH, METEOR_STRIKE, SOLAR_APOCALYPSE -> Sound.ITEM_FIRECHARGE_USE;
            case FROST_NOVA, CHRONOS_FINAL_HOUR, LUNAR_REQUIEM -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case SHADOW_STEP, VOID_PULL, WITHER_RAY, SHULKER_LEVITATION -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case EARTH_WALL, IRON_ARMOR -> Sound.BLOCK_STONE_BREAK;
            case NATURES_BLOOM, VERDANT_SOVEREIGN -> Sound.BLOCK_CHERRY_SAPLING_PLACE;
            case VEX_LEGION -> Sound.ENTITY_VEX_CHARGE;
            case SONIC_BOOM -> Sound.ENTITY_WARDEN_ATTACK_IMPACT;
            case GUARDIAN_BEAM -> Sound.ENTITY_GUARDIAN_ATTACK;
            case CRIMSON_OBLIVION -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            case HEAVENS_JUDGMENT -> Sound.BLOCK_BEACON_POWER_SELECT;
            case LIGHTNING_STRIKE -> Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
        };
        origin.getWorld().playSound(origin, sound, spell.isMythic() ? .65f : .35f, 1.6f);
    }

    void bolt(EffectEngine.Effect effect, Location from, Location to, int age) {
        line(from, to, spell.isMythic() ? 1.1f : .8f, 5);
        sparks(to, accent(), 2, .08);
        if (spell == Spell.FROST_NOVA) sparks(to, Particle.SNOWFLAKE, 2, .12);
        if (spell == Spell.CHRONOS_FINAL_HOUR && age % 4 == 0) ring(to, .28, right, up, age * .2, 10);
    }

    void discharge(EffectEngine.Effect effect, Location from, Location to, int elapsed) {
        if (elapsed % 2 != 0 || elapsed > 6) return;
        if (BasicSpellProfile.defaults(spell).shape() == BasicSpellProfile.Shape.BEAM) {
            line(from, to, spell.isMythic() ? 1.05f : .65f, 22);
            if (spell == Spell.LIGHTNING_STRIKE) sparks(to, Particle.ELECTRIC_SPARK, 3, .12);
        }
    }

    void pulse(Location center, double radius) {
        ring(center, radius, new Vector(1, 0, 0), new Vector(0, 0, 1), 0, 20);
        sparks(center, spell == Spell.EARTH_WALL ? Particle.CLOUD : Particle.CRIT, 3, radius / 3);
    }

    void impact(Location at) {
        lastImpact=at.clone();impactLife=0;
        sparks(at, accent(), spell.isMythic() ? 10 : 6, .22);
        ring(at, spell.isLegendary() ? .8 : .4, right, up, 0, 12);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, .4f, 1.5f);
    }

    void signature(EffectEngine.Effect effect, Location position, Location end, int age, int impactAge) {
        if (!spell.isMythic() || age % 2 != 0) return;
        double fade = impactAge < 0 ? 1 : Math.max(0, 1 - (age - impactAge) / 10.0);
        if (spell == Spell.VERDANT_SOVEREIGN) {
            Location sigil = origin.clone().subtract(forward.clone().multiply(1.25)).add(0, .65, 0);
            plane(effect, "dragon", "verdant_dragon", sigil, 2.8 * fade, 2.8 * fade, 0);
            plane(effect, "halo", "verdant_halo", sigil.clone().subtract(forward.clone().multiply(.05)),
                    3.25 * fade, 3.25 * fade, -age * .08);
            ray(effect, "breath", "verdant_ray", origin, end, .45 * fade);
            if (viewers.needsSealFallback()) ring(sigil, 1.4 * fade, right, up, age * .1, 20);
            Location wave = origin.clone().add(end.toVector().subtract(origin.toVector()).multiply(Math.min(1, age / 8.0)));
            ring(wave, .45 * fade, right, up, age * .12, 12);
        } else if (spell == Spell.LUNAR_REQUIEM) {
            for (int i = 0; i < 3; i++) {
                Location moon = origin.clone().add(right.clone().multiply((i - 1) * 1.15))
                        .add(up.clone().multiply(.85)).add(forward.clone().multiply(.75));
                plane(effect, "moon-" + i, "lunar_seal", moon, .85 * fade, .85 * fade, 0);
                plane(effect, "orbit-" + i, "lunar_orbit", moon.clone().subtract(forward.clone().multiply(.04)),
                        1.03 * fade, 1.03 * fade, age * .12 * (i == 1 ? -1 : 1));
                if (c.clear(moon, end)) ray(effect, "ray-" + i, "lunar_ray", moon, end, .16 * fade);
                if (viewers.needsSealFallback()) ring(moon, .45 * fade, right, up, age * .1, 10);
            }
        } else if (spell == Spell.CRIMSON_OBLIVION) {
            Location center = impactAge < 0 ? position : end;
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2 / 3 + age * .22;
                Location shard = center.clone().add(right.clone().multiply(Math.cos(angle) * .38 * fade))
                        .add(up.clone().multiply(Math.sin(angle) * .38 * fade));
                plane(effect, "shard-" + i, "crimson_shard", shard, .65 * fade, 1.25 * fade, angle);
                if (viewers.needsSealFallback()) line(shard.clone().subtract(up.clone().multiply(.5 * fade)),
                        shard.clone().add(up.clone().multiply(.5 * fade)), .9f, 5);
            }
            if (impactAge >= 0) plane(effect, "impact", "crimson_seal", end, 2.4 * (1 - fade) + .5,
                    2.4 * (1 - fade) + .5, age * .08);
        } else if (spell == Spell.SOLAR_APOCALYPSE) {
            Location sun = origin.clone().add(right.clone().multiply(1.2)).add(up.clone().multiply(.7));
            plane(effect, "sun", "solar_corona", sun, 1.5 * fade, 1.5 * fade, age * .08);
            plane(effect, "orbit", "solar_orbit", sun.clone().subtract(forward.clone().multiply(.04)),
                    1.9 * fade, 1.9 * fade, -age * .1);
            ray(effect, "sun-ray", "solar_ray", origin, end, .3 * fade);
            if (viewers.needsSealFallback()) ring(sun, .8 * fade, right, up, age * .1, 16);
        } else if (spell == Spell.CHRONOS_FINAL_HOUR) {
            plane(effect, "clock", "chronos_dial", position, .9 * fade, .9 * fade, 0);
            plane(effect, "hand", "chronos_minute", position.clone().subtract(forward.clone().multiply(.04)),
                    .9 * fade, .9 * fade, age * .3);
            if (viewers.needsSealFallback()) ring(position, .45 * fade, right, up, age * .2, 12);
        } else if (spell == Spell.HEAVENS_JUDGMENT) {
            ring(origin.clone().add(up.clone().multiply(.9)), .65 * fade, right, up, age * .1, 16);
            ring(end, .85 * fade, new Vector(1, 0, 0), new Vector(0, 0, 1), -age * .1, 16);
            sparks(end, Particle.END_ROD, 4, .25);
        } else {
            // Shulker's basic attack has an orbiting portal, without repeated levitation or a terrain zone.
            ring(position, .45 * fade, right, up, age * .25, 16);
            sparks(position, Particle.PORTAL, 3, .2);
        }
    }

    void flight(EffectEngine.Effect effect,int index,Location from,Location to,Vector direction,int age,double lane) {
        String key="flight-"+index+"-";
        if(spell==Spell.CRIMSON_OBLIVION){bolt(effect,from,to,age);return;}
        if(spell==Spell.VERDANT_SOVEREIGN){
            plane(effect,key+"wave","verdant_halo",to,1.45,1.45,age*.16+index);
            plane(effect,key+"core","verdant_beam_core",to,.55,.55,0);
            Location tail=to.clone().subtract(direction.clone().multiply(Math.min(1.8,to.distance(origin))));
            if(c.clear(tail,to))ray(effect,key+"ray","verdant_ray",tail,to,.28);
            ring(to,.35,right,up,age*.2,8);
        }else if(spell==Spell.LUNAR_REQUIEM){
            Location tail=to.clone().subtract(direction.clone().multiply(Math.min(2.0,to.distance(origin))));
            if(c.clear(tail,to))ray(effect,key+"needle","lunar_ray",tail,to,.22);
            sparks(to,Particle.END_ROD,3,.09);line(from,to,.8f,5);
        }else if(spell==Spell.CHRONOS_FINAL_HOUR){
            Location tail=to.clone().subtract(direction.clone().multiply(Math.min(1.4,to.distance(origin))));
            if(c.clear(tail,to))ray(effect,key+"needle",index==0?"chronos_ray":"chronos_echo",tail,to,.27);
            ring(to,.35,right,up,(index==0?1:-1)*age*.2,10);
        }else if(spell==Spell.SHULKER_LEVITATION){
            double turn=age*.12;
            for(int side=0;side<2;side++){
                double angle=turn+side*Math.PI/2;
                Vector axis=right.clone().multiply(Math.cos(angle)).add(forward.clone().multiply(Math.sin(angle)));
                planeOn(effect,key+"prism-"+side,"shulker_prism",to,axis,up,1.25,1.25,0);
            }
            cube(to,.48,age*.18);
            ring(to,.7,right,up,age*.16,12);
            ring(to,.65,right,forward,-age*.2,12);
            sparks(to,Particle.PORTAL,3,.15);
        }else if(spell==Spell.FROST_NOVA){
            Location tip=to.clone().add(direction.clone().multiply(.25));
            for(int side:new int[]{-1,1}){
                Location root=to.clone().subtract(direction.clone().multiply(.65)).add(right.clone().multiply(side*.13));
                line(root,tip,.65f,5);
            }
            sparks(to,Particle.SNOWFLAKE,2,.08);
        }else if(spell==Spell.VOID_PULL){
            ring(to,.4,right,up,age*.18,12);
            ring(to,.4,right,forward,-age*.18,12);
            sparks(to,Particle.REVERSE_PORTAL,4,.16);
        }else if(spell==Spell.WITHER_RAY){
            ring(to,.22,right,up,age*.1,10);
            line(from,to,.95f,5);sparks(to,Particle.SOUL_FIRE_FLAME,3,.1);
        }else if(spell==Spell.VEX_LEGION){
            line(from,to,1.15f,6);
            ring(to,.22,right,up,age*.15,8);sparks(to,Particle.SOUL,2,.06);
        }else if(spell==Spell.NATURES_BLOOM){
            ring(to,.18,right,up,age*.4,8);line(from,to,.7f,5);sparks(to,Particle.HAPPY_VILLAGER,2,.08);
        }else if(spell==Spell.METEOR_STRIKE){
            ring(to,.3,right,up,age*.16,10);line(from,to,1.15f,6);sparks(to,Particle.FLAME,4,.13);
        }else{
            line(from,to,.85f,5);sparks(to,Particle.FLAME,4,.12);
            ring(to,.16,right,up,age*.3,8);
        }
    }

    void endShot(int index){
        String prefix="flight-"+index+"-";
        art.forEach((key,piece)->{if(key.startsWith(prefix))piece.hide();});
    }

    void beam(EffectEngine.Effect effect,Location from,Location to,int chargeAge){
        beamEnd=to.clone();beamLife=0;
        if(spell==Spell.SOLAR_APOCALYPSE)ray(effect,"solar-discharge","solar_ray",from,to,.55);
        else if(spell==Spell.LIGHTNING_STRIKE){
            Vector delta=to.toVector().subtract(from.toVector());
            Location previous=from.clone();
            for(int i=1;i<=8;i++){
                Location point=from.clone().add(delta.clone().multiply(i/8.0));
                if(i<8)point.add(right.clone().multiply((i%2==0?1:-1)*.22));
                line(previous,point,1.0f,3);previous=point;
            }
            sparks(to,Particle.ELECTRIC_SPARK,4,.15);
        }else{
            line(from,to,.85f,22);sparks(to,Particle.BUBBLE_POP,4,.12);
        }
    }

    void wave(Location front,double radius,int age){
        ring(front,radius,right,up,age*.05,16);
        ring(front.clone().subtract(forward.clone().multiply(.55)),radius*.8,right,up,-age*.06,12);
        sparks(front,Particle.SONIC_BOOM,1,0);
    }

    void cone(Location at,double length,int age){
        for(int ring=1;ring<=3;ring++){
            double distance=length*ring/3;
            Location center=at.clone().add(forward.clone().multiply(distance));
            ring(center,.15+distance*.45,right,up,age*.2,10);
            sparks(center,Particle.DRAGON_BREATH,2,.18);
        }
    }

    void slash(Location at,int age){
        double swing=-1.0+age*.25;
        Location center=at.clone().add(forward.clone().multiply(2.2));
        Location previous=null;
        for(int i=0;i<18;i++){
            double angle=swing+(i-9)*.06;
            Location point=center.clone().add(right.clone().multiply(Math.sin(angle)*2))
                    .add(forward.clone().multiply(Math.cos(angle)*1.6)).add(up.clone().multiply(Math.sin(angle)*.4));
            if(previous!=null)line(previous,point,1.1f,2);previous=point;
        }
        if(age==3)sparks(center,Particle.SWEEP_ATTACK,1,0);
    }

    void ground(Location at,int age){
        for(int side:new int[]{-1,1}){
            Location root=at.clone().add(right.clone().multiply(side*.3));
            line(root,at.clone().add(0,.75,0),1.0f,5);
        }
        int emitted=Math.min(6,budget);
        if(emitted>0&&c.loaded(at)){
            budget-=emitted;
            at.getWorld().spawnParticle(Particle.BLOCK,at,emitted,.35,.15,.35,.05,Material.DEEPSLATE.createBlockData());
        }
    }

    void judgment(EffectEngine.Effect effect,Location base,double strength){
        judgmentBase=base.clone();
        var obstruction=base.getWorld().rayTraceBlocks(base.clone().add(0,.1,0),new Vector(0,1,0),4,FluidCollisionMode.NEVER,true);
        double height=obstruction==null?3.8:Math.max(.3,base.distance(obstruction.getHitPosition().toLocation(base.getWorld()))-.1);
        Location top=base.clone().add(0,height,0);
        if(!c.loaded(top))return;
        for(int layer=0;layer<2;layer++){
            var seal=piece(effect,"judgment-seal-"+layer,"judgment_seal_"+layer,top.clone().add(0,-layer*.28,0));
            if(seal!=null)seal.horizontalJudgment(viewers,top.clone().add(0,-layer*.28,0),2.2-layer*.4,impactLife*.1*(layer==0?1:-1),layer);
        }
        var beam=piece(effect,"judgment-column","judgment_beam",base);
        if(beam!=null)beam.verticalJudgment(viewers,base,height,.55*strength);
        if(viewers.needsSealFallback()){
            ring(top,1.1,right,forward,0,16);
            line(base,top,1.2f,16);
        }
    }

    void castSignature(EffectEngine.Effect effect,Location aim,int age,int finishAge){
        if(beamEnd!=null&&beamLife++<7&&age%2==0){
            if(spell==Spell.SOLAR_APOCALYPSE)ray(effect,"solar-discharge","solar_ray",origin,beamEnd,.55*(1-beamLife/9.0));
            else if(spell==Spell.GUARDIAN_BEAM)line(origin,beamEnd,.85f,20);
        }
        if(beamLife>=7&&spell==Spell.SOLAR_APOCALYPSE&&art.containsKey("solar-discharge"))art.get("solar-discharge").hide();
        if(lastImpact!=null&&spell==Spell.VOID_PULL&&impactLife++<10)
            ring(lastImpact,Math.max(.12,1.25-impactLife*.12),right,up,age*.2,16);
        if(!spell.isMythic()||age%2!=0)return;
        double fade=finishAge<0?1:Math.max(0,1-(age-finishAge)/12.0);
        double distance=Math.min(3.0,Math.max(.4,origin.distance(aim)-.4));
        Location gate=origin.clone().add(forward.clone().multiply(distance));
        if(spell==Spell.VERDANT_SOVEREIGN){
            gate.add(right.clone().multiply(-.9)).add(up.clone().multiply(.25));
            plane(effect,"dragon-front","verdant_dragon",gate,2.45*fade,2.45*fade,0);
            planeOn(effect,"dragon-orbit","verdant_halo",gate.clone().subtract(forward.clone().multiply(.06)),right,
                    up.clone().multiply(.85).add(forward.clone().multiply(.53)).normalize(),2.95*fade,2.95*fade,-age*.08);
            if(viewers.needsSealFallback())ring(gate,1.25*fade,right,up,age*.1,18);
        }else if(spell==Spell.LUNAR_REQUIEM){
            gate.add(right.clone().multiply(.95)).add(up.clone().multiply(.15));
            plane(effect,"lunar-gate","lunar_seal",gate,2.0*fade,2.0*fade,0);
            planeOn(effect,"lunar-orbit","lunar_orbit",gate.clone().subtract(forward.clone().multiply(.07)),right,
                    up.clone().multiply(.85).add(forward.clone().multiply(.53)).normalize(),2.65*fade,2.65*fade,age*.12);
            planeOn(effect,"lunar-stars","lunar_constellation",gate,right.clone().multiply(.8).add(forward.clone().multiply(.6)).normalize(),
                    up,2.9*fade,2.9*fade,-age*.09);
            if(viewers.needsSealFallback())ring(gate,1.0*fade,right,up,age*.1,18);
        }else if(spell==Spell.SOLAR_APOCALYPSE){
            gate.add(right.clone().multiply(.65)).add(up.clone().multiply(.2));
            double growth=Math.min(1,.25+age/10.0)*fade;
            plane(effect,"solar-core","solar_corona",gate,1.8*growth,1.8*growth,age*.1);
            planeOn(effect,"solar-ring-a","solar_orbit",gate,right,up.clone().multiply(.75).add(forward.clone().multiply(.66)).normalize(),
                    2.45*growth,2.45*growth,age*.12);
            planeOn(effect,"solar-ring-b","solar_orbit",gate,right.clone().multiply(.75).add(forward.clone().multiply(.66)).normalize(),
                    up,2.65*growth,2.65*growth,-age*.1);
            if(viewers.needsSealFallback())ring(gate,.95*growth,right,up,age*.15,20);
        }else if(spell==Spell.CHRONOS_FINAL_HOUR){
            gate.add(right.clone().multiply(-.7));
            plane(effect,"clock-gate","chronos_dial",gate,1.8*fade,1.8*fade,0);
            plane(effect,"clock-minute","chronos_minute",gate.clone().subtract(forward.clone().multiply(.05)),1.8*fade,1.8*fade,age*.2);
            plane(effect,"clock-hour","chronos_hour",gate.clone().subtract(forward.clone().multiply(.08)),1.8*fade,1.8*fade,-age*.09);
            if(viewers.needsSealFallback())ring(gate,.9*fade,right,up,age*.1,18);
        }else if(spell==Spell.HEAVENS_JUDGMENT){
            Location base=judgmentBase;
            if(base==null){
                var floor=aim.getWorld().rayTraceBlocks(aim.clone().add(0,.3,0),new Vector(0,-1,0),3,FluidCollisionMode.NEVER,true);
                base=floor==null?aim.clone():floor.getHitPosition().toLocation(aim.getWorld()).add(0,.1,0);
            }
            if(age<8){
                Location top=base.clone().add(0,3.8,0);
                if(c.loaded(top)){
                    var seal=piece(effect,"judgment-seal-0","judgment_seal_0",top);
                    if(seal!=null)seal.horizontalJudgment(viewers,top,1.2+age*.12,age*.1,0);
                    ring(base,.65,right,forward,age*.1,12);
                }
            }else judgment(effect,base,Math.max(.08,fade));
        }
    }

    private void cube(Location center,double radius,double angle){
        List<Location> corners=new ArrayList<>();
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
            Vector offset=right.clone().multiply(x*radius).add(up.clone().multiply(y*radius)).add(forward.clone().multiply(z*radius));
            offset.rotateAroundAxis(forward,angle);corners.add(center.clone().add(offset));
        }
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)line(corners.get(i),corners.get(i|bit),.95f,2);
    }

    private void planeOn(EffectEngine.Effect effect,String key,String model,Location at,Vector x,Vector y,double width,double height,double angle){
        if(!c.loaded(at))return;
        var piece=piece(effect,key,model,at);if(piece==null)return;
        if(width<.05||height<.05){piece.hide();return;}
        piece.plane(viewers,at,x,y,width,height,angle);
    }

    private Particle accent() {
        return switch (spell) {
            case BLAZE_BARRAGE, DRAGONS_BREATH, METEOR_STRIKE, SOLAR_APOCALYPSE -> Particle.FLAME;
            case FROST_NOVA, CHRONOS_FINAL_HOUR -> Particle.SNOWFLAKE;
            case NATURES_BLOOM, VERDANT_SOVEREIGN -> Particle.HAPPY_VILLAGER;
            case VEX_LEGION -> Particle.SOUL;
            case LIGHTNING_STRIKE -> Particle.ELECTRIC_SPARK;
            case GUARDIAN_BEAM -> Particle.BUBBLE_POP;
            case SHADOW_STEP, VOID_PULL, WITHER_RAY, SHULKER_LEVITATION -> Particle.PORTAL;
            default -> Particle.END_ROD;
        };
    }

    private void dust(Location at, float size) {
        if (budget <= 0 || !c.loaded(at)) return;
        budget--;
        viewers.dust(at, color, size);
    }

    private void sparks(Location at, Particle type, int count, double spread) {
        int emitted = Math.min(count, budget);
        if (emitted <= 0 || !c.loaded(at)) return;
        budget -= emitted;
        c.particles(at, type, emitted, spread);
    }

    private void line(Location from, Location to, float size, int samples) {
        Vector delta = to.toVector().subtract(from.toVector());
        int count = Math.max(1, Math.min(samples, (int) Math.ceil(delta.length() * 2)));
        for (int i = 0; i <= count && budget > 0; i++)
            dust(from.clone().add(delta.clone().multiply((double) i / count)), size);
    }

    private void ring(Location center, double radius, Vector x, Vector y, double turn, int samples) {
        for (int i = 0; i < samples && budget > 0; i++) {
            double angle = i * Math.PI * 2 / samples + turn;
            dust(center.clone().add(x.clone().multiply(Math.cos(angle) * radius))
                    .add(y.clone().multiply(Math.sin(angle) * radius)), .8f);
        }
    }

    private void plane(EffectEngine.Effect effect, String key, String model, Location at,
                       double width, double height, double angle) {
        if (!c.loaded(at)) return;
        MythicLineVisuals piece = piece(effect, key, model, at);
        if (piece == null) return;
        if (width < .05 || height < .05) { piece.hide(); return; }
        piece.plane(viewers, at, right, up, width, height, angle);
    }

    private void ray(EffectEngine.Effect effect, String key, String model, Location from, Location to, double width) {
        if (!c.loaded(from) || !c.loaded(to)) return;
        MythicLineVisuals piece = piece(effect, key, model, from);
        if (piece == null) return;
        if (width < .03 || from.distanceSquared(to) < .01) { piece.hide(); return; }
        piece.ray(viewers, from, to, width);
    }

    private MythicLineVisuals piece(EffectEngine.Effect effect, String key, String model, Location at) {
        MythicLineVisuals current = art.get(key);
        if (current != null) return current;
        if (art.size() >= 20) return null;
        current = new MythicLineVisuals(effect, at, model);
        art.put(key, current);
        return current;
    }

    @Override public void close() {
        art.values().forEach(MythicLineVisuals::close);
        art.clear();
        viewers.clear();
    }
}
