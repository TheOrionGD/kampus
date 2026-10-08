# Kampus Firebase-Free Event Notification Processor

Production-ready, event-driven notification processor and push gateway server for the **Kampus Android Event Platform**, operating completely **independent of Google Firebase and FCM**.

---

## 🏗️ Architecture Overview

```text
Kotlin Android App (Faculty / Admin)
        │
        │ Create & Publish Event
        ↓
MongoDB Atlas (`events` collection)
        │
        │ Database Trigger / MongoDB Change Stream (status = PUBLISHED)
        ↓
Notification Processor (`notification-server/server.js`)
        │
        ├── 1. Read event ID, college ID, and target audience (ALL_STUDENTS, DEPARTMENT, YEAR, SECTION)
        ├── 2. Query target students in `users` collection (Filtered strictly by collegeId)
        ├── 3. Query active devices in `deviceTokens` collection (Supports multiple devices per student)
        ├── 4. Idempotency Check on `notificationDeliveries` (eventId + userId + deviceId)
        ├── 5. Insert records into `notifications` collection
        ├── 6. Send push payloads via Firebase-free Push Gateway (SSE Stream / Push Provider)
        └── 7. Record delivery status in `notificationDeliveries` (SENT, FAILED, INVALID_DEVICE)
        ↓
Student Android Device (Background / Terminated / Foreground)
        │
        ↓
Native Phone Notification (NotificationChannel + NotificationManager)
        │
        ↓
Student Taps Notification
        │
        ↓
Event Details Screen (Deep Link with eventId)
```

---

## 🚫 Zero Firebase Guarantee

This system contains **ZERO Firebase dependencies**:
- No Firebase Cloud Messaging (FCM)
- No Firebase Authentication
- No Firebase Firestore
- No Firebase Cloud Functions
- No `google-services.json`
- No `com.google.firebase` packages

---

## ⚡ How Background / Terminated-App Delivery Works Without FCM

1. **Persistent Push Daemon Service (`KampusPushReceiverService`)**:
   Android apps maintain push notification capability without Google Play Services by establishing a persistent socket / Server-Sent Events (SSE) keep-alive connection to the Firebase-free Push Gateway via an Android Service.

2. **Native Android System Notification**:
   When a push payload lands on the device (even when the app is in the background or killed), `KampusPushReceiverService` parses the payload (`eventId`, `title`, `body`, `category`) and posts a native Android system notification using `NotificationManager` and `NotificationChannel("kampus_event_notifications_v2", "Event Notifications", IMPORTANCE_HIGH)`.

3. **Deep Linking**:
   Tapping the notification fires a `PendingIntent` that launches `MainActivity` and navigates directly to `EventDetailScreen` for that specific `eventId`.

---

## 🚀 Running the Notification Server

### 1. Install Dependencies
```bash
cd notification-server
npm install
```

### 2. Configure Environment Variables
Create a `.env` file or export environment variables:
```env
PORT=3000
MONGODB_URI=mongodb+srv://<username>:<password>@cluster0.mongodb.net/Kampus?retryWrites=true&w=majority
MONGODB_DATABASE=Kampus
```

### 3. Start Server
```bash
npm start
```

---

## 📊 Database Collections Schema

- `users`: `_id`, `name`, `email`, `role`, `collegeId`, `departmentId`, `year`, `section`, `isActive`
- `colleges`: `_id`, `name`, `code`, `contactEmail`, `isActive`
- `events`: `_id`, `collegeId`, `createdBy`, `createdByRole`, `title`, `description`, `category`, `status`, `targetType`, `targetDepartments`, `targetYears`, `targetSections`
- `deviceTokens`: `_id`, `userId`, `collegeId`, `deviceId`, `pushToken`, `platform`, `isActive`, `lastSeenAt`
- `notifications`: `_id`, `userId`, `eventId`, `collegeId`, `title`, `body`, `isRead`, `createdAt`
- `notificationDeliveries`: `_id`, `notificationId`, `userId`, `deviceId`, `status`, `attempts`, `sentAt`, `error`
