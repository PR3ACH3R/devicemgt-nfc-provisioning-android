package org.wso2.iot.nfcprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import com.skyfishjy.library.RippleBackground;

import org.wso2.iot.agent.proxy.IdentityProxy;
import org.wso2.iot.agent.proxy.beans.Token;
import org.wso2.iot.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.iot.agent.proxy.interfaces.TokenCallBack;
import org.wso2.iot.agent.proxy.utils.Constants;
import org.wso2.iot.nfcprovisioning.utils.Preference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ProvisioningActivity extends AppCompatActivity implements TokenCallBack, NfcAdapter.CreateNdefMessageCallback {
    private static final String TAG = ProvisioningActivity.class.getSimpleName();
    private Context context;
    private NfcAdapter nfcAdapter;
    private Activity activity;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provisioning);

        context = ProvisioningActivity.this;
        activity = ProvisioningActivity.this;
        Toolbar mainToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);

/*        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        if(ab!=null)
        ab.setDisplayHomeAsUpEnabled(true);*/

        final RippleBackground rippleBackground=(RippleBackground)findViewById(R.id.content);
        rippleBackground.startRippleAnimation();


        nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        if (nfcAdapter != null) {
            nfcAdapter.setNdefPushMessageCallback(this, activity);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(ProvisioningActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.top_bar_menu, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyToken();
    }

    @Override
    public void onReceiveTokenResult(Token token, String status, String message) {
        if (Constants.REQUEST_SUCCESSFUL.equals(status)) {
            this.token = token.getAccessToken();
        } else if (Constants.ACCESS_FAILURE.equals(status)) {
            Log.w(TAG, "Bad request: " + message);
            Preference.putBoolean(context, org.wso2.iot.nfcprovisioning.utils.Constants.TOKEN_EXPIRED, true);
            Toast.makeText(context, context.getResources().getString(R.string.msg_need_to_sign_in),
                    Toast.LENGTH_SHORT).show();
            Intent intent = new Intent( ProvisioningActivity.this, AuthenticationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }



    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Properties properties = new Properties();
        Map<String, String> provisioningValues = getProvisioningValues();
        if (org.wso2.iot.nfcprovisioning.utils.Constants.AUTHENTICATOR_IN_USE.equals(org.wso2.iot.nfcprovisioning.utils.Constants.OAUTH_AUTHENTICATOR)) {
            Properties props = new Properties();
            props.put("android.app.extra.token", token);
            StringWriter sw = new StringWriter();
            try {
                props.store(sw, "admin extras bundle");
                provisioningValues.put(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                        sw.toString());
                Log.d(TAG, "Admin extras bundle=" + provisioningValues.get(
                        DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
            } catch (IOException e) {
                Log.e(TAG, "Unable to build admin extras bundle");
            }
        }
        for (Map.Entry<String, String> e : provisioningValues.entrySet()) {
            if (!TextUtils.isEmpty(e.getValue())) {
                String value;
                if (e.getKey().equals(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID)) {
                    // Make sure to surround SSID with double quotes
                    value = e.getValue();
                    if (!value.startsWith("\"") || !value.endsWith("\"")) {
                        value = "\"" + value + "\"";
                    }
                } else //noinspection deprecation
                    if (e.getKey().equals(
                            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME)
                            && Build.VERSION.SDK_INT >= 23) {
                        continue;
                    } else {
                        value = e.getValue();
                    }
                properties.put(e.getKey(), value);
            }
        }
        // Make sure to put local time in the properties. This is necessary on some devices to
        // reliably download the device owner APK from an HTTPS connection.
        if (!properties.contains(DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME)) {
            properties.put(DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME,
                    String.valueOf(System.currentTimeMillis()));
        }
        try {
            properties.store(stream, getString(R.string.nfc_comment));
            NdefRecord record = NdefRecord.createMime(
                    DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC, stream.toByteArray());
            return new NdefMessage(new NdefRecord[]{record});
        } catch (IOException e) {
            e.printStackTrace();
        }


        return null;
    }

    Map<String, String > getProvisioningValues(){
        Map<String, String > valuesMap = new HashMap<>();

        valuesMap.put(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, getResources().getString(R.string.package_name));
        valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, getResources().getString(R.string.package_download_location));
        valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM, getResources().getString(R.string.package_checksum));
        valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID, getResources().getString(R.string.wifi_ssid));

        String wifiSecurityType = getResources().getString(R.string.wifi_security_type);
        valuesMap.put(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, wifiSecurityType);
        if(!wifiSecurityType.equals("NONE")) {
            valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD, getResources().getString(R.string.wifi_password));
        }
        valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE, getResources().getString(R.string.time_zone));
        valuesMap.put(DevicePolicyManager.EXTRA_PROVISIONING_LOCALE, getResources().getString(R.string.locale));


        if (Build.VERSION.SDK_INT >= 23) {
            valuesMap.put(
                    DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, (getResources().getString(R.string.encryption).equalsIgnoreCase("true")?"false":"true"));
        }
        return valuesMap;
    }

    void verifyToken(){
        String clientKey = Preference.getString(context, Constants.CLIENT_ID);
        String clientSecret = Preference.getString(context, Constants.CLIENT_SECRET);

        if (IdentityProxy.getInstance().getContext() == null) {
            IdentityProxy.getInstance().setContext(context);
        }

        IdentityProxy.getInstance().setRequestCode(org.wso2.iot.nfcprovisioning.utils.Constants.TOKEN_VERIFICATION_REQUEST_CODE);

        IdentityProxy.getInstance().requestToken(IdentityProxy.getInstance().getContext(), this,
                clientKey,
                clientSecret);
    }
}
