package com.denizenscript.denizencore.flags;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.*;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.utilities.Deprecations;
import com.denizenscript.denizencore.utilities.debugging.SlowWarning;
import com.denizenscript.denizencore.utilities.debugging.Warning;

import java.util.Collection;

public abstract class AbstractFlagTracker {

    public abstract MapTag getRootMap(String key);

    public abstract void setRootMap(String key, MapTag map);

    public abstract ObjectTag getFlagValue(String key);

    public abstract TimeTag getFlagExpirationTime(String key);

    public abstract Collection<String> listAllFlags();

    public void setFlag(String key, ObjectTag value, TimeTag expiration) {
        setFlag(key, value, expiration, true);
    }

    public abstract void setFlag(String key, ObjectTag value, TimeTag expiration, boolean doFlaggify);

    public boolean hasFlag(String key) {
        return getFlagValue(key) != null;
    }

    public static <T extends FlaggableObject> void registerFlagHandlers(ObjectTagProcessor<T> processor) {

        // <--[tag]
        // @attribute <FlaggableObject.flag[<flag_name>]>
        // @returns ObjectTag
        // @description
        // Returns the specified flag from the flaggable object.
        // If the flag is expired, will return null.
        // Consider also using <@link tag FlaggableObject.has_flag>.
        // See <@link language flag system>.
        // -->
        processor.registerTag(ObjectTag.class, "flag", (attribute, object) -> {
            AbstractFlagTracker tracker = object.getFlagTrackerForTag();
            if (tracker == null) {
                attribute.echoError("Cannot read flag tag for '" + object + "': " + object.getReasonNotFlaggable());
                return null;
            }
            return tracker.doFlagTag(attribute);
        });

        // <--[tag]
        // @attribute <FlaggableObject.has_flag[<flag_name>]>
        // @returns ElementTag(Boolean)
        // @description
        // Returns true if the flaggable object has the specified flag, otherwise returns false.
        // See <@link language flag system>.
        // -->
        processor.registerTag(ElementTag.class, "has_flag", (attribute, object) -> {
            AbstractFlagTracker tracker = object.getFlagTrackerForTag();
            if (tracker == null) {
                attribute.echoError("Cannot read has_flag tag for '" + object + "': " + object.getReasonNotFlaggable());
                return null;
            }
            return tracker.doHasFlagTag(attribute);
        });

        // <--[tag]
        // @attribute <FlaggableObject.flag_expiration[<flag_name>]>
        // @returns TimeTag
        // @description
        // Returns a TimeTag indicating when the specified flag will expire.
        // See <@link language flag system>.
        // -->
        processor.registerTag(TimeTag.class, "flag_expiration", (attribute, object) -> {
            AbstractFlagTracker tracker = object.getFlagTrackerForTag();
            if (tracker == null) {
                attribute.echoError("Cannot read flag_expiration tag for '" + object + "': " + object.getReasonNotFlaggable());
                return null;
            }
            return tracker.doFlagExpirationTag(attribute);
        });

        // <--[tag]
        // @attribute <FlaggableObject.list_flags>
        // @returns ListTag
        // @description
        // Returns a list of the flaggable object's flags.
        // Note that this is exclusively for debug/testing reasons, and should never be used in a real script.
        // See <@link language flag system>.
        // -->
        processor.registerTag(ListTag.class, "list_flags", (attribute, object) -> {
            AbstractFlagTracker tracker = object.getFlagTrackerForTag();
            if (tracker == null) {
                attribute.echoError("Cannot read list_flags tag for '" + object + "': " + object.getReasonNotFlaggable());
                return null;
            }
            return tracker.doListFlagsTag(attribute);
        });

        // <--[tag]
        // @attribute <FlaggableObject.flag_map[<name>|...]>
        // @returns MapTag
        // @description
        // Returns a raw map of the objects internal flag data for the flags with the given flag name. Names must be root names (no '.').
        // Output is a MapTag wherein each key is a flag name, and each value is a MapTag, containing keys '__value' and '__expiration', where '__value' contains the real object value.
        // Output also may contain key '__clear', which is a ListTag of flags that were listed in input but weren't present in output.
        // Using this without a parameter to get ALL flags is allowed exclusively for debug/testing reasons, and should never be used in a real script.
        // See <@link language flag system>.
        // -->
        processor.registerTag(MapTag.class, "flag_map", (attribute, object) -> {
            AbstractFlagTracker tracker = object.getFlagTrackerForTag();
            if (tracker == null) {
                attribute.echoError("Cannot read flag_map tag for '" + object + "': " + object.getReasonNotFlaggable());
                return null;
            }
            return tracker.doFlagMapTag(attribute);
        });
    }

    public ElementTag doHasFlagTag(Attribute attribute) {
        if (!attribute.hasParam()) {
            attribute.echoError("The has_flag[...] tag must have an input!");
            return null;
        }
        return new ElementTag(hasFlag(attribute.getParam()));
    }

    public ObjectTag doFlagTag(Attribute attribute) {
        if (!attribute.hasParam()) {
            attribute.echoError("The flag[...] tag must have an input!");
            return null;
        }
        if (attribute.getAttributeWithoutParam(2).equals("is_expired")) {
            Deprecations.flagIsExpiredTag.warn(attribute.context);
            boolean result = !hasFlag(attribute.getParam());
            attribute.fulfill(1);
            return new ElementTag(result);
        }
        else if (attribute.getAttributeWithoutParam(2).equals("expiration")) {
            Deprecations.flagExpirationTag.warn(attribute.context);
            TimeTag time = getFlagExpirationTime(attribute.getParam());
            if (time == null) {
                return null;
            }
            attribute.fulfill(1);
            return new DurationTag((time.millis() - TimeTag.now().millis()) / 1000.0);
        }
        return getFlagValue(attribute.getParam());
    }

    public TimeTag doFlagExpirationTag(Attribute attribute) {
        if (!attribute.hasParam()) {
            attribute.echoError("The flag_expiration[...] tag must have an input!");
            return null;
        }
        return getFlagExpirationTime(attribute.getParam());
    }

    public static Warning listFlagsTagWarning = new SlowWarning("listFlagsTagWarning", "The list_flags and flag_map tags are meant for testing/debugging only. Do not use it in scripts (ignore this warning if using for testing reasons).");

    public ListTag doListFlagsTag(Attribute attribute) {
        if (attribute.getScriptEntry() != null && attribute.getScriptEntry().getScript() != null) { // don't warn in '/ex'
            listFlagsTagWarning.warn(attribute.context);
        }
        ListTag list = new ListTag();
        list.addAll(listAllFlags());
        return list;
    }

    public MapTag getFlagMap() {
        MapTag result = new MapTag();
        for (String key : listAllFlags()) {
            result.putObject(key, getRootMap(key));
        }
        return result;
    }

    public MapTag doFlagMapTag(Attribute attribute) {
        if (!attribute.hasParam()) {
            if (attribute.getScriptEntry() != null && attribute.getScriptEntry().getScript() != null) { // don't warn in '/ex'
                listFlagsTagWarning.warn(attribute.context);
            }
            return getFlagMap();
        }
        MapTag result = new MapTag();
        ListTag clear = new ListTag();
        for (String key : attribute.paramAsType(ListTag.class)) {
            MapTag map = getRootMap(key);
            if (map != null) {
                result.putObject(key, map);
            }
            else {
                clear.addObject(new ElementTag(key, true));
            }
        }
        if (!clear.isEmpty()) {
            result.putObject("__clear", clear);
        }
        return result;
    }
}
