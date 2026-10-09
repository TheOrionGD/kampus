const { MongoClient } = require('mongodb');

const uri = "mongodb://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@ac-vyntutv-shard-00-00.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-01.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-02.rbxbuxe.mongodb.net:27017/Kampus?ssl=true&replicaSet=atlas-trie3b-shard-0&authSource=admin&retryWrites=true&w=majority&appName=hellotheOrionGD";
const dbName = "Kampus";

const PRESERVED_COLLECTIONS = [
  'students',
  'college_faculties',
  'users',
  'colleges'
];

async function clearDataKeepCredentials() {
  const client = new MongoClient(uri);
  try {
    await client.connect();
    console.log(`Connected to MongoDB Atlas: database '${dbName}'`);
    const db = client.db(dbName);

    const collections = await db.listCollections().toArray();
    console.log(`\nFound ${collections.length} collections in '${dbName}':`);
    collections.forEach(c => console.log(` - ${c.name}`));

    console.log(`\nPreserving login credentials in: ${PRESERVED_COLLECTIONS.join(', ')}`);

    for (const colInfo of collections) {
      const colName = colInfo.name;
      if (PRESERVED_COLLECTIONS.includes(colName)) {
        const count = await db.collection(colName).countDocuments();
        console.log(`[PRESERVED] Collection '${colName}': keeping ${count} credential documents intact.`);
      } else {
        const result = await db.collection(colName).deleteMany({});
        console.log(`[CLEARED] Collection '${colName}': deleted ${result.deletedCount} documents.`);
      }
    }

    console.log(`\n✅ Database Cleanup Complete: All non-credential data erased while user login credentials were preserved.`);
  } catch (err) {
    console.error(`❌ Error during database cleanup:`, err.message);
  } finally {
    await client.close();
  }
}

clearDataKeepCredentials();
