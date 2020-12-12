/*
 * Copyright (C) 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.wearverifyremoteapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.wearable.phone.PhoneDeviceType;
import android.support.wearable.view.ConfirmationOverlay;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;

import java.util.Set;

public class MainWearActivity extends FragmentActivity implements
        AmbientModeSupport.AmbientCallbackProvider,
        CapabilityClient.OnCapabilityChangedListener {

    private static final String TAG = MainWearActivity.class.getSimpleName();

    private static final String MSG_WELCOME = "WearVerify\n\n";
    private static final String MSG_CHECKING_APP = MSG_WELCOME
            + "Checking for Mobile app...";
    private static final String MSG_APP_MISSING = MSG_WELCOME
            + "You are missing the phone app, please click on the button below to "
            + "install it on your phone.";

    private static final String MSG_APP_INSTALLED = MSG_WELCOME
            + "You are ready to go.";

    /** Name of capability listed in Phone app's wear.xml.
     *
     * IMPORTANT NOTE: This should be named differently than your Wear app's capability.
     */
    private static final String CAPABILITY_PHONE_APP = "verifywear_phone_app";

    /**
     * Link to install mobile app for Android (Play Store).
     */
    private static final String URI_ANDROID_PLAY_STORE = "market://details?id=com.tennismath.ui.android";

    /**
     * Link to install mobile app for iOS.
     */
    // FIXME: Replace with AppStore link (when app is ready)
    private static final String URI_IOS_APP_STORE = "https://itunes.apple.com/us/app/android-wear/id000000000?mt=8";

    // Result from sending RemoteIntent to phone to open app in play/app store.
    private final ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode == RemoteIntent.RESULT_OK) {
                new ConfirmationOverlay().showOn(MainWearActivity.this);

            } else if (resultCode == RemoteIntent.RESULT_FAILED) {
                new ConfirmationOverlay()
                        .setType(ConfirmationOverlay.FAILURE_ANIMATION)
                        .showOn(MainWearActivity.this);
            } else {
                throw new IllegalStateException("Unexpected result " + resultCode);
            }
        }
    };

    private TextView txtPrompt;
    private Button btnInstallApp;

    private Node androidPhoneNodeWithApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "# onCreate()");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Enables Ambient mode.
        AmbientModeSupport.attach(this);
        //TODO use different screen colors for both modes

        txtPrompt = findViewById(R.id.txt_prompt);
        btnInstallApp = findViewById(R.id.btn_install_app);

        btnInstallApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAppInStoreOnPhone();
            }
        });
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "# onPause()");
        super.onPause();

        Wearable.getCapabilityClient(this).removeListener(this, CAPABILITY_PHONE_APP);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "# onResume()");
        super.onResume();

        Wearable.getCapabilityClient(this).addListener(this, CAPABILITY_PHONE_APP);

        checkIfPhoneHasApp();
    }

    /*
     * Updates UI when capabilities change (install/uninstall phone app).
     */
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.d(TAG, "# onCapabilityChanged(): " + capabilityInfo);

        androidPhoneNodeWithApp = pickBestNodeId(capabilityInfo.getNodes());
        verifyNodeAndUpdateUI();
    }

    private void checkIfPhoneHasApp() {
        Log.d(TAG, "# checkIfPhoneHasApp()");

        txtPrompt.setText(MSG_CHECKING_APP);

        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this)
                .getCapability(CAPABILITY_PHONE_APP, CapabilityClient.FILTER_ALL);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(@NonNull Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    Log.d(TAG, "Capability request succeeded.");
                    CapabilityInfo capabilityInfo = task.getResult();
                    if (capabilityInfo!=null) {
                        androidPhoneNodeWithApp = pickBestNodeId(capabilityInfo.getNodes());
                    } else {
                        Log.e(TAG, "Cannot get capability info");
                    }
                } else {
                    Log.d(TAG, "Capability request failed to return any results.");
                }

                verifyNodeAndUpdateUI();
            }
        });
    }

    private void verifyNodeAndUpdateUI() {

        if (androidPhoneNodeWithApp != null) {
            Log.d(TAG, String.format("Phone app installed: %s", androidPhoneNodeWithApp.getDisplayName()));
            txtPrompt.setText(MSG_APP_INSTALLED);
            btnInstallApp.setVisibility(View.INVISIBLE);

            // TODO: wait for the phone's message that tracking is started, then switch activity to ScoreTrackingActivity

        } else {
            Log.d(TAG, MSG_APP_MISSING);
            txtPrompt.setText(MSG_APP_MISSING);
            btnInstallApp.setVisibility(View.VISIBLE);
        }
    }

    private void openAppInStoreOnPhone() {
        Log.d(TAG, "# openAppInStoreOnPhone()");

        int phoneDeviceType = PhoneDeviceType.getPhoneDeviceType(getApplicationContext());
        switch (phoneDeviceType) {
            case PhoneDeviceType.DEVICE_TYPE_ANDROID:
                // Paired to Android phone, use Play Store URI.
                Log.d(TAG, "\tDEVICE_TYPE_ANDROID");

                // Create Remote Intent to open Play Store listing of app on remote device.
                Intent intentAndroid =
                        new Intent(Intent.ACTION_VIEW)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setData(Uri.parse(URI_ANDROID_PLAY_STORE));
                RemoteIntent.startRemoteActivity(
                        getApplicationContext(),
                        intentAndroid,
                        resultReceiver);
                break;

            case PhoneDeviceType.DEVICE_TYPE_IOS:
                // Paired to iPhone, use iTunes App Store URI
                Log.d(TAG, "\tDEVICE_TYPE_IOS");

                // Create Remote Intent to open App Store listing of app on iPhone.
                Intent intentIOS =
                        new Intent(Intent.ACTION_VIEW)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setData(Uri.parse(URI_IOS_APP_STORE));
                RemoteIntent.startRemoteActivity(
                        getApplicationContext(),
                        intentIOS,
                        resultReceiver);
                break;

            case PhoneDeviceType.DEVICE_TYPE_ERROR_UNKNOWN:
                Log.d(TAG, "\tDEVICE_TYPE_ERROR_UNKNOWN");
                break;
        }
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
     */
    private static Node pickBestNodeId(@NonNull Set<Node> nodes) {
        Log.d(TAG, "# pickBestNodeId(): " + nodes);

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.

        return nodes.isEmpty() ? null : nodes.iterator().next();
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new AmbientModeSupport.AmbientCallback () {
            @Override
            public void onEnterAmbient(Bundle ambientDetails) {
                super.onEnterAmbient(ambientDetails);

                Log.d(TAG, "# onEnterAmbient() " + ambientDetails);

                txtPrompt.setTextColor(Color.GRAY);
                txtPrompt.getPaint().setAntiAlias(false);

                btnInstallApp.setEnabled(false);
            }

            @Override
            public void onExitAmbient() {
                super.onExitAmbient();

                Log.d(TAG, "# onExitAmbient()");

                txtPrompt.setTextColor(Color.WHITE);
                txtPrompt.getPaint().setAntiAlias(true);

                btnInstallApp.setEnabled(true);

            }
         };
    }

}
