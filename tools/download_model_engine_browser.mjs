// Download the public trial through a clean headless Chromium profile (no user cookies).
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
const root=path.resolve('output/model-engine-trial-20261014');
const profile=path.join(root,'download-browser');
const downloads=path.join(root,'browser-downloads');
fs.mkdirSync(downloads,{recursive:true});
const exe='C:/Program Files/Google/Chrome/Application/chrome.exe';
const browser=spawn(exe,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
    '--disable-extensions','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
let ws;
try{
    const portFile=path.join(profile,'DevToolsActivePort');
    for(let i=0;i<100&&!fs.existsSync(portFile);i++)await new Promise(r=>setTimeout(r,100));
    const [port,endpoint]=fs.readFileSync(portFile,'utf8').trim().split(/\r?\n/);
    ws=new WebSocket('ws://127.0.0.1:'+port+endpoint);
    await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
    let id=0;const pending=new Map();let complete;const finished=new Promise(resolve=>complete=resolve);
    ws.onmessage=e=>{
        const msg=JSON.parse(e.data);
        if(msg.id){const callback=pending.get(msg.id);if(callback){pending.delete(msg.id);msg.error?callback.reject(msg.error):callback.resolve(msg.result);}}
        else if(msg.method==='Browser.downloadProgress'){
            if(msg.params.state==='completed')complete(msg.params);
            else if(msg.params.state==='canceled')complete({error:'Download canceled'});
        }
    };
    const send=(method,params={},sessionId)=>new Promise((resolve,reject)=>{const call=++id;pending.set(call,{resolve,reject});ws.send(JSON.stringify({id:call,method,params,...(sessionId?{sessionId}:{})}));});
    await send('Browser.setDownloadBehavior',{behavior:'allow',downloadPath:downloads,eventsEnabled:true});
    const {targetId}=await send('Target.createTarget',{url:'about:blank'});
    const {sessionId}=await send('Target.attachToTarget',{targetId,flatten:true});
    await send('Page.enable',{},sessionId);
    await send('Page.navigate',{url:'https://mythiccraft.io/downloads/modelengine4/free/ModelEngine-4.1.1.jar'},sessionId).catch(()=>{});
    console.log('Official trial download started in headless Chromium.');
    const result=await Promise.race([finished,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Trial download timed out')),90000))]);
    if(result.error)throw new Error(result.error);
    console.log('Downloaded',downloads,result.totalBytes,'bytes');
    await send('Browser.close');
}finally{if(ws)ws.close();browser.kill();}
