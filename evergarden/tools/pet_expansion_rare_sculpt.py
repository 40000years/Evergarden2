"""Seventeen rare companions: authored anatomy, three individual growth silhouettes.

Blockbench units, 16 per block; the face points along negative Z.  Ornaments are
attached to anatomical bones, and limbs have knees, rather than moving as sticks.
The shared exporter consumes SPECIES and build().  No random geometry is used.
"""
from __future__ import annotations

import math

from pet_rare_sculpt import Sculpt, _palette
from pet_menagerie_sculpt import _ellipsoid, _flower, _leg


def _new(stage, **colors):
    return Sculpt(stage, _palette(**colors))


def _oval(s, name, bone, p, size, mat, layers=5):
    _ellipsoid(s, name, bone, p, tuple(v / 2 for v in size), mat, layers=layers)


def _quad(s, width=7, height=7, length=11, legs=5, mat="fur", paw="cream", toes=False):
    k = s.scale
    by = (legs + height * .40) * k
    s.bones["body"] = ("root", [0, by, 0])
    _oval(s, "ribcage", "body", (0, by, -.5*k), (width*k, height*k, length*k), mat, 6)
    _oval(s, "shoulder", "body", (0, by+.5*k, -length*.25*k), (width*.94*k, height*1.04*k, length*.48*k), mat, 4)
    for side in (-1, 1):
        for front in (True, False):
            x, z = side*width*.34*k, (-1 if front else 1)*length*.32*k
            name = "leg_" + ("f" if front else "b") + ("l" if side<0 else "r")
            _leg(s, name, (x, legs*k+.9*k, z), (x, 0, z-.25*k),
                 width*.29*k, mat, lower_mat=paw, knee=(.50, (.5 if front else -1.0)*k),
                 lower_width=width*.20*k, paw=((width*(.36 if s.age==0 else .30))*k, 1.5*k, 2.7*k), paw_mat=paw)
            if toes:
                lower = name+"_lower"
                for i in range(3):
                    s.box("toe_claw", lower, (x+(i-1)*.64*k, .47*k, z-1.78*k), (.4*k,.55*k,.8*k), "gold")
    return by


def _head(s, center, size=(7,6.8,6.8), mat="fur", muzzle="cream", long=1.0, eyes=True):
    h = s.head_scale
    x, y, z = center
    s.bone("head", "body", (x,y-2*h,z+1.6*h))
    _oval(s, "cheek_skull", "head", center, tuple(v*h for v in size), mat, 6)
    _oval(s, "muzzle", "head", (x,y-1.45*h,z-size[2]*.43*h), (size[0]*.62*h,2.8*h,3.4*h*long), muzzle, 4)
    if eyes:
        s.eyes("head", size[0]*.255*h, y+.15*h, z-size[2]*.44*h, .82*h, gentle=s.age==0)
        s.box("velvet_nose", "head", (0,y-.94*h,z-size[2]*.43*h-1.78*h*long), (1.05*h,.65*h,.42*h), "pupil")
        for side in (-1,1):
            s.box("smile", "head", (side*.38*h,y-1.75*h,z-size[2]*.43*h-1.75*h*long), (.75*h,.15*h,.1),"pupil",(0,0,side*20))
    return y,z


def _ears(s, y, z, width=2, length=3.6, outer="fur", inner="blush", swept=False):
    h=s.head_scale
    for side in (-1,1):
        b=s.bone("ear_l" if side<0 else "ear_r", "head", (side*2.6*h,y+2.4*h,z))
        s.mass("ear_shell",b,(side*2.8*h,y+(2.4+length*.35)*h,z+.4*h),(width*h,length*h,1.65*h),outer)
        s.box("ear_velvet",b,(side*2.8*h,y+(2.4+length*.35)*h,z-.47*h),(width*.58*h,length*.69*h,.15),inner)
        if swept:
            s.box("ear_tip",b,(side*3.15*h,y+(2.8+length*.6)*h,z+.6*h),(width*.64*h,length*.44*h,1.3*h),outer,(0,0,-side*25))


def _path(s, name, bone, points, widths, mat, caps=True):
    for i,(a,b) in enumerate(zip(points,points[1:])):
        w=widths[i] if isinstance(widths,(tuple,list)) else widths
        m=mat[i] if isinstance(mat,(tuple,list)) else mat
        s.beam(name,bone,a,b,w,m)
        if caps and i:
            s.ball(name+"_joint",bone,a,(w,w,w),m)


def _tail(s, points, widths, mats, name="tail"):
    for i in range(len(points)-1):
        b=s.bone(name if i==0 else name+"_"+str(i), "body" if i==0 else (name if i==1 else name+"_"+str(i-1)), points[i])
        _path(s,"tail_segment",b,points[i:i+2],widths[i] if isinstance(widths,(list,tuple)) else widths,mats[i] if isinstance(mats,(list,tuple)) else mats)
    return b


def _feather(s, bone, a, b, width, mat, stripe=None):
    """A three-dimensional vane with shaft and bevelled, tapering distal lobes."""
    for j in range(3):
        t0,t1=j/3,(j+1)/3
        p=tuple(a[i]+(b[i]-a[i])*t0 for i in range(3))
        q=tuple(a[i]+(b[i]-a[i])*t1 for i in range(3))
        s.beam("overlap_vane",bone,p,q,width*(1-.24*j),mat,.36*width)
    s.beam("feather_shaft",bone,a,b,.13*width,stripe or mat)


def _rune(s,bone,x,y,z,size,mat="rune"):
    s.box("rune_upright",bone,(x,y,z),(size*.17,size,.12),mat)
    for side in (-1,1):
        s.box("rune_branch",bone,(x+side*size*.23,y+size*.12,z-.02),(size*.18,size*.62,.12),mat,(0,0,side*-42))


def _ground(s, hover=False):
    # Inspect the actual rotated cuboid corners, not just their unrotated bounds.
    # This keeps a folded feather or diagonal hoof from falling below the floor.
    low=1e9
    high=-1e9
    for p in s.parts:
        a=p["from"]; d=p["size"]; o=p.get("origin",[a[i]+d[i]/2 for i in range(3)])
        rx,ry,rz=[math.radians(v) for v in p.get("rotation",[0,0,0])]
        for xx in (a[0],a[0]+d[0]):
            for yy in (a[1],a[1]+d[1]):
                for zz in (a[2],a[2]+d[2]):
                    x,y,z=xx-o[0],yy-o[1],zz-o[2]
                    y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
                    x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
                    x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
                    low=min(low,y+o[1]);high=max(high,y+o[1])
    shift=(1.8*s.scale-low) if hover else -low
    for p in s.parts:
        p["from"][1]=round(p["from"][1]+shift,4)
        if "origin" in p:p["origin"][1]=round(p["origin"][1]+shift,4)
    for b,(parent,pivot) in list(s.bones.items()):
        if b!="root":s.bones[b]=(parent,[pivot[0],round(pivot[1]+shift,4),pivot[2]])
    result=s.finish(); result["height"]=round((high+shift)/16,3)
    return result


def _kirin(stage):
    s=_new(stage,fur="#E9F4EE",cream="#FFF6DF",mint="#7DD8BE",rose="#E9B0CE",blue="#94B9E9",violet="#BC9EE5",eye="#5BAF9E",horn="#E1F5FD",blush="#E9B6C6")
    k,h=s.scale,s.head_scale
    by=_quad(s,6.4,6.7,11,6.8,"fur","cream")
    _oval(s,"high_kirin_neck","body",(0,by+3.3*k,-4.4*k),(4.5*k,7.5*k,5*k),"fur",5)
    hy,hz=by+6.7*h,-6.0*k
    _head(s,(0,hy,hz),(5.8,6.2,6.4),long=1.25)
    _ears(s,hy,hz,1.5,3.3,inner="mint",swept=True)
    for side in (-1,1):
        for row in range(3+s.age):
            z=(-2+row*1.9)*k
            s.box("overlap_prism_scale","body",(side*2.82*k,by+.7*k,z),(.5*k,2.1*k,2.4*k),("mint","rose","blue","violet")[row%4],(0,side*12,side*12))
        # A long, stepped silken mane; adults gain a lower windblown layer.
        for j in range(3+s.age):
            y=hy+1.8*h-j*1.7*k
            s.box("silk_mane","head",(side*.8*k,y,hz+3*h+j*.4*k),(1.5*k,2.6*k,(2.2+j*.32)*k),("mint","blue","rose")[j%3],(-18,side*12,0))
    horn=s.bone("crown","head",(0,hy+2*h,hz))
    hornlen=(2.8,5.2,7.5)[s.age]
    pts=[(0,hy+2*h,hz),(0,hy+2*h+hornlen*.55,hz+.8*k),(0,hy+2*h+hornlen,hz+1.4*k)]
    _path(s,"central_prism_horn",horn,pts,[1.25*k,.8*k],["horn","blue"])
    if s.age:
        for side in (-1,1):
            for j in range(s.age):
                base=(0,hy+3.4*h+j*1.5*k,hz+.5*k)
                _path(s,"branched_crystal",horn,[base,(side*(1.7+j*.3)*k,base[1]+1.6*k,base[2]),(side*(2.5+j*.5)*k,base[1]+2.2*k,base[2]+.5*k)],[.55*k,.3*k],"rose" if j else "mint")
    s.gem("brow_prism","head",0,hy+1.5*h,hz-2.9*h,.75*h,"blue","mint")
    tailpts=[(0,by,4.5*k),(0,by+1.7*k,7*k),(1.3*k,by+1.3*k,10*k),(2.8*k,by+.3*k,12*k)]
    tip=_tail(s,tailpts,[1.1*k,.9*k,.7*k],"cream")
    for j in range(3+s.age):
        s.box("prism_tail_silk",tip,(2.8*k+(j-2)*.45*k,by+.8*k-j*.3*k,12*k),(1.15*k,3.5*k,2*k),("mint","rose","blue","violet")[j%4],(20,0,-20+j*10))
    return _ground(s)


