from opensextant.gazetteer import gaz_resource, AdminLevelCodes
from opensextant import get_csv_reader, load_us_provinces

# Load a mapping of US FIPS:ISO codings
us_states = {"f": {}, "i": {}}
for us_adm1 in load_us_provinces():
    us_states["i"][us_adm1.adm1_postalcode] = us_adm1.adm1
    us_states["f"][us_adm1.adm1] = us_adm1.adm1_postalcode

COMPONENTS = {
    # Generated using gaz_nga.py Countries.txt --adm1
    "nga_v2021": (gaz_resource("nga_2021_admin1_mapping.csv"), "FIPS=default"),

    # Generated using gaz_nga.py Whole_World.txt --adm1
    "nga_v2022": (gaz_resource("nga_2022_admin1_mapping.csv"), "ISO=default"),

    # Generated using SQLite export noted above, then ingesting with this script with `xponents` cmd
    "geonames": (gaz_resource("geonames_admin1_mapping.csv"), "FIPS=default;ISO=US,BE,ME,CH")
}
STD_SHORTHAND = {"FIPS": "f", "ISO": "i"}


def standard_spec(std):
    specs = []
    standards = std.split(";")
    for s in standards:
        name, behavior = s.split("=", 1)
        spec = {"name": name, "countries": set(behavior.split(","))}
        specs.append(spec)
    return specs


def choose_standard(specs, cc):
    """
    parse the standards rules above and return the appropriate standard ID given the exception rules.
    :param specs:
    :param cc:
    :return:
    """
    if len(specs) == 1:
        spec = specs[0]
        nm = spec["name"]
        return STD_SHORTHAND.get(nm)

    default_nm = None
    for spec in specs:
        cset = spec["countries"]
        if "default" in cset:
            default_nm = spec["name"]
        if cc in cset:
            nm = spec["name"]
            return STD_SHORTHAND.get(nm)
    return STD_SHORTHAND.get(default_nm)


def generate_global():
    country_registry = AdminLevelCodes()

    for source in COMPONENTS:
        fpath, std = COMPONENTS[source]
        specs = standard_spec(std)
        print(f"Loading FILE={fpath}, with specs {specs}")
        with open(fpath, "r", encoding="UTF-8") as fio:
            reader = get_csv_reader(fio, columns=["ADM1", "PLACE_ID", "LAT", "LON", "NAME"], delim="\t")
            for row in reader:
                plid = row["PLACE_ID"]
                grid = ",".join((row["LAT"], row["LON"]))
                hasc = row["ADM1"]
                if "." not in hasc:
                    continue
                cc, adm1 = hasc.split(".")
                std = choose_standard(specs, cc)
                country_registry.add_country(cc)

                # Add place, coord
                country_registry.add_place(plid, cc, std, adm1, grid)
                if cc == "US":
                    # Above ISO entry for US should have been done.
                    alt_adm1 = us_states["i"].get(adm1)
                    if alt_adm1:
                        country_registry.add_place(plid, cc, "f", alt_adm1, grid)
                    else:
                        print("Missing US code for", adm1)

    country_registry.align_admin1()
    country_registry.save(gaz_resource('global_admin1_mapping.json'))


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser(usage="Export the combined Global ADMIN1 codes")
    generate_global()
