const admin = require("firebase-admin");
let firebaseApp;

function getFirebaseAdmin() {
  if (firebaseApp) return admin;
  if (admin.apps && admin.apps.length > 0) {
    firebaseApp = admin.apps[0];
    return admin;
  }
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!projectId || !serviceAccountJson) {
    throw new Error("Firebase environment variables are missing (FIREBASE_PROJECT_ID and FIREBASE_SERVICE_ACCOUNT_JSON)");
  }
  const serviceAccount = JSON.parse(serviceAccountJson);
  firebaseApp = admin.initializeApp({
    credential: admin.credential.cert({
      projectId,
      clientEmail: serviceAccount.client_email,
      privateKey: serviceAccount.private_key,
    }),
  });
  return admin;
}

module.exports = { getFirebaseAdmin };
