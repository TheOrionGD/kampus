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

// POST Trigger event push notification (Authorized Faculty/Admin Event Publisher Route)
router.post("/notify-event", requireAuth, requirePublisherRole, async (req, res) => {
  try {
    const { recipientIds, eventId, title, body } = req.body;
    if (!title || !body) {
      return res.status(400).json({ message: "Title and body are required" });
    }
    const result = await sendEventNotification({
      recipientIds: recipientIds || ["ALL"],
      eventId: eventId || Date.now().toString(),
      title,
      body
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
