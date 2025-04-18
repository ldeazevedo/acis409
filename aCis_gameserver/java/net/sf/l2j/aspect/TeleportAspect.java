package net.sf.l2j.aspect;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.actor.Player;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Objects;

@Aspect
public class TeleportAspect {

    @Pointcut("execution(* net.sf.l2j.gameserver.model.actor.Npc.teleportTo(Object, int))")
    public void teleportPointcut() {
    }

    @Around("teleportPointcut()")
    public Object addExpAndSpAround(ProceedingJoinPoint joinPoint) throws Throwable {
        var player = (Player) joinPoint.getArgs()[0];
        if (Objects.isNull(player))
            return null;

        if (player.getStatus().getLevel() < 40) {
            player.sendMessage("You are too newbie, we are granting you free teleport.");
            Config.FREE_TELEPORT = true;
        }

        var output = joinPoint.proceed(joinPoint.getArgs());
        Config.FREE_TELEPORT = false;
        return output;
    }
}