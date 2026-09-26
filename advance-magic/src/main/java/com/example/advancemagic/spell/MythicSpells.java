package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import com.example.advancemagic.effect.TemporaryTerrainService;
import java.util.*;

/** Shared world-space geometry with journaled, owned temporary terrain.
 * No displays, camera packets, world-time changes or unowned tasks.
 * A single scope owns each complete cast, including its final attack and echoes.
 */
public final class MythicSpells {
    private static final double TAU=Math.PI*2;
    private static final Color GOLD=Color.fromRGB(0xFFD34D), WHITE=Color.fromRGB(0xFFF2AF),
        ORANGE=Color.fromRGB(0xFF732D), CYAN=Color.fromRGB(0x72EDFF), VIOLET=Color.fromRGB(0xA36BFF);
    private final MagicContext c;
    private final MythicVisuals visuals;
    private final CrystalBeamVisuals crystalBeams;
    private final Map<UUID,Integer> activeByWorld=new HashMap<>();
    public MythicSpells(MagicContext c){this.c=c;visuals=new MythicVisuals(c);crystalBeams=new CrystalBeamVisuals(c);}
    private boolean room(Location at) {
        return c.plugin.effects().hasCapacity()&&activeByWorld.getOrDefault(at.getWorld().getUID(),0)
            <Math.clamp(c.plugin.getConfig().getInt("mythic-max-active-per-world",4),1,16);
    }
    private void start(Player p,Location at,int duration,
            java.util.function.BiPredicate<com.example.advancemagic.effect.EffectEngine.Effect,Integer> body) {
        UUID world=at.getWorld().getUID();
        var effect=c.plugin.effects().start(p,duration,body);
        activeByWorld.merge(world,1,Integer::sum);
        effect.onClose(()->activeByWorld.computeIfPresent(world,(id,count)->count<=1?null:count-1));
        effect.onClose(visuals::clear);
    }

