package com.denizenscript.denizencore.scripts.commands.queue;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.QueueTag;
import com.denizenscript.denizencore.scripts.queues.core.TimedQueue;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.core.DurationTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;

public class WaitCommand extends AbstractCommand {

    public WaitCommand() {
        setName("wait");
        setSyntax("wait (<duration>) (queue:<name>) (system)");
        setRequiredArguments(0, 3);
        isProcedural = false; // A procedure can't wait
        setBooleansHandled("system");
        setPrefixesHandled("queue");
    }

    // <--[command]
    // @Name Wait
    // @Syntax wait (<duration>) (queue:<name>) (system)
    // @Required 0
    // @Maximum 3
    // @Short Delays a script for a specified amount of time.
    // @Synonyms Delay
    // @Group queue
    //
    // @Description
    // Pauses the script queue for the duration specified. If no duration is specified it defaults to 3 seconds.
    // Accepts the 'queue:<name>' argument which allows the delay of a different queue.
    //
    // Accepts a 'system' argument to delay based on system time (real-world time on a clock).
    // When that argument is not used, waits based on delta time (in-game time tracking, which tends to vary by small amounts, especially when the server is lagging).
    // Generally, do not use the 'system' argument unless you have a specific good reason you need it.
    //
    // @Tags
    // <QueueTag.speed>
    //
    // @Usage
    // Use to delay the current queue for 1 minute.
    // - wait 1m
    //
    // @Usage
    // Use to delay the current queue until 1 hour of system time passes.
    // - wait 1h system
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (arg.matchesArgumentType(DurationTag.class)
                    && !scriptEntry.hasObject("delay")
                    && arg.limitToOnlyPrefix("delay")) {
                scriptEntry.addObject("delay", arg.asType(DurationTag.class));
            }
            else {
                arg.reportUnhandled();
            }
        }
        scriptEntry.defaultObject("delay", new DurationTag(3));
    }

    public static class SystemTimeDelayTracker implements TimedQueue.DelayTracker {

        public long systemTimeEnd;

        public SystemTimeDelayTracker(long millis) {
            systemTimeEnd = CoreUtilities.monotonicMillis() + millis;
        }

        @Override
        public boolean isDelayed() {
            return systemTimeEnd > CoreUtilities.monotonicMillis();
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        QueueTag queue = scriptEntry.argForPrefix("queue", QueueTag.class, true);
        if (queue == null) {
            queue = new QueueTag(scriptEntry.getResidingQueue());
        }
        DurationTag delay = scriptEntry.getObjectTag("delay");
        boolean system = scriptEntry.argAsBoolean("system");
        if (scriptEntry.dbCallShouldDebug()) {
            Debug.report(scriptEntry, getName(), queue, delay, db("mode", system ? "system" : "delta"));
        }
        TimedQueue.DelayTracker tracker;
        if (system) {
            tracker = new SystemTimeDelayTracker(delay.getMillis());
        }
        else {
            tracker = new TimedQueue.DeltaTimeDelayTracker(delay.getMillis());
        }
        if (queue.queue instanceof TimedQueue) {
            ((TimedQueue) queue.queue).delay = tracker;
        }
        else {
            scriptEntry.setInstant(false);
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.echoDebug(scriptEntry, "Forcing queue " + queue.queue.id + " into a timed queue...");
            }
            queue.queue.forceToTimed(tracker);
        }
    }
}
