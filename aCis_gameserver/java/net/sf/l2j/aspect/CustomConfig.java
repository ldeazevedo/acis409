package net.sf.l2j.aspect;

import net.sf.l2j.Config;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;

public class CustomConfig {

    private static final CLogger log = new CLogger(CustomConfig.class.getName());

    private CustomConfig() {
        throw new IllegalStateException("Utility class");
    }

    //--- HAPPY HOUR
    public static boolean HAPPY_HOUR_ENABLED;
    public static double HAPPY_HOUR_EXP_MUL;
    public static double HAPPY_HOUR_SP_MUL;
    public static int[] HAPPY_HOUR_TIME_TABLE;

    public static void init() {
        var customConfig = Config.initProperties("./config/custom.properties");

        HAPPY_HOUR_ENABLED = customConfig.getProperty("HappyHourEnabled", false);
        HAPPY_HOUR_EXP_MUL = customConfig.getProperty("HappyHourExpMul", 1.5);
        HAPPY_HOUR_SP_MUL = customConfig.getProperty("HappyHourSpMul", 1.5);
        HAPPY_HOUR_TIME_TABLE = customConfig.getProperty("HappyHourTimeTable", (int[]) null, ",");

        StringUtil.printSection("Custom Config");
        log.info("Custom config loaded.");
    }
}