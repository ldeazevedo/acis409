package net.sf.l2j.gameserver.scripting.script.maker;

import net.sf.l2j.commons.util.ArraysUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.spawn.MultiSpawn;
import net.sf.l2j.gameserver.model.spawn.NpcMaker;
import net.sf.l2j.gameserver.model.spawn.SpawnData;

public class EventMaker extends DefaultMaker
{
	public EventMaker(String name)
	{
		super(name);
	}
	
	@Override
	public void onStart(NpcMaker maker)
	{
		if (shouldSpawn(maker))
		{
			for (MultiSpawn ms : maker.getSpawns())
			{
				if (ms.getTotal() != ms.getSpawned())
				{
					if (ms.getSpawnData() != null)
						ms.loadDBNpcInfo();
					else
					{
						long toSpawnCount = ms.getTotal() - ms.getSpawned();
						for (long i = 0; i < toSpawnCount; i++)
							if (maker.getMaximumNpc() - maker.getNpcsAlive() > 0)
								ms.doSpawn(false);
					}
				}
			}
		}
	}
	
	@Override
	public void onNpcDBInfo(MultiSpawn ms, SpawnData spawnData, NpcMaker maker)
	{
		// Do nothing.
	}
	
	@Override
	public void onNpcCreated(Npc npc, MultiSpawn ms, NpcMaker maker)
	{
		if (!shouldSpawn(maker))
			npc.deleteMe();
	}
	
	@Override
	public void onNpcDeleted(Npc npc, MultiSpawn ms, NpcMaker maker)
	{
		if (!shouldSpawn(maker))
			return;
		
		if (npc.getSpawn().getRespawnDelay() != 0)
			npc.scheduleRespawn(npc.getSpawn().calculateRespawnDelay() * 1000);
	}
	
	private static boolean shouldSpawn(NpcMaker maker)
	{
		return ArraysUtil.contains(Config.SPAWN_EVENTS, maker.getMakerMemo().get("EventName"));
	}
}