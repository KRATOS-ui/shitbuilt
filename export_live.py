import argparse, datetime as dt, decimal, os, sys, uuid
import psycopg2, psycopg2.extras
from pymongo import MongoClient

def conv(v):
    if isinstance(v, uuid.UUID): return str(v)
    if isinstance(v, decimal.Decimal): return float(v)
    if isinstance(v, dt.datetime): return v.replace(tzinfo=None) if v.tzinfo is None else v
    if isinstance(v, dt.date): return dt.datetime(v.year, v.month, v.day)
    if isinstance(v, memoryview): return bytes(v)
    return v

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--pg-host", default=os.getenv("DB_HOST", "localhost"))
    p.add_argument("--pg-port", default=os.getenv("DB_PORT", "5432"))
    p.add_argument("--pg-db", default=os.getenv("DB_NAME", "saas_calendar"))
    p.add_argument("--pg-user", default=os.getenv("DB_USER", "root"))
    p.add_argument("--pg-password", default=os.getenv("DB_PASSWORD", "rootpassword"))
    p.add_argument("--mongo-uri", default=os.getenv("MONGO_URI", "mongodb://localhost:27017"))
    p.add_argument("--mongo-db", default=os.getenv("MONGO_DB", "syncflow"))
    p.add_argument("--drop", action="store_true")
    a = p.parse_args()

    pg = psycopg2.connect(host=a.pg_host, port=a.pg_port, dbname=a.pg_db,
                          user=a.pg_user, password=a.pg_password)
    pg.set_session(isolation_level="REPEATABLE READ", readonly=True)
    mdb = MongoClient(a.mongo_uri)[a.mongo_db]

    with pg, pg.cursor() as cur:
        cur.execute("""SELECT table_name FROM information_schema.tables
                       WHERE table_schema='public' AND table_type='BASE TABLE'
                       ORDER BY table_name""")
        tables = [r[0] for r in cur.fetchall()]
        print("Tablas encontradas:", ", ".join(tables))
        for t in tables:
            with pg.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as c:
                c.execute(f'SELECT * FROM "{t}"')
                docs = []
                for row in c:
                    d = {k: conv(v) for k, v in row.items()}
                    if "id" in d: d = {"_id": d.pop("id"), **d}
                    docs.append(d)
            if a.drop: mdb[t].drop()
            if docs: mdb[t].insert_many(docs)
            print(f"  {t}: {len(docs)} documentos")

    idx = {"users": [("email", True)], "recurrence_rules": [("event_id", True)],
           "memberships": [("user_id", False), ("organization_id", False)],
           "invite_codes": [("code", False)],
           "events": [("created_by", False), ("organization_id", False)],
           "event_assignments": [("event_id", False), ("user_id", False)],
           "checklist_items": [("event_id", False)],
           "timeline_entries": [("event_id", False)]}
    for t, cols in idx.items():
        if t in tables:
            for col, uniq in cols: mdb[t].create_index(col, unique=uniq)
    print("Listo. La base PostgreSQL sigue intacta y en marcha.")

if __name__ == "__main__":
    sys.exit(main())