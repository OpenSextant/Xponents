from opensextant.CommonsUtils import fast_replace


test = fast_replace("Bloody mess, it is.", ".", ' ')
print(test)
test = fast_replace("9.9.9.3.1.3-321", ".", '-')
print(test)
cmp = fast_replace("9.9.9.3.1.3-321", ".", '')
cmp1 = fast_replace("9.9.9.3.1.3-321", ".")
cmp2 = fast_replace("9.9.9.3.1.3-321", ".", sub='')
assert(cmp == cmp1 == cmp2)