package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.Predicate;

/** Wand-specific paths and timing, with a shared per-target damage budget per cast. */
public final class BasicSpells {
    private final MagicContext c;
    private final Map<UUID,Integer> active=new HashMap<>();
    public BasicSpells(MagicContext c){this.c=c;}
    public BasicSpellProfile profile(Spell spell){return BasicSpellProfile.configured(c.plugin.getConfig(),spell);}
    public boolean hasCapacity(Player player){
        int limit=Math.clamp(c.plugin.getConfig().getInt("basic-casting.max-active-per-world",32),1,128);
        return c.plugin.effects().hasCapacity()&&active.getOrDefault(player.getWorld().getUID(),0)<limit;
    }
    public boolean cast(Player player,Spell spell,BasicSpellProfile profile){
        if(!hasCapacity(player)||!c.loaded(player.getEyeLocation()))return false;
        UUID world=player.getWorld().getUID();
        Attack attack=new Attack(player,spell,profile);
        active.merge(world,1,Integer::sum);
        try{
            var effect=c.plugin.effects().start(player,80,attack::tick);
            effect.onClose(()->{active.computeIfPresent(world,(key,count)->count<=1?null:count-1);attack.visuals.close();});
            return true;
        }catch(RuntimeException error){active.computeIfPresent(world,(key,count)->count<=1?null:count-1);throw error;}
    }

    private final class Attack {
        final Player player;
        final Spell spell;
        final BasicSpellProfile profile;
        final BasicSpellProfile.Pattern pattern;
        final Location origin,aim,columnBase;
        final Vector forward,right,up;
        final double upgrade;
        final BasicSpellVisuals visuals;
        final List<Shot> shots=new ArrayList<>();
        final Map<UUID,Double> spent=new HashMap<>();
        LivingEntity locked;
        EffectEngine.Effect effect;
        int finishAge=-1;
        Location end;

        Attack(Player player,Spell spell,BasicSpellProfile profile){
            this.player=player;this.spell=spell;this.profile=profile;pattern=BasicSpellProfile.pattern(spell);
            origin=player.getEyeLocation();forward=origin.getDirection().normalize();
            Vector side=forward.clone().crossProduct(new Vector(0,1,0));
            right=side.lengthSquared()<.001?new Vector(1,0,0):side.normalize();up=right.clone().crossProduct(forward).normalize();
            double length=safeDistance(origin,forward,profile.range());
            var hit=trace(origin,forward,length,.15,Set.of());
            aim=hit==null?origin.clone().add(forward.clone().multiply(length)):hit.getHitPosition().toLocation(origin.getWorld());
            locked=hit!=null&&hit.getHitEntity() instanceof LivingEntity living?living:null;
            var floor=origin.getWorld().rayTraceBlocks(aim.clone().add(0,.3,0),new Vector(0,-1,0),3,FluidCollisionMode.NEVER,true);
            columnBase=floor==null?aim.clone():floor.getHitPosition().toLocation(origin.getWorld()).add(0,.15,0);
            end=aim.clone();upgrade=c.getCastDamageMultiplier(player.getUniqueId());
            visuals=new BasicSpellVisuals(c,spell,origin,forward);
        }

