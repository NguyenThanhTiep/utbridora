"""Compose scanner preparation/probe. No database, files, worker or backend mutation.

start starts only ClamAV; the documented release command enables the backend.
The probe streams two synthetic byte strings to ClamD, not to application storage.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
PIN='clamav/clamav:1.4@sha256:ac15046e610610347c459afabea6683a52cd041c7a10adf5f050ebb05525d62d'
SCAN={'FILE_SCAN_ENABLED':'true','FILE_SCAN_HOST':'clamav','FILE_SCAN_PORT':'3310'}
BASES={'compose.local.yaml','compose.staging.yaml'}


def run(args, *, env=None, data=None, timeout=60):
    result=subprocess.run(args,cwd=ROOT,env=env,input=data,capture_output=True,timeout=timeout)
    if result.returncode:
        # Compose errors/config output can contain secrets. Report command class only.
        raise RuntimeError('Docker command failed (exit '+str(result.returncode)+'); inspect private operator logs')
    return result.stdout.decode('utf-8')


def environment(env_file):
    """The explicitly selected key=value file wins over stale terminal values."""
    path=Path(env_file).resolve()
    if not path.is_file():raise ValueError('Explicit deployment environment file is missing')
    if path.name=='.env.local-demo':raise ValueError('Use the normal deployment environment file')
    values={}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip() or line.lstrip().startswith('#'):continue
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*=.*',line):raise ValueError('Use plain KEY=value configuration without shell syntax')
        key,value=line.split('=',1)
        if '\x00' in value or value.startswith(('"',"'")) or '$' in value:raise ValueError('Use literal unquoted values without interpolation')
        if key in values:raise ValueError('Duplicate configuration key')
        values[key]=value
    env=dict(os.environ)
    # Remove all interpolation variables used by base and scanner files before applying this file.
    for name in BASES|{'compose.scanner.yaml'}:
        for key in re.findall(r'\$\{([A-Z][A-Z0-9_]*)', (ROOT/name).read_text(encoding='utf-8')):env.pop(key,None)
    env.update(values)
    return path,env


def validate(model):
    services=model['services'];av=services['clamav'];backend=services['backend']
    if av.get('ports') or av.get('privileged') or av.get('network_mode') or av.get('pid'):
        raise ValueError('Scanner must not publish ports or share host namespaces')
    if any(backend.get('environment',{}).get(k)!=v for k,v in SCAN.items()):
        raise ValueError('Backend must use the private ClamD service with its worker enabled')
    if backend.get('depends_on',{}).get('clamav',{}).get('condition')!='service_healthy':raise ValueError('Missing healthy scanner startup gate')
    image=av.get('image','')
    if not re.fullmatch(r'clamav/clamav:[0-9]+\.[0-9]+(?:\.[0-9]+)?@sha256:[a-f0-9]{64}',image):raise ValueError('Pin an official version and manifest digest')
    if set(av.get('networks',{}))!={'file_scan','signature_updates'} or not {'file_scan','default'}.issubset(backend.get('networks',{})):raise ValueError('Unexpected scanner network topology')
    networks=model['networks']
    if not networks['file_scan'].get('internal') or networks['signature_updates'].get('internal'):raise ValueError('Private scan network and separate update egress are required')
    for name,service in services.items():
        if name not in ('backend','clamav') and set(service.get('networks',{}))&{'file_scan','signature_updates'}:raise ValueError('Unrelated service joined the scanner network')
    mounts=av.get('volumes',[])
    expected={'/var/lib/clamav':'volume','/etc/clamav/clamd.conf':'bind','/etc/clamav/freshclam.conf':'bind','/opt/utbridora/clamav-start.sh':'bind'}
    if {v['target']:v['type'] for v in mounts}!=expected:raise ValueError('Unexpected scanner mount; never share application file storage')
    for v in mounts:
        if v['type']=='bind' and (not v.get('read_only') or not Path(v['source']).is_file()):raise ValueError('Scanner configuration must exist and be read-only')
    if av.get('environment',{}).get('CLAMAV_NO_FRESHCLAMD')!='false':raise ValueError('FreshClam must remain enabled')
    if int(av.get('mem_limit',0))<4*1024**3:raise ValueError('Scanner requires its 4 GiB memory budget')
    return dict(image=image,network=networks['file_scan']['name'],signatureVolume=model['volumes']['clamav_signatures']['name'])


def engine(env):
    info=json.loads(run(['docker','info','--format','{{json .}}'],env=env))
    if info.get('OSType')!='linux' or info.get('Architecture') not in ('x86_64','amd64'):raise ValueError('This pinned image is verified for Linux amd64; review a native ARM64 image separately')
    if int(info.get('MemTotal',0))<8_000_000_000 or int(info.get('NCPU',0))<4:raise ValueError('Allocate at least 8 GB RAM and 4 CPUs to the whole Docker host before enabling ClamAV')
    return dict(memoryBytes=info['MemTotal'],cpus=info['NCPU'],architecture=info['Architecture'])


def probe(image,network,env):
    # Same image used only as a tiny clamdscan client, no daemon/DB/user-file mount.
    args=['docker','run','--rm','--interactive','--network',network,'--memory','96m','--cpus','0.5',
        '--read-only','--tmpfs','/tmp:rw,noexec,nosuid,size=8m','--cap-drop','ALL',
        '--security-opt','no-new-privileges:true','--entrypoint','/bin/sh',image,'-ec',
        "printf 'TCPSocket 3310\\nTCPAddr clamav\\n' > /tmp/client.conf; exec clamdscan --config-file=/tmp/client.conf --stream --no-summary -"]
    eicar=base64.b64decode('WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=')
    checks={}
    for name,data,code,marker in [('clean',b'UTBridora synthetic ClamD deployment check\n',0,b'OK'),('eicar',eicar,1,b'FOUND')]:
        result=subprocess.run(args,cwd=ROOT,env=env,input=data,capture_output=True,timeout=75)
        if result.returncode!=code or marker not in result.stdout:raise RuntimeError('Private ClamD '+name+' probe failed; no application data was written')
        checks[name]='PASS'
    return checks


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['check','start','status','probe'])
    parser.add_argument('--base',choices=sorted(BASES),required=True)
    parser.add_argument('--env-file',required=True)
    parser.add_argument('--project',required=True)
    args=parser.parse_args()
    if not re.fullmatch('[a-z0-9][a-z0-9_-]{0,62}',args.project):raise ValueError('Invalid Compose project')
    path,env=environment(args.env_file)
    command=['docker','compose','--project-name',args.project,'--env-file',str(path),'-f',args.base,'-f','compose.scanner.yaml']
    model=json.loads(run(command+['config','--format','json'],env=env))
    scope=validate(model)
    if args.action=='status':
        print(run(command+['ps','clamav'],env=env));return
    resources=engine(env)
    if args.action=='check':print(json.dumps(dict(status='CONFIG_VALID_NOT_DEPLOYED',**scope,engine=resources)));return
    if args.action=='start':
        run(command+['pull','clamav'],env=env,timeout=1200)
        run(command+['up','-d','--no-deps','--wait','--wait-timeout','900','clamav'],env=env,timeout=960)
    checks=probe(scope['image'],scope['network'],env)
    print(json.dumps(dict(status='SCANNER_PROBE_PASS_BACKEND_UNCHANGED',**scope,checks=checks)))


if __name__=='__main__':
    try:main()
    except (ValueError,RuntimeError,subprocess.TimeoutExpired,FileNotFoundError,KeyError) as error:
        print('Scanner check failed: '+str(error),file=sys.stderr);raise SystemExit(1)
