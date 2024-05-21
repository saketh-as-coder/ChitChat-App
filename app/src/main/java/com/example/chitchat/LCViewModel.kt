package com.example.chitchat

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import com.example.chitchat.Data.CHATS
import com.example.chitchat.Data.ChatData
import com.example.chitchat.Data.ChatUser
import com.example.chitchat.Data.Event
import com.example.chitchat.Data.MESSAGE
import com.example.chitchat.Data.Message
import com.example.chitchat.Data.STATUS
import com.example.chitchat.Data.Status
import com.example.chitchat.Data.USER_NODE
import com.example.chitchat.Data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel

import java.lang.Exception
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject



@HiltViewModel
class LCViewModel @Inject constructor(
    val auth: FirebaseAuth,
    var db: FirebaseFirestore,
    val storage: FirebaseStorage
) : ViewModel() {


    var inProcess = mutableStateOf(false)
    var inProcessChats = mutableStateOf(false)
    val eventMutableState = mutableStateOf<Event<String>?>(null)
    var signIn = mutableStateOf(false)
    val userData = mutableStateOf<UserData?>(null)
    val chats = mutableStateOf<List<ChatData>>(listOf())
    val chatMessages = mutableStateOf<List<Message>>(listOf())
    val inProgressChatMessage = mutableStateOf(false)
    var currentChatMessageListener: ListenerRegistration? = null
    val status = mutableStateOf<List<Status>>(listOf())
    val inProgressStatus = mutableStateOf(false)

    init {
        val currentUser = auth.currentUser
        signIn.value = currentUser != null
        currentUser?.uid?.let {
            getUserData(it)
        }
    }

    fun populateMessages(chatId: String) {
        inProgressChatMessage.value = true
        currentChatMessageListener = db.collection(CHATS).document(chatId).collection(MESSAGE)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    handleException(error)
                }
                if (value != null) {
                    chatMessages.value = value.documents.mapNotNull {
                        it.toObject<Message>()
                    }.sortedBy { it.timestamp }
                    inProgressChatMessage.value = false
                }
            }
    }

    fun depopulateMessage() {
        chatMessages.value = listOf()
        currentChatMessageListener = null
    }

    fun populateChats() {
        inProcessChats.value = true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId),
            )
        ).addSnapshotListener {

                value, error ->
            if (error != null) {
                handleException(error)
            }
            if (value != null) {
                chats.value = value.documents.mapNotNull {
                    it.toObject<ChatData>()
                }
                inProcessChats.value = false
            }
        }

    }

    fun onSendReply(chatID: String, message: String) {
        val time = Calendar.getInstance().time.toString()
        val msg = Message(userData.value?.userId, message, time)
        db.collection(CHATS).document(chatID).collection(MESSAGE).document().set(msg)
    }

    fun signUp(name: String, number: String, email: String, password: String) {
        inProcess.value = true
        if (name.isEmpty() or number.isEmpty() or email.isEmpty() or password.isEmpty()) {
            handleException(customMessage = " Please Fill All fields")
            return
        }
//        inProcess.value=true
        db.collection(USER_NODE).whereEqualTo("number", number).get().addOnSuccessListener {
            if (it.isEmpty) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { authii ->
                        if (authii.isSuccessful) {
                            signIn.value = true
                            CreateOrUpdateProfile(name, number)
                        } else {
                            handleException(authii.exception, customMessage = "Sign Up failed")
                        }
                    }
            } else {
                handleException(customMessage = " number Already Exists")
                inProcess.value = false
            }
        }.addOnFailureListener {
            Log.i("error", "error")
        }

