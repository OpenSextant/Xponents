from opensextant import location_accuracy

"""
  - a coordinate with one decimal precision, that is very confident
  - a coordinate with six decimals of precision, that is very confident
  - a coordinate with twelve decimals of precision that was formatted with default floating point precision 
  - a small city with a common name, where we are not very confident it is right
  - a landmark or park
  - mention of a large country or region
"""

# Confidence for found coordinates is a default 90 (of 100) becuase formats are so specific
# We usually know its a coordinate and not something else.

print("""
Important -- Look at the comments in code for each example. In the output see that 
the ACCURACY column is a result that typicallylands between 0.01 and 0.30 (on a 0.0 to 1.0 scale). 
This makes it easy to compare and visualize any geographic entity that has been inferred.

Accuracy 1.0 = 100% confident with a 1 meter of error.

""")
print("EXAMPLES ....................\tACCURACY\tCONF\tPREC_ERR")

def print_example(ex, confidence=-1, prec_err=-1):
    acc = location_accuracy(confidence, prec_err)
    print(ex, f"\t{acc:0.3f}\t{confidence}\t{prec_err}")

print_example("Coord - 31.1N x 117.0W.          ", confidence=90, prec_err=10000)
print_example("Coord - 31.123456N x 117.098765W.", confidence=90, prec_err=10)
# This example I through in because I do hate to see scientific data with floating point precision that looks
# like a default C sprintf '%f' print.  9 or 12 decimal places on a lat/lon is not that helpful. So
# at your discretion you can adjust the precision error to what you think precision is... GPS=10m or less?
print_example("Coord - 31.123456789012N x 117.098765432101W.", confidence=90, prec_err=100)

# A good sized city is +/-5KM error.  But high confidence if qualified by a province.
print_example("City Euguene, Oregon  ..........", confidence=85, prec_err=5000)

# A generic village name may be amgiguous and low confidence, but high precision.
print_example("Poblacion, ... Philippines  ....", confidence=25, prec_err=1000)

# Cobbler's shop of a former vice president.  https://www.natickhistoricalsociety.org/henry-wilson. 50x50 foot plot.
print_example("Workshop of H. Wilson, Natick   ", confidence=95, prec_err=10)

# Name of a Laotian province.  error is +/-50KM
print_example(".....Khammouane.....in Laos.....", confidence=60, prec_err=50000)