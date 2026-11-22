import json, struct, unittest
from validator import DexFile, parse_resources, descriptor, validate_targets

def fixture():
    # One class, one defined virtual method, one reference-only method_id.
    strings=['Ltest/C;','Ljava/lang/Object;','V','defined','referenceOnly','源.java']
    b=bytearray(112+len(strings)*4+3*4+12+2*8+32)
    b[:8]=b'dex\n035\0'; struct.pack_into('<II',b,36,112,0x12345678)
    so=112; to=so+len(strings)*4; po=to+12; mo=po+12; co=mo+16
    for off,n,pos in [(56,len(strings),so),(64,3,to),(72,1,po),(80,0,0),(88,2,mo),(96,1,co)]: struct.pack_into('<II',b,off,n,pos)
    for i,s in enumerate(strings):
        struct.pack_into('<I',b,so+i*4,len(b)); b+=bytes([len(s)])+s.encode('utf-8')+b'\0'
    for i in range(3): struct.pack_into('<I',b,to+i*4,i)
    struct.pack_into('<III',b,po,2,2,0)
    for i in range(2): struct.pack_into('<HHI',b,mo+i*8,0,0,3+i)
    dataoff=len(b); b+=bytes([0,0,0,1,0,1,0])
    struct.pack_into('<8I',b,co,0,1,1,0,5,0,dataoff,0)
    struct.pack_into('<I',b,32,len(b))
    return bytes(b)

class Tests(unittest.TestCase):
    def test_mutf8_units_and_nul(self):
        self.assertEqual(DexFile.mutf8('中文'.encode(),2),'中文')
        self.assertEqual(DexFile.mutf8(b'a\xc0\x80b',3),'a\0b')
        self.assertEqual(DexFile.mutf8(bytes.fromhex('eda0bdedb880'),2),'😀')
        with self.assertRaises(ValueError): DexFile.mutf8('中'.encode(),3)
    def test_definition_not_method_ids(self):
        d=DexFile(fixture()); c=list(d.classes({'Ltest/C;'}))[0]
        self.assertEqual(c['source_file'],'源.java')
        self.assertEqual(d.method_count,2)
        self.assertEqual([(m['name'],m['descriptor']) for m in c['methods']],[('defined','()V')])
    def test_nul_termination_not_utf16_byte_slice(self):
        d=DexFile(fixture())
        self.assertEqual(d.string(5),'源.java')
        self.assertEqual(len('源.java'.encode('utf-8')),8)
    def test_structural_candidate(self):
        d=DexFile(fixture())
        rows=list(d.classes(set(),lambda n,s,f:f=='源.java'))
        self.assertEqual(rows[0]['descriptor'],'Ltest/C;')
    def test_truncated(self):
        with self.assertRaises(ValueError): DexFile(fixture()[:-1])
    def test_bad_header_and_bounds(self):
        for field,value in [(40,0x78563412),(36,120),(60,0xfffffff0)]:
            b=bytearray(fixture()); struct.pack_into('<I',b,field,value)
            with self.assertRaises(ValueError): DexFile(bytes(b))
        d=DexFile(fixture())
        with self.assertRaises(ValueError): d.method(d.method_count)
        with self.assertRaises(ValueError): d.string(d.string_count)
    def test_bad_mutf8(self):
        for raw in (b'\xf0\x9f\x98\x80',b'\xe0\x80\x80',b'\xc2',b'\x00'):
            with self.assertRaises(ValueError): DexFile.mutf8(raw,1)
    def test_outside_projection_unknown(self):
        class Meta:
            complete=True
            selected_names={'Ltest/C;'}
            def definitions(self,name): return []
        row=validate_targets(Meta(),[{'kind':'class','name':'test.Other'}])[0]
        self.assertEqual(row['status'],'unknown')
    def test_resources(self):
        rows=parse_resources(' resource 0x7f010002 com.test:id/title\n resource 0x7f010003 string/name')
        self.assertEqual(len(rows),2); self.assertEqual(rows[0]['name'],'title')
    def test_descriptor(self):
        self.assertEqual(descriptor('java.lang.String[]'),'[Ljava/lang/String;')
    def test_incomplete_unknown(self):
        c=list(DexFile(fixture()).classes())[0]
        class Meta:
            complete=True
            def definitions(self,name): return [c]
        rows=validate_targets(Meta(),[{'kind':'method','owner':'test.C','name':'defined'}, {'kind':'method','owner':'test.C','name':'referenceOnly','descriptor':'()V'}, {'kind':'method','owner':'test.C','name':'defined','descriptor':'()'}])
        self.assertEqual([r['status'] for r in rows],['unknown','absent','unknown'])
if __name__=='__main__': unittest.main(verbosity=2)
