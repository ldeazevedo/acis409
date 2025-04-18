package net.sf.l2j.gameserver.model.actor.attack;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

/**
 * This class groups all attack data related to a {@link Creature}.
 * @param <T> : The {@link Playable} used as actor.
 */
public class PlayableAttack<T extends Playable> extends CreatureAttack<T>
{
	public PlayableAttack(T actor)
	{
		super(actor);
	}
	
	@Override
	public boolean canAttack(Creature target)
	{
		if (!super.canAttack(target))
			return false;
		
		if (target instanceof Playable targetPlayable)
		{
			if (_actor.isInsideZone(ZoneId.PEACE))
			{
				_actor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_ATK_PEACEZONE));
				return false;
			}
			
			if (targetPlayable.isInsideZone(ZoneId.PEACE))
			{
				_actor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public void stop()
	{
		super.stop();
		
		_actor.getAI().tryToIdle();
	}
}