def _panda(stage):
    s=_new(stage,fur="#B75A3D",cream="#FCEACF",brown="#633A30",brass="#C7944D",copper="#8A5939",rune="#80D6C7",eye="#74BBA9",blush="#E8AC8A")
    k,h=s.scale,s.head_scale;by=_quad(s,8.3,7.5,10,3.6,"fur","brown")
    hy,hz=by+3*h,-5.5*k
    _head(s,(0,hy,hz),(8.2,6.7,6.5))
    _ears(s,hy,hz,2.8,2.8,inner="brown")
    for side in (-1,1):
        s.box("cream_face_patch","head",(side*2.9*h,hy-.6*h,hz-2.95*h),(1.6*h,2.7*h,.4*h),"cream",(0,0,side*-24))
        s.box("red_brow","head",(side*1.9*h,hy+1.6*h,hz-2.9*h),(2.1*h,.75*h,.4*h),"brown",(0,0,side*14))
        for front in ("f","b"):
            b="leg_"+front+("l" if side<0 else "r")+"_lower"
            x=side*8.3*.34*k;z=(-1 if front=="f" else 1)*10*.32*k
            if s.age>0:
                s.mass("brass_cuff",b,(x,2.2*k,z),(2.8*k,1.2*k,2.8*k),"brass")
                s.box("cuff_rivet",b,(x,2.3*k,z-1.46*k),(.5*k,.5*k,.2*k),"rune")
    tailbase=(0,by-.4*k,4*k)
    tailpoints=[tailbase,(0,by+1.0*k,7.5*k),(1.5*k,by+3.3*k,10*k),(3*k,by+5*k,11*k),(4*k,by+6*k,10.5*k)]
    for j in range(4):
        b=s.bone("tail" if j==0 else "tail_"+str(j),"body" if j==0 else ("tail" if j==1 else "tail_"+str(j-1)),tailpoints[j])
        for half in range(2):
            a=tuple(tailpoints[j][i]+(tailpoints[j+1][i]-tailpoints[j][i])*half/2 for i in range(3))
            q=tuple(tailpoints[j][i]+(tailpoints[j+1][i]-tailpoints[j][i])*(half+1)/2 for i in range(3))
            s.beam("fluffy_tail_ring",b,a,q,(3.4-.35*j)*k,"fur" if (j+half)%2 else "cream")
    s.bone("winder","body",(0,by+3.5*k,1.3*k))
    gear=(1.4,1.9,2.6)[s.age]
    for i in range(8):
        a=i*math.tau/8
        s.box("back_gear_tooth","winder",(math.cos(a)*gear,by+3.7*k,1.3*k+math.sin(a)*gear),(.8*k,.8*k,1.25*k),"brass",(0,-math.degrees(a),0))
    s.ball("back_gear_hub","winder",(0,by+3.7*k,1.3*k),(gear*1.55,.6*k,gear*1.55),"copper")
    s.gem("clockwork_heart","body",0,by+.5*k,-5.6*k,(.7+.25*s.age)*k,"rune","brass")
    if s.age==2:
        for j in range(5):
            s.box("leather_harness","body",(0,by+2*k-j*1.2*k,-5.1*k),(4.4*k,.22*k,.45*k),"copper")
    return _ground(s)


def _whale(stage):
    s=_new(stage,skin="#49537E",belly="#B2A6D5",fin="#7276AB",rune="#D5D4FF",nebula="#BD9BD8",pink="#E9B5DB",eye="#95DCE9",blush="#BF9DCD")
    k,h=s.scale,s.head_scale
    by=8*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"whale_barrel","body",(0,by,0),(11*k,10*k,17*k),"skin",8)
    _oval(s,"velvet_belly","body",(0,by-2.6*k,-1.7*k),(8.7*k,4.6*k,13*k),"belly",5)
    s.bone("head","body",(0,by,-4*k))
    _oval(s,"rounded_whale_brow","head",(0,by+.2*h,-6*k),(10.8*h,8.9*h,10.0*h),"skin",6)
    s.eyes("head",3.0*h,by+.3*h,-10.4*h,.72*h,gentle=True)
    s.box("smiling_whale_mouth","head",(0,by-2.2*h,-10.35*h),(5.2*h,.18*h,.2),"belly")
    for side in (-1,1):
        name="wing_l" if side<0 else "wing_r"; root=(side*4.3*k,by-1.1*k,-2*k)
        s.bone(name,"body",root)
        wrist=(side*(7+2*s.age)*k,by-2*k,1*k)
        fin=s.bone(name+"_tip",name,wrist)
        s.beam("pectoral_root",name,root,wrist,2.4*k,"fin",1.4*k)
        for j in range(3+s.age):
            _feather(s,fin,wrist,(side*(10+2.2*s.age-j*.7)*k,by-2.4*k+j*.4*k,(3+j*1.5)*k),(1.5+.2*s.age)*k,"fin" if j%2 else "belly")
    tailpts=[(0,by,7*k),(0,by+1.3*k,11*k),(0,by+3.0*k,14*k),(0,by+3.5*k,16*k)]
    tip=_tail(s,tailpts,[5*k,3.5*k,2.2*k],"skin")
    for side in (-1,1):
        b=s.bone("tail_fluke_l" if side<0 else "tail_fluke_r",tip,tailpts[-1])
        for j in range(3+s.age):
            _feather(s,b,tailpts[-1],(side*(4.8+j*.7)*k,by+(3.0+j*.35)*k,(16-j*.8)*k),2.0*k,"fin" if j%2 else "belly")
    s.bone("crown","body",(0,by+4.7*k,0))
    for j in range(2+s.age):
        zz=(-1+j*2.6)*k
        s.box("nebula_dorsal_crystal","crown",(0,by+(5.6+.5*(j%2))*k,zz),(1.1*k,(2.8+.4*j)*k,2.5*k),"nebula",(0,0,12 if j%2 else -12))
    for side in (-1,1):
        stars=[]
        for j in range(3+s.age):
            p=(side*(4.6-.13*j)*k,by+(2.0+math.sin(j*1.7))*k,(-3+j*2.4)*k)
            stars.append(p);s.box("constellation_star","body",p,(.33*k,.57*k,.45*k),"rune",(0,0,45))
        for a,b in zip(stars,stars[1:]):s.beam("constellation_link","body",a,b,.13*k,"rune")
    return _ground(s,True)


def _salamander(stage):
    s=_new(stage,fur="#444052",cream="#EC9C61",coal="#61536A",ember="#F07846",flame="#FFC36C",deep="#272639",eye="#FFD384",blush="#D88A75")
    k,h=s.scale,s.head_scale;by=_quad(s,8.4,5.3,12.5,2.4,"fur","coal",True)
    hy,hz=by+1.1*h,-6.2*k
    _head(s,(0,hy,hz),(9.1,5.5,6.6),muzzle="cream")
    for side in (-1,1):
        b=s.bone("gill_l" if side<0 else "gill_r","head",(side*3.5*h,hy,hz+1*h))
        for j in range(3+s.age):
            root=(side*3.6*h,hy+(.4-j*.35)*h,hz+(j*.45)*h)
            tip=(side*(6.1+.3*s.age-.25*j)*h,hy+(3-j*1.12)*h,hz+(1.2+j*.4)*h)
            s.beam("ember_gill_stem",b,root,tip,.75*k,"ember")
            s.box("gill_flame_leaf",b,tip,(1.15*k,2.1*k,.75*k),"flame",(0,0,-side*(20+j*8)))
    points=[(0,by,5*k),(0,by+.4*k,9*k),(1*k,by+.8*k,13*k),(2.8*k,by+1.2*k,16*k),(4*k,by+2.3*k,18*k)]
    tip=_tail(s,points,[3.6*k,2.7*k,1.8*k,.9*k],"fur")
    for j in range(4+s.age):
        z=(-3+j*2.6)*k
        y=by+(2.7 if j<4 else 2.1)*k
        s.mass("basalt_dorsal_plate","body" if j<4 else "tail",(0,y,z),((2.1-.1*j)*k,(1.6+.4*s.age)*k,2*k),"coal")
        s.box("glowing_crack","body" if j<4 else "tail",(0,y+.3*k,z-.9*k),(.32*k,(1.25+.25*s.age)*k,.15),"ember")
    if s.age==2:
        for side in (-1,1):
            _path(s,"split_tail_flame",tip,[points[-1],(points[-1][0]+side*1.8,points[-1][1]+2,points[-1][2]+1.4),(points[-1][0]+side*2.1,points[-1][1]+4,points[-1][2]+1.5)],[1.5,.65],["ember","flame"])
    for side in (-1,1):
        for j in range(3+s.age):
            s.box("lava_skin_speck","body",(side*4.05*k,by+.6*k,(-3+j*1.8)*k),(.28*k,.7*k,1.2*k),"ember",(0,side*9,0))
    return _ground(s)


