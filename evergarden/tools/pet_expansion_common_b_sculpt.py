"""Twenty authored Evergarden companions: natural anatomy with tiny garden stories.

Coordinates are Blockbench units, front is -Z, and all three ages are independently
proportioned.  Surface marks are placed onto anatomy rather than floating decals.
Every limb, tail, antenna and wing has an animation pivot.  No random geometry.
"""
from __future__ import annotations

import math
from pet_rare_sculpt import Sculpt, _palette
from pet_menagerie_sculpt import _ellipsoid, _flower, _dot_eyes, _smile, _leg


SPECIES = (
    "seashell_crab", "kelp_turtle", "tangerine_gecko", "dandelion_meerkat",
    "snowdrop_ermine", "cinnamon_alpaca", "lotus_snail", "pebble_pika",
    "coral_shrimp", "dew_dragonfly", "starling_sparrow", "apricot_tapir",
    "sage_koala", "chestnut_boar", "papaya_toucan", "lantern_firefly",
    "pearl_oyster", "clover_fawn", "dusk_jerboa", "ribbon_lemur",
)


def _p(**kw):
    colors=dict(cream="#FFF0D5",shade="#9B725D",leaf="#7FA67C",leaf_light="#B8D69B",
                pink="#EBA9AB",white="#FFF9EE")
    colors.update(kw)
    return _palette(**colors)


def _soft(s, name, bone, center, size, mat, layers=5):
    _ellipsoid(s, name, bone, center, tuple(v / 2 for v in size), mat, layers=layers)


def _leaf(s, bone, center, size, mat="leaf", yaw=0, roll=0):
    x, y, z = center
    w, h, d = size
    s.box("leaf_blade", bone, center, (w*.72, h, d), mat, (0, yaw, roll))
    s.box("leaf_tip", bone, (x, y+h*.03, z-d*.39), (w*.45, h*.86, d*.30), mat, (0, yaw, roll))
    s.box("leaf_vein", bone, (x, y+h*.55, z), (w*.07, h*.15, d*.83), "leaf_light", (0, yaw, roll))


def _toe_marks(s, bone, x, y, z, spread, count=3, mat="cream"):
    for j in range(count):
        s.box("toe_tip", bone, (x+(j-(count-1)/2)*spread, y, z),
              (spread*.48, spread*.40, spread*.8), mat)


def _nose(s, bone, y, z, width, mat="shade"):
    s.box("heart_nose", bone, (0,y,z), (width,width*.56,.3), mat)
    s.box("nose_bridge", bone, (0,y+width*.19,z+.05), (width*.6,width*.3,.24), mat)
    _smile(s,bone,y-width*.75,z-.02,width*.9,mat)


def _quad(s, length, width, torso, legs, mat, foot="cream", tall=False):
    k=s.scale
    by=(legs+torso*.43)*k
    s.bones["body"]=("root",[0,by,0])
    _soft(s,"ribcage","body",(0,by,0),(width*k,torso*k,length*k),mat,6)
    for side in (-1,1):
        for end in (-1,1):
            x,z=side*width*.32*k,end*length*.30*k
            name="leg_"+("f" if end<0 else "b")+("l" if side<0 else "r")
            paw=(width*.25*k,1.0*k,(1.9 if tall else 2.4)*k)
            _,lower=_leg(s,name,(x,(legs+torso*.08)*k,z),(x,0,z-.3*k),
                         width*(.16 if tall else .25)*k,mat,foot,knee=(.50,.35*k),paw=paw,paw_mat=foot)
            _toe_marks(s,lower,x,.4*k,z-1.05*k,width*.075*k,mat="shade")
    return by


def _rounded_ear(s, bone, x, y, z, w, h, mat="fur", inner="pink", tilt=0):
    s.mass("ear_outer",bone,(x,y,z),(w,h,w*.55),mat)
    s.box("ear_velvet",bone,(x,y,z-w*.29),(w*.55,h*.65,.18),inner,(0,0,tilt))
    s.box("ear_edge_light",bone,(x-w*.23,y+h*.20,z-w*.30),(w*.16,h*.35,.12),"cream")


def _finish(s):
    """Keep the feet grounded after rotations (beam boxes use a local Y axis)."""
    lowest=1e9
    for p in s.parts:
        origin=p.get("origin",[p["from"][i]+p["size"][i]/2 for i in range(3)])
        angles=[math.radians(v) for v in p.get("rotation",[0,0,0])]
        for a in (0,1):
            for b in (0,1):
                for c in (0,1):
                    v=[p["from"][i]+p["size"][i]*q-origin[i] for i,q in enumerate((a,b,c))]
                    for axis,angle in enumerate(angles):
                        co,si=math.cos(angle),math.sin(angle)
                        i,j=((1,2),(2,0),(0,1))[axis]
                        v[i],v[j]=v[i]*co-v[j]*si,v[i]*si+v[j]*co
                    lowest=min(lowest,v[1]+origin[1])
    shift=-lowest
    for p in s.parts:
        p["from"][1]=round(p["from"][1]+shift,4)
        if "origin" in p:p["origin"][1]=round(p["origin"][1]+shift,4)
    for name,(parent,pivot) in list(s.bones.items()):
        if name!="root":s.bones[name]=(parent,[pivot[0],round(pivot[1]+shift,4),pivot[2]])
    return s.finish()


def _seashell_crab(stage):
    s=Sculpt(stage,_p(shell="#EABBA7",shell_deep="#B87D81",shell_light="#FFDBC0",
                         coral="#E38C80",cream="#FFF0DA",shade="#8E5362",eye="#3F7886"))
    k,h=s.scale,s.head_scale
    by=3.9*k
    s.bones["body"]=("root",[0,by,0])
    _soft(s,"crab_carapace","body",(0,by,0),(10*k,5.0*k,8*k),"coral",6)
    # Six bent walking legs, each with a separate ankle and a sandy toe.
    for side in (-1,1):
        for i in range(3):
            z=(-.7+i*2)*k;x=side*(4.0+.15*i)*k
            limb=s.bone("leg_"+("l" if side<0 else "r")+str(i),"body",(x,by,z))
            knee=(side*(7.1+.4*i)*k,3.2*k,z+.5*k)
            s.beam("walking_leg",limb,(x,by,z),knee,.85*k,"coral")
            ankle=s.bone(limb+"_lower",limb,knee)
            s.beam("tapered_ankle",ankle,knee,(side*(7.8+.3*i)*k,.5*k,z-1*k),.55*k,"shell_deep")
            s.mass("sand_toe",ankle,(side*(7.8+.3*i)*k,.4*k,z-1.1*k),(1.0*k,.8*k,1.7*k),"shell")
    # Asymmetric pincer sizes are deliberate and visible at every age.
    for side in (-1,1):
        x=side*5.3*k;f=(1.25 if side<0 else .88)
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*3.8*k,by,-2.8*k))
        s.beam("claw_forearm",arm,(side*3.8*k,by,-2.8*k),(x,4.2*k,-6*k),1.6*k,"coral")
        _soft(s,"pincer_palm",arm,(x,4.2*k,-6.6*k),(3*f*k,3.5*f*k,3.6*f*k),"coral",4)
        for finger in (-1,1):
            tip=(x+finger*.9*f*k,4.4*k,-9.0*k)
            s.box("pincer_finger",arm,tip,(1.15*f*k,2.1*f*k,3.0*f*k),"shell_light",(0,finger*-13,0))
            for j in range(2+s.age):
                s.box("pincer_tooth",arm,(x+finger*.28*f*k,4.4*k,-8.0*k-j*.48*k),(.45*k,.6*k,.4*k),"cream")
    # A conch-like spiral on the back is built as a true raised logarithmic coil.
    shell=s.bone("shell","body",(0,by+1.5*k,1.1*k))
    steps=11+4*s.age
    for i in range(steps):
        t=i/(steps-1);a=math.radians(30)+t*math.pi*(2.2+.28*s.age)
        r=(.5+t*(3.3+.5*s.age))*k
        center=(math.cos(a)*r,by+4*k+math.sin(a)*r,2.1*k)
        s.box("conch_spiral_segment",shell,center,(1.25*k,1.3*k,(3.1-t*.7)*k),"shell_light" if i%3==0 else "shell",(0,0,math.degrees(a)))
        s.box("conch_ridge",shell,(center[0],center[1],center[2]-1.65*k),(.28*k,1.1*k,.18*k),"shell_deep",(0,0,math.degrees(a)))
    s.bone("head","body",(0,by,-3*k))
    for side in (-1,1):
        stalk=s.bone("eyestalk_l" if side<0 else "eyestalk_r","head",(side*2.1*h,by+.8*k,-3.1*k))
        s.beam("eye_stalk",stalk,(side*2.1*h,by+.8*k,-3.1*k),(side*2.6*h,by+3.3*h,-3.5*k),.65*h,"coral")
        s.ball("eyestalk_bulb",stalk,(side*2.6*h,by+3.3*h,-3.5*k),(2.3*h,2.5*h,2*h),"cream")
        # The bulb grows with head scale, while its stalk uses body scale.  Place
        # the pupil against that bulb's actual front so infant eyes stay visible.
        eye_z=-3.5*k-h-.08
        s.box("watchful_eye",stalk,(side*2.6*h,by+3.35*h,eye_z),(1.05*h,1.4*h,.2),"pupil")
        s.box("eye_shine",stalk,(side*2.6*h-.25*h,by+3.65*h,eye_z-.15),(.4*h,.45*h,.1),"shine")
    _smile(s,"head",by-.3*k,-4.02*k,1.7*h,"shade")
    for side in (-1,1):
        for i in range(3+s.age):
            s.box("carapace_speckle","body",(side*(3.3-.3*(i%2))*k,by+1.35*k,(i-2)*1.05*k),(.6*k,.2*k,.75*k),"shell_light")
    return _finish(s)


