package net.sf.l2j.aspect;

import net.sf.l2j.gameserver.taskmanager.HappyHourTaskManager;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ConfigLoadedAspect {

    @After("execution(* net.sf.l2j.commons.pool.ThreadPool.init())")
    public void afterLoadThreadPool() {
        CustomConfig.init();

        HappyHourTaskManager.getInstance();
    }
}