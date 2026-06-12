package com.piotrekwitkowski.libraryhce;

public class PaymentAuthState {
    private static boolean isAuthorized = false;
    private static String authorizedCardId = null;
    private static PaymentInteractionListener listener = null;

    public interface PaymentInteractionListener {
        void onApduInteraction();
    }

    public static synchronized void setAuthorized(boolean auth, String cardId) {
        isAuthorized = auth;
        authorizedCardId = cardId;
    }

    public static synchronized boolean isAuthorized() {
        return isAuthorized;
    }

    public static synchronized String getAuthorizedCardId() {
        return authorizedCardId;
    }

    public static synchronized void registerListener(PaymentInteractionListener newListener) {
        listener = newListener;
    }

    public static synchronized void unregisterListener() {
        listener = null;
    }

    public static synchronized void notifyInteraction() {
        if (listener != null) {
            listener.onApduInteraction();
        }
    }
}
