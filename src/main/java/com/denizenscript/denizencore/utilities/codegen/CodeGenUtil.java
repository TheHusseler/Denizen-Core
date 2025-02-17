package com.denizenscript.denizencore.utilities.codegen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.utilities.AsciiMatcher;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import org.objectweb.asm.Type;

public class CodeGenUtil {

    public static AsciiMatcher TAG_NAME_PERMITTED = new AsciiMatcher(AsciiMatcher.LETTERS_LOWER + AsciiMatcher.LETTERS_UPPER + AsciiMatcher.DIGITS + "_");

    public static final String TAG_GEN_PACKAGE = "com/denizenscript/tag_gen/";
    public static final String ATTRIBUTE_TYPE_PATH = Type.getInternalName(Attribute.class);
    public static final String ATTRIBUTE_LOCAL_TYPE = "L" + ATTRIBUTE_TYPE_PATH + ";";
    public static final String OBJECT_TAG_PATH = Type.getInternalName(ObjectTag.class);
    public static final String OBJECT_LOCAL_TYPE = "L" + OBJECT_TAG_PATH + ";";

    public static DynamicClassLoader loader = new DynamicClassLoader();

    public static class DynamicClassLoader extends ClassLoader {
        public Class<?> define(String className, byte[] bytecode) {
            Class<?> clazz = super.defineClass(className, bytecode, 0, bytecode.length);
            resolveClass(clazz);
            DenizenCore.implementation.saveClassToLoader(clazz);
            return clazz;
        }
        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return Class.forName(name);
            }
            catch (ClassNotFoundException ex) {
                Debug.echoError(ex);
                return super.findClass(name);
            }
        }
    }
}
