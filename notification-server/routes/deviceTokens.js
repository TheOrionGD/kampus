const express = require("express");
const router = express.Router();
const DeviceToken = require("../models/DeviceToken");
const { requireAuth } = require("../middleware/requireAuth");

router.post("/", requireAuth, async (req, res) => {
  try {
    const { token, userId, role, collegeId, platform } = req.body;
    if (typeof token !== "string" || !token.trim()) {
      return res.status(400).json({ message: "Token is required" });
    }

    const targetUserId = userId || req.user._id || req.user.email || "ALL";

    await DeviceToken.findOneAndUpdate(
      { token },
      { 
        userId: targetUserId, 
        token, 
        platform: platform || "android",
        role: role || "STUDENT",
        collegeId: collegeId || "col_abc"
      },
      { upsert: true, new: true, runValidators: true }
    );
    res.json({ message: "Device registered successfully" });
  } catch (error) {
    console.error("Device registration failed:", error.message);
    res.status(500).json({ message: "Could not register device" });
  }
});

module.exports = router;
