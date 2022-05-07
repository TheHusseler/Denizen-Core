package com.denizenscript.denizencore.scripts.commands.file;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.core.BinaryTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.utilities.CoreConfiguration;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.utilities.scheduling.AsyncSchedulable;
import com.denizenscript.denizencore.utilities.scheduling.OneTimeSchedulable;

import java.io.File;
import java.io.FileInputStream;

public class FileReadCommand extends AbstractCommand implements Holdable {

    public FileReadCommand() {
        setName("fileread");
        setSyntax("fileread [path:<path>]");
        setRequiredArguments(1, 1);
        isProcedural = false;
        setPrefixesHandled("path");
    }

    // <--[command]
    // @Name FileRead
    // @Syntax fileread [path:<path>]
    // @Required 1
    // @Maximum 1
    // @Short Reads the file at the given path.
    // @Group file
    //
    // @Description
    // Reads the file at the given path.
    //
    // The starting directory is server/plugins/Denizen.
    //
    // Note that in most cases this command should be ~waited for (like "- ~fileread ..."). Refer to <@link language ~waitable>.
    //
    // This command must be enabled by setting Denizen config option "Commands.File.Allow read" to true.
    //
    // @Tags
    // <entry[saveName].data> returns a BinaryTag of the raw file content.
    //
    // @Usage
    // Use to read 'myfile' and narrate the text content.
    // - ~fileread path:data/myfile.dat save:read
    // - narrate "Read data: <entry[read].data.utf8_decode>"
    //
    // -->

    @Override
    public void execute(final ScriptEntry scriptEntry) {
        ElementTag path = scriptEntry.argForPrefixAsElement("path", null);
        if (path == null) {
            throw new InvalidArgumentsRuntimeException("Missing 'path' argument.");
        }
        if (scriptEntry.dbCallShouldDebug()) {
            Debug.report(scriptEntry, getName(), path);
        }
        if (!CoreConfiguration.allowFileRead) {
            Debug.echoError(scriptEntry, "FileRead disabled in Denizen/config.yml (refer to command documentation).");
            scriptEntry.setFinished(true);
            return;
        }
        File file = new File(DenizenCore.implementation.getDataFolder(), path.asString());
        if (!DenizenCore.implementation.canReadFile(file)) {
            Debug.echoError("Cannot read from that file path due to security settings in Denizen/config.yml.");
            scriptEntry.setFinished(true);
            return;
        }
        try {
            if (!CoreConfiguration.filePathLimit.equals("none")) {
                File root = new File(DenizenCore.implementation.getDataFolder(), CoreConfiguration.filePathLimit);
                if (!file.getCanonicalPath().startsWith(root.getCanonicalPath())) {
                    Debug.echoError("File path '" + path.asString() + "' is not within the config's restricted data file path.");
                    scriptEntry.setFinished(true);
                    return;
                }
            }
            if (!file.exists()) {
                Debug.echoError(scriptEntry, "File read failed, file does not exist!");
                scriptEntry.setFinished(true);
                return;
            }
        }
        catch (Exception ex) {
            Debug.echoError(ex);
            scriptEntry.setFinished(true);
            return;
        }
        Runnable runme = () -> {
            try {
                FileInputStream stream = new FileInputStream(file);
                byte[] data = stream.readAllBytes();
                stream.close();
                scriptEntry.addObject("data", new BinaryTag(data));
                scriptEntry.setFinished(true);
            }
            catch (Exception e) {
                Debug.echoError(scriptEntry, e);
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
