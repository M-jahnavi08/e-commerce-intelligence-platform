"""Create the local application database if absent; leave existing data untouched."""
import os
import psycopg
with psycopg.connect(host='127.0.0.1', port=55432, user='commerce', password=os.environ['DATABASE_PASSWORD'], dbname='postgres', autocommit=True) as connection:
    if not connection.execute("SELECT 1 FROM pg_database WHERE datname='commerce'").fetchone():
        connection.execute('CREATE DATABASE commerce')
