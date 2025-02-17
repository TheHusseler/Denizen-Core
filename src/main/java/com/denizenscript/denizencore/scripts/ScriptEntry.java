package com.denizenscript.denizencore.scripts;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.ScriptTag;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.BracedCommand;
import com.denizenscript.denizencore.scripts.commands.CommandRegistry;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.tags.ParseableTag;
import com.denizenscript.denizencore.utilities.CoreConfiguration;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.Deprecations;
import com.denizenscript.denizencore.utilities.debugging.Debuggable;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.tags.TagManager;

import java.util.*;

/**
 * ScriptEntry contain information about a single entry from a dScript. It is used
 * by the CommandExecutor, among other parts of Denizen.
 */
public class ScriptEntry implements Cloneable, Debuggable, Iterable<Argument> {

    public static class ScriptEntryInternal {

        public String command = null;

        public AbstractCommand actualCommand = null;

        /** Raw text of arguments list. */
        public List<String> pre_tagged_args = null;

        public List<BracedCommand.BracedData> bracedSet = null;

        /** Full unaltered arguments list. */
        public InternalArgument[] all_arguments = null;

        /** Arguments list, excluding the ones that have pre-handled prefixes. */
        public InternalArgument[] arguments_to_use = null;

        /** Set of arguments given as exact raw input. */
        public HashSet<String> raw_input_args = null;

        public ScriptTag script = null;

        public Object yamlSubcontent = null;

        public boolean instant = false;

        public boolean waitfor = false;

        public boolean hasTags = false;

        public List<Argument> preprocArgs = null;

        public Object specialProcessedData = null;

        public String originalLine = null;

        public int lineNumber;

        public boolean brokenArgs = false;

        public HashMap<String, Integer> argPrefixMap = null;

        public ArgumentIterator argumentIterator = null;
    }

    public static class InternalArgument {

        public InternalArgument prefix = null;

        public ParseableTag value = null;

        public Argument aHArg = null;

        public boolean shouldProcess = false;

        public boolean hadColon = false;

        public String fullOriginalRawValue = null;
    }

    public static class ArgumentIterator implements Iterator<Argument> {

        public ArgumentIterator(ScriptEntry entry) {
            this.entry = entry;
        }

        public ScriptEntry entry;

        public int index = 0;

        @Override
        public boolean hasNext() {
            return index < entry.internal.arguments_to_use.length;
        }

        @Override
        public Argument next() {
            return entry.argAtIndex(entry.internal.arguments_to_use, index++);
        }
    }

    @Override
    public ArgumentIterator iterator() {
        // NOTE: This relies strongly on the assumption of non-async execution for performance benefit.
        internal.argumentIterator.index = 0;
        internal.argumentIterator.entry = this;
        return internal.argumentIterator;
    }

    public final Argument argAtIndex(ScriptEntry.InternalArgument[] argSet, int index) {
        InternalArgument internalArg = argSet[index];
        Argument arg = internalArg.aHArg;
        arg.scriptEntry = this;
        if (internalArg.shouldProcess) {
            TagManager.fillArgumentObjects(internalArg, arg, context);
            if (internalArg.hadColon && arg.prefix == null && arg.object instanceof ElementTag && ((ElementTag) arg.object).isRawInput) {
                arg.fillStr(arg.object.toString());
                if (arg.prefix != null && !internal.actualCommand.allowedDynamicPrefixes) {
                    arg.prefixWasDynamic = true;
                }
            }
            arg.canBeElement = arg.object instanceof ElementTag;
        }
        return arg;
    }

    public final boolean argAsBoolean(String argName) {
        if (internal.raw_input_args.contains(argName)) {
            return true;
        }
        Integer index = internal.argPrefixMap.get(argName);
        if (index == null) {
            return false;
        }
        Argument arg = argAtIndex(internal.all_arguments, index);
        return arg.asElement().asBoolean();
    }

    /**
     * Gets the List(ObjectTag) value of an argument by prefix name and type class.
     * @param throwError true if objects of the wrong type should error. False if wrong type should be ignored. Missing prefix never errors.
     */
    public final <T extends ObjectTag> List<T> argForPrefixList(String prefix, Class<T> clazz, boolean throwError) {
        Argument arg = argForPrefix(prefix);
        if (arg == null) {
            return null;
        }
        if (arg.matchesArgumentList(clazz)) {
            return arg.asType(ListTag.class).filter(clazz, this);
        }
        else if (throwError) {
            throw new InvalidArgumentsRuntimeException("Invalid input to '" + prefix + "': '" + arg.getValue() + "': not a valid " + clazz.getName());
        }
        return null;
    }

