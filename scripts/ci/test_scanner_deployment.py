import copy
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('utb_scanner',ROOT/'scripts/scanner.py')
scanner=importlib.util.module_from_spec(spec);spec.loader.exec_module(scanner)


def model():
    return {'services':{'backend':{'environment':dict(scanner.SCAN),'depends_on':{'clamav':{'condition':'service_healthy'}},'networks':{'default':{},'file_scan':{}}},
        'clamav':{'image':scanner.PIN,'mem_limit':4*1024**3,'environment':{'CLAMAV_NO_FRESHCLAMD':'false'},'networks':{'file_scan':{},'signature_updates':{}},'volumes':[
            {'type':'volume','source':'clamav_signatures','target':'/var/lib/clamav'},
            *[{'type':'bind','source':str(ROOT/'deploy/clamav'/name),'target':'/etc/clamav/'+name,'read_only':True} for name in ('clamd.conf','freshclam.conf')],
            {'type':'bind','source':str(ROOT/'deploy/clamav/start.sh'),'target':'/opt/utbridora/clamav-start.sh','read_only':True}]},
        'frontend':{'networks':{'default':{}}}},'networks':{'file_scan':{'internal':True,'name':'isolated_file_scan'},'signature_updates':{}},'volumes':{'clamav_signatures':{'name':'isolated_clamav_signatures'}}}


class ScannerDeploymentTest(unittest.TestCase):
    def test_private_deployment_model_and_reject_exposure(self):
        self.assertEqual('isolated_file_scan',scanner.validate(model())['network'])
        bad=model();bad['services']['clamav']['ports']=[{'published':'3310','target':3310}]
        with self.assertRaises(ValueError):scanner.validate(bad)
        bad=model();bad['services']['frontend']['networks']['file_scan']={}
        with self.assertRaises(ValueError):scanner.validate(bad)
        bad=model();bad['services']['clamav']['volumes'].append({'type':'volume','source':'local_files','target':'/data/files'})
        with self.assertRaises(ValueError):scanner.validate(bad)

    def test_unpinned_image_missing_update_process_or_worker_gate_is_rejected(self):
        for section,key,value in [('clamav','image','clamav/clamav:latest'),('clamav','mem_limit',1024**3)]:
            bad=model();bad['services'][section][key]=value
            with self.assertRaises(ValueError):scanner.validate(bad)
        bad=model();bad['services']['clamav']['environment']['CLAMAV_NO_FRESHCLAMD']='true'
        with self.assertRaises(ValueError):scanner.validate(bad)
        bad=model();bad['services']['backend']['depends_on']['clamav']['condition']='service_started'
        with self.assertRaises(ValueError):scanner.validate(bad)

    def test_selected_env_wins_and_missing_optional_values_do_not_leak_from_shell(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'.env.synthetic';path.write_text('DB_PASSWORD=Synthetic-only\nLOCAL_HTTP_PORT=18088\n',encoding='utf-8')
            with patch.dict(os.environ,{'DB_PASSWORD':'stale','FILE_SCAN_HOST':'untrusted','CLAMAV_IMAGE':'bad'}):
                _,env=scanner.environment(path)
            self.assertEqual('Synthetic-only',env['DB_PASSWORD']);self.assertNotIn('FILE_SCAN_HOST',env);self.assertNotIn('CLAMAV_IMAGE',env)
            path.write_text('DB_PASSWORD=x\nDB_PASSWORD=y\n',encoding='utf-8')
            with self.assertRaises(ValueError):scanner.environment(path)

    def test_resource_preflight_rejects_undersized_and_unverified_arch(self):
        import json
        for memory,cpus,arch in [(4*1024**3,4,'x86_64'),(8*1024**3,2,'x86_64'),(8*1024**3,4,'aarch64')]:
            with patch.object(scanner,'run',return_value=json.dumps({'OSType':'linux','Architecture':arch,'MemTotal':memory,'NCPU':cpus})):
                with self.assertRaises(ValueError):scanner.engine({})
