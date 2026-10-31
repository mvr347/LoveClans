#!/usr/bin/env python3
"""Import the supplied pack and align the tower's model with its walkable block profile."""
import copy,json,sys,uuid,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
archive=Path(sys.argv[1])
with zipfile.ZipFile(archive) as z:
    for n in z.namelist():
        if n.startswith(('contents/','blockbench/','vanilla-placement/')) or n in ('manifest.json','INTEGRATION_RU.md'):
            dest=(root/n).resolve()
            if not dest.is_relative_to(root):raise ValueError(n)
            dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))

def edit(elements,source=False):
    out=[]
    for original in elements:
        e=copy.deepcopy(original);name=e.get('name','')
        if name.startswith('oak_chassis_deck_'):e['from'][1]=4;e['to'][1]=6
        if name.startswith('oak_floor_plank_'):
            e['from'][1],e['to'][1]=(14,16) if original['from'][1]<20 else (24,26)
        if name.startswith('oak_assault_bridge_'):
            e['from'][1]=24;e['to'][1]=26
            if source:e['rotation']=[0,0,0]
            else:e['rotation']={'axis':'x','angle':0,'origin':[8,24,1]}
        if name.startswith('oak_rear_ladder_rail_'):e['from'][1]=0;e['to'][1]=28
        # Entrance and rear landings require real openings in the visible facade.
        pieces=[e]
        if 'front_' in name:
            for low,high in [(6,14),(16,24)]:
                next_pieces=[]
                for p in pieces:
                    f,t=p['from'],p['to']
                    if t[0]<=6 or f[0]>=10 or t[1]<=low or f[1]>=high:next_pieces.append(p);continue
                    # Subtract doorway [6,10] x [low,high], keep side strips and lintels.
                    boxes=[]
                    if f[0]<6:boxes.append(([f[0],f[1],f[2]],[6,t[1],t[2]]))
                    if t[0]>10:boxes.append(([10,f[1],f[2]],[t[0],t[1],t[2]]))
                    x0,x1=max(f[0],6),min(t[0],10)
                    if f[1]<low:boxes.append(([x0,f[1],f[2]],[x1,low,t[2]]))
                    if t[1]>high:boxes.append(([x0,high,f[2]],[x1,t[1],t[2]]))
                    for j,(a,b) in enumerate(boxes):
                        q=copy.deepcopy(p);q['from'],q['to']=a,b;q['name']=p['name']+f'_door_{low}_{j}'
                        if source:q['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,q['name']))
                        next_pieces.append(q)
                pieces=next_pieces
        out.extend(pieces)
    # Extend the visible ladder to the ground, matching the real climbable ladder.
    template=next(e for e in elements if e.get('name','').startswith('oak_rear_ladder_rung_'))
    for i,y in enumerate([0.7,2,3.3,4.6,24.5,25.8,27.1]):
        e=copy.deepcopy(template);e['name']=f'integration_ladder_rung_{i}';e['from'][1]=y;e['to'][1]=y+0.375
        if source:e['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,e['name']))
        out.append(e)
    return out
for path,source in [(root/'contents/loveclans/models/siege/siege_tower.json',False),(root/'blockbench/siege_tower.bbmodel',True)]:
    d=json.loads(path.read_text());d['elements']=edit(d['elements'],source)
    for context in ['head','fixed']:d['display'][context]['scale']=[4,4,4];d['display'][context]['translation']=[0,32,0]
    if source:d['outliner']=[e['uuid'] for e in d['elements']]
    else:d.pop('groups',None)
    path.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
p=root/'contents/loveclans/configs/siege.yml';s=p.read_text();at=s.index('  siege_tower:');s=s[:at]+s[at:].replace('width: 5','width: 9').replace('height: 6','height: 10');p.write_text(s)
p=root/'manifest.json';d=json.loads(p.read_text());tower=next(x for x in d['models'] if x['id']=='loveclans:siege_tower');tower['cubes']=len(json.loads((root/'contents/loveclans/models/siege/siege_tower.json').read_text())['elements']);tower['integration']='Walkable level IV; head/fixed scale 4; floor surfaces 1.5/4/6.5 blocks';p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
print('Imported six models; tower model and Blockbench source aligned.')
