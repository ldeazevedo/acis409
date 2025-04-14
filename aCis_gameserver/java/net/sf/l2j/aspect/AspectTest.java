package net.sf.l2j.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AspectTest {

    @Pointcut("execution(* net.sf.l2j.gameserver.GameServer.main(..))")
    public void gameServerInitPointcut() {
    }

    @Around("gameServerInitPointcut()")
    public Object gameServerInitAround(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("Before gameServerInitAround");
        var output = joinPoint.proceed();
        System.out.println("After gameServerInitAround");
        return output;
    }
}