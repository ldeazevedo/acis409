package net.sf.l2j.gameserver.model.botprevention;

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.cache.CrestCache;
import net.sf.l2j.gameserver.enums.CrestType;
import net.sf.l2j.gameserver.enums.PunishmentType;
import net.sf.l2j.gameserver.enums.RestartType;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.instance.Servitor;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PledgeCrest;
import net.sf.l2j.gameserver.network.serverpackets.TutorialCloseHtml;
import net.sf.l2j.gameserver.network.serverpackets.TutorialShowHtml;
import net.sf.l2j.gameserver.network.serverpackets.TutorialShowQuestionMark;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;

public class BotsPreventionManager
{
	final static CLogger LOGGER = new CLogger(BotsPreventionManager.class.getName());
	
	private enum ValidationState
	{
		NONE, VALIDATION
	}
	
	protected static final int PUNISH_TELEPORT = 1000;
	protected static final int PUNISH_KICK = 1001;
	protected static final int PUNISH_JAIL = 1002;
	protected static final int PUNISH_BAN = 1003;
		
	private class PlayerValidationData
    {
		public PlayerValidationData(boolean allowIncorrectChoice)
		{
			_state = ValidationState.NONE;
			_allowIncorrectChoice = allowIncorrectChoice;
		}
		
		public ValidationState _state;
		public boolean _allowIncorrectChoice;
		public int mainpattern;
		public ArrayList<Integer> options = new ArrayList<>();
		public int patternid;
		
		public boolean showLastChanceWindow()
		{
			if (!Config.BPS_ALLOW_INCORRECT_CHOICE)
				return false;
			
			return !_allowIncorrectChoice;
		}
	}
  
    protected static Map<Integer, Double> _monstersCounter;
    protected static Map<Integer, ValidationCountdown> _validationTasks;
    protected static Map<Integer, PlayerValidationData> _validationData;
    protected static Map<Integer, byte[]> _images;
    protected static List<Integer> _supportClasses;
    protected static List<Integer> _showMessageOnLogin;
    protected static byte[] _emptyImage;
    protected static int _emptyImageId;
    protected int VALIDATION_TIME = Config.BPS_VALIDATION_TIME * 1000;
    protected static List<Integer> _generatedPatternsIDs;
    protected static int IMAGES_STARTING_ID = 1500;
    
    public static final BotsPreventionManager getInstance()
    {
        return SingletonHolder._instance;
    }
  
    BotsPreventionManager()
    {
    	_monstersCounter = new HashMap<>();
        _validationTasks = new HashMap<>();
        _validationData = new HashMap<>();
        _supportClasses = new ArrayList<>();
        _images = new HashMap<>();
        _showMessageOnLogin = new ArrayList<>();
        _generatedPatternsIDs = new ArrayList<>();
        
        loadSupportClasses();
        loadImages();
    }

    private static void loadSupportClasses()
    {
    	if (Config.BPS_CHECK_BISHOPS)
    	{
    		_supportClasses.add(ClassId.BISHOP.getId());
    		_supportClasses.add(ClassId.CARDINAL.getId());
    	}
    	if (Config.BPS_CHECK_ELDERS)
    	{
    		_supportClasses.add(ClassId.ELVEN_ELDER.getId());
    		_supportClasses.add(ClassId.EVAS_SAINT.getId());
    	}
    	if (Config.BPS_CHECK_PROPHETS)
    	{
    		_supportClasses.add(ClassId.PROPHET.getId());
    		_supportClasses.add(ClassId.HIEROPHANT.getId());
    	}
    	if (Config.BPS_CHECK_BLADEDANCERS)
    	{
    		_supportClasses.add(ClassId.BLADEDANCER.getId());
    		_supportClasses.add(ClassId.SPECTRAL_DANCER.getId());
    	}
    	if (Config.BPS_CHECK_SWORDSINGERS)
    	{
    		_supportClasses.add(ClassId.SWORD_SINGER.getId());
    		_supportClasses.add(ClassId.SWORD_MUSE.getId());
    	}
    	if (Config.BPS_CHECK_WARCRYERS)
    	{
    		_supportClasses.add(ClassId.WARCRYER.getId());
    		_supportClasses.add(ClassId.DOOMCRYER.getId());
    	}
    	if (Config.BPS_CHECK_OVERLORDS)
    	{
    		_supportClasses.add(ClassId.OVERLORD.getId());
    		_supportClasses.add(ClassId.DOMINATOR.getId());
    	}
    }
    
