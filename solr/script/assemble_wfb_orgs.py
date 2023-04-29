# -*- coding: utf-8 -*-
#
"""
World Factbook harvest: This script collects international organizational names, acronyms and aliases.
from the WFB.  The intent is to gather various well-known named entities and have them
available to either tag any document or to negate geotags that tag an organization as a geographic place.

   'Detroit City Council' -- an organizational name that contains a city. Tagging the location and organization
   is considered mutually exclusive.  To dually tag or triply tag an span of text (exact matches or sub-matches)
   is a wholly different task.  These mechanics are left to the analytic -- this script only harvests the content.

Mechanics:
- harvest international orgs page, archive JSON version of that content (using BeautifulSoup4)
- Use taxcat_wfb.py to ingest item(s).
- Over time you may run this and gather significant updates year to year as the information changes
  CACHE past runs.
  TODO: fold together cached harvests and fold into a consolidated picture of all orgs over time for a given country.

"""
import json
import os
import re

import arrow
import bs4
import requests

pat = re.compile(r'\(([-/A-Z0-9 ]{2,15})\)', re.UNICODE | re.IGNORECASE)


def parse_aliases(nm):
    """
    Look for short abbreviations in parantheticals. 2-15 chars long.
    :param nm:
    :return:
    """
    alist = []
    for m in pat.finditer(nm):
        alist.append(m.group(1))
    return alist


base_url = "https://www.cia.gov/library/publications/resources/the-world-factbook/appendix/appendix-b.html"
master = {}
load_cached = True
cached_file = '/tmp/wfb-orgs.html'
html = None

if __name__ == "__main__":

    print("Web site no longer exists -- proceed with caution -- WFB was migrated to a new portal")
    if load_cached and os.path.exists(cached_file):
        with open(cached_file, 'r', encoding="UTF-8") as fh:
            html = fh.read()
    else:
        html_response = requests.get(base_url, verify=True)
        html = html_response.content
        with open('/tmp/wfb-orgs.html', 'w', encoding="UTF-8") as fh:
            fh.write(html.decode("UTF-8"))

    doc = bs4.BeautifulSoup(html, features="lxml")

    master = []
    for chx in range(ord('a'), ord('z')):
        ch = chr(chx)
        node_class = "appendix-entry reference-content ln-{}".format(ch)
        for entry in doc.find_all("div", attrs={"class": node_class}):
            org_name = entry.find("div", attrs={"class": "appendix-entry-name category"})
            org_desc = entry.find("div", attrs={"class": "appendix-entry-text category_data"})
            if org_name:
                k = org_name.text.strip()
                row = {"name": k, "description": org_desc.text.strip()}
                if "(" in k:
                    row["aliases"] = parse_aliases(k)
                    row["name"] = k.split("(")[0].strip()
                master.append(row)

    print("Script runs from ./solr folder, exports JSON result to ./etc/taxcat/data")
    print("""Run this to update content periodically -- but in aggregating all updates, 
        you should be resolving duplicates before entering into TaxCat""")
    ymd = arrow.utcnow().format("YYYY-MM-DD")
    target = os.path.join("etc", "taxcat", "data", "wfb-orgs-{}.json".format(ymd))
    with open(target, "w", encoding="UTF-8") as fh:
        json.dump({"orgs_and_groups": master}, fh, indent=2)
