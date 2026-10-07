package com.example.kampus

import com.example.kampus.models.ChatMessage
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.EventParticipant
import com.example.kampus.models.StudentUser

/**
 * Delegating legacy calls directly to MongoDBHelper (using MongoDB Atlas)
 */
object FirebaseHelper {
    fun uploadImageToStorage(uriStr: String, folderName: String, onComplete: (String) -> Unit) {
        MongoDBHelper.uploadImageToStorage(null, uriStr, folderName, onComplete)
    }

    fun saveParticipant(participant: EventParticipant, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.saveParticipant(participant, onComplete)
    }

    fun removeParticipant(eventId: String, studentEmail: String, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.removeParticipant(eventId, studentEmail, onComplete)
    }

    fun listenToParticipants(onDataChanged: (List<EventParticipant>) -> Unit) {
        MongoDBHelper.listenToParticipants(onDataChanged)
    }

    fun saveEvent(event: CollegeEvent, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.saveEvent(event, onComplete)
    }

    fun deleteEvent(eventId: String, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.deleteEvent(eventId, onComplete)
    }

    fun listenToEvents(onDataChanged: (List<CollegeEvent>) -> Unit) {
        MongoDBHelper.listenToEvents(onDataChanged)
    }

    fun saveStudent(student: StudentUser, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.saveStudent(student, onComplete)
    }

    fun listenToStudents(onDataChanged: (List<StudentUser>) -> Unit) {
        MongoDBHelper.listenToStudents(onDataChanged)
    }

    fun saveCollege(collegeId: String, collegeMap: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.saveCollege(collegeId, collegeMap, onComplete)
    }

    fun listenToColleges(onDataChanged: (List<Map<String, Any>>) -> Unit) {
        MongoDBHelper.listenToColleges(onDataChanged)
    }

    fun saveFaculty(facultyMap: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.saveFaculty(facultyMap, onComplete)
    }

    fun listenToFaculties(onDataChanged: (List<Map<String, Any>>) -> Unit) {
        MongoDBHelper.listenToFaculties(onDataChanged)
    }

    fun sendDirectMessage(message: ChatMessage, onComplete: (Boolean) -> Unit) {
        MongoDBHelper.sendDirectMessage(message, onComplete)
    }

    fun listenToDirectMessages(user1: String, user2: String, onUpdate: (List<ChatMessage>) -> Unit) {
        MongoDBHelper.listenToDirectMessages(user1, user2, onUpdate)
    }

    fun listenToAllIncomingMessages(userEmail: String, onNewMessage: (String, String) -> Unit) {
        MongoDBHelper.listenToAllIncomingMessages(userEmail, onNewMessage)
    }
}