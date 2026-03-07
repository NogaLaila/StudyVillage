package com.example.studyvillage

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.studyvillage.data.shop.local.DataBaseProvider
import com.example.studyvillage.data.user.UserRepository
import com.example.studyvillage.data.user.remote.UserRemote
import com.example.studyvillage.databinding.ActivityMainBinding
import com.example.studyvillage.util.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.itemIconTintList = null
        binding.bottomNav.itemTextColor = null

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthScreen =
                destination.id == R.id.splashFragment ||
                        destination.id == R.id.welcomeFragment ||
                        destination.id == R.id.loginFragment ||
                        destination.id == R.id.registerFragment

            binding.bottomNav.visibility = if (isAuthScreen) View.GONE else View.VISIBLE
            binding.bottomNavBg.visibility = if (isAuthScreen) View.GONE else View.VISIBLE
            binding.btnLogout.visibility = if (isAuthScreen) View.GONE else View.VISIBLE
        }

        binding.btnLogout.setOnClickListener {
            logoutAndGoToLogin(navController)
        }
    }

    private fun logoutAndGoToLogin(navController: androidx.navigation.NavController) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = DataBaseProvider.get(applicationContext)
                val userRepo = UserRepository(db.userDao(), UserRemote())

                userRepo.clearLocalUsers()
                UserSession.logout()

                Toast.makeText(this@MainActivity, "Logged out", Toast.LENGTH_SHORT).show()

                navController.navigate(
                    R.id.loginFragment,
                    null,
                    NavOptions.Builder()
                        .setPopUpTo(navController.graph.startDestinationId, true)
                        .build()
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    e.message ?: "Logout failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}