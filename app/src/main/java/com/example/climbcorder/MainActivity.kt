package com.example.climbcorder

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.climbcorder.ui.CameraFragment
import com.example.climbcorder.ui.HomeFragment
import com.example.climbcorder.ui.LibraryFragment
import com.example.climbcorder.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var keepAwakeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load home fragment by default
        loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_search -> {
                    loadFragment(CameraFragment())
                    true
                }
                R.id.navigation_library -> {
                    loadFragment(LibraryFragment())
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        applyKeepAwake()
    }

    override fun onResume() {
        super.onResume()
        applyKeepAwake()
    }

    override fun onPause() {
        super.onPause()
        keepAwakeRunnable?.let { handler.removeCallbacks(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        applyKeepAwake()
    }

    fun applyKeepAwake() {
        keepAwakeRunnable?.let { handler.removeCallbacks(it) }

        val prefs = getSharedPreferences("climbcorder_prefs", Context.MODE_PRIVATE)
        val seconds = prefs.getInt("keep_awake_seconds", 30)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val runnable = Runnable {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        keepAwakeRunnable = runnable
        handler.postDelayed(runnable, seconds * 1000L)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
