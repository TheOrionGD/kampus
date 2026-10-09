const express = require("express");
const router = express.Router();
const DeviceToken = require("../models/DeviceToken");

router.post("/", async (req, res) => {
  try {
    const { token, userId, role, collegeId, platform } = req.body;
    if (typeof token !== "string" || !token.trim()) {
      return res.status(400).json({ message: "Token is required" });
    }

    const safeUserId = (userId || req.headers['x-user-id'] || "ALL").trim().toLowerCase();

    await DeviceToken.findOneAndUpdate(
      { token: token.trim() },
      { 
        token: token.trim(),
        userId: safeUserId, 
        platform: platform || "android",
        role: role || "STUDENT",
        collegeId: collegeId || "col_abc",
        updatedAt: new Date()
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

