"""Generate evidence-fed profiles. Run python -B map-generate.py [--recheck].
Only map* outputs are written. Structural candidates are never promoted.
"""
import json,re,hashlib,sys,collections
from pathlib import Path
from validator import descriptor,dotted,ApkMetadata
ROOT=Path(sys.argv[sys.argv.index('--evidence-root')+1]).resolve() if '--evidence-root' in sys.argv else Path(__file__).resolve().parent
LABELS=['cn73932','cn74132','cn74332','cn74532','gp73932']
def load(n,default=None):
 p=ROOT/n
 return json.loads(p.read_text(encoding='utf-8-sig')) if p.exists() else default
def save(n,d): (ROOT/n).write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
def stable(n):
 # Last component is semantic, not an obfuscated short token.
 return '.' in n and all(len(v)>2 or v in ('Kt',) for v in n.rsplit('.',1)[-1].split('$') if v) and not n.rsplit('.',1)[-1].split('$')[0] in ('g0','n0','b0')
def unique(cs):
 out={}
 for c in cs: out.setdefault(c['name'],[]).append(c)
 return {n:v[0] for n,v in out.items() if len(v)==1}
def translated(sig,cm,known):
 def sub(m):
  n=dotted(m.group())
  if n in cm:return descriptor(cm[n])
  if n.startswith(('java.','javax.','android.','dalvik.')):return m.group()
  # Stable signature types must also be actually defined in the target APK.
  if stable(n) and descriptor(n) in known:return m.group()
  return '@UNRESOLVED:'+n+'@'
 return re.sub(r'L[^;]+;',sub,sig)
audit=load('audit-targets.json'); ev=load('audit-evidence-targets.json')
requested_classes={c['name'] for c in audit['classes']}
method_names={t['name'] for t in ev['targets'] if t['kind'] in ('method','constructor')}
field_names={t['name'] for t in ev['targets'] if t['kind']=='field'}
field_names.update(['g','Companion'])
visual=load('visual-dependencies.json',{})
for gate in visual.get('dependencyGates',[]):
 for key in gate.get('methods',[]):method_names.add(key.split('#',1)[1].split('(',1)[0])
 for key in gate.get('fields',[]):field_names.add(key.split('#',1)[1].split(':',1)[0])
for c in audit['classes']:
 method_names.update(m['name'] for m in c.get('methods',[]));field_names.update(f['name'] for f in c.get('fields',[]))
