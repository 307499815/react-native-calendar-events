package com.calendarevents;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import android.text.TextUtils;
import android.util.Log;

import org.w3c.dom.Text;

public class RNCalendarEventsSlim extends ReactContextBaseJavaModule implements PermissionListener {

    private static int PERMISSION_REQUEST_CODE = 37;
    private final ReactContext reactContext;
    private static final String RNC_PREFS = "REACT_NATIVE_CALENDAR_PREFERENCES";
    private static final HashMap<Integer, Promise> permissionsPromises = new HashMap<>();

    public RNCalendarEventsSlim(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNCalendarEvents";
    }

    //region Calendar Permissions
    private void requestCalendarPermission(boolean readOnly, final Promise promise)
    {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            promise.reject("E_ACTIVITY_NOT_PERMISSION_AWARE", "Activity does not implement the PermissionAwareActivity interface");
            return;
        }
        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;
        PERMISSION_REQUEST_CODE++;
        permissionsPromises.put(PERMISSION_REQUEST_CODE, promise);
        String[] permissions = new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR};
        if (readOnly == true) {
            permissions = new String[]{Manifest.permission.READ_CALENDAR};
        }
        activity.requestPermissions(permissions, PERMISSION_REQUEST_CODE, this);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (permissionsPromises.containsKey(requestCode)) {

            // If request is cancelled, the result arrays are empty.
            Promise permissionsPromise = permissionsPromises.get(requestCode);

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionsPromise.resolve("authorized");
            } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                permissionsPromise.resolve("denied");
            } else if (permissionsPromises.size() == 1) {
                permissionsPromise.reject("permissions - unknown error", grantResults.length > 0 ? String.valueOf(grantResults[0]) : "Request was cancelled");
            }
            permissionsPromises.remove(requestCode);
        }

        return permissionsPromises.size() == 0;
    }

    private boolean haveCalendarPermissions(boolean readOnly) {
        int writePermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR);
        int readPermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALENDAR);

        if (readOnly) {
            return readPermission == PackageManager.PERMISSION_GRANTED;
        }

        return writePermission == PackageManager.PERMISSION_GRANTED &&
                readPermission == PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowRequestPermissionRationale(boolean readOnly) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            Log.w(this.getName(), "Activity doesn't exist");
            return false;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            Log.w(this.getName(), "Activity does not implement the PermissionAwareActivity interface");
            return false;
        }

        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;

        if (readOnly) {
            return activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR);
        }
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR);
    }

    //endregion



    private Integer calAccessConstantMatchingString(String string) {
        if (string.equals("contributor")) {
            return CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        }
        if (string.equals("editor")) {
            return CalendarContract.Calendars.CAL_ACCESS_EDITOR;
        }
        if (string.equals("freebusy")) {
            return CalendarContract.Calendars.CAL_ACCESS_FREEBUSY;
        }
        if (string.equals("override")) {
            return CalendarContract.Calendars.CAL_ACCESS_OVERRIDE;
        }
        if (string.equals("owner")) {
            return CalendarContract.Calendars.CAL_ACCESS_OWNER;
        }
        if (string.equals("read")) {
            return CalendarContract.Calendars.CAL_ACCESS_READ;
        }
        if (string.equals("respond")) {
            return CalendarContract.Calendars.CAL_ACCESS_RESPOND;
        }
        if (string.equals("root")) {
            return CalendarContract.Calendars.CAL_ACCESS_ROOT;
        }
        return CalendarContract.Calendars.CAL_ACCESS_NONE;
    }

    private int addCalendar(ReadableMap details) throws Exception, SecurityException {

        ContentResolver cr = reactContext.getContentResolver();
        ContentValues calendarValues = new ContentValues();

        // required fields for new calendars
        if (!details.hasKey("source")) {
            throw new Exception("new calendars require `source` object");
        }
        if (!details.hasKey("name")) {
            throw new Exception("new calendars require `name`");
        }
        if (!details.hasKey("title")) {
            throw new Exception("new calendars require `title`");
        }
        if (!details.hasKey("color")) {
            throw new Exception("new calendars require `color`");
        }
        if (!details.hasKey("accessLevel")) {
            throw new Exception("new calendars require `accessLevel`");
        }
        if (!details.hasKey("ownerAccount")) {
            throw new Exception("new calendars require `ownerAccount`");
        }

        ReadableMap source = details.getMap("source");

        if (!source.hasKey("name")) {
            throw new Exception("new calendars require a `source` object with a `name`");
        }

        Boolean isLocalAccount = false;
        if (source.hasKey("isLocalAccount")) {
            isLocalAccount = source.getBoolean("isLocalAccount");
        }

        if (!source.hasKey("type") && isLocalAccount == false) {
            throw new Exception("new calendars require a `source` object with a `type`, or `isLocalAccount`: true");
        }

        calendarValues.put(CalendarContract.Calendars.ACCOUNT_NAME, source.getString("name"));
        calendarValues.put(CalendarContract.Calendars.ACCOUNT_TYPE, isLocalAccount ? CalendarContract.ACCOUNT_TYPE_LOCAL : source.getString("type"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_COLOR, details.getInt("color"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, calAccessConstantMatchingString(details.getString("accessLevel")));
        calendarValues.put(CalendarContract.Calendars.OWNER_ACCOUNT, details.getString("ownerAccount"));
        calendarValues.put(CalendarContract.Calendars.NAME, details.getString("name"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, details.getString("title"));
        // end required fields

        Uri.Builder uriBuilder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
        uriBuilder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, source.getString("name"));
        uriBuilder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, isLocalAccount ? CalendarContract.ACCOUNT_TYPE_LOCAL : source.getString("type"));

        Uri calendarsUri = uriBuilder.build();

        Uri calendarUri = cr.insert(calendarsUri, calendarValues);
        return Integer.parseInt(calendarUri.getLastPathSegment());
    }

    private boolean removeCalendar(String calendarID) {
        int rows = 0;

        try {
            ContentResolver cr = reactContext.getContentResolver();

            Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, (long) Integer.parseInt(calendarID));
            rows = cr.delete(uri, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return rows > 0;
    }

    private int removeCalendarByName(String calendarName) {
        int rows = 0;

        try {
            ContentResolver cr = reactContext.getContentResolver();

            Uri uri = CalendarContract.Calendars.CONTENT_URI;
            String query = "(" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?" +
                    " AND "+ CalendarContract.Calendars.ACCOUNT_TYPE + " = ? )";
            String[] args = new String[]{calendarName,CalendarContract.ACCOUNT_TYPE_LOCAL};
            rows = cr.delete(uri, query, args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rows;
    }

    private String getCalendarId(String calendarName) {
        if(TextUtils.isEmpty(calendarName)) return null;

        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        String query = "(" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?" +
                " AND "+ CalendarContract.Calendars.ACCOUNT_TYPE + " = ? )";
        String[] args = new String[]{calendarName,CalendarContract.ACCOUNT_TYPE_LOCAL};

        Cursor cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
        }, query, args, null);

        if (cursor != null && cursor.moveToFirst()) {
            String id = cursor.getString(0);
            cursor.close();
            return id;
        } else {
            return null;
        }
    }

    private int bulkAddEvents(ReadableArray details) throws Exception {
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = CalendarContract.Events.CONTENT_URI;
        List<ContentValues> valuesList = new ArrayList<>();
        for(int i = 0; i < details.size(); i++) {
            ReadableMap info = details.getMap(i);
            ContentValues values = getEventValues(info);
            valuesList.add(values);
        }

        int count = 0;
        if(valuesList.size() > 0) {
            count = cr.bulkInsert(uri,valuesList.toArray(new ContentValues[0]));
        }
        return count;
    }

    private int addEvent(ReadableMap detail) throws Exception {
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = CalendarContract.Events.CONTENT_URI;
        ContentValues values = getEventValues(detail);
        if(values == null) return 0;
        Uri calendarUri = cr.insert(uri, values);
        return Integer.parseInt(calendarUri.getLastPathSegment());
    }

    private int removeEvents(ReadableMap detail) throws Exception {
        String title = detail.getString("title");
        String location = detail.getString("location");
        String calendarId = detail.getString("calendarId");
        int eventId = 0;
        if(detail.hasKey("eventId")) {
            eventId = detail.getInt("eventId");
        }

        ContentResolver cr = reactContext.getContentResolver();
        if(eventId > 0) {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            return cr.delete(uri, null, null);
        }

        if(TextUtils.isEmpty(title) && TextUtils.isEmpty(location) && TextUtils.isEmpty(calendarId)) {
            return 0;
        }

        Uri uri = CalendarContract.Events.CONTENT_URI;
        List<String> params = new ArrayList<>();
        String selection = "((" + CalendarContract.Events.DELETED + " != 1) ";
        if(!TextUtils.isEmpty(title)) {
            selection = selection + " and ("+CalendarContract.Events.TITLE+" = ? ) ";
            params.add(title);
        }

        if(!TextUtils.isEmpty(location)) {
            selection = selection + " and ("+CalendarContract.Events.EVENT_LOCATION+" = ? ) ";
            params.add(location);
        }

        if(!TextUtils.isEmpty(calendarId)) {
            selection = selection + " and ("+CalendarContract.Events.CALENDAR_ID+" = " + calendarId + " ) ";
        }
        selection = selection + ")";
        int count = cr.delete(uri, selection, params.toArray(new String[0]));
        return count;
    }

    private int updateEvent(ReadableMap detail) throws Exception {
        String title = detail.getString("title");
        String location = detail.getString("location");
        String calendarId = detail.getString("calendarId");
        int eventId = 0;
        if(detail.hasKey("eventId")) {
            eventId = detail.getInt("eventId");
        }

        ContentResolver cr = reactContext.getContentResolver();

        if(eventId > 0) {
            Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            return cr.update(updateUri, getEventValues(detail), null, null);
        }

        if(TextUtils.isEmpty(title) && TextUtils.isEmpty(location) && TextUtils.isEmpty(calendarId)) {
            return 0;
        }

        List<String> params = new ArrayList<>();
        String selection = "((" + CalendarContract.Events.DELETED + " != 1) ";
        if(!TextUtils.isEmpty(title)) {
            selection = selection + " and ("+CalendarContract.Events.TITLE+" = ? ) ";
            params.add(title);
        }

        if(!TextUtils.isEmpty(location)) {
            selection = selection + " and ("+CalendarContract.Events.EVENT_LOCATION+" = ? ) ";
            params.add(location);
        }

        if(!TextUtils.isEmpty(calendarId)) {
            selection = selection + " and ("+CalendarContract.Events.CALENDAR_ID+" = " + calendarId + " ) ";
        }
        selection = selection + ")";

        Uri uri = CalendarContract.Events.CONTENT_URI;
        return cr.update(uri, getEventValues(detail),selection, params.toArray(new String[0]));
    }

    private ContentValues getEventValues(ReadableMap details) throws ParseException {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        boolean skipTimezone = false;
        if(details.hasKey("skipAndroidTimezone") && details.getBoolean("skipAndroidTimezone")){
            skipTimezone = true;
        }
        if(!skipTimezone){
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        ContentValues eventValues = new ContentValues();
        if(details.hasKey("title")) {
            eventValues.put(CalendarContract.Events.TITLE, details.getString("title"));
        }

        if (details.hasKey("description")) {
            eventValues.put(CalendarContract.Events.DESCRIPTION, details.getString("description"));
        }

        if (details.hasKey("location")) {
            eventValues.put(CalendarContract.Events.EVENT_LOCATION, details.getString("location"));
        }

        if (details.hasKey("startDate")) {
            Calendar startCal = Calendar.getInstance();
            ReadableType type = details.getType("startDate");

            try {
                if (type == ReadableType.String) {
                    startCal.setTime(sdf.parse(details.getString("startDate")));
                    eventValues.put(CalendarContract.Events.DTSTART, startCal.getTimeInMillis());
                } else if (type == ReadableType.Number) {
                    eventValues.put(CalendarContract.Events.DTSTART, (long)details.getDouble("startDate"));
                }
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
        }

        if (details.hasKey("endDate")) {
            Calendar endCal = Calendar.getInstance();
            ReadableType type = details.getType("endDate");

            try {
                if (type == ReadableType.String) {
                    endCal.setTime(sdf.parse(details.getString("endDate")));
                    eventValues.put(CalendarContract.Events.DTEND, endCal.getTimeInMillis());
                } else if (type == ReadableType.Number) {
                    eventValues.put(CalendarContract.Events.DTEND, (long)details.getDouble("endDate"));
                }
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
        }

        if (details.hasKey("recurrence")) {
            String rule = createRecurrenceRule(details.getString("recurrence"), null, null, null, null, null, null, null);
            if (rule != null) {
                eventValues.put(CalendarContract.Events.RRULE, rule);
            }
        }

        if (details.hasKey("recurrenceRule")) {
            ReadableMap recurrenceRule = details.getMap("recurrenceRule");

            if (recurrenceRule.hasKey("frequency")) {
                String frequency = recurrenceRule.getString("frequency");
                String duration = "PT1H";
                Integer interval = null;
                Integer occurrence = null;
                String endDate = null;
                ReadableArray daysOfWeek = null;
                ReadableArray daysOfMonth = null;
                String weekStart = null;
                Integer weekPositionInMonth = null;

                if (recurrenceRule.hasKey("interval")) {
                    interval = recurrenceRule.getInt("interval");
                }

                if (recurrenceRule.hasKey("duration")) {
                    duration = recurrenceRule.getString("duration");
                }

                if (recurrenceRule.hasKey("occurrence")) {
                    occurrence = recurrenceRule.getInt("occurrence");
                }

                if (recurrenceRule.hasKey("endDate")) {
                    ReadableType type = recurrenceRule.getType("endDate");
                    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

                    if (type == ReadableType.String) {
                        endDate = format.format(sdf.parse(recurrenceRule.getString("endDate")));
                    } else if (type == ReadableType.Number) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTimeInMillis((long)recurrenceRule.getDouble("endDate"));
                        endDate = format.format(calendar.getTime());
                    }
                }

                if (recurrenceRule.hasKey("daysOfWeek")) {
                    daysOfWeek = recurrenceRule.getArray("daysOfWeek");
                }

                if (recurrenceRule.hasKey("weekStart")) {
                    weekStart = recurrenceRule.getString("weekStart");
                }

                if (recurrenceRule.hasKey("weekPositionInMonth")) {
                    weekPositionInMonth = recurrenceRule.getInt("weekPositionInMonth");
                }

                if (recurrenceRule.hasKey("daysOfMonth")) {
                    daysOfMonth = recurrenceRule.getArray("daysOfMonth");
                }

                String rule = createRecurrenceRule(frequency, interval, endDate, occurrence, daysOfWeek, weekStart, weekPositionInMonth, daysOfMonth);
                if (duration != null) {
                    eventValues.put(CalendarContract.Events.DURATION, duration);
                }
                if (rule != null) {
                    eventValues.put(CalendarContract.Events.RRULE, rule);
                }
            }
        }

        if (details.hasKey("allDay")) {
            eventValues.put(CalendarContract.Events.ALL_DAY, details.getBoolean("allDay") ? 1 : 0);
        }

        if (details.hasKey("timeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, details.getString("timeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("endTimeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, details.getString("endTimeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("alarms")) {
            eventValues.put(CalendarContract.Events.HAS_ALARM, true);
        }

        if (details.hasKey("availability")) {
            eventValues.put(CalendarContract.Events.AVAILABILITY, availabilityConstantMatchingString(details.getString("availability")));
        }

        if (details.hasKey("calendarId")) {
            eventValues.put(CalendarContract.Events.CALENDAR_ID, Integer.parseInt(details.getString("calendarId")));
        } else {
            eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
        }

        return eventValues;
    }

    //region Availability
    private WritableNativeArray calendarAllowedAvailabilitiesFromDBString(String dbString) {
        WritableNativeArray availabilitiesStrings = new WritableNativeArray();
        for(String availabilityStr: dbString.split(",")) {
            int availabilityId = -1;

            try {
                availabilityId = Integer.parseInt(availabilityStr);
            } catch(NumberFormatException e) {
                // Some devices seem to just use strings.
                if (availabilityStr.equals("AVAILABILITY_BUSY")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_BUSY;
                } else if (availabilityStr.equals("AVAILABILITY_FREE")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_FREE;
                } else if (availabilityStr.equals("AVAILABILITY_TENTATIVE")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_TENTATIVE;
                }
            }

            switch(availabilityId) {
                case CalendarContract.Events.AVAILABILITY_BUSY:
                    availabilitiesStrings.pushString("busy");
                    break;
                case CalendarContract.Events.AVAILABILITY_FREE:
                    availabilitiesStrings.pushString("free");
                    break;
                case CalendarContract.Events.AVAILABILITY_TENTATIVE:
                    availabilitiesStrings.pushString("tentative");
                    break;
            }
        }

        return availabilitiesStrings;
    }

    private String availabilityStringMatchingConstant(Integer constant)
    {
        switch(constant) {
            case CalendarContract.Events.AVAILABILITY_BUSY:
            default:
                return "busy";
            case CalendarContract.Events.AVAILABILITY_FREE:
                return "free";
            case CalendarContract.Events.AVAILABILITY_TENTATIVE:
                return "tentative";
        }
    }

    private Integer availabilityConstantMatchingString(String string) throws IllegalArgumentException {
        if (string.equals("free")){
            return CalendarContract.Events.AVAILABILITY_FREE;
        }

        if (string.equals("tentative")){
            return CalendarContract.Events.AVAILABILITY_TENTATIVE;
        }

        return CalendarContract.Events.AVAILABILITY_BUSY;
    }
    //endregion

    private String ReadableArrayToString (ReadableArray strArr) {
        ArrayList<Object> array = strArr.toArrayList();
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            strBuilder.append(array.get(i).toString() + ',');
        }
        String newString = strBuilder.toString();
        newString = newString.substring(0, newString.length() - 1);
        return newString;
    }

    //region Recurrence Rule
    private String createRecurrenceRule(String recurrence, Integer interval, String endDate, Integer occurrence, ReadableArray daysOfWeek, String weekStart, Integer weekPositionInMonth, ReadableArray daysOfMonth) {
        String rrule;

        if (recurrence.equals("daily")) {
            rrule=  "FREQ=DAILY";
        } else if (recurrence.equals("weekly")) {
            rrule = "FREQ=WEEKLY";
        }  else if (recurrence.equals("monthly")) {
            rrule = "FREQ=MONTHLY";
        } else if (recurrence.equals("yearly")) {
            rrule = "FREQ=YEARLY";
        } else {
            return null;
        }

        if (daysOfWeek != null && recurrence.equals("weekly")) {
            rrule += ";BYDAY=" + ReadableArrayToString(daysOfWeek);
        }

        if (recurrence.equals("monthly") && daysOfWeek != null && weekPositionInMonth != null) {
            rrule += ";BYSETPOS=" + weekPositionInMonth;
            rrule += ";BYDAY=" + ReadableArrayToString(daysOfWeek);
        } else if(recurrence.equals("monthly") && daysOfMonth != null) {
            rrule += ";BYMonthDAY=" + ReadableArrayToString(daysOfMonth);
        }

        if (weekStart != null) {
            rrule += ";WKST=" + weekStart;
        }

        if (interval != null) {
            rrule += ";INTERVAL=" + interval;
        }

        if (endDate != null) {
            rrule += ";UNTIL=" + endDate;
        } else if (occurrence != null) {
            rrule += ";COUNT=" + occurrence;
        }

        return rrule;
    }
    //endregion


    private String getPermissionKey(boolean readOnly) {
        String permissionKey = "permissionRequested"; // default to previous key for read/write, backwards-compatible
        if (readOnly) {
            permissionKey = "permissionRequestedRead"; // new key for read-only permission requests
        }
        return permissionKey;
    }

    //region React Native Methods
    @ReactMethod
    public void checkPermissions(boolean readOnly, Promise promise) {
        SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
        boolean permissionRequested = sharedPreferences.getBoolean(getPermissionKey(readOnly), false);

        if (this.haveCalendarPermissions(readOnly)) {
            promise.resolve("authorized");
        } else if (!permissionRequested) {
            promise.resolve("undetermined");
        } else if(this.shouldShowRequestPermissionRationale(readOnly)) {
            promise.resolve("denied");
        } else {
            promise.resolve("restricted");
        }
    }

    @ReactMethod
    public void requestPermissions(boolean readOnly, Promise promise) {
        SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getPermissionKey(readOnly), true);
        editor.apply();

        if (this.haveCalendarPermissions(readOnly)) {
            promise.resolve("authorized");
        } else {
            this.requestCalendarPermission(readOnly, promise);
        }
    }

    @ReactMethod
    public void saveCalendar(final ReadableMap options, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        Integer calendarID = addCalendar(options);
                        promise.resolve(calendarID.toString());
                    } catch (Exception e) {
                        promise.reject("save calendar error", e.getMessage());
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            promise.reject("save calendar error", "Calendar could not be saved", e);
        }
    }


    @ReactMethod
    public void findCalendarId(final String name, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        String calendarId = getCalendarId(name);
                        promise.resolve(calendarId);
                    } catch (Exception e) {
                        promise.reject("find calendar error", e.getMessage());
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            promise.reject("find calendar error", e.getMessage());
        }
    }


    @ReactMethod
    public void removeCalendar(final String CalendarID, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        boolean successful = removeCalendar(CalendarID);
                        promise.resolve(successful);
                    } catch (Exception e) {
                        promise.reject("error removing calendar", e.getMessage());
                    }
                }
            });
            thread.start();

        } catch (Exception e) {
            promise.reject("error removing calendar", e.getMessage());
        }
    }

    @ReactMethod
    public void removeCalendarByName(final String CalendarName, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        int count = removeCalendarByName(CalendarName);
                        promise.resolve(count);
                    } catch (Exception e) {
                        promise.reject("error removing calendar byName", e.getMessage());
                    }
                }
            });
            thread.start();

        } catch (Exception e) {
            promise.reject("error removing calendar", e.getMessage());
        }
    }
    @ReactMethod
    public void saveEvents(final ReadableArray details, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        int count = bulkAddEvents(details);
                        promise.resolve(count);
                    } catch (Exception e) {
                        promise.reject("saveEvents error", e.getMessage());
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            promise.reject("saveEvents error", e.getMessage());
        }
    }

    @ReactMethod
    public void saveEvent(final ReadableMap detail, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        int eventId = addEvent(detail);
                        promise.resolve(eventId);
                    } catch (Exception e) {
                        promise.reject("saveEvent error", e.getMessage());
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            promise.reject("saveEvent error", e.getMessage());
        }
    }

    @ReactMethod
    public void removeEvents(final ReadableMap detail, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }

        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        int count = removeEvents(detail);
                        promise.resolve(count);
                    } catch(Exception e) {
                        promise.reject("error removing event", e.getMessage());
                    }
                }
            });
            thread.start();

        } catch (Exception e) {
            promise.reject("error removing event", e.getMessage());
        }
    }

    @ReactMethod
    public void updateEvent(final ReadableMap detail, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("denied", new Exception("no permission"));
            return;
        }

        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        int count = updateEvent(detail);
                        promise.resolve(count);
                    } catch(Exception e) {
                        promise.reject("error removing event", e.getMessage());
                    }
                }
            });
            thread.start();

        } catch (Exception e) {
            promise.reject("error removing event", e.getMessage());
        }
    }

    @ReactMethod
    public void uriForCalendar(Promise promise) {
        promise.resolve(CalendarContract.Events.CONTENT_URI.toString());
    }
    //endregion
}
