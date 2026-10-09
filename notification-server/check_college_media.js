const { MongoClient } = require('mongodb');

const MONGODB_URI = "";

async function checkCollegeMedia() {
    console.log("==================================================");
    console.log("🔍 Checking Campus Media in MongoDB Atlas...");
    console.log("==================================================");

    const client = new MongoClient(MONGODB_URI);

    try {
        await client.connect();
        const db = client.db("Kampus");
        console.log("✅ Connected to MongoDB Atlas 'Kampus' Database\n");

        // 1. Query 'colleges' collection
        console.log("--------------------------------------------------");
        console.log("📦 Collection: 'colleges'");
        console.log("--------------------------------------------------");
        const colleges = await db.collection("colleges").find({}).toArray();
        if (colleges.length === 0) {
            console.log("No documents found in 'colleges' collection.");
        } else {
            colleges.forEach((c, idx) => {
                console.log(`[College #${idx + 1}] ID: ${c._id}`);
                console.log(`  College Name: ${c.collegeName || c.name || "N/A"}`);
                console.log(`  College Email: ${c.collegeEmail || c.email || "N/A"}`);
                console.log(`  📸 Campus Entrance Banner Photo URL (collegePhotoUri): "${c.collegePhotoUri || c.collegePhoto || ""}"`);
                console.log(`  🗺️ Campus Blueprint Layout Map URL (campusLayoutUri): "${c.campusLayoutUri || c.campusLayout || ""}"`);
                console.log(`  Role: ${c.role || "COLLEGE_ADMIN"}`);
                console.log(`  Last Updated: ${c.lastUpdated ? new Date(c.lastUpdated).toISOString() : "N/A"}\n`);
            });
        }

        // 2. Query 'college_faculties' collection
        console.log("--------------------------------------------------");
        console.log("📦 Collection: 'college_faculties'");
        console.log("--------------------------------------------------");
        const faculties = await db.collection("college_faculties").find({}).toArray();
        if (faculties.length === 0) {
            console.log("No documents found in 'college_faculties' collection.");
        } else {
            faculties.forEach((f, idx) => {
                console.log(`[Faculty #${idx + 1}] ID: ${f._id}`);
                console.log(`  Name: ${f.name}`);
                console.log(`  Email: ${f.collegeEmail}`);
                console.log(`  Role: ${f.role}`);
                console.log(`  📸 Campus Entrance Banner Photo URL (collegePhotoUri): "${f.collegePhotoUri || ""}"`);
                console.log(`  🗺️ Campus Blueprint Layout Map URL (campusLayoutUri): "${f.campusLayoutUri || ""}"`);
                console.log(`  👤 Profile Photo URL: "${f.profilePhotoUri || ""}"\n`);
            });
        }

        console.log("==================================================");
        console.log("✅ Database Check Completed Successfully");
        console.log("==================================================");

    } catch (err) {
        console.error("❌ Error querying MongoDB Atlas:", err.message);
    } finally {
        await client.close();
    }
}

checkCollegeMedia();
