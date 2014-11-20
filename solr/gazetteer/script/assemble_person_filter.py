import os, sys,csv
reload(sys)
sys.setdefaultencoding('utf-8')

print "Note - run from ./solr/gazetteer/ folder" 

print "Ingesting filter files"

# The resulting name filter file:
output = 'conf/filters/person-name-filter.txt'

# All final names
names = set([])

def is_comment(s):
  if not s: return False
  return s.strip()[0] == '#'


# USGS/Census top 1000 surnames
#  To counter-act these names, add entries to  conf/inclusions/adhoc-places.txt
#
f1 =  os.path.abspath( 'conf/filters/census-gov-top1000-surnames-cy2010.csv' )
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    csvh = csv.reader(fh, delimiter=',')
    for row in csvh:
        if not is_comment(row[0]):
            names.add(unicode(row[0]).strip().lower())
    fh.close()
    
# Other useful names to use as not-place entries.
#
f1 = os.path.abspath( 'conf/filters/exclude-adhoc-names.txt' )
if os.path.exists(f1):
    print "\tParse ", f1
    fh = open(f1, 'rb')
    for row in fh:
        if not is_comment(row):
            names.add(unicode(row).strip().lower())
    fh.close()


# a list of valid known places.  Remove known places from name filter.
# 
f1 = os.path.abspath( 'conf/filters/include-adhoc-places.txt' )
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
    fh = open(output, 'wb')
    name_list = sorted(list(names))
    for n in name_list:
        fh.write(n)
        fh.write('\n')
    fh.close()


print "\tWahoo. Done"
print "\tResulting names filter file is at", os.path.abspath(output)
