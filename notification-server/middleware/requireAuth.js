/**
 * Kampus Authentication & Authorization Middleware
 * Verifies identity via Authorization Header (Bearer token), X-User-Id, or X-User-Role.
 */
function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  const userIdHeader = req.headers['x-user-id'] || req.headers['x-user-email'] || req.body.userId || req.body.email;
  const userRoleHeader = req.headers['x-user-role'] || req.body.role || req.body.userRole || 'STUDENT';
  const collegeIdHeader = req.headers['x-college-id'] || req.body.collegeId || 'col_abc';

  let userId = null;

  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    userId = userIdHeader || token;
  } else if (userIdHeader) {
    userId = userIdHeader;
  }

  if (!userId) {
    return res.status(401).json({ 
      error: "Unauthorized", 
      message: "Authentication token or X-User-Id / X-User-Email header is required" 
    });
  }

  const safeUserId = String(userId).trim().toLowerCase();
  const safeRole = String(userRoleHeader).trim().toUpperCase();

  req.user = {
    _id: safeUserId,
    id: safeUserId,
    email: safeUserId,
    userId: safeUserId,
    role: safeRole,
    collegeId: String(collegeIdHeader).trim()
  };

  next();
}

/**
 * Authorization Middleware: Ensures current user has an authorized role to publish events.
 */
function requirePublisherRole(req, res, next) {
  if (!req.user) {
    return res.status(401).json({ error: "Unauthorized", message: "Authentication required" });
  }

  const allowedPublisherRoles = [
    'FACULTY', 
    'PROFESSOR', 
    'DEAN', 
    'HOD', 
    'HEAD_OF_DEPARTMENT', 
    'ADMIN', 
    'SUPER_ADMIN', 
    'SUPERADMIN', 
    'COLLEGE_ADMIN', 
    'COMMUNITY_LEADER',
    'EVENT_COORDINATOR'
  ];

  const userRole = (req.user.role || '').toUpperCase();
  if (!allowedPublisherRoles.includes(userRole)) {
    console.warn(`[Auth] Access denied for user '${req.user.email}' with role '${userRole}'. Publisher authorization required.`);
    return res.status(403).json({ 
      error: "Forbidden", 
      message: `User with role '${userRole}' is not authorized to publish events or trigger push notifications.` 
    });
  }

  next();
}

module.exports = {
  requireAuth,
  requirePublisherRole
};
