"""Reads MobStackerSettings.java and prints every registered setting as JSON lines.

Fields: id, kind (Bool/Int/Double/Enum/String), category, description, default, min, max,
enum class, dependencies (requires / lockedOnBy / redundantWhen), and whether it is global only.
"""
import json
import re
import sys

SRC = sys.argv[1]
text = open(SRC, encoding='utf-8').read()


def split_args(s):
    """Split a Java argument list at top-level commas (strings, parens, braces aware)."""
    out, depth, cur, i, in_str = [], 0, [], 0, None
    while i < len(s):
        c = s[i]
        if in_str:
            cur.append(c)
            if c == '\\':
                cur.append(s[i + 1])
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c in '"\'':
            in_str = c
            cur.append(c)
        elif c in '([{':
            depth += 1
            cur.append(c)
        elif c in ')]}':
            depth -= 1
            cur.append(c)
        elif c == ',' and depth == 0:
            out.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    if cur:
        out.append(''.join(cur).strip())
    return out


def java_string(expr):
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', expr)
    return ''.join(p.replace('\\"', '"').replace('\\\\', '\\') for p in parts)


def matching_paren(s, start):
    depth, i, in_str = 0, start, None
    while i < len(s):
        c = s[i]
        if in_str:
            if c == '\\':
                i += 2
                continue
            if c == in_str:
                in_str = None
        elif c in '"\'':
            in_str = c
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError('unbalanced')


global_only = set(re.findall(r'"(\w+)"', re.search(r'GLOBAL_ONLY\s*=\s*java\.util\.Set\.of\(([^;]*)\);', text, re.S).group(1)))

for m in re.finditer(r'register\(ConfigOption\.of(\w+)\(', text):
    kind = m.group(1)
    open_paren = m.end() - 1
    close = matching_paren(text, open_paren)
    args = split_args(text[open_paren + 1:close])
    # The chained calls after the factory, up to the register's own closing paren.
    reg_close = matching_paren(text, m.start() + len('register'))
    chain = text[close + 1:reg_close]
    item = {
        'id': java_string(args[0]),
        'kind': kind,
        'category': args[1].replace('Category.', ''),
        'description': java_string(args[2]),
        'default': args[-1],
    }
    if kind in ('Int', 'Double'):
        item['min'], item['max'] = args[3], args[4]
    if kind == 'Enum':
        item['enum'] = args[3].replace('.class', '')
    for shape in ('requires', 'lockedOnBy', 'redundantWhen'):
        dep = re.search(r'\.' + shape + r'\("(\w+)"\)', chain)
        if dep:
            item[shape] = dep.group(1)
    item['globalOnly'] = item['id'] in global_only
    print(json.dumps(item, ensure_ascii=False))