def _kelp_turtle(stage):
    s=Sculpt(stage,_p(skin="#9CC3A2",shell="#4D8072",shell_dark="#345F59",shell_light="#82A997",
                         cream="#DDE5BB",shade="#547F6F",eye="#4F907D",leaf="#678E56",leaf_light="#B1C876"))
    k,h=s.scale,s.head_scale
    by=3.9*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"turtle_belly","body",(0,by-.65*k,0),(10*k,4.6*k,12*k),"cream",5)
    _soft(s,"domed_carapace","body",(0,by+1.1*k,1*k),(11*k,7.4*k,12.8*k),"shell",7)
    # Seven individual chamfered scutes and their pale outer rim.
    for row in range(-1,2):
        for col in range(-1,2):
            if abs(row)+abs(col)>1 and s.age==0:continue
            x,z=col*3.15*k,(row*3.5+1)*k
            yy=by+(4.7-.55*abs(col)-.6*abs(row))*k
            s.box("scute_seam","body",(x,yy,z),(3.5*k,.5*k,3.65*k),"shell_dark",(0,0,0))
            s.box("jade_scute","body",(x,yy+.24*k,z),(2.98*k,.35*k,3.03*k),"shell_light",(0,0,0))
            s.box("scute_inner_grain","body",(x-.3*k,yy+.44*k,z-.35*k),(1.35*k,.1*k,.3*k),"cream")
    for side in (-1,1):
        for end in (-1,1):
            x,z=side*4.0*k,end*3.6*k
            leg=s.bone("leg_"+("f" if end<0 else "b")+("l" if side<0 else "r"),"body",(x,by-1.0*k,z))
            s.mass("turtle_leg",leg,(x*1.16,1.6*k,z),(3.6*k,3.2*k,3.6*k),"skin")
            _toe_marks(s,leg,x*1.16,.7*k,z-1.85*k,.78*k,mat="cream")
    hy,hz=by+.8*h,-6.1*k
    s.bone("head","body",(0,hy-1*h,hz+1.7*h))
    _soft(s,"turtle_head","head",(0,hy,hz),(6.3*h,5.4*h,5.9*h),"skin",6)
    s.mass("turtle_muzzle","head",(0,hy-.8*h,hz-2.5*h),(4.9*h,2.3*h,2.3*h),"cream")
    _dot_eyes(s,"head",1.6*h,hy+.5*h,hz-2.98*h,1.0*h)
    _smile(s,"head",hy-.85*h,hz-3.7*h,1.35*h,"shade")
    for side in (-1,1):
        s.box("nose_dimple","head",(side*.72*h,hy-.15*h,hz-3.7*h),(.22*h,.22*h,.12),"shade")
        for i in range(3):s.box("cheek_freckle","head",(side*(2.2+i*.16)*h,hy-.5*h+(i%2)*.45*h,hz-2.75*h),(.28*h,.28*h,.14),"shell")
    tail=s.bone("tail","body",(0,by-.4*k,5.5*k))
    s.beam("turtle_tail",tail,(0,by-.4*k,5.5*k),(0,1.8*k,8.4*k),1.0*k,"skin")
    for i in range(3+2*s.age):
        a=i*2.39996;x=math.cos(a)*(1.6+.30*(i%2))*k;z=(1+math.sin(a)*2.2)*k
        stem=s.bone("kelp_"+str(i),"body",(x,by+4.3*k,z))
        for j in range(2+s.age):
            yy=by+(4.8+j*1.6)*k;xx=x+math.sin(j*1.4+i)*.65*k
            s.beam("kelp_stem",stem,(x,by+4.1*k,z),(xx,yy+.7*k,z),.30*k,"leaf")
            _leaf(s,stem,(xx+.8*k,yy,z),(1.6*k,.32*k,2.4*k),yaw=i*47,roll=25)
    return _finish(s)


def _tangerine_gecko(stage):
    s=Sculpt(stage,_p(skin="#F4A151",spot="#C96E42",belly="#FFE2A3",cream="#FFEAC0",
                         shade="#88513D",eye="#B5A548",toe="#F8C68A"))
    k,h=s.scale,s.head_scale
    by=2.7*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"gecko_torso","body",(0,by,.4*k),(5.8*k,4.2*k,10*k),"skin",6)
    s.mass("pale_belly","body",(0,1.8*k,-.6*k),(4.7*k,2.6*k,7.9*k),"belly")
    for side in (-1,1):
        for end in (-1,1):
            hip=(side*2.3*k,by,end*2.6*k)
            limb=s.bone("leg_"+("f" if end<0 else "b")+("l" if side<0 else "r"),"body",hip)
            elbow=(side*4.6*k,1.4*k,end*3.3*k)
            wrist=(side*5.1*k,.65*k,end*4.2*k)
            s.beam("splayed_thigh",limb,hip,elbow,1.35*k,"skin")
            lower=s.bone(limb+"_lower",limb,elbow)
            s.beam("gecko_wrist",lower,elbow,wrist,.9*k,"belly")
            s.ball("palm",lower,wrist,(1.6*k,1.0*k,1.4*k),"toe")
            for j in range(4):
                dx=(j-1.5)*.66*k
                tip=(wrist[0]+dx,wrist[1]-.25*k,wrist[2]-1.1*k-(1-abs(j-1.5)/2)*.5*k)
                s.beam("tiny_finger",lower,wrist,tip,.3*k,"skin")
                s.ball("adhesive_toepad",lower,tip,(.7*k,.55*k,.75*k),"toe")
    hy,hz=by+1.2*h,-5.1*k
    s.bone("head","body",(0,hy-1*h,hz+1*h))
    _soft(s,"gecko_skull","head",(0,hy,hz),(7.2*h,4.8*h,6.6*h),"skin",6)
    s.mass("broad_gecko_muzzle","head",(0,hy-.7*h,hz-2.8*h),(5.7*h,2.4*h,2.7*h),"belly")
    s.eyes("head",2.25*h,hy+.8*h,hz-2.9*h,.85*h,iris="eye",gentle=True)
    _smile(s,"head",hy-.65*h,hz-4.18*h,2.2*h,"shade")
    tail=s.bone("tail","body",(0,by,4.6*k))
    points=[(0,by,4.6*k),(1.1*k,2.7*k,7.1*k),(3.5*k,3.0*k,9.7*k),(5.7*k,4*k,10.2*k),(6.7*k,5.4*k,8.9*k)]
    for i in range(2+s.age):
        a,b=points[i],points[i+1]
        s.beam("plump_tail_segment",tail,a,b,(3.0-i*.48)*k,"skin",(2.5-i*.34)*k)
        for j in range(2):
            pos=tuple(a[n]+(b[n]-a[n])*(.35+j*.3) for n in range(3))
            s.box("tail_amber_band",tail,(pos[0],pos[1]+.6*k,pos[2]),((2.6-i*.4)*k,.35*k,.6*k),"spot",(0,-i*20,0))
    for side in (-1,1):
        for j in range(6+2*s.age):
            z=(-3.2+j*.8)*k;x=side*(1.4+.4*(j%2))*k
            s.box("tangerine_freckle","body",(x,by+1.75*k,z),((.55+.15*(j%3))*k,.22*k,.7*k),"spot",(0,15*j,0))
    for side in (-1,1):
        for i in range(3+s.age):s.box("temple_speckle","head",(side*(2.9-.2*(i%2))*h,hy+.6*h+(i//2)*.5*h,hz-2.55*h),(.32*h,.37*h,.14),"spot")
    return _finish(s)


def _dandelion_meerkat(stage):
    s=Sculpt(stage,_p(fur="#D6AC78",dark="#8C6550",muzzle="#F4DCAD",cream="#F8E8C5",
                         shade="#755A4B",eye="#9C864F",flower="#FFF2BD",seed="#F3E8C8"))
    k,h=s.scale,s.head_scale
    by=(6.1+1.0*s.age)*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"upright_meerkat","body",(0,by,0),(5.6*k,(10.0+1*s.age)*k,5.1*k),"fur",6)
    _soft(s,"cream_apron","body",(0,by-.2*k,-2.05*k),(3.9*k,7.1*k,1.4*k),"muzzle",4)
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*1.6*k,3.5*k,.8*k))
        s.mass("meerkat_thigh",leg,(side*1.8*k,2.3*k,1*k),(2.5*k,4*k,3.0*k),"fur")
        s.mass("long_hind_foot",leg,(side*1.9*k,.65*k,-.9*k),(2.1*k,1.3*k,4.3*k),"dark")
        _toe_marks(s,leg,side*1.9*k,.5*k,-3.1*k,.55*k,mat="cream")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*2.3*k,by+2.0*k,-.8*k))
        s.beam("folded_forearm",arm,(side*2.3*k,by+2.0*k,-.8*k),(side*.9*k,by-.4*k,-3.1*k),1.15*k,"fur")
        s.mass("little_hand",arm,(side*.9*k,by-.5*k,-3.1*k),(1.3*k,1.45*k,1.4*k),"dark")
        _toe_marks(s,arm,side*.9*k,by-.6*k,-3.85*k,.32*k,mat="cream")
    hy=by+6*h;hz=-.7*k
    s.bone("head","body",(0,hy-2.1*h,hz+1*h))
    _soft(s,"meerkat_head","head",(0,hy,hz),(6.5*h,6.1*h,5.1*h),"fur",6)
    for side in (-1,1):
        _rounded_ear(s,"head",side*2.8*h,hy+2.2*h,hz+.1*h,1.9*h,2.3*h,"fur","dark")
        s.disc("dark_eye_mask","head",(side*1.6*h,hy+.3*h,hz-2.52*h),2.2*h,2.8*h,.25,"dark")
    _dot_eyes(s,"head",1.6*h,hy+.35*h,hz-2.77*h,.83*h,blush=True)
    s.mass("pointed_muzzle","head",(0,hy-1.4*h,hz-2.5*h),(3.7*h,2.2*h,3*h),"muzzle")
    _nose(s,"head",hy-1.0*h,hz-4.1*h,.95*h)
    tail=s.bone("tail","body",(0,3.4*k,2*k))
    s.beam("balancing_tail",tail,(0,3.4*k,2*k),(.8*k,.7*k,7*k),1.35*k,"fur")
    s.beam("dark_tail_tip",tail,(.8*k,.7*k,7*k),(1.6*k,.5*k,8.5*k),.8*k,"dark")
    for side in (-1,1):
        _leaf(s,"body",(side*2.1*k,by+3.8*k,-1.3*k),(2.5*k,.35*k,3.2*k),yaw=side*45,roll=side*20)
    stem=s.bone("dandelion","body",(-2.5*k,by,-2.6*k))
    top=(-3.7*k,by+(5+1.1*s.age)*k,-3.1*k)
    s.beam("flower_stalk",stem,(-1*k,by-.2*k,-3.1*k),top,.4*k,"leaf")
    if s.age==0:
        _soft(s,"dandelion_bud",stem,top,(2.3*k,3.4*k,2.2*k),"flower",4)
    else:
        s.ball("seed_heart",stem,top,(2.1*k,2.1*k,2.1*k),"flower")
        count=10+6*s.age
        for j in range(count):
            a=j*2.39996;v=-.85+1.7*j/(count-1);r=math.sqrt(1-v*v)*(2.1+.4*s.age)*k
            end=(top[0]+math.cos(a)*r,top[1]+v*(2.1+.4*s.age)*k,top[2]+math.sin(a)*r)
            s.beam("seed_ray",stem,top,end,.13*k,"seed")
            s.box("seed_parachute",stem,end,(.9*k,.22*k,.75*k),"cream",(0,j*31,j*23))
    return _finish(s)


