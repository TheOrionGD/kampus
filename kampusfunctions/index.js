const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Triggered on new event publishing to broadcast data-only push notification to all students.
 */
exports.sendEventNotification = functions.database
    .ref("/events/{eventId}")
    .onCreate(async (snapshot, context) => {
        const eventData = snapshot.val();
        if (!eventData) return null;

        const eventId = context.params.eventId || eventData.id || "";
        const collegeName = eventData.college || "Kampus";
        const eventTitle = eventData.title || "New Event";
        const category = eventData.category || "General";
        const deadline = eventData.deadline || "Soon";
        const eventDate = eventData.eventDate || "";
        const startTime = eventData.startTime || eventData.time || "10:00 AM";
        const description = eventData.fullDescription || eventData.description || "";
        const posterUrl = eventData.posterUrl || "";

        // DATA-ONLY HIGH-PRIORITY FCM payload for reliable client-side handling across all app states
        const message = {
            topic: "college_events",
            android: {
                priority: "high",
                ttl: 86400, // 24 hours in seconds
            },
            data: {
                type: "NEW_EVENT",
                eventId: String(eventId),
                title: String(eventTitle),
                college: String(collegeName),
                category: String(category),
                deadline: String(deadline),
                eventDate: String(eventDate),
                startTime: String(startTime),
                description: String(description),
                posterUrl: String(posterUrl),
            },
        };

        try {
            await admin.messaging().send(message);
            console.log(`[KampusFCM] Successfully dispatched data-only FCM for event ${eventId} to college_events topic`);
            return null;
        } catch (error) {
            console.error("[KampusFCM] Error dispatching push notification:", error);
            return null;
        }
    });

/**
 * HTTPS endpoint to directly trigger data-only push notification for newly published events from backend/MongoDB.
 */
exports.publishEventPushNotification = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        return res.status(405).send("Method Not Allowed");
    }

    const { eventId, title, category, deadline, eventDate, startTime, college, description, posterUrl } = req.body || {};
    if (!title) {
        return res.status(400).send("Missing event title");
    }

    const message = {
        topic: "college_events",
        android: {
            priority: "high",
            ttl: 86400,
        },
        data: {
            type: "NEW_EVENT",
            eventId: String(eventId || ""),
            title: String(title || ""),
            college: String(college || "Kampus"),
            category: String(category || "General"),
            deadline: String(deadline || "Soon"),
            eventDate: String(eventDate || ""),
            startTime: String(startTime || "10:00 AM"),
            description: String(description || ""),
            posterUrl: String(posterUrl || ""),
        },
    };

    try {
        const response = await admin.messaging().send(message);
        console.log(`[KampusFCM] HTTPS endpoint dispatched data-only FCM: ${response}`);
        return res.status(200).json({ success: true, messageId: response });
    } catch (error) {
        console.error("[KampusFCM] HTTPS push dispatch error:", error);
        return res.status(500).json({ success: false, error: error.message });
    }
});