    public boolean onQuestionMark(Player player, int number)
    {
    	if (number != PUNISH_TELEPORT && number != PUNISH_JAIL && number != PUNISH_KICK  && number != PUNISH_BAN)
    		return false;

    	punishmentWindow(player, number);
    	return true;
    }

    public void onLogin(Player player) 
    {
    	sendImagesOnEnter(player);
    	//create player validation data if not exist
    	if (!_validationData.containsKey(player.getObjectId()))
    	{
    		_validationData.put(player.getObjectId(), new PlayerValidationData(Config.BPS_ALLOW_INCORRECT_CHOICE));
    	}
    	//create also validation task
    	if (!_validationTasks.containsKey(player.getObjectId()))
    	{
    		_validationTasks.put(player.getObjectId(), new ValidationCountdown(VALIDATION_TIME));
    	}
    	handleOnLogin(player);
    }
    
    private static void handleOnLogin(Player player)
    {
    	//check if player validation is in progress
    	if (_validationData.get(player.getObjectId())._state != ValidationState.NONE)
    	{
    		_validationTasks.get(player.getObjectId()).setPlayer(player);
    		_validationTasks.get(player.getObjectId()).showHtmlWindow();
    	}
    	
    	//handle message on login
    	if (_showMessageOnLogin.contains(player.getObjectId()))
    	{
    		var punishType = Config.BPS_PUNISHMENT + 1000;
    		showQuestionMark(player, punishType);
    	}
    }
    
    private static void showQuestionMark(Player player, int param) 
    {
    	player.sendPacket(new TutorialShowQuestionMark(param));
    }
    
    private static void sendImagesOnEnter(Player player)
    {
    	
    	//Crest ID is generated by Client while uploading, and each time Crest is changed new ID is generated: Example ID: 268481020
    	//When player is logging in, send images with static names
    	//Send all choice options to client
    	for (var imageId : _images.keySet())
		{
            PledgeCrest packet = new PledgeCrest(imageId, _images.get(imageId));
            player.sendPacket(packet);
		}
    	
        //Send empty image to client 
        PledgeCrest emptyImage = new PledgeCrest(_emptyImageId, _emptyImage);
        player.sendPacket(emptyImage);
    }
    
    public void onLogout(Player player) 
    {
    	
    }
        
    //on server shutdown
    public void cleanUp()
    {
    	//stop all tasks
    	for (var playerId : _validationTasks.keySet())
		{
			var validationTask = _validationTasks.get(playerId);
			if (validationTask != null)
			{
				validationTask.stopTask();
			}
		}
    }
    
    public void onCreatureKill(Creature killer, Creature victim)
    {
    	//check only if monster is killed
    	if (!(victim instanceof Monster))
    	{
    		return;
    	}
    	
    	Player killingPlayer = null;
    	if (killer instanceof Player)
    	{
    		killingPlayer = (Player) killer;
    	}
    	else if (killer instanceof Servitor)
    	{
    		killingPlayer = ((Servitor) killer).getOwner();
    	}
    	else if (killer instanceof Pet)
    	{
    		killingPlayer = ((Pet) killer).getOwner();
    	}
    	
    	//something wrong
    	if (killingPlayer == null)
    	{
    		return;
    	}
    	
    	checkSupportsKills(killingPlayer);
    	checkKillerKills(killingPlayer);
    }
    
    private void checkKillerKills(Player player)
    {
        if (!isValidationInProgress(player))
        {
        	updateKillsCounter(player, 1.0);
        }
    }
    