    public <T extends ObjectTag> T requiredArgForPrefix(String prefix, Class<T> clazz) {
        T result = argForPrefix(prefix, clazz, true);
        if (result == null) {
            throw new InvalidArgumentsRuntimeException("Must specify input to '" + prefix + "' argument. Did you forget an argument? Check meta docs!");
        }
        return result;
    }

    /**
     * Gets the ObjectTag value of an argument by prefix name and type class.
     * @param throwError true if objects of the wrong type should error. False if wrong type should be ignored. Missing prefix never errors.
     */
    public final <T extends ObjectTag> T argForPrefix(String prefix, Class<T> clazz, boolean throwError) {
        Argument arg = argForPrefix(prefix);
        if (arg == null) {
            return null;
        }
        if (arg.matchesArgumentType(clazz)) {
            return arg.asType(clazz);
        }
        else if (throwError) {
            throw new InvalidArgumentsRuntimeException("Invalid input to '" + prefix + "': '" + arg.getValue() + "': not a valid " + clazz.getName());
        }
        return null;
    }

    public final Argument argForPrefix(String prefix) {
        Integer index = internal.argPrefixMap.get(prefix);
        if (index == null) {
            return null;
        }
        return argAtIndex(internal.all_arguments, index);
    }

    public ElementTag requiredArgForPrefixAsElement(String prefix) {
        ElementTag result = argForPrefixAsElement(prefix, null);
        if (result == null) {
            throw new InvalidArgumentsRuntimeException("Must specify input to '" + prefix + "' argument. Did you forget an argument? Check meta docs!");
        }
        return result;
    }

    public final ElementTag argForPrefixAsElement(String prefix, String defaultValue) {
        Argument arg = argForPrefix(prefix);
        ElementTag result;
        if (arg == null) {
            if (defaultValue == null) {
                return null;
            }
            result = new ElementTag(defaultValue);
        }
        else {
            result = arg.asElement();
        }
        result.setPrefix(prefix);
        return result;
    }

    public ScriptEntryData entryData;

    public ScriptQueue queue = null;

    public ScriptEntryInternal internal;

    public TagContext context;

    public Boolean shouldDebugBool = null;

    private Map<String, Object> objects = new HashMap<>(8);

    public List<BracedCommand.BracedData> getBracedSet() {
        return internal.bracedSet;
    }

    public TagContext getContext() {
        return context;
    }

    public void updateContext() {
        context = DenizenCore.implementation.getTagContext(this);
    }

    public void setBracedSet(List<BracedCommand.BracedData> set) {
        internal.bracedSet = set;
    }

    public final static Argument NULL_ARGUMENT = new Argument("null_trick", "null_trick");

    public final static InternalArgument NULL_INTERNAL_ARGUMENT = new InternalArgument();

    static {
        NULL_INTERNAL_ARGUMENT.aHArg = NULL_ARGUMENT;
        NULL_INTERNAL_ARGUMENT.value = TagManager.DEFAULT_PARSEABLE_EMPTY;
        NULL_INTERNAL_ARGUMENT.fullOriginalRawValue = "";
    }

