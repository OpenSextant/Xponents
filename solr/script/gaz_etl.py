# EXPERIMENTAL
# This block of countries and territories have the most significant
# number of ADM1 level coding issues betweeen FIPS and ISO and between major sources
# such as Geonames.org and NGA
ADM1_MISALIGNED_COUNTRIES = [
    'BF', 'DO', 'EE', 'FR', 'GB',
    'GN', 'GP', 'GR', 'HK', 'IE',
    'IS', 'LT', 'MA', 'MG', 'MW',
    'NO', 'NP', 'PR', 'PS', 'RE', 'RS', 'TW', 'UG']

TOP_TIER_ADMIN_CODES = {'ADM1', 'TERR', 'PRSH'}

def is_valid_admin(cc, dsg):
    """
    Determine if the top level designation code is good for the given country.
    :param cc:
    :param dsg:
    :return:
    """
    if dsg in TOP_TIER_ADMIN_CODES or (dsg == "ADMD" and cc in ADM1_MISALIGNED_COUNTRIES):
        return True

    # Consider other ADMIN features later...
    return dsg in TOP_TIER_ADMIN_CODES

