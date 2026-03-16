import json
import os
import time
import psycopg2

DB_HOST = os.environ.get("DB_HOST", "sanctions-db")
DB_PORT = os.environ.get("DB_PORT", "5432")
DB_NAME = os.environ.get("DB_NAME", "sanctions")
DB_USER = os.environ.get("DB_USER", "sanctions_user")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "sanctions_pass")
DATA_PATH = "/data/Cleaned-Sanctions.json"

def wait_for_db(max_retries=10, delay=2):
    for attempt in range(max_retries):
        try:
            conn = psycopg2.connect(
                host=DB_HOST, port=DB_PORT,
                dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
            )
            conn.close()
            print("Database is ready.")
            return
        except psycopg2.OperationalError:
            print(f"Waiting for database (attempt {attempt + 1}/{max_retries})...")
            time.sleep(delay)
    raise RuntimeError("Could not connect to database after retries.")

def load_sanctions():
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT,
        dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*) FROM sanctioned_individuals")
    count = cur.fetchone()[0]
    if count > 0:
        print(f"Database already contains {count} records. Skipping load.")
        cur.close()
        conn.close()
        return

    with open(DATA_PATH, "r") as f:
        sanctions = json.load(f)

    insert_sql = """
        INSERT INTO sanctioned_individuals
            (name, nationality, gender, dob, position, sanctions, sanction_creator, reason, other_info)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    rows = []
    for record in sanctions:
        rows.append((
            record.get("name"),
            record.get("nationality"),
            record.get("gender"),
            record.get("D.O.B"),
            record.get("position"),
            record.get("sanctions"),
            record.get("sanction creator"),
            record.get("reason"),
            record.get("other information"),
        ))

    cur.executemany(insert_sql, rows)
    conn.commit()
    print(f"Loaded {len(rows)} sanctioned individuals into database.")

    cur.close()
    conn.close()

if __name__ == "__main__":
    wait_for_db()
    load_sanctions()
