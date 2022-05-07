package com.denizenscript.denizencore.scripts.commands.queue;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.BracedCommand;

import java.util.List;

public class RepeatCommand extends BracedCommand {

    public RepeatCommand() {
        setName("repeat");
        setSyntax("repeat [stop/next/<amount>] (from:<#>) (as:<name>) [<commands>]");
        setRequiredArguments(1, 3);
        isProcedural = true;
        setPrefixesHandled("from", "as");
        setBooleansHandled("stop", "next", "\0callback");
    }

    // <--[command]
    // @Name Repeat
    // @Syntax repeat [stop/next/<amount>] (from:<#>) (as:<name>) [<commands>]
    // @Required 1
    // @Maximum 3
    // @Short Runs a series of braced commands several times.
    // @Synonyms For
    // @Group queue
    // @Guide https://guide.denizenscript.com/guides/basics/loops.html
    //
    // @Description
    // Loops through a series of braced commands a specified number of times.
    // To get the number of loops so far, you can use <[value]>.
    //
    // Optionally, specify "as:<name>" to change the definition name to something other than "value".
    //
    // Optionally, to specify a starting index, use "from:<#>". Note that the "amount" input is how many loops will happen, not an end index.
    // The default "from" index is "1". Note that the value you give to "from" will be the value of the first loop.
    //
    // To stop a repeat loop, do - repeat stop
    //
    // To jump immediately to the next number in the loop, do - repeat next
    //
    // @Tags
    // <[value]> to get the number of loops so far
    //
    // @Usage
    // Use to loop through a command five times.
    // - repeat 5:
    //     - announce "Announce Number <[value]>"
    //
    // @Usage
    // Use to announce the numbers: 1, 2, 3, 4, 5.
    // - repeat 5 as:number:
    //     - announce "I can count! <[number]>"
    //
    // @Usage
    // Use to announce the numbers: 21, 22, 23, 24, 25.
    // - repeat 5 from:21:
    //     - announce "Announce Number <[value]>"
    // -->

    private static class RepeatData {
        public int index;
        public int target;
        public String valueName;
        public ObjectTag originalValue;

