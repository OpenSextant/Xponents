# -*- coding: utf-8 -*-
import os, sys,csv
import traceback
reload(sys)
sys.setdefaultencoding('utf-8')

print "Note - run from ./solr/ folder" 
print "Ingesting filter files"

GAZ_CONF = './etc/gazetteer'
# The resulting name filter file:
output = os.path.join(GAZ_CONF, 'filters/person-name-filter.txt')

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
fpath =   os.path.join(GAZ_CONF, 'filters/census/Names_2010Census.csv')
TOP_N=1500
f1 =  os.path.abspath( fpath )
if os.path.exists(f1):
    print "\tParse ", f1
    with open(f1, 'rb') as fh:
        counter=0
        csvh = csv.reader(fh, delimiter=',')
        for row in csvh:
            if not is_comment(row[0]):
                names.add(unicode(row[0]).strip().lower())
                counter += 1
                if counter > TOP_N: 
                    print ("Found top N names")
                    break
else:
   print "Census data not present:", fpath

#
fpath = os.path.join(GAZ_CONF, 'filters/census/dist.male.first')
f1 =  os.path.abspath( fpath )
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    # csvh = csv.reader(fh, delimiter='')
    for line in fh:
        row = line.split() 
        name = row[0].lower()
        if not is_comment(name) and len(name)>2:
            names.add(unicode(name))

    fh.close()
else:
   print "Census data not present:", fpath

fpath = os.path.join(GAZ_CONF, 'filters/census/dist.female.first')
f1 =  os.path.abspath( fpath )
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    # csvh = csv.reader(fh, delimiter='')
    for line in fh:
        row = line.split() 
        name = row[0].lower()
        if not is_comment(name) and len(name)>2:
            names.add(unicode(name))

    fh.close()
else:
   print "Census data not present:", fpath
    
# Other useful names to use as not-place entries.
#
f1 = os.path.abspath(os.path.join(GAZ_CONF, 'filters/exclude-adhoc-names.txt'))
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    for row in fh:
        if not is_comment(row):
            names.add(unicode(row).strip().lower())
    fh.close()


# a list of valid known places.  Remove known places from name filter.
# 
f1 = os.path.abspath( os.path.join(GAZ_CONF, 'filters/include-adhoc-places.txt' ))
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    for row in fh:
        if not is_comment(row):
            k = unicode(row).strip().lower()
            if k in names: names.remove(k)
    fh.close()

#
if names:

    # manual deletes, while these signify Person names, they also collide with general geographic cues
    # not enough terms that are both locations, person names, and confounded terms to warrant separate data sets.
    try:
        names.remove('hall')
        names.remove('many')
        names.remove('zona')
    except Exception, err:
        print "===========ERROR=========="
        print "Something is wrong with the data.  Check any downloaded files"
        print traceback.format_exc(limit=5)
        print "===========ERROR=========="

    fh = open(output, 'wb')
    fh.write('# Generated File:  census surnames + exclusions - inclusions\n')
    name_list = sorted(list(names))
    for n in name_list:
        fh.write(n)
        fh.write('\n')
    fh.close()

print "\tWahoo. Done"
print "\tResulting names filter file is at", os.path.abspath(output)
