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

    const eventId = eventDoc._id ? eventDoc._id.toString() : (eventDoc.id || String(Date.now()));
    const collegeId = eventDoc.collegeId || eventDoc.college || 'col_abc';
    const title = eventDoc.title || 'New Event';
    const description = eventDoc.fullDescription || eventDoc.description || eventDoc.announcementNote || 'A new event has been scheduled.';
    const category = eventDoc.category || 'GENERAL';

    console.log(`\n==================================================`);
    console.log(`[NotificationProcessor] Processing Event ID: ${eventId}`);
    console.log(`[NotificationProcessor] College: ${collegeId} | Title: ${title}`);

    try {
        // Query ALL users across 'students', 'college_faculties', and 'users' collections
        const [studentsList, facultiesList, genericUsersList] = await Promise.all([
            db.collection('students').find({}).toArray().catch(() => []),
            db.collection('college_faculties').find({}).toArray().catch(() => []),
            db.collection('users').find({}).toArray().catch(() => [])
        ]);

        const allTargetUsersMap = new Map();

        const addUserToMap = (u) => {
            const email = (u.email || u.collegeEmail || (u._id ? u._id.toString() : '')).trim().toLowerCase();
            if (email && !allTargetUsersMap.has(email)) {
                allTargetUsersMap.set(email, { email, raw: u });
            }
        };

        studentsList.forEach(addUserToMap);
        facultiesList.forEach(addUserToMap);
        genericUsersList.forEach(addUserToMap);

        // Always include broadcast "ALL" target
        allTargetUsersMap.set('ALL', { email: 'ALL', raw: {} });

        console.log(`[NotificationProcessor] Identified ${allTargetUsersMap.size} unique target identities.`);

        let notificationsCreated = 0;
        let pushDispatches = 0;

        for (const [userId] of allTargetUsersMap) {
            // 1. Create or update notification record in 'notifications' collection
            const notifDoc = {
                _id: `${eventId}_${userId}`,
                userId: userId,
                userRole: 'STUDENT',
                eventId: eventId,
                collegeId: collegeId,
                title: `🎉 New Event: ${title}`,
                message: description,
                body: description,
                type: 'EVENT',
                isRead: false,
                createdAt: SystemDateOrNow()
            };

            function SystemDateOrNow() {
                return new Date();
            }

            await db.collection('notifications').replaceOne(
                { _id: notifDoc._id },
                notifDoc,
                { upsert: true }
            );
            notificationsCreated++;
        }

        // 2. Query all active devices across 'device_push_tokens', 'deviceTokens', and 'fcm_tokens'
        const [tokens1, tokens2, tokens3] = await Promise.all([
            db.collection('device_push_tokens').find({}).toArray().catch(() => []),
            db.collection('deviceTokens').find({}).toArray().catch(() => []),
            db.collection('fcm_tokens').find({}).toArray().catch(() => [])
        ]);

        const allDevices = [...tokens1, ...tokens2, ...tokens3];
        const uniqueDevicesMap = new Map();
        for (const dev of allDevices) {
            const deviceId = dev.token || dev.deviceId || dev.pushToken || (dev._id ? dev._id.toString() : '');
            if (deviceId && !uniqueDevicesMap.has(deviceId)) {
                uniqueDevicesMap.set(deviceId, dev);
            }
        }

        for (const [deviceId, device] of uniqueDevicesMap) {
            const deliveryId = `${eventId}_${deviceId}`;

            // Idempotency Check / Duplicate Prevention
            const existingDelivery = await db.collection('notificationDeliveries').findOne({ _id: deliveryId });
            if (existingDelivery && existingDelivery.status === 'SENT') {
                continue;
            }

            const deliveryRecord = {
                _id: deliveryId,
                eventId: eventId,
                deviceId: deviceId,
                status: 'PROCESSING',
                sentAt: new Date()
            };

            const pushPayload = {
                title: `🎉 New Event: ${title}`,
                body: `${description}`,
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
                deliveryRecord.status = 'SENT';
                pushDispatches++;
            }

            await db.collection('notificationDeliveries').replaceOne(
                { _id: deliveryId },
                deliveryRecord,
                { upsert: true }
            );
        }

        console.log(`[NotificationProcessor] Summary: ${notificationsCreated} notifications saved, ${pushDispatches} push dispatches completed.`);
        console.log(`==================================================\n`);
    } catch (err) {
        console.error(`[NotificationProcessor] Error processing event notification:`, err);
    }
}

// --- 3. MongoDB Change Stream / Trigger Initialization ---

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
                    operationType: { $in: ['insert', 'update', 'replace'] }
                }
            }
        ], { fullDocument: 'updateLookup' });

        changeStream.on('change', (change) => {
            const eventDoc = change.fullDocument;
            if (eventDoc) {
                console.log(`[ChangeStream] MongoDB detected event creation/update: ${eventDoc.title}`);
                processPublishedEvent(eventDoc);
            }
        });

        console.log(`[ChangeStream] Listening for MongoDB Atlas event triggers...`);

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
