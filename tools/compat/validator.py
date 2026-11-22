"""Targeted APK definition metadata. Standard library only; never decodes instructions.
Run with python -B validator.py --help to keep imports free of cache writes.
"""
import argparse
import hashlib
import json
import re
import sqlite3
import struct
import subprocess
import zipfile
from pathlib import Path

NO_INDEX = 0xffffffff
PRIMITIVES = {'void':'V','boolean':'Z','byte':'B','short':'S','char':'C','int':'I','long':'J','float':'F','double':'D'}

def descriptor(name):
    if not isinstance(name, str) or not name:
        raise ValueError('empty type')
    if name in PRIMITIVES:
        return PRIMITIVES[name]
    if name.endswith('[]'):
        return '[' + descriptor(name[:-2])
    if name.startswith('[') or (name.startswith('L') and name.endswith(';')) or name in PRIMITIVES.values():
        return name
    return 'L' + name.replace('.', '/') + ';'

def dotted(name):
    return name[1:-1].replace('/', '.') if name.startswith('L') and name.endswith(';') else name

class DexFile:
    """One standard little-endian DEX. classes() yields class_defs/class_data only."""
    def __init__(self, data, name='classes.dex'):
        self.data, self.name = data, name
        if len(data) < 112 or data[:8] not in {b'dex\n'+str(v).encode()+b'\x00' for v in ('035','036','037','038','039','040')}:
            raise ValueError('unsupported DEX magic/version')
        if self.u32(40) != 0x12345678 or self.u32(36) != 112:
            raise ValueError('unsupported endian/header (DEX 041 containers excluded)')
        if self.u32(32) != len(data):
            raise ValueError('DEX file_size mismatch')
        self.string_count, self.string_off = self.pair(56)
        self.type_count, self.type_off = self.pair(64)
        self.proto_count, self.proto_off = self.pair(72)
        self.field_count, self.field_off = self.pair(80)
        self.method_count, self.method_off = self.pair(88)
        self.class_count, self.class_off = self.pair(96)
        for n, off, width in [(self.string_count,self.string_off,4),(self.type_count,self.type_off,4),(self.proto_count,self.proto_off,12),(self.field_count,self.field_off,8),(self.method_count,self.method_off,8),(self.class_count,self.class_off,32)]:
            self.bounds(off,n*width)
        self._strings, self._types, self._protos = {}, {}, {}
    def bounds(self, off, size):
        if off < 0 or size < 0 or off + size > len(self.data):
            raise ValueError('DEX range outside file')
    def u32(self, off):
        self.bounds(off,4)
        return struct.unpack_from('<I',self.data,off)[0]
    def u16(self, off):
        self.bounds(off,2)
        return struct.unpack_from('<H',self.data,off)[0]
    def pair(self, off):
        return self.u32(off), self.u32(off+4)
    def uleb(self, off):
        value=0
        for n in range(5):
            self.bounds(off,1)
            b=self.data[off]; off+=1
            if n==4 and b>15:
                raise ValueError('uleb128 overflow')
            value |= (b & 127) << (7*n)
            if not b & 128:
                return value,off
        raise ValueError('unterminated uleb128')
    @staticmethod
    def mutf8(raw, units):
        # Decode MUTF-8 to UTF-16 code units, including encoded NUL and surrogate pairs.
        out=[]; i=0
        while i<len(raw):
            b=raw[i]; i+=1
            if 1<=b<128:
                out.append(b)
            elif b & 0xe0 == 0xc0:
                if i>=len(raw) or raw[i]&0xc0!=0x80: raise ValueError('bad MUTF8 continuation')
                c=((b&31)<<6)|(raw[i]&63); i+=1
                if c<128 and not (b==0xc0 and c==0): raise ValueError('overlong MUTF8')
                out.append(c)
            elif b & 0xf0 == 0xe0:
                if i+1>=len(raw) or any(x&0xc0!=0x80 for x in raw[i:i+2]): raise ValueError('bad MUTF8 continuation')
                c=((b&15)<<12)|((raw[i]&63)<<6)|(raw[i+1]&63); i+=2
                if c<2048: raise ValueError('overlong MUTF8')
                out.append(c)
            else:
                raise ValueError('bad MUTF8 leading byte')
        if len(out)!=units: raise ValueError('MUTF8 UTF16 length mismatch')
        return b''.join(struct.pack('<H',x) for x in out).decode('utf-16-le','surrogatepass')
    def string(self, idx):
        if idx>=self.string_count: raise ValueError('string index out of range')
        if idx not in self._strings:
            off=self.u32(self.string_off+idx*4)
            units,start=self.uleb(off)
            end=self.data.find(b'\0',start)
            if end<0: raise ValueError('unterminated string_data_item')
            self._strings[idx]=self.mutf8(self.data[start:end],units)
        return self._strings[idx]
    def type(self, idx):
        if idx>=self.type_count: raise ValueError('type index out of range')
        if idx not in self._types:
            self._types[idx]=self.string(self.u32(self.type_off+idx*4))
        return self._types[idx]
    def type_list(self, off):
        if not off: return []
        n=self.u32(off); self.bounds(off+4,n*2)
        return [self.type(self.u16(off+4+i*2)) for i in range(n)]
    def proto(self, idx):
        if idx>=self.proto_count: raise ValueError('proto index out of range')
        if idx not in self._protos:
            off=self.proto_off+idx*12
            ret=self.type(self.u32(off+4)); params=self.type_list(self.u32(off+8))
            self._protos[idx]=(params,ret,'('+''.join(params)+')'+ret)
        return self._protos[idx]
    def method(self, idx):
        if idx>=self.method_count: raise ValueError('method index out of range')
        off=self.method_off+idx*8
        params,ret,desc=self.proto(self.u16(off+2))
        return {'owner':self.type(self.u16(off)), 'name':self.string(self.u32(off+4)), 'descriptor':desc, 'parameters':params,'return_type':ret,'method_idx':idx}
    def field(self, idx):
        if idx>=self.field_count: raise ValueError('field index out of range')
        off=self.field_off+idx*8
        return {'owner':self.type(self.u16(off)),'name':self.string(self.u32(off+4)), 'type':self.type(self.u16(off+2)),'field_idx':idx}
    def classes(self, wanted=None, class_predicate=None):
        """wanted: set of class descriptors; predicate(name,superclass,source_file)."""
        for i in range(self.class_count):
            off=self.class_off+i*32
            name=self.type(self.u32(off))
            sup=self.u32(off+8); src=self.u32(off+16)
            superclass=None if sup==NO_INDEX else self.type(sup)
            source_file=None if src==NO_INDEX else self.string(src)
            if wanted is not None and name not in wanted and not (class_predicate and class_predicate(name,superclass,source_file)):
                continue
            cls={'descriptor':name,'name':dotted(name),'superclass':superclass,'source_file':source_file,'interfaces':self.type_list(self.u32(off+12)), 'access_flags':self.u32(off+4),'dex':self.name,'class_def_index':i,'fields':[],'methods':[]}
            pos=self.u32(off+24)
            if pos:
                counts=[]
                for _ in range(4):
                    n,pos=self.uleb(pos); counts.append(n)
                for kind,count in zip(('static','instance','direct','virtual'),counts):
                    idx=0
                    for _ in range(count):
                        diff,pos=self.uleb(pos); idx+=diff
                        flags,pos=self.uleb(pos)
                        if kind in ('static','instance'):
                            item=self.field(idx); cls['fields'].append(item)
                        else:
                            code,pos=self.uleb(pos)
                            item=self.method(idx); item['code_off']=code; cls['methods'].append(item)
                        if item['owner']!=name: raise ValueError('class_data member owner mismatch')
                        item.update(access_flags=flags,kind=kind,defined=True)
            yield cls