    private Location center(Player player) {
        var hit=c.target(player,30);
        Location at=hit==null?player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(16))
            :hit.getHitEntity()!=null?hit.getHitEntity().getLocation().add(0,.15,0)
            :hit.getHitPosition().toLocation(player.getWorld()).add(0,.15,0);
        return c.loaded(at)?at:null;
    }
    private void dust(Location at,Color color,float size) {
        visuals.dust(at,color,size);
    }
    private void spark(Location at,Particle particle,int count,double spread) {
        if(c.loaded(at))at.getWorld().spawnParticle(particle,at,count,spread,spread,spread,0,null,true);
    }
    private void line(Location from,Location to,Color color,float size,int samples) {
        Vector delta=to.toVector().subtract(from.toVector());
        int n=Math.min(40,Math.max(2,samples));
        for(int i=0;i<=n;i++)dust(from.clone().add(delta.clone().multiply((double)i/n)),color,size);
    }
    private void ring(Location at,double radius,Color color,float size,int count,double turn) {
        for(int i=0;i<count;i++) {
            double a=TAU*i/count+turn;
            dust(at.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),color,size);
        }
    }
    private void glyph(Location at,int age,Color color) {
        ring(at,12,color,1.5f,64,0);
        ring(at,9,color,1.2f,48,0);
        for(int i=0;i<10;i++) {
            double a=TAU*i/10+age*.012;
            Vector inner=new Vector(Math.cos(a)*9,0,Math.sin(a)*9);
            Vector outer=new Vector(Math.cos(a)*12,0,Math.sin(a)*12);
            line(at.clone().add(inner),at.clone().add(outer),color,1.3f,4);
        }
    }
    private void sun(Location at,double radius,int age,int layer) {
        // Three great circles give a recognizable 3D orb from either client's view.
        int points=48+layer*8;float size=2.8f+layer*.25f;
        for(int i=0;i<points;i++) {
            double a=TAU*i/points, x=Math.cos(a)*radius, y=Math.sin(a)*radius;
            dust(at.clone().add(x,y,0),WHITE,size);
            dust(at.clone().add(x,0,y),GOLD,size);
            dust(at.clone().add(0,x,y),ORANGE,size-.2f);
        }
        for(int latitude=-2;latitude<=2;latitude++) {
            double y=radius*latitude/3, circle=Math.sqrt(radius*radius-y*y);
            ring(at.clone().add(0,y,0),circle,latitude%2==0?GOLD:ORANGE,size-.4f,points/2,age*.015);
        }
        for(int i=0;i<12;i++) {
            double a=TAU*i/12+age*.018;
            Vector ray=new Vector(Math.cos(a),Math.sin(a),Math.sin(a*.5)*.3);
            line(at.clone().add(ray.clone().multiply(radius)),at.clone().add(ray.multiply(radius+1.5)),GOLD,2f,3);
        }
        spark(at,Particle.FLAME,32+layer*8,radius*.65);
        spark(at,Particle.END_ROD,10,radius*.35);
    }
    private List<LivingEntity> targets(Player p,Location at,double radius,Spell spell) {
        return c.nearby(p,at,radius,false).stream().filter(e->c.affect(p,e,spell)).toList();
    }
    private void hit(Player p,LivingEntity target,double damage,double multiplier) {
        // The cast listener clears its multiplier after scheduling; capture it once.
        c.damage(p,target,damage*multiplier,DamageType.MAGIC);
    }
    private void terrainPulse(Player p,Location at,TemporaryTerrainService.Zone zone,int age,
                              Spell spell,double power,double damage,boolean poison) {
        if(age%5==0)for(Location surface:zone.samples(14,age*7)){
            dust(surface,poison?VIOLET:ORANGE,1.8f);
            dust(surface.clone().add(0,.35,0),poison?CYAN:GOLD,1.2f);
            spark(surface,poison?Particle.END_ROD:Particle.FLAME,2,.2);
        }
        if(age%20!=0)return;
        double radius=Math.clamp(c.plugin.getConfig().getInt("mythic-terrain.radius",16),6,18)+1;
        for(var e:targets(p,at,radius,spell))if(zone.touches(e)){
            hit(p,e,damage,power);
            if(e.isDead())continue;
            if(poison)c.potion(e,PotionEffectType.POISON,100,
                Math.clamp(c.plugin.getConfig().getInt("mythic-terrain.poison-amplifier",4),0,9));
            else e.setFireTicks(Math.max(e.getFireTicks(),60));
        }
    }
    public boolean solar(Player p) {
        Location at=center(p);
        if(at==null||!room(at))return false;
        double[] radii={4.4,6.2,8.0,9.8,11.6};double[] heights=new double[5];heights[0]=16;
        for(int i=1;i<5;i++)heights[i]=heights[i-1]+radii[i-1]+radii[i]+2;
        double scale=Math.min(1,(at.getWorld().getMaxHeight()-1-at.getY())/(heights[4]+radii[4]+1.5));
        if(scale<.3)return false;
        Location high=at.clone().add(0,heights[0]*scale,0);
        Location top=at.clone().add(0,(heights[4]+radii[4]+1.5)*scale,0);
        if(!c.loaded(high)||!c.loaded(top))return false;
        double power=c.getCastDamageMultiplier(p.getUniqueId());
        double beamDamage=c.configuredDamage("damage.solar-beam",32);
        double burstDamage=c.configuredDamage("damage.solar-apocalypse",180);
        double lavaDamage=c.configuredDamage("damage.solar-lava",12);
        int lifetime=c.plugin.terrain().duration();
        TemporaryTerrainService.Zone[] sea={null};
        at.getWorld().playSound(at,Sound.ENTITY_ENDER_DRAGON_GROWL,2f,.65f);
        start(p,at,Math.max(141,110+lifetime+1),(effect,age)->{
            if(!c.loaded(at)||!c.loaded(top))return false;
            visuals.frame(at);
            if(age%4==0&&age<110)glyph(at,age,GOLD);
            // Five larger orbs use a bounded 0.3-second redraw, not five full-rate effects.
            if(age%6==0&&age<110) {
                double descent=age<90?0:Math.min(1,(age-90)/20.0);
                double growth=age<40?.35+.65*age/40.0:1;
                for(int i=0;i<5;i++){
                    // Every layer begins falling together and reaches the same impact at tick 110.
                    double height=(heights[i]*(1-descent)+descent)*scale;
                    sun(at.clone().add(0,height,0),radii[i]*scale*growth*(1-descent*.25),age,i);
                }
            }
            if(age<90&&age%10==0)for(var e:targets(p,at,14,Spell.SOLAR_APOCALYPSE)) {
                Vector pull=at.toVector().subtract(e.getLocation().toVector()).setY(0);
                if(pull.lengthSquared()>.25)c.velocity(e,pull.normalize().multiply(.3).setY(.08));
                c.potion(e,PotionEffectType.SLOWNESS,15,1);
            }
            if(age>=40&&age<=80&&(age-40)%10==0) {
                int beam=(age-40)/10;
                double angle=TAU*beam/5;
                Location impact=at.clone().add(Math.cos(angle)*4,0,Math.sin(angle)*4);
                line(high,impact,WHITE,3.5f,40);
                line(high.clone().add(.5,0,0),impact.clone().add(.5,0,0),ORANGE,2.5f,32);
                ring(impact,5,GOLD,2f,40,0);
                spark(impact,Particle.FLAME,60,1.8);
                at.getWorld().playSound(impact,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1.5f,1.25f);
                for(var e:targets(p,impact,7,Spell.SOLAR_APOCALYPSE)) {
                    hit(p,e,beamDamage,power);
                    if(!e.isDead())e.setFireTicks(Math.max(e.getFireTicks(),60));
                }
            }
            if(age==110) {
                for(int i=0;i<5;i++)sun(at.clone().add(0,scale,0),radii[i]*scale*.75,age,i);
                spark(at.clone().add(0,1,0),Particle.EXPLOSION_EMITTER,1,0);
                spark(at.clone().add(0,2,0),Particle.FLAME,120,4);
                spark(at,Particle.END_ROD,60,3);
                at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,2.5f,.6f);
                for(var e:targets(p,at,16,Spell.SOLAR_APOCALYPSE)) {
                    hit(p,e,burstDamage,power);
                    if(!e.isDead()) {
                        Vector push=e.getLocation().toVector().subtract(at.toVector()).setY(0);
                        if(push.lengthSquared()>.01)c.velocity(e,push.normalize().multiply(1.4).setY(.65));
                    }
                }
                sea[0]=c.plugin.terrain().open(p,Spell.SOLAR_APOCALYPSE,at,Material.LAVA);
                effect.onClose(sea[0]::close);
            }
            if(age>=110&&age<=140&&age%2==0) {
                double radius=1+(age-110)*.6;
                ring(at.clone().add(0,.5,0),radius,ORANGE,2.5f,80,0);
                ring(at.clone().add(0,1.2,0),radius*.9,GOLD,1.8f,48,0);
            }
            if(sea[0]!=null){
                sea[0].tick(age-110,lifetime);
                terrainPulse(p,at,sea[0],age-110,Spell.SOLAR_APOCALYPSE,power,lavaDamage,false);
            }
            return true;
        });
        return true;
    }

    private void clock(Location at,Vector right,int age,int offset,double radius) {
        double turn=age<88?age*.055:-(age-88)*.12;
        turn+=offset*.35;
        // Static outlines persist for 0.8 seconds in Bedrock; redraw every 0.4 seconds.
        if(age%8==0){
            int points=radius>8?120:72;
            for(int i=0;i<points;i++) {
                double a=TAU*i/points;
                dust(at.clone().add(right.clone().multiply(Math.cos(a)*radius)).add(0,Math.sin(a)*radius,0),GOLD,radius>8?2.8f:2f);
            }
            for(int i=0;i<12;i++) {
                double a=TAU*i/12;
                Location outer=at.clone().add(right.clone().multiply(Math.cos(a)*radius*.9375)).add(0,Math.sin(a)*radius*.9375,0);
                Location inner=at.clone().add(right.clone().multiply(Math.cos(a)*radius*.825)).add(0,Math.sin(a)*radius*.825,0);
                line(inner,outer,i%3==0?CYAN:VIOLET,1.8f,3);
            }
            if(radius>8)for(int i=0;i<96;i++){
                double a=TAU*i/96;
                dust(at.clone().add(right.clone().multiply(Math.cos(a)*(radius+1))).add(0,Math.sin(a)*(radius+1),0),CYAN,2f);
            }
        }
        line(at,at.clone().add(right.clone().multiply(Math.cos(turn)*radius*.7875)).add(0,Math.sin(turn)*radius*.7875,0),CYAN,2.5f,20);
        line(at,at.clone().add(right.clone().multiply(Math.cos(turn*.22+1)*radius*.5)).add(0,Math.sin(turn*.22+1)*radius*.5,0),VIOLET,2.5f,14);
        spark(at,Particle.END_ROD,4,.4);
    }
    private boolean boss(LivingEntity target) {
        var health=target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return target instanceof EnderDragon||target instanceof Wither||target instanceof Warden
            ||(health!=null&&health.getValue()>=150);
    }
    public boolean chronos(Player p) {
        Location at=center(p);
        if(at==null||!room(at))return false;
        // Lift the constellation so its surrounding 44-block dial remains above ground.
        Location face=at.clone().add(0,24,0);
        if(!c.loaded(face.clone().add(0,23,0)))return false;
        Vector forward=p.getEyeLocation().getDirection().setY(0);
        if(forward.lengthSquared()<.01)forward=new Vector(0,0,1);
        forward.normalize();
        final Vector right=new Vector(forward.getZ(),0,-forward.getX()).normalize();
        List<Location> faces=new ArrayList<>();List<Vector> axes=new ArrayList<>();
        faces.add(face);axes.add(right);
        for(int i=0;i<4;i++){
            // Diagonal flanks relative to the caster keep a nearby clock off the central sightline.
            double angle=TAU*i/4+Math.PI/4;
            Vector radial=right.clone().multiply(Math.cos(angle)).add(forward.clone().multiply(Math.sin(angle)));
            Location surround=at.clone().add(radial.multiply(14)).add(0,25,0);
            if(!c.loaded(surround)||!c.loaded(surround.clone().add(0,8,0)))return false;
            faces.add(surround);axes.add(right.clone().multiply(-Math.sin(angle)).add(forward.clone().multiply(Math.cos(angle))));
        }
        List<Location> emitters=new ArrayList<>();
        for(int i=0;i<12;i++){
            double a=TAU*i/12;
            Location source=face.clone().add(right.clone().multiply(Math.cos(a)*22)).add(0,Math.sin(a)*22,0);
            if(!c.loaded(source))return false;
            // Java's native beam anchor bobs below the crystal entity's origin.
            emitters.add(source.add(0,1,0));
        }
        double power=c.getCastDamageMultiplier(p.getUniqueId());
        double bladeDamage=c.configuredDamage("damage.chronos-blade",24);
        double shatterDamage=c.configuredDamage("damage.chronos-shatter",100);
        double poisonDamage=c.configuredDamage("damage.chronos-poison",12);
        int lifetime=c.plugin.terrain().duration();
        TemporaryTerrainService.Zone[] sea={null};
        CrystalBeamVisuals.Projection[] projection={null};
        Set<UUID> firstRound=new HashSet<>();
        start(p,at,Math.max(191,88+lifetime+1),(effect,age)->{
            if(!c.loaded(at)||!c.loaded(face))return false;
            visuals.frame(at);
            if(age==0)projection[0]=crystalBeams.open(effect,emitters,face);
            if(age%4==0&&age<160) {
                for(int i=0;i<faces.size();i++)clock(faces.get(i),axes.get(i),age,i,8);
                clock(face,right,age,5,22);
                glyph(at,age,CYAN);
            }
            if(age%4==0&&projection[0]!=null){
                projection[0].sync(at,age>=40&&age<160);
                if(!projection[0].available()&&age>=40&&age<160)for(Location source:emitters){
                    line(source.clone().add(0,-1,0),face,VIOLET,2.8f,30);
                }
            }
            if(age%10==0&&age<=60)for(var e:targets(p,at,12,Spell.CHRONOS_FINAL_HOUR)) {
                if(boss(e))c.potion(e,PotionEffectType.SLOWNESS,25,1);
                else c.plugin.statuses().root(p,e);
            }
            // All five clocks fire together; one damage pulse per volley.
            boolean first=age>=40&&age<=80&&(age-40)%10==0;
            boolean echo=age>=100&&age<=140&&(age-100)%10==0;
            if(first||echo) {
                spark(at,Particle.END_ROD,16,2);
                at.getWorld().playSound(at,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1.2f,echo?.7f:1.6f);
                for(var e:targets(p,at,12,Spell.CHRONOS_FINAL_HOUR)) {
                    if(first)firstRound.add(e.getUniqueId());
                    if(first||firstRound.contains(e.getUniqueId()))hit(p,e,bladeDamage*(echo?.7:1),power);
                }
            }
            boolean firing=(age>=40&&age<=86&&(age-40)%10<=6)
                ||(age>=100&&age<=146&&(age-100)%10<=6);
            if(firing&&age%2==0)for(Location source:faces){
                line(source,at.clone().add(0,1,0),age>=100?VIOLET:CYAN,2.8f,36);
                line(source.clone().add(0,.3,0),at.clone().add(0,1.3,0),WHITE,1.4f,24);
            }
            if(age==88){
                at.getWorld().playSound(at,Sound.BLOCK_BEACON_DEACTIVATE,1.8f,.5f);
                sea[0]=c.plugin.terrain().open(p,Spell.CHRONOS_FINAL_HOUR,at,Material.WATER);
                effect.onClose(sea[0]::close);
            }
            if(age==160) {
                if(projection[0]!=null)projection[0].close();
                for(Location source:faces)spark(source,Particle.END_ROD,50,5);
                for(Location source:emitters)spark(source,Particle.END_ROD,12,.7);
                spark(at,Particle.EXPLOSION,4,3);
                at.getWorld().playSound(at,Sound.BLOCK_GLASS_BREAK,2f,.6f);
                for(var e:targets(p,at,12,Spell.CHRONOS_FINAL_HOUR))hit(p,e,shatterDamage,power);
            }
            if(age>=160&&age<=190&&age%2==0) {
                ring(at.clone().add(0,.8,0),1+(age-160)*.5,VIOLET,2f,64,-age*.06);
                ring(at.clone().add(0,1.5,0),1+(age-160)*.35,CYAN,1.6f,48,age*.06);
            }
            if(sea[0]!=null){
                sea[0].tick(age-88,lifetime);
                terrainPulse(p,at,sea[0],age-88,Spell.CHRONOS_FINAL_HOUR,power,poisonDamage,true);
            }
            return true;
        });
        return true;
    }
}
