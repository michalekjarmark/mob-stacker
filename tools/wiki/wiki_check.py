"""Checks the wiki pages: MDX-unsafe characters outside code, and $page#anchor links.

Usage: python tools/wiki/wiki_check.py wiki/docs
"""
import os
import re
import sys

root = sys.argv[1]
pages, problems = {}, []

for folder, _, files in os.walk(root):
    for name in files:
        if name.endswith('.mdx'):
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, root).replace(os.sep, '/')[:-4]
            pages[rel] = open(path, encoding='utf-8').read()


def slug(heading):
    text = re.sub(r'[`*_]', '', heading).strip().lower()
    text = re.sub(r'[^a-z0-9 -]', '', text)
    return re.sub(r' ', '-', text)


anchors = {}
for rel, text in pages.items():
    anchors[rel] = {slug(m.group(1)) for m in re.finditer(r'^#{1,6} (.+)$', text, re.M)}

for rel, text in pages.items():
    in_block = False
    for number, line in enumerate(text.split('\n'), 1):
        if line.startswith('```'):
            in_block = not in_block
            continue
        if in_block:
            continue
        bare = re.sub(r'`[^`]*`', '', line)
        bare = re.sub(r'\]\([^)]*\)', ']()', bare)
        for char in '<{}':
            if char in bare:
                problems.append('%s:%d: "%s" outside code: %s' % (rel, number, char, line.strip()))
        for link in re.findall(r'\]\(\$([^)#]+)(?:#([^)]+))?\)', line):
            page, anchor = link
            if page not in pages:
                problems.append('%s:%d: no page %s' % (rel, number, page))
            elif anchor and anchor not in anchors[page]:
                problems.append('%s:%d: no heading #%s in %s (has: %s)' % (
                    rel, number, anchor, page, ', '.join(sorted(anchors[page]))))

print('%d pages checked' % len(pages))
print('\n'.join(problems) if problems else 'no problems')