    private void checkSupportsKills(Player player)
    {
    	//no support classes declared, so we do not need analyse party
    	if (_supportClasses.isEmpty())
    	{
    		return;
    	}
    	
    	if (player.getParty() == null)
    	{
    		return;
    	}
    	
    	var supports = new ArrayList<Player>();
    	var damageDealersCount = 1.0; // 1 = because killer is considered as damage dealer
    	
		for (Player member : player.getParty().getMembers())
		{
			//if support killed a monster count him as damage dealer
			if (member == null || member.getObjectId() == player.getObjectId() || member.isDead())
				continue;
			
			//if party member is too far - skip checking
			if (player.distance2D(member) > 2000) 
			{
				continue;
			}
			
			if (_supportClasses.contains(member.getActiveClass()))
			{
				supports.add(member);
			}
			else
			{
				damageDealersCount ++;
			}
		}
    	
    	if (!supports.isEmpty())
    	{
        	for (Player sup : supports)
			{
            	//validation is already in progress...
                if (!isValidationInProgress(sup))
                {
                	updateKillsCounter(sup, 1.0 / damageDealersCount);
                }
			}
    	}
    }
    
    private void updateKillsCounter(Player player, double count)
    {
    	var currentCount = 0.0;
        if (_monstersCounter.containsKey(player.getObjectId()))
        {
        	currentCount = _monstersCounter.get(player.getObjectId());
        }
        currentCount += count;
        
        int next = Config.BPS_KILLS_COUNTER_RANDOMIZATION <= 0 ? 0 : Rnd.get(Config.BPS_KILLS_COUNTER_RANDOMIZATION);
        if (Config.BPS_KILLS_COUNTER + next < currentCount)
        {
            startValidationTask(player);
            _monstersCounter.put(player.getObjectId(), 0.0);
            player.broadcastTitleInfo();
        }
        else
        {
        	_monstersCounter.put(player.getObjectId(), currentCount);
        	player.broadcastTitleInfo();
        }
    }
  
    private static void loadImages()
    {
        String CRESTS_DIR = "data/html/mods/prevention/patterns";
       
        final File directory = new File(CRESTS_DIR);
        directory.mkdirs();
       
        int imageId = IMAGES_STARTING_ID;
        
        File[] files = directory.listFiles();
        Arrays.sort(files, Comparator.comparing(File::getName));
        
        for (File file : files)
        {
        	var fileName = file.getName();
            if (!(fileName.startsWith("pattern_") && fileName.endsWith(".dds")))
                continue;
           
            byte[] data;
           
            try (RandomAccessFile f = new RandomAccessFile(file, "r"))
            {
                data = new byte[(int) f.length()];
                f.readFully(data);
            }
            catch (Exception e)
            {
                continue;
            }
            _images.put(imageId, data);
            imageId ++;
        }
        
        //---- empty image ----
        
        final File emptyImage = new File(CRESTS_DIR + "/empty.dds");
        
        try (RandomAccessFile f = new RandomAccessFile(emptyImage, "r"))
        {
        	_emptyImage = new byte[(int) f.length()];
            f.readFully(_emptyImage);
            _emptyImageId = imageId; //set empty image ID as last of all images loaded
        }
        catch (Exception e)
        {
        }
        
    }
  
    private static void preValidationWindow(Player player)
    {
    	var container = _validationData.get(player.getObjectId());
    	
		String filename = "data/html/mods/prevention/prevention.htm";
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(filename);
		html.replace("%pattern%","<img src=Crest.crest_" + Config.SERVER_ID + "_" + (_validationData.get(player.getObjectId()).patternid) + " width=38 height=38>");
		
		
        StringBuilder tb = new StringBuilder();

        for (int i = 0; i < container.options.size(); i++)
        {
        	tb.append("<td align=center><button width=38 height=38 back=Crest.crest_" + Config.SERVER_ID + "_" + _emptyImageId + " fore=Crest.crest_" + Config.SERVER_ID + "_" + _emptyImageId + "></td>");
        }
        
        html.replace("%choices%", tb.toString());

        player.sendPacket(new TutorialShowHtml(html.getHtml()));
    }
    
