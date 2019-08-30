from opensextant.Extraction import TextMatch
from opensextant.FlexPat import class_for, resource_for, RegexPatternManager, RegexPattern, PatternTestCase

# ========================
# Basic Text span logic
# ========================
#
print("Text Span logic")
# Testing: Hold m1 constant. Move m2 around and test.
m1 = TextMatch("fluff", 4, 9)
m2 = TextMatch("nutter", 5, 8)

assert m1.contains(m2)

m2 = TextMatch("nutter", 0, 3)
assert m1.is_after(m2)

m2 = TextMatch("nutter", 3, 8)
assert m1.overlaps(m2)

# Boundary conditions
m2 = TextMatch("nutter", 9, 15)
assert m1.overlaps(m2)

m2 = TextMatch("nutter", 0, 4)
assert m1.overlaps(m2)

m2 = TextMatch("nutter", 4, 9)
assert m1.exact_match(m2)

# Boundary conditions
m2 = TextMatch("nutter", 22, 28)
assert m1.is_before(m2)

print(str(m2))

m2 = TextMatch("nüttér", 22, 28)
print(str(m2))
print(len(m2.text), "chars long")



# ========================
# File loader mechanics.
# ========================
#
print("Resource Loader mechanics")
cls = class_for("opensextant.FlexPat.RegexPattern", instantiate=False)
# Returns an instance, not just valid class name.
assert cls is not None
print("Invoke Class:", cls.__class__.__name__,  "Str():", cls)
cls_instance = cls("a", "b", "c")
print("Instatiated:",  cls_instance.__class__.__name__, cls_instance)

fpath = resource_for("poli_patterns.cfg")
assert fpath is not None
print("Found resource", fpath)

print("FlexPat configuration stuff - basic parsing and init")

from opensextant.extractors.poli import TelephoneNumber
num = TelephoneNumber(" 987-654-4321 X x  ", 0, 14, label="TELEPHONE")
print("BEFORE",str(num))
num.normalize()
print("AFTER", num.textnorm)


from opensextant.extractors.poli import PatternsOfLifeManager
from opensextant.FlexPat import PatternExtractor

mgr = PatternsOfLifeManager("poli_patterns.cfg")
patternsApp = PatternExtractor(mgr)

test_results = patternsApp.default_tests()
print("TEST RESULTS")
for result in test_results:
    print(repr(result))

print("Generic Test on nothing trying to run every pattern, default is 'all'")
real_results = patternsApp.extract(".... text blob 1-800-123-4567...")
print("TEST RESULTS")
for result in real_results:
    print(repr(result))
