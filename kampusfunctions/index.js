const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Triggered on new event publishing to broadcast push notification to all students.
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

        const notificationBody = `${eventTitle}\nCategory: ${category}\nRegistration Deadline: ${deadline}\nEvent Date: ${eventDate}\nTime: ${startTime}`;

        const message = {
            topic: "college_events",
            notification: {
                title: "New Event Published",
                body: `${eventTitle} (${category}) • Deadline: ${deadline}`,
            },
            data: {
                type: "EVENT_PUBLISHED",
                eventId: eventId,
                title: eventTitle,
                category: category,
                deadline: deadline,
                eventDate: eventDate,
                startTime: startTime,
                college: collegeName,
                fullBody: notificationBody,
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "kampus_event_channel_v1",
                    sound: "default",
                },
            },
        };

        try {
            await admin.messaging().send(message);
            console.log("Successfully sent instant event push notification to college_events topic!");
            return null;
        } catch (error) {
            console.error("Error sending push notification:", error);
            return null;
        }
    });

/**
 * HTTPS endpoint to directly trigger push notification for newly published events from backend/MongoDB.
 */
exports.publishEventPushNotification = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        return res.status(405).send("Method Not Allowed");
    }

    const { eventId, title, category, deadline, eventDate, startTime, college } = req.body || {};
    if (!title) {
        return res.status(400).send("Missing event title");
    }

    const notificationBody = `${title}\nCategory: ${category || "General"}\nRegistration Deadline: ${deadline || "Soon"}\nEvent Date: ${eventDate || ""}\nTime: ${startTime || "10:00 AM"}`;

    const message = {
        topic: "college_events",
        notification: {
            title: "New Event Published",
            body: `${title} (${category || "Event"}) • Deadline: ${deadline || "Soon"}`,
        },
        data: {
            type: "EVENT_PUBLISHED",
            eventId: eventId || "",
            title: title || "",
            category: category || "",
            deadline: deadline || "",
            eventDate: eventDate || "",
            startTime: startTime || "10:00 AM",
            college: college || "Kampus",
            fullBody: notificationBody,
        },
        android: {
            priority: "high",
            notification: {
                channelId: "kampus_event_channel_v1",
                sound: "default",
            },
        },
    };

    try {
        const response = await admin.messaging().send(message);
        return res.status(200).json({ success: true, messageId: response });
    } catch (error) {
        console.error("Push notification dispatch error:", error);
        return res.status(500).json({ success: false, error: error.message });
    }
});