    @Override
    public ScriptEntry clone() {
        try {
            ScriptEntry se = (ScriptEntry) super.clone();
            se.objects = new HashMap<>(8);
            se.entryData = entryData.clone();
            se.entryData.scriptEntry = se;
            se.updateContext();
            return se;
        }
        catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<Object> getInsideList() {
        if (internal.yamlSubcontent instanceof List) {
            return (List<Object>) internal.yamlSubcontent;
        }
        return null;
    }

    public ScriptEntry(String command, String[] arguments, ScriptContainer script) {
        this(command, arguments, script, null, 0);
    }

    public void crunchInto(InternalArgument argVal, String arg, TagContext refContext) {
        argVal.value = TagManager.parseTextToTag(arg, refContext);
        if (argVal.value.hasTag) {
            internal.hasTags = true;
        }
        argVal.aHArg = new Argument(argVal.prefix == null ? null : argVal.prefix.aHArg.getRawValue(), arg);
        if (argVal.value.rawObject != null) {
            argVal.aHArg.object = argVal.value.rawObject;
            argVal.aHArg.unsetValue();
        }
    }

    public ScriptEntry(String command, String[] arguments, ScriptContainer script, Object insides, int lineNum) {
        if (command == null) {
            throw new RuntimeException("Command name cannot be null!");
        }
        internal = new ScriptEntryInternal();
        internal.lineNumber = lineNum;
        entryData = DenizenCore.implementation.getEmptyScriptEntryData();
        internal.command = command.toUpperCase();
        internal.yamlSubcontent = insides;
        internal.argPrefixMap = new HashMap<>();
        if (script != null) {
            internal.script = script.getAsScriptArg();
        }
        updateContext();
        if (command.length() > 0) {
            if (command.charAt(0) == '^') {
                internal.instant = true;
                internal.command = command.substring(1);
            }
            else if (command.charAt(0) == '~') {
                internal.command = command.substring(1);
                internal.actualCommand = DenizenCore.commandRegistry.get(internal.command);
                if (internal.actualCommand instanceof Holdable) {
                    internal.waitfor = true;
                }
                else if (internal.actualCommand != null) {
                    Debug.echoError(this, "The command '" + internal.command + "' cannot be waited for!");
                }
            }
            internal.actualCommand = DenizenCore.commandRegistry.get(internal.command);
            if (internal.actualCommand != null && internal.actualCommand.forceHold) {
                internal.waitfor = true;
            }
            if (internal.actualCommand == null) {
                if (!AbstractCommand.noErrorCommandNames.contains(CoreUtilities.toLowerCase(internal.command))) {
                    Debug.echoError(this, "Unknown command '" + internal.command + "'.");
                }
            }
        }
        else {
            internal.actualCommand = null;
        }
        boolean hasBraces = false;
        if (arguments != null) {
            internal.pre_tagged_args = new ArrayList<>(arguments.length);
            internal.preprocArgs = new ArrayList<>(arguments.length);
            int nested_depth = 0;
            for (String arg : arguments) {
                if (arg.lastIndexOf('%') > arg.indexOf('%')) {
                    Deprecations.ancientDefs.warn(this);
                }
                if (arg.lastIndexOf('>') > arg.indexOf('<') && arg.contains("<^")) {
                    Deprecations.instantTags.warn(this);
                }
                if (arg.equals("{")) {
                    if (!hasBraces) {
                        if (getScript() != null) { // ex command allowed to bypass
                            Deprecations.oldBraceSyntax.warn(this);
                        }
                        hasBraces = true;
                    }
                    nested_depth++;
                    internal.pre_tagged_args.add(arg);
                    continue;
                }
                if (arg.equals("}")) {
                    nested_depth--;
                    internal.pre_tagged_args.add(arg);
                    continue;
                }
                if (nested_depth > 0) {
                    internal.pre_tagged_args.add(arg);
                    continue;
                }
                String parg = arg;
                String after = null;
                if (parg.endsWith("{")) {
                    after = "{";
                    parg = parg.substring(0, parg.length() - 1);
                    Debug.echoError(this, "Command '" + command + "' in script '" + (script == null ? "(None)" : script.getName()) + "' has typo: brace written without space... like 'arg{' when it should be 'arg {'.");
                }
                Argument argObj = new Argument(arg);
                if (argObj.hasPrefix()) {
                    if (argObj.matchesPrefix("save") || argObj.matchesPrefix("if") || DenizenCore.implementation.needsHandleArgPrefix(argObj.prefix)) {
                        internal.preprocArgs.add(argObj);
                    }
                    else {
                        internal.pre_tagged_args.add(arg);
                    }
                }
                else {
                    internal.pre_tagged_args.add(parg);
                }
                if (after != null) {
                    internal.pre_tagged_args.add(after);
                    if (after.equals("{")) {
                        nested_depth++;
                        internal.pre_tagged_args.add(arg);
                        continue;
                    }
                }
            }
            nested_depth = 0;
            TagContext refContext = DenizenCore.implementation.getTagContext(this);
            ArrayList<InternalArgument> allArgs = new ArrayList<>(internal.pre_tagged_args.size());
            internal.raw_input_args = new HashSet<>();
            for (int i = 0; i < internal.pre_tagged_args.size(); i++) {
                String arg = internal.pre_tagged_args.get(i);
                if (arg.equals("{")) {
                    InternalArgument brace = new InternalArgument();
                    brace.fullOriginalRawValue = "{";
                    brace.aHArg = new Argument("", "{");
                    allArgs.add(brace);
                    nested_depth++;
                    continue;
                }
                if (arg.equals("}")) {
                    InternalArgument brace = new InternalArgument();
                    brace.fullOriginalRawValue = "}";
                    brace.aHArg = new Argument("", "}");
                    allArgs.add(brace);
                    nested_depth--;
                    continue;
                }
                allArgs.add(NULL_INTERNAL_ARGUMENT);
                if (nested_depth > 0) {
                    continue;
                }
                InternalArgument argVal = new InternalArgument();
                argVal.fullOriginalRawValue = arg;
                allArgs.set(i, argVal);
                int first_colon = arg.indexOf(':');
                argVal.hadColon = first_colon > 0;
                int first_not_prefix = (internal.actualCommand != null && internal.actualCommand.anyPrefixSymbolAllowed) ? arg.length() : Argument.prefixCharsAllowed.indexOfFirstNonMatch(arg);
                if (first_colon > 0 && first_not_prefix >= first_colon) {
                    argVal.prefix = new InternalArgument();
                    argVal.prefix.fullOriginalRawValue = arg.substring(0, first_colon);
                    crunchInto(argVal.prefix, argVal.prefix.fullOriginalRawValue, refContext);
                    arg = arg.substring(first_colon + 1);
                }
                crunchInto(argVal, arg, refContext);
                if ((argVal.value.hasTag || argVal.prefix != null) && (internal.actualCommand == null || internal.actualCommand.shouldPreParse())) {
                    argVal.shouldProcess = true;
                }
                if (argVal.value.rawObject != null) {
                    internal.raw_input_args.add(CoreUtilities.toLowerCase(argVal.value.rawObject.toString()));
                }
                if (argVal.prefix != null && argVal.prefix.value.rawObject != null) {
                    String prefix = CoreUtilities.toLowerCase(argVal.prefix.value.rawObject.toString());
                    if (internal.actualCommand != null) {
                        String altPrefix = internal.actualCommand.prefixRemapper.get(prefix);
                        if (altPrefix != null) {
                            prefix = altPrefix;
                        }
                    }
                    internal.argPrefixMap.put(prefix, i);
                }
            }
            internal.all_arguments = allArgs.toArray(new InternalArgument[0]);
            ArrayList<InternalArgument> argsToUse = new ArrayList<>(internal.all_arguments.length);
            for (InternalArgument arg : internal.all_arguments) {
                if (shouldUseArg(arg)) {
                    argsToUse.add(arg);
                }
            }
            internal.arguments_to_use = argsToUse.toArray(new InternalArgument[0]);
        }
        else {
            internal.preprocArgs = new ArrayList<>();
            internal.pre_tagged_args = new ArrayList<>();
            internal.all_arguments = new InternalArgument[0];
            internal.arguments_to_use = new InternalArgument[0];
            internal.raw_input_args = new HashSet<>();
        }
        if (internal.actualCommand != null) {
            int argCount = getOriginalArguments().size();
            if (argCount < internal.actualCommand.minimumArguments || (!hasBraces && argCount > internal.actualCommand.maximumArguments)) {
                internal.brokenArgs = true;
                internal.actualCommand = CommandRegistry.debugInvalidCommand;
            }
            if (internal.actualCommand instanceof BracedCommand) {
                BracedCommand.getBracedCommands(this);
            }
        }
        else {
            internal.actualCommand = CommandRegistry.debugInvalidCommand;
        }
        internal.argumentIterator = new ArgumentIterator(this);
    }

    public boolean shouldUseArg(InternalArgument arg) {
        if (internal.actualCommand == null) {
            return true;
        }
        if (arg.value == null) {
            return true;
        }
        if (arg.prefix != null) {
            return arg.prefix.value.rawObject == null || !internal.actualCommand.prefixesHandled.contains(CoreUtilities.toLowerCase(arg.prefix.value.rawObject.toString()));
        }
        if (arg.value.rawObject == null) {
            return true;
        }
        return !internal.actualCommand.rawValuesHandled.contains(CoreUtilities.toLowerCase(arg.value.rawObject.toString()));
    }

    /**
     * Adds a context object to the script entry. Just provide a key and an object.
     * Technically any type of object can be stored, however providing ObjectTags
     * is preferred.
     *
     * @param key    the name of the object
     * @param object the object, preferably a ObjectTag
     */
    public ScriptEntry addObject(String key, Object object) {
        if (object == null) {
            return this;
        }
        if (object instanceof ObjectTag) {
            ((ObjectTag) object).setPrefix(key);
        }
        objects.put(key, object);
        return this;
    }

    /**
     * If the scriptEntry lacks the object corresponding to the key, set it to the first non-null argument
     *
     * @param key The key of the object to check
     * @return The scriptEntry
     */
    public ScriptEntry defaultObject(String key, Object... objects) throws InvalidArgumentsException {
        if (!this.objects.containsKey(key)) {
            for (Object obj : objects) {
                if (obj != null) {
                    this.addObject(key, obj);
                    break;
                }
            }
        }
        return this;
    }

    ////////////
    // INSTANCE METHODS
    //////////

    /**
     * Gets the original, pre-tagged arguments, as constructed. This is simply a copy of
     * the original arguments, immune from any changes that may be made (such as tag filling)
     * by the CommandExecutor.
     *
     * @return unmodified arguments from entry creation
     */
    public List<String> getOriginalArguments() {
        return internal.pre_tagged_args;
    }

    public String getCommandName() {
        return internal.command;
    }

    public AbstractCommand getCommand() {
        return internal.actualCommand;
    }

    private ScriptEntry owner = null;

    public void setOwner(ScriptEntry owner) {
        this.owner = owner;
    }

    public ScriptEntry getOwner() {
        return owner;
    }

    private Object data;

    public Object getData() {
        return data;
    }

    public void setData(Object result) {
        this.data = result;
    }

    public void copyFrom(ScriptEntry entry) {
        entryData = entry.entryData.clone();
        setSendingQueue(entry.getResidingQueue());
        updateContext();
    }

    //////////////////
    // SCRIPTENTRY CONTEXT
    //////////////

    public Map<String, Object> getObjects() {
        return objects;
    }

    public Object getObject(String key) {
        try {
            return objects.get(key);
        }
        catch (Exception ex) {
            if (CoreConfiguration.debugVerbose) {
                Debug.echoError(ex);
            }
            return null;
        }
    }

    // TODO: Rename this method
    public <T> T getObjectTag(String key) {
        try {
            Object gotten = objects.get(key);
            if (gotten == null) {
                return null;
            }
            if (gotten instanceof Enum) {
                return (T) new ElementTag(((Enum) gotten).name());
            }
            return (T) gotten;
        }
        catch (Exception ex) {
            if (CoreConfiguration.debugVerbose) {
                Debug.echoError(ex);
            }
            return null;
        }
    }

    public ElementTag getElement(String key) {
        try {
            Object gotten = objects.get(key);
            if (gotten == null) {
                return null;
            }
            return (ElementTag) gotten;
        }
        catch (Exception ex) {
            if (CoreConfiguration.debugVerbose) {
                Debug.echoError(ex);
            }
            return null;
        }
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    /////////////
    // CORE LINKED OBJECTS
    ///////

    public ScriptTag getScript() {
        return internal.script;
    }

    public ScriptEntry setScript(String scriptName) {
        internal.script = ScriptTag.valueOf(scriptName, CoreUtilities.basicContext);
        return this;
    }

    public ScriptQueue getResidingQueue() {
        return queue;
    }

    public void setSendingQueue(ScriptQueue scriptQueue) {
        queue = scriptQueue;
    }

    //////////////
    // TimedQueue FEATURES
    /////////

    public boolean forceInstant = false;

    public boolean isInstant() {
        return internal.instant || forceInstant;
    }

    public ScriptEntry setInstant(boolean instant) {
        forceInstant = instant;
        return this;
    }

    public boolean isFinished = false;

    public boolean shouldWaitFor() {
        return internal.waitfor && !isFinished;
    }

    public ScriptEntry setFinished(boolean finished) {
        isFinished = finished;
        return this;
    }

    /////////////
    // DEBUGGABLE
    /////////

    public boolean dbCallShouldDebug() {
        return DenizenCore.implementation.shouldDebug(this);
    }

    @Override
    public boolean shouldDebug() {
        if (shouldDebugBool != null) {
            return shouldDebugBool;
        }
        if (internal.script == null || internal.script.getContainer() == null) {
            shouldDebugBool = true;
            return true;
        }
        shouldDebugBool = internal.script.getContainer().shouldDebug();
        return shouldDebugBool;
    }

    private static String stringifyArg(String arg) {
        arg = arg.replace("\"", "<&dq>");
        if (CoreUtilities.contains(arg, ' ')) {
            return '"' + arg + '"';
        }
        else {
            return arg;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String str : getOriginalArguments()) {
            sb.append(" ").append(stringifyArg(str));
        }
        for (Argument arg : internal.preprocArgs) {
            sb.append(" ").append(stringifyArg(arg.toString()));
        }
        return internal.command + sb;
    }
}