    private static void validationWindow(Player player)
    {
    	var container = _validationData.get(player.getObjectId());
    	var isLastChance = container.showLastChanceWindow();
    	
		String filename = "data/html/mods/prevention/" + (isLastChance ? "prevention_event_mistake.htm" : "prevention_event.htm");
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(filename);
		html.replace("%pattern%","<img src=Crest.crest_" + Config.SERVER_ID + "_" + (_validationData.get(player.getObjectId()).patternid) + " width=38 height=38>");

        
        StringBuilder tb = new StringBuilder();

        for (int i = 0; i < container.options.size(); i++)
        {
        	tb.append("<td align=center><button action=\"link report_" + i + "\" width=38 height=38 back=Crest.crest_" + Config.SERVER_ID + "_" + container.options.get(i) + " fore=Crest.crest_" + Config.SERVER_ID + "_" + container.options.get(i) + "></td>");
        }
        
        html.replace("%choices%", tb.toString());
        
        player.sendPacket(new TutorialShowHtml(html.getHtml()));
    }
  
    private static boolean isValidationInProgress(Player player)
    {
    	return _validationData.containsKey(player.getObjectId()) && _validationData.get(player.getObjectId())._state != ValidationState.NONE;
    }
    
    private static void punishmentWindow(Player player, int punishType)
    {
    	//message has been displayed, remove 
    	if (_showMessageOnLogin.contains(player.getObjectId()))
    	{
    		_showMessageOnLogin.remove(_showMessageOnLogin.indexOf(player.getObjectId()));
    	}
    	
    	String punishMessage = "character has been punished";
    	if (punishType == PUNISH_TELEPORT)
    		punishMessage = "character has been moved to nearest town!";
    	else if (punishType == PUNISH_KICK)
    		punishMessage = "character has been disconnected from the server!";
    	else if (punishType == PUNISH_JAIL)
    		punishMessage = "character has been imprisoned!";
    	else if (punishType == PUNISH_BAN)
    		punishMessage = "character has been banned!";    	
    	
    	
		String filename = "data/html/mods/prevention/prevention_punishment.htm";
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(filename);
		
		html.replace("%punish_message%", punishMessage);
		player.sendPacket(html);
    }
  
    public void startValidationTask(Player player)
    {
    	var validationData = _validationData.get(player.getObjectId());
    	validationData._state = ValidationState.VALIDATION;
        randomizeimages(validationData, player);

        //Send pattern with unique file name to client 
        PledgeCrest patternImage = new PledgeCrest(validationData.patternid, _images.get(validationData.options.get(validationData.mainpattern)));
        player.sendPacket(patternImage);
       
        var validationTask = _validationTasks.get(player.getObjectId());
        validationTask.startTask(player, VALIDATION_TIME / 1000);
        ThreadPool.schedule(validationTask, 1);
    }
  
    protected void randomizeimages(PlayerValidationData container, Player player)
    {
        int buttonscount = 4;
        var imagesIds = _images.keySet().stream().collect(Collectors.toList());
        int imagescount = imagesIds.size();
        
        container.options.clear();
       
        for (int i = 0; i < buttonscount; i++)
        {
        	var randomIndex = Rnd.get(imagescount);
        	while (container.options.indexOf(imagesIds.get(randomIndex)) > -1)
            {
        		randomIndex = Rnd.get(imagescount);
            }
        	container.options.add(imagesIds.get(randomIndex));
        }
               
        int mainIndex = Rnd.get(buttonscount);
        container.mainpattern = mainIndex; 
       
        Calendar token =  Calendar.getInstance();
    	String uniquetoken = getDatePart(token,Calendar.DAY_OF_MONTH) + getDatePart(token,Calendar.HOUR_OF_DAY)+ getDatePart(token,Calendar.MINUTE)+ getDatePart(token,Calendar.SECOND) + getDatePart(token,Calendar.MILLISECOND);
    	var intUniqueToken = Integer.parseInt(uniquetoken);

    	//when token already exists, just increase by one (when in same miliseconds two players are validated)
    	//also check if ID is not used by any clan crest
        while(true)
        {
        	if (_generatedPatternsIDs.contains(intUniqueToken) || CrestCache.getInstance().getCrest(CrestType.PLEDGE, intUniqueToken) != null)
        	{
        		intUniqueToken++;
        	}
        	else
        	{
        		break;
        	}
        }
        
        synchronized(_generatedPatternsIDs)
        {
        	_generatedPatternsIDs.add(intUniqueToken);
        }
        
        container.patternid = intUniqueToken;  
    }
  
