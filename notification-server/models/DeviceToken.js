const mongoose = require("mongoose");

const deviceTokenSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.Mixed,
      required: true,
      index: true,
    },
    token: {
      type: String,
      required: true,
      unique: true,
    },
    platform: {
      type: String,
      default: "android",
    },
    role: {
      type: String,
      default: "STUDENT",
    },
    collegeId: {
      type: String,
      default: "col_abc",
    },
  },
  { timestamps: true }
);

module.exports = mongoose.models.DeviceToken || mongoose.model("DeviceToken", deviceTokenSchema);
