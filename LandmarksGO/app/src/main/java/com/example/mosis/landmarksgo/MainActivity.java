package com.example.mosis.landmarksgo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mosis.landmarksgo.authentication.LoginActivity;
import com.example.mosis.landmarksgo.authentication.User;
import com.example.mosis.landmarksgo.friends.Friends;
import com.example.mosis.landmarksgo.highscore.HighScore;
import com.example.mosis.landmarksgo.landmark.AddLandmark;
import com.example.mosis.landmarksgo.landmark.Landmark;
import com.example.mosis.landmarksgo.other.BackgroundService;
import com.example.mosis.landmarksgo.other.BitmapManipulation;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.example.mosis.landmarksgo.R.id.map;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, AdapterView.OnItemSelectedListener, OnMapReadyCallback {

    private FirebaseAuth.AuthStateListener authListener;
    private FirebaseAuth auth;
    private FirebaseUser loggedUser;
    private StorageReference storage;

    private static final String TAG = "LandmarksGO";
    private NavigationView navigationView;

    private GoogleMap mMap;
    private HashMap<String, Marker> mapMarkersLandmarks = new HashMap<String, Marker>();
    private HashMap<String, Marker> mapUseridMarker = new HashMap<String, Marker>();
    private HashMap<Marker, User> mapMarkerUser = new HashMap<Marker, User>();

    private int spinnerSelectedSearchOption;
    static File localFileProfileImage = null;

    private Marker myLocation = null;

    public static final int MARKER_LANDMARK = 1;
    public static final int MARKER_USER = 2;

    private static Bitmap profilePhotoBitmap=null;
    private static View headerView;
    private static ImageView profilePicture;

    private static boolean settingsShowPlayers;
    private static boolean settingsShowFriends;
    private static boolean settingsBackgroundService;
    private static int settingsGpsRefreshTime;

    private static ArrayList<String> friendList;
    private static boolean pauseWaitingForFriendsList =false;
    private Intent backgroundService;

    public static HashMap<String,Marker> friendsMarker;
    public static HashMap<String,Marker> landmarksMarker;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //to use network operations in main thread (BackgroundService)
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        auth = FirebaseAuth.getInstance();
        loggedUser = auth.getCurrentUser();

        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                loggedUser = firebaseAuth.getCurrentUser();
                if (loggedUser == null) {
                    // user auth state is changed - user is null
                    // launch login activity
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            }
        };

        if(loggedUser!=null){
            setUpLayout();

            //Google Maps
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(map);
            mapFragment.getMapAsync(this);

            backgroundService = new Intent(MainActivity.this, BackgroundService.class);
        }
        friendList = new ArrayList<String>();
        friendsMarker = new HashMap<>();
        landmarksMarker = new HashMap<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        auth.addAuthStateListener(authListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
        //stopService(backgroundService);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(loggedUser!=null){
            customizeUI();
            readSettingsFromServer();
        }
        //if(mMap!=null)
            //mMap.clear(); //TODO: should this be here?
    }

    private void getFriendsUidFromServer() {    //TODO: This works only when user exits and opens the app. Doesn't work the there is a change to friends in Friends activity.
        Log.d(TAG,"getFriendsUidFromServer started");
        pauseWaitingForFriendsList =true;
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("friends/" + loggedUser.getUid());
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG,"getFriendsUidFromServer onDataChange started");
                for (DataSnapshot singleSnapshot : dataSnapshot.getChildren()) {
                    String json = singleSnapshot.toString();

                    //TODO: deserialize via class, not like this
                    final String friendUid = json.substring(json.indexOf("value = ") + 8, json.length() - 2);
                    Log.d(TAG, "getFriendsUidFromServer friendUid: " + friendUid);
                    friendList.add(friendUid);
                }
                Log.d(TAG,"getFriendsUidFromServer onDataChange ended");
                Log.d(TAG,"getFriendsUidFromServer onDataChange friendList:" + friendList);
                pauseWaitingForFriendsList =false;
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }});
        Log.d(TAG,"getFriendsUidFromServer friendList:" + friendList);
        Log.d(TAG,"getFriendsUidFromServer ended");
    }

    private void readSettingsFromServer() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference("users").child(loggedUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                User u = dataSnapshot.getValue(User.class);
                settingsShowFriends = u.showfriends;
                settingsShowPlayers = u.showplayers;
                settingsBackgroundService = u.workback;
                settingsGpsRefreshTime = u.gpsrefresh;

                if(settingsBackgroundService){
                    backgroundService.putExtra("settingsGpsRefreshTime", settingsGpsRefreshTime);
                    backgroundService.putExtra("loggedUserUid", loggedUser.getUid());

                    if(!isMyServiceRunning(BackgroundService.class)){
                        startService(backgroundService);
                    }
                    /*
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            startService(backgroundService);
                        }
                    };
                    Thread backgroundServiceThread = new Thread(r);
                    backgroundServiceThread.start();
                    */
                }else{
                    Toast.makeText(MainActivity.this, "Stopping background service", Toast.LENGTH_SHORT).show();
                    stopService(backgroundService);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        //stopService(backgroundService);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_sendfriendrequest) {
            //TODO: temporary
            //DatabaseReference dbRef = database.getReference("friends/");

            //dbRef.push().setValue(friendship);
            //dbRef.child(myUid).setValue(getRandomFriendship());
            //root.child(user.getUid()).setValue(friendship);

            //Query phoneQuery = dbRef.orderByChild(myUid).equalTo(myUid);
            //Query phoneQuery = dbRef.equalTo(myUid);

            pushRandomFriendships(loggedUser.getUid());
            return true;
        }

        if (id==R.id.action_getfriends){
            Log.d(TAG,"My myUid:" + loggedUser.getUid());
            getFriends();
        }
        return super.onOptionsItemSelected(item);
    }

    private void getFriends() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference dbRef = database.getReference("friends/" + loggedUser.getUid());
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG,"u valueevent listener: count " + dataSnapshot.getChildrenCount());
                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    String json = singleSnapshot.toString();
                    Log.d(TAG,"json: " + json);

                    //TODO: deserialize via class, not like this
                    String friendUid = json.substring(json.indexOf("value = ") + 8);
                    Log.d(TAG,"friendUid: " + friendUid);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "onCancelled", databaseError.toException());
            }
        });
    }

    @NonNull
    private String getRandomFirebaseUid() {
        String randomUid = UUID.randomUUID().toString();
        randomUid = randomUid.replaceAll("-", "");
        randomUid = randomUid.substring(0, 28);
        return randomUid;
    }

    private List<String> getRandomFriendship(){
        Random ran = new Random();
        int x = ran.nextInt(5) + 2;
        List<String> friendsList = new ArrayList<>();
        for(int i=0;i<x;i++){
            friendsList.add(getRandomFirebaseUid());
        }
        //return new Friendship(user.getUid(), friendsList);
        return friendsList;
    }

    private void pushRandomFriendships(String uid){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference dbRef = database.getReference("friends/");
        dbRef.child(uid).setValue(getRandomFriendship());
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_map) {
            //Intent intent = new Intent(MainActivity.this,MapsActivity.class);
            //startActivity(intent);
        } else if (id == R.id.nav_friends){
            Intent intent = new Intent(MainActivity.this,Friends.class);
            startActivity(intent);
        } else if (id == R.id.nav_highscore){
            Intent intent = new Intent(MainActivity.this,HighScore.class);
            startActivity(intent);
        } else if (id == R.id.nav_settings){
            Intent intent = new Intent(MainActivity.this,SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_exit){
            stopService(backgroundService);
            moveTaskToBack(true);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    ///////////////////////////////////////////////////////

    private void setUpLayout() {
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddLandmark.class);
                startActivity(intent);
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        //Navigation Drawer
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //Search above map
        Spinner spinner = (Spinner) findViewById(R.id.spinnerMapSearchCategory);
        spinner.setOnItemSelectedListener(this);

        setUpSearchView();
    }

    private void customizeUI() {
        Log.d(TAG, "MainActivity:changeUI:photoUrl started");
        loggedUser = auth.getCurrentUser();
        if(loggedUser!=null) {
            Log.d(TAG, "MainActivity:changeUI: user!=null");

            headerView = navigationView.getHeaderView(0);

            String displayName = loggedUser.getDisplayName();
            String email = loggedUser.getEmail();

            Log.d(TAG, "MainActivity:changeUI: displayName=" + displayName);
            final TextView profileName = (TextView) headerView.findViewById(R.id.textViewProfileName);
            if(displayName!=null){
                profileName.setText(displayName);
            }else{
                //there is no displayName when users is signed in with an email
                profileName.setText("");
            }

            Log.d(TAG, "MainActivity:changeUI: email=" + email);
            if(email!=null){
                TextView profileEmail = (TextView) headerView.findViewById(R.id.textViewProfileEmail);
                profileEmail.setText(email);
            }

            profilePicture = (ImageView) headerView.findViewById(R.id.imageViewProfilePicture);
            changeProfilePhoto(headerView, profilePicture);
        }

        //Spinner for search
        Spinner spinner = (Spinner) findViewById(R.id.spinnerMapSearchCategory);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.search_type, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
    }

    private void changeProfilePhoto(View headerView, final ImageView iv) {
        //TODO: Save file app folder, load that file first then download.
        Uri photoUrl = FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl();
        Log.d(TAG, "MainActivity:changeUI: photoUrl=" + photoUrl);

        try {
            localFileProfileImage = File.createTempFile("profileImage",".jpg");
            Log.d(TAG,"localFile.getAbsolutePath()" + localFileProfileImage.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        storage = FirebaseStorage.getInstance().getReference().child("profile_images/" + loggedUser.getUid() + ".jpg");
        storage.getFile(localFileProfileImage).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                Bitmap bitmap = BitmapFactory.decodeFile(localFileProfileImage.getAbsolutePath());
                if(bitmap!=null){
                    Log.d(TAG,"Bitmap is NOT null");
                    bitmap = BitmapManipulation.getCroppedBitmap(bitmap);
                    iv.setImageBitmap(bitmap);
                }else{
                    Log.d(TAG,"Bitmap is null");
                }

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                //Toast.makeText(MainActivity.this, "Error downloading/saving profile image", Toast.LENGTH_SHORT).show();
                //TODO: Can't display this, maybe user doesn't have a profile photo
            }
        });
    }

    //for Search above map
    private void setUpSearchView() {
        SearchView search=(SearchView) findViewById(R.id.searchViewMap);
        search.setQueryHint("");

        search.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                //Toast.makeText(getBaseContext(), "onFocusChange: " + String.valueOf(hasFocus), Toast.LENGTH_SHORT).show();
            }
        });

        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                //Toast.makeText(getBaseContext(), "onQueryTextSubmit: " + query, Toast.LENGTH_SHORT).show();
                searchMarker(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //Toast.makeText(getBaseContext(), "onQueryTextChange: " + newText, Toast.LENGTH_SHORT).show();
                searchMarker(newText);
                return false;
            }
        });
    }

    private void searchMarker(String query) {
        Log.d(TAG, "MainActivity: searchMarker: searching for " + query);
        Log.d(TAG, "MainActivity: searchMarker: spinnerSelectedSearchOption=" + spinnerSelectedSearchOption);
        Marker mMarker = null;
        if(spinnerSelectedSearchOption==0){ //searching for name
            mMarker = mapMarkersLandmarks.get(query);
        }

        if(mMarker!=null){
            mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(mMarker.getPosition().latitude, mMarker.getPosition().longitude)));
            mMarker.showInfoWindow();
            //TODO: Add smooth animation

            //Force hide the onscreen keyboard
            //InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            //imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
        };
        Log.d(TAG, "MainActivity: searchMarker: found " + mMarker);
    }

    //for Spinner above map
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String arr[] = getResources().getStringArray(R.array.search_type);
        //Toast.makeText(this, "Searching: " + arr[position], Toast.LENGTH_SHORT).show();
        spinnerSelectedSearchOption = position;
    }
    //for Spinner above map
    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    //Google Maps
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                //Toast.makeText(getApplicationContext(), marker.getTitle(), Toast.LENGTH_SHORT).show();
                User user = null;
                user = mapMarkerUser.get(marker);
                if(user!=null){
                    Intent intent = new Intent(MainActivity.this, PlayerInfo.class);
                    intent.putExtra("uid",user.uid);
                    intent.putExtra("firstname",user.firstName);
                    intent.putExtra("lastname",user.lastName);
                    startActivity(intent);
                }

                return false;
            }
        });

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                Toast.makeText(getApplicationContext(), point.toString(), Toast.LENGTH_SHORT).show();
            }
        });

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(this,"Please grant all permissions",Toast.LENGTH_SHORT).show();
            return;
        }
        mMap.setMyLocationEnabled(true);

        //TODO: Make this better
        int height = 50;
        int width = 50;
        BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(R.drawable.obama);
        Bitmap b=bitmapdraw.getBitmap();
        final Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false);

        if (mMap != null) {
            mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location arg0) {
                    //mMap.addMarker(new MarkerOptions().position(new LatLng(arg0.getLatitude(), arg0.getLongitude())).title("It's Me!"));
                    if(myLocation!=null){
                        myLocation.remove();
                    }

                    //addMarkers(arg0.getLatitude(),arg0.getLongitude(),"I","", smallMarker, false, MARKER_USER);

                    //>This was moved to BackgroundService
                    //DatabaseReference users = FirebaseDatabase.getInstance().getReference("users");
                    //users.child(loggedUser.getUid()).child("lat").setValue(arg0.getLatitude());
                    //users.child(loggedUser.getUid()).child("lon").setValue(arg0.getLongitude());
                }
            });
        }
        getFriendsUidFromServer();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                loadLandmarksFromServer();
            }
        };
        Thread loadLandmarksFromServerThread = new Thread(r);
        loadLandmarksFromServerThread.start();

        Runnable r2 = new Runnable() {
            @Override
            public void run() {
                while(pauseWaitingForFriendsList){
                    synchronized (this) {
                        try {
                            wait(100);
                            Log.d(TAG,"Cekam 100ms");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                loadAllPlayersFromServer();
            }
        };
        Thread loadAllPlayersFromServerThread = new Thread(r2);
        loadAllPlayersFromServerThread.start();
    }

    private void loadLandmarksFromServer() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("landmarks");

        //https://firebase.google.com/docs/database/android/lists-of-data
        ChildEventListener childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                //Log.d(TAG, "onChildAdded:" + dataSnapshot.getKey());
                Landmark landmark = dataSnapshot.getValue(Landmark.class);
                Log.d(TAG, "onChildAdded:" + landmark.title);
                Marker marker = addMarkers(landmark.lat, landmark.lon, landmark.title, "", null, false, "");

                //Add to searchable HashMap
                mapMarkersLandmarks.put(landmark.title, marker);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(TAG, "onChildChanged:" + dataSnapshot.getKey());
                //We don't have a ability to change a landmark
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onChildRemoved:" + dataSnapshot.getKey());
                //We don't have a ability to change a landmark
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(TAG, "onChildMoved:" + dataSnapshot.getKey());
                //We don't have a ability to move a landmark in DB.
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "postComments:onCancelled", databaseError.toException());
            }
        };
        myRef.addChildEventListener(childEventListener);
    }

    private void loadAllPlayersFromServer() {
        Log.d(TAG,"loadAllPlayersFromServer started");
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("users");

        //https://firebase.google.com/docs/database/android/lists-of-data
        ChildEventListener childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                if(settingsShowPlayers){
                    Log.d(TAG,"TIMESTAMP.values: " + ServerValue.TIMESTAMP.values());
                    //Log.d(TAG, "onChildAdded:" + dataSnapshot.getKey());
                    final User user = dataSnapshot.getValue(User.class);
                    Log.d(TAG, "onChildAdded:" + user.firstName + " uid:" + user.uid);

                    Marker marker = addMarkers(user.lat, user.lon, user.firstName + " " + user.lastName, "", null, false, user.uid);
                    mapUseridMarker.put(user.uid, marker);
                    mapMarkerUser.put(marker, user);
                }
        }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                //Log.d(TAG, "onChildChanged:" + dataSnapshot.getKey());
                User user = dataSnapshot.getValue(User.class);
                //Log.d(TAG, "onChildChanged:" + user.firstName + " uid:" + user.uid);

                Marker mMarker;
                mMarker = mapUseridMarker.get(user.uid);

                if(mMarker!=null) {
                    Log.d(TAG,"Brisem marker");
                    mMarker.remove();
                    Marker marker = addMarkers(user.lat, user.lon, user.firstName + " " + user.lastName, null, null, false, user.uid);

                    //Add to searchable HashMap
                    mapUseridMarker.remove(user.uid);
                    mapUseridMarker.put(user.uid, marker);

                    mapMarkerUser.put(marker, user);    //TODO: remove previous marker
                }else{
                    Log.d(TAG,"Ne brisem marker");
                }

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onChildRemoved:" + dataSnapshot.getKey());
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
                Log.d(TAG, "onChildMoved:" + dataSnapshot.getKey());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "postComments:onCancelled", databaseError.toException());
            }
        };
        myRef.addChildEventListener(childEventListener);
    }

    private Marker addMarkers(double lat, double lng, String title, String snippet, Bitmap icon, boolean moveCamera, String uid){
        Log.d(TAG,"addMarkers uid:" + uid);
        Marker marker = null;

        MarkerOptions mo = new MarkerOptions();
        mo.position(new LatLng(lat, lng));
        mo.title(title);
        if(snippet!=null && snippet!=""){
            mo.snippet(snippet);
        }
        if(uid==null || uid==""){
            mo.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        }else{
            //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            //.icon(BitmapDescriptorFactory.fromBitmap(BitmapManipulation.getMarkerBitmapFromView(icon, MainActivity.this)))); //of course, this takes too much time to process
            if(uid.equals(loggedUser.getUid())){
                mo.icon(BitmapDescriptorFactory.fromBitmap(BitmapManipulation.getMarkerBitmapFromView(R.drawable.person_i, MainActivity.this)));
            }else{
                if(friendList.contains(uid)){
                    mo.icon(BitmapDescriptorFactory.fromBitmap(BitmapManipulation.getMarkerBitmapFromView(R.drawable.person_friend, MainActivity.this)));
                }else{
                    mo.icon(BitmapDescriptorFactory.fromBitmap(BitmapManipulation.getMarkerBitmapFromView(R.drawable.person, MainActivity.this)));

                }
            }
        }

        marker = mMap.addMarker(mo);
        if(friendList.contains(uid)){
            if(friendsMarker.containsKey(uid)){
                friendsMarker.remove(uid);
            }
            friendsMarker.put(uid, marker);
        }

        if(uid==null || uid==""){
            if(landmarksMarker.containsKey(marker.getId())){
                landmarksMarker.remove(marker.getId());
            }
            landmarksMarker.put(marker.getId(), marker);
        }

        if(moveCamera){
            mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
            // Zoom in the Google Map
            //mMap.animateCamera(CameraUpdateFactory.zoomTo(5));
        }
        return marker;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
