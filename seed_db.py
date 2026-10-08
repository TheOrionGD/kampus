"""
Kampus Database Seeder
Inserts Super Admin, College Admin, Dept Faculty, Students, and a Sample Event into MongoDB Atlas.
"""

import pymongo
from datetime import datetime

URI      = "mongodb+srv://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@hellotheoriongd.rbxbuxe.mongodb.net"
DB_NAME  = "Kampus"

client = pymongo.MongoClient(URI, serverSelectionTimeoutMS=10000)
client.admin.command("ping")
db = client[DB_NAME]

print(f"✅ Connected to MongoDB Atlas — database: '{DB_NAME}'\n")

# ──────────────────────────────────────────
# 1. SUPER ADMIN
# ──────────────────────────────────────────
admin_col = db["admins"]
admin_doc = {
    "_id":             "admin@kampus.com",
    "email":           "admin@kampus.com",
    "name":            "Kampus Super Admin",
    "password":        "admin123",
    "role":            "SUPER_ADMIN",
    "profilePhotoUri": "",
    "headline":        "Kampus Platform Super Administrator",
    "about":           "Platform administrator responsible for verifying colleges and managing the Kampus governance hierarchy.",
    "lastUpdated":     int(datetime.utcnow().timestamp() * 1000)
}
admin_col.replace_one({"_id": admin_doc["_id"]}, admin_doc, upsert=True)
print("✅ Super Admin seeded       → admin@kampus.com / admin123")

# ──────────────────────────────────────────
# 2. COLLEGE ADMIN  (stored in colleges collection)
# ──────────────────────────────────────────
colleges_col = db["colleges"]
college_admin_doc = {
    "_id":                       "collegeadmin@kampus.edu",
    "email":                     "collegeadmin@kampus.edu",
    "collegeEmail":              "collegeadmin@kampus.edu",
    "name":                      "Kampus College Admin",
    "password":                  "admin123",
    "role":                      "COLLEGE_ADMIN",
    "collegeName":               "Kampus Institute of Technology",
    "department":                "Administration",
    "designation":               "College Administrator",
    "contactNumber":             "9000000001",
    "accreditation":             "NAAC A+",
    "collegeWebsite":            "https://kampus.edu",
    "collegePhotoUri":           "",
    "idProofUri":                "",
    "profilePhotoUri":           "",
    "headline":                  "College Administrator — Kampus Institute of Technology",
    "about":                     "Responsible for verifying and managing department faculty on the Kampus platform.",
    "isVerifiedBySuperAdmin":    True,
    "isVerifiedByCollegeAdmin":  True,
    "lastUpdated":               int(datetime.utcnow().timestamp() * 1000)
}
colleges_col.replace_one({"_id": college_admin_doc["_id"]}, college_admin_doc, upsert=True)
print("✅ College Admin seeded     → collegeadmin@kampus.edu / admin123")

# ──────────────────────────────────────────
# 3. DEPT FACULTY  (stored in college_faculties collection)
# ──────────────────────────────────────────
faculties_col = db["college_faculties"]
faculty_doc = {
    "_id":                       "faculty@kampus.edu",
    "email":                     "faculty@kampus.edu",
    "collegeEmail":              "faculty@kampus.edu",
    "name":                      "Dr. Kampus Faculty",
    "password":                  "faculty123",
    "role":                      "DEPT_FACULTY",
    "collegeName":               "Kampus Institute of Technology",
    "department":                "Computer Science",
    "designation":               "Assistant Professor",
    "contactNumber":             "9000000002",
    "accreditation":             "NAAC A+",
    "collegeWebsite":            "https://kampus.edu",
    "collegePhotoUri":           "",
    "idProofUri":                "",
    "profilePhotoUri":           "",
    "headline":                  "Assistant Professor — Computer Science, Kampus Institute of Technology",
    "about":                     "Passionate educator dedicated to bridging academics with real-world tech opportunities for students.",
    "isVerifiedBySuperAdmin":    True,
    "isVerifiedByCollegeAdmin":  True,
    "lastUpdated":               int(datetime.utcnow().timestamp() * 1000)
}
faculties_col.replace_one({"_id": faculty_doc["_id"]}, faculty_doc, upsert=True)
print("✅ Dept Faculty seeded      → faculty@kampus.edu / faculty123")