def _sphinx(stage):
    s=_new(stage,fur="#DFCEB5",cream="#FFF2DC",stone="#A6C4DE",indigo="#445C8D",gold="#D9B66B",eye="#82BEE7",blush="#E2B9B7")
    k,h=s.scale,s.head_scale;by=_quad(s,7.1,6.7,11,4.6,"fur","cream")
    hy,hz=by+3.5*h,-5.6*k
    _head(s,(0,hy,hz),(7.1,6.9,6.5));_ears(s,hy,hz,2.2,4.0,inner="indigo")
    for side in (-1,1):
        for j in range(3):
            s.box("nemes_lappet","head",(side*(3.4-.12*j)*h,hy+.5*h-j*1.3*k,hz+1.2*h),(1.5*h,1.4*k,3.7*h),"indigo" if j%2 else "stone")
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.6*k,by+2*k,0))
        elbow=(side*(5+s.age*1.4)*k,by+(5.2+s.age*.5)*k,.4*k)
        tipbone=s.bone("wingtip_l" if side<0 else "wingtip_r",b,elbow)
        s.beam("stone_wing_arm",b,(side*2.6*k,by+2*k,0),elbow,1.3*k,"gold")
        for j in range(4+s.age):
            _feather(s,tipbone,(elbow[0]+side*j*.37*k,elbow[1]-.18*j*k,elbow[2]+j*.6*k),
                     (side*(7+s.age*2.5+j*.55)*k,by+(2.8-j*.32)*k,(3+j*.9)*k),1.65*k,"stone" if j%2 else "cream","gold")
    s.bone("crown","head",(0,hy+2.8*h,hz))
    s.crescent("lunar_crown","crown",(0,hy+(4.6+.7*s.age)*h,hz-.3*h),(1.7+.5*s.age)*h,.6*h,.7*h,"gold",segments=7)
    s.gem("moonstone_brow","head",0,hy+1.7*h,hz-3*h,.72*h,"stone","gold")
    for j in range(3+s.age):
        s.box("collar_bead","body",((j-(2+s.age)/2)*1.1*k,by+2*k,-5.6*k),(.9*k,1.4*k,.55*k),"indigo")
    tip=_tail(s,[(0,by,4.4*k),(0,by,8*k),(2*k,by+1*k,10*k),(3*k,by+3*k,10.5*k)],[.9*k,.8*k,.65*k],"fur")
    s.ball("moonstone_tail_pearl",tip,(3*k,by+3*k,10.5*k),(2.0*k,2.5*k,2*k),"stone")
    return _ground(s)


def _quetzal(stage):
    s=_new(stage,green="#3E9F8B",mint="#83CBBB",navy="#345D79",red="#DC8079",gold="#E9C66C",eye="#F2D77E",blush="#E5B3B1")
    k,h=s.scale,s.head_scale;by=9*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"quetzal_torso","body",(0,by,0),(7*k,8*k,9*k),"green",7)
    _oval(s,"coral_breast","body",(0,by-.2*k,-3.0*k),(5.2*k,7*k,3*k),"red",5)
    hy,hz=by+4.5*h,-2.8*k
    s.bone("head","body",(0,hy-2*h,hz+1*h))
    _oval(s,"emerald_bird_head","head",(0,hy,hz),(6.5*h,6*h,6*h),"green",6)
    s.eyes("head",1.7*h,hy+.3*h,hz-2.72*h,.72*h)
    s.mass("short_golden_beak","head",(0,hy-1*h,hz-3.7*h),(1.8*h,1.4*h,2.6*h),"gold")
    s.bone("crown","head",(0,hy+2*h,hz))
    for j in range(2+s.age):
        p=[((j-(1+s.age)/2)*.9*h,hy+2.5*h,hz+.3*h),((j-(1+s.age)/2)*1.4*h,hy+(5+j*.4)*h,hz+1*h),((j-(1+s.age)/2)*1.6*h,hy+(5.5+j*.4)*h,hz+2.6*h)]
        _path(s,"lightning_crest","crown",p,[.75*h,.48*h],"gold" if j%2 else "mint")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*1.6*k,by-2*k,.4*k))
        shin=s.bone("leg_l_lower" if side<0 else "leg_r_lower",leg,(side*1.6*k,by-4*k,.1*k))
        _path(s,"perching_leg",leg,[(side*1.6*k,by-2*k,.4*k),(side*1.6*k,by-4*k,.1*k)],.85*k,"navy")
        s.beam("perching_shin",shin,(side*1.6*k,by-4*k,.1*k),(side*1.6*k,2*k,-.8*k),.65*k,"gold")
        for toe in range(3):s.beam("grasping_toe",shin,(side*1.6*k,2*k,-.8*k),(side*1.6*k+(toe-1)*.7*k,1.8*k,-2.4*k),.38*k,"gold")
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.5*k,by+1*k,-1*k))
        wrist=(side*(5.8+1.5*s.age)*k,by+2*k,.5*k)
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",b,wrist)
        s.beam("wing_scapula",b,(side*2.5*k,by+1*k,-1*k),wrist,1.7*k,"green")
        for j in range(5+s.age):
            _feather(s,distal,(wrist[0]+side*j*.55*k,wrist[1]-.25*j*k,wrist[2]+j*.2*k),(side*(8.5+s.age*2.5+.5*j)*k,by+(.6-j*.35)*k,(4+j*.7)*k),1.6*k,("green","mint","navy")[j%3],"gold" if s.age==2 else "mint")
    tails=2 if s.age<2 else 4
    for j in range(tails):
        side=-1 if j%2==0 else 1; spread=(j//2+.5)*1.4*k
        root=(side*spread,by-1*k,3.2*k);b=s.bone("tail" if j==0 else "tail_"+str(j),"body",root)
        p=[root,(side*spread*1.5,by-1.4*k,7*k),(side*(spread+1.3*k),by-2.8*k,(10+2.5*s.age)*k),(side*(spread+2*k),by-2.4*k,(12+3*s.age)*k)]
        _path(s,"ribbon_tail",b,p,[1.3*k,1.1*k,.75*k],["green","mint","gold"])
    return _ground(s,True)


def _baku(stage):
    s=_new(stage,fur="#EAD7E1",cream="#FFF0DB",pink="#DF97BA",petal="#F6C3DA",leaf="#7AAE96",wood="#996F78",eye="#786790",blush="#E9A8C8")
    k,h=s.scale,s.head_scale;by=_quad(s,9.2,8.1,12.4,3.8,"fur","cream")
    hy,hz=by+2.4*h,-5.6*k
    _head(s,(0,hy,hz),(7.5,7.1,7.4),muzzle="cream",eyes=False)
    s.eyes("head",2.1*h,hy+.55*h,hz-3.2*h,.87*h,gentle=True)
    _ears(s,hy,hz,2.7,3.3,outer="leaf",inner="petal")
    # The trunk is a bent three-joint muzzle, ending in two real nostrils.
    trunklen=(3,5.2,6.8)[s.age]
    points=[(0,hy-1*h,hz-3.1*h),(0,hy-2.2*h,hz-5.1*h),(0,hy-2.1*h-trunklen*.55,hz-5.6*h),(0,hy-1.4*h-trunklen*.58,hz-6.9*h)]
    end=_tail(s,points,[2.6*h,2.0*h,1.4*h],"cream",name="trunk")
    for side in (-1,1):s.box("trunk_nostril",end,(side*.35*h,points[-1][1]+.1*h,points[-1][2]-.7*h),(.32*h,.4*h,.16),"wood")
    for side in (-1,1):
        for j in range(3+s.age):
            z=(-3+j*2.0)*k
            s.box("sakura_flank_patch","body",(side*4.42*k,by+.8*k,z),(.28*k,1.9*k,1.8*k),"pink",(0,side*12,0))
            if j%2==0:
                s.box("flank_petal_glint","body",(side*4.57*k,by+1.15*k,z-.2*k),(.16*k,.6*k,.8*k),"petal")
    s.bone("blossom_branch","body",(0,by+3.6*k,1*k))
    branch=[(-2*k,by+3.5*k,2*k),(0,by+5*k,1.6*k),(2.1*k,by+5.5*k,2.2*k)]
    _path(s,"sakura_branch","blossom_branch",branch,.55*k,"wood")
    for j in range(1+s.age*2):
        x=(j-(s.age))*1.25*k;y=by+(5.4+.3*(j%2))*k;z=(1.8+(j%2)*.8)*k
        _flower(s,"blossom_branch",(x,y,z),(1.15+.14*s.age)*k,"petal","gold",petals=5,facing="up",spin=j*18)
        s.box("sakura_leaf","blossom_branch",(x+1*k,y-.3*k,z+.8*k),(1.8*k,.28*k,.8*k),"leaf",(0,-30,0))
    tail=_tail(s,[(0,by-.6*k,5*k),(0,by,7*k),(1.1*k,by+1.5*k,7.8*k)],[1.2*k,.8*k],"pink")
    s.mass("petal_tail_tuft",tail,(1.1*k,by+1.5*k,7.8*k),(1.6*k,2.3*k,1.7*k),"cream")
    for j in range(2+s.age):
        s.box("neck_leaf_ruff","body",((j-(1+s.age)/2)*1.5*k,by+2.5*k,-5.2*k),(1.5*k,3*k,1.3*k),"leaf",(10,0,(-20+j*12)))
    return _ground(s)


def _basilisk(stage):
    s=_new(stage,scale="#414451",belly="#666979",edge="#77708B",obsidian="#343342",rune="#85CE9B",jade="#B6E2A3",eye="#BCE88C",blush="#BC99AE")
    k,h=s.scale,s.head_scale;by=4.6*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"long_basilisk_trunk","body",(0,by,1*k),(7.5*k,6*k,15*k),"scale",7)
    _oval(s,"pale_keel","body",(0,by-1.9*k,.8*k),(5.4*k,2.6*k,13*k),"belly",5)
    for side in (-1,1):
        for j in range(3):
            z=(-4+j*4.2)*k;x=side*3.3*k
            name="leg_"+("f" if j==0 else "b" if j==2 else "m")+("l" if side<0 else "r")
            _leg(s,name,(x,by-.4*k,z),(side*5*k,0,z-.7*k),1.6*k,"scale",lower_mat="belly",knee=(.4,.7*k),lower_width=1.1*k,paw=(2.0*k,1.1*k,2.5*k))
            for toe in range(2):s.box("obsidian_claw",name+"_lower",(side*5*k+(toe-.5)*.9*k,.35*k,z-2.1*k),(.4*k,.5*k,1*k),"jade")
    hy,hz=by+1.7*h,-7*k
    _head(s,(0,hy,hz),(7.8,5.8,7.8),mat="scale",muzzle="belly",long=1.25)
    # Back scales are large plates that interlock rather than a flat painted strip.
    for row in range(4+s.age):
        z=(-3.8+row*2.3)*k
        for side in (-1,1):
            s.box("interlocking_basalt_plate","body",(side*1.9*k,by+2.7*k,z),(3.7*k,.85*k,2.6*k),"edge",(0,side*12,side*12))
            s.box("mineral_seam","body",(side*3.33*k,by+2.25*k,z),(.22*k,.75*k,1.7*k),"rune",(0,side*12,side*12))
    s.bone("crown","head",(0,hy+2.4*h,hz))
    for j in range(3+s.age*2):
        x=(j-(2+s.age*2)/2)*.9*h
        length=(2.6+s.age*.9)*(1-.35*abs(x)/(3.8*h))
        s.box("basilisk_crown_crystal","crown",(x,hy+2.7*h+length*.4,hz+.4*h),(1.0*h,length,.9*h),"obsidian",(0,0,-x*9))
        s.box("crystal_inner_seam","crown",(x,hy+2.6*h+length*.35,hz-.08*h),(.22*h,length*.69,.13),"rune",(0,0,-x*9))
    for side in (-1,1):
        for j in range(2+s.age):
            s.box("cheek_blade","head",(side*(3.5+j*.3)*h,hy-j*.6*h,hz+1.4*h),(1.7*h,.8*h,2.6*h),"edge",(0,side*25,side*-15))
    points=[(0,by,7*k),(0,by+.3*k,11*k),(1.5*k,by+1*k,15*k),(3.2*k,by+1.5*k,18*k)]
    tip=_tail(s,points,[3.4*k,2.3*k,1.1*k],"scale")
    s.box("spear_tail_blade",tip,points[-1],(2.0*k,.85*k,4.2*k),"obsidian",(0,35,0))
    s.box("spear_tail_seam",tip,(3.2*k,by+1.95*k,18*k),(.22*k,.16*k,3.4*k),"rune",(0,35,0))
    return _ground(s)


