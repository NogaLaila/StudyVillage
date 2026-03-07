package com.example.studyvillage.ui.village

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studyvillage.data.owned.OwnedRepository
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.util.DefaultUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyVillageUiState(
    val coins: Long = 0L,
    val inventory: List<VillageUiItem> = emptyList(),
    val placed: List<VillageUiItem> = emptyList(),
    val selectedInstanceId: String? = null,
    val message: String? = null,
    val loading: Boolean = false
)

class MyVillageViewModel(
    private val userRepo: UserRepository,
    private val ownedRepo: OwnedRepository,
    private val shopDao: ShopDao
) : ViewModel() {

    private val _ui = MutableStateFlow(MyVillageUiState())
    val ui: StateFlow<MyVillageUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, message = null)

            val uid = DefaultUser.UID
            val email = DefaultUser.EMAIL

            try {
                userRepo.syncUser(uid, email)
                ownedRepo.syncOwned(uid)

                val coins = userRepo.getLocalUser(uid)?.coins ?: 0L

                val invEntities = ownedRepo.getInventory(uid)
                val placedEntities = ownedRepo.getPlaced(uid)

                val invUi = invEntities.map { e -> e.toUi(shopDao) }
                val placedUi = placedEntities.map { e -> e.toUi(shopDao) }

                _ui.value = _ui.value.copy(
                    coins = coins,
                    inventory = invUi,
                    placed = placedUi,
                    selectedInstanceId = null,
                    loading = false
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    message = e.message ?: "Load failed"
                )
            }
        }
    }

    fun selectForPlacement(instanceId: String) {
        _ui.value = _ui.value.copy(
            selectedInstanceId = instanceId,
            message = "Tap the island to place it"
        )
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selectedInstanceId = null, message = null)
    }

    fun placeSelected(x: Int, y: Int) {
        val instanceId = _ui.value.selectedInstanceId ?: return

        viewModelScope.launch {
            val uid = DefaultUser.UID
            try {
                ownedRepo.place(uid, instanceId, x, y)
                load()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(message = e.message ?: "Place failed")
            }
        }
    }
}

private suspend fun com.example.studyvillage.data.owned.local.OwnedInstanceEntity.toUi(
    shopDao: ShopDao
): VillageUiItem {
    val shop = shopDao.getItemById(itemId)
    return VillageUiItem(
        instanceId = instanceId,
        itemId = itemId,
        title = shop?.name ?: itemId,
        imageName = shop?.imageName ?: "ic_placeholder",
        x = x,
        y = y
    )
}