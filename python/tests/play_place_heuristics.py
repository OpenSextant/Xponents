from opensextant.gazetteer import PlaceHeuristics, get_default_db, DB

estimator = PlaceHeuristics(DB(get_default_db()))

print("NAME BIASING\n===============")
# Test numerics
term = "01"
nb = estimator.name_bias(term, "A", "ADM4", name_type="N")
print(term, nb)

# Test Abbreviations, Codes and short names
term = "AC"
nb = estimator.name_bias(term, "A", "PCLI", name_type="C")
print(term, nb)

term = "Ac"
nb = estimator.name_bias(term, "P", "PPL", name_type="N")
print(term, nb)

term = "Aç"
nb = estimator.name_bias(term, "P", "PPL", name_type="N")
print(term, nb)

term = "Ac."
nb = estimator.name_bias(term, "P", "PPL", name_type="A")
print(term, nb)

# Florida
# =============
term = "Florida"
nb = estimator.name_bias(term, "P", "PPL", name_type="N")
print(term, nb)
nb = estimator.name_bias(term, "A", "ADM1", name_type="N")
print(term, nb)
term = "Flørida"
nb = estimator.name_bias(term, "A", "ADM1", name_type="N")
print(term, nb)


print("LOCATION BIASING\n===============")

#
# Oddities:
# Florida|N||N|A|ADM3|PH|RP|I7||7.725668|125.66237|wc37gw|0|9|0|0
geo = {"geohash":"wc37gw", "feat_class":"A", "feat_code":"ADM3", "cc":"PH", "adm1":"I7"}
ib = estimator.location_bias(geo)
print("Location for Florida Philippines", ib)

# Test Same Toponym, different locations:
# FLORIDA
geo = {"geohash":"djj7d9vk034g", "feat_class":"A", "feat_code":"ADM1", "cc":"US", "adm1":"FL"}
ib = estimator.location_bias(geo)
print("Location for Florida State", ib)

# 6cbr9bwby4u7
geo = {"geohash":"6cbpx8", "feat_class":"A", "feat_code":"ADM1", "cc":"UY", "adm1":"07"}
ib = estimator.location_bias(geo)
print("Location for Florida province, Uruguay", ib)

# NOTE - LOCATION biasing only works for ADM1, AMD2 and cities.  This location represents "Florida" ADM3 in Paraguay
geo = {"geohash":"6erpg2", "feat_class":"A", "feat_code":"ADM3", "cc":"PA", "adm1":"06", "adm2":"1114"}
ib = estimator.location_bias(geo)
print("Location for Florida province, Paraguay", ib)

# TEST MAJOR CITIES
geo = {"geohash":"drdmvb", "feat_class":"A", "feat_code":"ADM1", "cc":"US", "adm1":"NY"}
ib = estimator.location_bias(geo)
print("Location for NY", ib)

geo = {"geohash":"dr5reg", "feat_class":"P", "feat_code":"PPL", "cc":"US", "adm1":"NY"}
ib = estimator.location_bias(geo)
print("Location for NYC", ib)