    private static String getDatePart(Calendar token, int part)
    {
    	var partNum = token.get(part);
    	if (part == Calendar.MILLISECOND) 
    	{
    		return Integer.toString(partNum / 100);
    	}
    	
    	if (partNum< 10) 
    	{
    		return "0" + partNum;
    	}
    	return Integer.toString(partNum);
    }
    
    protected void punishPlayer(Player player)
    {

    	_showMessageOnLogin.add(player.getObjectId());
    	
    	var punishType = Config.BPS_PUNISHMENT + 1000;
        switch (punishType)
        {
        // 0 = move character to the closest village.
        // 1 = kick characters from the server.
        // 2 = put character to jail.
        // 3 = ban character from the server.
            case PUNISH_TELEPORT:
                teleportPunishment(player, Config.BPS_PUNISHMENT_TIME * 60);
                break;
            case PUNISH_KICK:
                if (player.isOnline())
                {
                	showQuestionMark(player, PUNISH_KICK);
                    player.logout(true);
                }
                break;
            case PUNISH_JAIL:
                jailPunishment(player, Config.BPS_PUNISHMENT_TIME * 60);
                break;
            case PUNISH_BAN:
                changeaccesslevel(player, -100);
                break;
        }

    }
  
    private static void changeaccesslevel(Player player, int lvl)
    {
        if (player.isOnline())
        {
        	showQuestionMark(player, PUNISH_BAN);
            player.setAccessLevel(lvl);
            player.logout(false);
        }
        else
        {
        	try (Connection con = ConnectionPool.getConnection();
				PreparedStatement ps = con.prepareStatement("UPDATE characters SET accesslevel=? WHERE obj_id=?"))
			{
				ps.setInt(1, lvl);
				ps.setInt(2, player.getObjectId());
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.error("Couldn't change player's accesslevel.", e);
			}
        }
    }
    
    private static void teleportPunishment(Player player, int delay)
    {
        if (player.isOnline())
        {
        	player.abortAll(true);
            player.teleportTo(RestartType.TOWN);
            showQuestionMark(player, PUNISH_TELEPORT);
        }
        else
        {
        	player.teleportToOffline(RestartType.TOWN);
        	_showMessageOnLogin.add(player.getObjectId());
        }
    }
    
    
    private static void jailPunishment(Player player, int delay)
    {
        if (player.isOnline())
        {
        	showQuestionMark(player, PUNISH_JAIL);
        	player.getPunishment().setType(PunishmentType.JAIL, Config.BPS_PUNISHMENT_TIME);
        }
        else
        {
    		try (Connection con = ConnectionPool.getConnection();
    			PreparedStatement ps = con.prepareStatement("UPDATE characters SET x=-114356, y=-249645, z=-2984, punish_level=?, punish_timer=? WHERE obj_id=?"))
    		{
    			ps.setInt(1, PunishmentType.JAIL.ordinal());
    			ps.setLong(2, ((delay > 0) ? delay * 60000L : 0));
    			ps.setInt(3, player.getObjectId());
    			ps.execute();
    		}
    		catch (Exception e)
    		{
    			LOGGER.error("Couldn't jail offline Player.", e);
    		}
    		
    		_showMessageOnLogin.add(player.getObjectId());
        }
    }
  
    public void onBypass(String command, Player player)
    {
        if (!isValidationInProgress(player))
            return;
       
        String params = command.substring(command.indexOf("_") + 1);
       
        int chosenOption = -1;
        if (tryParseInt(params))
        {
        	chosenOption = Integer.parseInt(params);
        }
       
        if (chosenOption > -1)
        {
        	PlayerValidationData playerData = _validationData.get(player.getObjectId());
            if (chosenOption != playerData.mainpattern)
            {
            	//check attempts
            	if (playerData._allowIncorrectChoice)
            	{
            		playerData._allowIncorrectChoice = false;
            		validationWindow(player);
            	}
            	else 
            	{
            		showOnScreenMessage(player,"Unfortunately, patterns don't match.");
                	_validationTasks.get(player.getObjectId()).stopTask();
                	player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
                	punishPlayer(player);
                	resetValidationData(player);
            	}
            }
            else
            {
                showOnScreenMessage(player,"Congratulations, patterns match!");
                _validationTasks.get(player.getObjectId()).stopTask();
                player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
                resetValidationData(player);
                rewardPlayer(player);
            }
        }
    }
    
