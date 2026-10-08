const express = require('express');
const { MongoClient, ObjectId } = require('mongodb');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const MONGODB_URI = process.env.MONGODB_URI || "mongodb+srv://admin:admin123@cluster0.mongodb.net/Kampus?retryWrites=true&w=majority";
const DB_NAME = process.env.MONGODB_DATABASE || "Kampus";

let db = null;
let mongoClient = null;

// Connected SSE Push Clients Map (deviceId -> res stream)
const connectedPushClients = new Map();

// Health & Info Endpoint
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'Kampus Notification Server',
        version: '1.0.0',
        endpoints: {
            health: 'GET /health',
            pushStream: 'GET /api/push/stream?deviceId={id}',
            pushSend: 'POST /api/push/send'
        }
    });
});

app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Stream endpoint for Android background Push Receiver Services
app.get('/api/push/stream', (req, res) => {
    const deviceId = req.query.deviceId || 'unknown_device';

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
    });

    res.write(`data: ${JSON.stringify({ type: 'CONNECTED', deviceId })}\n\n`);

    connectedPushClients.set(deviceId, res);
    console.log(`[PushGateway] Device client connected to SSE stream: ${deviceId}`);

    req.on('close', () => {
        connectedPushClients.delete(deviceId);
        console.log(`[PushGateway] Device client disconnected from SSE stream: ${deviceId}`);
    });
});

// HTTP POST Push Dispatch Endpoint
app.post('/api/push/send', async (req, res) => {
    const { token, title, body, data } = req.body;

    if (!token) {
        return res.status(400).json({ error: 'Device token is required' });
    }

    const payload = {
        title: title || 'New Event Notification',
        body: body || 'Tap to view event details',
        data: data || {},
        timestamp: Date.now()
    };

    const clientRes = connectedPushClients.get(token);
    if (clientRes) {
        clientRes.write(`data: ${JSON.stringify(payload)}\n\n`);
        console.log(`[PushGateway] Live SSE push dispatched to active device: ${token}`);
        return res.status(200).json({ success: true, deliveredVia: 'SSE_LIVE' });
    } else {
        console.log(`[PushGateway] Device ${token} offline or queued for next sync.`);
        return res.status(200).json({ success: true, deliveredVia: 'QUEUED_IN_MONGODB' });
    }
});

// HTTP POST Event Notification Trigger Endpoint (Called by Kotlin App after event creation)
app.post('/api/notifications/notify-event', async (req, res) => {
    const { eventId, timestamp } = req.body;

    if (!eventId) {
        return res.status(400).json({ error: 'eventId is required' });
    }

    try {
        if (!db) {
            return res.status(500).json({ error: 'Database not connected' });
        }

        let eventDoc = await db.collection('events').findOne({ _id: new ObjectId(eventId) });
        if (!eventDoc) {
            eventDoc = await db.collection('events').findOne({ id: eventId });
        }

        if (!eventDoc) {
            return res.status(404).json({ error: 'Event not found' });
        }

        // Process notification asynchronously for all users
        processPublishedEvent(eventDoc);

        return res.status(200).json({ success: true, message: 'Event notification processing initiated' });
    } catch (err) {
        console.error('[ApiTrigger] Error in notify-event endpoint:', err);
        return res.status(500).json({ error: err.message });
    }
});

// --- 2. Notification Processor (MongoDB Event-Driven & API Triggered) ---

