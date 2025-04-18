package net.sf.l2j.aspect;

import net.sf.l2j.gameserver.taskmanager.HappyHourTaskManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AddExpAspect {

    @Pointcut("execution(* net.sf.l2j.gameserver.model.actor.status.PlayerStatus.addExpAndSp(long, int))")
    public void addExpAndSpPointCut() {
    }

    @Around("addExpAndSpPointCut()")
    public Object addExpAndSpAround(ProceedingJoinPoint joinPoint) throws Throwable {
        var exp = (long) joinPoint.getArgs()[0];
        var sp = (int) joinPoint.getArgs()[1];

        if (HappyHourTaskManager.getInstance().isHappyHourRunning()) {
            exp = (long) (exp * CustomConfig.HAPPY_HOUR_EXP_MUL);
            sp = (int) (sp * CustomConfig.HAPPY_HOUR_SP_MUL);
        }

        return joinPoint.proceed(new Object[]{exp, sp});
    }
}