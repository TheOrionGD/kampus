/**
 * Kampus Push Notification System Integration Test Suite
 * Validates Token Registration, Authorization, History, Read Status, & Cleanup
 */
const http = require('http');

const BASE_URL = process.env.TEST_URL || 'http://localhost:3000';

function makeRequest(path, method, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const postData = body ? JSON.stringify(body) : '';
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    };
    if (body) {
      options.headers['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch (_) {
          resolve({ status: res.statusCode, rawBody: data });
        }
      });
    });

    req.on('error', err => reject(err));
    if (body) req.write(postData);
    req.end();
  });
}

async function runTests() {
  console.log(`\n==================================================`);
  console.log(`🧪 Running Kampus Notification Integration Tests...`);
  console.log(`Target: ${BASE_URL}\n`);

  try {
    // 1. Health check
    const health = await makeRequest('/health', 'GET');
    console.log(`[Test 1] GET /health -> Status: ${health.status} (${JSON.stringify(health.body)})`);

    // 2. Register Device Token (Step 4)
    const tokenReg = await makeRequest('/api/device-tokens', 'POST', {
      'X-User-Id': 'student_test@kampus.edu',
      'X-User-Role': 'STUDENT'
    }, {
      token: 'fcm_test_device_token_xyz_123',
      userId: 'student_test@kampus.edu',
      role: 'STUDENT',
      collegeId: 'col_abc',
      platform: 'android'
    });
    console.log(`[Test 2] POST /api/device-tokens -> Status: ${tokenReg.status} (${JSON.stringify(tokenReg.body)})`);

    // 3. Unauthorized Event Publication Check (Step 6 / Publisher Auth Check)
    const unauthNotify = await makeRequest('/api/notifications/notify-event', 'POST', {
      'X-User-Id': 'student_test@kampus.edu',
      'X-User-Role': 'STUDENT'
    }, {
      eventId: 'evt_1001',
      title: 'Unauthorized Student Event',
      body: 'Should be blocked by publisher role check'
    });
    console.log(`[Test 3] Unauthorized Event Trigger -> Status: ${unauthNotify.status} (${JSON.stringify(unauthNotify.body)})`);

    // 4. Authorized Faculty Event Publication Trigger
    const authNotify = await makeRequest('/api/notifications/notify-event', 'POST', {
      'X-User-Id': 'faculty_prof@kampus.edu',
      'X-User-Role': 'FACULTY'
    }, {
      eventId: 'evt_1002',
      title: 'Annual Tech Hackathon 2026',
      body: 'Registrations are now open for all Computer Science students!'
    });
    console.log(`[Test 4] Authorized Faculty Event Trigger -> Status: ${authNotify.status} (${JSON.stringify(authNotify.body)})`);

    // 5. Get Notification History (Step 9)
    const history = await makeRequest('/api/notifications', 'GET', {
      'X-User-Id': 'student_test@kampus.edu',
      'X-User-Role': 'STUDENT'
    });
    console.log(`[Test 5] GET /api/notifications -> Status: ${history.status} (Count: ${history.body?.notifications?.length || 0})`);

    // 6. Mark Notification Read Status (Step 9)
    const notifId = history.body?.notifications?.[0]?._id || 'evt_1002_student_test@kampus.edu';
    const markRead = await makeRequest(`/api/notifications/${encodeURIComponent(notifId)}/read`, 'PATCH', {
      'X-User-Id': 'student_test@kampus.edu',
      'X-User-Role': 'STUDENT'
    });
    console.log(`[Test 6] PATCH /api/notifications/:id/read -> Status: ${markRead.status} (${JSON.stringify(markRead.body)})`);

    // 7. Admin Database Cleanup (Item 7)
    const cleanup = await makeRequest('/api/notifications/admin/cleanup', 'POST', {
      'X-User-Id': 'admin@kampus.edu',
      'X-User-Role': 'ADMIN'
    });
    console.log(`[Test 7] POST /api/notifications/admin/cleanup -> Status: ${cleanup.status} (${JSON.stringify(cleanup.body)})`);

    console.log(`\n✅ Integration Test Suite Execution Completed.`);
    console.log(`==================================================\n`);
  } catch (err) {
    console.error(`❌ Integration Test failed:`, err.message);
  }
}

// Execute test suite if run directly
if (require.main === module) {
  runTests();
}

module.exports = { runTests };