class ApkMetadata:
    """Loads only selected class definitions; errors preserve unknown coverage."""
    def __init__(self, apk, class_names, class_predicate=None):
        self.apk=str(Path(apk).resolve()); self.classes={}; self.errors=[]; self.dex=[]; self.class_index=[]
        wanted={descriptor(n) for n in class_names}; self.selected_names=wanted
        h=hashlib.sha256()
        with open(apk,'rb') as f:
            for part in iter(lambda:f.read(1024*1024),b''): h.update(part)
        self.sha256=h.hexdigest()
        with zipfile.ZipFile(apk) as z:
            members=[n for n in z.namelist() if re.fullmatch(r'classes(?:[0-9]+)?\.dex',n)]
            if not members: self.errors.append({'error':'no root classes*.dex members'})
            for name in members:
                try:
                    data=z.read(name); dex=DexFile(data,name)
                    for i in range(dex.class_count):
                        off=dex.class_off+i*32; sup=dex.u32(off+8); src=dex.u32(off+16)
                        self.class_index.append({'descriptor':dex.type(dex.u32(off)),'superclass':None if sup==NO_INDEX else dex.type(sup),'source_file':None if src==NO_INDEX else dex.string(src),'dex':name})
                    rows=list(dex.classes(wanted,class_predicate))
                    for row in rows: self.classes.setdefault(row['descriptor'],[]).append(row)
                    self.dex.append({'name':name,'sha256':hashlib.sha256(data).hexdigest(),'class_defs':dex.class_count,'selected':len(rows),'status':'complete'})
                except Exception as e:
                    self.errors.append({'dex':name,'error':str(e)})
    @property
    def complete(self): return not self.errors
    def definitions(self, name): return self.classes.get(descriptor(name),[])
    def as_dict(self):
        return {'apk':self.apk,'sha256':self.sha256,'coverage':'complete' if self.complete else 'partial','dex':self.dex,'errors':self.errors,'class_index':self.class_index,'selected_names':sorted(self.selected_names),'classes':[r for rows in self.classes.values() for r in rows]}

