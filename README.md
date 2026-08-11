# ShiftManager

A native Android app for coordinating work shifts in a small business. A manager creates a
business, defines the staff roles it uses, and publishes shifts for real events. Employees
find the business, join it, sign up for the role they want to work, and check in when they
arrive.

Final project for **Advanced Topics in App Innovation**, Reichman University.
Built by **Roei Lustig**.

---

## What problem it solves

Small businesses usually run their rota through WhatsApp groups and paper lists. The
manager ends up rebuilding the week's schedule out of scattered messages, and staff never
quite know which shifts are still open.

ShiftManager keeps the whole loop in one app: a manager publishes a shift, employees sign
up for a specific role on it, the manager approves who actually works it, and the employee
checks in on the day.

The deliberate difference from bigger tools:

- **Ubeya** splits managers and employees into two separate apps. ShiftManager puts every
  role in one app and decides what to show from the signed-in user's role.
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

**Location:** the shift feeds and check-in use GPS. On an emulator, grant the permission and
set a position up front so no system dialog interrupts:

```bash
adb shell pm grant com.example.shiftmanager android.permission.ACCESS_FINE_LOCATION
adb emu geo fix 34.7818 32.0853
```

---

## The three roles

| Role | How you get it | What it means |
|---|---|---|
| **Employee** | Chosen during onboarding | Joins businesses, signs up for shifts, checks in |
| **Manager** | Chosen during onboarding | Creates or joins a business, publishes shifts, approves people |
| **Admin** | Signing in as the address in `Constants.ADMIN_EMAIL` | The app's owner — sees every business and every account |

All managers of a business are equal; the one who created it is simply the first. The admin
is one specific real person rather than a role anyone can apply for, which is why it is
hard-coded and there is no "become an admin" button anywhere.

---

## The screens

| Screen | Role | What it does |
|---|---|---|
| **Login** | everyone | Google sign-in and email/password. Routes a returning user straight to where they belong. |
| **Tell us about you** | everyone | First-time onboarding: name, and whether you manage shifts or work them. |
| **Your business** | manager, employee | One list that does two jobs: with the search box empty it shows the businesses you already have a link to; as soon as you type it searches every business in the app. Joining always goes through a request. |
| **New business** | manager | Name, type, address, description, and the staff roles this business uses. |
| **Manager Dashboard** | manager | Every shift in this business, earliest first, with approved and pending counts plus the per-role breakdown. `+` publishes a new one. |
| **Shift Feed** | employee | The same shifts with a Sign up / Cancel button, the roles still open, and how far away each shift is. |
| **New / Edit shift** | manager | One form for both. Date and time pickers, location, and how many of each role are needed. |
| **Shift Detail & Approval** | both | The shift's details. A manager also sees applicants with the role each applied for and can approve or remove them, and sees their check-in time. |
| **Team** | manager | Everyone attached to the business and every request waiting on an answer, with pending ones sorted to the top. |
| **My Shifts** | employee | What this employee signed up for, its status, the Check in button, and a way into the shift. |
| **Admin** | admin | Every business expanded to its people, plus a final group for accounts in no business at all. |

---

## How the data is organised

Five Firestore collections:

```
users/{userId}
    name · email · role (admin|manager|employee) · active · createdAt
    activeBusinessId          which business they are currently looking at

businesses/{businessId}
    name · nameLower · type · address · description · createdBy · createdAt
    staffRoles: ["waiter", "cook", ...]

businessMemberships/{membershipId}
    businessId · userId · userName · userEmail · role
    status:      pending | approved | rejected
    requestedBy: joiner | manager

shifts/{shiftId}
    businessId · title · description · date · startTime · endTime · location
    latitude · longitude · status · createdBy · createdAt
    roleRequirements: { waiter: 3, cook: 1 }

shiftRegistrations/{registrationId}
    shiftId · businessId · employeeId · employeeName · role · status · note · createdAt
    checkedInAt · checkInLatitude · checkInLongitude · checkInDistanceMetres
```

**Why memberships are their own collection.** A person can work for several businesses at
once — event staff usually do — so the link cannot be a field on either side. It also has
to carry a status, because joining is a request somebody else has to answer.

**Why one collection covers both join directions.** An employee asking to join and a
manager inviting an employee are the same fact — this person and this business are linked,
and one side has not answered yet. Only *who* answers differs, which is what `requestedBy`
records. The value is called `joiner` rather than `employee` because a new manager joining
an existing business runs in the same direction.

**Why registrations are their own collection.** A sign-up is not a property of a shift or
of a person — it is a fact about the pairing of the two, plus where the manager's decision
stands. Keeping it separate is what lets one shift hold several applicants at different
stages for different roles, and it means a sign-up lives on the server rather than in
memory.

**Why staffing is per role.** A shift is not "6 people", it is "3 waiters and 1 cook", and
each fills independently — approving a fourth waiter is refused while the cook slot is
still open.

**Why `nameLower` exists.** Firestore has no case-insensitive matching, so the business
search bar queries a lower-cased copy of the name rather than the display name.

**Why dates are text.** `date` is written as `yyyy-MM-dd` and times as `HH:mm`, always
through the system pickers rather than free typing. Because every value is written the same
width, sorting them as text gives the correct chronological order, and comparing two times
is a plain string comparison.

**Counts are never stored.** A shift's approved and pending totals are counted from its
registrations each time the feed loads. Storing them on the shift as well would create two
places that could disagree about the same fact.

