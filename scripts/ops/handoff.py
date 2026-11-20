"""Offline synthetic-demo retention and DB/private-storage recovery. No shared-schema mode.

All reports and bundles are private operational artifacts, never repository fixtures.
The caller must stop writers/workers and supply an approved, exclusive scope manifest.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import uuid

MARKER = '.utbridora-demo-scope.json'
SCHEMA = re.compile(r'utbridora_pkg9_[a-f0-9]{16,32}\Z')
UTC = dt.timezone.utc


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def private_json(path, data):
    path = Path(path)
    # Exclusive creation: do not replace existing evidence or follow an output symlink.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write('\n')


def instant(text):
    value = dt.datetime.fromisoformat(text.replace('Z', '+00:00'))
    if value.tzinfo is None:
        raise ValueError('Timezone is required')
    return value.astimezone(UTC)


def safe_root(text):
    path = Path(text)
    if not path.is_absolute() or path.parent == path:
        raise ValueError('Expected a dedicated absolute resource directory')
    for ancestor in [path, *path.parents]:
        if ancestor.is_symlink():
            raise ValueError('Symlink resource paths are forbidden')
    path = path.resolve(strict=True)
    if not path.is_dir():
        raise ValueError('Resource root must exist')
    return path


def inventory(root):
    result = {}
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError('Symlinks in operational resources are forbidden')
        mode = path.stat().st_mode
        if stat.S_ISDIR(mode):
            continue
        if not stat.S_ISREG(mode):
            raise ValueError('Only regular files may be inventoried')
        name = path.relative_to(root).as_posix()
        if name == MARKER:
            continue
        result[name] = {'bytes': path.stat().st_size, 'sha256': digest(path.read_bytes())}
    return result


def config(path):
    value = read(path)
    if value.get('version') != 1 or value.get('exclusiveSynthetic') is not True or value.get('writersStopped') is not True:
        raise ValueError('Exclusive synthetic scope and offline writer attestation required')
    if not SCHEMA.fullmatch(value.get('schema', '')):
        raise ValueError('Shared/default schemas are forbidden; use an isolated utbridora_pkg9_<random> schema')
    uuid.UUID(value['scopeId']); uuid.UUID(value['serverUuid'])
    if not value.get('approvalRef') or len(value['approvalRef']) > 200:
        raise ValueError('Explicit acceptance/retention approval reference required')
    instant(value['acceptedAt'])
    if value.get('host') not in ('127.0.0.1', '::1'):
        raise ValueError('Run on the isolated host using its loopback database connection')
    roots = []
    for item in value['resources']:
        if item['kind'] not in ('private-files', 'backup', 'export'):
            raise ValueError('Unknown resource class')
        root = safe_root(item['path'])
        marker = read(root / MARKER)
        if marker != {'scopeId': value['scopeId'], 'schema': value['schema']}:
            raise ValueError('Resource scope marker mismatch')
        roots.append(root)
    if len(roots) != len(set(roots)) or any(a in b.parents for a in roots for b in roots if a != b):
        raise ValueError('Resource roots must be distinct and must not contain each other')
    if sum(item['kind'] == 'private-files' for item in value['resources']) != 1:
        raise ValueError('Exactly one private-storage root required')
    # Explicit complete inventory attestation includes off-host backups and exports.
    if value.get('allCopiesRegistered') is not True:
        raise ValueError('Register every backup/export copy before planning retirement')
    return value


def connect(c):
    import pymysql
    conn = pymysql.connect(host=c['host'], port=c['port'], user=c['user'],
                           password=os.environ['UTB_OPS_DB_PASSWORD'], database=c['schema'],
                           charset='utf8mb4', autocommit=False,
                           connect_timeout=10, read_timeout=120, write_timeout=120)
    with conn.cursor() as cur:
        cur.execute('SELECT @@server_uuid, DATABASE()')
        if cur.fetchone() != (c['serverUuid'], c['schema']):
            conn.close()
            raise ValueError('Database identity changed')
        cur.execute("SET time_zone='+00:00'")
    return conn


def ident(name):
    if not re.fullmatch(r'[A-Za-z0-9_]+', name):
        raise ValueError('Unexpected SQL identifier')
    return '`' + name + '`'


def tables(conn):
    with conn.cursor() as cur:
        cur.execute("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")
        return sorted(row[0] for row in cur.fetchall())


def database_inventory(conn):
    result = {}
    for table in tables(conn):
        with conn.cursor() as cur:
            cur.execute('SHOW CREATE TABLE ' + ident(table))
            ddl = cur.fetchone()[1]
            cur.execute('SELECT * FROM ' + ident(table))
            rows = []
            for row in cur:
                h = hashlib.sha256()
                for val in row:
                    raw = None if val is None else (val if isinstance(val, bytes) else str(val).encode())
                    h.update((-1 if raw is None else len(raw)).to_bytes(8, 'big', signed=True))
                    if raw is not None:
                        h.update(raw)
                rows.append(h.digest())
            result[table] = {'rows': len(rows), 'sha256': digest(b''.join(sorted(rows))), 'ddlSha256': digest(ddl.encode())}
    return result


def resources(c):
    return {r['path']: inventory(safe_root(r['path'])) for r in c['resources']}


def hold(c, now):
    h = c.get('hold')
    if h is None:
        return False
    if not h.get('approvalRef') or not h.get('reason'):
        raise ValueError('A hold requires its own approved reference and reason')
    return h.get('until') is None or now < instant(h['until'])


def plan(c, now):
    with connect(c) as conn:
        db = database_inventory(conn)
    if not {'users', 'organizations', 'audit_logs', 'flyway_schema_history'} <= db.keys():
        raise ValueError('Canonical database not found')
    return {'version': 1, 'planId': str(uuid.uuid4()), 'configSha256': digest(encoded(c)),
            'scopeId': c['scopeId'], 'serverUuid': c['serverUuid'], 'schema': c['schema'],
            'plannedAt': now.isoformat(), 'dataDue': now >= instant(c['acceptedAt']) + dt.timedelta(days=30),
            'held': hold(c, now), 'auditCutoff': (now - dt.timedelta(days=90)).isoformat(),
            'database': db, 'resources': resources(c),
            'policy': 'R30: retire entire exclusive synthetic dataset; retain UUID tombstones and in-window audit; never disable FKs'}


def deletion_order(conn):
    remaining = set(tables(conn)) - {'users', 'organizations', 'audit_logs', 'flyway_schema_history'}
    with conn.cursor() as cur:
        cur.execute('SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND TABLE_SCHEMA<>DATABASE()')
        if cur.fetchone()[0]:
            raise ValueError('Another schema references this scope; refuse retirement')
        cur.execute('SELECT TABLE_NAME,REFERENCED_TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_SCHEMA '
                    'FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL')
        refs = cur.fetchall()
        if any(schema != conn.db.decode() for _, _, _, schema in refs):
            raise ValueError('Cross-schema foreign key; refuse retirement')
    order = []
    while remaining:
        children = {parent for child, parent, _, _ in refs if child in remaining and parent in remaining and child != parent}
        ready = sorted(remaining - children)
        if not ready:
            raise ValueError('Foreign-key cycle requires a separately reviewed retirement adapter')
        order.extend(ready); remaining.difference_update(ready)
    return order, [(t, col) for t, parent, col, _ in refs if t == parent and t in order]


def retire(c, p, now):
    if p['configSha256'] != digest(encoded(c)) or p['scopeId'] != c['scopeId']:
        raise ValueError('Plan/config mismatch')
    if now < instant(p['plannedAt']) or now - instant(p['plannedAt']) > dt.timedelta(hours=1):
        raise ValueError('Plan expired or clock moved backwards')
    if hold(c, now) or p['held']:
        raise ValueError('Approved hold prevents retirement')
    if not p['dataDue'] or now < instant(c['acceptedAt']) + dt.timedelta(days=30):
        raise ValueError('Acceptance + 30 days has not been reached')
    # The same plan is a resumable command. Its audit row is the transactional commit receipt.
    with connect(c) as conn:
        with conn.cursor() as cur:
            cur.execute('SELECT GET_LOCK(%s,0)', ('pkg9-' + c['scopeId'],))
            if cur.fetchone()[0] != 1:
                raise ValueError('Another retirement is active')
            try:
                cur.execute('SELECT metadata_json FROM audit_logs WHERE id=%s', (p['planId'],))
                receipt = cur.fetchone()
                if receipt:
                    if json.loads(receipt[0]) != {'planSha256': digest(encoded(p))}:
                        raise ValueError('Receipt collision')
                else:
                    if database_inventory(conn) != p['database'] or resources(c) != p['resources']:
                        raise ValueError('Data/resources changed after preview; create a new plan')
                    order, self_refs = deletion_order(conn)
                    for table, col in self_refs:
                        cur.execute('SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND COLUMN_NAME=%s', (table, col))
                        if cur.fetchone()[0] != 'YES':
                            raise ValueError('Non-null self FK requires reviewed adapter')
                        cur.execute('UPDATE ' + ident(table) + ' SET ' + ident(col) + '=NULL')
                    for table in order:
                        cur.execute('DELETE FROM ' + ident(table))
                    # Keep UUIDs referenced by immutable audit; destroy identity/credentials and revoke sessions.
                    cur.execute("UPDATE users SET email=CONCAT('retired-',id,'@invalid'),display_name='Retired synthetic identity',password_hash='!RETIRED_NO_LOGIN!',status='DEACTIVATED',security_version=security_version+1,failed_attempts=0,locked_until=NULL")
                    cur.execute("UPDATE organizations SET name=CONCAT('Retired ',id),status='INACTIVE'")
                    cur.execute('DELETE FROM audit_logs WHERE created_at <= %s', ((now-dt.timedelta(days=90)).replace(tzinfo=None),))
                    cur.execute("INSERT INTO audit_logs(id,action,resource_type,resource_id,result,created_at,source,metadata_json) VALUES(%s,'DEMO_RETENTION_APPLIED','synthetic_scope',%s,'SUCCESS',%s,'OPERATIONS',%s)",
                                (p['planId'], c['scopeId'], now.replace(tzinfo=None), json.dumps({'planSha256': digest(encoded(p))})))
                    conn.commit()
                # File deletion follows committed removal of all bindings/sessions/credentials.
                # Failures remain visible and retry resumes from the same DB receipt.
                for text, entries in p['resources'].items():
                    root = safe_root(text)
                    current = inventory(root)
                    if any(name not in entries or entries[name] != value for name, value in current.items()):
                        raise ValueError('Resource changed during/resuming retirement; investigate without deleting')
                    for name in entries:
                        candidate = root / name
                        # Validate every path against its resolved dedicated root before removing a file.
                        if root not in candidate.resolve().parents:
                            raise ValueError('Retirement path escaped its scope')
                        candidate.unlink(missing_ok=True)
                    for directory in sorted((x for x in root.rglob('*') if x.is_dir()), key=lambda x: len(x.parts), reverse=True):
                        directory.rmdir()
                result = {'status': 'PASS', 'planId': p['planId'], 'planSha256': digest(encoded(p)),
                          'database': database_inventory(conn), 'remainingResources': resources(c),
                          'limits': ['Exclusive synthetic scope only; not selective deletion in mixed/live data',
                                     'All registered copies removed; external unregistered copies cannot be certified']}
                if any(result['remainingResources'].values()):
                    raise ValueError('Resources remain')
                return result
            finally:
                cur.execute('SELECT RELEASE_LOCK(%s)', ('pkg9-' + c['scopeId'],))


def backup(c, dump, output):
    dump = Path(dump)
    if dump.is_symlink() or not dump.is_file() or dump.stat().st_size == 0:
        raise ValueError('Expected a completed private SQL dump')
    out = Path(output)
    if not out.is_absolute() or out.exists():
        raise ValueError('Bundle needs a new absolute path inside a registered backup root')
    parent = safe_root(str(out.parent))
    if not any(r['kind'] == 'backup' and (safe_root(r['path']) == parent or safe_root(r['path']) in parent.parents) for r in c['resources']):
        raise ValueError('Backup destination is not in the registered copy inventory')
    out.mkdir(mode=0o700, parents=False, exist_ok=False)
    with connect(c) as conn:
        before = database_inventory(conn)
        source = next(r['path'] for r in c['resources'] if r['kind'] == 'private-files')
        original = inventory(safe_root(source))
        shutil.copyfile(dump, out / 'database.sql'); os.chmod(out / 'database.sql', 0o600)
        (out / 'files').mkdir(mode=0o700)
        for name in original:
            target = out / 'files' / name
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            shutil.copyfile(Path(source) / name, target); os.chmod(target, 0o600)
        if database_inventory(conn) != before or inventory(safe_root(source)) != original:
            raise ValueError('Writers changed snapshot; discard incomplete bundle after investigation')
        report = {'version': 1, 'scopeId': c['scopeId'], 'schema': c['schema'], 'database': before,
                  'dumpSha256': digest((out / 'database.sql').read_bytes()), 'files': original,
                  'commit': os.environ.get('GITHUB_SHA', 'UNVERIFIED_LOCAL'), 'createdAt': dt.datetime.now(UTC).isoformat(),
                  'restoreVerified': False, 'consistency': 'writers stopped; verify restored DB against these fingerprints'}
        private_json(out / 'manifest.json', report)
        return {'status': 'BACKUP_CREATED_NOT_RESTORE_VERIFIED', 'tables': len(before), 'files': len(report['files'])}


def restore_files(c, bundle):
    bundle = safe_root(str(Path(bundle).absolute()))
    manifest = read(bundle / 'manifest.json')
    if c['schema'] == manifest['schema']:
        raise ValueError('Restore target must be a new schema')
    if digest((bundle / 'database.sql').read_bytes()) != manifest['dumpSha256'] or inventory(bundle / 'files') != manifest['files']:
        raise ValueError('Backup bundle checksum mismatch')
    root = safe_root(next(r['path'] for r in c['resources'] if r['kind'] == 'private-files'))
    if inventory(root):
        raise ValueError('Private-file restore target must be empty')
    with connect(c) as conn:
        if database_inventory(conn) != manifest['database']:
            raise ValueError('Restored DB differs from backup; do not attach file storage')
        for name in manifest['files']:
            target = root / name
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            with (bundle / 'files' / name).open('rb') as src, target.open('xb') as dst:
                os.chmod(target, 0o600); shutil.copyfileobj(src, dst)
        if inventory(root) != manifest['files']:
            raise ValueError('Restored file bytes differ')
        with conn.cursor() as cur:
            cur.execute('SELECT storage_key,size_bytes,sha256 FROM files WHERE deleted_at IS NULL')
            for key, size, sha in cur:
                if not re.fullmatch(r'[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\.blob', key):
                    raise ValueError('Invalid committed storage key')
                entry = manifest['files'].get(key)
                if entry != {'bytes': size, 'sha256': sha}:
                    raise ValueError('Metadata/blob mismatch')
        return {'status': 'PASS', 'databaseTables': len(manifest['database']), 'privateFiles': len(manifest['files']),
                'dumpSha256': manifest['dumpSha256'], 'limits': ['Synthetic/offline restore; no RPO/RTO certification',
                  'Run application authorization and real scanner checks before reopening service; scan state is not manufactured here']}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config', required=True); p.add_argument('--output', required=True)
    commands = p.add_subparsers(dest='command', required=True)
    sub = commands.add_parser('plan-retention'); sub.add_argument('--now', required=True)
    sub = commands.add_parser('apply-retention'); sub.add_argument('--plan', required=True); sub.add_argument('--now', required=True)
    sub.add_argument('--confirm-scope', required=True)
    sub = commands.add_parser('backup'); sub.add_argument('--dump', required=True); sub.add_argument('--bundle', required=True)
    sub = commands.add_parser('restore-files'); sub.add_argument('--bundle', required=True)
    a = p.parse_args(); c = config(a.config)
    if Path(a.output).exists():
        raise ValueError('Evidence output already exists')
    if a.command == 'plan-retention':
        result = plan(c, instant(a.now))
    elif a.command == 'apply-retention':
        if a.confirm_scope != c['scopeId']:
            raise ValueError('Explicit scope confirmation mismatch')
        result = retire(c, read(a.plan), instant(a.now))
    elif a.command == 'backup':
        result = backup(c, a.dump, a.bundle)
    else:
        result = restore_files(c, a.bundle)
    private_json(a.output, result)
    print(json.dumps({'command': a.command, 'status': result.get('status', 'PREVIEW_ONLY')}))


if __name__ == '__main__':
    # No credentials, rows or traceback in console. Detailed evidence stays private.
    try:
        main()
    except Exception as error:
        detail = str(error) if isinstance(error, ValueError) else 'review private inputs and DB/storage state'
        raise SystemExit('Operation stopped: ' + type(error).__name__ + '; ' + detail) from None