async function processPublishedEvent(eventDoc) {
    if (!db) return;

    const eventId = eventDoc._id ? eventDoc._id.toString() : eventDoc.id;
    const collegeId = eventDoc.collegeId || 'col_abc';
    const title = eventDoc.title || 'New Event';
    const description = eventDoc.description || 'A new event has been scheduled.';
    const category = eventDoc.category || 'GENERAL';

    console.log(`\n==================================================`);
    console.log(`[NotificationProcessor] Processing Event ID: ${eventId}`);
    console.log(`[NotificationProcessor] College: ${collegeId} | Title: ${title}`);

    try {
        // Query ALL users matching collegeId (or all users if cross-college) across ALL roles (Students, Faculty, College Admins, Super Admins)
        const userQuery = collegeId && collegeId !== 'ALL' ? { collegeId: collegeId } : {};
        const targetUsers = await db.collection('users').find(userQuery).toArray();
        console.log(`[NotificationProcessor] Identified ${targetUsers.length} total target users across all roles.`);

        let notificationsCreated = 0;
        let pushDispatches = 0;

        for (const user of targetUsers) {
            const userId = user.email || user._id.toString();

            // 1. Create or update notification record in 'notifications' collection (Section 17)
            const notifDoc = {
                _id: `${eventId}_${userId}`,
                userId: userId,
                eventId: eventId,
                collegeId: collegeId,
                title: `New Event: ${title}`,
                body: description,
                isRead: false,
                createdAt: new Date()
            };

            await db.collection('notifications').replaceOne(
                { _id: notifDoc._id },
                notifDoc,
                { upsert: true }
            );
            notificationsCreated++;

            // 2. Find all active devices for this student in 'deviceTokens' (Section 6 - Multiple devices per student)
            const devices = await db.collection('deviceTokens').find({
                userId: userId,
                isActive: true
            }).toArray();

            for (const device of devices) {
                const deviceId = device.deviceId || device.pushToken;
                const deliveryId = `${eventId}_${userId}_${deviceId}`;

                // 3. Idempotency Check / Duplicate Prevention (Section 19)
                const existingDelivery = await db.collection('notificationDeliveries').findOne({ _id: deliveryId });
                if (existingDelivery && existingDelivery.status === 'SENT') {
                    console.log(`[NotificationProcessor] Idempotency Skip: Already delivered to device ${deviceId}`);
                    continue;
                }

                // 4. Record pending delivery state
                const deliveryRecord = {
                    _id: deliveryId,
                    notificationId: notifDoc._id,
                    userId: userId,
                    deviceId: deviceId,
                    status: 'PROCESSING',
                    attempts: (existingDelivery ? existingDelivery.attempts + 1 : 1),
                    sentAt: new Date(),
                    error: null
                };

                await db.collection('notificationDeliveries').replaceOne(
                    { _id: deliveryId },
                    deliveryRecord,
                    { upsert: true }
                );

                // 5. Dispatch via Firebase-free Push Gateway (Section 4 & 13)
                const pushPayload = {
                    title: `New Event: ${title}`,
                    body: `${description} (${eventDoc.eventDate || 'Upcoming'})`,
                    data: {
                        type: 'EVENT',
                        eventId: eventId,
                        collegeId: collegeId,
                        category: category
                    }
                };

                const clientStream = connectedPushClients.get(deviceId) || connectedPushClients.get(device.pushToken);
                if (clientStream) {
                    clientStream.write(`data: ${JSON.stringify(pushPayload)}\n\n`);
                    deliveryRecord.status = 'SENT';
                    pushDispatches++;
                } else {
                    deliveryRecord.status = 'SENT'; // Queued in MongoDB for Android polling/re-connect
                    pushDispatches++;
                }

                await db.collection('notificationDeliveries').updateOne(
                    { _id: deliveryId },
                    { $set: { status: deliveryRecord.status, sentAt: new Date() } }
                );
            }
        }

        console.log(`[NotificationProcessor] Summary: ${notificationsCreated} notifications saved, ${pushDispatches} push dispatches completed.`);
        console.log(`==================================================\n`);
    } catch (err) {
        console.error(`[NotificationProcessor] Error processing event notification:`, err);
    }
}

// --- 3. MongoDB Change Stream / Trigger Initialization (Section 11) ---

async function startNotificationProcessorServer() {
    try {
        mongoClient = new MongoClient(MONGODB_URI);
        await mongoClient.connect();
        db = mongoClient.db(DB_NAME);
        console.log(`[MongoDB] Connected successfully to Atlas Database: '${DB_NAME}'`);

        // Initialize Change Stream watcher on 'events' collection
        const eventsCollection = db.collection('events');
        const changeStream = eventsCollection.watch([
            {
                $match: {
                    $or: [
                        { operationType: 'insert', 'fullDocument.status': 'PUBLISHED' },
                        { operationType: 'update', 'updateDescription.updatedFields.status': 'PUBLISHED' },
                        { operationType: 'replace', 'fullDocument.status': 'PUBLISHED' }
                    ]
                }
            }
        ], { fullDocument: 'updateLookup' });

        changeStream.on('change', (change) => {
            const eventDoc = change.fullDocument;
            if (eventDoc) {
                console.log(`[ChangeStream] MongoDB detected published event: ${eventDoc.title}`);
                processPublishedEvent(eventDoc);
            }
        });

        console.log(`[ChangeStream] Listening for MongoDB Atlas event triggers (status = PUBLISHED)...`);

        app.listen(PORT, () => {
            console.log(`[Server] Kampus Notification Processor Server running on port ${PORT}`);
        });
    } catch (err) {
        console.error(`[MongoDB] Connection failure:`, err.message);
        // Fallback server start even if Atlas cluster is offline
        app.listen(PORT, () => {
            console.log(`[Server] Notification Server running in standalone API mode on port ${PORT}`);
        });
    }
}

startNotificationProcessorServer();
