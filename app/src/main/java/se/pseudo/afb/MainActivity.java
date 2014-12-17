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
    private boolean loggedIn = false, firstLoad = true, firstFlickr = true;

    private int loadingCounter = 0;

    private static final String AFB_LOGIN = "http://www.afb.se/templates/AFAdminLogin.aspx?ReturnUrl=%2fredimo%2faptus%2fext_gw.jsp%3fmodule%3dwwwash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new AFBWebChromeClient());
        webView.setWebViewClient(new AFBWebViewClient());
        webView.setPictureListener(new WebView.PictureListener() {
            @Override
            public void onNewPicture(WebView webView, Picture picture) {
                if(webView.getUrl().equals(AFB_LOGIN) && !loggedIn && !needsSetup() && loadingCounter == 0) {
                    login();
                }
            }
        });
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if(needsSetup()) {
            setUserDetails();
        } else {
            afb();
        }
    }

    private void setDialog(String msg) {
        if (dialog == null || !dialog.isShowing()) {
            dialog = ProgressDialog.show(this, "", msg, true, false);
        } else {
            dialog.setMessage(msg);
        }
    }

    private boolean needsSetup() {
        final String def = "poop";
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String user = sharedPref.getString(getString(R.string.pref_username), def);
        String pw = sharedPref.getString(getString(R.string.pref_password), def);

        Log.d("wtf", user + " " + pw + " " + ( user.equals(def )|| pw.equals(def)));

        return user.equals(def)|| pw.equals(def);
    }

    private void afb() {
        setDialog("Laddar AFB..");
        webView.loadUrl(AFB_LOGIN);
    }

    private void login() {
        if(webView.getUrl().equals(AFB_LOGIN)) {
            setDialog("Loggar in..");

            final String def = "poop";
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            String user = sharedPref.getString(getString(R.string.pref_username), def);
            String pw = sharedPref.getString(getString(R.string.pref_password), def);

            String js = String.format("document.getElementById(\"Username\").setAttribute(\"value\", \"%1$s\");document.getElementById(\"Password\").setAttribute(\"value\",\"%2$s\");document.getElementById(\"LoginImageButton\").click();", user, pw);

            webView.evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    loggedIn = true;
                }
            });
        } else {
            Toast.makeText(MainActivity.this, "Login can only be triggered on the login page.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setUserDetails() {
        UserDetailsDialogFragment dialog = new UserDetailsDialogFragment();
        dialog.show(getFragmentManager(), "userdetails");
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first
        loggedIn = false;
        afb();
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

            case R.id.action_login:
                login();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private class AFBWebViewClient extends WebViewClient {
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Toast.makeText(MainActivity.this, "Oh no! " + description, Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String urlNewString) {
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
                if(dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }

                if(url.equals(AFB_LOGIN) && loggedIn) {
                    if(!firstLoad) {
                        Toast.makeText(MainActivity.this, "Seems like you were unable to login!", Toast.LENGTH_SHORT).show();
                        setUserDetails();
                    } else {
                        firstLoad = false;
                    }
                }
            }
        }
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
                    .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {

                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString(getString(R.string.pref_username), username.getText().toString());
                            editor.putString(getString(R.string.pref_password), password.getText().toString());

                            editor.commit();

                            if (needsSetup()) {
                                MainActivity.this.setUserDetails();
                            } else {
                                if (webView.getUrl().equals(AFB_LOGIN)) {
                                    MainActivity.this.login();
                                }
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            UserDetailsDialogFragment.this.getDialog().cancel();
                        }
                    });

           return builder.create();
        }
    }
}


