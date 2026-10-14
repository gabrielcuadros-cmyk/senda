package com.fuerzasalvaje.senda.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/** Every student document lives under users/{uid}/... (see firestore.rules). */
object FirestorePaths {
    const val USERS = "users"
    const val COURSES = "courses"
    const val EVALUATIONS = "evaluations"
    const val QUESTIONS = "questions"
    const val SIMULACRA = "simulacra"
    const val AI_RECOMMENDATIONS = "ai_recommendations"
    const val POMODORO_SESSIONS = "pomodoro_sessions"
    const val STUDY_BLOCKS = "study_blocks"
    const val STRESS_CHECKINS = "stress_checkins"
}

fun FirebaseFirestore.userDoc(uid: String): DocumentReference =
    collection(FirestorePaths.USERS).document(uid)

fun FirebaseFirestore.userCollection(uid: String, name: String): CollectionReference =
    userDoc(uid).collection(name)