def _snowdrop_ermine(stage):
    s=Sculpt(stage,_p(fur="#F5F3E9",shadow="#B7C6CB",black="#444856",cream="#FFF9EF",
                         shade="#626778",eye="#8D93BA",leaf="#83A593",leaf_light="#B7D0AF",petal="#F7FFF5"))
    k,h=s.scale,s.head_scale
    by=_quad(s,13.5,5.1,4.8,2.6,"fur","shadow")
    hy,hz=by+1.4*h,-7.0*k
    s.bone("head","body",(0,hy-1.6*h,hz+1.4*h))
    _soft(s,"ermine_head","head",(0,hy,hz),(6*h,5*h,5.8*h),"fur",6)
    s.mass("ermine_muzzle","head",(0,hy-1.0*h,hz-2.5*h),(4.5*h,2.1*h,2.6*h),"cream")
    _dot_eyes(s,"head",1.65*h,hy+.55*h,hz-2.85*h,.90*h)
    _nose(s,"head",hy-.55*h,hz-3.92*h,.85*h,"black")
    for side in (-1,1):
        _rounded_ear(s,"head",side*2.65*h,hy+2.0*h,hz+.2*h,1.8*h,2.0*h,"fur","pink")
        for j in range(3):
            s.beam("silvery_whisker","head",(side*1.6*h,hy-1*h,hz-3.5*h),(side*(3.5+.3*j)*h,hy+(-1.4+.45*j)*h,hz-3.1*h),.12*h,"shadow")
    tail=s.bone("tail","body",(0,by,5.4*k))
    pts=[(0,by,5.4*k),(0,by+.3*k,8.4*k),(1.5*k,by+1.0*k,11.1*k),(3.8*k,by+2.3*k,12.2*k)]
    for j in range(3):
        s.beam("long_ermine_tail",tail,pts[j],pts[j+1],(2.25-j*.4)*k,"black" if j==2 else "fur")
        s.ball("tail_soft_joint",tail,pts[j+1],((2.1-j*.4)*k,(2.1-j*.4)*k,(2.1-j*.4)*k),"black" if j==2 else "fur")
    # Snowdrop cape: bent stems and pendant bell petals, not a generic flower decal.
    for i in range(3+2*s.age):
        a=(i-(2+s.age))*math.pi/(5+2*s.age)
        x=math.sin(a)*3.0*k;z=-4.1*k+math.cos(a)*1.1*k
        yy=by+(1.3+.6*math.cos(a))*k
        _leaf(s,"body",(x,yy,z),(1.65*k,.3*k,3.3*k),yaw=math.degrees(a),roll=-math.sin(a)*20)
    for side in (-1,1):
        stalk=s.bone("snowdrop_l" if side<0 else "snowdrop_r","head",(side*2.7*h,hy+1.6*h,hz))
        top=(side*3.0*h,hy+(3.6+.7*s.age)*h,hz-.1*h)
        s.beam("snowdrop_stem",stalk,(side*2.7*h,hy+1.6*h,hz),top,.22*h,"leaf")
        bell=(top[0]+side*.5*h,top[1]-.6*h,top[2]-.3*h)
        s.beam("bent_flower_neck",stalk,top,bell,.22*h,"leaf")
        for j in range(3 if s.age==0 else 5):
            a=j*math.tau/(3 if s.age==0 else 5)
            s.box("snowdrop_bell_petal",stalk,(bell[0]+math.cos(a)*.55*h,bell[1]-.8*h,bell[2]+math.sin(a)*.55*h),(.65*h,1.7*h,.55*h),"petal",(0,j*70,math.cos(a)*15))
        s.box("green_flower_heart",stalk,(bell[0],bell[1]-1.0*h,bell[2]),(.45*h,.9*h,.45*h),"leaf_light")
    return _finish(s)


def _cinnamon_alpaca(stage):
    s=Sculpt(stage,_p(fur="#C89168",curl="#DDA77C",dark="#9C674F",cream="#F4DBB4",
                         muzzle="#F8E7C9",shade="#6F5148",eye="#8F745A",cloth="#739B8D",trim="#E6B476"))
    k,h=s.scale,s.head_scale
    by=_quad(s,11.5,7.3,7.6,7.0,"fur","dark",True)
    # Sloping long neck, with a generous infant head and a taller adult posture.
    necktop=(0,by+(7.5+1.1*s.age)*k,-4.1*k)
    s.beam("alpaca_neck","body",(0,by+1.4*k,-3.2*k),necktop,3.7*k,"fur",4.2*k)
    hy,hz=necktop[1]+2.1*h,necktop[2]-1.0*h
    s.bone("head","body",(0,hy-2*h,hz+1*h))
    _soft(s,"alpaca_skull","head",(0,hy,hz),(5.5*h,5.8*h,5.5*h),"fur",6)
    s.mass("long_alpaca_muzzle","head",(0,hy-1.2*h,hz-2.5*h),(3.7*h,2.7*h,3.2*h),"muzzle")
    _dot_eyes(s,"head",1.6*h,hy+.35*h,hz-2.72*h,.82*h,blush=True)
    _nose(s,"head",hy-1.0*h,hz-4.14*h,1.05*h)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*1.8*h,hy+2.4*h,hz+.3*h))
        s.box("banana_ear",ear,(side*2.0*h,hy+4.0*h,hz+.2*h),(1.55*h,4.1*h,1.6*h),"fur",(0,0,side*-10))
        s.box("pink_ear_center",ear,(side*2*h,hy+4.1*h,hz-.66*h),(.75*h,2.9*h,.18),"pink",(0,0,side*-10))
        s.mass("eyebrow_fleece","head",(side*1.3*h,hy+2.2*h,hz-1.4*h),(2.7*h,1.6*h,2.7*h),"curl")
    # An actual fleece coat of interlocking curls, each with an inset hook.
    rows=3+s.age
    for side in (-1,1):
        for row in range(rows):
            for col in range(4):
                x=side*(3.2+.20*(row%2))*k
                y=by+(-2.3+row*1.2)*k;z=(-3.8+col*2.5+(row%2)*.3)*k
                s.box("wool_curl", "body",(x,y,z),(1.25*k,1.4*k,1.65*k),"curl",(0,0,side*9))
                if s.age>0 and (row+col)%2==0:
                    s.box("curl_hook","body",(x+side*.66*k,y+.25*k,z-.2*k),(.18*k,.55*k,.65*k),"dark")
    # Wool neck ring and woven saddle blanket.
    for i in range(4+s.age):
        s.mass("neck_fleece","body",(0,by+(3.0+i*1.2)*k,-3.5*k),(4.2*k,1.4*k,4.6*k),"curl")
    for side in (-1,1):
        s.box("woven_blanket","body",(side*3.75*k,by+.3*k,1.0*k),(.35*k,4.7*k,5.8*k),"cloth")
        for j in range(3+s.age):
            z=(-1.0+j*1.0)*k
            s.box("blanket_chevron","body",(side*3.98*k,by+.15*k,z),(.16*k,1.6*k,.4*k),"trim",(side*25,0,0))
            s.box("blanket_tassel","body",(side*3.85*k,by-2.4*k,z),(.48*k,1.0*k,.48*k),"trim")
    tail=s.bone("tail","body",(0,by+1*k,5*k))
    s.mass("alpaca_tail",tail,(0,by+1*k,6.1*k),(2.4*k,2.5*k,3.1*k),"curl")
    return _finish(s)


def _lotus_snail(stage):
    s=Sculpt(stage,_p(skin="#E9DCC6",shell="#DDA4B3",shell_dark="#AB768D",shell_light="#F3C3CF",
                         shade="#8F647A",eye="#628D8E",lotus="#F0ABC4",lotus_light="#FFE1E7",heart="#EED38B"))
    k,h=s.scale,s.head_scale
    s.bones["body"]=("root",[0,1.4*k,0])
    _soft(s,"snail_foot","body",(0,1.15*k,.7*k),(7.4*k,2.3*k,16*k),"skin",5)
    s.mass("snail_sole","body",(0,.35*k,.7*k),(7.6*k,.7*k,15.7*k),"cream")
    for side in (-1,1):
        for i in range(5+s.age):
            s.box("foot_ripple","body",(side*3.55*k,.75*k,(-5+i*1.8)*k),(.5*k,.6*k,1.05*k),"shell_light",(0,side*14,0))
    hy,hz=3.5*h,-5.6*k
    s.bone("head","body",(0,hy-1.2*h,hz+1.5*h))
    _soft(s,"soft_snail_face","head",(0,hy,hz),(6.1*h,4.8*h,4.9*h),"skin",5)
    for side in (-1,1):
        stalk=s.bone("eyestalk_l" if side<0 else "eyestalk_r","head",(side*1.8*h,hy+1.2*h,hz-.6*h))
        top=(side*2.3*h,hy+(4.4+.5*s.age)*h,hz-1*h)
        s.beam("tall_eyestalk",stalk,(side*1.8*h,hy+1.2*h,hz-.6*h),top,.6*h,"skin")
        s.ball("snail_eye_bulb",stalk,top,(2.1*h,2.4*h,2.1*h),"cream")
        s.box("snail_black_eye",stalk,(top[0],top[1],top[2]-1.08*h),(1.0*h,1.45*h,.2),"pupil")
        s.box("snail_eye_glint",stalk,(top[0]-.2*h,top[1]+.35*h,top[2]-1.22*h),(.4*h,.4*h,.1),"shine")
        s.beam("short_feeler","head",(side*2.2*h,hy-.6*h,hz-1.8*h),(side*3.4*h,hy-.3*h,hz-3*h),.3*h,"shell_dark")
        s.box("snail_cheek","head",(side*1.7*h,hy-.35*h,hz-2.43*h),(1.1*h,.4*h,.14),"pink")
    _smile(s,"head",hy-1.1*h,hz-2.5*h,1.6*h,"shade")
    shell=s.bone("shell","body",(0,3.5*k,2.2*k))
    _soft(s,"shell_volume",shell,(0,6.7*k,2.4*k),(9.0*k,10*k,8*k),"shell",7)
    # Mirror the coiled ridges on both lateral faces of the round shell.
    for side in (-1,1):
        seg=9+3*s.age
        for i in range(seg):
            t=i/(seg-1);a=t*math.pi*3.0;r=(.4+3.8*t)*k
            y=6.8*k+math.cos(a)*r;z=2.4*k+math.sin(a)*r
            s.box("shell_spiral_rib",shell,(side*4.2*k,y,z),(.65*k,1.1*k,1.1*k),"shell_light" if i%2 else "shell_dark",(math.degrees(a),0,0))
    # A bud opens into an eight-petal, two-tier lotus as it matures.
    flower_y=11.4*k
    tiers=1+s.age
    for ring in range(tiers):
        petals=5+ring*2
        for j in range(petals):
            a=j*math.tau/petals+ring*.5
            r=(1.2+ring*.8)*k
            x,z=math.cos(a)*r,2.4*k+math.sin(a)*r
            s.box("lotus_petal",shell,(x,flower_y+(.9-ring*.35)*k,z),((1.4+ring*.45)*k,(3.4-ring*.6)*k,1.0*k),"lotus_light" if ring%2 else "lotus",(math.sin(a)*(-12-10*ring),-math.degrees(a),math.cos(a)*(12+10*ring)))
            if s.age==2:s.box("petal_tip",shell,(x*1.35,flower_y+1.6*k,2.4*k+(z-2.4*k)*1.35),(.9*k,.7*k,.8*k),"lotus_light")
    s.ball("lotus_heart",shell,(0,flower_y+.6*k,2.4*k),(2.0*k,1.5*k,2.0*k),"heart")
    for side in (-1,1):_leaf(s,shell,(side*3*k,10.0*k,3.7*k),(3.5*k,.4*k,4.6*k),yaw=side*22,roll=side*-8)
    for i in range(3+s.age):
        s.box("dew_glint","body",(-2.3*k+i*.9*k,2.22*k,(-.3+i*1.0)*k),(.32*k,.13*k,.65*k),"shine")
    return _finish(s)


