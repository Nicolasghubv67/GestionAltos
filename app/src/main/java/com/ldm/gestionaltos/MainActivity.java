package com.ldm.gestionaltos;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;

        navController = navHostFragment.getNavController();
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // Mantiene el tab seleccionado en sync cuando navegas a subfragments
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            int id = destination.getId();

            if (id == R.id.scanFragment) {
                bottomNavigationView.getMenu().findItem(R.id.scanFragment).setChecked(true);
            } else if (id == R.id.racksFragment) {
                bottomNavigationView.getMenu().findItem(R.id.racksFragment).setChecked(true);
            } else if (id == R.id.helpFragment) {
                bottomNavigationView.getMenu().findItem(R.id.helpFragment).setChecked(true);
            }
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int destinationId = item.getItemId();

            if (!isTopLevelDestination(destinationId)) return false;

            navigateToTopLevel(destinationId);
            return true;
        });

        bottomNavigationView.setOnItemReselectedListener(item -> {
            int destinationId = item.getItemId();
            if (!isTopLevelDestination(destinationId)) return;

            popToGraphStart();
            navigateToTopLevel(destinationId);
        });
    }

    private boolean isTopLevelDestination(int destinationId) {
        return destinationId == R.id.scanFragment
                || destinationId == R.id.racksFragment
                || destinationId == R.id.helpFragment;
    }

    private void popToGraphStart() {
        if (navController == null) return;
        int startDest = navController.getGraph().getStartDestinationId();
        navController.popBackStack(startDest, false);
    }

    private void navigateToTopLevel(int destinationId) {
        if (navController == null) return;

        // Si ya estás en el destino, nada
        NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == destinationId) return;

        popToGraphStart();

        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .build();

        navController.navigate(destinationId, null, navOptions);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController == null) return super.onSupportNavigateUp();
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}