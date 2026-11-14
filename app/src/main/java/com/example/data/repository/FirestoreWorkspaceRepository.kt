package com.example.data.repository

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class WorkspaceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val toolId: String = "",
    val toolTitle: String = "",
    val modelEndpoint: String = "",
    val prompt: String = "",
    val responseText: String = "",
    val metadataSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectiveConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val commonDirective: String = "",
    val useCommonDirective: Boolean = true,
    val individualDirectives: Map<String, String> = emptyMap(),
    val individualEnabledMap: Map<String, Boolean> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreWorkspaceRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun recordsCollection(userId: String) =
        db.collection("users").document(userId).collection("workspaceRecords")

    private fun directiveConfigDoc(userId: String) =
        db.collection("users").document(userId).collection("directiveConfigs").document("default")

    fun observeRecords(userId: String): Flow<List<WorkspaceRecord>> =
        recordsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    WorkspaceRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }

    suspend fun saveRecord(
        userId: String,
        toolId: String,
        toolTitle: String,
        modelEndpoint: String,
        prompt: String,
        responseText: String,
        metadataSummary: String = ""
    ): Result<String> {
        val docRef = recordsCollection(userId).document()
        val payload = buildMap<String, Any> {
            put("userId", userId)
            put("toolId", toolId.take(64))
            put("toolTitle", toolTitle.take(100))
            put("modelEndpoint", modelEndpoint.take(150))
            put("prompt", prompt.take(20000))
            put("responseText", responseText.take(100000))
            if (metadataSummary.isNotBlank()) {
                put("metadataSummary", metadataSummary.take(200))
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getRecordById(userId: String, recordId: String): Result<WorkspaceRecord?> {
        val docRef = recordsCollection(userId).document(recordId)
        return try {
            val snap = docRef.get().await()
            val record = snap.toObject(
                WorkspaceRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateRecord(userId: String, record: WorkspaceRecord): Result<Unit> {
        require(record.id.isNotBlank()) { "Record ID must not be blank for update" }
        requireNotNull(record.createdAt) { "Record createdAt must not be null for update" }
        val docRef = recordsCollection(userId).document(record.id)
        return try {
            docRef.set(record.copy(updatedAt = null)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRecord(userId: String, recordId: String): Result<Unit> {
        val docRef = recordsCollection(userId).document(recordId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllRecords(userId: String): Result<Unit> {
        val colRef = recordsCollection(userId)
        return try {
            val docs = colRef.get().await().documents
            if (docs.isNotEmpty()) {
                val batch = db.batch()
                docs.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeDirectiveConfig(userId: String): Flow<DirectiveConfig?> =
        directiveConfigDoc(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    DirectiveConfig::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun saveDirectiveConfig(
        userId: String,
        commonDirective: String,
        useCommonDirective: Boolean,
        individualDirectives: Map<String, String>,
        individualEnabledMap: Map<String, Boolean>
    ): Result<Unit> {
        val docRef = directiveConfigDoc(userId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "commonDirective" to commonDirective.take(4000),
                    "useCommonDirective" to useCommonDirective,
                    "individualDirectives" to individualDirectives,
                    "individualEnabledMap" to individualEnabledMap,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "commonDirective" to commonDirective.take(4000),
                    "useCommonDirective" to useCommonDirective,
                    "individualDirectives" to individualDirectives,
                    "individualEnabledMap" to individualEnabledMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
