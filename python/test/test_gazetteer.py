from opensextant.gazetteer import  DB, get_default_db

db = DB(get_default_db())
names = db.list_admin_names()
print("ADMIN NAMES TOTAL", len(names))
print("First 10", list(names)[0:10])