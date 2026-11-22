"""Report callsite coverage; never treat candidate existence as registered support."""
import json,collections
from pathlib import Path
import sys
R=Path(sys.argv[sys.argv.index('--evidence-root')+1]).resolve() if '--evidence-root' in sys.argv else Path(__file__).resolve().parent
load=lambda n:json.loads((R/n).read_text(encoding='utf-8-sig'))
ev=load('audit-evidence-targets.json');reports=[]
for label in ['cn73932','cn74132','cn74332','cn74532','gp73932']:
 p=load('map-'+label+'.json');rows=[]
 for t in ev['targets']:
  owner=t.get('owner');kind=t['kind'];name=t['name'];sig=t.get('descriptor');status='unresolved';reason='runtime owner unresolved'
  if owner:
   if owner not in p['classes']:reason='owner not registered'
   else:
    tab=p['methods'] if kind in ('method','constructor') else p['fields'];key=owner+'#'+name
    if sig:key+=(':' if kind=='field' else '')+sig
    if key in tab:status='registered-exact' if sig else 'registered-all-overloads';reason='static definition mapping; runtime invocation not asserted'
    else:reason='full descriptor unresolved or exact key absent'
  context=' '.join(c.get('source','') for c in t.get('source_context',[]))
  domain='host-explicit' if owner else 'runtime-type-constrained'
  if not owner and any(x in t.get('call','') for x in ['View.class','ViewGroup.class','Activity.class','Canvas.class','Drawable.class','Class.class']):domain='platform-explicit'
  elif not owner and any(x in context for x in ['aq6.','wi3.','com.dragon.','f0.','androidx.compose']):domain='host-context-runtime-owner-unresolved'
  rows.append({'domain':domain,'file':t['file'],'line':t['line'],'kind':kind,'owner':owner,'name':name,'descriptor':sig,'status':status,'reason':reason})
 report={'label':label,'callsite_counts':dict(collections.Counter(x['status'] for x in rows)),'unresolved_reasons':dict(collections.Counter(x['reason'] for x in rows if x['status']=='unresolved')),'sites':rows};reports.append({k:v for k,v in report.items() if k!='sites'});(R/('map-coverage-'+label+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(R/'map-coverage-summary.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(reports))
