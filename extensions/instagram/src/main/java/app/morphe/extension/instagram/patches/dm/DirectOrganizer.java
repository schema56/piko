/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;

/**
 * Chat categories ("folders") for the DM inbox.
 *
 * The long-press sheet patch calls {@link #showCategorizeDialog} with the pressed
 * thread key; the inbox reorder patch reads {@link #categoryOf} and the folder
 * list to group the thread list.
 */
@SuppressWarnings("unused")
public final class DirectOrganizer {
    private static final String PREFS_FILE = "piko_dm";
    private static final String KEY_CATEGORIES = "piko_chat_categories";
    private static final String KEY_ASSIGNMENTS = "piko_chat_category_assignments";
    private static final String KEY_ACTIVE_FOLDER = "piko_chat_active_folder";
    private static final char SEPARATOR = '\u0001';

    private DirectOrganizer() {
    }

    private static SharedPreferences prefs() {
        return app.morphe.extension.shared.Utils.getContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    private static List<String> readLines(String key) {
        String raw = prefs().getString(key, "");
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return lines;
        }
        for (String line : raw.split("\n")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void writeLines(String key, Set<String> lines) {
        prefs().edit().putString(key, String.join("\n", lines)).apply();
    }

    public static Set<String> getCategories() {
        return new LinkedHashSet<>(readLines(KEY_CATEGORIES));
    }

    public static void createCategory(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Set<String> categories = getCategories();
        if (categories.add(trimmed)) {
            writeLines(KEY_CATEGORIES, categories);
        }
    }

    public static void assignThread(String threadKey, String category) {
        Set<String> rows = new LinkedHashSet<>(readLines(KEY_ASSIGNMENTS));
        rows.removeIf(row -> row.endsWith(SEPARATOR + threadKey));
        rows.add(category + SEPARATOR + threadKey);
        writeLines(KEY_ASSIGNMENTS, rows);
    }

    /** Removes a thread's category assignment so it returns to the main inbox list. */
    public static void unassignThread(String threadKey) {
        Set<String> rows = new LinkedHashSet<>(readLines(KEY_ASSIGNMENTS));
        if (rows.removeIf(row -> row.endsWith(SEPARATOR + threadKey))) {
            writeLines(KEY_ASSIGNMENTS, rows);
        }
    }

    /** Category of a thread, or null when uncategorized. */
    public static String categoryOf(String threadKey) {
        for (String row : readLines(KEY_ASSIGNMENTS)) {
            int split = row.indexOf(SEPARATOR);
            if (split > 0 && row.regionMatches(split + 1, threadKey, 0, threadKey.length())
                    && row.length() == split + 1 + threadKey.length()) {
                return row.substring(0, split);
            }
        }
        return null;
    }

    public static Set<String> threadsOf(String category) {
        Set<String> threadKeys = new LinkedHashSet<>();
        for (String row : readLines(KEY_ASSIGNMENTS)) {
            int split = row.indexOf(SEPARATOR);
            if (split > 0 && row.substring(0, split).equals(category)) {
                threadKeys.add(row.substring(split + 1));
            }
        }
        return threadKeys;
    }

    /** Whether the user is currently browsing a folder's private inbox (empty string = all chats). */
    public static String activeFolder() {
        return prefs().getString(KEY_ACTIVE_FOLDER, "");
    }

    public static void setActiveFolder(String category) {
        prefs().edit().putString(KEY_ACTIVE_FOLDER, category == null ? "" : category).apply();
    }

    /** Called from the long-press sheet patch with the pressed row's thread key. */
    /** Click listener for the sheet row; flips between categorize and uncategorize. */
    @SuppressWarnings("ClassNamingConvention")
    public static final class CategorizeClickListener implements android.view.View.OnClickListener {
        private final android.content.Context context;
        private final String threadKey;

        private CategorizeClickListener(android.content.Context context, String threadKey) {
            this.context = context;
            this.threadKey = threadKey;
        }

        @Override
        public void onClick(android.view.View v) {
            String current = categoryOf(threadKey);
            if (current != null) {
                unassignThread(threadKey);
                PikoUtils.toast("Chat descategorizado");
                return;
            }
            if (context instanceof Activity) {
                showCategorizeDialog((Activity) context, threadKey);
            } else {
                PikoUtils.toast("Categorizar: pantalla no disponible");
            }
        }
    }

    /**
     * Called from the injected long-press menu builder once the native option rows
     * are configured. Adds a "Categorizar" row to the sheet config by discovering
     * its row-adding method by signature (String, View.OnClickListener) -> void.
     */
    public static void onThreadMenuBuilt(Object sheetConfig, Object threadKeyObj, Object anchorView) {
        try {
            String threadKey = String.valueOf(threadKeyObj);
            String rowLabel = categoryOf(threadKey) != null ? "Descategorizar" : "Categorizar";
            android.content.Context context = ((android.view.View) anchorView).getContext();
            for (java.lang.reflect.Method method : sheetConfig.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getReturnType() != void.class
                        || params.length != 2
                        || params[0] != String.class
                        || params[1] != android.view.View.OnClickListener.class) {
                    continue;
                }
                method.invoke(sheetConfig, rowLabel, new CategorizeClickListener(context, threadKey));
                return;
            }
            Logger.printException(() -> "DirectOrganizer: no row-adding method found on the menu config");
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer menu hook failed", e);
        }
    }

    public static void showCategorizeDialog(Activity activity, String threadKey) {
        try {
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad / 2, pad, 0);

            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
            container.addView(input, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Categorizar chat")
                    .setView(container)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            createCategory(name);
                            assignThread(threadKey, name);
                            PikoUtils.toast("Chat categorizado: " + name);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer dialog failed", e);
        }
    }
}
