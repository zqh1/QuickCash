package ca.dal.csci3130.quickcash.joblisting;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Objects;

import ca.dal.csci3130.quickcash.MainActivity;
import ca.dal.csci3130.quickcash.R;
import ca.dal.csci3130.quickcash.common.Constants;
import ca.dal.csci3130.quickcash.common.DAO;
import ca.dal.csci3130.quickcash.jobmanagement.Job;
import ca.dal.csci3130.quickcash.usermanagement.SessionManager;


public class ViewJobsMapActivity extends FragmentActivity implements OnMapReadyCallback {

    public GoogleMap mMap;
    ArrayList<Job> pins = new ArrayList<>();
    FusedLocationProviderClient client;
    static final Integer MAP_REQUEST_CODE = 4254;
    SupportMapFragment mapFragment;
    public LatLng currentLocation;
    Button go_btn;
    EditText searchDistance;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_jobs_map);
        init();
        connectToFBDB();
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        client = LocationServices.getFusedLocationProviderClient(this);
        getCurrentLocation();
    }

    private void init() {
        recieveJobs();
        go_btn = findViewById(R.id.searchDistancesubmit);
        searchDistance = findViewById(R.id.searchDistanceFilling);
        searchDistance.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event){
                if(actionId == EditorInfo.IME_ACTION_DONE){
                    getFilteredJobs(view);
                    return true;
                }
                return false;
            }
        });
        go_btn.setOnClickListener(view -> getFilteredJobs(view));

    }

    private void getFilteredJobs(View view) {

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        if (searchDistance.getText().length() == 0) {
            Toast.makeText(this, "Please enter distance", Toast.LENGTH_SHORT).show();
            return;
        }

        double distance = Double.valueOf(searchDistance.getText().toString());

        mMap.clear();
        placePinsOnMap(distance);


    }
    /*
        This method Placed pins on the maps generated.
        It also, filter the search for the distance proximity
     */
    private void placePinsOnMap(double filterDistance) {

        MarkerOptions markerOptions = new MarkerOptions().position(currentLocation).title("You are here");
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 10));
        Objects.requireNonNull(mMap.addMarker(markerOptions)).showInfoWindow();

        if (filterDistance == 0) {

            for (Job j : pins) {
                LatLng jobPin = new LatLng(j.getLatitude(), j.getLongitude());
                double distance = Math.sqrt(Math.pow((111.3 * (j.getLatitude() - currentLocation.latitude)), 2) + Math.pow((71.5 * (j.getLongitude() - currentLocation.longitude)), 2));
                mMap.addMarker(new MarkerOptions().position(jobPin).title(j.getTitle()).snippet(Double.toString(Math.round(distance * 100.0) / 100.0) + "km"));
            }
        } else {

            for (Job j : pins) {
                LatLng jobPin = new LatLng(j.getLatitude(), j.getLongitude());
                double distance = Math.sqrt(Math.pow((111.3 * (j.getLatitude() - currentLocation.latitude)), 2) + Math.pow((71.5 * (j.getLongitude() - currentLocation.longitude)), 2));
                if (distance <= filterDistance)
                    mMap.addMarker(new MarkerOptions().position(jobPin).title(j.getTitle()).snippet(Double.toString(Math.round(distance * 100.0) / 100.0) + "km"));
            }

        }
    }

    private void connectToFBDB() {

        //Verify the user is valid, if user not loaded correctly, restart application from main
        if (SessionManager.getUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            return;
        }

        //Query firebase depending on if user is employee or employer
        final FirebaseRecyclerOptions<Job> options;

        if (SessionManager.getUser().getIsEmployee().equals("y")) {
            options = new FirebaseRecyclerOptions.Builder<Job>()
                    .setQuery(FirebaseDatabase.getInstance(Constants.FIREBASE_URL)
                            .getReference().child("Job"), Job.class).build();
        } else {
            options = new FirebaseRecyclerOptions.Builder<Job>()
                    .setQuery(FirebaseDatabase.getInstance(Constants.FIREBASE_URL)
                            .getReference().child("Job")
                            .orderByChild("employerID").equalTo(SessionManager.getUserID()), Job.class).build();
        }

    }

    private void recieveJobs() {
        DAO.getJobReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot shot : snapshot.getChildren()) {
                    pins.add(shot.getValue(Job.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    protected void getCurrentLocation() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Task<Location> task = client.getLastLocation();

        task.addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                mapFragment.getMapAsync(ViewJobsMapActivity.this);
            }
        });
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        placePinsOnMap(0);
    }

}