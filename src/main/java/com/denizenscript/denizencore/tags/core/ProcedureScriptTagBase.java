package com.denizenscript.denizencore.tags.core;

import com.denizenscript.denizencore.objects.core.DurationTag;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.tags.TagRunnable;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.ScriptTag;
import com.denizenscript.denizencore.scripts.containers.core.ProcedureScriptContainer;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ReplaceableTagEvent;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.ScriptUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.tags.TagManager;

public class ProcedureScriptTagBase {

    public ProcedureScriptTagBase() {
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                procedureTag(event);
            }
        }, "proc");
    }

    public void procedureTag(ReplaceableTagEvent event) {

        // <--[tag]
        // @attribute <proc[<procedure_script_name>]>
        // @returns ObjectTag
        // @description
        // Returns the 'determine' result of a procedure script.
        // -->
        if (!event.matches("proc")) {
            return;
        }

        Attribute attribute = event.getAttributes();
        ScriptTag script;
        String path = null;
        if (attribute.hasParam()) {
            if (attribute.getParam().indexOf('.') > 0) {
                String[] split = attribute.getParam().split("\\.", 2);
                path = split[1];
                script = ScriptTag.valueOf(split[0], attribute.context);
            }
            else {
                script = attribute.paramAsType(ScriptTag.class);
            }
        }
        else {
            Debug.echoError("Invalid procedure script tag!");
            return;
        }
        if (script == null) {
            attribute.echoError("Missing script for procedure script tag '" + attribute.getParam() + "'!");
            return;
        }
        if (!(script.getContainer() instanceof ProcedureScriptContainer)) {
            attribute.echoError("Chosen script is not a procedure script!");
            return;
        }
        ListTag definitions = null;

        // <--[tag]
        // @attribute <proc[<procedure_script_name>].context[<object>|...]>
        // @returns ObjectTag
        // @description
        // Returns the 'determine' result of a procedure script with the given context.
        // -->
        if (attribute.startsWith("context", 2)) {
            definitions = attribute.contextAsType(2, ListTag.class);
            attribute.fulfill(1);
        }
        ScriptQueue queue = ScriptUtilities.createAndStartQueue(script.getContainer(), path, event.getContext().getScriptEntryData(), null, (q) -> {
            q.procedural = true;
        }, new DurationTag(0), null, definitions, script.getContainer());
        if (queue == null) {
            attribute.echoError("Procedure queue start failed.");
            return;
        }
        attribute.fulfill(1);
        if (queue.determinations == null || queue.determinations.size() == 0) {
            attribute.echoError("Procedure call did not determine any value.");
            return;
        }
        event.setReplacedObject(CoreUtilities.autoAttribTyped(queue.determinations.getObject(0), attribute));
    }
}