        boolean tick(EffectEngine.Effect effect,int age){
            this.effect=effect;
            if(!c.loaded(origin))return false;
            visuals.beginFrame(origin);
            if(age==0){visuals.entrance();schedule();}
            boolean running=false;
            for(Shot shot:shots){
                if(!shot.done){running=true;if(age>=shot.delay)shot.tick(age);}
                if(effect.isClosed())return false;
            }
            switch(pattern){
                case RAY -> {if(age==0)ray(1);running=age<8;}
                case CHANNEL -> {if(age==2||age==6||age==10)channel();running=age<12;}
                case CHARGE -> {if(age==8)ray(1);running=age<18;}
                case COLUMN -> {if(age==8)column();running=age<22;}
                case NOVA -> {
                    Location center=origin.clone().subtract(0,player.getEyeHeight()-.25,0);
                    if(age==0)area(center,profile.range(),1,pulseLimit());
                    if(age<10)visuals.pulse(center,Math.max(.3,profile.range()*(age+1)/10));
                    running=age<10;
                }
                case CONE -> {
                    if(age==0||age==3||age==6)cone(Math.min(8,profile.range()),.75,1.0/3,3);
                    if(age<9)visuals.cone(origin,Math.min(8,profile.range())*(age%3+1)/3,age);
                    running=age<10;
                }
                case SLASH -> {
                    if(age==3)cone(Math.min(7,profile.range()),.65,1,3);
                    if(age<9)visuals.slash(origin,age);
                    running=age<9;
                }
                case GROUND -> {
                    double reach=Math.min(8,profile.range()*2.5);
                    Vector flat=forward.clone().setY(0);
                    if(flat.lengthSquared()<.001)flat=new Vector(-Math.sin(Math.toRadians(origin.getYaw())),0,Math.cos(Math.toRadians(origin.getYaw())));
                    flat.normalize();
                    if(age<16){
                        Location point=origin.clone().add(flat.multiply(reach*(age+1)/16)).subtract(0,player.getEyeHeight()-.3,0);
                        if(c.loaded(point)&&clear(origin,point)&&age%2==0){area(point,1.25,1,3);visuals.ground(point,age);}
                    }
                    running=age<16;
                }
                case WAVE -> {
                    if(age<14){
                        double reach=Math.min(profile.range(),(age+1)*1.6);
                        double length=safeDistance(origin,forward,reach);
                        var block=origin.getWorld().rayTraceBlocks(origin,forward,Math.max(.001,length),FluidCollisionMode.NEVER,true);
                        if(block!=null)length=origin.distance(block.getHitPosition().toLocation(origin.getWorld()));
                        Location front=origin.clone().add(forward.clone().multiply(length));
                        pierce(front,.7,1,3);visuals.wave(front,.35+age*.045,age);
                    }
                    running=age<14;
                }
                default -> {}
            }
            if(effect.isClosed())return false;
            if(!running&&finishAge<0)finishAge=age;
            if(spell==Spell.CRIMSON_OBLIVION){
                Shot shot=shots.getFirst();
                visuals.signature(effect,shot.position,end,age,shot.impactAge);
            }else visuals.castSignature(effect,aim,age,finishAge);
            return finishAge<0||age-finishAge<12;
        }

        void schedule(){
            switch(pattern){
                case BOLT,ORB,LOB -> add(0,0,1,0,forward);
                case FAN -> {for(int i=-1;i<=1;i++)add(i+1,0,1.0/3,i,forward.clone().rotateAroundAxis(up,Math.toRadians(i*12)));}
                case BURST -> {
                    int count=spell==Spell.VEX_LEGION||spell==Spell.CHRONOS_FINAL_HOUR?2:3;
                    for(int i=0;i<count;i++){
                        int delay=switch(spell){case VERDANT_SOVEREIGN->4+i*3;case LUNAR_REQUIEM->6+i*3;case CHRONOS_FINAL_HOUR->3+i*6;default->i*3;};
                        double fraction=spell==Spell.CHRONOS_FINAL_HOUR?(i==0?.6:.4):1.0/count;
                        add(i,delay,fraction,i-(count-1)/2.0,forward);
                    }
                }
                default -> {}
            }
        }
        void add(int index,int delay,double fraction,double lane,Vector direction){shots.add(new Shot(index,delay,fraction,lane,direction));}

