/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.categories

import app.morphe.patcher.Fingerprint

// The inbox thread long-press menu builder. It receives the pressed row's view and
// thread key and assembles the native option rows (Pin/Delete/Mute/...). Anchored by
// its unique controller trace string plus the reminder-impression strings only this
// builder contains.
internal object ThreadLongPressMenuFingerprint : Fingerprint(
    strings = listOf("DirectInboxThreadDialogController", "set_reminder_impression"),
    parameters = listOf(
        "Landroid/graphics/RectF;",
        "Landroid/view/View;",
        "LX/077r;",
        "LX/08r4;",
        "LX/0QAe;",
        "LX/095y;",
        "LX/0Qas;",
        "Lcom/instagram/model/direct/DirectShareTarget;",
        "Lcom/instagram/model/direct/DirectThreadKey;",
        "LX/03sn;",
        "Ljava/lang/Integer;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
        "Z",
        "Z",
        "Z",
    ),
    returnType = "V",
)
