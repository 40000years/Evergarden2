package com.example.advancemagic.spell;

import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import org.joml.Quaternionf;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Cosmetic-only scenes. No targets, damage, mana, physics, chunk loads or combat effect slots. */
public final class ElementalVfx implements AutoCloseable {
    private static final Vector X=new Vector(1,0,0), Y=new Vector(0,1,0), Z=new Vector(0,0,1);
    private final MagicContext context;
    private final NativeElementalVfx nativeVfx;
    private final NativeSpellProjectiles nativeProjectiles;
    private final Set<Scene> scenes=new LinkedHashSet<>();
    private final Map<UUID,Integer> worldPlanes=new HashMap<>(),packets=new HashMap<>(),details=new HashMap<>();
    private final Map<String,Scene> markers=new HashMap<>();
    private final Map<UUID,Long> soundTimes=new HashMap<>();
    private long tick;
    private int packetTick=Integer.MIN_VALUE;
    /**
     * Vanilla particles sent by the two spectacle Legendaries, per viewer for the current
     * renderer tick. tick() runs once per server tick, after combat effects emit.
     */
    private final Map<UUID,Integer> fxCounts=new HashMap<>();
    private long fxTick=Long.MIN_VALUE;
    private boolean warned;
    public ElementalVfx(MagicContext context){this.context=context;nativeVfx=new NativeElementalVfx(context);nativeProjectiles=new NativeSpellProjectiles(context);}
    private boolean nativeStyle(){return !context.plugin.getConfig().getString("elemental-visuals.style","native").equalsIgnoreCase("authored");}
    private int setting(String key,int fallback,int min,int max){return Math.clamp(context.plugin.getConfig().getInt("elemental-visuals."+key,fallback),min,max);}
    private boolean low(){return context.plugin.getConfig().getString("elemental-visuals.quality","balanced").equalsIgnoreCase("low");}
    private boolean permitPacket(Player p,int priority){
        // Effects emit before this service advances. Reset on the SERVER tick,
        // so a new impact never inherits the previous frame's spent allowance.
        int now=Bukkit.getCurrentTick();if(packetTick!=now){packets.clear();details.clear();packetTick=now;}
        UUID id=p.getUniqueId();int n=packets.getOrDefault(id,0),limit=setting("bedrock-packets-per-viewer-tick",12,2,32);
        if(n>=limit)return false;
        if(priority<3&&!low()){
            int detail=details.getOrDefault(id,0);
            if(detail>=3||n>=Math.max(0,limit-4))return false;
            details.put(id,detail+1);
        }
        packets.put(id,n+1);return true;
    }
    public Scene begin(Player owner,ElementalSpell spell){
        Scene scene=new Scene(owner,spell);
        if(context.plugin.getConfig().getBoolean("elemental-visuals.enabled",true)&&scenes.size()<setting("max-scenes",96,8,256))scenes.add(scene);
        else scene.close();
        return scene;
    }
    public void tick(){tick++;nativeVfx.tick();nativeProjectiles.tick();for(Scene s:List.copyOf(scenes))s.advance();soundTimes.keySet().removeIf(id->Bukkit.getPlayer(id)==null);}
    public void closeOwner(UUID owner){for(Scene s:List.copyOf(scenes))if(s.owner.getUniqueId().equals(owner))s.close();}
    @Override public void close(){for(Scene scene:List.copyOf(scenes))scene.close();nativeVfx.close();nativeProjectiles.close();markers.clear();worldPlanes.clear();packets.clear();details.clear();soundTimes.clear();}
    private static double ease(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private static Vector right(Vector direction){Vector value=new Vector(direction.getZ(),0,-direction.getX());return value.lengthSquared()<.001?X.clone():value.normalize();}
    private static Color color(Element e){return Color.fromRGB(switch(e){case FIRE->0xFF732D;case WATER->0x72EDFF;case ICE->0xD8F3FF;case WIND->0x58FFB1;case EARTH->0x8DEF81;case LIGHT->0xFFD34D;case DARK->0xA36BFF;case LIGHTNING->0xCCD8E0;});}
    private interface Draw {void paint(Frame frame);}
    private record Clip(long start,int life,Draw draw){}
    private record Piece(String model,MythicLineVisuals display,UUID world,int priority){}
    private static int priority(String key,String name){
        if(key.startsWith("status:")||key.startsWith("buff:")||key.startsWith("heal:")||key.startsWith("cage:"))return 0;
        if(key.startsWith("hit:")||key.equals("startup"))return 1;
        if(Set.of("hero","front","meteor","blade","line","links","fork","bolt-a","shaft","wings").contains(name)||name.startsWith("hero-"))return 4;
        return 3;
    }

    public final class Scene implements AutoCloseable {
        private final Player owner;
        private final ElementalSpell spell;
        private final World world;
        private final String signature,trail,hero,accent,ground;
        private final Vector facing,side,forward;
        private final MythicVisuals views;
        private final NativeElementalVfx.Scene nativeScene;
        private final NativeSpellProjectiles.Track nativeTrack;
        private final boolean legendaryLight,legendaryElement;
        private boolean legendaryStarted;
        private final Map<String,Clip> clips=new LinkedHashMap<>();
        private final Map<String,Piece> pieces=new HashMap<>();
        private Location anchor;
        private long lastView=-100,lastTouch=tick;
        private long lastProjectile=-100;
        private long lastResonance=-100;
        private int renderPriority=3;
        private boolean closed;
        private Scene(Player owner,ElementalSpell spell){
            this.owner=owner;this.spell=spell;world=owner.getWorld();anchor=owner.getLocation().clone();
            signature="elemental_"+spell.id();trail="elemental_"+spell.element().id()+"_trail";
            hero=spell.ultimate()?"elemental_rare_"+spell.id():signature;
            accent="elemental_rare_"+spell.element().id()+switch(spell.element()){
                case FIRE->"_plume";case WATER->"_crest";case ICE->"_shards";case WIND->"_ribbon";
                case EARTH->"_fragments";case LIGHT->"_rays";case DARK->"_fragments";case LIGHTNING->"_forks";};
            ground="elemental_rare_"+spell.element().id()+"_ground";
            facing=owner.getEyeLocation().getDirection();side=right(facing);forward=new Vector(-side.getZ(),0,side.getX());
            views=new MythicVisuals(context,p->permitPacket(p,renderPriority),48);
            // The eight flagship Legendary scenes own authored artwork on both
            // clients, including servers which keep ordinary spells native.
            legendaryLight=spell==ElementalSpell.HEAVENS_DESCENT
                &&context.plugin.getConfig().getBoolean("elemental-visuals.legendary-light.enabled",true);
            legendaryElement=Set.of(ElementalSpell.METEOR_RAIN,ElementalSpell.LEVIATHANS_WRATH,ElementalSpell.ABSOLUTE_ZERO,
                ElementalSpell.TEMPEST_DOMINION,ElementalSpell.TITANS_JUDGMENT,ElementalSpell.ABYSSAL_COLLAPSE,
                ElementalSpell.WRATH_OF_RAIJIN).contains(spell);
            boolean useNative=nativeStyle()&&!legendaryLight&&!legendaryElement;
            nativeScene=useNative?nativeVfx.begin(owner,spell):null;
            nativeTrack=useNative?nativeProjectiles.begin(owner,spell):null;
        }
        private boolean authoredLegend(){return legendaryLight||legendaryElement;}
        /** The seven elemental Legendaries: 3D props and heavy vanilla particles over existing rune circles. */
        private boolean spectacle(){return legendaryElement;}
        private List<Player> fxViewers=List.of();
        private long fxViewTick=-100;
        private List<Player> fxViewers(){
            if(tick-fxViewTick>=4){fxViewTick=tick;fxViewers=world.getPlayers().stream().filter(p->p.isOnline()&&p.getLocation().distanceSquared(anchor)<=96*96).toList();}
            return fxViewers;
        }
        /** One-off cosmetic accents on exact choreography beats; combat sounds stay in the spell service. */
        private void boom(Location at,Sound sound,float volume,float pitch){
            if(context.plugin.getConfig().getBoolean("elemental-visuals.sounds",true)&&context.loaded(at))world.playSound(at,sound,volume,pitch);
        }
        private boolean grand(){return spell.rarity().ordinal()>=ElementalSpell.Rarity.EPIC.ordinal();}
        public boolean enabled(){return !closed&&(nativeScene==null||nativeScene.enabled());}
        private boolean alive(){return !closed&&owner.isOnline()&&!owner.isDead()&&owner.getWorld()==world;}
        private void safe(Runnable task){if(!alive()){close();return;}try{task.run();}catch(RuntimeException ex){if(!warned){warned=true;context.plugin.getLogger().log(java.util.logging.Level.WARNING,"Elemental VFX disabled for failed scene; combat continues",ex);}close();}}
        private void refresh(Location at){if(at.getWorld()!=world||!context.loaded(at))return;anchor=at.clone();if(tick-lastView>=4){views.frame(anchor);lastView=tick;}}
        private void emit(String key,Location at,int life,Draw draw){emit(key,at,life,draw,0);}
        /**
         * {@code delay} 1 aligns a clip started by a combat effect with that effect's ages: combat
         * runs before this renderer's tick() in the same server tick, so frame ages then equal combat ages.
         */
        private void emit(String key,Location at,int life,Draw draw,int delay){safe(()->{
            if(!context.loaded(at))return;
            refresh(at);lastTouch=tick;
            if(!clips.containsKey(key)&&clips.size()>=32){
                // A crowded impact can create many target marks/heal clips.
                // Preserve this cast's finite main stage while retiring detail.
                String victim=clips.keySet().stream().filter(k->!authoredLegend()||(!k.equals("fx-lance")&&!k.equals("legendary-stage"))).findFirst().orElse(null);
                if(victim==null)return;
                clear(victim);
            }
            Clip clip=new Clip(tick+delay,Math.clamp(life,1,320),draw);clips.put(key,clip);
            Frame frame=new Frame(this,key,0);draw.paint(frame);frame.finish();
        });}
        private void advance(){
            if(nativeScene!=null){if(!alive()||!nativeScene.enabled())close();return;}
            safe(()->{
            if(authoredLegend()&&!context.loaded(anchor)){close();return;}
            if(clips.isEmpty()&&tick-lastTouch>12){close();return;}
            refresh(anchor);
            if(low()&&!authoredLegend())for(String key:List.copyOf(pieces.keySet()))release(key);
            // Long-lived status marks must not spend the native packet budget
            // before the actual spell silhouette has been sent.
            var ordered=new ArrayList<>(clips.entrySet());ordered.sort(Comparator.comparingInt((Map.Entry<String,Clip> e)->priority(e.getKey(),"hero")).reversed());
            for(var entry:ordered){
                Clip clip=entry.getValue();int age=(int)(tick-clip.start());if(age<0)continue;
                if(age>=clip.life()){clear(entry.getKey());continue;}
                int interval=entry.getKey().startsWith("beam:")||entry.getKey().startsWith("fx")?2:4;
                if(age>0&&age%interval==0){Frame frame=new Frame(this,entry.getKey(),age);clip.draw().paint(frame);frame.finish();}
            }
        });}
        public void clear(String prefix){
            if(nativeScene!=null){nativeScene.clear(prefix);nativeTrack.clear(prefix);}
            for(String key:List.copyOf(clips.keySet()))if(key.equals(prefix)||key.startsWith(prefix+":"))clips.remove(key);
            for(String key:List.copyOf(pieces.keySet()))if(key.startsWith(prefix+"/" )||key.startsWith(prefix+":"))release(key);
            markers.entrySet().removeIf(e->e.getValue()==this&&(e.getKey().equals(prefix)||e.getKey().startsWith(prefix+":")));
        }
        private void release(String key){Piece piece=pieces.remove(key);if(piece==null)return;piece.display().close();worldPlanes.computeIfPresent(piece.world(),(id,n)->n<=1?null:n-1);}
        @Override public void close(){if(closed)return;closed=true;if(nativeScene!=null){nativeScene.close();nativeTrack.close();}for(String key:List.copyOf(pieces.keySet()))release(key);clips.clear();views.clear();markers.values().removeIf(s->s==this);scenes.remove(this);}
        private void claim(String key){if(!alive())return;Scene previous=markers.put(key,this);if(previous!=null&&previous!=this)previous.clear(key);markers.put(key,this);}
        private void sound(Location at,Sound sound,float pitch){
            if(!context.plugin.getConfig().getBoolean("elemental-visuals.sounds",true)||!context.loaded(at)||tick-soundTimes.getOrDefault(owner.getUniqueId(),-100L)<3)return;
            soundTimes.put(owner.getUniqueId(),tick);at.getWorld().playSound(at,sound,.4f,pitch);
        }
        private void resonance(Location at){
            if(!grand()||tick-lastResonance<10||!context.plugin.getConfig().getBoolean("elemental-visuals.sounds",true)||!context.loaded(at))return;
            lastResonance=tick;
            Sound tone=switch(spell.element()){
                case FIRE->Sound.ENTITY_BLAZE_SHOOT;case WATER->Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED;
                case ICE->Sound.BLOCK_AMETHYST_BLOCK_RESONATE;case WIND->Sound.ITEM_ELYTRA_FLYING;
                case EARTH->Sound.BLOCK_DRIPSTONE_BLOCK_BREAK;case LIGHT->Sound.BLOCK_BEACON_ACTIVATE;
                case DARK->Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;case LIGHTNING->Sound.ITEM_TRIDENT_THUNDER;};
            // One short accent on a real activation, never on each visual frame.
            world.playSound(at,tone,spell.ultimate()?.55f:.35f,spell.ultimate()?.7f:.9f);
        }
        public void startup(){
            if(nativeScene!=null){safe(()->{nativeScene.startup();});return;}
            Location at=owner.getEyeLocation().add(facing.clone().multiply(.75)).add(0,-.3,0);
            if(legendaryElement){
                Location seal=owner.getLocation().add(0,.12,0);
                emit("startup",seal,10,f->f.planeSpin("glyph",legendaryTexture("sigil"),seal,X,Z,2*(1-ease(f.age/10.0)),f.age*.025,.025));
                resonance(seal);return;
            }
            if(legendaryLight){sound(at,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.75f);return;} // The lance clip draws its own charge.
            double scale=spell.ultimate()?1.35:grand()?1.05:spell.rarity()==ElementalSpell.Rarity.RARE?.85:.65;
            emit("startup",at,8,f->{
                f.plane("glyph",signature,at,side,Y,scale*(1-f.age/11.0),scale*(1-f.age/11.0),-.12*f.age);
                if(grand())for(int i:new int[]{-1,1})f.plane("flare"+i,accent,at.clone().add(side.clone().multiply(i*(.48+f.age*.025))).add(0,f.age*.035,0),side,Y,.7,1.2-f.age*.06,i*.25);
            });
            sound(at,switch(spell.element()){case FIRE->Sound.ENTITY_BLAZE_SHOOT;case WATER->Sound.ITEM_BUCKET_EMPTY;case ICE->Sound.BLOCK_AMETHYST_BLOCK_HIT;case WIND->Sound.ENTITY_PLAYER_ATTACK_SWEEP;case EARTH->Sound.BLOCK_STONE_HIT;case LIGHT->Sound.BLOCK_AMETHYST_BLOCK_CHIME;case DARK->Sound.BLOCK_ENCHANTMENT_TABLE_USE;case LIGHTNING->Sound.ITEM_TRIDENT_THROW;},grand()?.85f:1.2f);
        }
        public void projectile(Location from,Location to){
            if(nativeScene!=null){safe(()->{nativeTrack.projectile(from,to);nativeScene.projectile(from,to);});return;}
            if(tick-lastProjectile<2)return;lastProjectile=tick;
            Vector dir=to.toVector().subtract(from.toVector());if(dir.lengthSquared()<.001)return;dir.normalize();
            Vector r=right(dir),up=dir.clone().crossProduct(r).normalize();Location end=to.clone(),start=from.clone().subtract(dir.clone().multiply(.8));
            emit("projectile",end,3,f->{f.ray("tail",trail,start,end,spell==ElementalSpell.FIREBALL?.35:.18);
                f.plane("head","elemental_"+spell.element().id()+"_head",end,r,up,spell==ElementalSpell.FIREBALL?.8:.5,spell==ElementalSpell.FIREBALL?.9:.6,0);});
        }
        public void beam(Location from,Location to){
            if(nativeScene!=null){safe(()->{nativeTrack.impact(to);nativeScene.beam(from,to);});return;}
            if(legendaryElement)return;
            Location a=from.clone(),b=to.clone();String key="beam:"+a.getBlockX()+":"+a.getBlockY()+":"+a.getBlockZ()+":"+b.getBlockX()+":"+b.getBlockY()+":"+b.getBlockZ();
            emit(key,a,5,f->{
                if(spell==ElementalSpell.METEOR_RAIN){
                    f.plane("meteor",hero,b.clone().add(0,.8,0),side,Y,2.4,3.2,Math.PI);
                    if(f.age<4)f.ray("line",trail,b.clone().add(0,5,0),b,.7);
                    return;
                }
                double width=switch(spell.id()){case "inferno_spear","thunder_spear"->.5;case "water_blade","wind_cutter","thousand_wind_blades"->.55;default->.22;};
                Vector span=b.toVector().subtract(a.toVector());double length=span.length();Location middle=a.clone().add(span.clone().multiply(.5));
                if(spell==ElementalSpell.HELLFIRE_CHAIN&&length>.01){Vector up=span.clone().normalize().crossProduct(right(span)).normalize();f.plane("links",signature,middle,span.clone().normalize(),up,length,.65,0);}
                else if(spell==ElementalSpell.LIGHTNING_CHAIN&&length>.01)f.plane("fork",signature,middle,right(span),span.clone().normalize(),.8,length,0);
                else f.ray("line",trail,a,b,width*(1-f.age/7.0));
                if(Set.of("water_blade","wind_cutter","inferno_spear","thunder_spear","glacial_path","thousand_wind_blades").contains(spell.id())){
                    Location mid=a.clone().add(b.toVector().subtract(a.toVector()).multiply(.4+.08*f.age));
                    Vector dir=b.toVector().subtract(a.toVector());Vector r=right(dir);Vector u=dir.lengthSquared()<.001?Y.clone():dir.normalize().crossProduct(r).normalize();
                    f.plane("blade",signature,mid,r,u,grand()?1.6:1.2,grand()?3:2,spell.element()==Element.WIND?.65:0);
                    if(grand())f.plane("edge",accent,mid.clone().add(dir.clone().multiply(-.45)),r,u,1.4,2.2,-.25);
                }
            });
        }
        public void area(Location at,double radius){
            if(nativeScene!=null){safe(()->{nativeScene.area(at,radius);});return;}
            if(legendaryLight)return; // The synchronized pillar owns the complete impact.
            if(legendaryElement){if(spell==ElementalSpell.ABSOLUTE_ZERO)beginLegendary(at,80);return;}
            Location point=at.clone().add(0,.08,0);String key="area:"+point.getBlockX()+":"+point.getBlockY()+":"+point.getBlockZ()+":"+(int)(radius*10);
            resonance(point);
            emit(key,point,grand()?16:8,f->{
                if(grand()){grandImpact(f,point,radius);return;}
                f.plane("ground",signature,point,X,Z,radius*2,radius*2,0);
                if(spell==ElementalSpell.UPDRAFT)f.plane("lift",signature,point.clone().add(0,1.5,0),side,Y,3,3,0);
                if(f.age<4)f.plane("burst","elemental_"+spell.element().id()+"_burst",point.clone().add(0,.35,0),side,Y,Math.min(3,radius),Math.min(3,radius),f.age*.08);});
        }
        private void grandImpact(Frame f,Location at,double radius){
            double t=f.age/16.0,spread=radius*2*(.5+.5*ease((f.age+4)/12.0));
            // Only Absolute Zero is represented by a full upright hero here;
            // judgments own their hero in pillar(), waves in wave().
            if(spell==ElementalSpell.ABSOLUTE_ZERO){
                double h=4.8*(1-.3*t);
                f.plane("hero",hero,at.clone().add(0,2.5+t,0),side,Y,h,h,f.age*.018);
                f.plane("crown",accent,at.clone().add(0,1.5,0),forward,Y,4.3*(1-t*.3),3.5,0);
            }else if(spell==ElementalSpell.MAGMA_ERUPTION||spell==ElementalSpell.METEOR_RAIN){
                f.plane("hero",accent,at.clone().add(0,1.7+ease(t)*1.2,0),side,Y,3.8*(1-.45*t),4.5*(1-.2*t),0);
                for(int i:new int[]{-1,1})f.plane("ejecta"+i,accent,at.clone().add(side.clone().multiply(i*(.7+1.1*t))).add(0,1.2+Math.sin(t*Math.PI)*1.5,0),side,Y,1.5*(1-t*.5),2.2,i*.5);
            }else if(spell==ElementalSpell.SEISMIC_SLAM){
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;f.plane("stone"+i,accent,at.clone().add(Math.cos(a)*radius*.5,1+Math.sin(t*Math.PI)*.7,Math.sin(a)*radius*.5),right(new Vector(Math.cos(a),0,Math.sin(a))),Y,2.1,2*(1-t*.3),a*.2);}
            }
            f.plane("footprint",ground,at,X,Z,spread,spread,spell.element()==Element.DARK?-t*.4:0);
            // Preserve the spell's readable identity without filling the floor.
            if(f.age<8)f.plane("signature",signature,at.clone().add(0,.035,0),X,Z,radius*1.4,radius*1.4,0);
            if(f.age<8&&spell!=ElementalSpell.ABSOLUTE_ZERO&&spell!=ElementalSpell.MAGMA_ERUPTION&&spell!=ElementalSpell.METEOR_RAIN)
                f.plane("accent",accent,at.clone().add(0,.8,0),side,Y,Math.min(3,radius),Math.min(3,radius),0);
        }
        public void cone(Location origin,Vector direction,double radius,double cosine){
            if(nativeScene!=null){safe(()->{nativeScene.cone(origin,direction,radius,cosine);});return;}
            Location at=origin.clone().add(0,.12,0);Vector forward=direction.clone();Vector r=right(forward);
            emit("cone",at,6,f->{
                f.plane("foam",signature,at.clone().add(forward.clone().multiply(radius*.5)).add(0,.35,0),r,forward,radius*1.7,radius,0);
                double center=Math.atan2(forward.getZ(),forward.getX()),half=Math.acos(cosine);
                for(int i=0;i<10;i++){double angle=center-half+half*2*i/9;f.dust(at.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius),color(spell.element()),1);}
            });
        }
        public void boundary(Location at,double radius){
            if(nativeScene!=null){safe(()->{nativeScene.boundary(at,radius);});return;}
            if(authoredLegend())return; // The authored inner seal marks the gameplay radius.
            Location point=at.clone().add(0,.1,0);String key="boundary:"+point.getBlockX()+":"+point.getBlockY()+":"+point.getBlockZ()+":"+(int)(radius*10);
            emit(key,point,6,f->{for(int i=0;i<8;i++){double a=i*Math.PI/4;f.dust(point.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),color(spell.element()),.9f);}});
        }
        public void impact(LivingEntity target){
            if(nativeScene!=null){safe(()->{nativeScene.impact(target);});return;}
            Location at=target.getLocation().add(0,Math.min(1.3,target.getHeight()*.55),0);
            String key="hit:"+target.getUniqueId();Clip previous=clips.get(key);if(previous!=null&&tick-previous.start()<3)return;
            emit(key,at,5,f->f.plane("hit","elemental_"+spell.element().id()+"_burst",at,side,Y,.8*(1-f.age/7.0),.8*(1-f.age/7.0),.4));
        }
        public void heal(Player target){
            if(nativeScene!=null){safe(()->{nativeScene.heal(target);});return;}
            Location at=target.getLocation().add(0,.5,0);
            emit("heal:"+target.getUniqueId(),at,8,f->f.plane("heal","elemental_healing_spring",at.clone().add(0,f.age*.06,0),side,Y,.55,.7,0));
        }
        public void field(Location center,double radius,int age,int duration){
            if(nativeScene!=null){safe(()->{nativeScene.field(center,radius,age,duration);});return;}
            if(legendaryElement){beginLegendary(center,spell==ElementalSpell.ABSOLUTE_ZERO?80:spell==ElementalSpell.ABYSSAL_COLLAPSE?100:120);return;}
            if(age%4!=0)return;Location base=center.clone().add(0,.08,0);String id=spell.id();
            emit("field",base,6,f->{
                double turn=age*(id.equals("quicksand")?.008:.028);double diameter=radius*2;
                if(grand()){
                    grandField(f,base,radius,age+f.age);
                    f.plane("floor",ground,base,X,Z,diameter,diameter,spell.element()==Element.EARTH?0:turn*.3);
                    return;
                }
                f.plane("floor",signature,base,X,Z,diameter,diameter,id.equals("burning_ground")?0:turn);
                if(id.equals("rain_blessing")){
                    f.plane("rain-a",signature,base.clone().add(1,2.5,0),X,Y,2.5,2.5,0);
                    f.plane("rain-b",signature,base.clone().add(-1,2.5,0),Z,Y,2.5,2.5,0);
                }else if(Set.of("scorching_vortex","cyclone_trap","tempest_dominion","blizzard").contains(id)){
                    for(int i=0;i<2;i++){
                        double a=turn+i*Math.PI/2;Vector r=new Vector(Math.cos(a),0,Math.sin(a));
                        f.plane("curtain"+i,signature,base.clone().add(0,1.5+i*.6,0),r,Y,diameter*.7,3+i,turn*(i==0?1:-1));
                    }
                }else if(id.equals("holy_sanctuary"))f.plane("shrine",signature,base.clone().add(0,1.2,0),side,Y,2,2.2,0);
            });
        }
        private void grandField(Frame f,Location base,double radius,int age){
            String id=spell.id();double turn=age*.045,pulse=1+.06*Math.cos(age*Math.PI/10);
            if(spell==ElementalSpell.TEMPEST_DOMINION){
                f.plane("hero",hero,base.clone().add(0,3.5,0),side,Y,7*pulse,6,.08*Math.sin(age*.04));
                for(int i=0;i<2;i++){double a=turn+i*Math.PI;
                    f.plane("ribbon"+i,accent,base.clone().add(Math.cos(a)*radius*.48,1.6+i,Math.sin(a)*radius*.48),new Vector(Math.cos(a),0,Math.sin(a)),Y,4.8,2.5,-.25+i*.5);}
            }else if(spell==ElementalSpell.HOLY_SANCTUARY){
                f.plane("hero",signature,base.clone().add(0,1.1,0),side,Y,2.4,2.6,0);
                for(int i=0;i<3;i++){double a=i*Math.PI*2/3;
                    f.plane("spire"+i,accent,base.clone().add(Math.cos(a)*radius*.65,1.6,Math.sin(a)*radius*.65),side,Y,1.2*pulse,3.2,0);}
            }else if(spell==ElementalSpell.RAIN_BLESSING){
                f.plane("hero",signature,base.clone().add(0,3,0),side,Y,4,3.8,0);
                for(int i=0;i<2;i++){double a=turn*.35+i*Math.PI;
                    f.plane("blessing"+i,accent,base.clone().add(Math.cos(a)*2,1.6+Math.sin(age*.07+i)*.2,Math.sin(a)*2),side,Y,2,2,-.3+i*.6);}
            }else if(spell==ElementalSpell.STATIC_FIELD){
                f.plane("hero",signature,base.clone().add(0,.025,0),X,Z,radius*1.7,radius*1.7,0);
                if(age%20<8)for(int i=0;i<2;i++){double a=age/20*1.7+i*Math.PI;
                    f.plane("charge"+i,accent,base.clone().add(Math.cos(a)*radius*.6,1.1,Math.sin(a)*radius*.6),side,Y,1.7,2.4,0);}
            }else if(spell==ElementalSpell.WHIRLPOOL){
                // Three interleaved crests read as a fluid funnel, not a wall.
                for(int i=0;i<3;i++){double a=-turn+i*Math.PI*2/3;double r=radius*(.35+i*.15);
                    f.plane("hero-crest"+i,accent,base.clone().add(Math.cos(a)*r,.2+i*.12,Math.sin(a)*r),X,Z,radius,2.6,a);}
            }else{
                boolean flame=spell==ElementalSpell.SCORCHING_VORTEX,ice=spell==ElementalSpell.BLIZZARD;
                for(int i=0;i<3;i++){double a=(ice?-turn*.65:turn)+i*Math.PI*2/3;
                    Vector axis=new Vector(Math.cos(a),0,Math.sin(a));
                    Location at=base.clone().add(Math.cos(a)*radius*.5,flame?2.1:1.8+i*.45,Math.sin(a)*radius*.5);
                    f.plane("hero-layer"+i,accent,at,axis,Y,flame?2.7:ice?2.5:radius*1.35,flame?4.2:ice?3:2.8,flame?-.2:.3);
                }
            }
        }
        public void telegraph(Location center,double radius,int age,int duration){
            if(nativeScene!=null){safe(()->{nativeTrack.telegraph(center,age,duration);nativeScene.telegraph(center,radius,age,duration);});return;}
            if(legendaryElement){
                if(spectacle()&&!clips.containsKey("fx-charge")&&age<duration){
                    Location base=center.clone();int start=age;
                    emit("fx-charge",base,duration-age,f->chargeFx(f,base,Math.min(duration,start+f.age),duration),1);
                }
                if(age%4!=0&&age!=duration)return;
                Location base=center.clone();int current=age;
                emit("telegraph",base,6,f->legendaryCharge(f,base,Math.min(duration,current+f.age),duration));return;
            }
            if(legendaryLight)return;
            int remaining=duration-age;if(age%(remaining<=12?2:4)!=0&&age!=duration)return;
            Location at=center.clone();String id=spell.id();double t=(double)age/Math.max(1,duration);
            emit("telegraph",at,6,f->{
                if(id.equals("meteor_rain")){
                    meteorPreview(f,at,Math.max(0,12-remaining)/12.0,0);
                    f.plane("floor",ground,at.clone().add(0,.09,0),X,Z,12.6,12.6,0);
                    return;
                }
                double h=id.equals("titans_judgment")?8:id.equals("heavens_descent")?9:id.equals("divine_judgment")?8:id.equals("wrath_of_raijin")?9:2.5;
                double fall=Set.of("titans_judgment","divine_judgment","heavens_descent").contains(id)?ease((t-.6)/.4):0;
                double width=id.equals("heavens_descent")?7.5:id.equals("titans_judgment")?5.5:id.equals("abyssal_collapse")?4:3.5;
                Location pose=at.clone().add(0,h*(1-fall)+2.5,0);pose.setY(Math.min(pose.getY(),world.getMaxHeight()-5));
                if(spell==ElementalSpell.MAGMA_ERUPTION){
                    for(int i:new int[]{-1,1})f.plane("ember"+i,accent,at.clone().add(side.clone().multiply(i*1.2)).add(0,.3+t*.8,0),side,Y,.8+t*.4,1+t*1.5,i*.3);
                }else{
                    f.plane("hero",hero,pose,side,Y,width,spell==ElementalSpell.ABYSSAL_COLLAPSE?4.8:Math.min(6,h),spell==ElementalSpell.ABYSSAL_COLLAPSE?.08*Math.sin(age*.1):0);
                    if(grand())for(int i:new int[]{-1,1})f.plane("trim"+i,accent,pose.clone().add(side.clone().multiply(i*width*.36)),side,Y,1.2,Math.min(4,h),i*.2);
                }
                f.plane("floor",grand()?ground:signature,at.clone().add(0,.09,0),X,Z,radius*2,radius*2,spell==ElementalSpell.ABYSSAL_COLLAPSE?-t*.6:0);
            });
        }
        private void meteorPreview(Frame f,Location center,double progress,int n){
            if(progress<=0)return;double angle=n*Math.PI/3;Location at=center.clone().add(Math.cos(angle)*2.3,12*(1-Math.clamp(progress,0,1))+.7,Math.sin(angle)*2.3);
            f.plane("meteor",hero,at,side,Y,2.6,3.6,Math.PI);
            f.ray("tail",trail,at.clone().add(0,4,0),at,.75);
        }
        public void meteors(Location center,int age){
            if(nativeScene!=null){safe(()->{nativeTrack.meteors(center,age);nativeScene.meteors(center,age);});return;}
            if(legendaryElement){beginLegendary(center,141);return;}
            if(age%2!=0||age>=50)return;int n=age/10+1;
            emit("meteor-preview",center,3,f->meteorPreview(f,center,(age%10+2)/10.0,n));
        }
        public void pillar(Location at,double height){
            if(nativeScene!=null){safe(()->{nativeScene.pillar(at,height);});return;}
            if(legendaryElement){beginLegendary(at,spell==ElementalSpell.TITANS_JUDGMENT?44:spell==ElementalSpell.ABYSSAL_COLLAPSE?100:80);return;}
            if(legendaryLight)return;
            Location base=at.clone();boolean light=spell.element()==Element.LIGHT;
            emit("pillar",base,grand()?18:10,f->{
                if(grand()){
                    double t=f.age/18.0,fade=1-.35*t;
                    if(spell==ElementalSpell.TITANS_JUDGMENT){
                        f.plane("hero",hero,base.clone().add(0,2.8-t*.8,0),side,Y,5.8*fade,5.8*fade,-.06*t);
                        f.plane("arm",accent,base.clone().add(0,5-t,0),forward,Y,3.5*fade,5.5,0);
                    }else if(spell==ElementalSpell.HEAVENS_DESCENT){
                        f.plane("hero",hero,base.clone().add(0,5+1.5*t,0),side,Y,8*fade,6*fade,0);
                        f.ray("shaft",trail,base,base.clone().add(0,height,0),.85*(1-.65*t));
                        for(int i:new int[]{-1,1})f.plane("rays"+i,accent,base.clone().add(side.clone().multiply(i*2.4)).add(0,height*.5,0),side,Y,1.5*fade,height*.8,0);
                    }else if(spell==ElementalSpell.ABYSSAL_COLLAPSE){
                        f.plane("hero",hero,base.clone().add(0,2.4,0),side,Y,4.8*(1-.65*ease(t)),5.8*fade,.12*Math.sin(t*Math.PI));
                        f.plane("cross-rift",accent,base.clone().add(0,1.7,0),forward,Y,4*(1-t*.6),3.5,-t*.25);
                    }else if(spell==ElementalSpell.WRATH_OF_RAIJIN){
                        f.plane("hero",accent,base.clone().add(0,height*.5,0),side,Y,5*fade,height,0);
                        f.ray("shaft",trail,base,base.clone().add(0,height,0),.9*fade);
                    }else if(light){
                        f.plane("hero",signature,base.clone().add(0,height*.5,0),side,Y,2.4*fade,height,0);
                        f.plane("rays",accent,base.clone().add(0,height*.4,0),forward,Y,3*fade,height*.8,0);
                        f.ray("shaft",trail,base,base.clone().add(0,height,0),.6*fade);
                    }else f.plane("hero",accent,base.clone().add(0,height*.5,0),side,Y,3*fade,height,0);
                    return;
                }
                double visibleHeight=height*(1-.04*f.age);Location mid=base.clone().add(0,visibleHeight/2,0);
                f.plane("hero",signature,mid,side,Y,light?2:Math.min(5,height*.65),visibleHeight,0);
                if(light)f.ray("shaft","elemental_light_trail",base,base.clone().add(0,height,0),1.1*(1-f.age/14.0));
                else f.plane("side",signature,mid,new Vector(-side.getZ(),0,side.getX()),Y,Math.min(4,height*.5),visibleHeight,0);
            });
        }
        /** Seal artwork: Fire and Wind keep their own; the other five reuse existing rune circles (no new textures). */
        private String legendaryTexture(String suffix){
            return switch(spell.element()){
                case FIRE,WIND->"elemental_legendary_"+spell.element().id()+"_"+suffix;
                case WATER->"chronos_ripple";
                case ICE->"lunar_seal";
                case EARTH->"solar_corona";
                case DARK->"crimson_seal";
                case LIGHTNING->"lunar_constellation";
                default->"lunar_seal";
            };
        }
        /** The counter-rotating ring under and above each seal. */
        private String legendaryHalo(){
            return switch(spell.element()){
                case FIRE,WIND->legendaryTexture("sigil");
                case EARTH->"verdant_halo";
                case DARK->"crimson_seal";
                default->"lunar_orbit";
            };
        }
        private void beginLegendary(Location center,int duration){
            if(legendaryStarted||!alive()||!context.loaded(center))return;
            legendaryStarted=true;clear("telegraph");clear("startup");
            Location floor=center.clone().add(0,.12,0);
            emit("legendary-stage",floor,duration,f->legendaryElementFrame(f,floor,f.age,duration));resonance(floor);
            if(spectacle()){
                clear("fx-charge");
                emit("fx",floor,duration,f->legendaryFx(f,floor,f.age),1);
            }
        }
        private void legendaryCharge(Frame f,Location center,int age,int duration){
            double progress=age/(double)Math.max(1,duration),grow=.3+.7*ease(progress);
            if(spell==ElementalSpell.METEOR_RAIN){meteorCharge(f,center,age,progress,grow);return;}
            Location floor=center.clone().add(0,.12,0);
            f.planeSpin("hero-floor",legendaryTexture("sigil"),floor,X,Z,14*grow,age*.03,.03);
            if(!low())f.planeSpin("hero-orbit",legendaryHalo(),floor.clone().add(0,.07,0),X,Z,21*grow,-age*.018,-.018);
            if(!low())f.planeSpin("hero-crown",legendaryHalo(),floor.clone().add(0,16,0),X,Z,18*grow,-age*.014,-.014);
        }
        /** Approved Fire/Wind silhouettes, with shared lightweight runes for other elements. */
        private void legendaryElementFrame(Frame f,Location floor,int age,int duration){
            double t=age/(double)Math.max(1,duration);
            // The two impact finales retain a twenty-tick visual tail without
            // starting another meteor flight or another lightning damage cycle.
            double fade=spell==ElementalSpell.METEOR_RAIN?1-ease((age-120)/20.0)
                :spell==ElementalSpell.WRATH_OF_RAIJIN?1-ease((age-100)/20.0):1-ease((t-.88)/.12);
            double size=(.9+.1*ease(age/12.0))*fade;
            String seal=legendaryTexture("sigil");
            f.planeSpin("hero-floor",seal,floor,X,Z,16*size,age*.018,.018);
            if(!low())f.planeSpin("hero-orbit",legendaryHalo(),floor.clone().add(0,.07,0),X,Z,22*size,-age*.012,-.012);
            switch(spell.element()){
                case FIRE->{
                    // The sky gate flares while the giant meteor plunges; meteors and props live in the "fx" clip.
                    double flare=age>=96&&age<=120?1+.35*ease((age-96)/24.0):1,sky=meteorSky(floor);
                    f.planeSpin("hero-sky",seal,floor.clone().add(0,sky,0),X,Z,22*flare*fade,-age*.02,age>=96?-.05:-.02);
                    if(!low())f.planeSpin("hero-sky-halo",seal,floor.clone().add(0,sky+.1,0),X,Z,31*flare*fade,age*.012,.012);
                    if(age>=120){double d=(8+(age-120)*2.1)*fade;f.planeSpin("hero-blast",seal,floor.clone().add(0,.16,0),X,Z,d,age*.06,.06);}
                    if(!low())for(int i=0;i<6;i++){
                        int land=10+18*i;if(age<land)continue;
                        double scorch=ease((age-land)/6.0)*fade;
                        f.plane("crater-"+i,"elemental_rare_fire_ground",meteorLanding(floor,i).add(0,.06,0),X,Z,4.6*scorch,4.6*scorch,i*1.1);
                    }
                }


                case WIND->{
                    // A funnel of stacked magic circles: each layer rises in turn, counter-rotates and sways.
                    int layers=low()?3:6;double squeeze=tempestSqueeze(age),height=tempestHeight(age);
                    for(int k=0;k<layers;k++){
                        double h=1.4+k*(height-1.4)/Math.max(1,layers-1),appear=ease((age-k*2.5)/8.0);
                        if(appear<=0)continue;
                        double sway=.3+.14*k,turn=(k%2==0?1:-1)*(.05+.012*k);
                        Location layer=floor.clone().add(Math.sin(age*.07+k*.6)*sway,h,Math.cos(age*.06+k*.6)*sway);
                        f.planeSpin("hero-funnel-"+k,seal,layer,X,Z,2*tempestRadius(h,squeeze)*appear,age*turn,turn);
                    }
                    f.planeSpin("hero-sky",seal,floor.clone().add(0,height+1.6,0),X,Z,25*squeeze*ease(age/14.0),-age*.016,-.016);
                    if(!low())for(int i=0;i<3;i++){
                        double a=age*.11+i*Math.PI*2/3,r=7.6*Math.min(1.4,squeeze);
                        f.plane("blade-"+i,"elemental_rare_wind_ribbon",floor.clone().add(Math.cos(a)*r,2+i*3.2,Math.sin(a)*r),
                            new Vector(-Math.sin(a),0,Math.cos(a)),Y,6.5*ease(age/10.0),2.6,.35*Math.sin(a*2));
                    }
                }
                case WATER->{
                    f.planeSpin("hero-ceiling",legendaryHalo(),floor.clone().add(0,14,0),X,Z,20*size,-age*.02,-.02);
                    // A ripple runs out from the seal with every wave the leviathan launches.
                    double ripple=(age%24)/24.0;
                    f.planeSpin("hero-ripple",seal,floor.clone().add(0,.12,0),X,Z,(6+22*ease(ripple))*fade,age*.04,.04);
                }
                case ICE->{if(!low())f.planeSpin("hero-crown",legendaryHalo(),floor.clone().add(0,10,0),X,Z,18*size,-age*.015,-.015);}
                case EARTH->{
                    double expand=4+age*.65;
                    if(age<30)f.plane("hero-impact","elemental_rare_earth_ground",floor.clone().add(0,.14,0),X,Z,expand*fade,expand*fade,0);
                }
                case DARK->{
                    // Shadow chains bind the field to the singularity until it implodes.
                    Location core=floor.clone().add(0,4,0);
                    if(age<80)for(int i=0;i<(low()?2:4);i++){double a=i*Math.PI/2+age*.008;f.ray("links-"+i,"elemental_dark_trail",floor.clone().add(Math.cos(a)*7,.3,Math.sin(a)*7),core,.3*fade);}
                    if(age>=80){double burst=((age-80)*1.2+3)*fade;f.planeSpin("hero-collapse",seal,floor.clone().add(0,.2,0),X,Z,burst,-age*.035,-.035);}
                }
                case LIGHTNING->{
                    f.planeSpin("hero-storm",legendaryHalo(),floor.clone().add(0,14.5,0),X,Z,22*fade,age*.02,.02);
                    // Each strike's bolt runs from the drum ring to its ground point.
                    int pulse=Math.min(100,age/20*20);
                    if(age-pulse<6){
                        Location[] points=strikePoints(floor,pulse);
                        for(int i=0;i<points.length;i++)f.ray("bolt-"+i,"elemental_lightning_trail",drumAnchor(floor,age),points[i],(pulse==100?1.5:.75)*fade);
                        if(!low())f.plane("fork","elemental_rare_lightning_forks",floor.clone().add(0,6,0),side,Y,(pulse==100?10:6)*fade,12*fade,0);
                    }
                }
                default->{}
            }
        }
        // ---- Meteor Rain: synchronized with ElementalSpellService.meteors ----
        // Small meteor i lands at stage age 10+18i, 2.8 blocks out at i*60 degrees;
        // the giant meteor hits the centre at age 120. Visuals never decide hits.
        private static final Color EMBER=Color.fromRGB(0xFFB43A),MAGMA=Color.fromRGB(0xD9230F);
        private double meteorSky(Location floor){return Math.max(8,Math.min(24,world.getMaxHeight()-3-floor.getY()));}
        private Location meteorLanding(Location floor,int i){double a=i*Math.PI/3;return floor.clone().add(Math.cos(a)*2.8,0,Math.sin(a)*2.8);}
        /** Meteors fall in from outside the seal on a steep slant, accelerating; {@code u} runs 0..1. */
        private Location meteorPosition(Location floor,int i,double u){
            double a=i*Math.PI/3+.9,k=Math.pow(Math.clamp(u,0,1),1.7);
            Vector from=new Vector(Math.cos(a)*11,meteorSky(floor)-2,Math.sin(a)*11);
            return meteorLanding(floor,i).add(0,.7,0).add(from.multiply(1-k));
        }
        private Location giantMeteor(Location floor,int stageAge){
            double fall=stageAge<96?0:Math.pow(Math.clamp((stageAge-96)/24.0,0,1),2.2);
            return floor.clone().add(0,(meteorSky(floor)-5.5)*(1-fall)+1.6,0);
        }
        private Particle.DustTransition cinder(float size){return new Particle.DustTransition(EMBER,MAGMA,size);}
        private void meteorCharge(Frame f,Location center,int age,double progress,double grow){
            Location floor=center.clone().add(0,.12,0);double sky=meteorSky(floor);
            f.planeSpin("hero-floor",legendaryTexture("sigil"),floor,X,Z,14*grow,age*.03,.03);
            if(!low())f.planeSpin("hero-orbit",legendaryTexture("sigil"),floor.clone().add(0,.07,0),X,Z,23*grow,-age*.018,-.018);
            f.planeSpin("hero-sky",legendaryTexture("sigil"),floor.clone().add(0,sky,0),X,Z,22*grow,-age*(.02+.03*progress),-.02-.03*progress);
            if(!low())f.planeSpin("hero-sky-halo",legendaryTexture("sigil"),floor.clone().add(0,sky+.1,0),X,Z,31*grow,age*.012,.012);
            // The giant meteor condenses under the sky gate and keeps growing during the rain.
            double form=ease((progress-.3)/.7);
            if(form>0)drawGiant(f,giantMeteor(floor,0),age,1.87*form,false);
        }
        private void drawGiant(Frame f,Location core,int age,double size,boolean falling){
            f.prop("hero-giant-core",Material.MAGMA_BLOCK,core,size,age*.035,age*.022,.3);
            if(!low())for(int k=0;k<3;k++){
                double a=k*Math.PI*2/3+age*.05;
                Location lump=core.clone().add(Math.cos(a)*size*.42,(k-1)*size*.22,Math.sin(a)*size*.42);
                f.prop("hero-giant-"+k,k==1?Material.BLACKSTONE:k==0?Material.MAGMA_BLOCK:Material.NETHERRACK,lump,size*.62,-age*.04+k,age*.03,k*.7);
            }
        }
        private void meteorChargeFx(Frame f,Location center,int age,int duration){
            double t=age/(double)Math.max(1,duration),p=ease(t);Location floor=center.clone().add(0,.12,0);double sky=meteorSky(floor);
            // Embers stream up from the seal rim, heat is drawn into a spiral at the centre.
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6+age*.05;
                f.stream(Particle.FLAME,floor.clone().add(Math.cos(a)*7,.15,Math.sin(a)*7),new Vector(0,.12+.26*p,0));
            }
            for(int i=0;i<10;i++){
                double a=i*Math.PI/5-age*.09,r=7.5*(1-((age*.035+i*.1)%1));
                f.stream(Particle.SMALL_FLAME,floor.clone().add(Math.cos(a)*r,.25,Math.sin(a)*r),new Vector(-Math.cos(a+.6)*.16,.015,-Math.sin(a+.6)*.16));
            }
            if(age%4==0)f.fx(Particle.LAVA,floor,2+(int)(4*p),6,.1,6,0,null);
            f.fx(Particle.DUST_COLOR_TRANSITION,floor.clone().add(0,2.2,0),4+(int)(6*p),.6,2,.6,0,cinder(1.6f));
            // The sky gate gathers a burning storm.
            Location gate=floor.clone().add(0,sky-.4,0);
            f.fxRing(gate,9.5*(.35+.65*p),Particle.FLAME,16,age*.06,null);
            if(age%4==0)f.fxRing(gate.clone().add(0,1.2,0),12,Particle.LARGE_SMOKE,8,-age*.03,null);
            if(t>.45)f.fx(Particle.FALLING_LAVA,gate,2+(int)(4*p),7,.2,7,0,null);
            if(t>.3){Location core=giantMeteor(floor,0);double form=ease((t-.3)/.7);f.fx(Particle.FLAME,core,(int)(10*form),form,form,form,.02,null);}
            if(duration-age<=12&&age%4==0){f.flash(gate,EMBER);f.fx(Particle.LAVA,gate,10,3,.3,3,0,null);}
            asteroidBelt(f,floor,age,p);
        }
        /** A belt of burning rocks circles the sky gate, from which the meteors fall. */
        private void asteroidBelt(Frame f,Location floor,int age,double grow){
            if(low()||grow<=.02)return;
            double sky=meteorSky(floor);Material[] rocks={Material.MAGMA_BLOCK,Material.NETHERRACK,Material.BLACKSTONE};
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3+age*.035,r=11.5+Math.sin(age*.07+i)*.8;
                Location at=floor.clone().add(Math.cos(a)*r,sky-1.2+Math.sin(age*.09+i*1.7)*.9,Math.sin(a)*r);
                f.prop("belt-"+i,rocks[i%3],at,(.9+.25*(i%2))*grow,age*.12+i,age*.09,i);
                if(age%2==0)f.fx(Particle.FLAME,at,3,.3,.3,.3,.01,null);
                if(age%6==i%3)f.fx(Particle.LARGE_SMOKE,at,1,.2,.2,.2,.01,null);
            }
        }
        /** Burning chunks of the giant meteor are flung out of the crater and crash down round it. */
        private void ejecta(Frame f,Location floor,int t){
            int n=low()?3:8;
            for(int i=0;i<n;i++){
                double a=i*Math.PI*2/n+.2,out=.32+.06*(i%3),up=.55+.08*(i%2),g=.08,size=.8+.2*(i%3);
                double land=2*up/g,u=Math.min(t,land),height=Math.max(0,up*u-.5*g*u*u),r=1.5+out*u;
                double fade=t<land?1:1-ease((t-land)/6.0);
                if(fade<=0)continue;
                Location at=floor.clone().add(Math.cos(a)*r,height+size*.5,Math.sin(a)*r);
                f.prop("ejecta-"+i,i%2==0?Material.MAGMA_BLOCK:Material.BLACKSTONE,at,size*fade,t*.3+i,t*.2,i);
                if(t<land){f.fx(Particle.FLAME,at,2,.2,.2,.2,.01,null);if(t%4==0)f.fx(Particle.LARGE_SMOKE,at,1,.1,.1,.1,.01,null);}
                else if(t<land+2)f.fx(Particle.BLOCK,at,10,.4,.2,.4,.2,Material.MAGMA_BLOCK.createBlockData());
            }
        }
        private void meteorFx(Frame f,Location floor,int age){
            double sky=meteorSky(floor);
            // Ambient fire around the sky gate and embers drifting down onto the field.
            f.fxRing(floor.clone().add(0,sky-.3,0),9.5,Particle.FLAME,14,age*.06,null);
            if(age%4==0&&age<120){f.fx(Particle.FALLING_LAVA,floor.clone().add(0,sky-1,0),4,6,.2,6,0,null);f.fx(Particle.WHITE_ASH,floor.clone().add(0,6,0),10,7,4,7,0,null);}
            if(age<120){
                boolean falling=age>=96;Location core=giantMeteor(floor,age);
                double size=3.4*Math.min(1,.55+age/40.0);
                drawGiant(f,core,age,size,falling);
                f.fx(Particle.FLAME,core,falling?30:10,size*.5,size*.5,size*.5,.03,null);
                f.fx(Particle.FALLING_LAVA,core,2,size*.4,.2,size*.4,0,null);
                if(age%4==0)f.fx(Particle.LARGE_SMOKE,core.clone().add(0,size*.6,0),3,size*.4,.3,size*.4,.02,null);
                if(falling){
                    if(age==96)boom(core,Sound.ENTITY_ENDER_DRAGON_GROWL,1.4f,.55f);
                    if(age%6==0)boom(core,Sound.ITEM_FIRECHARGE_USE,1.2f,.5f);
                    f.ray("hero-giant-trail",trail,core.clone().add(0,15,0),core,4.6);
                    f.fx(Particle.CAMPFIRE_SIGNAL_SMOKE,core.clone().add(0,3,0),2,1,1.5,1,.02,null);
                    f.fx(Particle.DUST_COLOR_TRANSITION,core.clone().add(0,2,0),12,1.6,2.5,1.6,0,cinder(2.6f));
                    for(int k=0;k<6;k++){double a=k*Math.PI/3+age*.4;f.stream(Particle.FLAME,core.clone().add(Math.cos(a)*size*.5,0,Math.sin(a)*size*.5),new Vector(Math.cos(a)*.08,.55,Math.sin(a)*.08));}
                }
            }
            for(int i=0;i<6;i++){
                int land=10+18*i,start=land-16;
                if(age>=start&&age<land){
                    double u=(age-start)/16.0;Location at=meteorPosition(floor,i,u);
                    Vector dir=meteorPosition(floor,i,u+.08).toVector().subtract(at.toVector());
                    if(dir.lengthSquared()<1e-4)dir=new Vector(0,-1,0);
                    smallMeteor(f,i,at,dir.normalize(),age);
                }
                if(age==land)meteorImpact(f,meteorLanding(floor,i),false);
                if(age>land&&age<140){
                    Location crater=meteorLanding(floor,i).add(0,.2,0);
                    f.fx(Particle.SMALL_FLAME,crater,3,.7,.1,.7,.01,null);
                    if(age%6==0){f.fx(Particle.LAVA,crater,1,.4,0,.4,0,null);f.fx(Particle.SMOKE,crater,2,.5,.2,.5,.01,null);}
                }
            }
            if(age<120)asteroidBelt(f,floor,age+50,age<110?1:1-ease((age-110)/10.0));
            if(age==120)meteorImpact(f,floor,true);
            if(age>=120)ejecta(f,floor,age-120);
            if(age>120){
                // The fire wall rolls outward; cinders and ash rain afterwards.
                double r=2+(age-120)*.95;
                f.fxRing(floor.clone().add(0,.3,0),r,Particle.FLAME,30,age*.1,null);
                f.fxRing(floor.clone().add(0,.9,0),r*.92,Particle.LARGE_SMOKE,12,-age*.1,null);
                f.fx(Particle.CAMPFIRE_SIGNAL_SMOKE,floor.clone().add(0,.5,0),2,.8,.3,.8,.02,null);
                f.fx(Particle.FALLING_LAVA,floor.clone().add(0,9,0),6,9,1,9,0,null);
                f.fx(Particle.DUST_COLOR_TRANSITION,floor.clone().add(0,3,0),10,6,3,6,0,cinder(1.8f));
            }
        }
        private void smallMeteor(Frame f,int i,Location at,Vector dir,int age){
            if(age==10+18*i-16||age==0)boom(at,Sound.ENTITY_GHAST_SHOOT,1f,.6f+i*.06f);
            f.prop("hero-rock-"+i,Material.MAGMA_BLOCK,at,1.55,age*.38+i,age*.29,i);
            if(!low())f.prop("hero-rock-"+i+"b",Material.BLACKSTONE,at.clone().add(Math.cos(age*.5)*.45,.25,Math.sin(age*.5)*.45),1.0,-age*.33,i,age*.2);
            f.ray("hero-trail-"+i,trail,at.clone().subtract(dir.clone().multiply(7)),at,1.6);
            f.fx(Particle.FLAME,at,16,.45,.45,.45,.03,null);
            f.fx(Particle.LAVA,at,2,.3,.3,.3,0,null);
            f.fx(Particle.LARGE_SMOKE,at.clone().subtract(dir.clone().multiply(1.6)),3,.35,.35,.35,.02,null);
            f.fx(Particle.CAMPFIRE_SIGNAL_SMOKE,at.clone().subtract(dir.clone().multiply(2.6)),1,.2,.2,.2,.01,null);
            f.fx(Particle.DUST_COLOR_TRANSITION,at.clone().subtract(dir.clone().multiply(1.2)),6,.6,.6,.6,0,cinder(2f));
            for(int k=0;k<3;k++)f.stream(Particle.FLAME,at,dir.clone().multiply(-.22).add(new Vector(Math.sin(k*2.1+age)*.05,.04,Math.cos(k*2.1+age)*.05)));
        }
        private void meteorImpact(Frame f,Location at,boolean giant){
            Location g=at.clone().add(0,.35,0);
            f.flash(g,EMBER);
            if(giant){
                f.fx(Particle.EXPLOSION_EMITTER,g,3,1.6,.4,1.6,0,null);
                f.flash(g.clone().add(0,4,0),Color.fromRGB(0xFFF1C2));
                boom(g,Sound.ENTITY_DRAGON_FIREBALL_EXPLODE,2f,.55f);
                boom(g,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1.1f,.5f);
            }else f.fx(Particle.EXPLOSION,g,4,.8,.3,.8,0,null);
            int ring=giant?44:20;double push=giant?.95:.5;
            for(int k=0;k<ring;k++){double a=k*Math.PI*2/ring;f.stream(Particle.FLAME,g,new Vector(Math.cos(a)*push,.03,Math.sin(a)*push));}
            f.fx(Particle.FLAME,g,giant?140:55,.3,.3,.3,giant?.55:.32,null);
            f.fx(Particle.LAVA,g,giant?50:14,giant?2.5:1,.4,giant?2.5:1,0,null);
            f.fx(Particle.BLOCK,g,giant?70:28,giant?2:1,.3,giant?2:1,.4,Material.MAGMA_BLOCK.createBlockData());
            f.fx(Particle.LARGE_SMOKE,g,giant?55:18,giant?3:1.2,giant?1.4:.6,giant?3:1.2,.04,null);
            if(giant){
                for(int k=0;k<14;k++){double a=k*2.39996,r=1.6*Math.sqrt((k+.5)/14);f.stream(Particle.FLAME,g.clone().add(Math.cos(a)*r,0,Math.sin(a)*r),new Vector(0,.85+.03*k,0));}
                f.fx(Particle.CAMPFIRE_COSY_SMOKE,g,22,3,1,3,.02,null);
                f.fx(Particle.DUST_COLOR_TRANSITION,g.clone().add(0,2,0),30,3,2,3,0,cinder(3f));
            }
        }

        // ---- Tempest Dominion: synchronized with ElementalSpellService.tempestDominion ----
        // Damage pulses every 20 ticks (0..100), lifts every 24, the closing burst at 116.
        private static final Color GALE=Color.fromRGB(0xB8FFE0),GALE_DEEP=Color.fromRGB(0x3DDC97);
        private double tempestHeight(int age){return 17*ease(age/10.0);}
        /** The funnel tightens before the finale, then bursts outward. */
        private double tempestSqueeze(int age){return age<104?1:age<116?1-.55*ease((age-104)/12.0):.45+(age-116)*.6;}
        private double tempestRadius(double height,double squeeze){return (1.1+height*.42)*squeeze;}
        private void tempestFx(Frame f,Location floor,int age){
            double squeeze=tempestSqueeze(age),height=tempestHeight(age),spin=age*.32;
            if(age<116){
                // The funnel body: cloud strands spiralling up, each puff thrown along the swirl.
                int strands=low()?2:3,steps=low()?8:13;
                for(int s=0;s<strands;s++)for(int j=0;j<steps;j++){
                    double h=j*height/(steps-1),r=tempestRadius(h,squeeze),a=s*Math.PI*2/strands+spin-h*.3;
                    Location at=floor.clone().add(Math.cos(a)*r,h+.3,Math.sin(a)*r);
                    f.stream(Particle.CLOUD,at,new Vector(-Math.sin(a),0,Math.cos(a)).multiply(.3+.02*h).add(new Vector(0,.05,0)));
                    if(j%3==1)f.fx(Particle.DUST,at,1,.2,.2,.2,0,new Particle.DustOptions(j%2==0?GALE:GALE_DEEP,1.7f));
                }
                // Long wind streaks curving round the funnel (Java), and a rising core.
                if(!low())for(int k=0;k<5;k++){
                    double h=(age*.7+k*3.4)%Math.max(1,height),r=tempestRadius(h,squeeze)+.7,a=spin*1.15+k*1.26;
                    Location from=floor.clone().add(Math.cos(a)*r,h+.4,Math.sin(a)*r),to=floor.clone().add(Math.cos(a+1.1)*r,h+1.6,Math.sin(a+1.1)*r);
                    f.fx(Particle.TRAIL,from,1,0,0,0,0,new Particle.Trail(to,k%2==0?GALE:Color.WHITE,14));
                }
                for(int k=0;k<3;k++){double a=age*.7+k*2.1;f.stream(Particle.WHITE_SMOKE,floor.clone().add(Math.cos(a)*.6,.2,Math.sin(a)*.6),new Vector(0,.5,0));}
                // A dust skirt where the funnel meets the ground.
                int skirt=low()?8:14;
                for(int k=0;k<skirt;k++){
                    double a=-spin*.8+k*Math.PI*2/skirt;Location at=floor.clone().add(Math.cos(a)*7.2,.2,Math.sin(a)*7.2);
                    f.stream(Particle.CLOUD,at,new Vector(-Math.sin(a)*.45+Math.cos(a)*.05,.03,Math.cos(a)*.45+Math.sin(a)*.05));
                    if(k%2==0)f.fx(Particle.DUST_PLUME,at,1,.25,.1,.25,.01,null);
                }
                f.fx(Particle.CHERRY_LEAVES,floor.clone().add(0,height*.4,0),4,4*squeeze,height*.35,4*squeeze,.02,null);
                // A storm cap with sparks above the funnel.
                Location cap=floor.clone().add(0,height+1.8,0);
                if(age%2==0){f.fxRing(cap,9.6*Math.min(1.3,squeeze),Particle.LARGE_SMOKE,10,-age*.05,null);f.fxRing(cap.clone().add(0,-.6,0),7,Particle.CLOUD,10,age*.07,null);}
                if(age%10==6&&height>8){
                    double a=age*1.7;Location spark=cap.clone().add(Math.cos(a)*5,-.5,Math.sin(a)*5);
                    f.fx(Particle.ELECTRIC_SPARK,spark,18,1.5,.6,1.5,.15,null);f.flash(spark,Color.fromRGB(0xE6FFF4));
                }
            }
            if(age%20==0&&age<=100){
                // Damage pulse: wind blades around the field and a shockwave thrown outward.
                for(int k=0;k<12;k++){double a=k*Math.PI/6+age;f.fx(Particle.SWEEP_ATTACK,floor.clone().add(Math.cos(a)*5.5,1.2+(k%3)*.6,Math.sin(a)*5.5),1,0,0,0,0,null);}
                for(int k=0;k<28;k++){double a=k*Math.PI*2/28;f.stream(Particle.CLOUD,floor.clone().add(Math.cos(a)*1.5,.6,Math.sin(a)*1.5),new Vector(Math.cos(a)*.75,.04,Math.sin(a)*.75));}
                f.fx(Particle.GUST_EMITTER_SMALL,floor.clone().add(0,2,0),1,0,0,0,0,null);
                f.fx(Particle.SMALL_GUST,floor.clone().add(0,1.5,0),6,4,1,4,0,null);
                boom(floor,Sound.ENTITY_BREEZE_WIND_BURST,1f,.7f+age*.004f);
            }
            if(age%24==0&&age>0&&age<=96)for(int k=0;k<16;k++){
                double a=k*Math.PI/8,r=k%2==0?3.5:6;
                f.stream(Particle.CLOUD,floor.clone().add(Math.cos(a)*r,.2,Math.sin(a)*r),new Vector(0,.9,0));
            }
            if(age==116){
                // The funnel bursts outward.
                for(double h:new double[]{2,7,12})f.fx(Particle.GUST_EMITTER_LARGE,floor.clone().add(0,h,0),1,0,0,0,0,null);
                f.fx(Particle.EXPLOSION,floor.clone().add(0,3,0),6,3,2,3,0,null);
                f.flash(floor.clone().add(0,6,0),Color.WHITE);
                for(double h:new double[]{1,5,10})for(int k=0;k<32;k++){double a=k*Math.PI*2/32;f.stream(Particle.CLOUD,floor.clone().add(0,h,0),new Vector(Math.cos(a)*1.3,.05,Math.sin(a)*1.3));}
                for(int k=0;k<18;k++){double a=k*Math.PI/9;f.fx(Particle.SWEEP_ATTACK,floor.clone().add(Math.cos(a)*9,1.4,Math.sin(a)*9),1,0,0,0,0,null);}
                boom(floor,Sound.ENTITY_BREEZE_WIND_BURST,1.8f,.5f);
                boom(floor,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,.7f,1.3f);
            }
            if(age>116)f.fxRing(floor.clone().add(0,.5,0),9+(age-116)*2,Particle.POOF,24,0,null);
            tempestDebris(f,floor,age,height,squeeze,spin);
        }
        private static final Material[] DEBRIS={Material.DIRT,Material.GRASS_BLOCK,Material.OAK_LOG,Material.OAK_LEAVES,
            Material.COBBLESTONE,Material.MOSS_BLOCK,Material.SAND,Material.OAK_PLANKS};
        private void tempestDebris(Frame f,Location floor,int age,double height,double squeeze,double spin){
            int pieces=low()?3:8;
            for(int i=0;i<pieces;i++){
                double size=.55+.15*(i%3);
                if(age<116){
                    // Each piece is lifted from the ground, spirals up the funnel and is thrown off the top.
                    double phase=(age*.012+i/(double)pieces)%1,h=phase*height*.92+.6,rad=tempestRadius(h,squeeze)*.9,a=spin*1.3+i*2.39996-h*.3;
                    double grow=Math.sin(Math.PI*phase)*ease(age/10.0);
                    Location at=floor.clone().add(Math.cos(a)*rad,h,Math.sin(a)*rad);
                    f.prop("debris-"+i,DEBRIS[i],at,size*Math.min(1,grow*1.6),age*.4+i,age*.3,i);
                    if(age%4==i%4)f.fx(Particle.BLOCK,at,2,.2,.2,.2,0,DEBRIS[i].createBlockData());
                }else{
                    int u=age-116;double phase=(115*.012+i/(double)pieces)%1,h0=phase*17*.92+.6,a=115*.32*1.3+i*2.39996-h0*.3;
                    double r=tempestRadius(h0,.45)+.9*u,h=Math.max(.3,h0+.3*u-.05*u*u),fade=1-ease(u/14.0);
                    if(fade<=0)continue;
                    Location at=floor.clone().add(Math.cos(a)*r,h,Math.sin(a)*r);
                    f.prop("debris-"+i,DEBRIS[i],at,size*fade,age*.5+i,age*.4,i);
                    if(u%2==0)f.fx(Particle.CLOUD,at,1,.1,.1,.1,.02,null);
                }
            }
        }
        /** The charge particles and props of each Legendary with a charge (Tempest and Leviathan have none). */
        private void chargeFx(Frame f,Location center,int age,int duration){
            switch(spell.element()){
                case FIRE->meteorChargeFx(f,center,age,duration);
                case ICE->frostChargeFx(f,center,age,duration);
                case EARTH->pillarChargeFx(f,center,age,duration);
                case DARK->abyssChargeFx(f,center,age,duration);
                case LIGHTNING->stormChargeFx(f,center,age,duration);
                default->{}
            }
        }
        private void legendaryFx(Frame f,Location floor,int age){
            switch(spell.element()){
                case FIRE->meteorFx(f,floor,age);
                case WIND->tempestFx(f,floor,age);
                case WATER->leviathanFx(f,floor,age);
                case ICE->frostFx(f,floor,age);
                case EARTH->pillarFx(f,floor,age);
                case DARK->abyssFx(f,floor,age);
                case LIGHTNING->stormFx(f,floor,age);
                default->{}
            }
        }
        /** A cosmetic lightning bolt both editions draw; it never damages, ignites or charges anything. */
        private void strike(Location at){
            if(!alive()||!context.loaded(at)||!context.plugin.getConfig().getBoolean("elemental-visuals.legendary-lightning-strikes",true))return;
            world.strikeLightningEffect(at);
        }
        /** A column of cubes leaning outward step by step: reads the same on Java and Bedrock (no tilt needed). */
        private void cubeStack(Frame f,String name,Material[] materials,Location base,double[] sizes,Vector lean,double yaw,double grow){
            if(grow<=.02)return;
            double height=0;
            for(int j=0;j<sizes.length;j++){
                double s=sizes[j]*grow;
                Location at=base.clone().add(lean.clone().multiply(j*grow)).add(0,height+s/2,0);
                f.prop(name+"-"+j,materials[j%materials.length],at,s,yaw+j*.45,0,0);
                height+=s*.92;
            }
        }

        // ---- Leviathan's Wrath: synchronized with ElementalSpellService.repeatedWaves ----
        // Five waves leave the caster 24 ticks apart and travel one block per tick for 24 blocks.
        private static final Color SEA=Color.fromRGB(0x3A8DFF),FOAM=Color.fromRGB(0xC4F4FF);
        private record WaveFront(Location start,Location front,Vector direction,long seen){}
        private final Map<Integer,WaveFront> waveFronts=new HashMap<>();
        private void leviathanFx(Frame f,Location floor,int age){
            // The caster's seal throws up a ring of spray; water rains from the ceiling ring.
            f.fxRing(floor.clone().add(0,.3,0),6.5,Particle.SPLASH,12,age*.07,null);
            if(age%4==0)f.fx(Particle.FALLING_WATER,floor.clone().add(0,13.5,0),8,7,.2,7,0,null);
            f.fx(Particle.DUST,floor.clone().add(0,2,0),4,4,2,4,0,new Particle.DustOptions(age%4==0?FOAM:SEA,1.6f));
            int newest=-1;
            for(var entry:waveFronts.entrySet())if(tick-entry.getValue().seen()<=2&&entry.getKey()>newest)newest=entry.getKey();
            for(var entry:waveFronts.entrySet()){
                WaveFront wave=entry.getValue();
                if(tick-wave.seen()>2)continue;
                Location front=wave.front();Vector dir=wave.direction(),r=right(dir);
                double progress=Math.hypot(front.getX()-wave.start().getX(),front.getZ()-wave.start().getZ());
                if(progress<.5)boom(front,Sound.ITEM_TRIDENT_RIPTIDE_3,1.3f,.75f+entry.getKey()*.06f);
                // A curling wall of water: spray, droplets and a foaming crest.
                for(int x=-4;x<=4;x++){
                    Location at=front.clone().add(r.clone().multiply(x));
                    f.fx(Particle.SPLASH,at.clone().add(0,1.2,0),6,.3,.7,.3,0,null);
                    f.fx(Particle.DUST,at.clone().add(0,.6+Math.floorMod(x,2)*1.3,0),2,.35,.45,.35,0,new Particle.DustOptions(Math.floorMod(x,2)==0?SEA:FOAM,2.2f));
                    if(x%2==0)f.stream(Particle.CLOUD,at.clone().add(0,2.5,0),dir.clone().multiply(.32).add(new Vector(0,.18,0)));
                }
                f.fx(Particle.FALLING_WATER,front.clone().add(0,2.9,0),5,4.5,.3,.3,0,null);
                f.fx(Particle.BUBBLE_POP,front.clone().add(0,1.5,0),6,4,.8,.3,.02,null);
                for(int side:new int[]{-1,1})f.stream(Particle.CLOUD,front.clone().subtract(dir.clone().multiply(1.6)).add(0,.2,0),r.clone().multiply(side*.35).add(new Vector(0,.04,0)));
                if(entry.getKey()==newest)serpent(f,front,dir,r,progress,age);
            }
            guardianSerpent(f,floor,age);
        }
        private Location coilPoint(Location floor,double a){return floor.clone().add(Math.cos(a)*7.2,2.2+Math.sin(a*2.5)*1.4,Math.sin(a)*7.2);}
        /** A second, smaller serpent swims round the seal for the whole stage, weaving up and down. */
        private void guardianSerpent(Frame f,Location floor,int age){
            if(low())return;
            double grow=ease(age/12.0)*(age<108?1:1-ease((age-108)/12.0)),lead=age*.09;
            if(grow<=.02)return;
            for(int j=1;j<=5;j++){
                double a=lead-j*.2;Location at=coilPoint(floor,a);
                f.prop("coil-"+j,Material.PRISMARINE_BRICKS,at,(1.25-j*.12)*grow,Math.atan2(-Math.sin(a),Math.cos(a)),Math.sin(a*2.5)*.3,0);
                if(j%2==1&&age%4==0)f.fx(Particle.FALLING_WATER,at,2,.4,.2,.4,0,null);
            }
            Vector heading=new Vector(-Math.sin(lead),0,Math.cos(lead));Vector side=right(heading);
            Location head=coilPoint(floor,lead+.05);double yaw=Math.atan2(heading.getX(),heading.getZ());
            f.prop("coil-head",Material.DARK_PRISMARINE,head,1.45*grow,yaw,-.1,0);
            for(int s:new int[]{-1,1})f.prop("coil-eye"+s,Material.SEA_LANTERN,head.clone().add(side.clone().multiply(s*.5*grow)).add(heading.clone().multiply(.5*grow)).add(0,.32*grow,0),.3*grow,yaw,0,0);
            f.fx(Particle.SPLASH,head,4,.4,.3,.4,0,null);
            if(age%2==0)f.fx(Particle.BUBBLE_POP,head,2,.3,.3,.3,.02,null);
        }
        /**
         * The leviathan breaches above the newest wave's crest: a prismarine body rolling in humps behind a
         * dark prismarine head with a snapping jaw, sea-lantern eyes and glass fins. Every piece is a uniform
         * cube, so Java and Bedrock draw the same silhouette.
         */
        private void serpent(Frame f,Location front,Vector dir,Vector r,double progress,int age){
            double emerge=ease(progress/5.0),yaw=Math.atan2(dir.getX(),dir.getZ()),open=.5+.5*Math.sin(age*.5);
            Location head=front.clone().add(dir.clone().multiply(.6)).add(0,1.4+3.4*emerge+Math.sin(age*.3)*.45,0);
            double neck=head.getY()-front.getY()-1.1;
            int segments=low()?4:7;
            for(int j=1;j<=segments;j++){
                double s=j/(double)segments,blend=Math.min(1,(j-1)/2.0);
                // Humps roll backwards along the body, dipping it in and out of the spray.
                double hump=(.7+2.6*(.5+.5*Math.sin(age*.4-j*1.1)))*emerge;
                Location at=front.clone().subtract(dir.clone().multiply(j*1.45)).add(r.clone().multiply(Math.sin(age*.35-j*.7)*.45*s))
                    .add(0,neck*(1-blend)+hump*blend,0);
                f.prop("hero-serpent-"+j,Material.PRISMARINE_BRICKS,at,(1.75-s*.8)*emerge,yaw+Math.sin(age*.4-j)*.25,Math.cos(age*.4-j*1.1)*.3,0);
                if(!low()&&(j==2||j==4))f.prop("hero-fin-"+j,Material.LIGHT_BLUE_STAINED_GLASS,at.clone().add(0,(1.05-s*.4)*emerge,0),(.8-s*.25)*emerge,yaw+Math.PI/4,0,Math.PI/4);
                if(j%2==1){f.fx(Particle.FALLING_WATER,at,3,.5,.3,.5,0,null);f.fx(Particle.GLOW,at,1,.4,.4,.4,0,null);}
            }
            f.prop("hero-serpent-head",Material.DARK_PRISMARINE,head,2.2*emerge,yaw,-.12,0);
            Location jaw=head.clone().add(dir.clone().multiply(1.3*emerge));
            f.prop("hero-serpent-snout",Material.PRISMARINE,jaw.clone().add(0,(.15+.35*open)*emerge,0),1.25*emerge,yaw,-.25*open,0);
            if(!low())f.prop("hero-serpent-jaw",Material.PRISMARINE_BRICKS,jaw.clone().add(0,-(.55+.45*open)*emerge,0),1.0*emerge,yaw,.3*open,0);
            for(int side:new int[]{-1,1})f.prop("hero-serpent-eye"+side,Material.SEA_LANTERN,head.clone().add(r.clone().multiply(side*.78*emerge)).add(dir.clone().multiply(.75*emerge)).add(0,.5*emerge,0),.42*emerge,yaw,0,0);
            // Spray breaks off the head and pours from the open maw.
            f.fx(Particle.SPLASH,head,10,.8,.6,.8,0,null);
            f.fx(Particle.FALLING_WATER,jaw,4,.4,.2,.4,0,null);
            f.fx(Particle.GLOW,head,3,.6,.5,.6,0,null);
            f.fx(Particle.DUST,head.clone().add(0,1.3,0),2,.8,.3,.8,0,new Particle.DustOptions(FOAM,1.8f));
        }

        // ---- Absolute Zero: synchronized with ElementalSpellService.absoluteZero ----
        // Hits at stage ages 0 (slow), 20 (freeze) and 60 (shatter); the stage lasts 80 ticks.
        private static final Color FROST=Color.fromRGB(0xD8F6FF),GLACIER=Color.fromRGB(0x7FD8FF);
        private static final Material[] ICE_CUBES={Material.PACKED_ICE,Material.BLUE_ICE,Material.ICE};
        private Location spireBase(Location floor,int i){double a=i*Math.PI/3+Math.PI/6;return floor.clone().add(Math.cos(a)*4.2,0,Math.sin(a)*4.2);}
        private Vector outward(int i,double amount){double a=i*Math.PI/3+Math.PI/6;return new Vector(Math.cos(a)*amount,0,Math.sin(a)*amount);}
        private void frostChargeFx(Frame f,Location center,int age,int duration){
            double t=age/(double)Math.max(1,duration),p=ease(t);Location floor=center.clone().add(0,.12,0);
            for(int i=0;i<10;i++){
                double a=i*Math.PI/5-age*.08,r=7.5*(1-((age*.03+i*.1)%1));
                f.stream(Particle.SNOWFLAKE,floor.clone().add(Math.cos(a)*r,.4+(i%3)*.8,Math.sin(a)*r),new Vector(-Math.cos(a+.7)*.18,.01,-Math.sin(a+.7)*.18));
            }
            f.fx(Particle.WHITE_SMOKE,floor.clone().add(0,.2,0),3,5,.1,5,.005,null);
            f.fx(Particle.DUST,floor.clone().add(0,1.5,0),4,2.5,1.5,2.5,0,new Particle.DustOptions(age%4==0?GLACIER:FROST,1.5f));
            // Frost buds swell around the ring as the air freezes.
            for(int i=0;i<(low()?3:6);i++)f.prop("hero-bud-"+i,ICE_CUBES[i%3],spireBase(floor,low()?i*2:i).add(0,.35*p,0),.7*p,age*.05+i,0,0);
            if(duration-age<=8&&age%4==0){f.flash(floor.clone().add(0,1.5,0),GLACIER);f.fx(Particle.END_ROD,floor.clone().add(0,1.5,0),12,3,1,3,.05,null);}
        }
        private void frostFx(Frame f,Location floor,int age){
            if(age<60){
                double rise=ease(age/6.0),shake=age>=54?Math.sin(age*3.1)*.06:0;
                int count=low()?3:6;
                for(int k=0;k<count;k++){
                    int i=low()?k*2:k;
                    cubeStack(f,"hero-spire-"+i,new Material[]{ICE_CUBES[i%3],Material.ICE,Material.PACKED_ICE},spireBase(floor,i).add(shake,0,0),
                        low()?new double[]{1.15,.8}:new double[]{1.25,.95,.6},outward(i,.45),i+shake,rise);
                    f.fx(Particle.END_ROD,spireBase(floor,i).add(outward(i,1)).add(0,2.6*rise,0),2,.25,.4,.25,.01,null);
                }
                cubeStack(f,"hero-core",new Material[]{Material.BLUE_ICE,Material.PACKED_ICE,Material.ICE},floor.clone().add(shake,0,0),
                    new double[]{1.9,1.45,1.0},new Vector(0,0,0),age*.03,ease((age-20)/6.0));
                // A blizzard swirls round the frozen field.
                for(double h:new double[]{.8,2.4,4.2,6.2})for(int k=0;k<10;k++){
                    double a=k*Math.PI/5+age*.12+h;
                    f.stream(Particle.SNOWFLAKE,floor.clone().add(Math.cos(a)*6.5,h,Math.sin(a)*6.5),new Vector(-Math.sin(a)*.4,-.02,Math.cos(a)*.4));
                }
                f.fx(Particle.SNOWFLAKE,floor.clone().add(0,9,0),24,6,1,6,0,null);
                f.fx(Particle.WHITE_SMOKE,floor.clone().add(0,.25,0),8,5,.15,5,.004,null);
                f.fx(Particle.DUST,floor.clone().add(0,3,0),6,5,3,5,0,new Particle.DustOptions(age%4==0?FROST:GLACIER,1.8f));
                f.fx(Particle.ITEM_SNOWBALL,floor.clone().add(0,4,0),4,5,3,5,.02,null);
                if(age==0)for(int i=0;i<6;i++){Location base=spireBase(floor,i).add(0,.3,0);
                    f.fx(Particle.BLOCK,base,20,.6,.3,.6,.3,Material.PACKED_ICE.createBlockData());f.fx(Particle.SNOWFLAKE,base,14,.4,.4,.4,.25,null);}
                if(age==20){
                    f.flash(floor.clone().add(0,2,0),GLACIER);
                    for(int k=0;k<32;k++){double a=k*Math.PI/16;f.stream(Particle.SNOWFLAKE,floor.clone().add(0,.5,0),new Vector(Math.cos(a)*.55,.03,Math.sin(a)*.55));}
                    f.fx(Particle.BLOCK,floor.clone().add(0,.5,0),40,1.2,.4,1.2,.35,Material.BLUE_ICE.createBlockData());
                    boom(floor,Sound.ENTITY_PLAYER_HURT_FREEZE,1.4f,.6f);
                }
                if(age>=54&&age%2==0)f.fx(Particle.BLOCK,floor.clone().add(0,2.5,0),8,4,2,4,.05,Material.ICE.createBlockData());
            }
            if(age==60){
                // The whole frozen field shatters at once.
                f.flash(floor.clone().add(0,3,0),Color.WHITE);f.flash(floor.clone().add(0,1,0),GLACIER);
                f.fx(Particle.EXPLOSION,floor.clone().add(0,2,0),3,2,1,2,0,null);
                for(int i=0;i<6;i++)f.fx(Particle.BLOCK,spireBase(floor,i).add(0,1.5,0),26,.5,1.2,.5,.45,Material.BLUE_ICE.createBlockData());
                f.fx(Particle.BLOCK,floor.clone().add(0,2.5,0),80,.8,2,.8,.7,Material.PACKED_ICE.createBlockData());
                f.fx(Particle.SNOWFLAKE,floor.clone().add(0,2,0),60,2,2,2,.6,null);
                f.fx(Particle.END_ROD,floor.clone().add(0,3,0),40,3,2,3,.35,null);
                f.fx(Particle.ITEM_SNOWBALL,floor.clone().add(0,2,0),40,3,2,3,.4,null);
                for(int k=0;k<40;k++){double a=k*Math.PI/20;f.stream(Particle.SNOWFLAKE,floor.clone().add(0,1.2,0),new Vector(Math.cos(a)*.85,.08,Math.sin(a)*.85));}
                boom(floor,Sound.BLOCK_AMETHYST_CLUSTER_BREAK,1.8f,.6f);
                boom(floor,Sound.BLOCK_GLASS_BREAK,1.6f,.5f);
            }
            iceCrown(f,floor,age);
            if(age>60){
                f.fx(Particle.SNOWFLAKE,floor.clone().add(0,5,0),20,6,2,6,0,null);
                f.fx(Particle.END_ROD,floor.clone().add(0,3,0),8,5,2,5,.01,null);
                f.fx(Particle.WHITE_SMOKE,floor.clone().add(0,.25,0),3,6,.1,6,.004,null);
            }
        }

        /** Six blue-ice crystals ring the crown seal; at the shatter they drop and burst on the ground. */
        private void iceCrown(Frame f,Location floor,int age){
            if(low())return;
            double grow=ease(age/10.0),fall=9.6,g=.12;int land=(int)Math.ceil(Math.sqrt(2*(fall-.6)/g));
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3+Math.min(age,60)*.02;Location top=floor.clone().add(Math.cos(a)*5,fall,Math.sin(a)*5);
                int u=age-60;
                if(u>=land){
                    if(u<land+2){Location ground=top.clone();ground.setY(floor.getY()+.5);
                        f.fx(Particle.BLOCK,ground,16,.5,.3,.5,.3,Material.BLUE_ICE.createBlockData());f.fx(Particle.SNOWFLAKE,ground,10,.4,.3,.4,.2,null);}
                    continue;
                }
                Location at=top.clone().subtract(0,u>0?.5*g*u*u:0,0);
                f.prop("crown-"+i,Material.BLUE_ICE,at,1.2*grow,a+age*.01,.4,.4);
                if(u<0&&age%4==i%4){f.fx(Particle.DRIPPING_WATER,at.clone().add(0,-.7,0),1,.1,0,.1,0,null);f.fx(Particle.SNOWFLAKE,at,2,.3,.3,.3,.01,null);}
                if(u>=0)f.fx(Particle.SNOWFLAKE,at,3,.3,.3,.3,.02,null);
            }
        }

        // ---- Titan's Judgment: synchronized with ElementalSpellService.titansJudgment ----
        // A 40-tick charge, then one slam at stage age 0 and a 44-tick aftermath. The Pillar of Judgment,
        // a deepslate obelisk crowned in gold, forms in the sky, falls on the last charge ticks and drives into the ground.
        private static final Color AMBER=Color.fromRGB(0xF6D38B);
        private static final Material[] OBELISK={Material.DEEPSLATE_BRICKS,Material.DEEPSLATE_BRICKS,Material.CHISELED_DEEPSLATE,Material.GOLD_BLOCK};
        private static final double[] OBELISK_SIZES={2.5,2.2,1.9,1.3};
        private void obelisk(Frame f,Location bottom,int age,double grow){
            double height=0;
            for(int j=0;j<OBELISK.length;j++){
                double s=OBELISK_SIZES[j]*grow;
                f.prop("hero-obelisk-"+j,OBELISK[j],bottom.clone().add(0,height+s/2,0),s,age*.02+(j%2)*.785,0,0);
                height+=s*.95;
            }
        }
        private void pillarChargeFx(Frame f,Location center,int age,int duration){
            double t=age/(double)Math.max(1,duration),p=ease(t);Location floor=center.clone().add(0,.12,0);
            // The obelisk condenses high above, then plunges over the last fourteen charge ticks.
            double fall=Math.clamp((age-(duration-14))/14.0,0,1),height=16*(1-fall*fall)+.05;
            Location bottom=floor.clone().add(0,height,0);
            obelisk(f,bottom,age,ease(age/12.0));
            for(int k=0;k<8;k++){double a=k*Math.PI/4+age*.15,r=3.2;
                f.fx(Particle.DUST,bottom.clone().add(Math.cos(a)*r,1+k*.6,Math.sin(a)*r),1,.1,.1,.1,0,new Particle.DustOptions(AMBER,1.9f));}
            f.fx(Particle.FALLING_DUST,bottom.clone().add(0,.5,0),6,1.5,.5,1.5,0,Material.DEEPSLATE.createBlockData());
            if(fall>0)f.fx(Particle.CAMPFIRE_COSY_SMOKE,bottom.clone().add(0,5,0),3,1,1.5,1,.02,null);
            // The ground trembles: stones jump, dust pillars rise on the seal.
            f.fx(Particle.BLOCK,floor.clone().add(0,.2,0),3+(int)(5*p),5,.1,5,.15,Material.STONE.createBlockData());
            if(age%4==0)for(int i=0;i<4;i++){double a=i*Math.PI/2+age*.2,r=3+((age/4+i)%4);
                f.fx(Particle.DUST_PILLAR,floor.clone().add(Math.cos(a)*r,.1,Math.sin(a)*r),6,.2,.1,.2,0,Material.DIRT.createBlockData());}
            for(int i=0;i<4;i++){double a=i*Math.PI/2+age*.1;f.stream(Particle.CLOUD,floor.clone().add(Math.cos(a)*6.5,.2,Math.sin(a)*6.5),new Vector(-Math.cos(a)*.12,.08,-Math.sin(a)*.12));}
            if(age==duration-14)boom(bottom,Sound.ENTITY_ENDER_DRAGON_FLAP,1.5f,.5f);
        }
        private void pillarFx(Frame f,Location floor,int age){
            // Driven a block into the ground, then it sinks and crumbles at the end.
            double sink=age<30?1.1:1.1+ease((age-30)/14.0)*4,crumble=age<30?1:1-ease((age-30)/14.0)*.6;
            obelisk(f,floor.clone().add(0,-sink,0),age+40,crumble);
            if(age==0){
                Location g=floor.clone().add(0,.3,0);
                f.fx(Particle.EXPLOSION_EMITTER,g,2,1.5,.3,1.5,0,null);f.flash(g.clone().add(0,1,0),AMBER);
                f.fx(Particle.BLOCK,g,90,2,.3,2,.45,Material.STONE.createBlockData());
                f.fx(Particle.BLOCK,g,60,2.5,.3,2.5,.35,Material.DIRT.createBlockData());
                f.fx(Particle.CAMPFIRE_COSY_SMOKE,g,25,3,.6,3,.03,null);
                f.fx(Particle.DUST,g.clone().add(0,1.5,0),40,3,1.5,3,0,new Particle.DustOptions(AMBER,2.4f));
                for(int k=0;k<40;k++){double a=k*Math.PI/20;f.stream(Particle.CLOUD,g,new Vector(Math.cos(a)*.8,.06,Math.sin(a)*.8));}
                boom(g,Sound.BLOCK_ANVIL_LAND,1.4f,.5f);
                boom(g,Sound.ENTITY_WARDEN_ATTACK_IMPACT,1.6f,.6f);
            }
            if(age<16){
                // Blocks of earth heave up along the shockwave front as it rolls outward.
                Material[] earth={Material.DIRT,Material.COARSE_DIRT,Material.STONE,Material.GRAVEL};
                double r=1.5+age*.75;int n=low()?4:8;
                for(int i=0;i<n;i++){
                    double a=i*Math.PI*2/n+.2+age*.02;
                    Location at=floor.clone().add(Math.cos(a)*r,.25+.55*Math.abs(Math.sin(age*.8+i)),Math.sin(a)*r);
                    f.prop("heave-"+i,earth[i%4],at,(.95-age*.035),a+age*.2,.2*Math.sin(age+i),0);
                }
            }
            if(age<20){
                // The shockwave rolls outward, throwing dust and stone.
                double r=1+age*.75;
                for(int k=0;k<16;k++){double a=k*Math.PI/8+age*.05;Location at=floor.clone().add(Math.cos(a)*r,.2,Math.sin(a)*r);
                    f.fx(Particle.CLOUD,at,1,.2,.1,.2,.02,null);if(k%2==0)f.fx(Particle.BLOCK,at,2,.3,.1,.3,.2,Material.STONE.createBlockData());}
            }
            // Boulders thrown up by the slam arc out and land, then crumble away.
            Material[] rocks={Material.STONE,Material.COBBLESTONE,Material.DEEPSLATE,Material.GRANITE,Material.ANDESITE,Material.TUFF};
            for(int i=0;i<(low()?3:6);i++){
                double a=i*1.047+.3,r0=1.8+(i%2)*.8,out=.24+.05*(i%3),up=.6+.07*(i%3),g=.06,size=.7+.18*(i%3);
                double land=2*up/g,t=Math.min(age,land),height=Math.max(0,up*t-.5*g*t*t),r=r0+out*t;
                double fade=age<34?1:1-ease((age-34)/10.0);
                Location at=floor.clone().add(Math.cos(a)*r,height+size*.5,Math.sin(a)*r);
                f.prop("hero-boulder-"+i,rocks[i],at,size*fade,age<land?age*.25+i:land*.25+i,age<land?age*.18:land*.18,i);
                if(age<land&&age%2==0)f.fx(Particle.BLOCK,at,2,.2,.2,.2,0,rocks[i].createBlockData());
                if(Math.abs(age-land)<1.01)f.fx(Particle.CLOUD,at,6,.4,.1,.4,.05,null);
            }
            // Stone spikes burst from the ground in a ring, then sink back.
            for(int i=0;i<(low()?2:4);i++){
                double a=i*Math.PI/2+.4,grow=ease((age-2)/4.0)*(age<30?1:1-ease((age-30)/12.0));
                Location base=floor.clone().add(Math.cos(a)*5.6,-.3,Math.sin(a)*5.6);
                cubeStack(f,"hero-spike-"+i,new Material[]{Material.DEEPSLATE,Material.TUFF},base,new double[]{1.4,1.0},new Vector(Math.cos(a)*.35,0,Math.sin(a)*.35),a,grow);
                if(age>=2&&age<=6)f.fx(Particle.BLOCK,base.clone().add(0,.6,0),10,.5,.3,.5,.3,Material.DEEPSLATE.createBlockData());
            }
            if(age<30){
                // Dust hangs over the field after the slam.
                f.fx(Particle.DUST,floor.clone().add(0,1.2,0),8,5,1,5,0,new Particle.DustOptions(Color.fromRGB(0x8B6B4A),2.6f));
                f.fx(Particle.FALLING_DUST,floor.clone().add(0,4,0),6,4,1.5,4,0,Material.DIRT.createBlockData());
                if(age%4==0)f.fx(Particle.CAMPFIRE_COSY_SMOKE,floor.clone().add(0,.5,0),3,4,.2,4,.01,null);
            }
            // Gold runes keep rising round the embedded pillar.
            for(int k=0;k<4;k++){double a=k*Math.PI/2+age*.2;f.stream(Particle.DUST,floor.clone().add(Math.cos(a)*1.8,.3,Math.sin(a)*1.8),new Vector(0,.15,0));}
            f.fx(Particle.DUST,floor.clone().add(0,1.5,0),4,3,1.5,3,0,new Particle.DustOptions(AMBER,1.8f));
            f.fx(Particle.WAX_ON,floor.clone().add(0,2.5,0),3,1.5,2,1.5,.02,null);
        }

        // ---- Abyssal Collapse: synchronized with ElementalSpellService.abyssalCollapse ----
        // A 30-tick charge, hits at stage ages 0 and 80 (the implosion); the stage lasts 100 ticks.
        private static final Color VOID=Color.fromRGB(0x18081F),ARCANE=Color.fromRGB(0xA36BFF);
        private double coreSize(int age){
            if(age>=80)return 0;
            if(age>=76)return 2.4*(1-.85*ease((age-76)/4.0));
            double beat=Math.exp(-age/4.0)+(age>=40?Math.exp(-(age-40)/4.0):0);
            return 2.4*(1+.08*Math.sin(age*.4))+beat*.75;
        }
        private void drawCore(Frame f,Location core,int age,double size){
            if(size<.05)return;
            f.prop("hero-core-a",Material.CRYING_OBSIDIAN,core,size,age*.08,age*.05,0);
            f.prop("hero-core-b",Material.OBSIDIAN,core,size*.9,-age*.06,.785,.785);
        }
        private void abyssChargeFx(Frame f,Location center,int age,int duration){
            double t=age/(double)Math.max(1,duration),p=ease(t);Location floor=center.clone().add(0,.12,0),core=floor.clone().add(0,4,0);
            for(int i=0;i<10;i++){double a=i*Math.PI/5+age*.05;Location at=floor.clone().add(Math.cos(a)*7,.4,Math.sin(a)*7);
                f.stream(Particle.REVERSE_PORTAL,at,core.toVector().subtract(at.toVector()).multiply(.06));}
            f.fx(Particle.SQUID_INK,floor.clone().add(0,.3,0),2,3,.1,3,.01,null);
            f.fx(Particle.SOUL,floor.clone().add(0,.3,0),2,4,.1,4,.02,null);
            f.fx(Particle.DUST,core,6,2,2,2,0,new Particle.DustOptions(age%4==0?ARCANE:VOID,2f));
            drawCore(f,core,age,2.4*p);
        }
        private void abyssFx(Frame f,Location floor,int age){
            Location core=floor.clone().add(0,4,0);double size=coreSize(age);
            drawCore(f,core,age,size);
            if(age<80){
                // An accretion disc tilted round the core; the whole field is dragged inward.
                double tilt=.35;
                for(int k=0;k<16;k++){
                    double a=k*Math.PI/8+age*.2,r=3.2+size*.3;
                    Location at=core.clone().add(Math.cos(a)*r,Math.sin(a)*r*Math.sin(tilt),Math.sin(a)*r*Math.cos(tilt));
                    f.stream(Particle.REVERSE_PORTAL,at,new Vector(-Math.sin(a)*.3,0,Math.cos(a)*.3).add(core.toVector().subtract(at.toVector()).multiply(.05)));
                    if(k%4==0)f.fx(Particle.DUST,at,1,.1,.1,.1,0,new Particle.DustOptions(k%8==0?ARCANE:VOID,2.2f));
                }
                for(int k=0;k<8;k++){double a=k*Math.PI/4+age*.31;Location at=floor.clone().add(Math.cos(a)*8,.5+(k%3)*1.5,Math.sin(a)*8);
                    f.stream(Particle.REVERSE_PORTAL,at,core.toVector().subtract(at.toVector()).multiply(.07));}
                f.fx(Particle.SOUL,floor.clone().add(0,.3,0),2,5,.1,5,.02,null);
                f.fx(Particle.PORTAL,core,16,1.5,1.5,1.5,.6,null);
                // A shadow whirlpool on the ground spirals in under the core.
                for(int k=0;k<10;k++){double a=k*Math.PI/5-age*.14;Location at=floor.clone().add(Math.cos(a)*6,.25,Math.sin(a)*6);
                    f.stream(Particle.SQUID_INK,at,new Vector(-Math.sin(a)*.3-Math.cos(a)*.12,.01,Math.cos(a)*.3-Math.sin(a)*.12));}
                f.fx(Particle.DUST,floor.clone().add(0,2,0),6,4,2,4,0,new Particle.DustOptions(VOID,2.4f));
                // Debris spirals inward and is swallowed just before the implosion.
                Material[] debris={Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.BLACKSTONE};
                for(int i=0;i<(low()?3:6);i++){
                    double pull=Math.pow(ease(age/80.0),1.5),r=(6.5-i*.4)*(1-pull)+1.2,a=i*1.047+age*(.05+.35/r);
                    Location at=core.clone().add(Math.cos(a)*r,Math.sin(age*.05+i)*2*(r/6.5),Math.sin(a)*r);
                    if(age<78)f.prop("hero-debris-"+i,debris[i%3],at,.85-.35*pull,age*.2+i,age*.13,i);
                    if(age%4==0)f.fx(Particle.REVERSE_PORTAL,at,3,.2,.2,.2,.05,null);
                }
            }
            voidTentacles(f,floor,core,age);
            if(age==0||age==40){
                f.flash(core,ARCANE);
                for(int k=0;k<24;k++){double a=k*Math.PI/12;f.stream(Particle.SQUID_INK,core,new Vector(Math.cos(a)*.45,.02,Math.sin(a)*.45));}
                if(age==40)boom(core,Sound.ENTITY_WARDEN_HEARTBEAT,1.4f,.5f);
            }
            if(age>=76&&age<80)f.fx(Particle.REVERSE_PORTAL,core,40,4,3,4,.1,null);
            if(age==80){
                // The singularity collapses and detonates.
                for(int k=0;k<5;k++)f.fx(Particle.SONIC_BOOM,core.clone().add(Math.cos(k*1.26)*1.2,(k-2)*.6,Math.sin(k*1.26)*1.2),1,0,0,0,0,null);
                f.fx(Particle.EXPLOSION_EMITTER,core,2,1,1,1,0,null);
                f.flash(core,ARCANE);f.flash(core.clone().add(0,1,0),Color.WHITE);
                f.fx(Particle.REVERSE_PORTAL,core,150,1,1,1,1,null);
                f.fx(Particle.SQUID_INK,core,60,1,1,1,.5,null);
                f.fx(Particle.SOUL,core,40,2,2,2,.2,null);
                f.fx(Particle.DUST,core,60,4,3,4,0,new Particle.DustOptions(ARCANE,2.6f));
                for(int k=0;k<32;k++){double a=k*Math.PI/16;f.stream(Particle.SQUID_INK,floor.clone().add(0,.4,0),new Vector(Math.cos(a)*.9,.03,Math.sin(a)*.9));}
                boom(core,Sound.ENTITY_WARDEN_SONIC_BOOM,1.8f,.6f);
            }
            if(age>80){f.fx(Particle.SOUL,floor.clone().add(0,.5,0),4,5,.5,5,.03,null);f.fx(Particle.PORTAL,floor.clone().add(0,1,0),6,5,1,5,.3,null);}
        }

        private static final Material[] TENTACLE={Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.AMETHYST_BLOCK};
        /** Leaning cube columns curl from the field's rim towards the singularity, sway, and retract into the collapse. */
        private void voidTentacles(Frame f,Location floor,Location core,int age){
            if(age>=80)return;
            double grow=ease(age/10.0)*(age<72?1:1-ease((age-72)/8.0));
            int n=low()?2:4;
            for(int i=0;i<n;i++){
                double a=i*Math.PI*2/n+.4+Math.sin(age*.05)*.15,sway=Math.sin(age*.12+i*1.7)*.25;
                Location base=floor.clone().add(Math.cos(a)*6.5,-.2,Math.sin(a)*6.5);
                Vector lean=new Vector(-Math.cos(a)*(1.1+sway)-Math.sin(a)*sway,0,-Math.sin(a)*(1.1+sway)+Math.cos(a)*sway);
                cubeStack(f,"tentacle-"+i,TENTACLE,base,low()?new double[]{1.2,.8}:new double[]{1.25,1.0,.7},lean,a+age*.03,grow);
                Location tip=base.clone().add(lean.clone().multiply(2*grow)).add(0,2.6*grow,0);
                if(age%2==0)f.stream(Particle.REVERSE_PORTAL,tip,core.toVector().subtract(tip.toVector()).multiply(.08));
                if(age%4==i%4)f.fx(Particle.SQUID_INK,base.clone().add(0,.4,0),2,.4,.1,.4,.02,null);
            }
        }

        // ---- Wrath of Raijin: synchronized with ElementalSpellService.storm ----
        // A 30-tick charge; strikes every 20 ticks from stage age 0 to 80, the great bolt at 100.
        private static final Color STORM=Color.fromRGB(0xBFE6FF);
        private Location drumAnchor(Location floor,int age){
            double lift=age<100?0:(age-100)*.3;
            return floor.clone().add(0,11+Math.sin(age*.1)*.3+lift,0);
        }
        private Location[] strikePoints(Location floor,int pulse){
            if(pulse>=100)return new Location[]{floor.clone(),floor.clone().add(2.2,0,1.2),floor.clone().add(-1.8,0,-1.6)};
            double a=pulse/20*1.7;return new Location[]{floor.clone().add(Math.cos(a)*3.2,0,Math.sin(a)*3.2)};
        }
        /** Six upright drums (barrels) ring a gold hub; every strike makes them swell. */
        private void drawDrums(Frame f,Location hub,int age,double grow,double spin,boolean stage){
            int last=Math.min(100,age/20*20);
            double beat=stage&&age>=last&&age-last<6?1+.3*Math.exp(-(age-last)/2.0):1;
            double fade=!stage||age<100?1:1-ease((age-100)/20.0);
            for(int i=0;i<(low()?3:6);i++){
                double a=i*Math.PI/(low()?1.5:3)+spin;
                f.prop("hero-drum-"+i,Material.BARREL,hub.clone().add(Math.cos(a)*5.2,0,Math.sin(a)*5.2),1.6*grow*beat*fade,-a,0,0);
            }
            f.prop("hero-drum-hub",Material.GOLD_BLOCK,hub,.9*grow*fade,spin*2,.785,.785);
        }
        private void boltParticles(Frame f,Location from,Location to,int seed){
            Vector span=to.toVector().subtract(from.toVector());Vector r=right(span);
            for(int k=0;k<=12;k++){
                double t=k/12.0,shift=k==0||k==12?0:Math.sin(k*2.71+seed*1.3)*.6;
                Location at=from.clone().add(span.clone().multiply(t)).add(r.clone().multiply(shift));
                f.fx(Particle.ELECTRIC_SPARK,at,2,.05,.05,.05,0,null);
                if(k%2==0)f.fx(Particle.END_ROD,at,1,.02,.02,.02,0,null);
            }
        }
        private void stormChargeFx(Frame f,Location center,int age,int duration){
            double t=age/(double)Math.max(1,duration),p=ease(t);Location floor=center.clone().add(0,.12,0);
            Location cap=floor.clone().add(0,14,0);
            for(int k=0;k<10;k++){double a=k*Math.PI/5+age*.06,r=10-5*p;Location at=cap.clone().add(Math.cos(a)*r,0,Math.sin(a)*r);
                f.stream(Particle.CLOUD,at,new Vector(-Math.cos(a)*.12-Math.sin(a)*.12,0,-Math.sin(a)*.12+Math.cos(a)*.12));}
            if(age%2==0)f.fxRing(cap.clone().add(0,.6,0),8,Particle.LARGE_SMOKE,8,-age*.04,null);
            f.fx(Particle.ELECTRIC_SPARK,floor.clone().add(0,.5,0),4+(int)(6*p),5,.2,5,.08,null);
            if(age%6==0)f.flash(cap.clone().add(Math.cos(age)*4,0,Math.sin(age)*4),STORM);
            // The drums descend from the clouds into their ring.
            drawDrums(f,floor.clone().add(0,11+7*(1-p),0),age,p,age*.03,false);
        }
        private void stormFx(Frame f,Location floor,int age){
            double spin=age*(age<100?.03:.12);
            Location hub=drumAnchor(floor,age),cap=floor.clone().add(0,14,0);
            drawDrums(f,hub,age,1,spin,true);
            if(age%2==0){f.fxRing(cap,9,Particle.LARGE_SMOKE,10,-age*.04,null);f.fxRing(cap.clone().add(0,-.8,0),6.5,Particle.CLOUD,10,age*.06,null);}
            f.fx(Particle.ELECTRIC_SPARK,cap,6,6,.6,6,.1,null);
            if(age%6==3)f.flash(cap.clone().add(Math.cos(age*1.3)*5,0,Math.sin(age*1.3)*5),STORM);
            // Arcs leap between neighbouring drums.
            if(age%4==0&&age<110)for(int k=0;k<2;k++){
                int i=(age/4+k*3)%6;double a=i*Math.PI/3+spin,b=(i+1)*Math.PI/3+spin;
                boltParticles(f,hub.clone().add(Math.cos(a)*5.2,0,Math.sin(a)*5.2),hub.clone().add(Math.cos(b)*5.2,0,Math.sin(b)*5.2),age+k);
            }
            f.fx(Particle.ELECTRIC_SPARK,floor.clone().add(0,.4,0),3,5,.2,5,.06,null);
            if(age%20==0&&age<=100){
                boolean last=age==100;
                Location[] points=strikePoints(floor,age);
                for(int k=0;k<points.length;k++){
                    Location ground=points[k];strike(ground);
                    boltParticles(f,hub,ground.clone().add(0,.2,0),age+k);
                    f.flash(ground.clone().add(0,.5,0),STORM);
                    f.fx(Particle.ELECTRIC_SPARK,ground.clone().add(0,.3,0),last?60:30,.4,.4,.4,last?.7:.4,null);
                    f.fx(Particle.LARGE_SMOKE,ground.clone().add(0,.3,0),6,.4,.2,.4,.03,null);
                }
                if(last){
                    f.fx(Particle.EXPLOSION,floor.clone().add(0,1,0),4,1.5,.5,1.5,0,null);
                    f.fx(Particle.END_ROD,floor.clone().add(0,2,0),60,2,2,2,.5,null);
                    for(int k=0;k<36;k++){double a=k*Math.PI/18;f.stream(Particle.ELECTRIC_SPARK,floor.clone().add(0,.4,0),new Vector(Math.cos(a)*.9,.05,Math.sin(a)*.9));}
                }
            }
            if(age>100)f.fx(Particle.ELECTRIC_SPARK,floor.clone().add(0,1,0),6,4,1,4,.1,null);
            thunderDragon(f,floor,age);
        }
        private Location dragonPoint(Location floor,double a,double dive){
            Location loop=floor.clone().add(Math.cos(a)*(8+Math.sin(a*3)*.8),12.6+Math.sin(a*2.2)*1.5,Math.sin(a)*(8+Math.sin(a*3)*.8));
            return dive<=0?loop:loop.add(floor.clone().add(0,1,0).toVector().subtract(loop.toVector()).multiply(dive));
        }
        /** A dragon of sea lanterns and glass coils through the storm; it plunges with the great bolt at 100 and is gone. */
        private void thunderDragon(Frame f,Location floor,int age){
            if(low()||age>=100)return;
            double grow=ease(age/12.0),dive=age<94?0:ease((age-94)/6.0),lead=age*.11;
            for(int j=1;j<=7;j++){
                double lag=dive>0?j*.09:j*.38;
                Location at=dragonPoint(floor,lead-lag,Math.max(0,dive-j*.08));
                f.prop("dragon-"+j,j%2==0?Material.LIGHT_BLUE_STAINED_GLASS:Material.SEA_LANTERN,at,(1.3-j*.09)*grow,lead*2+j,Math.sin(age*.3-j)*.3,0);
                if(j%2==1)f.fx(Particle.ELECTRIC_SPARK,at,2,.3,.3,.3,.05,null);
            }
            Location head=dragonPoint(floor,lead,dive),ahead=dragonPoint(floor,lead+.1,dive);
            Vector heading=ahead.toVector().subtract(head.toVector());if(heading.lengthSquared()<1e-6)heading=new Vector(0,-1,0);heading.normalize();
            Vector side=right(heading);double yaw=Math.atan2(heading.getX(),heading.getZ());
            f.prop("dragon-head",Material.GOLD_BLOCK,head,1.5*grow,yaw,0,0);
            for(int s:new int[]{-1,1})f.prop("dragon-horn"+s,Material.SEA_LANTERN,head.clone().add(side.clone().multiply(s*.55*grow)).add(0,.75*grow,0).subtract(heading.clone().multiply(.3*grow)),.4*grow,yaw,.5,0);
            f.fx(Particle.END_ROD,head,2,.3,.3,.3,.03,null);
            if(age%4==0)f.fx(Particle.ELECTRIC_SPARK,head,8,.5,.5,.5,.2,null);
        }
        // ---- Heaven's Descent: a golden judgment beam cannon, synchronized with ElementalSpellService.heavensDescent ----
        // Every texture is the Ancient Judge's gold seal pair and the 1 KB solar ray; props are gold blocks.
        private static final Color GOLD=Color.fromRGB(0xFFD34D),GOLD_PALE=Color.fromRGB(0xFFF4BD),GOLD_DEEP=Color.fromRGB(0xE8A317);
        private Location lanceMuzzle,lanceEnd;
        private Vector lanceDir=Z.clone();
        private int lanceCharge=24,lanceFiring=120;
        /**
         * Called by combat every tick with the live aim. The "fx-lance" clip starts on the charge's first
         * tick and each frame draws the latest muzzle, impact point and direction.
         */
        public void lance(Location muzzle,Location end,Vector dir,int age,int charge,int firing){
            if(!legendaryLight){if(age>=charge&&age<charge+firing&&age%4==0)beam(muzzle,end);return;}
            safe(()->{
                if(muzzle.getWorld()!=world||end.getWorld()!=world)return;
                lanceMuzzle=muzzle.clone();lanceEnd=end.clone();lanceDir=dir.clone().normalize();lanceCharge=charge;lanceFiring=firing;
                // Viewers are selected around the middle of the beam, so the far impact stays in range.
                refresh(muzzle.clone().add(end.toVector().subtract(muzzle.toVector()).multiply(.5)));lastTouch=tick;
                if(age==0&&!clips.containsKey("fx-lance")){emit("fx-lance",muzzle.clone(),charge+firing+20,f->lanceFx(f,f.age),1);resonance(muzzle);}
            });
        }
        private void lanceFx(Frame f,int age){
            if(lanceMuzzle==null)return;
            double configured=context.plugin.getConfig().getDouble("elemental-visuals.legendary-light.scale",1);
            double scale=Double.isFinite(configured)?Math.clamp(configured,.5,1.5):1;
            Location muzzle=lanceMuzzle,end=lanceEnd,feet=owner.getLocation().add(0,.12,0);Vector dir=lanceDir;
            Vector r=dir.clone().crossProduct(Y);if(r.lengthSquared()<1e-4)r=X.clone();r.normalize();
            Vector up=r.clone().crossProduct(dir).normalize();
            int fired=age-lanceCharge,closing=fired-lanceFiring;
            // The seal appears as a small spark on the first tick and opens fully over the charge.
            double p=.15+.85*ease(age/(double)lanceCharge),open=fired<0?p:closing<0?1:1-ease(closing/12.0);
            // The seal stands three blocks out, so the caster still sees past it to aim; never beyond the impact.
            muzzle=muzzle.clone().add(dir.clone().multiply(Math.min(1.2,muzzle.distance(end)*.5)));
            double length=muzzle.distance(end);
            // The gold seal pair opens in front of the caster, facing the aim; a third ring rides the barrel.
            double pulse=1+.04*Math.sin(age*.9);
            f.plane("hero-seal","judgment_gold_seal",muzzle,r,up,5.4*open*pulse*scale,5.4*open*pulse*scale,age*(fired<0?.05:.11));
            f.plane("hero-halo","judgment_gold_halo",muzzle.clone().add(dir.clone().multiply(.35)),r,up,7.6*open*scale,7.6*open*scale,-age*(fired<0?.04:.08));
            if(!low()&&age>=lanceCharge-8)f.plane("hero-front","judgment_gold_halo",muzzle.clone().add(dir.clone().multiply(Math.min(2.4,length*.5)*open)),r,up,3.8*open*scale,3.8*open*scale,age*.14);
            f.plane("hero-floor","judgment_gold_seal",feet,X,Z,7.5*open*scale,7.5*open*scale,age*.03);
            // Four gold capacitors orbit the seal: they spiral in while charging and fly apart at the end.
            int caps=low()?2:4;
            for(int i=0;i<caps;i++){
                double a=i*Math.PI*2/caps+age*(fired<0?.12:.22),radius=(fired<0?3.3+2.7*(1-p):closing<0?3.3:3.3+closing*.7)*scale;
                double size=(fired<0?.5*p:closing<0?.55:.55*(1-closing/12.0))*scale;
                Location at=muzzle.clone().add(r.clone().multiply(Math.cos(a)*radius)).add(up.clone().multiply(Math.sin(a)*radius));
                f.prop("cap-"+i,Material.GOLD_BLOCK,at,size,a,a*.7,0);
                if(fired>=0&&closing<0&&age%4==0)f.fx(Particle.END_ROD,at,1,.05,.05,.05,.02,null);
            }
            if(fired<0){lanceChargeFx(f,muzzle,dir,r,up,age,p);return;}
            if(closing<0){
                double width=(2.3+.25*Math.sin(age*.9))*scale*Math.min(1,(fired+1)/3.0);
                f.ray("hero-beam","judgment_gold_ray",muzzle,end,width,2);
                if(!low())f.ray("hero-core","judgment_gold_ray",muzzle,end,width*.38,2);
                // Rings of judgment run down the barrel towards the target.
                if(length>4)for(int i=0;i<(low()?1:2);i++){
                    double d=(fired*1.6+i*length/2)%length,ring=(3.4-1.2*d/length)*width/2.3;
                    Location centre=muzzle.clone().add(dir.clone().multiply(d));
                    f.plane("ring-"+i,"judgment_gold_halo",centre,r,up,ring,ring,age*(i==0?.12:-.12));
                    for(int k=0;k<(low()?5:10);k++){
                        double a=k*Math.PI*2/(low()?5:10)+age*.3;
                        f.fx(Particle.DUST,centre.clone().add(r.clone().multiply(Math.cos(a)*ring*.48)).add(up.clone().multiply(Math.sin(a)*ring*.48)),1,.02,.02,.02,0,new Particle.DustOptions(GOLD_PALE,1.3f));
                    }
                }
                f.plane("impact","judgment_gold_seal",end.clone().subtract(dir.clone().multiply(.25)),r,up,(3.6+.5*Math.sin(age*.7))*scale,(3.6+.5*Math.sin(age*.7))*scale,-age*.09);
                lanceFiringFx(f,muzzle,end,dir,r,up,age,fired,length);
            }else lanceFinaleFx(f,end,dir,r,up,age,closing,scale);
        }
        private void lanceChargeFx(Frame f,Location muzzle,Vector dir,Vector r,Vector up,int age,double p){
            // Glyphs and light converge into the muzzle from a sphere around it.
            for(int i=0;i<8;i++){
                double a=i*2.39996+age*.37,b=Math.acos(1-2*((i*.618+age*.13)%1)),rad=4.5+1.5*Math.sin(age*.3+i);
                Vector off=new Vector(Math.sin(b)*Math.cos(a),Math.cos(b),Math.sin(b)*Math.sin(a)).multiply(rad);
                f.stream(Particle.ENCHANT,muzzle,off);
                if(i%2==0)f.stream(Particle.END_ROD,muzzle.clone().add(off),off.clone().multiply(-.11));
            }
            int ring=low()?8:14;
            for(int i=0;i<ring;i++){
                double a=i*Math.PI*2/ring+age*.2,radius=2.7*p;
                Location at=muzzle.clone().add(r.clone().multiply(Math.cos(a)*radius)).add(up.clone().multiply(Math.sin(a)*radius));
                f.fx(Particle.DUST,at,1,.04,.04,.04,0,new Particle.DustOptions(i%2==0?GOLD:GOLD_PALE,1.4f));
            }
            f.fx(Particle.ELECTRIC_SPARK,muzzle,4,.8,.8,.8,.08,null);
            f.fx(Particle.GLOW,owner.getLocation().add(0,1,0),2,.6,.8,.6,0,null);
            // No flash this close to the caster's eyes: a burst of light marks the last moments instead.
            if(lanceCharge-age<=6&&age%2==0)f.fx(Particle.END_ROD,muzzle,12,.3,.3,.3,.2,null);
        }
        private void lanceFiringFx(Frame f,Location muzzle,Location end,Vector dir,Vector r,Vector up,int age,int fired,double length){
            if(fired==0){
                // Ignition: a burst of light from the seal and a shock ring round the caster's feet.
                f.fx(Particle.EXPLOSION,muzzle,1,0,0,0,0,null);
                f.fx(Particle.END_ROD,muzzle,30,.2,.2,.2,.45,null);
                f.fxRing(owner.getLocation().add(0,.2,0),2.5,Particle.CLOUD,16,0,null);
            }
            // A double helix of gold dust winds down the beam inside a wider counter-turning spiral of
            // sparkles, with streaks of light racing along it.
            int steps=low()?6:16;
            for(int k=0;k<steps;k++){
                double s=(k+(age*.35)%1)/steps*length;
                for(int strand=0;strand<2;strand++){
                    double a=age*.45+s*.55+strand*Math.PI;
                    Location at=muzzle.clone().add(dir.clone().multiply(s)).add(r.clone().multiply(Math.cos(a)*1.3)).add(up.clone().multiply(Math.sin(a)*1.3));
                    f.fx(Particle.DUST,at,1,.03,.03,.03,0,new Particle.DustOptions(strand==0?GOLD:GOLD_PALE,1.7f));
                }
                if(!low()&&k%2==0){
                    double a=-age*.3-s*.35;
                    f.fx(Particle.WAX_ON,muzzle.clone().add(dir.clone().multiply(s)).add(r.clone().multiply(Math.cos(a)*2.1)).add(up.clone().multiply(Math.sin(a)*2.1)),1,.05,.05,.05,0,null);
                }
            }
            for(int i=0;i<3;i++){
                double a=i*Math.PI*2/3+age*.6;
                f.stream(Particle.END_ROD,muzzle.clone().add(r.clone().multiply(Math.cos(a)*.6)).add(up.clone().multiply(Math.sin(a)*.6)),dir.clone().multiply(1.6));
            }
            for(int i=0;i<3;i++)f.fx(Particle.ELECTRIC_SPARK,muzzle.clone().add(dir.clone().multiply(((age*.618+i*.37)%1)*length)),2,.45,.45,.45,.06,null);
            f.fx(Particle.DUST,muzzle,5,.9,.9,.9,0,new Particle.DustOptions(GOLD_DEEP,2.2f));
            // Recoil vents behind the caster.
            for(int side=-1;side<=1;side+=2)f.stream(Particle.CLOUD,muzzle.clone().subtract(dir.clone().multiply(2.6)),dir.clone().multiply(-.25).add(r.clone().multiply(side*.12)));
            // Impact: light explodes from the struck point, chips of the struck block fly, embers pop.
            Location face=end.clone().subtract(dir.clone().multiply(.3));
            if(age%4==0){f.fx(Particle.EXPLOSION,face,1,.2,.2,.2,0,null);f.flash(face,GOLD_PALE);}
            f.fx(Particle.END_ROD,face,14,.3,.3,.3,.32,null);
            f.fx(Particle.FIREWORK,face,4,.2,.2,.2,.25,null);
            f.fx(Particle.DUST,face,10,.9,.9,.9,0,new Particle.DustOptions(GOLD,2.4f));
            f.fx(Particle.LAVA,face,1,.3,.2,.3,0,null);
            f.fx(Particle.SMOKE,face,2,.4,.4,.4,.02,null);
            Block struck=end.clone().add(dir.clone().multiply(.1)).getBlock();
            if(struck.getType().isSolid())f.fx(Particle.BLOCK,face,12,.4,.4,.4,.15,struck.getBlockData());
        }
        private void lanceFinaleFx(Frame f,Location end,Vector dir,Vector r,Vector up,int age,int closing,double scale){
            Location face=end.clone().subtract(dir.clone().multiply(.3));
            if(closing==0){
                f.fx(Particle.EXPLOSION_EMITTER,face,1,0,0,0,0,null);f.flash(face,GOLD_PALE);
                f.fx(Particle.END_ROD,face,40,.4,.4,.4,.6,null);
                f.fx(Particle.FIREWORK,face,30,.3,.3,.3,.4,null);
                f.fx(Particle.DUST,face,20,2,1.2,2,0,new Particle.DustOptions(GOLD,3f));
            }
            if(closing<16){
                double fade=1-closing/16.0,blast=(4+closing*.8)*scale;
                f.plane("blast","judgment_gold_halo",face,r,up,blast*fade,blast*fade,age*.05);
                f.plane("blast-floor","judgment_gold_seal",end.clone().add(0,.15,0),X,Z,(3+closing*.9)*scale,(3+closing*.9)*scale,-age*.04);
                f.fxRing(end.clone().add(0,.3,0),2+closing*.55,Particle.END_ROD,12,closing*.3,null);
            }
            if(closing<8)f.fx(Particle.CAMPFIRE_COSY_SMOKE,face,1,.6,.2,.6,.01,null);
        }
        public void wave(Location front,Vector direction,double radius,int wave){
            if(nativeScene!=null){safe(()->{nativeScene.wave(front,direction,radius,wave);});return;}
            if(legendaryElement){
                beginLegendary(front,121);
                WaveFront known=waveFronts.get(wave);
                Vector heading=direction.clone().setY(0);
                if(heading.lengthSquared()<1e-6)heading=forward.clone();
                waveFronts.put(wave,new WaveFront(known==null?front.clone():known.start(),front.clone(),heading.normalize(),tick));
                return;
            }
            Clip previous=clips.get("wave:"+wave);if(previous!=null&&tick-previous.start()<2)return;
            Location at=front.clone();Vector r=right(direction);
            if(previous==null)resonance(at);
            emit("wave:"+wave,at,3,f->{
                boolean dragon=spell==ElementalSpell.LEVIATHANS_WRATH;
                f.plane("front",hero,at.clone().add(0,dragon?1.9:1.3,0),r,Y,dragon?6.5:radius*2,dragon?5.5:4.5,0);
                if(dragon)f.plane("hero-profile",hero,at.clone().add(direction.clone().multiply(-.8)).add(0,1.9,0),direction,Y,5,4.7,0);
                for(int i:new int[]{-1,1})f.plane("crest"+i,accent,at.clone().add(r.clone().multiply(i*radius*.55)).add(direction.clone().multiply(-.6)).add(0,.7,0),r,Y,radius*.95,2.5,i*.18);
                f.plane("foam",ground,at.clone().add(0,.04,0),r,direction,radius*2,3.2,0);
            });
        }
        public void storm(Location at,int age){
            if(nativeScene!=null){safe(()->{nativeScene.storm(at,age);});return;}
            if(legendaryElement){beginLegendary(at,121);return;}
            if(age%4!=0&&age!=100)return;
            emit("storm",at,6,f->{
                if(age<100)f.plane("hero",hero,at.clone().add(0,8.5,0),side,Y,8.5,5.5,.025*Math.sin(age*.1));
                if(age%20<4||age==100){
                    f.ray("bolt-a","elemental_lightning_trail",at.clone().add(0,12,0),at,.65);
                    f.plane("fork",accent,at.clone().add(0,5,0),side,Y,age==100?6:4,10,0);
                    f.plane("branch",accent,at.clone().add(0,4.2,0),forward,Y,age==100?5:3,8,.15);
                }
                f.plane("footprint",ground,at.clone().add(0,.12,0),X,Z,12,12,0);
            });
        }
        public void status(LivingEntity target,String key,int count,int duration,BooleanSupplier active){
            if(nativeScene!=null){safe(()->{nativeScene.status(target,key,count,duration,active);});return;}
            if(!Set.of("burn","wet","frost","air","radiance","curse","curse_mark","shock","break").contains(key))return;
            String id="status:"+target.getUniqueId()+":"+key;claim(id);
            Element element=switch(key){case "burn"->Element.FIRE;case "wet"->Element.WATER;case "frost"->Element.ICE;case "air"->Element.WIND;case "radiance"->Element.LIGHT;case "shock"->Element.LIGHTNING;case "break"->Element.EARTH;default->Element.DARK;};
            emit(id,target.getLocation(),duration,f->{
                if(!target.isValid()||target.isDead()||target.getWorld()!=world||!active.getAsBoolean()){clear(id);return;}
                Location point=target.getLocation().add(.3,Math.min(.8,target.getHeight()*.45),0);
                f.plane("mark","elemental_"+element.id()+"_mark",point,side,Y,.24,.32,0);
                for(int i=0;i<Math.min(3,count);i++)f.dust(point.clone().add(i*.1-.1,.23,0),color(element),.6f);
            });
        }
        public void cage(LivingEntity target,int duration,BooleanSupplier active){
            if(nativeScene!=null){safe(()->{nativeScene.cage(target,duration,active);});return;}
            String id="cage:"+target.getUniqueId();claim(id);
            emit(id,target.getLocation(),duration,f->{
                if(!target.isValid()||target.isDead()||target.getWorld()!=world||!active.getAsBoolean()){clear(id);return;}
                Location mid=target.getLocation().add(0,Math.min(1.3,target.getHeight()/2),0);
                String model=spell.element()==Element.WATER?"elemental_bubble_prison":"elemental_crystal_prison";
                f.plane("front",model,mid,X,Y,1.6,Math.min(3,target.getHeight()+.4),0);
                f.plane("side",model,mid,Z,Y,1.6,Math.min(3,target.getHeight()+.4),0);
                if(grand())f.plane("crown",accent,mid.clone().add(0,.35,0),side,Y,1.8,Math.min(3,target.getHeight()+.7),0);
            });
        }
        public void buff(LivingEntity target,String key,int duration,BooleanSupplier active){
            if(nativeScene!=null){safe(()->{nativeScene.buff(target,key,duration,active);});return;}
            String id="buff:"+target.getUniqueId()+":"+key;claim(id);
            emit(id,target.getLocation(),duration,f->{
                if(!target.isValid()||target.isDead()||target.getWorld()!=world||!active.getAsBoolean()){clear(id);return;}
                Location mid=target.getLocation().add(0,.9,0);Vector r=right(target.getLocation().getDirection());
                for(int i:new int[]{-1,1})f.plane("shell"+i,signature,mid.clone().add(r.clone().multiply(i*.5)),r,Y,.65,1.2,spell.element()==Element.WIND?f.age*.04:0);
            });
        }
        public void summon(LivingEntity entity,int age){
            if(nativeScene!=null){safe(()->{nativeScene.summon(entity,age);});return;}
            if(age%8!=0)return;Location at=entity.getLocation().add(0,.05,0);
            emit("summon:"+entity.getUniqueId(),at,10,f->{if(!entity.isValid()){clear("summon:"+entity.getUniqueId());return;}
                f.plane("base",signature,entity.getLocation().add(0,.05,0),X,Z,1.6,1.6,0);
                if(age==0)f.plane("rise",signature,entity.getLocation().add(0,1,0),side,Y,1.4,2.1,0);
                if(grand())for(int i:new int[]{-1,1}){
                    double a=(age+f.age)*.035+i*Math.PI/2;
                    f.plane("aura"+i,accent,entity.getLocation().add(Math.cos(a)*.7,1.1,Math.sin(a)*.7),side,Y,.65,1.6,i*.2);
                }
            });
        }
        public void wall(BoundingBox box){
            if(nativeScene!=null){safe(()->{nativeScene.wall(box);});return;}
            Location at=box.getCenter().toLocation(world);Vector r=box.getWidthX()>box.getWidthZ()?X:Z;
            emit("wall",at,6,f->f.bedrockPlane("elemental_stone_wall",at,r,Y,5,3));
        }
        public void phoenix(){
            if(nativeScene!=null){safe(()->{nativeScene.phoenix();});return;}
            Location at=owner.getLocation().add(0,1.2,0);
            resonance(at);
            emit("phoenix",at,28,f->{
                double t=f.age/28.0,spread=.75+.25*ease(Math.min(1,t*3));
                f.plane("wings",hero,at.clone().add(0,1+t*2.4,0),side,Y,7*spread*(1-t*.3),5.3*(1-t*.25),.035*Math.sin(t*8));
                for(int i:new int[]{-1,1})f.plane("feather"+i,accent,at.clone().add(side.clone().multiply(i*(1.3+t*1.2))).add(0,.3+Math.sin(t*Math.PI)*1.2,0),side,Y,1.5*(1-t*.4),2.8*(1-t*.3),i*(.2+t*.4));
                if(f.age<12)f.plane("ashes",ground,at.clone().add(0,-1.1,0),X,Z,6*(.5+ease(f.age/12.0)*.5),6,0);
            });
        }
    }

    private final class Frame {
        final Scene scene;final String key;final int age;int dustCount;
        final Set<String> used=new HashSet<>();
        Frame(Scene scene,String key,int age){this.scene=scene;this.key=key;this.age=age;}
        void finish(){for(String id:List.copyOf(scene.pieces.keySet()))if(id.startsWith(key+"/")&&!used.contains(id))scene.release(id);}
        private MythicLineVisuals sprite(String name,String model,Location at){
            String id=key+"/"+name;used.add(id);Piece value=scene.pieces.get(id);
            if(value!=null&&!value.model().equals(model)){scene.release(id);value=null;}
            if(value!=null)return value.display();
            UUID world=at.getWorld().getUID();int importance=priority(key,name),cap=scene.authoredLegend()?(scene.spectacle()?(low()?10:32):low()?7:12):scene.spell.ultimate()?12:scene.grand()?8:6;
            if(scene.pieces.size()>=cap){
                var victim=scene.pieces.entrySet().stream().filter(e->e.getValue().priority()<importance).min(Comparator.comparingInt(e->e.getValue().priority())).orElse(null);
                if(victim==null)return null;scene.release(victim.getKey());
            }
            if(worldPlanes.getOrDefault(world,0)>=setting("max-planes-per-world",128,16,256)){
                // Under load retire a small status/flash before losing a cast's
                // defining silhouette. Never exceed the world-wide hard cap.
                Scene victimScene=null;String victimKey=null;int lowest=importance;
                for(Scene candidate:scenes)for(var e:candidate.pieces.entrySet())if(e.getValue().world().equals(world)&&e.getValue().priority()<lowest){victimScene=candidate;victimKey=e.getKey();lowest=e.getValue().priority();}
                if(victimScene==null)return null;victimScene.release(victimKey);
            }
            MythicLineVisuals display=new MythicLineVisuals(at,model);scene.pieces.put(id,new Piece(model,display,world,importance));worldPlanes.merge(world,1,Integer::sum);return display;
        }
        void plane(String name,String model,Location at,Vector r,Vector up,double width,double height,double angle){
            if(width<.03||height<.03||!context.loaded(at)||!scene.views.hasViewers())return;
            scene.renderPriority=priority(key,name);
            boolean drawn=false;
            boolean authored=!low()||scene.authoredLegend();
            if(authored){
                if(scene.views.hasJavaViewers()){
                    MythicLineVisuals display=sprite(name,model,at);
                    if(display!=null){display.plane(scene.views,at,r,up,width,height,angle,key.equals("projectile")||key.startsWith("wave:")||key.equals("telegraph")||key.equals("meteor-preview")?0:key.startsWith("fx")?2:4);drawn=true;}
                    else sendPlane(model,at,r,up,width,height,angle);
                }else sendPlane(model,at,r,up,width,height,angle);
            }
            if(!authored||(!drawn&&scene.views.hasJavaViewers())||scene.views.needsSealFallback()){
                int n=scene.spell.element()==Element.ICE?6:4;
                for(int i=0;i<n;i++){
                    double a=angle+i*Math.PI*2/n;
                    Location edge=at.clone().add(r.clone().multiply(Math.cos(a)*width*.4)).add(up.clone().multiply(Math.sin(a)*height*.4));
                    dust(edge,color(scene.spell.element()),1.1f);
                }
            }
        }
        /** Animated seal: native Bedrock spin matches the continuously interpolated Java plane. */
        void planeSpin(String name,String model,Location at,Vector r,Vector up,double diameter,double angle,double angularVelocity){
            if(diameter<.03||!context.loaded(at)||!scene.views.hasViewers())return;
            scene.renderPriority=priority(key,name);
            boolean drawn=false;
            if(!low()||scene.authoredLegend()){
                if(scene.views.hasJavaViewers()){
                    MythicLineVisuals display=sprite(name,model,at);
                    if(display!=null){display.planeSpin(scene.views,at,r,up,diameter,diameter,angle,angularVelocity);drawn=true;}
                }
                if(!drawn){
                    Vector rr=r.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
                    Vector uu=up.clone().multiply(Math.cos(angle)).subtract(r.clone().multiply(Math.sin(angle)));
                    scene.views.linePlaneSpin(model,at,diameter,diameter,rr,uu,angularVelocity);
                }
            }
            if((low()&&!scene.authoredLegend())||(!drawn&&scene.views.hasJavaViewers())||scene.views.needsSealFallback()){
                for(int i=0;i<4;i++){
                    double a=angle+i*Math.PI/2;
                    Location edge=at.clone().add(r.clone().multiply(Math.cos(a)*diameter*.4)).add(up.clone().multiply(Math.sin(a)*diameter*.4));
                    dust(edge,color(scene.spell.element()),1.1f);
                }
            }
        }
        private void sendPlane(String model,Location at,Vector r,Vector up,double width,double height,double angle){
            Vector rr=r.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
            Vector uu=up.clone().multiply(Math.cos(angle)).subtract(r.clone().multiply(Math.sin(angle)));
            scene.views.linePlane(model,at,width,height,rr,uu);
        }
        void bedrockPlane(String model,Location at,Vector r,Vector up,double width,double height){scene.renderPriority=3;if(!low()&&context.loaded(at))scene.views.linePlane(model,at,width,height,r,up);}
        void ray(String name,String model,Location from,Location to,double width){ray(name,model,from,to,width,0);}
        /** {@code ticks} above zero interpolates the Java display, for a beam that sweeps rather than pulses. */
        void ray(String name,String model,Location from,Location to,double width,int ticks){
            if(width<.02||!context.loaded(from)||!context.loaded(to)||!scene.views.hasViewers())return;
            scene.renderPriority=priority(key,name);
            boolean drawn=false;
            if(!low()||scene.authoredLegend()){
                if(scene.views.hasJavaViewers()){
                    MythicLineVisuals display=sprite(name,model,from);
                    if(display!=null){display.ray(scene.views,from,to,width,ticks);drawn=true;}
                }
                if(!drawn){Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length>.01)scene.views.lineRay(model,from.clone().add(delta.clone().multiply(.5)),width,length,delta.normalize());}
            }
            if((low()&&!scene.authoredLegend())||(!drawn&&scene.views.hasJavaViewers())||scene.views.needsSealFallback()){
                Vector delta=to.toVector().subtract(from.toVector());for(int i=0;i<8;i++)dust(from.clone().add(delta.clone().multiply(i/7.0)),color(scene.spell.element()),1.1f);
            }
        }
        void dust(Location at,Color color,float size){if(dustCount++>=8||age%4!=0||!context.loaded(at))return;scene.renderPriority=0;scene.views.dust(at,color,size);}
        /** A solid vanilla chunk: a Java display plus a Bedrock cube rig with the same full pose. */
        void prop(String name,Material material,Location at,double size,double yaw,double pitch,double roll){
            if(size<.05||!context.loaded(at)||!scene.views.hasViewers())return;
            scene.renderPriority=priority(key,name);
            MythicLineVisuals display=sprite(name,"block:"+material.getKey().getKey(),at);
            if(display!=null)display.prop(scene.views,at,new Quaternionf().rotateYXZ((float)yaw,(float)pitch,(float)roll),size,(float)-Math.toDegrees(yaw),key.startsWith("fx")?2:4);
        }
        int fxCost;
        /**
         * Vanilla particles, which Geyser also shows on Bedrock. Bounded per frame and per
         * viewer per server tick (elemental-visuals.legendary-particles-per-viewer-tick).
         */
        void fx(Particle type,Location at,int count,double dx,double dy,double dz,double speed,Object data){
            if(count<0||scene.closed||at.getWorld()!=scene.world||!context.loaded(at))return;
            Class<?> wanted=type.getDataType();
            if(wanted==Void.class?data!=null:data==null||!wanted.isInstance(data))return;
            if(low()&&count>1)count=Math.max(1,count/2);
            int cost=Math.max(1,count);
            if(fxCost+cost>(low()?120:420))return;
            if(fxTick!=tick){fxTick=tick;fxCounts.clear();}
            int cap=setting("legendary-particles-per-viewer-tick",low()?160:450,32,2000);
            boolean sent=false;
            for(Player viewer:scene.fxViewers()){
                if(!viewer.isOnline()||viewer.getWorld()!=scene.world)continue;
                UUID id=viewer.getUniqueId();int used=fxCounts.getOrDefault(id,0);
                if(used+cost>cap)continue;
                try{viewer.spawnParticle(type,at,count,dx,dy,dz,speed,data,true);}catch(IllegalArgumentException unsupported){return;}
                fxCounts.put(id,used+cost);sent=true;
            }
            if(sent)fxCost+=cost;
        }
        void fx(Particle type,Location at,int count,double spread,double speed){fx(type,at,count,spread,spread,spread,speed,null);}
        /** A single particle launched with this velocity (blocks per tick). */
        void stream(Particle type,Location at,Vector velocity){fx(type,at,0,velocity.getX(),velocity.getY(),velocity.getZ(),1,null);}
        void fxRing(Location center,double radius,Particle type,int points,double phase,Object data){
            if(low())points=Math.max(4,points/2);
            for(int i=0;i<points;i++){double a=phase+i*Math.PI*2/points;fx(type,center.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),1,.05,.05,.05,.004,data);}
        }
        void flash(Location at,Color color){fx(Particle.FLASH,at,1,0,0,0,0,color);}
    }
}
