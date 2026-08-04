package com.example.shiftmanager;

/**
 * Holds who is signed in and which business they are currently looking at.
 *
 * Every screen needs both -- the header prints them, the shift feed filters by the
 * business, and the create-shift form offers that business's staff roles. Passing them
 * through Intent extras from screen to screen would mean every Activity re-reading them
 * from Firestore, so they are loaded once at sign-in and kept here.
 *
 * This is deliberately plain static state rather than anything clever. The cost is that
 * Android can kill the process while the app sits in the background and bring it back
 * straight to an inner screen with these fields empty -- so every screen calls
 * requireSession() in onCreate and bounces to the login screen if it comes back false.
 * That is also exactly what should happen after a log out.
 */
public final class Session {

    private static AppUser user;
    private static Business business;

    private Session() {
    }

    public static void setUser(AppUser signedInUser) {
        user = signedInUser;
    }

    public static AppUser getUser() {
        return user;
    }

    public static void setBusiness(Business activeBusiness) {
        business = activeBusiness;
    }

    public static Business getBusiness() {
        return business;
    }

    /** True when we know who is signed in. The business may still be unset. */
    public static boolean hasUser() {
        return user != null;
    }

    /** True when we know both the person and the business they are working in. */
    public static boolean isReady() {
        return user != null && business != null;
    }

    /** Called on log out, and whenever a sign-in fails part way through. */
    public static void clear() {
        user = null;
        business = null;
    }
}