RESOURCE_RE=re.compile(r'^\s*resource\s+(0x[0-9a-fA-F]{8})\s+([^\s:]+:)?([A-Za-z0-9_]+)/([^\s:(]+)',re.M)
def parse_resources(text):
    rows=[]; seen=set()
    for m in RESOURCE_RE.finditer(text):
        rid=int(m[1],16); package=(m[2] or '').rstrip(':'); typ=m[3]; name=m[4]
        key=(rid,package,typ,name)
        if key not in seen:
            seen.add(key); rows.append({'id':rid,'id_hex':f'0x{rid:08x}','package':package,'type':typ,'name':name,'qualified_name':(package+':' if package else '')+typ+'/'+name})
    return rows

def read_resources(apk,aapt2,timeout=120):
    args=[str(aapt2),'dump','resources',str(apk)]
    try:
        run=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
        text=run.stdout.decode('utf-8','replace'); rows=parse_resources(text)
        ok=run.returncode==0 and bool(rows)
        return {'status':'complete' if ok else 'unknown','command':args,'returncode':run.returncode,'stdout_sha256':hashlib.sha256(run.stdout).hexdigest(),'stderr':run.stderr.decode('utf-8','replace')[-4000:],'resources':rows}
    except (OSError,subprocess.TimeoutExpired) as e:
        return {'status':'unknown','command':args,'error':str(e),'resources':[]}

def target_class(t):
    return t.get('class') or t.get('className') or t.get('owner') or (t.get('name') if t.get('kind')=='class' else None)

def validate_targets(metadata,targets,resources=None):
    """Flat targets. Explicit owner+descriptor required for exact member signature."""
    results=[]
    for number,t in enumerate(targets):
        kind=t.get('kind'); owner=target_class(t)
        row={'target_index':number,'target':t,'status':'unknown','reason':'selector or owner unresolved'}
        if owner and hasattr(metadata,'selected_names') and descriptor(owner) not in metadata.selected_names and not metadata.definitions(owner):
            row['reason']='owner outside selected metadata projection'
            results.append(row); continue
        if kind=='class' and owner:
            defs=metadata.definitions(owner)
            row.update(status='defined' if defs else ('absent' if metadata.complete else 'unknown'),definitions=defs,reason='class_defs lookup')
        elif kind in ('method','field') and owner:
            defs=metadata.definitions(owner); members=[m for c in defs for m in c[kind+'s'] if m['name']==t.get('name')]
            sig=t.get('descriptor') if kind=='method' else t.get('type')
            if kind=='method' and not sig and t.get('parameters') is not None and t.get('return_type'):
                sig='('+''.join(descriptor(p) for p in t['parameters'])+')'+descriptor(t['return_type'])
            if kind=='field' and sig: sig=descriptor(sig)
            valid_type=r'(?:[ZBSCIJFD]|L[^;()]+;|\[+(?:[ZBSCIJFD]|L[^;()]+;))'
            if sig and kind=='method' and not re.fullmatch(r'\((?:'+valid_type+r')*\)(?:V|'+valid_type+r')',sig):
                sig=None
            if sig and kind=='field' and not re.fullmatch(valid_type,sig):
                sig=None
            if sig:
                matches=[m for m in members if m.get('descriptor' if kind=='method' else 'type')==sig]
                row.update(status='defined' if matches else ('absent' if metadata.complete else 'unknown'),matches=matches,reason='exact class_data definition signature')
            else:
                row.update(matches=members,reason='signature incomplete; name candidates are not compatibility proof')
        elif kind.startswith('resource') if isinstance(kind,str) else False:
            if resources and resources.get('status')=='complete':
                name=t.get('name',''); rid=t.get('id'); rid=int(rid,0) if isinstance(rid,str) else rid
                matches=[r for r in resources['resources'] if (rid is None or r['id']==rid) and (not name or name in (r['name'],r['type']+'/'+r['name'],r['qualified_name']))]
                row.update(status='defined' if matches else 'absent',matches=matches,reason='aapt2 resource table declaration')
        results.append(row)
    return results

