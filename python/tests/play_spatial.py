import sys
from argparse import ArgumentParser

from time import time as now
from opensextant import Coordinate, Place
from opensextant.gazetteer import DB

ap = ArgumentParser()
ap.add_argument("db", help="sqlite database")
ap.add_argument("--latlon", help="comma-separated lat,lon")
args = ap.parse_args()


def msg(m):
    print(f"\n{m}")


def deltat(tm):
    sec = now() - tm
    print(f"{sec:0.4} seconds")


def print_loc(pl: Place, distance):
    print("Distance", distance, "Grid", geo.geohash, "Place:", pl)


# db = DB(get_default_db())
db = DB(args.db)
if args.latlon:
    ll = args.latlon.split(",")
    coord = Coordinate(None, lat=ll[0], lon=ll[1])
    print("Your query ---", coord)
    t0 = now()
    for dist, geo in db.list_places_at(lat=coord.lat, lon=coord.lon):
        print_loc(geo, dist)
    deltat(t0)

    sys.exit()

gh = "9q5fp"

print("\n====================")
print("Location", gh, "Los Angeles County search against proximal geohash cells")
t0 = now()
locations = db.list_places_at(geohash=gh, radius=2000)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)

print("Time: ", now() - t0)

t0 = now()
locations = db.list_places_at(geohash=gh, radius=500)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)
print("Time: ", now() - t0)

gh = "9rupu"
# print("v1 bad version\n============")
# t0 = arrow.now()
# locations = db.list_places_at_v1(geohash=gh, radius=5000)
# print("Found", len(locations))
# for dist, geo in locations:
#    print_loc(geo, dist)
""" NOTA BENE:  When searching geohash by prefix -- even a coarse enough prefix -- you
still frequently run into issues at the edges of cells. 

9rup* borders c2h0*  -- certain cells as seen below are just within 5 KM, yet
they have very different prefixes.

============
Found 10
Distance 898 Grid 9rupum Place: Smith McPhee Ditch, US @(44.9848638,-117.9329947)
Distance 917 Grid 9rupuk Place: Kelsey Wilson Ditch, US @(44.9831971,-117.9360504)
Distance 1230 Grid 9rupud Place: Inman Reservoir, US @(44.968899,-117.9181418)
Distance 2266 Grid 9rupgu Place: Hutchinson Hill, US @(44.9784748,-117.9557728)
Distance 3793 Grid 9rupvd Place: Jacobsen Ditch, US @(44.9698656,-117.8802168)
Distance 3928 Grid c2h0h6 Place: North Powder Pond Number One, US @(45.0132047,-117.9305386)
Distance 3977 Grid c2h0j1 Place: North Powder Valley, US @(45.00792,-117.89938)
"""

print("Time: ", now() - t0)

print("v2 good version\n============")
t0 = now()
locations = db.list_places_at(geohash=gh, radius=5000)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)

print("Time: ", now() - t0)

t0 = now()
gh = "9q5fpg"
# 9qh5... is next to 9q5f...
print("\n====================")
print("Location", gh, "Los Angeles County search against proximal geohash cells at 6, 5, 4...")
locations = db.list_places_at(geohash=gh, radius=10000, limit=5)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)
print("Time: ", now() - t0)

# So look for places close to POI ~ "9q5fpg" but now in 9q5f* other cells.
locations = db.list_places_at(geohash=gh, radius=10000, limit=5)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)
print("Time: ", now() - t0)

# 9q% search:
# So look for places close to POI ~ "9q5fpg" but now in 9q* other cells.
# Notice RADIUS is smaller, because its likely 9qh places are closer than other 9q5 places.
print("\n====================")
print(" Find places in 9q* that are within 2KM of the point", gh)
locations = db.list_places_at(geohash=gh, radius=2000, limit=100)
print("Found", len(locations))
for dist, geo in locations:
    print_loc(geo, dist)
print("Time: ", now() - t0)

# Otherwise....
print("Spatial Queries for Places near a Lat/Lon")
# sample geohash query
gh = "dr21ry"

t0 = now()

print("\n====================")
print("Location", gh)
locations = db.list_places_at(geohash=gh)
for dist, geo in locations:
    print_loc(geo, dist)

print("Time: ", now() - t0)
t0 = now()

print("\n====================")
print("Location", gh, "Country = CA")
locations = db.list_places_at(geohash=gh, cc="CA")
for dist, geo in locations:
    print_loc(geo, dist)

print("Time: ", now() - t0)

t0 = now()
print("\n====================")
print("Location", gh, "Country = US, 1 KM")
locations = db.list_places_at(geohash=gh, cc="US", radius=1000)
for dist, geo in locations:
    print_loc(geo, dist)
print("Time: ", now() - t0)
