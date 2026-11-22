import json,re
from pathlib import Path
R=Path(__file__).resolve().parent
load=lambda n:json.loads((R/n).read_text(encoding='utf-8'))
root=R.parent.parent/'src/io/github/mianxyz/fanqiecustomizer';gates=[]
for f in root.glob('*.java'):
 text=f.read_text(encoding='utf-8')
 pattern=re.escape('HostSymbols.requireCapability(')+'"([^"]+)"'+''.join(re.escape(',new String[]{')+'([^}]*)'+re.escape('}') for _ in range(4))
 for m in re.finditer(pattern,text):gates.append(dict(module=m[1],file=f.name,**{k:re.findall('"([^"]+)"',m[i+2]) for i,k in enumerate(['classes','methods','fields','resources'])}))
assert gates,'No live capability gates extracted'
reports=[]
for label in ['cn73932','cn74132','cn74332','cn74532','gp73932']:
 p=load('map-'+label+'.json');mods=[]
 for g in gates:
  deps=[{'kind':t,'canonical':k,'actual':p[t].get(k),'status':'resolved' if k in p[t] else 'unknown'} for t in ['classes','methods','fields','resources'] for k in g[t]]
  mods.append({'module':g['module'],'file':g['file'],'status':'resolved-needs-runtime' if all(d['status']=='resolved' for d in deps) else 'unknown-required-dependency','runtime':'needs-runtime','no_host_component':False,'dependencies':deps})
 reports.append({'label':label,'modules':mods})
(R/'map-essential-matrix.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8');print([{ 'label':r['label'],'unknown':[(m['module'],[d['canonical'] for d in m['dependencies'] if d['status']=='unknown']) for m in r['modules'] if m['status'].startswith('unknown')]} for r in reports])
