package com.denizenscript.denizencore.scripts.commands.file;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.utilities.CoreConfiguration;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.utilities.scheduling.AsyncSchedulable;
import com.denizenscript.denizencore.utilities.scheduling.OneTimeSchedulable;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;

import java.io.File;
import java.nio.file.Files;

public class FileCopyCommand extends AbstractCommand implements Holdable {

    public FileCopyCommand() {
        setName("filecopy");
        setSyntax("filecopy [origin:<origin>] [destination:<destination>] (overwrite)");
        setRequiredArguments(2, 3);
        isProcedural = false;
    }

    // <--[command]
    // @Name FileCopy
    // @Syntax filecopy [origin:<origin>] [destination:<destination>] (overwrite)
    // @Required 2
    // @Maximum 3
    // @Short Copies a file from one location to another.
    // @Group file
    //
    // @Description
    // Copies a file from one location to another.
    //
    // The starting directory is server/plugins/Denizen.
    //
    // May overwrite existing copies of files.
    //
    // Note that in most cases this command should be ~waited for (like "- ~filecopy ..."). Refer to <@link language ~waitable>.
    //
    // This command can be disabled by setting Denizen config option "Commands.Filecopy.Allow copying files" to false.
    //
    // @Tags
    // <entry[saveName].success> returns whether the copy succeeded (if not, either an error or occurred, or there is an existing file in the destination.)
    //
    // @Usage
    // Use to copy a custom YAML data file to a backup folder, overwriting any old backup of it that exists.
    // - ~filecopy o:data/custom.yml d:data/backup.yml overwrite save:copy
    // - narrate "Copy success<&co> <entry[copy].success>"
    //
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("origin")
                    && arg.matchesPrefix("origin", "o")) {
                scriptEntry.addObject("origin", arg.asElement());
            }
            else if (!scriptEntry.hasObject("destination")
                    && arg.matchesPrefix("destination", "d")) {
                scriptEntry.addObject("destination", arg.asElement());
            }
            else if (!scriptEntry.hasObject("overwrite")
                    && arg.matches("overwrite")) {
                scriptEntry.addObject("overwrite", new ElementTag("true"));
            }
            else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("origin")) {
            throw new InvalidArgumentsException("Must have a valid origin!");
        }
        if (!scriptEntry.hasObject("destination")) {
            throw new InvalidArgumentsException("Must have a valid destination!");
        }
        scriptEntry.defaultObject("overwrite", new ElementTag("false"));
    }

    @Override
    public void execute(final ScriptEntry scriptEntry) {
        ElementTag origin = scriptEntry.getElement("origin");
        ElementTag destination = scriptEntry.getElement("destination");
        ElementTag overwrite = scriptEntry.getElement("overwrite");
        if (scriptEntry.dbCallShouldDebug()) {
            Debug.report(scriptEntry, getName(), origin, destination, overwrite);
        }
        if (!CoreConfiguration.allowFileCopy) {
            Debug.echoError(scriptEntry, "File copy disabled by server administrator (refer to command documentation).");
            scriptEntry.addObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }
        File o = new File(DenizenCore.implementation.getDataFolder(), origin.asString());
        File d = new File(DenizenCore.implementation.getDataFolder(), destination.asString());
        boolean ow = overwrite.asBoolean();
        boolean dexists = d.exists();
        boolean disdir = d.isDirectory() || destination.asString().endsWith("/");
        if (!DenizenCore.implementation.canReadFile(o)) {
            Debug.echoError("Cannot read from that file path due to security settings in Denizen/config.yml.");
            scriptEntry.addObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }
        if (!o.exists()) {
            Debug.echoError(scriptEntry, "File copy failed, origin does not exist!");
            scriptEntry.addObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }
        if (!DenizenCore.implementation.canWriteToFile(d)) {
            Debug.echoError("Cannot write to that file path due to security settings in Denizen/config.yml.");
            scriptEntry.addObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }
        if (dexists && !disdir && !ow) {
            Debug.echoDebug(scriptEntry, "File copy ignored, destination file already exists!");
            scriptEntry.addObject("success", new ElementTag("false"));
            scriptEntry.setFinished(true);
            return;
        }
        Runnable runme = () -> {
            try {
                if (dexists && !disdir) {
                    d.delete();
                }
                if (disdir && !dexists) {
                    d.mkdirs();
                }
                else if (!dexists && !d.getParentFile().exists()) {
                    d.getParentFile().mkdirs();
                }
                if (o.isDirectory()) {
                    CoreUtilities.copyDirectory(o, d, null);
                }
                else {
                    Files.copy(o.toPath(), (disdir ? d.toPath().resolve(o.toPath().getFileName()) : d.toPath()));
                }
                scriptEntry.addObject("success", new ElementTag("true"));
                scriptEntry.setFinished(true);
            }
            catch (Exception e) {
                Debug.echoError(scriptEntry, e);
                scriptEntry.addObject("success", new ElementTag("false"));
                scriptEntry.setFinished(true);
            }
        };
        if (scriptEntry.shouldWaitFor()) {
            DenizenCore.schedule(new AsyncSchedulable(new OneTimeSchedulable(runme, 0)));
        }
        else {
            runme.run();
        }
    }
}
