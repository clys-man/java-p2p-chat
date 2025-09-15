package edu.unifor.clysman.chat.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Json {
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    public static Gson get() {
        return gson;
    }
}