from opensextant.gazetteer import PlaceHeuristics, get_default_db, DB

estimator = PlaceHeuristics(DB(get_default_db()))

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