        final class Shot {
            final int index,delay;
            final double fraction,lane,length,speed;
            final Vector direction;
            Location position=origin.clone();
            boolean done;
            int impactAge=-1;
            Shot(int index,int delay,double fraction,double lane,Vector direction){
                this.index=index;this.delay=delay;this.fraction=fraction;this.lane=lane;this.direction=direction.clone();
                length=spell==Spell.METEOR_STRIKE||spell==Spell.NATURES_BLOOM||spell==Spell.VEX_LEGION||spell==Spell.LUNAR_REQUIEM?
                        Math.max(.5,origin.distance(aim)):profile.range();
                speed=switch(spell){case VOID_PULL->.65;case SHULKER_LEVITATION->.95;case WITHER_RAY->.85;case FROST_NOVA->1.5;case LUNAR_REQUIEM->1.7;default->profile.speed();};
            }
            Location point(double distance){
                double progress=Math.min(1,distance/length);
                Location point=origin.clone().add(direction.clone().multiply(Math.min(distance,length)));
                if(spell==Spell.METEOR_STRIKE)point.add(0,Math.sin(Math.PI*progress)*Math.min(3,length*.3),0);
                if(spell==Spell.NATURES_BLOOM)point.add(up.clone().multiply(Math.sin(Math.PI*progress)*.65));
                if(spell==Spell.VEX_LEGION||spell==Spell.LUNAR_REQUIEM)
                    point.add(right.clone().multiply(lane*Math.sin(Math.PI*progress)*1.1));
                if(spell==Spell.WITHER_RAY){
                    point.add(right.clone().multiply(Math.sin(distance*2.2)*.22));
                    point.add(up.clone().multiply(Math.cos(distance*2.2)*.22));
                }
                return point;
            }
            void tick(int age){
                if(done)return;
                Location from=position.clone(),wanted=point((age-delay+1)*speed);
                Vector segment=wanted.toVector().subtract(from.toVector());double distance=segment.length();
                if(distance<.001){impact(age);return;}
                Vector travel=segment.clone().multiply(1/distance);
                double safe=safeDistance(from,travel,distance);
                var hit=trace(from,travel,safe,profile.radius(),Set.of());
                position=hit==null?from.clone().add(travel.clone().multiply(safe)):hit.getHitPosition().toLocation(origin.getWorld());
                visuals.flight(effect,index,from,position,travel,age,lane);
                if(hit!=null&&hit.getHitEntity() instanceof LivingEntity target)
                    deal(target,fraction,living->intersects(living,from,position,profile.radius()));
                if(effect.isClosed())return;
                if(hit!=null||safe<distance-.001||(age-delay+1)*speed>=length-.001)impact(age);
            }
            void impact(int age){
                done=true;impactAge=age;end=position.clone();
                if(spell==Spell.VOID_PULL||spell==Spell.METEOR_STRIKE)area(position.clone().subtract(0,.7,0),1.5,fraction,3);
                if(effect.isClosed())return;
                visuals.endShot(index);visuals.impact(position);
            }
        }

