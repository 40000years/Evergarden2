package com.example.voidscape.world;

import org.bukkit.*;
import com.example.voidscape.world.OverworldSanctuaries.Site;

/** Matches the reserved combat floor in all six shipped sanctuary plans. */
public record SanctuaryArena(Site site,World world) {
    public static final double RADIUS=34;
    public Location center(){return new Location(world,site.x()+.5,site.kind().floor+(site.kind()==OverworldSanctuaries.Kind.BANSHEE?5:1),site.z()+27.5);}
    /** An active fight includes the air above its floor. Jumping never removes a target. */
    public boolean contains(Location p){Location c=center();return p!=null&&p.getWorld()==world&&Double.isFinite(p.getY())&&p.getY()>=c.getY()-.25
        &&Math.hypot(p.getX()-c.getX(),p.getZ()-c.getZ())<=RADIUS;}
    /** Idle sculptures still require an attack from the authored combat floor. */
    public boolean containsActivation(Location p){return contains(p)&&p.getY()<=center().getY()+(site.kind()==OverworldSanctuaries.Kind.COCYTUS?12:2.2);}
}
