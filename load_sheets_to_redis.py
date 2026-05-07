# load_excel_to_redis.py
# Reads data from local Excel file and stores it in Redis

import pandas as pd
import redis
import json
import sys
import os
from pathlib import Path

# ====================== CONFIGURATION ======================

# Path to your local Excel file
EXCEL_FILE_PATH = '/Users/rshoemake/Downloads/DummyProductData.xlsx'   # ← CHANGE THIS if needed (supports full path)

# Exactly two sheet names (tabs) you want to load
TABS_TO_LOAD = ['Products_100', 'Products_10k']               # Change as needed, e.g. ['100', '100k']

# Redis connection settings (for local Docker Redis)
REDIS_HOST = 'localhost'
REDIS_PORT = 6379
REDIS_DB = 0

# =========================================================

def main():
    # 1. Check if Excel file exists
    excel_path = Path(EXCEL_FILE_PATH)
    if not excel_path.exists():
        print(f"❌ Excel file not found: {excel_path}")
        print("   Please update EXCEL_FILE_PATH in the script with the correct location.")
        sys.exit(1)
    
    print(f"✅ Found Excel file: {excel_path}")

    # 2. Connect to Redis
    try:
        r = redis.Redis(
            host=REDIS_HOST,
            port=REDIS_PORT,
            db=REDIS_DB,
            decode_responses=True
        )
        r.ping()
        print(f"✅ Connected to Redis at {REDIS_HOST}:{REDIS_PORT}")
    except redis.ConnectionError:
        print("❌ Could not connect to Redis.")
        print("   Make sure Redis is running:")
        print("   docker run -d -p 6379:6379 --name local-redis redis")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Redis connection error: {e}")
        sys.exit(1)

    # 3. Load selected sheets from Excel into Redis
    print("\n📤 Loading data from Excel into Redis...\n")

    loaded = []

    for sheet_name in TABS_TO_LOAD:
        try:
            print(f"📥 Reading sheet: {sheet_name}")
            
            # Read the sheet
            df = pd.read_excel(EXCEL_FILE_PATH, sheet_name=sheet_name)
            
            if df.empty:
                print(f"   ⚠️  Sheet '{sheet_name}' is empty - skipping")
                continue
                
            row_count = len(df)
            print(f"   Loaded {row_count:,} rows with {len(df.columns)} columns")
            
            # Convert to list of dicts
            data = df.to_dict('records')
            
            # Save to Redis
            redis_key = f"products:{sheet_name}"
            r.set(redis_key, json.dumps(data, ensure_ascii=False, default=str))
            
            print(f"   ✅ Stored {row_count:,} rows → Redis key: {redis_key}")
            loaded.append(sheet_name)
            
        except ValueError as e:
            if "No sheet named" in str(e):
                print(f"   ❌ Sheet '{sheet_name}' not found in the Excel file")
            else:
                print(f"   ❌ Error reading sheet '{sheet_name}': {e}")
        except Exception as e:
            print(f"   ❌ Unexpected error with sheet '{sheet_name}': {e}")

    # Summary
    print("\n" + "="*60)
    if loaded:
        print(f"🎉 SUCCESS! Loaded {len(loaded)} sheet(s): {loaded}")
    else:
        print("⚠️  No sheets were loaded.")

    # Quick Redis verification
    print("\n🔍 Current data in Redis:")
    try:
        keys = r.keys("products:*")
        if keys:
            for key in sorted(keys):
                data_str = r.get(key)
                count = len(json.loads(data_str)) if data_str else 0
                print(f"   • {key} → {count:,} rows")
        else:
            print("   No product keys found.")
    except Exception as e:
        print(f"   Could not verify Redis keys: {e}")

    print("\nDone.")


if __name__ == "__main__":
    main()
