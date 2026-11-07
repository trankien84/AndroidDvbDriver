// SPDX-License-Identifier: GPL-2.0-or-later
package info.martinmarinov.dvbdriver;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class Tbs5530PermissionManifestTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private File source(String path) {
        File module = new File("src").isDirectory() ? new File(".") : new File("app");
        return new File(module, path);
    }
    private Document xml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(source(path));
    }
    @Test public void filterMatchesOnlyExactTbsVidPid() throws Exception {
        NodeList devices = xml("src/debug/res/xml/tbs5530_autonomous_device_filter.xml").getElementsByTagName("usb-device");
        assertEquals(1, devices.getLength()); Element device = (Element) devices.item(0);
        assertEquals(2, device.getAttributes().getLength());
        assertEquals("29516", device.getAttribute("vendor-id")); assertEquals("21808", device.getAttribute("product-id"));
    }
    @Test public void debugEntrypointHasExactFilterAndSingleTopWithoutGrantPermission() throws Exception {
        NodeList activities = xml("src/debug/AndroidManifest.xml").getElementsByTagName("activity");
        int found = 0;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (!activity.getAttributeNS(ANDROID, "name").endsWith("Tbs5530AutonomousDiagnosticActivity")) continue;
            found++; assertEquals("true", activity.getAttributeNS(ANDROID, "exported"));
            assertEquals("singleTop", activity.getAttributeNS(ANDROID, "launchMode"));
            NodeList actions = activity.getElementsByTagName("action"); assertEquals(1, actions.getLength());
            assertEquals("android.hardware.usb.action.USB_DEVICE_ATTACHED", ((Element) actions.item(0)).getAttributeNS(ANDROID, "name"));
            NodeList metadata = activity.getElementsByTagName("meta-data"); assertEquals(1, metadata.getLength());
            assertEquals("android.hardware.usb.action.USB_DEVICE_ATTACHED", ((Element) metadata.item(0)).getAttributeNS(ANDROID, "name"));
            assertEquals("@xml/tbs5530_autonomous_device_filter", ((Element) metadata.item(0)).getAttributeNS(ANDROID, "resource"));
        }
        assertEquals(1, found);
    }
    @Test public void preflightIsExplicitDebugEntrypointWithoutUsbDefaultFilter() throws Exception {
        NodeList activities = xml("src/debug/AndroidManifest.xml").getElementsByTagName("activity");
        int found = 0;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (!activity.getAttributeNS(ANDROID, "name").endsWith("Tbs5530PermissionPreflightActivity")) continue;
            found++; assertEquals("true", activity.getAttributeNS(ANDROID, "exported"));
            assertEquals(0, activity.getElementsByTagName("intent-filter").getLength());
            assertEquals(0, activity.getElementsByTagName("meta-data").getLength());
        }
        assertEquals(1, found);
    }
    @Test public void productionManifestAndResourcesDoNotReferenceDebugHandler() throws Exception {
        Document main = xml("src/main/AndroidManifest.xml");
        NodeList activities = main.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            String name = ((Element) activities.item(i)).getAttributeNS(ANDROID, "name");
            assertFalse(name.contains("Tbs5530Autonomous")); assertFalse(name.contains("Tbs5530PermissionPreflight"));
        }
        assertFalse(source("src/main/res/xml/tbs5530_autonomous_device_filter.xml").exists());
        assertFalse(source("src/release/res/xml/tbs5530_autonomous_device_filter.xml").exists());
    }
}
