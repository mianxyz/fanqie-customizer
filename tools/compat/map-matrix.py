"""Per-source dependency matrix; absent means exact declared-class absence only."""
import json,collections
from pathlib import Path
import sys
R=Path(sys.argv[sys.argv.index('--evidence-root')+1]).resolve() if '--evidence-root' in sys.argv else Path(__file__).resolve().parent
load=lambda n:json.loads((R/n).read_text(encoding='utf-8-sig'))
a=load('audit-targets.json');e=load('audit-evidence-targets.json');reports={}
for label in ['cn73932','cn74132','cn74332','cn74532','gp73932']:
 p=load('map-'+label+'.json');v=load('validator-'+label+'.json');known={x['descriptor'][1:-1].replace('/','.') for x in v['class_index']};rows=[]
 for c in a['classes']:
  n=c['name'];status='registered' if n in p['classes'] else 'unresolved';reason='verified class mapping' if status=='registered' else 'no verified class mapping'
  if status=='unresolved' and n.startswith(('com.dragon.','androidx.','com.bytedance.')) and len(n.rsplit('.',1)[-1])>3 and n not in known:status='confirmed-absent-declaration';reason='canonical stable name absent in complete class_defs; semantic replacement remains unknown'
  for src in c.get('evidence',[]):rows.append(dict(src,kind='class',canonical=n,status=status,reason=reason))
 cov=load('map-coverage-'+label+'.json')
 for t in cov['sites']:rows.append(dict(t,canonical=(t.get('owner') or '?')+'#'+t['name']))
 for r in a.get('resources',[]):
  n=r.get('name');srcs=r.get('evidence',[]) or [r]
  for s in srcs:
   if s.get('file'):rows.append({'file':s['file'],'line':s.get('line'),'kind':'resource','canonical':n,'status':'registered' if n in p['resources'] else 'unresolved','reason':'static layout/resource mapping' if n in p['resources'] else 'no unique resource mapping'})
 for gate in load('visual-dependencies.json').get('dependencyGates',[]):
  for tab in ('classes','methods','fields','resources'):
   for key in gate.get(tab,[]):rows.append({'file':gate['file'],'module':gate['module'],'kind':tab[:-1],'canonical':key,'status':'registered' if key in p[tab] else 'unresolved','reason':'explicit visual exact capability dependency'})
 reader_path=R/'map-reader-check.json'
 if reader_path.exists():
  rd=next((r for r in load('map-reader-check.json') if r['label']==label),{})
  for t in rd.get('targets',[]):rows.append({'file':'src/io/github/mianxyz/fanqiecustomizer/ReaderDramaPromotionHooks.java','module':'ReaderDramaPromotionHooks','kind':'method','canonical':t['owner'],'status':t['status'],'members':t['members'],'reason':'direct APK check plus reader provider new-instance evidence; no runtime run'})
 byfile={}
 for row in rows:
  f=row.get('file','unknown');byfile.setdefault(f,[]).append(row)
 out={'label':label,'scope':'Audit source literals and 256 reviewed member sites. Runtime owner unresolved is not absent.','counts':dict(collections.Counter(r['status'] for r in rows)),'files':[{ 'file':f,'counts':dict(collections.Counter(r['status'] for r in rs)),'dependencies':rs} for f,rs in sorted(byfile.items())]}
 (R/('map-matrix-'+label+'.json')).write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');reports[label]={'counts':out['counts'],'files':len(byfile)}
(R/'map-matrix-summary.json').write_text(json.dumps(reports,indent=2),encoding='utf-8');print(json.dumps(reports))
