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
import android.widget.TextView;

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
    /** Cached reflection accessors for reading a row's thread key (resolved once per process). */
    private static volatile java.lang.reflect.Field cachedRowSummaryField;
    private static volatile java.lang.reflect.Method cachedKeyAccessor;

    /**
     * Reorders the inbox thread list in place: categorized chats grouped by category
     * (in creation order) first, uncategorized chats keep their native order after.
     * Called from the injected thread-store sort hook.
     */
    @SuppressWarnings("unchecked")
    public static void reorderInbox(Object listObj) {
        try {
            java.util.List<Object> rows = (java.util.List<Object>) listObj;
            if (rows.isEmpty()) {
                return;
            }
            java.util.Map<String, java.util.List<Object>> byCategory = new java.util.LinkedHashMap<>();
            java.util.List<Object> uncategorized = new ArrayList<>();
            for (Object row : rows) {
                String key = keyOf(row);
                String category = key == null ? null : categoryOf(key);
                if (category == null) {
                    uncategorized.add(row);
                } else {
                    byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(row);
                }
            }
            int index = 0;
            PikoUtils.toast("Org: " + rows.size() + " filas, " + uncategorized.size()
                    + " sin categoria, carpetas=" + byCategory.keySet());
            for (String category : getCategories()) {
                java.util.List<Object> group = byCategory.remove(category);
                if (group == null) {
                    continue;
                }
                for (Object row : group) {
                    rows.set(index++, row);
                }
            }
            for (java.util.List<Object> group : byCategory.values()) {
                for (Object row : group) {
                    rows.set(index++, row);
                }
            }
            for (Object row : uncategorized) {
                rows.set(index++, row);
            }
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer reorder failed", e);
        }
    }

    /** Thread key of an inbox row, read reflectively: row -> summary field -> DirectThreadKey getter. */
    private static String keyOf(Object row) {
        try {
            if (cachedRowSummaryField != null && cachedKeyAccessor != null) {
                Object summary = cachedRowSummaryField.get(row);
                if (summary == null) {
                    return null;
                }
                Object key = cachedKeyAccessor.invoke(summary);
                return key == null ? null : key.toString();
            }
            for (java.lang.reflect.Field field : row.getClass().getDeclaredFields()) {
                if (field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(row);
                if (value == null) {
                    continue;
                }
                for (java.lang.reflect.Method method : value.getClass().getMethods()) {
                    if (method.getParameterCount() != 0
                            || !method.getReturnType().getName()
                                    .equals("com.instagram.model.direct.DirectThreadKey")) {
                        continue;
                    }
                    Object key = method.invoke(value);
                    if (key == null) {
                        continue;
                    }
                    // Only bind to the first accessor that actually yields a key on a live row.
                    cachedRowSummaryField = field;
                    cachedKeyAccessor = method;
                    PikoUtils.logger("DirectOrganizer: key accessor bound to field "
                            + field.getName() + " via " + method.getName());
                    return key.toString();
                }
            }
            PikoUtils.logger("DirectOrganizer: no thread key accessor resolved");
            return null;
        } catch (Exception e) {
            PikoUtils.logger("DirectOrganizer keyOf failed: " + e);
            return null;
        }
    }

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
    public static void onThreadMenuBuilt(Object sheetConfig, Object threadKeyObj, android.view.View anchorView) {
        try {
            String threadKey = String.valueOf(threadKeyObj);
            String rowLabel = categoryOf(threadKey) != null ? "Descategorizar" : "Categorizar";
            android.content.Context context = anchorView.getContext();
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
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int cardColor = dark ? 0xFF2C2C2E : 0xFFF2F2F7;
            int textColor = dark ? 0xFFFFFFFF : 0xFF000000;
            int separatorColor = dark ? 0xFF3A3A3C : 0xFFD1D1D6;
            float density = activity.getResources().getDisplayMetrics().density;
            int dp = (int) (density * 16);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable cardBackground = new android.graphics.drawable.GradientDrawable();
            cardBackground.setColor(cardColor);
            cardBackground.setCornerRadius(14 * density);
            card.setBackground(cardBackground);

            TextView title = new TextView(activity);
            title.setText("Categorizar chat");
            title.setTextColor(textColor);
            title.setTextSize(17);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setGravity(android.view.Gravity.CENTER);
            title.setPadding(dp, dp, dp, dp / 2);
            card.addView(title, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
            input.setTextColor(textColor);
            input.setHintTextColor(0xFF8E8E93);
            input.setHint("Nombre de la carpeta");
            input.setBackground(null);
            input.setGravity(android.view.Gravity.CENTER);
            input.setTextSize(16);
            card.addView(input, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            TextView cancel = new TextView(activity);
            cancel.setText("Cancelar");
            cancel.setTextColor(0xFF0A84FF);
            cancel.setTextSize(17);
            cancel.setGravity(android.view.Gravity.CENTER);
            cancel.setPadding(0, dp, 0, dp);
            TextView create = new TextView(activity);
            create.setText("Crear");
            create.setTextColor(0xFF0A84FF);
            create.setTextSize(17);
            create.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            create.setGravity(android.view.Gravity.CENTER);
            create.setPadding(0, dp, 0, dp);

            buttons.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            android.view.View midDivider = new android.view.View(activity);
            midDivider.setBackgroundColor(separatorColor);
            buttons.addView(midDivider, new LinearLayout.LayoutParams(
                    (int) (density * 1), LayoutParams.MATCH_PARENT));
            buttons.addView(create, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout buttonsWrap = new LinearLayout(activity);
            buttonsWrap.setOrientation(LinearLayout.VERTICAL);
            android.view.View topDivider = new android.view.View(activity);
            topDivider.setBackgroundColor(separatorColor);
            buttonsWrap.addView(topDivider, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, (int) (density * 1)));
            buttonsWrap.addView(buttons, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            card.addView(buttonsWrap, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                    .setView(card)
                    .create();
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            cancel.setOnClickListener(v -> dialog.dismiss());
            create.setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    createCategory(name);
                    assignThread(threadKey, name);
                    PikoUtils.toast("Chat categorizado: " + name);
                }
                dialog.dismiss();
            });
            dialog.show();
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout((int) (270 * density), LayoutParams.WRAP_CONTENT);
            }
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer dialog failed", e);
        }
    }
}
