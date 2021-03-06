package com.mongodb.rchatapp.ui.home

import androidx.lifecycle.*
import com.mongodb.rchatapp.ui.data.Conversation
import com.mongodb.rchatapp.ui.data.HomeNavigation
import com.mongodb.rchatapp.ui.data.User
import com.mongodb.rchatapp.utils.SingleLiveEvent
import com.mongodb.rchatapp.utils.getSyncConfig
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmResults
import io.realm.kotlin.where
import io.realm.mongodb.App

class HomeViewModel(private val realmSync: App) : ViewModel(), LifecycleObserver {

    private val TAG = "HomeViewModel"

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    private val _loadingBar: MutableLiveData<Boolean> = MutableLiveData(false)
    val loadingBar: LiveData<Boolean> = _loadingBar


    private val _chatList: MutableLiveData<List<Conversation>> = MutableLiveData()
    val chatList: LiveData<List<Conversation>> = _chatList

    private val _navigation: SingleLiveEvent<HomeNavigation> = SingleLiveEvent()
    val navigation: SingleLiveEvent<HomeNavigation> = _navigation

    private val _currentUser: MutableLiveData<User> = MutableLiveData()
    val userName: MutableLiveData<String> = MutableLiveData()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onLoad() {
        if (realmSync.currentUser() == null) {
            _navigation.value = HomeNavigation.GoToLogin
            return
        } else {
            getChatList()
            updateUserStatusToOnline()
        }
    }

    private fun getChatList() {
        val user = realmSync.currentUser() ?: return
        val config = realmSync.getSyncConfig("user=${user.id}")
        _loadingBar.value = true

        Realm.getInstanceAsync(config, object : Realm.Callback() {
            override fun onSuccess(realm: Realm) {
                val result = realm.where<User>().equalTo("_id", user.id).findAll()
                result.addChangeListener(RealmChangeListener<RealmResults<User>> {
                    it.first()?.let {
                        realm.copyFromRealm(it).apply {
                            _chatList.value = this?.conversations ?: emptyList()
                        }
                    }
                })

                val currentUser = result.first()?.let {
                    _currentUser.value = it
                    checkProfileCompleteStatus(it)
                    updateCurrentUserName(it)
                    realm.copyFromRealm(it)
                }

                _chatList.value = currentUser?.conversations ?: emptyList()
                _loadingBar.value = false
            }

            override fun onError(exception: Throwable) {
                super.onError(exception)
                _chatList.value = emptyList()
                _loadingBar.value = false
                //TODO : need to implement
            }
        })
    }

    fun onRoomClick(it: Conversation) {
        val currentUsername = userName.value ?: return
        _navigation.value =
            HomeNavigation.GoToSelectedRoom(
                conversationId = it.id,
                roomName = it.displayName,
                currentUsername = currentUsername
            )

        updateMessageCount(it.id)
    }

    private fun updateCurrentUserName(user: User) {
        userName.value = user.userPreferences?.displayName ?: user.userName
    }

    private fun checkProfileCompleteStatus(userInfo: User): Boolean {
        return if (userInfo.userPreferences?.displayName.isNullOrBlank()) {
            _navigation.value = HomeNavigation.GoToProfile
            true
        } else {
            false
        }
    }

    private fun updateMessageCount(conversationId: String) {
        val user = realmSync.currentUser() ?: return
        val config = realmSync.getSyncConfig("user=${user.id}")

        Realm.getInstanceAsync(config, object : Realm.Callback() {
            override fun onSuccess(realm: Realm) {
                realm.executeTransactionAsync {
                    val userInfo = it.where<User>().equalTo("_id", user.id).findFirst()
                    userInfo?.apply {
                        this.conversations.find { it.id == conversationId }?.let {
                            it.unreadCount = 0
                        }
                    }
                }
            }
        })
    }

    private fun updateUserStatusToOnline() {
        val user = realmSync.currentUser() ?: return
        val config = realmSync.getSyncConfig("user=${user.id}")

        Realm.getInstanceAsync(config, object : Realm.Callback() {
            override fun onSuccess(realm: Realm) {
                realm.executeTransactionAsync {
                    val userInfo = it.where<User>().equalTo("_id", user.id).findFirst()
                    userInfo?.apply {
                        this.presence = "On-Line"
                    }
                }
            }
        })
    }
}