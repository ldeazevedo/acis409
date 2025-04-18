package net.sf.l2j.gameserver.taskmanager;

import net.sf.l2j.aspect.CustomConfig;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.model.World;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;

public final class HappyHourTaskManager implements Runnable {

    private static final CLogger log = new CLogger(HappyHourTaskManager.class.getName());

    private boolean isHappyHourRunning = false;

    private HappyHourTaskManager() {
        // Run task each second.
        ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
    }

    @Override
    public void run() {
        if (!CustomConfig.HAPPY_HOUR_ENABLED)
            return;

        var hour = ZonedDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault()).getHour();
        var arrayOfHours = Arrays.stream(CustomConfig.HAPPY_HOUR_TIME_TABLE).boxed().toList();
        var isTimeToRun = arrayOfHours.contains(hour);
        if (isHappyHourRunning != isTimeToRun) {
            if (isTimeToRun) {
                World.announceToOnlinePlayers("HAPPY HOUR just started! Enjoy it!");
                log.info("HAPPY HOUR started.");
            } else {
                World.announceToOnlinePlayers("HAPPY HOUR just finished. Stay tuned for the next time!");
                log.info("HAPPY HOUR finished.");
            }

            isHappyHourRunning = arrayOfHours.contains(hour);
        }
    }

    public static HappyHourTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final HappyHourTaskManager INSTANCE = new HappyHourTaskManager();
    }

    public boolean isHappyHourRunning() {
        return isHappyHourRunning;
    }
}