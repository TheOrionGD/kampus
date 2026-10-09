const { MongoClient, ObjectId } = require('mongodb');
const https = require('https');

const MONGODB_URI = "mongodb://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@ac-vyntutv-shard-00-00.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-01.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-02.rbxbuxe.mongodb.net:27017/Kampus?ssl=true&replicaSet=atlas-trie3b-shard-0&authSource=admin&retryWrites=true&w=majority&appName=hellotheOrionGD";
const RENDER_BACKEND_URL = "https://kampus-notification-server.onrender.com";

async function testFacultyCreateHackathonEvent() {
    console.log("==================================================");
    console.log("🚀 Testing Production Faculty Event Creation & FCM Trigger...");
    console.log("==================================================");

    const client = new MongoClient(MONGODB_URI);
    try {
        await client.connect();
        const db = client.db("Kampus");
        console.log("✅ Successfully connected to MongoDB Atlas 'Kampus' database");

        // 1. Construct Hackathon Event Document
        const eventId = "evt_hackathon_" + Date.now();
        const hackathonEvent = {
            _id: eventId,
            id: eventId,
            title: "Kampus National Innovation Hackathon 2026",
            college: "Kampus Institute of Technology",
            category: "Hackathon",
            deadline: "12/10/2026",       // Registration ends 12th October
            eventDate: "21/10/2026",      // Conducted on 21st October
            startTime: "09:00 AM",
            endTime: "06:00 PM",
            mode: "Offline / Hybrid",
            eligibility: "All Computer Science & Engineering Students",
            fee: "Free",
            coordinatorName: "Prof. Ananya Sharma",
            coordinatorRole: "Assistant Professor & Event Convener (CSE)",
            postedTime: "Just now",
            announcementNote: "Official verified faculty hackathon event post.",
            fullDescription: "Join the flagship Kampus National Innovation Hackathon 2026! Build cutting-edge AI, Mobile, and Cloud solutions. Top teams win mentorship and cash prizes.",
            prizePool: "₹1,50,000 Cash Prizes & Trophy Certificates",
            targetDept: "Computer Science & Engineering",
            posterUrl: "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?auto=format&fit=crop&w=1000&q=80",
            externalRegLink: "https://kampus.edu/hackathon-2026",
            customField1Label: "Team Size",
            customField1Value: "2-4 Members",
            customField2Label: "Tracks",
            customField2Value: "AI / ML, Android Apps, Web3, Cloud Security",
            status: "PUBLISHED",
            createdByRole: "FACULTY",
            createdAt: new Date()
        };

        // 2. Upsert into MongoDB 'events' collection
        const eventsCol = db.collection("events");
        await eventsCol.replaceOne({ _id: eventId }, hackathonEvent, { upsert: true });
        console.log(`✅ Saved Hackathon event to MongoDB Atlas 'events' collection (ID: ${eventId})`);

        // 3. Save In-App Notification history for student users
        const notifsCol = db.collection("notifications");
        const studentNotification = {
            _id: "notif_" + Date.now(),
            id: "notif_" + Date.now(),
            userId: "ALL",
            userRole: "STUDENT",
            title: "🎉 New Event: Kampus National Innovation Hackathon 2026",
            message: "Registration deadline: 12/10/2026. Hackathon date: 21/10/2026. Prize Pool: ₹1,50,000!",
            type: "EVENT_PUBLISHED",
            eventId: eventId,
            isRead: false,
            createdAt: Date.now()
        };
        await notifsCol.replaceOne({ _id: studentNotification._id }, studentNotification, { upsert: true });
        console.log("✅ Saved in-app broadcast notification for student users to MongoDB");

        // 4. Trigger FCM Push Notification via Production Render Backend URL
        console.log(`\n📡 Triggering FCM push notification via Render backend: ${RENDER_BACKEND_URL}/api/notifications/notify-event ...`);
        
        const payload = JSON.stringify({
            eventId: eventId,
            title: hackathonEvent.title,
            body: hackathonEvent.fullDescription
        });

        const url = new URL(RENDER_BACKEND_URL + "/api/notifications/notify-event");
        const options = {
            hostname: url.hostname,
            port: 443,
            path: url.pathname,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
                'X-User-Id': 'faculty@kampus.edu',
                'X-User-Role': 'FACULTY'
            }
        };

        const req = https.request(options, (res) => {
            let responseData = '';
            res.on('data', chunk => responseData += chunk);
            res.on('end', () => {
                console.log(`📡 Render Server Response Code: ${res.statusCode}`);
                console.log(`📡 Render Server Response Payload: ${responseData}`);
                console.log("\n==================================================");
                console.log("🎉 TEST COMPLETE: Faculty Hackathon Event Created & FCM Triggered!");
                console.log("==================================================\n");
                client.close();
            });
        });

        req.on('error', (err) => {
            console.error(`❌ Render notification trigger HTTP error: ${err.message}`);
            client.close();
        });

        req.write(payload);
        req.end();

    } catch (err) {
        console.error("❌ Error during test execution:", err);
        client.close();
    }
}

testFacultyCreateHackathonEvent();
