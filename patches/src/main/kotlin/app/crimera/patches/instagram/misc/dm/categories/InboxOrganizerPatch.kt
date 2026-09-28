/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.categories

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/DirectOrganizer;"

// The thread store's sorted-copy method: the single point where the inbox thread
// list is ordered. Its systrace trace string is a stable anchor.
internal object InboxOrganizerFingerprint : Fingerprint(
    strings = listOf("DirectThreadStoreImpl.getSortedCopyOfThreadSummaries"),
)

@Suppress("unused")
val inboxOrganizerPatch =
    bytecodePatch(
        name = "Chat category grouping",
        description = "Groups categorized chats into their folders at the top of the inbox list.",
        default = true,
    ) {
        // settingsPatch brings the shared extension dex merge; without it the hook
        // class would not exist in the patched app.
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            InboxOrganizerFingerprint.method.apply {
                val returnIndex = instructions.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
                if (returnIndex <= 0) {
                    throw PatchException("Thread store sort method has no object return")
                }
                val returnInstruction = instructions[returnIndex] as OneRegisterInstruction
                val resultRegister = returnInstruction.registerA

                val freeRegisters = getFreeRegisterProvider(returnIndex, 1)
                val scratch = freeRegisters.getFreeRegister()

                addInstructions(
                    returnIndex,
                    """
                    move-object/from16 v$scratch, v$resultRegister
                    invoke-static {v$scratch}, $HOOK_CLASS->reorderInbox(Ljava/util/List;)V
                    """.trimIndent(),
                )
            }
        }
    }