def _narwhal(stage):
    s=_new(stage,skin="#91C5DB",belly="#D9EEF0",ice="#B6E1EB",blue="#5798BD",horn="#EDF8E9",eye="#557CBA",blush="#E7BCCF")
    k,h=s.scale,s.head_scale;by=8*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"narwhal_torso","body",(0,by,1*k),(9.6*k,8.8*k,15*k),"skin",7)
    _oval(s,"pearl_belly","body",(0,by-2*k,0),(7.6*k,4.4*k,13*k),"belly",5)
    s.bone("head","body",(0,by,-4*k))
    _oval(s,"soft_narwhal_forehead","head",(0,by+.3*h,-5.0*k),(9*h,8.2*h,8*h),"skin",6)
    s.eyes("head",2.55*h,by+.3*h,-8.4*h,.8*h,gentle=True)
    s.box("narwhal_smile","head",(0,by-2*h,-8.6*h),(3.7*h,.2*h,.16),"blue")
    horn=s.bone("horn","head",(0,by+1.1*h,-8*k))
    length=(2.8,6.6,10.5)[s.age]
    # Interlocking helix bands taper around the forward-facing horn, not a rod.
    for j in range(4+s.age*3):
        t=j/(4+s.age*3);width=(1.15-.85*t)*h
        z=-8.3*h-length*t
        s.box("spiral_ice_horn",horn,(0,by+1.1*h,z),(width,width,length/(4+s.age*3)+.2),"horn",(0,0,j*32))
        a=j*math.pi*.62
        s.box("sapphire_horn_ridge",horn,(math.cos(a)*width*.48,by+1.1*h+math.sin(a)*width*.48,z),(width*.25,width*.25,.55),"blue",(0,0,j*32))
    for side in (-1,1):
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*3.7*k,by-1.5*k,-1*k))
        wrist=(side*6.1*k,by-2*k,1.5*k);d=s.bone("wingtip_l" if side<0 else "wingtip_r",b,wrist)
        s.beam("pectoral_flipper",b,(side*3.7*k,by-1.5*k,-1*k),wrist,2.1*k,"skin",1.2*k)
        for j in range(3+s.age):
            _feather(s,d,wrist,(side*(7.3+j*.8+s.age*.4)*k,by+(-2.2+j*.3)*k,(2.3+j*.75)*k),1.7*k,"ice" if j%2 else "belly")
    tailpoints=[(0,by,6.6*k),(0,by+1*k,10*k),(0,by+2.3*k,13*k),(0,by+3.1*k,15*k)]
    tip=_tail(s,tailpoints,[4.7*k,3*k,1.6*k],"skin")
    for side in (-1,1):
        b=s.bone("tail_fluke_l" if side<0 else "tail_fluke_r",tip,tailpoints[-1])
        for j in range(3+s.age):
            _feather(s,b,tailpoints[-1],(side*(3.7+j*.65)*k,by+(3.4+j*.35)*k,(15-j*.6)*k),1.65*k,"ice","blue")
    for side in (-1,1):
        for j in range(3+s.age):
            s.box("frost_speckle","body",(side*4.35*k,by+(1.5+.4*(j%2))*k,(-2+j*1.8)*k),(.2*k,.7*k,.9*k),"ice",(0,0,45))
    return _ground(s,True)