def _pebble_pika(stage):
    s=Sculpt(stage,_p(fur="#A7A29B",fur_light="#C6BEB0",stone="#888A88",cream="#EDE4CC",
                         shade="#686967",eye="#655B53",pink="#DCB2AB"))
    k,h=s.scale,s.head_scale
    by=4.6*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"pika_plump_torso","body",(0,by,1*k),(9.0*k,8.6*k,8.6*k),"fur",7)
    _soft(s,"cream_pika_belly","body",(0,by-1.1*k,-2.1*k),(6.7*k,5.9*k,2.0*k),"cream",4)
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.7*k,2.9*k,2.1*k))
        s.mass("pika_haunch",leg,(side*3.0*k,2.2*k,2*k),(3.4*k,4.3*k,4.6*k),"fur_light")
        s.mass("pika_hind_foot",leg,(side*2.9*k,.65*k,-.5*k),(2.7*k,1.3*k,3.6*k),"cream")
        _toe_marks(s,leg,side*2.9*k,.5*k,-2.3*k,.65*k,mat="shade")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*3.3*k,by+.1*k,-1.8*k))
        s.beam("short_pika_arm",arm,(side*3.3*k,by+.1*k,-1.8*k),(side*2.0*k,by-1.5*k,-3.5*k),1.6*k,"fur")
        s.mass("tiny_pika_hand",arm,(side*2*k,by-1.5*k,-3.5*k),(1.8*k,1.5*k,1.6*k),"cream")
    hy,hz=by+3.5*h,-2.2*k
    s.bone("head","body",(0,hy-2*h,hz+1.4*h))
    _soft(s,"round_pika_head","head",(0,hy,hz),(8.0*h,6.7*h,6.1*h),"fur_light",6)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*2.6*h,hy+2.3*h,hz+.4*h))
        _soft(s,"round_pika_ear",ear,(side*2.9*h,hy+4.1*h,hz+.3*h),(3.5*h,4.3*h,1.4*h),"cream",4)
        s.disc("ear_velvet",ear,(side*2.9*h,hy+4.1*h,hz-.44*h),2.35*h,3.0*h,.18,"pink")
        s.mass("cheek_puff","head",(side*3.2*h,hy-1.3*h,hz-1*h),(2.9*h,2.8*h,3.2*h),"fur")
    _dot_eyes(s,"head",2.0*h,hy+.3*h,hz-3.08*h,1.05*h)
    s.mass("pika_muzzle","head",(0,hy-1.3*h,hz-2.8*h),(4.1*h,2.3*h,1.9*h),"cream")
    _nose(s,"head",hy-.9*h,hz-3.9*h,.85*h)
    for side in (-1,1):
        for j in range(2+s.age):s.box("cheek_fur_tick","head",(side*(3.4+.15*j)*h,hy+(-.9+j*.5)*h,hz-2.5*h),(.6*h,.22*h,.3*h),"cream",(0,0,side*15))
    tail=s.bone("tail","body",(0,by,4.1*k))
    s.ball("pika_tail",tail,(0,by,4.8*k),(2.4*k,2.4*k,2.1*k),"cream")
    for side in (-1,1):
        for j in range(5+2*s.age):
            z=(-1.8+(j%4)*1.7)*k;y=by+(1+(j//4)*1.6)*k
            s.box("pebble_coat_mottle","body",(side*(3.9-.35*(j//4))*k,y,z),(.35*k,(.65+(j%2)*.5)*k,(1.0+(j%3)*.25)*k),"stone" if j%2 else "fur_light",(side*12,0,0))
    # A clipped little meadow stem tucked behind one ear.
    if s.age>0:
        s.beam("dry_grass_stem","head",(-3*h,hy+1.8*h,hz),(-4*h,hy+5.2*h,hz+.5*h),.22*h,"leaf")
        for j in range(2+s.age):_leaf(s,"head",(-3.4*h,hy+(2.8+j*.7)*h,hz+.4*h),(1.3*h,.2*h,1.8*h),yaw=50+j*30,roll=30)
    return _finish(s)


def _coral_shrimp(stage):
    s=Sculpt(stage,_p(coral="#F29C9C",deep="#BE667D",light="#FFD3C4",cream="#FFF1D7",
                         shade="#94536B",eye="#583B57",fin="#E7AFC1",whisker="#EDC1A4"))
    k,h=s.scale,s.head_scale
    s.bones["body"]=("root",[0,5*k,0])
    # The curved abdomen is six articulated overlapping armour collars.
    curve=[(0,5.5*k,-1*k),(0,5.8*k,1.3*k),(0,5.4*k,3.7*k),(0,4.4*k,5.7*k),(0,2.9*k,7.0*k),(0,1.8*k,7.2*k)]
    segments=4+s.age
    parent="body"
    for i in range(segments):
        x,y,z=curve[i];bone=s.bone("tail" if i==0 else "tail_"+str(i),parent,(x,y,z));parent=bone
        w=(5.0-i*.44)*k;hh=(4.8-i*.48)*k
        _soft(s,"shrimp_armour_segment",bone,(x,y,z),(w,hh,2.9*k),"coral" if i%2==0 else "light",4)
        s.box("segment_crest",bone,(0,y+hh*.49,z),(w*.64,.35*k,1.9*k),"deep")
        for side in (-1,1):
            s.box("shell_flange",bone,(side*w*.45,y-.4*k,z+.3*k),(.55*k,hh*.75,2.4*k),"light",(0,0,side*12))
            s.box("shell_freckle",bone,(side*w*.46,y+.6*k,z-.5*k),(.19*k,.5*k,.7*k),"deep")
    hy,hz=6.2*h,-4.0*k
    s.bone("head","body",(0,hy-1.3*h,hz+2*h))
    _soft(s,"shrimp_cephalothorax","head",(0,hy,hz),(6.0*h,5.3*h,6.6*h),"coral",6)
    s.box("rostrum","head",(0,hy+1.6*h,hz-3.5*h),(.75*h,1.0*h,4.6*h),"light",(-10,0,0))
    for j in range(3+s.age):s.box("rostrum_serration","head",(0,hy+2*h,hz-(2.5+j*.7)*h),(.8*h,.9*h,.45*h),"deep",(0,0,45))
    for side in (-1,1):
        stalk=s.bone("eyestalk_l" if side<0 else "eyestalk_r","head",(side*2*h,hy+1.0*h,hz-1.9*h))
        s.beam("shrimp_eyestalk",stalk,(side*2*h,hy+1*h,hz-1.9*h),(side*3*h,hy+2.7*h,hz-2.6*h),.55*h,"light")
        s.ball("shrimp_eye",stalk,(side*3*h,hy+2.7*h,hz-2.6*h),(1.8*h,2*h,1.7*h),"pupil")
        s.box("eye_glimmer",stalk,(side*3*h-.3*h,hy+3.1*h,hz-3.5*h),(.5*h,.5*h,.1),"shine")
        for j in range(3+s.age):
            z=(-2+j*1.5)*k;leg=s.bone("leg_"+("l" if side<0 else "r")+str(j),"body",(side*2.0*k,4.0*k,z))
            mid=(side*3.5*k,2.9*k,z-.8*k);end=(side*3.1*k,1.0*k,z-2*k)
            s.beam("shrimp_walking_leg",leg,(side*2*k,4*k,z),mid,.35*k,"deep")
            s.beam("shrimp_leg_tip",leg,mid,end,.22*k,"light")
        antenna=s.bone("antenna_l" if side<0 else "antenna_r","head",(side*1.4*h,hy+.5*h,hz-3*h))
        pts=[(side*1.4*h,hy+.5*h,hz-3*h),(side*3.5*h,hy+1.8*h,hz-6*h),(side*(6+1*s.age)*k,hy+2*h,hz-8*k),(side*(8+1*s.age)*k,hy+1*h,hz-7*k)]
        for j in range(3):s.beam("long_sweeping_antenna",antenna,pts[j],pts[j+1],(.26-j*.04)*k,"whisker")
    _smile(s,"head",hy-1.2*h,hz-3.37*h,1.3*h,"shade")
    fan=s.bone("tail_fan",parent,curve[segments-1])
    y,z=curve[segments-1][1],curve[segments-1][2]+1.4*k
    for j in (-1,0,1):
        s.box("shrimp_tail_paddle",fan,(j*1.7*k,y-.1*k,z+1.0*k),(2.1*k,.55*k,3.6*k),"fin",(0,j*22,0))
        for q in range(3+s.age):s.box("tail_fin_ray",fan,(j*1.7*k+(q-1)*.4*k,y+.23*k,z+1*k),(.13*k,.13*k,3.0*k),"cream",(0,j*22,0))
    return _finish(s)


def _dew_dragonfly(stage):
    s=Sculpt(stage,_p(body="#5D9B91",deep="#3E6F7A",mint="#B1DAD0",wing="#D3E8DD",vein="#8CB9B1",
                         cream="#F1F9D8",shade="#4A7D7B",eye="#79BCA4",dew="#ECFFEB"))
    k,h=s.scale,s.head_scale
    by=7*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"dragonfly_thorax","body",(0,by,0),(4.0*k,4.2*k,6.0*k),"body",5)
    s.mass("thorax_racing_stripe","body",(0,by+1.9*k,0),(.85*k,.5*k,5.0*k),"mint")
    hy,hz=by+.8*h,-3.3*k
    s.bone("head","body",(0,hy-.5*h,hz+1*h))
    s.ball("dragonfly_head","head",(0,hy,hz),(4.5*h,3.6*h,3.6*h),"deep")
    for side in (-1,1):
        _soft(s,"huge_compound_eye","head",(side*1.7*h,hy+.7*h,hz-.7*h),(3.1*h,3.7*h,3.0*h),"eye",5)
        s.disc("eye_dark_lens","head",(side*1.7*h,hy+.7*h,hz-2.25*h),1.4*h,1.9*h,.2,"deep")
        s.box("eye_large_glint","head",(side*1.7*h-.35*h,hy+1.2*h,hz-2.4*h),(.65*h,.7*h,.1),"shine")
        for j in range(2+s.age):s.box("eye_facets","head",(side*(2.2+(j%2)*.2)*h,hy+(-.2+(j//2)*.5)*h,hz-1.8*h),(.35*h,.4*h,.14),"mint")
        s.beam("short_antenna","head",(side*.8*h,hy+1.5*h,hz-.5*h),(side*1.2*h,hy+3*h,hz-1.4*h),.22*h,"deep")
    _smile(s,"head",hy-.9*h,hz-1.95*h,1.25*h,"cream")
    tail=s.bone("tail","body",(0,by,2*k))
    count=5+s.age
    for j in range(count):
        z=(2.7+j*1.55)*k;y=by-(j*.2)*k;w=(1.9-j*.14)*k
        s.mass("abdominal_segment",tail,(0,y,z),(w,1.7*k,1.9*k),"body" if j%2 else "mint")
        s.box("abdomen_segment_ring",tail,(0,y+.72*k,z+.7*k),(w*.86,.25*k,.3*k),"deep")
    for side in (-1,1):
        s.beam("tail_cercus",tail,(side*.3*k,by-1.2*k,(2.7+(count-1)*1.55)*k),(side*.8*k,by-1.1*k,(4.0+(count-1)*1.55)*k),.3*k,"deep")
        for front in (-1,1):
            z=(-.9 if front<0 else 1.3)*k
            wing=s.bone("wing_"+("f" if front<0 else "b")+("l" if side<0 else "r"),"body",(side*1.2*k,by+.7*k,z))
            length=(7.0+1.4*s.age)*k
            # Broad stepped membrane with a long leading edge, veins and a dark pterostigma.
            s.box("wing_membrane",wing,(side*(1.4*k+length*.52),by+.7*k,z+.35*k),(length, .20*k,3.0*k),"wing",(0,front*side*-10,0))
            s.box("wing_round_tip",wing,(side*(1.4*k+length*.95),by+.7*k,z+.35*k),(1.6*k,.18*k,2.1*k),"wing")
            s.beam("leading_wing_spar",wing,(side*1.2*k,by+.9*k,z-1.1*k),(side*(length+1.4*k),by+.9*k,z-.8*k),.20*k,"vein")
            for j in range(3+s.age):
                xx=side*(2.4+j*(length/(3+s.age)))*k
                # k is already in length; interpolate in world units to keep age proportions.
                xx=side*(1.8*k+(j+1)/(4+s.age)*length)
                s.beam("cross_wing_vein",wing,(xx,by+.86*k,z-1*k),(xx+side*.8*k,by+.86*k,z+1.7*k),.12*k,"vein")
                s.box("dew_drop",wing,(xx,by+1.05*k,z+(.2 if j%2 else .8)*k),(.4*k,.3*k,.4*k),"dew",(0,45,0))
            s.box("wing_tip_patch",wing,(side*(length+.25*k),by+.86*k,z-.6*k),(1.2*k,.15*k,.65*k),"deep")
        for j in range(3):
            z=(-1+j*1.2)*k;leg=s.bone("leg_"+("l" if side<0 else "r")+str(j),"body",(side*1.3*k,by-.5*k,z))
            mid=(side*2.7*k,by-2.5*k,z-.3*k);end=(side*1.7*k,by-3.4*k,z-1.2*k)
            s.beam("dragonfly_leg",leg,(side*1.3*k,by-.5*k,z),mid,.3*k,"deep")
            s.beam("insect_tarsus",leg,mid,end,.20*k,"deep")
    return _finish(s)


def _bird_feet(s, by, spacing=1.8, mat="feet"):
    k=s.scale
    for side in (-1,1):
        x=side*spacing*k
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(x,by-1.4*k,.5*k))
        s.beam("bird_shin",leg,(x,by-1.4*k,.5*k),(x,1.0*k,-.3*k),.55*k,mat)
        for toe in range(3):
            end=(x+(toe-1)*.8*k,.25*k,-2.2*k+(abs(toe-1)*.4*k))
            s.beam("bird_toe",leg,(x,.6*k,-.3*k),end,.35*k,mat)
            s.box("tiny_bird_claw",leg,(end[0],.23*k,end[2]-.18*k),(.25*k,.27*k,.5*k),"cream")
        s.beam("back_toe",leg,(x,.5*k,-.2*k),(x,.2*k,1.4*k),.30*k,mat)


def _starling_sparrow(stage):
    s=Sculpt(stage,_p(fur="#626A7A",head="#737A87",brown="#9C8069",wing="#4B586F",tips="#9CA8B5",
                         cream="#EEE3C8",shade="#535A65",eye="#534D4E",feet="#AC8970",beak="#D5AC6B",star="#F3DBA2"))
    k,h=s.scale,s.head_scale
    by=5.1*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"sparrow_round_breast","body",(0,by,0),(7.8*k,8*k,7.9*k),"fur",6)
    _soft(s,"warm_sparrow_chest","body",(0,by-.5*k,-2.6*k),(5.7*k,5.5*k,2.5*k),"brown",5)
    _bird_feet(s,by,1.65)
    hy,hz=by+4.1*h,-1.5*k
    s.bone("head","body",(0,hy-1.8*h,hz+.7*h))
    _soft(s,"round_sparrow_head","head",(0,hy,hz),(6.9*h,6.2*h,5.8*h),"head",6)
    for side in (-1,1):
        s.box("cream_eyebrow","head",(side*1.85*h,hy+1.2*h,hz-2.64*h),(2.4*h,.65*h,.25),"cream",(0,0,side*8))
        s.mass("sparrow_cheek","head",(side*2.4*h,hy-.65*h,hz-1.8*h),(2.0*h,2.4*h,2.1*h),"cream")
    _dot_eyes(s,"head",1.8*h,hy+.25*h,hz-2.95*h,.9*h,blush=False)
    s.box("conical_upper_beak","head",(0,hy-.8*h,hz-3.22*h),(1.8*h,1.1*h,2.0*h),"beak",(-8,0,0))
    s.box("beak_tip","head",(0,hy-.9*h,hz-4.3*h),(.8*h,.7*h,.8*h),"shade")
    s.box("beak_lower","head",(0,hy-1.5*h,hz-3.3*h),(1.5*h,.35*h,1.7*h),"cream")
    for side in (-1,1):
        wing=s.bone("wing_l" if side<0 else "wing_r","body",(side*3.0*k,by+1.6*k,.4*k))
        s.mass("sparrow_wing_shoulder",wing,(side*3.45*k,by+.5*k,1.2*k),(2.3*k,5.1*k,6.0*k),"wing")
        for row in range(2+s.age):
            for j in range(4):
                x=side*(3.5+row*.12)*k;y=by+(1.6-row*1.0-j*.22)*k;z=(-.2+j*1.05+row*.25)*k
                s.box("overlapping_wing_feather",wing,(x,y,z),(.65*k,(2.8+row*.4)*k,1.25*k),"brown" if row==0 else "tips" if j==3 else "wing",(12,0,side*8))
                if s.age==2:s.box("feather_midline",wing,(x+side*.35*k,y-.4*k,z),(.11*k,1.8*k,.15*k),"tips")
    tail=s.bone("tail","body",(0,by-1.0*k,3*k))
    for j in range(3+s.age):
        x=(j-(2+s.age)/2)*1.0*k
        s.box("sparrow_tail_feather",tail,(x,by-1.5*k,5.3*k),(1.15*k,.65*k,(5.4+abs(x)*.3)*k),"wing",(-10,-x*5,0))
        s.box("tail_tip",tail,(x,by-1.05*k,7.8*k),(1.12*k,.25*k,1.0*k),"tips",(-10,-x*5,0))
    for j in range(4+3*s.age):
        x=((j%3)-1)*1.35*k;y=by+(1.6-(j//3)*1.1)*k
        s.box("chest_star_point","body",(x,y,-3.92*k),(.37*k,.75*k,.15),"star",(0,0,45))
        if s.age==2:s.box("chest_star_cross","body",(x,y,-4.02*k),(.65*k,.18*k,.12),"cream")
    return _finish(s)


def _apricot_tapir(stage):
    s=Sculpt(stage,_p(fur="#C68E77",apricot="#EAB791",dark="#8D6B65",cream="#F8E5C3",
                         shade="#725852",eye="#816355",nose="#B78879"))
    k,h=s.scale,s.head_scale
    by=_quad(s,13.8,8.1,8.4,4.9,"fur","dark")
    _soft(s,"apricot_saddle","body",(0,by+.9*k,1.3*k),(8.3*k,7.4*k,8.9*k),"apricot",6)
    # Infant stripes become an uninterrupted saddle across the adult back.
    for side in (-1,1):
        count=(8,6,4)[s.age]
        for j in range(count):
            z=(-4.7+(j%4)*2.6)*k;y=by+(-1.2+(j//4)*2.2)*k
            s.box("tapir_coat_mark","body",(side*4.0*k,y,z),(.3*k,(.55+.15*s.age)*k,(1.1+.75*s.age)*k),"cream",(0,0,side*-7))
    s.beam("tapir_neck","body",(0,by+.8*k,-4.5*k),(0,by+2.8*k,-6.2*k),5.3*k,"fur")
    hy,hz=by+2.9*h,-6.5*k
    s.bone("head","body",(0,hy-1.8*h,hz+1.5*h))
    _soft(s,"tapir_skull","head",(0,hy,hz),(6.2*h,5.7*h,7.0*h),"fur",6)
    s.mass("broad_tapir_cheek","head",(0,hy-1.1*h,hz-2.4*h),(5.1*h,3.8*h,4.1*h),"apricot")
    _dot_eyes(s,"head",1.8*h,hy+.5*h,hz-3.36*h,.8*h,blush=False)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*2.3*h,hy+2.1*h,hz+1.1*h))
        _rounded_ear(s,ear,side*2.6*h,hy+3.2*h,hz+1.2*h,2.0*h,3.0*h,"dark","pink",side*-10)
        s.box("tapir_ear_rim",ear,(side*2.6*h,hy+4.5*h,hz+.65*h),(1.6*h,.5*h,.35),"cream")
    # A small articulated prehensile snout, curved down in three soft blocks.
    snout=s.bone("snout","head",(0,hy-.3*h,hz-3.8*h))
    s.mass("snout_base",snout,(0,hy-.2*h,hz-4.0*h),(3.1*h,2.9*h,3.3*h),"nose")
    s.mass("downturned_trunk",snout,(0,hy-.95*h,hz-5.2*h),(2.65*h,2.7*h,2.8*h),"nose")
    s.mass("trunk_tip",snout,(0,hy-1.8*h,hz-5.9*h),(2.3*h,1.8*h,1.8*h),"apricot")
    for side in (-1,1):s.box("trunk_nostril",snout,(side*.62*h,hy-1.4*h,hz-6.85*h),(.5*h,.35*h,.15),"shade")
    _smile(s,"head",hy-2.3*h,hz-4.45*h,2.0*h,"shade")
    for j in range(3+2*s.age):
        s.box("tapir_neck_crest","body",(0,by+(4.0-.16*j)*k,(-4.9+j*.8)*k),(1.1*k,1.1*k,1.1*k),"dark",(0,0,0))
    tail=s.bone("tail","body",(0,by-.2*k,6.2*k))
    s.beam("tapir_tail",tail,(0,by-.2*k,6.2*k),(.5*k,by-2.0*k,7.8*k),1.0*k,"fur")
    s.mass("tail_tuft",tail,(.5*k,by-2.0*k,7.8*k),(1.5*k,1.6*k,1.5*k),"dark")
    return _finish(s)


def _sage_koala(stage):
    s=Sculpt(stage,_p(fur="#9BA89A",light="#C1CCBB",dark="#6D7F78",cream="#E8EBD5",
                         shade="#465551",eye="#655E4E",nose="#4A5651",leaf="#829F6A",leaf_light="#C0D09D",twig="#8E765C"))
    k,h=s.scale,s.head_scale
    by=5.1*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"seated_koala","body",(0,by,.8*k),(8.4*k,9*k,7.9*k),"fur",6)
    _soft(s,"koala_chest","body",(0,by-.5*k,-2.3*k),(5.9*k,6.8*k,2.7*k),"cream",5)
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.6*k,3.5*k,1.3*k))
        s.mass("koala_thigh",leg,(side*3.3*k,2.8*k,1.2*k),(3.8*k,4.1*k,4.8*k),"fur")
        s.mass("koala_big_foot",leg,(side*3.2*k,.9*k,-1*k),(3.8*k,1.8*k,4.8*k),"dark")
        _toe_marks(s,leg,side*3.2*k,.65*k,-3.48*k,.8*k,count=4,mat="cream")
    hy,hz=by+4.7*h,-1.7*k
    s.bone("head","body",(0,hy-2.1*h,hz+1.1*h))
    _soft(s,"broad_koala_head","head",(0,hy,hz),(8.2*h,7.0*h,6.6*h),"fur",6)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*3.7*h,hy+1.7*h,hz+.4*h))
        _soft(s,"koala_round_ear",ear,(side*4.0*h,hy+2*h,hz+.2*h),(4.7*h,4.8*h,2.4*h),"light",4)
        s.disc("ear_inner_velvet",ear,(side*4*h,hy+2*h,hz-1.08*h),2.9*h,3.2*h,.22,"pink")
        for j in range(4+s.age):
            a=(-100+j*220/(3+s.age))*math.pi/180
            s.box("ear_fuzzy_fringe",ear,(side*4*h+math.cos(a)*2.1*h,hy+2*h+math.sin(a)*2.1*h,hz-.1*h),(1.2*h,1.2*h,1.5*h),"cream",(0,0,math.degrees(a)))
    _dot_eyes(s,"head",2.1*h,hy+.1*h,hz-3.35*h,.84*h,blush=True)
    _soft(s,"koala_black_nose","head",(0,hy-.1*h,hz-3.3*h),(2.4*h,3.8*h,1.7*h),"nose",4)
    s.box("nose_sheen","head",(-.45*h,hy+.9*h,hz-4.18*h),(.65*h,.9*h,.12),"dark")
    s.mass("koala_muzzle","head",(0,hy-1.7*h,hz-2.8*h),(4.0*h,1.8*h,1.9*h),"cream")
    _smile(s,"head",hy-1.8*h,hz-3.85*h,1.6*h,"shade")
    branch=s.bone("branch","body",(0,by-2.5*k,-3*k))
    s.beam("sage_branch",branch,(-2*k,1.5*k,-3*k),(1.7*k,by+4.6*k,-4.2*k),.6*k,"twig")
    for j in range(3+2*s.age):
        y=(2.5+j*1.2)*k;x=(-1.8+j*.45)*k
        _leaf(s,branch,(x+(-1 if j%2 else 1)*1.0*k,y,-3.7*k),(2.5*k,.35*k,3.7*k),yaw=40 if j%2 else -35,roll=(-1 if j%2 else 1)*20)
    for side in (-1,1):
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*3.2*k,by+1.4*k,-1*k))
        wrist=(side*.9*k,by-.6*k,-4*k)
        s.beam("hugging_koala_arm",arm,(side*3.2*k,by+1.4*k,-1*k),wrist,2.2*k,"fur")
        s.mass("koala_hand",arm,wrist,(2.1*k,2.0*k,1.9*k),"dark")
        _toe_marks(s,arm,wrist[0],wrist[1]-.2*k,-5*k,.5*k,count=4,mat="cream")
    tail=s.bone("tail","body",(0,by-1*k,4.0*k))
    s.ball("tiny_koala_tail",tail,(0,by-1*k,4.7*k),(2.0*k,2.0*k,2.0*k),"light")
    return _finish(s)


def _chestnut_boar(stage):
    s=Sculpt(stage,_p(fur="#966B52",light="#BB8C66",mane="#684A40",cream="#EED3A0",
                         shade="#65493F",eye="#805C3C",nose="#D39B8C",hoof="#5F524B",tusk="#FFF1C9"))
    k,h=s.scale,s.head_scale
    by=_quad(s,13.0,8.6,8.5,4.4,"fur","hoof")
    hy,hz=by+1.8*h,-6.2*k
    s.bone("head","body",(0,hy-1.9*h,hz+1.8*h))
    _soft(s,"boar_wedge_head","head",(0,hy,hz),(7.8*h,6.7*h,7.2*h),"fur",6)
    s.mass("boar_heavy_muzzle","head",(0,hy-1.2*h,hz-3.3*h),(6.0*h,3.9*h,4.1*h),"light")
    s.mass("sensitive_pink_snout","head",(0,hy-1.0*h,hz-5.3*h),(4.4*h,2.8*h,1.5*h),"nose")
    for side in (-1,1):
        s.box("boar_nostril","head",(side*1.1*h,hy-.9*h,hz-6.1*h),(.85*h,.75*h,.16),"shade")
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*2.6*h,hy+2.4*h,hz+.4*h))
        s.box("boar_leaf_ear",ear,(side*3.1*h,hy+3.6*h,hz+.2*h),(2.4*h,3.8*h,1.8*h),"light",(0,0,side*-24))
        s.box("boar_pink_inner_ear",ear,(side*3.1*h,hy+3.6*h,hz-.77*h),(1.3*h,2.6*h,.18),"nose",(0,0,side*-24))
        # The tusks grow from ivory nubs into a three-section upward hook.
        if s.age==0:
            s.mass("milk_tusk","head",(side*2.5*h,hy-1.7*h,hz-4.8*h),(.9*h,.9*h,1.4*h),"tusk")
        else:
            pts=[(side*2.6*h,hy-1.8*h,hz-3.9*h),(side*3.2*h,hy-1.4*h,hz-5.0*h),(side*3.5*h,hy+.1*h,hz-5.5*h),(side*3.2*h,hy+(1+.4*s.age)*h,hz-5.3*h)]
            for j in range(2 if s.age==1 else 3):s.beam("ivory_tusk_hook","head",pts[j],pts[j+1],(1.05-j*.3)*h,"tusk")
    _dot_eyes(s,"head",2.25*h,hy+.9*h,hz-3.54*h,.83*h,blush=False)
    for side in (-1,1):
        s.box("boar_eyebrow","head",(side*2.3*h,hy+2*h,hz-3.25*h),(2.2*h,.6*h,.6*h),"mane",(0,0,side*-10))
        for j in range(5+2*s.age):
            z=(-4.4+(j%5)*2.15)*k;y=by+(-1.0+(j//5)*2)*k
            s.box("piglet_cream_stripe","body",(side*4.15*k,y,z),(.25*k,(.48 if s.age==0 else .32)*k,(1.6 if s.age<2 else .9)*k),"cream" if s.age<2 else "light",(0,0,side*-8))
    # Overlapping bristles follow the arched spine rather than forming a flat mohawk.
    for j in range(5+2*s.age):
        z=(-4.5+j*1.05)*k;y=by+(4.1+.7*math.sin(j*.5))*k
        s.box("chestnut_mane","body",(0,y,z),((2.8-.06*j)*k,(1.4+.25*s.age)*k,1.55*k),"mane",(20,0,0))
        if s.age>0:
            for side in (-1,1):s.box("mane_shoulder_bristle","body",(side*1.4*k,y-.4*k,z),(.8*k,1.5*k,1.2*k),"light",(15,0,side*-15))
    tail=s.bone("tail","body",(0,by+.1*k,5.9*k))
    pts=[(0,by+.1*k,5.9*k),(.8*k,by+1*k,7.2*k),(1.7*k,by+1.4*k,7.1*k),(1.8*k,by+.5*k,6.6*k)]
    for j in range(3):s.beam("curly_boar_tail",tail,pts[j],pts[j+1],.6*k,"light")
    return _finish(s)


def _papaya_toucan(stage):
    s=Sculpt(stage,_p(fur="#3B4D53",wing="#31434D",blue="#5C8290",cream="#F7E8BA",shade="#364747",
                         eye="#3A5253",feet="#6D8387",beak="#F0AE59",orange="#D97F43",green="#97AD65",eye_ring="#77B9AB"))
    k,h=s.scale,s.head_scale
    by=6.1*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"toucan_upright_body","body",(0,by,.7*k),(7.3*k,9.8*k,8.1*k),"fur",6)
    _soft(s,"ivory_toucan_throat","body",(0,by+1.7*k,-2.5*k),(5.9*k,7.1*k,2.9*k),"cream",5)
    _bird_feet(s,by,1.6)
    hy,hz=by+5.6*h,-1.3*k
    s.bone("head","body",(0,hy-2*h,hz+1*h))
    _soft(s,"toucan_skull","head",(0,hy,hz),(6.8*h,6.8*h,6.3*h),"fur",6)
    s.mass("toucan_cream_face","head",(0,hy-1.1*h,hz-2.35*h),(5.8*h,4.2*h,2.6*h),"cream")
    for side in (-1,1):s.disc("turquoise_orbital_ring","head",(side*1.9*h,hy+1.3*h,hz-3.0*h),2.0*h,2.3*h,.25,"eye_ring")
    _dot_eyes(s,"head",1.9*h,hy+1.3*h,hz-3.27*h,.72*h,blush=False)
    bill=s.bone("beak","head",(0,hy-.3*h,hz-2.8*h))
    length=(5.0+1.55*s.age)*h
    # The enormous fruit bill has a tall rounded base, a descending keel and green hook.
    _soft(s,"papaya_bill_base",bill,(0,hy-.1*h,hz-3.8*h),(4.5*h,4.2*h,4.8*h),"beak",5)
    s.box("long_upper_bill",bill,(0,hy+.4*h,hz-4.5*h-length*.33),(3.7*h,3.0*h,length*.68),"beak",(0,0,0))
    s.mass("bill_green_tip",bill,(0,hy-.1*h,hz-4.5*h-length*.70),(3.0*h,2.7*h,length*.30),"green")
    s.box("bill_lower_keel",bill,(0,hy-1.7*h,hz-4.3*h-length*.35),(3.2*h,.85*h,length*.70),"orange",(0,0,0))
    s.box("bill_mouth_line",bill,(0,hy-1.12*h,hz-4.45*h-length*.37),(3.76*h,.17*h,length*.70),"shade")
    for side in (-1,1):
        for j in range(2+s.age):
            s.box("bill_growth_ridge",bill,(side*1.9*h,hy+.5*h,hz-(4.4+j*.85)*h),(.15*h,2.5*h,.2*h),"orange",(8,0,0))
        s.box("bill_nostril",bill,(side*2.2*h,hy+.7*h,hz-3.6*h),(.14*h,.35*h,.6*h),"shade")
        wing=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.7*k,by+2*k,.7*k))
        s.mass("toucan_wing_cap",wing,(side*3.3*k,by+.6*k,1.7*k),(2.5*k,6.0*k,5.8*k),"wing")
        for row in range(2+s.age):
            for j in range(4):
                x=side*(3.6+.10*row)*k;y=by+(1.9-row*1.1-j*.25)*k;z=(-.5+j*1.3+row*.3)*k
                s.box("toucan_layered_flight_feather",wing,(x,y,z),(.65*k,3.1*k,1.4*k),"blue" if j==3 or row==0 else "wing",(18,0,side*6))
                if s.age==2:s.box("blue_feather_edge",wing,(x+side*.34*k,y-.7*k,z+.4*k),(.11*k,1.7*k,.3*k),"blue")
    tail=s.bone("tail","body",(0,by-2*k,3.2*k))
    for j in range(3+s.age):
        x=(j-(2+s.age)/2)*.9*k
        s.box("long_toucan_tail",tail,(x,by-2.8*k,6.5*k),(1.1*k,.7*k,(7.2+1*s.age)*k),"wing",(-12,-x*3,0))
        tipz=(9.0+.5*s.age)*k
        tipy=by-2.8*k+(tipz-6.5*k)*math.sin(math.radians(12))
        s.box("ivory_tail_tip",tail,(x,tipy,tipz),(1.1*k,.4*k,1.5*k),"cream",(-12,-x*3,0))
    # The broad bill meets the face below the eyes, as in a real toucan.  Keeping
    # these two landmarks separate also preserves the infant's expression.
    for p in s.parts:
        if p["bone"]=="beak":
            p["from"][1]-=1.5*h
            if "origin" in p:p["origin"][1]-=1.5*h
    bp=s.bones["beak"][1]
    s.bones["beak"]=("head",[bp[0],bp[1]-1.5*h,bp[2]])
    return _finish(s)


