package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import java.util.List;

/** Five upright dials, a horizontal master clock and independently moving hands. */
final class ChronosLineVisuals {
    private final EffectEngine.Effect effect;
    private final Location base,outer;
    private final List<Location> faces;
    private final List<Vector> axes;
    private final Vector right,outerUp;
    private final MythicLineVisuals[][] dials=new MythicLineVisuals[6][3];
    private final MythicLineVisuals[][] beams=new MythicLineVisuals[2][5];
    private final MythicLineVisuals[] links=new MythicLineVisuals[12],ripples=new MythicLineVisuals[2];
    private final MythicLineVisuals glyph;
    ChronosLineVisuals(EffectEngine.Effect effect,Location base,List<Location> faces,List<Vector> axes,
                       Location outer,Vector right,Vector outerUp) {
        this.effect=effect;this.base=base.clone();this.faces=faces;this.axes=axes;
        this.outer=outer.clone();this.right=right;this.outerUp=outerUp;
        String[] models={"chronos_dial","chronos_minute","chronos_hour"};
        for(int i=0;i<6;i++)for(int j=0;j<3;j++)dials[i][j]=new MythicLineVisuals(effect,i<5?faces.get(i):outer,models[j]);
        glyph=new MythicLineVisuals(effect,base,"chronos_dial");
        for(int i=0;i<2;i++)ripples[i]=new MythicLineVisuals(effect,base,"chronos_ripple");
    }
    void frame(MythicVisuals visuals,int age,boolean firing) {
        if(age<160&&age%4==0) {
            for(int i=0;i<6;i++) {
                Location at=i<5?faces.get(i):outer;
                Vector x=i<5?axes.get(i):right,y=i<5?MythicLineVisuals.Y:outerUp;
                double diameter=(i<5?8:22)*2*512/480;
                double turn=(age<88?age*.055:-(age-88)*.12)+i*.35;
                dials[i][0].plane(visuals,at,x,y,diameter,0);
                // Small offsets prevent coplanar depth flicker without changing the clock's position.
                Vector normal=x.clone().crossProduct(y).normalize();
                dials[i][1].plane(visuals,at.clone().add(normal.clone().multiply(.035)),x,y,diameter,turn);
                dials[i][2].plane(visuals,at.clone().add(normal.multiply(.07)),x,y,diameter,turn*.22+1);
            }
            glyph.plane(visuals,base.clone().add(0,.08,0),MythicLineVisuals.X,MythicLineVisuals.Z,26,-age*.012);
        }
        if(age%2==0&&age<160) {
            int variant=age>=100?1:0;
            for(int i=0;i<5;i++) {
                if(firing) {
                    if(beams[variant][i]==null)beams[variant][i]=new MythicLineVisuals(effect,faces.get(i),variant==0?"chronos_ray":"chronos_echo");
                    beams[variant][i].ray(visuals,faces.get(i),base.clone().add(0,1,0),.75);
                } else for(var group:beams)if(group[i]!=null)group[i].hide();
            }
        }
        if(age==160) {
            for(var dial:dials)for(var plane:dial)plane.close();
            for(var group:beams)for(var beam:group)if(beam!=null)beam.close();
            for(var link:links)if(link!=null)link.close();glyph.close();
        }
        if(age>=160&&age<=190&&age%2==0) {
            ripples[0].plane(visuals,base.clone().add(0,.8,0),MythicLineVisuals.X,MythicLineVisuals.Z,
                (1+(age-160)*.5)*2*512/474,-age*.06);
            ripples[1].plane(visuals,base.clone().add(0,1.5,0),MythicLineVisuals.X,MythicLineVisuals.Z,
                (1+(age-160)*.35)*2*512/474,age*.06);
        }
        if(age==192)for(var ripple:ripples)ripple.close();
    }
    void crystalFallback(MythicVisuals visuals,List<Location> emitters,Location face) {
        for(int i=0;i<emitters.size();i++) {
            if(links[i]==null)links[i]=new MythicLineVisuals(effect,emitters.get(i),"chronos_echo");
            links[i].ray(visuals,emitters.get(i).clone().add(0,-1,0),face,.5);
        }
    }
}
