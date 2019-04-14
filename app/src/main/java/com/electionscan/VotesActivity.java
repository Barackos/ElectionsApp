package com.electionscan;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static com.electionscan.StaticFunctions.toWhiteMessageHTML;

public class VotesActivity extends AppCompatActivity {

    public final static int REQ_PEOPLE = 234;

    public enum ViewType {
        Kalpi, Bunch, Manager, Telephony
    }

    private ViewType viewType;
    private String viewValue;
    private String viewValue_fb;

    private String bunchKalpiQuery;

    private DatabaseReference dbPeopleRef;
    private DatabaseReference connectedRef;
    private DatabaseReference dbRoleRef;
    private DatabaseReference dbKalpisOfBunchRef;
    private ValueEventListener personListener, personListener_offline, personByMasadListener;
    private ValueEventListener connectedListener;
    private ValueEventListener roleListener;

    private Button btnVote, btnUnvote, btnShowList, btnContacted, btnContactedNot;
    private AppCompatImageView imgPerson, imgPerson_voted, imgContacted;

    private String personKey;
    private Integer personId;
    private Integer queried;

    private SearchView searchView;
    private InputMethodManager imm;
    private Snackbar snackDisconnected;
    private ProgressBar loader;

    private CardView cardVote, cardOffline, cardWasContacted;
    private TextView tvFirstName;
    private TextView tvLastName;
    private TextView tvId;
    private TextView tvKalpi;
    private TextView tvId_Inside_Kalpi;
    private TextView tvStatus;
    private TextView tvOfflineAmount;
    private TextView tvContacted;

    private FirebaseUser mUser;

    private SharedPreferences sharedPref;

    private Spinner spinKalpi;
    private View spinKalpiLayout;
    private ValueEventListener kalpisBunchListener;

    //private MenuItem menuShowList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_votes);

