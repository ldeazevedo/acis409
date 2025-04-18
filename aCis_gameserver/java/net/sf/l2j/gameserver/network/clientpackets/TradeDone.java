package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.trade.TradeList;
import net.sf.l2j.gameserver.network.SystemMessageId;

public final class TradeDone extends L2GameClientPacket
{
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		final TradeList trade = player.getActiveTradeList();
		if (trade == null || trade.isLocked())
			return;
		
		if (_response != 1)
		{
			player.cancelActiveTrade();
			return;
		}
		
		// Trade owner not found, or owner is different of packet sender.
		final Player owner = trade.getOwner();
		if (owner == null || !owner.equals(player))
			return;
		
		// Cancel active enchant for trade owner.
		player.cancelActiveEnchant();
		
		// Trade partner not found, cancel trade.
		final Player partner = trade.getPartner();
		if (partner == null || World.getInstance().getPlayer(partner.getObjectId()) == null)
		{
			player.sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			return;
		}
		
		// Check if the trade owner can trade.
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		if (player.getInstanceId() != trade.getPartner().getInstanceId())
		{
			player.cancelActiveTrade();
			return;
		}
		
		// Cancel active enchant for trade partner.
		partner.cancelActiveEnchant();
		
		// Confirm the trade.
		trade.confirm();
	}
}