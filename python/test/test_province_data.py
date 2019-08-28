"""

Created on Dec 5, 2017

@author: ubaldino
"""

if __name__ == '__main__':
    from opensextant.GeonamesUtility import load_provinces, get_province

    print("Loading")
    load_provinces()

    tests = [('ES', '07'), ('US', '06'), ('NOTHING', 'X')]
    for prov in tests:
        pl = get_province(prov[0], prov[1])
        if pl:
            print(prov, pl.name, pl.country_code)
        else:
            print(prov, "Not found")