def _lantern_firefly(stage):
    s=Sculpt(stage,_p(body="#4E6D5E",wing="#617C64",dark="#365349",cream="#E9E8BB",shade="#3C5449",
                         eye="#3A5C51",glass="#EDC778",amber="#D5A452",light="#FFF0A8",vein="#9DB18B",membrane="#D0D8AF"))
    k,h=s.scale,s.head_scale
    by=6.0*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"firefly_thorax","body",(0,by,-.4*k),(5.0*k,4.4*k,6.0*k),"body",5)
    hy,hz=by+1.1*h,-3.5*k
    s.bone("head","body",(0,hy-1.2*h,hz+.8*h))
    _soft(s,"firefly_round_head","head",(0,hy,hz),(5.8*h,4.8*h,4.8*h),"body",5)
    s.mass("firefly_face_plate","head",(0,hy-.2*h,hz-2.0*h),(4.8*h,3.2*h,1.4*h),"cream")
    _dot_eyes(s,"head",1.4*h,hy+.4*h,hz-2.74*h,.86*h,blush=True)
    _smile(s,"head",hy-.9*h,hz-2.78*h,1.5*h,"shade")
    for side in (-1,1):
        antenna=s.bone("antenna_l" if side<0 else "antenna_r","head",(side*1.5*h,hy+1.8*h,hz-.3*h))
        pts=[(side*1.5*h,hy+1.8*h,hz-.3*h),(side*2.5*h,hy+3.4*h,hz-1.0*h),(side*3*h,hy+3.8*h,hz-2.1*h)]
        for j in range(2):s.beam("curved_firefly_antenna",antenna,pts[j],pts[j+1],.3*h,"dark")
        s.ball("antenna_pearl",antenna,pts[-1],(.85*h,.9*h,.85*h),"light")
    tail=s.bone("tail","body",(0,by,1.5*k))
    # A warm three-section lantern abdomen, framed by dark protective ribs.
    for j in range(2+s.age):
        z=(2.1+j*1.5)*k;y=by-(j*.32)*k
        w=(4.8-.25*j)*k
        _soft(s,"glowing_lantern_segment",tail,(0,y,z),(w,4.4*k,2.3*k),"glass" if j%2 else "light",4)
        s.box("lantern_dark_rim",tail,(0,y+1.98*k,z+.72*k),(w*.82,.3*k,.4*k),"amber")
        for side in (-1,1):
            s.box("lantern_longitudinal_rib",tail,(side*w*.4,y,z),(.3*k,3.4*k,1.8*k),"amber",(0,0,side*12))
            s.box("lantern_glint",tail,(side*w*.39,y+.6*k,z-.55*k),(.16*k,.7*k,.6*k),"shine")
    for side in (-1,1):
        wing=s.bone("wing_l" if side<0 else "wing_r","body",(side*1.8*k,by+1.4*k,-.4*k))
        # Split wing covers open enough for the gold lantern to remain visible.
        s.box("elytron_cover",wing,(side*3.2*k,by+1.6*k,1.3*k),(3.0*k,.8*k,7.1*k),"wing",(0,side*-20,side*-6))
        s.box("elytron_beveled_edge",wing,(side*4.4*k,by+1.4*k,1.3*k),(.5*k,.7*k,6.5*k),"vein",(0,side*-20,side*-6))
        for j in range(3+s.age):
            s.box("elytron_engraved_rib",wing,(side*(2.5+j*.32)*k,by+2.0*k,1.3*k),(.13*k,.15*k,6.1*k),"dark",(0,side*-20,0))
        flight=s.bone("wing_membrane_l" if side<0 else "wing_membrane_r",wing,(side*2.0*k,by+.7*k,.4*k))
        s.box("flight_wing_membrane",flight,(side*(5.0+.5*s.age)*k,by+.8*k,1.1*k),((6.8+1*s.age)*k,.18*k,3.6*k),"membrane",(0,side*-14,0))
        for j in range(3+s.age):
            x=side*(2.6+j*1.0)*k
            s.beam("transparent_wing_vein",flight,(side*2*k,by+1*k,.0*k),(x+side*1.0*k,by+1*k,2.7*k),.13*k,"vein")
        for j in range(3):
            z=(-1.7+j*1.4)*k;leg=s.bone("leg_"+("l" if side<0 else "r")+str(j),"body",(side*1.9*k,by-.7*k,z))
            knee=(side*3.1*k,by-2.4*k,z-.3*k);foot=(side*2.7*k,by-3.4*k,z-1.3*k)
            s.beam("firefly_leg",leg,(side*1.9*k,by-.7*k,z),knee,.35*k,"dark")
            s.beam("firefly_foot",leg,knee,foot,.24*k,"body")
            s.box("firefly_tarsus",leg,foot,(.45*k,.35*k,.7*k),"cream")
    return _finish(s)


