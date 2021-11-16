import sys
from argparse import ArgumentParser
from opensextant.gazetteer import  get_default_db, DB
from opensextant import Coordinate

ap = ArgumentParser()
ap.add_argument("db", help="sqlite database")
ap.add_argument("--latlon", help="comma-separated lat,lon")
args = ap.parse_args()

# db = DB(get_default_db())
db = DB(args.db)
if args.latlon:
    ll = args.latlon.split(",")
    coord = Coordinate(None, lat=ll[0], lon=ll[1])
    print("Your query ---", coord)
    for dist, geo in db.list_places_at(lat=coord.lat, lon=coord.lon):
        print("Distance", dist, "Place:", geo)
    
    sys.exit();


# Otherwise....

print("Spatial Queries for Places near a Lat/Lon")
# sample geohash query
gh = "dr21ry"

print("\n====================")
print("Location", gh)
locations = db.list_places_at(geohash=gh)
for dist, geo in locations:
    print("Distance", dist, "Place:", geo)

print("\n====================")
print("Location", gh, "Country = CA")
locations = db.list_places_at(geohash=gh, cc="CA")
for dist, geo in locations:
    print("Distance", dist, "Place:", geo)

print("\n====================")
print("Location", gh, "Country = US, 1 KM")
locations = db.list_places_at(geohash=gh, cc="US", radius=1000)
for dist, geo in locations:
    print("Distance", dist, "Place:", geo)
