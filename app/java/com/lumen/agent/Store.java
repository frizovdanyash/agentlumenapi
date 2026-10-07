package com.lumen.agent;

import android.content.Context;
import android.content.SharedPreferences;

public class Store {

    private final SharedPreferences sp;

    public Store(Context ctx) {
        sp = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE);
    }

    public String getStr(String key, String def) { return sp.getString(key, def); }
    public void setStr(String key, String value) { sp.edit().putString(key, value).apply(); }

    public boolean getBool(String key, boolean def) { return sp.getBoolean(key, def); }
    public void setBool(String key, boolean value) { sp.edit().putBoolean(key, value).apply(); }

    public int getInt(String key, int def) { return sp.getInt(key, def); }
    public void setInt(String key, int value) { sp.edit().putInt(key, value).apply(); }

    public void remove(String key) { sp.edit().remove(key).apply(); }

    public boolean has(String key) { return sp.contains(key); }

    public void clearAll() { sp.edit().clear().apply(); }
}