        void ray(double fraction){
            double length=safeDistance(origin,forward,profile.range());
            var hit=trace(origin,forward,length,profile.radius(),Set.of());
            end=hit==null?origin.clone().add(forward.clone().multiply(length)):hit.getHitPosition().toLocation(origin.getWorld());
            if(hit!=null&&hit.getHitEntity() instanceof LivingEntity target)deal(target,fraction,living->intersects(living,origin,end,profile.radius()));
            if(!effect.isClosed())visuals.beam(effect,origin,end,spell==Spell.SOLAR_APOCALYPSE?8:0);
        }
        void channel(){
            if(locked==null||!c.enemy(player,locked)||locked.getWorld()!=origin.getWorld())return;
            Location sampled=locked.getEyeLocation();
            if(sampled.distanceSquared(origin)>profile.range()*profile.range()||!clear(origin,sampled))return;
            end=sampled;
            deal(locked,1.0/3,living->intersects(living,origin,sampled,.2));
            if(!effect.isClosed())visuals.beam(effect,origin,sampled,0);
        }
        void column(){
            Location base=columnBase.clone();
            if(!c.loaded(base)||!clear(origin,aim))return;
            end=base;area(base,1.5,1,3);
            if(!effect.isClosed())visuals.judgment(effect,base,1);
        }
        int pulseLimit(){return Math.clamp(c.plugin.getConfig().getInt("basic-casting.max-pulse-targets",6),1,16);}
        void cone(double range,double cosine,double fraction,int limit){
            int count=0;
            for(LivingEntity target:c.nearby(player,origin,range,false)){
                if(spent.size()>=limit&&!spent.containsKey(target.getUniqueId()))continue;
                Predicate<LivingEntity> inside=living->{Vector offset=living.getEyeLocation().toVector().subtract(origin.toVector());
                    return offset.lengthSquared()<=range*range&&(offset.lengthSquared()<.01||offset.normalize().dot(forward)>=cosine);};
                if(inside.test(target)&&deal(target,fraction,inside)&&++count>=limit)break;
                if(effect.isClosed())break;
            }
        }
        void area(Location center,double radius,double fraction,int limit){
            int count=0;
            for(LivingEntity target:c.nearby(player,center,radius,false)){
                if(spent.size()>=limit&&!spent.containsKey(target.getUniqueId()))continue;
                if(deal(target,fraction,living->living.getLocation().distanceSquared(center)<=radius*radius)&&++count>=limit)break;
                if(effect.isClosed())break;
            }
        }
        void pierce(Location front,double radius,double fraction,int limit){
            Vector delta=front.toVector().subtract(origin.toVector());double length=delta.length();
            if(length<.01)return;
            Set<UUID> ignored=new HashSet<>();
            for(int i=0;i<limit;i++){
                var hit=trace(origin,delta.clone().multiply(1/length),length,radius,ignored);
                if(hit==null||!(hit.getHitEntity() instanceof LivingEntity target))break;
                ignored.add(target.getUniqueId());deal(target,fraction,living->intersects(living,origin,front,radius));
                if(effect.isClosed())break;
            }
        }
        boolean deal(LivingEntity target,double fraction,Predicate<LivingEntity> inside){
            if(effect.isClosed()||!c.enemy(player,target)||target.getWorld()!=origin.getWorld()||!inside.test(target)||!clear(origin,target.getEyeLocation()))return false;
            double pvp=c.plugin.getConfig().getDouble("basic-casting.player-damage-multiplier",.6);
            if(!Double.isFinite(pvp))pvp=.6;
            double cap=profile.damage()*upgrade*(target instanceof Player?Math.clamp(pvp,0,1):1);
            double prior=spent.getOrDefault(target.getUniqueId(),0.0),amount=Math.min(cap-prior,cap*fraction);
            if(amount<.00001||!c.affect(player,target,spell))return false;
            if(effect.isClosed()||!c.enemy(player,target)||target.getWorld()!=origin.getWorld()||!inside.test(target)||!clear(origin,target.getEyeLocation()))return false;
            spent.put(target.getUniqueId(),prior+amount);
            double previous=c.getCastDamageMultiplier(player.getUniqueId());c.setCastDamageMultiplier(player.getUniqueId(),1);
            try{c.damage(player,target,amount,DamageType.MAGIC);}finally{c.setCastDamageMultiplier(player.getUniqueId(),previous);}
            return true;
        }
        boolean intersects(LivingEntity target,Location from,Location to,double radius){
            Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();
            return length>.001&&target.getBoundingBox().expand(radius).rayTrace(from.toVector(),delta.multiply(1/length),length+.002)!=null;
        }
        boolean clear(Location from,Location to){return c.clear(from,to)&&!c.plugin.areas().blocksProjectile(from.getWorld(),from.toVector(),to.toVector());}
        RayTraceResult trace(Location from,Vector direction,double distance,double radius,Set<UUID> ignored){
            return distance<=.001?null:from.getWorld().rayTrace(from,direction,distance,FluidCollisionMode.NEVER,true,radius,
                    entity->c.enemy(player,entity)&&!ignored.contains(entity.getUniqueId()));
        }
        double safeDistance(Location from,Vector direction,double requested){
            if(requested<=.001)return 0;
            double safe=0;
            for(double distance=Math.min(.2,requested);distance<=requested+.0001;distance=Math.min(requested,distance+.2)){
                Location next=from.clone().add(direction.clone().multiply(distance));
                if(!c.loaded(next)||c.plugin.areas().blocksProjectile(from.getWorld(),from.clone().add(direction.clone().multiply(safe)).toVector(),next.toVector()))break;
                safe=distance;if(distance>=requested)break;
            }
            return safe;
        }
    }
}
