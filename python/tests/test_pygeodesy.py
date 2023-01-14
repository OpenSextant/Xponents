
from pygeodesy.ellipsoidalVincenty import LatLon as VicentyLL
found = {}
ctr = VicentyLL(44.4, -113.00)
print(ctr)
sw, ne =  ctr.boundsOf(1000 ,1000)
print(sw, ne)