//        data class datt(val number12: String)
//        db.collection(USER_NODE).document("number1").set(datt(number12 = number)).addOnSuccessListener {
//            if(it==null){
//                auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener { authii ->
//                    if(authii.isSuccessful){
//                        signIn.value=true
//                        CreateOrUpdateProfile(name,number)
//                    }
//                    else{
//                        handleException(authii.exception, customMessage = "Sign Up failed")
//                    }
//                }
//            }else{
//                handleException(customMessage = " number Already Exists")
//                inProcess.value=false
//            }
//        }.addOnFailureListener {
//            Log.i("error","error")
//        }

    }

    fun logIn(email: String, password: String) {
        if (email.isEmpty() or password.isEmpty()) {
            handleException(customMessage = " Please fill all the fields")
            return
        } else {
            inProcess.value = true
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        signIn.value = true
                        inProcess.value = false
                        auth.currentUser?.uid?.let {
                            getUserData(it)
                        }
                    } else {
                        handleException(exception = it.exception, customMessage = " Login failed")
                    }
                }
        }
    }

    fun uploadProfileImage(uri: Uri) {
        uploadImage(uri) {
            CreateOrUpdateProfile(imageUrl = it.toString())
        }
    }

    fun uploadImage(uri: Uri, onSuccess: (Uri) -> Unit) {
        inProcess.value = true
        val storageRef = storage.reference
        val uuid = UUID.randomUUID()
        val imageRef = storageRef.child("image/$uuid")
        val uploadTask = imageRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            val result = it.metadata?.reference?.downloadUrl
            result?.addOnSuccessListener(onSuccess)
            inProcess.value = false

        }
            .addOnFailureListener {
                handleException(it)
            }
    }

    fun CreateOrUpdateProfile(
        name: String? = null,
        number: String? = null,
        imageUrl: String? = null
    ) {
        val uid = auth.currentUser?.uid
        val userData = UserData(
            userId = uid,
            name = name ?: userData.value?.name,
            number = number ?: userData.value?.number,
            imageUrl = imageUrl ?: userData.value?.imageUrl

        )
        uid?.let {
            inProcess.value = true
            db.collection(USER_NODE).document(uid).get().addOnSuccessListener {

                if (it.exists()) {
                    db.collection(USER_NODE).document(uid).set(userData, SetOptions.merge())
                        .addOnSuccessListener {
                            inProcess.value = false
                            getUserData(uid)
                        }
                } else {
                    db.collection(USER_NODE).document(uid).set(userData)
                    inProcess.value = false
                    getUserData(uid)
                }
            }
                .addOnFailureListener {
                    handleException(it, "Cannot Retrieve User")
                }

        }
    }

    private fun getUserData(uid: String) {
        inProcess.value = true
        db.collection(USER_NODE).document(uid).addSnapshotListener { value, error ->
            if (error != null) {
                handleException(error, "Cannot Retrieve User")
            }
            if (value != null) {
                var user = value.toObject<UserData>()
                userData.value = user
                inProcess.value = false
                populateChats()
                populateStatuses()

            }
        }
    }

    fun handleException(exception: Exception? = null, customMessage: String = "") {
        Log.e("ChitChatApp", "Chat Exception: ", exception)
        exception?.printStackTrace()
        val errorMsg = exception?.localizedMessage ?: ""
        val message = if (customMessage.isNullOrEmpty()) errorMsg else customMessage
        eventMutableState.value = Event(message)
        inProcess.value = false
    }

    fun logout() {
        auth.signOut()
        signIn.value = false
        userData.value = null
        depopulateMessage()
        currentChatMessageListener = null
        eventMutableState.value = Event("Logged Out")
    }

    fun onAddChat(number: String) {
        if (number.isEmpty() || !number.isDigitsOnly()) {
            handleException(customMessage = "Number must contain digits only")
        } else {
            db.collection(CHATS).where(
                Filter.or(
                    Filter.and(
                        Filter.equalTo("user1.number", number),
                        Filter.equalTo("user2.number", userData.value?.number)
                    ),
                    Filter.and(
                        Filter.equalTo("user1.number", userData.value?.number),
                        Filter.equalTo("user2.number", number)
                    )
                )
            ).get().addOnSuccessListener {
                if (it.isEmpty) {
                    db.collection(USER_NODE).whereEqualTo("number", number).get()
                        .addOnSuccessListener { itt ->
                            if (itt.isEmpty) {
                                handleException(customMessage = "number not found")
                            } else {
                                val chatPartner = itt.toObjects<UserData>()[0]
                                val id = db.collection(CHATS).document().id
                                val chat = ChatData(
                                    chatId = id,
                                    ChatUser(
                                        userData.value?.userId,
                                        userData.value?.name,
                                        userData.value?.imageUrl,
                                        userData.value?.number
                                    ),
                                    ChatUser(
                                        chatPartner.userId,
                                        chatPartner.name,
                                        chatPartner.imageUrl,
                                        chatPartner.number
                                    )
                                )
                                db.collection(CHATS).document(id).set(chat).addOnSuccessListener {
                                    Log.i("success", "Chat added successfully")
                                }.addOnFailureListener { exception ->
                                    handleException(exception)
                                }
                            }
                        }
                        .addOnFailureListener { exp ->
                            handleException(exp)
                        }
                } else {
                    handleException(customMessage = "Chat already exists")
                }
            }
        }
    }

    fun uploadStatus(uri: Uri) {
        uploadImage(uri) {
            createStatus(it.toString())
        }
    }

    fun createStatus(imageUrl: String) {
        val newStatus = Status(
            ChatUser(
                userData.value?.userId,
                userData.value?.name,
                userData.value?.imageUrl,
                userData.value?.number,
            ),
            imageUrl,
            System.currentTimeMillis()
        )
        db.collection(STATUS).document().set(newStatus)
    }

    fun populateStatuses() {
        val timeDelta= 24L *60 *60 *1000
        val cutOff=System.currentTimeMillis()-timeDelta
        inProgressStatus.value = true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId),
            )
        ).addSnapshotListener { value, error ->
            if (error != null)
                handleException(error)
            if (value != null) {
                val currentConnections = arrayListOf(userData.value?.userId)
                val chats = value.toObjects<ChatData>()
                chats.forEach { chat ->
                    if (chat.user1.userId == userData.value?.userId) {
                        currentConnections.add(chat.user2.userId)
                    } else
                        currentConnections.add(chat.user1.userId)
                }
                db.collection(STATUS).whereGreaterThan("timestamp",cutOff).whereIn("user.userId", currentConnections)
                    .addSnapshotListener { value, error ->
                        if(error!=null){
                            handleException(error)
                        }
                        if(value!=null){
                            status.value=value.toObjects()
                            inProgressStatus.value=false
                        }
                    }
            }
        }
    }
}
