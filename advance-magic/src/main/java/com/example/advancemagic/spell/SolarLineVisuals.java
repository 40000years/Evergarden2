package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.Location;

/** Three great circles and five latitude bands keep each sun a true 3D orb. */
final class SolarLineVisuals {
    private final Location base;
    private final double scale;
    private final double[] radii,heights;
    private final MythicLineVisuals[][] suns=new MythicLineVisuals[5][8];
    private final MythicLineVisuals glyph,beam;
    private final MythicLineVisuals[] ripples=new MythicLineVisuals[2];
    SolarLineVisuals(EffectEngine.Effect effect,Location base,double scale,double[] radii,double[] heights) {
        this.base=base.clone();this.scale=scale;this.radii=radii;this.heights=heights;
        for(int i=0;i<5;i++)for(int j=0;j<8;j++)
            suns[i][j]=new MythicLineVisuals(effect,base.clone().add(0,heights[i]*scale,0),j<3?"solar_corona":"solar_orbit");
        glyph=new MythicLineVisuals(effect,base,"solar_corona");beam=new MythicLineVisuals(effect,base,"solar_ray");
        for(int i=0;i<2;i++)ripples[i]=new MythicLineVisuals(effect,base,"solar_orbit");
    }
    void frame(MythicVisuals visuals,int age) {
        if(age<=120&&age%4==0) {
            double descent=age<90?0:Math.min(1,(age-90)/20.0);
            double growth=age<40?.35+.65*age/40.0:1;
            if(age>110)growth*=Math.max(.02,1-(age-110)/12.0);
            for(int i=0;i<5;i++) {
                Location at=base.clone().add(0,(heights[i]*(1-descent)+descent)*scale,0);
                double r=radii[i]*scale*growth*(1-descent*.25),turn=age*.018+i*.2;
                suns[i][0].plane(visuals,at,MythicLineVisuals.X,MythicLineVisuals.Y,r*2,turn);
                suns[i][1].plane(visuals,at,MythicLineVisuals.Z,MythicLineVisuals.Y,r*2,-turn);
                suns[i][2].plane(visuals,at,MythicLineVisuals.X,MythicLineVisuals.Z,r*2,turn*.7);
                double body=r*398/512;
                for(int j=0;j<5;j++) {
                    double y=body*(j-2)/3,circle=Math.sqrt(body*body-y*y);
                    suns[i][j+3].plane(visuals,at.clone().add(0,y,0),MythicLineVisuals.X,MythicLineVisuals.Z,
                        circle*2*512/474,turn);
                }
            }
        }
        if(age<110&&age%4==0)glyph.plane(visuals,base.clone().add(0,.08,0),
            MythicLineVisuals.X,MythicLineVisuals.Z,26,age*.012);
        if(age==110)glyph.close();
        if(age==122)for(var sun:suns)for(var circle:sun)circle.close();
        boolean firing=age>=40&&age<90&&(age-40)%10<=6;
        if(firing&&age%2==0) {
            int pulse=(age-40)/10;double angle=Math.PI*2*pulse/5;
            beam.ray(visuals,base.clone().add(0,heights[0]*scale,0),
                base.clone().add(Math.cos(angle)*4,0,Math.sin(angle)*4),1.1);
        } else if(age%2==0)beam.hide();
        if(age>=110&&age<=140&&age%2==0) {
            double r=1+(age-110)*.6;
            ripples[0].plane(visuals,base.clone().add(0,.5,0),MythicLineVisuals.X,MythicLineVisuals.Z,r*2*512/474,0);
            ripples[1].plane(visuals,base.clone().add(0,1.2,0),MythicLineVisuals.X,MythicLineVisuals.Z,r*1.8*512/474,0);
        }
        if(age==142){beam.close();for(var ripple:ripples)ripple.close();}
    }
}
