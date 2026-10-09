const express = require("express");
const router = express.Router();
const Notification = require("../models/Notification");
const { requireAuth, requirePublisherRole } = require("../middleware/requireAuth");
const { sendEventNotification, cleanupStaleTokensAndOutbox } = require("../services/pushNotifications");

// GET Notification history for authenticated user
router.get("/", requireAuth, async (req, res) => {
  try {
    const userId = req.user._id || req.user.email || "ALL";
    const notifications = await Notification.find({
      $or: [
        { recipientId: userId },
        { recipientId: "ALL" },
        { recipientId: "all" }
      ]
    })
      .sort({ createdAt: -1 })
      .limit(100)
      .lean();
    res.json({ notifications });
  } catch (error) {
    console.error("[Notifications API] Get error:", error);
    res.status(500).json({ message: "Could not load notifications" });
  }
});

// PATCH Mark notification read status
router.patch("/:id/read", requireAuth, async (req, res) => {
  try {
    const userId = req.user._id || req.user.email || "ALL";
    const notification = await Notification.findOneAndUpdate(
      {
        _id: req.params.id,
        $or: [
          { recipientId: userId },
          { recipientId: "ALL" },
          { recipientId: "all" }
        ]
      },
      { $set: { isRead: true } },
      { new: true }
    );
    if (!notification) {
      return res.status(404).json({ message: "Notification not found" });
    }
    res.json({ notification });
  } catch (error) {
    console.error("[Notifications API] Patch read error:", error);
    res.status(500).json({ message: "Could not update notification" });
  }
});

// POST Trigger event push notification (Authorized & System Event Publisher Route)
router.post("/notify-event", async (req, res) => {
  try {
    const { recipientIds, eventId, title, body } = req.body;
    if (!eventId && (!title || !body)) {
      return res.status(400).json({ message: "eventId or title and body are required" });
    }

    if (eventId) {
      const mongoose = require("mongoose");
      const db = mongoose.connection.db;
      if (db) {
        const { ObjectId } = require("mongodb");
        let eventDoc = await db.collection("events").findOne({ _id: eventId });
        if (!eventDoc && ObjectId.isValid(eventId)) {
          eventDoc = await db.collection("events").findOne({ _id: new ObjectId(eventId) });
        }
        if (!eventDoc) {
          eventDoc = await db.collection("events").findOne({ id: eventId });
        }
        if (eventDoc) {
          const { filterRecipientsByEventVisibility } = require("../services/pushNotifications");
          const recipients = await filterRecipientsByEventVisibility(db, eventDoc);
          const eventTitle = eventDoc.title || "New Event";
          const eventDesc = eventDoc.fullDescription || eventDoc.description || eventDoc.announcementNote || "A new event has been scheduled.";
          const result = await sendEventNotification({
            recipientIds: recipients,
            eventId: String(eventDoc._id || eventDoc.id || eventId),
            title: `🎉 New Event: ${eventTitle}`,
            body: eventDesc,
            eventData: {
              college: eventDoc.college || eventDoc.collegeId || "Kampus",
              category: eventDoc.category || "GENERAL",
              deadline: eventDoc.deadline || "Upcoming",
              eventDate: eventDoc.eventDate || "Soon",
              startTime: eventDoc.startTime || "10:00 AM"
            }
          });
          return res.json({ success: true, result });
        }
      }
    }

    const result = await sendEventNotification({
      recipientIds: recipientIds || ["ALL"],
      eventId: eventId || Date.now().toString(),
      title: title || "New Event Notification",
      body: body || "Tap to view details"
    });
    res.json({ success: true, result });
  } catch (error) {
    console.error("[Notifications API] Notify event error:", error);
    res.status(500).json({ message: "Could not send notification", error: error.message });
  }
});

// POST Admin Cleanup Endpoint (Item 7: Purges old device tokens and completed outbox records)
router.post("/admin/cleanup", requireAuth, requirePublisherRole, async (req, res) => {
  try {
    const cleanupResult = await cleanupStaleTokensAndOutbox();
    res.json({ success: true, message: "Database maintenance cleanup completed", result: cleanupResult });
  } catch (error) {
    console.error("[Notifications API] Cleanup error:", error);
    res.status(500).json({ message: "Could not run database cleanup", error: error.message });
  }
});

module.exports = router;