def _mantis(stage):
    s=_new(stage,skin="#8EBFA1",belly="#CED9B5",petal="#E9BACF",petal_light="#F6DFE8",leaf="#578D79",vein="#A9C6AA",eye="#B57BBD",blush="#E6ACBE")
    k,h=s.scale,s.head_scale;by=8.3*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"mantis_abdomen","body",(0,by-1.0*k,2.3*k),(5.2*k,6.3*k,9*k),"skin",6)
    s.mass("upright_thorax","body",(0,by+2*k,-1.2*k),(2.7*k,7*k,3.5*k),"skin")
    for j in range(4+s.age):
        s.box("abdomen_segment","body",(0,by-.6*k+j*.6*k,(.1+j*1.0)*k),(4.6*k,.65*k,1.5*k),"belly",(-14,0,0))
    for side in (-1,1):
        for j in range(2):
            name="leg_"+("f" if j==0 else "b")+("l" if side<0 else "r")
            root=(side*1.5*k,by+(j*.4)*k,(1+j*3)*k);knee=(side*(5+j*.6)*k,by-2.5*k,(2+j*3)*k);foot=(side*(5.4+j*.8)*k,.5*k,(1.2+j*4)*k)
            b=s.bone(name,"body",root);d=s.bone(name+"_lower",b,knee)
            s.beam("mantis_upper_leg",b,root,knee,.85*k,"skin")
            s.ball("insect_knee",d,knee,(1.2*k,1.2*k,1.2*k),"petal")
            s.beam("slender_tibia",d,knee,foot,.55*k,"leaf")
            s.box("mantis_foot",d,(foot[0],.28*k,foot[2]-.4*k),(.7*k,.55*k,1.3*k),"leaf")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*1.6*k,by+3.2*k,-2*k))
        elbow=(side*4.6*k,by+1.6*k,-3*k);fore=s.bone("arm_l_lower" if side<0 else "arm_r_lower",arm,elbow)
        s.beam("raised_mantis_humerus",arm,(side*1.6*k,by+3.2*k,-2*k),elbow,1.05*k,"skin")
        end=(side*4.0*k,by+5.5*k,-5.2*k)
        s.beam("folded_raptorial_forearm",fore,elbow,end,1.5*k,"petal")
        for j in range(3+s.age):
            t=(j+.35)/(3+s.age)
            p=tuple(elbow[i]+(end[i]-elbow[i])*t for i in range(3))
            s.box("lotus_scythe_petal",fore,(p[0]+side*.6*k,p[1],p[2]),(2.2*k,(1.5+.2*s.age)*k,.75*k),"petal_light" if j%2 else "petal",(0,side*12,side*-25))
            s.box("mantis_inner_tooth",fore,(p[0]-side*.65*k,p[1],p[2]-.25*k),(.65*k,.75*k,.5*k),"belly",(0,0,side*20))
        wing=s.bone("wing_l" if side<0 else "wing_r","body",(side*1.2*k,by+1.8*k,0))
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",wing,(side*3.5*k,by+2.1*k,2*k))
        for j in range(3+s.age):
            a=(side*(1.4+j*.35)*k,by+2*k,(.5+j*.7)*k)
            b=(side*(4+s.age*1.3+j*.5)*k,by+(1.0-j*.1)*k,(6.4+j*.6)*k)
            _feather(s,distal,a,b,1.6*k,"petal_light" if j%2 else "vein","leaf")
    hy,hz=by+6.1*h,-2.7*k
    s.bone("head","body",(0,hy-1.6*h,hz+.8*h))
    s.mass("triangular_mantis_head","head",(0,hy,hz),(6.1*h,3.5*h,3.4*h),"skin")
    s.mass("tapered_mantis_chin","head",(0,hy-1.4*h,hz-.1*h),(3.3*h,2.0*h,3.0*h),"belly")
    for side in (-1,1):s.ball("bulb_eye","head",(side*2.7*h,hy+.55*h,hz-1.4*h),(2.1*h,2.3*h,1.7*h),"belly")
    s.eyes("head",2.7*h,hy+.65*h,hz-2.05*h,.58*h,gentle=True)
    for side in (-1,1):
        b=s.bone("antenna_l" if side<0 else "antenna_r","head",(side*1*h,hy+1.6*h,hz))
        _path(s,"curved_antenna",b,[(side*h,hy+1.6*h,hz),(side*1.4*h,hy+3.8*h,hz+.4*h),(side*2.2*h,hy+4.5*h,hz-.2*h)],[.25*h,.18*h],"leaf")
    _flower(s,"head",(0,hy+2.1*h,hz+.2*h),(1.15+.43*s.age)*h,"petal","gold",petals=5+s.age,facing="up")
    return _ground(s)


def _armadillo(stage):
    s=_new(stage,fur="#947D69",cream="#CBB39A",shell="#7E7F82",edge="#ACABA2",copper="#BC906E",rune="#8BDAD6",eye="#84CDCB",blush="#D8AAA8")
    k,h=s.scale,s.head_scale;by=_quad(s,9.4,7.0,11.8,2.6,"fur","cream",True)
    # Transverse overlapping armour bands follow the curved back in three planes.
    bands=3+s.age
    for row in range(bands):
        z=(-3.6+row*7.3/max(1,bands-1))*k
        center_y=by+(2.7+.35*math.sin(row*math.pi/max(1,bands-1)))*k
        s.mass("dorsal_shell_band","body",(0,center_y,z),(7.8*k,1.4*k,2.6*k),"shell")
        s.box("band_central_keel","body",(0,center_y+.82*k,z),(1.5*k,.6*k,2.2*k),"edge")
        for side in (-1,1):
            s.box("shell_shoulder_shingle","body",(side*3.8*k,center_y-.85*k,z),(2.3*k,1.4*k,2.6*k),"shell",(0,0,side*-42))
            s.box("shell_edge_shingle","body",(side*4.6*k,center_y-2*k,z),(1.3*k,2.2*k,2.5*k),"edge",(0,0,side*-12))
            _rune(s,"body",side*2.5*k,center_y+.5*k,z-1.34*k,.9*k)
    hy,hz=by+.5*h,-7*k
    _head(s,(0,hy,hz),(5.6,4.7,6.4),long=1.45)
    _ears(s,hy,hz,1.5,3.7,outer="copper",inner="cream")
    s.mass("forehead_armour","head",(0,hy+2.0*h,hz+.3*h),(4.6*h,1.25*h,5.5*h),"shell")
    _rune(s,"head",0,hy+1.65*h,hz-2.72*h,1.3*h)
    points=[(0,by,5*k),(0,by-.2*k,8*k),(1*k,by+.4*k,11*k),(2*k,by+1.5*k,13*k)]
    tail=_tail(s,points,[2.2*k,1.6*k,.8*k],"fur")
    for j in range(5+s.age):
        t=j/(5+s.age);z=(5+8*t)*k;x=2*t*t*k;y=by+(-.2+1.7*t*t)*k
        s.mass("tail_armour_ring","tail" if t<.4 else "tail_1" if t<.76 else tail,(x,y,z),((2.8-1.9*t)*k,(2.5-1.6*t)*k,.8*k),"edge" if j%2 else "shell")
    s.bone("shell_keystone","body",(0,by+3.5*k,1*k))
    if s.age:
        for side in (-1,1):
            s.box("keystone_rune_pylon","shell_keystone",(side*1.3*k,by+(4.6+s.age*.3)*k,1*k),(1.1*k,(1.8+s.age*.4)*k,1.0*k),"copper")
            _rune(s,"shell_keystone",side*1.3*k,by+(4.8+s.age*.3)*k,.45*k,.9*k)
    return _ground(s)


