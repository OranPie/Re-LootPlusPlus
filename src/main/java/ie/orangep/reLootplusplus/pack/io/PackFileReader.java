package ie.orangep.reLootplusplus.pack.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackFileReader {
    private PackFileReader() {
    }

    public static List<String> readLines(ZipFile zip, ZipEntry entry) throws Exception {
        List<String> lines = new ArrayList<>();
        byte[] data;
        try (InputStream stream = zip.getInputStream(entry)) {
            data = stream.readAllBytes();
        }
        String content;
        try {
            content = decodeStrict(data, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            content = decodeLenient(data, StandardCharsets.ISO_8859_1);
        }
        try (BufferedReader buffered = new BufferedReader(new StringReader(content))) {
            boolean first = true;
            String line;
            while ((line = buffered.readLine()) != null) {
                if (first) {
                    line = stripBom(line);
                    first = false;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private static String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private static String decodeStrict(byte[] data, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
    }

    private static String decodeLenient(byte[] data, Charset charset) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(data), charset);
             BufferedReader buffered = new BufferedReader(reader)) {
            StringBuilder sb = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = buffered.readLine()) != null) {
                if (!first) {
                    sb.append('\n');
                }
                sb.append(line);
                first = false;
            }
            return sb.toString();
        }
    }
}
