package com.denizenscript.denizencore.scripts.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.tags.TagManager;

public class CommandExecutor {

    public static ScriptQueue currentQueue;

    public static void debugSingleExecution(ScriptEntry scriptEntry) {
        StringBuilder output = new StringBuilder();
        output.append("<G>(line ").append(scriptEntry.internal.lineNumber).append(")<W> ");
        if (scriptEntry.internal.waitfor) {
            output.append("~");
        }
        output.append(scriptEntry.getCommandName());
        if (scriptEntry.getOriginalArguments() == null) {
            Debug.echoError(scriptEntry, "Original Arguments null for " + scriptEntry.getCommandName());
        }
        else {
            for (String arg : scriptEntry.getOriginalArguments()) {
                if (CoreUtilities.contains(arg, ' ')) {
                    output.append(" \"").append(arg).append("\"");
                }
                else {
                    output.append(" ").append(arg);
                }
            }
        }
        DenizenCore.implementation.debugQueueExecute(scriptEntry, scriptEntry.getResidingQueue().debugId, output.toString());
    }

    // <--[language]
    // @name The Save Argument
    // @group Script Command System
    // @description
    // The "save:<name>" argument is a special meta-argument that is available for all commands, but is only useful for some.
    // It is written like:
    // - run MyScript save:mysave
    //
    // When the save argument is used, the results of the command will be saved on the queue, for later usage by the "entry" tag.
    //
    // The useful entry keys available for any command are listed in the "Tags" documentation section for any command.
    // For example, the "run" command lists "<entry[saveName].created_queue>".
    // The "saveName" part should be replaced with whatever name you gave to the "save" argument,
    // and the "created_queue" part changes between commands.
    // Some commands have multiple save entry keys, some have just one, most don't have any.
    // -->

    // <--[language]
    // @name The Global If Argument
    // @group Script Command System
    // @description
    // The "if:<boolean>" argument is a special meta-argument that is available for all commands, but is more useful for some than others.
    // It is written like:
    // <code>
    // - stop if:<player.has_flag[forbidden]>
    // # Equivalent to
    // - if <player.has_flag[forbidden]>:
    //   - stop
    // </code>
    //
    // When the if argument is used, the command will only run if the value of the argument is 'true'.
    //
    // The most useful place to have this is a 'stop' command, to quickly stop a script if a condition is true (a player has a flag, lacks a permission, is outside a region, or whatever else).
    //
    // If you need more complex matching, especially using '&&', '||', '==', etc. you should probably just do an 'if' command rather than using the argument.
    // Though if you really want to, you can use tags here like <@link tag objecttag.is.to> or <@link tag elementtag.and> or <@link tag elementtag.or>.
    // -->

    public static boolean execute(ScriptEntry scriptEntry) {
        if (scriptEntry.dbCallShouldDebug()) {
            debugSingleExecution(scriptEntry);
        }
        TagManager.recentTagError = false;
        AbstractCommand command = scriptEntry.internal.actualCommand;
        currentQueue = scriptEntry.getResidingQueue();
        if (currentQueue.procedural && !command.isProcedural) {
            Debug.echoError("Command " + command.name + " is not accepted within a procedure. Procedures may not produce a change in the world, they may only process logic.");
            return false;
        }
        String saveName = null;
        try {
            TagContext context = scriptEntry.getContext();
            for (Argument arg : scriptEntry.internal.preprocArgs) {
                if (DenizenCore.implementation.handleCustomArgs(scriptEntry, arg, false)) {
                    // Do nothing
                }
                else if (arg.matchesPrefix("if")) {
                    String tagged = CoreUtilities.toLowerCase(TagManager.tag(arg.getValue(), context));
                    boolean shouldRun = tagged.equals("true") || tagged.equals("!false");
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, shouldRun ? "'if:' arg passed, command will run." : "'if:' arg returned false, command won't run.");
                    }
                    if (!shouldRun) {
                        scriptEntry.setFinished(true);
                        currentQueue = null;
                        return true;
                    }
                }
                else if (arg.matchesPrefix("save")) {
                    saveName = TagManager.tag(arg.getValue(), context);
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, "...remembering this script entry as '" + saveName + "'!");
                    }
                }
            }
            command.parseArgs(scriptEntry);
            command.execute(scriptEntry);
            if (saveName != null) {
                scriptEntry.getResidingQueue().holdScriptEntry(saveName, scriptEntry);
            }
            currentQueue = null;
            return true;
        }
        catch (InvalidArgumentsException | InvalidArgumentsRuntimeException e) {
            // Give usage hint if InvalidArgumentsException was called.
            Debug.echoError(scriptEntry, "Woah! Invalid arguments were specified!");
            if (e.getMessage() != null && e.getMessage().length() > 0) {
                Debug.log("+> MESSAGE follows: " + "'" + e.getMessage() + "'");
            }
            Debug.log("Usage: " + command.getUsageHint());
            Debug.log("(Attempted: " + scriptEntry + ")");
            Debug.echoDebug(scriptEntry, Debug.DebugElement.Footer);
            scriptEntry.setFinished(true);
            currentQueue = null;
            return false;
        }
        catch (Throwable e) {
            Debug.echoError(scriptEntry, "Woah! An exception has been called with this command!");
            Debug.echoError(scriptEntry, e);
            Debug.log("(Attempted: " + scriptEntry + ")");
            Debug.echoDebug(scriptEntry, Debug.DebugElement.Footer);
            scriptEntry.setFinished(true);
            currentQueue = null;
            return false;
        }
    }
}
