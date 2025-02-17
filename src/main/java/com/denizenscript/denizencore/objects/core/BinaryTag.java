package com.denizenscript.denizencore.objects.core;

import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.AsciiMatcher;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BinaryTag implements ObjectTag {

    // <--[ObjectType]
    // @name BinaryTag
    // @prefix binary
    // @base ElementTag
    // @ExampleTagBase binary[ff000010]
    // @ExampleValues ff0000,48454c4c4f20574f524c44
    // @ExampleForReturns
    // - filewrite path:data/mypath.dat data:%VALUE%
    // @format
    // The identity format for BinaryTag is a hex encoding of the byte set, in order.
    // Each byte is encoding as an individual big-endian hexadecimal pair, like "FF" for byte=255, "00" for byte=0, "0F" for 15, "F0" for 240, ... etc.
    //
    // @description
    // BinaryTags represent raw binary data in Denizen.
    // This is useful in particular for file or network interop.
    // Generally you should only be interacting with BinaryTag if you have a specific reason to, otherwise there are usually more appropriate types to use.
    // Most content you'll come across will be encoded in a text-based format, not a binary one.
    //
    // -->

    public static byte[] EMPTY = new byte[0];

    @Fetchable("binary")
    public static BinaryTag valueOf(String string, TagContext context) {
        if (string == null) {
            return null;
        }
        string = CoreUtilities.toLowerCase(string);
        if (string.startsWith("binary@")) {
            string = string.substring("binary@".length());
        }
        if (string.isEmpty()) {
            return new BinaryTag(EMPTY);
        }
        if (!VALID_HEX.isOnlyMatches(string)) {
            return null;
        }
        if (string.length() % 2 == 1) {
            return null;
        }
        return new BinaryTag(CoreUtilities.hexDecode(string));
    }

    public static AsciiMatcher VALID_HEX = new AsciiMatcher(AsciiMatcher.DIGITS + "abcdefABCDEF");

    public static boolean matches(String string) {
        try {
            if (string.startsWith("binary@")) {
                return true;
            }
            if (!VALID_HEX.isOnlyMatches(string)) {
                return false;
            }
            if (string.length() % 2 == 1) {
                return false;
            }
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    public byte[] data;

    private String prefix = "Binary";

    public BinaryTag(byte[] data) {
        this.data = data;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public ObjectTag setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public static String hexEncode(byte[] data, boolean spaced) {
        if (!spaced) {
            return CoreUtilities.hexEncode(data);
        }
        char[] output = new char[data.length * 3];
        for (int i = 0; i < data.length; i++) {
            byte valA = (byte) ((data[i] & 0xF0) >> 4);
            byte valB = (byte) (data[i] & 0x0F);
            output[i * 3] = CoreUtilities.charForByte[valA];
            output[(i * 3) + 1] = CoreUtilities.charForByte[valB];
            output[(i * 3) + 2] = ' ';
        }
        return new String(output);
    }

    @Override
    public String debuggable() {
        return "binary@<GR>" + hexEncode(data, true);
    }

    @Override
    public boolean isUnique() {
        return false;
    }

    @Override
    public String getObjectType() {
        return "binary";
    }

    @Override
    public String identify() {
        return "binary@" + hexEncode(data, false);
    }

    @Override
    public String identifySimple() {
        return identify();
    }

    @Override
    public String toString() {
        return identify();
    }

    public static void registerTags() {

        // <--[tag]
        // @attribute <BinaryTag.length>
        // @returns ElementTag(Number)
        // @description
        // Returns the number of bytes in this BinaryTag.
        // @example
        // # Narrates 3
        // - narrate <binary[010203].length>
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "length", (attribute, object) -> {
            return new ElementTag(object.data.length);
        });

        // <--[tag]
        // @attribute <BinaryTag.to_hex>
        // @returns ElementTag
        // @description
        // Returns a flat hexadecimal encoding of the binary data.
        // @example
        // # Narrates 010203
        // - narrate <binary[010203].to_hex>
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "to_hex", (attribute, object) -> {
            return new ElementTag(hexEncode(object.data, false));
        });

        // <--[tag]
        // @attribute <BinaryTag.to_base64>
        // @returns ElementTag
        // @description
        // Returns a base64 encoding of the binary data.
        // See also <@link tag ElementTag.base64_to_binary>
        // @example
        // - define data <binary[48454c4c4f20574f524c44]>
        // - define encoded <[data].to_base64>
        // - define decoded <[encoded].base64_to_binary>
        // - if <[decoded].to_hex> == <[data].to_hex>:
        //     - narrate "Everything works!"
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "to_base64", (attribute, object) -> {
            return new ElementTag(Base64.getEncoder().encodeToString(object.data));
        });

        // <--[tag]
        // @attribute <BinaryTag.utf8_decode>
        // @returns ElementTag
        // @description
        // Returns the raw text represented by this binary data, decoding using the standard UTF-8 encoding.
        // See also <@link tag ElementTag.utf8_encode>
        // @example
        // # narrates "HELLO WORLD"
        // - narrate <binary[48454c4c4f20574f524c44].utf8_decode>
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "utf8_decode", (attribute, object) -> {
            return new ElementTag(new String(object.data, StandardCharsets.UTF_8));
        });

        // <--[tag]
        // @attribute <BinaryTag.text_decode[<encoding>]>
        // @returns ElementTag
        // @description
        // Returns the raw text represented by this binary data, decoding using the specified encoding method.
        // Input can be for example "utf-8" or "iso-8859-1".
        // See also <@link tag ElementTag.text_encode>
        // @example
        // # narrates "HELLO WORLD"
        // - narrate <binary[48454c4c4f20574f524c44].text_decode[us-ascii]>
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "text_decode", (attribute, object) -> {
            if (!attribute.hasParam()) {
                return null;
            }
            try {
                return new ElementTag(new String(object.data, attribute.getParam()));
            }
            catch (UnsupportedEncodingException ex) {
                attribute.echoError("Invalid encoding '" + attribute.getParam() + "'");
                return null;
            }
        });

        // <--[tag]
        // @attribute <BinaryTag.decode_integer>
        // @returns ElementTag(Number)
        // @description
        // Returns the integer number represented by this binary data.
        // Data must be 1, 2, 4, or 8 bytes long.
        // Uses big-endian twos-complement format.
        // See also <@link tag ElementTag.integer_to_binary>
        // @example
        // # Narrates '255'
        // - narrate <binary[000000ff].decode_integer>
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "decode_integer", (attribute, object) -> {
            if (object.data.length == 1) {
                return new ElementTag(object.data[0]);
            }
            if (object.data.length > 8) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(object.data);
            if (object.data.length == 2) {
                return new ElementTag(buffer.getShort());
            }
            else if (object.data.length == 4) {
                return new ElementTag(buffer.getInt());
            }
            if (object.data.length == 8) {
                return new ElementTag(buffer.getLong());
            }
            return null;
        });

        // <--[tag]
        // @attribute <BinaryTag.gzip_compress>
        // @returns BinaryTag
        // @description
        // Returns the binary data, compressed via gzip.
        // See also <@link tag BinaryTag.gzip_decompress>
        // @example
        // - define data <binary[48454c4c4f20574f524c44]>
        // - define compressed <[data].gzip_compress>
        // - define decompressed <[data].gzip_decompress>
        // - if <[decompressed].to_hex> == <[data].to_hex>:
        //     - narrate "Everything works!"
        // -->
        tagProcessor.registerStaticTag(BinaryTag.class, "gzip_compress", (attribute, object) -> {
            return new BinaryTag(compressGzip(object.data));
        });

        // <--[tag]
        // @attribute <BinaryTag.gzip_decompress>
        // @returns BinaryTag
        // @description
        // Returns the binary data, compressed via gzip.
        // See also <@link tag BinaryTag.gzip_compress>
        // @example
        // - define data <binary[48454c4c4f20574f524c44]>
        // - define compressed <[data].gzip_compress>
        // - define decompressed <[data].gzip_decompress>
        // - if <[decompressed].to_hex> == <[data].to_hex>:
        //     - narrate "Everything works!"
        // -->
        tagProcessor.registerStaticTag(BinaryTag.class, "gzip_decompress", (attribute, object) -> {
            return new BinaryTag(decompressGzip(object.data));
        });
    }

    private static byte[] decompressGzip(byte[] data) {
        try {
            ByteArrayInputStream bytesIn = new ByteArrayInputStream(data);
            GZIPInputStream gzipIn = new GZIPInputStream(bytesIn);
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer, 0, 1024)) != -1) {
                bytesOut.write(buffer, 0, len);
            }
            byte[] result = bytesOut.toByteArray();
            gzipIn.close();
            bytesIn.close();
            bytesOut.close();
            return result;
        }
        catch (IOException ex) {
            Debug.echoError(ex);
            return null;
        }
    }

    private static byte[] compressGzip(byte[] data) {
        try {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(data.length);
            GZIPOutputStream gzipOut = new GZIPOutputStream(bytesOut);
            gzipOut.write(data);
            gzipOut.finish();
            byte[] result = bytesOut.toByteArray();
            gzipOut.close();
            bytesOut.close();
            return result;
        }
        catch (IOException ex) {
            Debug.echoError(ex);
            return null;
        }
    }

    public static ObjectTagProcessor<BinaryTag> tagProcessor = new ObjectTagProcessor<>();

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }
}
