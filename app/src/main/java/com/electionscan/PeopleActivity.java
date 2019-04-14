package com.electionscan;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import static com.electionscan.StaticFunctions.toWhiteMessageHTML;

public class PeopleActivity extends AppCompatActivity implements PeopleAdapter.PersonInterface {

    private DatabaseReference dbPeopleRef;
    private DatabaseReference connectedRef;
    private Query peopleQuery;
    private ValueEventListener peopleListener;
    private ValueEventListener connectedListener;
    private Snackbar snackDisconnected;
    private ArrayList<Person> people;

    private int kalpi;
    private VotesActivity.ViewType role;
    private FirebaseUser mUser;

    private SwipeRefreshLayout refresher;
    private PeopleAdapter adapter;
    private RecyclerView recycler;

    private TextView tvTotal, tvVoted;
    private ConstraintLayout layoutInfo;

    private Filter mFilter;

    enum Filter {
        All, Voted, Unvoted
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people);

        this.mUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mUser == null || mUser.getPhoneNumber() == null) {
            StaticFunctions.showExitDialogue(this, "הודעת מערכת",
                    "התרחשה תקלה. אנא נסו שנית מאוחר יותר");
            return;
        }

        this.kalpi = getIntent().getIntExtra("kalpi", -1);
        this.role = VotesActivity.ViewType.values()[getIntent().getIntExtra("viewType", 0)];
        if (kalpi == -1) {
            StaticFunctions.showExitDialogue(this, "הודעת מערכת",
                    "התרחשה תקלה. אנא נסו שנית מאוחר יותר");
            Crashlytics.logException(new Exception("Didn't find people in kalpi " + kalpi +
                    " for user: " + mUser.getPhoneNumber()));
            return;
        }

        this.mFilter = Filter.All;

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("בוחרים בקלפי " + kalpi);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        this.refresher = findViewById(R.id.refresher);
        this.refresher.setEnabled(false);
        this.refresher.setColorSchemeColors(
                getResources().getColor(R.color.colorAccent),
                getResources().getColor(R.color.colorPrimary));

        //Setting the recycler & adapter
        boolean instantVoteEnabled = role != VotesActivity.ViewType.Kalpi;
        boolean voteOption = role != VotesActivity.ViewType.Telephony;
        boolean contactOption = role == VotesActivity.ViewType.Manager || role == VotesActivity.ViewType.Telephony;
        this.recycler = findViewById(R.id.recycler);
        this.adapter = new PeopleAdapter(R.layout.person, this,
                instantVoteEnabled, voteOption, contactOption, refresher,
                getResources().getColor(R.color.markered), getResources().getColor(R.color.customBackground));
        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recycler.setAdapter(adapter);

        this.tvTotal = findViewById(R.id.tvTotal);
        this.tvVoted = findViewById(R.id.tvVoted);

        this.people = new ArrayList<>();

        //Setting Firebase
        this.connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        this.snackDisconnected = Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("אין חיבור לאינטרנט"), Snackbar.LENGTH_INDEFINITE);
        this.connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                if (connected == null)
                    return;
                if (connected)
                    snackDisconnected.dismiss();
                else
                    snackDisconnected.show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        };

        this.dbPeopleRef = FirebaseDatabase.getInstance().getReference()
                .child("people");
        this.peopleQuery = dbPeopleRef.orderByChild("kalpi").equalTo(kalpi);
        this.peopleListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    people = new ArrayList<>();

                    //Set the people arraylist
                    loadPeople(dataSnapshot);

                    //print the total amount & voted people
                    printVoteInfo();

                    //Show them on the RecyclerView based on the chosen filter
                    showAndFilter();
                } else {
                    StaticFunctions.showExitDialogue(PeopleActivity.this, "הודעת מערכת",
                            "תקלה - לא נמצאו אנשים בקלפי. אנא פנה/י למטה הבחירות.");
                    Crashlytics.logException(new Exception("Didn't find people in kalpi " + kalpi +
                            " for user: " + mUser.getPhoneNumber()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                refresher.setRefreshing(false);
                StaticFunctions.showExitDialogue(PeopleActivity.this, "הודעת מערכת",
                        "אירעה תקלה. אנא נסו שנית מאוחר יותר");
                Crashlytics.logException(new Exception("People listener cancelled for kalpi " + kalpi +
                        " for user: " + mUser.getPhoneNumber()));
            }
        };

        this.layoutInfo = findViewById(R.id.layoutInfo);
        this.layoutInfo.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21)
            layoutInfo.setElevation(4);
    }

    private void loadPeople(@NonNull DataSnapshot snapPeople) {
        for (DataSnapshot personSnap : snapPeople.getChildren()) {
            if (!personSnap.exists())
                return;

            String key = personSnap.getKey();
            String firstName = personSnap.child("firstName").getValue(String.class);
            String lastName = personSnap.child("lastName").getValue(String.class);
            Integer id = personSnap.child("id").getValue(Integer.class);
            Integer id_inside_kalpi = personSnap.child("id_inside_kalpi").getValue(Integer.class);
            Boolean voted, contacted, colored;
            Long timeOfContact;
            if (personSnap.child("voteInfo").exists()) {
                voted = personSnap.child("voteInfo").child("voted").getValue(Boolean.class);
            }
            else {
                voted = false;
            }

            contacted = personSnap.child("contacted").getValue(Boolean.class);
            timeOfContact = personSnap.child("contactedTime").getValue(Long.class);
            colored = personSnap.child("colored").getValue(Boolean.class);

            if (key == null || firstName == null || lastName == null || id == null ||
                    id_inside_kalpi == null || voted == null) {
                StaticFunctions.showExitDialogue(PeopleActivity.this, "הודעת מערכת",
                        "אירעה תקלה. אנא נסו שנית מאוחר יותר");
                Crashlytics.logException(new Exception("One of the people's values was null for kalpi " + kalpi +
                        " for user: " + mUser.getPhoneNumber()));
                return;
            }

            contacted = contacted != null ? contacted : false;
            timeOfContact = timeOfContact != null ? timeOfContact : (long) 0;
            colored = colored != null ? colored : false;
            Person person = new Person(key, firstName, lastName, id, id_inside_kalpi,
                    voted, contacted, timeOfContact, colored);
            people.add(person);
        }

        //Sort by id_inside_kalpi
        Collections.sort(people, new Comparator<Person>() {
            @Override
            public int compare(Person o1, Person o2) {
                int id_1 = o1.getId_inside_kalpi(),
                        id_2 = o2.getId_inside_kalpi();

                if (id_1 > id_2) return 1;
                else if (id_1 < id_2) return -1;
                else return 0;
            }
        });
    }

    private void showAndFilter() {
        if (mFilter == Filter.All) {
            adapter.setPeople(people);
        } else {
            boolean voted = mFilter == Filter.Voted;
            ArrayList<Person> array = new ArrayList<>();
            for (Person person : people)
                if (person.hasVoted() == voted)
                    array.add(person);

            adapter.setPeople(array);
        }
    }

    private void printVoteInfo() {
        if (layoutInfo.getVisibility() != View.VISIBLE)
            layoutInfo.setVisibility(View.VISIBLE);

        int total = people.size();
        int voted = 0;
        for (Person person : people)
            if (person.hasVoted())
                voted++;

        this.tvTotal.setText(String.valueOf(total));
        this.tvVoted.setText(String.valueOf(voted));
    }

    public void onRadioButtonClicked(View view) {
        Filter filter;
        switch (view.getId()) {
            case R.id.rbtnUnvoted:
                filter = Filter.Unvoted;
                break;
            case R.id.rbtnVotedOnly:
                filter = Filter.Voted;
                break;
            default:
                filter = Filter.All;
                break;
        }
        boolean changed = filter != mFilter;
        mFilter = filter;
        if (changed) showAndFilter();
    }

    @Override
    public void onPersonClicked(int personId) {
        Intent result = new Intent();
        result.putExtra("id", personId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onPersonVote(@NonNull String personKey, boolean voted) {
        if (personKey.equals(""))
            return;
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("voted", voted);
        updates.put("timeStamp", ServerValue.TIMESTAMP);
        updates.put("taggedBy", this.mUser.getPhoneNumber());
        //removeListener();

        StaticFunctions.shakeItBaby(PeopleActivity.this);
        dbPeopleRef.child(personKey)
                .child("voteInfo")
                .updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //attachListener();
                        Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("עודכן"), Snackbar.LENGTH_SHORT).show();
                        //changeVoteActionUI(!voted);
                        //updateAwaiting(false);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof DatabaseException)
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("אין הרשאה: לא תחת הקלפי/אשכול שלך"), Snackbar.LENGTH_SHORT).show();
                        else
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML(e.getLocalizedMessage()), Snackbar.LENGTH_SHORT).show();
                        //attachListener();
                        StaticFunctions.shakeItBaby(PeopleActivity.this);
                        //changeVoteActionUI(voted);
                        //updateAwaiting(false);
                    }
                });
    }

    @Override
    public void onPersonContacted(@NonNull String personKey, boolean contacted) {
        if (personKey.equals(""))
            return;

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("contacted", contacted);
        updates.put("contactedTime", ServerValue.TIMESTAMP);

        StaticFunctions.shakeItBaby(PeopleActivity.this);
        dbPeopleRef.child(personKey)
                .updateChildren(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                //attachListener();
                Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("עודכן"), Snackbar.LENGTH_SHORT).show();
                //changeVoteActionUI(!voted);
                //updateAwaiting(false);
            }
        })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof DatabaseException)
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("אין הרשאה, מסיבה כלשהי"), Snackbar.LENGTH_SHORT).show();
                        else
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML(e.getLocalizedMessage()), Snackbar.LENGTH_SHORT).show();
                        //attachListener();
                        StaticFunctions.shakeItBaby(PeopleActivity.this);
                        //changeVoteActionUI(voted);
                        //updateAwaiting(false);
                    }
                });
    }

    @Override
    public void onPersonColored(@NonNull String personKey, boolean toColor) {
        if (personKey.equals(""))
            return;

        dbPeopleRef.child(personKey)
                .child("colored").setValue(toColor)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //attachListener();
                        Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("עודכן"), Snackbar.LENGTH_SHORT).show();
                        //changeVoteActionUI(!voted);
                        //updateAwaiting(false);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof DatabaseException)
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML("אין הרשאה, מסיבה כלשהי"), Snackbar.LENGTH_SHORT).show();
                        else
                            Snackbar.make(findViewById(R.id.layoutRecycler), toWhiteMessageHTML(e.getLocalizedMessage()), Snackbar.LENGTH_SHORT).show();
                        //attachListener();
                        StaticFunctions.shakeItBaby(PeopleActivity.this);
                        //changeVoteActionUI(voted);
                        //updateAwaiting(false);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (kalpi != -1) {
            refresher.setRefreshing(true);
            this.peopleQuery.addValueEventListener(this.peopleListener);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    connectedRef.addValueEventListener(connectedListener);
                }
            }, 1000);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (kalpi != -1) {
            this.peopleQuery.removeEventListener(this.peopleListener);
            this.connectedRef.removeEventListener(connectedListener);
        }
    }
}
