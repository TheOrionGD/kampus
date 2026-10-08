"""
Database Management Script for Kampus MongoDB Atlas
Run this script to wipe all collections or inspect data status in MongoDB Atlas.
"""

import os
import pymongo

URI = "mongodb+srv://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@hellotheoriongd.rbxbuxe.mongodb.net"
DB_NAME = "Kampus"

def clear_all_data():
    print(f"Connecting to MongoDB Atlas database '{DB_NAME}'...")
    client = pymongo.MongoClient(URI, serverSelectionTimeoutMS=10000)

    try:
        client.admin.command('ping')
        print("Connected successfully!")
        
        db = client[DB_NAME]
        collections = db.list_collection_names()
        
        if not collections:
            print(f"No collections found in database '{DB_NAME}'. Database is empty.")
            return

        print(f"Found collections: {collections}\nClearing data...")
        
        for col_name in collections:
            col = db[col_name]
            count_before = col.count_documents({})
            col.delete_many({})
            count_after = col.count_documents({})
            print(f" - Collection '{col_name}': {count_before} documents deleted ({count_after} remaining).")
            
        print("\nAll database data has been successfully cleared!")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    clear_all_data()
