package com.electionscan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import static android.content.Context.VIBRATOR_SERVICE;

class StaticFunctions {
    static void showKeyboard(InputMethodManager imm) {
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }
    static void hideKeyboard(InputMethodManager imm, View view) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    // Vibrate
    static void shakeItBaby(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null)
            return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate
                    (VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(25);
        }
    }

    static Spanned toWhiteMessageHTML(@NonNull String message) {
        return Html.fromHtml("<font color=\"#ffffff\">" + message + "</font>");
    }

    static void showExitDialogue(@NonNull final Activity activity, @NonNull String title, @NonNull String message) {
        new AlertDialog.Builder(activity)
                .setTitle(title).setMessage(message)
                .setNeutralButton("אישור", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.finish();
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .create().show();
    }
}