def _pearl_oyster(stage):
    s=Sculpt(stage,_p(shell="#B9A7C6",ridge="#D6C2D9",dark="#8C7F9F",cream="#F5E0CA",shade="#7D718E",
                         eye="#75697E",lining="#EAD9E2",pearl="#FFF3DB",iridescent="#DBEAD9"))
    k,h=s.scale,s.head_scale
    by=2.4*k;s.bones["body"]=("root",[0,by,0])
    # A low cupped fan shell. The tapered wedge strips radiate from a real hinge.
    s.bone("shell_lower","body",(0,2.4*k,3.5*k))
    _soft(s,"lower_shell_cup","shell_lower",(0,2.0*k,-.3*k),(12.5*k,4.0*k,10.4*k),"shell",6)
    _soft(s,"lower_mother_of_pearl","shell_lower",(0,3.25*k,-.4*k),(10.7*k,1.9*k,8.6*k),"lining",4)
    for side in (-1,1):
        for j in range(4+s.age):
            a=math.radians(20+j*16);x=side*math.sin(a)*5.9*k;z=3.0*k-math.cos(a)*7.2*k
            s.mass("lower_shell_scallop","shell_lower",(x,2.7*k,z),(1.9*k,1.6*k,1.9*k),"cream")
    # The upper shell is raised vertically like a fan behind the pearl.
    lid=s.bone("shell_upper","body",(0,2.4*k,3.5*k))
    fan_height=(7.2+1.3*s.age)*k
    _soft(s,"raised_fan_shell",lid,(0,2.5*k+fan_height*.55,3.1*k),(12.3*k,fan_height,2.2*k),"shell",7)
    _soft(s,"inner_shell_satin",lid,(0,2.5*k+fan_height*.55,1.99*k),(10.2*k,fan_height*.83,.9*k),"lining",6)
    ribs=7+2*s.age
    hinge=(0,2.8*k,1.9*k)
    for j in range(ribs):
        a=math.radians(-66+j*132/(ribs-1));r=fan_height*.93
        end=(math.sin(a)*5.7*k,3.0*k+math.cos(a)*r,1.45*k)
        s.beam("fan_radial_rib",lid,hinge,end,.38*k,"ridge")
        s.mass("fan_cream_scallop",lid,end,(1.6*k,1.7*k,1.6*k),"cream")
        if s.age>0:
            mid=tuple(hinge[n]+(end[n]-hinge[n])*.72 for n in range(3))
            s.box("inner_shell_rainbow",lid,(mid[0],mid[1],1.12*k),(.55*k,1.3*k,.15),"iridescent",(0,0,-math.degrees(a)))
    s.mass("shell_hinge","body",(0,2.7*k,3.7*k),(4.1*k,1.6*k,2.3*k),"dark")
    # The pearl is the pet's head, pivoting independently inside the open shell.
    py,pz=5.0*h,-1.5*k
    s.bone("head","body",(0,py-1.5*h,pz+.7*h))
    _soft(s,"smiling_pearl","head",(0,py,pz),(5.5*h,5.4*h,5.4*h),"pearl",7)
    _dot_eyes(s,"head",1.28*h,py+.35*h,pz-2.75*h,.72*h,blush=True)
    _smile(s,"head",py-.9*h,pz-2.77*h,1.3*h,"shade")
    s.box("pearl_sheen","head",(-1.35*h,py+1.7*h,pz-1.95*h),(1.2*h,.55*h,.15),"shine",(0,0,-30))
    s.box("pearl_secondary_sheen","head",(-1.9*h,py+.9*h,pz-2.1*h),(.4*h,.45*h,.12),"iridescent")
    for j in range(3+s.age):
        a=j*2.39996
        s.ball("tiny_shell_pearl","body",(math.cos(a)*4.3*k,3.2*k,math.sin(a)*3.1*k-.4*k),(.6*k,.6*k,.6*k),"iridescent")
    return _finish(s)


