const express = require('express');
const { MongoClient, ObjectId } = require('mongodb');
const mongoose = require('mongoose');
const cors = require('cors');
const https = require('https');

let admin = null;
try {
    const { getFirebaseAdmin } = require('./config/firebase');
    admin = getFirebaseAdmin();
    console.log('[FirebaseAdmin] Firebase Admin SDK initialized successfully');
} catch (e) {
    try {
        admin = require('firebase-admin');
    } catch (_) {}
    console.log('[FirebaseAdmin] Firebase Admin SDK initialization info:', e.message);
}

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// Privacy Policy Endpoint
app.get(['/privacy-policy', '/privacy-policy.html'], (req, res) => {
    res.sendFile(__dirname + '/public/privacy-policy.html');
});

const PORT = process.env.PORT || 3000;
const MONGODB_URI = process.env.MONGODB_URI;
const DB_NAME = process.env.MONGODB_DATABASE || 'Kampus';
const FCM_SERVER_KEY = process.env.FCM_SERVER_KEY;

let db = null;
let mongoClient = null;

// Mount Modular Express Routes for FCM Device Tokens & Notification History
app.use('/api/device-tokens', require('./routes/deviceTokens'));
app.use('/api/notifications', require('./routes/notifications'));


// Health & Info Endpoint
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'Kampus FCM Notification Server',
        version: '2.0.0',
        endpoints: {
            health: 'GET /health',
            privacyPolicy: 'GET /privacy-policy',
            deviceTokens: 'POST /api/device-tokens',
            pushSend: 'POST /api/push/send',
            notifyEvent: 'POST /api/notifications/notify-event',
            getNotifications: 'GET /api/notifications',
            markRead: 'PATCH /api/notifications/:id/read'
        }
    });
});

app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

/**
 * Dispatch Push Notification via Firebase Cloud Messaging (FCM)
 */
async function sendFcmPush(targetTokenOrTopic, title, body, dataPayload = {}) {
    if (admin && admin.apps && admin.apps.length > 0) {
        try {
            const isTopic = targetTokenOrTopic.startsWith('/topics/') || !targetTokenOrTopic.includes(':');
            const topicName = targetTokenOrTopic.replace('/topics/', '');

            if (isTopic) {
                const message = {
                    topic: topicName || 'college_events',
                    notification: { title, body },
                    data: { title, body, ...dataPayload },
                    android: { priority: 'high', notification: { channelId: 'kampus_events_v2' } }
                };
                await admin.messaging().send(message);
                console.log(`[FirebaseAdmin] Dispatched FCM topic message to: ${topicName}`);
                return true;
            } else {
                const message = {
                    token: targetTokenOrTopic,
                    notification: { title, body },
                    data: { title, body, ...dataPayload },
                    android: { priority: 'high', notification: { channelId: 'kampus_events_v2' } }
                };
                await admin.messaging().send(message);
                console.log(`[FirebaseAdmin] Dispatched FCM token message to device`);
                return true;
            }
        } catch (adminErr) {
            console.warn(`[FirebaseAdmin] Fallback to FCM Legacy REST API: ${adminErr.message}`);
        }
    }

    return new Promise((resolve) => {
        const toTarget = targetTokenOrTopic.startsWith('/topics/') || targetTokenOrTopic.includes(':')
            ? targetTokenOrTopic
            : `/topics/college_events`;

        const payload = {
            to: toTarget,
            priority: 'high',
            notification: {
                title: title,
                body: body,
                sound: 'default'
            },
            data: {
                title: title,
                body: body,
                ...dataPayload
            }
        };

        const postData = JSON.stringify(payload);
        const options = {
            hostname: 'fcm.googleapis.com',
            path: '/fcm/send',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `key=${FCM_SERVER_KEY}`,
                'Content-Length': Buffer.byteLength(postData)
            }
        };

        const req = https.request(options, (res) => {
            let responseBody = '';
            res.on('data', (chunk) => responseBody += chunk);
            res.on('end', () => {
                console.log(`[FCM HTTP Dispatch] Status ${res.statusCode} -> Target: ${toTarget}`);
                resolve(res.statusCode === 200);
            });
        });

        req.on('error', (err) => {
            console.error(`[FCM Dispatch Error] ${err.message}`);
            resolve(false);
        });

        req.write(postData);
        req.end();
    });
}

