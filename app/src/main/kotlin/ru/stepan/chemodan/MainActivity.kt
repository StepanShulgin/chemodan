package ru.stepan.chemodan

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import ru.stepan.chemodan.databinding.ActivityMainBinding
import ru.stepan.chemodan.ui.ReferenceRateFragment
import ru.stepan.chemodan.ui.TripsFragment
import ru.stepan.chemodan.ui.uiText

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars =
            resources.getBoolean(R.bool.light_system_bars)
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightNavigationBars =
            resources.getBoolean(R.bool.light_system_bars)
        ViewCompat.setOnApplyWindowInsetsListener(binding.windowRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val target =
                if (item.itemId == R.id.nav_trips) TripsFragment() else ReferenceRateFragment()
            if (current?.javaClass != target.javaClass) {
                supportFragmentManager.popBackStackImmediate(
                    null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )
                show(target)
            }
            true
        }
        supportFragmentManager.addOnBackStackChangedListener { updateChrome() }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(
                    fm: androidx.fragment.app.FragmentManager,
                    f: Fragment,
                ) {
                    updateChrome()
                }
            },
            false,
        )
        if (savedInstanceState == null) show(TripsFragment())
    }

    fun show(fragment: Fragment, backStack: Boolean = false) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
            replace(R.id.fragment_container, fragment)
            if (backStack) addToBackStack(null)
        }
    }

    private fun updateChrome() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val root =
            fragment is TripsFragment ||
                (fragment is ReferenceRateFragment &&
                    supportFragmentManager.backStackEntryCount == 0)
        binding.bottomNavigation.visibility = if (root) View.VISIBLE else View.GONE
        binding.toolbar.navigationIcon =
            if (root) null
            else
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    this,
                    R.drawable.ic_back,
                )
        binding.toolbar.navigationContentDescription = uiText(R.string.back)
        binding.toolbar.title = if (root) uiText(R.string.app_brand) else ""
        binding.toolbar.menu.clear()
        if (fragment is ru.stepan.chemodan.ui.TripDetailsFragment) {
            binding.toolbar.menu.add(uiText(R.string.edit_trip)).apply {
                setIcon(R.drawable.ic_edit)
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    show(
                        ru.stepan.chemodan.ui.CreateTripFragment.newInstance(
                            fragment.requireArguments().getLong("trip_id")
                        ),
                        true,
                    )
                    true
                }
            }
        }
        if (root)
            binding.bottomNavigation.menu
                .findItem(if (fragment is TripsFragment) R.id.nav_trips else R.id.nav_converter)
                .isChecked = true
    }

    fun openCreatedTrip(id: Long) {
        supportFragmentManager.popBackStackImmediate()
        show(ru.stepan.chemodan.ui.TripDetailsFragment.newInstance(id), true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
