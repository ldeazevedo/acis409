package net.sf.l2j.gameserver.model.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.stream.IntStream;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.data.xml.ScriptData;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.handler.admincommandhandlers.AdminEditChar;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.events.tvt.TvTEvent;
import net.sf.l2j.gameserver.model.events.tvt.TvTEventTeleporter;
import net.sf.l2j.gameserver.model.events.tvt.TvTManager;
import net.sf.l2j.gameserver.model.events.util.EventConstants;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.GMHennaInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewItemList;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class TextCommandHandler
{
	protected static final CLogger log = new CLogger(TextCommandHandler.class.getName());

	private static Player pShift;

	public static boolean process(String text, Player player)
	{
		text = text.toLowerCase();
		//	final Player target = player.getTarget().getActingPlayer();
		switch (text)
		{
			case EventConstants.REGISTER:
			case EventConstants.UNREGISTER:
			case EventConstants.WATCH:
			case EventConstants.EXIT:
				log.info("Event command is being processed");
				NewEventManager.getInstance().processCommand(text, player);
				return true;
		}

		if (!TvTEvent.isInactive())
		{
			if (text.equals(".register"))
			{
				TvTEvent.onBypass("tvt_event_participation", player);
				return true;
			} else if (text.equals(".tvt"))
			{
				TvTEvent.addParticipant(player);
				return true;
			}
		}
		if (text.equals(".hair"))
		{
	//		player.setSwitchHair(!player.getHair());
			return true;
		}
		if (text.equals(".register") || text.equals(".unregister") || text.equals(".ver") || text.equals(".salir"))
		{
			//	EventManager.getInstance().checkEvents(text, player);
			return true;
		}

		if (text.equals(".expoff"))
		{
			player.invertExpOff();
			return true;
		}

		if (player.isGM())
		{
			if (text.equals(".stopvita"))
			{
//				player.setEffectVita();
//				player.stopAbnormalEffect(AbnormalEffect.VITALITY);
				return true;
			}
			if (text.equals(".heading"))
			{
				player.sendMessage("GetHeading: " + player.getHeading());
				return true;
			}
			if (text.equals(".test"))
			{
				int[] midpoint = calculateMidpoint(player.getPosition().toString(), player.getTarget().getPosition().toString());

				player.sendMessage("Midpoint: (" + midpoint[0] + ", " + midpoint[1] + ", " + midpoint[2] + ")");
				log.info("Midpoint: ({}, {}, {})", midpoint[0], midpoint[1], midpoint[2]);
				return true;
			}
			if (text.startsWith(".setinstance"))
			{
				final StringTokenizer st = new StringTokenizer(text, " ");
				st.nextToken();

				if (!st.hasMoreTokens())
				{
					player.sendMessage("Usage: .setinstance <id>");
					return true;
				}

				final WorldObject targetWorldObject = player.getTarget();

				final String param = st.nextToken();
				if (StringUtil.isDigit(param))
				{
					final int id = Integer.parseInt(param);
					if (targetWorldObject != null)
					{
						targetWorldObject.setInstanceId(id);
						targetWorldObject.decayMe();
						targetWorldObject.spawnMe();
						player.sendMessage("You successfully set in Instance " + id);
					}
				}
				return true;
			}
		/*	Player target = player.getTarget() instanceof Player ? (Player) player.getTarget() : null;
			if (text.equals(".pc"))
			{
				if (target != null)
					player.sendMessage(target.getName() + " tiene [" + target.getPcBangScore() + "] PC BANG SCORE");
				return;
			}
			else */
			switch (text)
			{
				case ".tvt_add":
					if (!(player.getTarget() instanceof Player))
					{
						player.sendMessage("You should select a player!");
						return true;
					}

					add(player, player.getTarget().getActingPlayer());
					break;
				case ".tvt_start":
					TvTManager.getInstance().startTvT();
					return true;
				case ".tvt_remove":
					if (!(player.getTarget() instanceof Player))
					{
						player.sendMessage("You should select a player!");
						return true;
					}

					remove(player, player.getTarget().getActingPlayer());
					break;
				case ".tvt_advance":
					TvTManager.getInstance().skipDelay();
					return true;
				case ".frintezza":
					ScriptData.getInstance().getQuest("Frintezza").startQuestTimer("start", null, null, 1000);
					return true;
				case ".read":
					player.setReadChat(!player.getReadChat());
					player.sendMessage("Read chats " + (!player.getReadChat() ? "off" : "on"));
					return true;
			}

			if (text.startsWith(".clanchat"))
			{
				try
				{
					var st = new StringTokenizer(text);
					var clanName = st.nextToken();
					var message = new StringBuilder();
					while (st.hasMoreTokens())
						message.append(st.nextToken()).append(" ");
					var receiverClan = ClanTable.getInstance().getClans().stream().filter(c -> c.getName().equalsIgnoreCase(clanName)).findFirst();
					if (receiverClan.isPresent())
					{
						receiverClan.get().broadcastToMembers(new CreatureSay(player.getObjectId(), SayType.CLAN, player.getName(), message.toString()));
						player.sendPacket(new CreatureSay(player.getObjectId(), SayType.ALLIANCE, player.getName(), "[" + receiverClan.get().getName() + "]:" + message));
					}
				} catch (Exception e)
				{
					log.error("Error when trying to use .chat|.all|.clan - ", e);
					player.sendMessage("Usage: .clanchat <clanname> [text]");
				}
				return true;
			}
			if (text.startsWith(".chat") || text.startsWith(".all") || text.startsWith(".clan"))
			{
				boolean global = text.startsWith(".all");
				boolean clan = text.startsWith(".clan");
				StringTokenizer st = new StringTokenizer(text);
				try
				{
					final String charName = st.nextToken();
					StringBuilder message = new StringBuilder();
					while (st.hasMoreTokens())
						message.append(st.nextToken()).append(" ");
					Player victim = World.getInstance().getPlayer(charName);
					if (victim != null)
					{
						if (!clan)
						{
							var cs = new CreatureSay(victim.getObjectId(), !global ? SayType.ALL : SayType.TRADE, victim.getName(), message.toString());
							if (global)
								World.getInstance().getPlayers().forEach(p -> p.sendPacket(cs));
							else
								victim.getKnownTypeInRadius(Player.class, 1250).stream().filter(Objects::nonNull).forEach(p -> {
									p.sendPacket(cs);
									victim.sendPacket(cs);
								});
						} else if (victim.getClan() != null)
						{
							AdminData.getInstance().broadcastToGMs(new CreatureSay(victim.getObjectId(), SayType.ALLIANCE, victim.getName(), "[" + victim.getClan().getName() + "]:" + message));
							victim.getClan().broadcastToMembers(new CreatureSay(victim.getObjectId(), SayType.CLAN, victim.getName(), message.toString()));
						}
					}
				} catch (Exception e)
				{
					log.error("Error when trying to use .chat|.all|.clan - ", e);
					player.sendMessage("Usage: .clanchat <clanname> [text]");
				}
				return true;
			}
		}
		return false;
	}

	public void bypass(Player client, String command)
	{
		final Player player = client.getActingPlayer();
		if (player == null)
			return;
		NpcHtmlMessage html = new NpcHtmlMessage(player.getObjectId());
		final Player shift = player.getShiftTarget();
		if (command.equalsIgnoreCase("shift_clan"))
		{
			html.setFile("data/html/mods/shift/clan.htm");
			player.sendPacket(html);
		} else if (command.equalsIgnoreCase("shift_stats"))
		{
			html.setFile("data/html/mods/shift/stats.htm");
			html.replace("%class%", shift.getClass().getSimpleName());
			html.replace("%name%", shift.getName());
			html.replace("%lvl%", shift.getStatus().getLevel());
			player.sendPacket(html);
		} else if (command.equalsIgnoreCase("shift_equipped"))
		{
			if (!player.isGM())
			{
				if (!shift.isGM())
					player.sendPacket(new GMViewItemList(shift, true));
				else
					player.sendMessage("You can't use it on GMs!");
			} else
				player.sendPacket(new GMViewItemList(shift));
			player.sendPacket(new GMHennaInfo(shift));
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		} else if (command.equalsIgnoreCase("home"))
		{
			html.setFile("data/html/mods/shift/initial.htm");
			player.sendPacket(html);
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	public static void showHtml(Player player, Player shift)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(player.getObjectId());
		if (player.isGM())
		{
			AdminEditChar.gatherPlayerInfo(player, shift, html);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		html.setFile("data/html/mods/shift/initial.htm");
		pShift = shift;
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}

	private static void add(Player player, Player playerInstance)
	{
		if (TvTEvent.isPlayerParticipant(playerInstance.getObjectId()))
		{
			player.sendMessage("Player already participated in the event!");
			return;
		}

		if (!TvTEvent.addParticipant(playerInstance))
		{
			player.sendMessage("Player instance could not be added, it seems to be null!");
			return;
		}

		if (TvTEvent.isStarted())
			new TvTEventTeleporter(playerInstance, TvTEvent.getParticipantTeamCoordinates(playerInstance.getObjectId()), true, false);
	}

	private static void remove(Player player, Player playerInstance)
	{
		if (!TvTEvent.removeParticipant(playerInstance.getObjectId()))
		{
			player.sendMessage("Player is not part of the event!");
			return;
		}
		new TvTEventTeleporter(playerInstance, Config.TVT_EVENT_PARTICIPATION_NPC_COORDINATES, true, true);
	}

	public static Player getShiftTarget()
	{
		return pShift;
	}

	public static TextCommandHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final TextCommandHandler INSTANCE = new TextCommandHandler();
	}

	public static int[] calculateMidpoint(String object1, String object2)
	{
		String[] coordinates1 = object1.split(", ");
		String[] coordinates2 = object2.split(", ");

		return IntStream.range(0, 3).boxed().collect(ArrayList<Integer>::new, (l, i) -> l.add((Integer.parseInt(coordinates1[i]) + Integer.parseInt(coordinates2[i])) / 2), List::addAll).stream().mapToInt(Integer::intValue).toArray();
	}
}
