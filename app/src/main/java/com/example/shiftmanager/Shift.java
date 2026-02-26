package com.example.shiftmanager;

public class Shift {
    private String id;
    private String title;
    private String date;
    private String time;
    private String location;
    private int approvedWorkers;
    private int pendingWorkers;
    private int maxWorkers;

    // We add a new memory switch here!
    private boolean isSignedUp = false;

    public Shift(String id, String title, String date, String time, String location, int approvedWorkers, int pendingWorkers, int maxWorkers) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.time = time;
        this.location = location;
        this.approvedWorkers = approvedWorkers;
        this.pendingWorkers = pendingWorkers;
        this.maxWorkers = maxWorkers;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getLocation() { return location; }
    public int getApprovedWorkers() { return approvedWorkers; }
    public int getPendingWorkers() { return pendingWorkers; }
    public int getMaxWorkers() { return maxWorkers; }

    // These let the adapter check and change the button state
    public boolean isSignedUp() { return isSignedUp; }
    public void setSignedUp(boolean signedUp) { isSignedUp = signedUp; }
}