        if (getIntent().getExtras() == null) {
            finish();
            return;
        } else {
            if (savedInstanceState == null) {
                this.viewType = ViewType.values()[getIntent().getIntExtra("ViewType", 0)];
                this.viewValue = getIntent().getStringExtra("ViewValue");
                this.viewValue_fb = getIntent().getStringExtra("ViewValue_fb");
            } else {
                this.viewType = (ViewType) savedInstanceState.getSerializable("viewType");
                this.viewValue = savedInstanceState.getString("viewValue");
                this.viewValue_fb = savedInstanceState.getString("viewValue_fb");
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle("");

        this.bunchKalpiQuery = null;
        this.btnShowList = findViewById(R.id.btnShowList);
        this.btnShowList.setVisibility(viewType == ViewType.Kalpi ? View.VISIBLE : View.GONE);
        this.btnShowList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String kalpi = currentKalpiView();
                if (kalpi == null)
                    return;

                Intent intent = new Intent(VotesActivity.this, PeopleActivity.class);
                intent.putExtra("kalpi", Integer.parseInt(kalpi));
                intent.putExtra("viewType", viewType.ordinal());
                startActivityForResult(intent, REQ_PEOPLE);
            }
        });

        this.imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        this.loader = findViewById(R.id.loader);
        this.loader.setVisibility(View.GONE);

        this.sharedPref = getSharedPreferences(StaticVars.SPSettings, Context.MODE_PRIVATE);

        this.imgPerson = findViewById(R.id.imgPerson);
        this.imgPerson_voted = findViewById(R.id.imgPerson_voted);
        this.personKey = "";
        this.cardVote = findViewById(R.id.cardDetails);
        this.cardVote.setVisibility(View.GONE);
        this.cardOffline = findViewById(R.id.cardOffline);
        this.cardOffline.setVisibility(View.GONE);
        if (this.sharedPref.getBoolean("alwaysSyncPeople", false))
            this.cardOffline.setVisibility(View.VISIBLE);
        this.cardWasContacted = findViewById(R.id.cardWasContacted);
        this.cardWasContacted.setVisibility(View.GONE);
        this.imgContacted = findViewById(R.id.imgContacted);
        this.btnContacted = findViewById(R.id.btnContacted);
        this.btnContactedNot = findViewById(R.id.btnContactedNot);
        this.tvContacted = findViewById(R.id.tvContacted);
        Button.OnClickListener clickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateContactedStatus(v.getId() == R.id.btnContacted);
            }
        };
        this.btnContacted.setOnClickListener(clickListener);
        this.btnContactedNot.setOnClickListener(clickListener);

        this.tvFirstName = findViewById(R.id.tvPhone);
        this.tvLastName = findViewById(R.id.tvLastName);
        this.tvId = findViewById(R.id.tvRole);
        this.tvKalpi = findViewById(R.id.tvKalpi);
        this.tvId_Inside_Kalpi = findViewById(R.id.tvId_Inside_Kalpi);
        this.tvStatus = findViewById(R.id.tvStatus);
        this.tvOfflineAmount = findViewById(R.id.tvOfflineAmount);
        int offlineAmount = getAwaiting();
        this.tvOfflineAmount.setText(offlineAmount + " עדכונים בהמתנה");
        this.spinKalpi = findViewById(R.id.spinKalpi);
        this.spinKalpiLayout = findViewById(R.id.kalpiPickerLayout);

        ArrayAdapter<String> temp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        temp.add("הכל");
        this.spinKalpi.setAdapter(temp);
        temp.notifyDataSetChanged();

        this.mUser = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseAuth.getInstance().addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                mUser = firebaseAuth.getCurrentUser();
                checkAuthState();
            }
        });

        if (this.mUser == null || this.mUser.getPhoneNumber() == null) {
            finish();
            return;
        }

        //Listen to changes for the current user
        initRoleListener();

        DatabaseReference dbRootRef = FirebaseDatabase.getInstance().getReference();
        this.dbPeopleRef = dbRootRef.child("people");
        this.personListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    DataSnapshot snap = dataSnapshot.getChildren().iterator().next();
                    queriedPersonUI(snap);

                } else {
                    showPersonCard(false);
                    Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("לא נמצא"), Snackbar.LENGTH_SHORT).show();
                    loader.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listenerOnCanceled(databaseError);
            }
        };

        this.personListener_offline = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    for (DataSnapshot person : dataSnapshot.getChildren()) {
                        if (person.exists() && person.hasChild("id")) {
                            Integer id = person.child("id").getValue(Integer.class);
                            if (id == null)
                                return;
                            if (id.equals(queried)) {
                                queriedPersonUI(person);
                                return;
                            }
                        }
                    }
                }
                //If reached here, for any reason - the person wasn't found.
                showPersonCard(false);
                Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("לא נמצא"), Snackbar.LENGTH_SHORT).show();
                loader.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listenerOnCanceled(databaseError);
            }
        };

        this.personByMasadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    for (DataSnapshot person : dataSnapshot.getChildren()) {
                        if (person.exists() && person.hasChild("id_inside_kalpi")) {
                            Integer id_inside_kalpi = person.child("id_inside_kalpi").getValue(Integer.class);
                            if (id_inside_kalpi == null)
                                return;
                            if (id_inside_kalpi.equals(queried)) {
                                queriedPersonUI(person);
                                return;
                            }
                        }
                    }
                }
                //If reached here, for any reason - the person wasn't found.
                showPersonCard(false);
                Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("לא נמצא"), Snackbar.LENGTH_SHORT).show();
                loader.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listenerOnCanceled(databaseError);
            }
        };

        //Search Operation
        this.searchView = findViewById(R.id.search);
        this.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                queried = Integer.parseInt(s);
                attachListener();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        StaticFunctions.hideKeyboard(imm, findViewById(R.id.rootView));
                    }
                }, 100);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                hideResultIfShown();
                return false;
            }
        });
        this.searchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setQuery(searchView.getQuery(), true);
            }
        });
        if (viewType == ViewType.Kalpi)
            this.searchView.setQueryHint("הקש ת.ז או מס\"ד");

        EditText et = searchView.findViewById(searchView.getContext().getResources()
                .getIdentifier("android:id/search_src_text", null, null));
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});

        initFabs();

        this.snackDisconnected = Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("אין חיבור לאינטרנט"), Snackbar.LENGTH_INDEFINITE);
        this.connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        this.connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected == null)
                    return;
                if (connected) {
                    snackDisconnected.dismiss();
                    dummyConnectionValidator();
                } else {
                    snackDisconnected.show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        };

        showPersonCard(false);
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.votes_menu, menu);
        this.menuShowList = menu.getItem(0);
        this.menuShowList.setVisible(viewType == ViewType.Kalpi);
        this.menuShowList.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String kalpi = currentKalpiView();
                if (kalpi == null)
                    return false;

                Intent intent = new Intent(VotesActivity.this, PeopleActivity.class);
                intent.putExtra("kalpi", Integer.parseInt(kalpi));
                startActivityForResult(intent, REQ_PEOPLE);
                return true;
            }
        });
        return true;
    }*/

    private void hideResultIfShown() {
        if (cardVote.getVisibility() == View.VISIBLE ||
                loader.getVisibility() == View.VISIBLE) {
            showPersonCard(false);
            removeListener();
            loader.setVisibility(View.GONE);
        }
    }

    private void initSpinKalpi(boolean toShow) {
        if (this.kalpisBunchListener == null) {
            this.kalpisBunchListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    ArrayAdapter<String> values = new ArrayAdapter<>(VotesActivity.this,
                            android.R.layout.simple_spinner_dropdown_item);
                    values.add("הכל");
                    if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                        ArrayList<Integer> kalpis = new ArrayList<>();
                        for (DataSnapshot kalpi : dataSnapshot.getChildren()) {
                            String key = kalpi.getKey();
                            if (key == null) {
                                Crashlytics.log("Key of kalpi was received as null for user " + mUser.getPhoneNumber());
                                StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התרחשה תקלה, אנא נסה/י שנית מאוחר יותר");
                                return;
                            }
                            kalpis.add(Integer.parseInt(key.substring(1)));
                        }
                        Collections.sort(kalpis);
                        for (Integer kalpi : kalpis)
                            values.add(kalpi.toString());
                    }

                    spinKalpi.setAdapter(values);
                    //values.notifyDataSetChanged();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Crashlytics.logException(databaseError.toException());
                    StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התרחשה תקלה, אנא נסה/י שנית מאוחר יותר");
                }
            };
            if (this.dbKalpisOfBunchRef != null)
                this.dbKalpisOfBunchRef.removeEventListener(this.kalpisBunchListener);

            if (!toShow) {
                this.spinKalpiLayout.setVisibility(View.GONE);
            } else {
                this.spinKalpiLayout.setVisibility(View.VISIBLE);
                if (viewType == ViewType.Bunch)
                    this.dbKalpisOfBunchRef = FirebaseDatabase.getInstance().getReference()
                            .child("bunches").child(viewValue_fb).child("kalpis");
                else
                    this.dbKalpisOfBunchRef = FirebaseDatabase.getInstance().getReference()
                            .child("bunches").child("masterBunch").child("kalpis");

                this.dbKalpisOfBunchRef.addValueEventListener(this.kalpisBunchListener);
            }

            setSelectedKalpiStyle();
        }

        this.spinKalpi.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) { //Show everything
                    bunchKalpiQuery = null;
                    searchView.setQueryHint("חיפוש תעודת זהות");
                    btnShowList.setVisibility(View.GONE);
                } else {
                    bunchKalpiQuery = spinKalpi.getSelectedItem().toString();
                    searchView.setQueryHint("הקש ת.ז או מס\"ד");
                    btnShowList.setVisibility(View.VISIBLE);
                }
                hideResultIfShown();
                searchView.setQuery("", false);

                //Change the text color to white
                setSelectedKalpiStyle();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setSelectedKalpiStyle() {
        TextView selectedText = (TextView) spinKalpi.getChildAt(0);
        if (selectedText != null) {
            selectedText.setTextColor(Color.WHITE);
            selectedText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            selectedText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
    }

    /**
     * This method receives a queried person's dataSnapshot,
     * and is supposed to update the UI accordingly
     *
     * @param snap This is a dataSnapshot that represents a person
     */
    private void queriedPersonUI(@NonNull DataSnapshot snap) {
        loader.setVisibility(View.GONE);
        String name = snap.child("firstName").getValue(String.class);
        String lastName = snap.child("lastName").getValue(String.class);
        personId = snap.child("id").getValue(Integer.class);
        Integer kalpi = snap.child("kalpi").getValue(Integer.class);
        Integer id_inside_kalpi = snap.child("id_inside_kalpi").getValue(Integer.class);
        Boolean contacted = snap.child("contacted").getValue(Boolean.class);
        tvFirstName.setText(name);
        tvLastName.setText(lastName);
        tvId.setText(String.valueOf(personId));
        tvKalpi.setText(String.valueOf(kalpi));
        tvId_Inside_Kalpi.setText(String.valueOf(id_inside_kalpi));

        if (snap.child("voteInfo").exists()) {
            Boolean voted = snap.child("voteInfo").child("voted").getValue(Boolean.class);
            if (voted != null) {
                changeVoteActionUI(!voted);
                tvStatus.setText(voted ? "הצביע/ה" : "לא הצביע/ה");
                if (voted && !personKey.equals(snap.getKey()) && viewType != ViewType.Telephony)
                    Snackbar.make(findViewById(R.id.rootView),
                            toWhiteMessageHTML("כבר דווח שהצביע/ה"), Snackbar.LENGTH_SHORT).show();
            }
        } else {
            tvStatus.setText("לא הצביע/ה");
            changeVoteActionUI(true);
        }
        //Change contacted layout based on value
        changeContactedUI(contacted != null && contacted);

        personKey = snap.getKey();

        showPersonCard(true);
    }

    private void listenerOnCanceled(@NonNull DatabaseError databaseError) {
        loader.setVisibility(View.GONE);
        String message;
        if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED)
            message = "אין הרשאה";
        else {
            message = "אירעה תקלה";
            Crashlytics.logException(databaseError.toException());
        }
        showPersonCard(false);
        Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML(message), Snackbar.LENGTH_SHORT).show();
    }

    /**
     * Determines which kalpi is currently being viewed, if at all.
     *
     * @return Kalpi num in String. If not kalpi viewing - returns null
     */
    private String currentKalpiView() {
        return bunchKalpiQuery != null ? bunchKalpiQuery :
                viewType == ViewType.Kalpi ? viewValue : null;
    }

    private void attachListener() {
        removeListener();
        loader.setVisibility(View.VISIBLE);
        String kalpiView = currentKalpiView();
        if (String.valueOf(queried).length() >= 5/* || viewType != ViewType.Kalpi*/)
            if (cardOffline.getVisibility() == View.VISIBLE) //Ofline mode indicator
                dbPeopleRef.orderByChild("kalpi").equalTo(Integer.parseInt(viewValue))
                        .addValueEventListener(personListener_offline);
            else
                dbPeopleRef.orderByChild("id").equalTo(queried)
                        .limitToFirst(1).addValueEventListener(personListener);
        else {
            if (kalpiView == null) {
                int id = searchView.getContext()
                        .getResources()
                        .getIdentifier("android:id/search_src_text", null, null);
                EditText editText = searchView.findViewById(id);
                editText.setError("מספר ת.ז לא תקין");
                loader.setVisibility(View.GONE);
            } else
                dbPeopleRef.orderByChild("kalpi").equalTo(Integer.parseInt(kalpiView))
                        .addValueEventListener(personByMasadListener);
        }
    }

    private void removeListener() {
        dbPeopleRef.removeEventListener(personListener);
        dbPeopleRef.removeEventListener(personListener_offline);
        dbPeopleRef.removeEventListener(personByMasadListener);
    }

    private void checkAuthState() {
        if (this.mUser == null) { //Not logged in
            StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "נותקת מהמערכת. אנא נסה/י להתחבר שנית");
        }
    }

    private void initRoleListener() {
        if (this.mUser.getPhoneNumber() == null) {
            finish();
            return;
        }
        this.dbRoleRef = FirebaseDatabase.getInstance().getReference()
                .child("approvedPhones").child(this.mUser.getPhoneNumber());
        this.roleListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Integer role = dataSnapshot.child("role").getValue(Integer.class);
                    Object roleValue = dataSnapshot.child("roleValue").getValue();
                    if (role == null || (role != 2 && role != 3 && roleValue == null)) {
                        StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התרחשה תקלה, אנא נסה/י שנית מאוחר יותר");
                        return;
                    }
                    if (role != viewType.ordinal()) {
                        StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התפקיד שלך עודכן. יש לרענן את העמוד");
                        return;
                    }
                    if (roleValue != null)
                        viewValue = roleValue.toString();
                    switch (viewType) {
                        case Kalpi:
                            initSpinKalpi(false);
                            break;
                        case Bunch:
                            String value_Firebase = dataSnapshot.child("roleValue_fb").getValue(String.class);
                            //viewValue_fb = dataSnapshot.child("roleValue_fb").getValue(String.class);
                            if (value_Firebase == null) {
                                Crashlytics.log("roleValue_fb was null for approvedPhone user " + mUser.getPhoneNumber());
                                StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התרחשה תקלה, אנא נסה/י שנית מאוחר יותר");
                                return;
                            }
                            if (!viewValue_fb.equals(value_Firebase)) {
                                StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "התפקיד שלך עודכן. יש לרענן את העמוד");
                                return;
                            }
                            initSpinKalpi(true);
                            break;
                        case Manager:
                            initSpinKalpi(true);
                            break;
                        case Telephony:
                            initSpinKalpi(true);
                            break;
                    }
                    String currentKalpi = currentKalpiView();
                    if (currentKalpi != null)
                        searchView.setQueryHint("הקש ת.ז או מס\"ד");
                    else
                        searchView.setQueryHint("חיפוש תעודת זהות");

                } else {
                    removeListener();
                    StaticFunctions.showExitDialogue(VotesActivity.this, "הודעת מערכת", "המשתמש שלך הוסר. אנא פנה למטה הבחירות הרלוונטי לבירור");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listenerOnCanceled(databaseError);
            }
        };
    }

    private void initFabs() {
        this.btnVote = findViewById(R.id.btnVote);
        this.btnUnvote = findViewById(R.id.btnUnvote);
        this.btnVote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loader.getVisibility() == View.GONE)
                    updateVoteStatus(true);
            }
        });
        this.btnUnvote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loader.getVisibility() == View.GONE)
                    updateVoteStatus(false);
            }
        });
    }

    private void showPersonCard(boolean toShow) {
        cardVote.setVisibility(toShow ? View.VISIBLE : View.GONE);
        cardWasContacted.setVisibility(toShow && (viewType == ViewType.Manager || viewType == ViewType.Telephony) ? View.VISIBLE : View.GONE);

        //findViewById(R.id.layoutActions).setVisibility(toShow ? View.VISIBLE : View.GONE);
        if (!toShow) {
            btnUnvote.setVisibility(View.GONE);
            btnVote.setVisibility(View.GONE);
            personKey = "";
        }
    }

    private void updateVoteStatus(final boolean voted) {
        if (personKey == null || personKey.equals(""))
            return;
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("voted", voted);
        updates.put("timeStamp", ServerValue.TIMESTAMP);
        updates.put("taggedBy", this.mUser.getPhoneNumber());
        loader.setVisibility(View.VISIBLE);
        //removeListener();

        StaticFunctions.shakeItBaby(VotesActivity.this);
        updateAwaiting(true);
        dbPeopleRef.child(personKey)
                .child("voteInfo")
                .updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //attachListener();
                        Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("עודכן"), Snackbar.LENGTH_SHORT).show();
                        //changeVoteActionUI(!voted);
                        updateAwaiting(false);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof DatabaseException)
                            Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("אין הרשאה"), Snackbar.LENGTH_SHORT).show();
                        else
                            Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML(e.getLocalizedMessage()), Snackbar.LENGTH_SHORT).show();
                        //attachListener();
                        StaticFunctions.shakeItBaby(VotesActivity.this);
                        //changeVoteActionUI(voted);
                        updateAwaiting(false);
                    }
                });
    }

    private void updateContactedStatus(final boolean contacted) {
        if (personKey == null || personKey.equals(""))
            return;

        loader.setVisibility(View.VISIBLE);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("contacted", contacted);
        updates.put("contactedTime", ServerValue.TIMESTAMP);

        StaticFunctions.shakeItBaby(VotesActivity.this);
        updateAwaiting(true);
        dbPeopleRef.child(personKey)
                .updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //attachListener();
                        Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("עודכן"), Snackbar.LENGTH_SHORT).show();
                        //changeVoteActionUI(!voted);
                        updateAwaiting(false);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof DatabaseException)
                            Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML("אין הרשאה"), Snackbar.LENGTH_SHORT).show();
                        else
                            Snackbar.make(findViewById(R.id.rootView), toWhiteMessageHTML(e.getLocalizedMessage()), Snackbar.LENGTH_SHORT).show();
                        //attachListener();
                        StaticFunctions.shakeItBaby(VotesActivity.this);
                        //changeVoteActionUI(voted);
                        updateAwaiting(false);
                    }
                });
    }

    private void updateAwaiting(boolean add) {
        int current = sharedPref.getInt("awaiting", 0);
        int newVal = add ? current + 1 : current - 1;
        setAwaiting(newVal);
    }

    private void setAwaiting(int awaiting) {
        sharedPref.edit().putInt("awaiting", awaiting).commit();
        this.tvOfflineAmount.setText(awaiting + " עדכונים בהמתנה");
    }

    private int getAwaiting() {
        return sharedPref.getInt("awaiting", 0);
    }

    private void dummyConnectionValidator() {
        final int currentAwaiting = getAwaiting();
        dbPeopleRef.child("dummy").setValue(true).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                int toSet = getAwaiting() - currentAwaiting;
                setAwaiting(toSet < 0 ? 0 : toSet);
            }
        });
    }

    private void changeContactedUI(boolean contacted) {
        if (!contacted) {
            this.imgContacted.setImageResource(R.drawable.ic_call_end_24dp);
            this.tvContacted.setText("לא נוצר קשר");
            this.btnContacted.setVisibility(View.VISIBLE);
            this.btnContactedNot.setVisibility(View.GONE);
        } else {
            this.imgContacted.setImageResource(R.drawable.ic_phone_on_24dp);
            this.tvContacted.setText("נוצר קשר");
            this.btnContacted.setVisibility(View.GONE);
            this.btnContactedNot.setVisibility(View.VISIBLE);
        }
    }

    private void changeVoteActionUI(boolean toVote) {
        if (viewType == ViewType.Telephony) {
            btnVote.setVisibility(View.GONE);
            btnUnvote.setVisibility(View.GONE);
            imgPerson.setVisibility(View.VISIBLE);
            imgPerson_voted.setVisibility(View.GONE);
        } else {
            if (!toVote) {
                btnVote.setVisibility(View.GONE);
                /*btnVote.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                btnVote.setEnabled(false);
                btnVote.setText("הצביע!");*/
                btnUnvote.setVisibility(View.VISIBLE);
                /*fabVote.setEnabled(false);
                fabUnvote.setEnabled(true);
                fabVote.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.darker_gray)));
                fabUnvote.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.fabRed)));*/
                //imgPerson.setImageResource(R.drawable.check_on_light);
                imgPerson.setVisibility(View.GONE);
                imgPerson_voted.setVisibility(View.VISIBLE);

            } else {
                btnVote.setVisibility(View.VISIBLE);
                /*btnVote.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                btnVote.setEnabled(true);
                btnVote.setText("עדכן הצבעה");*/
                btnUnvote.setVisibility(View.GONE);
                /*fabVote.setEnabled(true);
                fabUnvote.setEnabled(false);
                fabVote.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.fabGreen)));
                fabUnvote.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.darker_gray)));*/
                //imgPerson.setImageResource(R.drawable.how_to_vote_24px);
                imgPerson.setVisibility(View.VISIBLE);
                imgPerson_voted.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.dbRoleRef.addValueEventListener(this.roleListener);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                connectedRef.addValueEventListener(connectedListener);
            }
        }, 1000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        dbRoleRef.removeEventListener(this.roleListener);
        connectedRef.removeEventListener(connectedListener);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("viewType", this.viewType);
        outState.putString("viewValue", this.viewValue);
        outState.putString("viewValue_fb", this.viewValue_fb);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PEOPLE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    int id = data.getIntExtra("id", -1);
                    if (id != -1) {
                        searchView.setQuery(String.valueOf(id), true);
                    }
                }
            }
        }
    }
}
