package com.lumen.agent;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Termux {

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String SERVICE = "com.termux.app.RunCommandService";
    private static final String ACTION = "com.termux.RUN_COMMAND";

    public static boolean available(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static JSONObject run(Context ctx, String command, String workdir, int timeoutMs) {
        JSONObject out = new JSONObject();
        if (!available(ctx)) {
            try {
                out.put("ok", false);
                out.put("output", "Termux не установлен");
            } catch (Exception ignored) {
            }
            return out;
        }

        final String action = "com.lumen.agent.RESULT." + UUID.randomUUID();
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] holder = new String[1];

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String stdout = null;
                    String stderr = null;
                    Integer exit = null;
                    Bundle b = intent.getBundleExtra("result");
                    if (b == null) b = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE");
                    if (b != null) {
                        stdout = b.getString("stdout");
                        stderr = b.getString("stderr");
                        if (b.containsKey("exitCode")) exit = b.getInt("exitCode");
                    }
                    if (stdout == null && intent.hasExtra("stdout")) stdout = intent.getStringExtra("stdout");
                    if (stderr == null && intent.hasExtra("stderr")) stderr = intent.getStringExtra("stderr");
                    if (exit == null && intent.hasExtra("exitCode")) exit = intent.getIntExtra("exitCode", -1);

                    JSONObject o = new JSONObject();
                    if (stdout == null && stderr == null) {
                        o.put("ok", false);
                        o.put("output", "Termux принял команду, но не вернул вывод.\n"
                                + "Разреши внешние вызовы: echo allow-external-apps=true >> ~/.termux/termux.properties"
                                + " и перезапусти Termux.");
                    } else {
                        String text = (stdout == null ? "" : stdout) + (stderr == null ? "" : stderr);
                        o.put("ok", exit == null || exit == 0);
                        o.put("output", text.trim().isEmpty() ? "(команда завершилась без вывода)" : text);
                        o.put("exit", exit == null ? 0 : exit);
                    }
                    holder[0] = o.toString();
                } catch (Exception e) {
                    holder[0] = "{\"ok\":false,\"output\":\"ошибка приёма результата\"}";
                } finally {
                    latch.countDown();
                }
            }
        };

        try {
            IntentFilter filter = new IntentFilter(action);
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, filter);
            }

            Intent i = new Intent();
            i.setClassName(TERMUX_PACKAGE, SERVICE);
            i.setAction(ACTION);
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                    workdir != null && !workdir.isEmpty() ? workdir : "/data/data/com.termux/files/home");
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                    new Intent(action).setPackage(ctx.getPackageName()), flags);
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);

            try {
                ctx.startService(i);
            } catch (Exception e) {
                ctx.startForegroundService(i);
            }

            if (!latch.await(Math.max(3000, timeoutMs), TimeUnit.MILLISECONDS)) {
                JSONObject o = new JSONObject();
                o.put("ok", false);
                o.put("output", "Termux не ответил за " + (timeoutMs / 1000) + " с.\n"
                        + "Проверь в Termux: ~/.termux/termux.properties → allow-external-apps=true,"
                        + " затем перезапусти Termux.");
                holder[0] = o.toString();
            }
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put("ok", false);
                o.put("output", "не удалось запустить через Termux: " + e.getMessage());
                holder[0] = o.toString();
            } catch (Exception ignored) {
            }
        } finally {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
        try {
            return holder[0] != null ? new JSONObject(holder[0]) : new JSONObject().put("ok", false).put("output", "нет ответа");
        } catch (Exception e) {
            try {
                return new JSONObject().put("ok", false).put("output", "ошибка ответа");
            } catch (Exception e2) {
                return new JSONObject();
            }
        }
    }

    public static JSONObject runVisible(Context ctx, String command) {
        JSONObject out = new JSONObject();
        try {
            if (!available(ctx)) {
                out.put("ok", false);
                out.put("output", "Termux не установлен");
                return out;
            }
            Intent i = new Intent();
            i.setClassName(TERMUX_PACKAGE, SERVICE);
            i.setAction(ACTION);
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", 0);
            try {
                ctx.startService(i);
            } catch (Exception e) {
                ctx.startForegroundService(i);
            }
            out.put("ok", true);
            out.put("output", "команда открыта в Termux");
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("output", "ошибка: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