        public void reapplyAtEnd(ScriptQueue queue) {
            queue.addDefinition(valueName, originalValue);
        }
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (arg.matchesInteger() && !arg.hasPrefix()) {
                scriptEntry.addObject("quantity", arg.asElement());
            }
            else if (arg.matches("{")) {
                break;
            }
            else {
                arg.reportUnhandled();
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        boolean stop = scriptEntry.argAsBoolean("stop");
        boolean next = scriptEntry.argAsBoolean("next");
        boolean callback = scriptEntry.argAsBoolean("\0callback");
        ScriptQueue queue = scriptEntry.getResidingQueue();
        if (stop) {
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), db("instruction", "stop"));
            }
            boolean hasnext = false;
            for (int i = 0; i < queue.getQueueSize(); i++) {
                ScriptEntry entry = queue.getEntry(i);
                List<String> args = entry.getOriginalArguments();
                if (entry.getCommandName().equals("REPEAT") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                    hasnext = true;
                    break;
                }
            }
            if (hasnext) {
                while (queue.getQueueSize() > 0) {
                    ScriptEntry entry = queue.getEntry(0);
                    List<String> args = entry.getOriginalArguments();
                    if (entry.getCommandName().equals("REPEAT") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                        ((RepeatData) entry.getOwner().getData()).reapplyAtEnd(queue);
                        queue.removeFirst();
                        break;
                    }
                    queue.removeFirst();
                }
            }
            else {
                Debug.echoError("Cannot stop repeat: not in one!");
            }
            return;
        }
        else if (next) {
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), db("instruction", "next"));
            }
            boolean hasnext = false;
            for (int i = 0; i < queue.getQueueSize(); i++) {
                ScriptEntry entry = queue.getEntry(i);
                List<String> args = entry.getOriginalArguments();
                if (entry.getCommandName().equals("REPEAT") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                    hasnext = true;
                    break;
                }
            }
            if (hasnext) {
                while (queue.getQueueSize() > 0) {
                    ScriptEntry entry = queue.getEntry(0);
                    List<String> args = entry.getOriginalArguments();
                    if (entry.getCommandName().equals("REPEAT") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                        break;
                    }
                    queue.removeFirst();
                }
            }
            else {
                Debug.echoError("Cannot 'repeat next': not in one!");
            }
            return;
        }
        else if (callback) {
            if (scriptEntry.getOwner() != null && (scriptEntry.getOwner().getCommandName().equals("REPEAT") ||
                    scriptEntry.getOwner().getBracedSet() == null || scriptEntry.getOwner().getBracedSet().isEmpty() ||
                    scriptEntry.getBracedSet().get(0).value.get(scriptEntry.getBracedSet().get(0).value.size() - 1) != scriptEntry)) {
                RepeatData data = (RepeatData) scriptEntry.getOwner().getData();
                data.index++;
                if (data.index <= data.target) {
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, Debug.DebugElement.Header, "Repeat loop " + data.index);
                    }
                    queue.addDefinition(data.valueName, String.valueOf(data.index));
                    List<ScriptEntry> bracedCommands = BracedCommand.getBracedCommandsDirect(scriptEntry.getOwner(), scriptEntry);
                    ScriptEntry callbackEntry = scriptEntry.clone();
                    callbackEntry.copyFrom(scriptEntry);
                    callbackEntry.setOwner(scriptEntry.getOwner());
                    bracedCommands.add(callbackEntry);
                    for (ScriptEntry cmd : bracedCommands) {
                        cmd.setInstant(true);
                    }
                    queue.injectEntriesAtStart(bracedCommands);
                }
                else {
                    data.reapplyAtEnd(queue);
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, Debug.DebugElement.Header, "Repeat loop complete");
                    }
                }
            }
            else {
                Debug.echoError("Repeat CALLBACK invalid: not a real callback!");
            }
        }
        else {
            ElementTag as_name = scriptEntry.argForPrefixAsElement("as", "value");
            ElementTag from = scriptEntry.argForPrefixAsElement("from", "1");
            ElementTag quantity = scriptEntry.getElement("quantity");
            if (quantity == null) {
                throw new InvalidArgumentsRuntimeException("Must specify a quantity or 'stop' or 'next'!");
            }
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), from, quantity, as_name);
            }
            int target = quantity.asInt();
            if (target <= 0) {
                if (scriptEntry.dbCallShouldDebug()) {
                    Debug.echoDebug(scriptEntry, "Zero count, not looping...");
                }
                return;
            }
            RepeatData datum = new RepeatData();
            datum.index = from.asInt();
            datum.target = datum.index + target - 1;
            datum.valueName = as_name.asString();
            scriptEntry.setData(datum);
            ScriptEntry callbackEntry = new ScriptEntry("REPEAT", new String[] {"\0CALLBACK"},
                    (scriptEntry.getScript() != null ? scriptEntry.getScript().getContainer() : null));
            List<ScriptEntry> bracedCommandsList = getBracedCommandsDirect(scriptEntry, scriptEntry);
            if (bracedCommandsList == null || bracedCommandsList.isEmpty()) {
                Debug.echoError(scriptEntry, "Empty subsection - did you forget a ':'?");
                return;
            }
            datum.originalValue = queue.getDefinitionObject(datum.valueName);
            queue.addDefinition(datum.valueName, String.valueOf(datum.index));
            callbackEntry.copyFrom(scriptEntry);
            callbackEntry.setOwner(scriptEntry);
            bracedCommandsList.add(callbackEntry);
            for (ScriptEntry cmd : bracedCommandsList) {
                cmd.setInstant(true);
            }
            scriptEntry.setInstant(true);
            queue.injectEntriesAtStart(bracedCommandsList);
        }
    }
}
