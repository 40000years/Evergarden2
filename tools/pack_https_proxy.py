"""Public HTTPS for Minecraft packs using one account-free Cloudflare Quick Tunnel.

Only content-addressed ZIP routes are proxied; neither directories nor arbitrary
local URLs are exposed. Keep this helper running when restarting Minecraft.
"""
from __future__ import annotations
import argparse,datetime,http.client,json,logging,logging.handlers,os,queue,re,signal,subprocess,threading,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

PACK_PATH=re.compile(r'^/(advance-magic|evergarden)/[0-9a-f]{40}\.zip$')
PUBLIC_URL=re.compile(r'https://[a-z0-9-]+\.trycloudflare\.com\b')
FORWARDED_HEADERS=('Content-Type','Content-Length','ETag','Cache-Control','X-Content-Type-Options','Last-Modified')

class PackRouter(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,address,origins):
        self.origins=origins
        super().__init__(address,PackRequest)

class PackRequest(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    server_version='EvergardenPackHTTPS/1.0'
    sys_version=''
    def log_message(self,format,*args):logging.info('HTTP '+format,*args)
    def do_GET(self):self.proxy(False)
    def do_HEAD(self):self.proxy(True)
    def do_POST(self):self.reject(405,'GET, HEAD')
    def do_PUT(self):self.reject(405,'GET, HEAD')
    def do_DELETE(self):self.reject(405,'GET, HEAD')
    def do_OPTIONS(self):self.reject(405,'GET, HEAD')
    def reject(self,status,allow=None):
        self.send_response(status)
        if allow:self.send_header('Allow',allow)
        self.send_header('Content-Length','0');self.send_header('Connection','close');self.end_headers();self.close_connection=True
    def proxy(self,head):
        parsed=urlsplit(self.path)
        match=PACK_PATH.fullmatch(parsed.path)
        if parsed.scheme or parsed.netloc or not match or parsed.query or parsed.fragment:
            self.reject(404);return
        origin=self.server.origins.get(match.group(1))
        if origin is None:self.reject(404);return
        conn=http.client.HTTPConnection(origin[0],origin[1],timeout=60)
        headers={'User-Agent':self.headers.get('User-Agent','EvergardenPackHTTPS'),'Connection':'close'}
        if self.headers.get('If-None-Match'):headers['If-None-Match']=self.headers['If-None-Match']
        sent=False
        try:
            conn.request('HEAD' if head else 'GET',parsed.path,headers=headers)
            response=conn.getresponse()
            # A pack host must never redirect a client to another URL.
            if response.status not in (200,304,404):self.reject(502);return
            if response.status==404:self.reject(404);return
            if response.status==200:
                try:
                    if int(response.getheader('Content-Length'))<0:raise ValueError()
                except (TypeError,ValueError):self.reject(502);return
            self.send_response(response.status)
            for key in FORWARDED_HEADERS:
                value=response.getheader(key)
                if value is not None:self.send_header(key,value)
            if response.status!=304 and response.getheader('Content-Length') is None:
                self.send_header('Content-Length','0')
                self.send_header('Connection','close');self.end_headers();sent=True;self.close_connection=True;return
            self.send_header('Connection','close');self.end_headers();sent=True;self.close_connection=True
            if not head and response.status==200:
                while True:
                    chunk=response.read(128*1024)
                    if not chunk:break
                    self.wfile.write(chunk)
        except (OSError,http.client.HTTPException) as error:
            logging.warning('Pack route %s failed: %s',parsed.path,type(error).__name__)
            if not sent:self.reject(502)
            else:self.close_connection=True
        finally:conn.close()

def atomic_json(path,data):
    staged=path.with_suffix(path.suffix+'.tmp')
    staged.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8');os.replace(staged,path)

def configure_packs(server_dir:Path,url:str,backups:Path):
    changes=[]
    for plugin in ['advance-magic','Evergarden']:
        path=server_dir/'plugins'/plugin/'config.yml'
        raw=path.read_bytes();text=raw.decode('utf8');lines=text.splitlines(keepends=True)
        begin=next(i for i,s in enumerate(lines) if s.rstrip('\r\n')=='resource-pack:')
        end=next((i for i in range(begin+1,len(lines)) if re.match(r'^[A-Za-z][\w-]*:',lines[i])),len(lines))
        host=next(i for i in range(begin+1,end) if lines[i].rstrip('\r\n')=='  host:')
        host_end=next((i for i in range(host+1,end) if re.match(r'^  [A-Za-z][\w-]*:',lines[i])),end)
        replacements=[(begin,end,2,'url',"''"),(begin,end,2,'sha1',"''"),
                      (host+1,host_end,4,'enabled','true'),(host+1,host_end,4,'use-server-port','false'),
                      (host+1,host_end,4,'public-url',"'"+url+"'")]
        # Judge's addon follows the same HTTPS root instead of retaining an old override.
        if plugin=='Evergarden':
            for i,s in enumerate(lines):
                if s.rstrip('\r\n')=='    pack:':
                    pack_end=next((j for j in range(i+1,len(lines)) if re.match(r'^(?:\S|  \S|    \S)',lines[j]) and not lines[j].lstrip().startswith('#')),len(lines))
                    replacements.extend([(i+1,pack_end,6,'url',"''"),(i+1,pack_end,8,'public-url',"''"),(i+1,pack_end,8,'public-host',"''")]);break
        for start,stop,indent,key,value in replacements:
            matches=[i for i in range(start,stop) if re.match(r'^'+' '*indent+re.escape(key)+r':',lines[i])]
            if len(matches)!=1:raise RuntimeError('Expected one '+plugin+' '+key+' at indent '+str(indent))
            i=matches[0];ending='\r\n' if lines[i].endswith('\r\n') else '\n';lines[i]=' '*indent+key+': '+value+ending
        updated=''.join(lines).encode('utf8')
        if updated!=raw:changes.append((path,raw,updated,plugin))
    if changes:
        folder=backups/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f');folder.mkdir(parents=True)
        for path,raw,updated,plugin in changes:
            if path.read_bytes()!=raw:raise RuntimeError('Config changed concurrently: '+str(path))
            (folder/(plugin+'.yml')).write_bytes(raw)
        for path,raw,updated,plugin in changes:
            if path.read_bytes()!=raw:raise RuntimeError('Config changed concurrently: '+str(path))
            staged=path.with_name('config.yml.https.tmp');staged.write_bytes(updated);os.replace(staged,path)
    return len(changes)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--settings',type=Path,required=True);args=parser.parse_args()
    settings_path=args.settings.resolve();home=settings_path.parent;settings=json.loads(settings_path.read_text(encoding='utf8'))
    handler=logging.handlers.RotatingFileHandler(home/'helper.log',maxBytes=2*1024*1024,backupCount=2,encoding='utf8')
    logging.basicConfig(level=logging.INFO,format='%(asctime)s %(message)s',handlers=[handler])
    server=PackRouter(('127.0.0.1',settings.get('router_port',8190)),
                      {'advance-magic':('127.0.0.1',8187),'evergarden':('127.0.0.1',8188)})
    threading.Thread(target=server.serve_forever,daemon=True).start()
    stop=threading.Event()
    for sig in [signal.SIGINT,signal.SIGTERM]:signal.signal(sig,lambda *_:stop.set())
    stop_file=home/'stop.request';stop_file.unlink(missing_ok=True)
    state={'pid':os.getpid(),'status':'starting','url':'','started_at':datetime.datetime.now().isoformat(),'router_port':server.server_port}
    atomic_json(home/'state.json',state)
    empty=home/'cloudflared-empty.yml';empty.write_text('{}\n',encoding='ascii')
    process=None
    try:
        while not stop.is_set() and not stop_file.exists():
            command=[str(home/'cloudflared.exe'),'--config',str(empty),'tunnel','--no-autoupdate',
                     '--protocol','http2','--edge-ip-version','4','--metrics','127.0.0.1:0',
                     '--url',f'http://127.0.0.1:{server.server_port}']
            process=subprocess.Popen(command,cwd=home,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                     text=True,encoding='utf8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            state['cloudflared_pid']=process.pid;atomic_json(home/'state.json',state)
            messages=queue.Queue()
            def read_output(pipe):
                for line in pipe:messages.put(line.rstrip())
            threading.Thread(target=read_output,args=(process.stdout,),daemon=True).start()
            while process.poll() is None and not stop.is_set() and not stop_file.exists():
                try:line=messages.get(timeout=.5)
                except queue.Empty:continue
                logging.info('cloudflared %s',line)
                found=PUBLIC_URL.search(line)
                if found and found.group(0)!=state.get('url'):
                    url=found.group(0)
                    changed=configure_packs(Path(settings['server_dir']),url,home/'backups')
                    state.update(url=url,status='published',config_files_changed=changed,minecraft_restart_required=bool(changed))
                    atomic_json(home/'state.json',state);logging.info('Published HTTPS root %s; changed %s configs',url,changed)
                if 'Registered tunnel connection' in line:
                    state['status']='connected';atomic_json(home/'state.json',state)
            if stop.is_set() or stop_file.exists():break
            logging.warning('cloudflared exited with %s; retrying in 5 seconds',process.returncode)
            state['status']='reconnecting';atomic_json(home/'state.json',state);stop.wait(5)
    except Exception:
        logging.exception('HTTPS helper failed');state['status']='failed';atomic_json(home/'state.json',state)
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        server.shutdown();server.server_close()
        if state.get('status')!='failed':state['status']='stopped'
        atomic_json(home/'state.json',state);logging.info('HTTPS helper stopped')
if __name__=='__main__':main()
