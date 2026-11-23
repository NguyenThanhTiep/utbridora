"""Real official-image/config/network/signature-volume smoke test on isolated Linux CI only."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('utb_scanner',ROOT/'scripts/scanner.py')
scanner=importlib.util.module_from_spec(spec);spec.loader.exec_module(scanner)


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true' or sys.platform!='linux':raise ValueError('Run this test on the isolated Linux GitHub runner, never the local Docker daemon')
    project='utb-av-ci-'+uuid.uuid4().hex[:16]
    assert project.startswith('utb-av-ci-')
    result={'status':'FAIL','commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'scope':'Isolated official engine/config/network/definitions smoke; not VPS acceptance or a production signature SLA'}
    command=None;env=None
    with tempfile.TemporaryDirectory(prefix='utb-scanner-ci-') as folder:
        try:
            path=Path(folder)/'.env.synthetic'
            path.write_text('DB_PASSWORD=Synthetic-ci-only\nDB_ROOT_PASSWORD=Synthetic-ci-root-only\nBOOTSTRAP_ADMIN_EMAIL=ci@example.test\nBOOTSTRAP_ADMIN_PASSWORD=Synthetic-ci-admin-only\n',encoding='utf-8')
            _,env=scanner.environment(path)
            command=['docker','compose','--project-name',project,'--env-file',str(path),'-f','compose.local.yaml','-f','compose.scanner.yaml']
            model=json.loads(scanner.run(command+['config','--format','json'],env=env));scope=scanner.validate(model)
            result['image']=scope['image']
            scanner.run(command+['pull','clamav'],env=env,timeout=480)
            scanner.run(command+['up','-d','--no-deps','--wait','--wait-timeout','600','clamav'],env=env,timeout=650)
            container=scanner.run(command+['ps','-q','clamav'],env=env).strip()
            before=json.loads(scanner.run(['docker','inspect',container],env=env))[0]
            if before['HostConfig'].get('PortBindings'):raise ValueError('Unexpected published scanner ports')
            running=scanner.run(['docker','ps','--filter','label=com.docker.compose.project='+project,'--format','{{.Label "com.docker.compose.service"}}'],env=env).splitlines()
            if running!=['clamav']:raise ValueError('Smoke test started an unrelated service')
            result['probe']=scanner.probe(scope['image'],scope['network'],env)
            result['version']=scanner.run(command+['exec','-T','clamav','clamdscan','--version'],env=env).strip()
            if not result['version'].startswith('ClamAV 1.4.7/'):raise ValueError('Expected reviewed current LTS patch 1.4.7; refresh the image lock before release')
            scanner.run(command+['exec','-T','clamav','sh','-ec',"printf 'SYNTHETIC_PERSISTENCE' > /var/lib/clamav/.utb-ci-persistence"],env=env)
            volume=next(v['Name'] for v in before['Mounts'] if v['Destination']=='/var/lib/clamav')
            scanner.run(command+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','300','clamav'],env=env,timeout=350)
            after=json.loads(scanner.run(['docker','inspect',scanner.run(command+['ps','-q','clamav'],env=env).strip()],env=env))[0]
            if after['Id']==before['Id'] or next(v['Name'] for v in after['Mounts'] if v['Destination']=='/var/lib/clamav')!=volume:raise ValueError('Signature volume identity changed')
            if scanner.run(command+['exec','-T','clamav','cat','/var/lib/clamav/.utb-ci-persistence'],env=env)!='SYNTHETIC_PERSISTENCE':raise ValueError('Persistence marker lost')
            result['afterRecreateProbe']=scanner.probe(scope['image'],scope['network'],env)
            result.update(status='PASS',definitionsPersisted=True,noPublishedPorts=True,unrelatedServicesStarted=False)
        except Exception as error:
            result['failureClass']=type(error).__name__
            if command:
                logs=subprocess.run(command+['logs','--no-color','clamav'],cwd=ROOT,env=env,capture_output=True,timeout=20)
                target=ROOT/'target/scanner-deployment.log';target.parent.mkdir(exist_ok=True)
                target.write_bytes(logs.stdout+logs.stderr)
            raise
        finally:
            if command:
                # Only resources of the random, exclusively owned CI project are retired.
                cleanup=subprocess.run(command+['down','--volumes','--remove-orphans'],cwd=ROOT,env=env,capture_output=True,timeout=90)
                result['cleanupPassed']=cleanup.returncode==0
            target=ROOT/'target/scanner-deployment.json';target.parent.mkdir(exist_ok=True)
            target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))
    if not result.get('cleanupPassed'):raise RuntimeError('CI-only scanner cleanup failed')


if __name__=='__main__':main()
