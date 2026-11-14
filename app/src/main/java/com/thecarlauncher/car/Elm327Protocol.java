package com.thecarlauncher.car;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text side of an ELM327 adapter on the car's OBD-II port, without Android so it can be tested. Only reading: the
 * launcher sends the adapter's own set-up commands (AT...) and Mode 01 requests for current values, and nothing else
 * ({@link #allowed}). The replies are cleaned of echoes and spaces, the bytes of a Mode 01 reply are picked out, and a
 * few well known values are worked out from them (coolant, oil, intake and outside temperature, fuel level, engine load,
 * rpm, speed, control module voltage).
 */
public final class Elm327Protocol {
    /** The Mode 01 values the launcher asks for. */
    public static final int COOLANT = 0x05, LOAD = 0x04, RPM = 0x0C, SPEED = 0x0D, INTAKE = 0x0F, THROTTLE = 0x11,
            FUEL = 0x2F, MODULE_VOLTAGE = 0x42, AMBIENT = 0x46, OIL = 0x5C;

    /** The adapter's own commands: reset, echo off, line feeds off, spaces off, headers off, auto protocol, battery voltage. */
    private static final String[] AT_OK = {"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT1", "ATRV", "ATDP"};

    private Elm327Protocol() {}

    /** True for what may be sent to the adapter: one of the set-up commands above, or "01" and a two digit hex number. */
    public static boolean allowed(String cmd) {
        if (cmd == null) return false;
        String c = cmd.trim().toUpperCase(Locale.US);
        for (String a : AT_OK) if (a.equals(c)) return true;
        return c.length() == 4 && c.startsWith("01") && isHex(c.charAt(2)) && isHex(c.charAt(3));
    }

    private static boolean isHex(char c) { return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'); }

    /** The request for one value, "010C" for the rpm. */
    public static String request(int pid) { return String.format(Locale.US, "01%02X", pid); }

    /** A reply with the prompt, spaces and line ends taken out, upper case. */
    public static String clean(String reply) {
        if (reply == null) return "";
        return reply.replace(">", "").replace("\r", "").replace("\n", "").replace(" ", "").toUpperCase(Locale.US);
    }

    /** True when the adapter says there is nothing to give (no data, no connection to the car, an error). */
    public static boolean isFailure(String reply) {
        String c = clean(reply);
        return c.isEmpty() || c.contains("NODATA") || c.contains("UNABLETOCONNECT") || c.contains("ERROR")
                || c.contains("BUSINIT") || c.contains("STOPPED") || c.contains("?") || c.contains("CANERROR");
    }

    /**
     * The data bytes of the reply to a Mode 01 request ("41 0C 1A F8" gives {0x1A, 0xF8}), or null when the reply is not for
     * that value. Several answering control units, or a reply split in lines, are tolerated: the first matching "41xx"
     * is used.
     */
    public static int[] payload(String reply, int pid) {
        String c = clean(reply);
        String tag = String.format(Locale.US, "41%02X", pid);
        int i = c.indexOf(tag);
        if (i < 0) return null;
        List<Integer> out = new ArrayList<>();
        for (int k = i + 4; k + 1 < c.length() && out.size() < 4; k += 2) {
            try {
                out.add(Integer.parseInt(c.substring(k, k + 2), 16));
            } catch (NumberFormatException e) {
                break;
            }
        }
        if (out.isEmpty()) return null;
        int[] a = new int[out.size()];
        for (int k = 0; k < a.length; k++) a[k] = out.get(k);
        return a;
    }

    /** The value of one Mode 01 request from its data bytes (degrees C, percent, rpm, km/h, volts), NaN when it cannot be told. */
    public static double value(int pid, int[] d) {
        if (d == null || d.length == 0) return Double.NaN;
        int a = d[0], b = d.length > 1 ? d[1] : 0;
        switch (pid) {
            case COOLANT:
            case INTAKE:
            case AMBIENT:
            case OIL:
                return a - 40;
            case LOAD:
            case THROTTLE:
            case FUEL:
                return a * 100.0 / 255.0;
            case RPM:
                return d.length > 1 ? (a * 256 + b) / 4.0 : Double.NaN;
            case SPEED:
                return a;
            case MODULE_VOLTAGE:
                return d.length > 1 ? (a * 256 + b) / 1000.0 : Double.NaN;
            default:
                return Double.NaN;
        }
    }

    /**
     * The values a "supported" request lists: the reply to 0100, 0120, 0140 or 0160 is four bytes, one bit per value, the
     * first bit being base+1. Returns the numbers of the values the car gives, base+1 to base+32.
     */
    public static List<Integer> supported(int base, int[] mask) {
        List<Integer> out = new ArrayList<>();
        if (mask == null) return out;
        for (int byteIx = 0; byteIx < Math.min(4, mask.length); byteIx++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((mask[byteIx] & (0x80 >> bit)) != 0) out.add(base + byteIx * 8 + bit + 1);
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern VOLTS = java.util.regex.Pattern.compile("([0-9]+(?:[.][0-9]+)?)V");

    /** The adapter's battery voltage reply to ATRV ("12.6V", maybe with the echoed command in front), or NaN. */
    public static double voltage(String reply) {
        java.util.regex.Matcher m = VOLTS.matcher(clean(reply));
        if (!m.find()) return Double.NaN;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
