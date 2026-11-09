"""Writes wiki/docs/reference/settings.mdx from the settings registry.

Usage (from the repo root):
    python tools/wiki/settings_dump.py src/main/java/com/frikinjay/mobstacker/config/MobStackerSettings.java > settings.jsonl
    python tools/wiki/wiki_settings.py settings.jsonl wiki/docs/reference/settings.mdx

Run it again whenever a setting is added, renamed or changes its default.
"""
import json
import sys

CATEGORIES = [
    ('STACKING', 'Stacking', None),
    ('MOBS', 'Which mobs stack', 'See [Which mobs stack]($stacking/which_mobs_stack).'),
    ('ORIGIN', 'Spawn origin', 'Which mobs stack, by how they came into the world. See [Which mobs stack]($stacking/which_mobs_stack#spawn-origin).'),
    ('COMBAT', 'Combat', 'See [Combat]($gameplay/combat).'),
    ('FEEDBACK', 'Feedback', None),
    ('DISPLAY', 'Stack display', 'See [Names & colours]($stacking/names_and_colours).'),
    ('BREEDING', 'Breeding', 'See [Breeding, shearing & drops]($gameplay/farming).'),
    ('DROPS', 'Drops & XP', None),
    ('MOBCAPS', 'Spawning & despawning', None),
    ('SEPARATOR', 'Separator', 'See [Pets & mounts]($gameplay/pets_and_mounts#the-separator-item).'),
    ('REGIONS', 'Regions', 'See [Private regions]($regions/private_regions).'),
]
ENUM_VALUES = {
    'StackMode': '`off`, `regions`, `players`, `everywhere`',
    'MobListMode': '`blacklist`, `whitelist`',
    'StackAi': '`normal`, `physics_only`, `frozen`',
    'StackColor': 'any of the 16 [text colours]($stacking/names_and_colours)',
}
VANILLA_CAPS = {
    'monsterMobCap': 70, 'creatureMobCap': 10, 'ambientMobCap': 15, 'axolotlsMobCap': 5,
    'undergroundWaterCreatureMobCap': 5, 'waterCreatureMobCap': 5, 'waterAmbientMobCap': 20,
}
MAX_INT = 'Integer.MAX_VALUE'


def number(text):
    return '2147483647' if text == MAX_INT else text


def default_of(item):
    value = item['default']
    if item['kind'] == 'Enum':
        value = value.split('.')[-1]
    value = value.strip('"')
    return 'empty' if value == '' else '`' + value + '`'


def values_of(item):
    kind = item['kind']
    if kind == 'Bool':
        return '`true`, `false`'
    if kind == 'Enum':
        return ENUM_VALUES.get(item['enum'], 'see description')
    if kind in ('Int', 'Double'):
        low, high = number(item['min']), number(item['max'])
        return '%s to %s' % (low, high)
    if item['id'] == 'separatorItem':
        return 'an item id'
    return 'text'


def description_of(item):
    text = item['description'].replace('|', '\\|').replace('<', '&lt;').replace('{', '&#123;').replace('}', '&#125;')
    notes = []
    if item.get('requires'):
        notes.append('Needs `%s`.' % item['requires'])
    if item.get('lockedOnBy'):
        notes.append('Forced on while `%s` is on.' % item['lockedOnBy'])
    if item.get('redundantWhen'):
        notes.append('Does nothing while `%s` is on.' % item['redundantWhen'])
    return ' '.join([text] + notes)


items = [json.loads(line) for line in open(sys.argv[1], encoding='utf-8') if line.strip()]
by_category = {}
for item in items:
    by_category.setdefault(item['category'], []).append(item)

out = []
out.append('# All settings')
out.append('')
out.append('Every setting, as the config screen groups them. Change one with')
out.append('`/mobstacker set <setting> <value>` or in the [config screen]($reference/config_screen), and see one')
out.append('with `/mobstacker get <setting>`. `/mobstacker reset <setting>` (or the value `default`) puts it')
out.append('back, and wherever a whole number is asked for, `max` means as high as it goes.')
out.append('')
out.append('**Per region**: whether a [region can set its own value]($regions/region_settings) with')
out.append('`/mobstacker region set <region> <setting> <value>`.')
out.append('')
out.append('> [!NOTE]')
out.append('> The deny and allow mob lists and the stack ceilings per mob type are not settings of this kind:')
out.append('> see [Which mobs stack]($stacking/which_mobs_stack) and')
out.append('> [How stacks form]($stacking/how_stacks_form#how-big-a-stack-may-grow).')
for key, title, intro in CATEGORIES:
    rows = by_category.pop(key, [])
    if not rows:
        continue
    out.append('')
    out.append('## ' + title)
    out.append('')
    if intro:
        out.append(intro)
        out.append('')
    if key == 'MOBCAPS':
        out.append('Vanilla\'s spawn caps, each as a setting (global only), with every mob in a stack counted towards')
        out.append('them, and what despawns. See [For server owners]($reference/server_owners#spawning-and-despawning).')
        out.append('')
    out.append('| Setting | Default | Values | Per region | Description |')
    out.append('|---|---|---|---|---|')
    for item in rows:
        out.append('| `%s` | %s | %s | %s | %s |' % (
            item['id'], default_of(item), values_of(item),
            'no' if item['globalOnly'] else 'yes', description_of(item)))
if by_category:
    sys.exit('categories not placed: %s' % ', '.join(by_category))
out.append('')
out.append('*This page is generated from the mod\'s settings registry (%d settings).*' % len(items))
open(sys.argv[2], 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print('wrote %d settings' % len(items))
