# -*- coding: utf-8 -*-
#
"""
OBSOLETE -- as of 2021 this site no longer operates this way.


World Factbook harvest: This script collects world leaders "Chiefs of State (COS)"
from the WFB.  The intent is to gather various well-known named entities and have them
available to either tag any document or to negate geotags that tag a person's name as a geographic place.

   'George Washington'  --> not Washington, DC. or Washington State.

Mechanics:
- harvest world-leaders page, archive JSON version of that content (using BeautifulSoup4)
- Use taxcat_wfb.py to ingest item(s).
- Over time you may run this and gather significant updates year to year as the information changes
  CACHE past runs.
  TODO: fold together cached harvests and fold into a consolidated picture of all leaders over time for a given country.


OUTPUT:

    {
      "title_orig": "First Deputy CEO",
      "name": "Kyle Frentice",
      "official_title": "First Deputy CEO"
      "personal_title" : "Dr."
    },

"""
import json
import os

import arrow
import bs4
import requests
from opensextant import load_countries
from opensextant.utility import squeeze_whitespace

countries = load_countries()

base_url = "https://www.cia.gov/library/publications/resources/world-leaders-1/{}.html"
master = {}

abbreviated_titles = {
    "Adm.": "Admiral",
    "Admin.": "Administrative",
    "Asst.": "Assistant",
    "Brig.": "Brigadier",
    "Capt.": "Captain",
    "Cdr.": "Commander",
    "Cdte.": "Comandante",
    "Chmn.": "Chair",
    "Col.": "Colonel",
    "Ctte.": "Committee",
    "Del.": "Delegate",
    "Dep.": "Deputy",
    "Dept.": "Department",
    "Dir.": "Director",
    "Div.": "Division",
    "Dr.": "Doctor",
    "Eng.": "Engineer",
    "Fd. Mar.": "Field Marshal",
    "Fed.": "Federal",
    "Gen.": "General",
    "Govt.": "Government",
    "Intl.": "International",
    "Lt.": "Lieutenant",
    "Maj.": "Major",
    "Mar.": "Marshal",
    "Mbr.": "Member",
    "Min.": "Minister",
    "NDE": "No Diplomatic Exchange",
    "Org.": "Organization",
    "Pres.": "President",
    "Prof.": "Professor",
    "RAdm.": "Rear Admiral",
    "Ret.": "Retired",
    "Sec.": "Secretary",
    "VAdm.": "Vice Admiral",
    "VMar.": "Vice Marshal"
}

title_key = {}
for k in abbreviated_titles:
    title_key[k.lower()] = abbreviated_titles.get(k)


def expand_title(t):
    """
    Given a title, attempt to
    :param t:
    :return:
    """
    parts = t.split(' ')
    buf = []

    for tok in parts:
        if k in title_key:
            repl = title_key.get(k)
            buf.append(repl)
        else:
            buf.append(tok)

    return ' '.join(buf)


for C in countries:
    if not C.cc_iso2:
        continue

    url = base_url.format(C.cc_iso2)
    html = requests.get(url, verify=False)
    doc = bs4.BeautifulSoup(html.content, features="lxml")
    # text = doc.get_text()
    print("Country", C)
    cos = doc.find_all("div", attrs={"id": "chiefsOutput"})
    rows = []
    for chief in cos:
        row = dict()
        col = chief.find("span", attrs={"class": "title"})
        row["title_orig"] = col.text
        col = chief.find("span", attrs={"class": "cos_name"})
        row["name"] = col.text
        for k in row:
            val = squeeze_whitespace(row.get(k).strip())
            row[k] = val.replace(' ,', ',')
        row["official_title"] = expand_title(row['title_orig'])
        if "," in row["name"]:
            a, b = row["name"].split(",", 1)
            row["personal_title"] = b.strip()
            row["name"] = a.strip()
        rows.append(row)

    master[C.cc_iso2] = rows

print("------------DEPRECATED---------------")
print("Script runs from ./solr folder, exports JSON result to ./etc/taxcat/data")
print("""Run this to update content periodically -- but in aggregating all updates, 
    you should be resolving duplicates before entering into TaxCat""")
ymd = arrow.utcnow().format("YYYY-MM-DD")
target = os.path.join("etc", "taxcat", "data", "wfb-leaders-by-country-{}.json".format(ymd))
with open(target, "w", encoding="UTF-8") as fh:
    json.dump(master, fh, indent=2)
