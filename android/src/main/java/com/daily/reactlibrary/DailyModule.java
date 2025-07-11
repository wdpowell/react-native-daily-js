package com.daily.reactlibrary;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import co.daily.client.DailyClient;
import co.daily.client.Participant;
import co.daily.client.CallState;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

public class DailyModule extends ReactContextBaseJavaModule {
    private final DailyClient dailyClient;

    public DailyModule(ReactApplicationContext reactContext) {
        super(reactContext);
        dailyClient = new DailyClient(reactContext);

        dailyClient.addListener(new DailyClient.Listener() {
            @Override
            public void onCallStateUpdated(CallState state) {
                WritableMap params = Arguments.createMap();
                params.putString("callState", state.toString());
                sendEvent("CallStateUpdated", params);
            }

            @Override
            public void onParticipantJoined(Participant p) {
                WritableMap params = Arguments.createMap();
                params.putMap("participant", Arguments.makeNativeMap(p.toMap()));
                sendEvent("ParticipantJoined", params);
            }

            @Override
            public void onParticipantUpdated(Participant p) {
                WritableMap params = Arguments.createMap();
                params.putMap("participant", Arguments.makeNativeMap(p.toMap()));
                sendEvent("ParticipantUpdated", params);
            }

            @Override
            public void onParticipantLeft(Participant p) {
                WritableMap params = Arguments.createMap();
                params.putMap("participant", Arguments.makeNativeMap(p.toMap()));
                sendEvent("ParticipantLeft", params);
            }
        });
    }

    private void sendEvent(String eventName, WritableMap params) {
        ReactApplicationContext context = getReactApplicationContext();
        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @Override
    public String getName() {
        return "DailyNativeModule";
    }

    @ReactMethod
    public void join(String url, Promise promise) {
        dailyClient.join(url, new DailyClient.Callback() {
            @Override
            public void onSuccess() {
                promise.resolve(true);
            }

            @Override
            public void onError(Exception e) {
                promise.reject("JOIN_FAILED", e.getMessage());
            }
        });
    }

    @ReactMethod
    public void leave(Promise promise) {
        dailyClient.leave(new DailyClient.Callback() {
            @Override
            public void onSuccess() {
                promise.resolve(true);
            }

            @Override
            public void onError(Exception e) {
                promise.reject("LEAVE_FAILED", e.getMessage());
            }
        });
    }

    @ReactMethod
    public void participants(Promise promise) {
        try {
            java.util.Map<String, Object> participants = dailyClient.participants();
            promise.resolve(com.facebook.react.bridge.Arguments.makeNativeMap(participants));
        } catch (Exception e) {
            promise.reject("PARTICIPANTS_FAILED", e.getMessage());
        }
    }

    @ReactMethod
    public void setInputsEnabled(boolean mic, boolean cam, Promise promise) {
        try {
            dailyClient.setInputsEnabled(mic, cam);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("SET_INPUTS_FAILED", e.getMessage());
        }
    }
}
