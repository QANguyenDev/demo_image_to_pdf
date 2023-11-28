package com.example.imagestofpd;

import android.app.Application;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Locale;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static String formatTimestamp(long timesamp) {

        Calendar calendar = Calendar.getInstance(Locale.ENGLISH);
        calendar.setTimeInMillis(timesamp);

        String date = DateFormat.format("dd/MM/yyyy", calendar).toString();

        return date;

    }
}