---

## How the code is arranged

```
com.example.shiftmanager
├── Constants.java              every collection name, field name and fixed value
├── Callback.java               how a screen hears back from an async database call
├── Session.java                who is signed in and which business they are viewing
├── SessionUi.java              the shared header, the business switcher, the guards
│
├── AppUser.java               ─┐
├── Business.java               ├─ the data, mapped from Firestore documents
├── Membership.java             │
├── Shift.java                  │
├── Registration.java          ─┘
│
├── UserRepository.java        ─┐
├── BusinessRepository.java     ├─ all database reads and writes
├── MembershipRepository.java   │
├── ShiftRepository.java       ─┘
│
├── LocationHelper.java         GPS permission, position lookup, address geocoding
│
├── LoginActivity.java         ─┐
├── ProfileSetupActivity.java   │
├── BusinessSetupActivity.java  │
├── CreateBusinessActivity.java │
├── MainActivity.java           ├─ the screens
├── EmployeeActivity.java       │
├── CreateShiftActivity.java    │
├── ShiftDetailActivity.java    │
├── StaffDirectoryActivity.java │
├── MyShiftsActivity.java       │
├── AdminActivity.java         ─┘
│
├── ShiftAdapter.java          ─┐
├── MyShiftsAdapter.java        │
├── StaffAdapter.java           ├─ RecyclerView adapters
├── BusinessAdapter.java        │
├── ApplicantAdapter.java       │
├── PeopleAdapter.java          │
├── AdminAdapter.java          ─┘
│
├── AdminRow.java              ─┐ small row types the admin and business
└── BusinessRow.java           ─┘ lists render
```

The repositories exist because the manager screen and the employee screen originally held
their own copies of the same Firestore query, which had to be edited twice and drifted
apart. Now the screens only handle what is on screen and the repositories own the database.

`Session` is deliberately plain static state rather than anything clever. The cost is that
Android can kill the process in the background and restore it straight to an inner screen
with those fields empty — so every screen calls a guard in `onCreate` and bounces to login
if it comes back false. That is also exactly what should happen after a log out.

`CreateShiftActivity` doubles as the edit form when it is given an `EXTRA_SHIFT_ID`. Editing
updates the shift rather than replacing it, and leaves registrations alone, so correcting a
detail does not throw away the people already signed up.

---

## Firebase

- **Authentication** — Google sign-in, plus email/password as a second method.
- **Firestore** — the five collections above. There is no hardcoded application data.
- **Analytics** — events for the meaningful actions: each screen opened, sign-in success and
  failure per method, shift created and edited, location captured, address geocoded, sign-up,
  cancel, approve, remove, join requested and approved, and the admin's actions.
- **Crashlytics** — breadcrumb logs on the main paths and `recordException` on every
  database failure, so a failed read or write is reported rather than swallowed.

---

## The phone capability: GPS check-in

An approved employee taps **Check in** when they start their shift. The app records the
*server's* time — not the device clock, which would be forgeable attendance evidence — and
the phone's position on their registration. The manager sees the check-in time next to their
name on the approval screen. That replaces the paper timesheet with something the manager
can actually verify.

A shift can also carry a position, which is optional. When it has one, the feeds show how
far each shift is, and a check-in also records the distance from the site — so a manager can
tell an arrival at work from one on the sofa. When a shift has no position, the distance is
stored as `-1` and simply not shown, rather than a misleading zero.

There are two ways to put a position on a shift:

- **Use my current location** reads the phone's GPS.
- **Find the address above on the map** geocodes the typed address, so a manager can set up
  a shift at a venue they are not standing in. It needs no permission at all, because it
  reads typed text rather than the phone.

Check-in was deliberately chosen over stamping a location on every shift. Most small
businesses work from one address, so per-shift coordinates say very little; when somebody
actually turned up says a lot.

Location is requested at runtime, not just declared in the manifest. If the user refuses,
nothing breaks: the distance line stays empty and every other feature carries on. The lookup
asks GPS directly (`PRIORITY_HIGH_ACCURACY`) and falls back to the last known position — an
earlier version used the balanced-power setting, which leans on wifi and mobile-network
positioning, so a request could sit unanswered indefinitely and the feature looked broken
when it was not. Geocoding runs on a background thread using `Geocoder`'s blocking method,
because the listener version only exists from API 33 and this app supports 24.

---

## Known limitations

Honest notes rather than hidden gaps:

- **Firestore security rules require a signed-in user and nothing more.** For production
  they would have to enforce that only a manager of *that* business can approve, and that
  only the owner of a registration can cancel it.
- **The manager-only staffing counts are hidden by the interface, not by the database.** An
  employee sees which roles are open but not how many are needed — that is a UI decision, so
  a determined user could still read the document. Real privacy would need security rules or
  a separate document.
- **A profile document does not imply a login exists.** Deleting a user in Firebase Auth
  does not cascade to their `users` document, so the admin screen can show accounts with no
  login behind them. It offers a delete-profile action for exactly this.
- **Password reset reports success for an address with no account.** This is Firebase's
  email-enumeration protection: it will not reveal which addresses are registered, and the
  app cannot tell the difference either.
- **Someone must sign up before a manager can add them.** The invite flow works on existing
  accounts; there is no invitation for a person who has never opened the app.
- **Feeds re-read when a screen is returned to**, rather than streaming live updates.
  Firestore supports realtime listeners; a one-time read is simpler to follow and enough at
  this scale.