def _seahorse(stage):
    s=_new(stage,skin="#AEA3D5",belly="#E0D6E8",ridge="#7C86B2",fin="#C2B8E4",rune="#C7F0EA",gold="#DAB779",eye="#8AD7CD",blush="#DFB6D8")
    k,h=s.scale,s.head_scale;by=12*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"seahorse_chest","body",(0,by,0),(6.2*k,10*k,6.0*k),"skin",7)
    _oval(s,"pale_keel","body",(0,by-.5*k,-1.7*k),(4.3*k,7.5*k,3.3*k),"belly",5)
    for j in range(5+s.age):
        y=by+(-3+j*1.15)*k
        s.box("ribbed_front_armour","body",(0,y,-3.03*k),((4.4-.2*abs(j-3))*k,.54*k,.45*k),"gold" if j%3==0 else "ridge")
        for side in (-1,1):s.box("ribbed_side_armour","body",(side*2.5*k,y,.3*k),(.45*k,.63*k,3.5*k),"ridge")
    hy,hz=by+6.0*h,-2*k
    s.bone("head","body",(0,hy-2*h,hz+1.3*h))
    _oval(s,"horse_skull","head",(0,hy,hz),(5.2*h,6.4*h,5.6*h),"skin",6)
    s.mass("tubular_seahorse_muzzle","head",(0,hy-1.2*h,hz-3.7*h),(2.3*h,2.2*h,5.1*h),"belly")
    s.mass("flared_muzzle_lip","head",(0,hy-1.2*h,hz-6.1*h),(2.65*h,2.5*h,.65*h),"gold")
    s.box("muzzle_opening","head",(0,hy-1.2*h,hz-6.46*h),(1.1*h,.7*h,.15),"pupil")
    s.eyes("head",1.45*h,hy+.6*h,hz-2.55*h,.67*h,gentle=True)
    for side in (-1,1):
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.2*k,by+.6*k,1*k))
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",b,(side*3.2*k,by+.6*k,2.0*k))
        for j in range(4+s.age):
            root=(side*2.2*k,by+(.7-j*.4)*k,1*k)
            tip=(side*(5.3+.6*s.age)*k,by+(3-j*1.3)*k,(3.5+.3*j)*k)
            _feather(s,distal,root,tip,1.2*k,"fin","rune")
    # A true curling tail with separately pivoted turns, growing another half-turn.
    curl=(math.pi*1.20,math.pi*1.55,math.pi*1.9)[s.age]
    points=[(0,by-3.8*k,.6*k)]
    for j in range(1,7):
        t=j/6;a=-math.pi/2+curl*t;r=(3.8-1.9*t)*k
        points.append((0,(6.1+math.sin(a)*(3.8-1.9*t))*k,(1.2+math.cos(a)*(3.8-1.9*t))*k))
    for j in range(6):
        b=s.bone("tail" if j==0 else "tail_"+str(j),"body" if j==0 else ("tail" if j==1 else "tail_"+str(j-1)),points[j])
        s.beam("coiled_tail_segment",b,points[j],points[j+1],(2.0-j*.25)*k,"skin")
        s.mass("tail_plate_ring",b,points[j+1],((2.1-j*.25)*k,.55*k,(2.0-j*.23)*k),"ridge")
    s.bone("crown","head",(0,hy+2.6*h,hz+1*h))
    for j in range(3+s.age):
        x=(j-(2+s.age)/2)*.85*h
        s.box("astral_coronet_spine","crown",(x,hy+(3.8+.4*(j%2))*h,hz+1.1*h),(.65*h,(2.2+.4*s.age)*h,.8*h),"gold",(0,0,-x*15))
        s.ball("coronet_star_pearl","crown",(x*1.25,hy+(4.7+.4*s.age+.4*(j%2))*h,hz+1.1*h),(.75*h,.75*h,.75*h),"rune")
    for j in range(3+s.age):
        s.box("dorsal_spine","body",(0,by+(-2+j*1.5)*k,3.25*k),(1*k,1.2*k,(2.1+.3*s.age)*k),"fin",(20,0,0))
    return _ground(s,True)


def _lion(stage):
    s=_new(stage,fur="#C49358",cream="#F6D7A1",mane="#98613F",amber="#DAB06A",copper="#B87D47",fire="#F9D179",eye="#F7D87F",blush="#E1AB8F")
    k,h=s.scale,s.head_scale;by=_quad(s,8.5,7.8,12,5.2,"fur","cream",True)
    hy,hz=by+3.5*h,-5.6*k
    _head(s,(0,hy,hz),(8.1,7.7,7.2));_ears(s,hy,hz,2.2,2.7,inner="mane")
    mane=s.bone("mane","head",(0,hy,hz+1.7*h))
    rays=(8,10,12)[s.age]
    radius=(4.0,5.1,6.1)[s.age]*h
    for j in range(rays):
        a=math.tau*j/rays
        x,y=math.sin(a)*radius,hy+math.cos(a)*radius
        # Deep fur locks: each ray has a broad root and its own tapered tip.
        s.box("sun_mane_lock",mane,(x,y,hz+.5*h),(2.4*h,3.4*h,3.8*h),"mane",(0,0,-math.degrees(a)))
        s.box("sun_mane_bevel",mane,(x*1.1,hy+(y-hy)*1.1,hz-.1*h),(1.6*h,2.2*h,2.9*h),"amber",(0,0,-math.degrees(a)))
        if s.age==2:
            s.box("sun_mane_ray_tip",mane,(x*1.22,hy+(y-hy)*1.22,hz+.2*h),(.8*h,2.0*h,1.5*h),"fire",(0,0,-math.degrees(a)))
    # Chest armour is fitted in overlapping convex plates.
    for j in range(2+s.age):
        w=(5.9-j*.45)*k
        s.mass("sunforge_breastplate","body",(0,by+(2.4-j*1.2)*k,-5.6*k),(w,1.6*k,1*k),"copper")
    s.gem("solar_heart","body",0,by+1.4*k,-6.23*k,.95*k,"fire","gold")
    for side in (-1,1):
        b="leg_f"+("l" if side<0 else "r")+"_lower"
        if s.age:
            s.mass("forged_bracer",b,(side*8.5*.34*k,2.6*k,-4*k),(2.7*k,2.2*k,3*k),"copper")
            s.box("bracer_sun_inlay",b,(side*8.5*.34*k,2.6*k,-5.56*k),(.7*k,1.3*k,.16),"fire",(0,0,45))
    points=[(0,by-.4*k,5*k),(0,by,9*k),(1.8*k,by+2.1*k,11.5*k),(3.0*k,by+4*k,11*k)]
    tip=_tail(s,points,[1.15*k,.9*k,.7*k],"fur")
    for j in range(3+s.age):
        s.box("tail_flame_lock",tip,(3*k+(j-2)*.55*k,by+(4+j*.35)*k,11*k),(1.2*k,(3+j*.2)*k,1.8*k),"mane" if j%2 else "fire",(0,0,(j-2)*-16))
    return _ground(s)


def _crane(stage):
    s=_new(stage,paper="#F5EED8",crease="#D4C8AA",ink="#516D87",red="#C97976",gold="#CEAE6F",eye="#334F65",blush="#E7B9A6")
    k,h=s.scale,s.head_scale;by=9.3*k;s.bones["body"]=("root",[0,by,0])
    # The torso is a folded octahedron with a dorsal ridge and tucked belly flaps.
    s.box("paper_body_core","body",(0,by,0),(5.5*k,4*k,8.4*k),"paper")
    for side in (-1,1):
        s.box("folded_body_facet","body",(side*2.1*k,by+1.6*k,0),(3.2*k,.55*k,8.3*k),"paper",(0,0,side*34))
        s.box("belly_tucked_flap","body",(side*1.4*k,by-1.9*k,1*k),(3.0*k,.45*k,6*k),"crease",(0,0,-side*24))
        s.box("body_fold_line","body",(side*2.6*k,by+.1*k,0),(.14*k,.2*k,7.8*k),"gold")
    s.box("dorsal_paper_ridge","body",(0,by+2.3*k,.2*k),(.7*k,1.8*k,7*k),"paper")
    neck=s.bone("neck","body",(0,by,-3.2*k))
    neck_top=by+(4+1.5*s.age)*k
    neckpoints=[(0,by,-3.2*k),(0,by+2.8*k,-4.6*k),(0,neck_top,-3.6*k)]
    for j in range(2):
        s.beam("folded_paper_neck",neck,neckpoints[j],neckpoints[j+1],1.6*k,"paper",1.3*k)
        s.beam("neck_fold_seam",neck,(.82*k,neckpoints[j][1],neckpoints[j][2]),(.82*k,neckpoints[j+1][1],neckpoints[j+1][2]),.16*k,"gold")
    hy,hz=neck_top+.7*h,-4.8*k
    s.bone("head",neck,(0,hy-.7*h,hz+.6*h))
    s.mass("folded_crane_head","head",(0,hy,hz),(3.9*h,3.1*h,4.6*h),"paper")
    s.box("red_crown_fold","head",(0,hy+1.57*h,hz-.4*h),(2.0*h,.3*h,1.8*h),"red")
    s.eyes("head",1.05*h,hy+.1*h,hz-2.12*h,.44*h,gentle=True)
    beakroot=(0,hy-.5*h,hz-2.0*h);beaktip=(0,hy-.7*h,hz-(5.5+.5*s.age)*h)
    for j in range(3):
        t=j/3;u=(j+1)/3
        a=tuple(beakroot[i]+(beaktip[i]-beakroot[i])*t for i in range(3));b=tuple(beakroot[i]+(beaktip[i]-beakroot[i])*u for i in range(3))
        s.beam("tapered_paper_beak","head",a,b,(1.05-.29*j)*h,"gold")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*1.3*k,by-1.5*k,.8*k))
        knee=(side*1.3*k,4.5*k,1.4*k);foot=(side*1.3*k,1.4*k,-.4*k)
        lower=s.bone("leg_l_lower" if side<0 else "leg_r_lower",leg,knee)
        s.beam("folded_shin_upper",leg,(side*1.3*k,by-1.5*k,.8*k),knee,.45*k,"ink")
        s.beam("folded_shin_lower",lower,knee,foot,.35*k,"ink")
        for j in range(3):s.beam("paper_toe",lower,foot,(side*1.3*k+(j-1)*.55*k,1.1*k,-1.8*k),.24*k,"gold")
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2*k,by+1*k,-1*k))
        wrist=(side*(4.8+1.6*s.age)*k,by+(3+s.age*.5)*k,0)
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",b,wrist)
        s.beam("folded_wing_root",b,(side*2*k,by+1*k,-1*k),wrist,2.6*k,"paper",.55*k)
        # Parallel folds are volumetric accordion ribs, with an inked end tab.
        for j in range(5+s.age):
            a=(wrist[0]+side*.34*j*k,wrist[1]-.15*j*k,wrist[2]+j*.65*k)
            end=(side*(8.5+2*s.age+j*.4)*k,by+(3-j*.45)*k,(2.2+j*.9)*k)
            width=(1.4+.12*s.age)*k
            s.beam("accordion_paper_panel",distal,a,end,width,"paper",.38*k)
            offset=(side*width*.26,.22*k,0)
            aa=tuple(a[i]+offset[i] for i in range(3));bb=tuple(end[i]+offset[i] for i in range(3))
            s.beam("raised_fold_ridge",distal,aa,bb,.13*k,"crease")
            s.beam("gold_paper_edge",distal,a,end,.1*k,"gold")
            for q in range(2+s.age):
                t=.72+q*.055
                pos=tuple(a[i]+(end[i]-a[i])*t for i in range(3))
                s.box("wing_ink_brushstroke",distal,pos,(.2*k,.15*k,(.65+q*.12)*k),"ink",(0,side*25,0))
    for side in (-1,1):
        tail=s.bone("tail" if side<0 else "tail_r","body",(side*.8*k,by+.4*k,3*k))
        for j in range(3+s.age):
            a=(side*.8*k,by+.4*k,3*k);end=(side*(1.8+j*.45)*k,by+(2+j*.45)*k,(8.5+s.age*1.5)*k)
            s.beam("folded_tail_ribbon",tail,a,end,1.2*k,"paper",.4*k)
            s.beam("tail_gold_crease",tail,(a[0],a[1]+.22*k,a[2]),(end[0],end[1]+.22*k,end[2]),.13*k,"gold")
            s.box("tail_seal",tail,end,(.75*k,.35*k,.9*k),"red",(0,side*18,0))
    return _ground(s,True)


