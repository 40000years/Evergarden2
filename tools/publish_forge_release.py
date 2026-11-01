"""Publish the verified Forge 0.5.8 addon without replacing existing GitHub assets."""
from pathlib import Path
import argparse,hashlib,io,json,re,subprocess,urllib.error,urllib.parse,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
REPO='40000years/Evergarden2'
API='https://api.github.com/repos/'+REPO

def sha(data):return hashlib.sha256(data).hexdigest()

def credential():
    result=subprocess.run(['git','credential','fill'],input=f'protocol=https\nhost=github.com\npath={REPO}.git\n\n',capture_output=True,text=True,check=True,timeout=60)
    fields=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if not fields.get('password'):raise RuntimeError('No stored credential for '+REPO)
    return fields['password']

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,request,fp,code,msg,headers,newurl):
        target=super().redirect_request(request,fp,code,msg,headers,newurl)
        if target and urllib.parse.urlsplit(newurl).hostname!=urllib.parse.urlsplit(request.full_url).hostname:
            target.remove_header('Authorization')
        return target

OPENER=urllib.request.build_opener(SafeRedirect())

def request(method,url,secret=None,data=None,kind='application/json',binary=False):
    host=urllib.parse.urlsplit(url).hostname
    if secret and (host not in ('api.github.com','uploads.github.com') or not url.startswith('https://')):
        raise RuntimeError('Credential destination rejected')
    headers={'User-Agent':'evergarden-forge-release','Accept':'application/octet-stream' if binary else 'application/vnd.github+json',
             'X-GitHub-Api-Version':'2022-11-28','Content-Type':kind}
    if secret:headers['Authorization']='Bearer '+secret
    with OPENER.open(urllib.request.Request(url,data=data,method=method,headers=headers),timeout=60) as response:body=response.read()
    return body if binary else json.loads(body) if body else None

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit',required=True)
    parser.add_argument('--source-repo',type=Path,default=ROOT)
    parser.add_argument('--dry-run',action='store_true')
    args=parser.parse_args()
    assert re.fullmatch('[0-9a-f]{40}',args.commit)
    candidate=ROOT/'output/forge-build/evergarden-forge.jar';data=candidate.read_bytes();digest=sha(data)
    reports=[]
    for name,core in [('rates-058-final',False),('rates-058-core-final',True)]:
        receipt=json.loads((ROOT/'output/forge-probe'/('paper-'+name)/'forge-test-receipt.json').read_text())
        assert receipt['result'].startswith('PASS') and receipt['audit'] and receipt['with_core']==core
        assert not core or receipt['with_geyser']
        assert receipt['candidate_sha256']==digest
        for filename,value in receipt['sources'].items():
            local=ROOT/filename
            assert sha(local.read_bytes())==value,'Tested source changed: '+filename
            path=Path(filename).as_posix()
            published=subprocess.run(['git','-C',str(args.source_repo),'show',args.commit+':'+path],capture_output=True,check=True).stdout
            assert published.replace(b'\r\n',b'\n')==local.read_bytes().replace(b'\r\n',b'\n'),'Commit source differs: '+path
        reports.append(receipt['result'])
    baseline=json.loads((ROOT/'output/forge-rate-audit/baseline.json').read_text())
    assert all(sha((ROOT/path).read_bytes())==value for path,value in baseline.items() if path.startswith('dist/') and path!='dist/evergarden-forge.jar')
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        version=re.search(r"version: '([^']+)'",jar.read('plugin.yml').decode()).group(1)
        assert version=='0.5.8-rate-audit'
        files={'evergarden-forge.jar':data,'forge-config.yml':jar.read('config.yml')}
        for entry,name in [('forge-java.zip','forge-java.zip'),('forge-bedrock.mcpack','forge-bedrock.mcpack'),
                           ('geyser-mappings.json','forge-geyser-mappings.json'),('pack-hashes.json','pack-hashes.json')]:
            files[name]=jar.read('resource-packs/'+entry)
    hashes=json.loads(files['pack-hashes.json'])
    assert all(hashlib.sha1(files[name]).hexdigest()==hashes[name] for name in ('forge-java.zip','forge-bedrock.mcpack'))
    with zipfile.ZipFile(io.BytesIO(files['forge-bedrock.mcpack'])) as pack:
        resource_version=json.loads(pack.read('manifest.json'))['header']['version']
    tag='evergarden-forge-v0.5.8';stage=ROOT/'output/github-release-forge-v0.5.8';stage.mkdir(parents=True,exist_ok=True)
    validation=dict(version=version,source_commit=args.commit,source_repository='https://github.com/'+REPO,
        jar_sha256=digest,checks=reports,icons=46,bedrock_resource_version=resource_version,pack_sha1=hashes,
        rates_unchanged_from_057=True,older_releases_preserved=True,
        limitations=['Automated rate simulations and headless Paper tests; long-term balance and connected client rendering require playtests.'])
    files['validation.json']=(json.dumps(validation,indent=2)+'\n').encode()
    files['SHA256SUMS.txt']=''.join(sha(value)+'  '+name+'\n' for name,value in sorted(files.items())).encode()
    notes=(
        '# Evergarden Forge 0.5.8\n\n'
        'Keeps the 0.5.7 rates and closes the placed-ore piston farming loophole. Config upgrades now preserve pack URLs, ports, Geyser options and forge access settings, and retain existing backups.\n\n'
        '- Elite base chance: 5%; raid/trial multiplier: 0.5.\n'
        '- Ordinary hostile mob gems: 1%, purity I, with the existing area-heat/environmental-damage farm guard.\n'
        '- Mining gems: coal 0.5%; copper/lapis/redstone/quartz 0.75%; iron/gold 1.5%; Nether gold 0.25%; diamond/emerald/debris 4%; stone/deepslate 0.005%. Mining purity I/II weights: 85/15.\n'
        '- Overworld equipment stops at iron, Nether/End at diamond; netherite remains disabled by default.\n'
        '- All 46 gem/material icons are bundled for Java and Bedrock. Resource pack 0.5.1 is unchanged, so its existing SHA stays valid.\n\n'
        f'Validation: **{reports[0]}**, **{reports[1]}** (Evergarden + Advance Magic + Geyser). Includes 100% mining-rate regression checks for moved placed ore and config-upgrade checks.\n\n'
        'Install evergarden-forge.jar under the server plugins directory and restart with Geyser. Java receives the addon automatically; Forge installs its Bedrock pack/mapping on the same Geyser host. For a separate Geyser host, copy forge-bedrock.mcpack into packs/ and forge-geyser-mappings.json into custom_mappings/, then restart. forge-config.yml is a reference copy of the defaults.\n\n'
        f'Source: [{args.commit[:12]}](https://github.com/{REPO}/commit/{args.commit}) on workstation-2026-10-27. The binary matches these Forge sources (line endings normalized for comparison).\n\n'
        f'JAR SHA-256: {digest}.\n\n'
        'Older releases and the public packs branch are preserved. Gameplay rates have automated coverage; long-term balance and connected Java/Bedrock client rendering still require playtests.\n')
    (stage/'RELEASE.md').write_text(notes,encoding='utf8')
    for name,value in files.items():(stage/name).write_bytes(value)
    print('Staged',tag,'assets:',{name:len(value) for name,value in files.items()},flush=True)
    if args.dry_run:return
    secret=credential()
    remote=request('GET',API+'/commits/'+args.commit,secret);assert remote['sha']==args.commit
    try:release=request('GET',API+'/releases/tags/'+tag,secret)
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
        drafts=[r for r in request('GET',API+'/releases?per_page=100',secret) if r['tag_name']==tag and r['draft']]
        assert len(drafts)<=1
        release=drafts[0] if drafts else request('POST',API+'/releases',secret,json.dumps(dict(
            tag_name=tag,target_commitish=args.commit,name='Evergarden Forge 0.5.8 - verified rates and farm fixes',
            body=notes,draft=True,prerelease=False,make_latest='false')).encode())
    assert release['target_commitish'] in (args.commit,'workstation-2026-10-27')
    upload=release['upload_url'].split('{')[0];present={a['name']:a for a in release['assets']}
    for name,value in files.items():
        if name in present:
            assert present[name]['size']==len(value),'Refusing to replace existing asset '+name
            continue
        request('POST',upload+'?name='+urllib.parse.quote(name),secret,value,'application/octet-stream')
        print('Uploaded',name,flush=True)
    release=request('GET',release['url'],secret);assets={a['name']:a for a in release['assets']}
    for name,value in files.items():
        assert name in assets;asset=assets[name]
        downloaded=request('GET',asset['url'] if release['draft'] else asset['browser_download_url'],
            secret if release['draft'] else None,binary=True)
        assert sha(downloaded)==sha(value),'Uploaded asset mismatch '+name
        print('Verified',name,flush=True)
    if release['draft']:
        release=request('PATCH',release['url'],secret,json.dumps(dict(draft=False,make_latest='false',body=notes)).encode())
    for name,value in files.items():
        downloaded=request('GET',assets[name]['browser_download_url'],binary=True)
        assert sha(downloaded)==sha(value),'Public download mismatch '+name
    assert sha(candidate.read_bytes())==digest
    record=dict(url=release['html_url'],draft=release['draft'],source_commit=args.commit,
                assets={name:dict(bytes=len(value),sha256=sha(value),url=assets[name]['browser_download_url']) for name,value in files.items()})
    (stage/'publish-receipt.json').write_text(json.dumps(record,indent=2)+'\n')
    print('Published and verified:',release['html_url'])

if __name__=='__main__':main()
