const { MongoClient } = require('mongodb');

const MONGODB_URI = "mongodb://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@ac-vyntutv-shard-00-00.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-01.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-02.rbxbuxe.mongodb.net:27017/Kampus?ssl=true&replicaSet=atlas-trie3b-shard-0&authSource=admin&retryWrites=true&w=majority&appName=hellotheOrionGD";

async function seedDatabaseUsers() {
    console.log("==================================================");
    console.log("🌱 Seeding Kampus Platform Users into MongoDB Atlas...");
    console.log("==================================================");

    const client = new MongoClient(MONGODB_URI);

    try {
        await client.connect();
        const db = client.db("Kampus");
        console.log("✅ Connected to MongoDB Atlas 'Kampus' Database\n");

        // 1. Seed Super Admin
        const superAdmins = [
            {
                _id: "admin@kampus.com",
                email: "admin@kampus.com",
                password: "admin123",
                name: "Super Administrator",
                role: "SUPER_ADMIN",
                profilePhotoUri: "",
                headline: "Kampus Super Administrator",
                about: "Platform administrator responsible for verifying colleges and managing the Kampus governance hierarchy.",
                lastUpdated: Date.now()
            },
            {
                _id: "admin@kampus.edu",
                email: "admin@kampus.edu",
                password: "admin123",
                name: "Super Administrator",
                role: "SUPER_ADMIN",
                profilePhotoUri: "",
                headline: "Kampus Super Administrator",
                about: "Platform administrator responsible for verifying colleges and managing the Kampus governance hierarchy.",
                lastUpdated: Date.now()
            }
        ];

        const adminsCol = db.collection("admins");
        for (const adminDoc of superAdmins) {
            await adminsCol.replaceOne({ _id: adminDoc._id }, adminDoc, { upsert: true });
            console.log(`👤 Seeded Super Admin: ${adminDoc.email} / ${adminDoc.password}`);
        }

        // 2. Seed College Admin & Dept Faculty
        const faculties = [
            {
                _id: "collegeadmin@kampus.edu",
                collegeEmail: "collegeadmin@kampus.edu",
                name: "Dr. Rajesh Kumar",
                password: "admin123",
                collegeName: "Kampus Institute of Technology",
                department: "Administration",
                designation: "Dean / College Admin",
                contactNumber: "9876543210",
                collegeWebsite: "https://kampus.edu",
                accreditation: "NAAC A++",
                collegePhotoUri: "",
                campusLayoutUri: "",
                role: "COLLEGE_ADMIN",
                isVerifiedBySuperAdmin: true,
                isVerifiedByCollegeAdmin: true,
                profilePhotoUri: "",
                headline: "Dean & College Administrator",
                about: "Overseeing academic excellence and campus event governance."
            },
            {
                _id: "faculty@kampus.edu",
                collegeEmail: "faculty@kampus.edu",
                name: "Prof. Ananya Sharma",
                password: "faculty123",
                collegeName: "Kampus Institute of Technology",
                department: "Computer Science & Engineering",
                designation: "Assistant Professor & Event Convener",
                contactNumber: "9876543211",
                collegeWebsite: "https://kampus.edu",
                accreditation: "NAAC A++",
                collegePhotoUri: "",
                campusLayoutUri: "",
                role: "DEPT_FACULTY",
                isVerifiedBySuperAdmin: true,
                isVerifiedByCollegeAdmin: true,
                profilePhotoUri: "",
                headline: "Assistant Professor & Event Convener (CSE)",
                about: "Coordinating hackathons, symposiums, and student tech clubs."
            }
        ];

        const facultiesCol = db.collection("college_faculties");
        const collegesCol = db.collection("colleges");

        for (const facDoc of faculties) {
            await facultiesCol.replaceOne({ _id: facDoc._id }, facDoc, { upsert: true });
            if (facDoc.role === "COLLEGE_ADMIN") {
                await collegesCol.replaceOne({ _id: facDoc._id }, facDoc, { upsert: true });
            }
            console.log(`👨‍🏫 Seeded ${facDoc.role}: ${facDoc.collegeEmail} / ${facDoc.password}`);
        }

        // 3. Seed Students
        const students = [
            {
                _id: "student1@kampus.edu",
                email: "student1@kampus.edu",
                name: "Aravind Swaminathan",
                password: "student123",
                college: "Kampus Institute of Technology",
                department: "Computer Science & Engineering",
                year: "3rd Year",
                skills: ["Kotlin", "Android", "UI/UX", "Python"],
                headline: "Android Developer & AI Enthusiast",
                about: "Passionate CS student competing in hackathons and symposiums.",
                profilePhotoUri: "",
                coverPhotoUri: "",
                githubLink: "https://github.com",
                isOpenToWork: true,
                phoneNumber: "9876543220"
            },
            {
                _id: "student2@kampus.edu",
                email: "student2@kampus.edu",
                name: "Priya Natarajan",
                password: "student123",
                college: "Kampus Institute of Technology",
                department: "Information Technology",
                year: "Final Year",
                skills: ["React", "Node.js", "Cloud", "Cybersecurity"],
                headline: "Full-stack Developer & Tech Lead",
                about: "Building real-world web and mobile applications.",
                profilePhotoUri: "",
                coverPhotoUri: "",
                githubLink: "https://github.com",
                isOpenToWork: false,
                phoneNumber: "9876543221"
            }
        ];

        const studentsCol = db.collection("students");
        for (const studDoc of students) {
            await studentsCol.replaceOne({ _id: studDoc._id }, studDoc, { upsert: true });
            console.log(`👨‍🎓 Seeded Student: ${studDoc.email} / ${studDoc.password}`);
        }

        console.log("\n==================================================");
        console.log("🎉 ALL SEEDED USERS SUCCESSFULLY CREATED IN MONGODB ATLAS!");
        console.log("==================================================\n");

    } catch (err) {
        console.error("❌ Error seeding database:", err);
    } finally {
        await client.close();
    }
}

seedDatabaseUsers();
