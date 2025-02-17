package com.denizenscript.denizencore.tags.core;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.tags.TagManager;

public class ElementTagBase {

    public ElementTagBase() {

        // <--[tag]
        // @attribute <element[<element>]>
        // @returns ElementTag
        // @description
        // Returns an element constructed from the input value.
        // Refer to <@link objecttype ElementTag>.
        // -->
        TagManager.registerStaticTagBaseHandler(ElementTag.class, "element", (attribute) -> {
            if (!attribute.hasParam()) {
                attribute.echoError("Element tag base must have input.");
                return null;
            }
            return new ElementTag(attribute.getParam());
        });
    }
}
