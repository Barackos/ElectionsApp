package com.electionscan;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import static com.electionscan.StaticFunctions.toWhiteMessageHTML;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOGIN = 123;

    private FirebaseUser mUser;
    private TextView tvWelcome, tvNotApproved, tvPhone, tvRole, tvKalpi, tvBunch;
    private CardView cardRole, cardNoConnectivity;

    private DatabaseReference dbRoleRef;
    private DatabaseReference connectedRef;

    private ValueEventListener roleListener;
    private ValueEventListener connectedListener;

    private ProgressBar loader;
    private Button btnEnter;
    private Snackbar snackDisconnected;

    private PhoneNumberUtil phoneUtil;

    private Integer viewType;
    private String viewValue;
    private String viewValue_fb;

    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Toolbar
        ActionBar mActionBar = getSupportActionBar();
        if (mActionBar != null)
            mActionBar.setTitle(getString(R.string.title_activity_home));

        this.sharedPref = getSharedPreferences(StaticVars.SPSettings, Context.MODE_PRIVATE);
        boolean alwaysSyncPeople = this.sharedPref.getBoolean("alwaysSyncPeople", false);
        if (alwaysSyncPeople) {
            ((RadioButton) findViewById(R.id.btnYes)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.btnNo)).setChecked(true);
        }

        //User Authentication
        this.mUser = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseAuth.getInstance().addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                mUser = firebaseAuth.getCurrentUser();
                checkAuthState();
            }
        });

        this.phoneUtil = PhoneNumberUtil.getInstance();

        this.cardRole = findViewById(R.id.cardRole);
        this.cardNoConnectivity = findViewById(R.id.cardNoConnectivity);
        this.tvWelcome = findViewById(R.id.tvWelcome);
        this.tvNotApproved = findViewById(R.id.tvNotApproved);
        this.tvPhone = findViewById(R.id.tvPhone);
        this.tvRole = findViewById(R.id.tvRole);
        this.tvKalpi = findViewById(R.id.tvKalpi);
        this.tvBunch = findViewById(R.id.tvBunch);

        this.viewType = -1;
        this.viewValue = "";
        this.viewValue_fb = "";

        this.loader = findViewById(R.id.progressBar);

        this.btnEnter = findViewById(R.id.btnEnter);
        this.btnEnter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StaticFunctions.shakeItBaby(MainActivity.this);
                if (viewType == -1)
                    Snackbar.make(tvWelcome, toWhiteMessageHTML("יש להמתין לסיום טעינה"), Snackbar.LENGTH_SHORT).show();
                else {
                    Intent intent = new Intent(MainActivity.this, VotesActivity.class);
                    intent.putExtra("ViewType", viewType);
                    intent.putExtra("ViewValue", viewValue);
                    intent.putExtra("ViewValue_fb", viewValue_fb);
                    startActivity(intent);
                }
            }
        });
        this.roleListener = null;
        initApprovedPhone();

        //Disconnection alert
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
                } else {
                    snackDisconnected.show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        };

        findViewById(R.id.tvWhat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("מצב מקלט")
                        .setMessage("נועד למקרים בהם הקלפי נמצא באזור ללא קליטת אינטרנט (למשל, מקלט).\n\nיש להפעיל את המצב טרם כניסתך לקלפי, וזאת כדי שהטלפון שלך יטען מראש את בעלי זכות הבחירה הרלוונטים.\n\nכך, תוכל/י לעבוד ביום הבחירות ללא חיבור לאינטרנט.\n\nקח/י בחשבון, שאחת לכמה זמן תידרש/י לצאת מחוץ לקלפי כדי לאפשר לאפליקציה לשלוח עדכונים")
                        .setNeutralButton("אישור", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setCancelable(false)
                        .create().show();
            }
        });

        TextView tvPrivacy = findViewById(R.id.tvPrivacy);
        tvPrivacy.setLinksClickable(true);
        tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
        final String link = getString(R.string.Privacy_Link);
        String text = "<a href='" + link + "'>אמנת פרטיות</a>";
        tvPrivacy.setText(Html.fromHtml(text));
        findViewById(R.id.tvPrivacy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                startActivity(myIntent);
            }
        });
    }

    private void initApprovedPhone() {
        loader.setVisibility(View.VISIBLE);
        if (this.roleListener == null)
            this.roleListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    loader.setVisibility(View.GONE);
                    if (dataSnapshot.exists()) {
                        showRoleUI(true);
                        String welcome = "שלום " + dataSnapshot.child("name").getValue(String.class);
                        tvWelcome.setText(welcome);
                        String number = mUser.getPhoneNumber();
                        try {
                            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(number, "IL");
                            tvPhone.setText(phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                        } catch (NumberParseException e) {
                            Crashlytics.logException(e);
                            Snackbar.make(findViewById(R.id.rootView), "תקלה חריגה, פנה למפתח", Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        viewType = dataSnapshot.child("role").getValue(Integer.class);
                        if (viewType == null) {
                            Crashlytics.log("role was null for approvedPhone user " + mUser.getPhoneNumber());
                            return;
                        }
                        switch (viewType) {
                            case 0: //Observer
                                Integer value = dataSnapshot.child("roleValue").getValue(Integer.class);
                                if (value == null) {
                                    Crashlytics.log("roleValue was null for approvedPhone user " + mUser.getPhoneNumber());
                                    return;
                                }
                                viewValue = value.toString();
                                tvKalpi.setVisibility(View.VISIBLE);
                                tvKalpi.setText(viewValue);
                                findViewById(R.id.lblKalpi).setVisibility(View.VISIBLE);
                                tvBunch.setVisibility(View.GONE);
                                findViewById(R.id.lblBunch).setVisibility(View.GONE);

                                tvRole.setText("משקיף");

                                cardNoConnectivity.setVisibility(View.VISIBLE);

                                FirebaseDatabase.getInstance().getReference().child("people")
                                        .orderByChild("kalpi").equalTo(Integer.parseInt(viewValue))
                                        .keepSynced(true);

                                onRadioButtonClicked(null);
                                break;
                            case 1: //Bunch
                                viewValue = dataSnapshot.child("roleValue").getValue(String.class);
                                tvBunch.setVisibility(View.VISIBLE);
                                tvBunch.setText(viewValue);
                                findViewById(R.id.lblBunch).setVisibility(View.VISIBLE);
                                tvKalpi.setVisibility(View.GONE);
                                findViewById(R.id.lblKalpi).setVisibility(View.GONE);

                                tvRole.setText("מנהל אשכול");

                                viewValue_fb = dataSnapshot.child("roleValue_fb").getValue(String.class);
                                if (viewValue_fb == null) {
                                    Crashlytics.log("roleValue_fb was null for approvedPhone user " + mUser.getPhoneNumber());
                                    return;
                                }
                                FirebaseDatabase.getInstance().getReference()
                                        .child("bunches").child(viewValue_fb).child("kalpis")
                                        .keepSynced(true);

                                cardNoConnectivity.setVisibility(View.GONE);
                                sharedPref.edit().putBoolean("alwaysSyncPeople", false).apply();
                                break;
                            case 2: //Manager
                                tvKalpi.setVisibility(View.GONE);
                                findViewById(R.id.lblKalpi).setVisibility(View.GONE);
                                tvBunch.setVisibility(View.GONE);
                                findViewById(R.id.lblBunch).setVisibility(View.GONE);

                                tvRole.setText("מטה בחירות");

                                cardNoConnectivity.setVisibility(View.GONE);
                                sharedPref.edit().putBoolean("alwaysSyncPeople", false).apply();
                                break;
                            default: //Telephony
                                tvKalpi.setVisibility(View.GONE);
                                findViewById(R.id.lblKalpi).setVisibility(View.GONE);
                                tvBunch.setVisibility(View.GONE);
                                findViewById(R.id.lblBunch).setVisibility(View.GONE);

                                tvRole.setText("טלפוניה ועדכוני התקשרות");

                                cardNoConnectivity.setVisibility(View.GONE);
                                break;
                        }

                    } else {
                        showRoleUI(false);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
        if (this.mUser == null) {
            showRoleUI(false);
        } else {
            if (this.dbRoleRef == null) {
                if (this.mUser.getPhoneNumber() == null) {
                    Crashlytics.log("FirebaseUser.getPhoneNumber returned null, weird");
                    return;
                } else
                    this.dbRoleRef = FirebaseDatabase.getInstance().getReference()
                            .child("approvedPhones").child(this.mUser.getPhoneNumber());
            }
            loader.setVisibility(View.VISIBLE);
            this.dbRoleRef.addValueEventListener(this.roleListener);
        }
    }

    private void showRoleUI(boolean toShow) {
        if (toShow) {
            cardRole.setVisibility(View.VISIBLE);
            tvNotApproved.setVisibility(View.GONE);
            btnEnter.setEnabled(true);
        } else {
            cardRole.setVisibility(View.GONE);
            cardNoConnectivity.setVisibility(View.GONE);
            tvNotApproved.setVisibility(View.VISIBLE);
            btnEnter.setEnabled(false);
            findViewById(R.id.lblBunch).setVisibility(View.GONE);
            findViewById(R.id.lblKalpi).setVisibility(View.GONE);
            tvKalpi.setVisibility(View.GONE);
            tvBunch.setVisibility(View.GONE);
            viewType = -1;
            viewValue = "";
        }
    }

    private void checkAuthState() {
        if (this.mUser == null) //Not logged in
            login();
        initApprovedPhone();
    }

    private void login() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivityForResult(intent, REQ_LOGIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_LOGIN:
                if (resultCode == RESULT_CANCELED)
                    finish();
                break;
            default:
                break;
        }
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean toSync = ((RadioButton) findViewById(R.id.btnYes)).isChecked();

        sharedPref.edit().putBoolean("alwaysSyncPeople", toSync).apply();

        if (toSync && view != null) {
            new AlertDialog.Builder(this)
                    .setTitle("הנחיות")
                    .setMessage("1. יש להמתין 10 שניות עם חיבור לאינטרנט, מרגע קבלת הודעה זו, כדי שכל הנתונים ייטענו.\n\n2. במהלך יום הבחירות, יש להקפיד לצאת עם הטלפון מדי פעם לאוויר הפתוח כדי לאפשר לאפליקציה לעדכן את מטה הבחירות")
                    .setNeutralButton("אישור", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setCancelable(false)
                    .create().show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.dbRoleRef != null)
            this.dbRoleRef.removeEventListener(this.roleListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        snackDisconnected.dismiss();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                connectedRef.addValueEventListener(connectedListener);
            }
        }, 1500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        connectedRef.removeEventListener(connectedListener);
    }
}
