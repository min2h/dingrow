#!/usr/bin/env python3
"""Append KO/EN strings to every catalog (L10nTables.kt + shared/compose XML). Usage: addstrings.py file.tsv
TSV: key<TAB>ko<TAB>en. Existing keys are replaced."""
import sys, re, html
root = '/workspace/dingrow/'
rows = []
for ln in open(sys.argv[1], encoding='utf-8'):
    ln = ln.rstrip('\n')
    if not ln.strip() or ln.startswith('#'): continue
    k, ko, en = ln.split('\t')
    rows.append((k, ko, en))
def kt_esc(v): return v.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$')
def xml_esc(v):
    v = html.escape(v, quote=False).replace("'", "\\'").replace('"', '\\"')
    return v
p = root + 'shared/src/commonMain/kotlin/app/mongle/l10n/L10nTables.kt'
s = open(p, encoding='utf-8').read()
en_start = s.index('internal val En'); ko_start = s.index('internal val Ko')
def upsert(block, k, v):
    pat = re.compile(r'    put\("' + re.escape(k) + r'", ".*"\)\n')
    line = f'    put("{k}", "{kt_esc(v)}")\n'
    if pat.search(block): return pat.sub(lambda m: line, block, count=1)
    i = block.rindex('}')
    return block[:i] + line + block[i:]
en = s[en_start:ko_start]; ko = s[ko_start:]
for k, kv, ev in rows:
    en = upsert(en, k, ev); ko = upsert(ko, k, kv)
open(p, 'w', encoding='utf-8').write(s[:en_start] + en + ko)
for base in ['shared/src/commonMain/resources/l10n', 'composeApp/src/commonMain/composeResources']:
    for d, idx in [('values', 2), ('values-ko', 1)]:
        f = root + f'{base}/{d}/strings.xml'
        x = open(f, encoding='utf-8').read()
        for r in rows:
            k = r[0]; v = xml_esc(r[idx])
            pat = re.compile(r'    <string name="' + re.escape(k) + r'">.*</string>\n')
            line = f'    <string name="{k}">{v}</string>\n'
            if pat.search(x): x = pat.sub(lambda m: line, x, count=1)
            else: x = x.replace('</resources>', line + '</resources>')
        open(f, 'w', encoding='utf-8').write(x)
print('added', len(rows))
