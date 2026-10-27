import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;
import java.util.*;

/** Visible telegraphs, real particle packets, arm-following rays and shared hits. */
public class AcheronEffectsProbe extends AcheronSplitProbe {
    long particlePackets()throws Exception{
        Object listener=actor.connection;java.lang.reflect.Field field=null;
        for(Class<?> type=listener.getClass();type!=null;type=type.getSuperclass())try{field=type.getDeclaredField("connection");break;}catch(NoSuchFieldException ignored){}
        if(field==null)throw new AssertionError("Missing native player connection");field.setAccessible(true);
        net.minecraft.network.Connection connection=(net.minecraft.network.Connection)field.get(listener);
        return ((io.netty.channel.embedded.EmbeddedChannel)connection.channel).outboundMessages().stream()
            .filter(packet->packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket).count();
    }
    Location point(Object ray,String endpoint)throws Exception{return (Location)call(ray,endpoint,new Class<?>[]{});}
    List<?> rays()throws Exception{return (List<?>)call(controller,"spinQuills",new Class<?>[]{unit.getClass()},unit);}
    Map<?,?> planes()throws Exception{return (Map<?,?>)get(get(controller,"sigils"),"planes");}
    void spin()throws Exception{put(unit,"mark",mob.getLocation().add(0,0,10));call(controller,"release",new Class<?>[]{unit.getClass()},unit);}
    void protectedSpin(Object spirit,double oldDamage,double newDamage)throws Exception{
        Mob source=(Mob)get(spirit,"mob");Location at=source.getLocation().add(0,0,1);
        resetPlayer(at);protectionFour();
        call(controller,"hit",new Class<?>[]{Player.class,double.class,Entity.class},player,oldDamage,source);
        double before=200-player.getHealth();
        resetPlayer(at);protectionFour();put(spirit,"mark",at.clone());
        call(controller,"release",new Class<?>[]{spirit.getClass()},spirit);
        call(controller,"spinPulse",new Class<?>[]{spirit.getClass()},spirit);
        double after=200-player.getHealth();
        check(before>0&&after<newDamage&&Math.abs(after-before*3)<.001,"full diamond Protection IV takes exactly triple previous spin damage: "+before+" -> "+after);
        check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==(int)(newDamage/4)),"Protection IV armor wears once at new spin strength");
        player.setNoDamageTicks(0);call(controller,"spinPulse",new Class<?>[]{spirit.getClass()},spirit);
        check(Math.abs(200-player.getHealth()-after)<.001,"protected player cannot take repeated damage from the same spin");
    }
    void protectionFour(){
        var armor=new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(Material.DIAMOND_BOOTS),new org.bukkit.inventory.ItemStack(Material.DIAMOND_LEGGINGS),new org.bukkit.inventory.ItemStack(Material.DIAMOND_CHESTPLATE),new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET)};
        for(var item:armor)item.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);
        player.getInventory().setArmorContents(armor);
    }
    @Override void mechanics()throws Exception{
        super.mechanics();
        fresh();resetPlayer(pad.clone().add(0,0,4));long before=particlePackets();put(unit,"snareAt",0L);riverTicks(1);
        check(particlePackets()>before,"warning sends real pack-independent bright particle packets");
        check(planes().keySet().stream().anyMatch(k->k.toString().startsWith("snare:")),"large warning plane exists before release");
        Object plane=planes().entrySet().stream().filter(e->e.getKey().toString().startsWith("snare:")).findFirst().orElseThrow().getValue();
        check(player.canSee((Entity)plane),"accepted Java player can see the full-bright warning display");
        // Break the caster's eye ray AFTER marking. The already visible ground
        // circle must still bind a player standing directly on its own mark.
        for(int x=-2;x<=2;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+2).setType(Material.STONE,false);
        check(!mob.hasLineOfSight(player),"fixture interrupts giant receiver eye LOS after warning commit");riverTicks(37);
        check(roots().size()==1,"standing on marked ground is rooted even when caster eye LOS changes");
        check((long)get(unit,"snareBurstUntil")>now&&planes().keySet().stream().anyMatch(k->k.toString().startsWith("snare:")),"impact circle remains visible after release instead of vanishing");
        moveBlocked();riverTicks(10);
        check(planes().keySet().stream().noneMatch(k->k.toString().startsWith("snare:"))&&roots().size()==1,"brief impact flash cleans up while binding stays active");
        for(int x=-2;x<=2;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+2).setType(Material.AIR,false);
        call(controller,"clear",new Class<?>[]{});

        fresh();resetPlayer(pad.clone().add(0,0,4));
        org.bukkit.entity.Pig mount=world.spawn(player.getLocation(),org.bukkit.entity.Pig.class);mount.setAI(false);mount.addPassenger(player);
        check(player.isInsideVehicle(),"mounted root fixture enters a real vehicle");
        call(controller,"root",new Class<?>[]{org.bukkit.entity.Player.class,unit.getClass()},player,unit);
        check(!player.isInsideVehicle()&&roots().size()==1,"binding dismounts player so vehicles cannot carry them out");
        check(!mount.addPassenger(player),"rooted player cannot remount a moving vehicle");
        call(controller,"clear",new Class<?>[]{});mount.remove();
        fresh();resetPlayer(pad.clone().add(-10,0,-10));spin();List<?> first=rays();check(first.size()==22,"spin emits two eleven-ray wrist fans");
        Location from=point(first.get(0),"from");check(from.getY()-mob.getY()>3,"ray fan originates at the authored arm rather than feet");
        var geometry=Class.forName("com.example.voidscape.boss.BossHitbox").getDeclaredMethod("bonePoints",String.class,String.class,double.class,Location.class,double.class,Set.class);geometry.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Location> bones=(Map<String,Location>)geometry.invoke(null,"arkhaia_acheron","cast_active",0.0,mob.getLocation(),4.0,Set.of("weapon_r","weapon_l"));
        check(from.distance(bones.get("weapon_r"))<1e-7,"fan is anchored to actual authored wrist pivot");
        // Advancing one animation tick must rotate the actual hand origin 45deg.
        put(controller,"clock",now+1);List<?> next=rays();put(controller,"clock",now);
        Vector a=from.toVector().subtract(mob.getLocation().toVector()).setY(0),b=point(next.get(0),"from").toVector().subtract(mob.getLocation().toVector()).setY(0);
        check(Math.abs(Math.toDegrees(a.angle(b))-45)<.05,"quill origins rotate with the authored 45-degree-per-tick arm motion");
        double shortest=Double.POSITIVE_INFINITY,longest=0;for(Object ray:first){double length=point(ray,"from").distance(point(ray,"to"));shortest=Math.min(shortest,length);longest=Math.max(longest,length);}
        check(longest-shortest>2,"quill fan mixes short and long visible rays");
        before=particlePackets();call(controller,"spinPulse",new Class<?>[]{unit.getClass()},unit);
        check(planes().keySet().stream().filter(k->k.toString().startsWith("quill:")).count()==22,"each quill has its own rendered ray plane");
        check(particlePackets()>before,"spin sends travelling vanilla sparks as well as custom ray textures");
        Object rake=first.get(0);Location contact=point(rake,"to");resetPlayer(contact.clone().subtract(0,.3,0));
        double distance=Math.hypot(player.getX()-mob.getX(),player.getZ()-mob.getZ());check(distance>3.5,"ray contact fixture stands outside ground vortex");
        call(controller,"spinPulse",new Class<?>[]{unit.getClass()},unit);check(player.getHealth()==164,"visible ground-raking quill contact deals buffed main spin damage");
        for(int i=0;i<3;i++){player.setNoDamageTicks(0);call(controller,"spinPulse",new Class<?>[]{unit.getClass()},unit);}
        check(player.getHealth()==164,"multiple quill beams share one victim hit per spin");
        resetPlayer(pad.clone().add(-10,0,-10));riverTicks(25);
        check(planes().keySet().stream().noneMatch(k->k.toString().startsWith("quill:")),"quill planes disappear at end of spin before recovery can damage");

        fresh();protectedSpin(unit,12,36);
        fresh();call(controller,"split",new Class<?>[]{unit.getClass()},unit);
        check(echoes().size()==2,"protected echo damage fixture summons a real pair");
        protectedSpin(echoes().get(0),20,60);

        fresh();resetPlayer(pad.clone().add(-10,0,-10));spin();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)if(Math.abs(x)>=6||Math.abs(z)>=6)
            for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+z).setType(Material.STONE,false);
        for(Object ray:rays()){
            Location start=point(ray,"from"),end=point(ray,"to");Vector delta=end.toVector().subtract(start.toVector());double length=delta.length();
            if(length>.01){var hit=world.rayTraceBlocks(start,delta.clone().normalize(),Math.max(0,length-.03),FluidCollisionMode.NEVER,true);check(hit==null,"rendered quill is clipped at solid wall");}
        }
        call(controller,"clear",new Class<?>[]{});check(planes().isEmpty()&&roots().isEmpty(),"cleanup removes all ray planes, circles and root modifiers");
        check(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()>0,"movement is restored after effect cleanup");
    }
}