def _chimera(stage):
    s=_new(stage,fur="#E5E2D8",cream="#FFF1DA",mane="#AAB8BE",opal="#B5DED3",pink="#DEBDD9",violet="#A5A9D6",horn="#D9C9AA",eye="#71B9AA",blush="#E4B6CA")
    k,h=s.scale,s.head_scale;by=_quad(s,8.5,7.5,12,4.8,"fur","cream",True)
    hy,hz=by+3.6*h,-5.7*k
    _head(s,(0,hy,hz),(7.4,7.1,6.9));_ears(s,hy,hz,2.1,2.7,inner="pink")
    for side in (-1,1):
        for j in range(4+s.age):
            a=-.55+j*.3
            s.box("opal_cheek_mane","head",(side*(3.3+math.sin(a)*.6)*h,hy+(1.7-j*1.0)*h,hz+1.3*h),(2.0*h,2.5*h,3.6*h),"mane",(0,side*15,side*(-25+j*8)))
    # A little goat head leans over the right shoulder, with its own neck/pivot.
    goat=s.bone("head_goat","body",(3.0*k,by+2.5*k,1*k))
    gy,gz=by+(5.0+.5*s.age)*k,.4*k;gx=3.3*k;gh=(.76,.88,1.0)[s.age]
    _oval(s,"goat_secondary_skull",goat,(gx,gy,gz),(4.5*gh,5.3*gh,4.4*gh),"cream",5)
    s.mass("goat_muzzle",goat,(gx,gy-1.2*gh,gz-2.0*gh),(3.1*gh,2*gh,2.8*gh),"mane")
    for side in (-1,1):
        s.box("goat_eye",goat,(gx+side*1.1*gh,gy+.5*gh,gz-2.04*gh),(.75*gh,.95*gh,.2),"pupil")
        s.box("goat_eye_catchlight",goat,(gx+side*1.1*gh-.13*gh,gy+.7*gh,gz-2.17*gh),(.26*gh,.26*gh,.08),"shine")
        s.box("goat_leaf_ear",goat,(gx+side*2.4*gh,gy+.9*gh,gz+.2*gh),(2*gh,.7*gh,1.4*gh),"pink",(0,0,side*20))
        horn=s.bone("goat_horn_l" if side<0 else "goat_horn_r",goat,(gx+side*1.5*gh,gy+1.8*gh,gz+.2*gh))
        # Rolled ram horns become a full open spiral in adults.
        pts=[]
        for j in range(5+s.age):
            t=j/(4+s.age);ang=-1.0+t*(2.5+s.age*.5);r=(1.4+.4*s.age)*gh*(1-.4*t)
            pts.append((gx+side*(1.4+r*.65)*gh,gy+2.1*gh+math.cos(ang)*r,gz+.6*gh+math.sin(ang)*r))
        _path(s,"rolled_goat_horn",horn,pts,[max(.23,.72-j*.075)*gh for j in range(len(pts)-1)],"horn")
    # Small opal wings sit behind the goat neck and never obscure either face.
    for side in (-1,1):
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.8*k,by+1.8*k,2.3*k))
        wrist=(side*(5.4+s.age)*k,by+(5+s.age*.5)*k,3.8*k)
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",b,wrist)
        s.beam("opal_wing_arm",b,(side*2.8*k,by+1.8*k,2.3*k),wrist,1.2*k,"horn")
        for j in range(4+s.age):
            _feather(s,distal,wrist,(side*(7.7+s.age*1.7+j*.45)*k,by+(3.3-j*.42)*k,(5.5+j*.8)*k),1.7*k,("opal","pink","violet")[j%3],"cream")
    points=[(0,by,5*k),(0,by+.7*k,8.5*k),(-2*k,by+3.1*k,10.8*k),(-3.4*k,by+6.0*k,10*k)]
    tip=_tail(s,points,[1.7*k,1.4*k,1.1*k],"opal")
    snake=s.bone("head_snake",tip,points[-1])
    s.ball("snake_tail_head",snake,(-3.4*k,by+6.4*k,9.4*k),(2.5*k,2.4*k,3.6*k),"violet")
    for side in (-1,1):
        s.box("snake_eye",snake,((-3.4+side*.75)*k,by+6.7*k,7.92*k),(.42*k,.6*k,.2),"pupil")
        s.box("snake_eye_shine",snake,((-3.5+side*.75)*k,by+6.86*k,7.79*k),(.15*k,.15*k,.08),"shine")
    s.gem("opal_brow","head",0,hy+2*h,hz-3.1*h,.76*h,"opal","horn")
    return _ground(s)


