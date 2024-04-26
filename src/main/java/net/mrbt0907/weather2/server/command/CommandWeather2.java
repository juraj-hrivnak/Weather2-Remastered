package net.mrbt0907.weather2.server.command;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.mrbt0907.configex.ConfigManager;
import net.mrbt0907.weather2.Weather2;
import net.mrbt0907.weather2.api.WeatherAPI;
import net.mrbt0907.weather2.api.weather.WeatherEnum.Stage;
import net.mrbt0907.weather2.config.ConfigGrab;
import net.mrbt0907.weather2.config.ConfigStorm;
import net.mrbt0907.weather2.config.EZConfigParser;
import net.mrbt0907.weather2.network.packets.PacketRefresh;
import net.mrbt0907.weather2.network.packets.PacketVolcanoObject;
import net.mrbt0907.weather2.network.packets.PacketWeatherObject;
import net.mrbt0907.weather2.server.event.ServerTickHandler;
import net.mrbt0907.weather2.server.weather.WeatherManagerServer;
import net.mrbt0907.weather2.util.Maths;
import net.mrbt0907.weather2.util.Maths.Vec3;
import net.mrbt0907.weather2.util.ReflectionHelper;
import net.mrbt0907.weather2.weather.storm.FrontObject;
import net.mrbt0907.weather2.weather.storm.StormObject;
import net.mrbt0907.weather2.weather.storm.StormObject.StormType;
import net.mrbt0907.weather2.weather.volcano.VolcanoObject;

public class CommandWeather2 extends CommandBase
{	
	@Override
	public String getName()
	{
		return "storm";
	}

