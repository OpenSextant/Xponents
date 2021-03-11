from opensextant.gazetteer import DB
from arrow import now
db = DB(dbpath="tmp/master_gazetteer_010.sqlite")

plid = "N123456"
t1 = now()
for x in range(0,1000):
    g = db.get_places_by_id2(plid)
t2 = now()
print(t2-t1)

t1 = now()
for x in range(0,1000):
    g = db.get_places_by_id(plid)
t2 = now()
print(t2-t1)
