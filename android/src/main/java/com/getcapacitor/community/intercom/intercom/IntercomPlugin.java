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

    private boolean _initIntercom = false;
    private final IntercomPushClient intercomPushClient = new IntercomPushClient();

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

                                Intercom client = _getIntercomClient();
                                if (client != null) {
                                    client.handlePushMessage();
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
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.registerIdentifiedUser(registration);
        }
        call.resolve();
    }

    @PluginMethod
    public void registerUnidentifiedUser(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.registerUnidentifiedUser();
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
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.updateUser(builder.build());
        }
        call.resolve();
    }

    @PluginMethod
    public void logout(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.logout();
        }
        call.resolve();
    }

    @PluginMethod
    public void logEvent(PluginCall call) {
        String eventName = call.getString("name");
        Map<String, Object> metaData = mapFromJSON(call.getObject("data"));

        Intercom client = _getIntercomClient();
        if (client != null) {
            if (metaData == null) {
                client.logEvent(eventName);
            } else {
                client.logEvent(eventName, metaData);
            }
        }

        call.resolve();
    }

    @PluginMethod
    public void displayMessenger(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.displayMessenger();
        }
        call.resolve();
    }

    @PluginMethod
    public void displayMessageComposer(PluginCall call) {
        String message = call.getString("message");
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.displayMessageComposer(message);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayHelpCenter(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.displayHelpCenter();
        }
        call.resolve();
    }

    @PluginMethod
    public void hideMessenger(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.hideIntercom();
        }
        call.resolve();
    }

    @PluginMethod
    public void displayLauncher(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setLauncherVisibility(Intercom.VISIBLE);
        }
        call.resolve();
    }

    @PluginMethod
    public void hideLauncher(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setLauncherVisibility(Intercom.GONE);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayInAppMessages(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setLauncherVisibility(Intercom.VISIBLE);
        }
        call.resolve();
    }

    @PluginMethod
    public void hideInAppMessages(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setLauncherVisibility(Intercom.GONE);
        }
        call.resolve();
    }

    @PluginMethod
    public void displayCarousel(PluginCall call) {
        String carouselId = call.getString("carouselId");
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.displayCarousel(carouselId);
        }
        call.resolve();
    }

    @PluginMethod
    public void setUserHash(PluginCall call) {
        String hmac = call.getString("hmac");
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setUserHash(hmac);
        }
        call.resolve();
    }

    @PluginMethod
    public void setUserJwt(PluginCall call) {
        String jwt = call.getString("jwt");
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.setUserJwt(jwt);
        }
        call.resolve();
    }

    @PluginMethod
    public void setBottomPadding(PluginCall call) {
        Intercom client = _getIntercomClient();
        if (client != null) {
            String stringValue = call.getString("value");
            int value = Integer.parseInt(stringValue);
            client.setBottomPadding(value);
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
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.displayArticle(articleId);
        }
        call.resolve();
    }

    private void setUpIntercom(String loadApiKey, String loadAppId) {
         if (this._initIntercom) {
            return;
        }

        try {
            // get config
            CapConfig config = this.bridge.getConfig();
            String apiKey = (loadApiKey != null) ? loadApiKey : config.getPluginConfiguration("Intercom").getString("androidApiKey");
            String appId = (loadAppId != null) ? loadAppId : config.getPluginConfiguration("Intercom").getString("androidAppId");

            if (apiKey == null || apiKey.isEmpty() || appId == null || appId.isEmpty()) {
                Logger.warn("Intercom", "ERROR: Missing Intercom API key or App ID");
                this._initIntercom = false;
                return;
            }

            this._initIntercom = true;

            // init intercom sdk
            Intercom.initialize(this.getActivity().getApplication(), apiKey, appId);
            setUpUnreadCountListener();
        } catch (Exception e) {
            Logger.error("Intercom", "ERROR: Something went wrong when initializing Intercom. Check your configurations", e);
        }
    }

    private void setUpUnreadCountListener() {
        Intercom client = _getIntercomClient();
        if (client != null) {
            client.addUnreadConversationCountListener(new UnreadConversationCountListener() {
                @Override
                public void onCountUpdate(int unreadCount) {
                    JSObject result = new JSObject();
                    result.put("unreadCount", unreadCount);
                    notifyListeners(EVENT_UNREAD_COUNT_CHANGED, result);
                }
            });
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

    private Intercom _getIntercomClient() {
        return this._initIntercom ? Intercom.client() : null;
    }
}