base_data=load('validator-cn73932.json'); base=unique(base_data['classes'])
visual_extra={r['label']:r for r in load('validator-visual-extra.json',[])}
base.update(unique(visual_extra.get('cn73932',{}).get('classes',[])))
semantic={r['label']:r for r in load('validator-semantic-finish.json',[])}
bottom=load('inventory-bottom-verified.json',{}).get('profiles',{})
draw=load('inventory-draw-verified.json',{}).get('profiles',{})
dsl={r['label']:r for r in load('validator-dsl-summary.json',[])}
anchors=load('inventory-verified-mapping.json',{'profiles':{}})
resources=load('resource-mapping.json',{})
profiles={}; summaries=[]
for label in LABELS:
 data=base_data if label=='cn73932' else load('validator-'+label+'.json')
 actual=unique(data['classes']);actual.update(unique(visual_extra.get(label,{}).get('classes',[])));known={c['descriptor'] for c in data['class_index']}
 cm={n:n for n in base if label=='cn73932' or stable(n) and n in actual}
 evidence={n:{'level':'baseline-identity' if label=='cn73932' else 'stable-name-defined','dex':actual[n]['dex'],'class_def_index':actual[n]['class_def_index']} for n in cm}
 for row in anchors.get('profiles',{}).get(label,{}).get('mappings',[]):
  if row.get('status')=='verified-static-anchor':
   cm[row['from']]=row['to'];evidence[row['from']]={'level':row['status'],'anchors':[{'key':a.get('key'),'target':{k:v for k,v in a.get('target',{}).items() if k in ('evidence_path','evidence_line')},'baseline':[{k:v for k,v in b.items() if k in ('evidence_path','evidence_line')} for b in a.get('baseline',[])[:1]]} for a in row.get('anchors',[])[:3]]}
 for row in dsl.get(label,{}).get('string_resolver',[]):
  if row.get('defined'):cm['com.dragon.read.kmp.dsl.config.j']=dotted(row['owner']);evidence['com.dragon.read.kmp.dsl.config.j']={'level':'verified-Kt-direct-invoke','evidence':'validator-dsl-verified.json'}
 for n,t in draw.get(label,{}).get('class_map',{}).items():
  cm[n]=t;evidence[n]={'level':'verified-static-draw-ABI','evidence':'inventory-draw-verified.json'}
 for dm in draw.get(label,{}).get('members',[]):
  n='f0.d';cm[n]=dotted(dm['drawscope']);evidence[n]={'level':'verified-static-draw-ABI-parameter','evidence':'inventory-draw-verified.json'}
 for n,t in bottom.get(label,{}).get('class_map',{}).items():
  cm[n]=t;evidence[n]={'level':'verified-static-functional-subset','evidence':'inventory-bottom-verified.json'}
 for role in load('inventory-dsl-owner-roles.json',{}).get(label,[]):
  if role.get('status','').startswith('verified-'):
   n=dotted(role['from']);cm[n]=dotted(role['to']);evidence[n]={'level':role['status'],'evidence':'inventory-dsl-owner-roles.json'}
 for role in load('inventory-dsl-type-roles.json',{}).get(label,[]):
  if role.get('status')=='verified-static-field-use-role':
   n=dotted(role['from_type']);cm[n]=dotted(role['to_type']);evidence[n]={'level':role['status'],'node_field':role['baseline_node_field'],'evidence':'inventory-dsl-type-roles.json'}
 # Infer obfuscated signature types only through a unique stable semantic member.
 for iteration in range(4):
  proposals={}
  for owner,target in list(cm.items()):
   if owner not in base or target not in actual or not stable(owner):continue
   for kind in ('method','field'):
    for bm in base[owner][kind+'s']:
     if len(bm['name'])<4 or bm['name'].startswith('<'):continue
     bslots=bm['parameters']+[bm['return_type']] if kind=='method' else [bm['type']]
     matches=[]
     for tm in actual[target][kind+'s']:
      if tm['name']!=bm['name'] or bool(tm['access_flags']&8)!=bool(bm['access_flags']&8):continue
      tslots=tm['parameters']+[tm['return_type']] if kind=='method' else [tm['type']]
      if len(tslots)!=len(bslots):continue
      ok=True
      for bs,ts in zip(bslots,tslots):
       translated_slot=translated(bs,cm,known)
       if '@UNRESOLVED:' not in translated_slot:
        if translated_slot!=ts:ok=False;break
       elif not bs.lstrip('[').startswith('L') or not ts.lstrip('[').startswith('L') or len(bs)-len(bs.lstrip('['))!=len(ts)-len(ts.lstrip('[')):ok=False;break
      if ok:matches.append(tm)
     if len(matches)!=1:continue
     tm=matches[0];tslots=tm['parameters']+[tm['return_type']] if kind=='method' else [tm['type']]
     for position,(bs,ts) in enumerate(zip(bslots,tslots)):
      if '@UNRESOLVED:' not in translated(bs,cm,known):continue
      bn=dotted(bs.lstrip('['));tn=dotted(ts.lstrip('['))
      if descriptor(tn) not in known:continue
      proposals.setdefault(bn,[]).append((tn,{'level':'unique-stable-member-signature-position','owner':owner,'member':bm['name'],'baseline_descriptor':bm.get('descriptor',bm.get('type')),'actual_descriptor':tm.get('descriptor',tm.get('type')),'slot':position}))
  added=0
  for bn,rows in proposals.items():
   choices={v[0] for v in rows}
   if len(choices)!=1:continue
   tn=next(iter(choices))
   if bn in cm or tn in cm.values():continue
   cm[bn]=tn;evidence[bn]=rows[0][1];added+=1
  if not added:break
 # Load missing mapped class bodies directly from the APK, never class_index-only.
 missing=[a for a in cm.values() if a not in actual]
 if missing:
  fresh=ApkMetadata(data['apk'],missing)
  if fresh.errors:raise RuntimeError(fresh.errors)
  for ds in fresh.classes.values():
   if len(ds)==1:actual[ds[0]['name']]=ds[0]
 cm={n:a for n,a in cm.items() if a in actual}
 # Enforce bijection, discard ambiguous reverse aliases.
 counts=collections.Counter(cm.values());cm={n:a for n,a in cm.items() if counts[a]==1}
 methods={};fields={};validation=[];unresolved=[];member_evidence=[]
 for owner in sorted(set(cm)-set(base)):
  dst=actual[cm[owner]];validation.append({'kind':'class','canonical':owner,'actual':cm[owner],'superclass':dst['superclass'],'interfaces':dst['interfaces']})
 for owner,src in base.items():
  if owner not in cm:
   unresolved.append({'kind':'class','owner':owner,'reason':'no verified semantic anchor'});continue
  dst=actual[cm[owner]];validation.append({'kind':'class','canonical':owner,'actual':cm[owner],'superclass':dst['superclass'],'interfaces':dst['interfaces']})
  for kind,names in [('method',method_names),('field',field_names)]:
   for m in src[kind+'s']:
    if m['name'] not in names:continue
    sig=m['descriptor'] if kind=='method' else m['type'];tsig=sig if label=='cn73932' else translated(sig,cm,known)
    actual_member_name=m['name']
    if kind=='method':
     if owner=='androidx.compose.ui.draw.i' and m['name']=='s' and sig=='(Lf0/d;)V':
      for dm in draw.get(label,{}).get('members',[]):
       if dm['descriptor']==tsig:actual_member_name=dm['to']
     main=bottom.get(label,{}).get('main_icon_verified',{})
     if owner=='com.dragon.read.pages.main.MainFragmentActivity' and m['name']=='r2' and sig=='()Z' and main.get('status')=='verified-static-config-gate':actual_member_name=main['to']
     for bm in bottom.get(label,{}).get('members',[]):
      if bm['owner']==cm[owner] and bm['from']==m['name'] and bm['descriptor']==tsig:actual_member_name=bm['to']
    if kind=='method' and owner=='uo7.z0' and (m['name'],sig) in [('H','()I'),('J','(I)V')]:
     role='iget ' if m['name']=='H' else 'iput '
     matches_role=[h for obs in semantic.get(label,{}).get('observations',[]) for h in obs.get('height_accessors',[]) if any(ins.startswith(role) and ins.endswith('->d:I') for ins in h['instructions'])]
     if len(matches_role)==1:actual_member_name=matches_role[0]['method'].split()[-1].split('(')[0]
    matches=[x for x in dst[kind+'s'] if x['name']==actual_member_name and (x['descriptor'] if kind=='method' else x['type'])==tsig and bool(x['access_flags']&8)==bool(m['access_flags']&8)]
    if len(matches)!=1:
     unresolved.append({'kind':kind,'owner':owner,'name':m['name'],'descriptor':sig,'reason':'exact name+full descriptor+static not uniquely defined','translated':tsig});continue
    x=matches[0];key=owner+'#'+m['name']+('' if kind=='method' else ':')+sig
    (methods if kind=='method' else fields)[key]=x['name']
    validation.append({'kind':kind,'canonical_owner':owner,'owner':cm[owner],'canonical_name':m['name'],'name':x['name'],'canonical_descriptor':sig,'descriptor':tsig,'static':bool(x['access_flags']&8)})
    member_evidence.append({'key':key,'dex':dst['dex'],'definition_index':x.get('method_idx',x.get('field_idx')),'level':'exact-declared-name-prototype-static'})
 # Simple keys only when every audited baseline overload maps, never partial group aliases.
 for kind,tab in [('method',methods),('field',fields)]:
  for owner,src in base.items():
   for name in {x['name'] for x in src[kind+'s']}:
    members=[x for x in src[kind+'s'] if x['name']==name];keys=[owner+'#'+name+('' if kind=='method' else ':')+(x['descriptor'] if kind=='method' else x['type']) for x in members]
    if keys and all(k in tab for k in keys) and len({tab[k] for k in keys})==1:tab[owner+'#'+name]=tab[keys[0]]
 # Resource integration handled only after resource agent emits documented verified rows.
 rm={}
 if label=='cn73932':
  rm={r['baseline_name']:r['baseline_name'] for r in resources.get('resources',[]) if r.get('baseline_declaration')}
 else:
  for row in resources.get('registration',{}).get(label,[]):rm[row['canonical']]=row['actual']
 if len(set(rm.values()))!=len(rm):raise ValueError('resource reverse ambiguity '+label)
 for n in sorted(requested_classes-set(base)):
  unresolved.append({'kind':'class','owner':n,'reason':'audit target not uniquely defined in baseline; no identity assumption'})
 for t in ev['targets']:
  if not t.get('owner'):unresolved.append({'kind':t['kind'],'name':t['name'],'file':t['file'],'line':t['line'],'reason':'runtime owner unresolved'})
 package='com.dragon.read.oversea.gp' if label.startswith('gp') else 'com.dragon.read';code=int(label[2:])
 profile={'schema':1,'label':label,'package':package,'version_code':code,'apk':data['apk'],'apk_sha256':data['sha256'],'classes':dict(sorted(cm.items())),'methods':dict(sorted(methods.items())),'fields':dict(sorted(fields.items())),'resources':rm,'validation':validation,'class_evidence':evidence,'member_evidence':member_evidence,'unresolved':unresolved}
 save('map-'+label+'.json',profile);profiles[label]=profile
 summaries.append({'label':label,'classes':len(cm),'methods_exact':sum('#' in k and '(' in k for k in methods),'methods_keys':len(methods),'fields_exact':sum(':' in k for k in fields),'fields_keys':len(fields),'resources':len(rm),'unresolved':len(unresolved)})