	@Override
	public String getUsage(ICommandSender sender)
	{
		return "command.storm.usage";
	}
	
	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender)
    {
		return true;
    }
	
	@Override
	public int getRequiredPermissionLevel()
	{
		return 0;
	}
	
	public boolean hasPermission(ICommandSender sender, int level)
	{
		return sender instanceof EntityPlayerMP ? ConfigManager.getPermissionLevel(((EntityPlayerMP)sender).getPersistentID()) >= level : sender.canUseCommand(level, getName());
	}
	
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
	{
		switch(args.length - 1)
		{
			case 0:
				return getListOfStringsMatchingLastWord(args, new String[] {"config", "create", "kill", "view", "test"});
			case 1:
				switch(args[0])
				{
					case "config":
						return getListOfStringsMatchingLastWord(args, new String[] {"refresh", "grablist"});
					case "create":
						return getListOfStringsMatchingLastWord(args, new String[] {"random", "clouds", "rainstorm", "thunderstorm", "supercell", "tropicaldisturbance", "tropicaldepression" , "tropicalstorm", "sandstorm", "ef#", "f#", "c#"});
					case "kill":
						return getListOfStringsMatchingLastWord(args, new String[] {"all", "particles"});
					case "test":
						return getListOfStringsMatchingLastWord(args, new String[] {"class", "volcano"});
					default:
						return Collections.emptyList();
				}
			case 2:
				switch(args[0])
				{
					case "config":
						return getListOfStringsMatchingLastWord(args, new String[] {"all", "dimensionlist", "grablist", "replacelist", "stagelist", "windlist", "sounds", "scene"});
					case "create":
						return getListOfStringsMatchingLastWord(args, new String[] {"~"});
					default:
						return Collections.emptyList();
				}
			case 3:
				switch(args[0])
				{
					case "config":
						switch(args[1])
						{
							case "grablist":
								return getListOfStringsMatchingLastWord(args, new String[] {"addGrabEntry", "addReplaceEntry"});
							default:
								return Collections.emptyList();
						}
					case "create":
						return getListOfStringsMatchingLastWord(args, new String[] {"~"});
					default:
						return Collections.emptyList();
				}
			default: 
				switch(args[0])
				{
					case "create":
						return getListOfStringsMatchingLastWord(args, new String[] {"alwaysprogress", "ishailing", "isviolent", "isnatural", "isfirenado", "neverdissipate", "dontconvert", "revives=#", "direction=<#/north/south/east/west>", "speed=#", "size=#", "name=<Word>"});
					default:
						return Collections.emptyList();
				}
		}
	}
	
	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args)
	{
		World world = sender.getEntityWorld();
		Vec3d pos = sender.getPositionVector();
		int size = args.length;
		
		if (size > 0)
			switch(args[0].toLowerCase())
			{
				case "config":
					if (size > 1)
						switch (args[1].toLowerCase())
						{
							case "grablist":
								if (size > 2)
								{
									boolean isPlayer = sender instanceof EntityPlayer;
									Item itemMain = isPlayer ? ((EntityPlayer)sender).getHeldItemMainhand().getItem() : null;
									Block blockMain = isPlayer && itemMain != Items.AIR ? Block.getBlockFromItem(itemMain) : null;
									String entryMain = blockMain == null ? "" : blockMain.getRegistryName().toString();
									if (blockMain == null && size > 3)
									{
										entryMain = args[3];
									}
									switch (args[2].toLowerCase())
									{
										case "addgrabentry":
											if (entryMain.isEmpty())
											{
												say(sender, "config.grablist.addgrabentry.fail");
											}
											else
											{
												if (ConfigGrab.grab_list_entries.isEmpty())
													ConfigGrab.grab_list_entries += entryMain;
												else
													ConfigGrab.grab_list_entries += ", " + entryMain;
												say(sender, "config.grablist.addgrabentry.success");
												ConfigManager.save("Weather2 Remastered - Grab");
											}
											break;
										case "addreplaceentry":
											Item itemSecondary = isPlayer ? ((EntityPlayer)sender).getHeldItemOffhand().getItem() : null;
											Block blockSecondary = isPlayer && itemSecondary != Items.AIR ? Block.getBlockFromItem(itemSecondary) : null;
											String entrySecondary = blockSecondary == null ? "" : blockSecondary.getRegistryName().toString();
											if (blockSecondary == null && size > 4)
											{
												entrySecondary = args[4];
											}
											if (entryMain.isEmpty() || entrySecondary.isEmpty())
											{
												say(sender, "config.grablist.addreplaceentry.fail");
											}
											else
											{
												if (ConfigGrab.replace_list_entries.isEmpty())
													ConfigGrab.replace_list_entries += entryMain + "=" + entrySecondary;
												else
													ConfigGrab.replace_list_entries += ", " + entryMain + "=" + entrySecondary;
												say(sender, "config.grablist.addreplaceentry.success");
												ConfigManager.save("Weather2 Remastered - Grab");
											}
											break;
										case "addwindentry":
											
											if (entryMain.isEmpty() || args.length <= 3)
											{
												say(sender, "config.grablist.addwindentry.fail");
											}
											else
											{
												int index = size > 4 ? 4 : 3;
												if (ConfigGrab.wind_resistance_entries.isEmpty())
													ConfigGrab.wind_resistance_entries += entryMain + "=" + args[index].replaceAll("[^\\d\\.]", "");
												else
													ConfigGrab.wind_resistance_entries += ", " + entryMain + "=" + args[index].replaceAll("[^\\d\\.]", "");
												say(sender, "config.grablist.addwindentry.success");
												ConfigManager.save("Weather2 Remastered - Grab");
											}
											
											break;
									}
								}
								break;
							case "refresh":
								if (size > 2)
									switch (args[2].toLowerCase())
									{
										case "all":
											WeatherAPI.refreshDimensionRules();
											WeatherAPI.refreshGrabRules();
											PacketRefresh.resetSounds((EntityPlayerMP)sender);
											say(sender, "config.refresh.all.success");
											break;
										case "dimensionlist":
											WeatherAPI.refreshDimensionRules();
											say(sender, "config.refresh.dimensionlist.success");
											break;
										case "grablist":
											WeatherAPI.refreshGrabRules();
											say(sender, "config.refresh.grablist.success");
											break;
										case "replacelist":
											WeatherAPI.refreshGrabRules();
											say(sender, "config.refresh.replacelist.success");
											break;
										case "stagelist":
											WeatherAPI.refreshStages();
											say(sender, "config.refresh.stagelist.success");
											break;
										case "windlist":
											WeatherAPI.refreshGrabRules();
											say(sender, "config.refresh.windlist.success");
											break;
										case "sounds": case "sound":
											if (sender instanceof EntityPlayerMP)
											{
												PacketRefresh.resetSounds((EntityPlayerMP)sender);
												say(sender, "config.refresh.sounds.success");
											}
											else
												say(sender, "config.refresh.sounds.fail");
											break;
										case "scene": case "sceneenhancer":
											if (sender instanceof EntityPlayerMP)
											{
												PacketRefresh.resetSceneEnhancer((EntityPlayerMP)sender);
												say(sender, "config.refresh.sceneenhancer.success");
											}
											else
												say(sender, "config.refresh.sceneenhancer.fail");
											break;
										default:
											say(sender, "config.refresh.usage");
									}
								else
									say(sender, "config.refresh.usage");
								break;
							default:
								say(sender, "config.usage");
						}
					else
						say(sender, "config.usage");
					break;
				case "create":
					if (!hasPermission(sender, 4))
					{
						say(sender, "nopermission");
						break;
					}
					
					if (size > 1)
					{
						int stage = -1;
						boolean isRaining = false, isSandstorm = false, isCyclone = false , isRandom = false;
						String type = args[1].toLowerCase(); 
						
						switch (type)
						{
							case "random":
								isRandom = true;
								stage = Stage.NORMAL.getStage();
								break;
							case "cloud": case "clouds":
								stage = Stage.NORMAL.getStage();
								break;
							case "rain": case "rainstorm":
								isRaining = true;
								stage = Stage.RAIN.getStage();
								break;
							case "thunder": case "thunderstorm": case "lightning": case "lightningstorm":
								isRaining = true;
								stage = Stage.THUNDER.getStage();
								break;
							case "supercell": case "cell": case "severe": case "severethunder": case "severethunderstorm": case "severelightning": case "severelightningstorm":
								isRaining = true;
								stage = Stage.SEVERE.getStage();
								break;
							case "tropicaldisturbance": case "td1":
								isRaining = true;
								isCyclone = true;
								stage = Stage.TROPICAL_DISTURBANCE.getStage();
								break;
							case "tropicaldepression": case "td2":
								isRaining = true;
								isCyclone = true;
								stage = Stage.TROPICAL_DEPRESSION.getStage();
								break;
							case "tropicalstorm": case "ts":
								isRaining = true;
								isCyclone = true;
								stage = Stage.TROPICAL_STORM.getStage();
								break;
							case "sandstorm":
								isSandstorm = true;
								stage = Stage.NORMAL.getStage();
								break;
							default:
								isRaining = true;
								
								if (type.matches("(ef|f)\\d+"))
									stage = Stage.TORNADO.getStage() + Integer.parseInt(type.replaceAll("\\D*", ""));
								else if (type.matches("(category|c)\\d+"))
								{
									isCyclone = true;
									stage = Stage.TROPICAL_STORM.getStage() + Integer.parseInt(type.replaceAll("\\D*", ""));
								}
						}
						
						if (stage > -1)
						{
							if (isSandstorm)
							{
								int dimension = world.provider.getDimension();
								
								WeatherManagerServer wm = ServerTickHandler.dimensionSystems.get(dimension);
								if (wm == null || !EZConfigParser.isWeatherEnabled(dimension))
								{
									say(sender, "fail.nomanager");
									return;
								}
								if (size > 3)
								{
									BlockPos temp;
									try
									{
										temp = parseBlockPos2(sender, args, 2, true);
									}
									catch (NumberInvalidException e)
									{
										Weather2.error(e);
										say(sender, "create.sandstorm.fail.a");
										return;
									}
									pos = new Vec3d(temp.getX(), 0, temp.getY());
								}
								boolean spawned = wm.spawnSandstorm(new Vec3(pos));
								
								if (!spawned)
									say(sender, "create.sandstorm.fail.b");
								else
									say(sender, "create.sandstorm.success", Math.round(pos.x), Math.round(pos.z));
							}
							else
							{
								boolean isViolent = false, isNatural = false, isFirenado = false, neverDissipate = false, shouldConvert = true, alwaysProgress = false, isHailing = false;//, shouldaim = false;
								float sizeMultiplier = -1.0F, angle = -1.0F, speed = -1.0F;
								int revives = -1, dimension = world.provider.getDimension();
								String flag, flags = "", name = "";
								
								if (size > 3)
									try
									{
										BlockPos temp = parseBlockPos2(sender, args, 2, true);
										pos = new Vec3d(temp.getX(), temp.getY(), temp.getZ());
									}
									catch (NumberInvalidException e)
									{
										Weather2.error(e);
										say(sender, "create.fail");
										return;
									}
								
								for (int i = 4; i < size;i++)
								{
									flag = args[i].toLowerCase();
									switch(flag)
									{
										case "alwaysprogress":
											if (!alwaysProgress)
											{
												alwaysProgress = true;
												flags += ", Always Progresses";
											}
											break;
										case "isviolent": case "violent":
											if (!isViolent)
											{
												isViolent = true;
												flags += ", Violent Storm";
											}
											break;
										case "isnatural": case "natural":
											if (!isNatural)
											{
												isNatural = true;
												flags += ", Starts Naturally";
											}
											break;
										case "isfirenado": case "firenado":
											if (!isFirenado)
											{
												isFirenado = true;
												flags += ", Is A Firenado";
											}
											break;
										case "neverdissipate": case "neverdie":
											if (!neverDissipate)
											{
												neverDissipate = true;
												flags += ", Never Dissipates";
											}
											break;
										case "dontconvert": case "noconvert": case "convert":
											if (shouldConvert)
											{
												shouldConvert = false;
												flags += ", Never Converts To Hurricane";
											}
											break;
										case "ishailing": case "hailing": case "hail":
											if (!isHailing)
											{
												isHailing = true;
												flags += ", Storm Is Hailing";
											}
										default:
											if (flag.matches("revives\\=\\d+"))
											{
												if (revives < 0)
												{
													revives = Integer.parseInt(flag.replaceAll("\\D*", ""));
													flags += ", Will Revive " + revives + " time" + (revives > 1 ? "s" : "");
												}
											}
											else if (flag.matches("(angle|direction)\\=(north|south|east|west|\\d+)"))
											{
												if (angle < 0.0F)
												{
													angle = flag.contains("north") ? 180.0F : flag.contains("east") ? 270.0F : flag.contains("south") ? 0.0F : flag.contains("west") ? 90.0F : Float.parseFloat(flag.replaceAll("[^\\d\\.]*", ""));
													flags += ", Aiming at " + angle + " degrees";
												}
											}
											else if (flag.matches("speed\\=[\\d\\.]+"))
											{
												if (speed < 0.0F)
												{
													speed = Float.parseFloat(flag.replaceAll("[^\\d\\.]*", "")) * 0.05F;
													flags += ", Moving At " + (speed * 20.0F) + " M/s";
												}
											}
											else if (flag.matches("size\\=[\\d\\.\\%]+"))
											{
												if (sizeMultiplier < 0.0F)
												{
													sizeMultiplier = Float.parseFloat(flag.replaceAll("[^\\d\\.]*", "")) * 0.01F;
													
													flags += ", Will Grow " + (sizeMultiplier * 100.0F) + "%" + (sizeMultiplier < 1.0F ? " Smaller Than Normal" : sizeMultiplier == 1.0F ? "" : "Larger Than Normal");
												}
											}
											else if (flag.matches("name\\=\\w+"))
											{
												if (name == "")
												{
													name = args[i].replaceFirst("[nN][aA][mM][eE]\\=", "");
													flags += ", Named " + name;
												}
											}
										
										
									}
								}
								
								//TODO: make this handle non StormObject types better, currently makes instance and doesnt use that type if its a sandstorm
								WeatherManagerServer wm = ServerTickHandler.dimensionSystems.get(dimension);
								if (wm == null || !EZConfigParser.isWeatherEnabled(dimension))
								{
									say(sender, "fail.nomanager");
									return;
								}
								StormObject so = new StormObject(wm.getGlobalFront());
								
								so.layer = 0;
								so.isNatural = isNatural;
								so.temperature = 0.1F;
								so.pos = new Vec3(pos.x, so.getLayerHeight(), pos.z);
								so.stage = isRandom ? so.rollDiceOnMaxIntensity() : stage;
								so.stageMax = so.stage;
								so.intensity = so.stage - 0.99F;
								so.rain = isRaining ? (isNatural ? 50.0F : isHailing ? 200.0F : so.stage * 50.0F) + 1.0F : 0.0F;
								so.hail = isHailing ? isNatural ? 0.0F : 100.0F : 0.0F;
								so.hailRate = isHailing ? Math.min((float) ConfigStorm.hail_max_buildup_rate, 1.0F) : 0.0F;
								so.sizeRate = sizeMultiplier;
								if (angle >= 0.0F)
									so.setAngle(angle);
								if (speed >= 0.0F)
									so.setSpeed(speed);
								so.alwaysProgresses = alwaysProgress;
								so.neverDissipate = neverDissipate;
								so.isFirenado = isFirenado;
								so.shouldConvert = shouldConvert;
								so.isViolent = isViolent;
								so.maxRevives = revives;
								so.name = name;
								so.shouldBuildHumidity = true;
								
								if (isCyclone || isRandom && so.stage > 3 && Maths.chance(25))
									so.stormType = StormType.WATER.ordinal();
								
								so.init();
									
								if (so.rain > 0.0F && so.isNatural)
									so.initRealStorm();
								else
								{
									so.canProgress = true;
									
									if (so.sizeRate < 0.0F)
										so.sizeRate = (float) Maths.random(ConfigStorm.min_size_growth, ConfigStorm.max_size_growth);
									
									if (so.isViolent)
									{
										so.sizeRate += Maths.random(ConfigStorm.min_violent_size_growth, ConfigStorm.max_violent_size_growth);
										if (so.stageMax < 9)
											so.stageMax += 1;
									}
								}

								so.updateType();
								
								wm.getGlobalFront().addWeatherObject(so);
								PacketWeatherObject.create(wm.getDimension(), so);
								
								say(sender, "create.success", so.getName(true), Math.round(pos.x), Math.round(pos.z), flags);
								return;
							}
						}
						else
							say(sender, "create.usage");
					}
					else
						say(sender, "create.usage");
					break;
				case "kill":
					if (size > 1)
						switch (args[1].toLowerCase())
						{
							case "all":
								if (!hasPermission(sender, 4))
								{
									say(sender, "nopermission");
									break;
								}
								WeatherManagerServer wm = ServerTickHandler.dimensionSystems.get(world.provider.getDimension());
								if (wm == null || !EZConfigParser.isWeatherEnabled(world.provider.getDimension()))
								{
									say(sender, "fail.nomanager");
									return;
								}
								List<FrontObject> fronts = wm.getFronts();
								size = wm.getWeatherObjects().size();
								if (size > 0)
								{
									for (int i = 0; i < fronts.size(); i++)
									{
										FrontObject front = fronts.get(i);
										Weather2.debug("Killing front " + front.getUUID());
										front.isDead = true;
									}
									say(sender, "kill.all.success", size);
								}
								else
									say(sender, "kill.all.fail");
								break;
							case "particle": case "particles":
								if (sender instanceof EntityPlayerMP)
								{
									PacketWeatherObject.clientCleanup((EntityPlayerMP) sender);
									say(sender, "kill.particles.success");
								}
								else
									say(sender, "kill.particles.fail");
								break;
							default:
								say(sender, "kill.usage");
						}
					else
						say(sender, "kill.usage");
					break;
				case "view":
					if (size > 1)
						switch (args[1].toLowerCase())
						{
							default:
								say(sender, "view.fail");
						}
					else
						say(sender, "view.fail");	
					break;
				case "test":
					if (!hasPermission(sender, 4))
					{
						say(sender, "nopermission");
						break;
					}
					
					if (size > 1)
						switch (args[1].toLowerCase())
						{
							case "volcano":
							{
								WeatherManagerServer wm = ServerTickHandler.dimensionSystems.get(0);
								if (wm == null || !EZConfigParser.isWeatherEnabled(0))
								{
									say(sender, "fail.nomanager");
									return;
								}
								VolcanoObject vo = new VolcanoObject(wm);
								vo.pos = new CoroUtil.util.Vec3(pos);
								vo.init();
								wm.addVolcanoObject(vo);				
								PacketVolcanoObject.create(wm.getDimension(), vo);
								
								say(sender, "test.volcano.success");
								break;
							}
							case "class":
							{
								if (size > 2)
								{
									List<String> found = ReflectionHelper.view(args[2]);
									for (String line : found)
										say(sender, "test.class.success", line);
								}
								else
									say(sender, "test.class.usage");
								break;
							}
							default:
								say(sender, "test.usage");
						}
					else
						say(sender, "test.usage");	
					break;
				default:
					say(sender, "usage");
			}
		else
			say(sender, "usage");
	}
	
	public static BlockPos parseBlockPos2(ICommandSender sender, String[] args, int startIndex, boolean centerBlock) throws NumberInvalidException
	{
		BlockPos blockpos = sender.getPosition();
		return new BlockPos(parseDouble((double)blockpos.getX(), args[startIndex], -30000000, 30000000, centerBlock), 0.0D, parseDouble((double)blockpos.getZ(), args[startIndex + 1], -30000000, 30000000, centerBlock));
	}
		
	private void say(ICommandSender sender, String localizationID, Object... args)
	{
		notifyCommandListener(sender, this, "command." + getName() + "." + localizationID, args);
	}
}