# ──────────────────────────────────────────
# 4. STUDENTS  (stored in students collection)
# ──────────────────────────────────────────
students_col = db["students"]

student1 = {
    "_id":            "student1@kampus.edu",
    "name":           "Alex Kampus",
    "email":          "student1@kampus.edu",
    "password":       "student123",
    "college":        "Kampus Institute of Technology",
    "department":     "Computer Science",
    "year":           "3rd Year",
    "skills":         ["Python", "Kotlin", "Android Development", "Machine Learning"],
    "headline":       "CS Student & Android Developer | Open to Hackathons",
    "about":          "Passionate about mobile development and AI. Love participating in hackathons and inter-college events.",
    "profilePhotoUri":"",
    "coverPhotoUri":  "",
    "githubLink":     "https://github.com/student1kampus",
    "isOpenToWork":   True,
    "phoneNumber":    "9000000003"
}
students_col.replace_one({"_id": student1["_id"]}, student1, upsert=True)
print("✅ Student 1 seeded         → student1@kampus.edu / student123")

student2 = {
    "_id":            "student2@kampus.edu",
    "name":           "Priya Kampus",
    "email":          "student2@kampus.edu",
    "password":       "student123",
    "college":        "Kampus Institute of Technology",
    "department":     "Electronics & Communication",
    "year":           "2nd Year",
    "skills":         ["Circuit Design", "IoT", "Embedded Systems", "Python"],
    "headline":       "ECE Student | IoT Enthusiast | NSS Volunteer",
    "about":          "Curious about IoT and embedded systems. Active NSS volunteer and workshop organiser.",
    "profilePhotoUri":"",
    "coverPhotoUri":  "",
    "githubLink":     "https://github.com/student2kampus",
    "isOpenToWork":   False,
    "phoneNumber":    "9000000004"
}
students_col.replace_one({"_id": student2["_id"]}, student2, upsert=True)
print("✅ Student 2 seeded         → student2@kampus.edu / student123")

# ──────────────────────────────────────────
# 5. SAMPLE EVENT — HackKampus 2026
# ──────────────────────────────────────────
events_col = db["events"]
event_id   = "1728370000000"          # fixed stable ID
event_doc  = {
    "_id":             event_id,
    "id":              event_id,
    "title":           "HackKampus 2026",
    "college":         "Kampus Institute of Technology",
    "category":        "Hackathon",
    "deadline":        "20/10/26",
    "eventDate":       "25/10/26",
    "startTime":       "09:00 AM",
    "endTime":         "06:00 PM",
    "mode":            "Offline",
    "eligibility":     "All B.E / B.Tech Students",
    "fee":             "Free",
    "coordinatorName": "Dr. Kampus Faculty",
    "coordinatorRole": "Event Convener",
    "postedTime":      datetime.utcnow().strftime("%d/%m/%Y %I:%M %p"),
    "announcementNote":"Registrations open! Form teams of 2–4. Theme: AI for Social Good.",
    "fullDescription": (
        "HackKampus 2026 is a 9-hour hackathon hosted by Kampus Institute of Technology. "
        "Teams of 2–4 students will ideate, design, and prototype solutions around the theme "
        "'AI for Social Good'. Winners receive cash prizes and internship opportunities with our industry partners."
    ),
    "prizePool":       "₹50,000",
    "targetDept":      "All Departments",
    "posterUrl":       "",
    "externalRegLink": "https://kampus.edu/hackkampus2026",
    "customField1Label": "Team Size",
    "customField1Value": "2 – 4 Members",
    "customField2Label": "Theme",
    "customField2Value": "AI for Social Good"
}
events_col.replace_one({"_id": event_id}, event_doc, upsert=True)
print("✅ Sample Event seeded      → HackKampus 2026 (Hackathon, 25/10/26)")

print("\n🎉 All seed data inserted successfully into MongoDB Atlas — Kampus database!")
client.close()