    private static void rewardPlayer(Player player)
    {
    	if (Config.BPS_ENABLE_REWARD)
    	{
    		player.addItem(Config.BPS_REWARD_ID, Config.BPS_REWARD_AMOUNT, true);
    	}
    }
    
    private static void resetValidationData(Player player) 
    {
    	if (player == null)
    		return;
    	
    	_validationData.get(player.getObjectId())._allowIncorrectChoice = Config.BPS_ALLOW_INCORRECT_CHOICE;
    	_validationData.get(player.getObjectId())._state = ValidationState.NONE;
    }
    
    private static void showOnScreenMessage(Player player, String message)
    {
    	player.sendPacket(new ExShowScreenMessage(message, 2000, SMPOS.TOP_CENTER, false));
    }
    
	protected class ValidationCountdown implements Runnable
	{
        private Player _player = null;
        private int _time;
        private int _countTime;
        private boolean _stopTask = false;
		
        public ValidationCountdown(int time)
        {
            _time = time;
            _countTime = time + 3;
        }
		
        public void stopTask()
        {
        	_stopTask = true;
        	_countTime = -1;
        }
        
        public void startTask(Player player, int time)
        {
        	_stopTask = false;
        	_player = player;
            _time = time;
            _countTime = time + 3;
        }
        
        public void setPlayer(Player player)
        {
        	_player = player;
        }

        public void showHtmlWindow()
        {
        	//for first 3 seconds to avoid accidental click by player, display static window
        	if (_time < _countTime)
        	{
        		preValidationWindow(_player);
        	}
        	else 
        	{
        		validationWindow(_player);
        	}
        }
        
		@Override
		public void run()
		{
			if (_stopTask)
			{
				return;
			}
			
			if (_player != null)
            {
				//if player is online
				if (_player.isOnline())
				{
					 if (_time + 3 == _countTime)
	                {
	                	preValidationWindow(_player);
	                }
	                else if (_time  == _countTime)
	                {
	                	_player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
	                	validationWindow(_player);
	                }

	                //Display Time Counter
	                if (_countTime > 0 )
	                {
	                	if (_countTime <= _time) 
	                	{
	                        var seconds = _countTime % 60;
	                        var minutes = (_countTime - seconds) / 60;
	                        
	                        var strMinutes = "";
	                        if (minutes > 0)
	                        {
	                        	strMinutes = minutes + " minute(s) ";
	                        }
	                        
	                        //_player.sendPacket(new ExShowScreenMessage(1, -1, SMPOS.TOP_CENTER, false, 1, 0, 0, true, 2000, false, "You have " + strMinutes + seconds + " second(s) to match patterns."));
	                        _player.sendPacket(new ExShowScreenMessage("You have " + strMinutes + seconds + " second(s) to match patterns.", 2000, SMPOS.TOP_CENTER, false));
	                	}
	                }
				}
				
				if (_countTime <= 0)
				{
                	showOnScreenMessage(_player, "No action occurred in time.");
                	_player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
                	punishPlayer(_player);
                	resetValidationData(_player);
				}
				//reschedule and decrease time even if player is offline (will be punished while offline)
            	if (_countTime > 0)
            	{
                    //reschedule
                	var validationTask = _validationTasks.get(_player.getObjectId());
                    ThreadPool.schedule(validationTask, 1000);
            	}
                _countTime--;
            }
		}
	}
  
    protected boolean tryParseInt(String value)
    {
        try
        {
            Integer.parseInt(value);
            return true;
        }
       
        catch (NumberFormatException e)
        {
            return false;
        }
    }
  
    private static class SingletonHolder
    {
        protected static final BotsPreventionManager _instance = new BotsPreventionManager();
    }
}