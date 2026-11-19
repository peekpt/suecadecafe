"""Centro da primeira carta jogável da mão: as ilegais não têm onClick.
Uso: adb -s <serial> shell cat /sdcard/h.xml | python3 tools/cartas.py
"""
import re
import sys

alvos = []
for no in re.finditer(r'<node ([^>]*)/?>', sys.stdin.read()):
    a = dict(re.findall(r'(\w[\w-]*)="([^"]*)"', no.group(1)))
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', a.get('bounds', ''))
    if a.get('clickable') == 'true' and m:
        x1, y1, x2, y2 = map(int, m.groups())
        if 1500 < y1 < 2000 and 100 < x2 - x1 < 200:
            alvos.append((y1, x1, (x1 + x2) // 2, (y1 + y2) // 2))

if alvos:
    x, y = min(alvos)[2:]
    print(x, y)
