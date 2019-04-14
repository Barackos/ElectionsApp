package com.electionscan;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;

import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.mukesh.countrypicker.Country;
import com.mukesh.countrypicker.CountryPicker;
import com.mukesh.countrypicker.CountryPickerListener;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // UI references.
    private EditText etPhoneNumber;

    private Country country;
    private TextView tvCountry;
    private TextView etCountryPrefix;
    private AppCompatImageView imgFlag;

    private TextView tvCodeSent;
    private EditText etCodeToVerify;

    private boolean mVerificationInProgress;
    private boolean mRegistrationInProgress;
    private String mVerificationId;
    private String mRawPhoneNumber;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthCredential mCredentials;
    private PhoneNumberUtil phoneUtil;

    private ProgressDialog dialog;

    //Keyboard
    private InputMethodManager imm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        phoneUtil = PhoneNumberUtil.getInstance();

        //final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        etPhoneNumber = findViewById(R.id.etPhone);
        tvCountry = findViewById(R.id.spinnerCountry);
        ConstraintLayout btnCountry = findViewById(R.id.layoutCountryButton);
        Button btnNext = findViewById(R.id.btnNext);
        Button btnVerify = findViewById(R.id.btnVerify);
        tvCodeSent = findViewById(R.id.tvCodeSent);
        etCodeToVerify = findViewById(R.id.etVerificationCode);
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        this.dialog = new ProgressDialog(this);
        this.dialog.setIndeterminate(false);
        this.dialog.setMessage(getString(R.string.TaskStatus_PleaseWait));
        this.dialog.setCancelable(false);

        //savedInstance
        if (savedInstanceState != null) {
            mVerificationInProgress = savedInstanceState.getBoolean("verificationProgress", false);
            mRegistrationInProgress = savedInstanceState.getBoolean("registrationProgress", false);
            mRawPhoneNumber = savedInstanceState.getString("rawPhoneNumber", "");
            mVerificationId = savedInstanceState.getString("verificationId", null);
            String cName = savedInstanceState.getString("country", "");
            if (!cName.equals(""))
                country = Country.getCountryByName(cName);
            mResendToken = savedInstanceState.getParcelable("resendToken");
            mCredentials = savedInstanceState.getParcelable("credentials");
        } else {
            mVerificationInProgress = false;
            mRegistrationInProgress = false;
            mRawPhoneNumber = "";

            try {
                this.country = Country.getCountryFromSIM(getApplicationContext()); //Get user country based on SIM card
            } catch (Exception ex) {
                this.country = Country.getCountryByLocale(Locale.getDefault()); //Get country based on Locale
            }
            if (country == null)
                this.country = Country.getCountryByName("United States");
        }

        etPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                /*if (s.length() > 0)
                    fab.show();
                else
                    fab.hide();*/
            }
        });
        etPhoneNumber.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    confirmPhone(etPhoneNumber.getText().toString());
                    return true;
                } else return false;
            }
        });
        etCodeToVerify.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    verifyCode();
                    return true;
                }
                return false;
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmPhone(etPhoneNumber.getText().toString());
            }
        });
        btnVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verifyCode();
            }
        });
        btnCountry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCountryDialog();
            }
        });
        this.etCountryPrefix = findViewById(R.id.etCountryPrefix);
        this.imgFlag = findViewById(R.id.imgCountryFlag);
        updateUiFlag(country.getDialCode(), country.getFlag());

        LinearLayout layout = findViewById(R.id.layoutCountry);
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCountryDialog();
            }
        });

        //In case of rotation - continue verification / registration, whatever was in progress
        if (mVerificationInProgress) {
            confirmPhone(mRawPhoneNumber);
            dialog.show();
        } else if (mRegistrationInProgress) {
            signInWithPhoneAuthCredential(mCredentials);
            dialog.setMessage(getString(R.string.Verified_Phone_Registering));
            dialog.show();
        }

        findViewById(R.id.tvWrongNumber).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchPanels(false);
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

    private void showCountryDialog() {
        final CountryPicker picker = CountryPicker.newInstance(getString(R.string.Select_Country));  // dialog title
        picker.setListener(new CountryPickerListener() {
            @Override
            public void onSelectCountry(String name, String code, String dialCode, int flagDrawableResID) {
                // Implement your code here
                country = Country.getCountryByName(name);
                updateUiFlag(dialCode, flagDrawableResID);
                picker.dismiss();
            }
        });
        picker.show(getSupportFragmentManager(), "COUNTRY_PICKER");
    }

    private void updateUiFlag(String dialCode, int flagDrawableResID) {
        this.etCountryPrefix.setText(dialCode);
        this.imgFlag.setImageResource(flagDrawableResID);
        this.tvCountry.setText(country.getName());
    }

    private void confirmPhone(final String rawNumber) {
        //Create a confirmation dialog for the entered phone number.
        //Prevents a typo mistake & unnecessary phone number abuse
        final Phonenumber.PhoneNumber phoneNumber = phoneNumValidate(rawNumber);
        if (phoneNumber == null) return;

        final String numberE164 = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        final String numberPrint = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);

        StaticFunctions.hideKeyboard(imm, tvCountry);

        Spanned alertMessage;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            alertMessage = Html.fromHtml(getString(R.string.Register_Dialog_Confirm_Text, numberPrint), Html.FROM_HTML_MODE_LEGACY);
        else
            alertMessage = Html.fromHtml(getString(R.string.Register_Dialog_Confirm_Text, numberPrint));

        AlertDialog dialogConfirm = new AlertDialog.Builder(this)
                .setTitle(R.string.Register_Dialog_Confirm_Title)
                .setMessage(alertMessage)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        authenticate(rawNumber, numberE164, numberPrint);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .create();
        dialogConfirm.show();
    }

    private void authenticate(@NonNull String rawNumber, @NonNull final String phoneNumber,
                              @NonNull final String numberPrint) {
        if (!phoneNumber.equals("")) {

            this.mRawPhoneNumber = rawNumber;
            this.mVerificationInProgress = true;

            //Show dialog
            this.dialog.show();

            PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(PhoneAuthCredential credential) {
                    // This callback will be invoked in two situations:
                    // 1 - Instant verification. In some cases the phone number can be instantly
                    //     verified without needing to send or enter a verification code.
                    // 2 - Auto-retrieval. On some devices Google Play services can automatically
                    //     detect the incoming verification SMS and perform verification without
                    //     user action.
                    Log.d(TAG, "onVerificationCompleted:" + credential);

                    StaticFunctions.hideKeyboard(imm, tvCodeSent);

                    dialog.setMessage(getString(R.string.Verified_Phone_Registering));
                    if (!dialog.isShowing())
                        dialog.show();

                    signInWithPhoneAuthCredential(credential);
                }

                @Override
                public void onVerificationFailed(FirebaseException e) {
                    // This callback is invoked in an invalid request for verification is made,
                    // for instance if the the phone number format is not valid.
                    Log.w(TAG, "onVerificationFailed", e);

                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        Crashlytics.logException(e);
                        // Invalid request
                        // ...
                    } else if (e instanceof FirebaseTooManyRequestsException) {
                        Crashlytics.logException(e);
                        // The SMS quota for the project has been exceeded
                        // ...
                    }

                    // Show a message and update the UI
                    // ...

                    etPhoneNumber.setError(e.getLocalizedMessage());
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle(R.string.Error)
                            .setMessage(e.getLocalizedMessage())
                            .setIcon(android.R.drawable.stat_notify_error)
                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            })
                            .create().show();
                    dialog.dismiss();
                }

                @Override
                public void onCodeSent(String verificationId,
                                       PhoneAuthProvider.ForceResendingToken token) {
                    // The SMS verification code has been sent to the provided phone number, we
                    // now need to ask the user to enter the code and then construct a credential
                    // by combining the code with a verification ID.
                    Log.d(TAG, "onCodeSent:" + verificationId);

                    // Save verification ID and resending token so we can use them later
                    mVerificationId = verificationId;
                    mResendToken = token;

                    //Set the Title of the "Enter Code" screen
                    Spanned codeTitleMessage;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                        codeTitleMessage = Html.fromHtml(getString(R.string.Register_SMS_TypeCode, numberPrint), Html.FROM_HTML_MODE_LEGACY);
                    else
                        codeTitleMessage = Html.fromHtml(getString(R.string.Register_SMS_TypeCode, numberPrint));
                    tvCodeSent.setText(codeTitleMessage);

                    //Switch to the "Enter Code" screen
                    switchPanels(true);

                    dialog.dismiss();

                    StaticFunctions.showKeyboard(imm);

                    // ...
                }
            };

            //Firebase Phone Authentication Magic
            verifyPhoneNumber(phoneNumber, mCallbacks);
        }
    }

    /**
     * Switching between "Type your Number" and "Enter Code" screen.
     *
     * @param codeStage true if you want "Enter Code". false for "Type your Number"
     */
    private void switchPanels(boolean codeStage) {
        findViewById(R.id.layoutVerificationSMS).setVisibility(codeStage ? View.VISIBLE : View.GONE);
        findViewById(R.id.layoutPhoneRegistration).setVisibility(codeStage ? View.GONE : View.VISIBLE);
    }

    private void verifyPhoneNumber(String phoneNumber, PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mCallbacks);        // OnVerificationStateChangedCallbacks
    }

    private Phonenumber.PhoneNumber phoneNumValidate(String phoneNumber) {

        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(phoneNumber, country.getCode());
            if (phoneUtil.isValidNumber(number))
                return number;
            else
                etPhoneNumber.setError(getString(R.string.Invalid_Phone));

        } catch (NumberParseException e) {
            //etPhoneNumber.setError(e.getLocalizedMessage());
            etPhoneNumber.setError(getString(R.string.Invalid_Phone));
            e.printStackTrace();
        }
        return null;
    }

    private void verifyCode() {
        String code = etCodeToVerify.getText().toString();
        if (code.length() == 0) {
            etCodeToVerify.setError(getString(R.string.Register_SMS_TypeCode_Invalid));
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        signInWithPhoneAuthCredential(credential);
        if (!dialog.isShowing())
            dialog.show();
        dialog.setMessage(getString(R.string.TaskStatus_PleaseWait));
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mCredentials = credential;
        mVerificationInProgress = false;
        mRegistrationInProgress = true;
        StaticFunctions.hideKeyboard(imm, tvCountry);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        dialog.dismiss();

                        mRegistrationInProgress = false;
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");

                            FirebaseUser user = task.getResult().getUser();
                            // ...

                            //Saving UserID + UserPhoneNum inside Shared Preferences
                            SharedPreferences sharedPref = getSharedPreferences(StaticVars.SPSettings, Context.MODE_PRIVATE);
                            sharedPref.edit()
                                    .putString(StaticVars.UserID, user.getUid())
                                    .putString(StaticVars.UserPhone, user.getPhoneNumber())
                                    .apply();

                            Snackbar.make(etCountryPrefix, R.string.Success, Snackbar.LENGTH_SHORT).show();

                            setResult(RESULT_OK);
                            finish();

                        } else {
                            // Sign in failed, display a message and update the UI
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                // The verification code entered was invalid
                                Snackbar.make(etCountryPrefix, R.string.VerificationCode_Incorrect, Snackbar.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("verificationProgress", mVerificationInProgress);
        outState.putBoolean("registrationProgress", mRegistrationInProgress);
        outState.putString("country", country.getName());
        outState.putString("verificationId", mVerificationId);
        outState.putString("rawPhoneNumber", mRawPhoneNumber);
        if (mResendToken != null)
            outState.putParcelable("resendToken", mResendToken);
        if (mCredentials != null)
            outState.putParcelable("credentials", mCredentials);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}

