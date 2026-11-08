"""Validate and render the exact common-B authored cuboids without game assets."""
import itertools
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageColor, ImageDraw, ImageFont
import pet_expansion_common_b_sculpt as models


def vertices(part):
    lo=np.array(part["from"],float);size=np.array(part["size"],float)
    center=np.array(part.get("origin",lo+size/2),float)
    out=np.array([lo+size*np.array(v) for v in itertools.product((0,1),repeat=3)])
    out-=center
    for axis,degrees in enumerate(part.get("rotation",[0,0,0])):
        co,si=math.cos(math.radians(degrees)),math.sin(math.radians(degrees))
        i,j=((1,2),(2,0),(0,1))[axis]
        a,b=out[:,i].copy(),out[:,j].copy()
        out[:,i],out[:,j]=a*co-b*si,a*si+b*co
    return out+center


FACES=((0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3))


def projected(v,yaw=27,elev=18):
    a,b=math.radians(yaw),math.radians(elev)
    xx=v[:,0]*math.cos(a)+v[:,2]*math.sin(a)
    zz=-v[:,0]*math.sin(a)+v[:,2]*math.cos(a)
    yy=v[:,1]*math.cos(b)-zz*math.sin(b)
    dd=zz*math.cos(b)+v[:,1]*math.sin(b)
    return np.column_stack((xx,yy,dd))


def render(data,size=(360,330),bounds=None,yaw=27):
    ss=2;img=Image.new("RGB",(size[0]*ss,size[1]*ss),"#F2EEE5");draw=ImageDraw.Draw(img)
    parts=[(p,vertices(p)) for p in data["parts"]]
    allp=projected(np.concatenate([v for _,v in parts]),yaw)
    lo,hi=(allp.min(0),allp.max(0)) if bounds is None else bounds
    factor=min((size[0]-30)/(hi[0]-lo[0]),(size[1]-32)/(hi[1]-lo[1]))*ss
    offset=np.array([size[0]*ss/2-(lo[0]+hi[0])/2*factor,size[1]*ss-17*ss+lo[1]*factor])
    faces=[]
    light=np.array([-.4,.85,-.55]);light/=np.linalg.norm(light)
    for part,world in parts:
        pp=projected(world,yaw)
        color=np.array(ImageColor.getrgb(data["palette"][part["material"]]),float)
        for f in FACES:
            poly=world[list(f)];normal=np.cross(poly[1]-poly[0],poly[2]-poly[0]);normal/=max(1e-6,np.linalg.norm(normal))
            shade=.71+.29*max(0,float(np.dot(normal,light)))
            pts=[tuple(np.array([pp[i,0],-pp[i,1]])*factor+offset) for i in f]
            fill=tuple(int(c) for c in np.clip(color*shade,0,255))
            faces.append((np.mean(pp[list(f),2]),pts,fill))
    for _,pts,color in sorted(faces,reverse=True,key=lambda x:x[0]):
        draw.polygon(pts,fill=color)
    return img.resize(size,Image.Resampling.LANCZOS)


def main():
    out=Path(".tmp/common_b_expansion_previews");out.mkdir(parents=True,exist_ok=True)
    allmodels={};report=[];hashes=set()
    for species in models.SPECIES:
        row={"id":species,"forms":[]}
        for stage in ("baby","medium","adult"):
            data=models.build(species,stage);allmodels[(species,stage)]=data
            for p in data["parts"]:
                assert all(math.isfinite(v) for v in p["from"]+p["size"]+p.get("rotation",[])),(species,stage,"finite")
                assert min(p["size"])>0,(species,stage,"positive")
                assert p["material"] in data["palette"],(species,stage,"material",p["material"])
                assert p["bone"] in data["bones"],(species,stage,"bone")
            for name,(parent,pivot) in data["bones"].items():
                assert parent is None or parent in data["bones"],(species,stage,"parent",name)
                assert len(pivot)==3 and all(math.isfinite(v) for v in pivot),(species,stage,"pivot")
            verts=np.concatenate([vertices(p) for p in data["parts"]]);lo,hi=verts.min(0),verts.max(0)
            assert abs(lo[1])<.001,(species,stage,"ground",lo[1])
            fp=json.dumps(data,sort_keys=True);assert fp not in hashes;(hashes.add(fp))
            row["forms"].append({"stage":stage,"parts":len(data["parts"]),"bones":len(data["bones"]),
                                 "min":[round(v,3) for v in lo],"max":[round(v,3) for v in hi]})
        report.append(row)
    (out/"common_b_expansion_validation.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",17)
    adult=Image.new("RGB",(1600,1850),"#E4E1D7");draw=ImageDraw.Draw(adult)
    growth=Image.new("RGB",(1800,2050),"#E4E1D7");gd=ImageDraw.Draw(growth)
    for index,species in enumerate(models.SPECIES):
        col,row=index%4,index//4
        image=render(allmodels[(species,"adult")],(390,325))
        adult.paste(image,(col*400+5,row*370+35));draw.text((col*400+12,row*370+10),species,font=font,fill="#39433E")
        gd.text((col*450+12,row*410+10),species,font=font,fill="#39433E")
        ds=allmodels[(species,"adult")]
        points=projected(np.concatenate([vertices(p) for p in ds["parts"]]))
        bounds=(points.min(0),points.max(0))
        for age,stage in enumerate(("baby","medium","adult")):
            image=render(allmodels[(species,stage)],(146,350),bounds)
            growth.paste(image,(col*450+4+age*148,row*410+36))
            gd.text((col*450+12+age*148,row*410+387),stage,font=font,fill="#525C50")
    adult.save(out/"common_b_expansion_adults.png")
    growth.save(out/"common_b_expansion_growth.png")
    for species in ("seashell_crab","kelp_turtle","tangerine_gecko","papaya_toucan","pearl_oyster","ribbon_lemur"):
        render(allmodels[(species,"adult")],(800,760)).save(out/("common_b_expansion_"+species+".png"))
    print(json.dumps({"validated_forms":60,"adult_parts":{r["id"]:r["forms"][-1]["parts"] for r in report},"preview_directory":str(out)},indent=2))


if __name__=="__main__":main()
