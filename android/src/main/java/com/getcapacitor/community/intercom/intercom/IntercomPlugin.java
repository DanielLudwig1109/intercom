package com.getcapacitor.community.intercom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.getcapacitor.CapConfig;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import io.intercom.android.sdk.Intercom;
import io.intercom.android.sdk.IntercomError;
import io.intercom.android.sdk.IntercomPushManager;
import io.intercom.android.sdk.IntercomStatusCallback;
import io.intercom.android.sdk.UserAttributes;
import io.intercom.android.sdk.Company;
import io.intercom.android.sdk.identity.Registration;
import io.intercom.android.sdk.push.IntercomPushClient;
import io.intercom.android.sdk.UnreadConversationCountListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;

@CapacitorPlugin(name = "Intercom", permissions = @Permission(strings = {}, alias = "receive"))
public class IntercomPlugin extends Plugin {

    private static final String EVENT_WINDOW_DID_SHOW = "windowDidShow";
    private static final String EVENT_WINDOW_DID_HIDE = "windowDidHide";
    private static final String EVENT_UNREAD_COUNT_CHANGED = "updateUnreadCount";

    private Intercom _intercomClient = null;
    private final IntercomPushClient intercomPushClient = new IntercomPushClient();
    private final UnreadConversationCountListener unreadCountListener = new UnreadConversationCountListener() {
        @Override
        public void onCountUpdate(int unreadCount) {
            JSObject result = new JSObject();
            result.put("unreadCount", unreadCount);
            notifyListeners(EVENT_UNREAD_COUNT_CHANGED, result);
        }
    };

    @Override
    public void load() {
        // Set up Intercom
        setUpIntercom(null, null);

        // load parent
        super.load();
    }

    @PluginMethod
    public void loadWithKeys(PluginCall call) {
        String appId = call.getString("appId", "NO_APP_ID_PASSED");
        String apiKey = call.getString("apiKeyAndroid", "NO_API_KEY_PASSED");

        setUpIntercom(apiKey, appId);

        // load parent
        super.load();
    }