// Device Token Registration Endpoint (Step 4)
app.post('/api/device-tokens', async (req, res) => {
    const { token, userId, role, collegeId, platform } = req.body;

    if (!token || typeof token !== 'string') {
        return res.status(400).json({ message: 'Token is required' });
    }

    try {
        if (!db) {
            return res.status(500).json({ message: 'Database not connected' });
        }

        const safeEmail = (userId || 'ALL').trim().toLowerCase();
        const doc = {
            _id: token,
            deviceId: token,
            pushToken: token,
            token: token,
            userId: safeEmail,
            role: role || 'STUDENT',
            collegeId: collegeId || 'col_abc',
            platform: platform || 'ANDROID',
            status: 'ACTIVE',
            isActive: true,
            lastUpdated: Date.now(),
            updatedAt: new Date()
        };

        await db.collection('deviceTokens').replaceOne({ _id: token }, doc, { upsert: true });
        await db.collection('fcm_tokens').replaceOne({ _id: token }, doc, { upsert: true });

        console.log(`[DeviceTokens] Registered FCM token for user: ${safeEmail}`);
        return res.status(200).json({ message: 'Device registered successfully' });
    } catch (err) {
        console.error('[DeviceTokens] Registration error:', err);
        return res.status(500).json({ message: 'Could not register device' });
    }
});

// Notification History Endpoints (Step 9)
app.get('/api/notifications', async (req, res) => {
    const userId = (req.query.userId || req.headers['x-user-id'] || 'ALL').trim().toLowerCase();

    try {
        if (!db) return res.status(500).json({ message: 'Database not connected' });

        const query = {
            $or: [
                { userId: userId },
                { userId: 'ALL' }
            ]
        };

        const notifications = await db.collection('notifications')
            .find(query)
            .sort({ createdAt: -1 })
            .limit(100)
            .toArray();

        return res.status(200).json({ notifications });
    } catch (err) {
        console.error('[Notifications] Read error:', err);
        return res.status(500).json({ message: 'Could not load notifications' });
    }
});

app.patch('/api/notifications/:id/read', async (req, res) => {
    const notifId = req.params.id;

    try {
        if (!db) return res.status(500).json({ message: 'Database not connected' });

        const result = await db.collection('notifications').findOneAndUpdate(
            { _id: notifId },
            { $set: { isRead: true } },
            { returnDocument: 'after' }
        );

        if (!result) {
            return res.status(404).json({ message: 'Notification not found' });
        }

        return res.status(200).json({ notification: result });
    } catch (err) {
        console.error('[Notifications] Update read error:', err);
        return res.status(500).json({ message: 'Could not update notification' });
    }
});

// HTTP POST Push Dispatch Endpoint
app.post('/api/push/send', async (req, res) => {
    const { token, title, body, data } = req.body;

    const pushTitle = title || 'New Event Notification';
    const pushBody = body || 'Tap to view event details';
    const pushData = data || {};

    try {
        const target = token || '/topics/college_events';
        const sent = await sendFcmPush(target, pushTitle, pushBody, pushData);
        return res.status(200).json({ success: sent, target, deliveredVia: 'FCM' });
    } catch (err) {
        console.error('[PushGateway] Send error:', err);
        return res.status(500).json({ error: err.message });
    }
});

