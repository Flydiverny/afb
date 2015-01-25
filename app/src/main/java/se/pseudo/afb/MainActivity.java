package se.pseudo.afb;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

    private WebView webView;
    private ProgressDialog dialog;
    private UserDetailsDialogFragment userDetails;

    private boolean loggedIn = false, categoryLoaded = false, bounced = false, attemptAutoLogin = false;

    private int loadingCounter = 0;

    private static final String AFB_LOGIN = "http://www.afb.se/templates/AFAdminLogin.aspx?ReturnUrl=%2fredimo%2faptus%2fext_gw.jsp%3fmodule%3dwwwash";
    private static final String AFB_CATEGORY = "http://aptusportal.afb.se/wwwash.aspx";
    private static final String AFB_WASH = "http://aptusportal.afb.se/wwwashbookinglocationsforcategory.aspx?categoryId=1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setupWebView();

        loadAFB();
    }

    private boolean firstRun() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getBoolean(getString(R.string.pref_firstrun), true);
    }

    private void setupWebView() {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }*/

        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new AFBWebChromeClient());
        webView.setWebViewClient(new AFBWebViewClient());
        webView.setPictureListener(new WebView.PictureListener() {
            @Override
            public void onNewPicture(WebView webView, Picture picture) {
                if(webView.getUrl().equals(AFB_LOGIN) && !loggedIn && !firstRun() && loadingCounter == 0 && attemptAutoLogin) {
                    login();
                }
            }
        });

        // Fix Lollipop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    private void setDialog(String msg) {
        if (dialog == null || !dialog.isShowing()) {
            dialog = ProgressDialog.show(this, "", msg, true, false);
        } else {
            dialog.setMessage(msg);
        }
    }

    private void loadAFB() {
        if(firstRun()) {
            setUserDetails();
            return;
        }

        setDialog("Laddar AFB..");

        // Reset flags
        loggedIn = false;
        categoryLoaded = false;
        bounced = false;
        attemptAutoLogin = false;

        // Load login page
        webView.loadUrl(AFB_LOGIN);
    }

    private void login() {
        if(!webView.getUrl().equals(AFB_LOGIN)) {
            Toast.makeText(MainActivity.this, "Login can only be triggered on the login page.", Toast.LENGTH_SHORT).show();
            return;
        }

        setDialog("Loggar in..");

        final String def = "poop";
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String user = sharedPref.getString(getString(R.string.pref_username), def);
        String pw = sharedPref.getString(getString(R.string.pref_password), def);

        String js = String.format("document.getElementById(\"Username\").setAttribute(\"value\", \"%1$s\");document.getElementById(\"Password\").setAttribute(\"value\",\"%2$s\");document.getElementById(\"LoginImageButton\").click();", user, pw.replace("\"", "\\\""));


        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                loggedIn = true;
            }
        });
    }

    private void setUserDetails() {
        userDetails = new UserDetailsDialogFragment();
        userDetails.setCancelable(false);
        userDetails.show(getFragmentManager(), "userdetails");
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        // In case login box was up
        if(userDetails != null)
            userDetails.dismiss();

        loadAFB();
    }

    private class AFBWebViewClient extends WebViewClient {
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Toast.makeText(MainActivity.this, "Oh no! " + description, Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String urlNewString) {
            if(urlNewString.equals(AFB_CATEGORY)) {
                redirectToWashingCategory();
                return true;
            }

            loadingCounter++;
            webView.loadUrl(urlNewString);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap icon) {
            super.onPageStarted(view, url, icon);

            loadingCounter = Math.max(loadingCounter, 1); // First request move it to 1.

            if(dialog == null) {
                setDialog("Loading..");
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            if(--loadingCounter == 0) {
                if (!attemptAutoLogin && url.equals(AFB_LOGIN) && !loggedIn) {
                    attemptAutoLogin = true;
                    return;
                }

                switch(url) {
                    case AFB_LOGIN:
                        loginFailed();
                        return;
                }

                if(categoryLoaded) {
                    if(bounced) {
                        dialog.dismiss();
                        dialog = null;
                    } else {
                        bounced = true;
                    }
                }
            }
        }
    }

    private void loginFailed() {
        loggedIn = false;
        attemptAutoLogin = false;

        dialog.dismiss();

        Toast.makeText(MainActivity.this, R.string.login_failed, Toast.LENGTH_SHORT).show();

        setUserDetails();
    }

    private void redirectToWashingCategory() {
        // Login success -> Continue bouncing to Washing category :D
        setDialog("Laddar bokningsida..");

        categoryLoaded = true;

        webView.loadUrl(AFB_WASH);
    }

    private class AFBWebChromeClient extends WebChromeClient {
        public void onProgressChanged(WebView view, int progress) {
            // Activities and WebViews measure progress with different scales.
            // The progress meter will automatically disappear when we reach 100%
            MainActivity.this.setProgress(progress * 1000);
        }
    }

    @SuppressLint("ValidFragment")
    public class UserDetailsDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            // Get the layout inflater
            LayoutInflater inflater = getLayoutInflater();

            final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

            View view                   = inflater.inflate(R.layout.dialog_userdetails, null);
            final EditText username     = (EditText)view.findViewById(R.id.username);
            final EditText password     = (EditText)view.findViewById(R.id.password);


            String user = sharedPref.getString(getString(R.string.pref_username), "poop");
            String pw = sharedPref.getString(getString(R.string.pref_password), "poop");

            username.setText(user);
            password.setText(pw);


            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(view)
                    // Add action buttons
                    .setPositiveButton(MainActivity.this.loggedIn ? R.string.save : R.string.login, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {

                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString(getString(R.string.pref_username), username.getText().toString());
                            editor.putString(getString(R.string.pref_password), password.getText().toString());
                            editor.putBoolean(getString(R.string.pref_firstrun), false);

                            editor.commit();

                            if (AFB_LOGIN.equals(webView.getUrl())) {
                                MainActivity.this.login();
                            } else {
                                loadAFB();
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //UserDetailsDialogFragment.this.getDialog().cancel();
                            getActivity().finish();
                            System.exit(0);
                        }
                    });

           return builder.create();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                setUserDetails();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }
}



