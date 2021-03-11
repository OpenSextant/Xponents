"""

Created on Dec 5, 2017

@author: ubaldino
"""

if __name__ == '__main__':
    from opensextant import load_provinces, get_province, load_countries, countries_by_iso

    print("Loading")
    load_provinces()
    load_countries()

    tests = [('ES', '07'), ('US', '06'), ('NOTHING', 'X')]
    for prov in tests:
        pl = get_province(prov[0], prov[1])
        if pl:
            print(prov, pl.name, pl.country_code)
        else:
            print(prov, "Not found")

    print("Kosovo is a country? sovereign country? yes")
    assert countries_by_iso.get("XK") is not None
