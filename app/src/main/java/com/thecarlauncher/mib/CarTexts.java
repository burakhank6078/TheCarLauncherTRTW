package com.thecarlauncher.mib;

import android.content.Context;

import com.thecarlauncher.car.VehicleState;

import java.util.ArrayList;
import java.util.List;

/** The door and belt texts shared by the status page and the driving warning. */
public final class CarTexts {
    private CarTexts() {}

    /** Name of an open door; index in the order of {@link VehicleState#DOOR_ORDER}. */
    public static String doorName(Context c, int index) {
        switch (index) {
            case 0: return Mib.t(c, "Vrata vozača otvorena", "Driver door open");
            case 1: return Mib.t(c, "Vrata suvozača otvorena", "Passenger door open");
            case 2: return Mib.t(c, "Zadnja leva vrata otvorena", "Rear left door open");
            case 3: return Mib.t(c, "Zadnja desna vrata otvorena", "Rear right door open");
            default: return Mib.t(c, "Prtljažnik otvoren", "Trunk open");
        }
    }

    public static String beltText(Context c) {
        return Mib.t(c, "Pojas vozača nije vezan", "Driver seat belt not fastened");
    }

    /** The open doors and the belt as text lines; with more than one door open the first line says so. */
    public static List<String> lines(Context c, int doors, boolean beltOff) {
        List<String> out = new ArrayList<>();
        int open = 0;
        for (int bit : VehicleState.DOOR_ORDER) if (bit != VehicleState.DOOR_TRUNK && (doors & bit) != 0) open++;
        if (open > 1) {
            out.add(Mib.t(c, "Više vrata je otvoreno", "Several doors open"));
        } else {
            for (int i = 0; i < VehicleState.DOOR_ORDER.length; i++) {
                int bit = VehicleState.DOOR_ORDER[i];
                if (bit != VehicleState.DOOR_TRUNK && (doors & bit) != 0) out.add(doorName(c, i));
            }
        }
        if ((doors & VehicleState.DOOR_TRUNK) != 0) out.add(doorName(c, VehicleState.DOOR_ORDER.length - 1));
        if (beltOff) out.add(beltText(c));
        return out;
    }
}
