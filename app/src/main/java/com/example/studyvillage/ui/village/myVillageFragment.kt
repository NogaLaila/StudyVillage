package com.example.studyvillage.ui.village

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studyvillage.R
import com.example.studyvillage.data.owned.OwnedRepository
import com.example.studyvillage.data.owned.remote.OwnedRemote
import com.example.studyvillage.data.shop.ShopDao
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.databinding.FragmentMyVillageBinding
import com.example.studyvillage.util.UserSession
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import kotlin.math.floor

class MyVillageFragment : Fragment(R.layout.fragment_my_village) {

    private var _binding: FragmentMyVillageBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepo: UserRepository
    private lateinit var ownedRepo: OwnedRepository
    private lateinit var shopDao: ShopDao

    private var selectedInstanceId: String? = null
    private var controlsOpenForInstanceId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyVillageBinding.bind(view)

        val db = DataBaseProvider.get(requireContext())
        userRepo = UserRepository(db.userDao(), UserRemote())
        ownedRepo = OwnedRepository(db.ownedInstanceDao(), OwnedRemote())
        shopDao = db.shopDao()

        if (!UserSession.isLoggedIn()) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        refreshCoins()

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = UserSession.currentUid ?: return@launch
            try {
                ownedRepo.syncOwned(uid)
            } catch (_: Exception) {
            }
            renderPlacedItems()
        }

        binding.btnPlace.setOnClickListener {
            openInventoryBottomSheet()
        }

        binding.placedLayer.setOnTouchListener { v, event ->
            val instanceId = selectedInstanceId ?: return@setOnTouchListener false
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

            val uid = UserSession.currentUid
            if (uid == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnTouchListener true
            }

            controlsOpenForInstanceId = null
            val (x, y) = tapToGrid(v.width, v.height, event.x, event.y)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    ownedRepo.place(uid, instanceId, x, y)
                    selectedInstanceId = null
                    Toast.makeText(requireContext(), "Placed!", Toast.LENGTH_SHORT).show()
                    renderPlacedItems()
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        e.message ?: "Place failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            true
        }

        binding.islandTapLayer.setOnTouchListener { _, _ -> false }

        binding.placedLayer.setOnClickListener {
            if (selectedInstanceId == null && controlsOpenForInstanceId != null) {
                controlsOpenForInstanceId = null
                viewLifecycleOwner.lifecycleScope.launch {
                    renderPlacedItems()
                }
            }
        }
    }

    private fun refreshCoins() {
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch
            val uid = UserSession.currentUid
            val email = UserSession.currentEmail

            if (uid == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val localUser = userRepo.getLocalUser(uid)
            b.txtCoins.text = (localUser?.coins ?: 0L).toString()

            runCatching { userRepo.syncUser(uid, email) }

            val updated = userRepo.getLocalUser(uid)
            val b2 = _binding ?: return@launch
            b2.txtCoins.text = (updated?.coins ?: 0L).toString()
        }
    }

    private fun openInventoryBottomSheet() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = UserSession.currentUid
            if (uid == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                ownedRepo.syncOwned(uid)
            } catch (_: Exception) {
            }

            val inventory = ownedRepo.getInventory(uid)
            if (inventory.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Inventory empty. Buy an item first.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val uiItems = inventory.map { inv ->
                val shop = shopDao.getItemById(inv.itemId)
                val title = shop?.name ?: inv.itemId
                val imageName = shop?.imageName ?: "ic_placeholder"
                val resId =
                    resources.getIdentifier(imageName, "drawable", requireContext().packageName)

                InventoryUiItem(
                    instanceId = inv.instanceId,
                    itemId = inv.itemId,
                    title = title,
                    imageRes = if (resId != 0) resId else R.drawable.panda_shop
                )
            }

            val dialog = BottomSheetDialog(requireContext())
            val rv = androidx.recyclerview.widget.RecyclerView(requireContext())
            rv.layoutManager = LinearLayoutManager(requireContext())

            val adapter = InventoryAdapter { selected ->
                selectedInstanceId = selected.instanceId
                controlsOpenForInstanceId = null
                Toast.makeText(requireContext(), "Tap the island to place it", Toast.LENGTH_SHORT)
                    .show()
                dialog.dismiss()
            }

            rv.adapter = adapter
            adapter.submit(uiItems)

            dialog.setContentView(rv)
            dialog.show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private suspend fun renderPlacedItems() {
        val b = _binding ?: return
        val uid = UserSession.currentUid ?: return
        val placed = ownedRepo.getPlaced(uid)

        b.placedLayer.removeAllViews()

        b.placedLayer.post {
            val b2 = _binding ?: return@post

            val layerW = b2.placedLayer.width
            val layerH = b2.placedLayer.height
            if (layerW == 0 || layerH == 0) return@post

            val cols = 10
            val rows = 10
            val cellW = layerW / cols.toFloat()
            val cellH = layerH / rows.toFloat()

            placed.forEach { p ->
                val x = p.x ?: return@forEach
                val y = p.y ?: return@forEach

                viewLifecycleOwner.lifecycleScope.launch {
                    val b3 = _binding ?: return@launch

                    val shop = shopDao.getItemById(p.itemId)
                    val imageName = shop?.imageName ?: "ic_placeholder"
                    val resId = resources.getIdentifier(
                        imageName,
                        "drawable",
                        requireContext().packageName
                    )
                    val finalRes = if (resId != 0) resId else R.drawable.panda_shop

                    val imageSize = (cellW.coerceAtMost(cellH) * 1.45f).toInt()
                    val actionsAreaHeight = dp(40)
                    val containerWidth = imageSize
                    val containerHeight = imageSize + actionsAreaHeight

                    val container = FrameLayout(requireContext()).apply {
                        clipChildren = false
                        clipToPadding = false
                        isClickable = true
                        isFocusable = true
                    }

                    val itemImg = ImageView(requireContext()).apply {
                        setImageResource(finalRes)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }

                    container.addView(
                        itemImg,
                        FrameLayout.LayoutParams(
                            containerWidth,
                            imageSize
                        ).apply {
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        }
                    )

                    val actionsVisible = controlsOpenForInstanceId == p.instanceId

                    if (actionsVisible) {
                        val actionsBar = createActionsBar(
                            onMoveClick = {
                                selectedInstanceId = p.instanceId
                                controlsOpenForInstanceId = null
                                Toast.makeText(
                                    requireContext(),
                                    "Tap the island to move it",
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    renderPlacedItems()
                                }
                            },
                            onRemoveClick = {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val currentUid = UserSession.currentUid
                                    if (currentUid == null) {
                                        Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    try {
                                        ownedRepo.unplaceToInventory(currentUid, p.instanceId)
                                        controlsOpenForInstanceId = null
                                        if (selectedInstanceId == p.instanceId) {
                                            selectedInstanceId = null
                                        }
                                        renderPlacedItems()
                                        Toast.makeText(
                                            requireContext(),
                                            "Returned to inventory",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            requireContext(),
                                            e.message ?: "Failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )

                        container.addView(
                            actionsBar,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                bottomMargin = dp(2)
                            }
                        )
                    }

                    container.setOnClickListener {
                        if (selectedInstanceId != null) return@setOnClickListener

                        controlsOpenForInstanceId =
                            if (controlsOpenForInstanceId == p.instanceId) null else p.instanceId

                        viewLifecycleOwner.lifecycleScope.launch {
                            renderPlacedItems()
                        }
                    }

                    container.setOnLongClickListener {
                        showPlacedItemMenu(container, p.instanceId)
                        true
                    }

                    val lp = FrameLayout.LayoutParams(containerWidth, containerHeight).apply {
                        leftMargin = (x * cellW).toInt()
                        topMargin = (y * cellH).toInt()
                    }

                    b3.placedLayer.addView(container, lp)
                }
            }
        }
    }

    private fun createActionsBar(
        onMoveClick: () -> Unit,
        onRemoveClick: () -> Unit
    ): LinearLayout {

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.parseColor("#ffe8a8"))
        }

        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(4))
            elevation = dp(12).toFloat()
            isClickable = true
            isFocusable = true
        }

        fun createButton(
            icon: Int,
            description: String,
            click: () -> Unit
        ): ImageButton {
            return ImageButton(requireContext()).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#66000000"))
                }

                imageTintList = ColorStateList.valueOf(Color.WHITE)
                setImageResource(icon)
                contentDescription = description
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6))

                isClickable = true
                isFocusable = true

                setOnClickListener { click() }
            }
        }

        val moveBtn = createButton(
            android.R.drawable.ic_menu_edit,
            "Move",
            onMoveClick
        )

        val removeBtn = createButton(
            android.R.drawable.ic_menu_delete,
            "Remove to inventory",
            onRemoveClick
        )

        val btnParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            marginEnd = dp(6)
        }

        val lastBtnParams = LinearLayout.LayoutParams(dp(26), dp(26))

        bar.addView(moveBtn, btnParams)
        bar.addView(removeBtn, lastBtnParams)

        return bar
    }

    private fun tapToGrid(viewW: Int, viewH: Int, tapX: Float, tapY: Float): Pair<Int, Int> {
        val cols = 10
        val rows = 10

        val colW = viewW.toFloat() / cols
        val rowH = viewH.toFloat() / rows

        val x = floor(tapX / colW).toInt().coerceIn(0, cols - 1)
        val y = floor(tapY / rowH).toInt().coerceIn(0, rows - 1)

        return x to y
    }

    private fun showPlacedItemMenu(anchor: View, instanceId: String) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)

        popup.menu.add(0, 1, 0, "Edit (Move)")
        popup.menu.add(0, 2, 1, "Remove (Back to inventory)")
        popup.menu.add(0, 3, 2, "Delete permanently")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    selectedInstanceId = instanceId
                    controlsOpenForInstanceId = null
                    Toast.makeText(requireContext(), "Tap the island to move it", Toast.LENGTH_SHORT)
                        .show()
                    viewLifecycleOwner.lifecycleScope.launch { renderPlacedItems() }
                    true
                }

                2 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val uid = UserSession.currentUid
                        if (uid == null) {
                            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        try {
                            ownedRepo.unplaceToInventory(uid, instanceId)
                            controlsOpenForInstanceId = null
                            if (selectedInstanceId == instanceId) selectedInstanceId = null
                            renderPlacedItems()
                            Toast.makeText(
                                requireContext(),
                                "Returned to inventory",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext(),
                                e.message ?: "Failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    true
                }

                3 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val uid = UserSession.currentUid
                        if (uid == null) {
                            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        try {
                            ownedRepo.delete(uid, instanceId)
                            controlsOpenForInstanceId = null
                            if (selectedInstanceId == instanceId) selectedInstanceId = null
                            renderPlacedItems()
                            Toast.makeText(
                                requireContext(),
                                "Deleted permanently",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext(),
                                e.message ?: "Failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}