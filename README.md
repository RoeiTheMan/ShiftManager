# ShiftManager

A native Android app for coordinating work shifts in a small business. Managers publish
shifts; employees browse them, sign up, and see whether they were approved.

Final project for **Advanced Topics in App Innovation**, Reichman University.
Built by **Roei Lustig**.

---

## What problem it solves

Small businesses usually run their rota through WhatsApp groups and paper lists. The
manager ends up rebuilding the week's schedule out of scattered messages, and staff never
quite know which shifts are still open.

ShiftManager keeps the whole loop in one app: a manager publishes a shift, employees sign
up for it, and the manager approves who actually works it.

The deliberate difference from bigger tools:

- **Ubeya** splits managers and employees into two separate apps. ShiftManager puts both
  roles in one app and decides what to show from the signed-in user's role.
- **When I Work / Homebase** bundle payroll, labour costing and hiring. ShiftManager stays
  a scheduling tool on purpose.

---

## Running it

```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

Install the result on a running emulator or device:

```bash
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
```

The `-t` flag is required: the debug APK is marked test-only, and `adb` refuses it without it.

**Firebase:** the app talks to the Firebase project `shiftmanager-126058d2`. The
`google-services.json` in `app/` is already configured for Analytics, Crashlytics,
Firestore, and both sign-in methods. Google sign-in additionally needs the debug
certificate's SHA-1 registered in the Firebase Console; the one for this machine is
already registered.

---

## The five screens

| Screen | Role | What it does |
|---|---|---|
| **Login** | both | Google sign-in and email/password. On a first sign-in it asks whether you are a manager or an employee, then routes you accordingly for good. |
| **Manager Dashboard** | manager | Every shift, earliest date first, each showing how many workers are approved and how many are still waiting. `+` publishes a new one. |
| **Shift Feed** | employee | The same shifts, with a Sign up / Cancel button and how far away each one is. |
| **Shift Detail & Approval** | both | The shift's full details. A manager also sees everyone who applied and can approve or remove them; an employee sees the details only. |
| **Staff Directory** | manager | The business's employee roster, with add and remove. |
| **My Shifts** | employee | The shifts this employee signed up for and whether each was approved yet. |

---

## How the data is organised

Three Firestore collections:

```
users/{userId}
    name · email · role · active · createdAt

shifts/{shiftId}
    title · description · date · startTime · endTime · location
    latitude · longitude · maxWorkers · status · createdBy · createdAt

shiftRegistrations/{registrationId}
    shiftId · employeeId · employeeName · status · note · createdAt
    checkedInAt · checkInLatitude · checkInLongitude · checkInDistanceMetres
```

**Why registrations are their own collection.** A sign-up is not a property of a shift or
of a person — it is a fact about the pairing of the two, plus where the manager's decision
stands. Keeping it separate is what lets one shift hold several applicants at different
stages, and it means a sign-up is stored on the server rather than held in memory, so it
survives closing the app.

**Why dates are text.** `date` is written as `yyyy-MM-dd` and times as `HH:mm`, always
through the system pickers rather than free typing. Because every value is written the same
way, sorting them as text gives the correct chronological order, and comparing two times is
a plain string comparison.

**Counts are never stored.** A shift's approved and pending totals are counted from the
registrations each time the feed loads. Storing them on the shift as well would create two
places that could disagree about the same fact.

---

## How the code is arranged

```
com.example.shiftmanager
├── Constants.java             every collection name, field name and fixed value
├── Callback.java              how a screen hears back from an async database call
│
├── Shift.java                 ─┐
├── Registration.java           ├─ the data, mapped from Firestore documents
├── AppUser.java               ─┘
│
├── ShiftRepository.java       ─┐ all database reads and writes
├── UserRepository.java        ─┘
│
├── LocationHelper.java         GPS permission and position lookup
│
├── LoginActivity.java         ─┐
├── MainActivity.java           │
├── EmployeeActivity.java       ├─ the screens
├── CreateShiftActivity.java    │
├── ShiftDetailActivity.java    │
├── StaffDirectoryActivity.java │
├── MyShiftsActivity.java      ─┘
│
├── ShiftAdapter.java          ─┐
├── ApplicantAdapter.java       ├─ RecyclerView adapters
├── StaffAdapter.java           │
└── MyShiftsAdapter.java       ─┘
```

The repositories exist because the manager screen and the employee screen originally held
their own copies of the same Firestore query, which had to be edited twice and drifted
apart. Now the screens only handle what is on screen and the repositories own the database.

---

## Firebase

- **Authentication** — Google sign-in, plus email/password as a second method.
- **Firestore** — the three collections above. There is no hardcoded application data.
- **Analytics** — events for the meaningful actions: screens opened, shift created,
  sign-up, cancel, approve, remove, employee added or removed, location captured.
- **Crashlytics** — breadcrumb logs on the main paths, and `recordException` on every
  database failure, so a failed read or write is reported rather than swallowed.

---

## The phone capability: GPS check-in

An approved employee taps **Check in** when they start their shift. The app records the
server's time and the phone's position on their registration, and the manager sees the
check-in time next to their name on the approval screen. That replaces the paper timesheet
with something the manager can actually verify.

Publishing a shift can also stamp it with a position (**Use my current location**), which
is optional. When a shift has one, two extra things happen: the feeds show how far each
shift is from the user, and a check-in records the distance from the site — so a manager
can tell an arrival at work from one on the sofa. When a shift has no position, the
distance is stored as `-1` and simply not shown, rather than a misleading zero.

Check-in was deliberately chosen over stamping a location on every shift. Most small
businesses work from one address, so per-shift coordinates say very little; when somebody
actually turned up says a lot.

Location is requested at runtime, not just declared in the manifest. If the user refuses,
nothing breaks: the distance line stays empty and every other feature carries on.

The lookup asks GPS directly (`PRIORITY_HIGH_ACCURACY`) and falls back to the last known
position. An earlier version used the balanced-power setting, which leans on wifi and
mobile-network positioning — a request could sit unanswered indefinitely, which made the
feature look broken when it was not.

---

## Known limitations

Honest notes rather than hidden gaps:

- The app assumes **one business**. There is no `businesses` collection, because the design
  describes a single business and multi-tenancy would add complexity the project does not need.
- An employee added by hand in the Staff Directory has **no login account** until they sign
  in themselves. The directory is the business's roster, not a list of accounts.
- **Firestore security rules are in test mode.** For a production app they would need to be
  written so that only managers can approve and only the owner can cancel a registration.
- Feeds reload when a screen is returned to, rather than streaming live updates. Firestore
  supports realtime listeners; a one-time read is simpler to follow and enough at this scale.
