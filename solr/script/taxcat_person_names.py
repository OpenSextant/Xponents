from opensextant.TaxCat import TaxCatalogBuilder, Taxon


def create_entity(name):
    """
    Create a generic person name taxon, rather than a particular personality/celebrity
    """
    taxon = Taxon()
    n = name.strip().lower()
    taxon.name = 'person_name.{}'.format(n)
    taxon.phrase = n
    taxon.is_valid = True
    taxon.tags = []

    return taxon


data_sets = [
    {
        'source': 'census',
        'path': './etc/gazetteer/filters/person-name-filter.txt'
    },
    {
        'source': 'adhoc',
        'path': './etc/gazetteer/filters/exclude-adhoc-names.txt'
    }
]


def index_names(taxcat, fpath, cat, tag, rownum):
    with open(fpath, 'r', encoding="UTF-8") as fh:
        for row in fh:
            if row.startswith("#") or not row.strip():
                continue
            rownum = rownum + 1
            entity = create_entity(row)
            entity.id = rownum
            entity.tags.append(tag)
            taxcat.add(cat, entity)
            if rownum % 100 == 0:
                print("Row # ", rownum)
        taxcat.save(flush=True)
        taxcat.optimize()

    return rownum


if __name__ == '__main__':
    """
    taxcat_person_names.py --solr URL --names FILE
    """
    catalog_id = 'person_names'

    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument('--names')
    ap.add_argument('--starting-id', default=0)
    ap.add_argument('--solr')

    args = ap.parse_args()

    print(f"""
    TaxCat Builder for Taxonomy: {args.names}
    """)

    test = False
    row_max = -1
    builder = TaxCatalogBuilder(server=args.solr)
    builder.commit_rate = 100

    row_id = int(args.starting_id)

    for cfg in data_sets:
        row_id = index_names(builder, cfg['path'], catalog_id, cfg['source'], row_id)