    @Override
    public void handleOnStart() {
        super.handleOnStart();
        bridge
                .getActivity()
                .runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                //We also initialize intercom here just in case it has died. If Intercom is already set up, this won't do anything.
                                setUpIntercom(null, null);

                                if (_intercomClient != null) {
                                    _intercomClient.handlePushMessage();
                                }
                            }
                        }
                );
    }

    @Override
    public void handleOnPause() {
        super.handleOnPause();
        notifyListeners(EVENT_WINDOW_DID_SHOW, null);
    }

    @Override
    public void handleOnResume() {
        super.handleOnResume();
        notifyListeners(EVENT_WINDOW_DID_HIDE, null);
    }

    @PluginMethod
    public void registerIdentifiedUser(PluginCall call) {
        String email = call.getString("email");
        String userId = call.getData().getString("userId");

        Registration registration = new Registration();

        if (email != null && email.length() > 0) {
            registration = registration.withEmail(email);
        }
        if (userId != null && userId.length() > 0) {
            registration = registration.withUserId(userId);
        }

        if (_intercomClient != null) {
            _intercomClient.registerIdentifiedUser(registration);
        }
        call.resolve();
    }

    @PluginMethod
    public void registerUnidentifiedUser(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.registerUnidentifiedUser();
        }
        call.resolve();
    }

    @PluginMethod
    public void updateUser(PluginCall call) {
        UserAttributes.Builder builder = new UserAttributes.Builder();
        String userId = call.getString("userId");
        if (userId != null && userId.length() > 0) {
            builder.withUserId(userId);
        }
        String email = call.getString("email");
        if (email != null && email.length() > 0) {
            builder.withEmail(email);
        }
        String name = call.getString("name");
        if (name != null && name.length() > 0) {
            builder.withName(name);
        }
        String phone = call.getString("phone");
        if (phone != null && phone.length() > 0) {
            builder.withPhone(phone);
        }
        String languageOverride = call.getString("languageOverride");
        if (languageOverride != null && languageOverride.length() > 0) {
            builder.withLanguageOverride(languageOverride);
        }
        JSObject companyObject = call.getObject("company");
        String companyId = "";
        String companyName = "";
        if (companyObject != null) {
            companyId = companyObject.getString("id");
            companyName = companyObject.getString("name");
        }
        if (companyId != null && !companyId.isEmpty() && companyName != null && !companyName.isEmpty()) {
            Company company = new Company.Builder().withName(companyName).withCompanyId(companyId).build();
            builder.withCompany(company);
        }
        Map<String, Object> customAttributes = mapFromJSON(call.getObject("customAttributes"));
        builder.withCustomAttributes(customAttributes);
        if (_intercomClient != null) {
            _intercomClient.updateUser(builder.build());
        }
        call.resolve();
    }

    @PluginMethod
    public void logout(PluginCall call) {
        if (_intercomClient != null) {
            removeUnreadCountListener();
            _intercomClient.logout();
        }
        call.resolve();
    }

    @PluginMethod
    public void logEvent(PluginCall call) {
        String eventName = call.getString("name");
        Map<String, Object> metaData = mapFromJSON(call.getObject("data"));


        if (_intercomClient != null) {
            if (metaData == null) {
                _intercomClient.logEvent(eventName);
            } else {
                _intercomClient.logEvent(eventName, metaData);
            }
        }

        call.resolve();
    }

    @PluginMethod
    public void displayMessenger(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.displayMessenger();
        }
        call.resolve();
    }

    @PluginMethod
    public void displayMessageComposer(PluginCall call) {
        String message = call.getString("message");
        if (_intercomClient != null) {
            _intercomClient.displayMessageComposer(message);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayHelpCenter(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.displayHelpCenter();
        }
        call.resolve();
    }

    @PluginMethod
    public void hideMessenger(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.hideIntercom();
        }
        call.resolve();
    }

    @PluginMethod
    public void displayLauncher(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.setLauncherVisibility(Intercom.VISIBLE);
        }
        call.resolve();
    }

    @PluginMethod
    public void hideLauncher(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.setLauncherVisibility(Intercom.GONE);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayInAppMessages(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.setLauncherVisibility(Intercom.VISIBLE);
        }
        call.resolve();
    }

    @PluginMethod
    public void hideInAppMessages(PluginCall call) {
        if (_intercomClient != null) {
            _intercomClient.setLauncherVisibility(Intercom.GONE);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayCarousel(PluginCall call) {
        String carouselId = call.getString("carouselId");
        if (_intercomClient != null) {
            _intercomClient.displayCarousel(carouselId);
        }
        call.resolve();
    }

    @PluginMethod
    public void setUserHash(PluginCall call) {
        String hmac = call.getString("hmac");
        if (_intercomClient != null) {
            _intercomClient.setUserHash(hmac);
        }
        call.resolve();
    }

    @PluginMethod
    public void setUserJwt(PluginCall call) {
        String jwt = call.getString("jwt");
        if (_intercomClient != null) {
           _intercomClient.setUserJwt(jwt);
        }
        call.resolve();
    }

    @PluginMethod
    public void setBottomPadding(PluginCall call) {
        if (_intercomClient != null) {
            String stringValue = call.getString("value");
            int value = Integer.parseInt(stringValue);
            _intercomClient.setBottomPadding(value);
        }
        call.resolve();
    }

    @PluginMethod
    public void sendPushTokenToIntercom(PluginCall call) {
        String token = call.getString("value");
        try {
            intercomPushClient.sendTokenToIntercom(this.getActivity().getApplication(), token);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to send push token to Intercom", e);
        }
    }

    @PluginMethod
    public void receivePush(PluginCall call) {
        try {
            JSObject notificationData = call.getData();
            Map message = mapFromJSON(notificationData);
            if (intercomPushClient.isIntercomPush(message)) {
                intercomPushClient.handlePush(this.getActivity().getApplication(), message);
                call.resolve();
            } else {
                call.reject("Notification data was not a valid Intercom push message");
            }
        } catch (Exception e) {
            call.reject("Failed to handle received Intercom push", e);
        }
    }

    @PluginMethod
    public void displayArticle(PluginCall call) {
        String articleId = call.getString("articleId");
        if (_intercomClient != null) {
            _intercomClient.displayArticle(articleId);
        }
        call.resolve();
    }

    private void setUpIntercom(String loadApiKey, String loadAppId) {
        try {
            // get config
            CapConfig config = this.bridge.getConfig();
            String apiKey = (loadApiKey != null) ? loadApiKey : config.getPluginConfiguration("Intercom").getString("androidApiKey");
            String appId = (loadAppId != null) ? loadAppId : config.getPluginConfiguration("Intercom").getString("androidAppId");

            if (apiKey == null || apiKey.isEmpty() || appId == null || appId.isEmpty()) {
                Logger.warn("Intercom", "ERROR: Missing Intercom API key or App ID");
                _intercomClient = null;
                return;
            }
            
            _setIntercomClient();

            // init intercom sdk
            Intercom.initialize(this.getActivity().getApplication(), apiKey, appId);
            setUpUnreadCountListener();
        } catch (Exception e) {
            Logger.error("Intercom", "ERROR: Something went wrong when initializing Intercom. Check your configurations", e);
            removeUnreadCountListener();
        }
    }

    private void setUpUnreadCountListener() {
        if (_intercomClient != null) {
            _intercomClient.addUnreadConversationCountListener(unreadCountListener);
        }
    }

    private void removeUnreadCountListener() {
        if (_intercomClient != null) {
            _intercomClient.removeUnreadConversationCountListener(unreadCountListener);
        }
    }

    private static Map<String, Object> mapFromJSON(JSObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keysIter = jsonObject.keys();
        while (keysIter.hasNext()) {
            String key = keysIter.next();
            Object value = getObject(jsonObject.opt(key));
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Object getObject(Object value) {
        if (value instanceof JSObject) {
            value = mapFromJSON((JSObject) value);
        } else if (value instanceof JSArray) {
            value = listFromJSON((JSArray) value);
        }
        return value;
    }

    private static List<Object> listFromJSON(JSArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (int i = 0, count = jsonArray.length(); i < count; i++) {
            Object value = getObject(jsonArray.opt(i));
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private void _setIntercomClient() {
        if (_intercomClient == null) {
            try {
                _intercomClient = Intercom.client();
            } catch (Exception e) {
                Logger.error("Intercom", "Intercom client not ready yet.", e);
            }
        }

        removeUnreadCountListener();
    }
}