def _moth_dragon(stage):
    s=_new(stage,fur="#77718F",cream="#D6C6CF",wing="#8C7EAB",deep="#56506F",pink="#D3A7C8",gold="#D3BF8F",rune="#C0DCE1",eye="#CFBCE7",blush="#D9AAC6")
    k,h=s.scale,s.head_scale;by=9*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"plush_dragon_torso","body",(0,by,.6*k),(7.2*k,9.6*k,8.4*k),"fur",6)
    _oval(s,"velvet_belly","body",(0,by-1*k,-2.6*k),(5.3*k,6.8*k,2.7*k),"cream",5)
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.5*k,by-2.6*k,2*k))
        knee=(side*3.0*k,by-4*k,1.4*k);foot=(side*2.9*k,by-6*k,-.3*k)
        lower=s.bone("leg_l_lower" if side<0 else "leg_r_lower",leg,knee)
        s.mass("dragon_haunch",leg,(side*2.7*k,by-3.4*k,1.9*k),(3.1*k,4*k,3.6*k),"fur")
        s.beam("dragon_hind_shin",lower,knee,foot,1.3*k,"deep")
        s.mass("soft_hind_foot",lower,(side*2.9*k,by-6*k,-.7*k),(2.6*k,1.5*k,3.6*k),"cream")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*2.6*k,by+1.6*k,-2*k))
        s.beam("short_dragon_arm",arm,(side*2.6*k,by+1.6*k,-2*k),(side*3.1*k,by-1*k,-3.1*k),1.6*k,"fur")
        s.ball("dragon_mitten",arm,(side*3.1*k,by-1*k,-3.1*k),(2.0*k,2.0*k,1.8*k),"cream")
    hy,hz=by+5.5*h,-3.6*k
    _head(s,(0,hy,hz),(7.3,6.4,6.7),muzzle="cream")
    for side in (-1,1):
        s.box("velvet_cheek_tuft","head",(side*3.5*h,hy-.3*h,hz+.4*h),(2.4*h,3.1*h,2.8*h),"cream",(0,side*15,side*-25))
        antenna=s.bone("antenna_l" if side<0 else "antenna_r","head",(side*1.8*h,hy+2.5*h,hz))
        pts=[(side*1.8*h,hy+2.5*h,hz),(side*2.6*h,hy+(5.3+.4*s.age)*h,hz+.5*h),(side*4.2*h,hy+(6.3+.6*s.age)*h,hz+.2*h)]
        _path(s,"moth_antenna_stem",antenna,pts,[.4*h,.3*h],"gold")
        for j in range(3+s.age):
            t=(j+.5)/(3+s.age)
            p=tuple(pts[1][i]+(pts[2][i]-pts[1][i])*t for i in range(3))
            s.beam("antenna_comb",antenna,p,(p[0]+side*.8*h,p[1]+.7*h,p[2]),.2*h,"cream")
        for lowerwing in (False,True):
            name=("wing_lower" if lowerwing else "wing")+("_l" if side<0 else "_r")
            root=(side*2.6*k,by+(1.5 if lowerwing else 3)*k,(2 if lowerwing else .8)*k)
            s.bone(name,"body",root)
            span=(6.5+2.4*s.age)*( .83 if lowerwing else 1)*k
            tip=(side*(2.6*k+span),by+(1.5 if lowerwing else 6.5)*k,(6.5 if lowerwing else 2.3)*k)
            mid=((root[0]+tip[0])*.5,(root[1]+tip[1])*.5,(root[2]+tip[2])*.5)
            distal=s.bone(name+"_tip",name,mid)
            # Rounded, overlapping fan lobes with full thickness and a raised vein.
            for j in range(4+s.age):
                a=(root[0]+side*j*.25*k,root[1]-.12*j*k,root[2]+j*.28*k)
                b=(tip[0]-side*j*.35*k,tip[1]-(j*.7 if not lowerwing else j*.45)*k,tip[2]+j*.9*k)
                _feather(s,distal,a,b,(2.6+.15*s.age)*k,"wing" if j%2 else "deep","pink")
            # Bold eyespots use a restrained four-colour concentric panel.
            eyex=root[0]+side*span*.72;eyey=root[1]+(tip[1]-root[1])*.53;eyez=root[2]+(tip[2]-root[2])*.6+1.1*k
            # Eyespot faces towards -Z, as do the broad moth wing membranes.
            s.disc("wing_eye_outer",distal,(eyex,eyey,eyez-1.0*k),2.6*k,2.9*k,.3*k,"gold")
            s.disc("wing_eye_ring",distal,(eyex,eyey,eyez-1.22*k),1.9*k,2.15*k,.3*k,"pink")
            s.disc("wing_eye_pupil",distal,(eyex,eyey,eyez-1.43*k),1.1*k,1.4*k,.28*k,"deep")
            s.box("wing_eye_star",distal,(eyex-.2*k,eyey+.25*k,eyez-1.63*k),(.45*k,.45*k,.14),"rune",(0,0,45))
    points=[(0,by-1*k,3.5*k),(0,by-.8*k,7*k),(1.2*k,by+.5*k,10*k),(2.1*k,by+2*k,11*k)]
    tip=_tail(s,points,[2.7*k,1.9*k,1*k],"fur")
    s.crescent("tail_moon","tail_2",(2.1*k,by+2.7*k,11*k),(1.6+.25*s.age)*k,.5*k,.6*k,"gold",segments=6,opening=40)
    for j in range(3+s.age):
        s.box("plush_collar_lock","body",((j-(2+s.age)/2)*1.1*k,by+3.3*k,-3.2*k),(1.7*k,2.6*k,1.8*k),"cream",(0,0,(j-(2+s.age)/2)*12))
    return _ground(s,True)


def _capricorn(stage):
    s=_new(stage,fur="#ABD3B6",cream="#E8E7CB",leaf="#559780",deep="#447D77",wood="#998567",fin="#7CB9AC",rune="#BDDDB7",eye="#D1BD7B",blush="#DCBEC2")
    k,h=s.scale,s.head_scale;by=10*k;s.bones["body"]=("root",[0,by,0])
    _oval(s,"caprine_chest","body",(0,by,-1*k),(7.8*k,8.3*k,9.3*k),"fur",6)
    _oval(s,"seafoam_chest","body",(0,by-1*k,-4*k),(5.4*k,6.1*k,3.1*k),"cream",5)
    for side in (-1,1):
        b=s.bone("leg_fl" if side<0 else "leg_fr","body",(side*2.5*k,by-1*k,-3*k))
        joint=(side*2.8*k,by-4*k,-3.1*k);lower=s.bone("leg_fl_lower" if side<0 else "leg_fr_lower",b,joint)
        s.beam("front_goat_upper_leg",b,(side*2.5*k,by-1*k,-3*k),joint,1.8*k,"fur")
        s.ball("goat_knee",lower,joint,(1.75*k,1.75*k,1.75*k),"cream")
        s.beam("front_goat_shin",lower,joint,(side*2.9*k,by-6*k,-3.8*k),1.2*k,"cream")
        for toe in (-1,1):s.mass("cloven_hoof",lower,((side*2.9+toe*.45)*k,by-6.5*k,-4.0*k),(.78*k,1.5*k,2.1*k),"wood")
    hy,hz=by+4.6*h,-5.4*k
    _head(s,(0,hy,hz),(6.8,7,6.8),muzzle="cream",long=1.1)
    _ears(s,hy,hz,2.4,2.9,outer="leaf",inner="cream")
    for side in (-1,1):
        horn=s.bone("horn_l" if side<0 else "horn_r","head",(side*1.8*h,hy+2.5*h,hz+.4*h))
        length=(2.8,4.5,6.5)[s.age]
        pts=[(side*1.8*h,hy+2.5*h,hz+.4*h),(side*2.3*h,hy+3.1*h+length*.55,hz+1.3*h),(side*3*h,hy+3.1*h+length,hz+3.0*h),(side*3.2*h,hy+3.1*h+length*.94,hz+4.7*h)]
        _path(s,"branching_wood_horn",horn,pts,[1.0*h,.72*h,.42*h],"wood")
        for j in range(1+s.age):
            p=pts[min(2,j+1)]
            q=(p[0]+side*(1.7+j*.2)*h,p[1]+.65*h,p[2]-.8*h)
            s.beam("living_horn_branch",horn,p,q,.42*h,"wood")
            s.box("horn_sprig_leaf",horn,q,(1.7*h,.35*h,.85*h),"leaf",(0,side*25,side*18))
    # The hindquarters become a fish tail: a deep, curved, articulated silhouette.
    points=[(0,by,2.2*k),(0,by-1.1*k,6*k),(0,by-3.6*k,9*k),(1.2*k,by-4.5*k,12*k),(3*k,by-3.2*k,14.2*k)]
    tip=_tail(s,points,[6.5*k,4.8*k,3.2*k,1.8*k],["fur","fin","deep","fin"])
    for j in range(3+s.age):
        t=j/(3+s.age);z=(3.3+t*8)*k;y=by-(.5+t*4)*k
        bone="tail" if j<2 else "tail_1" if j<3 else "tail_2"
        for side in (-1,1):
            s.box("overlap_leaf_scale",bone,(side*(2.5-t*1.6)*k,y,z),(1.8*k,1.6*k,2.0*k),"leaf" if j%2 else "fin",(0,side*15,side*-16))
    for side in (-1,1):
        b=s.bone("tail_fin_l" if side<0 else "tail_fin_r",tip,points[-1])
        for j in range(3+s.age):
            _feather(s,b,points[-1],((3+side*(3.0+j*.6))*k,by+(-1+j*.45)*k,(15+j*.6)*k),1.6*k,"leaf" if j%2 else "fin","rune")
        b=s.bone("wing_l" if side<0 else "wing_r","body",(side*2.6*k,by,2.0*k))
        for j in range(3+s.age):
            _feather(s,b,(side*2.6*k,by,2*k),(side*(5.4+j*.5)*k,by+(.6-j*.7)*k,(4+j*.8)*k),1.5*k,"leaf","rune")
        for j in range(3+s.age):
            s.box("seaweed_mane_lock","body",(side*1.4*k,by+(3.5-j*1.35)*k,-4.0*k),(1.4*k,2.6*k,1.8*k),"leaf" if j%2 else "fin",(10,side*14,side*15))
    s.mass("goat_chin_beard","head",(0,hy-3.3*h,hz-1.5*h),(2.4*h,3*h,2.2*h),"cream")
    return _ground(s,True)


_BUILDERS = {
    "prism_kirin": _kirin,
    "clockwork_red_panda": _panda,
    "nebula_whale": _whale,
    "ember_salamander": _salamander,
    "moonstone_sphinx": _sphinx,
    "thunder_quetzal": _quetzal,
    "sakura_baku": _baku,
    "obsidian_basilisk": _basilisk,
    "glacier_narwhal": _narwhal,
    "lotus_mantis": _mantis,
    "rune_armadillo": _armadillo,
    "astral_seahorse": _seahorse,
    "sunforge_lion": _lion,
    "paper_crane": _crane,
    "opal_chimera": _chimera,
    "twilight_moth_dragon": _moth_dragon,
    "verdant_capricorn": _capricorn,
}
SPECIES = tuple(_BUILDERS)


def build(species, stage):
    """Return the standard editable articulated sculpture contract."""
    try:
        builder = _BUILDERS[species]
    except KeyError as exc:
        raise ValueError(f"Unknown rare expansion pet species: {species}") from exc
    return builder(stage)