save('map-summary.json',{'profiles':summaries,'policy':'No unique structural candidate promoted; exact declared member name/full prototype/static required; unknown owner not guessed.'})
print(json.dumps(summaries))
if '--recheck' in sys.argv:
 reports=[]
 for label,p in profiles.items():
  meta=ApkMetadata(p['apk'],list(p['classes'].values())); failures=[];checked=0
  if meta.errors:failures.extend(meta.errors)
  if meta.sha256!=p['apk_sha256']:failures.append('APK digest mismatch')
  for row in p['validation']:
   ds=meta.definitions(row['actual'] if row['kind']=='class' else row['owner'])
   if len(ds)!=1:failures.append(row);continue
   c=ds[0]
   if row['kind']=='class':ok=c['superclass']==row['superclass'] and c['interfaces']==row['interfaces']
   else:
    ok=sum(x['name']==row['name'] and (x['descriptor'] if row['kind']=='method' else x['type'])==row['descriptor'] and bool(x['access_flags']&8)==row['static'] for x in c[row['kind']+'s'])==1
   checked+=1
   if not ok:failures.append(row)
  report={'label':label,'apk_sha256':p['apk_sha256'],'checked':checked,'failures':failures,'errors':meta.errors,'mode':'fresh direct APK class_defs/class_data rescan'};reports.append(report);save('map-recheck-'+label+'.json',report);print(label,'fresh APK checks',checked,'failures',len(failures),flush=True)
 save('map-recheck-summary.json',reports)
 if any(r['failures'] for r in reports):raise SystemExit(1)
