# -*- coding: utf-8 -*-
from opensextant.utility import fast_replace, levenshtein_distance, has_cjk, has_arabic, trivial_bias, replace_diacritics

test = fast_replace("Bloody mess, it is.", ".", ' ')
print(test)
test = fast_replace("9.9.9.3.1.3-321", ".", '-')
print(test)
cmp = fast_replace("9.9.9.3.1.3-321", ".", '')
cmp1 = fast_replace("9.9.9.3.1.3-321", ".")
cmp2 = fast_replace("9.9.9.3.1.3-321", ".", sub='')
assert (cmp == cmp1 == cmp2)

print("Edit Distances")
assert (levenshtein_distance("pizza", "pizze") == 1)
assert (levenshtein_distance("pepperoni pizza", "pupparoni pizze") == 3)

print("Text has Chinese.")
assert has_cjk("该比赛上，太平洋联盟队以3比5负于中央联盟队") == True
print("Text has Middle-eastern / Arabic/Farsi content:")
assert has_arabic("أخبارطهران / 5 اذار /مارس /ارنا- صرح وزير الخارجية الايراني محمد جواد ظريف انه سيعلن قريبا عن خطوة ايران البناءة ") == True

# Diacritic Name that has odd quote at end:
nm = 'Gáza kormányzóság‘´'
print(trivial_bias(nm))

print(nm, "=>", replace_diacritics(nm))
