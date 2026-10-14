package com.fuerzasalvaje.senda.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth.apply { setLanguageCode("es") }

    /** Offline persistence is on by default on Android, so pending writes survive restarts. */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore
}
