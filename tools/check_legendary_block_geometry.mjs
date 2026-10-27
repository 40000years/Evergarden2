// Evaluate the actual shipped client animation expressions against captured Java display geometry.
// This checks the model's vertices between keyframes, beyond merely comparing packet metadata.
import fs from 'node:fs';
import path from 'node:path';

const [assets, capturePath, reportPath, previewPath] = process.argv.slice(2);
if (!reportPath) throw new Error('Usage: node check_legendary_block_geometry.mjs <Bedrock assets> <packet capture> <report.json>');
const read = name => JSON.parse(fs.readFileSync(path.join(assets,name),'utf8'));
const entity = read('entity/legendary_block.entity.json')['minecraft:client_entity'].description;
const animation = read('animations/legendary_block.animation.json').animations[entity.animations.pose];
const geometry = read('models/entity/legendary_block.geo.json')['minecraft:geometry'][0];
const materials=read('materials/legendary_blocks.material').materials;
for(const name of ['advance_magic_legendary_blocks:entity_alphatest','advance_magic_legendary_blocks_blend:entity_alphablend'])
    if(!materials[name]?.['+defines']?.includes('USE_UV_ANIM'))throw new Error('Block texture atlas requires the UV animation shader define: '+name);
const frame = new Function('q','v','math',entity.scripts.pre_animation.join('\n'));
const initialize = new Function('q','v','math',entity.scripts.initialize.join('\n'));
const evaluate = expression => typeof expression === 'number' ? ()=>expression : new Function('q','v','math','return ('+expression+');');
const bone = animation.bones.root;
const position = bone.position.map(evaluate),rotation = bone.rotation.map(evaluate),scale = evaluate(bone.scale);
const math = {
    clamp:(x,a,b)=>Math.max(a,Math.min(b,x)),lerp:(a,b,t)=>a+(b-a)*t,
    acos:x=>Math.acos(x)*180/Math.PI,asin:x=>Math.asin(x)*180/Math.PI,
    atan2:(y,x)=>Math.atan2(y,x)*180/Math.PI,sin:x=>Math.sin(x*Math.PI/180),sqrt:Math.sqrt,abs:Math.abs
};
const rot = (axis,degrees) => {
    const s=Math.sin(degrees*Math.PI/180),c=Math.cos(degrees*Math.PI/180);
    return axis===0 ? [[1,0,0],[0,c,-s],[0,s,c]] : axis===1 ? [[c,0,s],[0,1,0],[-s,0,c]] : [[c,-s,0],[s,c,0],[0,0,1]];
};
const mul = (m,v) => m.map(row=>row.reduce((sum,x,i)=>sum+x*v[i],0));
const add = (a,b) => a.map((x,i)=>x+b[i]);
const mirror = v => [-v[0],v[1],v[2]];
// Bedrock places bone space in the world as (x, y, -z) for an actor at yaw 0 (the convention every
// Evergarden boss and pet rig renders with; see tests/model-engine/check_bedrock_facing.py), then turns it
// by the actor's yaw. The yaw comes from the captured AddEntity packet, never from an assumption here.
function bedrockWorld(v,yaw) {
    const f=[v[0],v[1],-v[2]],a=yaw*Math.PI/180,c=Math.cos(a),s=Math.sin(a);
    return [f[0]*c-f[2]*s,f[1],f[0]*s+f[2]*c];
}
function qrotate(q,v) {
    // Quaternion-vector formula independent of the asset's Euler conversion.
    const [x,y,z,w]=q;
    const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
    const t=cross([x,y,z],v).map(x=>2*x),c=cross([x,y,z],t);
    return v.map((value,i)=>value+w*t[i]+c[i]);
}
function slerp(a,b,t) {
    const norm=q=>{const n=Math.hypot(...q);return q.map(x=>x/n);};
    a=norm(a);b=norm(b);
    let dot=a.reduce((sum,x,i)=>sum+x*b[i],0);
    if(dot<0){b=b.map(x=>-x);dot=-dot;}
    dot=Math.min(1,dot);
    if(dot>.9995)return norm(a.map((x,i)=>x*(1-t)+b[i]*t));
    const angle=Math.acos(dot),s=Math.sin(angle);
    return a.map((x,i)=>(x*Math.sin((1-t)*angle)+b[i]*Math.sin(t*angle))/s);
}
const nativeCorners=[];
for(const cube of geometry.bones[0].cubes)for(let mask=0;mask<8;mask++)nativeCorners.push(cube.origin.map((x,i)=>(x+((mask>>i)&1)*cube.size[i])/16));
const records=JSON.parse(fs.readFileSync(capturePath,'utf8'));
const channels=['x','y','z','qx','qy','qz','qw','size'];
let maxError=0,vertices=0,intermediate=0,rotated=0;
const byCast={};
const previews={};
const previewTicks={meteor_rain:72,tempest_dominion:60,leviathans_wrath:70,absolute_zero:40,titans_judgment:60,abyssal_collapse:60,wrath_of_raijin:80,heavens_descent:40};
for(const precision of ['double','float32'])for(const record of records){
    const properties={'advance_magic:revision':record.revision};
    for(let i=0;i<16;i++)properties['advance_magic:'+(i<8?'from_':'to_')+channels[i%8]]=record.pose[i];
    properties['advance_magic:duration']=record.pose[16];
    const v=new Proxy({}, {set:(obj,key,value)=>{obj[key]=precision==='float32'&&typeof value==='number'?Math.fround(value):value;return true;}});let time=0;
    const q={get life_time(){return time;},property:name=>properties[name]};
    initialize(q,v,math);frame(q,v,math);
    for(const fraction of [0,.25,.5,.75,1]){
        time=record.pose[16]*fraction;frame(q,v,math);
        const t=record.pose[16]===0?1:fraction;
        const angles=rotation.map(fn=>fn(q,v,math)),move=position.map(fn=>fn(q,v,math)/16),size=scale(q,v,math);
        const from=record.pose.slice(0,8),to=record.pose.slice(8,16);
        const quat=slerp(from.slice(3,7),record.java.rot,t);
        const center=add(record.anchor,from.slice(0,3).map((x,i)=>x*(1-t)+(record.java.pos[i]-record.anchor[i])*t));
        const expectedSize=from[7]*(1-t)+record.java.scale[0]*t;
        const javaCorners=[],bedrockCorners=[];
        for(const corner of nativeCorners){
            let p=corner.map(x=>x*size);
            // Bedrock Euler convention as implemented by Blockbench's Bedrock import/export.
            p=mul(rot(0,-angles[0]),p);p=mul(rot(1,angles[1]),p);p=mul(rot(2,-angles[2]),p);
            const native=add(record.anchor,bedrockWorld(add(p,move),record.yaw??0));
            const java=add(center,qrotate(quat,mirror(corner).map(x=>x*expectedSize)));
            javaCorners.push(java);bedrockCorners.push(native);
            const error=Math.hypot(...native.map((x,i)=>x-java[i]));
            if(!Number.isFinite(error)||error>.002)throw new Error(`Cube corner differs from Java by ${error}: ${record.cast}, tick ${record.tick}, fraction ${fraction}; ${JSON.stringify({record,angles,move,size,native,java})}`);
            maxError=Math.max(maxError,error);vertices++;
        }
        if(previewPath&&precision==='double'&&fraction===1&&record.quality==='balanced'&&record.tick===previewTicks[record.cast])
            (previews[record.cast]??=[]).push({material:record.java.model,java:javaCorners,bedrock:bedrockCorners});
        if(fraction>0&&fraction<1)intermediate++;
    }
    if(precision==='double'){
        if(Math.abs(record.java.rot[0])+Math.abs(record.java.rot[2])>.1)rotated++;
        byCast[record.cast]=(byCast[record.cast]??0)+1;
    }
}
if(records.length<10000||Object.keys(byCast).length!==8||rotated<1000)throw new Error('Capture does not cover all eight real Legendary casts and substantial pitch/roll');
const report={result:'PASS',poses:records.length,precisions:['double','float32'],vertices,intermediateSamples:intermediate,tiltedPoses:rotated,maxCornerErrorBlocks:maxError,byCast,
    scope:'Actual bundled Molang expressions and geometry evaluated numerically from real Geyser packet capture; no GPU/client rendering claim.'};
fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
if(previewPath)fs.writeFileSync(previewPath,JSON.stringify(previews));
