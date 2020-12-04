# -*- coding: utf-8 -*-
import csv
import os
import sys

from opensextant.CommonsUtils import get_text

print("Note - run from ./solr/ folder\nIngesting filter files")

GAZ_CONF = './etc/gazetteer'
TAX_CONF = './etc/taxcat'
# The resulting name filter file:
output = os.path.join(GAZ_CONF, 'filters/person-name-filter.txt')
nonpersons = os.path.join(TAX_CONF, 'non-person-names.txt')

# All final names
names = set([])


def is_comment(s):
    if not s: return False
    if not s.strip(): return False
    return s.strip()[0] == '#'


# USGS/Census top 1000 surnames
#  To counter-act these names, add entries to  conf/inclusions/adhoc-places.txt
#
# Formerly manual download: filters/census/top1000-surnames-cy2010.csv
fpath = os.path.join(GAZ_CONF, 'filters/census/Names_2010Census.csv')
TOP_N = 1500
f1 = os.path.abspath(fpath)
if os.path.exists(f1):
    print("\tParse ", f1)
    with open(f1, 'r', encoding="UTF-8") as fh:
        counter = 0
        csvh = csv.reader(fh, delimiter=',')
        for row in csvh:
            if not is_comment(row[0]):
                names.add(get_text(row[0]).strip().lower())
                counter += 1
                if counter > TOP_N:
                    print("Found top N names")
                    break
else:
    print("Census data not present:", fpath)

#
fpath = os.path.join(GAZ_CONF, 'filters/census/dist.male.first')
f1 = os.path.abspath(fpath)
if os.path.exists(f1):
    print("\tParse ", f1)
    with open(f1, 'r', encoding="UTF-8") as fh:
        # csvh = csv.reader(fh, delimiter='')
        for line in fh:
            row = line.split()
            name = row[0].lower()
            if not is_comment(name) and len(name) > 2:
                names.add(get_text(name))
else:
    print("Census data not present:", fpath)

fpath = os.path.join(GAZ_CONF, 'filters/census/dist.female.first')
f1 = os.path.abspath(fpath)
if os.path.exists(f1):
    print("\tParse ", f1)
    with open(f1, 'r', encoding="UTF-8") as fh:
        for line in fh:
            row = line.split()
            name = row[0].lower()
            if not is_comment(name) and len(name) > 2:
                names.add(get_text(name))
else:
    print("Census data not present:", fpath)

# Other useful names to use as not-place entries.
#
f1 = os.path.abspath(os.path.join(GAZ_CONF, 'filters/exclude-adhoc-names.txt'))
if os.path.exists(f1):
    print("\tParse ", f1)
    with open(f1, 'r', encoding="UTF-8") as fh:
        for row in fh:
            if not is_comment(row):
                names.add(get_text(row).strip().lower())

# a list of valid known places.  Remove known places from name filter.
# 
f1 = os.path.abspath(os.path.join(GAZ_CONF, 'filters/include-adhoc-places.txt'))
if os.path.exists(f1):
    print("\tParse ", f1)
    with open(f1, 'r', encoding="UTF-8") as fh:
        for row in fh:
            if not is_comment(row):
                k = get_text(row).strip().lower()
                if k in names: names.remove(k)

non_person_names = set([])
with open(nonpersons, 'r', encoding="UTF-8") as fh:
    for nm in fh:
        non_person_names.add(nm.strip().lower())

print("Found non-person names:", len(non_person_names))

#
if not names:
    print("No Names found!!!!")
    sys.exit(-1)

for nm in non_person_names:
    if nm in names:
        names.remove(nm)

print("Resulting Names: {}".format(len(names)))
with open(output, 'w', encoding="UTF-8") as fh:
    fh.write('# Generated File:  census surnames + exclusions - inclusions\n')
    name_list = sorted(list(names))
    for n in name_list:
        fh.write(n)
        fh.write('\n')

print("\tWahoo. Done")
print("\tResulting names filter file is at", os.path.abspath(output))
