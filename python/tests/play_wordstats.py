from opensextant.wordstats import WordStats

# Must run from ./solr/ folder
stats = WordStats('tmp/wordstats.sqlite')

print("Does Alabama appear 10K or more times? - YES")
result =  stats.find("alabama", threshold=10000)
print(result)
assert len(result) == 1

print("Does Alabama appear 10mil or more times? - NO")
result =  stats.find("alabama", threshold=10000000)
print(result)
assert len(result) == 0

print("Does bi-gram 'San Francisco' appear? - NO.  No bi-grams tracked")
result =  stats.find("san francisco", threshold=5000000)
print(result)
assert len(result) == 0

# Common City names
for term in ["dublin", "florence", "warren", "york", "columbia", "washington" ]:
    result =  stats.find(term, threshold=500000)
    print(result)

# Common terms
for term in ["surprise", "land", "liberty"]:
    result =  stats.find(term, threshold=1000000)
    print(result)
