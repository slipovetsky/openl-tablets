package org.openl.util;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import org.junit.Assert;
import org.junit.Test;

public class PropertiesUtilsTest {

    @Test
    public void loadEmpty() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader(""), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList(), result);
    }

    @Test
    public void loadNull() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("\n   spaced key1 \n key2\r key3\r\n  #x:y\r\n\r\n key4"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("spaced key1=null", "key2=null", "key3=null", "key4=null"), result);
    }

    @Test
    public void loadComments() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("#com=1\\\nx = 2\\\r\n#34 "), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("x=2#34"), result);
    }

    @Test
    public void loadTheSame() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("x=1\nx : 2 : 3 = 4 \r   \t\f\n\r \t\fx=3\n  \u1111:\u2222\\"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("x=1", "x=2 : 3 = 4", "x=3", "\u1111=\u2222"), result);
    }

    @Test
    public void loadSpecSymbols() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("\tx\t\f \\r\\n\\u0035y#$.,%^&@!+-*/w\\:t+-*/+-_)(*&^%$#@!~`<>,.{}[] = \t\fx\t\f\\t\\f\\n\\r\\u0034+-*/=+-_)(*&^%$#@!~`<>,.{}[]1"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("x\t\f \r\n5y#$.,%^&@!+-*/w:t+-*/+-_)(*&^%$#@!~`<>,.{}[]=x\t\f\t\f\n\r\u0034+-*/=+-_)(*&^%$#@!~`<>,.{}[]1"), result);
    }

    @Test
    public void loadSimple() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("x=1\n\ry=2\r\nz=3 \\"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("x=1", "y=2", "z=3"), result);
    }

    @Test(expected = EOFException.class)
    public void failNotFullUnicode() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(new StringReader("x=1\n\ry=\\u123"), (k, v) -> result.add(k + "=" + v));
    }

    @Test
    public void loadStream() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        try (var file = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-utf8.properties")) {
            PropertiesUtils.load(file, (k, v) -> result.add(k + "=" + v));
        }
        Assert.assertEquals(Arrays.asList("Привет! Это проверка=Пройдено!", "#=#", "hello!=passed ! \r  # not a comment"), result);
    }

    @Test
    public void loadPath() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(Paths.get("test-resources/test-utf8.properties"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("Привет! Это проверка=Пройдено!", "#=#", "hello!=passed ! \r  # not a comment"), result);
    }

    @Test
    public void loadURL() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(Thread.currentThread().getContextClassLoader().getResource("test-utf8.properties"), (k, v) -> result.add(k + "=" + v));
        Assert.assertEquals(Arrays.asList("Привет! Это проверка=Пройдено!", "#=#", "hello!=passed ! \r  # not a comment"), result);
    }

    @Test
    public void load() throws IOException {
        ArrayList<String> result = new ArrayList<>();
        PropertiesUtils.load(Paths.get("test-resources/specs.properties"), (k, v) -> result.add(k + "  ->  " + v));
        Assert.assertEquals(Arrays.asList(
                "website  ->  https://openl-tablets.org/",
                "language  ->  English",
                "whitespace inside a key  ->  like in a value",
                "empty  ->  null",
                "hello  ->  hello",
                "hello  ->  hello",
                "duplicateKey  ->  first",
                "duplicateKey  ->  second",
                "delimiterCharacters:=   ->  This is the value for the key \"delimiterCharacters:= \"",
                "key  ->  value with the whitespaces in the end     ",
                "multiline  ->  This line continues",
                "path  ->  c:\\openl\\rules",
                "evenKey  ->  This is on one line\\",
                "oddKey  ->  This is line one and\\# This is line two",
                "welcome  ->  Welcome to OpenL Tablets!",
                "valueWithEscapes  ->  This is a newline\n and a carriage return\r and a tab\t.",
                "encodedHelloInJapanese  ->  こんにちは",
                "helloInJapanese  ->  こんにちは"
        ), result);
    }

    @Test
    public void storeEmpty() throws IOException {
        var output = new StringWriter();
        var props = new LinkedHashMap<String, String>();

        PropertiesUtils.store(output, props.entrySet());
        Assert.assertEquals("", output.toString());
    }

    @Test
    public void store() throws IOException {
        var output = new StringWriter();
        var props = new LinkedHashMap<String, String>();
        props.put("x", "20 ");
        props.put("#Ж:=+-*/\\", "привет!+-*/\\#");
        props.put("Key with a space", "=Значение :");
        props.put(null, " It is = a comment\\");

        PropertiesUtils.store(output, props.entrySet());
        Assert.assertEquals("x=20 \n" +
                "\\#Ж\\:\\=+-*/\\\\=привет!+-*/\\\\#\n" +
                "Key with a space==Значение :\n" +
                "# It is = a comment\\\n", output.toString());
    }

    @Test
    public void storeStream() throws IOException {
        var output = new ByteArrayOutputStream();
        var props = new LinkedHashMap<Integer, Integer>();
        props.put(2, 20);
        props.put(1, 50);

        PropertiesUtils.store(output, props.entrySet());
        Assert.assertEquals("2=20\n1=50\n", output.toString());
    }

    @Test
    public void storePath() throws IOException {
        Path tempFile = Files.createTempFile("test-utf8", ".properties");
        var result = new LinkedHashMap<String, String>();
        var props = new LinkedHashMap<String, String>();
        props.put("2", "20");
        props.put("1", "50");
        props.put("3", "");
        props.put("Привет! Как дела? :=", "Отлично! :)");

        Assert.assertEquals(0, Files.size(tempFile));
        PropertiesUtils.store(tempFile, props.entrySet());
        Assert.assertEquals(68, Files.size(tempFile));
        PropertiesUtils.load(tempFile, result::put);
        Assert.assertEquals(props, result);
        Files.delete(tempFile);
    }
}