def write_sqlite(path,metadata):
    """Selected metadata as JSON rows with queryable identity columns."""
    with sqlite3.connect(path) as db:
        db.executescript('CREATE TABLE IF NOT EXISTS classes(apk_sha256 TEXT,dex TEXT,name TEXT,superclass TEXT,source_file TEXT,json TEXT); CREATE TABLE IF NOT EXISTS methods(apk_sha256 TEXT,owner TEXT,name TEXT,descriptor TEXT,json TEXT); CREATE TABLE IF NOT EXISTS fields(apk_sha256 TEXT,owner TEXT,name TEXT,type TEXT,json TEXT);')
        for table in ('classes','methods','fields'): db.execute('DELETE FROM '+table+' WHERE apk_sha256=?',(metadata.sha256,))
        for rows in metadata.classes.values():
            for c in rows:
                db.execute('INSERT INTO classes VALUES(?,?,?,?,?,?)',(metadata.sha256,c['dex'],c['descriptor'],c['superclass'],c['source_file'],json.dumps(c,ensure_ascii=True)))
                for table,key in [('methods','descriptor'),('fields','type')]:
                    for m in c[table]: db.execute('INSERT INTO '+table+' VALUES(?,?,?,?,?)',(metadata.sha256,c['descriptor'],m['name'],m[key],json.dumps(m,ensure_ascii=True)))
        db.executescript('CREATE INDEX IF NOT EXISTS idx_classes ON classes(name); CREATE INDEX IF NOT EXISTS idx_methods ON methods(owner,name,descriptor); CREATE INDEX IF NOT EXISTS idx_fields ON fields(owner,name,type);')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk',required=True); p.add_argument('--targets',required=True); p.add_argument('--out',required=True); p.add_argument('--sqlite'); p.add_argument('--aapt2')
    a=p.parse_args(); spec=json.loads(Path(a.targets).read_text(encoding='utf-8-sig')); targets=list(spec.get('targets',[]))
    for c in spec.get('classes',[]):
        targets.append({'kind':'class','name':c['name']})
        for kind in ('method','field'):
            targets.extend(dict(m,kind=kind,owner=c['name']) for m in c.get(kind+'s',[]))
    targets.extend(dict(r,kind='resource') for r in spec.get('resources',[]))
    names={target_class(t) for t in targets if target_class(t)}
    meta=ApkMetadata(a.apk,names)
    res=read_resources(a.apk,a.aapt2) if a.aapt2 else {'status':'unknown','resources':[],'reason':'aapt2 not selected'}
    report=meta.as_dict(); report['resource_table']=res; report['results']=validate_targets(meta,targets,res)
    report['limitations']=['Static definitions only; no runtime reachability or behavioral compatibility proof.','Incomplete signatures and unresolved owners remain unknown.','External framework, native and downloaded code are outside APK DEX coverage.']
    Path(a.out).write_text(json.dumps(report,ensure_ascii=True,indent=2),encoding='utf-8')
    if a.sqlite: write_sqlite(a.sqlite,meta)
    print(json.dumps({'out':a.out,'classes':len(report['classes']),'dex':len(meta.dex),'errors':meta.errors,'results':len(report['results'])}))
if __name__=='__main__': main()
