/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import org.thoughtcrime.securesms.logging.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;

import com.dd.CircularProgressButton;
import com.google.android.material.textfield.TextInputLayout;

import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.DynamicIntroTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private static final String TAG = PassphrasePromptActivity.class.getSimpleName();

  private DynamicIntroTheme dynamicTheme    = new DynamicIntroTheme();
  private DynamicLanguage   dynamicLanguage = new DynamicLanguage();

  private View                   passphraseAuthContainer;
  private TextInputLayout        passphraseLayout;
  private EditText               passphraseInput;
  private CircularProgressButton okButton;
  private View                   successView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate()");
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    passphraseInput.setText("");
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.log_submit, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_submit_debug_logs: handleLogSubmit(); return true;
    }

    return false;
  }

  private void handleLogSubmit() {
    Intent intent = new Intent(this, LogSubmitActivity.class);
    startActivity(intent);
  }

  private void handlePassphrase() {
    char[] passphrase = getEnteredPassphrase(passphraseInput);
    if (passphrase.length > 0) {
      SetMasterSecretTask task = new SetMasterSecretTask(passphrase);
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else {
      showFailure();
    }
  }

  private char[] getEnteredPassphrase(final EditText editText) {
    int len = editText.length();
    char[] passphrase = new char[len];
    if (editText.getText() != null) {
      editText.getText().getChars(0, len, passphrase, 0);
    }
    return passphrase;
  }

  private void initializeResources() {
    Toolbar     toolbar  = findViewById(R.id.toolbar);

    passphraseAuthContainer = findViewById(R.id.password_auth_container);
    passphraseLayout        = findViewById(R.id.passphrase_layout);
    passphraseInput         = findViewById(R.id.passphrase_input);
    okButton                = findViewById(R.id.ok_button);
    successView             = findViewById(R.id.success);

    setSupportActionBar(toolbar);
    getSupportActionBar().setTitle("");

    SpannableString hint = new SpannableString(getString(R.string.PassphrasePromptActivity_enter_passphrase));
    hint.setSpan(new RelativeSizeSpan(0.9f), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    hint.setSpan(new TypefaceSpan("sans-serif-light"), 0, hint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    passphraseInput.setHint(hint);
    passphraseInput.setOnEditorActionListener(new PassphraseActionListener());

    okButton.setOnClickListener(v -> handlePassphrase());
  }

  private class PassphraseActionListener implements TextView.OnEditorActionListener {
    @Override
    public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent keyEvent) {
      if ((keyEvent == null && actionId == EditorInfo.IME_ACTION_DONE) ||
          (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
           (actionId == EditorInfo.IME_NULL))) {
        if (okButton.isClickable()) {
          okButton.performClick();
        }
        return true;
      }
      return keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP
              && actionId == EditorInfo.IME_NULL;
    }
  }

  private void setInputEnabled(boolean enabled) {
    if (enabled) {
      passphraseInput.selectAll();
    } else {
      passphraseInput.clearFocus();
    }
    passphraseLayout.setEnabled(enabled);
    passphraseLayout.setPasswordVisibilityToggleEnabled(enabled);
    okButton.setClickable(enabled);
  }

  private void showProgress(float x) {
    double y = 1 + Math.pow(1 - x, 1.5) * -1;
    okButton.setProgress((int) (y * 99));
  }

  private void showFailure() {
    if (passphraseInput.requestFocus()) {
      InputMethodManager imm = ServiceUtil.getInputMethodManager(this);
      imm.showSoftInput(passphraseInput, InputMethodManager.SHOW_IMPLICIT);
    }

    TranslateAnimation shake = new TranslateAnimation(0, 30, 0, 0);
    shake.setDuration(50);
    shake.setRepeatCount(7);
    passphraseAuthContainer.startAnimation(shake);
  }

  private void handleSuccessfulPassphrase(MasterSecret masterSecret) {
    int shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

    successView.setAlpha(0f);
    successView.setVisibility(View.VISIBLE);
    successView.animate().alpha(1f).setDuration(shortAnimationDuration).setListener(null);

    okButton.animate().alpha(0f).setDuration(shortAnimationDuration).setListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        setMasterSecret(masterSecret);
      }
    });;
  }

  @SuppressLint("StaticFieldLeak")
  private class SetMasterSecretTask extends AsyncTask<Void, Float, MasterSecret> {

    private final char[] passphrase;

    private CountDownTimer progressTimer;

    SetMasterSecretTask(char[] passphrase) {
      this.passphrase = passphrase;
    }

    @Override
    protected void onPreExecute() {
      setInputEnabled(false);
      initializeProgressTimer();
    }

    @Override
    protected MasterSecret doInBackground(Void... voids) {
      progressTimer.start();

      MasterSecret masterSecret;
      try {
        masterSecret = MasterSecretUtil.getMasterSecret(getApplicationContext(), passphrase);
      } catch (InvalidPassphraseException | UnrecoverableKeyException e) {
        masterSecret = null;
      }

      progressTimer.cancel();

      return masterSecret;
    }

    @Override
    protected void onProgressUpdate(Float... values) {
      showProgress(values[0]);
    }

    @Override
    protected void onPostExecute(MasterSecret masterSecret) {
      if (masterSecret != null) {
        handleSuccessfulPassphrase(masterSecret);
      } else {
        setInputEnabled(true);
        showProgress(0f);
        showFailure();
      }
    }

    private void initializeProgressTimer() {
      long countdown = MasterSecretUtil.getKdfElapsedTimeMillis(getApplicationContext()) + 250;

      progressTimer = new CountDownTimer(countdown, 100) {
        @Override
        public void onTick(long millisUntilFinished) {
          publishProgress(1 - (millisUntilFinished / (float) countdown));
        }

        @Override
        public void onFinish() {
          publishProgress(1f);
        }
      };
    }
  }
}
