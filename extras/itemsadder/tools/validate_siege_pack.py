#!/usr/bin/env python3
"""Static pack checks. This does not replace placement tests on Paper/ItemsAdder."""
import hashlib,json,math
from pathlib import Path
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'manifest.json').read_text())
for item in manifest['models']:
    path=root/item['model'];raw=path.read_bytes();model=json.loads(raw)
    source=json.loads((root/item['source']).read_text())
    assert len(model['elements'])==len(source['elements'])==item['cubes'],item['id']
    if 'original_sha256' in item:assert hashlib.sha256(raw).hexdigest()==item['original_sha256'],item['id']
    for e,b in zip(model['elements'],source['elements']):
        assert e['from']==b['from'] and e['to']==b['to'],e['name']
        for axis in range(3):assert -16<=e['from'][axis]<=e['to'][axis]<=32,(item['id'],e['name'])
        rotation=e.get('rotation',{})
        assert rotation.get('angle',0) in [-45,-22.5,0,22.5,45],e['name']
        for face in e['faces'].values():
            assert face['texture'].startswith('#') and face['texture'][1:] in model['textures'],e['name']
            assert len(face['uv'])==4 and all(math.isfinite(v) and 0<=v<=16 for v in face['uv']),e['name']
    assert all(s<=4 for s in model['display']['head']['scale'])
    if item['id'].endswith('siege_tower'):
        assert model['display']['head']['scale']==[4,4,4]
        floors={tuple(e['from'][1:2]+e['to'][1:2]) for e in model['elements'] if e['name'].startswith('oak_floor_plank_')}
        assert floors=={(14,16),(24,26)},floors
    print(item['id'],len(model['elements']),'elements OK')
print('All six exports valid; original five JSON models preserved byte-for-byte.')
