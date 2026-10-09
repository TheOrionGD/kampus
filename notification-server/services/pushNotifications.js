const { getFirebaseAdmin } = require("../config/firebase");
const DeviceToken = require("../models/DeviceToken");
const Notification = require("../models/Notification");

/**
 * Filters recipient user IDs based on an event's visibility & audience criteria.
 */
async function filterRecipientsByEventVisibility(db, eventDoc) {
  if (!db) return ["ALL"];

  const collegeId = eventDoc.collegeId || eventDoc.college || null;
  const targetDept = eventDoc.targetDept || eventDoc.department || eventDoc.targetDepartments || null;
  const eligibility = eventDoc.eligibility || eventDoc.targetAudience || "ALL";

  const userQuery = {};

  if (collegeId && collegeId !== "ALL") {
    userQuery.$or = [
      { collegeId: collegeId },
      { college: collegeId }
    ];
  }

  if (targetDept && targetDept !== "ALL" && targetDept !== "All Departments") {
    userQuery.department = { $regex: new RegExp(targetDept, "i") };
  }

  if (eligibility && eligibility !== "ALL" && eligibility !== "All Students") {
    if (eligibility.toUpperCase().includes("FACULTY")) {
      userQuery.role = { $in: ["FACULTY", "PROFESSOR", "HOD", "ADMIN"] };
    } else if (eligibility.toUpperCase().includes("STUDENT")) {
      userQuery.role = "STUDENT";
    }
  }

  try {
    const students = await db.collection("students").find(userQuery).toArray().catch(() => []);
    const faculties = await db.collection("college_faculties").find(userQuery).toArray().catch(() => []);
    const users = await db.collection("users").find(userQuery).toArray().catch(() => []);

    const recipientSet = new Set();
    const extractEmailOrId = (u) => {
      const email = u.email || u.collegeEmail || (u._id ? u._id.toString() : "");
      if (email) recipientSet.add(email.trim().toLowerCase());
    };

    students.forEach(extractEmailOrId);
    faculties.forEach(extractEmailOrId);
    users.forEach(extractEmailOrId);

    // If query matches specific recipients, return their user IDs; otherwise fallback to broadcast ALL
    if (recipientSet.size > 0) {
      return Array.from(recipientSet);
    }
  } catch (err) {
    console.warn("[VisibilityFilter] Error querying recipients, using broadcast fallback:", err.message);
  }

  return ["ALL"];
}

/**
 * Sends FCM Push Notifications for published campus events
 */
async function sendEventNotification({ recipientIds, eventId, title, body, eventData = {} }) {
  let admin = null;
  try {
    admin = getFirebaseAdmin();
  } catch (err) {
    console.warn("[PushNotifications] Firebase Admin SDK warning:", err.message);
  }

  const recipients = [...new Set((recipientIds || []).map(id => String(id).trim().toLowerCase()))];
  if (!recipients.length) recipients.push("all");

  let devices = [];
  try {
    if (recipients.includes("all")) {
      devices = await DeviceToken.find({}).lean();
    } else {
      devices = await DeviceToken.find({ userId: { $in: recipients } }).lean();
    }
  } catch (e) {
    console.warn("[PushNotifications] Error fetching devices from Mongoose:", e.message);
  }

  const tokens = [...new Set(devices.map(d => d.token).filter(Boolean))];

  const records = recipients.map(recipientId => ({
    recipientId,
    title,
    body,
    type: "event",
    eventId,
    deliveryStatus: "pending",
  }));

  let saved = [];
  try {
    saved = await Notification.insertMany(records);
  } catch (e) {
    console.warn("[PushNotifications] Error saving notification history to DB:", e.message);
  }

  if (!tokens.length || !admin) {
    return { saved: saved.length, sent: 0, devicesTargeted: tokens.length };
  }

  let sent = 0;
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    let attempts = 0;
    let batchSuccess = false;

    while (attempts < 2 && !batchSuccess) {
      attempts++;
      try {
        const result = await admin.messaging().sendEachForMulticast({
          tokens: batch,
          notification: { title, body },
          data: {
            type: "event",
            eventId: String(eventId),
            title: String(title),
            body: String(body),
            ...eventData
          },
          android: {
            priority: "high",
            notification: {
              channelId: "kampus_events",
            },
          },
        });

        sent += result.successCount;
        batchSuccess = true;

        const invalidTokens = [];
        result.responses.forEach((response, index) => {
          if (
            !response.success &&
            [
              "messaging/registration-token-not-registered",
              "messaging/invalid-registration-token",
            ].includes(response.error?.code)
          ) {
            invalidTokens.push(batch[index]);
          }
        });

        if (invalidTokens.length) {
          await DeviceToken.deleteMany({ token: { $in: invalidTokens } });
          console.log(`[PushNotifications] Pruned ${invalidTokens.length} invalid FCM tokens from MongoDB.`);
        }
      } catch (fcmErr) {
        console.error(`[PushNotifications] FCM Multicast attempt ${attempts} failed:`, fcmErr.message);
        if (attempts >= 2 && saved.length) {
          await Notification.updateMany(
            { _id: { $in: saved.map(n => n._id) } },
            { $set: { deliveryStatus: "failed" } }
          );
        }
      }
    }
  }

  if (saved.length && sent > 0) {
    try {
      await Notification.updateMany(
        { _id: { $in: saved.map(n => n._id) }, deliveryStatus: "pending" },
        { $set: { deliveryStatus: "sent" } }
      );
    } catch (e) {}
  }

  return { saved: saved.length, sent, devicesTargeted: tokens.length };
}

/**
 * Periodically cleans up old inactive device tokens (> 30 days) and old completed outbox records
 */
async function cleanupStaleTokensAndOutbox() {
  try {
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

    const prunedTokens = await DeviceToken.deleteMany({
      updatedAt: { $lt: thirtyDaysAgo }
    });

    const prunedNotifs = await Notification.deleteMany({
      createdAt: { $lt: thirtyDaysAgo },
      deliveryStatus: { $in: ["sent", "failed"] }
    });

    console.log(`[Maintenance Cleanup] Pruned ${prunedTokens.deletedCount || 0} stale tokens and ${prunedNotifs.deletedCount || 0} old notification records.`);
    return { prunedTokens: prunedTokens.deletedCount || 0, prunedNotifs: prunedNotifs.deletedCount || 0 };
  } catch (err) {
    console.error("[Maintenance Cleanup] Error performing database cleanup:", err.message);
    return { error: err.message };
  }
}

module.exports = {
  filterRecipientsByEventVisibility,
  sendEventNotification,
  cleanupStaleTokensAndOutbox
};
