// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

/** Uses AGP's merged manifest and linked APK resources, not source-manifest approximations. */
public class Tbs5530MergedUsbManifestTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String ATTACH = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String DELEGATE = "info.martinmarinov.dvbservice.UsbDelegate";
    private static final String DEBUG = "info.martinmarinov.dvbdriver.Tbs5530AutonomousDiagnosticActivity";
    private static final int VID = 0x734c, PID = 0x5530;
    private static Document manifest;
    private static File apk, aapt2;
    private static boolean debug;
    private static final Map<String, List<String>> resourcePaths = new HashMap<>();

    private static File input(String key) {
        String path = System.getProperty(key);
        assertNotNull("AGP test input missing: " + key, path);
        File file = new File(path);
        assertTrue("AGP test input does not exist: " + path, file.exists());
        return file;
    }
    @BeforeClass public static void loadMergedArtifacts() throws Exception {
        String variant = System.getProperty("tbs5530.variant");
        assertTrue("Expected debug or release variant", "debug".equals(variant) || "release".equals(variant));
        debug = "debug".equals(variant);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        manifest = factory.newDocumentBuilder().parse(input("tbs5530.manifest"));
        aapt2 = input("tbs5530.aapt2");
        File[] apks = input("tbs5530.apkDir").listFiles((dir, name) -> name.endsWith(".apk"));
        assertNotNull(apks); assertEquals("One APK expected; do not silently pick a split", 1, apks.length);
        apk = apks[0];
        String resource = null;
        for (String line : dump("resources").split("\\R")) {
            Matcher entry = Pattern.compile("^\\s*resource 0x[0-9a-fA-F]+ (\\S+).*").matcher(line);
            if (entry.matches()) { resource = entry.group(1); continue; }
            Matcher file = Pattern.compile("\\(file\\) (\\S+) type=XML").matcher(line);
            if (resource != null && resource.startsWith("xml/") && file.find())
                resourcePaths.computeIfAbsent(resource, key -> new ArrayList<>()).add(file.group(1));
        }
        assertTrue("Linked XML resources missing", resourcePaths.containsKey("xml/device_filter"));
    }
    private static String dump(String... args) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList(aapt2.getAbsolutePath(), "dump"));
        command.addAll(Arrays.asList(args)); command.add(apk.getAbsolutePath());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        FutureTask<String> output = new FutureTask<>(() -> {
            try (InputStream stream = process.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        });
        Thread reader = new Thread(output, "merged-usb-manifest-aapt2"); reader.setDaemon(true); reader.start();
        try {
            assertTrue("AAPT2 timed out", process.waitFor(30, TimeUnit.SECONDS));
            String text = output.get(5, TimeUnit.SECONDS);
            assertEquals(text, 0, process.exitValue());
            return text;
        } finally { process.destroyForcibly(); }
    }
    private static List<Map<String, Integer>> devices(String reference) throws Exception {
        assertTrue("USB metadata must reference XML: " + reference, reference.startsWith("@xml/"));
        List<String> paths = resourcePaths.get(reference.substring(1));
        assertNotNull("Unresolved linked resource: " + reference, paths);
        assertEquals("Unexpected qualified USB filter; verify every configuration", 1, paths.size());
        List<Map<String, Integer>> devices = new ArrayList<>();
        Map<String, Integer> current = null;
        for (String line : dump("xmltree", "--file", paths.get(0)).split("\\R")) {
            if (line.trim().startsWith("E: usb-device ")) {
                current = new HashMap<>(); devices.add(current);
            } else if (line.trim().startsWith("E:")) current = null;
            else if (current != null) {
                Matcher attribute = Pattern.compile("^\\s*A: ([a-z-]+)=(0x[0-9a-fA-F]+|[0-9]+).*").matcher(line);
                if (attribute.matches()) current.put(attribute.group(1), Integer.decode(attribute.group(2)));
                else assertFalse("Unparsed USB filter attribute: " + line, line.trim().startsWith("A:"));
            }
        }
        assertFalse("Empty USB filter: " + reference, devices.isEmpty());
        return devices;
    }
    private static boolean matches(Map<String, Integer> device) {
        return (!device.containsKey("vendor-id") || device.get("vendor-id") == VID)
                && (!device.containsKey("product-id") || device.get("product-id") == PID);
    }
    private static String filter(Element activity) {
        NodeList metadata = activity.getElementsByTagName("meta-data");
        String reference = null;
        for (int i = 0; i < metadata.getLength(); i++) {
            Element entry = (Element) metadata.item(i);
            if (ATTACH.equals(entry.getAttributeNS(ANDROID, "name"))) {
                assertNull("Duplicate USB metadata", reference);
                reference = entry.getAttributeNS(ANDROID, "resource");
            }
        }
        assertNotNull("USB metadata missing on " + activity.getAttributeNS(ANDROID, "name"), reference);
        return reference;
    }
    private static Element activity(String name) {
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element entry = (Element) activities.item(i);
            if (name.equals(entry.getAttributeNS(ANDROID, "name"))) return entry;
        }
        fail("Merged Activity missing: " + name); return null;
    }
    @Test public void mergedManifestAndResolvedResourcesHaveExactlyOneTbsHandler() throws Exception {
        List<String> matches = new ArrayList<>();
        for (String tag : new String[]{"activity", "activity-alias"}) {
            NodeList activities = manifest.getElementsByTagName(tag);
            for (int i = 0; i < activities.getLength(); i++) {
                Element entry = (Element) activities.item(i);
                boolean attached = false;
                NodeList actions = entry.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++)
                    attached |= ATTACH.equals(((Element) actions.item(j)).getAttributeNS(ANDROID, "name"));
                if (!attached) continue;
                for (Map<String, Integer> device : devices(filter(entry))) if (matches(device)) {
                    matches.add(entry.getAttributeNS(ANDROID, "name")); break;
                }
            }
        }
        assertEquals(Collections.singletonList(debug ? DEBUG : DELEGATE), matches);
        if (debug) {
            assertEquals("singleTop", activity(DEBUG).getAttributeNS(ANDROID, "launchMode"));
            assertEquals("true", activity(DEBUG).getAttributeNS(ANDROID, "exported"));
            Map<String, Integer> exact = new HashMap<>(); exact.put("vendor-id", VID); exact.put("product-id", PID);
            assertEquals(Collections.singletonList(exact), devices(filter(activity(DEBUG))));
        }
    }
    @Test public void delegatePreservesLegacyFiltersAndExcludesOnlyTbsInDebug() throws Exception {
        List<Map<String, Integer>> original = devices("@xml/device_filter");
        assertEquals(1, original.stream().filter(Tbs5530MergedUsbManifestTest::matches).count());
        List<Map<String, Integer>> expected = new ArrayList<>(original);
        if (debug) expected.removeIf(Tbs5530MergedUsbManifestTest::matches);
        assertEquals(debug ? "@xml/device_filter_debug" : "@xml/device_filter", filter(activity(DELEGATE)));
        assertEquals(expected, devices(filter(activity(DELEGATE))));
    }
    @Test public void releaseDoesNotContainAnyDebugActivity() {
        if (debug) return;
        List<String> forbidden = Arrays.asList("Tbs5530ReloadInitActivity", "Tbs5530HistoryRecoveryActivity",
                "Tbs5530AutonomousDiagnosticActivity", "Tbs5530PermissionPreflightActivity",
                "Tbs5530UsbDiagnosticActivity", "Tbs5530OperationalDiagnosticActivity");
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            String name = ((Element) activities.item(i)).getAttributeNS(ANDROID, "name");
            assertFalse("Debug Activity leaked into release: " + name, forbidden.contains(name.substring(name.lastIndexOf('.') + 1)));
        }
        assertFalse(resourcePaths.containsKey("xml/device_filter_debug"));
        assertFalse(resourcePaths.containsKey("xml/tbs5530_autonomous_device_filter"));
    }
}