def _clover_fawn(stage):
    s=Sculpt(stage,_p(fur="#C69A72",dark="#98765D",cream="#F6E8C8",shade="#765C4B",eye="#8D6A3C",
                         hoof="#7A7466",antler="#A4976A",leaf="#83A36A",leaf_light="#C9D396",spot="#F4E8BE"))
    k,h=s.scale,s.head_scale
    by=_quad(s,10.4,5.6,6.3,8.4,"fur","hoof",True)
    s.beam("slender_fawn_neck","body",(0,by+.7*k,-3.1*k),(0,by+5.1*k,-4.5*k),3.2*k,"fur",3.6*k)
    s.mass("fawn_cream_throat","body",(0,by+3.2*k,-5.1*k),(2.5*k,5.4*k,1.3*k),"cream")
    hy,hz=by+6.6*h,-4.8*k
    s.bone("head","body",(0,hy-2*h,hz+1.6*h))
    _soft(s,"delicate_fawn_head","head",(0,hy,hz),(5.5*h,5.5*h,6.5*h),"fur",6)
    s.mass("fawn_muzzle","head",(0,hy-1.15*h,hz-2.8*h),(3.9*h,2.2*h,2.8*h),"cream")
    _dot_eyes(s,"head",1.65*h,hy+.5*h,hz-3.14*h,.95*h,blush=True)
    _nose(s,"head",hy-.8*h,hz-4.22*h,.9*h)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*2.1*h,hy+1.9*h,hz+.3*h))
        s.box("fawn_long_leaf_ear",ear,(side*3.3*h,hy+3.2*h,hz+.2*h),(2.5*h,4.5*h,1.5*h),"fur",(0,0,side*-42))
        s.box("fawn_ear_velvet",ear,(side*3.3*h,hy+3.2*h,hz-.6*h),(1.4*h,3.3*h,.18),"pink",(0,0,side*-42))
        s.box("fawn_white_ear_edge",ear,(side*4.2*h,hy+4.1*h,hz-.55*h),(.45*h,1.7*h,.22),"cream",(0,0,side*-42))
        s.box("fawn_brow_mark","head",(side*1.7*h,hy+1.9*h,hz-2.7*h),(1.35*h,.4*h,.25),"cream",(0,0,side*8))
    # Clover rosettes on the flanks stay legible and sparse.
    for side in (-1,1):
        for j in range(4+2*s.age):
            y=by+(.5+(j//4)*1.4)*k;z=(-3+(j%4)*1.8)*k;x=side*2.69*k
            s.box("fawn_dapple","body",(x,y,z),(.25*k,.65*k,.8*k),"spot")
            if s.age==2:
                s.box("clover_dapple_wing","body",(x+side*.03*k,y+.24*k,z+.3*k),(.27*k,.4*k,.4*k),"cream")
                s.box("clover_dapple_wing","body",(x+side*.03*k,y+.24*k,z-.3*k),(.27*k,.4*k,.4*k),"cream")
    for side in (-1,1):
        horn=s.bone("antler_l" if side<0 else "antler_r","head",(side*1.5*h,hy+2.2*h,hz+.9*h))
        if s.age==0:
            _leaf(s,horn,(side*1.7*h,hy+3.2*h,hz+.8*h),(1.4*h,.3*h,1.9*h),yaw=side*30,roll=side*20)
        else:
            top=(side*2.2*h,hy+(5.4+.8*s.age)*h,hz+1.0*h)
            s.beam("living_antler_stem",horn,(side*1.5*h,hy+2.2*h,hz+.9*h),top,.55*h,"antler")
            for j in range(1+s.age):
                base=(side*(1.7+j*.2)*h,hy+(3.2+j*.9)*h,hz+1*h)
                end=(side*(3.0+j*.4)*h,hy+(4.1+j*.9)*h,hz+.6*h)
                s.beam("antler_branch",horn,base,end,.35*h,"antler")
                for q in range(3):
                    a=q*math.tau/3
                    s.box("antler_clover_leaf",horn,(end[0]+math.cos(a)*.5*h,end[1]+.2*h,end[2]+math.sin(a)*.5*h),(.9*h,.35*h,.9*h),"leaf",(0,q*120,0))
                s.box("clover_leaf_heart",horn,(end[0],end[1]+.25*h,end[2]),(.35*h,.15*h,.35*h),"leaf_light")
    tail=s.bone("tail","body",(0,by+.3*k,4.5*k))
    s.mass("white_fawn_tail",tail,(0,by+.8*k,5.5*k),(2.1*k,2.4*k,3*k),"cream")
    return _finish(s)


def _dusk_jerboa(stage):
    s=Sculpt(stage,_p(fur="#B5A1A5",light="#D1BAB3",dark="#7C718A",cream="#F5E6D3",shade="#706579",
                         eye="#6B5883",ear="#DAA8B6",toe="#A196A4",tuft="#686783"))
    k,h=s.scale,s.head_scale
    by=6.3*k;s.bones["body"]=("root",[0,by,0])
    _soft(s,"jerboa_pear_torso","body",(0,by,.8*k),(6.9*k,8.2*k,6.9*k),"fur",6)
    _soft(s,"jerboa_cream_apron","body",(0,by-.3*k,-1.9*k),(4.7*k,6.1*k,2.0*k),"cream",5)
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.1*k,by-.7*k,1*k))
        _soft(s,"spring_loaded_haunch",leg,(side*3.0*k,3.4*k,1.8*k),(3.9*k,5.1*k,5.3*k),"light",4)
        shin=s.bone("leg_l_lower" if side<0 else "leg_r_lower",leg,(side*3.3*k,2.7*k,.9*k))
        s.beam("long_jerboa_shin",shin,(side*3.3*k,2.7*k,.9*k),(side*2.8*k,.7*k,-3.0*k),.95*k,"toe")
        s.mass("very_long_hind_foot",shin,(side*2.8*k,.55*k,-3.7*k),(1.8*k,1.1*k,5.5*k),"cream")
        _toe_marks(s,shin,side*2.8*k,.45*k,-6.55*k,.48*k,mat="dark")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*2.4*k,by+1.7*k,-1.0*k))
        s.beam("tiny_jerboa_arm",arm,(side*2.4*k,by+1.7*k,-1*k),(side*1.2*k,by+.1*k,-2.9*k),.85*k,"fur")
        s.mass("jerboa_hand",arm,(side*1.2*k,by,-2.9*k),(1.1*k,1.2*k,1.3*k),"cream")
        _toe_marks(s,arm,side*1.2*k,by-.1*k,-3.6*k,.26*k,mat="dark")
    hy,hz=by+4.6*h,-1.6*k
    s.bone("head","body",(0,hy-1.9*h,hz+1.0*h))
    _soft(s,"jerboa_round_head","head",(0,hy,hz),(6.7*h,6.1*h,5.3*h),"light",6)
    s.mass("jerboa_white_muzzle","head",(0,hy-1.2*h,hz-2.4*h),(4.7*h,2.4*h,2.0*h),"cream")
    _dot_eyes(s,"head",1.8*h,hy+.5*h,hz-2.7*h,1.08*h,blush=True)
    _nose(s,"head",hy-.7*h,hz-3.48*h,.75*h,"dark")
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*1.9*h,hy+2.1*h,hz+.3*h))
        length=(7.1+1.1*s.age)*h
        _soft(s,"huge_jerboa_ear",ear,(side*2.3*h,hy+2.1*h+length*.45,hz+.5*h),(2.8*h,length,1.5*h),"fur",6)
        s.box("translucent_inner_ear",ear,(side*2.3*h,hy+2.1*h+length*.45,hz-.3*h),(1.65*h,length*.80,.2),"ear",(0,0,side*-5))
        s.box("ear_rim_highlight",ear,(side*2.8*h,hy+2.5*h+length*.45,hz-.38*h),(.25*h,length*.54,.15),"cream",(0,0,side*-5))
        for j in range(3):s.beam("long_jerboa_whisker","head",(side*1.5*h,hy-1*h,hz-3.1*h),(side*4.3*h,hy+(-1.6+.6*j)*h,hz-2.7*h),.12*h,"cream")
    tail=s.bone("tail","body",(0,by-1*k,3.0*k))
    pts=[(0,by-1*k,3*k),(0,2.9*k,6.5*k),(2.3*k,1.5*k,10.1*k),(5.1*k,2.6*k,11.9*k),(7.4*k,4.6*k,11.0*k)]
    for j in range(3+s.age//2):s.beam("sweeping_jerboa_tail",tail,pts[j],pts[j+1],(.65-j*.09)*k,"fur")
    end=pts[3+s.age//2]
    _soft(s,"dusky_tail_brush",tail,end,(2.4*k,3.4*k,2.5*k),"tuft",5)
    for side in (-1,1):
        for j in range(3+s.age):s.box("tail_brush_strand",tail,(end[0]+side*.75*k,end[1]+(-1+j*.5)*k,end[2]),(.5*k,1.1*k,1.2*k),"dark",(0,0,side*-18))
    return _finish(s)


def _ribbon_lemur(stage):
    s=Sculpt(stage,_p(fur="#A6A5A5",light="#CECBC6",black="#474C55",cream="#F4ECDD",shade="#535660",
                         eye="#CCAD68",nose="#70656A",ring="#5C606E",ribbon="#B5A1B8"))
    k,h=s.scale,s.head_scale
    by=_quad(s,9.6,5.7,6.4,5.4,"fur","black")
    s.mass("lemur_white_breast","body",(0,by,-3.3*k),(4.7*k,5.2*k,2.3*k),"cream")
    hy,hz=by+3.7*h,-4.7*k
    s.bone("head","body",(0,hy-1.7*h,hz+1.2*h))
    _soft(s,"lemur_round_skull","head",(0,hy,hz),(6.9*h,6.1*h,5.8*h),"light",6)
    for side in (-1,1):
        ear=s.bone("ear_l" if side<0 else "ear_r","head",(side*2.5*h,hy+2.1*h,hz+.6*h))
        s.box("lemur_pointed_ear",ear,(side*2.8*h,hy+3.0*h,hz+.6*h),(2.0*h,3.2*h,1.5*h),"light",(0,0,side*-13))
        s.box("lemur_dark_ear_center",ear,(side*2.8*h,hy+3*h,hz-.2*h),(1.05*h,2.0*h,.2),"black",(0,0,side*-13))
        s.disc("lemur_eye_mask","head",(side*1.8*h,hy+.35*h,hz-2.9*h),2.65*h,3.2*h,.28,"black")
        for j in range(2+s.age):
            s.box("ribbon_cheek_tuft","head",(side*(3.1+.20*j)*h,hy+(-.8+j*.7)*h,hz-.7*h),(1.45*h,1.0*h,2.8*h),"cream" if j%2 else "light",(0,side*12,side*(-20+j*10)))
    s.eyes("head",1.8*h,hy+.4*h,hz-3.08*h,.78*h,iris="eye",gentle=True)
    s.mass("lemur_tapered_muzzle","head",(0,hy-1.1*h,hz-2.5*h),(3.3*h,2.5*h,3.1*h),"cream")
    _nose(s,"head",hy-.8*h,hz-4.16*h,.95*h,"nose")
    # A single continuous tail sweeps up as a striped ribbon; every ring follows
    # the curve's tangent, so the underside is striped too.
    tail=s.bone("tail","body",(0,by+.6*k,3.9*k))
    height=(9.5+2*s.age)*k
    points=[(0,by+.6*k,3.9*k),(1.4*k,by+2.0*k,6.7*k),(2.2*k,by+6*k,8.1*k),(1.2*k,by+height,8.1*k),(-1.5*k,by+height+1*k,6.6*k),(-3.0*k,by+height-.4*k,5.5*k)]
    segs=3+s.age
    for j in range(segs):
        a,b=points[j],points[j+1]
        slices=3+s.age//2
        for q in range(slices):
            t,u=q/slices,(q+1)/slices
            p=tuple(a[n]+(b[n]-a[n])*t for n in range(3));r=tuple(a[n]+(b[n]-a[n])*u for n in range(3))
            s.beam("ringed_ribbon_tail",tail,p,r,(1.8-j*.12)*k,"ring" if (j*slices+q)%2 else "cream",(1.65-j*.1)*k)
        s.ball("tail_curve_joint",tail,b,((1.7-j*.12)*k,(1.7-j*.12)*k,(1.7-j*.12)*k),"ring" if ((j+1)*slices)%2 else "cream")
    end=points[segs]
    s.mass("ribbon_tail_tip",tail,end,(1.6*k,2.2*k,1.6*k),"black")
    for side in (-1,1):
        for j in range(3+s.age):
            z=(-2.4+j*1.3)*k
            s.box("lemur_flank_fur","body",(side*2.65*k,by+1.3*k,z),(.5*k,1.35*k,1.6*k),"light",(0,0,side*12))
        # A subtle ribbon-coloured shoulder marking is unique to this lemur.
        s.box("ribbon_shoulder_mark","body",(side*2.8*k,by+1.1*k,-2.4*k),(.35*k,2.7*k,1.5*k),"ribbon",(0,0,side*20))
    return _finish(s)


_BUILDERS = {
    "seashell_crab":_seashell_crab,"kelp_turtle":_kelp_turtle,
    "tangerine_gecko":_tangerine_gecko,"dandelion_meerkat":_dandelion_meerkat,
    "snowdrop_ermine":_snowdrop_ermine,"cinnamon_alpaca":_cinnamon_alpaca,
    "lotus_snail":_lotus_snail,"pebble_pika":_pebble_pika,
    "coral_shrimp":_coral_shrimp,"dew_dragonfly":_dew_dragonfly,
    "starling_sparrow":_starling_sparrow,"apricot_tapir":_apricot_tapir,
    "sage_koala":_sage_koala,"chestnut_boar":_chestnut_boar,
    "papaya_toucan":_papaya_toucan,"lantern_firefly":_lantern_firefly,
    "pearl_oyster":_pearl_oyster,"clover_fawn":_clover_fawn,
    "dusk_jerboa":_dusk_jerboa,"ribbon_lemur":_ribbon_lemur,
}


def build(species, stage):
    """Return an authored, grounded three-stage sculpture for the exporter."""
    try:builder=_BUILDERS[species]
    except KeyError:raise ValueError("Unknown common expansion pet: "+str(species)) from None
    return builder(stage)
