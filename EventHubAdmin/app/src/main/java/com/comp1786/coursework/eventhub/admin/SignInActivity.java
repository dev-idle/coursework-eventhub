package com.comp1786.coursework.eventhub.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Signs the admin in with Firebase, as the database rules let only the admin change the events.
 */
public class SignInActivity extends AppCompatActivity {

    private View sheet;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;

    /** Returns whether the admin is signed in, which Firebase remembers between launches. */
    public static boolean isSignedIn() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Earlier versions signed in anonymously, which is not the admin.
        return user != null && !user.isAnonymous();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            // Above the keyboard when it shows, otherwise the sheet runs under the navigation bar.
            v.setPadding(bars.left, bars.top, bars.right, keyboard);
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(),
                    sheet.getPaddingRight(), getResources().getDimensionPixelSize(
                            R.dimen.screen_padding) + (keyboard > 0 ? 0 : bars.bottom));
            return insets;
        });

        bindViews();
        emailInput.addTextChangedListener(new InputWatcher(emailLayout));
        passwordInput.addTextChangedListener(new InputWatcher(passwordLayout));
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                signIn();
                return true;
            }
            return false;
        });
        signInButton.setOnClickListener(v -> signIn());
    }

    private void bindViews() {
        sheet = findViewById(R.id.sheet);
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        signInButton = findViewById(R.id.sign_in_button);
    }

    /** Checks both fields, then signs in with Firebase if the device is online. */
    private void signIn() {
        EventValidator.Result<String> email = EventValidator.validateText(textOf(emailInput));
        if (email.isValid()) {
            email = EventValidator.validateEmail(email.getValue());
        }
        String password = textOf(passwordInput);
        emailLayout.setError(email.isValid() ? null : getString(
                email.getReason() == EventValidator.Reason.EMPTY
                        ? R.string.error_email_required : R.string.error_email_format));
        passwordLayout.setError(
                password.isEmpty() ? getString(R.string.error_password_required) : null);
        for (TextInputLayout layout : new TextInputLayout[] {emailLayout, passwordLayout}) {
            if (layout.getError() != null) {
                layout.getEditText().requestFocus();
                return;
            }
        }

        // Put away, so it does not cover a message at the bottom of the screen.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .hide(WindowInsetsCompat.Type.ime());
        if (!CloudSync.isOnline(this)) {
            IconSnackbar.make(signInButton, getString(R.string.message_offline),
                    R.drawable.ic_cloud_off, Snackbar.LENGTH_LONG).show();
            return;
        }
        // Off until Firebase answers, so a second tap does not sign in twice.
        signInButton.setEnabled(false);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.getValue(), password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                        return;
                    }
                    signInButton.setEnabled(true);
                    if (task.getException() instanceof FirebaseAuthException) {
                        // One message for both, so it does not reveal which emails have accounts.
                        passwordLayout.setError(getString(R.string.error_sign_in));
                        passwordInput.requestFocus();
                    } else {
                        IconSnackbar.make(signInButton, getString(R.string.message_sign_in_failed),
                                R.drawable.ic_cloud_off, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private static String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /** Clears a field's error once the user edits it. */
    private static final class InputWatcher implements TextWatcher {

        private final TextInputLayout layout;
        private String textBefore = "";

        InputWatcher(TextInputLayout layout) {
            this.layout = layout;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            textBefore = s.toString();
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Some keyboards report changes that leave the text as it was.
            if (!s.toString().equals(textBefore)) {
                layout.setError(null);
            }
        }
    }
}