// HTTP POST Event Notification Trigger Endpoint (Called after event publishing)
app.post('/api/notifications/notify-event', async (req, res) => {
    const { eventId } = req.body;

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

        return res.status(200).json({ success: true, message: 'Event FCM notification processing initiated' });
    } catch (err) {
        console.error('[ApiTrigger] Error in notify-event endpoint:', err);
        return res.status(500).json({ error: err.message });
    }
});

// --- 2. Notification Processor (MongoDB In-App History & FCM Delivery) ---

const { filterRecipientsByEventVisibility, sendEventNotification, cleanupStaleTokensAndOutbox } = require('./services/pushNotifications');

async function processPublishedEvent(eventDoc) {
    if (!db) return;

    const eventId = eventDoc._id ? eventDoc._id.toString() : (eventDoc.id || String(Date.now()));
    const collegeId = eventDoc.collegeId || eventDoc.college || 'col_abc';
    const title = eventDoc.title || 'New Event';
    const description = eventDoc.fullDescription || eventDoc.description || eventDoc.announcementNote || 'A new event has been scheduled.';
    const category = eventDoc.category || 'GENERAL';
    const deadline = eventDoc.deadline || 'Upcoming';
    const eventDate = eventDoc.eventDate || 'Soon';
    const startTime = eventDoc.startTime || '10:00 AM';

    // Publisher Authorization Check
    const publisherRole = (eventDoc.createdByRole || eventDoc.publisherRole || eventDoc.role || 'FACULTY').toUpperCase();
    const allowedPublisherRoles = ['FACULTY', 'PROFESSOR', 'DEAN', 'HOD', 'ADMIN', 'SUPER_ADMIN', 'SUPERADMIN', 'COLLEGE_ADMIN', 'COMMUNITY_LEADER'];
    if (!allowedPublisherRoles.includes(publisherRole)) {
        console.warn(`[NotificationProcessor] Publisher authorization check failed for event ${eventId}. Role '${publisherRole}' is not authorized.`);
        return;
    }

    console.log(`\n==================================================`);
    console.log(`[NotificationProcessor] Processing Event ID: ${eventId}`);
    console.log(`[NotificationProcessor] College: ${collegeId} | Title: ${title} | Publisher Role: ${publisherRole}`);

    try {
        // Query recipient user IDs strictly matching event visibility & audience rules
        const recipientIds = await filterRecipientsByEventVisibility(db, eventDoc);
        console.log(`[NotificationProcessor] Filtered ${recipientIds.length} eligible recipient targets matching event visibility rules.`);

        // Dispatch FCM Push Notification & save notification history in MongoDB
        const result = await sendEventNotification({
            recipientIds,
            eventId,
            title: `🎉 New Event: ${title}`,
            body: description,
            eventData: {
                college: collegeId,
                category: category,
                deadline: deadline,
                eventDate: eventDate,
                startTime: startTime
            }
        });

        console.log(`[NotificationProcessor] Event Notification Summary: ${result.saved} history records saved, ${result.sent} FCM device pushes sent (${result.devicesTargeted} devices targeted).`);
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

        if (MONGODB_URI) {
            try {
                await mongoose.connect(MONGODB_URI, { dbName: DB_NAME });
                console.log(`[Mongoose] Connected successfully to Mongoose ORM: '${DB_NAME}'`);
            } catch (mErr) {
                console.warn(`[Mongoose] Warning connecting mongoose:`, mErr.message);
            }
        }

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

        // Schedule automated background cleanup of stale device tokens and old notification outbox records (runs every 24 hours)
        cleanupStaleTokensAndOutbox();
        setInterval(cleanupStaleTokensAndOutbox, 24 * 60 * 60 * 1000);

        app.listen(PORT, () => {
            console.log(`[Server] Kampus FCM Notification Processor Server running on port ${PORT}`);
        });
    } catch (err) {
        console.error(`[MongoDB] Connection failure:`, err.message);
        app.listen(PORT, () => {
            console.log(`[Server] Notification Server running in standalone API mode on port ${PORT}`);
        });
    }
}

startNotificationProcessorServer();
